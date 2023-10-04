package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

public class TableColumnResizer {
	/*
	 * should use on SwingWalker process.
	 * e.g.,
	 * SwingUtilities.invokeLater(new Runnable(){
	 * public void run(){
	 * ColumnResizer.adjustColumnPreferredWidths(table);
	 * }
	 * }
	 */
	public static void adjustColumnPreferredWidths(JTable table) {
		//check each column max size(with)
		TableColumnModel columnModel = table.getColumnModel();
		for(int col=0;col<table.getColumnCount();col++) {
			int maxWidth = 0;
			if(col == ((DICOMTreeTable)table).getArchivedColumnPosition()) {
				continue;
			}
			for(int row=0;row<table.getRowCount();row++) {
				TableCellRenderer rend = table.getCellRenderer(row, col);
				Object val = table.getValueAt(row, col);
				Component comp = rend.getTableCellRendererComponent(table, val, false, false, row, col);
				maxWidth = Math.max(comp.getPreferredSize().width, maxWidth);
			}
			TableColumn column = columnModel.getColumn(col);
			column.setPreferredWidth(maxWidth);
			TableCellRenderer headerRend = column.getHeaderRenderer();
			if(headerRend == null) {
				headerRend = table.getTableHeader().getDefaultRenderer();
			}
			Object headerVal = column.getHeaderValue();
			Component comp = headerRend.getTableCellRendererComponent(table, headerVal, false, false, 0, col);
			maxWidth = Math.max(maxWidth,comp.getPreferredSize().width);
		}
		table.revalidate();
	}
}
