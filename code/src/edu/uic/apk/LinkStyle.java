/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;


/**
 * 
 * based on code by Eric Tatara
 *
 */


import gov.nasa.worldwind.render.SurfacePolyline;
import gov.nasa.worldwind.render.SurfaceShape;

import java.awt.Color;

import repast.simphony.visualization.gis3D.style.SurfaceShapeStyle;

public class LinkStyle implements SurfaceShapeStyle<LinkAgent>{

	@Override
	public SurfaceShape getSurfaceShape(LinkAgent object, SurfaceShape shape) {
	  return new SurfacePolyline();
	}

	@Override
	public Color getFillColor(LinkAgent obj) {
		return null;
	}

	@Override
	public double getFillOpacity(LinkAgent obj) {
		return 1.0;
	}

	@Override
	public Color getLineColor(LinkAgent obj) {
		//return Color.yellow;
		return Color.black;
	}

	@Override
	public double getLineOpacity(LinkAgent obj) {
		//return 0.5;
		return 0.9;
	}

	@Override
	public double getLineWidth(LinkAgent obj) {
		//return 1;
		return 3;
	}

}
