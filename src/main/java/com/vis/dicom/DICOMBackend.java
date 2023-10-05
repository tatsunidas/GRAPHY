package com.vis.dicom;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.StringUtils;

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
		String backEnd = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props,GraphyProp.DICOMBackEnd);
		if(backEnd == null) {
			return DICOMBackend.DCM4CHE;
		}
		backEnd = StringUtils.trimWhitespace(backEnd);
		for(DICOMBackend b:DICOMBackend.values()) {
			if(b.name().toLowerCase().equals(backEnd.toLowerCase())) {
				return b;
			}
		}
		return UNKNOWN;
	}
}
