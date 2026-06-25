package hl.doc.extractor.pdf.extraction.util.base;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.util.Matrix;

import hl.common.ImgUtil;
import hl.doc.extractor.pdf.extraction.pojo.ContentItem;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;

public class ImageExtractUtil  {

	// ---- IMAGE BOUNDING BOXES (Y-flipped to match BufferedImage coordinates) ----
	public static List<ContentItem> extractImageContent(PDDocument doc, int pageIndex, boolean isResizeImage) throws IOException {
		PDPage page = doc.getPage(pageIndex);
		
		float scale = 1.0f;
		double pgHeight = page.getMediaBox().getHeight();
		double pgWidth 	= page.getMediaBox().getWidth();
		
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
	            double minX 	= ctm.getTranslateX();
	            double minY 	= ctm.getTranslateY();
	            double width 	= ctm.getScaleX() +1; //x start with 0
	            double height 	= ctm.getScaleY() +1; //y start with 0

	            // Flip Y for BufferedImage coordinates
	            double flippedY = (pgHeight - minY - height);
	            BufferedImage imgAdj = pdImage.getImage();
	            String imgFormat = pdImage.getSuffix();

	            int iX = (int)(minX * scale);
	            int iY = (int)(flippedY * scale);
	            int iW = (int)(width * scale);
	            int iH = (int)(height * scale);
	            
	            
	            if(isResizeImage)
	            {
	            	if(imgAdj.getWidth()>iW || imgAdj.getHeight()>iH)
	            	{
	            		imgAdj = ImgUtil.resizeImg(imgAdj, iW, iH, false);
	            	}
	            }
	            
	            // ---- Out-of-Bounds Image Adjustment ----
	            if(iX<0 || iY<0)
	            {
	            	// Adjust subimage extraction to prevent out-of-bounds access
	            	int srcX = Math.max(0, Math.abs(iX));
	            	int srcY = Math.max(0, Math.abs(iY));
	            	
	            	// Ensure subimage dimensions don't exceed source image bounds
	            	int srcWidth = Math.min(iW - srcX, imgAdj.getWidth() - srcX);
	            	int srcHeight = Math.min(iH - srcY, imgAdj.getHeight() - srcY);
	            	
	            	if(srcWidth > 0 && srcHeight > 0)
	            	{
	            		try {
	            			imgAdj = imgAdj.getSubimage(srcX, srcY, srcWidth, srcHeight);
	            		} catch (Exception e) {
	            			// Log and skip if subimage extraction fails
	            			System.err.println("Warning: Failed to extract subimage at (" + srcX + "," + srcY + 
	            					") with size (" + srcWidth + "x" + srcHeight + "): " + e.getMessage());
	            		}
	            	}
	            	
	            	// Reset coordinates to page origin
	            	if(iX<0) iX = 0;
	            	if(iY<0) iY = 0;
	            }
	            
	            // Clamp dimensions to page boundaries
	            if(iW + iX > pgWidth) {
	            	iW = (int)pgWidth - iX - 1;
	            }
	            if(iH + iY > pgHeight) {
	            	iH = (int)pgHeight - iY - 1;
	            }
	            
	            // Skip image if it's completely outside page bounds
	            if(iW <= 0 || iH <= 0)
	            {
	            	System.err.println("Warning: Image skipped - completely out of page bounds at (" + iX + "," + iY + ")");
	            	return;
	            }
	            
	            Rectangle2D rect = new Rectangle2D.Double(iX, iY, iW, iH);
	            
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
}
