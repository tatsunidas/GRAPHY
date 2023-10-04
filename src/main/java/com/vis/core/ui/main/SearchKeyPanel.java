package com.vis.core.ui.main;

import javax.swing.JToolBar;

import org.jdatepicker.impl.JDatePanelImpl;
import org.jdatepicker.impl.JDatePickerImpl;
import org.jdatepicker.impl.UtilDateModel;

import com.vis.core.util.PropertiesUtil;

import java.awt.Dimension;

import javax.swing.JPanel;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import javax.swing.JTextField;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.TimeZone;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.JScrollPane;

public class SearchKeyPanel extends JToolBar{

	private static final long serialVersionUID = 3903985270243893308L;
	private JTextField patIDField;
	private JTextField pNameField;
	private JCheckBox chckbxToday;
	private ModalitySelect ms;
	private UtilDateModel pickModelFrom;
	private UtilDateModel pickModelTo;
	private Properties pickerProperties;
	private JDatePanelImpl datePanelFrom;
	private JDatePanelImpl datePanelTo;
	JDatePickerImpl datePickerFrom;
	JDatePickerImpl datePickerTo;
	
	public SearchKeyPanel(ModalitySelect ms) {
		
		this.ms = ms;//modalities
		setDatePicker();
		
		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane);
		
		JPanel panel = new JPanel();
		scrollPane.setViewportView(panel);
		java.awt.Dimension dim = new Dimension(550, 180);
		scrollPane.setPreferredSize(dim);
		scrollPane.setMinimumSize(dim);
	    scrollPane.setMaximumSize(dim);
		
		GridBagLayout gbl_panel = new GridBagLayout();
		gbl_panel.columnWidths = new int[]{30, 66, 202, 0, 10, 0, 0};
		gbl_panel.rowHeights = new int[]{28, 28, 26, 28, 0, 0};
		gbl_panel.columnWeights = new double[]{0.0, 0.0, 1.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		gbl_panel.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		panel.setLayout(gbl_panel);
		
		JLabel lblPatientId = new JLabel("Patient ID*");
		GridBagConstraints gbc_lblPatientId = new GridBagConstraints();
		gbc_lblPatientId.anchor = GridBagConstraints.EAST;
		gbc_lblPatientId.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientId.gridx = 1;
		gbc_lblPatientId.gridy = 0;
		panel.add(lblPatientId, gbc_lblPatientId);
		
		patIDField = new JTextField();
		patIDField.getDocument().addDocumentListener(new TextSearchListener(patIDField, this));
		GridBagConstraints gbc_patIDField = new GridBagConstraints();
		gbc_patIDField.fill = GridBagConstraints.HORIZONTAL;
		gbc_patIDField.insets = new Insets(0, 0, 5, 5);
		gbc_patIDField.gridx = 2;
		gbc_patIDField.gridy = 0;
		panel.add(patIDField, gbc_patIDField);
		patIDField.setColumns(10);
		
		JLabel lblPatientName = new JLabel("Patient Name");
		GridBagConstraints gbc_lblPatientName = new GridBagConstraints();
		gbc_lblPatientName.anchor = GridBagConstraints.EAST;
		gbc_lblPatientName.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientName.gridx = 1;
		gbc_lblPatientName.gridy = 1;
		panel.add(lblPatientName, gbc_lblPatientName);
		
		pNameField = new JTextField();
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.insets = new Insets(0, 0, 5, 5);
		gbc_textField.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField.gridx = 2;
		gbc_textField.gridy = 1;
		panel.add(pNameField, gbc_textField);
		pNameField.setColumns(10);
		
		JLabel lblStudyDate = new JLabel("Study Date");
		GridBagConstraints gbc_lblStudyDate = new GridBagConstraints();
		gbc_lblStudyDate.anchor = GridBagConstraints.EAST;
		gbc_lblStudyDate.insets = new Insets(0, 0, 5, 5);
		gbc_lblStudyDate.gridx = 1;
		gbc_lblStudyDate.gridy = 2;
		panel.add(lblStudyDate, gbc_lblStudyDate);
		
		// Don't know about the formatter, but there it is...
		datePickerFrom = new JDatePickerImpl(datePanelFrom, new DateLabelFormatter());
		datePickerFrom.setTextEditable(true);
		GridBagConstraints gbc_datePickerFrom = new GridBagConstraints();
		gbc_datePickerFrom.fill = GridBagConstraints.HORIZONTAL;
		gbc_datePickerFrom.insets = new Insets(0, 0, 5, 5);
		gbc_datePickerFrom.gridx = 2;
		gbc_datePickerFrom.gridy = 2;
		panel.add(datePickerFrom, gbc_datePickerFrom);
		
		JLabel label_1 = new JLabel(" - ");
		GridBagConstraints gbc_label_1 = new GridBagConstraints();
		gbc_label_1.insets = new Insets(0, 0, 5, 5);
		gbc_label_1.gridx = 3;
		gbc_label_1.gridy = 2;
		panel.add(label_1, gbc_label_1);
		
		datePickerTo = new JDatePickerImpl(datePanelTo, new DateLabelFormatter());
		datePickerTo.setTextEditable(true);
		GridBagConstraints gbc_datePickerTo = new GridBagConstraints();
		gbc_datePickerTo.fill = GridBagConstraints.HORIZONTAL;
		gbc_datePickerTo.insets = new Insets(0, 0, 5, 5);
		gbc_datePickerTo.gridx = 4;
		gbc_datePickerTo.gridy = 2;
		panel.add(datePickerTo, gbc_datePickerTo);
		
		chckbxToday = new JCheckBox("Today");
		chckbxToday.setSelected(true);
		GridBagConstraints gbc_chckbxToday = new GridBagConstraints();
		gbc_chckbxToday.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxToday.gridx = 2;
		gbc_chckbxToday.gridy = 3;
		panel.add(chckbxToday, gbc_chckbxToday);
		chckbxToday.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent arg0) {
				if(chckbxToday.isSelected()){
					//set studydate to today
					Calendar cal = Calendar.getInstance(TimeZone.getDefault());
					cal.setTime(getTodayDate());//assurance
					datePickerFrom.getModel().setYear(cal.get(Calendar.YEAR));
					datePickerFrom.getModel().setMonth(cal.get(Calendar.MONTH));
					datePickerFrom.getModel().setDay(cal.get(Calendar.DATE));
					datePickerFrom.getModel().setSelected(true);
					datePickerFrom.getComponent(1).setEnabled(false);
					datePickerTo.getComponent(1).setEnabled(false);
					datePickerFrom.setTextEditable(false);
					datePickerTo.setTextEditable(false);
				}else {
					datePickerFrom.getComponent(1).setEnabled(true);
					datePickerTo.getComponent(1).setEnabled(true);
					datePickerFrom.setTextEditable(true);
					datePickerTo.setTextEditable(true);
				}
			}
		});
		
		JButton searchButton = new JButton("Search");
		GridBagConstraints gbc_searchButton = new GridBagConstraints();
		gbc_searchButton.insets = new Insets(0, 0, 0, 5);
		gbc_searchButton.fill = GridBagConstraints.HORIZONTAL;
		gbc_searchButton.gridwidth = 3;
		gbc_searchButton.gridx = 2;
		gbc_searchButton.gridy = 4;
		panel.add(searchButton, gbc_searchButton);
		searchButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				searchDBUsingThisConditions();
			}
		});
	}
	
	private void setDatePicker() {
		pickModelFrom = new UtilDateModel();
		pickModelTo = new UtilDateModel();
		//model.setDate(20,04,2014);
		// Need this...
		pickerProperties = new Properties();
		pickerProperties.put("text.today", "Today");
		pickerProperties.put("text.month", "Month");
		pickerProperties.put("text.year", "Year");
		datePanelFrom = new JDatePanelImpl(pickModelFrom, pickerProperties);
		datePanelTo = new JDatePanelImpl(pickModelTo, pickerProperties);
	}

	public String getPatID() {
		String val = patIDField.getText();
		if(val.equals("")) {
			val = null;
		}
		if(val != null) {
			int numOfPad = Integer.valueOf(PropertiesUtil.getPropValueFrom("conf/graphy.properties", "NumOfPadForPatientID"));
			for(int i=0;i<numOfPad;i++) {
				val = "0"+val;
			}
		}
		return val;
	}
	
	public String getPatName() {
		String val = pNameField.getText();
		if(val.equals("")) {
			val = null;
		}
		if(val != null && val.trim().length() != 0) {
			return val;
		}
		return null;
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
	
	public String getTodayString() {
		Calendar cl = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
		return sdf.format(cl.getTime());
	}
	
	//picker
	public Date getTodayDate() {
		Calendar cl = Calendar.getInstance();
		return cl.getTime();
	}
	
	public void searchDBUsingThisConditions(){
		/*
		 * pay attention "0"padding patientID
		 */
		String patID = validateInputString(getPatID());//do not need trim?
		String patName = validateInputString(getPatName());//do not need trim?
		String from = validateInputString(getTermFrom());
		String to = validateInputString(getTermTo());
		if(from != null) {
			from = from.trim();
		}else if(to != null) {
			to = to.trim();
		}
		if(isTodaySelected()) {
			from = getTodayString();
			to = null;
		}
		
		new QueryRetrieve().queryAndUpadateTreeTableByTextSearch(patID, patName, from, to, ms.selectedModalities());

	}
	
	public HashMap<String, Object> getCurrentSearchConditions(){
		HashMap<String, Object> keys = new HashMap<String, Object>();
		String patID = validateInputString(getPatID());//do not need trim?
		String patName = validateInputString(getPatName());//do not need trim?
		String from = validateInputString(getTermFrom());
		String to = validateInputString(getTermTo());
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
		keys.put("Modalities",  ms.selectedModalities());//ArrayList<String>
		return keys;
	}
	
	private String validateInputString(String input) {
		if(input != null) {
			String lineWithoutSpaces = input.replaceAll("\\s+","");
			if(lineWithoutSpaces.equals("")) {
				return null;
			}else {
				return input;
			}
		}
		return null;
	}
	
	public class DateLabelFormatter extends AbstractFormatter {

	    /**
		 * https://stackoverflow.com/questions/35264674/how-to-make-jdatepicker-text-field-formatted-for-input
		 */
		private static final long serialVersionUID = 8630567706572633049L;
		private String datePattern = "yyyy/MM/dd";
	    private SimpleDateFormat dateFormatter = new SimpleDateFormat(datePattern);

	    @Override
		public Object stringToValue(String text) throws ParseException {
	    	/*
	    	 * user should input correctly yyyy/MM/dd
	    	 */
	    	if(text.length() == 10) {
				Calendar cal = Calendar.getInstance();
				cal.setTime((Date) dateFormatter.parseObject(text));
				return cal;
	    	}else {
	    		return null;
	    	}
		}

	    @Override
	    public String valueToString(Object value) throws ParseException {
	    	if(value instanceof Date) {
	    		Date currentInput = (Date)value;
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
