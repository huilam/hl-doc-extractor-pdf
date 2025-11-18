package hl.doc.extractor.pdf.extraction.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDPattern;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;

public class ExtractionUtil  {

	// ---- TEXT BOUNDING BOXES ----
	public static List<ContentItem> extractTextContent(PDDocument doc, int pageIndex) throws IOException {

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

	            Rectangle2D rect2D = new Rectangle2D.Float(minX, minY, maxX - minX, maxY - minY);
	            ContentItem textItem = new ContentItem(Type.TEXT, sb.toString(), getCurrentPageNo(), rect2D);
	            textItem.setExtract_seq(iExtractSeq++);
	            contentItems.add(textItem);
	        }

	    }

	    GroupedTextStripper stripper = new GroupedTextStripper();
	    stripper.setStartPage(pageIndex + 1);
	    stripper.setEndPage(pageIndex + 1);
	    stripper.getText(doc);

	    return stripper.contentItems;
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
	            
	            BufferedImage img = pdImage.getImage();
	            String sImgformat = pdImage.getSuffix(); 
	            String sImgContent = null;
	            ByteArrayOutputStream baos = null;
	            try {
		            baos = new ByteArrayOutputStream();
	                ImageIO.write(img, sImgformat, baos);
	                sImgContent = Base64.getEncoder().encodeToString(baos.toByteArray());
	            }
	            finally
	            {
	            	if(baos!=null)
	            	{
	            		baos.close();
	            	}
	            }
                //
	            ContentItem item = new ContentItem(Type.IMAGE, sImgContent, pageIndex+1, rect);
	            item.setTagName(ContentUtil.TAGNAME_BASE64);
	            item.setContentFormat(sImgformat);
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
	
	// ---- Drawing (Rectangle, Line) -----
    public static List<ContentItem> extractVectorContent(PDDocument doc, int pageIndex) throws IOException {
        
        PDPage page = doc.getPage(pageIndex);

        class DrawingPositionEngine extends PDFGraphicsStreamEngine {

            final List<ContentItem> contentItems = new ArrayList<>();
            private GeneralPath currentPath = new GeneralPath();
            private boolean pathIsEmpty = true;
            int iExtractSeq = 1;

            DrawingPositionEngine(PDPage page) {
                super(page);
                // Silence verbose PDFBox logging
                Logger.getLogger("org.apache.pdfbox.contentstream.operator.graphics").setLevel(Level.SEVERE);
            }

            @Override public void moveTo(float x, float y) { currentPath.moveTo(x, y); pathIsEmpty = false; }
            @Override public void lineTo(float x, float y) { currentPath.lineTo(x, y); pathIsEmpty = false; }
            @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) 
            {
                currentPath.curveTo(x1, y1, x2, y2, x3, y3); pathIsEmpty = false;
            }
            @Override public void closePath() { currentPath.closePath(); }
            @Override public Point2D getCurrentPoint() { return currentPath.getCurrentPoint(); }
            @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) 
            {
                currentPath.moveTo(p0.getX(), p0.getY());
                currentPath.lineTo(p1.getX(), p1.getY());
                currentPath.lineTo(p2.getX(), p2.getY());
                currentPath.lineTo(p3.getX(), p3.getY());
                currentPath.closePath();
                pathIsEmpty = false;
            }
            
            @Override public void strokePath() { savePath(true, false); }
            @Override public void fillPath(int windingRule) { savePath(false, true); }
            @Override public void fillAndStrokePath(int windingRule) { savePath(true, true); }
            @Override public void endPath() { currentPath.reset(); pathIsEmpty = true; }

            private void savePath(boolean stroked, boolean filled) {
                if (pathIsEmpty) return;

                PDGraphicsState gs = getGraphicsState();
                Matrix ctm = gs.getCurrentTransformationMatrix();

                // Transform to PDF User Space
                Shape transformedShape = currentPath.createTransformedShape(ctm.createAffineTransform());

                // Handle Line Thickness
                if (stroked) {
                    BasicStroke stroke = new BasicStroke(gs.getLineWidth());
                    transformedShape = stroke.createStrokedShape(transformedShape);
                }

                Rectangle2D bounds = transformedShape.getBounds2D();
                
                if (bounds.getWidth() > 0 && bounds.getHeight() > 0) {
                	String sData = ContentUtil.vectorPathToString(currentPath);
                    ContentItem item = new ContentItem(Type.VECTOR, sData, pageIndex + 1, bounds);
                    item.setContentFormat(GeneralPath.class.getName());
                    
                    StringBuffer sbTagInfo = new StringBuffer();
                    
                    sbTagInfo.append("stroke.rgb:[");
                    if (stroked) {
                    	Color colorStroke = toRGB(gs.getStrokingColor());
                    	if(colorStroke!=null)
                    	{
	                    	sbTagInfo.append(colorStroke.getRed()).append(",");
	                    	sbTagInfo.append(colorStroke.getGreen()).append(",");
	                    	sbTagInfo.append(colorStroke.getBlue());
                    	}
                    }
                    sbTagInfo.append("]");
                    sbTagInfo.append(",fill.rgb:[");
                    if (filled) {
                    	Color colorFill = toRGB(gs.getNonStrokingColor());
                    	if(colorFill!=null)
                    	{
	                    	sbTagInfo.append(colorFill.getRed()).append(",");
	                    	sbTagInfo.append(colorFill.getGreen()).append(",");
	                    	sbTagInfo.append(colorFill.getBlue());
                    	}
                    }
                    sbTagInfo.append("]");
                    
                    item.setTagName(sbTagInfo.toString());
                    item.setExtract_seq(iExtractSeq++);
                    contentItems.add(item);
                }
                currentPath.reset();
                pathIsEmpty = true;
            }
            
            @Override public void clip(int windingRule) {}
            @Override public void shadingFill(COSName shadingName) throws IOException {}
            @Override public void drawImage(PDImage pdImage) throws IOException {}
            
            private Color toRGB(PDColor aPDColor)
            {
                float[] rgb = new float[3];
                PDColorSpace cs = aPDColor.getColorSpace();
                
                if (!(cs instanceof PDPattern)) {
	                try {
	                	rgb = cs.toRGB(aPDColor.getComponents());
						return new Color(rgb[0], rgb[1], rgb[2]);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                }
                return null;
            }
            
        }

        
        DrawingPositionEngine engine = new DrawingPositionEngine(page);
        engine.processPage(page);
        return engine.contentItems;
    }

}