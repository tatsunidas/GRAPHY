/**
 * copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.imageio;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;

import com.vis.core.util.ByteUtils;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Modality;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import io.github.tatsunidas.ij.plugin.nifti.NiftiHeader;

/**
 * このクラスの目的は、NIfTIを、DICOMビューワで閲覧可能な必要最小限のDICOMデータに変換すること。
 * 解析を目的とする場合は変換は行わず、別の解析環境でNIfTI単体で行うことが推奨される。
 * 
 * dcm2niixなどでは、DICOMメタデータをjsonで保持している。
 * このjsonを一緒に入力することで、json内のメタデータをDICOMへ記録できる。
 * 
 */
public class NIfTI2DCM {
	
	final static SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd");
    final static SimpleDateFormat sdfTime = new SimpleDateFormat("HHmmss");
    
    public static void saveAsDicom(
    		File nifti, 
    		File metaJSON, //null-able
    		String outputDir, // null-able
    		Modality mm, //null-able
    	    String patientId,
    	    String patientName,
    	    String studyUID,
    	    String seriesUID,
    	    java.util.Date studyDate,
    	    int seriesNumber
    		) throws IOException {
    	
    	Nifti_Reader nr = new Nifti_Reader();
    	ImagePlus images = nr.load(nifti.getParentFile().getCanonicalPath(), nifti.getName());
    	NiftiHeader hdr = nr.getHeader();//after load()
    	FileInfo fi = nr.getFileInfo();//after load()
    	String sopClassUID = sopClassUidOf(mm).uid();
        boolean isColor = images.isRGB();
        int BitsAllocated = isColor ? 8:images.getBitDepth();
        int w = images.getWidth();
        int h = images.getHeight();
        /*
         * すべてUnsignedとして扱う
         */
        boolean isSigned = false;
        
        boolean bigEndian = !fi.intelByteOrder;
        
        /*
         * signed対策
         */
        Calibration cal = images.getCalibration();
        double[] spacingYX = new double[] {cal.pixelHeight, cal.pixelWidth};
        double sliceThickness = cal.pixelDepth;
        double rescaleIntercept = 0.0;
        double rescaleSlope = 1.0;
        double[] coeff = cal.getCoefficients();
        if (coeff != null && coeff.length >= 2) {
            rescaleIntercept = coeff[0];
            rescaleSlope = coeff[1];
        }
        
        // NIfTIのアフィン行列(RAS)をDICOMのLPS空間行列に変換
        // sform が有効であると仮定（srow_x, y, z を使用）
        // LPSにするため、X(0行目)とY(1行目)の符号を反転させます
        double m00 = -hdr.srow_x[0]; double m01 = -hdr.srow_x[1]; double m02 = -hdr.srow_x[2]; double m03 = -hdr.srow_x[3];
        double m10 = -hdr.srow_y[0]; double m11 = -hdr.srow_y[1]; double m12 = -hdr.srow_y[2]; double m13 = -hdr.srow_y[3];
        double m20 =  hdr.srow_z[0]; double m21 =  hdr.srow_z[1]; double m22 =  hdr.srow_z[2]; double m23 =  hdr.srow_z[3];

        // IOPの計算（0列目がRowベクトル、1列目がColベクトル）と正規化
        double rLen = Math.sqrt(m00*m00 + m10*m10 + m20*m20);
        double cLen = Math.sqrt(m01*m01 + m11*m11 + m21*m21);

         // ベクトルが0（行列が無効）の場合は基本のAxialとしてフォールバック
        double[] iop;
        if (rLen == 0 || cLen == 0) {
            iop = new double[]{1.0, 0.0, 0.0, 0.0, 1.0, 0.0};
        } else {
            iop = new double[]{m00/rLen, m10/rLen, m20/rLen, m01/cLen, m11/cLen, m21/cLen};
        }
        
        // 4D（hyper stack）対応
        int nChannels = images.getNChannels();
        int nSlices = images.getNSlices(); // 空間のZ方向
        int nFrames = images.getNFrames(); // 時間方向
        
        int instNo = 1;
        
        // 時間 -> チャンネル -> Zスライスの順でループを回す
        for (int t = 1; t <= nFrames; t++) {
            for (int c = 1; c <= nChannels; c++) {
                for (int z = 1; z <= nSlices; z++) {
                    
                    // 空間上のスライスインデックス(k)は0始まり
                    int k = z - 1;
                    
                    // 現在のスライスkにおけるIPP (Image Position Patient) の計算
                    // IPP = M * (0, 0, k, 1)^T
                    double ippX = m03 + m02 * k;
                    double ippY = m13 + m12 * k;
                    double ippZ = m23 + m22 * k;
                    double[] ipp = new double[]{ippX, ippY, ippZ};
                    
                    // ImagePlusから該当する1枚を取得
                    images.setPosition(c, z, t);
                    
                    byte[] pixelDataBytes = null;
                    if(BitsAllocated == 8 && !isColor) {
                        ByteProcessor bp = (ByteProcessor) images.getProcessor();
                        pixelDataBytes = (byte[])bp.getPixels();
                    }else if(BitsAllocated == 16) {
                        ShortProcessor sp = (ShortProcessor) images.getProcessor();
                        pixelDataBytes = ByteUtils.shortToBytes((short[])sp.getPixels(), bigEndian);
                    }else if(BitsAllocated == 32) {
                        FloatProcessor fp = (FloatProcessor) images.getProcessor();
                        pixelDataBytes = ByteUtils.floatToBytes((float[])fp.getPixels(), bigEndian);
                    }
                                        
                    saveAsDicom(
                            outputDir,
                            sopClassUID,
                            patientId,
                            patientName,
                            studyUID,
                            seriesUID,
                            studyDate,
                            seriesNumber,
                            instNo,
                            isColor,
                            BitsAllocated,
                            w,
                            h,
                            isSigned,
                            rescaleIntercept,
                            rescaleSlope,
                            ipp,
                            iop,
                            spacingYX,
                            sliceThickness,
                            pixelDataBytes,
                            metaJSON);
                    
                    instNo++;
                }
            }
        }
    }
	
    /**
     * Save dicom instance
     * 
     * @param outputDirPath
     * @param SOPClassUID
     * @param patientId
     * @param patientName
     * @param studyUID
     * @param seriesUID
     * @param studyDate
     * @param seriesNumber
     * @param instanceNumber
     * @param isColor
     * @param BitsAllocated
     * @param width
     * @param height
     * @param isSigned
     * @param rescaleIntercept
     * @param rescaleSlope
     * @param ipp
     * @param iop
     * @param spacing
     * @param sliceThickness
     * @param pixelDataBytes
     * @param metaJSON
     */
	private static void saveAsDicom(String outputDirPath, String SOPClassUID, // UID.MRImageStorage etc
			String patientId, String patientName, String studyUID, String seriesUID, java.util.Date studyDate,
			int seriesNumber, int instanceNumber, boolean isColor, int BitsAllocated, int width, int height,
			boolean isSigned, double rescaleIntercept, double rescaleSlope, double[] ipp, double[] iop,
			double[] spacing, // y,x
			double sliceThickness, byte[] pixelDataBytes, File metaJSON) {
		
		try {
			File outDir = new File(outputDirPath);
            if (!outDir.exists()) {
            	outDir.mkdirs();
            }
            
			DicomObject ds = DicomObject.newDicomObject();
			// -- Patient / Study / Series --
			ds.setString(Tag.PatientName, VR.PN, patientName);
			ds.setString(Tag.PatientID, VR.LO, patientId);
			// ds.setString(Tag.PatientSex, VR.CS, "O");

			ds.setDate(Tag.StudyDate, VR.DA, studyDate);
			ds.setDate(Tag.StudyTime, VR.TM, studyDate);
			ds.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
			ds.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);

			ds.setString(Tag.SOPClassUID, VR.UI, SOPClassUID);
			ds.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());

			ds.setString(Tag.Modality, VR.CS, "MR");
			ds.setString(Tag.StudyDescription, VR.LO, "Converted From NIfTI");
			ds.setString(Tag.SeriesDescription, VR.LO, "NIfTI to DICOM");
			ds.setInt(Tag.SeriesNumber, VR.IS, seriesNumber);
			ds.setInt(Tag.InstanceNumber, VR.IS, instanceNumber);

			// -- Image Pixel --
			ds.setInt(Tag.SamplesPerPixel, VR.US, isColor ? 3 : 1);
			ds.setString(Tag.PhotometricInterpretation, VR.CS, isColor ? "RGB" : "MONOCHROME2");
			if(isColor) {
				ds.setInt(Tag.PlanarConfiguration, VR.US, 0/*rgbrgbrgb*/);
			}
			ds.setInt(Tag.Rows, VR.US, height);
			ds.setInt(Tag.Columns, VR.US, width);
			ds.setInt(Tag.BitsAllocated, VR.US, isColor ? 8:BitsAllocated);
			ds.setInt(Tag.BitsStored, VR.US, isColor ? 8:BitsAllocated);
			ds.setInt(Tag.HighBit, VR.US, isColor ? 8-1:BitsAllocated-1);
			ds.setInt(Tag.PixelRepresentation, VR.US, isSigned ? 1 : 0);

			ds.setDouble(Tag.RescaleSlope, VR.DS, rescaleSlope);
			ds.setDouble(Tag.RescaleIntercept, VR.DS, rescaleIntercept);
//            ds.setDouble(Tag.WindowCenter, VR.DS, 100.0);
//            ds.setDouble(Tag.WindowWidth, VR.DS, 200.0);

			// -- Image Plane --
			ds.setDouble(Tag.ImagePositionPatient, VR.DS, ipp);
			ds.setDouble(Tag.ImageOrientationPatient, VR.DS, iop);
			ds.setDouble(Tag.PixelSpacing, VR.DS, spacing);
			ds.setDouble(Tag.SliceThickness, VR.DS, sliceThickness);
			ds.setDouble(Tag.SpacingBetweenSlices, VR.DS, sliceThickness);

			// -- Pixel Data --
			ds.setBytes(Tag.PixelData, VR.OW, pixelDataBytes);

			// -- Write File --
			File outputFile = new File(outDir, String.format("slice_%04d.dcm", instanceNumber));
			DicomWriter writer = DicomWriter.newDicomWriter();
			writer.write(ds, UID.ExplicitVRLittleEndian.uid(), outputFile.getCanonicalPath());
			
		}catch(IOException e) {
			
		}
		
	}
	
	private static UID sopClassUidOf(Modality m) {
		if(m == Modality.CT) {
			return UID.CTImageStorage;
		}else if(m == Modality.ST) {
			return UID.NuclearMedicineImageStorage;
		}else if(m == Modality.PT) {
			return UID.PositronEmissionTomographyImageStorage;
		}else {
			//default
			return UID.MRImageStorage;
		}
	}

}
