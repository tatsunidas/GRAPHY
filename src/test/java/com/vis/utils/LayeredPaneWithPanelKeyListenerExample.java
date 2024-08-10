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
import java.awt.event.*;

public class LayeredPaneWithPanelKeyListenerExample {
    public static void main(String[] args) {
        JFrame frame = new JFrame("JLayeredPane with JPanel KeyListener Example");

        // JLayeredPaneの作成
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new java.awt.Dimension(400, 300));

        // JPanelの作成
        JPanel panel = new JPanel();
        panel.setBounds(50, 50, 300, 200);
        panel.setBackground(java.awt.Color.LIGHT_GRAY);

        // JPanelにKeyListenerを追加
        panel.addKeyListener(new KeyListener() {
            private boolean isCtrlPressed = false;
            private boolean isShiftPressed = false;

            @Override
            public void keyPressed(KeyEvent e) {
                // Ctrlキーが押されたかチェック
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    isCtrlPressed = true;
                }

                // Shiftキーが押されたかチェック
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    isShiftPressed = true;
                }

                // →キーが押されたときに、CtrlとShiftが押されているかチェック
                if (e.getKeyCode() == KeyEvent.VK_RIGHT && isCtrlPressed && isShiftPressed) {
                    panel.setBackground(java.awt.Color.GREEN);
                    System.out.println("Ctrl + Shift + →キーが押されました！");
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // Ctrlキーが離されたかチェック
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    isCtrlPressed = false;
                }

                // Shiftキーが離されたかチェック
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    isShiftPressed = false;
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                // ここでは何もしない
            }
        });

        // JPanelをJLayeredPaneに追加
        layeredPane.add(panel, JLayeredPane.DEFAULT_LAYER);

        // フレームにJLayeredPaneを追加
        frame.add(layeredPane);

        // フレームの設定
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        // JPanelにフォーカスを設定
        panel.setFocusable(true);
        panel.requestFocusInWindow();
    }
}
