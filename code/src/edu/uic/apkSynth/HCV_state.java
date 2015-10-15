package edu.uic.apkSynth;

public enum HCV_state {
	susceptible, exposed, infectiousacute, recovered, cured, chronic, unknown, ABPOS
	//unknown and ab_pos are only used in loading from data.  active agents would only have the other states
	//for parsing purposes, avoid '_'
}
