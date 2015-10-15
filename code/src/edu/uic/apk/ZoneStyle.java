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
	public Color getFillColor(ZoneAgent obj) {
		//return Color.GREEN;
		return Color.white;
	}

	@Override
	public double getFillOpacity(ZoneAgent obj) {
		//return 0.1;
		return 0.7;
	}

	@Override
	public Color getLineColor(ZoneAgent zone) {
		//return Color.blue;
		return Color.black;
	}

	@Override
	public double getLineOpacity(ZoneAgent obj) {
		//return 0.3;
		return 0.7;
	}

	@Override
	public double getLineWidth(ZoneAgent obj) {
		//return 1;
		return 2;
	}
}

