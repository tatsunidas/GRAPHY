package com.vis.core.ui.dialog;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.ui.function.DicomImporter;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.dicom.DicomFileCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.ResourceBundle;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JCheckBox;
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
	DICOMTreeTable mainTreeTable;
	private JCheckBox chckbxSaveAsLink;

	/**
	 * Creates new form FileChooserDialog
	 */
	public DicomImporterDialog(JFrame parent, boolean modal) {
		super(parent, modal);
		this.mainTreeTable = WindowManager.getMainScreen().getLocalTreeTable();
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
		dispose();
	}

	private void initComponents() {

		fileChooser = new javax.swing.JFileChooser();
		setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		fileChooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_AND_DIRECTORIES);
		fileChooser.setMultiSelectionEnabled(true);//IMPORTANT for avoid return selectedFiles null.
		fileChooser.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				fileChooserActionPerformed(evt);
			}
		});
		getContentPane().setLayout(new BorderLayout(0, 0));
		getContentPane().add(fileChooser);
		
		JPanel saveTypePanel = new JPanel();
		getContentPane().add(saveTypePanel, BorderLayout.SOUTH);
		
		chckbxSaveAsLink = new JCheckBox("Save as link");
		saveTypePanel.add(chckbxSaveAsLink);

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
			if (selectedFiles.length == 0) {
				return;
			}
			DicomFileCollection collec = new DicomFileCollection(selectedFiles);
			collec.collectCandidates();
			//if no dcm files exists, show popup
			if(collec.getNoDcmFiles().size()>0) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						PopUpMessage.showDialog(null, "None dicom file found !", "Dicom Importer can not import non dicom files.\nIf you would like to import, use General Image Format Importer instead.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
					}
				});
			}
			// If large dataset > 10000
			if (filesTooLarge(collec)) {
				int res = PopUpMessage.showDialog(this, "Large dataset",
						Resources.i18n("DicomImporterDialog.importWarning.largeFilesToImport"),
						JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
				if (res == JOptionPane.CANCEL_OPTION || res == JOptionPane.CLOSED_OPTION) {
					doClose(RET_CANCEL);
					return;
				}
			}
			boolean saveAsLink = chckbxSaveAsLink.isSelected();
//			boolean ignorePrivate = ignorePrivate();//TODO
			// import of each study.
			for (String willImportStudyUID : collec.getNoSubstituteStudyUIDList()) {
				ArrayList<String> candidateList = collec.selectCandidateUsingStudyUID(willImportStudyUID);
				DicomImporter importer = new DicomImporter(candidateList, willImportStudyUID,saveAsLink, false);
				importer.start();
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
