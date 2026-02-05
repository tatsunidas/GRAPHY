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
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.NoninvertibleTransformException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.ContextKey;
import com.vis.configuration.GraphyProp;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.view.D2.ui.glasses.*;

public class RoiBrush {
	final static int ADD = 0, SUBTRACT = 1;
	private int mode = ADD;

	// 新規作成モードかどうかのフラグ
	private boolean isNewRoiMode = false;

	int defaultSize = 15;
	String defaultType = "Circle";

	SlideGlass slide = null;
	ShapeRoi brush = null;

	// UIスレッド用の表示用ROI
	volatile RoiObj currentBrushingRoi = null;
	
	private RoiObj lastOperatedRoi = null;

	// 【重要】バックグラウンドスレッド専用の「計算途中の最新ROI」
	// ここに計算結果を累積させることで、UI反映が遅れても計算の整合性を保つ
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

		// Executorの初期化
		if (calcExecutor == null || calcExecutor.isShutdown()) {
			calcExecutor = Executors.newSingleThreadExecutor();
		}

		int slideX = pressedEvent.getX();
		int slideY = pressedEvent.getY();
		int size = getBrushSize();

		Point p = null;
		try {
			p = slide.offScreenCoordinate(slideX, slideY);
		} catch (NoninvertibleTransformException nte) {
			nte.printStackTrace();
			return;
		}

		int ox = p.x;
		int oy = p.y;

		String type = getBrushType();
		if (type.toLowerCase().equals("circle")) {
			brush = getCircularRoi(ox, oy, size);
		} else {
			brush = getSquareRoi(ox, oy, size);
		}
		brush.setActiveOverlayRoi(false);
		slide.setRoiBrush(brush);
		slide.repaint();

		// モードとターゲットの初期判定
		determineModeAndTarget(pressedEvent/* slide coords */, brush);

		// 【重要】計算用ROIの初期化タスクを投入
		// UI上のROIをクローンして、スレッド内の管理下に置く
		final RoiObj startRoi = currentBrushingRoi;
		calcExecutor.submit(() -> {
			if (startRoi != null) {
				internalWorkingRoi = (RoiObj) ((ShapeRoi) startRoi).clone(); // 安全のため複製
			} else {
				internalWorkingRoi = null;
			}
		});

		brushRoi(pressedEvent);
	}
	
	/**
     * 現在選択されている（アクティブな）ROIを探して返すヘルパー
     */
    private RoiObj getSelectedRoi() {
        if (slide == null) return null;
        for (RoiObj roi : slide.getRois()) {
            if (roi.isVisible() && roi.isActiveOverlayRoi() || roi.isSelected()) {
                return roi;
            }
        }
        return null;
    }

	/**
     * クリック時のモード判定
     * 位置による自動判定よりも、明示的なキー操作(Shift/Alt)を優先する
     */
    private void determineModeAndTarget(MouseEvent e, ShapeRoi initialBrush) {
        Point mousePoint = e.getPoint();
        
        // 1. 物理的なターゲット判定
        RoiObj hitRoi = slide.getRoiLocationAt(mousePoint.x, mousePoint.y);
        RoiObj overlappedRoi = (hitRoi == null) ? findOverlappingRoi(initialBrush) : null;
        RoiObj targetRoi = (hitRoi != null) ? hitRoi : overlappedRoi;

        // 2. キー入力
        boolean isShift = (e.getModifiersEx() & java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0;
        boolean isAlt = (e.getModifiersEx() & java.awt.event.InputEvent.ALT_DOWN_MASK) != 0;

        // 3. モード決定
        if (isAlt) {
            // --- Alt: 削除 ---
            mode = SUBTRACT;
            
            // 【修正箇所】 
            // 物理ターゲットがない場合、「直前に操作したROI」を削除対象として採用する
            // これにより、ROIの外側からドラッグして削り込む操作が可能になる
            if (targetRoi == null) {
                if (lastOperatedRoi != null && slide.getRois().contains(lastOperatedRoi)) {
                    targetRoi = lastOperatedRoi;
                }
            }

            currentBrushingRoi = targetRoi; 
            isNewRoiMode = false;
        } 
        else if (isShift) {
            // --- Shift: 拡張/統合 ---
            mode = ADD;
            
            // Shiftと同様のフォールバック
            if (targetRoi == null) {
                if (lastOperatedRoi != null && slide.getRois().contains(lastOperatedRoi)) {
                    targetRoi = lastOperatedRoi;
                }
            }

            currentBrushingRoi = targetRoi;
            isNewRoiMode = (targetRoi == null); 
        } 
        else {
            // --- キーなし (自動判定) ---
            // 変更なし
            if (hitRoi != null) {
                mode = ADD;
                currentBrushingRoi = hitRoi;
                isNewRoiMode = false;
            } else if (overlappedRoi != null) {
                mode = SUBTRACT;
                currentBrushingRoi = overlappedRoi;
                isNewRoiMode = false;
            } else {
                mode = ADD;
                currentBrushingRoi = null;
                isNewRoiMode = true;
            }
        }
    }

	private RoiObj findOverlappingRoi(ShapeRoi brushShape) {
		Rectangle brushBounds = brushShape.getBounds();
		List<RoiObj> rois = slide.getRois();
		synchronized (rois) {
			for (RoiObj roi : rois) {
				if (!roi.isVisible())
					continue;
				if (roi.getBounds().intersects(brushBounds)) {
					// 簡易判定としてBoundsチェックのみ採用（高速化）
					// 必要であればAreaクラスでの精密判定を入れてください
					return roi;
				}
			}
			return null;
		}
	}

	public void clearCurrentBrushingRoi() {
		currentBrushingRoi = null;
		isNewRoiMode = false;
		// workingRoiはスレッド内でnull制御されるためここでは触らない
	}

	public void brushDragged(MouseEvent e) {
		if (brush == null) {
			createBrush(e);
			return;
		}

		// ブラシ位置更新
		int sx = e.getX();
		int sy = e.getY();
		int RoiOffset = getBrushSize() / 2;

		Point p = null;
		try {
			p = slide.offScreenCoordinate(sx, sy);
		} catch (NoninvertibleTransformException nte) {
			return;
		}

		int ox = p.x;
		int oy = p.y;
		int xNew = ox - RoiOffset;
		int yNew = oy - RoiOffset;
		int dx = xNew - brush.startX;
		int dy = yNew - brush.startY;

		brush.x += dx;
		brush.y += dy;
		brush.oldX = brush.x;
		brush.oldY = brush.y;
		brush.startX = xNew;
		brush.startY = yNew;
		slide.lastDraggedX = sx;
		slide.lastDraggedY = sy;

		slide.repaint(); // ブラシの移動のみ即座に描画

		brushRoi(e);
	}

	public void brushingEnd() {
        if (calcExecutor != null && !calcExecutor.isShutdown()) {
            calcExecutor.submit(() -> {
                final RoiObj roiToSave = internalWorkingRoi;
                
                SwingUtilities.invokeLater(() -> {
                    if (roiToSave != null) {
                        if(currentBrushingRoi != null) {
                             slide.updateRoi(roiToSave); 
                        } else {
                             slide.addRoi(roiToSave);
                        }
                        slide.saveRoi(roiToSave);
                        
                        // ★追加: 次回のShift操作のために、確定したROIを記憶しておく
                        lastOperatedRoi = roiToSave;
                    }
                    
                    clearCurrentBrushingRoi(); // ここでcurrentBrushingRoiはnullになる
                    slide.setRoiBrush(null);
                    slide.repaint();
                });
                
                internalWorkingRoi = null;
            });
            calcExecutor.shutdown();
            calcExecutor = null;
        }
    }

	public void brushRoi(MouseEvent e) {
		final int targetMode = this.mode;
		// 現在のモードが新規作成かどうか
		final boolean isCreating = this.isNewRoiMode;

		// 計算スレッドに渡すために、現在のブラシ形状を複製
		final ShapeRoi brushSnapshot = (ShapeRoi) brush.clone();

		if (calcExecutor != null && !calcExecutor.isShutdown()) {
			calcExecutor.submit(() -> {
				// --- バックグラウンドスレッド ---

				// ここでは currentBrushingRoi ではなく internalWorkingRoi を使う
				// これにより、前回の計算結果に対し、今回のブラシ分を適用できる

				if (targetMode == ADD) {
					processAdd(brushSnapshot, isCreating);
				} else {
					processSubtract(brushSnapshot);
				}
			});

			// 新規作成モードは最初の1回で終わりなので、フラグを落とす
			// (これをUIスレッド側で即座にやっておかないと、次のイベントも新規作成しようとしてしまう)
			if (this.isNewRoiMode) {
				this.isNewRoiMode = false;
			}
		}
	}

	// スレッド内処理: ADD (Expand or New)
	private void processAdd(ShapeRoi brushSnapshot, boolean isCreating) {
		RoiObj result;

		if (internalWorkingRoi == null) {
			if (isCreating) {
				// まだROIがない＆新規作成モード -> ブラシそのものをROIにする
				result = (ShapeRoi) brushSnapshot.clone();
			} else {
				// 異常系（あるはずのROIがない）
				return;
			}
		} else {
			// 既存ROIとの結合
			ShapeRoi base = new ShapeRoi(internalWorkingRoi);
			// IDなどのプロパティを引き継ぐ
			String roiId = internalWorkingRoi.getProperty(ContextKey.RoiID.name());

			// ★計算実行★
			base = base.or(brushSnapshot);

			if (roiId != null)
				base.setProperty(ContextKey.RoiID.name(), roiId);
			result = base;
		}

		// 次の計算のために内部キャッシュを更新
		internalWorkingRoi = result;

		// UI反映
		updateUiRoi(result, isCreating);
	}

	// スレッド内処理: SUBTRACT
	private void processSubtract(ShapeRoi brushSnapshot) {
		if (internalWorkingRoi == null)
			return;

		if (!(internalWorkingRoi instanceof ShapeRoi)) {
			// ShapeRoiへの変換が必要な場合
			internalWorkingRoi = new ShapeRoi(internalWorkingRoi);
		}

		// ★計算実行★
		ShapeRoi result = ((ShapeRoi) internalWorkingRoi).not(brushSnapshot);

		// 消滅判定
		boolean isEmpty = (result.getContainedFloatPoints().xpoints.length <= 4
				|| (result.width <= 0 && result.height <= 0));

		if (isEmpty) {
			internalWorkingRoi = null; // なくなった
			SwingUtilities.invokeLater(() -> {
				if (currentBrushingRoi != null)
					slide.deleteRoi(currentBrushingRoi);
				currentBrushingRoi = null;
			});
		} else {
			internalWorkingRoi = result; // 次のために更新
			updateUiRoi(result, false);
		}
	}

	// UIへの反映ヘルパー
	private void updateUiRoi(final RoiObj resultRoi, final boolean isFirstCreation) {
		// 結果の複製を渡す必要はない（resultRoiはこの時点で他から触られない）が、念のため
		SwingUtilities.invokeLater(() -> {
			if (isFirstCreation && currentBrushingRoi == null) {
				slide.addRoi(resultRoi);
			} else {
				// 既に削除されていたらエラーになるのを防ぐチェックを入れても良い
				slide.updateRoi(resultRoi);
			}
			// 描画用の参照も更新
			currentBrushingRoi = resultRoi;
		});
	}

	// ... getCircularRoi, getSquareRoi, getBrushSize 等は省略 ...
	ShapeRoi getCircularRoi(int x, int y, int width) {
		double cx = x - (int) Math.floor(width / 2);
		double cy = y - (int) Math.floor(width / 2);
		RoiObj roi = new OvalRoi(cx, cy, width, width, slide);
		Polygon poly = roi.getPolygon();
		return new ShapeRoi(poly, slide);
	}

	ShapeRoi getSquareRoi(int x, int y, int width) {
		RoiObj roi = new RoiObj(x - (int) Math.floor(width / 2), y - (int) Math.floor(width / 2), width, width, 0,
				slide);
		return new ShapeRoi(roi);
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