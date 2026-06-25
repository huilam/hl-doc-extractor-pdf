package hl.doc.extractor.pdf.extraction.util;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import hl.doc.extractor.pdf.extraction.pojo.ContentItem;
import hl.doc.extractor.pdf.extraction.util.base.ImageExtractUtil;
import hl.doc.extractor.pdf.extraction.util.base.TextExtractUtil;
import hl.doc.extractor.pdf.extraction.util.base.VectorExtractUtil;

public class ExtractionUtil  {
	
	// ---- TEXT BOUNDING BOXES ----
	public static List<ContentItem> extractTextContent(PDDocument doc, int pageIndex) throws IOException 
	{
		return TextExtractUtil.extractTextContent(doc, pageIndex);
	}
	
	public static List<ContentItem> extractTextContentByAreas(PDDocument doc, int pageIndex, 
			Map<String, Rectangle> mapAreasOfInterest) throws IOException 
	{
		return TextExtractUtil.extractTextContentByAreas(doc, pageIndex, mapAreasOfInterest);
	}

	// ---- IMAGE BOUNDING BOXES (Y-flipped to match BufferedImage coordinates) ----
	public static List<ContentItem> extractImageContent(PDDocument doc, int pageIndex) throws IOException 
	{
		return extractImageContent(doc, pageIndex, false);
	}
	public static List<ContentItem> extractImageContent(PDDocument doc, int pageIndex, boolean isResizeImage) throws IOException 
	{
		return ImageExtractUtil.extractImageContent(doc, pageIndex, isResizeImage);
	}
		

	// ---- Drawing (Rectangle, Line etc) -----
    public static List<ContentItem> extractVectorContent(PDDocument doc, int pageIndex) throws IOException 
    {
    	
    	return extractVectorContent(doc, pageIndex, true);
	}
    
    public static List<ContentItem> extractVectorContent(PDDocument doc, int pageIndex, boolean isGroupVectors) throws IOException 
    {
    	
    	return VectorExtractUtil.extractVectorContent(doc, pageIndex, isGroupVectors);
	}

    
}