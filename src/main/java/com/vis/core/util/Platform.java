package com.vis.core.util;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.nio.file.Paths;
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
    /**
     * @return graphy current working directory
     */
	public static File getGraphyDirectory() {
		//final File appDirectory = new File(".");//DO NOT USE
		final File appDir = new File(Paths.get("").toAbsolutePath().toString());
		// this is also same
		// File appDir = new File(System.getProperty("user.dir"));
		return appDir;
	}

	/**
	 * graphy hidden folder in home dir.
	 * @param applicationName
	 * @return home dir / .applicationame directory.
	 */
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
    
	public static void setSystemProperties() {
		ImageIO.scanForPlugins();
		if (getCurrentPlatform() == MAC) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty("com.apple.mrj.application.apple.menu.about.name", ConfigInfo.AppName.toString());
			System.setProperty("apple.awt.antialiasing", "true");
			System.setProperty("apple.awt.textantialiasing", "true");
		} else if (getCurrentPlatform() == LINUX) {
			System.setProperty("sun.java2d.pmoffscreen", "false");
		}
		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true"); // Need to avoid the exceptions occured when using jdk 1.7
	}
    
	/**
	 * Add native lib path programmatically.
	 * 
	 * This method provide adding path alternate following statement,
	 * System.setProperty("java.library.path", "path to native lib") -> this can not add path.
	 * 
	 * WARNING: An illegal reflective access operation has occurred
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
		String[] paths =null;
		try {
			paths = (String[])usr_paths.get(null);
		} catch (IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
			return;
		}

		// if env has path, return
		for(String path : paths) {
			if(path.equals(libDir)) {
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