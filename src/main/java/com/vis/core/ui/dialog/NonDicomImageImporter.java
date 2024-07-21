package com.vis.core.ui.dialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;

import com.vis.core.log.Log;
import com.vis.core.util.DateUtils;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;
import com.vis.dicom.dimse.DimseUtilities;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.ImagePlusToDicomImage;
import com.vis.dicom.image.PhotometricInterpretation;
import com.vis.imageio.PDFReader;
import com.vis.imageio.VideoReader;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * 
 * convart consumer format image/video/pdf to dicom
 * 
 * Premise;
 * Motivation to import a general image/video is saving it as a secondary capture.
 * Integration into the existing Dicom series is not recommended.(but this can do by integrate series function)
 * 
 * Functions:
 * Integrate general images into an existing study as a new series
 * Imports as new studies not yet available. TODO 20231102
 * 
 * @author tatsu
 *
 */
@SuppressWarnings("serial")
public class NonDicomImageImporter extends JDialog implements Runnable{
	
	JFileChooser jfc;
	ImportNonDicomImagePanel panel;
	String approveButtonText = "Import";
	String approveToolTip = "";
	
	DatabaseHandler db = DatabaseHandler.getInstance();
	Thread t;
	
	final String NoName = "NoName";//OOMUNE^SOUDAROU
	final String NoPID = "0000000000";//10 digits
	
	public NonDicomImageImporter(JFrame parent, boolean modal) {
		super(parent, modal);
		if(db == null) {
			Log.logger.severe(" NonDicomImageImporter()::DB does not exists");
			return;
		}
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		panel = new ImportNonDicomImagePanel();
		//add to JFileChooser
		jfc = new JFileChooser();
		jfc.setDialogTitle("Non Dicom Image Importer-select files(no folder)-");
		jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);//NO DIR
		jfc.setMultiSelectionEnabled(true);
		jfc.setCurrentDirectory(new File(System.getProperty("user.home")));
		jfc.setAccessory(panel);
		jfc.setApproveButtonText(approveButtonText);
//		jfc.setApproveButtonToolTipText(approveToolTip);
		doAction(jfc.showOpenDialog(this));
	}
	
	void doAction(int res) {
		if(res != JFileChooser.APPROVE_OPTION) {
			dispose();
			return;
		}
		t = new Thread(this);
		t.start();
	}
	
	void doImport() {
		boolean importNewStudy = panel.isImportNew();
		HashMap<String,String> inputs = panel.getInputs();
		
		File[] files = jfc.getSelectedFiles();
		ArrayList<File> videos = new ArrayList<>();
		ArrayList<File> images = new ArrayList<>();
		ArrayList<File> pdfs = new ArrayList<>();
		for(File f : files) {
			if(DicomUtilities.isDicomFile(f)) {
				continue;
			}
			if(ImageUtils.isImageFile(f.getAbsolutePath())) {
				images.add(f);
			}
			if(ImageUtils.isVideoFile(f.getAbsolutePath())) {
				videos.add(f);
			}
			if(ImageUtils.isPDF(f.getAbsolutePath())) {
				pdfs.add(f);
			}
		}
		
		Calendar now = Calendar.getInstance();
		File dirInTemp = Utils.createNewDirInTemp();
		if(!importNewStudy) {
			/*
			 * get study uid
			 */
			String pname = inputs.get("PatientName");
			String pid = inputs.get("PatientID");
			String sex = inputs.get("PatientSex");
			String dob = inputs.get("DateOfBirth");
			String studyUID = inputs.get("StudyInstanceUID");
			
			Date dob_ = null;
			if(dob.contains("-")) {
				dob_ = DateUtils.toDateObj(dob, "-");
			}else {
				dob_ = DateUtils.toDateObj(dob, "/");
			}
			
			HashMap<String, String> studyInfo = db.getStudyInfoByUIDs(pid, studyUID);
			int numOfSeries = db.getNumOfSeriesInStudy(studyUID);
			String studyID = studyInfo.get("StudyID");
			String studyDate = studyInfo.get("StudyDate");
			String studyTime = studyInfo.get("StudyTime");
			String studyDesc = db.getStudyInfoByUIDs(pid, studyUID).get("StudyDescription");
			
			Date studyDate_ = DateUtils.toDateObj(studyDate, "/");
			Date studyTime_ = DateUtils.toTimeObj(studyTime, ":");

			HashMap<String,String> keys = panel.getInputs();
			String seriesDesc = keys.get("SeriesDesc");
			String seriesUID = UIDUtils.createUID();
			
			Date contentDateTime = now.getTime();
			
			createDcmImages(
					images,//as one series
					dirInTemp,
					pname,
					pid,
					sex,
					dob_,
					studyUID,
					studyID,
					studyDesc,
					studyDate_,//studyDate,
					studyTime_,//studyTime,
					contentDateTime,//contentDate,
					contentDateTime,
					seriesDesc,
					numOfSeries+1,
					seriesUID
					);
			//createVideos
			numOfSeries = images.size() == 0 ? numOfSeries+1:numOfSeries+2;/*images deal with one series*/
			createDcmVideos(
					videos, 
					dirInTemp, 
					pname, 
					pid, 
					sex, 
					dob_, 
					studyUID, 
					studyID, 
					studyDesc, 
					studyDate_, 
					studyTime_, 
					contentDateTime, 
					contentDateTime, 
					seriesDesc, 
					numOfSeries);
			//createPDFs
			numOfSeries = videos.size() == 0 ? numOfSeries+1:numOfSeries+videos.size();
			createDcmPDF(
					pdfs, 
					dirInTemp, 
					pname, 
					pid, 
					sex, 
					dob_, 
					studyUID, 
					studyID, 
					studyDesc, 
					studyDate_, 
					studyTime_, 
					contentDateTime, 
					contentDateTime, 
					seriesDesc, 
					numOfSeries);
		}else{
			/*
			 * import as new study
			 */
			HashMap<String,String> keys = panel.getInputs();
			String pname = keys.get("PatientName");
			if(pname == null || pname.trim().length()==0) {
				pname = NoName;
			}
			String pid = keys.get("PatientID");
			if(pid == null || pid.trim().length()==0) {
				pid = NoPID;
			}
			String dob = keys.get("BirthOfDate");
			String sex = keys.get("Sex");
			String studyDesc = keys.get("StudyDesc");
			String seriesDesc = keys.get("SeriesDesc");
			
//			//create new study/series uids
			String studyInstUID = UIDUtils.createUID();
			String seriesInstUID = UIDUtils.createUID();
			int numOfSeries = 1;//first series in new study
			
			Date nowDate = new Date();
			
			createDcmImages(
					images,
					dirInTemp,
					pname,
					pid,
					sex,
					DateUtils.toDateObj(dob, "/"),
					studyInstUID,
					null,//studyID,
					studyDesc,
					nowDate,//studyDate,
					nowDate,//studyTime,
					nowDate,//contentDate,
					nowDate,//contentTime,
					seriesDesc,
					numOfSeries,
					seriesInstUID
					);
			
			//import videos
			//createVideos
			numOfSeries = images.size() == 0 ? numOfSeries:numOfSeries+1;/*images deal with one series*/
			createDcmVideos(
					videos, 
					dirInTemp, 
					pname, 
					pid, 
					sex, 
					DateUtils.toDateObj(dob, "/"),
					studyInstUID, 
					null, 
					studyDesc, 
					nowDate,//studyDate,
					nowDate,//studyTime,
					nowDate,//contentDate,
					nowDate,//contentTime,
					seriesDesc, 
					numOfSeries);
			//createPDFs
			numOfSeries = videos.size() == 0 ? numOfSeries:numOfSeries+videos.size();
			createDcmPDF(
					pdfs, 
					dirInTemp, 
					pname, 
					pid, 
					sex, 
					DateUtils.toDateObj(dob, "/"),
					studyInstUID, 
					null, 
					studyDesc, 
					nowDate,//studyDate,
					nowDate,//studyTime,
					nowDate,//contentDate,
					nowDate,//contentTime,
					seriesDesc, 
					numOfSeries);
		}
		//send to graphy db...
		if (dirInTemp.listFiles() != null && dirInTemp.listFiles().length > 0) {
			DimseUtilities.sendMe(dirInTemp.listFiles());
		}
	}
	
	/**
	 * create images as a new series.
	 * 
	 * @param images
	 * @param tempDir
	 * @param patName
	 * @param patID
	 * @param sex
	 * @param dob
	 * @param studyUID
	 * @param studyID
	 * @param studyDesc
	 * @param studyDate
	 * @param studyTime
	 * @param contentDate
	 * @param contentTime
	 * @param seriesDesc
	 * @param seriesNumber
	 * @param seriesUID
	 */
	void createDcmImages(
			ArrayList<File> images,
			File tempDir,
			String patName,
			String patID,
			String sex,
			java.util.Date dob,
			String studyUID,
			String studyID,
			String studyDesc,
			java.util.Date studyDate,
			java.util.Date studyTime,
			java.util.Date contentDate,
			java.util.Date contentTime,
			String seriesDesc,
			int seriesNumber,
			String seriesUID
			) {
		DICOMBackend backend = DICOMBackend.getCurrent();
		for(int i=0;i<images.size();i++) {
			File f = images.get(i);
			ImagePlus imp = new ImagePlus(f.getAbsolutePath());
			if(imp.getNSlices() > 1) {
				continue;
			}
			HashMap<Integer,DicomImage> img = ImagePlusToDicomImage.imagePlusToDcm(imp, true/*as secondary*/);
			if(img == null || img.size()==0) {
				System.out.println("Could not convert imp to dcmimg");
				continue;
			}
			DicomObject core = img.get(0).getCore();
			core.setString(Tag.Patient​Name, TagDict.vrType(Tag.Patient​Name)[0], patName);
			core.setString(Tag.Patient​ID, TagDict.vrType(Tag.Patient​ID)[0], patID);
			core.setString(Tag.Patient​Sex, TagDict.vrType(Tag.Patient​Sex)[0], sex);
			if(dob !=null) core.setDate(Tag.Patient​Birth​Date, TagDict.vrType(Tag.Patient​Birth​Date)[0], dob);
			core.setString(Tag.Study​ID, TagDict.vrType(Tag.Study​ID)[0], studyID);
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
	
	//see also jpg2dcm
	void createDcmVideos(
			ArrayList<File> videos,
			File tempDir,
			String patName,
			String patID,
			String sex,
			java.util.Date dob,
			String studyUID,
			String studyID,
			String studyDesc,
			java.util.Date studyDate,
			java.util.Date studyTime,
			java.util.Date contentDate,
			java.util.Date contentTime,
			String seriesDesc,
			int seriesNumber//initial series number
			) {
		DICOMBackend backend = DICOMBackend.getCurrent();
		for(int i=0;i<videos.size();i++) {
			File f = videos.get(i);
			int frames = 0;
			int w = 0;
			int h = 0;
			int c = 3;
			int bits = 8;
			double flops;
			byte[] bulk = null;
			double duration;
			boolean isColor;
			if (VideoReader.readableFormat(f.getAbsolutePath())) {
				VideoReader reader = new VideoReader();
				try {
					reader.read(f);
				} catch (Exception e) {
					e.printStackTrace();
					reader.close();
					continue;
				}
				ImagePlus imp = reader.convert2ImagePlus();
				/*
				 * imageplus will ignore alpha.
				 */
				w = imp.getWidth();
				h = imp.getHeight();
				c = imp.getNChannels();
				bits = imp.getBitDepth();//if RGB, will be 24
				frames = imp.getNSlices();
				isColor = (c > 1 || bits >= 24);
				flops = imp.getCalibration().fps;
				duration = reader.getDurationSeconds();
				int len = w * h * c * bits / 8;
				bulk = new byte[frames*len];
				for (int k = 0; k < frames; k++) {
					imp.setSlice(k + 1);
					if(!isColor) {
						ImageProcessor ip = imp.getProcessor().convertToByte(true);
						byte[] b = (byte[]) ip.getPixels();
						System.arraycopy(b, 0, bulk, k * len, len);
					}else {
						ColorProcessor cp = (ColorProcessor) imp.getProcessor();
						byte[] r = new byte[w * h];
						byte[] g = new byte[w * h];
						byte[] b = new byte[w * h];
						cp.getRGB(r,g,b);
						int itr = 0;
						for(int p=0;p<len;p+=3) {
							bulk[p+0 + (k*len)] = r[itr];
							bulk[p+1 + (k*len)] = g[itr];
							bulk[p+2 + (k*len)] = b[itr];
							itr+=1;
						}
					}
				}
				reader.close();
			}else {//compressed type mpeg2, mp4
				//TODO
				continue;
			}
			DicomObject core = DicomObject.newDicomObject();
			core.setString(Tag.SOP​Class​UID, VR.UI, UID.SecondaryCaptureImageStorage.uid());
			core.setString(Tag.Patient​Name, TagDict.vrType(Tag.Patient​Name)[0], patName);
			core.setString(Tag.Patient​ID, TagDict.vrType(Tag.Patient​ID)[0], patID);
			core.setString(Tag.Patient​Sex, TagDict.vrType(Tag.Patient​Sex)[0], sex);
			if(dob !=null) core.setDate(Tag.Patient​Birth​Date, TagDict.vrType(Tag.Patient​Birth​Date)[0], dob);
			core.setString(Tag.Study​ID, TagDict.vrType(Tag.Study​ID)[0], studyID);
			core.setString(Tag.Study​Description, TagDict.vrType(Tag.Study​Description)[0], studyDesc);
			core.setString(Tag.Series​Description, TagDict.vrType(Tag.Series​Description)[0], seriesDesc);
			
			core.setInt(Tag.Series​Number, TagDict.vrType(Tag.Series​Number)[0], seriesNumber+i);
			core.setString(Tag.Study​Instance​UID, VR.UI, studyUID);
			core.setString(Tag.Series​Instance​UID, VR.UI, UIDUtils.createUID());
			core.setString(Tag.SOP​Instance​UID, VR.UI, UIDUtils.createUID());
			core.setInt(Tag.Instance​Number, TagDict.vrType(Tag.Instance​Number)[0], (i+1));
			core.setString(Tag.Modality, TagDict.vrType(Tag.Modality)[0], "OT");
			core.setInt(Tag.Number​Of​Frames, TagDict.vrType(Tag.Number​Of​Frames)[0], frames);
			
			core.setString(Tag.Photometric​Interpretation, VR.CS, isColor ? PhotometricInterpretation.RGB.name():PhotometricInterpretation.MONOCHROME2.name());
			core.setInt(Tag.Pixel​Representation, VR.US, 0/*unsigned*/);
			core.setInt(Tag.Samples​Per​Pixel, TagDict.vrType(Tag.Samples​Per​Pixel)[0],isColor ? 3:1);
			core.setInt(Tag.Planar​Configuration, VR.US, 0);//not banded
			
			core.setInt(Tag.Rows,TagDict.vrType(Tag.Rows)[0], h);
			core.setInt(Tag.Columns, TagDict.vrType(Tag.Columns)[0], w);
//			setDoubles(dcm, Tag.Pixel​Spacing, pixelSpacingYX);
//			setDouble(dcm, Tag.Spacing​Between​Slices, pixelSpacingZ);
			core.setInt(Tag.Bits​Allocated, TagDict.vrType(Tag.Bits​Allocated)[0],isColor ? 8:bits);
			core.setInt(Tag.Bits​Stored, TagDict.vrType(Tag.Bits​Stored)[0],isColor ? 8:bits);
			core.setInt(Tag.High​Bit, TagDict.vrType(Tag.High​Bit)[0],isColor ? (8-1):(bits-1));
			
			core.setInt(Tag.Cine​Rate, VR.IS, (int)flops);
			core.setDouble(Tag.Effective​Duration, VR.DS, duration);
			
			if(studyDate !=null) core.setDate(Tag.Study​Date, VR.DA, studyDate);
			if(studyTime !=null) core.setDate(Tag.Study​Time, VR.TM, studyTime);
			if(contentDate !=null) core.setDate(Tag.Content​Date, VR.DA, contentDate);
			if(contentTime !=null) core.setDate(Tag.Content​Time, VR.TM, contentTime);
			
			core.setValue(Tag.Pixel​Data, VR.OB, bulk/*packed byte[]*/);
			DicomWriter writer = DicomWriter.newDicomWriter(backend);
			writer.write(core, UID.ImplicitVRLittleEndian.uid(), tempDir.getAbsolutePath()+File.separator+core.getString(Tag.SOP​Instance​UID));
		}
	}
	
	void createDcmPDF(
			ArrayList<File> pdfs,
			File tempDir,
			String patName,
			String patID,
			String sex,
			Date dob,
			String studyUID,
			String studyID,
			String studyDesc,
			java.util.Date studyDate,
			java.util.Date studyTime,
			java.util.Date contentDate,
			java.util.Date contentTime,
			String seriesDesc,
			int seriesNumber // initial series number
			) {
		DICOMBackend backend = DICOMBackend.getCurrent();
		for(int i=0;i<pdfs.size();i++) {
			/*
			 * File srcPDF, 
			 * String pname,
			 * String pid,
			 * String dob,//1999/01/01 or 1999-01-01
			 * String sex,//M,F,O
			 * java.util.Date studyDate,
			 * java.util.Date studyTime,
			 * java.util.Date contentDate,
			 * java.util.Date contentTime,
			 * Integer seriesNo,
			 * String studyUID,//if null setNew
			 * String seriesUID//if null setNew
			 */
			DicomObject core = PDFReader.convert2DCM(
					pdfs.get(i), 
					patName, 
					patID, 
					dob, 
					sex,
					studyDate,
					studyTime,
					contentDate,
					contentTime,
					seriesNumber+i,
					studyUID, 
					UIDUtils.createUID());
			DicomWriter writer = DicomWriter.newDicomWriter(backend);
			writer.write(core, UID.ImplicitVRLittleEndian.uid(), tempDir.getAbsolutePath()+File.separator+core.getString(Tag.SOP​Instance​UID));
		}
	}

	@Override
	public void run() {
		doImport();
//		Utils.eraseTemporalDirContents();//erase when graphy shutting down.
		if(t != null && t.isAlive()) {
			Thread.interrupted();
		}
	}
}
