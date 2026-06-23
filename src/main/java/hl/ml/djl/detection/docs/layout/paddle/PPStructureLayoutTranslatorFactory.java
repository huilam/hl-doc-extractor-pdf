package hl.ml.djl.detection.docs.layout.paddle;
import ai.djl.Model;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import ai.djl.translate.TranslateException;
import ai.djl.util.Pair;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class PPStructureLayoutTranslatorFactory implements TranslatorFactory {

    @Override
    public Set<Pair<Type, Type>> getSupportedTypes() {
        return Collections.singleton(new Pair<>(Image.class, DetectedObjects.class));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I, O> Translator<I, O> newInstance(
            Class<I> input, 
            Class<O> output, 
            Model model, 
            Map<String, ?> arguments) throws TranslateException {
        
        // USE isAssignableFrom INSTEAD OF == TO BE SUBCLASS COMPATIBLE
        if (Image.class.isAssignableFrom(input) && DetectedObjects.class.isAssignableFrom(output)) {
            return (Translator<I, O>) new PPStructureLayoutTranslator();
        }
        
        throw new IllegalArgumentException("Unsupported mapping framework configuration parameters inside factory matrix.");
    }
}