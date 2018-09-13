/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import javax.swing.JPanel;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.joda.time.LocalDate;
import org.jscience.physics.amount.Amount;
import org.opengis.feature.simple.SimpleFeature;

import repast.simphony.context.Context;
import repast.simphony.context.space.gis.GeographyFactoryFinder;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.GUIRegistry;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.environment.RunState;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.essentials.RepastEssentials;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;
import repast.simphony.space.gis.GeographyParameters;
import repast.simphony.space.graph.Network;
import repast.simphony.visualization.IDisplay;
import repast.simphony.visualization.gis3D.DisplayGIS3D;
import cern.jet.random.Exponential;
import edu.uic.apkSynth.HCV_state;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;


/*
 * Main simulation class
 * - initialization
 * - global burn-in control
 * - creation of links in the network, regular link creation
 * - visualization of agents and links in the GUI
 */
public class APKBuilder implements ContextBuilder<Object> {

	private HashMap <String, ZoneAgent> zip_to_zones;
	private HashMap <ZoneAgent,LinkedList<IDU>> 		   effective_zone_population;      
	private HashMap <ZoneAgent,ArrayList<IDU>> 		       zone_population;      
	private HashMap <ZoneAgent,HashMap<ZoneAgent,Double>> zone_zone_distance;
	private int                                            total_IDU_population = 0;

	static final double market_lons[]   = {-87.74469, -87.66458, }; //centered about the Westside station
	static final double market_lats[]   = {41.88052,  41.77946,  }; //centered about the Southside station

	/* COIP NEP field stations
	 * Northside Field Station 		-87.65526,41.96201
	 * Northwest Side Field Station -87.70684,41.91056
	 * Westside Field Station 		-87.74469,41.88052
	 * Southside Field Station 		-87.66458,41.77946
	 * Southeastside Field Station  -87.55171,41.73325
	 */ 
	public enum EnrollmentMethodTreat { 
		unbiased, HRP, fullnetwork, inpartner, outpartner;
	}

	public enum EnrollmentMethodVaccine { 
		unbiased, positive_innetwork;
	}

		
	public static void main(String[] args) {
		getDistanceTest();
	}
	private static Context context;
	private static Geography geography;
	private static Network network;

	public static boolean burn_in_mode 		  	         = true;
	private ArrayList<AgentFactory> factories               = new ArrayList<AgentFactory> ();
	private final double  linking_time_window 		     = 0.1;
	private final double  excess_serosorting              = 0.0; //no longer used
	private static double interaction_home_cutoff         = Double.NaN;  //distance where the decay in the interaction rate starts
	private static double interaction_rate_constant       = Double.NaN;  //number of daily encounters of a pair living at a distance of cutoff from each other
	private static double interaction_rate_at_drug_sites  = Double.NaN;
	private static double interaction_rate_exzone         = Double.NaN; 
	
	private long run_start_time = System.currentTimeMillis();
	private HashMap <EnrollmentMethodTreat, Double> treatment_enrollment_probability = new HashMap <EnrollmentMethodTreat,Double> ();
	private HashMap <EnrollmentMethodTreat, Double> treatment_residual_enrollment    = new HashMap <EnrollmentMethodTreat,Double> ();
	private Double treatment_enrollment_per_PY = 0.0;
	private double treatment_mean_daily = 0.0; //updated just once when we load the parameters

	private int vaccine_study_arm_n = 0; //assume identical arm sizes
	private int vaccine_study_enrolled = 0;
	private String vaccine_schedule = "";
	private int vaccine_total_doses = 0;
	private double vaccine_start_of_enrollment = Double.NaN;
	private int vaccine_enrollment_duration_days = -1;//365; //duration of the recruitment phase of the vaccine trial  
	private double vaccine_enrollment_launch_day = -1; //delay after burn-in ends
	private double vaccine_dose2_day = -1;//60;
	private double vaccine_dose3_day = -1;//180;
	private double vaccine_followup_weeks = -1.0;
	private boolean vaccine_followup_purge_with_rna = false;
	private int vaccine_followup1_periods = -1;//545; 
	private int vaccine_followup2_periods = -1;//270; 
	private double vaccine_annual_loss = -1;
	private HashMap <EnrollmentMethodVaccine, Double> vaccine_enrollment_probability = new HashMap <EnrollmentMethodVaccine, Double> ();
	private HashMap <EnrollmentMethodVaccine, Double> vaccine_residual_enrollment    = new HashMap <EnrollmentMethodVaccine, Double> ();

	//we generally assume that the simulation is based on 2009 prevalence and starts on 2010-01-01
	public static LocalDate simulation_start_date = new LocalDate(2010, 1, 1, null);
	public static ISchedule main_schedule = null;

	/*
	 * The core initialization method of the simulation.  Repast calls this; then the simulation just calls the scheduler
	 * (non-Javadoc)
	 * @see repast.simphony.dataLoader.ContextBuilder#build(repast.simphony.context.Context)
	 */
	@Override
	public Context build(Context<Object> context) {
		reportClassPath();
		
		context.setId("edu.uic.apk");
		APKBuilder.context = context;
		GeographyParameters geoParams = new GeographyParameters();
		geography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("Geography", context, geoParams);

		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("infection network", context, true); //directed network
		network = netBuilder.buildNetwork();
		assert network.isDirected();

		LinkAgent.setStatics(context, geography);
		ZoneAgent.setBuilder(this);

		if (! RunEnvironment.getInstance().isBatch()) {
			try{
				Class.forName("javax.media.opengl.glu.GLU");
			} catch (Exception e) {
				System.out.println("Cannot access OpenGL:\n   " + e.toString());
			}
		}
		
		repast.simphony.engine.environment.RunEnvironment.getInstance().getCurrentSchedule().setTimeUnits(Amount.valueOf(1, javax.measure.unit.NonSI.DAY));
	    main_schedule = repast.simphony.engine.environment.RunEnvironment.getInstance().getCurrentSchedule();
		
		System.out.println("Building simulation...\n  seed=" +RandomHelper.getSeed() + ". In batch mode, the seed is carried over to subsequent runs, but the RNG state is new.");

		zip_to_zones    = load_zone_agents(zip_to_zones, "data/gisdata/illinois_zips/zt17_d00.shp");
		zip_to_zones    = load_zone_agents(zip_to_zones, "data/gisdata/michigan_zips/zt26_d00.shp");
		zip_to_zones    = load_zone_agents(zip_to_zones, "data/gisdata/indiana_zips/zt18_d00.shp");
		zip_to_zones    = load_zone_agents(zip_to_zones, "data/gisdata/wisconsin_zips/zt55_d00.shp");

		Parameters params = RunEnvironment.getInstance().getParameters();		
		burn_in_control((Double)params.getValue("burn_in_days"));
		run_end((Integer)params.getValue("run_length"));
		
		Immunology.setStatics(params);
		IDUbuilder1.setStatics(params);
		
		//excess_serosorting    		     = (Double)params.getValue("excess_serosorting");
		interaction_home_cutoff		     = (Double)params.getValue("interaction_home_cutoff");
		interaction_rate_constant 		 = (Double)params.getValue("interaction_rate_constant");
		interaction_rate_at_drug_sites	 = (Double)params.getValue("interaction_rate_at_drug_sites");
		interaction_rate_exzone 		 = (Double)params.getValue("interaction_rate_exzone");
		getDistanceTest();
		
		zone_zone_distance    = new HashMap <ZoneAgent,HashMap<ZoneAgent,Double>> ();
		for(ZoneAgent zone1 : zip_to_zones.values()) {  //very broad, constant values
			HashMap <ZoneAgent,Double> my_nbs = new HashMap<ZoneAgent,Double>();
			zone_zone_distance.put(zone1, my_nbs);
		}

		zone_population			  = new HashMap <ZoneAgent,ArrayList<IDU>> ();
		effective_zone_population = new HashMap <ZoneAgent,LinkedList<IDU>> ();

		treatment_enrollment_per_PY  = (Double)params.getValue("treatment_enrollment_per_PY");
		
		treatment_enrollment_probability.put(EnrollmentMethodTreat.unbiased, (Double)params.getValue("treatment_enrollment_probability_unbiased"));
		treatment_enrollment_probability.put(EnrollmentMethodTreat.HRP, (Double)params.getValue("treatment_enrollment_probability_HRP"));
		treatment_enrollment_probability.put(EnrollmentMethodTreat.fullnetwork, (Double)params.getValue("treatment_enrollment_probability_fullnetwork"));
		treatment_enrollment_probability.put(EnrollmentMethodTreat.inpartner, (Double)params.getValue("treatment_enrollment_probability_inpartner"));
		treatment_enrollment_probability.put(EnrollmentMethodTreat.outpartner, (Double)params.getValue("treatment_enrollment_probability_outpartner")); 
		//wishlist: check that the probabilities add to 1.0
		treatment_residual_enrollment.put(EnrollmentMethodTreat.unbiased, 0.0);
		treatment_residual_enrollment.put(EnrollmentMethodTreat.HRP, 0.0);
		treatment_residual_enrollment.put(EnrollmentMethodTreat.fullnetwork, 0.0);
		treatment_residual_enrollment.put(EnrollmentMethodTreat.inpartner, 0.0);
		treatment_residual_enrollment.put(EnrollmentMethodTreat.outpartner, 0.0);
		
		vaccine_enrollment_duration_days = (Integer)params.getValue("vaccine_enrollment_duration_days");	
		vaccine_annual_loss = (Double)params.getValue("vaccine_annual_loss");	
		vaccine_study_arm_n = (Integer)params.getValue("vaccine_study_arm_n");
		vaccine_schedule    = (String)params.getValue("vaccine_schedule");
		vaccine_enrollment_launch_day    = (Double)params.getValue("vaccine_enrollment_launch_day");
		vaccine_total_doses = vaccine_schedule.contains("1")?1 : (vaccine_schedule.contains("2")?2:3);
		
		vaccine_followup1_periods = (Integer)params.getValue("vaccine_followup1_periods");
		vaccine_followup2_periods = (Integer)params.getValue("vaccine_followup2_periods");
		vaccine_followup_weeks    = (Double)params.getValue("vaccine_followup_weeks");
		vaccine_followup_purge_with_rna = ((Integer)params.getValue("vaccine_followup_purge_with_rna")) == 1;
		
		if(vaccine_total_doses > 1) {
			vaccine_dose2_day = (Double)params.getValue("vaccine_dose2_day");
		}
		if(vaccine_total_doses > 2) {
			vaccine_dose3_day = (Double)params.getValue("vaccine_dose3_day");
		}

		//wishlist: check it adds up to 1.0
		vaccine_enrollment_probability.put(EnrollmentMethodVaccine.unbiased, (Double)params.getValue("vaccine_enrollment_probability_unbiased"));
		vaccine_enrollment_probability.put(EnrollmentMethodVaccine.positive_innetwork, (Double)params.getValue("vaccine_enrollment_probability_positiveinnetwork"));
		//counts carryover from day to next
		vaccine_residual_enrollment.put(EnrollmentMethodVaccine.unbiased, 0.0);
		vaccine_residual_enrollment.put(EnrollmentMethodVaccine.positive_innetwork, 0.0);

		Statistics.build(params, context, network, zip_to_zones, zone_zone_distance);
		Statistics.dump_network_distances(params.getValue("dump_network_distances"));

		//load the data for the factories
		HashMap <String,Object> population_params  = load_pop_params(params);
		HashMap <String,Object> extra_params    	= new HashMap <String,Object> ();
		extra_params.put("zip_to_zones", zip_to_zones);
		
		IDUbuilder1 factory1 = new IDUbuilder1(context, population_params, extra_params);

		//factory1.systematic_CNEPplus_synthetic(); //special code to output the entire population
		//run_end(-1); 
		//System.exit(1);
		
		factory1.generate_initial();
				
		main_schedule.schedule(ScheduleParameters.createOneTime(-0.2),        				   this, "rotate_globe");
		main_schedule.schedule(ScheduleParameters.createRepeating(1, 1, 0), 				   this, "do_zone_census");
		main_schedule.schedule(ScheduleParameters.createRepeating(1, linking_time_window, -1), this, "do_linking", 0.0); 		//"-1" to ensure after the census
		main_schedule.schedule(ScheduleParameters.createRepeating(0, 1, -2), 				   this, "update_link_geometries");

		do_initial_linking();
		//schedule.schedule(ScheduleParameters.createRepeating(1, 1), this, "update_display");
		
		if(treatment_enrollment_per_PY > 0) {
			double start_of_enrollment = (Double)params.getValue("burn_in_days") + (Double)params.getValue("treatment_enrollment_start_delay");
			main_schedule.schedule(ScheduleParameters.createRepeating(start_of_enrollment, 1, -1), this, "do_treatment");
		}

		if(! vaccine_schedule.equals("") && vaccine_study_arm_n > 0) {
			vaccine_start_of_enrollment = (Double)params.getValue("burn_in_days") + vaccine_enrollment_launch_day;
			main_schedule.schedule(ScheduleParameters.createRepeating(vaccine_start_of_enrollment, 1, -1), this, 
					"vaccine_trial_enroll");
		}

			
		return context;
	}
	
	/*
	 * Each zone is given a unique drug market based on the closest geographic place
	 */
	private void assign_drug_market(ZoneAgent zone) {
		Coordinate z_center = zone.getCentroid().getCoordinate();
        GeodeticCalculator calc = new GeodeticCalculator(DefaultGeographicCRS.WGS84);
		calc.setStartingGeographicPoint(z_center.x, z_center.y);
		int closest_market  = -1;
		double closest_d   = Double.MAX_VALUE;
		for (int market_index=0; market_index < market_lons.length; ++market_index) {
			calc.setDestinationGeographicPoint(market_lons[market_index], market_lats[market_index]);
			double d = calc.getOrthodromicDistance();
			if (d < closest_d) {
				closest_market = market_index;
				closest_d = d;
			}
		}
		zone.setDrug_market(closest_market);
	}

	/*
	 * activate the burn-in mode
	 * - should be called before the agents are created
	 * 
	 * TODO: the logic and naming of the functions is very bad.  negative burn-in values are not handled right.
	 *  current workaround: require a small amount of burn-in
	 *  TODO: refactor to ensure all initiation actions occur regardless if there is a burn-in mode
	 *  TODO: IDUbuilder should test that its burn-in flag and burn-in days has been set in the constructor
	 */
	public void burn_in_control(Double burn_in_days) {
		if(burn_in_days.isNaN() | burn_in_days <= 0) {
			System.err.println("Burnin days is NA or nonpositive - setting to 0.1");
			burn_in_days = 0.1;
		}
		Statistics.setBurnInMode(true);
		IDUbuilder1.setBurn_in_period(true, burn_in_days);
		
		main_schedule.schedule(ScheduleParameters.createOneTime(RepastEssentials.GetTickCount() + burn_in_days), this, "burn_in_end", burn_in_days);
	}
	/*
	 * activate the agents after the burn-in
	 * 
	 */
	public void burn_in_end(Double burn_in_days) {
		burn_in_mode = false;
		Statistics.setBurnInMode(false);
		IDUbuilder1.setBurn_in_period(false, -1);

		for(Object obj : context) {
			if(obj instanceof IDU) {
				IDU agent = (IDU) obj;
				agent.setBirthDate(agent.getBirthDate().plusDays(burn_in_days.intValue()));  
				assert agent.isActive();
				Statistics.fire_status_change(AgentMessage.activated, agent, "", null);
			}
		}
		System.out.println("\n**** Finished burn-in. Duration: " + burn_in_days + " ****");		
	}
	
	/*
	 * Constructs the initial network of the simulation
	 */
	private void do_initial_linking() {
		double total_edges = network.getDegree();
		double total_recept_edge_target = 0;
		double total_give_edge_target = 0;
		for (Object idu : network.getNodes()) {
			if(idu instanceof IDU) {
				total_recept_edge_target += ((IDU)idu).getDrugReceptDegree();
				total_give_edge_target   += ((IDU)idu).getDrugGivingDegree();
			} else{
				assert network.getDegree(idu) == 0;
				//ZoneAgents might enter the network, b/c they are part of the context.
			}
		}
		int iteration = 0; 
		final double DENSITY_TARGET = 0.95; //stopping criterion, in terms of actual vs. required number of edges
		final int    MAXITER = 30;           //maximal number of iterations when forming the network, to prevent too much work
		while ((total_edges/total_recept_edge_target < DENSITY_TARGET) && (total_edges/total_give_edge_target < DENSITY_TARGET) && (iteration < MAXITER)) {
			System.out.println("Total edges: " + total_edges + ". target in: " + total_recept_edge_target  + ". target out: " + total_give_edge_target);
			do_zone_census();
			do_linking(excess_serosorting);
			do_linking(excess_serosorting);
			total_edges = network.getDegree();
			iteration ++;
		}
		System.out.println("Total edges: " + total_edges + ". target in: " + total_recept_edge_target  + ". target out: " + total_give_edge_target);
		if (iteration == MAXITER) {
			System.out.println("Initial linking reached the maximum number of iterations ("+MAXITER+")");
		}
	}

	/*
	 * Regularly called to insert new connections
	 * (Existing connections are removed by the scheduler)
	 */
	public void do_linking(Double excess_serosorting) {
		//System.out.printf("%.1f: Linking ... ", RepastEssentials.GetTickCount());
		TreeMap <Double, Object[]> actions = new TreeMap <Double, Object[]> ();
		for(ZoneAgent zone1 : effective_zone_population.keySet()) {
			if(effective_zone_population.get(zone1).size() == 0) {
				continue;
			}
			for(ZoneAgent zone2 : effective_zone_population.keySet()) {
				Double rate = interaction_rate(zone1, zone2);
				if (rate == 0.0) {
					continue;
				}
				Exponential exp_gen = RandomHelper.createExponential(rate);
				for (double t = 0; ;) {
					t += exp_gen.nextDouble();
					if (t > linking_time_window) {
						break;
					}
					Object[] zones = new Object[2];
					zones[0] = zone1;
					zones[1] = zone2;
					actions.put(t, zones);
				}
			}
			if(actions.size() > 1E6) {
				System.out.println("Warning: too many linking actions might exhaust heap memory.  Reduce linking_time_window.");
			}
		}
		//System.out.print("building ... ");
		int num_new_links = 0;
		for(Object[] pair : actions.values()) {
			boolean new_link = link_zones((ZoneAgent)pair[0], (ZoneAgent)pair[1], excess_serosorting);
			num_new_links += new_link? 1: 0;
		}
		//System.out.println("Done. New links:" + num_new_links);
	}

	
	/*
	 * runs daily and recruits PWID for treatment
	 * 
	 */
	public void do_treatment() {
		treatment_mean_daily =  total_IDU_population * treatment_enrollment_per_PY / 365.0; //the value changes if the population changes. recall, we assume year is exactly 365 days
		double todays_total_enrollment = RandomHelper.createPoisson(treatment_mean_daily).nextInt();
		//TODO: check that any residual individuals are saved
		
		if (todays_total_enrollment <= 0) {
			return; //do nothing.  occurs when we previously over-enrolled
		} 
		
		//TODO: test by changing the methods.  Is the outcome responsive?
		//TODO: is the total daily target achieved?
		ArrayList <IDU> candidates = new ArrayList<IDU>();
		for(ArrayList <IDU> zonePop : zone_population.values()) {
			for(IDU candidate : zonePop) {
				if(candidate.isTreatable()) {
					candidates.add(candidate);
				}
			}
		}
		if(candidates.size() == 0) {
			System.out.println("Not treating - treatable PWIDs: 0");
			return;
		}
		for(EnrollmentMethodTreat mthd : EnrollmentMethodTreat.values()) {
			double enrollment_target = todays_total_enrollment * treatment_enrollment_probability.get(mthd) + treatment_residual_enrollment.get(mthd);
			treatment_residual_enrollment.put(mthd, enrollment_target);  //update.  might be fractional increase 	
			if(enrollment_target < 1) {
				//System.out.println("Method: " + mthd + ". Enrolled: " + 0 + ". Residual: " + (enrollment_target - 0));
				continue;
			}
			Collections.shuffle(candidates); //shuffle for every method
			HashSet<IDU> enrolled = do_treatment_select(mthd, candidates, enrollment_target); //set, to avoid accidentally double-recruiting
			treatment_residual_enrollment.put(mthd, enrollment_target - enrolled.size()); 	//carried over from day to the next day.  this can give below 0
			for(IDU idu : enrolled) {
				idu.startTreatment();
			}
			//System.out.println("Method: " + mthd + ". Enrolled: " + enrolled.size() + ". Residual: " + (enrollment_target - enrolled.size()));
		}
	}
	
	/*
	 * attempts to recruit for treatment using method enrMethod
	 * graceful operation - does not guarantee success in recruiting the desired number
	 */
	public HashSet<IDU> do_treatment_select(EnrollmentMethodTreat enrMethod, ArrayList <IDU> candidates, double enrollment_target) {
		HashSet<IDU> enrolled = new HashSet<IDU> (); //set, to avoid accidentally double-recruiting
		if(candidates.size() == 0) {
			return enrolled;
		}
		int next_candidate_idx = 0;
		if(enrMethod == EnrollmentMethodTreat.unbiased) {
			for(; (enrolled.size() < enrollment_target) && (next_candidate_idx < candidates.size()); ++next_candidate_idx) {
				IDU idu = candidates.get(next_candidate_idx);
				if(idu.isTreatable()) {
					enrolled.add(idu);
				}
			}
		} else if(enrMethod == EnrollmentMethodTreat.HRP) {
			for(; (enrolled.size() < enrollment_target) && (next_candidate_idx < candidates.size()); ++next_candidate_idx) {
				IDU idu = candidates.get(next_candidate_idx);
				if(idu.isTreatable() && idu.isInHarmReduction()) {
					enrolled.add(idu);
				}
			}
		} else if(enrMethod == EnrollmentMethodTreat.fullnetwork) {
			for(; (enrolled.size() < enrollment_target) && (next_candidate_idx < candidates.size()); ++next_candidate_idx) {
				IDU idu = candidates.get(next_candidate_idx);
				if(! idu.isTreatable()) {
					continue;
				}
				enrolled.add(idu);
				Iterable nbs = network.getAdjacent(idu);
				for(Object nb : nbs) {
					if(nb != null && (nb instanceof IDU) && ((IDU)nb).isTreatable()) {
						enrolled.add((IDU)nb);
					}
				}
			}
		} else if(enrMethod == EnrollmentMethodTreat.inpartner || enrMethod == EnrollmentMethodTreat.outpartner) {
			for(; (enrolled.size() < enrollment_target) && (next_candidate_idx < candidates.size()); ++next_candidate_idx) {
				IDU idu = candidates.get(RandomHelper.nextIntFromTo(0, candidates.size()-1));
				if(! idu.isTreatable()) {
					continue;
				}
				enrolled.add(idu);
				Iterable nbs = null;
				if(enrMethod == EnrollmentMethodTreat.inpartner) {
					nbs = network.getPredecessors(idu); 
				} else {
					nbs = network.getSuccessors(idu);
				}
				for(Object nb : nbs) {
					if((nb != null) && (nb instanceof IDU) && ((IDU)nb).isTreatable()) {
						enrolled.add((IDU)nb);
						break; //only one
					}
				}
			}
		}
		return enrolled;
	}
	

	/*
	 * Determine how many IDUs in each zones are available to form new connections
	 * - the census is stored in zone_population (all) and effective_zone_population (only those that can form new connections)
	 * - typically occurs once a day (linking_time_window)
	 */
	public void do_zone_census() {
		//System.out.print("Census... ");
		zone_population			  = new HashMap <ZoneAgent,ArrayList<IDU>> ();
		effective_zone_population = new HashMap <ZoneAgent,LinkedList<IDU>> ();
        total_IDU_population      = 0;
		for(Object obj : context) {
			if(obj instanceof IDU) {
				IDU agent = (IDU) obj;
				total_IDU_population += 1;
				assert agent.isActive();
				ZoneAgent zone = agent.getZone();
				LinkedList <IDU> my_eff_agents = effective_zone_population.get(zone);
				if (my_eff_agents == null) {
					my_eff_agents = new LinkedList<IDU> ();
					effective_zone_population.put(zone, my_eff_agents);				
				}
				if(agent.can_accept_in_or_out_connection()) {
					my_eff_agents.add(agent);
				}
				ArrayList <IDU> my_agents = zone_population.get(zone);
				if (my_agents == null) {
					my_agents = new ArrayList<IDU> ();
					zone_population.put(zone, my_agents);				
				}
				my_agents.add(agent);
			}
		}
		//System.out.println("Done");
	}

	/*
	 * Geodesic distance between the centers of the zones - used to compute interaction rates
	 *  - wishlist: consider also internal distances of large ZIP codes using SurfacePolygon.getLength
	 */
	protected static double getDistance(ZoneAgent zone1, ZoneAgent zone2) {
		if(zone1 == zone2) {
			return 0.;
		}
		//distance between centroids of the zones
		Coordinate c1 = zone1.getCentroid().getCoordinate();
        GeodeticCalculator calc = new GeodeticCalculator(DefaultGeographicCRS.WGS84);
		calc.setStartingGeographicPoint(c1.x, c1.y);
		
		Coordinate c2 = zone2.getCentroid().getCoordinate();
        calc.setDestinationGeographicPoint(c2.x, c2.y);

		//returns the distance in meters
		return 0.001 * calc.getOrthodromicDistance();
	}

	/*
	 * A simple pre-computed test of the distance function
	 * - used in testing only
	 */
	protected static void getDistanceTest() {
		GeodeticCalculator calc = new GeodeticCalculator(DefaultGeographicCRS.WGS84);
		calc.setStartingGeographicPoint(-87.66736, 41.869057);
		calc.setDestinationGeographicPoint(-87.650217, 41.8702);
//		LatLon p1 = new LatLon(Angle.fromDegrees(41.869057), Angle.fromDegrees(-87.66736));  //UIC-SPH
//		LatLon p2 = new LatLon(Angle.fromDegrees(41.8702), Angle.fromDegrees(-87.650217));   //UIC recreation facility

		double dis = 0.001*calc.getOrthodromicDistance();
		System.out.println(dis);
		//"0.001" because it returns the distance in meters
		final double true_dis = 1.65; //known value
		
		assert dis < 1.8;
		assert dis > 1.4;
		if(Math.abs(dis-true_dis) > 0.5 ){ 
			System.err.println("Failed distance test ...");
			System.exit(1);
		}
		if(interaction_home_cutoff < 0.1 || interaction_home_cutoff > 200){ 
			System.err.println("interaction_home_cutoff too large...");
			System.exit(1);
		}
	}
	public static LocalDate getSimulation_start_date() {
		return simulation_start_date;
	}

	protected static double getDateDifference(LocalDate c1, LocalDate c2) {
		//like c1 - c2 in years
		assert c2 != null;
		if(c1 == null) {
			c1 = getSimulationDate();
		}
		LocalDate dt = c1.minusDays(c2.getDayOfYear()).minusYears(c2.getYear());
		return dt.getYear() + (dt.getDayOfYear() / 365.0);  //year is 365 ticks throughout
	}
	
	protected static LocalDate getSimulationDate() {
		//the current year (in decimals)
		LocalDate now = simulation_start_date;
		double shift = Math.ceil(RepastEssentials.GetTickCount()); //note: the tick starts at -1
		now = now.plusDays((int) shift);
		return now;
	}
	public static String getSimulationDateStr() {
		//the current year (in decimals)
		return getSimulationDate().toString();
	}

	public ArrayList<IDU> getZonePopulation(ZoneAgent a) {
		ArrayList<IDU> zp = zone_population.get(a);
		if (zp != null) {
			return zp;
		} else {  //if called before the first census
			return new ArrayList<IDU>();
		}
	}

	/*
	 * Computes the probability of interaction between two zones
	 * - considers the distances and any common drug market
	 */
	private double interaction_rate(ZoneAgent zone1, ZoneAgent zone2){
		if(zone1 == null || zone2 == null) {
			return 0.0;
		}
		Double dis = zone_zone_distance.get(zone1).get(zone2);
		if(dis == null) {
			dis = getDistance(zone1,zone2);
			zone_zone_distance.get(zone1).put(zone2, dis);
		}
		LinkedList<IDU> near_pop    = effective_zone_population.get(zone1);
		LinkedList<IDU> distant_pop = effective_zone_population.get(zone2);
		if(near_pop == null || distant_pop == null) {
			return 0.0;
		}
		//zone-zone interaction rate based on distance and population
		int pop1 = near_pop.size();
		int pop2 = distant_pop.size();
		double ret = 0;
		if (dis > interaction_home_cutoff) {
			if (zone1.getDrug_market() == zone2.getDrug_market()){
				ret = (interaction_rate_at_drug_sites*pop1*pop2) + (interaction_rate_exzone*pop1*pop2)/Math.pow(dis, 2);
			} else {
				ret = (interaction_rate_exzone*pop1*pop2)/Math.pow(dis, 2);
			}
		} else {
			ret = (interaction_rate_at_drug_sites*pop1*pop2) + (interaction_rate_constant*pop1*pop2);
		}
		return ret;
	}

	/*
	 * Attempt to construct links between two zones
	 */
	public boolean link_zones(ZoneAgent zone1, ZoneAgent zone2, Double excess_serosorting) {
		boolean new_link = false;
		if(effective_zone_population.get(zone1) == null || effective_zone_population.get(zone2) == null) {
			new_link = false;
			return new_link;
		}
		int s1 = effective_zone_population.get(zone1).size();
		int s2 = effective_zone_population.get(zone2).size();
		if(s1 == 0 || s2 == 0) {
			new_link = false;
			return new_link;
		}
		
		int a1_idx = RandomHelper.nextIntFromTo(0, s1-1);
		int a2_idx = RandomHelper.nextIntFromTo(0, s2-1);
		if(zone1 == zone2 && a1_idx == a2_idx) {
			new_link = false;
			return new_link;
		}
		IDU a1 = effective_zone_population.get(zone1).get(a1_idx);
		IDU a2 = effective_zone_population.get(zone2).get(a2_idx);
		if(a1 == null || ! context.contains(a1) || a2 == null || ! context.contains(a2)) { 
			new_link = false;
			return new_link;
		}
		if ((a1.isNaive() != a1.isNaive()) && (RandomHelper.nextDouble() < excess_serosorting)) {
			new_link = false;
			return new_link;
		}
		if(a1.try_connect(a2)) {
			new_link = true;
			if (! RunEnvironment.getInstance().isBatch()) { 
				//link agents are used for visualization only.  agents actually decide on deleting connections.
				LinkAgent newLink = new LinkAgent(a1, a2);
				context.add(newLink);
			}
		} else {
			new_link = false;
		}
		return new_link;
	}

	
	private HashMap<String,Object> load_pop_params(Parameters params) {
		HashMap <String,Object> ret = new HashMap <String,Object> ();
		for(String pname : params.getSchema().parameterNames()) {
			ret.put(pname, params.getValue(pname));
		}
		return ret;
	}
	/*
	 * Load the shapefiles for the area
	 */
	private HashMap<String,ZoneAgent> load_zone_agents(HashMap<String,ZoneAgent> mapping, String filename) {
		if(mapping == null) {
			mapping = new HashMap <String, ZoneAgent>();
		}
		URL url = null;
		SimpleFeatureIterator fiter = null;
		ShapefileDataStore store = null;
		try {
			url = new File(filename).toURI().toURL();
			store = new ShapefileDataStore(url);
			fiter = store.getFeatureSource().getFeatures().features();
		} catch (IOException e) {
			e.printStackTrace();
			return mapping;
		}
	
		while(fiter.hasNext()){
			SimpleFeature feature = fiter.next();
			Geometry geom = (Geometry)feature.getDefaultGeometry();
			Object agent = null;
			
			assert geom instanceof MultiPolygon;
			
			MultiPolygon mp = (MultiPolygon)feature.getDefaultGeometry();
			geom = (Polygon)mp.getGeometryN(0);

			String zip_str = (String)feature.getAttribute("ZCTA");
			if(zip_str.length() != 5) {
				System.out.println("Error in format at zip:" + zip_str);
				continue;
			} else {
				//System.out.println(zip_str);
			}
			Polygon polygon = (Polygon)mp.getGeometryN(0);
			assert polygon != null;
			
			ZoneAgent zone = new ZoneAgent(polygon.getCentroid(), polygon, zip_str);
			assert zone != null;
			assign_drug_market(zone);
			
			context.add(zone);
			geography.move(zone, polygon);
	
			mapping.put(zip_str, zone);
		}
		return mapping;
	}
	
	/*
	 * Outputs the locations where classes are found
	 */
	private void reportClassPath() {
		System.out.println("Class path:");
		for(String cname: System.getProperty("java.class.path").split(":")) {			
			System.out.println(cname);
		}
		System.out.println();
		ClassLoader cl = ClassLoader.getSystemClassLoader();
		Enumeration<URL> en_url;
		try {
			en_url = cl.getSystemResources("jar");
			for (URL url; en_url.hasMoreElements(); ) {
				url=en_url.nextElement();
				//for (url : cl.geten_url.hasMoreElements(); url=en_url.nextElement()) {
				System.out.println(url.toString());
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}
	/*
	 * For easier visualization: rotates the GUI globe to be above Chicago
	 */
	public void rotate_globe() {
		if (RunEnvironment.getInstance().isBatch()) {
			return;
		}

		try {
			//geography.
			GUIRegistry gr              = RunState.getInstance().getGUIRegistry();
			List <IDisplay> displays    = gr.getDisplays();
			IDisplay earth_display      = displays.get(0);
			JPanel jp = earth_display.getPanel();

			assert earth_display instanceof DisplayGIS3D;
			WorldWindow ww = ((DisplayGIS3D)earth_display).getWwglCanvas();
			//final WorldWindowGLCanvas wwc     = ((DisplayGIS3D)earth_display).getWwglCanvas();
			LatLon ChicagoLatLong = LatLon.fromDegrees(41.9,-87.85);
			ww.getView().goTo(new Position(ChicagoLatLong, 0), 70e3);  //x,000m above the surface

			RepastEssentials.PauseSimulationRun();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Globe rotation failed on tic: " + RepastEssentials.GetTickCount());
			System.out.println("Recovered!");
		}
	}
	
	/*
	 * report final data
	 */
	public void run_end(Integer run_length) {
		if (run_length == null) {
			return; //no run length
		} else if (run_length.equals(-1)) {  //at the end of the run
			long run_time_in_sec = (System.currentTimeMillis() - this.run_start_time)/1000;
			try {
				/*
				if (! RunEnvironment.getInstance().isBatch()) {
					JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), "The simulation run has ended ...");
				}
				*/
			    //repast.simphony.engine.environment.RunEnvironment.getInstance().endRun();
				for(AgentFactory ib : factories) {
					ib.remove_IDUs();
				}
			    ISchedule schedule  	   = repast.simphony.engine.environment.RunEnvironment.getInstance().getCurrentSchedule();
			    schedule.executeEndActions();
			    RepastEssentials.EndSimulationRun();
			    System.out.println("-----------------------------------------");
			    System.out.printf ("------ Simulation Finished: ticks=%.1f\n", RepastEssentials.GetTickCount());
			    System.out.println("-----------------------------------------");
			    System.out.println("Wall time duration: " + run_time_in_sec + " sec.");
			}
			catch (Exception e) {
			    System.err.println("Error: " + e.getMessage());
			}
			return;
		} else {
			ScheduleParameters sparams = ScheduleParameters.createOneTime(run_length);
			main_schedule.schedule(sparams, this, "run_end", new Integer(-1));
		}
	}
	
	/*
	 * For GUI only, 
	 * 	1. remove LinkAgents which were deleted from the network
	 *  2. other links are reoriented based on the current positions on the agents
	 *  - the LinkAgents are created by do_linking, and not here
	 *  - repast does not automatically delete links from the visualization when they change in the network, so we do it here.
	 *  - this method is public - for scheduling
	 */
	public void update_link_geometries() {
		if (RunEnvironment.getInstance().isBatch()) {
			return;
		}
		ArrayList <LinkAgent> removed_links = new ArrayList <LinkAgent>(); //del
		for(Object obj : context) {
			if (! (obj instanceof LinkAgent)) {
				continue;
			}
			IDU a1 = ((LinkAgent)obj).a1;
			IDU a2 = ((LinkAgent)obj).a2;
			if (! context.contains(a1) || ! context.contains(a2) || ! network.isAdjacent(a1, a2)) {
				removed_links.add((LinkAgent) obj);
			} else {
				((LinkAgent)obj).sync_location();
			}
		}
		context.removeAll(removed_links);
	}
	
	/*
	 * runs daily and recruits PWID for vaccine_trial
	 * 
	 */
	public void vaccine_trial_enroll() {
		if (RepastEssentials.GetTickCount() > vaccine_start_of_enrollment + vaccine_enrollment_duration_days) {
			return; //enrollment has ended
		}

		double vaccine_trial_daily_both_arms_mean = ((double)vaccine_study_arm_n) * 2 / vaccine_enrollment_duration_days; 
		//double todays_total_enrollment = RandomHelper.createPoisson(vaccine_trial_daily_both_arms_mean).nextInt();
		System.out.println("Recruiting: " + vaccine_trial_daily_both_arms_mean);

		boolean enrollment_today = false;
		for(EnrollmentMethodVaccine mthd : EnrollmentMethodVaccine.values()) {
			double enrollment_target = vaccine_trial_daily_both_arms_mean * vaccine_enrollment_probability.get(mthd) + vaccine_residual_enrollment.get(mthd);
			vaccine_residual_enrollment.put(mthd, enrollment_target);  //update.  might be fractional increase 	
			enrollment_today = enrollment_today | enrollment_target >= 1.0; 
		}
		if (! enrollment_today) {
			return; //do nothing.  save time trying to find people.  
		}
		
		ArrayList <IDU> vaccine_candidates = new ArrayList<IDU>(); //rough vaccine candidates
		//wishlist: do a global list to speed up the recruitment.  update this list with newly-arriving PWID only.
		for(ArrayList <IDU> zonePop : zone_population.values()) {
			for(IDU candidate : zonePop) {
				if(candidate.isVaccineTrialSuitable()) {
					vaccine_candidates.add(candidate);
				}
			}
		}
		if(vaccine_candidates.size() == 0) {
			System.out.println("Not enrolling - Enrollable PWIDs: 0");
			return;
		}
		for(EnrollmentMethodVaccine mthd : EnrollmentMethodVaccine.values()) {
			double enrollment_target = vaccine_residual_enrollment.get(mthd);
			if(enrollment_target < 1) {
				//System.out.println("Method: " + mthd + ". Enrolled: " + 0 + ". Residual: " + (enrollment_target - 0));
				continue;
			}
			HashSet<IDU> enrolled = vaccine_trial_select(mthd, vaccine_candidates, Math.ceil(enrollment_target)); //set, to avoid accidentally double-recruiting
			for(IDU idu : enrolled) {
				vaccine_trial_start(idu);
			}
			//wishlist bug fix: make sure that we don't accidentally overshoot vaccine_study_arm_n
			vaccine_residual_enrollment.put(mthd, enrollment_target - enrolled.size()); 	//carried over from day to the next day.  this can give below 0
			//System.out.println("Method: " + mthd + ". Enrolled: " + enrolled.size() + ". Residual: " + vaccine_residual_enrollment.get(mthd));
		}
	}
	
	/*
	 * attempts to recruit for treatment using method enrMethod
	 * graceful operation - does not guarantee success in recruiting the desired number
	 * 
	 */
	public HashSet<IDU> vaccine_trial_select(EnrollmentMethodVaccine enrMethod, ArrayList <IDU> candidates, double enrollment_target) {
		Collections.shuffle(candidates); //shuffle for every method
		HashSet<IDU> enrolled = new HashSet<IDU> (); //set, to avoid accidentally double-recruiting
		if(candidates.size() == 0) {
			return enrolled;
		}
		int next_candidate_idx = 0;
		if(enrMethod == EnrollmentMethodVaccine.unbiased) {
			for(; (enrolled.size() < enrollment_target) && (next_candidate_idx < candidates.size()); ++next_candidate_idx) {
				IDU idu = candidates.get(next_candidate_idx);
				if(idu.isVaccineTrialSuitable()) {
					enrolled.add(idu);
				} else {
					candidates.remove(idu);
				}
			}
		} else if(enrMethod == EnrollmentMethodVaccine.positive_innetwork) {
			for(; (enrolled.size() < enrollment_target) && (next_candidate_idx < candidates.size()); ++next_candidate_idx) {
				IDU idu = candidates.get(next_candidate_idx); //no need to do more shuffling RandomHelper.nextIntFromTo(0, candidates.size()-1));
				//if(! idu.isVaccineTrialSuitable()) {
				//	continue;
				//}
				//enrolled.add(idu);
				if(idu == null) {
					System.out.println(candidates.size());
					int i = 1; //error
				}
				Iterable nbs = null;
				nbs = network.getSuccessors(idu);
				if (nbs == null) {
					continue; //idu removed from context
				}
				for(Object nb : nbs) {
					if ((nb != null) && (nb instanceof IDU)) {
						if (((IDU)nb).isVaccineTrialSuitable()) {
							enrolled.add((IDU)nb);
							break; //only ONE to avoid non-independence of samples
						} else {
							candidates.remove(idu);
						}
					}
				}
			}
		}
		return enrolled;
	}

	
	public void vaccine_trial_start(IDU idu) {
		Immunology.TRIAL_ARM arm = (vaccine_study_enrolled % 2 == 0)? Immunology.TRIAL_ARM.study : Immunology.TRIAL_ARM.placebo; //ensure strict balance
		idu.setCurrent_trial_arm(arm);
		System.out.println("IDU: " + idu.getSimID() + " Arm:" + arm.toString());
		vaccine_study_enrolled++;  //deterministic to maximize balance

		vaccine_trial_advance(idu, VACCINE_STAGE.received_dose1, 0);

		double time_to_abandon = RepastEssentials.GetTickCount() + 365*RandomHelper.createExponential(vaccine_annual_loss).nextDouble();
		ScheduleParameters p = ScheduleParameters.createOneTime(time_to_abandon, -1);
		main_schedule.schedule(p, this, "vaccine_trial_advance", idu, VACCINE_STAGE.abandoned, 0);
	}
	
	/*
	 * called when the IDU has arrived to the time of new_stage
	 */
	public void vaccine_trial_advance(IDU idu, VACCINE_STAGE new_stage, Integer remaining_followups) {
		if(idu.getCurrent_trial_stage() == VACCINE_STAGE.abandoned) {
			return;
		}
		
		VACCINE_STAGE next_stage = VACCINE_STAGE.notenrolled;	
		boolean completed_doses = false;
		switch(new_stage) {
			case received_dose1:
				idu.receiveVaccineDose(); //updates the stage
				next_stage = VACCINE_STAGE.received_dose2;
				Statistics.fire_status_change(AgentMessage.trialstarted, idu, "arm="+idu.getCurrent_trial_arm(), null);
				Statistics.fire_status_change(AgentMessage.vaccinated, idu, "newstage="+next_stage.toString()+";arm="+idu.getCurrent_trial_arm(), null);
				if(vaccine_total_doses == 1) {
					completed_doses = true; //see below
				}
				break;
			case received_dose2:
				idu.receiveVaccineDose(); //updates the stage
				next_stage = VACCINE_STAGE.received_dose3;
				Statistics.fire_status_change(AgentMessage.vaccinated, idu, "newstage="+next_stage.toString()+";arm="+idu.getCurrent_trial_arm(), null);
				if(vaccine_total_doses == 2) {
					completed_doses = true; //see below
				}
				break;
			case received_dose3:
				idu.receiveVaccineDose(); //updates the stage
				next_stage = VACCINE_STAGE.followup;
				Statistics.fire_status_change(AgentMessage.vaccinated, idu, "newstage="+next_stage.toString()+";arm="+idu.getCurrent_trial_arm(), null);
				completed_doses = true; //see below
				break;
			case followup: //after the first or subsequently follow-up month
				if (idu.isHcvRNA()){
					next_stage = VACCINE_STAGE.followup2;
					idu.setVaccine_stage(next_stage);
					remaining_followups = vaccine_followup2_periods - 1;
					Statistics.fire_status_change(AgentMessage.infollowup2, idu, "RNA="+idu.isHcvRNA()+";arm="+idu.getCurrent_trial_arm(), null);
				} else if (remaining_followups > 0 ) {
					next_stage = VACCINE_STAGE.followup;
					Statistics.fire_status_change(AgentMessage.infollowup, idu, "RNA="+idu.isHcvRNA()+";arm="+idu.getCurrent_trial_arm(), null);
					remaining_followups -= 1;
				} else {
					next_stage = VACCINE_STAGE.completed;
					idu.setVaccine_stage(next_stage);
					Statistics.fire_status_change(AgentMessage.trialcompleted, idu, "RNA="+idu.isHcvRNA()+";arm="+idu.getCurrent_trial_arm(), null);
				}
				break;
			case followup2:
 				if (remaining_followups > 0) { //still infected
					remaining_followups -= 1;
					next_stage = VACCINE_STAGE.followup2;
					idu.setVaccine_stage(next_stage);
					Statistics.fire_status_change(AgentMessage.infollowup2, idu, "RNA="+idu.isHcvRNA()+";arm="+idu.getCurrent_trial_arm(), null);
				} else {
					next_stage = VACCINE_STAGE.completed;
					idu.setVaccine_stage(next_stage);
					Statistics.fire_status_change(AgentMessage.trialcompleted, idu, "RNA="+idu.isHcvRNA()+";arm="+idu.getCurrent_trial_arm(), null);
				}
				break;
			case abandoned:
				if (idu.getCurrent_trial_stage() == VACCINE_STAGE.completed) {
					next_stage = VACCINE_STAGE.completed; //stay completed
				} else {
					next_stage = VACCINE_STAGE.abandoned;
					idu.setVaccine_stage(next_stage);
					Statistics.fire_status_change(AgentMessage.trialabandoned, idu, "RNA="+idu.isHcvRNA()+";arm="+idu.getCurrent_trial_arm(), null);
				}
				break;
			case completed: //end of story.
				next_stage = VACCINE_STAGE.completed;
				break;
			case notenrolled:
				System.err.println("error in vaccine trial: incorrect action (not enrolled)");
				assert false; //shouldn't cause a call of this method
				break;
			default:
				System.err.println("error in vaccine trial: incorrect action" + new_stage);
				assert false;
				break;
		}
		if (next_stage == VACCINE_STAGE.notenrolled) {
			System.err.println("error in vaccine trial: incorrect action (next stage is notenrolled)");
			assert false; //shouldn't reach this point
			return;
		}
		
		if (completed_doses) {
			if (idu.isHcvRNA() && vaccine_followup_purge_with_rna) {
				next_stage = VACCINE_STAGE.abandoned; //wishlist: change to special purged
				idu.setVaccine_stage(next_stage);
				Statistics.fire_status_change(AgentMessage.trialpurged, idu, "RNA="+idu.isHcvRNA()+";arm="+idu.getCurrent_trial_arm(), null);
			} else {
				next_stage = VACCINE_STAGE.followup;
				idu.setVaccine_stage(next_stage);
				remaining_followups = vaccine_followup1_periods - 1;
				Statistics.fire_status_change(AgentMessage.enteredfollowup, idu, "RNA="+idu.isHcvRNA()+";arm="+idu.getCurrent_trial_arm(), null);
			}
	
		}
		double next_event_time = Double.NaN;
		switch(next_stage) {
			case notenrolled:
				System.err.println("Error in programming - shouldn't switch to notenrolled stage!");;
				break;
			case received_dose2:
				next_event_time = RepastEssentials.GetTickCount() + vaccine_dose2_day;
				break;
			case received_dose3:
				next_event_time = RepastEssentials.GetTickCount() + vaccine_dose3_day - vaccine_dose2_day; //because counted from first dose
				break;
			case followup:
				next_event_time = RepastEssentials.GetTickCount() + vaccine_followup_weeks*7;
				break;
			case followup2:
				next_event_time = RepastEssentials.GetTickCount() + vaccine_followup_weeks*7;
				break;
			case abandoned:
				break;
			case completed:
				break;
			default:
				break;
		}
		if (! Double.isNaN(next_event_time)) {
			ScheduleParameters p = ScheduleParameters.createOneTime(next_event_time, -1);
			main_schedule.schedule(p, this, "vaccine_trial_advance", idu, next_stage, remaining_followups);
		}
	}
}
