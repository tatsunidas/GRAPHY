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
package com.vis.core.view.D2.ui.glasses;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.JTextArea;

import com.vis.configuration.ContextKey;
import com.vis.core.log.Log;
import com.vis.core.util.Platform;
import com.vis.core.view.D2.roi.*;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.core.view.mpr.CenterPositionLine;
import com.vis.core.view.mpr.ReferenceLineMPR;
import com.vis.db.DatabaseHandler;

/**
 *
 * Overlay glass of slideglass. As works for canvas of RoiObj.
 * 
 * @author Tatsunidas
 *
 */
public class CanvasGlass extends javax.swing.JPanel {

	private static final long serialVersionUID = 775809436040950583L;
	private Praparat pp;
	private SlideGlass sg;
	private ArrayList<RoiObj> roiset;
	private final String sopUID;
	public boolean paintSizeCaliper = true;
	private RoiObj currentRoi = null;
	private RoiBrush brushTool = null;
	private RoiObj brush = null;//roi brush, see also draw()
	
	private GeneralPath crossLine = null;
	private Color crossLineColor = new Color(31, 255, 0, 127);
	private int crossLineStrokeSize = 3;
	
	private java.util.List<java.awt.geom.Point2D> localizerGeo = null;
	private Color localizerColor = new Color(255, 0, 0, 127);
	private int localizerStrokeSize = 1;

	boolean rect = false;
	
	public CanvasGlass(SlideGlass sg) {
		setOpaque(false);
		setLayout(null);
		setDoubleBuffered(true);//fail safe
		this.sopUID = sg.getSOPInstanceUID();
		this.pp = sg.getPraparat();
		this.sg = sg;
		this.roiset = new ArrayList<RoiObj>();
	}

	/*
	 * slide XY, prap basis.
	 */
	protected RoiObj activateAndGetCurrentRoiAt(int screenX, int screenY) {

		if (currentRoi != null) {
			// polygonroi
			int type = currentRoi.getType();
			RoiType t = RoiType.find(type);
			if ((t == RoiType.POLYGON || t == RoiType.POLYLINE || t == RoiType.ANGLE || t == RoiType.LINE  || t == RoiType.MULTIPOINT)
					&& currentRoi.getState() == RoiObj.CONSTRUCTING) {
				return currentRoi;
			}
		}

		int ix = sg.offScreenX(screenX);
		int iy = sg.offScreenY(screenY);
		ArrayList<RoiObj> rois = getRoiSet();
		int handle = -1;
		boolean found = false;
		if (rois != null && rois.size() > 0) {
			// reset activate
			for (RoiObj roi : rois) {
				roi.setActiveOverlayRoi(false);
			}
			for (RoiObj roi : rois) {
				handle = roi.isHandle(screenX, screenY);
				if (handle >= 0) {
					roi.setActiveOverlayRoi(true);
					currentRoi = roi;
					found = true;
					break;
				} 
				if (roi.contains(ix, iy)) {
					roi.setActiveOverlayRoi(true);
					currentRoi = roi;
					found = true;
					break;
				}
			}
			if (handle >= 0) {
				sg.setCursor(new Cursor(Cursor.HAND_CURSOR));
			} else if (found && currentRoi.contains(ix, iy)) {
				sg.setCursor(new Cursor(Cursor.MOVE_CURSOR));
			} else {
				sg.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
				currentRoi = null;
			}
		}
		return currentRoi;
	}
	
	public void addRoi(RoiObj newRoi) {
		if (newRoi instanceof CenterPositionLine) {
			//For MPR.
			return;
		}
		if (isExistsInRoiSet(newRoi)) {
			HashMap<ContextKey, String> uids = newRoi.getUIDs();
			String patID = uids.get(ContextKey.PatientID);
			String studyUID = uids.get(ContextKey.StudyInstanceUID);
			String seriesUID = uids.get(ContextKey.SeriesInstanceUID);
			String sopUID = uids.get(ContextKey.SOPInstanceUID);
			String roiID = uids.get(ContextKey.RoiID);
			updateRoi(patID, studyUID, seriesUID, sopUID, roiID, newRoi);
		} else {
			roiset.add(newRoi);
			insertOrUpdateRoi4DB(newRoi);
		}
	}

	public void addRoi(RoiObj newRoi, boolean updateDB) {
		if(newRoi instanceof CenterPositionLine) {
			return;
		}
		if(updateDB) {
			addRoi(newRoi);
		}else {
			if (!this.roiset.contains(newRoi)) {
				roiset.add(newRoi);
			}
		}
	}

	/**
	 * Starts the process of creating a new selection, where sx and sy are the
	 * starting screen coordinates. The selection type is determined by which tool
	 * in the tool bar is active. The user interactively sets the selection size and
	 * shape.
	 */
	public RoiObj createNewRoi(int screenX, int screenY, int roiType) {
		
		int imageX = sg.offScreenX(screenX);//org img X
		int imageY = sg.offScreenY(screenY);//org img Y
		RoiObj roi = null;
		roiType = pp.getCurrentViewerToolType();
		RoiType t = RoiType.find(roiType);
		switch (t) {
		case RECTANGLE://0
			roi = new RoiObj(imageX, imageY, 1, 1, 0, sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case OVAL:
			roi = new OvalRoi((double)imageX, (double)imageY, 1,1,sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case FREEROI:
			roi = new FreehandRoi(imageX, imageY, RoiType.FREEROI.id(),sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case POLYGON:
			roi = new PolygonRoi(imageX, imageY,roiType,sg);
			roi.setState(RoiObj.CONSTRUCTING);//fail safe
			break;
		case ANGLE:
			roi = new PolygonRoi(imageX, imageY,roiType,sg);
			roi.setState(RoiObj.CONSTRUCTING);//fail safe
			break;
		case LINE://5
			/*subPixelResolution is true*/
			roi = new Line((double)imageX, (double)imageY, imageX+1, imageY+1, sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case FREELINE:
			roi = new FreehandRoi(imageX, imageY, RoiType.FREELINE.id(),sg);
			break;
		case POLYLINE:
			roi = new PolygonRoi(imageX, imageY, RoiType.POLYLINE.id(),sg);
			break;
		case ARROW:
			roi = new Arrow(imageX, imageY, imageX+1, imageY+1, sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case TEXT:
			roi = new TextRoi(imageX, imageY, null, sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case POINT:
			roi = new PointRoi(imageX, imageY, sg);
			break;
		case MULTIPOINT:
			roi = new PointRoi(imageX, imageY, RoiType.MULTIPOINT.id(), sg);
			break;
//			if (Prefs.pointAddToOverlay) {
//				int measurements = Analyzer.getMeasurements();
//				if (!(Prefs.pointAutoMeasure && (measurements & Measurements.ADD_TO_OVERLAY) != 0))
//					IJ.run(this, "Add Selection...", "");
//				Overlay overlay2 = getOverlay();
//				if (overlay2 != null)
//					overlay2.drawLabels(!Prefs.noPointLabels);
//				Prefs.pointAddToManager = false;
//			}
//			if (Prefs.pointAutoMeasure || (Prefs.pointAutoNextSlice && !Prefs.pointAddToManager))
//				IJ.run(this, "Measure", "");
//			if (Prefs.pointAddToManager) {
//				IJ.run(this, "Add to Manager ", "");
//				ImageCanvas ic = getCanvas();
//				if (ic != null) {
//					RoiManager rm = RoiManager.getInstance();
//					if (rm != null) {
//						if (Prefs.noPointLabels)
//							rm.runCommand("show all without labels");
//						else
//							rm.runCommand("show all with labels");
//					}
//				}
//			}
//			if (Prefs.pointAutoNextSlice && getStackSize() > 1) {
//				IJ.run(this, "Next Slice [>]", "");
//				deleteRoi();
//			}
		default:
			//do nothig
		}
		if (roi != null) {
			addRoi(roi);
			repaint();
		}
		return roi;
	}
	
	public void deleteRoi(int sx, int sy) {
		if (roiset == null || roiset.size() < 1) {
			return;
		}
		RoiObj roi2remove = activateAndGetCurrentRoiAt(sx, sy);
		if (roi2remove != null) {
			HashMap<ContextKey, String> uids = roi2remove.getUIDs();
			String patID = uids.get(ContextKey.PatientID);
			String studyUID = uids.get(ContextKey.StudyInstanceUID);
			String seriesUID = uids.get(ContextKey.SeriesInstanceUID);
			String sopUID = uids.get(ContextKey.SOPInstanceUID);
			String roiID = uids.get(ContextKey.RoiID);
			deleteRoi(patID, studyUID, seriesUID, sopUID, roiID);
		}
		repaint();
	}
	
	public void deleteRoi(RoiObj roi2remove) {
		if (roiset == null || roiset.size() < 1) {
			return;
		}
		HashMap<ContextKey, String> uids = roi2remove.getUIDs();
		String patID = uids.get(ContextKey.PatientID);
		String studyUID = uids.get(ContextKey.StudyInstanceUID);
		String seriesUID = uids.get(ContextKey.SeriesInstanceUID);
		String sopUID = uids.get(ContextKey.SOPInstanceUID);
		String roiID = uids.get(ContextKey.RoiID);
		deleteRoi(patID, studyUID, seriesUID, sopUID, roiID);
		if(brushTool != null) {
			brushTool.clearCurrentBrushingRoi();
		}
	}

	public void deleteRoi(String patID, String studyUID, String seriesUID, String sopUID, String roiId) {
		if (roiset == null || roiset.size() < 1) {
			return;
		}
		/*
		 * pay attention remove item from list
		 * see, https://stackoverflow.com/questions/8104692/how-to-avoid-java-util-concurrentmodificationexception-when-iterating-through-an
		 */
		Iterator<RoiObj> itr = roiset.iterator();
		ArrayList<RoiObj> roi2Remove = new ArrayList<>();
		while(itr.hasNext()){
		    RoiObj roi = itr.next();
			if (roi.isThisRoi(patID, studyUID, seriesUID, sopUID, roiId)) {
//				removeRoiPopupDialogOnCanvas(roi.getRoiPopupDialog());
				if(roi instanceof TextRoi) {
					Component[] coms = getComponents();
					for(Component c : coms) {
						if(c instanceof JTextArea) {
							if(c.getName().equals(roiId)) {
								remove(c);
							}
						}
					}
				}
				deleteRoiFromDB(roi);
				roi2Remove.add(roi);
				
				break;
			} else if (studyUID == null && seriesUID == null && sopUID == null) {
				// SliceLine or temporal roi
				// skip delete from db
				/*
				 * TODO 20240817
				 */
//				removeRoiPopupDialogOnCanvas(roi.getRoiPopupDialog());
				roi2Remove.add(roi);
			}
		}
		if(roiset.size() > 0) {
			roiset.removeAll(roi2Remove);
		}
		setCurrentRoi2NULL();
	}

	private void deleteRoiFromDB(RoiObj roi) {
		HashMap<ContextKey, String> uids = roi.getUIDs();
		String patID = uids.get(ContextKey.PatientID);
		String studyUID = uids.get(ContextKey.StudyInstanceUID);
		String seriesUID = uids.get(ContextKey.SeriesInstanceUID);
		String sopUID = uids.get(ContextKey.SOPInstanceUID);
		String roiID = uids.get(ContextKey.RoiID);
		DatabaseHandler.getInstance().deleteRoi(patID, studyUID, seriesUID,sopUID,roiID);
		if(Viewer2DScreen.getRoiObjManager() != null) {
			RoiObjManager rom = Viewer2DScreen.getRoiObjManager();
			rom.updateRoiObjList(sg.getPatientID());
		}
	}
	
	public void createCross(MouseEvent e) {
		//No AffineTransform
		Point p = e.getPoint();
		GeneralPath path = new GeneralPath();
		int ix = sg.offScreenX(p.x);
		int iy = sg.offScreenY(p.y);
		path.moveTo(0f, iy);
		path.lineTo(sg.getOriginalImage().getWidth(), iy);
		path.moveTo(ix, 0f);
		path.lineTo(ix, sg.getOriginalImage().getHeight());
		setCrossLine(path);
		repaint();
	}
	
	private void drawCross(Graphics g) {
		if(crossLine != null) {
			Graphics2D g2 = (Graphics2D)g;
			g2.setColor(crossLineColor);
			g2.setStroke(new BasicStroke(crossLineStrokeSize));
			g2.draw(crossLine);
		}
	}

	private void drawCanvas(Graphics g) {
		//AffinTransform is done in performed by paintComponent.
		drawRoi(g);
		drawReferenceLine(g);
		drawLocalizerLine(g);
		drawCross(g);
	}

	private void drawLocalizerLine(Graphics g) {
		if (localizerGeo != null && pp.getReferenceLineMPR()== null) {
			Point2D p0_leftUpper = localizerGeo.get(0);
			Point2D p1_rightUpper = localizerGeo.get(1);
			Point2D p2_rightLower = localizerGeo.get(2);
			Point2D p3_leftLower = localizerGeo.get(3);
			GeneralPath loca = new GeneralPath();
			loca.moveTo(p0_leftUpper.getX(), p0_leftUpper.getY());
			loca.lineTo(p1_rightUpper.getX(), p1_rightUpper.getY());
			loca.lineTo(p2_rightLower.getX(), p2_rightLower.getY());
			loca.lineTo(p3_leftLower.getX(), p3_leftLower.getY());
			loca.lineTo(p0_leftUpper.getX(), p0_leftUpper.getY());
			Graphics2D g2 = (Graphics2D) g;
			g2.setColor(localizerColor);
			g2.setStroke(new BasicStroke(localizerStrokeSize));
			g2.draw(loca);
		}
	}
	
	/**
	 * for reslice
	 * @param g
	 */
	private void drawReferenceLine(Graphics g) {
		ReferenceLineMPR refLineMPR = pp.getReferenceLineMPR();
		if (refLineMPR != null) {
			refLineMPR.draw(g, pp.getName()/*XY XZ YZ*/);
		}
	}

	private void drawRoi(Graphics g) {
		for (int i = 0; i < roiset.size(); i++) {
			RoiObj roiObj = roiset.get(i);
			roiObj.draw(g);
		}
		
		if(brush != null) {
			brush.draw(g);
		}
	}
	
	public RoiObj findCurrentRoi() {
		return getActiveRoi();
	}
	
	public RoiObj getActiveRoi() {
		for (RoiObj roi : roiset) {
			if (roi.isActiveOverlayRoi()) {
				return roi;
			}
		}
		return null;
	}
	
	protected RoiObj getBrush() {
		return brush;
	}
	
	public RoiObj getCurrentRoi() {
		return currentRoi;
	}
	
	/**
	 * 
	 * @param screenX:slideX
	 * @param screenY:slideY
	 * @return
	 */
	public RoiObj getRoiLoacationAt(int screenX, int screenY) {

		int ix = sg.offScreenX(screenX);
		int iy = sg.offScreenY(screenY);
		/*
		 * if rois are overlapping, return roi that find first.
		 */
		if (roiset != null && roiset.size() > 0) {
			for (RoiObj roi : roiset) {
				if (roi.contains(ix, iy)) {
					return roi;
				}
			}
		}
		return null;
	}
	
	public RoiPopUpDialog getRoiPopupAt(int slideX, int slideY) {
		/*
		 * MouseEventのgetXYでは、
		 * RoiPopupDialogがJPanelのサブクラスならslideXYのままでいいのだけど TextAreaにすると座標がリセットされる
		 */
		Component com = getComponentAt(slideX, slideY);
		if (com != null && com instanceof RoiPopUpDialog) {
			return (RoiPopUpDialog) com;
		} else {
			return null;
		}
	}
	
	public RoiPopUpDialog getRoiPopupAt(MouseEvent e) {
		Object obj = e.getSource();
		if(obj != null && obj instanceof RoiPopUpDialog) {
			return (RoiPopUpDialog)obj;
		}else {
			return null;
		}
	}
	
	ArrayList<RoiObj> getRoiSet(){
		return roiset;
	}

	public void handleRoiBrushMouseDown(MouseEvent e) {
		//see also mouseMove.
		if(brushTool == null) {
			brushTool = new RoiBrush(sg, e, true/*brush appear*/);
		}else {
			brushTool.createBrush(e);
		}
		//update dragging point
		sg.lastDraggedX = e.getX();
		sg.lastDraggedY = e.getY();
	}
	
	public void roiMouseDown(MouseEvent e) {
		if(e.isConsumed()) {
			return;
		}
		int sx = e.getX();//slide screen x (praparat view coordinates)
		int sy = e.getY();//slide screen y (praparat view coordinates)
		int roiType = pp.getCurrentViewerToolType();
		CenterPositionLine cenLine = centerPositionLineHereAt(sx,sy);
		if(cenLine != null) {
			int handle = cenLine.isHandle(sx, sy);
			cenLine.mouseDownInHandle(handle, sx, sy);
			cenLine.mouseDown(e);
			return;
		}
		if(currentRoi != null && (currentRoi instanceof PolygonRoi) && roiType==RoiType.POLYGON.id() && (currentRoi.getState() == RoiObj.CONSTRUCTING)) {
			return;
		}
		//get currentRoi
		currentRoi = activateAndGetCurrentRoiAt(sx, sy);
		
		if (currentRoi != null){
			if(sg.isHereRoiPopup(e)) {
				//NORTICE; if mouse on RoiPopup, slideXY is change to RoiPopUp origin...
				RoiPopUpDialog dialog = getRoiPopupAt(e);
				dialog.mousePressed(e);
				return;
			}
			currentRoi.mouseDown(e);
		}else {
			if(sg.isHereRoiPopup(e)) {
				//NORTICE; if mouse on RoiPopup, slideXY is change to RoiPopUp origin...
				RoiPopUpDialog dialog = getRoiPopupAt(e);
				dialog.mousePressed(e);
				return;
			}
			currentRoi = createNewRoi(sx, sy,roiType);
		}
	}

	public boolean handleRoiMouseDragged(MouseEvent e) {
		int dragSX = e.getX();//x on slideglass
		int dragSY = e.getY();
		int flags = e.getModifiersEx();//input event flag
		/*
		 * drag roi or popup
		 */
		int roiType = pp.getCurrentViewerToolType();//from viewer2d
		if(roiType == Viewer2DToolBar.Brush) {
			if(brushTool != null && brush != null) {
				brushTool.brushDragged(e);
			}
			sg.lastDraggedX = dragSX;
			sg.lastDraggedY = dragSY;
			return true;
		}
		boolean dragging = false;
		if (flags==0 && Platform.isMac()) {
			// workaround for Mac OS 9 bug
			flags = InputEvent.BUTTON1_DOWN_MASK;
		}
		//is reference line?
		CenterPositionLine cenLine = centerPositionLineHereAt(dragSX, dragSY);
		if(cenLine != null) {
			cenLine.mouseDrag(dragSX, dragSY, flags);
			sg.lastDraggedX = dragSX;
			sg.lastDraggedY = dragSY;
			return true;
		}
		//is dialog ?
		if(isHereRoiPopup(e)) {
//			System.out.println("RoiDialog DRAGGING!!!");
			RoiPopUpDialog dialog = getRoiPopupAt(e);
			if(dialog != null) {
				dialog.mouseDragged(e);
				dragging = true;
			}
		//is roi ?
		}else {
			if (currentRoi != null) {
				currentRoi.mouseDrag(dragSX, dragSY, flags);
//			if(currentRoi instanceof OvalRoi) {
//				OvalRoi oval = (OvalRoi)currentRoi;
//				oval.handleMouseDrag(dragSX, dragSY, flags,sg);
//			}else {
//				
//			}
				dragging = true;
			}
		}
		
		sg.lastDraggedX = dragSX;
		sg.lastDraggedY = dragSY;
		return dragging;
	}

	public void hideRoiDialogAt(int sx, int sy) {
		Component com = getComponentAt(sx, sy);
		if (com != null && com instanceof RoiPopUpDialog) {
			RoiPopUpDialog rpd = (RoiPopUpDialog) com;
			rpd.setVisible(false);
			repaint();
		}
	}
	
	public void hideRoiDialogOf(RoiObj roi) {
		if (roi == null) {
			return;
		}
//		roi.setVisibleRoiPopup(false);//todo
		repaint();
	}
	
	private void initRoiSet() {
		roiset = null;
		roiset = new ArrayList<RoiObj>();
	}
	
	public void insertOrUpdateRoi4DB(RoiObj roi) {
		if(roi == null) {
			return;
		}
		//save as new or update
		if(DatabaseHandler.getInstance() != null) {
			DatabaseHandler.getInstance().insertRoi(roi.readContext());
		}
	}
	
	private boolean isExistsInRoiSet(RoiObj newRoi) {
		ArrayList<RoiObj> currentRoiSet = getRoiSet();
		int size = currentRoiSet.size();
		for(int i =0;i<size;i++) {
			RoiObj r = currentRoiSet.get(i);
			if(r.isThisRoi(newRoi)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isHereRoiPopup(MouseEvent e) {
		Object obj = e.getSource();
		if (obj instanceof RoiPopUpDialog) {
			return true;
		} else {
			return false;
		}
	}
	
	public void loadRoiFromDB() {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			return;
		}
		String pid = sg.getPatientID();
		String studyUid = sg.getStudyInstanceUID();
		String seriesUid = sg.getSeriesInstanceUID();
		String sopUid = sopUID;
		ArrayList<HashMap<String,Object>> cons = db.loadRoiContextFromInstance(pid, studyUid, seriesUid, sopUid);
		if(cons != null && cons.size() > 0) {
			for(int i=0; i<cons.size(); i++) {
				RoiObj roi = new RoiConverter().buildRoiObj(cons.get(i));
				if(roi == null) {
					continue;
				}
				roi.setSlideGlass(sg);
				addRoi(roi, false/*update db*/);
			}
		}
	}
	
	public void mouseMoved(MouseEvent e) {
		//update currentRoi
		activateAndGetCurrentRoiAt(e.getX(), e.getY());
//		Log.logger.fine("CanvasComponent: "+getComponentAt(e.getX(),e.getY()).getName());
		int type = currentRoi != null ? currentRoi.getType() : -1;
		if (type>0 && (type==RoiType.POLYGON.id()||type==RoiType.POLYLINE.id()||type==RoiType.ANGLE.id()||type==RoiType.LINE.id()||type==RoiType.MULTIPOINT.id()) 
		&& currentRoi.getState()==RoiObj.CONSTRUCTING) {
			currentRoi.mouseMoved(e);
		}
	}
	
	public void mouseDragged(MouseEvent e) {
		if (pp.isShowCrossLineMode()) {
			createCross(e);
		}
		int dragSX = e.getX();
		int dragSY = e.getY();
		int flags = e.getModifiersEx();
		//Brush tool
		int roiType = pp.getCurrentViewerToolType();//from viewer2d
		if(roiType == Viewer2DToolBar.Brush) {
			if(brushTool == null) {
				return;
			}
			brushTool.brushDragged(e);
			return;
		}
		if (flags==0 && Platform.isMac()) {
			// workaround for Mac OS 9 bug
			flags = InputEvent.BUTTON1_DOWN_MASK;
		}
		//is reference line?
		//see also mousePressed.
		CenterPositionLine cpl = centerPositionLineHereAt(dragSX, dragSY);
		if(cpl != null) {
			Log.logger.fine("ReferenceLine Dragging");
			cpl.mouseDrag(dragSX, dragSY, flags);
			sg.lastDraggedX = dragSX;
			sg.lastDraggedY = dragSY;
			return;
		}
		//is roi info dialog ?
		if(sg.isHereRoiPopup(e)) {
//			System.out.println("RoiDialog DRAGGING!!!");
			RoiPopUpDialog dialog = getRoiPopupAt(e);
			if(dialog != null) {
				dialog.mouseDragged(e);
			}
		//is roi ?
		}else {
			if (currentRoi != null) {
				currentRoi.mouseDrag(dragSX, dragSY, flags);
			}
		}
		sg.lastDraggedX = dragSX;
		sg.lastDraggedY = dragSY;
	}
	
	public void mousePressed(MouseEvent e) {
		int toolID = pp.getViewer2DToolType();
		int sx = e.getX();
		int sy = e.getY();
		sg.mouseX = sx; 
		sg.mouseY = sy;
		switch (toolID) {
		case Viewer2DToolBar.Brush:
			handleRoiBrushMouseDown(e);
			e.consume();
			break;
		default:
			roiMouseDown(e);
		}
	}

	public void mouseReleased(MouseEvent emr) {
		if(currentRoi != null) {
			currentRoi.handleMouseUp(emr.getX(), emr.getY());
			if(currentRoi.getState() != RoiObj.CONSTRUCTING) {
				saveCurrentRoiSate();
			}
		}
		//brush
		if(pp.getCurrentViewerToolType()==Viewer2DToolBar.Brush) {
			if(brushTool != null) {
				brushTool.brushingEnd();
				repaint();
			}
		}
	}
	
	public void mouseDoubleClicked(MouseEvent e) {
		if(e.isConsumed()) {
			return;
		}
		if(currentRoi != null && currentRoi instanceof TextRoi) {
			TextRoi tr = (TextRoi) currentRoi;
			if(tr.isFocusable()) {
				tr.setFocusable(false);
			}else {
				tr.setFocusable(true);
			}
		}
	}
	
	/**
	 * 
	 * @param keyCode
	 * @param mex
	 * @return move roi done or not
	 */
	public boolean keyPressed(int keyCode, int mex/* ModifiersEx */) {
		boolean doingSomething = false;
		if ((mex & InputEvent.ALT_DOWN_MASK) != 0) {
			doingSomething = true;
		}
		if ((mex & InputEvent.SHIFT_DOWN_MASK) != 0) {
			doingSomething = true;
		}
		if ((mex & InputEvent.CTRL_DOWN_MASK) != 0) {
			doingSomething = true;
		}
		if ((mex & InputEvent.META_DOWN_MASK) != 0) {
			doingSomething = true;
		}
		if ((mex & InputEvent.BUTTON1_DOWN_MASK) != 0) {
			doingSomething = true;
		}
		if(doingSomething) {
			return false;
		}
		if(currentRoi == null) {
			return false;
		}
		RoiType t = currentRoi.getRoiType();
		//move roi
		switch (t) {
		case RECTANGLE:
		case OVAL:
		case POLYGON:
		case POLYLINE:
		case ANGLE:
		case FREEROI:
		case COMPOSITE:
		case TRACED_ROI:
			if (keyCode == KeyEvent.VK_LEFT) {
				currentRoi.x -= 1;
			} else if (keyCode == KeyEvent.VK_RIGHT) {
				currentRoi.x += 1;
			} else if (keyCode == KeyEvent.VK_UP) {
				currentRoi.y -= 1;
			} else if (keyCode == KeyEvent.VK_DOWN) {
				currentRoi.y += 1;
			}
			break;
		case LINE:
		case ARROW:
			Line l = (Line)currentRoi;
			if (keyCode == KeyEvent.VK_LEFT) {
				l.updateCoordinates(l.x1d-1.0, l.y1d, l.x2d-1.0, l.y2d);
			} else if (keyCode == KeyEvent.VK_RIGHT) {
				l.updateCoordinates(l.x1d+1.0, l.y1d, l.x2d+1.0, l.y2d);
			} else if (keyCode == KeyEvent.VK_UP) {
				l.updateCoordinates(l.x1d, l.y1d-1.0, l.x2d, l.y2d-1.0);
			} else if (keyCode == KeyEvent.VK_DOWN) {
				l.updateCoordinates(l.x1d, l.y1d+1.0, l.x2d, l.y2d+1.0);
			}
			break;
		case POINT:
			PointRoi r = (PointRoi)currentRoi;
//			int activeHandle = r.isHandle(sg.screenX(r.x), sg.screenY(r.y));
			int activeHandle = 0;//single point
			if (keyCode == KeyEvent.VK_LEFT) {
				if (r.xpf != null) {
					r.xpf[activeHandle] -= (float)1;
				} else {
					r.xp[activeHandle] -= 1;
				}
			} else if (keyCode == KeyEvent.VK_RIGHT) {
				if (r.xpf != null) {
					r.xpf[activeHandle] += (float)1;
				} else {
					r.xp[activeHandle] += 1;
				}
			} else if (keyCode == KeyEvent.VK_UP) {
				if (r.ypf != null) {
					r.ypf[activeHandle] -= (float)1;
				} else {
					r.yp[activeHandle] -= 1;
				}
			} else if (keyCode == KeyEvent.VK_DOWN) {
				if (r.ypf != null) {
					r.ypf[activeHandle] += (float)1;
				} else {
					r.yp[activeHandle] += 1;
				}
			}
			break;
		case MULTIPOINT:
			/*
			 * move all
			 */
			PointRoi mp = (PointRoi)currentRoi;
			int npoints = mp.getPolygon().xpoints.length;
			if (keyCode == KeyEvent.VK_LEFT) {
				if (mp.xpf != null) {
					for(int i=0; i< npoints; i++) {
						mp.xpf[i] -= (float)1;
					}
				} else {
					for(int i=0; i< npoints; i++) {
						mp.xp[i] -= (float)1;
					}
				}
			} else if (keyCode == KeyEvent.VK_RIGHT) {
				if (mp.xpf != null) {
					for(int i=0; i< npoints; i++) {
						mp.xpf[i] += (float)1;
					}
				} else {
					for(int i=0; i< npoints; i++) {
						mp.xp[i] += (float)1;
					}
				}
			} else if (keyCode == KeyEvent.VK_UP) {
				if (mp.ypf != null) {
					for(int i=0; i< npoints; i++) {
						mp.ypf[i] -= (float)1;
					}
				} else {
					for(int i=0; i< npoints; i++) {
						mp.ypf[i] -= (float)1;
					}
				}
			} else if (keyCode == KeyEvent.VK_DOWN) {
				if (mp.ypf != null) {
					for(int i=0; i< npoints; i++) {
						mp.ypf[i] += (float)1;
					}
				} else {
					for(int i=0; i< npoints; i++) {
						mp.ypf[i] += (float)1;
					}
				}
			}
			break;
		case TEXT:
			TextRoi tr = (TextRoi)currentRoi;
			Rectangle2D.Double b = tr.getFloatBounds();
			if (keyCode == KeyEvent.VK_LEFT) {
				b.x -= 1;
			} else if (keyCode == KeyEvent.VK_RIGHT) {
				b.x += 1;
			} else if (keyCode == KeyEvent.VK_UP) {
				b.y -= 1;
			} else if (keyCode == KeyEvent.VK_DOWN) {
				b.y += 1;
			}
			tr.setBounds(b);
			break;
		default:
			break;
		}
		updateRoi(currentRoi);
		repaint();
		return true;
	}
	
	/**
	 * Handle draw event on OffScreen (without Caliper).
	 */
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		Graphics2D g2d = (Graphics2D) g;
	    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (paintSizeCaliper) {
			showCaliper(g);
		}
		
		AffineTransform aTx = new AffineTransform();
		double mag = sg.getMagnification();
		double scaleXY[] = sg.getScaleFactor();
		Point offset = sg.getDisplayImageOriginXY();
		//First, translate image origin without mag and component scale.
		aTx.translate(offset.x, offset.y);
		//Second, scale Roi graphics
		aTx.scale(mag*scaleXY[0],mag*scaleXY[1]);
		g2d.setTransform(aTx);
		//then draw all
		drawCanvas(g);
	}
	
	/**
	 * TODO check with Slab class.
	 * set reference line activate color and change cursor
	 * @param screenX
	 * @param screenY
	 * @return
	 */
	protected CenterPositionLine centerPositionLineHereAt(int screenX, int screenY) {
		ReferenceLineMPR refLineMPR = pp.getReferenceLineMPR();
		if (refLineMPR != null) {
			return refLineMPR.centerPositionLineHereAt(pp, screenX, screenY);
		}
		return null;
	}
	
	public void removeRoiPopupDialogOnCanvas(RoiPopUpDialog rpd) {
		if (rpd != null) {
			remove(rpd);
			repaint();
		}
	}
	
	public void replaceRoi(String patID, String beReplacedStudyUID, String beReplacedSeriesUID, String beReplacedSopUID, String beReplacedRoiId, RoiObj roiToReplace) {
		if(roiToReplace == null) {
			return;
		}
		if (roiset != null && roiset.size() > 0) {
			for (RoiObj roi : roiset) {
				if (roi.isThisRoi(patID, beReplacedStudyUID, beReplacedSeriesUID, beReplacedSopUID, beReplacedRoiId)) {
					updateRoi(patID, beReplacedStudyUID, beReplacedSeriesUID, beReplacedSopUID, beReplacedRoiId, roiToReplace);
					break;
				}
			}
		}
	}
	
	public void reset() {
		initRoiSet();
		loadRoiFromDB();
	}

	public void saveCurrentRoiSate() {
		RoiObj roi = findCurrentRoi();
		insertOrUpdateRoi4DB(roi);
	}
	
	public void setBasicStatistics2Popup(RoiObj roi) {
    	if(roi.getState() == RoiObj.CONSTRUCTING) {
    		return;
    	}
    	RoiType t = roi.getRoiType();
    	if(t == RoiType.ARROW || t == RoiType.TEXT || t==RoiType.NOTYPE) {
    		return;
    	}
    	
    	/*
    	 * TODO 
    	 */
    	
//    	if(rpd == null) {
//    		return;
//    	}
//    	rpd.setBasicStats();
    }
    
	//TODO
    public void setVisibleRoiPopup(boolean show) {
//		if(rpd == null || !rpd.roiAlive()) {
//			return;
//		}
//		if(getType() != TEXT && getType() != ARROW) {
//			rpd.setVisible(show);
//		}else {
//			rpd.setVisible(false);
//		}
	}
    
    /**
     * Roi state to be selected or not.
     * @param e
     * @return
     */
    public boolean setSelectStateOfCurrentRoi(MouseEvent e) {
    	currentRoi = activateAndGetCurrentRoiAt(e.getX(), e.getY());
    	if(currentRoi !=null) {
    		if(!currentRoi.isSelected()) {
    			currentRoi.setSelectedState(true);
    		}else {
    			currentRoi.setSelectedState(false);
    		}
    	}
    	return currentRoi != null;
    }
	
	protected void setBrush(RoiObj brush) {
		this.brush = brush;
	}
	
	/**
	 * keep null-able.
	 */
	public void setCrossLine(GeneralPath cross) {
		//keep null-able
		this.crossLine = cross;
	}
	
	public void setCrossLineColor(Color crossColor) {
		if(crossColor != null) {
			this.crossLineColor = crossColor;
		}
	}
	
	public void setCrossLineStrokeSize(int strokeSize) {
		if(strokeSize > 30) {
			strokeSize = 30;
		}else if(strokeSize < 1) {
			strokeSize = 1;
		}
		crossLineStrokeSize = strokeSize;
	}

	protected void setCurrentRoi2NULL() {
		currentRoi = null;
	}
	
	public void setLocalizerColor(Color color) {
		if(color != null) {
			this.localizerColor = color;
		}
	}
	public synchronized void setLocalizerGeometry(java.util.List<java.awt.geom.Point2D> localizerGeo) {
		//keep null-able
		this.localizerGeo = localizerGeo;
	}

	public void setLocalizerStrokeSize(int strokeSize) {
		if(strokeSize > 30) {
			strokeSize = 30;
		}else if(strokeSize < 1) {
			strokeSize = 1;
		}
		localizerStrokeSize = strokeSize;
	}
	
	public void setPaintCaliper(boolean show) {
		this.paintSizeCaliper = show;
	}

	private void showCaliper(Graphics gs) {
		setSize(sg.getWidth(), sg.getHeight());
		// 100 mm scale bar
		gs.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		if (sg != null) {
			if (paintSizeCaliper) {
				if (sg.getDisplayPixelSpacingY() > 0.) {
					// pixels in 100 mm
					/*
					 * calc FOV
					 */
					double fovY = sg.getOriginalPixelSpacingY() * sg.getOriginalImageSize().height;
					double imgY = sg.getCurrentDisplayImagePlus().getHeight();
					double currentViewingPixelSizeY = (double) fovY / imgY;
					double fovX = (int) (sg.getOriginalPixelSpacingX() * sg.getOriginalImageSize().width);
					double imgX = sg.getCurrentDisplayImagePlus().getWidth();
					double currentViewingPixelSizeX = (double) fovX / imgX;

					int viewScaleHeight = (int) (100 / currentViewingPixelSizeY);
					//show location
					//upper
					int y1 = (getHeight() - viewScaleHeight) / 2;
					//lower
					int y2 = y1 + viewScaleHeight;
					//location x for height
					int hx = 20;
					if(sg.getPixelSpacingUnit().equals("mm")) {
						gs.setColor(Color.YELLOW);
					}else {
						//pixel unit
						gs.setColor(Color.LIGHT_GRAY);
					}
					gs.drawLine(hx, y1, hx, y2);
					gs.drawLine(hx, y1, hx + 12, y1);
					gs.drawLine(hx, y2, hx + 12, y2);
					double viewScaleHeightMinorTick = (double)(viewScaleHeight / 10d);
					/*
					 * TODO draw unit [mm] string on top.
					 */
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 1)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 1)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 2)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 2)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 3)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 3)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 4)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 4)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 5)), hx + 12,
							(int) (y1 + (viewScaleHeightMinorTick * 5)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 6)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 6)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 7)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 7)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 8)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 8)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 9)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 9)));
					/*
					 * scale width (show under of slide)
					 */
					int viewScaleWidth = (int) (100 / currentViewingPixelSizeX);
					int wx1 = (getWidth() - viewScaleWidth) / 2;
					int wy = getHeight() - 20;
					int wx2 = wx1 + viewScaleWidth;
					gs.drawLine(wx1, wy, wx2, wy);
					gs.drawLine(wx1, wy, wx1, wy - 12);
					gs.drawLine(wx2, wy, wx2, wy - 12);
					double viewScaleWidthMinorTick = (double)(viewScaleWidth / 10d);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 1)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 1)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 2)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 2)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 3)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 3)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 4)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 4)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 5)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 5)),
							wy - 12);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 6)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 6)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 7)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 7)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 8)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 8)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 9)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 9)),
							wy - 6);
				}
			}
		}
	}
	
	public void showRoiPopUp(RoiObj roi, boolean show) {
		if (!show) {
			//check already showing on.
			Component[] roiPopups = getComponents();
	    	for(Component com:roiPopups) {
	    		if(com instanceof RoiPopUpDialog) {
	    			RoiPopUpDialog rpd = (RoiPopUpDialog)com;
	    			RoiObj r = rpd.getRoi();
	    			if(r.equals(roi)) {
	    				rpd.setVisible(show);
	    				repaint();
	    				break;
	    			}
	    		}
	    	}
		}else {
			boolean exist = false;
			Component[] roiPopups = getComponents();
	    	for(Component com:roiPopups) {
	    		if(com instanceof RoiPopUpDialog) {
	    			RoiPopUpDialog rpd = (RoiPopUpDialog)com;
	    			RoiObj r = rpd.getRoi();
	    			if(r.equals(roi)) {
	    				exist = true;
	    				break;
	    			}
	    		}
	    	}
	    	if(!exist) {
				int sx = (int) (sg.screenXD(roi.getBounds().x));
				int sy = (int) (sg.screenYD(roi.getBounds().y) + roi.getBounds().height);
				RoiPopUpDialog rpd = new RoiPopUpDialog(sg, roi);
				rpd.setLocation(sx, sy);
				add(rpd);
				repaint();
	    	}else {
	    		
	    	}
		}
	}
	
	public String sopInstanceUID() {
		return sopUID;
	}
	
	public void updateRoi(RoiObj roi) {
		String patID = roi.getProperty(ContextKey.PatientID.name());
		String studyUID = roi.getProperty(ContextKey.StudyInstanceUID.name());
		String seriesUID = roi.getProperty(ContextKey.SeriesInstanceUID.name());
		String sopUID = roi.getProperty(ContextKey.SOPInstanceUID.name());
		String roiInd = roi.getProperty(ContextKey.RoiID.name());
		updateRoi(patID, studyUID, seriesUID, sopUID, roiInd, roi);
	}
	
	/**
	 * 
	 * @param StudyUID
	 * @param seriesUID
	 * @param sopUID
	 * @param roiInd
	 * @param updatedRoi : attached attributes should be same to original roi.
	 */
	public void updateRoi(String patID, String studyUID, String seriesUID, String sopUID, String roiId, RoiObj willUpdateRoi) {
		if(willUpdateRoi == null) {
			return;
		}
		int ind = -1;
		if (roiset != null && roiset.size() > 0) {
			for(int i=0;i<roiset.size();i++) {
				if (roiset.get(i).isThisRoi(patID, studyUID, seriesUID, sopUID, roiId)) {
					ind = i;
					break;
				}
			}
			if(ind != -1) {
				willUpdateRoi.setProperty(ContextKey.PatientID, patID);
				willUpdateRoi.setProperty(ContextKey.StudyInstanceUID, studyUID);
				willUpdateRoi.setProperty(ContextKey.SeriesInstanceUID, seriesUID);
				willUpdateRoi.setProperty(ContextKey.SOPInstanceUID, sopUID);
				willUpdateRoi.setProperty(ContextKey.RoiID, roiId);
				roiset.set(ind, willUpdateRoi);
				insertOrUpdateRoi4DB(willUpdateRoi);// saveRoi to db
			}
		}
	}
}
