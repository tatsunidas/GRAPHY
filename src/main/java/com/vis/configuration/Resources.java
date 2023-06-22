package com.vis.configuration;

import java.net.URL;

public enum Resources {
	
	Splash("/icon/splash.png"),
	
	SQL_LISTENER("/sql/LISTENER.sql"), // deprecated, use AE instead.
	SQL_LOCALE("/sql/LOCALE.sql"),
	SQL_MISCELLANEOUS("/sql/MISCELLANEOUS.sql"),// delete ?
	SQL_MODALITY("/sql/MODALITY.sql"),
	SQL_PATIENT("/sql/PATIENT.sql"),
	SQL_STUDY("/sql/STUDY.sql"),
	SQL_SERIES("/sql/SERIES.sql"),
	SQL_IMAGE("/sql/IMAGE.sql"),
	SQL_PRESET("/sql/PRESETS.sql"),
	SQL_ROI("/sql/ROI.sql"),
	SQL_AE("/sql/AE.sql"),//previous name is SERVERS.sql
	SQL_TEXTANNOTATION("/sql/TEXTANNOTATION.sql"),
	SQL_THEME("/sql/THEME.sql"),
	
	; 
	
	private String pathInResource;
	private Resources(String path) {
		this.pathInResource = path;
	}
	
	public String path() {
		return pathInResource;
	}
	
	public URL toURL() {
		return getClass().getResource(pathInResource);
	}
	
}
