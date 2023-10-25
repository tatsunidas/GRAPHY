package com.vis.core.ui.main.dcmtreetable;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;

import javax.swing.SwingUtilities;

import com.vis.core.facade.WindowManager;
import com.vis.db.DatabaseHandler;

public class TreeTableMouseListener implements MouseListener{
	
	private DICOMTreeTable treeTable;
	
	public TreeTableMouseListener(DICOMTreeTable treeTable) {
		this.treeTable=treeTable;
		this.treeTable.addMouseListener(this);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		//right click
		if (SwingUtilities.isRightMouseButton(e)) {
			/*
			 * example
			 */
//			Point clicked = treeTable.getPopupLocation(e);//return null...why
			/*
			 * sample for right click
			 */
//	        int row = treeTable.getTree().getClosestRowForLocation(e.getX(), e.getY());
//			JPopupMenu popup = new JPopupMenu();
//			JMenuItem item1 = new JMenuItem("test-r-click");
//			item1.addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent arg0) {
//					System.out.println("right clicked at "+ row +", do something");
//					ArrayList<DICOMNode> selected = treeTable.getSelectedNodes();
//				}
//			});
//			popup.add(item1);
//			popup.show(e.getComponent(), e.getX(), e.getY());
		}else if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() != 2) {
			int row = treeTable.getTree().getClosestRowForLocation(e.getX(), e.getY());
			DICOMNode target = treeTable.nodeForRow(row);
			if(target == null) {
				return;
			}
			/*
			 * show on the bird's eye
			 */
			WindowManager.getMainScreen().showImagesOnBirdsEye();
		}else if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
			/*
			 * double clicked
			 * show 2d viewer
			 */
			int row = treeTable.getTree().getClosestRowForLocation(e.getX(), e.getY());
			DICOMNode node = treeTable.nodeForRow(row);//ATTENTION, getParent return null.
//			ArrayList<DICOMNode> nodes = mediator.getMainScreen().getSelectedNode();
//			if (nodes == null || nodes.size() < 1) {
//				System.out.println("Viewer2DWindow is needed DICOMNode selection. return.");
//				return;
//			}
			
			/*
			 * TODO 20230901
			 */
//			Viewer2DScreen viewer = ApplicationContext.getInstance().getViewer2DScreen();
//			if (viewer == null) {
//				// this case never occur ?
//				System.out.println("Viewer2DWindow is NULL !! Please restart graphy.");
//				return;
//			}
//			if (!viewer.isVisible()) {
//				viewer.initContents();
//			}
			
			
			int level = node.getLevel();
			String patID = node.getData(DICOMNode.PatientID);
			String studyUID = node.getData(DICOMNode.StudyInstanceUID);
			DatabaseHandler db = DatabaseHandler.getInstance();
			

			/*
			 * TODO 20230901
			 */
			
//			if (level == DICOMNode.STUDY) {
//				// search series
//				ArrayList<String> seriesUIDs = db.getSeriesUidList(patID, studyUID);
//				for(String seriesUID:seriesUIDs) {
//					ArrayList<String> instUIDs = db.getInstanceUidList(patID, studyUID, seriesUID);
//					String frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID" ,patID, studyUID, seriesUID, instUIDs.get(0));
//					if (instUIDs.size() > 0) {
//						//load image through db
//						viewer.loadImagesOnStage(patID, studyUID, seriesUID,
//								instUIDs.toArray(new String[instUIDs.size()]), frameOfRefUID);
//					}else {
//						System.out.println("This study does not has any images..., pid:"+patID+", studyuid:"+studyUID);
//					}
//				}
//			}else if(level == DICOMNode.SERIES) {
//				String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
//				ArrayList<String> instUIDs = db.getInstanceUidList(patID, studyUID, seriesUID);
//				if (instUIDs.size() > 0) {
//					String frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID" ,patID, studyUID, seriesUID, instUIDs.get(0));
//					viewer.loadImagesOnStage(patID, studyUID, seriesUID,
//							instUIDs.toArray(new String[instUIDs.size()]), frameOfRefUID);
//				}else {
//					System.out.println("This study does not has any images..., pid:"+patID+", studyuid:"+studyUID);
//				}
//			}else if(level == DICOMNode.IMAGE){
//				String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
//				String instUID = node.getData(DICOMNode.SOPInstanceUID);
//				String frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID" ,patID, studyUID, seriesUID, instUID);
//				viewer.loadImagesOnStage(patID, studyUID, seriesUID, new String[] {instUID}, frameOfRefUID);
//			}
//			viewer.setVisible(true);
//			viewer.revalidate();
//			viewer.repaint();
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent arg0) {}

	@Override
	public void mousePressed(MouseEvent arg0) {}

	@Override
	public void mouseReleased(MouseEvent arg0) {}

}
