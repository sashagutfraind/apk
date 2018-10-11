package edu.uic.apk.load;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.joda.time.LocalDate;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import cern.jet.random.Uniform;
import edu.uic.apk.APKBuilder;
import edu.uic.apk.IPersonGenerator;
import edu.uic.apkSynth.DrugUser;
import edu.uic.apkSynth.Gender;
import edu.uic.apkSynth.HCV_state;
import edu.uic.apkSynth.HarmReduction;
import edu.uic.apkSynth.Race;

/**
 * PersonGenerator implementation that generates DrugUser instances from a file-
 *   based data set.
 *   
 * @author Eric Tatara
 *
 */
public class FilePersonGenerator implements IPersonGenerator {

  private static int AGE_IDX = 0;							// Age (String)
  private static int AGE_STARTED_IDX = 1;			// Age started year (float double)
  private static int GENDER_IDX = 2;					// Gender : MALE | FEMALE (String)
  private static int RACE_IDX = 3;						// Race  (String)
  private static int SYRINGE_SOURCE_IDX = 4;	// Syringe Source (String)
  private static int ZIP_IDX = 5;							// Initial zip code (String) since some are not all numeric
  private static int HCV_STATE_IDX = 6;				// HCV State (String)  susceptible, exposed, infectiousacute, recovered, cured, chronic, unknown, ABPOS
  private static int DRUG_REC_DEG_IDX = 7;		// Network drug recept (in) degree (int)
  private static int DRUG_GIV_DEG_IDX = 8;		// Network drug giving (out) degree (int)
  private static int INJECT_INTENS_IDX = 9;		// Injection intensity (float double)
  private static int FRAC_REC_SHAR_IDX = 10;	// Fraction recept sharing (float double)
  
  List<DrugUserData> drugUserDataList;
	Uniform unif01; 
	private double pwid_maturity_threshold = Double.NaN;
	
  public FilePersonGenerator(String personFilename, double maturity_threshold_param, int rng_seed) {
  	pwid_maturity_threshold = maturity_threshold_param;
  	unif01 = new Uniform(0,1,rng_seed);
  	
  	drugUserDataList = loadData(personFilename);
  }
  
  private List<DrugUserData> loadData(String personFilename) {
	
	 List<DrugUserData> personList = new ArrayList<DrugUserData>();
	
	CsvParserSettings settings = new CsvParserSettings();
	settings.getFormat().setLineSeparator("\n");	
	settings.setHeaderExtractionEnabled(true);
	CsvParser parser = new CsvParser(settings);
	
	InputStreamReader reader = null;
	try {
	  reader = new InputStreamReader(new FileInputStream(personFilename));
	} catch (FileNotFoundException e) {
	  e.printStackTrace();
	}
	
	List<String[]> allRows = parser.parseAll(reader);
	for (String[] line : allRows) {
	  DrugUserData data = new DrugUserData();
	  data.age = Double.valueOf(line[AGE_IDX]);
	  data.ageStarted = Double.valueOf(line[AGE_STARTED_IDX]);
	  data.gender = Gender.valueOf(line[GENDER_IDX]);
	  data.race = Race.valueOf(line[RACE_IDX]);
	  data.syringeSource = HarmReduction.valueOf(line[SYRINGE_SOURCE_IDX]);
	  data.zipCode = line[ZIP_IDX];
	  data.hcvState = HCV_state.valueOf(line[HCV_STATE_IDX]);

	  data.drug_inDegree = Integer.valueOf(line[DRUG_REC_DEG_IDX]);
	  data.drug_outDegree = Integer.valueOf(line[DRUG_GIV_DEG_IDX]);
	  data.injectionIntensity = Double.valueOf(line[INJECT_INTENS_IDX]);
	  data.fractionReceptSharing = Double.valueOf(line[FRAC_REC_SHAR_IDX]);

	  data.early_career = (data.age - data.ageStarted) < pwid_maturity_threshold;

	  personList.add(data);
	}
	
	return personList;
  }
  
  @Override
  public DrugUser generate(HashMap<String, Object> params) throws Exception {
  	DrugUser du = new DrugUser();
  	
  	// TODO Refactor with In memory person generator for new abstract class with shared code
  	// TODO handle params like early career
  	
  	Boolean early_idus_only = false;
		if(params.containsKey("early_idus_only")) {
			early_idus_only = (Boolean) params.get("early_idus_only");
		}
		Integer db_reference_number = null;
		if (params.containsKey("db_reference_number")) {
			db_reference_number = (Integer) params.get("db_reference_number");
			if (db_reference_number >= drugUserDataList.size()) {
				throw new Exception("reference_too_big");
			}
		}
  	
		DrugUserData data = null;
  	int remaining_trials;
	
		//attempt to draw DrugUser from the catalog.  
		for (remaining_trials = 250; remaining_trials > 0; --remaining_trials) {
			int model_instance_index;
			if (db_reference_number == null) {
				model_instance_index = unif01.nextIntFromTo(0, drugUserDataList.size()-1);
			} else {
				model_instance_index = db_reference_number;
				remaining_trials = 1; //would have just one chance to make this IDU without an Exception
			}
			data = drugUserDataList.get(model_instance_index);
		
			if (early_idus_only && (! data.early_career)) {		
				continue; //too old
			} 
			
//			idu = model_idu.clone();
//			idu.getCharacteristics().put("ID", idu.hashCode());
			break;
		}  
		if (remaining_trials == 0) {
			throw new Exception("Error in basic profile information");
		}  	
  	
		// Birth date is current date - age
		LocalDate birthDate = APKBuilder.getSimulationDate();
		birthDate = birthDate.minusDays((int)(data.age*365));
		
		// TODO assume the survey date is now to prevent additional age modifications
		//      in the IDUBuilder.
		LocalDate surveyDate = APKBuilder.getSimulationDate();
		du.setSurveyDate(surveyDate);
  	du.setBirthDate(birthDate);
  	du.setAgeStarted(data.ageStarted);
  	du.setDrugGivingDegree(data.drug_outDegree);
  	du.setDrugReceptDegree(data.drug_inDegree);
  	du.setFractionReceptSharing(data.fractionReceptSharing);
  	du.setGender(data.gender);
  	du.setHcvState(data.hcvState);
  	du.setInjectionIntensity(data.injectionIntensity);
  	du.setPreliminaryZip(data.zipCode);
  	du.setRace(data.race);
  	du.setSyringe_source(data.syringeSource);
  	
  	return du;
  }


  @Override
  public int catalogueSize() {
  	return drugUserDataList.size();
  }
}