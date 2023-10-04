package com.vis.core.ui.function;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.AnimatingSheet;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.ui.main.dcmtreetable.LocalDBStateCellRendererableEditor;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.Tag;
import com.vis.dicom.VR;

import java.io.File;
import java.util.List;

import javax.swing.JOptionPane;
import java.util.ArrayList;

import javax.swing.SwingUtilities;

/**
 * @author tatsunidas
 */
public class DicomImporter implements Runnable {

	boolean saveAsLink = false;//TODO :: this specified from chooser GUI
	boolean ignorePrivate = false;// TODO :: this specified from chooser GUI
	private ArrayList<String> candidateList;// Dicom Files exclude dicomdir

//	boolean isVideo = false;//TODO
	
	int total = -1;
	
	// Threading
	Thread thisThread;
	boolean suspend = false;
	protected boolean stopped;// same as cancel
	protected boolean sleepScheduled;
	protected boolean suspended;
	public final static int SLEEP_TIME = 1 * 50;

	public DicomImporter(ArrayList<String> candidateList, boolean saveAsLink, boolean ignorePrivate) {
		this.saveAsLink = saveAsLink;
		this.ignorePrivate = ignorePrivate;
		this.candidateList = candidateList;
		total = candidateList.size();
		// create new thread and add to main importer thread group.
		thisThread = new Thread(this);
		stopped = false;
		sleepScheduled = true;//useful for debug
		suspended = false;
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
				
				new AnimatingSheet(getCandidateFilesList().size() + " "
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
	
	private void performImport() {
		int count = 0;
		while (!(count == total) && !(isStopped())) {
			if(!MainScreen.importing) {
				MainScreen.importing = true;
				DICOMTreeTable treeTable = WindowManager.getMainScreen().getLocalTreeTable();
				treeTable.getTableHeader().setEnabled(false);//stop table sort feature. can not activate??
			}
			if(total == -1) {
				setStopped(true);
				break;
			}
			String candidate = candidateList.get(count);
			/* To get particular study row to show progress bar */
			String willImportPatID = DicomUtilities.getPatientID(candidate);
			String willImportSUID = DicomUtilities.getStudyInstanceUID(candidate);
			if (count == 0) {
				// should do nothing, 
				//first loop wait for write new record in db.
			} else {// with update progressbar
				if (sleepScheduled) {
					try {
						Thread.sleep(SLEEP_TIME);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				/*
				 * this synchronize state block is looks good to prevent multiple study import.
				 * need more test.
				 */
				MainScreen mainSc = WindowManager.getMainScreen();
				synchronized(mainSc.getLocalTreeTable().getTree()) {
					int currentRow = mainSc.getLocalTreeTable().getParticularStudyRow(willImportPatID,willImportSUID);
					int currentCol = mainSc.getLocalTreeTable().getArchivedColumnPosition();
					if(Utils.isDebug) {
						Log.logger.info("ImportingThread:ContextProgressAt:"+currentRow+" "+currentCol);
					}
					updateProgress(mainSc.getLocalTreeTable().getStateCellEditorAtArchiveColumn(currentCol), willImportSUID, currentRow,
							currentCol, count);
					mainSc.getLocalTreeTable().revalidate();
				}
			}
//			DicomInputStream dis = null;
			try {
//				dis = new DicomInputStream(new File(candidate));
				DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
				reader.read(new File(candidate).getAbsolutePath());
				DicomObject data = reader.getCore();
				DatabaseHandler db = DatabaseHandler.getInstance();
				if (data != null && avoidPatientIDNUll(data)) {
					if (saveAsLink) {
						db.setSaveAsLinkState(saveAsLink);
						synchronized(this){
							db.writeDatasetInfo(data, candidate);
						}
					} else {// store image file to DB
						synchronized(this){
							List<String> cmd = new ArrayList<String>();
							String listenerInfo[] = db.getListenerDetails();
							cmd.add("-c");
							cmd.add(listenerInfo[0] + "@" + listenerInfo[1] + ":"+ listenerInfo[2]);
							cmd.add(candidate);
							String args[] = new String[cmd.size()];
							args = cmd.toArray(args);
							com.vis.dicom.dimse.StoreSCU.main(args);// then do run, and writeDB in DcmQRSCP
						}
					}
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
			} catch (Exception e) {
				Log.logger.severe("DicomImporter::performImport():Unable to import file. Stoped import...\n"+e.getMessage());
				return;
			} finally {
				WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
				count++;
			}
		} // while loop end
		if(!isStopped()) {
			doneImport();// TODO
		}
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
		performImport();
	}

	protected void updateProgress(LocalDBStateCellRendererableEditor stateCell, String suid, int row, int col,
			int progress) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				stateCell.setProgressAt(suid, row, col, progress);
//				mediator.getMainScreen().getTreeTable().repaint();
			}
		});
	}

	protected void doneImport() {
		showImportResult();
		MainScreen.importing = false;
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

	public void startImport() {
		thisThread.start();
	}

	public synchronized void resumeImport() {
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
	}

	public synchronized boolean isStopped() {
		return stopped;
	}

	public void stopImport() {
		thisThread.interrupt();
	}

	/*
	 * TODO comment out 20230901
	 * 
	 * all time do single thread,
	 * AllAndWait never call...maybe. tatsu
	 */
	public static void cancelAllAndWait() {
//		int count = ApplicationContext.importerThreadGroup.activeCount();
//		Thread[] threads = new Thread[count];
//		count = ApplicationContext.importerThreadGroup.enumerate(threads);
//		ApplicationContext.importerThreadGroup.interrupt();
//		for (int i = 0; i < count; i++) {
//			try {
//				threads[i].join();
//			} catch (InterruptedException ie) {
//			}
//			;
//		}
	}
}
