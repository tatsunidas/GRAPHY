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
import com.vis.core.ui.main.QRHandler;
import com.vis.core.ui.main.QueryRetrieve;
import com.vis.core.util.Utils;
import com.vis.dicom.DicomCommunicationNode;
//import com.vis.dimse.delegate.QRHandler;
//import com.vis.dimse.delegate.QueryRetrieve;
//import com.vis.ui.form.dialog.DicomImporter;

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
			new Thread(qr).start();
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
		System.out.println("Editor "+row+"  "+col);
		ImportingStateContext isc = getImportingCellStateAt(row, col);
		if (isc != null) {
			isc.getSuspendButton().addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					// suspend current import thread
					isc.getQueryRetrieve().setSuspended(true);
					int res = JOptionPane.showConfirmDialog(table, "Would you cancel this import ?", "Cancel Importing",
							JOptionPane.YES_NO_OPTION);
					if (res == JOptionPane.YES_OPTION) {
						// stop
						isc.getQueryRetrieve().setStopped(true);
						isc.getQueryRetrieve().stopImport();
						importInterupted(isc);
						WindowManager.getMainScreen().updateQRTreeTables();
					} else {
						// resume
						isc.getQueryRetrieve().resumeImport();
					}
				}
			});
			isc.getSuspendButton().setText("Suspend");// NEEDED
			return isc.getProgressBar();// keep return progressbar which added cancelBtn.
		/* Waiting State */
		} else {
			DICOMNode node = treeTable.nodeForRow(row);
			if (node == null) {
				return null;
			}
			if (node.getLevel() == DICOMNode.STUDY) {
				if (QRHandler.archivedInLocalAllInstance(node)) {
					setRetrievable(false,node);
					return this;
				} else {
					setRetrievable(true,node);
					return this;
				}
			} else if (node.getLevel() == DICOMNode.SERIES) {
				if (QRHandler.archivedInAllInstancesRelatedSeries(node)) {
					setRetrievable(false,node);
					return this;
				} else {
					setRetrievable(true,node);
					return this;
				}
			} else if (node.getLevel() == DICOMNode.IMAGE){
				if (QRHandler.inLocalInstance(node)) {
					setRetrievable(false,node);
					return this;
				} else {
					setRetrievable(true,node);
					return this;
				}
			}
			return this;
		}
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
	public ImportingStateContext getParticularImportingStateContext(String[] infoset) {
		for(ImportingStateContext isc:importingList) {
//			System.out.println(isc.getInfoSet());//why, variable address...
//			System.out.println(Arrays.toString(infoset));//correct...
			String patID = isc.getInfoSet()[0];
			String studyIUID = isc.getInfoSet()[1];
			
			if((patID+studyIUID).equals(infoset[0]+infoset[1])) {
				return isc;
			}
		}
		return null;
	}
	
	public void setCellStateLocationInCurrentTableView(String suid, int importingRow,int importingCol) {
		if(importingList.size()<1) {
			Log.logger.info("please do addImportingState first.");
			return;
		}
		for(ImportingStateContext isc:importingList) {
			if(isc.getSuid().equals(suid)) {
				isc.setImportingRow(importingRow);
				isc.setImportingCol(importingCol);
			}
		}
	}
	
	public void addImportingState(String[] infoset, QueryRetrieve qrTask, int total) {
		ImportingStateContext isc = new ImportingStateContext(infoset,qrTask);
		isc.setTotal(total);
		importingList.add(isc);
	}
	
	/*
	 * progress is 1 start based.
	 */
	public void setProgressAt(String[] infoset, int row, int col,int progress) {
//		fireEditingStopped();
		ImportingStateContext context = getParticularImportingStateContext(infoset);
		if(context == null) {
			return;
		}
		context.setImportingRow(row);
		context.setImportingCol(col);
		/* importing done is not recognize here, see, while loop ending */
//		if (progress == context.getTotal()) {
//			importingIsDone(infoset);
//			return;
//		}
		//see, DicomImporter::updateProgress
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				context.getProgressBar().setValue(progress);//already increment 1, see performRetrieve()
				JButton btn = (JButton) context.getProgressBar().getComponent(0);
				btn.setText((progress) + " / " + context.getTotal());
				btn.repaint();
				context.getProgressBar().revalidate();
				context.getProgressBar().repaint();
			}
		});
	}
	
	private ImportingStateContext getImportingCellStateAt(int row, int col) {
		if(importingList.size() == 0) {
			return null;
		}
		for (int i = 0; i < importingList.size(); i++) {
			ImportingStateContext isc = importingList.get(i);
			int r = isc.getImportingRow();
			int c = isc.getImportingCol();
			if (r == row && c == col) {
				return isc;
			}
		}
		return null;
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
		if(Utils.isDebug) {
			Log.logger.info("import interupted : " + isc.getSuid());
			Log.logger.info("After cleanup, still remain importing list size is " + importingList.size());
		}
		
		isc = null;//for renderer
	}
	
	public void importingIsDone(String[] infoset) {
		ImportingStateContext isc = getParticularImportingStateContext(infoset);
		completionList.add(isc);
		//init
		if(importingList.size() == completionList.size()) {
			completionList = null;
			importingList = null;
			completionList = new ArrayList<ImportingStateContext>();
			importingList = new ArrayList<ImportingStateContext>();
			if(Utils.isDebug) {
				Log.logger.info("All retrieve task completed");
			}
		}
		isc = null;
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
