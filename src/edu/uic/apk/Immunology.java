/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import java.util.ArrayList;

import edu.uic.apkSynth.Gender;
import edu.uic.apkSynth.HCV_state;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
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
	private final static double acute_boost = 1;

	private static double mean_days_acute_naive          	= Double.NaN; //approx. 102; 
	private static double mean_days_acute_rechallenged   	= Double.NaN; //approx. 28; 
	private static double mean_days_naive_to_infectious  	= Double.NaN; //approx. 3; 
	private static double prob_self_limiting_female 	   	= Double.NaN; //approx. 0.346; 
	private static double prob_self_limiting_male   		= Double.NaN; //approx. 0.121; 
	private static double prob_clearing     				= Double.NaN; //approx. 0.85; //reflects clearing the infection in experienced individuals
	private static double transmissibility   				= Double.NaN; //approx. 0.006;
	
	//private fields
	private transient IDU agent; //back reference to the IDU
	private HCV_state hcv_state = HCV_state.susceptible;
	private ArrayList<ISchedulableAction> next_immunology_actions      = new ArrayList<ISchedulableAction>();  //helps clean up unnecessary actions if the agent dies

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
		ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		for(ISchedulableAction act : next_immunology_actions) {
			schedule.removeAction(act);
		}
		next_immunology_actions.clear();
		//we keep track of those actions as a bug trap, to remove them if the agent dies.  the array is optional.
		//we currently do not clean them when the action is executed, and hence the array might actually hold spent actions.
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
	public boolean isExposed() {
		return hcv_state == HCV_state.exposed;
	}
	public boolean isHcvABpos() { //presence of antigens
		return (hcv_state != HCV_state.susceptible) || (hcv_state == HCV_state.ABPOS);
	}
	public boolean isHcvRNA() {
		return hcv_state == HCV_state.exposed || hcv_state == HCV_state.infectiousacute || hcv_state == HCV_state.chronic;
	}
	public boolean isInfectious() {
		return hcv_state == HCV_state.infectiousacute || hcv_state == HCV_state.chronic;
	}
	public boolean isNaive() {
		return hcv_state == HCV_state.susceptible;
	}
	public boolean isResistant() {
		return hcv_state == HCV_state.recovered;
	}
	public HCV_state getHcvState() {
		return hcv_state;
	}	
	/*
	 * this function overrides the normal course of an infection
	 * it should not be used for natural exposures
	 * 
	 * censored_acute = in the middle of an acute infection
	 */
	public void setHcvInitState(HCV_state state, int logging) {
		assert state != HCV_state.ABPOS; //used only in the DrugUser container
		hcv_state = state;
		switch (state) {
			case exposed:
				receive_infectious_dose();
				if (logging > 0) {
					Statistics.fire_status_change(AgentMessage.exposed, this.agent, "", null);
					this.agent.setLastExposureDate(); //must follow fire, b/c it uses the signature
				}
				break;
			case infectiousacute:
				double acute_end_time = (int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
				acute_end_time += RandomHelper.createExponential(1./mean_days_acute_naive).nextDouble();	
				ScheduleParameters acute_end_params = ScheduleParameters.createOneTime(acute_end_time);
				ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
				next_immunology_actions.add(schedule.schedule(acute_end_params, this, "leave_acute", false));
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
		prob_self_limiting_female 	  = (Double) params.getValue("prob_self_limiting_female");
		prob_self_limiting_male 	  = (Double) params.getValue("prob_self_limiting_male");
		prob_clearing 				  = (Double) params.getValue("prob_clearing");
		transmissibility 			  = (Double) params.getValue("transmissibility");		
	}

	public boolean leave_acute(boolean past_recovered) {
		//returns true iff self limiting
		double prob_self_limiting = agent.getGender() == Gender.Male? prob_self_limiting_male : prob_self_limiting_female; 
		if (((!past_recovered) && prob_self_limiting > RandomHelper.nextDouble()) ||
				((past_recovered) && prob_clearing > RandomHelper.nextDouble()))	{
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

	/*
	 * start a NATURAL infection via exposure.  
	 * 1. the calling method is responsible for announcing the exposure, and updating the time of last exposure
	 * 2. if one is recovered, it's possible to be "infected" a new
	 * 3. if one has RNA, then no new infection would be established.
	 * 
	 * return true iff new infection has been started
	 */
	protected boolean receive_infectious_dose() {
		if(isHcvRNA()) {
			return false;
		}
		Boolean past_recovered = hcv_state == HCV_state.recovered;
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
		ScheduleParameters acute_end_params = ScheduleParameters.createOneTime(acute_end_time);
		next_immunology_actions.add(schedule.schedule(acute_end_params, this, "leave_acute", past_recovered));
		
		return true;
	}
}
