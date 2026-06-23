package hl.doc.extractor.pdf.extraction.util.base;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;

import hl.doc.extractor.pdf.extraction.model.ContentItem;

public class TextExtractUtil  {

	public static List<ContentItem> extractTextContent(
			PDDocument doc, int pageIndex, 
			boolean isGroupByParagraph) throws IOException {
		
		return extractTextContentByAreas(doc, pageIndex, null, isGroupByParagraph);
	}
	
	public static List<ContentItem> extractTextContentByAreas(
			PDDocument doc, int pageIndex, Map<String, Rectangle> mapAreasOfInterest,
			boolean isGroupByParagraph) throws IOException {

	    GroupedTextStripper stripper = new GroupedTextStripper();
	    stripper.setSortByPosition(true);
	    stripper.setStartPage(pageIndex + 1);
	    stripper.setEndPage(pageIndex + 1);
	    
	    if(mapAreasOfInterest!=null && mapAreasOfInterest.size()>0)
	    {
	    	for(String sLabel: mapAreasOfInterest.keySet())
	    	{
	    		Rectangle rect = mapAreasOfInterest.get(sLabel);
	    		stripper.addAreaOfInterest(sLabel, rect);
	    	}
	    }
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

}
