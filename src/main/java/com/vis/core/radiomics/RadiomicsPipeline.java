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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import com.vis.configuration.ContextKey;
import com.vis.core.view.D2.roi.RoiConverter;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.Praparat;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import io.github.tatsunidas.radiomics.features.IntensityBasedStatisticalFeatureType;
import io.github.tatsunidas.radiomics.features.IntensityBasedStatisticalFeatures;
import io.github.tatsunidas.radiomics.features.IntensityHistogramFeatureType;
import io.github.tatsunidas.radiomics.features.IntensityHistogramFeatures;
import io.github.tatsunidas.radiomics.features.IntensityVolumeHistogramFeatureType;
import io.github.tatsunidas.radiomics.features.IntensityVolumeHistogramFeatures;
import io.github.tatsunidas.radiomics.features.LocalIntensityFeatureType;
import io.github.tatsunidas.radiomics.features.LocalIntensityFeatures;
import io.github.tatsunidas.radiomics.features.MorphologicalFeatureType;
import io.github.tatsunidas.radiomics.features.MorphologicalFeatures;
import io.github.tatsunidas.radiomics.main.RadiomicsJ;
import weka.classifiers.Classifier;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.SMOreg;
import weka.classifiers.pmml.consumer.Regression;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.Utils;

/*
 * init model
 * calculate feature
 * train
 * prediction
 */
public class RadiomicsPipeline {
	
	//RadiomicsPanel radP;
	final Classifier defaltClf = new RandomForest();
	Classifier clf = new RandomForest();
	String[] model_options;
	
	//settings
	RadiomicsSettings setting;
	HashMap<String/*className*/, List<RoiObj>> roiset;
	Praparat prap;
	/**
	 * train dataset
	 */
	Instances trainingDataset;
	ArrayList<Attribute> explanatoryAttr;
	Attribute targetAttr;
	final String targetColName = "LABEL";
	
	public RadiomicsPipeline modelIs(Classifier model) {
		this.clf = model;
		System.out.println("Current classifier:"+model.getClass().getName());
		if (model instanceof OptionHandler) {
			OptionHandler optionHandler = (OptionHandler) model;
			try {
				String[] optionsArray = optionHandler.getOptions(); // OptionHandlerのメソッドを呼び出す
				String optionsString = weka.core.Utils.joinOptions(optionsArray);
				System.out.println("model options:" + optionsString);
			} catch (Exception ex) {
				System.err.println("Classifierのオプション取得中にエラー: " + ex.getMessage());
				ex.printStackTrace();
			}
		} else {
			System.out.println("このClassifierはOptionHandlerを実装していません。オプションは取得できません。");
		}
		return this;
	}
	
	public RadiomicsPipeline modelIs(Classifier model, String[] options) {
		this.clf = model;
		this.model_options = options;
		return this;
	}
	
	public RadiomicsPipeline trainWith(RadiomicsSettings setting, HashMap<String/*className*/, List<RoiObj>> roiset, Praparat prap) {
		this.setting = setting;
		this.roiset = roiset;
		this.prap = prap;
		return this;
	}
	
	public void train() {
		if (clf == null || setting == null || roiset == null || prap == null) {
			System.out.println("Do first modelIs() and trainWith().");
			return;
		}
		ResultsTable rt = null;
		for (String className : roiset.keySet()) {
			List<RoiObj> rois = roiset.get(className);
			ResultsTable rt1 = calcFeatures(setting, rois, prap);
			// add target label
			for (int i = 0; i < rt.size(); i++) {
				rt.addValue(targetColName, className);
			}
			rt = io.github.tatsunidas.radiomics.main.Utils.combineTables(rt/* null-able */, rt1);
		}
		prepareTrainDataset(rt, targetColName, null);
		
		//TODO
		/**
		 * drop corr
		 * drop zero variance
		 * then feature selection
		 * finally, train.
		 */
		
		System.out.println("Classifierを訓練中...");
		try {
			if (clf instanceof OptionHandler && model_options != null) {
				OptionHandler optionHandler = (OptionHandler) clf;
				optionHandler.setOptions(model_options); // OptionHandlerのsetOptionsを呼び出す
			} else {
				System.out.println("オプション設定なし - OptionHandlerを実装していません)");
			}
			clf.buildClassifier(trainingDataset);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Classifierの訓練が完了しました。");
	}
	
	public Classifier getClassifier() {
		return clf;
	}
	
	public Instances getTrainDataset() {
		return trainingDataset;
	}
	
	public boolean isPraparatReady() {
		return prap != null;
	}
	
	public Praparat getPraparat() {
		return prap;
	}
	
	/**
	 * 
	 * @param featureNames
	 * @param imp: preprocessed
	 * @param mask
	 * @param label
	 * @return
	 */
	private ResultsTable calcFeatures(Properties settingsProp/*radiomicsSetting*/, List<String> featureNames, ImagePlus imp, ImagePlus mask, int label){
		ResultsTable rt = new ResultsTable();
		rt.addRow();
		for(String fname : featureNames) {
			String fam = fname.split("_")[0];
			String name = fname.split("_")[1];
			switch(fam) {
			case SettingsContext.MORPHOLOGICAL:
				for(MorphologicalFeatureType t:MorphologicalFeatureType.values()) {
					if(name.equals(t.name())) {
						MorphologicalFeatures mf = new MorphologicalFeatures(imp, mask, label);
						rt.addValue(fname, mf.calculate(t.id()));
					}
				}
				break;
			case SettingsContext.LOCALINTENSITY:
				for(LocalIntensityFeatureType t:LocalIntensityFeatureType.values()) {
					if(name.equals(t.name())) {
						LocalIntensityFeatures f = new LocalIntensityFeatures(imp, mask, label);
						rt.addValue(fname, f.calculate(t.id()));
					}
				}
				break;
			case SettingsContext.INTENSITYSTATS:
				for(IntensityBasedStatisticalFeatureType t:IntensityBasedStatisticalFeatureType.values()) {
					if(name.equals(t.name())) {
						IntensityBasedStatisticalFeatures f = new IntensityBasedStatisticalFeatures(imp, mask, label);
						rt.addValue(fname, f.calculate(t.id()));
					}
				}
				break;
			case SettingsContext.INTENSITYHISTOGRAM:
				Boolean useBinCountHist = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountHISTOGRAM));
				Integer binCountHist = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountHISTOGRAM));
				Double binWidthHist = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthHISTOGRAM));
				for(IntensityHistogramFeatureType t:IntensityHistogramFeatureType.values()) {
					if(name.equals(t.name())) {
						IntensityHistogramFeatures f;
						try {
							f = new IntensityHistogramFeatures(imp, mask, label, useBinCountHist, binCountHist,binWidthHist);
							rt.addValue(fname, f.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			case SettingsContext.INTENSITYVOLUMEHISTOGRAM:
				int mode = 0;
				Boolean useOriginalIVH = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseOriginalIVH));
				Boolean useBinCountIVH = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountIVH));
				Integer binCountIVH = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountIVH));
				Double binWidthIVH = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthIVH));
				if(useOriginalIVH==false && useBinCountIVH==false) {
					mode=2;
					RadiomicsJ.IVH_binWidth = binWidthIVH;
				}else if(useBinCountIVH==true) {
					mode =1;
					RadiomicsJ.IVH_binCount = binCountIVH;
				}
				for(IntensityVolumeHistogramFeatureType t:IntensityVolumeHistogramFeatureType.values()) {
					if(name.equals(t.name())) {
						IntensityVolumeHistogramFeatures f;
						try {
							f = new IntensityVolumeHistogramFeatures(imp, mask, label, mode);
							rt.addValue(fname, f.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			default:
				//do nothing
			}
		}
		return rt;
	}
	
	public ResultsTable calcFeatures(RadiomicsSettings setting, List<RoiObj> rois, Praparat prap) {
		Properties prop = setting.currentSettings();
		Integer label = Integer.valueOf((String)prop.get(SettingsContext.MASK_LABEL));
		RadiomicsJ rad = new RadiomicsJ();
		ImagePlus imp = prap.getImagePlus();
		boolean d3_basis = Boolean.valueOf((String)prop.getProperty(SettingsContext.D3Basis));
		ResultsTable rt = null;
		//grab rois for groups
		HashMap<String, List<RoiObj>> groups = new HashMap<>();
		int nullCount = 0;
		for (RoiObj r : rois) {
			String gname = r.getProperty(ContextKey.RoiGroup);
			if (gname == null) {
				gname = "null"+nullCount;
				nullCount++;//null is individual
			}
			if (groups.get(gname) == null) {
				groups.put(gname, new ArrayList<RoiObj>());
			}
			groups.get(gname).add(r);
		}
		if(d3_basis) {
			for(String key : groups.keySet()) {
				List<RoiObj> roi_group = groups.get(key);
				ImagePlus mask = createMaskWithRois(imp, roi_group, label);
				if(rt == null) {
					try {
						rt = rad.execute(imp, mask, label);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}else {
					try {
						ResultsTable rt2 = rad.execute(imp, mask, label);
						rt = io.github.tatsunidas.radiomics.main.Utils.combineTables(rt, rt2);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}else { // 2d basis
			//if 2d basis, calculate slice by slice
			for(String key : groups.keySet()) {
				List<RoiObj> roi_group = groups.get(key);
				ImagePlus mask = createMaskWithRois(imp, roi_group, label);
				if(rt == null) {
					try {
						rt = rad.extractAllSlice(imp, mask, label);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}else {
					try {
						ResultsTable rt2 = rad.extractAllSlice(imp, mask, label);
						rt = io.github.tatsunidas.radiomics.main.Utils.combineTables(rt, rt2);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return rt;
	}
	
	/**
     * RadimomicsJのResultsTableからWEKAのInstancesオブジェクトを生成します。
     *
     * @param rt              RadimomicsJのResultsTable
     * @param classAttributeName 予測したいクラス属性の列名 (nullの場合、クラス属性は設定されません)
     * @return WEKAのInstancesオブジェクト
     * @throws Exception エラーが発生した場合
     */
	public Instances convertResultsTableToWekaInstances(ResultsTable rt, String classAttributeName) throws Exception {
		if (rt == null || rt.getCounter() == 0) {
			throw new IllegalArgumentException("ResultsTable is null or empty.");
		}

		// 属性リストの作成
		ArrayList<Attribute> attributes = new ArrayList<>();
		ArrayList<String> classLabels = new ArrayList<>(); // クラスラベルを収集するためのリスト

		// 各列をWEKAのAttributeに変換
		String[] columnHeadings = rt.getHeadings();
		for (String heading : columnHeadings) {
			// "ClassLabel" 列は特別に処理し、数値属性としては追加しない
			if (classAttributeName != null && heading.equals(classAttributeName)) {
				// 後でnominal attributeとして追加するために、ラベルを収集
				for (int i = 0; i < rt.getCounter(); i++) {
					String label = rt.getStringValue(rt.getColumnIndex(heading), i);
					if (!classLabels.contains(label)) {
						classLabels.add(label);
					}
				}
				continue; // 次の列へ
			}
			// 通常の数値特徴量の場合
			attributes.add(new Attribute(heading));
		}

		// クラス属性を最後に追加 (もし指定されていれば)
		if (classAttributeName != null && !classLabels.isEmpty()) {
			attributes.add(new Attribute(classAttributeName, classLabels));
		}

		// Instancesオブジェクトの初期化
		Instances data = new Instances("RadimomicsFeatures", attributes, rt.getCounter());

		// クラス属性が設定されている場合、それを指定
		if (classAttributeName != null) {
			data.setClassIndex(data.numAttributes() - 1); // 最後の属性をクラス属性とする
		}

		// 各行をWEKAのInstanceに変換
		for (int i = 0; i < rt.getCounter(); i++) {
			double[] vals = new double[data.numAttributes()];
			int currentAttrIndex = 0;

			for (String heading : columnHeadings) {
				if (classAttributeName != null && heading.equals(classAttributeName)) {
					// クラス属性は別途処理
					continue;
				}
				int colIndex = rt.getColumnIndex(heading);
				vals[currentAttrIndex++] = rt.getValueAsDouble(colIndex, i);
			}
			// クラス属性の値を設定
			if (classAttributeName != null) {
				String label = rt.getStringValue(rt.getColumnIndex(classAttributeName), i);
				vals[currentAttrIndex] = data.classAttribute().indexOfValue(label);
			}
			data.add(new DenseInstance(1.0, vals)); // 1.0は重み
		}
		return data;
	}
	
	private void prepareTrainDataset(ResultsTable rt, String targetColName, List<String> drop) {
		String[] headerStrings = rt.getHeadings();
		List<String> header = Arrays.asList(headerStrings);
		if(drop != null) {
			header.removeAll(drop);
		}
		explanatoryAttr = toAttributes(header);
		targetAttr = targetClassAttribute4Clf(rt, targetColName);
		ArrayList<Attribute> attributes = new ArrayList<>(explanatoryAttr);
		attributes.add(targetAttr);
		trainingDataset = new Instances("TrainingDataset", attributes, 0/*volume of row*/);
		
		// クラス属性のインデックスを設定 (通常は最後の属性)
		trainingDataset.setClassIndex(attributes.size() - 1);
       
       // 訓練データのインスタンスを作成してデータセットに追加
		int col = header.size();
		int row = rt.size();
		boolean numericalTarget = isNumericalTarget(rt.getColumnAsStrings(targetColName));
		for(int r =0; r<row; r++) {
			Instance record = new DenseInstance(col); // 属性の数
			for(int c =0; c<col; c++) {
				String h = header.get(c);
				if(h.startsWith(SettingsContext.DIAGNOSTICS) || h.startsWith(SettingsContext.OPERATIONAL)) {
					continue;
				}
				Attribute attr = attributes.get(c);
				if(h.equals(targetColName)) {
					String v = rt.getColumnAsStrings(h)[r];
					/*
					 * drop if target is null
					 */
					if(v == null || v.length() == 0) {
						continue;
					}
					//string or double ?
					if(numericalTarget) {
						Double dv = rt.getColumn(h)[r];
						record.setValue(attr, dv);
					}else {//string
						record.setValue(attr, v);
					}
					continue;
				}
				//others always numerical.
				Double v = rt.getColumn(h)[r];
//				if(v == null) {
//					v = weka.core.Utils.missingValue();//Double.NaN
//				}
				record.setValue(attr, v);
			}
			trainingDataset.add(record);
		}
		System.out.println("データセットが正常にロードされました。");
        System.out.println("インスタンス数: " + trainingDataset.numInstances());
        System.out.println("属性数: " + trainingDataset.numAttributes());
	}
	
	/**
	 * 
	 * @return segment img, proba img
	 */
	public ImagePlus predict(int slidePos/*1 to n*/) {
		/**
		 * TODO
		 * stride 2d/3d rect roi over all voxel
		 * estimate processing time
		 */
		int patchSize = 15;//keep odd value.
		
		// 説明変数の名前を格納するリスト
		List<String> featureNames = new ArrayList<>();
		// 全ての属性を列挙
		Enumeration<Attribute> attributes = trainingDataset.enumerateAttributes();
		while (attributes.hasMoreElements()) {
			Attribute attribute = attributes.nextElement();
			// クラス属性でない場合、その名前をリストに追加
			if (!attribute.equals(trainingDataset.classAttribute())) {
				featureNames.add(attribute.name());
			}
		}
		ImagePlus imp = prap.getImagePlus();
		int w = imp.getWidth();
		int h = imp.getHeight();
		Properties prop = setting.currentSettings();
		
		boolean is3D = ((String)prop.get(SettingsContext.D3Basis)).equals("true");
		int label = Integer.valueOf((String)prop.get(SettingsContext.MASK_LABEL));
		
		FloatProcessor labelImg = new FloatProcessor(w,h);
		FloatProcessor probaImg = new FloatProcessor(w,h);
		
		for(int j=0; j<h; j++) {
			for(int i=0; i<w; i++) {
				try {
					ImagePlus patch_img = cropAndPadImage3D(imp, i, j, slidePos-1, patchSize, patchSize, is3D ? patchSize:1);
					ResultsTable rt = calcFeatures(prop, featureNames, patch_img, null, label);
					Instances ds2pred = convertResultsTableToWekaInstances(rt, null/*targetColName*/);
					Instance inst = ds2pred.firstInstance();
					//double predictedClassIndex = clf.classifyInstance(inst);
					//String predictedClassName = trainingDataset.classAttribute().value((int) predictedClassIndex);
					// 各クラスに属する確率分布の予測
					double[] proba = clf.distributionForInstance(inst);
					int predClassLabel = getIndexOfMaxValue(proba);
					labelImg.setf(i, j, (float)predClassLabel);
					probaImg.setf(i, j, (float)proba[predClassLabel]);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		ImageStack pstack = new ImageStack(w,h);
		pstack.addSlice(labelImg);
		pstack.addSlice(probaImg);
		ImagePlus preds = new ImagePlus("result", pstack);
		return preds;
	}
	
	public int getIndexOfMaxValue(double[] arr) {
        // 配列がnullまたは空の場合は -1 を返す
        if (arr == null || arr.length == 0) {
            return -1;
        }

        double maxValue = arr[0];
        int maxIndex = 0;

        // 配列の2番目の要素から最後までループ
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > maxValue) {
                maxValue = arr[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
	
	public static ImagePlus cropAndPadImage3D(ImagePlus originalImage, int centerX, int centerY, int centerZ,
			int patchSizeX, int patchSizeY, int patchSizeZ) {
		if (originalImage == null) {
			throw new IllegalArgumentException("元のImagePlusはnullであってはなりません。");
		}
		if (patchSizeX <= 0 || patchSizeY <= 0 || patchSizeZ <= 0) {
			throw new IllegalArgumentException("パッチサイズはすべて正の値でなければなりません。");
		}

		int originalWidth = originalImage.getWidth();
		int originalHeight = originalImage.getHeight();
		int originalDepth = originalImage.getStackSize(); // Z軸のサイズ (スライス数)

		// 新しいImageStackを作成
		ImageStack originalStack = originalImage.getStack();
		ImageStack croppedStack = new ImageStack(patchSizeX, patchSizeY);

		int imageType = originalImage.getType();

		// クロップ領域の左上奥の座標を計算
		int cropStartX = centerX - patchSizeX / 2;
		int cropStartY = centerY - patchSizeY / 2;
		int cropStartZ = centerZ - patchSizeZ / 2;

		// 各Zスライスに対して処理
		for (int z = 0; z < patchSizeZ; z++) {
			ImageProcessor croppedProcessor;
			// 画像タイプに応じたImageProcessorを作成し、0で初期化
			switch (imageType) {
			case ImagePlus.GRAY8:
				croppedProcessor = new ByteProcessor(patchSizeX, patchSizeY);
				break;
			case ImagePlus.GRAY16:
				croppedProcessor = new ShortProcessor(patchSizeX, patchSizeY);
				break;
			case ImagePlus.GRAY32:
				croppedProcessor = new FloatProcessor(patchSizeX, patchSizeY);
				break;
			default:
				// 未対応の画像タイプの場合、元のプロセッサと同じタイプで初期化
				// 汎用的な方法だが、カラーの場合は適切ではない可能性あり
				croppedProcessor = originalImage.getProcessor().createProcessor(patchSizeX, patchSizeY);
				break;
			}

			// パディングのために新しいプロセッサを0で初期化
			croppedProcessor.setValue(0);
			croppedProcessor.fill();

			int originalZ = cropStartZ + z; // 元のスタックでのZ座標 (スライス番号)
			// 元の画像のZ軸範囲内であれば、そのスライスからピクセルをコピー
			if (originalZ >= 0 && originalZ < originalDepth) {
				ImageProcessor originalSliceProcessor = originalStack.getProcessor(originalZ + 1); // ImageStackは1ベースインデックス

				for (int y = 0; y < patchSizeY; y++) {
					for (int x = 0; x < patchSizeX; x++) {
						int originalX = cropStartX + x;
						int originalY = cropStartY + y;
						// 元の画像のXY範囲内であればピクセルをコピー
						if (originalX >= 0 && originalX < originalWidth && originalY >= 0
								&& originalY < originalHeight) {
							croppedProcessor.setf(x, y, originalSliceProcessor.getf(originalX, originalY));
						}
					}
				}
			}
			croppedStack.addSlice(null, croppedProcessor);
		}
		ImagePlus croppedImage = new ImagePlus(originalImage.getTitle() + "_cropped_3D", croppedStack);
		croppedImage.copyScale(originalImage); // スケール情報をコピー
		return croppedImage;
	}
	
	/**
	 * 指定されたパッチサイズとラベル値で3次元のマスク画像を生成します。 マスク画像はByteProcessorで構成されます。
	 *
	 * @param patchSizeX マスク画像のX軸サイズ
	 * @param patchSizeY マスク画像のY軸サイズ
	 * @param patchSizeZ マスク画像のZ軸サイズ
	 * @param labelValue マスクを塗りつぶすラベル値 (0-255)
	 * @return 生成されたマスクImagePlusオブジェクト
	 */
	public static ImagePlus create3DMaskImage(int patchSizeX, int patchSizeY, int patchSizeZ, int labelValue) {
		// 入力値のバリデーション
		if (patchSizeX <= 0 || patchSizeY <= 0 || patchSizeZ <= 0) {
			throw new IllegalArgumentException("パッチサイズはすべて正の値でなければなりません。");
		}
		if (labelValue < 0 || labelValue > 255) {
			throw new IllegalArgumentException("ラベル値は0から255の範囲でなければなりません。");
		}

		// 新しいImageStackを作成
		ImageStack maskStack = new ImageStack(patchSizeX, patchSizeY);

		// 各Zスライスに対して処理
		for (int z = 0; z < patchSizeZ; z++) {
			// ByteProcessorを作成
			ByteProcessor bp = new ByteProcessor(patchSizeX, patchSizeY);

			// 指定されたラベル値で塗りつぶす
			bp.setValue(labelValue);
			bp.fill();

			// マスクスタックにスライスを追加
			maskStack.addSlice("Mask_Z" + (z + 1), bp);
		}

		// 新しいImagePlusオブジェクトを作成して返す
		ImagePlus maskImage = new ImagePlus(
				"Mask_X" + patchSizeX + "_Y" + patchSizeY + "_Z" + patchSizeZ + "_Label" + labelValue, maskStack);
		return maskImage;
	}
	
	ArrayList<Attribute> toAttributes(List<String> headers){
		ArrayList<Attribute> fs = new ArrayList<>();
		for(String h : headers) {
			if(h.startsWith("Operational_")) {
				continue;
			}else if(h.startsWith("Diagnostics_")) {
				continue;
			}
			Attribute attr = new Attribute(h);
			fs.add(attr);
		}
		return fs;
	}
	
	/**
	 * variables in target are deal as String.
	 * @param rt
	 * @param targetColName
	 * @return
	 */
	Attribute targetClassAttribute4Clf(ResultsTable rt, String targetColName) {
		String target[] = rt.getColumnAsStrings(targetColName);
		HashSet<String> set = new HashSet<>();
		for (String cv : target) {
			set.add(cv);
		}
		List<String> classes = new ArrayList<>(set);
		Collections.sort(classes);
		Attribute classAttribute = new Attribute(targetColName, classes);
		return classAttribute;
	}
	
	boolean isNumericalTarget(String[] target) {
		int search_idx = 0;
		for (int i = 0; i < target.length; i++) {
			String t = target[i];
			if (t != null && t.length() > 0) {
				search_idx = i;
				break;
			}
		}
		return isNumerical(target[search_idx]);
	}
	
	boolean isNumerical(String v) {
		try {
			Double.valueOf(v);
			return true;
		}catch(NumberFormatException e) {
			return false;
		}
	}
	
	ImagePlus createMaskWithRois(ImagePlus img, List<RoiObj> rois, Integer label) {
		int lbl = label == null ? 255:label;
		int w = img.getWidth();
		int h = img.getHeight();
		int s = img.getNSlices();
		ImageStack stack = new ImageStack(w, h);
		for(int z=0; z<s; z++) {
			ImageProcessor ip = new ByteProcessor(w, h);
			stack.addSlice(ip);
		}
		for(RoiObj ro:rois) {
			int pos = ro.getPosition();
			if(pos == 0) {
				System.out.println("This roi can not asign any slices...sklip.:"+ro.getName());
				continue;
			}
			Roi r = new RoiConverter().convert2Roi(ro);
			ImageProcessor ip = stack.getProcessor(pos);
			ip.setValue(lbl);
			//ip.setRoi(r);
			ip.fill(r);
		}
		ImagePlus mask = new ImagePlus("mask", stack);
		return mask;
	}
	
	public class WekaLogisticRegressionExample {

//	    public void main(String[] args) {
//	        try {
//	            // 1. 属性（特徴量とクラスラベル）の定義
//	            // 特徴量1 (数値型)
//	            Attribute feature1 = new Attribute("feature1");
//	            // 特徴量2 (数値型)
//	            Attribute feature2 = new Attribute("feature2");
//
//	            // クラスラベル (名義型: "ClassA", "ClassB")
//	            ArrayList<String> classValues = new ArrayList<>();
//	            classValues.add("ClassA");
//	            classValues.add("ClassB");
//	            Attribute classAttribute = new Attribute("classLabel", classValues);
//
//	            // 属性のリストを作成
//	            ArrayList<Attribute> attributes = new ArrayList<>();
//	            attributes.add(feature1);
//	            attributes.add(feature2);
//	            attributes.add(classAttribute);
//
//	            // 2. データセット (Instances オブジェクト) の作成
//	            // "WekaLogisticRegressionDataset" はデータセットの名前、attributes は属性リスト、
//	            // 0 は初期容量（必要に応じて自動で拡張される）
//	            Instances trainingData = new Instances("WekaLogisticRegressionDataset", attributes, 0);
//
//	            // クラス属性のインデックスを設定 (通常は最後の属性)
//	            trainingData.setClassIndex(attributes.size() - 1);
//
//	            // 3. 訓練データのインスタンスを作成してデータセットに追加
//	            // インスタンス1: feature1=1.0, feature2=2.0, classLabel="ClassA"
//	            Instance inst1 = new DenseInstance(3); // 属性の数
//	            inst1.setValue(feature1, 1.0);
//	            inst1.setValue(feature2, 2.0);
//	            inst1.setValue(classAttribute, "ClassA");
//	            trainingData.add(inst1);
//
//	            // インスタンス2: feature1=2.0, feature2=1.0, classLabel="ClassA"
//	            Instance inst2 = new DenseInstance(3);
//	            inst2.setValue(feature1, 2.0);
//	            inst2.setValue(feature2, 1.0);
//	            inst2.setValue(classAttribute, "ClassA");
//	            trainingData.add(inst2);
//
//	            // インスタンス3: feature1=5.0, feature2=6.0, classLabel="ClassB"
//	            Instance inst3 = new DenseInstance(3);
//	            inst3.setValue(feature1, 5.0);
//	            inst3.setValue(feature2, 6.0);
//	            inst3.setValue(classAttribute, "ClassB");
//	            trainingData.add(inst3);
//
//	            // インスタンス4: feature1=6.0, feature2=5.0, classLabel="ClassB"
//	            Instance inst4 = new DenseInstance(3);
//	            inst4.setValue(feature1, 6.0);
//	            inst4.setValue(feature2, 5.0);
//	            inst4.setValue(classAttribute, "ClassB");
//	            trainingData.add(inst4);
//
//	            System.out.println("--- Training Data ---");
//	            System.out.println(trainingData);
//
//	            // 4. ロジスティック回帰モデルの初期化
//	            Logistic logisticModel = new Logistic();
//
//	            // オプション設定 (例: リッジパラメータを設定する場合)
//	            // String[] options = weka.core.Utils.splitOptions("-R 1.0E-8 -M 500");
//	            // logisticModel.setOptions(options);
//
//	            // 5. モデルの訓練
//	            System.out.println("\n--- Training Model ---");
//	            logisticModel.buildClassifier(trainingData);
//	            System.out.println("Model training completed.");
//	            System.out.println(logisticModel); // 学習済みモデルの詳細を出力
//
//	            // (オプション) モデルの保存
//	            // SerializationHelper.write("logistic_model.model", logisticModel);
//	            // (オプション) モデルの読み込み
//	            // Logistic loadedModel = (Logistic) SerializationHelper.read("logistic_model.model");
//
//
//	            // 6. 推論の実行 (新しいデータインスタンスで予測)
//	            System.out.println("\n--- Prediction ---");
//
//	            // 推論用の新しいインスタンスを作成 (クラスラベルは未設定またはダミーでOK)
//	            // このインスタンスは訓練データと同じ属性構造を持つ必要がある
//	            Instance newInstance1 = new DenseInstance(3);
//	            newInstance1.setDataset(trainingData); // 属性情報を紐付ける
//	            newInstance1.setValue(feature1, 1.5);
//	            newInstance1.setValue(feature2, 1.8);
//	            // newInstance1.setClassMissing(); // クラスラベルが不明なことを示す
//
//	            Instance newInstance2 = new DenseInstance(3);
//	            newInstance2.setDataset(trainingData);
//	            newInstance2.setValue(feature1, 5.5);
//	            newInstance2.setValue(feature2, 5.2);
//	            // newInstance2.setClassMissing();
//
//	            // 推論インスタンスのリスト
//	            ArrayList<Instance> testInstances = new ArrayList<>();
//	            testInstances.add(newInstance1);
//	            testInstances.add(newInstance2);
//
//	            for (Instance testInst : testInstances) {
//	                System.out.println("\nPredicting for instance: " + testInst);
//
//	                // クラスラベルの予測
//	                double predictedClassIndex = logisticModel.classifyInstance(testInst);
//	                String predictedClassName = trainingData.classAttribute().value((int) predictedClassIndex);
//	                System.out.println("Predicted class: " + predictedClassName + " (Index: " + predictedClassIndex + ")");
//
//	                // 各クラスに属する確率分布の予測
//	                double[] distribution = logisticModel.distributionForInstance(testInst);
//	                System.out.println("Probability distribution:");
//	                for (int i = 0; i < distribution.length; i++) {
//	                    System.out.println("  " + trainingData.classAttribute().value(i) + ": " + String.format("%.4f", distribution[i]));
//	                }
//	            }
//
//	        } catch (Exception e) {
//	            e.printStackTrace();
//	        }
//	    }
	}

	
	public class WekaSvrExample {
	    public void main(String[] args) {
//	        try {
//	            // 1. データセットの読み込み (例: ARFFファイル)
//	            // このパスは実際のファイルパスに置き換えてください
//	            DataSource source = new DataSource("your_regression_dataset.arff");
//	            Instances data = source.getDataSet();
//
//	            // ターゲット変数を設定 (通常は最後の属性がターゲット)
//	            if (data.classIndex() == -1) {
//	                data.setClassIndex(data.numAttributes() - 1);
//	            }
//
//	            // 2. SMOregモデルの初期化
//	            SMOreg svr = new SMOreg();
//
//	            // オプション設定 (例)
//	            svr.setC(1.0); // コストパラメータ C
//	            // svr.setKernel(new weka.classifiers.functions.supportVector.RBFKernel(data, 250007, 0.01)); // RBFカーネルの場合
//	            // svr.setEpsilon(0.001); // イプシロン
//
//	            // 3. モデルの訓練
//	            svr.buildClassifier(data);
//	            System.out.println("SVR Model trained successfully.");
//	            System.out.println(svr); // 学習済みモデルの詳細
//
//	            // 4. 推論 (例: 最初のインスタンスで予測)
//	            if (data.numInstances() > 0) {
//	                double prediction = svr.classifyInstance(data.instance(0));
//	                System.out.println("Prediction for first instance: " + prediction);
//	                System.out.println("Actual value for first instance: " + data.instance(0).classValue());
//	            }
//
//	            // (オプション) モデルの評価 (例: 10-foldクロスバリデーション)
//	            Evaluation eval = new Evaluation(data);
//	            eval.crossValidateModel(svr, data, 10, new Random(1));
//	            System.out.println("\n--- Evaluation Results ---");
//	            System.out.println(eval.toSummaryString());
//	            System.out.println("Mean Absolute Error: " + eval.meanAbsoluteError());
//	            System.out.println("Root Mean Squared Error: " + eval.rootMeanSquaredError());
//
//	        } catch (Exception e) {
//	            e.printStackTrace();
//	        }
	    }
	}
}
