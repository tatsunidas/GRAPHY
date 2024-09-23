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

package com.vis.core.view.D2.ui;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.vis.core.view.D2.ui.glasses.PraparatShelf;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Indicates what images showing StageView on with patient level.
 * 
 * @author tatsunidas
 *
 */
public class PatientInfoCake extends JPanel{

	private static final long serialVersionUID = -6517900200456356463L;
	private PatientInfoPanel patPanel;
	private StudyListTable studies;
	private SeriesListTable series;
	private ImageListTable images;
	final private HashMap<String,String> patInfoSet;
	
	public PatientInfoCake(HashMap<String,String> patInfoSet) {
		this.patInfoSet = patInfoSet;
		setLayout(new BorderLayout());
		initComponent();
	}
	
	private void initComponent() {
		//patient info panel
		String patID = patInfoSet.get("PatientID");
		String patName = patInfoSet.get("PatientName");
		String patBOD = patInfoSet.get("PatientBirthDate");
		String patSex = patInfoSet.get("PatientSex");
		patPanel = new PatientInfoPanel();
		patPanel.setPatientInfo(patID,patName,patBOD,patSex);
		
		constructHierarchyTables();
		
		//patPanel and studies
		JSplitPane patAndStudySplit = new JSplitPane();
		patAndStudySplit.setOrientation(JSplitPane.VERTICAL_SPLIT);
		patAndStudySplit.setLeftComponent(patPanel);
		patAndStudySplit.setRightComponent(studies.getAsScrollPane());
		patAndStudySplit.setDividerLocation(100);
		JPanel patAndStudySplitBase = new JPanel();
		patAndStudySplitBase.setLayout(new BorderLayout(0, 0));
		patAndStudySplitBase.add(patAndStudySplit,BorderLayout.CENTER);
		//patAndStudySplitBase and SeriesTable
		JSplitPane patAndStudyAndSeriesSplit = new JSplitPane();
		patAndStudyAndSeriesSplit.setOrientation(JSplitPane.VERTICAL_SPLIT);
		patAndStudyAndSeriesSplit.setLeftComponent(patAndStudySplitBase);
		patAndStudyAndSeriesSplit.setRightComponent(series.getAsScrollPane());
		patAndStudyAndSeriesSplit.setDividerLocation(200);
		JPanel patAndStudyAndSeriesSplitBase = new JPanel();
		patAndStudyAndSeriesSplitBase.setLayout(new BorderLayout(0, 0));
		patAndStudyAndSeriesSplitBase.add(patAndStudyAndSeriesSplit,BorderLayout.CENTER);
		//
		JSplitPane patAndStudyAndSeriesAndImageSplit = new JSplitPane();
		patAndStudyAndSeriesAndImageSplit.setOrientation(JSplitPane.VERTICAL_SPLIT);
		patAndStudyAndSeriesAndImageSplit.setLeftComponent(patAndStudyAndSeriesSplitBase);
		patAndStudyAndSeriesAndImageSplit.setRightComponent(images.getAsScrollPane());
		patAndStudyAndSeriesAndImageSplit.setDividerLocation(400);
		
		add(patAndStudyAndSeriesAndImageSplit,BorderLayout.CENTER);
		
		revalidate();
	}
	
	private void constructHierarchyTables() {
		//set up tables
		studies = new StudyListTable(this);
		series = new SeriesListTable(this, studies);
		images = new ImageListTable(this, series);
	}
	
	String getPatientInfo(String key) {
		return patInfoSet.get(key);
	}
	
	String getSelectedStudyUID() {
		return studies.getSlectedStudyUID();
	}
	
	String getSelectedSeriesUID() {
		return series.getSelectedSeriesUID();
	}
	
	String[] getSelectedSopUIDs() {
		return images.getSelectedSopUIDs();
	}
		
	public void linkWithEyepiece(List<com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext> prapcons) {
		List<String> studyUIDSet = new ArrayList<String>();
		List<String> seriesUIDSet = new ArrayList<String>();
		List<String[]> sopUIDSet = new ArrayList<String[]>();
		for(PraparatShelf.PraparatContext pcon:prapcons) {
			Object uids[] = pcon.getContextUIDs();
			studyUIDSet.add((String)uids[1]);
			seriesUIDSet.add((String)uids[2]);
			sopUIDSet.add((String[])uids[3]);
		}
		studies.cleanUpdatePresenceOnStageStudy(studyUIDSet);
		series.cleanUpdatePresenceOnStageSeries(seriesUIDSet);
		images.cleanUpdatePresenceOnStageImages(sopUIDSet);
	}
}
