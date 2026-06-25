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

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import com.vis.core.facade.ApplicationFacade;
import com.vis.core.log.Log;
import com.vis.core.plugin.PlugInCompiler;
import com.vis.core.plugin.PluginShelf;
import com.vis.core.ui.dialog.HelpDialog;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class ViewerMenu extends JMenuBar {
	
	JMenu pluginMenu;

	public ViewerMenu() {
		setLayout(new FlowLayout(FlowLayout.LEADING));
		setMenu();
	}

	private void setMenu() {
		JMenu mnFile = new JMenu("File");
		add(mnFile);
		JMenuItem mntmSaveNewSeries = new JMenuItem("Save as new series");
		mntmSaveNewSeries.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Viewer2DScreen own = Viewer2DScreen.getInstance();
				DatabaseHandler db = DatabaseHandler.getInstance();
				if(own == null || db == null) {
					Log.logger.info("Ouch, viewer or database is null...");
					return;
				}
				ArrayList<Praparat>  selectedPraps = own.getSelectedPraps();
				for(Praparat pp : selectedPraps) {
					try {
						db.storeDicomImagesToDb(pp.getDicomImages());
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
			}
		});
		mnFile.add(mntmSaveNewSeries);
		
		JMenu mnImage = new JMenu("Image");
		add(mnImage);
		JMenuItem mntmWWWL = new JMenuItem("Adjust contrast");
		mntmWWWL.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Viewer2DScreen own = Viewer2DScreen.getInstance();
				if(own == null) {
					Log.logger.info("Ouch, viewer is null...");
					return;
				}
				ArrayList<Praparat> selectedPraps = own.getSelectedPraps();
				for(Praparat pp : selectedPraps) {
					try {
						WwWlAdjusterDialog.showDialog(pp, own);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					//do just first pp.
					break;
				}
			}
		});
		mnImage.add(mntmWWWL);

		JMenuItem mntmCurvedMpr = new JMenuItem("Curved MPR...");
		mntmCurvedMpr.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Viewer2DScreen own = Viewer2DScreen.getInstance();
				if(own == null) {
					Log.logger.info("Ouch, viewer is null...");
					return;
				}
				ArrayList<Praparat> selectedPraps = own.getSelectedPraps();
				for(Praparat pp : selectedPraps) {
					try {
						CurvedMprDialog.showDialog(pp, own);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					//do just first pp.
					break;
				}
			}
		});
		mnImage.add(mntmCurvedMpr);

		JMenu mnProcess = new JMenu("Process");
		add(mnProcess);
		JMenuItem mntmHistogram = new JMenuItem("Histogram...");
		mntmHistogram.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Viewer2DScreen own = Viewer2DScreen.getInstance();
				if(own == null) {
					Log.logger.info("Ouch, viewer is null...");
					return;
				}
				ArrayList<Praparat> selectedPraps = own.getSelectedPraps();
				for(Praparat pp : selectedPraps) {
					try {
						HistogramDialog.showDialog(pp, own);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					//do just first pp.
					break;
				}
			}
		});
		mnProcess.add(mntmHistogram);

		pluginMenu = new JMenu("Plugins");
		add(pluginMenu);
		updatePluginsMenuItem();
		
		JMenu mnHelp = new JMenu("Help&Contact");
		add(mnHelp);
		JMenuItem mntmHelp = new JMenuItem("Requests");
		mntmHelp.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				HelpDialog help = new HelpDialog();
				help.setLocationRelativeTo(Viewer2DScreen.getInstance());
			}
		});
		mnHelp.add(mntmHelp);
		
		
		
//		JMenuItem mntmDelete = new JMenuItem("Delete");
//		mntmDelete.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent arg0) {
//				ArrayList<DICOMNode> selected = ApplicationContext.getInstance().getMainScreen().getSelectedNode();
//				DeleteImage.deleteImages(selected);
//			}
//		});
//		mnFile.add(mntmDelete);
//		
//		JMenuItem debugMenuItem = new JMenuItem("Debug");
//		debugMenuItem.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent arg0) {
////				DatabaseHandler db = DatabaseHandler.getRunningInstance();
////				String sopUid = DicomUtilities.getStudyInstanceUID("/home/tatsunidas/.GRAPHY/archive/DICOM/681BADCF/153D4BCF/5B13D698");
////				System.out.println(sopUid);
////				java.util.HashMap<String,String> patInfo = db.findPatientRecordByPatID("LGG-104");
////				if(patInfo != null) {
////					for(String key:patInfo.keySet()) {
////						System.out.println(patInfo.get(key));
////					}
////				}
////				new DicomExporter().createNoDuplicateImageListToExport(ApplicationContext.getInstance().mainScreen.getSelectedNode());
//			}
//		});
//		mnFile.add(debugMenuItem);
//
//		JMenu mnNetwork = new JMenu("Network");
//		add(mnNetwork);
//
//		JMenu mnEdit = new JMenu("Edit");
//		add(mnEdit);
//
//		JMenu mnFormat = new JMenu("Format");
//		add(mnFormat);
//
//		JMenu mnd2dviewer = new JMenu("2DViewer");
//		add(mnd2dviewer);
//
//		JMenu mnd3dviewer = new JMenu("3DViewer");
//		add(mnd3dviewer);
//
//		JMenu mnRoi = new JMenu("ROI");
//		add(mnRoi);
//
//		JMenu mnOpenrecent = new JMenu("OpenRecent");
//		add(mnOpenrecent);
//
//		JMenu mnWindow = new JMenu("Window");
//		add(mnWindow);
//
//		JMenu mnPrefs = new JMenu("Preferences");
//		add(mnPrefs);
//
//		JMenuItem mntmSettings = new JMenuItem("Settings");
//		mntmSettings.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				/* if showing, show to top, else, create new window */
//				Frame[] allFrames = Frame.getFrames();
//				for (Frame fr : allFrames) {
//					String specificFrameName = fr.getClass().getName();
//					if (specificFrameName.equals("com.vis.environment.PreferencesWin")) {
//						// close the frame
//						if (fr.isShowing()) {
//							fr.toFront();
//							return;
//						}
//					}
//				}
//				new PreferencesWin();
//			}
//		});
//		mnPrefs.add(mntmSettings);
	}
	
	public void addPluginMenuItem(JMenuItem pluginMenuItem) {
		this.pluginMenu.add(pluginMenuItem);
	}
	
	void addCompileItem() {
		JMenuItem pluginItem = new JMenuItem("Compile...");// getText() can get key name.
		pluginItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				PlugInCompiler.selectAndCompile();
			}
		});
		pluginMenu.add(pluginItem);
	}
	
	public void updatePluginsMenuItem(){
		
		if(ApplicationFacade.pluginShelf == null) {
			return;
		}
		this.pluginMenu.removeAll();//init menuItems
		
		/**
		 * remove from v0.0.18
		 */
		//First, add compile menu
//		addCompileItem();
		
		PluginShelf pluginShelf = ApplicationFacade.pluginShelf;
		java.util.HashMap<String,String> plugins = pluginShelf.getLoadedPluginNames();
		if(plugins.size() < 1) {
			return;
		}
		
		java.util.Set<String> keys = plugins.keySet();
		for (String key : keys) {
			JMenuItem pluginItem = new JMenuItem(key);// getText() can get key name.
			pluginItem.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					/*
					 * here, always run without args. TODO ??
					 */
					pluginShelf.runPlugIn(key, null);
				}
			});
			pluginMenu.add(pluginItem);
		}
	}
}
