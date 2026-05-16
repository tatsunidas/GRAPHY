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
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import com.vis.configuration.Resources;
import com.vis.core.anonymize.StudyCheckBoxTree;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;

import io.github.tatsunidas.radiomics.main.RadiomicsJ;

/**
 * 
 * @author tatsunidas
 *
 */
public class RadiomicsWindow extends JFrame{

	private static final long serialVersionUID = -8494940884028066246L;
	
	/*
	 * load a study.
	 */
	private JTree seriesTree;
    private JSplitPane mainSplitPane;
	
	static RadiomicsJ radiomics = new RadiomicsJ();
//	SampleClassifierPanel panel;
	RadiomicsSettings textureParams;
	RadiomicsBatchModePanel batchPanel;
	RadiomicsPipeline pipeline;
	
	RadiomicsVisualizationPanel visPanel;
	
	public static void main(String[] args) {
		new RadiomicsWindow();
	}
	
	public RadiomicsWindow() {
		pipeline = new RadiomicsPipeline();
		buildGUI(null);
	}
	
	public RadiomicsWindow(DICOMNode study) {
		pipeline = new RadiomicsPipeline();
		buildGUI(study);
	}
	
	private void buildGUI(DICOMNode study/*null-able*/) {
		
		loadStudyDataAndBuildTree(study);
		
		JTabbedPane tabPane = new JTabbedPane();
		JScrollPane treeScrollPane = new JScrollPane(seriesTree);
        treeScrollPane.setPreferredSize(new Dimension(200, 600));
        
		//Machine learning sample
//		panel = new SampleClassifierPanel(this);
//		tabPane.addTab("Operation",panel);
		
		textureParams = new RadiomicsSettings();
		tabPane.addTab("TextureParams", textureParams);
		
		batchPanel = new RadiomicsBatchModePanel(textureParams);
		tabPane.addTab("Batch Execution", batchPanel);
		
		visPanel = new RadiomicsVisualizationPanel(textureParams);
		tabPane.addTab("Visualization Map", visPanel);
		
        // 全体をJSplitPaneで左右に分割
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, tabPane);
        mainSplitPane.setDividerLocation(200);
        
        add(mainSplitPane, BorderLayout.CENTER);
		pack();
		if(WindowManager.getMainScreen() == null) {
			setLocationRelativeTo(null);
		}else {
			setLocationRelativeTo(WindowManager.getMainScreen());
		}
		setTitle("Machine Learning & Radimics Feature Calculator");
		setIconImage(Resources.RadiomicsJIcon.loadIconFromResource().getImage());
		setSize(900, 600);
		setVisible(true);
		textureParams.adjustDividerLocation();
	}
	
	public RadiomicsPipeline getPipeline() {
		return pipeline;
	}
	
	public Properties getRadiomicsSettingsAsProp() {
		return textureParams.currentSettings();
	}
	
	public RadiomicsSettings getRadiomicsSettings() {
		return textureParams;
	}
	
	public void loadRadiomicsSettings(Properties prop) {
		textureParams.loadSettings(prop);
	}
	
	/**
	 * DBなどから取得したスタディ情報を受け取り、ツリーを構築する
	 * 
	 * @param study 入力された1つのスタディ
	 */
	private void loadStudyDataAndBuildTree(DICOMNode study) {
		if (study == null) {
			Log.logger.info("Study is null...");
			return;
		}
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			Log.logger.log(Level.SEVERE, "Graphy DB cannot found !");
			return;
		}
		if(study.getLevel() != DICOMNode.STUDY){
			Log.logger.info("This selected node is not StudyNode. Series cannot be loaded...");
			return;
		}

		String pid = study.getData(DICOMNode.PatientID);
		String studyUID = study.getData(DICOMNode.StudyInstanceUID);

		if (db.getNumOfSeries(pid, studyUID) <= 0) {
			Log.logger.log(Level.SEVERE, "This study does not have any series... please check DB records !");
			return;
		};
		// 4. ツリーモデルを更新
        DefaultTreeModel model = new DefaultTreeModel(study);
        seriesTree = new JTree(model);
        seriesTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DICOMNode node = (DICOMNode) seriesTree.getLastSelectedPathComponent();
                if (node == null || node.isRoot()) return;
                if(node.getLevel() != DICOMNode.SERIES) {
                	return;
                }
                String studyUID = node.getData(DICOMNode.StudyInstanceUID);
                String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
                // show images to visPanel
                String modality = node.getData(DICOMNode.Modality);
                if(modality != null && modality.contains("SEG")) {
                	visPanel.onLoadMaskFromDb(pid, studyUID, seriesUID);
                }else {
                	visPanel.onLoadImageFromDb(pid, studyUID, seriesUID);
                }
            }
        });

        seriesTree.setRootVisible(true);
        seriesTree.setShowsRootHandles(true);

        // 全ノードを展開状態にする
        expandAllNodes(seriesTree, 0, seriesTree.getRowCount());
        
    }
	
	private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
		for (int i = startingIndex; i < rowCount; ++i) {
			tree.expandRow(i);
		}
		if (tree.getRowCount() != rowCount) {
			expandAllNodes(tree, rowCount, tree.getRowCount());
		}
	}
}
