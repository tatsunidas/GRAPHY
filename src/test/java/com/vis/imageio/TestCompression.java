/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.imageio;

import com.vis.dicom.Tag;
import com.vis.dicom.UID;

import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;

import org.dcm4che3.imageio.codec.ImageWriterFactory.ImageWriterParam;

import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.VR;
import com.vis.dicom.image.BufferedImageUtils;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.PhotometricInterpretation;

public class TestCompression {

	public static void main(String[] args) {
		
		//8 bit grayscale image to test
//		String rgb = "/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/JIRA_DICOM/US_LEE_IR6.dcm";
//		DicomImage gray = create8BitGrayDicomSampleFrom(rgb);
//		DicomWriter writer = DicomWriter.newDicomWriter();
//		writer.write(gray.getCore(), gray.getTSUID().uid(), "test_gray.dcm");
		
//		for (String type : ImageIO.getWriterFormatNames()) {
//			System.out.println(type);
//		}
		
		String gray8Path = "test_gray.dcm";
		String gray16Path = "/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/JIRA_DICOM/CT_LEE_IR87a.dcm";
		String rgbPath = "/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/JIRA_DICOM/US_LEE_IR6.dcm";
		
		DicomWriter writer = DicomWriter.newDicomWriter();
		
		/*
		 *  -N <near-lossless>           Near-Lossless parameter of JPEG LS Lossy compression
		 *  おそらく、0~1 or 0~100。テストしてみて。
		 *  
		 *  
		 *  -q <quality>                 compression quality (0.0-1.0) of JPEG Lossy
                              compression
           -Q <compression>             compression factor (5-100) of JPEG 2000
                              Lossy compression
		 */
		
		//8 bit
		//jpeg 8-bit
//		DicomImage dcm = DicomImage.newDicomImage(gray8Path, DICOMBackend.DCM4CHE);
//		Compressor c = Compressor.newInstance(dcm.getCore(), dcm.getTSUID().uid(), DICOMBackend.DCM4CHE);
//		c.compress(Codec.JPEGBase.uidString(), new String[] {"compressionQuality=0.8"});
//		dcm = DicomImage.newDicomImage(dcm.getCore(), Codec.JPEGBase.uid);
//		writer.write(dcm.getCore(), dcm.getTSUID().uid(), "test_jpegbaseline8bit.dcm");
		
		//jpeg 12-bit -8 bit basis- 16-bit data must be encoded with syntax 1.2.840.10008.1.2.4.57 or 1.2.840.10008.1.2.4.70 
//		DicomImage dcm = DicomImage.newDicomImage(gray8Path, DICOMBackend.DCM4CHE);
//		Compressor c = Compressor.newInstance(dcm.getCore(), dcm.getTSUID().uid(), DICOMBackend.DCM4CHE);
//		c.compress(Codec.JPEGExtended12Bit.uidString(), new String[] {"compressionQuality=0.8"});
//		dcm = DicomImage.newDicomImage(dcm.getCore(), Codec.JPEGExtended12Bit.uid);
//		writer.write(dcm.getCore(), dcm.getTSUID().uid(), "test_jpegextended12bit.dcm");
		
		//jpeg2000
//		DicomImage dcm = DicomImage.newDicomImage(gray16Path, DICOMBackend.DCM4CHE);
//		Compressor c = Compressor.newInstance(dcm.getCore(), dcm.getTSUID().uid(), DICOMBackend.DCM4CHE);
//		c.compress(Codec.JPEG2000.uidString(), new String[] {"compressionRatiofactor=80"});
//		dcm = DicomImage.newDicomImage(dcm.getCore(), Codec.JPEG2000.uid);
//		writer.write(dcm.getCore(), dcm.getTSUID().uid(), "test_jpeg2000.dcm");
		
		//jpeg2000-lossless
//		DicomImage dcm = DicomImage.newDicomImage(gray16Path, DICOMBackend.DCM4CHE);
//		Compressor c = Compressor.newInstance(dcm.getCore(), dcm.getTSUID().uid(), DICOMBackend.DCM4CHE);
//		c.compress(Codec.JPEG2000LOSSLESS.uidString(), new String[] {});
//		dcm = DicomImage.newDicomImage(dcm.getCore(), Codec.JPEG2000LOSSLESS.uid);
//		writer.write(dcm.getCore(), dcm.getTSUID().uid(), "test_jpeg2000lossless.dcm");
		
		//jpeg-lossless
//		DicomImage dcm = DicomImage.newDicomImage(gray16Path, DICOMBackend.DCM4CHE);
//		Compressor c = Compressor.newInstance(dcm.getCore(), dcm.getTSUID().uid(), DICOMBackend.DCM4CHE);
//		c.compress(Codec.JPEGLOSSLESS.uidString(), new String[] {});
//		dcm = DicomImage.newDicomImage(dcm.getCore(), Codec.JPEGLOSSLESS.uid);
//		writer.write(dcm.getCore(), dcm.getTSUID().uid(), "test_jpeglossless.dcm");
		
		//jpeg-ls
		//Force compression to JPEG-LS lossless as lossy is not adapted to signed data.
//		DicomImage dcm = DicomImage.newDicomImage(gray16Path, DICOMBackend.DCM4CHE);
//		Compressor c = Compressor.newInstance(dcm.getCore(), dcm.getTSUID().uid(), DICOMBackend.DCM4CHE);
//		c.compress(Codec.JPEG_LS.uidString(), new String[] {});
//		dcm = DicomImage.newDicomImage(dcm.getCore(), Codec.JPEG_LS.uid);
//		writer.write(dcm.getCore(), dcm.getTSUID().uid(), "test_jpegLSlossless.dcm");
		
		//jpeg-ls-near lossless
//		DicomImage dcm = DicomImage.newDicomImage(gray16Path, DICOMBackend.DCM4CHE);
//		Compressor c = Compressor.newInstance(dcm.getCore(), dcm.getTSUID().uid(), DICOMBackend.DCM4CHE);
//		c.compress(Codec.JPEG_NearLS.uidString(), new String[] {"nearLossless=80"});
//		dcm = DicomImage.newDicomImage(dcm.getCore(), Codec.JPEG_NearLS.uid);
//		writer.write(dcm.getCore(), dcm.getTSUID().uid(), "test_jpegNearLSlossless.dcm");
		
		//RGB-RLE
		//Unsupported Transfer Syntax: 1.2.840.10008.1.2.5
//		DicomImage dcm = DicomImage.newDicomImage(rgbPath, DICOMBackend.DCM4CHE);
//		Compressor c = Compressor.newInstance(dcm.getCore(), dcm.getTSUID().uid(), DICOMBackend.DCM4CHE);
//		c.compress(Codec.RLE.uidString(), new String[] {});
//		dcm = DicomImage.newDicomImage(dcm.getCore(), Codec.RLE.uid);
//		writer.write(dcm.getCore(), dcm.getTSUID().uid(), "test_RLE.dcm");
	}
	
	static DicomImage create8BitGrayDicomSampleFrom(String path) {
		DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
		reader.read(path);
		DicomObject dcm = reader.getCore();
		DicomImage img = DicomImage.newDicomImage(dcm, reader.checkTSUID());
		
		int samples = img.getCore().getInt(Tag.Samples​Per​Pixel, 0);
		boolean banded = img.getCore().getInt(Tag.Planar​Configuration, 0) != 0;
		byte[] pixels = img.getPixelData(0);		
		if(samples == 3 && banded) {
			int size = pixels.length/3;
			byte[] gray = new byte[size];
			byte[] r = new byte[size];
			byte[] g = new byte[size];
			byte[] b = new byte[size];
			for(int i=0; i<size; i++) {
				r[i] = pixels[i];
				g[i] = pixels[i+size];
				b[i] = pixels[i+size*2];
				gray[i] = (byte)((r[i]+g[i]+b[i])/3);
			}
			//int frame, int w, int h, int samples, int bitsPerPixel, byte[] newFrame
			img.setPixelData(0, img.getWidth(), img.getHeight(), img.getSamples(), img.getBitsAllocated(), gray);
			
			dcm.setInt(Tag.Samples​Per​Pixel, VR.US, 1);
	       dcm.setString(Tag.Photometric​Interpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.name());
	       
	       dcm.remove(Tag.Planar​Configuration);
	       
//	       dcm.setInt(Tag.Rows, VR.US, rows);
//	       dcm.setInt(Tag.Columns, VR.US, columns);
	       dcm.setInt(Tag.Bits​Allocated, VR.US, 8);
	       dcm.setInt(Tag.Bits​Stored, VR.US, 8);
	       dcm.setInt(Tag.High​Bit, VR.US, 7);
	       dcm.setInt(Tag.Pixel​Representation, VR.US, 0);
			
		}else if(samples == 3 && !banded) {
			int size = pixels.length/3;
			byte[] gray = new byte[size];
			byte[] r = new byte[size];
			byte[] g = new byte[size];
			byte[] b = new byte[size];
			for(int i=0; i<size; i+=3) {
				r[i] = pixels[i];
				g[i] = pixels[i+1];
				b[i] = pixels[i+2];
				gray[i] = (byte)((r[i]+g[i]+b[i])/3);
			}
			//int frame, int w, int h, int samples, int bitsPerPixel, byte[] newFrame
			img.setPixelData(0, img.getWidth(), img.getHeight(), img.getSamples(), img.getBitsAllocated(), gray);
			
			dcm.setInt(Tag.Samples​Per​Pixel, VR.US, 1);
	       dcm.setString(Tag.Photometric​Interpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.name());
	       
	       dcm.remove(Tag.Planar​Configuration);
	       
//	       dcm.setInt(Tag.Rows, VR.US, rows);
//	       dcm.setInt(Tag.Columns, VR.US, columns);
	       dcm.setInt(Tag.Bits​Allocated, VR.US, 8);
	       dcm.setInt(Tag.Bits​Stored, VR.US, 8);
	       dcm.setInt(Tag.High​Bit, VR.US, 7);
	       dcm.setInt(Tag.Pixel​Representation, VR.US, 0);
		}
		return img;
	}

}
