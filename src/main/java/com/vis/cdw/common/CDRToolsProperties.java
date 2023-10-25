package com.vis.cdw.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class CDRToolsProperties {

	public static String[] loadDeviceCandidates() {
		java.util.Properties prop = new java.util.Properties();
		FileInputStream fis = null;
		ArrayList<String> candi = new ArrayList<String>();
		try {
			fis = new FileInputStream(new java.io.File("cdrtools/cdrecord.properties"));
			prop.load(fis);
			for (Object key : prop.keySet()) {
				if (((String) key).contains("DEVICE_CANDIDATES")) {
					candi.add(prop.getProperty((String) key));
				}
			}
			return candi.toArray(new String[candi.size()]);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				fis.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			prop = null;
		}
		return null;
	}

	public static Integer loadBurnSpeed() {
		java.util.Properties prop = new java.util.Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(new java.io.File("cdrtools/cdrecord.properties"));
			prop.load(fis);
			for (Object key : prop.keySet()) {
				if (((String) key).contains("SPEED")) {
					String res = prop.getProperty((String) key);
					if (res == null || res.equals("")) {
						return null;
					}
					return Integer.parseInt(res);
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				fis.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			prop = null;
		}
		return null;
	}

	public static Boolean loadEjectAfterBurn() {
		java.util.Properties prop = new java.util.Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(new java.io.File("cdrtools/cdrecord.properties"));
			prop.load(fis);
			for (Object key : prop.keySet()) {
				if (((String) key).contains("EJECT")) {
					String res = prop.getProperty((String) key);
					if (res == null || res.equals("")) {
						return null;
					}
					int eject_label = Integer.parseInt(prop.getProperty((String) key));
					if (eject_label == 0) {
						return false;
					} else {
						return true;
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				fis.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			prop = null;
		}
		return null;
	}
	
	public static Boolean loadWithViewer() {
		java.util.Properties prop = new java.util.Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(new java.io.File("cdrtools/cdrecord.properties"));
			prop.load(fis);
			for (Object key : prop.keySet()) {
				if (((String) key).contains("WITH_VIEWER")) {
					String res = prop.getProperty((String) key);
					if (res == null || res.equals("")) {
						return null;
					}
					int eject_label = Integer.parseInt(prop.getProperty((String) key));
					if (eject_label == 0) {
						return false;
					} else {
						return true;
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				fis.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			prop = null;
		}
		return null;
	}
	
	public static void setPropertiesAndSave(String key, String val) {
		java.util.Properties prop = new java.util.Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(new java.io.File("cdrtools/cdrecord.properties"));
			prop.load(fis);
			prop.put(key, val);
			saveProperties(prop);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				fis.close();
				prop = null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static boolean saveProperties(java.util.Properties prop) {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(new File("cdrtools/cdrecord.properties"));
			prop.store(out, "overwrite Properties");
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				out.close();
				prop = null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
	

}