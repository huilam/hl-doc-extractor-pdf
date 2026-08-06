package hl.doc.extractor.pdf.extraction.util;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PathGeometryUtils {

    // Make sure this is PUBLIC
    public static Point2D[] getEndpoints(Path2D path) {
        PathIterator iterator = path.getPathIterator(null);
        double[] coords = new double[6];
        
        Point2D start = null;
        Point2D end = null;
        
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);
            
            if (type == PathIterator.SEG_MOVETO) {
                // Only set start once, in case the path has multiple sub-paths
                if (start == null) {
                    start = new Point2D.Double(coords[0], coords[1]);
                }
                end = new Point2D.Double(coords[0], coords[1]); 
            } 
            else if (type == PathIterator.SEG_LINETO) {
                end = new Point2D.Double(coords[0], coords[1]);
            } 
            else if (type == PathIterator.SEG_QUADTO) {
                // Quadratic curve ends at indices 2 and 3
                end = new Point2D.Double(coords[2], coords[3]);
            } 
            else if (type == PathIterator.SEG_CUBICTO) {
                // Cubic curve ends at indices 4 and 5
                end = new Point2D.Double(coords[4], coords[5]);
            }
            
            iterator.next();
        }
        
        return new Point2D[]{start, end};
    }

    // Make sure this is PUBLIC (This is what caused your error)
    public static boolean pointsTouch(Point2D p1, Point2D p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        
        // Use a tiny tolerance (0.1 pixels squared) to account for floating-point math errors
        double toleranceSq = 0.5; 
        return p1.distanceSq(p2) <= toleranceSq;
    }
    
    
    /**
     * Analyzes a Path2D to see if it forms a grid.
     * 
     * @param path The shape to analyze
     * @param minRows The minimum number of distinct horizontal lines required to be a grid (e.g., 3)
     * @param minCols The minimum number of distinct vertical lines required to be a grid (e.g., 3)
     * @return true if it is a grid, false otherwise
     */
    public static boolean isGrid(Path2D path, int minRows, int minCols) {
        if (path == null) return false;

        List<Double> horizontalLinesY = new ArrayList<>();
        List<Double> verticalLinesX = new ArrayList<>();
        
        int nonGridSegments = 0;
        int totalSegments = 0;

        PathIterator iterator = path.getPathIterator(null);
        double[] coords = new double[6];
        double lastX = 0, lastY = 0;

        // 1. Extract all lines and check their direction
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);

            if (type == PathIterator.SEG_MOVETO) {
                lastX = coords[0];
                lastY = coords[1];
            } 
            else if (type == PathIterator.SEG_LINETO) {
                double curX = coords[0];
                double curY = coords[1];
                totalSegments++;

                // Check if line is horizontal (Y values are almost the same)
                if (Math.abs(curY - lastY) < 1.0) {
                    horizontalLinesY.add(curY);
                } 
                // Check if line is vertical (X values are almost the same)
                else if (Math.abs(curX - lastX) < 1.0) {
                    verticalLinesX.add(curX);
                } 
                // It is a diagonal line
                else {
                    nonGridSegments++;
                }

                lastX = curX;
                lastY = curY;
            } 
            else if (type == PathIterator.SEG_QUADTO || type == PathIterator.SEG_CUBICTO) {
                // Grids generally do not contain curves
                nonGridSegments++;
                totalSegments++;
            }

            iterator.next();
        }

        // If there are too many diagonals or curves (e.g., more than 5% of the shape), it's not a strict grid
        if (totalSegments == 0 || (double) nonGridSegments / totalSegments > 0.05) {
            return false;
        }

        // 2. Count the DISTINCT lines (using a tolerance to group lines that are slightly offset)
        int uniqueRows = countDistinctLines(horizontalLinesY, 2.0);
        int uniqueCols = countDistinctLines(verticalLinesX, 2.0);

        // 3. Decide if it meets the criteria of a grid
        return uniqueRows >= minRows && uniqueCols >= minCols;
    }

    /**
     * Helper method to count unique grid lines, merging lines that are very close to each other.
     * (e.g., a line at Y=100.0 and Y=100.5 are the same visual row).
     */
    private static int countDistinctLines(List<Double> coordinates, double tolerance) {
        if (coordinates.isEmpty()) return 0;

        Collections.sort(coordinates);
        
        int distinctCount = 1;
        double currentGroupLine = coordinates.get(0);

        for (int i = 1; i < coordinates.size(); i++) {
            double coord = coordinates.get(i);
            
            // If the next line is further away than the tolerance, it's a new row/column
            if (coord - currentGroupLine > tolerance) {
                distinctCount++;
                currentGroupLine = coord;
            }
        }
        
        return distinctCount;
    }
}