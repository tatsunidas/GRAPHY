package com.vis.core.view.D2.ui;

import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

@SuppressWarnings("serial")
public class CustomTableCellRenderer extends DefaultTableCellRenderer {

    private Map<Integer, Color> mapColors;
    String search;
    
    public CustomTableCellRenderer(String search) {
        mapColors = new HashMap<Integer, Color>();
        this.search = search;
    }

    public void setRowColor(int row, Color color) {
    	mapColors.put(row, color);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object obj, boolean isSelected, boolean hasFocus, int row, int column) {

        super.getTableCellRendererComponent(table, obj, isSelected, hasFocus, row, column);
        if(isSelected) {
        	return this;
        }
        
        if(this.search.equals((String)obj)) {
			Color color = mapColors.get(row);
			this.setBackground(Color.cyan);
        }else {
			this.setBackground(table.getBackground());
		}
        return this;
    }
}
