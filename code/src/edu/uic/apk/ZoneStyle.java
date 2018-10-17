/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;


import gov.nasa.worldwind.render.SurfacePolygon;
import gov.nasa.worldwind.render.SurfaceShape;

import java.awt.Color;

import repast.simphony.visualization.gis3D.style.SurfaceShapeStyle;

public class ZoneStyle implements SurfaceShapeStyle<ZoneAgent>{

	@Override
	public SurfaceShape getSurfaceShape(ZoneAgent object, SurfaceShape shape) {
		return new SurfacePolygon();
	}

	@Override
	public Color getFillColor(ZoneAgent zone) {
		if (zone.getNumIDU() > 0)
			return Color.white;
		
		else return null;
	}

	@Override
	public double getFillOpacity(ZoneAgent zone) {
			return 0.7;
		
	}

	@Override
	public Color getLineColor(ZoneAgent zone) {
			return Color.black;
	}

	@Override
	public double getLineOpacity(ZoneAgent zone) {
		if (zone.getNumIDU() > 0)
			return 0.7;
		else return 0;
	}

	@Override
	public double getLineWidth(ZoneAgent zone) {
		if (zone.getNumIDU() > 0)
			return 2;
		else return 0;
	}
}

