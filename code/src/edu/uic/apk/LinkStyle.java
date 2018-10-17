/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import java.awt.Color;

import gov.nasa.worldwind.render.SurfacePolyline;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.visualization.gis3D.style.NetworkStyleGIS;

public class LinkStyle implements NetworkStyleGIS{

	@Override
	public SurfacePolyline getSurfaceShape(RepastEdge edge, SurfacePolyline shape) {
		return new SurfacePolyline();
	}

	@Override
	public Color getLineColor(RepastEdge edge) {
		return Color.BLUE;
	}

	@Override
	public double getLineOpacity(RepastEdge edge) {
		return 1.0;
	}

	@Override
	public double getLineWidth(RepastEdge edge) {
		return 2.0;
	}
}
