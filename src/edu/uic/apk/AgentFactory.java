/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;
import java.util.ArrayList;

/*
 * common interface of all generators of agents. 
 */
public interface AgentFactory {
	public ArrayList <IDU> add_new_IDUs(int count, boolean immature_agents);
	
	public ArrayList <IDU> generate_initial();
	
	public void remove_IDUs();
}
