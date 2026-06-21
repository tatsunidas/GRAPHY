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

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;

@SuppressWarnings("serial")
public class PreferencesWin extends JFrame{
	
	private static JTabbedPane tabPane;
	private JScrollPane scrPane;
	ImageIcon settingsIcon;
	private static PreferencesWin prefWin;

	private PreferencesWin() {
		setContents();
		setTitle(ConfigInfo.PreferencesWinow.toString());
		setName(ConfigInfo.PreferencesWinow.toString());//for window manager
		settingsIcon = Resources.MenuBarSettingsIcon.loadIconFromResource();
		setIconImage(settingsIcon.getImage());
		setMaximumSize(new Dimension(150, 100));
		/* show window */
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(WindowManager.getMainScreen());
		pack();
		/*
		 * DISPOSE_ON_CLOSE destroys the native peer, but the static singleton
		 * reference below would otherwise keep pointing at this now-invalid
		 * Frame. A stale Frame left in Frame.getFrames() with no peer makes
		 * any later modal Dialog.setVisible(true) throw
		 * "IllegalArgumentException: Window must not be zero" while AWT
		 * walks all frames to set up modal blocking. Clear it on close so
		 * getInstance() creates a fresh window next time.
		 */
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				WindowManager.removeWindow(PreferencesWin.this);
				prefWin = null;
			}
		});
	}
	
	public static PreferencesWin getInstance() {
		if(prefWin == null) {
			prefWin = new PreferencesWin();
			WindowManager.addWindow(prefWin);
			return prefWin;
		}
		return prefWin;
	}

	private void setContents() {
		tabPane = new JTabbedPane();
		/* General */
		GeneralPrefs general = new GeneralPrefs();
		tabPane.add(general);
		tabPane.setIconAt(0, Resources.PrefsIcon.loadIconFromResource());
		/* PACS Nodes */
		PACSConnectionPrefs pacsPref = new PACSConnectionPrefs();
		tabPane.add(pacsPref);
		tabPane.setIconAt(1, Resources.PrefsPACSIcon.loadIconFromResource());

		/* Roi Prefs */
		/*
		 * deprecate from 2024/07/31
		 * ROI setting should be done with the ROI toolbar.
		 */
//		RoiPrefs roiPref = new RoiPrefs();
//		tabPane.add(roiPref);
//		tabPane.setIconAt(2, Resources.PrefsROIIcon.loadIconFromResource());
		
		scrPane = new JScrollPane(tabPane);
		getContentPane().add(scrPane);
		
	}
}
