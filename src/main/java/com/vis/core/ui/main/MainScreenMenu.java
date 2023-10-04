package com.vis.core.ui.main;

import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URISyntaxException;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.LogWindow;
import com.vis.core.ui.dialog.DicomImporterDialog;
import com.vis.core.ui.dialog.HelpDialog;
import com.vis.core.ui.function.DeleteImage;
import com.vis.core.ui.function.PatientInfoEditor;
import com.vis.core.ui.function.SeriesIntegrator;
import com.vis.core.ui.function.SeriesSeparator;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;

//import com.vis.function.DeleteImage;
//import com.vis.function.PatientInfoEditor;
//import com.vis.function.SeriesIntegrator;
//import com.vis.function.SeriesSeparator;
//import com.vis.ui.form.dialog.DicomExporter;
//import com.vis.ui.form.dialog.DicomImporter;
//import com.vis.ui.form.dialog.DicomImporterDialog;

@SuppressWarnings("serial")
public class MainScreenMenu extends JMenuBar{
	
	public MainScreenMenu() {
		setLayout(new FlowLayout(FlowLayout.LEADING));
		setMenu();
	}

	private void setMenu() {
		
		JMenu fileMenu = new JMenu("File");
		add(fileMenu);
		// File menus
		JMenuItem importItem = new JMenuItem("Import");
		importItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				// TODO Auto-generated method stub
				DicomImporterDialog fcd = new  DicomImporterDialog(WindowManager.getMainScreen(),true);
				fcd.setLocationRelativeTo(WindowManager.getMainScreen());
				fcd.setVisible(true);
			}
		});
		fileMenu.add(importItem);
		
		JMenuItem mntmExport = new JMenuItem("Export");
		mntmExport.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Window win = WindowManager.getWindow(ConfigInfo.MainScreen.toString());
				if(win != null) {
					MainScreen main = (MainScreen)win;
					ArrayList<DICOMNode> selected = main.getSelectedNode();
					//TODO 20230823
//					new DicomExporter(selected);
				}
			}
		});
		fileMenu.add(mntmExport);
		
		JMenuItem mntmDelete = new JMenuItem("Delete");
		mntmDelete.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
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
				new SeriesSeparator().separateSeries();
			}
		});
		dbMenu.add(mntmSeparate);
		
		JMenuItem mntmIntegrate = new JMenuItem("Integrate series");
		mntmIntegrate.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				new SeriesIntegrator().integrateSeries();
			}
		});
		dbMenu.add(mntmIntegrate);
		
		JMenuItem mntmPatientEdit = new JMenuItem("Edit patient information");
		mntmPatientEdit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Window win = WindowManager.getWindow(ConfigInfo.MainScreen.toString());
				if(win != null) {
					MainScreen main = (MainScreen)win;
					ArrayList<DICOMNode> selected = main.getSelectedNode();
					new PatientInfoEditor(selected);
				}
			}
		});
		dbMenu.add(mntmPatientEdit);
		
		JMenu mnSys = new JMenu("System");
		add(mnSys);
		JMenuItem mntmSys = new JMenuItem("Show Log");
		mntmSys.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				LogWindow logWin = LogWindow.getInstance();
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
				com.vis.core.ui.dialog.HelpDialog help = null;
				try {
					help = new HelpDialog();
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				help.setLocationRelativeTo(WindowManager.getMainScreen());
			}
		});
		mnHelp.add(mntmHelp);
		

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
}
