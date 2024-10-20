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
package com.vis.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

public class SliceViewer2D extends JFrame {
    private static final int SIZE = 100; // 仮のボリュームデータサイズ
    private int[][][] volumeData; // 3Dデータ

    private SlicePanel axialPanel;    // 横断面ビュー
    private SlicePanel sagittalPanel; // 矢状断面ビュー
    private SlicePanel coronalPanel;  // 冠状断面ビュー

    public SliceViewer2D() {
        setTitle("2D Slice Viewer");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 仮の3次元ボリュームデータを生成 (例として、ランダム値を設定)
        generateDummyVolumeData();

        // 各断面用のビューパネルを作成
        axialPanel = new SlicePanel("Axial (横断面)", getAxialSlice(50));
        sagittalPanel = new SlicePanel("Sagittal (矢状断面)", getSagittalSlice(50));
        coronalPanel = new SlicePanel("Coronal (冠状断面)", getCoronalSlice(50));

        // レイアウトを設定
        setLayout(new BorderLayout());

        // パネルを配置
        JPanel slicePanel = new JPanel();
        slicePanel.setLayout(new GridLayout(1, 3));
        slicePanel.add(axialPanel);
        slicePanel.add(sagittalPanel);
        slicePanel.add(coronalPanel);

        add(slicePanel, BorderLayout.CENTER);

        // スライダを追加 (スライス位置を変更)
        JScrollBar zSlider = new JScrollBar(JScrollBar.HORIZONTAL, 50, 1, 0, SIZE - 1);
        zSlider.addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                int sliceIndex = e.getValue();
                axialPanel.setImage(getAxialSlice(sliceIndex));
                sagittalPanel.setImage(getSagittalSlice(sliceIndex));
                coronalPanel.setImage(getCoronalSlice(sliceIndex));
            }
        });
        add(zSlider, BorderLayout.SOUTH);
    }

    // 仮の3Dボリュームデータを生成 (ランダムな値を使用)
    private void generateDummyVolumeData() {
        volumeData = new int[SIZE][SIZE][SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    volumeData[z][y][x] = (int) (Math.random() * 255); // 0~255のランダム値
                }
            }
        }
    }

    // 横断面 (Axial) スライスの抽出 (z方向)
    private int[][] getAxialSlice(int z) {
        int[][] slice = new int[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                slice[y][x] = volumeData[z][y][x];
            }
        }
        return slice;
    }

    // 矢状断面 (Sagittal) スライスの抽出 (x方向)
    private int[][] getSagittalSlice(int x) {
        int[][] slice = new int[SIZE][SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int y = 0; y < SIZE; y++) {
                slice[y][z] = volumeData[z][y][x];
            }
        }
        return slice;
    }

    // 冠状断面 (Coronal) スライスの抽出 (y方向)
    private int[][] getCoronalSlice(int y) {
        int[][] slice = new int[SIZE][SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                slice[z][x] = volumeData[z][y][x];
            }
        }
        return slice;
    }

    // 2Dスライスの描画パネル
    private class SlicePanel extends JPanel {
        private String title;
        private int[][] slice;

        public SlicePanel(String title, int[][] slice) {
            this.title = title;
            this.slice = slice;
            setPreferredSize(new Dimension(200, 200));
        }

        public void setImage(int[][] slice) {
            this.slice = slice;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (slice != null) {
                int width = getWidth();
                int height = getHeight();
                for (int y = 0; y < SIZE; y++) {
                    for (int x = 0; x < SIZE; x++) {
                        int value = slice[y][x];
                        g.setColor(new Color(value, value, value)); // グレースケール
                        g.fillRect(x * width / SIZE, y * height / SIZE, width / SIZE, height / SIZE);
                    }
                }
            }
            g.setColor(Color.BLACK);
            g.drawString(title, 10, 20);
        }
    }

    public static void main(String[] args) {
        SliceViewer2D viewer = new SliceViewer2D();
        viewer.setVisible(true);
    }
}
