package hl.doc.extractor.pdf.extraction;

import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ContentItem.Type;
import hl.doc.extractor.pdf.extraction.model.ExtractedContent;
import hl.doc.extractor.pdf.extraction.model.MetaData;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;
import hl.doc.extractor.pdf.extraction.util.ContentUtil.SORT;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

import javax.imageio.ImageIO;

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
    
    public static File saveAsFile(ExtractedContent aExtractData, File aOutputFile)
    {
    	boolean isJsonFormat = aOutputFile.getName().toLowerCase().endsWith(".json");
    	String sContent = isJsonFormat? aExtractData.toJsonFormat(): aExtractData.toPlainTextFormat(true);
    	
    	try {
			if(ContentUtil.saveAsFile(aOutputFile, sContent))
			{
				System.out.println("    [saved] "+aOutputFile.getAbsolutePath());
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
					
					if(ImageIO.write(img, "jpg", fileImg))
					{
						System.out.println("    [saved] "+fileImg.getName());
					}
				}
			}
			
			//PDF Images
			for(ContentItem it: aExtractData.getContentItemList())
			{
				if(it.getType()==Type.IMAGE)
				{
					BufferedImage img = aExtractData.getBufferedImage(it);
					if(img!=null)
					{
						String sImgFileName = it.getTagName(); //filename
						String sImgFormat = it.getContentFormat(); //image-format
						File fileImg = new File(aOutputFile.getParent()+"/"+sImgFileName);
						
						if(ImageIO.write(img, sImgFormat, fileImg))
						{
							System.out.println("    [saved] "+fileImg.getName());
						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HHmm-SS.sss");
        String sExecID = df.format(System.currentTimeMillis()); 
        for(File f : files)
        {
        	if(f.isFile())
        	{
        		if(f.getName().toLowerCase().endsWith(".pdf"))
        		{
        			File folderSaveOutput = new File(folderOutput.getAbsolutePath()+"/"+sExecID+"/"+f.getName());
        			
        			
        			long lStartTimeMs = System.currentTimeMillis(); 
        			System.out.println("\nExtracting "+f.getName()+" ...");
        			
			        PDFExtractor pdfExtract = new PDFExtractor(f);
			        pdfExtract.setStartPageNo(0);
			        pdfExtract.setEndPageNo(0);
			        pdfExtract.setSortingOrder(SORT.BY_PAGE, SORT.BY_Y, SORT.BY_X);
			        ExtractedContent content = pdfExtract.extractAll();
			        
			        for(String sTypeExt : sOutputTypes)
			        {
			        	MetaData metaData = content.getMetaData();
				        
				        File fileOutput = new File(
				        		folderSaveOutput.getAbsolutePath()
				        		+"/extracted_"+metaData.getSourceFileName()+"."+sTypeExt);
				        
				        saveAsFile(content, fileOutput);
				        
				        long lElapsedMs = System.currentTimeMillis() - lStartTimeMs;
				        System.out.println("  Extracted "+metaData.getTotalPages()
				        					+" pages ("+sTypeExt+" "+lElapsedMs+" ms)");
			        }
			        
	        	}
        	}
        }
    }
}