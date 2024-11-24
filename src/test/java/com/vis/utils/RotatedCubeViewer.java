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
import java.awt.event.*;
import java.awt.geom.Line2D;

public class RotatedCubeViewer extends JFrame {
    private double[][] cubeVertices = {
        {-100, -100, -100}, {100, -100, -100}, {100, 100, -100}, {-100, 100, -100}, // 前面
        {-100, -100, 100}, {100, -100, 100}, {100, 100, 100}, {-100, 100, 100}      // 背面
    };
    private double angleX = 0, angleY = 0, angleZ;
    private double offsetX = 0, offsetY = 0;
    private boolean isPanning = false;
    private double prevMouseX, prevMouseY;

    private SlicePanel sliceXY, sliceYZ, sliceXZ;

    public RotatedCubeViewer() {
        setTitle("Rotatable and Pannable Cube Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // スライス断面のパネル
        sliceXY = new SlicePanel("XY Slice", projectVerticesXY());
        sliceYZ = new SlicePanel("YZ Slice", projectVerticesYZ());
        sliceXZ = new SlicePanel("XZ Slice", projectVerticesXZ());
        
     // XYスライスの回転のみ適用
        sliceXY.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                prevMouseX = e.getX();
                prevMouseY = e.getY();
                isPanning = !isNearCorner(e.getX(), e.getY());
            }
        });
        sliceXY.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                double deltaX = prevMouseX - e.getX();
                double deltaY = prevMouseY - e.getY();

                if (isPanning) {
                    offsetX += deltaX;
                    offsetY += deltaY;
                } else {
                    angleZ += deltaX * 0.01; // XYスライスではZ軸回転
                    rotateCube(false, false, true); // XYスライスでの回転を許可
                }

                prevMouseX = e.getX();
                prevMouseY = e.getY();

                sliceXY.updateProjection(projectVerticesXY());
                sliceYZ.updateProjection(projectVerticesYZ());
                sliceXZ.updateProjection(projectVerticesXZ());
                
                angleZ = 0d;//reset

                repaint();
            }
        });

        // YZスライスの回転のみ適用
        sliceYZ.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                prevMouseX = e.getX();
                prevMouseY = e.getY();
                isPanning = !isNearCorner(e.getX(), e.getY());
            }
        });
        sliceYZ.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                double deltaX = e.getX() - prevMouseX;
                double deltaY = e.getY() - prevMouseY;

                if (isPanning) {
                    offsetX += deltaX;
                    offsetY += deltaY;
                } else {
                    // YZスライスではY軸とZ軸に沿った回転のみを適用
                    angleX += deltaX * 0.01; // X軸回転
                    rotateCube(true, false, false); // YZスライスでの回転を許可
                }

                prevMouseX = e.getX();
                prevMouseY = e.getY();

                sliceXY.updateProjection(projectVerticesXY());
                sliceYZ.updateProjection(projectVerticesYZ());
                sliceXZ.updateProjection(projectVerticesXZ());
                
                angleX = 0;

                repaint();
            }
        });

        // XZスライスの回転のみ適用
        sliceXZ.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                prevMouseX = e.getX();
                prevMouseY = e.getY();
                isPanning = !isNearCorner(e.getX(), e.getY());
            }
        });
        sliceXZ.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                double deltaX = e.getX() - prevMouseX;
                double deltaY = e.getY() - prevMouseY;

                if (isPanning) {
                    offsetX += deltaX;
                    offsetY += deltaY;
                } else {
                    // XZスライスではX軸とZ軸に沿った回転のみを適用
                    angleX += deltaX * 0.01; // X軸回転
                    angleZ += deltaY * 0.01; // Z軸回転
                    rotateCube(false, false, true); // XZスライスでの回転を許可
                }

                prevMouseX = e.getX();
                prevMouseY = e.getY();

                sliceXY.updateProjection(projectVerticesXY());
                sliceYZ.updateProjection(projectVerticesYZ());
                sliceXZ.updateProjection(projectVerticesXZ());

                repaint();
            }
        });
        

        JPanel panel = new JPanel();
        panel.add(sliceXY);
        panel.add(sliceYZ);
        panel.add(sliceXZ);
        add(panel);

        
    }
    
	private boolean isNearCorner(double mouseX, double mouseY) {
		double threshold = 30.0; // 10ピクセル以内なら四隅とみなす
		// XYスライスの四隅（ここでは立方体のXY投影の頂点を使用）
		double[][] cornersXY = projectVerticesXY();
		// 四隅とマウス位置の距離を計算
		for (double[] corner : cornersXY) {
			double dx = corner[0] - mouseX;
			double dy = corner[1] - mouseY;
			double distance = Math.sqrt(dx * dx + dy * dy);
			// もし距離が閾値以内なら、四隅に近いと判断
			if (distance < threshold) {
				return true;
			}
		}
		return false; // 四隅に近くない
	}

 // 立方体の回転処理を修正し、指定された軸に沿って回転を適用
    private void rotateCube(boolean rotateX, boolean rotateY, boolean rotateZ) {
        double[] center = getCubeCenter();

        for (double[] vertex : cubeVertices) {
            // 頂点を重心に移動
            vertex[0] -= center[0];
            vertex[1] -= center[1];
            vertex[2] -= center[2];

            // X軸回転（XY, XZスライスで適用）
            if (rotateX) {
                double tempY = vertex[1];
                double tempZ = vertex[2];
                vertex[1] = tempY * Math.cos(angleX) - tempZ * Math.sin(angleX);
                vertex[2] = tempY * Math.sin(angleX) + tempZ * Math.cos(angleX);
            }

            // Y軸回転（XY, YZスライスで適用）
            if (rotateY) {
                double tempX = vertex[0];
                double tempZ = vertex[2];
                vertex[0] = tempX * Math.cos(angleY) + tempZ * Math.sin(angleY);
                vertex[2] = -tempX * Math.sin(angleY) + tempZ * Math.cos(angleY);
            }

            // Z軸回転（XZ, YZスライスで適用）
            if (rotateZ) {
                double tempX = vertex[0];
                double tempY = vertex[1];
                vertex[0] = tempX * Math.cos(angleZ) - tempY * Math.sin(angleZ);
                vertex[1] = tempX * Math.sin(angleZ) + tempY * Math.cos(angleZ);
            }

            // 元の位置に戻す
            vertex[0] += center[0];
            vertex[1] += center[1];
            vertex[2] += center[2];
        }
    }

    // 立方体の重心を計算
    private double[] getCubeCenter() {
        double[] center = {0, 0, 0};
        for (double[] vertex : cubeVertices) {
            center[0] += vertex[0];
            center[1] += vertex[1];
            center[2] += vertex[2];
        }
        center[0] /= cubeVertices.length;
        center[1] /= cubeVertices.length;
        center[2] /= cubeVertices.length;
        return center;
    }

    // XYスライスの頂点を投影
    private double[][] projectVerticesXY() {
        double[][] projected = new double[8][2];
        for (int i = 0; i < cubeVertices.length; i++) {
            projected[i][0] = cubeVertices[i][0] + offsetX; // X
            projected[i][1] = cubeVertices[i][1] + offsetY; // Y
        }
        return projected;
    }

    // YZスライスの頂点を投影
    private double[][] projectVerticesYZ() {
        double[][] projected = new double[8][2];
        for (int i = 0; i < cubeVertices.length; i++) {
            projected[i][0] = cubeVertices[i][1] + offsetX; // Y
            projected[i][1] = cubeVertices[i][2] + offsetY; // Z
        }
        return projected;
    }

    // XZスライスの頂点を投影
    private double[][] projectVerticesXZ() {
        double[][] projected = new double[8][2];
        for (int i = 0; i < cubeVertices.length; i++) {
            projected[i][0] = cubeVertices[i][0] + offsetX; // X
            projected[i][1] = cubeVertices[i][2] + offsetY; // Z
        }
        return projected;
    }

    // スライス断面を描画するためのパネル
    private class SlicePanel extends JPanel {
        private String title;
        private double[][] vertices;

        public SlicePanel(String title, double[][] vertices) {
            this.title = title;
            this.vertices = vertices;
            setPreferredSize(new Dimension(250, 250));
         
        }

        public void updateProjection(double[][] newVertices) {
            this.vertices = newVertices;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // タイトルを描画
            g2d.drawString(title, 10, 20);

            // 立方体のエッジを描画
            drawEdges(g2d);
        }

        private void drawEdges(Graphics2D g2d) {
            drawEdge(g2d, vertices[0], vertices[1]); // 前面下辺
            drawEdge(g2d, vertices[1], vertices[2]); // 前面右辺
            drawEdge(g2d, vertices[2], vertices[3]); // 前面上辺
            drawEdge(g2d, vertices[3], vertices[0]); // 前面左辺

            drawEdge(g2d, vertices[4], vertices[5]); // 背面下辺
            drawEdge(g2d, vertices[5], vertices[6]); // 背面右辺
            drawEdge(g2d, vertices[6], vertices[7]); // 背面上辺
            drawEdge(g2d, vertices[7], vertices[4]); // 背面左辺

            drawEdge(g2d, vertices[0], vertices[4]); // 左縦辺
            drawEdge(g2d, vertices[1], vertices[5]); // 右縦辺
            drawEdge(g2d, vertices[2], vertices[6]); // 右縦辺
            drawEdge(g2d, vertices[3], vertices[7]); // 左縦辺
        }

        private void drawEdge(Graphics2D g2d, double[] p1, double[] p2) {
            g2d.draw(new Line2D.Double(p1[0], p1[1], p2[0], p2[1]));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            RotatedCubeViewer viewer = new RotatedCubeViewer();
            viewer.setVisible(true);
        });
    }
}

