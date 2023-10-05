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
import java.awt.FlowLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import com.vis.core.ui.MissingIcon;

@SuppressWarnings("serial")
public class BirdsEyeView extends JPanel{
	
	ThumbnailListView seriesListView;
	JPanel tileView;
	JPanel praparatView;
	PatientInfoPanel pInfo;
	JSplitPane patInfoAndBirdsEyeSplit;
	JSplitPane birdsEyeSplit;//Thumbnail and tileAndPrapatSplit  
	JSplitPane tileAndPrapatSplit; 
	
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
		
		tileAndPrapatSplit = new JSplitPane();
		tileAndPrapatSplit.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
		
		birdsEyeSplit.setLeftComponent(seriesListView);
		birdsEyeSplit.setRightComponent(tileAndPrapatSplit);
		
		tileView = new JPanel();
		tileView.setLayout(new BorderLayout());
		//split-right
		praparatView = new JPanel();
		praparatView.setLayout(new BorderLayout());
		
		tileAndPrapatSplit.setLeftComponent(tileView);
		tileAndPrapatSplit.setRightComponent(praparatView);
		tileAndPrapatSplit.setOneTouchExpandable(true);
		tileAndPrapatSplit.setDividerLocation(700);
		
		add(patInfoAndBirdsEyeSplit, BorderLayout.CENTER);
	}
	
	public void addSeries(Object praparat) {
		seriesListView.addSeries(praparat);
	}
	
	public void testSeriesList() {
		for(int i=0; i<3; i++) {
			addSeries(null);
		}
	}
	
	class ThumbnailListView extends JScrollPane{
		JPanel seriesListPanel;
		private ThumbnailListView() {
			seriesListPanel = new JPanel();
			seriesListPanel.setLayout(new FlowLayout(FlowLayout.LEADING));
			setViewportView(seriesListPanel);
		}
		
		void addSeries(Object praparat) {
			//TODO
			if(praparat == null) {
				JLabel l = new JLabel(new MissingIcon(Color.blue, 30, 30));
				seriesListPanel.add(l);
			}
		}
		
	}
	
}
