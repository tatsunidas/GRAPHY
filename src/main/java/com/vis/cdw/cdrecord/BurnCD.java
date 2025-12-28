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
package com.vis.cdw.cdrecord;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import com.vis.cdw.common.CDRToolsExec;
import com.vis.cdw.common.DriveUtil;
import com.vis.cdw.common.ExecutionProp;
import com.vis.cdw.common.ExecutionStatusInfo;
import com.vis.cdw.common.MediaCreationException;
import com.vis.core.log.Log;

/**
 * cdrtools (32-bit recommended, 64-bit has BUG)
 * https://opensourcepack.blogspot.com/p/cdrtools.html
 * 
 * Version
 * windows-cdrtools-3.02a01_mingw
 * mac → needs to build from src on macOS.
 * linux → needs to build from src on linux
 * 
 * build cdrtools from src
 * require following external libs.
 * cdrecord : To burn cd-r/dvd-r/blueray
 * mkisofs : To create iso file from folder
 * 
 * How to build > do "make" command on parent of src dir.
 * then, executables will be created in OBJ folder.
 * 
 * Issue
 * - Cannot execute mkisofs from JVM. Use ISOCreationTool instead.(UDF unsupported)
 * 
 * @author tatsunidas
 *
 */
public class BurnCD {

//	private static final int MIN_GRACE_TIME = 2;

	// write mode
//    private static final String TAO = "tao";//track at once
//	private static final String DAO = "dao";// disc at once, default
//    private static final String SAO = "sao";//session at once
//    private static final String RAW = "raw";//same as raw96r
//    private static final String RAW96R = "raw96r";//for old device 
//    private static final String RAW96P = "raw96p";//for old device 
//    private static final String RAW16 = "raw16";//for old device 

	// track type
//    private static final String DATA = "data";
//    private static final String MODE2 = "mode2";
//    private static final String XA = "xa";
//    private static final String XA1 = "xa1";
//    private static final String XA2 = "xa2";
//    private static final String XAMIX = "xamix";
//    private static final String[] TRACK_TYPES = { DATA, MODE2, XA, XA1, XA2, XAMIX};

	private String device = "0,0,0";
//	private String writeMode = DAO;
//	private boolean padding = false;
//    private int graceTime = MIN_GRACE_TIME;

	private String mediaType;
	private String driveLetter;
	private String driveId;

	static final int MIN_RETRY_INTERVAL = 10;
	protected int writeSpeed = -1;
	protected boolean multiSession = false;// always false
	protected boolean appendEnabled = false;// always false
	protected boolean simulate = false;
	protected boolean eject = true;
	protected boolean autoLoad = false;
//    protected int numberOfRetries = 0;
//    protected int retryInterval = 60;
	protected boolean verify = false;// future work
	protected boolean mount = false;// always false, almost, every os were auto mount.
	protected boolean logEnabled = true;
	protected int mountTime = 10; // auto mount waiting time
//    protected int pauseTime = 10;
	protected File logFile;

	Logger log = Log.logger;

	public BurnCD(String device, int speed, boolean eject, boolean verify) {
		// set up
		File homedir = new File(".");
		this.logFile = new File(homedir, "log" + File.separator + "BurnCD.log");
		setDeviceScsi(device);
		setWriteSpeed(speed);
		setEject(eject);
		setVerify(verify);
	}

	/*
	 * device scsi : e.g, 0,0,0
	 */
	public void setDeviceScsi(String devicescsi) {
		this.device = devicescsi;
	}

	public String getDeviceScsi() {
		return device;
	}

	public final boolean isVerify() {
		return verify;
	}

	public final void setVerify(boolean verify) {
		this.verify = verify;
	}

	public final String getDriveLetter() {
		return driveLetter;
	}

	// driveLetter + ':'
	public final void setDriveLetter(String driveLetter) {
		this.driveLetter = driveLetter;
	}

//    public final boolean isAppendEnabled() {
//        return appendEnabled;
//    }
//
//    public final void setAppendEnabled(boolean appendEnabled) {
//        this.appendEnabled = appendEnabled;
//    }

	public final boolean isSimulate() {
		return simulate;
	}

	public final void setSimulate(boolean simulate) {
		this.simulate = simulate;
	}

	public final boolean isEject() {
		return eject;
	}

	public final void setEject(boolean eject) {
		this.eject = eject;
	}

	public final boolean isAutoLoad() {
		return autoLoad;
	}

	public final void setAutoLoad(boolean autoLoad) {
		this.autoLoad = autoLoad;
	}

	
	public final int getWriteSpeed() {
		return writeSpeed;
	}

	/**
	 * upper speed is, in general, DVD+R : 8X DVD-R : 8X DVD+R(SL, M-DISC) : 4X
	 * DVD+RW : 8X DVD-RW : 6X DVD+R(DL) : 6X DVD-R(DL) : 6X DVD-RAM : 5X CD-R : 24X
	 * CD-RW : 24X
	 */
	public final void setWriteSpeed(int writeSpeed) {
		if (writeSpeed < 1 || writeSpeed > 24)
			throw new IllegalArgumentException("writeSpeed:" + writeSpeed);
		this.writeSpeed = writeSpeed;
	}

	public final boolean isMultiSession() {
		return multiSession;
	}

	public final void setMultiSession(boolean multiSession) {
		this.multiSession = multiSession;
	}

	public final boolean isLogEnabled() {
		return logEnabled;
	}

	public final void setLogEnabled(boolean logEnabled) {
		this.logEnabled = logEnabled;
	}

	public final String getMediaType() {
		return mediaType;
	}

	public final void setMediaType(String mediaType) {
		this.mediaType = mediaType.toLowerCase();
	}

	public final String getDriveId() {
		return driveId;
	}

	public final void setDriveId(String driveId) {
		this.driveId = driveId;
	}

	public void load(String device) {
		CDRToolsExec.exec(makeLoadCmd(ExecutionProp.loadCdrecordExecution().getAbsolutePath(), device), device, false);
	}

	public void eject(String device) {
		CDRToolsExec.exec(makeEjectCmd(ExecutionProp.loadCdrecordExecution().getAbsolutePath(), device), device, false);
	}

	public void burn(File isoRoot, File isoImageFile) throws MediaCreationException {
		if (!isoImageFile.exists()) {
            throw new MediaCreationException("ISO file not found: " + isoImageFile.getAbsolutePath());
        }
		
		if (!checkDrive(getDeviceScsi())) {
			log.warning("Drive not ready.");
			throw new MediaCreationException("Drive Check failed");
		}

		int exit = -1;
		OutputStream logout = null;
		try {
			String[] cmdarray = makeBurnCmd(isoImageFile);
			if (logEnabled) {
				logout = new BufferedOutputStream(new FileOutputStream(logFile));
			}
			exit = CDRToolsExec.execAndShowProgress(cmdarray, "Burn cd/dvd maybe failed...", false);
		} catch (IOException e) {
			throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE, e);
		} finally {
			if (logout != null)
				try {
					logout.close();
					if (exit != 0) {
						log.warning("Burn cd/dvd maybe failed...");
					}
					if (verify) {
						load(device);
//                    verify(getDriveLetterOrMountDirectory(), isoImageFile);//future work, deal with every os
						if (eject) {
							eject(getDeviceScsi());
						}
					} else {
						if (eject) {
							eject(getDeviceScsi());
						}
					}
					log.info("Finished Creating Media");
				} catch (Exception e) {
					
				}
		}
	}
	
	private List<String> buildCommand(File isoImageFile) {
		List<String> cmd = new ArrayList<>();
		cmd.add(ExecutionProp.loadCdrecordExecution().getAbsolutePath());
		cmd.add("-v"); // verbose
		cmd.add("speed=" + writeSpeed);
		if (eject)
			cmd.add("-eject");
		cmd.add("dev=" + device);
		cmd.add("-dao"); // Disk At Once推奨
		if (simulate) {
			cmd.add("-dummy");
		}
		cmd.add(isoImageFile.getAbsolutePath());
		
//      if (multiSession) cmd.add("-multi");//never using
//      cmd.add("-" + writeMode);//dao // for auto set mode
//      cmd.add(padding ? "-pad" : "-nopad");//never padding.
//      cmd.add("-" + trackType);//never using
		return cmd;
	}

	protected String[] makeBurnCmd(File isoImageFile) {
		List<String> cmd = buildCommand(isoImageFile);
		return (String[]) cmd.toArray(new String[cmd.size()]);
	}

	public String[] makeLoadCmd(String executable, String device) {
		return new String[] { executable, "-load", "dev=" + device };
	}

	public String[] makeEjectCmd(String executable, String device) {
		return new String[] { executable, "-eject", "dev=" + device };
	}

	/*
	 * isovfy.exe filetoiso also good ?
	 */
	@SuppressWarnings("unused")
	private void verify(String drivePath, File iso) throws MediaCreationException {
		try {
			if (mountTime > 0) {
				try {
					Thread.sleep(mountTime * 1000L);
				} catch (InterruptedException e) {
					log.warning("Mount Time was interrupted!\n");
					log.warning(e.getLocalizedMessage());
				}
			}
			if (mount) {
				mount(drivePath);
			}
			// cmp /dev/cdrom /path/cdrom.iso
			String verifyCmd[] = new String[] { "cmp", drivePath, iso.getAbsolutePath() };
			if (!exec(verifyCmd, "Verification failed!")) {
				JOptionPane.showMessageDialog(null,
						"BurnCD process was not successful, please check/verify disk contents yourself.");
				throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE, "Verification failed!");
			}
		} finally {
			// if (mount) umount(drivePath);
		}
	}

	
	public boolean checkDrive(String device) throws MediaCreationException {
		return new DriveUtil().checkDrive(device);
	}

//    public boolean checkDisk(String executable) throws MediaCreationException {
//        String[] cmdarray = { executable, "--info", "disc", "--drive", driveId};
//        return check(cmdarray, "writeable");
//    }

	// for solaris
	public void mount(String drivePath) throws MediaCreationException {
		if (drivePath == null) {
			return;
		}
		String[] cmdArray = { "mount", drivePath };
		int exit = 0;
		try {
			java.lang.Runtime rt = java.lang.Runtime.getRuntime();
			java.lang.Process p = rt.exec(cmdArray);
			exit = p.waitFor();
		} catch (Exception e) {
			throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE, "mount " + drivePath + " failed:", e);
		}
		if (exit != 0) {
			throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
					"mount " + drivePath + " failed: exit(" + exit + ")");
		}
	}

	// for solaris
	public boolean umount(String drivePath) {
		String[] cmdArray = { "umount", "-l", drivePath };
		String warning = "Failed to unmount " + drivePath + ":";
		return exec(cmdArray, warning);
	}

	private boolean exec(String[] cmd, String warning) {
		int exit = 0;
		java.lang.Process p = null;
		try {
			java.lang.Runtime rt = java.lang.Runtime.getRuntime();
			p = rt.exec(cmd);
			exit = p.waitFor();
			if (exit == 0) {
				return true;
			} else {
				log.warning(warning + " exit(" + exit + ")");
			}
		} catch (Exception e) {
			log.warning(warning);
			return false;
		} finally {
			if (p != null && p.isAlive()) {
				p.destroy();
			}
		}
		return false;
	}

}
