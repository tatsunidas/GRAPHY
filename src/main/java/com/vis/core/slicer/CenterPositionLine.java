/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.view.mpr;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;

import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;

import ij.ImagePlus;
import ij.measure.Calibration;

/**
 * 
 * @author tatsunidas
 *
 */
public class CenterPositionLine extends com.vis.core.view.D2.roi.Line{
	
	private static final long serialVersionUID = 1L;
	Color sliceLineColor = Color.CYAN;
	Color center_support_line_color;//vertical line
	int sliceLineStrokeWidth = 1;
	
	int imgW;
	int imgH;
	int imgS;
	
	double px;//pixel width to reference stack
	double py;//pixel height
	double pz;//pizel depth
	
	CutSurface plane;//Praparat plane
	
	/**
	 * use offscreen coordinates.
	 */
	public CenterPositionLine(CutSurface plane, double x1, double y1, double x2, double y2, SlideGlass slide) {
		super(x1, y1, x2, y2, slide);
		this.plane = plane;
		if(plane == CutSurface.AXIAL) {
			sliceLineColor = Color.red;//horizontal
			center_support_line_color = Color.blue;
			if(x2 <= x1) {
				throw new IllegalArgumentException("Seems not suitable line for Axial");
			}
		}else if(plane == CutSurface.CORONAL) {
			sliceLineColor = Color.green;//vertical
			center_support_line_color = Color.red;//horizontal
			if(y2 <= y1) {
				throw new IllegalArgumentException("Seems not suitable line for Coronal");
			}
		}else {
			sliceLineColor = Color.blue;//horizontal
			center_support_line_color = Color.green;
			if(x2 <= x1) {
				throw new IllegalArgumentException("Seems not suitable line for Sagittal");
			}
		}
		setSpacialInfo(slide.getOriginalImage());
	}
	
	private void setSpacialInfo(ImagePlus imp) {
		this.imgW = imp.getWidth();
		this.imgH = imp.getHeight();
		this.imgS = imp.getNSlices();
		Calibration cal = imp.getCalibration().copy();
		this.px = cal.pixelWidth;
		this.py = cal.pixelHeight;
		this.pz = cal.pixelDepth;
	}
	
	@Override
	public int isHandle(int sx, int sy) {
		int size = HANDLE_SIZE+5;
		if (getStrokeWidth()>1) size += (int)Math.log(getStrokeWidth());
		int halfSize = size/2;
		
		Point sp1 = slide.slideglassCoordinateFromOffScreen(getXBase()+x1R, getYBase()+y1R);
		Point sp2 = slide.slideglassCoordinateFromOffScreen(getXBase()+x2R, getYBase()+y2R);
		
		int sx1 = sp1.x - halfSize;
		int sy1 = sp1.y - halfSize;
		int sx2 = sp2.x - halfSize;
		int sy2 = sp2.y - halfSize;
		int sx3 = sx1 + (sx2-sx1)/2-1;
		int sy3 = sy1 + (sy2-sy1)/2-1;
		
//		if (sx>=sx1&&sx<=sx1+size&&sy>=sy1&&sy<=sy1+size) return 0;
//		if (sx>=sx2&&sx<=sx2+size&&sy>=sy2&&sy<=sy2+size) return 1;
		// only use center.
		if (sx>=sx3&&sx<=sx3+size+2&&sy>=sy3&&sy<=sy3+size+2) return 2;
		return -1;
	}
	
	public void draw(Graphics g) {
		
		x1d=getXBase()+x1R; y1d=getYBase()+y1R; x2d=getXBase()+x2R; y2d=getYBase()+y2R;
		x1=(int)x1d; y1=(int)y1d; x2=(int)x2d; y2=(int)y2d;
		
		int x3 = x1 + (int)((x2 - x1) / 2.);
		int y3 = y1 + (int)((y2 - y1) / 2.);
		
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

		//draw main reference line
		if(isActiveOverlayRoi()) {
			g2d.setColor(Color.CYAN);
		}else {
			g2d.setColor(sliceLineColor);
		}
		g2d.drawLine(x1, y1, x2, y2);
		//draw virtical line
		g2d.setColor(center_support_line_color);
		GeneralPath verLine_ = getCrossVerticalLine(x1d, y1d, x2d, y2d);
		g2d.draw(verLine_);
		//draw handles
//		drawHandle(g, x1, y1);
//		drawHandle(g, x2, y2);
		//middle handle
		drawHandle(g, x3, y3);
	}

	public GeneralPath getCrossVerticalLine(double x1, double y1, double x2, double y2) {
		GeneralPath verLine = new GeneralPath();
		double cx = (x1 + x2) / 2;
		double cy = (y1 + y2) / 2;
		double angle = 90;
		double x1Rot = cx + Math.cos(Math.toRadians(angle)) * (x1 - cx) - Math.sin(Math.toRadians(angle)) * (y1 - cy);
		double y1Rot = cy + Math.sin(Math.toRadians(angle)) * (x1 - cx) + Math.cos(Math.toRadians(angle)) * (y1 - cy);
		double x2Rot = cx + Math.cos(Math.toRadians(angle)) * (x2 - cx) - Math.sin(Math.toRadians(angle)) * (y2 - cy);
		double y2Rot = cy + Math.sin(Math.toRadians(angle)) * (x2 - cx) + Math.cos(Math.toRadians(angle)) * (y2 - cy);
		addLine(verLine, (float) (x1Rot), (float) (y1Rot), (float) (x2Rot),
				(float) (y2Rot));
		return verLine;
	}

	public void addLine(GeneralPath sl, float x1, float y1, float x2, float y2) {
		sl.moveTo(x1, y1);
		sl.lineTo(x2, y2);
	}
}
