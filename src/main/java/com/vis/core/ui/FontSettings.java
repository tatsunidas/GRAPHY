package com.vis.core.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.Enumeration;

import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.facade.WindowManager;
import com.vis.core.util.PropertiesUtil;

public class FontSettings {
	
	final static String defaultTextFont = Font.SANS_SERIF;
	final static int defaultFontSize = 12;
	
	public static void setFont2Component(Component comp,String fontName, int fontStyle, int fontSize){
//		final Font currentFont = comp.getFont();//do something ?
		final Font nextFont = new Font(fontName, fontStyle, fontSize);
		comp.setFont(nextFont);
		if(comp instanceof JTree) {
			JTree tree = (JTree)comp;
			FontMetrics fontmetrics = tree.getFontMetrics(nextFont);
			int height = fontmetrics.getHeight();
			tree.setRowHeight(height);
			if (tree.getRowHeight() < 1) {
				// Metal looks better like this.
				tree.setRowHeight(20);
			}
		}else if(comp instanceof JTable) {
			JTable table = (JTable)comp;
			FontMetrics fontmetrics = table.getFontMetrics(nextFont);
			int height = fontmetrics.getHeight();
			table.setRowHeight(height);
			if (table.getRowHeight() < 1) {
				// Metal looks better like this.
				table.setRowHeight(20);
			}
		}
		comp.revalidate();
		comp.repaint();
	}
	
	public static String getCurrentTextFont() {
		String textFont = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.TextFont);
		if(textFont == null || textFont.isBlank() || textFont.isEmpty()) {
			textFont = defaultTextFont;
		}
		return textFont;
	}
	
	public static int getCurrentTextSize() {
		String fontSize = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.FontSize);
		if(fontSize == null || fontSize.isBlank() || fontSize.isEmpty()) {
			return defaultFontSize;
		}else {
			try {
				return Integer.parseInt(fontSize);
			}catch(NumberFormatException e) {
				return defaultFontSize;
			}
		}
	}
	
	public static void saveFont(Font f) {
		saveTextFont(f.getFontName());
		saveFontSize(f.getSize());
	}
	
	public static void saveTextFont(String textFont) {
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.TextFont, textFont);
	}
	
	public static void saveFontSize(int size) {
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.FontSize, size+"");
	}
	
	public static void changeFontAllComps(Font font) {
		/* Recursive UI Setting to next action */
		setUIFont(new FontUIResource(font.getFontName(),font.getStyle(), font.getSize()));// important
		/* Adjust TreeTable Row height */ // IMPORTANT
		WindowManager.getMainScreen().getLocalTreeTable().setRowHeight(font.getSize() + (int)(font.getSize()*0.5));
		SwingUtilities.updateComponentTreeUI(WindowManager.getMainScreen());//important
		saveFont(font);
	}
	
	/* change particular comps Font */
	public static void changeFont(Component component, Font font) {
		component.setFont(font);
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				changeFont(child, font);
			}
		}
		SwingUtilities.updateComponentTreeUI(WindowManager.getMainScreen());//IMPORTANT
		/* Adjust TreeTable Row height */
		WindowManager.getMainScreen().getLocalTreeTable().setRowHeight(font.getSize() + 5);
	}
	
	/*
	 * main screenが起動する前に実行
	 */
	public static void setUIFont(javax.swing.plaf.FontUIResource font) {
		Enumeration<Object> keys = UIManager.getDefaults().keys();
		while (keys.hasMoreElements()) {
			Object key = keys.nextElement();
			if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) {
				UIManager.put(key, font);
			}
		}
	}
}
