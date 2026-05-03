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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.TreeMap;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.plugin.PlugIn;
import com.vis.core.plugin.PluginShelf;
import com.vis.core.plugin.ToolbarPlugIn;
import com.vis.core.radiomics.RadiomicsWindow;
import com.vis.core.ui.dialog.OptionDialog;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.ui.dialog.WandToolDialog;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.roi.*;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D3.ui.Viewer3DMain;
import com.vis.core.view.D3.ui.VolumeData;
import com.vis.core.view.D3.ui.VolumeLoader;
import com.vis.core.view.mpr.SimpleMPRViewer;

/**
 * buttons design https://material.io/tools/icons/?style=outline
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class Viewer2DToolBar extends JToolBar{
	
	/* roi tool ids */
	/**
	 * RoiType.XXX.id(); will cause "case expressions must be constant expressions" error in switch statement.
	 */
	public final static int RectangleRoi = ij.gui.Roi.RECTANGLE;//RoiType.RECTANGLE.id();
	public final static int OvalRoi = ij.gui.Roi.OVAL;//RoiType.OVAL.id();
	public final static int PolygonRoi = ij.gui.Roi.POLYGON;//RoiType.POLYGON.id();
	public final static int FreeRoi = ij.gui.Roi.FREEROI;//RoiType.FREEROI.id();
	public final static int LineRoi = ij.gui.Roi.LINE;//RoiType.LINE.id();
	public final static int PolyLineRoi = ij.gui.Roi.POLYLINE;//RoiType.POLYLINE.id();
	public final static int FreeLineRoi = ij.gui.Roi.FREELINE;//RoiType.FREELINE.id();
	public final static int AngleRoi = ij.gui.Roi.ANGLE;//RoiType.ANGLE.id();
	public final static int PointRoi = ij.gui.Roi.POINT;//RoiType.POINT.id();
	public final static int MultiPointRoi = 101;//RoiType.MULTIPOINT.id();
	public final static int ArrowRoi = 100;//RoiType.ARROW.id();
	public final static int TextRoi = 102;//RoiType.TEXT.id();
	public final static int ShapeRoi = ij.gui.Roi.COMPOSITE;//RoiType.COMPOSITE.id();
	public final static int Brush = 103;//RoiType.BRUSH.id();
	public final static int Wand = 104;
	
	public final static int Windowing = 1000;
	public final static int Analysis = 1001;//RoiObjManager
	public final static int NONE = Integer.MIN_VALUE;
	
	public final static int[] roiTools = new int[] {
			RectangleRoi,
			OvalRoi,
			PolygonRoi,
			FreeRoi,
			LineRoi,
			PolyLineRoi,
			FreeLineRoi,
			AngleRoi,
			PointRoi,
			MultiPointRoi,
			ArrowRoi,
			TextRoi,
			ShapeRoi,
			Brush,
			Wand,
			// add to roi tool
	};
	
	//process features
	JButton resetBtn;
	JButton invertBtn;
	JButton flipLRBtn;
	JButton flipHFBtn;
	JButton removeBtn;
	JButton analysisBtn;
	JButton cropBtn;
	JButton cutBtn;
	JCheckBox windowChk;
	
	//roi features
	ButtonGroup roiGroup = new ButtonGroup();
	JCheckBox rectangleChk;
	JCheckBox ovalChk;
	JCheckBox polyChk;
	JCheckBox freeChk;
	JCheckBox lineChk;
	JCheckBox polyLineChk;
	JCheckBox freelineChk;
	JCheckBox angleChk;
	JCheckBox pointChk;
	JCheckBox mPointChk;//multi point
	JCheckBox arrowChk;
	JCheckBox textChk;
	JCheckBox brushChk;
	JCheckBox wandChk;
	
	int defaultImgIconSize = 48;
	private int currentTool = Windowing;//default
	
	JPanel base;
	JPanel pluginListPanel;// holds toolbar item buttons

	public Viewer2DToolBar() {
		
		if(Utils.isDebug) {
			try {
				checkRoiIDs();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		base = new JPanel();
		int hgap = 1;
		int vgap = hgap;
		base.setLayout(new FlowLayout(FlowLayout.LEFT, hgap, vgap));
		
		base.add(loadButtons(initProcessFunctions()));
		addSeparator();
		base.add(loadButtons(initRois()));
		addSeparator();
		loadPluginTools();
		
		// after above, add jscrollpane.
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setViewportView(base);
		scrollPane.setPreferredSize(new Dimension(300/*dummy*/, defaultImgIconSize*2+(hgap*2)+4/*adjust*/));
		add(scrollPane);
		
		if(windowChk != null) {
			if (!isRoiTool(currentTool)) {
				windowChk.setSelected(true);
				windowChk.setBackground(Color.CYAN);
			}
		}
	}
	
	private void checkRoiIDs() throws Exception {
		if(RectangleRoi != RoiType.RECTANGLE.id()) throw new Exception("Invalid RoiID");
		if(OvalRoi != RoiType.OVAL.id()) throw new Exception("Invalid RoiID");
		if(PolygonRoi != RoiType.POLYGON.id()) throw new Exception("Invalid RoiID");
		if(FreeRoi != RoiType.FREEROI.id()) throw new Exception("Invalid RoiID");
		if(LineRoi != RoiType.LINE.id()) throw new Exception("Invalid RoiID");
		if(ArrowRoi != RoiType.ARROW.id()) throw new Exception("Invalid RoiID");
		if(PolyLineRoi != RoiType.POLYLINE.id()) throw new Exception("Invalid RoiID");
		if(FreeLineRoi != RoiType.FREELINE.id()) throw new Exception("Invalid RoiID");
		if(AngleRoi != RoiType.ANGLE.id()) throw new Exception("Invalid RoiID");
		if(PointRoi != RoiType.POINT.id()) throw new Exception("Invalid RoiID");
		if(MultiPointRoi != RoiType.MULTIPOINT.id()) throw new Exception("Invalid RoiID");
		if(TextRoi != RoiType.TEXT.id()) throw new Exception("Invalid RoiID");
		if(ShapeRoi != RoiType.COMPOSITE.id()) throw new Exception("Invalid RoiID");
		if(Brush != RoiType.BRUSH.id()) throw new Exception("Invalid RoiID");
		//add more
	}

	public JPanel loadButtons(HashMap<String, Resources> buttonLabels) {
		JPanel p = new JPanel();
		p.setBorder(new LineBorder(Color.GRAY, 3, true));
		TreeMap<String, Resources> sortedMap = new TreeMap<>(buttonLabels);
		for (String key : sortedMap.keySet()) {
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
				p.add(rectangleChk);
			}else if(key.equals("oval")) {
				ovalChk = new JCheckBox(key, new ImageIcon(img));
				ovalChk.setName(key);
				ovalChk.setFocusPainted(true);
				ovalChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				ovalChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(ovalChk);
				roiGroup.add(ovalChk);
				p.add(ovalChk);
			}else if(key.equals("free")) {
				freeChk = new JCheckBox(key, new ImageIcon(img));
				freeChk.setName(key);
				freeChk.setFocusPainted(true);
				freeChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				freeChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(freeChk);
				roiGroup.add(freeChk);
				p.add(freeChk);
			}else if(key.equals("line")) {
				lineChk = new JCheckBox(key, new ImageIcon(img));
				lineChk.setName(key);
				lineChk.setFocusPainted(true);
				lineChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				lineChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(lineChk);
				roiGroup.add(lineChk);
				p.add(lineChk);
			}else if(key.equals("polyline")) {
				polyLineChk = new JCheckBox(key, new ImageIcon(img));
				polyLineChk.setName(key);
				polyLineChk.setFocusPainted(true);
				polyLineChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				polyLineChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(polyLineChk);
				roiGroup.add(polyLineChk);
				p.add(polyLineChk);
			}else if(key.equals("freeline")) {
				freelineChk = new JCheckBox(key, new ImageIcon(img));
				freelineChk.setName(key);
				freelineChk.setFocusPainted(true);
				freelineChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				freelineChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(freelineChk);
				roiGroup.add(freelineChk);
				p.add(freelineChk);
			}else if(key.equals("polygon")) {
				polyChk = new JCheckBox(key, new ImageIcon(img));
				polyChk.setName(key);
				polyChk.setFocusPainted(true);
				polyChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				polyChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(polyChk);
				roiGroup.add(polyChk);
				p.add(polyChk);
			}else if(key.equals("point")) {
				pointChk = new JCheckBox(key, new ImageIcon(img));
				pointChk.setName(key);
				pointChk.setFocusPainted(true);
				pointChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				pointChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(pointChk);
				roiGroup.add(pointChk);
				p.add(pointChk);
			}else if(key.equals("multipoint")) {
				mPointChk = new JCheckBox(key, new ImageIcon(img));
				mPointChk.setName(key);
				mPointChk.setFocusPainted(true);
				mPointChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				mPointChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(mPointChk);
				roiGroup.add(mPointChk);
				p.add(mPointChk);
			}else if(key.equals("arrow")) {
				arrowChk = new JCheckBox(key, new ImageIcon(img));
				arrowChk.setName(key);
				arrowChk.setFocusPainted(true);
				arrowChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				arrowChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(arrowChk);
				roiGroup.add(arrowChk);
				p.add(arrowChk);
			}else if(key.equals("text")) {
				textChk = new JCheckBox(key, new ImageIcon(img));
				textChk.setName(key);
				textChk.setFocusPainted(true);
				textChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				textChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(textChk);
				roiGroup.add(textChk);
				p.add(textChk);
			}else if(key.equals("angle")) {
				angleChk = new JCheckBox(key, new ImageIcon(img));
				angleChk.setName(key);
				angleChk.setFocusPainted(true);
				angleChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				angleChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(angleChk);
				roiGroup.add(angleChk);
				p.add(angleChk);
			}else if(key.equals("brush")) {
				brushChk = new JCheckBox(key, new ImageIcon(img));
				brushChk.setName(key);
				brushChk.setFocusPainted(true);
				brushChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				brushChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(brushChk);
				roiGroup.add(brushChk);
				p.add(brushChk);
			}else if(key.equals("wand")) {
				wandChk = new JCheckBox(key, new ImageIcon(img));
				wandChk.setName(key);
				wandChk.setFocusPainted(true);
				wandChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				wandChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(wandChk);
				roiGroup.add(wandChk);
				p.add(wandChk);
			}else if(key.equals("window")) {
				windowChk = new JCheckBox(key, new ImageIcon(img));
				windowChk.setName(key);
				windowChk.setFocusPainted(true);
				windowChk.setVerticalTextPosition(SwingConstants.BOTTOM);
				windowChk.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(windowChk);
				p.add(windowChk);
			}else {
				JButton btn = new JButton(key, new ImageIcon(img));
				btn.setName(key);
				btn.setFocusPainted(true);
				btn.setVerticalTextPosition(SwingConstants.BOTTOM);
				btn.setHorizontalTextPosition(SwingConstants.CENTER);
				setAction(btn);
				p.add(btn);
			}
		}
		return p;
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
							PopUpMessage.showDialog(own, "Reset", "There is no series selected.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
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
						currentTool = RectangleRoi;
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
						currentTool = OvalRoi;
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "free":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(freeChk.isSelected()) {
						currentTool = FreeRoi;
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
						currentTool = LineRoi;
						lineChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "freeline":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(freelineChk.isSelected()) {
						currentTool = FreeLineRoi;
						freelineChk.setBackground(Color.CYAN);
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
						currentTool = PolygonRoi;
						polyChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "polyline":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(polyLineChk.isSelected()) {
						currentTool = PolyLineRoi;
						polyLineChk.setBackground(Color.CYAN);
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
						currentTool = ArrowRoi;
						//arrowChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
						Log.logger.fine("ARROW TOOL activated ! : "+currentTool);
					}
				}
			});
			break;
		case "point":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(pointChk.isSelected()) {
						currentTool = PointRoi;
						pointChk.setBackground(Color.CYAN);
						setSelectedToolBackground();
					}
				}
			});
			break;
		case "multipoint":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(mPointChk.isSelected()) {
						currentTool = MultiPointRoi;
						mPointChk.setBackground(Color.CYAN);
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
						currentTool = TextRoi;
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
						currentTool = AngleRoi;
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
						rom.toFront();
					}else {
						rom.requestFocus();
						rom.toFront();
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
					for(Praparat pp:selectedPraps) {
						pp.processCropRectangle(true);
						break;//only perform first selected prap
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
						pp.processCut(true);//cut current roi area
						break;//only perform first selected prap
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
					currentTool = Brush;
					brushChk.setBackground(Color.CYAN);
					setSelectedToolBackground();
				}
			});
			// 右クリックを検知してオプションダイアログを開く
			chk.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e) {
					// 右クリック（またはMacのControl+クリック）を検知
					if (SwingUtilities.isRightMouseButton(e)) {
						showBrushOptionsDialog();
					}
				}
			});
			break;
		case "wand":
			chk.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					currentTool = Wand;
					wandChk.setBackground(Color.CYAN);
					setSelectedToolBackground();
					Viewer2DScreen v2s = Viewer2DScreen.getInstance();
					try {
						WandToolDialog.getInstance(v2s, "Wand Tool Options");
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
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
					
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					ArrayList<Praparat>  selectedPraps = own.getSelectedPraps();
					int size = selectedPraps.size();
					if(selectedPraps == null || size < 1) {
						return;
					}
					//show only first prap
					Praparat prap = selectedPraps.get(0);
					new Thread(() -> {
						SwingUtilities.invokeLater(() -> {
							Viewer3DMain frame = new Viewer3DMain();
							frame.setVisible(true); // ウィンドウを表示
							frame.revalidate();
							frame.repaint();

							javax.swing.Timer timer = new javax.swing.Timer(16, e -> { // 約60FPS
								if (frame.canvas != null) {
									frame.canvas.render(); // これが呼ばれると paintGL() が動く
									frame.canvas.repaint();
								}
							});
							timer.setRepeats(true);
							timer.start();
							VolumeData vol = VolumeLoader.loadDicom(prap);
							if (vol != null) {
								// Canvasにデータを渡す
								frame.canvas.setVolumeData(vol); // ← これを使う
							}
						});
//						JOptionPane.showConfirmDialog(null, "GRAPHY 3D Viewer is under development. Please use ImageJ's Volume Viewer graphy plugin !");
					}).start();

					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "mpr":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg) {
					Viewer2DScreen own = Viewer2DScreen.getInstance();
					ArrayList<Praparat>  selectedPraps = own.getSelectedPraps();
					int size = selectedPraps.size();
					if(selectedPraps == null || size < 1) {
						return;
					}
					Praparat prap = selectedPraps.get(0);
					new Thread(() -> {
						new SimpleMPRViewer(prap.getImagePlus(-1, -1));
					}).start();
					
					currentTool = Windowing;
					setSelectedToolBackground();
				}
			});
			break;
		case "radiomics":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg) {
					new Thread(() -> {
						new RadiomicsWindow();
			        }).start();
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

	private HashMap<String, Resources> initRois() {
		HashMap<String, Resources> map = new HashMap<>();
		map.put("rectangle", Resources.RectangleRoiIcon);
		map.put("oval", Resources.OvalRoiIcon);
		map.put("free", Resources.FreeRoiIcon);
		map.put("line", Resources.LineRoiIcon);
		map.put("polyline", Resources.PolyLineRoiIcon);
		map.put("freeline", Resources.FreeLineRoiIcon);
		map.put("polygon", Resources.PolygonRoiIcon);
		map.put("arrow", Resources.ArrowRoiIcon);
		map.put("point", Resources.PointRoiIcon);
		map.put("multipoint", Resources.MultiPointRoiIcon);
		map.put("text", Resources.TextRoiIcon);
		map.put("angle", Resources.AngleRoiIcon);
		map.put("brush", Resources.RoiBrushIcon);
		map.put("wand", Resources.RoiWandIcon);
		return map;
	}
	
	private HashMap<String, Resources> initProcessFunctions(){
		HashMap<String, Resources> map = new HashMap<>();
		map.put("reset", Resources.ResetPraparatIcon);
		map.put("invert", Resources.InvertIcon);
		map.put("flipLR", Resources.FlipLRIcon);
		map.put("flipHF", Resources.FlipHFIcon);
		map.put("screen out", Resources.ScreenOutIcon);
		map.put("window", Resources.WindowContrastIcon);
		map.put("analysis", Resources.RoiObjManagerWinIcon);
		map.put("crop", Resources.CropIcon);
		map.put("cut", Resources.CutIcon);
		map.put("viewer3d", Resources.MenuBarViewer3DIcon);
		map.put("mpr", Resources.MenuBarMPRWindowIcon);
		map.put("radiomics", Resources.RadiomicsJIcon);
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
	
	public void loadPluginTools() {

		PluginShelf pluginShelf = ApplicationFacade.pluginShelf;
		if (pluginShelf == null) {
			return;
		}
		
		if(pluginListPanel == null) {
			pluginListPanel = new JPanel();
			pluginListPanel.setBorder(new LineBorder(Color.GRAY, 3, true));
			pluginListPanel.setName("TOOLBAR_PLUGIN_LIST");
		}
		
		if(base != null) {
			boolean alreadyExists = false;
			for(Component c : base.getComponents()) {
				if ("TOOLBAR_PLUGIN_LIST".equals(c.getName())) {
					alreadyExists = true;
					break;
				}
			}
			if (!alreadyExists) {
				base.add(pluginListPanel);
			}
		}

		// 1. ロードされた全プラグインのリストを取得
		HashMap<String, String> loadedPlugins = pluginShelf.getLoadedPluginNames();
		if (loadedPlugins == null)
			return;

		// 2. 各プラグインを検査
		for (String pluginKey : loadedPlugins.keySet()) {

			// pluginShelfのリフレクションを使ってインスタンスを生成・取得
			PlugIn p = PluginShelf.findPlugIn(pluginKey);
			if (p != null) {
				// 3. ToolbarPlugIn インターフェースを実装しているか判定
				if (p instanceof ToolbarPlugIn) {
					ToolbarPlugIn tbPlugin = (ToolbarPlugIn) p;

					boolean alreadyExists = false;
					for (Component c : pluginListPanel.getComponents()) {
						// コンポーネントにセットした名前とpluginKeyを比較
						if (pluginKey.equals(c.getName())) {
							alreadyExists = true;
							break;
						}
					}

					// すでに存在する場合は、このプラグインの追加処理をスキップ
					if (alreadyExists) {
						continue;
					}

					// 4. ボタンを生成
					JButton btn = new JButton();
					btn.setName(pluginKey);
					Icon icon = tbPlugin.getIcon();

					if (icon != null) {
						btn.setIcon(icon);
					} else {
						// アイコンが無い場合はキー名（クラス名）をテキスト表示
						btn.setText(pluginKey);
					}

					btn.setToolTipText(tbPlugin.getToolTipText());
					btn.setFocusable(false); // ツールバーボタンの定石

					// 5. ボタンクリック時のアクションを設定
					btn.addActionListener(e -> {
						// プラグインを実行する
						pluginShelf.runPlugIn(pluginKey, null);
					});

					// 追加
					pluginListPanel.add(btn);
				}
			}
		}
		// 追加後にツールバーの表示を更新
		pluginListPanel.revalidate();
		pluginListPanel.repaint();
		revalidate();
		repaint();
	}
	
	/**
	 * ブラシオプショントグルダイアログを表示し、プロパティを更新する
	 */
	private void showBrushOptionsDialog() {
		// 1. 現在の設定をプロパティから読み込む
		int currentSize = 15; // default
		String sizeStr = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushSize);
		if (sizeStr != null) {
			try {
				currentSize = Integer.parseInt(sizeStr.trim());
			} catch (NumberFormatException e) {}
		}
		
		String currentType = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushType);
		if (currentType == null) {
			currentType = "Circle";
		}

		// 2. OptionDialog を作成して表示する
		OptionDialog gd = new OptionDialog("Brush Options", (JFrame)WindowManager.getWindow(ConfigInfo.D2ViewerWindow));
		gd.addNumericField("Brush Size", currentSize, 0, 5, "pixels");
		gd.addChoice("Brush Type", new String[]{"Circle", "Square"}, currentType);
		gd.pack();
		gd.showDialog();

		// キャンセルされたら何もしない
		if (gd.wasCanceled()) return;

		// 3. 入力された値を取得
		int newSize = (int) gd.getNextNumber();
		String newType = gd.getNextChoice();

		// 安全のためのガード（1未満や大きすぎるサイズを防ぐ）
		if (newSize < 1) newSize = 1;
		if (newSize > 500) newSize = 500;

		// 4. プロパティに書き込んで保存する
		// （※PropertiesUtil の正確な set/save メソッド名に合わせて調整してください）
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushSize, String.valueOf(newSize));
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushType, newType);
		
	}
}
