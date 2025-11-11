package hl.doc.extractor.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import hl.doc.extractor.pdf.model.ContentItem;
import hl.doc.extractor.pdf.model.ExtractedContent;
import hl.doc.extractor.pdf.model.MetaData;
import hl.doc.extractor.pdf.util.ContentUtil;
import hl.doc.extractor.pdf.util.ContentUtil.SORT;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFExtractor {

	private File file_orig_pdf 		= null;	
	private int start_page_no 		= 0;
	private int end_page_no 		= 0;
	//
	private PDDocument pdf_doc 		= null;
	private SORT[] sortings 		= new SORT[] {SORT.BY_PAGE, SORT.BY_Y, SORT.BY_X};
	//
	
    public PDFExtractor(File aPDFFile) throws IOException {
    	pdf_doc = Loader.loadPDF(aPDFFile);
    	
    	if(pdf_doc!=null)
    	{
 	    	setStartPageNo(1);
	    	setEndPageNo(pdf_doc.getNumberOfPages());
	   		this.file_orig_pdf = aPDFFile;
    	}
    }

    
    public File getOrigPdfFile()
    {
    	return file_orig_pdf;
    }
    
    public void setStartPageNo(int iPageNo)
    {
    	int iLastPageNo = pdf_doc.getNumberOfPages();
    	
    	if(iPageNo<1)
    		iPageNo = 1;
    	else if(iPageNo>iLastPageNo)
    		iPageNo = 1;
    	
    	this.start_page_no = iPageNo;
    }
    
    public int getStartPageNo()
    {
    	return this.start_page_no;
    }
    
    public void setEndPageNo(int iPageNo)
    {
    	int iLastPageNo = pdf_doc.getNumberOfPages();
    	
    	if(iPageNo<1)
    		iPageNo = iLastPageNo;
    	else if(iPageNo>iLastPageNo)
    		iPageNo = iLastPageNo;
    	
    	this.end_page_no = iPageNo;
    }
    
    public int getEndPageNo()
    {
    	return this.end_page_no;
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
    	return this.sortings;
    }
    
    //////////////////

    public ExtractedContent extractAll() throws IOException
    {
    	return extractPages(getStartPageNo(), getEndPageNo());
    }
    
    public ExtractedContent extractPage(int aPageNo) throws IOException
    {
    	return extractPages(aPageNo, aPageNo);
    }
    
    public ExtractedContent extractPages(int aStartPageNo, int aEndPageNo) throws IOException
    {
    	List<ContentItem> listItems = new ArrayList<>();
    	
    	for(int iPageNo=aStartPageNo; iPageNo<=aEndPageNo; iPageNo++)
    	{
	    	List<ContentItem> listText = PageExtractor.extractTextContent(pdf_doc, iPageNo-1);
	    	listItems.addAll(listText);
	    	
	    	List<ContentItem> listImage = PageExtractor.extractImageContent(pdf_doc, iPageNo-1);
	    	listItems.addAll(listImage);
    	}
    	
		///
    	listItems = ContentUtil.sortContentItems(listItems, getSortingOrder());
        ///

    	int iDocSeq 	= 1;
    	int iPgLineSeq 	= 1;
    	int iLastPageNo = 1;
        for(ContentItem item : listItems)
        {	
        	if(iLastPageNo != item.getPage_no())
        	{
        		iLastPageNo = item.getPage_no();
        		iPgLineSeq = 1;
        	}
        	item.setDoc_seq(iDocSeq++);
        	item.setPg_line_seq(iPgLineSeq++);
        }
        
        MetaData meta = new MetaData(this.pdf_doc);
        meta.setSourceFileName(this.file_orig_pdf.getName());
        
        ExtractedContent extracted = new ExtractedContent(meta);
        extracted.setContentItemList(listItems);
        return extracted;
    }
    
}