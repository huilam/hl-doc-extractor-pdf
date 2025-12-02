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

import org.json.JSONObject;

import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;
import hl.doc.extractor.pdf.extraction.util.DataUtil;

public class ExtractedData {

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
	
	public void clear()
	{
		clearDataCache();
		pdf_meta.clear();
		pdf_meta = null;
		min_pageno = Integer.MAX_VALUE;
		max_pageno = 0;
		//
	}
	public void clearDataCache()
	{
		if(imgbase64_cache!=null)
			imgbase64_cache.clear();
		
		if(page_content_list!=null)
			page_content_list.clear();
		
		if(full_content_list!=null)
			full_content_list.clear();
	}
	
	
	private String genImageFileName(ContentItem aImageItem, int aImageCount)
	{
		return String.format("image_p%02d-%d_%d-%d_%dx%d.%s",
						(int)aImageItem.getPage_no(), 
						(int)aImageCount, 
						(int)aImageItem.getX1(), (int)aImageItem.getY1(), 
						(int)aImageItem.getWidth(), (int)aImageItem.getHeight(), 
						aImageItem.getContentFormat());
	}
	
	public void setContentItemList(List<ContentItem> aContentItemList)
	{
		if(aContentItemList==null)
			aContentItemList = new ArrayList<ContentItem>();
		
		clearDataCache();

		int iImgCount = 0;
		
		for(ContentItem it : aContentItemList)
		{
			if(it.getType()==Type.IMAGE)
			{
				iImgCount++;
				//
				String sBase64Img = ContentUtil.getImageBase64(it);
				if(sBase64Img!=null)
				{
					//
					String sImgFileName = it.getTagName();
					
					if(sImgFileName==null || sImgFileName.trim().length()==0 
							|| sImgFileName.equalsIgnoreCase(ContentUtil.TAGNAME_EMBEDED_BASE64))
					{	
						sImgFileName = genImageFileName(it, iImgCount);
						it.setTagName(sImgFileName);
					}
					//
					this.imgbase64_cache.put(sImgFileName, sBase64Img);
					//
					String sImgContent = "![image "+iImgCount+"]("+sImgFileName+")";
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
		return toPlainTextFormat(isShowPageNo, 3);
	}
	
	public String toPlainTextFormat(boolean isShowPageNo, int aMaxAppendLineBreaks)
    {
		return DataUtil.toPlainTextFormat(getContentItemList(), isShowPageNo, aMaxAppendLineBreaks);
    }
    
    public JSONObject toJsonFormat(boolean isIncludeImages)
    {
    	return DataUtil.toJsonFormat(getContentItemList(), getExtractedBase64Images(), isIncludeImages);
    }
    
    //
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
    
}