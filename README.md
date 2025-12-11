# hl-doc-extractor-pdf<br>

**A simple lightweight (&ast; 2.50 MB) Java PDF extractor based on [PDFBox](https://github.com/apache/pdfbox) that supports custom content-sorting orders.**<br>
<sub>* 250 KB including PDFBox jar files.<br>
<br>

**Sample Code:**<br>
<br>
&nbsp;&nbsp;//## Initialize PDF Extract with a file<br>
&nbsp;&nbsp;PDFExtractor extractor = new PDFExtractor\(new File\("file.pdf"\)\);<br>
&nbsp;&nbsp;&nbsp;&nbsp;extractor\.setSortingOrder(SORT.BY_PAGE, SORT.BY_Y, SORT.BY_X);  //## Optional item sorting order<br>
<br>
&nbsp;&nbsp;//## Extract PDF content by page<br>
&nbsp;&nbsp;ExtractedContent data = null;<br>
&nbsp;&nbsp;&nbsp;&nbsp;data = extractor\.extractAll(); //## all pages<br>
&nbsp;&nbsp;&nbsp;&nbsp;data = extractor\.extractPage(2); //## specified page - 2<br>
&nbsp;&nbsp;&nbsp;&nbsp;data = extractor\.extractPages(1,3); //## page range - 1 to 3<br>
<br>
&nbsp;&nbsp;//## Export to JSON <sup>[sample](samples/json/sample_extracted-json.json)</sup><br>
&nbsp;&nbsp;JSONObject jsonData = data\.toJsonFormat(true);   //## true to include image base64<br>
<br>
&nbsp;&nbsp;//## Export to Plain Text <sup>[sample](samples/plaintext/sample_extracted-plaintext.txt)</sup> & Images <sup>[sample](samples/plaintext/image_1_p1_74-540_146x205.jpg)</sup><br>
&nbsp;&nbsp;JSONObject jsonData = data\.toPlainTextFormat\(true\);   //## true to include page number<br>
&nbsp;&nbsp;&nbsp;&nbsp;Map<String,BufferedImage> mapImages = data.getExtractedBufferedImages\(\);   //## <FileName, BufferedImage><br>
<br>
&nbsp;&nbsp;//## Render page layout <sup>[sample](samples/layout/sample_page_layout.jpg)</sup> as BufferedImage<br>
&nbsp;&nbsp;MetaData meta = data\.getMetaData\(\);<br>
&nbsp;&nbsp;&nbsp;&nbsp;List<ContentItem> page1Data = aExtractData\.getContentItemListByPageNo\(1\);<br>
&nbsp;&nbsp;&nbsp;&nbsp;BufferedImage imgLayout = ContentUtil\.renderPageLayout\(<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;meta\.getPageWidth\(\), &nbsp;meta\.getPageHeight\(\), <br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Color\.WHITE, //# background color<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;false, //# render text<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;page1Data\); //# page's extracted item<br>
<br>
<br>
**Sample Rendered Page Layout:**<br>
![image](samples/sample_page_layout_preview.jpg)
