package com.vis.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Calendar;

import org.apache.commons.io.FileUtils;

import com.vis.configuration.ConfigInfo;

public class Utils {

	public static boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()
			.toString().indexOf("-agentlib:jdwp") > 0;

	/*
	 * https://stackoverflow.com/questions/4871051/how-to-get-the-current-working-
	 * directory-in-java
	 */
	public static File getGraphyDir() {
		// final File appDirectory = new File(".");//if win10, return currentDir/./, DO
		// NOT USE
		final File appDir = new File(Paths.get("").toAbsolutePath().toString());
		return appDir;
	}

	public static String getConfSubDirPath(ConfigInfo dirNameType) {
		return ConfigInfo.getPath(dirNameType);
	}

	public static void eraseTemporalDirContents() {
		File tmp = new File("./" + ConfigInfo.TemporalDirName.toString());
		if (tmp.exists()) {
			if (tmp.listFiles().length > 0) {
				try {
					FileUtils.deleteDirectory(tmp);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private static ArrayList<File> candidates;
	public static ArrayList<File> readFilesRecursively(File[] filesAndDirectories) {
		if (filesAndDirectories == null) {
			return null;
		}
		candidates = new ArrayList<>();
		for (File f : filesAndDirectories) {
			if (f.isFile()) {
				candidates.add(f);
			} else if (f.isDirectory()) {
				walkFiles(f);
			}
		}
		return candidates;
	}

	private static void walkFiles(File file) {
		if (file == null) {
			return;
		}
		for (File f : file.listFiles()) {
			if (f.isFile()) {
				candidates.add(f);
			} else if (f.isDirectory()) {
				walkFiles(file);
			}
		}
	}

	public static String calculateAge(java.util.Date birthOfDate) {
		if (birthOfDate == null) {
			return "";
		}
		Calendar c = Calendar.getInstance();
		c.setTime(birthOfDate);
		int year = c.get(Calendar.YEAR);
		int month = c.get(Calendar.MONTH) + 1;
		int date = c.get(Calendar.DATE);
		LocalDate l1 = LocalDate.of(year, month, date);
		LocalDate now1 = LocalDate.now();
		Period diff1 = Period.between(l1, now1);
		return String.valueOf(diff1.getYears());
	}

}
