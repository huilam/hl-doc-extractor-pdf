package hl.doc.extractor.pdf.extraction.model;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONObject;

import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;

public class ExtractedData {

	public static String JSON_GROUP_CONTENT = "content";
	public static String JSON_GROUP_IMAGES 	= "images";
	
	public static String JSON_PAGE_NO 	= "page_no";
	public static String JSON_LINE_SEQ 	= "line_seq";
	public static String JSON_X 		= "x";
	public static String JSON_Y 		= "y";
	public static String JSON_WIDTH 	= "width";
	public static String JSON_HEIGHT 	= "height";
	public static String JSON_TYPE 		= "type";
	public static String JSON_DATA 		= "data";
	
	private Map<Integer, List<ContentItem>> page_content_list = new HashMap<>();
	private List<ContentItem> full_content_list 			  = null;
	
	private MetaData pdf_meta 	= null;
	private int min_pageno 		= Integer.MAX_VALUE;
	private int max_pageno 		= 0;
	
	private Map<String, String> imgbase64_cache = new LinkedHashMap<String, String>();
	//
	
	public ExtractedData(MetaData aPDFMeta)
	{
		this.pdf_meta = aPDFMeta;
	}
	
	public MetaData getMetaData()
	{
		return this.pdf_meta;
	}
	
	public int getStartPageNo()
	{
		return this.min_pageno;
	}
	
	public int getEndPageNo()
	{
		return this.max_pageno;
	}
	
	public void setContentItemList(List<ContentItem> aContentItemList)
	{
		if(aContentItemList==null)
			aContentItemList = new ArrayList<ContentItem>();
		
		page_content_list.clear();

		int iImgCount = 0;
		imgbase64_cache.clear();
		
		for(ContentItem it : aContentItemList)
		{
			if(it.getType()==Type.IMAGE)
			{
				int iPageNo = it.getPage_no();
				int iX = (int)Math.round(it.getX1());
				int iY = (int)Math.round(it.getY1());
				int iW = (int)Math.round(it.getWidth());
				int iH = (int)Math.round(it.getHeight());
				
				String sBase64Img = ContentUtil.getImageBase64(it);
				if(sBase64Img!=null)
				{
					iImgCount++;
					//
					String sImgFormat = it.getContentFormat();
					String sImgFileName = "image_"+iImgCount+"_p"+iPageNo+"_"+iX+"-"+iY+"_"+iW+"x"+iH+"."+sImgFormat;
					//
					it.setTagName(sImgFileName);
					it.setContentFormat(sImgFormat);
					//
					this.imgbase64_cache.put(sImgFileName, sBase64Img);
					//
					String sImgContent = "![image]("+sImgFileName+")";
					it.setData(sImgContent);
				}
			}
			
			int iPageNo = it.getPage_no();
			
			if(iPageNo<this.min_pageno)
				this.min_pageno = iPageNo;
			
			if(iPageNo>this.max_pageno)
				this.max_pageno = iPageNo;
			
			List<ContentItem> listPageItem = page_content_list.get(iPageNo);
			if(listPageItem==null)
			{
				listPageItem = new ArrayList<ContentItem>();
			}
			listPageItem.add(it);
			page_content_list.put(iPageNo, listPageItem);
		}
		this.pdf_meta.setTotalImages(iImgCount);;
		this.full_content_list = aContentItemList;
	}
	
	public BufferedImage getBufferedImage(ContentItem aContentItem)
	{
		BufferedImage img = null;
		if(aContentItem.getType() == Type.IMAGE)
		{
			String sImgBase64 = ContentUtil.getImageBase64(aContentItem);
			if(sImgBase64==null)
			{
				//get from cache
				sImgBase64 = this.imgbase64_cache.get(aContentItem.getTagName());
			}
			//
			if(sImgBase64!=null)
			{
				try {
					img = ImageIO.read(new ByteArrayInputStream(
							Base64.getDecoder().decode(sImgBase64)));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return img;
	}
	
	public List<ContentItem> getContentItemList()
	{
		return this.full_content_list;
	}
	
	public List<ContentItem> getContentItemListByPageNo(int aPageNo)
	{
		return page_content_list.get(aPageNo);
	}
	
	public String toPlainTextFormat(boolean isShowPageNo)
    {
    	StringBuffer sb = new StringBuffer();
    	int iPageNo = 0;
    	for(ContentItem it : getContentItemList())
    	{
    		if(it.getType()==Type.VECTOR)
    			continue;
    		
    		if(iPageNo==0 || iPageNo!=it.getPage_no())
    		{
    			iPageNo = it.getPage_no();
    			if(iPageNo>1)
    				sb.append("\n\n");
    			//
    			if(isShowPageNo)
    				sb.append("----[ page ").append(iPageNo).append(" ]----\n");
    			//
    		}
    		
    		sb.append(it.getData());
    		sb.append("\n");
    	}
    	
    	if(isShowPageNo && sb.length()>0)
			sb.append("----[ end ]----");
    	
    	return sb.toString();
    }
    
    public JSONObject toJsonFormat(boolean isIncludeImages)
    {
    	JSONObject jsonDoc = new JSONObject();
    	
    	JSONArray jArrContent = new JSONArray();
    	for(ContentItem it : getContentItemList())
    	{
    		JSONObject jsonItem = new JSONObject();
    		
    		jsonItem.put(JSON_PAGE_NO, it.getPage_no());
    		jsonItem.put(JSON_LINE_SEQ, it.getPg_line_seq());
    		jsonItem.put(JSON_X, it.getX1());
    		jsonItem.put(JSON_Y, it.getY1());
    		jsonItem.put(JSON_WIDTH, it.getWidth());
    		jsonItem.put(JSON_HEIGHT, it.getHeight());
    		jsonItem.put(JSON_TYPE, it.getType());
    		jsonItem.put(JSON_DATA, it.getData());
    		
    		jArrContent.put(jsonItem);
    	}
    	jsonDoc.put(JSON_GROUP_CONTENT, jArrContent);
    	if(isIncludeImages)
    	{
    		jsonDoc.put(JSON_GROUP_IMAGES, getExtractedImagesJson());
    	}
    	
    	return jsonDoc;
    }
    
    public Map<String, String> getExtractedBase64Images()
    {
    	return this.imgbase64_cache;
    }
	
    public Map<String, BufferedImage> getExtractedBufferedImages()
    {
    	Map<String, BufferedImage> mapImages = new LinkedHashMap<>();
    	
    	Map<String, String> mapBase64Images = getExtractedBase64Images();
    	for(String sFileName : mapBase64Images.keySet())
		{
			String sImgBase64 = mapBase64Images.get(sFileName);
			if(sImgBase64!=null)
			{
				BufferedImage img = null;
				try {
					img = ImageIO.read(new ByteArrayInputStream(
							Base64.getDecoder().decode(sImgBase64)));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if(img!=null)
				{
					mapImages.put(sFileName, img);
				}
			}
		}
    	return mapImages;
    }
    
    public JSONObject getExtractedImagesJson()
    {
    	JSONObject jsonImages = new JSONObject();
    	
    	Map<String, String> mapBase64Images = getExtractedBase64Images();
    	for(String sFileName : mapBase64Images.keySet())
    	{
    		String sImgBase64 = mapBase64Images.get(sFileName);
    		jsonImages.put(sFileName, sImgBase64);
    	}
    	return jsonImages;
    }
}