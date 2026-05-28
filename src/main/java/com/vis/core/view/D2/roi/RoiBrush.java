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
import java.awt.event.MouseEvent;
import java.awt.geom.NoninvertibleTransformException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.view.D2.ui.glasses.*;

/**
 * @author tatsunidas
 */
public class RoiBrush {
	final static int ADD = 0, SUBTRACT = 1;
	private int mode = ADD;

	private boolean isNewRoiMode = false;

	int defaultSize = 15;
	String defaultType = "Circle";

	SlideGlass slide = null;
	ShapeRoi brush = null;

	volatile RoiObj currentBrushingRoi = null;
	private RoiObj lastOperatedRoi = null;

	private RoiObj internalWorkingRoi = null;
	private ExecutorService calcExecutor;

	public RoiBrush(SlideGlass slide, MouseEvent pressedEvent, boolean createBrush) {
		this.slide = slide;
		if (createBrush) {
			createBrush(pressedEvent);
		}
	}

	public void createBrush(MouseEvent pressedEvent) {
		if (slide == null) return;

		if (calcExecutor == null || calcExecutor.isShutdown()) {
			calcExecutor = Executors.newSingleThreadExecutor();
		}

		determineModeAndTarget(pressedEvent);
		updateBrushShape(pressedEvent.getX(), pressedEvent.getY());

		final RoiObj startRoi = currentBrushingRoi;
		calcExecutor.submit(() -> {
			if (startRoi != null) {
				if (startRoi instanceof ShapeRoi) {
					internalWorkingRoi = (RoiObj) ((ShapeRoi) startRoi).clone(); 
				} else {
					internalWorkingRoi = new ShapeRoi(startRoi);
				}
			} else {
				internalWorkingRoi = null;
			}
		});

		brushRoi(pressedEvent);
	}

	private void determineModeAndTarget(MouseEvent e) {
		Point mousePoint = e.getPoint();
		RoiObj hitRoi = slide.getRoiLocationAt(mousePoint.x, mousePoint.y);

		CanvasGlass cg = (CanvasGlass) slide.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
		RoiObj activeRoi = null;
		if (cg != null) {
			activeRoi = cg.getCurrentRoi();
			if (activeRoi == null) activeRoi = cg.getSelectedRoi();
		}
		if (activeRoi == null) activeRoi = lastOperatedRoi;

		boolean isShift = e.isShiftDown();
		boolean isAlt = e.isAltDown();

		if (isAlt) {
			mode = SUBTRACT;
			currentBrushingRoi = (hitRoi != null) ? hitRoi : activeRoi;
			isNewRoiMode = false;
		} 
		else if (isShift) {
			mode = ADD;
			currentBrushingRoi = (hitRoi != null) ? hitRoi : activeRoi;
			if (currentBrushingRoi == null) {
				java.util.List<RoiObj> rois = slide.getRois();
				if (rois != null && !rois.isEmpty()) {
					currentBrushingRoi = rois.get(rois.size() - 1);
				}
			}
			isNewRoiMode = (currentBrushingRoi == null); 
		} 
		else {
			if (hitRoi != null) {
				mode = ADD;
				currentBrushingRoi = hitRoi;
				isNewRoiMode = false;
			} else {
				mode = ADD;
				currentBrushingRoi = null;
				isNewRoiMode = true;
			}
		}
		
		// ★ 検証ログ 1: モード判定結果
		com.vis.core.log.Log.logger.info(String.format(
			"[Brush-Debug 1] determineModeAndTarget | Keys(Shift:%b, Alt:%b) | Mode: %s | isNew: %b | Target Found: %b",
			isShift, isAlt, (mode == ADD ? "ADD" : "SUBTRACT"), isNewRoiMode, (currentBrushingRoi != null)
		));
	}

	public void clearCurrentBrushingRoi() {
		currentBrushingRoi = null;
		isNewRoiMode = false;
	}

	public void brushDragged(MouseEvent e) {
		if (brush == null) {
			createBrush(e);
			return;
		}
		updateBrushShape(e.getX(), e.getY());
		brushRoi(e);
	}
	
	private void updateBrushShape(int screenX, int screenY) {
		Point p = null;
		try {
			p = slide.offScreenCoordinate(screenX, screenY);
		} catch (NoninvertibleTransformException nte) {
			return;
		}

		int ox = p.x;
		int oy = p.y;

		String type = getBrushType();
		int size = getBrushSize();
		
		if (type.toLowerCase().equals("circle")) {
			brush = getCircularRoi(ox, oy, size);
		} else {
			brush = getSquareRoi(ox, oy, size);
		}
		
		brush.setActiveOverlayRoi(false);
		slide.setRoiBrush(brush);

		slide.lastDraggedX = screenX;
		slide.lastDraggedY = screenY;
		slide.repaint(); 
	}

	public void brushingEnd() {
		slide.setRoiBrush(null);
		slide.repaint();
		
		if (calcExecutor != null && !calcExecutor.isShutdown()) {
			calcExecutor.submit(() -> {
				final RoiObj roiToSave = internalWorkingRoi;

				SwingUtilities.invokeLater(() -> {
					if (roiToSave != null) {
						if (currentBrushingRoi != null) {
							slide.replaceRoi(currentBrushingRoi.getUIDs(), roiToSave);
						} else {
							slide.addRoi(roiToSave);
						}
						slide.saveRoi(roiToSave);
						
						lastOperatedRoi = roiToSave;
						if (slide != null) {
							slide.saveUndoState();
						}
					}
					clearCurrentBrushingRoi(); 
				});
				internalWorkingRoi = null;
			});
			calcExecutor.shutdown();
			calcExecutor = null;
		}
	}

	public void brushRoi(MouseEvent e) {
		final int targetMode = this.mode;
		final boolean isCreating = this.isNewRoiMode;
		final ShapeRoi brushSnapshot = (ShapeRoi) brush.clone();

		if (calcExecutor != null && !calcExecutor.isShutdown()) {
			calcExecutor.submit(() -> {
				if (targetMode == ADD) {
					processAdd(brushSnapshot, isCreating);
				} else {
					processSubtract(brushSnapshot);
				}
			});

			if (this.isNewRoiMode) {
				this.isNewRoiMode = false;
			}
		}
	}

	private void processAdd(ShapeRoi brushSnapshot, boolean isCreating) {
		RoiObj result;
		
		// ★ 検証ログ 2-1: ADD処理開始
		com.vis.core.log.Log.logger.info(String.format(
			"[Brush-Debug 2-1] processAdd Start | isCreating: %b | BrushBounds: %s",
			isCreating, brushSnapshot.getBounds().toString()
		));

		if (internalWorkingRoi == null) {
			if (isCreating) {
				result = (ShapeRoi) brushSnapshot.clone();
			} else {
				return;
			}
		} else {
			ShapeRoi base = (internalWorkingRoi instanceof ShapeRoi) ? 
							new ShapeRoi(internalWorkingRoi) : new ShapeRoi(internalWorkingRoi);
			java.util.Properties oldProps = internalWorkingRoi.getProperties();

			base.or(brushSnapshot);

			if (oldProps != null) {
				base.props = (java.util.Properties) oldProps.clone();
			}
			base.setSlideGlass(slide, false);
			result = base;
		}
		
		internalWorkingRoi = result;
		
		// ★ 検証ログ 2-2: ADD処理終了
		com.vis.core.log.Log.logger.info(String.format(
			"[Brush-Debug 2-2] processAdd End | ResultBounds: %s | ResultPoints: %d",
			result.getBounds().toString(), result.getFloatPolygon().npoints
		));
		
		updateUiRoi(result, isCreating);
	}

	private void processSubtract(ShapeRoi brushSnapshot) {
		if (internalWorkingRoi == null) return;

		// ★ 検証ログ 3-1: SUBTRACT処理開始
		com.vis.core.log.Log.logger.info(String.format(
			"[Brush-Debug 3-1] processSubtract Start | TargetBounds: %s | BrushBounds: %s",
			internalWorkingRoi.getBounds().toString(), brushSnapshot.getBounds().toString()
		));

		ShapeRoi base = (internalWorkingRoi instanceof ShapeRoi) ? 
						new ShapeRoi(internalWorkingRoi) : new ShapeRoi(internalWorkingRoi);

		base.not(brushSnapshot);

		java.util.Properties oldProps = internalWorkingRoi.getProperties();
		if (oldProps != null) {
			base.props = (java.util.Properties) oldProps.clone();
		}
		base.setSlideGlass(slide, false);

		boolean isEmpty = (base.getContainedFloatPoints().xpoints.length <= 4
				|| (base.width <= 0 && base.height <= 0));

		// ★ 検証ログ 3-2: SUBTRACT処理終了
		com.vis.core.log.Log.logger.info(String.format(
			"[Brush-Debug 3-2] processSubtract End | ResultBounds: %s | ResultPoints: %d | isEmpty: %b",
			base.getBounds().toString(), base.getFloatPolygon().npoints, isEmpty
		));

		if (isEmpty) {
			internalWorkingRoi = null; 
			SwingUtilities.invokeLater(() -> {
				if (currentBrushingRoi != null)
					slide.deleteRoi(currentBrushingRoi);
				currentBrushingRoi = null;
			});
		} else {
			internalWorkingRoi = base; 
			updateUiRoi(base, false);
		}
	}

	private void updateUiRoi(final RoiObj resultRoi, final boolean isFirstCreation) {
		SwingUtilities.invokeLater(() -> {
			if (isFirstCreation && currentBrushingRoi == null) {
				slide.addRoi(resultRoi);
			} else {
				if (currentBrushingRoi != null) {
					slide.replaceRoi(currentBrushingRoi.getUIDs(), resultRoi);
				} else {
					slide.updateRoi(resultRoi);
				}
			}
			currentBrushingRoi = resultRoi;
		});
	}

	ShapeRoi getCircularRoi(int cx, int cy, int size) {
		double x = cx - size / 2.0;
		double y = cy - size / 2.0;
		RoiObj oval = new OvalRoi(x, y, size, size, slide);
		return new ShapeRoi(oval);
	}

	ShapeRoi getSquareRoi(int cx, int cy, int size) {
		double x = cx - size / 2.0;
		double y = cy - size / 2.0;
		RoiObj rect = new RoiObj(x, y, size, size, 0, slide);
		return new ShapeRoi(rect);
	}

	int getBrushSize() {
		String sizeStr = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushSize);
		if (sizeStr == null) return defaultSize;
		return Integer.valueOf(sizeStr.trim());
	}

	String getBrushType() {
		String type = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushType);
		return type == null ? defaultType : type;
	}
}