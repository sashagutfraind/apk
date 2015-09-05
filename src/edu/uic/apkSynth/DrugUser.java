package edu.uic.apkSynth;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.joda.time.LocalDate;

import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;

/**
 * This is a container for a standard Drug User.
 * The class is intended as a standard format for loading data about drug users from different databases.
 * It stores information about the attributes of the drug user, but does not perform any activities.
 * It also supports imputation using Weka by mapping the attributes into an array.
 * 
 */
public class DrugUser implements SimAgent, java.io.Serializable, Cloneable{
	static private FastVector			       wekaAttInfo       = null;
	static private HashMap<String,Attribute> wekaAttInfoMap    = null;
	
	public final static Integer[] SouthSideZips = {60608,60609,60615,60616,60617,60619,60620,60621,60628,60629,60632,60633,60636,60637,60638,60643,60649,60652,60653,60655,60827};
	public final static Integer[] NorthSideZips = {60602,60603,60604,60605,60610,60611,60613,60614,60618,60625,60626,60630,60631,60634,60640,60641,60645,60646,60654,60656,60657,60659,60660,60666};
	public final static Integer[] WestSideZips  = {60601,60606,60607,60612,60622,60623,60624,60639,60642,60644,60647,60651,60661};

	public static HashSet<Integer> SouthSideZipsHS = (new HashSet<Integer> (Arrays.asList(SouthSideZips)));
	public static HashSet<Integer> NorthSideZipsHS = (new HashSet<Integer> (Arrays.asList(NorthSideZips)));
	public static HashSet<Integer> WestSideZipsHS = (new HashSet<Integer> (Arrays.asList(WestSideZips)));
					
	//private attributes.  initialize to bogus values to catch errors.
	private double age_started                    = -1;
	private LocalDate birth_date 				 	= null;
	private HashMap <String,Object> characteristics = new HashMap <String,Object> ();
	private String dblabel                         = null;
	private double drug_in_degree 					= Double.NaN;
	private double drug_out_degree 				= Double.NaN;
	private LocalDate entry_date             		= null; //simulation_start_date + (1/365.)*RepastEssentials.GetTickCount();
	private double fraction_recept_sharing 		= Double.NaN;
	private Gender gender							= null;
	private HCV_state hcv_state						= HCV_state.unknown;
	private HIV_state hiv_state						= HIV_state.unknown;
	private double injection_intensity     		= Double.NaN;
	private LocalDate last_exposure_date           = new LocalDate(1800, 1, 1);
	private String preliminary_zip 					= null;
	private Race race 								= null;
	private Activity_profile profile               = Activity_profile.in_community; //not used for now
	private LocalDate survey_date 				 	= null;
	private HarmReduction syringe_source			= HarmReduction.nonHR;
	
	private static final long serialVersionUID = -1957901545451182954L;

	public DrugUser() {
	}
	public DrugUser(DrugUser alter) { 
		assert false;  
	}

	/*
	 * Determines the area, based on the ZIP code.
	 */
	public String getGeorgraphicArea() {
		int zip = 0;
		try {
			if(preliminary_zip == null) {
				return "";
			} 
			zip = Integer.parseInt(preliminary_zip);
		} catch (Exception e){
			return "Suburbs";
		}
		if (DrugUser.NorthSideZipsHS.contains(zip)) {
			return "NorthSide";
		} else if (DrugUser.SouthSideZipsHS.contains(zip)) {
			return "SouthSide";
		} else if (DrugUser.WestSideZipsHS.contains(zip)) {
			return "WestSide";
		} else {  //the default.  primarily ZIP!=606XY
			return "Suburbs";
		}
	}
	/*
	 * attribute structure for Weka
	 */
	public static FastVector getAttInfo() {
		if (wekaAttInfo != null) { 
			return (FastVector) wekaAttInfo.copy();
		}
		//wishlist: rename the attribute variables to avoid overloading the class members
		Integer attrib_index = 0;
		wekaAttInfo    = new FastVector();
		wekaAttInfoMap = new HashMap <String,Attribute>();
		Attribute age_started = new Attribute("age_started", attrib_index);
		wekaAttInfo.addElement(age_started);
		wekaAttInfoMap.put("age_started", age_started);
		attrib_index++;
		
		Attribute age = new Attribute("age", attrib_index);
		wekaAttInfo.addElement(age);
		wekaAttInfoMap.put("age", age);
		attrib_index++;

		Attribute drug_in_degree = new Attribute("drug_in_degree", attrib_index);
		wekaAttInfo.addElement(drug_in_degree);
		wekaAttInfoMap.put("drug_in_degree", drug_in_degree);
		attrib_index++;

		//removed - no data in NHBS
		//Attribute drug_out_degree = new Attribute("drug_out_degree", attrib_index);
		//wekaAttInfo.addElement(drug_out_degree);
		//wekaAttInfoMap.put("drug_out_degree", drug_out_degree);
		//attrib_index++;

		Attribute fraction_recept_sharing = new Attribute("fraction_recept_sharing", attrib_index);
		wekaAttInfo.addElement(fraction_recept_sharing);
		wekaAttInfoMap.put("fraction_recept_sharing", fraction_recept_sharing);
		attrib_index++;

		FastVector genders = new FastVector();
		for(Gender o : Gender.values()) {
			genders.addElement(o.toString());
		}
		Attribute gender = new Attribute("gender", genders, attrib_index);
		wekaAttInfo.addElement(gender);
		wekaAttInfoMap.put("gender", gender);
		attrib_index++;

		FastVector hcv_states = new FastVector();
		//hcv_states.addElement("susceptible");  //using 2 states instead of all 6 should improve classification
		//hcv_states.addElement("ab_pos"); 
		for(HCV_state o : HCV_state.values()) {
			hcv_states.addElement(o.toString());
		}
		Attribute hcv_state = new Attribute("hcv_state", hcv_states, attrib_index);
		//numeric (poorly supported) - 1=ab_pos, 0=anything else
		//Attribute hcv_state = new Attribute("hcv_state", attrib_index);
		wekaAttInfo.addElement(hcv_state);
		wekaAttInfoMap.put("hcv_state", hcv_state);
		attrib_index++;

		//would add accuracy, but conceptually problematic
		//FastVector hiv_states = new FastVector();
		//for(HIV_state o : HIV_state.values()) {
		//	hiv_states.addElement(o.toString());
		//}
		//Attribute hiv_state = new Attribute("hiv_state", hiv_states, attrib_index);
		//wekaAttInfo.addElement(hiv_state);
		//wekaAttInfoMap.put("hiv_state", hiv_state);
		//attrib_index++;

		Attribute injection_intensity = new Attribute("injection_intensity", attrib_index);
		wekaAttInfo.addElement(injection_intensity);
		wekaAttInfoMap.put("injection_intensity", injection_intensity);
		attrib_index++;

		FastVector areas = new FastVector();
		areas.addElement("NorthSide");  areas.addElement("SouthSide");  areas.addElement("WestSide");  areas.addElement("Suburbs");
		Attribute preliminary_zip = new Attribute("geographic_area", areas, attrib_index);
		wekaAttInfo.addElement(preliminary_zip);
		wekaAttInfoMap.put("geographic_area", preliminary_zip);
		attrib_index++;

		//String not supported by RandomForest, and certain other classifiers
		//		FastVector dummy = null;
		//		Attribute preliminary_zip = new Attribute("preliminary_zip", dummy, attrib_index);
		//		wekaAttInfo.addElement(preliminary_zip);
		//		wekaAttInfoMap.put("preliminary_zip", preliminary_zip);
		
		FastVector races = new FastVector();
		for(Race o : Race.values()) {
			races.addElement(o.toString());
		}
		Attribute race = new Attribute("race", races, attrib_index);
		wekaAttInfo.addElement(race);
		wekaAttInfoMap.put("race", race);
		attrib_index++;
		//System.out.println("race idx: " + race.index());
		//System.out.println("wekaAttInfo size: " + wekaAttInfo.size());
		
		return (FastVector) wekaAttInfo;
		//return (FastVector) wekaAttInfo.copy();
	}

	public static HashMap <String, Attribute> getAttribMap() {
		if (wekaAttInfo == null) {
			getAttInfo();
		}
		assert wekaAttInfoMap != null;
		return wekaAttInfoMap;
	}

	public Instance getInstance() {
		//wishlist: calls to setValue cause a copy of the internal structure.  see if there is a faster approach
		Instance instance = new Instance(getAttInfo().size());
		
		if (age_started != Integer.MAX_VALUE) {
			instance.setValue(wekaAttInfoMap.get("age_started"), age_started);
		} else {
			instance.setMissing(wekaAttInfoMap.get("age_started"));
		}

		instance.setValue(wekaAttInfoMap.get("age"), 2014 - birth_date.getYear()); //age at 2014
		//future: this might be slightly better, and is also now the default value for getAge()
		//instance.setValue(wekaAttInfoMap.get("age"), survey_date.getYear() - birth_date.getYear());

		if (drug_in_degree != Double.NaN) {
			instance.setValue(wekaAttInfoMap.get("drug_in_degree"), drug_in_degree);
		} else {
			instance.setMissing(wekaAttInfoMap.get("drug_in_degree"));
		}

		//NHBS lacks data on this
		//if (drug_out_degree != Double.NaN) {
		//	instance.setValue(wekaAttInfoMap.get("drug_out_degree"), drug_out_degree);
		//} else {
		//	instance.setMissing(wekaAttInfoMap.get("drug_out_degree"));
		//}

		if (fraction_recept_sharing != Double.NaN) {
			instance.setValue(wekaAttInfoMap.get("fraction_recept_sharing"), fraction_recept_sharing);
		} else {
			instance.setMissing(wekaAttInfoMap.get("fraction_recept_sharing"));
		}

		instance.setValue(wekaAttInfoMap.get("gender"), gender.toString());

		if (hcv_state != HCV_state.unknown) {
			instance.setValue(wekaAttInfoMap.get("hcv_state"), hcv_state.toString());
		} else {
			instance.setMissing(wekaAttInfoMap.get("hcv_state"));
		}
		
		//we not using HIV
		//		if (hiv_state != HIV_state.unknown) {
		//			instance.setValue(wekaAttInfoMap.get("hiv_state"), hiv_state.toString());
		//		} else {
		//			instance.setMissing(wekaAttInfoMap.get("hiv_state"));
		//		}
		
		if (injection_intensity != Double.NaN) {
			instance.setValue(wekaAttInfoMap.get("injection_intensity"), injection_intensity);
		} else {
			instance.setMissing(wekaAttInfoMap.get("injection_intensity"));
		}

		//System.out.println(preliminary_zip);
		//wishlist: detect bad zips
		//Integer zip;
		//try{
		//	zip = Integer.parseInt(preliminary_zip);
		//} catch (java.lang.NumberFormatException e) {
		//	zip = -1; //wishlist: better default
		//}
		instance.setValue(wekaAttInfoMap.get("geographic_area"), getGeorgraphicArea());

		instance.setValue(wekaAttInfoMap.get("race"), race.toString());
		return instance;
	}
	
	public DrugUser clone() {
		//x.clone() != x && x.clone().getClass() == x.getClass() && x.clone().equals(x)
		try {
			DrugUser ret = (DrugUser) super.clone();
			ret.characteristics   	= (HashMap<String, Object>) this.characteristics.clone();
			return ret;
		} catch(Exception e){ 
			return null; 
		}
	}
	/*
	 * returns age as of survey date.  
	 * DrugUser agents do not age - needed for imputation.  Only the related IDU agents can age.
	 */
	public Double getAge() {
		LocalDate dt = survey_date.minusDays(birth_date.getDayOfYear()).minusYears(birth_date.getYear());
		Double ret = dt.getYear() + (dt.getDayOfYear() / 365.0);
		return ret;
	}
	@Override
	public double getAgeStarted() {
		return age_started;
	}
	@Override
	public void setAgeStarted(double new_age) {
		assert new_age != Double.NaN;
		age_started = new_age;
	}
	@Override
	public LocalDate getBirthDate() {
		return birth_date;
	}
	@Override
	public void setBirthDate(LocalDate b_date) {
		birth_date = b_date;
	}
	@Override
	public Object getCharacteristic(String name) {
		return characteristics.get(name);
	}
	@Override
	public HashMap<String, Object> getCharacteristics() {
		return characteristics;
	}
	@Override
	public void setCharacteristics(HashMap<String, Object>  attributes) {
		this.characteristics = new HashMap<String,Object> ();
		this.characteristics.putAll(attributes);
	}
	@Override
	public void setDatabaseLabel(String label) {
		dblabel = label;
	}
	@Override
	public String getDatabaseLabel() {
		return dblabel;
	}

	@Override
	public void setDrugGivingDegree(double v) {
		drug_out_degree = v;
	}
	@Override
	public double getDrugGivingDegree() {
		return drug_out_degree;
	}
	@Override
	public double getDrugReceptDegree() {
		return drug_in_degree;
	}
	@Override
	public void setDrugReceptDegree(double val) {
		assert val!= Double.NaN;
		drug_in_degree = val;
	}
	public LocalDate getEntryDate() {
		return entry_date;
	}
	public void setEntryDate(LocalDate val) {
		entry_date = val;
	}

	@Override
	public double getFractionReceptSharing() {
		return fraction_recept_sharing;
	}
	@Override
	public void setFractionReceptSharing(double val) {
		assert val!= Double.NaN;
		fraction_recept_sharing = val;
	}

	@Override
	public Gender getGender() {
		return gender;
	}
	@Override
	public void setGender(Gender val) {
		assert val!= null;
		gender = val;
	}
	@Override
	public HCV_state getHcvState() {
		return hcv_state;
	}	
	@Override
	public void setHcvState(HCV_state state) {
		hcv_state = state;
	}
	public HIV_state getHivState() {
		return hiv_state;
	}	
	public void setHivState(HIV_state state) {
		assert false; //this functionality is disabled
		hiv_state = state;
	}
	@Override
	public double getInjectionIntensity() {
		return injection_intensity;
	}
	@Override
	public void setInjectionIntensity(double val) {
		assert val!= Double.NaN;
		injection_intensity = val;
	}
	protected LocalDate getLastExposureDate() {
		//the time of the last infection event (not just contact with an HCV+ IDU)
		return last_exposure_date;
	}
	public String getLastExposureDate_() {
		return last_exposure_date.toString();
	}
	@Override
	public String getPreliminaryZip() {
		//preliminary zip is used before the agent is associated with a zone
		if (! preliminary_zip.equals("")){
			return preliminary_zip;
		} else {
			return null;
		}
	}
	@Override
	public void setPreliminaryZip(String z) {
		assert z!=null;
		preliminary_zip = z.toString();
	}
	
	public String getRace_() {
		return race.toString();
	}
	@Override
	public Race getRace() {
		return race;
	}
	@Override
	public void setRace(Race val) {
		assert val!=null;
		race = val;
	}
	public LocalDate getSurveyDate() {
		return survey_date;
	}
	public void setSurveyDate(LocalDate s_date) {
		survey_date = s_date;
	}
	public HarmReduction getSyringe_source() {
		return syringe_source;
	}
	public void setSyringe_source(HarmReduction syringe_source) {
		this.syringe_source = syringe_source;
	}

	public boolean isEarlyCareer(double maturity_threshold) {
		return (getAge() - getAgeStarted()) < maturity_threshold;
	}
	@Override
	public boolean isHcvABpos() {
		return (hcv_state == HCV_state.infectiousacute) || (hcv_state == HCV_state.chronic) || (hcv_state == HCV_state.recovered) || (hcv_state == HCV_state.ABPOS);
	}
	@Override
	public boolean isHcvRNA() {
		return (hcv_state == HCV_state.infectiousacute) || (hcv_state == HCV_state.chronic);
	}
	
	@Override
	public void receive_equipment_or_drugs() {
		assert false;
	}
	@Override
	public String toString() {
		//"HCV_friend_preval" + +Activity_profile
		String ret = "Age"+getAge()+" Age_Started"+getAgeStarted()+" Gender"+gender+" Race"+race+" Zip"+preliminary_zip+" HCV"+hcv_state.toString()
			      	+" Drug_in_degree"+drug_in_degree+" Drug_out_degree"+drug_out_degree//" Num Buddies"+getNumBuddies()
				    +" Daily_injection_intensity"+injection_intensity+" Fraction_recept_sharing"+fraction_recept_sharing
				    +" Database_label"+dblabel;
		
		return ret;		
	}
}
