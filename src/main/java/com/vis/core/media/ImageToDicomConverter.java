package com.vis.core.media;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import com.vis.core.util.DateUtils;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.VR;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;

public class ImageToDicomConverter {
	
	public static void convertImages(
			ArrayList<File> images,
			File tempDir,
			NonDicomMediaContext context,
			int seriesNumber
			) {
		
		if(images ==null || images.size()==0) {
			return;
		}
		
		String patName = context.pname;
		String patID = context.pid;
		String sex = context.sex;
		java.util.Date dob = context.dob == null ? null:DateUtils.toDateObj(context.dob, "/");
		
		String studyDesc = context.studyDesc;
		/*
		 * StudyDate/Time
		 * When merging existing study, copy from it.
		 * When create as new study, set desired date time.
		 */
		java.util.Date studyDate = context.studyDate == null ? null:DateUtils.toDateObj(context.studyDate, "/");
		java.util.Date studyTime = context.studyTime == null ? null:DateUtils.toTimeObj(context.studyTime, ":");
		/*
		 * Content Date/Time
		 * Set date/time that images were created. 
		 */
		java.util.Date contentDate = context.contentDate == null ? null:DateUtils.toDateObj(context.contentDate, "/");
		java.util.Date contentTime = context.contentTime == null ? null:DateUtils.toTimeObj(context.contentTime, ":");
		String seriesDesc = context.seriesDesc;
		
		String studyUID = context.studyUID;
		String seriesUID = context.seriesUID;
		
		DICOMBackend backend = DICOMBackend.getCurrent();
		for(int i=0;i<images.size();i++) {
			File f = images.get(i);
			ImagePlus imp = new ImagePlus(f.getAbsolutePath());
			if(imp.getNSlices() > 1) {
				continue;
			}
			HashMap<Integer,DicomImage> img = GDicomTools.imagePlusToDcm(imp, true/*as secondary*/);
			if(img == null || img.size()==0) {
				System.out.println("Could not convert imp to dcmimg");
				continue;
			}
			DicomObject core = img.get(0).getHeader();
			core.setString(Tag.Patient​Name, TagDict.vrType(Tag.Patient​Name)[0], patName);
			core.setString(Tag.Patient​ID, TagDict.vrType(Tag.Patient​ID)[0], patID);
			core.setString(Tag.Patient​Sex, TagDict.vrType(Tag.Patient​Sex)[0], sex);
			if(dob !=null) core.setDate(Tag.Patient​Birth​Date, TagDict.vrType(Tag.Patient​Birth​Date)[0], dob);
			core.setString(Tag.Study​Description, TagDict.vrType(Tag.Study​Description)[0], studyDesc);
			core.setString(Tag.Series​Description, TagDict.vrType(Tag.Series​Description)[0], seriesDesc);
			core.setInt(Tag.Series​Number, TagDict.vrType(Tag.Series​Number)[0], seriesNumber);
			core.setString(Tag.Study​Instance​UID, VR.UI, studyUID);
			core.setString(Tag.Series​Instance​UID, VR.UI, seriesUID);
			core.setInt(Tag.Instance​Number, TagDict.vrType(Tag.Instance​Number)[0], (i+1));
			core.setString(Tag.Modality, TagDict.vrType(Tag.Modality)[0], "OT");
			if(studyDate !=null) core.setDate(Tag.Study​Date, VR.DA, studyDate);
			if(studyTime !=null) core.setDate(Tag.Study​Time, VR.TM, studyTime);
			if(contentDate !=null) core.setDate(Tag.Content​Date, VR.DA, contentDate);
			if(contentTime !=null) core.setDate(Tag.Content​Time, VR.TM, contentTime);
			DicomWriter writer = DicomWriter.newDicomWriter(backend);
			writer.write(core, UID.ImplicitVRLittleEndian.uid(), tempDir.getAbsolutePath()+File.separator+core.getString(Tag.SOP​Instance​UID));
		}
	}

}
