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
import java.util.logging.Level;

import org.apache.commons.io.FileUtils;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.DBUtils;
import com.vis.core.util.DateUtils;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.VR;
import com.vis.dicom.dimse.DimseUtilities;

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
		
		DICOMBackend backend = null;
		try {
			backend = DICOMBackend.getCurrent();
		} catch (Exception e1) {
			backend = DICOMBackend.DCM4CHE;
		}
		
		for (String orgPath : imagePaths) {
			DicomReader dr = DicomReader.newDicomReader(backend);
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


	/**
	 * What is this method ? -> See MPR.
	 * 
	 * @param org
	 * @param newObj
	 * @return
	 * @throws Exception 
	 */
	@Deprecated
	public static void createNewSeriesAndStore2DB(Praparat prap, boolean secondaryCapture,
			boolean retainAmbiguousTags) throws Exception {
//		if (prap == null) {
//			return;
//		}
//		if (prap.getImageFileLocations() == null || prap.getImageFileLocations().size() < 1) {
//			// force change
//			secondaryCapture = true;
//		}
//		Object pidAndUIDs[] = prap.getUIDs();
//		String pid = (String) pidAndUIDs[0];
//		String studyUID = (String) pidAndUIDs[1];
//		String seriesUID = (String) pidAndUIDs[2];
//		String[] sopUIDs = (String[]) pidAndUIDs[3];
//		String frameRefUID = (String) pidAndUIDs[4];
//		/*
//		 * duplicator needs these uids...
//		 */
//		if (pid == null || studyUID == null) {
//			System.err.println("DicomDuplicator:can not find pid or stuyUID, can not create dcm, return.");
//			return;
//		}
//
//		/*
//		 * create duplicate to temp folder.
//		 */
//		Path tempParent = null;
//		try {
//			tempParent = Files.createTempDirectory(null);
//		} catch (IOException e1) {
//			e1.printStackTrace();
//			System.err.println("Can not create temp folder, sorry stop duplicate...");
//			return;
//		}
//		File destDir = tempParent.toFile();
//
//		DatabaseHandler db = ApplicationContext.databaseRef;
//		if (db == null)
//			return;
//
//		if (prap.isMultiFrame()) {
//			/*
//			 * create stacked imageplus using all slides(after masked) in prap to create
//			 * multiframe dicom image as one file.
//			 */
//			HashMap<Integer, JLayer<SlideGlass>> slides = prap.getAllSlides();
//			Integer sampleKey = slides.keySet().iterator().next();
//			ImagePlus sample = slides.get(sampleKey).getView().getOriginalImage();
//			Calibration cal = sample.getCalibration().copy();
//			ImageStack newStack = new ImageStack(sample.getWidth(), sample.getHeight());
//			Set<Integer> keys = slides.keySet();
//			for (int readNo : keys) {
//				ImagePlus impInSlide = slides.get(readNo).getView().getOriginalImage();
//				newStack.addSlice(impInSlide.getProcessor());
//			}
//			ImagePlus newImages = new ImagePlus("duplicated", newStack);
//			newImages.setCalibration(cal);
//			// to dcm
//			DicomObject dcm = new DicomImageBuilder().create(newImages);
//			/*
//			 * multiframe have only one sopUID(all frames sharing one sopUID in prap).
//			 */
//			String newSeriesUID = DBUtil.createNewUIDNoExistingInDB("series");
//			String newSopInstUID = DBUtil.createNewUIDNoExistingInDB("image");
//			String orgPath = db.getFileLocation(pid, studyUID, seriesUID, sopUIDs[0]);
//			if (DICOMBackend.isBackend(DICOMBackend.DCM4CHE)) {
//				DicomReader dr = new DicomReader(orgPath, false);
//				Object orgDcm = dr.getCoreDataset();
//				try {
//					if (orgDcm instanceof Attributes) {// handle with dcm4che
//						Attributes orgAttr = (Attributes) orgDcm;
//						Attributes imgAttr = ((DicomObjectDcm4che) dcm.getCore()).getAttributes();
//						orgAttr.addAll(imgAttr);
//						if (secondaryCapture) {
//							if (!retainAmbiguousTags) {
//								deleteAmbiguousTag(orgAttr);
//							}
//							orgAttr.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
//						}
//						// change seriesUID and instUID
//						orgAttr.setString(Tag.SeriesInstanceUID, VR.UI, newSeriesUID);
//						orgAttr.setString(Tag.SOPInstanceUID, VR.UI, newSopInstUID);
//						orgAttr.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, newSopInstUID);
//						Attributes fmi = orgAttr.createFileMetaInformation(UID.ImplicitVRLittleEndian);
//						final DicomObject dcmTemp = new DicomObject();
//						DicomObjectDcm4che dcmCore = new DicomObjectDcm4che(orgAttr, fmi);
//						dcmTemp.setCore(dcmCore);
//						/*
//						 * write
//						 */
//						final File dest = new File(destDir.getAbsolutePath());
//						DicomWriter.write(dcmTemp, dest.getAbsolutePath() + File.separator + "dup_" + sopUIDs[0]);
//						/*
//						 * send to graphy refresh table load image
//						 */
//						DimseUtilities.sendFile(
//								new File(dest.getAbsolutePath() + File.separator + "dup_" + sopUIDs[0] + ".dcm"));
//
//					} else {// other dicom libs
//					}
//				} finally {
//					// delete temp files
//					try {
//						FileUtils.deleteDirectory(destDir);
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
//					// re-open
//					Viewer2DFrame viewer2d = Viewer2DFrame.getInstance();
//					if (viewer2d != null && viewer2d.isVisible()) {
//						viewer2d.loadImagesOnStage(pid, studyUID, newSeriesUID, new String[] { newSopInstUID },
//								frameRefUID);
//					}
//				}
//			} else {
//				// dcmtk
//			}
//			// single frame images
//		} else {
//			HashMap<Integer, JLayer<SlideGlass>> slides = prap.getAllSlides();
//			Set<Integer> keys = slides.keySet();
//			String newSeriesUID = DBUtil.createNewUIDNoExistingInDB("series");
//			ArrayList<String> newSopUIDs = new ArrayList<>();
//			if (DICOMBackend.isBackend(DICOMBackend.DCM4CHE)) {
//				try {
//					// save dicom slice by slice
//					for (Integer key : keys) {
//						// get current image from slide
//						SlideGlass sg = slides.get(key).getView();
//						DicomObject dcm = new DicomImageBuilder().create(sg.getOriginalImage());
//						// load original dcm from db
//						pid = (String) prap.getUIDs()[0];
//						studyUID = sg.getStudyInstanceUID();
//						seriesUID = sg.getSeriesInstanceUID();
//						String sopUID = sg.getSOPInstanceUID();// current SOPInstUID
//						String orgPath = db.getFileLocation(pid, studyUID, seriesUID, sopUID);
//						DicomReader dr = new DicomReader(orgPath, false);
//						Object orgDcm = dr.getCoreDataset();
//						// write
//						if (orgDcm instanceof Attributes) {// handle with dcm4che
//							Attributes orgAttr = (Attributes) orgDcm;
//							Attributes imgAttr = ((DicomObjectDcm4che) dcm.getCore()).getAttributes();
//							orgAttr.addAll(imgAttr);
//							if (secondaryCapture) {
//								if (!retainAmbiguousTags) {
//									deleteAmbiguousTag(orgAttr);
//								}
//								orgAttr.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
//							}
//							// change seriesUID and instUID
//							orgAttr.setString(Tag.SeriesInstanceUID, VR.UI, newSeriesUID);
//							String newSopInstUID = DBUtil.createNewUIDNoExistingInDB("image");
//							orgAttr.setString(Tag.SOPInstanceUID, VR.UI, newSopInstUID);
//							orgAttr.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, newSopInstUID);
//							newSopUIDs.add(newSopInstUID);
//							Attributes fmi = orgAttr.createFileMetaInformation(UID.ImplicitVRLittleEndian);
//							final DicomObject dcmTemp = new DicomObject();
//							DicomObjectDcm4che dcmCore = new DicomObjectDcm4che(orgAttr, fmi);
//							dcmTemp.setCore(dcmCore);
//							final File dest = new File(destDir.getAbsolutePath());
//							DicomWriter.write(dcmTemp, dest.getAbsolutePath() + File.separator + "DUP_" + sopUID);
//							// see, finally state
//						} else {// other dicom libs
//						}
//					}
//				} finally {
//					/*
//					 * send to graphy and refresh table
//					 */
//					DimseUtilities.sendMe(destDir.listFiles());
//					// delete after send
//					try {
//						FileUtils.deleteDirectory(destDir);
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
//					// re-open
//					Viewer2DFrame viewer2d = Viewer2DFrame.getInstance();
//					if (viewer2d != null && viewer2d.isVisible()) {
//						viewer2d.loadImagesOnStage(pid, studyUID, newSeriesUID,
//								newSopUIDs.toArray(new String[newSopUIDs.size()]), frameRefUID);
//					}
//				}
//			} else {
//				// dcmtk
//			}
//
//		}
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
