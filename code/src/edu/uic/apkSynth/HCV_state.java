package edu.uic.apkSynth;

//for parsing purposes, avoid '_'
public enum HCV_state {
	susceptible, exposed, infectiousacute, recovered, cured, chronic, 
	unknown, ABPOS,
	//unknown and ab_pos are only used in loading from data.  
	//active agents would only have the other states
	vaccinated 
	/*
	 * only for people with no prior exposure: recovered individuals will not shift to vaccinated
	 * any stage of the vaccine; the actual stage is tracked by a separate variable
	 */
}
