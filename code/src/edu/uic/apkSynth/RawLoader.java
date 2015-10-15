package edu.uic.apkSynth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
//incompatible with 1.6
//import java.nio.charset.Charset;
//import java.nio.file.Files;
//import java.nio.file.Paths;
import java.util.ArrayList;

import org.joda.time.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import repast.simphony.random.RandomHelper;

import java.util.HashMap;
import java.util.List;

import cern.jet.random.*;
import edu.uic.apk.IDU;

/*
 * loads epi surveys with PWIDs and constructs preliminary DrugUser profiles
 * classification of antibody-positive results has been moved to IDUbuilder1
 * singleton class
 */
public class RawLoader  {
	public final static String NEP_mock_survey_path  = "datagen/NEP_mock_2014.csv";
	public final static String NHBS_mock_survey_path = "datagen/NHBS_mock_2014.csv";
	//wishlist: label output if it was generated from mock input
	
	public static String  STRING_NOVALUE = "NOVALUE";
	public static Integer INT_NOVALUE    = Integer.MAX_VALUE;
	public static Double  DOUBLE_NOVALUE = Double.NaN;
	
	private List<DrugUser> survey_data_NEP;   //lacks HCV information, to be imputed
	private List<DrugUser> survey_data_NHBS;  //contains all information.  the training data for the classifier
	
	private static double mean_enrichment_ChicagoNonNEP  = Double.NaN;// = 1.0; //for non-606XY zips, how many copies to insert
	private static double mean_enrichment_suburbs = Double.NaN;// = 1.0; //for 60!6XY zips, how many copies to insert
	private static double nonNEP_age_boost = Double.NaN;
	private static double nonNEP_age_started_boost = Double.NaN;
	private static double nonNEP_hcv_ab_boost = Double.NaN;
	private static double nonNEP_degree_boost = Double.NaN;
	private static double nonNEP_injection_ratio = Double.NaN;
	private static double nonNEP_sharing_boost = Double.NaN;
	private static String NEP_survey_fpath = "";
	private static String NHBS_IDU2_survey_fpath = "";
	
	private Normal  randNormal;
	private Poisson randPoisson;
	private Uniform randUnif01;
	
	public RawLoader(HashMap<String, Object> population_params, boolean load_NHBS, boolean load_NEP, int rng_seed) {
        mean_enrichment_ChicagoNonNEP = (Double) population_params.get("mean_enrichment_ChicagoNonNEP");
        mean_enrichment_suburbs       = (Double) population_params.get("mean_enrichment_suburbs");
        nonNEP_age_boost          	  = (Double) population_params.get("nonNEP_age_boost");
        nonNEP_age_started_boost      = (Double) population_params.get("nonNEP_age_started_boost");
        nonNEP_degree_boost           = (Double) population_params.get("nonNEP_degree_boost");
        nonNEP_hcv_ab_boost           = (Double) population_params.get("nonNEP_hcv_ab_boost");
        nonNEP_injection_ratio        = (Double) population_params.get("nonNEP_injection_ratio");
        nonNEP_sharing_boost          = (Double) population_params.get("nonNEP_sharing_boost");
		NEP_survey_fpath       		  = (String) population_params.get("NEP_data");
        NHBS_IDU2_survey_fpath        = (String) population_params.get("NHBS_data");
        
        if(! (new File(NEP_survey_fpath)).exists()) {
        	NEP_survey_fpath = NEP_mock_survey_path;
        	System.out.println("NEP is data is not available. Using synthetic data: " + NEP_mock_survey_path);
        }
        if(! (new File(NHBS_IDU2_survey_fpath)).exists()) {
        	NHBS_IDU2_survey_fpath = NHBS_mock_survey_path;
        	System.out.println("NHBS is data is not available. Using synthetic data: " + NHBS_IDU2_survey_fpath);
        }

        setSeed(rng_seed);
        
        try {
        	if(load_NHBS) {
        		build_NHBS();
        	}
        	if(load_NEP) {
        		build_NEP();
        	}
        } catch (Exception e) {
        	e.printStackTrace();
        	System.exit(1);
        }
    }
	
	public void build_NEP() throws IOException {
		survey_data_NEP  = read_NEP(NEP_survey_fpath);		
		//survey_data_NEP  = read_NHBS(NHBS_IDU2_survey_fpath);  //test: builds population based on NHBS profiles 	
		for(DrugUser idu : survey_data_NEP) {
			idu.setHcvState(HCV_state.unknown);
			//idu.setDrugReceptDegree(idu.getDrugReceptDegree()+5);
			//idu.setFractionReceptSharing(0.9);
		}
	}
	public void build_NHBS() throws IOException {
		survey_data_NHBS = read_NHBS(NHBS_IDU2_survey_fpath);
		int count = survey_data_NHBS.size();
		if(count > 0) {
			int num_abpos    = 0;
			for(DrugUser idu : survey_data_NHBS) {
				//System.out.println(idu.toString());
				num_abpos    += idu.isHcvABpos() ? 1: 0;
			}
			System.out.println();
			System.out.println("NHBS HCV prevalence (Antibody+): " + num_abpos/(1.0*count) );
		}
	}
	/*
	 * Get the database of canned profiles, from which IDUs will be duplicated
	 */
	public List<DrugUser> getProfilesData() {
		System.out.println("NEP profiles: " + survey_data_NEP.size());
		assert survey_data_NEP != null;
		if (mean_enrichment_suburbs + mean_enrichment_ChicagoNonNEP == 0.0) {
			return survey_data_NEP;
		} 
		int num_city_basic_profiles      = 0;
		int num_city_enriched_profiles   = 0;
		int num_suburbs_basic_profiles   = 0;
		int num_suburbs_enriched_profiles= 0;
		List<DrugUser> compensated_survey_data_NEP = new ArrayList<DrugUser> ();
		for(DrugUser idu : survey_data_NEP) {
			compensated_survey_data_NEP.add(idu);
			if(idu.getPreliminaryZip() == null) {
				continue;
			} else if(idu.getPreliminaryZip().startsWith("606")) {
				num_city_basic_profiles += 1;
				int num_extra_copies = randPoisson.nextInt(mean_enrichment_ChicagoNonNEP);
				for(;num_extra_copies>0; num_extra_copies--) {
					DrugUser updatedIDU = idu.clone();
					adjust_risk_nonNEP(updatedIDU);
					compensated_survey_data_NEP.add(updatedIDU);
					num_city_enriched_profiles += 1;
				}
			} else {
				num_suburbs_basic_profiles += 1;
				int num_extra_copies = randPoisson.nextInt(mean_enrichment_suburbs);
				for(;num_extra_copies>0; num_extra_copies--) {
					DrugUser updatedIDU = idu.clone();
					adjust_risk_nonNEP(updatedIDU);
					compensated_survey_data_NEP.add(updatedIDU);
					num_suburbs_enriched_profiles += 1;
				}
			}
		}
		System.out.println("  Chicago profiles: " + num_city_basic_profiles + ". non-NEP: " + num_city_enriched_profiles);
		System.out.println("  Suburbs profiles: " + num_suburbs_basic_profiles + ". non-NEP: " + num_suburbs_enriched_profiles);
		System.out.println("  TOTAL CNEP+: " + compensated_survey_data_NEP.size());
		return compensated_survey_data_NEP;
	}
	private void adjust_risk_nonNEP(DrugUser idu) {
		idu.setSyringe_source(HarmReduction.nonHR);
		//we use a non-uniform distribution b/c of large SD, and because degree is discretized (boost by 0.1 would make no difference)
		idu.setDrugGivingDegree(idu.getDrugGivingDegree() + randPoisson.nextInt(nonNEP_degree_boost));
		idu.setDrugReceptDegree(idu.getDrugReceptDegree() + randPoisson.nextInt(nonNEP_degree_boost));
		idu.setInjectionIntensity(idu.getInjectionIntensity()*(2*randUnif01.nextDouble()*nonNEP_injection_ratio));
		double revised_sharing = idu.getFractionReceptSharing() + nonNEP_sharing_boost*2*randUnif01.nextDouble(); //the mean of a [0,1] RW is 0.5.  Hence, multiply by 2x  
		idu.setFractionReceptSharing(Math.min(revised_sharing, 1.0));   
		idu.setBirthDate(idu.getBirthDate().minusMonths((int)Math.round(12*nonNEP_age_boost*2*randUnif01.nextDouble())));
		idu.setAgeStarted(idu.getAgeStarted() + nonNEP_age_started_boost*2*randUnif01.nextDouble());
		if(idu.getAgeStarted() > idu.getAge()) {
			idu.setAgeStarted(idu.getAge()-0.1);
		}

		if(nonNEP_hcv_ab_boost > randUnif01.nextDouble()) {
			idu.setHcvState(HCV_state.ABPOS);
		}
	}

	public List<DrugUser> getLearningData() {
		assert survey_data_NHBS != null;
		return survey_data_NHBS;
	}
	
	private List<String> read_file(String fpath) throws FileNotFoundException, IOException{
		//only works in 1.7: 
		//List<String> lines=Files.readAllLines(Paths.get(survey_fpath), Charset.forName("UTF-8"));
		ArrayList <String> lines = new ArrayList <String> ();
		BufferedReader br = new BufferedReader (new FileReader (fpath));		
		String line = br.readLine();
		while(line!=null) {
			lines.add(line);
			line = br.readLine();
		}
		br.close();
		return lines;
	}
	
	/*
	 * create IDU dataset based on the NEP data.  the data is incomplete and internally inconsistent.
	 * Note: Unexpected behavior with -enableassertions. On this project, this causes a lot of errors;  
	 *       however, when it's called from APK, the assertions are skipped!
	 */
	private List<DrugUser> read_NEP(String fpath) throws IOException {
		String[] header = null;
		ArrayList<String[]> survey_data   = new ArrayList<String[]> ();
        if (fpath != null && fpath != "") {
        	try {
        		List<String> lines = read_file(fpath);
        		header = lines.get(0).split(",");
        		lines.remove(0); //the header
        		for(String line : lines) {
        			survey_data.add(line.split(","));
        		}
        	} catch (FileNotFoundException e) {
        	    System.err.println("FileNotFoundException: " + e.getMessage());
        	    System.out.println("File path: " + (new File(fpath).getAbsolutePath()));
        	    throw(e);
        	} catch (IOException e) {
        	    System.err.println("Caught IOException: " + e.getMessage());
        	    throw(e);
        	}
        } else {
        	System.err.println("Warning: no path for NEP data");
        }
        assert header != null; //= {"Obs","Tag","QUESTDATE","STUDYNUM","SITE","INTID","SEX","AGE","RACEETH","Q3","Q5","Q5OTHDESC","Q6A","Q6BAMT","Q6BUNIT","Q7A1","Q7A2","Q7A3","Q7A4","Q7A5","Q7A5DESC","Q7B","Q8A","Q8B","Q9A","Q9B","Q10AA","Q10AB","Q10AC","Q10AD","Q10AE","Q10AF","Q10AFDESC","Q10B","Q11","Q12A","Q12B","Q13AA","Q13AB","Q13AC","Q13AD","Q13AE","Q13AF","Q13AFDESC","Q13B","Q14","Q15","Q16A","Q16BA","Q16BB","Q16BC","Q16BD","Q16BE","Q16BF","Q17","Q18A","Q18B","Q18C","Q18D","Q18E","Q18F","Q19A","Q19B","Q19C","Q19D","Q19E","Q19E2","Q19E3","Q19F","Q19","Q20","Q20A","Q20ADESC","Q20B3A","Q20B3B","Q20B3C","Q20B3D","Q20B3E","Q20B3EDESC","Q20B","Q20BDESC","Q21","Q22","Q23","Q24A","Q24B","Q24C","Q24D","Q24E","Q24F","Q24G","Q25A","Q25B","Q25C","Q25D","Q25E","Q25F","Q25G","Q26","Q27","Q28","Q29","Q30","Q31","Q31DESC","ZIPCODE"};
        //we do NOT assume that the columns follow a particular order
		HashMap<String,Integer> survey_header_NEP = new HashMap<String,Integer>();
		int h_idx = 0;
		for(String h : header) {
			if(h.charAt(0) != '"') {
				survey_header_NEP.put(h, h_idx);
			} else{
				survey_header_NEP.put(h.substring(1,h.length()-1), h_idx);
			}
			h_idx++;		
		}
   
        ArrayList <DrugUser> idu_list = new ArrayList <DrugUser> ();
		DateTimeFormatter date_format = DateTimeFormat.forPattern("MM/dd/yyyy");
        int line_num = 1; //the header line num
		for (String[] line : survey_data) {
			line_num++;
			try{
				//when we get NOVALUE we throw out the whole line
				if(line.length < survey_header_NEP.size()) {
					System.err.println("incomplete data at line: " + line_num);
					continue;
				}
	        	DrugUser idu = new DrugUser();
				HashMap <String,Object> attributes = new HashMap <String,Object> ();
	        	for(String attrib : survey_header_NEP.keySet()) {
	        		attributes.put(attrib, line[survey_header_NEP.get(attrib)]);
	        	}
	    		idu.setCharacteristics(attributes);
	    		if(survey_header_NEP.containsKey("Obs")) {
                    idu.setDatabaseLabel(line[survey_header_NEP.get("Obs")]);
	    		} else {
	    			idu.setDatabaseLabel("" + line_num);
	    		}
	    		
	    		idu.setPreliminaryZip(line[survey_header_NEP.get("ZIPCODE")]);
	    		
	    		String gender = line[survey_header_NEP.get("SEX")];
	    		if(gender.equalsIgnoreCase("0")) {
	    			idu.setGender(Gender.Female); 
	    		} else {
	    			idu.setGender(Gender.Male);  //including transsexuals
	    		} 
	    		
	    		LocalDate questdate = date_format.parseLocalDate(line[survey_header_NEP.get("QUESTDATE")]) ; //e.g. 12/02/2001
	    		idu.setSurveyDate(questdate);

	    		//wishlist: change the birthday at random.  currently it is exact N years before the survey date.
	    		String AGE     		= line[survey_header_NEP.get("AGE")];
	    		int age_at_survey   = Integer.parseInt(AGE); 
	    		LocalDate birthdate = questdate.minusYears(age_at_survey);
	    		idu.setBirthDate(birthdate);
	    		if(age_at_survey < 10) {
					System.err.println("nonsense value: age at line: " + line_num);
					continue;
	    		}
	    		double age_started = Double.parseDouble(line[survey_header_NEP.get("Q3")]);
	    		if(age_started > age_at_survey - 0.1) {
	    			age_started = age_at_survey - randUnif01.nextDouble(); //try to correct the value
	    		}
	    		if(age_started < 10) {
					System.err.println("nonsense value: age started at line: " + line_num);
					continue;
	    		}
    			idu.setAgeStarted(age_started);
    			    			
	    		String race = line[survey_header_NEP.get("RACEETH")];
	    		if(race.equalsIgnoreCase("1")) {
	    			idu.setRace(Race.NHBlack);
	    		} else if (race.equalsIgnoreCase("2")) {
	    			idu.setRace(Race.NHWhite);
	    		} else if (race.equalsIgnoreCase("3") | race.equalsIgnoreCase("4") | race.equalsIgnoreCase("5")) {
	    			idu.setRace(Race.Hispanic);
	    		} else {//if (race.equalsIgnoreCase("6")) {
	    			idu.setRace(Race.Other);
	    		} 
	
	    		//Integer indegree_over_30days = Integer.parseInt(line[survey_header_NEP.get("Q9B")]);  
	    		//checked later. //assert (indegree_over_30days >= 0) && (indegree_over_30days <= 98);

    			//=number of people from which drugs were received;  999=never did this.
	    		Integer indegree_over_30days = Integer.parseInt(line[survey_header_NEP.get("Q12B")]);  
	    		//checked later. //assert (indegree_over_30days >= 0) && (indegree_over_30days <= 98);	    		
	    		//=How many people did you use a cooker, cotton or water behind or with in those 30 days?

	    		if(indegree_over_30days < 999){ 
	    			idu.setDrugReceptDegree((double)indegree_over_30days);
	    		} else if (indegree_over_30days == 999){ 
	    			idu.setDrugReceptDegree(0.0);	    			
	    		} else {
					System.err.println("nonsense value: degree at line: " + line_num);
					continue;
	    			//throw new Exception("could not parse degree"); //idu.setDrug_degree(Double.NaN);
	    		}
    			Integer giving_degree_over_30days = Integer.parseInt(line[survey_header_NEP.get("Q16A")]);
    			//checked later. //assert (giving_degree_over_30days >= 0) && (giving_degree_over_30days <= 98);
	    		//=number of people who received from me;  999=never did this.
	    		
    			if(giving_degree_over_30days < 999){ 
	    			idu.setDrugGivingDegree((double)giving_degree_over_30days);
	    		} else if (giving_degree_over_30days == 999){ 
	    			idu.setDrugGivingDegree(0.0);	    			
	    		} else {
					System.err.println("nonsense value: degree at line: " + line_num);
					continue;
					//throw new Exception("could not parse degree"); //idu.setDrug_degree(Double.NaN);
	    		}
	    		
	    		idu.setHcvState(HCV_state.unknown);

	    		Integer injections_days_per_30days = Integer.parseInt(line[survey_header_NEP.get("Q8A")]);
	    		Integer injections_per_1day    	   = Integer.parseInt(line[survey_header_NEP.get("Q8B")]);
	    		int total_injections = injections_per_1day * injections_days_per_30days;
	    		if(total_injections > 0) {
	    			idu.setInjectionIntensity(total_injections/30.); //averaged
	    		} else {
					System.err.println("could not parse total injections (=0) at line: " + line_num);
					continue;
	    		}
	    		if(total_injections > 0) {
	    			//Q9A ask for episodes of using non-sterile needles;  we assume they were not rinsed, and represent effective sharing.
	    			Integer nonsterile_episodes = Integer.parseInt(line[survey_header_NEP.get("Q9A")]);
	    			//wishlist: document bleaching is rare
	    			//Integer bleach_episodes     = Integer.parseInt(line[survey_header_NEP.get("Q11")]);
	    			//backloading is highly correlated
	    			//Integer num_backloading = Integer.parseInt(line[survey_header_NEP.get("Q9B")]);
	    			nonsterile_episodes = Math.min(nonsterile_episodes, total_injections);
	    			idu.setFractionReceptSharing(((double) nonsterile_episodes)/total_injections);
	    		} else {
	    			idu.setFractionReceptSharing(0.0);
    			}
				idu.setSyringe_source(HarmReduction.HR);
	    		//wishlist: somewhere here the model idu should also get its HCV state: infected, suspectible, chronic etc. (currently we do it too high)
	    		idu_list.add(idu);
			} catch (Exception e) {
				System.err.println("Exception reading NEP row: " + line_num);
				System.err.println(line);
				System.err.println(e.toString());
				//e.printStackTrace();
			}
			catch (Error e) {
				System.err.println("error reading NEP row: " + line_num);
				System.err.println(line);
				System.err.println(e.toString());
				//e.printStackTrace();
			}
		}
		System.out.println("Finished loading NEP.  Total valid lines: " + idu_list.size());
        return idu_list;
	}
	
	private List<DrugUser> read_NHBS(String fpath) throws IOException {
		//create IDU listing based on the NEP data.  the data is incomplete and internally inconsistent.
		String[] header = null;
		ArrayList<String[]> survey_data   = new ArrayList<String[]> ();
        if (fpath != null && fpath != "") {
        	try {
        		List<String> lines = read_file(fpath);
        		header = lines.get(0).split(",");
        		lines.remove(0); //the header
        		for(String line : lines) {
        			survey_data.add(line.split(","));
        		}
        	} catch (FileNotFoundException e) {
        	    System.err.println("FileNotFoundException: " + e.getMessage());
        	    System.out.println("File path: " + (new File(fpath).getAbsolutePath()));
        	    throw(e);
        	} catch (IOException e) {
        	    System.err.println("Caught IOException: " + e.getMessage());
        	    throw(e);
        	}
        } else {
        	System.err.println("Warning: no path for NHBS data");
        }
		assert header != null; // = {"response", "HIVRSLT", "HCVEIARSLT1", "auto1", "INT2", "YEAR", "ES1", "ES3", "ES5", "ES9", "ES10", "ES12", "rds_rel", "rds_inja", "rds_seea", "rds_male", "rds_fem", "Q2", "Q5", "Q6", "Q7", "Q10", "q99aidu", "pharmacy", "mdhosp", "friend", "dealer", "exchprog", "ndleoth", "q101yearidu", "q102inj12a", "q102inj12b", "q102inj12c", "q102inj12d", "q102inj12e", "q102inj12f", "q102injoth", "q102spothinj", "q102spothif", "q103num_na", "q104num_ccw", "q105num_dda", "q106sharndle", "q107sharcook", "q108sharcott", "q109sharwatr", "samesyr", "q113injl_un", "q113ainjl_nn", "q114injl_ccw", "q115injl_dd", "q116injl_mf", "injl_mfo", "q117injl_msm", "q118injl_khs", "q118ainjl_hiv", "q119injl_khc", "q119ainjl_hcv", "q120injl_rel", "q122ani12a", "q122bni12b", "q122cni12c", "q122dni12d", "q122eni12e", "q122fni12f", "q122gni12g", "q122hni12h", "q122ini12i", "q122jni12j", "q122kni12k", "q122lni12l", "q122othnidr", "ni12oth", "q122othnidra", "q125evertest", "q129alochiv", "q138hepever", "q138atyphep", "q139whenhcv", "q139ahcvmeds", "q140evrhctst", "q140arcthctst", "q141heprxhiv", "q142heprx6mo", "q143hepvacc", "q143atypevacc", "q147hcvjail", "q147ajlhcvrst", "q149stndl12", "q149a1needlea", "q149a2needleb", "q149a3needlec", "q149a4needled", "q149a5needlee", "q149a6needlef", "specndle", "racea", "raceb", "racec", "raced", "racee", "rds_rela", "rds_relb", "rds_relc", "rds_reld", "rds_rele", "rds_relf", "rds_relg", "rds_relh", "rds_reli", "typhepa", "typhepb", "typhepc"};
		HashMap<String,Integer> survey_header_NHBS = new HashMap<String,Integer>();
		int h_idx = 0;
		for(String h : header) {
			if(h.charAt(0) != '"') {
				survey_header_NHBS.put(h, h_idx);
			} else{
				survey_header_NHBS.put(h.substring(1,h.length()-1), h_idx);
			}
			h_idx++;		
		}
        
        ArrayList <DrugUser> idu_list = new ArrayList <DrugUser> ();
		DateTimeFormatter birth_date_format = DateTimeFormat.forPattern("d-MMM-YY"); //29-Jan-81
        int line_num = 1; //the header line num
		for (String[] line : survey_data) {
			line_num++;
			try{
				if(line.length < survey_header_NHBS.size()) {
					System.err.println("incomplete data at line: " + line_num);
					continue;
				}
	        	DrugUser idu = new DrugUser();
				HashMap <String,Object> attributes = new HashMap <String,Object> ();
	        	for(String attrib : survey_header_NHBS.keySet()) {
	        		attributes.put(attrib, line[survey_header_NHBS.get(attrib)]);
	        	}
	    		idu.setCharacteristics(attributes);
	    		if(survey_header_NHBS.containsKey("response")) {
                    idu.setDatabaseLabel(line[survey_header_NHBS.get("response")]);
	    		} else {
	    			idu.setDatabaseLabel("" + line_num);
	    		}
	    		
	    		idu.setPreliminaryZip(line[survey_header_NHBS.get("Q2")]);
	    		
	    		//wishlist: imputation (chronic/resistant) at this stage is problematic.
	    		String hcv_state_str = line[survey_header_NHBS.get("HCVEIARSLT1")];
 				if (hcv_state_str.equals("1")) { 
 					idu.setHcvState(HCV_state.ABPOS); //this is a non-immunological state.  it needs to be refined later!
 				} else if(hcv_state_str.equals("0")) {
 					idu.setHcvState(HCV_state.susceptible); 
 					//assume that no-AB -> no infection.  in reality, AB test might fail in early infection.
	 			} else {
 					idu.setHcvState(HCV_state.unknown); 	 				
	 			}

 				//HIV state will not be used for now
	    		//String hiv_state_str = line[survey_header_NHBS.get("HIVRSLT")];
 				//if (hiv_state_str.equals("1")) { 
				//	idu.setHivState(HIV_state.chronic); //never allowed acute
 				//} else if(hiv_state_str.equals("0")) {
 				//	idu.setHivState(HIV_state.susceptible); 
 				//	//assume that no-AB -> no infection.  in reality, AB test might fail in early infection.
	 			//} else {
 				//	idu.setHivState(HIV_state.unknown); 	 				
	 			//}

				String gender = line[survey_header_NHBS.get("ES9")];
	    		idu.setGender((gender).contains("2")?Gender.Female:Gender.Male);  //3 is transgender, which we neglect since it's very infrequent and might confuse the imputation
	    		
	    		String date_of_birth_str = line[survey_header_NHBS.get("ES1")];
	    		date_of_birth_str = date_of_birth_str.replaceAll("\"", "");
	    		//System.out.println("" + line_num + ": ES1=" + date_of_birth_str);
	    		LocalDate date_of_birth  = birth_date_format.parseLocalDate(date_of_birth_str) ; //e.g. 12-02-88
	    		idu.setBirthDate(date_of_birth);
	    		
	    		LocalDate survey_date = birth_date_format.parseLocalDate(line[survey_header_NHBS.get("QUESTDATE")]);
	    		//survey_date 		  = survey_date.plusDays((int) (365*(randUnif01.nextDouble() - 0.5)));
	    		idu.setSurveyDate(survey_date);
	    		
	    		String race_white  = line[survey_header_NHBS.get("racee")];
	    		String race_black  = line[survey_header_NHBS.get("racec")];
	    		String ethnic_hisp = line[survey_header_NHBS.get("ES3")];
				//American Indian or Alaska Native…………… 1
				//Asian ..……………………..………………....... 2
				//Black or African American……………..…….. 3
				//Native Hawaiian or Other Pacific Islander……...4
				//White ……………..……………………………. 5
	    		if (ethnic_hisp.equalsIgnoreCase("ES3")) {
					//"Do you consider yourself to be Hispanic or Latino/a?"
					idu.setRace(Race.Hispanic);
				} else if (race_white.equals("1")) {
					idu.setRace(Race.NHWhite);
				} else if (race_black.equals("1")) {
					idu.setRace(Race.NHBlack);
				} else {
	    			idu.setRace(Race.Other);
	    		} 

	    		idu.setAgeStarted(Double.parseDouble(line[survey_header_NHBS.get("q99aidu")]));
		
	    		Long degree = (Long) Math.round(Long.parseLong(line[survey_header_NHBS.get("q103num_na")])*1.0); 
	    		//degree = Math.round(degree * 1.698); //correction in NHBS2012
	    		if(degree < 100){
	    			idu.setDrugReceptDegree((double)degree);
	    			idu.setDrugGivingDegree((double)degree); //wishlist: differentiate
	    		} else {
	    			idu.setDrugReceptDegree(Double.NaN);
	    			idu.setDrugGivingDegree(Double.NaN); 
	    		}
	    		
	    		String injection_int = line[survey_header_NHBS.get("q102inj12b")];
				double daily_injection_intensity = 0.0;
				if(injection_int.equals("1")) { //>1/day
					daily_injection_intensity = randNormal.nextDouble(4, 2);
				} else if (injection_int.equals("2")) { //1 day
					daily_injection_intensity = randNormal.nextDouble(1, 0.5);
				} else if (injection_int.equals("3")) { //>1/week
					daily_injection_intensity = randNormal.nextDouble(3.5/7, 2./7);
				} else if (injection_int.equals("4")) { //1/week
					daily_injection_intensity = randNormal.nextDouble(2./7, 0.5/7);
				} else if (injection_int.equals("5")) { //>1/mo
					daily_injection_intensity = randNormal.nextDouble(2./30, 1./30);
				} else if (injection_int.equals("6")) { //1/mo
					daily_injection_intensity = randNormal.nextDouble(1./30, 0.5/30.);
				} else if (injection_int.equals("7")) { //<1/mo
					daily_injection_intensity = randNormal.nextDouble(0.5/30, 0.25/30);
				} else if (injection_int.equals("8")) {
					daily_injection_intensity = 0.0;
				} else {
					daily_injection_intensity = 1; //will assume 1/day // Double.NaN; 
					//throw new Exception("Unrecognized survey value (q102inj12b): " + injection_int);
				}
				idu.setInjectionIntensity(Math.max(0.0, daily_injection_intensity));
	
				double sharing_prob = 0.0;
	    		if(daily_injection_intensity > 0) {
	    			String sharing_level = line[survey_header_NHBS.get("q106sharndle")];
    				if(sharing_level.equals("0")) {
    					sharing_prob = randUnif01.nextDoubleFromTo(0.0, 0.125);
    				} else if (sharing_level.equals("1")) {
    					sharing_prob = randUnif01.nextDoubleFromTo(0.125, 0.375);
    				} else if (sharing_level.equals("2")) { //half the time
    					sharing_prob = randUnif01.nextDoubleFromTo(0.375, 0.625);
    				} else if (sharing_level.equals("3")) { //most of the time
    					sharing_prob = randUnif01.nextDoubleFromTo(0.625, 0.825);
    				} else if (sharing_level.equals("4")) { //always
    					sharing_prob = randUnif01.nextDoubleFromTo(0.825, 1.0);
    				} else if (sharing_level.equals("7") || sharing_level.equals("9")) { //7=refused, 9=don't know
    					//if(idu_list.size() > 0) {
    					//	int rand_inputation_index = randUnif01.nextIntFromTo(0, idu_list.size()-1);
    					//	sharing_prob = idu_list.get(rand_inputation_index).getFractionReceptSharing(); 
    					//} else {
        				//	sharing_prob = Double.NaN; 
    					//}
    					sharing_prob = randUnif01.nextDoubleFromTo(0.0, 0.125); //will assume some sharing Double.NaN;
    				} else {
    					sharing_prob = Double.NaN; 
    					//throw new Exception("Unrecognized survey value (q106sharndle): " + sharing_level);
    				}
	    		} else {
	    			sharing_prob = 0.0;
				}
	    		//sharing_prob = sharing_prob * 1.233;  //correction for NHBS2012
	    		idu.setFractionReceptSharing(sharing_prob);

	    		if(test_ok(idu, 1)) {
	    			//we test NHBS now;  NEP profiles will be tested before loading data
	    			idu_list.add(idu);
	    		}
	    		
			} catch (Exception e) {
				System.err.println("error reading NHBS row: " + line_num);
				System.err.println(line);
				System.err.println(e.toString());
				e.printStackTrace();
			}
		}
		System.out.println("Finished loading NHBS.  Total valid lines: " + idu_list.size());
        return idu_list;
	}

	public void setSeed(int rng_seed) {
		//the distributions have a default mean (sometimes SD), but is bypassed by an argument
		randNormal  = new Normal(1, 0.01, new cern.jet.random.engine.MersenneTwister(rng_seed));
		randPoisson = new Poisson(10, new cern.jet.random.engine.MersenneTwister(rng_seed));
		randUnif01  = new Uniform(0, 1, rng_seed);
	}
	
	/*
	 * Self-check of the agent
	 * True iff the agent is ready to enter the simulation;  all the attributes are OK.
	 */
	public boolean test_ok(DrugUser idu, int verbose_level) {
		try {
			if (idu.getBirthDate() == null) {
				throw new Exception("birth_date");
				//return false;
			}
			//System.out.println("Career maturity: " + (getAge() - age_started));
			if(idu.getAgeStarted() < 0 || Double.isNaN(idu.getAgeStarted()) || idu.getAgeStarted() > idu.getAge()) {  //no margin of tolerance here b/c need integrity
				throw new Exception("age started");
				//return false;
			}
			if (idu.getCharacteristics() == null) {
				throw new Exception("characteristics");
				//return false;
			}
			if (Double.isNaN(idu.getDrugReceptDegree())) {
				throw new Exception("drug_in_degree");
				//return false;
			}
			if (Double.isNaN(idu.getDrugGivingDegree()) || idu.getDrugReceptDegree() > 98) {
				throw new Exception("drug_out_degree");
				//return false;
			}
			if (Double.isNaN(idu.getFractionReceptSharing())  || idu.getDrugGivingDegree() > 98) {
				throw new Exception("fraction_recept_sharing");
				//return false;
			}
			if ((idu.getHcvState() != HCV_state.ABPOS) && (idu.getHcvState() != HCV_state.susceptible) ) {
				throw new Exception("HCV state");
				//return false;
			}
			if (idu.getGender() == null) {
				throw new Exception("gender");
				//return false;
			}
			if (Double.isNaN(idu.getInjectionIntensity()) || (idu.getInjectionIntensity() > 20)) {
				throw new Exception("injection_intensity");
				//return false;
			}
			if (idu.getPreliminaryZip() == null) {
				throw new Exception("preliminary_zip");
				//return false;
			}
			if(idu.getPreliminaryZip().length() != 5 || idu.getPreliminaryZip().equalsIgnoreCase("0")) {
				throw new Exception("preliminary_zip");
				//return false;
			} 
			if (idu.getRace() == null) {
				throw new Exception("race");
				//return false;
			}
		}
		catch (Exception e) {
			if (verbose_level > 0) {
				System.out.printf("Throwing away IDU (label: %s) b/c of problem in: %s\n", idu.getDatabaseLabel(), e.getMessage());
			}
			return false;
		}
		return true;
	}

}

