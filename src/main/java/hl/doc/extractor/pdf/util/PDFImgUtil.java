package hl.doc.extractor.pdf.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import hl.doc.extractor.pdf.model.ContentItem;
import hl.doc.extractor.pdf.model.ContentItem.Type;


public class PDFImgUtil  {
	
	private static Color DEF_PADDING_COLOR  = Color.BLACK;
	
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
    	List<ContentItem> listLayout = new ArrayList<>();
    	List<ContentItem> listPageItem = filterItemsByPage(aContentList, aPageNo);
    	
    	if(listPageItem.size()>0)
    	{
    		Map<Double, Rectangle> mapSegment = new HashMap<Double, Rectangle>();
    		for(ContentItem it : listPageItem)
    		{
    			if(it.getType()==Type.TEXT)
    			{
    			
    				if(it.getSeg()>-1)
    				{
		    			Rectangle rect = mapSegment.get(it.getSeg());
		    			if(rect==null)
		    				rect = new Rectangle(aPageWidth,aPageHeight,0,0);
		    			
		    			int x1 = (int)Math.ceil(it.getX1());
		    			int y1 = (int)Math.ceil(it.getY1());
		    			if(x1 < rect.x)
		    			{
		    				rect.x = x1;
		    			}
		    			if(y1 < rect.y)
		    			{
		    				rect.y = y1;
		    			}
		    			//////////////////////////
		    			int x2 = (int)Math.ceil(it.getX2());
		    			int y2 = (int)Math.ceil(it.getY2());
		    			int width 	= x2-x1;
		    			int height 	= y2-y1;
		    			if(width > rect.width)
		    			{
		    				rect.width = width;
		    			}
		    			if(height > rect.height)
		    			{
		    				rect.height = height;
		    			}
		    			
		    			mapSegment.put(it.getSeg(), rect);
    				}
    				else
    				{
    					//outlier
    					listLayout.add(it);
    				}
    			}
    			else
    			{
    				listLayout.add(it);
    			}
    		}
    		
    		if(mapSegment.size()>0)
    		{
	    		for(double dSeg : mapSegment.keySet())
	    		{
	    			Rectangle rect = mapSegment.get(dSeg);
	    			if(rect.width>0 && rect.height>0)
	    			{
		    			ContentItem item = new ContentItem(Type.TEXT,"", aPageNo,
		    					rect.x, rect.y, rect.width, rect.height);
		    			listLayout.add(item);
	    			}
	    			
	    		}
    		}
    	}
    	return render(aPageWidth, aPageHeight, 
    			aBackgroundColor, false, listLayout);
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
    	
    	
System.out.println(" render -> "+aContentList.size());
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
		        
		        for(ContentItem item : aContentList)
		        {
	        		int x = (int) Math.round(item.getX1());
	        		int y = (int) Math.round(item.getY1());
	        		int w = (int) Math.round(item.getWidth());
	        		int h = (int) Math.round(item.getHeight());
	        		
		        	if(item.getType() == ContentItem.Type.IMAGE)
		        	{
		        		g2d.setColor(Color.RED);
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
		        			
			        		Font awt = new Font("Helvetica", Font.PLAIN, h);
			        		g2d.setFont(awt);
			        		g2d.drawString(sText, x, y + h);
		        		}
		        	}
		        	
	        		g2d.drawRect(x, y, w, h);
		        }
		        
	        }finally
	        {
	        	if(g2d!=null)
	        		g2d.dispose();
	        }
    	}
    	return img;
    }
    
    public static boolean saveImage(BufferedImage aImage, File aOutputFile)
    {
    	return saveImage(aImage, "JPG", aOutputFile);
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