package hl.doc.extractor.pdf.extraction.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.VectorData;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;

public class ExtractionUtil  {
	
	// ---- TEXT BOUNDING BOXES ----
	public static List<ContentItem> extractTextContent(PDDocument doc, int pageIndex) throws IOException 
	{
		return extractTextContent(doc, pageIndex, true);
	}
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
		return groupVerticalText(aTextItems, 1.5, true);
	}
	private static List<ContentItem> groupVerticalText(List<ContentItem> aTextItems, double aYThreshold, boolean isMatchFontStyle)
	{
		List<ContentItem> textItems = new ArrayList<ContentItem>();
		
		if(aYThreshold<0)
			aYThreshold = 1.5;
		
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
				String sCombinedText = prevText.getData()+"\n"+curText.getData();
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


	// ---- IMAGE BOUNDING BOXES (Y-flipped to match BufferedImage coordinates) ----
	public static List<ContentItem> extractImageContent(PDDocument doc, int pageIndex) throws IOException {
		PDPage page = doc.getPage(pageIndex);
		
		float scale = 1.0f;
		double pgHeight = page.getMediaBox().getHeight();
		//double pgWidth 	= page.getMediaBox().getWidth();
		
	    class ImagePositionEngine extends PDFGraphicsStreamEngine {
	        final List<ContentItem> contentItems = new ArrayList<>();
	        int iExtractSeq = 1;

	        ImagePositionEngine(PDPage page) { 
	        	super(page); 
	        	Logger.getLogger("org.apache.pdfbox.contentstream.operator.graphics").setLevel(Level.SEVERE);
	        }

	        @Override
	        public void drawImage(PDImage pdImage) throws IOException {
	            PDGraphicsState gs = getGraphicsState();
	            Matrix ctm = gs.getCurrentTransformationMatrix(); // image → user-space

	            // Compute bounding box from CTM without multiplying by image pixel size
	            double minX = ctm.getTranslateX();
	            double minY = ctm.getTranslateY();
	            double width = ctm.getScaleX();
	            double height = ctm.getScaleY();

	            // Flip Y for BufferedImage coordinates
	            double flippedY = (pgHeight - minY - height);
	            BufferedImage imgAdj = pdImage.getImage();
	            String imgFormat = pdImage.getSuffix();

	            int iX = (int)(minX * scale);
	            int iY = (int)(flippedY * scale);
	            int iW = (int)(width * scale);
	            int iH = (int)(height * scale);
	            
	            /**
	             * Out-of-Bound Image Adjustment
	             * 
	            if(iX<0 || iY<0)
	            {
	            	imgAdj = imgAdj.getSubimage(Math.abs(iX), Math.abs(iY), iW, iH);
	            	
		            if(iX<0) iX = 0;
		            if(iY<0) iY = 0;
	            }
	            if(iW+iX>pgWidth) iW = (int)pgWidth-iX-1;
	            if(iH+iY>pgHeight) iH = (int)pgHeight-iY-1;;
	            **/
	            
	            Rectangle2D rect = new Rectangle2D.Double(iX,iY,iW,iH);
	            
	            ContentItem item = ContentUtil.imageToContentItem(
	            		imgAdj,  //Buffered Image
	            		imgFormat, //Image format
	            		pageIndex+1, rect);
	            item.setExtract_seq(iExtractSeq++);
	            //
	            contentItems.add(item);
	        }

	        // ---- Required no-op overrides ----
	        @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {}
	        @Override public void clip(int windingRule) {}
	        @Override public void moveTo(float x, float y) {}
	        @Override public void lineTo(float x, float y) {}
	        @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {}
	        @Override public Point2D getCurrentPoint() { return null; }
	        @Override public void closePath() {}
	        @Override public void endPath() {}
	        @Override public void strokePath() {}
	        @Override public void fillPath(int windingRule) {}
	        @Override public void fillAndStrokePath(int windingRule) {}
	        @Override public void shadingFill(COSName shadingName) throws IOException {}
	    }

	    ImagePositionEngine engine = new ImagePositionEngine(page);
	    engine.processPage(page);
	    return engine.contentItems;
	}
	
	// ---- Drawing (Rectangle, Line etc) -----
	public static List<ContentItem> extractVectorContent(PDDocument doc, int pageIndex) throws IOException {
	      return extractVectorContent(doc, pageIndex, false);
	}
    public static List<ContentItem> extractVectorContent(PDDocument doc, int pageIndex, boolean isGroupVectors) throws IOException {
        
        // Silence verbose PDFBox logging
        Logger.getLogger("org.apache.pdfbox.contentstream.operator.graphics").setLevel(Level.SEVERE);

        PDPage page = doc.getPage(pageIndex);
        //float pgWidth 	= page.getMediaBox().getWidth();
        float pgHeight 	= page.getMediaBox().getHeight();

        class DrawingPositionEngine extends PDFGraphicsStreamEngine {

            final List<Path2D> listVector 	= new ArrayList<>();
            private Path2D currentPath 		= new GeneralPath();
            private Color docBgColor 		= null;

            DrawingPositionEngine(PDPage page) {
                super(page);
            }

            @Override public void moveTo(float x, float y) 
            { 
            	currentPath.moveTo(x, pgHeight - y); 
            }
            @Override public void lineTo(float x, float y) 
            { 
            	currentPath.lineTo(x, pgHeight - y); 
            }
            @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) 
            {
            	y1 = pgHeight - y1;
            	y2 = pgHeight - y2;
            	y3 = pgHeight - y3;
                currentPath.curveTo(x1, y1, x2, y2, x3, y3); 
            }
            
            @Override public void closePath() { 
            	currentPath.closePath(); 
            }
            
            @Override public Point2D getCurrentPoint() 
            { 	
            	Point2D pt = currentPath.getCurrentPoint();
            	pt.setLocation(pt.getX(), pgHeight - pt.getY());
            	return pt; 
            }
            @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) 
            {
                moveTo((float)p0.getX(), (float)p0.getY());
                lineTo((float)p1.getX(), (float)p1.getY());
                lineTo((float)p2.getX(), (float)p2.getY());
                lineTo((float)p3.getX(), (float)p3.getY());
                closePath();
            }
            
            @Override public void strokePath() { savePath(true, false); }
            @Override public void fillPath(int windingRule) { savePath(false, true); }
            @Override public void fillAndStrokePath(int windingRule) { savePath(true, true); }
            //
            @Override public void endPath() { currentPath.reset(); }

            private boolean isEmpty(Path2D aShapePath)
            {
            	return aShapePath!=null && aShapePath.getPathIterator(null).isDone();
            }
            private void savePath(boolean stroked, boolean filled) {
            	 PDGraphicsState gs = getGraphicsState();
            	 PDColor pdColor = filled ? gs.getNonStrokingColor() : gs.getStrokingColor();
            	
            	if(docBgColor==null)
            	{
            		docBgColor = filled ? toAwtColor(pdColor) : Color.WHITE;
            	}
            	
            	//check if empty
                if (isEmpty(currentPath)) return;

                Matrix ctm = gs.getCurrentTransformationMatrix();

                // Transform to PDF User Space
                Shape transformedShape = currentPath.createTransformedShape(ctm.createAffineTransform());

                // Handle Line Thickness
                if (stroked) {
                    BasicStroke stroke = new BasicStroke(gs.getLineWidth());
                    transformedShape = stroke.createStrokedShape(transformedShape);
                }

                Rectangle2D bounds = transformedShape.getBounds2D();
                
                if (bounds.getWidth() > 0 || bounds.getHeight() > 0) 
                {
                	if(isSimilarColor(docBgColor,toAwtColor(pdColor),10))
                	{
                		//Drop since it's not visible to human
                		//System.out.println(" DROP "+currentPath);
                	}
                	else
                	{
                		listVector.add(new GeneralPath(currentPath));
                	}

                }
                currentPath.reset();
            }
            
    		private Color toAwtColor(PDColor aPDColor) {
    			Color color = null;
    			if(aPDColor!=null)
    			{
	    			PDColorSpace csStroke 	= aPDColor.getColorSpace();
	            	try {
	            		float[] rgb = csStroke.toRGB(aPDColor.getComponents());
	            		color = new Color(rgb[0], rgb[1], rgb[2]);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
            	return color;
    		}
    		
    		private boolean isSimilarColor(Color aColor1, Color aColor2, int aTolerance) {
    			
    			if(aColor1!=null && aColor2!=null)
    			{
	    			int iRDiff = Math.abs(aColor1.getRed() - aColor2.getRed());
	    			int iGDiff = Math.abs(aColor1.getGreen() - aColor2.getGreen());
	    			int iBDiff = Math.abs(aColor1.getBlue() - aColor2.getBlue());
	    			return (iRDiff<aTolerance 
	    					&& iGDiff<aTolerance 
	    					&& iBDiff <aTolerance);
    			}
            	return false;
    		}
            
            @Override public void clip(int windingRule) {}
            @Override public void shadingFill(COSName shadingName) throws IOException {}
            @Override public void drawImage(PDImage pdImage) throws IOException {}
        }

        DrawingPositionEngine engine = new DrawingPositionEngine(page);
        engine.processPage(page);
        
        List<Path2D> listVectors = engine.listVector;
        
        if(isGroupVectors)
        {
        	listVectors = groupByBounds(pageIndex, listVectors, 4);
        }
        
        System.out.println("listVectors.size ==> "+listVectors.size());
        //////////////////////////////////////////////////
        ///
        // Convert List<GeneralPath> to List<ContentItem>
        int iExtractSeq = 1;
        List<ContentItem> contentItems = new ArrayList<>();
        for(Path2D vector : listVectors)
        {
        	VectorData vData = new VectorData(vector);
	        ContentItem item = new ContentItem(Type.VECTOR, vData.toJson().toString(), 
	        		pageIndex + 1, vector.getBounds2D());
	        item.setContentFormat(VectorData.class.getName());
	        item.setExtract_seq(iExtractSeq++);
	        contentItems.add(item);
        }
        
        System.out.println("contentItems.size ==> "+contentItems.size());
        return contentItems;
    }
    
    private static List<Path2D> groupByBounds(int aPageIndex, List<Path2D> aOrigVectorList, int aExpandedPixel)
    {
        List<Path2D> listVectors = new ArrayList<>();
        
        //Make the aExpandedPixel even number
        if(aExpandedPixel%2==1)
        	aExpandedPixel++;
        
        Map<Double, List<Path2D>> mapSortedVectors = new TreeMap<>();
        for(Path2D p : aOrigVectorList)
        {
        	Rectangle rect = p.getBounds();
        	double dArea = rect.getWidth() * rect.getHeight();
        	
        	List<Path2D> listAreaSize = mapSortedVectors.get(dArea);
        	if(listAreaSize==null)
        		listAreaSize = new ArrayList<>();
        	listAreaSize.add(p);
        	
        	mapSortedVectors.put(dArea, listAreaSize);
        }
        
        //Flatten Sorted Map<Doible,List<Vectors>> to 2D Vectors[]
        List<Path2D> listFlattenSortedVectors = new ArrayList<>();
        for(List<Path2D> listSortedVectors : mapSortedVectors.values())
        {
        	listFlattenSortedVectors.addAll(listSortedVectors);
        }
        
        Path2D[] vectors = listFlattenSortedVectors.toArray(new Path2D[listFlattenSortedVectors.size()]);
        
        for(int i=vectors.length-1; i>=0; i--)
        {
        	if(vectors[i]==null)
				continue;
        	
        	for(int z=0; z<i; z++)
        	{
        		if(vectors[z]==null)
    				continue;
            	
        		Rectangle2D rect1	= vectors[i].getBounds();
        		Rectangle2D rect2 	= vectors[z].getBounds();
        		if(rect1.intersects(rect2.getBounds2D()))
                {
        			Rectangle2D rectIntersect = new Area(rect1.createIntersection(rect2)).getBounds();
        			double dAreaRect = rect2.getWidth() * rect2.getHeight();
        			double dAreaIntersect = rectIntersect.getWidth() * rectIntersect.getHeight();
        			double dPercentage = dAreaIntersect/dAreaRect;
        			if(dPercentage>0.6)
        			{
	        			vectors[i].append(vectors[z], false);
	        			vectors[z] = null;
        			}
             	}
        	 }
        	
        	listVectors.add(vectors[i]);
        }
        
        return listVectors;
    }
    
    
    public static int countSegment(final Path2D aVectorPath)
	{
		PathIterator iterPath = aVectorPath.getPathIterator(null);
		int iSegCount = 0;
		while(!iterPath.isDone())
		{
			double[] coord = new double[6]; //CUBICTO required 3 points of x,y
			
			switch(iterPath.currentSegment(coord))
			{
				case PathIterator.SEG_LINETO:
				case PathIterator.SEG_CUBICTO:
				case PathIterator.SEG_QUADTO:
				case PathIterator.SEG_CLOSE:
					iSegCount++;
					break;
				case PathIterator.SEG_MOVETO:;
				default:
			}
			iterPath.next();
		}
		return iSegCount;
	}
    
    public static boolean isBoundingBox(final Path2D aVectorPath, int aMinLength)
    {
    	return isBoundingBox(aVectorPath, aMinLength, aMinLength);
    }
    
    public static boolean isBoundingBox(final Path2D aVectorPath, int aMinWidth, int aMinHeight)
    {
		Area areaPath = new Area(aVectorPath);
		return  aVectorPath.getBounds().getWidth()>aMinWidth 
				&& aVectorPath.getBounds().getHeight()>aMinHeight
				&& areaPath.isRectangular();
    }

}