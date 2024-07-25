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
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;

@SuppressWarnings("serial")
public class PreferencesWin extends JFrame{
	
	private final static String name = "Preferences";
	private static JTabbedPane tabPane;
	private JScrollPane scrPane;
	ImageIcon settingsIcon;
	private static PreferencesWin prefWin = new PreferencesWin();

	private PreferencesWin() {
		
		setContents();
		setTitle(name);
		setName(name);//for window manager
		settingsIcon = Resources.MenuBarSettingsIcon.loadIconFromResource();
		setIconImage(settingsIcon.getImage());
		setMaximumSize(new Dimension(150, 100));
		/* show window */
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(WindowManager.getMainScreen());
		pack();
	}
	
	public static PreferencesWin getInstance() {
		return prefWin;
	}

	private void setContents() {
		tabPane = new JTabbedPane();
		/* construct panels */
		/* General */
		GeneralPrefs general = new GeneralPrefs();
		tabPane.add(general);
		tabPane.setIconAt(0, Resources.PrefsIcon.loadIconFromResource());
		/* PACS Nodes */
		PACSConnectionPrefs pacsPref = new PACSConnectionPrefs();
		tabPane.add(pacsPref);
		tabPane.setIconAt(1, Resources.PrefsPACSIcon.loadIconFromResource());

		/* Roi Prefs */
		RoiPrefs roiPref = new RoiPrefs();
		tabPane.add(roiPref);
		tabPane.setIconAt(2, Resources.PrefsROIIcon.loadIconFromResource());
		scrPane = new JScrollPane(tabPane);
		getContentPane().add(scrPane);
		
	}
}
