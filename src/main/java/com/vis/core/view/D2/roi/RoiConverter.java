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

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import com.vis.configuration.ContextKey;
import com.vis.core.log.Log;

import ij.gui.Roi;

/**
 * 
 * @author tatsunidas
 *
 */
public class RoiConverter {
	
	public ij.gui.Roi convert2Roi(RoiObj roiObj){
		if(roiObj == null) {
			Log.logger.fine("RoiConverter.convert2Roi:roiObj is null, return...");
			return null;
		}
		int type = roiObj.getType();
		RoiType t = RoiType.find(type);
		switch(t) {
		case RECTANGLE:
			double x = roiObj.getXBase();
			double y = roiObj.getYBase();
			int w = roiObj.width;
			int h = roiObj.height;
			return copyProperties2IJRoi(roiObj, new Roi(x,y,w,h));
		case POLYGON:
			ij.gui.PolygonRoi polygon = new ij.gui.PolygonRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints, Roi.POLYGON);
			return copyProperties2IJRoi(roiObj, polygon);
		case POLYLINE:
			ij.gui.PolygonRoi pl = new ij.gui.PolygonRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints, Roi.POLYLINE);
			return copyProperties2IJRoi(roiObj, pl);
		case FREEROI:
			ij.gui.PolygonRoi free = new ij.gui.PolygonRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints, Roi.FREEROI);
			return copyProperties2IJRoi(roiObj, free);
		case FREELINE:
			ij.gui.PolygonRoi freeline = new ij.gui.PolygonRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints, Roi.FREELINE);
			return copyProperties2IJRoi(roiObj, freeline);
		case ANGLE:
			ij.gui.PolygonRoi angle = new ij.gui.PolygonRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints, Roi.ANGLE);
			return copyProperties2IJRoi(roiObj, angle);
		case OVAL:
			ij.gui.OvalRoi oval = new ij.gui.OvalRoi(roiObj.getXBase(), roiObj.getYBase(),roiObj.getFloatWidth(),roiObj.getFloatHeight());
			return copyProperties2IJRoi(roiObj, oval);
		case LINE:
			/*
			 * do not use bounding rect.
			 */
			com.vis.core.view.D2.roi.Line l = (com.vis.core.view.D2.roi.Line)roiObj;
			ij.gui.Line line = new ij.gui.Line(l.x1, l.y1, l.x2, l.y2);
			return copyProperties2IJRoi(roiObj, line);
		case ARROW:
			Arrow al = (com.vis.core.view.D2.roi.Arrow)roiObj;
			ij.gui.Arrow arrow = new ij.gui.Arrow(al.x1, al.y1, al.x2, al.y2);
			return copyProperties2IJRoi(roiObj, arrow);
		case TEXT:
			ij.gui.TextRoi txt = new ij.gui.TextRoi(roiObj.getXBase(), roiObj.getYBase(), roiObj.width, roiObj.height, roiObj.getProperty(ContextKey.Description.name()), new Font(Font.SANS_SERIF, Font.PLAIN, 14));
			return copyProperties2IJRoi(roiObj, txt);
		case POINT:case MULTIPOINT:
			ij.gui.PointRoi p = new ij.gui.PointRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints);
			return copyProperties2IJRoi(roiObj, p);
		case COMPOSITE:case TRACED_ROI:
			com.vis.core.view.D2.roi.ShapeRoi sRoiObj = (com.vis.core.view.D2.roi.ShapeRoi)roiObj;
			java.awt.Shape shape = sRoiObj.getShape();
			ij.gui.ShapeRoi shapeRoi = new ij.gui.ShapeRoi((int)sRoiObj.getXBase(), (int)sRoiObj.getYBase(), shape);
//			ij.gui.ShapeRoi shapeRoi = new ij.gui.ShapeRoi(shape);//reset shape origin to (0,0)
			return copyProperties2IJRoi(roiObj, shapeRoi);
		//add more cases
		default:
			return null;
		}
	}
	
	private ij.gui.Roi copyProperties2IJRoi(RoiObj roiObj, ij.gui.Roi ijRoi){
		for(ContextKey key : ContextKey.values()) {
			if(key == ContextKey.RoiMetaProperties) {
				continue;
			}
			String value = roiObj.getProperty(key.name());
			if(value != null) {
				ijRoi.setProperty(key.name(), value);
			}
		}
		Properties props = roiObj.getProperties();
		for(Object k : props.keySet()) {
			boolean mainProp = false;
			for (ContextKey ck : ContextKey.values()) {
				if(((String)k).equals(ck.name())) {
					mainProp = true;
					if(k==ContextKey.RoiMetaProperties) {
						Log.logger.log(Level.WARNING, "RoiMetaProperties should not include in roi properties.\nThis ContextKey only used for load/insert/update roi from db.");
					}
					break;
				}
			}
			if(mainProp) {
				continue;
			}
			String metaAttr = (String)props.get(k);
			ijRoi.setProperty((String) k, metaAttr);
		}
		return ijRoi;
	}
	
	/**
	 * convert original imagej'roi to GRAPHY roi.
	 * Even if imagej'roi has extra meta data, ignore it.
	 * Because imagej'roi does not open prop variable.
	 * 
	 * @param roi
	 * @return
	 */
	public RoiObj convert2RoiObj(Roi roi){
		HashMap<String, Object> roiCon = new HashMap<>();
		int x = roi.getBounds().x;
		int y = roi.getBounds().y;
		int w = roi.getBounds().width;
		int h = roi.getBounds().height;
		float[] pointX = roi.getFloatPolygon().xpoints;
		float[] pointY = roi.getFloatPolygon().ypoints;
		//set geometry
		roiCon.put(RoiGeometry.OriginX.name(), x);
		roiCon.put(RoiGeometry.OriginY.name(), y);
		roiCon.put(RoiGeometry.Width.name(), w);
		roiCon.put(RoiGeometry.Height.name(), h);
		roiCon.put(RoiGeometry.PointX.name(), pointX);
		roiCon.put(RoiGeometry.PointY.name(), pointY);
		if(roi instanceof ij.gui.ShapeRoi) {
			roiCon.put(RoiGeometry.Shape.name(),((ij.gui.ShapeRoi)roi).getShapeAsArray());
		}
		
		int type = roi.getType();
		if(roi instanceof ij.gui.Arrow) {
			type = RoiType.ARROW.id();
		}else if(roi instanceof ij.gui.TextRoi) {
			type = RoiType.TEXT.id();
		}else if(roi instanceof ij.gui.PointRoi) {
			if(roi.getContainedPoints().length > 1) {
				//multi point
				type = RoiType.MULTIPOINT.id();
			}
		}
		roiCon.put(ContextKey.RoiType.name(), String.valueOf(type));//keep String
		
		//add all remain context props
		for(ContextKey key : ContextKey.values()) {
			if(key == ContextKey.RoiType) {
				continue;
			}
			String value = roi.getProperty(key.name());
			if(value != null) {
				roiCon.put(key.name(), value);
			}
		}
		
		return buildRoiObj(roiCon);
	}
	
	public RoiObj buildRoiObj(HashMap<String, Object> roiCon) {
		//to open roi file
		int type = roiCon.get(ContextKey.RoiType.name()) instanceof String ? Integer.valueOf((String)roiCon.get(ContextKey.RoiType.name())):(int)roiCon.get(ContextKey.RoiType.name());
		int x = (int)roiCon.get("OriginX");
		int y = (int)roiCon.get("OriginY");
		int w = (int)roiCon.get("Width");
		int h = (int)roiCon.get("Height");
		float[] pointX = roiCon.get("PointX") == null ? null:(float[])roiCon.get("PointX");
		float[] pointY = roiCon.get("PointY") == null ? null:(float[])roiCon.get("PointY");
		float[] shapeArray = roiCon.get("Shape") == null ? null:(float[])roiCon.get("Shape");

		String desc = (String)roiCon.get("Description");//TextRoi
		
		//add meta properties
		
		RoiObj roi = null;
		RoiType t = RoiType.find(type);
		switch (t) {
		case RECTANGLE:
			RoiObj rect = new RoiObj(x, y, w, h, 0, null);
			rect.setProperties(roiCon);
			roi = rect;
			break;
		case POLYGON:
			RoiObj poly = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiType.POLYGON.id(), null);
			poly.setProperties(roiCon);
			roi = poly;
			break;
		case POLYLINE:
			RoiObj polyline = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiType.POLYLINE.id(), null);
			polyline.setProperties(roiCon);
			roi = polyline;
			break;
		case ANGLE:
			RoiObj angle = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiType.ANGLE.id(), null);
			angle.setProperties(roiCon);
			roi = angle;
			break;
		case OVAL:
			RoiObj oval = new com.vis.core.view.D2.roi.OvalRoi(x,y,w,h,null);
			oval.setProperties(roiCon);
			return oval;
		case FREEROI:
			RoiObj free = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, pointX.length, RoiType.FREEROI.id(), null);
			free.setProperties(roiCon);
			roi = free;
			break;
		case LINE:
			RoiObj line = new com.vis.core.view.D2.roi.Line(pointX[0],pointY[0],pointX[1],pointY[1],null);
			line.setProperties(roiCon);
			roi = line;
			break;
		case ARROW:
			RoiObj arrow = new com.vis.core.view.D2.roi.Arrow(pointX[0],pointY[0],pointX[1],pointY[1],null);
			arrow.setProperties(roiCon);
			roi = arrow;
			break;
		case FREELINE:
			RoiObj freeline = new com.vis.core.view.D2.roi.PolygonRoi(pointX,pointY,pointX.length,RoiType.FREELINE.id(),null);
			freeline.setProperties(roiCon);
			roi = freeline;
			break;
		case TEXT:
			RoiObj txt = new com.vis.core.view.D2.roi.TextRoi(x, y, w, h, desc, null, null);
			txt.setProperties(roiCon);//update text string
			roi = txt;
			break;
		case POINT:case MULTIPOINT:
			RoiObj pt = new com.vis.core.view.D2.roi.PointRoi(pointX,pointY,null);
			pt.setProperties(roiCon);
			roi = pt;
			break;
		case COMPOSITE:
			if(shapeArray == null) {
				return null;
			}
			ShapeRoi sr = new com.vis.core.view.D2.roi.ShapeRoi(shapeArray, null);
			sr.setProperties(roiCon);//update text string
			roi = sr;
			break;
		case TRACED_ROI:
			if(shapeArray == null) {
				RoiObj polyTrace = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiType.POLYGON.id(), null);
				polyTrace.setProperties(roiCon);
				roi = polyTrace;
				break;
			}
			ShapeRoi srTrace = new com.vis.core.view.D2.roi.ShapeRoi(shapeArray, null);
			srTrace.setProperties(roiCon);//update text string
			roi = srTrace;
			break;
		// add cases
		default:
			return null;
		}
		
		//add meta prop
		if(roiCon.get(ContextKey.RoiMetaProperties.name()) == null) {
			return roi;
		}
		@SuppressWarnings("unchecked")
		Map<String, String> metaProp = (HashMap<String,String>)roiCon.get(ContextKey.RoiMetaProperties.name());
		for(String k : metaProp.keySet()) {
			roi.addProperty(k, metaProp.get(k));
		}
		return roi;
	}
}
