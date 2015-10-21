/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import java.io.File;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import org.joda.time.*;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import edu.uic.apk.IDU.AgeDecade;
import edu.uic.apk.IDU.AgeGroup;
import edu.uic.apk.IDU.AreaType;
import edu.uic.apkSynth.Gender;
import edu.uic.apkSynth.HCV_state;
import edu.uic.apkSynth.Race;
import edu.uic.apkSynth.HarmReduction;
import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunState;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.PriorityType;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.essentials.RepastEssentials;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;



/*
 * Main statistical class
 * - collects information on a daily, yearly and event-levels
 * - note: we assume throughout that a year corresponds to 365 tics.  This more round number is just a little short, but reduces rounding errors.
 */
public class Statistics {
	//need singleton rather than static methods 
	//1. to use the schedule.schedule() methods, and the @Schedule annotation
	//2. catch bugs: after initialization, singleton != null
	private static Statistics singleton; 
	private static Context context;
	private static Network network;
	private static HashMap <String, ZoneAgent> zip_to_zones; //we don't use it now, but might need it in the future
	private static HashMap <ZoneAgent,HashMap<ZoneAgent,Double>> zone_zone_distance;
	
	private static Hashtable <String, Vector <Double>> runningStats; 
	private static Hashtable <String, Double> avgStats;
	private static Hashtable <String, Double> initialStats;
	private static Hashtable <String, Double> finalStats;

	private static long runLength;
	private static String SimName = "Simulation " + (new Date()).toString();
	private static String outputDirectory;

	private static String fileSep = System.getProperty("file.separator");
	private static String lineSep = System.getProperty("line.separator");

	private static PrintStream staticOutStream = null;
	private static PrintStream popStatsStream;
	private static PrintStream eventsStream;
	private static PrintStream regularStatusStream;
	private static PrintStream statusStream;
	
	private static final double daily_stats_timing = 1.001;  //should be near 1.  
	private static Parameters params;
	private static int aggregate_courses = 0; 
	private static int aggregate_treated = 0; 
	//probably unnecessary: private static TreeSet<Integer> treated_hashes = new TreeSet <Integer>();

	private static int activations_ALL = 0;    //daily losses (attrition, retirement, etc). updated during fire_status_change
	private static int incidence_ALL = 0; //daily incidence. updated during fire_status_change
	private static int losses_ALL = 0;    //daily losses (attrition, retirement, etc). updated during fire_status_change
	private static int recruitments_ALL = 0;    //recruitment into treatment. updated during fire_status_change
	private static ArrayList <String> runtimeStatNames;
	private static boolean burn_in_mode = false;

	private Statistics() {		
	}
	
	public static void build(Parameters _params, Context _context, Network _network, HashMap <String, ZoneAgent> _zip_to_zones, HashMap <ZoneAgent,HashMap<ZoneAgent,Double>> _zone_zone_distance) {
		singleton = new Statistics();
		
		//needed to be recognized as a DataSource.  automatically schedules any annotated objects
		_context.add(singleton);  
		build_helper(_params, _context, _network, _zip_to_zones, _zone_zone_distance);
	}
	public static void setBurnInMode(boolean b) {
		burn_in_mode = b;
	}
	
	private static void build_helper(Parameters _params, Context _context, Network _network, HashMap <String, ZoneAgent> _zip_to_zones, HashMap <ZoneAgent,HashMap<ZoneAgent,Double>> _zone_zone_distance) {
		context = _context;
		network = _network;
		params  = _params;
		zip_to_zones = _zip_to_zones;
		zone_zone_distance = _zone_zone_distance;
		
		runtimeStatNames = new ArrayList<String>();
		runtimeStatNames.add("activations_ALL");
		runtimeStatNames.add("losses_ALL");
		runtimeStatNames.add("incidence_ALL");
		runtimeStatNames.add("recruitments_ALL");
		runtimeStatNames.add("mean-age_ALL");
		runtimeStatNames.add("mean-career_ALL");
		runtimeStatNames.add("mean-dailyinj_ALL");
		runtimeStatNames.add("mean-indeg_ALL");
		runtimeStatNames.add("mean-outdeg_ALL");
		runtimeStatNames.add("mean-sharing_ALL");
		runtimeStatNames.add("fraction_ALL"); //will always = 1.0
		runtimeStatNames.add("hcvabpos_ALL");
		runtimeStatNames.add("infected_ALL");
		runtimeStatNames.add("population_ALL");
		runtimeStatNames.add("prevalence_ALL");
		runtimeStatNames.add("intreatment_ALL");
		runtimeStatNames.add("treated_ALL");
		runtimeStatNames.add("treatments_ALL");

		for (Gender g : Gender.values()) {
			runtimeStatNames.add("hcvabpos_Gender=" + g.toString());			
			runtimeStatNames.add("fraction_Gender=" + g.toString());			
			runtimeStatNames.add("infected_Gender=" + g.toString());			
			runtimeStatNames.add("population_Gender=" + g.toString());
			runtimeStatNames.add("prevalence_Gender=" + g.toString());			
			runtimeStatNames.add("treated_Gender=" + g.toString());			
		}
		for (HCV_state s : HCV_state.values()) {
			runtimeStatNames.add("hcvabpos_HCV=" + s.toString());			
			runtimeStatNames.add("fraction_HCV=" + s.toString());			
			runtimeStatNames.add("infected_HCV=" + s.toString());			
			runtimeStatNames.add("population_HCV=" + s.toString());
			runtimeStatNames.add("prevalence_HCV=" + s.toString());			
			runtimeStatNames.add("treated_HCV=" + s.toString());			
		}
		for (Race r : Race.values()) {
			runtimeStatNames.add("fraction_Race=" + r.toString());
			runtimeStatNames.add("hcvabpos_Race=" + r.toString());
			runtimeStatNames.add("infected_Race=" + r.toString());
			runtimeStatNames.add("population_Race=" + r.toString());
			runtimeStatNames.add("prevalence_Race=" + r.toString());			
			runtimeStatNames.add("treated_Race=" + r.toString());			
		}
		for (HarmReduction syrsrc : HarmReduction.values()) {
			runtimeStatNames.add("fraction_SyringeSource=" + syrsrc.toString());
			runtimeStatNames.add("hcvabpos_SyringeSource=" + syrsrc.toString());
			runtimeStatNames.add("infected_SyringeSource=" + syrsrc.toString());
			runtimeStatNames.add("population_SyringeSource=" + syrsrc.toString());
			runtimeStatNames.add("prevalence_SyringeSource=" + syrsrc.toString());
			runtimeStatNames.add("treated_SyringeSource=" + syrsrc.toString());			
		}
		for (IDU.AgeDecade age_dec : IDU.AgeDecade.values()) {
			runtimeStatNames.add("fraction_AgeDec=" + age_dec);
			runtimeStatNames.add("hcvabpos_AgeDec=" + age_dec);
			runtimeStatNames.add("infected_AgeDec=" + age_dec);
			runtimeStatNames.add("population_AgeDec=" + age_dec);
			runtimeStatNames.add("prevalence_AgeDec=" + age_dec);
			runtimeStatNames.add("treated_AgeDec=" + age_dec);
		}
		for (IDU.AgeGroup age_grp : IDU.AgeGroup.values()) {
			runtimeStatNames.add("fraction_Age=" + age_grp);
			runtimeStatNames.add("hcvabpos_Age=" + age_grp);
			runtimeStatNames.add("infected_Age=" + age_grp);
			runtimeStatNames.add("population_Age=" + age_grp);
			runtimeStatNames.add("prevalence_Age=" + age_grp);
			runtimeStatNames.add("treated_Age=" + age_grp);
		}
		for (IDU.AreaType area_cat : IDU.AreaType.values()) {
			runtimeStatNames.add("fraction_Area=" + area_cat);
			runtimeStatNames.add("hcvabpos_Area=" + area_cat);
			runtimeStatNames.add("infected_Area=" + area_cat);
			runtimeStatNames.add("population_Area=" + area_cat);
			runtimeStatNames.add("prevalence_Area=" + area_cat);
			runtimeStatNames.add("treated_Area=" + area_cat);
		}

		System.out.printf("Random seed: %d"+lineSep+lineSep, RandomHelper.getSeed());
		runLength = Long.parseLong(params.getValueAsString("run_length"));
		try {
			//InputStream class_stream = this.getClass().getResource("edu.uic.apk.APKBuilder");
			//Class this_class = Class.forName("APKBuilder");
			File file = new java.io.File("bin/edu/uic/apk/APKBuilder.class");
			if (! file.exists()) {
				System.out.println("tried to find: " + file.getAbsolutePath());
			} else {
				System.out.printf("APK Version (from modification signature for %s):\n  %s"+lineSep+lineSep, file.getAbsoluteFile(), (new DateTime(file.lastModified())).toString() );
			}
		} catch (Exception e){}
			
		outputDirectory = params.getValueAsString("output_directory");
		if (outputDirectory == null) {
			outputDirectory = System.getProperty("java.io.tmpdir");
		}
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd--hh.mm.ss");
		Date currentTime = new Date();
		outputDirectory = outputDirectory + fileSep + formatter.format(currentTime) + "-" + RandomHelper.nextIntFromTo(0, Integer.MAX_VALUE);
        setOutputDirectory(outputDirectory);
		
		File outDir = new File(outputDirectory);
		System.out.println("Initializing output.  Path for output files: " + outDir.getAbsolutePath());

		String rawStatsFileDir = "";
		String rawStatsFileName = "";
		try {
			rawStatsFileDir = outputDirectory+fileSep;
			File file = new java.io.File(rawStatsFileDir);
			if (! file.exists()) {
				file.mkdirs();
			}
			rawStatsFileName = rawStatsFileDir+formatter.format(currentTime)+".populations.csv";
			popStatsStream = new PrintStream(rawStatsFileName); 
			System.out.println((new File(rawStatsFileName)).getAbsolutePath());

			String eventsFileName = rawStatsFileDir+formatter.format(currentTime)+".events.csv";
			eventsStream = new PrintStream(eventsFileName); 
			System.out.println((new File(eventsFileName)).getAbsolutePath());
			
			String statusFileName = rawStatsFileDir+formatter.format(currentTime)+".status.csv";
			statusStream = new PrintStream(statusFileName); 
			System.out.println((new File(statusFileName)).getAbsolutePath());

			String regularStatusFileName = rawStatsFileDir+formatter.format(currentTime)+".statusRegular.csv";
			regularStatusStream = new PrintStream(regularStatusFileName); 
			System.out.println((new File(regularStatusFileName)).getAbsolutePath());
			
			
			//line 1: the name of the simulation
			popStatsStream.printf("%s,,,,"+lineSep, SimName);
			eventsStream.printf("%s,,,,"+lineSep, SimName);
			statusStream.printf("%s,,,,"+lineSep, SimName);
			regularStatusStream.printf("%s,,,,"+lineSep, SimName);

			//line 2&3: param names and values
			String paramNames = new String();
			String paramVals  = new String();
			for (String paramName : sortedParamNames()) {
				paramNames += paramName + ",";
				paramVals  += params.getValueAsString(paramName) + ",";
			}
			paramNames += lineSep;
			paramVals += lineSep;

			popStatsStream.printf(paramNames);
			popStatsStream.printf(paramVals);
			eventsStream.printf(paramNames);
			eventsStream.printf(paramVals);
			statusStream.printf(paramNames);
			statusStream.printf(paramVals);
			regularStatusStream.printf(paramNames);
			regularStatusStream.printf(paramVals);

			//line 4: blank
			popStatsStream.printf(lineSep);

			//line 5: header of the stats
			popStatsStream.printf("BurnInMode,Tick,");
			for (int i=0; i<runtimeStatNames.size(); ++i) {
				popStatsStream.printf("%s,",runtimeStatNames.get(i));
			}
			popStatsStream.printf(lineSep);
                        //wishlist: add the parameter file name

			//insert header into the events and status files
			fire_status_change(null, null, null, null);

		} catch (Exception ex) {
			System.out.println("Failed to initialize output streams:");
			System.out.println("Raw output dir: " + rawStatsFileDir);
			System.out.println("Raw output filename: " + rawStatsFileName);
			ex.printStackTrace();
			System.exit(42);    	
			//rawStatsStream.close();
		} 

		runningStats = new Hashtable<String, Vector<Double>>();
		for (int i=0; i<runtimeStatNames.size(); ++i) {
			runningStats.put(runtimeStatNames.get(i), new Vector<Double>());
		}
		for (Iterator it = runningStats.values().iterator(); it.hasNext(); ) {
			Vector<Double> ar = (Vector<Double>) it.next();
			ar.ensureCapacity((int)runLength+1);
		}

	}
	
	public Hashtable <String,Double> collect_stats() {
		Hashtable <String,Double> currentData = new Hashtable <String,Double> (); 
		for(String k : runtimeStatNames) {
			currentData.put(k, 0.0);
		}
		for(Object obj: context.getAgentLayer(IDU.class)) {
			IDU agent = (IDU) obj;
			String gender = "Gender="+agent.getGender_();
			String hcvstate = "HCV="+agent.getHcvState();
			String race   = "Race="+agent.getRace_();
			String syrsrc = "SyringeSource="+agent.getSyringe_source();
			String agegrp  = "Age="+(agent.getAgeGroup());
			String agedec  = "AgeDec="+(agent.getAgeDecade());
			String areatype = "Area="+agent.getAreaType();
			
			currentData.put("mean-age_ALL",     currentData.get("mean-age_ALL")+agent.getAge());  //divided later
			currentData.put("mean-career_ALL",  currentData.get("mean-career_ALL")+agent.getAge()-agent.getAgeStarted());  //divided later
			currentData.put("mean-dailyinj_ALL",currentData.get("mean-dailyinj_ALL")+agent.getInjectionIntensity());  //divided later
			currentData.put("mean-indeg_ALL",   currentData.get("mean-indeg_ALL")+agent.getDrugReceptDegree());  //divided later
			currentData.put("mean-outdeg_ALL",  currentData.get("mean-outdeg_ALL")+agent.getDrugGivingDegree());  //divided later
			currentData.put("mean-sharing_ALL", currentData.get("mean-sharing_ALL")+agent.getFractionReceptSharing());  //divided later

			agent.isInTreatment();
			currentData.put("intreatment_ALL", currentData.get("intreatment_ALL")+(agent.isInTreatment()?1:0));
			
			currentData.put("population_ALL",     currentData.get("population_ALL")+1);
			currentData.put("population_"+gender, currentData.get("population_"+gender)+1);
			currentData.put("population_"+hcvstate, currentData.get("population_"+hcvstate)+1);
			currentData.put("population_"+race,   currentData.get("population_"+race)+1);
			currentData.put("population_"+syrsrc, currentData.get("population_"+syrsrc)+1);
			currentData.put("population_"+agedec,  currentData.get("population_"+agedec)+1);
			currentData.put("population_"+agegrp,  currentData.get("population_"+agegrp)+1);
			currentData.put("population_"+areatype,   currentData.get("population_"+areatype)+1);
			if(agent.isHcvRNA()) {
				currentData.put("infected_ALL",     currentData.get("infected_ALL") + 1);
				currentData.put("infected_"+gender, currentData.get("infected_"+gender)+1);
				currentData.put("infected_"+hcvstate, currentData.get("infected_"+hcvstate)+1);
				currentData.put("infected_"+race, 	currentData.get("infected_"+race)+1);
				currentData.put("infected_"+syrsrc, currentData.get("infected_"+syrsrc)+1);
				currentData.put("infected_"+agedec,  currentData.get("infected_"+agedec)+1);
				currentData.put("infected_"+agegrp,  currentData.get("infected_"+agegrp)+1);
				currentData.put("infected_"+areatype,   currentData.get("infected_"+areatype)+1);
			}
			if(agent.isHcvABpos()) {
				currentData.put("hcvabpos_ALL",     currentData.get("hcvabpos_ALL") + 1);
				currentData.put("hcvabpos_"+gender, currentData.get("hcvabpos_"+gender)+1);
				currentData.put("hcvabpos_"+hcvstate, currentData.get("hcvabpos_"+hcvstate)+1);
				currentData.put("hcvabpos_"+race, 	currentData.get("hcvabpos_"+race)+1);
				currentData.put("hcvabpos_"+syrsrc, currentData.get("hcvabpos_"+syrsrc)+1);
				currentData.put("hcvabpos_"+agedec,  currentData.get("hcvabpos_"+agedec)+1);
				currentData.put("hcvabpos_"+agegrp,  currentData.get("hcvabpos_"+agegrp)+1);
				currentData.put("hcvabpos_"+areatype,   currentData.get("hcvabpos_"+areatype)+1);
			}
			if(agent.isInTreatment() || agent.isCured()) {
				currentData.put("treated_ALL",       currentData.get("treated_ALL") + 1);
				currentData.put("treated_"+gender,   currentData.get("treated_"+gender)+1);
				currentData.put("treated_"+hcvstate, currentData.get("treated_"+hcvstate)+1);
				currentData.put("treated_"+race, 	 currentData.get("treated_"+race)+1);
				currentData.put("treated_"+syrsrc,   currentData.get("treated_"+syrsrc)+1);
				currentData.put("treated_"+agedec,   currentData.get("treated_"+agedec)+1);
				currentData.put("treated_"+agegrp,   currentData.get("treated_"+agegrp)+1);
				currentData.put("treated_"+areatype, currentData.get("treated_"+areatype)+1);
			}
		}
		Double total_population = Math.max(1.0, currentData.get("population_ALL"));
		currentData.put("mean-age_ALL",     currentData.get("mean-age_ALL")/total_population);
		currentData.put("mean-career_ALL",  currentData.get("mean-career_ALL")/total_population);
		currentData.put("mean-dailyinj_ALL",currentData.get("mean-dailyinj_ALL")/total_population);
		currentData.put("mean-indeg_ALL",   currentData.get("mean-indeg_ALL")/total_population);
		currentData.put("mean-outdeg_ALL",  currentData.get("mean-outdeg_ALL")/total_population);
		currentData.put("mean-sharing_ALL", currentData.get("mean-sharing_ALL")/total_population);
	
		for (String metric : currentData.keySet()) {
			if(! metric.startsWith("population")) {
				continue;
			}
			String group_name  = metric.split("_")[1];
			Double population  = currentData.get(metric);
			Double hcvabpos    = currentData.get("hcvabpos_"+group_name);
			if (population != null && population > 0) {
				currentData.put("prevalence_"+group_name, hcvabpos/population);
			} else {
				currentData.put("prevalence_"+group_name, Double.NaN);
			}
			currentData.put("fraction_"+group_name, population/total_population);
		}

		//we collect these separately when the events arrive
		currentData.put("activations_ALL", new Double(Statistics.activations_ALL));
		currentData.put("losses_ALL",      new Double(Statistics.losses_ALL));
		currentData.put("incidence_ALL",   new Double(Statistics.incidence_ALL));
		currentData.put("recruitments_ALL",new Double(Statistics.recruitments_ALL));		
		currentData.put("treated_ALL",     new Double(aggregate_treated));
		currentData.put("treatments_ALL",  new Double(aggregate_courses));
		
		return currentData;
	}

	private double computeAverage(Vector <Double> data, Integer burnin) {
		if(burnin == null) {
			burnin = 0;
		}
		if (data == null || data.size() == 0) {
			System.err.println("Warning: average for NaN b/c of missing data");
			return Double.NaN;  
		}
		double avg = 0;
		for(int i=burnin; i<data.size(); ++i) {
			avg += data.get(i).doubleValue();
		}
		return avg/data.size();
	}
	
	/*
	 * number of agents removed in the current tick
	 */
	public static int daily_losses() {
		assert singleton != null;
		return Statistics.losses_ALL;
	}

	/*
	 * If time >= 0 but not the flag time, schedule the dump
	 * If time == flag_time, dump the network
	 */
	public static void dump_network_distances(Object time) {
		final int flag_time = 123123123;
		assert singleton != null;
		Integer time_val = (Integer)time;
		if (time_val != flag_time) {  //schedule the dump
			if (time_val >= 0) {
				ScheduleParameters sparams = ScheduleParameters.createOneTime(time_val);
			    ISchedule schedule  	   = repast.simphony.engine.environment.RunEnvironment.getInstance().getCurrentSchedule();
				schedule.schedule(sparams, singleton, "dump_network_distances", flag_time );
				return;
			} else {
				return; //will not do a dump
			}
		}
		
		PrintStream networkOutStream = null;
		SimpleDateFormat formatter = new SimpleDateFormat ("yyyy-MM-dd--hh.mm.ss");
		Date currentTime = new Date();
		String net_data_path = singleton.outputDirectory+singleton.fileSep+formatter.format(currentTime)+".network.csv";
		try {
		    System.out.print("Saving network to file ... ");
			networkOutStream = new PrintStream(net_data_path); 
			
			//line 1: the name of the simulation
			networkOutStream.printf("#,%s,,,,"+lineSep, SimName);

			//line 2&3: param names and values
			String paramNames = new String();
			String paramVals  = new String();
			for (String paramName : sortedParamNames()) {
				paramNames += paramName + ",";
				paramVals  += params.getValueAsString(paramName) + ",";
			}
			paramNames += lineSep;
			paramVals += lineSep;

			networkOutStream.printf("#,"+paramNames);
			networkOutStream.printf("#,"+paramVals);

			//line 4: blank
			networkOutStream.printf("#,"+lineSep);

			//line 5: network header
			int total_idu = 0;
			for(Object obj : singleton.network.getNodes()) {
				if(obj instanceof IDU) {
					total_idu ++;
				}
			}
			networkOutStream.println("#,"+"Network snapshot,IDU_Nodes=,"+total_idu);
			String a1header = IDU.toString_header()+","; //added "," for the next line
			a1header = a1header.replace(",", "1,");
			String a2header = IDU.toString_header()+","; //added "," for the next line
			a2header = a2header.replace(",", "2,");
			networkOutStream.println("Agent1,Agent2,Zip1,Zip2,distance(km)," + a1header + a2header);
			for (Object edge_obj : singleton.network.getEdges()) {
				RepastEdge edge = (RepastEdge) edge_obj;
				IDU agent1 = (IDU) edge.getSource();
				IDU agent2 = (IDU) edge.getTarget();
				ZoneAgent zone1 = agent1.getZone();
				ZoneAgent zone2 = agent2.getZone();
				//if(agent1.getAge() > 30 | agent2.getAge() > 30) {
				//	continue;
				//}
				Double dis = singleton.zone_zone_distance.get(zone1).get(zone2);
				if(dis == null) {
					dis = APKBuilder.getDistance(zone1,zone2);
					singleton.zone_zone_distance.get(zone1).put(zone2, dis);
				}						
				networkOutStream.printf("%d,%d,%s,%s,%.3f,%s,%s"+lineSep, agent1.hashCode(), agent2.hashCode(), agent1.getZip(), agent2.getZip(), dis, agent1.toString(), agent2.toString());
			}
			System.out.println("Written: "+net_data_path);
		}
		catch (Exception e) {
		    System.err.println("Error: " + e.getMessage());
		}
	    if (networkOutStream!=null) {
	    	networkOutStream.close();
	    }
		return;
	}

	public void finalize_stats()	{
	}

	public static void fire_status_change(AgentMessage message, IDU agent, String message_info, HashMap details) {
		//Note: make sure that HCV state is updated before calling this message
		//EventClass is currently just D=disease.  potentially could help presort events based on EventClass - one parser per class
		assert singleton != null;
		String eventClass = "D"; //for now
		//return; 
		if(agent==null) {
			eventsStream.printf("NOTE: we don't record failed exposure events or successful exposures during acute or chronic HCV (for space reasons)" + lineSep);
			eventsStream.printf("Time,EventClass,Agent,Event,Info,L1,N1,L2,N2,,");
			eventsStream.printf(IDU.toString_header());
			eventsStream.printf(lineSep);

			statusStream.printf("Time,EventClass,Agent,Event,Info,L1,N1,L2,N2,,");
			statusStream.printf(IDU.toString_header());
			statusStream.printf(lineSep);

			regularStatusStream.printf("Time,EventClass,Agent,Event,Info,L1,N1,L2,N2,,");
			regularStatusStream.printf(IDU.toString_header());
			regularStatusStream.printf(lineSep);
			return;
		}

		if(burn_in_mode) {
			return;
		}
		if(! context.contains(agent)) {
			//System.err.println("Warning: attempted event on agent not in context: " + agent.hashCode());
			return;
		}
		double time_now = APKBuilder.getDateDifference(APKBuilder.getSimulationDate(), APKBuilder.getSimulation_start_date()); //RunEnvironment.getInstance().getCurrentSchedule().getTickCount()
		
		fire_aggregatehelper(time_now, agent, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
		
		switch(message) {
			case activated:
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				Statistics.activations_ALL += 1;
				break;
			case cured:
			case failed_treatment:
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				break;
			case chronic:
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				break;
			case deactivated:
				Statistics.losses_ALL += 1;
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				break;
			case exposed:
				break; //not recorded - too many events
			case infected:
				boolean is_agents_first_exposure = (agent.getLastExposureDate().getYear() < 1900);
				if (is_agents_first_exposure || agent.isPostTreatment()) {
					//warning: this does not consider the burn-in time
					double time_to_exposure = APKBuilder.getDateDifference(APKBuilder.getSimulationDate(), agent.getEntryDate());					
					fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "Time_to_exposure", new Double(time_to_exposure), "-", "-", agent.toString());
				} else {
					//time_to_exposure = time_now - agent.getLastExposureDate();
					//NOTE: we don't record this event for space reasons
					//fire_helper(time_now, eventClass, agent.hashCode(), message, message_info, "Time_to_exposure", new Double(time_to_exposure), "", "", agent.toString());
				}
				break;
			case infectious:
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				break;
			case info:
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				break;
			case recovered:
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				break;
			case regular_status:
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				break;
			case status:
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				break;
			case started_treatment:
				fire_entryhelper(time_now, eventClass, agent.hashCode(), message, message_info, "-", "-", "-", "-", agent.toString());
				Statistics.recruitments_ALL += 1;
				break;
			default:
				break;
		}
	}
	/*
	 * update certain aggregate statistics for this time step
	 */
	private static void fire_aggregatehelper(double time_now, IDU agent, String eventClass, int agentID, AgentMessage message, Object message_info,
		    String label1, Object data1, String label2, Object data2, 
		    Object agentDetails) {
		switch(message) {
			case infected:
				Statistics.incidence_ALL += 1;
				break;
			case started_treatment:
				Statistics.aggregate_courses += 1;
				break;
			case cured:
			case failed_treatment:
				Statistics.aggregate_treated += 1;
				break;
			default:
				break;
		}
	}
	/*
	 * record the event in the event log
	 */
	private static void fire_entryhelper(double time_now, String eventClass, int agentID, AgentMessage message, Object message_info,
									    String label1, Object data1, String label2, Object data2, 
									    Object agentDetails) {
		if(burn_in_mode) {
			System.err.println("Should not happen! fire_helper called while in burn-in mode.  Not recording.");
			return;
		}

		if (message == null) {
			message = AgentMessage.info;
		}
		if (message_info == null) {
			message_info = "";
		}
		PrintStream target_stream = singleton.eventsStream;
		if(message == AgentMessage.status){ 
			target_stream = singleton.statusStream;
		} else if (message == AgentMessage.regular_status) {
			target_stream = singleton.regularStatusStream;
		}
        target_stream.printf("%.3f,%s,%d,%s,%s,",	time_now, eventClass, agentID, message.toString(), message_info.toString());
        target_stream.printf("%s,%s,%s,%s,,%s" + System.lineSeparator(), label1, data1.toString(), label2, label2.toString(), agentDetails.toString());
	}

	/*
	 * reports the prevalence for various cross-sections (HCV RNA)
	 */
	public Double prevalence_ALL() {
		return getCurrentStat("prevalence_ALL");
	}
	public Double prevalence_Female() {
		return getCurrentStat("prevalence_Gender=Female");
	}
	public Double prevalence_Male() {
		return getCurrentStat("prevalence_Gender=Male");
	}
	public Double prevalence_Black() {
		return getCurrentStat("prevalence_Race=Black");
	}
	public Double prevalence_Hispanic() {
		return getCurrentStat("prevalence_Race=Hispanic");
	}
	public Double prevalence_NHWhite() {
		return getCurrentStat("prevalence_Race=NHWhite");
	}
	public Double prevalence_City() {
		return getCurrentStat("prevalence_Area="+AreaType.City);
	}
	public Double prevalence_Suburban() {
		return getCurrentStat("prevalence_Area="+AreaType.Suburban);
	}
	public Double prevalence_HR() {
		return getCurrentStat("prevalence_SyringeSource=HR");
	}
	public Double prevalence_NonHR() {
		return getCurrentStat("prevalence_SyringeSource=nonHR");
	}
	public Double prevalence_AgeLEQ30() {
		return getCurrentStat("prevalence_Age="+AgeGroup.LEQ30);
	}
//	public Double prevalence_treated() {
//		return getCurrentStat("prevalence_treated");
//	}
	//wishlist: speedup: HCV states and Populations should also use this access (via Statistics), rather than their own sinks.

	public Double getCurrentStat(String statName) {
		Vector <Double> series = runningStats.get(statName);
		if (series != null && series.size() > 0){
			return series.lastElement();
		} else {
			return 0.0;
		}
	}

	@ScheduledMethod(start = 0)
	public void recordInitStats() throws Exception {
		assert this == singleton;
		initialStats = new Hashtable<String, Double>();
		for (String s : runtimeStatNames) {
			initialStats.put(s, Double.MAX_VALUE); //error trap
		}
		initialStats.putAll(collect_stats());

		for (String statName : runtimeStatNames) {
			if(initialStats.get(statName) == Double.MAX_VALUE) {
				System.out.println("Programming error: runtime statistic "+ statName + " is not computed!");
			}
		}
	}

	@ScheduledMethod(start = Double.POSITIVE_INFINITY, priority=-100)
	public void recordFinalStats() {
		if(burn_in_mode) {
			System.err.println("Warning: not recording final stats - currently in burn-in mode");
			return;
		}
		
		assert this == singleton;
		try {
			initialStats = new Hashtable <String, Double>();
			finalStats = new Hashtable <String, Double>();
			avgStats = new Hashtable <String, Double>();
			for (String statName : runtimeStatNames) {
				if(runningStats.get(statName).size() > 0) {
					initialStats.put(statName, runningStats.get(statName).firstElement());
					finalStats.put(statName, runningStats.get(statName).lastElement());
					//the AVG refers to time-average (the underlying quantities are network-averages)
					//ignoring burn-in
					avgStats.put(statName, computeAverage(runningStats.get(statName), null));
				} else {
					initialStats.put(statName, Double.NaN);
					finalStats.put(statName, Double.NaN);
					avgStats.put(statName, Double.NaN);					
				}
			}

			if (staticOutStream == null) {
				SimpleDateFormat formatter = new SimpleDateFormat ("yyyy-MM-dd--hh.mm.ss");
				Date currentTime = new Date();
				staticOutStream = new PrintStream(outputDirectory+fileSep+formatter.format(currentTime)+".csv"); 
			}

			RunState rs = RunState.getInstance();
			if (rs.getRunInfo().getRunNumber() == 1) {
				;
			}
			String paramNames = new String();
			String paramVals  = new String();
			for (String paramName : sortedParamNames()) {
				paramNames += paramName + ",";
				paramVals  += params.getValueAsString(paramName) + ",";
			}
			paramNames += lineSep;
			paramVals += lineSep;
			staticOutStream.printf(paramNames);
			staticOutStream.printf(paramVals);

			reportSummaryToStream(System.out);
			System.out.println();
			reportSummaryToStream(staticOutStream);
			staticOutStream.println();

			if (popStatsStream != null) {
				popStatsStream.flush();
				popStatsStream.close();
			}
			if (eventsStream != null) {
				eventsStream.flush();
				eventsStream.close();
			}
			if (statusStream != null) {
				statusStream.flush();
				statusStream.close();
			}
		} catch (Exception ex) {
			System.out.printf("Cannot produce output due to an error:"+lineSep);
			ex.printStackTrace();
		}
	}

	/* annual reports of the simulation.  
	 * 	this is in addition to the agents' status on THEIR anniversary.
	 */
	@ScheduledMethod(start = 365, interval=365)
	public void recordRegularStats() {
		System.out.println("Reporting regular agent stats ...");
		assert this == singleton;
		for(Object obj : singleton.context) {
			if (obj == null) {
				continue;
			}
			if (obj instanceof IDU) {
				fire_status_change(AgentMessage.regular_status, (IDU)obj, "", null);
			}
 		}
	}
	
	private void reportSummaryToStream(PrintStream stream) {
		if(burn_in_mode) {
			return;
		}
		assert this == singleton;
		stream.printf("seed: %d," + lineSep, RandomHelper.getSeed());
		stream.printf("\t Init\t\t Avg(post-burnin time)\t\t Final" + lineSep);
		for (String statName : runtimeStatNames) {
			stream.printf("%s: %f\t%f\t%f" + lineSep, statName, 
					initialStats.get(statName), avgStats.get(statName), finalStats.get(statName));
		}
	}

	public static void setOutputDirectory(String s) {
		outputDirectory = s;
		outputDirectory = outputDirectory.replace(fileSep+fileSep, fileSep); //wishlist: better handle formatting problems
		File outDir = new File(outputDirectory);
		if (! outDir.exists()) {
			outDir.mkdirs();
		}
	}

	private static ArrayList<String> sortedParamNames(){
		ArrayList<String> ret = new ArrayList<String> ();
		String paramNames = new String();
		for (String paramName : params.getSchema().parameterNames()) {
			ret.add(paramName);
		}
		Collections.sort(ret);
		return ret;
		
	}

	@ScheduledMethod(start = daily_stats_timing, interval = 1, priority=1000)
	public void statsDailyAction(){
		if(burn_in_mode) {
			System.out.printf(lineSep+"Day (incl. burnin): %.3f. Doing burn-in. ", RepastEssentials.GetTickCount());
			return; //optionally, we could record the events, but discard the row with "burn_in_mode=1"
		}
		assert this == singleton;
		try{
			Hashtable <String,Double> currentData = collect_stats(); 
			popStatsStream.printf("%d,%.4f,",(burn_in_mode?1:0),RepastEssentials.GetTickCount()); 
			for (String statName : runtimeStatNames) {
				double val;
				if(currentData.containsKey(statName)) {
					val = currentData.get(statName);
				} else {
					val = Double.NaN;
					System.err.println("No data for: " + statName);
				}
				popStatsStream.printf("%f,",val);
				runningStats.get(statName).add(val);
			}
			if (! Double.isNaN(currentData.get("prevalence_ALL"))) {
				System.out.printf(lineSep+"Day: %.3f. Prevalence_ALL: %.4f"+lineSep, RepastEssentials.GetTickCount(), currentData.get("prevalence_ALL"));
				System.out.printf("Day: %.3f. Prevalence_ALL(RNA+): %.4f"+lineSep, RepastEssentials.GetTickCount(), currentData.get("infected_ALL")/currentData.get("population_ALL"));
				//System.out.printf("Day: %.3f. InTreatment_ALL: %.4f"+lineSep, RepastEssentials.GetTickCount(), currentData.get("intreatment_ALL"));
			}
			popStatsStream.printf(lineSep);

			//System.out.println("T" + RepastEssentials.GetTickCount() + ". Lost: " + losses_now);
			incidence_ALL 	= 0;  //initialize accumulator for current tick
			activations_ALL = 0;  //initialize accumulator for current tick
			losses_ALL    	= 0;  //initialize accumulator for current tick
			recruitments_ALL    	= 0;  //initialize accumulator for current tick
		} catch (Exception ex) {
			System.out.println("Error while computing statistics:");
			ex.printStackTrace();
		}
	}

	/*
	 * the exact time of the day when the statistics counter resets
	 */
	public static double getCounterResetTiming() {
		assert daily_stats_timing >= 0.0; //no population before tick 0
		assert daily_stats_timing < 1.1; //should be close to the start of the day  
		return daily_stats_timing;
	}
}
