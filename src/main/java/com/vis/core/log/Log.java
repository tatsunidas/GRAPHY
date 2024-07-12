package com.vis.core.log;

import java.io.IOException;
import java.util.Locale;
import java.util.logging.*;

import com.vis.configuration.ConfigInfo;

/**
 * 
 * Log configuration class
 * log file location : ConfigInfo.log_file_path.
 *  * 
 * @author tatsunidas
 * 
 */
public class Log {
	
	public static Logger logger = Logger.getLogger(Log.class.getName());
	
	Handler logFileHandler;
	
	// ANSI escape code
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";
	
	public Log() {
		addLogHandler(logger);
	}
	
	void setGlobalLogger(Logger logger){
		Log.logger = logger;
		if(Log.logger != null) {
			addLogHandler(Log.logger);
		}
	}
	
	/**
	 * 
	 * @param logFilePath
	 * @param limit : if extended limit, last record leaving.(1000000000 means 1G (in byte), if 0, means no limit)
	 * @param nLogFiles
	 * @param append_mode : if true, append lines to existing log file 
	 * @return
	 */
	Handler initHandler(String logFilePath, long limit, int nLogFiles, boolean append_mode) {
		Handler logFileHandler = null;
		try {
			logFileHandler = new FileHandler(ConfigInfo.LogFilePath.toString(), limit, nLogFiles, append_mode);
		} catch (SecurityException e) {
			
		} catch (IOException e) {
			
		}
		if(logFileHandler != null) {
			Formatter formatter =  new SimpleFormatter();
			logFileHandler.setFormatter(formatter);
		}
		return logFileHandler;
	}
	
	void addLogHandler(Logger logger) {
		if(logFileHandler == null) {
			/* if true, append lines to existing log file */
			boolean append_mode = false;
			// if extended limit, last record leaving.
			long limit = 1000000000;// 1G (in byte), if 0, means no limit
			int nLogFiles = 1;//num ber of log files for recording.
			logFileHandler = initHandler(ConfigInfo.LogFilePath.toString(), limit, nLogFiles, append_mode);
		}
		if(logFileHandler != null) {
			logger.removeHandler(logFileHandler);
			logger.addHandler(logFileHandler);
		}
	}
	
	/**
	 * If locale is japan, return as-is. because ansi string will be garbled characters.
	 * 
	 * @param lv logger level
	 * @param message message text string
	 * @return
	 */
	public static String message(Level lv, String message) {
		if(message == null || message.length() < 1) {
			return null;
		}
		if(Locale.getDefault() == Locale.JAPAN) {
			return message;//to avoid garbled characters
		}
		String msg = null;
		if(lv == Level.CONFIG || lv == Level.FINE) {
			msg = ANSI_GREEN + message.toString() + ANSI_RESET;
		}else if(lv == Level.INFO) {
			msg = ANSI_BLUE + message.toString() + ANSI_RESET;
		}else if(lv == Level.WARNING) {
			msg = ANSI_PURPLE + message.toString() + ANSI_RESET;
		}else if(lv == Level.SEVERE) {
			msg = ANSI_RED + message.toString() + ANSI_RESET;
		}else {
			msg = ANSI_BLACK + message.toString() + ANSI_RESET;
		}
		return msg;
	}
}