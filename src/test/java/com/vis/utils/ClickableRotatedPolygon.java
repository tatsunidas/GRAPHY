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
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;

public class ClickableRotatedPolygon extends JPanel {

    // 描画する元のポリゴン座標（OffScreen座標）
    private final Polygon offScreenPolygon;
    
    // 表示パラメータ
    private double rotateAngleInDegrees = 0.0; // 回転角度
    private double zoomFactor = 1.2;          // ズーム/スケール倍率 (簡単のため固定)
    private final double scaleToFit = 1.7;    // スケール係数 (簡単のため固定)
    private Point dispOrigin = new Point(100, 100); // パンニングオフセット

    // OffScreen座標系におけるポリゴンの中心
    private final double polyCenterX;
    private final double polyCenterY;

    // 変換結果を表示するラベル
    private JLabel coordLabel;

    public ClickableRotatedPolygon() {
        // --- 1. OffScreenポリゴンの定義 (50x50の四角) ---
        offScreenPolygon = new Polygon(
            new int[]{0, 50, 50, 0}, 
            new int[]{0, 0, 50, 50}, 
            4
        );
        
        // ポリゴンの中心座標を計算
        polyCenterX = 25.0; // (50 / 2.0)
        polyCenterY = 25.0; // (50 / 2.0)

        // --- 2. GUIの初期設定 ---
        setLayout(new BorderLayout());
        this.setPreferredSize(new Dimension(400, 300));
        
        coordLabel = new JLabel("クリックして座標を確認...");
        this.add(coordLabel, BorderLayout.SOUTH);

        // --- 3. マウスイベントリスナーの追加 ---
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point panelPoint = e.getPoint();
                try {
                    Point offScreenPoint = panelToOffScreen(panelPoint.x, panelPoint.y);
                    coordLabel.setText(
                        String.format("Panel座標: (%d, %d) → OffScreen座標: (%d, %d)", 
                            panelPoint.x, panelPoint.y, 
                            offScreenPoint.x, offScreenPoint.y)
                    );
                } catch (NoninvertibleTransformException ex) {
                    coordLabel.setText("エラー: 変換行列が非可逆です。");
                    ex.printStackTrace();
                }
            }
        });
        
        // --- 4. 角度調整用スライダーの追加 ---
        JSlider slider = new JSlider(-180, 180, (int) rotateAngleInDegrees);
        slider.setMajorTickSpacing(90);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.addChangeListener(e -> {
            rotateAngleInDegrees = slider.getValue();
            repaint(); // 角度が変わったら再描画
        });
        this.add(slider, BorderLayout.NORTH);
    }
    
    /**
     * Panel座標をOffScreen座標に変換するメソッド（検証コードと同じロジック）
     * @param panelX Panel上のX座標
     * @param panelY Panel上のY座標
     * @return OffScreen座標 (Point型)
     */
    public Point panelToOffScreen(int panelX, int panelY) throws NoninvertibleTransformException {
        
        // スケールとズームの合成倍率
        double s = scaleToFit * zoomFactor;
        
        // 回転角度をラジアンに変換
        double thetaInRadians = Math.toRadians(rotateAngleInDegrees);

        /* 1. 順変換行列 (OffScreen -> Panel) の構築 */
        AffineTransform forwardAt = new AffineTransform();
        
        // A. パンニングオフセットを適用 (最後に適用される)
        forwardAt.translate(dispOrigin.x, dispOrigin.y);
        
        // B. スケーリングを適用
        forwardAt.scale(s, s);
        
        // C. 回転の中心を元に戻す
        forwardAt.translate(polyCenterX, polyCenterY);
        
        // D. 回転を適用
        forwardAt.rotate(thetaInRadians);
        
        // E. 回転の中心を原点(0,0)に移動 (最初に適用される)
        forwardAt.translate(-polyCenterX, -polyCenterY);
        
        /* 2. 逆変換の実行 (Panel -> OffScreen) */
        
        // 逆行列を取得
        AffineTransform inverseAt = forwardAt.createInverse();
        
        // JPanel上の座標 (入力値)
        Point2D.Double panelPoint = new Point2D.Double(panelX, panelY);
        // OffScreen座標 (出力先)
        Point2D.Double offScreenPoint = new Point2D.Double();
        
        // 逆行列を適用して座標を変換
        inverseAt.transform(panelPoint, offScreenPoint);
        
        // 結果をPoint型で返す
        return new Point((int) Math.round(offScreenPoint.getX()), (int) Math.round(offScreenPoint.getY()));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        
        // 順変換行列を取得し、Graphicsに適用
        double s = scaleToFit * zoomFactor;
        double thetaInRadians = Math.toRadians(rotateAngleInDegrees);

        AffineTransform transform = new AffineTransform();
        transform.translate(dispOrigin.x, dispOrigin.y);
        transform.scale(s, s);
        transform.translate(polyCenterX, polyCenterY);
        transform.rotate(thetaInRadians);
        transform.translate(-polyCenterX, -polyCenterY);
        
        // Graphicsに変換を適用
        g2d.setTransform(transform);
        
        // ポリゴンの描画
        g2d.setColor(Color.BLUE);
        g2d.fill(offScreenPolygon);
        
        // 境界線（OffScreen座標 (0,0) の目印として）
        g2d.setColor(Color.RED);
        g2d.drawRect(0, 0, 50, 50); 
        
        // 回転中心（OffScreen座標 (25, 25) の目印として）
        g2d.setColor(Color.GREEN);
        g2d.fillOval((int)polyCenterX - 2, (int)polyCenterY - 2, 4, 4);

        // 変換をリセット (ラベルなどに影響を与えないように)
        g2d.setTransform(new AffineTransform());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Clickable Rotated Polygon Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new ClickableRotatedPolygon());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}