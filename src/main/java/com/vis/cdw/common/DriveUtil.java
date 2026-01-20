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
package com.vis.cdw.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vis.core.log.Log;


/**
 * 
 * @author tatsunidas
 *
 */
/**
 * Drive management utilities. Handles interaction with cdrtools (checkdrive)
 * and file system mount points.
 */
public class DriveUtil {

	private static final Logger log = Logger.getLogger(DriveUtil.class.getName());
	// --- CDRecord Wrapper Methods ---

	/**
	 * Checks if the drive at the specific SCSI address is available using 'cdrecord
	 * -checkdrive'.
	 */
	/**
     * Checks if the drive at the specific SCSI address is available.
     */
    public static boolean isDriveReady(String scsiAddress) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ExecutionProp.loadCdrecordExecution().getAbsolutePath());
        cmd.add("-checkdrive");
        cmd.add("dev=" + scsiAddress);

        try {
            List<String> result = executeCommand(cmd);
            
            // 成功(exit 0)していれば当然OK
            if (result != null) {
                return true;
            }
            
            // 【追加修正】Windowsの場合の救済措置
            // Windowsでは checkdrive が排他制御等で exit -1 になることが頻発します。
            // しかし、このメソッドが呼ばれている時点で scanbus (一覧取得) には成功して
            // アドレス(0,2,0等)が渡ってきているはずなので、
            // Windowsなら「エラーが出てもドライブは存在する」とみなして true を返します。
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                log.warning("Windows: checkdrive failed for " + scsiAddress + ", but assuming drive exists.");
                return true; 
            }
            
            return false;

        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to check drive: " + scsiAddress, e);
            // 例外の場合もWindowsなら強行突破させるかどうかですが、
            // ここは一旦 false のままとし、上記 result==null の分岐で救います。
            return false;
        }
    }
    
	/**
	 * Loads detailed drive information using 'cdrecord -checkdrive'.
	 */
	public static DriveInfo loadDriveInfo(String scsiAddress) {
		if (!isDriveReady(scsiAddress)) {
			return null;
		}

		List<String> cmd = new ArrayList<>();
		cmd.add(ExecutionProp.loadCdrecordExecution().getAbsolutePath());
		cmd.add("-checkdrive");
		cmd.add("dev=" + scsiAddress);

		try {
			List<String> lines = executeCommand(cmd);
			if (lines == null)
				return null;

			String vendor = "";
			String identification = "";
			String deviceType = "";

			for (String line : lines) {
				if (line.contains("Device type")) {
					deviceType = parseValue(line);
				} else if (line.contains("Vendor_info")) {
					vendor = parseValue(line);
				} else if (line.contains("Identifikation")) {
					identification = parseValue(line);
				}
			}
			return new DriveInfo(scsiAddress, vendor, identification, deviceType);

		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to load drive info: " + scsiAddress, e);
			return null;
		}
	}

	private static String parseValue(String line) {
		int idx = line.indexOf(':');
		return (idx != -1 && idx < line.length() - 1) ? line.substring(idx + 1) : "";
	}

	/**
     * Gets display names for all detected "removable" drives.
     * This works even if no disc is inserted.
     */
	public static List<String> getAvailableDriveNames() {
		// 動的スキャン結果を使う
		List<String> scannedAddresses = scanScsiAddresses();
		List<String> names = new ArrayList<>();

		for (String scsiAddress : scannedAddresses) {
			// 詳細情報（ベンダー名など）を取得
			DriveInfo info = loadDriveInfo(scsiAddress);
			if (info != null) {
				names.add(info.getDisplayName());
			} else {
				// 万が一 loadDriveInfo が失敗しても、アドレスだけでも表示する（保険）
				names.add("Generic Drive (" + scsiAddress + ")");
			}
		}
		return names;
	}
	
	/**
     * Executes 'cdrecord -scanbus' and filters ONLY optical drives.
     */
	public static List<String> scanScsiAddresses() {
		List<String> cmd = new ArrayList<>();
		cmd.add(ExecutionProp.loadCdrecordExecution().getAbsolutePath());
		cmd.add("-scanbus");

		List<String> foundAddresses = new ArrayList<>();

		try {
			List<String> lines = executeCommand(cmd);
			if (lines == null)
				return foundAddresses;

			for (String line : lines) {
				String trimmed = line.trim();

				// 1. SCSIアドレスのパターンに一致するか確認 (例: "0,0,0")
				if (trimmed.matches("^\\d+,\\d+,\\d+.*")) {

					// 2. 【重要】光学ドライブに関連するキーワードが含まれているかチェック
					// これがないと、HDDや空のスロットも拾ってしまう
					if (isOpticalDriveLine(trimmed)) {
						String scsiAddr = trimmed.split("\\s+")[0];
						foundAddresses.add(scsiAddr);
					}
				}
			}
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to scan bus", e);
		}

		return foundAddresses;
	}

	/**
	 * scanbusの出力行が光学ドライブを示しているか判定するヘルパーメソッド
	 */
	private static boolean isOpticalDriveLine(String line) {
		String upperLine = line.toUpperCase();

		// 明らかに除外すべきものを弾く (Disk=HDD, Processor=CPUなど)
		if (upperLine.contains("DISK") || upperLine.contains("PROCESSOR")) {
			return false;
		}

		// 光学ドライブを示すキーワードが含まれているか
		// 一般的には "CD-ROM", "DVD", "BD", "WRITER", "REMOVABLE" などが含まれる
		return upperLine.contains("CD") || upperLine.contains("DVD") || upperLine.contains("BD")
				|| upperLine.contains("WRITER") || upperLine.contains("ROM");
	}
    
	/**
	 * Finds the file system path where the optical disc is mounted. Supports
	 * Windows, Mac, and Linux (Standard paths).
	 * CDが挿入されていないと検出できない。
	 */	
	public static List<File> findAllDriveMountPoints() {
		List<File> searchRoots = getSystemSearchRoots();
		List<File> foundDrives = new ArrayList<>(); // 結果格納用リスト

		for (File root : searchRoots) {
			if (!root.exists())
				continue;

			try {
				FileStore store = Files.getFileStore(root.toPath());
				String type = store.type().toUpperCase();

				// Common file system types for Optical Media
				// Windows: CDFS, UDF
				// *nix: iso9660, udf, cd9660
				if (type.contains("CD") || type.contains("DVD") || type.contains("UDF") || type.contains("ISO9660")) {
					foundDrives.add(root);
				}
			} catch (IOException e) {
				continue;
			}
		}
		return foundDrives;
	}
	
	/**
     * 指定されたデバイスに空のディスクが入っているか確認します。
     * コマンド: cdrecord -minfo dev=x,y,z
     */
	/**
     * 指定されたデバイスに空のディスクが入っているか確認します。
     * 判定不能なエラーが出た場合も false を返しますが、ログに残します。
     */
    public static boolean isDiskEmpty(String device) {
        // staticメソッドとして定義（BurnerWindowの呼び出しに合わせています）
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(ExecutionProp.loadCdrecordExecution().getAbsolutePath());
        cmd.add("-minfo");
        cmd.add("dev=" + device);

        try {
            // executeCommandは以前作成したものをstaticにするか、インスタンス化して呼んでください
            // ここでは簡易的にProcessBuilderで書きます
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true); 
            Process p = pb.start();

            boolean isEmpty = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // デバッグ用ログ
                    // log.info("[minfo] " + line); 
                    
                    String lower = line.toLowerCase();
                    // "empty" または "blank" があれば空とみなす
                    if (lower.contains("disk status: empty") || lower.contains("blank")) {
                        isEmpty = true;
                    }
                    // "appendable" (追記可能) も空き領域ありとみなすなら true
                    if (lower.contains("disk status: appendable")) {
                        isEmpty = true;
                    }
                }
            }
            p.waitFor();
            return isEmpty;

        } catch (Exception e) {
            Log.logger.warning("Failed to check media info: " + e.getMessage());
            return false;
        }
    }

    /**
     * 空き容量を取得します。失敗時は -1 を返します。
     */
    public static long getMediaFreeSpaceInBlocks(String device) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(ExecutionProp.loadCdrecordExecution().getAbsolutePath());
        cmd.add("-minfo");
        cmd.add("dev=" + device);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            long size = -1;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Remaining writable size:")) {
                         String[] parts = line.split(":");
                         if (parts.length > 1) {
                             String blocks = parts[1].toLowerCase().replace("blocks", "").trim();
                             try {
                                 size = Long.parseLong(blocks);
                             } catch (NumberFormatException e) {}
                         }
                    }
                }
            }
            p.waitFor();
            return size;
        } catch (Exception e) {
            return -1;
        }
    }
	
	/**
     * mkisofs -print-size を使用して、ISO化後の正確なサイズ（ブロック数）を取得します。
     * ブロックサイズは通常2048バイトです。
     */
	/**
     * mkisofs -print-size を使用して、ISO化後の正確なサイズ（ブロック数）を取得します。
     * 改良点: ログ出力の強化と、数値パースの柔軟性向上
     */
    public static long getIsoSizeInBlocks(File sourceDir) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(ExecutionProp.loadMakeIsoFsExecution().getAbsolutePath());
        cmd.add("-print-size");
        cmd.add("-quiet"); // 余計なバナー表示を抑制
        cmd.add("-R");     // RockRidge
        cmd.add("-J");     // Joliet
        cmd.add("-V");     // Volume ID (空白対策)
        cmd.add("DICOM_CD");
        cmd.add("-f");     // Follow symlinks
        // パスはすでに正規化されていますが、念のため引用符で囲むか、そのまま渡します
        // ProcessBuilderは個別の引数として渡せばスペースがあっても自動処理します
        cmd.add(sourceDir.getAbsolutePath());

        long estimatedSize = -1;

        try {
            // executeCommand は標準出力とエラー出力をマージしてリストで返します
            List<String> lines = executeCommand(cmd);
            
            if (lines != null) {
                for (String line : lines) {
                    // デバッグ用: mkisofsが何を言っているか全てログに出す
                    log.info("[mkisofs output] " + line);
                    
                    String trimmed = line.trim();
                    
                    // 数字だけの行を探す
                    if (trimmed.matches("^\\d+$")) {
                        try {
                            estimatedSize = Long.parseLong(trimmed);
                        } catch (NumberFormatException e) {
                            // 無視
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warning("Failed to calculate ISO size: " + e.getMessage());
        }
        
        if (estimatedSize == -1) {
            log.warning("mkisofs finished but valid size was not found.");
        }
        
        return estimatedSize;
    }

   
	public static Long getUsableSpace(File driveDir) {
		if (driveDir == null)
			return null;
		try {
			FileStore store = Files.getFileStore(driveDir.toPath());
			return store.getUsableSpace();
		} catch (IOException e) {
			log.log(Level.WARNING, "Failed to get usable space for: " + driveDir, e);
			return null;
		}
	}

	// --- Private Helpers ---

	private static List<File> getSystemSearchRoots() {
		List<File> roots = new ArrayList<>();
		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("win")) {
			Collections.addAll(roots, File.listRoots());
		} else if (os.contains("mac")) {
			File volumes = new File("/Volumes");
			if (volumes.exists() && volumes.isDirectory()) {
				File[] files = volumes.listFiles();
				if (files != null)
					Collections.addAll(roots, files);
			}
		} else {
			// Linux/Unix generic attempts
			String user = System.getProperty("user.name");
			addIfExists(roots, "/media");
			addIfExists(roots, "/media/" + user);
			addIfExists(roots, "/run/media/" + user);
			addIfExists(roots, "/mnt");
			addIfExists(roots, "/cdrom");
		}
		return roots;
	}

	private static void addIfExists(List<File> list, String path) {
		File f = new File(path);
		if (f.exists() && f.isDirectory()) {
			list.add(f);
			// Linuxの/mediaなどは直下がマウントポイントなので、その子要素を追加する必要がある場合が多い
			File[] subs = f.listFiles();
			if (subs != null)
				Collections.addAll(list, subs);
		}
	}

	public static List<String> executeCommand(List<String> command) throws IOException {
		//先にcdrtoolsのbinファイルに実行権限を与える
		if (command != null && !command.isEmpty()) {
            File exeFile = new File(command.get(0));
            if (exeFile.exists() && !exeFile.canExecute()) {
                boolean success = exeFile.setExecutable(true);
                if (success) {
                    log.info("Granted execute permission to: " + exeFile.getAbsolutePath());
                } else {
                    log.warning("Failed to grant execute permission to: " + exeFile.getAbsolutePath());
                }
            }
        }
		
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);

		Process process = pb.start();
		List<String> output = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.add(line);
			}
		}

		try {
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				// 失敗時はnullを返す設計（呼び出し元でハンドリング）
				log.warning("Command failed (exit " + exitCode + "): " + command);
				return null;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while executing command", e);
		}

		return output;
	}
	
	public static int executeCommand(List<String> command, String taskName) throws MediaCreationException {
		// 実行権限のチェックと付与
		if (!command.isEmpty()) {
			File exe = new File(command.get(0));
			if (exe.exists() && !exe.canExecute()) {
				if (exe.setExecutable(true)) {
					log.info("Granted execute permission to: " + exe.getAbsolutePath());
				} else {
					log.warning("Failed to grant permission to: " + exe.getAbsolutePath());
				}
			}
		}

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true); // エラー出力を標準出力に統合

		Process process = null;

		// ★追加：ログを貯めておくためのバッファ
		StringBuilder outputLog = new StringBuilder();

		try {
			log.info("[" + taskName + "] Executing: " + String.join(" ", command));
			process = pb.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "MS932"))) { // Windows文字化け対策
				String line;
				while ((line = reader.readLine()) != null) {
					log.info("[" + taskName + "] " + line);

					// ★追加：全行を記録しておく
					outputLog.append(line).append("\n");
				}
			}

			int exitCode = process.waitFor();

			// ★変更：失敗時に、貯めておいたログを例外メッセージに突っ込む
			if (exitCode != 0) {
				throw new MediaCreationException(taskName + " failed (Exit code " + exitCode + ").\n\n"
						+ "--- エラー詳細 ---\n" + outputLog.toString());
			}

			return exitCode;

		} catch (IOException | InterruptedException e) {
			throw new MediaCreationException("Exception during " + taskName, e);
		} finally {
			if (process != null && process.isAlive())
				process.destroy();
		}
	}
}
