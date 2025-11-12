package hl.doc.extractor.pdf.extraction.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;

public class ContentUtil  {

	private static String IMGTAG_PREFIX = "![image ";
	
	public enum SORT 
	{
	    BY_PAGE		(Comparator.comparing(ContentItem::getPage_no)),
	    BY_X    	(Comparator.comparing(ContentItem::getX1)),
	    BY_Y    	(Comparator.comparing(ContentItem::getY1)),
	    BY_SEGMENT	(Comparator.comparing(ContentItem::getSegment_no));
	
	    private final Comparator<ContentItem> cmp;

	    SORT(Comparator<ContentItem> cmp) {
	        this.cmp = cmp;
	    }

	    public Comparator<ContentItem> comparator() {
	        return cmp;
	    }
	}
	
	
    public static List<ContentItem> sortContentItems(
    		List<ContentItem> aListItem, 
    		SORT ... aSortings)
    {
		if(aSortings!=null && aSortings.length>0)
		{
	    	Comparator<ContentItem> cmp = aSortings[0].comparator();
	    	
	    	if(aSortings.length>1)
	    	{
		    	for (int idx=1; idx<aSortings.length; idx++)
		    	{
		    		cmp = cmp.thenComparing(aSortings[idx].comparator());
		    	}
	    	}
			aListItem.sort(cmp);
		}
		return aListItem;
    }
    
    public static int getExtractedTypeCount(final List<ContentItem> aContentList, Type aType)
    {
    	int iImageCount = 0;
    	for(ContentItem it : aContentList)
    	{
    		if(it.getType() == aType)
    			iImageCount++;
    	}
    	return iImageCount;
    }
    
    public static boolean isEmbededBase64Image(ContentItem aContentItem)
    {
    	if(aContentItem.getType() == Type.IMAGE)
    	{
    		int iContentSize = aContentItem.getContent().length();
    		
    		String sData100 = "";
    		if(iContentSize>100)
    		{
    			sData100 = aContentItem.getContent().substring(0, 100);
    		}
    		else
    		{
    			sData100 = aContentItem.getContent();
    		}
    		
    		return !sData100.startsWith(IMGTAG_PREFIX);
    	}
    	return false;
    }
    
    public static boolean saveAsFile(File aFile, String aContent) throws IOException
    {
    	aFile.getParentFile().mkdirs();
    	
    	BufferedWriter wrt  = null;
    	FileWriter f = null;
    	try {
    		f = new FileWriter(aFile);
    		
    		wrt = new BufferedWriter(f);
    		wrt.write(aContent);
    		wrt.flush();
    	}
    	finally
    	{
    		if(f!=null)
    			f.close();
    		
    		if(wrt!=null)
    			wrt.close();
    	}
    	
    	return aFile.isFile();
    }
    
    ////////////
    public static BufferedImage renderPageLayout(
    		int aPageWidth, int aPageHeight, 
    		Color aBackgroundColor, boolean isRenderText, 
    		List<ContentItem> aPageContentItem)
    {
    	BufferedImage img = new BufferedImage(
    			aPageWidth, aPageHeight, 
    			BufferedImage.TYPE_INT_RGB);
    	
    	
    	if(aPageContentItem!=null && aPageContentItem.size()>0)
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
		        for(ContentItem item : aPageContentItem)
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
    
}