package quenfo.de.uni_koeln.spinfo.classification.jasc.workflow;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//import org.apache.log4j.Logger;

import quenfo.de.uni_koeln.spinfo.classification.core.classifier.model.Model;
import quenfo.de.uni_koeln.spinfo.classification.core.data.ClassifyUnit;
import quenfo.de.uni_koeln.spinfo.classification.core.data.ExperimentConfiguration;
import quenfo.de.uni_koeln.spinfo.classification.core.helpers.EncodingProblemTreatment;
import quenfo.de.uni_koeln.spinfo.classification.db_io.Class_DBConnector;
import quenfo.de.uni_koeln.spinfo.classification.jasc.data.JASCClassifyUnit;
import quenfo.de.uni_koeln.spinfo.classification.jasc.preprocessing.ClassifyUnitSplitter;
import quenfo.de.uni_koeln.spinfo.classification.zone_analysis.classifier.RegexClassifier;
import quenfo.de.uni_koeln.spinfo.classification.zone_analysis.helpers.SingleToMultiClassConverter;
import quenfo.de.uni_koeln.spinfo.classification.zone_analysis.workflow.ZoneJobs;
import quenfo.de.uni_koeln.spinfo.core.helpers.PropertiesHandler;

public class ConfigurableDatabaseClassifier {
	
//	private static Logger log = Logger.getLogger(ConfigurableDatabaseClassifier.class);
	private Logger log = LogManager.getLogger();
	
	private Connection inputDb, outputDb;
	int queryLimit, fetchSize, currentId;

	private String trainingDataFileName;
	private ZoneJobs jobs;

	public ConfigurableDatabaseClassifier(Connection inputDb, /*Connection corrConnection,*/ Connection origConnection,
			int queryLimit, int fetchSize, int currentId, String trainingDataFileName)
					throws IOException {
		this.inputDb = inputDb;
//		this.corrConnection = corrConnection;
		this.outputDb = origConnection;
		this.queryLimit = queryLimit;
		this.fetchSize = fetchSize;
		this.currentId = currentId;
		this.trainingDataFileName = trainingDataFileName;

		// set Translations
		Map<Integer, List<Integer>> translations = new HashMap<Integer, List<Integer>>();
		List<Integer> categories = new ArrayList<Integer>();
		categories.add(1);
		categories.add(2);
		translations.put(5, categories);
		categories = new ArrayList<Integer>();
		categories.add(2);
		categories.add(3);
		translations.put(6, categories);
		SingleToMultiClassConverter stmc = new SingleToMultiClassConverter(6, 4, translations);
		jobs = new ZoneJobs(stmc);
	}
	
	public void classifyWithConfig(ExperimentConfiguration config, String tableName) throws ClassNotFoundException, IOException, SQLException {
		classify(config, tableName);
	}



	private void classify(ExperimentConfiguration config, String tableName)
			throws IOException, SQLException, ClassNotFoundException {

		// get trainingdata from file (and db)
		File trainingDataFile = new File(trainingDataFileName);
		List<ClassifyUnit> trainingData = new ArrayList<ClassifyUnit>();

		trainingData.addAll(jobs.getCategorizedParagraphsFromFile(trainingDataFile,
				config.getFeatureConfiguration().isTreatEncoding()));

		if (trainingData.size() == 0) {
			System.out.println(
					"\nthere are no training paragraphs in the specified training-DB. \nPlease check configuration and try again");
			System.exit(0);
		}
		log.info("training paragraphs: " + trainingData.size());
			

		trainingData = jobs.initializeClassifyUnits(trainingData);
		log.info("Configuration: " + config.getFeatureConfiguration());
		trainingData = jobs.setFeatures(trainingData, config.getFeatureConfiguration(), true);
		trainingData = jobs.setFeatureVectors(trainingData, config.getFeatureQuantifier(), null);

		// build model
		Model model = jobs.getNewModelForClassifier(trainingData, config);

		log.info("...classifying...");	
		// get data from db
		int done = 0;
		String query = null;
		String postingID = "";
		int jahrgang = 0;
		if (tableName.equals("jobs_textkernel"))
			query = "SELECT POSTINGID, Jahrgang, STELLENBESCHREIBUNG FROM " + tableName + " WHERE LANG='de' LIMIT ? OFFSET ?;";
		else
			query = "SELECT ZEILENNR, Jahrgang, STELLENBESCHREIBUNG FROM " + tableName + " LIMIT ? OFFSET ?;";
		
		PreparedStatement prepStmt = inputDb.prepareStatement(query);
		prepStmt.setInt(1, queryLimit);
		prepStmt.setInt(2, currentId);
		prepStmt.setFetchSize(fetchSize);
		// execute
		ResultSet queryResult = prepStmt.executeQuery();

		// total entries to process:
		if (queryLimit < 0) {

			String countQuery = "SELECT COUNT(*) FROM " + tableName + ";";
			Statement stmt = inputDb.createStatement();
			ResultSet countResult = stmt.executeQuery(countQuery);
			int tableSize = countResult.getInt(1);
			stmt.close();
			stmt = inputDb.createStatement();
			ResultSet rs = null;
			rs = stmt.executeQuery("SELECT COALESCE(" + tableSize + "+1, 0) FROM " + tableName + ";");

			queryLimit = rs.getInt(1);
		}

//		boolean goOn = true;
//		boolean askAgain = true;	
		
//		Map<Integer, String> unsplitted = new HashMap<>();
		

		while (queryResult.next()/* && goOn*/) {
			
			/*
			 *  TODO JB: improvement
			 *  bereits klassifizierte Anzeigen schon vorher abfangen
			 */

			String jobAd = null;
			postingID = queryResult.getString("POSTINGID");
//			postingID = queryResult.getString("POSTINGID");
			jahrgang = queryResult.getInt("Jahrgang");
			jobAd = queryResult.getString("STELLENBESCHREIBUNG");
			// if there is an empty job description, classifying is of no use,
			// so skip
			if (jobAd == null) {
				System.out.println("________________________________________________________________");
				System.out.println("JobAd ist null");
				System.out.println("Zeilennummer: " + postingID);
				System.out.println("Jahrgang: " + jahrgang);
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss ");
				Date currentTime = new Date();
				System.out.println("Zeit und Datum : " + formatter.format(currentTime));
				System.out.println("_________________________________________________________________");
				continue;
			}
			if (jobAd.isEmpty()) {
				System.out.println("__________________________________________________________________");
				System.out.println(" JobAd ist leer!");
				System.out.println("Zeilennummer: " + postingID);
				System.out.println("Jahrgang: " + jahrgang);
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss ");
				Date currentTime = new Date();
				System.out.println("Zeit und Datum : " + formatter.format(currentTime));
				System.out.println("___________________________________________________________________");
				continue;
			}

			// 1. Split into paragraphs and create a ClassifyUnit per paragraph
			Set<String> paragraphs = ClassifyUnitSplitter.splitIntoParagraphs(jobAd);		
//			if (paragraphs.size() == 1)
//				if (jobAd.length() > 450)
//					unsplitted.put(zeilenNr, jobAd);
			//log.info(paragraphs.size() + " paragraphs");
			
			// if treat enc
			if (config.getFeatureConfiguration().isTreatEncoding()) {
				paragraphs = EncodingProblemTreatment.normalizeEncoding(paragraphs);
			}
			List<ClassifyUnit> classifyUnits = new ArrayList<ClassifyUnit>();
			for (String string : paragraphs) {
//				classifyUnits.add(new JASCClassifyUnit(string, jahrgang, postingID));
				classifyUnits.add(new JASCClassifyUnit(string, jahrgang, postingID));
			}
			// prepare ClassifyUnits
			classifyUnits = jobs.initializeClassifyUnits(classifyUnits);
			classifyUnits = jobs.setFeatures(classifyUnits, config.getFeatureConfiguration(), false);
			classifyUnits = jobs.setFeatureVectors(classifyUnits, config.getFeatureQuantifier(), model.getFUOrder());
			
			
			// 2. Classify
			RegexClassifier regexClassifier = new RegexClassifier(PropertiesHandler.getRegex());
			Map<ClassifyUnit, boolean[]> preClassified = new HashMap<ClassifyUnit, boolean[]>();
			for (ClassifyUnit cu : classifyUnits) {
				boolean[] classes = regexClassifier.classify(cu, model);
				preClassified.put(cu, classes);
			}
			
			
			Map<ClassifyUnit, boolean[]> classified = jobs.classify(classifyUnits, config, model);
			
			classified = jobs.mergeResults(classified, preClassified);
			classified = jobs.translateClasses(classified);

			List<ClassifyUnit> results = new ArrayList<ClassifyUnit>();
			for (ClassifyUnit cu : classified.keySet()) {
				((JASCClassifyUnit) cu).setClassIDs(classified.get(cu));
				results.add(cu);
			}
			Class_DBConnector.insertClassifiedParagraphsinDB(outputDb, results, jahrgang, postingID);
			done++;

			if (done % fetchSize == 0)
				log.info(done + " Anzeigen bearbeitet");
//			// time needed
//			if (done % fetchSize == 0) {
//				// continue?
//				if (askAgain) {
//
//					System.out.println(
//							"\n\n" + "continue (c),\n" + "don't interrupt again (d),\n" + "or stop (s) classifying?");
//
//					boolean answered = false;
//					while (!answered) {
//						BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
//						String answer = in.readLine();
//
//						if (answer.toLowerCase().trim().equals("c")) {
//							goOn = true;
//							answered = true;
//							log.info("...classifying...");
//						} else if (answer.toLowerCase().trim().equals("d")) {
//							goOn = true;
//							askAgain = false;
//							answered = true;
//							log.info("...classifying...");
//						} else if (answer.toLowerCase().trim().equals("s")) {
//							goOn = false;
//							answered = true;
//						} else {
//							System.out.println("C: invalid answer! please try again...");
//							System.out.println();
//						}
//					}
//				}
//			}
		
		}
//		Class_DBConnector.writeUnsplittedJobAds(origConnection, unsplitted);		
	}


}
