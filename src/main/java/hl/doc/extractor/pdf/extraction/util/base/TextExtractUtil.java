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

	    // Silence the missing font warning
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

	            // --- 2) Bounding box extremes ---
	            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
	            float maxX = 0, maxY = 0;
	            StringBuffer sb = new StringBuffer();
	            
	            // Track the horizontal end position of the previous character
	            float lastXEnd = -1f;
	            
	            for (TextPosition t : line) {
	                float x = t.getXDirAdj();
	                float w = t.getWidthDirAdj();

	                // --- Detect missing word spaces using horizontal gaps ---
	                if (lastXEnd != -1f) {
	                    float gap = x - lastXEnd;
	                    
	                    // Fallback to a sensible default if the font doesn't specify a space width
	                    float spaceWidthThreshold = t.getWidthOfSpace();
	                    if (spaceWidthThreshold <= 0) {
	                        spaceWidthThreshold = t.getWidthDirAdj() * 0.5f; 
	                    } else {
	                        spaceWidthThreshold = spaceWidthThreshold * 0.5f; // 50% of a standard space
	                    }

	                    // If the gap is wide enough and the text doesn't already end/start with a space
	                    if (gap > spaceWidthThreshold && sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ' && !t.getUnicode().startsWith(" ")) {
	                        sb.append(" ");
	                    }
	                }

	                // --- 3) NORMALIZED baseline for bounding box only ---
	                float normalizedBaseline = mainBaseline;

	                // --- 4) NORMALIZED height for bounding box only ---
	                float normalizedHeight = maxHeight;

	                float yTop = normalizedBaseline - normalizedHeight;

	                minX = Math.min(minX, x);
	                minY = Math.min(minY, yTop);
	                maxX = Math.max(maxX, x + w);
	                maxY = Math.max(maxY, normalizedBaseline);

	                sb.append(t.getUnicode());
	                
	                // Update the end pointer for the next character comparison
	                lastXEnd = x + w;
	            }
	            
	            String sData = sb.toString();
	            
	            // --- OPTION 1 FIX: Handle line breaks ---
	            // Append a trailing space if the extracted line text doesn't already end with one.
	            // (If you prefer an actual line break character instead of a space, change " " to "\n")
	            if (sData.length() > 0 && !sData.endsWith(" ")) {
	                sData += " ";
	            }

	            String sFormat = null;
	            if(sData.trim().length() > 0)
	            {
	                sFormat = getCommonFontStyle(line);
	            }

	            double dX = minX;
	            double dY = minY;
	            double dW = maxX - minX;
	            double dH = maxY - minY;
	            
	            // Assumption: empty out of view bounding box 
	            if(dX < 0) dX = 0;
	            if(dY < 0) dY = 0;
	            
	            Rectangle2D rect2D = new Rectangle2D.Double(dX, dY, dW, dH);
	            
	            ContentItem textItem = new ContentItem(Type.TEXT, sData, getCurrentPageNo(), rect2D);
	            textItem.setExtract_seq(iExtractSeq++);
	            textItem.setContentFormat(sFormat);
	            contentItems.add(textItem);
	        }

	    }

	    GroupedTextStripper stripper = new GroupedTextStripper();
	    stripper.setSortByPosition(true);
	    stripper.setStartPage(pageIndex + 1);
	    stripper.setEndPage(pageIndex + 1);
	    stripper.getText(doc);

	    List<ContentItem> textItems = stripper.contentItems;
	    
	    if(isGroupByParagraph)
	        textItems = groupTextByParagraph(textItems);
	    
	    return textItems;
	}
	
	// ---- Helper function to count words in a line ----
	private static int countWords(String text)
	{
		if(text == null || text.trim().length() == 0)
			return 0;
		return text.trim().split("\\s+").length;
	}
	
	private static List<ContentItem> groupTextByParagraph(List<ContentItem> aTextItems)
	{
		return groupVerticalText(aTextItems, 1.4, true, true);
	}

	private static List<ContentItem> groupVerticalText(List<ContentItem> aTextItems, double aYThreshold, boolean isMatchFontStyle, boolean isCombineMultiLines)
	{
	    List<ContentItem> textItems = new ArrayList<ContentItem>();
	    
	    if (aTextItems == null || aTextItems.isEmpty())
	        return textItems;
	        
	    if (aYThreshold < 0)
	        aYThreshold = 1.5;
	    
	    String sLineSeparator = isCombineMultiLines ? " " : "\n";
	    
	    int iSeqNo = 1;
	    ContentItem prevText = null;
	    
	    for (ContentItem curText : aTextItems)
	    {
	        if (prevText == null)
	        {
	            prevText = curText;
	            continue;
	        }
	        
	        // 1. Check different font styles
	        if (isMatchFontStyle && !prevText.getContentFormat().equalsIgnoreCase(curText.getContentFormat()))
	        {
	            prevText.setExtract_seq(iSeqNo++);
	            textItems.add(prevText);
	            prevText = curText;
	            continue;
	        }
	        
	        Rectangle2D prevBounds = prevText.getRect2D();
	        Rectangle2D curBounds = curText.getRect2D();
	        
	        double dExpansion = Math.min(curText.getHeight(), prevText.getHeight()) * aYThreshold;
	        Rectangle2D prevExpanded = expandRect2D(prevBounds, dExpansion, dExpansion);
	        
	        // 2. Check physical geometric intersection
	        if (prevExpanded.intersects(curBounds))
	        {
	            // Calculate hypothetical combined text
	            String sCombinedText = prevText.getData() + sLineSeparator + curText.getData();
	            
	            // --- FIX FOR INDEXES (3, 4, 5, 6) ---
	            // Instead of evaluating individual sub-sentences strictly, we look at whether
	            // the WHOLE accumulating block has substantial sentence content (> 3 words total)
	            // AND ensure we normalize wide spaces inside the check so formatting doesn't break it.
	            int totalWordCount = countWords(sCombinedText);
	            boolean hasWideGap = containsInvalidSpacing(sCombinedText);
	            
	            if (totalWordCount > 3 && !hasWideGap)
	            {
	                // Approved: This forms an active paragraph block containing an index and a sentence.
	                Rectangle2D rectCombined = combineRect2Ds(prevBounds, curBounds);
	                prevText.setData(sCombinedText);
	                prevText.setRect2D(rectCombined);
	            }
	            else
	            {
	                // Denied: Disconnect if it's just sequential short numbers (e.g., "3" intersecting "4")
	                // without any main text body, or if it spans entirely different columns (wide gaps).
	                prevText.setExtract_seq(iSeqNo++);
	                textItems.add(prevText);
	                prevText = curText;
	            }
	        }
	        else
	        {
	            prevText.setExtract_seq(iSeqNo++);
	            textItems.add(prevText);
	            prevText = curText;
	        }
	    }
	    
	    if (prevText != null)
	    {
	        prevText.setExtract_seq(iSeqNo++);
	        textItems.add(prevText);
	    }
	    
	    return textItems;
	}

	/**
	 * Validates if a text sequence contains uncomfortably massive wide gaps 
	 * (3+ spaces) within actual textual phrases, ignoring structural newlines.
	 */
	private static boolean containsInvalidSpacing(String text) {
	    if (text == null) return false;
	    // Split by lines first to ensure we only check horizontal spacing within lines
	    String[] lines = text.split("[\\n\\r]+");
	    for (String line : lines) {
	        if (line.trim().matches(".*\\s{3,}.*")) {
	            return true;
	        }
	    }
	    return false;
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
		TextPosition textLast = null;
		
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
				textLast = aLineText.get(iListSize-i);
				if(textLast.getUnicode().trim().length()>0)
				{
					lastFont = textLast.getFont();
				}
			}
			
			if(lastFont!=null && firstFont!=null)
				break;
		}
		
		if(firstFont!=null && lastFont!=null)
		{
			String[] sFontNames = new String[] 
					{firstFont.getName(), lastFont.getName()};
			
			for(int i=0; i<sFontNames.length; i++)
			{
				//Remove custom random font name prefix 
				if (sFontNames[i]!=null && sFontNames[i].contains("+")) {
					sFontNames[i] = sFontNames[i].substring(sFontNames[i].indexOf("+") + 1);
				}
			}
			
			if(sFontNames[0]!=null)
			{
				if(sFontNames[0].equals(sFontNames[1]))
					return sFontNames[0]+" ("+textFirst.getFontSizeInPt()+")";
			}
			else if(textFirst.getFontSizeInPt() == textLast.getFontSizeInPt())
			{
				return "unknown ("+textFirst.getFontSizeInPt()+")";
			}
			
				
		}
		
		return null;
	}
}
