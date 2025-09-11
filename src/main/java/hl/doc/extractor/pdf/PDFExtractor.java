package hl.doc.extractor.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.json.JSONArray;
import org.json.JSONObject;

import hl.doc.extractor.pdf.model.ContentItem;
import hl.doc.extractor.pdf.model.ContentItem.Type;
import hl.doc.extractor.pdf.util.PDFImgUtil;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;

public class PDFExtractor extends PDFTextStripper {

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
	
	private boolean extracted 			= false;
	private boolean export_image_jpg 	= true;
	private boolean embed_image_base64 	= false;
	private int max_image_size			= 0;
	
    private final List<ContentItem> _items 	= new ArrayList<>();
    private SORT _sorting[] = new SORT[] {SORT.BY_PAGE, SORT.BY_Y, SORT.BY_X};
    
	enum SORT 
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
    	
    	super.setAddMoreFormatting(true);
    	super.setSortByPosition(true);
    	super.setShouldSeparateByBeads(true);
    	
    	pdf_doc = Loader.loadPDF(aPDFFile);
    	
    	if(pdf_doc!=null)
    	{
 	    	super.setStartPage(1);
	    	super.setEndPage(pdf_doc.getNumberOfPages());

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
    
    public List<ContentItem> getItemsByPage( List<ContentItem> aListItems, int aPageNo) { 
    	
    	List<ContentItem> listPageItems = new ArrayList<>();
    	for(ContentItem it : aListItems)
    	{
    		if(it.getPage_no()==aPageNo)
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
    		super.writeText(pdf_doc, new StringWriter());
    		
    		List<ContentItem> listItems = new ArrayList<>();
    		listItems.addAll(this.getItems());
    		listItems = postProcessExtractedItems(listItems);
    		sortPageItems(listItems);
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
    
    // Capture text with position
    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException 
    {
        if (string != null && !string.isEmpty()) {
        
            // take position of first character
            TextPosition textFirst 	= textPositions.get(0);
            TextPosition textLast 	= textPositions.get(textPositions.size()-1);
            
            PDFont fontFirst 	= textFirst.getFont();
            PDFont fontLast 	= textLast.getFont();
            
            float x1 = textFirst.getXDirAdj();
            float y1 = textFirst.getYDirAdj();
            
            float x2  = textLast.getXDirAdj();
            float x2w = textLast.getWidthDirAdj();
            
            float w = (x2+x2w)-x1;
            float h = textFirst.getFontSizeInPt();
            
            if(string.trim().isEmpty())
            {
            	string = "";
            }
            else
            {
            	String sFirstFontName = fontFirst.getName().toLowerCase();
            	String sLastFontName = fontLast.getName().toLowerCase();
            	
            	if(sFirstFontName.contains("bold") && sLastFontName.contains("bold"))
	            {
	            	string = HEADING_2+" "+string;
	            }
            	else if((sFirstFontName.contains("italic") || sFirstFontName.contains("oblique"))
            			&& (sLastFontName.contains("italic") || sLastFontName.contains("oblique")))
	            {
	            	string = HEADING_3+" "+string;
	            }
            }
            
            this._items.add(new ContentItem(
            		ContentItem.Type.TEXT, string, 
            		getCurrentPageNo(), 
            		x1, y1, w, h ));
        }
    }

    // Capture images with bounding box
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String op = operator.getName();
        if ("Do".equals(op)) {
            COSName objectName = (COSName) operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);

            if (xobject instanceof PDImageXObject image) {
                Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
               
                // Position (PDF bottom-left)
                float x = ctm.getTranslateX();
                float y = ctm.getTranslateY();
                float w = ctm.getScalingFactorX();
                float h = ctm.getScalingFactorY();
                
                if(w<1 || h<1)
                {
                	//skip if just 1 pixel
                	return;
                }

                // If you need top-left UI coordinates, adjust with crop box:
                PDRectangle crop = getCurrentPage().getCropBox();
                float pageHeight = crop.getHeight();
                x = x - crop.getLowerLeftX();
                y = pageHeight - ((y - crop.getLowerLeftY()) + h);
                
                String sImgFileID = String.format("extracted_p%02d_%d_%d_%dx%d",
                		getCurrentPageNo(), 
                		(int)x, (int)y, (int)w, (int)h);
               
                String sImgContent = sImgFileID;
                BufferedImage bimg = image.getImage();
                
                if(max_image_size>0)
                {
	                if(bimg.getWidth()>max_image_size || bimg.getHeight()>max_image_size)
	            	{
	            		bimg = PDFImgUtil.resizeWithAspect(bimg, max_image_size);
	            	}
                }
                
                if(isEmbedImageBase64())
                {
                	 // Write image data to a byte array
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bimg, IMG_FILEEXT, baos);
                    byte[] imageBytes = baos.toByteArray();
                    sImgContent = "image/"+IMG_FILEEXT+";base64,"+Base64.getEncoder().encodeToString(imageBytes);
                }
                
                if(isExportImageAsJPG())
                {
                	sImgFileID += "."+IMG_FILEEXT;
                	sImgContent = getOutputFolder().getAbsolutePath()+"/images/"+sImgFileID;
                	try {
                		File fileImage = new File(sImgContent);
                		fileImage.getParentFile().mkdirs();
                		if(!PDFImgUtil.saveImage(bimg, IMG_FILEEXT, fileImage))
                			throw new IOException("Failed to save - "+sImgContent+"  bimg="+bimg);
                	}catch(IOException ex)
                	{
                		System.err.println(ex.getMessage());
                	}
                }
                
                StringBuffer sbImgContent = new StringBuffer();
                sbImgContent.append(IMGTAG_PREFIX).append(sImgContent).append(IMGTAG_SUFFIX);
                
                this._items.add(new ContentItem(ContentItem.Type.IMAGE, sbImgContent.toString(), getCurrentPageNo(), x, y, w, h));
                return;
            } else if (xobject instanceof PDFormXObject form) {
                showForm(form);
                return;
            } 
        }
        /**
        else if ("re".equals(op)) 
        {
            float x = ((COSNumber) operands.get(0)).floatValue();
            float y = ((COSNumber) operands.get(1)).floatValue();
            float w = ((COSNumber) operands.get(2)).floatValue();
            float h = ((COSNumber) operands.get(3)).floatValue();
            
            // transform with current matrix
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            Point2D.Float p0 = ctm.transformPoint(x, y);
            Point2D.Float p1 = ctm.transformPoint(x + w, y + h);
            
            Rectangle2D.Float rect = new Rectangle2D.Float(
                    Math.min(p0.x, p1.x),
                    Math.min(p0.y, p1.y),
                    Math.abs(p1.x - p0.x),
                    Math.abs(p1.y - p0.y)
                );
            
            
            this._items.add(new ContentItem(ContentItem.Type.RECT, "", getCurrentPageNo(), 
            		rect.x, rect.y, rect.width, rect.height));
            return;
        }
        **/
        super.processOperator(operator, operands);
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
    	}
    	
    	
    	return aOutputFile;
    }

    public void setIsExportImageAsJPG(boolean isExportJpg)
    {
    	this.export_image_jpg = isExportJpg;
    }
    public boolean isExportImageAsJPG()
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
    
    @Override
    public void setStartPage(int iPageNo)
    {
    	int iLastPageNo = pdf_doc.getNumberOfPages();
    	
    	if(iPageNo<1)
    		iPageNo = 1;
    	else if(iPageNo>iLastPageNo)
    		iPageNo = 1;
    	
    	super.setStartPage(iPageNo);
    }
    
    @Override
    public void setEndPage(int iPageNo)
    {
    	int iLastPageNo = pdf_doc.getNumberOfPages();
    	
    	if(iPageNo<1)
    		iPageNo = iLastPageNo;
    	else if(iPageNo>iLastPageNo)
    		iPageNo = iLastPageNo;
    	
    	super.setEndPage(iPageNo);
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
			        pdfExtract.setStartPage(0);
			        pdfExtract.setEndPage(0);
			        pdfExtract.setIsExportImageAsJPG(true);
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