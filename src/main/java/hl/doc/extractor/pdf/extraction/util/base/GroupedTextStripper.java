package hl.doc.extractor.pdf.extraction.util.base;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;

public class GroupedTextStripper extends PDFTextStripper {
	
    List<ContentItem> contentItems = new ArrayList<>();
    List<TextPosition> currentLine = new ArrayList<>();

    GroupedTextStripper() throws IOException {}
    int iExtractSeq = 1;
    
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
