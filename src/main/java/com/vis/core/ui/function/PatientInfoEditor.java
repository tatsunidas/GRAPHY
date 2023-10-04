package com.vis.core.ui.function;

import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.DateUtils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;

//TODO
//import com.vis.dimse.delegate.DicomDuplicator;
//import com.vis.dimse.delegate.DimseUtilities;
//import com.vis.ui.listener.DateTextKeyListener;

public class PatientInfoEditor {
	
	/*
	 * pid -this is primary key, so you can not edit pid.(if you want to do, delete it then import again.)-
	 * pname
	 * bod
	 * sex
	 */
	ArrayList<DICOMNode> selected;
	JTextField pidField;
	JTextField pnameField;
	JTextField bodField;
	ButtonGroup selectSexGroup;
	JCheckBox processAllChk;
	
	String previousPID;
	String previousPNAME;
	String previousBOD;
	String previousSEX;
	
	public PatientInfoEditor(ArrayList<DICOMNode> selected) {
		if (selected == null || selected.size() < 1) {
			return;
		}
		this.selected = selected;
		if(!isReady()) {
			JOptionPane.showMessageDialog(null,"PatientInfoEditor: please select same patient images.");
			return;
		}
		if(JOptionPane.showConfirmDialog(null, constructPanel(), "Edit patient information for selected images", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
			if(previousPID == null || previousPID.length() == 0) {
				//somethig strange
				System.out.println("patientID not found. return");
				return;
			}
			String newPID = pidField.getText();
			String newPNAME = pnameField.getText();
			String newBOD = bodField.getText();
			String newSex = selectSexGroup.getSelection().getActionCommand();//Male, Female, Other
			if(newPID.length() == 0) {
				JOptionPane.showMessageDialog(null,"PatientInfoEditor: Please input PatientID.");
				return;
			}
			boolean processAll = processAllChk.isSelected();
			if(previousPID.equals(newPID) && previousPNAME.equals(newPNAME) && previousSEX.equals(newSex) && previousBOD.equals(newBOD)) {
				System.out.println("Edit patient information : no change, return.");
				return;
			}
			if(!processAll) {
				HashMap<String, String> pmap = new HashMap<>();
				pmap.put("PatientID", newPID);
				pmap.put("PatientName", newPNAME);
				pmap.put("PatientBirthDate", newBOD);
				pmap.put("PatientSex", newSex);
				ArrayList<String[]> imageUIDsStillInDB = editOnlySelected(pmap);
				if(imageUIDsStillInDB != null) {
					// delete
					if (JOptionPane.showConfirmDialog(null, "Delete these files after re-write ?", "Delete ?",
							JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
						boolean dummy = true;
						DeleteImage.deleteImages(imageUIDsStillInDB, dummy);
					}
				}
			}else {
				/*
				 * even if already exists newPID, update all images with this condition.
				 */
				ArrayList<String[]> imageUIDsStillInDB = editAll(newPID, newPNAME, newBOD, newSex);
				if(imageUIDsStillInDB != null) {
					// delete
					if (JOptionPane.showConfirmDialog(null, "Delete these files after re-write ?", "Delete ?",
							JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
						boolean dummy = true;
						DeleteImage.deleteImages(imageUIDsStillInDB, dummy);
					}
				}
			}
		}
//		reflesh table
		WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
	}
	
	/**
	 * return duplicated original UIDs still in DB.
	 * 
	 */
	private ArrayList<String[]> editAll(String pid, String pname, String bod, String sex) {
//		get all images which have previous pid
		DatabaseHandler db = DatabaseHandler.getInstance();
		ArrayList<String> files = (ArrayList<String>) db.getFileLocationsByPid(previousPID);//no duplicate
		// create idset
		ArrayList<String[]> imageUIDs = (ArrayList<String[]>) db.getUIDsByFileLocations(files);
		HashMap<String, String> pmap = new HashMap<>();
		pmap.put("PatientID", pid);
		pmap.put("PatientName", pname);
		pmap.put("PatientBirthDate", bod);
		pmap.put("PatientSex", sex);
//		rewrite dicom data
		if(!previousPID.equals(pid)) {
			/**
			 * TODO 20230829
			 */
//			DicomDuplicator.updatePatientInformationAndStore2DB(imageUIDs,pmap);
			return imageUIDs;
		}else {
			for(String f : files) {
				overwritePatientAttributes(db,f, pmap);
			}
			return null;
		}
	}
	
	private ArrayList<String[]> editOnlySelected(HashMap<String, String> patInfoMap) {
		//pid-studyuid-seriesuid-instuid array list
		ArrayList<String[]> noDuplicatedInstList = WindowManager.getMainScreen().getLocalTreeTable().createNoDuplicateImageList(selected);
		//edit dicom and dup
		if(!previousPID.equals(patInfoMap.get("PatientID"))) {
			/**
			 * TODO 20280829
			 */
//			DicomDuplicator.updatePatientInformationAndStore2DB(noDuplicatedInstList, patInfoMap);
			return noDuplicatedInstList;
		}else {
			if (JOptionPane.showConfirmDialog(null, "Edit all images this patient ?", "Edit all ?",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
				String pid = patInfoMap.get("PatientID");
				String pname = patInfoMap.get("PatientName");
				String bod = patInfoMap.get("PatientBirthDate");
				String sex = patInfoMap.get("PatientSex");
				return editAll(pid, pname, bod, sex);
			}else {
				return null;
			}
		}
	}

	private boolean isReady() {
//		all nodes have same pid ?
		String pid = selected.get(0).getData(DICOMNode.PatientID);
		for(int i=1;i<selected.size();i++) {
			if(!pid.equals(selected.get(i).getData(DICOMNode.PatientID))) {
				return false;
			}
		}
		return true;
	}
	
	private JPanel constructPanel() {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			previousPID = selected.get(0).getData(DICOMNode.PatientID);
			previousPNAME = selected.get(0).getData(DICOMNode.PatientName);
			previousBOD = selected.get(0).getData(DICOMNode.BirthDate);
			if(previousBOD != null && previousBOD.length()!=0) {
				previousBOD.replace("-", "/");
			}
			previousSEX = selected.get(0).getData(DICOMNode.Sex).trim();
		}else {
			HashMap<String, String> pmap = db.getPatientInfoByPatID(selected.get(0).getData(DICOMNode.PatientID));
			previousPID = pmap.get("PatientID");
			previousPNAME = pmap.get("PatientName");
			previousBOD = pmap.get("PatientBirthDate");
			if(previousBOD != null && previousBOD.length()!=0) {
				previousBOD.replace("-", "/");
			}
			previousSEX = pmap.get("PatientSex");
		}
		/*
		 * JTextField.getText() will return "" blank string (not null).
		 * here, adjust null value to ""(blank=string.length()==0).
		 */
		//patientID is none null value.
		if(previousPNAME == null) {
			previousPNAME = "";
		}
		if(previousBOD == null) {
			previousBOD = "";
		}
		if(previousSEX == null) {
			previousSEX = "";
		}
		JPanel p = new JPanel();
		int gap = 2;
		p.setLayout(new java.awt.GridLayout(5,2,gap,gap));
		processAllChk = new JCheckBox("Process all images in DB about this patient");
		processAllChk.setSelected(false);
		processAllChk.setToolTipText("if yes, re-write all images about this patients, else, edit only selected images.");
		pidField = new JTextField(previousPID);
		pnameField = new JTextField(previousPNAME);
		bodField = new JTextField(previousBOD);
		
		/**
		 * TODO
		 * 20230829
		 */
//		bodField.addKeyListener(new DateTextKeyListener());
		
		selectSexGroup = new ButtonGroup();
		JRadioButton rbtnMale = new JRadioButton("Male");
		JRadioButton rbtnFemale = new JRadioButton("Female");
		JRadioButton rbtnOther = new JRadioButton("Other");
		rbtnMale.setActionCommand("M");
		rbtnFemale.setActionCommand("F");
		rbtnOther.setActionCommand("O");
		if(previousSEX.equals("M")) {
			rbtnMale.setSelected(true);
		}else if(previousSEX.equals("F")) {
			rbtnFemale.setSelected(true);
		}else {
			rbtnOther.setSelected(true);
		}
		selectSexGroup.add(rbtnMale);
		selectSexGroup.add(rbtnFemale);
		selectSexGroup.add(rbtnOther);
		JPanel sexPanel = new JPanel();
		sexPanel.add(rbtnMale);
		sexPanel.add(rbtnFemale);
		sexPanel.add(rbtnOther);
		p.add(new JLabel("Process Mode"));
		p.add(processAllChk);
		p.add(new JLabel("PatientID"));
		p.add(pidField);
		p.add(new JLabel("PatientName"));
		p.add(pnameField);
		p.add(new JLabel("BirthOfDate (yyyy/MM/dd)"));
		p.add(bodField);
		p.add(new JLabel("Sex"));
		p.add(sexPanel);
		return p;
	}
	
	
	public void overwritePatientAttributes(DatabaseHandler db, String filePath, HashMap<String, String> pmap) {
		String backend = DICOMBackend.getCurrent().name();
		if(backend.equals("dcm4che")) {
			/*
			 * overwrite
			 */
			
			/**
			 * TODO 20230829
			 */
//			DicomWriter.overWritePatientInfo(filePath, pmap.get("PatientName"), pmap.get("PatientBirthDate"), pmap.get("PatientSex"));
			
			// db updation
			db.update("PATIENT", "PatientName", pmap.get("PatientName"), "PatientID", pmap.get("PatientID"));
			java.sql.Date sqlDate = DateUtils.toSQLDateObj(pmap.get("PatientBirthDate"));
			/*
			 * BOD is NULL-able, but, here not set if null.
			 */
			if(sqlDate != null) {
				db.update("PATIENT", "PatientBirthDate", sqlDate, "PatientID", pmap.get("PatientID"));
			}
			db.update("PATIENT", "PatientSex", pmap.get("PatientSex"), "PatientID", pmap.get("PatientID"));
		}else {
			//do something another dcm engine ?
		}
	}
}
