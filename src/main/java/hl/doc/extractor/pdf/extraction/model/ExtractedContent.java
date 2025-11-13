package hl.doc.extractor.pdf.extraction.model;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONObject;

import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;

public class ExtractedContent {

	private Map<Integer, List<ContentItem>> page_content_list = new HashMap<>();
	private List<ContentItem> full_content_list 			  = null;
	
	private MetaData pdf_meta 	= null;
	private int min_pageno 		= 1000;
	private int max_pageno 		= 1;
	
	private Map<String, String> imgbase64_cache = new HashMap<String, String>();
	//
	
	public ExtractedContent(MetaData aPDFMeta)
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
					String sImgContent = "![image "+iImgCount+"]("+sImgFileName+")";
					it.setContent(sImgContent);
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
				byte[] byteImg = Base64.getDecoder().decode(sImgBase64);
				
				try {
					img = ImageIO.read(new ByteArrayInputStream(byteImg));
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
    		
    		sb.append(it.getContent());
    		sb.append("\n");
    	}
    	
    	if(isShowPageNo && sb.length()>0)
			sb.append("----[ end ]----");
    	
    	return sb.toString();
    }
    
    public String toJsonFormat()
    {
    	JSONArray jArrItems = new JSONArray();
    	for(ContentItem it : getContentItemList())
    	{
    		JSONObject json = new JSONObject();
    		
    		json.put("page_no", it.getPage_no());
    		json.put("line_seq", it.getPg_line_seq());
    		json.put("x", it.getX1());
    		json.put("y", it.getY1());
    		json.put("width", it.getWidth());
    		json.put("height", it.getHeight());
    		json.put("type", it.getType());
       		json.put("content", it.getContent());
       	    		
    		jArrItems.put(json);
    	}
    	
    	return jArrItems.toString(4);
    }
    
    public Map<String, String> getImageMapping()
    {
    	return this.imgbase64_cache;
    }
	
}