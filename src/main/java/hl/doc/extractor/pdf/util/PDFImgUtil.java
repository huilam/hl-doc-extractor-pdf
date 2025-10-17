package hl.doc.extractor.pdf.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import hl.doc.extractor.pdf.model.ContentItem;


public class PDFImgUtil  {
	
	private static Pattern pattImgPrefix = Pattern.compile("(data\\:image\\/(.+?)\\;base64\\,)");
	private static Color DEF_PADDING_COLOR  = Color.WHITE;
	
    public static BufferedImage renderContentByPage(
    		int aPageWidth, int aPageHeight, 
    		Color aBackgroundColor, boolean isRenderText, 
    		final List<ContentItem> aContentList, int aPageNo)
    {
    	List<ContentItem> listPageItem = filterItemsByPage(aContentList, aPageNo);
    	return render(aPageWidth, aPageHeight, 
    			aBackgroundColor, isRenderText, listPageItem);
    }
    
    public static BufferedImage renderLayoutByPage(
    		int aPageWidth, int aPageHeight, 
    		Color aBackgroundColor, 
    		List<ContentItem> aContentList, int aPageNo)
    {
    	List<ContentItem> listPageItem = filterItemsByPage(aContentList, aPageNo);

    	return render(aPageWidth, aPageHeight, 
    			aBackgroundColor, false, listPageItem);
    }
    
    private static List<ContentItem> filterItemsByPage(List<ContentItem> aContentList, int aPageNo)
    {
    	List<ContentItem> listPageItem = new ArrayList<ContentItem>();
    	for(ContentItem item : aContentList)
        {
    		if(item.getPage_no()==aPageNo)
        	{
    			listPageItem.add(item);
        	}
        }
    	return listPageItem;
    }
    
    private static BufferedImage render(
    		int aPageWidth, int aPageHeight, 
    		Color aBackgroundColor, boolean isRenderText, 
    		List<ContentItem> aContentList)
    {
    	BufferedImage img = new BufferedImage(
    			aPageWidth, aPageHeight, 
    			BufferedImage.TYPE_INT_RGB);
    	
    	
    	if(aContentList!=null && aContentList.size()>0)
    	{
	    	Graphics2D g2d = null;
	        try
	        {
		        g2d = img.createGraphics();
		        g2d.setBackground(aBackgroundColor); // padding color
		        g2d.setColor(aBackgroundColor);
		        g2d.fillRect(0, 0, aPageWidth-1, aPageHeight-1);
		        
		        g2d.setColor(Color.BLACK); 
		        g2d.drawRect(0, 0, aPageWidth-1, aPageHeight-1);
		        
		        FontMetrics fm = g2d.getFontMetrics();
		        for(ContentItem item : aContentList)
		        {
		        	if(item.getType() == ContentItem.Type.IMAGE)
		        	{
		        		g2d.setColor(Color.RED);
		        	}
		        	else if(item.getType() == ContentItem.Type.RECT)
		        	{
		        		g2d.setColor(Color.GREEN);
		        	}
		        	else if(item.getType() == ContentItem.Type.TEXT)
		        	{
		        		g2d.setColor(Color.LIGHT_GRAY);
		        		if(isRenderText)
		        		{
		        			String sText = item.getContent();
		        			
		        			if(sText.startsWith("## "))
		        				sText = sText.substring(2);
		        			else if(sText.startsWith("# "))
		        				sText = sText.substring(1);
		        			
			        		Font awt = new Font("Helvetica", Font.PLAIN, (int)item.getHeight());
			        		g2d.setFont(awt);
			        		fm = g2d.getFontMetrics();
			        		
			        		float textX = (float) item.getX1();
			                float textY = (float) item.getY1() + fm.getAscent();
			                g2d.drawString(item.getContent(), textX, textY);
		        		}
		        	}
		        	
		        	g2d.draw(item.getRect2D());
		        }
		        
	        }finally
	        {
	        	if(g2d!=null)
	        		g2d.dispose();
	        }
    	}
    	return img;
    }
    
    public static boolean saveImageAsJPG(BufferedImage aImage, File aOutputFile)
    {
    	return saveImage(aImage, "JPG", aOutputFile);
    }
    public static boolean saveImage(BufferedImage aImage, String aImageExt, File aOutputFile)
    {
    	boolean isSaved = false;
    	try {
    		if(!aImageExt.endsWith("PNG"))
    		{
    			aImage = convertToRGB(aImage);
    		}
    		isSaved = ImageIO.write(aImage, aImageExt, aOutputFile);
    		
    	}catch(IOException ex)
    	{
    		System.err.println(ex.getMessage());
    	}
    	return isSaved;
    }
    
    public static File saveBase64AsImage(String aImageBase64, File aOutputFile)
    {
    	String sImgFormat = "JPG";
    	BufferedImage img = null;
    	Matcher m = pattImgPrefix.matcher(aImageBase64.subSequence(0, 25));
		if(m.find())
		{
			String sPrefix = m.group(1);
			sImgFormat = m.group(2);
			String sImgData = aImageBase64.substring(sPrefix.length());
			try {
		        byte[] decodedBytes = Base64.getDecoder().decode(sImgData);
		        img = ImageIO.read(new ByteArrayInputStream(decodedBytes));
		    } catch (IOException e) {
		        e.printStackTrace();
		    }
		}
		
		if(img!=null)
		{
			String sFileName = aOutputFile.getAbsolutePath();
			if(!sFileName.toLowerCase().endsWith(sImgFormat.toLowerCase()))
			{
				aOutputFile = new File(sFileName+"."+sImgFormat.toLowerCase());
			}
			
			if(saveImage(img, sImgFormat, aOutputFile))
				return aOutputFile;
		}
		return null;
    }
    
    private static BufferedImage convertToRGB(final BufferedImage input) 
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
    
    public static void main(String args[])
    {
    	String sBase64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4XmNgAAIAAAUAAQYUdaMAAAAASUVORK5CYII=";
    	Pattern pattImgPrefix = Pattern.compile("(data\\:image\\/(.+?)\\;base64\\,)");
    	
    	Matcher m = pattImgPrefix.matcher(sBase64.subSequence(0, 30));
    	System.out.println("sData="+sBase64);
    					boolean found = m.find();
    	System.out.println("found="+found);
    }
    
}