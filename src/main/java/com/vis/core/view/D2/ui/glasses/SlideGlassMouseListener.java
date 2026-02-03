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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.GhostGlassPane;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.core.view.D2.ui.cursor.RotateCursor;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.mpr.CenterPositionLine;
import com.vis.core.view.mpr.MPRViewerWindow;
import com.vis.core.view.mpr.ReferenceLineMPR;

/**
 * 
 * @author tatsunidas
 *
 */
public class SlideGlassMouseListener implements MouseListener, MouseMotionListener, MouseWheelListener {

	private SlideGlass slide;
	private Praparat pp;
	private Eyepiece prapManager;
	private CanvasGlass cg;
	private int viewerToolType = Viewer2DToolBar.Windowing;
	
	private int wheelRotationAccumulator = 0;
	private final int wheelThreshold = 2;
	
	/*
	 * ghost dragging
	 */
	private Timer longPressTimer;
	private int pressingTimeToBeGhost = 1500;
	private int GHOST_MOVEMENT_THRESHOLD = 3;
	boolean isGhostDragging = false;
		
	private Logger logger = Log.logger;

	public SlideGlassMouseListener(SlideGlass slide) {
		this.slide = slide;
		this.cg = (CanvasGlass) slide.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
		this.pp = slide.getPraparat();
		this.prapManager = pp.getEyepiece();

		ActionListener act = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				startGhostDrag();
			}
		};

		longPressTimer = new Timer(pressingTimeToBeGhost, act);
		longPressTimer.setRepeats(false); // 1回だけ実行
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		int rotation = e.getWheelRotation();
		int mod = e.getModifiersEx();
		
		viewerToolType = pp.getViewer2DToolType();
		
		if (!pp.isProcessSeries()) {
			slide.mouseX = e.getX();
			slide.mouseY = e.getY();
		} else {
			HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.mouseX = e.getX();
				sg.mouseY = e.getY();
			}
		}
		
//		CanvasGlass cg = (CanvasGlass) slide.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
//		RoiObj roi = cg.activateAndGetCurrentRoiAt(slide.mouseX, slide.mouseY);
		
		wheelRotationAccumulator += rotation;
		// paging
		if ((mod & InputEvent.CTRL_DOWN_MASK) == 0 && (mod & InputEvent.SHIFT_DOWN_MASK) == 0 && (mod & InputEvent.ALT_DOWN_MASK) == 0) {
			if (!pp.isShowGridViewOn()) {// single grid true
				ArrayList<Praparat> syncingPraps = null;
				if (prapManager != null) {
					syncingPraps = prapManager.getSelectingPraparats();
				}
				if (syncingPraps != null) {
					if (syncingPraps.size() > 1) {
						for (Praparat prap : syncingPraps) {
							int pos = prap.getCurrentSlidePos();
							if (rotation < 0) {
								pos -= 1;
							} else {
								pos += 1;
							}
							prap.setImagePositionUsingSlider(pos);// work with slider
						}
					} else {
						int pos = pp.getCurrentSlidePos();
						if (rotation < 0) {
							pos -= 1;
						} else {
							pos += 1;
						}
						pp.setImagePositionUsingSlider(pos);// work with slider
					}
				} else {
					int pos = pp.getCurrentSlidePos();
					if (rotation < 0) {
						pos -= 1;
					} else {
						pos += 1;
					}
					if (pp.getViewMode() != ViewMode.FilmGrid) {
						pp.setImagePositionUsingSlider(pos);// work with slider
					}
				}
				e.consume();
			} else {// showGridViewOn
				try {
					Component t = e.getComponent();
					Component c = pp.getViewPanel().getComponent(0);
					if (c instanceof SlideGlassGrid) {
						SlideGlassGrid gridPane = (SlideGlassGrid) c;
						MouseEvent me = SwingUtilities.convertMouseEvent(t, e, gridPane);
						gridPane.dispatchEvent(me);
						e.consume();/* consume after dispatch */
					}
				} catch (ArrayIndexOutOfBoundsException aioobe) {
					// do nothing
				}
				e.consume();
			}
		// rotate
		} else if ((mod & InputEvent.CTRL_DOWN_MASK) != 0 && (mod & InputEvent.SHIFT_DOWN_MASK) == 0
				&& (mod & InputEvent.ALT_DOWN_MASK) == 0) {
			if (pp.getViewMode() == ViewMode.Thumbnail) {
				return;
			}
			logger.fine("rotate! " + rotation);
			this.slide.setCursor(new Cursor(Cursor.WAIT_CURSOR));
			if (!pp.isProcessSeries()) {
				this.slide.rotate(rotation);
			} else {
				HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
				for (Integer key : slides.keySet()) {
					SlideGlass sg = slides.get(key);
					sg.rotate(rotation);
				}
			}
			this.slide.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
			e.consume();
		// zoom
		} else if ((mod & InputEvent.CTRL_DOWN_MASK) == 0 && (mod & InputEvent.SHIFT_DOWN_MASK) != 0
				&& (mod & InputEvent.ALT_DOWN_MASK) == 0) {
			if (pp.getViewMode() == ViewMode.Thumbnail) {
				return;
			}
			if (Math.abs(wheelRotationAccumulator) >= wheelThreshold) {
				logger.fine("zoom performed!");
				this.slide.setCursor(new Cursor(Cursor.WAIT_CURSOR));
				double currentMag = slide.getMagnification();
				double change = 0.1;
				boolean zoomUp = false;
				if (wheelRotationAccumulator > 0) { // Turn the wheel down to reduce
					currentMag -= change;
				} else { // Turn the wheel up to large
					currentMag += change;
					zoomUp = true;
				}
				if (!pp.isProcessSeries()) {
					slide.zoom(currentMag, zoomUp);
				} else {
					HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for (Integer key : slides.keySet()) {
						SlideGlass sg = slides.get(key);
						sg.zoom(currentMag, zoomUp);
					}
				}
				this.slide.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
				wheelRotationAccumulator = 0;
			}
			e.consume();
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {

		//screen x,y
		int x = e.getX();
		int y = e.getY();
		
		viewerToolType = pp.getViewer2DToolType();
		Log.logger.fine("Dragging , ViewerTool is "+viewerToolType);
		
		// MPR
		if(pp.mode == ViewMode.MPR) {
			Eyepiece eye = pp.getEyepiece();
			if (eye != null) {
				java.awt.Window w = SwingUtilities.getWindowAncestor(eye);
				MPRViewerWindow mprwin = null;
				if (w instanceof MPRViewerWindow) {
					mprwin = (MPRViewerWindow) w;
				}
				if (mprwin != null) {
					if (mprwin.getCurrentViewType() == MPRViewerWindow.CROSS_MODE) {
						if (eye.crossViewMode && pp.isShowCrossLineMode()) {
							Point p = null;
							try {
								p = slide.offScreenCoordinate(x, y);
							} catch (NoninvertibleTransformException nte) {
								nte.printStackTrace();
								Log.logger.log(Level.SEVERE, "Can not translate offscreen coordinates...1");
							}
							
							int ox = p.x;
							int oy = p.y;
							
							mprwin.updateCrossSectionViews(pp, ox, oy);
//							if (pp.isShowCrossLineMode()) {
//								cg.createCross(e);
//							}
							return;
						} else if (eye.crossViewMode && !pp.isShowCrossLineMode()) {
							Point p = null;
							try {
								p = slide.offScreenCoordinate(x, y);
							} catch (NoninvertibleTransformException nte) {
								nte.printStackTrace();
								Log.logger.log(Level.SEVERE, "Can not translate offscreen coordinates...2");
							}
							
							int ox = p.x;
							int oy = p.y;
							mprwin.updateCrossSectionViews(pp, ox, oy);
							return;
						} else if (!eye.crossViewMode && pp.isShowCrossLineMode()) {
//							cg.createCross(e);
							return;
						}
					} else if (mprwin.getCurrentViewType() == MPRViewerWindow.SLICE_MODE) {
						ReferenceLineMPR refLines = pp.getReferenceLineMPR();
						if(refLines != null && refLines.getState() != RoiObj.NORMAL) {
							refLines.mouseDragged(pp, x, y, e.getModifiersEx());
							slide.lastDraggedX = x;
							slide.lastDraggedY = y;
							return;
						}
					}
				}
			}
		}
		
		if (pp.getViewMode() == ViewMode.Thumbnail) {
			/* Force windowing */
			viewerToolType = Viewer2DToolBar.Windowing;
		} else {
			if (viewerToolType == Viewer2DToolBar.NONE /*-1*/) {
				viewerToolType = Viewer2DToolBar.Windowing;
			}
		}
		
		// 1. すでにドラッグモードになっている場合 -> 通常の移動処理
		if (isGhostDragging) {
			Component source = (Component) e.getSource();
			Point screenP = e.getPoint();
			SwingUtilities.convertPointToScreen(screenP, (Component) e.getSource());
			pp.getGhostGlassPane().moveDrag(screenP);

			//location
			Point panelPoint = SwingUtilities.convertPoint(source, e.getPoint(), prapManager);
			prapManager.updateInsertionIndex(panelPoint);
	       pp.getGhostGlassPane().repaint();
			return;
		}
        // 2. まだドラッグモードでない場合 -> 動きの監視
        // 移動量のしきい値を超えて動いたら、長押し失敗としてタイマーを解除
		Point current = e.getPoint();
		Point pressPoint = new Point(slide.lastPressedX, slide.lastPressedY);
		if (pressPoint.distance(current) > GHOST_MOVEMENT_THRESHOLD) {
			longPressTimer.stop();
		}

		// roi or brush
		if (viewerToolType == Viewer2DToolBar.Brush || Viewer2DToolBar.isRoiTool(viewerToolType)) {
			cg.mouseDragged(e);
		}
		
		/*
		 * WW/WL
		 */
		if (viewerToolType == Viewer2DToolBar.Windowing) {
			if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown()) {
				// WW/WL left button
				pp.adjustContrastFromMouseAction(x, y);
			}
		}

		if (SwingUtilities.isMiddleMouseButton(e)) {
			if (pp.getViewMode() == ViewMode.Thumbnail) {
				return;
			}
			//do something
		}

		// panning
		if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown() && !e.isShiftDown() && !e.isAltDown()) {
			if (pp.getViewMode() == ViewMode.Thumbnail) {
				return;
			}
			slide.setCursor(new Cursor(Cursor.MOVE_CURSOR));
			double moveX = slide.lastDraggedX - x;
			double moveY = slide.lastDraggedY - y;
			if (!pp.isProcessSeries()) {
				slide.panning(moveX, moveY);
			} else {
				// process series
				synchronized (this) {
					HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for (Integer key : slides.keySet()) {
						SlideGlass sg = slides.get(key);
						sg.panning(moveX, moveY);
					}
				}
			}
			slide.lastDraggedX = x;
			slide.lastDraggedY = y;
			slide.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		viewerToolType = pp.getViewer2DToolType();
		
		slide.mouseX = x;
		slide.mouseY = y;
		slide.updatePrapInfoLabel(x, y);
		
		// MPR
		if(pp.mode == ViewMode.MPR) {
			/*
			 * Reference line is not included in rois array.
			 * It is need to handle as another object.
			 */
			ReferenceLineMPR refLines = pp.getReferenceLineMPR();
			if(refLines != null) {
				//find and activate reference line.
				CenterPositionLine cenLine = refLines.centerPositionLineHereAt(pp, e.getX(), e.getY());
				if(cenLine != null) {
					Log.logger.fine("CanvasComponent: "+cenLine.getClass().getName());
					slide.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
				}else {
					//do something
					Log.logger.fine("isInBoundingBox? "+pp.getReferenceLineMPR().isBoundingBox(pp, e.getX(), e.getY()));
					boolean rotateArea = pp.getReferenceLineMPR().isPeripheralArea(pp, e.getX(), e.getY());
					Log.logger.fine("isNearCorner? "+rotateArea);
					if(rotateArea) {
						slide.setCursor(new RotateCursor(null).createCursor());
					}else {
						slide.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
					}
				}
			}
		}
		
		// roi
		if(Viewer2DToolBar.isRoiTool(viewerToolType)) {
			cg.mouseMoved(e);
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		
		viewerToolType = pp.getViewer2DToolType();
		
		// handle select event
		if (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown()) {
			if(cg.setSelectStateOfCurrentRoi(e)) {
				//if true (currentRoi not null and selected)
				e.consume();
				return;
			}
			slide.setSelectionState();
			if (pp.getViewMode() != ViewMode.Thumbnail) {
				pp.setSelectionState(true);
			}
			e.consume();
		}
		
		// handle double click event.
		if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && !e.isConsumed()) {
			if (pp.getViewMode() == ViewMode.Thumbnail) {
				MainScreen ms = WindowManager.getMainScreen();
				if (ms != null) {
					ms.showImagesOnBirdsEye(pp);
					e.consume();
				}
			}
			
			if(viewerToolType == Viewer2DToolBar.TextRoi) {
				cg.mouseDoubleClicked(e);
			}
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {
		
		viewerToolType = pp.getViewer2DToolType();
		if (pp.getViewMode() == ViewMode.Thumbnail) {
			viewerToolType = Viewer2DToolBar.Windowing;
		}
		
		slide.mouseX = e.getX();
		slide.mouseY = e.getY();
		slide.lastDraggedX = e.getX();
		slide.lastDraggedY = e.getY();
		slide.lastPressedX = e.getX();
		slide.lastPressedY = e.getY();
		slide.startChangeContrastWW = slide.currentMax - slide.currentMin;
		slide.startChangeContrastWL = slide.currentMin + (slide.startChangeContrastWW/2.);
		
		isGhostDragging = false;		
		longPressTimer.start();
		
		if (SwingUtilities.isLeftMouseButton(e) && !e.isShiftDown()) {
			logger.fine("mouse pressed (x,y):" + e.getX() + " " + e.getY());
			// MPR
			if(pp.mode == ViewMode.MPR) {
				//remove localizer line
				Eyepiece eye = pp.getEyepiece();
				if(eye != null && eye.crossViewMode) {
					List<Praparat> praps = eye.getAllPraparat();
					for(Praparat pp : praps) {
						if(pp.getViewMode() == Praparat.ViewMode.MPR) {
							SlideGlass sg = pp.getCurrentSlide();
							CanvasGlass cg = (CanvasGlass)sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
							cg.setLocalizerGeometry(null);
						}
					}
				}
				ReferenceLineMPR refLines = pp.getReferenceLineMPR();
				if(refLines != null) {
					//if will rotate or move referencelines, return
					refLines.mousePressed(pp, e.getX(), e.getY());
					if(refLines.getState() != RoiObj.NORMAL) {
						return;
					}//else, continue to following.
				}
			}
			
			if (viewerToolType == Viewer2DToolBar.Brush || Viewer2DToolBar.isRoiTool(viewerToolType)) {
				cg.mousePressed(e);
				return;
			}
			
			if (viewerToolType == Viewer2DToolBar.NONE || viewerToolType == Viewer2DToolBar.Windowing) {
				if (!pp.isProcessSeries()) {
					slide.lastMin = slide.currentMin;
					slide.lastMax = slide.currentMax;
				} else {
					HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for (Integer key : slides.keySet()) {
						SlideGlass sg = slides.get(key);
						sg.lastPressedX = e.getX();
						sg.lastPressedY = e.getY();
						sg.lastDraggedX = e.getX();
						sg.lastDraggedY = e.getY();
						sg.lastMin = sg.currentMin;
						sg.lastMax = sg.currentMax;
					}
				}
			} // ww/wl end
		} // left btn down end

		// do something ?
		if (SwingUtilities.isMiddleMouseButton(e)) {
			
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		
		/*
		 * reset monitoring ghost dragging
		 */
		longPressTimer.stop();
		
		viewerToolType = pp.getViewer2DToolType();
		
		// roi
		if(Viewer2DToolBar.isRoiTool(viewerToolType) || viewerToolType == Viewer2DToolBar.Brush) {
			cg.mouseReleased(e);
		}
		
		// MPR
		if (pp.mode == ViewMode.MPR) {
			// remove cross line
			Eyepiece eye = pp.getEyepiece();
			if (eye != null && eye.crossViewMode) {
				List<Praparat> praps = eye.getAllPraparat();
				for (Praparat pp : praps) {
					if (pp.getViewMode() == Praparat.ViewMode.MPR) {
						pp.clearCrossLines();
					}
				}
			}
			ReferenceLineMPR refLines = pp.getReferenceLineMPR();
			if(refLines != null) {
				refLines.mouseReleased();
			}
		}
		
		if (isGhostDragging) {
			// ドラッグ完了処理
			prapManager.performReorder();
			GhostGlassPane ggp = pp.getGhostGlassPane();
			// ドラッグ終了処理
			ggp.setVisible(false);
			prapManager.setDraggingComponent(null);
			isGhostDragging = false;
			e.consume();
		} else {
			// do nothing
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		//first, show to top 2d viewer window
		JFrame v2d = (JFrame)WindowManager.getWindow(ConfigInfo.D2ViewerWindow);
		if(v2d != null) {
			v2d.toFront();//important to enable focus only mouse move.
		}
		viewerToolType = pp.getViewer2DToolType();
		/*
		 * show borders
		 */
		slide.setFocusGained(true);
		pp.setFocusGained(true);
	}

	@Override
	public void mouseExited(MouseEvent e) {
		viewerToolType = pp.getViewer2DToolType();
		slide.setFocusGained(false);
		pp.setFocusGained(false);
	}
	
	private void startGhostDrag() {
		if (pp == null || prapManager == null) {
			isGhostDragging = false;
			return;
		}
		
		if(pp.isAttachedToMainFrame()) {
			return;
		}
		
		GhostGlassPane ggp = pp.getGhostGlassPane();
		if (ggp == null) {
			isGhostDragging = false;
			Log.logger.warning("If you want to Dragging SlideGlass in Eyepiece, setGhostGlassPane to Prap.");
			return;
		}
		
		prapManager.setDraggingComponent(pp);

		isGhostDragging = true;

		// 視覚的フィードバック（カーソルが変わる、少し浮くなど）
		// 現在のマウス位置を取得する必要がある
		Point mouseLoc = MouseInfo.getPointerInfo().getLocation();

		// ゴースト画像の作成
		BufferedImage img = new BufferedImage(slide.getWidth(), slide.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		slide.paint(g2);
		g2.dispose();

		// Use, 2DViewer GhostGlassPane or another JFrame/JDialog's GhostGlassPane
		ggp.startDrag(img, mouseLoc);
		ggp.setVisible(true);

		Log.logger.fine("Ghost Drag Mode Activated!");
	}

}
