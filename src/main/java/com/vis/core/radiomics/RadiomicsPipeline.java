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
import ij.process.ImageProcessor;
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
	
	//model
	String modelName;
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
	String targetColName;
	
	public RadiomicsPipeline() {
	}
	
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
		//binary or multi class
		ResultsTable rt = null;
		for(String className:roiset.keySet()) {
			List<RoiObj> rois = roiset.get(className);
		}
		if (modelName.equals("logistic")) {
			clf = new Logistic();
			if(model_options != null) {
				Logistic m = (Logistic)clf;
				try {
					m.setOptions(model_options);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			System.out.println("\n--- Training Model ---");
			try {
				clf.buildClassifier(trainingDataset);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("Model training completed.");
		}
//		else if() {
//			
//		}
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
	
	public ResultsTable calcFeatures(RadiomicsSettings setting, List<RoiObj> rois, Praparat prap, String className) {
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
				ImagePlus mask = createMask(imp, roi_group, label);
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
				ImagePlus mask = createMask(imp, roi_group, label);
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
	
	private void prepareTrainDataset(ResultsTable rt, String targetColName, List<String> drop) {
		String[] headerStrings = rt.getHeadings();
		List<String> header = Arrays.asList(headerStrings);
		if(drop != null) {
			header.removeAll(drop);
		}
		explanatoryAttr = toAttributes(header);
		targetAttr = targetClassAttribute4Clf(rt, targetColName);
		//TODO target null exception ?
		this.targetColName = targetColName;
		
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
	}
	
	
	
	public List<Object[]> predict(ResultsTable pred_dataset/*without target*/) {
		String[] headerStrings = pred_dataset.getHeadings();
		List<String> header = Arrays.asList(headerStrings);
		ArrayList<Attribute> attributes = new ArrayList<>(explanatoryAttr);
		attributes.add(targetAttr);
		Instances dataset = new Instances("Predictions", attributes, 0/*volume of row*/);
		// クラス属性のインデックスを設定 (通常は最後の属性)
		dataset.setClassIndex(attributes.size() - 1);
		int row = pred_dataset.size();
		int col = attributes.size();
		for(int r =0; r<row; r++) {
			Instance record = new DenseInstance(col);
			record.setDataset(trainingDataset);// 属性情報を紐付ける
			for(int c =0; c<col; c++) {
				String h = header.get(c);
				if(targetColName.equals(h)) {
					continue;
				}
				Attribute a = attributes.get(c);
				Double v = pred_dataset.getColumn(h)[r];
				record.setValue(a, v);
			}
			record.setClassMissing(); // クラスラベルが不明なことを示す
			dataset.add(record);
		}
		List<Object[]> preds = new ArrayList<>();
		for (Instance testInst : dataset) {
			System.out.println("\nPredicting for instance: " + testInst);
			// クラスラベルの予測
			double predictedClassIndex;
			try {
				predictedClassIndex = clf.classifyInstance(testInst);
				String predictedClassName = trainingDataset.classAttribute().value((int) predictedClassIndex);
				System.out.println(
						"Predicted class: " + predictedClassName + " (Index: " + predictedClassIndex + ")");

				// 各クラスに属する確率分布の予測
				double[] proba = clf.distributionForInstance(testInst);
				System.out.println("Probability distribution:");
				for (int i = 0; i < proba.length; i++) {
					System.out.println("  " + trainingDataset.classAttribute().value(i) + ": "
							+ String.format("%.4f", proba[i]));
				}
				preds.add(new Object[]{predictedClassName, predictedClassIndex, proba});
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return preds;
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
	
	ImagePlus createMask(ImagePlus img, List<RoiObj> rois, Integer label) {
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
