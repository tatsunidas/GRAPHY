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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import com.vis.core.log.Log;
import com.vis.core.ui.MissingIcon;
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
	JSplitPane birdsEyeSplit; // Thumbnail and filmAndSingleGridSplit  
	JSplitPane filmAndSingleGridSplit; 
	JPanel filmGridPane;
	JPanel singleGridPane;
	DatabaseHandler db;
	String currentStudyUID;
	String currentSeriesUID;
	
	HashMap<String,String> pInfoMap;
	
	Dimension lastSingleGridViewSize = new Dimension(0, 0);
	
	public static final int thumbnailSize = 64 + 24;//88
	
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
		filmAndSingleGridSplit.setDividerLocation(650);
		
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
		
		PropertyChangeListener pcl_thumb = new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				String propertyName = evt.getPropertyName();
				if (propertyName.equals(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY)) {
					keepDividerInPlace();
				}
			}
		};
				
		birdsEyeSplit.addPropertyChangeListener(pcl_thumb);
		
		add(patInfoAndBirdsEyeSplit, BorderLayout.CENTER);
		db = DatabaseHandler.getInstance();
	}
	
	public void ignoreRepaintAllSlides(boolean ignore) {
		HashMap<Integer, SlideGlass> slides1 = filmGridView.getAllSlides();
		if(slides1 != null && slides1.size() > 0) {
			for(Integer key : slides1.keySet()) {
				slides1.get(key).setIgnoreRepaint(ignore);
			}
		}
		HashMap<Integer, SlideGlass> slides2 = singleGridView.getAllSlides();
		if(slides2 != null && slides2.size() > 0) {
			for(Integer key : slides1.keySet()) {
				slides2.get(key).setIgnoreRepaint(ignore);
			}
		}
		List<Praparat> thumbs = seriesListView.getAllThumbnails();
		if(thumbs != null) {
			for(Praparat pp : thumbs) {
				HashMap<Integer, SlideGlass> slides3 = pp.getAllSlides();
				if(slides3 != null && slides3.size() > 0) {
					for(Integer key : slides3.keySet()) {
						slides3.get(key).setIgnoreRepaint(ignore);
					}
				}
			}
		}
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
		
		Insets ins = seriesListView.getInsets();
		birdsEyeSplit.setDividerLocation(thumbnailSize+ins.top+ins.bottom);
		int w = filmAndSingleGridSplit.getWidth();
		filmAndSingleGridSplit.setDividerLocation(w-(int)(w/3));
		revalidate();
		repaint();
	}
	
	private void waitingFilmGridView() {
		if(filmGridPane == null) {
			return;
		}
		filmGridPane.remove(0);
		filmGridPane.add(waitingPanel1);
	}
	
	private void keepDividerInPlace() {
		int h = birdsEyeSplit.getLastDividerLocation();
		Insets ins = seriesListView.getInsets();
		int gap = seriesListView.getVGap();
//		System.out.println("divider loc : "+h);
		/*
		 * Originally, twice the Gap would be correct. However, the size does not match.
		 * Maybe it is due to the Insets relationship of the component. Here, we adjust
		 * the size by 4 times.
		 */
		if(h < thumbnailSize+ins.top+ins.bottom+gap*4) {
			birdsEyeSplit.setDividerLocation(thumbnailSize+ins.top+ins.bottom+gap*4);
			birdsEyeSplit.repaint();
//			System.out.println("new divider size "+(thumbnailSize+ins.top+ins.bottom+gap*4));
//			System.out.println("series list view size:"+seriesListView.getHeight());
		}
	}
	
	public void setPatientInfo(HashMap<String,String> infoset) {
		this.pInfoMap = infoset;
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
			logger.fine("BirdsEyeView:showImages::Does not allow patId null or studyUid null. return.");
			return;
		}
		ArrayList<String> allSeriesUIDList = db.getSeriesUidList(patId,studyUid);
//		ArrayList<String> allInstUIDList = db.getAllInstanceUIDsFromSTUDY(studyUid);
		
		resetViews(false/*clearPatientInfo*/);
		
		currentStudyUID = studyUid;
		
		if(selectedSeriesUIDs == null || selectedSeriesUIDs.size()==0) {
			currentSeriesUID = allSeriesUIDList.get(0);
		}else {
			currentSeriesUID = selectedSeriesUIDs.get(0);
		}
		
		HashMap<String,String> infoset = db.getInfoset(patId, currentStudyUID, currentSeriesUID);
		setPatientInfo(infoset);

		/*
		 * thumbnails: praparat list holder
		 */
		//load all series in study
		for(String series : allSeriesUIDList) {
			//add thumbnails
			ArrayList<String> sopUidsInSeries = db.getInstanceUidList(patId, studyUid, series);
			if(sopUidsInSeries != null && sopUidsInSeries.size() > 0) {
				Praparat th = new Praparat(ViewMode.Thumbnail);
				String[] sopUids = sopUidsInSeries.toArray(new String[sopUidsInSeries.size()]);
				th.prepareSlideGlasses(patId, studyUid, series, sopUids);
				th.setTextVisible(false);
				th.setAnnotationVisible(false);
				th.doSingleGridLayout();
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
		boolean isMultiFrame = thumbnail.isMultiFrame();
		boolean isPDF = thumbnail.isPDF();
		currentSeriesUID = (String)thumbnail.getUIDs()[2];
		thumbnail.getCurrentSlide().setCursor(new Cursor(Cursor.WAIT_CURSOR));
		seriesListView.setCursor(new Cursor(Cursor.WAIT_CURSOR));
		/*
		 * single grid view
		 * load all images
		 */
		if(!isMultiFrame && !isPDF) {
			singleGridView.prepareSlideGlasses(thumbnail);
		}else {
			HashMap<String, Object> info = thumbnail.getInfoSet();
			String padId = (String)info.get(thumbnail.KEY_PadID);
			String studyUid = (String)info.get(thumbnail.KEY_StudyUID);
			String seriesUid = (String)info.get(thumbnail.KEY_SeriesUID);
			ArrayList<String> sopUidsInSeries = db.getInstanceUidList(padId, studyUid, seriesUid);
			String[] sopUids = sopUidsInSeries.toArray(new String[sopUidsInSeries.size()]);
			singleGridView.prepareSlideGlasses(padId, studyUid, seriesUid, sopUids);
		}
		singleGridView.doSingleGridLayout();
		singleGridView.showFirstImage();
		//after set first image
		singleGridView.getController().showInfoText(false);
		singleGridView.setTextVisible(false);
		setSingleGridView();
		
		/*
		 * film grid view
		 * Exclude MultiFrame
		 */
		//show same series in single grid view
//		if(!isMultiFrame && !isPDF && singleGridView.getNumberOfImages() > 1) {
		if(!isMultiFrame) {
			filmGridView.prepareSlideGlasses(thumbnail);
			filmGridView.gridViewOn(true);//fail safe
			filmGridView.doFilmGridLayout(null);
			filmGridView.setTextVisible(false);
			filmGridView.setAnnotationVisible(false);
			setFilmGridView();
		}else {
			waitingFilmGridView();
		}
		birdsEyeSplit.setDividerLocation(thumbnailSize);
		seriesListView.highlightSelectedThumbnail(currentSeriesUID);
		/*SlideGlass has set cross-hair cursor as default*/
		thumbnail.getCurrentSlide().setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		seriesListView.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
	}
	
	public void highlightSelectedImages(ArrayList<String> selectedSopUIDsInItsSeriesOnTreeTable) {
		if(selectedSopUIDsInItsSeriesOnTreeTable == null || selectedSopUIDsInItsSeriesOnTreeTable.size() == 0) {
			return;
		}
		//show top slide at selectedSopUIDsInItsSeries.get(0)
		HashMap<Integer,SlideGlass> slides = singleGridView.getAllSlides();
		Set<Integer> keys = slides.keySet();
		for(int i : keys) {
			SlideGlass sg = slides.get(i);
			if(sg.getSOPInstanceUID().equals(selectedSopUIDsInItsSeriesOnTreeTable.get(0))) {
				singleGridView.setImagePositionUsingSlider(i);
				break;
			}
		}
		slides = filmGridView.getAllSlides();
		if(slides == null) {
			return;//if series only have an one image, slides will null.
		}
		keys = slides.keySet();
		for(int i : keys) {
			SlideGlass sg = slides.get(i);
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
		//escape, such as separated new series.
		if(seriesListView.numOfThumbnails() != allSeriesUIDList.size()) {
			showImages(patId, studyUid, selectedSeriesUIDs, selectedSopUIDs);
			return;
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
	
	public HashMap<String, String> getPatientInfo() {
		return pInfoMap;
	}
	
	class ThumbnailListView extends JScrollPane{
		JPanel seriesListPanel;
		private ThumbnailListView() {
			seriesListPanel = new JPanel();
			seriesListPanel.setLayout(new FlowLayout(FlowLayout.LEADING));
			setViewportView(seriesListPanel);
		}
		
		void addSeries(Praparat praparat) {
			if(praparat == null) {
				JLabel l = new JLabel(new MissingIcon(Color.RED, Praparat.ThumbnailSize, Praparat.ThumbnailSize));
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
		
		List<Praparat> getAllThumbnails(){
			Component[] thums = seriesListPanel.getComponents();
			List<Praparat> list = new ArrayList<>();
			for(Component c : thums) {
				if(c instanceof Praparat) {
					Praparat pp = (Praparat)c;
					list.add(pp);
				}
			}
			if(list.isEmpty()) {
				return null;
			}else {
				return list;
			}
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
		
		int numOfThumbnails() {
			return seriesListPanel.getComponents().length;
		}
		
		int getVGap() {
			FlowLayout l = (FlowLayout)seriesListPanel.getLayout();
			return l.getVgap();
		}
		
		int getPanelHeight() {
			return seriesListPanel.getHeight();
		}
	}	
}
