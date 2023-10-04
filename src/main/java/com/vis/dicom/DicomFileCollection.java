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
package com.vis.dicom;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.logging.Level;

import com.vis.core.log.Log;
import com.vis.core.util.Utils;


public class DicomFileCollection {
	
	boolean isDebug = Utils.isDebug;
	
	boolean ignorePrivate = false;
	private File[] files;
	private ArrayList<File> dicomdirCandidate;// to search DicomDir
	private HashMap<String, String> dcmFileCandidate;// path and studyUID
	private HashMap<String, Integer> numOfInstances;
	
	public DicomFileCollection(File[] files){
		setSelectedFiles(files);
	};
	
	private void addImportCandidate(File dcmimage) {
		if(!this.dcmFileCandidate.keySet().contains(dcmimage.getAbsolutePath())) {
			String studyUID = DicomUtilities.getStudyInstanceUID(dcmimage.getAbsolutePath());
			this.dcmFileCandidate.put(dcmimage.getAbsolutePath(), studyUID);
		}
	}
	
	private void calcSizeOfStudies() {
		numOfInstances = new HashMap<>();
		for(String p : dcmFileCandidate.keySet()) {
			String uid = dcmFileCandidate.get(p);
			if(!numOfInstances.containsKey(uid)) {
				numOfInstances.put(uid, 1);
				continue;
			}
			numOfInstances.put(uid, numOfInstances.get(uid)+1);
		}
	}

	/**
	 * search dicomdir and all dicom files recursively. if failed, return false.
	 * see also Utils.readFilesRecursively(selectedFiles) to collect non dcm files.
	 */
	public boolean collectCandidates() {
		if (files == null) {
			if(isDebug) Log.logger.info("Selectd file is null, return.");
			return false;
		}
		if (files.length == 0) {
			if(isDebug) Log.logger.info("Files is not selected, return.");
			return false;
		}
		// init
		dicomdirCandidate = new ArrayList<>();
		dcmFileCandidate = new HashMap<>();
		// collect all abs paths
		try {
			readAllDICOMFiles(files);
		} catch (IOException e) {
			e.printStackTrace();
			Log.logger.log(Level.SEVERE, "Error occured when reading Dicom files...", e);
			return false;
		}
		if (dicomdirCandidate.size() > 0) {
			try {
				readDicomDir();// add to dcmFileCandidate array
			} catch (Exception e) {
				e.printStackTrace();
				Log.logger.log(Level.SEVERE, "Error occured when reading Dicom files...", e);
				return false;
			}
		}
		calcSizeOfStudies();
		// output result
		if(isDebug) Log.logger.info("Number of dicomdir is " + dicomdirCandidate.size() + " found");
		if(isDebug) Log.logger.info("Number of instances is " + dcmFileCandidate.size() + " found");
		return true;
	}

	/*
	 * ref::weasis:DcmDirLoader
	 * https://github.com/nroduit/Weasis/blob/master/weasis-dicom/weasis-dicom-
	 * explorer/src/main/java/org/weasis/dicom/explorer/DicomDirLoader.java
	 */
	
	private ArrayList<File> getDicomDirs() {
		return this.dicomdirCandidate;
	}
	

	// for grouping studies when import
	public ArrayList<String> getNoSubstituteStudyUIDList() {
		ArrayList<String> studyUIDs = new ArrayList<>();
		for(String p : dcmFileCandidate.keySet()) {
			studyUIDs.add(dcmFileCandidate.get(p));
		}
		// no duplicate
		return new ArrayList<String>(new LinkedHashSet<>(studyUIDs));
	}

	public int getNumOfInstancesInStudy(String studyUID) {
		if(numOfInstances == null) {
			return -1;
		}
		Integer num = numOfInstances.get(studyUID);
		return num != null ? num:-1;
	}

	public int getNumOfTotalDcmFiles() {
		if(dcmFileCandidate != null) {
			return dcmFileCandidate.size();
		}else {
			return -1;
		}
	}
	
	private void InvalidDicomDirError(String level) {
		if (level.equals("Patient")) {
			Log.logger.log(Level.SEVERE,"Get error when DICOMDIR loading, at Patient level record");
		} else if (level.equals("Study")) {
			Log.logger.log(Level.SEVERE,"Get error when DICOMDIR loading, at Study level record");
		} else if (level.equals("Series")) {
			Log.logger.log(Level.SEVERE,"Get error when DICOMDIR loading, at Series level record");
		} else if (level.equals("Instance")) {
			Log.logger.log(Level.SEVERE,"Get error when DICOMDIR loading, at Instance level record");
		}
	}

	private void readAllDICOMFiles(File[] foldersAndFiles) throws IOException {
		for(File folderOrFile : foldersAndFiles) {
			if (folderOrFile.isDirectory()) {
				File[] dicomFiles = folderOrFile.listFiles();
				for (int i = 0; i < dicomFiles.length; i++) {
					if (dicomFiles[i].isFile()) {
						if (DicomUtilities.isDicomFile(dicomFiles[i])) {
							if (!DicomUtilities.namedDICOMDIR(dicomFiles[i])) {
								addImportCandidate(dicomFiles[i]);
							}else { //dicomdir
								dicomdirCandidate.add(dicomFiles[i]);
							}
						}
					} else {
						readAllDICOMFiles(new File[] {dicomFiles[i]});
					}
				}
			} else {// single file
				if (folderOrFile.isFile()) {
					if (!DicomUtilities.namedDICOMDIR(folderOrFile)) {
						addImportCandidate(folderOrFile);
					}else { //dicomdir
						dicomdirCandidate.add(folderOrFile);
					}
				}
			}
		}
	}
	
	//add all files to dcmCandidate
	private void readDicomDir() throws Exception {
		DicomDirReader dicomDirReader = null;
		for (File dcmdir : getDicomDirs()) {
			dicomDirReader = DicomDirReader.newDicomDirReader(dcmdir);
			DicomObject patient = dicomDirReader.findFirstRootDirectoryRecord(ignorePrivate);
			while (patient != null) {
				if (RecordType.PATIENT.name().equals(patient.getString(Tag.Directory​Record​Type))) {
					if (patient.getString(Tag.Patient​ID) == null) {
						InvalidDicomDirError("Patient");
						return;
					}
					DicomObject study = dicomDirReader.findLowerDirectoryRecord(patient, ignorePrivate);
					Log.message(Level.INFO, "Parsing DICOMDIR -Patient");
					while (study != null) {
						if (RecordType.STUDY.name().equals(study.getString(Tag.Directory​Record​Type))) {
							if (study.getString(Tag.Study​Instance​UID) == null) {
								InvalidDicomDirError("Study");
								return;
							}
							DicomObject series = dicomDirReader.findLowerDirectoryRecord(study, ignorePrivate);
							Log.message(Level.INFO, "Parsing DICOMDIR -Study");
							while (series != null) {
								if (RecordType.SERIES.name().equals(series.getString(Tag.Directory​Record​Type))) {
									if (series.getString(Tag.Series​Instance​UID) == null) {
										InvalidDicomDirError("Series");
										return;
									}
									DicomObject instance = dicomDirReader.findLowerDirectoryRecord(series, true);
									Log.message(Level.INFO, "Parsing DICOMDIR -Series");
									while (instance != null) {
										if (RecordType.IMAGE.name()
												.equals(instance.getString(Tag.Directory​Record​Type))) {
											File file = toFileName(instance, dicomDirReader);
											if (file.exists()) {
												addImportCandidate(file);
											} else {
												Log.message(Level.SEVERE, "File : " + file.getAbsolutePath()
														+ " not exist that specified in dicomdir...");
											}
										}
										// ignorePrivate is true on following process
										instance = dicomDirReader.findNextDirectoryRecord(instance, true);
									}
								}
								series = dicomDirReader.findNextDirectoryRecord(series, true);
							}
						}
						study = dicomDirReader.findNextDirectoryRecord(study, true);
					}
				}
				patient = dicomDirReader.findNextDirectoryRecord(patient, true);
			}
		}
	}

	public ArrayList<String> selectCandidateUsingStudyUID(String willImportStudyUID) {
		ArrayList<String> list = new ArrayList<String>();
		for (String p2dcm : dcmFileCandidate.keySet()) {
			if (willImportStudyUID.equals(dcmFileCandidate.get(p2dcm))) {
				list.add(p2dcm);
			}
		}
		return list;
	}

	private void setSelectedFiles(File[] files){
		this.files = files;
	}
	
	/*
	 * To read will import dicomdir. 
	 * From https://github.com/nroduit/Weasis/blob/master/weasis-dicom/weasis-dicom-
	 * explorer/src/main/java/org/weasis/dicom/explorer/DicomDirLoader.java
	 */
	private File toFileName(DicomObject dcmObject, DicomDirReader reader) {
		String[] fileID = dcmObject.getStrings(Tag.Referenced​File​ID);
		if (fileID == null || fileID.length == 0) {
			return null;
		}
		StringBuilder sb = new StringBuilder(fileID[0]);
		for (int i = 1; i < fileID.length; i++) {
			sb.append(File.separatorChar).append(fileID[i]);
		}
		File file = new File(reader.getFile().getParent(), sb.toString());
		if (!file.exists()) {
			// Try to find lower case relative path, it happens sometimes when mounting
			// cdrom on Linux
			File fileLowerCase = new File(reader.getFile().getParent(), sb.toString().toLowerCase());
			if (fileLowerCase.exists()) {
				file = fileLowerCase;
			}
		}
		return file;
	}
}