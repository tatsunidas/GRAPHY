package com.vis.core.ui.dialog;

import java.awt.Dimension;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import javax.swing.SwingConstants;

import com.vis.core.ui.listener.AlphanumericTextKeyListener;
import com.vis.core.ui.listener.DateTextKeyListener;

@SuppressWarnings("serial")
public class ImportNonDicomImagePanel extends JPanel{
	
	private ButtonGroup btnGroupMode;
	private ButtonGroup btnGroupSex;
	
	private JTextField textField_pname;//name
	private JTextField textField_pid;//id
	private JTextField textField_dob;//date of birth
	private JTextField textField_studyDesc;//to import new study
	private JTextField textField_seriesDesc;
	
	private JRadioButton rdbtnMale;
	private JRadioButton rdbtnFemale;
	private JRadioButton rdbtnOther;
	private JRadioButton rdbtnImportToStudy;
	private JRadioButton rdbtnImportNew;
	
	//test
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		JFrame f = new JFrame();
		f.setPreferredSize(new Dimension(500,300));
		f.getContentPane().add(new ImportNonDicomImagePanel());
		f.pack();
		f.setVisible(true);
	}

	public ImportNonDicomImagePanel() {
		
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 1.0, 0.0, 1.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		rdbtnImportToStudy = new JRadioButton("Import to selected Study");
		rdbtnImportToStudy.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if(!isVisible()) {
					return;
				}
				if(textField_pid == null) {
					return;
				}
				textField_pid.setEditable(false);
				textField_pname.setEditable(false);
				textField_dob.setEditable(false);
				rdbtnMale.setEnabled(false);
				rdbtnFemale.setEnabled(false);
				rdbtnOther.setEnabled(false);
				textField_studyDesc.setEditable(false);
				repaint();
			}
		});
		rdbtnImportToStudy.setSelected(true);
		rdbtnImportToStudy.setToolTipText("Integrate selected study.(select a study node in main window.)");
		rdbtnImportToStudy.setActionCommand("ImportToStudy");
		rdbtnImportToStudy.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_rdbtnImportToStudy = new GridBagConstraints();
		gbc_rdbtnImportToStudy.anchor = GridBagConstraints.WEST;
		gbc_rdbtnImportToStudy.insets = new Insets(0, 0, 5, 5);
		gbc_rdbtnImportToStudy.gridx = 0;
		gbc_rdbtnImportToStudy.gridy = 0;
		add(rdbtnImportToStudy, gbc_rdbtnImportToStudy);
		
		rdbtnImportNew = new JRadioButton("Import as new study");
		rdbtnImportNew.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if(!isVisible()) {
					return;
				}
				if(textField_pid == null) {
					return;
				}
				textField_pid.setEditable(true);
				textField_pname.setEditable(true);
				textField_dob.setEditable(true);
				rdbtnMale.setEnabled(true);
				rdbtnFemale.setEnabled(true);
				rdbtnOther.setEnabled(true);
				textField_studyDesc.setEditable(true);
				repaint();
			}
		});
		rdbtnImportNew.setToolTipText("Create new study");
		rdbtnImportNew.setActionCommand("ImportNew");
		rdbtnImportNew.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_rdbtnImportNew = new GridBagConstraints();
		gbc_rdbtnImportNew.anchor = GridBagConstraints.WEST;
		gbc_rdbtnImportNew.insets = new Insets(0, 0, 5, 5);
		gbc_rdbtnImportNew.gridx = 1;
		gbc_rdbtnImportNew.gridy = 0;
		add(rdbtnImportNew, gbc_rdbtnImportNew);
		
		btnGroupMode = new ButtonGroup();
		btnGroupMode.add(rdbtnImportToStudy);
		btnGroupMode.add(rdbtnImportNew);
		
		JLabel lblPatientname = new JLabel("PatientName*");
		GridBagConstraints gbc_lblPatientname = new GridBagConstraints();
		gbc_lblPatientname.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientname.anchor = GridBagConstraints.EAST;
		gbc_lblPatientname.gridx = 0;
		gbc_lblPatientname.gridy = 1;
		add(lblPatientname, gbc_lblPatientname);
		
		textField_pname = new JTextField();
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.gridwidth = 2;
		gbc_textField.insets = new Insets(0, 0, 5, 5);
		gbc_textField.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField.gridx = 1;
		gbc_textField.gridy = 1;
		add(textField_pname, gbc_textField);
		textField_pname.setColumns(10);
		
		
		JLabel lblPatientid = new JLabel("PatientID*");
		GridBagConstraints gbc_lblPatientid = new GridBagConstraints();
		gbc_lblPatientid.anchor = GridBagConstraints.EAST;
		gbc_lblPatientid.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientid.gridx = 0;
		gbc_lblPatientid.gridy = 2;
		add(lblPatientid, gbc_lblPatientid);
		
		textField_pid = new JTextField();
		GridBagConstraints gbc_textField_1 = new GridBagConstraints();
		gbc_textField_1.gridwidth = 2;
		gbc_textField_1.insets = new Insets(0, 0, 5, 5);
		gbc_textField_1.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField_1.gridx = 1;
		gbc_textField_1.gridy = 2;
		add(textField_pid, gbc_textField_1);
		textField_pid.setColumns(10);
		
		JLabel lblPatientdateofbirth = new JLabel("DateOfBirth(yyyy/MM/dd)*");
		GridBagConstraints gbc_lblPatientdateofbirth = new GridBagConstraints();
		gbc_lblPatientdateofbirth.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientdateofbirth.anchor = GridBagConstraints.EAST;
		gbc_lblPatientdateofbirth.gridx = 0;
		gbc_lblPatientdateofbirth.gridy = 3;
		add(lblPatientdateofbirth, gbc_lblPatientdateofbirth);
		
		textField_dob = new JTextField();
		GridBagConstraints gbc_textField_2 = new GridBagConstraints();
		gbc_textField_2.gridwidth = 2;
		gbc_textField_2.insets = new Insets(0, 0, 5, 5);
		gbc_textField_2.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField_2.gridx = 1;
		gbc_textField_2.gridy = 3;
		add(textField_dob, gbc_textField_2);
		textField_dob.setColumns(10);
		
		
		JLabel lblPatientSex = new JLabel("PatientSex");
		lblPatientSex.setToolTipText("Select PatientSex");
		GridBagConstraints gbc_lblPatientSex = new GridBagConstraints();
		gbc_lblPatientSex.insets = new Insets(0, 0, 5, 5);
		gbc_lblPatientSex.anchor = GridBagConstraints.EAST;
		gbc_lblPatientSex.gridx = 0;
		gbc_lblPatientSex.gridy = 4;
		add(lblPatientSex, gbc_lblPatientSex);
		
		JPanel panel = new JPanel();
		GridBagConstraints gbc_panel = new GridBagConstraints();
		gbc_panel.insets = new Insets(0, 0, 5, 5);
		gbc_panel.fill = GridBagConstraints.HORIZONTAL;
		gbc_panel.gridx = 1;
		gbc_panel.gridy = 4;
		add(panel, gbc_panel);
		
		rdbtnMale = new JRadioButton("Male");
		rdbtnMale.setSelected(true);
		rdbtnMale.setActionCommand("Male");
		panel.add(rdbtnMale);
		
		rdbtnFemale = new JRadioButton("Female");
		rdbtnFemale.setActionCommand("Female");
		panel.add(rdbtnFemale);
		
		rdbtnOther = new JRadioButton("Other");
		rdbtnOther.setActionCommand("Other");
		panel.add(rdbtnOther);
		
		btnGroupSex = new ButtonGroup();
		btnGroupSex.add(rdbtnMale);
		btnGroupSex.add(rdbtnFemale);
		btnGroupSex.add(rdbtnOther);
		
		JLabel lblStudyDescLabel = new JLabel("StudyDescription");
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel.gridx = 0;
		gbc_lblNewLabel.gridy = 5;
		add(lblStudyDescLabel, gbc_lblNewLabel);
		
		textField_studyDesc = new JTextField();
		GridBagConstraints gbc_textField_3 = new GridBagConstraints();
		gbc_textField_3.insets = new Insets(0, 0, 5, 5);
		gbc_textField_3.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField_3.gridx = 1;
		gbc_textField_3.gridy = 5;
		add(textField_studyDesc, gbc_textField_3);
		textField_studyDesc.setColumns(10);
		textField_studyDesc.addKeyListener(new AlphanumericTextKeyListener(64, null));
		
		JLabel lblSeriesdescription = new JLabel("SeriesDescription");
		GridBagConstraints gbc_lblSeriesdescription = new GridBagConstraints();
		gbc_lblSeriesdescription.insets = new Insets(0, 0, 5, 5);
		gbc_lblSeriesdescription.anchor = GridBagConstraints.EAST;
		gbc_lblSeriesdescription.gridx = 0;
		gbc_lblSeriesdescription.gridy = 6;
		add(lblSeriesdescription, gbc_lblSeriesdescription);
		
		textField_seriesDesc = new JTextField();
		GridBagConstraints gbc_textField_4 = new GridBagConstraints();
		gbc_textField_4.insets = new Insets(0, 0, 5, 0);
		gbc_textField_4.gridwidth = 3;
		gbc_textField_4.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField_4.gridx = 1;
		gbc_textField_4.gridy = 6;
		add(textField_seriesDesc, gbc_textField_4);
		textField_seriesDesc.setColumns(10);
		textField_seriesDesc.addKeyListener(new AlphanumericTextKeyListener(64, null));
		
		//listener
		AlphanumericTextKeyListener pnameTextListener = new AlphanumericTextKeyListener(64, AlphanumericTextKeyListener.pname_acceptables);
		AlphanumericTextKeyListener pidTextListener = new AlphanumericTextKeyListener(64, AlphanumericTextKeyListener.pid_acceptables);
		DateTextKeyListener dobTextListener = new DateTextKeyListener();
		
		textField_pid.addKeyListener(pidTextListener);
		
		textField_dob.addKeyListener(dobTextListener);
		textField_pname.addKeyListener(pnameTextListener);
		
		/*
		 * when starting up, set "import to study" mode
		 */
		textField_pid.setEditable(false);
		textField_pname.setEditable(false);
		textField_dob.setEditable(false);
		rdbtnMale.setEnabled(false);
		rdbtnFemale.setEnabled(false);
		rdbtnOther.setEnabled(false);
		textField_studyDesc.setEditable(false);
	}
	
	/*
	 * Mode1 actCmd:ImportToStudy
	 * Mode2:ImportNew
	 */
	String getSelectedMode(){
		String selectedCommands = this.btnGroupMode.getSelection().getActionCommand();
		return selectedCommands;
	}
	
	public boolean doImportToStudy() {
		if(getSelectedMode().equals("ImportToStudy")) {
			return true;
		}else {
			return false;
		}
	}
	
	boolean doImportNew() {
		if(getSelectedMode().equals("ImportNew")) {
			return true;
		}else {
			return false;
		}
	}
	
	public HashMap<String,String> getInputs(){
		HashMap<String,String> inputs = new HashMap<>();
		inputs.put("PatientName", textField_pname.getText());
		inputs.put("PatientID", textField_pid.getText());
		inputs.put("BirthOfDate", textField_dob.getText());
		String sex = btnGroupSex.getSelection().getActionCommand();//Male, Female,Other
		if(sex.equals("Male")) {
			sex = "M";
		}else if(sex.equals("Female")) {
			sex = "F";
		}else {
			sex = "O";
		}
		inputs.put("Sex", sex);
		inputs.put("StudyDesc", textField_studyDesc.getText());
		inputs.put("SeriesDesc", textField_seriesDesc.getText());
		return inputs;
	}
	
	public boolean dateValidation(String inputDateString){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        dateFormat.setLenient(false);
        Date parsedDate = null;
        try {
            parsedDate = dateFormat.parse(inputDateString);
        } catch (ParseException e) {
            return false;
        }
        return dateFormat.format(parsedDate).equals(inputDateString);
    }
}
