package hl.doc.extractor.pdf.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ExtractedContent;
import hl.doc.extractor.pdf.extraction.model.MetaData;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;
import hl.doc.extractor.pdf.extraction.util.ContentUtil.SORT;
import hl.doc.extractor.pdf.extraction.util.ExtractionUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class PDFExtractor {

	private File file_orig_pdf 	= null;
	private MetaData pdf_meta   = null;
	private int start_page_no 	= 0;
	private int end_page_no 	= 0;
	//
	private PDDocument pdf_doc 	= null;
	private SORT[] sortings 	= null; 
	//
	
    public PDFExtractor(File aPDFFile) throws IOException {
    	
    	this.pdf_doc = Loader.loadPDF(aPDFFile);
    	
    	if(this.pdf_doc!=null)
    	{
 	    	setStartPageNo(1);
	    	setEndPageNo(pdf_doc.getNumberOfPages());
	   		this.file_orig_pdf = aPDFFile;
	   		
	   		this.pdf_meta = new MetaData(this.pdf_doc);
	   		this.pdf_meta.setSourceFileName(aPDFFile.getName());
    	}
    	
    	initExtractor();
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
    	if(this.sortings==null || this.sortings.length==0)
    	{
    		return new SORT[] {SORT.BY_PAGE, SORT.BY_Y, SORT.BY_X};
    	}
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
    
    //////////////////
    public void initExtractor()
    {
    }
    
    public List<ContentItem> preSortProcess(List<ContentItem> aContentList)
    {
    	return aContentList;
    }
    
    public List<ContentItem> postSortProcess(List<ContentItem> aContentList)
    {
    	return aContentList;
    }
    //////////////////

}