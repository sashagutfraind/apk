/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

	private static String vaccine_schedule = ""; //wishlist: make enum.  this should indicate the efficacy
	private static Map<String,double[]> vaccine_eff;	
	
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
	private double vaccine_dose_received_day = Double.NaN; //needed to calculate the efficacy

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
		return (hcv_state != HCV_state.susceptible) 
				//wishlist: make a little less crude! 
				//for vaccinees, antibodies don't emerge until a little bit later
				//for natural infection, it also takes about a month
				|| (hcv_state == HCV_state.ABPOS)  
				|| past_cured || past_vaccinated || past_recovered;
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
				acute_end_time += RandomHelper.createExponential(1./mean_days_acute_naive).nextDouble();	
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
		vaccine_schedule              = (String) params.getValue("vaccine_schedule");	//wishlist: make enum
		
		vaccine_eff = new HashMap<String,double[]> ();	

		//Vaccine Efficacy: at index i, get the VE from dose i.
		//MAKE SURE: values are monotonically increasing even if 2nd or 3rd dose are not in the protocol
		final double[] VE_D1 =  {0, 0.80, 0.80, 0.80};
		final double[] VE_D2a = {0, 0.20, 0.60, 0.60};
		final double[] VE_D2b = {0, 0.40, 0.80, 0.80};
		final double[] VE_D3a = {0, 0.01, 0.20, 0.80};
		final double[] VE_D3b = {0, 0.05, 0.50, 0.80};
		final double[] VE_X1a = {0, 1.00, 1.00, 1.00}; //testing
		final double[] VE_X3a = {0, 0.30, 0.60, 1.00}; //testing
		vaccine_eff.put("D1", VE_D1);
		vaccine_eff.put("D2a", VE_D2a);
		vaccine_eff.put("D2b", VE_D2b);
		vaccine_eff.put("D3a", VE_D3a);
		vaccine_eff.put("D3b", VE_D3b);
		vaccine_eff.put("X1a", VE_X1a);
		vaccine_eff.put("X3a", VE_X3a);
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
		if (successful_acute_response()) {
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
		double exposed_end_time = time_now  
		   		  + RandomHelper.createExponential(1./mean_days_naive_to_infectious).nextDouble();
		ScheduleParameters exposed_end_params = ScheduleParameters.createOneTime(exposed_end_time);
		next_immunology_actions.add(schedule.schedule(exposed_end_params, this, "leave_exposed"));

		double acute_end_time;
		if (! past_recovered) {
			acute_end_time = time_now + RandomHelper.createExponential(1./mean_days_acute_naive).nextDouble();	
		} else {
			acute_end_time = time_now + RandomHelper.createExponential(1./mean_days_acute_rechallenged).nextDouble();
		}					
		acute_end_time = Math.max(acute_end_time, exposed_end_time + 0.1); //ensure correct sequencing
		//wishlist: possibly modify duration with vaccine
		ScheduleParameters acute_end_params = ScheduleParameters.createOneTime(acute_end_time);
		next_immunology_actions.add(schedule.schedule(acute_end_params, this, "leave_acute"));
		
		return true;
	}
	
	/*
	 * Received doses
	 * Note: it's possible for the state to change to chronic before the doses are complete
	 */
	public void receiveVaccineDose() {
		assert this.vaccine_trial_arm != TRIAL_ARM.noarm; //must be set 
		if(hcv_state == HCV_state.recovered) {
			System.out.println("Note: recruited a recovered individual");
		}
		//note: the person might be HCV-RNA+ due to infection after the initial dose

		switch(vaccine_stage) {
			case notenrolled:
				vaccine_dose_received_day = RepastEssentials.GetTickCount();
				if(isHcvRNA()) { 
					System.err.println("Infected individuals shouldn't be enrolled in vaccine trials");  
					System.exit(1);
				}
				vaccine_stage = VACCINE_STAGE.received_dose1;
				if(hcv_state == HCV_state.susceptible) {
					hcv_state = HCV_state.vaccinated;
				}
				past_vaccinated = true;
				break;
			case received_dose1:
				vaccine_dose_received_day = RepastEssentials.GetTickCount();
				vaccine_stage = VACCINE_STAGE.received_dose2;
				break;
			case received_dose2:
				vaccine_dose_received_day = RepastEssentials.GetTickCount();
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
	public boolean successful_acute_response() {
		assert hcv_state == HCV_state.infectiousacute;
		//case 1: modified immune system, from strongest to weakest immune responses
		if (past_recovered) {
			//strongest response
			return prob_clearing > RandomHelper.nextDouble();
		} 
		if (vaccine_stage != VACCINE_STAGE.notenrolled) {
			return vaccine_successful();
		}
		if (past_cured) {
			//previous experience is defining of the immune response - cannot be modified by vaccine 
			return (treatment_susceptibility < RandomHelper.nextDouble());
		}
		//case 2: unmodified immune system
		
		//infection might self-limit
		double prob_self_limiting = agent.getGender() == Gender.Male? prob_self_limiting_male : prob_self_limiting_female; 
		
		return prob_self_limiting > RandomHelper.nextDouble();
	}
		
	public boolean vaccine_successful() {		
		assert hcv_state == HCV_state.infectiousacute;
		if (vaccine_trial_arm == Immunology.TRIAL_ARM.placebo) {
			return false;
		}
		double days_since_last_dose = (RepastEssentials.GetTickCount() - this.vaccine_dose_received_day);
		double immunity_fractional_boost = 0; //since the last dose
		if (days_since_last_dose > VACCINE_ONSET_OF_IMMUNITY_DAY && days_since_last_dose < VACCINE_MAX_INDUCED_IMMUNITY_DAY)  {
			immunity_fractional_boost = (days_since_last_dose-VACCINE_ONSET_OF_IMMUNITY_DAY)/(VACCINE_MAX_INDUCED_IMMUNITY_DAY-VACCINE_ONSET_OF_IMMUNITY_DAY);
		} else {
			immunity_fractional_boost = 1.0;
		}
		
		int dose_idx = vaccine_stage == VACCINE_STAGE.received_dose1? 1 : (vaccine_stage == VACCINE_STAGE.received_dose2? 2: 3); 
		//this is a little hack: immunity at 3 or follow-up stage is given by the last value.  this is a little unsafe since the user might give 0, 0.8, -1, -1.  
		//TODO: make safer;  correctly calculate the dose
		if(! vaccine_eff.containsKey(vaccine_schedule)) {
			System.err.println("Unknown vaccine schedule" + vaccine_schedule);
			return false;
		}
		double[] VE = vaccine_eff.get(vaccine_schedule); 
		double current_ve = VE[dose_idx-1] + (VE[dose_idx]-VE[dose_idx-1])*immunity_fractional_boost;
		
		boolean vaccine_success = current_ve > RandomHelper.nextDouble();		
		return vaccine_success;
	}
	
	
}
