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
package com.vis.core.launcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.StartingUpConfigurations;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.log.Log;
import com.vis.core.util.Platform;
import com.vis.core.util.PropertiesUtil;

/**
 * GRAPHY launcher
 * 
 * @author tatsunidas
 *
 */
public class Launcher {
	
	public static void main(String[] args) {
		new Launcher(args);
	}
	
	public Launcher(String[] args) {
		Log.getInstance();//this method also do Log.init();
		new ApplicationFacade(parseArgs(args));
	}
	
	public static void restart() {
		// JVM
		String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		// main class
		String jarFilePath = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().getPath())
				.getAbsolutePath();

		// build command
		List<String> command = new ArrayList<>();
		command.add(java);
		String xms = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Xms);
		String xmx = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.Xmx);
		command.add("-Xms" + ((xms != null && !xms.isBlank()) ? "1g" : xms)); // 初期ヒープサイズを設定
		command.add("-Xmx" + ((xmx != null && !xmx.isBlank()) ? "10g" : xmx)); // 最大ヒープサイズを設定
		command.add("-Djava.library.path=" + Platform.getOpenCVNativeLibLocation());
		command.add("-cp");
		command.add(jarFilePath);
		command.add(Launcher.class.getName());
		
		//this following code also can use for instead -cp.
//		command.add("-jar");
//		command.add(jarFilePath);

		// add original commands
		ArrayList<String> orgCmd = new ArrayList<>();
		boolean no_splash = Boolean.valueOf(PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.NO_SPLASH));
		if(no_splash) {
			orgCmd.add(StartingUpConfigurations.no_splash.name());
		}
		
		command.addAll(orgCmd);
		
		ProcessBuilder builder = new ProcessBuilder(command);

		try {
			builder.start();
		} catch (IOException e) {
			e.printStackTrace();
			Log.logger.severe("Sorry, something happened when restarting, this process will close automatically. After that, you can restart GRAPHY by manualy...");
			try {
				Thread.sleep(1000 * 5);
			} catch (InterruptedException e1) {
				System.exit(0);
			}
			System.exit(0);
		}

		// close current process
		System.exit(0);
	}
	
	private HashMap<StartingUpConfigurations, String[]> parseArgs(String[] args){
		if(args == null || args.length == 0) {
			return new HashMap<StartingUpConfigurations, String[]>();
		}else {
			return readArgs(args);
 		}
	}

	private HashMap<StartingUpConfigurations, String[]> readArgs(String[] args) {
		HashMap<StartingUpConfigurations, String[]> map = new HashMap<>();

		//https://commons.apache.org/proper/commons-cli/usage.html
		org.apache.commons.cli.Options options = new Options();
		
		// example
//		Option input_opt = 
//				Option
//				.builder("i")//short name of the option
//				.longOpt("input")//long name of the option
//				.required(false)
//				.hasArg(true)
//				.argName("file")
//				.numberOfArgs(1)
//				.desc("specify input file location to show it.")
//				.build();
		
		Option splash_opt = 
				Option
				.builder(StartingUpConfigurations.no_splash.name())//short name of the option
				.longOpt(StartingUpConfigurations.no_splash.name())//long name of the option
				.required(false)
				.hasArg(false)
				.desc("Starting up without show splash window.")//no_i18n
				.build();
		
		options.addOption(splash_opt);

		HelpFormatter hf = new HelpFormatter();
		hf.printHelp("[options]", options);

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			Log.logger.log(Level.WARNING, e.getCause().getMessage());
			return map;
		}
		
		//cmd.hasOption::check both short name and long name 
		if(cmd.hasOption(StartingUpConfigurations.no_splash.name())){
			Log.logger.info("graphy option : "+StartingUpConfigurations.no_splash.name());
			map.put(StartingUpConfigurations.no_splash, new String[]{"true"});//cmd.getOptionValues("i")
		}
		//to be continue...
       
		return map;
	}
}
