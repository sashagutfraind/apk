/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;


import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.render.SurfacePolyline;
import gov.nasa.worldwind.render.SurfaceShape;


public class LinkShape extends SurfacePolyline implements SurfaceShape {	
	public LinkShape() {
		super();
	}
	/*
	public void setLocations(Iterable <? extends LatLon> pts) {
		System.out.println("old");
		if(getLocations() != null) {
			for(LatLon ll : getLocations()) {
				System.out.print(ll.toString());
			}
			System.out.println();
		}
		System.out.println("new");
		for(LatLon ll : pts) {
			System.out.print(ll.toString());
		}
		super.setLocations(pts); //wishlist: see if this works better
		//setOuterBoundary(positions);
	}
	*/

}
