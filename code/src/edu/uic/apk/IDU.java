/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import cern.jet.random.Exponential;
import cern.jet.random.Normal;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import edu.uic.apk.Immunology.TRIAL_ARM;
import edu.uic.apkSynth.Activity_profile;
import edu.uic.apkSynth.Gender;
import edu.uic.apkSynth.HCV_state;
import edu.uic.apkSynth.Race;
import edu.uic.apkSynth.SimAgent;
import edu.uic.apkSynth.HarmReduction;
import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.essentials.RepastEssentials;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.ui.probe.ProbeID;

/*
 * agent representing the Person who Injects Drugs (PWID), also known as Injecting Drug User (IDU) in some sources
 * - stores attributes
 * - contains drug related behaviors
 * - health behaviors are in the Immunology member class (imm)
 * 
 * wishlist
 * 1. rename injection intensity to daily injections
 * 2. hide from the GUI some of the boolean variables like "isBlack"
 */
public class IDU implements SimAgent, java.io.Serializable, Cloneable {
	static transient final String[] male_names   = {"Jacob","Mason","William","Jayden","Noah","Michael","Ethan","Alexander","Aiden","Daniel",};
	static transient final String[] female_names = {"Sophia","Isabella","Emma","Olivia","Ava","Emily","Abigail","Madison","Mia","Chloe",};
	
	//all of those variables are not currently used
	static final double mean_time_in_community = Double.NaN;
	static final double mean_time_incarcerated = Double.NaN;
	static final double mean_time_in_rehab = Double.NaN;
	static final double rehab_risk_factor  = Double.NaN;

	static private double homophily            = Double.NaN; 
	static private double mean_tie_lifetime    = Double.NaN; //days
	static private double treatment_nonadherence = Double.NaN; //days
	static private double attrition_rate 	      = Double.NaN; //includes death and incarceration
	static private double mean_career_duration = Double.NaN; 
	static private double prob_cessation 	      = Double.NaN; 
	
	private static final long serialVersionUID = -5388747128529153646L; //class ID for serialization

	static transient private Context context;
	static transient private Geography<IDU> geography;
	static transient private Network<IDU> network;

	private static Normal cessation_time_distribution = null;
	private static Exponential life_time_distribution = null;
	private static Exponential tie_endurance_distribution = null;
	
	//private attributes.  initializes to bogus values to catch errors.
	private boolean active = false; //whether the idu participates in the simulation (false in some prototype IDUs)
	private double age_started                    = -1;
	private LocalDate birth_date 				 	= null;
	private HashMap <String,Object> characteristics = new HashMap <String,Object> ();
	private String	dblabel							= null; //any label or rowid for the progenitoc profile for this agent 
	private double drug_in_degree 					= Double.NaN;
	private double drug_out_degree 				= Double.NaN;
	private LocalDate entry_date             		= null; //simulation_start_date + (1/365.)*RepastEssentials.GetTickCount();
	private double fraction_recept_sharing 		= Double.NaN;
	private Gender gender							= null;
	private Immunology imm				     		= new Immunology(this);
	private double injection_intensity     		= Double.NaN; //doses per day 
	private LocalDate last_exposure_date           = new LocalDate(1800, 1, 1);  //bogus value to detect errors
	private boolean moved; //this field is watched
	private transient ISchedulableAction my_end = null; 
	private transient ISchedulableAction my_status= null; 
	private transient ZoneAgent my_zone 			= null;
	private String name								= null;
	private String preliminary_zip 					= null;
	private Race race 								= null;
	private HarmReduction hr_enrollment            = HarmReduction.nonHR;
	//wishlist - change the profile dynamically in the simulation
	private Activity_profile profile	    		= Activity_profile.in_community; 
	
	//wishlist: mark up methods with @Override as needed

	public enum AgeDecade{
		AgeLEQ20, Age21to30, Age31to40, Age41to50, Age51to60, AgeOver60;
	}
	public enum AgeGroup{
		LEQ30, Over30;
	}
	public enum AreaType{
		City, Suburban;
	}
	
	public IDU() {
	}
	/*
	 * creates a partial copy of the existing IDU.  the new IDU would not be added to the context.
	 * 	 the copy would have un-initialized field: death;   call activate()
	 *   the copy would be otherwise identical - no randomization (even for ID)
	 */
	public IDU(IDU alter) { 
		assert alter.self_test_ok(0);
		
		this.active					= false;
		this.age_started			= alter.age_started;
		this.birth_date				= alter.birth_date;
		this.characteristics   		= (HashMap<String, Object>) alter.characteristics.clone();
		this.dblabel                = alter.dblabel;
		this.drug_in_degree   		= alter.drug_in_degree;
		this.drug_out_degree   		= alter.drug_out_degree;
		this.entry_date 			= alter.entry_date;
		this.fraction_recept_sharing = alter.fraction_recept_sharing;
		this.gender 				= alter.gender;
		this.hr_enrollment 			= alter.hr_enrollment;
		this.imm 					= new Immunology(alter.getHcvState(), this);
		this.injection_intensity   	= alter.injection_intensity;
		this.last_exposure_date 	= alter.last_exposure_date;
		this.moved                 = false;
		this.my_end   				= null;
		this.my_zone				= alter.my_zone;
		this.name					= alter.name;
		this.preliminary_zip        = alter.preliminary_zip;
		this.race                   = alter.race;
		this.profile                = alter.profile;
		
		assert self_test_ok(0);
	}
	
	public IDU clone() {
		return new IDU(this);
	}
	public boolean isActive() {
		return active;
	}

	public double getAge() {
		double ret = APKBuilder.getDateDifference(null, birth_date);
		return ret;
	}
	
	public AgeDecade getAgeDecade() {
		double age = getAge();
		if(age<=20) {
			return AgeDecade.AgeLEQ20;
		} else if(age<=30) {
			return AgeDecade.Age21to30;
		} else if(age<=40) {
			return AgeDecade.Age31to40;
		} else if(age<=50) {
			return AgeDecade.Age41to50;
		} else if(age<=60) {
			return AgeDecade.Age51to60;
		} else {
			return AgeDecade.AgeOver60;
		}
	}
	
	public AgeGroup getAgeGroup() {
		return (getAge() <= 30)? AgeGroup.LEQ30: AgeGroup.Over30;
	}
	
	@Override
	public double getAgeStarted() { 
		return age_started;
	}
	@Override
	public void setAgeStarted(double new_age) {
		assert ! Double.isNaN(new_age);
		age_started = new_age;
	}
	public static void setAttrition_rate(double val) {
		IDU.attrition_rate = val;
		life_time_distribution = RandomHelper.createExponential(attrition_rate/365.0); //from birth
	}

	/*
	 * Area of Chicago
	 * @return City if and only if the the ZIP code starts with 606
	 */
	public AreaType getAreaType() {
		String zip = this.getZip();
		if((zip != null) && (! zip.substring(0,3).equalsIgnoreCase("606"))) {
			return AreaType.Suburban;
		} else {
			return AreaType.City;
		}

	}
	@Override
	public LocalDate getBirthDate() {
		return birth_date;
	}
	@Override
	public void setBirthDate(LocalDate b_date) {
		birth_date = b_date;
	}
	public String getBirthDate_() {
		DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy/MM/dd");
		return birth_date.toString(dtf);
	}
	//wishlist: make the list visible in the Agent menu
	public Iterable <IDU> getBuddies() {
		try{ //might be deleted ..
			return network.getAdjacent(this);
		}
		catch (Exception e) {
			return null;
		}
	}
	public HashMap <String,Object> getCharacteristics() {
		return characteristics;
	}

	public void setCharacteristics(HashMap<String, Object> new_characteristics) {
		this.characteristics = new HashMap <String,Object> ();
		updateCharacteristics(new_characteristics);
	}

	public void updateCharacteristics(HashMap <String, Object> new_characteristics) {
		try{
			for(Object attr_name : new_characteristics.keySet()) {
				assert attr_name instanceof String;
				characteristics.put((String)attr_name, new_characteristics.get(attr_name));
			}
		} catch (Exception e) {
			System.out.println(e.toString());
		}
	}
	
	@Override
	public Object getCharacteristic(String name) {
		return characteristics.get(name);
	}
	public String getCharacteristics_() {
		return characteristics.toString();
	}
	/*
	 * misc information about this IDU
	 */
	public void setCharacteristic(String name, Object val) {
		HashMap <String,Object> hm = new HashMap <String,Object> ();
		hm.put(name, val);
		updateCharacteristics(hm);
	}
	
	public TRIAL_ARM getCurrent_trial_arm() {
		return imm.getCurrent_trial_arm();
	}
	public void setCurrent_trial_arm(TRIAL_ARM current_trial_arm) {
		imm.setCurrent_trial_arm(current_trial_arm);
	}	
	public VACCINE_STAGE getCurrent_trial_stage() {
		return imm.getVaccine_stage();
	}
	public void setVaccine_stage(VACCINE_STAGE vaccine_stage) {
		imm.setVaccine_stage(vaccine_stage);		
	}
	
	@Override
	public void setDatabaseLabel(String label) {
		this.dblabel = label;
		
	}
	@Override
	public String getDatabaseLabel() {
		return dblabel;
	}
	public int getDrug_market() {
		return my_zone.getDrug_market();
	}
	/*
	 * the number of in-connections this node is supposed to have. the actual number might be smaller
	 */
	@Override
	public double getDrugReceptDegree() {
		return drug_in_degree;
	}
	@Override
	public void setDrugReceptDegree(double val) {
		assert ! Double.isNaN(val);
		drug_in_degree = val;
	}
	/*
	 * the number of out-connections this node is supposed to have. the actual number might be smaller
	 */
	@Override
	public double getDrugGivingDegree() {
		return drug_out_degree;
	}
	@Override
	public void setDrugGivingDegree(double val) {
		assert ! Double.isNaN(val);
		drug_out_degree = val;
	}
	public LocalDate getEntryDate() {
		return entry_date;
	}
	public String getEntryDate_() {
		assert entry_date != null;
		DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy/MM/dd");
		return entry_date.toString(dtf);
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
		assert ! Double.isNaN(val);
		fraction_recept_sharing = val;
	}
	/*
	 * number of unique persons connected to this IDU (either in the in-network or in out-network, not double counting)
	 */
	public int getNumBuddies() {
		HashSet <IDU> buddies = new HashSet <IDU>();
		try {
			buddies.addAll((Collection<IDU>)network.getPredecessors(this));
			buddies.addAll((Collection<IDU>)network.getSuccessors(this));
			return buddies.size();
			//return network.getDegree(this); //returns total degree, but we want unique persons
		} catch (Exception e) {
			return 0;
		}
	}

	public String getGender_() {
		return gender.toString();
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

	public void setGender_(String gender) throws RuntimeException {
		if(gender == null) {
			return;
		}
		if (gender.equals("Male")) {
			this.gender = Gender.Male;
		} else if (gender.equals("Female") )  {
			this.gender = Gender.Female;
		} else {
			throw new RuntimeException("Unknown gender");
		}
	}

	@Override
	public HCV_state getHcvState() {
		return imm.getHcvState();
	}	
	public String getHcvState_() {
		return imm.getHcvState().toString();
	}	
	@Override
	public void setHcvState(HCV_state state) {
		imm.setHcvInitState(state, 1);
	}
	/*
	 * initial HCV state (i.e., when the agent just enters the simulation).  generally, infection states are only changed by the Immunology class
	 * setting the initial state is not logged as a true infection event;  
	 * the course of infection is different b/c some time must have passed from the exposure to t=0.
	 */
	public void setHcvInitialState(HCV_state state) {
		assert ! active;
		assert ! context.contains(this);
		imm.setHcvInitState(state, 0);
	}
	public void setHcvState_(String state) {
		if (state.equalsIgnoreCase("susceptible")) {
			imm.setHcvInitState(HCV_state.susceptible, 1); 
		} else if (state.equalsIgnoreCase("exposed")) {
			imm.setHcvInitState(HCV_state.exposed, 1); 
		} else if (state.equalsIgnoreCase("infectious")) {
			imm.setHcvInitState(HCV_state.infectiousacute, 1); 
		} else if (state.equalsIgnoreCase("resistant")) {
			imm.setHcvInitState(HCV_state.recovered, 1); 
		} else if (state.equalsIgnoreCase("chronic")) {
			imm.setHcvInitState(HCV_state.chronic, 1); 
		} else if (state.equalsIgnoreCase("unknown")) {
			imm.setHcvInitState(HCV_state.chronic, 1);  //wishlist: do this better
		} else {
			System.out.println("Failed to set HCV state. Cannot recognize state: " + state);
		}
	}
	public double getHcvNeighborPrevalence() {
		if(! context.contains(this)) {
			return 0.0;
		}
		int num_nbs      = network.getDegree(this);
		int num_infected = 0;
		if(num_nbs == 0) {
			return 0.0;
		}
		for(IDU nb : network.getAdjacent(this)) {
			if(nb.isHcvRNA()) {
				num_infected += 1;
			}
		}
		return ((double)num_infected)/num_nbs;
	}	
	public static void setHomophilyStrength(double val) {
		homophily = val;
	}

	@ProbeID
	public String getSimID() { 
		return "" + this.hashCode();
	}
	
	@Override
	public double getInjectionIntensity() { 
		return injection_intensity;
	}
	@Override
	public void setInjectionIntensity(double val) {
		assert ! Double.isNaN(val);
		injection_intensity = val;
	}
	public LocalDate getLastExposureDate() {
		//the time of the last infection event (not just contact with an HCV+ IDU)
		return last_exposure_date;
	}
	public String getLastExposureDate_() {
		return last_exposure_date.toString();
	}
	public void setLastExposureDate() {
		//the time of the last infection event (not just contact with an HCV+ IDU)
		last_exposure_date = APKBuilder.getSimulationDate();
	}
	public static void setMean_career_duration(Double val) {
		IDU.mean_career_duration = val;
		IDU.cessation_time_distribution = RandomHelper.createNormal(IDU.mean_career_duration*365.0, IDU.mean_career_duration*365.0/3.0);
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		assert name != null;
		this.name = name;
	}
	public String getPreliminaryZip() {
		//preliminary zip is used before the agent is associated with a zone
		if (! preliminary_zip.equals("")){
			return preliminary_zip;
		} else {
			return null;
		}
	}
	public void setPreliminaryZip(String z) {
		assert z!=null;
		preliminary_zip = z;
	}

	Activity_profile getProfile() {
		return null; //not used
		//return profile;
	}
	
	public static void setProb_cessation(Double val) {
		IDU.prob_cessation = val;
	}
	public String getProfile_() {
		return profile.toString();
	}

	public void setProfile(Activity_profile profile) {
		assert false; //not used
		//this.profile = profile;
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
	public void setRace_(String val) {
		assert val!=null;
		race = Race.valueOf(val);
	}

	public static void setMeanTieLifetime(double val) {
		IDU.mean_tie_lifetime = val; //in years
		tie_endurance_distribution = RandomHelper.createExponential((1./mean_tie_lifetime)/365.);  //1 year divided by val in years, returns distribution in days
	}
	
	public static void setTreatmentNonadherence(double val) {
		IDU.treatment_nonadherence = val; //fraction
	}
	public Double getTreatmentStartDate() {
		return imm.getTreatmentStartDate();
	}
	

	public String getZip() {
		if (active) {
			assert my_zone != null;
			return my_zone.getZip();
		} else {
			//wishlist: why is this even called?  
			//System.err.println("Warning: no zip code for inactive agent.  Prelim zip:" + preliminary_zip);
			//assert false;
			if(my_zone != null) {
				return my_zone.getZip();
			} else {
				return null;
			}
		}
	}
	public ZoneAgent getZone() {
		return my_zone;
	}

	public void setZone(ZoneAgent z) {
		assert my_zone == null;  //only set once
		assert z != null;
		my_zone = z;
	}

	/*
	 * check if the connection is acceptable topologically or considering degree.
	 * homophily is not checked here b/c it should only be checked once
	 */
	public boolean acceptable_in_connection(IDU other) {
		if (network.getInDegree(this) >= drug_in_degree) {
			return false;
		}
		//if (network.isSuccessor(this, other) ) { 
		//	return false;
		//}
		return true;
	}
	
	public boolean acceptable_out_connection(IDU other) {
		if (network.getOutDegree(this) >= drug_out_degree) {
			return false;
		}
		//if (network.isPredecessor(this, other) ) {
		//	return false;
		//}
		return true;
	}

	/*
	 * let the agent know that it's about go enter the simulation
	 * this method should be called before adding the IDU to context
	 * life_extension is used to adjust for possible burn-in time or time already in the drug career
	 */
	public boolean activate(double residual_burnin_days, double elapsed_career_days, double status_report_frequency) {
		active = true;
		if(! schedule_end(residual_burnin_days, elapsed_career_days)) {
			return false;
		}
		
		if(status_report_frequency > 0) {
			ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
			ScheduleParameters sched_params = ScheduleParameters.createRepeating(RepastEssentials.GetTickCount()+0.0001, status_report_frequency);
			my_status = schedule.schedule(sched_params, this, "report_status");		
		}
		return true;
	}

	public void change_profile(Activity_profile profile) {
		this.profile = profile;
		
		//this functionality is disabled for now
		/*
		Activity_profile next_profile;
		while(true) {
			next_profile = Activity_profile.get_random();
			if (next_profile != profile){
				break;
			}
		}

        double mean_wait;
        if(profile == Activity_profile.in_community) {
        	mean_wait = mean_time_in_community;
        } else if (profile == Activity_profile.in_community) {
        	mean_wait = mean_time_incarcerated;
        } else {
        	mean_wait = mean_time_in_rehab;
        }

		double start_time = RunEnvironment.getInstance().getCurrentSchedule().getTickCount() 
				            + RandomHelper.createExponential(1./mean_wait).nextDouble();
        ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		ScheduleParameters sched_params = ScheduleParameters.createOneTime(start_time);
		schedule.schedule(sched_params, this, "change_profile", next_profile);
		//System.out.printf("Agent: %s;  time: %.1f, new profile: %s,    next change in tick: %.1f, next profile: %s\n", 
		//				   this.name, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), profile.toString(), start_time, next_profile.toString());
		*/
	}

	public boolean can_accept_in_or_out_connection() {
		boolean not_incarcerated = (profile != Activity_profile.incarcerated);
		boolean has_free_in_deg  = (network.getInDegree(this) < drug_in_degree);
		boolean has_free_out_deg = (network.getOutDegree(this) < drug_out_degree);
		return not_incarcerated && (has_free_in_deg || has_free_out_deg);
	}

	/*
	 * called when the agent ceases IDU or dies
	 * repast removes the agent from the context and all projections (including the network), but I did not see any removal from the scheduler
	 * imm.deactiave would remove this agent from all health-based actions;  Statistics would block all events from agents not in the context
	 */
	public void deactivate() {
		//wishlist: creation of new agents should be a transaction, so that everything could be rolled back
		Statistics.fire_status_change(AgentMessage.deactivated, this, "", null);
		context.remove(this);
		imm.deactivate();
		if(my_status != null) {
			ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
			schedule.removeAction(my_status); 
		}
	}

	/*
	 * attribute and social distance between the individuals (>=0)
	 * specifically excludes geographic distance
	 * assumes all attributes are weighted by 1.0.  allow some to contribute > 1.0
	 */
	private double demographic_distance(IDU other) {
		double ret = 0.0;
		ret += race == other.race? 0.0: 1.0;
		ret += (Math.abs(APKBuilder.getDateDifference(this.birth_date, other.birth_date) / 10.0)); 
		return ret / 2.0;
	}

	public void end_relationship(IDU buddy) {
		RepastEdge <IDU> e = network.getEdge(this, buddy);
		if (e != null) {
			network.removeEdge(e);
		}
	}

	public boolean isAcute() {
		return imm.isAcute();
	}
	public boolean isBlack() {
		return race == Race.NHBlack;
	}
	public boolean isChronic() {
		return imm.isChronic();
	}
	public boolean isCity() {
		return getAreaType() == AreaType.City;
	}
	public boolean isCured() {
		return imm.isCured();
	}
	public boolean isFemale() {
		return gender == Gender.Female;
	}
	public boolean isHcvABpos() {
		return imm.isHcvABpos();
	}
	public boolean isHcvRNA() {
		return imm.isHcvRNA();
	}
	public boolean isHispanic() {
		return race == Race.Hispanic;
	}
	public boolean isInfectious() {
		return imm.isInfectious();
	}
	public boolean isInTreatment() {
		return imm.isInTreatment();
	}
	public boolean isInHarmReduction() {
		return hr_enrollment == HarmReduction.HR;
	}
	public boolean isMale() {
		return gender == Gender.Male;
	}
	public boolean isNaive() {
		return imm.isNaive();
	}
	public boolean isNHWhite() {
		return race == Race.NHWhite;
	}
	public boolean isPostTreatment() {
		return imm.isPostTreatment();
	}
	public boolean isResistant() {
		return imm.isResistant();
	}
	public boolean isSuburban() {
		return getAreaType() == AreaType.Suburban;
	}
	public boolean isTreatable() {
		return imm.isTreatable();
	}
	public boolean isVaccineTrialSuitable() {
		if (imm.getVaccine_stage() != VACCINE_STAGE.notenrolled) {
			return false;
		}
		if (isHcvRNA() || isInTreatment() || isResistant()) {
			return false; 
		}
		if (getAge() < 18 || getAge() > 60) {
			//wishlist: control with parameters
			return false;
		}
		if (injection_intensity < 1/30.0) { //at least once a month
			//wishlist: control with parameters
			return false;
		}
		return true;
	}
	public boolean isInVaccineTrial() {
		return (imm.getCurrent_trial_arm() != TRIAL_ARM.noarm) && 
				(imm.getVaccine_stage() != VACCINE_STAGE.completed) && 
				(imm.getVaccine_stage() != VACCINE_STAGE.abandoned);
	}

	public boolean isYoung() {
		return getAgeGroup() == AgeGroup.LEQ30; 
	}
	
	/*
	 * The geographic location of the agent, based on the centroid of the ZIP code.
	 * Only used by the GUI.  Adds jitter, to help display multiple IDUs in the same zone
	 */
	public Point getLocation() {  	
		//movement is actually only used in the GUI 
		Geometry geom = geography.getGeometry(this);
		Coordinate coord = geom.getCoordinates()[0];
		Coordinate possible_agent_coord = new Coordinate(coord.x + 0.001*(Math.random() - .5), 
			                                           	 coord.y + 0.001*(Math.random() - .5));
		Point pt = (new GeometryFactory()).createPoint(possible_agent_coord);
		if (my_zone.getPolygon().contains(pt)) {
			//we don't maintain a list of LinkAgent, so link_agent.move() is called elsewhere
		} else {
			//assert my_zone.getPolygon().contains(geom);
			pt = my_zone.getPolygon().getInteriorPoint();
		}
		return pt;
	}
	
	public void receive_equipment_or_drugs() {
		//be exposed to the blood of a friend
		IDU donor = network.getRandomPredecessor(this);
		assert(donor != this);
		if (donor != null) {
			donor.imm.give_exposure(imm);			
		}
	}
	
	public void receiveVaccineDose() {
		imm.receiveVaccineDose();
	}

	
	public void report_status() {
		Statistics.fire_status_change(AgentMessage.status, this, "", null);
	}
	/*
	 * called at initialization to plan the death of this agent 
	 * @param life_extension_days is usually negative, indicating the duration of elapsed drug career.
	 */
	private boolean schedule_end(double residual_burnin_days, double elapsed_career_days) {
		assert active;
        ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		if (my_end != null) {
			schedule.removeAction(my_end);
		} 
		//distributions for days until end
		double residual_life        = 0;
		double time_to_cessation    = 0;
		double residual_time_in_apk = 0;
		for(int trial=0; trial<100; ++trial) {
			//anticipate lifetime from birth, accounting for burnin period
			residual_life     = residual_burnin_days + life_time_distribution.nextDouble();
			residual_life    -= getAge() * 365.0;
			if(RandomHelper.nextDouble() < IDU.prob_cessation) {
                time_to_cessation = residual_burnin_days - elapsed_career_days + cessation_time_distribution.nextDouble();
				residual_time_in_apk = Math.min(time_to_cessation, residual_life);
			} else {
				residual_time_in_apk = residual_life;
			}
			if (residual_time_in_apk > residual_burnin_days) {
				break;
			}
		}
		if (residual_time_in_apk <= residual_burnin_days) {
			return false;
		}
		//System.out.println("A: " + getAge() + " R: " + residual_time_in_apk/365.0);
		//wishlist: replace the exponential mortality model - very high variance and lifetimes > 70
		//System.out.println("residual years:" + residual_time_in_apk/365.0);
		residual_time_in_apk += RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		ScheduleParameters death_sched_params = ScheduleParameters.createOneTime(residual_time_in_apk);
		my_end = schedule.schedule(death_sched_params, this, "deactivate");
		return true;
	}

	/*
	 * Self-check of the agent
	 * True iff the agent is ready to enter the simulation;  all the attributes are OK.
	 */
	public boolean self_test_ok(int verbose_level) {
		try {
			if (birth_date == null) {
				throw new Exception("birth_date");
				//return false;
			}
			//System.out.println("Career maturity: " + (getAge() - age_started));
			if(age_started < 0 || Double.isNaN(age_started) || age_started > getAge()) {  //no margin of tolerance here b/c need integrity
				//this errors happens mostly frequently because of crude randomization of the two ages.  If an IDU has age==20 and age_started==20, randomization might cause age_started < age.
				throw new Exception("age started");
				//return false;
			}
			if (characteristics == null) {
				throw new Exception("characteristics");
				//return false;
			}
			if (Double.isNaN(drug_in_degree)) {
				throw new Exception("drug_in_degree");
				//return false;
			}
			if (Double.isNaN(drug_out_degree)) {
				throw new Exception("drug_out_degree");
				//return false;
			}
			if (Double.isNaN(fraction_recept_sharing)) {
				throw new Exception("fraction_recept_sharing");
				//return false;
			}
			if (gender == null) {
				throw new Exception("gender");
				//return false;
			}
			if (imm == null) {
				throw new Exception("imm");
				//return false;
			}
			if (imm.getHcvState() == HCV_state.unknown || imm.getHcvState() == HCV_state.ABPOS) {
				throw new Exception("hcv_state");
				//return false;
			}
			if (Double.isNaN(injection_intensity) || injection_intensity > 20) {
				throw new Exception("injection_intensity");
				//return false;
			}
			if (last_exposure_date == null) {
				throw new Exception("last_exposure_date");
				//return false;
			}
			if(my_zone == null) {
				throw new Exception("zone");
				//return false;
			}
			if (name == null) {
				throw new Exception("name");
				//return false;
			}
			if (preliminary_zip == null) {
				throw new Exception("preliminary_zip");
				//return false;
			}
			if(getPreliminaryZip().length() != 5 || getPreliminaryZip().equalsIgnoreCase("0")) {
				throw new Exception("preliminary_zip");
				//return false;
			} 
			if (race == null) {
				throw new Exception("race");
				//return false;
			}
			if (profile == null) {
				throw new Exception("profile");
				//return false;
			}
		}
		catch (Exception e) {
			if (verbose_level > 0) {
				System.out.printf("Throwing away IDU (label: %s) b/c of problem in: %s\n", dblabel, e.getMessage());
			}
			return false;
		}
		return true;
	}
	static public void setStatics(Context c, Network<IDU> n, Geography<IDU> g) {
		context = c;
		network = n;
		geography = g;
	}
		
	@ScheduledMethod(start = 1, interval = 1)
	public void step() {
		if (! active) {
			return;
		}
		test_integrity();
		try{
			//movement
			if ((! RunEnvironment.getInstance().isBatch()) & (profile != Activity_profile.incarcerated)) {
				
				// TODO scatter the agent location within the zip one time at init since the agents don't really move.
//				geography.move(this, getLocation());
			}
			//infection (NOTE: ActivityProfile is now always in_community)
			if (profile != Activity_profile.incarcerated) {
				double num_sharing_episodes = Math.round(RandomHelper.getUniform().nextDouble()*injection_intensity*fraction_recept_sharing);
				if (profile == Activity_profile.in_rehab) {
					num_sharing_episodes = num_sharing_episodes/rehab_risk_factor;
				}
				for (int episode=0; episode<num_sharing_episodes; ++episode) {
					receive_equipment_or_drugs();
				}
			} else {
				//essentially no risk
			}
		} catch (Exception e) {
			System.out.println(e.toString());
		}
	}

	public String getSyringeSource_() {
		return hr_enrollment.toString();
	}
	
	@Override
	public HarmReduction getSyringe_source() {
		return hr_enrollment;
	}
	@Override
	public void setSyringe_source(HarmReduction val) {
		hr_enrollment = val;
	}

	public void startTreatment() {
		boolean adherent = RandomHelper.nextDouble() > treatment_nonadherence;
		imm.startTreatment(adherent);
	}
	@ScheduledMethod(start = 1)
	public void test_integrity() {
		if (active) {
			assert my_zone != null;
		}
		//self_test_ok(0);
	}

	public static String toString_header() {
		return "Age,Gender,Race,Zip,Syringe_source,HCV,HCV_friend_preval,Age_Started,Drug_in_degree,Drug_out_degree,Num Buddies,Daily_injection_intensity,Fraction_recept_sharing,DBLabel";
	}
	
	@Override
	public String toString() {
		return ""+getAge()+","+getGender()+","+getRace()+","+getZip()+","+getSyringe_source()+","+getHcvState()+","+getHcvNeighborPrevalence()
				+","+getAgeStarted()
				+","+getDrugReceptDegree()+","+getDrugGivingDegree()+","+getNumBuddies()+","+getInjectionIntensity()+","+getFractionReceptSharing()+","+getDatabaseLabel();
	}

	/*
	 * try to establish an arc this->obj
	 *    will also attempt to establish a reciprocal tie
	 */
	public boolean try_connect(IDU obj) {
		if(! acceptable_out_connection(obj) || ! obj.acceptable_in_connection(this)) {
			return false;
		}
		if (demographic_distance(obj) * homophily > RandomHelper.nextDouble()) {
			return false;
		}
		network.addEdge(this, (IDU) obj);
		double out_tie_end_time = RunEnvironment.getInstance().getCurrentSchedule().getTickCount() 
				                 + tie_endurance_distribution.nextDouble();		
		ScheduleParameters out_tie_end_params = ScheduleParameters.createOneTime(out_tie_end_time);
        ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		schedule.schedule(out_tie_end_params, this, "end_relationship", obj);
		
		if(! obj.acceptable_out_connection(this) || ! this.acceptable_in_connection(obj)) {
			return true;  //one link added, but no reciprocal link
		}
		network.addEdge((IDU) obj, this);
		schedule.schedule(out_tie_end_params, obj, "end_relationship", this); //ends at the same time
		return true; //reciprocal link
	}
}
