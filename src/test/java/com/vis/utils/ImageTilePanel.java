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

public class ImageTilePanel extends JPanel {

    private Image image;

    public ImageTilePanel() {
        setPreferredSize(new Dimension(100, 100)); // パネルのサイズを設定
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
//        if (image != null) {
//            // 画像を中央に表示するための座標を計算
//            int x = (getWidth() - image.getWidth(this)) / 2;
//            int y = (getHeight() - image.getHeight(this)) / 2;
//            g.drawImage(image, x, y, this);
//        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Image Tiles");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            
            JPanel panel = new JPanel(null);

         // パネルを作成して追加
            int row = 3;
            int col =3;
            int panelSize = 100;
            int padding = 5;
            
            int panelW = panelSize*col + (padding*(col+1)); 
            int panelH = panelSize*row + (padding*(row+1)); 

			for (int r = 0; r < row; r++) {
				for (int c = 0; c < col; c++) {
					JPanel tilePanel = new JPanel();
					tilePanel.setPreferredSize(new Dimension(panelSize, panelSize));
					tilePanel.setBounds(((c+1)*padding) + (panelSize *c), ((r+1)*padding) + (panelSize * r), panelSize, panelSize);
					//tilePanel.setBounds(col * (panelSize + padding), row * (panelSize + padding), panelSize, panelSize);
					tilePanel.setBackground(Color.RED);
					panel.add(tilePanel);
				}
			}
			
			panel.setPreferredSize(new Dimension(panelW, panelH));

            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
