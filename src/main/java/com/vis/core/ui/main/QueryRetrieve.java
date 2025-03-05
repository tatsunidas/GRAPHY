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

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.tool.getscu.GetSCU;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.task.Task;
import com.vis.core.task.TaskContext;
import com.vis.core.task.TaskManager;
import com.vis.core.task.TaskType;
import com.vis.core.task.context.ImportingStateContext;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.core.util.DeleteFolder;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.dimse.DimseUtilities;
import com.vis.dicom.dimse.FindSCU;

/**
 * 
 * @author tatsunidas
 *
 */
public class QueryRetrieve implements Task {

	/*
	 * Relational Queries have been in the DICOM standard since the start, \n but
	 * are supported by only about half the PACS in the world. ... \n It is no
	 * longer necessary to quote the unique identifiers for every level above the
	 * current query level. \n It is possible to use matching attributes at
	 * different levels of the hierarchy.
	 */
	public boolean relationalQR = false;

	/* for retrieve */
	boolean retreiveReady = false;
	// patID,studyUID,seUID,instUID
	private ArrayList<String[]> candidateInfoSet;
	private DicomCommunicationNode dest;
	
	TaskContext con;
	
	// Threading
	private Thread thisThread;
	boolean suspend = false;
	protected boolean stopped;// same as cancel
	protected boolean sleepScheduled;
	protected boolean suspended;
	public final static int SLEEP_TIME = 1000;
	
	boolean copyToTemp = false;
	
	public QueryRetrieve() {
		thisThread = new Thread(this);
		stopped = false;
		sleepScheduled = false;//useful for debug
		suspended = false;
	}

	public DICOMNode queryToDay(DicomCommunicationNode dest) {
		/*
		 * today query is default
		 */
		boolean fuzzy = false;
		List<String> studyKeys = new ArrayList<String>();
		studyKeys.add("StudyDate=" + QRUtil.getTodayString(""));//yyyyMMdd
		return query(dest, fuzzy, null /*patKeys*/, studyKeys, null, null);
	}

	public DICOMNode querySimpleSearchKeys(String serverNickname, String patID, String patName, String from, String to,
			ArrayList<String> modalities) {
		HashMap<String, Object> nodeMaterials = DatabaseHandler.getInstance().getServerNamed(serverNickname);
		DicomCommunicationNode dest = new DicomCommunicationNode(nodeMaterials);
		List<String> patKeys = new ArrayList<String>();
		if (patID != null) {
			patKeys.add("PatientID=" + patID);
		}
		if (patName != null) {
//			patKeys.add("SpecificCharacterSet=\\\\ISO 2022 IR 87");
//			patKeys.add("PatientName="+QRUtil.convertPatientNameForQuery(patName));
			/* if you use asterisk, input first word correctly.*/
			/* SHIBUYA^YASUK -> SHIBUYA* */
			patKeys.add("PatientName=" + patName);
		}
		List<String> studyKeys = new ArrayList<String>();
		if (from != null && to != null) {
			studyKeys.add("StudyDate=" + from + "-" + to);
		} else if (from == null && to != null) {
			studyKeys.add("StudyDate=" + "-" + to);
		} else if (from != null && to == null) {
			studyKeys.add("StudyDate=" + from + "-");
		}
		return query(dest, false, patKeys, studyKeys, modalities);
	}

	private DICOMNode query(DicomCommunicationNode dest, boolean fuzzy, List<String> patKeys, List<String> studyKeys,
			ArrayList<String> modalities) {
		ArrayList<DICOMNode> rootResultList = new ArrayList<DICOMNode>();
		if (modalities != null && modalities.size() > 0) {
			for (String modality : modalities) {
				List<String> seriesKeys = new ArrayList<String>();
				seriesKeys.add("Modality=" + modality);
				rootResultList.add(query(dest, fuzzy, patKeys, studyKeys, seriesKeys, null));
			}
		} else {
			rootResultList.add(query(dest, fuzzy, patKeys, studyKeys, null, null));
		}
		rootResultList.removeAll(Collections.singleton(null));
		List<DICOMNode> studies = new ArrayList<DICOMNode>();
		for (DICOMNode rootResult : rootResultList) {
			List<DICOMNode> studiesResult = (List<DICOMNode>) rootResult.getChildren();
			studies.addAll(studiesResult);
		}
		return new DICOMNode(true, studies);
	}

	/**
	 * keys-> -m,"key=value"... statements; usage=findscu [options] -c
	 * <aet>@<host>:<port> [--] [<dicom-file>|<xml-file>...]
	 */
	private DICOMNode query(DicomCommunicationNode dest, boolean fuzzy, List<String> patKeys, List<String> studyKeys,
			List<String> seriesKeys, List<String> instKeys) {
		// echo
		if (!DimseUtilities.echo(dest)) {
			JOptionPane.showMessageDialog(WindowManager.getWindow(ConfigInfo.MainScreen.toString()), "Echo failed.",
					"Query validation...", JOptionPane.INFORMATION_MESSAGE);
			return null;
		}
		/*
		 * check query key is empty or not
		 */
		if (patKeys == null) {
			patKeys = Collections.emptyList();
		}
		if (studyKeys == null) {
			studyKeys = Collections.emptyList();
		}
		if (seriesKeys == null) {
			seriesKeys = Collections.emptyList();
		}
		if (instKeys == null) {
			instKeys = Collections.emptyList();
		}
		/* remove null value */
		patKeys.removeAll(Collections.singleton(null));
		studyKeys.removeAll(Collections.singleton(null));
		seriesKeys.removeAll(Collections.singleton(null));
		instKeys.removeAll(Collections.singleton(null));
		// Patient Level QR and set root.
		ArrayList<Attributes> patResps = queryPatientLevel(dest, patKeys, fuzzy);
		if (patResps != null) {
			DICOMNode root = new DICOMNode(true, null);
			for (Attributes patResp : patResps) {
				String patID = patResp.getString(Tag.PatientID);
				ArrayList<Attributes> studyResps = queryStudyLevel(dest, patID, studyKeys);
				if (studyResps != null) {
					for (Attributes studyResp : studyResps) {
						DICOMNode studyNode = constructStudyNode(patResp, studyResp);
						String studyIUID = studyResp.getString(Tag.StudyInstanceUID);
						ArrayList<Attributes> seriesResps = querySeriesLevel(dest, patID, studyIUID, seriesKeys);
						if (seriesResps != null) {
							for (Attributes seriesResp : seriesResps) {
								DICOMNode seriesNode = constructSeriesNode(patResp, studyResp, seriesResp);
								String seriesIUID = seriesResp.getString(Tag.SeriesInstanceUID);
								ArrayList<Attributes> instResps = queryInstanceLevel(dest, patID, studyIUID, seriesIUID,
										instKeys);
								if (instResps != null) {
									for (Attributes instResp : instResps) {
										DICOMNode instNode = constructInstanceNode(patResp, studyResp, seriesResp,
												instResp);
										seriesNode.addChild(instNode);
									}
								} else {
									continue;
								}
								studyNode.addChild(seriesNode);
							}
						} else {
							continue;
						}
						root.addChild(studyNode);
					}
				} else {
					continue;
				}
			}
			return root;
		} else {
			return emptyRoot();
		}
	}

	/*
	 * http://dicom.nema.org/medical/dicom/current/output/chtml/part04/sect_C.3.4.html
	 */
	private ArrayList<Attributes> queryPatientLevel(DicomCommunicationNode dest, List<String> patKeys, boolean fuzzy) {
		// set dest
		ArrayList<String> connectTo = new ArrayList<>();
		connectTo.add("-c");
		connectTo.add(dest.getAETitle() + "@" + dest.getHostName() + ":" + dest.getPort());// AET@host:port
		if (fuzzy) {
			connectTo.add("--fuzzy");// fuzzy search for patientName
		}
		// set opt
		ArrayList<String> option = new ArrayList<>();
		option.add("--accept-timeout");
		option.add("60000");// 1 minutes
		option.add("-L");
		option.add("PATIENT");
		option.add("-M");
		option.add("PatientRoot");

		/* set requests return value */
		ArrayList<String> responseKeys = new ArrayList<>();
		responseKeys.add("-r");
		responseKeys.add("PatientName");
		responseKeys.add("-r");
		responseKeys.add("PatientID");
		responseKeys.add("-r");
		responseKeys.add("NumberOfPatientRelatedStudies");
		responseKeys.add("-r");
		responseKeys.add("NumberOfPatientRelatedSeries");
		responseKeys.add("-r");
		responseKeys.add("NumberOfPatientRelatedInstances");
		/* no use as usual */
		/* ************************************** */
		responseKeys.add("-r");
		responseKeys.add("PatientSex");
		responseKeys.add("-r");
		responseKeys.add("PatientBirthDate");
		responseKeys.add("-r");// future work, current findscu does not return this value
		responseKeys.add("PatientAge");
		/* *************************************** */
		ArrayList<String> queryStmt = new ArrayList<>();
		queryStmt.addAll(connectTo);
		queryStmt.addAll(option);
		queryStmt = setPairedKeyToQuery(queryStmt, patKeys);
		queryStmt.addAll(responseKeys);
		FindSCU cfind = null;
		try {
			cfind = new FindSCU();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		String[] query = queryStmt.toArray(new String[queryStmt.size()]);
		ArrayList<Attributes> response = cfind.simpleQuery(query);
		if (response == null || response.size() < 1) {
			return null;
		}
		return response;
	}

	/*
	 * queryKeys can include pid and pname, study date study time ModalitiesInStudy
	 * StudyIUID
	 */
	private ArrayList<Attributes> queryStudyLevel(DicomCommunicationNode dest, String patID, List<String> studyKeys) {

		// set dest
		ArrayList<String> connectTo = new ArrayList<>();
		connectTo.add("-c");
		connectTo.add(dest.getAETitle() + "@" + dest.getHostName() + ":" + dest.getPort());// AET@host:port
		// set opt
		ArrayList<String> option = new ArrayList<>();
		option.add("--accept-timeout");
		option.add("60000");// 1 minutes
		option.add("-L");
		option.add("STUDY");// findscu default
		option.add("-M");
		option.add("StudyRoot");// findscu default
		// set response
		ArrayList<String> response = new ArrayList<>();
		response.add("-r");
		response.add("PatientName");
		response.add("-r");
		response.add("PatientID");
		response.add("-r");
		response.add("PatientSex");
		response.add("-r");
		response.add("PatientBirthDate");
		response.add("-r");
		response.add("StudyDate");
		response.add("-r");
		response.add("StudyTime");
		response.add("-r");
		response.add("ModalitiesInStudy");
		response.add("-r");
		response.add("StudyDescription");
		response.add("-r");
		response.add("NumberOfStudyRelatedSeries");
		response.add("-r");
		response.add("NumberOfStudyRelatedInstances");
		response.add("-r");
		response.add("AccessionNumber");
		response.add("-r");
		response.add("StudyInstanceUID");
		// composite query statement
		ArrayList<String> queryStudy = new ArrayList<>();
		queryStudy.addAll(connectTo);
		queryStudy.addAll(option);
		queryStudy = setPairedPatIDToQuery(queryStudy, patID);
		queryStudy = setPairedKeyToQuery(queryStudy, studyKeys);
		queryStudy.addAll(response);
		FindSCU cfind = null;
		try {
			cfind = new FindSCU();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		String[] query = queryStudy.toArray(new String[queryStudy.size()]);
		ArrayList<Attributes> studies = cfind.simpleQuery(query);
		if (studies == null || studies.size() < 1) {
			return null;
		}
		return studies;
	}

	/**
	 * 
	 * @param dest
	 * @param patKeys
	 * @param studyKeys  must include studyIUID
	 * @param seriesKeys
	 * @return
	 */
	private ArrayList<Attributes> querySeriesLevel(DicomCommunicationNode dest, String patID, String studyIUID,
			List<String> seriesKeys) {
		// create series request
		// set dest
		ArrayList<String> connectTo = new ArrayList<>();
		connectTo.add("-c");
		connectTo.add(dest.getAETitle() + "@" + dest.getHostName() + ":" + dest.getPort());// AET@host:port
		// set opt
		ArrayList<String> option = new ArrayList<>();
		option.add("--accept-timeout");
		option.add("60000");// 1 minutes
		option.add("-L");
		option.add("SERIES");
//		option.add("-M");
//		option.add("StudyRoot");// keep study root

		// SERIES LEVEL Response
		ArrayList<String> response = new ArrayList<>();
		response.add("-r");
		response.add("SeriesDate");
		response.add("-r");
		response.add("SeriesDescription");
		response.add("-r");
		response.add("Modality");
		response.add("-r");
		response.add("InstitutionName");
		response.add("-r");
		response.add("ManufacturerModelName");
		response.add("-r");
		response.add("SeriesNumber");
		response.add("-r");
		response.add("NumberOfSeriesRelatedInstances");
		response.add("-r");
		response.add("SeriesInstanceUID");
		// composite query statement
		ArrayList<String> querySeries = new ArrayList<>();
		querySeries.addAll(connectTo);
		querySeries.addAll(option);
//		querySeries = setPairedPatIDToQuery(querySeries, patID);
		querySeries = setPairedStudyIUIDToQuery(querySeries, studyIUID);
		querySeries = setPairedKeyToQuery(querySeries, seriesKeys);
		querySeries.addAll(response);
		FindSCU cfind = null;
		try {
			cfind = new FindSCU();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		String[] query = querySeries.toArray(new String[querySeries.size()]);
		ArrayList<Attributes> series = cfind.simpleQuery(query);
		if (series == null || series.size() < 1) {
			return null;
		}
		return series;
	}

	/**
	 * 
	 * @param dest
	 * @param patKeys
	 * @param studyKeys  : must include studyIUID
	 * @param seriesKeys : must include seriesIUID
	 * @param instKeys
	 * @return
	 */
	private ArrayList<Attributes> queryInstanceLevel(DicomCommunicationNode dest, String patID, String studyIUID,
			String seriesIUID, List<String> instKeys) {
		// create image request
		// set dest
		ArrayList<String> connectTo = new ArrayList<>();
		connectTo.add("-c");
		connectTo.add(dest.getAETitle() + "@" + dest.getHostName() + ":" + dest.getPort());// AET@host:port
		// set opt
		ArrayList<String> option = new ArrayList<>();
		option.add("--accept-timeout");
		option.add("60000");// 1 minutes
		option.add("-L");
		option.add("IMAGE");
		option.add("-M");
		option.add("StudyRoot");// keep study root
		// IMAGE LEVEL Response
		ArrayList<String> response = new ArrayList<>();
		response.add("-r");
		response.add("AcquisitionTime");
		response.add("-r");
		response.add("AcquisitionNumber");
		response.add("-r");
		response.add("InstanceNumber");
		response.add("-r");
		response.add("SOPInstanceUID");
		ArrayList<String> queryInstance = new ArrayList<>();
		// composite query statement
		queryInstance.addAll(connectTo);
		queryInstance.addAll(option);
		queryInstance = setPairedPatIDToQuery(queryInstance, patID);
		queryInstance = setPairedStudyIUIDToQuery(queryInstance, studyIUID);
		queryInstance = setPairedSeriesIUIDToQuery(queryInstance, seriesIUID);
		queryInstance = setPairedKeyToQuery(queryInstance, instKeys);
		queryInstance.addAll(response);
		FindSCU cfind = null;
		try {
			cfind = new FindSCU();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		String[] query = queryInstance.toArray(new String[queryInstance.size()]);
		ArrayList<Attributes> images = cfind.simpleQuery(query);
		if (images == null || images.size() < 1) {
			return null;
		}
		return images;
	}

	/**
	 * 
	 * @param query
	 * @param pairedkeys-> string : "key=value"
	 * @return
	 */
	private ArrayList<String> setPairedKeyToQuery(ArrayList<String> query, List<String> pairedkeys) {
		if (pairedkeys == null || pairedkeys.isEmpty()) {
			return query;
		}
		for (String pairkey : pairedkeys) {
			query.add("-m");
			query.add(pairkey);
		}
		return query;
	}

	private ArrayList<String> setPairedPatIDToQuery(ArrayList<String> query, String patID) {
		if (patID == null) {
			return query;
		}
		query.add("-m");
		query.add("PatientID=" + patID);
		return query;
	}

	private ArrayList<String> setPairedStudyIUIDToQuery(ArrayList<String> query, String studyIUID) {
		if (studyIUID == null) {
			return query;
		}
		query.add("-m");
		query.add("StudyInstanceUID=" + studyIUID);
		return query;
	}

	private ArrayList<String> setPairedSeriesIUIDToQuery(ArrayList<String> query, String seriesIUID) {
		if (seriesIUID == null) {
			return query;
		}
		query.add("-m");
		query.add("SeriesInstanceUID=" + seriesIUID);
		return query;
	}

	private DICOMNode emptyRoot() {
		DICOMNode root = new DICOMNode(true, new ArrayList<DICOMNode>());
		return root;
	}

	private DICOMNode constructStudyNode(Attributes patResp, Attributes studyResp) {
		DICOMNode node = new DICOMNode(DICOMNode.STUDY, patResp.getString(Tag.PatientName, ""), // pname,
				patResp.getString(Tag.PatientID, ""), // pid,
				studyResp.getString(Tag.StudyDate, ""), // studyDate,
				"", // seriesDate,
				studyResp.getString(Tag.StudyTime, ""), // studyTime,
				"", // acquisitiontime,
				studyResp.getString(Tag.StudyDescription, ""), // studyDesc,
				"", // seriesDesc,
				studyResp.getString(Tag.ModalitiesInStudy, ""), // modality,
				patResp.getString(Tag.PatientSex, ""), // sex,
				patResp.getString(Tag.PatientBirthDate, ""), // bod,
				patResp.getString(Tag.PatientAge, ""), // age,
				"", // institution,
				"", // modelname,
				"", // seriesNumber,
				"", // acquisitionNumber,
				"", // instanceNumber,
				studyResp.getString(Tag.AccessionNumber, ""), // AccessionNumber,
				studyResp.getString(Tag.NumberOfStudyRelatedSeries, ""), // numOfSeries,
				studyResp.getString(Tag.NumberOfStudyRelatedInstances, ""), // numOfInstances,
				studyResp.getString(Tag.StudyInstanceUID, ""), // studyUID,
				"", // SeriesInstanceUID,
				"", // sopInstaceUID,
				null);
		return node;
	}

	private DICOMNode constructSeriesNode(Attributes patResp, Attributes studyResp, Attributes seriesResp) {
		DICOMNode node = new DICOMNode(DICOMNode.SERIES, patResp.getString(Tag.PatientName, ""), // pname,
				patResp.getString(Tag.PatientID, ""), // pid,
				"", // studyDate,
				seriesResp.getString(Tag.SeriesDate), // seriesDate,
				"", // studyTime,
				"", // acquisitiontime,
				"", // studyDesc,
				seriesResp.getString(Tag.SeriesDescription), // seriesDesc,
				seriesResp.getString(Tag.Modality), // modality,
				"", // sex,
				"", // bod,
				"", // age,
				seriesResp.getString(Tag.InstitutionName), // institution,
				seriesResp.getString(Tag.ManufacturerModelName), // modelname,
				seriesResp.getString(Tag.SeriesNumber), // seriesNumber,
				"", // acquisitionNumber,
				"", // instanceNumber,
				"", // AccessionNumber,
				"", // numOfSeries,
				seriesResp.getString(Tag.NumberOfSeriesRelatedInstances), // numOfInstances,
				studyResp.getString(Tag.StudyInstanceUID, ""), // studyUID,
				seriesResp.getString(Tag.SeriesInstanceUID, ""), "", // sopInstaceUID,
				null);
		return node;
	}

	private DICOMNode constructInstanceNode(Attributes patResp, Attributes studyResp, Attributes seriesResp,
			Attributes instResp) {
		DICOMNode node = new DICOMNode(DICOMNode.IMAGE, patResp.getString(Tag.PatientName, ""), // pname,
				patResp.getString(Tag.PatientID, ""), // pid,
				"", // studyDate,
				"", // seriesDate,
				"", // studyTime,
				instResp.getString(Tag.AcquisitionTime, ""), // acquisitiontime,
				"", // studyDesc,
				"", // seriesDesc,
				"", // modality,
				"", // sex,
				"", // bod,
				"", // age,
				"", // institution,
				"", // modelname,
				"", // seriesNumber,
				instResp.getString(Tag.AcquisitionNumber, ""), // acquisitionNumber,
				instResp.getString(Tag.InstanceNumber, ""), // instanceNumber,
				"", // AccessionNumber,
				"", // numOfSeries,
				"", // numOfInstances,
				studyResp.getString(Tag.StudyInstanceUID, ""), // studyUID,
				seriesResp.getString(Tag.SeriesInstanceUID, ""), // seriesIUID
				instResp.getString(Tag.SOPInstanceUID, ""), // sopInstaceUID,
				null);
		return node;
	}

	boolean getDataExistence(Attributes attr) {
		ArrayList<HashMap<String, String>> list = DatabaseHandler.getInstance().listStudies(
				attr.getString(Tag.PatientName), attr.getString(Tag.PatientID), attr.getString(Tag.PatientBirthDate),
				attr.getString(Tag.AccessionNumber), attr.getString(Tag.StudyDate),
				attr.getString(Tag.StudyDescription), attr.getString(Tag.Modality));
		if (list == null || list.size() < 1) {
			return false;
		} else {
			return true;
		}
	}

	public ArrayList<String[]> prepareCandidate(DICOMNode node) {
		ArrayList<String[]> candidate = new ArrayList<String[]>();
		if (node.getLevel() == DICOMNode.STUDY) {
			List<DICOMNode> serieslist = node.getChildren();
			for (DICOMNode series : serieslist) {
				List<DICOMNode> imagelist = series.getChildren();
				for (DICOMNode image : imagelist) {
					String[] infoset = new String[4];
					infoset[0] = image.getData(DICOMNode.PatientID);
					infoset[1] = image.getData(DICOMNode.StudyInstanceUID);
					infoset[2] = image.getData(DICOMNode.SeriesInstanceUID);
					infoset[3] = image.getData(DICOMNode.SOPInstanceUID);
					candidate.add(infoset);
				}
			}
		} else if (node.getLevel() == DICOMNode.SERIES) {
			List<DICOMNode> imagelist = node.getChildren();
			for (DICOMNode image : imagelist) {
				String[] infoset = new String[4];
				infoset[0] = image.getData(DICOMNode.PatientID);
				infoset[1] = image.getData(DICOMNode.StudyInstanceUID);
				infoset[2] = image.getData(DICOMNode.SeriesInstanceUID);
				infoset[3] = image.getData(DICOMNode.SOPInstanceUID);
				candidate.add(infoset);
			}
		} else if (node.getLevel() == DICOMNode.IMAGE) {
			String[] infoset = new String[4];
			infoset[0] = node.getData(DICOMNode.PatientID);
			infoset[1] = node.getData(DICOMNode.StudyInstanceUID);
			infoset[2] = node.getData(DICOMNode.SeriesInstanceUID);
			infoset[3] = node.getData(DICOMNode.SOPInstanceUID);
			candidate.add(infoset);
		} else {
			return null;
		}
		return candidate;
	}
	
	/**
	 * Prepare import to DB.
	 * @param dest
	 * @param studynode
	 */
	public void prepareRetrieve(DicomCommunicationNode dest, DICOMNode studynode) {
		prepareRetrieve(dest, studynode, false);
	}

	/**
	 * 
	 * @param dest
	 * @param studynode
	 * @param copyToTemp : if true, do not store to db. just output temp file dir(temp/studyUID/seriesUID/./). 
	 */
	public void prepareRetrieve(DicomCommunicationNode dest, DICOMNode studynode, boolean copyToTemp) {
		this.candidateInfoSet = null;
		this.candidateInfoSet = prepareCandidate(studynode);
		this.dest = dest;
		String[] retrieveinfoset = new String[4];// study will retrieve
		retrieveinfoset[0] = studynode.getData(DICOMNode.PatientID);
		retrieveinfoset[1] = studynode.getData(DICOMNode.StudyInstanceUID);
		retrieveinfoset[2] = (studynode.getData(DICOMNode.SeriesInstanceUID) != null
				? studynode.getData(DICOMNode.SeriesInstanceUID)
				: "");
		retrieveinfoset[3] = (studynode.getData(DICOMNode.SOPInstanceUID) != null
				? studynode.getData(DICOMNode.SOPInstanceUID)
				: "");
		thisThread = new Thread(this);
		//add to task manager
		TaskManager tm = TaskManager.getInstance();
		tm.addTask(thisThread.getId(), this);
		
		this.copyToTemp = copyToTemp;
		
		stopped = false;
		sleepScheduled = true;// useful for debug
		suspended = false;
		/* must to run first this method */
		retreiveReady = true;
	}
	
	// need synchronizes
	/**
	 * used to only QR task!
	 */
	public synchronized void store(String read_file, File tempStoredDirWillDelete) {
		String listenerDetail[] = DatabaseHandler.getInstance().getListenerDetails();
		String aet = listenerDetail[0];
		String host = listenerDetail[1];
		int port = Integer.valueOf(listenerDetail[2]);
		String args[] = { "-c", aet + "@" + host + ":" + port, read_file };
		com.vis.dicom.dimse.StoreSCU.storeInstance2Graphy(args, true);
		if(tempStoredDirWillDelete != null) {
			DeleteFolder.deleteDirectory(tempStoredDirWillDelete);
		}
	}

	/**
	 * 
	 * @param dest()
	 * @param patID
	 * @param studyIUID
	 * @param seriesIUID
	 * @param sopIUID
	 * @return retrieve destination
	 * @throws IOException
	 */
	public File getInstanceToTemp(DicomCommunicationNode remote, String patID, String studyIUID, String seriesIUID,
			String sopIUID) throws IOException {
		String aet = remote.getAETitle();
		String host = remote.getHostName();
		int port = remote.getPort();
		/*
		 * keep strict substance path for instance.
		 */
		File tempRetriveDir = new File(Utils.getConfSubDirPath(ConfigInfo.TemporalDirName)+File.separator+patID+File.separator+studyIUID+File.separator+seriesIUID);
		if(!tempRetriveDir.exists()) {
			if(!tempRetriveDir.mkdirs()) {
				IOException e = new IOException("Cannot create temp dir for retrive...");
				throw e;
			}
		}
		String args[] = { "-c", aet + "@" + host + ":" + port, "-L", "IMAGE", "-m", "PatientID=" + patID, "-m",
				"StudyInstanceUID=" + studyIUID, "-m", "SeriesInstanceUID=" + seriesIUID, "-m",
				"SOPInstanceUID=" + sopIUID, "--directory", tempRetriveDir.getAbsolutePath()};
		GetSCU.main(args);
		return tempRetriveDir;
	}
	
	private void performRetrieve() {
		if (!retreiveReady || candidateInfoSet.size() < 1 || dest == null) {
			setStopped(true);
			Log.logger.warning("Retrieve taget is null ?? please check what you wolud retrieve.");
			return;
		}
		TreeTableDockManager tabDockMng = WindowManager.getMainScreen().getTreeTableDockManager();
		TabDock anchorDock = tabDockMng.getDock(dest.getNickname());
		DICOMTreeTable treeTable = anchorDock.getDICOMTreeTable();
		treeTable.getTableHeader().setEnabled(false);// stop table sort feature.
		int count = 0;
		int size = candidateInfoSet.size();
		while (!(count == size) && !(isStopped())) {
			synchronized (this) {
				if (isSuspended()) {
					try {
						this.wait();
					} catch (InterruptedException ie) {
						setStopped(true);
						break;
					}
				}
			}
			if (Thread.interrupted()) {
				setStopped(true);
				break;
			}
			if (sleepScheduled) {
				try {
					Thread.sleep(SLEEP_TIME);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			/* retrieve */
			String infoset[] = candidateInfoSet.get(count);
			// copy to temp dir
			File retrieveDir = null;
			try {
				retrieveDir = getInstanceToTemp(dest, infoset[0], infoset[1], infoset[2], infoset[3]);
			} catch (IOException e) {
				Log.logger.severe(e.getLocalizedMessage());
				break;
			}
			File tempRetriveParentDir = new File(Utils.getConfSubDirPath(ConfigInfo.TemporalDirName)+File.separator+infoset[0]);
			
			if(!copyToTemp) {
				// retrieve and delete temp file
				String instancePath = retrieveDir.listFiles()[0].getAbsolutePath();
				store(instancePath, tempRetriveParentDir/*delete after stored*/);
			}
			
			if(count  == 0) {
				HashMap<String, Object> update_con = new HashMap<>();
				update_con.put(TaskContext.TASK_TYPE, TaskType.TypeImport);
				update_con.put(TaskContext.THREAD_ID, thisThread.getId());
				update_con.put(TaskContext.SIZE, candidateInfoSet.size());
				update_con.put(TaskContext.CURRENT_IND, count);
				con = new ImportingStateContext(infoset[1], update_con);
			}else {
				HashMap<String, Object> updation = new HashMap<>();
				updation.put(TaskContext.CURRENT_IND, count);
				//task context always update, even if failed import.
				con.updateState(updation);
			}
			/* count up */
			count++;
			/*
			 * IMPRTANT to update cell editor.
			 */
			treeTable.revalidate();
			treeTable.repaint();
		} // while loop end
		done();
	}
	
	public void done() {
		setStopped(true);
		retreiveReady = false;
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				Window win = WindowManager.getWindow(ConfigInfo.MainScreen.toString());
				if(win != null) {
					MainScreen main = (MainScreen)win;
					main.updateQRTreeTables();
					TreeTableDockManager tabDockMng = WindowManager.getMainScreen().getTreeTableDockManager();
					TabDock anchorDock = tabDockMng.getDock(dest.getNickname());
					DICOMTreeTable treeTable = anchorDock.getDICOMTreeTable();
					treeTable.getTableHeader().setEnabled(true);
				}
			}
		});
		TaskManager tm = TaskManager.getInstance();
		tm.removeTask(con.getThreadId());
	}

	public Thread getThread() {
		return thisThread;
	}

	public ArrayList<String[]> getCandidateFilesList() {
		return this.candidateInfoSet;
	}
	
	public synchronized void resume() {
//		thisThread.notify();//DO NOT DO THIS
		this.notify();
	}

	public synchronized void setSleepScheduled(boolean doSleep) {
		sleepScheduled = doSleep;
	}

	public synchronized boolean isSleepScheduled() {
		return sleepScheduled;
	}

	public synchronized void setSuspended(boolean suspend) {
		if(suspended && suspend) {
			//do nothing
		}else if(!suspended && suspend) {
			suspended = true;
		}else if(suspended && !suspend) {
			suspended = false;
			resume();
		}else if(!suspended && !suspend) {
			//do nothing
		}
	}

	public synchronized boolean isSuspended() {
		return suspended;
	}

	public synchronized void setStopped(boolean stop) {
		stopped = stop;
	}

	public synchronized boolean isStopped() {
		return stopped;
	}

	public void stopImport() {
		if(thisThread != null) {
			setStopped(true);
		}
	}

	/* run retrieve */
	@Override
	public void run() {
		performRetrieve();
	}

	@Override
	public TaskContext getContext() {
		return con;
	}

	@Override
	public void start() {
		thisThread.start();
	}
}
