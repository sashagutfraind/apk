/*
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt
 */

package edu.uic.apk;

import edu.uic.apkSynth.HCV_state;
import gov.nasa.worldwind.render.BasicWWTexture;
import gov.nasa.worldwind.render.Offset;
import gov.nasa.worldwind.render.PatternFactory;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;

import repast.simphony.visualization.gis3D.style.DefaultMarkStyle;

public class GisAgentStyle extends DefaultMarkStyle<IDU>{
	HCV_state last_state = null;
	@Override
	public BasicWWTexture getTexture(IDU idu, BasicWWTexture texture) {
		if (texture != null && idu.getHcvState() == last_state) {
			return texture;
		}
		last_state = idu.getHcvState();
		
		Color color;
		if(idu.isNaive()){
			color = Color.BLUE;
		} else if(idu.isResistant()){
			color = Color.GREEN;
		} else {
			color = Color.RED;
		}
		
		BufferedImage image = PatternFactory.createPattern(PatternFactory.PATTERN_CIRCLE, new Dimension(10, 10), 0.7f,  color, color);
		BasicWWTexture bt = new BasicWWTexture(image);
		return bt;
	}
	
	@Override
	public double getScale(IDU obj) {
		return 0.8;
	}

	@Override
	public double getHeading(IDU obj) {
		return 0;
	}

	@Override
	public String getLabel(IDU obj) {
		return null;
	}

	@Override
	public Color getLabelColor(IDU obj) {
		return null;
	}

	@Override
	public Font getLabelFont(IDU obj) {
		return null;
	}

	@Override
	public Offset getLabelOffset(IDU obj) {
		return null;
	}
}
