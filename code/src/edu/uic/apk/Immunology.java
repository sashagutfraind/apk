/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cern.jet.random.Normal;
import edu.uic.apkSynth.Gender;
import edu.uic.apkSynth.HCV_state;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.essentials.RepastEssentials;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;

/*
 * Controls all behavior related to health, including from the moment of exposure, progression of HCV infection.
 * One instance is associated with every instance of IDU.
 * The IDU only 
 */
public class Immunology implements java.io.Serializable {
	private static final long serialVersionUID = 3121231231231211L;
	private static final double contact_risk  			= 1.0;   //future use for various modes of contact
	private static final double acute_boost 			= 1.0;   //risk of contact during acute infection

	private final double VACCINE_ONSET_OF_IMMUNITY_DAY 	= 14;
	private final double VACCINE_MAX_INDUCED_IMMUNITY_DAY = 28;
	public static enum TRIAL_ARM {placebo, study, noarm}; //only changes once

	public static final double ACUTE_NAIVE_DAYS_MIN = 77;
	public static final double ACUTE_NAIVE_DAYS_MAX = 127;
	public static final double ACUTE_RECHALLENGED_DAYS_MIN = 8;
	public static final double ACUTE_RECHALLENGED_DAYS_MAX = 48;
	public static final double BECOME_INFECTIOUS_DAYS_MIN = 2;
	public static final double BECOME_INFECTIOUS_DAYS_MAX = 4;

	private static double mean_days_acute_naive          	= Double.NaN; //approx. 102; 
	private static double mean_days_acute_rechallenged   	= Double.NaN; //approx. 28; 
	private static double mean_days_naive_to_infectious  	= Double.NaN; //approx. 3; 
	private static double mean_days_residual_hcv_infectivity = Double.NaN; //for treatment
	private static double prob_self_limiting_female 	   	= Double.NaN; //approx. 0.346; 
	private static double prob_self_limiting_male   		= Double.NaN; //approx. 0.121; 
	private static double prob_clearing     				= Double.NaN; //approx. 0.85; //reflects clearing the infection in experienced individuals
	private static double transmissibility   				= Double.NaN; //approx. 0.006;
	private static double treatment_duration   			= Double.NaN; //
	private static boolean treatment_repeatable 			= Boolean.FALSE;
	private static double treatment_svr   				= Double.NaN; //
	private static double treatment_susceptibility       = Double.NaN; //

	private static enum VACCINE_SCHEDULE_ENUM {D1E00,
											  D1E50, D1E60, D1E70, D1E80,
											  D2E50, D2E60, D2E70, D2E80, D2E85,
											  D3E50, D3E60, D3E70, D3E80, NONE}; //always upper case
	private static VACCINE_SCHEDULE_ENUM vaccine_schedule = VACCINE_SCHEDULE_ENUM.NONE;
											  
	//private fields
	private boolean past_cured     = false;
	private boolean past_recovered = false;
	private boolean past_vaccinated = false;
	
	private transient IDU agent; //back reference to the IDU
	private HCV_state hcv_state = HCV_state.susceptible;
	
	//treatment
	//private boolean in_treatment_viral_suppression = false; //true as soon as titers drop, and false when leave treatment (true even if not adherent)
	private boolean in_treatment = false; //true as soon as initiate treatment, and false when leave
	private Double treatment_start_date = null; //null indicates never treated
	
	//vaccine
	private TRIAL_ARM   vaccine_trial_arm 	= TRIAL_ARM.noarm; 
	private VACCINE_STAGE vaccine_stage 	= VACCINE_STAGE.notenrolled;
	private double vaccine_first_dose_received_day = Double.NaN; //when the first dose was received
	private double vaccine_latest_dose_received_day = Double.NaN; //needed to calculate the efficacy

	//register all changes in state here
	//-- helps cancel pre-scheduled disease progression
	//-- helps clean up unnecessary actions if the agent dies, or receives treatment
	private ArrayList<ISchedulableAction> next_immunology_actions      = new ArrayList<ISchedulableAction>();  
	
	public Immunology(IDU agent_) {
		this.agent = agent_;
	}
	/*
	 * copy the immunology.  notice that this does not copy the disease progression events
	 */
	public Immunology(HCV_state alter_state, IDU agent_) {
		this.agent		= agent_;
		this.hcv_state	= alter_state;
	}

	/*
	 * called when leaving the simulation
	 */
	protected void deactivate() {
		//List<ISchedulableAction> actions = RunEnvironment.getInstance().getCurrentSchedule().schedule(this);
		//TODO: see if actions gets all the same actions as next_immunology_actions

		purge_actions();
		//we keep track of those actions as a bug trap, to remove them if the agent dies.  the array is optional.
		//TODO: we currently do not clean them when the action is executed, and hence the array might actually hold spent actions.
	}

	private static double generate_truncated_exponential(String var_name) {
		double mean_parameter = 0;
		double min_bound = 0;
		double max_bound = 0;
		if(var_name == "acute_naive") {
			mean_parameter = mean_days_acute_naive;
			min_bound = ACUTE_NAIVE_DAYS_MIN;
			max_bound = ACUTE_NAIVE_DAYS_MAX;
		} else if (var_name == "acute_rechallenged") {
			mean_parameter = mean_days_acute_rechallenged;
			min_bound = ACUTE_RECHALLENGED_DAYS_MIN;
			max_bound = ACUTE_RECHALLENGED_DAYS_MAX;
		} else if (var_name == "become_infectious") {
			mean_parameter = mean_days_naive_to_infectious;
			min_bound = BECOME_INFECTIOUS_DAYS_MIN;
			max_bound = BECOME_INFECTIOUS_DAYS_MAX;
		} else {
			System.err.println("Distribution for " + var_name + " not recognized.  Exiting");
			System.exit(-1);	
		}
		
		double days = 0;
		for(int i=0; i<1000; ++i) {
			days = RandomHelper.createExponential(1./mean_parameter).nextDouble();
			if ((days >= min_bound) && (days <= max_bound)) {
				//System.out.println("Generated duration for " + var_name + ": "+ days);
				return days;
			}
			//System.out.println("REJECTED duration for " + var_name + ": " + days);
		}
		System.err.println("Inserting default value for " + var_name + ". Is the corresponding parameter out of bounds (hardcoded)?");
		return (min_bound + max_bound)/2;
	}
	
	/*
	 * this IDU exposes a partner.  this might lead to an infection.
	 * the method simulates a contact
	 * returns true if new infection was established in partner
	 */
	public boolean give_exposure(Immunology partner) {
		assert hcv_state != HCV_state.unknown;
		//if (agent.getProfile() == Activity_profile.incarcerated) {
		//	return false;
		//}
		Statistics.fire_status_change(AgentMessage.exposed, partner.agent, "by agent "+agent.hashCode(), null);
		if (! isHcvRNA()) {
			return false;
		}
		if (isAcute() ) {
			if (RandomHelper.nextDouble() > contact_risk*transmissibility*acute_boost) {
				Statistics.fire_status_change(AgentMessage.exposed, agent, "transmission failed", null);
				return false;
			}
		} else {
			if (RandomHelper.nextDouble() > contact_risk*transmissibility) {
				Statistics.fire_status_change(AgentMessage.exposed, agent, "transmission failed", null);
				return false;
			}
		}
		boolean established_new_infection = partner.receive_infectious_dose();
		if (established_new_infection) {
			partner.agent.setLastExposureDate(); //important: must follow Statistics.fire()
		}
		return established_new_infection;
	}
	
	public boolean isAcute() {
		return hcv_state == HCV_state.exposed || hcv_state == HCV_state.infectiousacute;
	}
	public boolean isChronic() {
		return hcv_state == HCV_state.chronic;
	}
	public boolean isCured() {
		return hcv_state == HCV_state.cured;
	}
	public boolean isExposed() {
		return hcv_state == HCV_state.exposed;
	}
	public boolean isHcvABpos() { //presence of antigens
		//vaccine_first_dose_received_day might be NaN
		boolean induced_antibodies = (past_vaccinated 
										&& (vaccine_trial_arm == TRIAL_ARM.study)
				                        && (RepastEssentials.GetTickCount() - vaccine_first_dose_received_day > VACCINE_ONSET_OF_IMMUNITY_DAY));
		return hcv_state == HCV_state.infectiousacute //wishlist: perhaps too early
				|| hcv_state == HCV_state.recovered
				|| hcv_state == HCV_state.chronic 
				|| past_recovered
				|| hcv_state == HCV_state.cured
				|| treatment_start_date != null
				|| induced_antibodies;
	}
	public boolean isHcvRNA() {
		return (hcv_state == HCV_state.exposed 
				|| hcv_state == HCV_state.infectiousacute 
				|| hcv_state == HCV_state.chronic) 
			&& (! isIn_treatment_viral_suppression());
	}
	public boolean isInfectious() {
		return (hcv_state == HCV_state.infectiousacute 
				|| hcv_state == HCV_state.chronic) 
			&& (! isIn_treatment_viral_suppression());
	}
	public boolean isInTreatment() {
		return in_treatment;
	}
	//true is the RNA is not detectable due to treatment
	private boolean isIn_treatment_viral_suppression() {
		if(! in_treatment) {
			return false;
		}
		return (treatment_start_date + Immunology.mean_days_residual_hcv_infectivity) < RepastEssentials.GetTickCount();
	}
	public boolean isNaive() {
		return hcv_state == HCV_state.susceptible;
	}
	public boolean isResistant() {
		return hcv_state == HCV_state.recovered;
	}
	public boolean isPostTreatment() { //i.e. completed a course of treatment
		return (! in_treatment) & (treatment_start_date != null);
	}
	public boolean isTreatable() {
		return (! in_treatment) && isHcvRNA() && (treatment_repeatable || (! isPostTreatment()));
		//note: in the future we will have a parameter "treatment_repeatable" to allow multiple courses
		//TODO: it's not converting in the batch params
	}
	
	public TRIAL_ARM getCurrent_trial_arm() {
		return vaccine_trial_arm;
	}
	//called to set the arm at the start of a trial
	public void setCurrent_trial_arm(TRIAL_ARM new_trial_arm) {
		assert vaccine_trial_arm == TRIAL_ARM.noarm; //only set once.  never changes even if trial is complete
		vaccine_trial_arm = new_trial_arm;
	}	

	
	public HCV_state getHcvState() {
		return hcv_state;
	}	
	/*
	 * for initializing agents
	 * this function overrides the normal course of an infection
	 * it should not be used for natural exposures
	 * 
	 * censored_acute = in the middle of an acute infection
	 */
	public void setHcvInitState(HCV_state state, int logging) {
		assert state != HCV_state.ABPOS; //used only in the DrugUser container
		hcv_state = state;
		switch (state) {
			case susceptible:
				hcv_state = HCV_state.susceptible;
				if (logging > 0) {
					Statistics.fire_status_change(AgentMessage.info, agent, "new_hcv_state="+state.toString(), null);
				}
				break;
//			case cured:
//				assert false; //should not be called from here
//				treatment_start_date = RepastEssentials.GetTickCount();
//				hcv_state = HCV_state.cured;
//				if (logging > 0) {
//					Statistics.fire_status_change(AgentMessage.started_treatment, this.agent, "", null);
//				}
//				break;
//			case exposed: 
//				assert false;//should not be called
//				receive_infectious_dose();
//				if (logging > 0) {
//					Statistics.fire_status_change(AgentMessage.exposed, this.agent, "", null);
//					this.agent.setLastExposureDate(); //must follow fire, b/c it uses the signature
//				}
//				break;
			case infectiousacute:
				double acute_end_time = (int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
				acute_end_time += generate_truncated_exponential("acute_naive");	
				ScheduleParameters acute_end_params = ScheduleParameters.createOneTime(acute_end_time);
				ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
				next_immunology_actions.add(schedule.schedule(acute_end_params, this, "leave_acute"));
				if (logging > 0) {
					Statistics.fire_status_change(AgentMessage.infectious, this.agent, "", null);
					this.agent.setLastExposureDate(); //must follow fire, b/c it uses the signature
				}
				break;
			case chronic:
				this.agent.setLastExposureDate();
				if (logging > 0) {
					Statistics.fire_status_change(AgentMessage.chronic, this.agent, "", null);
					this.agent.setLastExposureDate(); //must follow fire, b/c it uses the signature
				}
				break;
			case recovered:
				this.agent.setLastExposureDate();
				if (logging > 0) {
					Statistics.fire_status_change(AgentMessage.recovered, this.agent, "", null);
					this.agent.setLastExposureDate(); //must follow fire, b/c it uses the signature
				}
				break;
			default: 
				assert false;
				if (logging > 0) {
					Statistics.fire_status_change(AgentMessage.info, agent, "new_hcv_state="+state.toString(), null);
				}
		}
	}
	public static void setStatics(Parameters params) {
		//contact_risk 				  = (Double) params.getValue("contact_risk");
		mean_days_acute_naive         = (Double) params.getValue("mean_days_acute_naive");
		mean_days_acute_rechallenged  = (Double) params.getValue("mean_days_acute_rechallenged");
		mean_days_naive_to_infectious = (Double) params.getValue("mean_days_naive_to_infectious");
		mean_days_residual_hcv_infectivity = (Double) params.getValue("mean_days_residual_hcv_infectivity");
		prob_self_limiting_female 	  = (Double) params.getValue("prob_self_limiting_female");
		prob_self_limiting_male 	  = (Double) params.getValue("prob_self_limiting_male");
		prob_clearing 				  = (Double) params.getValue("prob_clearing");
		transmissibility 			  = (Double) params.getValue("transmissibility");		
		treatment_duration 			  = (Double) params.getValue("treatment_duration");		
		treatment_repeatable          = ((Integer) params.getValue("treatment_repeatable")) == 0? false: true; //repast doesn't handle boolean params well
		//test this
		treatment_svr 			  	  = (Double) params.getValue("treatment_svr");		
		treatment_susceptibility      = (Double) params.getValue("treatment_susceptibility");		
		//transmissibility 			  = (Double) params.getValue("transmissibility");		
		
		String vaccine_schedule_string = (String) params.getValue("vaccine_schedule");
		try{
			//this should trigger an an Exception if the protocol is unknown
			vaccine_schedule = vaccine_schedule.valueOf(vaccine_schedule_string.toUpperCase(Locale.ENGLISH));		
		} catch (Exception e) {
			System.err.println("Invalid vaccine schedule.  If you intended no vaccine trial, use NONE");
			vaccine_schedule = vaccine_schedule.NONE;
		}
		
	}
	
	/*
	 * return a double array indicating the efficacy after 0,1,2,3 doses (always return 3, even if the vaccine is only given as 1 or 2 doses)
	 * wishlist: cache the results, if it gets too slow
	 */
	double[] getVaccineEffDoses() {
		double vaccine_max_ve = Double.NaN;
		int vaccine_doses     = 0;
		
		assert(! past_recovered);  //this is possible in some trials, but we have no formula to calculate prob of clearance for this case
		
		switch (vaccine_schedule) {
			case D1E00:
			case D1E50:
			case D1E60:
			case D1E70:
			case D1E80:
				vaccine_doses = 1;
				break;
			case D2E50:
			case D2E60:
			case D2E70:
			case D2E80:
			case D2E85:
				vaccine_doses = 2;
				break;
			case D3E50:
			case D3E60:
			case D3E70:
			case D3E80:
				vaccine_doses = 3;
				break;
		case NONE:
			return null;
		default:
			break;
		}
		
		double baseline_eff = (agent.getGender() == Gender.Male? prob_self_limiting_male : prob_self_limiting_female);
		switch (vaccine_schedule) {
			case D1E00:
				vaccine_max_ve = baseline_eff;
				break;
			case D1E50:
			case D2E50:
			case D3E50:
				vaccine_max_ve = 0.5;
				break;
			case D1E60:
			case D2E60:
			case D3E60:
				vaccine_max_ve = 0.6;
				break;
			case D1E70:
			case D2E70:
			case D3E70:
				vaccine_max_ve = 0.7;
				break;
			case D1E80:
			case D2E80:
			case D3E80:
				vaccine_max_ve = 0.8;
				break;
			case D2E85:
				vaccine_max_ve = 0.85;
				break;
			default:
				System.err.println("Vaccine schedule " + vaccine_schedule + " not implemented.  Exiting");
				System.exit(-1);
			
		}
		if(vaccine_max_ve < baseline_eff) {
			System.err.println("Vaccine schedule " + vaccine_schedule + " has lower efficiacy than baseline");
			System.exit(-1);
		}
		double rise_per_dose = (vaccine_max_ve - baseline_eff) / vaccine_doses;
		
		double[] vaccine_eff = new double[4];
		switch(vaccine_doses) {
		case 1:
			vaccine_eff[0] = baseline_eff;
			vaccine_eff[1] = baseline_eff + rise_per_dose;
			vaccine_eff[2] = vaccine_eff[1];
			vaccine_eff[3] = vaccine_eff[1];
			break;
		case 2:
			vaccine_eff[0] = baseline_eff;
			vaccine_eff[1] = baseline_eff + rise_per_dose;
			vaccine_eff[2] = baseline_eff + rise_per_dose + rise_per_dose;
			vaccine_eff[3] = vaccine_eff[2];
			break;
		case 3:
			vaccine_eff[0] = baseline_eff;
			vaccine_eff[1] = baseline_eff + rise_per_dose;
			vaccine_eff[2] = baseline_eff + rise_per_dose + rise_per_dose;
			vaccine_eff[3] = baseline_eff + rise_per_dose + rise_per_dose + rise_per_dose;
			break;
		default:
			System.err.println("Vaccine schedule " + vaccine_schedule + " cannot have more than 3 doses");
			System.exit(-1);
		}

		return vaccine_eff;
	}
	

	public Double getTreatmentStartDate() {
		return treatment_start_date;
	}
	
	public VACCINE_STAGE getVaccine_stage() {
		return vaccine_stage;
	}
	public void setVaccine_stage(VACCINE_STAGE new_stage) {
		if (new_stage == VACCINE_STAGE.received_dose1 || 
				new_stage == VACCINE_STAGE.received_dose2 ||
				new_stage == VACCINE_STAGE.received_dose3) {
			//see receiveVaccineDose() instead
			assert false;
			return;
		}
		vaccine_stage = new_stage;
	}
	public boolean leave_acute() {
		//returns true iff self limiting
		if (is_acute_resolved_successfully()) {
			hcv_state = HCV_state.recovered;	
			Statistics.fire_status_change(AgentMessage.recovered, agent, "", null);
			return true;
		} else {
			hcv_state = HCV_state.chronic;	
			Statistics.fire_status_change(AgentMessage.chronic, agent, "", null);
			return false;
		}
	}

	public void leave_exposed() {
		hcv_state = HCV_state.infectiousacute;		
		Statistics.fire_status_change(AgentMessage.infectious, agent, "", null);
	}
	
	public void leave_treatment(boolean treatment_succeeded) {
		in_treatment = false;
		if (treatment_succeeded) {
			hcv_state = HCV_state.cured; 
			Statistics.fire_status_change(AgentMessage.cured, agent, "", null); 
		} else {
			Statistics.fire_status_change(AgentMessage.failed_treatment, agent, "", null); 
			hcv_state = HCV_state.chronic; //even if entered as acute.  ignore the case where was about to self-limit 
		}
	}

	/*
	 * called whenever the disease progression of the agent is altered
	 * - also, at death, prevents actions on this agent
	 * wishlist: consider a more fine-grained "purge" operation (all vs. all_infection) since this might have unexpected effects
	 */
	private void purge_actions() {
		//prevent any accidental switch to chronic during treatment
		ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		for(ISchedulableAction act : next_immunology_actions) {
			//wishlist: bug risk: would this remove actions not only for this agent?
			schedule.removeAction(act);
		}
		next_immunology_actions.clear(); 
		in_treatment = false;
	}
	/*
	 * start a NATURAL infection via exposure.  
	 * 1. the calling method is responsible for announcing the exposure, and updating the time of last exposure
	 * 2. if one is recovered, it's possible to be "infected" a new
	 * 3. if one has RNA, then no new infection would be established.
	 * 
	 * return true iff new infection has been started
	 */
	protected boolean receive_infectious_dose() {
		if(isHcvRNA() || isInTreatment()) {
			return false;
		}
		purge_actions();
		
		//note: this long memory changes the behavior compared to original APK, even in the default (no treatment) case.
		past_recovered = past_recovered | (hcv_state == HCV_state.recovered);
		past_cured     = past_cured     | (hcv_state == HCV_state.cured);
		
		hcv_state = HCV_state.exposed;
		Statistics.fire_status_change(AgentMessage.infected, agent, "", null);
		
		double time_now = (int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		
		ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		double exposed_end_time = time_now +  generate_truncated_exponential("become_infectious");
		ScheduleParameters exposed_end_params = ScheduleParameters.createOneTime(exposed_end_time);
		next_immunology_actions.add(schedule.schedule(exposed_end_params, this, "leave_exposed"));

		double acute_end_time;
		if (! past_recovered) { //NOTE: we are implicitly assuming that vaccinees are naive
			acute_end_time = time_now + generate_truncated_exponential("acute_naive");	
		} else {
			acute_end_time = time_now + generate_truncated_exponential("acute_rechallenged");
		}					
		acute_end_time = Math.max(acute_end_time, exposed_end_time + 0.1); //ensure correct sequencing
		//wishlist: possibly modify duration with vaccine
		ScheduleParameters acute_end_params = ScheduleParameters.createOneTime(acute_end_time);
		next_immunology_actions.add(schedule.schedule(acute_end_params, this, "leave_acute"));
		
		return true;
	}
	
	/*
	 * Update the immune state after receiving the dose
	 * Immunity increases, not necesarily immediately, after the doses
	 * Note: it's possible for the state to change to chronic before the doses are complete
	 */
	public void receiveVaccineDose() {
		assert this.vaccine_trial_arm != TRIAL_ARM.noarm; //must be set 
//		if(hcv_state == HCV_state.recovered) {
//			System.out.println("Note: recruited a recovered individual");
//		}
		
		switch(vaccine_stage) {
			case notenrolled:
				vaccine_first_dose_received_day = RepastEssentials.GetTickCount();
				vaccine_latest_dose_received_day = vaccine_first_dose_received_day;
				if(isHcvRNA()) { 
					System.err.println("Infected individuals shouldn't be enrolled in vaccine trials");  
					//note: this is an error on initial dose;  
					//however, for 2nd and 3rd, the receiving PWID might be HCV-RNA+ due to infection after the initial dose
				}
				vaccine_stage = VACCINE_STAGE.received_dose1;
				if(hcv_state == HCV_state.susceptible) {
					hcv_state = HCV_state.vaccinated;
				}
				past_vaccinated = true;
				break;
			case received_dose1:
				vaccine_latest_dose_received_day = RepastEssentials.GetTickCount();
				vaccine_stage = VACCINE_STAGE.received_dose2;
				break;
			case received_dose2:
				vaccine_latest_dose_received_day = RepastEssentials.GetTickCount();
				vaccine_stage = VACCINE_STAGE.received_dose3;
				break;
			default:
				System.err.println("Not eligible to receive dose in IDU " + this.agent.getSimID());
				break;
		}
	}
	
	/*
	 * called to initiate treatment
	 */
	public void startTreatment(boolean adherent) {
		if(! isTreatable()) {
			System.out.println("Agent cannot be treated [doubly recruited?] ..." + agent.toString());
			return;
		}
		//prevent any accidental switch to chronic during treatment
		purge_actions(); //here - to purge any residual actions, such as switch to chronic
		treatment_start_date = RepastEssentials.GetTickCount();
		in_treatment = true;
		Statistics.fire_status_change(AgentMessage.started_treatment, this.agent, "", null);

		ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
//		Normal residual_infectivity_rng_stream = RandomHelper.createNormal(Immunology.mean_days_residual_hcv_infectivity, 1);
//		//end infectivity, then end treatment (see leave_infectious)
//		double infectious_end_time = RepastEssentials.GetTickCount()
//			   		  + residual_infectivity_rng_stream.nextDouble();
//		ScheduleParameters leave_infectious_titers_time = ScheduleParameters.createOneTime(infectious_end_time);
//		next_immunology_actions.add(schedule.schedule(leave_infectious_titers_time, this, "leave_infectious_titers"));

		Normal treatment_end_rng_stream = RandomHelper.createNormal(Immunology.treatment_duration, 1);
		double treatment_end_time = RepastEssentials.GetTickCount()
		   		  + treatment_end_rng_stream.nextDouble();
		ScheduleParameters leave_treatment_time = ScheduleParameters.createOneTime(treatment_end_time);
		boolean treatment_succeeds = (RandomHelper.nextDouble() < treatment_svr) && adherent;
		next_immunology_actions.add(schedule.schedule(leave_treatment_time , this, "leave_treatment", treatment_succeeds));

	}
	
	/*
	 * Test if the immune system successfully overcomes an acute infection
	 */
	public boolean is_acute_resolved_successfully() {
		assert hcv_state == HCV_state.infectiousacute;

		if (vaccine_trial_arm == TRIAL_ARM.noarm) {
			if (past_cured) {
				//because infection disrupts the immune system and wipes out any previously learned response
				return (treatment_susceptibility < RandomHelper.nextDouble());
			}
			//infection might self-limit due to either naive response, or with history of recovery
			return probability_self_limiting() > RandomHelper.nextDouble();
		} else {
			return does_vaccinee_recover(); //includes placebo arm
		}
	}
	
	/*
	 * returns true if overcomes the infection due to vaccine action OR any previously learned response and naive response
	 */
	public boolean does_vaccinee_recover() {
		assert hcv_state == HCV_state.infectiousacute;
		if (vaccine_trial_arm == Immunology.TRIAL_ARM.placebo) {
			return probability_self_limiting() > RandomHelper.nextDouble();
		}
		double days_since_last_dose = (RepastEssentials.GetTickCount() - vaccine_latest_dose_received_day);
		double immunity_fractional_boost = 0; //fraction of the most recent dose contributing to the immunity
		if (days_since_last_dose > VACCINE_ONSET_OF_IMMUNITY_DAY && days_since_last_dose < VACCINE_MAX_INDUCED_IMMUNITY_DAY)  {
			//between doses
			immunity_fractional_boost = (days_since_last_dose-VACCINE_ONSET_OF_IMMUNITY_DAY)/
									(VACCINE_MAX_INDUCED_IMMUNITY_DAY-VACCINE_ONSET_OF_IMMUNITY_DAY);
			
			
		} else {
			//waiting for the next one, or finished the dose series
			immunity_fractional_boost = 1.0;
		}
		
		int dose_idx = vaccine_stage == VACCINE_STAGE.received_dose1? 1 : (vaccine_stage == VACCINE_STAGE.received_dose2? 2: 3); 
		double[] VE = getVaccineEffDoses(); 
		double current_ve = VE[dose_idx-1] + (VE[dose_idx]-VE[dose_idx-1])*immunity_fractional_boost;
		
		boolean vaccine_success = Math.max(probability_self_limiting(), current_ve) > RandomHelper.nextDouble();
		return vaccine_success;
	}
	
	public double probability_self_limiting() {
		if (past_recovered) {
			return prob_clearing; 
		} else {
			return (agent.getGender() == Gender.Male? prob_self_limiting_male : prob_self_limiting_female);
		}
	}
	
}
