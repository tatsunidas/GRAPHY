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
package com.vis.cdw.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.vis.configuration.ConfigInfo;

public class CDRToolsProperties {

	public static String[] loadDeviceCandidates() {
		java.util.Properties prop = new java.util.Properties();
		FileInputStream fis = null;
		ArrayList<String> candi = new ArrayList<String>();
		try {
			fis = new FileInputStream(new java.io.File(ConfigInfo.getPath(ConfigInfo.CDRTOOL_Props)));
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