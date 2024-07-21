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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.logging.Level;

import com.vis.core.log.Log;

/**
 * Collect information will import.
 * @author tatsunidas
 *
 */
public class DicomFileCollection {
	
	private File[] files;
	private ArrayList<File> dicomdirCandidate;// to search DicomDir
	private ArrayList<File> no_dcm_files;
	private HashMap<String, String[]> dcmFileCandidate;// path and infoset
	private int numOfPatient;
	private HashMap<String, Integer> numOfInstancesPerStudy;
	
	boolean ignorePrivate = false;
	
	public DicomFileCollection(File[] files){
		setSelectedFiles(files);
	};
	
	private void addImportCandidate(File dcmimage) {
		if(!this.dcmFileCandidate.keySet().contains(dcmimage.getAbsolutePath())) {
			String[] info = DicomUtilities.getUIDSet(dcmimage.getAbsolutePath());
			this.dcmFileCandidate.put(dcmimage.getAbsolutePath(), info);
		}
	}
	
	private void calcSize() {
		HashSet<String> patients = new HashSet<>();
		numOfInstancesPerStudy = new HashMap<>();
		for(String p : dcmFileCandidate.keySet()) {
			String[] ids = dcmFileCandidate.get(p);
			patients.add(ids[0]);
			if(!numOfInstancesPerStudy.containsKey(ids[1])) {
				numOfInstancesPerStudy.put(ids[1], 1);
				continue;
			}
			numOfInstancesPerStudy.put(ids[1], numOfInstancesPerStudy.get(ids[1])+1);
		}
		numOfPatient = patients.size();
	}

	/**
	 * search dicomdir and all dicom files recursively. if failed, return false.
	 * see also Utils.readFilesRecursively(selectedFiles) to collect non dcm files.
	 */
	public boolean collectCandidates() {
		if (files == null) {
			Log.logger.fine("Files are null, return.");
			return false;
		}
		if (files.length == 0) {
			Log.logger.fine("Files are not selected, return.");
			return false;
		}
		// init
		dicomdirCandidate = new ArrayList<>();
		no_dcm_files = new ArrayList<>();
		dcmFileCandidate = new HashMap<>();
		// collect all abs paths
		try {
			readAllDICOMFiles(files);
		} catch (IOException e) {
			Log.logger.severe("Error occured when reading Dicom files...");
			Log.logger.log(Level.SEVERE, e.getMessage());
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
		calcSize();
		// output result
		Log.logger.fine("Number of dicomdir is " + dicomdirCandidate.size() + " found");
		Log.logger.fine("Number of instances is " + dcmFileCandidate.size() + " found");
		return true;
	}

	private ArrayList<File> getDicomDirs() {
		return this.dicomdirCandidate;
	}
	
	public int getNumOfPatients() {
		return numOfPatient;
	}

	// for grouping studies when import
	public ArrayList<String> getNoSubstituteStudyUIDList() {
		ArrayList<String> studyUIDs = new ArrayList<>();
		for(String p : dcmFileCandidate.keySet()) {
			studyUIDs.add(dcmFileCandidate.get(p)[1]);
		}
		// no duplicate
		return new ArrayList<String>(new LinkedHashSet<>(studyUIDs));
	}

	public int getNumOfInstancesInStudy(String studyUID) {
		if(numOfInstancesPerStudy == null) {
			return -1;
		}
		Integer num = numOfInstancesPerStudy.get(studyUID);
		return num != null ? num:-1;
	}

	public int getNumOfTotalDcmFiles() {
		if(dcmFileCandidate != null) {
			return dcmFileCandidate.size();
		}else {
			return -1;
		}
	}
	
	public ArrayList<File> getNoDcmFiles() {
		return no_dcm_files;
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
						}else {
							no_dcm_files.add(dicomFiles[i]);
						}
					} else {
						readAllDICOMFiles(new File[] {dicomFiles[i]});
					}
				}
			} else {// single file
				if (!DicomUtilities.namedDICOMDIR(folderOrFile)) {
					if (DicomUtilities.isDicomFile(folderOrFile)) {
						addImportCandidate(folderOrFile);
					} else {
						no_dcm_files.add(folderOrFile);
					}
				} else { // dicomdir
					if (DicomUtilities.isDicomFile(folderOrFile)) {
						dicomdirCandidate.add(folderOrFile);
					} else {
						no_dcm_files.add(folderOrFile);
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
														+ " is not exists at this location specified in dicomdir...");
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
			String[] info = dcmFileCandidate.get(p2dcm);
			if (willImportStudyUID.equals(info[1])) {
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