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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import com.vis.configuration.RoiDBKey;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.roi.RoiConverter;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.Praparat;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import io.github.tatsunidas.radiomics.features.FractalFeatureType;
import io.github.tatsunidas.radiomics.features.FractalFeatures;
import io.github.tatsunidas.radiomics.features.GLCMFeatureType;
import io.github.tatsunidas.radiomics.features.GLCMFeatures;
import io.github.tatsunidas.radiomics.features.GLDZMFeatureType;
import io.github.tatsunidas.radiomics.features.GLDZMFeatures;
import io.github.tatsunidas.radiomics.features.GLRLMFeatureType;
import io.github.tatsunidas.radiomics.features.GLRLMFeatures;
import io.github.tatsunidas.radiomics.features.GLSZMFeatureType;
import io.github.tatsunidas.radiomics.features.GLSZMFeatures;
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
import io.github.tatsunidas.radiomics.features.NGLDMFeatureType;
import io.github.tatsunidas.radiomics.features.NGLDMFeatures;
import io.github.tatsunidas.radiomics.features.NGTDMFeatureType;
import io.github.tatsunidas.radiomics.features.NGTDMFeatures;
import io.github.tatsunidas.radiomics.features.Shape2DFeatureType;
import io.github.tatsunidas.radiomics.features.Shape2DFeatures;
import io.github.tatsunidas.radiomics.main.ImagePreprocessing;
import io.github.tatsunidas.radiomics.main.RadiomicsJ;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.BestFirst;
import weka.attributeSelection.WrapperSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.functions.Logistic;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.SerializationHelper;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;

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
	
	public RadiomicsPipeline() {}
	
	public RadiomicsPipeline(RadiomicsSettings setting) {
		this.setting = setting;
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
	
	public RadiomicsPipeline trainWith(
			RadiomicsSettings setting, 
			HashMap<String/*className*/, 
			List<RoiObj>> roiset, 
			Praparat prap) {
		this.setting = setting;
		this.roiset = roiset;
		this.prap = prap;
		return this;
	}
	
	public void train(boolean impute, boolean balance, boolean featureSelect) {
		if (clf == null || setting == null || roiset == null || prap == null) {
			System.out.println("Do first modelIs() and trainWith().");
			return;
		}
		ResultsTable rt = null;
		for (String className : roiset.keySet()) {
			List<RoiObj> rois = roiset.get(className);
			ResultsTable rt_ = calcAllFeatures(setting, rois, prap);
			// add target label to calculation results
			for (int i = 0; i < rt_.size(); i++) {
				rt_.setValue(targetColName, i, className);
				//rt_.save(className+"_trainDataset.csv");//debug
			}
			rt = combineTables(rt/* null-able */, rt_);
		}
		
		//rt.save("trainingDataset.csv");//debug
		
		try {
			prepareTrainDataset(rt, targetColName, impute, balance, featureSelect);
		}catch(Exception e) {
			System.out.println(e.getMessage());
		}
		
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
	
	public Praparat getPraparat() {
		return prap;
	}
	
	public void loadDatasetARFF(File f) {
		Instances data = null;
		try {
			DataSource source = new DataSource(f.getAbsolutePath());
			data = source.getDataSet();
			// クラス属性が設定されていない場合、最後の属性をクラス属性に設定
			if (data.classIndex() == -1) {
				data.setClassIndex(data.numAttributes() - 1);
			}
			this.trainingDataset = data;
			System.out.println("ARFF Dataset was loaded successfully.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean isPraparatReady() {
		return prap != null;
	}
	
	public boolean isPredictionReady() {
		return trainingDataset != null;
	}
	
	public void saveModel(String dest) {
		Classifier classifierToSave = getClassifier();
		if(!dest.endsWith(".model")) {
			dest += ".model";
		}
		try {
            SerializationHelper.write(dest, classifierToSave);
            System.out.println("WEKAモデルが正常に保存されました: " + dest);
        } catch (IOException e) {
            System.err.println("モデルの保存/読み込み中にIOエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("その他のエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
	}
	
	
	public void saveDatasetARFF(String dest) {
		Instances trainds = getTrainDataset();
		if(trainds == null) {
			System.out.println("This pipeline does not perform training. no write dataset.");
			return;
		}
		try {
			if (!dest.endsWith(".arff")) {
				dest += ".arff";
			}
			ArffSaver saver = new ArffSaver();
			saver.setInstances(trainds);
			String outputArffPath = dest; // 保存するARFFファイルの名前
			File outputFile = new File(outputArffPath);
			saver.setFile(outputFile);
			// （オプション）圧縮して保存したい場合
			// saver.setCompressOutput(true); // .arff.gz 形式で保存されます
			saver.writeBatch(); // または saver.writeIncremental() を使用することも可能
			System.out.println("InstancesがARFFファイルに正常に保存されました: " + outputFile.getAbsolutePath());
		} catch (IOException e) {
			System.err.println("ARFFファイルの保存中にI/Oエラーが発生しました: " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println("その他のエラーが発生しました: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param prop from settings
	 * @param features to calculate
	 * @param imp
	 * @param mask
	 * @param mask label
	 * @return
	 */
	private ResultsTable calcFeatures(
			Properties settingsProp/*radiomicsSetting*/, 
			List<String> featureNames, ImagePlus imp, ImagePlus mask, int label){
		ResultsTable rt = new ResultsTable();
		rt.addRow();//or incrementCounter(); these are same.
		boolean is3D = ((String)settingsProp.get(SettingsContext.D3Basis)).equals("true");
		if(is3D == false) {
			RadiomicsJ.force2D = true;
		}else {
			RadiomicsJ.force2D = false;
		}
		Boolean useBinCountHist = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountHISTOGRAM));
		Integer binCountHist = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountHISTOGRAM));
		Double binWidthHist = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthHISTOGRAM));
		
		Boolean useOriginalIVH = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseOriginalIVH));
		Boolean useBinCountIVH = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountIVH));
		Integer binCountIVH = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountIVH));
		Double binWidthIVH = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthIVH));
		
		Boolean useBinCountGLCM = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountGLCM));
		Integer binCountGLCM = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountGLCM));
		Double binWidthGLCM = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthGLCM));
		Integer deltaGLCM = Integer.valueOf((String)settingsProp.get(SettingsContext.DeltaGLCM));
		
		Boolean useBinCountGLRLM = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountGLRLM));
		Integer binCountGLRLM = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountGLRLM));
		Double binWidthGLRLM = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthGLRLM));
		
		Boolean useBinCountGLSZM = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountGLSZM));
		Integer binCountGLSZM = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountGLSZM));
		Double binWidthGLSZM = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthGLSZM));
		
		Boolean useBinCountGLDZM = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountGLDZM));
		Integer binCountGLDZM = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountGLDZM));
		Double binWidthGLDZM = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthGLDZM));
		
		Boolean useBinCountNGTDM = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountNGTDM));
		Integer binCountNGTDM = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountNGTDM));
		Double binWidthNGTDM = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthNGTDM));
		Integer deltaNGTDM = Integer.valueOf((String)settingsProp.get(SettingsContext.DeltaNGTDM));
		
		Boolean useBinCountNGLDM = Boolean.valueOf((String)settingsProp.get(SettingsContext.UseBinCountNGLDM));
		Integer binCountNGLDM = Integer.valueOf((String)settingsProp.get(SettingsContext.BinCountNGLDM));
		Double binWidthNGLDM = Double.valueOf((String)settingsProp.get(SettingsContext.BinWidthNGLDM));
		Integer alphaNGLDM = Integer.valueOf((String)settingsProp.get(SettingsContext.AlphaNGLDM));
		Integer deltaNGLDM = Integer.valueOf((String)settingsProp.get(SettingsContext.DeltaNGLDM));
		
		String boxSizes = (String)settingsProp.get(SettingsContext.BoxSizesFRACTAL);
		
		//init feature calculations
		Shape2DFeatures shape2d = null;
		MorphologicalFeatures mf = null;
		LocalIntensityFeatures lif = null;
		IntensityBasedStatisticalFeatures isf = null;
		IntensityHistogramFeatures ihf = null;
		IntensityVolumeHistogramFeatures ivhf = null;
		GLCMFeatures glcm = null;
		GLRLMFeatures glrlm = null;
		GLSZMFeatures glszm = null;
		GLDZMFeatures gldzm = null;
		NGTDMFeatures ngtdm = null;
		NGLDMFeatures ngldm = null;
		FractalFeatures ff = null;
		
		for(String fname : featureNames) {
			String fam = fname.split("_")[0];//full family name
			switch(fam) {
			case SettingsContext.SHAPE2D:
				if(shape2d!=null) break;
				shape2d = new Shape2DFeatures(imp, mask,1,label);
				break;
			case SettingsContext.MORPHOLOGICAL:
				if(mf!=null) break;
				mf = new MorphologicalFeatures(imp, mask, label);
				break;
			case SettingsContext.LOCALINTENSITY:
				if(lif!=null) break;
				lif = new LocalIntensityFeatures(imp, mask, label);
				break;
			case SettingsContext.INTENSITYSTATS:
				if(isf!=null) break;
				isf = new IntensityBasedStatisticalFeatures(imp, mask, label);
				break;
			case SettingsContext.INTENSITYHISTOGRAM:
				if(ihf!=null) break;
				try {
					ihf = new IntensityHistogramFeatures(imp, mask, label, useBinCountHist, binCountHist,binWidthHist);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case SettingsContext.INTENSITYVOLUMEHISTOGRAM:
				if(ivhf!=null) break;
				int mode = 0;
				if(useOriginalIVH==false && useBinCountIVH==false) {
					mode=1;
				}else if(useBinCountIVH==true) {
					mode =2;
				}
				RadiomicsJ.IVH_binCount = binCountIVH;
				RadiomicsJ.IVH_binWidth = binWidthIVH;
				try {
					ivhf = new IntensityVolumeHistogramFeatures(imp, mask, label, mode);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case SettingsContext.GLCM:
				if(glcm!=null) break;
				try {
					glcm = new GLCMFeatures(imp, mask, label, deltaGLCM, useBinCountGLCM, binCountGLCM, binWidthGLCM, null);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case SettingsContext.GLRLM:
				if(glrlm!=null) break;
				try {
					glrlm = new GLRLMFeatures(imp, mask, label, useBinCountGLRLM, binCountGLRLM, binWidthGLRLM, null);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case SettingsContext.GLSZM:
				if(glszm!=null) break;
				try {
					glszm = new GLSZMFeatures(imp, mask, label, useBinCountGLSZM, binCountGLSZM, binWidthGLSZM);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case SettingsContext.GLDZM:
				if(gldzm!=null) break;
				try {
					gldzm = new GLDZMFeatures(imp, mask, label, useBinCountGLDZM, binCountGLDZM, binWidthGLDZM);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case SettingsContext.NGTDM:
				if(ngtdm!=null) break;
				try {
					ngtdm = new NGTDMFeatures(imp, mask, label, deltaNGTDM, useBinCountNGTDM, binCountNGTDM, binWidthNGTDM);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case SettingsContext.NGLDM:
				if(ngldm!=null) break;
				try {
					ngldm = new NGLDMFeatures(imp, mask, label,alphaNGLDM, deltaNGLDM, useBinCountNGLDM, binCountNGLDM, binWidthNGLDM);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case SettingsContext.FRACTAL:
				if(ff!=null) break;
				ff = new FractalFeatures(imp, mask, label, convertCommaSeparatedStringToIntArray(boxSizes));
				break;
			default:
				//do nothing
			}
		}
		
		//execute all feature calculations
		for(String fname : featureNames) {
			String fam = fname.split("_")[0];//full family name
			String name = fname.split("_")[1];
			switch(fam) {
			case SettingsContext.SHAPE2D:
				for(Shape2DFeatureType t:Shape2DFeatureType.values()) {
					if(name.equals(t.name())) {
						Double v = shape2d.calculate(t.id());
						v = v == null ? Double.NaN:v;
//						if(name.startsWith("PixelSur")) {
//							System.out.println(v);
//						}
						rt.addValue(fname, v);
					}
				}
				break;
			case SettingsContext.MORPHOLOGICAL:
				for(MorphologicalFeatureType t:MorphologicalFeatureType.values()) {
					if(name.equals(t.name())) {
						rt.addValue(fname, mf.calculate(t.id()));
					}
				}
				break;
			case SettingsContext.LOCALINTENSITY:
				for(LocalIntensityFeatureType t:LocalIntensityFeatureType.values()) {
					if(name.equals(t.name())) {
						rt.addValue(fname, lif.calculate(t.id()));
					}
				}
				break;
			case SettingsContext.INTENSITYSTATS:
				for(IntensityBasedStatisticalFeatureType t:IntensityBasedStatisticalFeatureType.values()) {
					if(name.equals(t.name())) {
						rt.addValue(fname, isf.calculate(t.id()));
					}
				}
				break;
			case SettingsContext.INTENSITYHISTOGRAM:
				for(IntensityHistogramFeatureType t:IntensityHistogramFeatureType.values()) {
					if(name.equals(t.name())) {
						try {
							rt.addValue(fname, ihf.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			case SettingsContext.INTENSITYVOLUMEHISTOGRAM:
				for(IntensityVolumeHistogramFeatureType t:IntensityVolumeHistogramFeatureType.values()) {
					if(name.equals(t.name())) {
						try {
							rt.addValue(fname, ivhf.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			case SettingsContext.GLCM:
				for(GLCMFeatureType t:GLCMFeatureType.values()) {
					if(name.equals(t.name())) {
						try {
							rt.addValue(fname, glcm.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			case SettingsContext.GLRLM:
				for(GLRLMFeatureType t:GLRLMFeatureType.values()) {
					if(name.equals(t.name())) {
						try {
							rt.addValue(fname, glrlm.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			case SettingsContext.GLSZM:
				for(GLSZMFeatureType t:GLSZMFeatureType.values()) {
					if(name.equals(t.name())) {
						try {
							rt.addValue(fname, glszm.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			case SettingsContext.GLDZM:
				for(GLDZMFeatureType t:GLDZMFeatureType.values()) {
					if(name.equals(t.name())) {
						try {
							rt.addValue(fname, gldzm.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			case SettingsContext.NGTDM:
				for(NGTDMFeatureType t:NGTDMFeatureType.values()) {
					if(name.equals(t.name())) {
						try {
							rt.addValue(fname, ngtdm.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			case SettingsContext.NGLDM:
				for(NGLDMFeatureType t:NGLDMFeatureType.values()) {
					if(name.equals(t.name())) {
						try {
							rt.addValue(fname, ngldm.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
				break;
			case SettingsContext.FRACTAL:
				for(FractalFeatureType t:FractalFeatureType.values()) {
					if(name.equals(t.name())) {
						try {
							rt.addValue(fname, ff.calculate(t.id()));
						} catch ( Exception e) {
							e.printStackTrace();
						}
					}
				}
			default:
				//do nothing
			}
		}
		return rt;
	}

	public ResultsTable calcAllFeatures(
			RadiomicsSettings setting, 
			List<RoiObj> rois/*belonging a class*/, 
			Praparat prap) {
		Properties settingsProp = setting.currentSettings();
		Integer label = Integer.valueOf((String)settingsProp.get(SettingsContext.MASK_LABEL));
		boolean d3_basis = Boolean.valueOf((String)settingsProp.getProperty(SettingsContext.D3Basis));
		List<String> featureNames = setting.getTargetFeatureNames();
		ResultsTable rt = null;
		if (d3_basis) {
			ImagePlus mask = createMaskWithRoisFor3D(prap, rois, label);
			ImagePlus imp = prap.getImagePlus();
			ImagePlus pair[] = preprocessing(imp, mask, settingsProp);
			try {
				ResultsTable rt_ = calcFeatures(settingsProp, featureNames, pair[0], pair[1], RadiomicsJ.label_);
				rt = combineTables(rt, rt_);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else { // 2d basis
			// if 2d basis, calculate slice by slice
			ImagePlus imp = prap.getImagePlus(-1,-1);
			for (RoiObj r : rois) {
				String sopUID = r.getProperty(RoiDBKey.SOPInstanceUID);
				if(sopUID == null) {
					System.err.println("Cannot create mask from this RoiObj...");
					continue;
				}
				int pos = prap.getSlidePosition(sopUID);//0 to n-1
				if(pos == -1) {
					System.out.println("This roi can not asign any slices...sklip.:"+r.getName());
					continue;
				}
				ImagePlus slice = new ImagePlus("", imp.getStack().getProcessor(pos+1));
				ByteProcessor maskIp = new ByteProcessor(slice.getWidth(), slice.getHeight());
				maskIp.setValue(label);
				maskIp.fill(new RoiConverter().convert2Roi(r));
				ImagePlus mask = new ImagePlus("mask", maskIp);
				mask.setCalibration(imp.getCalibration());
				mask.getCalibration().disableDensityCalibration();
				
				ImagePlus pair[] = preprocessing(slice, mask, settingsProp);
				
				ResultsTable rt_ = calcFeatures(settingsProp, featureNames, pair[0], pair[1], RadiomicsJ.label_);
				rt = combineTables(rt, rt_);
			}
		}
		return rt;
	}
	
	public ResultsTable calcAllFeatures(
			Properties settingsProp,  // from by setting.currentSettings();
			List<String> featureNames, // from by setting.getTargetFeatureNames();
			ImagePlus images, 
			ImagePlus masks) {
		
		boolean d3_basis = Boolean.valueOf((String)settingsProp.getProperty(SettingsContext.D3Basis));
		
		if (d3_basis) {
			ImagePlus pair[] = preprocessing(images, masks, settingsProp);
			try {
				ResultsTable rt_ = calcFeatures(settingsProp, featureNames, pair[0], pair[1], RadiomicsJ.label_);
				return rt_;
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else { // 2d basis
			// if 2d basis, calculate slice by slice
			ResultsTable rt = null;
			for (int p =1; p<=images.getNSlices(); p++) {
				ImagePlus slice = new ImagePlus(images.getStack().getSliceLabel(p), images.getStack().getProcessor(p));
				ImagePlus mask = new ImagePlus(masks.getStack().getSliceLabel(p), masks.getStack().getProcessor(p));
				ImageStatistics stats = ImageStatistics.getStatistics(mask.getProcessor(), Measurements.MIN_MAX, null/*calibration*/);
				if ((int) stats.max == 0) {
					// this is blank mask, skip.
					continue;
				}
				slice.setCalibration(images.getCalibration());
				mask.setCalibration(slice.getCalibration());
				mask.getCalibration().disableDensityCalibration();
				ImagePlus pair[] = preprocessing(slice, mask, settingsProp);
				ResultsTable rt_ = calcFeatures(settingsProp, featureNames, pair[0], pair[1], RadiomicsJ.label_);
				rt = combineTables(rt, rt_);
			}
			return rt;
		}
		return null;
	}
	
	private ImagePlus[] preprocessing(ImagePlus imp, ImagePlus mask, Properties settingsProp) {
		
		if(settingsProp==null) {
			settingsProp = setting.currentSettings();
		}
		
		String val = settingsProp.getProperty(SettingsContext.MASK_LABEL);
		if(val == null || val.equals("") || val.toLowerCase().equals("null") ) {
			//keep default
			throw new IllegalArgumentException("RadiomicsPipeline:preprocessing() require MASK_LABEL...");
		}
		int targetLabel = -1;
		try {
			targetLabel = Integer.valueOf(val);
		}catch(NumberFormatException e) {
			throw e;
		}
		
		boolean d3_basis = ((String)settingsProp.get(SettingsContext.D3Basis)).equals("true");
		boolean doResample = ((String)settingsProp.get(SettingsContext.Resampling)).equals("true");
		boolean doRangeFiltering = ((String)settingsProp.get(SettingsContext.RangeFiltering)).equals("true");
		boolean doRemoveOutliers = ((String)settingsProp.get(SettingsContext.RemoveOutliers)).equals("true");
		boolean doNormalize = false;// todo
		
		//fail safe
		mask.setCalibration(imp.getCalibration());
		
		/**
		 * MASK_LABEL to be ONE.
		 */
		ImagePlus mask_ = io.github.tatsunidas.radiomics.main.Utils.initMaskAsFloatAndConvertLabelOne(mask, targetLabel/*Be careful, this is targetLabel.*/);
		
		/**
		 * Following masks have always Label=1. This is same as Radiomics.Label_ value.
		 */
		
		ImagePlus[] imagePair = new ImagePlus[] {imp, mask_};
		if (imagePair[0] == null || imagePair[1] == null) {
			System.out.println("RadiomicsJ:preprocess()::Creating Mask was failed. return.");
			return new ImagePlus[] { imp, mask };
		}
		//resample
		if(doResample) {
			if(Utils.isDebug) {
				System.out.println("==========================");
				System.out.println("Before resample IMAGE:(W)"+imagePair[0].getWidth()+"(H)"+imagePair[0].getHeight()+"(D)"+imagePair[0].getNSlices());
				System.out.println("Before resample MASK:(W)"+imagePair[1].getWidth()+"(H)"+imagePair[1].getHeight()+"(D)"+imagePair[1].getNSlices());
				System.out.println("==========================");
			}
			
			Double[] xyz = new Double[] {Double.NaN, Double.NaN, Double.NaN};
			
			val = settingsProp.getProperty(SettingsContext.ResamplingX);
			if(val == null || val.equals("") || val.toLowerCase().equals("null") ) {
				//keep default
				throw new IllegalArgumentException("RadiomicsPipeline:preprocessing()-Resampling- require resampling XYZ sizes...");
			}
			try {
				xyz[0] = Double.valueOf(val);
			}catch(NumberFormatException e) {
				throw e;
			}
			
			val = settingsProp.getProperty(SettingsContext.ResamplingY);
			if(val == null || val.equals("") || val.toLowerCase().equals("null") ) {
				//keep default
				throw new IllegalArgumentException("RadiomicsPipeline:preprocessing()-Resampling- require resampling XYZ sizes...");
			}
			try {
				xyz[1] = Double.valueOf(val);
			}catch(NumberFormatException e) {
				throw e;
			}
			
			val = settingsProp.getProperty(SettingsContext.ResamplingZ);
			if(val == null || val.equals("") || val.toLowerCase().equals("null") ) {
				//keep default
				throw new IllegalArgumentException("RadiomicsPipeline:preprocessing()-Resampling- require resampling XYZ sizes...");
			}
			try {
				xyz[2] = Double.valueOf(val);
			}catch(NumberFormatException e) {
				throw e;
			}
			
			if(d3_basis) {
				for(Double v : xyz) {
					if(v <= 0) throw new IllegalArgumentException("RadiomicsPipeline:preprocessing()-Resampling3D- require larger than 0 voxel sizes.");
				}
			}else {
				if(xyz[0] <= 0 || xyz[1] <= 0) {
					throw new IllegalArgumentException("RadiomicsPipeline:preprocessing()-Resampling2D- require larger than 0 pixel sizes.");
				}
			}
			
			if(d3_basis == false) {
				//ignore z
				imagePair[0] = io.github.tatsunidas.radiomics.main.Utils.resample2D(imagePair[0], false, xyz[0].doubleValue(), xyz[1].doubleValue(), RadiomicsJ.interpolation2D);
				imagePair[1] = io.github.tatsunidas.radiomics.main.Utils.resample2D(imagePair[1], true, xyz[0].doubleValue(), xyz[1].doubleValue(), RadiomicsJ.interpolation2D);
			}else {
				// trilinear interpolation
				imagePair[0] = io.github.tatsunidas.radiomics.main.Utils.resample3D(imagePair[0], false, xyz[0].doubleValue(), xyz[1].doubleValue(), xyz[2].doubleValue());
				imagePair[1] = io.github.tatsunidas.radiomics.main.Utils.resample3D(imagePair[1], true, xyz[0].doubleValue(), xyz[1].doubleValue(), xyz[2].doubleValue());
			}
			if(Utils.isDebug) {
				System.out.println("==========================");
				System.out.println("After resample IMAGE:(W)"+imagePair[0].getWidth()+"(H)"+imagePair[0].getHeight()+"(D)"+imagePair[0].getNSlices());
				System.out.println("After resample MASK:(W)"+imagePair[1].getWidth()+"(H)"+imagePair[1].getHeight()+"(D)"+imagePair[1].getNSlices());
				System.out.println("==========================");
			}
		}
		//range filtering and remove outliers
		//use label 1.
		if(doRangeFiltering) {
			
			Double[] min_max = new Double[] {Double.NaN, Double.NaN};
			
			val = settingsProp.getProperty(SettingsContext.RangeFilteringMin);
			if(val == null || val.equals("") || val.toLowerCase().equals("null") ) {
				//keep default
				throw new IllegalArgumentException("RadiomicsPipeline:preprocessing()-RangeFiltering- require range min and max...");
			}
			try {
				min_max[0] = Double.valueOf(val);
			}catch(NumberFormatException e) {
				throw e;
			}
			
			val = settingsProp.getProperty(SettingsContext.RangeFilteringMax);
			if(val == null || val.equals("") || val.toLowerCase().equals("null") ) {
				//keep default
				throw new IllegalArgumentException("RadiomicsPipeline:preprocessing()-RangeFiltering- require range min and max...");
			}
			try {
				min_max[1] = Double.valueOf(val);
			}catch(NumberFormatException e) {
				throw e;
			}
			
			Double rangeMin = min_max[0];
			Double rangeMax = min_max[1];
			if(rangeMax != null && rangeMin != null) {
				//resegment only mask.
				imagePair[1] = ImagePreprocessing.rangeFiltering(imagePair[0], imagePair[1], RadiomicsJ.label_, rangeMax, rangeMin);
			}
			if(Utils.isDebug) {
				System.out.println("==========================");
				System.out.println("Range Filtering Resegmentation was done.");
				System.out.println("==========================");
			}
		}
		
		if(doRemoveOutliers) {
			// get new mask removed outliers
			
			val = settingsProp.getProperty(SettingsContext.RemoveOutliersSigma);
			if(val == null || val.equals("") || val.toLowerCase().equals("null") ) {
				//keep default
				throw new IllegalArgumentException("RadiomicsPipeline:preprocessing()-RemoveOutliers- require sigma...");
			}
			Double sigma = Double.NaN;
			try {
				sigma = Double.valueOf(val);
			}catch(NumberFormatException e) {
				throw e;
			}
			
			if(sigma == null || sigma.isNaN() || sigma <= 0 ) {
				System.out.println("Sigma is null or NaN or less than zero. Use default sigma=3.");
			}else {
				RadiomicsJ.zScore = sigma;
			}
			imagePair[1] = ImagePreprocessing.outlierFiltering(imagePair[0], imagePair[1], RadiomicsJ.label_);
			if(Utils.isDebug) {
				System.out.println("==========================");
				System.out.println("Remove Outliers was done.");
				System.out.println("==========================");
			}
		}
		/*
		 * normalize: under development in RadiomicsJ, DO NOT USE
		 */
		if(doNormalize) {
//			if(debug) {
//				System.out.println("perform normalization ...");
//			}
//			resampledImp = ImagePreprocessing.normalize(resampledImp, resegmentedMask, RadiomicsJ.label_);
		}
		return imagePair;
	}
	
	public ResultsTable combineTables(ResultsTable to, ResultsTable from) {
		if(to == null || to.getCounter() == 0) {
			return from;
		}else {
			if(from != null) {
				String[] headings = from.getHeadings();
				for(int i=0; i< from.size(); i++) {
					to.incrementCounter();
					int row  = 0;
					for(String h : headings) {
						if(h.equals("ID") || h.startsWith("OperationalInfo_") || h.equals("LABEL")) {
							String v = from.getStringValue(h, row);
							to.addValue(h, v);
						}else {
							double v = from.getValue(h, row);
							to.addValue(h, v);
						}
					}
				}
			}
		}
		return to;
	}

	public int[] convertCommaSeparatedStringToIntArray(String commaSeparatedString) {
		// null または空の文字列の場合、空の配列を返す
		if (commaSeparatedString == null || commaSeparatedString.trim().isEmpty()) {
			return null;
		}
		// カンマで文字列を分割
		// trim() を使用して前後の空白を除去
		String[] stringNumbers = commaSeparatedString.split(",");
		// 変換された整数を一時的に格納するためのリスト
		List<Integer> intList = new ArrayList<>();
		for (String s : stringNumbers) {
			// 各文字列をトリムし、空でないことを確認してから整数に変換
			String trimmedS = s.trim();
			if (!trimmedS.isEmpty()) {
				try {
					intList.add(Integer.parseInt(trimmedS));
				} catch (NumberFormatException e) {
					// 数字に変換できない文字列が含まれていた場合
					System.err.println("エラー: '" + trimmedS + "' は有効な数字ではありません。");
					throw e; // 例外を再スローするか、エラー処理を記述
				}
			}
		}
		// List<Integer> を int[] に変換
		int[] intArray = new int[intList.size()];
		for (int i = 0; i < intList.size(); i++) {
			intArray[i] = intList.get(i);
		}
		return intArray;
	}
	
	/**
     * RadimomicsJのResultsTableからWEKAのInstancesオブジェクトを生成します。
     *
     * @param rt              RadimomicsJのResultsTable
     * @param classAttributeName 予測したいクラス属性の列名 (nullの場合、クラス属性は設定されません)
     * @return WEKAのInstancesオブジェクト
     * @throws Exception エラーが発生した場合
     */
	public Instances convertResultsTableToWekaInstances(
			ResultsTable rt, String classAttributeName) throws Exception {
		
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
	
	private void prepareTrainDataset(
			ResultsTable rt, 
			String targetColName, 
			boolean impute, boolean balance, boolean featureSelect) {
		String[] headerStrings = rt.getHeadings();
		List<String> header = Arrays.asList(headerStrings);
		explanatoryAttr = toExplanatoryAttributes(header);//ラベル列は含めない。
		targetAttr = targetClassAttribute4Clf(rt, targetColName);
		ArrayList<Attribute> attributes = new ArrayList<>(explanatoryAttr);
		attributes.add(targetAttr);//ここで最後に接続
		trainingDataset = new Instances("TrainingDataset", attributes, 0/* volume of row */);

		// クラス属性のインデックスを設定 (通常は最後の属性)
		trainingDataset.setClassIndex(attributes.size() - 1);

		// 値をデータセットに追加
		int col = header.size();
		int row = rt.size();
		boolean numericalTarget = isNumericalTarget(rt.getColumnAsStrings(targetColName));
		for (int r = 0; r < row; r++) {
			Instance record = new DenseInstance(col); // 属性の数
			for (int c = 0; c < col; c++) {
				String h = header.get(c);
				Attribute attr = attributes.get(c);
				if (h.equals(targetColName)) {
					String v = rt.getColumnAsStrings(h)[r];
					/*
					 * drop if target is null
					 */
					if (v == null || v.length() == 0) {
						continue;
					}
					// string or double ?
					if (numericalTarget) {
						Double dv = rt.getColumn(h)[r];
						record.setValue(attr, dv);
					} else {// string
						record.setValue(attr, v);
					}
					continue;
				}
				// others always numerical.
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
		
		if (impute) {
			// 3. ReplaceMissingValuesフィルターのインスタンスを作成
			ReplaceMissingValues replacer = new ReplaceMissingValues();
			try {
				// フィルターにデータセットのフォーマットを教える (必須)
				replacer.setInputFormat(trainingDataset);
				// 新しいデータセットを取得
				trainingDataset = Filter.useFilter(trainingDataset, replacer);
				System.out.println("\n--- 平均値で補完しました ---");
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Simple measn imputation was Failed...");
			}
		}
		
		if (balance) {
			int numClasses = trainingDataset.numClasses();
			int[] classCounts = new int[numClasses];
			for (int i = 0; i < trainingDataset.numInstances(); i++) {
				int cls = (int) trainingDataset.instance(i).classValue();
				classCounts[cls]++;
			}

			// 最大クラス数（多数クラスのインスタンス数）を見つける
			int maxCount = 0;
			for (int c : classCounts) {
				if (c > maxCount)
					maxCount = c;
			}

			Instances currentData = new Instances(trainingDataset);
			// 各クラスに対して個別にSMOTEを適用
			for (int clsIndex = 0; clsIndex < numClasses; clsIndex++) {
				int currentCount = classCounts[clsIndex];
				if (currentCount < maxCount) {
					double percentage = 100.0 * (maxCount - currentCount) / (double) currentCount;
					SMOTE smote = new SMOTE();
					try {
						smote.setInputFormat(currentData);
						smote.setClassValue(String.valueOf(clsIndex)); // 文字列でクラス指定
						smote.setPercentage(percentage);
						currentData = Filter.useFilter(currentData, smote);
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("Connt oversampling with SMOTE !!! Training was failed !!!");
						return;
					}
				}
			}
			trainingDataset = currentData;
			// クラス数確認（任意）
			int[] finalCounts = new int[numClasses];
			for (int i = 0; i < currentData.numInstances(); i++) {
				int cls = (int) currentData.instance(i).classValue();
				finalCounts[cls]++;
			}

			System.out.println("--- Balanced class distribution ---");
			for (int i = 0; i < numClasses; i++) {
				System.out.printf("  Class %d: %d instances%n", i, finalCounts[i]);
			}
		}

		if (featureSelect && trainingDataset.size()>=5) {
			System.out.println("--- Start feature selection ---");
			// --- ステップ1: 分散が0の属性を除去 ---
			System.out.println("\n--- ステップ1: 分散が0の属性を除去 ---");
			RemoveUseless removeUselessFilter = new RemoveUseless();
			Instances dataAfterRemoveUseless = null;
			try {
				removeUselessFilter.setInputFormat(trainingDataset);
				dataAfterRemoveUseless = Filter.useFilter(trainingDataset, removeUselessFilter);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			System.out.println("フィルタリング後の属性数: " + dataAfterRemoveUseless.numAttributes());
			System.out.println(dataAfterRemoveUseless.toSummaryString());

			Instances dataAfterRemoveMultiCorr = null;
			try {
				dataAfterRemoveMultiCorr = dropHighlyCorrelatedFeatures(dataAfterRemoveUseless, 0.9);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// --- ステップ3: LASSOによる特徴選択 ---
			System.out.println("\n--- ステップ2: LASSOによる特徴選択 (L1正則化を持つロジスティック回帰を評価器として使用) ---");
			// LASSOの係数が0になるような特徴選択を行うには、L1正則化をサポートする分類器を使用します。
			// ここではLogistic (Lasso) を使用します。
			// LogisticのデフォルトではL1正則化が有効になっています。
			// (Ridge (L2) と Lasso (L1) は、useRidge=false, useLasso=true で制御されます)

			// 評価器 (Evaluator) の設定: WrapperSubsetEval を使用し、内部でLogisticモデルを評価
			WrapperSubsetEval wrapperEval = new WrapperSubsetEval();
			Logistic logisticClassifier = new Logistic();
			// LogisticのL1正則化の強さ (lambda) を調整することも可能ですが、
			// デフォルト設定で試すのが一般的です。
			// logisticClassifier.setRidge(0.01); // L2正則化の強さ
			// (Lassoの場合は0にするか、useRidge=false)
			wrapperEval.setClassifier(logisticClassifier);

			// 探索アルゴリズム (Search) の設定: BestFirst を使用
			// 最適な特徴量サブセットを効率的に探索
			BestFirst search = new BestFirst();
			// search.setDirection(new SelectedTag(BestFirst.SEARCH_FORWARD,
			// BestFirst.TAGS_SEARCH_DIRECTION)); // 順方向探索
			// search.setDirection(new SelectedTag(BestFirst.SEARCH_BACKWARD,
			// BestFirst.TAGS_SEARCH_DIRECTION)); // 逆方向探索
			// search.setDirection(new SelectedTag(BestFirst.SEARCH_BIDIRECTIONAL,
			// BestFirst.TAGS_SEARCH_DIRECTION)); // 両方向探索
			// デフォルトは両方向探索

			// AttributeSelection フィルターの適用
			AttributeSelection attributeSelection = new AttributeSelection();
			attributeSelection.setEvaluator(wrapperEval);
			attributeSelection.setSearch(search);
			Instances finalSelectedData = null;
			try {
				attributeSelection.SelectAttributes(dataAfterRemoveMultiCorr);
				finalSelectedData = attributeSelection.reduceDimensionality(dataAfterRemoveMultiCorr);
				trainingDataset = finalSelectedData;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("最終的に選択された属性数: " + finalSelectedData.numAttributes());
			System.out.println("最終的に選択された属性:\n" + finalSelectedData.toSummaryString());
		}else {
			if(featureSelect) {
				System.out.println("Feature Select was canceled because number of instances less than 5...");
			}
		}
	}
	
	/**
     * WEKA Instances内で相関の高い数値属性のペアを見つけ、一方をドロップします。
     * ただし、ターゲット変数（クラス属性）は相関計算およびドロップの対象外とします。
     *
     * @param data        処理対象のWEKA Instancesオブジェクト。
     * @param threshold   相関のしきい値。この値以上の相関がある場合、一方の属性をドロップします。
     * @return            相関の高い属性がドロップされた新しいInstancesオブジェクト。
     * @throws Exception  フィルタリング処理中にエラーが発生した場合。
     */
    public static Instances dropHighlyCorrelatedFeatures(Instances data, double threshold) throws Exception {
        // クラス属性のインデックスを取得
        int classIndex = data.classIndex();

        // 相関計算の対象となる数値属性のみを抽出
        List<Attribute> numericAttributesForCorrelation = new ArrayList<>();
        List<Integer> originalNumericAttributeIndexes = new ArrayList<>(); // 元のInstancesでのインデックスを保持
        
        for (int i = 0; i < data.numAttributes(); i++) {
            Attribute att = data.attribute(i);
            // 数値属性であり、かつクラス属性ではない場合のみ対象とする
            if (att.isNumeric() && i != classIndex) {
                numericAttributesForCorrelation.add(att);
                originalNumericAttributeIndexes.add(i);
            }
        }

        if (numericAttributesForCorrelation.isEmpty()) {
            System.out.println("警告: 相関計算対象の数値属性が見つかりませんでした。元のInstancesを返します。");
            return data;
        }

        int numNumericAttributes = numericAttributesForCorrelation.size();
        double[][] correlationMatrix = new double[numNumericAttributes][numNumericAttributes];
        PearsonsCorrelation pearsonCorrelation = new PearsonsCorrelation();

        // 相関行列の計算
        for (int i = 0; i < numNumericAttributes; i++) {
            for (int j = i; j < numNumericAttributes; j++) {
                if (i == j) {
                    correlationMatrix[i][j] = 1.0;
                } else {
                    Attribute att1 = numericAttributesForCorrelation.get(i);
                    Attribute att2 = numericAttributesForCorrelation.get(j);

                    double[] x = data.attributeToDoubleArray(att1.index());
                    double[] y = data.attributeToDoubleArray(att2.index());

                    double correlation = pearsonCorrelation.correlation(x, y);
                    correlationMatrix[i][j] = Math.abs(correlation);
                    correlationMatrix[j][i] = Math.abs(correlation);
                }
            }
        }

        // ドロップする属性の元のインデックスを特定
        List<Integer> attributeIndexesToDrop = new ArrayList<>();
        List<String> attributeNamesToDrop = new ArrayList<>();

        for (int i = 0; i < numNumericAttributes; i++) {
            for (int j = i + 1; j < numNumericAttributes; j++) {
                if (correlationMatrix[i][j] > threshold) {
                    // 相関が高い場合、後の方の属性（j番目）をドロップ候補に追加
                    // ここでのjは `numericAttributesForCorrelation` リスト内でのインデックス
                    int originalIndexToDrop = originalNumericAttributeIndexes.get(j);
                    
                    // クラス属性は絶対にドロップしないことを保証
                    if (originalIndexToDrop != classIndex && !attributeIndexesToDrop.contains(originalIndexToDrop)) {
                        attributeIndexesToDrop.add(originalIndexToDrop);
                        attributeNamesToDrop.add(data.attribute(originalIndexToDrop).name());
                    }
                }
            }
        }
        
        // ドロップする属性のインデックスを昇順にソート（WEKAのRemoveフィルタの要件のため）
        Collections.sort(attributeIndexesToDrop);

        // WEKAのRemoveフィルタを使用して属性をドロップ
        Remove removeFilter = new Remove();
        String attributeIndices = attributeIndexesToDrop.stream()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(","));
        
        if (attributeIndices.isEmpty()) {
            System.out.println("ドロップする属性はありませんでした。元のInstancesを返します。");
            return data;
        }

        removeFilter.setAttributeIndices(attributeIndices);
        removeFilter.setInputFormat(data);
        Instances newData = Filter.useFilter(data, removeFilter);

        System.out.println("MultiCorrとしてドロップされた属性: " + attributeNamesToDrop);
        return newData;
    }

//	private void prepareTrainDataset(ResultsTable rt, String targetColName, List<String> drop) {
//		String[] headerStrings = rt.getHeadings();
//		List<String> header = Arrays.asList(headerStrings);
//		if (drop != null) {
//			header.removeAll(drop);
//		}
//		explanatoryAttr = toAttributes(header);
//		targetAttr = targetClassAttribute4Clf(rt, targetColName);
//		ArrayList<Attribute> attributes = new ArrayList<>(explanatoryAttr);
//		attributes.add(targetAttr);
//		trainingDataset = new Instances("TrainingDataset", attributes, 0/* volume of row */);
//
//		// クラス属性のインデックスを設定 (通常は最後の属性)
//		trainingDataset.setClassIndex(attributes.size() - 1);
//
//		// 訓練データのインスタンスを作成してデータセットに追加
//		int col = header.size();
//		int row = rt.size();
//		boolean numericalTarget = isNumericalTarget(rt.getColumnAsStrings(targetColName));
//		for (int r = 0; r < row; r++) {
//			Instance record = new DenseInstance(col); // 属性の数
//			for (int c = 0; c < col; c++) {
//				String h = header.get(c);
//				if (h.startsWith(SettingsContext.DIAGNOSTICS) || h.startsWith(SettingsContext.OPERATIONAL)) {
//					continue;
//				}
//				Attribute attr = attributes.get(c);
//				if (h.equals(targetColName)) {
//					String v = rt.getColumnAsStrings(h)[r];
//					/*
//					 * drop if target is null
//					 */
//					if (v == null || v.length() == 0) {
//						continue;
//					}
//					// string or double ?
//					if (numericalTarget) {
//						Double dv = rt.getColumn(h)[r];
//						record.setValue(attr, dv);
//					} else {// string
//						record.setValue(attr, v);
//					}
//					continue;
//				}
//				// others always numerical.
//				Double v = rt.getColumn(h)[r];
////				if(v == null) {
////					v = weka.core.Utils.missingValue();//Double.NaN
////				}
//				record.setValue(attr, v);
//			}
//			trainingDataset.add(record);
//		}
//		System.out.println("データセットが正常にロードされました。");
//		System.out.println("インスタンス数: " + trainingDataset.numInstances());
//		System.out.println("属性数: " + trainingDataset.numAttributes());
//	}
	
	/**
	 * 
	 * @return segment img, proba img
	 */
	public ImagePlus predict(int slidePos/*0 to n-1*/) {
		/**
		 * TODO
		 * stride 2d/3d rect roi over all voxel
		 * estimate processing time
		 */
		
		if(!isPredictionReady()) {
			System.out.println("Prediction not ready...");
			return null;
		}
		
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
		
		if(prap == null) {
			Viewer2DScreen d2s = Viewer2DScreen.getInstance();
			prap = d2s.getSelectedPraps().get(0);
			if(prap == null) {
				System.out.println("Cannot load any Series. Prediction was interupted.");
				return null;
			}
		}
		
		ImagePlus imp = prap.getImagePlus(-1,-1);
		//imp = new ImagePlus("", imp.getStack().getProcessor(slidePos+1));//DO NOT DO THIS
		int w = imp.getWidth();
		int h = imp.getHeight();
		Properties prop = setting.currentSettings();
		
//		boolean is3D = ((String)prop.get(SettingsContext.D3Basis)).equals("true");
		boolean is3D = false;//TODO
		
		int label = Integer.valueOf((String)prop.get(SettingsContext.MASK_LABEL));
		
		FloatProcessor labelImg = new FloatProcessor(w,h);
		FloatProcessor probaImg = new FloatProcessor(w,h);
		
		// 予測用インスタンスの受け皿となる、ヘッダー情報のみの空のデータセットを作成
		Instances predictionHeader = new Instances(trainingDataset, 0);
		// クラス属性のインデックスをセット（ヘッダーコピー時に引き継がれるが念のため）
		predictionHeader.setClassIndex(predictionHeader.numAttributes() - 1);
		
		for (int j = 0; j < h; j++) {
			for (int i = 0; i < w; i++) {
				try {
					ImagePlus patch_img = cropAndPadImage3D(imp, i, j, slidePos, patchSize, patchSize,
							is3D ? patchSize : 1);
					ResultsTable rt = calcFeatures(prop, featureNames, patch_img, null, label);

					// 1. 訓練データと同じ構造を持つ、データ1行分のインスタンスを生成
					Instance inst = new DenseInstance(predictionHeader.numAttributes());

					// 2. このインスタンスがどのデータセットに属するかを関連付ける (重要)
					inst.setDataset(predictionHeader);

					// 3. ResultsTableから値を取得し、インスタンスにセットしていく
					// featureNamesの順序と、rtの列の順序が一致している必要があります
					for (int k = 0; k < featureNames.size(); k++) {
						String featureName = featureNames.get(k);
						// rtから値を取得（rt.getValueの第一引数は列名、第二引数は行番号）
						double value = rt.getValue(featureName, 0);
						// instに値をセット（第二引数は属性のインデックス）
						inst.setValue(k, value);
					}

					// 4. クラス属性の値を「不明(?)」としてセットする
					// これにより、分類器はこの値を予測しようとします
					inst.setClassMissing();

//					Instances ds2pred = convertResultsTableToWekaInstances(rt, null/*targetColName*/);
//					Instance inst = ds2pred.firstInstance();
					// double predictedClassIndex = clf.classifyInstance(inst);
					// String predictedClassName = trainingDataset.classAttribute().value((int)
					// predictedClassIndex);
					// 各クラスに属する確率分布の予測
					double[] proba = clf.distributionForInstance(inst);
					int predClassLabel = getIndexOfMaxValue(proba);
					labelImg.setf(i, j, (float) predClassLabel);
					probaImg.setf(i, j, (float) proba[predClassLabel]);
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
	
	/**
	 * WEKAデータセット用のヘッダーのようなもの。属性定義。
	 * @param headers
	 * @return
	 */
	ArrayList<Attribute> toExplanatoryAttributes(List<String> headers){
		ArrayList<Attribute> fs = new ArrayList<>();
		for(String h : headers) {
			if(h.startsWith("Operational_")) {
				continue;
			}else if(h.startsWith("Diagnostics_")) {
				continue;
			}else if(h.startsWith("ID") || h.startsWith("LABEL")) {
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
	
	ImagePlus createMaskWithRoisFor3D(Praparat pp, List<RoiObj> rois, Integer label) {
		int lbl = label == null ? 255:label;
		int w = pp.getImageWidth();
		int h = pp.getImageHeight();
		int s = pp.getNumberOfImages();
		ImageStack stack = new ImageStack(w, h);
		for(int z=0; z<s; z++) {
			ImageProcessor ip = new ByteProcessor(w, h);
			stack.addSlice(ip);
		}
		for(RoiObj ro:rois) {
			String sopUID = ro.getProperty(RoiDBKey.SOPInstanceUID);
			if(sopUID == null) {
				System.err.println("Cannot create mask from this RoiObj...");
				continue;
			}
			int pos = pp.getSlidePosition(sopUID);//0 to n-1
			if(pos == -1) {
				System.out.println("This roi can not asign any slices...sklip.:"+ro.getName());
				continue;
			}
			Roi r = new RoiConverter().convert2Roi(ro);
			ImageProcessor ip = stack.getProcessor(pos+1);
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
