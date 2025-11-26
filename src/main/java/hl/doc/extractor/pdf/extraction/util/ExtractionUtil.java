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
	public static List<ContentItem> extractTextContent(PDDocument doc, int pageIndex) throws IOException {

		//Silent the missing font warning
    	Logger.getLogger("org.apache.pdfbox.pdmodel.font").setLevel(Level.SEVERE);

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
	            float tolerance = Math.max(hLast, hCurrent) * 0.6f;

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

	            Rectangle2D rect2D = new Rectangle2D.Float(minX, minY, maxX - minX, maxY - minY);
	            ContentItem textItem = new ContentItem(Type.TEXT, sData, getCurrentPageNo(), rect2D);
	            textItem.setExtract_seq(iExtractSeq++);
	            textItem.setContentFormat(sFormat);
	            contentItems.add(textItem);
	        }

	    }

	    GroupedTextStripper stripper = new GroupedTextStripper();
	    stripper.setAddMoreFormatting(true);
	    stripper.setSortByPosition(true);
	    stripper.setStartPage(pageIndex + 1);
	    stripper.setEndPage(pageIndex + 1);
	    stripper.getText(doc);

	    return stripper.contentItems;
	}
	
	private static String getCommonFontStyle(List<TextPosition> aLineText)
	{
		if(aLineText==null || aLineText.size()==0)
			return null;
		
		PDFont firstFont 	= null;
		PDFont lastFont 	= null;
		
		int iListSize = aLineText.size()-1;
		//search first character
		for(int i=0; i<=iListSize; i++)
		{
			if(firstFont==null)
			{
				TextPosition text1 = aLineText.get(i);
				if(text1.getUnicode().trim().length()>0)
				{
					firstFont = text1.getFont();
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
				return sFontNames[0];
		}
		
		return null;
	}


	// ---- IMAGE BOUNDING BOXES (Y-flipped to match BufferedImage coordinates) ----
	public static List<ContentItem> extractImageContent(PDDocument doc, int pageIndex) throws IOException {
		PDPage page = doc.getPage(pageIndex);
		
		float scale = 1.0f;
		double pageHeightPoints = page.getMediaBox().getHeight();
		
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
	            double flippedY = (pageHeightPoints - minY - height);

	            Rectangle2D rect = new Rectangle2D.Double(
	                minX * scale,
	                flippedY * scale,
	                width * scale,
	                height * scale
	            );
	            
	            ContentItem item = ContentUtil.imageToContentItem(
	            		pdImage.getImage(),  //Buffered Image
	            		pdImage.getSuffix(), //Image format
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
	      return extractVectorContent(doc, pageIndex, true);
	}
    public static List<ContentItem> extractVectorContent(PDDocument doc, int pageIndex, boolean isGroupVectors) throws IOException {
        
        // Silence verbose PDFBox logging
        Logger.getLogger("org.apache.pdfbox.contentstream.operator.graphics").setLevel(Level.SEVERE);

        PDPage page = doc.getPage(pageIndex);
        float pgWidth 	= page.getMediaBox().getWidth();
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
            	return aShapePath.getPathIterator(null).isDone();
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
                
                if (bounds.getWidth() > 0 && bounds.getHeight() > 0) 
                {
                	if(isSimilarColor(docBgColor,toAwtColor(pdColor),10))
                	{
                		//Drop since it's not visible to human
                		//System.out.println(" DROP "+currentPath);
                	}
                	else
                	{
                		//only store visible vectors, there are some vectors with -x and -y
                		if(bounds.getX() > 0 
                			&& bounds.getY() > 0 
                			&& bounds.getWidth()+bounds.getX() < pgWidth
                			&& bounds.getHeight()+bounds.getY() < pgHeight)
                		{
                			listVector.add(new GeneralPath(currentPath));
                		}
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
        	listVectors = groupByBounds(pageIndex, engine.listVector, 4);
        }
        //////////////////////////////////////////////////
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