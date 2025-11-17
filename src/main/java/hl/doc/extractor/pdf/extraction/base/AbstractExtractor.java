package hl.doc.extractor.pdf.extraction.base;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import hl.common.ImgUtil;
import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ExtractedContent;
import hl.doc.extractor.pdf.extraction.model.MetaData;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;
import hl.doc.extractor.pdf.extraction.util.ContentUtil.SORT;
import hl.doc.extractor.pdf.extraction.util.ExtractionUtil;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
abstract public class AbstractExtractor {

	private File file_orig_pdf 	= null;
	private MetaData pdf_meta   = null;
	//
	private PDDocument pdf_doc 	= null;
	private SORT[] sortings 	= null; 
	//
	
    public AbstractExtractor(File aPDFFile) throws IOException {
    	
    	this.pdf_doc = Loader.loadPDF(aPDFFile);
    	
    	if(this.pdf_doc!=null)
    	{
	   		this.file_orig_pdf = aPDFFile;
	   		
	   		this.pdf_meta = new MetaData(this.pdf_doc);
	   		this.pdf_meta.setSourceFileName(aPDFFile.getName());
    	}
    }

    public File getOrigPdfFile()
    {
    	return file_orig_pdf;
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
    
    //////////////////

    public ExtractedContent extractAll() throws IOException
    {
    	return extractPages(1, pdf_doc.getNumberOfPages());
    }
    
    public ExtractedContent extractPage(int aPageNo) throws IOException
    {
    	return extractPages(aPageNo, aPageNo);
    }
    
    public ExtractedContent extractPages(int aStartPageNo, int aEndPageNo) throws IOException
    {
    	List<ContentItem> listItems = new ArrayList<>();
    	
    	if(aStartPageNo==0)
    		aStartPageNo = 1;
    	
    	if(aEndPageNo>pdf_doc.getNumberOfPages())
    		aEndPageNo = pdf_doc.getNumberOfPages();
    	
    	for(int iPageNo=aStartPageNo; iPageNo<=aEndPageNo; iPageNo++)
    	{
	    	List<ContentItem> listText = ExtractionUtil.extractTextContent(pdf_doc, iPageNo-1);
	    	listItems.addAll(listText);
	    	
	    	List<ContentItem> listImage = ExtractionUtil.extractImageContent(pdf_doc, iPageNo-1);
	    	listItems.addAll(listImage);
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
        
        ExtractedContent extracted = new ExtractedContent(this.pdf_meta);
        extracted.setContentItemList(listItems);
        return extracted;
    }
    
    public BufferedImage renderPagePreview(int iPageNo, double aRenderScale) 
    {
    	PDFRenderer pdfRenderer = new PDFRenderer(this.pdf_doc);
    	BufferedImage pageImage = null;
		try {
			int iPageIndex = iPageNo-1; //index start with 0
			pageImage = pdfRenderer.renderImageWithDPI(iPageIndex, 150);
			
			if(aRenderScale>0 && aRenderScale!=0)
				pageImage = ImgUtil.resizeImg(pageImage, 
						Math.round(pageImage.getHeight()*aRenderScale), 
						Math.round(pageImage.getHeight()*aRenderScale), 
						true);
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return pageImage;
    }
    
    //////////////////    
    abstract protected List<ContentItem> preSortProcess(List<ContentItem> aContentList);
    
    abstract protected List<ContentItem> postSortProcess(List<ContentItem> aContentList);
    //////////////////

}