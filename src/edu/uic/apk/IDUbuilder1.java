/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import edu.uic.apkSynth.DrugUser;
import edu.uic.apkSynth.Gender;
import edu.uic.apkSynth.HCV_state;
import edu.uic.apkSynth.PersonGenerator;

import java.lang.reflect.Method;
//incompatible with 1.6
//import java.nio.charset.Charset;
//import java.nio.file.Files;
//import java.nio.file.Paths;
import java.util.ArrayList;




//import java.util.Calendar;
import org.joda.time.*;

import java.util.HashMap;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import cern.jet.random.Poisson;
import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.IAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.essentials.RepastEssentials;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;
import repast.simphony.space.graph.Network;


/*
 * An agent factory for generating a population of injecting-drug users (IDUs), also called "persons who inject drugs" (PWID)
 * - created as a singleton
 */
public class IDUbuilder1 implements AgentFactory {
	HashMap <String,Object> extra_params;
	HashMap <String,Object> sim_params;
	
	private Context context;
	private Geography<IDU> geography;
	private Network<IDU> network;
	HashMap <String, ZoneAgent> zip_to_zones;
	Poisson idu_arrivals;

	//Note: we avoid defaults and constants, instead relying on the parameter file
	private static double ab_prob_chronic           = Double.NaN;// = 0.80; //when testing antibody-positive
	private static double ab_prob_acute 			   = Double.NaN;//   = 0.05; //when testing antibody-positive
	private static double burn_in_days              = Double.NaN;
	private static boolean burn_in_mode             = false;
	private static int     initial_pwid_count        = -1;
	private static double net_inflow         = Double.NaN;
	private static double pwid_maturity_threshold   = Double.NaN; //usually about 5
	private static double prob_infected_when_arrive = Double.NaN;
	private static double status_report_frequency = -1;

	private Method idu_generator;
	PersonGenerator pg = null;
	
	public IDUbuilder1 (Context context,
						HashMap <String,Object> sim_params,
					    HashMap <String,Object> extra_params) {
		this.extra_params 		= extra_params;
		this.sim_params 	    = sim_params;
		this.context            = context;
		this.geography          = (Geography<IDU>) context.getProjection("Geography");
		this.network 			= (Network<IDU>) context.getProjection("infection network");
		this.zip_to_zones       = (HashMap<String, ZoneAgent>) extra_params.get("zip_to_zones");


		IDU.setStatics(context, network, geography);
		IDU.setHomophilyStrength((Double) sim_params.get("homophily_strength"));//
		IDU.setMeanTieLifetime((Double) sim_params.get("mean_tie_lifetime"));// 
		//IDU.setMeanSero_bias((Double) sim_params.get("mean_sero_bias"));//
		IDU.setAttrition_rate((Double) sim_params.get("attrition_rate"));// 
		IDU.setMean_career_duration((Double) sim_params.get("mean_career_duration"));
		IDU.setProb_cessation((Double) sim_params.get("prob_cessation"));

        try {
        	pg             = PersonGenerator.make_NEP_generator(sim_params, pwid_maturity_threshold, RandomHelper.nextIntFromTo(0, Integer.MAX_VALUE));
        	idu_generator  = IDUbuilder1.class.getDeclaredMethod("generate_SynthNEP", HashMap.class);

        } catch (Exception e) {
        	e.printStackTrace();
        	System.exit(1);
        }
    	
        prob_infected_when_arrive = (Double) sim_params.get("prob_infected_when_arrive");
        status_report_frequency   = (Double) sim_params.get("status_report_frequency");
        
        net_inflow          = (Double) sim_params.get("net_inflow");
        assert net_inflow >= 0;  //the other case needs to be modeled as attrition or cessation
        initial_pwid_count  = Integer.parseInt(sim_params.get("initial_pwid_count").toString());
                
        ISchedule schedule = repast.simphony.engine.environment.RunEnvironment.getInstance().getCurrentSchedule();
        schedule.schedule(ScheduleParameters.createRepeating(burn_in_days + Statistics.getCounterResetTiming()-0.001, 1.0), this, "generate_arriving"); 
        //remove -0.001 to make sure that the call is made just before the stats counter is reset
	}
	public static double getAb_prob_acute() {
		return ab_prob_acute;
	}
	public static double getAb_prob_chronic() {
		return ab_prob_chronic;
	}
	public static void setStatics(Parameters params) {
		IDUbuilder1.ab_prob_acute = (Double) params.getValue("ab_prob_acute");
		IDUbuilder1.ab_prob_chronic = (Double) params.getValue("ab_prob_chronic");
		IDUbuilder1.pwid_maturity_threshold = (Double) params.getValue("pwid_maturity_threshold");
		assert ab_prob_acute + ab_prob_chronic <= 1.0;
	}

	public static void setBurn_in_period(boolean burn_in_mode, double burn_in_period) {
		IDUbuilder1.burn_in_mode = burn_in_mode;
		IDUbuilder1.burn_in_days = burn_in_period;
	}

	/*
	 * 1. receives a raw IDU from a generator
	 * 2. checks the IDUs
	 * 3. add the IDUs to the simulation
	 * prob_infected_when_arrive refers only to immature agents
	 */
	public ArrayList <IDU> add_new_IDUs(int count, boolean early_idus_only) {
		ArrayList <IDU> my_IDUs = new ArrayList <IDU> ();
		int num_requested_idus = (count>=0)? count : idu_arrivals.nextInt();
		GeometryFactory fac = new GeometryFactory();
		HashMap <String, Object> generator_params = new HashMap<String, Object> ();
		generator_params.put("early_idus_only", (Boolean)early_idus_only);
		generator_params.put("max_trials", (Integer)200);

		while(my_IDUs.size() < num_requested_idus) {
			IDU idu = null;
			try {
				idu = (IDU) idu_generator.invoke(this, generator_params);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
			if (early_idus_only) {
				int days_since_initiated = (int) Math.round(365*(idu.getAge() - idu.getAgeStarted()));  
				idu.setBirthDate(idu.getBirthDate().plusDays(days_since_initiated - 50)); 
				if(prob_infected_when_arrive < RandomHelper.nextDouble()) { //typically for new IDU
					idu.setHcvInitialState(HCV_state.susceptible);
				} else {
					idu.setHcvInitialState(HCV_state.infectiousacute);
				}
			}
			
			ZoneAgent my_zone = zip_to_zones.get(idu.getPreliminaryZip());
			if (my_zone == null) {
				idu.deactivate();
				continue; //must be off the map? e.g. 60662
			}
			idu.setZone(my_zone);
			if (! idu.self_test_ok(0) ) {
                System.out.println("f");
				idu.deactivate();
				continue;
			}
			
			idu.setEntryDate(APKBuilder.getSimulationDate());
			double elapsed_career_days = 365.0*(idu.getAge() - idu.getAgeStarted());
			double residual_burnin_days = 0;
			if (burn_in_mode) {
				residual_burnin_days = Math.max(0, burn_in_days - RepastEssentials.GetTickCount());
			}
			if(! idu.activate(residual_burnin_days, elapsed_career_days, status_report_frequency)) {
				context.remove(idu);
				continue;
			}
			my_IDUs.add(idu);
			context.add(idu);
			Statistics.fire_status_change(AgentMessage.activated, idu, "", null);
			System.out.print(".");
			
			Coordinate possible_agent_coord = new Coordinate(my_zone.getCentroid().getX() + 0.01 * Math.random(), my_zone.getCentroid().getY() + 0.01 * Math.random());
			Point coord = fac.createPoint(possible_agent_coord);
			if (! my_zone.getPolygon().contains(coord)) {
				coord = my_zone.getPolygon().getInteriorPoint();
			}
			geography.move(idu, coord);
		}

		return my_IDUs;
	}
	
    //immigration + replacement of mortality
	public ArrayList <IDU> generate_arriving() {
		int total_lost = Statistics.daily_losses();
		idu_arrivals   = RandomHelper.createPoisson(total_lost + (net_inflow/365.0));
		int new_count  = idu_arrivals.nextInt();
		ArrayList<IDU> pop = add_new_IDUs(new_count, true);
		//System.out.println("T" + RepastEssentials.GetTickCount() + ": Added: " + new_count);
	
        return pop;

	}
	public ArrayList <IDU> generate_initial() {
		//called just once in the beginning of the simulations
		assert APKBuilder.getDateDifference(APKBuilder.getSimulationDate(), APKBuilder.getSimulation_start_date()) < 0.02;
		
		int count = initial_pwid_count;
		ArrayList<IDU> pop = add_new_IDUs(count, false);
		
		if(count > 0) {
			int num_infected = 0;
			int num_abpos    = 0;
			for(IDU idu : pop) {
				num_infected += idu.isHcvRNA() ? 1: 0;
				num_abpos    += idu.isHcvABpos() ? 1: 0;
			}
			System.out.println();
			System.out.println("Initial HCV prevalence (RNA+): " + num_infected/(1.0*count) );
			System.out.println("                  (Antibody+): " + num_abpos/(1.0*count) );
		}
		return pop;
	}

	/*
	 * calls the NEP generator, converts DrugUser to full IDU object (specific HCV state, name)
	 */
	public IDU generate_SynthNEP(HashMap <String, Object> generator_params) throws Exception {
		DrugUser modelDU = null;
		Integer max_trials = 50;
		if(generator_params.containsKey("max_trials")) {
			max_trials = (Integer) generator_params.get("max_trials");
		}
		for(int trial = 0; trial < max_trials; trial++) {
			try{
				modelDU = pg.generate(generator_params);
				assert modelDU != null;
				break;
			} catch (Exception e){
				System.out.println("x"); 
				modelDU = null;
			}
		} 
		if (modelDU == null) {
			throw new Exception("failed to generate");
		}
				
		IDU idu = new IDU();
		idu.setAgeStarted(modelDU.getAgeStarted());

		LocalDate b_day         = modelDU.getBirthDate();
		LocalDate age_at_survey = modelDU.getSurveyDate();
		LocalDate sim_date      = APKBuilder.getSimulationDate();
		b_day = b_day.plusYears(sim_date.getYear()      - age_at_survey.getYear());
		b_day = b_day.plusDays (sim_date.getDayOfYear() - age_at_survey.getDayOfYear());
		b_day = b_day.plusDays((int) (0.05*365*(RandomHelper.nextDouble() - 0.5))); //some slight jitter (0.05 of a year)
		idu.setBirthDate(b_day);
		//System.out.println("Age:" + idu.getAge() + "Model age:" + modelDU.getAge());

		idu.setDatabaseLabel(modelDU.getDatabaseLabel());
		idu.setEntryDate(LocalDate.now());
		idu.setDrugGivingDegree(modelDU.getDrugGivingDegree());
		idu.setDrugReceptDegree(modelDU.getDrugReceptDegree());
		idu.setFractionReceptSharing(modelDU.getFractionReceptSharing());
		idu.setGender(modelDU.getGender());
		if(modelDU.getHcvState() == HCV_state.ABPOS) {
			double roll = RandomHelper.nextDouble() - ab_prob_chronic;
			if (roll < 0 ){
				idu.setHcvInitialState(HCV_state.chronic);
			} else if (roll - ab_prob_acute < 0){
				idu.setHcvInitialState(HCV_state.infectiousacute);  
			} else {
				idu.setHcvInitialState(HCV_state.recovered);
			}			
		} else {
			idu.setHcvInitialState(HCV_state.susceptible);
		}
		idu.setInjectionIntensity(modelDU.getInjectionIntensity());
		if (idu.getName() == null) {
			if (idu.getGender() == Gender.Male) {
				idu.setName(IDU.male_names[RandomHelper.nextIntFromTo(0, IDU.male_names.length-1)]);
			} else {
				idu.setName(IDU.female_names[RandomHelper.nextIntFromTo(0, IDU.female_names.length-1)]);
			}
		}
		idu.setPreliminaryZip(modelDU.getPreliminaryZip());
		idu.setRace(modelDU.getRace());
		idu.setSyringe_source(modelDU.getSyringe_source());
		return idu;
	}
	public void remove_IDUs() {
		ArrayList <IDU> removed_agents = new ArrayList <IDU> ();
		for(Object obj : context) {
			if(obj instanceof IDU) {
				IDU agent = (IDU) obj;
				removed_agents.add(agent);
				
			}
		}
		for(IDU agent : removed_agents) {
			agent.deactivate();
		}
	}
	
	/*
     * used for debugging: constructs IDUs based on each of the NEP synthetic pops
     * - note that this algorithm assumes that the dblabels are sequential
	 */
	public void systematic_NEP_synthetic() {
		assert Math.abs((Double)sim_params.get("mean_enrichment_suburbs") - 0.0) < 0.00001;
		assert Math.abs((Double)sim_params.get("mean_enrichment_ChicagoNonNEP") - 0.0) < 0.00001;
        ArrayList <IDU> idu_list = new ArrayList <IDU> ();
		GeometryFactory fac = new GeometryFactory();
		HashMap <String, Object> generator_params = new HashMap<String, Object> ();
		generator_params.put("max_trials", 1);
		for(Integer db_reference_number = 0; db_reference_number < pg.catalogueSize(); db_reference_number ++) {
			IDU idu = null;
			try {
				generator_params.put("db_reference_number", db_reference_number);
				//LOAD the characteristics of the agents
				System.out.print("\n NEP#"+generator_params.get("db_reference_number"));
				idu = (IDU) generate_SynthNEP(generator_params);
				if(idu == null) {
					continue;
				}
			} catch (Exception e) {
				continue;
			}			
			ZoneAgent my_zone = zip_to_zones.get(idu.getPreliminaryZip());
			if (my_zone == null) {
				continue; //must be off the map? e.g. 60662
			}
			idu.setZone(my_zone);
			if (! idu.self_test_ok(1) ) {
				continue;
			}
			idu.setEntryDate(APKBuilder.getSimulationDate());
			double elapsed_career_days = 365.0*(idu.getAge() - idu.getAgeStarted());
			double residual_burnin_days = 0;
			if (burn_in_mode) {
				residual_burnin_days = Math.max(0, burn_in_days - RepastEssentials.GetTickCount());
			}
			if(! idu.activate(residual_burnin_days, elapsed_career_days, status_report_frequency)) {
				continue;
			}
			
        	idu_list.add(idu);
			context.add(idu);
			Statistics.fire_status_change(AgentMessage.activated, idu, "", null);
			System.out.print(".");
			
			Coordinate possible_agent_coord = new Coordinate(my_zone.getCentroid().getX() + 0.01 * Math.random(), my_zone.getCentroid().getY() + 0.01 * Math.random());
			Point coord = fac.createPoint(possible_agent_coord);
			if (! my_zone.getPolygon().contains(coord)) {
				coord = my_zone.getPolygon().getInteriorPoint();
			}
			geography.move(idu, coord);
		}
		int count = idu_list.size();
		if(count > 0) {
			int num_infected = 0;
			int num_abpos    = 0;
			for(IDU idu : idu_list) {
				num_infected += idu.isHcvRNA() ? 1: 0;
				num_abpos    += idu.isHcvABpos() ? 1: 0;
			}
			System.out.println();
			System.out.println("Initial HCV prevalence (RNA+): " + num_infected/(1.0*count) );
			System.out.println("                  (Antibody+): " + num_abpos/(1.0*count) );
		}

		System.out.println("\nDone. Finished " + idu_list.size() + " agents.");
		return;
	}
}

