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

import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.reporting.ReportService;
import com.vis.core.reporting.sr.SopClassUtil;
import com.vis.core.reporting.ui.ReportListPanel;
import com.vis.core.task.Task;
import com.vis.core.ui.qr.QueryRetrieve;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.db.DatabaseHandler;

/**
 * 
 * @author tatsunidas
 *
 */
public class TreeTableMouseListener implements MouseListener{
	
	private DICOMTreeTable treeTable;
	private final boolean isRemote;
	
	public TreeTableMouseListener(DICOMTreeTable treeTable) {
		this.treeTable=treeTable;
		this.treeTable.addMouseListener(this);
		isRemote = treeTable.isQR;
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
			if(!isRemote) {
				int row = treeTable.rowAtPoint(e.getPoint());
				int arc_col = treeTable.getColumnPosition(DICOMTreeTableModel.ArchivedCol);
				int col = treeTable.columnAtPoint(e.getPoint());
				DICOMNode target = treeTable.nodeForRow(row);
				if(target == null) {
					return;
				}
				// Click on the Report column -> open the study's report list (skip Bird's eye).
				if(col == treeTable.getColumnPosition(DICOMTreeTableModel.ReportCol)) {
					openReportListPopup(target);
					return;
				}
				if(arc_col == col) {
					Task t = treeTable.getTaskTypeImportAt(target);
					if(t != null) {
						return;
					}
				}
				/*
				 * show on the bird's eye
				 */
				try {
					WindowManager.getMainScreen().setCursor(new Cursor(Cursor.WAIT_CURSOR));
					WindowManager.getMainScreen().showImagesOnBirdsEye();
				}finally {
					WindowManager.getMainScreen().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				}
			}else {
				//do nothiing
			}
		}else if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
			int columnIndex = treeTable.columnAtPoint(e.getPoint());
			//Datasets(tree icon column) and Archived columns have TreeTableModel.class as ColumnClass.
			if (treeTable.getColumnClass(columnIndex) == TreeTableModel.class) {
				return;
			}
			// The Report column handles its own (single) click; ignore double-clicks on it.
			if (columnIndex == treeTable.getColumnPosition(DICOMTreeTableModel.ReportCol)) {
				return;
			}
			int row = treeTable.rowAtPoint(e.getPoint());
//			int row = treeTable.getTree().getClosestRowForLocation(e.getX(), e.getY());//same result
			DICOMNode node = treeTable.nodeForRow(row);
			if(node == null) return;
			final Viewer2DScreen viewer = Viewer2DScreen.getInstance();//WindowManager.getWindow(ConfigInfo.D2ViewerWindow.toString());/*may cause null*/
			if (viewer == null) {
				Log.logger.warning("2D Viewer missing...");
				return;
			}
			if(!isRemote) {
				try {
					WindowManager.getMainScreen().setCursor(new Cursor(Cursor.WAIT_CURSOR));
					// SR-family objects (SR/RDSR/KO) open in the SR HTML viewer, not the image
					// viewer — do not launch the empty 2D viewer for them.
					if (routeSrNode(node)) {
						return;
					}
					ArrayList<DICOMNode> clicked = new ArrayList<>();
					clicked.add(node);
					viewer.loadImagesOnStage(clicked);
					viewer.setVisible(true);
					viewer.toFront();
				}finally {
					WindowManager.getMainScreen().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				}
			}else{
				String msg = "GRAPHY will retrieve to show images on viewer.\n";
				msg += "YES : Retrieve to DB and then show images on viewer.\n";
				msg += "NO : Cancel";
				int res = JOptionPane.showOptionDialog(
						treeTable, 
						msg, 
						"Load images from Remote DB?",//title 
						JOptionPane.YES_NO_OPTION, 
						JOptionPane.QUESTION_MESSAGE,
						null,
						new String[] {"Retrieve", "Cancel"},
						"Retrieve"	);
				if(res == JOptionPane.YES_OPTION) {
					/*
					 * node is selected by mouse action.
					 * Do not use viewer.loadImagesOnStageFromExternal();
					 */
					try {
						QueryRetrieve qr = new QueryRetrieve(false/* queryOnly */);
						qr.prepareRetrieve(treeTable.getRemoteDicomCommunicationNode(), node);
						qr.start();
						qr.monitorTasks();
						try {
							qr.getThread().join(); // waiting finish qr task on background.
						} catch (InterruptedException ie) {
							Log.logger.warning(ie.getLocalizedMessage());
						}
					}finally {
						WindowManager.getMainScreen().setCursor(new Cursor(Cursor.WAIT_CURSOR));
						viewer.loadImagesOnStage((String) node.getData(DICOMNode.PatientID),
								(String) node.getData(DICOMNode.StudyInstanceUID), null, null, null);
						viewer.setVisible(true);
						viewer.toFront();
						WindowManager.getMainScreen().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					}
				}
			}
		}
	}

	/**
	 * If the double-clicked node is an SR-family document (SR/RDSR/KO), open it in the
	 * SR HTML viewer and report that it was handled (so the caller skips the image viewer).
	 * Only IMAGE and SERIES level nodes are short-circuited here; a STUDY/PATIENT node may
	 * mix images and SR, and the per-series guard in
	 * {@link Viewer2DScreen#loadImagesOnStage(String, String, String, String[], String)}
	 * routes the SR series within it.
	 *
	 * @return true if the node was an SR object and has been routed to the SR viewer.
	 */
	private boolean routeSrNode(DICOMNode node) {
		if (isRemote || node == null) {
			return false;
		}
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			return false;
		}
		int level = node.getLevel();
		String patID = node.getData(DICOMNode.PatientID);
		String studyUID = node.getData(DICOMNode.StudyInstanceUID);
		if (level == DICOMNode.IMAGE) {
			String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
			String sopUID = node.getData(DICOMNode.SOPInstanceUID);
			if (SopClassUtil.isSrFamily(db.getValueFromImage("SOPClassUID", patID, studyUID, seriesUID, sopUID))) {
				new ReportService().openSr(patID, studyUID, seriesUID, sopUID);
				return true;
			}
		} else if (level == DICOMNode.SERIES) {
			String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
			ArrayList<String> sops = db.getInstanceUidList(patID, studyUID, seriesUID);
			if (sops == null || sops.isEmpty()) {
				return false;
			}
			String sop0 = sops.get(0);
			String cls = db.getValueFromImage("SOPClassUID", patID, studyUID, seriesUID, sop0);
			boolean sr = SopClassUtil.isSrFamily(cls);
			if (!sr && cls == null) {
				sr = SopClassUtil.isSrModality(db.getValueFromSeries("Modality", patID, studyUID, seriesUID));
			}
			if (sr) {
				new ReportService().openSr(patID, studyUID, seriesUID, sop0);
				return true;
			}
		}
		return false;
	}

	/**
	 * Open the report list for the clicked study (the Report column marker). Does nothing for
	 * non-study rows or studies that have no reports.
	 */
	private void openReportListPopup(DICOMNode node) {
		if (node == null || node.getLevel() != DICOMNode.STUDY) {
			return;
		}
		String state = node.getData(DICOMNode.ReportState);
		if (state == null || "none".equals(state)) {
			return;
		}
		String patID = node.getData(DICOMNode.PatientID);
		String studyUID = node.getData(DICOMNode.StudyInstanceUID);
		String studyDate = node.getData(DICOMNode.StudyDate);
		ReportListPanel panel = new ReportListPanel();
		panel.setContext(patID, studyUID, studyDate);
		java.awt.Window owner = WindowManager.getMainScreen();
		javax.swing.JDialog d = new javax.swing.JDialog(owner,
				Resources.i18n("Reporting.window.reports.title"), java.awt.Dialog.ModalityType.MODELESS);
		d.setContentPane(panel);
		d.setSize(700, 460);
		d.setLocationRelativeTo(owner);
		d.setVisible(true);
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
