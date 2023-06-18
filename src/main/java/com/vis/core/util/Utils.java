package com.vis.core.util;

import java.io.File;
import java.nio.file.Paths;

import com.vis.configuration.ConfigInfo;

public class Utils {
	
	public static boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
	
    /*
     * https://stackoverflow.com/questions/4871051/how-to-get-the-current-working-directory-in-java
     */
    public static File getGraphyDir() {
    	// final File appDirectory = new File(".");//if win10, return currentDir/./, DO NOT USE 
    	final File appDir = new File(Paths.get("").toAbsolutePath().toString());
       return appDir;
    }
    
    public static String getConfSubDirPath(ConfigInfo dirNameType) {
    	return ConfigInfo.getPath(dirNameType);
    }
			
}
