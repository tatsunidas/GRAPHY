package com.vis.core.view.D2.roi;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.ContextKey;
import com.vis.configuration.GraphyProp;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.view.D2.ui.glasses.*;


/** Implements the ROI Brush tool.
 * 
 * when brushing start;
 * if roi exists -> update this roi
 * if no roi -> create new roi
 * 
 * @author tatsunidas
 * 
 * */

public class RoiBrush implements Runnable{
	final static int ADD=0, SUBTRACT=1;
	final static int leftClick=16, alt=9, shift=1;
//	private Polygon poly;
	private Point previousP;
	private int mode = ADD;
	
	int defaultSize = 10;
	String defaultType = "Circle";
	
	SlideGlass slide = null;
	MouseEvent me = null;
	
	public RoiBrush(SlideGlass slide, MouseEvent me) {
		this.slide = slide;
		this.me = me;
		Thread thread = new Thread(this, "RoiBrush");
		thread.start();
//		try {
//			SwingUtilities.invokeAndWait(this);
//		} catch (InvocationTargetException | InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	
	// first mouse down
	public void createBrush(MouseEvent e) {
		int slideX = e.getX();
		int slideY = e.getY();
		int size = getBrushSize();
		String type = getBrushType();
		if (slide==null) return;
		if(slide.getRoiPopUpAt(slideX, slideY) != null) {
			System.out.println("Here on RoiPopUp ! return. RoiBrush::createBrush");
			return;
		}
		RoiObj roi = slide.getRoiLocationAt(slideX, slideY);
		ShapeRoi brush = null;
		/*
		 * slide origin shift
		 */
		int dispX = slide.onDisplayImageX(slideX);
		int dispY = slide.onDisplayImageY(slideY);
		int XinOrgScale = slide.onImageX(slideX);
		int YinOrgScale = slide.onImageY(slideY);
		System.out.println("brush loc: "+slideX+" , "+slideY+" and "+dispX+" , "+dispY+" and scaled2org "+ XinOrgScale + " , "+YinOrgScale);
		if(type.equals("Circle")) {
			brush = getCircularRoi(XinOrgScale, YinOrgScale, size);
		}else {
			brush = getSquareRoi(XinOrgScale, YinOrgScale, size);
		}
		brush.setActiveOverlayRoi(false);
		slide.setRoiBrush(brush);
		/* 
		 * if roi exist near from this brush,
		 * sub or add 
		 */
		Point p = new Point(XinOrgScale, YinOrgScale);
		if (roi!=null && !roi.isArea()) {
			if (!roi.contains(p.x, p.y)) {
				mode = SUBTRACT;
			}
		}
		
		if(previousP != null) {
			if(p.equals(previousP)) {
				return;
			}
		}
		
		previousP = p;
		int flags = e.getModifiers();//TODO
		if ((flags&leftClick)==0) {
			return;
		}
		if ((flags&shift)!=0) {
			mode = ADD;
		}else if ((flags&alt)!=0) {
			mode = SUBTRACT;
		}
		if (mode==ADD) {
			add(roi, brush,p.x, p.y);
		}else {
			subtract(roi, brush, p.x, p.y);
		}
	}
	
	/*
	 * mouseup
	 */
	public void brushingEnd(){
		slide.setRoiBrush(null);
	}

//	void add(RoiObj roi, ShapeRoi brush, int x, int y) {
//		if(roi != null) {
//			ShapeRoi replace = null;
//			ShapeRoi sRoi = null;
//			if (!(roi instanceof ShapeRoi)) {
//				sRoi = new ShapeRoi(roi);
//			}else {
//				sRoi = (ShapeRoi)roi;
//			}
//			replace = sRoi.or(brush);
//			replace.copyAttributes(roi);//no update roi id
//			replace.setProperty(RoiContextKeySet.RoiID.name(), roi.getProperty(RoiObj.RoiContextKeySet.RoiID.name()));
//			slide.replaceRoi(roi.getStudyUID(), roi.getSeriesUID(), roi.getSopUID(), roi.getProperty(RoiObj.RoiContextKeySet.RoiID.name()), replace);
//		}else {
//			slide.addRoi(brush);
//		}
//	}
	
	void add(RoiObj roi, ShapeRoi brush, int x, int y) {
		if(roi != null) {
			ShapeRoi replace = null;
			ShapeRoi sRoi = new ShapeRoi(roi);//did copyAttributes
			replace = sRoi.or(brush);
//			replace.copyAttributes(roi);//no update RoiID
			replace.setProperty(ContextKey.RoiID.name(), roi.getProperty(ContextKey.RoiID.name()));
//			slide.replaceRoi(roi.getStudyUID(), roi.getSeriesUID(), roi.getSopUID(), roi.getProperty(RoiObj.RoiContextKeySet.RoiID.name()), replace);
			slide.updateRoi(replace);
		}else {
			slide.addRoi(brush);
		}
	}

	void subtract(RoiObj roi, ShapeRoi brush, int x, int y) {
		if (roi!=null) {
			if (!(roi instanceof ShapeRoi)) {
				roi = new ShapeRoi(roi);
			}
			((ShapeRoi)roi).not(brush);
//			roi.copyAttributes(roi);
			System.out.println(roi.getContainedFloatPoints().xpoints.length);
			if(roi.getContainedFloatPoints().xpoints.length <= 4) {
				slide.deleteRoi(roi);
				return;
			}
		} else {
			roi = brush;
		}
		slide.updateRoi(roi);
	}

    
	ShapeRoi getCircularRoi(int x, int y, int width) {
//		if (poly==null) {
//			RoiObj roi = new OvalRoi(x-width/2, y-width/2, width, width,slide);
//			poly = roi.getPolygon();
//			for (int i=0; i<poly.npoints; i++) {
//				poly.xpoints[i] -= x;
//				poly.ypoints[i] -= y;
//			}
//		}
//		return new ShapeRoi(x-width/2, y-width/2, poly, slide);		
		RoiObj roi = new OvalRoi(x-width/2, y-width/2, width, width,slide);
		return new ShapeRoi(x-width/2, y-width/2, roi.getPolygon(), slide);
	}
	
	ShapeRoi getSquareRoi(int x, int y, int width) {
//		if (poly==null) {
//			RoiObj roi = new RoiObj(x-width/2, y-width/2, width, width, 0, slide);
//			poly = roi.getPolygon();
//			for (int i=0; i<poly.npoints; i++) {
//				poly.xpoints[i] -= x;
//				poly.ypoints[i] -= y;
//			}
//		}
//		return new ShapeRoi(x-width/2, y-width/2, poly, slide);
		RoiObj roi = new RoiObj(x-width/2, y-width/2, width, width, 0, slide);
		return new ShapeRoi(x-width/2, y-width/2, roi.getPolygon(), slide);
	}
	
	int getBrushSize() {
		String sizeStr = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushSize); 
		if(sizeStr == null) {
			return defaultSize;
		}else {
			return Integer.valueOf(sizeStr.trim());
		}
	}
	
	String getBrushType() {
		String type = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushType); 
		if(type == null) {
			type = defaultType;
		}
		return type;
	}

	@Override
	public void run() {
		createBrush(me);
	}

}