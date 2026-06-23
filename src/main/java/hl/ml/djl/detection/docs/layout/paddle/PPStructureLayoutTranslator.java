package hl.ml.djl.detection.docs.layout.paddle;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Use NoBatchifyTranslator to prevent DJL from adding an extra batch dimension
public class PPStructureLayoutTranslator implements NoBatchifyTranslator<Image, DetectedObjects> {

    private static final List<String> LAYOUT_CLASSES = Arrays.asList(
    		"abstract",          // 0
    	    "algorithm",         // 1
    	    "aside_text",        // 2
    	    "chart",             // 3
    	    "content",           // 4
    	    "display_formula",   // 5
    	    "doc_title",         // 6
    	    "figure_title",      // 7
    	    "footer",            // 8
    	    "footer_image",      // 9
    	    "footnote",          // 10
    	    "formula_number",    // 11
    	    "header",            // 12 -> Matches running headers in p4/p9
    	    "header_image",      // 13
    	    "image",             // 14
    	    "inline_formula",    // 15
    	    "number",            // 16 -> Matches the page numbers "6" and "7"
    	    "paragraph_title",   // 17 -> Matches section subtitles / headers
    	    "reference",         // 18
    	    "reference_content", // 19
    	    "seal",              // 20
    	    "table",             // 21
    	    "text",              // 22 -> Matches all column body text blocks
    	    "vertical_text",     // 23
    	    "vision_footnote"    // 24
    );

    private final int inputWidth;
    private final int inputHeight;
    private final float threshold;

    public PPStructureLayoutTranslator() {
        // CHANGED: Match the model's expected fixed 800x800 input dimension
        this.inputWidth = 800;
        this.inputHeight = 800;
        this.threshold = 0.5f;
    
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Image input) {
    	ctx.setAttachment("input", input); // Save image reference for output mapping
        
        int originalWidth = input.getWidth();
        int originalHeight = input.getHeight();

        // 1. Image preprocessing
        NDArray array = input.toNDArray(ctx.getNDManager(), Image.Flag.COLOR);
        array = ai.djl.modality.cv.util.NDImageUtils.resize(array, inputWidth, inputHeight);
        
        array = array.toType(DataType.FLOAT32, false).div(255.0f);
        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};
        NDArray meanArray = ctx.getNDManager().create(mean).reshape(1, 1, 3);
        NDArray stdArray = ctx.getNDManager().create(std).reshape(1, 1, 3);
        array = array.sub(meanArray).div(stdArray);

        // 2. Shape to exact CHW format
        if (array.getShape().dimension() == 4) {
            array = array.transpose(0, 3, 1, 2); 
        } else {
            array = array.transpose(2, 0, 1);    
        }
        
        // Ensure exactly 4D [1, C, H, W]
        if (array.getShape().dimension() == 3) {
            array = array.expandDims(0);
        }
        array.setName("image");

        // 3. Setup metadata inputs expected by PaddlePaddle
        float[] imShapeValues = {(float) inputHeight, (float) inputWidth};
        NDArray imShape = ctx.getNDManager().create(imShapeValues).reshape(1, 2);
        imShape.setName("im_shape");

        float scaleY = (float) inputHeight / originalHeight;
        float scaleX = (float) inputWidth / originalWidth;
        float[] scaleFactorValues = {scaleY, scaleX};
        NDArray scaleFactor = ctx.getNDManager().create(scaleFactorValues).reshape(1, 2);
        scaleFactor.setName("scale_factor");

        // Return raw list without DJL batchification intervention
        return new NDList(imShape, array, scaleFactor);
    }
    
    @Override
    public DetectedObjects processOutput(TranslatorContext ctx, NDList list) {
        NDArray results = list.get(0); 
        
        if (results.getShape().dimension() == 3) {
            results = results.get(0); 
        }

        List<String> classNames = new ArrayList<>();
        List<Double> probabilities = new ArrayList<>();
        List<BoundingBox> boundingBoxes = new ArrayList<>();

        long numBoxes = results.getShape().get(0);
        float[] floatResults = results.toFloatArray();
        int elementsPerRow = (int) (floatResults.length / numBoxes);

        // Fetch original image dimensions stored during processInput
        Image inputImage = (Image) ctx.getAttachment("input");
        double imgWidth = inputImage != null ? inputImage.getWidth() : inputWidth;
        double imgHeight = inputImage != null ? inputImage.getHeight() : inputHeight;

        for (int i = 0; i < numBoxes; i++) {
            int offset = i * elementsPerRow;
            
            int classId = (int) floatResults[offset];
            float score = floatResults[offset + 1];
            
            if (score >= threshold) {
                float xMin = floatResults[offset + 2];
                float yMin = floatResults[offset + 3];
                float xMax = floatResults[offset + 4];
                float yMax = floatResults[offset + 5];

                // 1. First clip the raw predictions safely inside image limits
                xMin = Math.max(0f, Math.min((float) imgWidth, xMin));
                yMin = Math.max(0f, Math.min((float) imgHeight, yMin));
                xMax = Math.max(xMin, Math.min((float) imgWidth, xMax));
                yMax = Math.max(yMin, Math.min((float) imgHeight, yMax));

                // 2. Compute exact image width and height in absolute pixels
                double pixelX = xMin;
                double pixelY = yMin;
                double pixelW = xMax - xMin;
                double pixelH = yMax - yMin;

                String className = classId < LAYOUT_CLASSES.size() ? LAYOUT_CLASSES.get(classId) : "unknown_" + classId;
                
                classNames.add(className);
                probabilities.add((double) score);
                
                // 3. Create absolute coordinate Rectangle instead of relative
                boundingBoxes.add(new Rectangle(pixelX, pixelY, pixelW, pixelH));
            }
        }

        return new DetectedObjects(classNames, probabilities, boundingBoxes);
    }
}