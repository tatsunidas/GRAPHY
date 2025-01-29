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
package com.vis.core.ui.main.dcmtreetable;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.db.DatabaseHandler;

/**
 * 
 * @author tatsunidas
 *
 */
public class TreeTableMouseListener implements MouseListener{
	
	private DICOMTreeTable treeTable;
	
	public TreeTableMouseListener(DICOMTreeTable treeTable) {
		this.treeTable=treeTable;
		this.treeTable.addMouseListener(this);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		//right click
		if (SwingUtilities.isRightMouseButton(e) && !SwingUtilities.isLeftMouseButton(e) && !SwingUtilities.isMiddleMouseButton(e)) {
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
//			ArrayList<DICOMNode> nodes = WindowManager.getMainScreen().getSelectedNode();
//			if (nodes == null || nodes.size() < 1) {
//				System.out.println("Viewer2DWindow is needed DICOMNode selection. return.");
//				return;
//			}
			
			Viewer2DScreen viewer = (Viewer2DScreen) WindowManager.getWindow(ConfigInfo.D2ViewerWindow.toString());
			if (viewer == null) {
				viewer = Viewer2DScreen.getInstance();
			}
			
			int level = node.getLevel();
			String patID = node.getData(DICOMNode.PatientID);
			String studyUID = node.getData(DICOMNode.StudyInstanceUID);
			DatabaseHandler db = DatabaseHandler.getInstance();
			
			if (level == DICOMNode.STUDY) {
				// search series
				ArrayList<String> seriesUIDs = db.getSeriesUidList(patID, studyUID);
				for(String seriesUID:seriesUIDs) {
					ArrayList<String> instUIDs = db.getInstanceUidList(patID, studyUID, seriesUID);
					String frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID" ,patID, studyUID, seriesUID, instUIDs.get(0));
					if (instUIDs.size() > 0) {
						//load image through db
						viewer.loadImagesOnStage(patID, studyUID, seriesUID,
								instUIDs.toArray(new String[instUIDs.size()]), frameOfRefUID);
					}else {
						System.out.println("This study does not has any images..., pid:"+patID+", studyuid:"+studyUID);
					}
				}
			}else if(level == DICOMNode.SERIES) {
				String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
				ArrayList<String> instUIDs = db.getInstanceUidList(patID, studyUID, seriesUID);
				if (instUIDs.size() > 0) {
					String frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID" ,patID, studyUID, seriesUID, instUIDs.get(0));
					viewer.loadImagesOnStage(patID, studyUID, seriesUID,
							instUIDs.toArray(new String[instUIDs.size()]), frameOfRefUID);
				}else {
					System.out.println("This study does not has any images..., pid:"+patID+", studyuid:"+studyUID);
				}
			}else if(level == DICOMNode.IMAGE){
				String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
				String instUID = node.getData(DICOMNode.SOPInstanceUID);
				String frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID" ,patID, studyUID, seriesUID, instUID);
				viewer.loadImagesOnStage(patID, studyUID, seriesUID, new String[] {instUID}, frameOfRefUID);
			}
			if (!viewer.isVisible()) {
				viewer.setVisible(true);
			}
			viewer.revalidate();
			viewer.repaint();
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
