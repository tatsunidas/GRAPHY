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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.DateUtils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.VR;
import com.vis.core.ui.listener.DateTextKeyListener;

/**
 * Edit all instances of particular patient on PATIENT LEVEL.
 * @author tatsunidas
 *
 */
public class PatientInfoEditor {
	
	/*
	 * pid -this is primary key, so if you want to edit pid, delete from db and re-record it.-
	 * pname
	 * bod
	 * sex
	 */
	ArrayList<DICOMNode> selected;
	JTextField pidField;
	JTextField pnameField;
	JTextField bodField;
	ButtonGroup selectSexGroup;
	JCheckBox editAllStudy;
	
	String previousPID;
	String previousPNAME;
	String previousBOD;
	String previousSEX;
	
	DICOMBackend backend = DICOMBackend.getCurrent();
	DatabaseHandler db = DatabaseHandler.getInstance();
	
	public PatientInfoEditor(ArrayList<DICOMNode> selected) {
		if (selected == null || selected.size() < 1) {
			return;
		}
		this.selected = selected;
		if(!isReady()) {
			PopUpMessage.showDialog(WindowManager.getMainScreen(), "Patient Info Editor", "Please select same patient images.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		int res = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), constructPanel(), "Edit patient information", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if(res == JOptionPane.OK_OPTION) {
			if(previousPID == null || previousPID.length() == 0) {
				Log.logger.fine("patientID not found. return");
				return;
			}
			String newPID = pidField.getText();
			String newPNAME = pnameField.getText();
			String newBOD = bodField.getText();
			String newSex = selectSexGroup.getSelection().getActionCommand();//Male, Female, Other
			boolean editAllStudies = editAllStudy.isSelected();
			if(newPID.length() == 0) {
				JOptionPane.showMessageDialog(WindowManager.getMainScreen(),"PatientInfoEditor: Please input PatientID.");
				return;
			}
			if(previousPID.equals(newPID) && previousPNAME.equals(newPNAME) && previousSEX.equals(newSex) && previousBOD.equals(newBOD)) {
				Log.logger.info("Edit patient information : There is no changes, return.");
				return;
			}
			HashMap<String, String> pmap = new HashMap<>();
			pmap.put("PatientID", newPID);
			pmap.put("PatientName", newPNAME);
			pmap.put("PatientBirthDate", newBOD);
			pmap.put("PatientSex", newSex);
			
			edit(newPID, newPNAME, newBOD, newSex, editAllStudies);
		}
//		reflesh table
		WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
		
	}
	
	/**
	 * check it contains only one patient.
	 * @return
	 */
	private boolean isReady() {
		// check whether all nodes have same pid
		String pid = selected.get(0).getData(DICOMNode.PatientID);
		for (int i = 1; i < selected.size(); i++) {
			if (!pid.equals(selected.get(i).getData(DICOMNode.PatientID))) {
				JOptionPane.showMessageDialog(null,"PatientInfoEditor: Please select single Patient.");
				return false;
			}
		}
		/*
		 * comment out. tatsuaki
		 * On editing patient info, should be handle changing information in patient level. 
		 */
		// check whether has only one study uid.
//		String studyUID = selected.get(0).getData(DICOMNode.StudyInstanceUID);
//		for (int i = 1; i < selected.size(); i++) {
//			if (!studyUID.equals(selected.get(i).getData(DICOMNode.StudyInstanceUID))) {
//				JOptionPane.showMessageDialog(null,"PatientInfoEditor: Please select only single study.");
//				return false;
//			}
//		}
		return true;
	}
	
	private JPanel constructPanel() {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			previousPID = selected.get(0).getData(DICOMNode.PatientID);
			previousPNAME = selected.get(0).getData(DICOMNode.PatientName);
			previousBOD = selected.get(0).getData(DICOMNode.BirthDate);
			if(previousBOD != null && previousBOD.length()!=0) {
				previousBOD = previousBOD.replace("-", "/");
			}
			previousSEX = selected.get(0).getData(DICOMNode.Sex).trim();
		}else {
			HashMap<String, String> pmap = db.getPatientInfo(selected.get(0).getData(DICOMNode.PatientID));
			previousPID = pmap.get("PatientID");
			previousPNAME = pmap.get("PatientName");
			previousBOD = pmap.get("PatientBirthDate");
			if(previousBOD != null && previousBOD.length()!=0) {
				previousBOD = previousBOD.replace("-", "/");
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
		editAllStudy = new JCheckBox("Apply changes to all studies");
		pidField = new JTextField(previousPID);
		pnameField = new JTextField(previousPNAME);
		bodField = new JTextField(previousBOD);
		bodField.addKeyListener(new DateTextKeyListener());
		
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
		p.add(new JLabel(""));
		p.add(editAllStudy);
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
	
	
	private void edit(String pid, String pname, String bod, String sex, boolean editAllStudies) {
//		get all images which have previous pid
		ArrayList<String> files = null;
		if(editAllStudies) {
			files = db.getFileLocationsPatientLevel(previousPID);//no duplicate
		}else {
			files = db.getFileLocationsStudyLevel(selected.get(0).getData(DICOMNode.StudyInstanceUID));
		}
		// create idset
		ArrayList<String[]> imageUIDs = (ArrayList<String[]>) db.getUIDsByFileLocations(files);
		HashMap<String, String> pmap = new HashMap<>();
		pmap.put("PatientID", pid);
		pmap.put("PatientName", pname);
		pmap.put("PatientBirthDate", bod);
		pmap.put("PatientSex", sex);
		
		//case_1: to new patient
		if(!previousPID.equals(pid)) {
			//search existing patient from DB.
			HashMap<String, String> info = db.getPatientInfo(pid);
			//if true, integrate it to.
			if(info != null) {
				Log.logger.info(pid+" is already exists, will integrate to.");
				DicomDuplicator.updatePatientInformationAndStore2DB(imageUIDs,info);
			//else, to new one
			}else {
				//update UIDs and write to DB.
				DicomDuplicator.updatePatientInformationAndStore2DB(imageUIDs,pmap);
			}
			// delete
			if (JOptionPane.showConfirmDialog(null, "Delete these files after re-write ?", "Delete ?",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_NO_OPTION) {
				DeleteImage.deleteImagesByFilePath(files);
			}
		//case_2: if pid was not changed, overwrite all of the studies.
		}else {
			if(editAllStudies) {
				for (String f : files) {
					overwritePatientAttributes(f, pmap);
				}
			}else {
				Log.logger.log(Level.WARNING, "You are trying to change patient information of some studies.");
				String msg = "You are trying to change patient information of some studies.\n";
				msg += "Basically, patient level information must be changed for all data.\n";
				msg += "Would you like to continue?";
				int res = PopUpMessage.showDialog(WindowManager.getMainScreen(),"Continue edit all ?",msg, JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
				if(res == JOptionPane.OK_OPTION) {
					for (String f : files) {
						overwritePatientAttributes(f, pmap);
					}
				}else {
					return;
				}
			}
		}
	}
	
	public void overwritePatientAttributes(String filePath, HashMap<String, String> pmap) {
		
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			return;
		}
		
		DicomReader dr = DicomReader.newDicomReader(backend); 
		dr.read(filePath, true);// with pixel
		
		DicomObject orgDcm = dr.getHeader();
		String tsUID = dr.checkTSUID().uid();

		String PID = pmap.get("PatientID").trim(); //pid is never changing
		String newPNAME = pmap.get("PatientName").trim();
		String newBOD = pmap.get("PatientBirthDate").trim().replace("/", "");
		String newSex = pmap.get("PatientSex").trim();
		
		orgDcm.setString(Tag.Patient​Name, VR.PN, newPNAME);
		orgDcm.setDate(Tag.Patient​Birth​Date, VR.DA, DateUtils.toSQLDateObj(newBOD));
		orgDcm.setString(Tag.Patient​Sex, VR.CS, newSex);
		//do not update UIDs, keep original.
//		orgDcm.setString(Tag.Study​Instance​UID, VR.UI, newStudyUID);
//		orgDcm.setString(Tag.Series​Instance​UID, VR.UI, newSeriesUID);
//		orgDcm.setString(Tag.SOP​Instance​UID, VR.UI, newSopInstUID);
//		orgDcm.setString(Tag.Media​Storage​SOP​Instance​UID, VR.UI, newSopInstUID);
		/*
		 * re-write
		 */
		DicomWriter writer = DicomWriter.newDicomWriter(backend);
		writer.write(orgDcm,  tsUID, filePath);
		// db updation
		db.update("PATIENT", "PatientName", newPNAME, "PatientID", PID);
		java.sql.Date sqlDate = DateUtils.toSQLDateObj(newBOD);
		/*
		 * BOD is NULL-able, but, here not set null.
		 */
		if (sqlDate != null) {
			db.update("PATIENT", "PatientBirthDate", sqlDate, "PatientID", PID);
		}
		db.update("PATIENT", "PatientSex", newSex, "PatientID", PID);
	}
}
