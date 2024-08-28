package com.vis.core.view.D2.roi;

import java.util.HashMap;

import com.vis.configuration.ContextKey;
import com.vis.core.log.Log;

import ij.gui.Roi;
import ij.process.FloatPolygon;

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
			FloatPolygon fpg = l.getFloatPoints();
			float[] xps = fpg.xpoints;
			float[] yps = fpg.ypoints;
			ij.gui.Line line = new ij.gui.Line(xps[0], yps[0], xps[1], yps[1]);
			return copyProperties2IJRoi(roiObj, line);
		case ARROW:
			Arrow al = (com.vis.core.view.D2.roi.Arrow)roiObj;
			fpg = al.getFloatPoints();
			xps = fpg.xpoints;
			yps = fpg.ypoints;
			ij.gui.Arrow arrow = new ij.gui.Arrow(xps[0], yps[0], xps[1], yps[1]);
			return copyProperties2IJRoi(roiObj, arrow);
		case TEXT:
			ij.gui.TextRoi txt = new ij.gui.TextRoi(roiObj.getXBase(), roiObj.getYBase(), roiObj.getProperty(ContextKey.Description.name()));
			return copyProperties2IJRoi(roiObj, txt);
		case POINT:case MULTIPOINT:
			ij.gui.PointRoi p = new ij.gui.PointRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints);
			return copyProperties2IJRoi(roiObj, p);
		case COMPOSITE://shape roi
			com.vis.core.view.D2.roi.ShapeRoi sRoiObj = (com.vis.core.view.D2.roi.ShapeRoi)roiObj;
			java.awt.Shape shape = sRoiObj.getShape();
//			ij.gui.ShapeRoi shapeRoi = new ij.gui.ShapeRoi((int)sRoiObj.getXBase(), (int)sRoiObj.getYBase(), shape);//DO NOT USE, geometry conflicts.
			ij.gui.ShapeRoi shapeRoi = new ij.gui.ShapeRoi(shape);
			return copyProperties2IJRoi(roiObj, shapeRoi);
		//add cases
		default:
			return null;
		}
	}
	
	private ij.gui.Roi copyProperties2IJRoi(RoiObj roiObj, ij.gui.Roi ijRoi){
		for(ContextKey key : ContextKey.values()) {
			String value = roiObj.getProperty(key.name());
			ijRoi.setProperty(key.name(), value);
		}
		return ijRoi;
	}
	
	public RoiObj convert2RoiObj(Roi roi){
		HashMap<String, Object> roiCon = new HashMap<>();
		//do not use imp
		int type = roi.getType();
		RoiType t = RoiType.find(type);
		if(t == RoiType.RECTANGLE) {
			if(roi.isDrawingTool()) {
				t = RoiType.TEXT;
			}
		}else if(t == RoiType.LINE) {
			if(roi.isDrawingTool()) {
				t = RoiType.ARROW;
			}
		}
		String rid = (String)roi.getProperty(ContextKey.RoiID.name());
		int x = roi.getBounds().x;
		int y = roi.getBounds().y;
		int w = roi.getBounds().width;
		int h = roi.getBounds().height;
		float[] pointX = roi.getFloatPolygon().xpoints;
		float[] pointY = roi.getFloatPolygon().ypoints;
		//frameNo
		String instNoString = roi.getProperty(ContextKey.InstanceNo.name());
		Integer instNo = 1;
		if(instNoString == null) {
			instNo = roi.getPosition();
		}else {
			instNo = Integer.parseInt(instNoString);
		}
		String rgString = roi.getProperty(ContextKey.RoiGroup.name());//int
		Integer rg = null;
		if(rgString != null) {
			rg = Integer.valueOf(rgString.trim());
		}
		String rlbl = roi.getProperty(ContextKey.RoiLabel.name());
		String ot = roi.getProperty(ContextKey.ObjectType.name());
		String organ = roi.getProperty(ContextKey.Organ.name());
		String desc = roi.getProperty(ContextKey.Description.name());
		String pid = roi.getProperty(ContextKey.PatientID.name());
		String studyUid = roi.getProperty(ContextKey.StudyInstanceUID.name());
		String seriesUid = roi.getProperty(ContextKey.SeriesInstanceUID.name());
		String sopUid = roi.getProperty(ContextKey.SOPInstanceUID.name());
		//set context
		roiCon.put(ContextKey.RoiID.name(), rid);
		roiCon.put("OriginX", x);
		roiCon.put("OriginY", y);
		roiCon.put("Width", w);
		roiCon.put("Height", h);
		roiCon.put("PointX", pointX);
		roiCon.put("PointY", pointY);
		if(roi instanceof ij.gui.ShapeRoi) {
			roiCon.put("Shape",((ij.gui.ShapeRoi)roi).getShapeAsArray());
		}
		roiCon.put(ContextKey.RoiType.name(), type);
		roiCon.put(ContextKey.PatientID.name(), pid);
		roiCon.put(ContextKey.StudyInstanceUID.name(),studyUid);
		roiCon.put(ContextKey.SeriesInstanceUID.name(),seriesUid);
		roiCon.put(ContextKey.SOPInstanceUID.name(),sopUid);
		roiCon.put(ContextKey.InstanceNo.name(),instNo);//int
		roiCon.put(ContextKey.RoiGroup.name(),rg);//int
		roiCon.put(ContextKey.RoiLabel.name(),rlbl);
		roiCon.put(ContextKey.ObjectType.name(),ot);
		roiCon.put(ContextKey.Organ.name(),organ);
		roiCon.put(ContextKey.Description.name(),desc);
		return buildRoiObj(roiCon);
	}
	
	public RoiObj buildRoiObj(HashMap<String, Object> roiCon) {
		int type = (int)roiCon.get(ContextKey.RoiType.name());
		int x = (int)roiCon.get("OriginX");
		int y = (int)roiCon.get("OriginY");
		int w = (int)roiCon.get("Width");
		int h = (int)roiCon.get("Height");
		float[] pointX = roiCon.get("PointX") == null ? null:(float[])roiCon.get("PointX");
		float[] pointY = roiCon.get("PointY") == null ? null:(float[])roiCon.get("PointY");
		float[] shapeArray = roiCon.get("Shape") == null ? null:(float[])roiCon.get("Shape");
		/*
		 * use, as you need 
		 * following properties will add after construction. 
		 */
//		String rid = (String)roiCon.get("RoiID");
//		Integer frameNo = (Integer)roiCon.get("FrameNo");
//		Integer rg = (Integer)roiCon.get("RoiGroup");
//		String rlbl = (String)roiCon.get("RoiLabel");
//		String ot = (String)roiCon.get("ObjectType");
//		String organ = (String)roiCon.get("Organ");
		String desc = (String)roiCon.get("Description");//TextRoi
//		String pid = (String)roiCon.get("PatientID");
//		String studyUid = (String)roiCon.get("StudyInstanceUID");
//		String seriesUid = (String)roiCon.get("SeriesInstanceUID");
//		String sopUid = (String)roiCon.get("SOPInstanceUID");
		
		RoiType t = RoiType.find(type);
		switch (t) {
		case RECTANGLE:
			RoiObj rect = new RoiObj(x, y, w, h, 0, null);
			rect.setProperties(roiCon);
			return rect;
		case POLYGON:
			RoiObj poly = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiType.POLYGON.id(), null);
			poly.setProperties(roiCon);
			return poly;
		case POLYLINE:
			RoiObj polyline = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiType.POLYLINE.id(), null);
			polyline.setProperties(roiCon);
			return polyline;
		case ANGLE:
			RoiObj angle = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiType.ANGLE.id(), null);
			angle.setProperties(roiCon);
			return angle;
		case OVAL:
			RoiObj oval = new com.vis.core.view.D2.roi.OvalRoi(x,y,w,h,null);
			oval.setProperties(roiCon);
			return oval;
		case FREEROI:
			RoiObj free = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, pointX.length, RoiType.FREEROI.id(), null);
			free.setProperties(roiCon);
			return free;
		case LINE:
			RoiObj line = new com.vis.core.view.D2.roi.Line(pointX[0],pointY[0],pointX[1],pointY[1],null);
			line.setProperties(roiCon);
			return line;
		case FREELINE:
			RoiObj freeline = new com.vis.core.view.D2.roi.PolygonRoi(pointX,pointY,pointX.length,RoiType.FREELINE.id(),null);
			freeline.setProperties(roiCon);
			return freeline;
		case ARROW:
			/*
			 * 4 points included of x and y points.
			 */
			RoiObj arrow = new com.vis.core.view.D2.roi.Arrow(pointX[0],pointY[0],pointX[2],pointY[2],null);
			arrow.setProperties(roiCon);
			return arrow;
		case TEXT:
			RoiObj txt = new com.vis.core.view.D2.roi.TextRoi(x, y, w, h, desc, null, null);
			txt.setProperties(roiCon);//update text string
			return txt;
		case POINT:case MULTIPOINT:
			RoiObj pt = new com.vis.core.view.D2.roi.PointRoi(pointX,pointY,null);
			pt.setProperties(roiCon);
			return pt;
		case COMPOSITE:
		case TRACED_ROI:
			if(shapeArray == null) {
				return null;
			}
			ShapeRoi sr = new com.vis.core.view.D2.roi.ShapeRoi(shapeArray, null);
			sr.setProperties(roiCon);//update text string
			return sr;
		// add cases
		default:
			return null;
		}
		
	}
	
	

}
