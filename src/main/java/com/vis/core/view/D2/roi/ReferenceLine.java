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
package com.vis.core.view.D2.roi;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Arrays;

import org.scijava.vecmath.Point2d;

import com.vis.core.view.D2.ui.glasses.*;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;

import ij.ImagePlus;
import ij.measure.Calibration;

/**
 * Reference line for reslice.
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class ReferenceLine extends com.vis.core.view.D2.roi.Line{
	
	double thickness;//distance between lines
	double gap;
	int numOfSlice = 1;//main line is always exists.
	GeneralPath sliceLines;
	Color sliceLineColor = new Color(230,0,126,200);
	int sliceLineStrokeWidth = 1;
	
	int imgW;
	int imgH;
	int imgS;
	
	double px;//pixel width to reference stack
	double py;//pixel height
	double pz;//pizel depth
	
	CutSurface plane;
	
	/**
	 * use offscreen coordinates.
	 */
	public ReferenceLine(CutSurface plane, double x1, double y1, double x2, double y2, SlideGlass slide) {
		super(x1, y1, x2, y2, slide);
		this.plane = plane;
		setSpacialInfo(slide.getOriginalImage());
//		setRoiPopupVisible(false);//TODO
	}
		
	private void setSpacialInfo(ImagePlus imp) {
		this.imgW = imp.getWidth();
		this.imgH = imp.getHeight();
		this.imgS = imp.getNSlices();
		Calibration cal = imp.getCalibration().copy();
		this.px = cal.pixelWidth;
		this.py = cal.pixelHeight;
		this.pz = cal.pixelDepth;
//		this.ay = this.py/this.px;
//		this.az = this.pz/this.px;
	}
	
	public void setThickness(double th) {
		this.thickness = th;
	}
	
	public void setGap(double gap) {
		this.gap = gap;
	}
	
	private double getHalfGap() {
		/*
		 * 
		 * line center | interval + gap + interval | line center
		 * 
		 * here, need more accurate correlation.
		 * caluculate again pixel size by sine theorem.
		 */
		double correctedPixelSize = getCorrectedPixelSizeInXY();
		double thicknessInPixels = this.thickness/correctedPixelSize;
		double gapInPixel = this.gap/correctedPixelSize;
		double interval = thicknessInPixels * 0.5;
		return (interval+gapInPixel+interval);
	}
	
	public void setNumOfSlice(int num) {
		this.numOfSlice = num;
	}
	
	public GeneralPath getSliceLines() {
		return sliceLines;
	}
	
	public CutSurface getPlane() {
		return plane;
	}
	
	/**
	 * 
	 * @return: 
	 */
	private Point2d[] nextParallelLinePoints(Point2d lineStart, Point2d lineEnd, boolean topSide) {
		// https://stackoverflow.com/questions/11796230/draw-2-parallel-lines-between-any-2-coordinates-on-the-stage-in-as3
		Point2d startPoint = lineStart;
		Point2d endPoint = lineEnd;
		double lineAngle = Math.atan2(endPoint.y - startPoint.y, endPoint.x - startPoint.x);
		double angle;
		double lineHalfGap = getHalfGap();
		double radians = 180 / Math.PI;
		if (topSide) {
			angle = 90 / radians + lineAngle;
			double topOffsetX = Math.cos(angle) * lineHalfGap;
			double topOffsetY = Math.sin(angle) * lineHalfGap;
			Point2d topStart = new Point2d(startPoint.x + topOffsetX, startPoint.y + topOffsetY);
			Point2d topEnd = new Point2d(endPoint.x + topOffsetX, endPoint.y + topOffsetY);
			return new Point2d[]{topStart, topEnd};
		} else {
			angle = -90 / radians + lineAngle;
			double bottomOffsetX = Math.cos(angle) * lineHalfGap;
			double bottomOffsetY = Math.sin(angle) * lineHalfGap;
			Point2d bottomStart = new Point2d(startPoint.x + bottomOffsetX, startPoint.y + bottomOffsetY);
			Point2d bottomEnd = new Point2d(endPoint.x + bottomOffsetX, endPoint.y + bottomOffsetY);
			return new Point2d[]{bottomStart, bottomEnd};
		}
	}
	
	/**
	 * create general path in original image coordinates scale.
	 * @return
	 */
	public GeneralPath createSliceLinesWithOffScreenCoordinates() {
		if(Double.isNaN(this.thickness) || this.thickness <= 0.) {
			return null;
		}
		if(Double.isNaN(this.numOfSlice) || this.numOfSlice < 1) {
			return null;
		}
		if(Double.isNaN(this.gap) || this.gap < 0d) {
			this.gap = 0d;
		}
		
		this.sliceLines = new GeneralPath();
		/*
		 * center line.
		 */
		double centerLineX1 = this.x1d;
		double centerLineY1 = this.y1d;
		double centerLineX2 = this.x2d;
		double centerLineY2 = this.y2d;
		
		Point2d currentLineStart = null;
		Point2d currentLineEnd = null;
		
//		System.out.println("create slice lines");
		//start from 1 (to exclude main center line)
		for(int i=0; i<numOfSlice;i++) {
			if(i == 0) {
				//add centerline
				currentLineStart = new Point2d(centerLineX1, centerLineY1);
				currentLineEnd = new Point2d(centerLineX2, centerLineY2);
				addSliceLine(sliceLines, (float)currentLineStart.x, (float)currentLineStart.y, (float)currentLineEnd.x, (float)currentLineEnd.y);
				continue;
			}
			if(i <= numOfSlice/2) {// create upper slide slice
				Point2d[] nextPoints = nextParallelLinePoints(currentLineStart, currentLineEnd, true);
				addSliceLine(sliceLines, (float)nextPoints[0].x, (float)nextPoints[0].y, (float)nextPoints[1].x, (float)nextPoints[1].y);
				currentLineStart = nextPoints[0];
				currentLineEnd = nextPoints[1];
				if(i == numOfSlice/2) {//reset to center
					currentLineStart = new Point2d(centerLineX1, centerLineY1);
					currentLineEnd = new Point2d(centerLineX2, centerLineY2);
				}
			}else {// create bottom slide slice
				Point2d[] nextPoints = nextParallelLinePoints(currentLineStart, currentLineEnd, false);
				addSliceLine(sliceLines, (float)nextPoints[0].x, (float)nextPoints[0].y, (float)nextPoints[1].x, (float)nextPoints[1].y);
				currentLineStart = nextPoints[0];
				currentLineEnd = nextPoints[1];
			}
		}
		return sliceLines;
	}
	
	
	public void addSliceLine(GeneralPath sl, float x1, float y1, float x2, float y2) {
//		System.out.println(x1+" "+y1+" "+x2+" "+y2);
		sl.moveTo(x1, y1);
		sl.lineTo(x2, y2);
	}
	
	public GeneralPath toScreenCoordinates(GeneralPath offScreenSliceLines) {
		if(offScreenSliceLines == null) {
			return null;
		}
		GeneralPath sliceLinesOnScreen = new GeneralPath();
		ArrayList<float[]> points = getPoints(offScreenSliceLines);
//		System.out.println("number of ref line pair "+points.size()/2);
		for(int i=0;i<points.size();i+=2) {
			float x_m = (float) slide.screenXD(points.get(i)[0]);
			float y_m = (float) slide.screenYD(points.get(i)[1]);
			float x_l = (float) slide.screenXD(points.get(i+1)[0]);
			float y_l = (float) slide.screenYD(points.get(i+1)[1]);
//			System.out.println(x_m+" "+y_m+" "+x_l+" "+y_l);
			addSliceLine(sliceLinesOnScreen, x_m, y_m, x_l,y_l);
		}
		return sliceLinesOnScreen;
	}
	
	
	public boolean isHorizontal() throws Exception {
		/*
		 * horizontal state is 0 degree.
		 * clockwise -> minus angle
		 * counterclockwise -> plus angle
		 * 0 to 180 and 0 to -180
		 */
		double angle = getAngle();
		if(angle <= 45 && angle > -45) {
			return true;
		}
		if(angle <= -45 && angle > -135) {
			return false;
		}
		if((angle <= -135 && angle > -180 ) || (angle > 135 && angle <= 180 )) {
			return true;
		}
		if(angle > 45 && angle <= 135) {
			return false;
		}else {
			throw new Exception("SliceLine:isXZ()::-main line- can not calculate plane direction from angle...");
		}
	}
	
	public double getDepthSpacingInPixel() {
		double correctedPixelSize = getCorrectedPixelSizeInXY();//mm unit
		return this.thickness/correctedPixelSize;//convert to pixel unit
	}
	
	public double getGapSpacingInPixel() {
		double correctedPixelSize = getCorrectedPixelSizeInXY();//mm unit
		return this.gap/correctedPixelSize;//convert to pixel unit
	}
	
	//TODO test, reproductivity for spacial point calculation.
	public double getCorrectedPixelSizeInXY() {
		/*
		 * default value is px.
		 * 
		 * Line obj angle 
		 * -180 ~ 0 ~ 180
		 */
		double angle = getAngle();
//		System.out.println("ANGLE"+" "+angle);
		angle = Math.abs(angle);
		if(angle == 0d) {
			return this.px;
		}else if(angle ==90d) {
			return this.py;
		}else if(angle ==180d) {
			return this.px;
		}
		if(angle < 90d) {
			if(angle <= 45) {
				// opposite line length is px
				return this.px / Math.sin(Math.toRadians(180 - 90 - angle));
			}else {
				// opposite line length is py
				angle = 90d - angle;
				return this.py / Math.sin(Math.toRadians(180 - 90 - angle));
			}
			
		}else if(angle > 90d) {
			angle -= 90d;
			if(angle <= 45) {
				//opposite line length is py
				return this.py/Math.sin(Math.toRadians(180 - 90 - angle));
			}else {
				// opposite line length is py
				angle = 90d - angle;
				return this.px / Math.sin(Math.toRadians(180 - 90 - angle));
			}
		}else {
			//default value
			return this.px;
		}
	}
	
	public ArrayList<float[]> getPoints(GeneralPath path) {
		ArrayList<float[]> pointList = new ArrayList<float[]>();
	    PathIterator pathIterator = path.getPathIterator(new AffineTransform());
	    while (!pathIterator.isDone()) {
	    	float[] coords = new float[6];
	        switch (pathIterator.currentSegment(coords)) {
	        case PathIterator.SEG_MOVETO:
//	            System.out.printf("move to x1=%f, y1=%f\n",
//	                    coords[0], coords[1]);
	        	pointList.add(java.util.Arrays.copyOf(coords, 2));
	            break;
	        case PathIterator.SEG_LINETO:
//	            System.out.printf("line to x1=%f, y1=%f\n",
//	                    coords[0], coords[1]);
	        	pointList.add(java.util.Arrays.copyOf(coords, 2));
	            break;
	        case PathIterator.SEG_QUADTO:
//	            System.out.printf("quad to x1=%f, y1=%f, x2=%f, y2=%f\n",
//	                    coords[0], coords[1], coords[2], coords[3]);
	        	pointList.add(java.util.Arrays.copyOf(coords, 4));
	            break;
	        case PathIterator.SEG_CUBICTO:
//	            System.out.printf("cubic to x1=%f, y1=%f, x2=%f, y2=%f, x3=%f, y3=%f\n",
//	                    coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
	        	pointList.add(java.util.Arrays.copyOf(coords, 6));
	            break;
	        case PathIterator.SEG_CLOSE:
	            break;
	        }
	        pathIterator.next();
	    }
	    return sortPointList(pointList);
	}
	
	public ArrayList<float[]> sortPointList(ArrayList<float[]> pointList) {
		ArrayList<float[]> sortedPointList = new ArrayList<>();
		boolean isXZ = true;
		try {
			isXZ = isHorizontal();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (isXZ) {// cor
			// sort by y
			float[] y_array = new float[pointList.size() / 2];// moveto point
			int cnt = 0;
			for (int i = 0; i < pointList.size(); i += 2) {
				y_array[cnt++] = pointList.get(i)[1];
			}
			cnt = 0;
			Arrays.sort(y_array);
			ArrayList<float[]> done = new ArrayList<>();
			for (float y_ : y_array) {
				for (int i = 0; i < pointList.size(); i += 2) {
					if (y_ == pointList.get(i)[1]) {
						if (!done.contains(pointList.get(i))) {
							sortedPointList.add(pointList.get(i));// moveto
							sortedPointList.add(pointList.get(i + 1));// lineto
							done.add(pointList.get(i));
						}
					}
				}
			}
			done = null;
		} else {
			// sort by x
			float[] x_array = new float[pointList.size() / 2];// moveto point
			int cnt = 0;
			for (int i = 0; i < pointList.size(); i += 2) {
				x_array[cnt++] = pointList.get(i)[0];
			}
			cnt = 0;
			Arrays.sort(x_array);
			ArrayList<float[]> done = new ArrayList<>();
			for (float x_ : x_array) {
				for (int i = 0; i < pointList.size(); i += 2) {
					if (x_ == pointList.get(i)[0]) {
						if (!done.contains(pointList.get(i))) {
							sortedPointList.add(pointList.get(i));
							sortedPointList.add(pointList.get(i + 1));
							done.add(pointList.get(i));
						}
					}
				}
			}
			done = null;
		}
		return sortedPointList;
		
	}
	
	public GeneralPath getCrossVerticalLine(double x1, double y1, double x2, double y2) {
		
		GeneralPath verLine = new GeneralPath();
//		//center point
		double cx = (x1+x2)/2;
		double cy = (y1+y2)/2;
		double angle = 90;
		double x1Rot = cx + Math.cos(Math.toRadians(angle)) * (x1 - cx) - Math.sin(Math.toRadians(angle)) * (y1 - cy);
	    double y1Rot = cy + Math.sin(Math.toRadians(angle)) * (x1 - cx) + Math.cos(Math.toRadians(angle)) * (y1 - cy);
	    double x2Rot = cx + Math.cos(Math.toRadians(angle)) * (x2 - cx) - Math.sin(Math.toRadians(angle)) * (y2 - cy);
	    double y2Rot = cy + Math.sin(Math.toRadians(angle)) * (x2 - cx) + Math.cos(Math.toRadians(angle)) * (y2 - cy);
		
	    //using line obj
//		com.vis.viewer2d.roi.Line l = new Line(x1, y1, x2, y2, null);
//		l.rotateLine(90);
		
		double extend = 20;
//		double diffX = (l.x2 - l.x1);
//		double diffY = (l.y2 - l.y1);
		double diffX = (x2Rot - x1Rot);
		double diffY = (y2Rot - y1Rot);
		double distance = Math.sqrt(diffX*diffX + diffY*diffY);
		double extendX = diffX*extend/distance;
		double extendY = diffY*extend/distance;
//		addSliceLine(verLine, (float)(l.x1d-extendX), (float)(l.y1d-extendY), (float)(l.x2d+extendX), (float)(l.y2d+extendY));
		addSliceLine(verLine, (float)(x1Rot-extendX), (float)(y1Rot-extendY), (float)(x2Rot+extendX), (float)(y2Rot+extendY));
		return verLine;
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		super.mouseDragged(e);
		createSliceLinesWithOffScreenCoordinates();
	}
}