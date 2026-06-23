package hl.ml.djl;

import java.util.HashMap;
import java.util.Map;

import ai.djl.Device;
import ai.djl.translate.TranslatorFactory;

public class DjlModelConfig {
	
	public static final String RT_ENGINE_ONNX 		= "OnnxRuntime";
	public static final String RT_ENGINE_PYTORCH 	= "PyTorch";
	
	private String model_name 				= null;
	private String model_uri 				= null;
	private String runtime_engine 			= DjlConstants.RT_ENGINE_ONNX;
	//
	private Device device_type				= null;
	private TranslatorFactory translator_factory 	= null;
	private Map<String, Object> mapMLArgs 			= new HashMap<>();
	private Map<String, String> mapOptions 			= new HashMap<>();
	
	
	public String getModel_name() {
		return model_name;
	}
	public void setModel_name(String model_name) {
		this.model_name = model_name;
	}
	
	public TranslatorFactory getTranslator_factory() {
		return translator_factory;
	}
	public void setTranslator_factory(TranslatorFactory translator_factory) {
		this.translator_factory = translator_factory;
	}
	public String getModel_uri() {
		return model_uri;
	}
	public void setModel_uri(String model_uri) {
		this.model_uri = model_uri;
	}
	
	public String getRuntime_engine() {
		return runtime_engine;
	}
	public void setRuntime_engine(String runtime_engine) {
		this.runtime_engine = runtime_engine;
	}
	
	public Device getDevice_type() {
		return device_type;
	}
	
	public void setDevice_type(Device device_type) {
		this.device_type = device_type;
	}
	
	public void clearMLArgs()
	{
		mapMLArgs.clear();
	}
	
	public void addMLArg(String key, String value)
	{
		mapMLArgs.put(key, value);
	}
	
	public void removeMLArg(String key, String value)
	{
		mapMLArgs.remove(key, value);
	}
	
	public Map<String, Object> getMLArgs()
	{
		return mapMLArgs;
	}
	
	public void addOption(String key, String value)
	{
		mapOptions.put(key, value);
	}
	
	public void removeOption(String key, String value)
	{
		mapOptions.remove(key, value);
	}
	
	public Map<String, String> getOptions()
	{
		return mapOptions;
	}
	
}