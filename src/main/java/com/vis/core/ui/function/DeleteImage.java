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

import java.sql.SQLException;
import java.util.ArrayList;

import javax.swing.JOptionPane;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomReader;
import com.vis.dicom.Tag;

/**
 * @author tatsunidas
 */

public class DeleteImage {

	public static void deleteImages(ArrayList<DICOMNode> nodeList){
		if(nodeList == null || nodeList.size() <1) {
			Log.logger.info("Please select node from TreeTable.");
			PopUpMessage.showDialog(MainScreen.getInstance(), "Files not selected.", "Please select files to delete.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		ArrayList<String[]> deleteList = WindowManager.getMainScreen().getLocalTreeTable().createNoDuplicateImageList(nodeList);
		deleteImages(deleteList, true);
	}
	
	public static void deleteImages(ArrayList<String[]> deleteInstList, boolean dummy){
		if(deleteInstList == null || deleteInstList.size() <1) {
			return;
		}
		for(String[] infoSet : deleteInstList) {
			//0:pid,1:studyuid,2:seriesuid,3:sopuid,4:path2img
			String patID = infoSet[0];
			String studyUID = infoSet[1];
			String seriesUID = infoSet[2];
			String sopUID = infoSet[3];
			try {
				DatabaseHandler.getInstance().deleteInstance(patID, studyUID, seriesUID, sopUID);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
		WindowManager.getMainScreen().clearPatientInfo();
	}
	
	public static void deleteImagesByFilePath(ArrayList<String> deleteInstFileLocs){
		if( deleteInstFileLocs == null || deleteInstFileLocs.size() <1) {
			return;
		}
		for(String loc: deleteInstFileLocs) {
			//0:pid,1:studyuid,2:seriesuid,3:sopuid,4:path2img
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
			reader.read(loc);
			String patID = reader.getCore().getString(Tag.Patient​ID);
			String studyUID = reader.getCore().getString(Tag.Study​Instance​UID);
			String seriesUID = reader.getCore().getString(Tag.Series​Instance​UID);
			String sopUID = reader.getCore().getString(Tag.SOP​Instance​UID);
			reader = null;
			try {
				DatabaseHandler.getInstance().deleteInstance(patID, studyUID, seriesUID, sopUID);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
		WindowManager.getMainScreen().clearPatientInfo();
	}
}
