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

package com.vis.core.view.D2.ui.glasses;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.view.D2.ui.StageDockManager;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext;
import com.vis.db.DatabaseHandler;

@SuppressWarnings("serial")
public class Eyepiece extends JPanel{
	
	/**
	 * Eyepiece is a StudyManager
	 */
	final String patID;
	DatabaseHandler db = DatabaseHandler.getInstance();
	PraparatShelf prapShelf = null;
	GridLayout gridLayout = new GridLayout();
	HashMap<String, Color> studyColors;
	
	public Eyepiece(String patID) {
		this.patID = patID;
		init();
//		DropTarget dt = new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE,new ImageDropTargetListener());
		new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE,new ImageDropTargetListener());
	}
	
	private void init() {
		prapShelf = new PraparatShelf();
		setLayout(gridLayout);
		studyColors = new HashMap<>();
		ArrayList<String> studyUIDs = db.getStudyUidList(patID);
		int colorInd = 0;
		for(String studyUID : studyUIDs) {
			studyColors.put(studyUID, allocateStudyColor(colorInd));
			colorInd+=10;
		}
	}
	
	/**
	 * If sopUIDs is null, load all instances automatically.
	 * 
	 * @param patID
	 * @param studyUID
	 * @param seriesUID
	 * @param sopUIDs
	 * @param studyColor
	 * @return
	 */
	private Praparat buildPraparat(String patID, String studyUID, String seriesUID,String[] sopUIDs, String refUID) {
		if(seriesUID == null) {
			Log.logger.fine("SeriesUID is NUll, return null.");
			return null;
		}
		//load instances locations
		ArrayList<String> p2images = new ArrayList<String>();
		for(String sopUID:sopUIDs) {
			String p2img = db.getFileLocation(studyUID, seriesUID, sopUID);
			if(p2img != null) {
				p2images.add(p2img);
			}
		}
		Praparat prap = new Praparat(patID, studyUID, seriesUID, sopUIDs, p2images, refUID, this, studyColors.get(studyUID), ViewMode.Normal);
		return prap;
	}
	
	public Praparat getPraparatAt(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		return prapShelf.getPraparat(patID, studyUID, seriesUID, sopUIDs);
	}
	
	public Praparat getPraparatOnEyeAt(java.awt.Point p) {
		Component prap = getComponentAt(p);
		if(prap instanceof Praparat) {
			return (Praparat) prap;
		}else {
			return null;
		}
	}
	
	public void removePraparat(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		if(seriesUID == null) {
			return;
		}
		prapShelf.removePraparat(patID, studyUID, seriesUID, sopUIDs);
	}
	
	public void removePraparat(Praparat pp) {
		prapShelf.removePraparat(pp);
	}
	
	public void removeSelectedPraparats() {
		ArrayList<Praparat> candidates = getSelectingPraparats();
		for(Praparat pp:candidates) {
			removePraparat(pp);
		}
	}
	
	public void addPraparat(String patID, String studyUID, String seriesUID,
			String[] sopUIDs, String refUID) {
		if(patID == null || studyUID == null) {
			return;
		}
		if(seriesUID == null) {
			//load all series to eyepiece
			ArrayList<String> seriesList = db.getSeriesUidList(patID, studyUID);
			for (String seUID : seriesList) {
				//get sopUIDs
				ArrayList<String> sopUIDInSeries = db.getInstanceUidList(patID,studyUID, seUID);
				prapShelf.addPraparat(buildPraparat(patID, studyUID, seUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]), refUID));
			}
		}else {
			//select instances to show
			if(sopUIDs != null) {
				//only show specified instances
				prapShelf.addPraparat(buildPraparat(patID, studyUID, seriesUID, sopUIDs, refUID));
			}else {
				//show all instances in particular series
				ArrayList<String> sopUIDInSeries = db.getInstanceUidList(patID, studyUID, seriesUID);
				prapShelf.addPraparat(buildPraparat(patID, studyUID, seriesUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]), refUID));
			}
		}
	}
	
	public Color allocateStudyColor(int index) {
		ij.process.LUT studyColors = Resources.LUT_FIRE.loadLUT();
 		byte ind = (byte) index;//convert range to -128 ~ 127
		int location = (int)((int)ind + 128);//0 ~ 255
		int r = studyColors.getRed(location);
		int g = studyColors.getGreen(location);
		int b = studyColors.getBlue(location);
		return new Color(r,g,b);
	}
	
	//TODO 20240814
	public void updatePraparat(Praparat prevPrap, String newPatID, String newStudyUID, String newSeriesUID, String[] newSopUIDs, String newRefUID) {
		Praparat pp = buildPraparat(newPatID, newStudyUID, newSeriesUID, newSopUIDs, newRefUID);
		prapShelf.updatePraparatContext(prevPrap, pp);
	}
	
	public ArrayList<Praparat> getPraparatAmbiguously(String patID, String studyUID, String seriesUID) {
		ArrayList<PraparatContext> praps = getAllPraparatContext();
		ArrayList<Praparat> result = new ArrayList<Praparat>();
		for(PraparatContext pcon:praps) {
			Object[] con = pcon.getContextUIDs();
			String patID_ = (String)con[0];
			String studyUID_ = (String)con[1];
			String seriesUID_ = (String)con[2];
			if(patID_.equals(patID) && studyUID_.equals(studyUID) && seriesUID_.equals(seriesUID)) {
				result.add(pcon.getPraparat());
			}
		}
		return result;
	}
	
	public ArrayList<Praparat> getAllPraparatByFrameOfReferenceUID(String patID, String studyUID, String refUID) {
		ArrayList<PraparatContext> praps = getAllPraparatContext();
		ArrayList<Praparat> result = new ArrayList<Praparat>();
		for(PraparatContext pcon:praps) {
			Object[] con = pcon.getContextUIDs();
			String patID_ = (String)con[0];
			String studyUID_ = (String)con[1];
			// String seriesUID_ = (String)con[2];//do not need .
			String frameOfRefUID_ = (String)con[4];
			if(patID_.equals(patID) && studyUID_.equals(studyUID) && frameOfRefUID_.equals(refUID)) {
				result.add(pcon.getPraparat());
			}
		}
		return result;
	}
	
	public void autoLayout() {
		int numOfPrap = prapShelf.howManyPraparat();
		int rows = (int) Math.sqrt(numOfPrap);
		int cols = (int) Math.ceil((double) numOfPrap / rows);
		if(numOfPrap < 1) {
			gridLayout.setRows(1);
			gridLayout.setColumns(1);
			removeAll();
			Viewer2DScreen d2 = Viewer2DScreen.getInstance();
			StageDockManager sdm = d2.getStageDockManager();
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					sdm.deleteStage(patID);
				}
			});
		}else {
			removeAll();
			gridLayout.setRows(rows);
			gridLayout.setColumns(cols);
			for (PraparatContext pcon : prapShelf.getAllShelfContents()) {
				add(pcon.getPraparat());
			}
		}
	}
	
	public ArrayList<Praparat> getSelectingPraparats() {
		ArrayList<Praparat> praps = new ArrayList<Praparat>();
		for(PraparatContext pcon:prapShelf.getAllShelfContents()) {
			Praparat prap = pcon.getPraparat();
			if(prap.isSelected()) {
				praps.add(prap);
			}
		}
		return praps;
	}
	
	public ArrayList<PraparatContext> getAllPraparatContext(){
		return prapShelf.getAllShelfContents();
	}
	
	public PraparatContext getPraparatContextOf(String pid, String studyUID, String seriesUID, String[] sopUIDs) {
		return prapShelf.getPraparatContext(pid, studyUID, seriesUID, sopUIDs);
	}
	
	public void lostAllPraparatFocusGained() {
		ArrayList<PraparatContext> pcons = getAllPraparatContext();
		for(PraparatContext pcon:pcons) {
			Praparat pp = pcon.getPraparat();
			pp.setFocusGained(false);
		}
	}
}
