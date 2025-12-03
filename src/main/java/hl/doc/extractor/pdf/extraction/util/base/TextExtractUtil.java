package hl.doc.extractor.pdf.extraction.util.base;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;

public class TextExtractUtil  {

	public static List<ContentItem> extractTextContent(PDDocument doc, int pageIndex, boolean isGroupByParagraph) throws IOException {

		//Silent the missing font warning
    	Logger.getLogger("org.apache.pdfbox.pdmodel").setLevel(Level.SEVERE);

	    class GroupedTextStripper extends PDFTextStripper {
	        List<ContentItem> contentItems = new ArrayList<>();
	        List<TextPosition> currentLine = new ArrayList<>();

	        GroupedTextStripper() throws IOException {}
	        int iExtractSeq = 1;

	        @Override
	        protected void processTextPosition(TextPosition text) {
	            if (currentLine.isEmpty()) {
	                currentLine.add(text);
	                return;
	            }

	            TextPosition last = currentLine.get(currentLine.size() - 1);

	            // --- FIX 1: Baseline-based grouping to handle superscripts/subscripts ---
	            float baselineLast = last.getTextMatrix().getTranslateY();
	            float baselineCurrent = text.getTextMatrix().getTranslateY();

	            float hLast = last.getHeightDir();
	            float hCurrent = text.getHeightDir();

	            // relative tolerance based on the larger font height
	            float tolerance = Math.max(hLast, hCurrent) * 0.8f;

	            if (Math.abs(baselineLast - baselineCurrent) < tolerance) {
	                currentLine.add(text);
	            } else {
	                addBoundingBox(getCurrentPage(), currentLine);
	                currentLine.clear();
	                currentLine.add(text);
	            }
	            super.processTextPosition(text);
	        }

	        @Override
	        protected void endPage(PDPage page) throws IOException {
	            if (!currentLine.isEmpty()) {
	                addBoundingBox(page, currentLine);
	                currentLine.clear();
	            }
	            super.endPage(page);
	        }

	        private void addBoundingBox(PDPage page, List<TextPosition> line) {
	            if (line.isEmpty()) return;

	            // --- 1) Compute main baseline (median) and full line height ---
	            List<Float> baselines = new ArrayList<>();
	            float maxHeight = 0;

	            for (TextPosition t : line) {
	                baselines.add(t.getYDirAdj());
	                maxHeight = Math.max(maxHeight, t.getHeightDir());
	            }

	            Collections.sort(baselines);
	            float mainBaseline = baselines.get(baselines.size() / 2);

	            // Define group height tolerance
	            //float tolerance = maxHeight * 0.3f;

	            // --- 2) Bounding box extremes ---
	            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
	            float maxX = 0, maxY = 0;
	            StringBuffer sb = new StringBuffer();
	            
	            for (TextPosition t : line) {
	                float x = t.getXDirAdj();
	                //float baseline = t.getYDirAdj();
	                float w = t.getWidthDirAdj();
	                //float h = t.getHeightDir();

	                // --- 3) NORMALIZED baseline for bounding box only ---
	                float normalizedBaseline = mainBaseline;

	                // --- 4) NORMALIZED height for bounding box only ---
	                float normalizedHeight = maxHeight;

	                // NOTE:
	                // We DO NOT modify t's visual baseline or height.
	                // The normalization only applies to bounding box calculation.

	                float yTop = normalizedBaseline - normalizedHeight;

	                minX = Math.min(minX, x);
	                minY = Math.min(minY, yTop);
	                maxX = Math.max(maxX, x + w);
	                maxY = Math.max(maxY, normalizedBaseline);

	                sb.append(t.getUnicode());
	            }
	            

	            String sData   = sb.toString();
	            String sFormat = null;
	            
	            if(sData.trim().length()>0)
	            {
	            	sFormat = getCommonFontStyle(line);
	            	//System.out.println("sFormat---->"+sFormat);
	            }

	            double dX = minX;
	            double dY = minY;
	            double dW = maxX - minX;
	            double dH = maxY - minY;
	            
	            //Assumption : empty out of view bounding box 
	            if(dX<0) dX = 0;
	            if(dY<0) dY = 0;
	            
	            Rectangle2D rect2D = new Rectangle2D.Double(dX, dY, dW, dH);
	            
	            ContentItem textItem = new ContentItem(Type.TEXT, sData, getCurrentPageNo(), rect2D);
	            textItem.setExtract_seq(iExtractSeq++);
	            textItem.setContentFormat(sFormat);
	            contentItems.add(textItem);
	        }

	    }

	    GroupedTextStripper stripper = new GroupedTextStripper();
	    //stripper.setAddMoreFormatting(true);
	    stripper.setSortByPosition(true);
	    stripper.setStartPage(pageIndex + 1);
	    stripper.setEndPage(pageIndex + 1);
	    stripper.getText(doc);

	    List<ContentItem> textItems = stripper.contentItems;
	    
	    if(isGroupByParagraph)
	    	textItems = groupTextByParagraph(textItems);
	    
	    return textItems;
	}
	
	private static List<ContentItem> groupTextByParagraph(List<ContentItem> aTextItems)
	{
		return groupVerticalText(aTextItems, 1.4, true, true);
	}
	private static List<ContentItem> groupVerticalText(List<ContentItem> aTextItems, double aYThreshold, boolean isMatchFontStyle, boolean isCombineMultiLines)
	{
		List<ContentItem> textItems = new ArrayList<ContentItem>();
		
		if(aYThreshold<0)
			aYThreshold = 1.5;
		
		String sLineSeparator = isCombineMultiLines?" ":"\n";
		
		int iSeqNo = 1;
		ContentItem prevText = null;
		for(ContentItem curText : aTextItems)
		{
			if(prevText==null)
			{
				prevText = curText;
				continue;
			}
			
			//different font style
			if(isMatchFontStyle && !prevText.getContentFormat().equalsIgnoreCase(curText.getContentFormat()))
			{
				prevText.setExtract_seq(iSeqNo++);
				textItems.add(prevText);
				prevText = curText;
				continue;
			}
			
			Rectangle2D prevBounds = prevText.getRect2D();
			
			double dExpansion = Math.min(curText.getHeight(), prevText.getHeight()) * aYThreshold;
			Rectangle2D prevExpanded = expandRect2D(prevBounds, dExpansion, dExpansion);
			
			if(prevExpanded.intersects(curText.getRect2D()))
			{
				String sCombinedText = prevText.getData() + sLineSeparator + curText.getData();
				Rectangle2D rectCombined = combineRect2Ds(prevText.getRect2D(), curText.getRect2D());
				prevText.setData(sCombinedText);
				prevText.setRect2D(rectCombined);
			}
			else
			{
				prevText.setExtract_seq(iSeqNo++);
				textItems.add(prevText);
				prevText = curText;
			}
			
		}
		
		if(prevText!=null)
			textItems.add(prevText);
		
		return textItems;
	}
	
	private static Rectangle2D expandRect2D(Rectangle2D rect, double aExpandW, double aExpandH)
	{
		return new Rectangle2D.Double(rect.getX(), rect.getY(), 
				rect.getWidth()+aExpandW, 
				rect.getHeight()+aExpandH);
	}

	private static Rectangle2D combineRect2Ds(Rectangle2D rect1, Rectangle2D rect2)
	{
		double dX1 = Math.min(rect1.getX(), rect2.getX());
		double dY1 = Math.min(rect1.getY(), rect2.getY());
		
		double dX2 = Math.max(rect1.getX()+rect1.getWidth(), rect2.getX()+rect2.getWidth());
		double dY2 = Math.max(rect1.getY()+rect1.getHeight(), rect2.getY()+rect2.getHeight());
		
		return new Rectangle2D.Double(dX1, dY1, dX2-dX1, dY2-dY1);
	}
	
	private static String getCommonFontStyle(List<TextPosition> aLineText)
	{
		if(aLineText==null || aLineText.size()==0)
			return null;
		
		PDFont firstFont 	= null;
		PDFont lastFont 	= null;
		
		TextPosition textFirst = null;
		
		int iListSize = aLineText.size()-1;
		//search first character
		for(int i=0; i<=iListSize; i++)
		{
			if(firstFont==null)
			{
				textFirst = aLineText.get(i);
				if(textFirst.getUnicode().trim().length()>0)
				{
					firstFont = textFirst.getFont();
				}
			}
			if(lastFont==null)
			{
				TextPosition text2 = aLineText.get(iListSize-i);
				if(text2.getUnicode().trim().length()>0)
				{
					lastFont = text2.getFont();
				}
			}
			
			if(lastFont!=null && firstFont!=null)
				break;
		}
		
		if(firstFont!=null && lastFont!=null)
		{
			String[] sFontNames = new String[] 
					{firstFont.getName().toLowerCase(), 
					lastFont.getName().toLowerCase()};
			
			for(int i=0; i<sFontNames.length; i++)
			{
				//Remove custom random font name prefix 
				if (sFontNames[i].contains("+")) {
					sFontNames[i] = sFontNames[i].substring(sFontNames[i].indexOf("+") + 1);
				}
			}
			
			if(sFontNames[0].equals(sFontNames[1]))
				return sFontNames[0]+" ("+textFirst.getFontSizeInPt()+")";
		}
		
		return null;
	}
}