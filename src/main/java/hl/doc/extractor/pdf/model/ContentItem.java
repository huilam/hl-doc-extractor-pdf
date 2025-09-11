package hl.doc.extractor.pdf.model;

import java.awt.Rectangle;

public class ContentItem {
	//
	public enum Type { TEXT, IMAGE, RECT }
	//
	private Type type		= Type.TEXT;
	private int doc_seq 	= 0;
	private int page_no 	= 0;
	private int pg_line_seq = 0;
	private String content 	= "";
	private double segment	= 0;
	private Rectangle rect= null;

	public ContentItem(Type type, String content, int pageno, 
			float x, float y, float width, float height) {
		Rectangle rect = new Rectangle(
				Math.round(x), Math.round(y), 
				Math.round(width), Math.round(height));
		init(type, content, pageno, rect);
    }
	
	public ContentItem(Type type, String content, int pageno, Rectangle rect2d) {
		init(type, content, pageno, rect2d);
    }
	
	private void init(Type type, String content, int pageno, Rectangle rect2d)
	{
        this.type = type; 
        this.page_no = pageno;
        this.rect = rect2d;
        this.content = content;
	}

	//
	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public int getDoc_seq() {
		return doc_seq;
	}

	public void setDoc_seq(int doc_seq) {
		this.doc_seq = doc_seq;
	}

	public int getPage_no() {
		return page_no;
	}

	public void setPage_no(int page_no) {
		this.page_no = page_no;
	}

	public int getPg_line_seq() {
		return pg_line_seq;
	}

	public void setPg_line_seq(int pg_line_seq) {
		this.pg_line_seq = pg_line_seq;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public double getSegment_no() {
		return segment;
	}

	public void setSegment_no(double seg) {
		this.segment = seg;
	}

	public double getX1() {
		return this.rect.getX();
	}

	public double getX2() {
		return this.rect.getMaxX();
	}

	public double getY1() {
		return this.rect.getY();
	}
	
	public double getY2() {
		return this.rect.getMaxY();
	}

	public double getWidth() {
		return this.rect.width;
	}

	public double getHeight() {
		return this.rect.height;
	}
	
	public String toString()
	{
		boolean isText = getType()==Type.TEXT;
		StringBuffer sb = new StringBuffer();
		sb.append("p").append(getPage_no()).append(" ");
		sb.append("{").append(getX1()).append(",").append(getY1()).append("} ");
		sb.append(getWidth()).append("x").append(getHeight());
		sb.append(" ").append(isText?"TEXT":"IMAGE");
		sb.append(" ").append(getContent());
		return sb.toString();
	}
	
}