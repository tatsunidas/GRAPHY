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

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.HashMap;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;

import com.vis.configuration.Resources;
import com.vis.core.task.Task;
import com.vis.core.task.TaskContext;
import com.vis.core.task.TaskManager;
import com.vis.core.task.TaskType;
import com.vis.core.task.context.ImportingStateContext;
import com.vis.core.ui.MissingIcon;
import com.vis.core.ui.main.QRHandler;
import com.vis.core.ui.main.QueryRetrieve;
import com.vis.dicom.DicomCommunicationNode;

public class ArchiveCellEditor extends AbstractCellEditor implements TableCellEditor{
	
	final boolean isRemote;
	final DicomCommunicationNode remote;
	private final JPanel panel = new JPanel(new CardLayout());
	private final JButton button = new JButton();
	private final JProgressBar progressBar = new JProgressBar(0, 100);
	final DICOMTreeTable treeTable;
	
	DICOMNode node;
	
	final ImageIcon qrReadyIcon;
	final ImageIcon localIcon;

	private ActionListener retrieve = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			if(remote == null || node == null) {
				return;
			}
			QueryRetrieve qr = new QueryRetrieve();
			qr.prepareRetrieve(remote, node);
			qr.start();
			//fireEditingStopped();// to show progressbar
			((CardLayout) panel.getLayout()).show(panel, "Progress");
		}
	};
	
	public ArchiveCellEditor(DICOMTreeTable treeTable) {
		remote = treeTable.getRemoteDicomCommunicationNode();
		if(treeTable.isQR && remote == null) {
			throw new IllegalArgumentException("DICOMTreeTable is QR mode but remote communiication node is NULL");
		}
		isRemote = treeTable.isQR;
		this.treeTable = treeTable;
		
		localIcon = Resources.ArchivedIcon.loadIconFromResource();
		qrReadyIcon = Resources.QR_Ready_Icon.loadIconFromResource();

		panel.add(button, "Button");
		panel.add(progressBar, "Progress");
		progressBar.setStringPainted(true);

		if (isRemote) {
			button.addActionListener(retrieve);
		}

		progressBar.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleSuspendResume();
			}
		});
	}
	
	@Override
	public boolean isCellEditable(EventObject e) {
		if(e instanceof MouseEvent) {
			MouseEvent me = (MouseEvent)e;
			int colPos = treeTable.columnAtPoint(me.getPoint());
			int colPosOnTreeTable = treeTable.getArchivedColumnPosition();
			if(colPos == colPosOnTreeTable) {
				return true;
			}
		}
	    return false;
	}
    
	private void toggleSuspendResume() {
		if (node == null) {
			return;
		}
		Task t = getTaskTypeImportByCellLocationAt(node);
		ImportingStateContext isc = null;
		if (t != null) {
			isc = (ImportingStateContext) t.getContext();
		} else {
			return;
		}
		if (isc == null)
			return;
		if (!t.isSuspended()) {
			t.setSuspended(true);
			progressBar.setEnabled(false);
			progressBar.setString("Suspended");
			int res = JOptionPane.showConfirmDialog(treeTable, "Would you cancel this import/retrieve ?",
					"Cancel Importing", JOptionPane.YES_NO_OPTION);
			if (res == JOptionPane.YES_OPTION) {
				// stop
				t.setStopped(true);
				reset(false);
				//WindowManager.getMainScreen().updateQRTreeTables();
			} else {
				// resume
				t.setSuspended(false);
				progressBar.setString(null);
				progressBar.setEnabled(true);
				((CardLayout) panel.getLayout()).show(panel, "Progress");
			}
		}else {
			progressBar.setString(null);
			((CardLayout) panel.getLayout()).show(panel, "Progress");
		}
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
		
		DICOMTreeTable treeTable = (DICOMTreeTable)table;
		DICOMNode node = treeTable.nodeForRow(row);
		if(node ==null) return panel; 
		
		setRetrieveDICOMNode(node);
		Task t = getTaskTypeImportByCellLocationAt(node);
//		ImportingStateContext isc = null;
//		if(t != null) {
//			isc = (ImportingStateContext)t.getContext();
//		}
		if (t != null /* importing or suspending */) {/* STUDY Level */
			final ImportingStateContext isc = (ImportingStateContext) t.getContext();
			if (progressBar.getMinimum() == Integer.MIN_VALUE) {
				progressBar.setMinimum(0);
				progressBar.setMaximum(isc.totalSize());
			}
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					progressBar.setValue((Integer) isc.currentIndex() + 1);// 1 base for progress bar
				}
			});
			
			if(isc.totalSize() == (isc.currentIndex() + 1) || t.isStopped()) {
				reset(false/*retrievable*/);
			}else {
				((CardLayout) panel.getLayout()).show(panel, "Progress");
			}
		}else {
			if(!isRemote) {//HOME
				if (node.getLevel() == DICOMNode.STUDY || node.getLevel() == DICOMNode.SERIES) {
					reset(false);
				} else if (node.getLevel() == DICOMNode.IMAGE){
					if (QRHandler.inLocalInstance(node)) {
						reset(false);
					} else {//missing
						button.setIcon(new MissingIcon(Color.red, treeTable.getRowHeight(), treeTable.getRowHeight()));
						button.setEnabled(false);
						((CardLayout) panel.getLayout()).show(panel, "Button");
					}
				}
			}else {//REMOTE
				if (node.getLevel() == DICOMNode.STUDY) {
					if (QRHandler.archivedInLocalAllInstance(node)) {
						reset(false);
					} else {
						reset(true);
					}
				} else if (node.getLevel() == DICOMNode.SERIES) {
					if (QRHandler.archivedInAllInstancesRelatedSeries(node)) {
						reset(false);
					} else {
						reset(true);
					}
				} else if (node.getLevel() == DICOMNode.IMAGE){
					if (QRHandler.inLocalInstance(node)) {
						reset(false);
					} else {//retrievable
						reset(true);
					}
				}
			}
		}
		return panel;
	}
	
	public void cancelCellEditing() {
		super.fireEditingStopped();
	}
	
	private Task getTaskTypeImportByCellLocationAt(DICOMNode node ) {
		if(node.getLevel()==DICOMNode.STUDY) {
			TaskManager tm = TaskManager.getInstance();
			HashMap<Long, Task> tasks = tm.getAllTask();
			for (long tid : tasks.keySet()) {
				Task t = tasks.get(tid);
				TaskContext con = t.getContext();
				if (con instanceof ImportingStateContext && con.getType()==TaskType.TypeImport) {
					ImportingStateContext isc = (ImportingStateContext) con;
					if(isc.getThreadId() == tid) {
						Thread thr = tm.getThread(tid);
						if(t != null && thr.isAlive() && isc.getStudyUID().equals(node.getData(DICOMNode.StudyInstanceUID))) {
							return t;
						}
					}
				}
			}
		}
		return null;
	}
	
	private void reset(boolean retrievable) {
		if(!retrievable) {
			button.setEnabled(false);
			button.setIcon(localIcon);
			button.removeActionListener(retrieve);
		}else {
			button.setEnabled(true);
			button.setIcon(qrReadyIcon);
			button.addActionListener(retrieve);
		}
		//reset progressbar
		progressBar.setMinimum(Integer.MIN_VALUE);
		progressBar.setMaximum(Integer.MAX_VALUE);
		SwingUtilities.invokeLater(() -> {
			((CardLayout) panel.getLayout()).show(panel, "Button");
			cancelCellEditing();
	        treeTable.revalidate();  // レイアウト更新
	        treeTable.repaint();     // 画面を再描画
	    });
	}
	
	private void setRetrieveDICOMNode(DICOMNode node) {
		this.node = node;
	}
}
