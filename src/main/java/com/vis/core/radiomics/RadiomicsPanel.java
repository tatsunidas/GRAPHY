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
package com.vis.core.radiomics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.ContextKey;
import com.vis.core.facade.WindowManager;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.view.D2.roi.RoiConverter;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiObjManager;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.io.SaveDialog;
import weka.classifiers.Classifier;
import weka.core.SerializationHelper;
import weka.gui.GUIChooserApp;
import weka.gui.GenericObjectEditor;
import weka.gui.PropertyPanel;

public class RadiomicsPanel extends JPanel{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	//model
	JButton trainModelBtn;
	JCheckBox autoImputation;
	JCheckBox balance;
	JCheckBox autoFeatureSelect;
	//inference
	JButton predBtn;
	JButton showResultsBtn;
	JButton saveResultsBtn;
	//model config
	JButton saveConfigBtn;
	JButton loadConfigBtn;
	
	JPanel classListPanel;
	JButton addClassBtn;
	JButton deleteClassBtn;
	
	GenericObjectEditor m_ClassifierEditor;
	
	/**
	 * weka, to manipulate dataset csv.
	 */
	JButton wekaBtn;
	
	
	RoiObjManager rm = (RoiObjManager)WindowManager.getWindow(ConfigInfo.RoiManager);
	
	//command names
	private final String SAVE_CONFIG = "Save Configurations";
	private final String LOAD_CONFIG = "Load Configurations";
	private final String TRAIN_MODEL = "Train model";
	private final String IMPUTE = "Impute";
	private final String BALANCE = "Balance";
	private final String FEATURE_SELECT = "FeatureSelect";
	private final String PREDICTION = "Prediction";
	private final String SHOW_RESULTS = "Show Results";
	private final String SAVE_RESULTS = "Save Results";
	private final String WEKA = "WEKA";
	private final String ADD_CLASS = "Add New Class";
	private final String DELETE_CLASS = "Delete Class";
	
	final String[] defaultClasses = new String[] {"class1","class2"};
	List<ClassPanel> classes = new ArrayList<>();
	
	final RadiomicsWindow radWin;
	RadiomicsPipeline pipeline;
	
	ImagePlus pred;
	
	public RadiomicsPanel(RadiomicsWindow radW) {
		this.radWin = radW;
		setPipeline(radW.getPipeline());
		initBtns();
		buildGUI();
	}
	
	private void initBtns() {
		
		trainModelBtn = new JButton(TRAIN_MODEL);
		trainModelBtn.setActionCommand(TRAIN_MODEL);
		setAction(trainModelBtn);
		
		predBtn = new JButton(PREDICTION);
		predBtn.setActionCommand(PREDICTION);
		showResultsBtn = new JButton(SHOW_RESULTS);
		showResultsBtn.setActionCommand(SHOW_RESULTS);
		saveResultsBtn = new JButton(SAVE_RESULTS);
		saveResultsBtn.setActionCommand(SAVE_RESULTS);
		setAction(predBtn);
		setAction(showResultsBtn);
		setAction(saveResultsBtn);
		
		saveConfigBtn = new JButton(SAVE_CONFIG);
		saveConfigBtn.setActionCommand(SAVE_CONFIG);
		loadConfigBtn = new JButton(LOAD_CONFIG);
		loadConfigBtn.setActionCommand(LOAD_CONFIG);
		setAction(saveConfigBtn);
		setAction(loadConfigBtn);
		
		wekaBtn = new JButton(WEKA);
		wekaBtn.setActionCommand(WEKA);
		setAction(wekaBtn);
		
	}

	private void buildGUI() {
		setLayout(new BorderLayout());
		//functions
		JPanel func = buidFunctionPanel();
		JPanel trainds = buildTrainingDataPanel();
		JPanel center = new JPanel(new GridLayout(0, 2, 3, 3));
		center.add(func);
		center.add(trainds);
		
		add(center, BorderLayout.CENTER);
		setPreferredSize(new Dimension(730, 500));
	}
	
	private JPanel buidFunctionPanel() {
		JPanel westPanel = new JPanel();
		westPanel.setLayout(new BoxLayout(westPanel, BoxLayout.Y_AXIS));
		
		Border b = BorderFactory.createSoftBevelBorder(BevelBorder.RAISED, Color.ORANGE, Color.GRAY);
		
		JPanel model = new JPanel();
		model.setLayout(new GridLayout(3, 1));
		model.add(trainModelBtn);
		JPanel modelNameP = new JPanel(new FlowLayout(FlowLayout.LEFT));
		modelNameP.add(new JLabel("Model:"));
		// Add Weka panel for selecting the classifier and its options
		m_ClassifierEditor = new GenericObjectEditor();
		m_ClassifierEditor.setClassType(Classifier.class);
		m_ClassifierEditor.setValue(pipeline.getClassifier());
		m_ClassifierEditor.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				initClassifier();
			}
		});
		PropertyPanel m_CEPanel = new PropertyPanel(m_ClassifierEditor);
		modelNameP.add(m_CEPanel);
		model.add(modelNameP);
		
		autoImputation = new JCheckBox(IMPUTE);
		autoImputation.setToolTipText("Impute by mean");
		balance = new JCheckBox(BALANCE);
		balance.setToolTipText("Do balancing (to be even number of instances in classes)");
		autoFeatureSelect = new JCheckBox(FEATURE_SELECT);
		autoFeatureSelect.setToolTipText("Drop useless&multicorr, then, will select by using LASSO");
		autoImputation.setSelected(true);
		balance.setSelected(true);
		autoFeatureSelect.setSelected(true);
		JPanel modelSettingP = new JPanel(new GridLayout(1, 3));
		modelSettingP.add(autoImputation);
		modelSettingP.add(balance);
		modelSettingP.add(autoFeatureSelect);
		model.add(modelSettingP);
		
		model.setBorder(BorderFactory.createTitledBorder(b, "Model", TitledBorder.CENTER, TitledBorder.DEFAULT_JUSTIFICATION));
		westPanel.add(model);
		
		JPanel inference = new JPanel();
		inference.setLayout(new GridLayout(0, 1, 0, 5));
		inference.setBorder(BorderFactory.createTitledBorder(b, "Inference", TitledBorder.CENTER, TitledBorder.DEFAULT_JUSTIFICATION));
		inference.add(predBtn);
		inference.add(showResultsBtn);
		inference.add(saveResultsBtn);
		westPanel.add(inference);
		
		JPanel settings = new JPanel();
		settings.setLayout(new GridLayout(0, 1, 0, 5));
		settings.setBorder(BorderFactory.createTitledBorder(b, "Settings", TitledBorder.CENTER,
				TitledBorder.DEFAULT_JUSTIFICATION));
		settings.add(loadConfigBtn);
		settings.add(saveConfigBtn);
		westPanel.add(settings);
		
		JPanel wekaP = new JPanel();
		wekaP.setLayout(new GridLayout(0, 1, 0, 5));
		wekaP.setBorder(BorderFactory.createTitledBorder(b, "Data science", TitledBorder.CENTER, TitledBorder.DEFAULT_JUSTIFICATION));
		wekaP.add(wekaBtn);
		westPanel.add(wekaP);
		
		return westPanel;
	}
	
	private JPanel buildTrainingDataPanel() {
		JPanel classListPanelBase = new JPanel(new BorderLayout());
		classListPanel = new JPanel();
		classListPanel.setLayout(new BoxLayout(classListPanel, BoxLayout.Y_AXIS));
		JScrollPane classScroll = new JScrollPane(classListPanel);
		Border b = BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.cyan, Color.DARK_GRAY);
		classScroll.setBorder(BorderFactory.createTitledBorder(b, "Training dataset", TitledBorder.CENTER, TitledBorder.DEFAULT_POSITION));
		
		for(String name: defaultClasses) {
			ClassPanel cp = (ClassPanel) createNewClass(name);
			classListPanel.add(cp);
			classes.add(cp);
		}
		classListPanel.setPreferredSize(new Dimension(210, 500));
		classListPanelBase.add(classScroll, BorderLayout.CENTER);
		
		//buttons
		addClassBtn = new JButton(ADD_CLASS);
		addClassBtn.setActionCommand(ADD_CLASS);
		setAction(addClassBtn);
		deleteClassBtn = new JButton(DELETE_CLASS);
		deleteClassBtn.setActionCommand(DELETE_CLASS);
		setAction(deleteClassBtn);
		JPanel btnPanel = new JPanel(new GridLayout(1, 2));
		btnPanel.add(addClassBtn);
		btnPanel.add(deleteClassBtn);
		classListPanelBase.add(btnPanel, BorderLayout.SOUTH);
		
		return classListPanelBase;
	}
	
	private ClassPanel createNewClass(String name) {
		int new_index = classes.size();
		return new ClassPanel(new_index, name);
	}
	
	public void addNewClass(String name) {
		if(isDuplicateName(name)) {
			JOptionPane.showConfirmDialog(null, "This class already exists !");
			return;
		}
		ClassPanel cp = (ClassPanel) createNewClass(name);
		classListPanel.add(cp);
		classListPanel.revalidate();
		classListPanel.repaint();
		classes.add(cp);
	}
	
	private ClassPanel getClassPanel(String name) {
		for(ClassPanel cp : classes) {
			if(cp.name().equals(name)) {
				return cp;
			}
		}
		return null;
	}
	
	public void deleteClass(String name) {
		ClassPanel cp = getClassPanel(name);
		classListPanel.remove(cp);
		classListPanel.revalidate();
		classListPanel.repaint();
		classes.remove(cp);
	}
	
	private String[] getClassNames() {
		String[] names = new String[classes.size()];
		int i = 0;
		for(ClassPanel cp : classes) {
			names[i] = cp.name();
			i++;
		}
		return names;
	}
	
	public HashMap<String/*className*/, List<RoiObj>> getRois(){
		HashMap<String, List<RoiObj>> rois = new HashMap<>();
		for(ClassPanel cp:classes) {
			rois.put(cp.name(), cp.getRois());
		}
		return rois;
	}
	
	public void setPipeline(RadiomicsPipeline pipe) {
		this.pipeline = pipe;
	}
	
	public void initClassifier() {
		Object clf = m_ClassifierEditor.getValue();
		pipeline = pipeline.modelIs((Classifier)clf);
	}
	
	/**
	 * Operation specific information.
	 * @param prop
	 * @return
	 */
	private Properties addModelConfiguration(Properties prop) {
		prop.setProperty("MODEL_"+BALANCE, String.valueOf(balance.isSelected()));
		prop.setProperty("MODEL_"+FEATURE_SELECT, String.valueOf(autoFeatureSelect.isSelected()));
		prop.setProperty("MODEL_"+IMPUTE, String.valueOf(autoImputation.isSelected()));
		return prop;
	}
	
	public void saveConfigration() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setMultiSelectionEnabled(false);
		chooser.setDialogTitle("Select folder");
		int userSelection = chooser.showOpenDialog(this);
		// ユーザーが「開く」ボタンを押したかどうかをチェック
		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File selectedDirectory = chooser.getSelectedFile();
			saveRois(getRois(), selectedDirectory);
			// feature calculation settings
			Properties prop = radWin.getRadiomicsSettingsAsProp();
			// pipeline settings
			prop = addModelConfiguration(prop);
			saveProp(prop, selectedDirectory.getAbsolutePath() + File.separator + "configuration");
			pipeline.saveModel(selectedDirectory.getAbsolutePath() + File.separator + "model");
			/**
			 * train_dataset is used to build a instance for prediction.
			 */
			pipeline.saveDatasetARFF(selectedDirectory.getAbsolutePath() + File.separator + "traindataset");
		} else {
			System.out.println("フォルダ選択がキャンセルされました。");
		}
	}
	
	public void saveRois(HashMap<String, List<RoiObj>> roiset, File dir) {
		try {
			File saveTo = new File(dir.getCanonicalPath()+File.separator+"ROI");
			for(String className : roiset.keySet()) {
				File saveTo_ = new File(saveTo.getCanonicalPath()+File.separator+className);
				saveTo_.mkdirs();
				List<RoiObj> rois = roiset.get(className);
				int itr = 1;
				for(RoiObj ro : rois) {
					if(ro !=null) {
						String rName = className+"_"+itr;
						RoiObjManager.saveRoi(ro, saveTo_.getCanonicalPath()+File.separator+rName+".roi");
					}
					itr++;
				}
			}
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public void saveProp(Properties config, String dest) {
		FileOutputStream fos = null; // FileOutputStream を後でクローズするために外で宣言
		try {
			if(!dest.endsWith(".properties")) {
				dest += ".properties";
			}
			// 3. FileOutputStream を作成 (ファイルが存在しない場合は新規作成、存在する場合は上書き)
			fos = new FileOutputStream(dest);
			// 4. Properties オブジェクトをファイルに保存
			// store(OutputStream out, String comments) メソッドを使用
			config.store(fos, "radiomics segmentation configuration file");
			System.out.println("Propertiesファイルが正常に保存されました: " + dest);
		} catch (IOException e) {
			System.err.println("Propertiesファイルの保存中にエラーが発生しました: " + e.getMessage());
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					System.err.println("FileOutputStreamのクローズ中にエラーが発生しました: " + e.getMessage());
				}
			}
		}
	}
	
	
	public void loadConfiguration() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setMultiSelectionEnabled(false);
		chooser.setDialogTitle("Select folder");
		int userSelection = chooser.showOpenDialog(this);
		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File selectedDirectory = chooser.getSelectedFile();
			File[] files = selectedDirectory.listFiles();
			for (File f : files) {
				if (f.getName().equals("ROI")) {
					/**
					 * 再起動後には、Praparatの紐付けができなくなる。
					 * 現バージョンではtrainWith()が必要になる。
					 * ROIのロードは一旦取りやめる。
					 */
//					loadRois(f);
				} else if (f.getName().endsWith(".properties")) {
					loadConfigurationSettingProps(f);
				} else if (f.getName().endsWith(".model")) {
					loadModel(f);
				}else if (f.getName().endsWith(".arff")) {
					loadDatasetARFF(f);
				}
			}
		} else {
			System.out.println("フォルダ選択がキャンセルされました。");
		}
	}
	
	/**
	 * 関連のあるシリーズが解析対象だった場合にのみ、
	 * クラスパネルへROIをロードする。
	 * 
	 * @param roiDir
	 */
	public void loadRois(File roiDir) {
		System.out.println("Start LOAD ROI...");
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (!pipeline.isPraparatReady() || db == null) {
			System.out.println("Praparat or Database is not ready, can not load rois.");
			return;
		}
		
		Praparat prap = pipeline.getPraparat();
		
		// init classPanels ? 
		//initClassPanels();
		/*
		 * load rois
		 * DOES NOT import to graphy even if not exists.
		 */
		File classes[] = roiDir.listFiles();
		if(classes == null || classes.length == 0) {
			return;
		}
		int choice = Integer.MAX_VALUE;
		for (File c : classes) {
			if (choice != Integer.MAX_VALUE && choice != JOptionPane.YES_OPTION) {
				break;
			}
			String className = c.getName();
			//add classpanel if not exists.
			addNewClass(className);
			File rois[] = c.listFiles();
			for (File r : rois) {
				if (r.getName().endsWith(".roi")) {
					try {
						Roi r_ = new RoiDecoder(r.getAbsolutePath()).getRoi();
						if(r_ == null) {
							System.out.println("Null ROI... skip. :"+r.getAbsolutePath());
							continue;
						}
						RoiObj ro = new RoiConverter().convert2RoiObj(r_);
						if (isRoiBelongingTo(ro, prap)) {
							//already done add new class
							getClassPanel(className).add(ro);
						} else {
							System.out.println("This roi does not match to current series.");
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	private boolean isRoiBelongingTo(RoiObj r, Praparat prap) {
		HashMap<String, Object> info = prap.getInfoSet();
		String patID = (String)info.get("PatientID");
		String studyUID = (String)info.get("StudyInstanceUID");
		String seriesUID = (String)info.get("SeriesInstanceUID");
		String[] sopUIDs = (String[])info.get("SOPInstanceUIDs");
		String pid_ = r.getProperty(ContextKey.PatientID);
		String stUID = r.getProperty(ContextKey.StudyInstanceUID);
		String seUID = r.getProperty(ContextKey.SeriesInstanceUID);
		String sopUID = r.getProperty(ContextKey.SOPInstanceUID);
		if(patID.equals(pid_)&&studyUID.equals(stUID)&&seriesUID.equals(seUID)) {
			for(String sop : sopUIDs) {
				if(sop.equals(sopUID)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public void loadConfigurationSettingProps(File f) {
		Properties prop = PropertiesUtil.loadProperties(f.getAbsolutePath());
		radWin.loadRadiomicsSettings(prop);
		loadModelSettings(prop);
	}
	
	public void loadModelSettings(Properties prop) {
		String v = (String)prop.get("MODEL_"+BALANCE);
		if(v !=null) {
			balance.setSelected(Boolean.valueOf(v));
		}
		v = (String)prop.get("MODEL_"+FEATURE_SELECT);
		if(v !=null) {
			autoFeatureSelect.setSelected(Boolean.valueOf(v));
		}
		v = (String)prop.get("MODEL_"+IMPUTE);
		if(v !=null) {
			autoImputation.setSelected(Boolean.valueOf(v));
		}
	}
	
	public void loadModel(File f) {
		try {
			Classifier clf = (Classifier) SerializationHelper.read(f.getAbsolutePath());
			m_ClassifierEditor.setValue(clf);//be careful, this is a reason for locating loadModel() in this class.
			pipeline.modelIs(clf);
			System.out.println("Model is loaded successfully.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void loadDatasetARFF(File f) {
		pipeline.loadDatasetARFF(f);
	}
	
	public void initClassPanels() {
		classes = new ArrayList<>();
		for(Component com : classListPanel.getComponents()) {
			if(com instanceof ClassPanel) {
				classListPanel.remove(com);
			}
		}
		classListPanel.revalidate();
		classListPanel.repaint();
	}
	
	private void setAction(JComponent con) {
		if(con instanceof JButton) {
			JButton btn = (JButton)con;
			String name = btn.getActionCommand();
			if(name.equals("")) {
				
			}else if(name.equals(TRAIN_MODEL)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						Viewer2DScreen d2 = Viewer2DScreen.getInstance();
						if(d2 == null) {
							return;
						}
						initClassifier();//set model
						RadiomicsSettings rs = radWin.getRadiomicsSettings();
						HashMap<String, List<RoiObj>> ds = getRois();
						Praparat pp = d2.getSelectedPraps().get(0);
						pipeline = pipeline.trainWith(rs, ds, pp);
						pipeline.train(autoImputation.isSelected(), balance.isSelected(), autoFeatureSelect.isSelected());
					}
				});	
			}else if(name.equals(PREDICTION)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						/**
						 * DO NOT update/init model here.
						 * If model was loaded by loadSettings(),
						 * Model will be changed. 
						 */
						Viewer2DScreen d2 = Viewer2DScreen.getInstance();
						if(d2 == null) {
							return;
						}
						RadiomicsSettings rs = radWin.getRadiomicsSettings();
						HashMap<String, List<RoiObj>> ds = getRois();
						Praparat pp = d2.getSelectedPraps().get(0);
						pipeline = pipeline.trainWith(rs, ds, pp);
						/* pred imageplus
						 * slice 0 : label image
						 * slice 1 : proba image
						 */
						pred = pipeline.predict(pp.getCurrentSlidePos());
						System.out.println("PREDICTION was done !");
					}
				});
			}else if(name.equals(SHOW_RESULTS)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(pred != null) {
							new Thread(new Runnable() {
								@Override
								public void run() {
									// TODO Auto-generated method stub
									pred.show();
								}
							}).start();
						}
					}
				});
			}else if(name.equals(SAVE_RESULTS)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(pred != null) {
							SaveDialog sd = new SaveDialog("Save prediction results", "pred_seg", ".tif");
							if (sd.getFileName() == null) {
					            //("キャンセルされました。");
					            return; // 処理を中断
					        }
							// 3. ディレクトリとファイル名を取得
					        String directory = sd.getDirectory();
					        String fileName = sd.getFileName();
					        String savePath = directory + fileName;

					        // 結果をログに表示
					        System.out.println("選択された保存パス: " + savePath);
					        
					        // 4. 取得したパスを使って画像を保存
					        // このsaveAsメソッドが実際の保存処理を行います
					        IJ.saveAs(pred, "tiff", savePath);
					        System.out.println("完了:"+savePath + " に画像を保存しました。");
						}
					}
				});
			}else if(name.equals(LOAD_CONFIG)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						loadConfiguration();
					}
				});
			}else if(name.equals(SAVE_CONFIG)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						saveConfigration();
					}
				});
			}else if(name.equals(WEKA)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						launch_weka();
					}
				});
			}else if(name.equals(ADD_CLASS)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						String className = JOptionPane.showInputDialog("Please input new class name:", null);
						if (className != null) {
							if (!className.trim().isEmpty()) {
								System.out.println("入力されたクラス名: " + className);
								addNewClass(className);
							} else {
								// 空白のみ、または何も入力せずにOKを押した場合
								System.out.println("クラス名が入力されませんでした。");
							}
						} else {
							System.out.println("入力がキャンセルされました。");
						}
					}
				});
			}else if(name.equals(DELETE_CLASS)) {
				btn.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
				        String[] options = getClassNames();
				        JComboBox<String> comboBox = new JComboBox<>(options);
				        JPanel panel = new JPanel();
				        panel.add(new JLabel("Select a class:"));
				        panel.add(comboBox);
				        // JOptionPane.showOptionDialog() を使用してカスタムダイアログを表示
				        int result = JOptionPane.showOptionDialog(
				            null,                       // 親フレーム (nullで画面中央)
				            panel,                      // 表示するカスタムコンポーネント（JPanel）
				            "Select Class to Delete",            // ダイアログのタイトル
				            JOptionPane.OK_CANCEL_OPTION, // OKとCancelボタンを表示
				            JOptionPane.QUESTION_MESSAGE, // メッセージの種類 (アイコン表示)
				            null,                       // アイコン (nullでデフォルト)
				            null,                       // オプションボタンの配列 (nullでデフォルトのOK/Cancel)
				            null                        // デフォルトで選択されるオプション (nullで最初のボタン)
				        );

				        // ユーザーの選択に応じた処理
				        if (result == JOptionPane.OK_OPTION) {
				            // OKボタンが押された場合、選択されたアイテムを取得
				            String selectedOption = (String) comboBox.getSelectedItem();
				            System.out.println("選択されたクラス: " + selectedOption);
				            deleteClass(selectedOption);
				        } else {
				            // Cancelボタンが押されたか、ダイアログが閉じられた場合
				            System.out.println("選択がキャンセルされました。");
				            JOptionPane.showMessageDialog(null, "選択がキャンセルされました。", "キャンセル", JOptionPane.INFORMATION_MESSAGE);
				        }
					}
				});
			}
		}
	}
	
	public void launch_weka() {
		GUIChooserApp chooser = new GUIChooserApp();
		for (WindowListener wl : chooser.getWindowListeners()) {
			chooser.removeWindowListener(wl);
		}
		chooser.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		chooser.setLocationRelativeTo(this);
		chooser.setVisible(true);
	}
	
	boolean isDuplicateName(String newClassName) {
		for(ClassPanel cp : classes) {
			if(cp.name().equals(newClassName)) {
				return true;
			}
		}
		return false;
	}
	
	class ClassPanel extends JPanel{
		private static final long serialVersionUID = 1L;
		final int ind;
		final String name;
		JList<RoiObj> roiList;
		DefaultListModel<RoiObj> listModel = new DefaultListModel<>();
		JButton addBtn = new JButton("Add");
		JButton deleteBtn = new JButton("Delete");
		ClassPanel(int index, String name){
			ind = index;
			this.name = name;
			roiList = new JList<>(listModel);
			roiList.setCellRenderer(new RoiListCellRenderer());
			setLayout(new BorderLayout());
			JPanel btnP = new JPanel(new GridLayout(1, 2));
			btnP.add(addBtn);
			btnP.add(deleteBtn);
			add(btnP, BorderLayout.NORTH);
			addBtn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					Viewer2DScreen screen = (Viewer2DScreen) WindowManager.getWindow(ConfigInfo.D2ViewerWindow);
					if(screen == null) {
						System.out.println("2DViewer is NULL !!!");
						return;
					}
					ArrayList<Praparat> praps = screen.getSelectedPraps();
					if(praps != null && praps.size() > 0) {
						for(Praparat prap : praps) {
							ArrayList<RoiObj> rois = prap.getSelectedRois();
							if(rois.size() > 0) {
								for(RoiObj r : rois) {
									add(r);
								}
							}
						}
					}else {
						System.out.println("Any praparats are not selected, cannot find roi... ");
					}
				}
			});
			deleteBtn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					List<RoiObj> selected = roiList.getSelectedValuesList();
					if(selected == null || selected.size()==0) {
						return;
					}
					for(RoiObj r : selected) {
						if(r != null) {
							delete(r);
						}
					}
				}
			});
			add(roiList, BorderLayout.CENTER);
			Border b = BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.cyan, Color.DARK_GRAY);
			setBorder(BorderFactory.createTitledBorder(b, name, TitledBorder.CENTER, TitledBorder.DEFAULT_POSITION));
			setPreferredSize(new Dimension(200, 200));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
		}
		int index() {
			return ind;
		}
		String name() {
			return name;
		}
		
		void add(RoiObj r) {
			String roiId = r.getUIDs().get(ContextKey.RoiID);
			if(roiId == null) {
				System.out.println("Cannot load. This roi is not created by GRAPHY...:"+r.getName());
				return;
			}
			if(listModel.contains(r)) {
				System.out.println(roiId + " is already listed.");
				return;
			}
			for(int i=0; i<listModel.size(); i++) {
				RoiObj r2 = listModel.get(i);
				String roiId2 = r2.getUIDs().get(ContextKey.RoiID);
				if(roiId.equals(roiId2)) {
					//already in. skip.
					return;
				}
			}
			listModel.add(listModel.getSize(), r);
		}
		
		void delete(RoiObj r) {
			int pos = listModel.indexOf(r);
			if(pos >= 0) {
				listModel.remove(listModel.indexOf(r));
			}
		}
		
		void updateOrReplace(int row/*0 to n-1*/, RoiObj r) {
			if(row < 0 || row > listModel.getSize()) {
				System.out.println("RadiomicsPanel.ClassPanle:updateOrReplace:: Out Of Range ! "+row);
				return;
			}
			if(r == null) {
				System.out.println("RadiomicsPanel.ClassPanle:updateOrReplace:: Rois is NULL ! "+row);
				return;
			}
			listModel.set(row, r);
		}
		
		List<RoiObj> getRois(){
			List<RoiObj> rois = new ArrayList<>();
			int size = listModel.getSize();
			for(int i=0; i<size; i++) {
				rois.add(listModel.get(i));
			}
			return rois;
		}
	}
	
	class RoiListCellRenderer extends DefaultListCellRenderer {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList<?> list,
				Object value, // RoiObj
				int index, 
				boolean isSelected,
				boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof RoiObj) {
				RoiObj roi = (RoiObj) value;
				setText(roi.getUIDs().get(ContextKey.RoiID));
			} else {
				setText((value == null) ? "" : value.toString());
			}
			return this;
		}
	}
	
}
