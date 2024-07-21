package com.vis.dicom;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomStreamException;
import org.dcm4che3.tool.dcmdir.DcmDir;
import org.dcm4che3.util.SafeClose;

import com.vis.core.log.Log;
import com.vis.core.util.Utils;

public class DicomUtilities {
	
	static Logger logger = Log.logger;
	
	public static boolean isDicomFile(File file) {
		if (file.isDirectory()) {
			System.out.println("this file is directory");
			return false;
		}
		FileInputStream fileinstream = null;
		try {
			fileinstream = new FileInputStream(file);
			byte[] dcm = new byte[4];
			fileinstream.skip(128);
			@SuppressWarnings("unused")
			int read = fileinstream.read(dcm, 0, 4);// IMPORTANT
			if (dcm[0] == 68 && dcm[1] == 73 && dcm[2] == 67 && dcm[3] == 77) {
				return true;
			}
		} catch (FileNotFoundException ex) {
			logger.severe(ex.toString());
			return false;
		} catch (IOException ex) {
			logger.severe(ex.toString());
			return false;
		} finally {
			try {
				fileinstream.close();
			} catch (IOException e) {
				e.printStackTrace();
				logger.severe("Fail to read file...:isDicomFile\n"+e);
			}
		}
		return false;
	}

	public boolean dicomExists(File f) {
		if (f.isDirectory()) {
			File[] files = f.listFiles();
			for (File child : files) {
				if (child.isDirectory()) {
					return dicomExists(child);
				} else {
					return isDicomFile(child);
				}
			}
		// single file
		} else {
			return isDicomFile(f);
		}
		return false;
	}
	
	public static boolean isDICOMDIR(File file) {
		if (namedDICOMDIR(file)) {
			if (DicomUtilities.isDicomFile(file)) {
				if (Utils.isDebug) {logger.info("DICOMDIR: "+ file.getName() +" found.");}
				return true;
			}
		}
		return false;
	}
	
	public static boolean namedDICOMDIR(File f) {
		if (f.getName().toLowerCase().startsWith("dicomdir")) {
				return true;
		}else {
			return false;
		}
	}
	
//	public static Attributes readDicomObject(String path, boolean withPixel){
//		DicomInputStream dis = null;
//		Attributes dataset = null;
//		@SuppressWarnings("unused")
//		Attributes fmi = null;
//		try {
//			dis = new DicomInputStream(new File(path));
//			dis.setIncludeBulkData(IncludeBulkData.URI);
//			fmi = dis.readFileMetaInformation();
//			if (!withPixel) {
//				dataset = dis.readDatasetUntilPixelData();
//			} else {
//				dataset = dis.readDataset(-1, o -> false);
//			}
//		}catch(DicomStreamException dse) {
//			logger.error("Reading dicom file...:getDicomAttribute",dse);
//			return null;
//		} catch (IOException e) {
//			e.printStackTrace();
//			logger.error("Reading dicom file...:getDicomAttribute",e);
//			return null;
//		}finally {
//			SafeClose.close(dis);
//		}
//		return dataset;
//	}
//	
//	public static Attributes readDicomObject(File dcmFile, boolean withPixel){
//		DicomInputStream dis = null;
//		Attributes dataset = null;
//		@SuppressWarnings("unused")
//		Attributes fmi = null;
//		try {
//			dis = new DicomInputStream(dcmFile);
//			dis.setIncludeBulkData(IncludeBulkData.URI);
//			fmi = dis.readFileMetaInformation();
//			if (!withPixel) {
//				dataset = dis.readDatasetUntilPixelData();
//			} else {
//				dataset = dis.readDataset(-1, o -> false);
//			}
//		}catch(DicomStreamException dse) {
//			logger.error("Reading dicom file...:getDicomAttribute",dse);
//			return null;
//		} catch (IOException e) {
//			e.printStackTrace();
//			logger.error("Reading dicom file...:getDicomAttribute",e);
//			return null;
//		}finally {
//			SafeClose.close(dis);
//		}
//		return dataset;
//	}
	
//	public static void writeDicomObject(Attributes dataset, String tsuid, String dest) {
//		DicomOutputStream dos = null;
//		try {
//			dos = new DicomOutputStream(new File(dest));
//			Attributes fmi = dataset.createFileMetaInformation(tsuid);
//			dos.writeFileMetaInformation(fmi);
//			dataset.writeTo(dos);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} finally {
//			try {
//				dos.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			logger.info("Write Dicom File Done.");
//		}
//	}
	
		
	public static Object getDicomElement(String path, int tag){
		DicomInputStream dis = null;
		try {
			dis = new DicomInputStream(new File(path));
			final Attributes dataset = dis.readDatasetUntilPixelData();
			if(tag == Tag.TransferSyntaxUID) {
				return dis.getTransferSyntax();
			}
			return dataset.getValue(tag);//getValue return byte[].
		}catch(DicomStreamException dse) {
			logger.severe("Reading dicom file...:getDicomAttribute\n"+dse);
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe("Reading dicom file...:getDicomAttribute\n"+e);
			return null;
		}finally {
			SafeClose.close(dis);
		}
	}
	
	public static String getPatientID(String path) {
		return new String((byte[])getDicomElement(path, Tag.PatientID));
	}
	
	public static String[] getPatientInfo(String path) {
		String[] ids = new String[4];
		DicomInputStream dis = null;
		try {
			dis = new DicomInputStream(new File(path));
			final Attributes dataset = dis.readDatasetUntilPixelData();
			ids[0] = dataset.getString(Tag.PatientID);
			ids[1] = dataset.getString(Tag.PatientName);
			ids[2] = dataset.getString(Tag.PatientBirthDate);
			ids[3] = dataset.getString(Tag.PatientSex);
			return ids;
		}catch(DicomStreamException dse) {
			logger.severe("Reading dicom file...:getDicomAttribute\n"+dse);
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe("Reading dicom file...:getDicomAttribute\n"+e);
			return null;
		}finally {
			SafeClose.close(dis);
		}
	}
	
	public static String getStudyInstanceUID(String path) {
		return new String((byte[])getDicomElement(path, Tag.StudyInstanceUID));
	}
	
	public static String getSeriesInstanceUID(String path) {
		return new String((byte[])getDicomElement(path, Tag.SeriesInstanceUID));
	}
	
	public static String getSOPInstanceUID(String path) {
		return new String((byte[])getDicomElement(path, Tag.SOPInstanceUID));
	}
	
	public static String getSOPClassUID(String path) {
		return new String((byte[])getDicomElement(path, Tag.SOPClassUID));
	}
	
	public static String getTransferSyntaxUID(String path) {
		return (String) getDicomElement(path, Tag.TransferSyntaxUID);
	}
	
	public static String[] getUIDSet(String path) {
		String[] ids = new String[4];
		DicomInputStream dis = null;
		try {
			dis = new DicomInputStream(new File(path));
			final Attributes dataset = dis.readDatasetUntilPixelData();
			ids[0] = dataset.getString(Tag.PatientID);
			ids[1] = dataset.getString(Tag.StudyInstanceUID);
			ids[2] = dataset.getString(Tag.SeriesInstanceUID);
			ids[3] = dataset.getString(Tag.SOPInstanceUID);
			//ids[x] = dis.getTransferSyntax();
			//ids[x] = dataset.getString(Tag.TransferSyntaxUID)
			return ids;
		}catch(DicomStreamException dse) {
			logger.severe("Reading dicom file...:getDicomAttribute\n"+dse);
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe("Reading dicom file...:getDicomAttribute\n"+e);
			return null;
		}finally {
			SafeClose.close(dis);
		}
	}
	
	/*
	 * DO NOT USE saveAsLink.
	 * Directory Records
	 * ReferencedFileID=DICOM/study hash/series hash/instance hash
	 */
	public static String convertAbsPath2ReferencedFileID(String absPath, boolean isLink) {
		if(isLink) {
			return absPath;
		}
		String sep = File.separator;
		String searchString = sep+"archive"+sep+"DICOM";
		int last = absPath.lastIndexOf(sep+"archive"+sep+"DICOM");
		String ReferencedFileID = absPath.substring(last+searchString.length()-5, absPath.length());
		return ReferencedFileID;
	}
	
	// JISAutoDetect
	// https://docs.oracle.com/javase/jp/6/technotes/guides/intl/encoding.doc.html
	public static boolean isJISEncode(Attributes in) {
		String scs = new String((byte[]) in.getValue(Tag.SpecificCharacterSet));
		if (scs.contains("JP") || scs.contains("MS932") || scs.contains("JIS") || scs.contains("ISO 2022 IR 87")
				|| scs.contains("ISO 2022 IR 6")) {
			return true;
		}
		return false;
	}
	
	public static void attachDICOMDIRTo(String path2PatDir) {
		/*
		 * path:"DICOM" folder root:"Pname" folder
		 */
		String[] cmd = { "-c", path2PatDir + File.separator + "DICOMDIR", path2PatDir };
		try {
			DcmDir.main(cmd);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}
}
