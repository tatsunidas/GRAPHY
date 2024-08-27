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
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import ij.gui.ShapeRoi;

public class ShapeRoiDragExample extends JPanel {
    private ij.gui.ShapeRoi shapeRoi;
    private int offsetX, offsetY;

    public ShapeRoiDragExample() {
        // ShapeRoiを定義 (例として四角形)
        shapeRoi = new ij.gui.ShapeRoi(new Rectangle(50, 50, 100, 100));
        
        // JPanelのマウスリスナーを設定
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (shapeRoi.contains(e.getX(), e.getY())) {
                    offsetX = e.getX() - shapeRoi.getBounds().x;
                    offsetY = e.getY() - shapeRoi.getBounds().y;
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (shapeRoi.contains(e.getX() - offsetX, e.getY() - offsetY)) {
                    Rectangle bounds = shapeRoi.getBounds();
                    shapeRoi = new ShapeRoi(new Rectangle(e.getX() - offsetX, e.getY() - offsetY, bounds.width, bounds.height));
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.draw(shapeRoi.getShape());
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("ShapeRoi Drag Example");
        ShapeRoiDragExample panel = new ShapeRoiDragExample();
        frame.add(panel);
        frame.setSize(400, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}