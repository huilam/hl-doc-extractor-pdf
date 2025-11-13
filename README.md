A simple lightweight (250 KB on disk) Java PDF extractor based on PDFBox.<br>
<br>
Sample Code:<br>
<br>
//## Initialize PDF Extract with a file<br>
PDFExtractor extractor = new PDFExtractor(new File("file.pdf"));<br>
extractor.setStartPageNo(0);<br>
extractor.setEndPageNo(0);<br>
<br>
//## Extract all selected pages<br>
ExtractedContent data = extractor.extractAll();<br>
<br>
//## Export to JSON<br>
JSONObject jsonData = data.toJsonFormat(true);   //## true to include image base64<br>
<br>
//## Export to Plain text & Images Map(FileName, BufferedImage)<br>
JSONObject jsonData = data.toPlainTextFormat(true);   //## true to indicate page number<br>
Map<String,BufferedImage> mapImages = data.getExtractedBufferedImages();<br>
