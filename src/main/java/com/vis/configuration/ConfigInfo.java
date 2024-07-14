package com.vis.configuration;

import com.vis.core.util.Utils;

public enum ConfigInfo {
	//version
	Version("1.0.0"),
	//name
	AppName("GRAPHY"),
	
	//windows
	MainScreen("GRAPHY Main Screen"),
	D2ViewerWindow("GRAPHY 2D Window"),
	D3ViewerWindow("GRAPHY 3D Window"),
	MPRWindow("GRAPHY MPR Window"),
	
	// subfolders
	ConfDirName("conf"),
	LogDirName("log"),
	TemporalDirName("temp"),
	PluginDirName("plugins"),
	DBDirName("graphydb"),
	
	// log files
	LogFileName("graphy.log"),
	LogFilePath("./" + LogDirName.toString() + "/" + LogFileName.toString()),
	LogFileLimit("1048576"/*1024 * 1024 bytes*/),
	LogFileCount("3"),
	
	// db
	DefaultDBLocation(Utils.getGraphyDir().getAbsolutePath()),
	
	RecordFactory("RecordFactory.xml"),
	AEProp("ae.properties"),
	
	GRAPHY_Props("./conf/graphy.properties"),
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
		return "./" + name.toString()+"/";
	}
	
}
