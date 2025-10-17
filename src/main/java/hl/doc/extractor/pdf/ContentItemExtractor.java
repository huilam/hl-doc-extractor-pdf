package hl.doc.extractor.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

import hl.doc.extractor.pdf.model.ContentItem;
import hl.doc.extractor.pdf.model.ContentItem.Type;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;

public class ContentItemExtractor {

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
                String sImgContent = "image/"+sImgformat+";base64,"+Base64.getEncoder().encodeToString(imageBytes);

	            ContentItem item = new ContentItem(Type.IMAGE, sImgContent, 1, rect);
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
	
	public static void main(String[] args) throws Exception {
	   
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-DD_HHmm-SS.sss");
        String sExecID = df.format(System.currentTimeMillis()); 
        
		File folderInput 	= new File("./test/");
		String sOutputFolder = folderInput.getAbsoluteFile()+"/"+sExecID;
       

		for(File f : folderInput.listFiles())
		{
			if(f.getName().toLowerCase().endsWith(".pdf"))
			{
				PDDocument doc = Loader.loadPDF(f);
				File folderOutput 	= new File(sOutputFolder+"/"+f.getName());
				folderOutput.mkdirs();
				System.out.println("Processing "+f.getName()+" ...");

		        PDFRenderer renderer = new PDFRenderer(doc);

		        for (int pageIndex = 0; pageIndex < doc.getNumberOfPages(); pageIndex++) {
		            PDPage page = doc.getPage(pageIndex);

		            // Extract text bounding boxes
		            List<ContentItem> textBoxes = extractTextContent(doc, pageIndex);

		            // Extract image bounding boxes
		            List<ContentItem> imageBoxes = extractImageContent(doc, pageIndex);

		            // Render page
		            BufferedImage image = renderer.renderImage(pageIndex);
		            
		            // --- Create blank image ---
		            float pageWidth = page.getMediaBox().getWidth();
		            float pageHeight = page.getMediaBox().getHeight();
		            int imgWidth = (int) Math.ceil(pageWidth);  // 1 point = 1 pixel for simplicity
		            int imgHeight = (int) Math.ceil(pageHeight);

		            image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);

		            // Fill white background
		            Graphics2D g = null;
		            
		            try {
			            g = image.createGraphics();
			            g.setColor(Color.WHITE);
			            g.fillRect(0, 0, imgWidth, imgHeight);
			            g.setColor(Color.BLACK); 
			            g.draw(new Rectangle2D.Float(0,0,(image.getWidth()-1f),(image.getHeight())-1f));
	
			           
			            g.setColor(Color.RED); // red for text
			            FontMetrics fm = g.getFontMetrics();
			            for (ContentItem item : textBoxes) {
			                g.draw(item.getRect2D());
			                
			                float textX = (float) item.getX1();
			                float textY = (float) item.getY1() + fm.getAscent();
			                g.drawString(item.getContent(), textX, textY);
			            }
	
			            g.setColor(Color.BLUE); // blue for images
			            for (ContentItem item : imageBoxes) {
			            	 g.draw(item.getRect2D());
			            }
		            }finally
		            {
		            	if(g!=null)
		            		g.dispose();
		            }

		            String sOutputFileName = "pg_" + (pageIndex + 1) + "_layout.jpg";
		            File fileOutput = new File(folderOutput.getAbsolutePath()+"/"+sOutputFileName);
		            ImageIO.write(image, "JPG", fileOutput);
		            System.out.println("Saved: "+fileOutput.getAbsolutePath());
		        }

		        doc.close();
			}
		}
    }
}