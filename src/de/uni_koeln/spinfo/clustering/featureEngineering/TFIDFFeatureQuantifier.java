package de.uni_koeln.spinfo.clustering.featureEngineering;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TFIDFFeatureQuantifier<E> extends AbstractFeatureQuantifier<E> {

	public Map<E, double[]> getFeatureVectors(Map<E, List<String>> documentsByKey) {
		//set FeatureUnitOrder
		this.featureUnitOrder = getFeatureUnitOrder(documentsByKey.values());
		//calc termFrequencies
		Map<E,Map<String,Integer>> termFrequencies = getTermFrequencies(documentsByKey);
		
		Map<String,Integer> docFrequencies = getDocumentFrequencies(documentsByKey);
		
		Map<E,double[]> vectors = new HashMap<E, double[]>();
		
		for (E key : documentsByKey.keySet()) {
			System.out.println(key);
			double[] vector = new double[featureUnitOrder.size()];
			for (int i = 0; i < featureUnitOrder.size(); i++) {
				String feature = featureUnitOrder.get(i);
				if(termFrequencies.get(key).containsKey(feature)){
					int tf = termFrequencies.get(key).get(feature);
					int df = docFrequencies.get(feature);
					double idf = (double)Math.log((double)documentsByKey.size()/df);
					double ntf = ((double)(tf) / maxTF);
					double tfidf = ntf * idf;
					vector[i] = tfidf;
				}	
			}
			vectors.put(key, vector);
		}
		return vectors;
	}

}