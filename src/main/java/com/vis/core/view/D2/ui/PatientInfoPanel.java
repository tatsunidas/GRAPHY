package com.vis.core.view.D2.ui;

import javax.swing.JPanel;
import java.awt.GridBagLayout;
import javax.swing.JLabel;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;

public class PatientInfoPanel extends JPanel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3738586364721796620L;
	private JLabel lblName;
	private JLabel lblId;
	private JLabel lblBod;
	private JLabel lblSex;

	public PatientInfoPanel() {
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		JLabel lblPatientname = new JLabel("PatientName");
		GridBagConstraints gbc_lblPatientname = new GridBagConstraints();
		gbc_lblPatientname.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientname.gridx = 0;
		gbc_lblPatientname.gridy = 0;
		add(lblPatientname, gbc_lblPatientname);
		
		lblName = new JLabel("name");
		GridBagConstraints gbc_lblName = new GridBagConstraints();
		gbc_lblName.insets = new Insets(0, 0, 5, 0);
		gbc_lblName.gridx = 1;
		gbc_lblName.gridy = 0;
		add(lblName, gbc_lblName);
		
		JLabel lblPatientid = new JLabel("PatientID");
		GridBagConstraints gbc_lblPatientid = new GridBagConstraints();
		gbc_lblPatientid.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientid.gridx = 0;
		gbc_lblPatientid.gridy = 1;
		add(lblPatientid, gbc_lblPatientid);
		
		lblId = new JLabel("id");
		GridBagConstraints gbc_lblId = new GridBagConstraints();
		gbc_lblId.insets = new Insets(0, 0, 5, 0);
		gbc_lblId.gridx = 1;
		gbc_lblId.gridy = 1;
		add(lblId, gbc_lblId);
		
		JLabel lblPatientbirthofdate = new JLabel("PatientBirthOfDate");
		GridBagConstraints gbc_lblPatientbirthofdate = new GridBagConstraints();
		gbc_lblPatientbirthofdate.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientbirthofdate.gridx = 0;
		gbc_lblPatientbirthofdate.gridy = 2;
		add(lblPatientbirthofdate, gbc_lblPatientbirthofdate);
		
		lblBod = new JLabel("bod");
		GridBagConstraints gbc_lblBod = new GridBagConstraints();
		gbc_lblBod.insets = new Insets(0, 0, 5, 0);
		gbc_lblBod.gridx = 1;
		gbc_lblBod.gridy = 2;
		add(lblBod, gbc_lblBod);
		
		JLabel lblPatientsex = new JLabel("PatientSex");
		GridBagConstraints gbc_lblPatientsex = new GridBagConstraints();
		gbc_lblPatientsex.insets = new Insets(0, 0, 0, 5);
		gbc_lblPatientsex.gridx = 0;
		gbc_lblPatientsex.gridy = 3;
		add(lblPatientsex, gbc_lblPatientsex);
		
		lblSex = new JLabel("sex");
		GridBagConstraints gbc_lblSex = new GridBagConstraints();
		gbc_lblSex.gridx = 1;
		gbc_lblSex.gridy = 3;
		add(lblSex, gbc_lblSex);
		
	}
	
	public void setPatientInfo(String patID, String patName, String bod, String sex){
		lblId.setText(patID);
		lblName.setText(patName);
		lblBod.setText(bod);
		lblSex.setText(sex);
		repaint();
	}
}
