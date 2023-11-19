package com.vis.core.ui.main;

import java.awt.Frame;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.ui.dialog.DicomExporter;
import com.vis.core.ui.dialog.DicomImporterDialog;
import com.vis.core.ui.dialog.NonDicomImageImporter;
import com.vis.core.ui.function.DatabaseBrowser;
import com.vis.core.ui.function.DeleteImage;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.Platform;
import com.vis.core.util.Utils;

//import com.vis.ui.form.dialog.BurnerWindow;
//import com.vis.function.DatabaseBrowser2;
//import com.vis.function.DeleteImage;
//import com.vis.resource.GraphyIcon;
//import com.vis.resource.Resource;
//import com.vis.ui.context.ApplicationContext;
//import com.vis.ui.dcmtreetable.DICOMNode;
//import com.vis.ui.environment.PreferencesWin;
//import com.vis.ui.form.dialog.DicomExporter;
//import com.vis.ui.form.dialog.DicomImporterDialog;
//import com.vis.ui.form.dialog.DicomPostman;
//import com.vis.ui.form.dialog.DicomTagsViewer;
//import com.vis.ui.form.dialog.NonDicomImageImporter;
//import com.vis.viewer2d.ui.frame.Viewer2DFrame;

/**
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class MainScreenToolBar extends JToolBar {

	ArrayList<String> buttonLabels = new ArrayList<String>();
	ArrayList<String> keys = new ArrayList<>();
	
	int NEW_WIDTH = 32;
	int NEW_HEIGHT = 32;
	
	private enum Tool{
		Import,
		Export,
		BrowseDB,
		BurnCD,
		ImportNoneDcm,
		Delete,
		Metadata,
		Send,
//		Query,//do not need
		Viewer,
		Viewer3D,
		Settings;
	}

	public MainScreenToolBar() {
		loadButtons();
	}

	public void loadButtons() {
		removeAll();
		HashMap<Tool,ImageIcon> icons = initButtonList();
		for(Tool t:Tool.values()) {
			ImageIcon ic = icons.get(t);
			if(ic == null) {continue;}
			Image img = ic.getImage();
			Image newimg = img.getScaledInstance( NEW_WIDTH, NEW_HEIGHT,  java.awt.Image.SCALE_SMOOTH );
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
		if(Utils.isDebug) {
			map.put(Tool.BurnCD, Resources.MenuBarBurnCDIcon.loadIconFromResource());
		}else {
			if(Platform.getCurrentPlatform()==Platform.WINDOWS) {
				map.put(Tool.BurnCD, Resources.MenuBarBurnCDIcon.loadIconFromResource());
			}
		}
		map.put(Tool.ImportNoneDcm, Resources.MenuBarImportNoDcmIcon.loadIconFromResource());
		map.put(Tool.Delete, Resources.MenuBarDeleteIcon.loadIconFromResource());
		map.put(Tool.Metadata,  Resources.MenuBarMetadataIcon.loadIconFromResource());
		map.put(Tool.Send, Resources.MenuBarSendIcon.loadIconFromResource());
////		map.put("query", "/icon" + sep + "ic_import_export_black_48dp.png");
		map.put(Tool.Viewer, Resources.MenuBarViewer2DIcon.loadIconFromResource());
		map.put(Tool.Viewer3D, Resources.MenuBarViewer3DIcon.loadIconFromResource());
		map.put(Tool.Settings, Resources.MenuBarSettingsIcon.loadIconFromResource());
		return map;
	}

	private void setAction(JButton btn) {
		Tool type = null;
		for(Tool t:Tool.values()) {
			if(t.name().equals(btn.getName())) {
				type = t;
				break;
			}
		}
		switch (type) {
		case Import:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
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
					 * From Only Home Dock.
					 */
					ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
					new DicomExporter(selected);
				}
			});
			break;
		case ImportNoneDcm:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					new NonDicomImageImporter();
				}
			});
			break;
		case Delete:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					int res = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Delete selected records from DB ?");
					if(res == JOptionPane.OK_OPTION) {
						ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
						DeleteImage.deleteImages(selected);
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
					//target dir including dicom file
					//graphy_tmp/target_dir
					File burnFileInTemp = new File("");
					//create dicomdir file
					
					//TODO 20230906
//					new BurnerWindow(burnFileInTemp, false);
				}
			});
			break;
		case Metadata:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					/*
					 * only allow localtreetable
					 */
					ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
					if (selected == null || selected.size() < 1) {
						return;
					}
					DICOMNode focusNode = selected.get(0);
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							// TODO 20230906
//							new DicomTagsViewer(focusNode);
						}
					});
				}
			});
			break;
		case Send:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					/*
					 * only allow localtreetable
					 */
					ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							//TODO 20230906
//							new DicomPostman(selected);
						}
					});
				}
			});
			break;
		case Viewer:
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					
					//TODO 20230901
					
//					Viewer2DFrame viewer = mediator.getViewer2DFrame();
//					if(viewer != null) {
//						SwingUtilities.invokeLater(new Runnable() {
//							public void run() {
//								viewer.loadImagesOnSatge();
//							}
//						});
//					}
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
						String specificFrameName = fr.getClass().getName();
						if (specificFrameName.equals("com.vis.environment.PreferencesWin")) {
							// close the frame
							if (fr.isShowing()) {
								fr.toFront();
								return;
							}
						}
					}
					// TODO 20230906
//					new PreferencesWin();
				}
			});
		default:
		}
	}
}
