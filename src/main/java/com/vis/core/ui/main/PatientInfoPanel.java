package com.vis.core.ui.main;

import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashMap;


@SuppressWarnings("serial")
public class PatientInfoPanel extends JToolBar{
	
	private JTable table;

	public PatientInfoPanel(){
		
		String[] columnNames = {"Attributes", "Value"};
		String[][] tabledata = {
		  {"PatientName", ""},
		  {"PatientID", ""},
		  {"DateOfBirth", ""},
		  {"Sex", ""},
		  {"Age", ""},
		  {"Modality", ""},
		  {"StudyDate", ""}
		  };

		DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
		    @Override
		    public boolean isCellEditable(int row, int column) {
		       //all cells false
		       return false;
		    }
		};
		
		table = new JTable(tableModel);
		for (int i = 0; i < table.getColumnCount(); i++) {
			TableColumn col = table.getColumnModel().getColumn(i);
			col.setCellRenderer(new HorizontalAlignmentTableRenderer());
		}
		table.setRowHeight(24);
		table.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		for(int i = 0 ; i < tabledata.length ; i++){
		    tableModel.addRow(tabledata[i]);
		}
		table.setPreferredSize(new Dimension(300,300));
//		table.setEnabled(false);//could not copy from UI
		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		add(scrollPane);
	}
	
	public void setInfoset(HashMap<String,String> infoset) {
		table.getModel().setValueAt(infoset.get("PatientName"), 0, 1);
		table.getModel().setValueAt(infoset.get("PatientID"), 1, 1);
		table.getModel().setValueAt(infoset.get("PatientBirthDate"), 2, 1);//see, DatabaseHandler getInfoSet
		table.getModel().setValueAt(infoset.get("PatientSex"), 3, 1);
		table.getModel().setValueAt(infoset.get("PatientAge"), 4, 1);
		table.getModel().setValueAt(infoset.get("Modality"), 5, 1);
		table.getModel().setValueAt(infoset.get("StudyDate"), 6, 1);
		table.repaint();
	}
	
	public void clear() {
		table.getModel().setValueAt("", 0, 1);
		table.getModel().setValueAt("", 1, 1);
		table.getModel().setValueAt("", 2, 1);//see, DatabaseHandler getInfoSet
		table.getModel().setValueAt("", 3, 1);
		table.getModel().setValueAt("", 4, 1);
		table.getModel().setValueAt("", 5, 1);
		table.getModel().setValueAt("", 6, 1);
		table.repaint();
	}
	
	class HorizontalAlignmentTableRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (c instanceof JLabel) {
				JLabel l = (JLabel) c;
				l.setHorizontalAlignment(SwingConstants.CENTER);
			}
			return c;
		}
	}
}
