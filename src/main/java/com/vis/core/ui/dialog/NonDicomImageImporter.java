package com.vis.core.ui.dialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import com.vis.configuration.ConfigInfo;
import com.vis.core.log.Log;
import com.vis.core.util.DateUtils;
import com.vis.core.util.ImageUtils;
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
import com.vis.dicom.image.GDicomTools;
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
 * If new patient, will import as a new studies not yet available in DB.
 * 
 * @author tatsunidas
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
	final String NoPID = "NoPID";
	
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
		HashMap<Integer,String> inputs = panel.getInputs();
		
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
				continue;
			}
			if(ImageUtils.isVideoFile(f.getAbsolutePath())) {
				videos.add(f);
				continue;
			}
			if(ImageUtils.isPDF(f.getAbsolutePath())) {
				pdfs.add(f);
				continue;
			}
		}
		
		Calendar now = Calendar.getInstance();
		
		String pname = inputs.get(Tag.Patient​Name);
		String pid = inputs.get(Tag.Patient​ID);
		String sex = inputs.get(Tag.Patient​Sex);
		String dob = inputs.get(Tag.Patient​Birth​Date);
		String studyDesc = inputs.get(Tag.Study​Description);
		String seriesDesc = inputs.get(Tag.Series​Description);
		String studyUID = inputs.get(Tag.Study​Instance​UID);
		
		/*
		 * General format files do not contain patient level information.
		 * The PID is allowed to be treated as the "NoPID".
		 */
		if(pid == null || pid.trim().length()==0) {
			int res = PopUpMessage.showDialog(
					this, 
					"PatientID is blank", 
					"You have to input PatientID.\nIf you'd like to continue no PatientID, PatientID will set to NoPID.", 
					JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION);
			if(res != JOptionPane.OK_OPTION) {
				Thread.interrupted();
				return;
			}
			pid = "NoPID";
		}
		
		if(pname == null || pname.trim().length()==0) {
			pname = NoName;
		}
		
		Date dob_ = null;
		if(dob.contains("-")) {
			dob_ = DateUtils.toDateObj(dob, "-");
		}else {
			dob_ = DateUtils.toDateObj(dob, "/");
		}
		
		Path tempDir = null;
		try {
			tempDir = Files.createTempDirectory(ConfigInfo.AppName.toString(), new FileAttribute<?>[0]);
		} catch (IOException e) {
			Log.logger.severe(e.getMessage());
			Thread.interrupted();
			return;
		}
		
		if(!importNewStudy) {
			HashMap<String, String> studyInfo = db.getStudyInfoByUIDs(pid, studyUID);
			int numOfSeries = db.getNumOfSeriesInStudy(studyUID);
			
			if(numOfSeries == 0) {
				PopUpMessage.showDialog(this, "NoneDicomFileImport Error", "This study does not have any series, empty study.", JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			String studyID = studyInfo.get("StudyID");
			String studyDate = studyInfo.get("StudyDate");
			String studyTime = studyInfo.get("StudyTime");
			
			Date studyDate_ = DateUtils.toDateObj(studyDate, "/");
			Date studyTime_ = DateUtils.toTimeObj(studyTime, ":");

			String seriesUID = UIDUtils.createUID();
			
			Date contentDateTime = now.getTime();
			
			/*
			 * images will pack to a series.
			 */
			numOfSeries = images.size() == 0 ? numOfSeries:numOfSeries+1;
			
			createDcmImages(
					images,//as one series
					tempDir.toFile(),
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
					numOfSeries,
					seriesUID
					);
			
			/*
			 * start from numOfSeries and counting-up in for-loop.
			 */
			numOfSeries = videos.size() == 0 ? numOfSeries:numOfSeries+1;
			
			createDcmVideos(
					videos, 
					tempDir.toFile(), 
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
			numOfSeries = videos.size() == 0 ? numOfSeries+1:numOfSeries+videos.size()-1/*adjust already counted-up*/;
			createDcmPDF(
					pdfs, 
					tempDir.toFile(), 
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
			//create new study/series uids
			String studyInstUID = UIDUtils.createUID();
			String seriesInstUID = UIDUtils.createUID();
			int numOfSeries = 1;//first series in new study
			
			Date nowDate = new Date();
			
			createDcmImages(
					images,
					tempDir.toFile(),
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
			// start from numOfSeries in for-loop.
			numOfSeries = images.size() == 0 ? numOfSeries:numOfSeries+1;/*images deal with one series*/
			createDcmVideos(
					videos, 
					tempDir.toFile(),
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
			numOfSeries = videos.size() == 0 ? numOfSeries:numOfSeries+videos.size()-1/*adjust already counted-up*/;
			createDcmPDF(
					pdfs, 
					tempDir.toFile(),
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
		if (tempDir.toFile().listFiles() != null && tempDir.toFile().listFiles().length > 0) {
			DimseUtilities.sendMe(tempDir.toFile().listFiles());
			for(File f : tempDir.toFile().listFiles()) {
				try {
					Files.delete(f.toPath());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if (tempDir != null && Files.exists(tempDir)) {
			try {
				Files.delete(tempDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.logger.fine("Temporary directory deleted");
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
		
		if(images ==null || images.size()==0) {
			return;
		}
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
			VideoReader reader = VideoReader.load(f);
			if (reader != null) {
				ImagePlus imp = null;
				try {
					imp = reader.read();
				} catch (Exception e) {
					Log.logger.warning(e.getMessage());
					// skip this video
					continue;
				}
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
				duration = Math.rint(frames * flops);
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
			}else {
				//cannot handle format
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
