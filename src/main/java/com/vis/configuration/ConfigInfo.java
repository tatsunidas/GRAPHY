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
	;
	
	private final String v;
	
	private ConfigInfo(String value) {
		v = value;
	}
	
	@Override
	public String toString() {
		return v;
	}
	
	public static String getPath(ConfigInfo dirName) {
		if(dirName == LogFileName || dirName == LogFilePath) {
			return null;
		}
		return "./" + dirName.toString()+"/";
	}
	
}
