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

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JRadioButton;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.MainScreen;

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
/*
 * future work
 * 
 * 1.calculate features on praparat level and save it as csv
 * 2.settings of each features
 * 3.show parametric images and fusion view and saveAsNewSeries or saveAsTif
 * 4.Pipe to WEKA (already in radiomicsj.)
 *  * - work with color features
 * - work on whole Stack 
 * - change training image
 * - do probability output (accessible?) and define threshold
 */
	
	int mode;
	RadiomicsPanel panel;
	
	public static void main(String[] args) {
		new RadiomicsWindow();
	}
	
	public RadiomicsWindow() {
		buildGUI();
	}
	
	private void buildGUI() {
		panel = new RadiomicsPanel();
		add(panel, BorderLayout.CENTER);
		pack();
		if(WindowManager.getMainScreen() == null) {
			setLocationRelativeTo(null);
		}else {
			setLocationRelativeTo(WindowManager.getMainScreen());
		}
		setVisible(true);
	}
	
}
