package hl.doc.extractor.pdf.extraction.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.json.JSONObject;

import hl.common.ImgUtil;
import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.VectorData;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;
import hl.doc.extractor.pdf.extraction.model.ExtractedData;

public class ContentUtil  {

	public static String TAGNAME_BASE64 = "base64";
	
	private static Pattern pattImgBase64Prefix 	= Pattern.compile("(data\\:image\\/(.+?)\\;base64\\,)");
	
	public enum SORT 
	{
	    BY_PAGE			(Comparator.comparing(ContentItem::getPage_no)),
	    BY_X    		(Comparator.comparing(ContentItem::getX1)),
	    BY_Y    		(Comparator.comparing(ContentItem::getY1)),
	    BY_AREA_SIZE	(Comparator.comparing(ContentItem::getAreaSize)),
	    BY_GROUP_NO		(Comparator.comparing(ContentItem::getGroup_no)),
	    BY_EXTRACT_SEQ	(Comparator.comparing(ContentItem::getExtract_seq));
	
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
    		int iContentSize = aContentItem.getData().length();
    		
    		String sData100 = "";
    		if(iContentSize>50)
    		{
    			sData100 = aContentItem.getData().substring(0, 50);
    		}
    		else
    		{
    			sData100 = aContentItem.getData();
    		}
    		return pattImgBase64Prefix.matcher(sData100).find();
    	}
    	return false;
    }
    
    public static boolean saveAsFile(File aFile, String aContent)
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
    	catch(IOException ioEx)
    	{
    		ioEx.printStackTrace();
    	}
    	finally
    	{
    		if(f!=null)
				try {
					f.close();
				} catch (IOException e) {
					//do nothing
				}
    		
    		if(wrt!=null)
				try {
					wrt.close();
				} catch (IOException e) {
					//do nothing
				}
    	}
    	
    	return aFile!=null?aFile.isFile():null;
    }
	
	public static String getImageBase64(ContentItem aContentItem)
	{
		String sBase64 = null;
		if(aContentItem.getType()==Type.IMAGE)
		{
			if(aContentItem.getTagName()==TAGNAME_BASE64)
			{
				sBase64 = aContentItem.getData();
			}
		}
		return sBase64;
	}
	
	public static VectorData getVectorData(ContentItem aContentItem)
	{
		VectorData vector = null;
		if(aContentItem.getType()==Type.VECTOR)
		{
			if(aContentItem.getContentFormat().equals(VectorData.class.getName()))
			{
				vector = new VectorData(new JSONObject(aContentItem.getData()));
			}
		}
		return vector;
	}
	
	public static ContentItem imageToContentItem(BufferedImage aImage, String aFormat, int aPageNo, Rectangle2D aImgCoord) throws IOException
	{
		ContentItem item = null;
		String sImgBase64 = ImgUtil.imageToBase64(aImage, aFormat);
        
        if(sImgBase64!=null)
        {
        	item = new ContentItem(Type.IMAGE, sImgBase64, aPageNo, aImgCoord);
            item.setTagName(ContentUtil.TAGNAME_BASE64);
            item.setContentFormat(aFormat);
        }
        
		return item;
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
    		aPageContentItem = sortContentItems(aPageContentItem, SORT.BY_PAGE, SORT.BY_EXTRACT_SEQ);
    		
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
		        	Shape shape = null;
		        	if(item.getType() == ContentItem.Type.IMAGE)
		        	{
		        		g2d.setColor(Color.RED);
		        		shape = item.getRect2D();
		        	}
		        	else if(item.getType() == ContentItem.Type.TEXT)
		        	{
	        			String sText = item.getData();
	        			
	        			//skip if linebreak only
	        			if(sText.trim().length()==0)
	        				continue;

	        			g2d.setColor(Color.LIGHT_GRAY);
		        		if(isRenderText)
		        		{
		        			if(sText.startsWith("## "))
		        				sText = sText.substring(2);
		        			else if(sText.startsWith("# "))
		        				sText = sText.substring(1);
		        			
			        		Font awt = new Font("Helvetica", Font.PLAIN, (int)item.getHeight());
			        		g2d.setFont(awt);
			        		fm = g2d.getFontMetrics();
			        		
			        		float textX = (float) item.getX1();
			                float textY = (float) item.getY1() + fm.getAscent();
			                g2d.drawString(item.getData(), textX, textY);
		        		}
		        		shape = item.getRect2D();
		        	}
		        	else if(item.getType() == ContentItem.Type.VECTOR)
		        	{
		        		VectorData vector = new VectorData(new JSONObject(item.getData()));
		        		
		        		//Draw Border only in GREEN for now
		        		g2d.setColor(Color.GREEN);
		        		g2d.draw(vector.getVector());
		        		continue; //skip
		        	}
		        	
		        	if(shape!=null)
		        		g2d.draw(shape);
		        }
		        
	        }finally
	        {
	        	if(g2d!=null)
	        		g2d.dispose();
	        }
    	}
    	return img;
    }
    
    public static BufferedImage renderPagePreview(final PDDocument aPDDoc, int iPageNo, float aScale) 
    {
    	PDFRenderer pdfRenderer = new PDFRenderer(aPDDoc);
    	BufferedImage pageImage = null;
		try {
			int iPageIndex = iPageNo-1; //index start with 0
			
			if(aScale<=0 || aScale>5)
				aScale = 1;
			
			pageImage = pdfRenderer.renderImageWithDPI(iPageIndex, aScale * 72);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return pageImage;
    }
    
    public static BufferedImage renderPageArea(final PDDocument aPDDoc, int iPageNo, Rectangle2D aROIrect, float aScale) 
    {
    	BufferedImage pageImage = renderPagePreview(aPDDoc, iPageNo, aScale);
    	if(pageImage!=null && aROIrect!=null
    			&& pageImage.getWidth()>aROIrect.getWidth()
    			&& pageImage.getHeight()>aROIrect.getHeight())
    	{
    		double X2 = aROIrect.getX()+aROIrect.getWidth();
    		double Y2 = aROIrect.getY()+aROIrect.getHeight();
    		
    		if(pageImage.getWidth()>X2 && pageImage.getHeight()>Y2)
    		{
    			pageImage = pageImage.getSubimage(
		    			(int)(aROIrect.getX() * aScale), 
		    			(int)(aROIrect.getY() * aScale), 
		    			(int)(aROIrect.getWidth() * aScale), 
		    			(int)(aROIrect.getHeight() * aScale));
    		}
    	}
    	return pageImage;
    }
    
    
    public static Map<Integer, List<ContentItem>> searchItems(ExtractedData aExtractedData, List<String> aSearchList)
    {
    	Map<Integer, List<ContentItem>> mapMatchedItems = new HashMap<>();
    	
    	List<String> listLowerCase = new ArrayList<>();
    	for(String sSearch : aSearchList)
    	{
    		listLowerCase.add(sSearch.toLowerCase());
    	}
    	
        for(ContentItem it : aExtractedData.getContentItemList())
        {
        	String sData = it.getData().toLowerCase();
        	boolean isFound = false;
        	for(String sSearch : listLowerCase)
        	{
        		if(sData.indexOf(sSearch)>-1)
        		{
        			isFound = true;
        			break;
        		}
        	}
        	if(isFound)
        	{
        		List<ContentItem> listMatched = mapMatchedItems.get(it.getPage_no());
        		if(listMatched==null)
        		{
        			listMatched = new ArrayList<>();
        		}
        		listMatched.add(it);
        		mapMatchedItems.put(it.getPage_no(), listMatched);
        	}
        }
        return mapMatchedItems;
    }

    public static BufferedImage highlightItems(BufferedImage aImage, List<ContentItem> aHighlightItemList)
    {
    	return highlightItems(aImage, aHighlightItemList, 1.0f, Color.RED);
    }
    
    public static BufferedImage highlightItems(BufferedImage aImage, List<ContentItem> aHighlightItemList, float aScale)
    {
    	return highlightItems(aImage, aHighlightItemList, aScale, Color.RED);
    }
    
    public static BufferedImage highlightItems(BufferedImage aImage, 
    		List<ContentItem> aHighlightItemList, 
    		float aScale, Color aColor)
    {
    	if(aImage!=null)
    	{
    		Graphics2D g2d = null;
    		try {
    			g2d = (Graphics2D)aImage.getGraphics();
    			g2d.setColor(aColor);
    			
	        	for(ContentItem it : aHighlightItemList)
	        	{
	        		Rectangle2D box = new Rectangle2D.Double(
	        				(it.getX1()-2) * aScale, 
	        				(it.getY1()-2) * aScale,
	        				(it.getWidth()+4) * aScale, 
	        				(it.getHeight()+4) * aScale);
	        		g2d.draw(box);
	        	}
    		}
    		finally
    		{
    			if(g2d!=null)
    				g2d.dispose();
    		}
    	}
        return aImage;
    }
    
}