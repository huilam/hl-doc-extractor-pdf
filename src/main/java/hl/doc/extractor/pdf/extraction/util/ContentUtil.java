package hl.doc.extractor.pdf.extraction.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;

public class ContentUtil  {

	public static String TAGNAME_BASE64 = "base64";
	
	private static Pattern pattImgBase64Prefix 	= Pattern.compile("(data\\:image\\/(.+?)\\;base64\\,)");
	private static Pattern pattVectorLineRGB 	= Pattern.compile("stroke\\.rgb\\:\\[([0-9]{1,3})\\,([0-9]{1,3})\\,([0-9]{1,3})\\]");
	private static Pattern pattVectorFillRGB 	= Pattern.compile("fill\\.rgb\\:\\[(.+?)\\,(.+?)\\,(.+?)\\]");

	
	public enum SORT 
	{
	    BY_PAGE			(Comparator.comparing(ContentItem::getPage_no)),
	    BY_X    		(Comparator.comparing(ContentItem::getX1)),
	    BY_Y    		(Comparator.comparing(ContentItem::getY1)),
	    BY_SEGMENT		(Comparator.comparing(ContentItem::getSegment_no)),
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
	
	public static GeneralPath getVectorPath(ContentItem aContentItem)
	{
		GeneralPath path = null;
		if(aContentItem.getType()==Type.VECTOR)
		{
			if(aContentItem.getContentFormat().equals(GeneralPath.class.getName()))
			{
				path = stringToVectorPath(aContentItem.getData());
			}
		}
		return path;
	}
	
	public static Color[] getVectorColor(ContentItem aContentItem)
	{
		Color[] colors = new Color[]{null,null};
		if(aContentItem.getType()==Type.VECTOR)
		{
			if(aContentItem.getContentFormat().equals(GeneralPath.class.getName()))
			{
				Matcher m = pattVectorLineRGB.matcher(aContentItem.getTagName());
				if(m.find())
				{
					int iRed 	= Integer.parseInt(m.group(1));
					int iGreen 	= Integer.parseInt(m.group(2));
					int iBlue 	= Integer.parseInt(m.group(3));
					colors[0] = new Color(iRed, iGreen, iBlue);
				}
				
				m = pattVectorFillRGB.matcher(aContentItem.getTagName());
				if(m.find())
				{
					int iRed 	= Integer.parseInt(m.group(1));
					int iGreen 	= Integer.parseInt(m.group(2));
					int iBlue 	= Integer.parseInt(m.group(3));
					colors[1] = new Color(iRed, iGreen, iBlue);
				}
			}
		}
		return colors;
	}

	
	public static String vectorPathToString(GeneralPath path) {
        StringBuilder sb = new StringBuilder();
        PathIterator it = path.getPathIterator(null);

        float[] coords = new float[6];
        while (!it.isDone()) {
            int type = it.currentSegment(coords);
            sb.append(type);
            for (int i = 0; i < coordCount(type); i++) {
                sb.append(',').append(coords[i]);
            }
            sb.append(';'); // segment separator
            it.next();
        }
        return sb.toString();
    }

    // Number of coords per segment type
    private static int coordCount(int segmentType) {
        return switch (segmentType) {
            case PathIterator.SEG_MOVETO -> 2;
            case PathIterator.SEG_LINETO -> 2;
            case PathIterator.SEG_QUADTO -> 4;
            case PathIterator.SEG_CUBICTO -> 6;
            case PathIterator.SEG_CLOSE -> 0;
            default -> throw new IllegalArgumentException("Unknown segment");
        };
    }
    
    public static GeneralPath stringToVectorPath(String s) {
        GeneralPath path = new GeneralPath();

        for (String seg : s.split(";")) {
            if (seg.isEmpty()) continue;
            String[] parts = seg.split(",");
            int type = Integer.parseInt(parts[0]);

            switch (type) {
                case PathIterator.SEG_MOVETO ->
                    path.moveTo(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));

                case PathIterator.SEG_LINETO ->
                    path.lineTo(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));

                case PathIterator.SEG_QUADTO ->
                    path.quadTo(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3]), Float.parseFloat(parts[4]));

                case PathIterator.SEG_CUBICTO ->
                    path.curveTo(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]),
                                 Float.parseFloat(parts[3]), Float.parseFloat(parts[4]),
                                 Float.parseFloat(parts[5]), Float.parseFloat(parts[6]));

                case PathIterator.SEG_CLOSE ->
                    path.closePath();

                default ->
                    throw new IllegalArgumentException("Unknown segment: " + type);
            }
        }

        return path;
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
		        		GeneralPath vector = stringToVectorPath(item.getData());
		        		shape = vector;
		        		
		        		Color[] colors = getVectorColor(item);
		        		if(colors[1]!=null)
		        		{
			        		g2d.setColor(colors[1]);
		        			g2d.fill(shape);
		        		}
		        		
		        		g2d.setColor(Color.GREEN);
		        		if(colors[0]!=null)
		        		{
		        			g2d.setColor(colors[0]);
		        		}
		        		
		        		
		        	}
		        	
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
    
}