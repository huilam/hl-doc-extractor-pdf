package hl.doc.extractor.pdf.extraction.example;

import hl.doc.extractor.pdf.extraction.PDFExtractor;
import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.model.ExtractedData;
import hl.doc.extractor.pdf.extraction.util.ContentUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

public class TestSearchItems {

	public static Map<String, BufferedImage> searchAndHighlight(File aPdfDile, int aPageNo, List<String> aSearchTextList) throws IOException
	{
		Map<String, BufferedImage> mapRenderedPage = new HashMap<>();
		PDFExtractor pdfExtract = null;
		try
		{
			pdfExtract = new PDFExtractor(aPdfDile);
			pdfExtract.setExtractText(true);
	        pdfExtract.setExtractImage(true);
	        ExtractedData data = null;
	        
	        if(aPageNo>0)
	        	data = pdfExtract.extractPage(aPageNo);
	        else
	        	data = pdfExtract.extractAll();
	        
	        System.out.print("    - Searching "+data.getContentItemList().size()+" items ... ");
	        Map<Integer, List<ContentItem>> mapMatchedItems = ContentUtil.searchItems(data, aSearchTextList);
	        System.out.println(mapMatchedItems.size()+" matches");
	        
	        for(Integer iPageNo : mapMatchedItems.keySet())
	        {
	        	float scale = 1.5f;
	        	
	        	BufferedImage imgPage = pdfExtract.renderPagePreview(iPageNo, scale);
	        	if(imgPage!=null)
	        	{
	        		List<ContentItem> listPageItems = mapMatchedItems.get(iPageNo);
	        		imgPage = ContentUtil.highlightItems(imgPage, listPageItems, scale);
	        		System.out.println("       * Page "+iPageNo+" matched:"+listPageItems.size());
	        		
	        		String sFilename = "p"+iPageNo+"_"+listPageItems.size()+".jpg";
	        		
	        		mapRenderedPage.put(sFilename, imgPage);
	        	}	
	        }

		}
		finally
		{
			if(pdfExtract!=null)
				pdfExtract.release();
		}
		
		return mapRenderedPage;
	}
	
    //=========================================================== 
    public static void main(String[] args) throws IOException{
        File folderInput 		= null;
        File folderOutput 		= null;
        
		folderInput 	= new File("test/");
		folderOutput	= new File(folderInput.getAbsolutePath()+"/output");

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
        			System.out.println("\n "+(iFileSeq++)+". Searching "+f.getName()+" ...");
        			
        			List<String> listSearchText = new ArrayList<>();
        			//listSearchText.add("PDFBox");
		        	listSearchText.add("is an open source pure-Java library");
		        	listSearchText.add("that can be used to create, render, print, split, merge,");
		        	listSearchText.add("alter, verify and extract text and meta-data of PDF");
		        	listSearchText.add("files.");
		        	listSearchText.add("![image](image_2_p1_343-149_188x41.png)");
		        	
		        	
        			Map<String, BufferedImage> mapMatchedPages = searchAndHighlight(f, -1, listSearchText);
        			
        			for(String sFileName : mapMatchedPages.keySet())
        			{
        				BufferedImage imgPage = mapMatchedPages.get(sFileName);
        				File fileImg = new File(folderSaveOutput.getAbsolutePath()+"/highlight_"+sFileName);
        				fileImg.getParentFile().mkdirs();
        				if(ImageIO.write(imgPage, "jpg", fileImg))
						{
							System.out.println("         + [saved] "+fileImg.getName());
						}
        			}
        							        
			        long lElapsedMs = System.currentTimeMillis() - lStartTimeMs;
			        System.out.println("    - Elapsed time "+lElapsedMs+" ms");
			        System.out.println();

	        	}
        	}
        }
    }
}