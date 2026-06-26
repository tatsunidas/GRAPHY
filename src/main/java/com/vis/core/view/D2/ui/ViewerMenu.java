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

	// Praparat currently in segmentation edit mode, so "Stop editing" works even if
	// the series is no longer the toolbar selection by the time it is clicked.
	private Praparat segmentationEditingPraparat;

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
		JMenuItem segImport = new JMenuItem(Resources.i18n("ViewerMenu.seg.import"));
		segImport.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				importRoisToSegmentationOnSelected();
			}
		});
		JMenuItem segSave = new JMenuItem(Resources.i18n("ViewerMenu.seg.saveSeg"));
		segSave.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				saveSegmentationAsSegOnSelected();
			}
		});
		JMenuItem segImportSeg = new JMenuItem(Resources.i18n("ViewerMenu.seg.importSeg"));
		segImportSeg.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				importSegFromFile();
			}
		});
		mnSeg.add(segNew);
		mnSeg.add(segImport);
		mnSeg.add(segSave);
		mnSeg.add(segImportSeg);
		mnSeg.add(segStop);
		// Fail-safe: enable/disable items by the current session state each time it opens,
		// enforcing the New -> edit -> Stop bracket.
		mnSeg.addMenuListener(new javax.swing.event.MenuListener() {
			@Override
			public void menuSelected(javax.swing.event.MenuEvent e) {
				updateSegmentationMenuState(segNew, segImport, segSave, segImportSeg, segStop);
			}
			@Override
			public void menuDeselected(javax.swing.event.MenuEvent e) {}
			@Override
			public void menuCanceled(javax.swing.event.MenuEvent e) {}
		});
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

		JMenu mnReport = new JMenu(Resources.i18n("Reporting.menu"));
		add(mnReport);
		JMenuItem mntmNewReport = new JMenuItem(Resources.i18n("Reporting.menu.new"));
		mntmNewReport.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Viewer2DScreen own = Viewer2DScreen.getInstance();
				if (own == null) {
					return;
				}
				ArrayList<Praparat> sel = own.getSelectedPraps();
				if (sel == null || sel.isEmpty()) {
					JOptionPane.showMessageDialog(own, Resources.i18n("ViewerMenu.info.noPraparatSelected"));
					return;
				}
				Object[] uids = sel.get(0).getUIDs();
				com.vis.core.reporting.ui.ReportEditorDialog.showNew(own, (String) uids[0], (String) uids[1], null,
						null);
			}
		});
		mnReport.add(mntmNewReport);

		JMenuItem mntmReports = new JMenuItem(Resources.i18n("Reporting.menu.reports"));
		mntmReports.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Viewer2DScreen own = Viewer2DScreen.getInstance();
				if (own == null) {
					return;
				}
				// Reports are study-scoped: list every study currently OPEN in the viewer
				// (no Praparat selection required).
				java.util.List<String[]> studies = collectOpenStudies(own);
				if (studies.isEmpty()) {
					JOptionPane.showMessageDialog(own, Resources.i18n("Reporting.list.noStudyOpen"));
					return;
				}
				com.vis.core.reporting.ui.ReportListPanel panel = new com.vis.core.reporting.ui.ReportListPanel();
				panel.setStudies(studies);
				javax.swing.JDialog d = new javax.swing.JDialog(own,
						Resources.i18n("Reporting.window.reports.title"), false);
				d.setContentPane(panel);
				d.setSize(760, 440);
				d.setLocationRelativeTo(own);
				d.setVisible(true);
			}
		});
		mnReport.add(mntmReports);

		JMenuItem mntmExportMeas = new JMenuItem(Resources.i18n("Reporting.menu.exportMeasurements"));
		mntmExportMeas.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Viewer2DScreen own = Viewer2DScreen.getInstance();
				if (own == null) {
					return;
				}
				ArrayList<Praparat> sel = own.getSelectedPraps();
				if (sel == null || sel.isEmpty()) {
					JOptionPane.showMessageDialog(own, Resources.i18n("ViewerMenu.info.noPraparatSelected"));
					return;
				}
				// extract on the EDT (reads live ROI/image state), then store off the EDT
				final com.vis.core.reporting.measurement.MeasurementReport report = com.vis.core.reporting.measurement.MeasurementExtractor
						.fromPraparat(sel.get(0), null);
				if (report.getGroups().isEmpty()) {
					JOptionPane.showMessageDialog(own, Resources.i18n("Reporting.measurements.none"));
					return;
				}
				new javax.swing.SwingWorker<String, Void>() {
					@Override
					protected String doInBackground() {
						return new com.vis.core.reporting.ReportService().finalizeMeasurementsAsSR(report);
					}

					@Override
					protected void done() {
						String sopUID = null;
						try {
							sopUID = get();
						} catch (Exception ex) {
							Log.logger.warning("Export measurements as SR failed: " + ex.getMessage());
						}
						if (sopUID != null) {
							// Re-stamp the tree Report column: ReportState is computed only at
							// tree-build time, so rebuild the local studies tree to reflect the new SR.
							com.vis.core.ui.main.MainScreen ms = com.vis.core.ui.main.MainScreen.getInstance();
							if (ms != null) {
								try {
									ms.loadLocalStudiesBySearchKey();
								} catch (Exception ex) {
									Log.logger.warning("Tree refresh after measurement SR export failed: "
											+ ex.getMessage());
								}
							}
						}
						JOptionPane.showMessageDialog(own,
								Resources.i18n(sopUID != null ? "Reporting.measurements.done"
										: "Reporting.measurements.failed"));
					}
				}.execute();
			}
		});
		mnReport.add(mntmExportMeas);

		JMenu mnView = new JMenu(Resources.i18n("MainScreenMenu.menu.view"));
		add(mnView);
		JMenuItem mntmCompare = new JMenuItem(Resources.i18n("MainScreenMenu.view.compare"));
		mntmCompare.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Viewer2DScreen own = Viewer2DScreen.getInstance();
				if (own == null) {
					return;
				}
				java.util.List<com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext> contexts = null;
				StageDockManager sdm = own.getStageDockManager();
				if (sdm != null) {
					StageView active = sdm.getStage(own.getStageIDInAction());
					if (active != null && active.getEyepiece() != null) {
						contexts = active.getEyepiece().getAllPraparatContext();
					}
				}
				ComparisonScreen.getInstance().launch(ComparisonScreen.studiesFromPraparats(contexts));
			}
		});
		mnView.add(mntmCompare);

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
		segmentationEditingPraparat = pp;
		PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
				Resources.i18n("ViewerMenu.seg.editingStarted"),
				JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Roi2Mask: imports the currently selected 2D/3D ROIs of the selected Praparat
	 * into its active segmentation (creating one if none is active). The source ROIs
	 * are left intact.
	 */
	private void importRoisToSegmentationOnSelected() {
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
		// Selected 2D ROIs (across slices) plus any selected 3D ROIs.
		java.util.List<com.vis.core.view.D2.roi.RoiObj> selectedRois = new ArrayList<>(pp.getSelectedRois());
		if (pp.getRoi3DList() != null) {
			for (com.vis.core.view.D2.roi.RoiObj r : pp.getRoi3DList()) {
				if (r != null && r.isSelected() && !selectedRois.contains(r)) {
					selectedRois.add(r);
				}
			}
		}
		if (selectedRois.isEmpty()) {
			PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
					Resources.i18n("ViewerMenu.seg.selectRoisFirst"),
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		com.vis.core.view.D3.roi.FreeFormRoi3D target = pp.getActiveSegmentation();
		if (target == null) {
			String name = JOptionPane.showInputDialog(own,
					Resources.i18n("ViewerMenu.seg.namePrompt"),
					Resources.i18n("ViewerMenu.menu.segmentation"), JOptionPane.PLAIN_MESSAGE);
			if (name == null) {
				return;
			}
			target = com.vis.core.view.D3.roi.SegmentationManager.createSegmentation(pp, name);
			if (target == null) {
				PopUpMessage.showDialog(own, Resources.i18n("dialog.title.error"),
						Resources.i18n("ViewerMenu.seg.createFailed"),
						JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
				return;
			}
			pp.setActiveSegmentation(target);
			segmentationEditingPraparat = pp;
		}
		com.vis.core.view.D3.roi.SegmentationManager.importRoisIntoSegmentation(pp, selectedRois, target, null);
		// Persist the updated mask.
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null) {
			try {
				db.insertRoi(target.readContext());
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		com.vis.core.view.D2.ui.glasses.SlideGlass cur = pp.getCurrentSlide();
		if (cur != null) {
			cur.repaintCanvasGlass();
		}
		PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
				String.format(Resources.i18n("ViewerMenu.seg.imported"), selectedRois.size()),
				JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Writes all segmentation objects of the selected Praparat as a single
	 * multi-segment DICOM SEG series and ingests it into the database.
	 */
	private void saveSegmentationAsSegOnSelected() {
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
		java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> segs = com.vis.core.view.D3.roi.SegmentationManager
				.getSegmentations(pp);
		if (segs.isEmpty()) {
			PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
					Resources.i18n("ViewerMenu.seg.noSegToSave"),
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		try {
			com.vis.dicom.DicomObject seg = com.vis.dicom.seg.SegWriter.build(pp, segs);
			if (seg == null) {
				PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
						Resources.i18n("ViewerMenu.seg.noSegToSave"),
						JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			String patID = seg.getString(0x00100020);
			String studyUID = seg.getString(0x0020000D);
			String seriesUID = seg.getString(0x0020000E);
			String sopUID = seg.getString(0x00080018);
			java.io.File tmp = java.io.File.createTempFile("graphy_seg_", ".dcm");
			if (!com.vis.dicom.seg.SegWriter.writeToFile(seg, tmp.getAbsolutePath())) {
				tmp.delete();
				PopUpMessage.showDialog(own, Resources.i18n("dialog.title.error"),
						Resources.i18n("ViewerMenu.seg.saveFailed"),
						JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
				return;
			}
			// Ingest the SEG file into the local DB (deletes the temp file afterward).
			com.vis.dicom.dimse.DimseUtilities.store(tmp.getAbsolutePath(), true);

			// Refresh the HOME tree table so the new SEG series appears in the database.
			com.vis.core.ui.main.MainScreen main = com.vis.core.facade.WindowManager.getMainScreen();
			if (main != null) {
				main.loadLocalStudiesBySearchKey();
			}

			// Load and show the saved SEG in the 2D viewer (works even while still editing).
			// The SEG is anchored to the reference slice grid, so it loads as a regular
			// (slices x channels) series.
			try {
				own.loadImagesOnStage(patID, studyUID, seriesUID,
						(sopUID != null ? new String[] { sopUID } : null), null);
				own.setVisible(true);
				own.toFront();
			} catch (Exception ex2) {
				ex2.printStackTrace();
			}

			PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
					Resources.i18n("ViewerMenu.seg.savedSeg"),
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			ex.printStackTrace();
			PopUpMessage.showDialog(own, Resources.i18n("dialog.title.error"),
					Resources.i18n("ViewerMenu.seg.saveFailed"),
					JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Mask2Roi from DICOM: reads a BINARY SEG file and attaches its segments to the
	 * selected reference Praparat as editable FreeFormRoi3D objects.
	 */
	private void importSegFromFile() {
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
		javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
		if (fc.showOpenDialog(own) != javax.swing.JFileChooser.APPROVE_OPTION) {
			return;
		}
		java.io.File file = fc.getSelectedFile();
		try {
			com.vis.dicom.DicomReader reader = com.vis.dicom.DicomReader
					.newDicomReader(com.vis.dicom.DICOMBackend.getCurrent());
			reader.read(file.getAbsolutePath(), true);

			// (A) Frame-of-reference check: warn if the SEG belongs to a different study/
			// region than the selected series (0020,0052 = FrameOfReferenceUID).
			com.vis.core.view.D2.ui.glasses.SlideGlass refSg = pp.getFirstNoEmptySlide();
			String refFoR = (refSg != null && refSg.getHeader() != null)
					? refSg.getHeader().getString(0x00200052) : null;
			String segFoR = reader.getHeader().getString(0x00200052);
			if (refFoR != null && segFoR != null && !refFoR.equals(segFoR)) {
				int res = JOptionPane.showConfirmDialog(own,
						Resources.i18n("ViewerMenu.seg.importForMismatch"),
						Resources.i18n("ViewerMenu.menu.segmentation"),
						JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (res != JOptionPane.YES_OPTION) {
					return;
				}
			}

			java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> all = com.vis.dicom.seg.SegReader
					.read(reader.getHeader());
			if (all.isEmpty()) {
				PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
						Resources.i18n("ViewerMenu.seg.importSegNone"),
						JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			// (A) Keep only segments that physically overlap the selected series
			// (tolerates slice thickness / gap / FOV differences via nearest-slice mapping).
			java.util.List<double[]> refIpps = com.vis.core.view.D3.roi.SegmentationManager.referenceSliceIpps(pp);
			java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> kept = new ArrayList<>();
			int skipped = 0;
			for (com.vis.core.view.D3.roi.FreeFormRoi3D s : all) {
				if (refIpps.isEmpty() || com.vis.core.view.D3.roi.SegmentationManager.overlapsReference(s, refIpps)) {
					kept.add(s);
				} else {
					skipped++;
				}
			}
			if (kept.isEmpty()) {
				PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
						Resources.i18n("ViewerMenu.seg.importNoOverlap"),
						JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			DatabaseHandler db = DatabaseHandler.getInstance();
			for (com.vis.core.view.D3.roi.FreeFormRoi3D s : kept) {
				pp.addRoi3D(s);
				if (db != null) {
					try {
						db.insertRoi(s.readContext());
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
			com.vis.core.view.D2.ui.glasses.SlideGlass cur = pp.getCurrentSlide();
			if (cur != null) {
				cur.repaintCanvasGlass();
			}
			String msg = (skipped > 0)
					? String.format(Resources.i18n("ViewerMenu.seg.importedWithSkip"), kept.size(), skipped)
					: String.format(Resources.i18n("ViewerMenu.seg.importSegDone"), kept.size());
			PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"), msg,
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			ex.printStackTrace();
			PopUpMessage.showDialog(own, Resources.i18n("dialog.title.error"),
					Resources.i18n("ViewerMenu.seg.importSegFailed"),
					JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Leaves segmentation edit mode and confirms to the user. */
	private void stopSegmentationOnSelected() {
		Viewer2DScreen own = Viewer2DScreen.getInstance();
		// Prefer the tracked editing Praparat; fall back to the current selection.
		Praparat pp = segmentationEditingPraparat;
		if ((pp == null || !pp.isSegmentationEditing()) && own != null) {
			ArrayList<Praparat> sel = own.getSelectedPraps();
			if (sel != null && !sel.isEmpty() && sel.get(0).isSegmentationEditing()) {
				pp = sel.get(0);
			}
		}
		if (pp == null || !pp.isSegmentationEditing()) {
			PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
					Resources.i18n("ViewerMenu.seg.notEditing"),
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		pp.setActiveSegmentation(null);
		segmentationEditingPraparat = null;
		// Repaint so the de-selected mask is redrawn in its normal (segment) color.
		com.vis.core.view.D2.ui.glasses.SlideGlass cur = pp.getCurrentSlide();
		if (cur != null) {
			cur.repaintCanvasGlass();
		}
		PopUpMessage.showDialog(own, Resources.i18n("ViewerMenu.menu.segmentation"),
				Resources.i18n("ViewerMenu.seg.editingStopped"),
				JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Fail-safe enable/disable for the segmentation menu, enforcing the editing
	 * bracket on the selected Praparat:
	 * <ul>
	 * <li>New / Import DICOM SEG: only when a series is selected and NOT editing.</li>
	 * <li>Save: only when not editing and segmentations exist (finish editing first).</li>
	 * <li>Roi2Mask import / Stop editing: only while editing (active mask required).</li>
	 * </ul>
	 */
	private void updateSegmentationMenuState(JMenuItem newItem, JMenuItem importRois, JMenuItem save,
			JMenuItem importSeg, JMenuItem stop) {
		Praparat pp = null;
		try {
			Viewer2DScreen own = Viewer2DScreen.getInstance();
			if (own != null) {
				ArrayList<Praparat> sel = own.getSelectedPraps();
				if (sel != null && !sel.isEmpty()) {
					pp = sel.get(0);
				}
			}
		} catch (Exception e) {
			pp = null;
		}
		boolean hasPp = pp != null;
		boolean editing = hasPp && pp.isSegmentationEditing();
		boolean hasSegs = hasPp && !com.vis.core.view.D3.roi.SegmentationManager.getSegmentations(pp).isEmpty();

		newItem.setEnabled(hasPp && !editing);
		importSeg.setEnabled(hasPp && !editing);
		importRois.setEnabled(editing);
		// Save is allowed even while editing (no need to press Stop first).
		save.setEnabled(hasPp && hasSegs);
		stop.setEnabled(editing);
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

	/**
	 * The distinct studies currently open across all stages of the 2D viewer, as
	 * {@code {patID, studyUID, studyDate(null)}} rows. Reports are study-scoped, so the
	 * Reports list targets these regardless of which Praparat (if any) is selected.
	 */
	private static java.util.List<String[]> collectOpenStudies(Viewer2DScreen own) {
		java.util.LinkedHashMap<String, String[]> distinct = new java.util.LinkedHashMap<>();
		StageDockManager sdm = own.getStageDockManager();
		if (sdm != null) {
			String[] patients = sdm.getAllPatientList();
			if (patients != null) {
				for (String pid : patients) {
					StageView sv = sdm.getStage(pid);
					if (sv == null || sv.getEyepiece() == null) {
						continue;
					}
					java.util.List<com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext> ctxs = sv
							.getEyepiece().getAllPraparatContext();
					if (ctxs == null) {
						continue;
					}
					for (com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext ctx : ctxs) {
						Object[] u = ctx.getContextUIDs();
						if (u == null || u[1] == null) {
							continue;
						}
						distinct.putIfAbsent(u[0] + "|" + u[1], new String[] { (String) u[0], (String) u[1], null });
					}
				}
			}
		}
		return new java.util.ArrayList<>(distinct.values());
	}
}
