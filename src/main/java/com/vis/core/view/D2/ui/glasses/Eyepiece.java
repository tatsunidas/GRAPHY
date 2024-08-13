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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext;
import com.vis.db.DatabaseHandler;

@SuppressWarnings("serial")
public class Eyepiece extends JPanel{
	
	/**
	 * Eyepiece is a StudyManager
	 */
	
	DatabaseHandler db = DatabaseHandler.getInstance();
	PraparatShelf prapShelf = null;
	GridLayout gridLayout;
	//JPanel base ;
	byte goneOutStudyColorPos = 0;

	public Eyepiece(String patID, String studyUID, String seriesUID, String[] sopUIDs, String frameOfRefUID) {
		init();
		addPraparat(patID, studyUID, seriesUID, sopUIDs, frameOfRefUID, allocateStudyColor());
//		DropTarget dt = new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE,new ImageDropTargetListener());
		new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE,new ImageDropTargetListener());
		autoLayout();
	}
	
	private void init() {
		prapShelf = new PraparatShelf();
		gridLayout = new GridLayout(1,1);
		setLayout(gridLayout);
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
	private Praparat buildPraparat(String patID, String studyUID, String seriesUID,String[] sopUIDs, Color studyColor) {
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
		Praparat prap = new Praparat(patID, studyUID, seriesUID, sopUIDs, p2images,this, studyColor,ViewMode.Normal);
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
			String[] sopUIDs, String refUID, Color studyColor) {
		if(patID == null || studyUID == null) {
			return;
		}
		if(seriesUID == null) {
			//load all series to eyepiece
			ArrayList<String> seriesList = db.getSeriesUidList(patID, studyUID);
			for (String seUID : seriesList) {
				//get sopUIDs
				ArrayList<String> sopUIDInSeries = db.getInstanceUidList(patID,studyUID, seUID);
				prapShelf.addPraparat(patID,studyUID,seUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]),refUID,buildPraparat(patID, studyUID, seUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]),studyColor));
			}
		}else {
			//select instances to show
			if(sopUIDs != null) {
				//only show specified instances
				prapShelf.addPraparat(patID,studyUID,seriesUID, sopUIDs,refUID,buildPraparat(patID, studyUID, seriesUID, sopUIDs,studyColor));
			}else {
				//show all instances in particular series
				ArrayList<String> sopUIDInSeries = db.getInstanceUidList(patID, studyUID, seriesUID);
				prapShelf.addPraparat(patID,studyUID,seriesUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]),refUID,buildPraparat(patID, studyUID, seriesUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]),studyColor));
			}
		}
	}
	
	public void updatePraparat(Praparat prevPrap, String newPatID, String newStudyUID, String newSeriesUID, String[] newSopUIDs, String refUID) {
		System.out.println(prevPrap.getUIDs()[2]+"  "+newSeriesUID);
		prapShelf.updatePraparatContext(prevPrap, newPatID, newStudyUID, newSeriesUID, newSopUIDs, refUID);
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
	
	//TODO 20240813
	public void autoLayout() {
		int numOfPrap = prapShelf.howManyPraparat();
		if(numOfPrap > 1) {
			int row = -1;
			int col = -1;
			if(numOfPrap == 2) {
				row = 1;
				col = 2;
			}else if(numOfPrap == 3 || numOfPrap ==4){
				row = 2;
				col = 2;
			}else {
				int s = 3;
				while(s*s < numOfPrap) {
					s = s+ 1;
				}
				row = s;
				col = s;
			}
			gridLayout.setRows(row);
			gridLayout.setColumns(col);
		}else {
			gridLayout.setRows(1);
			gridLayout.setColumns(1);
		}
		
		for(PraparatContext pcon:prapShelf.getAllShelfContents()) {
			add(pcon.getPraparat());
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
	
	public Color allocateStudyColor() {
		ij.process.LUT studyColors = Resources.LUT_FIRE.loadLUT();
		byte index = goneOutStudyColorPos;
		byte increment = 10;
 		index = (byte) (index + increment);//-128 ~ 127
		int location = (int)((int)index + 128);//0 ~ 255
		int r = studyColors.getRed(location);
		int g = studyColors.getGreen(location);
		int b = studyColors.getBlue(location);
		goneOutStudyColorPos = (byte)location;
		return new Color(r,g,b);
	}
}
