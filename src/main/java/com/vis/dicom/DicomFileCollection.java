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
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.vis.core.log.Log;

/**
 * Collect information will import.
 * @author tatsunidas
 *
 */
public class DicomFileCollection {
	
	private File[] files;
	private ArrayList<File> dicomDirCandidates;
	private ArrayList<File> nonDicomFiles;
	private HashMap<String, String[]> dcmFileCandidates; 
	private int numOfPatient;
	private HashMap<String, Integer> numOfInstancesPerStudy;
	
	boolean ignorePrivate = false;
	
	public DicomFileCollection(File[] files) {
		setSelectedFiles(files);
	}
	
	private void addImportCandidate(File dcmimage) {
		dcmFileCandidates.computeIfAbsent(dcmimage.getAbsolutePath(), k -> DicomUtilities.getUIDSet(dcmimage.getAbsolutePath()));
	}
	
	private void calcSize() {
		Set<String> patients = new HashSet<>();
		numOfInstancesPerStudy = new HashMap<>();
		
		for (String[] ids : dcmFileCandidates.values()) {
			patients.add(ids[0]); // PatientID
			String studyUID = ids[1];
			// Java 8以降の getOrDefault を使用して簡略化
			numOfInstancesPerStudy.put(studyUID, numOfInstancesPerStudy.getOrDefault(studyUID, 0) + 1);
		}
		numOfPatient = patients.size();
	}

	/**
	 * search dicomdir and all dicom files recursively. if failed, return false.
	 * see also Utils.readFilesRecursively(selectedFiles) to collect non dcm files.
	 */
	public boolean collectCandidates() {
		if (files == null || files.length == 0) {
			Log.logger.fine("Files are null or not selected, return.");
			return false;
		}

		// init
		dicomDirCandidates = new ArrayList<>();
		nonDicomFiles = new ArrayList<>();
		dcmFileCandidates = new HashMap<>();
		
		// collect all abs paths
		try {
			readAllDICOMFiles(files);
		} catch (IOException e) {
			Log.logger.severe("Error occured when reading Dicom files...");
			Log.logger.log(Level.SEVERE, e.getMessage());
			return false;
		}
		
		if (!dicomDirCandidates.isEmpty()) {
			try {
				readDicomDir();
			} catch (Exception e) {
				Log.logger.log(Level.SEVERE, "Error occured when reading Dicom files...", e);
				return false;
			}
		}
		
		calcSize();
		
		// output result
		Log.logger.fine("Number of dicomdir is " + dicomDirCandidates.size() + " found");
		Log.logger.fine("Number of instances is " + dcmFileCandidates.size() + " found");
		return true;
	}

	private ArrayList<File> getDicomDirs() {
		return this.dicomDirCandidates;
	}
	
	public int getNumOfPatients() {
		return numOfPatient;
	}

	// for grouping studies when import
	public ArrayList<String> getNoSubstituteStudyUIDList() {
		ArrayList<String> studyUIDs = new ArrayList<>();
		for(String[] info : dcmFileCandidates.values()) {
			studyUIDs.add(info[1]);
		}
		return new ArrayList<>(new LinkedHashSet<>(studyUIDs));
	}

	public int getNumOfInstancesInStudy(String studyUID) {
		if (numOfInstancesPerStudy == null) {
			return -1;
		}
		return numOfInstancesPerStudy.getOrDefault(studyUID, -1);
	}

	public int getNumOfTotalDcmFiles() {
		return dcmFileCandidates != null ? dcmFileCandidates.size() : -1;
	}
	
	public ArrayList<File> getNoDcmFiles() {
		return nonDicomFiles;
	}
	
	private void logInvalidDicomDirError(String level) {
		Log.logger.log(Level.SEVERE, "Get error when DICOMDIR loading, at " + level + " level record");
	}

	private void readAllDICOMFiles(File[] foldersAndFiles) throws IOException {
		for (File file : foldersAndFiles) {
			if (file.isDirectory()) {
				File[] children = file.listFiles();
				if (children != null) {
					readAllDICOMFiles(children);
				}
			} else {
				categorizeFile(file);
			}
		}
	}

	private void categorizeFile(File file) {
		if (DicomUtilities.isDicomFile(file)) {
			if (DicomUtilities.isDICOMDIR(file)) {
				dicomDirCandidates.add(file);
			} else {
				addImportCandidate(file);
			}
		} else {
			nonDicomFiles.add(file);
		}
	}
	
	private void readDicomDir() throws Exception {
		for (File dcmdir : getDicomDirs()) {
			DicomDirReader dicomDirReader = DicomDirReader.newDicomDirReader(dcmdir);
			DicomObject patient = dicomDirReader.findFirstRootDirectoryRecord(ignorePrivate);
			
			while (patient != null) {
				processPatientRecord(patient, dicomDirReader);
				patient = dicomDirReader.findNextDirectoryRecord(patient, true);
			}
		}
	}

	private void processPatientRecord(DicomObject patient, DicomDirReader reader) throws Exception {
		if (!RecordType.PATIENT.name().equals(patient.getString(Tag.DirectoryRecordType))) return;
		
		if (patient.getString(Tag.PatientID) == null) {
			logInvalidDicomDirError("Patient");
			return;
		}
		
		DicomObject study = reader.findLowerDirectoryRecord(patient, ignorePrivate);
		Log.logger.log(Level.INFO, "Parsing DICOMDIR -Patient");
		
		while (study != null) {
			processStudyRecord(study, reader);
			study = reader.findNextDirectoryRecord(study, true);
		}
	}

	private void processStudyRecord(DicomObject study, DicomDirReader reader) throws Exception {
		if (!RecordType.STUDY.name().equals(study.getString(Tag.DirectoryRecordType))) return;
		
		if (study.getString(Tag.StudyInstanceUID) == null) {
			logInvalidDicomDirError("Study");
			return;
		}
		
		DicomObject series = reader.findLowerDirectoryRecord(study, ignorePrivate);
		Log.logger.log(Level.INFO, "Parsing DICOMDIR -Study");
		
		while (series != null) {
			processSeriesRecord(series, reader);
			series = reader.findNextDirectoryRecord(series, true);
		}
	}

	private void processSeriesRecord(DicomObject series, DicomDirReader reader) throws Exception {
		if (!RecordType.SERIES.name().equals(series.getString(Tag.DirectoryRecordType))) return;
		
		if (series.getString(Tag.SeriesInstanceUID) == null) {
			logInvalidDicomDirError("Series");
			return;
		}
		
		// 元の仕様通り、Instanceレベルの検索では ignorePrivate ではなくハードコードで true を渡す
		DicomObject instance = reader.findLowerDirectoryRecord(series, true);
		Log.logger.log(Level.INFO, "Parsing DICOMDIR -Series");
		
		while (instance != null) {
			processInstanceRecord(instance, reader);
			instance = reader.findNextDirectoryRecord(instance, true);
		}
	}

	private void processInstanceRecord(DicomObject instance, DicomDirReader reader) {
		if (!RecordType.IMAGE.name().equals(instance.getString(Tag.DirectoryRecordType))) return;
		
		File file = toFileName(instance, reader);
		if (file != null && file.exists()) {
			addImportCandidate(file);
		} else {
			Log.logger.log(Level.SEVERE, "File : " + (file != null ? file.getAbsolutePath() : "null")
					+ " is not exists at this location specified in dicomdir...");
		}
	}

	public ArrayList<String> selectCandidateUsingStudyUID(String willImportStudyUID) {
		ArrayList<String> list = new ArrayList<>();
		for (Map.Entry<String, String[]> entry : dcmFileCandidates.entrySet()) {
			if (willImportStudyUID.equals(entry.getValue()[1])) {
				list.add(entry.getKey());
			}
		}
		return list;
	}

	private void setSelectedFiles(File[] files) {
		this.files = files;
	}
	
	private File toFileName(DicomObject dcmObject, DicomDirReader reader) {
		String[] fileID = dcmObject.getStrings(Tag.ReferencedFileID);
		if (fileID == null || fileID.length == 0) {
			return null;
		}
		StringBuilder sb = new StringBuilder(fileID[0]);
		for (int i = 1; i < fileID.length; i++) {
			sb.append(File.separatorChar).append(fileID[i]);
		}
		File file = new File(reader.getFile().getParent(), sb.toString());
		if (!file.exists()) {
			File fileLowerCase = new File(reader.getFile().getParent(), sb.toString().toLowerCase());
			if (fileLowerCase.exists()) {
				file = fileLowerCase;
			}
		}
		return file;
	}
}