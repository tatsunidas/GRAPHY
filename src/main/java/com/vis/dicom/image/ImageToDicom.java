package com.vis.dicom.image;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.logging.SimpleFormatter;

import org.dcm4che3.util.UIDUtils;

//import org.dcm4che3.data.Attributes;
//import org.dcm4che3.data.Tag;
//import org.dcm4che3.data.UID;
//import org.dcm4che3.data.VR;
//import org.dcm4che3.util.UIDUtils;

import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.VR;

import ij.ImagePlus;

/**
 * create dicomobject (as secondary capture) from imageplus 
 * secondary capture builder.
 * convert consumer image format data to dicom secondary capture.
 * 
 * @author tatsunidas
 *
 */
public class ImageToDicom {
	
	ImagePlus imp = null;
	
	public ImageToDicom() {}
	
	public DicomObject createDicomObjectFromImagePlus(
			DicomObject sampleReferenceDcm, 
			double[] imagePositionPatient, 
			double[] imageOrientationPatient, 
			ImagePlus singleFrame, 
			String studyUID,
			String seriesUID, 
			String instUID, 
			Integer sliceNo, 
			String frameOfReferenceUID,
			String graphy_version,
			Integer seriesNo){
		/*
		 * start creation
		 */
		String tsuid = UID.ImplicitVRLittleEndian.uid();	
		
		/*
		 * TODO 20230906
		 */
		
//		if(core instanceof DicomObjectDcm4che) {
			
//			//create image attr
//			DicomImageBuilder builder = new DicomImageBuilder();
//			DicomObject pixelsObj = builder.create(singleFrame);
//			DicomObjectDcm4che pix = (DicomObjectDcm4che) pixelsObj.getCore();
//			DicomObjectDcm4che src = ((DicomObjectDcm4che)core).duplicate();
//			//copy all from sampleReferenceDcm
//			Attributes attr = src.getAttributes();
//			attr.addAll(pix.getAttributes());
//			
//			/*
//			 * 0008,0012  Instance Creation Date: 20221022
//			 * 0008,0013  Instance Creation Time: 121105.117
//			 */
////			LocalDate now = LocalDate.now();
////			DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyyMMdd");
////	        String str = now.format(sdf);
//	        
//	        java.util.Date now = Calendar.getInstance().getTime();
////	        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMdd");
////	        SimpleDateFormat sdf2 = new SimpleDateFormat("HHmmSS.sss");
////	        System.out.println(sdf1.format(now1));//20221215
////	        System.out.println(sdf2.format(now1));//1230190.040
//	        
//	        attr.setDate(TagDict.At("InstanceCreationDate"), VR.DA, now);
//	        attr.setDate(TagDict.At("InstanceCreationTime"), VR.DT, now);
//			
//			/*
//			 * 0008,0016  SOP Class UID: 1.2.840.10008.5.1.4.1.1.4
//			 */
//	        attr.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
//	        
//	        
//	        /*
//	         * 0020,000D  Study Instance UID: 1.2.392.200036.9123.100.12.12.40115.90221022115342038817193225
//	         * 0020,000E  Series Instance UID: 1.2.392.200036.9123.100.12.12.40115.90221022120705041558324330
//	         * 0008,0018  SOP Instance UID: 1.2.392.200036.9123.100.12.12.40115.90221022121105042374384146
//	         */
//	        attr.setString(TagDict.At("SeriesInstanceUID"), VR.UI, studyUID);
//	        attr.setString(TagDict.At("SeriesInstanceUID"), VR.UI, seriesUID);
//			attr.setString(TagDict.At("SOPInstanceUID"), VR.UI, instUID);
//			
//	        /*
//	         * 0008,0020  Study Date: 20221022
//	         * 0008,0021  Series Date: 20221022
//	         * 0008,0022  Acquisition Date: 20221022
//	         * 0008,0023  Image Date: 20221022
//	         * 0008,0030  Study Time: 121008.0
//	         * 0008,0031  Series Time: 121054.967
//	         * 0008,0032  Acquisition Time: 121054.967
//	         * 0008,0033  Image Time: 121105.0
//	         * 0020,0010  Study ID: 20221001-123
//	         * 
//	         * AS-IS
//	         */
//			
//			/*
//			 * 0008,0060  Modality: e.g., MR
//			 * 
//			 * AS-IS
//			 */
//			
//			/*
//			 * 0008,0090  Referring Physician's Name: 
//			 * 
//			 * anonymouse
//			 */
//			attr.setString(TagDict.At("ReferringPhysicianName"), VR.PN, "");
//			
//			/*
//			 * 0008,0070  Manufacturer: Visionary Imaging Services,Inc.
//			 * 0008,1010  Station Name: GRAPHY
//			 * 0008,1090  Manufacturer's Model Name: GRAPHY
//			 * 0018,1000  Device Serial Number: 40115
//			 * 0018,1020  Software Versions(s): V7.0B
//			 */
//			attr.setString(TagDict.At("Manufacturer"), VR.LO, "Visionary Imaging Services,Inc.");
//			attr.setString(TagDict.At("StationName"), VR.SH, "GRAPHY");
//			attr.setString(TagDict.At("ManufacturerModelName"), VR.LO, "GRAPHY");
//			attr.setString(TagDict.At("DeviceSerialNumber"), VR.LO, "");
//			attr.setString(TagDict.At("SoftwareVersions"), VR.LO, graphy_version);
//			
//			/*
//			 * 0008,103E  Series Description: Scano 3plane_SAG
//			 * 0008,1070  Operator's Name: =AutoLogonUser
//			 * 
//			 * AS-IS
//			 */
//			
//			/* 0010,0010  Patient's Name: TEST^TARO
//			 * 0010,0020  Patient ID: 0000012345
//			 * 0010,0030  Patient's Birth Date: 19840405
//			 * 0010,0032  Patient's Birth Time: 000000
//			 * 0010,0040  Patient's Sex: M 
//			 * 0010,1010  Patient's Age: 038Y
//			 * 0010,1030  Patient's Weight: 65
//			 * 
//			 * AS-IS
//			 */
//			
//			/*
//			 * 0020,0011  Series Number: 2 
//			 */
//			attr.setInt(TagDict.At("SeriesNumber"), VR.IS, seriesNo);
//			
//			/*
//			 * 0020,0012  Acquisition Number: 0 
//			 * 
//			 * AS-IS
//			 */
//			
//			/*
//			 * 0020,0013  Image Number: 3
//			 */
//			attr.setInt(TagDict.At("ImageNumber"), VR.IS, sliceNo);
//			attr.setInt(TagDict.At("InstanceNumber"), VR.IS, sliceNo);
//			
//			/*
//			 * 0020,0020  Patient Orientation: P\F 
//			 * 
//			 * AS-IS
//			 */
//			
//			/*
//			 * 0020,0032  Image Position (Patient): -45.05793\-174.554\125
//			 * 0020,0037  Image Orientation (Patient): 0.080062\0.996789\0\0\0\-1
//			 */
//			
//			/*
//			 * If you want to use this image as localizar, set Frame of Reference UID.
//			 * 0020,0052  Frame of Reference UID: 1.2.392.200036.9123.100.12.12.40115.90221022121008042180226300
//			 */
//			attr.setString(TagDict.At("FrameOfReferenceUID"), VR.UI, frameOfReferenceUID);
//			
//			
//			
////			Tag.ImagePositionPatient
//			if(imagePositionPatient != null && imagePositionPatient.length == 3) {
//				attr.setDouble(TagDict.At("ImagePositionPatient"), VR.DS, imagePositionPatient);
//			}else {
//				attr.remove(TagDict.At("ImagePositionPatient"));
//			}
////			Tag.ImageOrientationPatient
//			if(imageOrientationPatient != null && imagePositionPatient.length == 6) {
//				attr.setDouble(TagDict.At("ImageOrientationPatient"), VR.DS, imageOrientationPatient);
//			}else {
//				attr.remove(TagDict.At("ImageOrientationPatient"));
//			}
//			DicomObject dcm = new DicomObject();
//			DicomObjectDcm4che che = new DicomObjectDcm4che(attr, attr.createFileMetaInformation(tsuid));
//			dcm.setCore(che);
//			return finalizeFmi(dcm);
//		}else {
//			//dcmtk
//			return null;
//		}
		return null;
	}
	
	/**
	 * create secondary captures.
	 * 
	 * @param imp
	 * @param pname
	 * @param pid
	 * @param sex
	 * @param dob
	 * @param studyID
	 * @param seriesNo
	 * @param instNo
	 * @param burnedInAnnotation
	 * @return
	 */
	public DicomObject convert(
			ImagePlus imp,
			String pname,
			String pid,
			String sex,
			String dob,
			Integer studyID, Integer seriesNo, Integer instNo, boolean burnedInAnnotation) {
		
		if(imp == null || imp.getStackSize() == 0) {
			return null;
		}
		
		
		//TODO 20230906
		
//		DicomImageBuilder builder = new DicomImageBuilder(null, null);
//		DicomObject dcm = null;
//		
//		//image
//		if(imp.getStackSize()==1) {
//			if(imp.getType() == ImagePlus.GRAY8) {
//				dcm = builder.createByteDcm(imp);
//			}else if(imp.getType() == ImagePlus.GRAY16){
//				dcm = builder.createShortDcm(imp);
//			}else if(imp.getType() == ImagePlus.GRAY32){
//				dcm = builder.createFloatDcm(imp);
//			}else if(imp.getType() == ImagePlus.COLOR_RGB){
//				dcm = builder.createRGBDcm(imp);
//			}else{//ImagePlus.COLOR_256 is a just color, skip.
//				System.out.println("Graphy can not convert inputed file image type ...");
//				return null;
//			}
//		}else {//video
//			if(imp.getType() == ImagePlus.GRAY8) {
//				dcm = builder.createMultiFrameByteDcm(imp);
//			}else if(imp.getType() == ImagePlus.GRAY16){
//				dcm = builder.createMultiFrameShortDcm(imp);
//			}else if(imp.getType() == ImagePlus.GRAY32){
//				//no func
//				System.out.println("multi frame is not applicable to 32-bit...return null");
//				return null;
//			}else if(imp.getType() == ImagePlus.COLOR_RGB){
//				dcm = builder.createMultiFrameTrueColorDcm(imp);
//			}else{//ImagePlus.COLOR_256 is a just color, skip.
//				System.out.println("Graphy can not convert inputed file image type ...");
//				return null;
//			}
//		}
//		if(dcm != null) {
//			dcm = setPrimaryAttributes(dcm,pname,pid,sex,dob);
//			dcm = setStudyAttributes(dcm, studyID);
//			dcm = setSeriesAttributes(dcm, seriesNo);
//			dcm = setImageAttributes(dcm, instNo ,burnedInAnnotation);
//			dcm = setUIDs(dcm);
//			return finalizeFmi(dcm);
//		}else {
//			return null;
//		}
		return null;
	}

	private DicomObject setPrimaryAttributes(DicomObject dcm,
											String pname,
											String pid,
											String sex,
											String dobString) {
		dcm.setString(Tag.Patient​Name, VR.PN, pname);
		dcm.setString(Tag.Patient​ID, VR.LO, pid);
		dcm.setString(Tag.Patient​Sex, VR.CS, sex);//M,F,O
		if(dobString != null) {
			dobString = dobString.replace("/", "-");
			Date dob = Date.valueOf(dobString);
			dcm.setDate(Tag.Patient​Birth​Date, VR.DA, dob);
		} else {
			Date dob = null;
			dcm.setDate(Tag.Patient​Birth​Date, VR.DA, dob);//Empty if Unknown
		}
		return dcm;
	}
	
	/**
	 * 
	 * @param dcm: DicomObject
	 * @param studyID: 123456, 6 digits int string
	 * @return
	 */
	private DicomObject setStudyAttributes(DicomObject dcm, Integer studyID) {
		/*
		 * study required  attr
		 */
		Calendar now = Calendar.getInstance();
		dcm.setDate(Tag.Study​Date, VR.DA, now.getTime());//new java.text.SimpleDateFormat("yyyyMMdd").format(currentDateTime)
		dcm.setDate(Tag.Study​Time, VR.TM, now.getTime());
		dcm.setString(Tag.Referring​Physician​Name, VR.PN, "");//null if unknown
		dcm.setString(Tag.Study​ID, VR.SH, studyID != null ? String.valueOf(studyID):null);//e.g, 123456, 6digits
		dcm.setString(Tag.Accession​Number, VR.SH, "");//null if unknown
		return dcm;
	}
	
	private DicomObject setSeriesAttributes(DicomObject dcm, Integer seriesNo) {
		dcm.setString(Tag.Modality, VR.CS, "OT");
		dcm.setString(Tag.Series​Number, VR.IS, seriesNo != null ? String.valueOf(seriesNo):"1");
		dcm.setString(Tag.Manufacturer, VR.LO, "VisionaryImagingServices,Inc.");//general equipment
		dcm.setString(Tag.Conversion​Type, VR.CS, "WSD");//convarted by workstation
		return dcm;
	}
	
	private DicomObject setImageAttributes(DicomObject dcm, Integer instNo, boolean burnedInAnnotation) {
		dcm.setString(Tag.Instance​Number, VR.IS, instNo != null ? String.valueOf(instNo):"1");
//		dcm.setString(Tag.PatientOrientation, "CS", "");
//		dcm.setString(Tag.PatientPosition, "CS", "");
		dcm.setString(Tag.Image​Type, VR.CS, "DERIVED","SECONDARY");
		dcm.setString(Tag.Burned​In​Annotation, VR.CS, burnedInAnnotation ? "YES":"NO");
		/*
		 * if multiframe, add CineRate ?? but this tag required level is 3...here, skip.
		 */
		return dcm;
	}
	
	private DicomObject setUIDs(DicomObject dcm) {
		if(dcm.getInt(Tag.Number​Of​Frames,1)>1) {
			dcm.setString(Tag.SOP​Class​UID, VR.UI, UID.SecondaryCaptureImageStorage.uid());
		}else {
			if(dcm.getInt(Tag.Bits​Allocated, -1) == 8) {
				if(dcm.getString(Tag.Photometric​Interpretation).equals("RGB")) {
					dcm.setString(Tag.SOP​Class​UID, VR.UI, UID.MultiFrameTrueColorSecondaryCaptureImageStorage.uid());
				}else {
					dcm.setString(Tag.SOP​Class​UID, VR.UI, UID.MultiFrameGrayscaleByteSecondaryCaptureImageStorage.uid());
				}
			}else if(dcm.getInt(Tag.Bits​Allocated, -1) == 16) {
				/*
				 * only grayscale
				 */
				dcm.setString(Tag.SOP​Class​UID, VR.UI, UID.MultiFrameGrayscaleWordSecondaryCaptureImageStorage.uid());
			}else {
				//not acceptable format
				return null;
			}
		}
		dcm.setString(Tag.Study​Instance​UID, VR.UI, UIDUtils.createUID());
		dcm.setString(Tag.Series​Instance​UID, VR.UI, UIDUtils.createUID());
		String iuid = UIDUtils.createUID();
		dcm.setString(Tag.SOP​Instance​UID, VR.UI, iuid);
		return dcm;
	}
	
	private void finalizeFmi(DicomImage dcm) {
		dcm.updateFileMetaInfo(UID.ImplicitVRLittleEndian);
	}
	
	public void write(DicomObject dcm, String dest) {
		DicomWriter writer = DicomWriter.newDicomWriter();
		writer.write(dcm,UID.ImplicitVRLittleEndian.uid(),dest);
	}
}
