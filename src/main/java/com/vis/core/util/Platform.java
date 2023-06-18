package com.vis.core.util;

import java.io.File;
import java.nio.ByteOrder;
import java.nio.file.Paths;

/**
 *
 * @author tatsunidas
 * @version 0.1
 *
 */
public enum Platform {

    LINUX, WINDOWS, MAC, SOLARIS, NONE;

    public static Platform getCurrentPlatform() {
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
    
    /*
     * https://stackoverflow.com/questions/4871051/how-to-get-the-current-working-directory-in-java
     */
    public static File getGraphyDirectory() {
//        final File appDirectory = new File(".");//if win10, return currentDir/./, DO NOT USE 
    	final File appDir = new File(Paths.get("").toAbsolutePath().toString());
        return appDir;
    }

    public static File getHomeDirectory(final String applicationName) {
        final String userHome = System.getProperty("user.home", ".");
        final File appDirectory;
        switch (Platform.getCurrentPlatform()) {
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
                appDirectory = new File(userHome, "Library" + File.separator + "Application Support" + File.separator + applicationName);
                break;
            default:
                return new File(".");
        }
        if (!appDirectory.exists()) {
            if (!appDirectory.mkdirs()) {
                throw new RuntimeException("The working directory could not be created: " + appDirectory.getAbsolutePath());
            }
        }
        return appDirectory;
    }
    
    public static boolean is32bitOS() {
    	return getOsBit() == 32;
    }
    
    public static final int getOsBit() {
//      String os = System.getProperty( "sun.arch.data.mode" ) ; mode ではなく model
      String os = System.getProperty( "sun.arch.data.model" ) ;
      if( os != null && ( os = os.trim() ).length() > 0 ) {
          if( "32".equals( os ) ) {
              return 32 ;
          }
          else if( "64".equals( os ) ) {
              return 64 ;
          }
      }
      os = System.getProperty( "os.arch" ) ;
      if( os == null || ( os = os.trim() ).length() <= 0 ) {
          return -1 ;
      }
//      if( os.endsWith( "32" ) ) { // 32 ではなく 86
      if( os.endsWith( "86" ) ) {
          return 32 ;
      }
      else if( os.endsWith( "64" ) ) {
          return 64 ;
      }
      return 32 ;
  }
    
    public static String getEndianness() {
		if (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) {
			return "Big-endian";
		} else {
			return "Little-endian";
		}
	}
}