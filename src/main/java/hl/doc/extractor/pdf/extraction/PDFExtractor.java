package hl.doc.extractor.pdf.extraction;

import java.io.File;
import java.io.IOException;
import java.util.List;

import hl.doc.extractor.pdf.extraction.base.AbstractExtractor;
import hl.doc.extractor.pdf.extraction.model.ContentItem;
import hl.doc.extractor.pdf.extraction.util.ContentUtil.SORT;

public class PDFExtractor extends AbstractExtractor
{

	public PDFExtractor(File aPDFFile) throws IOException {
		super(aPDFFile);
		
		//default config
		super.setSortingOrder(new SORT[] {SORT.BY_PAGE, SORT.BY_Y, SORT.BY_X});
	}

	@Override
	protected List<ContentItem> preSortProcess(List<ContentItem> aContentList) {
		// Empty
		return aContentList;
	}

	@Override
	protected List<ContentItem> postSortProcess(List<ContentItem> aContentList) {
		// Empty
		return aContentList;
	}

}