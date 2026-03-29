package com.vis.configuration;

import java.awt.Color;
import java.lang.reflect.Field;

public enum GraphyProp {
	Locale,
	Xms,//memory heap
	Xmx,
	GraphyDBDir,
	UseDefaultLocalDBLocation,
	LocalDBLocation,
	NO_SPLASH,
	
	DICOMBackEnd,
	DIMSE_CGET_CMOVE,
	
	RoiFillColor,
	RoiStrokeColor,
	RoiStrokeWidth,
	RoiHandleColor,
	RoiBrushSize,
	RoiBrushType,
	
	MainScreenX,
	MainScreenDeviceID,
	MainScreenHeight,
	MainScreenY,
	Viewer2DScreenDeviceID,
	MainTreeTableKeepTopTitle,
	Viewer2DScreenX,
	Viewer2DScreenHeight,
	Viewer2DScreenY,
	LookAndFeels,
	RefreshQRTreeTableOn,
	IgnoreNullSearchKeyWarning,
	Viewer2DScreenWidth,
	MainScreenWidth,
	FontSize,
	TextFont,
	
	ColumnOrder;
	
	public static Color getColorFromName(String colorName, Color defaultColor) {
		try {
			Field field = Color.class.getField(colorName.toUpperCase());
			return (Color) field.get(null);
		} catch (Exception e) {
			return defaultColor;
		}
	}
	
	/**
	 * To save Color name in prop.
	 */
	public static String findColorNameByColor(Color c) {
		String candidateColorName = null;
		int rgbDistance = -1;
		for(Field f : Color.class.getFields()) {
			Color sys_c = null;
			try {
				sys_c = (Color) f.get(null);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
				return null;
			}
			if(sys_c == null) {
				continue;
			}
			int dif_r = Math.abs(c.getRed() - sys_c.getRed());
			int dif_g = Math.abs(c.getGreen() - sys_c.getGreen());
			int dif_b = Math.abs(c.getBlue() - sys_c.getBlue());
			int sum = dif_r+dif_g+dif_b;
			if(sum == 0) {
				return f.getName().trim().toLowerCase();
			}else {
				if (rgbDistance == -1) {
					rgbDistance = sum;
					candidateColorName = f.getName().trim().toLowerCase();
				}
				if(rgbDistance > sum) {
					rgbDistance = sum;
					candidateColorName = f.getName().trim().toLowerCase();
				}
			}
		}
		return candidateColorName;
	}
}
