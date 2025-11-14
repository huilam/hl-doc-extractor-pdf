# hl-doc-extractor-pdf<br>

**A simple lightweight (250 KB*) Java PDF extractor based on [PDFBox](https://github.com/apache/pdfbox).**<br>
<sub>* 250 KB including PDFBox jar files.<br>
<br>
<br>
**Sample Code:**<br>
<br>
//## Initialize PDF Extract with a file<br>
PDFExtractor extractor = new PDFExtractor(new File("file.pdf"));<br>
extractor.setStartPageNo(0);<br>
extractor.setEndPageNo(0);<br>
<br>
//## Extract all selected pages<br>
ExtractedContent data = extractor.extractAll();<br>
<br>
//## Export to JSON <sup>[sample](samples/json/sample_extracted-json.json)</sup><br>
JSONObject jsonData = data.toJsonFormat(true);   //## true to include image base64<br>
<br>
<br>
//## Export to Plain Text <sup>[sample](samples/plaintext/sample_extracted-plaintext.txt)</sup> & Images <sup>[sample](samples/plaintext/image_1_p1_74-540_146x205.jpg)</sup><br>
JSONObject jsonData = data.toPlainTextFormat(true);   //## true to indicate page number<br>
Map<String,BufferedImage> mapImages = data.getExtractedBufferedImages();   //## <FileName, BufferedImage><br>
<br>
<br>
//## Render page layout <sup>[sample](samples/layout/sample_page_layout.jpg)</sup> as BufferedImage<br>
MetaData meta = data.getMetaData();<br>
List<ContentItem> page1Data = aExtractData.getContentItemListByPageNo(1);<br>
BufferedImage imgLayout = ContentUtil.renderPageLayout(<br>
&nbsp;&nbsp;&nbsp;&nbsp;meta.getPageWidth(), <br>
&nbsp;&nbsp;&nbsp;&nbsp;meta.getPageHeight(), <br>
&nbsp;&nbsp;&nbsp;&nbsp;Color.WHITE, //# background color<br>
&nbsp;&nbsp;&nbsp;&nbsp;false, //# render text<br>
&nbsp;&nbsp;&nbsp;&nbsp;page1Data); //# page's extracted item<br>
<br>
<br>
Sample Rendered Page Layout:<br>
![image](samples/sample_page_layout_preview.jpg)
