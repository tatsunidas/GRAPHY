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
 * ***** END LICENSE BLOCK *****
 */

package com.vis.core.view.D2.roi;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.NoninvertibleTransformException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.view.D2.ui.glasses.*;
import com.vis.core.view.D3.roi.FreeFormRoi3D;
import com.vis.core.view.D3.roi.SphereRoi3D;

/**
 * @author tatsunidas
 */
public class RoiBrush {
	final static int ADD = 0, SUBTRACT = 1;
	private int mode = ADD;

	private boolean isNewRoiMode = false;
	private boolean brushCreationAborted = false; // ★ 追加: エラー時のドラッグ抑止フラグ

	int defaultSize = 15;
	String defaultType = "Circle";

	SlideGlass slide = null;
	ShapeRoi brush = null;

	volatile RoiObj currentBrushingRoi = null;

	@SuppressWarnings("unused")
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
		if (slide == null)
			return;
		brushCreationAborted = false;

		// 複数選択などのエラー時は中断
		if (!determineModeAndTarget(pressedEvent)) {
			brushCreationAborted = true;
			return;
		}

		if (calcExecutor == null || calcExecutor.isShutdown()) {
			calcExecutor = Executors.newSingleThreadExecutor();
		}

		updateBrushShape(pressedEvent.getX(), pressedEvent.getY());

		final RoiObj startRoi = currentBrushingRoi;
		calcExecutor.submit(() -> {
			if (startRoi != null) {
				// ==========================================================
				// ★ 3D-ROI のクローンとコンバート処理
				// ==========================================================
				if (startRoi instanceof FreeFormRoi3D) {
					internalWorkingRoi = (RoiObj) startRoi.clone();
				} else if (startRoi instanceof SphereRoi3D) {
					String groupId = startRoi.getProperty(com.vis.configuration.RoiDBKey.RoiGroup.name());
					if (groupId == null)
						groupId = String.valueOf((int) (System.currentTimeMillis() % 1000000000L));
					internalWorkingRoi = FreeFormRoi3D.createFromSphere(slide.getPraparat(), (SphereRoi3D) startRoi,
							groupId);
					if (internalWorkingRoi != null) {
						// DBで上書き保存できるように元のRoiIDを引き継ぐ
						internalWorkingRoi.setProperty(com.vis.configuration.RoiDBKey.RoiID.name(),
								startRoi.getProperty(com.vis.configuration.RoiDBKey.RoiID.name()));
					}
				} else if (startRoi instanceof ShapeRoi) {
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

	private boolean determineModeAndTarget(MouseEvent e) {
		RoiObj targetRoi = null;
		int selectedCount = 0; // ★ 追加: 選択されているROIの数をカウント

		// ==========================================================
		// 1. ターゲットのロックオン：選択状態のROIを探す
		// ==========================================================

		// 1-1. Praparatの3D-ROIリストから探す
		if (slide.getPraparat() != null && slide.getPraparat().getRoi3DList() != null) {
			for (RoiObj r3d : slide.getPraparat().getRoi3DList()) {
				if (r3d.isSelected()) {
					selectedCount++;
					targetRoi = r3d;
					// 3D-ROIの場合、現在のスライス平面での編集コンテキストを注入
					r3d.setSlideGlass(slide, false);
				}
			}
		}

		// 1-2. 現在の2Dスライスから探す
		for (RoiObj r2d : slide.getRois()) {
			if (r2d.isSelected()) {
				selectedCount++;
				if (targetRoi == null)
					targetRoi = r2d;
			}
		}

		// ==========================================================
		// ★ 複数選択の警告ポップアップ
		// ==========================================================
		if (selectedCount > 1) {
			PopUpMessage.showDialog(SwingUtilities.getWindowAncestor(slide), "Multiple ROIs Selected",
					"Multiple ROIs are currently selected.\nPlease select only ONE ROI to edit with the brush.",
					JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
			return false; // 中断フラグを返す
		}

		// ==========================================================
		// 2. モードと新規作成判定
		// ==========================================================
		/*
		 * shiftキーは、Roi選択切り替えと競合するので廃止
		 */
		boolean isShift = e.isShiftDown();
		if (isShift) {
			return false;
		}

		boolean isAlt = e.isAltDown();

		if (targetRoi == null) {
			currentBrushingRoi = null;
			// ★ 修正: 対象がない状態でAlt(削除)が押された場合は、新規作成せずに空振りのSUBTRACTにする
			if (isAlt) {
				isNewRoiMode = false;
				mode = SUBTRACT;
			} else {
				isNewRoiMode = true;
				mode = ADD;
			}
		} else {
			// ターゲットをロックオン
			isNewRoiMode = false;
			currentBrushingRoi = targetRoi;

			if (isAlt) {
				mode = SUBTRACT;
			} else if (isShift) {
				mode = ADD;
			} else {
				mode = ADD; // デフォルト
			}
		}

		com.vis.core.log.Log.logger.info(String.format(
				"[Brush-Debug 1] Target Lock-On | Keys(Shift:%b, Alt:%b) | Mode: %s | isNew: %b | Target Found: %b (Type: %s)",
				isShift, isAlt, (mode == ADD ? "ADD" : "SUBTRACT"), isNewRoiMode, (currentBrushingRoi != null),
				(currentBrushingRoi != null ? currentBrushingRoi.getClass().getSimpleName() : "None")));

		return true; // 正常続行
	}

	public void clearCurrentBrushingRoi() {
		currentBrushingRoi = null;
		isNewRoiMode = false;
	}

	public void brushDragged(MouseEvent e) {
		// ★ エラー時はドラッグ処理をスキップ
		if (brushCreationAborted)
			return;

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
		// ★ エラー時はクリーンアップのみでスキップ
		if (brushCreationAborted) {
			brushCreationAborted = false;
			slide.setRoiBrush(null);
			slide.repaint();
			return;
		}

		slide.setRoiBrush(null);
		slide.repaint();

		if (calcExecutor != null && !calcExecutor.isShutdown()) {
			calcExecutor.submit(() -> {
				final RoiObj roiToSave = internalWorkingRoi;

				SwingUtilities.invokeLater(() -> {
					if (roiToSave != null) {
						// ==========================================================
						// ★ 3D-ROI の保存・反映処理
						// ==========================================================
						if (roiToSave instanceof FreeFormRoi3D) {
							Praparat pp = slide.getPraparat();
							if (currentBrushingRoi != null && currentBrushingRoi != roiToSave) {
								pp.removeRoi3D(currentBrushingRoi);
							}
							if (!pp.getRoi3DList().contains(roiToSave)) {
								pp.addRoi3D(roiToSave);
							}

							com.vis.db.DatabaseHandler db = com.vis.db.DatabaseHandler.getInstance();
							if (db != null) {
								db.insertRoi(roiToSave.readContext());
							}

							lastOperatedRoi = roiToSave;
							roiToSave.setSelectedState(true);
							// 全スライスの残像を消すために再描画
							for (SlideGlass sg : pp.getAllSlides().values()) {
								if (sg != null)
									sg.repaintCanvasGlass();
							}
						} else {
							// 従来の 2D ROI の保存
							if (currentBrushingRoi != null) {
								slide.replaceRoi(currentBrushingRoi.getUIDs(), roiToSave);
							} else {
								slide.addRoi(roiToSave);
							}
							slide.saveRoi(roiToSave);

							lastOperatedRoi = roiToSave;
							roiToSave.setSelectedState(true);
							if (slide != null) {
								slide.saveUndoState();
							}
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

		// ★ 3D編集インタフェースへのルーティング (true = Add)
		if (internalWorkingRoi instanceof com.vis.core.view.D3.roi.Editable3D) {
			((com.vis.core.view.D3.roi.Editable3D) internalWorkingRoi).editWithBrush(brushSnapshot, true);
			updateUiRoi(internalWorkingRoi, false);
			return;
		}

		com.vis.core.log.Log.logger
				.info(String.format("[Brush-Debug 2-1] processAdd Start | isCreating: %b | BrushBounds: %s", isCreating,
						brushSnapshot.getBounds().toString()));

		if (internalWorkingRoi == null) {
			if (isCreating) {
				result = (ShapeRoi) brushSnapshot.clone();
			} else {
				return;
			}
		} else {
			ShapeRoi base = (internalWorkingRoi instanceof ShapeRoi) ? new ShapeRoi(internalWorkingRoi)
					: new ShapeRoi(internalWorkingRoi);
			java.util.Properties oldProps = internalWorkingRoi.getProperties();

			base.or(brushSnapshot);

			if (oldProps != null) {
				base.props = (java.util.Properties) oldProps.clone();
			}
			base.setSlideGlass(slide, false);
			result = base;
		}

		internalWorkingRoi = result;

		com.vis.core.log.Log.logger
				.info(String.format("[Brush-Debug 2-2] processAdd End | ResultBounds: %s | ResultPoints: %d",
						result.getBounds().toString(), result.getFloatPolygon().npoints));

		updateUiRoi(result, isCreating);
	}

	private void processSubtract(ShapeRoi brushSnapshot) {
		if (internalWorkingRoi == null)
			return;

		// ★ 3D編集インタフェースへのルーティング (false = Subtract)
		if (internalWorkingRoi instanceof com.vis.core.view.D3.roi.Editable3D) {
			((com.vis.core.view.D3.roi.Editable3D) internalWorkingRoi).editWithBrush(brushSnapshot, false);
			updateUiRoi(internalWorkingRoi, false);
			return;
		}

		com.vis.core.log.Log.logger
				.info(String.format("[Brush-Debug 3-1] processSubtract Start | TargetBounds: %s | BrushBounds: %s",
						internalWorkingRoi.getBounds().toString(), brushSnapshot.getBounds().toString()));

		ShapeRoi base = (internalWorkingRoi instanceof ShapeRoi) ? new ShapeRoi(internalWorkingRoi)
				: new ShapeRoi(internalWorkingRoi);

		base.not(brushSnapshot);

		java.util.Properties oldProps = internalWorkingRoi.getProperties();
		if (oldProps != null) {
			base.props = (java.util.Properties) oldProps.clone();
		}
		base.setSlideGlass(slide, false);

		boolean isEmpty = (base.getContainedFloatPoints().xpoints.length <= 4 || (base.width <= 0 && base.height <= 0));

		com.vis.core.log.Log.logger.info(String.format(
				"[Brush-Debug 3-2] processSubtract End | ResultBounds: %s | ResultPoints: %d | isEmpty: %b",
				base.getBounds().toString(), base.getFloatPolygon().npoints, isEmpty));

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
			// ==========================================================
			// ★ UIへのリアルタイム反映（3Dと2Dの分岐）
			// ==========================================================
			if (resultRoi instanceof FreeFormRoi3D) {
				Praparat pp = slide.getPraparat();
				if (currentBrushingRoi != null && currentBrushingRoi != resultRoi) {
					pp.removeRoi3D(currentBrushingRoi);
				}
				if (!pp.getRoi3DList().contains(resultRoi)) {
					pp.addRoi3D(resultRoi);
				}
				currentBrushingRoi = resultRoi;
				currentBrushingRoi.setSelectedState(true);
				// スライス平面の編集結果をリアルタイムに見せるための描画更新
				slide.repaintCanvasGlass();
			} else {
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
				currentBrushingRoi.setSelectedState(true);
			}
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
		if (sizeStr == null)
			return defaultSize;
		return Integer.valueOf(sizeStr.trim());
	}

	String getBrushType() {
		String type = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushType);
		return type == null ? defaultType : type;
	}
}