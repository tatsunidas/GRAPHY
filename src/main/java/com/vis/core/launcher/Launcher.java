package com.vis.core.launcher;

import java.util.HashMap;
import java.util.logging.Level;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.vis.configuration.StartingUpConfigurations;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.log.Log;

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
		new ApplicationFacade(parseArgs(args));
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
