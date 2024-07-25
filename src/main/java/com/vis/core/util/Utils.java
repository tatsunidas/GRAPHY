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
package com.vis.core.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.log.Log;

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
	
	/**
	 * The DB location can be changed as desired.
	 * However, you cannot change the other configuration folders (conf, temp, etc), so do not confuse them.
     * @return current graphy db location (this is different from current app directory)
     */
	public static File getGraphyDBLocation() {
		try {
			Properties prop = PropertiesUtil.loadProperties(ConfigInfo.GRAPHY_Props.toString());
			if (prop == null) {
				throw new Exception("Can not load graphy.properties...");
			} else {
				String loc = prop.getProperty(GraphyProp.LocalDBLocation.name());
				if (loc == null || loc.isBlank()) {
					loc = ConfigInfo.DefaultDBLocation.toString();
					PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props.toString(), GraphyProp.LocalDBLocation.name(),loc);
				}
				return new File(loc);
			}
		} catch (Exception e) {
			Log.logger.severe("can not find graphy.properties::DatabaseHandler::loadDBLocationFromProp");
		}
		return null;
	}
	
	public static Path getJarPath() throws URISyntaxException {
		CodeSource codeSource = Utils.class.getProtectionDomain().getCodeSource();
		if (codeSource != null) {
			URL jarUrl = codeSource.getLocation();
			if (jarUrl != null) {
				return Paths.get(jarUrl.toURI()).toAbsolutePath();
			}
		}
		return null;
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
					Log.logger.severe("temp dir creation failed...:NonDicomImport");
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
	 * For file system
	 * @param sourceDir
	 * @param destinationDir
	 * @throws IOException
	 */
	public static void copyDirectoryRecursively(Path sourceDir, Path destinationDir) throws IOException {
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetPath = destinationDir.resolve(sourceDir.relativize(dir));
                if (!Files.exists(targetPath)) {
                    Files.createDirectories(targetPath);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destinationDir.resolve(sourceDir.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
	
	public static void copyResource(String resource, String dest, Class<?> c) throws IOException {
	    InputStream src = c.getResourceAsStream(resource);
	    Files.copy(src, Paths.get(dest), StandardCopyOption.REPLACE_EXISTING);
	    src.close();
	}
	
	/**
	 * Copy file from resource in Jar.
	 * 
	 * e.g., 
	 * resourcename = /resources/weasis/weasis-portable
	 * to = /home/tatsunidas
	 * as a result : /home/tatsunidas/contents of resources
	 * 
	 * @param resourcename : e.g., /resources/weasis/weasis-portable
	 * @param to : e.g., /home/tatsunidas/
	 * @throws Exception
	 */
	public static void copyResourceFromJAR(String resourcename, String to) throws Exception {
		
		//resourcename must start with "/resources".
		if(!resourcename.startsWith("/resources")) {
			if(!resourcename.startsWith("/")) {
				resourcename = "/resources" + resourcename;
			}else {
				resourcename = "/resources/" + resourcename;
			}
		}
		
		final String name = resourcename;//to handle in walkFileTree.

		Path jar = getJarPath();//full path. 

		final URI jarFileUri = URI.create("jar:file:" + jar);

		Map<String, String> env = new HashMap<>();//empty map
		
		final FileSystem fs = FileSystems.newFileSystem(jarFileUri, env);
		Path root = fs.getPath(name);

		// recursive
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				InputStream is = Utils.class.getResourceAsStream(file.toString());
				//remove /resource/name from destination path
				Files.copy(is, Paths.get(to + file.toString().substring(name.length())));
				is.close();
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				//remove /resource/name from destination path
				if (!Files.exists(Paths.get(to + dir.toString().substring(name.length())), LinkOption.NOFOLLOW_LINKS)) {
					Files.createDirectories(Paths.get(to + dir.toString().substring(name.length())));
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * delete temp dir and included files
	 */
	public static void eraseTemporalDir() {
		File tmp = new File("./" + ConfigInfo.TemporalDirName.toString());
		if (tmp.exists()) {
			if (tmp.listFiles().length > 0) {
				try {
					/*
					 * delete all dirs and files
					 */
					FileUtils.deleteDirectory(tmp);
				} catch (IOException e) {
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
