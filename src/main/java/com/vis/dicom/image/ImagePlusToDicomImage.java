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
package com.vis.dicom.image;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;

import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;

import ij.ImagePlus;
import ij.util.DicomTools;

/**
 * To create DicomObject from ImagePlus.
 * 
 * @author tatsunidas
 *
 */
public class ImagePlusToDicomImage {
	

	public static HashMap<Integer,DicomImage> imagePlusToDcm(
			ImagePlus imp, 
			boolean dealWithSecondaryCapture) {
		if(imp == null) {
			return null;
		}
		HashMap<Integer,DicomImage> images = new HashMap<>();
		int w = imp.getWidth();
		int h = imp.getHeight();
		int samples = imp.getNChannels();
		int bits = imp.getBitDepth();
		int s = imp.getNSlices();
		for(int i=0;i<s;i++) {
			DicomObject core = DicomObject.newDicomObject();
			addAttributes(core, i, imp, dealWithSecondaryCapture);
			//create DicomImage and update FMI.
			DicomImage dcmImg = DicomImage.newDicomImage(core, UID.ImplicitVRLittleEndian);
			Object pix = imp.getProcessor().getPixels();
			dcmImg.setPixelData(0, w, h, samples, bits, pix);
			images.put(i,dcmImg);
		}
		return images;
	}
	
	private static void addAttributes(DicomObject dcm, int slicePos/*0 to N-1*/, ImagePlus imp/*should be set current processor*/, boolean dealWithSecondaryCapture) {
		
		imp.setSlice(slicePos+1);
		
		String sopClassUID = DicomTools.getTag(imp, "0008,0016");
		if (dealWithSecondaryCapture) {
			sopClassUID = UID.SecondaryCaptureImageStorage.uid();
		}
		String sopInstUID = DicomTools.getTag(imp, "0008,0018");
		if(sopInstUID == null || sopInstUID.trim().length()==0) {
			sopInstUID = UIDUtils.createUID();
		}
		setString(dcm, Tag.SOP​Class​UID, sopClassUID);
		setString(dcm, Tag.SOP​Instance​UID, sopInstUID);
		
		/* 0010,0010  Patient's Name: TEST^TARO
		 * 0010,0020  Patient ID: 0000012345
		 * 0010,0030  Patient's Birth Date: 19840405
		 * 0010,0032  Patient's Birth Time: 000000
		 * 0010,0040  Patient's Sex: M 
		 * 0010,1030  Patient's Weight: 65
		 */
		String patID = DicomTools.getTag(imp, "0010,0020");
		String patName = DicomTools.getTag(imp, "0010,0010");
		String patBoD = DicomTools.getTag(imp, "0010,0030");
		String patBoT = DicomTools.getTag(imp, "0010,0032");
		String patSex = DicomTools.getTag(imp, "0010,0040");
		String patWeight = DicomTools.getTag(imp, "0010,1030");
		setString(dcm, Tag.Patient​Name, patName);
		setString(dcm, Tag.Patient​ID, patID);
		setDate(dcm, Tag.Patient​Birth​Date, patBoD);
		setTime(dcm, Tag.Patient​Birth​Time, patBoT);
		setString(dcm, Tag.Patient​Sex, patSex);
		setDouble(dcm, Tag.Patient​Weight, patWeight);
		
		/*
		 * UIDs
		 */
		//study uid
		String studyUID = DicomTools.getTag(imp, "0020,000D");
		if(studyUID == null || studyUID.trim().length()==0) {
			studyUID = UIDUtils.createUID();
		}
		//series uid
		String seriesUID = DicomTools.getTag(imp, "0020,000E");
		if(seriesUID == null || seriesUID.trim().length()==0) {
			seriesUID = UIDUtils.createUID();
		}
		//reference uid(Tag.Frame​Of​Reference​UID)
		String refUID = DicomTools.getTag(imp, "0020,0052");
		setString(dcm, Tag.Study​Instance​UID, studyUID);
		setString(dcm, Tag.Series​Instance​UID, seriesUID);
		setString(dcm, Tag.Frame​Of​Reference​UID, refUID);
		
		/*
		 * 0008,0060  Modality: e.g., MR
		 * 0008, 0061 Modalities​In​Study
		 * 0008,0070  Manufacturer: Visionary Imaging Services,Inc.
		 * 0018,1000  Device Serial Number: 40115
		 * 0008,1010  Station Name: GRAPHY
		 * 0018,1020  Software Versions(s): V7.0B
		 * 0008,1030  Study Descreption 
		 * 0008,103E  Series Description: Scano 3plane_SAG
		 * 0008,1090  Manufacturer's Model Name: GRAPHY
		 */
		String modality = DicomTools.getTag(imp, "0008,0060");
		String modalities = DicomTools.getTag(imp, "0008,0061");
		String manu = DicomTools.getTag(imp, "0008,0070");
		String deviceNo = DicomTools.getTag(imp, "0018,1000");
		String station = DicomTools.getTag(imp, "0008,1010");
		String softVer = DicomTools.getTag(imp, "0018,1020");
		String studyDesc = DicomTools.getTag(imp, "0008,1030");
		String seriesDesc = DicomTools.getTag(imp, "0008,103E");
		String modelName = DicomTools.getTag(imp, "0008,1090");
		setString(dcm, Tag.Modality, modality);
		setString(dcm, Tag.Modalities​In​Study, modalities);
		setString(dcm, Tag.Manufacturer, manu);
		setString(dcm, Tag.Device​Serial​Number, deviceNo);//long string
		setString(dcm, Tag.Station​Name, station);
		setString(dcm, Tag.Software​Versions, softVer);//long string
		setString(dcm, Tag.Study​Description, studyDesc);
		setString(dcm, Tag.Series​Description, seriesDesc);
		setString(dcm, Tag.Manufacturer​Model​Name, modelName);
		
        /*
         * 0008, 0008 ImageType
         * 0008,0012  Instance Creation Date: 20221022
		 * 0008,0013  Instance Creation Time: 121105.117
        * 0008,0020  Study Date: 20221022
        * 0008,0021  Series Date: 20221022
        * 0008,0022  Acquisition Date: 20221022
        * 0008,0023  Content Date: 20221022
        * 0008,0030  Study Time: 121008.0
        * 0008,0031  Series Time: 121054.967
        * 0008,0032  Acquisition Time: 121054.967
        * 0008,0033  Content Time: 121105.0
        * */
		String imageType = DicomTools.getTag(imp, "0008,0008");
		String instanceCreationDate = DicomTools.getTag(imp, "0008,0012");
		String instanceCreationTime = DicomTools.getTag(imp, "0008,0013");
		String studyDate = DicomTools.getTag(imp, "0008,0020");
		String seriesDate = DicomTools.getTag(imp, "0008,0021");
		String acquiDate = DicomTools.getTag(imp, "0008,0022");
		String contDate = DicomTools.getTag(imp, "0008,0023");
		String studyTime = DicomTools.getTag(imp, "0008,0030");
		String seriesTime = DicomTools.getTag(imp, "0008,0031");
		String acquiTime = DicomTools.getTag(imp, "0008,0032");
		String contTime = DicomTools.getTag(imp, "0008,0033");
		setString(dcm, Tag.Image​Type, imageType);
		setDate(dcm, Tag.Instance​Creation​Date, instanceCreationDate);
		setTime(dcm, Tag.Instance​Creation​Time, instanceCreationTime);
		setDate(dcm, Tag.Study​Date, studyDate);
		setDate(dcm, Tag.Series​Date, seriesDate);
		setDate(dcm, Tag.Acquisition​Date, acquiDate);
		setDate(dcm, Tag.Content​Date, contDate);
		setTime(dcm, Tag.Study​Time, studyTime);
		setTime(dcm, Tag.Series​Time, seriesTime);
		setTime(dcm, Tag.Acquisition​Time, acquiTime);
		setTime(dcm, Tag.Content​Time, contTime);
		
		/*
		 * 0020,0010  Study ID: 20221001-123
		 * 0020,0011  Series Number: 2 
		 * 0020,0012  Acquisition Number: 0 
		 * 0020,0013  InstanceNumber: 3
		 * 0020,0032 image position patient
		 * 0020,0037 image orientation patient
		 */
		String studyID = DicomTools.getTag(imp, "0020,0010");
		String seriesNo = DicomTools.getTag(imp, "0020,0011");
		String acquiNo = DicomTools.getTag(imp, "0020,0012");
		String instNo = DicomTools.getTag(imp, "0020,0013");
		String imgPosPat = DicomTools.getTag(imp, "0020,0032");
		String imgOriPat = DicomTools.getTag(imp, "0020,0037");
		setString(dcm, Tag.Study​ID, studyID);
		setInt(dcm, Tag.Series​Number, seriesNo);
		setInt(dcm, Tag.Acquisition​Number, acquiNo);
		setInt(dcm, Tag.Instance​Number, instNo);
		setDoubles(dcm, Tag.Image​Position​Patient, imgPosPat);
		setDoubles(dcm, Tag.Image​Orientation​Patient, imgOriPat);
		
//		String samplesPerPixel = DicomTools.getTag(imp, "0028,0002");
		int samplesPerPixel = imp.getNChannels();/*if imp is non dcm, it dose not has attributes*/
		String planarConfigurationString = DicomTools.getTag(imp, "0028,0006");
		int rows = imp.getHeight();//DicomTools.getTag(imp, "0028,0010");
		int cols = imp.getWidth();//DicomTools.getTag(imp, "0028,0011");
		String pixelSpacingYX = DicomTools.getTag(imp, "0028,0030");
		Double pixelSpacingZ = DicomTools.getVoxelDepth(imp.getStack());//SpacingBetweenSlices
		if(pixelSpacingZ == -1) {
			pixelSpacingZ = null;
		}
		int bitsAllocated = imp.getBitDepth();//DicomTools.getTag(imp, "0028,0100");
		String bitsStored = DicomTools.getTag(imp, "0028,0101");
		String highBit = DicomTools.getTag(imp, "0028,0102");
		String pixelRepresentationString = DicomTools.getTag(imp, "0028,0103");
		String intercept = DicomTools.getTag(imp, "0028,1052");
		String slope = DicomTools.getTag(imp, "0028,1053");
		setInt(dcm, Tag.Samples​Per​Pixel, samplesPerPixel);
		setInt(dcm, Tag.Planar​Configuration, planarConfigurationString);//banded or not
		setInt(dcm, Tag.Rows, rows);
		setInt(dcm, Tag.Columns, cols);
		setDoubles(dcm, Tag.Pixel​Spacing, pixelSpacingYX);
		setDouble(dcm, Tag.Spacing​Between​Slices, pixelSpacingZ);
		setInt(dcm, Tag.Bits​Allocated, bitsAllocated);
		setInt(dcm, Tag.Bits​Stored, bitsStored);
		setInt(dcm, Tag.High​Bit, highBit);
		setInt(dcm, Tag.Pixel​Representation, pixelRepresentationString);//signed 1
		setDouble(dcm, Tag.Rescale​Intercept, intercept);
		setDouble(dcm, Tag.Rescale​Slope, slope);
	}
	
	static private void setString(DicomObject dcm, int tag, String v) {
		if(v == null || v.trim().length()==0) {
			return;
		}
		v = v.trim();
		dcm.setString(tag, TagDict.vrType(tag)[0], v);
	}
	
	static private void setInt(DicomObject dcm, int tag, int v) {
		dcm.setInt(tag, TagDict.vrType(tag)[0], v);
	}
	
	static private void setInt(DicomObject dcm, int tag, String v) {
		if(v == null || v.trim().length()==0) {
			return;
		}
		v = v.trim();
		try {
			int intV = Integer.parseInt(v);
			setInt(dcm, tag, intV);
		}catch(NumberFormatException e) {
			//do nothing
		}
	}
	
	static private void setDouble(DicomObject dcm, int tag, Double v) {
		if(v == null) {
			return;
		}
		dcm.setDouble(tag, TagDict.vrType(tag)[0], v);
	}
	
	static private void setDouble(DicomObject dcm, int tag, String v) {
		if(v == null || v.trim().length()==0) {
			return;
		}
		v = v.trim();
		try {
			double dV = Double.parseDouble(v);
			dcm.setDouble(tag, TagDict.vrType(tag)[0], dV);
		}catch(NumberFormatException e) {
			//do nothing
		}
	}
	
	static private void setDoubles(DicomObject dcm, int tag, String vals) {
		if(vals == null || vals.trim().length()==0) {
			return;
		}
		vals = vals.trim();
		try {
			String[] vals_ = vals.split("\\\\");
			double[] array = new double[vals_.length];
			for(int j=0; j<array.length; j++) {
				array[j] = Double.parseDouble(vals_[j].trim());
			}
			dcm.setDouble(tag, TagDict.vrType(tag)[0], array);
		}catch(NumberFormatException e) {
			//do nothing
		}
	}
	
	static private void setDate(DicomObject dcm, int tag, String v) {
		if(v == null || v.trim().length()==0) {
			return;
		}
		v = v.trim();
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd");
		try {
			dcm.setDate(tag, TagDict.vrType(tag)[0], sdfDate.parse(v));
		} catch (ParseException e) {
			//do nothing
		}
	}
	
	static private void setTime(DicomObject dcm, int tag, String v) {
		if(v == null || v.trim().length()==0) {
			return;
		}
		v = v.trim();
		SimpleDateFormat sdfTime = new SimpleDateFormat("HHmmSS.sss");
		try {
			dcm.setDate(tag, TagDict.vrType(tag)[0], sdfTime.parse(v));
		} catch (ParseException e) {
			//do nothing
		}
	}
}
