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
package com.vis.configuration;

import java.io.File;

/**
 * 
 * @author tatsunidas
 *
 */
public enum ConfigInfo {
	/*
	 * version: see ApplicationFacade.version
	 */
	//name
	AppName("GRAPHY"),
	
	//windows
	MainScreen("GRAPHY Main Screen"),
	D2ViewerWindow("GRAPHY 2D Window"),
	RoiManager("ROI MANAGER"),
	D3ViewerWindow("GRAPHY 3D Window"),
	ResultWindow("Result Window"),
	MPRWindow("GRAPHY MPR Window"),
	
	// subfolders
	ConfDirName("conf"),
	LogDirName("log"),
	TemporalDirName("temp"),
	PluginDirName("plugins"),
	LutDirName("luts"),
	LibDirName("lib"),
	//DBDirName("graphydb"),
	
	//natives
	OpenCVLinux32("./conf/native/native_opencv/linux-x86"),
	OpenCVLinux64("./conf/native/native_opencv/linux-x86-64"),
	OpenCVSolaris32("./conf/native/native_opencv/solaris-x86"),
	OpenCVSolaris64("./conf/native/native_opencv/solaris-x86-64"),
	OpenCVMacOS("./conf/native/native_opencv/macosx-x86-64"),
	OpenCVWindows32("./conf/native/native_opencv/windows-x86"),
	OpenCVWindows64("./conf/native/native_opencv/windows-x86-64"),
	CDRToolsLinux("./conf/native/native_cdrtools/linux"),
	CDRToolsMac("./conf/native/native_cdrtools/mac"),
	CDRToolsWindows("./conf/native/native_cdrtools/windows"),
	
	// log files
	LogFileName("graphy.log"),
	LogFilePath("./" + LogDirName.toString() + "/" + LogFileName.toString()),
	LogFileLimit("1048576"/*1024 * 1024 bytes*/),
	LogFileCount("3"),
	
	// db
	DefaultDBLocation(System.getProperty("user.home")+File.separator+".GRAPHY"),
	
	/*
	 * Files used from a working folder directly under the application should be
	 * relative paths, and those obtained by streaming from a JAR resource should be
	 * paths in the resource.
	 */
	GRAPHY_Props("./conf/graphy.properties"),
	VERSION("application.properties"),
	CDRTOOL_Props("./conf/cdrecord.properties"),
	BURN2CDLocation("./temp/BURN2CD"),
	
	WEASIS("./weasis/weasis-portable/"),
	RecordFactory("RecordFactory.xml"),
	AEProp("ae.properties"),
	
	SERVER_AE_Props("./conf/ae.properties"),
	SERVER_QRSOPCLASSES_Props("./conf/query-sop-classes.properties"),
	SERVER_RecordFactory_Props("./conf/RecordFactory.xml"),
	SERVER_RetrieveSOPCLASSES_Props("./conf/retrieve-sop-classes.properties"),
	GRAPHY_StorageSOPCLASSES_Props("./conf/storage-sop-classes.properties"),
	;
	
	private final String v;
	
	private ConfigInfo(String value) {
		v = value;
	}
	
	@Override
	public String toString() {
		return v;
	}
		
	public static String getPath(ConfigInfo name) {
		if(name == LogFileName || name == LogFilePath) {
			return null;
		}else if(name == RecordFactory || name == AEProp) {
			return "./conf/" + name.toString();
		}
		return "./" + name.toString();
	}
	
}
