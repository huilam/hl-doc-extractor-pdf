package hl.doc.extractor.pdf.extraction.model;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public class MetaData {
	//
	private static String META_SOURCE_FILENAME	= "source_filename";
	private static String META_DOCNAME			= "docname";
	private static String META_AUTHOR 			= "author";
	private static String META_CREATION_DATE 	= "creation_date";
	private static String META_VERSION 			= "version";
	private static String META_ENCRYPTED 		= "encrypted";
	private static String META_PAGE_WIDTH 		= "page_width";
	private static String META_PAGE_HEIGHT 		= "page_height";
	private static String META_TOTAL_PAGES 		= "total_pages";
	private static String META_TOTAL_IMAGES 	= "total_images";
	
	private Properties prop_meta = new Properties();
	
	public MetaData(PDDocument aPDDocument)
	{
		PDDocumentInformation pdfInfo = aPDDocument.getDocumentInformation();
        setDocCreationDate(pdfInfo.getCreationDate());
        setAuthorName(pdfInfo.getAuthor());
        setDocVersion(aPDDocument.getVersion());
        setIsEncryted(aPDDocument.isEncrypted());
        setTotalPages(aPDDocument.getNumberOfPages());
        
        PDRectangle cropBox = aPDDocument.getPage(0).getCropBox();
        setPageWidth((int)Math.ceil(cropBox.getWidth()));
        setPageHeight((int)Math.ceil(cropBox.getHeight()));
	}
	
	
	public void clear()
	{
		prop_meta.clear();
	}
	public void setSourceFileName(String aSourceFileName)
	{
		prop_meta.setProperty(META_SOURCE_FILENAME, aSourceFileName);
	}
	
	public String getSourceFileName()
	{
		return prop_meta.getProperty(META_SOURCE_FILENAME);
	}
	/////
	public void setDocName(String aDocName)
	{
		prop_meta.setProperty(META_DOCNAME, aDocName);
	}
	
	public String getDocName()
	{
		return prop_meta.getProperty(META_DOCNAME);
	}
	//////
	public void setAuthorName(String aAuthorName)
	{
		if(aAuthorName==null)
			aAuthorName = "-";
		prop_meta.setProperty(META_AUTHOR, aAuthorName);
	}
	
	public String getAuthorName()
	{
		return prop_meta.getProperty(META_AUTHOR);
	}
	//////
	public void setDocCreationDate(Calendar aCalendar)
	{
		if(aCalendar!=null && aCalendar.getTimeZone()!=null)
	    {
			String sTimeZone =  aCalendar.getTimeZone().getDisplayName();
	        DateFormat df = new SimpleDateFormat("dd MMM yyyy HH:MM:ss");
	        String sCreationDate = df.format(aCalendar.getTime())+" "+sTimeZone;
			prop_meta.setProperty(META_CREATION_DATE, sCreationDate);
        }
	}
	public String getDocCreationDate()
	{
		return prop_meta.getProperty(META_CREATION_DATE);
	}
	//////
	public void setDocVersion(float aDocVersion)
	{
		prop_meta.setProperty(META_VERSION, String.valueOf(aDocVersion));
	}
	
	public String getDocVersion()
	{
		return prop_meta.getProperty(META_VERSION);
	}
	//////
	public void setIsEncryted(boolean aIsEncrypted)
	{
		prop_meta.setProperty(META_ENCRYPTED, String.valueOf(aIsEncrypted));
	}
	
	public String isEncryted()
	{
		return prop_meta.getProperty(META_ENCRYPTED);
	}
	
	//////
	public void setPageWidth(int aPageWidth)
	{
		prop_meta.setProperty(META_PAGE_WIDTH, String.valueOf(aPageWidth));
	}
	
	public int getPageWidth()
	{
		return getPropValAsInt(META_PAGE_WIDTH);
	}
	//////
	public void setPageHeight(int aPageHeight)
	{
		prop_meta.setProperty(META_PAGE_HEIGHT, String.valueOf(aPageHeight));
	}
	
	public int getPageHeight()
	{
		return getPropValAsInt(META_PAGE_HEIGHT);
	}

	//////
	public void setTotalPages(int aTotal)
	{
		prop_meta.setProperty(META_TOTAL_PAGES, String.valueOf(aTotal));
	}
	
	public int getTotalPages()
	{
		return getPropValAsInt(META_TOTAL_PAGES);
	}
	
	//////
	public void setTotalImages(int aTotal)
	{
		prop_meta.setProperty(META_TOTAL_IMAGES, String.valueOf(aTotal));
	}
	
	public int getTotalImages()
	{
		return getPropValAsInt(META_TOTAL_IMAGES);
	}
	//////
	//////
	public int getPropValAsInt(String aPropKey)
	{
		int iVal = 0;
		String sValue = prop_meta.getProperty(aPropKey);
		if(sValue==null || sValue.trim().length()==0)
			return 0;
		try {
			iVal = Integer.parseInt(sValue);
		}catch(NumberFormatException ex)
		{
			iVal = 0;
		}
		
		return iVal;
	}
}