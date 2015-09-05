package edu.uic.apkSynth;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.rules.NNge;
import weka.classifiers.trees.*;
import weka.classifiers.lazy.KStar;
import weka.classifiers.mi.*;
import weka.classifiers.meta.*;
import weka.classifiers.functions.*;
import weka.classifiers.functions.supportVector.*;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils.DataSink;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.neighboursearch.*;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.RemoveUseless;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/*
record of cleanups to the original data: 

NHBS
a. ES1 was replaced by birthyear;  
b. replaced with NOVALUE all of 99, 999, 9999, #!NULL, [[Skipped]], others?
c. wishlist: also replace some of the "8", "9" etc


NEP
a. replace all ,, with ,NOVALUE,
b. deleted all rows 
c. deleted the column INTID


tasks:
* Weka has something called weka.gui.beans.PredictionAppender: it adds predictions to instances ... interesting 
* 

 */

/*  Error handling policies

1. missing value in training data, e.g. NHBS
=> discard the row

2. missing value in the input dataset, e.g. NEP age
=> discard the row

wishlist => impute.
need to check that the classifier is correctly coping with it.
  
 */

/*
 * This is a stand-alone program for constructing the synthetic population
 * - APK would not import this class
 */
public class MainJ {
	public static void analyze_accuracy_NHBS(int rng_seed) throws Exception {
		HashMap <String,Object> population_params = load_defaults(null);
        RawLoader rl = new RawLoader(population_params, true, false, rng_seed);
        List<DrugUser> learningData = rl.getLearningData();
        
		Instances nhbs_data = new Instances("learning_instances", DrugUser.getAttInfo(), learningData.size());
		for (DrugUser du : learningData) {
			nhbs_data.add(du.getInstance());
		}
		System.out.println(nhbs_data.toSummaryString());
        nhbs_data.setClass(DrugUser.getAttribMap().get("hcv_state"));
        
		//wishlist: remove infrequent values
		//weka.filters.unsupervised.instance.RemoveFrequentValues()
		Filter f1 = new RemoveUseless();
		f1.setInputFormat(nhbs_data);
		nhbs_data = Filter.useFilter(nhbs_data, f1);
		
	
		System.out.println("NHBS IDU 2009 Dataset");
		System.out.println("Summary of input:");
		//System.out.printlnnhbs_data.toSummaryString());
		System.out.println("  Num of classes: " + nhbs_data.numClasses());
		System.out.println("  Num of attributes: " + nhbs_data.numAttributes());
		for (int idx=0; idx<nhbs_data.numAttributes(); ++idx){
			Attribute attr = nhbs_data.attribute(idx);
			System.out.println("" + idx + ": " + attr.toString());
			System.out.println("     distinct values:" + nhbs_data.numDistinctValues(idx));
			//System.out.println("" + attr.enumerateValues());			
		}
		
		ArrayList <String> options = new ArrayList <String> ();
		options.add("-Q"); options.add(""+rng_seed);
		//System.exit(0);
		//nhbs_data.deleteAttributeAt(0); //response ID
		//nhbs_data.deleteAttributeAt(16); //zip


		//Classifier classifier = new NNge(); //best nearest-neighbor classifier: 40.00
		//ROC=0.60
		//Classifier classifier = new MINND(); 
		//Classifier classifier = new CitationKNN(); 
		//Classifier classifier = new LibSVM(); //requires LibSVM classes. only gets 37.7%
		//Classifier classifier = new SMOreg();
		Classifier classifier = new Logistic(); 
		//ROC=0.686
		//Classifier classifier = new LinearNNSearch(); 
		
		//LinearRegression: Cannot handle multi-valued nominal class!
		//Classifier classifier = new LinearRegression(); 
		
        //Classifier classifier = new RandomForest();
        //String[] options = {"-I", "100", "-K", "4"}; //-I trees, -K features per tree.  generally, might want to optimize (or not https://cwiki.apache.org/confluence/display/MAHOUT/Random+Forests)
        //options.add("-I"); options.add("100"); options.add("-K"); options.add("4"); 
        //ROC=0.673
		
        //KStar classifier = new KStar(); 
		//classifier.setGlobalBlend(20); //the amount of not greedy, in percent
		//ROC=0.633

		//Classifier classifier = new AdaBoostM1();
		//ROC=0.66
		//Classifier classifier = new MultiBoostAB();
		//ROC=0.67
		//Classifier classifier = new Stacking();
		//ROC=0.495
		
		//J48 classifier = new J48(); // new instance of tree //building a C45 tree classifier
		//ROC=0.585		
		//String[] options = new String[1];
		//options[0] = "-U"; // unpruned tree
		//classifier.setOptions(options); // set the options
		
		classifier.setOptions((String[])options.toArray(new String[0]));
		
		//not needed before CV: http://weka.wikispaces.com/Use+WEKA+in+your+Java+code
		//classifier.buildClassifier(nhbs_data); // build classifier
		
		//evaluation
		Evaluation eval = new Evaluation(nhbs_data);
		eval.crossValidateModel(classifier, nhbs_data, 10, new Random(1));  //10-fold cross validation
		System.out.println(eval.toSummaryString("\nResults\n\n", false));
		System.out.println(eval.toClassDetailsString());
		//System.out.println(eval.toCumulativeMarginDistributionString());
	}


	/*
	public static void label() throws Exception {
		// load unlabeled data and set class attribute
		Instances unlabeled = DataSource.read("/some/where/unlabeled.arff");
		unlabeled.setClassIndex(unlabeled.numAttributes() - 1);
		// create copy
		Instances labeled = new Instances(unlabeled);
		//wishlist: train
		//Classifier Tree tree = RandomTree();
		J48 tree = new J48();
		
		// label instances
		for (int i = 0; i < unlabeled.numInstances(); i++) {
			double clsLabel = tree.classifyInstance(unlabeled.instance(i));
			labeled.instance(i).setClassValue(clsLabel);
		}
		// save newly labeled data
		DataSink.write("/some/where/labeled.arff", labeled);		
	}
	*/
	
	/*
	 * Only to be used when running from this class (could we test for this?)
	 *   otherwise the values come from the parameter file only
	 */
	private static HashMap <String,Object> load_defaults(HashMap <String,Object> population_params) {
		if(population_params == null) {
			population_params = new HashMap <String,Object> ();
		}
		population_params.put("NEP_data",  "/home/sasha/Documents/projects/hcv/code/data/NEP_Risk_Base_Merge.csv");
        population_params.put("NHBS_data", "/home/sasha/Documents/projects/hcv/code/data/IDU2_HCV_model_012913_cleaned.csv");
        population_params.put("ab_prob_acute", new Double(0.02));
        population_params.put("ab_prob_chronic", new Double(0.67));
        population_params.put("idu_maturity_threshold", new Double(5.0));
        population_params.put("mean_enrichment_suburbs", new Double(2.0));
        population_params.put("mean_enrichment_ChicagoNonNEP", new Double(0.5));

        population_params.put("nonNEP_age_boost", new Double(-0.67));
        population_params.put("nonNEP_age_started_boost", new Double(-0.74));
        population_params.put("nonNEP_degree_boost", new Double(0.24));
        population_params.put("nonNEP_hcv_ab_boost", new Double(0.011));
        population_params.put("nonNEP_sharing_boost", new Double(0.10));
        
        return population_params;
	}
	public static List<DrugUser> make_from_NEP(int target_count, int rng_seed) throws Exception {
		HashMap <String,Object> population_params = load_defaults(null);
        //RawLoader rl = new RawLoader(population_params);
		//Instances hcv_learning_data 	= buildLearningInstances(rl.getLearningData());
		//System.out.println(hcv_learning_data.toSummaryString());
		//System.out.println(hcv_learning_data.toString());
		//System.exit(1);
		//HashMap <String, Classifier> classifiers   = train_classifiers(hcv_learning_data);
		PersonGenerator pg = PersonGenerator.make_NEP_generator(population_params, (Double)population_params.get("idu_maturity_threshold"), rng_seed);
	
		HashMap <String, Object> generator_params = new HashMap<String, Object> ();
		generator_params.put("early_idus_only", (Boolean)false);
		ArrayList <DrugUser> pop = new ArrayList<DrugUser> (); 
		for(int idu_num=0; idu_num < target_count; ++idu_num) {
			try{
				pop.add(pg.generate(generator_params));
				System.out.print(".");
			} catch(Exception e) {
				System.out.println("x"); 
			}
		}
		System.out.println("Synthetic population:" + pop.size());
		if(pop.size() > 0) {
			int num_infected = 0;
			int num_abpos    = 0;
			for(DrugUser idu : pop) {
				num_abpos    += idu.isHcvABpos() ? 1: 0;
			}
			System.out.println(System.lineSeparator()+"Initial HCV prevalence (AB):  " + num_abpos/(1.0*pop.size()) );
		}
		return pop;
	}
	
	
	private static void test_generator() {
		//1. under biased labels (>90% positive), does it give biased synthetic labels?
		//2. if we set all the attributes to NOVALUE, how would it function?
		//3. report basic stats on the classes
	}

	
	public static void test_NHBS_old() throws Exception {
		//load the data
		CSVLoader loader = new CSVLoader();
		//these must come before the getDataSet()
		//loader.setEnclosureCharacters(",\'\"S");
		//loader.setNominalAttributes("16,71"); //zip code, drug name
		//loader.setStringAttributes("");
		//loader.setDateAttributes("0,1");
		//loader.setSource(new File("hcv/data/NHBS/IDU2_HCV_model_012913_cleaned_for_weka.csv"));
		loader.setSource(new File("/home/sasha/hcv/code/data/IDU2_HCV_model_012913_cleaned.csv"));
		Instances nhbs_data = loader.getDataSet();
		loader.setMissingValue("NOVALUE"); 
		//loader.setMissingValue(""); 		
		
		nhbs_data.deleteAttributeAt(12);  //zip code
		nhbs_data.deleteAttributeAt(1);  //date - redundant with age
		nhbs_data.deleteAttributeAt(0);  //date
		System.out.println("classifying attribute:");
		nhbs_data.setClassIndex(1); //new index  3->2->1
		nhbs_data.attribute(1).getMetadata().toString(); //HCVEIARSLT1

		//wishlist: perhaps it would be smarter to throw out unclassified instance?  they interfere with the scoring	
		nhbs_data.deleteWithMissingClass();
		//nhbs_data.setClass(new Attribute("HIVRSLT"));//.setClassIndex(1); //2nd column.  all are mostly negative
		//nhbs_data.setClass(new Attribute("HCVEIARSLT1"));//.setClassIndex(2); //3rd column
		
		//#14, i.e. rds_fem, should be made numeric
		System.out.println("NHBS IDU 2009 Dataset");
		System.out.println("Summary of input:");
		//System.out.printlnnhbs_data.toSummaryString());
		System.out.println("  Num of classes: " + nhbs_data.numClasses());
		System.out.println("  Num of attributes: " + nhbs_data.numAttributes());
		for (int idx=0; idx<nhbs_data.numAttributes(); ++idx){
			Attribute attr = nhbs_data.attribute(idx);
			System.out.println("" + idx + ": " + attr.toString());
			System.out.println("     distinct values:" + nhbs_data.numDistinctValues(idx));
			//System.out.println("" + attr.enumerateValues());			
		}
		
		//System.exit(0);
		//nhbs_data.deleteAttributeAt(0); //response ID
		//nhbs_data.deleteAttributeAt(16); //zip


		//Classifier classifier = new NNge(); //best nearest-neighbor classifier: 40.00
		//Classifier classifier = new MINND(); 
		//Classifier classifier = new CitationKNN(); 
		//Classifier classifier = new LibSVM(); //requires LibSVM classes. only gets 37.7%
		//Classifier classifier = new SMOreg();
		//Classifier classifier = new LinearNNSearch(); 
		
		//LinearRegression: Cannot handle multi-valued nominal class!
		//Classifier classifier = new LinearRegression(); 
		
        Classifier classifier = new RandomForest();
        String[] options = {"-I", "100", "-K", "4"}; //-I trees, -K features per tree.  generally, might want to optimize (or not https://cwiki.apache.org/confluence/display/MAHOUT/Random+Forests)
        classifier.setOptions(options);
		//Classifier classifier = new Logistic(); 

        //KStar classifier = new KStar(); 
		//classifier.setGlobalBlend(20); //the amount of not greedy, in percent

		//does poorly
		//Classifier classifier = new AdaBoostM1();
		//Classifier classifier = new MultiBoostAB();
		//Classifier classifier = new Stacking();

		
		//building a C45 tree classifier
		//J48 classifier = new J48(); // new instance of tree
		//String[] options = new String[1];
		//options[0] = "-U"; // unpruned tree
		//classifier.setOptions(options); // set the options
		//classifier.buildClassifier(nhbs_data); // build classifier
		
		//wishlist: remove infrequent values
		//weka.filters.unsupervised.instance.RemoveFrequentValues()
		Filter f1 = new RemoveUseless();
		f1.setInputFormat(nhbs_data);
		nhbs_data = Filter.useFilter(nhbs_data, f1);
		
		//evaluation
		Evaluation eval = new Evaluation(nhbs_data);
		eval.crossValidateModel(classifier, nhbs_data, 10, new Random(1));
		System.out.println(eval.toSummaryString("\nResults\n\n", false));
		System.out.println(eval.toClassDetailsString());
		//System.out.println(eval.toCumulativeMarginDistributionString());
	}
	
	public static void main(String[] arguments) throws Exception {
		int rng_seed = cern.jet.random.Uniform.staticNextIntFromTo(0, Integer.MAX_VALUE);//3;
		//analyze_accuracy_NHBS(rng.nextInt());
		List<DrugUser> synthPop = make_from_NEP(40000, rng_seed);
		System.out.println("\nLeaving APK-SynthPop");
		
	}
}
