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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.vis.core.log.Log;

/**
 * 
 * @author tatsunidas
 *
 */
public class PraparatShelf {
	
	ArrayList<PraparatContext> praparats = null;
	
	public PraparatShelf() {
		praparats = new ArrayList<PraparatContext>();
	}
	
	public void addPraparat(Praparat pp) {
		Object[] uids = pp.getUIDs();
		addPraparat((String)uids[0], (String)uids[1], (String)uids[2], (String[])uids[3], (String)uids[4], pp);
	}
	
	private void addPraparat(String patID,String studyUID,String seriesUID,String[] sopUIDs, String refUID, Praparat pp) {
		if(patID==null || studyUID==null || seriesUID==null || sopUIDs==null || refUID==null || pp==null) {
			Log.logger.warning("UIDs do not allow null.");
			return;
		}
		//add new or replace
		PraparatContext con = new PraparatContext(pp, patID, studyUID, seriesUID, sopUIDs, refUID);
		//if exists, return.
		if(praparats.size() < 1) {
			praparats.add(con);
		}else {
			boolean exists = false;
			for (PraparatContext ppcon : praparats) {
				if (ppcon.equals(con)) {
					exists = true;
					break;
				}
			}
			if (!exists) {
				praparats.add(con);
			}
		}
	}
	
	/*
	 * remove prap and pracon from prapshelf if match completely.
	 */
	public void removePraparat(String patID, String studyUID,String seriesUID,String[] sopUIDs) {
		Log.logger.fine("pre "+praparats.size());
		praparats.remove(getPraparatContext(patID, studyUID, seriesUID, sopUIDs));
		Log.logger.fine("post "+praparats.size());
	}
	
	public void removePraparat(Praparat pp) {
		Object[] uids = pp.getUIDs();
		removePraparat((String)uids[0], (String)uids[1], (String)uids[2], (String[])uids[3]);
	}
	
	public Praparat getPraparat(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		for(PraparatContext pcon : praparats) {
			if(pcon.equals(patID, studyUID, seriesUID, sopUIDs)) {
				return pcon.getPraparat();
			}
		}
		return null;
	}
	
	public void updatePraparatContext(Praparat prevPrap, Praparat newPrap) {
		Object[] uids = prevPrap.getUIDs();
		String patID = (String)uids[0];
		String studyUID = (String)uids[1];
		String seriesUID = (String)uids[2];
		String[] sopUIDs = (String[])uids[3];
		for(PraparatContext pcon : praparats) {
			if(pcon.equals(patID, studyUID, seriesUID, sopUIDs)) {
				Object[] uids_ = newPrap.getUIDs();
				String patID_ = (String)uids_[0];
				String studyUID_ = (String)uids_[1];
				String seriesUID_ = (String)uids_[2];
				String[] sopUIDs_ = (String[])uids_[3];
				String refUID_ = (String)uids_[4];
				pcon = new PraparatContext(newPrap, patID_, studyUID_, seriesUID_, sopUIDs_, refUID_);
				break;
			}
		}
	}
	
	public PraparatContext getPraparatContext(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		for(PraparatContext pcon : praparats) {
			if(pcon.equals(patID, studyUID, seriesUID, sopUIDs)) {
				return pcon;
			}
		}
		return null;
	}
	
	public int howManyPraparat() {
		return praparats.size();
	}
	
	public ArrayList<PraparatContext> getAllShelfContents(){
		return praparats;
	}
	
	/**
	 * perticular study && perticular series && perticular images
	 * @author tatsu
	 *
	 */
	public class PraparatContext {
		Praparat pp = null;
		String patID = null;
		String studyUID = null;
		String seriesUID = null;
		List<String> sopUIDs = null;//array
		String frameOfReferenceUID = null;//(0020,0052)
		
		private PraparatContext(Praparat pp, String patID, String studyUID, String seriesUID, String[] sopUIDs, String frameOfReferenceUID) {
			if(pp == null || patID == null || studyUID == null || seriesUID == null || sopUIDs == null || frameOfReferenceUID == null) {
				return;
			}
			this.pp = pp;
			this.patID = patID;
			this.studyUID = studyUID;
			this.seriesUID = seriesUID;
			if(sopUIDs != null) {
				this.sopUIDs = Arrays.asList(sopUIDs);//array
			}
			this.frameOfReferenceUID = frameOfReferenceUID;
		}
		
		public void updateContext(String[] sopUIDs, String frameOfReferenceUID) {
			if(sopUIDs == null || frameOfReferenceUID == null) {
				return;
			}
			this.sopUIDs = Arrays.asList(sopUIDs);
			this.frameOfReferenceUID = frameOfReferenceUID;
			getPraparat().reloadSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
		}
		
		public Object[] getContextUIDs() {
			Object[] context = new Object[5];
			context[0] = patID;
			context[1] = studyUID;
			context[2] = seriesUID;
			if(sopUIDs != null) {
				context[3] = sopUIDs.toArray(new String[sopUIDs.size()]);
			}else {
				context[3] = null;//array
			}
			context[4] = frameOfReferenceUID;
			return context;
		}
		
		public Praparat getPraparat() {
			return this.pp;
		}
		
		@Override
		public boolean equals(Object pcon) {
			if(pcon instanceof PraparatContext) {
				PraparatContext pcon_ = (PraparatContext)pcon;
				String patID = (String)pcon_.getContextUIDs()[0];
				String studyUID = (String)pcon_.getContextUIDs()[1];
				String seriesUID = (String)pcon_.getContextUIDs()[2];
				Object sopUIDs = pcon_.getContextUIDs()[3];//array
				if(this.patID.equals(patID) && this.studyUID.equals(studyUID) && this.seriesUID.equals(seriesUID)) {
					if(this.sopUIDs != null && sopUIDs != null) {
						String[] sopUIDsArray = (String[])sopUIDs;
						List<String> soplist = Arrays.asList(sopUIDsArray);
						Collections.sort(soplist);
						Collections.sort(this.sopUIDs);
						if(this.sopUIDs.equals(soplist)) {
							return true;
						}
					}else if(this.sopUIDs == null && sopUIDs == null) {
						return true;
					}
				}
			}
			return false;
		}
		
		public boolean equals(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
			if (this.patID.equals(patID) && this.studyUID.equals(studyUID) && this.seriesUID.equals(seriesUID)) {
				if (this.sopUIDs != null && sopUIDs != null) {
					String[] sopUIDsArray = (String[]) sopUIDs;
					List<String> soplist = Arrays.asList(sopUIDsArray);
					Collections.sort(soplist);
					Collections.sort(this.sopUIDs);
					if (this.sopUIDs.equals(soplist)) {
						return true;
					}
				}else if(this.sopUIDs == null && sopUIDs == null) {
					return true;
				}
			}
			return false;
		}
	}
}
