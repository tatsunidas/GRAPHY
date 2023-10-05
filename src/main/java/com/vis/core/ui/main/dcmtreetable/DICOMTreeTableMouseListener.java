package com.vis.core.ui.main.dcmtreetable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

import com.vis.core.facade.WindowManager;
import com.vis.db.DatabaseHandler;

//import com.vis.database.DatabaseHandler;
//import com.vis.ui.context.ApplicationContext;
//import com.vis.ui.toolbar.PatientIDPadding;
//import com.vis.viewer2d.ui.frame.Viewer2DFrame;//TODO 20230901

public class DICOMTreeTableMouseListener implements MouseListener{
	
	DICOMTreeTable treeTable;
	
	public DICOMTreeTableMouseListener(DICOMTreeTable treeTable) {
		// TODO Auto-generated constructor stub
		this.treeTable=treeTable;
		this.treeTable.addMouseListener(this);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		//right click
		if (SwingUtilities.isRightMouseButton(e)) {
			//https://stackoverflow.com/questions/517704/right-click-context-menu-for-java-jtree
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
			DICOMNode target = treeTable.nodeForRow(row);//ATTENTION, getParent return null.
			if(target == null) {
				return;
			}
			HashMap<String,String> infoset = null;
			if(target.getLevel() == DICOMNode.STUDY) {
				String patID = target.getData(DICOMNode.PatientID);
				String studyUID = target.getData(DICOMNode.StudyInstanceUID);
				String seUID = target.getFirstChild().getData(DICOMNode.SeriesInstanceUID);
				infoset = DatabaseHandler.getInstance().getInfoset(patID, studyUID, seUID);
	    	}else if(target.getLevel() == DICOMNode.SERIES || target.getLevel() == DICOMNode.IMAGE) {
	    		String patID = target.getData(DICOMNode.PatientID);
				String studyUID = target.getData(DICOMNode.StudyInstanceUID);
				String seUID = target.getData(DICOMNode.SeriesInstanceUID);
				infoset = DatabaseHandler.getInstance().getInfoset(patID, studyUID, seUID);
	    	}
			String topDockName = WindowManager.getMainScreen().getCurrentTreeTableManager().getTopTabNickname();
			infoset.put("Nickname", topDockName);
			//20231004 TODO
//			WindowManager.getMainScreen().setInfoset(infoset);
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
//			Viewer2DFrame viewer = ApplicationContext.getInstance().getViewer2DFrame();
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
