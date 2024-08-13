package com.vis.core.view.D2.ui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URISyntaxException;
import java.util.ArrayList;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import com.vis.core.ui.dialog.HelpDialog;
import com.vis.core.view.D2.ui.glasses.Praparat;

//import com.vis.dimse.delegate.DicomDuplicator;//TODO 20231006


@SuppressWarnings("serial")
public class ViewerMenu extends JMenuBar {
	
	JMenu pluginMenu;

	public ViewerMenu() {
		setLayout(new FlowLayout(FlowLayout.LEADING));
		setMenu();
	}

	private void setMenu() {
		
		// TODO Auto-generated method stub
		JMenu mnFile = new JMenu("File");
		add(mnFile);

		JMenuItem mntmSaveNewSeries = new JMenuItem("Save as new series");
		mntmSaveNewSeries.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Viewer2DScreen own = Viewer2DScreen.getInstance();
				if(own == null) {
					return;
				}
				ArrayList<Praparat>  selectedPraps = own.getSelectedPraps();
				for(Praparat pp:selectedPraps) {
					try {
						/*
						 * TODO 20231006
						 */
//						DicomDuplicator.createNewSeriesAndStore2DB(pp, false, false);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
			}
		});
		mnFile.add(mntmSaveNewSeries);
		
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
		
//		JMenuItem mntmExport = new JMenuItem("Export");
//		mntmExport.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent arg0) {
//				// TODO Auto-generated method stub
//				ArrayList<DICOMNode> selected = ApplicationContext.getInstance().getMainScreen().getSelectedNode();
//				new DicomExporter(selected);
//			}
//		});
//		mnFile.add(mntmExport);
//		
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
//		JMenu mnPlugins = new JMenu("Plugins");
//		add(mnPlugins);
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
	
	public void updatePluginsMenuItem(){
		if(this.pluginMenu == null) {
			return;
		}
		/*
		 * TODO 20231006
		 */
//		if(ApplicationContext.pluginShelf == null) {
//			return;
//		}
//		PluginShelf pluginShelf = ApplicationContext.pluginShelf;
//		java.util.HashMap<String,String> plugins = pluginShelf.getLoadedPluginNames();
//		if(plugins.size() < 1) {
//			return;
//		}
//		this.pluginMenu.removeAll();//init menuItems
//		java.util.Set<String> keys = plugins.keySet();
//		for (String key : keys) {
//			JMenuItem pluginItem = new JMenuItem(key);// getText() can get key name.
//			pluginItem.addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent e) {
//					/*
//					 * here, always run without args. TODO ??
//					 */
//					ApplicationContext.pluginShelf.runPlugIn(key, null);
//				}
//			});
//			pluginMenu.add(pluginItem);
//		}
	}
}
