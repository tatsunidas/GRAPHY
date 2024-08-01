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
import javax.swing.plaf.LayerUI;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class ImageWithTextOverlay extends JFrame {
    private BufferedImage image;

    public ImageWithTextOverlay(String imagePath, String overlayText) {
        try {
            image = ImageIO.read(new File(imagePath));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));

        // 画像パネルを作成
        JPanel imagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(image, 0, 0, getWidth(), getHeight(), this);
            }
        };
        imagePanel.setBounds(0, 0, image.getWidth(), image.getHeight());

        // テキストラベルを作成
        JLabel textLabel = new JLabel(overlayText);
        textLabel.setFont(new Font("Arial", Font.BOLD, 24));
        textLabel.setForeground(Color.WHITE);
        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        textLabel.setBounds(0, image.getHeight() - 50, image.getWidth(), 50);

        // 画像パネル用のLayerUIを作成
        LayerUI<JComponent> imageLayerUI = new LayerUI<JComponent>() {
            @Override
            public void paint(Graphics g, JComponent c) {
                super.paint(g, c);
            }

            @Override
            protected void processMouseEvent(MouseEvent e, JLayer<? extends JComponent> l) {
                if (e.getID() == MouseEvent.MOUSE_CLICKED) {
                    JOptionPane.showMessageDialog(null, "画像がクリックされました!");
                }
                super.processMouseEvent(e, l);
            }
        };

        // テキストラベル用のLayerUIを作成
        LayerUI<JComponent> textLayerUI = new LayerUI<JComponent>() {
            @Override
            public void paint(Graphics g, JComponent c) {
                super.paint(g, c);
            }

            @Override
            protected void processMouseEvent(MouseEvent e, JLayer<? extends JComponent> l) {
                if (e.getID() == MouseEvent.MOUSE_CLICKED) {
                    JOptionPane.showMessageDialog(null, "テキストがクリックされました!");
                }
                super.processMouseEvent(e, l);
            }
        };

        // JLayerを作成
        JLayer<JComponent> imageLayer = new JLayer<>(imagePanel, imageLayerUI);
        imageLayer.setBounds(0, 0, image.getWidth(), image.getHeight());
        imageLayer.setOpaque(false);
        imageLayer.setLayerEventMask(AWTEvent.MOUSE_EVENT_MASK);

        JLayer<JComponent> textLayer = new JLayer<>(textLabel, textLayerUI);
        textLayer.setBounds(0, image.getHeight() - 50, image.getWidth(), 50);
        textLayer.setOpaque(false);
        textLayer.setLayerEventMask(AWTEvent.MOUSE_EVENT_MASK);

        // レイヤードパネルに追加
        layeredPane.add(imageLayer, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(textLayer, JLayeredPane.PALETTE_LAYER);

        add(layeredPane);
        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        String imagePath = "/home/tatsunidas/ピクチャ/Screenshot 2023-09-28 17:15:09.png";
        String overlayText = "Sample Text";

        SwingUtilities.invokeLater(() -> new ImageWithTextOverlay(imagePath, overlayText));
    }
}