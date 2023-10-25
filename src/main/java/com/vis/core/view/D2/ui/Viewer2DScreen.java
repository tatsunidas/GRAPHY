package com.vis.core.view.D2.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.view.D2.roi.RoiObjManager;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;

public class Viewer2DScreen extends JFrame implements WindowFocusListener, WindowStateListener {

	private static final long serialVersionUID = 7624168171524035750L;
	private static final Viewer2DScreen viewerWin = new Viewer2DScreen();
	private static RoiObjManager rom = new RoiObjManager(); 
	private DatabaseHandler db;
	private Viewer2DToolBar toolBar;
	private boolean focusGained = false;

	private String stageInAction = null;

	boolean isDebug = false;
	private Logger logger = Log.logger;

	// debug
//	public static void main(String[] args) {
//		ArrayList<String> test = new ArrayList<String>();
//		test.add("12345");
//		new Viewer2DScreen(test);
//	}

	private StatusBar status;
	private StageDockManager sdm;// tab pane

	private Viewer2DScreen() {
		super(getScreenGraphicsConfiguration());
		setName("Viewer2DScreen");
//		setIconImage(TODO);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setTitle("GRAPHY 2D Viewer");
		isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
				.indexOf("-agentlib:jdwp") > 0;
		if (isDebug) {
			setTitle(getTitle() + " -Debugging-");
		}
		addWindowFocusListener(this);
		addWindowStateListener(this);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				super.windowClosing(e);
				logger.info("Viewer2DScreen::Viewer2DScreen closing...");
				if(getRoiObjManager().isVisible()) {
					getRoiObjManager().setVisible(false);
				}
				/*
				 * save window location
				 */
				saveCurrentScreenState();
				System.gc();
			}
		});
		initContents();
		setLastScreenState();
	}
	
//	public Viewer2DScreen(ArrayList<String> images) {
//	setMinimumSize(new Dimension(700, 350));
//	status = new StatusBar();
//	add(status,BorderLayout.SOUTH);
//	setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//	setStages(images.get(0));
//	loadImagesOnSatge(images);
//	pack();
//	setVisible(true);
//}

	private static GraphicsConfiguration getScreenGraphicsConfiguration() {
		GraphicsDevice[] screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		String lastScreenDeviceID = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenDeviceID);
		if(screenDevices != null && (lastScreenDeviceID != null && !lastScreenDeviceID.equals(""))) {
			for(GraphicsDevice gd:screenDevices) {
				if(gd.getIDstring().equals(lastScreenDeviceID)) {
					return gd.getDefaultConfiguration();
				}
			}
		}
		return null;
	}

	public static Viewer2DScreen getInstance() {
		return viewerWin;
	}
	
	public static RoiObjManager getRoiObjManager() {
		return rom;
	}

	public void initContents() {
		if (sdm != null && sdm.getTabCount() > 0) {
			getContentPane().removeAll();
			getContentPane().setLayout(new BorderLayout());
		}
		ViewerMenu menu = new ViewerMenu();
		setJMenuBar(menu);
		toolBar = new Viewer2DToolBar();
		add(toolBar, BorderLayout.NORTH);
		status = new StatusBar();
		add(status, BorderLayout.SOUTH);
		sdm = new StageDockManager();
		add(sdm, BorderLayout.CENTER);
	}

	public void setDatabase(DatabaseHandler db) {
		this.db = db;
	}

	public DatabaseHandler getDatabase() {
		return this.db;
	}

	public StageDockManager getStageDockManager() {
		return this.sdm;
	}
	
	public String[] getPatientsListOnViewer(){
		StageDockManager sdm = getStageDockManager();
		if(sdm == null) {
			return null;
		}
		return sdm.getAllPatientList();
	}
	
	public void initStage() {
		if (sdm != null && sdm.getTabCount() > 0) {
			String[] patList = sdm.getAllPatientList();
			for(String pat:patList) {
				sdm.deleteStage(pat);
			}
		}
	}

	/*
	 * for temporal use, future work
	 */
	public void loadImagesOnSatge() {
		ArrayList<DICOMNode> nodes = WindowManager.getMainScreen().getSelectedNode();
		loadImagesOnSatgeThroughDB(nodes, true);
	}
	
	public void loadImagesOnSatgeThroughDB(ArrayList<DICOMNode> nodes, boolean initAllStages) {
		Viewer2DScreen viewer = getInstance();
		if(this.db == null) {
			return;
		}
		if (viewer == null) {
			// maybe, this case never occur.
			System.out.println("Viewer2DWindow is NULL !! Please restart graphy.");
			return;
		}
		if(nodes == null) {
			return;
		}
		int size = nodes.size();
		if (size < 1) {
			System.out.println("Viewer2DWindow is needed DICOMNode selection. return.");
			return;
		}
		
		/*
		 * if viewer disposed, init all stages.
		 */
		boolean initDone = false;
		if (!viewer.isVisible()) {
			viewer.initContents();
			initDone = true;
		}
		if(!initDone) {
			if(initAllStages) {
				viewer.initContents();
				initDone = true;
			}
		}
		
		ArrayList<String> doneSeries = new ArrayList<String>();
		ArrayList<String> doneImages = new ArrayList<String>();
		/*
		 * search procedure 
		 * first, load study node
		 * second, load series node
		 * finally, load image node
		 */
		//First, process selected study node
		for (DICOMNode studyNode : nodes) {
			int level = studyNode.getLevel();
			String patID = studyNode.getData(DICOMNode.PatientID);
			if (level == DICOMNode.STUDY) {
				String studyUID = studyNode.getData(DICOMNode.StudyInstanceUID);
				// search series
				ArrayList<DICOMNode> seriesNodes = (ArrayList<DICOMNode>) studyNode.getChildren();
				for (DICOMNode seriesNode : seriesNodes) {
					if (seriesNode.getLevel() == DICOMNode.SERIES) {
						String patIDchi = seriesNode.getData(DICOMNode.PatientID);
						String studyUIDchi = seriesNode.getData(DICOMNode.StudyInstanceUID);
						if (patID.equals(patIDchi) && studyUID.equals(studyUIDchi)) {
							String seriesUID = seriesNode.getData(DICOMNode.SeriesInstanceUID);
							if (!doneSeries.contains(patID + studyUID + seriesUID)) {
								// search images
								ArrayList<String> sopUIDs = new ArrayList<String>();
								ArrayList<DICOMNode> imageNodes = (ArrayList<DICOMNode>) seriesNode.getChildren();
								for (DICOMNode chichi : imageNodes) {
									if (chichi.getLevel() == DICOMNode.IMAGE) {
										String patIDchichi = chichi.getData(DICOMNode.PatientID);
										String studyUIDchichi = chichi.getData(DICOMNode.StudyInstanceUID);
										String seriesUIDchichi = chichi.getData(DICOMNode.SeriesInstanceUID);
										if (patID.equals(patIDchichi) && studyUID.equals(studyUIDchichi)
												&& seriesUID.equals(seriesUIDchichi)) {
											sopUIDs.add(chichi.getData(DICOMNode.SOPInstanceUID));
										}
									}
								}
								if (sopUIDs.size() > 0) {
									String frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID", patID,
											studyUID, seriesUID, sopUIDs.get(0));
									if (frameOfRefUID == null) {
										frameOfRefUID = "";
									}
									viewer.loadImagesOnStage(patID, studyUID, seriesUID,
											sopUIDs.toArray(new String[sopUIDs.size()]), frameOfRefUID);
									for(String su:sopUIDs) {
										if(!doneImages.contains(patID + studyUID + seriesUID+su)) {
											doneImages.add(patID + studyUID + seriesUID+su);
										}
									}
								}
								doneSeries.add(patID + studyUID + seriesUID);
							}
						}
					}
				}
			}
		} // end selected study node loop
		
		// second, process selected series level nodes
		for (DICOMNode seriesNode : nodes) {
			int level = seriesNode.getLevel();
			String patID = seriesNode.getData(DICOMNode.PatientID);
			if (level == DICOMNode.SERIES) {
				String studyUID = seriesNode.getData(DICOMNode.StudyInstanceUID);
				String seriesUID = seriesNode.getData(DICOMNode.SeriesInstanceUID);
				// check already done
				if (!doneSeries.contains(patID + studyUID + seriesUID)) {
					// search images in selected node
					ArrayList<String> sopUIDs = new ArrayList<String>();
					ArrayList<DICOMNode> img_nodes = (ArrayList<DICOMNode>) seriesNode.getChildren();
					for (DICOMNode chi : img_nodes) {
						if (chi.getLevel() == DICOMNode.IMAGE) {
							String patIDchi = chi.getData("PatientID");
							String studyUIDchi = chi.getData("StudyInstanceUID");
							String seriesUIDchi = chi.getData(DICOMNode.SeriesInstanceUID);
							if (patID.equals(patIDchi) && studyUID.equals(studyUIDchi)
									&& seriesUID.equals(seriesUIDchi)) {
								sopUIDs.add(chi.getData(DICOMNode.SOPInstanceUID));
							}
						}
					}
					if (sopUIDs.size() > 0) {
						String frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID", patID,
								studyUID, seriesUID, sopUIDs.get(0));
						if (frameOfRefUID == null) {
							frameOfRefUID = "";
						}
						viewer.loadImagesOnStage(patID, studyUID, seriesUID,
								sopUIDs.toArray(new String[sopUIDs.size()]), frameOfRefUID);
						for(String su:sopUIDs) {
							if(!doneImages.contains(patID + studyUID + seriesUID+su)) {
								doneImages.add(patID + studyUID + seriesUID+su);
							}
						}
					}
					doneSeries.add(patID + studyUID + seriesUID);
				}
			}
		} // end selected series node loop
		
		// escape image level nodes
		for (DICOMNode imageNode : nodes) {
			int level = imageNode.getLevel();
			if (level == DICOMNode.IMAGE) {
				String patID = imageNode.getData(DICOMNode.PatientID);
				String studyUID = imageNode.getData(DICOMNode.StudyInstanceUID);
				String seriesUID = imageNode.getData(DICOMNode.SeriesInstanceUID);
				String sopUID = imageNode.getData(DICOMNode.SOPInstanceUID);
				// check already done
				if (!doneImages.contains(patID + studyUID + seriesUID + sopUID)) {
					String frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID", patID, studyUID,
							seriesUID, sopUID);
					if (frameOfRefUID == null) {
						frameOfRefUID = "";
					}
					viewer.loadImagesOnStage(patID, studyUID, seriesUID, new String[] { sopUID }, frameOfRefUID);
					if (!doneImages.contains(patID + studyUID + seriesUID + sopUID)) {
						doneImages.add(patID + studyUID + seriesUID + sopUID);
					}
				}
			}
		}
		viewer.setVisible(true);
		viewer.revalidate();
		viewer.repaint();
	}

	/*
	 * load particular images which specified by sopUIDs
	 */
	public void loadImagesOnStage(String patID, String studyUID, String seriesUID, String[] sopUIDs, String refUID) {
		HashMap<String, String> patInfo = db.getPatientInfoByPatID(patID);
		if (!sdm.existsInDock(patID)) {
			constructStageView(patInfo, patID, studyUID, seriesUID, sopUIDs, refUID);
		} else {
			// get Stage
			StageView sv = sdm.getStage(patID);
			// add Eyepiece
			sv.addPraparatOnStage(patID, studyUID, seriesUID, sopUIDs, refUID);
		}
		//fail safe
//		MainScreen mainScreen = MainScreen.getInstance();
//		if(mainScreen != null) {
//			mainScreen.refreshTreeTable();
//		}
	}

	private void constructStageView(HashMap<String, String> patientInfoSet, String patID, String studyUID,
			String seriesUID, String[] sopUIDs, String refUID) {
		StageView stage = new StageView(patientInfoSet, studyUID, seriesUID, sopUIDs, refUID);
		sdm.addStage(patID, stage);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				viewerWin.pack();
				viewerWin.revalidate();
				viewerWin.repaint();
			}
		});
	}

	public StageView getStageViewAt(String patID) {
		StageView sv = sdm.getStage(patID);
		return sv;
	}

	public Eyepiece getEyepieceOnStageWhere(String patID) {
		StageView sv = sdm.getStage(patID);
		return sv.getEyepiece();
	}
	
	public ArrayList<Praparat> getSelectedPraps(){
		StageDockManager sdm = getStageDockManager();
		String stageID = getStageInAction();
		StageView activeStage = sdm.getStage(stageID);
		Eyepiece eye = activeStage.getEyepiece();
		return eye.getSelectingPraparats();
	}

	public void setStageInAction(String pid) {
		this.stageInAction = pid;
		System.out.println("Stage In Action:" + pid);
	}

	public String getStageInAction() {
//		Window activeWindow = javax.swing.FocusManager.getCurrentManager().getFocusedWindow();
//		String activeWinName = activeWindow.getName();
		/*
		 * Window名で識別するか？->不要 Viewer2DScreen Floating ToolBar(PatID DialogWindow)
		 * 
		 * Tabが１つのとき、または、複数あるが、すでに選択状態にあるタブを再選択するとき、替えられない。 focusGainedで対応。
		 */
		return this.stageInAction;
	}
	
	public int getCurrentToolType() {
		return toolBar.getCurrentToolType();
	}
	
	private void setLastScreenState() {
		String lastScreenX = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenX);
		String lastScreenY = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenY);
		String lastScreenW = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenWidth);
		String lastScreenH = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenHeight);
		if(lastScreenX == null || lastScreenY == null || lastScreenW == null || lastScreenH == null) {
			setDefaultScreenLocation();
			return;
		}else if(lastScreenX.equals("") || lastScreenY.equals("") || lastScreenW.equals("") || lastScreenH.equals("")) {
			setDefaultScreenLocation();
			return;
		}
		int x = Integer.parseInt(lastScreenX);
		int y = Integer.parseInt(lastScreenY);
		setLocation(x, y);
		int w = Integer.parseInt(lastScreenW);
		int h = Integer.parseInt(lastScreenH);
		setPreferredSize(new Dimension(w, h));
		setBounds(x,y,w,h);//important
		/*
		 * if you want show full screen
		 * use, maximizeWindow() after setVisible(true)
		 */
		
		/*
		 * do not perform here.
		 * setVisible(true);//see facade
		 */
	}
	
	private void setDefaultScreenLocation() {
		setSize(new Dimension(1200, 900));
		setPreferredSize(new Dimension(1200, 900));
		setLocationRelativeTo(null);//set location
		setBounds(getX(),getY(),1200, 900);//important
	}
	
	private synchronized void saveCurrentScreenState() {
		String last2DViewerScreenDeviceID = getGraphicsConfiguration().getDevice().getIDstring();
		String last2DViewerScreenX = String.valueOf(getLocationOnScreen().x);
		String last2DViewerScreenY = String.valueOf(getLocationOnScreen().y);
		String last2DViewerScreenW = String.valueOf(getWidth());
		String last2DViewerScreenH = String.valueOf(getHeight());
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenDeviceID, last2DViewerScreenH);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenDeviceID, last2DViewerScreenDeviceID);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenX, last2DViewerScreenX);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenY, last2DViewerScreenY);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenWidth, last2DViewerScreenW);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenHeight, last2DViewerScreenH);
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
//		        int screen_height = gcon.getBounds().height;
//				setLocation(0, 0);
//				setSize(screen_width, screen_height));
//				setBounds(0,0,screen_width, screen_height);//important
			}
		});
	}

	@Override
	public void windowGainedFocus(WindowEvent arg0) {
		this.focusGained = true;
//		System.out.println("Viewer2DFocued !!");
		if (sdm != null && !(sdm.getComponentCount() < 1)) {
			int currentTabIndex = sdm.getSelectedIndex();
			setStageInAction(sdm.getPatIdAt(currentTabIndex));// resque
		}
	}

	@Override
	public void windowLostFocus(WindowEvent arg0) {
		this.focusGained = false;
	}

	@Override
	public void windowStateChanged(WindowEvent e) {
		// minimized
		if ((e.getNewState() & JFrame.ICONIFIED) == JFrame.ICONIFIED) {
			//do nothing
		}
		// maximized
		else if ((e.getNewState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH) {
			if(sdm == null || sdm.getComponentCount() < 0) {
				return;
			}
			//Example
			StageDockManager sdm = getStageDockManager();
			String stageID = getStageInAction();
			StageView activeStage = sdm.getStage(stageID);
			Eyepiece eye = activeStage.getEyepiece();
		}
	}
}
