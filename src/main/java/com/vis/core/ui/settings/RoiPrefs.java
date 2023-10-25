package com.vis.core.ui.settings;

import javax.swing.JPanel;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.ui.FontSettings;
import com.vis.core.util.PropertiesUtil;

import java.awt.GridBagLayout;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import java.awt.Color;
import java.awt.Font;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class RoiPrefs extends JPanel implements ItemListener{
	
	int currentTextSize = 12;
	Integer defaultStrokeWidth = 1;
	String defaultStrokeColor = "orange";
	String defaultFillColor = "orange";
	String defaultHandleColor = "white";
//	String defaultRoiBrush = "Circle";
	Integer defaultBrushSize = 10;
	
	/*
	 * Roi stroke color
	 * Roi stroke witdh
	 * Roi fill color
	 * Roi brush type circle or rect
	 * Roi brush size in pixels
	 */
	
public RoiPrefs() {
		
		currentTextSize = FontSettings.getCurrentTextSize();
		
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 1.0, 1.0, Double.MIN_VALUE};//左右均等に配置
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		GridBagConstraints gbc = new GridBagConstraints();
//        gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(0, 0, 5, 5);
        
		JLabel lblNewLabel = new JLabel(" Roi settings");
		lblNewLabel.setFont(new Font("MS UI Gothic", Font.BOLD, 14));
		lblNewLabel.setBackground(Color.LIGHT_GRAY);
		
		gbc.gridx = 0;
		gbc.gridy = 0;
		add(lblNewLabel, gbc);
		
		JLabel strokeColorLabel = new JLabel(" Roi stroke color");
		gbc.gridx = 0;
		gbc.gridy = 1;
		add(strokeColorLabel, gbc);
		
		JComboBox<String> strokeColorCombo = new JComboBox<String>(colors());
		strokeColorCombo.setName("RoiStrokeColorCombo");
		String currentColor = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiStrokeColor);
		if(currentColor == null) {
			strokeColorCombo.setSelectedItem(defaultStrokeColor);
		}else {
			strokeColorCombo.setSelectedItem(currentColor);
		}
		gbc.gridx = 1;
		gbc.gridy = 1;
		add(strokeColorCombo, gbc);
		
		JLabel strokeWidthLabel = new JLabel(" Roi stroke width");
		gbc.gridx = 0;
		gbc.gridy = 2;
		add(strokeWidthLabel, gbc);
		
		JComboBox<Integer> strokeWidthCombo = new JComboBox<Integer>(widths());
		strokeWidthCombo.setName("RoiStrokeWidthCombo");
		String currentWidth = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiStrokeWidth);
		if(currentWidth == null) {
			strokeWidthCombo.setSelectedItem(defaultStrokeWidth);
		}else {
			strokeWidthCombo.setSelectedItem(Integer.valueOf(currentWidth.trim()));
		}
		gbc.gridx = 1;
		gbc.gridy = 2;
		add(strokeWidthCombo, gbc);
		
		JLabel fillColorLabel = new JLabel(" Roi fill color");
		gbc.gridx = 0;
		gbc.gridy = 3;
		add(fillColorLabel, gbc);
		
		JComboBox<String> fillColorCombo = new JComboBox<String>(colors());
		fillColorCombo.setName("RoiFillColorCombo");
		String currentFillColor = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiFillColor);
		if(currentFillColor == null) {
			fillColorCombo.setSelectedItem(defaultFillColor);
		}else {
			fillColorCombo.setSelectedItem(currentFillColor.trim());
		}
		gbc.gridx = 1;
		gbc.gridy = 3;
		add(fillColorCombo, gbc);
		
		JLabel handleColorLabel = new JLabel(" Roi handle color");
		gbc.gridx = 0;
		gbc.gridy = 4;
		add(handleColorLabel, gbc);
		
		JComboBox<String> handleColorCombo = new JComboBox<String>(colors());
		handleColorCombo.setName("RoiHandleColorCombo");
		String currentHandleColor = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiHandleColor);
		if(currentHandleColor == null) {
			handleColorCombo.setSelectedItem(defaultHandleColor);
		}else {
			handleColorCombo.setSelectedItem(currentHandleColor.trim());
		}
		gbc.gridx = 1;
		gbc.gridy = 4;
		add(handleColorCombo, gbc);
		
		JLabel lblNewLabel_1 = new JLabel(" Roi brush");
		lblNewLabel_1.setFont(new Font("MS UI Gothic", Font.BOLD, 14));
		lblNewLabel_1.setBackground(Color.LIGHT_GRAY);
		gbc.gridx = 0;
		gbc.gridy = 5;
		add(lblNewLabel_1, gbc);
		
		JLabel brushTypeLabel = new JLabel(" Roi brush type");
		gbc.gridx = 0;
		gbc.gridy = 6;
		add(brushTypeLabel, gbc);
		
		JRadioButton circleRadioButton = new JRadioButton("Circle");
		circleRadioButton.setName("Circle");
		gbc.gridx = 1;
		gbc.gridy = 6;
		add(circleRadioButton, gbc);
		
		JRadioButton squareRadioButton = new JRadioButton("Square");
		squareRadioButton.setName("Square");
		gbc.gridx = 2;
		gbc.gridy = 6;
		add(squareRadioButton, gbc);
		
		ButtonGroup bgroup = new ButtonGroup();
		bgroup.add(circleRadioButton);
		bgroup.add(squareRadioButton);
		// set current
		String currentType = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushType);
		if(currentType == null) {
			circleRadioButton.setSelected(true);
		}else {
			if(currentType.trim().equals("Circle")) {
				circleRadioButton.setSelected(true);
			}else if(currentType.trim().equals("Square")){
				squareRadioButton.setSelected(true);
			}
		}
				
		JLabel brushSizeLabel = new JLabel(" Roi brush size");
		brushSizeLabel.setToolTipText("(In pixels)");
		gbc.gridx = 0;
		gbc.gridy = 7;
		/** IMPORTANT : last row component set anchor to GridBagConstraints.NORTHWEST**/
		gbc.anchor = GridBagConstraints.NORTHWEST;
		add(brushSizeLabel, gbc);
		
		JComboBox<Integer> brushSizeCombo = new JComboBox<Integer>(brushSizes());
		brushSizeCombo.setName("RoiBrushSizeCombo");
		String currentSize = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushSize);
		if(currentSize == null) {
			brushSizeCombo.setSelectedItem(defaultBrushSize);
		}else {
			brushSizeCombo.setSelectedItem(Integer.valueOf(currentSize.trim()));
		}
		gbc.gridx = 1;
		gbc.gridy = 7;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		add(brushSizeCombo, gbc);
		
		//add listener
		strokeColorCombo.addItemListener(this);
		strokeWidthCombo.addItemListener(this);
		fillColorCombo.addItemListener(this);
		brushSizeCombo.addItemListener(this);
		circleRadioButton.addItemListener(this);
		squareRadioButton.addItemListener(this);
		handleColorCombo.addItemListener(this);
	}
	
	private String[] colors() {
		String[] c = new String[] {
				"yellow",
				"orange",
				"blue",
				"green",
				"cyan",
				"magenta",
				"pink",
				"red",
				"white",
				"black"
		};
		return c;
	}
	
	private Integer[] widths() {
		Integer[] w = new Integer[] {
				1,3,5,7,10,12,15
		};
		return w;
	}
	
	private Integer[] brushSizes() {
		Integer[] s = new Integer[] {
				3,5,7,10,12,15,17,20,25,30,35,40,50,70
		};
		return s;
	}
	
	private Color colorFromString(String colorName) {
		if(colorName == null) {
			return Color.orange;
		}
		Color color = null;;
		try {
			java.lang.reflect.Field field = Class.forName("java.awt.Color").getField(colorName.trim());
		    color = (Color)field.get(null);
		} catch (Exception e) {
		    color = null; // Not defined
		}
		if(color == null) {
			return Color.orange;
		}else {
			return color;
		}
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		Object obj = e.getSource();
		if(obj instanceof JComboBox) {
			@SuppressWarnings("rawtypes")
			JComboBox c = (JComboBox)obj;
			if(c.getName().equals("RoiStrokeColorCombo")) {
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RoiStrokeColor, (String)c.getSelectedItem());
			}else if(c.getName().equals("RoiStrokeWidthCombo")) {
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RoiStrokeWidth, String.valueOf((Integer)c.getSelectedItem()));
			}else if(c.getName().equals("RoiFillColorCombo")) {
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RoiFillColor, (String)c.getSelectedItem());
			}else if(c.getName().equals("RoiBrushSizeCombo")) {
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushSize, String.valueOf((Integer)c.getSelectedItem()));
			}else if(c.getName().equals("RoiHandleColorCombo")) {
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RoiHandleColor, (String)c.getSelectedItem());
			}
		}else if(obj instanceof JRadioButton) {
			JRadioButton btn = (JRadioButton)obj;
			String name = btn.getName();
			if(name.equals("Circle")) {
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushType, "Circle");
			}else {
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RoiBrushType, "Square");
			}
		}
	}
}
