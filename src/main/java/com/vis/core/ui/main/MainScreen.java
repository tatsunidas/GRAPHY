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

import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.ContextKey;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.log.Log;
import com.vis.core.ui.LookAndFeels;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.ui.main.dcmtreetable.DICOMNodeBuilder;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTableModel;
import com.vis.core.ui.main.dcmtreetable.TreeTableModel;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.dimse.DimseUtilities;

import javax.swing.JMenuBar;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceListener;

import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class MainScreen extends JFrame implements WindowListener, ComponentListener{

	// singleton
	private static MainScreen mainScreen;

	// debug
	boolean isDebug = Utils.isDebug;
	public boolean qrAutoRefreshOn = false;
	boolean isLocal;
	/* Main Explorer */
	private TreeTableDockManager tabDockManager;
	private DICOMTreeTable localTreeTable;// home treetable
	/* Main menu bar */
	MainScreenMenu mainMenuBar;
	/* Main ToolBar */
	MainScreenToolBar mainToolBar;
	/* Main search Bar */
	SearchToolBar searchToolBar;

	JToolBar treeTableDock;// dockable treetable
	BirdsEyeView bev;
	JSplitPane treeTbaleAndBirdsEyeSplitPane;
	public int progressValue = 0;

	public JPanel activeViewPanel;
	public JSplitPane seriesThumbnailSplit;
	private javax.swing.JLabel queryInfoLabel;

	/* drag and drop */
	DICOMNode draggedNode;

	DragSourceListener sourceListener;

	DragSource dragSource;

	Logger logger = Log.logger;
	
	public static boolean importing = false;
	
	/**
	 * singleton
	 */
	private MainScreen() {
		super(loadLastGraphicConfiguration());
		setSettings();
		setContents();
		setLastScreenState();
		loadLocalStudiesWhenStartingUp();
	}
	
	public static MainScreen getInstance() {
		if (mainScreen == null) {
			synchronized (MainScreen.class) {
				if (mainScreen == null) {
					mainScreen = new MainScreen();
				}
			}
		}
		return mainScreen;
	}
	private static GraphicsConfiguration loadLastGraphicConfiguration() {
		GraphicsDevice[] screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		String lastMainScreenDeviceID = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props.toString(), GraphyProp.MainScreenDeviceID.name());
		if(screenDevices != null && (lastMainScreenDeviceID != null && lastMainScreenDeviceID.length()!=0)) {
			for(GraphicsDevice gd:screenDevices) {
				if(gd.getIDstring().equals(lastMainScreenDeviceID)) {
					return gd.getDefaultConfiguration();
				}
			}
		}
		return null;
	}
	
	public static void setImportingState(boolean importing) {
		MainScreen.importing = importing;
	}
	
	public void clearPatientInfo() {
		bev.resetViews(true);
	}

	@Override
	public void componentHidden(ComponentEvent e) {}
	
	@Override
	public void componentMoved(ComponentEvent e) {}
	
	@Override
	public void componentResized(ComponentEvent e) {}
	
	@Override
	public void componentShown(ComponentEvent e) {
		if(bev !=null) {
			bev.resetViews(true);
		}
		if(treeTbaleAndBirdsEyeSplitPane !=null) {
			int h =treeTbaleAndBirdsEyeSplitPane.getHeight();
			treeTbaleAndBirdsEyeSplitPane.setDividerLocation(h-(h/2));
		}
	}
	
	public TreeTableDockManager getCurrentTreeTableManager() {
		return this.tabDockManager;
	}
	
	public DICOMTreeTable getLocalTreeTable() {
		return localTreeTable;
	}

	/**
	 * this is not toolbar
	 */
	public JMenuBar getMainMenuBar() {
		return this.mainMenuBar;
	}
	
	public SearchToolBar getMainSearchToolBar() {
		return this.searchToolBar;
	}
	
	public ArrayList<DICOMNode> getSelectedNode() {
		return localTreeTable.getSelectedNodes();
	}
	
	private void initTreeTables(){
		// Local/QR TreeTables Manager
		tabDockManager = new TreeTableDockManager();//TabbedPane
		/* Local(HOME) TreeTable */
		TreeTableModel treeTableModel = new DICOMTreeTableModel(new DICOMNode(true, new ArrayList<DICOMNode>()));
		localTreeTable = new DICOMTreeTable(treeTableModel,false,null);
		try {
			tabDockManager.addTreeTable(true, "HOME", localTreeTable);
		} catch (URISyntaxException e) {
			e.printStackTrace();
			try {
				ApplicationFacade.exitApp(Level.SEVERE, "Filed to construct MainTreeTable...");
			} catch (Throwable e1) {
				e1.printStackTrace();
			}
		}
		/* QR TreeTables */
		ArrayList<DicomCommunicationNode> servers = DatabaseHandler.getInstance().loadServerList();
		if(servers != null && !servers.isEmpty()) {
			//show top ? if no, set HOME to Top.
			String keepTopTitle = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.MainTreeTableKeepTopTitle);
			for(DicomCommunicationNode svr:servers) {
				//constructQRTreeTables
				boolean svrReady = DimseUtilities.echo(svr);
				if(!svrReady) {
					continue;
				}
				DICOMTreeTableModel qrTreeTableModel = new DICOMTreeTableModel(new QueryRetrieve().startQRTable(svr));
				DICOMTreeTable qrTreeTable = new DICOMTreeTable(qrTreeTableModel, true,svr);
				try {
					tabDockManager.addTreeTable(false, svr.getNickname(), qrTreeTable);
					if(keepTopTitle != null && !keepTopTitle.isEmpty()) {
						if (keepTopTitle.equals(svr.getNickname())) {
							tabDockManager.setTopTab(keepTopTitle);
							break;
						}
					}
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			}
			tabDockManager.startRefreshQRTableTimer();
		}
	}
	
	public void loadLocalStudiesBySearchKey() {
		HashMap<String, Object> keys = getMainSearchToolBar().getCurrentSearchConditions();
		DatabaseHandler db = DatabaseHandler.getInstance();
		@SuppressWarnings("unchecked")
		ArrayList<DefaultMutableTreeNode> selectedStudies = db.selectStudiesWithSearchKeysUsingPatName((String)keys.get("PatientID"), (String)keys.get("PatientName"), (String)keys.get("From"), (String)keys.get("To"),
				(ArrayList<String>)keys.get("Modalities"));
		if(selectedStudies == null) {
			selectedStudies = new ArrayList<>();
		}
		DICOMNodeBuilder builder = new DICOMNodeBuilder();
		DICOMNode newRoot = builder.buildRootNodeUsingTreeNodes(selectedStudies);
		this.tabDockManager.getHomeDock().updateTreeTable(newRoot);
	}
	
	/**
	 * do it starting-up
	 */
	private void loadLocalStudiesWhenStartingUp() {
		/*
		 * today query is default
		 */
		String patID = null;//anybody
		String from = QRHandler.getTodayString("/");
		String to = null;
		ArrayList<String> modalities = null;
		//study list
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			return;
		}
		ArrayList<DefaultMutableTreeNode> localStudies = db.selectStudiesWithSearchKeys(patID,from, to, modalities);
		//construct root dicom node
		DICOMNodeBuilder builder = new DICOMNodeBuilder();
		DICOMNode newRoot = builder.buildRootNodeUsingTreeNodes(localStudies);
		this.tabDockManager.getHomeDock().updateTreeTable(newRoot);
	}
	
	public void maximizeWindow() {
		if(!isVisible()) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setExtendedState(JFrame.MAXIMIZED_BOTH);
				toFront(); // brings to front without setAlwaysOnTop
				requestFocus();
			}
		});
	}

	// see QueryRetrieve
	public void refleshAnchorTreeTable() {
		getMainSearchToolBar().searchDBOnCurrentConditions();
	}
	
	/**
	 * reflesh laf
	 */
	public void refreshLookAndFeels() {
		LookAndFeels laf = ApplicationFacade.getLookAndFeels();
		laf.updateLookAndFeels(mainScreen);
	}
	
	private void saveCurrentScreenState() {
		String lastMainScreenDeviceID = getGraphicsConfiguration().getDevice().getIDstring();
		String lastMainScreenX = String.valueOf(getLocationOnScreen().x);
		String lastMainScreenY = String.valueOf(getLocationOnScreen().y);
		String lastMainScreenW = String.valueOf(getWidth());
		String lastMainScreenH = String.valueOf(getHeight());
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.MainScreenDeviceID, lastMainScreenDeviceID);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.MainScreenX, lastMainScreenX);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.MainScreenY, lastMainScreenY);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.MainScreenWidth, lastMainScreenW);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.MainScreenHeight, lastMainScreenH);
	}
	
	private void setContents() {
		//Menubar
		mainMenuBar = new MainScreenMenu();
		setJMenuBar(mainMenuBar);
		
		/*
		 * North Component
		 */
		JPanel mainNorthPanel = new JPanel();
		mainNorthPanel.setLayout(new BorderLayout());
		
		//Toolbar under to north panel
		mainToolBar = new MainScreenToolBar();
		mainNorthPanel.add(mainToolBar,BorderLayout.NORTH);
		
		/* SearchToolBar */
		searchToolBar = new SearchToolBar();
		mainNorthPanel.add(searchToolBar,BorderLayout.CENTER);
		add(mainNorthPanel, BorderLayout.NORTH);
		
		/*
		 * Center Component
		 */
		//treetable and bird's eye view
		treeTbaleAndBirdsEyeSplitPane = new JSplitPane();
		treeTbaleAndBirdsEyeSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		treeTbaleAndBirdsEyeSplitPane.setOneTouchExpandable(true);
		
		//treeTables
		initTreeTables();
		treeTbaleAndBirdsEyeSplitPane.setLeftComponent(tabDockManager);
		
		//birds eye view
		bev = new BirdsEyeView();
		treeTbaleAndBirdsEyeSplitPane.setRightComponent(bev);
		
		add(treeTbaleAndBirdsEyeSplitPane, BorderLayout.CENTER);
		
		/*
		 * South Component 
		 */
		//triangle bar
		TriangleBar statusBar = new TriangleBar();
		getContentPane().add(statusBar, BorderLayout.SOUTH);
		
		addWindowListener(this);
		addComponentListener(this);
	}
	
//	public synchronized void constructHomeTreeTable(DICOMNode root) {
//		/* 
//		 * table =treeTable has Adapter.
//		 * tree = treeTable.getTree() has TreeTableModel.
//		 */
//		//get current selected row
//		int[] selectedRows = localTreeTable.getSelectedRows();//using table no good	
//		//get already opened tree node locations
//		ArrayList<Integer> willExpand = localTreeTable.getExpandedRowsPos();
//		((DICOMTreeTableModel) localTreeTable.getTree().getModel()).setRoot((Object)root);
//		((DICOMTreeTableModel) localTreeTable.getTree().getModel()).reload(root);
//		TableColumnResizer.adjustColumnPreferredWidths(localTreeTable);
//		((DICOMTreeTableModelAdapter)localTreeTable.getModel()).fireTableDataChanged();
////		treeTable.repaint();
//		//re-expand tree nodes
//		for (int i = 0; i < willExpand.size(); i++) {
//			localTreeTable.getTree().expandRow(willExpand.get(i));
//		}
//		//re-select node
//		//table approach
//		for(int row:selectedRows) {
//			localTreeTable.changeSelection(row, 0, true, false);//row,col,toggle,extend
//			//treeTable.selectRow(selectedRows);//DO NOT USE
//		}
//		localTreeTable.setLastColumnOrder();
//	}
	
//	public synchronized void constructQRTreeTable(DICOMTreeTable qrTreeTable, DICOMNode root) {
//		((DICOMTreeTableModel) qrTreeTable.getTree().getModel()).setRoot((Object)root);
//		((DICOMTreeTableModel) qrTreeTable.getTree().getModel()).reload(root);
//		TableColumnResizer.adjustColumnPreferredWidths(qrTreeTable);
//		((DICOMTreeTableModelAdapter)qrTreeTable.getModel()).fireTableDataChanged();
//		/* set column order to same localTreeTable*/
//		qrTreeTable.setLastColumnOrder();
//	}
	
	private void setDefaultScreenLocation() {
		//first, set size
		setSize(new Dimension(1200, 700));
		setPreferredSize(new Dimension(1200, 700));
		setLocationRelativeTo(null);
		setBounds(getX(),getY(),1200,700);//important		
	}
	
	public void setInfoToPatientPanel(HashMap<String,String> infoset) {
		bev.setPatientInfo(infoset);
	}

	/**
	 * Perform before setVisible.
	 */
	private void setLastScreenState() {
		String lastMainScreenX = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.MainScreenX);
		String lastMainScreenY = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.MainScreenY);
		String lastMainScreenW = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.MainScreenWidth);
		String lastMainScreenH = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.MainScreenHeight);
		if(lastMainScreenX == null || lastMainScreenY == null || lastMainScreenW == null || lastMainScreenH == null) {
			setDefaultScreenLocation();
			return;
		}else if(lastMainScreenX.equals("") || lastMainScreenY.equals("") || lastMainScreenW.equals("") || lastMainScreenH.equals("")) {
			setDefaultScreenLocation();
			return;
		}
		int x = Integer.parseInt(lastMainScreenX);
		int y = Integer.parseInt(lastMainScreenY);
		setLocation(x, y);
		int w = Integer.parseInt(lastMainScreenW);
		int h = Integer.parseInt(lastMainScreenH);
		setPreferredSize(new Dimension(w, h));
		setBounds(x,y,w,h);//important

		/*
		 * do not perform here.
		 * setVisible(true);//see facade
		 */
	}
	
	private void setSettings() {
		setName(ConfigInfo.MainScreen.toString());
		setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		//set window title
		if (!isDebug) {
			setTitle(ConfigInfo.AppName.toString()+" "+ConfigInfo.Version.toString());
		} else {
			setTitle(ConfigInfo.AppName.toString()+" "+ConfigInfo.Version.toString()+" -debug mode-");
		}
		//set icon
		setIconImage(Resources.MainWindowIcon.loadIconFromResource().getImage());
	}

	public void setStudiesFound(String text) {
		queryInfoLabel.setText(text);
	}

	public void showImagesOnBirdsEye() {
		TreeTableDockManager ttdm = getCurrentTreeTableManager();
		if(!ttdm.getCurrentAnchorTitle().equals(TreeTableDockManager.homeTabName)) {
			return;
		}
		TabDock homeDock = ttdm.getHomeDock();
		DICOMTreeTable tt = homeDock.getDICOMTreeTable();
		ArrayList<DICOMNode> selectedNodes = tt.getSelectedNodes();
		if(selectedNodes == null) {
			return;
		}
		// if selected multiple studies, do not show images 
		int studyCount = 0;
		for(DICOMNode n : selectedNodes) {
			if(n.getLevel()==DICOMNode.STUDY) {
				studyCount++;
			}
		}
		if(studyCount > 1) {
			logger.fine("showImagesOnBirdsEye(): Can not show multi studies on Bird's eye view.");
			return;
		}
		ArrayList<String> selectedSeriesUIDs = new ArrayList<>();
//		ArrayList<String> selectedImageUIDs = new ArrayList<>();
		HashMap<String, ArrayList<String>> selectedImageUIDs = new HashMap<>();
		for(DICOMNode n : selectedNodes) {
			if(n.getLevel()==DICOMNode.SERIES) {
				selectedSeriesUIDs.add(n.getData(DICOMNode.SeriesInstanceUID));
			}
			if(n.getLevel()==DICOMNode.IMAGE) {
				if(selectedImageUIDs.get(n.getData(DICOMNode.SeriesInstanceUID))==null) {
					ArrayList<String> uids = new ArrayList<>();
					uids.add(n.getData(DICOMNode.SOPInstanceUID));
					selectedImageUIDs.put(n.getData(DICOMNode.SeriesInstanceUID), uids);
				}else {
					selectedImageUIDs.get(n.getData(DICOMNode.SeriesInstanceUID)).add(n.getData(DICOMNode.SOPInstanceUID));
				}
			}
		}
		String patID = selectedNodes.get(0).getData(DICOMNode.PatientID);
		String studyUID = selectedNodes.get(0).getData(DICOMNode.StudyInstanceUID);
		if(bev != null) {
			String currentShowingStudyUID = bev.getShowingStudyUID();
			if(currentShowingStudyUID == null || !currentShowingStudyUID.equals(studyUID)) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						tt.setCursor(new Cursor(Cursor.WAIT_CURSOR));
						bev.showImages(patID, studyUID, selectedSeriesUIDs, selectedImageUIDs);
						tt.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					}
				});
			}else if(currentShowingStudyUID.equals(studyUID)) {
				if(selectedSeriesUIDs.size() == 0 && selectedImageUIDs.size()==0) {
					return;
				}
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						tt.setCursor(new Cursor(Cursor.WAIT_CURSOR));
						bev.updateViews(patID, studyUID, selectedSeriesUIDs, selectedImageUIDs);
						tt.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					}
				});
			}
		}
	}

	public void showImagesOnBirdsEye(Praparat thumbnailedParap) {
		bev.showImagesFromThumbnailAction(thumbnailedParap);
	}
	
	/**
	 * The state of the ROI may change after editing in 2Dviewer; if a patient
	 * handled in 2DViewer was opened in BEV, reset it once.
	 * 
	 * @param patIDs
	 */
	public void resetBirdsEyeView(String[] patIDs) {
		HashMap<String,String> pInfo = bev.getPatientInfo();
		if(pInfo == null) {
			return;
		}
		for(String patID : patIDs) {
			if(patID.equals(pInfo.get(ContextKey.PatientID.name()))) {
				bev.resetViews(true);
			}
		}
	}

	public void updateQRTreeTables(){
		//get serverlist
		ArrayList<DicomCommunicationNode> servers = DatabaseHandler.getInstance().loadServerList();
		String keepTopTitle = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.MainTreeTableKeepTopTitle);
		for(DicomCommunicationNode svr:servers) {
			//constructQRTreeTables
			boolean svrReady = DimseUtilities.echo(svr);
			if(!svrReady) {
				//if existing on docks, remove it TODO
				continue;
			}
			//check already on tabDock
			boolean found = false;
			for(String nickname:tabDockManager.getNicknameCurrentDocks()) {
				if(nickname.toLowerCase().equals("home")) {
					continue;
				}
				if(nickname.equals(svr.getNickname())) {
					found = true;
					break;
				}
			}
			DICOMTreeTable qrTreeTable = null;
			if(found) {
				//update tree
				//get prev root node
				TabDock prevDock = tabDockManager.getParticularDock(svr.getNickname());
				DICOMTreeTable prevTreeTable = prevDock.getDICOMTreeTable();
				DICOMNode root = (DICOMNode) prevTreeTable.getTree().getModel().getRoot();
				//create new TabDock and set tabDockManager
				TreeTableModel model = new DICOMTreeTableModel(root);
				qrTreeTable = new DICOMTreeTable(model, true, svr);
			}else{//addNew
				DICOMTreeTableModel qrTreeTableModel = new DICOMTreeTableModel(new QueryRetrieve().startQRTable(svr));
				qrTreeTable = new DICOMTreeTable(qrTreeTableModel, true, svr);
				/* add or update Dock */
				try {
					tabDockManager.addTreeTable(false, svr.getNickname(), qrTreeTable);
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			}
			if(keepTopTitle.equals(svr.getNickname())){
				tabDockManager.setTopTab(keepTopTitle);
			}
		}
		// Add 20231003
		tabDockManager.startRefreshQRTableTimer();
		/*
		 * searchDBUsingThisConditions() updation ?? TODO
		 */
	}

	@Override
	public void windowActivated(WindowEvent e) {}

	@Override
	public void windowClosed(WindowEvent e) {}

	@Override
	public void windowClosing(WindowEvent e) {
		saveCurrentScreenState();
		//close all window(2d,3d, etc)
		/*
		 * 1. shutdown db
		 * 2. shutdown qrscp
		 * 3. delete tmp dir contents
		 * 4. system.exit(0)
		 */
		try {
			boolean close = ApplicationFacade.exitApp(Level.INFO, "Shutting down graphy...");
			if(!close) {
				return;
			}
		} catch (Throwable e1) {
			e1.printStackTrace();
			System.exit(13);
		}
	}

	@Override
	public void windowDeactivated(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {}

	@Override
	public void windowOpened(WindowEvent e) {}
}

