package hl.doc.extractor.pdf.model;

public class ContentItem {
	//
	public enum Type { TEXT, IMAGE }
	//
	private Type type		= Type.TEXT;
	private int doc_seq 	= 0;
	private int page_no 	= 0;
	private int pg_line_seq = 0;
	private String content 	= "";
	private double seg		= 0;
	private double x1, y1	= 0;
	//private double x2, y2	= 0;
	private double w, h  	= 0;

	public ContentItem(Type type, String content, int pageno, float x, float y, float width, float height) {
        this.type = type; 
        this.page_no = pageno;
        this.x1 = x; 
        this.y1 = y; 
        //this.x2 = x1+width; 
        //this.y2 = y1+height; 
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

	public double getSeg() {
		return seg;
	}

	public void setSeg(double seg) {
		this.seg = seg;
	}

	public double getX1() {
		return x1;
	}

	public double getX2() {
		return x1 + getWidth();
	}

	public void setX1(double x) {
		this.x1 = x;
	}

	public double getY1() {
		return y1;
	}
	
	public double getY2() {
		return y1 + getHeight();
	}

	public void setY1(double y) {
		this.y1 = y;
	}

	public double getWidth() {
		return w;
	}

	public void setWidth(double w) {
		this.w = w;
	}

	public double getHeight() {
		return h;
	}

	public void setHeight(double h) {
		this.h = h;
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