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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.HashMap;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.vis.core.log.Log;

public class SlideGlassGrid extends JScrollPane{

	private static final long serialVersionUID = 1002582639306789967L;
	private final HashMap<Integer, SlideGlass> slides;
	private int rows = -1;
	private int cols = -1;
	private final int numOfImage;
	private final int defaultCol = 5;
	private final int defaultPanelSize = 200;
	private final int minimumCellSize = 64;
	private int padding = 3;
	private final JPanel view;
	private final boolean useGridLayout; 
	
	int prevParentW = 0;
	
	public SlideGlassGrid(Praparat pp, int cols, boolean useGridLayout) {
		this.slides = pp.getAllSlides();
		if(cols < 0) {
			Log.logger.info("Cols must be larger than 0. it to be set default cols(5).");
			cols = defaultCol;
		}
		this.cols = cols;
		rows = calcRows(cols);
		numOfImage = slides.size();
		view = new JPanel(useGridLayout ? new GridLayout(rows, cols): null);
		view.setBackground(Color.black);
		this.useGridLayout = useGridLayout;
		init();
	}
	
	public SlideGlassGrid(HashMap<Integer, SlideGlass> slides, int rows, int cols, boolean useGridLayout) {
		this.slides = slides;
		this.rows = rows;
		this.cols = cols;
		numOfImage = slides.size();
		view = new JPanel(useGridLayout ? new GridLayout(rows, cols): null);
		view.setBackground(Color.black);
		this.useGridLayout = useGridLayout;
		init();
	}
	
	private int calcRows(int cols) {
		int numOfImage = slides.size();
		if(numOfImage <= cols) {
			return 1;
		}else {
			if (numOfImage % cols > 0) {
				return (int) (numOfImage / cols) + 1;
			} else {
				return (int) (numOfImage / cols);
			}
		}
	}
	
	private int calcCellSize(int parentW) {
		int gap_size = (cols+1) * padding;
		int space = parentW - gap_size;
		int newW = space/cols;
		if(newW < minimumCellSize) {
			return minimumCellSize;
		}else {
			return newW;
		}
	}
	
	private void init() {
		int cellSize = defaultPanelSize;
		if (!useGridLayout) {
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					if(((r+1)*(c+1))<=numOfImage) {
						SlideGlass sg = slides.get((r*cols) + c);
						sg.setSize(cellSize, cellSize);
						sg.setBounds(((c + 1) * padding) + (cellSize * c), ((r + 1) * padding) + (cellSize * r),
								cellSize, cellSize);
						view.add(sg);
					}else {
						JPanel emptyP = new JPanel();
						emptyP.setPreferredSize(new Dimension(cellSize, cellSize));
						emptyP.setBackground(Color.BLACK);
						emptyP.setBounds(((c + 1) * padding) + (cellSize * c), ((r + 1) * padding) + (cellSize * r),
								cellSize, cellSize);
						view.add(emptyP);
					}
				}
			}
		} else {
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					if(((r+1)*(c+1))<=numOfImage) {
						SlideGlass sg = slides.get((r*cols) + c);
						sg.setSize(cellSize, cellSize);
						view.add(sg);
					}else {
						JPanel emptyP = new JPanel();
						emptyP.setPreferredSize(new Dimension(cellSize, cellSize));
						emptyP.setBackground(Color.BLACK);
						view.add(emptyP);
					}
				}
			}
		}
		int viewW = cellSize * cols + (padding * (cols + 1));
		int viewH = cellSize * rows + (padding * (rows + 1));
		view.setPreferredSize(new Dimension(viewW, viewH));
		setViewportView(view);
	}
	
	public void update(int parentW) {
		int cellSize = calcCellSize(parentW);
		Log.logger.fine("FilmGridCellSize: "+ cellSize);
		view.removeAll();
		if (!useGridLayout) {
			System.out.println("Yes ! cell size:"+cellSize);
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					if(((r+1)*(c+1))<=numOfImage) {
						SlideGlass sg = slides.get((r*cols) + c);
						sg.setSize(cellSize, cellSize);
						sg.setBounds(((c + 1) * padding) + (cellSize * c), ((r + 1) * padding) + (cellSize * r),
								cellSize, cellSize);
						view.add(sg);
						sg.repaint();
					}else {
						JPanel emptyP = new JPanel();
						emptyP.setPreferredSize(new Dimension(cellSize, cellSize));
						emptyP.setBackground(Color.BLACK);
						emptyP.setBounds(((c + 1) * padding) + (cellSize * c), ((r + 1) * padding) + (cellSize * r),
								cellSize, cellSize);
						view.add(emptyP);
					}
				}
			}
		} else {
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					if(((r+1)*(c+1))<=numOfImage) {
						SlideGlass sg = slides.get((r*cols) + c);
						sg.setSize(cellSize, cellSize);
						view.add(sg);
					}else {
						JPanel emptyP = new JPanel();
						emptyP.setPreferredSize(new Dimension(cellSize, cellSize));
						emptyP.setBackground(Color.BLACK);
						view.add(emptyP);
					}
				}
			}
		}
		int viewW = cellSize * cols + (padding * (cols + 1));
		int viewH = cellSize * rows + (padding * (rows + 1));
		view.setPreferredSize(new Dimension(viewW, viewH));
		view.setBounds(0, 0, viewW, viewH);
		setViewportView(view);
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		int panelWidth = getParent().getWidth();
		int panelHeight = getParent().getHeight();
		if(prevParentW == panelWidth) {
			view.repaint();
			return;
		}
		// set bounds of slideglassgrid to same as pp.viewPanel.
		setBounds(0, 0, panelWidth, panelHeight);
		update(panelWidth);
		view.repaint();
		prevParentW = panelWidth;
	}
}
