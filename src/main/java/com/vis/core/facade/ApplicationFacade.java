package com.vis.core.facade;

import java.io.File;
import java.util.HashMap;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.StartingUpConfigurations;
import com.vis.core.log.LogWindow;
import com.vis.core.plugin.PluginShelf;

/**
 * Manage starting-up process.
 * "just be facade"
 * 
 * show Splash
 * load args and properties
 * init/start DB
 * start MainScreen
 * 
 * @author tatsunidas
 */
public class ApplicationFacade{
	
	private GraphySplashScreen splash;
	
	public static LogWindow logWin;
	
	public PluginShelf pluginShelf;
	
	public ApplicationFacade(HashMap<StartingUpConfigurations, String[]> args) {
		goodMorning(args);
	}
	
	private void goodMorning(HashMap<StartingUpConfigurations, String[]> args) {

		readyToStart(args.get(StartingUpConfigurations.no_splash) != null);
		//add more process to starting up.
	}
	
	private void readyToStart(boolean no_splash) {
		
		logWin = new LogWindow();
		
		/*
		 * check exist subfolders before loading plugins
		 */
		for(String name : new String[] {ConfigInfo.ConfDirName.toString(), ConfigInfo.LogDirName.toString(), ConfigInfo.PluginDirName.toString(), ConfigInfo.TemporalDirName.toString()}) {
			if(!new File("./"+name).exists()) {
				new File("./"+name).mkdirs();
			}
		}
		
		if(!no_splash) {
			splash = new GraphySplashScreen();
		}
		
		initPlugInShelf();
		
		if(splash != null) {
			if(pluginShelf.getLoadedPluginNames() != null) {
				int numOfPlugin = pluginShelf.getLoadedPluginNames().size();
				splash.startProgressAndClose("Loading Plugins", numOfPlugin);
			}else {
				splash.startProgressAndClose("Loading Plugins", 0);
			}
		}
		
		//internationalization
		//locale
//		loadDBLocationFromProperties();
//		mediator.loadQRRefreshOn();
	}
	
	private void initPlugInShelf() {
		pluginShelf = new PluginShelf();
		pluginShelf.loadPlugins();
	}

	private void runMainScreen() {
		
		if(splash != null) {
			splash.dispose();
		}
		
//		
////		setSystemProperties();
//		//open db
//		try {
//			mediator.openAndConnectDB();//start embedded local server
//			mediator.startDCMQRSCP();//start DCMQRSCP which can communicate localDB
//		} catch (Exception e) {
//			//SystemExit
//			try {
//				exitApp("Can not starting-up GRAPHY...");
//			} catch (Throwable e1) {
//				e1.printStackTrace();
//			}
//		}
//		//set locale i18n
//		mediator.setLocale();
//		//create UI
//		mainScreen = MainScreen.getInstance();
////		splash.setVisible(false);
//		mainScreen.setVisible(true);
//		mediator.setMainScreen(mainScreen);
//		mediator.getMainScreen().loadLocalStudiesBySearchKey();
//		mediator.setCurrentTreeTable(mainScreen.getTreeTable());
//		//setTheme()
//		mediator.loadUISettingsFromProperties();//run after visible main screen
//		mediator.initLookAndFeel();
//		mediator.getDatabaseRef().deleteMissingLinkedFiles();
//		
//		//init viewer2dframe no-visible state.
//		viewer2DWin = Viewer2DFrame.getInstance();
//		mediator.setViewer2D(viewer2DWin);
//		viewer2DWin.setDatabase(mediator.getDatabaseRef());
	}

//	private void setSystemProperties() {
//		String currentDir = System.getProperty("user.dir");//apps working dir
//		//TODO, OS by OS
//		System.setProperty("java.library.path", System.getProperty("user.dir") + File.separator + "lib");
//		if(new File(currentDir+"/lib/dcm4che-5.15.1/lib/linux-x86_64").exists()) {
//			setEnv(currentDir+"/lib/dcm4che-5.15.1/lib/linux-x86_64");
////			System.setProperty("java.library.path", System.getProperty("user.dir") + File.separator + "lib/dcm4che-5.15.1/lib/linux-x86_64");//can not
//		}
//		Field fieldSysPath;
//		try {
//			fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
//			fieldSysPath.setAccessible(true);
//			fieldSysPath.set(null, null);
//		} catch (Exception ex) {
//			ApplicationContext.logger.log(Level.SEVERE, "Unable to set Library Path.", ex);
//		}
//
//		if (SystemUtils.IS_OS_MAC) {
//			System.setProperty("apple.laf.useScreenMenuBar", "true");
//			System.setProperty("com.apple.mrj.application.apple.menu.about.name", ApplicationContext.applicationName);
//			System.setProperty("apple.awt.antialiasing", "true");
//			System.setProperty("apple.awt.textantialiasing", "true");
//		}
//		if (SystemUtils.IS_OS_LINUX) {
//			System.setProperty("sun.java2d.pmoffscreen", "false");
//		}
//		ImageIO.scanForPlugins();
//		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true"); // Need to avoid the exceptions occured when
//																			// using jdk 1.7
//	}

//	public static void exitApp(String exitString) throws Throwable {
////		if (splash != null) {
////			splash.setVisible(false);
////		}
//		ApplicationContext.logger.severe(exitString);
//		JOptionPane.showMessageDialog(null, exitString, "ERROR", JOptionPane.ERROR_MESSAGE);
//		ApplicationContext.databaseRef.shutdownDB();
//		System.exit(0);
//	}

//	private static void loadStudiesBasedOnInputParameter(InputArgumentValues inputArgumentValues) {
//		DirectLaunch directLauncher = new DirectLaunch(inputArgumentValues);
//		directLauncher.execute();
//	}
}