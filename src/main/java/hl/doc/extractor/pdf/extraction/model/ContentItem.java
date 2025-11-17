package hl.doc.extractor.pdf.extraction.model;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

public class ContentItem {
	//
	public enum Type { TEXT, IMAGE, RECT }
	//
	private Type type		= Type.TEXT;
	private String format	= null;
	private String tagname 	= null;
	private int doc_seq 	= 0;
	private int page_no 	= 0;
	private int pg_line_seq = 0;
	private String data 	= "";
	private double segment	= 0;
	private Rectangle2D rect= null;

	public ContentItem(Type type, String content, int pageno, 
			float x, float y, float width, float height) {
		Rectangle2D rect = new Rectangle2D() {
			
			@Override
			public boolean isEmpty() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public double getY() {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public double getX() {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public double getWidth() {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public double getHeight() {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public void setRect(double x, double y, double w, double h) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public int outcode(double x, double y) {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public Rectangle2D createUnion(Rectangle2D r) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Rectangle2D createIntersection(Rectangle2D r) {
				// TODO Auto-generated method stub
				return null;
			}
		};
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
	
	public String getTagName() {
		return this.tagname;
	}

	public void setTagName(String aTagName) {
		this.tagname = aTagName;
	}
	
	public String getContentFormat() {
		return this.format;
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

	public double getSegment_no() {
		return segment;
	}

	public void setSegment_no(double segno) {
		this.segment = segno;
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
	
	public Rectangle2D getRect2D()
	{
		return this.rect;
	}
	
	public String toString()
	{
		boolean isText = getType()==Type.TEXT;
		StringBuffer sb = new StringBuffer();
		sb.append("p").append(getPage_no()).append(" ");
		sb.append("{").append(getX1()).append(",").append(getY1()).append("} ");
		sb.append(getWidth()).append("x").append(getHeight());
		sb.append(" ").append(isText?"TEXT":"IMAGE");
		sb.append(" ").append(getData());
		return sb.toString();
	}
	
}