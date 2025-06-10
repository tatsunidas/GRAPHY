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
package com.vis.core.radiomics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.ContextKey;
import com.vis.configuration.GraphyProp;
import com.vis.core.facade.WindowManager;
import com.vis.core.view.D2.roi.RoiConverter;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiObjManager;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.Praparat;

import ij.gui.Roi;
import weka.gui.GUIChooserApp;

public class RadiomicsPanel extends JPanel{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	ButtonGroup modeSelect;
	ClassPanel rois4ClfPanel;
	
	//config
	JButton saveConfigBtn;
	JButton loadConfigBtn;
	//model
	JButton trainModelBtn;
	JButton loadModelBtn;
	JButton saveModelBtn;
	//inference
	JButton predBtn;
	JButton showProbBtn;
	JButton createMaskBtn;
	//settings
	JButton settingsBtn;
	
	/**
	 * weka, to manipulate dataset csv.
	 */
	JButton wekaBtn;
	
	JButton loadRoiBtn;
	
	RoiObjManager rm = (RoiObjManager)WindowManager.getWindow(ConfigInfo.RoiManager);
	
	//command names
	private final String CLASSIFICATION = "Classification";
	private final String SEGMENTATION = "Segmentation";
	private final String SAVE_CONFIG = "Save Configurations";
	private final String LOAD_CONFIG = "Load Configurations";
	private final String TRAIN_MODEL = "Train model";
	private final String SAVE_MODEL = "Save model";
	private final String LOAD_MODEL = "Load model";
	private final String PREDICTION = "Prediction";
	private final String SHOW_PROBABILITIES = "Show probabilities";
	private final String SHOW_MASKS = "Show masks";
	private final String SETTINGS = "Settings";
	private final String WEKA = "WEKA";
	private final String LOAD_ROIS = "Load Rois to predict";
	
	final String[] defaultClasses = new String[] {"class1","class2"};
	List<ClassPanel> classes = new ArrayList<>();
	
	RadiomicsPipeline pipeline;
	
	public RadiomicsPanel() {
		initBtns();
		buildGUI();
	}
	
	private void initBtns() {
		
		/**
		 * select mode of task, classification or segmentation 
		 */
		modeSelect = new ButtonGroup();
		
		trainModelBtn = new JButton(TRAIN_MODEL);
		loadModelBtn = new JButton(LOAD_MODEL);
		saveModelBtn = new JButton(SAVE_MODEL);
		
		predBtn = new JButton(PREDICTION);
		showProbBtn = new JButton(SHOW_PROBABILITIES);
		createMaskBtn = new JButton(SHOW_MASKS);
		
		settingsBtn = new JButton(SETTINGS);
		saveConfigBtn = new JButton(SAVE_CONFIG);
		loadConfigBtn = new JButton(LOAD_CONFIG);
		
		wekaBtn = new JButton(WEKA);
		wekaBtn.setActionCommand(WEKA);
		setAction(wekaBtn);
		
		loadRoiBtn = new JButton(LOAD_ROIS);
		setAction(loadRoiBtn);
	}

	private void buildGUI() {
		setLayout(new BorderLayout());
		JPanel north = new JPanel();
		JRadioButton rbClassification = new JRadioButton(CLASSIFICATION);
		rbClassification.setActionCommand(CLASSIFICATION);
		JRadioButton rbSegmentation = new JRadioButton(SEGMENTATION);
		rbSegmentation.setActionCommand(SEGMENTATION);
		north.add(rbClassification);
		north.add(rbSegmentation);
		modeSelect.add(rbClassification);
		modeSelect.add(rbSegmentation);
		modeSelect.setSelected(rbClassification.getModel(), true);
		add(north, BorderLayout.NORTH);
		//functions
		JPanel func = buidFunctionPanel();
		JScrollPane trainds = buildTrainingDataPanel();
		JPanel predRoi = buildRoi4ClassificationPanel(); 
		JPanel center = new JPanel(new GridLayout(0, 3, 3, 3));
		center.add(func);
		center.add(trainds);
		center.add(predRoi);
		
		add(center, BorderLayout.CENTER);
		setPreferredSize(new Dimension(730, 500));
	}
	
	private JPanel buidFunctionPanel() {
		JPanel westPanel = new JPanel();
		westPanel.setLayout(new BoxLayout(westPanel, BoxLayout.Y_AXIS));
		
		Border b = BorderFactory.createSoftBevelBorder(BevelBorder.RAISED, Color.ORANGE, Color.GRAY);
				
		JPanel model = new JPanel();
		model.setLayout(new GridLayout(0, 1, 0, 5));
		model.setBorder(BorderFactory.createTitledBorder(b, "Model", TitledBorder.CENTER, TitledBorder.DEFAULT_JUSTIFICATION));
		model.add(trainModelBtn);
		model.add(loadModelBtn);
		model.add(saveModelBtn);
		westPanel.add(model);
		
		JPanel inference = new JPanel();
		inference.setLayout(new GridLayout(0, 1, 0, 5));
		inference.setBorder(BorderFactory.createTitledBorder(b, "Inference", TitledBorder.CENTER, TitledBorder.DEFAULT_JUSTIFICATION));
		inference.add(predBtn);
		inference.add(showProbBtn);
		inference.add(createMaskBtn);
		westPanel.add(inference);
		
		JPanel settings = new JPanel();
		settings.setLayout(new GridLayout(0, 1, 0, 5));
		settings.setBorder(BorderFactory.createTitledBorder(b, "Settings", TitledBorder.CENTER,
				TitledBorder.DEFAULT_JUSTIFICATION));
		settings.add(settingsBtn);
		settings.add(loadConfigBtn);
		settings.add(saveConfigBtn);
		westPanel.add(settings);
		
		JPanel wekaP = new JPanel();
		wekaP.setLayout(new GridLayout(0, 1, 0, 5));
		wekaP.setBorder(BorderFactory.createTitledBorder(b, "Data science", TitledBorder.CENTER, TitledBorder.DEFAULT_JUSTIFICATION));
		wekaP.add(wekaBtn);
		westPanel.add(wekaP);
		
		return westPanel;
	}
	
	private JScrollPane buildTrainingDataPanel() {
		JPanel eastPanel = new JPanel();
		eastPanel.setLayout(new BoxLayout(eastPanel, BoxLayout.Y_AXIS));
		JScrollPane eastScroll = new JScrollPane(eastPanel);
		Border b = BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.cyan, Color.DARK_GRAY);
		eastScroll.setBorder(BorderFactory.createTitledBorder(b, "Training dataset", TitledBorder.CENTER, TitledBorder.DEFAULT_POSITION));
		
		for(String name: defaultClasses) {
			ClassPanel cp = (ClassPanel) createNewClass(name);
			eastPanel.add(createNewClass(name));
			classes.add(cp);
		}
		
		eastPanel.setPreferredSize(new Dimension(210, 500));
		
		return eastScroll;
	}
	
	private JPanel buildRoi4ClassificationPanel() {
		rois4ClfPanel = new ClassPanel(0, "Rois for classification");
		rois4ClfPanel.setLayout(new BorderLayout());
		rois4ClfPanel.add(loadRoiBtn, BorderLayout.SOUTH);
		return rois4ClfPanel;
	}
	
	private JPanel createNewClass(String name) {
		int new_index = classes.size();
		return new ClassPanel(new_index, name);
	}
	
	public String whatMode() {
		for (Enumeration<AbstractButton> buttons = modeSelect.getElements(); buttons.hasMoreElements();) {
			AbstractButton button = buttons.nextElement();
			if (button.isSelected()) {
				return button.getActionCommand();
			}
		}
		return null;
	}
	
	private void setAction(JComponent con) {
		if(con instanceof JButton) {
			JButton btn = (JButton)con;
			String name = btn.getActionCommand();
			if(name.equals("")) {
				
			}else if(name.equals(TRAIN_MODEL)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						//pipeline.train("logistic", null, null);
					}
				});	
			}else if(name.equals(WEKA)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						launch_weka();
					}
				});
			}else if(name.equals(LOAD_ROIS)) {
				//TODO TEST
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						ArrayList<Roi> rois = new ArrayList<>();
						JFileChooser chooser = new JFileChooser();
						chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
						chooser.setMultiSelectionEnabled(true);
						Window d2s = WindowManager.getWindow(ConfigInfo.D2ViewerWindow);
						int res = chooser.showOpenDialog(d2s/*null-able*/);
						if(res == JFileChooser.APPROVE_OPTION) {
							File[] files = chooser.getSelectedFiles();
							if(files != null && files.length > 0) {
								for(File f : files) {
									List<Roi> rs = RoiObjManager.open(f.getAbsolutePath());
									if(rs != null) {
										rois.addAll(rs);
									}
								}
							}
						}
						for(Roi r : rois) {
							rois4ClfPanel.add(new RoiConverter().convert2RoiObj(r));
						}
					}
				});
			}
		}
	}
	
	public void launch_weka() {
		GUIChooserApp chooser = new GUIChooserApp();
		for (WindowListener wl : chooser.getWindowListeners()) {
			chooser.removeWindowListener(wl);
		}
		chooser.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		chooser.setLocationRelativeTo(this);
		chooser.setVisible(true);
	}
	
	class ClassPanel extends JPanel{
		private static final long serialVersionUID = 1L;
		final int ind;
		final String name;
		JList<RoiObj> roiList;
		DefaultListModel<RoiObj> listModel = new DefaultListModel<>();
		JButton addBtn = new JButton("Add");
		ClassPanel(int index, String name){
			ind = index;
			this.name = name;
			roiList = new JList<>(listModel);
			roiList.setCellRenderer(new RoiListCellRenderer());
			setLayout(new BorderLayout());
			add(addBtn, BorderLayout.NORTH);
			addBtn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					Viewer2DScreen screen = (Viewer2DScreen) WindowManager.getWindow(ConfigInfo.D2ViewerWindow);
					if(screen == null) {
						System.out.println("2DViewer is NULL !!!");
						return;
					}
					ArrayList<Praparat> praps = screen.getSelectedPraps();
					if(praps != null && praps.size() > 0) {
						for(Praparat prap : praps) {
							ArrayList<RoiObj> rois = prap.getSelectedRois();
							if(rois.size() > 0) {
								for(RoiObj r : rois) {
									add(r);
								}
							}
						}
					}else {
						System.out.println("Any praparats are not selected, cannot find roi... ");
					}
				}
			});
			add(roiList, BorderLayout.CENTER);
			Border b = BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.cyan, Color.DARK_GRAY);
			setBorder(BorderFactory.createTitledBorder(b, name, TitledBorder.CENTER, TitledBorder.DEFAULT_POSITION));
			setPreferredSize(new Dimension(200, 200));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
		}
		int index() {
			return ind;
		}
		String name() {
			return name;
		}
		
		void add(RoiObj r) {
			String roiId = r.getUIDs().get(ContextKey.RoiID);
			if(roiId == null) {
				System.out.println("Cannot load. This roi is not created by GRAPHY...:"+r.getName());
				return;
			}
			if(listModel.contains(r)) {
				System.out.println(roiId + " is already listed.");
				return;
			}
			for(int i=0; i<listModel.size(); i++) {
				RoiObj r2 = listModel.get(i);
				String roiId2 = r2.getUIDs().get(ContextKey.RoiID);
				if(roiId.equals(roiId2)) {
					//already in. skip.
					return;
				}
			}
			listModel.add(listModel.getSize(), r);
		}
		
		void delete(RoiObj r) {
			int pos = listModel.indexOf(r);
			if(pos >= 0) {
				listModel.remove(listModel.indexOf(r));
			}
		}
		
		void updateOrReplace(int row/*0 to n-1*/, RoiObj r) {
			if(row < 0 || row > listModel.getSize()) {
				System.out.println("RadiomicsPanel.ClassPanle:updateOrReplace:: Out Of Range ! "+row);
				return;
			}
			if(r == null) {
				System.out.println("RadiomicsPanel.ClassPanle:updateOrReplace:: Rois is NULL ! "+row);
				return;
			}
			listModel.set(row, r);
		}
	}
	
	class RoiListCellRenderer extends DefaultListCellRenderer {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList<?> list,
				Object value, // RoiObj
				int index, 
				boolean isSelected,
				boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof RoiObj) {
				RoiObj roi = (RoiObj) value;
				setText(roi.getUIDs().get(ContextKey.RoiID));
			} else {
				setText((value == null) ? "" : value.toString());
			}
			return this;
		}
	}
	
}
