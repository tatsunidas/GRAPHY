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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import ij.gui.Roi;

public class RadiomicsPanel extends JPanel{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	ButtonGroup modeSelect;
	
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
	
	final String[] defaultClasses = new String[] {"class1","class2"};
	List<ClassPanel> classes = new ArrayList<>();
	
	public RadiomicsPanel() {
		initBtns();
		buildGUI();
	}
	
	private void initBtns() {
		
		/**
		 * select mode of task, classification or segmentation 
		 */
		modeSelect = new ButtonGroup();
		
		saveConfigBtn = new JButton("Save Configurations");
		loadConfigBtn = new JButton("Load Configurations");
		
		trainModelBtn = new JButton("Train model");
		loadModelBtn = new JButton("Load Model");
		saveModelBtn = new JButton("Save Model");
		
		predBtn = new JButton("Prediction");
		showProbBtn = new JButton("Show Probabilities");
		createMaskBtn = new JButton("Show Masks");
		
		settingsBtn = new JButton("Settings");
		
		wekaBtn = new JButton("WEKA");
		
		loadRoiBtn = new JButton("Load Roi to predict");
	}

	private void buildGUI() {
		setLayout(new BorderLayout());
		JPanel north = new JPanel();
		JRadioButton rbClassification = new JRadioButton("Classification");
		rbClassification.setActionCommand("CLASSIFICATION");
		JRadioButton rbSegmentation = new JRadioButton("Segmentation");
		rbSegmentation.setActionCommand("SEGMENTATION");
		north.add(rbClassification);
		north.add(rbSegmentation);
		modeSelect.add(rbClassification);
		modeSelect.add(rbSegmentation);
		modeSelect.setSelected(rbClassification.getModel(), true);
		add(north, BorderLayout.NORTH);
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
		
		JPanel configuration = new JPanel();
		configuration.setLayout(new GridLayout(0, 1, 0, 5));
		configuration.setBorder(BorderFactory.createTitledBorder(b, "Configuration", TitledBorder.CENTER, TitledBorder.DEFAULT_JUSTIFICATION));
		configuration.add(loadConfigBtn);
		configuration.add(saveConfigBtn);
		westPanel.add(configuration);
		
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
		settings.setBorder(BorderFactory.createTitledBorder(b, "Settings", TitledBorder.CENTER, TitledBorder.DEFAULT_JUSTIFICATION));
		settings.add(settingsBtn);
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
		JPanel panel = new ClassPanel(0, "Rois for prediction");
		panel.setLayout(new BorderLayout());
		panel.add(loadRoiBtn, BorderLayout.SOUTH);
		return panel;
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
		return null; // 何も選択されていない場合
	}
	
	class ClassPanel extends JPanel{
		final int ind;
		final String name;
		JList<Roi> roiList;
		DefaultListModel<Roi> listModel = new DefaultListModel<>();
		ClassPanel(int index, String name){
			ind = index;
			this.name = name;
			roiList = new JList<>(listModel);
			roiList.setCellRenderer(new RoiListCellRenderer());
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
		
		void add(Roi r) {
			listModel.add(listModel.getSize(), r);
		}
		
		void delete(Roi r) {
			int pos = listModel.indexOf(r);
			if(pos >= 0) {
				listModel.remove(listModel.indexOf(r));
			}
		}
		
		void updateOrReplace(int row/*0 to n-1*/, Roi r) {
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
		@Override
		public Component getListCellRendererComponent(JList<?> list, // リスト自体
				Object value, // 表示する値 (この場合は Roi オブジェクト)
				int index, // リスト内のインデックス
				boolean isSelected, // 項目が選択されているか
				boolean cellHasFocus) { // 項目がフォーカスを持っているか

			// 親クラスのメソッドを呼び出して、基本的なレンダリング（選択状態の色など）を取得
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			// value が Roi のインスタンスであるかを確認
			if (value instanceof Roi) {
				Roi roi = (Roi) value;
				// Roi の名前を取得してテキストとして設定
				setText(roi.getName());
			} else {
				// Roi 以外の場合は、デフォルトのtoString()の結果などを使用
				setText((value == null) ? "" : value.toString());
			}
			return this;
		}
	}
	
}
