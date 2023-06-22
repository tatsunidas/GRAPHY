package com.vis.core.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.vis.configuration.GraphyProp;


/**
 * Manage primary settings by properties files.
 * @author tatsunidas
 *
 */
public class PropertiesUtil {
	
	// manage following properties
	public static final String GRAPHY_Props = "conf/graphy.properties";
	public static final String SERVER_AE_Props = "conf/ae.properties";
	public static final String SERVER_QRSOPCLASSES_Props = "conf/query-sop-classes.properties";
	public static final String SERVER_RecordFactory_Props = "conf/RecordFactory.xml";
	public static final String SERVER_RetrieveSOPCLASSES_Props = "conf/retrieve-sop-classes.properties";
	public static final String GRAPHY_StorageSOPCLASSES_Props = "conf/storage-sop-classes.properties";
	
	public static final String backendKey = "DICOMBackEnd";//key of backend in graphy_prop

	public static Properties loadProperties(String path){
		if(!new File(path).exists()) {
			return null;
		}
		path = new File(path).getAbsolutePath();
		Properties prop = new Properties();
		InputStream in = null;
    	try {
    		in = new FileInputStream(new File(path));
			prop.load(in);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}finally {
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    	return prop; 
	}
	
	public static boolean saveProperties(Properties prop, String path) {
		path = new File(path).getAbsolutePath();
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(new File(path));
			prop.store(out, "overwrite Properties");
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
	
	
	/*
	 * Lifepaths.class.getClass().getResourceAsStream(...) 
	 *  > loads resources using system class loader, it obviously fails because it does not see your JARs
	 * Lifepaths.class.getResourceAsStream(...) 
	 *  > loads resources using the same class loader that loaded Lifepaths class and it should have access to resources in your JARs
	 */
	public static Properties loadPropertiesInResource(String pathinresource){
		Properties prop = new Properties();
    	//InputStream in = PropertiesUtil.class.getResourceAsStream(pathinresource);//to get from resources in jar.
		InputStream in = PropertiesUtil.class.getResourceAsStream(pathinresource);
		try {
			prop.load(in);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}finally {
			try {
				in.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	return prop; 
	}

	
	public static void setPropertyAt(String proppath, String key, String value) {
		proppath = new File(proppath).getAbsolutePath();
		Properties prop = loadProperties(proppath);
		prop.setProperty(key, value);
		saveProperties(prop, proppath);
	}
	
	public static String getPropValueFrom(String path, String key) {
		path = new File(path).getAbsolutePath();
		Properties prop = loadProperties(path);
		return prop != null ? (String) loadProperties(path).get(key):null;
	}
	
	/**
	 * 
	 * @param language : get by locale.getLanguage().
	 */
	public static void writeLocale(String language) {
		setPropertyAt(GRAPHY_Props, GraphyProp.Locale.name(), language);
	}
	
}
