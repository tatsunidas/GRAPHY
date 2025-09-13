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
package com.vis.core.ui.settings;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;

import javax.swing.JPanel;
import java.awt.GridBagLayout;
import javax.swing.JCheckBox;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import java.awt.Insets;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.FontSettings;
import com.vis.core.ui.LookAndFeels;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;

import java.awt.Component;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JTextField;

/**
 * 
 * @author tatsunidas
 *
 */
public class GeneralPrefs extends JPanel{
	
	private static final long serialVersionUID = 5828583604993199622L;
	JButton btnNewButton;//DB loc select
	
	private JTextField textField_db;
	JCheckBox chckbxSetDefault;
	JComboBox<Integer> comboBox_font;
	JComboBox<String> comboBox_laf;
	JButton btnSetDefault;
	JCheckBox chckbxOn;
	JCheckBox chckbxIgnore;
	
	LookAndFeels laf = ApplicationFacade.getLookAndFeels();
	final Integer defaultFontSize = 12;
	final String defaultLAF = LookAndFeels.defaultLAF;
	final Integer[] fontSizes = { 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32};
	/*
	 * default db location is "user_home/.GRAPHY"
	 */
	final String defaultLoc; //abs path
	
	Integer currentFontSize;
	String currentLAF;
	
	public GeneralPrefs() {
		File defaultDBDir = new File(ConfigInfo.DefaultDBLocation.toString());
		defaultLoc = defaultDBDir.getAbsolutePath();
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0, 54, 0, 0, 0, 0, 0, 0, 0, 48, 97, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		Component rigidArea = Box.createRigidArea(new Dimension(20, 20));
		GridBagConstraints gbc_rigidArea = new GridBagConstraints();
		gbc_rigidArea.insets = new Insets(0, 0, 5, 5);
		gbc_rigidArea.gridx = 0;
		gbc_rigidArea.gridy = 0;
		add(rigidArea, gbc_rigidArea);
		
		Component rigidArea_1 = Box.createRigidArea(new Dimension(20, 20));
		GridBagConstraints gbc_rigidArea_1 = new GridBagConstraints();
		gbc_rigidArea_1.insets = new Insets(0, 0, 5, 0);
		gbc_rigidArea_1.gridx = 13;
		gbc_rigidArea_1.gridy = 0;
		add(rigidArea_1, gbc_rigidArea_1);
		
		JLabel lblGeneral = new JLabel("General");
		GridBagConstraints gbc_lblGeneral = new GridBagConstraints();
		gbc_lblGeneral.insets = new Insets(0, 0, 5, 5);
		gbc_lblGeneral.gridx = 1;
		gbc_lblGeneral.gridy = 1;
		add(lblGeneral, gbc_lblGeneral);
		
		JLabel lblLocalDb = new JLabel("Local DB");
		GridBagConstraints gbc_lblLocalDb = new GridBagConstraints();
		gbc_lblLocalDb.insets = new Insets(0, 0, 5, 5);
		gbc_lblLocalDb.anchor = GridBagConstraints.EAST;
		gbc_lblLocalDb.gridx = 2;
		gbc_lblLocalDb.gridy = 2;
		add(lblLocalDb, gbc_lblLocalDb);
		
		textField_db = new JTextField();
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.gridwidth = 9;
		gbc_textField.insets = new Insets(0, 0, 5, 5);
		gbc_textField.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField.gridx = 3;
		gbc_textField.gridy = 2;
		add(textField_db, gbc_textField);
		textField_db.setColumns(10);
		textField_db.setEditable(false);
		
		btnNewButton = new JButton("Select");
		GridBagConstraints gbc_btnNewButton = new GridBagConstraints();
		gbc_btnNewButton.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnNewButton.insets = new Insets(0, 0, 5, 5);
		gbc_btnNewButton.gridx = 12;
		gbc_btnNewButton.gridy = 2;
		add(btnNewButton, gbc_btnNewButton);
		
		chckbxSetDefault = new JCheckBox("Set default");
		chckbxSetDefault.setToolTipText("user_home/.GRAPHY");
		GridBagConstraints gbc_chckbxSetDefault = new GridBagConstraints();
		gbc_chckbxSetDefault.gridwidth = 9;
		gbc_chckbxSetDefault.fill = GridBagConstraints.HORIZONTAL;
		gbc_chckbxSetDefault.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxSetDefault.gridx = 3;
		gbc_chckbxSetDefault.gridy = 3;
		add(chckbxSetDefault, gbc_chckbxSetDefault);
		
		JLabel lblFontSize = new JLabel("Font Size");
		GridBagConstraints gbc_lblFontSize = new GridBagConstraints();
		gbc_lblFontSize.anchor = GridBagConstraints.EAST;
		gbc_lblFontSize.insets = new Insets(0, 0, 5, 5);
		gbc_lblFontSize.gridx = 2;
		gbc_lblFontSize.gridy = 4;
		add(lblFontSize, gbc_lblFontSize);
		
		comboBox_font = new JComboBox<>();
		GridBagConstraints gbc_comboBox = new GridBagConstraints();
		gbc_comboBox.gridwidth = 4;
		gbc_comboBox.insets = new Insets(0, 0, 5, 5);
		gbc_comboBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBox.gridx = 3;
		gbc_comboBox.gridy = 4;
		add(comboBox_font, gbc_comboBox);
		
		DefaultComboBoxModel<Integer> fontCombModel = new DefaultComboBoxModel<>(fontSizes);
		comboBox_font.setModel((ComboBoxModel<Integer>) fontCombModel);
		
		JLabel lblLookAndFeel = new JLabel("Look and Feel");
		GridBagConstraints gbc_lblLookAndFeel = new GridBagConstraints();
		gbc_lblLookAndFeel.anchor = GridBagConstraints.EAST;
		gbc_lblLookAndFeel.insets = new Insets(0, 0, 5, 5);
		gbc_lblLookAndFeel.gridx = 2;
		gbc_lblLookAndFeel.gridy = 5;
		add(lblLookAndFeel, gbc_lblLookAndFeel);
		
		comboBox_laf = new JComboBox<>();
		GridBagConstraints gbc_comboBox_1 = new GridBagConstraints();
		gbc_comboBox_1.gridwidth = 9;
		gbc_comboBox_1.insets = new Insets(0, 0, 5, 5);
		gbc_comboBox_1.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBox_1.gridx = 3;
		gbc_comboBox_1.gridy = 5;
		add(comboBox_laf, gbc_comboBox_1);
		DefaultComboBoxModel<String> lafmodel = new DefaultComboBoxModel<String>();
		/*
		 * this is a just short name of laf.
		 * You have to use long name laf to change UI.
		 * laf.getInstalledLAFMap().get(selectItem);//selectItem is short name. 
		 */
		ArrayList<String> names = laf.getAllInstalledLAFShortName();
		for(String laf_name:names) {
			lafmodel.addElement(laf_name);
		}
		comboBox_laf.setModel(lafmodel);
		
		btnSetDefault = new JButton("Set default");
		btnSetDefault.setToolTipText("Back to default settings font and look nad feels.");
		GridBagConstraints gbc_btnSetDefault = new GridBagConstraints();
		gbc_btnSetDefault.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnSetDefault.insets = new Insets(0, 0, 5, 5);
		gbc_btnSetDefault.gridx = 12;
		gbc_btnSetDefault.gridy = 5;
		add(btnSetDefault, gbc_btnSetDefault);
		
		JLabel lblRefleshQr = new JLabel("Reflesh QR");
		GridBagConstraints gbc_lblRefleshQr = new GridBagConstraints();
		gbc_lblRefleshQr.anchor = GridBagConstraints.EAST;
		gbc_lblRefleshQr.insets = new Insets(0, 0, 5, 5);
		gbc_lblRefleshQr.gridx = 2;
		gbc_lblRefleshQr.gridy = 6;
		add(lblRefleshQr, gbc_lblRefleshQr);
		
		chckbxOn = new JCheckBox("On");
		chckbxOn.setToolTipText("Reflesh QR tables every 20 seconds");
		GridBagConstraints gbc_chckbxOn = new GridBagConstraints();
		gbc_chckbxOn.anchor = GridBagConstraints.WEST;
		gbc_chckbxOn.gridwidth = 4;
		gbc_chckbxOn.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxOn.gridx = 3;
		gbc_chckbxOn.gridy = 6;
		add(chckbxOn, gbc_chckbxOn);
		
		if(Utils.isQRRefreshOn()) {
			chckbxOn.setSelected(true);
		}else {
			chckbxOn.setSelected(false);
		}
		
		JLabel lblInoreNullSearchKey = new JLabel("Ignore null search key warning");
		GridBagConstraints gbc_lblInoreNullSearchKey = new GridBagConstraints();
		gbc_lblInoreNullSearchKey.anchor = GridBagConstraints.EAST;
		gbc_lblInoreNullSearchKey.insets = new Insets(0, 0, 5, 5);
		gbc_lblInoreNullSearchKey.gridx = 2;
		gbc_lblInoreNullSearchKey.gridy = 7;
		add(lblInoreNullSearchKey, gbc_lblInoreNullSearchKey);
		
		chckbxIgnore = new JCheckBox("Ignore");
		chckbxIgnore.setToolTipText("Ignore null search key warning");
		GridBagConstraints gbc_chckbxIgnore = new GridBagConstraints();
		gbc_chckbxIgnore.anchor = GridBagConstraints.WEST;
		gbc_chckbxIgnore.gridwidth = 4;
		gbc_chckbxIgnore.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxIgnore.gridx = 3;
		gbc_chckbxIgnore.gridy = 7;
		add(chckbxIgnore, gbc_chckbxIgnore);
		
		if(Utils.ignoreNullSearchKeyWarning()) {
			chckbxIgnore.setSelected(true);
		}else {
			chckbxIgnore.setSelected(false);
		}
		
		Component rigidArea_2 = Box.createRigidArea(new Dimension(20, 20));
		GridBagConstraints gbc_rigidArea_2 = new GridBagConstraints();
		gbc_rigidArea_2.insets = new Insets(0, 0, 0, 5);
		gbc_rigidArea_2.gridx = 0;
		gbc_rigidArea_2.gridy = 7;
		add(rigidArea_2, gbc_rigidArea_2);
		
		//finally
		init();
	}
	
	private void init() {
		//set local db location to field
		String useDefaultDBLoc = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.UseDefaultLocalDBLocation);
		String currentLoc = DatabaseHandler.getInstance().getDatabaseFolderPath(false);
		//if default location true
		if(defaultLoc.equals(currentLoc) || useDefaultDBLoc.equals("true")) {
			chckbxSetDefault.setSelected(true);
			btnNewButton.setEnabled(false);
		}else {
			chckbxSetDefault.setSelected(false);
			btnNewButton.setEnabled(true);
		}
		textField_db.setText(currentLoc);
		
		String fontSizeString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.FontSize);
		if(fontSizeString != null && !fontSizeString.isBlank()) {
			try {
				currentFontSize = Integer.parseInt(fontSizeString);
				comboBox_font.setSelectedItem(currentFontSize);
			}catch(NumberFormatException e){
				currentFontSize = defaultFontSize.intValue();
				comboBox_font.setSelectedItem(currentFontSize);
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.FontSize, String.valueOf(currentFontSize));
			}
		}
		currentLAF = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.LookAndFeels);
		if(currentLAF != null && !currentLAF.isBlank() && laf.isInstalled(currentLAF)) {
			comboBox_laf.setSelectedItem(currentLAF);
		}else {
			currentLAF = defaultLAF.toString();
			comboBox_laf.setSelectedItem(currentLAF);
			PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.LookAndFeels, currentLAF);
		}
		/*
		 * add listeners
		 */
		
		/**
		 * back to default loc db.
		 */
		chckbxSetDefault.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					String dbPath = textField_db.getText();
					if(dbPath.equals(defaultLoc)/*abs path*/ && defaultLoc.equals(currentLoc)) {
						//already set to default.
						//this case do not need restart.
						btnNewButton.setEnabled(false);
						return;
					}
					textField_db.setText(defaultLoc);
					btnNewButton.setEnabled(false);
					//restart
					int res = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Need restart. \nAre you ready to restart (You have to re-open) ?");
					if(res ==JOptionPane.OK_OPTION) {
						PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.LocalDBLocation, defaultLoc);
						PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.UseDefaultLocalDBLocation, "true");
						Utils.restart();
					}else {
						return;
					}
				} else {
					Log.logger.fine("Another DB location will set...");
					PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.UseDefaultLocalDBLocation, "false");
					btnNewButton.setEnabled(true);
				}
			}
		});
		
		//local db location
		btnNewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(chckbxSetDefault.isSelected()) {
					return;
				}
				JFileChooser jfc = new JFileChooser();
				jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				jfc.setMultiSelectionEnabled(false);
				int res= jfc.showDialog(PreferencesWin.getInstance(), "Select DB Folder");
				if(res==JFileChooser.APPROVE_OPTION) {
					File selectDir = jfc.getSelectedFile();
					if(selectDir == null) {
						return;
					}
					String selectPath = selectDir.getAbsolutePath();
					if(selectPath.equals(defaultLoc)) {
						chckbxSetDefault.setSelected(true);//activate itemlistener
						return;
					}
					String dbDir = DatabaseHandler.getInstance().getDatabaseFolderPath(false/*withDBNameFolder*/);
					if(selectPath.equals(dbDir)) {
						return;
					}else {
						int res_sub = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Need restart. \nAre you ready to restart ?");
						if(res_sub==JOptionPane.OK_OPTION) {
							PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.LocalDBLocation, selectPath);
							Utils.restart();
						}else {
							return;
						}
					}
				}else {
					return;
				}
			}
		});
		comboBox_font.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent ie) {
				Integer size = (Integer) ie.getItem();
				if (size.intValue() == currentFontSize.intValue()) {
					return;
				} else {
					currentFontSize = size;
				}
				Font f = new Font(FontSettings.getCurrentTextFont(), Font.PLAIN, currentFontSize);// name, style, size
				WindowManager.updateFont(f);//update laf and save font
			}
		});
		comboBox_laf.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				String select = (String) e.getItem();
				Log.logger.fine(select);
				String laf_ = laf.getInstalledLAFMap().get(select);
				if(laf_.equals(currentLAF)) {
					return;
				}else {
					currentLAF = laf_;
				}
				laf.setLookAndFeel(currentLAF);
				WindowManager.updateLookAndFeels(laf);
			}
		});
		btnSetDefault.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
//				comboBox_font.setSelectedItem(defaultFontSize);
//				currentFontSize = defaultFontSize.intValue();
//				Font f = new Font(Font.SANS_SERIF,Font.PLAIN,currentFontSize);//name style size
//				WindowManager.updateFont(f);
				//save properties 
				comboBox_laf.setSelectedItem(laf.getShortName(defaultLAF));
				currentLAF = defaultLAF.toString();//copy
				laf.setLookAndFeel(currentLAF);
				WindowManager.updateLookAndFeels(laf);
			}
		});
		
		chckbxOn.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RefreshQRTreeTableOn, "true");
					WindowManager.getMainScreen().qrAutoRefreshOn = true;
					TreeTableDockManager dttm = WindowManager.getMainScreen().getTreeTableDockManager();
					dttm.startRefreshQRTableTimer();
				} else {
					PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RefreshQRTreeTableOn, "false");
					WindowManager.getMainScreen().qrAutoRefreshOn = false;
					TreeTableDockManager dttm = WindowManager.getMainScreen().getTreeTableDockManager();
					dttm.stopRefreshQRTableTimer();
				}
			}
		});
		
		chckbxIgnore.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.IgnoreNullSearchKeyWarning, "true");
				} else {
					PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.IgnoreNullSearchKeyWarning, "false");
				}
			}
		});
	}
}
