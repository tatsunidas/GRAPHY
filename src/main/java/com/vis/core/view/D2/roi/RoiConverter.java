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

import com.vis.configuration.RoiDBKey;
import com.vis.configuration.RoiMetaContextKey;
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
		ij.process.FloatPolygon fp;
		float[] pointsX;
		float[] pointsY;
		switch(t) {
		case RECTANGLE:
			double x = roiObj.getXBase();
			double y = roiObj.getYBase();
			int w = roiObj.width;
			int h = roiObj.height;
			return copyProperties2IJRoi(roiObj, new Roi(x,y,w,h));
		case POLYGON:
			fp = roiObj.getFloatPolygon();
			pointsX = java.util.Arrays.copyOf(fp.xpoints, fp.npoints);
			pointsY = java.util.Arrays.copyOf(fp.ypoints, fp.npoints);
			ij.gui.PolygonRoi polygon = new ij.gui.PolygonRoi(pointsX, pointsY, Roi.POLYGON);
			return copyProperties2IJRoi(roiObj, polygon);
		case POLYLINE:
			fp = roiObj.getFloatPolygon();
			pointsX = java.util.Arrays.copyOf(fp.xpoints, fp.npoints);
			pointsY = java.util.Arrays.copyOf(fp.ypoints, fp.npoints);
			ij.gui.PolygonRoi pl = new ij.gui.PolygonRoi(pointsX, pointsY, Roi.POLYLINE);
			return copyProperties2IJRoi(roiObj, pl);
		case FREEROI:
			fp = roiObj.getFloatPolygon();
			pointsX = java.util.Arrays.copyOf(fp.xpoints, fp.npoints);
			pointsY = java.util.Arrays.copyOf(fp.ypoints, fp.npoints);
			ij.gui.PolygonRoi free = new ij.gui.PolygonRoi(pointsX, pointsY, Roi.FREEROI);
			return copyProperties2IJRoi(roiObj, free);
		case FREELINE:
			fp = roiObj.getFloatPolygon();
			pointsX = java.util.Arrays.copyOf(fp.xpoints, fp.npoints);
			pointsY = java.util.Arrays.copyOf(fp.ypoints, fp.npoints);
			ij.gui.PolygonRoi freeline = new ij.gui.PolygonRoi(pointsX, pointsY, Roi.FREELINE);
			return copyProperties2IJRoi(roiObj, freeline);
		case ANGLE:
			fp = roiObj.getFloatPolygon();
			pointsX = java.util.Arrays.copyOf(fp.xpoints, fp.npoints);
			pointsY = java.util.Arrays.copyOf(fp.ypoints, fp.npoints);
			ij.gui.PolygonRoi angle = new ij.gui.PolygonRoi(pointsX, pointsY, Roi.ANGLE);
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
			ij.gui.TextRoi txt = new ij.gui.TextRoi(roiObj.getXBase(), roiObj.getYBase(), roiObj.width, roiObj.height, roiObj.getProperty(RoiDBKey.Description.name()), new Font(Font.SANS_SERIF, Font.PLAIN, 14));
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
	
	private ij.gui.Roi copyProperties2IJRoi(RoiObj roiObj, ij.gui.Roi ijRoi) {
		for (RoiDBKey key : RoiDBKey.values()) {
			if (key == RoiDBKey.RoiMetaProperties) {
				continue;
			}
			String value = roiObj.getProperty(key.name());
			if (value != null) {
				ijRoi.setProperty(key.name(), value);
			}
		}

		Properties props = roiObj.getProperties();
		for (Object k : props.keySet()) {
			boolean mainProp = false;
			for (RoiDBKey ck : RoiDBKey.values()) {
				if (((String) k).equals(ck.name())) {
					mainProp = true;
					if (k == RoiDBKey.RoiMetaProperties) {
						Log.logger.log(Level.WARNING,
								"RoiMetaProperties should not include in roi properties.\nThis ContextKey only used for load/insert/update roi from db.");
					}
					break;
				}
			}
			if (mainProp) {
				continue;
			}
			String metaAttr = (String) props.get(k);
			ijRoi.setProperty((String) k, metaAttr);
		}
		
		// ImageJのシステムにポジション（スタックの何枚目か）を認識させるための必須処理
		String posStr = roiObj.getProperty(RoiDBKey.Position.name());
		if (posStr != null && !posStr.isEmpty() && !posStr.equals("0")) {
			try {
				int pos = Integer.parseInt(posStr);
				ijRoi.setPosition(pos); // write to binary header
			} catch (NumberFormatException e) {
				// ignore
			}
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
		roiCon.put(RoiDBKey.RoiType.name(), String.valueOf(type));//keep String
		
		// ★ 追加: SplineFit の状態をチェックしてプロパティに入れる
		if (roi instanceof ij.gui.PolygonRoi) {
			ij.gui.PolygonRoi pRoi = (ij.gui.PolygonRoi) roi;
			if (pRoi.isSplineFit()) {
				roiCon.put(RoiMetaContextKey.isSplineFit.name(), "true");//keep String
			}
		}
		
		//add all remain context props
		for(RoiDBKey key : RoiDBKey.values()) {
			if(key == RoiDBKey.RoiType) {
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
		int type = roiCon.get(RoiDBKey.RoiType.name()) instanceof String ? Integer.valueOf((String)roiCon.get(RoiDBKey.RoiType.name())):(int)roiCon.get(RoiDBKey.RoiType.name());
		int x = (int)roiCon.get("OriginX");
		int y = (int)roiCon.get("OriginY");
		int w = (int)roiCon.get("Width");
		int h = (int)roiCon.get("Height");
		float[] pointX = getFloatArrayFromDB(roiCon.get("PointX"));
		float[] pointY = getFloatArrayFromDB(roiCon.get("PointY"));
		float[] shapeArray = getFloatArrayFromDB(roiCon.get("Shape"));

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
			RoiObj poly = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, pointX.length/*keep num of points*/, RoiType.POLYGON.id(), null);
			poly.setProperties(roiCon);
			if ("true".equals(poly.getProperty(RoiMetaContextKey.isSplineFit.name()))) {
				((com.vis.core.view.D2.roi.PolygonRoi)poly).fitSpline(poly.getOptimalSplinePoints(3.0));
			}
			roi = poly;
			break;
		case POLYLINE:
			RoiObj polyline = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, pointX.length, RoiType.POLYLINE.id(), null);
			polyline.setProperties(roiCon);
			if ("true".equals(polyline.getProperty(RoiMetaContextKey.isSplineFit.name()))) {
				((com.vis.core.view.D2.roi.PolygonRoi)polyline).fitSpline(polyline.getOptimalSplinePoints(3.0));
			}
			roi = polyline;
			break;
		case ANGLE:
			RoiObj angle = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, pointX.length, RoiType.ANGLE.id(), null);
//			RoiObj angle = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiType.ANGLE.id(), null);
			angle.setProperties(roiCon);
			roi = angle;
			break;
		case OVAL:
			RoiObj oval = new com.vis.core.view.D2.roi.OvalRoi(x,y,w,h,null);
			oval.setProperties(roiCon);
			return oval;
		case FREEROI:
			if ("FREEFORM".equals(roiCon.get(RoiMetaContextKey.Shape_3D_Type.name()))) {
				com.vis.core.view.D3.roi.FreeFormRoi3D free3d = new com.vis.core.view.D3.roi.FreeFormRoi3D(x, y, w, h, null);
				free3d.setProperties(roiCon);
				free3d.initFromProperties();
				roi = free3d;
			} else {
				RoiObj free = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, pointX.length, RoiType.FREEROI.id(), null);
				free.setProperties(roiCon);
				roi = free;
			}
			break;
		case LINE:
			RoiObj line = new com.vis.core.view.D2.roi.Line(pointX[0],pointY[0],pointX[1],pointY[1],null);
			line.setProperties(roiCon);
			roi = line;
			break;
		case SPHERE_3D:
			com.vis.core.view.D3.roi.SphereRoi3D sphere = new com.vis.core.view.D3.roi.SphereRoi3D(x, y, w, h, null);
			sphere.setProperties(roiCon);
			sphere.initFromProperties();
			roi = sphere;
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
		if(roiCon.get(RoiDBKey.RoiMetaProperties.name()) == null) {
			return roi;
		}
		@SuppressWarnings("unchecked")
		Map<String, String> metaProp = (HashMap<String,String>)roiCon.get(RoiDBKey.RoiMetaProperties.name());
		for(String k : metaProp.keySet()) {
			roi.addProperty(k, metaProp.get(k));
		}
		return roi;
	}
	
	// ★ 追加：DBからの戻り値（Object）を安全に float[] に変換するヘルパーメソッド
	private float[] getFloatArrayFromDB(Object obj) {
		if (obj == null) {
			return null;
		}

		// もしすでに float[] ならそのまま返す
		if (obj instanceof float[]) {
			return (float[]) obj;
		}

		// もし double[] なら float[] にダウンキャストして返す
		if (obj instanceof double[]) {
			double[] doubleArr = (double[]) obj;
			float[] floatArr = new float[doubleArr.length];
			for (int i = 0; i < doubleArr.length; i++) {
				floatArr[i] = (float) doubleArr[i];
			}
			return floatArr;
		}

		// JSONなどで Object[] (または Number[]) として返ってきた場合
		if (obj instanceof Object[]) {
			Object[] objArr = (Object[]) obj;
			float[] floatArr = new float[objArr.length];
			for (int i = 0; i < objArr.length; i++) {
				if (objArr[i] instanceof Number) {
					floatArr[i] = ((Number) objArr[i]).floatValue();
				}
			}
			return floatArr;
		}

		return null;
	}
}
