/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import org.joda.time.*;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.swing.JPanel;

import repast.simphony.context.Context;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.context.space.gis.GeographyFactoryFinder;
import repast.simphony.engine.environment.GUIRegistry;
import repast.simphony.engine.environment.RunState;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.space.gis.Geography;
import repast.simphony.space.gis.GeographyParameters;
import repast.simphony.space.graph.Network;
import repast.simphony.visualization.IDisplay;
import repast.simphony.visualization.gis3D.DisplayGIS3D;

import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.essentials.RepastEssentials;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Earth;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.jscience.physics.amount.Amount;

import cern.jet.random.Exponential;

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

	static final double market_lons[]   = {-87.74469, -87.66458, }; //centered about the Westside station
	static final double market_lats[]   = {41.88052,  41.77946,  }; //centered about the Southside station

	/* COIP NEP field stations
	 * Northside Field Station 		-87.65526,41.96201
	 * Northwest Side Field Station -87.70684,41.91056
	 * Westside Field Station 		-87.74469,41.88052
	 * Southside Field Station 		-87.66458,41.77946
	 * Southeastside Field Station  -87.55171,41.73325
	 */ 
	
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

		zip_to_zones    = load_zone_agents(zip_to_zones, "gisdata/illinois_zips/zt17_d00.shp");
		zip_to_zones    = load_zone_agents(zip_to_zones, "gisdata/michigan_zips/zt26_d00.shp");
		zip_to_zones    = load_zone_agents(zip_to_zones, "gisdata/indiana_zips/zt18_d00.shp");
		zip_to_zones    = load_zone_agents(zip_to_zones, "gisdata/wisconsin_zips/zt55_d00.shp");

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

		Statistics.build(params, context, network, zip_to_zones, zone_zone_distance);
		Statistics.dump_network_distances(params.getValue("dump_network_distances"));

		//load the data for the factories
		HashMap <String,Object> population_params  = load_pop_params(params);
		HashMap <String,Object> extra_params    	= new HashMap <String,Object> ();
		extra_params.put("zip_to_zones", zip_to_zones);
		
		
		IDUbuilder1 factory1 = new IDUbuilder1(context, population_params, extra_params);
		factory1.generate_initial();
		//factory1.systematic_NEP_synthetic(); //special testing code
		
		main_schedule.schedule(ScheduleParameters.createOneTime(-0.2),        				   this, "rotate_globe");
		main_schedule.schedule(ScheduleParameters.createRepeating(1, 1, 0), 				   this, "do_zone_census");
		main_schedule.schedule(ScheduleParameters.createRepeating(1, linking_time_window, -1), this, "do_linking", 0.0); 		//"-1" to ensure after the census
		main_schedule.schedule(ScheduleParameters.createRepeating(0, 1, -2), 				   this, "update_link_geometries");

		do_initial_linking();
		//schedule.schedule(ScheduleParameters.createRepeating(1, 1), this, "update_display");
			
		return context;
	}
	
	/*
	 * Each zone is given a unique drug market based on the closest geographic place
	 */
	private void assign_drug_market(ZoneAgent zone) {
		Coordinate z_center = zone.getCentroid().getCoordinate();
		LatLon idu_loc      = new LatLon(Angle.fromDegrees(z_center.y), Angle.fromDegrees(z_center.x));
		int closest_market  = -1;
		double closest_d   = Double.MAX_VALUE;
		for (int market_index=0; market_index < market_lons.length; ++market_index) {
			LatLon market_loc = new LatLon(Angle.fromDegrees(market_lats[market_index]), Angle.fromDegrees(market_lons[market_index]));
			double d = LatLon.ellipsoidalDistance(idu_loc, market_loc, Earth.WGS84_EQUATORIAL_RADIUS, Earth.WGS84_POLAR_RADIUS);
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
	 */
	public void burn_in_control(Double burn_in_days) {
		if(burn_in_days <= 0) {
			burn_in_mode = false;
			return;
		}
		Statistics.setBurnInMode(true);
		IDUbuilder1.setBurn_in_period(true, burn_in_days);
		
		main_schedule.schedule(ScheduleParameters.createOneTime(RepastEssentials.GetTickCount() + burn_in_days), this, "burn_in_end", burn_in_days);
	}
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
	 * Determine how many IDUs in each zones are available to form new connections
	 * - the census is stored in zone_population (all) and effective_zone_population (only those that can form new connections)
	 * - typically occurs once a day (linking_time_window)
	 */
	public void do_zone_census() {
		//System.out.print("Census... ");
		zone_population			  = new HashMap <ZoneAgent,ArrayList<IDU>> ();
		effective_zone_population = new HashMap <ZoneAgent,LinkedList<IDU>> ();
		for(Object obj : context) {
			if(obj instanceof IDU) {
				IDU agent = (IDU) obj;
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
		LatLon p1 = new LatLon(Angle.fromDegrees(c1.y), Angle.fromDegrees(c1.x));
		
		Coordinate c2 = zone2.getCentroid().getCoordinate();
		LatLon p2 = new LatLon(Angle.fromDegrees(c2.y), Angle.fromDegrees(c2.x));

		//returns the distance in meters
		return 0.001 * LatLon.ellipsoidalDistance(p1, p2, Earth.WGS84_EQUATORIAL_RADIUS, Earth.WGS84_POLAR_RADIUS);
	}

	/*
	 * A simple pre-computed test of the distance function
	 * - used in testing only
	 */
	protected static void getDistanceTest() {
		LatLon p1 = new LatLon(Angle.fromDegrees(41.869057), Angle.fromDegrees(-87.66736));  //UIC-SPH
		LatLon p2 = new LatLon(Angle.fromDegrees(41.8702), Angle.fromDegrees(-87.650217));   //UIC recreation facility

		double dis = 0.001 * LatLon.ellipsoidalDistance(p1, p2, Earth.WGS84_EQUATORIAL_RADIUS, Earth.WGS84_POLAR_RADIUS);
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
			    System.out.println("Wall time duration: " + run_time_in_sec);
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
}
