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
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.vis.core.log.Log;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;

public class SlideGlassMouseListener implements MouseListener, MouseMotionListener, MouseWheelListener {

	private SlideGlass slide;
	private Praparat pp;
	private Eyepiece prapManager;
	private int viewerToolType = Viewer2DToolBar.Windowing;
	private Logger logger = Log.logger;

	public SlideGlassMouseListener(SlideGlass slide) {
		this.slide = slide;
		this.pp = slide.getPraparat();
		this.prapManager = pp.getEyepieceAsPraparatManager();
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		int rotation = e.getWheelRotation();
		int mod = e.getModifiersEx();
		// paging
		if ((mod & InputEvent.CTRL_DOWN_MASK) == 0 && (mod & InputEvent.SHIFT_DOWN_MASK) == 0) {
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
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {

		int x = e.getX();
		int y = e.getY();
		slide.mouseX = x;
		slide.mouseY = y;

		// MPR
		/*
		 * TODO 20240811 ドラッグでクロスラインを描画したら、WW/wL変更ができないじゃないか。
		 */
//		if(pp.isShowCrossLineMode()) {
//			slide.drawCross(e);
//			return;//attention
//		}

		viewerToolType = pp.getViewer2DToolType();
		if (pp.getViewMode() == ViewMode.Thumbnail) {
			/* Force windowing */
			viewerToolType = Viewer2DToolBar.Windowing;
		} else {
			if (viewerToolType == Viewer2DToolBar.NONE /*-1*/) {
				viewerToolType = Viewer2DToolBar.Windowing;
			}
		}

		// roi brush
		if (viewerToolType == Viewer2DToolBar.Brush) {
			if (slide.handleRoiMouseDragged(e)) {
				return;
			}
		}
		// roi
		if (Viewer2DToolBar.isRoiTool(viewerToolType)) {
			if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
				if (slide.handleRoiMouseDragged(e)) {
					return;
				}
			}
		}

		// reference line
		if (pp.getReferenceLine() != null) {
			if (slide.handleRoiMouseDragged(e)) {
				return;
			}
			return;// attention
		}

		/*
		 * WW/WL
		 */
		if (viewerToolType == Viewer2DToolBar.Windowing) {
			if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown()) {
				// WW/WL left button
				if (!pp.isProcessSeries()) {
					slide.adjustWindowFromMouseAction(x, y);
				} else {
					HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for (Integer key : slides.keySet()) {
						SlideGlass sg = slides.get(key);
						sg.adjustWindowFromMouseAction(x, y);
					}
				}
			}
		}

		// zoom
		if (SwingUtilities.isMiddleMouseButton(e)) {
			if (pp.getViewMode() == ViewMode.Thumbnail) {
				return;
			}
			/*
			 * only calcurate mag
			 */
			int currentDragY = e.getY();
			if (!pp.isProcessSeries()) {
				// lastDraggedYはEnter時に更新されている
				double diffY = slide.lastDraggedY - currentDragY;
				double change = 0.005 * diffY;// 緩やかに拡大させるために小さく
				double currentMag = slide.getMagnification();
				double newMag = currentMag + change;
				logger.info("dragging to zoom : lastY " + slide.lastY + " NowDragging:" + currentDragY + " mag:"
						+ newMag + " diffY:" + diffY);
				slide.zoom(newMag);
				slide.lastDraggedX = e.getX();
				slide.lastDraggedY = currentDragY;
			} else {
				HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
				double newMag = -1;
				for (Integer key : slides.keySet()) {
					SlideGlass sg = slides.get(key);
					double diffY = sg.lastDraggedY - currentDragY;
					// get current mag
					double mag = sg.getMagnification();
					double magFactor = 0.005 * diffY;// 緩やかに拡大させるために小さくする
					newMag = mag + magFactor;
					sg.zoom(newMag);
					sg.lastDraggedX = e.getX();
					sg.lastDraggedY = currentDragY;
				}
				System.out.println("zooming : mag:" + newMag);
			}
		}

		// panning
		/*
		 * panning中は、実寸サイズと表示サイズの比を考慮したscaled originで考える。 pannnig後は、バックスケールする
		 */
		if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown()) {
			if (pp.getViewMode() == ViewMode.Thumbnail) {
				return;
			}
			slide.setCursor(new Cursor(Cursor.MOVE_CURSOR));
			double moveX = slide.lastX - e.getX();
			double moveY = slide.lastY - e.getY();
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
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		slide.mouseX = x;
		slide.mouseY = y;
		slide.updatePrapInfoLabel(x, y);
		// roi
		slide.handleRoiMouseMoved(e);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mousePressed(MouseEvent e) {
		// set start point for ww/wl, panning, roi
		if (SwingUtilities.isLeftMouseButton(e) && !e.isShiftDown() && !e.isControlDown() && !e.isAltDown()) {
			logger.fine("mouse pressed (x,y):" + e.getX() + " " + e.getY());
			viewerToolType = pp.getViewer2DToolType();
			if (pp.getViewMode() == ViewMode.Thumbnail) {
				viewerToolType = Viewer2DToolBar.Windowing;
			}else {
				if(viewerToolType == Viewer2DToolBar.NONE) {
					viewerToolType = Viewer2DToolBar.Windowing;
				}
			}
			
			if (viewerToolType == Viewer2DToolBar.Brush || Viewer2DToolBar.isRoiTool(viewerToolType)) {
				slide.handleRoiMousePressed(e);
				return;
			}
			
			if (viewerToolType == Viewer2DToolBar.Windowing) {
				// WW/WL
				if (!pp.isProcessSeries()) {
					slide.lastX = e.getX();
					slide.lastY = e.getY();
					slide.lastOriginX = slide.imageSpecimen.originX;
					slide.lastOriginY = slide.imageSpecimen.originY;
					slide.lastMin = slide.getCurrentDisplayImagePlus().getDisplayRangeMin();
					slide.lastMax = slide.getCurrentDisplayImagePlus().getDisplayRangeMax();
				} else {
					HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for (Integer key : slides.keySet()) {
						SlideGlass sg = slides.get(key);
						sg.lastX = e.getX();
						sg.lastY = e.getY();
						sg.lastOriginX = sg.imageSpecimen.originX;
						sg.lastOriginY = sg.imageSpecimen.originY;
						sg.lastMin = sg.getCurrentDisplayImagePlus().getDisplayRangeMin();
						sg.lastMax = sg.getCurrentDisplayImagePlus().getDisplayRangeMax();
					}
				}
			} // ww/wl end
		} // left btn down end

		// select current slideglass
		if (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown()) {
			slide.setSelectionState();
		}

		// zoom
		if (SwingUtilities.isMiddleMouseButton(e)) {
			/*
			 * USB Mouse issue. Issue that is fired pressed action continuity. To avoid this
			 * issue, DO NOT USE these USB Mouses
			 */
			if (pp.getViewMode() == ViewMode.Thumbnail) {
				return;
			}
			logger.info("zoom : middle mouse btn pressed!!");
			if (!pp.isProcessSeries()) {
				slide.lastX = e.getX();// for move position
				slide.lastY = e.getY();// for move position
				slide.lastDraggedX = e.getX();// for cappulate mag
				slide.lastDraggedY = e.getY();// for cappulate mag
				slide.lastOriginX = slide.imageSpecimen.originX;
				slide.lastOriginY = slide.imageSpecimen.originY;
			} else {
				HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
				for (Integer key : slides.keySet()) {
					SlideGlass sg = slides.get(key);
					sg.lastX = e.getX();
					sg.lastY = e.getY();
					sg.lastOriginX = sg.imageSpecimen.originX;
					sg.lastOriginY = sg.imageSpecimen.originY;
					sg.lastDraggedX = e.getX();
					sg.lastDraggedY = e.getY();
				}
			}
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		// roi
		slide.handleRoiMouseUp(e);
		// release panning
		if (!pp.isProcessSeries()) {
			if (slide.panningInAction) {
				slide.releasePanning();
			}
		} else {
			// process series
			Log.logger.fine("panning series released !! mouse released.");
			if (slide.panningInAction) {
				slide.releasePanning();
			}
			synchronized (this) {
				HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
				for (Integer key : slides.keySet()) {
					SlideGlass sg = slides.get(key);
					if (sg.panningInAction) {
						sg.releasePanning();
					}
				}
			}
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		slide.setFocusGained(true);
	}

	@Override
	public void mouseExited(MouseEvent e) {
		slide.setFocusGained(false);
	}

}
