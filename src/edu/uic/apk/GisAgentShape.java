/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import java.util.ArrayList;

import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.render.SurfacePolygon;
import gov.nasa.worldwind.render.SurfaceShape;

public class GisAgentShape extends SurfacePolygon implements SurfaceShape {

	double lats  = 0.0017; //angle
	double longs = 0.003; //angle
	
	public GisAgentShape() {
		super();
	}
	public GisAgentShape(double lats, double longs){
		super();
		this.lats = lats;
		this.longs = longs;
	}
	
	@Override
	public void setLocations(Iterable <? extends LatLon> pts) {
		ArrayList <LatLon> positions = new ArrayList();
		
		LatLon base_pos = pts.iterator().next();
		LatLon pos1 = new LatLon(base_pos.latitude.addDegrees(-lats), base_pos.longitude.addDegrees(-longs));
		LatLon pos2 = new LatLon(base_pos.latitude.addDegrees(-lats), base_pos.longitude.addDegrees(longs));
		LatLon pos3 = new LatLon(base_pos.latitude.addDegrees(lats),  base_pos.longitude.addDegrees(longs));
		LatLon pos4 = new LatLon(base_pos.latitude.addDegrees(lats),  base_pos.longitude.addDegrees(-longs));
		//System.out.println(pos2.toString());
		//System.out.println(pos3.toString());
		//System.out.println(pos4.toString());

		positions.add(pos1);
		positions.add(pos2);
		positions.add(pos3);
		positions.add(pos4);
		
		super.setLocations(positions); //wishlist: see if this works better
		//setOuterBoundary(positions);
	}
}
