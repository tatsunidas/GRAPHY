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
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.ContextKey;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.LookAndFeels;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.ui.main.dcmtreetable.DICOMNodeBuilder;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.core.ui.qr.QueryRetrieve;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTableModel;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.dimse.DimseUtilities;

import javax.swing.JMenuBar;
import javax.swing.JOptionPane;

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
public class MainScreen extends JFrame implements WindowListener, ComponentListener {

	// singleton
	private static MainScreen mainScreen;

	// debug
	boolean isDebug = Utils.isDebug;
	public boolean qrAutoRefreshOn = false;
	boolean isLocal;
	/* Main Explorer */
	private TreeTableDockManager tabDockManager;
	private DICOMTreeTable homeTreeTable;// local treetable
	public final String home = TreeTableDockManager.homeTabName;

	/* Main menu */
	MainScreenMenu mainMenu;
	/* Main ToolBar */
	MainScreenToolBar mainToolBar;
	/* Main search Bar */
	SearchToolBar searchToolBar;

	JToolBar treeTableDock;// dockable treetable
	BirdsEyeView bev;
	JSplitPane treeTbaleAndBirdsEyeSplitPane;

	InformationBar statusBar;

	public int progressValue = 0;

	public JPanel activeViewPanel;
	public JSplitPane seriesThumbnailSplit;
	private javax.swing.JLabel queryInfoLabel;

	/* drag and drop */
	DICOMNode draggedNode;

	DragSourceListener sourceListener;

	DragSource dragSource;

	Logger logger = Log.logger;

	/* Guard to cancel stale BirdsEyeView update threads */
	private final AtomicLong birdsEyeRequestId = new AtomicLong(0);
	private javax.swing.Timer birdsEyeDelayTimer;

	/**
	 * singleton
	 */
	private MainScreen() {
		super(loadLastGraphicConfiguration());
		setSettings();
		setContents();
		setLastScreenState();
	}

	public static MainScreen getInstance() {
		if (mainScreen == null) {
			mainScreen = new MainScreen();
		}
		return mainScreen;
	}

	private static GraphicsConfiguration loadLastGraphicConfiguration() {
		GraphicsDevice[] screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		String lastMainScreenDeviceID = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props.toString(),
				GraphyProp.MainScreenDeviceID.name());
		if (screenDevices != null && (lastMainScreenDeviceID != null && lastMainScreenDeviceID.length() != 0)) {
			for (GraphicsDevice gd : screenDevices) {
				if (gd.getIDstring().equals(lastMainScreenDeviceID)) {
					Log.logger.fine("MainScreen::GraphicsConfiguration: " + gd.getDefaultConfiguration());
					return gd.getDefaultConfiguration();
				}
			}
		}
		return null;
	}

	public void clearPatientInfo() {
		bev.resetViews(true);
	}

	@Override
	public void componentHidden(ComponentEvent e) {
	}

	@Override
	public void componentMoved(ComponentEvent e) {
	}

	@Override
	public void componentResized(ComponentEvent e) {
	}

	@Override
	public void componentShown(ComponentEvent e) {
		if (bev != null) {
			bev.resetViews(true);
		}
		if (treeTbaleAndBirdsEyeSplitPane != null) {
			int h = treeTbaleAndBirdsEyeSplitPane.getHeight();
			treeTbaleAndBirdsEyeSplitPane.setDividerLocation(h - (h / 2));
		}
	}

	public TreeTableDockManager getTreeTableDockManager() {
		return this.tabDockManager;
	}

	public DICOMTreeTable getLocalTreeTable() {
		return homeTreeTable;
	}

	/**
	 * this is not toolbar
	 */
	public JMenuBar getMainMenuBar() {
		return this.mainMenu;
	}

	public SearchToolBar getMainSearchToolBar() {
		return this.searchToolBar;
	}
	
	public BirdsEyeView getBirdsEyeView() {
		return bev;
	}

	public ArrayList<DICOMNode> getSelectedNode() {
		return homeTreeTable.getSelectedNodes();
	}

	public boolean isHomeTop() {
		return tabDockManager.isHomeTop();
	}

	public void ignoreRepaintBirdsEye(boolean ignore) {
		bev.ignoreRepaintAllSlides(ignore);
	}

	private void initHomeTreeTables() {
		// Local/QR TreeTables Manager
		tabDockManager = new TreeTableDockManager();// TabbedPane
		/* Local(HOME) TreeTable */
		DICOMTreeTableModel model = new DICOMTreeTableModel(new DICOMNode(true, new ArrayList<DICOMNode>()));
		homeTreeTable = new DICOMTreeTable(model, false, null);
		try {
			tabDockManager.addTreeTable(true, home, homeTreeTable);
		} catch (URISyntaxException e) {
			e.printStackTrace();
			try {
				ApplicationFacade.readyToClose(Level.SEVERE, "Filed to construct MainTreeTable...");
			} catch (Throwable e1) {
				e1.printStackTrace();
			}
		}
	}

	private void initQRTreeTables() {
		/* QR TreeTables */
		ArrayList<DicomCommunicationNode> servers = DatabaseHandler.getInstance().loadServerList();
		if (servers != null && !servers.isEmpty()) {
			// show top ? if no, set HOME to Top.
			String keepTopTitle = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props,
					GraphyProp.MainTreeTableKeepTopTitle);
			for (DicomCommunicationNode svr : servers) {
				// constructQRTreeTables
				boolean svrReady = DimseUtilities.echo(svr);
				if (!svrReady) {
					continue;
				}
				// set today's nodes.
				DICOMTreeTableModel modelQR = new DICOMTreeTableModel(
						new QueryRetrieve(true/* queryOnly */).queryToday(svr));
				DICOMTreeTable qrTreeTable = new DICOMTreeTable(modelQR, true, svr);
				try {
					tabDockManager.addTreeTable(false, svr.getNickname(), qrTreeTable);
					tabDockManager.getDock(svr.getNickname()).updateTreeTableStatus();
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			}
			if (keepTopTitle != null && !keepTopTitle.isEmpty()) {
				tabDockManager.setToTopTab(keepTopTitle);
			}
			tabDockManager.startRefreshQRTableTimer();
		}
	}

	public void loadLocalStudiesBySearchKey() {
		HashMap<String, Object> keys = getMainSearchToolBar().getCurrentSearchConditions();
		DatabaseHandler db = DatabaseHandler.getInstance();
		@SuppressWarnings("unchecked")
		ArrayList<DefaultMutableTreeNode> selectedStudies = db.selectStudiesWithSearchKeys2(
				(String) keys.get("PatientID"), (String) keys.get("PatientName"), (String) keys.get("From"),
				(String) keys.get("To"), (ArrayList<String>) keys.get("Modalities"));
		if (selectedStudies == null) {
			selectedStudies = new ArrayList<>();
		}
		DICOMNodeBuilder builder = new DICOMNodeBuilder();
		DICOMNode newRoot = builder.buildRootNodeUsingTreeNodes(selectedStudies);
		this.tabDockManager.getHomeDock().updateTreeTableStatus(newRoot);
	}

	/**
	 * do it starting-up
	 */
//	private void loadLocalStudiesWhenStartingUp() {
//		/*
//		 * today query is default
//		 */
//		String patID = null;//anybody
//		String from = QRUtil.getTodayString("/");
//		String to = null;
//		ArrayList<String> modalities = null;
//		//study list
//		DatabaseHandler db = DatabaseHandler.getInstance();
//		if(db == null) {
//			return;
//		}
//		ArrayList<DefaultMutableTreeNode> localStudies = db.selectStudiesWithSearchKeys(patID,from, to, modalities);
//		//construct root dicom node
//		DICOMNodeBuilder builder = new DICOMNodeBuilder();
//		DICOMNode newRoot = builder.buildRootNodeUsingTreeNodes(localStudies);
//		this.tabDockManager.getHomeDock().updateTreeTable(newRoot);
//	}

	public void maximizeWindow() {
		if (!isVisible()) {
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

	/**
	 * mimic method, but easy to use for me.
	 */
	public void refleshAnchorTreeTable() {
		searchCurrentConditions();
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

	/**
	 * to avoid mimic method confusion for me.
	 */
	public void searchCurrentConditions() {
		queryAndUpadateTreeTable();
	}

	public void queryAndUpadateTreeTable() {
		HashMap<String, Object> keys = searchToolBar.getCurrentSearchConditions();
		String patID = (String) keys.get("PatientID");
		String patName = (String) keys.get("PatientName");
		String from = (String) keys.get("From");
		String to = (String) keys.get("To");
		@SuppressWarnings("unchecked")
		ArrayList<String> m = (ArrayList<String>) keys.get("Modalities");
		boolean ignoreNullSearchKeyWarning = Utils.ignoreNullSearchKeyWarning();
		if (Utils.isDebug) {
			Log.logger.log(Level.FINE, "Ignore null search key warning when DEBUG mode.");
			ignoreNullSearchKeyWarning = true;
		}
		queryAndUpadateTreeTable(patID, patName, from, to, m, ignoreNullSearchKeyWarning);
	}

	/**
	 * update current treetable. If all docks are floating, update all.
	 * 
	 * @param patID
	 * @param patName
	 * @param from
	 * @param to
	 * @param modalities
	 * @param askNullSearchKey
	 */
	public void queryAndUpadateTreeTable(String patID, String patName, String from, String to,
			ArrayList<String> modalities, boolean ignoreNullSearchKey) {
		if (!ignoreNullSearchKey) {
			if (searchToolBar.nullSearchKeys()) {
				int res = PopUpMessage.showDialog(WindowManager.getMainScreen(), "No search keys",
						"Do you want to show all datasets in DB/REMOTE ?? (It is not recommended as usual.)",
						JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
				if (res != JOptionPane.OK_OPTION) {
					return;
				}
			}
		}
		TabDock dock = tabDockManager.getCurrentTopDockStayInTabbedPane();
		if (dock == null /* floating all docks */) {
			// update all dock ?
//			for(String nickName : tabDockManager.getAllNicknamesFromDocks()) {
//				TabDock d = tabDockManager.getDock(nickName);
//				queryAndUpadateTreeTable(d, patID, patName, from, to, modalities);
//			}
			String anchorNickName = tabDockManager.getCurrentAnchorTitle();
			TabDock anchor = tabDockManager.getDock(anchorNickName);
			new Thread(() -> {
				queryAndUpadateTreeTable(anchor, patID, patName, from, to, modalities);
			}).start();
		} else {
			new Thread(() -> {
				queryAndUpadateTreeTable(dock, patID, patName, from, to, modalities);
			}).start();
		}
	}

	private void queryAndUpadateTreeTable(TabDock dock, String patID, String patName, String from, String to,
			ArrayList<String> modalities) {
		if (dock.getName().equals(home)) {
			Log.logger.fine("QueryAndUpadateTreeTable : TreeTableDock [" + home + "]");
			ArrayList<DefaultMutableTreeNode> selectedStudiesMaterials = DatabaseHandler.getInstance()
					.selectStudiesWithSearchKeys2(patID, patName, from, to, modalities);
			DICOMNode newRoot = new DICOMNodeBuilder().buildRootNodeUsingTreeNodes(selectedStudiesMaterials);
			SwingUtilities.invokeLater(() -> dock.updateTreeTableStatus(newRoot));
		} else {
			Log.logger.fine("QueryAndUpadateTreeTable : TreeTableDock [" + dock.getName() + "]");
			/* root */
			DICOMNode queryResults = new QueryRetrieve(true/* queryOnly */).querySimpleSearchKeys(dock.getName(), patID,
					patName, from, to, modalities);
			SwingUtilities.invokeLater(() -> dock.updateTreeTableStatus(queryResults));
		}
	}

	private void setContents() {
		// Menubar
		mainMenu = new MainScreenMenu();
		setJMenuBar(mainMenu);

		/*
		 * North Component
		 */
		JPanel mainNorthPanel = new JPanel();
		mainNorthPanel.setLayout(new BorderLayout());

		// Toolbar under to north panel
		mainToolBar = new MainScreenToolBar();
		mainNorthPanel.add(mainToolBar, BorderLayout.NORTH);

		/* SearchToolBar */
		searchToolBar = new SearchToolBar(this);
		mainNorthPanel.add(searchToolBar, BorderLayout.CENTER);
		add(mainNorthPanel, BorderLayout.NORTH);

		/*
		 * Center Component
		 */
		// treetable and bird's eye view
		treeTbaleAndBirdsEyeSplitPane = new JSplitPane();
		treeTbaleAndBirdsEyeSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		treeTbaleAndBirdsEyeSplitPane.setOneTouchExpandable(true);

		// treeTables
		initHomeTreeTables();

		// Next, update QRTables.
		/*
		 * do not use main threads. To avoid freeze during QR.
		 */
		new Thread(() -> {
			initQRTreeTables();
		}).start();

		treeTbaleAndBirdsEyeSplitPane.setLeftComponent(tabDockManager);

		// birds eye view
		bev = new BirdsEyeView();
		treeTbaleAndBirdsEyeSplitPane.setRightComponent(bev);

		add(treeTbaleAndBirdsEyeSplitPane, BorderLayout.CENTER);

		/*
		 * South Component
		 */
		// triangle bar
		statusBar = new InformationBar();
		getContentPane().add(statusBar, BorderLayout.SOUTH);

		addWindowListener(this);
		addComponentListener(this);
	}

	/*
	 * type: Cursor.DEFAULT_CURSOR, etc
	 */
	public void setCursor(int type) {
		setCursor(new Cursor(type));
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
		// first, set size
		setSize(new Dimension(1200, 700));
		setPreferredSize(new Dimension(1200, 700));
		setLocationRelativeTo(null);
		setBounds(getX(), getY(), 1200, 700);// important
	}

	public void setInfoToPatientPanel(HashMap<String, String> infoset) {
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
		if (lastMainScreenX == null || lastMainScreenY == null || lastMainScreenW == null || lastMainScreenH == null) {
			setDefaultScreenLocation();
			return;
		} else if (lastMainScreenX.equals("") || lastMainScreenY.equals("") || lastMainScreenW.equals("")
				|| lastMainScreenH.equals("")) {
			setDefaultScreenLocation();
			return;
		}
		int x = Integer.parseInt(lastMainScreenX);
		int y = Integer.parseInt(lastMainScreenY);
		setLocation(x, y);
		int w = Integer.parseInt(lastMainScreenW);
		int h = Integer.parseInt(lastMainScreenH);
		setPreferredSize(new Dimension(w, h));
		setBounds(x, y, w, h);// important

		/*
		 * do not perform here. setVisible(true);//see facade
		 */
	}

	private void setSettings() {
		setName(ConfigInfo.MainScreen.toString());
		setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		// set window title
		if (!isDebug) {
			setTitle(ConfigInfo.AppName.toString() + " " + ApplicationFacade.version);
		} else {
			setTitle(ConfigInfo.AppName.toString() + " " + ApplicationFacade.version + " -debug mode-");
		}
		// set icon
		setIconImage(Resources.MainWindowIcon.loadIconFromResource().getImage());
	}

	public void setStudiesFound(String text) {
		queryInfoLabel.setText(text);
	}

	/**
	 * show images on BEV when home tab is selected.
	 */
	public void showImagesOnBirdsEye() {
		if (birdsEyeDelayTimer != null && birdsEyeDelayTimer.isRunning()) {
			birdsEyeDelayTimer.restart();
			return;
		}
		// 200ミリ秒後に1回だけ実行するタイマーを設定
		birdsEyeDelayTimer = new javax.swing.Timer(200, e -> {
			// ★ ここから下が本来の処理（遅延実行される）
			executeShowImagesOnBirdsEye();
		});
		birdsEyeDelayTimer.setRepeats(false);
		birdsEyeDelayTimer.start();

	}

	/**
	 * Load and Show images on BirdsEyeView
	 */
	private void executeShowImagesOnBirdsEye() {
		TreeTableDockManager ttdm = getTreeTableDockManager();
		if (!ttdm.getCurrentAnchorTitle().equals(TreeTableDockManager.homeTabName)) {
			return;
		}
		TabDock homeDock = ttdm.getHomeDock();
		DICOMTreeTable tt = homeDock.getDICOMTreeTable();
		ArrayList<DICOMNode> selectedNodes = tt.getSelectedNodes();

		if (selectedNodes == null || selectedNodes.isEmpty()) {
			if (bev != null)
				bev.resetViews(true);
			return;
		}

		// もし複数スタディが選択されている場合は表示しない
		int studyCount = 0;
		for (DICOMNode n : selectedNodes) {
			if (n.getLevel() == DICOMNode.STUDY) {
				studyCount++;
			}
		}
		if (studyCount > 1) {
			logger.fine("showImagesOnBirdsEye(): Can not show multi studies on Bird's eye view.");
			return;
		}

		ArrayList<String> selectedSeriesUIDs = new ArrayList<>();
		HashMap<String, ArrayList<String>> selectedImageUIDs = new HashMap<>();
		for (DICOMNode n : selectedNodes) {
			if (n.getLevel() == DICOMNode.SERIES) {
				selectedSeriesUIDs.add(n.getData(DICOMNode.SeriesInstanceUID));
			}
			if (n.getLevel() == DICOMNode.IMAGE) {
				if (selectedImageUIDs.get(n.getData(DICOMNode.SeriesInstanceUID)) == null) {
					ArrayList<String> uids = new ArrayList<>();
					uids.add(n.getData(DICOMNode.SOPInstanceUID));
					selectedImageUIDs.put(n.getData(DICOMNode.SeriesInstanceUID), uids);
				} else {
					selectedImageUIDs.get(n.getData(DICOMNode.SeriesInstanceUID))
							.add(n.getData(DICOMNode.SOPInstanceUID));
				}
			}
		}

		String patID = selectedNodes.get(0).getData(DICOMNode.PatientID);
		String studyUID = selectedNodes.get(0).getData(DICOMNode.StudyInstanceUID);

		if (bev != null) {
			String currentShowingStudyUID = bev.getShowingStudyUID();

			// 別の患者（Study）が選択された場合は、スレッド開始前に即座に画面をクリアする！
			if (currentShowingStudyUID == null || !currentShowingStudyUID.equals(studyUID)) {
				// ★ 画面クリアを強制実行して、古い患者の画像が残るのを防ぐ
				bev.resetViews(true);
				final long reqId = birdsEyeRequestId.incrementAndGet();

				setCursor(Cursor.WAIT_CURSOR);

				new Thread(() -> {
					if (reqId != birdsEyeRequestId.get()) {
						// 自分が最新のタスクでなければ即終了
						// カーソルは戻す
						SwingUtilities.invokeLater(() -> setCursor(Cursor.DEFAULT_CURSOR));
						return;
					}
					bev.showImages(patID, studyUID, selectedSeriesUIDs, selectedImageUIDs);
				}).start();

			} else if (currentShowingStudyUID.equals(studyUID)) {
				if (selectedSeriesUIDs.size() == 0 && selectedImageUIDs.size() == 0) {
					return;
				}
				final long reqId = birdsEyeRequestId.incrementAndGet();
				setCursor(Cursor.WAIT_CURSOR);
				new Thread(() -> {
					// 自分が最新のタスクでなければ即終了
					if (reqId != birdsEyeRequestId.get()) {
	                    // キャンセル時はすぐにデフォルトに戻す
	                    SwingUtilities.invokeLater(() -> setCursor(Cursor.DEFAULT_CURSOR));
	                    return;
	                }
					bev.updateViews(patID, studyUID, selectedSeriesUIDs, selectedImageUIDs);
				}).start();
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
		if (patIDs == null) {
			// see, referencing by treeTableDockManager.
			bev.resetViews(true);
			return;
		}
		HashMap<String, String> pInfo = bev.getPatientInfo();
		if (pInfo == null) {
			return;
		}
		for (String patID : patIDs) {
			if (patID.equals(pInfo.get(ContextKey.PatientID.name()))) {
				bev.resetViews(true);
			}
		}
	}

	public void updateQRTreeTables() {
		// get serverlist
		ArrayList<DicomCommunicationNode> remoteServers = DatabaseHandler.getInstance().loadServerList();
		String keepTopTitle = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props,
				GraphyProp.MainTreeTableKeepTopTitle);
		for (DicomCommunicationNode svr : remoteServers) {
			// constructQRTreeTables
			boolean svrReady = DimseUtilities.echo(svr);
			if (!svrReady) {
				/*
				 * if remote svr in non-communicate still stay in docks, remove it.
				 */
				tabDockManager.removeDockAt(svr.getNickname());
				continue;
			}
			// check already on tabDock
			boolean found = false;
			for (String nickname : tabDockManager.getAllNicknamesFromDocks()) {
				if (nickname.equals(home)) {// is this dead code ?? home is not including remote.
					continue;
				}
				if (nickname.equals(svr.getNickname())) {
					found = true;
					break;
				}
			}
			DICOMTreeTable qrTreeTable = null;
			if (found) {
				// update tree
				// get prev root node
				TabDock prevDock = tabDockManager.getDock(svr.getNickname());
				DICOMTreeTable prevTreeTable = prevDock.getDICOMTreeTable();
				DICOMNode root = (DICOMNode) prevTreeTable.getTree().getModel().getRoot();
				// create new TabDock and set tabDockManager
				DICOMTreeTableModel model = new DICOMTreeTableModel(root);
				qrTreeTable = new DICOMTreeTable(model, true, svr);
			} else {// addNew
				DICOMTreeTableModel model = new DICOMTreeTableModel(
						new QueryRetrieve(true/* queryOnly */).queryToday(svr));
				qrTreeTable = new DICOMTreeTable(model, true, svr);
				/* add or update Dock */
				try {
					tabDockManager.addTreeTable(false, svr.getNickname(), qrTreeTable);
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			}
			if (keepTopTitle.equals(svr.getNickname())) {
				tabDockManager.setToTopTab(keepTopTitle);
			}
		}
		tabDockManager.startRefreshQRTableTimer();
	}

	public void startProgressBar(int taskSizeTotal) {
		statusBar.initProgressBar(taskSizeTotal);
		statusBar.showProgressBar(true);
	}

	public void setProgressValue(int v) {
		statusBar.setProgressValue(v);
	}

	public void removeProgressBar() {
		statusBar.showProgressBar(false);
	}

	@Override
	public void windowActivated(WindowEvent e) {
	}

	@Override
	public void windowClosed(WindowEvent e) {
	}

	@Override
	public void windowClosing(WindowEvent e) {
		saveCurrentScreenState();
		// close all window(2d,3d, etc)
		/*
		 * 1. shutdown db 2. shutdown qrscp 3. delete tmp dir contents 4. system.exit(0)
		 */
		try {
			boolean close = ApplicationFacade.readyToClose(Level.INFO, "Shutting down graphy...");
			if (!close) {
				return;
			}
		} catch (Throwable e1) {
			e1.printStackTrace();
			System.exit(13);
		}
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
	}

	@Override
	public void windowIconified(WindowEvent e) {
	}

	@Override
	public void windowOpened(WindowEvent e) {
	}
}
