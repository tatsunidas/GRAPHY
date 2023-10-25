package com.vis.core.view.D2.ui;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.vis.core.view.D2.ui.glasses.PraparatShelf;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;

public class DataInfoTower extends JPanel{

	/**
	 * Emphasize color for list on Eye.
	 */
	private static final long serialVersionUID = -6517900200456356463L;
	private PatientInfoPanel patPanel;
	private StudyListTable studies;
	private SeriesListTable series;
	private ImageListTable images;

	public DataInfoTower() {
		setLayout(new BorderLayout(0, 0));
	}
	
	public DataInfoTower(HashMap<String,String> patInfoSet,String studyUID,String seriesUID, String[] sopUIDs) {
		setLayout(new BorderLayout(0, 0));
		buildTower(patInfoSet, studyUID, seriesUID, sopUIDs);
	}

	public void buildTower(HashMap<String,String> patInfoSet,String studyUID,String seriesUID, String[] sopUIDs) {
		//patient info panel
		String patID = patInfoSet.get("PatientID");
		String patName = patInfoSet.get("PatientName");
		String patBOD = patInfoSet.get("PatientBirthDate");
		String patSex = patInfoSet.get("PatientSex");
		patPanel = new PatientInfoPanel();
		patPanel.setPatientInfo(patID,patName,patBOD,patSex);
		setPatientInfoPanel(patPanel);
		//construct hierarchy tables
		constructHierarchyTables(patID, studyUID, seriesUID, sopUIDs);
		
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
	}
	
	private void constructHierarchyTables(String patID,String studyUID,String seriesUID, String[] sopUIDs) {
		//set up tables
		StudyListTable studyList = new StudyListTable(patID,studyUID);
		SeriesListTable seriesList = new SeriesListTable(patID,studyUID,seriesUID);
		/*
		 * if selected DICOMNode level is study, seriesUID or sopUID are null. 
		 */
		if(seriesUID == null) {
			//select fistRow in study in db.
			seriesUID = seriesList.getSeriesInstanceUIDAtSelectedRow(0);
		}
		ImageListTable imageList = new ImageListTable(patID,studyUID,seriesUID,sopUIDs);
		
		//set relations
		studyList.setRelatedSeriesListTable(seriesList);
		seriesList.setImageListTable(imageList);
		//add to tower
		setStudyList(studyList);
		setSeriesList(seriesList);
		setImageList(imageList);
	}
	
	public void setPatientInfoPanel(PatientInfoPanel pp) {
		this.patPanel = pp;
	}
	
	public void setStudyList(StudyListTable sl) {
		this.studies = sl;
	}
	
	public void setSeriesList(SeriesListTable sel) {
		this.series = sel;
	}
	
	public void setImageList(ImageListTable imglisttbl) {
		this.images = imglisttbl;
	}
	
	public void linkWithEyepiece(ArrayList<com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext> prapcons) {
		ArrayList<String> studyUIDSet = new ArrayList<String>();
		ArrayList<String> seriesUIDSet = new ArrayList<String>();
		ArrayList<String[]> sopUIDSet = new ArrayList<String[]>();
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
