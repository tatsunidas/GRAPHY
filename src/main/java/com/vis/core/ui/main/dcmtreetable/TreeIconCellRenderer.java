package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import com.vis.configuration.Resources;

/**
 * Render tree icon.
 * @author tatsunidas
 *
 */
public class TreeIconCellRenderer extends DefaultTreeCellRenderer {

	private static final long serialVersionUID = 5759419998482053684L;
	private boolean showsIcons = true;
	Icon defaultIcon = UIManager.getIcon("FileView.directoryIcon");
//	int iconSize = 12;

	TreeIconCellRenderer() {}

	public Icon getClosedIcon() {
		return (showsIcons ? super.getClosedIcon() : null);
	}

	public Icon getDefaultClosedIcon() {
		return (showsIcons ? super.getDefaultClosedIcon() : null);
	}

	public Icon getDefaultLeafIcon() {
		return (showsIcons ? super.getDefaultLeafIcon() : null);
	}

	public Icon getDefaultOpenIcon() {
		return (showsIcons ? super.getDefaultOpenIcon() : null);
	}

	public Icon getLeafIcon() {
		return (showsIcons ? super.getLeafIcon() : null);
	}

	public Icon getOpenIcon() {
		return (showsIcons ? super.getOpenIcon() : null);
	}

	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
			boolean leaf, int row, boolean hasFocus) {

		DICOMNode node = (DICOMNode) value;
		JLabel iconLabel = new JLabel();
		
//		if (node.getLevel() == DICOMNode.PATIENT) {
//			iconLabel.setIcon(UIManager.getIcon("FileView.directoryIcon"));
//		}
		
		ImageIcon im = null;
		
		if (node.getLevel() == DICOMNode.STUDY) {
			if(expanded) {
				im = Resources.TreeStudyLevelOpenIcon.loadIconFromResource();
			}else {
				im = Resources.TreeStudyLevelCloseIcon.loadIconFromResource();
			}
			if(im != null) {
				iconLabel.setIcon(im);
			}else {
				iconLabel.setIcon(defaultIcon);
			}
			String desc = node.getData(DICOMNode.StudyDescription);
			if(desc == null || desc.isBlank() || desc.isEmpty()) {
				desc = "NO-STUDY-DESC";
			}
			iconLabel.setText(desc);
		} else if (node.getLevel() == DICOMNode.SERIES) {
			im = Resources.TreeSeriesLevelIcon.loadIconFromResource();			
			if(im != null) {
				iconLabel.setIcon(im);
			}else {
				iconLabel.setIcon(defaultIcon);
			}
			String desc = node.getData(DICOMNode.SeriesDescription);
			if(desc == null || desc.isBlank() || desc.isEmpty()) {
				desc = "NO-SERIES-DESC";
			}
			iconLabel.setText(desc);
		} else if (node.getLevel() == DICOMNode.IMAGE) {
			im = Resources.TreeImageLevelIcon.loadIconFromResource();			
			if(im != null) {
				iconLabel.setIcon(im);
			}else {
				iconLabel.setIcon(defaultIcon);
			}
		}else {
			iconLabel.setIcon(defaultIcon);
		}
		return iconLabel;
	}
}
