package hl.doc.extractor.pdf.extraction.model;

import java.awt.Color;
import java.awt.Paint;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;

import org.json.JSONArray;
import org.json.JSONObject;

public class VectorData {

	private Path2D vector_path = null;
	private int path_seg_count 		= 0;
	private float line_width 		= 0;
	private Color line_color 		= null;
	private Color fill_color 		= null;
	private Paint fill_pattern 		= null;
	
	
	public VectorData(Path2D aVectorShape)
	{
		this.vector_path = aVectorShape;
		init();
	}
	
	public VectorData(JSONObject aVectorJson)
	{
		fromJson(aVectorJson);
	}
	
	
	private void init()
	{
		PathIterator iterPath = vector_path.getPathIterator(null);
		int iSegCount = 0;
		while(!iterPath.isDone())
		{
			iSegCount++;
			iterPath.next();
		}
		this.path_seg_count = iSegCount;
	}
	//=====

	public Path2D getVector()
	{
		return this.vector_path;
	}
	
	public int getPathSegmentCount()
	{
		return this.path_seg_count;
	}
	
	public double getBoundSize()
	{
		Rectangle2D rect = getVector().getBounds();
		return rect.getWidth() * rect.getHeight();
	}
	//=====
	
	public void setLineWidth(float aLineWidth)
	{
		this.line_width = aLineWidth;
	}
	
	public float getLineWidth()
	{
		return this.line_width;
	}
	
	//=====
	
	public void setLineColor(Color aColor)
	{
		this.line_color = aColor;
	}
	
	public Color getLineColor()
	{
		return this.line_color;
	}
	
	//=====
	
	public void setFillColor(Color aColor)
	{
		this.fill_color = aColor;
	}
	
	public Color getFillColor()
	{
		return this.fill_color;
	}
	
	//=====
	
	public void setFillPattern(Paint aFillPattern)
	{
		this.fill_pattern = aFillPattern;
	}
	
	public Paint getFillPattern()
	{
		return this.fill_pattern;
	}
	
	//=====

	
	private static JSONArray colorToRGBarray(Color aColor)
	{
		if(aColor!=null)
		{
			JSONArray jArr = new JSONArray();
			jArr.put(aColor.getRed());
			jArr.put(aColor.getGreen());
			jArr.put(aColor.getBlue());
			return jArr;
		}
		
		return null;
	}
    
	private static Path2D stringToVectorPath(String s) {
		
		if(s==null)
			return null;
		
		Path2D path = new GeneralPath();

        for (String seg : s.split(";")) {
            if (seg.isEmpty()) continue;
            String[] parts = seg.split(",");
            int type = Integer.parseInt(parts[0]);

            switch (type) {
                case PathIterator.SEG_MOVETO ->
                    path.moveTo(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));

                case PathIterator.SEG_LINETO ->
                    path.lineTo(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));

                case PathIterator.SEG_QUADTO ->
                    path.quadTo(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3]), Float.parseFloat(parts[4]));

                case PathIterator.SEG_CUBICTO ->
                    path.curveTo(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]),
                                 Float.parseFloat(parts[3]), Float.parseFloat(parts[4]),
                                 Float.parseFloat(parts[5]), Float.parseFloat(parts[6]));

                case PathIterator.SEG_CLOSE ->
                    path.closePath();

                default ->
                    throw new IllegalArgumentException("Unknown segment: " + type);
            }
        }

        return path;
    }
    
	private static String vectorPathToString(Path2D path) {
        StringBuilder sb = new StringBuilder();
        PathIterator it = path.getPathIterator(null);

        float[] coords = new float[6];
        while (!it.isDone()) {
            int type = it.currentSegment(coords);
            sb.append(type);
            for (int i = 0; i < coordCount(type); i++) {
                sb.append(',').append(coords[i]);
            }
            sb.append(';'); // segment separator
            it.next();
        }
        return sb.toString();
    }

    // Number of coords per segment type
    private static int coordCount(int segmentType) {
        return switch (segmentType) {
            case PathIterator.SEG_MOVETO -> 2;
            case PathIterator.SEG_LINETO -> 2;
            case PathIterator.SEG_QUADTO -> 4;
            case PathIterator.SEG_CUBICTO -> 6;
            case PathIterator.SEG_CLOSE -> 0;
            default -> throw new IllegalArgumentException("Unknown segment");
        };
    }
	
	public JSONObject toJson()
	{
		JSONObject json = new JSONObject();
		json.put("vector_path",	vectorPathToString(getVector()));
		json.put("line_width",	getLineWidth());
		json.put("line_color",	colorToRGBarray(getLineColor()));
		json.put("fill_color",	colorToRGBarray(getFillColor()));
		//json.put("fill_pattern",getFillPattern());
		return json;
	}
	
	public void fromJson(JSONObject aJson)
	{
		this.vector_path = stringToVectorPath(aJson.optString("vector_path",null));;
		this.setLineWidth(aJson.optInt("line_width",0));
		
		JSONArray jarLC = aJson.optJSONArray("line_color",null);
		if(jarLC!=null && jarLC.length()==3)
		{
			this.setLineColor(new Color(jarLC.getInt(0), jarLC.getInt(1), jarLC.getInt(2)));
		}
		
		JSONArray jarFC = aJson.optJSONArray("fill_color",null);
		if(jarFC!=null && jarFC.length()==3)
		{
			this.setFillColor(new Color(jarFC.getInt(0), jarFC.getInt(1), jarFC.getInt(2)));
		}
		
		//this.setFillPattern(aJson.optString("fill_pattern",null));
		init();
	}
    
    public String toString()
    {
    	return toJson().toString();
    }
    
}