package hl.doc.extractor.pdf.extraction.util.base;

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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.util.Matrix;

import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.VectorData;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;

public class VectorExtractUtil  {

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
        	listVectors = groupByBounds(pageIndex, listVectors, 4, 1.0f);
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

    public static List<Path2D> groupByBounds(int aPageIndex, List<Path2D> aOrigVectorList, int aExpandedPixel, float aMinIntersectThreshold)
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
        
        if(aMinIntersectThreshold<0)
        	aMinIntersectThreshold = 0f;
        
        if(aMinIntersectThreshold>1)
        	aMinIntersectThreshold = 1.0f;
        
        
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
        		
        		if(rect1.intersects(rect2))
        		{
	        		Rectangle2D rectIntersect = new Area(rect1.createIntersection(rect2)).getBounds();
	    			
	        		double dSizeRect = Math.min(rect1.getWidth()*rect1.getHeight(), rect2.getWidth()*rect2.getHeight());
	    			double dSizeIntersect = rectIntersect.getWidth() * rectIntersect.getHeight();
	    			
	    			float fPercentage = (float)(dSizeIntersect/dSizeRect);
	    			System.out.println("intersection="+fPercentage);
	        		if(fPercentage> aMinIntersectThreshold)
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