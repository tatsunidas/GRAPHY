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

import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

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
	
	public void clearCurrentBrushingRoi() {
		currentBrushingRoi = null;
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
		
		int RoiOffset = getBrushSize()/2;//original image scale 
		
		int ox = slide.offScreenX(sx);
		int oy = slide.offScreenY(sy);
		
		int xNew = ox-RoiOffset;
		int yNew = oy-RoiOffset;
		
		int dx = xNew - brush.startX;
		int dy = yNew - brush.startY;
		
		/*
		 * MEMO
		 * ShapeRoi:getPolygon() adding getXYBase of each point.
		 * If you want processing without roi origin, subtract it.
		 */
//		Polygon poly = brush.getPolygon();
//		for(int i=0; i<poly.npoints; i++) {
//			poly.xpoints[i] -= brush.getXBase();
//			poly.ypoints[i] -= brush.getYBase();
//		}
//		brush.setShape(poly);
		
		/*
		 * ShapeRoi getXYBase() return ShapeRoi.x ShapeRoi.y.
		 * (Not using bounds.xy)
		 */
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
		if(currentBrushingRoi != null) {
			slide.saveRoi(currentBrushingRoi);
		}
		clearCurrentBrushingRoi();
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
				}else {
					mode = ADD;
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
			replace.setProperty(ContextKey.RoiID.name(), roi.getProperty(ContextKey.RoiID.name()));
			slide.addRoi(replace);
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
		Log.logger.fine("Brush Oval Location: x:"+roi.x+" , y:"+roi.y);
		/* 
		 * OvalRoi.getPolygon is return adding roi origin offset to all points.
		 * new ShapeRoi(poly, slide) will perform subtract roi origin again.
		 */
		Polygon poly = roi.getPolygon();
		return new ShapeRoi(poly, slide);
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