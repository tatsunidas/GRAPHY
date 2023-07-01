package com.vis.configuration;

public enum ConfigInfo {
	// subfolders
	ConfDirName("conf"),
	LogDirName("log"),
	TemporalDirName("temp"),
	PluginDirName("plugins"),
	
	// log files
	LogFileName("graphy.log"),
	LogFilePath("./" + LogDirName.toString() + "/" + LogFileName.toString()),
	
	// db
	//DefaultDBLocation("") // changeable location. basically, application located dir.
	
	RecordFactory("RecordFactory.xml"),
	
	GRAPHY_Props("/conf/graphy.properties"),
	SERVER_AE_Props("/conf/ae.properties"),
	SERVER_QRSOPCLASSES_Props("/conf/query-sop-classes.properties"),
	SERVER_RecordFactory_Props("conf/RecordFactory.xml"),
	SERVER_RetrieveSOPCLASSES_Props("/conf/retrieve-sop-classes.properties"),
	GRAPHY_StorageSOPCLASSES_Props("/conf/storage-sop-classes.properties"),
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
		}else if(name == RecordFactory) {
			return "./conf/" + name.toString();
		}
		return "./" + name.toString()+"/";
	}
	
}
