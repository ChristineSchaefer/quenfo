package quenfo.de.uni_koeln.spinfo.classification.zone_analysis.workflow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
//import org.apache.log4j.Logger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import quenfo.de.uni_koeln.spinfo.classification.core.classifier.model.Model;
import quenfo.de.uni_koeln.spinfo.classification.core.data.ClassifyUnit;
import quenfo.de.uni_koeln.spinfo.classification.core.data.ExperimentConfiguration;
import quenfo.de.uni_koeln.spinfo.classification.core.data.FeatureUnitConfiguration;
import quenfo.de.uni_koeln.spinfo.classification.core.feature_engineering.FeatureUnitTokenizer;
import quenfo.de.uni_koeln.spinfo.classification.core.feature_engineering.feature_reduction.MutualInformationFilter;
import quenfo.de.uni_koeln.spinfo.classification.core.feature_engineering.feature_reduction.Normalizer;
import quenfo.de.uni_koeln.spinfo.classification.core.feature_engineering.feature_reduction.Stemmer;
import quenfo.de.uni_koeln.spinfo.classification.core.feature_engineering.feature_reduction.StopwordFilter;
import quenfo.de.uni_koeln.spinfo.classification.core.feature_engineering.feature_selection.LetterNGrammGenerator;
import quenfo.de.uni_koeln.spinfo.classification.core.feature_engineering.feature_selection.SuffixTreeFeatureGenerator;
import quenfo.de.uni_koeln.spinfo.classification.core.feature_engineering.feature_weighting.AbstractFeatureQuantifier;
import quenfo.de.uni_koeln.spinfo.classification.core.helpers.ClassifyUnitFilter;
import quenfo.de.uni_koeln.spinfo.classification.core.helpers.EncodingProblemTreatment;
import quenfo.de.uni_koeln.spinfo.classification.jasc.data.JASCClassifyUnit;
import quenfo.de.uni_koeln.spinfo.classification.zone_analysis.classifier.ZoneAbstractClassifier;
import quenfo.de.uni_koeln.spinfo.classification.zone_analysis.helpers.SingleToMultiClassConverter;
import quenfo.de.uni_koeln.spinfo.classification.zone_analysis.preprocessing.TrainingDataGenerator;
import quenfo.de.uni_koeln.spinfo.core.helpers.PropertiesHandler;

public class ZoneJobs {

//	private static Logger log = Logger.getLogger(ZoneJobs.class);
	private Logger log = LogManager.getLogger();
	@Deprecated
	public ZoneJobs() throws IOException {
		log.info("ZoneJobs: Achtung - keine Translations gesetzt");
		sw_filter = new StopwordFilter(new File(PropertiesHandler.getStopwords()));
		normalizer = new Normalizer();
		stemmer = new Stemmer();
		tokenizer = new FeatureUnitTokenizer();
		suffixTreeBuilder = new SuffixTreeFeatureGenerator();
	}

	public ZoneJobs(SingleToMultiClassConverter stmc) throws IOException {
		if (stmc == null) {
			log.info("ZoneJobs: Achtung - keine Translations gesetzt");
		}
		this.stmc = stmc;
		sw_filter = new StopwordFilter(new File(PropertiesHandler.getStopwords()));
		normalizer = new Normalizer();
		stemmer = new Stemmer();
		tokenizer = new FeatureUnitTokenizer();
		suffixTreeBuilder = new SuffixTreeFeatureGenerator();
		JASCClassifyUnit.setNumberOfCategories(stmc.getNumberOfCategories(),
				stmc.getNumberOfClasses(), stmc.getTranslations());

	}

	protected SuffixTreeFeatureGenerator suffixTreeBuilder;
	protected StopwordFilter sw_filter;
	protected Normalizer normalizer;
	protected Stemmer stemmer;
	protected FeatureUnitTokenizer tokenizer;
	private SingleToMultiClassConverter stmc;
	MutualInformationFilter mi_filter = new MutualInformationFilter();

	/**
	 * A method to get pre-categorized paragraphs from the specified file
	 * 
	 * @param trainingDataFile file with pre-categorized paragraphs
	 * @return A list of pre-categorized paragraphs
	 * @throws IOException
	 */
	public List<ClassifyUnit> getCategorizedParagraphsFromFile(File trainingDataFile, boolean treatEncoding)
			throws IOException {
		TrainingDataGenerator tdg = new TrainingDataGenerator(trainingDataFile, stmc.getNumberOfCategories(),
				stmc.getNumberOfClasses(), stmc.getTranslations());

		List<ClassifyUnit> paragraphs = tdg.getTrainingData();

		if (treatEncoding) {
			for (ClassifyUnit classifyUnit : paragraphs) {
				String content = classifyUnit.getContent();
				classifyUnit.setContent(EncodingProblemTreatment.normalizeEncoding(content));
			}
		}

		return paragraphs;
	}

	@Deprecated
	public List<ClassifyUnit> getCategorizedParagraphsFromDB(Connection trainingConnection, boolean treatEncoding)
			throws ClassNotFoundException, SQLException {

		List<ClassifyUnit> toReturn = new ArrayList<ClassifyUnit>();
		Statement stmt = trainingConnection.createStatement();
		String sql = "SELECT Jahrgang, ZEILENNR, Text, ClassONE, ClassTWO, ClassTHREE, ClassFOUR  FROM trainingData";
		ResultSet result = stmt.executeQuery(sql);
		ClassifyUnit cu;
		while (result.next()) {
			String content = result.getString(3);
			int jahrgang = result.getInt(1);
			String postingID = result.getString(2);
			boolean[] classIDs = new boolean[stmc.getNumberOfCategories()];
			for (int i = 0; i < stmc.getNumberOfCategories(); i++) {
				classIDs[i] = parseIntToBool(result.getInt(4 + i));
			}
			JASCClassifyUnit.setNumberOfCategories(stmc.getNumberOfCategories(), stmc.getNumberOfClasses(),
					stmc.getTranslations());
			if (treatEncoding)
				content = EncodingProblemTreatment.normalizeEncoding(content);
			
			cu = new JASCClassifyUnit(content, jahrgang, postingID);

			((JASCClassifyUnit) cu).setClassIDs(classIDs);
			toReturn.add(cu);
		}
		return toReturn;
	}

	private boolean parseIntToBool(int toParse) {
		if (toParse == 0) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Initializes the feature units (as tokens) of each classify unit.
	 * 
	 * @param paragraphs Classify units to initialize
	 * @return Classify units with initialized feature units (tokens)
	 */
	public List<ClassifyUnit> initializeClassifyUnits(List<ClassifyUnit> paragraphs) {
		List<ClassifyUnit> toProcess = new ArrayList<ClassifyUnit>();
		for (ClassifyUnit paragraph : paragraphs) {
			List<String> tokens = tokenizer.tokenize(paragraph.getContent());

			if (tokens == null) 
				continue;
			
			paragraph.setFeatureUnits(tokens);
			toProcess.add(paragraph);
		}
		return toProcess;
	}

	/**
	 * Sets the feature units of each classify unit by following the instructions
	 * within the specified feature unit configuration.
	 * 
	 * @param paragraphs Classify units with initialized features
	 * @param fuc        feature unit configuration
	 * @return Classify units with features
	 * @throws IOException
	 */
	public List<ClassifyUnit> setFeatures(List<ClassifyUnit> paragraphs, FeatureUnitConfiguration fuc,
			boolean trainingPhase) throws IOException {

		for (ClassifyUnit cu : paragraphs) {
			// if(fuc.isTreatEncoding()){
			// List<String> normalizeEncoding =
			// EncodingProblemTreatment.normalizeEncoding(cu.getFeatureUnits());
			// cu.setFeatureUnits(normalizeEncoding);
			// }
			if (fuc.isNormalize()) {
				normalizer.normalize(cu.getFeatureUnits());
			}
			if (fuc.isFilterStopwords()) {
				// System.out.println("Stopwords filtered");
				List<String> filtered = sw_filter.filterStopwords(cu.getFeatureUnits());
				cu.setFeatureUnits(filtered);
			}
			if (fuc.isStem()) {
				// System.out.println("Stemmed");
				List<String> stems = stemmer.getStems(cu.getFeatureUnits());
				cu.setFeatureUnits(stems);
			}
			int[] ngrams2 = fuc.getNgrams();
			if (ngrams2 != null) {
				// System.out.println(" GramsCont: " + continuusNgrams);
				// useNGrams(cu, nGramLength, continuusNgrams);
				List<String> ngrams = new ArrayList<String>();
				for (int i : ngrams2) {
					ngrams.addAll(LetterNGrammGenerator.getNGramms(cu.getFeatureUnits(), i, fuc.isContinuusNGrams()));
				}
				cu.setFeatureUnits(ngrams);
			}
			
//			System.out.println(cu.getFeatureUnits());

		}
		if (fuc.getMiScore() != 0) {

			if (trainingPhase) {
				mi_filter.initialize(fuc, paragraphs);
			}
			mi_filter.filter(paragraphs, fuc.getMiScore());
		}
		if (fuc.isSuffixTree()) {
			paragraphs = suffixTreeBuilder.getSuffixTreeFreatures(paragraphs);
		}
		List<ClassifyUnit> filtered = ClassifyUnitFilter.filterByFUs(paragraphs, 1);
		return filtered;
	}

	/**
	 * Initializes the feature vectors of the classify units.
	 * 
	 * @param paragraphs       Classify units with features
	 * @param fq               The feature quantifier that should be used.
	 * @param featureUnitOrder Pre-determined oder of pre-determined features. If
	 *                         null, a new fuo will be generated using the specified
	 *                         features.
	 * @return Classify units with feature vectors
	 */
	public List<ClassifyUnit> setFeatureVectors(List<ClassifyUnit> paragraphs, AbstractFeatureQuantifier fq,
			List<String> featureUnitOrder) {
		if (fq != null) {
			// initialisieren mit trainingdata...
			fq.setFeatureValues(paragraphs, featureUnitOrder);
		}
		return paragraphs;
	}


	/**
	 * @param cus       the classify units
	 * @param expConfig the experiment configuration
	 * @return a model for the specified experiment configuration
	 * @throws IOException
	 */
	public Model getNewModelForClassifier(List<ClassifyUnit> cus, ExperimentConfiguration expConfig)
			throws IOException {		

		// build model...
		Model model = expConfig.getClassifier().buildModel(cus, expConfig.getFeatureConfiguration(),
				expConfig.getFeatureQuantifier(), expConfig.getDataFile());
		// store model
		// exportModel(expConfig.getModelFile(), model);
		model.setConfigHash(expConfig.hashCode());
		
		return model;

	}

	/**
	 * Classifies the specified classify units with the specified classifier, based
	 * on the specified model
	 * 
	 * @param paragraphs Units to classify
	 * @param classifier Classifier to use
	 * @param model      Model to train the classifier
	 * @return A map of all classify units as key and the guessed categories as
	 *         values
	 */

	public Map<ClassifyUnit, boolean[]> classify(List<ClassifyUnit> paragraphs, ExperimentConfiguration expConfig,
			Model model) {
		ZoneAbstractClassifier classifier = (ZoneAbstractClassifier) expConfig.getClassifier();

		Map<ClassifyUnit, boolean[]> classified = new HashMap<ClassifyUnit, boolean[]>();
		for (ClassifyUnit cu : paragraphs) {
			boolean[] classes = ((ZoneAbstractClassifier) classifier).classify(cu, model);
			classified.put(cu, classes);
		}
		return classified;
	}

	/**
	 * Merges the results of the second map into the first map. For every classify
	 * unit each class will be matched that matches in the first OR in the second
	 * map.
	 * 
	 * @param all   A map containg all classify units
	 * @param other A map that should not contain more or other classify units than
	 *              the
	 * @return A map of all classify units as key and the guessed categories as
	 *         values
	 */
	public Map<ClassifyUnit, boolean[]> mergeResults(Map<ClassifyUnit, boolean[]> all,
			Map<ClassifyUnit, boolean[]> other) {
		Map<ClassifyUnit, boolean[]> toReturn = new HashMap<ClassifyUnit, boolean[]>();
		Set<ClassifyUnit> allUnits = all.keySet();
		for (ClassifyUnit classifyUnit : allUnits) {

			boolean[] otherCats = other.get(classifyUnit);
			boolean[] allCats = all.get(classifyUnit);
			boolean[] toReturnCats = new boolean[allCats.length];

			for (int i = 0; i < allCats.length; i++) {
				if (allCats[i]) {
					toReturnCats[i] = true;
				}
				if (otherCats != null && otherCats[i]) {

					toReturnCats[i] = true;
				}
			}
			toReturn.put(classifyUnit, toReturnCats);
			// for (int i = 0; i < toReturnCats.length; i++) {
			// if(toReturnCats[i] != allCats[i]){
			// System.out.println(classifyUnit.getContent());
			// System.out.println(((ZoneClassifyUnit)
			// classifyUnit).getActualClassID());
			// System.out.println("difference");
			// System.out.println("all");
			// for (int j = 0; j < toReturnCats.length; j++) {
			// System.out.print(allCats[j]+" ");
			// }
			// System.out.println();
			// System.out.println("pre");
			// for (int j = 0; j < toReturnCats.length; j++) {
			// System.out.print(otherCats[j]+" ");
			// }
			// System.out.println();
			// System.out.println("merged");
			// for (int j = 0; j < toReturnCats.length; j++) {
			// System.out.print(toReturnCats[j]+" ");
			// }
			// System.out.println();
			// }
			// }
		}
		return toReturn;
	}

	/**
	 * Translates the boolean arrays of the specified map (key and value) into
	 * possibly-multiple-true-values-boolean arrays (Short: It translates
	 * single-value-6-classes to multiple-value-4-classes)
	 * 
	 * @param untranslated A map with boolean arrays each containing no more than
	 *                     one true value (6 single-value classes)
	 * @return A map with boolean arrays each possibly containing more than one true
	 *         value (4 multiple-value classes)
	 */
	public Map<ClassifyUnit, boolean[]> translateClasses(Map<ClassifyUnit, boolean[]> untranslated) {
		Map<ClassifyUnit, boolean[]> translated = new HashMap<ClassifyUnit, boolean[]>();
		Set<ClassifyUnit> untransCUs = untranslated.keySet();

		// log.info(keySet.toString());

		for (ClassifyUnit classifyUnit : untransCUs) {
			JASCClassifyUnit jcu = (JASCClassifyUnit) classifyUnit;

			// log.info(zcu.toString());
			boolean[] classIDs = jcu.getClassIDs();

			int singleClassID = -1;
			for (int i = 0; i < classIDs.length; i++) {
//				System.out.print(classIDs[i] + " ");
				if (classIDs[i])
					singleClassID = i + 1;
			}
			// System.out.print(" -> " + singleClassID + " -> ");
			boolean[] multiClasses = stmc.getMultiClasses(singleClassID);

//			for (int i = 0; i < multiClasses.length; i++) {
//				System.out.print(multiClasses[i] + " ");
//			}

			jcu.setClassIDs(multiClasses);

			boolean[] newClassIDs = untranslated.get(jcu);
//			log.info(untranslated.containsKey(zcu));
//			log.info(Arrays.asList(newClassIDs).toString());
			singleClassID = -1;
			for (int i = 0; i < newClassIDs.length; i++) {
				if (newClassIDs[i]) {
					singleClassID = i + 1;
				}
			}
			// System.out.println(" --> " + singleClassID + " " + zcu.getActualClassID());
			boolean[] newMultiClasses = stmc.getMultiClasses(singleClassID);
			translated.put(classifyUnit, newMultiClasses);

			// System.out.println("---------------------------------");
		}
		return translated;
	}

	public SingleToMultiClassConverter getStmc() {
		return stmc;
	}

}
