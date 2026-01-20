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

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;

import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.launcher.Launcher;
import com.vis.core.log.Log;
import com.vis.db.DatabaseHandler;

/**
 * 
 * @author tatsunidas
 *
 */
public class Utils {

	public static boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()
			.toString().indexOf("-agentlib:jdwp") > 0;

	
	public static File getCurrentWorkingDirectory() {
		//return new File(Paths.get("").toAbsolutePath().toString());
		return Platform.getAppDirectory();
	}
	
	/**
	 * The DB location.
	 * 
	 * Alternate, DatabaseHandler's getListenerDetail also can return dir.
	 * But, when starting-up, derby acquiring dbdir to start own.
	 * Database Dir is handled both graphy_prop and derbydb(listener table).
	 * 
	 * However, you cannot change the other configuration folders (conf, temp, etc), so do not confuse them.
     * @return current graphy db location (this is different from current app directory)
     */
	public static File getGraphyDBLocationFromProp() {
		try {
			Properties prop = PropertiesUtil.loadProperties(ConfigInfo.GRAPHY_Props.toString());
			if (prop == null) {
				throw new Exception("Can not load graphy.properties...");
			} else {
				return new File(ConfigInfo.DefaultDBLocation.toString());
			}
		} catch (Exception e) {
			Log.logger.severe("can not find graphy.properties::DatabaseHandler::loadDBLocation");
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

	@Deprecated
	public static String getConfSubDirPath(ConfigInfo dirNameType) {
		return ConfigInfo.getPath(dirNameType);
	}
	
	/**
	 * ./temp/dateAndTimeDir
	 * @return
	 */
	public static File createNewDirInTemp() {
	    // 1. 親ディレクトリのパス解決 (Path APIを使用)
	    Path tempParentPath = Paths.get(ConfigInfo.getPath(ConfigInfo.TemporalDirName)).toAbsolutePath().normalize();

	    // 2. 親ディレクトリが存在しない場合は作成する
	    if (!Files.exists(tempParentPath)) {
	        try {
	            Files.createDirectories(tempParentPath);
	        } catch (IOException e) {
	            Log.logger.log(Level.SEVERE, "親Tempディレクトリの作成に失敗しました: " + tempParentPath, e);
	            throw new RuntimeException("Cannot create parent temp folder.", e);
	        }
	    }

	    // 3. プレフィックスの生成 (yyyyMMdd_HHmmss_ 形式で、ソートしやすく安全な名前に)
	    String prefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_"));

	    try {
	        // 4. 一意なディレクトリを作成 (自動でランダムなサフィックスが付くため重複チェック不要)
	        Path tempDirPath = Files.createTempDirectory(tempParentPath, prefix);
	        return tempDirPath.toFile();

	    } catch (IOException e) {
	        Log.logger.log(Level.SEVERE, "Tempディレクトリの作成に失敗しました", e);
	        throw new RuntimeException("Cannot create Temp Folder to create dcm.", e);
	    }
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
     * 指定されたディレクトリの内容を別のディレクトリにコピーします。
     *
     * @param sourceDirStr コピー元のディレクトリパス文字列。null であってはならない。
     * @param destDirStr   コピー先のディレクトリパス文字列。null であってはならない。コピー先に存在しない場合は作成されます。
     * @throws IOException          コピー中に I/O エラーが発生した場合。
     * @throws NullPointerException 引数が null の場合。
     * @throws IllegalArgumentException sourceDirStr がディレクトリでない場合。
     */
    public static void copyDirectory(String sourceDirStr, String destDirStr) throws IOException {
        // Objects.requireNonNull で null チェックと例外スローを行う
        Path sourceDir = Paths.get(Objects.requireNonNull(sourceDirStr, "Source directory must not be null"));
        Path destDir = Paths.get(Objects.requireNonNull(destDirStr, "Destination directory must not be null"));

        // コピー元がディレクトリであることを確認
        if (!Files.isDirectory(sourceDir) || !Files.exists(sourceDir)) {
            Log.logger.warning("Source is not exists or Source must be a directory: " + sourceDir);
            return;
        }

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // コピー元からの相対パスを計算
                Path relativePath = sourceDir.relativize(dir);
                // コピー先ディレクトリパスを計算
                Path targetDir = destDir.resolve(relativePath);
                // コピー先にディレクトリを作成 (存在しない場合のみ)
                // createDirectories は親ディレクトリも含めて作成してくれる
                Files.createDirectories(targetDir);
                System.out.println("Created directory: " + targetDir); // ログ出力例
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // コピー元からの相対パスを計算
                Path relativePath = sourceDir.relativize(file);
                // コピー先ファイルパスを計算
                Path targetFile = destDir.resolve(relativePath);
                // ファイルをコピー (既存のファイルを上書きする場合は REPLACE_EXISTING を追加)
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                System.out.println("Copied file: " + file + " to " + targetFile); // ログ出力例
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // ファイルアクセスに失敗した場合のエラー処理
                System.err.println("Failed to access file: " + file + " - " + exc);
                // エラーが発生しても処理を続ける場合は CONTINUE、中断する場合は例外をスロー
                // throw exc; // エラー発生時に処理を中断する場合
                return FileVisitResult.CONTINUE; // エラーがあっても続行する場合
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                 // ディレクトリ処理後のエラー処理 (通常は null)
                if (exc != null) {
                    System.err.println("Error after visiting directory: " + dir + " - " + exc);
                     // throw exc; // エラー発生時に処理を中断する場合
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
	
	 
	public static Color colorFromString(String colorName) {
		if (colorName == null) {
			throw new IllegalArgumentException("Need colorName");
		}
		Color color = null;
		try {
			java.lang.reflect.Field field = Class.forName("java.awt.Color").getField(colorName.trim());
			color = (Color) field.get(null);
		} catch (Exception e) {
			color = null; // Not defined
		}
		return color;
	}
	
	public static boolean isQRRefreshOn() {
		String refreshOnString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RefreshQRTreeTableOn);
		if(refreshOnString == null || refreshOnString.isBlank()) {
			return false;
		}
		return Boolean.parseBoolean(refreshOnString.toLowerCase());
	}
	
	public static boolean ignoreNullSearchKeyWarning() {
		String ignore = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.IgnoreNullSearchKeyWarning);
		if(ignore == null || ignore.isBlank()) {
			return false;
		}
		return Boolean.parseBoolean(ignore.toLowerCase());
	}
	
	public static int availableProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}
	
	public static ResourceBundle i18n() {
		ResourceBundle messages = null;
		try {
			// Specify the UTF-8 using ResourceBundle.Control
			// in resource, i18n/basename_**_**.properties (basename is i18n)
			messages = ResourceBundle.getBundle("i18n.i18n", locale(), new ResourceBundle.Control() {
				@Override
				public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader,
						boolean reload) throws IllegalAccessException, InstantiationException, IOException {
					String bundleName = toBundleName(baseName, locale);
					String resourceName = toResourceName(bundleName, "properties");
					ResourceBundle bundle = null;
					InputStream stream = null;
					if (reload) {
						//do something
					} else {
						stream = loader.getResourceAsStream(resourceName);
					}
					if (stream != null) {
						try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
							bundle = new java.util.PropertyResourceBundle(reader);
						}
					}
					return bundle;
				}
			});
		} finally {
			//do nothing
		}
		return messages;
	}
	
	public static Locale locale() {
		String locale_str = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props.toString(), GraphyProp.Locale.name());
		if(locale_str != null && locale_str.length()!=0) {
			for(Locale l:Locale.getAvailableLocales()) {
				if (l.getLanguage().equals(new Locale(locale_str).getLanguage())) {
					return l;
				}
			}
		}
		return Locale.getDefault();
	}
	
	/**
	 * restart by using script file.
	 */
	public static void restart() {

		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			throw new NullPointerException("Database is NULL, GRAPHY cannot reboot..., please restart manualy.");
		}

		try {
			ApplicationFacade.readyToClose(Level.INFO, "Rebooting...");
		} catch (Throwable e) {
			e.printStackTrace();
		}

		try {
			// 1. 実行中のコード（jarファイルまたはクラスファイル）の場所を取得
			CodeSource codeSource = Launcher.class.getProtectionDomain().getCodeSource();
			File jarFile = new File(codeSource.getLocation().toURI().getPath());
			// 2. jarファイルが置かれているディレクトリを取得
			String jarDir = jarFile.getParent();
			// 3. スクリプトへの絶対パスを構築
			String scriptName = System.getProperty("os.name").toLowerCase().contains("win") 
					? "run.bat"
					: "run.sh";
			File scriptFile = new File(jarDir, scriptName);
			String scriptAbsolutePath = scriptFile.getAbsolutePath();

			// 4. 絶対パスでコマンドを実行
			List<String> command = new ArrayList<>();
			if (System.getProperty("os.name").toLowerCase().contains("win")) {
				// Windowsの場合は cmd /c を経由して実行するのが一般的
				command.add("cmd.exe");
				command.add("/c");
				command.add(scriptAbsolutePath);
			} else {
				command.add("sh");
				command.add(scriptAbsolutePath);
			}
			/*
			 * 起動ファイル側で指定するため不要。
			 */
			// jarファイル自身の絶対パスを引数としてスクリプトに渡す
//			command.add(jarFile.getAbsolutePath());

			System.out.println("Executing command: " + command);
			new ProcessBuilder(command).start();
			/////////////////
			System.exit(0);
			/////////////////
		} catch (URISyntaxException | IOException e) {
			e.printStackTrace();
			System.out.println("Reboot was failed, please restart manualy...");
			JOptionPane.showConfirmDialog(null, "Reboot was failed, please restart manualy...");
		}
	}
}
