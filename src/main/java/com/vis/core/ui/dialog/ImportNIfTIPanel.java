/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.ui.dialog;

import javax.swing.JPanel;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.ButtonGroup;
import java.awt.Dimension;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.JComboBox;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.vis.core.ui.listener.AlphanumericTextKeyListener;
import com.vis.core.ui.listener.DateTextKeyListener;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.Tag;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.Modality;

// ★ extends JPanel に戻し、完全に独立したパネルとして扱います
public class ImportNIfTIPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	
	private JTextField textField_pid;
	private JTextField textField_pname;
	private JTextField textField_dob;
	private JTextField textField_studyDate;
	private JTextField textField_study;
	private JTextField textField_series;
	private JTextField textField_json; // JSONファイルパス用
	
	private ButtonGroup btnGroupSex;
	private JRadioButton rdbtnMale;
	private JRadioButton rdbtnFemale;
	private JRadioButton rdbtnOther;
	private JRadioButton rdbtnNone;
	
	private JComboBox<String> comboBoxModality; // Modality用
	private JComboBox<StudyContext> comboBoxStudies;
	private JCheckBox chckbxAddToExisting;
	private JButton btnBrowseJson; // JSON選択ボタン
	
	private File metaJsonFile = null;
	
	DatabaseHandler db = DatabaseHandler.getInstance();
	
	public ImportNIfTIPanel() {
		initLayout();
		setupListeners();
	}
	
	private void initLayout() {
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{20, 0, 200, 80, 20};
		gridBagLayout.rowHeights = new int[]{20, 0, 0, 0, 0, 0, 0, 0, 31, 0, 0, 20};
		gridBagLayout.columnWeights = new double[]{0.0, 0.0, 1.0, 0.0, 0.0};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
		setLayout(gridBagLayout);
		
		int row = 1;
		
		// --- Patient ID ---
		JLabel lblPatientId = new JLabel("Patient ID");
		add(lblPatientId, createGbc(1, row, 1, GridBagConstraints.EAST));
		textField_pid = new JTextField();
		add(textField_pid, createGbc(2, row, 2, GridBagConstraints.HORIZONTAL));
		row++;
		
		// --- Patient Name ---
		JLabel lblPatientName = new JLabel("Patient Name");
		add(lblPatientName, createGbc(1, row, 1, GridBagConstraints.EAST));
		textField_pname = new JTextField();
		add(textField_pname, createGbc(2, row, 2, GridBagConstraints.HORIZONTAL));
		row++;
		
		// --- Date Of Birth ---
		JLabel lblBirthOfDate = new JLabel("Date Of Birth (yyyy/MM/dd)");
		add(lblBirthOfDate, createGbc(1, row, 1, GridBagConstraints.EAST));
		textField_dob = new JTextField();
		add(textField_dob, createGbc(2, row, 2, GridBagConstraints.HORIZONTAL));
		row++;
		
		JLabel lblStudyDate = new JLabel("Study Date (yyyy/MM/dd)");
		add(lblStudyDate, createGbc(1, row, 1, GridBagConstraints.EAST));
		textField_studyDate = new JTextField();
		textField_studyDate.setToolTipText("Leave blank to use current date."); // 空欄なら現在日時になることを明示
		add(textField_studyDate, createGbc(2, row, 2, GridBagConstraints.HORIZONTAL));
		row++;
		
		// --- Patient Sex ---
		JLabel lblPatientSex = new JLabel("Patient Sex");
		add(lblPatientSex, createGbc(1, row, 1, GridBagConstraints.EAST));
		JPanel sexPanel = new JPanel();
		rdbtnMale = new JRadioButton("Male"); rdbtnMale.setActionCommand("Male");
		rdbtnFemale = new JRadioButton("Female"); rdbtnFemale.setActionCommand("Female");
		rdbtnOther = new JRadioButton("Other"); rdbtnOther.setActionCommand("Other");
		rdbtnNone = new JRadioButton("None"); rdbtnNone.setActionCommand("None");
		btnGroupSex = new ButtonGroup();
		btnGroupSex.add(rdbtnMale); btnGroupSex.add(rdbtnFemale); btnGroupSex.add(rdbtnOther); btnGroupSex.add(rdbtnNone);
		rdbtnNone.setSelected(true);
		sexPanel.add(rdbtnMale); sexPanel.add(rdbtnFemale); sexPanel.add(rdbtnOther); sexPanel.add(rdbtnNone);
		add(sexPanel, createGbc(2, row, 2, GridBagConstraints.BOTH));
		row++;
		
		// --- Modality ---
		JLabel lblModality = new JLabel("Modality");
		add(lblModality, createGbc(1, row, 1, GridBagConstraints.EAST));
		comboBoxModality = new JComboBox<>(new String[]{"MR", "CT", "PT", "ST"});
		comboBoxModality.setSelectedIndex(0);
		add(comboBoxModality, createGbc(2, row, 2, GridBagConstraints.HORIZONTAL));
		row++;
		
		// --- Meta JSON File ---
		JLabel lblMetaJson = new JLabel("Meta JSON File");
		lblMetaJson.setToolTipText("Select the BIDS JSON file associated with the NIfTI image.");
		add(lblMetaJson, createGbc(1, row, 1, GridBagConstraints.EAST));
		textField_json = new JTextField();
		textField_json.setEditable(false);
		add(textField_json, createGbc(2, row, 1, GridBagConstraints.HORIZONTAL));
		btnBrowseJson = new JButton("Browse");
		add(btnBrowseJson, createGbc(3, row, 1, GridBagConstraints.HORIZONTAL));
		row++;
		
		// --- Import as ---
		JLabel lblImport = new JLabel("Import as");
		add(lblImport, createGbc(1, row, 1, GridBagConstraints.EAST));
		chckbxAddToExisting = new JCheckBox("Add to existing a study");
		add(chckbxAddToExisting, createGbc(2, row, 2, GridBagConstraints.HORIZONTAL));
		row++;
		
		// --- Study ComboBox ---
		comboBoxStudies = new JComboBox<>();
		comboBoxStudies.setEditable(true);
		comboBoxStudies.setPreferredSize(new Dimension(231, 31));
		add(comboBoxStudies, createGbc(2, row, 2, GridBagConstraints.HORIZONTAL));
		row++;
		
		// --- Study Desc ---
		JLabel lblStudyDesc = new JLabel("Study Desc");
		add(lblStudyDesc, createGbc(1, row, 1, GridBagConstraints.EAST));
		textField_study = new JTextField();
		add(textField_study, createGbc(2, row, 2, GridBagConstraints.HORIZONTAL));
		row++;
		
		// --- Series Desc ---
		JLabel lblSeriesDesc = new JLabel("Series Desc");
		add(lblSeriesDesc, createGbc(1, row, 1, GridBagConstraints.EAST));
		textField_series = new JTextField();
		add(textField_series, createGbc(2, row, 2, GridBagConstraints.HORIZONTAL));
	}
	
	private GridBagConstraints createGbc(int x, int y, int width, int fill) {
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 5, 2, 5);
		gbc.gridx = x;
		gbc.gridy = y;
		gbc.gridwidth = width;
		gbc.fill = fill;
		if (fill == GridBagConstraints.HORIZONTAL || fill == GridBagConstraints.BOTH) {
			gbc.weightx = 1.0;
		}
		if (x == 1) {
			gbc.anchor = GridBagConstraints.EAST;
		} else {
			gbc.anchor = GridBagConstraints.WEST;
		}
		return gbc;
	}
	
	private void setupListeners() {
		textField_pid.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ENTER) searchInDB();
			}
			@Override
			public void keyReleased(KeyEvent e) { searchInDB(); }
		});
		
		chckbxAddToExisting.addActionListener(e -> updateFieldsState());
		
		AlphanumericTextKeyListener pnameTextListener = new AlphanumericTextKeyListener(64, AlphanumericTextKeyListener.pname_acceptables);
		AlphanumericTextKeyListener pidTextListener = new AlphanumericTextKeyListener(64, AlphanumericTextKeyListener.pid_acceptables);
		DateTextKeyListener dobTextListener = new DateTextKeyListener();

		textField_pid.addKeyListener(pidTextListener);
		textField_pname.addKeyListener(pnameTextListener);
		textField_dob.addKeyListener(dobTextListener);
		textField_studyDate.addKeyListener(dobTextListener);
		
		btnBrowseJson.addActionListener(e -> {
			JFileChooser jsonChooser = new JFileChooser();
			jsonChooser.setDialogTitle("Select Meta JSON File");
			jsonChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
			if (metaJsonFile != null) {
				jsonChooser.setCurrentDirectory(metaJsonFile.getParentFile());
			}
			int res = jsonChooser.showOpenDialog(this);
			if (res == JFileChooser.APPROVE_OPTION) {
				metaJsonFile = jsonChooser.getSelectedFile();
				textField_json.setText(metaJsonFile.getAbsolutePath());
			}
		});
	}

	public Modality getSelectedModality() {
		String modStr = (String) comboBoxModality.getSelectedItem();
		try {
			return Modality.valueOf(modStr);
		} catch (Exception e) {
			return Modality.MR;
		}
	}
	
	public File getMetaJsonFile() {
		return metaJsonFile;
	}

	// =========================================================================
	// 以下のメソッド群を NonDicomImagePanel からコピーしてこのクラスに持たせます
	// =========================================================================

	private String getSelectedSex() {
		if (rdbtnMale.isSelected()) return "M";
		if (rdbtnFemale.isSelected()) return "F";
		if (rdbtnOther.isSelected()) return "O";
		return null;
	}

	private void setSexSelection(String sex) {
		if ("M".equals(sex)) rdbtnMale.setSelected(true);
		else if ("F".equals(sex)) rdbtnFemale.setSelected(true);
		else if ("O".equals(sex)) rdbtnOther.setSelected(true);
		else rdbtnNone.setSelected(true);
	}
	
	public void searchInDB() {
		if (textField_pid != null && db != null) {
			String pid = textField_pid.getText();
			if (pid != null && !pid.trim().isEmpty()) {
				HashMap<String, String> info = db.getPatientInfo(pid);
				if (info != null) {
					textField_pname.setText(info.get("PatientName"));
					textField_dob.setText(info.get("PatientBirthDate"));
					setSexSelection(info.get("PatientSex"));
					whetherItCanBeAddedToStudy(pid);
				}
			}
		}
	}
	
	String getSelectedStudyIUID() {
		Object item = comboBoxStudies.getSelectedItem();
		if(item == null) return null;
		StudyContext item_ = (StudyContext)item;
		return item_.getUID();
	}
	
	public String getStudyDateString() {
		return textField_studyDate.getText().trim();
	}
	
	public HashMap<Integer,String> getInputs() {
		String pid = textField_pid.getText();
		String pname = textField_pname.getText();
		String dob = textField_dob.getText();
		String sex = getSelectedSex();
		
		String study_uid = getSelectedStudyIUID();
		String study_desc = null;
		String series_desc = null;
		
		if(isImportNew()) {
			study_desc = textField_study.getText();
			series_desc = textField_series.getText();
			study_uid = UIDUtils.createUID();
		} else {
			study_desc = db.getValueFromStudy("StudyDescription", pid, study_uid);
			series_desc = textField_series.getText();
		}
		
		HashMap<Integer,String> info = new HashMap<>();
		info.put(Tag.PatientID, pid);
		info.put(Tag.PatientName, pname);
		info.put(Tag.PatientBirthDate, dob);
		info.put(Tag.PatientSex, sex);
		info.put(Tag.StudyDescription, study_desc);
		info.put(Tag.SeriesDescription, series_desc);
		info.put(Tag.StudyInstanceUID, study_uid);
		return info;
	}
	
	public boolean isImportNew() {
		return !(chckbxAddToExisting.isSelected() && comboBoxStudies.isEnabled());
	}
	
	void initComboBox(String pid) {
		ArrayList<String> uids = db.getStudyUidList(pid);
		comboBoxStudies.removeAllItems();
		for(String uid : uids) {
			String date = db.getValueFromStudy("StudyDate", pid, uid);
			List<String> modality = db.getModalitiesInStudyRealatedAllSeries(pid, uid);
			String m = "";
			for(String i : modality) {
				m += i+",";
			}
			if(m.length() > 0) m = m.substring(0, m.length()-1);
			String studyDesc = db.getValueFromStudy("StudyDescription", pid, uid);
			String item = date+"_"+m+"_"+studyDesc;
			comboBoxStudies.addItem(new StudyContext(item, uid));
		}
	}
	
	void whetherItCanBeAddedToStudy(String pid) {
		boolean nostudy = db.getNumOfStudyByPatient(pid) == 0;
		if(!nostudy) initComboBox(pid);
		changeStateThereAreNoStudies(nostudy);
	}
	
	private void updateFieldsState() {
		boolean isAddToExisting = chckbxAddToExisting.isSelected();
		comboBoxStudies.setEnabled(isAddToExisting);
		textField_study.setEnabled(!isAddToExisting);
		textField_study.setEditable(!isAddToExisting);
		repaint();
	}

	void changeStateThereAreNoStudies(boolean noStudyFound) {
		chckbxAddToExisting.setEnabled(!noStudyFound);
		if (noStudyFound) {
			chckbxAddToExisting.setSelected(false);
		}
		updateFieldsState();
	}
	
	class StudyContext{
		String name;
		String uid;
		StudyContext(String name, String uid){
			this.name = name;
			this.uid = uid;
		}
		String getUID() { return uid; }
		@Override
		public String toString() { return name; }
	}
}