package hl.doc.extractor.pdf.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hl.doc.extractor.pdf.model.ContentItem;
import hl.doc.extractor.pdf.model.ContentItem.Type;

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
    
}