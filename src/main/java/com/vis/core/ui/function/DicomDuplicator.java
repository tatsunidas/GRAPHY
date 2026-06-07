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

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.DBUtils;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.VR;
import com.vis.dicom.dimse.DimseUtilities;
import com.vis.dicom.image.DicomImage;

/**
 * 
 * @author tatsunidas
 */
public class DicomDuplicator {
	
	/**
	 * You should use...
	 * 
	 * Praparat reslice = win.getPraparatAt(CutSurface.OBLIQUE); 
	 * HashMap<Integer,DicomImage> dcmImages = reslice.getDicomImages(); 
	 * if(dcmImages == null) {JOptionPane.showMessageDialog(this, "Dicom images are empty.\nDo reslice
	 * first."); return; } 
	 * db.storeDicomImagesToDb(dcmImages);
	 */
	
	public DicomDuplicator() {}
	
	/**
	 * 
	 * @param noDuplicatedImages
	 * @param patInfoMap
	 * @param setNewInstanceUID
	 */
	@Deprecated
	public static void updatePatientInformationAndStore2DB(ArrayList<String[]> noDuplicatedImages,
			HashMap<String, String> patInfoMap) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			return;
		}
		db.updatePatientInformationAndStore2DB(noDuplicatedImages, patInfoMap);
	}
	

	/**
	 * create new series
	 * 
	 * @param imageNodes
	 * @param newPID
	 * @param newStudyUID
	 * @param newSeriesUID
	 * @param setNewInstanceUID
	 */
	public static void duplicateImageAndStore2DB(ArrayList<DICOMNode> imageNodes, String newPID, String newStudyUID,
			String newSeriesUID, boolean setNewInstanceUID) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			return;
		}
		if (imageNodes == null || imageNodes.size() < 1) {
			return;
		}

		Path tempParent = null;
		try {
			tempParent = Files.createTempDirectory(null);
		} catch (IOException e) {
			Log.logger.log(Level.SEVERE,"Cannot create TempDir to create duplicate dcm files.");
			e.printStackTrace();
			return;
		}
		File tempDir = tempParent.toFile();
		
		DICOMBackend backend = null;
		try {
			backend = DICOMBackend.getCurrent();
		} catch (Exception e1) {
			backend = DICOMBackend.DCM4CHE;
		}
		
		for (DICOMNode node : imageNodes) {
			// org
			//String pid = node.getData(DICOMNode.PatientID);
			String studyUID = node.getData(DICOMNode.StudyInstanceUID);
			String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
			String sopUID = node.getData(DICOMNode.SOPInstanceUID);

			String orgPath = db.getFileLocation(studyUID, seriesUID, sopUID);
			DicomReader dr = DicomReader.newDicomReader(backend);
			dr.read(orgPath, true);
			DicomObject orgDcm = dr.getHeader();
			String tsUID = dr.checkTSUID().uid();
			
			// change IDs
			orgDcm.setString(Tag.Patient​ID, VR.LO, newPID);
			orgDcm.setString(Tag.Study​Instance​UID, VR.UI, newStudyUID);
			orgDcm.setString(Tag.Series​Instance​UID, VR.UI, newSeriesUID);
			if (setNewInstanceUID) {
				String newSopInstUID = DBUtils.createNewUIDNoExistingInDB("image");
				orgDcm.setString(Tag.SOP​Instance​UID, VR.UI, newSopInstUID);
				orgDcm.setString(Tag.Media​Storage​SOP​Instance​UID, VR.UI, newSopInstUID);
			}
			
			/*
			 * write
			 */
			String dest = tempDir.getAbsolutePath() + File.separator + "dup_" + sopUID + ".dcm";
			DicomWriter writer = DicomWriter.newDicomWriter(backend);
			writer.write(orgDcm,  tsUID, dest);
			/*
			 * send to graphy refresh table load image
			 */
			DimseUtilities.store(dest, false/*deleteAfterStored*/);
		}
		
		try {
			FileUtils.deleteDirectory(tempDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Window win = WindowManager.getMainScreen();
		if(win !=null) {
			MainScreen main = (MainScreen) win;
			main.loadLocalStudiesBySearchKey();
		}
	}
	
	public static void duplicateImageAndStore2DB(String[] imagePaths, String newPID, String newStudyUID,
			String newSeriesUID, boolean setNewInstanceUID) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			return;
		}
		if (imagePaths == null || imagePaths.length < 1) {
			return;
		}

		Path tempParent = null;
		try {
			tempParent = Files.createTempDirectory(null);
		} catch (IOException e) {
			Log.logger.severe("Cannot create TempDir to create duplicate dcm files.");
			e.printStackTrace();
			return;
		}
		File tempDir = tempParent.toFile();
		
		for (String orgPath : imagePaths) {
			DicomReader dr = DicomReader.newDicomReader(null);
			dr.read(orgPath, true);
			DicomObject orgDcm = dr.getHeader();
			String tsUID = dr.checkTSUID().uid();
			
			// change IDs
			orgDcm.setString(Tag.Patient​ID, VR.LO, newPID);
			orgDcm.setString(Tag.Study​Instance​UID, VR.UI, newStudyUID);
			orgDcm.setString(Tag.Series​Instance​UID, VR.UI, newSeriesUID);
			String newSopInstUID = orgDcm.getString(Tag.SOP​Instance​UID);
			if (setNewInstanceUID) {
				newSopInstUID = DBUtils.createNewUIDNoExistingInDB("image");
				orgDcm.setString(Tag.SOP​Instance​UID, VR.UI, newSopInstUID);
				orgDcm.setString(Tag.Media​Storage​SOP​Instance​UID, VR.UI, newSopInstUID);
			}
			
			/*
			 * write
			 */
			String dest = tempDir.getAbsolutePath() + File.separator + newSopInstUID + ".dcm";
			DicomWriter writer = DicomWriter.newDicomWriter(null);
			writer.write(orgDcm,  tsUID, dest);
			/*
			 * send to graphy refresh table load image
			 */
			DimseUtilities.store(dest, false/*deleteAfterStored*/);
		}
		
		try {
			FileUtils.deleteDirectory(tempDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Window win = WindowManager.getMainScreen();
		if(win !=null) {
			MainScreen main = (MainScreen) win;
			main.loadLocalStudiesBySearchKey();
		}
	}

	/**
	 * "Save as new series" function in Viewer2D
	 * @param prap
	 * @param secondaryCapture
	 * @throws Exception
	 */
	@Deprecated
	public static void createNewSeriesAndStore2DB(Praparat prap, boolean secondaryCapture) throws Exception {
		if (prap == null) {
			throw new IllegalArgumentException("duplicate target is null");
		}
		DatabaseHandler db = DatabaseHandler.getInstance();
		HashMap<Integer,DicomImage> dcmImages = prap.getDicomImages();
		if(dcmImages == null) {
			Log.logger.warning("DicomDuplicator: DICOM images are empty.");
			JOptionPane.showMessageDialog(null, Resources.i18n("DicomDuplicator.error.emptyImages"), Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		db.storeDicomImagesToDb(dcmImages);
	}

}
