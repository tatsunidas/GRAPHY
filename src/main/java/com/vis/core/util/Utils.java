package com.vis.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	
	/**
	 * ./temp/dateAndTimeDir
	 * @return
	 */
	public static File createNewDirInTemp() {
		File tempParentDir = new File(getConfSubDirPath(ConfigInfo.TemporalDirName));
		Calendar now = Calendar.getInstance();
		String tempDirName = now.getTime().toString().replace(":", "_");// do not include ":" in path.
		Path tempDirPath = null;
		if(!new File(tempParentDir.getAbsolutePath()+File.separator+tempDirName).exists()) {
			Path root = tempParentDir.toPath();
			try {
				tempDirPath = Files.createTempDirectory(root, tempDirName);
				if(tempDirPath == null) {
					System.out.println("temp dir creation failed...:NonDicomImport");
					throw new NullPointerException("Can not create Temp Folder to create dcm.");
				}
				return tempDirPath.toFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * delete temp dir and included files
	 */
	public static void eraseTemporalDir() {
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
	
	public static void eraseTemporalDirContents() {
		File tmp = new File("./" + ConfigInfo.TemporalDirName.toString());
		if (tmp.exists()) {
			if (tmp.listFiles().length > 0) {
				File[] tmp_files = tmp.listFiles();
				for(File f : tmp_files) {
					if(f.isFile()) {
						try {
							FileUtils.delete(f);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}else {
						try {
							FileUtils.deleteDirectory(f);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
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

	public static String calculateCurrentAge(java.util.Date birthOfDate) {
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
	
	public static Integer calculateAge(java.util.Date birthOfDate, java.util.Date studyDate) {
		if (birthOfDate == null || studyDate == null) {
			return null;
		}
		Calendar any = Calendar.getInstance();
		any.setTime(birthOfDate);
		int year0 = any.get(Calendar.YEAR);
		int month0 = any.get(Calendar.MONTH) + 1;
		int date0 = any.get(Calendar.DATE);
		any.setTime(studyDate);
		int year1 = any.get(Calendar.YEAR);
		int month1 = any.get(Calendar.MONTH) + 1;
		int date1 = any.get(Calendar.DATE);
		LocalDate born = LocalDate.of(year0, month0, date0);
		LocalDate study = LocalDate.of(year1, month1, date1);
		Period dif = Period.between(born, study);
		return dif.getYears();
	}
	
	/**
	 * yyyy/MM/dd format
	 * @param bod
	 * @param studyDate
	 * @return
	 */
	public static Integer calculateAge(String bod, String studyDate) {
		if (bod == null || studyDate == null) {
			return null;
		}
		java.util.Date birth = DateUtils.toDateObj(bod, "-");
		java.util.Date studydate = DateUtils.toDateObj(studyDate, "-");
		if(birth ==null || studydate == null) {
			return null;
		}
		long diff = studydate.getTime() - birth.getTime();
		int diffDays = (int) (diff / (24 * 60 * 60 * 1000));
		return (int) diffDays / 365;
	}
}
