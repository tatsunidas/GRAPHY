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
/* ***** BEGIN LICENSE BLOCK 省略 ***** */
package com.vis.core.view.D2.ui.glasses;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

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
 * @author tatsunidas
 */
public class SlideGlassMouseListener implements MouseListener, MouseMotionListener, MouseWheelListener {

	private SlideGlass slide;
	private Praparat pp;
	private Eyepiece prapManager;
	private CanvasGlass cg;
	private int viewerToolType = Viewer2DToolBar.Windowing;
	
	private int wheelRotationAccumulator = 0;
	private final int wheelThreshold = 2;
	
	/* ghost dragging */
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

		ActionListener act = e -> startGhostDrag();
		longPressTimer = new Timer(pressingTimeToBeGhost, act);
		longPressTimer.setRepeats(false); 
	}

	@Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int rotation = e.getWheelRotation();
        int mod = e.getModifiersEx();
        
        // ページング (修飾キーなし、またはC, Tキーとの組み合わせ)
        if ((mod & (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK)) == 0) {
            if (!pp.isShowGridViewOn()) {
                int step = (rotation < 0) ? -1 : 1;
                
                // SlideGlassKeyListener の static メソッドを使用して判定
                String targetDim = "Slice";
                if (SlideGlassKeyListener.isKeyPressed(KeyEvent.VK_C)) targetDim = "Channel";
                else if (SlideGlassKeyListener.isKeyPressed(KeyEvent.VK_T)) targetDim = "Time";
                
                ArrayList<Praparat> syncingPraps = (prapManager != null) ? prapManager.getSelectingPraparats() : null;
				
				// ★ 修正：選択されているPraparatが複数ある場合のみ同期スクロールさせる
				if (syncingPraps != null && syncingPraps.size() > 1) {
					for (Praparat p : syncingPraps) {
						p.stepDimension(targetDim, step);
					}
				} else {
					// 1つしか選択されていない、または未選択の場合は、マウスカーソルが乗っているPraparat単体を動かす
					if (pp.getViewMode() != ViewMode.FilmGrid) {
						pp.stepDimension(targetDim, step);
					}
				}
                e.consume();
            } else {
                // FilmGrid時は親のスクロールへ
                dispatchToGrid(e);
            }
            
        // Rotate (Ctrl + Wheel)
        } else if ((mod & InputEvent.CTRL_DOWN_MASK) != 0) {
            handleRotate(e,rotation);
            e.consume();
            
        // Zoom (Shift + Wheel)
        } else if ((mod & InputEvent.SHIFT_DOWN_MASK) != 0) {
            handleZoom(e);
            e.consume();
        }
    }

    private void dispatchToGrid(MouseWheelEvent e) {
        try {
            Component c = pp.getViewPanel().getComponent(0);
            if (c instanceof SlideGlassGrid) {
                MouseEvent me = SwingUtilities.convertMouseEvent(e.getComponent(), e, c);
                c.dispatchEvent(me);
            }
        } catch (Exception ex) {}
    }

	@Override
	public void mouseDragged(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		viewerToolType = pp.getViewer2DToolType();
		
		if(pp.mode == ViewMode.MPR) {
			Eyepiece eye = pp.getEyepiece();
			if (eye != null) {
				java.awt.Window w = SwingUtilities.getWindowAncestor(eye);
				MPRViewerWindow mprwin = null;
				if (w instanceof MPRViewerWindow) mprwin = (MPRViewerWindow) w;
				
				if (mprwin != null) {
					if (mprwin.getCurrentViewType() == MPRViewerWindow.CROSS_MODE) {
						if (eye.crossViewMode && pp.isShowCrossLineMode()) {
							Point p = null;
							try { p = slide.offScreenCoordinate(x, y); } 
							catch (NoninvertibleTransformException nte) { nte.printStackTrace(); }
							mprwin.updateCrossSectionViews(pp, p.x, p.y);
							return;
						} else if (eye.crossViewMode && !pp.isShowCrossLineMode()) {
							Point p = null;
							try { p = slide.offScreenCoordinate(x, y); } 
							catch (NoninvertibleTransformException nte) { nte.printStackTrace(); }
							mprwin.updateCrossSectionViews(pp, p.x, p.y);
							return;
						} else if (!eye.crossViewMode && pp.isShowCrossLineMode()) {
							return;
						}
					} else if (mprwin.getCurrentViewType() == MPRViewerWindow.SLICE_MODE) {
						ReferenceLineMPR refLines = pp.getReferenceLineMPR();
						if(refLines != null && refLines.getState() != RoiObj.NORMAL) {
							refLines.mouseDragged(pp, x, y, e.getModifiersEx());
							slide.lastDraggedX = x; slide.lastDraggedY = y;
							return;
						}
					}
				}
			}
		}
		
		if (pp.getViewMode() == ViewMode.Thumbnail) {
			viewerToolType = Viewer2DToolBar.Windowing;
		} else {
			if (viewerToolType == Viewer2DToolBar.NONE) viewerToolType = Viewer2DToolBar.Windowing;
		}
		
		if (isGhostDragging) {
			Component source = (Component) e.getSource();
			Point screenP = e.getPoint();
			SwingUtilities.convertPointToScreen(screenP, (Component) e.getSource());
			pp.getGhostGlassPane().moveDrag(screenP);
			Point panelPoint = SwingUtilities.convertPoint(source, e.getPoint(), prapManager);
			prapManager.updateInsertionIndex(panelPoint);
			pp.getGhostGlassPane().repaint();
			return;
		}

		Point current = e.getPoint();
		Point pressPoint = new Point(slide.lastPressedX, slide.lastPressedY);
		if (pressPoint.distance(current) > GHOST_MOVEMENT_THRESHOLD) {
			longPressTimer.stop();
		}

		if (viewerToolType == Viewer2DToolBar.Brush || Viewer2DToolBar.isRoiTool(viewerToolType)) {
			cg.mouseDragged(e);
		}
		
		if (viewerToolType == Viewer2DToolBar.Windowing) {
			if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown()) {
				pp.adjustContrastFromMouseAction(x, y);
			}
		}

		if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown() && !e.isShiftDown() && !e.isAltDown()) {
			if (pp.getViewMode() == ViewMode.Thumbnail) return;
			slide.setCursor(new Cursor(Cursor.MOVE_CURSOR));
			double moveX = slide.lastDraggedX - x;
			double moveY = slide.lastDraggedY - y;
			if (!pp.isProcessSeries()) {
				slide.panning(moveX, moveY);
			} else {
				synchronized (this) {
					ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for (Integer key : slides.keySet()) slides.get(key).panning(moveX, moveY);
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
		
		if(pp.mode == ViewMode.MPR) {
			ReferenceLineMPR refLines = pp.getReferenceLineMPR();
			if(refLines != null) {
				CenterPositionLine cenLine = refLines.centerPositionLineHereAt(pp, e.getX(), e.getY());
				if(cenLine != null) {
					slide.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
				}else {
					boolean rotateArea = pp.getReferenceLineMPR().isPeripheralArea(pp, e.getX(), e.getY());
					if(rotateArea) slide.setCursor(new RotateCursor(null).createCursor());
					else slide.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
				}
			}
		}
		
		if(Viewer2DToolBar.isRoiTool(viewerToolType)) {
			cg.mouseMoved(e);
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		viewerToolType = pp.getViewer2DToolType();
		
		if (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown()) {
			if(cg.setSelectStateOfCurrentRoi(e)) {
				e.consume();
				return;
			}
			slide.setSelectionState();
			if (pp.getViewMode() != ViewMode.Thumbnail) pp.setSelectionState(true);
			e.consume();
		}
		
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
		if (pp.getViewMode() == ViewMode.Thumbnail) viewerToolType = Viewer2DToolBar.Windowing;
		
		slide.mouseX = e.getX();
		slide.mouseY = e.getY();
		slide.lastDraggedX = e.getX();
		slide.lastDraggedY = e.getY();
		slide.lastPressedX = e.getX();
		slide.lastPressedY = e.getY();
		slide.startChangeContrastWW = slide.currentMax - slide.currentMin;
		slide.startChangeContrastWL = slide.currentMin + (slide.startChangeContrastWW/2.);
		
		if (pp.getViewMode() == Praparat.ViewMode.FilmGrid) {
			pp.setImagePositionTo(slide);
		}
		
		isGhostDragging = false;		
		longPressTimer.start();
		
		if (SwingUtilities.isLeftMouseButton(e) && !e.isShiftDown()) {
			if(pp.mode == ViewMode.MPR) {
				Eyepiece eye = pp.getEyepiece();
				if(eye != null && eye.crossViewMode) {
					List<Praparat> praps = eye.getAllPraparat();
					for(Praparat prap : praps) {
						if(prap.getViewMode() == Praparat.ViewMode.MPR) {
							CanvasGlass cg_ = (CanvasGlass)prap.getCurrentSlide().getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
							cg_.setLocalizerGeometry(null);
						}
					}
				}
				ReferenceLineMPR refLines = pp.getReferenceLineMPR();
				if(refLines != null) {
					refLines.mousePressed(pp, e.getX(), e.getY());
					if(refLines.getState() != RoiObj.NORMAL) {
						longPressTimer.stop(); 
						return;
					}
				}
			}
			
			if (viewerToolType == Viewer2DToolBar.NONE || viewerToolType == Viewer2DToolBar.Windowing) {
				if (!pp.isProcessSeries()) {
					slide.lastMin = slide.currentMin;
					slide.lastMax = slide.currentMax;
				} else {
					ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for (Integer key : slides.keySet()) {
						SlideGlass sg = slides.get(key);
						sg.lastPressedX = e.getX(); sg.lastPressedY = e.getY();
						sg.lastDraggedX = e.getX(); sg.lastDraggedY = e.getY();
						sg.lastMin = sg.currentMin; sg.lastMax = sg.currentMax;
					}
				}
			}
		}
		
		if (SwingUtilities.isLeftMouseButton(e)) {
			if (viewerToolType == Viewer2DToolBar.Brush || Viewer2DToolBar.isRoiTool(viewerToolType)) {
				longPressTimer.stop();
				cg.mousePressed(e);
				return;
			}
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		longPressTimer.stop();
		viewerToolType = pp.getViewer2DToolType();
		
		if(Viewer2DToolBar.isRoiTool(viewerToolType) || viewerToolType == Viewer2DToolBar.Brush) {
			cg.mouseReleased(e);
		}
		
		if (pp.mode == ViewMode.MPR) {
			Eyepiece eye = pp.getEyepiece();
			if (eye != null && eye.crossViewMode) {
				List<Praparat> praps = eye.getAllPraparat();
				for (Praparat prap : praps) {
					if (prap.getViewMode() == Praparat.ViewMode.MPR) prap.clearCrossLines();
				}
			}
			ReferenceLineMPR refLines = pp.getReferenceLineMPR();
			if(refLines != null) refLines.mouseReleased();
		}
		
		if (isGhostDragging) {
			prapManager.performReorder();
			GhostGlassPane ggp = pp.getGhostGlassPane();
			ggp.setVisible(false);
			prapManager.setDraggingComponent(null);
			isGhostDragging = false;
			e.consume();
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		JFrame v2d = (JFrame)WindowManager.getWindow(ConfigInfo.D2ViewerWindow);
		if(v2d != null) v2d.toFront();
		viewerToolType = pp.getViewer2DToolType();
		slide.setFocusGained(true);
		pp.setFocusGained(true);
	}

	@Override
	public void mouseExited(MouseEvent e) {
		viewerToolType = pp.getViewer2DToolType();
		slide.setFocusGained(false);
		pp.setFocusGained(false);
	}
	
	private void handleRotate(MouseEvent e, int rotation) {
		if (pp.getViewMode() == ViewMode.Thumbnail) return;
		logger.fine("rotate! " + rotation);
		this.slide.setCursor(new Cursor(Cursor.WAIT_CURSOR));
		if (!pp.isProcessSeries()) {
			this.slide.rotate(rotation);
		} else {
			ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
			for (Integer key : slides.keySet()) slides.get(key).rotate(rotation);
		}
		this.slide.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		e.consume();
	}
	
	private void handleZoom(MouseEvent e) {
		if (pp.getViewMode() == ViewMode.Thumbnail) return;
		if (Math.abs(wheelRotationAccumulator) >= wheelThreshold) {
			logger.fine("zoom performed!");
			this.slide.setCursor(new Cursor(Cursor.WAIT_CURSOR));
			double currentMag = slide.getMagnification();
			double change = 0.1;
			boolean zoomUp = false;
			if (wheelRotationAccumulator > 0) { 
				currentMag -= change;
			} else { 
				currentMag += change;
				zoomUp = true;
			}
			if (!pp.isProcessSeries()) {
				slide.zoom(currentMag, zoomUp);
			} else {
				ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
				for (Integer key : slides.keySet()) slides.get(key).zoom(currentMag, zoomUp);
			}
			this.slide.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
			wheelRotationAccumulator = 0;
		}
		e.consume();
	}
	
	private void startGhostDrag() {
		if (pp == null || prapManager == null || pp.isAttachedToMainFrame()) {
			isGhostDragging = false;
			return;
		}
		
		GhostGlassPane ggp = pp.getGhostGlassPane();
		if (ggp == null) {
			isGhostDragging = false;
			return;
		}
		
		prapManager.setDraggingComponent(pp);
		isGhostDragging = true;

		Point mouseLoc = MouseInfo.getPointerInfo().getLocation();
		BufferedImage img = new BufferedImage(slide.getWidth(), slide.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		slide.paint(g2);
		g2.dispose();

		ggp.startDrag(img, mouseLoc);
		ggp.setVisible(true);
	}
}
