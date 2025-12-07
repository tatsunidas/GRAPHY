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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.TabDock;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.ui.qr.QueryRetrieve;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.roi.RoiObjManager;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext;
import com.vis.db.DatabaseHandler;

/**
 * 
 * @author tatsunidas
 *
 */
public class Viewer2DScreen extends JFrame implements WindowListener, WindowStateListener{

	private static final long serialVersionUID = 7624168171524035750L;
	private static final Viewer2DScreen viewerWin = new Viewer2DScreen();
	private static RoiObjManager rom = new RoiObjManager();
	private DatabaseHandler db = DatabaseHandler.getInstance();
	private Viewer2DToolBar toolBar;

	private String stageInAction = null;//stage id is patient id.

	boolean isDebug = Utils.isDebug;
	private Logger logger = Log.logger;

	private StatusBar status;
	private StageDockManager sdm;// tab pane
	
	Dimension minSize = new Dimension(64, 64);

	private Viewer2DScreen() {
		super(getScreenGraphicsConfiguration());
		setName(ConfigInfo.D2ViewerWindow.toString());
		setIconImage(Resources.Viewer2DFrameWinIcon.loadIconFromResource().getImage());
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		if (isDebug) {
			setTitle("GRAPHY 2D Viewer" + " -Debugging-");
		} else {
			setTitle("GRAPHY 2D Viewer");
		}
		addWindowListener(this);
		addWindowStateListener(this);
		initContents();
		setLastScreenState();
		WindowManager.addWindow(this);
	}

	private static GraphicsConfiguration getScreenGraphicsConfiguration() {
		GraphicsDevice[] screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		String lastScreenDeviceID = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props,
				GraphyProp.Viewer2DScreenDeviceID);
		if (screenDevices != null && (lastScreenDeviceID != null && !lastScreenDeviceID.equals(""))) {
			for (GraphicsDevice gd : screenDevices) {
				if (gd.getIDstring().equals(lastScreenDeviceID)) {
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

	public StageDockManager getStageDockManager() {
		return this.sdm;
	}

	public String[] getPatientsListOnViewer() {
		StageDockManager sdm = getStageDockManager();
		if (sdm == null) {
			return null;
		}
		return sdm.getAllPatientList();
	}

	public void initStage() {
		if (sdm != null && sdm.getTabCount() > 0) {
			String[] patList = sdm.getAllPatientList();
			for (String pat : patList) {
				sdm.deleteStage(pat);
			}
		}
		revalidate();
		repaint();
	}

	/**
	 * load from mainscreen
	 */
	public void loadImagesOnStage() {
		if(WindowManager.getMainScreen() == null) {
			Log.logger.fine("If you want show images on 2D Viewer, use another loadImagesOnStage(...) instead.");
			return;
		}
		ArrayList<DICOMNode> nodes = WindowManager.getMainScreen().getSelectedNode();
		if(nodes == null) {
			Log.logger.info("Plase select table rows.");
			return;
		}
		loadImagesOnStage(nodes);
		String patID = nodes.get(0).getData(DICOMNode.PatientID);
		sdm.toTop(patID);
		revalidate();
		repaint();
	}
	
	/**
	 * load from external dicom network node
	 */
	public void loadImagesOnStageFromExternal() {
		MainScreen ms = WindowManager.getMainScreen();
		TabDock dock = ms.getTreeTableDockManager().getCurrentTopDockStayInTabbedPane();
		DICOMTreeTable treeTable = dock.getDICOMTreeTable();
		if(ms.isHomeTop() || dock.isHomeTab() || !treeTable.isQR) {
			loadImagesOnStage();
		}else {
			String msg = "GRAPHY will retrieve to show images on viewer.\n";
			msg += "YES : Retrieve to DB and then show images on viewer.\n";
			msg += "NO : Cancel";
			int res = JOptionPane.showOptionDialog(
					ms, 
					msg, 
					"Load images from Remote DB?",//title 
					JOptionPane.YES_NO_OPTION, 
					JOptionPane.QUESTION_MESSAGE,
					null,
					new String[] {"Retrieve", "Cancel"},
					"Retrieve"	);
			if(res == JOptionPane.YES_OPTION) {
				new Thread(() -> {
					List<DICOMNode> studies = treeTable.getSelectedNodes();
					if(studies == null || studies.size()==0) {
						Log.logger.warning("No DICOM Study selected... Return.");
						return;
					}
					DICOMNode node = studies.get(0);
					QueryRetrieve qr = new QueryRetrieve(false/* queryOnly */);
					qr.prepareRetrieve(treeTable.getRemoteDicomCommunicationNode(), node,
							false/* false means will load to db */);
					qr.start();
					qr.monitorTasks();
					try {
						qr.getThread().join(); // waiting finish qr task on background.
						Thread.sleep(500);
					} catch (InterruptedException ie) {
						Log.logger.warning(ie.getLocalizedMessage());
					}
					WindowManager.getMainScreen().setCursor(new Cursor(Cursor.WAIT_CURSOR));
					loadImagesOnStage((String) node.getData(DICOMNode.PatientID),
							(String) node.getData(DICOMNode.StudyInstanceUID), null, null, null);
					setVisible(true);
					toFront();
					WindowManager.getMainScreen().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				}).start();
			}
		}
	}

	/**
	 * The nodes to be selected may be different patients and at different levels.
	 * Nodes are loaded per level and entered into Stage in Praparat units.
	 * 
	 * The lower level of the node has priority. For example, if a series node is
	 * selected and a particular image node within that series is selected, all
	 * images that the series has will not be loaded, only the selected images.
	 * 
	 * @param nodes
	 */
	public void loadImagesOnStage(ArrayList<DICOMNode> nodes) {
		if (this.db == null) {
			return;
		}
		if (nodes == null) {
			return;
		}
		int size = nodes.size();
		if (size < 1) {
			Log.logger.info("Viewer2DWindow is needed DICOMNode selection. return.");
			return;
		}

		ArrayList<String> selectedStudies = new ArrayList<>();
		ArrayList<String> selectedSeries = new ArrayList<>();
		
		ArrayList<DICOMNode> imageLevelNodes = new ArrayList<>();
		
		ArrayList<String> doneImages = new ArrayList<String>();
		
		// search study level
		for (DICOMNode node : nodes) {
			int level = node.getLevel();
			if (level == DICOMNode.STUDY) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyUID = node.getData(DICOMNode.StudyInstanceUID);
				// check already done
				if (!selectedStudies.contains(patID + studyUID)) {
					selectedStudies.add(patID + studyUID);
				}
			}
		}

		// search series level node from selected node
		for (DICOMNode node : nodes) {
			int level = node.getLevel();
			if (level == DICOMNode.SERIES) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyUID = node.getData(DICOMNode.StudyInstanceUID);
				String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
				if (!selectedSeries.contains(patID + studyUID + seriesUID)) {
					selectedSeries.add(patID + studyUID + seriesUID);
				}
				/*
				 * if series was specified, will not load all images from study.
				 */
				if (selectedStudies.contains(patID + studyUID)) {
					selectedStudies.remove(patID + studyUID);
				}
			}
		}
		
		//search image level node from selected node
		for (DICOMNode imageNode : nodes) {
			int level = imageNode.getLevel();
			if (level == DICOMNode.IMAGE) {
				String patID = imageNode.getData(DICOMNode.PatientID);
				String studyUID = imageNode.getData(DICOMNode.StudyInstanceUID);
				String seriesUID = imageNode.getData(DICOMNode.SeriesInstanceUID);
				imageLevelNodes.add(imageNode);
				/*
				 * if image was specified, will not load all images in series.
				 */
				// check whether already selected above
				if (selectedSeries.contains(patID + studyUID + seriesUID)) {
					selectedSeries.remove(patID + studyUID + seriesUID);
				}
			}
		}

		/*
		 * load collected datasets
		 */
		// search study node
		for (DICOMNode node : nodes) {
			int level = node.getLevel();
			if (level == DICOMNode.STUDY) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyUID = node.getData(DICOMNode.StudyInstanceUID);
				/*
				 * Study nodes for which no series node were selected will display all images.
				 */
				if(!selectedStudies.contains(patID+studyUID)) {
					continue;
				}
				/*
				 * if through, load all series and images in study. 
				 */
				// search series
				ArrayList<DICOMNode> seriesNodes = (ArrayList<DICOMNode>) node.getChildren();
				for (DICOMNode seriesNode : seriesNodes) {
					if (seriesNode.getLevel() == DICOMNode.SERIES) {
						String seriesUID = seriesNode.getData(DICOMNode.SeriesInstanceUID);
						// search images
						ArrayList<String> sopUIDs = new ArrayList<String>();
						ArrayList<DICOMNode> imageNodes = (ArrayList<DICOMNode>) seriesNode.getChildren();
						for (DICOMNode image : imageNodes) {
							if (image.getLevel() == DICOMNode.IMAGE) {
								sopUIDs.add(image.getData(DICOMNode.SOPInstanceUID));
							}
						}
						if (sopUIDs.size() > 0) {
							String frameOfRefUID = db.getValueFromImage("FrameOfReferenceUID", patID, studyUID,
									seriesUID, sopUIDs.get(0));
							if (frameOfRefUID == null) {
								frameOfRefUID = "";
							}
							loadImagesOnStage(patID, studyUID, seriesUID, sopUIDs.toArray(new String[sopUIDs.size()]),
									frameOfRefUID);
							for (String su : sopUIDs) {
								if (!doneImages.contains(patID + studyUID + seriesUID + su)) {
									doneImages.add(patID + studyUID + seriesUID + su);
								}
							}
						}
					}
				}
			}
		} // end selected study node loop

		// second, load series level nodes
		for (DICOMNode node : nodes) {
			int level = node.getLevel();
			if (level == DICOMNode.SERIES) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyUID = node.getData(DICOMNode.StudyInstanceUID);
				String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
				/*
				 * Series nodes for which no image node was selected will display all images.
				 */
				if (!selectedSeries.contains(patID + studyUID + seriesUID)) {
					continue;
				}
				/*
				 * if go through, load all images in series.
				 */
				// search images in selected node
				ArrayList<String> sopUIDs = new ArrayList<String>();
				ArrayList<DICOMNode> img_nodes = (ArrayList<DICOMNode>) node.getChildren();
				for (DICOMNode chi : img_nodes) {
					if (chi.getLevel() == DICOMNode.IMAGE) {
						sopUIDs.add(chi.getData(DICOMNode.SOPInstanceUID));
					}
				}
				if (sopUIDs.size() > 0) {
					String frameOfRefUID = db.getValueFromImage("FrameOfReferenceUID", patID, studyUID,
							seriesUID, sopUIDs.get(0));
					if (frameOfRefUID == null) {
						frameOfRefUID = "";
					}
					loadImagesOnStage(patID, studyUID, seriesUID, sopUIDs.toArray(new String[sopUIDs.size()]),
							frameOfRefUID);
					for (String su : sopUIDs) {
						if (!doneImages.contains(patID + studyUID + seriesUID + su)) {
							doneImages.add(patID + studyUID + seriesUID + su);
						}
					}
				}
			}
		} // end selected series node loop
		
		// finally, load remaining image level nodes
		for (DICOMNode node : imageLevelNodes) {
			String patID = node.getData(DICOMNode.PatientID);
			String studyUID = node.getData(DICOMNode.StudyInstanceUID);
			String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
			String sopUID = node.getData(DICOMNode.SOPInstanceUID);
			/*
			 * if already loaded above, skipped.
			 */
			if(doneImages.contains(patID + studyUID + seriesUID + sopUID)) {
				continue;
			}
			// search images in selected node
			ArrayList<String> sopUIDs = new ArrayList<String>();
			sopUIDs.add(sopUID);
			for (DICOMNode node_ : imageLevelNodes) {
				if(node == node_) {
					continue;
				}
				String patID_ = node.getData(DICOMNode.PatientID);
				String seriesUID_ = node.getData(DICOMNode.SeriesInstanceUID);
				String studyUID_ = node.getData(DICOMNode.StudyInstanceUID);
				String sopUID_ = node.getData(DICOMNode.SOPInstanceUID);
				if(patID.equals(patID_) && studyUID.equals(studyUID_) && seriesUID.equals(seriesUID_)) {
					sopUIDs.add(sopUID_);
					if (!doneImages.contains(patID_ + studyUID_ + seriesUID_ + sopUID_)) {
						doneImages.add(patID_ + studyUID_ + seriesUID_ + sopUID_);
					}
				}
			}
			if (sopUIDs.size() > 0) {
				String frameOfRefUID = db.getValueFromImage("FrameOfReferenceUID", patID, studyUID, seriesUID,
						sopUIDs.get(0));
				if (frameOfRefUID == null) {
					frameOfRefUID = "";
				}
				loadImagesOnStage(patID, studyUID, seriesUID, sopUIDs.toArray(new String[sopUIDs.size()]),
						frameOfRefUID);
			}
		} // end selected image node loop
		
		selectedStudies = null;
		selectedSeries = null;
		imageLevelNodes = null;
		doneImages = null;
	}

	/*
	 * Build praparat using particular images with specified sopUIDs
	 */
	public void loadImagesOnStage(String patID, String studyUID, String seriesUID, String[] sopUIDs, String refUID) {
		HashMap<String, String> patInfo = db.getPatientInfo(patID);
		if (!sdm.existsInDock(patID)) {
			StageView stage = new StageView(patInfo);
			stage.addPraparatOnEye(patID, studyUID, seriesUID, sopUIDs, refUID);
			sdm.addStage(patID, stage);
		} else {
			StageView sv = sdm.getStage(patID);
			sv.addPraparatOnEye(patID, studyUID, seriesUID, sopUIDs, refUID);
		}
	}
	
	public StageView getStageViewAt(String patID) {
		StageView sv = sdm.getStage(patID);
		return sv;
	}

	public Eyepiece getEyepieceOnStageWhere(String patID) {
		StageView sv = sdm.getStage(patID);
		return sv.getEyepiece();
	}

	public ArrayList<Praparat> getSelectedPraps() {
		StageDockManager sdm = getStageDockManager();
		String stageID = getStageIDInAction();
		StageView activeStage = sdm.getStage(stageID);
		Eyepiece eye = activeStage.getEyepiece();
		return eye.getSelectingPraparats();
	}

	public void setStageIDInAction(String pid) {
		this.stageInAction = pid;
		Log.logger.fine("Stage In Action:" + pid);
	}
	
	public String getStageIDInAction() {
		Component selectedComponent = sdm.getSelectedComponent();
		if (selectedComponent instanceof StageView) {
		    StageView sv = (StageView) selectedComponent;
		    stageInAction = sv.getName();
		}
		return stageInAction;
	}

	public int getCurrentToolType() {
		return toolBar.getCurrentToolType();
	}
	
	/**
	 * for 3D Viewer's Canvas3D.
	 * @param ignore
	 */
	public void ignoreRepaintAllSlides(boolean ignore) {
		Viewer2DScreen d2 = Viewer2DScreen.getInstance();
		if(d2 == null || !d2.isVisible()) {
			return;
		}
		StageDockManager sdm = d2.getStageDockManager();
		String[] plist = sdm.getAllPatientList();
		if(plist == null) {
			return;
		}
		for(String pid:plist) {
			StageView sv = sdm.getStage(pid);
			Eyepiece eye = sv.getEyepiece();
			List<PraparatContext> prapCons = eye.getAllPraparatContext();
			if(prapCons != null && prapCons.size()>0) {
				for(PraparatContext pcon:prapCons) {
					/*
					 * praparat does not have complex paintComponent().
					 * So, not set ignoreRepaint.
					 */
					Praparat pp = pcon.getPraparat();
					HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for(int pos:slides.keySet()) {
						SlideGlass sg = slides.get(pos);
						sg.setIgnoreRepaint(ignore);
					}
					
					if(!ignore) {
						pp.revalidate();
						pp.repaint();
					}
				}
			}
		}
	}
	
	public void repaintAllPraparat() {
		StageDockManager sdm = getStageDockManager();
		String[] patIDs = sdm.getAllPatientList();
		for (String patID : patIDs) {
			StageView sv = sdm.getStage(patID);
			Eyepiece eye = sv.getEyepiece();
			List<PraparatContext> pcon = eye.getAllPraparatContext();
			for (PraparatContext pc : pcon) {
				Praparat pp = pc.getPraparat();
				pp.revalidate();
				pp.repaint();
			}
		}
	}

	private void setLastScreenState() {
		String lastScreenX = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenX);
		String lastScreenY = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenY);
		String lastScreenW = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenWidth);
		String lastScreenH = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenHeight);
		if (lastScreenX == null || lastScreenY == null || lastScreenW == null || lastScreenH == null) {
			setDefaultScreenLocation();
			return;
		} else if (lastScreenX.equals("") || lastScreenY.equals("") || lastScreenW.equals("")
				|| lastScreenH.equals("")) {
			setDefaultScreenLocation();
			return;
		}
		int x = Integer.parseInt(lastScreenX);
		int y = Integer.parseInt(lastScreenY);
		setLocation(x, y);
		int w = Integer.parseInt(lastScreenW);
		int h = Integer.parseInt(lastScreenH);
		setPreferredSize(new Dimension(w, h));
		setBounds(x, y, w, h);// important
		/*
		 * if you want show full screen use, maximizeWindow() after setVisible(true)
		 */

		/*
		 * do not perform here. setVisible(true);
		 */
	}

	private void setDefaultScreenLocation() {
		setSize(new Dimension(1200, 900));
		setLocationRelativeTo(null);// set location
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				pack();
				revalidate();
				repaint();
			}
		});
	}

	private void saveCurrentScreenState() {
		String last2DViewerScreenDeviceID = getGraphicsConfiguration().getDevice().getIDstring();
		String last2DViewerScreenX = String.valueOf(getLocationOnScreen().x);
		String last2DViewerScreenY = String.valueOf(getLocationOnScreen().y);
		String last2DViewerScreenW = String.valueOf(getWidth());
		String last2DViewerScreenH = String.valueOf(getHeight());
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenDeviceID, last2DViewerScreenH);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenDeviceID,
				last2DViewerScreenDeviceID);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenX, last2DViewerScreenX);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenY, last2DViewerScreenY);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenWidth, last2DViewerScreenW);
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.Viewer2DScreenHeight, last2DViewerScreenH);
	}

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

	@Override
	public void windowOpened(WindowEvent e) {}

	@Override
	public void windowClosing(WindowEvent e) {
		logger.fine("Viewer2DScreen::Viewer2DScreen closing...");
		
		int res = PopUpMessage.showDialog(this, "Close 2D viewer ?", "Close 2D Viewer Window ?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
		if(res == JOptionPane.OK_OPTION) {
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		}else {
			setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			return;
		}
		
		if (getRoiObjManager() != null) {
			getRoiObjManager().dispose();
		}
		
		/*
		 * save window location
		 */
		saveCurrentScreenState();
		/*
		 * close StageViews
		 */
		String[] patIDs = sdm.getAllPatientList();
		if(patIDs != null) {
			for(String patID : patIDs) {
				sdm.deleteStage(patID);
			}
		}
		
		/*
		 * reset birds eye
		 */
		MainScreen ms = MainScreen.getInstance();
		ms.resetBirdsEyeView(patIDs);
		System.gc();
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
	public void windowStateChanged(WindowEvent e) {
		/*
		 * TextRoi cannot resize own if windos maximize.
		 */
		// maximize
		if ((e.getNewState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH) {
			Log.logger.fine("Viewer2DScreen was maximized.");
			repaintAllPraparat();
		} else if (e.getNewState() == JFrame.NORMAL) {
			// back to normal from maximize
			Log.logger.fine("JFrame was restored to normal size.");
			repaintAllPraparat();
		}
	}
}
