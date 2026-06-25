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

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
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
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JComboBox;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.AnimatingSheet;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.dimse.StoreSCU;


public class DicomPostman extends JDialog implements Runnable{

	/**
	 * @author tatsunidas
	 *  
	 * TODO
	 * implement the Task interface
	 * DICOM-MOVE: From QR table To Another node
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
	public int SLEEP_TIME = 1000;
	protected boolean suspended = false;
	private JComboBox<String> comboBox;
	private JProgressBar progressBar;
	private JButton btnSuspend;
	private JButton btnContinue;
	private JButton btnCancel;
	private JButton btnSend;
	
	public DicomPostman(ArrayList<DICOMNode> selectedNodes) {
		if(selectedNodes == null || selectedNodes.size() < 1) {
			Log.logger.warning("DicomPostman: no nodes selected.");
			JOptionPane.showMessageDialog(WindowManager.getMainScreen(), Resources.i18n("DicomPostman.error.noSelection"), Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		db = DatabaseHandler.getInstance();

		//check servers
		ArrayList<HashMap<String,Object>> serverMaterials = db.getCommunicationServerList();
		if(serverMaterials == null || serverMaterials.isEmpty()) {
			Log.logger.warning("DicomPostman: no communicable remote servers found.");
			JOptionPane.showMessageDialog(WindowManager.getMainScreen(), Resources.i18n("DicomPostman.error.noServers"), Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		servers = new ArrayList<DicomCommunicationNode>();
		for(HashMap<String,Object> materials:serverMaterials) {
			DicomCommunicationNode server = new DicomCommunicationNode(materials);
			servers.add(server);
		}
		setCandidateFilesList(selectedNodes);
		setUpGui();
		stopped = false;
		sleepScheduled = false;
		suspended = false;
		setVisible(true);
		thisThread = new Thread(this);
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
		
		btnSend = new JButton("Send");
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
			String pname = db.getValueFromPatient("PatientName", info[0]);
			String studydate = db.getValueFromStudy("StudyDate", info[0], info[1]);
			String modality = db.getValueFromSeries("Modality", info[0], info[1], info[2]);
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
		JScrollPane scrollPane = new JScrollPane(list);
		panel.add(scrollPane, BorderLayout.CENTER);
		
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
		candidateList = new ArrayList<String>();
		exportSet = WindowManager.getMainScreen().getLocalTreeTable().createNoDuplicateImageList(selectedNodes);
		DatabaseHandler db = DatabaseHandler.getInstance();
		for(String[] dcminfo:exportSet) {
			candidateList.add(db.getFileLocation(dcminfo[1], dcminfo[2], dcminfo[3]));//path2dcm
		}
	}
	
	ArrayList<String> getCandidateFilesList(){
		return candidateList;
	}
	
	private void showImportResult(int donecount) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// show result
				new AnimatingSheet(
						donecount+"/"+getCandidateFilesList().size() + " "
								+ Utils.i18n().getString("MainScreen.send.completed.text"),
						JOptionPane.INFORMATION_MESSAGE);
			}
		});
	}
	
	void send(String path2img) {
		HashMap<String,Object> serverMaterial = db.getServerInfo(destSeverName);
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
		// ★ 接続先ノードが「Use TLS」なら、下層のStoreSCU.mainへTLS要求を伝搬する。
		com.vis.dicom.tls.DicomTlsConfig.requestScuTls(remote.isTlsEnabled(), remote.getCipher());
		try {
			StoreSCU.main(args);
		} finally {
			com.vis.dicom.tls.DicomTlsConfig.clearScuTls();
		}
	}
	
	private void performSend() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				progressBar.setMaximum(candidateList.size());
			}
		});
		int count = 0;
		while (!(count == candidateList.size()) && !(isStopped())) {
			if (sleepScheduled) {
				try {
					Thread.sleep(SLEEP_TIME);
				} catch (InterruptedException e) {
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
}
