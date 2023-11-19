package com.vis.core.view.D2.ui.glasses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.vis.core.util.Utils;

public class PraparatShelf {
	
	/*
	 * Manage Praparats by study level.
	 */
	ArrayList<PraparatContext> praparats = null; //praparat shelf
	
	public PraparatShelf() {
		praparats = new ArrayList<PraparatContext>();
	}
	
	public void addPraparat(String patID,String studyUID,String seriesUID,String[] sopUIDs, String refUID, Praparat pp) {
		if(praparats == null) {
			return;
		}
		if(patID==null || studyUID==null || seriesUID==null || sopUIDs==null || refUID==null || pp==null) {
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
	
//	public void addPraparat(Eyepiece manager, String patID, String studyUID,String seriesUID,String[] sopUIDs, ArrayList<String> images) {
//		Praparat pp = new Praparat(images,manager);
//		addPraparat(patID,studyUID,seriesUID,sopUIDs, pp);
//	}
	
	public void updatePraparatContext(Praparat prevPrap, String newPatID,String newStudyUID,String newSeriesUID,String[] newSopUIDs, String newRefUID) {
		if(prevPrap==null || newPatID==null || newStudyUID==null || newSeriesUID==null || newSopUIDs==null || newRefUID==null) {
			return;
		}
		Object[] uids = prevPrap.getUIDs();
		String prevPatID = (String)uids[0];
		String prevStudyUID = (String)uids[1];
		String prevSeriesUID = (String)uids[2];
		String prevSopUIDs[] = (String[])uids[3];
		PraparatContext pcon = getPraparatContext(prevPatID, prevStudyUID, prevSeriesUID, prevSopUIDs);
		if(Utils.isDebug){
			System.out.println("updatePraparatContext, previous: "+pcon.getContextUIDs()[2]);
		}
		pcon.updateContext(newPatID, newStudyUID, newSeriesUID, newSopUIDs, newRefUID);
		//to check
//		for(PraparatContext pcon0:getAllShelfContents()) {
//			System.out.println(pcon0.getContextUIDs()[2]);
//		}
	}
	
	/*
	 * remove prap and pracon from prapshelf if match completely.
	 */
	public void removePraparat(String patID, String studyUID,String seriesUID,String[] sopUIDs) {
//		System.out.println("pre "+praparats.size());
		praparats.remove(getPraparatContext(patID, studyUID, seriesUID, sopUIDs));
		praparats.trimToSize();
//		System.out.println("post "+praparats.size());
	}
	
	public void removePraparat(Praparat pp) {
		Object[] uids = pp.getUIDs();
		removePraparat((String)uids[0], (String)uids[1], (String)uids[2], (String[])uids[3]);
	}
	
	public Praparat getPraparat(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		for(PraparatContext pcon : praparats) {
			if(pcon.match(patID, studyUID, seriesUID, sopUIDs)) {
				return pcon.getPraparat();
			}
		}
		return null;
	}
	
	public PraparatContext getPraparatContext(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		for(PraparatContext pcon : praparats) {
			if(pcon.match(patID, studyUID, seriesUID, sopUIDs)) {
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
		
		public void updateContext(String patID, String studyUID, String seriesUID, String[] sopUIDs, String frameOfReferenceUID) {
			if(patID == null || studyUID == null || seriesUID == null || sopUIDs == null || frameOfReferenceUID == null) {
				return;
			}
			this.patID = patID;
			this.studyUID = studyUID;
			this.seriesUID = seriesUID;
			if(sopUIDs != null) {
				this.sopUIDs = Arrays.asList(sopUIDs);//array
			}
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
		
		/**
		 * All values required with non null.
		 * @param patID
		 * @param studyUID
		 * @param seriesUID
		 * @param sopUIDs
		 * @return
		 */
		private boolean match(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
			if(patID == null || studyUID == null || seriesUID == null || sopUIDs == null) {
				return false;
			}
			if(this.patID.equals(patID) && this.studyUID.equals(studyUID) && this.seriesUID.equals(seriesUID)) {
				if(this.sopUIDs != null && sopUIDs != null) {
					List<String> soplist = Arrays.asList(sopUIDs);
					if(this.sopUIDs.equals(soplist)) {
						return true;
					}else {
						return false;
					}
				}else {//null of each
					return true;
				}
			}else {
				return false;
			}
		}
		
		//TODO Override equals()
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
						if(this.sopUIDs.equals(soplist)) {
							return true;
						}else {
							return false;
						}
					}else {//null of each
						return true;
					}
				}else {
					return false;
				}
			}else {
				return false;
			}
		}
	}
}
