package hl.doc.extractor.pdf.extraction.util;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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

	        @Override
	        protected void processTextPosition(TextPosition text) {
	            if (currentLine.isEmpty()) {
	                currentLine.add(text);
	                return;
	            }

	            TextPosition last = currentLine.get(currentLine.size() - 1);
	            // Check if same line (Y close enough)
	            if (Math.abs(last.getYDirAdj() - text.getYDirAdj()) < 3) {
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
	            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
	            float maxX = 0, maxY = 0;
	            StringBuffer sb = new StringBuffer();
	            for (TextPosition t : line) {
	                float x = t.getXDirAdj();
	                float y = t.getYDirAdj() - t.getHeightDir();
	                float w = t.getWidthDirAdj();
	                float h = t.getHeightDir();
	                minX = Math.min(minX, x);
	                minY = Math.min(minY, y);
	                maxX = Math.max(maxX, x + w);
	                maxY = Math.max(maxY, y + h);
	                
	                sb.append(t.getUnicode()); 
	            }
	            Rectangle2D rect2D = new Rectangle2D.Float(minX, minY, maxX - minX, maxY - minY);
	            contentItems.add(new ContentItem(Type.TEXT, sb.toString(), getCurrentPageNo(), rect2D));
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

	        ImagePositionEngine(PDPage page) { super(page); }

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
	            
	            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, sImgformat, baos);
                byte[] imageBytes = baos.toByteArray();
                String sImgContent = Base64.getEncoder().encodeToString(imageBytes);
                //
	            ContentItem item = new ContentItem(Type.IMAGE, sImgContent, pageIndex+1, rect);
	            item.setTagName(ContentUtil.TAGNAME_BASE64);
	            item.setContentFormat(sImgformat);
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
	        @Override public void shadingFill(org.apache.pdfbox.cos.COSName shadingName) throws IOException {}
	    }

	    ImagePositionEngine engine = new ImagePositionEngine(page);
	    engine.processPage(page);
	    return engine.contentItems;
	}
}