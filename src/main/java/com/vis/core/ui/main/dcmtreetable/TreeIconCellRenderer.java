package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.util.PropertiesUtil;

public class DICOMTreeIconCellRenderer extends DefaultTreeCellRenderer {

	private static final long serialVersionUID = 5759419998482053684L;
	private JLabel iconLabel;
	private boolean showsIcons = true;
	
//	private String fontSize;
	private String textFont;

	DICOMTreeIconCellRenderer() {
		iconLabel = new JLabel();
//		fontSize = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.FontSize);
		textFont = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.TextFont);
	}

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

	private Image getScaledImage(Image srcImg, int w, int h) {
		BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = resizedImg.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2.drawImage(srcImg, 0, 0, w, h, null);
		g2.dispose();
		return resizedImg;
	}

	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
			boolean leaf, int row, boolean hasFocus) {

		DICOMNode node = (DICOMNode) value;
		
		/* Set Font */
		
		if(textFont != null) {
//			iconLabel.setFont(textFont);// TODO
		}
		iconLabel.setText(node.getData(DICOMNode.PatientID));
		int iconSize = 12;//ApplicationContext.textFont.getSize();// TODO
		
		if (node.getLevel() == DICOMNode.IMAGE) {
			try {
				iconLabel.setIcon(new ImageIcon(getScaledImage(
						ImageIO.read(getClass().getResourceAsStream("/icon/dcm_32x32x32.png")), iconSize+3, iconSize+3)));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			iconLabel.setIcon(UIManager.getIcon("FileView.directoryIcon"));
		}
		
//		else if (node.getLevel() == DICOMNode.PATIENT) {
//			iconLabel.setIcon(UIManager.getIcon("FileView.directoryIcon"));
//		} else if (node.getLevel() == DICOMNode.STUDY) {
//			iconLabel.setIcon(UIManager.getIcon("FileView.directoryIcon"));
//		} else if (node.getLevel() == DICOMNode.SERIES) {
//			iconLabel.setIcon(UIManager.getIcon("FileView.directoryIcon"));
//			System.out.println("series level");
//		} else if (node.getLevel() == DICOMNode.IMAGE) {
//			try {
//				iconLabel.setIcon(new ImageIcon(ImageIO.read(getClass().getResourceAsStream("/icon/dicom.png"))));
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
		return iconLabel;
	}
}
