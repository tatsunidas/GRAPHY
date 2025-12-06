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
package com.vis.core.ui.main;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.border.EtchedBorder;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jdatepicker.impl.JDatePanelImpl;
import org.jdatepicker.impl.JDatePickerImpl;
import org.jdatepicker.impl.UtilDateModel;

import com.vis.core.util.StringUtils;
import com.vis.dicom.Modality;

/**
 * 
 * @author tatsunidas
 *
 */
public class SearchToolBar extends JToolBar{
	/**
	 * 
	 */
	private static final long serialVersionUID = 2534156319392459059L;
	
	MainScreen dbScreen;
	
	private JPanel panel;
	private JButton searchBtn;
	private JTextField patIDField;
	private JTextField pNameField;
	private JCheckBox chckbxToday;
	private JDatePickerImpl datePickerFrom;
	private JDatePickerImpl datePickerTo;
	private HashMap<Modality, JCheckBox> modalities;
	
	private final String datePattern = "yyyy/MM/dd";
	private SimpleDateFormat dateFormatter = new SimpleDateFormat(datePattern);
	
	public SearchToolBar(MainScreen dbScreen) {
		this.dbScreen = dbScreen;
		init();
	}
	
	private void init() {
			
		panel = new JPanel();
		int hgap = 3;
		int vgap = hgap;
		panel.setLayout(new FlowLayout(FlowLayout.LEFT, hgap, vgap));
		
		setSearchButton();
		addSeparator();
		setPatientTxtField();
		addSeparator();
		setDatePicker();
		addSeparator();
		setModalities();
		
		// after above, add jscrollpane.
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setViewportView(panel);
		scrollPane.setPreferredSize(new Dimension(300/*dummy*/, datePickerFrom.getPreferredSize().height*2));
		add(scrollPane);
	}
	
	/*
	 * 
	 */
	private void setSearchButton() {
		searchBtn = new JButton("Search");
		searchBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				new Thread(() -> {
					dbScreen.searchCurrentConditions();
				}).start();
			}
		});
		Font font = new Font(Font.SANS_SERIF, Font.BOLD, 14);
		searchBtn.setFont(font);
		panel.add(searchBtn);
	}
	
	/*
	 * add patient info search txt fields
	 */
	private void setPatientTxtField(){
		JPanel pPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 1, 1));
		pPanel.setBorder(new EtchedBorder(EtchedBorder.RAISED));
		int col_size = 10;
		JLabel lblPatientId = new JLabel(" Patient ID :");
		pPanel.add(lblPatientId);
		patIDField = new JTextField();
		patIDField.setColumns(col_size);
		patIDField.getDocument().addDocumentListener(createTextSearchListener());
		pPanel.add(patIDField);
		
		JLabel lblPatientName = new JLabel(" Patient Name :");
		pPanel.add(lblPatientName);
		pNameField = new JTextField();
		pNameField.setColumns(col_size);
		//pNameField.getDocument().addDocumentListener(createTextSearchListener());
		pPanel.add(pNameField);
		panel.add(pPanel);
	}
	
	private void setDatePicker() {
		
		JPanel dPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 1, 1));
		dPanel.setBorder(new EtchedBorder(EtchedBorder.RAISED));
		
		UtilDateModel pickModelFrom = new UtilDateModel();
		UtilDateModel pickModelTo = new UtilDateModel();
		//example, model.setDate(20,04,2014);
		// Need this...
		Properties pickerProperties = new Properties();
		pickerProperties.put("text.today", "Today");
		pickerProperties.put("text.month", "Month");
		pickerProperties.put("text.year", "Year");
		JDatePanelImpl datePanelFrom = new JDatePanelImpl(pickModelFrom, pickerProperties);
		JDatePanelImpl datePanelTo = new JDatePanelImpl(pickModelTo, pickerProperties);
		
		datePickerTo = new JDatePickerImpl(datePanelTo, new DateLabelFormatter());
		datePickerFrom = new JDatePickerImpl(datePanelFrom, new DateLabelFormatter());
		
		datePickerTo.setTextEditable(true);
		datePickerFrom.setTextEditable(true);
		
		chckbxToday = new JCheckBox("Today");
		Font font = new Font(Font.SANS_SERIF, Font.BOLD, 12);
		chckbxToday.setFont(font);
		chckbxToday.setSelected(true);
		chckbxToday.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent arg0) {
				if(chckbxToday.isSelected()){
					// grayout, not editable
					datePickerFrom.getComponent(1).setEnabled(false);
					datePickerTo.getComponent(1).setEnabled(false);
					datePickerFrom.setTextEditable(false);
					datePickerTo.setTextEditable(false);
				}else {
					// set editable
					datePickerFrom.getComponent(1).setEnabled(true);
					datePickerTo.getComponent(1).setEnabled(true);
					datePickerFrom.setTextEditable(true);
					datePickerTo.setTextEditable(true);
				}
			}
		});
		panel.add(chckbxToday);
		dPanel.add(new JLabel(" From "));
		dPanel.add(datePickerFrom);
		dPanel.add(new JLabel(" To "));
		dPanel.add(datePickerTo);
		panel.add(dPanel);
	}
	
	private void setModalities() {
		JPanel mPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 1, 1));
		mPanel.setBorder(new EtchedBorder(EtchedBorder.RAISED));
		modalities = new HashMap<>();
		for(Modality m: Modality.values()) {
			modalities.put(m, new JCheckBox(m.name()));
			mPanel.add(modalities.get(m));
		}
		panel.add(mPanel);
	}
	
	public String getString(JTextField tf) {
		String val = tf.getText();
		if(val == null) {
			return null;
		}
		val = StringUtils.trimWhitespace(val);
		if(val.equals("") || val.isBlank() || val.isEmpty()) {
			val = null;
		}
		return val;
	}
	
	public String getTermFrom() {
		String val = datePickerFrom.getJFormattedTextField().getText();
		if (val.equals("")) {
			val = null;
		}
		return val;
	}

	public String getTermTo() {
		String val = datePickerTo.getJFormattedTextField().getText();
		if (val.equals("")) {
			val = null;
		}
		return val;
	}
	
	public boolean isTodaySelected() {
		return chckbxToday.isSelected();
	}
		
	private String getTodayString() {
		Calendar cl = Calendar.getInstance();
		return dateFormatter.format(cl.getTime());
	}
	
	private DocumentListener createTextSearchListener() {
		return new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				queryAndUpdateTreeTableByDocumentListnerWithPID();
			}
			
			@Override
			public void insertUpdate(DocumentEvent e) {
				queryAndUpdateTreeTableByDocumentListnerWithPID();
			}
			
			@Override
			public void changedUpdate(DocumentEvent e) {
				queryAndUpdateTreeTableByDocumentListnerWithPID();
			}
		};
	}
	
	public ArrayList<String> getSelectedModalities(){
		ArrayList<String> selected = new ArrayList<>();
		for(Modality m: modalities.keySet()) {
			if(modalities.get(m).isSelected()) {
				selected.add(m.name());
			}
		}
		return selected;
	}
	
	/**
	 * for DocumentListener.
	 */
	private void queryAndUpdateTreeTableByDocumentListnerWithPID() {
		String pid = patIDField.getText();
		if (pid != null && !pid.trim().isBlank()) {
			dbScreen.searchCurrentConditions();
		}
	}
	
	
	public boolean nullSearchKeys() {
		String patID = getString(patIDField);
		String patName = getString(pNameField);
		String from = getTermFrom();
		String to = getTermTo();
		if(isTodaySelected()) {
			from = getTodayString();
			to = null;
		}
		ArrayList<String> m = getSelectedModalities();
		if(patID == null && patName==null && from==null && to ==null && m.isEmpty()) {
			return true;
		}else {
			return false;
		}
	}
	
	public HashMap<String, Object> getCurrentSearchConditions(){
		HashMap<String, Object> keys = new HashMap<String, Object>();
		String patID = getString(patIDField);
		String patName = getString(pNameField);
		String from = getTermFrom();
		String to = getTermTo();
		if(from != null) {
			from = from.trim();
		}else if(to != null) {
			to = to.trim();
		}
		if(isTodaySelected()) {
			from = getTodayString();
			to = null;
		}
		keys.put("PatientID", patID);
		keys.put("PatientName", patName);
		keys.put("From", from);
		keys.put("To", to);
		keys.put("Modalities",  getSelectedModalities());//ArrayList<String>
		return keys;
	}
	
	public class DateLabelFormatter extends AbstractFormatter {

		/**
		 * https://stackoverflow.com/questions/35264674/how-to-make-jdatepicker-text-field-formatted-for-input
		 */
		private static final long serialVersionUID = 8630567706572633049L;

		@Override
		public Object stringToValue(String text) throws ParseException {
			/*
			 * user should input correctly yyyy/MM/dd
			 */
			if (text.length() == 10) {
				Calendar cal = Calendar.getInstance();
				cal.setTime((Date) dateFormatter.parseObject(text));
				return cal;
			} else {
				return null;
			}
		}

		@Override
		public String valueToString(Object value) throws ParseException {
			if (value instanceof Date) {
				Date currentInput = (Date) value;
				Calendar cal = Calendar.getInstance();
				cal.setTime(currentInput);
				return dateFormatter.format(cal.getTime());
			}
			if (value instanceof Calendar) {
				Calendar cal = (Calendar) value;
				return dateFormatter.format(cal.getTime());
			}
			return "";
		}
	}
}
