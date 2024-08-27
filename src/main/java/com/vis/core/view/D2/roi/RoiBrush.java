package com.vis.core.view.D2.roi;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.ContextKey;
import com.vis.configuration.GraphyProp;
import com.vis.core.log.Log;
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

public class RoiBrush {
	final static int ADD=0, SUBTRACT=1;
	final static int leftClick=InputEvent.BUTTON1_DOWN_MASK, alt=InputEvent.ALT_DOWN_MASK, shift=InputEvent.SHIFT_DOWN_MASK;
//	private Polygon poly;
	private Point previousP;
	private int mode = ADD;
	
	int defaultSize = 11;//keep odd number.
	/*
	 * circle
	 * rectangle
	 */
	String defaultType = "Circle";
	
	SlideGlass slide = null;
	
	ShapeRoi brush = null;
	
	RoiObj currentBrushingRoi = null;
	
	public RoiBrush(SlideGlass slide, MouseEvent pressedEvent, boolean createBrush) {
		this.slide = slide;
		if(createBrush) {
			createBrush(pressedEvent);
		}
	}
	
	
	/**
	 * mousePressed : create brush and add or subtract roi.
	 * mouseDragged : create brush and add or subtract roi.
	 */
	public void createBrush(MouseEvent pressedEvent) {
		if (slide == null)
			return;
		int slideX = pressedEvent.getX();
		int slideY = pressedEvent.getY();
		int size = getBrushSize();
		int ox = slide.offScreenX(slideX);
		int oy = slide.offScreenY(slideY);
		
		/*
		 * build any time
		 */
		String type = getBrushType();
		if (type.toLowerCase().equals("circle")) {
			brush = getCircularRoi(ox, oy, size);
		} else {
			brush = getSquareRoi(ox, oy, size);
		}
		brush.setActiveOverlayRoi(false);
		slide.setRoiBrush(brush);
		slide.repaint();// show brush
		
		//roibrush origin was shifted half-size of brush width.
		Log.logger.fine("RoiBrush created on(offscreen), x:"+brush.x+", y:"+brush.y);
		
		brushRoi(pressedEvent);
//		SwingUtilities.invokeLater(() -> {
//			brushRoi(pressedEvent);
//        });
	}
	
	/**
	 * brush dragged
	 */
	public void brushDragged(MouseEvent e) {
		if(brush == null) {
			Log.logger.fine("RoiBrush -Dragging-: Brush null");
			return;
		}
		
		if(brush == null) {
			createBrush(e);
		}
		
		int sx = e.getX();
		int sy = e.getY();
		
		int RoiOffset = getBrushSize()/2;
		
		int ox = slide.offScreenX(sx);
		int oy = slide.offScreenY(sy);
		
		int xNew = ox-RoiOffset;
		int yNew = oy-RoiOffset;
		
		int dx = xNew - brush.startX;
		int dy = yNew - brush.startY;
		
		Polygon p = brush.getPolygon();
		Polygon poly = (Polygon) ShapeRoi.cloneShape(p);
		/*
		 * ShapeRoi:getPolygon() adding getXYBase of each point.
		 * Here subtract it.
		 */
		for(int i=0; i<poly.npoints; i++) {
//			Log.logger.fine("Xpoints:"+poly.xpoints[i]+", Ypoints:"+poly.ypoints[i]);
			poly.xpoints[i] -= brush.getXBase();
			poly.ypoints[i] -= brush.getYBase();
//			Log.logger.fine("Xpoints:"+poly.xpoints[i]+", Ypoints:"+poly.ypoints[i]);
			poly.xpoints[i] += dx;
			poly.ypoints[i] += dy;
		}
		brush.setShape(poly);
		
		brush.x += dx;
		brush.y += dy;
		//see, RoiObj.move()
		brush.oldX = brush.x;
		brush.oldY = brush.y;
		brush.startX = xNew;
		brush.startY = yNew;
		slide.lastDraggedX = sx;
		slide.lastDraggedY = sy;
		Log.logger.fine("dragging brushX:"+brush.x+", brushY:"+brush.y);
		
		brushRoi(e);
//		SwingUtilities.invokeLater(() -> {
//			brushRoi(e);
//        });
	}
	
	/*
	 * mouseUp
	 */
	public void brushingEnd(){
		slide.setRoiBrush(null);
		slide.repaint();
	}
	
	public void brushRoi(MouseEvent e) {
		int slideX = e.getX();
		int slideY = e.getY();
		int ox = slide.offScreenX(slideX);
		int oy = slide.offScreenY(slideY);
		Point p = new Point(ox, oy);
		mode = -1;//reset
		if(currentBrushingRoi == null) {
			currentBrushingRoi = slide.getRoiLocationAt(slideX, slideY);
		}else {
			if (currentBrushingRoi.isArea()) {
				if (!currentBrushingRoi.contains(p.x, p.y)) {
					mode = SUBTRACT;
				}
			}
		}
		if(previousP != null) {
			if(p.equals(previousP)) {
				return;
			}
		}
		previousP = p;
		int flags = e.getModifiersEx();
		if ((flags&InputEvent.SHIFT_DOWN_MASK)!=0) {
			mode = ADD;
		}else if ((flags&InputEvent.ALT_DOWN_MASK)!=0) {
			mode = SUBTRACT;
		}
		if (mode==ADD) {
			Log.logger.fine("RoiBrush: ADD MODE");
			add(currentBrushingRoi/*null-able*/, brush, p.x, p.y);
		}else {
			Log.logger.fine("RoiBrush: SUBTRACT MODE");
			subtract(currentBrushingRoi, brush, p.x, p.y);
		}
	}
	
	void add(RoiObj roi, ShapeRoi brush, int x, int y) {
		if(roi != null) {
			ShapeRoi sRoi = new ShapeRoi(roi);//done copyAttributes
			ShapeRoi replace = sRoi.or(brush);
//			replace.copyAttributes(roi);//no update RoiID
			replace.setProperty(ContextKey.RoiID.name(), roi.getProperty(ContextKey.RoiID.name()));
//			slide.replaceRoi(roi.getStudyUID(), roi.getSeriesUID(), roi.getSopUID(), roi.getProperty(RoiObj.RoiContextKeySet.RoiID.name()), replace);
			slide.addRoi(roi);
			currentBrushingRoi = replace;
		}else {
			ShapeRoi r = (ShapeRoi)brush.clone();
			slide.addRoi(r);
			currentBrushingRoi = r;
		}
	}
	
	public void setCurrentBrushingRoi(RoiObj roi) {
		if(roi == null || !roi.isArea()) {
			currentBrushingRoi = null;
			return;
		}
		currentBrushingRoi = roi;
	}

	void subtract(RoiObj roi, ShapeRoi brush, int x, int y) {
		if (roi!=null) {
			if (!(roi instanceof ShapeRoi)) {
				roi = new ShapeRoi(roi);
			}
			roi = ((ShapeRoi)roi).not(brush);
			if(roi.getContainedFloatPoints().xpoints.length <= 4 || (roi.width <= 0 && roi.height <= 0)) {
				slide.deleteRoi(roi);
				currentBrushingRoi = null;
				return;
			}else {
				slide.updateRoi(roi);
			}
		}
	}
	
	ShapeRoi getCircularRoi(int x, int y, int width) {
		RoiObj roi = new OvalRoi(x-(int)Math.floor(width/2), y-(int)Math.floor(width/2), width, width, slide);
		Polygon poly = roi.getPolygon();
		return new ShapeRoi(roi.x, roi.y, poly, slide);
	}
	
	ShapeRoi getSquareRoi(int x, int y, int width) {
		RoiObj roi = new RoiObj(x-(int)Math.floor(width/2), y-(int)Math.floor(width/2), width, width, 0, slide);
		return new ShapeRoi(roi);
	}
	
	/**
	 * original image coordinate scale size.
	 * @return
	 */
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
}