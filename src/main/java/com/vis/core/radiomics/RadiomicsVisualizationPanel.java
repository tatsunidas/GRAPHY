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
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.VR;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageRoi;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.plugin.FolderOpener;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;
import ij.process.ShortProcessor;
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
//	private JButton loadImageFromDbButton, loadMaskFromDbButton;
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
//       JPanel loadDbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
//       loadDbPanel.setBorder(BorderFactory.createTitledBorder("Load from Database"));
//       loadImageFromDbButton = new JButton("Load Image (DB)...");
//       loadMaskFromDbButton = new JButton("Load Mask (DB)...");
//       loadDbPanel.add(loadImageFromDbButton);
//       loadDbPanel.add(loadMaskFromDbButton);
//       gbc.gridy++; 
//       configPanel.add(loadDbPanel, gbc);

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
//		loadImageFromDbButton.addActionListener(e -> onLoadImageFromDb());
//		loadMaskFromDbButton.addActionListener(e -> onLoadMaskFromDb());

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
			this.originalImage = originalImagePanel.getImagePlus(-1,-1);
			if(this.maskImage != null) {
				this.maskImage.copyScale(this.originalImage);
				maskImagePanel.reloadSlideGlasses(this.maskImage);
				this.maskImage = maskImagePanel.getImagePlus(-1,-1);
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
			this.maskImage = maskImagePanel.getImagePlus(-1,-1);
			if (this.originalImage != null) {
				this.maskImage.copyScale(this.originalImage);
				maskImagePanel.reloadSlideGlasses(this.maskImage);
				this.maskImage = maskImagePanel.getImagePlus(-1,-1);
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
    public void onLoadImageFromDb(String pid, String studyUID, String seriesUID) {
    	if(studyUID == null || seriesUID == null) {
    		Log.logger.warning("Images cannnot load. studyUID/seriesUID does not allowed null");
    	}
    	originalImagePanel.loadSeries(pid, studyUID, seriesUID, null);
    	originalImagePanel.doSingleGridLayout();
    	originalImagePanel.showFirstImage();
    	Log.logger.info("Load Images from DB");
    }

    /**
     * [概念] データベースセレクタからマスク画像（DICOM SEG等）をロードします。
     */
    public void onLoadMaskFromDb(String pid, String studyUID, String seriesUID) {
    	if(studyUID == null || seriesUID == null) {
    		Log.logger.warning("Masks cannnot load. studyUID/seriesUID does not allowed null");
    	}
    	maskImagePanel.loadSeries(pid, studyUID, seriesUID, null);
    	maskImagePanel.doSingleGridLayout();
    	maskImagePanel.showFirstImage();
    	maskImagePanel.adjustContrastByMinMax(0, 1);
    	Log.logger.info("Load Mask from DB");
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
	 * 32-bit FloatのRadiomics Mapを、輝度キャリブレーション情報付きの16-bit画像に変換する
	 */
	private ImagePlus convertTo16BitWithCalibration(ImagePlus srcMap) {
	    int w = srcMap.getWidth();
	    int h = srcMap.getHeight();
	    int slices = srcMap.getNSlices();
	    
	    // マップ全体の最小値・最大値を取得
	    ImageStatistics stats = srcMap.getStatistics(Measurements.MIN_MAX);
	    double min = stats.min;
	    double max = stats.max;
	    
	    // 32-bit float値を 16-bit直線マッピング (0 〜 65535) するためのスケーリング係数
	    // 物理値 Y = Slope * ピクセル値X + Intercept 
	    double slope = (max - min) / 65535.0;
	    double intercept = min;
	    
	    if (slope == 0) slope = 1.0; // 単一値マップの場合のゼロ除算防止

	    ImageStack outStack = new ImageStack(w, h);
	    for (int i = 1; i <= slices; i++) {
	        FloatProcessor fp = (FloatProcessor) srcMap.getStack().getProcessor(i);
	        ShortProcessor sp = new ShortProcessor(w, h);
	        
	        for (int p = 0; p < fp.getPixelCount(); p++) {
	            float rawVal = fp.getf(p);
	            // 16bit整数値へ逆算してキャスト
	            int pixel16 = (int) ((rawVal - intercept) / slope + 0.5);
	            // 範囲内にクリッピング
	            if (pixel16 < 0) pixel16 = 0;
	            if (pixel16 > 65535) pixel16 = 65535;
	            sp.set(p, pixel16);
	        }
	        outStack.addSlice(sp);
	    }
	    
	    ImagePlus map16 = new ImagePlus("RadiomicsMap_16bit", outStack);
	    map16.copyScale(srcMap); // 幾何情報コピー
	    
	    // ImageJの輝度キャリブレーション（密度関数）を設定
	    Calibration cal = map16.getCalibration();
	    cal.setFunction(Calibration.STRAIGHT_LINE, new double[]{intercept, slope}, "Value");
	    
	    return map16;
	}

	/**
	 * 保存用：メタデータのコピーとSeries Descriptionの変更を行う
	 */
	private void setupMetadataForSave(ImagePlus targetMap) {
	    if (this.originalImage == null) return;
	    
	    // 1. ImageJのプロパティ（Infoなど）をコピー
	    Object info = this.originalImage.getProperty("Info");
	    if (info != null) {
	        targetMap.setProperty("Info", info);
	    }
	    
	    GDicomTools.headerCopy(originalImage, targetMap);
	    //always set to unsigned, before change it to dcm.
	    for(int i=0; i< targetMap.getNSlices(); i++) {
	    	GDicomTools.setTag(targetMap, i+1, Tag.PixelRepresentation, "0"/*UNSIGNED*/);
	    }
	    
	    HashMap<Integer, DicomImage> dcm = GDicomTools.imagePlusToDcm(targetMap, true);
	    Calibration cal = targetMap.getCalibration();
	    String seriesDesc = GDicomTools.getTag(originalImage, Tag.SeriesDescription);
	    if(seriesDesc == null) seriesDesc = "";
	    for(int k : dcm.keySet()) {
	    	DicomImage inst = dcm.get(k);
	    	DicomObject header = inst.getHeader();
	    	header.setInt(Tag.BitsAllocated, VR.IS, 16);//BitsAllocated(16);
	    	header.setInt(Tag.BitsStored, VR.IS, 15);//BitsAllocated(16);
	    	header.setInt(Tag.HighBit, VR.IS, 15);//BitsAllocated(16);
	    	// 輝度キャリブレーションをDICOMタグ（Rescale Slope / Intercept）に書き換える
		    double[] coefficients = cal.getCoefficients(); // [Intercept, Slope]
		    header.setDouble(Tag.RescaleIntercept, VR.DS, coefficients[0]);
		    header.setDouble(Tag.RescaleSlope, VR.DS, coefficients[1]);
		    /*
		     * vis mapを計算するときに、先に特徴名を付けておくとより良い
		     */
//		    String newSeriesDesc = "[Radiomics Map] " + seriesDesc;
//		    header.setString(Tag.SeriesDescription, VR.LO, newSeriesDesc);
	    }
	    
	    /*
	    // Series Description の先頭に文字列を追加
	    String orgDesc = this.originalImage.getSeriesDescription();
	    String newDesc = "[Radiomics Map] " + (orgDesc != null ? orgDesc : "");
	    newHeader.setSeriesDescription(newDesc);
	    
	    // 新しいメタデータをマップオブジェクトに紐付け
	    targetMap.setCustomMetadata(newHeader);
	    */
	}

	/**
	 * ファイルへ保存する実処理の拡張
	 */
	private void onSaveMap() {
	    if (this.radiomicsMap == null) {
	        JOptionPane.showMessageDialog(this, "保存する可視化マップがありません。", "Warning", JOptionPane.WARNING_MESSAGE);
	        return;
	    }

	    // 1. 32-bitから16-bitへの輝度キャリブレーション付き変換
	    ImagePlus saveMapInstance = convertTo16BitWithCalibration(this.radiomicsMap);
	    
	    // 2. メタデータのコピーと記述の変更
	    setupMetadataForSave(saveMapInstance);

	    // --- 保存先選択ダイアログを表示 ---
	    JFileChooser fc = new JFileChooser();
	    fc.setDialogTitle("Save Radiomics Map");
	    fc.setSelectedFile(new File("radiomics_parametric_map.tif"));
	    
	    FileNameExtensionFilter tiffFilter = new FileNameExtensionFilter("TIFF Image (*.tif)", "tif");
	    fc.addChoosableFileFilter(tiffFilter);
	    
	    // DB保存ボタンとは別に、ファイル保存形式としてDICOM(*.dcm)も選べるようにする場合
	    FileNameExtensionFilter dicomFilter = new FileNameExtensionFilter("DICOM ファイル (*.dcm)", "dcm");
	    fc.addChoosableFileFilter(dicomFilter);
	    
	    fc.setFileFilter(tiffFilter);

	    if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
	        try {
	            String path = fc.getSelectedFile().getAbsolutePath();
	            FileFilter selectedFilter = fc.getFileFilter();
	            
	            if (selectedFilter == dicomFilter || path.toLowerCase().endsWith(".dcm")) {
	                // TODO: DICOMとしてファイル保存するロジック
	                // saveAsDicomFile(saveMapInstance, path);
	                System.out.println("DICOMファイルとして保存: " + path);
	            } else {
	                // Tiffファイルとして保存（ImageJのFileSaverはCalibration情報もファイル内に保持してくれます）
	                if (!path.toLowerCase().endsWith(".tif") && !path.toLowerCase().endsWith(".tiff")) {
	                    path += ".tif";
	                }
	                FileSaver fs = new FileSaver(saveMapInstance);
	                if (fs.saveAsTiff(path)) {
	                    JOptionPane.showMessageDialog(this, "Tiff可視化マップを保存しました。");
	                }
	            }
	        } catch (Exception ex) {
	            ex.printStackTrace();
	            JOptionPane.showMessageDialog(this, "保存中にエラーが発生しました: " + ex.getMessage());
	        }
	    }
	}

	/**
	 * Database(PACS)へ保存（ストア）する実処理の拡張
	 */
	private void onSaveMapToDb() {
	    if (this.radiomicsMap == null) {
	        JOptionPane.showMessageDialog(this, "データベースへ保存する可視化マップがありません。", "Warning", JOptionPane.WARNING_MESSAGE);
	        return;
	    }
	    
	    // 1. 16-bit & キャリブレーション変換
	    ImagePlus saveMapInstance = convertTo16BitWithCalibration(this.radiomicsMap);
	    
	    // 2. メタデータ再構成
	    setupMetadataForSave(saveMapInstance);
	    
	    // 3. GRAPHYのDatabaseHandlerやDcmSender等を利用してDBへストア
	    try {
	        // 例: 
	        // DatabaseHandler.getInstance().storeImagePlusAsNewSeries(saveMapInstance);
	        JOptionPane.showMessageDialog(this, "可視化マップを新しいDICOMシリーズとしてデータベースに保存しました。", "Success", JOptionPane.INFORMATION_MESSAGE);
	    } catch (Exception ex) {
	        JOptionPane.showMessageDialog(this, "DB保存に失敗しました: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
	    }
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
			background = originalImagePanel.getImagePlus(-1,-1);
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
