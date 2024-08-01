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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;


public class ImageOverlayWithJLayeredPane extends JPanel implements KeyListener {
    private BufferedImage image;
    private JPanel overlayPanel;

    public ImageOverlayWithJLayeredPane(String imagePath) {
        setLayout(new BorderLayout());

        // 画像を読み込む
        try {
            image = ImageIO.read(new File(imagePath));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        // 画像を表示するパネルを作成
        JPanel imagePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(image, 0, 0, this);
            }
        };
        imagePanel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));

        // オーバーレイを表示するパネルを作成
        overlayPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(255, 0, 0, 128));  // 半透明の赤
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        overlayPanel.setOpaque(false);

        // JLayeredPaneを作成し、画像パネルとオーバーレイパネルを追加
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        
        imagePanel.setBounds(0, 0, image.getWidth(), image.getHeight());
        overlayPanel.setBounds(0, 0, image.getWidth(), image.getHeight());
        
        layeredPane.add(imagePanel, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(overlayPanel, JLayeredPane.PALETTE_LAYER);

        add(layeredPane, BorderLayout.CENTER);

        // キーリスナーを追加
        addKeyListener(this);
        setFocusable(true);
        requestFocusInWindow();
    }

    // KeyListenerのメソッドを実装
    @Override
    public void keyTyped(KeyEvent e) {
        System.out.println("Key Typed: " + e.getKeyChar());
    }

    @Override
    public void keyPressed(KeyEvent e) {
        System.out.println("Key Pressed: " + e.getKeyCode());
    }

    @Override
    public void keyReleased(KeyEvent e) {
        System.out.println("Key Released: " + e.getKeyCode());
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Image with Overlay and Key Listener using JLayeredPane");
        ImageOverlayWithJLayeredPane panel = new ImageOverlayWithJLayeredPane("/home/tatsunidas/ピクチャ/Screenshot 2023-09-28 17:15:09.png");
        
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
