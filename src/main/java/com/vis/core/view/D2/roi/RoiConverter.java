package com.vis.core.view.D2.roi;

import java.util.HashMap;
import ij.gui.Roi;
import ij.process.FloatPolygon;

public class RoiConverter {
	
	public ij.gui.Roi convert2Roi(RoiObj roiObj){
		if(roiObj == null) {
			System.out.println("RoiConverter.convert2Roi:roiObj is null, return...");
			return null;
		}
		//do not use imp
		int type = roiObj.getType();
		switch(type) {
		case RoiObj.RECTANGLE:
			double x = roiObj.getXBase();
			double y = roiObj.getYBase();
			int w = roiObj.width;
			int h = roiObj.height;
			return copyProperties2IJRoi(roiObj, new Roi(x,y,w,h));
		case RoiObj.POLYGON:
			ij.gui.PolygonRoi polygon = new ij.gui.PolygonRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints, Roi.POLYGON);
			return copyProperties2IJRoi(roiObj, polygon);
		case RoiObj.ANGLE:
			ij.gui.PolygonRoi angle = new ij.gui.PolygonRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints, Roi.ANGLE);
			return copyProperties2IJRoi(roiObj, angle);
		case RoiObj.OVAL:
			ij.gui.OvalRoi oval = new ij.gui.OvalRoi(roiObj.getXBase(), roiObj.getYBase(),roiObj.getFloatWidth(),roiObj.getFloatHeight());
			return copyProperties2IJRoi(roiObj, oval);
		case RoiObj.LINE:
			/*
			 * do not use bounding rect.
			 */
			com.vis.core.view.D2.roi.Line l = (com.vis.core.view.D2.roi.Line)roiObj;
			FloatPolygon fpg = l.getFloatPoints();
			float[] xps = fpg.xpoints;
			float[] yps = fpg.ypoints;
			ij.gui.Line line = new ij.gui.Line(xps[0], yps[0], xps[1], yps[1]);
			return copyProperties2IJRoi(roiObj, line);
		case RoiObj.ARROW:
			Arrow al = (com.vis.core.view.D2.roi.Arrow)roiObj;
			fpg = al.getFloatPoints();
			xps = fpg.xpoints;
			yps = fpg.ypoints;
			ij.gui.Arrow arrow = new ij.gui.Arrow(xps[0], yps[0], xps[1], yps[1]);
			return copyProperties2IJRoi(roiObj, arrow);
		case RoiObj.TEXT:
			ij.gui.TextRoi txt = new ij.gui.TextRoi(roiObj.getXBase(), roiObj.getYBase(), roiObj.getPropertyAt(RoiObj.RoiContextKeySet.Description.name()));
			return copyProperties2IJRoi(roiObj, txt);
		case RoiObj.POINT:
			ij.gui.PointRoi p = new ij.gui.PointRoi(roiObj.getFloatPolygon().xpoints, roiObj.getFloatPolygon().ypoints);
			return copyProperties2IJRoi(roiObj, p);
		case RoiObj.COMPOSITE://shape roi
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
		for(RoiObj.RoiContextKeySet key : RoiObj.RoiContextKeySet.values()) {
			String value = roiObj.getProperty(key.name());
			ijRoi.setProperty(key.name(), value);
		}
		return ijRoi;
	}
	
	public RoiObj convert2RoiObj(Roi roi){
		HashMap<String, Object> roiCon = new HashMap<>();
		//do not use imp
		int type = roi.getType();
		if(type == RoiObj.RECTANGLE) {
			if(roi.isDrawingTool()) {
				type = RoiObj.TEXT;
			}
		}else if(type == RoiObj.LINE) {
			if(roi.isDrawingTool()) {
				type = RoiObj.ARROW;
			}
		}
		String rid = (String)roi.getProperty(RoiObj.RoiContextKeySet.RoiID.name());
		int x = roi.getBounds().x;
		int y = roi.getBounds().y;
		int w = roi.getBounds().width;
		int h = roi.getBounds().height;
		float[] pointX = roi.getFloatPolygon().xpoints;
		float[] pointY = roi.getFloatPolygon().ypoints;
		//frameNo
		String instNoString = roi.getProperty(RoiObj.RoiContextKeySet.InstanceNo.name());
		Integer instNo = 1;
		if(instNoString == null) {
			instNo = roi.getPosition();
		}else {
			instNo = Integer.parseInt(instNoString);
		}
		String rgString = roi.getProperty(RoiObj.RoiContextKeySet.RoiGroup.name());//int
		Integer rg = null;
		if(rgString != null) {
			rg = Integer.valueOf(rgString.trim());
		}
		String rlbl = roi.getProperty(RoiObj.RoiContextKeySet.RoiLabel.name());
		String ot = roi.getProperty(RoiObj.RoiContextKeySet.ObjectType.name());
		String organ = roi.getProperty(RoiObj.RoiContextKeySet.Organ.name());
		String desc = roi.getProperty(RoiObj.RoiContextKeySet.Description.name());
		String pid = roi.getProperty(RoiObj.RoiContextKeySet.PatientID.name());
		String studyUid = roi.getProperty(RoiObj.RoiContextKeySet.StudyInstanceUID.name());
		String seriesUid = roi.getProperty(RoiObj.RoiContextKeySet.SeriesInstanceUID.name());
		String sopUid = roi.getProperty(RoiObj.RoiContextKeySet.SOPInstanceUID.name());
		//set context
		roiCon.put(RoiObj.RoiContextKeySet.RoiID.name(), rid);
		roiCon.put("OriginX", x);
		roiCon.put("OriginY", y);
		roiCon.put("Width", w);
		roiCon.put("Height", h);
		roiCon.put("PointX", pointX);
		roiCon.put("PointY", pointY);
		if(roi instanceof ij.gui.ShapeRoi) {
			roiCon.put("Shape",((ij.gui.ShapeRoi)roi).getShapeAsArray());
		}
		roiCon.put(RoiObj.RoiContextKeySet.RoiType.name(), type);
		roiCon.put(RoiObj.RoiContextKeySet.PatientID.name(), pid);
		roiCon.put(RoiObj.RoiContextKeySet.StudyInstanceUID.name(),studyUid);
		roiCon.put(RoiObj.RoiContextKeySet.SeriesInstanceUID.name(),seriesUid);
		roiCon.put(RoiObj.RoiContextKeySet.SOPInstanceUID.name(),sopUid);
		roiCon.put(RoiObj.RoiContextKeySet.InstanceNo.name(),instNo);//int
		roiCon.put(RoiObj.RoiContextKeySet.RoiGroup.name(),rg);//int
		roiCon.put(RoiObj.RoiContextKeySet.RoiLabel.name(),rlbl);
		roiCon.put(RoiObj.RoiContextKeySet.ObjectType.name(),ot);
		roiCon.put(RoiObj.RoiContextKeySet.Organ.name(),organ);
		roiCon.put(RoiObj.RoiContextKeySet.Description.name(),desc);
		return buildRoiObj(roiCon);
	}
	
	public RoiObj buildRoiObj(HashMap<String, Object> roiCon) {
		int type = (int)roiCon.get(RoiObj.RoiContextKeySet.RoiType.name());
		int x = (int)roiCon.get("OriginX");
		int y = (int)roiCon.get("OriginY");
		int w = (int)roiCon.get("Width");
		int h = (int)roiCon.get("Height");
		float[] pointX = roiCon.get("PointX") == null ? null:(float[])roiCon.get("PointX");
		float[] pointY = roiCon.get("PointY") == null ? null:(float[])roiCon.get("PointY");
		float[] shapeArray = roiCon.get("Shape") == null ? null:(float[])roiCon.get("Shape");
		/** use, as you need **/
//		String rid = (String)roiCon.get("RoiID");
//		Integer frameNo = (Integer)roiCon.get("FrameNo");
//		Integer rg = (Integer)roiCon.get("RoiGroup");
//		String rlbl = (String)roiCon.get("RoiLabel");
//		String ot = (String)roiCon.get("ObjectType");
//		String organ = (String)roiCon.get("Organ");
//		String desc = (String)roiCon.get("Description");
//		String pid = (String)roiCon.get("PatientID");
//		String studyUid = (String)roiCon.get("StudyInstanceUID");
//		String seriesUid = (String)roiCon.get("SeriesInstanceUID");
//		String sopUid = (String)roiCon.get("SOPInstanceUID");
		/*
		 * ij.Arrow is recognized as Line type.
		 * deal with this by float polygon point number.
		 * IJ ROI -> this method
		 * 	Arrow has 4 floats(>0.0) points
		 */
		boolean adjustArrow = false;
		if(type == RoiObj.LINE || type == RoiObj.ARROW) {
			int pointNum = 0;
			for(float xp : pointX) {
				if(xp > 0) {
					pointNum++;
				}
			}
			if(pointNum > 2) {
				type = RoiObj.ARROW;
				adjustArrow = true;
			}
		}
		switch (type) {
		case RoiObj.RECTANGLE:
			RoiObj rect = new RoiObj(x, y, w, h, 0, null);
			rect.setProperties(roiCon);
			return rect;
		case RoiObj.POLYGON:
			RoiObj poly = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiObj.POLYGON, null);
			poly.setProperties(roiCon);
			return poly;
		case RoiObj.ANGLE:
			RoiObj angle = new com.vis.core.view.D2.roi.PolygonRoi(pointX, pointY, RoiObj.ANGLE, null);
			angle.setProperties(roiCon);
			return angle;
		case RoiObj.OVAL:
			RoiObj oval = new com.vis.core.view.D2.roi.OvalRoi(x,y,w,h,null);
			oval.setProperties(roiCon);
			return oval;
		case RoiObj.LINE:
			RoiObj line = null;
			if(pointX==null) {
				//debug
//				line = new com.vis.viewer2d.roi.Line(x,y,w,h,null);//NG
			}else {
				line = new com.vis.core.view.D2.roi.Line(pointX[0],pointY[0],pointX[1],pointY[1],null);
			}
			line.setProperties(roiCon);
			return line;
		case RoiObj.ARROW:
			/*
			 * 4 points included of x and y points.
			 */
			RoiObj arrow = null;
			if(!adjustArrow) {//In graphy handlding only
				arrow = new com.vis.core.view.D2.roi.Arrow(pointX[0],pointY[0],pointX[1],pointY[1],null);
			}else {//graphy - IJ'Roi - graphy handling
				float arrowX1 = Math.abs((pointX[0]+pointX[1])/2.0f);
				float arrowY1 = Math.abs((pointY[0]+pointY[1])/2.0f);
				float arrowX2 = Math.abs((pointX[2]+pointX[3])/2.0f);
				float arrowY2 = Math.abs((pointX[2]+pointX[3])/2.0f);
				arrow = new com.vis.core.view.D2.roi.Arrow(arrowX1, arrowY1, arrowX2, arrowY2, null);
			}
			arrow.setProperties(roiCon);
			return arrow;
		case RoiObj.TEXT:
			RoiObj txt = new com.vis.core.view.D2.roi.TextRoi(x, y, w, h, (String)roiCon.get("Description"), null, null);
//			RoiObj txt = new com.vis.viewer2d.roi.TextRoi(x,y,(String)roiCon.get("Description"),null);
			txt.setProperties(roiCon);//update text string
			return txt;
		case RoiObj.POINT:
			RoiObj pt = new com.vis.core.view.D2.roi.PointRoi(x,y,null);
			pt.setProperties(roiCon);//update text string
			return pt;
		case RoiObj.COMPOSITE:case RoiObj.TRACED_ROI:
			if(shapeArray == null) {
				return null;
			}
			ShapeRoi sr = new com.vis.core.view.D2.roi.ShapeRoi(shapeArray, null);
//			sr.x = x;//強制修正になってしまう。根本解決になっていない
//			sr.y = y;
			sr.setProperties(roiCon);//update text string
			return sr;
		// add cases
		default:
			return null;
		}
		
	}
	
	

}
