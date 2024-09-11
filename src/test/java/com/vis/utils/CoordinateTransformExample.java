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
import java.awt.geom.*;
import javax.swing.*;

public class CoordinateTransformExample extends JPanel {
    private AffineTransform screenToOffscreen;
    private AffineTransform offscreenToScreen;

    public CoordinateTransformExample() {
        // 拡大縮小とパンニングのためのアフィン変換
        screenToOffscreen = new AffineTransform();
        screenToOffscreen.scale(2.0, 2.0); // 拡大
        screenToOffscreen.translate(100, 50); // パンニング

        try {
            // オフスクリーン座標からスクリーン座標への逆変換
            offscreenToScreen = screenToOffscreen.createInverse();
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // 現在のトランスフォーム状態を保存
        AffineTransform originalTransform = g2d.getTransform();

        // オフスクリーン座標系で描画
        g2d.setTransform(screenToOffscreen);

        // オフスクリーン座標での図形の描画（拡大・パンされた状態）
        Shape rect = new Rectangle2D.Double(50, 50, 100, 100);
        g2d.setColor(Color.BLUE);
        g2d.fill(rect);

        // Strokeの影響を戻すために元のトランスフォームに戻す
        g2d.setTransform(originalTransform);

        // 線の太さを維持するための描画
        g2d.setStroke(new BasicStroke(2)); // 固定の太さのストローク
        g2d.setColor(Color.BLACK);
        g2d.draw(rect);

        // 固定サイズのハンドルを描画
        g2d.fill(new Ellipse2D.Double(150, 150, 10, 10));
    }

    // スクリーン座標をオフスクリーン座標に変換
    public Point2D screenToOffscreen(Point2D p) {
        return screenToOffscreen.transform(p, null);
    }

    // オフスクリーン座標をスクリーン座標に変換
    public Point2D offscreenToScreen(Point2D p) {
        return offscreenToScreen.transform(p, null);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Coordinate Transform Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new CoordinateTransformExample());
        frame.setSize(400, 400);
        frame.setVisible(true);
    }
}