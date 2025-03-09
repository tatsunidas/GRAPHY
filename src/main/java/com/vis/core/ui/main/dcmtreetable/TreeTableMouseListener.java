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

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.task.Task;
import com.vis.core.ui.main.QueryRetrieve;
import com.vis.core.view.D2.ui.Viewer2DScreen;

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
				if(arc_col == col) {
					Task t = treeTable.getTaskTypeImportAt(target);
					if(t != null) {
						return;
					}
				}
				/*
				 * show on the bird's eye
				 */
				WindowManager.getMainScreen().showImagesOnBirdsEye();
			}else {
				//do nothiing
			}
		}else if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
			int columnIndex = treeTable.columnAtPoint(e.getPoint());
			//Datasets(tree icon column) and Archived columns have TreeTableModel.class as ColumnClass.
			if (treeTable.getColumnClass(columnIndex) == TreeTableModel.class) {
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
				ArrayList<DICOMNode> clicked = new ArrayList<>();
				clicked.add(node);
				viewer.loadImagesOnStage(clicked);
				viewer.setVisible(true);
				viewer.toFront();
			}else{
				String msg = "GRAPHY will retrieve to show images on viewer.\n";
				msg += "YES : Retrieve to DB and then show images on viewer.\n";
				msg += "NO : Cancel";
				int res = JOptionPane.showOptionDialog(
						treeTable, 
						"Load images from Remote DB?", 
						msg, 
						JOptionPane.YES_NO_OPTION, 
						JOptionPane.QUESTION_MESSAGE,
						null,
						new String[] {"Retrieve", "Cancel"},
						"Retrieve"	);
				if(res == JOptionPane.YES_OPTION) {
					QueryRetrieve qr = new QueryRetrieve(false/*queryOnly*/);
					qr.prepareRetrieve(treeTable.getRemoteDicomCommunicationNode(), node, false/* false means will load to db*/);
					qr.start();
					qr.monitorTasks();
					new Thread(() -> {
						try {
							qr.getThread().join(); // waiting finish qr task on background.
							Thread.sleep(1000);
						} catch (InterruptedException ie) {
							Log.logger.warning(ie.getLocalizedMessage());
						}
						SwingUtilities.invokeLater(() -> {
							viewer.loadImagesOnStage((String) node.getData(DICOMNode.PatientID),
									(String) node.getData(DICOMNode.StudyInstanceUID), null, null, null);
							viewer.setVisible(true);
							viewer.toFront();
						});
					}).start();
				}
			}
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
