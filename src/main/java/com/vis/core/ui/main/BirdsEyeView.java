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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import com.vis.core.log.Log;
import com.vis.core.ui.MissingIcon;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.Tag;

/**
 * @author tatsunidas
 */
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
	
	Dimension minSize = new Dimension(64, 64);
	
	// ★追加：現在実行中のレンダリングタスクIDを管理
	private final java.util.concurrent.atomic.AtomicLong renderTaskId = new java.util.concurrent.atomic.AtomicLong(0);
	
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
		
		// splitpaneのために最小サイズを保証
		pInfo.setMinimumSize(minSize);
		birdsEyeSplit.setMinimumSize(minSize);
		
		patInfoAndBirdsEyeSplit.setLeftComponent(pInfo);
		patInfoAndBirdsEyeSplit.setRightComponent(birdsEyeSplit);
		patInfoAndBirdsEyeSplit.setDividerLocation(250);
		patInfoAndBirdsEyeSplit.setResizeWeight(0.0);
		
		seriesListView = new ThumbnailListView();
		
		filmAndSingleGridSplit = new JSplitPane();
		filmAndSingleGridSplit.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
		filmAndSingleGridSplit.setMinimumSize(minSize);
		
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
		
		filmGridPane.setMinimumSize(minSize);
		singleGridPane.setMinimumSize(minSize);
		
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
		ConcurrentHashMap<Integer, SlideGlass> slides1 = filmGridView.getAllSlides();
		if(slides1 != null && slides1.size() > 0) {
			for(Integer key : slides1.keySet()) {
				slides1.get(key).setIgnoreRepaint(ignore);
			}
		}
		ConcurrentHashMap<Integer, SlideGlass> slides2 = singleGridView.getAllSlides();
		if(slides2 != null && slides2.size() > 0) {
			for(Integer key : slides2.keySet()) {
				slides2.get(key).setIgnoreRepaint(ignore);
			}
		}
		List<Praparat> thumbs = seriesListView.getAllThumbnails();
		if(thumbs != null) {
			for(Praparat pp : thumbs) {
				ConcurrentHashMap<Integer, SlideGlass> slides3 = pp.getAllSlides();
				if(slides3 != null && slides3.size() > 0) {
					for(Integer key : slides3.keySet()) {
						slides3.get(key).setIgnoreRepaint(ignore);
					}
				}
			}
		}
	}
	
	public void resetViews(boolean clearPatientInfo) {
		if(clearPatientInfo) {
			clearPatientInfo();
			currentStudyUID = null;
			currentSeriesUID = null;
		}
		
		currentSeriesUID = null;
		
		//clear thumbnails
		seriesListView.removeAllThumbnails();
		
		resetGridPanes();
		
		SwingUtilities.invokeLater(()->{
			birdsEyeSplit.setDividerLocation(getOptimalThumbnailHeight());
		});
		
		int w = filmAndSingleGridSplit.getWidth();
		if (w > 0) { // to avoid zero size initialization error
			filmAndSingleGridSplit.setDividerLocation(w-(int)(w/3));
		}
		revalidate();
		repaint();
	}

	private void waitingFilmGridView() {
		if(filmGridPane == null) {
			return;
		}
		// ★ 修正: 確実にすべて消してから待機パネルを追加する
		filmGridPane.removeAll();
		filmGridPane.add(waitingPanel1);
		filmGridPane.revalidate();
		filmGridPane.repaint();
	}
	
//	private void keepDividerInPlace() {
//		int h = birdsEyeSplit.getLastDividerLocation();
//		Insets ins = seriesListView.getInsets();
//		int gap = seriesListView.getVGap();
////		System.out.println("divider loc : "+h);
//		/*
//		 * Originally, twice the Gap would be correct. However, the size does not match.
//		 * Maybe it is due to the Insets relationship of the component. Here, we adjust
//		 * the size by 4 times.
//		 */
//		if(h < thumbnailSize+ins.top+ins.bottom+gap*4) {
//			birdsEyeSplit.setDividerLocation(thumbnailSize+ins.top+ins.bottom+gap*4);
//			birdsEyeSplit.repaint();
////			System.out.println("new divider size "+(thumbnailSize+ins.top+ins.bottom+gap*4));
////			System.out.println("series list view size:"+seriesListView.getHeight());
//		}
//	}
	
	// BirdsEyeView.java の keepDividerInPlace() メソッド内
	private void keepDividerInPlace() {
	    int h = birdsEyeSplit.getLastDividerLocation();
	    int optimalHeight = getOptimalThumbnailHeight();
	    // 【修正】高さを新しい最適サイズと比較する
	    if(h < optimalHeight) {
	        birdsEyeSplit.setDividerLocation(optimalHeight);
	        birdsEyeSplit.repaint();
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
		
	public void showImages(String patId, String studyUid, ArrayList<String> selectedSeriesUIDs/*nullable*/, HashMap<String, ArrayList<String>> selectedSopUIDs/*nullable*/) {
		// タスクIDを発行
		final long myTaskId = renderTaskId.incrementAndGet();

		if(db == null) {
			db = DatabaseHandler.getInstance();
		}
		if(patId == null || studyUid == null) {
			logger.fine("BirdsEyeView:showImages::Does not allow patId null or studyUid null. return.");
			return;
		}
		ArrayList<String> allSeriesUIDList = db.getSeriesUidList(patId,studyUid);
		
		// ★ 修正 1：invokeAndWait を使って、確実に画面の初期化（リセット）が終わるまで待機させる！
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
				resetViews(false/*clearPatientInfo*/);
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		currentStudyUID = studyUid;
		
		if(selectedSeriesUIDs == null || selectedSeriesUIDs.size()==0) {
			currentSeriesUID = allSeriesUIDList.get(0);
		}else {
			currentSeriesUID = selectedSeriesUIDs.get(0);
		}
		
		HashMap<String,String> infoset = db.getInfoset(patId, currentStudyUID, currentSeriesUID);
		
		javax.swing.SwingUtilities.invokeLater(() -> {
			setPatientInfo(infoset);
		});

		Set<String> addedSeriesUIDs = new HashSet<>();
		
		// 後でグリッドに表示するためのターゲットを保持する変数を用意
		Praparat targetThumbnail = null;

		for(String series : allSeriesUIDList) {
			// タスクキャンセルのガード
			if (myTaskId != renderTaskId.get()) {
				return; 
			}

			if(addedSeriesUIDs.contains(series)) {
				continue;
			}
			addedSeriesUIDs.add(series);
			
			ArrayList<String> sopUidsInSeries = db.getInstanceUidList(patId, studyUid, series);
			Praparat th = null;
			
			if(sopUidsInSeries != null && sopUidsInSeries.size() > 0) {
				th = new Praparat(ViewMode.Thumbnail);
				String[] sopUids = sopUidsInSeries.toArray(new String[sopUidsInSeries.size()]);
				th.loadSeries(patId, studyUid, series, sopUids); 
				th.setTextVisible(false);
				th.setAnnotationVisible(false);
				th.doSingleGridLayout();
				
				// ★ 修正 3：現在選択されているシリーズなら、ここで実体を確保しておく！
				if(series.equals(currentSeriesUID)) {
					targetThumbnail = th;
				}
			}

			final Praparat finalTh = th;
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (myTaskId == renderTaskId.get()) {
					addSeries(finalTh);
					if(series.equals(currentSeriesUID)) {
						seriesListView.highlightSelectedThumbnail(currentSeriesUID);
					}
				}
			});
		}
		
		final Praparat finalTarget = targetThumbnail;
		
		// ループ完走後、確保しておいた実体を使ってグリッドに画像をロードする
		if (myTaskId == renderTaskId.get() && finalTarget != null) {
			
			// ★ 修正 4：UIから探すのではなく、確保した実体を直接渡す！
			// (この処理自体も重いため、バックグラウンドスレッドのまま実行します)
			showImagesFromThumbnailAction(finalTarget);
			
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (myTaskId == renderTaskId.get()) {
					highlightSelectedImages(selectedSopUIDs.get(currentSeriesUID));
					birdsEyeSplit.setDividerLocation(getOptimalThumbnailHeight());
				}
			});
		}else {
			//when canceled, cursor is back to default.
			javax.swing.SwingUtilities.invokeLater(() -> {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                MainScreen.getInstance().setCursor(Cursor.DEFAULT_CURSOR);
            });
		}
	}
	
	/**
	 * Must use after showImages()
	 * @param thumbnail
	 */
	public void showImagesFromThumbnailAction(Praparat thumbnail){
		if(thumbnail == null) {
			return;
		}
		
		// remove all
		try {
			if (SwingUtilities.isEventDispatchThread()) {
				resetGridPanes();
			} else {
				SwingUtilities.invokeAndWait(() -> resetGridPanes());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		boolean isMultiFrame = thumbnail.isMultiFrame();
		boolean isMultiDimensional = thumbnail.isMultiDimensional();
		if (thumbnail.getCurrentSlide() != null) {
			isMultiFrame = isMultiFrame && thumbnail.getCurrentSlide().getHeader().getInt(Tag.Number​Of​Frames, -1) > 1;
		}
		boolean isPDF = thumbnail.isPDF();
		currentSeriesUID = (String)thumbnail.getUIDs()[2];
		if (com.vis.core.ui.main.MainScreen.getInstance() != null) {
			com.vis.core.ui.main.MainScreen.getInstance().setCursor(new Cursor(Cursor.WAIT_CURSOR));
		}
		seriesListView.setCursor(new Cursor(Cursor.WAIT_CURSOR));
		/*
		 * single grid view
		 * load all images
		 */
		if(!isMultiFrame && !isPDF) {
			singleGridView.loadSeries(thumbnail);
		}else {
			HashMap<String, Object> info = thumbnail.getInfoSet();
			String padId = (String)info.get(thumbnail.KEY_PadID);
			String studyUid = (String)info.get(thumbnail.KEY_StudyUID);
			String seriesUid = (String)info.get(thumbnail.KEY_SeriesUID);
			ArrayList<String> sopUidsInSeries = db.getInstanceUidList(padId, studyUid, seriesUid);
			String[] sopUids = sopUidsInSeries.toArray(new String[sopUidsInSeries.size()]);
			singleGridView.loadSeries(padId, studyUid, seriesUid, sopUids);
		}
		// praparat に委ねる
//		singleGridView.doSingleGridLayout();
//		singleGridView.showFirstImage();
		//after set first image
		singleGridView.getController().showInfoText(false);
		singleGridView.setTextVisible(false);
		setSingleGridView();
		
		/*
		 * film grid view
		 * Exclude MultiFrame
		 */
		//show same series in single grid view
		if(!isMultiFrame && !isMultiDimensional && !isPDF) {
			filmGridView.loadSeries(thumbnail);
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
	}
	
	public void highlightSelectedImages(ArrayList<String> selectedSopUIDsInItsSeriesOnTreeTable) {
		if(selectedSopUIDsInItsSeriesOnTreeTable == null || selectedSopUIDsInItsSeriesOnTreeTable.size() == 0) {
			return;
		}
		//show top slide at selectedSopUIDsInItsSeries.get(0)
		ConcurrentHashMap<Integer,SlideGlass> slides = singleGridView.getAllSlides();
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
	}
	
	public void addSeries(Object praparat) {
		seriesListView.addSeries((Praparat)praparat);
	}
	
	/**
	 * サムネイル領域の最適な高さを計算します。
	 * サムネイルサイズ + 余白 + 横スクロールバーの高さ を考慮します。
	 */
	private int getOptimalThumbnailHeight() {
	    Insets ins = seriesListView.getInsets();
	    int gap = seriesListView.getVGap();
	    // JScrollPane の横スクロールバーの標準的な高さを取得(およそ16〜18px)
	    int scrollBarHeight = javax.swing.UIManager.getInt("ScrollBar.width");
	    if (scrollBarHeight == 0) scrollBarHeight = 18; // 取得できない場合のフォールバック
	    
	    // 上下のInsetとGap、スクロールバーの分を合算して返す
	    return thumbnailSize + ins.top + ins.bottom + (gap * 2) + scrollBarHeight;
	}
	
	public String getShowingStudyUID() {
		return currentStudyUID;
	}
	
	public HashMap<String, String> getPatientInfo() {
		return pInfoMap;
	}
	
	public ThumbnailListView getThumbnailListView() {
		return this.seriesListView;
	}
	
	/**
	 * グリッドのインスタンスを確実に破棄して待機状態に戻す
	 */
	private void resetGridPanes() {
		filmGridView = new Praparat(Praparat.ViewMode.FilmGrid);		
		filmGridView.gridViewOn(true);
		singleGridView = new Praparat(Praparat.ViewMode.SingleGrid);
		
		filmGridPane.removeAll();
		singleGridPane.removeAll();
		
		filmGridPane.add(waitingPanel1);
		singleGridPane.add(waitingPanel2);
		
		filmGridPane.revalidate();
		filmGridPane.repaint();
		singleGridPane.revalidate();
		singleGridPane.repaint();
	}
	
	public class ThumbnailListView extends JScrollPane{
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
//			Component[] thums = seriesListPanel.getComponents();
//			for(Component c : thums) {
//				if(c instanceof Praparat) {
//					seriesListPanel.remove(c);
//				}
//			}
			seriesListPanel.removeAll();
			seriesListPanel.revalidate();
			seriesListPanel.repaint();
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
		
		@Override
		public void setCursor(java.awt.Cursor cursor) {
			super.setCursor(cursor);
			seriesListPanel.setCursor(cursor);
			Component[] thums = seriesListPanel.getComponents();
			for(Component c : thums) {
				if(c instanceof Praparat) {
					Praparat pp = (Praparat)c;
					pp.setCursor(cursor);
				}
			}
			repaint();
		}
	}	
}
