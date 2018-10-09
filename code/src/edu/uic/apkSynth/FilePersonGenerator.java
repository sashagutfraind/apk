package edu.uic.apkSynth;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import edu.uic.apk.load.DrugUserData;

/**
 * PersonGenerator implementation that generates DrugUser instances from a file-
 *   based data set.
 *   
 * @author Eric Tatara
 *
 */
public class FilePersonGenerator implements PersonGenerator {

  private static int AGE_IDX = 0;				// Age (String)
  private static int AGE_STARTED_IDX = 1;		// Age started year (float double)
  private static int GENDER_IDX = 2;			// Gender : MALE | FEMALE (String)
  private static int RACE_IDX = 3;				// Race  (String)
  private static int SYRINGE_SOURCE_IDX = 4;	// Syringe Source (String)
  private static int ZIP_IDX = 5;				// Initial zip code (String) since some are not all numeric
  private static int HCV_STATE_IDX = 6;			// HCV State (String)  susceptible, exposed, infectiousacute, recovered, cured, chronic, unknown, ABPOS
  private static int DRUG_REC_DEG_IDX = 7;		// Network drug recept (in) degree (int)
  private static int DRUG_GIV_DEG_IDX = 8;		// Network drug giving (out) degree (int)
  private static int INJECT_INTENS_IDX = 9;		// Injection intensity (float double)
  private static int FRAC_REC_SHAR_IDX = 10;	// Fraction recept sharing (float double)
  
  List<DrugUserData> drugUserDataList;
  
  public FilePersonGenerator(String personFilename) {
	drugUserDataList = loadData(personFilename);
  }
  
  private List<DrugUserData> loadData(String personFilename) {
	
	 List<DrugUserData> personList = new ArrayList<DrugUserData>();
	
	CsvParserSettings settings = new CsvParserSettings();
	settings.getFormat().setLineSeparator("\n");	
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
	  data.gender = line[GENDER_IDX];
	  data.race = line[RACE_IDX];
	  data.syringeSource = line[SYRINGE_SOURCE_IDX];
	  data.zipCode = line[ZIP_IDX];
	  data.hcvState = HCV_state.valueOf(line[HCV_STATE_IDX]);

	  data.drug_inDegree = Integer.valueOf(line[DRUG_REC_DEG_IDX]);
	  data.drug_outDegree = Integer.valueOf(line[DRUG_GIV_DEG_IDX]);
	  data.injectionIntensity = Double.valueOf(line[INJECT_INTENS_IDX]);
	  data.fractionReceptSharing = Double.valueOf(line[FRAC_REC_SHAR_IDX]);

//	  data.early_career = (data.age - data.ageStarted) < maturityThreshold;

	}
	
	return personList;
  }
  
  @Override
  public DrugUser generate(HashMap<String, Object> params) throws Exception {
	// TODO Auto-generated method stub
	return null;
  }

  @Override
  public int catalogueSize() {
	// TODO Auto-generated method stub
	return 0;
  }

}
