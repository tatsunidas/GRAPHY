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
import javax.swing.JOptionPane;

import com.vis.configuration.Resources;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.log.Log;
import com.vis.core.plugin.PlugInCompiler;
import com.vis.core.plugin.PluginShelf;
import com.vis.core.ui.dialog.HelpDialog;
import com.vis.core.ui.dialog.PopUpMessage;
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
				// The adjuster only makes sense for a selected Praparat. If none is
				// selected, tell the user how to select one (Shift + left-click).
				if (selectedPraps == null || selectedPraps.isEmpty()) {
					PopUpMessage.showDialog(own,
							Resources.i18n("dialog.title.information"),
							Resources.i18n("ViewerMenu.info.noPraparatSelected"),
							JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
					return;
				}
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

		// Representative WW/WL presets. The submenu is rebuilt every time it is
		// opened so it always reflects the latest values from graphy.properties.
		JMenu mnPreset = new JMenu(Resources.i18n("ViewerMenu.menu.presets"));
		populatePresetMenu(mnPreset);
		mnPreset.addMenuListener(new javax.swing.event.MenuListener() {
			@Override
			public void menuSelected(javax.swing.event.MenuEvent e) {
				populatePresetMenu(mnPreset);
			}
			@Override
			public void menuDeselected(javax.swing.event.MenuEvent e) {}
			@Override
			public void menuCanceled(javax.swing.event.MenuEvent e) {}
		});
		mnImage.add(mnPreset);

		// Series sorting (InstanceNumber / spatial Z-axis, each reversible).
		// Rebuilt on open so spatial items can be disabled for MPEG video series.
		JMenu mnSort = new JMenu(Resources.i18n("ViewerMenu.menu.sort"));
		populateSortMenu(mnSort);
		mnSort.addMenuListener(new javax.swing.event.MenuListener() {
			@Override
			public void menuSelected(javax.swing.event.MenuEvent e) {
				populateSortMenu(mnSort);
			}
			@Override
			public void menuDeselected(javax.swing.event.MenuEvent e) {}
			@Override
			public void menuCanceled(javax.swing.event.MenuEvent e) {}
		});
		mnImage.add(mnSort);

		// Segmentation: minimal entry to create and edit a binary mask object.
		// (The full manager UI lives in RoiObjManager.)
		JMenu mnSeg = new JMenu(Resources.i18n("ViewerMenu.menu.segmentation"));
		JMenuItem segNew = new JMenuItem(Resources.i18n("ViewerMenu.seg.new"));
		segNew.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newSegmentationOnSelected();
			}
		});
		JMenuItem segStop = new JMenuItem(Resources.i18n("ViewerMenu.seg.stop"));
		segStop.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				stopSegmentationOnSelected();
			}
		});
		mnSeg.add(segNew);
		mnSeg.add(segStop);
		mnImage.add(mnSeg);

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

	/**
	 * (Re)builds the WW/WL preset submenu from graphy.properties. Each preset item
	 * applies the preset to the selected Praparat; the last item opens the editor.
	 */
	private void populatePresetMenu(JMenu mnPreset) {
		mnPreset.removeAll();
		for (final WwWlPresets.WwWlPreset preset : WwWlPresets.loadPresets()) {
			JMenuItem item = new JMenuItem(preset.toString());
			item.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					applyPresetToSelected(preset);
				}
			});
			mnPreset.add(item);
		}
		mnPreset.addSeparator();
		JMenuItem editItem = new JMenuItem(Resources.i18n("ViewerMenu.preset.edit"));
		editItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				WwWlPresets.showDialog(Viewer2DScreen.getInstance());
			}
		});
		mnPreset.add(editItem);
	}

	/**
	 * Applies the given preset to the first selected Praparat. If none is selected,
	 * tells the user how to select one (Shift + left-click).
	 */
	private void applyPresetToSelected(WwWlPresets.WwWlPreset preset) {
		Viewer2DScreen own = Viewer2DScreen.getInstance();
		if (own == null) {
			Log.logger.info("Ouch, viewer is null...");
			return;
		}
		ArrayList<Praparat> selectedPraps = own.getSelectedPraps();
		if (selectedPraps == null || selectedPraps.isEmpty()) {
			PopUpMessage.showDialog(own,
					Resources.i18n("dialog.title.information"),
					Resources.i18n("ViewerMenu.info.noPraparatSelected"),
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		for (Praparat pp : selectedPraps) {
			try {
				WwWlPresets.applyPreset(pp, preset);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			// do just first pp.
			break;
		}
	}

	/**
	 * (Re)builds the series Sort submenu. Spatial (Z-axis) items are disabled for
	 * MPEG video series, which have no IPP and support only InstanceNumber order.
	 */
	private void populateSortMenu(JMenu mnSort) {
		mnSort.removeAll();

		// Decide whether spatial sort is applicable based on the selected Praparat.
		boolean spatialEnabled = true;
		Viewer2DScreen own = Viewer2DScreen.getInstance();
		if (own != null) {
			ArrayList<Praparat> sel = own.getSelectedPraps();
			if (sel != null && !sel.isEmpty() && sel.get(0).isMpegVideoSeries()) {
				spatialEnabled = false;
			}
		}

		addSortItem(mnSort, Resources.i18n("ViewerMenu.sort.instanceAsc"),
				Praparat.SeriesSortMode.INSTANCE_ASC, true);
		addSortItem(mnSort, Resources.i18n("ViewerMenu.sort.instanceDesc"),
				Praparat.SeriesSortMode.INSTANCE_DESC, true);
		mnSort.addSeparator();
		addSortItem(mnSort, Resources.i18n("ViewerMenu.sort.positionAsc"),
				Praparat.SeriesSortMode.SPATIAL_Z_ASC, spatialEnabled);
		addSortItem(mnSort, Resources.i18n("ViewerMenu.sort.positionDesc"),
				Praparat.SeriesSortMode.SPATIAL_Z_DESC, spatialEnabled);
	}

	private void addSortItem(JMenu mnSort, String label, final Praparat.SeriesSortMode mode, boolean enabled) {
		JMenuItem item = new JMenuItem(label);
		item.setEnabled(enabled);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				applySortToSelected(mode);
			}
		});
		mnSort.add(item);
	}

	/**
	 * Applies the chosen sort mode to the first selected Praparat. If none is
	 * selected, tells the user how to select one (Shift + left-click).
	 */
	private void applySortToSelected(Praparat.SeriesSortMode mode) {
		Viewer2DScreen own = Viewer2DScreen.getInstance();
		if (own == null) {
			Log.logger.info("Ouch, viewer is null...");
			return;
		}
		ArrayList<Praparat> selectedPraps = own.getSelectedPraps();
		if (selectedPraps == null || selectedPraps.isEmpty()) {
			PopUpMessage.showDialog(own,
					Resources.i18n("dialog.title.information"),
					Resources.i18n("ViewerMenu.info.noPraparatSelected"),
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		for (Praparat pp : selectedPraps) {
			try {
				pp.applySortMode(mode);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			// do just first pp.
			break;
		}
	}

	/**
	 * Creates a new segmentation object on the selected Praparat and enters edit
	 * mode (drawing tools then paint into its mask; hold Alt to erase).
	 */
	private void newSegmentationOnSelected() {
		Viewer2DScreen own = Viewer2DScreen.getInstance();
		if (own == null) {
			Log.logger.info("Ouch, viewer is null...");
			return;
		}
		ArrayList<Praparat> sel = own.getSelectedPraps();
		if (sel == null || sel.isEmpty()) {
			PopUpMessage.showDialog(own, Resources.i18n("dialog.title.information"),
					Resources.i18n("ViewerMenu.info.noPraparatSelected"),
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Praparat pp = sel.get(0);
		String name = JOptionPane.showInputDialog(own,
				Resources.i18n("ViewerMenu.seg.namePrompt"),
				Resources.i18n("ViewerMenu.menu.segmentation"), JOptionPane.PLAIN_MESSAGE);
		if (name == null) {
			return; // cancelled
		}
		com.vis.core.view.D3.roi.FreeFormRoi3D seg = com.vis.core.view.D3.roi.SegmentationManager
				.createSegmentation(pp, name);
		if (seg == null) {
			PopUpMessage.showDialog(own, Resources.i18n("dialog.title.error"),
					Resources.i18n("ViewerMenu.seg.createFailed"),
					JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
			return;
		}
		pp.setActiveSegmentation(seg);
		PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
				Resources.i18n("ViewerMenu.seg.editingStarted"),
				JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
	}

	/** Leaves segmentation edit mode on the selected Praparat. */
	private void stopSegmentationOnSelected() {
		Viewer2DScreen own = Viewer2DScreen.getInstance();
		if (own == null) {
			return;
		}
		ArrayList<Praparat> sel = own.getSelectedPraps();
		if (sel == null || sel.isEmpty()) {
			return;
		}
		sel.get(0).setActiveSegmentation(null);
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
