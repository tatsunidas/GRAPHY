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
package com.vis.core.facade;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.opencv.osgi.OpenCVNativeLoader;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.StartingUpConfigurations;
import com.vis.core.log.Log;
import com.vis.core.plugin.PluginClassLoader;
import com.vis.core.plugin.PluginShelf;
import com.vis.core.task.TaskManager;
import com.vis.core.ui.LookAndFeels;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.settings.PreferencesWin;
import com.vis.core.util.Platform;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.db.DatabaseHandler.DatabaseHandlerBuilder;
import com.vis.dicom.DICOMBackend;

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
	public static PluginShelf pluginShelf;
	public static PluginClassLoader classLoader;//load externals and plugins
	private static DatabaseHandler db; 
	private static LookAndFeels laf;
	public static final DICOMBackend backend = DICOMBackend.getCurrent();
	
	public static String version;
	
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
		load_version();
		// # 2
		loadLocale();//before show splash
		// # 3
		/*
		 * load external libs and plugins using current thread.
		 */
		initPlugInShelf();
		// # 4
		if(!no_splash) {
			splash = new GraphySplashScreen();
			PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.NO_SPLASH, "false");
			if(pluginShelf.getLoadedPluginNames() != null) {
				int numOfPlugin = pluginShelf.getLoadedPluginNames().size();
				splash.startProgressAndClose(ResourceBundle.getBundle("i18n.i18n").getString("ApplicationFacade.loadingPlugin"), numOfPlugin);
			}else {
				splash.startProgressAndClose(ResourceBundle.getBundle("i18n.i18n").getString("ApplicationFacade.loadingPlugin"), 0);
			}
		}else {
			PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.NO_SPLASH, "true");
		}
		// # 5
		initDB();
	}
	
	private void initConfigurationFolders() {
		String appDir = Platform.getAppDirectory().getAbsolutePath()+File.separator;
		if(Utils.isDebug) {
			appDir = "";
		}
		for(ConfigInfo name : new ConfigInfo[] {ConfigInfo.ConfDirName, ConfigInfo.LogDirName, ConfigInfo.PluginDirName, ConfigInfo.TemporalDirName, ConfigInfo.LibDirName}) {
			if(!new File(appDir+name.toString()).exists()) {
				switch (name) {
				case LogDirName:
				case PluginDirName:
				case TemporalDirName:
				case LibDirName:
					new File(appDir+name.toString()).mkdirs();
					break;
				case ConfDirName:
					new File(appDir+name.toString()).mkdirs();
					try {
						Files.copy(getClass().getResourceAsStream("/default/conf/graphy.properties"), Path.of(new File(appDir+name.toString()+"/graphy.properties").toURI()));
						Files.copy(getClass().getResourceAsStream("/default/conf/cdrecord.properties"), Path.of(new File(appDir+name.toString()+"/cdrecord.properties").toURI()));
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
					if(!new File(appDir+name.toString()+"/graphy.properties").exists()) {
						try {
							Files.copy(getClass().getResourceAsStream("/default/conf/graphy.properties"), Path.of(new File(appDir+name.toString()+"/graphy.properties").toURI()));
						} catch (IOException e) {
							Log.logger.severe("Cannot copy default graphy properties file.");
							Log.logger.severe(e.getMessage());
						}
					}
					if(!new File(appDir+name.toString()+"/cdrecord.properties").exists()) {
						try {
							Files.copy(getClass().getResourceAsStream("/default/conf/cdrecord.properties"), Path.of(new File(appDir+name.toString()+"/cdrecord.properties").toURI()));
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
		Locale.setDefault(Utils.locale());
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
		/*
		 * load extenals and plugins
		 */
		pluginShelf.loadPlugins();
	}
	
	private void initDB() {
		db = new DatabaseHandlerBuilder().build();
		if(db.startingUp() == false) {
			try {
				readyToClose(Level.SEVERE, "Can not start graphy db.");
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
		// look and feels
		laf = new LookAndFeels();
		
		//create UI
		SwingUtilities.invokeLater(() -> {
			MainScreen mainScreen = MainScreen.getInstance();
			mainScreen.setVisible(true);
			mainScreen.loadLocalStudiesBySearchKey();
			WindowManager.addWindow(mainScreen);
			//add preferenceWin
			WindowManager.addWindow(PreferencesWin.getInstance());
		});
		
		String fontSize = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.FontSize);
		if(fontSize != null && !fontSize.isBlank()) {
			try {
				int s = Integer.parseInt(fontSize);
				Font f = new Font(Font.SANS_SERIF, Font.PLAIN, s);// name, style, size
				WindowManager.updateFont(f);
			}catch(NumberFormatException e) {
				//do nothing
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.FontSize, "12");
			}
		}
		WindowManager.updateLookAndFeels(laf);
	}
	
	public static LookAndFeels getLookAndFeels() {
		return laf;
	}
	
	public static void load_version() {
		Properties properties = new Properties();
		try (InputStream input = ApplicationFacade.class.getClassLoader()
				.getResourceAsStream(ConfigInfo.VERSION.toString())) {
			if (input == null) {
				System.out.println("Sorry, unable to find application.properties");
				return;
			}
			properties.load(input);
			version = properties.getProperty("app.version");
			Log.logger.info("Application Version: " + version);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	
	public static boolean readyToClose(Level level, String exitString) throws Throwable {
		if (splash != null) {
			splash.dispose();
		}
		TaskManager tm = TaskManager.getInstance();
		tm.shutdownAndWait();
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