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
package com.vis.core.ui.main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.MissingIcon;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.db.DatabaseHandler;

@SuppressWarnings("serial")
public class BirdsEyeView extends JPanel{
	
	ThumbnailListView seriesListView;
	Praparat filmGridView;
	Praparat singleGridView;
	PatientInfoPanel pInfo;
	JPanel waitingPanel1;
	JPanel waitingPanel2;
	JSplitPane patInfoAndBirdsEyeSplit;
	JSplitPane birdsEyeSplit;//Thumbnail and filmAndSingleGridSplit  
	JSplitPane filmAndSingleGridSplit; 
	JPanel filmGridPane;
	JPanel singleGridPane;
	DatabaseHandler db = DatabaseHandler.getInstance();
	String currentStudyUID;
	String currentSeriesUID;
	
	final int thumbnailSize = 64 + 24;
	
	Logger logger = Log.logger;
	
	public BirdsEyeView() {
		initContents();
	}
	
	void initContents() {
		setLayout(new BorderLayout());
		
		patInfoAndBirdsEyeSplit = new JSplitPane();
		patInfoAndBirdsEyeSplit.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
		patInfoAndBirdsEyeSplit.setOneTouchExpandable(true);
		
		pInfo = new PatientInfoPanel();
		
		birdsEyeSplit = new JSplitPane();
		birdsEyeSplit.setOrientation(JSplitPane.VERTICAL_SPLIT);
		
		patInfoAndBirdsEyeSplit.setLeftComponent(pInfo);
		patInfoAndBirdsEyeSplit.setRightComponent(birdsEyeSplit);
		
		seriesListView = new ThumbnailListView();
		
		filmAndSingleGridSplit = new JSplitPane();
		filmAndSingleGridSplit.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
		
		birdsEyeSplit.setLeftComponent(seriesListView);
		birdsEyeSplit.setRightComponent(filmAndSingleGridSplit);
		
		filmAndSingleGridSplit.setOneTouchExpandable(true);
		filmAndSingleGridSplit.setDividerLocation(700);
		
		filmGridPane = new JPanel(new GridLayout(1, 1));
		singleGridPane = new JPanel(new GridLayout(1, 1));
		
		waitingPanel1 = new JPanel();
		waitingPanel1.setBackground(Color.BLACK);
		waitingPanel2 = new JPanel();
		waitingPanel2.setBackground(Color.BLACK);
		
		filmGridPane.add(waitingPanel1);
		singleGridPane.add(waitingPanel2);
		
		filmAndSingleGridSplit.setLeftComponent(filmGridPane);
		filmAndSingleGridSplit.setRightComponent(singleGridPane);
		
		add(patInfoAndBirdsEyeSplit, BorderLayout.CENTER);
	}
	
	/**
	 * clear info and all views
	 */
	public void resetViews(boolean clearPatientInfo) {
		if(clearPatientInfo) {
			clearPatientInfo();
			currentStudyUID = null;
			currentSeriesUID = null;
		}
		
		currentSeriesUID = null;
		
		//clear thumbnails
		seriesListView.removeAllThumbnails();
		
		//clear filmgirid and singlegrid
		filmGridView = new Praparat(Praparat.ViewMode.FilmGrid);		
		filmGridView.gridViewOn(true);//fail safe
		singleGridView = new Praparat(Praparat.ViewMode.SingleGrid);
		
		filmGridPane.remove(0);
		singleGridPane.remove(0);
		
		filmGridPane.add(waitingPanel1);
		singleGridPane.add(waitingPanel2);
		
		birdsEyeSplit.setDividerLocation(thumbnailSize);
		int w = filmAndSingleGridSplit.getWidth();
		filmAndSingleGridSplit.setDividerLocation(w-(int)(w/3));
		revalidate();
		repaint();
	}
	
	public void setPatientInfo(HashMap<String,String> infoset) {
		pInfo.setInfoset(infoset);
		repaint();
	}
	
	public void setFilmGridView() {
		if(filmAndSingleGridSplit == null || filmGridView == null) {
			return;
		}
		Component showingCom = filmGridPane.getComponent(0);
		if(showingCom == waitingPanel1) {
			filmGridPane.remove(showingCom);
		}
		if(showingCom != filmGridView) {
			filmGridPane.add(filmGridView);
		}
		if(filmGridPane.isVisible()) {
			filmGridPane.revalidate();
			filmGridPane.repaint();
		}
	}
	
	public void setSingleGridView() {
		if(filmAndSingleGridSplit == null || singleGridView == null) {
			return;
		}
		Component showingCom = singleGridPane.getComponent(0);
		if(showingCom == waitingPanel2) {
			singleGridPane.remove(showingCom);
		}
		if(showingCom != singleGridView) {
			singleGridPane.add(singleGridView);
		}
		if(singleGridPane.isVisible()) {
			singleGridPane.revalidate();
			singleGridPane.repaint();
		}
	}
	
	public void clearPatientInfo() {
		pInfo.clear();
		repaint();
	}
	
	/**
	 * Able to load only one study.
	 * @param patId
	 * @param studyUid
	 * @param selectedSeriesUIDs : selected series in it's study on treetable
	 * @param selectedSopUIDs : selected images in series in it's study on treetable
	 */
	public void showImages(String patId, String studyUid, ArrayList<String> selectedSeriesUIDs/*nullable*/, HashMap<String, ArrayList<String>> selectedSopUIDs/*nullable*/) {
		if(db == null) {
			db = DatabaseHandler.getInstance();
		}
		if(patId == null || studyUid == null) {
			if(Utils.isDebug) {
				logger.info("BirdsEyeView:showImages::Does not allow patId null or studyUid null. return.");
			}
			return;
		}
		ArrayList<String> allSeriesUIDList = db.getSeriesUidList(patId,studyUid);
//		ArrayList<String> allInstUIDList = db.getAllInstanceUIDsFromSTUDY(studyUid);
		
		resetViews(false);
		
		currentStudyUID = studyUid;
		
		if(selectedSeriesUIDs == null || selectedSeriesUIDs.size()==0) {
			currentSeriesUID = allSeriesUIDList.get(0);
		}else {
			currentSeriesUID = selectedSeriesUIDs.get(0);
		}
		
		DatabaseHandler db = DatabaseHandler.getInstance();
		HashMap<String,String> infoset = db.getInfoset(patId, currentStudyUID, currentSeriesUID);
		setPatientInfo(infoset);
		
		ArrayList<String> sopUidsInSeries = db.getInstanceUidList(patId, studyUid, currentSeriesUID);
//		ArrayList<String> instLocsInSeries = db.getInstancesLoc(studyUid, currentSeriesUID);
		
		/*
		 * thumbnails: praparat list holder
		 */
		//load all series in study
		for(String series : allSeriesUIDList) {
			//add thumbnails
			sopUidsInSeries = db.getInstanceUidList(patId, studyUid, series);
			if(sopUidsInSeries != null && sopUidsInSeries.size() > 0) {
				Praparat th = new Praparat(ViewMode.Thumbnail);
				String[] sopUids = sopUidsInSeries.toArray(new String[sopUidsInSeries.size()]);
				th.prepareSlideGlasses(patId, studyUid, series, sopUids);
				th.setTextVisible(false);
				th.setAnnotationVisible(false);
				th.initImageSizeAndShowFirstImage();
				addSeries(th);
			}else {
				addSeries(null);
			}
			if(series.equals(currentSeriesUID)) {
				seriesListView.highlightSelectedThumbnail(currentSeriesUID);
			}
		}
		showImagesFromThumbnailAction(seriesListView.getThumbnail(currentSeriesUID));
		highlightSelectedImages(selectedSopUIDs.get(currentSeriesUID));
		birdsEyeSplit.setDividerLocation(thumbnailSize);
	}
	
	/**
	 * Must use after showImages()
	 * @param thumbnail
	 */
	public void showImagesFromThumbnailAction(Praparat thumbnail){
		if(thumbnail == null) {
			return;
		}
		
		HashMap<String, Object> infoset = thumbnail.getInfoSet();
		String patId = (String)infoset.get("PatientID");
		String studyUid = (String)infoset.get("StudyInstanceUID");
		String seriesUid = (String)infoset.get("SeriesInstanceUID");
		currentSeriesUID = seriesUid;
		String[] sopUidsInSeries = (String[])infoset.get("SOPInstanceUIDs");
		ArrayList<String> instLocsInSeries = db.getInstancesLoc(studyUid, currentSeriesUID);
		
		/*
		 * single grid view
		 * load all images
		 */
		//set series to single grid
		singleGridView.setInfo(patId, studyUid, currentSeriesUID, sopUidsInSeries, instLocsInSeries);
		singleGridView.loadSlideGlasses(thumbnail.getAllSlides());
		singleGridView.initImageSizeAndShowFirstImage();
		//after set first image
		singleGridView.getController().showInfoText(false);
		singleGridView.setTextVisible(false);
		setSingleGridView();
		
		/*
		 * film grid view
		 * if series includes only one image, does not show self.
		 */
		//show same series in single grid view
		if(sopUidsInSeries.length != 1) {
//			filmGridView.prepareSlideGlasses(patId, studyUid, currentSeriesUID, sopUidsInSeries.toArray(new String[sopUidsInSeries.size()]), instLocsInSeries);
			//sharing slides with singlegridview
			filmGridView.setInfo(patId, studyUid, currentSeriesUID, sopUidsInSeries, instLocsInSeries);
			filmGridView.loadSlideGlasses(seriesListView.getThumbnail(currentSeriesUID).getAllSlides());
			filmGridView.gridViewOn(true);//fail safe
			filmGridView.doFilmGridLayout(5);
			filmGridView.setTextVisible(false);
			filmGridView.setAnnotationVisible(false);
			setFilmGridView();
		}
		birdsEyeSplit.setDividerLocation(thumbnailSize);
		seriesListView.highlightSelectedThumbnail(currentSeriesUID);
	}
	
	public void highlightSelectedImages(ArrayList<String> selectedSopUIDsInItsSeriesOnTreeTable) {
		if(selectedSopUIDsInItsSeriesOnTreeTable == null || selectedSopUIDsInItsSeriesOnTreeTable.size() == 0) {
			return;
		}
		//show top slide at selectedSopUIDsInItsSeries.get(0)
		HashMap<Integer,JLayer<SlideGlass>> slides = singleGridView.getAllSlides();
		Set<Integer> keys = slides.keySet();
		for(int i : keys) {
			SlideGlass sg = slides.get(i).getView();
			if(sg.getSOPInstanceUID().equals(selectedSopUIDsInItsSeriesOnTreeTable.get(0))) {
				singleGridView.setImagePositionUsingSlider(i);
				break;
			}
		}
		slides = filmGridView.getAllSlides();
		keys = slides.keySet();
		for(int i : keys) {
			SlideGlass sg = slides.get(i).getView();
			for(String uid : selectedSopUIDsInItsSeriesOnTreeTable) {
				if(sg.getSOPInstanceUID().equals(uid)) {
					sg.setSelectionState(true);
				}else {
//					sg.setSelectionState(false);//remain already selected
				}
			}
		}
	}
	
	public void updateViews(String patId,String studyUid, ArrayList<String> selectedSeriesUIDs/*nullable*/, HashMap<String, ArrayList<String>> selectedSopUIDs) {
		if(!currentStudyUID.equals(studyUid)){
			return;
		}
		ArrayList<String> allSeriesUIDList = db.getSeriesUidList(patId,studyUid);
		if(selectedSeriesUIDs == null || selectedSeriesUIDs.size()==0) {
			if(selectedSopUIDs != null && selectedSopUIDs.size()>0) {
				currentSeriesUID = selectedSopUIDs.keySet().iterator().next();
			}else {
				currentSeriesUID = allSeriesUIDList.get(0);
			}
		}else {
			currentSeriesUID = selectedSeriesUIDs.get(0);
		}
		showImagesFromThumbnailAction(seriesListView.getThumbnail(currentSeriesUID));
		highlightSelectedImages(selectedSopUIDs.get(currentSeriesUID));
	}
	
	public void addSeries(Object praparat) {
		seriesListView.addSeries((Praparat)praparat);
	}
	
	public String getShowingStudyUID() {
		return currentStudyUID;
	}
	
	class ThumbnailListView extends JScrollPane{
		JPanel seriesListPanel;
		private ThumbnailListView() {
			seriesListPanel = new JPanel();
			seriesListPanel.setLayout(new FlowLayout(FlowLayout.LEADING));
			setViewportView(seriesListPanel);
		}
		
		void addSeries(Praparat praparat) {
			//TODO
			if(praparat == null) {
				JLabel l = new JLabel(new MissingIcon(Color.RED, 64, 64));
				seriesListPanel.add(l);
			}else {
				seriesListPanel.add(praparat);
			}
		}
		
		void removeAllThumbnails() {
			Component[] thums = seriesListPanel.getComponents();
			for(Component c : thums) {
				if(c instanceof Praparat) {
					seriesListPanel.remove(c);
				}
			}
		}
		
		Praparat getThumbnail(String seriesUID) {
			Component[] thums = seriesListPanel.getComponents();
			for(Component c : thums) {
				if(c instanceof Praparat) {
					Praparat pp = (Praparat)c;
					if(pp.getInfoSet().get("SeriesInstanceUID").equals(seriesUID)) {
						return pp;
					}
				}
			}
			return null;
		}
		
		void highlightSelectedThumbnail(String seriesUID) {
			Component[] thums = seriesListPanel.getComponents();
			if(thums == null) {
				return;
			}
			for(Component c : thums) {
				if(c instanceof Praparat) {
					Praparat pp = (Praparat)c;
					if(pp.getInfoSet().get("SeriesInstanceUID").equals(seriesUID)) {
						pp.setSelectionState(true);
					}else {
						pp.setSelectionState(false);
					}
				}
			}
		}
	}	
}
