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
import java.util.logging.Level;

import org.joml.Vector3d;

import com.vis.core.log.Log;
import com.vis.core.util.ByteUtils;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.TagUtils;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;

import ij.ImagePlus;
import ij.ImageStack;
import ij.VirtualStack;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import ij.util.Tools;

public class GDicomTools extends ij.util.DicomTools{
	
	public static String getTag(ImagePlus imp, String id) {
		String v = ij.util.DicomTools.getTag(imp, id);
		if(v != null) {
			v = v.trim();
		}
		return v;
	}
	
	public static String getTag(ImagePlus imp, int pos/*1 to N*/, String tag/*gggg,eeee*/) {
		imp.setSlice(pos);
		return getTag(imp, tag);
	}
	
	public static Double getDouble(ImagePlus imp, int pos/*1 to N*/, String tag) {
		imp.setSlice(pos);
		String value = getTag(imp, pos, tag);
		if (value==null) return Double.NaN;
		int index3 = value.indexOf("\\");
		if (index3>0)
			value = value.substring(0, index3);
		return Tools.parseDouble(value);
	}
	
	public static double[] getDoubles(ImagePlus imp, int pos/*1 to N*/, String tag) {
		String res = getTag(imp, pos, tag);
		if (res == null) return null;
		String[] xyz = res.split("\\\\");
		if(xyz == null || xyz.length < 1) {
			return null;
		}
		double[] arr = new double[xyz.length];
		for(int i=0;i<xyz.length;i++) {
			arr[i] = Tools.parseDouble(xyz[i]);//can keep minus in case of -0.0.
		}
		return arr;
	}
	
	public static double[] getDoubles(ImagePlus imp, String tag) {
		return getDoubles(imp, imp.getCurrentSlice(),tag);
	}
	
	public static void setTag(ImagePlus imp, int pos/*1 to N*/, String tag, String value) {
		if(imp.getNSlices() > 1) {
			ImageStack stack = imp.getStack();
			String hdr = stack.getSliceLabel(pos);
			if(hdr == null) {
				hdr = "";
			}
			int index1 = hdr.indexOf(tag);
			if (index1 != -1) {// found
				if (hdr.charAt(index1 + 11) == '>') {
					// ignore tags in sequences
					index1 = hdr.indexOf(tag, index1 + 10);
				}
				index1 = hdr.indexOf(":", index1) + 1;
				String upper = hdr.substring(0, index1);
				int index2 = hdr.indexOf("\n", index1);
				String after = hdr.substring(index2);
				hdr = upper + value + after;
			} else {// not found
				if (!hdr.endsWith("\n")) {
					hdr = hdr + "\n" + tag + ": " + value + "\n";
				} else {
					hdr = hdr + tag + ": " + value + "\n";
				}
			}
			stack.setSliceLabel(hdr, pos);
		}else {
			String hdr = (String)imp.getProperty("Info");
			if (hdr == null) {
				hdr = "";
			}
			int index1 = hdr.indexOf(tag);
			if (index1 != -1 && hdr.endsWith(">")) {// sequence found
				if (hdr.charAt(index1 + 11) == '>') {
					// ignore tags in sequences
					index1 = hdr.indexOf(tag, index1 + 10);
				}
				index1 = hdr.indexOf(":", index1) + 1;
				String upper = hdr.substring(0, index1);
				int index2 = hdr.indexOf("\n", index1);
				String after = hdr.substring(index2);
				hdr = upper + value + after;
			} else {// not found
				if(hdr.equals("")) {
					hdr = hdr + tag + ": " + value + "\n";
				}else if (!hdr.endsWith("\n")) {
					hdr = hdr + "\n" + tag + ": " + value + "\n";
				} else {
					hdr = hdr + tag + ": " + value + "\n";
				}
			}
			imp.setProperty("Info", hdr);
		}
	}
	
	public static void setDoubles(ImagePlus imp, int pos, String tag, double[] values) {
		String arr = "";
		for(double v : values) {
			arr += String.valueOf(v) + "\\";
		}
		//delete end of "\\"
		arr = arr.substring(0, arr.lastIndexOf('\\'));
		setTag(imp, pos, tag, arr);
	}
	
	public static double getVoxelDepth(DicomObject header) {
		double spacingBetweenSlices = header.getDouble(Tag.Spacing​Between​Slices, Double.NaN);
		double sliceThickness = header.getDouble(Tag.Slice​Thickness, Double.NaN);
		if (!Double.isNaN(spacingBetweenSlices)) {
			return spacingBetweenSlices;//prior
		}
		if (!Double.isNaN(sliceThickness)){
			return sliceThickness;
		}
		return 1d;
	}
	
	public static double getVoxelDepth(ImagePlus imp) {
		double z = imp.getCalibration().pixelDepth;
		double spacingBetweenSlices = getDouble(imp, 1, "0018,0088");
		double sliceThickness = getDouble(imp, 1, "0018,0050");
		if (!Double.isNaN(spacingBetweenSlices)) {
			return spacingBetweenSlices;//prior
		}
		if (!Double.isNaN(sliceThickness)){
			return sliceThickness;
		}
		return z;
	}
	
	/**
	 * 
	 * @param stack
	 * @param n : from 1 to n
	 * @return
	 */
	public static String getHeader(ImageStack stack, int n) {
		String hdr = stack.getSliceLabel(n);
		if ((hdr == null || hdr.length() < 100) && stack.isVirtual()) {
			String dir = ((VirtualStack) stack).getDirectory();
			String name = ((VirtualStack) stack).getFileName(n);
			ImagePlus reader = new ImagePlus(dir + name);
			hdr = reader.getInfoProperty();
			if (hdr != null)
				hdr = name + "\n" + hdr;
		}
		return hdr;
	}
	
	public static void headerCopy(ImagePlus from, ImagePlus to) {
		if(from.getNSlices() != to.getNSlices()) {
			Log.logger.info("Can not copy header, not matching stack sizes.");
			return;
		}
		if(from.getNSlices() == 1) {
			to.setProperty("Info", from.getInfoProperty());
		}else {
			int size = from.getNSlices();
			for(int i=1;i<=size;i++) {
				String hdr = from.getStack().getSliceLabel(i);
				to.getStack().setSliceLabel(hdr, i);
			}
		}
	}
	
	public static ImagePlus dcmImgToImagePlus(DicomImage dcmImg) {
		if(!dcmImg.isMultiFrame()) {
			ImagePlus imp = new ImagePlus("",dcmImg.getImageProcessor(0));
			DicomObject header = dcmImg.getCore();
			int[] tags = header.tags();
			for(int t : tags) {
				if(t == Tag.Pixel​Data || t == Tag.Float​​Pixel​​Data || t == Tag.Double​Float​Pixel​​Data) {
					continue;
				}
				String ts = TagUtils.toDicomToolsString(t);
				String vmString = TagDict.vmOf(t);
				if(vmString == null) { // maybe private tag
					continue;
				}
				if(vmString.equals("1")) {
					setTag(imp,1,ts,header.getString(t));
				}else {
					String[] vals = header.getStrings(t);
					String v = "";
					for(String val:vals) {
						v = v + val+"\\";
					}
					v = v.substring(0, v.length()-1);//remove last "\\"
					setTag(imp,1,ts,v);
				}
			}
			calibrate(imp, header);
			return imp;
		}else {
			int size = dcmImg.getNumOfFrames();
			ImageStack stack = new ImageStack();
			Calibration cal = null;
			for(int i=0; i<size; i++) {
				/*single frame imp*/
				ImagePlus imp = new ImagePlus("",dcmImg.getImageProcessor(i));
				DicomObject header = dcmImg.getCore();
				int[] tags = header.tags();
				for(int t : tags) {
					if(t == Tag.Pixel​Data || t == Tag.Float​​Pixel​​Data || t == Tag.Double​Float​Pixel​​Data) {
						continue;
					}
					String ts = TagUtils.toDicomToolsString(t);
					if(TagDict.vmOf(t).equals("1")) {
						setTag(imp,1,ts,header.getString(t));
					}else {
						String[] vals = header.getStrings(t);
						String v = "";
						for(String val:vals) {
							v = v + val+"\\";
						}
						v = v.substring(0, v.length()-1);//remove last "\\"
						setTag(imp,1,ts,v);
					}
				}
				setTag(imp,(i+1),TagUtils.toDicomToolsString(Tag.Instance​Number),String.valueOf(i+1));
				calibrate(imp, header);
				if(cal == null) {
					cal = imp.getCalibration();
				}
				stack.addSlice(imp.getProcessor());
				stack.setSliceLabel(imp.getInfoProperty(), i+1);
			}
			ImagePlus newImp = new ImagePlus("",stack);
			newImp.setCalibration(cal);
			return newImp;
		}
	}
	
	public static HashMap<Integer, DicomImage> imagePlusToDcm(ImagePlus imp, boolean dealWithSecondaryCapture) {
		if (imp == null) {
			return null;
		}
		HashMap<Integer, DicomImage> images = new HashMap<>();
		int w = imp.getWidth();
		int h = imp.getHeight();
		/*
		 * imp.getNChannels() may return 1 even if RGB images. reproduce code String url
		 * = "https://imagej.net/ij/images/flybrain.zip"; ImagePlus image =
		 * IJ.openImage(url); sysout(image.getNChannels());//return 1
		 */
		int samples = imp.getProcessor() instanceof ColorProcessor ? 3 : 1;
		int bits = imp.getBitDepth();
		int s = imp.getNSlices();
		boolean signed16 = imp.getProcessor().isSigned16Bit();
		for (int i = 0; i < s; i++) {
			DicomObject core = DicomObject.newDicomObject();
			addAttributes(core, i, imp, dealWithSecondaryCapture);
			DicomImage dcmImg = DicomImage.newDicomImage(core, UID.ImplicitVRLittleEndian);
			Object pix = imp.getProcessor().getPixels();// setPosition() was done addAttributes()
			if (signed16) {
				/*
				 * When loading a pixel array from ImagePlus, Signed16Bit images are already
				 * Unsigned16Bit, so they are converted back to their original format(Signed16Bit).
				 */
				short[] pixels = (short[]) pix;
				for (int k = 0; k < pixels.length; k++) {
					pixels[k] = (short) (pixels[k] - (short) 32768);
				}
				pix = pixels;
			}
			dcmImg.setPixelData(0, w, h, samples, bits, pix);
			images.put(i, dcmImg);
		}
		return images;
	}

	/**
	 * Add attributes from ImagePlus to DicomObject 
	 * @param dcm
	 * @param slicePos
	 * @param imp
	 * @param dealWithSecondaryCapture
	 */
	private static void addAttributes(DicomObject dcm, int slicePos/* 0 to N-1 */,
			ImagePlus imp/* should be set current processor */, boolean dealWithSecondaryCapture) {

		imp.setPosition(slicePos + 1);

		String sopClassUID = GDicomTools.getTag(imp, "0008,0016");
		if(sopClassUID != null) sopClassUID = sopClassUID.trim();
		if (dealWithSecondaryCapture) {
			sopClassUID = UID.SecondaryCaptureImageStorage.uid();
		}
		
		String sopInstUID = GDicomTools.getTag(imp, "0008,0018");
		if(sopInstUID != null) sopInstUID = sopInstUID.trim();
		if (sopInstUID == null || sopInstUID.length() == 0) {
			sopInstUID = UIDUtils.createUID();
		}
		setString(dcm, Tag.SOP​Class​UID, sopClassUID);
		setString(dcm, Tag.SOP​Instance​UID, sopInstUID);

		/*
		 * 0010,0010 Patient's Name: TEST^TARO 0010,0020 Patient ID: 0000012345
		 * 0010,0030 Patient's Birth Date: 19840405 0010,0032 Patient's Birth Time:
		 * 000000 0010,0040 Patient's Sex: M 0010,1030 Patient's Weight: 65
		 */
		String patID = GDicomTools.getTag(imp, "0010,0020");
		String patName = GDicomTools.getTag(imp, "0010,0010");
		String patBoD = GDicomTools.getTag(imp, "0010,0030");
		String patBoT = GDicomTools.getTag(imp, "0010,0032");
		String patSex = GDicomTools.getTag(imp, "0010,0040");
		String patWeight = GDicomTools.getTag(imp, "0010,1030");
		setString(dcm, Tag.Patient​Name, patName);
		setString(dcm, Tag.Patient​ID, patID);
		setDate(dcm, Tag.Patient​Birth​Date, patBoD);
		setTime(dcm, Tag.Patient​Birth​Time, patBoT);
		setString(dcm, Tag.Patient​Sex, patSex);
		setDouble(dcm, Tag.Patient​Weight, patWeight);

		/*
		 * UIDs
		 */
		// study uid
		String studyUID = GDicomTools.getTag(imp, "0020,000D");
		if (studyUID == null || studyUID.trim().length() == 0) {
			studyUID = UIDUtils.createUID();
		}
		// series uid
		String seriesUID = GDicomTools.getTag(imp, "0020,000E");
		if (seriesUID == null || seriesUID.trim().length() == 0) {
			seriesUID = UIDUtils.createUID();
		}
		// reference uid(Tag.Frame​Of​Reference​UID)
		String refUID = GDicomTools.getTag(imp, "0020,0052");
		setString(dcm, Tag.Study​Instance​UID, studyUID);
		setString(dcm, Tag.Series​Instance​UID, seriesUID);
		setString(dcm, Tag.Frame​Of​Reference​UID, refUID);

		/*
		 * 0008,0060 Modality: e.g., MR 0008, 0061 Modalities​In​Study 0008,0070
		 * Manufacturer: Visionary Imaging Services,Inc. 0018,1000 Device Serial Number:
		 * 40115 0008,1010 Station Name: GRAPHY 0018,1020 Software Versions(s): V7.0B
		 * 0008,1030 Study Descreption 0008,103E Series Description: Scano 3plane_SAG
		 * 0008,1090 Manufacturer's Model Name: GRAPHY
		 */
		String modality = GDicomTools.getTag(imp, "0008,0060");
		String modalities = GDicomTools.getTag(imp, "0008,0061");
		String manu = GDicomTools.getTag(imp, "0008,0070");
		String deviceNo = GDicomTools.getTag(imp, "0018,1000");
		String station = GDicomTools.getTag(imp, "0008,1010");
		String softVer = GDicomTools.getTag(imp, "0018,1020");
		String studyDesc = GDicomTools.getTag(imp, "0008,1030");
		String seriesDesc = GDicomTools.getTag(imp, "0008,103E");
		String modelName = GDicomTools.getTag(imp, "0008,1090");
		setString(dcm, Tag.Modality, modality);
		setString(dcm, Tag.Modalities​In​Study, modalities);
		setString(dcm, Tag.Manufacturer, manu);
		setString(dcm, Tag.Device​Serial​Number, deviceNo);// long string
		setString(dcm, Tag.Station​Name, station);
		setString(dcm, Tag.Software​Versions, softVer);// long string
		setString(dcm, Tag.Study​Description, studyDesc);
		setString(dcm, Tag.Series​Description, seriesDesc);
		setString(dcm, Tag.Manufacturer​Model​Name, modelName);

		/*
		 * 0008, 0008 ImageType 0008,0012 Instance Creation Date: 20221022 0008,0013
		 * Instance Creation Time: 121105.117 0008,0020 Study Date: 20221022 0008,0021
		 * Series Date: 20221022 0008,0022 Acquisition Date: 20221022 0008,0023 Content
		 * Date: 20221022 0008,0030 Study Time: 121008.0 0008,0031 Series Time:
		 * 121054.967 0008,0032 Acquisition Time: 121054.967 0008,0033 Content Time:
		 * 121105.0
		 */
		String imageType = GDicomTools.getTag(imp, "0008,0008");
		String instanceCreationDate = GDicomTools.getTag(imp, "0008,0012");
		String instanceCreationTime = GDicomTools.getTag(imp, "0008,0013");
		String studyDate = GDicomTools.getTag(imp, "0008,0020");
		String seriesDate = GDicomTools.getTag(imp, "0008,0021");
		String acquiDate = GDicomTools.getTag(imp, "0008,0022");
		String contDate = GDicomTools.getTag(imp, "0008,0023");
		String studyTime = GDicomTools.getTag(imp, "0008,0030");
		String seriesTime = GDicomTools.getTag(imp, "0008,0031");
		String acquiTime = GDicomTools.getTag(imp, "0008,0032");
		String contTime = GDicomTools.getTag(imp, "0008,0033");
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
		 * 0020,0010 Study ID: 20221001-123 0020,0011 Series Number: 2 0020,0012
		 * Acquisition Number: 0 0020,0013 InstanceNumber: 3 0020,0032 image position
		 * patient 0020,0037 image orientation patient
		 */
		String studyID = GDicomTools.getTag(imp, "0020,0010");
		String seriesNo = GDicomTools.getTag(imp, "0020,0011");
		String acquiNo = GDicomTools.getTag(imp, "0020,0012");
		String instNo = GDicomTools.getTag(imp, "0020,0013");
		String imgPosPat = GDicomTools.getTag(imp, "0020,0032");
		String imgOriPat = GDicomTools.getTag(imp, "0020,0037");
		setString(dcm, Tag.Study​ID, studyID);
		setInt(dcm, Tag.Series​Number, seriesNo);
		setInt(dcm, Tag.Acquisition​Number, acquiNo);
		setInt(dcm, Tag.Instance​Number, instNo);
		setDoubles(dcm, Tag.Image​Position​Patient, imgPosPat);
		setDoubles(dcm, Tag.Image​Orientation​Patient, imgOriPat);

//		String samplesPerPixel = GDicomTools.getTag(imp, "0028,0002");
		int samplesPerPixel = imp.getProcessor() instanceof ColorProcessor ? 3 : 1; // DO NOT USE imp.getNChannels()
		String planarConfigurationString = GDicomTools.getTag(imp, "0028,0006");
		int rows = imp.getHeight();// GDicomTools.getTag(imp, "0028,0010");
		int cols = imp.getWidth();// GDicomTools.getTag(imp, "0028,0011");
		String pixelSpacingYX = GDicomTools.getTag(imp, "0028,0030");
		if (pixelSpacingYX == null) {
			Calibration cal = imp.getCalibration();
			pixelSpacingYX = cal.pixelHeight + "\\\\" + cal.pixelWidth;
		}
		Double pixelSpacingZ = GDicomTools.getVoxelDepth(imp.getStack());// SpacingBetweenSlices
		if (pixelSpacingZ <= 0.0) {
			pixelSpacingZ = imp.getCalibration().pixelDepth;
		}
		int bitsAllocated = imp.getBitDepth();// GDicomTools.getTag(imp, "0028,0100");
		String bitsStored = GDicomTools.getTag(imp, "0028,0101");
		String highBit = GDicomTools.getTag(imp, "0028,0102");
		String pixelRepresentationString = GDicomTools.getTag(imp, "0028,0103");
		if (pixelRepresentationString == null) {
			pixelRepresentationString = imp.getProcessor().isSigned16Bit() ? "1" : "0";
		}
		String intercept = GDicomTools.getTag(imp, "0028,1052");
		String slope = GDicomTools.getTag(imp, "0028,1053");
		setInt(dcm, Tag.Samples​Per​Pixel, samplesPerPixel);
		setInt(dcm, Tag.Planar​Configuration, planarConfigurationString);// banded or not
		setInt(dcm, Tag.Rows, rows);
		setInt(dcm, Tag.Columns, cols);
		setDoubles(dcm, Tag.Pixel​Spacing, pixelSpacingYX);
		setDouble(dcm, Tag.Spacing​Between​Slices, pixelSpacingZ);
		setInt(dcm, Tag.Bits​Allocated, bitsAllocated);
		setInt(dcm, Tag.Bits​Stored, bitsStored);
		setInt(dcm, Tag.High​Bit, highBit);
		setInt(dcm, Tag.Pixel​Representation, pixelRepresentationString);// signed 1
		setDouble(dcm, Tag.Rescale​Intercept, intercept);
		setDouble(dcm, Tag.Rescale​Slope, slope);
	}

	static public void setString(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		dcm.setString(tag, TagDict.vrType(tag)[0], v);
	}

	static public void setInt(DicomObject dcm, int tag, int v) {
		dcm.setInt(tag, TagDict.vrType(tag)[0], v);
	}

	static public void setInt(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		try {
			int intV = Integer.parseInt(v);
			setInt(dcm, tag, intV);
		} catch (NumberFormatException e) {
			// do nothing
		}
	}

	static public void setDouble(DicomObject dcm, int tag, Double v) {
		if (v == null) {
			return;
		}
		dcm.setDouble(tag, TagDict.vrType(tag)[0], v);
	}

	static public void setDouble(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		try {
			double dV = Double.parseDouble(v);
			dcm.setDouble(tag, TagDict.vrType(tag)[0], dV);
		} catch (NumberFormatException e) {
			// do nothing
		}
	}

	static public void setDoubles(DicomObject dcm, int tag, String vals) {
		if (vals == null || vals.trim().length() == 0) {
			return;
		}
		vals = vals.trim();
		try {
			String[] vals_ = vals.split("\\\\+");
			double[] array = new double[vals_.length];
			for (int j = 0; j < array.length; j++) {
				array[j] = Double.parseDouble(vals_[j].trim());
			}
			dcm.setDouble(tag, TagDict.vrType(tag)[0], array);
		} catch (NumberFormatException e) {
			// do nothing
			Log.logger.fine("setDouble was failed");
		}
	}

	static public void setDate(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd");
		try {
			dcm.setDate(tag, TagDict.vrType(tag)[0], sdfDate.parse(v));
		} catch (ParseException e) {
			// do nothing
			Log.logger.fine("setDate was failed");
		}
	}

	static public void setTime(DicomObject dcm, int tag, String v) {
		if (v == null || v.trim().length() == 0) {
			return;
		}
		v = v.trim();
		dcm.setString(tag, TagDict.vrType(tag)[0], v);
	}
	
	/**
	 * Calibrate imageplus using header.
	 * @param imp
	 * @param header
	 */
	public static void calibrate(ImagePlus imp/*No calibrated imageplus*/, DicomObject header) {
		if(imp == null) {
			throw new NullPointerException();
		}
		if(header == null) {
			return;
		}
		/*No calibrated imageplus*/
		Calibration originalCal = imp.getCalibration();
		boolean isRGB = imp.getType() == ImagePlus.COLOR_RGB;
		if(isRGB) {
			imp.getProcessor().snapshot();
		}
		/*
		 * Spatial calibrations
		 */
		// x-y-z
		double pixelSpacingX = 1.0;
		double pixelSpacingY = 1.0;
		double pixelSpacingZ = 1.0;
		// Pixel Spacing = Row Spacing [PY] \ Column Spacing [PX] = 0.30\0.25.
		double[] pixelSpacing = header.getDoubles(Tag.Pixel​Spacing);
		double spacingBetweenSlices = header.getDouble(Tag.Spacing​Between​Slices, -1);
		if (pixelSpacing != null && pixelSpacing != ByteUtils.EMPTY_DOUBLES) {
			pixelSpacingX = pixelSpacing[1];// column
			pixelSpacingY = pixelSpacing[0];// row
			if (spacingBetweenSlices != -1) {
				pixelSpacingZ = spacingBetweenSlices;
			} else {
				double sliceThickness = header.getDouble(Tag.Slice​Thickness, -1);
				if (sliceThickness != -1) {
					pixelSpacingZ = sliceThickness;
				}
			}
			/*
			 * Units is mm, that is dicom default. see, Pixel Spacing Attribute (0028,0030)
			 * definition.
			 */
			originalCal.setUnit("mm");//
		}
		// then, set to cal
		originalCal.pixelWidth = pixelSpacingX;
		originalCal.pixelHeight = pixelSpacingY;
		originalCal.pixelDepth = pixelSpacingZ;
		
		/*
		 * density calibration
		 */
		Double slope = header.getDouble(Tag.Rescale​Slope, Double.NaN);
		Double intercept = header.getDouble(Tag.Rescale​Intercept, Double.NaN);
		String modality = header.getString(Tag.Modality);
		boolean isSigned = header.getInt(Tag.Pixel​Representation, 0) != 0;
		if (header.getInt(Tag.Bits​Allocated, -1) == 16 && isSigned) {
			if (!intercept.isNaN() && !slope.isNaN()) {
				double[] coeff = new double[2];
				coeff[0] = slope*(-32768) + intercept;
				coeff[1] = slope;
				originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
				originalCal.getCTable();//to make cTable.
//				originalCal.setSigned16BitCalibration();//DO NOT USE
				if(modality != null && modality.equals("CT")) {
					originalCal.setValueUnit("HU");
				}
				//add another modalities unit...
			}
		}else if (intercept!=0.0 && slope==1.0) {
			double[] coeff = new double[2];
			coeff[0] = intercept;
			coeff[1] = slope;
			originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
			originalCal.getCTable();//to make cTable.
		}
		// adjust WW/WL
		int WL = header.getInt(Tag.Window​Center, Integer.MIN_VALUE);
		int WW = header.getInt(Tag.Window​Width, Integer.MIN_VALUE);	
		if (WL == Integer.MIN_VALUE || WW == Integer.MIN_VALUE) {
			// do nothing
		}else {
			double newMin = WL - (.5 * WW);
			double newMax = WL + (.5 * WW);
			if (newMin > newMax) {
				Log.logger.log(Level.WARNING,"SlideGlass::changeWindow() problem occured :" + newMin + " " + newMax);
			}else {
				imp.setDisplayRange(newMin, newMax);
			}
		}
		imp.setCalibration(originalCal);
	}
	
	
	/**
	 * 
	 * @param dcms : if it has multi slices, set image position before perform.
	 * @param ipp
	 */
	public static void setImagePositionPatient(ImagePlus dcms, int pos, Vector3d ipp){
		if(ipp == null) {
			Log.logger.info("ImagePositionPatient must have 3 component x,y,z...");
			return;
		}
		setImagePositionPatient(dcms, pos, new double[] {ipp.x(),ipp.y(),ipp.z()});
	}
	
	/**
	 * 
	 * @param dcms : if it has multi slices, set image position before perform.
	 * @param ipp
	 */
	public static void setImagePositionPatient(ImagePlus dcms, int pos, double[] ipp){
		if(ipp == null || ipp.length != 3) {
			Log.logger.info("ImagePositionPatient must have 3 component x,y,z...");
			return;
		}
		setDoubles(dcms, pos, "0020,0032", ipp);
	}
	
	/**
	 * 
	 * @param dcms : if it has multi slices, set image position before perform.
	 * @param ipp
	 */
	public static void setImageOrientationPatient(ImagePlus dcms, int pos, double[] iop){
		if(iop == null || iop.length != 6) {
			Log.logger.info("ImageOrientationPatient must have 6 component y axis(Row) : xyz, x axis(Col) : xyz");
			return;
		}
		setDoubles(dcms, pos, "0020,0037", iop);
	}
	
	public static void setImageOrientationPatient(ImagePlus dcms, int pos, Vector3d row, Vector3d col){
		setImageOrientationPatient(dcms, pos, new double[] {row.x, row.y, row.z,col.x, col.y, col.z});
	}
	
	public static double[] getImagePositionPatient(ImagePlus imp, int pos/*1 to N*/) {
		double[] ipp = getDoubles(imp, pos, "0020,0032");
		return ipp;
	}
	
	public static double[] getImageOrientationPatient(ImagePlus imp, int pos/*1 to N*/) {
		double[] iop = getDoubles(imp, pos, "0020,0037");
		return iop;
	}
}
