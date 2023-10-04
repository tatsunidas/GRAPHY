package com.vis.core.ui.dialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.UID;
import com.vis.dicom.VR;
import com.vis.dicom.dimse.DimseUtilities;
import com.vis.dicom.image.ImageToDicom;
import com.vis.imageio.PDFReader;

import ij.ImagePlus;
import ij.plugin.AVI_Reader;

/**
 * 
 * convart consumer format image to dicom
 * 
 * basic way to do this(Premise);
 * In general, the motivation for importing a general image is saving as a secondary capture.
 * Integration into the existing Dicom series is not acceptable.
 * 
 * Functions:
 * Integrate general images into an existing study as a new series
 * Imports as new studies not yet available.
 * 
 * @author tatsu
 *
 */
@SuppressWarnings("serial")
public class NonDicomImageImporter extends JFrame{
	
	JFileChooser jfc;
	ImportNonDicomImagePanel panel;
	String approveButtonText = "Import";
	String approveToolTip = "";
	
	String NoName = "NoName";
	String NoPID = "0000000000";

	//test
	public static void main(String[] args) {
		
	}

	public NonDicomImageImporter() {
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		//ImportNonDicomImagePanel
		panel = new ImportNonDicomImagePanel();
		//add to JFileChooser like as Exporter.
		jfc = new JFileChooser();
		jfc.setDialogTitle("Non Dicom Image Importer-select files(without folder)-");
		jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);//NO DIR
		jfc.setMultiSelectionEnabled(true);
		jfc.setCurrentDirectory(new File(System.getProperty("user.home")));
		jfc.setAccessory(panel);
		jfc.setApproveButtonText(approveButtonText);
		jfc.setApproveButtonToolTipText(approveToolTip);
		doAction(jfc.showOpenDialog(this));
	}
	
	/*
	 * TODO
	 * if included dicom, ignore it.
	 * if select folder, gathering in one series.
	 * if select single file, import as single dcm/series
	 * if pid already exists, 
	 * 
	 */
	void doAction(int num) {
		if(!(num == JFileChooser.APPROVE_OPTION)) {
			dispose();
			return;
		}
		
		boolean isImportToExistingStudy = panel.doImportToStudy();
		HashMap<String,String> inputs = panel.getInputs();
		
		if(!isImportToExistingStudy && !panel.dateValidation(inputs.get("BirthOfDate"))) {
			JOptionPane.showMessageDialog(this, "please input date of birth correctly (e.g, 2021/01/01).");
			return;
		}
		
		//get selected study
		/*
		 * if multi selected, error
		 */
		ArrayList<DICOMNode> nodes = WindowManager.getMainScreen().getSelectedNode();
		// check single study was selected.
		int numOfStudy = 0;
		for(DICOMNode node :nodes) {
			int level = node.getLevel();
			if(level == DICOMNode.STUDY) {
				numOfStudy++;
			}
		}
		if(numOfStudy > 1) {
			JOptionPane.showMessageDialog(this, "Please select only one study node(table row)");
			return;
		}
		
		/*
		 * do not include both image and movie (avi,mpeg) at same time
		 * a movie is always set to one series.
		 */
		File[] files = jfc.getSelectedFiles();
		boolean isVideo = false;
		boolean isImage = false;
		boolean isPDF = false;
		for(File f : files) {
			if(ImageUtils.isVideoFile(f.getAbsolutePath())) {
				isVideo = true;
			}
			if(ImageUtils.isImageFile(f.getAbsolutePath())) {
				isImage = true;
			}
			if(ImageUtils.isPDF(f.getAbsolutePath())) {
				isPDF = true;
			}
			/*
			 * 画像以外の隠しファイルなどの存在を許容する
			 */
//			if(isVideo) {
//				if(childs.length > 1) {
//					JOptionPane.showMessageDialog(this, "Sorry, can not including multi video files at same time.");
//					return;
//				}
//			}
			if((isVideo && isImage) || (isPDF && isImage) || (isVideo && isPDF)) {
				JOptionPane.showMessageDialog(this, "Sorry, can not including both image,movie,pdf at same time.");
				return;
			}
		}
		//create temp dir
		Calendar now = Calendar.getInstance();
		File tempParentDir = new File(Utils.getConfSubDirPath(ConfigInfo.TemporalDirName));
		String tempDirName = now.getTime().toString().replace(":", "_");
		Path tempDir = null;
		File dirInTemp = null;
		if(!new File(tempParentDir.getAbsolutePath()+File.separator+tempDirName).exists()) {
			Path root = tempParentDir.toPath();
			try {
				// do not include ":" in path.
				tempDir = Files.createTempDirectory(root, tempDirName);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			boolean ok = new File(dirInTemp.getParentFile(), now.getTime().toString()).mkdirs();//could not create...
		}else {
			//to be empty
			dirInTemp = new File(tempParentDir.getAbsolutePath()+File.separator+tempDirName);
			File[] no_need = dirInTemp.listFiles();
			for(File f:no_need) {
				if(f.isFile()) {
					f.delete();
				}
			}
			no_need = null;
		}
		if(tempDir == null) {
			System.out.println("temp dir creation failed...:NonDicomImport");
			return;
		}else {
			dirInTemp = tempDir.toFile();
			dirInTemp.deleteOnExit();//if files in folder, not work. after this, use FileUtils.deleteFolder.
		}
		if(isImportToExistingStudy) {
			/*
			 * get study uid
			 */
			String pname = nodes.get(0).getData(DICOMNode.PatientName);
			String pid = nodes.get(0).getData(DICOMNode.PatientID);
			String sex = nodes.get(0).getData(DICOMNode.Sex);
			String dob = nodes.get(0).getData(DICOMNode.BirthDate);
			String studyUID = nodes.get(0).getData(DICOMNode.StudyInstanceUID);
			
			DatabaseHandler db = DatabaseHandler.getInstance();
			
			int numOfSeries = db.getNumOfSeriesInStudy(pid, studyUID);
			String studyID = db.getStudyInfoByUIDs(pid, studyUID).get("StudyUID");
			Integer studyIdInt = null;
			if(studyID != null) {
				try {
					studyIdInt = Integer.parseInt(studyID);
				}catch (NumberFormatException e) {
					//do nothing
					studyIdInt = null;
				}
			}
			HashMap<String,String> keys = panel.getInputs();
			String seriesDesc = keys.get("SeriesDesc");
			String seriesInstUID = UIDUtils.createUID();
			for(int i=0; i<files.length; i++) {
				String p2f = files[i].getAbsolutePath();
				/*
				 * if input image files,
				 * same study, same series.
				 * if input avi files,
				 * same study, another series.
				 */
				if(ImageUtils.isImageFile(p2f)) {
					ImageToDicom itd = new ImageToDicom();
					/*
					 * TODO check seriesNo exists ...?
					 * TODO seriesinstUID share
					 */
					DicomObject sc_imgObj = itd.convert(new ImagePlus(p2f), pname, pid, sex, dob, studyIdInt, numOfSeries+1, i+1, false);
					sc_imgObj.setString(Tag.SeriesDescription, VR.LO, seriesDesc);
					sc_imgObj.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
					sc_imgObj.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstUID);
//					sc_imgObj.updateFileMetaInfo();
					//save to tmp
					DicomWriter.newDicomWriter().write(sc_imgObj, UID.SecondaryCaptureImageStorage.uid(), dirInTemp.getAbsolutePath()+File.separator+i);
				}else if(files[i].getAbsolutePath().endsWith("avi") && ImageUtils.isVideoFile(p2f)) {
					ImageToDicom itd = new ImageToDicom();
					/*
					 * TODO check seriesNo exists ...?
					 * video is series level handling.
					 */
					ImagePlus imp = AVI_Reader.open(files[i].getAbsolutePath(), false);
					DicomObject sc_imgObj = itd.convert(imp, pname, pid, sex, dob, studyIdInt, numOfSeries++, 1, false);
					/*
					 * seriesUID is created every SC creation.
					 */
					sc_imgObj.setString(Tag.SeriesDescription, VR.LO, seriesDesc);
					sc_imgObj.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
//					sc_imgObj.updateFileMetaInfo();
					//save to tmp
					DicomWriter.newDicomWriter().write(sc_imgObj, UID.SecondaryCaptureImageStorage.uid() ,dirInTemp.getAbsolutePath()+File.separator+i);
				}else if(files[i].getAbsolutePath().endsWith("pdf") && isPDF) {
					PDFReader pdfReader = new PDFReader(files[i]);
					File dest = new File(dirInTemp.getAbsolutePath()+File.separator+"_pdf"+i);
					//convert pdf to dcm and save
					pdfReader.convert2DCM(files[i], dest, pname, pid, dob, sex, null, null, null, null, null, studyIdInt, numOfSeries+1, studyUID, seriesInstUID, false);
					pdfReader.close();
				}
			}
		}else{
			/*
			 * imoprt new
			 */
			HashMap<String,String> keys = panel.getInputs();
			String pname = keys.get("PatientName");
			if(pname == null || pname.trim().equals("")) {
				pname = NoName;
			}
			String pid = keys.get("PatientID");
			if(pid == null || pid.trim().equals("")) {
				pid = NoPID;
			}
			String dob = keys.get("BirthOfDate");
			String sex = keys.get("Sex");
			String seriesDesc = keys.get("SeriesDesc");
			DatabaseHandler db = DatabaseHandler.getInstance();
			if(db.getPatientName(pid) != null) {
				int res = JOptionPane.showConfirmDialog(this, "This patient already exist, do you want to integrate ?");
				if(res != JOptionPane.OK_OPTION) {
					return;
				}
			}
			//create new study
			String studyInstUID = UIDUtils.createUID();
			String seriesInstUID = UIDUtils.createUID();
			int numOfSeries = 1;
			for(int i=0; i<files.length; i++) {
				String p2f = files[i].getAbsolutePath();
				if(ImageUtils.isImageFile(p2f)) {
					ImageToDicom itd = new ImageToDicom();
					DicomObject sc_imgObj = itd.convert(new ImagePlus(p2f), pname, pid, sex, dob, null, numOfSeries, i+1, false);
					sc_imgObj.setString(Tag.SeriesDescription, VR.LO, seriesDesc);
					sc_imgObj.setString(Tag.StudyInstanceUID, VR.UI, studyInstUID);
					sc_imgObj.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstUID);
//					sc_imgObj.updateFileMetaInfo();
					//save to tmp
					DicomWriter.newDicomWriter().write(sc_imgObj, UID.SecondaryCaptureImageStorage.uid() ,dirInTemp.getAbsolutePath()+File.separator+i);
				}else if(files[i].getAbsolutePath().endsWith("avi") && ImageUtils.isVideoFile(p2f)) {
					ImageToDicom itd = new ImageToDicom();
					/*
					 * TODO check seriesNo exists ...?
					 * video is series level handling.
					 */
					ImagePlus imp = AVI_Reader.open(files[i].getAbsolutePath(), false);
					DicomObject sc_imgObj = itd.convert(imp, pname, pid, sex, dob, null, numOfSeries++, 1, false);
					/*
					 * seriesUID is created every SC creation.
					 */
					sc_imgObj.setString(Tag.SeriesDescription, VR.LO, seriesDesc);
					sc_imgObj.setString(Tag.StudyInstanceUID, VR.UI, studyInstUID);
//					sc_imgObj.updateFileMetaInfo();
					//save to tmp
					DicomWriter.newDicomWriter().write(sc_imgObj, UID.SecondaryCaptureImageStorage.uid() ,dirInTemp.getAbsolutePath()+File.separator+i);
				}else if(files[i].getAbsolutePath().endsWith("pdf") && isPDF) {
					PDFReader pdfReader = new PDFReader(files[i]);
					File dest = new File(dirInTemp.getAbsolutePath()+File.separator+"_pdf"+i);
					//convert pdf to dcm and save
					pdfReader.convert2DCM(files[i], dest, pname, pid, dob, sex, null, null, null, null, null, null, numOfSeries+1, studyInstUID, seriesInstUID, false);
					pdfReader.close();
				}
			}
		}
		//send to graphy db...
		if(dirInTemp.listFiles() != null) {
			DimseUtilities.sendMe(dirInTemp.listFiles());
		}
		Utils.eraseTemporalDirContents();
	}
}
