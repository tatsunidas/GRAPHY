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
import javax.swing.filechooser.FileNameExtensionFilter;

import com.vis.core.fusion.FusionDisplay;
import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.db.DatabaseHandler;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.plugin.FolderOpener;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
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

	// test
	public static void main(String args[]) {
		/**
		 * add VM option -Djava.library.path=./native/native_opencv/linux-x86-64
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
			vPanel.onLoadMask("/home/tatsunidas/ダウンロード/case_test/Mask_Plaque/left");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// --- UI Components ---
//	private JButton loadImageButton, loadMaskButton;
	private JComboBox<String> featureComboBox;
	private JSpinner filterSizeSpinner;
	private JSpinner strideSpinner;
	private JButton executeSliceButton, executeAllButton;
	private JButton saveMapAsTiffButton, saveMapAsDcmButton, saveMapToDbButton;
	
	private JPanel configPanel;
	private JSplitPane splitPane;

	private Praparat originalImagePanel;
	private Praparat maskImagePanel;
	private Praparat radiomicsMapPanel;
	private Praparat fusionImagePanel;
	private JRadioButton fusionMapRadio, fusionMaskRadio;
	private JSlider transparencySlider;

	// --- Data Holders ---
//	private ImagePlus originalImage;
//	private ImagePlus maskImage;//not aligned
	
	private ImagePlus calcImage;//calculate original images stack for feature calculation
	private ImagePlus alignedMask;//calculate aligned mask stack for feature calculation
	private ImagePlus radiomicsMap;
	private ImagePlus fusionImage;

	RadiomicsSettings radSetting;

	final String[] textures = { "GLCM", "GLRLM", "GLSZM", "GLDZM", "NGTDM", "NGLDM" };

	public RadiomicsVisualizationPanel(RadiomicsSettings radSetting) {
		super();
		this.radSetting = radSetting;
		buildup();
		addListeners();
	}

	void buildup() {

		this.setLayout(new BorderLayout());

		// configuration panel
		configPanel = new JPanel();
		configPanel.setLayout(new GridBagLayout());

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0; // すべてのコンポーネントを同じ列(0)に配置
		gbc.gridy = 0; // 最初の行
		gbc.weightx = 1.0; // 水平方向のリサイズ時に幅を広げる
		gbc.weighty = 0.0; // 垂直方向には広がらない（スペーサーが担当）
		gbc.fill = GridBagConstraints.HORIZONTAL; // 水平方向にいっぱいに広げる
		gbc.anchor = GridBagConstraints.NORTH; // セル内で上寄せにする
		gbc.insets = new Insets(2, 2, 2, 2); // コンポーネント間の余白

		// feature calculation settings
		JPanel settingsPanel = new JPanel();
		settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Calculation Settings"));
		/*
		 * choose a texture feature (no multiple selection)
		 */
		JPanel featurePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		featurePanel.add(new JLabel("Texture Feature:"));

		// load featureNames
		List<String> names = new ArrayList<>();

		for (String fam : textures) {
			names.addAll(RadiomicsSettings.featureNames(fam));
		}
		String[] features = new String[names.size()];
		for (int i = 0; i < names.size(); i++) {
			features[i] = names.get(i);
		}
		featureComboBox = new JComboBox<>(features);
		featurePanel.add(featureComboBox);
		settingsPanel.add(featurePanel);

		/*
		 * set image filter size and stride
		 */
		JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		
		// Filter Size 設定
		filterPanel.add(new JLabel("Filter Size (odd):"));
		SpinnerNumberModel spinnerModel = new SpinnerNumberModel(9, 3, 99, 2);// default, min, max, step
		filterSizeSpinner = new JSpinner(spinnerModel);
		filterSizeSpinner.setEditor(new JSpinner.NumberEditor(filterSizeSpinner, "#"));
		filterSizeSpinner.setPreferredSize(new Dimension(60, 25));
		filterPanel.add(filterSizeSpinner);
		//spacer
		filterPanel.add(javax.swing.Box.createHorizontalStrut(15));
		// Stride
		filterPanel.add(new JLabel("Stride[X,Y]:"));
		SpinnerNumberModel strideModel = new SpinnerNumberModel(3, 1, 99, 1); // default, min, max, step
		strideSpinner = new JSpinner(strideModel);
		strideSpinner.setEditor(new JSpinner.NumberEditor(strideSpinner, "#"));
		strideSpinner.setPreferredSize(new Dimension(60, 25));
		filterPanel.add(strideSpinner);

		settingsPanel.add(filterPanel);
		
		/*
		 * execute slice btn and show on
		 */
		JPanel executePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		executePanel.setBorder(BorderFactory.createTitledBorder("Execute"));
		executeSliceButton = new JButton("Execute Current IMAGE Slice");
		executeAllButton = new JButton("Execute All Slices(take long time)");
		executePanel.add(executeSliceButton);
		executePanel.add(executeAllButton);
		settingsPanel.add(executePanel);
		
		gbc.gridy++;
		configPanel.add(settingsPanel, gbc);

		// save function
		JPanel savePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		savePanel.setBorder(BorderFactory.createTitledBorder("Save Results"));
		saveMapAsTiffButton = new JButton("Save Map as Tiff...");
		saveMapAsDcmButton = new JButton("Save Map as DICOM...");
		saveMapToDbButton = new JButton("Save Map to DB");
		savePanel.add(saveMapAsTiffButton);
		savePanel.add(saveMapAsDcmButton);
		savePanel.add(saveMapToDbButton);
		gbc.gridy++;
		configPanel.add(savePanel, gbc);
		
		// select images and masks using file system
//		JPanel loadFilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
//		loadFilePanel.setBorder(BorderFactory.createTitledBorder("Load from File"));
//		loadImageButton = new JButton("Load Image...");
//		loadMaskButton = new JButton("Load Mask...");
//		loadFilePanel.add(loadImageButton);
//		loadFilePanel.add(loadMaskButton);
//		gbc.gridy++;
//		configPanel.add(loadFilePanel, gbc);

		// add spacer
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
		JPanel visualizationPanel = new JPanel(new BorderLayout(5, 5));

		JPanel visGridPanel = new JPanel(new GridLayout(2, 2, 5, 5));
		visGridPanel.setBorder(BorderFactory.createEtchedBorder());

		originalImagePanel = new Praparat(ViewMode.Normal);
		maskImagePanel = new Praparat(ViewMode.Normal);
		radiomicsMapPanel = new Praparat(ViewMode.Normal);
		fusionImagePanel = new Praparat(ViewMode.Normal);

		visGridPanel.add(originalImagePanel);
		visGridPanel.add(maskImagePanel);
		visGridPanel.add(radiomicsMapPanel);
		visGridPanel.add(fusionImagePanel);
		visualizationPanel.add(visGridPanel, BorderLayout.CENTER);

		// Fusion
		JPanel fusionControlsPanel = new JPanel();
		fusionControlsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 30, 0));
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
		transparencySlider.setMinorTickSpacing(5); // 5ごとに小目盛り
		transparencySlider.setPaintTicks(true); // 目盛りを表示
		transparencySlider.setPaintLabels(true); // ラベル (0, 25, 50, 75, 100) を表示
		transparencySlider.setPreferredSize(new Dimension(250, 45)); // スライダーの推奨サイズ
		transparencyPanel.add(transparencySlider);

		// コントロールパネルに2つのパネルを追加
		fusionControlsPanel.add(fusionTargetPanel);
		fusionControlsPanel.add(transparencyPanel);

		visualizationPanel.add(fusionControlsPanel, BorderLayout.SOUTH);
		
		configScrollPane.setMinimumSize(new Dimension(30,30));
		visualizationPanel.setMinimumSize(new Dimension(30,30));

		// --- Split Pane ---
		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, visualizationPanel, configScrollPane);
		splitPane.setResizeWeight(1.0);
		splitPane.setOneTouchExpandable(true);

		this.add(splitPane, BorderLayout.CENTER);

		/*
		 * This is just memo. please ignore. TEST: roi etc functions are ignored.(such
		 * functions are available in the common viewer. )
		 */
	}
	
	/**
	 * 下部の設定パネルの推奨サイズに合わせて、SplitPaneのDivider位置を動的にフィットさせます。
	 */
	public void adjustDividerLocation() {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (splitPane != null && splitPane.getHeight() > 0 && configPanel != null) {
					// グリッドバッグで組まれた中身（configPanel）の実際に必要な高さを取得
					int preferredConfigHeight = configPanel.getPreferredSize().height;
					
					// ボーダーやスクロールバーの余白を考慮したわずかなバッファ（25ピクセル程度）
					int padding = 25;
					
					// 計算：全体の高さ - (設定パネルに必要な高さ + Dividerの厚み + 余白)
					int targetLocation = splitPane.getHeight() - (preferredConfigHeight + splitPane.getDividerSize() + padding);
					
					// 計算結果がウィンドウ内に収まる妥当な範囲かチェックして適用
					if (targetLocation > 50 && targetLocation < splitPane.getHeight() - 50) {
						splitPane.setDividerLocation(targetLocation);
					} else {
						// 万が一計算が破綻した場合の安全なフォールバック（下部を全体の35%にする）
						splitPane.setDividerLocation((int)(splitPane.getHeight() * 0.65));
					}
				}
			}
		});
	}

	private void addListeners() {
		// --- Load Actions ---
//		loadImageButton.addActionListener(e -> onLoadImage());
//		loadMaskButton.addActionListener(e -> onLoadMask());
		
		// --- Execute Actions ---
		executeSliceButton.addActionListener(e -> onExecute(false));
		executeAllButton.addActionListener(e -> onExecute(true));

		// --- Save Actions ---
		saveMapAsTiffButton.addActionListener(e -> onSaveMap(false));
		saveMapAsDcmButton.addActionListener(e -> onSaveMap(true));
		saveMapToDbButton.addActionListener(e -> onSaveMapToDb());

		// --- Fusion Actions ---
		ActionListener fusionTargetListener = e -> updateFusionImage();
		fusionMapRadio.addActionListener(fusionTargetListener);
		fusionMaskRadio.addActionListener(fusionTargetListener);
		transparencySlider.addChangeListener(e -> {
			JSlider slider = (JSlider) e.getSource();
			// fire off after pressed.
			if (!slider.getValueIsAdjusting()) {
				updateFusionImage();
			}
		});
	}
	
	private void initMetaDataUsingSrcImage(ImagePlus input, ImagePlus src, boolean isSecondaryCapture) {
		if(input == null || input.getNSlices() == 0) {
			Log.logger.warning("Inputed imageplus has no images...");
			return;
		}
		if(src == null || src.getNSlices() == 0) {
			Log.logger.warning("To add UIDs to a new inputed imageplus, reference images are required...");
			return;
		}
		
		String sopClassUid = GDicomTools.getTag(src, Tag.SOPClassUID);
		if(isSecondaryCapture)  sopClassUid = UID.SecondaryCaptureImageStorage.toString();
		
		String pid = GDicomTools.getTag(src, Tag.PatientID);
		String studyUid = GDicomTools.getTag(src, Tag.StudyInstanceUID);
		String refUid = GDicomTools.getTag(src, Tag.FrameOfReferenceUID);
		String seriesUid = UIDUtils.createUID();
		String sopInstUid = UIDUtils.createUID();
		
		int cTotal = input.getNChannels();
		int zTotal = input.getNSlices();
		int tTotal = input.getNFrames();
		int instNo = 1;

		// ZCTを明示して処理する
		for (int t = 1; t <= tTotal; t++) {
			for (int z = 1; z <= zTotal; z++) {
				for (int c = 1; c <= cTotal; c++) {
					GDicomTools.setTag(input, z, c, t, Tag.PatientID, pid);
					GDicomTools.setTag(input, z, c, t, Tag.StudyInstanceUID, studyUid);
					GDicomTools.setTag(input, z, c, t, Tag.SeriesInstanceUID, seriesUid);
					if (refUid != null) {
						GDicomTools.setTag(input, z, c, t, Tag.FrameOfReferenceUID, refUid);
					}
					GDicomTools.setTag(input, z, c, t, Tag.SOPClassUID, sopClassUid);
					GDicomTools.setTag(input, z, c, t, Tag.MediaStorageSOPClassUID, sopInstUid);
					GDicomTools.setTag(input, z, c, t, Tag.SOPInstanceUID, sopInstUid);
					GDicomTools.setTag(input, z, c, t, Tag.InstanceNumber, String.valueOf(instNo++));

					// =========================================================
					// ★ 追加：表示とDICOM変換に必須な「空間座標」と「解像度」をコピー
					// =========================================================
					String ipp = GDicomTools.getTag(src, z, c, t, "0020,0032"); // Image Position (Patient)
					if (ipp != null)
						GDicomTools.setTag(input, z, c, t, "0020,0032", ipp);

					String iop = GDicomTools.getTag(src, z, c, t, "0020,0037"); // Image Orientation (Patient)
					if (iop != null)
						GDicomTools.setTag(input, z, c, t, "0020,0037", iop);

					String pixelSpacing = GDicomTools.getTag(src, z, c, t, "0028,0030"); // Pixel Spacing
					if (pixelSpacing != null)
						GDicomTools.setTag(input, z, c, t, "0028,0030", pixelSpacing);

					String sliceThickness = GDicomTools.getTag(src, z, c, t, "0018,0050"); // Slice Thickness
					if (sliceThickness != null)
						GDicomTools.setTag(input, z, c, t, "0018,0050", sliceThickness);
				}
			}
		}
	}

	@SuppressWarnings("unused")
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
		if (f.isDirectory()) {
			originalImage = FolderOpener.open(path);
		} else {
			Opener opener = new Opener();
			originalImage = opener.openImage(path);
		}
		if (originalImage != null) {
			String pid = GDicomTools.getTag(originalImage, Tag.PatientID);
			String stUid = GDicomTools.getTag(originalImage, Tag.StudyInstanceUID);
			String seUid = GDicomTools.getTag(originalImage, Tag.SeriesInstanceUID);
			String siUid = GDicomTools.getTag(originalImage, Tag.SOPInstanceUID);
			if(pid == null || stUid == null || seUid == null || siUid == null) {
				JOptionPane.showConfirmDialog(this, "This images do not have essential UIDs to load Radiomics Function.\n Select DICOM series again.");
				return;
			}
			originalImagePanel.reloadSlideGlasses(originalImage);
		} else {
			throw new Exception("Failed to open image.");
		}
	}

	@SuppressWarnings("unused")
	private void onLoadMask() {
		if(originalImagePanel.getAllSlides() == null || originalImagePanel.getAllSlides().size() == 0) {
			JOptionPane.showConfirmDialog(this, "Please load src images first. After that, try again loading masks.");
			return;
		}
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			try {
				String path = fc.getSelectedFile().getAbsolutePath();
				onLoadMask(path);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this, "Failed to load mask: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
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
			ImagePlus src = originalImagePanel.getImagePlus(1,1);
			initMetaDataUsingSrcImage(originalImagePanel.getImagePlus(1,1), maskImage, false);
			maskImage.copyScale(src);
			StackStatistics stats = new StackStatistics(maskImage);
		    int max = (int) stats.max;
			maskImagePanel.reloadSlideGlasses(maskImage);
			maskImagePanel.adjustContrastByMinMax(0, max);
		} else {
			throw new Exception("Failed to open image.");
		}
	}

	public void onLoadImageFromDb(String pid, String studyUID, String seriesUID) {
		if (studyUID == null || seriesUID == null) {
			Log.logger.warning("Images cannnot load. studyUID/seriesUID does not allowed null");
		}
		originalImagePanel.loadSeries(pid, studyUID, seriesUID, null);
		originalImagePanel.doSingleGridLayout();
		originalImagePanel.showFirstImage();
		Log.logger.info("Load Images from DB");
	}

	/**
	 * データベースセレクタからマスク画像（DICOM SEG等）をロードします。
	 */
	public void onLoadMaskFromDb(String pid, String studyUID, String seriesUID) {
		if (studyUID == null || seriesUID == null) {
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
	 * @param slice: 1 to N,  -1 means calculate all.
	 */
	private void onExecute(boolean executeAllSlices) {
		
		// 現在画面に表示されているオリジナル画像の [Z, C, T] 座標を取得
		SlideGlass sg = originalImagePanel.getCurrentSlide();
		if(sg == null) {
			Log.logger.info("SlideGlass is null");
			return;
		}
		int[] orgZct = originalImagePanel.getZCTArray(sg);
		int orgZ = orgZct[0];
		int orgC = orgZct[1];
		int orgT = orgZct[2];
		
		// 表示中のチャンネル(C)・時相(T)に対応するオリジナル画像の純粋なZスタックを抽出
		calcImage = originalImagePanel.getImagePlus(orgC, orgT);

		// 4. マスク画像（SEG）のペアリング処理
		alignedMask = null;
		if (maskImagePanel != null && maskImagePanel.getAllSlides() != null) {
			// 現在画面に表示されているマスクの [C, T] を取得（表示中のセグメント部位を対象とする）
			int maskCurrentIdx = maskImagePanel.getCurrentSlidePos();
			int[] maskZct = maskImagePanel.calcZCTArrayFromIndex(maskCurrentIdx);
			int maskC = maskZct[1];
			int maskT = maskZct[2];

			// オリジナル画像の空間に完全にアライメントされたマスクを生成（枚数違いを自動パディング）
			alignedMask = com.vis.core.fusion.ImagePairingEngine.alignMaskToOriginalSpace(originalImagePanel, orgC,
					orgT, maskImagePanel, maskC, maskT);
			if (alignedMask != null) {
				this.alignedMask.copyScale(this.calcImage);
			}
//			ij.IJ.saveAsTiff(alignedMask, "test_mask.tif");
		}
		
		if (!validateInputs()) {
			return;
		}
		
		int slice = -1;
		if(!executeAllSlices) {
			slice = orgZ + 1;
		}

		// 5. 要件：マスクが空の場合はフルフェイスマスク（画像全体を対象）を自動生成
		if (alignedMask == null) {
			alignedMask = createFullFaceMask(calcImage);
		}
		
		String featureClass = (String) featureComboBox.getSelectedItem();
		String familyAndFeature[] = featureClass.split("_");
		int filterSize = (int) filterSizeSpinner.getValue();
		int stride = (int) strideSpinner.getValue();

		// build settings from radSetting
		Properties settingsProp = radSetting.currentSettings();
		Map<String, Object> settings = settingsMap(familyAndFeature, settingsProp);

		boolean d3_mode = Boolean.valueOf((String) settingsProp.get(SettingsContext.D3Basis));
		boolean d2_mode = d3_mode == false;

		FeatureSpecifier<RadiomicsFeature> featuresToCalculate = new FeatureSpecifier<>(
				radSetting.loadClass(familyAndFeature[0] + "Features"), radSetting.loadFeatureType(familyAndFeature),
				settings);

		FeatureCalculator calculator = new FeatureCalculatorFactory().create(featuresToCalculate);
		
		// === 【デバッグ用】RadiomicsJ 計算実行直前の状態チェック ===
		Log.logger.info("====== DEBUG INFO ======");
		Log.logger.info(
				"Image: " + calcImage.getWidth() + "x" + calcImage.getHeight() + ", Slices: " + calcImage.getNSlices());
		Log.logger.info("Mask : " + alignedMask.getWidth() + "x" + alignedMask.getHeight() + ", Slices: "
				+ alignedMask.getNSlices());

		// 1. マスク画像の画素値（最小・最大）を調べる
		ij.process.StackStatistics maskStats = new ij.process.StackStatistics(alignedMask);
		Log.logger.info("Mask Pixel Min: " + maskStats.min + ", Max: " + maskStats.max);

		// 2. RadiomicsJ に渡す設定（Target Label）を調べる
		Object targetLabelObj = settings.get(RadiomicsFeature.LABEL);
		int targetLabel = targetLabelObj != null ? (Integer) targetLabelObj : 1; // RadiomicsJのデフォルトは1
		Log.logger.info("Target Mask Label (Settings): " + targetLabel);

		if (maskStats.max == 0) {
			Log.logger.warning("★マスク画像が完全に真っ黒（全ピクセル0）です。計算対象領域がありません。");
		} else if (maskStats.max != targetLabel && maskStats.max != 0) {
			Log.logger.warning("★警告: マスク画像の最大画素値 (" + maskStats.max + ") と、計算対象ラベル (" + targetLabel
					+ ") が異なっています！これがNullの原因の可能性が高いです。");
		}

		Log.logger.info("Feature: " + featureClass + ", Filter: " + filterSize + ", Stride: " + stride);
		Log.logger.info("========================");

		// 2. マップを生成 (元のコード)
		long startTime = System.currentTimeMillis();
		ImagePlus radiomicsMapRaw = FeatureVisualizationMap.generateFeatureMap(calcImage, alignedMask, slice, calculator,
				filterSize, d2_mode, stride);
		// ==============================================================

		// ★念のため、16-bit変換に入る前にNull判定をログに出す
		if (radiomicsMapRaw == null) {
			Log.logger.severe("★ generateFeatureMap の戻り値が直接 Null になっています。");
		} else {
			Log.logger.info("★ generateFeatureMap は成功しました (16bit変換へ進みます)。");
		}
		
		long endTime = System.currentTimeMillis();
		Log.logger.info("--> Generation took " + (endTime - startTime) + " ms.");
		if (radiomicsMapRaw != null) {
			// ==============================================================
			// ★ 単一スライス実行時(1枚出力)、元のZ位置にはめ込んで38枚のスタックにする
			// ==============================================================
			if (radiomicsMapRaw.getNSlices() == 1 && calcImage.getNSlices() > 1 && slice != -1) {
				ImageStack paddedStack = new ImageStack(radiomicsMapRaw.getWidth(), radiomicsMapRaw.getHeight());
				for (int i = 1; i <= calcImage.getNSlices(); i++) {
					if (i == slice) {
						paddedStack.addSlice(radiomicsMapRaw.getProcessor());
					} else {
						// 計算対象外のスライスは NaN（計算値なし）の空プロセッサで埋める
						FloatProcessor emptyFp = new FloatProcessor(radiomicsMapRaw.getWidth(),
								radiomicsMapRaw.getHeight());
						emptyFp.setValue(Double.NaN);
						emptyFp.fill();
						paddedStack.addSlice(emptyFp);
					}
				}
				radiomicsMapRaw = new ImagePlus("Padded_Map", paddedStack);
			}

			// ==============================================================
			// ★　Stride等で縮小されている場合、元のサイズ(512x512)に拡大する
			// ==============================================================
			if (radiomicsMapRaw.getWidth() != calcImage.getWidth()
					|| radiomicsMapRaw.getHeight() != calcImage.getHeight()) {
				ImageStack resizedStack = new ImageStack(calcImage.getWidth(), calcImage.getHeight());
				for (int i = 1; i <= radiomicsMapRaw.getNSlices(); i++) {
					ij.process.ImageProcessor ip = radiomicsMapRaw.getStack().getProcessor(i);
					// 特徴量マップなので、補間(ぼかし)をかけずに四角く引き伸ばす
					ip.setInterpolationMethod(ij.process.ImageProcessor.NONE);
					resizedStack.addSlice(ip.resize(calcImage.getWidth(), calcImage.getHeight()));
				}
				radiomicsMapRaw = new ImagePlus("Resized_Map", resizedStack);
			}
			this.radiomicsMap = convertTo16BitWithCalibration(radiomicsMapRaw);
			// add series description when saving.
			this.radiomicsMap.setProperty("RadiomicsFeatureName", featureClass);
			//to show on prap
			initMetaDataUsingSrcImage(this.radiomicsMap, calcImage, true/* secondary */);
			radiomicsMapPanel.reloadSlideGlasses(radiomicsMap);
			radiomicsMap.resetDisplayRange();
			if(slice != -1) {
				int zct = radiomicsMapPanel.calcZctIndex(new int[] { slice - 1, 0, 0 });
				radiomicsMapPanel.setImagePositionUsingSlider(zct);
			}
		} else {
			JOptionPane.showConfirmDialog(this, "Radiomics map was not created... Please check logs. ");
		}

		// Fusion画像を更新
		updateFusionImage();
		if(slice != -1) {
			int zct = fusionImagePanel.calcZctIndex(new int[] { slice - 1, 0, 0 });
			fusionImagePanel.setImagePositionUsingSlider(zct);
		}
	}
	
	/**
	 * マスクがロードされていない場合に、画像全体をカバーするフルフェイスマスクを動的に生成するヘルパーメソッド
	 * (RadiomicsVisualizationPanelクラスの末尾などに追記してください)
	 */
	private ImagePlus createFullFaceMask(ImagePlus srcImg) {
		int w = srcImg.getWidth();
		int h = srcImg.getHeight();
		int slices = srcImg.getNSlices();
		
		Properties settingsProp = radSetting.currentSettings();
		int label = 1;
		try {
			label = Integer.parseInt(settingsProp.getProperty(SettingsContext.MASK_LABEL, "1"));
		} catch (NumberFormatException e) { /* default to 1 */ }

		ImageStack maskStack = new ImageStack(w, h);
		for (int i = 1; i <= slices; i++) {
			ByteProcessor bp = new ByteProcessor(w, h);
			bp.setValue(label);
			bp.fill(); // 全ピクセルをターゲットラベル値で埋める
			maskStack.addSlice(bp);
		}
		
		ImagePlus fullFaceMask = new ImagePlus("FullFaceMask", maskStack);
		fullFaceMask.copyScale(srcImg);
		return fullFaceMask;
	}

	/**
	 * 32-bit FloatのRadiomics Mapを、輝度キャリブレーション情報付きの16-bit画像に変換する
	 */
	private ImagePlus convertTo16BitWithCalibration(ImagePlus rawMap) {
		int w = rawMap.getWidth();
		int h = rawMap.getHeight();
		int slices = rawMap.getNSlices();

		// マップ全体の最小値・最大値を取得
		ImageStatistics stats = rawMap.getStatistics(Measurements.MIN_MAX);
		double min = stats.min;
		double max = stats.max;

		// 32-bit float値を 16-bit直線マッピング (0 〜 65535) するためのスケーリング係数
		// 物理値 Y = Slope * ピクセル値X + Intercept
		double slope = (max - min) / 65535.0;
		double intercept = min;

		if (slope == 0)
			slope = 1.0; // 単一値マップの場合のゼロ除算防止

		ImageStack outStack = new ImageStack(w, h);
		for (int i = 1; i <= slices; i++) {
			FloatProcessor fp = (FloatProcessor) rawMap.getStack().getProcessor(i);
			ShortProcessor sp = new ShortProcessor(w, h);

			for (int p = 0; p < fp.getPixelCount(); p++) {
				float rawVal = fp.getf(p);
				// 16bit整数値へ逆算してキャスト
				int pixel16 = (int) ((rawVal - intercept) / slope + 0.5);
				// 範囲内にクリッピング
				if (pixel16 < 0)
					pixel16 = 0;
				if (pixel16 > 65535)
					pixel16 = 65535;
				sp.set(p, pixel16);
			}
			outStack.addSlice(sp);
		}

		ImagePlus map16 = new ImagePlus("RadiomicsMap_16bit", outStack);
		map16.copyScale(rawMap); // 幾何情報コピー
		// ImageJの輝度キャリブレーション（密度関数）を設定
		Calibration cal = map16.getCalibration();
		cal.setFunction(Calibration.STRAIGHT_LINE, new double[] { intercept, slope }, "Value");

		return map16;
	}

	/**
	 * 保存用：メタデータのコピーとSeries Descriptionの変更を行う
	 */
	private HashMap<Integer, DicomImage> setupMetadataToSave(ImagePlus targetMap) {
		if (this.calcImage == null)
			return null;

		// 1. ImageJのプロパティ（Infoなど）をコピー
		Object info = this.calcImage.getProperty("Info");
		if (info != null) {
			targetMap.setProperty("Info", info);
		}
		
		String featureName = targetMap.getProp("RadiomicsFeatureName");
		if(featureName == null) {
			featureName = "";
		}
		
		GDicomTools.headerCopy(calcImage, targetMap);
		
		//set series number
		int seriesNumber = 100;
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db != null) {
			String pid = GDicomTools.getTag(calcImage, Tag.PatientID);
			String studyUid = GDicomTools.getTag(calcImage, Tag.StudyInstanceUID);
			if(pid != null && studyUid != null) {
				int seriesNumber_ = db.getNumOfSeries(pid, studyUid);
				if(seriesNumber_ != 0) {
					seriesNumber = seriesNumber_ + 1;
				}
			}
		}
		
		HashMap<Integer, DicomImage> dcmStack = GDicomTools.imagePlusToDcm(targetMap, true);
		Calibration cal = targetMap.getCalibration();
		String seriesDesc = GDicomTools.getTag(calcImage, Tag.SeriesDescription);
		if (seriesDesc == null)
			seriesDesc = "";
		String seriesUID = GDicomTools.getTag(targetMap, Tag.SeriesInstanceUID);
		if(seriesUID == null) {
			seriesUID = UIDUtils.createUID();
		}
		for (int k : dcmStack.keySet()) {
			DicomImage inst = dcmStack.get(k);
			DicomObject header = inst.getHeader();
			String instUid = UIDUtils.createUID();
			//change UIDs
			header.setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage.toString());
			header.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, instUid);
			header.setString(Tag.SOPInstanceUID, VR.UI, instUid);
			
			//image attrs
			header.setInt(Tag.PixelRepresentation, VR.IS, 0);/* UNSIGNED */
			header.setInt(Tag.BitsAllocated, VR.IS, 16);// BitsAllocated(16);
			header.setInt(Tag.BitsStored, VR.IS, 15);// BitsAllocated(16);
			header.setInt(Tag.HighBit, VR.IS, 15);// BitsAllocated(16);
			// 輝度キャリブレーションをDICOMタグ（Rescale Slope / Intercept）に書き換える
			double[] coefficients = cal.getCoefficients(); // [Intercept, Slope]
			header.setDouble(Tag.RescaleIntercept, VR.DS, coefficients[0]);
			header.setDouble(Tag.RescaleSlope, VR.DS, coefficients[1]);
			/*
			 * vis mapを計算するときに、RadiomicsMapのプロパティに特徴名を保存しておく
			 */
		    String newSeriesDesc = featureName + " " + seriesDesc;
		    header.setString(Tag.SeriesDescription, VR.LO, newSeriesDesc);
		    header.setInt(Tag.SeriesNumber, VR.IS, seriesNumber);
		}
		return dcmStack;
	}

	/**
	 * ファイルへ保存する実処理の拡張
	 */
	private void onSaveMap(boolean outdcm) {
		if (this.radiomicsMap == null) {
			JOptionPane.showMessageDialog(this, "保存する可視化マップがありません。", "Warning", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		if(!outdcm) {
			// --- 保存先選択ダイアログを表示 ---
			JFileChooser fc = new JFileChooser();
			fc.setDialogTitle("Save Radiomics Map");
			fc.setSelectedFile(new File("radiomics_parametric_map.tif"));
			FileNameExtensionFilter tiffFilter = new FileNameExtensionFilter("TIFF Image (*.tif)", "tif");
			fc.addChoosableFileFilter(tiffFilter);
			fc.setFileFilter(tiffFilter);
			if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				try {
					String path = fc.getSelectedFile().getAbsolutePath();
					// Tiffファイルとして保存（ImageJのFileSaverはCalibration情報もファイル内に保持してくれます）
					if (!path.toLowerCase().endsWith(".tif") && !path.toLowerCase().endsWith(".tiff")) {
						path += ".tif";
					}
					FileSaver fs = new FileSaver(this.radiomicsMap);
					if (fs.saveAsTiff(path)) {
						JOptionPane.showMessageDialog(this, "Tiff可視化マップを保存しました。");
					}
					return;
				} catch (Exception ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(this, "保存中にエラーが発生しました: " + ex.getMessage());
					return;
				}
			}
		}else {
			//save as dcm
			// 2. メタデータのコピーと記述の変更
			HashMap<Integer, DicomImage> dcms = setupMetadataToSave(this.radiomicsMap);

			// --- 保存先選択ダイアログを表示 ---
			JFileChooser fc = new JFileChooser();
			fc.setDialogTitle("Save Radiomics Map");
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fc.setSelectedFile(new File("radiomics_parametric_map"));
			DicomWriter writer = DicomWriter.newDicomWriter();
			if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				try {
					String parent_path = fc.getSelectedFile().getAbsolutePath();
					for(int idx : dcms.keySet()) {
						DicomImage im = dcms.get(idx);
						int instNo = im.getHeader().getInt(Tag.InstanceNumber, idx);
						writer.write(im.getHeader(), UID.ImplicitVRLittleEndian.uid(), parent_path+File.separator+instNo+".dcm");
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(this, "Error occuered when saving: " + ex.getMessage());
				}
			}
		}
	}

	/**
	 * Database(PACS)へ保存（ストア）する実処理の拡張
	 */
	private void onSaveMapToDb() {
		if (this.radiomicsMap == null) {
			JOptionPane.showMessageDialog(this, "Radiomics Map not ready.\nPlease create Radiomics Map first.",
					"Warning", JOptionPane.WARNING_MESSAGE);
			return;
		}
		// copy and replace meta data to new series.
		HashMap<Integer, DicomImage> dcms = setupMetadataToSave(this.radiomicsMap);

		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			JOptionPane.showMessageDialog(this, "GRAPHY DB does not ready, can not save to DB.");
			return;
		}
		String pid = GDicomTools.getTag(radiomicsMap, Tag.PatientID);
		String studyUID = GDicomTools.getTag(radiomicsMap, Tag.StudyInstanceUID);
		String seriesUID = GDicomTools.getTag(radiomicsMap, Tag.SeriesInstanceUID);
		if (pid == null || studyUID == null || seriesUID == null) {
			JOptionPane.showMessageDialog(this,
					"Can not create new series, this images does not have dicom attributes.");
			return;
		}
		try {
			db.storeDicomImagesToDb(dcms);
			JOptionPane.showMessageDialog(this, "Done, save reslice series.");
		} catch (Exception e1) {
			e1.printStackTrace();
			Log.logger.warning("Something happen, can not store radiomics map to db ...");
			return;
		}
	}
	
	private void updateFusionImage() {
		
		if (this.calcImage == null) {
			return; // まだ画像が準備されていなければ何もしない
		}
		
	    LUT fLUT = radiomicsMapPanel.getLUT();
	    ImagePlus foreground = null;

		if (fusionMapRadio.isSelected()) {
			if (this.radiomicsMap == null) return; // マップ未計算ならスキップ
			foreground = this.radiomicsMap;
			fLUT = radiomicsMapPanel.getLUT();
			
		} else if (fusionMaskRadio.isSelected()) {
			// まだアライメント済みマスクが無い場合は、ここで動的にペアリングしてあげる
			if (this.alignedMask == null && maskImagePanel != null && maskImagePanel.getAllSlides() != null && maskImagePanel.getAllSlides().size() > 0) {
				SlideGlass orgSg = originalImagePanel.getCurrentSlide();
				SlideGlass maskSg = maskImagePanel.getCurrentSlide();
				if(orgSg != null && maskSg != null) {
					int[] orgZct = originalImagePanel.getZCTArray(orgSg);
					int[] maskZct = maskImagePanel.getZCTArray(maskSg);
					this.alignedMask = com.vis.core.fusion.ImagePairingEngine.alignMaskToOriginalSpace(
							originalImagePanel, orgZct[1], orgZct[2], maskImagePanel, maskZct[1], maskZct[2]);
					if (this.alignedMask != null) this.alignedMask.copyScale(this.calcImage);
				}
			}
			foreground = this.alignedMask;
			// マスク用のLUT（赤色など）を作成して割り当てる
			fLUT = LUT.createLutFromColor(java.awt.Color.RED); 
		}

		if (foreground == null) return;

		double opacity = transparencySlider.getValue() * 0.01;
		this.fusionImage = FusionDisplay.createFusionImage(foreground, this.calcImage, opacity, fLUT);
		
		if (this.fusionImage != null) {
			ImagePlus org = originalImagePanel.getImagePlus(1, 1);
			initMetaDataUsingSrcImage(fusionImage,org, true);
			fusionImagePanel.reloadSlideGlasses(this.fusionImage);
		}
	}

	private boolean validateInputs() {
		if (calcImage == null) {
			JOptionPane.showMessageDialog(this, "Please load image first.", "Input Required",
					JOptionPane.WARNING_MESSAGE);
			return false;
		}

		if (alignedMask != null) {
			if (calcImage.getNSlices() != alignedMask.getNSlices()) {
				JOptionPane.showMessageDialog(this, "Please load same size images and masks.", "Mask is invalid.",
						JOptionPane.WARNING_MESSAGE);
				return false;
			}

			if (calcImage.getWidth() != alignedMask.getWidth()) {
				JOptionPane.showMessageDialog(this, "Please load same size images and masks.", "Mask is invalid.",
						JOptionPane.WARNING_MESSAGE);
				return false;
			}

			if (calcImage.getHeight() != alignedMask.getHeight()) {
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
		if (v != null) {
			int v_ = Integer.valueOf((String) v);
			settings.put(RadiomicsFeature.LABEL, v_);
		}
		// {"GLCM", "GLRLM", "GLSZM", "GLDZM", "NGTDM", "NGLDM"};
		if (fam_and_feature[0].equals(textures[0]/* GLCM */)) {
			Object o = prop.get(SettingsContext.UseBinCountGLCM);
			if (o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinCountGLCM);
			if (o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinWidthGLCM);
			if (o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String) o));
			}
			o = prop.get(SettingsContext.DeltaGLCM);
			if (o != null) {
				settings.put(RadiomicsFeature.DELTA, Integer.valueOf((String) o));
			}
		} else if (fam_and_feature[0].equals(textures[1]/* GLRLM */)) {
			Object o = prop.get(SettingsContext.UseBinCountGLRLM);
			if (o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinCountGLRLM);
			if (o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinWidthGLRLM);
			if (o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String) o));
			}
		} else if (fam_and_feature[0].equals(textures[2]/* GLSZM */)) {
			Object o = prop.get(SettingsContext.UseBinCountGLSZM);
			if (o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinCountGLSZM);
			if (o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinWidthGLSZM);
			if (o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String) o));
			}
		} else if (fam_and_feature[0].equals(textures[3]/* GLDZM */)) {
			Object o = prop.get(SettingsContext.UseBinCountGLDZM);
			if (o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinCountGLDZM);
			if (o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinWidthGLDZM);
			if (o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String) o));
			}
		} else if (fam_and_feature[0].equals(textures[4]/* NGTDM */)) {
			Object o = prop.get(SettingsContext.UseBinCountNGTDM);
			if (o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinCountNGTDM);
			if (o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinWidthNGTDM);
			if (o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String) o));
			}
			o = prop.get(SettingsContext.DeltaNGTDM);
			if (o != null) {
				settings.put(RadiomicsFeature.DELTA, Integer.valueOf((String) o));
			}
		} else if (fam_and_feature[0].equals(textures[5]/* NGLDM */)) {
			Object o = prop.get(SettingsContext.UseBinCountNGLDM);
			if (o != null) {
				settings.put(RadiomicsFeature.USE_BIN_COUNT, Boolean.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinCountNGLDM);
			if (o != null) {
				settings.put(RadiomicsFeature.nBins, Integer.valueOf((String) o));
			}
			o = prop.get(SettingsContext.BinWidthNGLDM);
			if (o != null) {
				settings.put(RadiomicsFeature.BinWidth, Double.valueOf((String) o));
			}
			o = prop.get(SettingsContext.AlphaNGLDM);
			if (o != null) {
				settings.put(RadiomicsFeature.ALPHA, Integer.valueOf((String) o));
			}
			o = prop.get(SettingsContext.DeltaNGLDM);
			if (o != null) {
				settings.put(RadiomicsFeature.DELTA, Integer.valueOf((String) o));
			}
		}

		return settings;
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
	}

}
