package com.vis.core.ui.dialog;

import java.awt.BorderLayout;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.JComboBox;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.AnimatingSheet;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.dimse.StoreSCU;


public class DicomPostman extends JDialog implements Runnable{

	/**
	 * ローカルツリーから送信可能なノードを取得
	 * 送信対象リストをダンプ
	 * 送信
	 * 進捗バー更新
	 * 
	 * TODO
	 * QRテーブルから別のサーバーへのMOVE？
	 */
	private static final long serialVersionUID = -631636213313795170L;
	private ArrayList<String> candidateList;// Dicom Files
	ArrayList<String[]> exportSet;//candidate UIDs and path.
	private DatabaseHandler db;
	ArrayList<DicomCommunicationNode> servers=null;
	String destSeverName = "";
	// Threading
	Thread thisThread;
	protected boolean stopped = false;// same as cancel
	protected boolean sleepScheduled = false;//debug
	public final static int SLEEP_TIME = 3000;
	protected boolean suspended = false;
	private JComboBox<String> comboBox;
	private JProgressBar progressBar;
	private JButton btnSuspend;
	private JButton btnContinue;
	private JButton btnCancel;
	private JButton btnSend;
	
	public DicomPostman(ArrayList<DICOMNode> selectedNodes) {
		if(selectedNodes == null || selectedNodes.size() < 1) {
			JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "You should select data from home treetable.");
			return;
		}
		db = DatabaseHandler.getInstance();
		//送信可能な(server)があるか。無い場合は起動しない。
		ArrayList<HashMap<String,Object>> serverMaterials = db.getCommunicationServerList();
		if(serverMaterials == null || serverMaterials.isEmpty()) {
			JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Can not detect communicationable remote servers.");
			return;
		}
		servers = new ArrayList<DicomCommunicationNode>();
		for(HashMap<String,Object> materials:serverMaterials) {
			DicomCommunicationNode server = new DicomCommunicationNode(materials);
			servers.add(server);
		}
		setCandidateFilesList(selectedNodes);
		//GUIセットアップ
		setUpGui();
		
		stopped = false;
		sleepScheduled = true;// useful for debug
		suspended = false;
		setVisible(true);
	}
	
	private void setUpGui() {
		setTitle("Postman");
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		
		progressBar = new JProgressBar();
		mainPanel.add(progressBar, BorderLayout.SOUTH);
		
		JPanel panel = new JPanel();
		mainPanel.add(panel, BorderLayout.CENTER);
		panel.setLayout(new BorderLayout(0, 0));
		
		JPanel panel_1 = new JPanel();
		panel.add(panel_1, BorderLayout.SOUTH);
		
		btnSend = new JButton("send");
//		btnSend.setBackground(SystemColor.info);
		btnSend.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				btnSend.setEnabled(false);
				btnSuspend.setEnabled(true);
				btnCancel.setEnabled(true);
				comboBox.setEnabled(false);
				startSending();
			}
		});
		panel_1.add(btnSend);
		
		btnSuspend = new JButton("suspend");
		btnSuspend.setEnabled(false);
		btnSuspend.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setSuspended(true);
				progressBar.setIndeterminate(true);
				btnContinue.setEnabled(true);
			}
		});
		panel_1.add(btnSuspend);
		
		btnContinue = new JButton("continue");
		btnContinue.setEnabled(false);
		btnContinue.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setSuspended(false);
				progressBar.setIndeterminate(false);
				btnContinue.setEnabled(false);
				resumeSend();
			}
		});
		panel_1.add(btnContinue);
		
		btnCancel = new JButton("cancel");
		btnCancel.setEnabled(false);
		btnCancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setStopped(true);
			}
		});
		panel_1.add(btnCancel);
		
		String[] candidateInfo = new String[exportSet.size()];
		ArrayList<String> sorter = new ArrayList<String>();
		for(String[] info : exportSet) {
			//patID-name-studydate-modality
			String pname = db.getParticularInfoFromPatient("PatientName", info[0]);
			String studydate = db.getParticularInfoFromStudy("StudyDate", info[0], info[1]);
			String modality = db.getParticularInfoFromSeries("Modality", info[0], info[1], info[2]);
			String line = info[0]+"-"+pname+"-"+studydate+"-"+modality;
			sorter.add(line);
		}
		//sort
		Collections.sort(sorter);
		int row = 0;
		for(String line : sorter) {
			candidateInfo[row] = line;
			row++;
		}
		JList<String> list = new JList<>(candidateInfo);
		panel.add(list, BorderLayout.CENTER);
		
		JPanel panel_2 = new JPanel();
		panel.add(panel_2, BorderLayout.NORTH);
		
		JLabel lblSummary = new JLabel("Summary : ");
		panel_2.add(lblSummary);
		
		JLabel lblInfo = new JLabel("info");
		panel_2.add(lblInfo);
		constructSummaryInfo(lblInfo);
		
		getContentPane().add(mainPanel);
		
		String[] comboList = new String[servers.size()];
		int num = 0;
		for(DicomCommunicationNode svr:servers) {
			comboList[num] = svr.getNickname();
		}
		//avoid can not select on itemlistener
		if(comboList.length == 1) {
			setDestServerName(comboList[0]);
		}
		
		comboBox = new JComboBox<String>(comboList);
		comboBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					setDestServerName((String) comboBox.getModel().getSelectedItem());
				}
			}
		});
		mainPanel.add(comboBox, BorderLayout.NORTH);
		
		pack();
	}
	
	void setDestServerName(String nickname){
		this.destSeverName = nickname;
	}
	
	String getDestServerName(){
		return destSeverName;
	}

	private void constructSummaryInfo(JLabel lblInfo) {
		int totalStudy = 0;
		int totalSeries = 0;
		int totalInst = 0;
		//patIDs
		ArrayList<String> patIDs = new ArrayList<String>();
		for(String[] dcminfo:exportSet) {
			patIDs.add(dcminfo[0]);
		}
		List<String> listNoDupPat = new ArrayList<String>(new HashSet<>(patIDs));
		ArrayList<String> studyUIDs = new ArrayList<String>();
		ArrayList<String> seriesUIDs = new ArrayList<String>();
		ArrayList<String> sopUIDs = new ArrayList<String>();
		//pat
		for(String patID:listNoDupPat) {
			//study
			for(String[] dcminfo:exportSet) {
				if(dcminfo[0].equals(patID)) {
					String studyUID = dcminfo[1];
					if(!studyUIDs.contains(studyUID)) {
						studyUIDs.add(studyUID);
					}
					//series
					for(String[] dcminfo2:exportSet) {
						if(dcminfo2[0].equals(patID) && dcminfo2[1].equals(studyUID)) {
							String seriesUID = dcminfo2[2];
							if(!seriesUIDs.contains(seriesUID)) {
								seriesUIDs.add(seriesUID);
							}
							//instance
							for(String[] dcminfo3:exportSet) {
								if(dcminfo3[0].equals(patID) && dcminfo3[1].equals(studyUID) && dcminfo3[2].equals(seriesUID)) {
									String sopUID = dcminfo3[3];
									if(!sopUIDs.contains(sopUID)) {
										sopUIDs.add(sopUID);
									}
								}
							}
						}
					}
				}
			}
		}
		//sum up
		totalStudy = studyUIDs.size();
		totalSeries = seriesUIDs.size();
		totalInst = sopUIDs.size();
		lblInfo.setText("studies "+totalStudy + " series "+totalSeries+" images "+totalInst);
	}

	/*
	 * TODO 20231007
	 */
	public void setCandidateFilesList(ArrayList<DICOMNode> selectedNodes){
//		candidateList = new ArrayList<String>();
//		exportSet = WindowManager.getMainScreen().getTreeTable().createNoDuplicateImageList(selectedNodes);
//		DatabaseHandler db = DatabaseHandler.getInstance();
//		for(String[] dcminfo:exportSet) {
//			candidateList.add(db.getFileLocation(dcminfo[0], dcminfo[1], dcminfo[2], dcminfo[3]));//path2dcm
//		}
	}
	
	ArrayList<String> getCandidateFilesList(){
		return candidateList;
	}
	
	private void showImportResult(int donecount) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// show result
				/* when run send multi studies, only show last send dialog */
				new AnimatingSheet(
						donecount+"/"+getCandidateFilesList().size() + " "
								+ Resources.i18n("MainScreen.import.filesCopied.text"),
						JOptionPane.INFORMATION_MESSAGE);
			}
		});
	}
	
	void send(String path2img) {
		HashMap<String,Object> serverMaterial = db.getServerNamed(destSeverName);
		DicomCommunicationNode remote = new DicomCommunicationNode(serverMaterial);
		String remoteAET = remote.getAETitle();
		String remoteHost = remote.getHostName();
		int remotePort = remote.getPort();
		String listener[] = db.getListenerDetails();
		String args[] = { 
				"-b",
				listener[0] + "@" + listener[1] + ":" + listener[2],//should do test !!
				"-c", 
				remoteAET + "@" + remoteHost + ":" + remotePort, 
				path2img
		};
		StoreSCU.main(args);
	}
	
	private void performSend() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				progressBar.setMaximum(candidateList.size());
			}
		});
		int count = 0;
		while (!(count == candidateList.size()) && !(isStopped())) {
			if (sleepScheduled) {
				try {
					Thread.sleep(SLEEP_TIME);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			/*send*/
			send(candidateList.get(count));
			/*******/
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
			count++;
			updateProgress(count);
		} // while loop end
		doneSend(count);
	}

	@Override
	public void run() {
		performSend();
	}
	
	protected void updateProgress(int progress) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				progressBar.setValue(progress);
			}
		});
	}

	protected void doneSend(int donecount) {
		showImportResult(donecount);
		setVisible(false);
		dispose();
	}

	public Thread getThread() {
		return thisThread;
	}

	public void startSending() {
		thisThread.start();
	}

	public synchronized void resumeSend() {
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

	public void stopSending() {
		thisThread.interrupt();
	}

	/*
	 * all time do single thread,
	 * AllAndWait never call...maybe. tatsu
	 */
//	public static void cancelAllAndWait() {
//		int count = ApplicationContext.importerThreadGroup.activeCount();
//		Thread[] threads = new Thread[count];
//		count = ApplicationContext.importerThreadGroup.enumerate(threads);
//		ApplicationContext.importerThreadGroup.interrupt();
//		for (int i = 0; i < count; i++) {
//			try {
//				threads[i].join();
//			} catch (InterruptedException ie) {
//			}
//		}
//	}
}
