package hl.ml.djl.detection.docs.layout.paddle;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.json.JSONArray;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.repository.zoo.Criteria;
import ai.djl.translate.TranslateException;
import hl.ml.djl.AbtractDjlBaseImpl;
import hl.ml.djl.DjlConstants;
import hl.ml.djl.DjlModelConfig;

public class PPDocLayout extends AbtractDjlBaseImpl <Image, DetectedObjects>{
	
	public PPDocLayout()
	{
		DjlModelConfig config = new DjlModelConfig();
		//
		config.setModel_name("pp-doclayoutv3");
		config.setRuntime_engine(DjlConstants.RT_ENGINE_ONNX);
		config.setTranslator_factory(new PPStructureLayoutTranslatorFactory());
		
		super(
			PPDocLayout.class, 
			config, 
			Criteria.builder().setTypes(Image.class, DetectedObjects.class));
		
		super.loadModel();
	}
	
	public JSONArray getDocLayoutInJson(BufferedImage aImage) throws IOException
	{
		Image inputImage = ImageFactory.getInstance().fromImage(aImage);
		if(inputImage!=null)
			return getDocLayoutInJson(inputImage);
		else
			return null;
	}	
	
	public JSONArray getDocLayoutInJson(Image aInputImage) throws IOException
	{
		JSONArray jsonData = new JSONArray();
		DetectedObjects detection = detectDocLayout(aInputImage);
		if(detection!=null)
		{
			jsonData = new JSONArray(detection.toJson());
		}
		return jsonData;
	} 
	
	public DetectedObjects detectDocLayout(Image aInputImage) throws IOException
	{
		DetectedObjects detection = null;
		try {
			detection = this.predictor.predict(aInputImage);
		} catch (TranslateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return detection;
	} 
	
	public void destroy()
	{
		if(this.predictor!=null)
			this.predictor.close();
		
		if(this.model!=null)
			this.model.close();
	}
	
	public static void annotateImage(File originalFile, DetectedObjects detections, String outputFilePath) throws IOException {
        // 1. Read the original image into a standard Java BufferedImage
        BufferedImage img = ImageIO.read(originalFile);
        Graphics2D g2d = null;
        
        try {
        	g2d = img.createGraphics();
		    
		    // 2. Configure high-quality rendering options
		    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		
		    // 3. Loop through detections using DJL's standard .items() list collection
		    List<DetectedObjects.DetectedObject> items = detections.items();
		    for (DetectedObjects.DetectedObject item : items) {
		        String className = item.getClassName();
		        double score = item.getProbability();
		        
		        // Extract the underlying box geometry interface 
		        BoundingBox box = item.getBoundingBox();
		        
		        // Cast the bounding box interface down to our absolute pixel Rectangle
		        if (box instanceof Rectangle) {
		            Rectangle rect = (Rectangle) box;
		            
		            int x = (int) rect.getX();
		            int y = (int) rect.getY();
		            int w = (int) rect.getWidth();
		            int h = (int) rect.getHeight();
		
		            // 4. Generate a deterministic color based on the label name
		            Color labelColor = getColorForClass(className);
		
		            // 5. Draw the bounding box frame
		            g2d.setColor(labelColor);
		            g2d.setStroke(new BasicStroke(3.0f)); // Thick 3px border line
		            g2d.drawRect(x, y, w, h);
		
		            // 6. Draw background label tag text banner
		            String labelText = String.format("%s (%.2f)", className, score);
		            g2d.setFont(new Font("Arial", Font.BOLD, 14));
		            FontMetrics metrics = g2d.getFontMetrics();
		            int textWidth = metrics.stringWidth(labelText);
		            int textHeight = metrics.getHeight();
		
		            // Safely offset label tag if it overflows top of the image
		            int tagY = (y - textHeight < 0) ? y + textHeight : y;
		            
		            g2d.fillRect(x, tagY - textHeight, textWidth + 6, textHeight + 2);
		
		            // 7. Write label text inside banner tag
		            g2d.setColor(Color.WHITE); // High-contrast white text color
		            g2d.drawString(labelText, x + 3, tagY - 4);
		        }
		    }
        }catch(Exception ex)
        {
        	if(g2d!=null)
        		 g2d.dispose();
        }

        // 8. Output file persistence
        File outputFile = new File(outputFilePath);
        String format = outputFilePath.toLowerCase().endsWith(".png") ? "PNG" : "JPG";
        ImageIO.write(img, format, outputFile);
    }

    /**
     * Helper to reliably generate identical colors per unique layout label
     */
    private static Color getColorForClass(String className) {
        Random random = new Random(className.hashCode());
        // Stay in bright-saturation spectrum range
        int r = random.nextInt(150) + 50;
        int g = random.nextInt(150) + 50;
        int b = random.nextInt(150) + 50;
        return new Color(r, g, b);
    }
	
	public static void main(String args[]) throws IOException, TranslateException
	{
		File folderImage = new File("./test/images/pdf");
		if(folderImage.listFiles()!=null)
		{
			//
			PPDocLayout det = new PPDocLayout();
			//
			File folderOutput = new File(folderImage.getAbsoluteFile()+"/output/"+System.currentTimeMillis());
			for(File f : folderImage.listFiles())
			{
				if(f.isFile())
				{
					if(f.getName().toLowerCase().endsWith(".jpg"))
					{
						long lStartMs = System.currentTimeMillis();
						
						Image inputImage = ImageFactory.getInstance().fromFile(f.toPath());
						DetectedObjects detObjs = det.detectDocLayout(inputImage);
						long lElapsedMs = System.currentTimeMillis()-lStartMs;
						
						folderOutput.mkdirs();
						String sAnnotatedFileName = folderOutput.getAbsolutePath()+"/"+f.getName()+"_annotated.jpg";
						annotateImage(f, detObjs, sAnnotatedFileName);
						
						System.out.println("ElapsedMs="+lElapsedMs+" ms, AnnotatedImage="+sAnnotatedFileName);
						
					}
				}
			}
		}
		else
		{
			System.err.println("No files found in "+folderImage.getAbsolutePath());
		}
	}
}