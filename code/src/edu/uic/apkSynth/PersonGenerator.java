package edu.uic.apkSynth;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

import org.joda.time.LocalDate;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;
import cern.jet.random.Uniform;
import cern.jet.stat.Descriptive;
import edu.uic.apk.IPersonGenerator;
import weka.classifiers.Classifier;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.SMO;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.functions.supportVector.*;
import weka.classifiers.mi.CitationKNN;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;

/*
 * Generates synthetic agents on demand
 * - The class is not used directly, but rather via a factory make_NEP_generator()
 * - The class has no awareness of the simulation, or the current date.
 * - There is some residual support for multiple classifiers, but currently only the HCV state is imputed
 *   The HCV classifier gives a conditional probability distribution for the HCV state, for a given agent;  we then sample from that distribution.
 */
public class PersonGenerator implements IPersonGenerator{
	List<DrugUser> unclassified_profiles = null;   //full profiles from NEP
	private static double pwid_maturity_threshold = Double.NaN;
	Instances hcv_training_dataset;
	//HashMap <String, Classifier>  classifiers; 
	Classifier classifier_HCV_state;// = classifiers.get("hcv_state");
	Uniform unif01; 
	
	private PersonGenerator(List <DrugUser> unclassified_profiles_param, double maturity_threshold_param, int rng_seed) {
		pwid_maturity_threshold = maturity_threshold_param;
		unclassified_profiles = unclassified_profiles_param;
		unif01 = new Uniform(0,1,rng_seed);
	}
	private void setClassifiers(HashMap <String, Classifier> classifiers, Instances hcv_training_dataset_param) {
		//this.classifiers = classifiers;
		classifier_HCV_state = classifiers.get("hcv_state");
		this.hcv_training_dataset = hcv_training_dataset_param;
		//System.out.println("Training instances: " + hcv_training_dataset.numInstances());
		//System.out.println(hcv_training_dataset.toString()); //nice report on everything.
		//System.out.println(hcv_training_dataset.toSummaryString());
	}
	
	/*
	 * called by the PG factory
	 */
	private static Instances buildLearningInstances(List<DrugUser> learningData) {
		Instances dataset = new Instances("learning_instances", DrugUser.getAttInfo(), learningData.size());
		for (DrugUser du : learningData) {
			dataset.add(du.getInstance());
		}
		//System.out.println("Source profile instances: " + dataset.numInstances());
		//System.out.println(dataset.toString()); //nice report on everything.		
		for(int i=0; i<dataset.numAttributes(); ++i) {
			Attribute att = dataset.attribute(i);
			if(att.isAveragable()) {
				System.out.println(att.name() + ": " + Descriptive.mean(new DoubleArrayList(dataset.attributeToDoubleArray(i))));  //should be about 0.58
			} else if (att.isNominal()) {
				System.out.println(att.name() + ": " + (new IntArrayList(dataset.attributeStats(i).nominalCounts)));  //should be about 0.58
			}
			//dataset.attributeStats(i).nominalCounts
		}
		//System.exit(1);
		return dataset;
	}

	/*
	 * the number of the canned profiles
	 */
	public int catalogueSize() {
		return unclassified_profiles.size();
	}
	/**
	 * Creates a single DrugUser from the dataset;  internally calls the classifier(s) to impute missing data
	 *  the class is not aware of the simulation time
	 *  the generator may need to be called multiple times, until the process is successful without Exception
	 *  
	 *  TODO: there is somewhat deficient randomization of the attributes.  Ideally, this function should further randomize the attributes of the agents, rather than directly copy from a fairly small catalog.
	 * @param params
	 * -- early_idus_only     = only agents within the first few years of their drug career (not their biological age)
	 * -- db_reference_number = the number in the CNEP database 
	 * @return
	 * @throws Exception 
	 */
	public DrugUser generate(HashMap<String, Object> params) throws Exception {
		Boolean early_idus_only = false;
		if(params.containsKey("early_idus_only")) {
			early_idus_only = (Boolean) params.get("early_idus_only");
		}
		Integer db_reference_number = null;
		if (params.containsKey("db_reference_number")) {
			db_reference_number = (Integer) params.get("db_reference_number");
			if (db_reference_number >= unclassified_profiles.size()) {
				throw new Exception("reference_too_big");
			}
		}
		
		assert (unclassified_profiles != null);
		assert unclassified_profiles.size() > 100;  
		DrugUser idu = null;
		int remaining_trials;
		Instance model_instance = null;
		//attempt to draw DrugUser from the catalog.  
		for (remaining_trials = 50; remaining_trials > 0; --remaining_trials) {
			int model_instance_index;
			if (db_reference_number == null) {
				model_instance_index = unif01.nextIntFromTo(0, unclassified_profiles.size()-1);
			} else {
				model_instance_index = db_reference_number;
				remaining_trials = 1; //would have just one chance to make this IDU without an Exception
			}
			DrugUser model_idu = unclassified_profiles.get(model_instance_index);
			model_instance = model_idu.getInstance();
			assert model_instance != null;
			
			if (early_idus_only && (! model_idu.isEarlyCareer(pwid_maturity_threshold))) {
				continue; //too old
			} 
			idu = model_idu.clone();
			idu.getCharacteristics().put("ID", idu.hashCode());
			break;
		}  
		if (remaining_trials == 0) {
			throw new Exception("Error in basic profile information");
		}
		//the classifier returns the distribution of HCV states, from which we decide on THE HCV state
		if(idu.getHcvState() == HCV_state.unknown) {
			//Classifier classifier_HCV_state = classifiers.get("hcv_state");
			Attribute hcv_states = DrugUser.getAttribMap().get("hcv_state");
			//profile_instances.setClass(hcv_states);
			model_instance.setDataset(hcv_training_dataset);
			double[] hcv_state_distribution = classifier_HCV_state.distributionForInstance(model_instance);
			int state_idx = select_random(hcv_state_distribution);
			String chosen_state = hcv_states.value(state_idx);
			idu.setHcvState(HCV_state.valueOf(chosen_state));
		}
			
		//TESTING
		String zip_str = idu.getPreliminaryZip();		
        try{
        	Integer.parseInt(zip_str);
            idu.setPreliminaryZip(zip_str);
        } catch (Exception e) {
			throw new Exception("Error in ZIP code.");
        } 

		LocalDate model_b_day = idu.getBirthDate();
		if((model_b_day.getYear() > 1900) && (model_b_day.getYear()<2010) && (idu.getAge() > 0) && (idu.getAge() < 100)) {
			;
		}//else impute
		else {
			throw new Exception("Error in birth year");
		}

		LocalDate model_s_day = idu.getSurveyDate();
		if ((model_s_day.getYear() > 1900) && (model_s_day.getYear()<2014)) {
			;
		} else {
			throw new Exception("Error in survey date");
		}

		double val = idu.getAgeStarted();
		if((val < 100) && (val > 0)) {
			;
		} //else impute
		else{	
			throw new Exception("Error in age");
		}

		val = idu.getFractionReceptSharing();
		if((val <= 1) && (val >= 0)) {
			;
		}//else impute
		else {
			throw new Exception("Error in sharing");
		}

		val = idu.getDrugReceptDegree();
		if((val <= 98) && (val >= 0)) {
			;
		} //else impute
		else {
			throw new Exception("Error in receptive drug degree");
		}

		val = idu.getDrugGivingDegree();
		if((val <= 98) && (val >= 0)) {
			;
		}//else impute
		else {
			throw new Exception("Error in giving drug degree");
		}

		val = idu.getInjectionIntensity();
		if((val <= 20) && (val >= 0)) {  //=0 is possible for people who might use drugs much less than once a day
			;	
		} //else impute
		else{
			throw new Exception("Error in daily injection intensity");
		}

		return idu;

	}
	
	/*
	 * PG factory - main access point
	 */
	public static PersonGenerator make_NEP_generator(HashMap<String, Object> population_params, double idu_maturity_threshold, int rng_seed) throws Exception {
		System.out.println("Synthetic population generator ... seed: " + rng_seed);
		RawLoader rl = new RawLoader(population_params, true, true, rng_seed);
		rl.setSeed(rng_seed);
		Instances hcv_learning_data 			   = buildLearningInstances(rl.getLearningData());
		HashMap <String, Classifier> classifiers   = train_classifiers(hcv_learning_data, rng_seed);
		System.out.println(hcv_learning_data.toSummaryString());
		
		PersonGenerator pg = new PersonGenerator(rl.getProfilesData(), idu_maturity_threshold, rng_seed);
		pg.setClassifiers(classifiers, hcv_learning_data);
		
		return pg;
	}


	/*
	 * Sample from a probability distribution
	 */
	private int select_random(double[] state_distribution) {
		//for(int i=0; i<state_distribution.length; ++i) {
		//	System.out.print("" + state_distribution[i]);
		//} System.out.println();
		double dieroll = unif01.nextDouble();
		int state_idx=0;
		while(state_idx < state_distribution.length) {
			dieroll = dieroll - state_distribution[state_idx];
			if(dieroll < 0) {
				break;
			}
			 ++state_idx;
		}
		return state_idx;
	}
	
	/*
	 * initialization - called by the PersonGenerator factory
	 */
    private static HashMap <String, Classifier> train_classifiers(Instances training_profiles, int rng_seed) throws Exception {
		HashMap <String, Classifier> classifiers = new HashMap <String, Classifier> ();
		//note: certain classifiers need to be tuned;  there is no auto-tuning for number of trees.  use MainJ class
		{
			ArrayList <String> options = new ArrayList <String> ();
			//Classifier classifier_hcv_state = new RandomForest();
			//-I trees, -K features per tree.  generally, might want to optimize (or not https://cwiki.apache.org/confluence/display/MAHOUT/Random+Forests)
	        //options.add("-I"); options.add("100"); options.add("-K"); options.add("4"); 
			
			Classifier classifier_hcv_state = new Logistic();  //wishlist: consider LogisticBoost
			options.add("-Q"); options.add(""+rng_seed);
			//System.out.println(classifier_hcv_state.getOptions());
			training_profiles.setClass(DrugUser.getAttribMap().get("hcv_state"));
			//setting the class is apparently not destructive
			//classifier itself is required not to change the instances (i.e. the training dataset)
			classifier_hcv_state.setOptions(options.toArray(new String[0]));
			classifier_hcv_state.buildClassifier(training_profiles);
			classifiers.put("hcv_state", classifier_hcv_state);
			//Enumeration strar = classifier_hcv_state.listOptions();
		}
		//we do not use other classifiers, for now, and just discard responses that with missing data.
		return classifiers;
	}
}
