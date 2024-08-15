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

package com.vis.core.view.D2.ui;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.util.ImageUtils;
import com.vis.core.view.D2.roi.*;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;

//import com.vis.mpr.MPRViewerWindow;//TODO 20231006
//import com.vis.resource.GraphyIcon;
//import com.vis.ui.context.ApplicationContext;
//import com.vis.viewer2d.roi.RoiObj;
//import com.vis.viewer2d.roi.RoiObjManager;
//import com.vis.viewer2d.ui.eyepiece.Eyepiece;
//import com.vis.viewer2d.ui.eyepiece.Praparat;
//import com.vis.viewer2d.ui.frame.Viewer2DScreen;
//import com.vis.viewer2d.ui.stage.StageDockManager;
//import com.vis.viewer2d.ui.stage.StageView;
//import com.vis.viewer3d.Viewer3DFrame_IJ;//TODO 20231006

/**
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class Viewer2DToolBar extends JToolBar{
	
	/* roi tool ids */
	public final static int RectangleRoi = RoiObj.RECTANGLE;
	public final static int OvalRoi = RoiObj.OVAL;
	public final static int PointRoi = RoiObj.POINT;
	public final static int LineRoi = RoiObj.LINE;
	public final static int PolygonRoi = RoiObj.POLYGON;
	public final static int AngleRoi = RoiObj.ANGLE;
	public final static int TextRoi = RoiObj.TEXT;
	public final static int ArrowRoi = RoiObj.ARROW;
	
	public final static int[] roiTools = new int[] {
			RectangleRoi,
			OvalRoi,
			PointRoi,
			LineRoi,
			PolygonRoi,
			AngleRoi,
			TextRoi,
			ArrowRoi
	};
	
	public final static int Brush = 100;
	public final static int Windowing = 101;
	public final static int Analysis = 102;//RoiObjManager
	public final static int NONE = -1;

	//process features
	JButton resetBtn;
	JButton invertBtn;
	JButton flipLRBtn;
	JButton flipHFBtn;
	JButton removeBtn;
	JButton analysisBtn;
	JButton cropBtn;
	JButton cutBtn;
	//roi features
	ButtonGroup roiGroup = new ButtonGroup();
	JCheckBox windowChk;
	JCheckBox rectangleChk;
	JCheckBox lineChk;
	JCheckBox ovalChk;
	JCheckBox polyChk;
	JCheckBox pointChk;
	JCheckBox arrowChk;
	JCheckBox textChk;
	JCheckBox angleChk;
//	JCheckBox shapeChk;
	JCheckBox brushChk;
	
	int defaultImgIconSize = 48;
	/*
	 * buttons design https://material.io/tools/icons/?style=outline
	 */
//	private ArrayList<String> buttonLabels = new ArrayList<String>();
//	private ArrayList<String> keys = null;
	private int currentTool = Windowing;//default

	public Viewer2DToolBar() {
		loadButtons(initButtonList());
	}
	
	public void loadButtons(HashMap<String, Resources> buttonLabels) {
		removeAll();
		for (String key : buttonLabels.keySet()) {
			BufferedImage img = (BufferedImage) buttonLabels.get(key).loadIconFromResource().getImage();
			if(img.getWidth() != defaultImgIconSize) {
				img = (BufferedImage) ImageUtils.resize(img,defaultImgIconSize,defaultImgIconSize);
			}
			if(key.equals("rectangle")) {
				rectangleChk = new JCheckBox(key, new ImageIcon(img));
				rectangleChk.setName(key);
				rectangleChk.setFocusPainted(true);
				rectangleChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				rectangleChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(rectangleChk);
				roiGroup.add(rectangleChk);
				add(rectangleChk);
			}else if(key.equals("oval")) {
				ovalChk = new JCheckBox(key, new ImageIcon(img));
				ovalChk.setName(key);
				ovalChk.setFocusPainted(true);
				ovalChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				ovalChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(ovalChk);
				roiGroup.add(ovalChk);
				add(ovalChk);
			}else if(key.equals("line")) {
				lineChk = new JCheckBox(key, new ImageIcon(img));
				lineChk.setName(key);
				lineChk.setFocusPainted(true);
				lineChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				lineChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(lineChk);
				roiGroup.add(lineChk);
				add(lineChk);
			}else if(key.equals("polygon")) {
				polyChk = new JCheckBox(key, new ImageIcon(img));
				polyChk.setName(key);
				polyChk.setFocusPainted(true);
				polyChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				polyChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(polyChk);
				roiGroup.add(polyChk);
				add(polyChk);
			}else if(key.equals("point")) {
				pointChk = new JCheckBox(key, new ImageIcon(img));
				pointChk.setName(key);
				pointChk.setFocusPainted(true);
				pointChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				pointChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(pointChk);
				roiGroup.add(pointChk);
				add(pointChk);
			}else if(key.equals("arrow")) {
				arrowChk = new JCheckBox(key, new ImageIcon(img));
				arrowChk.setName(key);
				arrowChk.setFocusPainted(true);
				arrowChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				arrowChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(arrowChk);
				roiGroup.add(arrowChk);
				add(arrowChk);
			}else if(key.equals("text")) {
				textChk = new JCheckBox(key, new ImageIcon(img));
				textChk.setName(key);
				textChk.setFocusPainted(true);
				textChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				textChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(textChk);
				roiGroup.add(textChk);
				add(textChk);
			}else if(key.equals("angle")) {
				angleChk = new JCheckBox(key, new ImageIcon(img));
				angleChk.setName(key);
				angleChk.setFocusPainted(true);
				angleChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				angleChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(angleChk);
				roiGroup.add(angleChk);
				add(angleChk);
			}else if(key.equals("brush")) {
				brushChk = new JCheckBox(key, new ImageIcon(img));
				brushChk.setName(key);
				brushChk.setFocusPainted(true);
				brushChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				brushChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(brushChk);
				roiGroup.add(brushChk);
				add(brushChk);
			}else if(key.equals("window")) {
				windowChk = new JCheckBox(key, new ImageIcon(img));
				windowChk.setName(key);
				windowChk.setFocusPainted(true);
				windowChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				windowChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(windowChk);
				add(windowChk);
			}else {
				JButton btn = new JButton(key, new ImageIcon(img));
				btn.setName(key);
				btn.setFocusPainted(true);
				btn.setVerticalTextPosition(SwingConstants.BOTTOM);
				btn.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(btn);
				add(btn);
			}
			currentTool = Windowing;//default
			if(windowChk != null) {
				if (!isRoiTool(currentTool)) {
					windowChk.setSelected(true);
					windowChk.setBackground(Color.CYAN);
				}
			}
		}
		repaint();
	}

	private void setAction(JComponent comp) {
		JButton btn = null;
		JCheckBox chk = null;
		if(comp instanceof JButton) {
			btn = (JButton)comp;
		}else if(comp instanceof JCheckBox) {
			chk = (JCheckBox)comp;
		}
		switch (comp.getName()) {
		case "reset":
			Log.logger.fine("reset");
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					currentTool = Windowing;
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					StageDockManager sdm = own.getStageDockManager();
					String stageID = own.getStageIDInAction();
					StageView activeStage = sdm.getStage(stageID);
					if(activeStage != null) {
						Eyepiece eye = activeStage.getEyepiece();
						ArrayList<Praparat> selectedPraps = eye.getSelectingPraparats();
						if(selectedPraps.size() != 0) {
							for (Praparat pp : selectedPraps) {
								pp.resetView();
							}
						}else {
							PopUpMessage.showDialog(own, "Reset", "There is no series selected.", JOptionPane.INFORMATION_MESSAGE, JOptionPane.OK_OPTION);
						}
					}
					setSelectedToolBackground();
				}
			});
			break;
		case "invert":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					currentTool = Windowing;
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					StageDockManager sdm = own.getStageDockManager();
					String stageID = own.getStageIDInAction();
					StageView activeStage = sdm.getStage(stageID);
					if (activeStage != null) {
						Eyepiece eye = activeStage.getEyepiece();
						ArrayList<Praparat> selectedPraps = eye.getSelectingPraparats();
						for (Praparat pp : selectedPraps) {
							pp.processInvertImages();
						}
					}
					setSelectedToolBackground();
				}
			});
			break;
		case "flipLR":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					StageDockManager sdm = own.getStageDockManager();
					String stageID = own.getStageIDInAction();
					StageView activeStage = sdm.getStage(stageID);
					if(activeStage == null) {
						return;
					}
					Eyepiece eye = activeStage.getEyepiece();
					ArrayList<Praparat>  selectedPraps = eye.getSelectingPraparats();
					for(Praparat pp:selectedPraps) {
						pp.processFlipLR();
					}
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "flipHF":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					StageDockManager sdm = own.getStageDockManager();
					String stageID = own.getStageIDInAction();
					StageView activeStage = sdm.getStage(stageID);
					if(activeStage == null) {
						return;
					}
					Eyepiece eye = activeStage.getEyepiece();
					ArrayList<Praparat>  selectedPraps = eye.getSelectingPraparats();
					for(Praparat pp:selectedPraps) {
						pp.processFlipHF();
					}
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "screen out":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					StageDockManager sdm = own.getStageDockManager();
					String stageID = own.getStageIDInAction();
					StageView activeStage = sdm.getStage(stageID);
					if(activeStage == null) {
						return;
					}
					Eyepiece eye = activeStage.getEyepiece();
					eye.removeSelectedPraparats();
					eye.autoLayout();
					activeStage.updateInfoCake();
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "window":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "rectangle":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(rectangleChk.isSelected()) {
						currentTool = RoiObj.RECTANGLE;
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "oval":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(ovalChk.isSelected()) {
						currentTool = RoiObj.OVAL;
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "line":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(lineChk.isSelected()) {
						currentTool = RoiObj.LINE;
						lineChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "polygon":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(polyChk.isSelected()) {
						currentTool = RoiObj.POLYGON;
						polyChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "arrow":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(arrowChk.isSelected()) {
						currentTool = RoiObj.ARROW;
						arrowChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "point":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(pointChk.isSelected()) {
						currentTool = RoiObj.POINT;
						pointChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "text":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(textChk.isSelected()) {
						currentTool = RoiObj.TEXT;
						textChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "angle":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(angleChk.isSelected()) {
						currentTool = RoiObj.ANGLE;
						angleChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "analysis":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					currentTool = Analysis;
					Viewer2DScreen.getInstance();
					RoiObjManager rom = Viewer2DScreen.getRoiObjManager();
					rom.updateState();
					if(!rom.isVisible()) {
						rom.setVisible(true);
					}else {
						rom.requestFocus();
					}
					setSelectedToolBackground();
				}
			});
			break;
		case "crop":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					StageDockManager sdm = own.getStageDockManager();
					String stageID = own.getStageIDInAction();
					StageView activeStage = sdm.getStage(stageID);
					if(activeStage == null) {
						return;
					}
					Eyepiece eye = activeStage.getEyepiece();
					ArrayList<Praparat>  selectedPraps = eye.getSelectingPraparats();
					currentTool = Windowing;//to allow windowing in series window
					for(Praparat pp:selectedPraps) {
						pp.cropRectangle(true);//TODO crop using current roi
					}
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "cut":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					ArrayList<Praparat>  selectedPraps = own.getSelectedPraps();
					if(selectedPraps != null && selectedPraps.size() == 0) {
						return;
					}
					
					for(Praparat pp:selectedPraps) {
						pp.processCut();//cut current roi area
					}
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "brush":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					currentTool = Brush;//100
					brushChk.setBackground(Color.CYAN);
					setSelectedToolBackground();
				}
			});
			break;
		case "settings":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					/* if showing, show to top, else, create new window */
					Frame[] allFrames = Frame.getFrames();
					for (Frame fr : allFrames) {
						String specificFrameName = fr.getClass().getName();
						if (specificFrameName.equals("com.vis.environment.PreferencesWin")) {
							// close the frame
							if (fr.isShowing()) {
								fr.toFront();
								return;
							}
						}
					}
//					new PreferencesWin();
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "viewer3d":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					//can not show this, why ... ImageCanvas matter ??			
					/*
					 * anyway, I can handle to how to show canvas.
					 * I only do minimize and re-show window when show up 3d frame window.
					 */
//					SwingUtilities.invokeLater(() -> {
//						String sample = "src/test/resources/flybrain.tif";
//						ImagePlus imp = new ImagePlus(sample);
//						imp.show();
//			        });
//					new Thread(new Runnable() {
//						@Override
//						public void run() {
//							String sample = "src/test/resources/flybrain.tif";
//							ImagePlus imp = new ImagePlus(sample);
//							imp.show();
//						}
//					}).start();
					
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					ArrayList<Praparat>  selectedPraps = own.getSelectedPraps();
					int size = selectedPraps.size();
					if(selectedPraps == null || size < 1) {
						return;
					}
					//show only first prap
					Praparat prap = selectedPraps.get(0);
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							//TODO 20231008
							//new Viewer3DFrame_IJ(prap);
						}
					});
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "mpr":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					ArrayList<Praparat>  selectedPraps = own.getSelectedPraps();
					int size = selectedPraps.size();
					if(selectedPraps == null || size < 1) {
						return;
					}
					//show only first
					Praparat prap = selectedPraps.get(0);
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							//TODO 20231006
							//new MPRViewerWindow(prap);
						}
					});
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		default:
			currentTool = Windowing;
			setSelectedToolBackground();
		}
	}

	private HashMap<String, Resources> initButtonList() {
		HashMap<String, Resources> map = new HashMap<>();
		map.put("reset", Resources.ResetPraparatIcon);
		map.put("invert", Resources.InvertIcon);
		map.put("flipLR", Resources.FlipLRIcon);
		map.put("flipHF", Resources.FlipHFIcon);
		map.put("screen out", Resources.ScreenOutIcon);
		map.put("rectangle", Resources.RectangleRoiIcon);
		map.put("oval", Resources.OvalRoiIcon);
		map.put("line", Resources.LineRoiIcon);
		map.put("polygon", Resources.PolygonRoiIcon);
		map.put("arrow", Resources.ArrowRoiIcon);
		map.put("point", Resources.PointRoiIcon);
		map.put("text", Resources.TextRoiIcon);
		map.put("window", Resources.WindowContrastIcon);
		map.put("angle", Resources.AngleRoiIcon);
		map.put("analysis", Resources.RoiObjManagerWinIcon);
		map.put("crop", Resources.CropIcon);
		map.put("cut", Resources.CutIcon);
		map.put("brush", Resources.RoiBrushIcon);
		map.put("viewer3d", Resources.MenuBarViewer3DIcon);
		map.put("mpr", Resources.MenuBarMPRWindowIcon);
		return map;
	}
	
	public int getCurrentToolType() {
		return currentTool;
	}
	
	public static boolean isRoiTool(int toolType) {
		for(int i : roiTools) {
			if(i==toolType) {
				return true;
			}
		}
		return false;
	}
	
	/*
	 * window -> change color
	 * roi tool -> change color
	 * other -> no change
	 */
	private void setSelectedToolBackground() {
		if(windowChk == null) {
			return;
		}
		if(currentTool == Analysis) {
			roiGroup.clearSelection();
		}
		if(currentTool == Windowing) {
			if(!windowChk.isSelected()) {
				windowChk.setSelected(true);
			}
			windowChk.setBackground(Color.CYAN);
			roiGroup.clearSelection();
		}else {
			if(windowChk.isSelected()) {
				windowChk.setSelected(false);
			}
			windowChk.setBackground(getBackground());
		}
		for (Enumeration<AbstractButton> e = roiGroup.getElements(); e.hasMoreElements();) {
			AbstractButton chb = e.nextElement();
			if(!chb.isSelected()) {
				chb.setBackground(getBackground());
			}else {
				chb.setBackground(Color.cyan);
			}
		}
	}
}
