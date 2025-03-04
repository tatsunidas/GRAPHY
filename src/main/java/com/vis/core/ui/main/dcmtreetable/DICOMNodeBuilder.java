package com.vis.core.ui.main.dcmtreetable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

import com.vis.core.log.Log;
import com.vis.db.DatabaseHandler;

public class DICOMNodeBuilder {
	
	static final DatabaseHandler db = DatabaseHandler.getInstance();

	public DICOMNodeBuilder() {}
	
	/**
	 * For load specific studies/sereis
	 * @param rootMaterials:list of study node materials
	 * @return
	 */
	public DICOMNode buildRootNodeUsingTreeNodes(ArrayList<DefaultMutableTreeNode> rootMaterials) {
		if(rootMaterials == null) {
			throw new IllegalArgumentException("DICOMNodeBuilder acquire non null root node materials.");
		}
		ArrayList<DICOMNode> studies = new ArrayList<DICOMNode>();
		for(DefaultMutableTreeNode studyMaterialNode:rootMaterials) {//study loop
			@SuppressWarnings("unchecked")
			HashMap<String,Object> studyInfo = (HashMap<String, Object>) studyMaterialNode.getUserObject();
			HashMap<String,String> studyInfoString = convertObjectToStringInMap(studyInfo);
			String patID = (String) studyInfo.get("PatientID");
			HashMap<String,String> patInfo = db.getPatientInfoByPatID(patID);
			DICOMNode studyNode = buildStudyNode(patInfo, studyInfoString);
			for(int i=0;i<studyMaterialNode.getChildCount();i++) {//series loop
				DefaultMutableTreeNode seriesMaterialNode = (DefaultMutableTreeNode) studyMaterialNode.getChildAt(i);
				@SuppressWarnings("unchecked")
				HashMap<String,Object> seriesInfo = (HashMap<String, Object>) seriesMaterialNode.getUserObject();
				HashMap<String,String> seriesInfoString = convertObjectToStringInMap(seriesInfo);
				DICOMNode seriesNode = buildSeriesNode(patID, seriesInfoString);
				for(int j=0;j<seriesMaterialNode.getChildCount();j++) {//image loop
					DefaultMutableTreeNode imageMaterialNode = (DefaultMutableTreeNode) seriesMaterialNode.getChildAt(j);
					@SuppressWarnings("unchecked")
					HashMap<String,Object> imageInfo = (HashMap<String, Object>) imageMaterialNode.getUserObject();
					HashMap<String,String> imageInfoString = convertObjectToStringInMap(imageInfo);
					DICOMNode imageNode = buildImageNode(patID, imageInfoString);
					seriesNode.addChild(imageNode);
				}
				if(seriesNode.getChildCount() > 0) {
					studyNode.addChild(seriesNode);
				}
			}//series loop end
			studies.add(studyNode);
		}//study loop end
		DICOMNode root = new DICOMNode(true,studies);
		return root;
		
	}

	/**
	 * for loadAllStudies/Series
	 * 
	 * @param patID
	 * @param studyUID
	 * @return
	 */
	public DICOMNode buildConnectedNodeRelatedStudy(String patID, String studyUID) {
		DICOMNode studyNode = null;
		//check study exists
		if(!db.checkStudyRecordExists(patID,studyUID)) {
			return null;
		}
		//study node 構築に必要な情報
		HashMap<String,String> patInfo = db.getPatientInfoByPatID(patID);
		HashMap<String, String> studyInfo = db.getStudyInfoByUIDs(patID,studyUID);
		studyNode = buildStudyNode(patInfo,studyInfo);
		//search series
		//if study not null, must exists series with.
		if(studyNode != null) {
			List<HashMap<String, String>> seriesInfoList = db.getSeriesInfoByUIDs(patID, studyUID);
			if(seriesInfoList == null) {
				return null;
			}
			for(HashMap<String,String> seriesInfo:seriesInfoList) {
				DICOMNode seriesNode = buildConnectedNodeRelatedSeries(patID,seriesInfo);
				if(seriesNode != null) {
					studyNode.addChild(seriesNode);
				}
			}
			if(studyNode.getChildCount() == 0) {
				return null;
			}
		}else {
			return null;
		}
		return studyNode;
	}

	public static DICOMNode buildConnectedNodeRelatedSeries(String patID, HashMap<String, String> seriesInfo) {
		if (patID == null || seriesInfo==null) {
			Log.logger.warning("All keys needed when DICOMNode(SeriesNodeLevel) building, return null");
			return null;
		}
		
		String studyUID = seriesInfo.get("StudyInstanceUID");
		String seriesUID = seriesInfo.get("SeriesInstanceUID");
		
		DICOMNode connectedSeriesNode = buildSeriesNode(patID, seriesInfo);
		//get images
		List<HashMap<String, String>> imageList = db.getImagesInfoByUIDs(patID, studyUID,seriesUID);
		if(imageList == null) {
			return null;
		}
		for(HashMap<String, String> imageInfo:imageList) {
			DICOMNode imageNode = buildImageNode(patID,imageInfo);
			connectedSeriesNode.addChild(imageNode);
		}
		return connectedSeriesNode;
	}
	
	private DICOMNode buildStudyNode(HashMap<String, String> patInfo, HashMap<String,String> studyInfo) {

		// see database HashMap<String,Object> loadStudyNodeMaterial
//		studyNodeMaterial.put("PatientName", patientInfo.getString("PatientName"));
//		studyNodeMaterial.put("PatientID", patientInfo.getString("PatientID"));
//		studyNodeMaterial.put("StudyDate", studyInfo.getString("StudyDate"));
//		studyNodeMaterial.put("StudyTime", studyInfo.getString("StudyTime"));
//		studyNodeMaterial.put("StudyDescription", studyInfo.getString("StudyDescription"));
//		studyNodeMaterial.put("ModalitiesInStudy", studyInfo.getString("ModalitiesInStudy"));
//		studyNodeMaterial.put("PatientSex", patientInfo.getString("PatientSex"));
//		studyNodeMaterial.put("PatientBirthDate", patientInfo.getString("PatientBirthDate"));
//		studyNodeMaterial.put("PatientAge", studyInfo.getString("PatientAge"));//get from study info
//		studyNodeMaterial.put("AccessionNumber", studyInfo.getString("AccessionNumber"));
//		studyNodeMaterial.put("NumOfSeriesInStudy", String.valueOf(getNumOfSeriesInStudy(patientInfo.getString("PatientID"), studyInfo.getString("StudyInstanceUID"))));
//		studyNodeMaterial.put("NumOfInstancesInStudy", String.valueOf(getNumOfInstancesInStudy(patientInfo.getString("PatientID"), studyInfo.getString("StudyInstanceUID"))));
//		studyNodeMaterial.put("StudyInstanceUID", studyInfo.getString("StudyInstanceUID"));

		DICOMNode studyNode = null;
		studyNode = new DICOMNode(
				DICOMNode.STUDY, 
				patInfo.get("PatientName"),
				patInfo.get("PatientID"), 
				studyInfo.get("StudyDate"), 
				"", // seriesDate
				studyInfo.get("StudyTime"), 
				"", // acquisitiontime
				studyInfo.get("StudyDescription"), 
				"", // series desc
				studyInfo.get("ModalitiesInStudy"), 
				patInfo.get("PatientSex"),
				patInfo.get("PatientBirthDate"), 
				studyInfo.get("PatientAge"), 
				"", // institution
				"", // modelname
				"", // series number
				"", // acquisition number
				"", // instance number
				studyInfo.get("AccessionNumber"), 
				studyInfo.get("NumOfSeriesInStudy"),
				studyInfo.get("NumOfInstancesInStudy"), 
				studyInfo.get("StudyInstanceUID"), 
				null, // seriesUID, IMPORTANT set to null
				null, // sopUID, IMPORTANT set to null
				null);
		return studyNode;
	}

	private static DICOMNode buildSeriesNode(String patID, HashMap<String, String> nodeMaterial) {
		DICOMNode seriesNode = null;
		seriesNode = new DICOMNode(
				DICOMNode.SERIES, 
				"", // pname
				patID, // pid
				"", // studydate
				(String)nodeMaterial.get("SeriesDate"), 
				"", // studytime
				"", // acquisitionTime
				"", // studyDesc
				(String)nodeMaterial.get("SeriesDescription"), 
				(String)nodeMaterial.get("Modality"), 
				"", // sex
				"", // BoD
				"", // age
				(String)nodeMaterial.get("InstitutionName"), // institution
				(String)nodeMaterial.get("ModelName"), // modelname
				(String)nodeMaterial.get("SeriesNumber"), 
				"", // acquisitionNo
				"", // instanceNo
				"", // AccessionNumber
				"", // NumOfSeries
				(String)nodeMaterial.get("NumOfInstanceInSeries"),
				(String)nodeMaterial.get("StudyInstanceUID"), 
				(String)nodeMaterial.get("SeriesInstanceUID"),
				null, // SOPInstanceUID IMPORTANT set to null
				null);
		return seriesNode;
	}

	private static DICOMNode buildImageNode(String patID,HashMap<String, String> nodeMaterial) {
//		nodeMaterial.put("level", 4);
//		nodeMaterial.put("PatientID", imageInfo.getString("PatientID"));
//		nodeMaterial.put("AcquisitionDateTime", imageInfo.getString("AcquisitionDateTime"));
//		nodeMaterial.put("AcquisitionNumber", imageInfo.getString("AcquisitionNumber"));
//		nodeMaterial.put("InstanceNumber", imageInfo.getString("InstanceNumber"));
//		nodeMaterial.put("StudyInstanceUID", imageInfo.getString("StudyInstanceUID"));
//		nodeMaterial.put("SeriesInstanceUID", imageInfo.getString("SeriesInstanceUID"));
//		nodeMaterial.put("SOPInstanceUID", imageInfo.getString("SOPInstanceUID"));
		DICOMNode imageNode = null;
			imageNode = new DICOMNode(
					DICOMNode.IMAGE, 
					"",//pname, 
					patID,//pid, 
					"",//studyDate, 
					"",//seriesDate, 
					"",//studyTime, 
					(String)nodeMaterial.get("AcquisitionDateTime"), 
					"",//studyDesc, 
					"",//seriesDesc, 
					"",//modality, 
					"",//sex, 
					"",//bod, 
					"",//age, 
					"",//institution, 
					"",//modelname, 
					"",//seriesNumber, 
					(String)nodeMaterial.get("AcquisitionNumber"), 
					(String)nodeMaterial.get("InstanceNumber"), 
					"",//accessionNumber, 
					"",//numOfSeries, 
					"",//numOfInstances, 
					(String)nodeMaterial.get("StudyInstanceUID"), 
					(String)nodeMaterial.get("SeriesInstanceUID"), 
					(String)nodeMaterial.get("SOPInstanceUID"), 
					null);
		return imageNode;
	}
	
	private HashMap<String,String> convertObjectToStringInMap(HashMap<String,Object> materials) {
		HashMap<String,String> converted = new HashMap<String, String>();
		for(String key:materials.keySet()) {
			Object v = materials.get(key);
			if(v instanceof String) {
				converted.put(key, (String)v);
			}else {
				String unknown = String.valueOf(v);
				converted.put(key, unknown);
			}
		}
		return converted;
	}
}
