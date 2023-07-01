package com.vis.dicom;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.util.PropertiesUtil;

public enum DICOMBackend {
	
	DCM4CHE,
	DCMTK,
	UNKNOWN;
	
	public static final String backendKey = GraphyProp.DICOMBackEnd.name();
	
	private DICOMBackend() {}
	
	public static boolean isBackend(DICOMBackend backend) {
		if(backend == null) {
			return false;
		}
		try {
			java.util.Properties prop = PropertiesUtil.loadProperties(ConfigInfo.GRAPHY_Props.toString());
			if (prop != null) {
				String currentBackend = prop.getProperty(backendKey);
				if (currentBackend.equals(backend.name())) {
					prop = null;
					return true;
				} else {
					prop = null;
					return false;
				}
			} else {
				throw new Exception("Could not read backend from graphy.properties");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public static DICOMBackend getCurrent() {
		try {
			java.util.Properties prop = PropertiesUtil.loadProperties(ConfigInfo.GRAPHY_Props.toString());
			if (prop != null) {
				String currentBackend = prop.getProperty(backendKey);
				if (currentBackend != null && !currentBackend.isBlank()) {
					prop = null;
					for(DICOMBackend b:DICOMBackend.values()) {
						if(b.name().equals(currentBackend)) {
							return b;
						}
					}
				} else {
					prop = null;
					return UNKNOWN;
				}
			} else {
				throw new Exception("Could not read backend from graphy.properties");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return UNKNOWN;
	}

}
