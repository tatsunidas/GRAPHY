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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageRoi;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.plugin.FolderOpener;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.StackStatistics;
import io.github.tatsunidas.radiomics.features.RadiomicsFeature;
import io.github.tatsunidas.radiomics.main.FeatureCalculator;
import io.github.tatsunidas.radiomics.main.FeatureCalculatorFactory;
import io.github.tatsunidas.radiomics.main.FeatureSpecifier;
import io.github.tatsunidas.radiomics.main.FeatureVisualizationMap;

/**
 * 
 * @author tatsunidas
 *
 */
public class RadiomicsVisualizationPanel extends JPanel {
	
	//test
	public static void main(String args[]) {
		/**
		 * add VM option
		 * -Djava.library.path=./native/native_opencv/linux-x86-64 
		 */
		JFrame f = new JFrame();
		f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		RadiomicsSettings radSetting = new RadiomicsSettings();
		RadiomicsVisualizationPanel vPanel = new RadiomicsVisualizationPanel(radSetting);
		
		JTabbedPane tabPane = new JTabbedPane();
		tabPane.addTab("Settings", radSetting);
		tabPane.addTab("Visualization", vPanel);
		
		f.add(tabPane);
		f.setSize(1000, 1000);
		f.setVisible(true);
		radSetting.adjustDividerLocation();
		
		try {
			vPanel.onLoadImage("/home/tatsunidas/ダウンロード/case_test/DICOM_T1");
			vPanel.onLoadMask("/home/tatsunidas/ダウンロード/case_test/Mask_Plaque/left",1);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// --- UI Components ---
	// LEFT configuration
	private JButton loadImageButton, loadMaskButton;
	private JButton loadImageFromDbButton, loadMaskFromDbButton;
	private JComboBox<String> featureComboBox;
	private JSpinner filterSizeSpinner;
	private JButton executeSliceButton, executeAllButton;
	private JButton saveMapButton, saveMapToDbButton;

	// RIGHT ("Praparats")
	private Praparat originalImagePanel;
	private Praparat maskImagePanel;
	private Praparat radiomicsMapPanel;
	private Praparat fusionImagePanel;
	private JRadioButton fusionMapRadio, fusionMaskRadio;
	private JSlider transparencySlider;

	// --- Data Holders ---
	private ImagePlus originalImage;
	private ImagePlus maskImage;
	private ImagePlus radiomicsMap;
	private ImagePlus fusionImage;
	private ImagePlus fusionBackground;//base image
	
	RadiomicsSettings radSetting;
	
	final String[] textures = {"GLCM", "GLRLM", "GLSZM", "GLDZM", "NGTDM", "NGLDM"}; 

	public RadiomicsVisualizationPanel(RadiomicsSettings radSetting) {
		super();
		this.radSetting = radSetting;
		buildup();
		addListeners();
	}

	void buildup() {
		
		this.setLayout(new BorderLayout());
		
		
		// left side panel: configuration panel
		JPanel configPanel = new JPanel();
       configPanel.setLayout(new GridBagLayout());
       
       GridBagConstraints gbc = new GridBagConstraints();
       gbc.gridx = 0; // すべてのコンポーネントを同じ列(0)に配置
       gbc.gridy = 0; // 最初の行
       gbc.weightx = 1.0; // 水平方向のリサイズ時に幅を広げる
       gbc.weighty = 0.0; // 垂直方向には広がらない（スペーサーが担当）
       gbc.fill = GridBagConstraints.HORIZONTAL; // 水平方向にいっぱいに広げる
       gbc.anchor = GridBagConstraints.NORTH; // セル内で上寄せにする
       gbc.insets = new Insets(2, 2, 2, 2); // コンポーネント間の余白

		// select images and masks
       	// 1. ファイルからの読み込み
       JPanel loadFilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
       loadFilePanel.setBorder(BorderFactory.createTitledBorder("Load from File"));
       loadImageButton = new JButton("Load Image...");
       loadMaskButton = new JButton("Load Mask...");
       loadFilePanel.add(loadImageButton);
       loadFilePanel.add(loadMaskButton);
       configPanel.add(loadFilePanel, gbc);
		
		// select images and masks from DB selector
       JPanel loadDbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
       loadDbPanel.setBorder(BorderFactory.createTitledBorder("Load from Database"));
       loadImageFromDbButton = new JButton("Load Image (DB)...");
       loadMaskFromDbButton = new JButton("Load Mask (DB)...");
       loadDbPanel.add(loadImageFromDbButton);
       loadDbPanel.add(loadMaskFromDbButton);
       gbc.gridy++; 
       configPanel.add(loadDbPanel, gbc);

		// feature calculation settings
       JPanel settingsPanel = new JPanel();
       settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
       settingsPanel.setBorder(BorderFactory.createTitledBorder("Calculation Settings"));
		/*
		 * choose a texture feature (no multiple selection)
		 */
       JPanel featurePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
       featurePanel.add(new JLabel("Texture Feature:"));
       
       //load featureNames
       List<String> names = new ArrayList<>();
       
       for(String fam : textures) {
    	   names.addAll(RadiomicsSettings.featureNames(fam));
       }
       String[] features = new String[names.size()];
       for(int i=0;i<names.size(); i++) {
    	   features[i] = names.get(i);
       }
       featureComboBox = new JComboBox<>(features);
       featurePanel.add(featureComboBox);
       settingsPanel.add(featurePanel);
       
		/*
		 * set image filter size (odd number recommended)
		 */
       JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
       filterPanel.add(new JLabel("Filter Size (odd):"));
       SpinnerNumberModel spinnerModel = new SpinnerNumberModel(9, 3, 99, 2);
       filterSizeSpinner = new JSpinner(spinnerModel);
       filterSizeSpinner.setEditor(new JSpinner.NumberEditor(filterSizeSpinner, "#"));
       filterSizeSpinner.setPreferredSize(new Dimension(60, 25));
       filterPanel.add(filterSizeSpinner);
       settingsPanel.add(filterPanel);
       gbc.gridy++;
       configPanel.add(settingsPanel, gbc);

		/*
		 * execute slice btn and show on result praparat
		 */
       JPanel executePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
       executePanel.setBorder(BorderFactory.createTitledBorder("Execute"));
       executeSliceButton = new JButton("Execute Current IMAGE Slice");
       executeAllButton = new JButton("Execute All Slices(take long time)");
       executePanel.add(executeSliceButton);
       executePanel.add(executeAllButton);
       gbc.gridy++;
       configPanel.add(executePanel, gbc);

		// save function
       JPanel savePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
       savePanel.setBorder(BorderFactory.createTitledBorder("Save Results"));
       saveMapButton = new JButton("Save Map to File...");
       saveMapToDbButton = new JButton("Save Map to DB");
       savePanel.add(saveMapButton);
       savePanel.add(saveMapToDbButton);
       gbc.gridy++;
       configPanel.add(savePanel, gbc);
       
       // add spacer to left component
       gbc.gridy++;
       gbc.weighty = 1.0; // 垂直方向の余白をすべて引き受ける
       gbc.fill = GridBagConstraints.BOTH; // 垂直・水平両方に広がる
       JPanel spacer = new JPanel();
       spacer.setOpaque(false); // 透明にして目に見えないようにする
       configPanel.add(spacer, gbc);

       // 設定パネルが長くなった場合に備えてスクロール可能にする
       JScrollPane configScrollPane = new JScrollPane(configPanel);
       configScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
       configScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

       /*
        * right panel (view component)
        */
		// 2*2 gridlayout, set 4 praparats.
		/*
		 * top-left praparat: original images top-right praparat: original mask
		 * bottom-left praparat: visualization map bottom-right: fusion images, original
		 * and visualization map.
		 */
       JPanel rightMainPanel = new JPanel(new BorderLayout(5, 5));

       JPanel visGridPanel = new JPanel(new GridLayout(2, 2, 5, 5));
       visGridPanel.setBorder(BorderFactory.createEtchedBorder());

       originalImagePanel = new Praparat(ViewMode.Normal);
       maskImagePanel = new Praparat(ViewMode.Normal);
       radiomicsMapPanel  = new Praparat(ViewMode.Normal);
       fusionImagePanel = new Praparat(ViewMode.Normal);

       visGridPanel.add(originalImagePanel);
       visGridPanel.add(maskImagePanel);
       visGridPanel.add(radiomicsMapPanel);
       visGridPanel.add(fusionImagePanel);
       rightMainPanel.add(visGridPanel, BorderLayout.CENTER);

       // Fusion
       JPanel fusionControlsPanel = new JPanel();
       fusionControlsPanel.setLayout(new BoxLayout(fusionControlsPanel, BoxLayout.Y_AXIS));
       fusionControlsPanel.setBorder(BorderFactory.createTitledBorder("Fusion Controls"));

       // 1. Fusion対象の選択パネル (ラジオボタン)
       JPanel fusionTargetPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
       fusionTargetPanel.add(new JLabel("Foreground:"));
       fusionMapRadio = new JRadioButton("Radiomics Map", true);
       fusionMaskRadio = new JRadioButton("Mask");
       ButtonGroup fusionGroup = new ButtonGroup();
       fusionGroup.add(fusionMapRadio);
       fusionGroup.add(fusionMaskRadio);
       fusionTargetPanel.add(fusionMapRadio);
       fusionTargetPanel.add(fusionMaskRadio);
       
       // 2. 透明度設定パネル (スライダー)
       JPanel transparencyPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
       transparencyPanel.add(new JLabel("Opacity:"));
       // 0% (透明) から 100% (不透明) までのスライダー。初期値 50%
       transparencySlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 50);
       transparencySlider.setMajorTickSpacing(25); // 25ごとに大目盛り
       transparencySlider.setMinorTickSpacing(5);  // 5ごとに小目盛り
       transparencySlider.setPaintTicks(true);    // 目盛りを表示
       transparencySlider.setPaintLabels(true);   // ラベル (0, 25, 50, 75, 100) を表示
       transparencySlider.setPreferredSize(new Dimension(250, 45)); // スライダーの推奨サイズ
       transparencyPanel.add(transparencySlider);

       // コントロールパネルに2つのパネルを追加
       fusionControlsPanel.add(fusionTargetPanel);
       fusionControlsPanel.add(transparencyPanel);

       rightMainPanel.add(fusionControlsPanel, BorderLayout.SOUTH);

       // --- Split Pane ---
       JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
               configScrollPane,
               rightMainPanel);
       splitPane.setDividerLocation(350); // 設定パネルの初期幅
       splitPane.setOneTouchExpandable(true);

       this.add(splitPane, BorderLayout.CENTER);

		/*
		 * This is just memo. please ignore. TEST: roi etc functions are ignored.(such
		 * functions are available in the common viewer. )
		 */
	}
	
	private void addListeners() {
		// --- Load Actions ---
		loadImageButton.addActionListener(e -> onLoadImage());
		loadMaskButton.addActionListener(e -> onLoadMask());
		loadImageFromDbButton.addActionListener(e -> onLoadImageFromDb());
		loadMaskFromDbButton.addActionListener(e -> onLoadMaskFromDb());

		// --- Execute Actions ---
		executeSliceButton.addActionListener(e -> onExecuteSlice());
		executeAllButton.addActionListener(e -> onExecuteAll());

		// --- Save Actions ---
		saveMapButton.addActionListener(e -> onSaveMap());
		saveMapToDbButton.addActionListener(e -> onSaveMapToDb());

		// --- Fusion Actions ---
		ActionListener fusionTargetListener = e -> updateFusionImage();
		fusionMapRadio.addActionListener(fusionTargetListener);
		fusionMaskRadio.addActionListener(fusionTargetListener);
		transparencySlider.addChangeListener(e -> {
			JSlider slider = (JSlider) e.getSource();
			// マウスを離した時だけ更新したい場合は以下を有効にする
			if (!slider.getValueIsAdjusting()) {
				updateFusionImage();
			}
		});
	}

	private void onLoadImage() {
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			try {
				String path = fc.getSelectedFile().getAbsolutePath();
				onLoadImage(path);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this, "Failed to load image: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}
    
	public void onLoadImage(String path) throws Exception {
		File f = new File(path);
		ImagePlus originalImage = null;
		if(f.isDirectory()) {
			originalImage = FolderOpener.open(path);
		}else {
			Opener opener = new Opener();
			originalImage = opener.openImage(path);
		}
		if (originalImage != null) {
			originalImagePanel.reloadSlideGlasses(originalImage);
			//init from praparat, important.
			this.originalImage = originalImagePanel.getImagePlus();
			if(this.maskImage != null) {
				this.maskImage.copyScale(this.originalImage);
				maskImagePanel.reloadSlideGlasses(this.maskImage);
				this.maskImage = maskImagePanel.getImagePlus();
			}
		} else {
			throw new Exception("Failed to open image.");
		}
	}
    
    private void onLoadMask() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
             try {
                String path = fc.getSelectedFile().getAbsolutePath();
                onLoadMask(path);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to load mask: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
	public void onLoadMask(String path) throws Exception {
		File f = new File(path);
		ImagePlus maskImage = null;
		if (f.isDirectory()) {
			maskImage = FolderOpener.open(path);
		} else {
			Opener opener = new Opener();
			maskImage = opener.openImage(path);
		}

		if (maskImage != null) {
			maskImagePanel.reloadSlideGlasses(maskImage);
			this.maskImage = maskImagePanel.getImagePlus();
			if (this.originalImage != null) {
				this.maskImage.copyScale(this.originalImage);
				maskImagePanel.reloadSlideGlasses(this.maskImage);
				this.maskImage = maskImagePanel.getImagePlus();
			}
		} else {
			throw new Exception("Failed to open image.");
		}
	}
	
	/**
	 * TODO
	 * Cannot set min max range...
	 * @param path
	 * @param mask_lbl
	 * @throws Exception
	 */
	public void onLoadMask(String path, int mask_lbl) throws Exception {
		onLoadMask(path);
		if (this.maskImage != null) {
			maskImagePanel.adjustContrastByMinMax(mask_lbl-1, mask_lbl);
		} else {
			throw new Exception("Failed to open image.");
		}
	}

    /**
     * [概念] データベースセレクタからオリジナル画像をロードします。
     */
    private void onLoadImageFromDb() {
        // TODO: GRAPHY既存のDBセレクタダイアログを呼び出す
        // 例:
        // DbSelectorDialog dbDialog = new DbSelectorDialog(this, "Select Image Series");
        // dbDialog.setVisible(true);
        // if (dbDialog.isSucceeded()) {
        //     String seriesUID = dbDialog.getSelectedSeriesUID();
        //     this.originalImage = loadSeriesFromDb(seriesUID); // DBからロードするヘルパーメソッド
        //     originalImagePanel.setImage(this.originalImage);
        // }
        JOptionPane.showMessageDialog(this, "Concept: Load Image from DB");
    }

    /**
     * [概念] データベースセレクタからマスク画像（DICOM SEG等）をロードします。
     */
    private void onLoadMaskFromDb() {
        // TODO: 同上。画像に対応するマスクを検索するロジックが必要
        JOptionPane.showMessageDialog(this, "Concept: Load Mask from DB");
    }

    /**
     * 現在表示中のIMAGEスライスに対してRadiomics特徴量マップを計算します。
     */
	private void onExecuteSlice() {
		if (!validateInputs()) {
			return;
		}
		String featureClass = (String) featureComboBox.getSelectedItem();
		String familyAndFeature[] = featureClass.split("_");
		int filterSize = (int) filterSizeSpinner.getValue();
		
		// build settings from radSetting
		Properties settingsProp = radSetting.currentSettings();
		Map<String, Object> settings = settingsMap(familyAndFeature, settingsProp);

		boolean d3_mode = Boolean.valueOf((String) settingsProp.get(SettingsContext.D3Basis));
		boolean d2_mode = d3_mode == false;
		
		FeatureSpecifier<RadiomicsFeature> featuresToCalculate = new FeatureSpecifier<>(
				radSetting.loadClass(familyAndFeature[0]+"Features"),
				radSetting.loadFeatureType(familyAndFeature), 
				settings);

		FeatureCalculator calculator = new FeatureCalculatorFactory().create(featuresToCalculate);

		// 2. マップを生成
		long startTime = System.currentTimeMillis();
		/*
		 * slice = -1 means calculate all.
		 */
		int slice = originalImagePanel.getCurrentSlidePos() + 1;// to 1 to N
		this.radiomicsMap = FeatureVisualizationMap.generateFeatureMap(this.originalImage, this.maskImage, slice,
				calculator, filterSize, d2_mode);
		long endTime = System.currentTimeMillis();
		System.out.println("--> Generation took " + (endTime - startTime) + " ms.");
		
		if(radiomicsMap != null) {
			radiomicsMapPanel.reloadSlideGlasses(radiomicsMap);
			radiomicsMap.resetDisplayRange();
		}else {
			JOptionPane.showConfirmDialog(this, "Radiomics map was not created... Please check logs. ");
		}
		
		fusionBackground = new ImagePlus(this.originalImage.getStack().getSliceLabel(slice), this.originalImage.getStack().getProcessor(slice));
		
		// Fusion画像を更新
		updateFusionImage();
	}

    /**
     * 全スライスに対してRadiomics特徴量マップを計算します。
     */
	private void onExecuteAll() {
		if (!validateInputs()) {
			return;
		}
		
		// 3Dが選択されている場合は、3D計算を実行する
		String featureClass = (String) featureComboBox.getSelectedItem();
		String familyAndFeature[] = featureClass.split("_");
		int filterSize = (int) filterSizeSpinner.getValue();

		// build settings from radSetting
		Properties settingsProp = radSetting.currentSettings();
		Map<String, Object> settings = settingsMap(familyAndFeature, settingsProp);
		
		boolean d3_mode = Boolean.valueOf((String)settingsProp.get(SettingsContext.D3Basis));
		boolean d2_mode = d3_mode == false;
		
		FeatureSpecifier<RadiomicsFeature> featuresToCalculate = new FeatureSpecifier<>(
				radSetting.loadClass(familyAndFeature[0] + "Features"), radSetting.loadFeatureType(familyAndFeature),
				settings);

		FeatureCalculator calculator = new FeatureCalculatorFactory().create(featuresToCalculate);

		// 2. マップを生成
		long startTime = System.currentTimeMillis();
		/*
		 * slice = -1 means calculate all.
		 */
		int slice = -1;
		
		this.radiomicsMap = FeatureVisualizationMap.generateFeatureMap(this.originalImage, this.maskImage, slice,
				calculator, filterSize, d2_mode);
		long endTime = System.currentTimeMillis();
		System.out.println("--> Generation took " + (endTime - startTime) + " ms.");

		if (radiomicsMap != null) {
			radiomicsMapPanel.reloadSlideGlasses(radiomicsMap);
			radiomicsMap.resetDisplayRange();
		} else {
			JOptionPane.showConfirmDialog(this, "Radiomics map was not created... Please check logs. ");
		}

		fusionBackground = this.originalImage;

		// Fusion画像を更新
		updateFusionImage();

	}

    /**
     * 計算結果のマップをファイルに保存します。
     */
	private void onSaveMap() {
        if (radiomicsMap == null) {
            JOptionPane.showMessageDialog(this, "No radiomics map to save.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 1. JFileChooser を作成
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Radiomics Map (as TIFF)");
        // デフォルトのファイル名を提案
        fc.setSelectedFile(new File("radiomics_map.tif"));

        // 2. TIFF (*.tif, *.tiff) のファイルフィルタを設定
        FileNameExtensionFilter tiffFilter = new FileNameExtensionFilter("TIFF Image (*.tif, *.tiff)", "tif", "tiff");
        fc.addChoosableFileFilter(tiffFilter);
        fc.setFileFilter(tiffFilter); // デフォルトをTIFFに

        // 3. オプション（Fusion画像も保存）を持つカスタムアクセサリパネルを作成
        JPanel optionsPanel = new JPanel();
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Save Options"));
        
        // Fusion画像を保存するかのチェックボックス
        JCheckBox saveFusionCheckBox = new JCheckBox("Save Fusion image (as PNG) too");
        
        // fusionImageがまだ計算されていない (null) 場合は、チェックボックスを無効化
        saveFusionCheckBox.setEnabled(this.fusionImage != null);
        optionsPanel.add(saveFusionCheckBox);

        // 4. JFileChooserにアクセサリパネルを追加
        fc.setAccessory(optionsPanel);

        // 5. SaveDialogを表示
        int result = fc.showSaveDialog(this);

		// 6. ユーザーが「保存」を選択した場合
		if (result == JFileChooser.APPROVE_OPTION) {
			try {
				// 7. ユーザーが入力した（またはデフォルトの）ファイルパスを取得
				String userPath = fc.getSelectedFile().getAbsolutePath();

				// --- 8. Radiomics Map (TIFF) の保存パスを決定 ---
				String mapPath = userPath;
				// ユーザーが .tif 以外の拡張子をつけたか、何もつけなかった場合の処理
				if (!mapPath.toLowerCase().endsWith(".tif") && !mapPath.toLowerCase().endsWith(".tiff")) {
					// 既存の拡張子を削除
					int dotIndex = mapPath.lastIndexOf('.');
					if (dotIndex > 0) {
						mapPath = mapPath.substring(0, dotIndex);
					}
					// 正しい .tif 拡張子を付与
					mapPath += ".tif";
				}
                
				// Radiomics Map を TIFF として保存
				FileSaver mapSaver = new FileSaver(this.radiomicsMap);
				if (!mapSaver.saveAsTiff(mapPath)) {
					throw new Exception("Failed to save Radiomics Map to: " + mapPath);
				}

				// --- 9. Fusion Image (PNG) の保存 (オプションが選択された場合) ---
				if (saveFusionCheckBox.isSelected() && this.fusionImage != null) {
					// ベース名を取得 (例: "path/to/radiomics_map.tif" -> "path/to/radiomics_map")
					String baseName = userPath;
					int dotIndex = baseName.lastIndexOf('.');
					if (dotIndex > 0) {
						baseName = baseName.substring(0, dotIndex);
					}

					// fusionパスを生成 (例: "path/to/radiomics_map_fusion.png")
					String fusionPath = baseName + "_fusion_";
					
					for(int i=1; i<=fusionImage.getNSlices(); i++) {
						fusionImage.setSlice(i);
						IJ.saveAs(fusionImage, "png", fusionPath+i+".png");
					}
				}

				JOptionPane.showMessageDialog(this, "File(s) saved successfully.", "Save Complete",
						JOptionPane.INFORMATION_MESSAGE);

			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this, "Failed to save file(s): \n" + ex.getMessage(), "Save Error",
						JOptionPane.ERROR_MESSAGE);
				ex.printStackTrace();
			}
		}
    }

    /**
     * [概念] 計算結果のマップをDICOM (Parametric Map等) としてDBに保存します。
     */
    private void onSaveMapToDb() {
         if (radiomicsMap == null) {
            JOptionPane.showMessageDialog(this, "No radiomics map to save.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // TODO:
        // 1. radiomicsMap (ImagePlus) を DICOM Parametric Map オブジェクトに変換
        // 2. 必要なDICOMタグ（参照元画像など）を付与
        // 3. GRAPHYのDcmSenderなどを使ってPACS/DBにストア
        JOptionPane.showMessageDialog(this, "Concept: Save Map to Database");
    }
    
	private void updateFusionImage() {
		ImagePlus foreground = null;
		ImagePlus background = null;
		LUT fLUT = null;//means keep AS-IS.
//		LUT fLUT = Resources.LUT_FIRE.loadLUT();
		if (fusionMapRadio.isSelected() && radiomicsMap != null && fusionBackground != null) {
			foreground = radiomicsMap;
			background = fusionBackground;
			fLUT = radiomicsMapPanel.getLUT();
		} else if (fusionMaskRadio.isSelected() && maskImage != null) {
			/*
			 * マスクラベルが小さい場合、Fusionしても見えないので、255にスケール。
			 */
			StackStatistics stats = new StackStatistics(maskImage);
			double globalMin = stats.min;
			double globalMax = stats.max;
			double scale = 255.0;
			if (globalMax - globalMin > 0) {
				scale = 255.0 / (globalMax - globalMin);
			}

			ImageStack stack = new ImageStack(maskImage.getWidth(), maskImage.getHeight());
			for (int i = 1; i <= maskImage.getNSlices(); i++) {
				ImageProcessor ip = maskImage.getStack().getProcessor(i);
				ByteProcessor bp = new ByteProcessor(ip.getWidth(), ip.getHeight());
				byte[] bpPixels = (byte[]) bp.getPixels();
				// ピクセルごとにスケーリング
				for (int k = 0; k < ip.getPixelCount(); k++) {
					double val = ip.getf(k); // 元の値
					int scaledVal = (int) ((val - globalMin) * scale + 0.5); // +0.5は四捨五入
					// 値を 0-255 の範囲にクリッピング
					if (scaledVal < 0)
						scaledVal = 0;
					if (scaledVal > 255)
						scaledVal = 255;
					bpPixels[k] = (byte) scaledVal;
				}
				stack.addSlice(bp);
			}
			foreground = new ImagePlus("scaled 8-bit mask", stack);
			foreground.copyAttributes(maskImage);
			/**
			 * IMPORTANT
			 */
			background = originalImagePanel.getImagePlus();
			//update original image contrast
			double[] dispWinMinMax = originalImagePanel.getCurrentSlide().getCurrentWindowMinMax();
			double min = dispWinMinMax[0];
			double max = dispWinMinMax[1];
			int pos = background.getCurrentSlice();
			for (int i = 1; i <= background.getNSlices(); i++) {
				background.setSlice(i);
				background.setDisplayRange(min, max);
				background.updateAndDraw();
				System.out.println("before min and max:"+min+","+max);
			}
			background.setSlice(pos);
		}
		
		if (foreground == null || background == null) {
			this.fusionImage = null;
		} else {
			// --- Fusion実行 ---
			int opacity_percent = transparencySlider.getValue();
			double opacity = opacity_percent * 0.01d;
			this.fusionImage = fusion(foreground, background, opacity, fLUT);
		}
		fusionImagePanel.reloadSlideGlasses(this.fusionImage);
	}
	
	/**
	 * 
	 * @param foreground
	 * @param background
	 * @param opacity: 0.0-1.0, where 0.0 is fully transparent and 1.0 is fully opaque.
	 * @return
	 */
	private ImagePlus fusion(ImagePlus foreground, ImagePlus background, double opacity, LUT foregroundLUT) {
		
		if(foreground.getNSlices() != background.getNSlices()) {
			System.out.println("Invalid stack size foreground and background, cannot create fusion.");
			return null;
		}
		int s = foreground.getNSlices();
		ImageStack stack = new ImageStack(background.getWidth(), background.getHeight());
		for(int i=1; i<=s; i++) {
			ImageProcessor ip = foreground.getStack().getProcessor(i).duplicate();
			if(foregroundLUT != null) {
				ip.setLut(foregroundLUT);
			}
			// ImageRoi を作成
			ImageRoi roi = new ImageRoi(0, 0, ip);
			// Roiに透明度を設定
			roi.setOpacity(opacity);
			// 背景画像に ImageRoi をオーバーレイとしてセット
			background.setSlice(i);
			// "flatten" (焼き付け)
			// スタックの場合、1枚目のみに適応されてしまうので取り出す。
			ImagePlus flatten = new ImagePlus(i+"", background.getProcessor().duplicate());
//			flatten.setOverlay(roi, getForeground(), 1/* stroke */, getBackground());
			flatten.setRoi(roi);//also OK.
			flatten = flatten.flatten();//1 slice.
			flatten.updateAndDraw();
			stack.addSlice(flatten.getProcessor());
			background.deleteRoi();
		}
		// RGB images
		ImagePlus fusionImage = new ImagePlus("fusion", stack);
		return fusionImage;
	}
    
	
	private boolean validateInputs() {
		if (originalImage == null) {
			JOptionPane.showMessageDialog(this, "Please load an image AND a mask first.", "Input Required",
					JOptionPane.WARNING_MESSAGE);
			return false;
		}

		if (maskImage != null) {
			if (originalImage.getNSlices() != maskImage.getNSlices()) {
				JOptionPane.showMessageDialog(this, "Please load same size images and masks.", "Mask is invalid.",
						JOptionPane.WARNING_MESSAGE);
				return false;
			}

			if (originalImage.getWidth() != maskImage.getWidth()) {
				JOptionPane.showMessageDialog(this, "Please load same size images and masks.", "Mask is invalid.",
						JOptionPane.WARNING_MESSAGE);
				return false;
			}

			if (originalImage.getHeight() != maskImage.getHeight()) {
				JOptionPane.showMessageDialog(this, "Please load same size images and masks.", "Mask is invalid.",
						JOptionPane.WARNING_MESSAGE);
				return false;
			}
		}
		return true;
	}
	
	private Map<String, Object> settingsMap(String[] fam_and_feature, Properties currentProp) {
		Map<String, Object> settings = new HashMap<>();
		Properties prop = currentProp;
		
		Object v = prop.get(SettingsContext.MASK_LABEL);
		if(v != null) {
			int v_ = Integer.valueOf((String)v);
			settings.put(RadiomicsFeature.LABEL, v_);
		}
		//{"GLCM", "GLRLM", "GLSZM", "GLDZM", "NGTDM", "NGLDM"}; 
		if(fam_and_feature[0].equals(textures[0]/*GLCM*/)) {
			Object o = prop.get(SettingsContext.UseBinCountGLCM);
			if(o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinCountGLCM);
			if(o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinWidthGLCM);
			if(o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String)o));
			}
			o = prop.get(SettingsContext.DeltaGLCM);
			if(o != null) {
				settings.put(RadiomicsFeature.DELTA, Integer.valueOf((String)o));
			}
		}else if(fam_and_feature[0].equals(textures[1]/*GLRLM*/)) {
			Object o = prop.get(SettingsContext.UseBinCountGLRLM);
			if(o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinCountGLRLM);
			if(o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinWidthGLRLM);
			if(o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String)o));
			}
		}else if(fam_and_feature[0].equals(textures[2]/*GLSZM*/)) {
			Object o = prop.get(SettingsContext.UseBinCountGLSZM);
			if(o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinCountGLSZM);
			if(o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinWidthGLSZM);
			if(o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String)o));
			}
		}else if(fam_and_feature[0].equals(textures[3]/*GLDZM*/)) {
			Object o = prop.get(SettingsContext.UseBinCountGLDZM);
			if(o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinCountGLDZM);
			if(o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinWidthGLDZM);
			if(o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String)o));
			}
		}else if(fam_and_feature[0].equals(textures[4]/*NGTDM*/)) {
			Object o = prop.get(SettingsContext.UseBinCountNGTDM);
			if(o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinCountNGTDM);
			if(o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinWidthNGTDM);
			if(o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String)o));
			}
			o = prop.get(SettingsContext.DeltaNGTDM);
			if(o != null) {
				settings.put(RadiomicsFeature.DELTA, Integer.valueOf((String)o));
			}
		}else if(fam_and_feature[0].equals(textures[5]/*NGLDM*/)) {
			Object o = prop.get(SettingsContext.UseBinCountNGLDM);
			if(o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinCountNGLDM);
			if(o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String)o));
			}
			o = prop.get(SettingsContext.BinWidthNGLDM);
			if(o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String)o));
			}
			o = prop.get(SettingsContext.AlphaNGLDM);
			if(o != null) {
				settings.put(RadiomicsFeature.ALPHA, Integer.valueOf((String)o));
			}
			o = prop.get(SettingsContext.DeltaNGLDM);
			if(o != null) {
				settings.put(RadiomicsFeature.DELTA, Integer.valueOf((String)o));
			}
		}
		
		return settings;
	}
    
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
	}

}
