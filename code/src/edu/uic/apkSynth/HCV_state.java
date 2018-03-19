package edu.uic.apkSynth;

//development note: for parsing purposes, avoid '_' in names
public enum HCV_state {
	susceptible, exposed, infectiousacute, recovered, chronic, 
	unknown, ABPOS,
	//unknown and ab_pos are only used in loading from data.  
	//active agents would only have the other states
	cured, 
	/*
	 * completed a course of treatment and not currently infected
	 * during treatment other state would apply
	 */
	vaccinated 
	/*
	 * received at least one dose of the vaccine and not currently infected
	 * 
	 * only for people with no prior exposure: recovered individuals will not shift to vaccinated
	 * any stage of the vaccine; the actual stage is tracked by a separate variable
	 * "vaccinated" is like the general state - the refined state is tracked by VACCINE_STAGE
	 * WARNING: in acute response, this state would be lost -- test the VACCINE_STAGE instead
	 */
}
