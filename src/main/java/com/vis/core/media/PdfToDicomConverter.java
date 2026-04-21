package com.vis.core.media;

import java.io.File;

import com.vis.core.util.DateUtils;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.imageio.PDFReader;

public class PdfToDicomConverter {
	
	public static void convertPDF(
			File pdf,
			File tempDir,
			NonDicomMediaContext context,
			int seriesNumber
			) {
		DICOMBackend backend = DICOMBackend.getCurrent();
		
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
		
		DicomObject core = PDFReader.convert2DCM(
				pdf, 
				patName, 
				patID, 
				dob, 
				sex,
				studyDate,
				studyTime,
				studyDesc,
				contentDate,
				contentTime,
				seriesDesc,
				seriesNumber,
				studyUID, 
				seriesUID);
		DicomWriter writer = DicomWriter.newDicomWriter(backend);
		writer.write(core, UID.ImplicitVRLittleEndian.uid(), tempDir.getAbsolutePath()+File.separator+core.getString(Tag.SOP​Instance​UID));
	
	}
	
}
