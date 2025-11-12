package hl.doc.extractor.pdf.extraction.model;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;

public class ExtractedContent {

	private Map<Integer, List<ContentItem>> page_content_list = new HashMap<>();
	private List<ContentItem> full_content_list 			  = null;
	
	private MetaData pdf_meta 	= null;
	private int min_pageno 		= 1000;
	private int max_pageno 		= 1;
	
	private Map<String, BufferedImage> image_list 	= new HashMap<String, BufferedImage>();
	//
	private static Pattern pattImgPrefix = Pattern.compile("(data\\:image\\/(.+?)\\;base64\\,)");
	private static Pattern pattImgTag 	= Pattern.compile("(\\!\\[image .+?\\]\\((.+?)\\))");
	
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
		image_list.clear();
		
		for(ContentItem it : aContentItemList)
		{
			if(it.getType()==Type.IMAGE)
			{
				int iPageNo = it.getPage_no();
				int iX = (int)Math.round(it.getX1());
				int iY = (int)Math.round(it.getY1());
				int iW = (int)Math.round(it.getWidth());
				int iH = (int)Math.round(it.getHeight());
				
				String sContentData = it.getContent();
				String sImgFormat = null;
				Matcher m = pattImgPrefix.matcher(sContentData);
				if(m.find())
				{
					iImgCount++;
					String sBase64Prefix 	= m.group(1);
					sImgFormat 				= m.group(2);
					
					String sBase64Img = sContentData.substring(sBase64Prefix.length());
					byte[] byteImg = Base64.getDecoder().decode(sBase64Img);
					BufferedImage img = null;
					try {
						img = ImageIO.read(new ByteArrayInputStream(byteImg));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					if(img!=null)
					{
						String sImgFileName = getMetaData().getSourceFileName()
								+"_image_p"+iPageNo+"_"+iX+"-"+iY+"_"+iW+"x"+iH+"."+sImgFormat;
						
						String sImgTagName = "![image "+iImgCount+"]("+sImgFileName+")";
						
						this.image_list.put(sImgTagName, img);
						it.setContent(sImgTagName);
					}
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
	
	public String getImageFileName(String aImageTagName)
	{
		Matcher m = pattImgTag.matcher(aImageTagName);
		if(m.find())
		{
			String sFileName = m.group(2);
			return sFileName;
		}
		
		return null;
	}
	
	public BufferedImage getBufferedImage(String aImageTagName)
	{
		BufferedImage img = this.image_list.get(aImageTagName);
		if(img==null)
		{
			//trying to extract the filename only if the tagName is failed
			String sImgFileName = getImageFileName(aImageTagName);
			if(sImgFileName!=null)
			{
				img = this.image_list.get(sImgFileName);
			}
		}
		return img;
	}
	
}