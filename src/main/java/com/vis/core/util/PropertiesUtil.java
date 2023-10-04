package com.vis.core.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;


/**
 * Manage primary settings by properties files.
 * @author tatsunidas
 *
 */
public class PropertiesUtil {

	public static Properties loadProperties(String path){
		if(!new File(path).exists()) {
			return null;
		}
		Properties prop = new Properties();
		InputStreamReader reader = null;
    	try {
    		reader = new InputStreamReader(new FileInputStream(path), "UTF-8");
			prop.load(reader);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}finally {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    	return prop; 
	}
	
	public static Properties loadProperties(URL url){
		if(url == null) {
			return null;
		}
		
		File f = null;
		try {
			f = new File(url.toURI());
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(!f.exists()) {
			return null;
		}
		
		String path = f.getAbsolutePath();
		Properties prop = new Properties();
		InputStreamReader reader = null;
    	try {
    		reader = new InputStreamReader(new FileInputStream(path), "UTF-8");
			prop.load(reader);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}finally {
			try {
				f = null;
				reader.close();
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
	
	public static void setPropertyAt(ConfigInfo propFile, GraphyProp key, String value) {
		String proppath = new File(propFile.toString()).getAbsolutePath();
		Properties prop = loadProperties(proppath);
		prop.setProperty(key.name(), value);
		saveProperties(prop, proppath);
	}
	
	public static String getPropValueFrom(String path, String key) {
		path = new File(path).getAbsolutePath();
		Properties prop = loadProperties(path);
		return prop != null ? (String) loadProperties(path).get(key):null;
	}
	
	public static String getPropValueFrom(ConfigInfo propFile, GraphyProp key) {
		String p = new File(propFile.toString()).getAbsolutePath();
		Properties prop = loadProperties(p);
		return prop != null ? (String) loadProperties(p).get(key.name()):null;
	}
	
	/**
	 * 
	 * @param language : get by locale.getLanguage().
	 */
	public static void writeLocale(String language) {
		setPropertyAt(ConfigInfo.GRAPHY_Props.toString(), GraphyProp.Locale.name(), language);
	}
	
}
