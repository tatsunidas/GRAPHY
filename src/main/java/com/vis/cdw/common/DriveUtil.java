package com.vis.cdw.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;

public class DriveUtil {
	
	/*
	 * https://kagamihoge.hatenablog.com/entry/20130110/1357810886
	 */
	
	private String[] makeCheckDriveCmd(String devicescsi) {
		return new String[] {ExecutionProp.loadCdrecordExecution().getAbsolutePath(),"-checkdrive","dev="+devicescsi};
	}
	
	/*
	 * メディアは無関係で、ドライブの情報のみ表示。-inqでもよさそう。
	 */
	public boolean checkDrive(String devicescsi) {
		int exit = 0;
		try {
			java.lang.Runtime rt = java.lang.Runtime.getRuntime();
    		java.lang.Process p = rt.exec(makeCheckDriveCmd(devicescsi));
            exit = p.waitFor();
            if(exit == 0) {
				return true;
			}else {
				//no drive/device
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}
	
	public java.util.HashMap<String,String> loadDriveInfo(String devicescsi) {
		int exit = 0;
		try {
			java.lang.Runtime rt = java.lang.Runtime.getRuntime();
    		java.lang.Process p = rt.exec(makeCheckDriveCmd(devicescsi));
            exit = p.waitFor();
            if(exit == 0) {
				String stdout = IOUtils.toString(p.getInputStream(), "UTF-8");
				/*Examples
				 * Device type    : Removable CD-ROM
				 * Version        : 0
				 * Response Format: 2
				 * Capabilities   : 
				 * Vendor_info    : 'ASUS    '
				 * Identifikation : 'SDRW-08U5S-U    '
				 * Revision       : 'F201'
				 * Device seems to be: Generic mmc2 DVD-R/DVD-RW/DVD-RAM.
				 * Using generic SCSI-3/mmc   CD-R/CD-RW driver (mmc_cdr).
				 * Driver flags   : MMC-3 SWABAUDIO BURNFREE 
				 * Supported modes: TAO PACKET SAO SAO/R96P SAO/R96R RAW/R16 RAW/R96P RAW/R96R
				 */
				String[] info = stdout.split("\n");
				HashMap<String,String> res = new HashMap<>();
				res.put("SCSI", devicescsi);
				for(String row :info) {
					if(row.contains("Device type")) {
						res.put("DeviceType", row.substring(row.indexOf(":")+1).replace(" ", "").replace("'", "").replace("\n", "").replace("\r", ""));//\rも改行文字
					}else if(row.contains("Vendor_info")) {
						res.put("VendorInfo", row.substring(row.indexOf(":")+1).replace(" ", "").replace("'", "").replace("\n", "").replace("\r", ""));
					}else if(row.contains("Identifikation")) {
						res.put("Identification", row.substring(row.indexOf(":")+1).replace(" ", "").replace("'", "").replace("\n", "").replace("\r", ""));
					}
				}
				return res;
			}else {
				//no drivedevice
				return null;
			}
		} catch (Exception e) {
			return null;
		}
	}
	
	/*
	 * all OS 
	 */
	private ArrayList<String> grubAliveDevices() {
		String[] candidate = CDRToolsProperties.loadDeviceCandidates();
		ArrayList<String> alive = new ArrayList<String>();
		for(String candi:candidate) {
			if(checkDrive(candi)) {
				alive.add(candi);
			}
		}
//		return list.toArray(new String[list.size()]);//if you want legacy array
		return alive;
	}
	
	public String[] grubAliveDevicesByNickName() {
		ArrayList<String> alive =  grubAliveDevices();
		ArrayList<String> names = new ArrayList<String>();
		for(String device : alive) {
			names.add(driveNickName(loadDriveInfo(device)));
		}
		return names.toArray(new String[names.size()]);//if you want legacy array
//		return names;
	}
	
	/**
	 * this is not the device name which defined in Cdrecord.
	 * just readable name in this apps. 
	 */
	public String driveNickName(HashMap<String,String> driveInfo) {
		String name = new String(driveInfo.get("DeviceType")+"_"+driveInfo.get("VendorInfo")+"_"+driveInfo.get("Identification")+"_"+driveInfo.get("SCSI"));
		return name.trim();
	}
	
	/*
	 * this is deprecated.
	 * you should use cdrecord directly.
	 * This is useful for windows.
	 * https://stackoverflow.com/questions/7034216/get-all-dvd-drives-in-java
	 */
	public ArrayList<File> grubDriveDir4Windows() {
		ArrayList<File> driveDirs = new ArrayList<File>();
		for (File root : findRootDirs()) {
			/*
			 * a query on the file store's type() should do it.
			 * With a CD not in the drive, the getFileStore() call throws
			 * java.nio.file.FileSystemException: D:: The device is not ready.
			 */
			try {
				FileStore store = Files.getFileStore(root.toPath());
				String type = store.type();
				//NTFS:HDD or SSD or USB
				//FAT32 is almost USB
				if(type.equals("NTFS") || type.equals("FAT32")) {
					continue;
				}
				/*
				 * タイプ名はOSによっても変わりそう。
				 * UDFは、WinデフォルトのCDRを入れてUSBフォーマットしたときのタイプ名
				 * フォーマットしない、あるいはCDのように使うとした場合は、incorrect functionエラーになる
				 */
				if(type.contains("CD") || type.contains("DVD") || type.contains("UDF")) {
					driveDirs.add(root);
				}
			} catch (IOException e) {
				//フォーマットしない、あるいはCDのように使うとした場合は、incorrect functionエラーになるので、レスキュー
				/*
				 * D:\: D:\: デバイスの準備ができていません。
				 * F:\: F:\: ファンクションが間違っています。
				 */
				driveDirs.add(root);
				System.out.println(root.getAbsolutePath() + ": <Maybe this dir is Drive>" + e.getLocalizedMessage());
				
			}
		}
		return driveDirs.size() < 1  ? null : driveDirs;
	}
	
	public void test() {
		FileSystem fs = FileSystems.getDefault();
		for (Path rootPath : fs.getRootDirectories()) {
			try {
				FileStore store = Files.getFileStore(rootPath);
				System.out.println(rootPath + ": " + store.type());
			} catch (IOException e) {
				System.out.println(rootPath + ": " + "<error getting store details>");
			}
		}
	}
	
	//https://stackoverflow.com/questions/12144098/get-the-drive-program-is-running-on-in-mac
	public File grubDriveDir4Mac() {
		File[] mnt_pnt_files = new File("/Volumes").listFiles();
		File driveDir = null;
		for(File t : mnt_pnt_files) {
			try {
				FileStore store = Files.getFileStore(t.toPath());
				String type = store.type();
				if(type.contains("CD") || type.contains("UDF") || type.contains("DVD")) {
					driveDir = t;
					break;
				}
			} catch (IOException e) {
				System.out.println(t.getAbsolutePath() + " : " + "<error getting store details>");
			}
		}
		return driveDir;
	}
	
	//https://forums.ubuntulinux.jp/viewtopic.php?id=8834
	//https://superuser.com/questions/630588/how-to-detect-whether-there-is-a-cd-rom-in-the-drive
	/*
	 * linux is means ubuntu...
	 */
	//STILL NOT TEST
	public File grubDriveDir4Linux() {
		/*
		 * On ubuntu, cd/dvd drive is mounted by gnome automatically.
		 * but, in this case, does NOT named /dev/sr0 always.
		 * some cases, mount point was set to "/media/something/"
		 * Or, my environment always mount to burn:///
		 * Here, we using burn:///.
		 */
		//if you need user-name::System.getProperty("user.name")
//		String mnt_parent_point = "/media";
//		File[] mnt_pnt_files = new File(mnt_parent_point).listFiles();
//		File driveDir = null;
//		for(File t : mnt_pnt_files) {
//			try {
//				FileStore store = Files.getFileStore(t.toPath());
//				String type = store.type();
//				if(type.contains("CD") || type.contains("UDF") || type.contains("DVD")) {
//					driveDir = t;
//					break;
//				}
//			} catch (IOException e) {
//				System.out.println(t.getAbsolutePath() + " : " + "<error getting store details>");
//			}
//		}
		return new File("burn:///");
	}
	
	public Long readUsableSpaceOnDisk(File driveDir) {
		try {
			FileStore store = Files.getFileStore(driveDir.toPath());
			return store.getUsableSpace();//suitable
//			return store.getTotalSpace();
		} catch (IOException e) {
			System.err.println(driveDir.getAbsolutePath() + ": " + "<error when getting usable space details>");
		}
		return null;
	}
	
	/*
	 * for windows
	 * C:\
	 * D:\
	 * E:\
	 * 
	 * for linux, always return only ["/"]
	 */
	public File[] findRootDirs() {
		return File.listRoots();
	}

	public boolean diskReady() {
		return false;
	}

}
