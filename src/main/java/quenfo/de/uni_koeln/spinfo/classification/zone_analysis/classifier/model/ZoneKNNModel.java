package quenfo.de.uni_koeln.spinfo.classification.zone_analysis.classifier.model;

import java.util.HashMap;
import java.util.Map;

import quenfo.de.uni_koeln.spinfo.classification.core.classifier.AbstractClassifier;
import quenfo.de.uni_koeln.spinfo.classification.core.classifier.model.Model;
import quenfo.de.uni_koeln.spinfo.classification.zone_analysis.classifier.ZoneKNNClassifier;

/**
 * @author geduldia
 * 
 * a model-object based on the KNNClassifier
 *
 */
public class ZoneKNNModel extends Model {
	
	


	private static final long serialVersionUID = 1L;
	
	private Map<double[], boolean[]> trainingData = new HashMap<double[], boolean[]>();
	
	
	
	/**
	 * @return trainingData
	 */
	public Map<double[], boolean[]> getTrainingData() {
		return trainingData;
	}
	
	


	/**
	 * @param trainingData
	 */
	public void setTrainingData(Map<double[], boolean[]> trainingData){
		this.trainingData = trainingData;
	}


	public AbstractClassifier getClassifier(){
		return new ZoneKNNClassifier();
	}
	
	

}
