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
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;

import io.github.tatsunidas.radiomics.main.RadiomicsJ;

/**
 * 
 * 1. radiomics classifier
 * - dataset is built-up from roi by roi.
 * 
 * 2. radiomics segmentation
 * - dataset is built-up from pixel by pixel.
 * 
 * @author tatsunidas
 *
 */
public class RadiomicsWindow extends JFrame{
/**
	 * 
	 */
	private static final long serialVersionUID = -8494940884028066246L;
/*
 * future work
 * 
 * 1.calculate features on praparat level and save it as csv
 * 2.settings of each features
 * 3.show parametric images and fusion view and saveAsNewSeries or saveAsTif
 * 4.Pipe to WEKA.
 * - work with color features
 * - work on whole Stack 
 * - change training image
 * - output probability and define threshold to mask
 */
	
	static RadiomicsJ radiomics = new RadiomicsJ();
	RadiomicsPanel panel;
	RadiomicsSettings settings;
	
	public static void main(String[] args) {
		new RadiomicsWindow();
	}
	
	public RadiomicsWindow() {
		buildGUI();
		
	}
	
	private void buildGUI() {
		JTabbedPane tabPane = new JTabbedPane();
		panel = new RadiomicsPanel();
		tabPane.addTab("Operation",panel);
		settings = new RadiomicsSettings();
		tabPane.addTab("Settings", settings);
		add(tabPane, BorderLayout.CENTER);
		pack();
		if(WindowManager.getMainScreen() == null) {
			setLocationRelativeTo(null);
		}else {
			setLocationRelativeTo(WindowManager.getMainScreen());
		}
		setTitle("Machine Learning");
		setIconImage(Resources.RadiomicsJIcon.loadIconFromResource().getImage());
		setSize(900, 600);
		setVisible(true);
		settings.adjustDividerLocation();
	}
	
	public void loadSettings(Properties prop) {
		try {
			List<String> settingsItem = SettingsContext.getStringFieldValues();
			for(String p : settingsItem){
				switch(p) {
					case SettingsContext.D3Basis:
						break;
					case SettingsContext.RangFiltering:
						break;
					//add more
					default:
						//do nothing
				}
			}
			Log.logger.log(Level.INFO, "All radiomics properties are loaded.");
//			System.out.println("All radiomics properties are loaded.");
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
