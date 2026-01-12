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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.vis.core.log.Log;

/**
 * 
 * @author tatsunidas
 *
 */
public class SlideGlassGrid extends JScrollPane {

    private static final long serialVersionUID = 1002582639306789967L;
    private final HashMap<Integer, SlideGlass> slides;
    private int rows = -1;
    private int cols = -1;
    private final int numOfImage;
    private final int defaultCol = 5;
    private final int defaultPanelSize = 200;
    private final int minimumCellSize = 64;
    private int padding = 3;
    private final JPanel view;
    private final boolean useGridLayout;
    
    Praparat pp;

	public SlideGlassGrid(Praparat pp, int cols, boolean useGridLayout) {
		this.slides = pp.getAllSlides();
		if (cols < 0) {
			Log.logger.info("Cols must be larger than 0. it to be set default cols(5).");
			cols = defaultCol;
		}
		this.pp = pp;
		this.cols = cols;
		rows = calcRows(cols);
		numOfImage = slides.size();
		view = new JPanel(useGridLayout ? new GridLayout(rows, cols) : null);
		view.setBackground(Color.black);
		this.useGridLayout = useGridLayout;
		init();
	}

	private int calcRows(int cols) {
		int numOfImage = -1;
		if (this.slides != null) {
			numOfImage = slides.size();
		}
		if (numOfImage <= cols) {
			return 1;
		} else {
			if (numOfImage % cols > 0) {
				return (int) (numOfImage / cols) + 1;
			} else {
				return (int) (numOfImage / cols);
			}
		}
	}

    /**
     * 親の幅ではなく、利用可能なビューポートの幅に基づいて計算します。
     */
    private int calcCellSize(int availableWidth) {
        // 幅が極端に小さい場合（初期化時など）のガード
        if (availableWidth <= 0) return defaultPanelSize;

        int gap_size = (cols + 1) * padding;
        int space = availableWidth - gap_size;
        int newW = space / cols;
        if (newW < minimumCellSize) {
            return minimumCellSize;
        } else {
            return newW;
        }
    }

	private void init() {
		// 初期構築
		constructView(defaultPanelSize);
		setViewportView(view);

		// リサイズイベントの監視を追加
		this.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				// スクロールバーを除いた表示領域の幅を取得
				int width = getViewport().getWidth();
				// 初期化直後などで幅が0の場合は処理しない、または親の幅を使う
				if (width <= 0)
					width = getWidth();

				update(width);
			}
		});

		// スクロールイベントのリスナーを追加
		getVerticalScrollBar().addAdjustmentListener(e -> {
			if (!e.getValueIsAdjusting()) { // スクロールが止まった、または動いている最中
				updateVisibleImages();
			}
		});
	}
	
	/**
     * 現在の表示矩形（Viewport）から、表示されている画像のインデックス範囲を計算し、
     * ロードを指示します。
     */
    public void updateVisibleImages() {
        Rectangle viewRect = getViewport().getViewRect();
        int cellSize = calcCellSize(getViewport().getWidth());
        int totalPadding = padding; 
        
        // 何行目が表示されているかを計算
        int startRow = viewRect.y / (cellSize + totalPadding);
        int endRow = (viewRect.y + viewRect.height) / (cellSize + totalPadding);
        
        // 余裕を持って前後1行分余分にロード対象とする（マージン）
        startRow = Math.max(0, startRow - 1);
        endRow = Math.min(rows - 1, endRow + 1);

        int firstIndex = startRow * cols;
        int lastIndex = Math.min(numOfImage - 1, (endRow + 1) * cols - 1);

        // Praparatに表示範囲を通知
        pp.manageGridCache(firstIndex, lastIndex);
    }

    /**
     * viewの中身を構築するロジックを分離
     */
	private void constructView(int cellSize) {
		view.removeAll(); // 既存のコンポーネントをクリア
		this.rows = calcRows(this.cols);

		if (!useGridLayout) {
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					if (((r * cols) + c) < numOfImage) {
						SlideGlass sg = slides.get((r * cols) + c);
						sg.setSize(cellSize, cellSize);
						sg.setBounds(((c + 1) * padding) + (cellSize * c), ((r + 1) * padding) + (cellSize * r),
								cellSize, cellSize);
						view.add(sg);

					} else {
						JPanel emptyP = new JPanel();
						emptyP.setPreferredSize(new Dimension(cellSize, cellSize));
						emptyP.setBackground(Color.BLACK);
						emptyP.setBounds(((c + 1) * padding) + (cellSize * c), ((r + 1) * padding) + (cellSize * r),
								cellSize, cellSize);
						view.add(emptyP);
					}
				}
			}
		} else {
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					if (((r * cols) + c) < numOfImage) {
						SlideGlass sg = slides.get((r * cols) + c);
						sg.setSize(cellSize, cellSize);
						view.add(sg);
					} else {
						JPanel emptyP = new JPanel();
						emptyP.setPreferredSize(new Dimension(cellSize, cellSize));
						emptyP.setBackground(Color.BLACK);
						view.add(emptyP);
					}
				}
			}
		}

		int viewW = cellSize * cols + (padding * (cols + 1));
		int viewH = cellSize * rows + (padding * (rows + 1));
		view.setPreferredSize(new Dimension(viewW, viewH));

		// !useGridLayout の場合、view自体のサイズ確定に必要
		if (!useGridLayout) {
			view.setSize(new Dimension(viewW, viewH));
		}

		// 初回表示用に一度計算
		SwingUtilities.invokeLater(this::updateVisibleImages);
	}

    public void update(int availableWidth) {
        int cellSize = calcCellSize(availableWidth);
        Log.logger.fine("FilmGridCellSize: " + cellSize);

        // Viewの再構築
        constructView(cellSize);

        // レイアウトの更新を通知
        view.revalidate();
        view.repaint();
    }

}
