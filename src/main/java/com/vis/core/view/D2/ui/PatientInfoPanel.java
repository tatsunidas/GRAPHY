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
package com.vis.core.view.D2.ui;

import javax.swing.JPanel;
import java.awt.GridBagLayout;
import javax.swing.JLabel;

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
