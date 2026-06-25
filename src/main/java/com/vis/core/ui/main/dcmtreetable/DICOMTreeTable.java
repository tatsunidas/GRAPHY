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

import java.awt.*;
import java.awt.dnd.Autoscroll;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.table.*;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.log.Log;
import com.vis.core.task.Task;
import com.vis.core.task.TaskContext;
import com.vis.core.task.TaskManager;
import com.vis.core.task.TaskType;
import com.vis.core.task.context.ImportingStateContext;
import com.vis.core.util.PropertiesUtil;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;


import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.List;

/**
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class DICOMTreeTable extends JTreeTable implements Autoscroll {
	
	private final int col_minWidth = 90;
	private final int rowHeight = 24;
	
	public boolean isQR = false;
	private DicomCommunicationNode remote;
	protected Point viewLocation;
	
	// drop target
	DragSource dragSource = DragSource.getDefaultDragSource();
	protected DICOMNodeDragSourceListener sourceListener;
	protected ArrayList<DICOMNode> draggedComponent;
	
	private boolean treeEditable = true;
	private boolean showsIcons = true;//tree icon
	
	public DICOMTreeTable(DICOMTreeTableModel model, boolean isQR, DicomCommunicationNode remote) {
		super(model);
		this.isQR = isQR;
		if(this.isQR) {
			if(remote == null) {
				throw new IllegalArgumentException("DICOMTreeTable QR mode is needed remote communication node information.");
			}
			this.remote = remote;
		}
		//keep default cell renderer with JTreeTable.TreeTableCellRenderer
		//change default cell editor to DICOMTreeTableCellEditor, to handle Archive col actions.
		setDefaultEditor(TreeTableModel.class, new DICOMTreeTableCellEditor(this));
		ArchiveCellRenderer acr = new ArchiveCellRenderer(isQR);
		ArchiveCellEditor ace = new ArchiveCellEditor(this);
		getColumn(DICOMTreeTableModel.ArchivedCol).setCellRenderer(acr);
		getColumn(DICOMTreeTableModel.ArchivedCol).setCellEditor(ace);

		// Study-level Report column: icon marker (clicks handled in TreeTableMouseListener).
		// Placed right after the Archived column for visibility.
		getColumn(DICOMTreeTableModel.ReportCol).setCellRenderer(new ReportCellRenderer());
		getColumn(DICOMTreeTableModel.ReportCol).setPreferredWidth(48);
		getColumn(DICOMTreeTableModel.ReportCol).setMaxWidth(80);
		try {
			getColumnModel().moveColumn(getColumnPosition(DICOMTreeTableModel.ReportCol), 2);
		} catch (Exception ignore) {
			// keep default (last) position if the move is not possible
		}

		getTree().setCellRenderer(new TreeIconCellRenderer());
		setRootVisible(false);
		setShowGrid(true);
		setShowHorizontalLines(true);

		setAutoResizeMode(JTable.AUTO_RESIZE_OFF);// need to show horizontal scroll
		setRowHeight(rowHeight);
		// set column cell's min size
		TableColumnModel columnModel = getColumnModel();
		for (int i = 0; i < columnModel.getColumnCount(); i++) {
			columnModel.getColumn(i).setMinWidth(col_minWidth);
		}
		
		/*
		 * Set Listeners
		 */
		/*set mouse listener*/
		new TreeTableMouseListener(this);
		if(!isQR) {
			/* set drop listener for importing */
			new DropTarget(this, new TreeTableDropListener());
			/*
			 * set drag source and listener through gesture recognizer to tree nodes for
			 * local export
			 */
			dragSource = new DragSource();
			@SuppressWarnings("unused")
			DragGestureRecognizer dgr = dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY,
					new DICOMNodeDragGestureListener());
		}
		
		/* To avoid mouse moving tree node selection */
		setDragEnabled(false);//get dnd.InvalidDnDOperationException: Drag and drop in progress
		
		/* to manage column order */
		getColumnModel().addColumnModelListener(new TableColumnModelListener() {
	        @Override
	        public void columnAdded(TableColumnModelEvent e) {}
	        @Override
	        public void columnRemoved(TableColumnModelEvent e) {}
	        @Override
	        public void columnMoved(TableColumnModelEvent e) {
	            if (e.getFromIndex() != e.getToIndex()) {
	            	String colOrder = "";//do not set null to avoid contain "null" string
	            	for(int i=0;i<getColumnCount();i++) {
	            		String colName = (String)getColumnModel().getColumn(i).getHeaderValue();
	            		if(i == getColumnCount()-1) {
	            			colOrder = colOrder + colName;
	            		}else {
	            			colOrder = colOrder + colName+",";
	            		}
	            	}
	            	PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.ColumnOrder, colOrder);
	            }
	        }
	        @Override
	        public void columnMarginChanged(ChangeEvent e) {}
	        @Override
	        public void columnSelectionChanged(ListSelectionEvent e) {}
	    });
		
		/*
		 * Sort.
		 * Sorter acquire tree table model, so set near end to fail safe set-up.
		 */
		TreeTableNodeSorter sorter = new TreeTableNodeSorter(this);
		getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int col = columnAtPoint(e.getPoint());
				String colname = getColumnName(col);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						sorter.sort(colname);
					}
				});
			}
		});
		
		/*
		 * View settings
		 */
		setFillsViewportHeight(true);
		TableColumnResizer.adjustColumnPreferredWidths(this);
		
		/*
		 * must set same font.
		 */
		//treeTable is JTable, tree is JTree
		setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		getTree().setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
	}


	/**
	 * Overridden to message super and forward the method to the tree. Since the
	 * tree is not actually in the component hieachy it will never receive this
	 * unless we forward it in this manner.
	 */
	
	public boolean getTreeEditable() {
		return treeEditable;
	}

	public void setTreeEditable(boolean editable) {
		treeEditable = editable;
	}

	public boolean getShowsIcons() {
		return showsIcons;
	}

	public void setShowsIcons(boolean show) {
		showsIcons = show;
	}

	public void setRootVisible(boolean visible) {
		getTree().setRootVisible(visible);
	}

	public boolean getShowsRootHandles() {
		return getTree().getShowsRootHandles();
	}

	public void setShowsRootHandles(boolean newValue) {
		getTree().setShowsRootHandles(newValue);
	}

	/*
	 * get all child node level expand state. if following case, will get 0,1,2 rows
	 * were expanded. study - series -- image
	 */
	public ArrayList<Integer> getExpandedRowsPos() {
		ArrayList<Integer> expanded = new ArrayList<Integer>();
		if (getRowCount() > 0) {
			for (int i = 0; i < getRowCount(); i++) {
				if (getTree().isExpanded(i)) {
					expanded.add(i);
				}
			}
		}
		// another example
		// if you need only expanded study tree position row,
//		if (getRowCount() > 0) {
//			for (int i = 0; i < getRowCount(); i++) {
//				if (treeTable.getTree().isExpanded(i)) {
//					if(specifiedLevel == DICOMNode.STUDY) {
//						expanded.add(i);
//					}
//				}
//			}
//		}
		return expanded;
	}
	
	
	/* DO NOT USE avoid column value confuse. */
//	public String getValueAt(int row,int col) {
//		return (String)adapter.getValueAt(row, col);
//	}

	public void selectRow(int[] indexes) {
		if(indexes == null || indexes.length == 0) {
			return;
		}
		for(int i=0;i<indexes.length;) {
			setEditingRow(i);
		}
	}
	
	public int rowForNode(DICOMNode node) {
//		TreeTableModelAdapter ttm = (TreeTableModelAdapter) getModel();
//		for (int i = 0; i < ttm.getRowCount(); i++) {
//			DICOMNode n = (DICOMNode) ttm.nodeForRow(i);
//			if (node == n) {
//				return i;
//			}
//		}
//		return -1;
		return ((TreeTableModelAdapter) getModel()).rowForNode(node);
	}

	public DICOMNode nodeForRow(int row) {
//		int ind = convertRowIndexToModel(row);//DO NOT USE for nodeForRow.
		return (DICOMNode) ((TreeTableModelAdapter) getModel()).nodeForRow(row);
	}
	
	public void removeRow(int row) {
		if(row >= 0) {
			((TreeTableModelAdapter) getModel()).removeRow(row);
		}
	}

	public int getParticularStudyRow(String patID,String StudyUID) {
		TreeTableModelAdapter ttm = (TreeTableModelAdapter) getModel();
		for (int i = 0; i < ttm.getRowCount(); i++) {
			DICOMNode node = (DICOMNode) ttm.nodeForRow(i);
			if (node.getData(DICOMNode.PatientID).equals(patID) && node.getData(DICOMNode.StudyInstanceUID).equals(StudyUID)) {
				return i;
			}
		}
		return -1;
	}
	
	public void setColumnOrder(List<String> order) throws Exception {
		TableColumnModel columnModel = getColumnModel();
		for (int i = 0; i < order.size(); i++) {
			String colname = order.get(i);
			/* get default position */
			int initialInd = -1;
			try {
				initialInd = columnModel.getColumnIndex(colname);
			}catch(java.lang.IllegalArgumentException e) {
				throw new Exception(e.getLocalizedMessage());
			}
			columnModel.moveColumn(initialInd, i);//convertColumnIndexToView(i));
		}
	}
	
	public void setLastColumnOrder() {
		String columnOrder = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.ColumnOrder);
		if(columnOrder == null || columnOrder.equals("")) {
			return;
		}
		List<String> order = readColumnOrderFromProp();
		if(order != null) {
			try {
				setColumnOrder(order);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}else {
			return;
		}
	}
	
	public List<String> readColumnOrderFromProp(){
		String columnOrder = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.ColumnOrder);
		List<String> columns = new ArrayList<>();
		if(columnOrder == null || columnOrder.equals("")) {
			return null;
		}else {
			columns = Arrays.asList(columnOrder.split("\\s*,\\s*"));
			return columns;
		}
	}
	
	/**
	 * 1, tree it was set with TreeTableModel(TreeModel)
	 *  2, table it was set with TreeTableModelAdapter(TableModel) 
	 * @param root
	 */
	public void reload(DICOMNode root) {
		TreeTableModelAdapter modelAda = (TreeTableModelAdapter)getModel();
		modelAda.reload(root);
	}
	
	public Task getTaskTypeImportAt(DICOMNode node) {
		TaskManager tm = TaskManager.getInstance();
		List<Integer> taskKeys = tm.getAllTaskIds();
		for (int tid : taskKeys) {
			Task t = tm.getTask(tid);
			TaskContext con = t.getContext();
			if (con instanceof ImportingStateContext && con.getType() == TaskType.TypeImport) {
				return t;
			}
		}
		return null;
	}
	
	public int getColumnPosition(String colName) {
		TableColumnModel tcm = getColumnModel();
		return tcm.getColumnIndex(colName);
	}

	/* to get current named "Archived" Column position */
	public int getArchivedColumnPosition() {
		return getColumnPosition(DICOMTreeTableModel.ArchivedCol);//"Archived"
	}

//	public ArrayList<DICOMNode> getSelectedNodes() {
//		ArrayList<DICOMNode> nodes = new ArrayList<>();
//		int rows[] = getSelectedRowIndexes();
//		if (rows == null || rows.length < 1) {
//			return null;
//		}
//		for (int row : rows) {
//			nodes.add((DICOMNode) ((TreeTableModelAdapter) getModel()).nodeForRow(row));
//		}
//		return nodes;
//	}
	
	public ArrayList<DICOMNode> getSelectedNodes() {
		ArrayList<DICOMNode> nodes = new ArrayList<>();
		
		// 1. まずは JTable（表全体）の選択行から取得を試みる
		int rows[] = getSelectedRowIndexes();
		if (rows != null && rows.length > 0) {
			for (int row : rows) {
				DICOMNode node = (DICOMNode) ((TreeTableModelAdapter) getModel()).nodeForRow(row);
				if (node != null) {
					nodes.add(node);
				}
			}
		}

		// 2. JTable側の選択が空（フォーカス外れ等で解除）の場合、
		// 内部の JTree（ツリー部分）の選択状態を直接見に行く（強力なフォールバック）
		if (nodes.isEmpty()) {
			TreePath[] paths = getTree().getSelectionPaths();
			if (paths != null) {
				for (TreePath path : paths) {
					Object comp = path.getLastPathComponent();
					if (comp instanceof DICOMNode) {
						nodes.add((DICOMNode) comp);
					}
				}
			}
		}

		// 選択が1つも無ければ null を返す（呼び出し元のエラー判定のため）
		return nodes.isEmpty() ? null : nodes;
	}
	
	public int[] getSelectedRowIndexes() {
		int[] selections = getSelectedRows(); //maybe occur WARNING.but np.
		for (int i = 0; i < selections.length; i++) {
			selections[i] = convertRowIndexToModel(selections[i]);
//			System.out.println(selections[i]);
		}
		return selections;
	}

	/**
	 * Returns the tree that is being shared between the model.
	 */
	public JTree getTree() {
		return tree;
	}

	public JTable getTable() {
		return this;
	}
	
	public DicomCommunicationNode getRemoteDicomCommunicationNode() {
		return remote;
	}

	public DefaultTreeModel getTreeModelInTreeTable() {
		return (DefaultTreeModel) tree.getModel();
		// or return (TreeTableModel)tree.getModel();
	}

	public TreeTableModelAdapter getTableModelInTreeTable() {
		return (TreeTableModelAdapter) getModel();
	}
	
	public int countStudy() {
		if(tree != null) {
			DICOMNode root = (DICOMNode) tree.getModel().getRoot();
			return root.getChildCount();
		}else {
			return 0;
		}
	}

	/**
	 * Overriden to invoke repaint for the particular location if the column
	 * contains the tree. This is done as the tree editor does not fill the bounds
	 * of the cell, we need the renderer to paint the tree in the background, and
	 * then draw the editor over it.
	 */
	public boolean editCellAt(int row, int column, EventObject e) {
		boolean retValue = super.editCellAt(row, column, e);
		if (retValue && getColumnClass(column) == TreeTableModel.class) {
			repaint(getCellRect(row, column, false));
		}
		return retValue;
	}
	
	/**
	 * 
	 * @param nodeList
	 * @return ArrayList<String[]> -> //0:pid,1:studyuid,2:seriesuid,3:sopuid
	 */
	public ArrayList<String[]> createNoDuplicateImageList(ArrayList<DICOMNode> nodeList){
		if(nodeList == null) {
			Log.logger.info("Please select row at TreeTable");
			return null;
		}
		/* uid set */
		ArrayList<String[]> infoSet = new ArrayList<>();//pid,study,series,image
		for(DICOMNode node:nodeList) {
			if(node.isRoot()) {
				continue;
			}
			//Get all instanceUIDs in study
			if(node.getLevelString().equals("STUDY")) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyIUID = node.getData(DICOMNode.StudyInstanceUID);
				ArrayList<String> instanceUIDs = DatabaseHandler.getInstance().getAllInstanceUIDsFromSTUDY(studyIUID);
				for(String sopUID:instanceUIDs) {
					String seriesIUID = DatabaseHandler.getInstance().getSeriesIUID(patID, studyIUID, sopUID);
					String[] info = new String[] {patID,studyIUID,seriesIUID,sopUID};
					infoSet.add(info);
				}
			//Get all instanceUIDs in series
			}else if(node.getLevelString().equals("SERIES")) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyIUID = node.getData(DICOMNode.StudyInstanceUID);
				String seriesIUID = node.getData(DICOMNode.SeriesInstanceUID);
				ArrayList<String> instanceUIDs = DatabaseHandler.getInstance().getInstanceUidList(patID,studyIUID,seriesIUID);
				for(String sopUID:instanceUIDs) {
					String[] info = new String[] {patID,studyIUID,seriesIUID,sopUID};
					infoSet.add(info);
				}
			//Image SOP instanceUID
			}else if(node.getLevelString().equals("IMAGE")) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyIUID = node.getData(DICOMNode.StudyInstanceUID);
				String seriesIUID = node.getData(DICOMNode.SeriesInstanceUID);
				String sopIUID = node.getData(DICOMNode.SOPInstanceUID);
				String[] info = new String[] {patID,studyIUID,seriesIUID,sopIUID};
				infoSet.add(info);
			}
		}
		/* check no duplication */
		ArrayList<String[]> noDupArray = new ArrayList<>();
		for(int i=0;i<infoSet.size();i++) {
			String[] info = infoSet.get(i);
			boolean exists = false;
			for(int j=0; j<noDupArray.size();j++) {
				String[] info_ = noDupArray.get(j);
				if(info[0].equals(info_[0]) && info[1].equals(info_[1]) && info[2].equals(info_[2]) && info[3].equals(info_[3])) {
					exists = true;
					break;
				}
			}
			if(exists) {
				continue;
			}else {
				noDupArray.add(info);
			}
		}
		return noDupArray;
	}

	protected Rectangle getTableRect() {
		JViewport jvp = (JViewport) (SwingUtilities.getAncestorOfClass(JViewport.class, this));
		return (jvp == null ? null : jvp.getViewRect());
	}

	protected void scheduleViewportUpdate() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				JScrollBar scrollBar;
				Point p;
				synchronized (this) {
					p = viewLocation;
				}
				JScrollPane jsp = (JScrollPane) (SwingUtilities.getAncestorOfClass(JScrollPane.class, DICOMTreeTable.this));
				// do not use
//		      JViewport jvp = jsp.getViewport();
//		      jvp.setViewPosition(p);
				scrollBar = jsp.getHorizontalScrollBar();
				scrollBar.setValue(p.x);
				scrollBar = jsp.getVerticalScrollBar();
				scrollBar.setValue(p.y);
			}
		});
	}

	@Override
	public synchronized void autoscroll(Point p) {
		int offset;
		Insets insets = getAutoscrollInsets();
		Rectangle rect = getTableRect();
		JViewport jvp = (JViewport) (SwingUtilities.getAncestorOfClass(JViewport.class, this));
		if (jvp != null) {
			Point oldLocation = jvp.getViewPosition();
			if (p.y < insets.top) {
				offset = getScrollableUnitIncrement(rect, SwingConstants.VERTICAL, -1);
				viewLocation = new Point(oldLocation.x, oldLocation.y - offset);
			}
			if (p.x < insets.left) {
				offset = getScrollableUnitIncrement(rect, SwingConstants.HORIZONTAL, -1);
				viewLocation = new Point(oldLocation.x - offset, oldLocation.y);
			}
			if (p.y > getHeight() - insets.bottom) {
				offset = getScrollableUnitIncrement(rect, SwingConstants.VERTICAL, 1);
				viewLocation = new Point(oldLocation.x, oldLocation.y + offset);
			}
			if (p.x > getWidth() - insets.right) {
				offset = getScrollableUnitIncrement(rect, SwingConstants.HORIZONTAL, 1);
				viewLocation = new Point(oldLocation.x + offset, oldLocation.y);
			}

			if (!(oldLocation.equals(viewLocation))) {
				scheduleViewportUpdate();
			}
		}
	}

	@Override
	public Insets getAutoscrollInsets() {
		Insets insets = new Insets(0, 0, 0, 0);
	    Rectangle rect = getTableRect();
	    if (rect != null) {
	      insets.top = rect.y + 20;
	      insets.left = rect.x + 20;
	      insets.bottom = getHeight() - (rect.y + rect.height) + 20;
	      insets.right = getWidth() - (rect.x + rect.width) + 20;
	    }
	    return insets;
	}

}