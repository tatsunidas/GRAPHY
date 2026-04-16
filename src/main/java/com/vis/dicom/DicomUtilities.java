package com.vis.dicom;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomStreamException;
import org.dcm4che3.tool.dcmdir.DcmDir;
import org.dcm4che3.util.SafeClose;

import com.vis.core.log.Log;

public class DicomUtilities {
	
	static Logger logger = Log.logger;
	
	public static boolean isDicomFile(File file) {
		// 1. Reject directories or non-existent files
		if (file == null || !file.isFile()) {
			System.out.println("This file is invalid or a directory.");
			return false;
		}

		// Automate close() using try-with-resources statement (also prevents
		// NullPointerException)
		try (FileInputStream fis = new FileInputStream(file)) {

			// Prepare a buffer to read 132 bytes at once (avoid using skip())
			byte[] buffer = new byte[132];
			int bytesRead = fis.read(buffer, 0, 132);

			// If the file size is too small, it's not a DICOM file
			if (bytesRead < 4) {
				return false;
			}

			// ==========================================
			// Pattern A: Standard DICOM (Part 10 compliant)
			// Check if bytes 128-131 contain the string "DICM"
			// ==========================================
			if (bytesRead == 132 && buffer[128] == 68 && buffer[129] == 73 && buffer[130] == 67 && buffer[131] == 77) {
				return true;
			}

			// ==========================================
			// Pattern B: DICOM without preamble (Non-standard but common in practice)
			// Check if the file starts with specific DICOM tags (Group 0002 or Group 0008)
			// ==========================================
			// For Little Endian: 0x02 0x00 (Group 0002) or 0x08 0x00 (Group 0008)
			// For Big Endian: 0x00 0x02 or 0x00 0x08
			boolean startsWithGroup0002 = (buffer[0] == 0x02 && buffer[1] == 0x00)
					|| (buffer[0] == 0x00 && buffer[1] == 0x02);
			boolean startsWithGroup0008 = (buffer[0] == 0x08 && buffer[1] == 0x00)
					|| (buffer[0] == 0x00 && buffer[1] == 0x08);

			if (startsWithGroup0002 || startsWithGroup0008) {
				// Treat as DICOM since it starts with a DICOM data structure, even without
				// "DICM"
				return true;
			}

		} catch (IOException ex) {
			logger.severe("Fail to read file...:isDicomFile\n" + ex.toString());
			return false;
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
		if (file == null || !file.isFile()) {
			return false;
		}
		DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
		try {
			reader.read(file.getCanonicalPath(), false);
			DicomObject fmi = reader.getFileMetaInfomation();
			if (fmi != null) {
				String mediaStorageSopClassUID = fmi.getString(Tag.MediaStorageSOPClassUID);

				// "1.2.840.10008.1.3.10" (Media Storage Directory Storage)
				if (UID.MediaStorageDirectoryStorage.uid().equals(mediaStorageSopClassUID)) {
					if (com.vis.core.util.Utils.isDebug) {
						logger.info("DICOMDIR (UID Verified): " + file.getName() + " found.");
					}
					return true;
				}
			}
		} catch (IOException e) {
			// ignore
		}finally {
			reader = null;
		}
		return false;
	}
	
//	public static boolean namedDICOMDIR(File f) {
//		if (f.getName().toLowerCase().startsWith("dicomdir")) {
//				return true;
//		}else {
//			return false;
//		}
//	}
		
	private static Object getDicomElement(String path, int tag){
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
		/*
		 * TSUID is return as String.
		 */
		return (String)getDicomElement(path, Tag.TransferSyntaxUID);
	}
	
	public static String getFrameOfReferenceUID(String path) {
		Object refUID = getDicomElement(path, Tag.FrameOfReferenceUID);
		if (refUID != null) {
			return new String((byte[]) getDicomElement(path, Tag.FrameOfReferenceUID));
		}
		return null;
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
	
	/**
	 * path2PatDir
	 * 		- DICOM
	 * 			- STUDY1
	 *  		- STUDY2
	 * @param path2PatDir
	 */
	public static void attachDICOMDIRTo(String path2PatDir) {
		/*
		 * path:"DICOM" folder root:"Pname" folder
		 */
		String[] cmd = { "-c", path2PatDir + File.separator + "DICOMDIR", path2PatDir };
		try {
			DcmDir.main(cmd);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
	}
}
