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

import javax.swing.JPanel;
import java.awt.GridBagLayout;
import javax.swing.JLabel;

import java.awt.GridBagConstraints;
import javax.swing.JTextField;

import com.vis.core.log.Log;
import com.vis.core.ui.listener.AlphanumericTextKeyListener;
import com.vis.core.ui.listener.DateTextKeyListener;
import com.vis.db.DatabaseHandler;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.awt.Component;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.ButtonGroup;

import java.awt.Dimension;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.JComboBox;

public class ImportNonDicomImagePanel extends JPanel{

	private static final long serialVersionUID = 1L;
	
	private JTextField textField_pid;
	private JTextField textField_pname;
	private JTextField textField_dob;
	private JTextField textField_study;
	private JTextField textField_series;
	private ButtonGroup btnGroupSex;
	private JComboBox<StudyContext> comboBoxStudies;
	private JCheckBox chckbxAddToExisting;
	
	String currentStudyUID = null;
	
	DatabaseHandler db = DatabaseHandler.getInstance();
	
	public ImportNonDicomImagePanel() {
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 231, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 31, 0, 0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		Component rigidArea = Box.createRigidArea(new Dimension(20, 20));
		GridBagConstraints gbc_rigidArea = new GridBagConstraints();
		gbc_rigidArea.insets = new Insets(0, 0, 5, 5);
		gbc_rigidArea.gridx = 0;
		gbc_rigidArea.gridy = 0;
		add(rigidArea, gbc_rigidArea);
		
		Component rigidArea_1 = Box.createRigidArea(new Dimension(20, 20));
		GridBagConstraints gbc_rigidArea_1 = new GridBagConstraints();
		gbc_rigidArea_1.insets = new Insets(0, 0, 5, 0);
		gbc_rigidArea_1.gridx = 3;
		gbc_rigidArea_1.gridy = 0;
		add(rigidArea_1, gbc_rigidArea_1);
		
		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut.gridx = 0;
		gbc_horizontalStrut.gridy = 1;
		add(horizontalStrut, gbc_horizontalStrut);
		
		JLabel lblPatientId = new JLabel("Patient ID");
		GridBagConstraints gbc_lblPatientId = new GridBagConstraints();
		gbc_lblPatientId.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientId.anchor = GridBagConstraints.EAST;
		gbc_lblPatientId.gridx = 1;
		gbc_lblPatientId.gridy = 1;
		add(lblPatientId, gbc_lblPatientId);
		
		textField_pid = new JTextField();
		GridBagConstraints gbc_textField_pid = new GridBagConstraints();
		gbc_textField_pid.insets = new Insets(0, 0, 5, 5);
		gbc_textField_pid.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField_pid.gridx = 2;
		gbc_textField_pid.gridy = 1;
		add(textField_pid, gbc_textField_pid);
		//textField_pid.setColumns(30);
		// Add a key adapter to the text field
		textField_pid.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				int keyCode = e.getKeyCode();
				if(keyCode == KeyEvent.VK_ENTER) {
					Log.logger.fine("Key Pressed: " + KeyEvent.getKeyText(keyCode));
					searchInDB();
				}
			}
			@Override
			public void keyReleased(KeyEvent e) {
				searchInDB();
			}
		});
		
		JLabel lblPatientName = new JLabel("Patient Name");
		GridBagConstraints gbc_lblPatientName = new GridBagConstraints();
		gbc_lblPatientName.anchor = GridBagConstraints.EAST;
		gbc_lblPatientName.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientName.gridx = 1;
		gbc_lblPatientName.gridy = 2;
		add(lblPatientName, gbc_lblPatientName);
		
		textField_pname = new JTextField();
		GridBagConstraints gbc_textField_pname = new GridBagConstraints();
		gbc_textField_pname.insets = new Insets(0, 0, 5, 5);
		gbc_textField_pname.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField_pname.gridx = 2;
		gbc_textField_pname.gridy = 2;
		add(textField_pname, gbc_textField_pname);
		//textField_pname.setColumns(30);
		
		JLabel lblBirthOfDate = new JLabel("Date Of Birth (yyyy/MM/dd)");
		GridBagConstraints gbc_lblBirthOfDate = new GridBagConstraints();
		gbc_lblBirthOfDate.anchor = GridBagConstraints.EAST;
		gbc_lblBirthOfDate.insets = new Insets(0, 0, 5, 5);
		gbc_lblBirthOfDate.gridx = 1;
		gbc_lblBirthOfDate.gridy = 3;
		add(lblBirthOfDate, gbc_lblBirthOfDate);
		
		textField_dob = new JTextField();
		GridBagConstraints gbc_textField_dob = new GridBagConstraints();
		gbc_textField_dob.insets = new Insets(0, 0, 5, 5);
		gbc_textField_dob.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField_dob.gridx = 2;
		gbc_textField_dob.gridy = 3;
		add(textField_dob, gbc_textField_dob);
		textField_dob.setColumns(10);
		
		JLabel lblPatientSex = new JLabel("Patient Sex");
		GridBagConstraints gbc_lblPatientSex = new GridBagConstraints();
		gbc_lblPatientSex.anchor = GridBagConstraints.EAST;
		gbc_lblPatientSex.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientSex.gridx = 1;
		gbc_lblPatientSex.gridy = 4;
		add(lblPatientSex, gbc_lblPatientSex);
		
		JPanel panel = new JPanel();
		GridBagConstraints gbc_panel = new GridBagConstraints();
		gbc_panel.insets = new Insets(0, 0, 5, 5);
		gbc_panel.fill = GridBagConstraints.BOTH;
		gbc_panel.gridx = 2;
		gbc_panel.gridy = 4;
		add(panel, gbc_panel);
		
		JRadioButton rdbtnMale = new JRadioButton("Male");
		rdbtnMale.setActionCommand("Male");
		panel.add(rdbtnMale);
		
		JRadioButton rdbtnFemale = new JRadioButton("Female");
		rdbtnFemale.setActionCommand("Female");
		panel.add(rdbtnFemale);
		
		JRadioButton rdbtnOther = new JRadioButton("Other");
		rdbtnOther.setActionCommand("Other");
		panel.add(rdbtnOther);
		
		JRadioButton rdbtnNone = new JRadioButton("None");
		rdbtnNone.setActionCommand("None");
		panel.add(rdbtnNone);
		
		btnGroupSex = new ButtonGroup();
		btnGroupSex.add(rdbtnMale);
		btnGroupSex.add(rdbtnFemale);
		btnGroupSex.add(rdbtnOther);
		btnGroupSex.add(rdbtnNone);
		
		JLabel lblImport = new JLabel("Import as");
		GridBagConstraints gbc_lblImport = new GridBagConstraints();
		gbc_lblImport.anchor = GridBagConstraints.EAST;
		gbc_lblImport.insets = new Insets(0, 0, 5, 5);
		gbc_lblImport.gridx = 1;
		gbc_lblImport.gridy = 5;
		add(lblImport, gbc_lblImport);
		
		chckbxAddToExisting = new JCheckBox("Add to existing a study");
		chckbxAddToExisting.setToolTipText("If not checked, import the data as a new study.");
		GridBagConstraints gbc_chckbxAddToExisting = new GridBagConstraints();
		gbc_chckbxAddToExisting.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxAddToExisting.gridx = 2;
		gbc_chckbxAddToExisting.gridy = 5;
		add(chckbxAddToExisting, gbc_chckbxAddToExisting);
		// Add an action listener to the checkbox
		chckbxAddToExisting.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(chckbxAddToExisting.isSelected()) {
					comboBoxStudies.setEnabled(true);
					comboBoxStudies.setEditable(false);
					textField_study.setEnabled(false);
					textField_study.setEditable(false);
					
				}else {
					comboBoxStudies.setEnabled(false);
					textField_study.setEnabled(true);
					textField_study.setEditable(true);
				}
			}
		});
		
		comboBoxStudies = new JComboBox<>();
		comboBoxStudies.setEditable(true);
		// Set the preferred size of the JComboBox
       comboBoxStudies.setPreferredSize(new Dimension(231, 31));

		GridBagConstraints gbc_comboBoxStudies = new GridBagConstraints();
		gbc_comboBoxStudies.insets = new Insets(0, 0, 5, 5);
		gbc_comboBoxStudies.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBoxStudies.gridx = 2;
		gbc_comboBoxStudies.gridy = 6;
		add(comboBoxStudies, gbc_comboBoxStudies);
		
		JLabel lblStudyDesc = new JLabel("Study Desc");
		GridBagConstraints gbc_lblStudyDesc = new GridBagConstraints();
		gbc_lblStudyDesc.insets = new Insets(0, 0, 5, 5);
		gbc_lblStudyDesc.gridx = 1;
		gbc_lblStudyDesc.gridy = 7;
		add(lblStudyDesc, gbc_lblStudyDesc);
		
		textField_study = new JTextField();
		GridBagConstraints gbc_textField_study = new GridBagConstraints();
		gbc_textField_study.insets = new Insets(0, 0, 5, 5);
		gbc_textField_study.fill = GridBagConstraints.BOTH;
		gbc_textField_study.gridx = 2;
		gbc_textField_study.gridy = 7;
		add(textField_study, gbc_textField_study);
		
		JLabel lblSeriesDesc = new JLabel("Series Desc");
		GridBagConstraints gbc_lblSeriesDesc = new GridBagConstraints();
		gbc_lblSeriesDesc.insets = new Insets(0, 0, 5, 5);
		gbc_lblSeriesDesc.gridx = 1;
		gbc_lblSeriesDesc.gridy = 8;
		add(lblSeriesDesc, gbc_lblSeriesDesc);
		
		textField_series = new JTextField();
		GridBagConstraints gbc_textField_series  = new GridBagConstraints();
		gbc_textField_series .insets = new Insets(0, 0, 5, 5);
		gbc_textField_series .fill = GridBagConstraints.BOTH;
		gbc_textField_series .gridx = 2;
		gbc_textField_series .gridy = 8;
		add(textField_series , gbc_textField_series);
		
		Component rigidArea_2 = Box.createRigidArea(new Dimension(20, 20));
		GridBagConstraints gbc_rigidArea_2 = new GridBagConstraints();
		gbc_rigidArea_2.insets = new Insets(0, 0, 0, 5);
		gbc_rigidArea_2.gridx = 0;
		gbc_rigidArea_2.gridy = 9;
		add(rigidArea_2, gbc_rigidArea_2);
		
		Component rigidArea_3 = Box.createRigidArea(new Dimension(20, 20));
		GridBagConstraints gbc_rigidArea_3 = new GridBagConstraints();
		gbc_rigidArea_3.gridx = 3;
		gbc_rigidArea_3.gridy = 9;
		add(rigidArea_3, gbc_rigidArea_3);
		
		//listener
		AlphanumericTextKeyListener pnameTextListener = new AlphanumericTextKeyListener(64,
				AlphanumericTextKeyListener.pname_acceptables);
		AlphanumericTextKeyListener pidTextListener = new AlphanumericTextKeyListener(64,
				AlphanumericTextKeyListener.pid_acceptables);
		DateTextKeyListener dobTextListener = new DateTextKeyListener();

		textField_pid.addKeyListener(pidTextListener);
		textField_pname.addKeyListener(pnameTextListener);
		textField_dob.addKeyListener(dobTextListener);
		
	}
	
	public void searchInDB() {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (textField_pid != null && db != null) {
			String pid = textField_pid.getText();
			if (pid != null && pid.strip().length()!=0) {
				HashMap<String, String> info = db.getPatientInfoByPatID(pid);
				if (info != null) {
					textField_pname.setText(info.get("PatientName"));
					textField_dob.setText(info.get("PatientBirthDate"));
					String sex = info.get("PatientSex");
					
					Log.logger.fine("PID key listener is working!"+":"+pid+"_"+info.get("PatientName")+"_"+info.get("PatientBirthDate")+"_"+sex);
					
					if (sex != null) {
						Iterator<AbstractButton> iter = btnGroupSex.getElements().asIterator();
						while (iter.hasNext()) {
							AbstractButton currentButton = iter.next();
							if (sex.equals("M")) {
								if (currentButton.getActionCommand().equals("Male")) {
									btnGroupSex.setSelected(currentButton.getModel(), true);
									break;
								}
							} else if (sex.equals("F")) {
								if (currentButton.getActionCommand().equals("Female")) {
									btnGroupSex.setSelected(currentButton.getModel(), true);
									break;
								}
							} else if (sex.equals("O")) {
								if (currentButton.getActionCommand().equals("Other")) {
									btnGroupSex.setSelected(currentButton.getModel(), true);
									break;
								}
							} else {//for anonymized data
								if (currentButton.getActionCommand().equals("None")) {
									btnGroupSex.setSelected(currentButton.getModel(), true);
									break;
								}
							}
						}
					}else {
						Iterator<AbstractButton> iter = btnGroupSex.getElements().asIterator();
						while (iter.hasNext()) {
							AbstractButton currentButton = iter.next();
							if (currentButton.getActionCommand().equals("None")) {
								btnGroupSex.setSelected(currentButton.getModel(), true);
								break;
							}
						}
					}
					//update state of ComboBox
					whetherItCanBeAddedToStudy(pid);
				}
			}
		}
	}
	
	String getSelectedStudyIUID() {
		StudyContext item = (StudyContext)comboBoxStudies.getSelectedItem();
		if(item == null) {
			return null;
		}else {
			return item.getUID();
		}
	}
	
	public HashMap<String,String> getInputs() {
		String pid = textField_pid.getText();
		String pname = textField_pname.getText();
		String dob = textField_dob.getText();
		String sex = btnGroupSex.getSelection().getActionCommand();
		if(sex.equals("Male")) {
			sex = "M";
		}else if(sex.equals("Female")) {
			sex = "F";
		}else if(sex.equals("Other")) {
			sex = "O";
		}else if(sex.equals("None")) {
			sex = null;
		}
		String study_uid = getSelectedStudyIUID();
		HashMap<String,String> info = new HashMap<>();
		info.put("PatientID", pid);
		info.put("PatientName", pname);
		info.put("DateOfBirth", dob);
		info.put("PatientSex", sex);
		
		info.put("StudyInstanceUID", study_uid);
		return info;
	}
	
	public boolean isImportNew() {
		if(chckbxAddToExisting.isSelected() && comboBoxStudies.isEnabled()) {
			return false;
		}else {
			return true;
		}
	}
	
	void initComboBox(String pid) {
		ArrayList<String> uids = db.getStudyUidList(pid);
		comboBoxStudies.removeAllItems();
		for(String uid : uids) {
			String date = db.getParticularInfoFromStudy("StudyDate", pid, uid);
			List<String> modality = db.getModalitiesInStudyRealatedAllSeries(pid, uid);
			String m = "";
			for(String i : modality) {
				m += i+",";
			}
			m = m.substring(0,m.length()-1);// remove last ","
			String studyDesc = db.getParticularInfoFromStudy("StudyDescription", pid, uid);
			String item = date+"_"+m+"_"+studyDesc;
			comboBoxStudies.addItem(new StudyContext(item, uid));
		}
	}
	
	void whetherItCanBeAddedToStudy(String pid) {
		boolean nostudy = db.getNumOfStudyInPatient(pid) == 0;
		if(!nostudy) initComboBox(pid);
		changeStateThereAreNoStudies(nostudy);
	}
	
	void changeStateThereAreNoStudies(boolean yes) {
		if(yes) {
			textField_study.setEditable(true);
			chckbxAddToExisting.setSelected(false);
			chckbxAddToExisting.setEnabled(false);
			comboBoxStudies.setEnabled(false);
			textField_study.setEnabled(true);
			textField_study.setEditable(true);
		}else {
			chckbxAddToExisting.setEnabled(true);
			if(chckbxAddToExisting.isSelected()) {
				comboBoxStudies.setEnabled(true);
				comboBoxStudies.setEditable(false);
				textField_study.setEnabled(false);
				textField_study.setEditable(false);
				
			}else {
				comboBoxStudies.setEnabled(false);
				textField_study.setEnabled(true);
				textField_study.setEditable(true);
			}
		}
		repaint();
	}
	
	class StudyContext{
		String name;
		String uid;
		StudyContext(String name, String uid){
			this.name = name;
			this.uid = uid;
		}
		
		String getUID() {
			return uid;
		}
		
		@Override
		public String toString() {
            return name;
        }
	}
}
