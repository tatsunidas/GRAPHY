package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import com.vis.configuration.Resources;
import com.vis.core.task.context.ImportingStateContext;
import com.vis.core.ui.main.QRHandler;

public class QRStateCellRenderer extends JButton implements TableCellRenderer{

	private static final long serialVersionUID = 8117398301929629622L;
	int state = -1;
	
	ImageIcon qrReadyIcon;
	ImageIcon localIcon;
	private final QRStateCellEditor cellEditor;
	ArrayList<ImportingStateContext> importingList;
	int progress;
	int total;
	
	public QRStateCellRenderer(QRStateCellEditor cellEditor) {
		super();
		this.cellEditor = cellEditor;
		importingList = cellEditor.getImportingStateContext();
		qrReadyIcon = Resources.QR_Ready_Icon.loadIconFromResource();
		localIcon = Resources.LocalIcon.loadIconFromResource();
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object obj, boolean selected, boolean focused, int row,
			int col) {
		DICOMTreeTable treeTable = (DICOMTreeTable)table;
		ImportingStateContext isc = null;
		if (treeTable.getArchivedColumnPosition() == col) {
			importingList = cellEditor.getImportingStateContext();
			isc = getImportingCellStateAt(importingList,row, col);
		}
		if (isc != null) {
//			return isc.getProgressBar();//20231010
		} else {// waiting state
			if (selected) {
	            setForeground(table.getSelectionForeground());
	            super.setBackground(table.getSelectionBackground());
	        } else {
	            setForeground(table.getForeground());
	            setBackground(table.getBackground());
	        }
			DICOMNode node = treeTable.nodeForRow(row);
			
			if (node == null) {
				return null;
			}
			if (node.getLevel() == DICOMNode.STUDY) {
				if (QRHandler.archivedInLocalAllInstance(node)) {
					setIcon(localIcon);
					setEnabled(false);
					return this;
				} else {
					setEnabled(true);
					setIcon(qrReadyIcon);
					return this;
				}
			} else if (node.getLevel() == DICOMNode.SERIES) {
				if (QRHandler.archivedInAllInstancesRelatedSeries(node)) {
					setIcon(localIcon);
					setEnabled(false);
					return this;
				} else {
					setEnabled(true);
					setIcon(qrReadyIcon);
					return this;
				}
			} else if (node.getLevel() == DICOMNode.IMAGE){
				if (QRHandler.inLocalInstance(node)) {
					setIcon(localIcon);
					setEnabled(false);
					repaint();
					return this;
				} else {
					setEnabled(true);
					setIcon(qrReadyIcon);
					return this;
				}
			}
			return this;
		}
		return this;
	}

	private ImportingStateContext getImportingCellStateAt(ArrayList<ImportingStateContext> importingList,int row, int col) {
		if(importingList == null || importingList.size() == 0) {
			return null;
		}
		//20231010
//		for (int i = 0; i < importingList.size(); i++) {
//			ImportingStateContext isc = importingList.get(i);
//			int r = isc.getImportingRow();
//			int c = isc.getImportingCol();
//			
//			if (r == row && c == col) {
//				return isc;
//			}
//		}
		return null;
	}
}
