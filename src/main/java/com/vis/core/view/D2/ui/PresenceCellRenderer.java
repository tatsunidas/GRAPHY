package com.vis.core.view.D2.ui;

import java.awt.Component;
import java.awt.Image;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import com.vis.configuration.Resources;

public class PresenceCellRenderer  extends JLabel implements TableCellRenderer{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ImageIcon openEye;
	public static int DEFAULT = 0;
	public static int SHARIN = 1;
	public static int HORUS = 2;
	
	public PresenceCellRenderer(int iconType) {
		if(iconType == SHARIN) {
			openEye = Resources.PresenceCellSharinIcon.loadIconFromResource();
			openEye = new ImageIcon(openEye.getImage().getScaledInstance(16, 16, Image.SCALE_FAST));
		}else if(iconType == HORUS) {
			openEye = Resources.PresenceCellHorusIcon.loadIconFromResource();
			openEye = new ImageIcon(openEye.getImage().getScaledInstance(20, 16, Image.SCALE_SMOOTH));
		}else {
			openEye = Resources.PresenceCellStandardIcon.loadIconFromResource();
			openEye = new ImageIcon(openEye.getImage().getScaledInstance(16, 16, Image.SCALE_FAST));
		}
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		setHorizontalAlignment(JLabel.CENTER);
		if(isSelected) {
			setForeground(table.getSelectionForeground());
			setBackground(table.getSelectionBackground());
		}else {
			setForeground(table.getForeground());
			setBackground(table.getBackground());
		}
		
		if(value instanceof Boolean) {
			boolean onEye = (Boolean) value;
			setOpaque(true);// important
			setIcon(null);// reset
			if (onEye) {
				setIcon(openEye);
			}
		}
		return this;
	}
}
