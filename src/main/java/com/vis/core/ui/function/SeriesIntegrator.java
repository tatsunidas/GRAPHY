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

package com.vis.core.ui.function;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.db.DatabaseHandler;

/**
 * @author tatsunidas
 */
public class SeriesIntegrator{
	
	private ArrayList<DICOMNode> selected;//same patient only.
	private JComboBox<String> destSeriesList;
	private String destSeriesKey;
	private HashMap<String, DICOMNode> seriesMap;
	
	public SeriesIntegrator() {}
	
	public void integrateSeries() {
		ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
		integrateSeries(selected);
	}
	
	public void integrateSeries(ArrayList<DICOMNode> selectedNodes) {
		
		this.selected = selectedNodes;
		if(!isReady()) {
			return;
		}
		buildDestSeriesCombo();
		//show custom popup
		if(showCustomPopup() == JOptionPane.OK_OPTION) {
			destSeriesKey = (String) destSeriesList.getSelectedItem();
			//do integrate
			integrate();
		}
	}
	
	private boolean isReady() {
		if(selected == null || selected.size() < 1) {
			return false;
		}
		//if including nor series or image level, return false.
		for(DICOMNode node : selected) {
			if(node.getLevel() != DICOMNode.SERIES && node.getLevel() != DICOMNode.IMAGE) {
				JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "Can not interate. Please select series/images.");
				return false;
			}
		}
		//if only including a series, return false
		HashMap<String/*SeriesUID*/, DICOMNode> series = new HashMap<String/*SeriesUID*/, DICOMNode>();
		for(DICOMNode node : selected) {
			String seUID = node.getData(DICOMNode.SeriesInstanceUID);
			if(seUID == null) {
				Log.logger.fine("This dcm file does not have SeriesUID, can not handle to integration.");
				return false;
			}
			series.put(seUID, node);
		}
		if(series.isEmpty() || series.size()==1) {
			PopUpMessage.showDialog(WindowManager.getMainScreen(), "Can not interate", "Please select 2 or more series.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
		}
		//if not same patient, return false
		Set<String> keys = series.keySet();
		for(String k : keys) {
			DICOMNode n = series.get(k);
			String pid = n.getData(DICOMNode.PatientID);
			if(pid == null) pid = "NULL";
			for(String k2 : keys) {
				DICOMNode n2 = series.get(k2);
				if(n == n2) {
					continue;
				}
				String pid2 = n2.getData(DICOMNode.PatientID);
				if(pid2 == null) pid2 = "NULL";
				if(!pid.equals(pid2)) {
					PopUpMessage.showDialog(WindowManager.getMainScreen(), "Can not interate", "Selected dataset contains multiple patients.\nIF you want to do that, edit patient info first(modifiy to same patient).", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
					return false;
				}
			}
		}
		
		
		seriesMap = new HashMap<String/*Series name*/, DICOMNode>();
		
		for(DICOMNode node : selected) {
			if(node.getLevel() == DICOMNode.SERIES) {
				String pid = node.getData(DICOMNode.PatientID);
				String date = node.getData(DICOMNode.SeriesDate);
				String desc = node.getData(DICOMNode.SeriesDescription);
				String seUID = node.getData(DICOMNode.SeriesInstanceUID);
				seriesMap.put(pid+"_"+date+"_"+desc+"_"+seUID, node);
			}
		}
		return true;
	}
	
	private void buildDestSeriesCombo() {
		/*
		 * key : pid + study date + series desc + series uid
		 * value : node
		 */
		String[] keys = new String[seriesMap.size()];
		int itr = 0;
		for(String k : seriesMap.keySet()) {
			String name = k;//.substring(0, k.lastIndexOf("_"));
			keys[itr++] = name;
		}
		destSeriesList = new JComboBox<>(keys);
		destSeriesList.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				destSeriesKey = (String) destSeriesList.getSelectedItem();
			}
		});
	}
		
	private JPanel constructConfirmPanel() {
		JPanel p = new JPanel();
		p.setLayout(new java.awt.BorderLayout());
		JLabel l1 = new JLabel("Do integrate to...");
		p.add(l1, java.awt.BorderLayout.NORTH);
		p.add(destSeriesList, java.awt.BorderLayout.SOUTH);
		return p;
	}

	private int showCustomPopup() {
		 return JOptionPane.showConfirmDialog(null,constructConfirmPanel(),"Select integration destination...", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
	}
	
	private void integrate() {
		DICOMNode destNode = seriesMap.get(destSeriesKey);
		String refPid = destNode.getData(DICOMNode.PatientID);
		String refStudyUID = destNode.getData(DICOMNode.StudyInstanceUID);
		String refSeriesUID = destNode.getData(DICOMNode.SeriesInstanceUID);
		ArrayList<String> willIntegrate = new ArrayList<>();//file paths
		
		for(DICOMNode node : selected) {
			/*
			 * skip destNode
			 * set destNode's SeriesUID
			 */
			if(node == destNode) {
				continue;
			}
			//fail safe
			String pid = node.getData(DICOMNode.PatientID);
			String studyUID = node.getData(DICOMNode.StudyInstanceUID);
			String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
			DatabaseHandler db = DatabaseHandler.getInstance();
			if(refPid.equals(pid) && refStudyUID.equals(studyUID) && refSeriesUID.equals(seriesUID)) {
				continue;
			}else {
				if(node.getLevel()==DICOMNode.SERIES) {
					ArrayList<String> paths= db.getInstancesLoc(studyUID, seriesUID);
					for(String p:paths) {
						if(!willIntegrate.contains(p)) {
							willIntegrate.add(p);
						}
					}
				}else if(node.getLevel()==DICOMNode.IMAGE) {
					String path = db.getFileLocation(studyUID, seriesUID, node.getData(DICOMNode.SOPInstanceUID));
					if(!willIntegrate.contains(path)) {
						willIntegrate.add(path);
					}
				}
			}
		}
		DicomDuplicator.duplicateImageAndStore2DB((String[]) willIntegrate.toArray(new String[willIntegrate.size()]), refPid, refStudyUID, refSeriesUID, true);
		int res2 = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Do you want to delete integrated images from current series ?");
		if(res2 == JOptionPane.OK_OPTION) {
			DeleteImage.deleteImagesByFilePath(willIntegrate);
		}
	}
}
