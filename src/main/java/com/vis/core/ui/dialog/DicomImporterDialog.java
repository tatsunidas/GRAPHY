package com.vis.core.ui.dialog;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.task.TaskManager;
import com.vis.core.ui.function.DicomImporter;
import com.vis.dicom.DicomFileCollection;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;

import javax.swing.SwingUtilities;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import java.awt.BorderLayout;

/**
 *
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class DicomImporterDialog extends javax.swing.JDialog {

	public static final int RET_CANCEL = 0;// return status code
	public static final int RET_OK = 1;// return status code
	private int returnStatus = RET_CANCEL;
	private javax.swing.JFileChooser fileChooser;
	// private JCheckBox chckbxSaveAsLink; //never use
	private DicomImporterPanel infoPanel;

	/**
	 * Creates new form FileChooserDialog
	 */
	public DicomImporterDialog(JFrame parent, boolean modal) {
		super(parent, modal);
		initComponents();
		setTitle(ResourceBundle.getBundle("i18n.i18n").getString("DicomImporterDialog.title"));
		setCurrentDirectory();
	}

	/**
	 * @return the return status of this dialog - one of RET_OK or RET_CANCEL
	 */
	public int getReturnStatus() {
		return returnStatus;
	}

	private void doClose(int retStatus) {
		returnStatus = retStatus;
		if(fileChooser != null && fileChooser.isVisible()) {
			fileChooser.setVisible(false);
			fileChooser = null;
		}
		//update home treetable
		WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
		dispose();
	}

	private void initComponents() {
		fileChooser = new javax.swing.JFileChooser();
		setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		fileChooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_AND_DIRECTORIES);
		fileChooser.setMultiSelectionEnabled(true);//IMPORTANT for avoid return selectedFiles null.
		infoPanel = new DicomImporterPanel();
		fileChooser.setAccessory(infoPanel);
		fileChooser.setApproveButtonText("Import");
		// add listener
		fileChooser.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				fileChooserActionPerformed(evt);
			}
		});
		fileChooser.addPropertyChangeListener(evt -> {
			if (javax.swing.JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
				File selectedFile = fileChooser.getSelectedFile();
				if (selectedFile != null && DicomUtilities.isDicomFile(selectedFile)) {
					 String[] info = DicomUtilities.getPatientInfo(selectedFile.getAbsolutePath());
					 infoPanel.setInputs(info);
				}else {
					String[] info = new String[] {null,null,null,null};
					 infoPanel.setInputs(info);
				}
			}
		});
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(fileChooser);
		pack();
	}

	private void fileChooserActionPerformed(java.awt.event.ActionEvent evt) {

		if (evt.getActionCommand().equalsIgnoreCase("CancelSelection")) {
			doClose(RET_CANCEL);
		}

		if (evt.getActionCommand().equalsIgnoreCase("ApproveSelection")) {
			File[] selectedFiles = fileChooser.getSelectedFiles();
			if (selectedFiles == null || selectedFiles.length == 0) {
				PopUpMessage.showDialog(this, "Can not import",
						Resources.i18n("DicomImporterDialog.importError.FilesNotSelected"), JOptionPane.OK_OPTION,
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			DicomFileCollection collec = new DicomFileCollection(selectedFiles);
			collec.collectCandidates();
			//if no dcm files exists, show popup
			if(collec.getNoDcmFiles().size()>0) {
				/*
				 * if non dicom file are included, continue import.
				 * here, to avoid interruption by popmesg, use swingutil.
				 */
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						PopUpMessage.showDialog(WindowManager.getMainScreen(), "Non dicom file found !", "Dicom Importer can not import non dicom files. \nIf you would like to import non dicom, use Non Dcm Importer instead.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
						//still continue
					}
				});
			}
			// If large dataset > 10000
			int res = -1;
			if (filesTooLarge(collec)) {
				res = PopUpMessage.showDialog(this, "Large dataset",
						Resources.i18n("DicomImporterDialog.importWarning.largeFilesToImport"),
						JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
				if (res == JOptionPane.CANCEL_OPTION || res == JOptionPane.CLOSED_OPTION) {
					doClose(RET_CANCEL);
					return;
				}
			}
			//if inputs multiple patients
			res = -1;
			if(collec.getNumOfPatients()>1) {
				String msg = "Will you continue to import without editing patient info ?\n";
				msg += "if (Yes), import all data AS-IS(ignore editing patient info), \n";
				msg += "else (No) imports all data with the specified patient info as a single patient.\n";
				msg += "(study/series descriptions will be ignored)";
				res = PopUpMessage.showDialog(
						WindowManager.getMainScreen(), 
						"Multiple patient dataset found !", 
						msg,
						JOptionPane.YES_NO_CANCEL_OPTION, 
						JOptionPane.INFORMATION_MESSAGE);
				if(res == JOptionPane.CANCEL_OPTION) {doClose(RET_OK);}
				
				if(res == JOptionPane.YES_OPTION) {
					// import each study AS-IS.
					for (String willImportStudyUID : collec.getNoSubstituteStudyUIDList()) {
						ArrayList<String> candidateList = collec.selectCandidateUsingStudyUID(willImportStudyUID);
						DicomImporter importer = new DicomImporter(candidateList, willImportStudyUID);
						TaskManager tm = TaskManager.getInstance();
						tm.startTask(importer.getTaskId());
					}
				}else if(res == JOptionPane.NO_OPTION) {
					HashMap<Integer, Object> info = infoPanel.getInputs();
					String pid = (String)info.get(Tag.Patient​ID);
					if(pid == null || pid.length() < 1) {
						PopUpMessage.showDialog(
								WindowManager.getMainScreen(), 
								"Cannot import !", 
								"Please set PatientID.",
								JOptionPane.YES_NO_CANCEL_OPTION, 
								JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					for (String willImportStudyUID : collec.getNoSubstituteStudyUIDList()) {
						ArrayList<String> candidateList = collec.selectCandidateUsingStudyUID(willImportStudyUID);
						DicomImporter importer = new DicomImporter(candidateList, info, willImportStudyUID);
						TaskManager tm = TaskManager.getInstance();
						tm.startTask(importer.getTaskId());
					}
				}
			}else {
				HashMap<Integer, Object> info = infoPanel.getInputs();
				String pid = (String)info.get(Tag.Patient​ID);
				if(pid == null || pid.length() < 1) {
					res = PopUpMessage.showDialog(
							WindowManager.getMainScreen(), 
							"Will continue import ?", 
							"When PatientID is NULL, AS-IS PatientID will be used to import.",
							JOptionPane.YES_NO_CANCEL_OPTION, 
							JOptionPane.INFORMATION_MESSAGE);
					if(res == JOptionPane.OK_OPTION) {
						for (String willImportStudyUID : collec.getNoSubstituteStudyUIDList()) {
							ArrayList<String> candidateList = collec.selectCandidateUsingStudyUID(willImportStudyUID);
							DicomImporter importer = new DicomImporter(candidateList, null, willImportStudyUID);
							TaskManager tm = TaskManager.getInstance();
							tm.startTask(importer.getTaskId());
						}
					}
				}else {
					for (String willImportStudyUID : collec.getNoSubstituteStudyUIDList()) {
						ArrayList<String> candidateList = collec.selectCandidateUsingStudyUID(willImportStudyUID);
						DicomImporter importer = new DicomImporter(candidateList, infoPanel.getInputs(), willImportStudyUID);
						TaskManager tm = TaskManager.getInstance();
						tm.startTask(importer.getTaskId());
					}
				}
			}
		}
		doClose(RET_OK);
	}

	private void setCurrentDirectory() {
		fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
	}
	
	private boolean filesTooLarge(DicomFileCollection collec) {
		int num = collec.getNumOfTotalDcmFiles();
		return num > 10000 ? true:false;
	}
}
