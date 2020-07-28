package quenfo.de.uni_koeln.spinfo.classification.zone_analysis.classifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;

import quenfo.de.uni_koeln.spinfo.classification.core.classifier.model.Model;
import quenfo.de.uni_koeln.spinfo.classification.core.data.ClassifyUnit;
import quenfo.de.uni_koeln.spinfo.classification.core.data.FeatureUnitConfiguration;
import quenfo.de.uni_koeln.spinfo.classification.core.feature_engineering.feature_weighting.AbstractFeatureQuantifier;
import quenfo.de.uni_koeln.spinfo.classification.jasc.data.JASCClassifyUnit;

/**
 * @author geduldia
 * 
 * a classifier based on regular expressions 
 *
 */
public class RegexClassifier extends ZoneAbstractClassifier {
	
	private static final long serialVersionUID = 1L;
	
	
	/**
	 * stores a category for each regular expression  
	 */
	private Map<String, Integer> regexes = new HashMap<String, Integer>();
	
	
	/**
	 * counts the number of matches for each regular expression
	 */
	Map<String, Integer> matchCount = new HashMap<String,Integer>();
	
	/**
	 * @return matchCount
	 */
	public Map<String, Integer> getMatchCount() {
		return matchCount;
	}



	/**
	 * @param matchCount
	 */
	public void setMatchCount(Map<String, Integer> matchCount) {
		this.matchCount = matchCount;
	}



	/**
	 * @param regexFile
	 * @throws IOException
	 */
	public RegexClassifier(String regexFile) throws IOException{
		BufferedReader in = new BufferedReader(new FileReader(new File(regexFile)));
		String line = in.readLine();
		while(line!= null){
			String[] splitted = line.split("\\t");
			StringBuffer sb = new StringBuffer();
			for(int i = 1; i < splitted.length; i++){
				sb.append(splitted[i]);
			}
			int classID;
			try{
				classID = Integer.parseInt(splitted[0]);
				regexes.put(sb.toString(), classID);
				matchCount.put(sb.toString(), 0);
			}
			catch(NumberFormatException e){
				
			}

			line = in.readLine();
		}
		in.close();
		
	}
	


	/* (non-Javadoc)
	 * @see de.uni_koeln.spinfo.bibb.jasc.classifier.AbstractClassifier#classify(de.uni_koeln.spinfo.bibb.jasc.data.ClassifyUnit, de.uni_koeln.spinfo.bibb.jasc.classifier.models.AbstractModel)
	 * 
	 */
	@Override
	public boolean[] classify(ClassifyUnit cu, Model model) {
		int numberOfClasses = ((JASCClassifyUnit) cu).getClassIDs().length;
		boolean[] toReturn = new boolean[numberOfClasses];
		String content = cu.getContent();
		for (String regex : regexes.keySet()) {
			
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher =  pattern.matcher(content.toLowerCase());
			if(matcher.find()){
				int count = matchCount.get(regex);
				count++;
				matchCount.put(regex, count);
				toReturn[regexes.get(regex)-1] = true;
			}
		}
		return toReturn;

	}
	
	/**
	 * @param regex
	 * @param classID
	 */
	public void addRegex(String regex, int classID){
		regexes.put(regex, classID);
	}

	@Override
	public Model buildModel(List<ClassifyUnit> cus,
			FeatureUnitConfiguration fuc, AbstractFeatureQuantifier fq,
			File dataFile) {
		return null;
	}



	@Override
	public Dao<? extends Model, ?> getModelDao(ConnectionSource connection) {
		//Regex Classifier has no model
		return null;
	}



	@Override
	public List<? extends Model> getPersistedModels(int configHash) {
		//Regex Classifier has no model
		return null;
	}

	

}
