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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import io.github.tatsunidas.radiomics.features.FractalFeatureType;
import io.github.tatsunidas.radiomics.features.GLCMFeatureType;
import io.github.tatsunidas.radiomics.features.GLDZMFeatureType;
import io.github.tatsunidas.radiomics.features.GLRLMFeatureType;
import io.github.tatsunidas.radiomics.features.GLSZMFeatureType;
import io.github.tatsunidas.radiomics.features.IntensityBasedStatisticalFeatureType;
import io.github.tatsunidas.radiomics.features.IntensityHistogramFeatureType;
import io.github.tatsunidas.radiomics.features.IntensityVolumeHistogramFeatureType;
import io.github.tatsunidas.radiomics.features.LocalIntensityFeatureType;
import io.github.tatsunidas.radiomics.features.MorphologicalFeatureType;
import io.github.tatsunidas.radiomics.features.NGLDMFeatureType;
import io.github.tatsunidas.radiomics.features.NGTDMFeatureType;
import io.github.tatsunidas.radiomics.features.Shape2DFeatureType;
import io.github.tatsunidas.radiomics.main.RadiomicsJ;

public class RadiomicsSettings extends JPanel{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * Common settings
	 */
	//2d/3d switch, when turn on, images will calculate slice by slice
	boolean d3_mode = true;
	//label
	int label = 255;
	//discretization, count or width
	boolean useBinCount = true;
	double binWidth;
	int binCount;
	//remove outlier
	boolean removeOutliers = true;
	int sigma = 3;
	//resampling
	boolean resample = false;
	double vx;
	double vy;
	double vz;
	//range filtering
	boolean rangeFilter = false;
	double rangeMin;
	double rangeMax;
	
	String boxSizes = "2,3,4,6,8,12,16,32,64";
	
	List<String> featureNames;
	final int numOfTotalFeatures;
	
	/**
	 * Manhattan: same as "no_weight", M_ij/np.sum(M_ij)
	 * Euclidean: M_ij/np.sqrt(np.sum(M_ij**2))
	 * Infinity: M_ij/np.max(M_ij)
	 */
	final String[] norms = new String[] {"manhattan", "euclidean", "infinity"};
	
	final String OPERATIONAL = "Operational";
	final String DIAGNOSTICS = "Diagnostics";
	final String MORPHOLOGICAL = "Molphological";
	final String LOCALINTENSITY = "LocalIntensity";
	final String INTENSITYSTATS = "IntensityStats";
	final String INTENSITYHISTOGRAM = "IntensityHistogram";
	final String VOLUMEHISTOGRAM = "VolumeHistogram";
	final String GLCM = "GLCM";
	final String GLRLM = "GLRLM";
	final String GLSZM = "GLSZM";
	final String GLDZM = "GLDZM";
	final String NGTDM = "NGTDM";
	final String NGLDM = "NGLDM";
	final String FRACTAL = "Fractal";
	
	//calculation target and exclusion features
	DefaultListModel<String> targetListModel = new DefaultListModel<>();
	DefaultListModel<String> exclusionListModel = new DefaultListModel<>();
	JList<String> target;
	JList<String> exclusion;
	
	JSplitPane sp1;
	JSplitPane sp2;
	JLabel targetCount;
	JLabel exclusionCount;
		
	public RadiomicsSettings() {
		featureNames = featureNames();
		numOfTotalFeatures = featureNames.size();
		buildGUI();
	}
	
	private void buildGUI() {
		setLayout(new BorderLayout());
		JComponent common = buildCommonPanel();
		JComponent features = buildFeaturesPanel();
		sp1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		sp1.add(common,JSplitPane.LEFT);
		sp1.add(features,JSplitPane.RIGHT);
		sp1.setPreferredSize(new Dimension(800,400));
		JComponent parameters = buildParametersTab();
		sp2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		sp2.add(sp1,JSplitPane.TOP);
		sp2.add(parameters,JSplitPane.BOTTOM);
		sp2.setPreferredSize(new Dimension(800,200));
		add(sp2, BorderLayout.CENTER);
	}
	
	private JComponent buildCommonPanel() {
		JPanel common = new JPanel(new GridLayout(11, 1));
		addBorder(common, Color.white, "Preprocessing");
		//3d/2d
		JRadioButton d2Btn = new JRadioButton("2D basis");
		JRadioButton d3Btn = new JRadioButton("3D basis");
		JPanel dimPanel = new JPanel();
		FlowLayout fl = (FlowLayout) dimPanel.getLayout();
		fl.setAlignment(FlowLayout.LEFT);
		dimPanel.add(d2Btn);
		dimPanel.add(d3Btn);
		common.add(dimPanel);
		ButtonGroup dimGroup = new ButtonGroup();
		dimGroup.add(d2Btn);
		dimGroup.add(d3Btn);
		dimGroup.setSelected(d3Btn.getModel(), true);
		//label
		JLabel lbl = new JLabel("Label value:");
		JTextField lbltxt = new JTextField(10);
		JPanel lblP = new JPanel();
		lblP.setLayout(fl);
		lblP.add(lbl);
		lblP.add(lbltxt);
		common.add(lblP);
		//removeOutliers outliers
		JCheckBox roChk = new JCheckBox("Remove Outliers");
		roChk.setSelected(removeOutliers);
		common.add(roChk);
		JLabel rolbl = new JLabel("Sigma:");
		JTextField rotxt = new JTextField(10);
		rotxt.setText(sigma+"");
		JPanel roP = new JPanel();
		roP.add(rolbl);
		roP.add(rotxt);
		common.add(roP);
		//range filtering
		JCheckBox rfChk = new JCheckBox("Range Filtering");
		rfChk.setSelected(rangeFilter);
		common.add(rfChk);
		JLabel rfMinlbl = new JLabel("min:");
		JLabel rfMaxlbl = new JLabel("max:");
		JTextField rfMintxt = new JTextField(10);
		JTextField rfMaxtxt = new JTextField(10);
		JPanel rfMinP = new JPanel();
		JPanel rfMaxP = new JPanel();
		rfMinP.add(rfMinlbl);
		rfMinP.add(rfMintxt);
		rfMaxP.add(rfMaxlbl);
		rfMaxP.add(rfMaxtxt);
		common.add(rfMinP);
		common.add(rfMaxP);
		//resampling
		JCheckBox reChk = new JCheckBox("Resampling");
		rfChk.setSelected(resample);
		common.add(reChk);
		JLabel reXlbl = new JLabel("vx:");
		JLabel reYlbl = new JLabel("vy:");
		JLabel reZlbl = new JLabel("vz:");
		JTextField reXtxt = new JTextField(10);
		JTextField reYtxt = new JTextField(10);
		JTextField reZtxt = new JTextField(10);
		JPanel reXP = new JPanel();
		JPanel reYP = new JPanel();
		JPanel reZP = new JPanel();
		reXP.add(reXlbl);
		reXP.add(reXtxt);
		reYP.add(reYlbl);
		reYP.add(reYtxt);
		reZP.add(reZlbl);
		reZP.add(reZtxt);
		common.add(reXP);
		common.add(reYP);
		common.add(reZP);	
		JScrollPane sPane = new JScrollPane(common);
		return sPane;
	}
	
	private JComponent buildFeaturesPanel() {
		JPanel infoGroupChkP = new JPanel(new GridLayout(1,4));
		JCheckBox operational = new JCheckBox(OPERATIONAL);
		operational.setSelected(true);
		JCheckBox diagnostics = new JCheckBox(DIAGNOSTICS);
		diagnostics.setSelected(true);
		infoGroupChkP.add(operational);
		infoGroupChkP.add(diagnostics);
		addBorder(infoGroupChkP, Color.MAGENTA, "Info");
		
		JPanel featuresGroupChkP = new JPanel(new GridLayout(3,4));
		JCheckBox morphological = new JCheckBox(MORPHOLOGICAL);
		morphological.setSelected(true);
		JCheckBox localIntens = new JCheckBox(LOCALINTENSITY);
		localIntens.setSelected(true);
		JCheckBox intensityStats = new JCheckBox(INTENSITYSTATS);
		intensityStats.setSelected(true);
		JCheckBox histogram = new JCheckBox(INTENSITYHISTOGRAM);
		histogram.setSelected(true);
		JCheckBox volumeHist = new JCheckBox(VOLUMEHISTOGRAM);
		volumeHist.setSelected(true);
		JCheckBox glcm = new JCheckBox(GLCM);
		glcm.setSelected(true);
		JCheckBox glrlm = new JCheckBox(GLRLM);
		glrlm.setSelected(true);
		JCheckBox glszm = new JCheckBox(GLSZM);
		glszm.setSelected(true);
		JCheckBox gldzm = new JCheckBox(GLDZM);
		gldzm.setSelected(true);
		JCheckBox ngtdm = new JCheckBox(NGTDM);
		ngtdm.setSelected(true);
		JCheckBox ngldm = new JCheckBox(NGLDM);
		ngldm.setSelected(true);
		JCheckBox fractal = new JCheckBox(FRACTAL);
		fractal.setSelected(true);
		featuresGroupChkP.add(morphological);
		featuresGroupChkP.add(localIntens);
		featuresGroupChkP.add(intensityStats);
		featuresGroupChkP.add(histogram);
		featuresGroupChkP.add(volumeHist);
		featuresGroupChkP.add(glcm);
		featuresGroupChkP.add(glrlm);
		featuresGroupChkP.add(glszm);
		featuresGroupChkP.add(gldzm);
		featuresGroupChkP.add(ngtdm);
		featuresGroupChkP.add(ngldm);
		featuresGroupChkP.add(fractal);
		addBorder(featuresGroupChkP, Color.gray, "Features group");
		
		JPanel chksP = new JPanel(new BorderLayout());
		chksP.add(infoGroupChkP, BorderLayout.NORTH);
		chksP.add(featuresGroupChkP, BorderLayout.CENTER);
		
		JPanel featuresPanel = new JPanel();
		featuresPanel.setLayout(new BorderLayout());
		featuresPanel.add(chksP, BorderLayout.NORTH);
		
		JPanel featureListP = new JPanel();
		featureListP.setLayout(new BorderLayout());
		target = new JList<>(targetListModel);
		exclusion = new JList<>(exclusionListModel);
		//init first.
		targetCount = new JLabel("-/-");
		exclusionCount = new JLabel("-/-");
		RadiomicsJ radiomics = RadiomicsWindow.radiomics;
		/**
		 * TODO
		 * 現状、各特徴量の名前が一意になっていないので、
		 * RadiomicsJ側でname()に特徴ファミリー名を接頭辞に追加する。
		 */
//		HashSet<String> defaultExclusions = radiomics.getExcludedFeatures();
		HashSet<String> defaultExclusions = new HashSet<>();
		/*
		 * MorphologicalFeatureType.VolumeDensity_OrientedMinimumBoundingBox.name(),
		 * MorphologicalFeatureType.AreaDensity_OrientedMinimumBoundingBox.name(),
		 * MorphologicalFeatureType.VolumeDensity_MinimumVolumeEnclosingEllipsoid.name(),
		 * MorphologicalFeatureType.AreaDensity_MinimumVolumeEnclosingEllipsoid.name(),
		 * IntensityVolumeHistogramFeatureType.AreaUnderTheIVHCurve.name(),
		 * NGLDMFeatureType.DependenceCountPercentage.name(),
		 */
		defaultExclusions.add("Morpho_"+MorphologicalFeatureType.VolumeDensity_OrientedMinimumBoundingBox.name());
		defaultExclusions.add("Morpho_"+MorphologicalFeatureType.AreaDensity_OrientedMinimumBoundingBox.name());
		defaultExclusions.add("Morpho_"+MorphologicalFeatureType.VolumeDensity_MinimumVolumeEnclosingEllipsoid.name());
		defaultExclusions.add("Morpho_"+MorphologicalFeatureType.AreaDensity_MinimumVolumeEnclosingEllipsoid.name());
		defaultExclusions.add("IVH_"+IntensityVolumeHistogramFeatureType.AreaUnderTheIVHCurve.name());
		defaultExclusions.add("NGLDM_"+NGLDMFeatureType.DependenceCountPercentage.name());
		
		addList(featureNames, targetListModel);
		deleteFromList(new ArrayList<>(defaultExclusions), targetListModel);
		
		JPanel left = new JPanel(new BorderLayout());
		JPanel right = new JPanel(new BorderLayout());
		addBorder(left, Color.cyan, "TARGET");
		addBorder(right, Color.red, "EXCLUSION");
		JScrollPane leftSP = new JScrollPane(target);
		JScrollPane rightSP = new JScrollPane(exclusion);
		left.add(leftSP, BorderLayout.CENTER);
		right.add(rightSP, BorderLayout.CENTER);
		left.add(targetCount, BorderLayout.NORTH);
		right.add(exclusionCount, BorderLayout.NORTH);
		
		JPanel featureListCenter = new JPanel();
		featureListCenter.setLayout(new GridLayout(1,2));
		featureListCenter.add(left);
		featureListCenter.add(right);
		featureListP.add(featureListCenter, BorderLayout.CENTER);
		
		JPanel btnP = new JPanel();
		btnP.setLayout(new GridLayout(1,2));
		JButton removeFromTarget = new JButton("> Remove from calculation >");
		removeFromTarget.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int[] selected = target.getSelectedIndices();
				if(selected != null && selected.length > 0) {
					for(int i : selected) {
						String n = targetListModel.get(i);
						deleteFromList(n, targetListModel);
					}
				}
			}
		});
		JButton add2Target = new JButton("< Add to calculation <");
		add2Target.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int[] selected = exclusion.getSelectedIndices();
				if(selected != null && selected.length > 0) {
					for(int i : selected) {
						String n = exclusionListModel.get(i);
						deleteFromList(n, exclusionListModel);
					}
				}
			}
		});
		btnP.add(removeFromTarget);
		btnP.add(add2Target);
		featureListP.add(btnP, BorderLayout.SOUTH);
		
		featuresPanel.add(featureListP, BorderLayout.CENTER);
		JScrollPane sp = new JScrollPane(featuresPanel);
		return sp;
	}
	
	private JComponent buildParametersTab() {
		JTabbedPane tp = new JTabbedPane();
		//texture param
		JPanel textureParamsP = new JPanel();
		textureParamsP.setLayout(new GridLayout(7, 1));
		JScrollPane texturesS = new JScrollPane(textureParamsP);
		texturesS.setPreferredSize(new Dimension(400, 300));
		tp.addTab("Texture family prams", texturesS);
		//common discretization
		JPanel commonP = new JPanel();
		commonP.setLayout(new GridLayout(5, 1));
		JCheckBox useCommonBin = new JCheckBox("Use for all texture");
		commonP.add(useCommonBin);
		JRadioButton binCountBtn = new JRadioButton("Bin Count");
		JRadioButton binWidthBtn = new JRadioButton("Bin Width");
		JPanel binPanel = new JPanel();
		FlowLayout fl = (FlowLayout) binPanel.getLayout();
		fl.setAlignment(FlowLayout.LEFT);
		binPanel.add(binCountBtn);
		binPanel.add(binWidthBtn);
		commonP.add(binPanel);
		ButtonGroup binGroup = new ButtonGroup();
		binGroup.add(binCountBtn);
		binGroup.add(binWidthBtn);
		binGroup.setSelected(binCountBtn.getModel(), useBinCount);
		JLabel bclbl = new JLabel("Bin Count:");
		JTextField bclbltxt = new JTextField(10);
		JPanel bclblP = new JPanel();
		bclblP.add(bclbl);
		bclblP.add(bclbltxt);
		commonP.add(bclblP);
		JLabel bwlbl = new JLabel("Bin Width:");
		JTextField bwlbltxt = new JTextField(10);
		JPanel bwlblP = new JPanel();
		bwlblP.add(bwlbl);
		bwlblP.add(bwlbltxt);
		commonP.add(bwlblP);
		//ノルム
		JPanel normP = new JPanel();
		normP.setLayout(fl);
		JLabel wtlbl = new JLabel("Normalize method");
		JComboBox<String> norm = new JComboBox<>(norms);
		normP.add(wtlbl);
		normP.add(norm);
		commonP.add(normP);
		addBorder(commonP, Color.gray, "Common texture settings");
		textureParamsP.add(commonP);
		//GLCM
		JPanel glcm = new JPanel();
		glcm.setLayout(new GridLayout(5, 1));
		JCheckBox useGLCMBin = new JCheckBox("Use this bin for GLCM");
		glcm.add(useGLCMBin);
		JRadioButton glcmBinCountBtn = new JRadioButton("Bin Count");
		JRadioButton glcmBinWidthBtn = new JRadioButton("Bin Width");
		JPanel glcmBinPanel = new JPanel();
		glcmBinPanel.setLayout(fl);
		glcmBinPanel.add(glcmBinCountBtn);
		glcmBinPanel.add(glcmBinWidthBtn);
		glcm.add(glcmBinPanel);
		ButtonGroup binGroup_glcm = new ButtonGroup();
		binGroup_glcm.add(glcmBinCountBtn);
		binGroup_glcm.add(glcmBinWidthBtn);
		binGroup_glcm.setSelected(glcmBinCountBtn.getModel(), useBinCount);
		JLabel glcm_bclbl = new JLabel("Bin Count");
		JTextField glcm_bclbltxt = new JTextField(10);
		JPanel glcm_bclblP = new JPanel();
		glcm_bclblP.add(glcm_bclbl);
		glcm_bclblP.add(glcm_bclbltxt);
		glcm.add(glcm_bclblP);
		JLabel glcm_bwlbl = new JLabel("Bin Width");
		JTextField glcm_bwlbltxt = new JTextField(10);
		JPanel glcm_bwlblP = new JPanel();
		glcm_bwlblP.add(glcm_bwlbl);
		glcm_bwlblP.add(glcm_bwlbltxt);
		glcm.add(glcm_bwlblP);
		JPanel glcm_delta = new JPanel();
		glcm_delta.setLayout(fl);
		glcm_delta.add(new JLabel("delta:"));
		JTextField glcm_deltatxt = new JTextField(10);
		glcm_delta.add(glcm_deltatxt);
		glcm.add(glcm_delta);
		addBorder(glcm, Color.gray, "GLCM");
		textureParamsP.add(glcm);
		//glrlm
		JPanel glrlm = new JPanel();
		glrlm.setLayout(new GridLayout(4, 1));
		JCheckBox useGLRLMBin = new JCheckBox("Use this bin for GLRLM");
		glrlm.add(useGLRLMBin);
		JRadioButton glrlmBinCountBtn = new JRadioButton("Bin Count");
		JRadioButton glrlmBinWidthBtn = new JRadioButton("Bin Width");
		JPanel glrlmBinPanel = new JPanel();
		glrlmBinPanel.setLayout(fl);
		glrlmBinPanel.add(glrlmBinCountBtn);
		glrlmBinPanel.add(glrlmBinWidthBtn);
		glrlm.add(glrlmBinPanel);
		ButtonGroup binGroup_glrlm = new ButtonGroup();
		binGroup_glrlm.add(glrlmBinCountBtn);
		binGroup_glrlm.add(glrlmBinWidthBtn);
		binGroup_glrlm.setSelected(glrlmBinCountBtn.getModel(), useBinCount);
		JLabel glrlm_bclbl = new JLabel("Bin Count");
		JTextField glrlm_bclbltxt = new JTextField(10);
		JPanel glrlm_bclblP = new JPanel();
		glrlm_bclblP.add(glrlm_bclbl);
		glrlm_bclblP.add(glrlm_bclbltxt);
		glrlm.add(glrlm_bclblP);
		JLabel glrlm_bwlbl = new JLabel("Bin Width");
		JTextField glrlm_bwlbltxt = new JTextField(10);
		JPanel glrlm_bwlblP = new JPanel();
		glrlm_bwlblP.add(glrlm_bwlbl);
		glrlm_bwlblP.add(glrlm_bwlbltxt);
		glrlm.add(glrlm_bwlblP);
		addBorder(glrlm, Color.gray, "GLRLM");
		textureParamsP.add(glrlm);
		//GLSZM
		JPanel glszm = new JPanel();
		glszm.setLayout(new GridLayout(4, 1));
		JCheckBox useGLSZMBin = new JCheckBox("Use this bin for GLSZM");
		glszm.add(useGLSZMBin);
		JRadioButton glszmBinCountBtn = new JRadioButton("Bin Count");
		JRadioButton glszmBinWidthBtn = new JRadioButton("Bin Width");
		JPanel glszmBinPanel = new JPanel();
		glszmBinPanel.setLayout(fl);
		glszmBinPanel.add(glszmBinCountBtn);
		glszmBinPanel.add(glszmBinWidthBtn);
		glszm.add(glszmBinPanel);
		ButtonGroup binGroup_glszm = new ButtonGroup();
		binGroup_glszm.add(glszmBinCountBtn);
		binGroup_glszm.add(glszmBinWidthBtn);
		binGroup_glszm.setSelected(glszmBinCountBtn.getModel(), useBinCount);
		JLabel glszm_bclbl = new JLabel("Bin Count");
		JTextField glszm_bclbltxt = new JTextField(10);
		JPanel glszm_bclblP = new JPanel();
		glszm_bclblP.add(glszm_bclbl);
		glszm_bclblP.add(glszm_bclbltxt);
		glszm.add(glszm_bclblP);
		JLabel glszm_bwlbl = new JLabel("Bin Width");
		JTextField glszm_bwlbltxt = new JTextField(10);
		JPanel glszm_bwlblP = new JPanel();
		glszm_bwlblP.add(glszm_bwlbl);
		glszm_bwlblP.add(glszm_bwlbltxt);
		glszm.add(glszm_bwlblP);
		addBorder(glszm, Color.gray, "GLSZM");
		textureParamsP.add(glszm);
		//GLDZM
		JPanel gldzm = new JPanel();
		gldzm.setLayout(new GridLayout(4, 1));
		JCheckBox useGLDZMBin = new JCheckBox("Use this bin for GLDZM");
		gldzm.add(useGLDZMBin);
		JRadioButton gldzmBinCountBtn = new JRadioButton("Bin Count");
		JRadioButton gldzmBinWidthBtn = new JRadioButton("Bin Width");
		JPanel gldzmBinPanel = new JPanel();
		gldzmBinPanel.setLayout(fl);
		gldzmBinPanel.add(gldzmBinCountBtn);
		gldzmBinPanel.add(gldzmBinWidthBtn);
		gldzm.add(gldzmBinPanel);
		ButtonGroup binGroup_gldzm = new ButtonGroup();
		binGroup_gldzm.add(gldzmBinCountBtn);
		binGroup_gldzm.add(gldzmBinWidthBtn);
		binGroup_gldzm.setSelected(gldzmBinCountBtn.getModel(), useBinCount);
		JLabel gldzm_bclbl = new JLabel("Bin Count");
		JTextField gldzm_bclbltxt = new JTextField(10);
		JPanel gldzm_bclblP = new JPanel();
		gldzm_bclblP.add(gldzm_bclbl);
		gldzm_bclblP.add(gldzm_bclbltxt);
		gldzm.add(gldzm_bclblP);
		JLabel gldzm_bwlbl = new JLabel("Bin Width");
		JTextField gldzm_bwlbltxt = new JTextField(10);
		JPanel gldzm_bwlblP = new JPanel();
		gldzm_bwlblP.add(gldzm_bwlbl);
		gldzm_bwlblP.add(gldzm_bwlbltxt);
		gldzm.add(gldzm_bwlblP);
		addBorder(gldzm, Color.gray, "GLDZM");
		textureParamsP.add(gldzm);
		//NGTDM
		JPanel ngtdm = new JPanel();
		ngtdm.setLayout(new GridLayout(4, 1));
		JCheckBox useNGTDMBin = new JCheckBox("Use this bin for NGTDM");
		ngtdm.add(useNGTDMBin);
		JRadioButton ngtdmBinCountBtn = new JRadioButton("Bin Count");
		JRadioButton ngtdmBinWidthBtn = new JRadioButton("Bin Width");
		JPanel ngtdmBinPanel = new JPanel();
		ngtdmBinPanel.setLayout(fl);
		ngtdmBinPanel.add(ngtdmBinCountBtn);
		ngtdmBinPanel.add(ngtdmBinWidthBtn);
		ngtdm.add(ngtdmBinPanel);
		ButtonGroup binGroup_ngtdm = new ButtonGroup();
		binGroup_ngtdm.add(ngtdmBinCountBtn);
		binGroup_ngtdm.add(ngtdmBinWidthBtn);
		binGroup_ngtdm.setSelected(ngtdmBinCountBtn.getModel(), useBinCount);
		JLabel ngtdm_bclbl = new JLabel("Bin Count");
		JTextField ngtdm_bclbltxt = new JTextField(10);
		JPanel ngtdm_bclblP = new JPanel();
		ngtdm_bclblP.add(ngtdm_bclbl);
		ngtdm_bclblP.add(ngtdm_bclbltxt);
		ngtdm.add(ngtdm_bclblP);
		JLabel ngtdm_bwlbl = new JLabel("Bin Width");
		JTextField ngtdm_bwlbltxt = new JTextField(10);
		JPanel ngtdm_bwlblP = new JPanel();
		ngtdm_bwlblP.add(ngtdm_bwlbl);
		ngtdm_bwlblP.add(ngtdm_bwlbltxt);
		ngtdm.add(ngtdm_bwlblP);
		JPanel ngtdm_delta = new JPanel();
		ngtdm_delta.setLayout(fl);
		ngtdm_delta.add(new JLabel("delta:"));
		JTextField ngtdm_deltatxt = new JTextField(10);
		ngtdm_delta.add(ngtdm_deltatxt);
		ngtdm.add(ngtdm_delta);
		addBorder(ngtdm, Color.gray, "NGTDM");
		textureParamsP.add(ngtdm);
		//NGLDM
		JPanel ngldm = new JPanel();
		ngldm.setLayout(new GridLayout(6, 1));
		JCheckBox useNGLDMBin = new JCheckBox("Use this bin for NGLDM");
		ngldm.add(useNGLDMBin);
		JRadioButton ngldmBinCountBtn = new JRadioButton("Bin Count");
		JRadioButton ngldmBinWidthBtn = new JRadioButton("Bin Width");
		JPanel ngldmBinPanel = new JPanel();
		ngldmBinPanel.setLayout(fl);
		ngldmBinPanel.add(ngldmBinCountBtn);
		ngldmBinPanel.add(ngldmBinWidthBtn);
		ngldm.add(ngtdmBinPanel);
		ButtonGroup binGroup_ngldm = new ButtonGroup();
		binGroup_ngldm.add(ngldmBinCountBtn);
		binGroup_ngldm.add(ngldmBinWidthBtn);
		binGroup_ngldm.setSelected(ngldmBinCountBtn.getModel(), useBinCount);
		JLabel ngldm_bclbl = new JLabel("Bin Count");
		JTextField ngldm_bclbltxt = new JTextField(10);
		JPanel ngldm_bclblP = new JPanel();
		ngldm_bclblP.add(ngldm_bclbl);
		ngldm_bclblP.add(ngldm_bclbltxt);
		ngldm.add(ngldm_bclblP);
		JLabel ngldm_bwlbl = new JLabel("Bin Width");
		JTextField ngldm_bwlbltxt = new JTextField(10);
		JPanel ngldm_bwlblP = new JPanel();
		ngldm_bwlblP.add(ngldm_bwlbl);
		ngldm_bwlblP.add(ngldm_bwlbltxt);
		ngldm.add(ngldm_bwlblP);
		JPanel ngldm_alpha = new JPanel();
		ngldm_alpha.setLayout(fl);
		ngldm_alpha.add(new JLabel("alpha:"));
		JTextField ngldm_alphatxt = new JTextField(10);
		ngldm_alpha.add(ngldm_alphatxt);
		ngldm.add(ngldm_alpha);
		JPanel ngldm_delta = new JPanel();
		ngldm_delta.setLayout(fl);
		ngldm_delta.add(new JLabel("delta:"));
		JTextField ngldm_deltatxt = new JTextField(10);
		ngldm_delta.add(ngldm_deltatxt);
		ngldm.add(ngldm_delta);
		addBorder(ngldm, Color.gray, "NGLDM");
		textureParamsP.add(ngldm);
		
		//Intensity family param
		JPanel intensP = new JPanel(new GridLayout(1/*increment if you want to add panel*/, 1));
		JScrollPane spIntens = new JScrollPane(intensP);
		tp.addTab("Intensity family param", spIntens);
		JPanel ivh = new JPanel(new GridLayout(4, 1));
		JCheckBox useIVHBin = new JCheckBox("Use this bin for IVH");
		ivh.add(useIVHBin);
		JRadioButton ivhBinCountBtn = new JRadioButton("Bin Count");
		JRadioButton ivhBinWidthBtn = new JRadioButton("Bin Width");
		JPanel ivhBinPanel = new JPanel();
		ivhBinPanel.setLayout(fl);
		ivhBinPanel.add(ivhBinCountBtn);
		ivhBinPanel.add(ivhBinWidthBtn);
		ivh.add(ivhBinPanel);
		ButtonGroup binGroup_ivh = new ButtonGroup();
		binGroup_ivh.add(ivhBinCountBtn);
		binGroup_ivh.add(ivhBinWidthBtn);
		binGroup_ivh.setSelected(ivhBinCountBtn.getModel(), useBinCount);
		JLabel ivh_bclbl = new JLabel("Bin Count");
		JTextField ivh_bclbltxt = new JTextField(10);
		JPanel ivh_bclblP = new JPanel();
		ivh_bclblP.add(ivh_bclbl);
		ivh_bclblP.add(ivh_bclbltxt);
		ivh.add(ivh_bclblP);
		JLabel ivh_bwlbl = new JLabel("Bin Width");
		JTextField ivh_bwlbltxt = new JTextField(10);
		JPanel ivh_bwlblP = new JPanel();
		ivh_bwlblP.add(ivh_bwlbl);
		ivh_bwlblP.add(ivh_bwlbltxt);
		ivh.add(ivh_bwlblP);
		addBorder(ivh, Color.gray, "IVH");
		intensP.add(ivh);
		
		//Fractal param
		JPanel fracP = new JPanel(new GridLayout(1/*increment num of items*/, 1));
		JScrollPane spFrac = new JScrollPane(fracP);
		tp.addTab("Fractal family param", spFrac);
		JPanel frac = new JPanel(new GridLayout(3, 1));
		JCheckBox useThisSizes = new JCheckBox("Use this size of boxes");
		JPanel boxP = new JPanel();
		JLabel boxlbl = new JLabel("box sizes:");
		JTextField sizetxt = new JTextField(25);
		sizetxt.setText(boxSizes);
		sizetxt.setToolTipText("Default values :2,3,4,6,8,12,16,32,64");
		boxP.add(boxlbl);
		boxP.add(sizetxt);
		frac.add(useThisSizes);
		frac.add(boxP);
		addBorder(frac,Color.gray, "Box counting");
		fracP.add(frac);
		return tp;
	}
	
	private void addBorder(JComponent p, Color c, String name) {
		Border b = BorderFactory.createBevelBorder(BevelBorder.RAISED, c, Color.DARK_GRAY);
		p.setBorder(BorderFactory.createTitledBorder(b, name, TitledBorder.LEFT, TitledBorder.DEFAULT_POSITION));
	}
	
	public void moveToCalc(List<String> names) {
		
	}
	
	public void moveToExclusion(List<String> names) {
		
	}
	
	public boolean validateCalcAndExclud() {
		return false;
	}
	
	public void addList(List<String> names, DefaultListModel<String> listModel) {
		for (String n : names) {
			addList(n, listModel);
		}
	}
	
	public void addList(String name, DefaultListModel<String> listModel) {
		if (listModel.contains(name)) {
			System.out.println(name + " is already listed.");
			return;
		}
		listModel.add(listModel.getSize(), name);
		if(listModel == targetListModel) {
			deleteFromList(name, exclusionListModel);
		}else if(listModel == exclusionListModel){
			deleteFromList(name,targetListModel);
		}
		updateCount();
		target.repaint();
		exclusion.repaint();
	}
	
	public void deleteFromList(List<String> names, DefaultListModel<String> listModel) {
		for (String n : names) {
			deleteFromList(n, listModel);
		}
	}
	
	public void deleteFromList(String name, DefaultListModel<String> listModel) {
		int pos = listModel.indexOf(name);
		if(pos >= 0) {
			listModel.remove(listModel.indexOf(name));
			if(listModel == targetListModel) {
				addList(name,exclusionListModel);
			}else if(listModel == exclusionListModel){
				addList(name,targetListModel);
			}
			updateCount();
		}
		target.repaint();
		exclusion.repaint();
	}
	
	private void updateCount() {
		if(targetCount != null) {
			targetCount.setText(targetListModel.getSize()+"/"+numOfTotalFeatures);
			targetCount.repaint();
		}
		if(exclusionCount != null) {
			exclusionCount.setText(exclusionListModel.getSize()+"/"+numOfTotalFeatures);
			exclusionCount.repaint();
		}
	}
	
	/**
	 * Operational/Diagnostics are excluded.
	 * @return feature names
	 */
	public List<String> featureNames(){
//		HashSet<String> names = new HashSet<>();//cannot keep adding order.
		List<String> names = new ArrayList<>();
		for(MorphologicalFeatureType f : MorphologicalFeatureType.values()) {
			names.add("Morpho_"+f.name());
		}
		for(LocalIntensityFeatureType f : LocalIntensityFeatureType.values()) {
			names.add("LocalInt_"+f.name());
		}
		for(IntensityBasedStatisticalFeatureType f : IntensityBasedStatisticalFeatureType.values()) {
			names.add("Stat_"+f.name());
		}
		for(IntensityHistogramFeatureType f : IntensityHistogramFeatureType.values()) {
			names.add("Hist_"+f.name());
		}
		for(IntensityVolumeHistogramFeatureType f : IntensityVolumeHistogramFeatureType.values()) {
			names.add("IVH_"+f.name());
		}
		for(GLCMFeatureType f : GLCMFeatureType.values()) {
			names.add("GLCM_"+f.name());
		}
		for(GLRLMFeatureType f : GLRLMFeatureType.values()) {
			names.add("GLRLM_"+f.name());
		}
		for(GLSZMFeatureType f : GLSZMFeatureType.values()) {
			names.add("GLSZM_"+f.name());
		}
		for(GLDZMFeatureType f : GLDZMFeatureType.values()) {
			names.add("GLDZM_"+f.name());
		}
		for(NGTDMFeatureType f : NGTDMFeatureType.values()) {
			names.add("NGTDM_"+f.name());
		}
		for(NGLDMFeatureType f : NGLDMFeatureType.values()) {
			names.add("NGLDM_"+f.name());
		}
		for(FractalFeatureType f : FractalFeatureType.values()) {
			names.add("Fractal_"+f.name());
		}
		/*
		 * calculate only force2D set true.
		 */
		for(Shape2DFeatureType f : Shape2DFeatureType.values()) {
			names.add("Shape2D_"+f.name());
		}
		return names;
	}
	
	public void adjustDividerLocation() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// JSplitPane がサイズを持ってから divider location を設定
				// この時点では sp と sp2 のサイズが0より大きいことが期待される
				if (sp1.getWidth() > 0 && sp1.getHeight() > 0) {
					sp1.setDividerLocation(0.4);
				}
				if (sp2.getWidth() > 0 && sp2.getHeight() > 0) {
					sp2.setDividerLocation(0.6);
				}
			}
		});
	}

}
