package hl.doc.extractor.pdf.extraction.util;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;

public class DataUtil  {
	
	public static String JSON_GROUP_CONTENT = "content";
	public static String JSON_GROUP_IMAGES 	= "images";
	
	public static String JSON_PAGE_NO 	= "page_no";
	public static String JSON_LINE_SEQ 	= "line_seq";
	public static String JSON_GROUP_NO 	= "group_no";
	public static String JSON_X 		= "x";
	public static String JSON_Y 		= "y";
	public static String JSON_WIDTH 	= "width";
	public static String JSON_HEIGHT 	= "height";
	public static String JSON_TYPE 		= "type";
	public static String JSON_FORMAT 	= "format";
	public static String JSON_TAGNAME 	= "tagname";
	public static String JSON_DATA 		= "data";
	
	
	public static String toPlainTextFormat(final List<ContentItem> listExportItems, boolean isShowPageNo, int aMaxAppendLineBreaks)
    {
		if(listExportItems==null)
			return null;
		
    	StringBuffer sb = new StringBuffer();
    	int iPageNo = 0;
    	
    	ContentItem prev = null;
    	
    	for(ContentItem cur : listExportItems)
    	{
    		if(cur.getType()==Type.VECTOR)
    			continue;
    		
    		if(iPageNo==0 || iPageNo!=cur.getPage_no())
    		{
    			iPageNo = cur.getPage_no();
    			//
    			if(isShowPageNo)
    			{
    				if(iPageNo>1)
        				sb.append("\n\n");
    				sb.append("----[ page ").append(iPageNo).append(" ]----\n");
    			//
    			}
    			prev = cur; //reset as new page
    		}
    		else 
    		{	
    			if(cur.getData().trim().length()==0)
    			{
    				if(prev.getData().trim().length()==0)
    				{
    					//We just pad in last round
    					prev = cur;
    					continue;
    				}
    			}
    			
    			String sCurData = cur.getData().replace(" ", ""); //remove all spaces
    			if(sCurData.startsWith("\n") || sCurData.endsWith("\n"))
    			{
    				//No padding needed
    			}
    			else if(aMaxAppendLineBreaks>0)
    			{
	    			double minHeight = Math.min(cur.getHeight(), prev.getHeight());
	    			
    				double dXDiff = Math.abs(cur.getX1() - prev.getX1());
    				if(dXDiff<(minHeight*2))
    				{
		    			double dGapH = Math.abs(cur.getY1() - prev.getY2());
		    			if(dGapH > minHeight)
		    			{
		    				int iEmptyLines = (int) Math.floor(dGapH / minHeight);
		    				
		    				if(iEmptyLines > aMaxAppendLineBreaks)
		    					iEmptyLines = aMaxAppendLineBreaks;
		    				
		    				for(;iEmptyLines>0;iEmptyLines--)
		    				{
		    					sb.append("\n");
		    				}
		    				
		    				Rectangle2D rectCur = cur.getRect2D();
		    				cur.setRect2D(
		    						new Rectangle2D.Double(
		    								rectCur.getX(), rectCur.getY(), rectCur.getWidth(), rectCur.getHeight()*(1+iEmptyLines)));
		    				
		    				if(cur.getData().trim().length()==0)
		    				{
		    					prev = cur;
		    					continue;
		    				}
		    			}
    				}
    				else
        			{
        	    		sb.append("\n");
        			}
    			}
    			else
    			{
    	    		sb.append("\n");
    			}
    		}
    		
    		String sPrefix = "";
    		String sSuffix = "";
    		if(cur.getType()==Type.TEXT)
    		{
    			String sFontFormat = cur.getContentFormat();
	    		if(sFontFormat!=null) 
	    		{
	    			if(sFontFormat.contains("bold"))
	    			{
	    				sPrefix = "### ";
	    			}
	    			else if(sFontFormat.contains("italic") || sFontFormat.contains("oblique"))
	    			{
	    				sPrefix = "## ";
	    			}
	    		}
    		}
    		sb.append(sPrefix).append(cur.getData()).append(sSuffix);
    		prev = cur;
    	}
    	
    	if(isShowPageNo && sb.length()>0)
			sb.append("\n----[ end ]----");
    	
    	return sb.toString();
    }
	
	
	public static JSONObject toJsonFormat(final List<ContentItem> listExportItems, final Map<String, String> aImageBase64Cache, boolean isIncludeImages)
    {
    	JSONObject jsonDoc = new JSONObject();
    	
    	JSONArray jArrContent = new JSONArray();
    	for(ContentItem it : listExportItems)
    	{
    		JSONObject jsonItem = new JSONObject();
    		
    		jsonItem.put(JSON_PAGE_NO, it.getPage_no());
    		jsonItem.put(JSON_LINE_SEQ, it.getPg_line_seq());
    		jsonItem.put(JSON_GROUP_NO, it.getGroup_no());
    		jsonItem.put(JSON_X, it.getX1());
    		jsonItem.put(JSON_Y, it.getY1());
    		jsonItem.put(JSON_WIDTH, it.getWidth());
    		jsonItem.put(JSON_HEIGHT, it.getHeight());
    		jsonItem.put(JSON_FORMAT, it.getContentFormat());
    		jsonItem.put(JSON_TYPE, it.getType());
    		jsonItem.put(JSON_TAGNAME, it.getTagName());
    		
    		if(it.getType()==Type.VECTOR)
    		{
    			jsonItem.put(JSON_DATA, new JSONObject(it.getData()));
    		}
    		else
    		{
    			jsonItem.put(JSON_DATA, it.getData());
    		}
    		
    		jArrContent.put(jsonItem);
    	}
    	jsonDoc.put(JSON_GROUP_CONTENT, jArrContent);
    	if(isIncludeImages)
    	{
    		jsonDoc.put(JSON_GROUP_IMAGES, getExtractedImagesJson(aImageBase64Cache));
    	}
    	
    	return jsonDoc;
    }
	/**
	public static List<ContentItem> fromJsonFormat(final JSONObject aJsonContentList)
    {
		List<ContentItem> listContentList = new ArrayList<>();

    	if(aJsonContentList!=null)
    	{
    		JSONObject jsonImages = aJsonContentList.getJSONObject(JSON_GROUP_IMAGES);
    		
    		JSONArray jArrContent = aJsonContentList.getJSONArray(JSON_GROUP_CONTENT);
    		for(int i=0; i<jArrContent.length(); i++)
    		{
    			JSONObject jsonItem = jArrContent.getJSONObject(i);
    			jsonItem.getInt(JSON_PAGE_NO);
        		jsonItem.getInt(JSON_LINE_SEQ);
        		jsonItem.getInt(JSON_GROUP_NO);
        		jsonItem.getDouble(JSON_X);
        		jsonItem.getDouble(JSON_Y);
        		jsonItem.getDouble(JSON_WIDTH);
        		jsonItem.getDouble(JSON_HEIGHT);
        		jsonItem.getString(JSON_FORMAT);
        		jsonItem.getString(JSON_TAGNAME);
        		
        		
        		jsonItem.get(JSON_TYPE);
        		jsonItem.get(JSON_DATA);
        		
        		String sBase64Image = jsonImages.getString(jsonItem.getString(JSON_TAGNAME));
    			
    		}
    	}
		
		return listContentList;
    }
	**/
	//////
    private static JSONObject getExtractedImagesJson(final Map<String, String> aImageBase64Cache)
    {
    	JSONObject jsonImages = new JSONObject();
    	
    	if(aImageBase64Cache!=null)
    	{
	    	for(String sFileName : aImageBase64Cache.keySet())
	    	{
	    		String sImgBase64 = aImageBase64Cache.get(sFileName);
	    		jsonImages.put(sFileName, sImgBase64);
	    	}
    	}
    	return jsonImages;
    }

}