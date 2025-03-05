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

import java.util.ArrayList;

import javax.swing.JOptionPane;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.DBUtils;

public class SeriesSeparator {
	
	private ArrayList<DICOMNode> selected;
	
	public SeriesSeparator() {}
	
	public void separateSeries() {
		ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
		separateSeries(selected);
	}
	
	public void separateSeries(ArrayList<DICOMNode> selected) {
		if(!isSeparateReady(selected)) {
			JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "Not ready to separate. Please select images from a series on HOME TreeTable.");
			return;
		}
		this.selected = selected;
		//show custom popup
		int res = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Do you want to separate series current selected images ?");
		if(res == JOptionPane.OK_OPTION) {
			separate();
			int res2 = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Do you want to delete selected images from current series ?");
			if(res2 == JOptionPane.OK_OPTION) {
				delete();
			}
		}
	}
	
	private boolean isSeparateReady(ArrayList<DICOMNode> selected) {
		if(selected == null || selected.size() < 1) {
			return false;
		}
		//ref uids
		String pid = null;
		String studyUID = null;
		String seriesUID = null;
		for(DICOMNode node : selected) {
			if(node.getLevel() != DICOMNode.IMAGE) {
				return false;
			}
			if(pid == null && studyUID == null && seriesUID == null) {
				pid = node.getData(DICOMNode.PatientID);
				studyUID = node.getData(DICOMNode.StudyInstanceUID);
				seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
			}
			//avoid something strange.
			if(pid == null || studyUID == null || seriesUID == null) {
				return false;
			}
			//contamination check
			String pidChi = node.getData(DICOMNode.PatientID);
			String studyUIDChi = node.getData(DICOMNode.StudyInstanceUID);
			String seriesUIDChi = node.getData(DICOMNode.SeriesInstanceUID);
			if(pidChi == null || studyUIDChi == null || seriesUIDChi == null) {
				return false;
			}
			if(!pid.equals(pidChi) || !studyUID.equals(studyUIDChi) || !seriesUID.equals(seriesUIDChi)) {
				return false;
			}
		}
		return true;
	}
	
	private void separate() {
		String pid = selected.get(0).getData(DICOMNode.PatientID);
		String studyUID = selected.get(0).getData(DICOMNode.StudyInstanceUID);
		String newSeriesUID = DBUtils.createNewUIDNoExistingInDB("SERIES");
		DicomDuplicator.duplicateImageAndStore2DB(selected, pid, studyUID,
				newSeriesUID, true /*setNewInstanceUID*/);
	}

	private void delete() {
		DeleteImage.deleteImages(selected);
	}
}
