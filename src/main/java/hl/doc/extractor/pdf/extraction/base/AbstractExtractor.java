package hl.doc.extractor.pdf.extraction.base;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.json.JSONObject;

import hl.common.ImgUtil;
import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ExtractedData;
import hl.doc.extractor.pdf.extraction.model.MetaData;
import hl.doc.extractor.pdf.extraction.model.VectorData;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;
import hl.doc.extractor.pdf.extraction.util.ContentUtil.SORT;
import hl.doc.extractor.pdf.extraction.util.ExtractionUtil;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

@SuppressWarnings("unused")
abstract public class AbstractExtractor 
{
	public static String _version = "0.9.0";
	//
	private File file_orig_pdf 	= null;
	private MetaData pdf_meta   = null;
	//
	private PDDocument pdf_doc 	= null;
	private SORT[] sortings 	= null; 
	//
	private boolean is_extract_text 	= true;
	private boolean is_extract_image 	= true;
	private boolean is_extract_vector 	= false;
	//
	private float force_pdf_version = -1f;
	private boolean is_group_text_vertically = false;
	//
	
	public String getVersion()
	{
		return _version;
	}
	
    public AbstractExtractor(File aPDFFile) throws IOException {
    	
    	this.pdf_doc = Loader.loadPDF(aPDFFile);
    	
    	if(this.force_pdf_version>0 && this.force_pdf_version!=this.pdf_doc.getVersion())
    	{
    		/////
    		//System.out.println("Convert from "+this.pdf_doc+" to "+this.force_pdf_version+" in-memory.");
    		this.pdf_doc.setVersion(this.force_pdf_version);
    		//
    		ByteArrayOutputStream bytesPdf = new ByteArrayOutputStream();
    		this.pdf_doc.save(bytesPdf);
    		this.pdf_doc.close();
    		//////
    		//System.out.println("Reload converted "+this.force_pdf_version+" from memory");
    		this.pdf_doc = Loader.loadPDF(bytesPdf.toByteArray());
    	}
    	
    	if(this.pdf_doc!=null)
    	{
	   		this.file_orig_pdf = aPDFFile;
	   		
	   		this.pdf_meta = new MetaData(this.pdf_doc);
	   		this.pdf_meta.setSourceFileName(aPDFFile.getName());
    	}
    }
    
    public void release()
    {
    	if(pdf_doc!=null)
			try {
				pdf_doc.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    }

    public File getOrigPdfFile()
    {
    	return file_orig_pdf;
    }
    
    public void setForcePDFVersion(float aPDFVer)
    {
    	this.force_pdf_version = aPDFVer;
    }
    
    public void setSortingOrder(SORT ... aSorts )
    {
   		if(aSorts==null)
   			aSorts = new SORT[]{};
    	
    	this.sortings = new SORT[aSorts.length];
   		if(aSorts.length>0)
    	{
 	    	for (int idx=0; idx<aSorts.length; idx++)
	    	{
	    		this.sortings[idx] = aSorts[idx];
	    	}
    	}
    }
    
    public SORT[] getSortingOrder()
    {
    	if(this.sortings==null)
    	{
    		return new SORT[] {};
    	}
    	return this.sortings;
    }
    
    public void setIsGroupTextVertically(boolean isGroupVertically)
    {
    	this.is_group_text_vertically = isGroupVertically;
    }
    
    public boolean getIsGroupTextVertically()
    {
    	return this.is_group_text_vertically;
    }
    
    //////////////////

    public ExtractedData extractAll() throws IOException
    {
    	return extractPages(1, pdf_doc.getNumberOfPages());
    }
    
    public ExtractedData extractPage(int aPageNo) throws IOException
    {
    	return extractPages(aPageNo, aPageNo);
    }
    
    public ExtractedData extractPages(int aStartPageNo, int aEndPageNo) throws IOException
    {
    	List<ContentItem> listItems = new ArrayList<>();
    	
    	if(aStartPageNo<=0)
    		aStartPageNo = 1;
    	
    	if(aEndPageNo<=0 || aEndPageNo>pdf_doc.getNumberOfPages())
    		aEndPageNo = pdf_doc.getNumberOfPages();
    	
    	for(int iPageNo=aStartPageNo; iPageNo<=aEndPageNo; iPageNo++)
    	{
    		List<ContentItem> listText 		= new ArrayList<>();
    		List<ContentItem> listImage 	= new ArrayList<>();
    		List<ContentItem> listVector 	= new ArrayList<>();
    		
    		if(this.is_extract_text)
    		{
		    	listText = ExtractionUtil.extractTextContent(pdf_doc, iPageNo-1, this.is_group_text_vertically);
    		}
	    	////
    		if(this.is_extract_image)
    		{
		    	listImage = ExtractionUtil.extractImageContent(pdf_doc, iPageNo-1);
    		}
	    	////
    		if(this.is_extract_vector)
    		{
    			List<ContentItem> listVectorTemp = ExtractionUtil.extractVectorContent(pdf_doc, iPageNo-1);
	    		
	    		for(ContentItem it : listVectorTemp)
	    		{
	    			VectorData vData = new VectorData(new JSONObject(it.getData()));
	    			if(vData.getPathSegmentCount()>20000)
	    			{
	    				//image
	    				Rectangle2D rect = vData.getVector().getBounds();
	    				BufferedImage imgPage = ContentUtil.renderPagePreview(pdf_doc, iPageNo, 1.0f);	    				
	    				BufferedImage imgVector = imgPage.getSubimage((int)rect.getX(), (int)rect.getY(), (int)rect.getWidth(), (int)rect.getHeight());
	    				
	    				if(imgVector!=null)
	    				{
	    					ContentItem item = ContentUtil.imageToContentItem(imgVector, "jpg", iPageNo, rect);
		    				item.setExtract_seq(-1);
		    				listImage.add(item);
	    				}
	    			}
	    			else
	    			{
	    				listVector.add(it);
	    			}
	    		}
    		}
    		
    		
    		if(listText.size()>0)
    			listItems.addAll(listText);
    		if(listImage.size()>0)
    			listItems.addAll(listImage);
    		if(listVector.size()>0)
    			listItems.addAll(listVector);
    	}
    	
    	listItems = preSortProcess(listItems);
		///
		///
    	listItems = ContentUtil.sortContentItems(listItems, getSortingOrder());
        ///
        ///
    	listItems = postSortProcess(listItems);

    	int iDocSeq 	= 1;
    	int iPgLineSeq 	= 1;
    	int iLastPageNo = 1;
        for(ContentItem it : listItems)
        {	
        	if(iLastPageNo != it.getPage_no())
        	{
        		iLastPageNo = it.getPage_no();
        		iPgLineSeq = 1;
        	}
        	it.setDoc_seq(iDocSeq++);
        	it.setPg_line_seq(iPgLineSeq++);
        }
        
        ExtractedData extracted = new ExtractedData(this.pdf_meta);
        extracted.setContentItemList(listItems);
        return extracted;
    }
    
    public void setExtractText(boolean isExtract)
    {
    	this.is_extract_text = isExtract;
    }
    
    public void setExtractImage(boolean isExtract)
    {
    	this.is_extract_image = isExtract;
    }
    
    public void setExtractVector(boolean isExtract)
    {
    	this.is_extract_vector= isExtract;
    }
    
    public BufferedImage renderPagePreview(int iPageNo, float aScale)
    {
    	return ContentUtil.renderPagePreview(this.pdf_doc, iPageNo, aScale);
    }
    
    public BufferedImage renderPageArea(int iPageNo, Rectangle2D aROI, float aScale)
    {
    	return ContentUtil.renderPageArea(this.pdf_doc, iPageNo, aROI, aScale);
    }
    
    //////////////////    
    abstract protected List<ContentItem> preSortProcess(List<ContentItem> aContentList);
    
    abstract protected List<ContentItem> postSortProcess(List<ContentItem> aContentList);
    //////////////////

}