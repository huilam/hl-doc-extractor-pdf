package hl.doc.extractor.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.json.JSONArray;
import org.json.JSONObject;

import hl.doc.extractor.pdf.model.ContentItem;
import hl.doc.extractor.pdf.model.ContentItem.Type;
import hl.doc.extractor.pdf.util.PDFImgUtil;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public class PDFExtractor {

	public static String META_DOCNAME			= "docname";
	public static String META_AUTHOR 			= "author";
	public static String META_CREATION_DATE 	= "creationdate";
	public static String META_VERSION 			= "version";
	public static String META_TOTAL_PAGES 		= "totalpages";
	public static String META_ENCRYPTED 		= "encrypted";
	public static String META_TOTAL_IMAGES 		= "totalimages";
	public static String META_PAGE_WIDTH 		= "pagewidth";
	public static String META_PAGE_HEIGHT 		= "pageheight";

	protected static String IMGTAG_PREFIX 		= "![IMAGE:";
	protected static String IMGTAG_SUFFIX 		= "]";
	protected static String IMG_FILEEXT			= "jpg";
	protected static String HEADING_1			= "#";
	protected static String HEADING_2			= "##";
	protected static String HEADING_3			= "###";

	private PDDocument pdf_doc 		= null;
	private File file_orig_pdf 		= null;
	private File folder_output 		= null;
	private Properties prop_meta 	= null;
	
	private int start_page_no 		= 0;
	private int end_page_no 		= 0;
	
	private boolean extracted 			= false;
	private boolean export_image_jpg 	= true;
	private boolean embed_image_base64 	= false;
	private int max_image_size			= 0;
	
    private final List<ContentItem> _items 	= new ArrayList<>();
    private SORT _sorting[] = new SORT[] {SORT.BY_PAGE, SORT.BY_Y, SORT.BY_X};
    
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
	
    
    public PDFExtractor(File aPDFFile) throws IOException {
    	pdf_doc = Loader.loadPDF(aPDFFile);
    	
    	if(pdf_doc!=null)
    	{
 	    	setStartPageNo(1);
	    	setEndPageNo(pdf_doc.getNumberOfPages());

	   		this.file_orig_pdf = aPDFFile;

	   		PDDocumentInformation pdfInfo = pdf_doc.getDocumentInformation();
	        DateFormat df = new SimpleDateFormat("dd MMM yyyy HH:MM:ss");
	        
	        Calendar calCreateDate = pdfInfo.getCreationDate();
	        String sTimeZone =  calCreateDate.getTimeZone().getDisplayName();
	        String sCreationDate = df.format(calCreateDate.getTime())+" "+sTimeZone;
	        String sAuthor = pdfInfo.getAuthor();
	        if(sAuthor==null)
	        	sAuthor = "-";
	        
	        prop_meta = new Properties();
	        prop_meta.put(META_DOCNAME, file_orig_pdf.getName());
	        prop_meta.put(META_TOTAL_PAGES, String.valueOf(pdf_doc.getNumberOfPages()));
	        prop_meta.put(META_CREATION_DATE, sCreationDate);
	        prop_meta.put(META_ENCRYPTED, String.valueOf(pdf_doc.isEncrypted()));
	        prop_meta.put(META_VERSION,String.valueOf(pdf_doc.getVersion()));
	        prop_meta.put(META_AUTHOR, sAuthor);
	        
	        prop_meta.put(META_PAGE_WIDTH, getPageWidth());
	        prop_meta.put(META_PAGE_HEIGHT, getPageHeight());
    	}
    }
    
    public List<ContentItem> getItems() { 
    	return this._items; 
    }
    
    public List<ContentItem> getItemsByPage(
    		List<ContentItem> aListItems, int aPageNo) { 
    	return getItemsByPageType(aListItems, aPageNo, null);
    }
    
    public List<ContentItem> getItemsByPageType(
    		List<ContentItem> aListItems, int aPageNo, Type aItemType) { 
    	
    	return getItemsByPageTypeRegion(aListItems, aPageNo, aItemType, null);
    }
    
    public List<ContentItem> getItemsByPageRegion(
    		List<ContentItem> aListItems, int aPageNo, Rectangle aRegion) { 
    	return getItemsByPageTypeRegion(aListItems, aPageNo, null, aRegion);
    }
    
    private List<ContentItem> getItemsByPageTypeRegion(
    		List<ContentItem> aListItems, int aPageNo, Type aItemType, Rectangle aRegion) { 
    	
    	List<ContentItem> listPageItems = new ArrayList<>();
    	for(ContentItem it : aListItems)
    	{
    		boolean isWithinPage 	= (aPageNo==-1 || it.getPage_no()==aPageNo);
    		boolean isSameType 		= (aItemType==null || aItemType==it.getType());
    		boolean isWithinRegion 	= false;
    		
    		if(isWithinPage && isSameType)
    		{
        		if(aRegion==null)
        		{
        			isWithinRegion = true;
        		}
        		else
        		{
        			isWithinRegion = it.getX1()>aRegion.getX() 
        					&& it.getX2()<aRegion.getMaxX() 
        					&& it.getY1()>aRegion.getY() 
        					&& it.getY2()<aRegion.getMaxY();
        		}
    		}
    		
    		if(isWithinRegion)
    		{
    			listPageItems.add(it);
    		}
    	}
    	return listPageItems; 
    }
    
    public int getExtractedTypeCount(Type aType)
    {
    	int iImageCount = 0;
    	for(ContentItem it : getItems())
    	{
    		if(it.getType() == aType)
    			iImageCount++;
    	}
    	return iImageCount;
    }
    
    public int getTotalPages()
    {
    	return pdf_doc.getNumberOfPages();
    }
    
    public int getPageWidth()
    {
    	return (int)Math.ceil(pdf_doc.getPage(0).getCropBox().getWidth());
    }
    
    public int getPageHeight()
    {
    	return (int)Math.ceil(pdf_doc.getPage(0).getCropBox().getHeight());
    }
    
    public void setSortingOrder(SORT ... aSorts )
    {
   		if(aSorts==null)
   			aSorts = new SORT[]{};
    	
    	this._sorting = new SORT[aSorts.length];
   		if(aSorts.length>0)
    	{
 	    	for (int idx=0; idx<aSorts.length; idx++)
	    	{
	    		this._sorting[idx] = aSorts[idx];
	    	}
    	}
    }
    
    public SORT[] getSortingOrder()
    {
    	return this._sorting;
    }
    
    protected void sortPageItems(List<ContentItem> aListItem)
    {
    	sortPageItems(aListItem,this.getSortingOrder());
    }
    
    public void sortPageItems(
    		List<ContentItem> aListItem, 
    		SORT ... aSortings)
    {
		if(aSortings.length>0)
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
    }
    
    private List<ContentItem> extract() throws IOException
    {
    	if(!this.extracted)
    	{
	    	this.extracted = true;
	    	///////////////////////
	    	List<ContentItem> listItems = new ArrayList<>();
	    	for(int iPageNo=getStartPageNo(); iPageNo<=getEndPageNo(); iPageNo++)
	    	{
		    	List<ContentItem> listText = ContentParser.extractTextContent(pdf_doc, iPageNo-1);
		    	listItems.addAll(listText);
		    	
		    	List<ContentItem> listImage = ContentParser.extractImageContent(pdf_doc, iPageNo-1);
		    	listItems.addAll(listImage);
	    	}
	    	
    		///////////////////////
    		listItems = postProcessExtractedItems(listItems);
    		sortPageItems(listItems);
    		///////////////////////
       		this._items.clear();
    		this._items.addAll(listItems);
        	///////////////////////
	    	int iDocSeq 	= 1;
	    	int iPgLineSeq 	= 1;
	    	int iLastPageNo = 1;
	        for(ContentItem item : this.getItems())
	        {	
	        	if(iLastPageNo != item.getPage_no())
	        	{
	        		iLastPageNo = item.getPage_no();
	        		iPgLineSeq = 1;
	        	}
	        	item.setDoc_seq(iDocSeq++);
	        	item.setPg_line_seq(iPgLineSeq++);
	        }
	        updateMetaData(META_TOTAL_IMAGES, getExtractedTypeCount(Type.IMAGE));
    	}
    	
        return this._items;
    }
    
    // Custom Post Process
    public List<ContentItem> postProcessExtractedItems(List<ContentItem> aList)
    {
    	//sortPageItems(aList, new SORT[] {SORT.BY_PAGE});
		return aList;
    }
    
    //////////////////////////////////////
    protected List<String> metaDataAsPlainText() throws IOException
    {
    	List<String> listMeta = new ArrayList<String>();
    	
    	for(Object oKey: prop_meta.keySet())
    	{
    		String sMeta = oKey+"="+prop_meta.get((String)oKey);
    		listMeta.add(sMeta);
    	}
    	return listMeta;
    }
    
    protected List<String> contentAsPlainText() throws IOException
    {
    	this.extract();
    	
    	List<String> listPDF = new ArrayList<String>();
    	
    	int iLeadingZero = String.valueOf(this._items.size()).length();
    	int iLastPageNo  = 0;
    	
    	for (ContentItem it : this.getItems()) 
    	{	
    		if(it.getType()==Type.RECT)
    		{
    			//ignore
    			continue;
    		}
    		
    		if(iLastPageNo!=it.getPage_no())
    		{
    			listPDF.add(" ");
    			iLastPageNo = it.getPage_no();
    		}
    		
    		String StrData = String.format(
    				"%0"+iLeadingZero+"d p%01d.%02d [%.0f]"+
    				"   %s",
    				it.getDoc_seq(), it.getPage_no(), it.getPg_line_seq(),
    				it.getSegment_no(),
    				it.getContent());
    		listPDF.add(StrData);
    	}
    	
    	return listPDF;
    }
    
    
    public List<String> extractAsPlainText() throws IOException
    {
    	List<String> listPDF = metaDataAsPlainText();
    	listPDF.addAll(contentAsPlainText());
    	return listPDF;
    }
    
    //////////////////////////////////////
    protected JSONObject metaDataAsJSON() throws IOException
    {
    	JSONObject jsonMeta = new JSONObject();
    	for(Object oKey: prop_meta.keySet())
    	{
    		String sKey = (String)oKey;
    		jsonMeta.put(sKey, prop_meta.get(sKey));
    	}
    	return jsonMeta;
    }
    
    protected JSONArray contentAsJSONArray() throws IOException
    {
    	this.extract();
    	
    	JSONArray jArrPages = new JSONArray();
        
        for (ContentItem it : this.getItems()) 
        {
    		if(it.getType()==Type.RECT)
    		{
    			//ignore
    			continue;
    		}
    		
        	JSONObject jsonPageData = new JSONObject();
        	jsonPageData.put("doc_seq", it.getDoc_seq());
        	jsonPageData.put("page_no", it.getPage_no());
        	jsonPageData.put("page_line_seq", it.getPg_line_seq());
        	jsonPageData.put("data", it.getContent());
        	jsonPageData.put("x", it.getX1());
        	jsonPageData.put("y", it.getY1());
        	jsonPageData.put("width", it.getWidth());
        	jsonPageData.put("height", it.getHeight());
        	if(it.getType() == ContentItem.Type.TEXT)
        		jsonPageData.put("type", "TEXT");
        	else 
        		jsonPageData.put("type", "IMAGE");
        	
        	jArrPages.put(jsonPageData);
        }
        
		return jArrPages;
    }
    
    public JSONObject extractAsJSON() throws IOException
    {
    	JSONObject jsonDoc = new JSONObject();
    	
    	jsonDoc.put(META_DOCNAME,this.file_orig_pdf.getName());
    	jsonDoc.put("meta", metaDataAsJSON());
    	jsonDoc.put("content", contentAsJSONArray());
    	
    	return jsonDoc;
    }
    
    //////////////////////////////////////
    public int renderContentAsImage() throws IOException
    {
    	return renderAsImage(false).size();
    }
    public int renderLayoutAsImage() throws IOException
    {
    	return renderAsImage(true).size();
    }
    
    private List<File> renderAsImage(boolean isLayout) throws IOException
    {
    	this.extract();
    	
    	List<File> listOutputImg = new ArrayList<File>();
    	
    	JSONObject jsonMeta = metaDataAsJSON();
    	int iPageWidth 	= jsonMeta.optInt(META_PAGE_WIDTH,0);
    	int iPageHeight = jsonMeta.optInt(META_PAGE_HEIGHT,0);
    	int iTotalPages = jsonMeta.optInt(META_TOTAL_PAGES,0);
    	
    	String sRenderFilePrefix = getOutputFolder()+"/layout/rendered_";
    	sRenderFilePrefix += isLayout?"layout":"content";
    	
    	for(int iPageNo = 1; iPageNo<=iTotalPages; iPageNo++)
    	{
    		BufferedImage img = null;
    		List<ContentItem> listItems = new ArrayList<>();
    		listItems.addAll(this.getItems());
    		
    		if(isLayout)
    		{
	    		img = PDFImgUtil.renderLayoutByPage(iPageWidth, iPageHeight, 
	    				Color.WHITE, listItems, iPageNo);
    		}else
    		{
    			img = PDFImgUtil.renderContentByPage(iPageWidth, iPageHeight, 
    					Color.WHITE, true, listItems, iPageNo);
    		}
    		
    		File fileImage = new File(String.format(sRenderFilePrefix+"_%d.%s", 
    				iPageNo, IMG_FILEEXT.toLowerCase()));
    		
    		if(!fileImage.getParentFile().isDirectory())
    		{
    			fileImage.getParentFile().mkdirs();
    		}
    		
    		if(PDFImgUtil.saveImage(img, IMG_FILEEXT, fileImage))
    		{
    			listOutputImg.add(fileImage);
    		}
    			
    	}
    	
    	/**
    	for(File f : listOutputImg)
    	{
    		System.out.println("  - "+f.getAbsolutePath());
    	}
    	**/

    	return listOutputImg;
    }
    
    public File extractAsFile(File aOutputFile) throws IOException
    {
    	this.extract();
    	
    	List<String> listOutput = null;
    	
    	String sFileName = aOutputFile.getName().toLowerCase();
    	
    	boolean isJsonFormat = sFileName.endsWith(".json");
    	
    	if(isJsonFormat)
    	{
    		listOutput = new ArrayList<>();
    		JSONObject jsonOutput = extractAsJSON();
    		listOutput.add(jsonOutput.toString(4));
    	}
    	else
    	{
    		listOutput = extractAsPlainText();
    	}
    	
    	if(listOutput!=null && listOutput.size()>0)
    	{
    		aOutputFile.getParentFile().mkdirs();
    		
    		BufferedWriter wrt = null;
    		int iLineCount = 0;
    		
    		try{
    			wrt = new BufferedWriter(new FileWriter(aOutputFile));
    			
    			for(String sLineData : listOutput)
        		{
    				iLineCount++;
    				wrt.write(sLineData+"\n");
    				if(iLineCount%100==0)
    					wrt.flush();
        		}
    			wrt.flush();
    		}
    		finally {
    			if(wrt!=null)
    				wrt.close();
    		}
    		
        	if(isExportImage())
    		{
        		int id = 1;
        		
        		File folderImages = new File(getOutputFolder().getAbsolutePath()+"/images/");
        		folderImages.mkdirs();
        		
        		List<ContentItem> items = this.extract();
    			for(ContentItem item : items)
    			{
    				if(item.getType()==Type.IMAGE)
    				{
	    				String sImgBase64 = item.getContent();
	    				
	    				if(sImgBase64!=null && sImgBase64.length()>20)
	    				{
	    					String sImgExt = "jpg";
		    				String sImgFileName = 
		    						getOrigPdfFile().getName()+"_"+"img"+(id++)
		    						+"_"+item.getX1()+"_"+item.getY1()+"_"+item.getWidth()+"x"+item.getHeight()
		    						+"."+sImgExt;
		                	
		                	try {
		                		File fileImage = new File(folderImages.getAbsolutePath()+"/"+sImgFileName);
		                		fileImage.getParentFile().mkdirs();
		                		
		                		
		                		
		                		File fileSaved = PDFImgUtil.saveBase64AsImage(sImgBase64,fileImage);
		                		
		                		if(!fileSaved.exists())
		                		{
		                			throw new IOException("Failed to save - fileName="+fileImage.getAbsolutePath());
		                		}
		                		else
		                		{
		                			System.out.println("    saved "+fileSaved.getName());
		                		}
		                	}catch(IOException ex)
		                	{
		                		System.err.println(ex.getMessage());
		                	}
	    				}
    				}
    			}
    		}
    	}
    	
    	
    	return aOutputFile;
    }

    public void setIsExportImage(boolean isExportJpg)
    {
    	this.export_image_jpg = isExportJpg;
    }
    public boolean isExportImage()
    {
    	return this.export_image_jpg;
    }
    
    public void setIsEmbedImageBase64(boolean isInclude)
    {
    	this.embed_image_base64 = isInclude;
    }
    
    public boolean isEmbedImageBase64()
    {
    	return this.embed_image_base64;
    }
    
    public void setMaxImageSize(int iMaxPixels)
    {
    	this.max_image_size = iMaxPixels;
    }
    
    public int getMaxImageSize()
    {
    	return this.max_image_size;
    }
    
    public Properties getMetaData()
    {
    	return prop_meta;
    }
    
    public void updateMetaData(String aPropKey, Object aProVal)
    {
    	this.prop_meta.put(aPropKey, aProVal);
    }
    
    public void setOutputFolder(File aFolder)
    {
    	if(aFolder!=null)
    	{
    		this.folder_output = aFolder;
    	}
    }
    
    public File getOutputFolder()
    {
    	if(this.folder_output!=null)
    	{
    		this.folder_output.mkdirs();
    		if(this.folder_output.isDirectory())
    		
    		{
    			return this.folder_output;
    		}
    	}

		return file_orig_pdf.getParentFile();
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
    
    public static boolean isConsoleSyntaxOK(String[] args)
    {
    	boolean isSyntaxOK = false;
        
        if(args.length>0)
        {
        	
        	File folderInput = new File(args[0]);
        	if(!folderInput.isDirectory())
        	{
        		isSyntaxOK = false;
        		System.err.println("Invalid <input-folder> !");
        	}
        	
        	if(args.length>1)
        	{
        		File folderOutput = new File(args[1]);
        		if(!folderOutput.isDirectory())
            	{
        			isSyntaxOK = false;
            		System.err.println("Invalid <output-folder> !");
            	}
        	}
        	
        	if(!isSyntaxOK)
        	{
        		System.err.println("Syntax :");
        		System.err.println("   PDFExtractor <input-folder> [output-folder]");
        		System.err.println();
        		System.err.println("Example :");
        		System.err.println("   PDFExtractor test ");
        		System.err.println("   PDFExtractor test output");
        		System.err.println("   PDFExtractor test test/output");
        	}
        	
        }
        return isSyntaxOK;
    }
    
    //=========================================================== 
    public static void main(String[] args) throws IOException{
        File folderInput 		= null;
        File folderOutput 		= null;
        boolean isRenderLayout  = true;
        String[] sOutputTypes  	= new String[]{"json","txt"};
        
        if(PDFExtractor.isConsoleSyntaxOK(args))
        {
        	folderInput 	= new File(args[0]);
    		folderOutput	= new File(args[1]);
        }
        else
        {
        	folderInput 	= new File("test/");
        	folderOutput	= new File(folderInput.getAbsolutePath()+"/output");
        }

        if(!folderInput.isDirectory())
        {
        	System.err.println("Invalid input folder ! NULL");
        	return;
        }
        
        File files[] = folderInput.listFiles(new FileFilter() {
			
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().toLowerCase().endsWith(".pdf");
			}
		});
        if(files==null)
        	files = new File[]{};

        if(files.length==0)
        {
        	System.err.println("PDF file NOT found ! "+folderInput.getAbsolutePath());
        	return;
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-DD_HHmm-SS.sss");
        String sExecID = df.format(System.currentTimeMillis()); 
        for(File f : files)
        {
        	if(f.isFile())
        	{
        		if(f.getName().toLowerCase().endsWith(".pdf"))
        		{
        			
        			long lStartTimeMs = System.currentTimeMillis(); 
        			System.out.println("Extracting "+f.getName()+" ...");
        			
			        PDFExtractor pdfExtract = new PDFExtractor(f);
			        pdfExtract.setStartPageNo(0);
			        pdfExtract.setEndPageNo(0);
			        pdfExtract.setIsExportImage(true);
			        pdfExtract.setIsEmbedImageBase64(false);
			        pdfExtract.setMaxImageSize(0);
			        pdfExtract.setSortingOrder(SORT.BY_PAGE, SORT.BY_Y, SORT.BY_X);
			        pdfExtract.setOutputFolder(new File(folderOutput.getParent()+"/"+sExecID+"/"+f.getName()));
			        
			        for(String sTypeExt : sOutputTypes)
			        {
				        Properties propMeta = pdfExtract.getMetaData();
				        
				        File fileOutput = new File(pdfExtract.getOutputFolder().getAbsolutePath()
				        		+"/extracted_data."+sTypeExt);
				        pdfExtract.extractAsFile(fileOutput);
				        
				        long lElapsedMs = System.currentTimeMillis() - lStartTimeMs;
				        System.out.println("  Extracted "+propMeta.getProperty(META_TOTAL_PAGES)
				        					+" pages ("+sTypeExt+" "+lElapsedMs+" ms)");
			        }
			        
			        if(isRenderLayout)
	        		{
			        	int iRenderedPages = pdfExtract.renderLayoutAsImage();
			        	System.out.println("  Rendered layout : "+iRenderedPages);
	        		}
	        	}
        	}
        }
    }
}