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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;

public class NestedCubeDemo extends JPanel {
	
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    // 親Cubeの初期頂点（中心は原点）
    private double[][] parentCubeVertices = {
        {-100, -100, -100}, {-100, -100,  100}, {-100,  100, -100}, {-100,  100,  100},
        { 100, -100, -100}, { 100, -100,  100}, { 100,  100, -100}, { 100,  100,  100},
    };

    // 子Cubeの初期頂点（親Cubeのローカル座標系）
    private double[][] childCube1Vertices = {
        {-30, -30, -30}, {-30, -30,  30}, {-30,  30, -30}, {-30,  30,  30},
        { 30, -30, -30}, { 30, -30,  30}, { 30,  30, -30}, { 30,  30,  30},
    };
    private double[][] childCube2Vertices = {
        {50, 50, 50}, {50, 50,  80}, {50,  80, 50}, {50,  80,  80},
        { 80, 50, 50}, {80, 50,  80}, {80,  80, 50}, {80,  80,  80},
    };

    // 回転角度
    private double angleX = 0; // X軸周りの回転角
    private double angleY = 0; // Y軸周りの回転角

    // マウスのドラッグ操作で回転角度を更新
    private int lastMouseX;
    private int lastMouseY;

    public NestedCubeDemo() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);

        // マウスイベントで回転角を更新
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;

                angleX += dy * 0.5; // Y方向のドラッグでX軸を回転
                angleY += dx * 0.5; // X方向のドラッグでY軸を回転

                lastMouseX = e.getX();
                lastMouseY = e.getY();

                repaint(); // 再描画
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2));

        // 画面中央を原点に
        AffineTransform transform = g2d.getTransform();
        g2d.translate(WIDTH / 2, HEIGHT / 2);

        // 回転行列を計算
        double[][] rotationX = {
            {1, 0, 0},
            {0, Math.cos(Math.toRadians(angleX)), -Math.sin(Math.toRadians(angleX))},
            {0, Math.sin(Math.toRadians(angleX)),  Math.cos(Math.toRadians(angleX))}
        };
        double[][] rotationY = {
            {Math.cos(Math.toRadians(angleY)), 0, Math.sin(Math.toRadians(angleY))},
            {0, 1, 0},
            {-Math.sin(Math.toRadians(angleY)), 0, Math.cos(Math.toRadians(angleY))}
        };

        // 親Cubeの回転
        double[][] rotatedParentVertices = new double[parentCubeVertices.length][3];
        for (int i = 0; i < parentCubeVertices.length; i++) {
            double[] v = parentCubeVertices[i];
            rotatedParentVertices[i] = multiplyMatrixVector(rotationY, multiplyMatrixVector(rotationX, v));
        }

        // 子Cubeの回転（親の回転行列を適用）
        double[][] rotatedChild1Vertices = rotateChildCube(childCube1Vertices, rotationX, rotationY);
        double[][] rotatedChild2Vertices = rotateChildCube(childCube2Vertices, rotationX, rotationY);

        // 親Cubeのエッジ描画
        drawCube(g2d, rotatedParentVertices);

        // 子Cubeのエッジ描画
        g2d.setColor(Color.CYAN);
        drawCube(g2d, rotatedChild1Vertices);

        g2d.setColor(Color.YELLOW);
        drawCube(g2d, rotatedChild2Vertices);

        // 元の変換に戻す
        g2d.setTransform(transform);
    }

    // 子Cubeの回転計算
    private double[][] rotateChildCube(double[][] childVertices, double[][] rotationX, double[][] rotationY) {
        double[][] rotatedVertices = new double[childVertices.length][3];
        for (int i = 0; i < childVertices.length; i++) {
            double[] v = childVertices[i];
            rotatedVertices[i] = multiplyMatrixVector(rotationY, multiplyMatrixVector(rotationX, v));
        }
        return rotatedVertices;
    }

    // Cubeのエッジを描画
    private void drawCube(Graphics2D g2d, double[][] vertices) {
        int[][] edges = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0}, // 前面
            {4, 5}, {5, 7}, {7, 6}, {6, 4}, // 背面
            {0, 4}, {1, 5}, {2, 6}, {3, 7}  // 接続
        };

        for (int[] edge : edges) {
            int x1 = (int) vertices[edge[0]][0];
            int y1 = (int) vertices[edge[0]][1];
            int x2 = (int) vertices[edge[1]][0];
            int y2 = (int) vertices[edge[1]][1];
            g2d.drawLine(x1, y1, x2, y2);
        }
    }

    // 行列とベクトルの掛け算
    private double[] multiplyMatrixVector(double[][] matrix, double[] vector) {
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = matrix[i][0] * vector[0] + matrix[i][1] * vector[1] + matrix[i][2] * vector[2];
        }
        return result;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Nested Cube Rotation");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new NestedCubeDemo());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
