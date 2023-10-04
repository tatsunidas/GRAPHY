package com.vis.core.ui.main;

import javax.swing.JToolBar;

import com.vis.core.ui.main.dcmtreetable.DICOMNode;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import javax.swing.JLabel;

import java.awt.Component;
import java.util.HashMap;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.Box;

@SuppressWarnings("serial")
public class PatientInfoPanel extends JToolBar{
	
	private JLabel lblPatientName;
	private JLabel lblBirthOfDate;
	private JLabel lblSex;
	private JLabel lblAge;
	private JLabel lblModality;
	private JLabel lblStudyDate;
	private JLabel lblSelectedTreeTable;
	private JLabel lblPatientid;
	private Component horizontalStrut;

	public PatientInfoPanel(){
		
		JPanel panel = new JPanel();
		JScrollPane scrollPane = new JScrollPane(panel);
		add(scrollPane);
		GridBagLayout gbl_panel = new GridBagLayout();
		gbl_panel.columnWidths = new int[]{109, 0, 0};
		gbl_panel.rowHeights = new int[]{0, 18, 0, 0, 0, 0, 0, 0, 0};
		gbl_panel.columnWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
		gbl_panel.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		panel.setLayout(gbl_panel);
		
		lblSelectedTreeTable = new JLabel("Selected DicomNode");
		GridBagConstraints gbc_lblSelectedTreeTable = new GridBagConstraints();
		gbc_lblSelectedTreeTable.anchor = GridBagConstraints.NORTHWEST;
		gbc_lblSelectedTreeTable.insets = new Insets(0, 0, 5, 5);
		gbc_lblSelectedTreeTable.gridx = 0;
		gbc_lblSelectedTreeTable.gridy = 0;
		panel.add(lblSelectedTreeTable, gbc_lblSelectedTreeTable);
		
		horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut.gridx = 1;
		gbc_horizontalStrut.gridy = 0;
		panel.add(horizontalStrut, gbc_horizontalStrut);
		
		lblPatientName = new JLabel("Patient Name");
		GridBagConstraints gbc_lblPatientName = new GridBagConstraints();
		gbc_lblPatientName.anchor = GridBagConstraints.NORTHWEST;
		gbc_lblPatientName.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientName.gridx = 0;
		gbc_lblPatientName.gridy = 1;
		panel.add(lblPatientName, gbc_lblPatientName);
		
		lblPatientid = new JLabel("PatientID");
		GridBagConstraints gbc_lblPatientid = new GridBagConstraints();
		gbc_lblPatientid.anchor = GridBagConstraints.WEST;
		gbc_lblPatientid.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientid.gridx = 0;
		gbc_lblPatientid.gridy = 2;
		panel.add(lblPatientid, gbc_lblPatientid);
		
		lblStudyDate = new JLabel("Study date");
		GridBagConstraints gbc_lblStudyDate = new GridBagConstraints();
		gbc_lblStudyDate.insets = new Insets(0, 0, 5, 5);
		gbc_lblStudyDate.anchor = GridBagConstraints.NORTHWEST;
		gbc_lblStudyDate.gridx = 0;
		gbc_lblStudyDate.gridy = 3;
		panel.add(lblStudyDate, gbc_lblStudyDate);
		
		lblModality = new JLabel("Modality");
		GridBagConstraints gbc_lblModality = new GridBagConstraints();
		gbc_lblModality.anchor = GridBagConstraints.NORTHWEST;
		gbc_lblModality.insets = new Insets(0, 0, 5, 5);
		gbc_lblModality.gridx = 0;
		gbc_lblModality.gridy = 4;
		panel.add(lblModality, gbc_lblModality);
		
		lblBirthOfDate = new JLabel("Birth of date");
		GridBagConstraints gbc_lblBirthOfDate = new GridBagConstraints();
		gbc_lblBirthOfDate.anchor = GridBagConstraints.NORTHWEST;
		gbc_lblBirthOfDate.insets = new Insets(0, 0, 5, 5);
		gbc_lblBirthOfDate.gridx = 0;
		gbc_lblBirthOfDate.gridy = 5;
		panel.add(lblBirthOfDate, gbc_lblBirthOfDate);
		
		lblSex = new JLabel("Sex");
		GridBagConstraints gbc_lblSex = new GridBagConstraints();
		gbc_lblSex.anchor = GridBagConstraints.NORTHWEST;
		gbc_lblSex.insets = new Insets(0, 0, 5, 5);
		gbc_lblSex.gridx = 0;
		gbc_lblSex.gridy = 6;
		panel.add(lblSex, gbc_lblSex);
		
		lblAge = new JLabel("Age");
		GridBagConstraints gbc_lblAge = new GridBagConstraints();
		gbc_lblAge.anchor = GridBagConstraints.NORTHWEST;
		gbc_lblAge.insets = new Insets(0, 0, 0, 5);
		gbc_lblAge.gridx = 0;
		gbc_lblAge.gridy = 7;
		panel.add(lblAge, gbc_lblAge);
		
	}
	
	public void setInfoset(HashMap<String,String> infoset) {
		lblSelectedTreeTable.setText("CurrentTable : "+infoset.get("Nickname"));
		lblPatientid.setText("PatientID : "+infoset.get("PatientID"));
		lblPatientName.setText("PatientName : "+infoset.get("PatientName"));
		lblStudyDate.setText("StudyDate : "+infoset.get("StudyDate"));
		lblModality.setText("Modality : "+infoset.get("Modality"));
		lblBirthOfDate.setText("BirthOfDate : "+infoset.get(DICOMNode.BirthDate));
		lblSex.setText("Sex : "+infoset.get("PatientSex"));
		lblAge.setText("Age : "+infoset.get("PatientAge"));
		revalidate();
		repaint();
	}
}
