package hl.doc.extractor.pdf.extraction.base;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.json.JSONArray;
import org.json.JSONObject;

import hl.ml.djl.detection.docs.layout.paddle.PPDocLayout;

public class DocLayoutDetector 
{	
	private PPDocLayout ppDocLayout = null;
	
	public DocLayoutDetector()
	{
		ppDocLayout = new PPDocLayout();
	}
	
	public void release()
	{
		if(ppDocLayout!=null)
		{
			ppDocLayout.destroy();
			ppDocLayout = null;
		}
	}
	
	public Map<String, Rectangle> detectLayoutROI(PDDocument pdf_doc, int iPageNo) throws IOException
	{
		Map<String, Rectangle> mapInterestAreas = new HashMap<>();
		
		PDFRenderer pdfRenderer = new PDFRenderer(pdf_doc);
        BufferedImage imagePage = pdfRenderer.renderImageWithDPI(iPageNo-1, 72, ImageType.RGB);
        
        JSONArray jsonArrDets = ppDocLayout.getDocLayoutInJson(imagePage);
        
System.out.println(jsonArrDets.toString(4));
        
        for(int i=0; i<jsonArrDets.length(); i++)
        {
        	JSONObject json = jsonArrDets.getJSONObject(i);
        	String sObjClassName = json.optString("className");
        	if(sObjClassName.equalsIgnoreCase("text"))
        	{
        		JSONObject jsonBox = json.optJSONObject("boundingBox");
        		if(jsonBox!=null)
        		{
        			JSONArray jsonRect = jsonBox.optJSONArray("rect");
        			
        			int iX = Math.round(jsonRect.getFloat(0));
        			int iY = Math.round(jsonRect.getFloat(1));
        			int iW = Math.round(jsonRect.getFloat(2));
        			int iH = Math.round(jsonRect.getFloat(3));
        			
        			Rectangle rect = new Rectangle(iX, iY, iW, iH);
        			
        			mapInterestAreas.put(sObjClassName+"_"+iX+"_"+iY, rect);
        		}
        	}
        	
        }
        
        return mapInterestAreas;
             
	}
}