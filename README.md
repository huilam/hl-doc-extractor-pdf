# hl-doc-extractor-pdf<br>

**A simple lightweight (&ast; 250 KB) Java PDF extractor based on [PDFBox](https://github.com/apache/pdfbox).**<br>
<sub>* 250 KB including PDFBox jar files.<br>
<br>
**Sample Code:**<br>
<br>
&nbsp;&nbsp;//## Initialize PDF Extract with a file<br>
&nbsp;&nbsp;PDFExtractor extractor = new PDFExtractor\(new File\("file.pdf"\)\);<br>
&nbsp;&nbsp;extractor\.setStartPageNo\(0\);<br>
&nbsp;&nbsp;extractor\.setEndPageNo\(0\);<br>
<br>
&nbsp;&nbsp;//## Extract all selected pages<br>
&nbsp;&nbsp;ExtractedContent data = extractor\.extractAll();<br>
<br>
&nbsp;&nbsp;//## Export to JSON <sup>[sample](samples/json/sample_extracted-json.json)</sup><br>
&nbsp;&nbsp;JSONObject jsonData = data\.toJsonFormat(true);   //## true to include image base64<br>
<br>
&nbsp;&nbsp;//## Export to Plain Text <sup>[sample](samples/plaintext/sample_extracted-plaintext.txt)</sup> & Images <sup>[sample](samples/plaintext/image_1_p1_74-540_146x205.jpg)</sup><br>
&nbsp;&nbsp;JSONObject jsonData = data\.toPlainTextFormat\(true\);   //## true to include page number<br>
&nbsp;&nbsp;Map<String,BufferedImage> mapImages = data.getExtractedBufferedImages\(\);   //## <FileName, BufferedImage><br>
<br>
&nbsp;&nbsp;//## Render page layout <sup>[sample](samples/layout/sample_page_layout.jpg)</sup> as BufferedImage<br>
&nbsp;&nbsp;MetaData meta = data\.getMetaData\(\);<br>
&nbsp;&nbsp;List<ContentItem> page1Data = aExtractData\.getContentItemListByPageNo\(1\);<br>
&nbsp;&nbsp;BufferedImage imgLayout = ContentUtil\.renderPageLayout\(<br>
&nbsp;&nbsp; &nbsp;&nbsp;&nbsp;&nbsp;meta\.getPageWidth\(\), &nbsp;meta\.getPageHeight\(\), <br>
&nbsp;&nbsp; &nbsp;&nbsp;&nbsp;&nbsp;Color\.WHITE, //# background color<br>
&nbsp;&nbsp; &nbsp;&nbsp;&nbsp;&nbsp;false, //# render text<br>
&nbsp;&nbsp; &nbsp;&nbsp;&nbsp;&nbsp;page1Data\); //# page's extracted item<br>
<br>
<br>
**Sample Rendered Page Layout:**<br>
![image](samples/sample_page_layout_preview.jpg)
