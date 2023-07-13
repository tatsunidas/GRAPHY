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
				logger.severe("Reading file...:isDicomFile\n"+e);
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
				logger.info("DICOMDIR: "+ file.getName() +" found.");
				return true;
			}
		}
		return false;
	}
	
	public static boolean namedDICOMDIR(File f) {
		if (f.getName().startsWith("dicomdir")) {
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
	
	/*
	 * DBにコピーしていればいいが、リンクの場合はこれではNG？
	 * Directory Records
	 * ReferencedFileID=DICOM/study hash/series hash/instance hash
	 * ReferencedFileIDはDicomDirにとって必要なものだと思う。
	 * QRやFindなどの、こちらがSCPとなりサービス提供する際は不要と思われる。
	 */
	public static String convertAbsPath2ReferencedFileID(String absPath, boolean isLink) {
		if(isLink) {
			/* この対応が正しいかテストしないといけない */ //see DatabaseHandler::findInstanceRecord**
			/* RetrieveTaskではFile.toURI().toString()で返している */
			/* エラー検知のためNULLに?? */
//			return null;
			return absPath;
		}
		String sep = File.separator;
		String searchString = sep+".GRAPHY"+sep+"archive"+sep+"DICOM";
		int last = absPath.lastIndexOf(sep+".GRAPHY"+sep+"archive"+sep+"DICOM");
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
	
////public static ImagePlus readUncompressedSingleFrameDcmAsImagePlus(Attributes dcm) {
////Object pixelData = dcm.getValue(Tag.PixelData);
////Fragments pixelDataFragments = null;
////if(pixelData instanceof Fragments) {
////	pixelDataFragments = (Fragments) pixelData;
////}else {
////	/* this image need not decompression? */
////	logger.info(" this image does not need decompression? return null.");
////	return null;
////}
////Object frag = pixelData;//frame number started from 1
////byte[] pixels = null;
////if (frag instanceof BulkData) {
////	try {
////		pixels = ((BulkData) frag).toBytes(VR.OB, false);
////	} catch (IOException e) {
////		// TODO Auto-generated catch block
////		e.printStackTrace();
////		return null;
////	}
////}else if(frag instanceof byte[]) {
////	pixels = (byte[])frag;
////}else {
////	/* I do not know how to handle other objects */
////	logger.info(" I do not know how to handle this file (other objects type). return null.");
////	return null;//return
////}
////
////}
}
