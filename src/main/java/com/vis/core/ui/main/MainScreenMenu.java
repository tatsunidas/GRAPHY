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
package com.vis.core.ui.main;

import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.DicomExporter;
import com.vis.core.ui.dialog.DicomImporterDialog;
import com.vis.core.ui.dialog.HelpDialog;
import com.vis.core.ui.function.DeleteImage;
import com.vis.core.ui.function.PatientInfoEditor;
import com.vis.core.ui.function.SeriesIntegrator;
import com.vis.core.ui.function.SeriesSeparator;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;

/**
 * @author tatsunidas 
 */
@SuppressWarnings("serial")
public class MainScreenMenu extends JMenuBar{
	
	public MainScreenMenu() {
		setLayout(new FlowLayout(FlowLayout.LEADING));
		setMenu();
	}
	
	private boolean HOMEinAction() {
		MainScreen ms = WindowManager.getMainScreen();
		TreeTableDockManager dockManager = ms.getTreeTableDockManager();
		TabDock topDock = dockManager.getCurrentTopDockStayInTabbedPane();
		if(topDock == null /*floating*/) {
			return true;
		}
		if(topDock.getName().equals(TreeTableDockManager.homeTabName)) {
			return true;
		}
		return false;
	}

	private void setMenu() {
		
		JMenu fileMenu = new JMenu("File");
		add(fileMenu);
		// File menus
		JMenuItem importItem = new JMenuItem("Import");
		importItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(!HOMEinAction()) {
					JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "This action recquire selections from only HOME TreeTable.");
					return;
				}
				DicomImporterDialog fcd = new  DicomImporterDialog(WindowManager.getMainScreen(),true);
				fcd.setLocationRelativeTo(WindowManager.getMainScreen());
				fcd.setVisible(true);
			}
		});
		fileMenu.add(importItem);
		
		JMenuItem mntmExport = new JMenuItem("Export");
		mntmExport.setToolTipText("Export dicom files selected on HOME TreeTable.");
		mntmExport.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(!HOMEinAction()) {
					JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "This action recquire selections from only HOME TreeTable.");
					return;
				}
				Window win = WindowManager.getWindow(ConfigInfo.MainScreen.toString());
				if(win != null) {
					MainScreen main = (MainScreen)win;
					ArrayList<DICOMNode> selected = main.getSelectedNode();
					new DicomExporter(selected);
				}
			}
		});
		fileMenu.add(mntmExport);
		
		JMenuItem mntmDelete = new JMenuItem("Delete");
		mntmDelete.setToolTipText("Delete dicom files selected on HOME TreeTable.");
		mntmDelete.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(!HOMEinAction()) {
					JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "This action recquire selections from only HOME TreeTable.");
					return;
				}
				if(JOptionPane.showConfirmDialog(null, "Do you want to delete current selected images ?", "Delete images...", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION){
					Window win = WindowManager.getWindow(ConfigInfo.MainScreen.toString());
					if(win != null) {
						MainScreen main = (MainScreen)win;
						ArrayList<DICOMNode> selected = main.getSelectedNode();
						DeleteImage.deleteImages(selected);
					}
				}
			}
		});
		fileMenu.add(mntmDelete);
		
		JMenu dbMenu = new JMenu("Database");
		add(dbMenu);
		//Database items
		JMenuItem mntmSeparate = new JMenuItem("Separate series");
		mntmSeparate.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(!HOMEinAction()) {
					JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "This action recquire selections from only HOME TreeTable.");
					return;
				}
				new SeriesSeparator().separateSeries();
			}
		});
		dbMenu.add(mntmSeparate);
		
		JMenuItem mntmIntegrate = new JMenuItem("Integrate series");
		mntmIntegrate.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(!HOMEinAction()) {
					JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "This action recquire selections from only HOME TreeTable.");
					return;
				}
				new SeriesIntegrator().integrateSeries();
			}
		});
		dbMenu.add(mntmIntegrate);
		
		JMenuItem mntmPatientEdit = new JMenuItem("Edit patient information");
		mntmPatientEdit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(!HOMEinAction()) {
					JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "This action recquire selections from only HOME TreeTable.");
					return;
				}
				Window win = WindowManager.getWindow(ConfigInfo.MainScreen.toString());
				MainScreen main = (MainScreen) win;
				ArrayList<DICOMNode> selected = main.getSelectedNode();
				new PatientInfoEditor(selected);
			}
		});
		dbMenu.add(mntmPatientEdit);
		
		JMenu mnSys = new JMenu("System");
		add(mnSys);
		JMenuItem mntmSys = new JMenuItem("Show Log");
		mntmSys.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Log logWin = Log.getInstance();
				if(!logWin.isVisible()) {
					logWin.setVisible(true);
				}else {
					/*
					 * when already showing, show to top.
					 */
					logWin.setVisible(false);
					logWin.setVisible(true);
				}
				logWin.setLocationRelativeTo((JFrame)WindowManager.getMainScreen());
			}
		});
		mnSys.add(mntmSys);
		
		JMenu mnHelp = new JMenu("Help&Contact");
		add(mnHelp);
		JMenuItem mntmHelp = new JMenuItem("Requests");
		mntmHelp.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				new HelpDialog();
			}
		});
		mnHelp.add(mntmHelp);		
	}
}
