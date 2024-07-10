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
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;

import com.vis.configuration.ConfigInfo;
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
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
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

	// debug
	boolean isDebug = Utils.isDebug;
	//singleton
	private static final MainScreen mainScreen = new MainScreen();
	
	public static boolean importing = false;
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
	JToolBar treeTableDock;//dockable treetable
	BirdsEyeView bev;
	JSplitPane treeTbaleAndBirdsEyeSplitPane;
	
	// Variables
//	private ArrayList<String> serverLabels = new ArrayList<String>();
//	private JPopupMenu preferencesPopup;
//	private JMenuItem preferencesItem, resetItem, importItem;
	public int progressValue = 0;
	public JPanel activeViewPanel;
	public JSplitPane seriesThumbnailSplit;
	private javax.swing.JLabel queryInfoLabel;
	
	/* drag and drop */
	DICOMNode draggedNode;
	DragSourceListener sourceListener;
	DragSource dragSource;
	
	Logger logger = Log.logger;

	/**
	 * keep singleton
	 */
	private MainScreen() {
		super(loadLastGraphicConfiguration());
		setSettings();
		setContents();
		setLastScreenState();
		initAppDefaults();
	}
	
	public static MainScreen getInstance() {
		return MainScreen.mainScreen;
	}
	
	private static GraphicsConfiguration loadLastGraphicConfiguration() {
		GraphicsDevice[] screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		String lastMainScreenDeviceID = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props.toString(), GraphyProp.MainScreenDeviceID.name());
		if(screenDevices != null && (lastMainScreenDeviceID != null && !lastMainScreenDeviceID.equals(""))) {
			for(GraphicsDevice gd:screenDevices) {
				if(gd.getIDstring().equals(lastMainScreenDeviceID)) {
					return gd.getDefaultConfiguration();
				}
			}
		}
		return null;
	}
	
	public JMenuBar getMainMenuBar() {//attention, this is not toolbar
		return this.mainMenuBar;
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
	
	public void setInfoToPatientPanel(HashMap<String,String> infoset) {
		bev.setPatientInfo(infoset);
	}
	
	public void clearPatientInfo() {
		bev.resetViews(true);
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
	
	private void setDefaultScreenLocation() {
		//first, set size
		setSize(new Dimension(1200, 700));
		setPreferredSize(new Dimension(1200, 700));
		setLocationRelativeTo(null);
		setBounds(getX(),getY(),1200,700);//important		
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
				/*
				 * Or...
				 */
//				GraphicsConfiguration gcon = ApplicationContext.getInstance().getMainScreenGraphicsConfiguration();
//				int screen_width = gcon.getBounds().width;
//		       int screen_height = gcon.getBounds().height;
//				setLocation(0, 0);
//				setSize(screen_width, screen_height));
//				setBounds(0,0,screen_width, screen_height);
			}
		});
	}

	public DICOMTreeTable getLocalTreeTable() {
		return localTreeTable;
	}

	private void initAppDefaults() {
		loadLocalStudiesWhenStartingUp();//create treetable and count studies
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
					if(keepTopTitle != null && !keepTopTitle.isBlank()) {
						if (keepTopTitle.equals(svr.getNickname())) {
							tabDockManager.setTopTab(keepTopTitle);
							break;
						}
					}
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			}
			tabDockManager.setAndStartRefreshQRTableTimer();
		}
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
			if(Utils.isDebug) {
				logger.info("showImagesOnBirdsEye(): Can not show multi studies on Bird's eye view.");
			}
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
		tabDockManager.setAndStartRefreshQRTableTimer();
		/*
		 * searchDBUsingThisConditions() updation ?? TODO
		 */
	}
	
	public TreeTableDockManager getCurrentTreeTableManager() {
		return this.tabDockManager;
	}
	
	public SearchToolBar getMainSearchToolBar() {
		return this.searchToolBar;
	}

	public void addNewServer(String MODE, String serverName) {
//		if(MODE.equals("LOCAL")) {
//			dbListScrollPaneLocal.addServer(serverName);
//		}else if(MODE.equals("EXTSERVER")) {
//			dbListScrollPaneExtServers.addServer(serverName);
//		}
	}

//	public JSplitPane constructSplitPaneWithPreview() {
//		ImagePreviewPanel imagePreviewPanel = new ImagePreviewPanel();
//		TreeTable treeTab = new TreeTable();
//		treeTab.addMouseListener(new TreeTableMouseListener(treeTab, imagePreviewPanel));
//		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, imagePreviewPanel, new JScrollPane(treeTab));
//		imagePreviewPanel.setMinimumSize(new Dimension(270, 0));
//		return splitPane;
//	}

	public void setStudiesFound(String text) {
		queryInfoLabel.setText(text);
	}
	
	/*
	 * 使わないほうが良い。
	 * クエリ条件を使う
	 */
//	@Deprecated
//	public void loadAllLocalStudies() {
//		//study list
//		ArrayList<DICOMNode> localStudies = ApplicationContext.getInstance().getDatabaseRef().listAllLocalStudies();
//		for (int i = 0; i < localStudies.size(); i++) {
//			//series list in study
//			ArrayList<DICOMNode> seriesList = ApplicationContext.getInstance().getDatabaseRef()
//					.getSeriesList(localStudies.get(i).getData("PatientID"), localStudies.get(i));
//			localStudies.get(i).setChildren(seriesList);
//		}
//		constructTreeTable(new DICOMNode(true,localStudies));
//	}
	
	/**
	 * do it starting-up
	 */
	public void loadLocalStudiesWhenStartingUp() {
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
	
	// see QueryRetrieve
	public void refleshAnchorTreeTable() {
		getMainSearchToolBar().searchDBOnCurrentConditions();
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
	
	public ArrayList<DICOMNode> getSelectedNode() {
		return localTreeTable.getSelectedNodes();
	}
	
//	public ImagePreviewPanel getCurrentImagePreviewPanel() {
//		if (serverTab.getSelectedIndex() == 0) {
//			return ((ImagePreviewPanel) ((JSplitPane) serverTab.getSelectedComponent()).getLeftComponent());
//		}
//		return null;
//	}

//	public void removeAllPreviewsOfImagePreviewPanel() {
//		try {
//			((ImagePreviewPanel) ((JSplitPane) ((JSplitPane) serverTab.getSelectedComponent()).getRightComponent())
//					.getLeftComponent()).resetImagePreviewPanel();
//		} catch (ClassCastException cce) {
//			ApplicationContext.logger.log(Level.INFO, "MainScreen", cce.getMessage());
//		}
//	}

	// To filter the studies
	//tatsu, comment out
	public void loadMatchingStudies() {
//		String pid = String.valueOf(
//				ApplicationContext.currentTreeTable.getValueAt(ApplicationContext.currentTreeTable.getSelectedRow(), 2))
//				.trim();
//		String pname = String.valueOf(
//				ApplicationContext.currentTreeTable.getValueAt(ApplicationContext.currentTreeTable.getSelectedRow(), 3))
//				.trim();
//		String dob = String.valueOf(
//				ApplicationContext.currentTreeTable.getValueAt(ApplicationContext.currentTreeTable.getSelectedRow(), 4))
//				.trim();
//		String accNo = String.valueOf(
//				ApplicationContext.currentTreeTable.getValueAt(ApplicationContext.currentTreeTable.getSelectedRow(), 5))
//				.trim();
//		String studyDate = String.valueOf(
//				ApplicationContext.currentTreeTable.getValueAt(ApplicationContext.currentTreeTable.getSelectedRow(), 6))
//				.trim();
//		String studyDesc = String.valueOf(
//				ApplicationContext.currentTreeTable.getValueAt(ApplicationContext.currentTreeTable.getSelectedRow(), 7))
//				.trim();
//		String modality = String.valueOf(
//				ApplicationContext.currentTreeTable.getValueAt(ApplicationContext.currentTreeTable.getSelectedRow(), 8))
//				.trim();
//
//		ArrayList<StudyNode> studies = ApplicationContext.databaseRef.listStudies("%" + pname.toUpperCase() + "%",
//				"%" + pid.toUpperCase() + "%", "%" + dob + "%", "%" + accNo.toUpperCase() + "%", "%" + studyDate + "%",
//				"%" + studyDesc.toUpperCase() + "%", "%" + modality.toUpperCase() + "%");
//
//		for (int i = 0; i < studies.size(); i++) {
//			ArrayList<SeriesNode> seriesList = ApplicationContext.databaseRef
//					.getSeriesList_SepMultiframe(studies.get(i).getStudyUID());
//			studies.get(i).setChildren(seriesList);
//		}
//
//		StudyNode root = new StudyNode(pid, pname, dob, accNo, studyDate, "", studyDesc, modality, "", "", "");
//		root.setChildren(studies, 0);
//		constructTreeTable(root);
	}

	public void addKeyEventDispatcher() {
		KeyEventDispatcher keyEventDispatcher = new KeyEventDispatcher() {
			@Override
			public boolean dispatchKeyEvent(KeyEvent e) {
				if (e.getID() == KeyEvent.KEY_TYPED && isLocal
						&& isFocused()) {
					keyEventProcessor(e);
				}
				boolean discardEvent = false;
				return discardEvent;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);
	}

	private void keyEventProcessor(KeyEvent e) {
		if (localTreeTable.getSelectedRow() == 0 && isFocused()) {
			int row = localTreeTable.getSelectedRow();
			int column = localTreeTable.getSelectedColumn();
			if (e.getKeyChar() == KeyEvent.VK_DELETE) {
				localTreeTable.setValueAt("", row, column);
			} else if (e.getKeyChar() == KeyEvent.VK_BACK_SPACE) {
				String str = (String) localTreeTable.getValueAt(row, column);
				try {
					localTreeTable.setValueAt(str.substring(0, str.length() - 1), row, column);
				} catch (StringIndexOutOfBoundsException sioobe) {
					Log.logger.severe(sioobe.getMessage());
				}
			} else {
				localTreeTable.setValueAt(
						(String) localTreeTable.getValueAt(row, column) + e.getKeyChar(), row,
						column);
			}
			mainScreen.loadMatchingStudies();
			localTreeTable.changeSelection(row, column, false, false);
		} else if (isFocused() && e.getKeyChar() == KeyEvent.VK_DELETE) { // To delete the selective studies
			int[] selectedRows = localTreeTable.getSelectedRows();
//			if (selectedRows.length > 0
//					&& JOptionPane
//							.showOptionDialog(rootPane,
//									ApplicationContext.currentBundle
//											.getString("MainScreen.deleteStudyConfirmation.text"),
//									ApplicationContext.currentBundle
//											.getString("MainScreen.deleteStudyConfirmation.title.text"),
//									JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
//									new String[] { ApplicationContext.currentBundle.getString("YesButtons.text"),
//											ApplicationContext.currentBundle.getString("NoButtons.text") },
//									"default") == 0) {
//				for (int i = 0; i < selectedRows.length; i++) {
//					String studyUid = (String) ((TreeTableModelAdapter) ApplicationContext.currentTreeTable.getModel())
//							.getValueAt(selectedRows[i], 10);
//
//					if (studyUid == null) {
//						int j = selectedRows[i];
//						while (studyUid == null) {
//							j--;
//							studyUid = (String) ((TreeTableModelAdapter) ApplicationContext.currentTreeTable.getModel())
//									.getValueAt(j, 10);
//						}
//					}
//					ApplicationContext.databaseRef
//							.deleteLocalStudy((String) ApplicationContext.currentTreeTable.getValueAt(i, 2), studyUid);
//					removeInViewer(studyUid);
//				}
//				loadlocalStudies();
//				if (getCurrentImagePreviewPanel().parent.getComponentCount() > 0) {
//					getCurrentImagePreviewPanel().resetImagePreviewPanel();
//				}
//			}
		}
	}

//	public void applyLocaleChange() {
//		ApplicationContext.applyLocaleChange();
//		this.setTitle(ApplicationContext.currentBundle.getString("MainScreen.title.text"));
//		serverTab.setTitleAt(0, ApplicationContext.currentBundle.getString("MainScreen.local.text"));
//		TreeTable treeTable;
//		SearchFilterForm filterForm = null;
//		for (int i = 0; i < serverTab.getTabCount(); i++) {
//			if (i != 0) {
//				filterForm = (SearchFilterForm) ((JSplitPane) serverTab.getComponentAt(i)).getTopComponent();
//				if (((JSplitPane) serverTab.getComponentAt(i)).getBottomComponent() instanceof JScrollPane) {
//					treeTable = ((TreeTable) ((JViewport) ((JScrollPane) ((JSplitPane) serverTab.getComponentAt(i))
//							.getBottomComponent()).getComponent(0)).getComponent(0));
//				} else {
//					treeTable = ((TreeTable) ((JViewport) ((JScrollPane) ((JSplitPane) ((JSplitPane) serverTab
//							.getComponentAt(i)).getBottomComponent()).getRightComponent()).getComponent(0))
//									.getComponent(0));
//				}
//			} else {
//				treeTable = ((TreeTable) ((JViewport) ((JScrollPane) ((JSplitPane) serverTab.getComponentAt(i))
//						.getBottomComponent()).getComponent(0)).getComponent(0));
//			}
//			JTableHeader tableHeader = treeTable.getTableHeader();
//			TableColumnModel columnModel = tableHeader.getColumnModel();
//			if (columnModel.getColumnCount() > 0) {
//				TableColumn idColumn = columnModel.getColumn(2);
//				idColumn.setHeaderValue(ApplicationContext.currentBundle.getString("MainScreen.patientIdColumn.text"));
//				TableColumn nameColumn = columnModel.getColumn(3);
//				nameColumn.setHeaderValue(
//						ApplicationContext.currentBundle.getString("MainScreen.patientNameColumn.text"));
//				TableColumn dobColumn = columnModel.getColumn(4);
//				dobColumn.setHeaderValue(ApplicationContext.currentBundle.getString("MainScreen.dobColumn.text"));
//				TableColumn accNoColumn = columnModel.getColumn(5);
//				accNoColumn.setHeaderValue(
//						ApplicationContext.currentBundle.getString("MainScreen.accessionNoColumn.text"));
//				TableColumn studyDateColumn = columnModel.getColumn(6);
//				studyDateColumn
//						.setHeaderValue(ApplicationContext.currentBundle.getString("MainScreen.studyDateColumn.text"));
//				TableColumn studyDescColumn = columnModel.getColumn(7);
//				studyDescColumn
//						.setHeaderValue(ApplicationContext.currentBundle.getString("MainScreen.studyDescColumn.text"));
//				TableColumn modalityColumn = columnModel.getColumn(8);
//				modalityColumn
//						.setHeaderValue(ApplicationContext.currentBundle.getString("MainScreen.modalityColumn.text"));
//				TableColumn imagesColumn = columnModel.getColumn(9);
//				imagesColumn.setHeaderValue(ApplicationContext.currentBundle.getString("MainScreen.imagesColumn.text"));
//				tableHeader.validate();
//				tableHeader.repaint();
//			}
//			if (filterForm != null) {
//				filterForm.applyLocaleChange();
//			}
//		}
//		if (!queryInfoLabel.getText().equals("")) {
//			queryInfoLabel.setText(ApplicationContext.currentBundle.getString("MainScreen.studiesFoundLabel.text")
//					+ queryInfoLabel.getText().split(":")[1]);
//		}
//		progressLabel.setText(ApplicationContext.currentBundle.getString("MainScreen.downloadingLabel.text"));
//		settingsForm.applyLocaleChange();
//		if (ApplicationContext.viewer != null) {
//			ApplicationContext.viewer.applyLocaleChange();
//		}
//		preferencesItem.setText(ApplicationContext.currentBundle.getString("MainScreen.settingsMenuItem.text"));
//		resetItem.setText(ApplicationContext.currentBundle.getString("MainScreen.resetLocalDbMenuItem.text"));
//		importItem.setText(ApplicationContext.currentBundle.getString("MainScreen.importMenuItem.text"));
//	}

//	private void createPreferences() {
//		Font textFont = new Font("Arial", Font.BOLD, 15);
//		preferencesPopup = new JPopupMenu();
//		preferencesItem = new JMenuItem(ApplicationContext.currentBundle.getString("MainScreen.settingsMenuItem.text"));
//		preferencesItem.setFont(textFont);
//		preferencesItem.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				settingsForm.setSelectedTab();
//				settingsForm.setLocationRelativeTo(ApplicationContext.mainScreenObj);
//				settingsForm.setVisible(true);
//			}
//		});
//		resetItem = new JMenuItem(ApplicationContext.currentBundle.getString("MainScreen.resetLocalDbMenuItem.text"));
//		resetItem.setFont(textFont);
//		ActionListener resetListener = new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				int isReset = JOptionPane.showOptionDialog(rootPane,
//						ApplicationContext.currentBundle.getString("MainScreen.resetDBConfirmation.text"),
//						ApplicationContext.currentBundle.getString("MainScreen.resetDBConfirmation.title.text"),
//						JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
//						new String[] { ApplicationContext.currentBundle.getString("YesButtons.text"),
//								ApplicationContext.currentBundle.getString("NoButtons.text") },
//						"default");
//				if (isReset == 0) {
//					ApplicationContext.databaseRef.rebuild();
//					if (serverTab.getSelectedIndex() == 0) {
//						constructTreeTable(null);
//					}
//					((ImagePreviewPanel) ((JSplitPane) serverTab.getComponentAt(0)).getLeftComponent())
//							.resetImagePreviewPanel();
//					if (ApplicationContext.viewer != null) {
//						ApplicationContext.viewer.dispose();
//						ApplicationContext.viewer = null;
//						ApplicationContext.tabbedPane = null;
//					}
//				}
//			}
//		};
//		importItem = new JMenuItem(ApplicationContext.currentBundle.getString("MainScreen.importMenuItem.text"));
//		importItem.setFont(textFont);
//		importItem.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				FileChooserDialog fcd = new FileChooserDialog(ApplicationContext.mainScreenObj, true);
//				fcd.setLocationRelativeTo(ApplicationContext.mainScreenObj);
//				fcd.setVisible(true);
//			}
//		});
//		resetItem.addActionListener(CursorController.createListener(ApplicationContext.mainScreenObj, resetListener));
//		preferencesPopup.add(preferencesItem);
//		preferencesPopup.add(resetItem);
//		preferencesPopup.add(importItem);
//	}

	public void refreshTreeTable() {
		loadLocalStudiesBySearchKey();
		//do something more...
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

//	public SearchFilterForm getCurrentSearchFilterForm() {
//		return ((SearchFilterForm) ((JSplitPane) serverTab.getSelectedComponent()).getTopComponent());
//	}

//	public void removeInViewer(String studyUid) {
//		if (ApplicationContext.tabbedPane != null) {
//			for (int tab_Iter = 0; tab_Iter < ApplicationContext.tabbedPane.getTabCount(); tab_Iter++) {
//				if (ApplicationContext.tabbedPane.getComponentAt(tab_Iter).getName().equals(studyUid)) {
//					ApplicationContext.tabbedPane.removeTabAt(tab_Iter);
//					ApplicationContext.tabbedPane.revalidate();
//					if (ApplicationContext.tabbedPane.getTabCount() == 0) {
//						ApplicationContext.viewer.dispose();
//						ApplicationContext.viewer = null;
//					}
//					break;
//				}
//			}
//		}
//	}
	
	/**
	 * reflesh laf all components
	 */
	public void refreshLookAndFeels() {
		LookAndFeels laf = ApplicationFacade.getCurrentLookAndFeels();
		laf.updateLookAndFeels(mainScreen);
	}
	
	public static void setImportingState(boolean importing) {
		MainScreen.importing = importing;
	}

	@Override
	public void windowOpened(WindowEvent e) {}

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
	public void windowClosed(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowActivated(WindowEvent e) {}

	@Override
	public void windowDeactivated(WindowEvent e) {}

	@Override
	public void componentResized(ComponentEvent e) {}

	@Override
	public void componentMoved(ComponentEvent e) {}

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

	@Override
	public void componentHidden(ComponentEvent e) {}
}

