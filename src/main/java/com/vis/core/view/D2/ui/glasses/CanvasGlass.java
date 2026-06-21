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
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;

import javax.swing.JTextArea;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.RoiDBKey;
import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.slicer.CenterPositionLine;
import com.vis.core.slicer.ReferenceLineMPR;
import com.vis.core.ui.listener.RoiObjListener;
import com.vis.core.util.Platform;
import com.vis.core.view.D2.roi.*;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.Tag;

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
	public boolean paintCaliper = true;
	
	/*
	 * Current/Hovered ROI
	 */
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
	
	private boolean isMousePressed = false;
	
	public CanvasGlass(SlideGlass sg) {
		setOpaque(false);
		setLayout(null);
		setDoubleBuffered(true);//fail safe
		this.sopUID = sg.getSOPInstanceUID();
		this.pp = sg.getPraparat();
		this.sg = sg;
		this.roiset = new ArrayList<RoiObj>();
	}
	
	
	protected RoiObj activateRoiAt(int screenX, int screenY) {
		
		Point p = null;
		try {
			p = sg.offScreenCoordinate(screenX, screenY);
		} catch (NoninvertibleTransformException e) {
			e.printStackTrace();
			Log.logger.log(Level.SEVERE, "CanvasGlass::activateRoiAt : Can not translate offscreen coordinates...");
			return null;
		}
		
		int ix = p.x;
		int iy = p.y;
		
		//do not do this, polygon families can not construct smoothly.
//		if(currentRoi != null && currentRoi.contains(ix, iy)) {
		if (currentRoi != null) {
			// polygonroi
			int type = currentRoi.getType();
			RoiType t = RoiType.find(type);
			if ((t == RoiType.POLYGON || t == RoiType.POLYLINE || t == RoiType.ANGLE || t == RoiType.LINE  || t == RoiType.MULTIPOINT)
					&& currentRoi.getState() == RoiObj.CONSTRUCTING) {
				return currentRoi;
			}
		}
		ArrayList<RoiObj> rois = getRoiSet();
		int handle = -1;
		boolean found = false;

		// 2D ROI のアクティブ状態をリセット
		if (rois != null) {
			for (RoiObj roi : rois) {
				roi.setActiveOverlayRoi(false);
			}
		}
		// 3D ROI のアクティブ状態もリセット
		if (pp != null) {
			java.util.List<RoiObj> roi3DList = pp.getRoi3DList();
			if (roi3DList != null) {
				for (RoiObj roi3D : roi3DList) {
					if (roi3D != null) roi3D.setActiveOverlayRoi(false);
				}
			}
		}

		// 2D ROI から検索
		if (rois != null && rois.size() > 0) {
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
		}

		// 2D ROI で見つからない場合、Praparat の 3D ROI リストを検索
		if (!found && pp != null) {
			java.util.List<RoiObj> roi3DList = pp.getRoi3DList();
			if (roi3DList != null) {
				for (RoiObj roi3D : roi3DList) {
					if (roi3D == null) continue;
					roi3D.setSlideGlass(sg, false);
					handle = roi3D.isHandle(screenX, screenY);
					if (handle >= 0) {
						roi3D.setActiveOverlayRoi(true);
						currentRoi = roi3D;
						found = true;
						break;
					}
					if (roi3D.contains(ix, iy)) {
						roi3D.setActiveOverlayRoi(true);
						currentRoi = roi3D;
						found = true;
						break;
					}
				}
			}
		}

		if (handle >= 0) {
			sg.setCursor(new Cursor(Cursor.HAND_CURSOR));
		} else if (found && currentRoi != null && currentRoi.contains(ix, iy)) {
			sg.setCursor(new Cursor(Cursor.MOVE_CURSOR));
		} else {
			/*
			 * when no roi at (x,y), current roi set to null.
			 */
			sg.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
			currentRoi = null;
		}
		return currentRoi;
	}
	
	public void addRoi(RoiObj newRoi) {
		if (newRoi instanceof CenterPositionLine) {
			//For MPR.
			return;
		}
		synchronized(roiset) {
			if (isExistsInRoiSet(newRoi)) {
				HashMap<RoiDBKey, String> uids = newRoi.getUIDs();
				String patID = uids.get(RoiDBKey.PatientID);
				String studyUID = uids.get(RoiDBKey.StudyInstanceUID);
				String seriesUID = uids.get(RoiDBKey.SeriesInstanceUID);
				String sopUID = uids.get(RoiDBKey.SOPInstanceUID);
				String roiID = uids.get(RoiDBKey.RoiID);
				updateRoi(patID, studyUID, seriesUID, sopUID, roiID, newRoi);
				//this roi already in RoiObjManager.
			} else {
				/*
				 * before adding to list
				 */
				if (sg != null) {
					sg.saveUndoState(); 
				}
				
				roiset.add(newRoi);
				/*
				 * DBに存在していてもUpdateのために保存する。
				 */
				insertOrUpdateRoi4DB(newRoi);
				//update RoiObjManager
				RoiObjManager rom = (RoiObjManager)WindowManager.getWindow(ConfigInfo.RoiManager);
				if(rom != null) {
					rom.updateState();
				}
			}
		}
	}
	
	/**
	 * DBから読み込まれたROIを、再保存なしで内部リストに追加します（互換性維持用）。
	 */
	public void addRoiFromDB(RoiObj roi) {
		if (roi == null)
			return;
		synchronized (roiset) {
			if (!this.roiset.contains(roi)) {
				this.roiset.add(roi);
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
		
		Point p = null;
		try {
			p = sg.offScreenCoordinate(screenX, screenY);
		} catch (NoninvertibleTransformException e) {
			e.printStackTrace();
			Log.logger.log(Level.SEVERE, "CanvasGlass::activateRoiAt : Can not translate offscreen coordinates...");
			return null;
		}
		
		int imageX = p.x;
		int imageY = p.y;
		
		RoiObj roi = null;
		RoiType t = RoiType.find(roiType);
		if (t == null) {
			System.out.println("This id is not Roi :"+roiType);
	        return null; // 不明なツールの場合は作成しない
	    }
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
		case SPHERE_3D:
			roi = create3DSphere(imageX, imageY);
		default:
			//do nothing
		}
		if (roi != null) {
			//insertOrUpdateRoi4DB
			addRoi(roi);
			// RoiObjManager が開いていれば即座に登録を反映
			RoiObjManager rom = RoiObjManager.getInstance();
			if (rom != null) rom.updateState();
			repaint();
		}
		return roi;
	}

	/**
	 * ワンクリックで半径5mmの SphereRoi3D を生成し、Praparatの3Dリストに登録します。
	 * マスター/スレーブ設計は使わず、純粋な SphereRoi3D として管理します。
	 */
	public RoiObj create3DSphere(int offScreenX, int offScreenY) {
		double pixelSpacingX = sg.getOriginalPixelSpacingX();
		double pixelSpacingY = sg.getOriginalPixelSpacingY();
		if (pixelSpacingX <= 0) pixelSpacingX = 1.0;
		if (pixelSpacingY <= 0) pixelSpacingY = 1.0;

		final double radiusMm = 5.0;
		double radiusPxX = radiusMm / pixelSpacingX;
		double radiusPxY = radiusMm / pixelSpacingY;

		int startX = (int) (offScreenX - radiusPxX);
		int startY = (int) (offScreenY - radiusPxY);
		int width  = (int) (radiusPxX * 2.0);
		int height = (int) (radiusPxY * 2.0);

		com.vis.core.view.D3.roi.SphereRoi3D sphere3D =
				new com.vis.core.view.D3.roi.SphereRoi3D(startX, startY, width, height, sg);
		sphere3D.setState(RoiObj.NORMAL);

		int uniqueGroupId = (int) (System.currentTimeMillis() % 1000000000L);
		sphere3D.setProperty(RoiDBKey.RoiGroup.name(), String.valueOf(uniqueGroupId));

		// 3D中心座標 (IPP) の算出: IPP + IOP_row * X * spacingX + IOP_col * Y * spacingY
		double[] currentIpp = sg.getHeader().getDoubles(Tag.ImagePositionPatient);
		double[] iop = sg.getHeader().getDoubles(Tag.ImageOrientationPatient);
		if (currentIpp != null && currentIpp.length == 3) {
			if (iop != null && iop.length == 6) {
				double physX = currentIpp[0] + iop[0] * offScreenX * pixelSpacingX + iop[3] * offScreenY * pixelSpacingY;
				double physY = currentIpp[1] + iop[1] * offScreenX * pixelSpacingX + iop[4] * offScreenY * pixelSpacingY;
				double physZ = currentIpp[2] + iop[2] * offScreenX * pixelSpacingX + iop[5] * offScreenY * pixelSpacingY;
				sphere3D.setCenterIpp(physX, physY, physZ);
			} else {
				sphere3D.setCenterIpp(currentIpp[0], currentIpp[1], currentIpp[2]);
			}
		}
		sphere3D.setRadiusMm(radiusMm);

		if (pp != null) {
			pp.addRoi3D(sphere3D);
		}
		currentRoi = sphere3D;
		return sphere3D;
	}
	
	public void deleteRoi(int sx, int sy) {
		// activateRoiAt は roiset と 3D リストを両方チェックするので、そのまま使う
		RoiObj roi2remove = activateRoiAt(sx, sy);
		if (roi2remove != null) {
			deleteRoi(roi2remove);
			RoiObjManager rom = (RoiObjManager)WindowManager.getWindow(ConfigInfo.RoiManager.toString());
			if(rom != null) {
				rom.updateState();
			}
		}
		repaint();
	}
	
	public boolean deleteRoi(RoiObj roi2remove) {
		if (roi2remove == null) return false;
		// 3D リスト管理の ROI (SphereRoi3D / FreeFormRoi3D) は専用削除パス
		if (pp != null && pp.getRoi3DList().contains(roi2remove)) {
			deleteSphere3D(roi2remove);
			if (roi2remove == currentRoi) setCurrentRoi2NULL();
			repaint();
			return true;
		}
		if (roiset == null || roiset.size() < 1) {
			return false;
		}
		HashMap<RoiDBKey, String> uids = roi2remove.getUIDs();
		String patID = uids.get(RoiDBKey.PatientID);
		String studyUID = uids.get(RoiDBKey.StudyInstanceUID);
		String seriesUID = uids.get(RoiDBKey.SeriesInstanceUID);
		String sopUID = uids.get(RoiDBKey.SOPInstanceUID);
		String roiID = uids.get(RoiDBKey.RoiID);
		/*
		 * see, deleteRoiFromDB to notify listener.
		 */
		boolean deleted = deleteRoi(patID, studyUID, seriesUID, sopUID, roiID);
		if(brushTool != null) {
			brushTool.clearCurrentBrushingRoi();
		}
		return deleted;
	}

	public boolean deleteRoi(String patID, String studyUID, String seriesUID, String sopUID, String roiId) {
		if (roiset == null || roiset.size() < 1) {
			return false;
		}
		/*
		 * pay attention remove item from list
		 * see, https://stackoverflow.com/questions/8104692/how-to-avoid-java-util-concurrentmodificationexception-when-iterating-through-an
		 */
		RoiObjManager rom = RoiObjManager.getInstance();
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
		boolean res = roiset.removeAll(roi2Remove);
		setCurrentRoi2NULL();
		if (rom != null) {
	        rom.updateState(); 
	    }
		return res;
	}

	private void deleteRoiFromDB(RoiObj roi) {
		if (sg != null) sg.saveUndoState();
		
		String shapeType = roi.getProperty(RoiMetaContextKey.Shape_3D_Type.name());
		if (com.vis.core.view.D3.roi.SphereRoi3D.Shape_3D_Type.equals(shapeType)
				|| com.vis.core.view.D3.roi.FreeFormRoi3D.Shape_3D_Type.equals(shapeType)) {
			if (pp != null) {
				pp.removeRoi3D(roi);
			}
		}
		
		HashMap<RoiDBKey, String> uids = roi.getUIDs();
		String patID = uids.get(RoiDBKey.PatientID);
		String studyUID = uids.get(RoiDBKey.StudyInstanceUID);
		String seriesUID = uids.get(RoiDBKey.SeriesInstanceUID);
		String sopUID = uids.get(RoiDBKey.SOPInstanceUID);
		String roiID = uids.get(RoiDBKey.RoiID);
		//notify
		roi.notifyListeners(RoiObjListener.DELETED);
		DatabaseHandler.getInstance().deleteRoi(patID, studyUID, seriesUID,sopUID,roiID);
		if(Viewer2DScreen.getRoiObjManager() != null) {
			RoiObjManager rom = Viewer2DScreen.getRoiObjManager();
			rom.updateRoiObjList(sg.getPatientID());
		}
	}
	
	public void createCross(MouseEvent e) {
		try {
			Point p = sg.offScreenCoordinate(e.getX(), e.getY());
			createCross(p.x, p.y);
		} catch (NoninvertibleTransformException nte) {
			nte.printStackTrace();
			Log.logger.log(Level.SEVERE, "CanvasGlass::activateRoiAt : Can not translate offscreen coordinates...");
			return;
		}
	}
	
	public void createCross(int x, int y) {
		GeneralPath path = new GeneralPath();
		path.moveTo(0f, y);
		path.lineTo(sg.getOriginalImage().getWidth(), y);
		path.moveTo(x, 0f);
		path.lineTo(x, sg.getOriginalImage().getHeight());
		setCrossLine(path);
		repaint();
	}
	
	public RoiObj getSelectedRoi() {
		for (RoiObj roi : roiset) {
			if (roi.isSelected()) return roi;
		}
		// 3D ROI リストも確認
		if (pp != null) {
			java.util.List<RoiObj> roi3DList = pp.getRoi3DList();
			if (roi3DList != null) {
				for (RoiObj roi3D : roi3DList) {
					if (roi3D != null && roi3D.isSelected()) return roi3D;
				}
			}
		}
		return null;
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

		Point p = null;
		try {
			p = sg.offScreenCoordinate(screenX, screenY);
		} catch (NoninvertibleTransformException e) {
			e.printStackTrace();
			Log.logger.log(Level.SEVERE, "CanvasGlass::activateRoiAt : Can not translate offscreen coordinates...");
			return null;
		}
		
		int ix = p.x;
		int iy = p.y;
		/*
		 * if rois are overlapping, return roi that find first.
		 */
		synchronized(roiset) {
			if (roiset != null && roiset.size() > 0) {
				for (RoiObj roi : roiset) {
					if (roi.contains(ix, iy)) {
						return roi;
					}
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
		int roiType = pp.getViewer2DToolType();
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
		//get Roi
		currentRoi = activateRoiAt(sx, sy);
		
		if (currentRoi != null){
			if(sg.isHereRoiPopup(e)) {
				return;
			}
			currentRoi.mouseDown(e);
			
			String shape_3d_type = currentRoi.getProperty(RoiMetaContextKey.Shape_3D_Type.name());
			if (shape_3d_type != null
					&& (com.vis.core.view.D3.roi.SphereRoi3D.Shape_3D_Type.equals(shape_3d_type)
					||  com.vis.core.view.D3.roi.FreeFormRoi3D.Shape_3D_Type.equals(shape_3d_type))) {
				currentRoi.setState(RoiObj.MOVING);
			}
		}else {
			if(sg.isHereRoiPopup(e)) {
				return;
			}
			currentRoi = createNewRoi(sx, sy,roiType);
			e.consume();
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
//				dialog.mouseDragged(e);
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
		
		/*
		 * IPP同期
		 */
		// ROIが描画されたフレームのIPP（3D空間座標）をプロパティに保持する
		double[] ipp = sg.getHeader().getDoubles(Tag.ImagePositionPatient);
		if (ipp != null && ipp.length >= 3) {
			String ippStr = ipp[0] + "," + ipp[1] + "," + ipp[2];
			roi.setProperty(RoiMetaContextKey.ReferenceImagePositionPatient.name(), ippStr);
		}
		int[] zct = pp.getZCTArray(sg);
		roi.setProperty(RoiMetaContextKey.Dim_Z.name(), String.valueOf(zct[0]));
		roi.setProperty(RoiMetaContextKey.Dim_C.name(), String.valueOf(zct[1]));
		roi.setProperty(RoiMetaContextKey.Dim_T.name(), String.valueOf(zct[2]));

		String frameOfRef = sg.getHeader().getString(Tag.FrameOfReferenceUID);

		if (frameOfRef == null || frameOfRef.trim().isEmpty()) {
			if (ipp != null && ipp.length == 3) {
				// IPPが存在するなら、空間の概念はあると見なして SeriesInstanceUID でグループ化
				frameOfRef = sg.getHeader().getString(Tag.SeriesInstanceUID);
			} else {
				// IPPすら存在しない（完全な2D画像など）場合は、他画像との空間マッチングは物理的に不可能。
				// 最も安全な SOPInstanceUID にフォールバックし、その画像単体に縛る。
				frameOfRef = sg.getHeader().getString(Tag.SOPInstanceUID);
			}
		}
		roi.setProperty("FrameOfReferenceUID", frameOfRef);
		
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
	
	public boolean isMousePressed() {
		return isMousePressed;
	}
	
	public void loadRoiFromDB() {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			return;
		}
		String pid = sg.getPatientID();
		String studyUid = sg.getStudyInstanceUID();
		String seriesUid = sg.getSeriesInstanceUID();
		String sopInstUid = sg.getSOPInstanceUID();
				
		// ---------------------------------------------------------
		// ★ 変更: 現在のSOPだけでなく、Praparatが持つすべてのSOPを対象にする
		Object[] uids = pp.getUIDs();
		String[] allSopUIDs = (String[]) uids[3];
		if (allSopUIDs == null) return;
		
		// 現在のスライドのIPPを取得
		double[] currentIpp = sg.getHeader().getDoubles(Tag.ImagePositionPatient);
		// ---------------------------------------------------------

		if(currentIpp != null && currentIpp.length >= 3) {
			// ★ 追加: 全SOPをループして検索
			for (String targetSop : allSopUIDs) {
				ArrayList<HashMap<String,Object>> cons = db.loadRoiContextFromInstance(pid, studyUid, seriesUid, targetSop);
				synchronized(roiset) {
					if(cons != null && cons.size() > 0) {
						for(int i=0; i<cons.size(); i++) {
							RoiObj roi = new RoiConverter().buildRoiObj(cons.get(i));
							if(roi == null) {
								continue;
							}
							

							// ---------------------------------------------------------
							// ★ 追加: IPPによる空間位置（スライス位置）のフィルタリング
							String roiIppStr = roi.getProperty("RoiImagePositionPatient");
							if (roiIppStr != null) {
								String[] parts = roiIppStr.split(",");
								if (parts.length == 3) {
									try {
										double rx = Double.parseDouble(parts[0]);
										double ry = Double.parseDouble(parts[1]);
										double rz = Double.parseDouble(parts[2]);
										
										// 3D空間上の距離（ユークリッド距離）を計算
										double dx = currentIpp[0] - rx;
										double dy = currentIpp[1] - ry;
										double dz = currentIpp[2] - rz;
										double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
										
										// 距離が 0.001mm より大きい場合は、別スライスとみなしてスキップ
										if (distance > 1e-3) {
											continue; 
										}
									} catch (NumberFormatException e) {
										// パースエラー時はフォールバックとしてそのまま表示（旧データ互換のため）
									}
								}
							} else {
								// ---------------------------------------------------------
								// 空間位置が判定できない場合は、「現在のスライド(SOP)に直接描かれたROI」のみを表示する
								String roiSop = roi.getProperty(RoiDBKey.SOPInstanceUID.name());
								String currentSop = sg.getSOPInstanceUID();
								
								if (roiSop != null && currentSop != null) {
									if (!roiSop.equals(currentSop)) {
										continue; // 別のSOP（他チャンネル・他スライス等）のROIなのでスキップ
									}
								}
							}
							roi.setSlideGlass(sg, false);
							if (!this.roiset.contains(roi)) {
								roiset.add(roi);
							}
						}
					}
				}
			} // end of for(targetSop)
		}else {
			ArrayList<HashMap<String,Object>> cons = db.loadRoiContextFromInstance(pid, studyUid, seriesUid, sopInstUid);
			synchronized(roiset) {
				if(cons != null && cons.size() > 0) {
					for(int i=0; i<cons.size(); i++) {
						RoiObj roi = new RoiConverter().buildRoiObj(cons.get(i));
						if(roi == null) {
							continue;
						}
						roi.setSlideGlass(sg, false);
						if (!this.roiset.contains(roi)) {
							roiset.add(roi);
						}
					}
				}
			}
		}
	}
	
	public void mouseMoved(MouseEvent e) {
		//update currentRoi
		activateRoiAt(e.getX(), e.getY());
		
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
		if(isHereRoiPopup(e)) {
//			System.out.println("RoiDialog DRAGGING!!!");
			RoiPopUpDialog dialog = getRoiPopupAt(e);
			if(dialog != null) {
//				dialog.mouseDragged(e);
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
		
		this.isMousePressed = true; // ★ここを追加
		
		int toolID = pp.getViewer2DToolType();
		int sx = e.getX();
		int sy = e.getY();
		sg.lastPressedX = sx; 
		sg.lastPressedY = sy;
		
		if (toolID == Viewer2DToolBar.Brush) {
			handleRoiBrushMouseDown(e); // ← 元々あった正しい呼び出しに戻す
			return;
		}
		
		if(toolID == Viewer2DToolBar.Wand) {
			e.consume();
			return;
		}
		
		RoiObj hitRoi = activateRoiAt(sx, sy);
		
		// ==========================================================
		// ★修正: ハンドル操作（Alt/Shift）の最優先処理（線上も対応）
		// ==========================================================
		if (hitRoi != null && hitRoi.getState() == RoiObj.NORMAL) {
			
			// マウスが3D-ROIをヒットした時の処理内
			if (hitRoi instanceof com.vis.core.view.D3.roi.FreeFormRoi3D || hitRoi instanceof com.vis.core.view.D3.roi.SphereRoi3D) {
				sg.getPraparat().setCurrentRoi(hitRoi); // ★Praparatに記憶させる
			}
			
			int handle = hitRoi.isHandle(sx, sy); // ハンドル番号を取得 (-1ならハンドル外)
			if (handle >= 0) {
				// 1. ハンドル上の場合
				if (e.isShiftDown() || e.isAltDown() || e.isControlDown()) {
					hitRoi.mouseDownInHandle(handle, sx, sy);
					repaint();
					return; // 通常の移動や新規作成に進ませない
				}
			}
		}
		roiMouseDown(e);
	}

	public void mouseReleased(MouseEvent emr) {
		this.isMousePressed = false; // ★Roiを離したのでフラグを下ろす
		
		if(currentRoi != null) {
			currentRoi.handleMouseUp(emr.getX(), emr.getY());
			//作成終了時
			if(currentRoi.getState() != RoiObj.CONSTRUCTING) {
				String shape_3d_type = currentRoi.getProperty(RoiMetaContextKey.Shape_3D_Type.name());
				if (shape_3d_type != null) {
					if (com.vis.core.view.D3.roi.SphereRoi3D.Shape_3D_Type.equals(shape_3d_type)
							|| com.vis.core.view.D3.roi.FreeFormRoi3D.Shape_3D_Type.equals(shape_3d_type)) {
						// 3D ROI の状態を DB に保存する
						insertOrUpdateRoi4DB(currentRoi);
					} else {
						saveCurrentRoiSate();
					}
				} else {
					saveCurrentRoiSate();
				}
				
				// ==========================================================
				// ★ 追加: 形や位置が確定した「マウスを離した瞬間」に1回だけUndoを保存する
				// ==========================================================
				if (sg != null) {
					sg.saveUndoState();
				}
			}
		}
		//brush
		if(pp.getCurrentViewerToolType()==Viewer2DToolBar.Brush) {
			if(brushTool != null) {
				brushTool.brushingEnd();
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

		// Delete/Backspace でアクティブな 3D ROI を削除
		if ((keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE)
				&& pp != null && pp.getRoi3DList().contains(currentRoi)) {
			deleteSphere3D(currentRoi);
			currentRoi = null;
			repaint();
			return true;
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
	
	private void drawCross(Graphics g) {
		if(crossLine != null) {
			Graphics2D g2 = (Graphics2D)g;
			g2.setColor(crossLineColor);
			g2.setStroke(new BasicStroke(crossLineStrokeSize));
			g2.draw(crossLine);
		}
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
			if(roiObj !=null) roiObj.draw(g);
		}
		
		if (pp != null) {
			java.util.List<RoiObj> roi3DList = pp.getRoi3DList();
			if (roi3DList != null) {
				RoiObj activeRoi = pp.getCurrentRoi();
				for (RoiObj roi3d : roi3DList) {
					if (roi3d == null)
						continue;
					if (roi3d == activeRoi) {
						roi3d.setActiveOverlayRoi(true);
					}
					roi3d.setSlideGlass(sg, false); // コンテキストを現在のスライドに設定
					roi3d.draw(g);
				}
			}
		}
		
		if(brush != null) {
			brush.draw(g);
		}
	}
	
	/**
	 * Handle draw event on OffScreen (without Caliper).
	 */
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (paintCaliper) {
			// show first, do not require transform.
			//slide glass coordinates
			drawScaleBar(g);
		}
		
		/*
		 * if Windows high DPI, original transform is already have something transform.
		 */
		Graphics2D g2d = (Graphics2D) g;
		java.awt.geom.AffineTransform imageTransform = (java.awt.geom.AffineTransform) sg.getCurrentTransform().clone();
		g2d.transform(imageTransform);
		
		/*
		 * DO NOT set transform directly.
		 */
//		g2d.setTransform(sg.getCurrentTransform());
		
		/*
		 * offscreen cood.
		 */
		drawRoi(g2d);
		drawReferenceLine(g);
		drawLocalizerLine(g);
		drawCross(g);
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
    	currentRoi = activateRoiAt(e.getX(), e.getY());
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
	
	/**
	 * set current roi
	 * @param r
	 */
	protected void setActiveRoi(RoiObj r) {
		currentRoi = r;
	}

	public void setCurrentRoi2NULL() {
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
		this.paintCaliper = show;
	}

//	/**
//	 * 100mm scaler
//	 * @param gs
//	 */
//	private void showCaliper(Graphics gs) {
//		if (sg != null) {
//			/*
//			 * calc FOV
//			 */
//			Dimension dim = sg.getOriginalImageSize();
//			double orgImgHeight = dim.height;
//			double compHeight = getHeight();
//			double viewScale = compHeight/orgImgHeight;
//			
//			double left_bar_size_in_pixel = 100.0/(double)sg.getPixelSpacingY() * viewScale;
//			double bottom_bar_size_in_pixel = 100.0/(double)sg.getPixelSpacingX() * viewScale;
//			
//			// show location
//			// upper
//			int y1 = (int)(getHeight() - left_bar_size_in_pixel) / 2;
//			// lower
//			int y2 = y1 + (int)left_bar_size_in_pixel;
//			// location x
//			int x = 20;
//			if (sg.getPixelSpacingUnit().equals("mm")) {
//				gs.setColor(Color.YELLOW);
//			} else {
//				// pixel unit
//				gs.setColor(Color.LIGHT_GRAY);
//			}
//			//main
//			gs.drawLine(x, y1, x, y2);
//			//upper, lower
//			gs.drawLine(x, y1, x + 12, y1);
//			gs.drawLine(x, y2, x + 12, y2);
//			
//			gs.drawString(sg.getPixelSpacingUnit(), x , y1 - 5); 
//			
//			double viewScaleHeightUnitTick = (double) (left_bar_size_in_pixel / 100d);
//			for (int i = 1; i <= 99; i++) {
//				int currentY = (int) (y1 + (viewScaleHeightUnitTick * i));
//				int tickLength = 3; // 1単位の目盛りの初期長さ
//
//				if (i == 50) {
//					tickLength = 12; // 50単位マーク (一番長い)
//				} else if (i % 10 == 0) {
//					tickLength = 9; // 10単位ごと (中くらい長い)
//				} else if (i % 5 == 0) {
//					tickLength = 6; // 5単位ごと (標準)
//				}
//				
//				// 目盛り線を描画
//				gs.drawLine(x, currentY, x + tickLength, currentY); 
//			}
//			
//			/*
//			 * Bottom scaler (show under of slide)
//			 */
//			int x1 = (int) (getWidth() - bottom_bar_size_in_pixel) / 2;
//			int x2 = (int) (x1 + bottom_bar_size_in_pixel);
//			int y = getHeight() - 20;
//			gs.drawLine(x1, y, x2, y);
//			gs.drawLine(x1, y, x1, y - 12);
//			gs.drawLine(x2, y, x2, y - 12);
//			double viewScaleWidthUnitTick = (double) (bottom_bar_size_in_pixel / 100d);
//
//			// ----------------------------------------------------
//			// 横のスケーラの目盛り（100段階、1単位ごと）を描画
//			// ----------------------------------------------------
//			for (int i = 1; i <= 99; i++) {
//				int currentX = (int) (x1 + (viewScaleWidthUnitTick * i));
//				int tickLength = 3; // 1単位の目盛りの初期長さ
//
//				if (i == 50) {
//					tickLength = 12; // 50単位マーク (一番長い)
//				} else if (i % 10 == 0) {
//					tickLength = 9; // 10単位ごと (中くらい長い)
//				} else if (i % 5 == 0) {
//					tickLength = 6; // 5単位ごと (標準)
//				}
//
//				// 目盛り線を描画
//				gs.drawLine(currentX, y, currentX, y - tickLength);
//			}
//		}
//	}
	
	/**
	 * Dynamic Scale Bar.
	 * 動的にサイズが変わるキャリパーを表示する
	 * 
	 * @param gs
	 */
	private void drawScaleBar(Graphics gs) {
		if (sg == null)
			return;

		// 1. 基本パラメータの計算
		Dimension orgDim = sg.getOriginalImageSize();
		if (orgDim == null || orgDim.height <= 0 || orgDim.width <= 0 || getWidth() <= 0 || getHeight() <= 0)
			return; // コンポーネントや画像サイズが未確定（リサイズ中など）の場合は描画をスキップする
		double viewScale = (double) getHeight() / orgDim.height; // 現在の表示倍率
		if (!Double.isFinite(viewScale) || viewScale <= 0)
			return;

		// 2. 単位とスペーシングの決定
		String rawUnit = sg.getPixelSpacingUnit();
		double spX = sg.getPixelSpacingX();
		double spY = sg.getPixelSpacingY();
		Color color;
		String displayUnit = "px";

		// 単位判定 (mm または um/µm)
		boolean isMm = "mm".equalsIgnoreCase(rawUnit);
		boolean isUm = "um".equalsIgnoreCase(rawUnit) || "µm".equalsIgnoreCase(rawUnit)
				|| "micro".equalsIgnoreCase(rawUnit);

		if ((isMm || isUm) && spX > 0 && spY > 0) {
			color = Color.YELLOW;
			// 表示用の単位文字列を整える
			displayUnit = isMm ? "mm" : "\u00B5m"; // µm
		} else {
			// ピクセル単位として扱う（フォールバック）
			spX = 1.0;
			spY = 1.0;
			color = Color.LIGHT_GRAY;
			displayUnit = "px";
		}

		gs.setColor(color);

		// 3. 縦と横で、短い方の距離を選択して統一する処理

		// 画面の幅・高さの約60%を最大ピクセル長とする
		double maxHPixels = getWidth() * 0.6;
		double maxVPixels = getHeight() * 0.6;

		// それを物理サイズ(またはpx単位)に変換
		// 実世界サイズ = ピクセル数 / 倍率 * 画素サイズ
		double maxHPhysical = maxHPixels / viewScale * spX;
		double maxVPhysical = maxVPixels / viewScale * spY;

		// 【変更点2】縦と横で「物理的に短い方」を基準にする
		double commonMaxPhysical = Math.min(maxHPhysical, maxVPhysical);

		// 「キリの良い」数値を決定 (例: 100mm, 50um...)
		double targetPhysicalSize = getNiceRoundNumber(commonMaxPhysical);

		// ----------------------------------------------------
		// 縦のスケーラ (左側に表示)
		// ----------------------------------------------------
		// Y位置は画面中央
		drawRuler(gs, true, targetPhysicalSize, viewScale, spY, displayUnit, 10, getHeight() / 2);

		// ----------------------------------------------------
		// 横のスケーラ (下側に表示)
		// ----------------------------------------------------
		// X位置は画面中央、Y位置は画面下部から20px上
		drawRuler(gs, false, targetPhysicalSize, viewScale, spX, displayUnit, getWidth() / 2, getHeight() - 10);
	}

	/**
	 * ルーラー（定規）を描画するヘルパーメソッド 変更点: 内部でサイズ計算せず、渡された targetPhysicalSize を描画することに専念する
	 * * @param gs Graphics context
	 * 
	 * @param isVertical         縦向きならtrue
	 * @param targetPhysicalSize 描画する物理サイズ（キリの良い数字、例: 100.0）
	 * @param viewScale          表示倍率
	 * @param pixelSpacing       物理サイズ/ピクセル (mm/px) または 1.0
	 * @param unit               単位文字列 ("mm", "µm", "px")
	 * @param centerX            描画中心X
	 * @param centerY            描画中心Y
	 */
	private void drawRuler(Graphics gs, boolean isVertical, double targetPhysicalSize, double viewScale,
			double pixelSpacing, String unit, int centerX, int centerY) {

		// 1. キリの良い数値を、画面上のピクセル長に変換
		// ピクセル数 = 実世界サイズ / 画素サイズ * 倍率
		double drawLengthPx = targetPhysicalSize / pixelSpacing * viewScale;

		// 2. 座標計算 (中心基準)
		int startX, startY, endX, endY;

		if (isVertical) {
			startX = centerX;
			startY = (int) (centerY - (drawLengthPx / 2));
			endX = centerX;
			endY = (int) (centerY + (drawLengthPx / 2));
		} else {
			startX = (int) (centerX - (drawLengthPx / 2));
			startY = centerY;
			endX = (int) (centerX + (drawLengthPx / 2));
			endY = centerY;
		}

		// --- メインの線を描画 ---
		gs.drawLine(startX, startY, endX, endY);

		// --- 両端の「ヒゲ」を描画 ---
		int barSize = 12; // ヒゲの長さ

		// 数値のフォーマット (小数は必要なら表示、整数なら.0を消す)
		String valueStr = (targetPhysicalSize % 1 == 0) ? String.valueOf((int) targetPhysicalSize)
				: String.valueOf(targetPhysicalSize);

		if (isVertical) {
			gs.drawLine(startX, startY, startX + barSize, startY);
			gs.drawLine(endX, endY, endX + barSize, endY);
			// テキスト描画 (上端の少し上)
			gs.drawString(valueStr + " " + unit, startX, startY - 5);
		} else {
			gs.drawLine(startX, startY, startX, startY - barSize);
			gs.drawLine(endX, endY, endX, endY - barSize);
			// テキスト描画 (右端の少し右)
			gs.drawString(valueStr + " " + unit, endX + 5, endY);
		}

		// --- 目盛りを描画 ---
		// 1単位(1mm or 1px)あたりの画面上の長さ
		double pixelsPerUnit = drawLengthPx / targetPhysicalSize;

		// 目盛りのループ回数
		// targetPhysicalSizeが小数の場合(例: 0.5)も考慮し、最小単位での描画ロジックが必要ですが、
		// ここでは簡易的に「数値が整数の場合」または「十分大きい場合」を前提とした既存ロジックを流用します。
		// ※厳密に0.1mm単位などを描画したい場合はループのステップを変更する必要があります。

		int totalUnits = (int) targetPhysicalSize;
		// 異常な拡大率や画像サイズによって totalUnits が極端に大きくなり、
		// 描画ループが暴走して EDT をフリーズさせることがあるため、安全な上限を設ける。
		final int MAX_TICKS = 2000;
		if (totalUnits > MAX_TICKS)
			totalUnits = MAX_TICKS;

		if (totalUnits > 0) {
			for (int i = 1; i < totalUnits; i++) {
				// 現在の単位位置での画面上のオフセット
				double offset = i * pixelsPerUnit;

				int tickLen = 3; // 小目盛り
				// 目盛りの間引きロジック (数値の大きさによって調整)
				if (i % 50 == 0)
					tickLen = 12;
				else if (i % 10 == 0)
					tickLen = 9;
				else if (i % 5 == 0)
					tickLen = 6;

				if (isVertical) {
					int y = (int) (startY + offset);
					gs.drawLine(startX, y, startX + tickLen, y);
				} else {
					int x = (int) (startX + offset);
					gs.drawLine(x, startY, x, startY - tickLen);
				}
			}
		}
	}

	/**
	 * 与えられた最大値以下で、最も適切な「キリの良い数字」を返す 改良版: 桁数が変わっても対応できるように対数的に計算する 例: 85 -> 50, 120
	 * -> 100, 0.8 -> 0.5
	 */
	private double getNiceRoundNumber(double maxVal) {
		if (maxVal <= 0)
			return 1.0;

		// 桁数を求める (例: 85 -> 10^1=10, 850 -> 10^2=100)
		double log10 = Math.log10(maxVal);
		double base = Math.pow(10, Math.floor(log10));

		// 先頭の数字 (例: 85なら 8.5)
		double unitVal = maxVal / base;

		// 候補: 1, 2, 5 (x base)
		if (unitVal >= 5) {
			return 5 * base;
		} else if (unitVal >= 2) {
			return 2 * base;
		} else {
			return 1 * base;
		}
	}
	
	public String sopInstanceUID() {
		return sopUID;
	}

	/**
	 * ROI ツール以外でも 3D ROI のホバー状態を検出し、カレントROIを更新する。
	 * 2D roiset には触れず、3D ROI リストのみを対象にする。
	 */
	void update3DRoiHover(int screenX, int screenY) {
		if (pp == null) return;
		java.util.List<RoiObj> roi3DList = pp.getRoi3DList();
		if (roi3DList == null || roi3DList.isEmpty()) return;

		// 全 3D ROI のアクティブ状態をリセット
		for (RoiObj roi3D : roi3DList) {
			if (roi3D != null) roi3D.setActiveOverlayRoi(false);
		}

		java.awt.Point p;
		try {
			p = sg.offScreenCoordinate(screenX, screenY);
		} catch (java.awt.geom.NoninvertibleTransformException e) {
			return;
		}
		int ix = p.x;
		int iy = p.y;

		boolean found = false;
		for (RoiObj roi3D : roi3DList) {
			if (roi3D == null) continue;
			roi3D.setSlideGlass(sg, false);
			if (roi3D.contains(ix, iy)) {
				roi3D.setActiveOverlayRoi(true);
				currentRoi = roi3D;
				sg.setCursor(new java.awt.Cursor(java.awt.Cursor.MOVE_CURSOR));
				found = true;
				break;
			}
		}
		if (!found) {
			// 現在 3D ROI がカレントなら解除
			if (currentRoi instanceof com.vis.core.view.D3.roi.SphereRoi3D) {
				currentRoi = null;
			}
			sg.setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));
		}
		repaint();
	}

	/** 3D リスト管理の ROI を Praparat3D リスト・DB・RoiObjManager から一括削除する */
	private void deleteSphere3D(RoiObj sphere) {
		sg.saveUndoState();
		if (pp != null) pp.removeRoi3D(sphere);
		sphere.notifyListeners(com.vis.core.ui.listener.RoiObjListener.DELETED);
		HashMap<RoiDBKey, String> uids = sphere.getUIDs();
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null) {
			db.deleteRoi(
				uids.get(RoiDBKey.PatientID),
				uids.get(RoiDBKey.StudyInstanceUID),
				uids.get(RoiDBKey.SeriesInstanceUID),
				uids.get(RoiDBKey.SOPInstanceUID),
				uids.get(RoiDBKey.RoiID)
			);
		}
		RoiObjManager rom = (RoiObjManager) com.vis.core.facade.WindowManager.getWindow(
				com.vis.configuration.ConfigInfo.RoiManager.toString());
		if (rom != null) rom.updateState();
	}

	public void updateRoi(RoiObj roi) {
		String patID = roi.getProperty(RoiDBKey.PatientID.name());
		String studyUID = roi.getProperty(RoiDBKey.StudyInstanceUID.name());
		String seriesUID = roi.getProperty(RoiDBKey.SeriesInstanceUID.name());
		String sopUID = roi.getProperty(RoiDBKey.SOPInstanceUID.name());
		String roiInd = roi.getProperty(RoiDBKey.RoiID.name());
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
				willUpdateRoi.setProperty(RoiDBKey.PatientID, patID);
				willUpdateRoi.setProperty(RoiDBKey.StudyInstanceUID, studyUID);
				willUpdateRoi.setProperty(RoiDBKey.SeriesInstanceUID, seriesUID);
				willUpdateRoi.setProperty(RoiDBKey.SOPInstanceUID, sopUID);
				willUpdateRoi.setProperty(RoiDBKey.RoiID, roiId);
				roiset.set(ind, willUpdateRoi);
				insertOrUpdateRoi4DB(willUpdateRoi);// saveRoi to db
			}
		}
	}
}
