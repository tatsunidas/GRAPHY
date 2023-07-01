package com.vis.core.facade;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;

import javax.swing.JOptionPane;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.configuration.StartingUpConfigurations;
import com.vis.core.log.Log;
import com.vis.core.plugin.PluginShelf;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;

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
	
	private static GraphySplashScreen splash;
	public static Locale locale;
	private static PluginShelf pluginShelf;
	public static DatabaseHandler db; 
	
	public ApplicationFacade(HashMap<StartingUpConfigurations, String[]> args) {
		if(Utils.isDebug) {
			Log.logger.info("Running in debug mode.");
		}
		readyToStart(args.get(StartingUpConfigurations.no_splash) != null);
		//add more process to starting up.
	}
	
	/**
	 * Prepare to start ;
	 * - configurations (if not exists, load default)
	 * - load locale
	 * - load plugins
	 * - show main screen
	 * @param no_splash
	 */
	private void readyToStart(boolean no_splash) {
		
		initConfigurationFolders();
		loadLocale();//before show splash
		
		if(!no_splash) {
			splash = new GraphySplashScreen();
		}
		
		initPlugInShelf();
		
		if(splash != null) {
			if(pluginShelf.getLoadedPluginNames() != null) {
				int numOfPlugin = pluginShelf.getLoadedPluginNames().size();
				splash.startProgressAndClose(ResourceBundle.getBundle("i18n.i18n").getString("ApplicationFacade.loadingPlugin"), numOfPlugin);
			}else {
				splash.startProgressAndClose(ResourceBundle.getBundle("i18n.i18n").getString("ApplicationFacade.loadingPlugin"), 0);
			}
		}
	}
	
	private void initConfigurationFolders() {
		for(ConfigInfo name : new ConfigInfo[] {ConfigInfo.ConfDirName, ConfigInfo.LogDirName, ConfigInfo.PluginDirName, ConfigInfo.TemporalDirName}) {
			if(!new File("./"+name.toString()).exists()) {
				switch (name) {
				case LogDirName:
				case PluginDirName:
				case TemporalDirName:
					new File("./"+name.toString()).mkdirs();
					break;
				case ConfDirName:
					new File("./"+name.toString()).mkdirs();
					try {
						Files.copy(getClass().getResourceAsStream("/default/conf/graphy.properties"), Path.of(new File("./"+name.toString()+"/graphy.properties").toURI()));
					} catch (IOException e) {
						Log.logger.severe(e.getMessage());
						Log.logger.severe("Cannot copy default graphy properties file.");
					}
					break;
				default:
					break;
				}
			}else {
				if(name == ConfigInfo.ConfDirName) {
					if(!new File("./"+name.toString()+"/graphy.properties").exists()) {
						try {
							Files.copy(getClass().getResourceAsStream("/default/conf/graphy.properties"), Path.of(new File("./"+name.toString()+"/graphy.properties").toURI()));
						} catch (IOException e) {
							Log.logger.severe(e.getMessage());
							Log.logger.severe("Cannot copy default graphy properties file.");
						}
					}
				}
			}
		}
	}
	
	/**
	 * load locale from graphy.properties.
	 * if null, set default locale.
	 */
	private void loadLocale() {
		String locale_str = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props.toString(), GraphyProp.Locale.name());
		if(locale_str != null && !locale_str.isBlank()) {
			for(Locale l:Locale.getAvailableLocales()) {
				if (l.getLanguage().equals(new Locale(locale_str).getLanguage())) {
					this.locale = l;
					Locale.setDefault(this.locale);
					return;
				}
			}
		}
		this.locale = Locale.getDefault();
		Locale.setDefault(this.locale);
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

	public static void exitApp(Level level, String exitString) throws Throwable {
		if (splash != null) {
			splash.dispose();
		}
		Log.logger.log(level, exitString);
		if(level == Level.INFO) {
			int res = JOptionPane.showConfirmDialog(null, "Close the window ? (application will close)");
			if(res == JOptionPane.OK_OPTION || res == JOptionPane.YES_OPTION) {
				/*
				 * TODO safeClose()
				 */
				//		ApplicationContext.databaseRef.shutdownDB();
				// application will close without any errors.
				System.exit(0);
			}else if(res == JOptionPane.CANCEL_OPTION) {
				//to be continue
				return;
			}
		}else {
			//TODO safeClose()
			System.exit(Level.SEVERE.intValue());
		}
	}
}