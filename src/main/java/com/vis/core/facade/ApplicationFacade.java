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

import org.opencv.osgi.OpenCVNativeLoader;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.StartingUpConfigurations;
import com.vis.core.log.Log;
import com.vis.core.plugin.PluginShelf;
import com.vis.core.ui.LookAndFeels;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.db.DatabaseHandler.DatabaseHandlerBuilder;

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
	private static LookAndFeels laf;
	
	public ApplicationFacade(HashMap<StartingUpConfigurations, String[]> args) {
		readyToStart(args.get(StartingUpConfigurations.no_splash) != null);
		runMainScreen();
	}
	
	/**
	 * Prepare to start ;
	 * - set up configurations (if not exists, load default)
	 * - load locale
	 * - load plugins
	 * - show main screen
	 * @param no_splash
	 */
	private void readyToStart(boolean no_splash) {
		// # 0
		com.vis.core.util.Platform.setSystemProperties();
		// # 1
		initConfigurationFolders();
		// # 2
		loadLocale();//before show splash
		// # 3
		initPlugInShelf();
		// # 4
		if(!no_splash) {
			splash = new GraphySplashScreen();
			if(pluginShelf.getLoadedPluginNames() != null) {
				int numOfPlugin = pluginShelf.getLoadedPluginNames().size();
				splash.startProgressAndClose(ResourceBundle.getBundle("i18n.i18n").getString("ApplicationFacade.loadingPlugin"), numOfPlugin);
			}else {
				splash.startProgressAndClose(ResourceBundle.getBundle("i18n.i18n").getString("ApplicationFacade.loadingPlugin"), 0);
			}
		}		
		// # 5
		initDB();
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
						Files.copy(getClass().getResourceAsStream("/default/conf/cdrecord.properties"), Path.of(new File("./"+name.toString()+"/cdrecord.properties").toURI()));
					} catch (IOException e) {
						Log.logger.severe("Cannot copy default graphy properties file.");
						Log.logger.severe(e.getMessage());
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
							Log.logger.severe("Cannot copy default graphy properties file.");
							Log.logger.severe(e.getMessage());
						}
					}
					if(!new File("./"+name.toString()+"/cdrecord.properties").exists()) {
						try {
							Files.copy(getClass().getResourceAsStream("/default/conf/cdrecord.properties"), Path.of(new File("./"+name.toString()+"/cdrecord.properties").toURI()));
						} catch (IOException e) {
							Log.logger.severe("Cannot copy default graphy properties file.");
							Log.logger.severe(e.getMessage());
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
					ApplicationFacade.locale = l;
					Locale.setDefault(ApplicationFacade.locale);
					return;
				}
			}
		}
		ApplicationFacade.locale = Locale.getDefault();
		Locale.setDefault(ApplicationFacade.locale);
	}
	
	@SuppressWarnings("unused")
	private void loadNativeLibs() {
		// opencv
		/*
		 * opencv natives are loaded automatically from javax.imageio.ImageIO.
		 */
		OpenCVNativeLoader loader = new OpenCVNativeLoader();
		loader.init();
		// add more...
	}
	
	private void initPlugInShelf() {
		pluginShelf = new PluginShelf();
		pluginShelf.loadPlugins();
	}
	
	private void initDB() {
		db = new DatabaseHandlerBuilder().build();
		if(db.startingUp() == false) {
			try {
				exitApp(Level.SEVERE, "Can not start graphy db.");
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		db.deleteMissingLinkedFiles();
	}

	private void runMainScreen() {
		
		if(splash != null) {
			splash.dispose();
		}
		
//		//create UI
		MainScreen mainScreen = MainScreen.getInstance();
		WindowManager.addWindow(mainScreen);
		mainScreen.setVisible(true);
		mainScreen.loadLocalStudiesBySearchKey();
		
		// look and feels
		laf = new LookAndFeels();
		laf.updateLookAndFeels(mainScreen);//run after mainScreen visible true
		
		// TODO 20231003
//		//init viewer2dframe no-visible state.
//		Viewer2DFrame viewer2DWin = Viewer2DFrame.getInstance();
//		WindowManager.addWindow(viewer2DWin);
//		viewer2DWin.setDatabase(db);
		
	}
	
	public static LookAndFeels getCurrentLookAndFeels() {
		return laf;
	}
	
	public static boolean exitApp(Level level, String exitString) throws Throwable {
		if (splash != null) {
			splash.dispose();
		}
		Log.logger.log(level, exitString);
		boolean close = true;
		if(level == Level.INFO) {
			int res = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Close the window ? (application will close)");
			if(res == JOptionPane.OK_OPTION || res == JOptionPane.YES_OPTION) {
				// application will close without any errors.
				if(db != null) {
					db.shutdownDB();
				}
				Utils.eraseTemporalDir();
				WindowManager.getMainScreen().setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
//				System.exit(0);
			}else if(res == JOptionPane.CANCEL_OPTION || res == JOptionPane.NO_OPTION) {
				//to be continue
				close = false;
				return close;
			}
		}else {
			if(db != null) {
				db.shutdownDB();
			}
			Utils.eraseTemporalDir();
			WindowManager.getMainScreen().setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
			System.exit(Level.SEVERE.intValue());
		}
		return close;
	}
}