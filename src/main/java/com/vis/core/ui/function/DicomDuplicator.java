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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.io.FileUtils;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.DBUtils;
import com.vis.core.util.DateUtils;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.VR;
import com.vis.dicom.dimse.DimseUtilities;
import com.vis.imageio.PixelDataDecoder;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;

public class DicomDuplicator {
	
	public DicomDuplicator() {}
	
	/**
	 * create study as new patient all UIDs are primary-key, so not allow same id
	 * already existing in DB. all UIDs are replaced.
	 * 
	 * @param noDuplicatedImages
	 * @param patInfoMap
	 * @param setNewInstanceUID
	 */
	public static void updatePatientInformationAndStore2DB(ArrayList<String[]> noDuplicatedImages,
			HashMap<String, String> patInfoMap) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			return;
		}
		if (noDuplicatedImages == null || noDuplicatedImages.size() < 1) {
			return;
		}
		if (patInfoMap == null) {
			return;
		}

		Path tempParent = null;
		try {
			tempParent = Files.createTempDirectory(null);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Cannot create TempDir to create duplicate dcm files.");
			return;
		}
		File tempDir = tempParent.toFile();
		
		DICOMBackend backend = null;
		try {
			backend = DICOMBackend.getCurrent();
		} catch (Exception e1) {
			backend = DICOMBackend.DCM4CHE;
		}
		
		// collect studyUIDs
		String[] studyUIDs = getNoDuplicatedIDs(noDuplicatedImages, "STUDY");
		for (String studyUID : studyUIDs) {
			String newStudyUID = DBUtils.createNewUIDNoExistingInDB("STUDY");
			//collect seriesUIDs
			String[] seriesUIDs = getNoDuplicatedIDs(noDuplicatedImages, "SERIES");
			for (String seriesUID : seriesUIDs) {
				String newSeriesUID = DBUtils.createNewUIDNoExistingInDB("SERIES");
				for (String[] idset : noDuplicatedImages) {
					if (idset[1].equals(studyUID) && idset[2].equals(seriesUID)) {
						String newSopInstUID = DBUtils.createNewUIDNoExistingInDB("IMAGE");
						// org
						//String pid = idset[0];
						String sopUID = idset[3];

						String orgPath = db.getFileLocation(studyUID, seriesUID, sopUID);
						DicomReader dr = DicomReader.newDicomReader(backend); 
						dr.read(orgPath, true);// with pixel
						
						DicomObject orgDcm = dr.getCore();
						String tsUID = dr.checkTSUID().uid();

						String newPID = patInfoMap.get("PatientID").trim();
						String newPNAME = patInfoMap.get("PatientName").trim();
						String newBOD = patInfoMap.get("PatientBirthDate").trim().replace("/", "");
						String newSex = patInfoMap.get("PatientSex").trim();
						
						orgDcm.setString(Tag.Patient​ID, VR.LO, newPID);
						orgDcm.setString(Tag.Patient​Name, VR.PN, newPNAME);
						orgDcm.setDate(Tag.Patient​Birth​Date, VR.DA, DateUtils.toSQLDateObj(newBOD));
						orgDcm.setString(Tag.Patient​Sex, VR.CS, newSex);
						orgDcm.setString(Tag.Study​Instance​UID, VR.UI, newStudyUID);
						orgDcm.setString(Tag.Series​Instance​UID, VR.UI, newSeriesUID);
						orgDcm.setString(Tag.SOP​Instance​UID, VR.UI, newSopInstUID);
						orgDcm.setString(Tag.Media​Storage​SOP​Instance​UID, VR.UI, newSopInstUID);
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
						
					} else {
						continue;
					}
				}
			}
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
			DicomObject orgDcm = dr.getCore();
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
			e.printStackTrace();
			System.out.println("Cannot create TempDir to create duplicate dcm files.");
			return;
		}
		File tempDir = tempParent.toFile();
		
		for (String orgPath : imagePaths) {
			DicomReader dr = DicomReader.newDicomReader(null);
			dr.read(orgPath, true);
			DicomObject orgDcm = dr.getCore();
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
	public static void createNewSeriesAndStore2DB(Praparat prap, boolean secondaryCapture) throws Exception {
		if (prap == null) {
			throw new IllegalArgumentException("duplicate target is null");	
		}
		Object pidAndUIDs[] = prap.getUIDs();
		String pid = (String) pidAndUIDs[0];
		String studyUID = (String) pidAndUIDs[1];
		String seriesUID = (String) pidAndUIDs[2];
		String[] sopUIDs = (String[]) pidAndUIDs[3];
		String frameRefUID = (String) pidAndUIDs[4];
		/*
		 * duplicator needs these uids...
		 */
		if (pid == null || studyUID == null) {
			Log.logger.warning("DicomDuplicator:can not find pid or stuyUID, can not create dcm, return.");
			return;
		}

		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) return;
		
		/*
		 * create duplicate to temp folder.
		 */
		Path tempParent = null;
		try {
			tempParent = Files.createTempDirectory(new File(ConfigInfo.getPath(ConfigInfo.TemporalDirName)).getAbsolutePath());
		} catch (IOException e1) {
			e1.printStackTrace();
			Log.logger.severe("Can not create temp folder, sorry stop duplicate...");
			return;
		}
		File destDir = tempParent.toFile();
		
		/*
		 * create stacked imageplus using all slides.
		 */
		HashMap<Integer, SlideGlass> slides = prap.getAllSlides();
		Integer sampleKey = slides.keySet().iterator().next();
		ImagePlus sample = slides.get(sampleKey).getOriginalImage();
		Calibration cal = sample.getCalibration().copy();
		ImageStack newStack = new ImageStack(sample.getWidth(), sample.getHeight());
		Set<Integer> keys = slides.keySet();
		for (int readNo : keys) {
			ImagePlus impInSlide = slides.get(readNo).getOriginalImage();
			newStack.addSlice(impInSlide.getProcessor());
		}
		ImagePlus newImages = new ImagePlus("dup", newStack);
		newImages.setCalibration(cal);
		if (prap.isMultiFrame()) {
			/*
			 * multiframe have only one sopUID(all frames sharing one sopUID in prap).
			 */
			String newSeriesUID = DBUtils.createNewUIDNoExistingInDB("series");
			String newSopInstUID = DBUtils.createNewUIDNoExistingInDB("image");
			String orgPath = db.getFileLocation(studyUID, seriesUID, sopUIDs[0]);
			DicomReader reader = DicomReader.newDicomReader(null);
			reader.read(orgPath, false);
			DicomObject header = reader.getCore();
			PixelDataDecoder deco = new PixelDataDecoder();
			byte[] pixels = deco.pixel2Byte(newImages);
			VR vr = VR.OB;
			header.setValue(Tag.Pixel​Data, vr,pixels);
			if(secondaryCapture) {
				header.setString(Tag.SOP​Class​UID, VR.UI, UID.SecondaryCaptureImageStorage.uid());
			}
			
			//Series number
			int numOfSeries = db.getNumOfSeries(pid, studyUID);
			if(numOfSeries == -1) {
				numOfSeries = 1;
			}else {
				numOfSeries += 1;
			}
			header.setString(Tag.Series​Number, VR.IS, numOfSeries+"");
			
			// change seriesUID and instUID
			header.setString(Tag.Series​Instance​UID, VR.UI, newSeriesUID);
			header.setString(Tag.SOP​Instance​UID, VR.UI, newSopInstUID);
			header.setString(Tag.Media​Storage​SOP​Instance​UID, VR.UI, newSopInstUID);
			/*
			 * write
			 */
			final File dest = new File(destDir.getAbsolutePath());
			DicomWriter.newDicomWriter().write(header, UID.ImplicitVRLittleEndian.uid(),dest.getAbsolutePath() + File.separator + "dup_" + sopUIDs[0]);
			/*
			 * send to graphy refresh table load image
			 */
			DimseUtilities.sendFile(
					new File(dest.getAbsolutePath() + File.separator + "dup_" + sopUIDs[0] + ".dcm"));
			
			// delete temp files
			try {
				FileUtils.deleteDirectory(destDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
			// re-open
			Viewer2DScreen viewer2d = Viewer2DScreen.getInstance();
			if (viewer2d != null && viewer2d.isVisible()) {
				viewer2d.loadImagesOnStage(pid, studyUID, newSeriesUID, new String[] { newSopInstUID },
						frameRefUID);
			}
		// single frame images
		} else {
			String newSeriesUID = DBUtils.createNewUIDNoExistingInDB("series");
			//Series number
			int numOfSeries = db.getNumOfSeries(pid, studyUID);
			if(numOfSeries == -1) {
				numOfSeries = 1;
			}else {
				numOfSeries += 1;
			}
			List<String> newSopUIDs = new ArrayList<>();
			for (Integer key : keys) {
				SlideGlass sg = slides.get(key);
				ImagePlus imp = sg.getOriginalImage();
				PixelDataDecoder deco = new PixelDataDecoder();
				byte[] pixels = deco.pixel2Byte(imp);
				DicomObject core = sg.getDicomImage().getCore();
				core.setString(Tag.Series​Number, VR.IS, numOfSeries+"");
				core.setValue(Tag.Pixel​Data, VR.OB, pixels);
				// change seriesUID and instUID
				core.setString(Tag.Series​Instance​UID, VR.UI, newSeriesUID);
				String newSopInstUID = DBUtils.createNewUIDNoExistingInDB("image");
				core.setString(Tag.SOP​Instance​UID, VR.UI, newSopInstUID);
				core.setString(Tag.Media​Storage​SOP​Instance​UID, VR.UI, newSopInstUID);
				newSopUIDs.add(newSopInstUID);
				final File dest = new File(destDir.getAbsolutePath());
				DicomWriter.newDicomWriter().write(core, UID.ImplicitVRLittleEndian.uid(), dest.getAbsolutePath() + File.separator + "DUP_" + newSopInstUID);
				/*
				 * send to graphy and refresh table
				 */
				DimseUtilities.sendMe(destDir.listFiles());
				// delete after send
				try {
					FileUtils.deleteDirectory(destDir);
				} catch (IOException e) {
					e.printStackTrace();
				}
				// re-open
				Viewer2DScreen viewer2d = Viewer2DScreen.getInstance();
				if (viewer2d != null && viewer2d.isVisible()) {
					viewer2d.loadImagesOnStage(pid, studyUID, newSeriesUID,
							newSopUIDs.toArray(new String[newSopUIDs.size()]), frameRefUID);
				}
			}
		}
	}

	
	/**
	 * 
	 * @param instanceUIDSets: array of [pid, studyUid, seriesUid, sopUid]
	 * @param dcmLevel: patient study series image
	 * @return
	 */
	static String[] getNoDuplicatedIDs(ArrayList<String[]> instanceUIDSets, String dcmLevel) {
		dcmLevel = dcmLevel.toLowerCase();
		HashSet<String> ids = new HashSet<>();
		// idset:pid,studyuid,seriesuid,sopuid
		for (String[] idset : instanceUIDSets) {
			if (dcmLevel.equals("patient")) {
				ids.add(idset[0]);
			} else if (dcmLevel.equals("study")) {
				ids.add(idset[1]);
			} else if (dcmLevel.equals("series")) {
				ids.add(idset[2]);
			} else if (dcmLevel.equals("image")) {
				ids.add(idset[3]);
			}
		}
		return ids.toArray(new String[ids.size()]);
	}

}
