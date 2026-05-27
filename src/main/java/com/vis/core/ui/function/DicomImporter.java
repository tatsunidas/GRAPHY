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
package com.vis.core.ui.function;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.task.Task;
import com.vis.core.task.TaskContext;
import com.vis.core.task.TaskManager;
import com.vis.core.task.TaskType;
import com.vis.core.task.context.ImportingStateContext;
import com.vis.core.ui.main.AnimatingSheet;
import com.vis.core.ui.main.TabDock;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.Tag;
import com.vis.dicom.dimse.DimseUtilities;

import java.io.File;

import javax.swing.JOptionPane;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.SwingUtilities;

/**
 * Import dicom files study by study.
 * 
 * DicomImporter importer = new DicomImporter(candidateList, null, willImportStudyUID);
 * TaskManager tm = TaskManager.getInstance();
 * tm.startTask(importer.getTaskId());
 * 
 * TODO
 * - update multi-threading, see, DicomExporter.class
 * 
 * @author tatsunidas
 */
public class DicomImporter implements Task, Runnable {

	boolean ignorePrivate = false;
	private ArrayList<String> candidateList;// Dicom Files exclude dicomdir
	private HashMap<Integer,Object> willEditTo;//data will be edited this patient information.
	TaskContext con = null;

	int total = -1;
	
	Thread thisThread;
	final int taskId;
	boolean suspend = false;
	protected boolean stopped;// same as cancel
	protected boolean sleepScheduled;
	protected boolean suspended;
	boolean isCompleted = false;
	final String studyUID;
	
	public final static int SLEEP_TIME = 1000;

	/**
	 * Import dicom files AS-IS.
	 * @param candidateFileList
	 * @param studyUID
	 * @param saveAsLink
	 * @param ignorePrivate
	 */
	public DicomImporter(ArrayList<String> candidate, String studyUID) {
		this(candidate, null, studyUID);
	}
	
	public DicomImporter(ArrayList<String> candidate, HashMap<Integer,Object> info, String studyUID) {
		this.candidateList = candidate;
		total = candidateList.size();
		this.willEditTo = info;
		stopped = false;
		sleepScheduled = false;//Utils.isDebug;//useful for debug
		suspended = false;
		this.con = null;
		this.studyUID = studyUID;
		thisThread = new Thread(this);
		//add to task manager
		TaskManager tm = TaskManager.getInstance();
		taskId = tm.addTask(this);
	}
	
	private void showImportResult() {
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// show import result
//				String msg = ApplicationContext.currentBundle.getString("MainScreen.import.filesCopied.text")
				String msg = "imported !";
				
				new AnimatingSheet(con.currentIndex()+1+"/"+totalSize() + " "
						+ msg, JOptionPane.INFORMATION_MESSAGE);
			}
		});
	}
	
	private void perform() {
		int count = 0;
		TreeTableDockManager tabDockMng = WindowManager.getMainScreen().getTreeTableDockManager();
		TabDock homeDock = tabDockMng.getHomeDock();
		DICOMTreeTable treeTable = homeDock.getDICOMTreeTable();
		while (count != total && !isStopped()) {
			if(total <= 0) {
				setStopped(true);
				break;
			}
			synchronized (this) {
				if (isSuspended()) {
					try {
						this.wait();
					} catch (InterruptedException ie) {
						setStopped(true);
						break;
					}
				}
			}
			if (Thread.interrupted()) {
				setStopped(true);
				break;
			}
			if (sleepScheduled) {
				try {
					Thread.sleep(SLEEP_TIME);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			try {
				String candidate = candidateList.get(count);
				DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
				reader.read(new File(candidate).getAbsolutePath(),false);
				DicomObject data = reader.getHeader();
				String seriesUID = data.getString(Tag.SeriesInstanceUID);
				DatabaseHandler db = DatabaseHandler.getInstance();
				int seriesCountPrev = db.getNumOfSeries(data.getString(Tag.PatientID), data.getString(Tag.StudyInstanceUID));
				db.setSaveAsLinkState(false);//never use saveAsLink
				if (data != null) {
					synchronized(this){
						if(willEditTo == null) {
							DimseUtilities.store(candidate, false/*deleteAfterStored*/);
						}else {
							DimseUtilities.editBeforeSend(new File(candidate), willEditTo);
						}
					}
				}
				if(count ==0) {
					HashMap<String, Object> update_con = new HashMap<>();
					update_con.put(TaskContext.TASK_TYPE, TaskType.TypeImport);
					update_con.put(TaskContext.THREAD_ID, thisThread.getId());
					update_con.put(TaskContext.TASK_ID, taskId);
					update_con.put(TaskContext.SIZE, candidateList.size());
					update_con.put(TaskContext.CURRENT_IND, count);
					con = new ImportingStateContext(studyUID, seriesUID, update_con);
				}else {
					HashMap<String, Object> updation = new HashMap<>();
					updation.put(TaskContext.CURRENT_IND, count);
					//task context always update, even if failed import.
					con.updateState(updation);
				}
				//update count
				count ++;
				
				SwingUtilities.invokeLater(() -> {
					int seriesCountPost = db.getNumOfSeries(data.getString(Tag.PatientID), data.getString(Tag.StudyInstanceUID));
					//update treetable if new series imported ( in other words, if series count cout-up 0 to 1, this is new study)
					if(seriesCountPrev < seriesCountPost) {
						WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
					}
					treeTable.repaint();
				});
			} catch (Exception e) {
				Log.logger.severe("DicomImporter::perform():Unable to import file. Stoped import...\n"+e.getMessage());
				return;
			}
		} // while loop end
		done();
	}

	/**
	 * run from thisThread.start().
	 */
	@Override
	public void run() {
		perform();
	}

	public void done() {
		setStopped(true);//fail safe
		isCompleted = true;//set above to task remove.
		TaskManager tm = TaskManager.getInstance();
		tm.removeCompletedTasks();
		// インポートがすべて完了したタイミングで、ツリーモデル全体を更新する
		SwingUtilities.invokeLater(() -> {
			showImportResult();//show first
			WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
			//clear from task manager
			DICOMTreeTable treeTable = WindowManager.getMainScreen().getLocalTreeTable();
			treeTable.getTableHeader().setEnabled(true);
		});
	}

	private ArrayList<String> getCandidateFilesList() {
		return this.candidateList;
	}
	
	public int totalSize() {
		return getCandidateFilesList().size();
	}

	public void start() {
		DICOMTreeTable treeTable = WindowManager.getMainScreen().getLocalTreeTable();
		treeTable.getTableHeader().setEnabled(false);
		thisThread.start();
	}

	public synchronized void resume() {
//		thisThread.notify();//DO NOT DO THIS
		this.notify();
	}

	public synchronized void setSleepScheduled(boolean doSleep) {
		sleepScheduled = doSleep;
	}

	public synchronized boolean isSleepScheduled() {
		return sleepScheduled;
	}

	public synchronized void setSuspended(boolean suspend) {
		if(suspended && suspend) {
			//do nothing
		}else if(!suspended && suspend) {
			suspended = true;
		}else if(suspended && !suspend) {
			suspended = false;
			resume();
		}else if(!suspended && !suspend) {
			//do nothing
		}
	}

	public synchronized boolean isSuspended() {
		return suspended;
	}

	public synchronized void setStopped(boolean stop) {
		stopped = stop;
	}

	public synchronized boolean isStopped() {
		return stopped;
	}
	
	/**
	 * set true in done()
	 */
	public boolean isCompleted() {
		return isCompleted;
	}
	
	public void monitorTasks() {
		new Thread(() -> {
			while (!isCompleted() && !isStopped()) {
				try {
					Thread.sleep(500); // monitor each 0.5 sec
					if(con == null) {
						//when starting, still con is null.
						continue;
					}
					int currentInd = con.currentIndex();
					int total = con.totalSize();
					System.out.println("Remaining tasks: " + (total - (currentInd + 1)));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			System.out.println("Task completed or cancelled.");
			TaskManager tm = TaskManager.getInstance();
			tm.removeCompletedTasks();
		}).start();
	}
	
	public int getTaskId() {
		return taskId;
	}
	
	@Override
	public TaskContext getContext() {
		return con;
	}
}
