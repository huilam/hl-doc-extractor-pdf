package hl.doc.extractor.pdf.extraction;

import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ExtractedData;
import hl.doc.extractor.pdf.extraction.model.MetaData;
import hl.doc.extractor.pdf.extraction.model.VectorData;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.json.JSONObject;

public class ConsoleApp {

    private static boolean isConsoleSyntaxOK(String[] args)
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
    
    public static File saveAsFile(ExtractedData aExtractData, File aOutputFile)
    {
    	boolean isJsonFormat = aOutputFile.getName().toLowerCase().endsWith(".json");
    	String sContent = isJsonFormat? 
    			aExtractData.toJsonFormat(true).toString(4): 
    			aExtractData.toPlainTextFormat(true);

		System.out.println("\tFolder:["+aOutputFile.getParent()+"]");
		if(ContentUtil.saveAsFile(aOutputFile, sContent))
		{
			System.out.println("\t[saved] "+aOutputFile.getName());
		}
		
		//PDF Layout
		MetaData metaData = aExtractData.getMetaData();
		for(int iPageNo = aExtractData.getStartPageNo(); iPageNo<=aExtractData.getEndPageNo(); iPageNo++)
		{
			List<ContentItem> listPageItems = aExtractData.getContentItemListByPageNo(iPageNo);
			BufferedImage img = ContentUtil.renderPageLayout(
					metaData.getPageWidth(), metaData.getPageHeight(), 
					Color.WHITE, false, listPageItems);
			
			if(img!=null)
			{
				String sImgLayoutFileName = "layout_p"+iPageNo+".jpg";
				File fileImg = new File(aOutputFile.getParent()+"/"+sImgLayoutFileName);
				
				try {
					if(ImageIO.write(img, "jpg", fileImg))
					{
						System.out.println("\t[saved] "+fileImg.getName());
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		
		//PDF Images
		Map<String,BufferedImage> mapImages = aExtractData.getExtractedBufferedImages();
		for(String sFileName : mapImages.keySet())
		{
			File fileImg = new File(aOutputFile.getParent()+"/"+sFileName);
			BufferedImage img = mapImages.get(sFileName);
			if(img!=null)
			{
				int iPos = sFileName.lastIndexOf(".");
				if(iPos>-1)
				{
					String sImgFormat = sFileName.substring(iPos+1);
					try {
						if(ImageIO.write(img, sImgFormat, fileImg))
						{
							System.out.println("\t[saved] "+fileImg.getName());
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		//PDF Vectors
		for(ContentItem it : aExtractData.getContentItemList())
		{
			if(it.getType()!=Type.VECTOR)
				continue;
				
			VectorData vector = new VectorData(new JSONObject(it.getData()));
			
			
			int iWidth 	= (int)it.getWidth();
			int iHeight = (int)it.getHeight();
			
			int imgWidth = iWidth;
			int imgHeight = iHeight;
			
			if(imgWidth==0)
				imgWidth = 1;
			
			if(imgHeight==0)
				imgHeight = 1;
			
			String sFileName = String.format("vector_p%d_%03d_%d-%d_%dx%d_seg-%d.jpg", 
					it.getPage_no(), 
					it.getExtract_seq(), 
					(int)it.getX1(), (int)it.getY1(), 
					(int)it.getWidth(), (int)it.getHeight(), 
					vector.getPathSegmentCount());
			
			Rectangle2D bound = it.getRect2D();
					
			//Add 1 pixel borders
			imgWidth 	= (int)(bound.getX()+bound.getWidth())+2;
			imgHeight	= (int)(bound.getY()+bound.getHeight())+2;
			
			BufferedImage imgVector = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = null;
			try {
				g = imgVector.createGraphics();
				g.setColor(Color.WHITE);
				g.fillRect(0,0,imgVector.getWidth(), imgVector.getHeight());
				
				g.setColor(Color.GREEN);
				g.draw(vector.getVector());
				
				try {
					File fileImg = new File(aOutputFile.getParent()+"/"+sFileName);
					
					imgVector = imgVector.getSubimage((int)bound.getX(), (int)bound.getY(), 
							(int)bound.getWidth()+2, (int)bound.getHeight()+2);
					
					if(ImageIO.write(imgVector, "jpg", fileImg))
					{
						System.out.println("\t[saved] "+fileImg.getName());
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}finally
			{
				if(g!=null)
					g.dispose();
			}
			
		}
    	
    	return aOutputFile;
    }
    
    //=========================================================== 
    public static void main(String[] args) throws IOException{
        File folderInput 		= null;
        File folderOutput 		= null;
        String[] sOutputTypes  	= new String[]{"json","txt"};
        
        if(isConsoleSyntaxOK(args))
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

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HHmm-ss.SSS");
        String sExecID = df.format(System.currentTimeMillis()); 
        int iFileSeq = 1;
        for(File f : files)
        {
        	if(f.isFile())
        	{
        		if(f.getName().toLowerCase().endsWith(".pdf"))
        		{
        			File folderSaveOutput = new File(folderOutput.getAbsolutePath()+"/"+sExecID+"/"+f.getName());
        			
        			
        			long lStartTimeMs = System.currentTimeMillis(); 
        			System.out.println("\n "+(iFileSeq++)+". Extracting "+f.getName()+" ...");
        			
			        PDFExtractor pdfExtract = null;
			        
			        try {
				        pdfExtract = new PDFExtractor(f);
				        pdfExtract.setExtractText(true);
				        pdfExtract.setExtractImage(true);
				        pdfExtract.setExtractVector(false);
				        ExtractedData content = pdfExtract.extractAll();
				        
				        MetaData metaData = content.getMetaData();
				        for(String sTypeExt : sOutputTypes)
				        {
				        	System.out.println("    - Export to "+sTypeExt+" ...");
				        	
					        File fileOutput = new File(
					        		folderSaveOutput.getAbsolutePath()
					        		+"/extracted_"+metaData.getSourceFileName()+"."+sTypeExt);
					        
					        saveAsFile(content, fileOutput);
					        
					        long lElapsedMs = System.currentTimeMillis() - lStartTimeMs;
					        System.out.println("    - Extracted "+metaData.getTotalPages()
					        					+" pages ("+sTypeExt+" "+lElapsedMs+" ms)");
					        System.out.println();
				        }
				        
				        for(int iPageNo=1; iPageNo <= metaData.getTotalPages(); iPageNo++)
				        {
					        BufferedImage img = pdfExtract.renderPagePreview(iPageNo, 1.0f);
					        if(img!=null)
					        {
					        	File fileImg = new File(
					        			folderSaveOutput.getAbsolutePath()
					        			+"/preview_page_"+iPageNo+".jpg");
						        if(ImageIO.write(img, "jpg", fileImg))
								{
									System.out.println("    - [saved] "+fileImg.getName());
								}
					        }
				        }
			        }
			        finally
			        {
			        	if(pdfExtract!=null)
			        		pdfExtract.release();
			        }
	        	}
        	}
        }
    }
}