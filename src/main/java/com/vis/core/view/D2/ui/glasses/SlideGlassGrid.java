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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

import com.vis.core.log.Log;

public class SlideGlassGrid extends JScrollPane {

	private static final long serialVersionUID = 1L;
	private final HashMap<Integer, SlideGlass> slides;
	private int rows = -1;
	private int cols = -1;
	private final int numOfImage;
	private final int defaultCol = 5;
	private final int defaultPanelSize = 128;
	private final int minimumCellSize = 64;
	private int padding = 1;

	private final JPanel view;
	private final boolean useGridLayout;

	Praparat pp;

	public SlideGlassGrid(Praparat pp, int cols, boolean useGridLayout) {
		this.pp = pp;
		this.slides = pp.getAllSlides();

		// カラム数のガード
		if (cols < 0) {
			Log.logger.info("Cols must be larger than 0. Defaulting to " + defaultCol);
			this.cols = defaultCol;
		} else {
			this.cols = cols;
		}

		this.numOfImage = (slides != null) ? slides.size() : 0;
		this.rows = calcRows(this.cols);
		this.useGridLayout = useGridLayout;

		view = new JPanel(useGridLayout ? new GridLayout(rows, this.cols, padding, padding) : null);
		view.setBackground(Color.black);
		setViewportView(view);
		setListeners();
	}

	private int calcRows(int cols) {
		if (numOfImage <= 0)
			return 1;
		// 切り上げ計算: (num + cols - 1) / cols と等価
		return (int) Math.ceil((double) numOfImage / cols);
	}

	private int calcCellSize(int availableWidth) {
		if (availableWidth <= 0)
			return defaultPanelSize;

		// 利用幅から「列間の隙間」と「左右の最低限の隙間」を引く
		// padding * (cols + 1) は、左端+列間+右端 の合計
		int gapTotal = (cols + 1) * padding;
		int space = availableWidth - gapTotal;

		int newW = space / cols;
		return Math.max(newW, minimumCellSize);
	}

	private void setListeners() {
		this.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				updateLayout();
			}
		});
		getVerticalScrollBar().addAdjustmentListener(e -> {
			if (!e.getValueIsAdjusting()) {
				updateVisibleImages();
			}
		});
	}
	
    public void populateView() {
        view.removeAll();
        
        // 足りない分を埋めるEmptyPanelも含めて追加
        int totalCells = rows * cols;
        
        for (int i = 0; i < totalCells; i++) {
            if (i < numOfImage) {
                SlideGlass sg = slides.get(i);
                view.add(sg);
            } else {
                JPanel emptyP = new JPanel();
                emptyP.setBackground(Color.BLACK);
                view.add(emptyP);
            }
        }
    }

	/**
	 * 現在のViewportサイズに合わせてレイアウト（サイズ・余白）を更新する
	 */
	public void updateLayout() {
		int viewportW = getViewport().getWidth();
		// 初期化直後などで幅が取れない場合は親の幅、それもダメならデフォルト
		if (viewportW <= 0)
			viewportW = getWidth();
		if (viewportW <= 0)
			viewportW = defaultPanelSize * cols;

		int viewportH = getViewport().getHeight();
		if (viewportH <= 0)
			viewportH = getHeight();

		// 1. セルサイズの計算
		int cellSize = calcCellSize(viewportW);
		Log.logger.fine("FilmGridCellSize: " + cellSize);

		// 2. コンテンツ（グリッド部分）のサイズ計算
		int contentW = (cellSize * cols) + (padding * (cols + 1));
		int contentH = (cellSize * rows) + (padding * (rows + 1));

		// 3. センタリングのためのオフセット（余白）計算
		// ビューポートよりコンテンツが小さい場合、差分を2で割って余白にする
		int offsetX = Math.max(0, (viewportW - contentW) / 2);
		int offsetY = Math.max(0, (viewportH - contentH) / 2);

		// 4. View全体のサイズ設定
		// コンテンツ幅かビューポート幅、大きい方に合わせる
		int totalW = Math.max(contentW, viewportW);
		int totalH = Math.max(contentH, viewportH);
		Dimension newSize = new Dimension(totalW, totalH);
		view.setPreferredSize(newSize);
		if (!useGridLayout) {
			view.setSize(newSize);
		}

		// 5. レイアウト適用
		if (useGridLayout) {
			// GridLayoutの場合: Borderを使ってパディングとセンタリングを表現
			// 内部gap(padding)と、センタリング用オフセット(offset)を合算
			view.setBorder(new EmptyBorder(padding + offsetY, // Top
					padding + offsetX, // Left
					padding + offsetY, // Bottom
					padding + offsetX // Right
			));

			// GridLayout自体の設定更新
			GridLayout gl = (GridLayout) view.getLayout();
			gl.setHgap(padding);
			gl.setVgap(padding);
			gl.setRows(rows);
			gl.setColumns(cols);

			// GridLayout内のコンポーネントサイズヒント更新
			for (int i = 0; i < view.getComponentCount(); i++) {
				Object c = view.getComponent(i);
				if(c instanceof SlideGlass) {
					SlideGlass sg = (SlideGlass)c;
					sg.setSize(cellSize, cellSize);
				}else {
					//Empty panel
					((Component)c).setPreferredSize(new Dimension(cellSize, cellSize));
				}
			}
		} else {
			// Null Layoutの場合: 手動で座標計算
			// Borderは設定しない（座標計算に含めるため）
			view.setBorder(null);

			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					int idx = r * cols + c;
					if (idx >= view.getComponentCount())
						break;

					int x = (padding + offsetX) + (c * (cellSize + padding));
					int y = (padding + offsetY) + (r * (cellSize + padding));
					Object com = view.getComponent(idx);
					if(com instanceof SlideGlass) {
						SlideGlass sg = (SlideGlass)com;
						//override method
						sg.setSize(cellSize, cellSize);
					}else {
						((Component)com).setBounds(x, y, cellSize, cellSize);
					}
				}
			}
		}

		view.revalidate();
		view.repaint();
	}

	public void update() {
		// 外部から呼ばれる互換用メソッド
		updateLayout();
		updateVisibleImages();
	}

	public void updateVisibleImages() {
		Rectangle viewRect = getViewport().getViewRect();
		int cellSize = calcCellSize(getViewport().getWidth());

		// センタリング用余白（Insets）を取得して補正する
		Insets insets = view.getInsets();
		int topOffset = (insets != null) ? insets.top : 0;

		int unitSize = cellSize + padding;
		if (unitSize <= 0)
			unitSize = 1;

		// ビューポートのY座標から、上部の余白分を引いた位置で計算する
		// これにより、余白がたくさんあっても正しい行が計算される
		int relativeY = viewRect.y - topOffset;

		int startRow = relativeY / unitSize;
		int endRow = (relativeY + viewRect.height) / unitSize;

		startRow = Math.max(0, startRow - 1);
		endRow = Math.min(rows - 1, endRow + 1);

		int firstIndex = startRow * cols;
		int lastIndex = Math.min(numOfImage - 1, (endRow + 1) * cols - 1);

		if (firstIndex < 0)
			firstIndex = 0;
		if (lastIndex < firstIndex)
			lastIndex = firstIndex;

		pp.manageGridCache(firstIndex, lastIndex);
	}
}