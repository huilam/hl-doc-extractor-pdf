package hl.doc.extractor.pdf.model;

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

import hl.doc.extractor.pdf.model.ContentItem.Type;

public class ExtractedContent {

	private List<ContentItem> content_list 			= null;
	private Map<String, BufferedImage> image_list 	= new HashMap<String, BufferedImage>();
	private MetaData pdf_meta 						= null;
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
	
	public void setContentItemList(List<ContentItem> aContentItemList)
	{
		if(aContentItemList==null)
			aContentItemList = new ArrayList<ContentItem>();

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
								+"_p"+iPageNo+"_"+iX+"-"+iY+"_"+iW+"x"+iH+"."+sImgFormat;
						
						String sImgTagName = "![image "+iImgCount+"]("+sImgFileName+")";
						
						this.image_list.put(sImgTagName, img);
						it.setContent(sImgTagName);
					}
				}
				
				
				
			}
			
			this.pdf_meta.setTotalImages(iImgCount);;
		}
		
		this.content_list = aContentItemList;
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
		return this.image_list.get(aImageTagName);
	}
	
	public List<ContentItem> getContentItemList()
	{
		return this.content_list;
	}
	
	public ContentItem[] getContentItems()
	{
		return this.content_list.toArray(new ContentItem[this.content_list.size()]);
	}
}