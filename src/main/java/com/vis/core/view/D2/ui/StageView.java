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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JDialog;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.ToolBarUI;
import javax.swing.plaf.basic.BasicToolBarUI;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.PraparatShelf;

/**
 * Manage Eyepiece(study level) and PatientInfoCake
 * 
 * @author tatsunidas
 *
 */
public class StageView extends JToolBar/*floatable*/ implements AncestorListener, ContainerListener{
	
	private static final long serialVersionUID = 2374161851214606166L;

	//contents
	private JSplitPane cakeAndEye;
	private PatientInfoCake cake;
	private Eyepiece eye;
	//praparat context
//	private ArrayList<PraparatShelf.PraparatContext> praps;
	final HashMap<String, String> patInfoSet;

	public StageView(HashMap<String, String> patInfoSet) {
		setLayout(new BorderLayout());
		this.patInfoSet = patInfoSet;
		addAncestorListener(this);
		addContainerListener(this);
		constructStage();
	}
		
	public void addPraparatOnEye(String patID, String studyUID, String seriesUID, String[] sopUIDs, String refUID) {
		if(eye == null) {
			return;
		}
		eye.addPraparat(patID, studyUID, seriesUID, sopUIDs, refUID);
		eye.autoLayout();
		updateInfoCake();
	}
	
	@Override
	public void ancestorAdded(AncestorEvent arg0) {
		/*
		 * ((BasicToolBarUI) toolbar.getUI()).setFloatingLocation(300, 200);
		 * ((BasicToolBarUI) toolbar.getUI()).setFloating(true, null);//こちらを使ったほうがいいかも
		 */
		StageDockManager sdm = Viewer2DScreen.getInstance().getStageDockManager();
		String patID = patInfoSet.get("PatientID");
		if(sdm == null || sdm.getComponentCount()<0) {
			return;
		}
		if (SwingUtilities.getWindowAncestor(this) == Viewer2DScreen.getInstance()) {
			// tab icon and name rebuildStageView currentStage = (StageView) sdm.getComponentAt(sdm.getCurrentTabIndex());
			int selectedTabInd = sdm.getCurrentTabIndex();
			if(selectedTabInd == -1) {
				// nobody docking
				int pos = 0;
				sdm.setTabComponentAt(pos, sdm.buildTabComponent(patID));//tab tag component!
				sdm.setSelectedIndex(pos);//show top in dock
				sdm.revalidate();
				sdm.repaint();
				return;
			}
			StageView currentStage = (StageView) sdm.getComponentAt(selectedTabInd);
			if(currentStage == null) {
				return;
			}
			ToolBarUI ui = currentStage.getUI();
			boolean floating = ui instanceof BasicToolBarUI && ((BasicToolBarUI) ui).isFloating();			
			if(!floating) {
				System.out.println("...StageDock still stay in dock:"+ " "+patID);
				int pos = sdm.getTabPosition(patID);
				sdm.setTabComponentAt(pos, sdm.buildTabComponent(patID));//tab tag component!
				sdm.setSelectedIndex(sdm.lastSelectedTabIndex);//show top in dock
				sdm.revalidate();
				sdm.repaint();
			}else {
				System.out.println("...StageDock make a homeward voyage:"+ " "+patID);
				//when re-docking, add tab at last.
//				int pos = sdm.getComponentCount()-1;//too many ?
				int pos = sdm.getTabCount()-1;//get last tab pos
//				sdm.setTitleAt(pos, patID);
				sdm.setTabComponentAt(pos, sdm.buildTabComponent(patID));//tab tag component!
				sdm.setSelectedIndex(pos);//show top in dock
				sdm.revalidate();
				sdm.repaint();
			}
			
		} else {
			Log.logger.fine(patID+" StageDock is floating...");
			Viewer2DScreen.getInstance().setStageIDInAction(patID);
			Component win = SwingUtilities.getWindowAncestor(this);
			if (win instanceof JDialog) {
				/* OK */
				JDialog floatingFrame = (JDialog) SwingUtilities.getWindowAncestor(this);
				if(!floatingFrame.isResizable()) {
					floatingFrame.setResizable(true);
				}
				floatingFrame.addComponentListener(new FloatingDialogWindowListener());
				floatingFrame.setName(patID);
				floatingFrame.setTitle(patID);
				int w = sdm.getWidth();
				int h = sdm.getHeight();
//				System.out.println(w +" "+h);
				if(w < 100) {
					w = 150;
				}
				if(h < 100) {
					h = 150;
				}
				// to avoid floating dialog minimize */
				floatingFrame.setPreferredSize(new Dimension(w, h));
				floatingFrame.setBounds(sdm.getLocationOnScreen().x+10, sdm.getLocationOnScreen().y+10, w, h);
				floatingFrame.revalidate();
				floatingFrame.repaint();
				sdm.revalidate();
				sdm.repaint();
			}
		}
	}
	
	@Override
	public void ancestorMoved(AncestorEvent arg0) {
//		System.out.println("stage view moved");
	}
	
	@Override
	public void ancestorRemoved(AncestorEvent arg0) {}
	
	@Override
	public void componentAdded(ContainerEvent e) {
		Log.logger.fine("added !!!");
	}
		
	@Override
	public void componentRemoved(ContainerEvent e) {
		Log.logger.fine("removed !!!");
	}
		
	private void constructStage() {
		if(cakeAndEye != null) {
			removeAll();
		}
		cakeAndEye = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		cakeAndEye.setOneTouchExpandable(true);
		add(cakeAndEye, BorderLayout.CENTER);
		initDataInfoCake();
		initEyepiece();
	}
	
	/**
	 * see also "ancestorAdded"
	 */
	public void endFloating() {
		BasicToolBarUI ui = (BasicToolBarUI) getUI();
		ui.setFloating(false, null);
	}

	public ArrayList<Object[]> getAllPraparatContextInfoSet(){
		/*
		 * PraparatContextは、表示中の画像セットをグループ情報としてまとめたもの。
		 * 一つのスタディ、一つのシリーズ、それに付随するインスタンスセットをまとめている。
		 */
		ArrayList<PraparatShelf.PraparatContext> praps = eye.getAllPraparatContext();
		ArrayList<Object[]> praparatInfoSet = new ArrayList<>();
		for(PraparatShelf.PraparatContext prap:praps) {
			Object uids[] = prap.getContextUIDs();
			praparatInfoSet.add(uids);
		}
		return praparatInfoSet;
	}
	
	public Eyepiece getEyepiece() {
		return eye;
	}
	
	public HashMap<String, String> getPatientInfo() {
		return patInfoSet;
	}
	
	private void initDataInfoCake() {
		// intit DataInfoCake
		cake = new PatientInfoCake(patInfoSet);
		cakeAndEye.setLeftComponent(cake);
	}
	
	public void initEyepiece() {
		//init Eyepiece 
		eye = new Eyepiece(patInfoSet.get("PatientID"));
		cakeAndEye.setRightComponent(eye);
	}
	
	public boolean isFloating() {
		BasicToolBarUI ui = (BasicToolBarUI) getUI();
		return ui.isFloating();
	}
	
	public void removePraparatFromEye(Praparat pp) {
		if(eye == null) {
			return;
		}
		eye.removePraparat(pp);
		eye.autoLayout();
		updateInfoCake();
	}

	public void removePraparatFromEye(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
		if(eye == null) {
			return;
		}
		eye.removePraparat(patID,studyUID,seriesUID,sopUIDs);
		eye.autoLayout();
		updateInfoCake();
	}

	public void removeSelectedAllPraparatFromEye() {
		if(eye == null) {
			return;
		}
		eye.removeSelectedPraparats();
		eye.autoLayout();
		updateInfoCake();
	}

	/**
	 * see also "ancestorAdded"
	 * @param location : floating dialog location
	 */
	public void startFloating(Point location) {
		BasicToolBarUI ui = (BasicToolBarUI) getUI();
		if (location == null) {
			Point p = getParent().getLocationOnScreen();
			ui.setFloating(true, new Point(p.x + 50, p.y + 50));
		} else {
			ui.setFloating(true, new Point(location.x, location.y));
		}
	}

	public void updateInfoCake() {
		cake.linkWithEyepiece(eye.getAllPraparatContext());
		cake.repaint();
	}

	public void updatePraparatOnEye(Praparat target, String patID, String studyUID, String seriesUID, String[] sopUIDs, String refUID) {
		if(eye == null) {
			return;
		}
		eye.updatePraparat(target, patID, studyUID, seriesUID, sopUIDs, refUID);
		updateInfoCake();
	}
}
