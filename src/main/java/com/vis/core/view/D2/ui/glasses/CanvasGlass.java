package com.vis.core.view.D2.ui.glasses;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.JScrollPane;

import com.vis.configuration.ContextKey;
import com.vis.core.log.Log;
import com.vis.core.util.Platform;
import com.vis.core.view.D2.roi.*;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
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
	private RoiObj previousRoi = null;
	private RoiBrush brushTool = null;
	private RoiObj brush = null;//roi brush, see also draw()
	
	private GeneralPath crossLine = null;
	private Color crossLineColor = new Color(31, 255, 0, 127);
	private int crossLineStrokeSize = 3;
	
	private java.util.List<java.awt.geom.Point2D> localizerGeo = null;
	private Color localizerColor = new Color(255, 0, 0, 127);
	private int localizerStrokeSize = 3;

	boolean rect = false;
	
	public CanvasGlass(SlideGlass sg) {
		setOpaque(false);
		setLayout(null);
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
			previousRoi = currentRoi;
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
//					roi.showRoiPopupOnCanvas();//TODO
					currentRoi = roi;
					found = true;
					break;
				} else if (roi.contains(ix, iy)) {
					if (roi instanceof ShapeRoi) {
						Log.logger.info("this is shape roi !!");
					}
					roi.setActiveOverlayRoi(true);
//					roi.showRoiPopupOnCanvas();//TODO 20240817
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
			/*
			 * after search current roi
			 */
			if (found) {
				if (currentRoi != previousRoi && previousRoi != null) {
//					previousRoi.setVisibleRoiPopup(false);//TODO
				}
			} else {
//					if(previousRoi != null) {
//						previousRoi.setVisibleRoiPopup(false);
//					}
			}
		}
		return currentRoi;
	}
	
	public void addRoi(RoiObj newRoi) {
		if(newRoi instanceof ReferenceLine) {
			return;
		}
		if (!this.roiset.contains(newRoi)) {
			if(isExistsInRoiSet(newRoi)) {
				HashMap<ContextKey, String> uids = newRoi.getUIDs();
				String patID = uids.get(ContextKey.PatientID);
				String studyUID = uids.get(ContextKey.StudyInstanceUID);
				String seriesUID = uids.get(ContextKey.SeriesInstanceUID);
				String sopUID = uids.get(ContextKey.SOPInstanceUID);
				String roiID = uids.get(ContextKey.RoiID);
				updateRoi(patID, studyUID, seriesUID, sopUID, roiID, newRoi);
			}else {
				roiset.add(newRoi);
				insertOrUpdateRoi4DB(newRoi);
			}
		}
	}

	public void addRoi(RoiObj newRoi, boolean updateDB) {
		if(newRoi instanceof ReferenceLine) {
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
			roi = new OvalRoi(imageX, imageY, 1,1,sg);
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
			roi = new Line(imageX, imageY, imageX+1, imageY+1, sg);
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
	}

	public void deleteRoi(String patID, String studyUID, String seriesUID, String sopUID, String roiInd) {
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
			if (roi.isThisRoi(patID, studyUID, seriesUID, sopUID, roiInd)) {
//				removeRoiPopupDialogOnCanvas(roi.getRoiPopupDialog());
				if(roi instanceof TextRoi) {
					Component[] coms = getComponents();
					for(Component c : coms) {
						if(c instanceof JScrollPane) {
							remove(c);
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
	
	public void drawCross(MouseEvent e) {
		Point currentScreenPos = e.getPoint();
		GeneralPath path = new GeneralPath();
		int sx = currentScreenPos.x;
		int sy = currentScreenPos.y;
		path.moveTo(0f, sy);
		path.lineTo(getWidth(), sy);
		path.moveTo(sx, 0f);
		path.lineTo(sx, getHeight());
		setCrossLine(path);
		repaint();
	}

	public void drawCross(Point onOrgImageCoordinatePoint) {
		GeneralPath path = new GeneralPath();
		int sx = sg.screenX(onOrgImageCoordinatePoint.x);
		int sy = sg.screenY(onOrgImageCoordinatePoint.y);
		path.moveTo(0f, sy);
		path.lineTo(getWidth(), sy);
		path.moveTo(sx, 0f);
		path.lineTo(sx, getHeight());
		setCrossLine(path);
		// do not return
		repaint();
	}

	private void drawCanvas(Graphics g) {
		if (paintSizeCaliper) {
			showCaliper(g);
		}
		drawRoi(g);
		drawReferenceLine(g);
		drawLocalizerLine(g);
		//show cross line
		if(crossLine != null) {
			Graphics2D g2 = (Graphics2D)g;
			g2.setColor(crossLineColor);
			g2.setStroke(new BasicStroke(crossLineStrokeSize));
			g2.draw(crossLine);
		}
	}

	private void drawLocalizerLine(Graphics g) {
		if(localizerGeo != null) {
//			System.out.println(shapes.size());
			Point2D p0_leftlower = localizerGeo.get(0);
			Point2D p1_rightlower = localizerGeo.get(1);
			Point2D p2_rightupper = localizerGeo.get(2);
			Point2D p3_leftupper = localizerGeo.get(3);
//			System.out.println(p0_leftlower.getX()+" "+p0_leftlower.getY());
//			System.out.println(p1_rightlower.getX()+" "+p1_rightlower.getY());
//			System.out.println(p2_rightupper.getX()+" "+p2_rightupper.getY());
//			System.out.println(p3_leftupper.getX()+" "+p3_leftupper.getY());
			GeneralPath loca = new GeneralPath();
	        loca.moveTo(sg.screenXD(p3_leftupper.getX()), sg.screenYD(p3_leftupper.getY()));
	        loca.lineTo(sg.screenXD(p2_rightupper.getX()), sg.screenYD(p2_rightupper.getY()));
	        loca.lineTo(sg.screenXD(p1_rightlower.getX()), sg.screenYD(p1_rightlower.getY()));
	        loca.lineTo(sg.screenXD(p0_leftlower.getX()), sg.screenYD(p0_leftlower.getY()));
	        loca.lineTo(sg.screenXD(p3_leftupper.getX()), sg.screenYD(p3_leftupper.getY()));
			Graphics2D g2 = (Graphics2D)g;
			g2.setColor(localizerColor);
			g2.setStroke(new BasicStroke(localizerStrokeSize));
			g2.draw(loca);
		}
	}
	
	private void drawReferenceLine(Graphics g) {
		if (getRoiSet() == null || getRoiSet().size() < 1) {
			if (pp.getReferenceLine() != null) {
				ReferenceLine refLine = pp.getReferenceLine();
				refLine.draw(g);
			}
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
		RoiObj currentRoi = getActiveRoi();
		if (currentRoi != null) {
			return currentRoi;
		} else {
			currentRoi = getCurrentRoi();
			if(currentRoi == null) {
				return getPreviousRoi();
			}else {
				return currentRoi;
			}
		}
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
	
	public RoiObj getPreviousRoi() {
		return previousRoi;
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

		int sx = e.getX();//slide screen x (praparat view coordinates)
		int sy = e.getY();//slide screen y (praparat view coordinates)
		int roiType = pp.getCurrentViewerToolType();
		if(referenceLineHereAt(sx,sy)!=null) {
			ReferenceLine refLine = referenceLineHereAt(sx,sy);
			int handle = refLine.isHandle(sx, sy);
			refLine.setRoiModState(e, handle);
			refLine.mouseDown(e);
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
		if(referenceLineHereAt(dragSX, dragSY) != null) {
			pp.getReferenceLine().mouseDrag(dragSX, dragSY, flags);
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
		int toolType = Viewer2DScreen.getInstance().getCurrentToolType();
		if(pp.getReferenceLine() != null) {
			ReferenceLine refLine = referenceLineHereAt(e.getX(), e.getY());
			if(refLine != null) {
				return;
			}
		}
		//update currentRoi
		activateAndGetCurrentRoiAt(e.getX(), e.getY());
		if(toolType == Viewer2DToolBar.Brush) {
			if(brushTool == null) {
				brushTool = new RoiBrush(sg, e, false);
			}
			if(currentRoi != null) {
				brushTool.setCurrentBrushingRoi(currentRoi);
			}
		}
		Log.logger.fine("CanvasComponent: "+getComponentAt(e.getX(),e.getY()).getName());
		int type = currentRoi != null ? currentRoi.getType() : -1;
		if (type>0 && (type==RoiType.POLYGON.id()||type==RoiType.POLYLINE.id()||type==RoiType.ANGLE.id()||type==RoiType.LINE.id()||type==RoiType.MULTIPOINT.id()) 
		&& currentRoi.getState()==RoiObj.CONSTRUCTING) {
			currentRoi.mouseMoved(e);
		}
	}
	
	public void mouseDragged(MouseEvent e) {
		if (pp.isShowCrossLineMode()) {
			drawCross(e);
		}
		if (pp.getReferenceLine() != null) {
			//TODO 20240819
			// do something
			return;// attention
		}
		int dragSX = e.getX();//x on slideglass
		int dragSY = e.getY();
		int flags = e.getModifiersEx();
		/*
		 * drag roi or popup
		 */
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
		if(referenceLineHereAt(dragSX, dragSY) != null) {
			pp.getReferenceLine().mouseDrag(dragSX, dragSY, flags);
			sg.lastDraggedX = dragSX;
			sg.lastDraggedY = dragSY;
			return;
		}
		//is dialog ?
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
//		int ox = sg.onImageX(sx);
//		int oy = sg.onImageY(sy);
		switch (toolID) {
//		case Viewer2DToolBar.MAGNIFIER:
//			if (IJ.shiftKeyDown())
//				zoomToSelection(ox, oy);
//			else if ((flags & (Event.ALT_MASK|Event.META_MASK|Event.CTRL_MASK))!=0) {
//				zoomOut(x, y);
//				if (getMagnification()<1.0)
//					imp.repaintWindow();
//			} else {
// 				zoomIn(x, y);
//				if (getMagnification()<=1.0)
//					imp.repaintWindow();
//			}
//			break;
//		case Toolbar.HAND:
//			setupScroll(ox, oy);
//			break;
//		case Toolbar.DROPPER:
//			setDrawingColor(ox, oy, IJ.altKeyDown());
//			break;
//		case Toolbar.WAND:
//			double tolerance = WandToolOptions.getTolerance();
//			Roi roi = imp.getRoi();
//			if (roi!=null && (tolerance==0.0||imp.isThreshold()) && roi.contains(ox, oy)) {
//				Rectangle r = roi.getBounds();
//				if (r.width==imageWidth && r.height==imageHeight)
//					imp.deleteRoi();
//				else if (!e.isAltDown()) {
//					handleRoiMouseDown(e);
//					return;
//				}
//			}
//			if (roi!=null) {
//				int handle = roi.isHandle(x, y);
//				if (handle>=0) {
//					roi.mouseDownInHandle(handle, x, y);
//					return;
//				}
//			}
//			setRoiModState(e, roi, -1);
//			String mode = WandToolOptions.getMode();
//			if (Prefs.smoothWand)
//				mode = mode + " smooth";
//			int npoints = IJ.doWand(ox, oy, tolerance, mode);
//			if (Recorder.record && npoints>0) {
//				if (Recorder.scriptMode())
//					Recorder.recordCall("IJ.doWand(imp, "+ox+", "+oy+", "+tolerance+", \""+mode+"\");");
//				else {
//					if (tolerance==0.0 && mode.equals("Legacy"))
//						Recorder.record("doWand", ox, oy);
//					else
//						Recorder.recordString("doWand("+ox+", "+oy+", "+tolerance+", \""+mode+"\");\n");
//				}
//			}
//			break;
		case Viewer2DToolBar.Brush:
			handleRoiBrushMouseDown(e);
			break;
		default:  //rois
			roiMouseDown(e);
		}
	}

	public void mouseReleased(MouseEvent emr) {
		if(currentRoi != null) {
			currentRoi.handleMouseUp(emr.getX(), emr.getY());
			if(currentRoi.getState() != RoiObj.CONSTRUCTING) {
				saveCurrentRoiSate();
				previousRoi = currentRoi;
			}
		}
		//brush
		if(pp.getCurrentViewerToolType()==Viewer2DToolBar.Brush) {
			if(brushTool != null) {
				brushTool.brushingEnd();
				repaint();
			}
		}
			
//		int ox = offScreenX(e.getX());
//		int oy = offScreenY(e.getY());
//		if ((overlay!=null||showAllOverlay!=null) && ox==mousePressedX && oy==mousePressedY) {
//			boolean cmdDown = IJ.isMacOSX() && e.isMetaDown();
//			Roi roi = imp.getRoi();
//			if (roi!=null && roi.getBounds().width==0)
//				roi=null;
//			if ((e.isAltDown()||e.isControlDown()||cmdDown) && roi==null) {
//				if (activateOverlayRoi(ox, oy))
//					return;
//			} else if ((System.currentTimeMillis()-mousePressedTime)>250L && !drawingTool()) {
//				if (activateOverlayRoi(ox,oy))
//					return;
//			}
//		}
//
//		PlugInTool tool = Toolbar.getPlugInTool();
//		if (tool!=null) {
//			tool.mouseReleased(imp, e);
//			if (e.isConsumed()) return;
//		}
//		flags = e.getModifiers();
//		flags &= ~InputEvent.BUTTON1_MASK; // make sure button 1 bit is not set
//		flags &= ~InputEvent.BUTTON2_MASK; // make sure button 2 bit is not set
//		flags &= ~InputEvent.BUTTON3_MASK; // make sure button 3 bit is not set
//		Roi roi = imp.getRoi();
//		if (roi != null) {
//			Rectangle r = roi.getBounds();
//			int type = roi.getType();
//			if ((r.width==0 || r.height==0)
//			&& !(type==Roi.POLYGON||type==Roi.POLYLINE||type==Roi.ANGLE||type==Roi.LINE)
//			&& !(roi instanceof TextRoi)
//			&& roi.getState()==roi.CONSTRUCTING
//			&& type!=roi.POINT)
//				imp.deleteRoi();
//			else
//				roi.handleMouseUp(e.getX(), e.getY());
		
	}
	
	// http://alga.no.coocan.jp/paint.html
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		drawCanvas(g);
	}
	
	/**
	 * set reference line activate color and change cursor
	 * @param screenX
	 * @param screenY
	 * @return
	 */
	protected ReferenceLine referenceLineHereAt(int screenX, int screenY) {

		int ix = sg.offScreenX(screenX);
		int iy = sg.offScreenY(screenY);
		int handle = -1;
		boolean found = false;
		if (pp.getReferenceLine() != null) {
			ReferenceLine refLine = pp.getReferenceLine();
			refLine.setActiveOverlayRoi(false);// reset activate
			handle = refLine.isHandle(screenX, screenY);
			if (handle >= 0) {
				refLine.setActiveOverlayRoi(true);
				found = true;
			} else if (refLine.contains(ix, iy)) {
				refLine.setActiveOverlayRoi(true);
				found = true;
			}
			if (handle >= 0) {
				sg.setCursor(new Cursor(Cursor.HAND_CURSOR));
			} else if (found && refLine.contains(ix, iy)) {
				sg.setCursor(new Cursor(Cursor.MOVE_CURSOR));
			} else {
				sg.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
			}
			return refLine;
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
		String candidateRoiID = roiToReplace.getProperty(ContextKey.RoiID.name());
		if (roiset != null && roiset.size() > 0) {
			for (RoiObj roi : roiset) {
				if (roi.isThisRoi(patID, beReplacedStudyUID, beReplacedSeriesUID, beReplacedSopUID, beReplacedRoiId)) {
					if(roiToReplace.getProperty(ContextKey.RoiID.name()).equals(candidateRoiID)) {
						updateRoi(patID, beReplacedStudyUID, beReplacedSeriesUID, beReplacedSopUID, candidateRoiID, roiToReplace);
					}else {
						deleteRoi(roi);
						addRoi(roiToReplace);
					}
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
		revalidate();
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
					/*
					 * 表示ピクセルサイズがいくつあれば100mmになるか(num of pixels)
					 */
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
	public void updateRoi(String patID, String studyUID, String seriesUID, String sopUID, String roiInd, RoiObj updatedRoi) {
		if(updatedRoi == null) {
			return;
		}
		int ind = -1;
		if (roiset != null && roiset.size() > 0) {
			for(int i=0;i<roiset.size();i++) {
				if (roiset.get(i).isThisRoi(patID, studyUID, seriesUID, sopUID, roiInd)) {
					ind = i;
					break;
				}
			}
			if(ind != -1) {
				roiset.set(ind, updatedRoi);
				insertOrUpdateRoi4DB(updatedRoi);// saveRoi to db
			}
		}
	}
}
