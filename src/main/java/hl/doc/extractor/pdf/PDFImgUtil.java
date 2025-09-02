package hl.doc.extractor.pdf;

import hl.doc.extractor.pdf.PDFExtractor.ContentItem;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

public class PDFImgUtil  {
	
	private static Color DEF_PADDING_COLOR  = Color.BLACK;
	
    public static BufferedImage renderContentByPage(int aPageWidth, int aPageHeight, 
    		Color aBackgroundColor, final List<ContentItem> aContentList, int aPageNo)
    {
    	BufferedImage img = new BufferedImage(aPageWidth, aPageHeight, BufferedImage.TYPE_INT_RGB);
    	Graphics2D g2d = null;
        try
        {
	        g2d = img.createGraphics();
	        g2d.setColor(aBackgroundColor); // padding color
	        g2d.fillRect(0, 0, aPageWidth, aPageHeight);
	        
	        g2d.setColor(Color.BLACK);
	        g2d.drawRect(0, 0, aPageWidth-1, aPageHeight-1);
	        for(ContentItem item : aContentList)
	        {
	        	if(item.page_no==aPageNo)
	        	{
		        	if(item.type == ContentItem.Type.IMAGE)
		        	{
		        		g2d.setColor(Color.RED);
		        	}
		        	else if(item.type == ContentItem.Type.TEXT)
		        	{
		        		g2d.setColor(Color.BLACK);
		        		g2d.drawString(item.content, (int)item.x, (int)item.y);
		        	}
		        	
	        		g2d.drawRect((int)item.x, (int)item.y, (int)item.w, (int)item.h);
	        	}
	        }
	        
        }finally
        {
        	if(g2d!=null)
        		g2d.dispose();
        }
    	
    	return img;
    }
    
    public static boolean saveImage(BufferedImage aImage, String aImageExt, File aOutputFile)
    {
    	boolean isSaved = false;
    	try {
    		aImage = convertToRGB(aImage);
    		isSaved = ImageIO.write(aImage, aImageExt, aOutputFile);
    		
    	}catch(IOException ex)
    	{
    		System.err.println(ex.getMessage());
    	}
    	return isSaved;
    }
    
    private static BufferedImage convertToRGB(BufferedImage input) 
    {
    	if(input==null || input.getType()==BufferedImage.TYPE_INT_RGB)
    		return input;
    	
    	
    	BufferedImage imageNew = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
    	Graphics2D g2d = null;
        try
        {
	        g2d = imageNew.createGraphics();
	        g2d.setColor(DEF_PADDING_COLOR); // padding color
	        g2d.fillRect(0, 0, input.getWidth(), input.getHeight());
	        g2d.drawImage(input, 0, 0, null);
        }finally
        {
        	if(g2d!=null)
        		g2d.dispose();
        }
    	
    	return imageNew;
    }
    
    public static BufferedImage resizeWithAspect(BufferedImage input, int targetSize) {
        int origWidth = input.getWidth();
        int origHeight = input.getHeight();

        // scale factor to fit the longest side to targetSize
        double scale = (double) targetSize / Math.max(origWidth, origHeight);

        int newWidth = (int) Math.round(origWidth * scale);
        int newHeight = (int) Math.round(origHeight * scale);

        // resize image
        Image scaled = input.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = null;
        try
        {
	        g2d = resized.createGraphics();
	        g2d.setColor(DEF_PADDING_COLOR); // padding color
	        g2d.fillRect(0, 0, targetSize, targetSize);
	
	        // center the image
	        int x = (targetSize - newWidth) / 2;
	        int y = (targetSize - newHeight) / 2;
	        g2d.drawImage(scaled, x, y, null);
        }finally
        {
        	if(g2d!=null)
        		g2d.dispose();
        }

        return resized;
    }
    
}