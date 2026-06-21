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

import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.anonymize.PixelAnonymizerDialog;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.radiomics.RadiomicsWindow;
import com.vis.core.search.DicomTagExtractorDialog;
import com.vis.core.search.SeriesConditionExtractorDialog;
import com.vis.core.ui.dialog.BurnerWindow;
import com.vis.core.ui.dialog.DicomExporter;
import com.vis.core.ui.dialog.DicomImporterDialog;
import com.vis.core.ui.dialog.DicomPostman;
import com.vis.core.ui.dialog.DicomTagsViewer;
import com.vis.core.ui.dialog.NonDicomImageImporter;
import com.vis.core.ui.function.DatabaseBrowser;
import com.vis.core.ui.function.DeleteImage;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.ui.settings.PreferencesWin;
import com.vis.core.util.Platform;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.Viewer2DScreen;

/**
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class MainScreenToolBar extends JToolBar {

	ArrayList<String> buttonLabels = new ArrayList<String>();
	ArrayList<String> keys = new ArrayList<>();

	int NEW_WIDTH = 48;
	int NEW_HEIGHT = 48;

	private enum Tool {
		Import, Export, BrowseDB, BurnCD, ImportNoneDcm, Delete, Metadata, Send,
		TagExtractor,
		SeriesExporter,
		Anonymizer,
		Radiomics,
		Viewer, Viewer3D, Settings;
	}

	public MainScreenToolBar() {
		loadButtons();
	}

	public void loadButtons() {
		removeAll();
		HashMap<Tool, ImageIcon> icons = initButtonList();
		for (Tool t : Tool.values()) {
			ImageIcon ic = icons.get(t);
			if (ic == null) {
				continue;
			}
			Image img = ic.getImage();
			Image newimg = img.getScaledInstance(NEW_WIDTH, NEW_HEIGHT, java.awt.Image.SCALE_SMOOTH);
			ImageIcon icon = new ImageIcon(newimg);
			JButton btn = new JButton(t.name(), icon);
			btn.setName(t.name());
			btn.setFocusPainted(true);
			btn.setVerticalTextPosition(SwingConstants.BOTTOM);
			btn.setHorizontalTextPosition(SwingConstants.CENTER);
			setAction(btn);
			add(btn);
		}
	}

	public HashMap<Tool, ImageIcon> initButtonList() {
		HashMap<Tool, ImageIcon> map = new HashMap<>();
		map.put(Tool.Import, Resources.MenuBarImportIcon.loadIconFromResource());
		map.put(Tool.Export, Resources.MenuBarExportIcon.loadIconFromResource());
		map.put(Tool.BrowseDB, Resources.MenuBarBrowseDBIcon.loadIconFromResource());
		if (Utils.isDebug) {
			map.put(Tool.BurnCD, Resources.MenuBarBurnCDIcon.loadIconFromResource());
		} else {
			if (Platform.getOS() == Platform.WINDOWS) {
				map.put(Tool.BurnCD, Resources.MenuBarBurnCDIcon.loadIconFromResource());
			}
		}
		map.put(Tool.ImportNoneDcm, Resources.MenuBarImportNoDcmIcon.loadIconFromResource());
		map.put(Tool.Delete, Resources.MenuBarDeleteIcon.loadIconFromResource());
		map.put(Tool.Metadata, Resources.MenuBarMetadataIcon.loadIconFromResource());
		map.put(Tool.Send, Resources.MenuBarSendIcon.loadIconFromResource());
//		map.put("query", "/icon" + sep + "ic_import_export_black_48dp.png");
		map.put(Tool.Viewer, Resources.MenuBarViewer2DIcon.loadIconFromResource());
		map.put(Tool.Radiomics, Resources.RadiomicsJIcon.loadIconFromResource());
		map.put(Tool.Anonymizer, Resources.MenuBarAnonymizer.loadIconFromResource());
		map.put(Tool.TagExtractor, Resources.MenuBarTagExtractor.loadIconFromResource());
		map.put(Tool.SeriesExporter, Resources.MenuBarConditionalSeriesExtractor.loadIconFromResource());//
		map.put(Tool.Settings, Resources.MenuBarSettingsIcon.loadIconFromResource());
		return map;
	}

	private void setAction(JButton btn) {
		Tool type = null;
		for (Tool t : Tool.values()) {
			if (t.name().equals(btn.getName())) {
				type = t;
				break;
			}
		}
		switch (type) {
		case Import:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					/*
					 * DO not use thread. Use EDT as-is.
					 */
					DicomImporterDialog fcd = new DicomImporterDialog(WindowManager.getMainScreen(), true);
					fcd.setLocationRelativeTo(WindowManager.getMainScreen());
					fcd.setVisible(true);
				}
			});
			break;
		case Export:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					/**
					 * only work when browsing on Home Dock.
					 */
					// DO NOT USE Thread in EDT
					MainScreen ms = WindowManager.getMainScreen();
					if (ms.isHomeTop()) {
						ArrayList<DICOMNode> selected = ms.getSelectedNode();
						DicomExporter export = new DicomExporter(selected);
						export.start();
						export.monitorTasks();
					} else {
						JOptionPane.showMessageDialog(ms,
								"You cannot export files from external dicom network node. Use 'HOME' instead.");
					}
				}
			});
			break;
		case ImportNoneDcm:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					//DO NOT USE Thread in EDT
					new NonDicomImageImporter(WindowManager.getMainScreen(), false);
				}
			});
			break;
		case Delete:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					MainScreen ms = WindowManager.getMainScreen();
					if (!ms.isHomeTop()) {
						JOptionPane.showMessageDialog(ms,
								Resources.i18n("MainScreenToolBar.error.requireHomeSelection"), Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
						return;
					}
					int res = JOptionPane.showConfirmDialog(ms, Resources.i18n("MainScreenToolBar.confirm.deleteRecords"), Resources.i18n("dialog.title.delete"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
					if (res == JOptionPane.OK_OPTION) {
						ArrayList<DICOMNode> selected = ms.getSelectedNode();
						// run with another thread. (no EDT thread.)
						new Thread(() -> {
							DeleteImage.deleteImages(selected);
						}).start();
					}
				}
			});
			break;
		case BrowseDB:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						new DatabaseBrowser();
					} catch (Exception e1) {
						e1.printStackTrace();
						return;
					}
				}
			});
			break;
		case BurnCD:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg) {
					MainScreen ms = WindowManager.getMainScreen();
					if (!ms.isHomeTop()) {
						JOptionPane.showMessageDialog(ms,
								"You can not burn files from an external dicom network node. Use on 'HOME' tab instead.");
						return;
					}
					File burnDestFileInTemp = Utils.createNewDirInTemp();
					ArrayList<DICOMNode> selected = ms.getSelectedNode();
					ArrayList<String[]> dcmFilesUIDs = WindowManager.getMainScreen().getLocalTreeTable()
							.createNoDuplicateImageList(selected);
					DicomExporter export = new DicomExporter();
					export.setShowExportResult(false);
					export.exportDICOM(burnDestFileInTemp, dcmFilesUIDs, false/* flat */, false/* decompress */,
							false/* with viewer */);
					/*
					 * exportDICOM method is synchronous.
					 * So, here simply run after it.
					 */
					SwingUtilities.invokeLater(() -> {
						new BurnerWindow(burnDestFileInTemp, false/* debug */);
					});
				}
			});
			break;
		case Metadata:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					MainScreen ms = WindowManager.getMainScreen();
					if (!ms.isHomeTop()) {
						JOptionPane.showMessageDialog(ms,
								"You cannot show metadata in external dicom network node. Use 'HOME' instead.");
						return;
					}
					ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
					if (selected == null || selected.size() < 1) {
						return;
					}
					DICOMNode focusNode = selected.get(0);
					new Thread(() -> {
						new DicomTagsViewer(focusNode);
					}).start();
				}
			});
			break;
		case Send:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					/*
					 * only allow the localtreetable
					 */
					MainScreen ms = WindowManager.getMainScreen();
					if (!ms.isHomeTop()) {
						JOptionPane.showMessageDialog(ms,
								"You cannot send files in external dicom network node. Use 'HOME' instead.");
						return;
					}
					ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
					new Thread(() -> {
						new DicomPostman(selected);
					}).start();
				}
			});
			break;
		case Viewer:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					MainScreen ms = WindowManager.getMainScreen();
					// カーソルを砂時計にする（GUI操作なのでThreadの外で即座に実行）
					ms.setCursor(new Cursor(Cursor.WAIT_CURSOR));

					new Thread(() -> {
						// 画像のロード（重い処理）
						Viewer2DScreen viewer = Viewer2DScreen.getInstance();
						if(viewer != null) {
							if(ms.isHomeTop()) {
								viewer.loadImagesOnStage();
							} else {
								viewer.loadImagesOnStageFromExternal();
							}
							// ロード完了後、画面の表示とカーソル戻し（GUI操作なのでinvokeLater）
							SwingUtilities.invokeLater(() -> {
								if(ms.isHomeTop()) {
									viewer.setVisible(true);
								}
								ms.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
							});
						} else {
							// エラー時も確実にカーソルを戻す
							SwingUtilities.invokeLater(() -> ms.setCursor(new Cursor(Cursor.DEFAULT_CURSOR)));
						}
					}).start();
				}
			});
			break;
		case Settings:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					/* if showing, show to top, else, create new window */
					Frame[] allFrames = Frame.getFrames();
					for (Frame fr : allFrames) {
						if (fr instanceof PreferencesWin) {
							if (fr.isShowing()) {
								fr.toFront();
								return;
							}
						}
					}
					PreferencesWin.getInstance().setVisible(true);
				}
			});
			break;
		case TagExtractor:
		    btn.addActionListener(e -> {
		        DicomTagExtractorDialog dialog = new DicomTagExtractorDialog(WindowManager.getMainScreen());
		        dialog.setVisible(true);
		    });
		    break;
		case SeriesExporter:
		    btn.addActionListener(e -> {
		        SeriesConditionExtractorDialog dialog = new SeriesConditionExtractorDialog(WindowManager.getMainScreen());
		        dialog.setVisible(true);
		    });
		    break;
		case Anonymizer:
		    btn.addActionListener(e -> {
		    	ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
		    	if(selected == null || selected.size() == 0) {
		    		Log.logger.log(Level.INFO, "No selected study node, cannot start Anonymizer.");
		    		return;
		    	}
		    	DICOMNode target = null;
		    	for(DICOMNode node : selected) {
		    		if(node.getLevel()==DICOMNode.STUDY) {
		    			target = node;
		    			break;
		    		}
		    	}
		    	PixelAnonymizerDialog dialog = new PixelAnonymizerDialog(WindowManager.getMainScreen(), target);
		        dialog.setVisible(true);
		    });
		    break;
		case Radiomics:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg) {
					ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
					if(selected == null || selected.size() == 0) {
					    Log.logger.log(Level.INFO, "No selected study node, cannot start Radiomics.");
					    return;
					}

					DICOMNode target = null;
					for(DICOMNode node : selected) {
					    if(node.getLevel() == DICOMNode.STUDY) {
					        target = node;
					        break;
					    }
					}

					// ターゲットとなるSTUDYノードが見つかった場合のみ処理
					if (target != null) {
					    // ★ここがポイント：元のオブジェクトを壊さないよう、SERIESレベルまでを安全にディープコピー（IMAGEは自動除外）
					    final DICOMNode study = target.cloneUpToLevel(DICOMNode.SERIES);
					    
					    SwingUtilities.invokeLater(() -> {
					        new RadiomicsWindow(study);
					    });
					}
				}
			});
			break;
		default:
		}
	}
}
