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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.NumberFormatter;

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

@SuppressWarnings("serial")
public class RadiomicsSettings extends JPanel{
	
	/**
	 * default settings
	 */
	// 2d/3d switch, when turn on, images will calculate slice by slice
	// currently, 2d-basis is set to default.
	boolean d3_basis = false;
	//label
	final int defaultLabel = 1;
	//remove outlier
	final boolean removeOutliers = true;
	final int sigma = 3;
	//range filtering
	final boolean rangeFilter = false;
	//resampling
	final boolean resample = false;
	//discretization, count or width
	final int defaultBinCount = 16;
	final int defaultDelta = 1;
	final int defaultAlpha = 1;
	final boolean useBinCount = true;
	final String boxSizes = "2,3,4,6,8,12,16,32,64";
	/**
	 * Manhattan: same as "no_weight", M_ij/np.sum(M_ij)
	 * Euclidean: M_ij/np.sqrt(np.sum(M_ij**2))
	 * Infinity: M_ij/np.max(M_ij)
	 */
	final String[] norms = new String[] {"manhattan", "euclidean", "infinity"};
	
	/**
	 * components
	 */
	JFormattedTextField lbltxt;//mask label
	JCheckBox roChk;//remove outliers
	JFormattedTextField roSigma;
	JCheckBox rfChk;
	JFormattedTextField rfMin;
	JFormattedTextField rfMax;
	JCheckBox resampleChk;
	JFormattedTextField resampleX;
	JFormattedTextField resampleY;
	JFormattedTextField resampleZ;
	
	/**
	 * calculation target, select all.
	 */
	JCheckBox operationalChk;
	JCheckBox diagnosticsChk;
	JCheckBox morphologicalChk;
	JCheckBox localIntensChk;
	JCheckBox intensityStatsChk;
	JCheckBox histogramChk;
	JCheckBox volumeHistChk;
	JCheckBox glcmChk;
	JCheckBox glrlmChk;
	JCheckBox glszmChk;
	JCheckBox gldzmChk;
	JCheckBox ngtdmChk;
	JCheckBox ngldmChk;
	JCheckBox fractalChk;
	JCheckBox shape2dChk;
	
	List<String> featureNames;
	final int numOfTotalFeatures;
	
	/**
	 * Operational information
	 */
	final String OPERATIONAL = SettingsContext.OPERATIONAL;
	final String DIAGNOSTICS = SettingsContext.DIAGNOSTICS;
	
	/**
	 * Feature family name
	 */
	static final String MORPHOLOGICAL = SettingsContext.MORPHOLOGICAL;
	static final String LOCALINTENSITY = SettingsContext.LOCALINTENSITY;
	static final String INTENSITYSTATS = SettingsContext.INTENSITYSTATS;
	static final String INTENSITYHISTOGRAM = SettingsContext.INTENSITYHISTOGRAM;
	static final String INTENSITYVOLUMEHISTOGRAM = SettingsContext.INTENSITYVOLUMEHISTOGRAM;
	
	static final String morpShort = "Morpho";
	static final String liShort = "LocalInt";
	static final String statShort = "Stats";
	static final String histShort = "Hist";
	static final String ivhShort = "IVH";
	
	static final String GLCM = SettingsContext.GLCM;
	static final String GLRLM = SettingsContext.GLRLM;
	static final String GLSZM = SettingsContext.GLSZM;
	static final String GLDZM = SettingsContext.GLDZM;
	static final String NGTDM = SettingsContext.NGTDM;
	static final String NGLDM = SettingsContext.NGLDM;
	static final String FRACTAL = SettingsContext.FRACTAL;
	static final String SHAPE2D = SettingsContext.SHAPE2D;
	
	//calculation target and exclusion features
	DefaultListModel<String> targetListModel = new DefaultListModel<>();
	DefaultListModel<String> exclusionListModel = new DefaultListModel<>();
	JList<String> target;
	JList<String> exclusion;
	HashSet<String> defaultExclusions;
	JButton addDefaultExclusionBtn;
	
	JSplitPane sp1;
	JSplitPane sp2;
	JLabel targetCount;
	JLabel exclusionCount;
	
	/**
	 * Feature param components
	 */
	ButtonGroup binGroup_glcm;
	BinCountSettings bcs_glcm;
	BinWidthSettings bws_glcm;
	AlphaDeltaSettings delta_glcm;
//	NormComboPanel norm_glcm;
	
	ButtonGroup binGroup_glrlm;
	BinCountSettings bcs_glrlm;
	BinWidthSettings bws_glrlm;
//	NormComboPanel norm_glrlm;
	
	ButtonGroup binGroup_glszm;
	BinCountSettings bcs_glszm;
	BinWidthSettings bws_glszm;
//	NormComboPanel norm_glszm;
	
	ButtonGroup binGroup_gldzm;
	BinCountSettings bcs_gldzm;
	BinWidthSettings bws_gldzm;
//	NormComboPanel norm_gldzm;
	
	ButtonGroup binGroup_ngtdm;
	BinCountSettings bcs_ngtdm;
	BinWidthSettings bws_ngtdm;
	AlphaDeltaSettings delta_ngtdm;
//	NormComboPanel norm_ngtdm;
	
	ButtonGroup binGroup_ngldm;
	BinCountSettings bcs_ngldm;
	BinWidthSettings bws_ngldm;
	AlphaDeltaSettings alpha_ngldm;
	AlphaDeltaSettings delta_ngldm;
//	NormComboPanel norm_ngldm;
	
	ButtonGroup binGroup_hist;
	BinCountSettings bcs_hist;
	BinWidthSettings bws_hist;
	
	ButtonGroup binGroup_ivh;
	JRadioButton useOrg_ivh;
	BinCountSettings bcs_ivh;
	BinWidthSettings bws_ivh;
	
	JTextField boxsize_fractal;
	
	public RadiomicsSettings() {
		featureNames = featureNames(null);//load all features
		numOfTotalFeatures = featureNames.size();
		buildGUI();
	}
	
	private void buildGUI() {
		setLayout(new BorderLayout());
		JComponent common = buildMaskSettingsPanel();
		JComponent features = buildFeaturesPanel();
		sp1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		sp1.add(common,JSplitPane.LEFT);
		sp1.add(features,JSplitPane.RIGHT);
		sp1.setPreferredSize(new Dimension(800,400));
		JTabbedPane parameters = new JTabbedPane();
		JScrollPane textureParam = buildTextureParam();
		JScrollPane intensParam = buildIntensityFamilyParam();
		JScrollPane fracParam = buildFractalParam();
		parameters.addTab("Texture family prams", textureParam);
		parameters.addTab("Intensity family param", intensParam);
		parameters.addTab("Fractal family param", fracParam);
		sp2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		sp2.add(sp1,JSplitPane.TOP);
		sp2.add(parameters,JSplitPane.BOTTOM);
		sp2.setPreferredSize(new Dimension(800,200));
		add(sp2, BorderLayout.CENTER);
	}
	
	private JComponent buildMaskSettingsPanel() {
		
		JPanel base = new JPanel();
		base.setLayout(new BorderLayout());
		
		FlowLayout fl = new FlowLayout();
		fl.setAlignment(FlowLayout.LEFT);
		
		JPanel dimPanel = new JPanel();
		addBorder(dimPanel, Color.white, "Computational Dimension");
		//3d/2d
		JRadioButton d2Btn = new JRadioButton("2D basis");
		d2Btn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(d2Btn.isSelected()) {
					d3_basis = false;
					switch2D3D(d3_basis/*is3D*/);
				}
			}
		});
		JRadioButton d3Btn = new JRadioButton("3D basis");
		d3Btn.setEnabled(false);//202507, calculation time is too long...
		d3Btn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(d3Btn.isSelected()) {
					d3_basis = true;
					switch2D3D(d3_basis/*is3D*/);
				}
			}
		});
		dimPanel.add(d2Btn);
		dimPanel.add(d3Btn);
		base.add(dimPanel, BorderLayout.NORTH);
		ButtonGroup dimGroup = new ButtonGroup();
		dimGroup.add(d2Btn);
		dimGroup.add(d3Btn);
//		dimGroup.setSelected(d3Btn.getModel(), d3_basis);
		dimGroup.setSelected(d2Btn.getModel(), true);
		
		JPanel maskSettings = new JPanel(new GridLayout(10, 1));
		addBorder(maskSettings, Color.white, "Mask Settings");
		//label
		JLabel lbl = new JLabel("Label value:");
		lbltxt = formattedTextField(false, 5);
		lbltxt.setValue(defaultLabel);
		lbltxt.setToolTipText("1 ~ 255");
		lbltxt.setHorizontalAlignment(JTextField.RIGHT);
		JPanel lblP = new JPanel();
		lblP.setLayout(fl);
		lblP.add(lbl);
		lblP.add(lbltxt);
		maskSettings.add(lblP);
		//removeOutliers outliers
		roChk = new JCheckBox("Remove Outliers");
		roChk.setSelected(removeOutliers);
		roChk.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(roSigma != null) {
					if(roChk.isSelected()) {
						roSigma.setEnabled(true);
					}else {
						roSigma.setEnabled(false);
					}
				}
			}
		});
		maskSettings.add(roChk);
		JLabel rolbl = new JLabel("Sigma:");
		roSigma = formattedTextField(false, 5);
		roSigma.setValue(sigma);
		roSigma.setEnabled(removeOutliers);
		JPanel roP = new JPanel();
		roP.add(rolbl);
		roP.add(roSigma);
		maskSettings.add(roP);
		
		//range filtering
		rfChk = new JCheckBox("Range Filtering");
		rfChk.setSelected(rangeFilter);
		rfChk.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(rfMin != null && rfMax != null) {
					if(rfChk.isSelected()) {
						rfMin.setEnabled(true);
						rfMax.setEnabled(true);
					}else {
						rfMin.setEnabled(false);
						rfMax.setEnabled(false);
					}
				}
			}
		});
		maskSettings.add(rfChk);
		JLabel rfMinlbl = new JLabel("min:");
		JLabel rfMaxlbl = new JLabel("max:");
		rfMin = formattedTextField(true, 10);
		rfMax = formattedTextField(true, 10);
		rfMin.setEnabled(rangeFilter);
		rfMax.setEnabled(rangeFilter);
		JPanel rfMinP = new JPanel();
		JPanel rfMaxP = new JPanel();
		rfMinP.add(rfMinlbl);
		rfMinP.add(rfMin);
		rfMaxP.add(rfMaxlbl);
		rfMaxP.add(rfMax);
		maskSettings.add(rfMinP);
		maskSettings.add(rfMaxP);
		//resampling
		resampleChk = new JCheckBox("Resampling");
		resampleChk.setSelected(resample);
		resampleChk.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(resampleX != null && resampleY != null && resampleZ != null) {
					if(resampleChk.isSelected()) {
						resampleX.setEnabled(true);
						resampleY.setEnabled(true);
						resampleZ.setEnabled(true);
					}else {
						resampleX.setEnabled(false);
						resampleY.setEnabled(false);
						resampleZ.setEnabled(false);
					}
				}
			}
		});
		maskSettings.add(resampleChk);
		JLabel reXlbl = new JLabel("vx:");
		JLabel reYlbl = new JLabel("vy:");
		JLabel reZlbl = new JLabel("vz:");
		resampleX = formattedTextField(true, 10);
		resampleY = formattedTextField(true, 10);
		resampleZ = formattedTextField(true, 10);
		resampleX.setEnabled(resample);
		resampleY.setEnabled(resample);
		resampleZ.setEnabled(resample);
		JPanel reXP = new JPanel();
		JPanel reYP = new JPanel();
		JPanel reZP = new JPanel();
		reXP.add(reXlbl);
		reXP.add(resampleX);
		reYP.add(reYlbl);
		reYP.add(resampleY);
		reZP.add(reZlbl);
		reZP.add(resampleZ);
		maskSettings.add(reXP);
		maskSettings.add(reYP);
		maskSettings.add(reZP);
		
		base.add(maskSettings, BorderLayout.CENTER);
		
		JScrollPane sPane = new JScrollPane(base);
		return sPane;
	}
	
	private JComponent buildFeaturesPanel() {
		JPanel infoGroupChkP = new JPanel(new GridLayout(1,4));
		operationalChk = new JCheckBox(OPERATIONAL);
		operationalChk.setSelected(true);
		diagnosticsChk = new JCheckBox(DIAGNOSTICS);
		diagnosticsChk.setSelected(true);
		infoGroupChkP.add(operationalChk);
		infoGroupChkP.add(diagnosticsChk);
		addBorder(infoGroupChkP, Color.gray, "Info");
		
		JPanel featuresGroupChkP = new JPanel(new GridLayout(4,4));
		morphologicalChk = new JCheckBox(MORPHOLOGICAL);
		
		localIntensChk = new JCheckBox(LOCALINTENSITY);
		localIntensChk.setSelected(true);
		intensityStatsChk = new JCheckBox(INTENSITYSTATS);
		intensityStatsChk.setSelected(true);
		histogramChk = new JCheckBox(INTENSITYHISTOGRAM);
		histogramChk.setSelected(true);
		volumeHistChk = new JCheckBox(INTENSITYVOLUMEHISTOGRAM);
		volumeHistChk.setSelected(true);
		glcmChk = new JCheckBox(GLCM);
		glcmChk.setSelected(true);
		glrlmChk = new JCheckBox(GLRLM);
		glrlmChk.setSelected(true);
		glszmChk = new JCheckBox(GLSZM);
		glszmChk.setSelected(true);
		gldzmChk = new JCheckBox(GLDZM);
		gldzmChk.setSelected(true);
		ngtdmChk = new JCheckBox(NGTDM);
		ngtdmChk.setSelected(true);
		ngldmChk = new JCheckBox(NGLDM);
		ngldmChk.setSelected(true);
		fractalChk = new JCheckBox(FRACTAL);
		fractalChk.setSelected(true);
		shape2dChk = new JCheckBox(SHAPE2D);
		//add action
		addFeatureGroupCheckBoxAction(morphologicalChk);
		addFeatureGroupCheckBoxAction(localIntensChk);
		addFeatureGroupCheckBoxAction(intensityStatsChk);
		addFeatureGroupCheckBoxAction(histogramChk);
		addFeatureGroupCheckBoxAction(volumeHistChk);
		addFeatureGroupCheckBoxAction(glcmChk);
		addFeatureGroupCheckBoxAction(glrlmChk);
		addFeatureGroupCheckBoxAction(glszmChk);
		addFeatureGroupCheckBoxAction(gldzmChk);
		addFeatureGroupCheckBoxAction(ngtdmChk);
		addFeatureGroupCheckBoxAction(ngldmChk);
		addFeatureGroupCheckBoxAction(fractalChk);
		addFeatureGroupCheckBoxAction(shape2dChk);
		featuresGroupChkP.add(morphologicalChk);
		featuresGroupChkP.add(localIntensChk);
		featuresGroupChkP.add(intensityStatsChk);
		featuresGroupChkP.add(histogramChk);
		featuresGroupChkP.add(volumeHistChk);
		featuresGroupChkP.add(glcmChk);
		featuresGroupChkP.add(glrlmChk);
		featuresGroupChkP.add(glszmChk);
		featuresGroupChkP.add(gldzmChk);
		featuresGroupChkP.add(ngtdmChk);
		featuresGroupChkP.add(ngldmChk);
		featuresGroupChkP.add(fractalChk);
		featuresGroupChkP.add(shape2dChk);
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
		/**
		 * 現状、各特徴量の名前が一意になっていないので.
		 */
//		HashSet<String> defaultExclusions = radiomics.getExcludedFeatures();
		defaultExclusions = new HashSet<>();
		/*
		 * MorphologicalFeatureType.VolumeDensity_OrientedMinimumBoundingBox.name(),
		 * MorphologicalFeatureType.AreaDensity_OrientedMinimumBoundingBox.name(),
		 * MorphologicalFeatureType.VolumeDensity_MinimumVolumeEnclosingEllipsoid.name(),
		 * MorphologicalFeatureType.AreaDensity_MinimumVolumeEnclosingEllipsoid.name(),
		 * IntensityVolumeHistogramFeatureType.AreaUnderTheIVHCurve.name(),
		 * NGLDMFeatureType.DependenceCountPercentage.name(),
		 */
		defaultExclusions.add(morpShort+"_"+MorphologicalFeatureType.VolumeDensity_OrientedMinimumBoundingBox.name());
		defaultExclusions.add(morpShort+"_"+MorphologicalFeatureType.AreaDensity_OrientedMinimumBoundingBox.name());
		defaultExclusions.add(morpShort+"_"+MorphologicalFeatureType.VolumeDensity_MinimumVolumeEnclosingEllipsoid.name());
		defaultExclusions.add(morpShort+"_"+MorphologicalFeatureType.AreaDensity_MinimumVolumeEnclosingEllipsoid.name());
		defaultExclusions.add(ivhShort+"_"+IntensityVolumeHistogramFeatureType.AreaUnderTheIVHCurve.name());
		defaultExclusions.add(NGLDM+"_"+NGLDMFeatureType.DependenceCountPercentage.name());
		
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
		
		JPanel resetDefaultExclusionsP = new JPanel();
		resetDefaultExclusionsP.setLayout(new FlowLayout(FlowLayout.LEFT));
		resetDefaultExclusionsP.add(exclusionCount);
		addDefaultExclusionBtn = new JButton("Add default exclusions");
		addDefaultExclusionBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(exclusionListModel != null) {
					deleteFromList(new ArrayList<>(defaultExclusions), targetListModel);
				}
			}
		});
		resetDefaultExclusionsP.add(addDefaultExclusionBtn);
		right.add(resetDefaultExclusionsP, BorderLayout.NORTH);
		
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
					Arrays.sort(selected);
					for (int i = selected.length - 1; i >= 0; i--) {
						int indexToDelete = selected[i];
						String n = targetListModel.get(indexToDelete);
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
					Arrays.sort(selected);
					for (int i = selected.length - 1; i >= 0; i--) {
						int indexToDelete = selected[i];
						String n = exclusionListModel.get(indexToDelete);
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
		
		/*
		 * when starting-up, initialize with the selection state reversed, and then use
		 * doClick() in switch2D3D() to reverse the selection state so that it works correctly.
		 */
		morphologicalChk.setSelected(!d3_basis);
		shape2dChk.setSelected(d3_basis);
		switch2D3D(d3_basis);
		
		return sp;
	}
	
	/**
	 * 
	 * @param chk
	 */
	private void addFeatureGroupCheckBoxAction(JCheckBox chk) {
		String name = chk.getText();
		if(name == null || name.equals("")) {
			return;
		}
		if(targetListModel == null || exclusionListModel == null) {
			return;
		}
		switch(name) {
			case MORPHOLOGICAL:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(MORPHOLOGICAL), targetListModel);
						}else {
							addList(featureNames(MORPHOLOGICAL), exclusionListModel);
						}
					}
				});
				break;
			case LOCALINTENSITY:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(LOCALINTENSITY), targetListModel);
						}else {
							addList(featureNames(LOCALINTENSITY), exclusionListModel);
						}
					}
				});
				break;
			case INTENSITYSTATS:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(INTENSITYSTATS), targetListModel);
						}else {
							addList(featureNames(INTENSITYSTATS), exclusionListModel);
						}
					}
				});
				break;
			case INTENSITYHISTOGRAM:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(INTENSITYHISTOGRAM), targetListModel);
						}else {
							addList(featureNames(INTENSITYHISTOGRAM), exclusionListModel);
						}
					}
				});
				break;
			case INTENSITYVOLUMEHISTOGRAM:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(INTENSITYVOLUMEHISTOGRAM), targetListModel);
						}else {
							addList(featureNames(INTENSITYVOLUMEHISTOGRAM), exclusionListModel);
						}
					}
				});
				break;
			case GLCM:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(GLCM), targetListModel);
						}else {
							addList(featureNames(GLCM), exclusionListModel);
						}
					}
				});
				break;
			case GLRLM:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(GLRLM), targetListModel);
						}else {
							addList(featureNames(GLRLM), exclusionListModel);
						}
					}
				});
				break;
			case GLSZM:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(GLSZM), targetListModel);
						}else {
							addList(featureNames(GLSZM), exclusionListModel);
						}
					}
				});
				break;
			case GLDZM:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(GLDZM), targetListModel);
						}else {
							addList(featureNames(GLDZM), exclusionListModel);
						}
					}
				});
				break;
			case NGTDM:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(NGTDM), targetListModel);
						}else {
							addList(featureNames(NGTDM), exclusionListModel);
						}
					}
				});
				break;
			case NGLDM:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(NGLDM), targetListModel);
						}else {
							addList(featureNames(NGLDM), exclusionListModel);
						}
					}
				});
				break;
			case FRACTAL:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(FRACTAL), targetListModel);
						}else {
							addList(featureNames(FRACTAL), exclusionListModel);
						}
					}
				});
				break;
			case SHAPE2D:
				chk.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if(chk.isSelected()) {
							addList(featureNames(SHAPE2D), targetListModel);
						}else {
							addList(featureNames(SHAPE2D), exclusionListModel);
						}
					}
				});
				break;
			default:
				//do nothing
		}
	}
	
	/**
	 * texture param tab
	 */
	private JScrollPane buildTextureParam() {
		/**
		 * texture param tab
		 */
		JPanel textureParamsP = new JPanel();
		//GLCM, RLM, SZM, DZM, NGTDM, NGLDM
		textureParamsP.setLayout(new GridLayout(6/*family group*/, 1));
		JScrollPane texturesS = new JScrollPane(textureParamsP);
		texturesS.setPreferredSize(new Dimension(400, 300));
		
		FlowLayout fl = new FlowLayout();
		fl.setAlignment(FlowLayout.LEFT);
		//bin count, bin width, alpha, beta
		GridLayout itemGridLayout = new GridLayout(4/*max row size*/, 1); 
		
		/**
		 * GLCM
		 */
		JPanel glcm = new JPanel();
		glcm.setLayout(itemGridLayout);
		bcs_glcm = new BinCountSettings(defaultBinCount);
		bws_glcm = new BinWidthSettings(Double.NaN);
		binGroup_glcm = setButtonGroup(bcs_glcm, bws_glcm, useBinCount, GLCM);
		delta_glcm = new AlphaDeltaSettings("delta", defaultDelta);
//		norm_glcm = new NormComboPanel(norms[0]);
		glcm.add(bcs_glcm);
		glcm.add(bws_glcm);
		glcm.add(delta_glcm);
//		glcm.add(norm_glcm);
		insertBlankPanel(glcm, 1);
		addBorder(glcm, Color.gray, "GLCM");
		textureParamsP.add(glcm);
		/**
		 * GLRLM
		 */
		JPanel glrlm = new JPanel();
		glrlm.setLayout(itemGridLayout);
		bcs_glrlm = new BinCountSettings(defaultBinCount);
		bws_glrlm = new BinWidthSettings(Double.NaN);
		binGroup_glrlm = setButtonGroup(bcs_glrlm, bws_glrlm, useBinCount, GLRLM);
//		norm_glrlm = new NormComboPanel(norms[0]);
		glrlm.add(bcs_glrlm);
		glrlm.add(bws_glrlm);
//		glrlm.add(norm_glrlm);
		insertBlankPanel(glrlm, 2);
		addBorder(glrlm, Color.gray, "GLRLM");
		textureParamsP.add(glrlm);
		/**
		 * GLSZM
		 */
		JPanel glszm = new JPanel();
		glszm.setLayout(itemGridLayout);
		bcs_glszm = new BinCountSettings(defaultBinCount);
		bws_glszm = new BinWidthSettings(Double.NaN);
		binGroup_glszm = setButtonGroup(bcs_glszm, bws_glszm, useBinCount, GLSZM);
//		norm_glszm = new NormComboPanel(norms[0]);
		glszm.add(bcs_glszm);
		glszm.add(bws_glszm);
//		glszm.add(norm_glszm);
		insertBlankPanel(glszm, 2);
		addBorder(glszm, Color.gray, "GLSZM");
		textureParamsP.add(glszm);
		/**
		 * GLDZM
		 */
		JPanel gldzm = new JPanel();
		gldzm.setLayout(itemGridLayout);
		bcs_gldzm = new BinCountSettings(defaultBinCount);
		bws_gldzm = new BinWidthSettings(Double.NaN);
		binGroup_gldzm = setButtonGroup(bcs_gldzm, bws_gldzm, useBinCount, GLDZM);
//		norm_gldzm = new NormComboPanel(norms[0]);
		gldzm.add(bcs_gldzm);
		gldzm.add(bws_gldzm);
//		gldzm.add(norm_gldzm);
		insertBlankPanel(gldzm, 2);
		addBorder(gldzm, Color.gray, "GLDZM");
		textureParamsP.add(gldzm);
		/**
		 * NGTDM
		 */
		JPanel ngtdm = new JPanel();
		ngtdm.setLayout(itemGridLayout);
		bcs_ngtdm = new BinCountSettings(defaultBinCount);
		bws_ngtdm = new BinWidthSettings(Double.NaN);
		binGroup_ngtdm = setButtonGroup(bcs_ngtdm, bws_ngtdm, useBinCount, NGTDM);
		delta_ngtdm = new AlphaDeltaSettings("delta", defaultDelta);
//		norm_ngtdm = new NormComboPanel(norms[0]);
		ngtdm.add(bcs_ngtdm);
		ngtdm.add(bws_ngtdm);
		ngtdm.add(delta_ngtdm);
//		ngtdm.add(norm_ngtdm);
		insertBlankPanel(ngtdm, 1);
		addBorder(ngtdm, Color.gray, "NGTDM");
		textureParamsP.add(ngtdm);
		/**
		 * NGLDM
		 */
		JPanel ngldm = new JPanel();
		ngldm.setLayout(itemGridLayout);
		bcs_ngldm = new BinCountSettings(defaultBinCount);
		bws_ngldm = new BinWidthSettings(Double.NaN);
		binGroup_ngldm = setButtonGroup(bcs_ngldm, bws_ngldm, useBinCount, NGLDM);
		alpha_ngldm = new AlphaDeltaSettings("alpha", defaultAlpha);
		delta_ngldm = new AlphaDeltaSettings("delta", defaultDelta);
//		norm_ngldm = new NormComboPanel(norms[0]);
		ngldm.add(bcs_ngldm);
		ngldm.add(bws_ngldm);
		ngldm.add(alpha_ngldm);
		ngldm.add(delta_ngldm);
//		ngldm.add(norm_ngldm);
		addBorder(ngldm, Color.gray, "NGLDM");
		textureParamsP.add(ngldm);
		
		return texturesS;
	}
	
	private JScrollPane buildIntensityFamilyParam() {
		/**
		 * Intensity family param TAB
		 */
		JPanel intensP = new JPanel(new GridLayout(2/*increment if you want to add panel*/, 1));
		JScrollPane spIntens = new JScrollPane(intensP);
		
		/**
		 * IntensityHistogram
		 */
		JPanel hist = new JPanel(new GridLayout(3,1));
		bcs_hist = new BinCountSettings(defaultBinCount);
		bws_hist = new BinWidthSettings(Double.NaN);
		binGroup_hist = setButtonGroup(bcs_hist, bws_hist, useBinCount, histShort);
		hist.add(bcs_hist);
		hist.add(bws_hist);
		insertBlankPanel(hist, 1);
		addBorder(hist, Color.gray, "IntensityHistogram");
		intensP.add(hist);
		/**
		 * IntensityVolumeHistgram
		 */
		JPanel ivh = new JPanel(new GridLayout(3,1));
		bcs_ivh = new BinCountSettings(defaultBinCount);
		bws_ivh = new BinWidthSettings(Double.NaN);
		useOrg_ivh = new JRadioButton("Use As-Is");
		useOrg_ivh.setActionCommand(SettingsContext.UseOriginalIVH);
		binGroup_ivh = setButtonGroup(bcs_ivh, bws_ivh, useBinCount, ivhShort);
		binGroup_ivh.add(useOrg_ivh);
		
		JPanel useOrgP = new JPanel(new FlowLayout(FlowLayout.LEFT));
		useOrgP.add(useOrg_ivh);
		
		ivh.add(bcs_ivh);
		ivh.add(bws_ivh);
		ivh.add(useOrgP);//adjust left spacing
//		insertBlankPanel(ivh, 1);
		addBorder(ivh, Color.gray, "IVH");
		intensP.add(ivh);
		return spIntens;
	}
	
	private JScrollPane buildFractalParam() {
		// Fractal param
		JPanel fracP = new JPanel(new GridLayout(1/* increment num of items */, 1));
		JScrollPane spFrac = new JScrollPane(fracP);
		JPanel frac = new JPanel(new GridLayout(5, 1));
		JPanel boxP = new JPanel();
		boxP.setLayout(new FlowLayout(FlowLayout.LEFT));
		JLabel boxlbl = new JLabel("Box sizes:");
		boxsize_fractal = new JTextField(25);
		boxsize_fractal.setText(boxSizes);
		boxsize_fractal.setToolTipText("Default values: 2,3,4,6,8,12,16,32,64");
		boxsize_fractal.getDocument().addDocumentListener(new DocumentListener() {
			// 元の背景色を保持しておく
			private final Color defaultColor = boxsize_fractal.getBackground();

			@Override
			public void insertUpdate(DocumentEvent e) {
				validate();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				validate();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				// Plain text components do not fire these events
			}

			private void validate() {
				// Swingのイベントは別スレッドで処理されるため、
				// UIの更新はSwingUtilities.invokeLaterで囲むのが安全
				SwingUtilities.invokeLater(() -> {
					String text = boxsize_fractal.getText();

					// 2. 正規表現で現在のテキストを検証
					if (isValidBoxSizes(text)) {
						// 3. 有効な場合は背景色を元に戻す
						boxsize_fractal.setBackground(defaultColor);
					} else {
						// 4. 無効な場合は背景色をエラー色に変更
						boxsize_fractal.setBackground(ERROR_COLOR);
					}
				});
			}
		});
		boxP.add(boxlbl);
		boxP.add(boxsize_fractal);
		frac.add(boxP);
		addBorder(frac, Color.gray, "Box counting");
		fracP.add(frac);
		return spFrac;
	}
	
	public boolean isRemoveOutliers() {
		return roChk.isSelected();
	}
	
	public boolean isRangeFiltering() {
		return rfChk.isSelected();
	}
	
	public boolean isResample() {
		return resampleChk.isSelected();
	}
	
	public Double sigmaOfRemoveOutliers() {
		String sv = roSigma.getText();
		try {
			Double v = Double.valueOf(sv);
			return v;
		}catch(NumberFormatException e) {
			return null;
		}
	}
	
	public Double[] rangeFilteringMinAndMax() {
		String smin = rfMin.getText();
		String smax = rfMax.getText();
		try {
			Double min = Double.valueOf(smin);
			Double max = Double.valueOf(smax);
			return new Double[] {min,max};
		}catch(NumberFormatException e) {
			return null;
		}
	}
	
	public Double[] resampligVoxelXYZ() {
		String sx = resampleX.getText();
		String sy = resampleY.getText();
		String sz = resampleZ.getText();
		try {
			Double x = Double.valueOf(sx);
			Double y = Double.valueOf(sy);
			Double z = Double.valueOf(sz);
			return new Double[] {x,y,z};
		}catch(NumberFormatException e) {
			return null;
		}
	}
	
	public List<String> getTargetFeatureNames(){
		int s = targetListModel.getSize();
		List<String> names = new ArrayList<>();
		for(int i=0;i<s;i++) {
			String n_short = targetListModel.get(i);
			String nn[] = n_short.split("_");
			nn[0] = shortFamilyNameToFull(nn[0]);
			names.add(nn[0]+"_"+nn[1]);
		}
		return names;
	}
	
	public String getSelectedCommandFromButtonGroup(ButtonGroup bg) {
		ButtonModel selectedModel = bg.getSelection();
		if (selectedModel != null) {
			return selectedModel.getActionCommand();
		}
		return null;
	}
	
	// 有効なパターンの正規表現をコンパイルしておく（パフォーマンス向上）
    private static final Pattern VALID_PATTERN = Pattern.compile("^(\\d+(,\\d+)*)?$");
    // エラー時の背景色
    private static final Color ERROR_COLOR = new Color(255, 200, 200);

	private static boolean isValidBoxSizes(String text) {
        // Pattern.matcher(text).matches() は、文字列全体がパターンに一致するかをチェックする
        return VALID_PATTERN.matcher(text).matches();
    }
	
	private void addBorder(JComponent p, Color c, String name) {
		Border b = BorderFactory.createBevelBorder(BevelBorder.RAISED, c, Color.DARK_GRAY);
		p.setBorder(BorderFactory.createTitledBorder(b, name, TitledBorder.LEFT, TitledBorder.DEFAULT_POSITION));
	}
	
	public void loadSettings(Properties prop) {
		try {
			List<String> params = SettingsContext.getStringFieldValues();
			for(String key : params) {
				String val = prop.getProperty(key);
				if(val == null || val.equals("") || val.toLowerCase().equals("null") ) {
					//keep default
					continue;
				}
				switch (key) {
				/**
				 * mask settings
				 */
				case SettingsContext.D3Basis:
					boolean is3D = Boolean.valueOf(val);
					if (this.d3_basis == true && is3D == true) {
						// do nothing, already 3D.
					} else if (this.d3_basis == false && is3D == true) {
						this.d3_basis = true;
						switch2D3D(true);
					} else if (this.d3_basis == true && is3D == false) {
						this.d3_basis = false;
						switch2D3D(false);
					} else {
						// false && false
						// do nothing
					}
					break;
				case SettingsContext.MASK_LABEL:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						lbltxt.setValue(v);
					} catch (NumberFormatException e) {
						// skip
					}
					break;
				case SettingsContext.RemoveOutliers:
					//set true if string is "true", else return false of all.
					boolean ro = Boolean.valueOf(val);
					roChk.setSelected(ro);
					break;
				case SettingsContext.RemoveOutliersSigma:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						roSigma.setValue(v);
					} catch (NumberFormatException e) {
						// skip
					}
					break;
				case SettingsContext.RangeFiltering:
					//set true if string is "true", else return false of all.
					boolean rf = Boolean.valueOf(val);
					rfChk.setSelected(rf);
					break;
				case SettingsContext.RangeFilteringMin:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						rfMin.setValue(v);
					} catch (NumberFormatException e) {
						// skip
					}
					break;
				case SettingsContext.RangeFilteringMax:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						rfMax.setValue(v);
					} catch (NumberFormatException e) {
						// skip
					}
					break;
				case SettingsContext.Resampling:
					//set true if string is "true", else return false of all.
					boolean re = Boolean.valueOf(val);
					resampleChk.setSelected(re);
					break;
				case SettingsContext.ResamplingX:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						resampleX.setValue(v);
					} catch (NumberFormatException e) {
						// skip
					}
					break;
				case SettingsContext.ResamplingY:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						resampleY.setValue(v);
					} catch (NumberFormatException e) {
						// skip
					}
					break;
				case SettingsContext.ResamplingZ:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						resampleZ.setValue(v);
					} catch (NumberFormatException e) {
						// skip
					}
					break;
				/**
				 * Feature group settings
				 * This is family level.
				 * If family name exists, add all to target.
				 * Finally, end of in this method, will exclude features by "EXCLUSION_" prefix. 
				 */
				case SettingsContext.OPERATIONAL:
					operationalChk.setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.DIAGNOSTICS:
					diagnosticsChk.setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.MORPHOLOGICAL:
					if(d3_basis==false) {
						if(morphologicalChk.isSelected()==true) {
							morphologicalChk.doClick();//be off
						}
					}else {
						if(Boolean.valueOf(val) == true && morphologicalChk.isSelected()==false) {
							morphologicalChk.doClick();//be on
						}else if(Boolean.valueOf(val) == false && morphologicalChk.isSelected()==true) {
							morphologicalChk.doClick();//be off
						}
					}
					break;
				case SettingsContext.LOCALINTENSITY:
					if(Boolean.valueOf(val) == true && localIntensChk.isSelected()==false) {
						localIntensChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && localIntensChk.isSelected()==true) {
						localIntensChk.doClick();//be off
					}
					break;
				case SettingsContext.INTENSITYSTATS:
					if(Boolean.valueOf(val) == true && intensityStatsChk.isSelected()==false) {
						intensityStatsChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && intensityStatsChk.isSelected()==true) {
						intensityStatsChk.doClick();//be off
					}
					break;
				case SettingsContext.INTENSITYHISTOGRAM:
					if(Boolean.valueOf(val) == true && histogramChk.isSelected()==false) {
						histogramChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && histogramChk.isSelected()==true) {
						histogramChk.doClick();//be off
					}
					break;
				case SettingsContext.INTENSITYVOLUMEHISTOGRAM:
					if(Boolean.valueOf(val) == true && volumeHistChk.isSelected()==false) {
						volumeHistChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && volumeHistChk.isSelected()==true) {
						volumeHistChk.doClick();//be off
					}
					break;
				case SettingsContext.GLCM:
					if(Boolean.valueOf(val) == true && glcmChk.isSelected()==false) {
						glcmChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && glcmChk.isSelected()==true) {
						glcmChk.doClick();//be off
					}
					break;
				case SettingsContext.GLRLM:
					if(Boolean.valueOf(val) == true && glrlmChk.isSelected()==false) {
						glrlmChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && glrlmChk.isSelected()==true) {
						glrlmChk.doClick();//be off
					}
					break;
				case SettingsContext.GLSZM:
					if(Boolean.valueOf(val) == true && glszmChk.isSelected()==false) {
						glszmChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && glszmChk.isSelected()==true) {
						glszmChk.doClick();//be off
					}
					break;
				case SettingsContext.GLDZM:
					if(Boolean.valueOf(val) == true && gldzmChk.isSelected()==false) {
						gldzmChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && gldzmChk.isSelected()==true) {
						gldzmChk.doClick();//be off
					}
					break;
				case SettingsContext.NGTDM:
					if(Boolean.valueOf(val) == true && ngtdmChk.isSelected()==false) {
						ngtdmChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && ngtdmChk.isSelected()==true) {
						ngtdmChk.doClick();//be off
					}
					break;
				case SettingsContext.NGLDM:
					if(Boolean.valueOf(val) == true && ngldmChk.isSelected()==false) {
						ngldmChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && ngldmChk.isSelected()==true) {
						ngldmChk.doClick();//be off
					}
					break;
				case SettingsContext.FRACTAL:
					if(Boolean.valueOf(val) == true && fractalChk.isSelected()==false) {
						fractalChk.doClick();//be on
					}else if(Boolean.valueOf(val) == false && fractalChk.isSelected()==true) {
						fractalChk.doClick();//be off
					}
					break;
				case SettingsContext.SHAPE2D:
					if(d3_basis==false) {
						if(Boolean.valueOf(val) == true && shape2dChk.isSelected()==false) {
							shape2dChk.doClick();//be on
						}else if(Boolean.valueOf(val) == false && shape2dChk.isSelected()==true) {
							shape2dChk.doClick();//be off
						}
					}else {
						if(shape2dChk.isSelected()==true) {
							shape2dChk.doClick();//be off
						}
					}
					break;
				/**
				 * Features param
				 */
				case SettingsContext.UseBinCountGLCM:
					bcs_glcm.getRadioButton().setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.BinCountGLCM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						bcs_glcm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.BinWidthGLCM:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						bws_glcm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.DeltaGLCM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						delta_glcm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
//				case SettingsContext.NormGLCM:
//					norm_glcm.setSelectedItem(val);
//					break;
				case SettingsContext.UseBinCountGLRLM:
					bcs_glrlm.getRadioButton().setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.BinCountGLRLM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						bcs_glrlm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.BinWidthGLRLM:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						bws_glrlm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
//				case SettingsContext.NormGLRLM:
//					norm_glrlm.setSelectedItem(val);
//					break;
				case SettingsContext.UseBinCountGLSZM:
					bcs_glszm.getRadioButton().setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.BinCountGLSZM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						bcs_glszm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.BinWidthGLSZM:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						bws_glszm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
//				case SettingsContext.NormGLSZM:
//					norm_glszm.setSelectedItem(val);
//					break;
				case SettingsContext.UseBinCountGLDZM:
					bcs_gldzm.getRadioButton().setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.BinCountGLDZM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						bcs_gldzm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.BinWidthGLDZM:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						bws_gldzm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
//				case SettingsContext.NormGLDZM:
//					norm_gldzm.setSelectedItem(val);
//					break;
				case SettingsContext.UseBinCountNGTDM:
					bcs_ngtdm.getRadioButton().setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.BinCountNGTDM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						bcs_ngtdm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.BinWidthNGTDM:
					try {
						double v = Double.valueOf(val);// check whether integer or not
						bws_ngtdm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.DeltaNGTDM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						delta_ngtdm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
//				case SettingsContext.NormNGTDM:
//					norm_ngtdm.setSelectedItem(val);
//					break;
				case SettingsContext.UseBinCountNGLDM:
					bcs_ngldm.getRadioButton().setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.BinCountNGLDM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						bcs_ngldm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.BinWidthNGLDM:
					try {
						double v = Double.valueOf(val);
						bws_ngldm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.AlphaNGLDM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						alpha_ngldm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.DeltaNGLDM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						delta_ngldm.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
//				case SettingsContext.NormNGLDM:
//					norm_ngldm.setSelectedItem(val);
//					break;
				case SettingsContext.UseBinCountHISTOGRAM:
					bcs_hist.getRadioButton().setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.BinCountHISTOGRAM:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						bcs_hist.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.BinWidthHISTOGRAM:
					try {
						double v = Double.valueOf(val);
						bws_hist.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.UseOriginalIVH:
					useOrg_ivh.setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.UseBinCountIVH:
					bcs_ivh.getRadioButton().setSelected(Boolean.valueOf(val));
					break;
				case SettingsContext.BinCountIVH:
					try {
						int v = Integer.valueOf(val);// check whether integer or not
						bcs_ivh.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.BinWidthIVH:
					try {
						double v = Double.valueOf(val);
						bws_ivh.setValue(v);
					}catch(NumberFormatException e) {
						//skip
					}
					break;
				case SettingsContext.BoxSizesFRACTAL:
					boxsize_fractal.setText(val);
					break;
				default:
					break;
				}
			}
			/*
			 * Finally, exclude EXCLUSION_FEATURE
			 */
			for(String key : params) {
				/*
				 * EXCLUSION key is specify Exfeature name.
				 */
				if(key.startsWith("EXCLUSION")) {
					String fname = key.replace("EXCLUSION_", "");
					String fullFam = fullFamilyNameToShort(fname.split("_")[0]);
					fname = fullFam + "_" + fname.split("")[1];
					addList(fname, exclusionListModel);
				}
			}
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		};
	}
	
	public Properties currentSettings() {
		Properties prop = new Properties();
		try {
			Integer i = null;
			Double d = null;
			List<String> params = SettingsContext.getStringFieldValues();
			for(String key : params) {
				switch (key) {
				/**
				 * mask settings
				 */
				case SettingsContext.D3Basis:
					prop.setProperty(key, String.valueOf(d3_basis));
					break;
				case SettingsContext.MASK_LABEL:
					i = (Integer) lbltxt.getValue();
					if(i != null) {
						prop.setProperty(key, String.valueOf(i));
					}
					break;
				case SettingsContext.RemoveOutliers:
					prop.setProperty(key, String.valueOf(roChk.isSelected()));
					break;
				case SettingsContext.RemoveOutliersSigma:
					i = (Integer) roSigma.getValue();
					if(i != null) {
						prop.setProperty(key, String.valueOf(i));
					}
					break;
				case SettingsContext.RangeFiltering:
					prop.setProperty(key, String.valueOf(rfChk.isSelected()));
					break;
				case SettingsContext.RangeFilteringMin:
					d = (Double) rfMin.getValue();
					if(d != null) {
						prop.setProperty(key, String.valueOf(d));
					}
					break;
				case SettingsContext.RangeFilteringMax:
					d = (Double) rfMax.getValue();
					if(d != null) {
						prop.setProperty(key, String.valueOf(d));
					}
					break;
				case SettingsContext.Resampling:
					prop.setProperty(key, String.valueOf(resampleChk.isSelected()));
					break;
				case SettingsContext.ResamplingX:
					d = (Double) resampleX.getValue();
					if(d != null) {
						prop.setProperty(key, String.valueOf(d));
					}
					break;
				case SettingsContext.ResamplingY:
					Double v = (Double) resampleY.getValue();
					if(v != null) {
						prop.setProperty(key, String.valueOf(v));
					}
					break;
				case SettingsContext.ResamplingZ:
					d = (Double) resampleZ.getValue();
					if(d != null) {
						prop.setProperty(key, String.valueOf(d));
					}
					break;
				/**
				 * Feature group settings
				 */
				case SettingsContext.OPERATIONAL:
					prop.setProperty(key, String.valueOf(operationalChk.isSelected()));
					break;
				case SettingsContext.DIAGNOSTICS:
					prop.setProperty(key, String.valueOf(diagnosticsChk.isSelected()));
					break;
				case SettingsContext.MORPHOLOGICAL:
					prop.setProperty(key, String.valueOf(morphologicalChk.isSelected()));
					break;
				case SettingsContext.LOCALINTENSITY:
					prop.setProperty(key, String.valueOf(localIntensChk.isSelected()));
					break;
				case SettingsContext.INTENSITYSTATS:
					prop.setProperty(key, String.valueOf(intensityStatsChk.isSelected()));
					break;
				case SettingsContext.INTENSITYHISTOGRAM:
					prop.setProperty(key, String.valueOf(histogramChk.isSelected()));
					break;
				case SettingsContext.INTENSITYVOLUMEHISTOGRAM:
					prop.setProperty(key, String.valueOf(volumeHistChk.isSelected()));
					break;
				case SettingsContext.GLCM:
					prop.setProperty(key, String.valueOf(glcmChk.isSelected()));
					break;
				case SettingsContext.GLRLM:
					prop.setProperty(key, String.valueOf(glrlmChk.isSelected()));
					break;
				case SettingsContext.GLSZM:
					prop.setProperty(key, String.valueOf(glszmChk.isSelected()));
					break;
				case SettingsContext.GLDZM:
					prop.setProperty(key, String.valueOf(gldzmChk.isSelected()));
					break;
				case SettingsContext.NGTDM:
					prop.setProperty(key, String.valueOf(ngtdmChk.isSelected()));
					break;
				case SettingsContext.NGLDM:
					prop.setProperty(key, String.valueOf(ngldmChk.isSelected()));
					break;
				case SettingsContext.FRACTAL:
					prop.setProperty(key, String.valueOf(fractalChk.isSelected()));
					break;
				case SettingsContext.SHAPE2D:
					prop.setProperty(key, String.valueOf(shape2dChk.isSelected()));
					break;
				/**
				 * Features param
				 */
				case SettingsContext.UseBinCountGLCM:
					prop.setProperty(key, String.valueOf(bcs_glcm.getRadioButton().isSelected()));
					break;
				case SettingsContext.BinCountGLCM:
					prop.setProperty(key, String.valueOf(bcs_glcm.getValue()));
					break;
				case SettingsContext.BinWidthGLCM:
					prop.setProperty(key, String.valueOf(bws_glcm.getValue()));
					break;
				case SettingsContext.DeltaGLCM:
					prop.setProperty(key, String.valueOf(delta_glcm.getValue()));
					break;
//				case SettingsContext.NormGLCM:
//					prop.setProperty(key, norm_glcm.getSelectedItem());
//					break;
				case SettingsContext.UseBinCountGLRLM:
					prop.setProperty(key, String.valueOf(bcs_glrlm.getRadioButton().isSelected()));
					break;
				case SettingsContext.BinCountGLRLM:
					prop.setProperty(key, String.valueOf(bcs_glrlm.getValue()));
					break;
				case SettingsContext.BinWidthGLRLM:
					prop.setProperty(key, String.valueOf(bws_glrlm.getValue()));
					break;
//				case SettingsContext.NormGLRLM:
//					prop.setProperty(key, norm_glrlm.getSelectedItem());
//					break;
				case SettingsContext.UseBinCountGLSZM:
					prop.setProperty(key, String.valueOf(bcs_glszm.getRadioButton().isSelected()));
					break;
				case SettingsContext.BinCountGLSZM:
					prop.setProperty(key, String.valueOf(bcs_glszm.getValue()));
					break;
				case SettingsContext.BinWidthGLSZM:
					prop.setProperty(key, String.valueOf(bws_glszm.getValue()));
					break;
//				case SettingsContext.NormGLSZM:
//					prop.setProperty(key, norm_glszm.getSelectedItem());
//					break;
				case SettingsContext.UseBinCountGLDZM:
					prop.setProperty(key, String.valueOf(bcs_gldzm.getRadioButton().isSelected()));
					break;
				case SettingsContext.BinCountGLDZM:
					prop.setProperty(key, String.valueOf(bcs_gldzm.getValue()));
					break;
				case SettingsContext.BinWidthGLDZM:
					prop.setProperty(key, String.valueOf(bws_gldzm.getValue()));
					break;
//				case SettingsContext.NormGLDZM:
//					prop.setProperty(key, norm_gldzm.getSelectedItem());
//					break;
				case SettingsContext.UseBinCountNGTDM:
					prop.setProperty(key, String.valueOf(bcs_ngtdm.getRadioButton().isSelected()));
					break;
				case SettingsContext.BinCountNGTDM:
					prop.setProperty(key, String.valueOf(bcs_ngtdm.getValue()));
					break;
				case SettingsContext.BinWidthNGTDM:
					prop.setProperty(key, String.valueOf(bws_ngtdm.getValue()));
					break;
				case SettingsContext.DeltaNGTDM:
					prop.setProperty(key, String.valueOf(delta_ngtdm.getValue()));
					break;
//				case SettingsContext.NormNGTDM:
//					prop.setProperty(key, norm_ngtdm.getSelectedItem());
//					break;
				case SettingsContext.UseBinCountNGLDM:
					prop.setProperty(key, String.valueOf(bcs_ngldm.getRadioButton().isSelected()));
					break;
				case SettingsContext.BinCountNGLDM:
					prop.setProperty(key, String.valueOf(bcs_ngldm.getValue()));
					break;
				case SettingsContext.BinWidthNGLDM:
					prop.setProperty(key, String.valueOf(bws_ngldm.getValue()));
					break;
				case SettingsContext.AlphaNGLDM:
					prop.setProperty(key, String.valueOf(alpha_ngldm.getValue()));
					break;
				case SettingsContext.DeltaNGLDM:
					prop.setProperty(key, String.valueOf(delta_ngldm.getValue()));
					break;
//				case SettingsContext.NormNGLDM:
//					prop.setProperty(key, norm_ngldm.getSelectedItem());
//					break;
				case SettingsContext.UseBinCountHISTOGRAM:
					prop.setProperty(key, String.valueOf(bcs_hist.getRadioButton().isSelected()));
					break;
				case SettingsContext.BinCountHISTOGRAM:
					prop.setProperty(key, String.valueOf(bcs_hist.getValue()));
					break;
				case SettingsContext.BinWidthHISTOGRAM:
					prop.setProperty(key, String.valueOf(bws_hist.getValue()));
					break;
				case SettingsContext.UseOriginalIVH:
					prop.setProperty(key, String.valueOf(useOrg_ivh.isSelected()));
					break;
				case SettingsContext.UseBinCountIVH:
					prop.setProperty(key, String.valueOf(bcs_ivh.getRadioButton().isSelected()));
					break;
				case SettingsContext.BinCountIVH:
					prop.setProperty(key, String.valueOf(bcs_ivh.getValue()));
					break;
				case SettingsContext.BinWidthIVH:
					prop.setProperty(key, String.valueOf(bws_ivh.getValue()));
					break;
				case SettingsContext.BoxSizesFRACTAL:
					prop.setProperty(key, String.valueOf(boxsize_fractal.getText()));
					break;
				default:
					break;
				}
			}//loop end
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		};
		//finally, add exclusions
		int size = exclusionListModel.getSize();
		for(int k=0; k<size; k++) {
			String ex_fname = exclusionListModel.get(k);
			String fullFam = shortFamilyNameToFull(ex_fname.split("_")[0]);
			ex_fname = fullFam + "_" + ex_fname.split("_")[1];
			prop.setProperty("EXCLUSION_"+ex_fname, ex_fname/*dummy*/);
		}
		return prop;
	}
	
	private String shortFamilyNameToFull(String shortFamilyName) {
		switch (shortFamilyName) {
		case morpShort:
			return MORPHOLOGICAL;
		case liShort:
			return LOCALINTENSITY;
		case histShort:
			return INTENSITYHISTOGRAM;
		case statShort:
			return INTENSITYSTATS;
		case ivhShort:
			return INTENSITYVOLUMEHISTOGRAM;
		default:
			return shortFamilyName;
		}
	}
	
	private String fullFamilyNameToShort(String fullFamilyName) {
		switch (fullFamilyName) {
		case MORPHOLOGICAL:
			return morpShort;
		case LOCALINTENSITY:
			return liShort;
		case INTENSITYHISTOGRAM:
			return histShort;
		case INTENSITYSTATS:
			return statShort;
		case INTENSITYVOLUMEHISTOGRAM:
			return ivhShort;
		default:
			return fullFamilyName;
		}
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
			if(listModel == targetListModel) {
				System.out.println(name + " is already listed in targetList");
			}else {
				System.out.println(name + " is already listed in exclusionList");
			}
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
	
	private void insertBlankPanel(JPanel gridPanel, int iteration) {
		for(int i=0; i<iteration; i++) {
			gridPanel.add(new JPanel());
		}
	}
	
	
	private ButtonGroup setButtonGroup(BinCountSettings bcs, BinWidthSettings bws, boolean useBinCount, String family) {
		bcs.getRadioButton().setActionCommand("BinCount"+family);
		bws.getRadioButton().setActionCommand("BinWidth"+family);
		ButtonGroup bg = new ButtonGroup();
		bg.add(bcs.getRadioButton());
		bg.add(bws.getRadioButton());
		if(useBinCount) {
			bg.setSelected((ButtonModel) bcs.getRadioButton().getModel(), useBinCount);
		}
		return bg;
	}
	
	private void switch2D3D(boolean is3D) {
		morphologicalChk.doClick();
		shape2dChk.doClick();
		if(is3D==true) {
			if(shape2dChk.isSelected()) {
				shape2dChk.doClick();//off
			}
			shape2dChk.setEnabled(false);
			morphologicalChk.setEnabled(true);
			if(morphologicalChk.isSelected() == false) {
				morphologicalChk.doClick();
			}
		}else {
			shape2dChk.setEnabled(true);
			if(shape2dChk.isSelected() == false) {
				shape2dChk.doClick();//on
			}
			if(morphologicalChk.isSelected()) {
				morphologicalChk.doClick();//off
			}
			morphologicalChk.setEnabled(false);
		}
		addDefaultExclusionBtn.doClick();
		revalidate();
		repaint();
	}
	
	/**
	 * Operational/Diagnostics are excluded.
	 * @return feature names
	 */
	public static List<String> featureNames(String familyName/*null-able*/){
//		HashSet<String> names = new HashSet<>();//cannot keep order.
		List<String> names = new ArrayList<>();
		if(familyName == null) {
			for(MorphologicalFeatureType f : MorphologicalFeatureType.values()) {
				names.add(morpShort+"_"+f.name());
			}
			for(LocalIntensityFeatureType f : LocalIntensityFeatureType.values()) {
				names.add(liShort+"_"+f.name());
			}
			for(IntensityBasedStatisticalFeatureType f : IntensityBasedStatisticalFeatureType.values()) {
				names.add(statShort+"_"+f.name());
			}
			for(IntensityHistogramFeatureType f : IntensityHistogramFeatureType.values()) {
				names.add(histShort+"_"+f.name());
			}
			for(IntensityVolumeHistogramFeatureType f : IntensityVolumeHistogramFeatureType.values()) {
				names.add(ivhShort+"_"+f.name());
			}
			for(GLCMFeatureType f : GLCMFeatureType.values()) {
				names.add(GLCM+"_"+f.name());
			}
			for(GLRLMFeatureType f : GLRLMFeatureType.values()) {
				names.add(GLRLM+"_"+f.name());
			}
			for(GLSZMFeatureType f : GLSZMFeatureType.values()) {
				names.add(GLSZM+"_"+f.name());
			}
			for(GLDZMFeatureType f : GLDZMFeatureType.values()) {
				names.add(GLDZM+"_"+f.name());
			}
			for(NGTDMFeatureType f : NGTDMFeatureType.values()) {
				names.add(NGTDM+"_"+f.name());
			}
			for(NGLDMFeatureType f : NGLDMFeatureType.values()) {
				names.add(NGLDM+"_"+f.name());
			}
			for(FractalFeatureType f : FractalFeatureType.values()) {
				names.add(FRACTAL+"_"+f.name());
			}
			/*
			 * calculate only force2D set true.
			 */
			for(Shape2DFeatureType f : Shape2DFeatureType.values()) {
				names.add(SHAPE2D+"_"+f.name());
			}
		}else if(familyName.equals(MORPHOLOGICAL)) {
			for(MorphologicalFeatureType f : MorphologicalFeatureType.values()) {
				names.add(morpShort+"_"+f.name());
			}
		}else if(familyName.equals(LOCALINTENSITY)) {
			for(LocalIntensityFeatureType f : LocalIntensityFeatureType.values()) {
				names.add(liShort+"_"+f.name());
			}
		}else if(familyName.equals(INTENSITYSTATS)) {
			for(IntensityBasedStatisticalFeatureType f : IntensityBasedStatisticalFeatureType.values()) {
				names.add(statShort+"_"+f.name());
			}
		}else if(familyName.equals(INTENSITYHISTOGRAM)) {
			for(IntensityHistogramFeatureType f : IntensityHistogramFeatureType.values()) {
				names.add(histShort+"_"+f.name());
			}
		}else if(familyName.equals(INTENSITYVOLUMEHISTOGRAM)) {
			for(IntensityVolumeHistogramFeatureType f : IntensityVolumeHistogramFeatureType.values()) {
				names.add(ivhShort+"_"+f.name());
			}
		}else if(familyName.equals(GLCM)) {
			for(GLCMFeatureType f : GLCMFeatureType.values()) {
				names.add(GLCM+"_"+f.name());
			}
		}else if(familyName.equals(GLRLM)) {
			for(GLRLMFeatureType f : GLRLMFeatureType.values()) {
				names.add(GLRLM+"_"+f.name());
			}
		}else if(familyName.equals(GLSZM)) {
			for(GLSZMFeatureType f : GLSZMFeatureType.values()) {
				names.add(GLSZM+"_"+f.name());
			}
		}else if(familyName.equals(GLDZM)) {
			for(GLDZMFeatureType f : GLDZMFeatureType.values()) {
				names.add(GLDZM+"_"+f.name());
			}
		}else if(familyName.equals(NGTDM)) {
			for(NGTDMFeatureType f : NGTDMFeatureType.values()) {
				names.add(NGTDM+"_"+f.name());
			}
		}else if(familyName.equals(NGLDM)) {
			for(NGLDMFeatureType f : NGLDMFeatureType.values()) {
				names.add(NGLDM+"_"+f.name());
			}
		}else if(familyName.equals(FRACTAL)) {
			for(FractalFeatureType f : FractalFeatureType.values()) {
				names.add(FRACTAL+"_"+f.name());
			}
		}else if(familyName.equals(SHAPE2D)) {
			/*
			 * calculate only force2D set true.
			 */
			for(Shape2DFeatureType f : Shape2DFeatureType.values()) {
				names.add(SHAPE2D+"_"+f.name());
			}
		}
		return names;
	}
	
	public void adjustDividerLocation() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (sp1.getWidth() > 0 && sp1.getHeight() > 0) {
					sp1.setDividerLocation(0.4);
				}
				if (sp2.getWidth() > 0 && sp2.getHeight() > 0) {
					sp2.setDividerLocation(0.6);
				}
			}
		});
	}
	
	private JFormattedTextField formattedTextField(boolean isDouble, int columnSize) {
		NumberFormat format = null;
		NumberFormatter formatter = null;
		if (!isDouble) {
			format = NumberFormat.getIntegerInstance();
			format.setGroupingUsed(false); // 桁区切りカンマを無効にする場合
			formatter = new NumberFormatter(format);
			formatter.setValueClass(Integer.class); // 値のクラスをIntegerに設定
		} else {
			format = NumberFormat.getNumberInstance();
			format.setGroupingUsed(false); // 桁区切りカンマを無効にする場合
			formatter = new NumberFormatter(format);
			formatter.setValueClass(Double.class); // 値のクラスをIntegerに設定
		}
		formatter.setAllowsInvalid(false); // 無効な入力を許可しない
		formatter.setCommitsOnValidEdit(true); // 有効な編集が行われたら即座に値をコミット
		// 3. JFormattedTextFieldを作成し、フォーマッタをセット
		JFormattedTextField textField = new JFormattedTextField(formatter);
		textField.setColumns(columnSize); // フィールドの幅を設定
		textField.setHorizontalAlignment(JTextField.RIGHT);
		return textField;
	}
	
	
	class BinCountSettings extends JPanel{
		JRadioButton btn;
		JFormattedTextField textField;
		public BinCountSettings(int bin) {
			FlowLayout fl = new FlowLayout();
			fl.setAlignment(FlowLayout.LEFT);
			setLayout(fl);
			btn = new JRadioButton("Bin Count");
			add(btn);
			textField = formattedTextField(false, 10);
	       textField.setValue(bin);    // 初期値を設定
	       add(textField);
		}
		
		JRadioButton getRadioButton() {
			return btn;
		}
		
		void setValue(int bin) {
			textField.setValue(bin);
		}
		
		int getValue() {
			Object v =  textField.getValue();
			try {
				Integer v_ = (int)v;
				return v_;
			}catch(NumberFormatException e) {
				return -1;
			}
		}
	}
	
	class BinWidthSettings extends JPanel{
		JRadioButton btn;
		JFormattedTextField textField;
		public BinWidthSettings(double width) {
			FlowLayout fl = new FlowLayout();
			fl.setAlignment(FlowLayout.LEFT);
			setLayout(fl);
			btn = new JRadioButton("Bin Width");
			add(btn);
	       textField = formattedTextField(true, 10);
	       setValue(width);
	       add(textField);
		}
		
		JRadioButton getRadioButton() {
			return btn;
		}
		
		void setValue(double width) {
			if(!Double.isNaN(width)) {
				textField.setValue(width);
			}
		}
		
		double getValue() {
			Object v =  textField.getValue();
			try {
				Double v_ = (double)v;
				return v_;
			}catch(NullPointerException | NumberFormatException e) {
				return -1;
			}
		}
	}
	
	class AlphaDeltaSettings extends JPanel{
		final boolean isDouble; 
		JFormattedTextField textField;
		public AlphaDeltaSettings(String name, Object param) {
			FlowLayout fl = new FlowLayout();
			fl.setAlignment(FlowLayout.LEFT);
			setLayout(fl);
			add(new JLabel(name+":"));
			isDouble = param instanceof Double;
			textField = formattedTextField(isDouble, 10);
	       textField.setValue(param);    // 初期値を設定
	       add(textField);
		}
		
		void setValue(Object param) {
			if(isDouble) {
				textField.setValue((Double)param);
			}else {
				textField.setValue((Integer)param);
			}
		}
		
		int getValue() {
			Object v =  textField.getValue();
			try {
				Integer v_ = (int)v;
				return v_;
			}catch(NullPointerException | NumberFormatException e) {
				return -1;
			}
		}
	}
	
	class NormComboPanel extends JPanel {
		JComboBox<String> combo;
		public NormComboPanel(String norm) {
			FlowLayout fl = new FlowLayout(FlowLayout.LEFT);
			setLayout(fl);
			JLabel lbl = new JLabel("Normalize method");
			combo = new JComboBox<>(norms);
			add(lbl);
			add(combo);
			setSelectedItem(norm);
		}
		
		String getSelectedItem() {
			return (String)combo.getSelectedItem();
		}
		
		void setSelectedItem(String item) {
			combo.setSelectedItem(item);
		}
	}
}
