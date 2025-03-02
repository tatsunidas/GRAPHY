package com.vis.core.ui.main;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.db.DatabaseHandler;

public class QRHandler {
	
	/*
	 * Available keys of each QR level
	 */
	/* QR string for Patient/Study level */
	public final static String PID = "PatientID";
	public final static String PName = "PatientName";
	public final static String PBirthDate = "PatientBirthDate";
	public final static String PSex = "PatientSex";
	public final static String NumberOfPatientRelatedStudies = "NumberOfPatientRelatedStudies";
	public final static String NumberOfPatientRelatedSeries = "NumberOfPatientRelatedSeries";
	public final static String NumberOfPatientRelatedInstances = "NumberOfPatientRelatedInstances";
	
	/* QR string for Study level */
	public final static String StudyDate = "StudyDate";
	public final static String StudyTime = "StudyTime";
	public final static String StudyIUID= "StudyInstanceUID";
	public final static String ModalitiesInStudy = "ModalitiesInStudy";
	public final static String StudyDescription = "StudyDescription";
	public final static String NumberOfStudyRelatedSeries = "NumberOfStudyRelatedSeries";
	public final static String NumberOfStudyRelatedInstances = "NumberOfStudyRelatedInstances";
	public final static String AccessionNumber = "AccessionNumber";
	public final static String StudyInstanceUID ="StudyInstanceUID";
	
	/* QR string for Series level */
	public final static String SeriesDate = "SeriesDate";
	public final static String SeriesDescription = "SeriesDescription";
	public final static String Modality = "Modality";
	public final static String InstitutionName = "InstitutionName";
	public final static String ManufacturerModelName = "ManufacturerModelName";
	public final static String SeriesNumber = "SeriesNumber";
	public final static String NumberOfSeriesRelatedInstances = "NumberOfSeriesRelatedInstances";
	public final static String SeriesInstanceUID = "SeriesInstanceUID";
	
	/* QR string for Instance level */
	public final static String AcquisitionTime = "AcquisitionTime";
	public final static String AcquisitionNumber = "AcquisitionNumber";
	public final static String InstanceNumber = "InstanceNumber";
	public final static String SOPInstanceUID = "SOPInstanceUID";
		
	public static String convertPatientNameForQuery(String pname) {
		if(pname == null || pname.length()<1) {
			return "*";
		}
		if(pname.contains("=")) {
			String compositNames[] = pname.split("=");//SENDAGAYA^YURIKO=千駄ヶ谷^百合子=せんだがや^ゆりこ
			for(String name:compositNames) {
				if(name.length()>0 && name.contains("^")) {
					return name+"*";
				}
			}
		}else if(pname.contains(" ")) {//hankaku
			return pname.replace(" ", "^");
		}else if(pname.contains("　")) {//zenkaku
			return pname.replace("　", "^");
		}
//		return pname+"*";
		return pname;
	}
	
	public static String getTodayString(String type) {
		DateFormat dateFormat = null;
		if(type.equals("/")) {
			dateFormat = new SimpleDateFormat("yyyy/MM/dd");
		}else if(type.equals("-")) {
			dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		}else if(type.equals("")) {
			dateFormat = new SimpleDateFormat("yyyyMMdd");
		}
		Calendar cal = Calendar.getInstance();
		cal.setTime(cal.getTime());//assurance
		String str = dateFormat.format(cal.getTime());
//		System.out.println("StudyDate="+cal.get(Calendar.YEAR)+cal.get(Calendar.MONTH)+cal.get(Calendar.DATE));
		return str;
	}
	
	public static boolean archivedInLocalAllInstance(DICOMNode qrNodeStudy) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		String patID = qrNodeStudy.getData("PatientID");
		String studyIUID = qrNodeStudy.getData("StudyInstanceUID");
		
		boolean studyFound = db.checkStudyRecordExists(patID, studyIUID);
		if(studyFound) {
			List<DICOMNode> qrNodeSeries = qrNodeStudy.getChildren();
			for(DICOMNode series : qrNodeSeries) {
				if(!archivedInAllInstancesRelatedSeries(series)) {
					return false;
				}
			}
		}else {
			return false;
		}
		return true;
	}
	
	public static boolean archivedInAllInstancesRelatedSeries(DICOMNode qrNodeSeries) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		String patID = qrNodeSeries.getData("PatientID");
		String studyIUID = qrNodeSeries.getData("StudyInstanceUID");
		String seriesIUID = qrNodeSeries.getData("SeriesInstanceUID");
		boolean seriesFound = db.checkSeriesRecordExists(patID, studyIUID, seriesIUID);
		if(seriesFound) {
			List<DICOMNode> qrNodeInst = qrNodeSeries.getChildren();
			for(DICOMNode inst : qrNodeInst) {
				if(!inLocalInstance(inst)) {
					return false;
				}
			}
		}else {
			return false;
		}
		return true;
	}
	
	public static boolean inLocalInstance(DICOMNode qrInstNode) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		String patID = qrInstNode.getData("PatientID");
		String studyIUID = qrInstNode.getData("StudyInstanceUID");
		String seriesIUID = qrInstNode.getData("SeriesInstanceUID");
		String sopIUID = qrInstNode.getData("SOPInstanceUID");
		boolean imageFound = db.checkImageRecordExists(patID, studyIUID, seriesIUID,sopIUID);
		if(!imageFound) {
			return false;
		}
		return true;
	}
}
