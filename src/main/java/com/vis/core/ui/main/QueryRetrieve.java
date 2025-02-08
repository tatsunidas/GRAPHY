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
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.tool.getscu.GetSCU;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.task.Task;
import com.vis.core.task.TaskContext;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.ui.main.dcmtreetable.DICOMNodeBuilder;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.core.ui.main.dcmtreetable.QRStateCellEditor;
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
	private QRStateCellEditor cellEditor;
	// Threading
	private Thread thisThread;
	// watch folder thread
	private Thread watchThread;
	boolean suspend = false;
	protected boolean stopped;// same as cancel
	protected boolean sleepScheduled;
	protected boolean suspended;
	public final static int SLEEP_TIME = 3000;
	
	public QueryRetrieve() {}

	public DICOMNode startQRTable(DicomCommunicationNode dest) {
		/*
		 * today query is default
		 */
		boolean fuzzy = false;
		List<String> patKeys = new ArrayList<String>();
		patKeys.add("*");// anybody
		List<String> studyKeys = new ArrayList<String>();
		studyKeys.add("StudyDate=" + QRHandler.getTodayString("/"));
		return query(dest, fuzzy, patKeys, studyKeys, null, null);
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
//			patKeys.add("SpecificCharacterSet=\\ISO 2022 IR 87");//why, cannot do this...
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

	public DICOMNode query(DicomCommunicationNode dest, boolean fuzzy, List<String> patKeys, List<String> studyKeys,
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
		if (rootResultList == null || rootResultList.size() == 0 || rootResultList.isEmpty()) {
			int noKeys = JOptionPane.showConfirmDialog(WindowManager.getWindow(ConfigInfo.MainScreen.toString()),
					"This query does not have any search keys, continue to load all study ?", "No search keys",
					JOptionPane.OK_CANCEL_OPTION);
			if (noKeys == JOptionPane.CANCEL_OPTION) {
				return null;
			}
		}
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
	public DICOMNode query(DicomCommunicationNode dest, boolean fuzzy, List<String> patKeys, List<String> studyKeys,
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
		if ((patKeys.size() == 0) && (studyKeys.size() == 0) && (seriesKeys.size() == 0) && (instKeys.size() == 0)) {
			int noKeys = JOptionPane.showConfirmDialog(WindowManager.getWindow(ConfigInfo.MainScreen.toString()),
					"This query does not have any search keys, continue to load all study ?", "No search keys",
					JOptionPane.OK_CANCEL_OPTION);
			if (noKeys == JOptionPane.CANCEL_OPTION) {
				return null;
			}
		}
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
	 * http://dicom.nema.org/medical/dicom/current/output/chtml/part04/sect_C.3.4.
	 * html
	 */
	public ArrayList<Attributes> queryPatientLevel(DicomCommunicationNode dest, List<String> patKeys, boolean fuzzy) {
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
	public ArrayList<Attributes> queryStudyLevel(DicomCommunicationNode dest, String patID, List<String> studyKeys) {

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
	public ArrayList<Attributes> querySeriesLevel(DicomCommunicationNode dest, String patID, String studyIUID,
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
		option.add("-M");
		option.add("StudyRoot");// keep study root

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
		querySeries = setPairedPatIDToQuery(querySeries, patID);
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
	public ArrayList<Attributes> queryInstanceLevel(DicomCommunicationNode dest, String patID, String studyIUID,
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

	/*
	 * image by image retrieve.
	 */
	public void prepareRetrieve(DicomCommunicationNode dest, DICOMNode studynode) {
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
		/* set celleditor */
		TreeTableDockManager tabDockMng = WindowManager.getMainScreen().getCurrentTreeTableManager();
		TabDock anchorDock = tabDockMng.getParticularDock(dest.getNickname());
		DICOMTreeTable treeTable = anchorDock.getDICOMTreeTable();
		int arcInd = treeTable.getArchivedColumnPosition();
		TableColumnModel tcm = treeTable.getColumnModel();
		
		//20231010
//		this.cellEditor = (QRStateCellEditor) tcm.getColumn(arcInd).getCellEditor();
//		this.cellEditor.addImportingState(retrieveinfoset, this, candidateInfoSet.size());
		
		// create new thread and add to main importer thread group.
		/* shared main importer thread group */
		/**
		 * TODO 20230829
		 */
//		thisThread = new Thread(ApplicationContext.importerThreadGroup, this);
		
		stopped = false;
		sleepScheduled = true;// useful for debug
		suspended = false;
		/* must to run first this method */
		retreiveReady = true;
	}

	public void queryAndUpadateTreeTableByTextSearch(String patID, String patName, String from, String to,
			ArrayList<String> modalities) {
		TreeTableDockManager tabDockMng = WindowManager.getMainScreen().getCurrentTreeTableManager();
		String anchorTreeTableTitle = tabDockMng.getCurrentAnchorTitle();
		if (anchorTreeTableTitle.equals("HOME")) {
			Log.logger.fine("QR Pane : Home");
			ArrayList<DefaultMutableTreeNode> selectedStudiesMaterials = DatabaseHandler.getInstance()
					.selectStudiesWithSearchKeysUsingPatName(patID, patName, from, to, modalities);
//			if(selectedStudiesMaterials == null || selectedStudiesMaterials.size() < 1) {
//				return;
//			}
			DICOMNode newRoot = new DICOMNodeBuilder().buildRootNodeUsingTreeNodes(selectedStudiesMaterials);
			TabDock currentDock = tabDockMng.getHomeDock();
			currentDock.updateTreeTable(newRoot);
		} else {
			if(Utils.isDebug) Log.logger.info("QR Pane : " + anchorTreeTableTitle);
			TabDock anchorDock = tabDockMng.getParticularDockFromMap(anchorTreeTableTitle);
			String nickname = anchorTreeTableTitle;
			/* root */
			DICOMNode queryResults = new QueryRetrieve().querySimpleSearchKeys(nickname, patID, patName, from, to,
					modalities);
			anchorDock.updateTreeTable(queryResults);
		}
	}

	public void startWatching(int totalNumOfWillRetreive) {
		/* start temp folder listening */
		Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					watchTempDir(totalNumOfWillRetreive);
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
			}
		};
		watchThread = new Thread(task);
		watchThread.start();
	}

	/* do in thread::startWatching */
	private void watchTempDir(int totalNumOfWillRetreive) throws IOException, InterruptedException {
		Path dir = Paths.get(Utils.getConfSubDirPath(ConfigInfo.TemporalDirName));
		WatchService watcher = FileSystems.getDefault().newWatchService();
		dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
		int process = 0;
		while (totalNumOfWillRetreive != process) {
			WatchKey watchKey = watcher.take();
			for (WatchEvent<?> event : watchKey.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
					continue;
				} else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
					continue;
				}
				WatchEvent<Path> ev = cast(event);
				Path name = ev.context();
				Path child = dir.resolve(name);
				String newfile = String.format("%s", child);// full path
				store(newfile);
//				deleteFile(newfile, name);//see, StoreSCU.storeInstance2Graphy
				process = process + 1;
			}
			watchKey.reset();
		}
		Log.logger.info("Watching Loop process end...");
		if (process != totalNumOfWillRetreive) {
			Log.logger.warning("Should check whether done correctly retrieve images...");
		}
	}

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	// need synchronizes
	/**
	 * used to only QR task!
	 */
	public synchronized void store(String read_file) {
		String listenerDetail[] = DatabaseHandler.getInstance().getListenerDetails();
		String aet = listenerDetail[0];
		String host = listenerDetail[1];
		int port = Integer.valueOf(listenerDetail[2]);
		String args[] = { "-c", aet + "@" + host + ":" + port, read_file };
		com.vis.dicom.dimse.StoreSCU.storeInstance2Graphy(args, true);
	}

	public void getInstanceToTemp(DicomCommunicationNode dest, String patID, String studyIUID, String seriesIUID,
			String sopIUID) {
		String aet = dest.getAETitle();
		String host = dest.getHostName();
		int port = dest.getPort();
		String args[] = { "-c", aet + "@" + host + ":" + port, "-L", "IMAGE", "-m", "PatientID=" + patID, "-m",
				"StudyInstanceUID=" + studyIUID, "-m", "SeriesInstanceUID=" + seriesIUID, "-m",
				"SOPInstanceUID=" + sopIUID, "--directory", Utils.getConfSubDirPath(ConfigInfo.TemporalDirName)};
		GetSCU.main(args);
	}

	/*
	 * tmpフォルダを監視する。 getscuでフォルダにコピーが作られる 監視がこれを見つけ、storeが実行される
	 * storeされると、DBに登録され、自動的にファイルは削除される これをインスタンス単位で繰り返す
	 * 
	 * ↑はインタラプトエラーが起こるときがあるので、やめた。 スタンダードにやる。
	 */
	private void performRetrieve() {
		if (!retreiveReady || candidateInfoSet.size() < 1 || dest == null) {
			stopImport();
			return;
		}
		
		//TODO 20230829
//		ApplicationContext.importing = true;
		
		TreeTableDockManager tabDockMng = WindowManager.getMainScreen().getCurrentTreeTableManager();
		TabDock anchorDock = tabDockMng.getParticularDock(dest.getNickname());
		DICOMTreeTable treeTable = anchorDock.getDICOMTreeTable();
		treeTable.getTableHeader().setEnabled(false);// stop table sort feature. can not ??
//		startWatching(candidateInfoSet.size());
		int count = 0;
		int size = candidateInfoSet.size();
		while (!(count == size) && !(isStopped())) {
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
			getInstanceToTemp(dest, infoset[0], infoset[1], infoset[2], infoset[3]);
			// retrieve and delete temp file
			String instancePath = new File(ConfigInfo.getPath(ConfigInfo.TemporalDirName)).listFiles()[0].getAbsolutePath();
			store(instancePath);
			/* get info from QRTable */
			int currentRow = treeTable.getParticularStudyRow(infoset[0], infoset[1]);
			int currentCol = treeTable.getArchivedColumnPosition();
			System.out.println("QR:Retrieving, ProgressAt:" + currentRow + " " + currentCol);
			updateProgress(cellEditor, infoset, currentRow, currentCol, count + 1);
			treeTable.revalidate();// NEED
			treeTable.repaint();
			/* count up */
			count++;
			synchronized (this) {
				if (isSuspended()) {
					try {
						this.wait();
						setSuspended(false);
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
		} // while loop end
		doneRetrieve(cellEditor, candidateInfoSet.get(count - 1));// -1 is to subtract last pseudo count up
		treeTable.getTableHeader().setEnabled(true);
	}

	/*
	 * TODO 20231010
	 */
	protected void updateProgress(QRStateCellEditor stateCell, String[] infoset, int row, int col, int progress) {
//		stateCell.setProgressAt(infoset, row, col, progress);
	}

	protected void doneRetrieve(QRStateCellEditor cellEditor, String[] infoset) {
		stopImport();
		//TODO 20230829
		
//		ApplicationContext.importing = false;
		
		cellEditor.importingIsDone(infoset);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				Window win = WindowManager.getWindow(ConfigInfo.MainScreen.toString());
				if(win != null) {
					MainScreen main = (MainScreen)win;
					main.updateQRTreeTables();
				}
			}
		});
	}

	public Thread getThread() {
		return thisThread;
	}

	public ArrayList<String[]> getCandidateFilesList() {
		return this.candidateInfoSet;
	}

	public void startImport() {
		thisThread.start();
	}

	public synchronized void resumeImport() {
		this.notify();
	}

	public synchronized void setSleepScheduled(boolean doSleep) {
		sleepScheduled = doSleep;
	}

	public synchronized boolean isSleepScheduled() {
		return sleepScheduled;
	}

	public synchronized void setSuspended(boolean suspend) {
		suspended = suspend;
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
//		watchThread.interrupt();
		thisThread.interrupt();
	}

	/*
	 * all time do single thread, AllAndWait never call...maybe. tatsu
	 */
	public static void cancelAllAndWait() {
		
		//TODO 20230829
//		int count = ApplicationContext.importerThreadGroup.activeCount();
//		
//		Thread[] threads = new Thread[count];
//		
//		count = ApplicationContext.importerThreadGroup.enumerate(threads);
//		ApplicationContext.importerThreadGroup.interrupt();
//		
//		for (int i = 0; i < count; i++) {
//			try {
//				threads[i].join();
//			} catch (InterruptedException ie) {
//			}
//			;
//		}
	}

	/* run retrieve */
	@Override
	public void run() {
		performRetrieve();
	}

	@Override
	public void setContext(TaskContext con) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public TaskContext getContext() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void terminate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void done() {
		// TODO Auto-generated method stub
		
	}
}
