/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.util;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.Arrays;

import javax.imageio.ImageIO;

import com.vis.configuration.ConfigInfo;

/**
 *
 * @author tatsunidas
 * @version 0.1
 *
 */
public enum Platform {

	LINUX, WINDOWS, MAC, SOLARIS, NONE;

	public static Platform getOS() {
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.startsWith("mac")) {
			return MAC;
		} else if (osName.startsWith("windows")) {
			return WINDOWS;
		} else if (osName.startsWith("linux")) {
			return LINUX;
		} else if (osName.startsWith("solaris")) {
			return SOLARIS;
		}
		return NONE;
	}
	
	public static boolean isWindows() {
		return getOS() == WINDOWS;
	}
	
	public static boolean isLinux() {
		return getOS() == LINUX;
	}
	
	public static boolean isMac() {
		return getOS() == MAC;
	}
	
	public static boolean isSolaris() {
		return getOS() == SOLARIS;
	}

	/**
	 * graphy hidden folder in home dir.
	 * 
	 * @param applicationName
	 * @return home dir / .applicationame directory.
	 */
	public static File getHomeDirectory(final String applicationName) {
		final String userHome = System.getProperty("user.home", ".");
		final File appDirectory;
		switch (Platform.getOS()) {
		case LINUX:
		case SOLARIS:
			appDirectory = new File(userHome, '.' + applicationName + File.separator);
			break;
		case WINDOWS:
			final String applicationData = System.getenv("APPDATA");
			if (applicationData != null) {
				appDirectory = new File(applicationData, "." + applicationName + File.separator);
			} else {
				appDirectory = new File(userHome, '.' + applicationName + File.separator);
			}
			break;
		case MAC:
			appDirectory = new File(userHome,
					"Library" + File.separator + "Application Support" + File.separator + applicationName);
			break;
		default:
			return new File(".");
		}
		if (!appDirectory.exists()) {
			if (!appDirectory.mkdirs()) {
				throw new RuntimeException(
						"The working directory could not be created: " + appDirectory.getAbsolutePath());
			}
		}
		return appDirectory;
	}

	public static String getOpenCVNativeLibLocation() {
		switch (Platform.getOS()) {
		case LINUX:
			if(is32bitOS()) {
				return "."+File.separator+"native"+File.separator+"linux-x86";
			}else {
				return "."+File.separator+"native"+File.separator+"linux-x86-64";
			}
		case SOLARIS:
			if(is32bitOS()) {
				return "."+File.separator+"native"+File.separator+"solaris-x86";
			}else {
				return "."+File.separator+"native"+File.separator+"solaris-x86-64";
			}
		case WINDOWS:
			if(is32bitOS()) {
				return "."+File.separator+"native"+File.separator+"windows-x86";
			}else {
				return "."+File.separator+"native"+File.separator+"windows-x86-64";
			}
		case MAC:
			return "."+File.separator+"native"+File.separator+"macosx-x86-64";
		default:
			return null;
		}
	}

	public static boolean is32bitOS() {
		return getOsBit() == 32;
	}

	public static final int getOsBit() {
		String os = System.getProperty("sun.arch.data.model");
		if (os != null && (os = os.trim()).length() > 0) {
			if ("32".equals(os)) {
				return 32;
			} else if ("64".equals(os)) {
				return 64;
			}
		}
		os = System.getProperty("os.arch");
		if (os == null || (os = os.trim()).length() <= 0) {
			return -1;
		}
		if (os.endsWith("86")) {
			return 32;
		} else if (os.endsWith("64")) {
			return 64;
		}
		return 32;
	}

	public static String getEndianness() {
		if (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) {
			return "Big-endian";
		} else {
			return "Little-endian";
		}
	}
	
	public static int availableProcessors() {
		return Utils.availableProcessors();
	}

	public static void setSystemProperties() {
		ImageIO.scanForPlugins();
		if (getOS() == MAC) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty("com.apple.mrj.application.apple.menu.about.name", ConfigInfo.AppName.toString());
			System.setProperty("apple.awt.antialiasing", "true");
			System.setProperty("apple.awt.textantialiasing", "true");
		} else if (getOS() == LINUX) {
			System.setProperty("sun.java2d.pmoffscreen", "false");
		}
		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true"); // Need to avoid the exceptions occured when
																			// using jdk 1.7
	}

	/**
	 * Add native lib path programmatically.
	 * 
	 * This method provide adding path alternate following statement,
	 * System.setProperty("java.library.path", "path to native lib") -> this can not
	 * add path.
	 * 
	 * WARNING: An illegal reflective access operation has occurred
	 * 
	 * @param libDir
	 */
	@Deprecated
	public static void setEnv(String libDir) {
		Field usr_paths = null;
		try {
			usr_paths = ClassLoader.class.getDeclaredField("usr_paths");
		} catch (NoSuchFieldException e1) {
			e1.printStackTrace();
			return;
		} catch (SecurityException e1) {
			e1.printStackTrace();
			return;
		}
		usr_paths.setAccessible(true);

		// get current path
		String[] paths = null;
		try {
			paths = (String[]) usr_paths.get(null);
		} catch (IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
			return;
		}

		// if env has path, return
		for (String path : paths) {
			if (path.equals(libDir)) {
				return;
			}
		}

		// add path to env
		String[] newPaths = Arrays.copyOf(paths, paths.length + 1);
		newPaths[newPaths.length - 1] = libDir;
		try {
			usr_paths.set(null, newPaths);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			return;
		} catch (IllegalAccessException e) {
			e.printStackTrace();
			return;
		}
	}
}