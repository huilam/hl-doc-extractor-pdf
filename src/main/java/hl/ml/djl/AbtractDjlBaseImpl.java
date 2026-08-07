package hl.ml.djl;

import java.io.IOException;
import java.net.URL;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;

public abstract class AbtractDjlBaseImpl<I, O> {
	
	protected Criteria.Builder<I, O> criteria_builder	= null;
	protected Criteria<I, O> criteria 					= null;
	protected Predictor<I, O> predictor 				= null;
	protected ZooModel<I, O> model 						= null;
	//
	protected DjlModelConfig djl_model_config 			= null;
	protected boolean model_init_ok 					= false;
	//
	private Class<?> child_impl_class = null;
	
	@SuppressWarnings("rawtypes")
	protected AbtractDjlBaseImpl(
			Class aImplClass, 
			Criteria.Builder<I, O> aCriteriaBuilder)
	{
		this.child_impl_class = aImplClass;
		this.criteria_builder = aCriteriaBuilder;
	}
	
	public void setDjlModelConfig(DjlModelConfig aDjlModelConfig)
	{
		this.djl_model_config = aDjlModelConfig;
	}
	
	public void loadModel() {
		
		DjlModelConfig djlModelConfig 	= this.djl_model_config;
		Criteria.Builder<I, O> builder 	= this.criteria_builder;
		
		if(djlModelConfig.getModel_uri()==null)
		{
			URL url = child_impl_class.getProtectionDomain().getCodeSource().getLocation();
			String sModelFolder = url.toString()+child_impl_class.getPackageName().replace(".","/")+"/model/";
			djlModelConfig.setModel_uri( sModelFolder + djlModelConfig.getModel_name());
		}
		
		String sModelPath = djlModelConfig.getModel_uri();
		int iPos = sModelPath.indexOf(":");
		if(iPos>-1)
		{
			sModelPath = sModelPath.substring(iPos+1);
		}
		
		builder.optModelUrls(sModelPath);
		builder.optEngine(djlModelConfig.getRuntime_engine());
		
		if(djlModelConfig.getDevice_type()!=null)
			builder.optDevice(djlModelConfig.getDevice_type());
	        	
		if(djlModelConfig.getMLArgs()!=null && djlModelConfig.getMLArgs().size()>0)
	        builder.optArguments(djlModelConfig.getMLArgs());
		
		builder.optOptions(djlModelConfig.getOptions());
		
		if(djlModelConfig.getTranslator_factory()!=null)
			builder.optTranslatorFactory(djlModelConfig.getTranslator_factory());

		this.criteria = builder.build();
		
		try {
			this.model = this.criteria.loadModel();
			this.predictor = this.model.newPredictor();
			this.model_init_ok = true;
		} catch (ModelNotFoundException | MalformedModelException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
    public boolean isModelInitOk() {
		return this.model_init_ok;
	}
    
    public String getRt_engine() {
		return djl_model_config.getRuntime_engine();
	}

    public String getModel_name() {
		return djl_model_config.getModel_name();
	}
    
}