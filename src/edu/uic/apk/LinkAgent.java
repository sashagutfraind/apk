/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;


import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.environment.RunState;
import repast.simphony.space.gis.Geography;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.LineString;

public class LinkAgent {
	public IDU a1;
	public IDU a2;
	
	//LinkShape shape;
	//LineString my_line;
	
	static private Context context;
	private static Geography geography;
	
	public LinkAgent(IDU a1, IDU a2){
		if (context == null) {
			 context = RunState.getInstance().getMasterContext();
		}
		if (RunEnvironment.getInstance().isBatch()) {
			return;
		}

		this.a1 = a1;
		this.a2 = a2;

		assert a1 != a2;
		
		Coordinate[] endpoints  = getEndpoints();
		LineString geom  		= new GeometryFactory().createLineString(endpoints);
		geography.move(this, geom);
	}

	private Coordinate[] getEndpoints() {
		if(a1 == null || a2 == null) {
			return null;
		}
		if(! context.contains(a1) || ! context.contains(a2) ) {
			a1 = null;
			a2 = null;
			return null;
		}
		
		Point p1 = geography.getGeometry(a1).getCentroid();
		Point p2 = geography.getGeometry(a2).getCentroid();		
		//System.out.println(p1.toText());
		//System.out.println(p2.toText());
		Coordinate[] al = new Coordinate[2];
		al[0] = p1.getCoordinate();
		al[1] = p2.getCoordinate();

		return al;
	}
	
	/*
	 * changes the endpoints of the link on the map.  called from update_link_geometries()
	 */
	public void sync_location() {
		if(a1 == null || a2 == null) {
			return;
		}
		if(! context.contains(a1) || ! context.contains(a1) ) {
			a1 = null;
			a2 = null;
			context.remove(this);
			return;
		}
		Coordinate[] endpoints  = getEndpoints();
		if (endpoints == null || endpoints.length != 2) {
			return; //some kind of a problem ... wishlist: why is this happening?
		}
		LineString geom  		= new GeometryFactory().createLineString(endpoints);
		//System.out.println("Syncing link: "+getDescription());
		//geography.move(this, geom);

		geom.getCoordinateN(0).setCoordinate(endpoints[0]);
		geom.getCoordinateN(1).setCoordinate(endpoints[1]);		
		geography.move(this, geom);
		
		//GeometryFactory geomFac = new GeometryFactory();
		//my_line = geomFac.createLineString(getEndpoints());		
	}
	
	public void delete() {
	}

	public String getDescription() {
		return a1.toString() + " = " + a2.toString();
	}
	static public void setStatics(Context c, Geography<IDU> g) {
		context = c;
		geography = g;
	}
}
