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

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.ContextKey;
import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.view.D2.ui.ResultWindow;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
import ij.gui.RotatedRectRoi;
import ij.measure.Calibration;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

/**
 * ij.plugin.filter.Analyzer implentation.
 * @author tatsunidas
 *
 */
public class RoiAnalyzer {
	
	
	final com.vis.core.view.D2.roi.RoiObj roiObj;
	final ij.gui.Roi ijroi;
	final ImagePlus imp;
	
	
	public RoiAnalyzer(RoiObj roi) {
		this(roi, roi.getSlideGlass());
	}
	
	public RoiAnalyzer(RoiObj roi, SlideGlass sg) {
		this(roi, sg.getOriginalImage());
	}
	
	public RoiAnalyzer(RoiObj roi, ImagePlus imp) {
		if(roi == null || imp == null) {
			throw new IllegalArgumentException("RoiObj or ImagePlus is null...");
		}
		this.roiObj = roi;
		this.ijroi = convert2IJRoi(roiObj);
		this.imp = imp;
	}
	
	private ij.gui.Roi convert2IJRoi(RoiObj roiObj){
		return new RoiConverter().convert2Roi(roiObj);
	}
	
	public List<HashMap<Measurements, Double>> measure(){
		List<HashMap<Measurements, Double>> results = new ArrayList<>();
		if (roiObj.getRoiType()==RoiType.POINT || roiObj.getRoiType()==RoiType.MULTIPOINT) {
			return measurePoint();
		}
		if (roiObj.getRoiType()==RoiType.ANGLE) {
			results.add(measureAngle());
			return results;
		}
		if (roiObj.isLine()) {/*isLine() includes ANGLE. ANGLE should stay on above.*/
			results.add(measureLength());
			return results;
		}
		//others
		imp.deleteRoi();
		imp.setRoi(ijroi);
		ImageStatistics stats = imp.getStatistics(Measurements.allStats());
		results.add(stats2Map(stats));
		return results;
	}
	
	private List<HashMap<Measurements, Double>> measurePoint() {
		imp.deleteRoi();
		ij.gui.Roi roi = ijroi;
		FloatPolygon p = roi.getFloatPolygon();
		ImagePlus imp2 = imp;
		ImageStack stack = null;
		List<HashMap<Measurements, Double>> res = new ArrayList<>();
		if (imp2.getStackSize()>1)
			stack = imp2.getStack();
		PointRoi pointRoi = roi instanceof PointRoi?(PointRoi)roi:null;
		for (int i=0; i<p.npoints; i++) {
			int position = 0;
			if (pointRoi!=null && p.npoints>1)
				position = pointRoi.getPointPosition(i);
			ImageProcessor ip = null;
			if (stack!=null && position>0 && position<=stack.size())
				ip = stack.getProcessor(position);
			else
				ip = imp2.getProcessor();
			ip.setRoi((int)Math.round(p.xpoints[i]), (int)Math.round(p.ypoints[i]), 1, 1);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, Measurements.allStats(), imp2.getCalibration());
			stats.xCenterOfMass = p.xpoints[i];
			stats.yCenterOfMass = p.ypoints[i];
			res.add(stats2Map(stats));
		}
		return res;
	}
	
	private HashMap<Measurements, Double> measureAngle() {
		imp.deleteRoi();
		ij.gui.Roi roi = ijroi;
		ImageProcessor ip = imp.getProcessor();
		ip.setRoi(roi.getPolygon());
		ImageStatistics stats = new ImageStatistics();
		return stats2Map(stats);
	}
	
	private HashMap<Measurements, Double> measureLength() {
		imp.deleteRoi();
		ij.gui.Roi roi = ijroi;
		ImagePlus imp2 = imp;
		imp2.setRoi(roi);
		boolean straightLine = roi.getType()==Roi.LINE;
		Double length = Double.NaN;
		Double angle = Double.NaN;
//		int all_stats = Measurements.allStats();
		int majors = Measurements.AREA.id()+
					Measurements.MEAN.id()+
					Measurements.STD_DEV.id()+
					Measurements.MODE.id()+
					Measurements.MIN_MAX.id()+
					//Measurements.CENTROID.id()+
					Measurements.MEDIAN.id();
		length = roi.getLength();
		if (straightLine) {
			Line line = (Line)roi;
			angle = line.getFloatAngle(line.x1d,line.y1d,line.x2d,line.y2d);
		}
		int lineWidth = (int)Math.round(roi.getStrokeWidth());
		ImageProcessor ip2 = imp2.getProcessor();
		double minThreshold = ip2.getMinThreshold();
		double maxThreshold = ip2.getMaxThreshold();
		int limit = Measurements.LIMIT.id();//256
		boolean calibrated = imp2.getCalibration().calibrated();
		Rectangle saveR = null;
		Calibration globalCal = calibrated?imp2.getGlobalCalibration():null;
		Calibration localCal = null;
		if (globalCal!=null) {
			imp2.setGlobalCalibration(null);
			localCal = imp2.getCalibration().copy();
			imp2.setCalibration(globalCal);
		} if (lineWidth>1) {
			saveR = ip2.getRoi();
			ip2.setRoi(Roi.convertLineToArea(roi));
		} else if (calibrated && limit!=0) {
			Calibration cal = imp2.getCalibration().copy();
			imp2.getCalibration().disableDensityCalibration();
			ProfilePlot profile = new ProfilePlot(imp2);
			imp2.setCalibration(cal);
			double[] values = profile.getProfile();
			if (values!=null) {
				ip2 = new FloatProcessor(values.length, 1, values);
				ip2 = convertToOriginalDepth(imp2, ip2);
				ip2.setCalibrationTable(cal.getCTable());
			}
		} else {
			ProfilePlot profile = new ProfilePlot(imp2);
			double[] values = profile.getProfile();
			if(values != null) {
				ip2 = new FloatProcessor(values.length, 1, values);
				if (limit!=0)
					ip2 = convertToOriginalDepth(imp2, ip2);
			}
		}
		if (limit!=0 && minThreshold!=ImageProcessor.NO_THRESHOLD)
			ip2.setThreshold(minThreshold,maxThreshold,ImageProcessor.NO_LUT_UPDATE);
		ImageStatistics stats = ImageStatistics.getStatistics(ip2, majors+limit, imp2.getCalibration());
		if (saveR!=null)
			ip2.setRoi(saveR);
		if (straightLine) {
			FloatPolygon p = ((Line)roi).getFloatPoints();
			stats.xCentroid = p.xpoints[0] + (p.xpoints[1]-p.xpoints[0])/2.0;
			stats.yCentroid = p.ypoints[0] + (p.ypoints[1]-p.ypoints[0])/2.0;
			if (imp2!=null) {
				Calibration cal = imp.getCalibration();
				stats.xCentroid = cal.getX(stats.xCentroid);
				stats.yCentroid = cal.getY(stats.yCentroid, imp2.getHeight());
			}
		}
		if (globalCal!=null && localCal!=null) {
			imp2.setGlobalCalibration(globalCal);
			imp2.setCalibration(localCal);
		}
		HashMap<Measurements, Double> res = stats2Map(stats);
		res.put(Measurements.LENGTH, length);
		res.put(Measurements.ANGLE, angle);
		return res;
	}
	
	private ImageProcessor convertToOriginalDepth(ImagePlus imp, ImageProcessor ip) {
		if (imp.getBitDepth()==8)
			ip = ip.convertToByte(false);
		else if (imp.getBitDepth()==16)
			ip = ip.convertToShort(false);
		return ip;
	}
	
	final double getArea(Polygon p) {
		if (p==null) return Double.NaN;
		int carea = 0;
		int iminus1;
		for (int i=0; i<p.npoints; i++) {
			iminus1 = i-1;
			if (iminus1<0) iminus1=p.npoints-1;
			carea += (p.xpoints[i]+p.xpoints[iminus1])*(p.ypoints[i]-p.ypoints[iminus1]);
		}
		return (Math.abs(carea/2.0));
	}
	
	private HashMap<Measurements, Double> stats2Map(ImageStatistics stats){
		HashMap<Measurements, Double> map = new HashMap<Measurements, Double>();
		map.put(Measurements.AREA, stats.area);
		map.put(Measurements.MEAN, stats.mean);
		map.put(Measurements.STD_DEV, stats.stdDev);
		map.put(Measurements.MODE, stats.dmode);
		map.put(Measurements.MIN, stats.min);
		map.put(Measurements.MAX, stats.max);
		map.put(Measurements.CENTROID_X, stats.xCentroid);
		map.put(Measurements.CENTROID_Y, stats.yCentroid);
		map.put(Measurements.CENTER_OF_MASS_X, stats.xCenterOfMass);
		map.put(Measurements.CENTER_OF_MASS_Y, stats.yCenterOfMass);
		double perimeter = ijroi.getLength();
		map.put(Measurements.PERIMETER, perimeter);
		double circularity = perimeter==0.0?0.0:4.0*Math.PI*(stats.area/(perimeter*perimeter));
		if (circularity>1.0) circularity = 1.0;
		map.put(Measurements.CIRCULARITY, circularity);
		boolean isArea = ijroi.isArea();
		double convexArea = isArea ? getArea(ijroi.getConvexHull()):stats.pixelCount;
		map.put(Measurements.ASPECT_RATIO, isArea?stats.major/stats.minor:0.0);
		map.put(Measurements.ROUNDNESS, isArea?4.0*stats.area/(Math.PI*stats.major*stats.major):0.0);
		map.put(Measurements.SOLIDITY, isArea?stats.pixelCount/convexArea:Double.NaN);
		if (ijroi.isLine()) {
			Rectangle bounds = ijroi.getBounds();
			double rx = bounds.x;
			double ry = bounds.y;
			double rw = bounds.width;
			double rh = bounds.height;
			Calibration cal = imp!=null?imp.getCalibration():null;
			if (cal!=null) {
				rx = cal.getX(rx);
				ry = cal.getY(ry, imp.getHeight());
				rw *= cal.pixelWidth;
				rh *= cal.pixelHeight;
			}
			map.put(Measurements.ROI_X, rx);
			map.put(Measurements.ROI_Y, ry);
			map.put(Measurements.ROI_WIDTH, rw);
			map.put(Measurements.ROI_HEIGHT, rh);
		} else {
			map.put(Measurements.ROI_X,stats.roiX);
			map.put(Measurements.ROI_Y,stats.roiY);
			map.put(Measurements.ROI_WIDTH,stats.roiWidth);
			map.put(Measurements.ROI_HEIGHT,stats.roiHeight);
		}
		map.put(Measurements.MAJOR,stats.major);
		map.put(Measurements.MINOR,stats.minor);
		map.put(Measurements.ANGLE,stats.angle);
		double FeretDiameter=Double.NaN, feretAngle=Double.NaN, minFeret=Double.NaN,
			feretX=Double.NaN, feretY=Double.NaN;
		Roi roi2 = ijroi;
		if (roi2==null && imp!=null)
			roi2 = new Roi(0, 0, imp.getWidth(), imp.getHeight());
		if (roi2!=null && roi2.getType() != Roi.POINT) {
			double[] a = roi2.getFeretValues();
			if (a!=null) {
				FeretDiameter = a[0];
				feretAngle = a[1];
				minFeret = a[2];
				feretX = a[3];
				feretY = a[4];
			}
		}
		map.put(Measurements.FERET, FeretDiameter);
		map.put(Measurements.FERET_X, feretX);
		map.put(Measurements.FERET_Y, feretY);
		map.put(Measurements.FERET_ANGLE, feretAngle);
		map.put(Measurements.FERET_MIN, minFeret);
		map.put(Measurements.INTEGRATED_DENSITY,stats.area*stats.mean);
		map.put(Measurements.RAW_INTEGRATED_DENSITY,stats.pixelCount*stats.umean);
		map.put(Measurements.MEDIAN, stats.median);
		map.put(Measurements.SKEWNESS, stats.skewness);
		map.put(Measurements.KURTOSIS, stats.kurtosis);
		map.put(Measurements.AREA_FRACTION, stats.areaFraction);
		map.put(Measurements.CHANNEL, (double)imp.getChannel());
		String sliceStr = imp.getProp("SlicePosition");//TODO futurework
		if(sliceStr != null) {
			try {
				int pos = Integer.parseInt(sliceStr);
				map.put(Measurements.SLICE, (double)pos);
			}catch(NumberFormatException e) {
				//do nothing
			}
		}
		
		//ANGLE only calculated when roi is line or angle.
		if (ijroi.getType()==Roi.ANGLE) {
			double angle = ((PolygonRoi)ijroi).getAngle();
//			if (Prefs.reflexAngle) angle = 360.0-angle;//tatsu
			map.put(Measurements.ANGLE, angle);
		} else if (ijroi.isLine()) {
			map.put(Measurements.LENGTH, perimeter);
			if (ijroi.getType()==Roi.LINE) {
				Line line = (Line)ijroi;
				map.put(Measurements.ANGLE, line.getFloatAngle(line.x1d,line.y1d,line.x2d,line.y2d));
			}
		}
		
		if (imp.getBitDepth()!=24) {
			map.put(Measurements.MIN_THRESHOLD, stats.lowerThreshold);
			map.put(Measurements.MAX_THRESHOLD, stats.upperThreshold);
		}
		if (ijroi instanceof RotatedRectRoi) {
			double[] p = ((RotatedRectRoi)ijroi).getParams();
			double dx = p[2] - p[0];
			double dy = p[3] - p[1];
			double length = Math.sqrt(dx*dx+dy*dy);
			Calibration cal = imp!=null?imp.getCalibration():null;
			double pw = 1.0;
			if (cal!=null && cal.pixelWidth==cal.pixelHeight)
				pw = cal.pixelWidth;
			map.put(Measurements.PERIMETER, length*pw);
			map.put(Measurements.LENGTH, length*pw);
			map.put(Measurements.ROI_WIDTH, p[4]*pw);
		}
		String groupId = roiObj.getProperty(ContextKey.RoiGroup);
		if(groupId != null) {
			try {
				int gid = Integer.parseInt(groupId);
				map.put(Measurements.GROUP, (double)gid);
			}catch(NumberFormatException e) {
				//do nothing
			}
		}
		return map;
	}
	
	public void showInResultWindow(HashMap<Measurements, Double> results) {
		ResultWindow rw = null;
		java.awt.Window win = WindowManager.getWindow(ConfigInfo.ResultWindow.name());
		if(win == null) {
			rw = new ResultWindow(ConfigInfo.ResultWindow.name(), null, 400, 350, true/*showRowIndex*/);
			WindowManager.addWindow(rw);
			rw.setLocationRelativeTo(MainScreen.getInstance());
		}else {
			rw = (ResultWindow)win;
		}
		int row = rw.getRowCount();
		rw.setValue(ContextKey.RoiID.name(), row, roiObj.getProperty(ContextKey.RoiID.name()));
		Set<Measurements> keys = results.keySet();
		for(Measurements m : Measurements.values()) {
			if(keys.contains(m)) {
				rw.setValue(m.name(), row, String.valueOf(results.get(m)));
			}
		}
		rw.setVisible(true);
		rw.toFront();
	}

}
