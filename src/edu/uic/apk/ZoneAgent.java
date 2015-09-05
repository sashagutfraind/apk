/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class ZoneAgent implements java.io.Serializable {
	private static final long serialVersionUID = -3068777252444040161L;
	private String zip;
	private Point centroid;
	private Polygon polygon;
	private static APKBuilder builder;
	private int drug_market = -1;

	public ZoneAgent(Point coordinate, Polygon polygon, String zip){
		this.centroid = coordinate;
		this.polygon = polygon;
		this.zip = zip;		
		assert coordinate != null;
		assert polygon != null;
		assert zip != null;
	}

	public Point getCentroid() {
		return centroid;
	}

	public static void setBuilder(APKBuilder builder) {
		ZoneAgent.builder = builder;
	}

	public void setCentroid(Point coordinate) {
		this.centroid = coordinate;
	}
	public int getDrug_market() {
		return drug_market;
	}
	public void setDrug_market(int drug_market) {
		this.drug_market = drug_market;
	}

	public int getNumIDU() {
		return builder.getZonePopulation(this).size();
	}

	public Polygon getPolygon() {
		return polygon;
	}

	public void setPolygon(Polygon polygon) {
		this.polygon = polygon;
	}

	private double getHcvPreval() {
		ArrayList <IDU> my_agents = builder.getZonePopulation(this);
		int pop = my_agents.size();
		if(pop == 0) {
			return 0.0;
		}
		int counts = 0;
		for(IDU idu: my_agents) {
			counts += idu.isNaive()? 0: 1;
		}
		return ((double)counts)/pop;
	}
	public String getHcvPrevalence() {
		return "".format("%.0f%%", 100*getHcvPreval());
	}
	public String getZip() {
		return zip;
	}
}
