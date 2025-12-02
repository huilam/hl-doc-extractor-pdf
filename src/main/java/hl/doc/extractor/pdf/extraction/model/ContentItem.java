package hl.doc.extractor.pdf.extraction.model;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

public class ContentItem {
	//
	public enum Type { 
		TEXT 
		,IMAGE 
		,VECTOR 
	}
	//
	private Type type		= Type.TEXT;
	private String format	= "";
	private String tagname 	= "";
	private int extract_seq = -1;
	private int doc_seq 	= -1;
	private int page_no 	= -1;
	private int pg_line_seq = -1;
	private String data 	= "";
	private double group_no	= -1;
	private Rectangle2D rect= null;

	public ContentItem(Type type, String content, int pageno, 
			float x, float y, float width, float height) {
		Rectangle2D rect = new Rectangle2D.Double(x,y,width, height);
		init(type, content, pageno, rect);
    }
	
	public ContentItem(Type type, String content, int pageno, 
			int x, int y, int width, int height) {
		Rectangle rect = new Rectangle(x, y, width, height);
		init(type, content, pageno, rect);
    }
	
	public ContentItem(Type type, String content, int pageno, Rectangle2D rect2d) {
		init(type, content, pageno, rect2d);
    }
	
	private void init(Type type, String data, int pageno, Rectangle2D rect2d)
	{
        this.type = type; 
        this.page_no = pageno;
        this.rect = rect2d;
        this.data = data;
	}

	//
	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}
	
	public int getExtract_seq() {
		return extract_seq;
	}

	public void setExtract_seq(int extract_seq) {
		this.extract_seq = extract_seq;
	}

	public String getTagName() {
		return this.tagname!=null?this.tagname:"";
	}

	public void setTagName(String aTagName) {
		this.tagname = aTagName;
	}
	
	public String getContentFormat() {
		return this.format!=null?this.format:"";
	}

	public void setContentFormat(String aFormat) {
		this.format = aFormat;
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

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public double getGroup_no() {
		return group_no;
	}

	public void setGroup_no(double group_no) {
		this.group_no = group_no;
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
		return this.rect.getWidth();
	}

	public double getHeight() {
		return this.rect.getHeight();
	}
	
	public void setRect2D(Rectangle2D aNewRect)
	{
		this.rect = aNewRect;
	}
	
	public Rectangle2D getRect2D()
	{
		return this.rect;
	}
	
	public double getAreaSize()
	{
		return getWidth() * getHeight();
	}
	
	public String toString()
	{
		String sType = "unknown";
		switch(getType())
		{
			case Type.TEXT : 
				sType = "TEXT"; 
				break;
			case Type.IMAGE : 
				sType = "IMAGE"; 
				break;
			case Type.VECTOR : 
				sType = "VECTOR"; 
				break;
			default:
		}

		StringBuffer sb = new StringBuffer();
		sb.append("p").append(getPage_no());
		sb.append("\n").append(sType);
		sb.append("\n").append("{").append(getX1()).append(",").append(getY1()).append("} ");
		sb.append("\n").append(getWidth()).append("x").append(getHeight());
		sb.append("\n").append(getContentFormat());
		sb.append("\n").append(getData());
		return sb.toString();
	}
	
}