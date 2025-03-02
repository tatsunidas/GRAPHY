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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EventObject;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.table.TableCellEditor;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.task.context.ImportingStateContext;
import com.vis.core.ui.main.QRHandler;
import com.vis.core.ui.main.QueryRetrieve;
import com.vis.core.util.Utils;
import com.vis.dicom.DicomCommunicationNode;

/**
 * @author tatsunidas
 */

public class QRStateCellEditor extends JButton implements TableCellEditor{
	
	private static final long serialVersionUID = 1L;
	
	ImageIcon qrReadyIcon;
	ImageIcon localIcon;

	ArrayList<ImportingStateContext> importingList = new ArrayList<>();
	ArrayList<ImportingStateContext> completionList = new ArrayList<>();
	int progress;
	int total;
	DICOMNode node;
	DicomCommunicationNode remote;
	
	protected EventListenerList listenerList = new EventListenerList();
	protected ChangeEvent changeEvent = new ChangeEvent(this);
	protected ActionListener retrieve = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			//retrieve
			QueryRetrieve qr = new QueryRetrieve();
			qr.prepareRetrieve(remote,node);
			qr.start();
			fireEditingStopped();//to show progressbar
		}
	};
	
	public QRStateCellEditor(DicomCommunicationNode remote) {
		super();
		qrReadyIcon = Resources.QR_Ready_Icon.loadIconFromResource();
		localIcon = Resources.LocalIcon.loadIconFromResource();
		this.remote = remote;
		setName(remote.getNickname());
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object obj, boolean selected, int row, int col) {
		/* Retrieving State */
		DICOMTreeTable treeTable = (DICOMTreeTable)table;
		ImportingStateContext isc = null;//getImportingCellStateAt(row, col);
		if (isc != null) {
			//TODO 20231010
//			addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent arg0) {
//					// suspend current import thread
//					isc.suspend();
//					int res = JOptionPane.showConfirmDialog(table, "Would you cancel this import ?", "Cancel Importing",
//							JOptionPane.YES_NO_OPTION);
//					if (res == JOptionPane.YES_OPTION) {
//						// stop
//						isc.stop();
//						importInterupted(isc);
//						WindowManager.getMainScreen().updateQRTreeTables();
//					} else {
//						// resume
//						isc.resume();
//					}
//				}
//			});
			setText("Suspend");// NEEDED
		/* Waiting State */
		} else {
			DICOMNode node = treeTable.nodeForRow(row);
			if (node == null) {
				return null;
			}
			if (node.getLevel() == DICOMNode.STUDY) {
				if (QRHandler.archivedInLocalAllInstance(node)) {
					setRetrievable(false,node);
				} else {
					setRetrievable(true,node);
				}
			} else if (node.getLevel() == DICOMNode.SERIES) {
				if (QRHandler.archivedInAllInstancesRelatedSeries(node)) {
					setRetrievable(false,node);
				} else {
					setRetrievable(true,node);
				}
			} else if (node.getLevel() == DICOMNode.IMAGE){
				if (QRHandler.inLocalInstance(node)) {
					setRetrievable(false,node);
				} else {
					setRetrievable(true,node);
				}
			}
		}
		return this;
	}
	
	private void setRelatedDICOMNode(DICOMNode node) {
		this.node = node;
	}
	
	private void setRetrievable(boolean retrievable, DICOMNode node) {
		if(!retrievable) {
			setEnabled(false);
			setIcon(localIcon);
			removeActionListener(retrieve);
		}else {
			setEnabled(true);
			setIcon(qrReadyIcon);
			addActionListener(retrieve);
		}
		setRelatedDICOMNode(node);
	}
	
	/*
	 * usually, patID+studyIUID+""+""
	 * "" are seriesIUID and sopIUID, these UIDs not include when initialize.
	 */
	//20231010
//	public ImportingStateContext getParticularImportingStateContext(String[] infoset) {
//		for(ImportingStateContext isc:importingList) {
////			System.out.println(isc.getInfoSet());//why, variable address...
////			System.out.println(Arrays.toString(infoset));//correct...
//			String patID = isc.getInfoSet()[0];
//			String studyIUID = isc.getInfoSet()[1];
//			
//			if((patID+studyIUID).equals(infoset[0]+infoset[1])) {
//				return isc;
//			}
//		}
//		return null;
//	}
	
	//20231010
	public void setCellStateLocationInCurrentTableView(String suid, int importingRow,int importingCol) {
//		if(importingList.size()<1) {
//			Log.logger.info("please do addImportingState first.");
//			return;
//		}
//		for(ImportingStateContext isc:importingList) {
//			if(isc.getSuid().equals(suid)) {
//				isc.setImportingRow(importingRow);
//				isc.setImportingCol(importingCol);
//			}
//		}
	}
	
	
	public ArrayList<ImportingStateContext> getImportingStateContext(){
		return importingList;
	}
	
	public void importInterupted(ImportingStateContext isc) {
		completionList.add(isc);
		int sum = 0;
		for (ImportingStateContext completed : completionList) {
			if (importingList.contains(completed)) {
				sum++;
			}
		}
		//init
		if(importingList.size() == sum) {
			completionList = null;
			importingList = null;
			completionList = new ArrayList<ImportingStateContext>();
			importingList = new ArrayList<ImportingStateContext>();
		}
		
		isc = null;//for renderer
	}
	//TODO
	//20231010
	public void importingIsDone(String[] infoset) {
//		ImportingStateContext isc = getParticularImportingStateContext(infoset);
//		completionList.add(isc);
//		//init
//		if(importingList.size() == completionList.size()) {
//			completionList = null;
//			importingList = null;
//			completionList = new ArrayList<ImportingStateContext>();
//			importingList = new ArrayList<ImportingStateContext>();
//			if(Utils.isDebug) {
//				Log.logger.info("All retrieve task completed");
//			}
//		}
//		isc = null;
	}

	@Override
	public void addCellEditorListener(CellEditorListener listener) {
		listenerList.add(CellEditorListener.class, listener);
	}

	@Override
	public void cancelCellEditing() {
		fireEditingCanceled();
	}

	protected void fireEditingStopped() {
		CellEditorListener listener;
		Object[] listeners = listenerList.getListenerList();
		for (int i = 0; i < listeners.length; i++) {
			if (listeners[i] == CellEditorListener.class) {
				listener = (CellEditorListener) listeners[i + 1];
				listener.editingStopped(changeEvent);
			}
		}
	}

	private void fireEditingCanceled() {
		CellEditorListener listener;
		Object[] listeners = listenerList.getListenerList();
		for (int i = 0; i < listeners.length; i++) {
			if (listeners[i] == CellEditorListener.class) {
				listener = (CellEditorListener) listeners[i + 1];
				listener.editingCanceled(changeEvent);
			}
		}
	}

	@Override
	public Object getCellEditorValue() {
		return null;//ここでコンテクストを返したら良いと思う
	}

	@Override
	public boolean isCellEditable(EventObject arg0) {
		return true;// keep always true to push button
	}

	@Override
	public void removeCellEditorListener(CellEditorListener listener) {
		listenerList.remove(CellEditorListener.class, listener);
	}

	@Override
	public boolean shouldSelectCell(EventObject arg0) {
		return true;
	}

	@Override
	public boolean stopCellEditing() {
		fireEditingStopped();
		return true;
	}
}
