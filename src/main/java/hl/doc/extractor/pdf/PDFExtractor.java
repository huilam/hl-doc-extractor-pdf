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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
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

	protected static String IMGTAG_PREFIX 		= "![IMAGE:";
	protected static String IMGTAG_SUFFIX 		= "]";
	protected static String IMG_FILEEXT			= "jpg";
	protected static String FONTBOLD_PREFIX		= "##";
	protected static Color DEF_PADDING_COLOR  	= Color.BLACK;
	
	private PDDocument pdf_doc 	= null;
	private File file_orig_pdf 	= null;
	private File folder_output 	= null;
	private Properties prop_meta = null;
	
	private boolean extracted 			= false;
	private boolean export_image_jpg 	= true;
	private boolean embed_image_base64 	= false;
	private int max_image_size			= 0;
	
    protected final List<ContentItem> items = new ArrayList<>();

    
    public class ContentItem {
    	public enum Type { TEXT, IMAGE }
    	public Type type		= Type.TEXT;
    	public int doc_seq 		= 0;
    	public int page_no 		= 0;
    	public int pg_line_seq 	= 0;
    	public String content 	= "";
    	public float x, y		= 0;
    	public float w, h  		= 0; 

        // For text & image
    	public ContentItem(Type type, String content, int pageno, float x, float y, float w, float h) {
            this.type = type; this.page_no = pageno;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.content = content;
        }
    }
    
    // Capture text with position
    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException 
    {
        if (string != null && !string.isEmpty()) {
        	
            // take position of first character
            TextPosition pos = textPositions.get(0);
            float x = pos.getXDirAdj();
            float y = pos.getYDirAdj();
            float w = pos.getWidthDirAdj();
            float h = pos.getHeightDir();
            
            if(string.trim().isEmpty())
            {
            	string = "";
            }
            else
            {
	            PDFont font = pos.getFont();
	            boolean isBold = font.getName().toLowerCase().contains("bold");
	            if(isBold)
	            {
	            	string = FONTBOLD_PREFIX+" "+string;
	            }
            }
            
            items.add(new ContentItem(
            		ContentItem.Type.TEXT, string, getCurrentPageNo(), 
            		x, y, w, h ));
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
                
                String sImgFileID = String.format("extracted_%s_p%02d_%d_%d_%dx%d",
                		file_orig_pdf.getName(), getCurrentPageNo(), 
                		(int)x, (int)y, (int)w, (int)h);
               
                String sImgContent = sImgFileID;
                BufferedImage bimg = image.getImage();
                
                if(max_image_size>0)
                {
	                if(bimg.getWidth()>max_image_size || bimg.getHeight()>max_image_size)
	            	{
	            		bimg = resizeWithAspect(bimg, max_image_size);
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
                	sImgContent = getOutputFolder().getAbsolutePath()+"/"+sImgFileID;
                	 // Write image to file
                	File fileImg = new File(sImgContent);
                	try {
                		bimg = convertToRGB(bimg);
                		boolean isSaved = ImageIO.write(bimg, IMG_FILEEXT, fileImg);
                		if(!isSaved)
                			throw new IOException("Failed to save - "+sImgContent+"  bimg="+bimg);
                	}catch(IOException ex)
                	{
                		System.err.println(ex.getMessage());
                	}
                }
                
                StringBuffer sbImgContent = new StringBuffer();
                sbImgContent.append(IMGTAG_PREFIX).append(sImgContent).append(IMGTAG_SUFFIX);
                
                items.add(new ContentItem(ContentItem.Type.IMAGE, sbImgContent.toString(), getCurrentPageNo(), x, y, w, h));
                return;
            } else if (xobject instanceof PDFormXObject form) {
                showForm(form);
                return;
            }
        }
        super.processOperator(operator, operands);
    }
    
    public List<ContentItem> getItems() { return items; }
    
    public PDFExtractor(File aPDFFile) throws IOException {
    	
    	super.setAddMoreFormatting(true);
    	super.setSortByPosition(false);
    	
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
    	}
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
    	extract();
    	
    	List<String> listPDF = new ArrayList<String>();
    	
    	int iLeadingZero = String.valueOf(items.size()).length();
    	int iLastPageNo  = 0;
    	
    	for (ContentItem it : this.getItems()) 
    	{	
    		if(iLastPageNo!=it.page_no)
    		{
    			listPDF.add(" ");
    			iLastPageNo = it.page_no;
    		}
    		
    		String StrData = String.format(
    				"%0"+iLeadingZero+"d p%01d.%02d"+
    				//" y:%03.2f x:%03.2f"+
    				"   %s",
    				it.doc_seq, it.page_no, it.pg_line_seq,
    				//it.y, it.x,
    				it.content);
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
        
        for (ContentItem it : this.getItems()) {

        	JSONObject jsonPageData = new JSONObject();
        	jsonPageData.put("doc_seq", it.doc_seq);
        	jsonPageData.put("page_no", it.page_no);
        	jsonPageData.put("page_line_seq", it.pg_line_seq);
        	jsonPageData.put("data", it.content);
        	jsonPageData.put("x", it.x);
        	jsonPageData.put("y", it.y);
        	jsonPageData.put("width", it.w);
        	jsonPageData.put("height", it.h);
        	if(it.type == ContentItem.Type.TEXT)
        		jsonPageData.put("type", "TEXT");
        	else 
        		jsonPageData.put("type", "IMAGE");
        	
        	jArrPages.put(jsonPageData);
        }
        
		return jArrPages;
    }
    
    protected JSONObject extractAsJSON() throws IOException
    {
    	JSONObject jsonDoc = new JSONObject();
    	
    	jsonDoc.put(META_DOCNAME,this.file_orig_pdf.getName());
    	jsonDoc.put("meta", metaDataAsJSON());
    	jsonDoc.put("content", contentAsJSONArray());
    	
    	return jsonDoc;
    }
    
    //////////////////////////////////////
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
    	if(aFolder==null || aFolder.isDirectory())
    	{
    		this.folder_output = aFolder;
    	}
    }
    
    public File getOutputFolder()
    {
    	if(this.folder_output!=null && this.folder_output.isDirectory())
    	{
    		return this.folder_output;
    	}
    	else
    	{
    		return file_orig_pdf.getParentFile();
    	}
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
    
    protected List<ContentItem> extract() throws IOException
    {
    	if(!this.extracted)
    	{
	    	this.extracted = true;
    		super.writeText(pdf_doc, new StringWriter());

	    	this.getItems().sort(Comparator
	                .comparingInt((ContentItem it) -> it.page_no)
	                .thenComparing((ContentItem it) -> it.y)
	                .thenComparing(it -> it.x));
	        
	    	int iDocSeq 	= 1;
	    	int iPgLineSeq 	= 1;
	    	int iLastPageNo = 1;
	    	
	    	int iExtractedImgCount = 0;
	    	
	    	List<ContentItem> listUpdated = new ArrayList<>();
	    	ContentItem itemPrev = null;
	    	
	        for(ContentItem item : this.getItems())
	        {	
	        	if(iLastPageNo != item.page_no)
	        	{
	        		iLastPageNo = item.page_no;
	        		iPgLineSeq = 1;
	        	}
	        	else if (itemPrev!=null)
	        	{
	        		if(itemPrev.type == ContentItem.Type.IMAGE && item.content.trim().length()==0)
	        		{
	        			if(item.type == ContentItem.Type.TEXT)
	        				continue;
	        		}
	        		
	        		//same page_no same y
	        		if((item.y==itemPrev.y))
	        		{
        				//merge text to prev previous as same y-coord
        				itemPrev.content += item.content;
        				continue;
	        		}
	        	}
	        	
	        	item.doc_seq 		= iDocSeq++;
	        	item.pg_line_seq 	= iPgLineSeq++;
	        	itemPrev = item;
	        	listUpdated.add(item);
	        	
	        	if(item.type == ContentItem.Type.IMAGE)
	        		iExtractedImgCount++;
	        }
	        
	        items.clear();
	        items.addAll(listUpdated);
	        updateMetaData(META_TOTAL_IMAGES, iExtractedImgCount);
    	}
        return items;
    }
    
    private static BufferedImage convertToRGB(BufferedImage input) 
    {
    	if(input==null || input.getType()==BufferedImage.TYPE_INT_RGB)
    		return input;
    	
    	
    	BufferedImage imageNew = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
    	Graphics2D g2d = null;
        try
        {
	        g2d = imageNew.createGraphics();
	        g2d.setColor(DEF_PADDING_COLOR); // padding color
	        g2d.fillRect(0, 0, input.getWidth(), input.getHeight());
	        g2d.drawImage(input, 0, 0, null);
        }finally
        {
        	if(g2d!=null)
        		g2d.dispose();
        }
    	
    	return imageNew;
    }
    
    private static BufferedImage resizeWithAspect(BufferedImage input, int targetSize) {
        int origWidth = input.getWidth();
        int origHeight = input.getHeight();

        // scale factor to fit the longest side to targetSize
        double scale = (double) targetSize / Math.max(origWidth, origHeight);

        int newWidth = (int) Math.round(origWidth * scale);
        int newHeight = (int) Math.round(origHeight * scale);

        // resize image
        Image scaled = input.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = null;
        try
        {
	        g2d = resized.createGraphics();
	        g2d.setColor(DEF_PADDING_COLOR); // padding color
	        g2d.fillRect(0, 0, targetSize, targetSize);
	
	        // center the image
	        int x = (targetSize - newWidth) / 2;
	        int y = (targetSize - newHeight) / 2;
	        g2d.drawImage(scaled, x, y, null);
        }finally
        {
        	if(g2d!=null)
        		g2d.dispose();
        }

        return resized;
    }
    
    //=========================================================== 
    public static void main(String[] args) throws IOException{
    	
        File folderInput 	= new File("test/");
        File folderOutput 	= folderInput;
        String[] sOutputTypes  = new String[]{"json","txt"};
        
        if(args.length>0)
        {
        	boolean isSyntaxErr = false;
        	
        	folderInput = new File(args[0]);
        	if(!folderInput.isDirectory())
        	{
        		isSyntaxErr = true;
        		System.err.println("Invalid <input-folder> !");
        	}
        	
        	if(args.length>1)
        	{
        		folderOutput = new File(args[1]);
        		if(!folderOutput.isDirectory())
            	{
            		isSyntaxErr = true;
            		System.err.println("Invalid <output-folder> !");
            	}
        	}
        	
        	if(isSyntaxErr)
        	{
        		System.err.println("Syntax :");
        		System.err.println("   PDFExtractor <input-folder> [output-folder]");
        		System.err.println();
        		System.err.println("Example :");
        		System.err.println("   PDFExtractor test ");
        		System.err.println("   PDFExtractor test output");
        		System.err.println("   PDFExtractor test test/output");
        		return;
        	}
        }
        
        
        for(File f : folderInput.listFiles())
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
			        pdfExtract.setOutputFolder(folderOutput);
			        
			        for(String sTypeExt : sOutputTypes)
			        {
				        File fileOutput = new File(pdfExtract.getOutputFolder().getAbsolutePath()+"/extracted_"+f.getName()+"."+sTypeExt);
				        pdfExtract.extractAsFile(fileOutput);
				        JSONObject jsonMeta = pdfExtract.metaDataAsJSON();
				        long lElapsedMs = System.currentTimeMillis() - lStartTimeMs;
				        System.out.println("  Extracted "+jsonMeta.getLong(META_TOTAL_PAGES)+" pages ("+sTypeExt+" "+lElapsedMs+" ms)");
			        }
	        		System.out.println();
	        	}
        	}
        }
        
    }
}