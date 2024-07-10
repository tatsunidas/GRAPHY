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
import com.vis.core.task.context.ImportingStateContext;
import com.vis.core.ui.main.AnimatingSheet;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.Tag;
import com.vis.dicom.VR;
import com.vis.dicom.dimse.DimseUtilities;

import java.io.File;

import javax.swing.JOptionPane;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.SwingUtilities;

/**
 * Import dicom files study by study.
 * @author tatsunidas
 */
public class DicomImporter implements Task {

	boolean saveAsLink = false;//TODO :: this specified from chooser GUI
	boolean ignorePrivate = false;// TODO :: this specified from chooser GUI
	private ArrayList<String> candidateList;// Dicom Files exclude dicomdir
	TaskContext con;

//	boolean isVideo = false;//TODO
	
	int total = -1;
	
	Thread thisThread;
	boolean suspend = false;
	protected boolean stopped;// same as cancel
	protected boolean sleepScheduled;
	protected boolean suspended;
	
	public final static int SLEEP_TIME = 50;

	public DicomImporter(ArrayList<String> candidateFileList, String studyUID, boolean saveAsLink, boolean ignorePrivate) {
		this.saveAsLink = saveAsLink;
		this.ignorePrivate = ignorePrivate;
		this.candidateList = candidateFileList;
		total = candidateList.size();
		// create new thread and add to main importer thread group.
		thisThread = new Thread(this);
		stopped = false;
		sleepScheduled = true;//useful for debug
		suspended = false;
		con = new ImportingStateContext(studyUID, this);
		con.setThreadId(thisThread.getId());
		setContext(con);
		TaskManager tm = TaskManager.getInstance();
		tm.addTask(thisThread.getId(), con);
	}
	
	public void setSaveAsLink(boolean isLink) {
		this.saveAsLink = isLink;
	}
	
	void setIgnorePrivateAttr(boolean ignorePrivate) {
		this.ignorePrivate = ignorePrivate;
	}

	/*
	 * obtain bool value to setting save as link or not.
	 * for drag and drop
	 */
	public int isLink() {
//		int select = JOptionPane.showOptionDialog(ApplicationContext.getInstance().getMainScreen(),
//				ApplicationContext.currentBundle.getString("MainScreen.importConfirmation.text"),
//				ApplicationContext.currentBundle.getString("MainScreen.importConfirmation.title.text"),
//				JOptionPane.OK_CANCEL_OPTION, JOptionPane.YES_NO_CANCEL_OPTION, null,
//				new String[] { ApplicationContext.currentBundle.getString("MainScreen.import.copy.text"),
//						ApplicationContext.currentBundle.getString("MainScreen.import.link.text") },
//				"default");
		
		int select = JOptionPane.showOptionDialog(WindowManager.getMainScreen(),
				"Dicom Import",
				"Execute Import Dicoms ?",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.YES_NO_CANCEL_OPTION, null,
				new String[] { "Copy","Link" },	"default");
		
		switch (select) {
		case 0:// copy
			saveAsLink = false;
			break;
		case 1:// link
			saveAsLink = true;
			break;
		default:// others, -1
			System.out.println("import canceled");
			break;
		}
		return select;
	}

	private void showImportResult() {
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// show import result
				/* when run multi studies import only show last import dialog */
				
//				String msg = ApplicationContext.currentBundle.getString("MainScreen.import.filesCopied.text")
				String msg = "imported !";
				
				new AnimatingSheet(con.currentIndex()+1+"/"+totalSize() + " "
						+ msg, JOptionPane.INFORMATION_MESSAGE);

				/*
				 * No AnimatingSheet methods
				 */
				//				JOptionPane.showOptionDialog(mediator.getMainScreen(),
//						getCandidateFilesList().size() + " "
//								+ ApplicationContext.currentBundle.getString("MainScreen.import.filesCopied.text"),
//						ApplicationContext.currentBundle.getString("MainScreen.importMenuItem.text"),
//						JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
//						new String[] { ApplicationContext.currentBundle.getString("OkButtons.text") }, "default");
			}
		});
	}
	
	private void perform() {
		int count = 0;
		while (count != total && !isStopped()) {
			if(total <= 0) {
				setStopped(true);
				break;
			}
			synchronized (this) {
				if (isSuspended()) {
					try {
						this.wait();
						setSuspended(false);
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
				reader.read(new File(candidate).getAbsolutePath());
				DicomObject data = reader.getCore();
				DatabaseHandler db = DatabaseHandler.getInstance();
				db.setSaveAsLinkState(saveAsLink);
				if (data != null && avoidPatientIDNUll(data)) {
					
					if (saveAsLink) {
						synchronized(this){
							db.writeDatasetInfo(data, candidate);
						}
					} else {// store image file to DB
						synchronized(this){
							DimseUtilities.store(candidate, false/*deleteAfterStored*/);
						}
					}
				}
				HashMap<String, Object> updation = new HashMap<>();
				updation.put("CurrentIndex", count++);
				//task context always update, even if failed import.
				con.updateState(updation);
				//update treetable
				WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
			} catch (Exception e) {
				Log.logger.severe("DicomImporter::performImport():Unable to import file. Stoped import...\n"+e.getMessage());
				return;
			}
		} // while loop end
		done();
	}

	private boolean avoidPatientIDNUll(DicomObject data) {
		String patID = data.getString(Tag.Patient​ID);
		if(patID == null || patID.equals("")) {
			data.setString(Tag.Patient​ID, VR.LO, "null");
		}
		return true;
	}

	@Override
	public void run() {
		perform();
	}

	public void done() {
		TaskManager tm = TaskManager.getInstance();
		tm.removeAndCleanUpTasks(con.getThreadId());
		showImportResult();
		DICOMTreeTable treeTable = WindowManager.getMainScreen().getLocalTreeTable();
		treeTable.getTableHeader().setEnabled(true);
		WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
	}

	public Thread getThread() {
		return thisThread;
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
		this.notify();
	}

	public synchronized void setSleepScheduled(boolean doSleep) {
		sleepScheduled = doSleep;
	}

	public synchronized boolean isSleepScheduled() {
		return sleepScheduled;
	}

	public synchronized void setSuspended(boolean suspend) {
		suspended = suspend;
	}

	public synchronized boolean isSuspended() {
		return suspended;
	}

	public synchronized void setStopped(boolean stop) {
		stopped = stop;
		if(stopped) {
			done();
		}
	}

	public synchronized boolean isStopped() {
		return stopped;
	}

	public void terminate() {
		thisThread.interrupt();
		DICOMTreeTable treeTable = WindowManager.getMainScreen().getLocalTreeTable();
		treeTable.getTableHeader().setEnabled(true);
		if (Utils.isDebug) {
			Log.logger.info("import interupted.");
		}
	}

	@Override
	public void setContext(TaskContext con) {
		this.con = con;
	}

	@Override
	public TaskContext getContext() {
		return con;
	}
}
