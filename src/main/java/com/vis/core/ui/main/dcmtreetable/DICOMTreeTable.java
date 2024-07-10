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
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;


import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;

/**
 * This example shows how to create a simple JTreeTable component, by using a
 * JTree as a renderer (and editor) for the cells in a particular column in the
 * JTable.
 * 
 * https://www.comp.nus.edu.sg/~cs3283/ftp/Java/swingConnect/tech_topics/tables_trees_2/tables_trees_2.html
 *
 * @author Philip Milne
 * @author Scott Violet
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class DICOMTreeTable extends JTable implements Autoscroll {
	
	private final int col_minWidth = 90;
	private final int rowHeight = 24;
	
	public boolean isQR = false;
	private DicomCommunicationNode remote;
	private TreeTableModelAdapter adapter; 
	protected TreeTableCellRenderer tree;//sub class of jtree
	protected Point viewLocation;
	
	// drop target
	DragSource dragSource = DragSource.getDefaultDragSource();
	protected DICOMNodeDragSourceListener sourceListener;
	protected ArrayList<DICOMNode> draggedComponent;

	//////////////////////////////
	// Convenience routines //
	/////////////////////////////

	private boolean treeEditable = true;
	private boolean showsIcons = true;

	public DICOMTreeTable(TreeTableModel treeTableModel, boolean isQR, DicomCommunicationNode remote) {
		super();
		this.isQR = isQR;
		
		if(this.isQR) {
			if(remote == null) {
				try {
					throw new Exception();
				} catch (Exception e1) {
					e1.printStackTrace();
					return;
				}
			}
			this.remote = remote;
		}
		
		// Create the tree. It will be used as a renderer and editor.
		tree = new TreeTableCellRenderer(this, treeTableModel);

		// set root node no visible
		tree.setRootVisible(false);

		// Install a tableModel representing the visible rows in the tree.
		adapter = new TreeTableModelAdapter(treeTableModel, tree);
		super.setModel(adapter);
		
		// Force the JTable and JTree to share their row selection models.
		ListToTreeSelectionModelWrapper selectionWrapper = new ListToTreeSelectionModelWrapper(tree);
		tree.setSelectionModel(selectionWrapper);
		
		super.setSelectionModel(selectionWrapper.getListSelectionModel());

		// Install the tree editor renderer and editor.
		setDefaultRenderer(TreeTableModel.class, tree);
		setDefaultEditor(TreeTableModel.class,  new TreeTableCellEditor(tree,this));

		if (isQR) {
			QRStateCellEditor sce = new QRStateCellEditor(this.remote);
			QRStateCellRenderer scr= new QRStateCellRenderer(sce);
			getColumn("Archived").setCellRenderer(scr);
			getColumn("Archived").setCellEditor(sce);
		} else {
			// int mode, int state, Integer row, Integer col,Integer progress,Integer
			// total,JTextField holder
			ArchiveCellRendererableEditor srcre = new ArchiveCellRendererableEditor(null);
			getColumn("Archived").setCellRenderer(srcre);
			getColumn("Archived").setCellEditor(srcre);
		}

		setShowGrid(true);
		setShowHorizontalLines(true);

		// No internal cell spacing
		setIntercellSpacing(new Dimension(0, 0));
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
		
		/*
		 * Sort
		 */
		DICOMTreeTable dcmTreeTable = this;
		getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int col = columnAtPoint(e.getPoint());
				String colname = getColumnName(col);
				if(Utils.isDebug) System.out.println("Column index selected " + col + " " + colname);
				// sortTree
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						new TreeTableNodeSorter().sort(dcmTreeTable, colname);
					}
				});
			}
		});
		
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
		 * View settings
		 */
		setFillsViewportHeight(true);
		TableColumnResizer.adjustColumnPreferredWidths(this);
		
		/*
		 * must set same font.
		 */
		//treeTable is JTable, tree is JTree
		setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		tree.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
	}

	/**
	 * Overridden to message super and forward the method to the tree. Since the
	 * tree is not actually in the component hieachy it will never receive this
	 * unless we forward it in this manner.
	 */
	public void updateUI() {
		super.updateUI();
		if (tree != null) {
			tree.updateUI();
			// Do this so that the editor is referencing the current renderer
			// from the tree. The renderer can potentially change each time
			// laf changes.
			
			int w = WindowManager.getMainScreen().getWidth();
			setSize(w,getHeight());
			
			//do not set tatsu
//			setDefaultEditor(TreeTableModel.class, new TreeTableCellEditor());
		}
		// Use the tree's default foreground and background colors in the
		// table.
		/* using Swing LAF */
		LookAndFeel.installColorsAndFont(this, "Tree.background", "Tree.foreground", "Tree.font");
	}
	
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
		tree.setRootVisible(visible);
	}

	public boolean getShowsRootHandles() {
		return tree.getShowsRootHandles();
	}

	public void setShowsRootHandles(boolean newValue) {
		tree.setShowsRootHandles(newValue);
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
	
	public int getRowCount() {
		if(adapter == null) {//
			return 0;
		}
		return adapter.getRowCount();
	}
	
	/* DO NOT USE avoid column value confuse. */
//	public String getValueAt(int row,int col) {
//		return (String)adapter.getValueAt(row, col);
//	}

	/**
	 * Workaround for BasicTableUI anomaly. Make sure the UI never tries to resize
	 * the editor. The UI currently uses different techniques to paint the renderers
	 * and editors and overriding setBounds() below is not the right thing to do for
	 * an editor. Returning -1 for the editing row in this case, ensures the editor
	 * is never painted.
	 */
	public int getEditingRow() {
		return (getColumnClass(editingColumn) == TreeTableModel.class) ? -1 : editingRow;
	}

	/**
	 * Returns the actual row that is editing as <code>getEditingRow</code> will
	 * always return -1.
	 */
	private int realEditingRow() {
		return editingRow;
	}
	
	public void selectRow(int[] indexes) {
		if(indexes == null || indexes.length == 0) {
			return;
		}
		for(int i=0;i<indexes.length;) {
			setEditingRow(i);
		}
	}

	/**
	 * This is overriden to invoke supers implementation, and then, if the receiver
	 * is editing a Tree column, the editors bounds is reset. The reason we have to
	 * do this is because JTable doesn't think the table is being edited, as
	 * <code>getEditingRow</code> returns -1, and therefore doesn't automaticly
	 * resize the editor for us.
	 */
	public void sizeColumnsToFit(int resizingColumn) {
		super.sizeColumnsToFit(resizingColumn);
		if (getEditingColumn() != -1 && getColumnClass(editingColumn) == TreeTableModel.class) {
			Rectangle cellRect = getCellRect(realEditingRow(), getEditingColumn(), false);
			Component component = getEditorComponent();
			component.setBounds(cellRect);
			component.validate();
		}
	}

	/**
	 * Overridden to pass the new rowHeight to the tree.
	 */
	public void setRowHeight(int rowHeight) {
		super.setRowHeight(rowHeight);
		if (tree != null && tree.getRowHeight() != rowHeight) {
			tree.setRowHeight(getRowHeight());
		}
	}

	public DICOMNode nodeForRow(int row) {
//		int ind = convertRowIndexToModel(row);//DO NOT USE for nodeForRow.
		return (DICOMNode) ((TreeTableModelAdapter) getModel()).nodeForRow(row);
	}

	public ArchiveCellRendererableEditor getStateCellEditorAtArchiveColumn(int col_index) {
		TableColumnModel tcm = getColumnModel();
		return (ArchiveCellRendererableEditor) tcm.getColumn(col_index).getCellEditor();
	}
	
	public QRStateCellEditor getQRStateCellEditorAtArchiveColumn(int col_index) {
		TableColumnModel tcm = getColumnModel();
		return (QRStateCellEditor) tcm.getColumn(col_index).getCellEditor();
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
	
	public void setColumnOrder(List<String> order) {
		TableColumnModel columnModel = getColumnModel();
		for (int i = 0; i < order.size(); i++) {
			String colname = order.get(i);
			/* get default position */
			int initialInd = columnModel.getColumnIndex(colname);
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
			setColumnOrder(order);
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

	/* to get current named "Archived" Column position */
	public int getArchivedColumnPosition() {
		TableColumnModel tcm = getColumnModel();
		return tcm.getColumnIndex(DICOMTreeTableModel.columnNames[1]);//"Archived"
	}

	public ArrayList<DICOMNode> getSelectedNodes() {
		ArrayList<DICOMNode> nodes = new ArrayList<>();
		int rows[] = getSelectedRowIndexes();
		if (rows == null || rows.length < 1) {
			return null;
		}
		for (int row : rows) {
			nodes.add((DICOMNode) ((TreeTableModelAdapter) getModel()).nodeForRow(row));
		}
		return nodes;
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
			Log.logger.info("please select row at DICOMTreeTable");
			return null;
		}
		/* uid set */
		ArrayList<String[]> infoSet = new ArrayList<>();//pid,study,series,image
		for(DICOMNode node:nodeList) {
			if(node.isRoot()) {
				continue;
			}
			//スタディノード内の全インスタンスUIDを取得
			if(node.getLevelString().equals("STUDY")) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyIUID = node.getData(DICOMNode.StudyInstanceUID);
				ArrayList<String> instanceUIDs = DatabaseHandler.getInstance().getAllInstanceUIDsFromSTUDY(studyIUID);
				for(String sopUID:instanceUIDs) {
					String seriesIUID = DatabaseHandler.getInstance().getSeriesIUID(patID, studyIUID, sopUID);
					String[] info = new String[] {patID,studyIUID,seriesIUID,sopUID};
					infoSet.add(info);
				}
			//シリーズノード内の全インスタンスUIDを取得
			}else if(node.getLevelString().equals("SERIES")) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyIUID = node.getData(DICOMNode.StudyInstanceUID);
				String seriesIUID = node.getData(DICOMNode.SeriesInstanceUID);
				ArrayList<String> instanceUIDs = DatabaseHandler.getInstance().getInstanceUidList(patID,studyIUID,seriesIUID);
				for(String sopUID:instanceUIDs) {
					String[] info = new String[] {patID,studyIUID,seriesIUID,sopUID};
					infoSet.add(info);
				}
			//イメージノードのインスタンスUIDを取得
			}else if(node.getLevelString().equals("IMAGE")) {
				String patID = node.getData(DICOMNode.PatientID);
				String studyIUID = node.getData(DICOMNode.StudyInstanceUID);
				String seriesIUID = node.getData(DICOMNode.SeriesInstanceUID);
				String sopIUID = node.getData(DICOMNode.SOPInstanceUID);
				String[] info = new String[] {patID,studyIUID,seriesIUID,sopIUID};
				infoSet.add(info);
			}
		}
		/* 重複のチェック */
		ArrayList<String> dicomuidlist = new ArrayList<>();
		for(int i=0;i<infoSet.size();i++) {
			String[] info = infoSet.get(i);
			String dicomuid = info[0]+info[1]+info[2]+info[3];
			dicomuidlist.add(dicomuid);
		}
		//https://stackoverflow.com/questions/41095090/index-of-duplicates-items-in-java-arraylist
		HashMap<String,List<Integer>> indices = new HashMap<>();
		for (int i = 0; i < dicomuidlist.size(); i++) {
		    indices.computeIfAbsent(dicomuidlist.get(i), c -> new ArrayList<>()).add(i);
		}		
		ArrayList<String[]> noDupArray = new ArrayList<>();
		for(String dicomuid:indices.keySet()) {
			/* 最初の１つめのみを取得する */
			noDupArray.add(infoSet.get(indices.get(dicomuid).get(0)));
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