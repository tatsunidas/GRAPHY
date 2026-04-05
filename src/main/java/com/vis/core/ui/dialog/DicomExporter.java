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

package com.vis.core.ui.dialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.task.Task;
import com.vis.core.task.TaskContext;
import com.vis.core.task.TaskManager;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomUtilities;
import com.vis.imageio.Decompressor;

/**
 * 
 * @author tatsunidas t_kobayashi@vis-ionary.com
 *
 */
@SuppressWarnings("serial")
public class DicomExporter extends JFrame implements Task {
	
	private final ExecutorService executor;
	private final BlockingQueue<Future<?>> tasks = new LinkedBlockingQueue<>();
	private final ReentrantLock lock = new ReentrantLock();
    
    DatabaseHandler db = DatabaseHandler.getInstance();
	
	private ArrayList<String[]> dcmFilesUIDs;
	private JFileChooser jfc;
	private File dest;
	final int doExport;
	private ExportOptionPanel eop;
	final String approveButtonText = "Export";
	private boolean flatOutput = false;// default
	private boolean decompress = false;// default
	private boolean withViewer = false;

	final int taskId;
	TaskContext con = null;
	
	protected boolean sleepScheduled = false;
	protected boolean suspended = false;//pause
	boolean isCompleted = false;
	
	boolean showExportResult = true;
	
	/*
	 * for burn cd.
	 */
	public DicomExporter() {
		doExport = -1;
		executor = null;
		this.taskId = -1;
	}
	
	/**
	 * Open dialog
	 * @param targetNodes
	 */
	public DicomExporter(ArrayList<DICOMNode> targetNodes) {
		this.executor = Executors.newFixedThreadPool(Utils.availableProcessors());
		taskId = TaskManager.getInstance().addTask(this);
		doExport = showDialog();//choose settings
		if(doExport != JFileChooser.APPROVE_OPTION) {
			TaskManager.getInstance().removeTask(taskId);
			return;
		}
		dest = jfc.getSelectedFile();
		if(dest == null) {
			TaskManager.getInstance().removeTask(taskId);
			throw new IllegalArgumentException("Please input output destination folder.");
		}
		if (targetNodes == null || targetNodes.size() < 1) {
			TaskManager.getInstance().removeTask(taskId);
			Log.logger.info("Please select node from TreeTable.");
			PopUpMessage.showDialog(MainScreen.getInstance(), "Export files not selected.", "Please select files to export.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String[] selected = eop.getSelectedButtonsName();
		flatOutput = selected[0].equals("flat");//hierarchical or flat
		decompress = selected[1].equals("decompress");//decompress or asis
		withViewer = eop.withViewer();
		//UIDs of each dicom instance.
		this.dcmFilesUIDs = WindowManager.getMainScreen().getLocalTreeTable().createNoDuplicateImageList(targetNodes);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	}
	
	/**
	 * Export dcm file from DB to any destination.
	 * 
	 * @param flatOutput
	 * @param decompress
	 * @param selectedDir
	 * @param exportSet
	 */
	public void exportDICOM(
			File selectedDir/*dest dir*/,
			ArrayList<String[]> exportSet,
			boolean flatOutput, 
			boolean decompress, 
			boolean withViewer) {
		
		if(selectedDir == null || !selectedDir.exists()) {
			Log.logger.warning("Export destination folder is not exists.");
			return;
		}
		
		if(exportSet == null || exportSet.size()==0) {
			Log.logger.info("Export target images not selected.");
			return;
		}
		
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			Log.logger.info("DatabaseHandler not found. Cannot export images.");;
		}
		
		for(String[] uidInfoSet: exportSet) {
			String patID = uidInfoSet[0];
			String studyIUID = uidInfoSet[1];
			String seriesIUID = uidInfoSet[2];
			String sopIUID = uidInfoSet[3];
			if (!db.checkImageRecordExists(patID, studyIUID, seriesIUID, sopIUID)) {
				continue;
			}
			if (patID == null || patID.equals("") || patID.contentEquals(" ")) {
				patID = "NULL-PatientID";
			}
			String studyDesc = db.getValueFromStudy("StudyDescription", patID, studyIUID);
			if (studyDesc == null || studyDesc.equals("") || studyDesc.equals(" ")) {
//				studyDesc = "no-studydesc";//to avoid duplication, use UID.
				studyDesc = studyIUID;
				Log.logger.fine("studyDesc is null, uid used instead.");
			}
			String seriesDesc = db.getValueFromSeries("SeriesDescription", patID, studyIUID, seriesIUID);
			if (seriesDesc == null || seriesDesc.equals("") || seriesDesc.equals(" ")) {
				seriesDesc = seriesIUID;
				Log.logger.fine("seriesDesc is null, use uid instead.");
			}
			int instNo = db.getInstanceNo(studyIUID, seriesIUID, sopIUID);
			String destSeries= selectedDir.getAbsolutePath() + File.separator + "DICOM" + File.separator + patID + File.separator
					+ studyDesc + File.separator + seriesDesc;
			File destSeriesFolder = new File(destSeries);
			if (!destSeriesFolder.exists()) {
				destSeriesFolder.mkdirs();
			}
			String dest = destSeriesFolder.getAbsolutePath() + File.separator + instNo + ".dcm";
			// copy to temp
			String dcmPath = db.getValueFromImage("FileStoreUrl", patID, studyIUID, seriesIUID, sopIUID);
			File from = new File(dcmPath);
			File to = new File(dest);
			if (!from.exists()) {
				Log.logger.severe(dcmPath + " is missing in graphy database...");
				continue;
			}
			synchronized (to) {
				try {
					if(!decompress) {
						Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
					}else{
						DICOMBackend backend = DICOMBackend.getCurrent();
						Decompressor.newInstance(backend).decompress(from, to);
					}
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
			}
		}

		/*
		 * how to embed weasis in cdr https://groups.google.com/g/dcm4che/c/9HIr2lyR9Os
		 * 
		 * DICOM - STUDIES 
		 * DICOMDIR 
		 * finally add weasis-portable files
		 * 
		 */
		// finally add viewer and dicomdir
		if(withViewer) {
			try {
				/*
				 * DICOMDIR was needed for viewer to load images when starting-up.
				 */
				DicomUtilities.attachDICOMDIRTo(selectedDir.getAbsolutePath());
				Utils.copyDirectory(ConfigInfo.WEASIS.toString(), selectedDir.getAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		done();
	}
	
	public void setShowExportResult(boolean showResult) {
		this.showExportResult = showResult;
	}
	
	/**
	 * Export dcm file from DB to any destination.
	 */
	private void moveFile(File from, File to) {
		try {
			while (isSuspended()) {
				Thread.sleep(100); // 一時停止中は定期的にチェック
			}
			if (!decompress) {
				Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} else {
				DICOMBackend backend = DICOMBackend.getCurrent();
				Decompressor.newInstance(backend).decompress(from, to);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private ArrayList<String[]> validateExport(ArrayList<String[]> allDcmFilesUIDs){
		
		ArrayList<String[]> candidate = new ArrayList<>();//no duplicate
		ArrayList<String> patIDs = new ArrayList<String>();
		ArrayList<String> studyIUIDs = new ArrayList<String>();
		ArrayList<String> seriesIUIDs = new ArrayList<String>();
		ArrayList<String> sopIUIDs = new ArrayList<String>();
		for (String[] info : dcmFilesUIDs) {
			patIDs.add(info[0]);
			studyIUIDs.add(info[1]);
			seriesIUIDs.add(info[2]);
			sopIUIDs.add(info[3]);
		}
		patIDs = new ArrayList<>(new HashSet<>(patIDs));
		studyIUIDs = new ArrayList<>(new HashSet<>(studyIUIDs));
		seriesIUIDs = new ArrayList<>(new HashSet<>(seriesIUIDs));
		sopIUIDs = new ArrayList<>(new HashSet<>(sopIUIDs));

		ArrayList<String> missingFiles = new ArrayList<>();

		for (String patID : patIDs) {
			for (String studyIUID : studyIUIDs) {
				for (String seriesIUID : seriesIUIDs) {
					for (String sopIUID : sopIUIDs) {
						/*
						 * here, drop instance which does not have accurate UID combination. 
						 */
						if (!db.checkImageRecordExists(patID, studyIUID, seriesIUID, sopIUID)) {
							continue;
						}
						candidate.add(new String[] {patID, studyIUID, seriesIUID, sopIUID});
					}
				}
			} // study loop
		} // patient loop

		if (!missingFiles.isEmpty()) {
			String msg = "Missing files found, cannot completed exporting all files.\n";
			JOptionPane.showConfirmDialog(MainScreen.getInstance(), msg);
			for(String missing : missingFiles) {
				msg += missing + "\n";
			}
			Log.logger.severe(msg);
		}
		return candidate;
	}
	
	private void showExportResult() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// show result
				JOptionPane.showOptionDialog(WindowManager.getMainScreen(), "Export was done.",
						"Complete -Export images-", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
						new String[] { "OK" }, "default");
			}
		});
	}
	
	@Override
	public TaskContext getContext() {
		return con;
	}

	
	public int showDialog() {
		jfc = new JFileChooser();
		jfc.setDialogTitle("Export Option Dialog");
		jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		String userDir = new File(System.getProperty("user.home")).getAbsolutePath();
		jfc.setCurrentDirectory(new File(userDir+File.separator+"Export"));
		eop = new ExportOptionPanel();
		jfc.setAccessory(eop);
		jfc.setApproveButtonText(approveButtonText);
		jfc.setApproveButtonToolTipText("Export to selected folder");
		return jfc.showOpenDialog(this);
	}
	
	public int getTaskId() {
		return taskId;
	}
	
	public void monitorTasks() {
		// monitor by another thread.
		new Thread(() -> {
			while (!isCompleted() && !executor.isShutdown()) {
				try {
					Thread.sleep(1000); // 1秒ごとに監視
					tasks.removeIf(Future<?>::isDone);
					System.out.println("Remaining tasks: " + tasks.size());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			TaskManager tm = TaskManager.getInstance();
			tm.removeCompletedTasks();
			System.out.println("Task completed or cancelled.");
		}).start();
	}
	
	@Override
	public void start() {
		ArrayList<String[]> candidate = validateExport(dcmFilesUIDs);
		if(candidate == null || candidate.size() == 0) {
			return;
		}
		MainScreen ms = MainScreen.getInstance();
		ms.startProgressBar(candidate.size());
		/*
		 * When use descriptions for folder name,
		 * it may cause folder name duplication error.
		 * Here, use UIDs for folders.
		 * 
		 * [NO USE]
		 * String studyDesc = db.getParticularInfoFromStudy("StudyDescription", patID, studyIUID);
		 * String seriesDesc = db.getParticularInfoFromSeries("SeriesDescription", patID, studyIUID,seriesIUID);
		 */
		int itr = 0;
		if (!flatOutput) {
			/*
			 * export files with hierarchical, 
			 * If hierarchical DICOMDIR and withViewer
			 */
			for (String[] UIDs : candidate) {
				String patID = UIDs[0];
				String studyIUID = UIDs[1];
				String seriesIUID = UIDs[2];
				String sopIUID = UIDs[3];
				if (patID == null || patID.equals("") || patID.contentEquals(" "/* full-width */)) {
					patID = "NULL-PatientID";
				}
				int instNo = db.getInstanceNo(studyIUID, seriesIUID, sopIUID);
				if (instNo == 0) {
					Log.logger.warning(
							"DICOMExport: this instance's instance number is zero. Such case is never occur as usual...");
				}
				String destParent = dest.getAbsolutePath() + File.separator + "DICOM" +File.separator + patID + File.separator + studyIUID
						+ File.separator + seriesIUID;
				File destDirs = new File(destParent);
				if (!destDirs.exists()) {
					destDirs.mkdirs();
				}
				String dest = destParent + File.separator + instNo + ".dcm";
				String dcmPath = db.getValueFromImage("FileStoreUrl", patID, studyIUID, seriesIUID, sopIUID);
				File from = new File(dcmPath);
				File to = new File(dest);
				Future<?> future = executor.submit(() -> moveFile(from, to));
				tasks.add(future);
				final int counter = itr++;
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						ms.setProgressValue(counter);
					}
				});
			}
		} else {
			ArrayList<String> patIDs = new ArrayList<String>();
			ArrayList<String> studyIUIDs = new ArrayList<String>();
			ArrayList<String> seriesIUIDs = new ArrayList<String>();
			ArrayList<String> sopIUIDs = new ArrayList<String>();
			for (String[] info : candidate) {
				patIDs.add(info[0]);
				studyIUIDs.add(info[1]);
				seriesIUIDs.add(info[2]);
				sopIUIDs.add(info[3]);
			}
			patIDs = new ArrayList<>(new HashSet<>(patIDs));
			studyIUIDs = new ArrayList<>(new HashSet<>(studyIUIDs));
			seriesIUIDs = new ArrayList<>(new HashSet<>(seriesIUIDs));
			sopIUIDs = new ArrayList<>(new HashSet<>(sopIUIDs));

			for (String patID : patIDs) {
				for (String studyIUID : studyIUIDs) {
					int instanceCount = 1;// for flat saving, reset counter
					for (String seriesIUID : seriesIUIDs) {
						for (String sopIUID : sopIUIDs) {
							/*
							 * here, drop instance which does not have accurate UID combination.
							 */
							if (!db.checkImageRecordExists(patID, studyIUID, seriesIUID, sopIUID)) {
								continue;
							}
							if (patID == null || patID.equals("") || patID.contentEquals(" "/* full-width */)) {
								patID = "NULL-PatientID";
							}

							int instNo = db.getInstanceNo(studyIUID, seriesIUID, sopIUID);
							if (instNo == 0) {
								Log.logger.warning(
										"DICOMExport: this instance's instance number is zero. Such case is never occur as usual...");
							}

							String destParent = dest.getAbsolutePath() + File.separator + "DICOM" +File.separator + patID + File.separator
									+ studyIUID;
							File destDirs = new File(destParent);
							if (!destDirs.exists()) {
								destDirs.mkdirs();
							}
							String dest = destParent + File.separator + instanceCount + ".dcm";
							String dcmPath = db.getValueFromImage("FileStoreUrl", patID, studyIUID, seriesIUID,
									sopIUID);
							File from = new File(dcmPath);
							File to = new File(dest);
							Future<?> future = executor.submit(() -> moveFile(from, to));
							tasks.add(future);
							instanceCount++;//in study level
							final int counter = itr++;//all instance
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									ms.setProgressValue(counter);
								}
							});
						}
					}
				} // study loop
			} // patient loop
		}

		// finally, attach viewer and create DICOMDIR
		if(withViewer) {
			try {
				/*
				 * DICOMDIR was needed for viewer to load images when starting-up.
				 */
				DicomUtilities.attachDICOMDIRTo(dest.getAbsolutePath());
				Utils.copyDirectory(ConfigInfo.WEASIS.toString(), dest.getAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		done();
	}

	@Override
	public void done() {
		if(jfc != null) {
			jfc = null;
		}
		if(showExportResult) {
			showExportResult();
		}
		if(executor != null && !executor.isShutdown()) {
			executor.shutdown();
		}
		isCompleted = tasks.stream().allMatch(Future::isDone);
		dispose();
	}
	
	public boolean isCompleted() {
		return isCompleted;
	}

	@Override
	public void setSleepScheduled(boolean doSleep) {
		sleepScheduled = doSleep;
	}

	@Override
	public boolean isSleepScheduled() {
		return sleepScheduled;
	}

	/**
	 * suspend and resume.
	 */
	@Override
	public void setSuspended(boolean suspend) {
		lock.lock();
		try {
			suspended = suspend;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean isSuspended() {
		return suspended;
	}

	@Override
	public void setStopped(boolean stop) {
		if (stop) {
			for (Future<?> task : tasks) {
				task.cancel(true);
			}
			executor.shutdownNow();
		}
	}

	@Override
	public boolean isStopped() {
		return false;
	}
}
