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
package com.vis.core.view.D2.ui.glasses;

import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.HashMap;

import javax.swing.JLayer;
import javax.swing.JPanel;

public class SlideGlassGrid extends JPanel{
	
	private HashMap<Integer, SlideGlass> slides;
	private int rows;
	private int cols;
	
	public SlideGlassGrid(HashMap<Integer, SlideGlass> slides, int rows, int cols) {
		this.slides = slides;
		this.rows = rows;
		this.cols = cols;
		
		setLayout(new GridLayout(rows, cols, 1/*hgap*/, 1/*vgap*/));
		
	}
	
	@Override
	protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int cellSize = Math.min(panelWidth / cols, panelHeight / rows);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index < slides.size()) {
                    int x = col * cellSize;
                    int y = row * cellSize;
//                    Image scaledImage = images[index].getScaledInstance(cellSize, cellSize, Image.SCALE_SMOOTH);
//                    g.drawImage(scaledImage, x, y, cellSize, cellSize, this);
                }
            }
        }
    }
	
}
