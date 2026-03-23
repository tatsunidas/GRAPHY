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
package com.vis.core.view.D2.processing;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.vis.core.view.D2.roi.*;
import com.vis.core.view.D2.ui.glasses.*;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.ImageCalculator;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * masking using roiObj
 * 
 * @author tatsunidas
 *
 */
public class Masking {
	
	/**
	 * Create mask from single roi.
	 * @param roi
	 * @return mask (in the roi is 255)
	 */
	public static ImagePlus run(RoiObj roi) {
		return run(roi, null);
	}
	
	/**
	 * Create mask from single roi.
	 * @param roi
	 * @param maskValue, null-able
	 * @return mask with in the roi mask value will be maskValue.
	 */
	public static ImagePlus run(RoiObj roi, Byte maskValue) {
		if (roi == null) {
			throw new IllegalArgumentException("Roi is null...");
		}
		if (roi.getSlideGlass() == null) {
			throw new IllegalArgumentException("This roi does not have SlideGlass. return.");
		}
		return run(roi, roi.getSlideGlass(), maskValue);
	}
	
	/**
	 * Create mask from single roi.
	 * If roi is null return blank image.
	 * 
	 * @param roi null-able
	 * @param sg
	 * @param maskValue
	 * @return mask
	 */
	public static ImagePlus run(RoiObj roi, SlideGlass sg, Byte maskValue) {
		if (sg == null) {
			throw new IllegalArgumentException("This SlideGlass is null. return.");
		}
		Calibration cal = sg.getOriginalImage().getCalibration();
		ImageProcessor mask = null;
		if (roi == null) {
			mask = new ByteProcessor(sg.getWidth(), sg.getHeight());
		}else {
			mask = getMask(roi, sg, maskValue);
		}
		ImagePlus m = new ImagePlus("", mask);
		cal.disableDensityCalibration();//mask does not need density calibration.
		m.setCalibration(cal);
		return m;
	}
	
	public static ImagePlus run(SlideGlass sg, Byte maskValue) {
		int w = sg.getWidth();
		int h = sg.getHeight();
		ArrayList<RoiObj> rois_in_slide = sg.getRois();
		ImagePlus blank = run(null, sg, maskValue);
		if(rois_in_slide.size() == 0) {
			return blank;
		}
		ImagePlus sum = blank.createImagePlus();
		for(int i=0; i<rois_in_slide.size();i++) {
			RoiObj roi = rois_in_slide.get(i);
			ImagePlus imp = run(roi, sg, (byte)1);
			if(i==0) {
				sum = ImageCalculator.run(blank, imp, "add create 32-bit");
				continue;
			}
			sum = ImageCalculator.run(sum, imp, "add 32-bit");
		}
		float[] pixelsSum = (float[])sum.getProcessor().getPixels();
		byte[] pixels = (byte[])blank.getProcessor().getPixels();
		for(int j=0; j<h; j++) {
			for(int i=0; i<w; i++) {
				float v = Math.abs(pixelsSum[w*j+i]);
				if(v > 0.0000001) {
					pixels[w*j+i] = maskValue;
				}
			}
		}
		ImageProcessor mask = new ByteProcessor(w, h, pixels);
		Calibration cal = sg.getOriginalImage().getCalibration();
		ImagePlus m = new ImagePlus("", mask);
		cal.disableDensityCalibration();//mask does not need density calibration.
		m.setCalibration(cal);
		return m;
	}
	
	/**
	 * Create mask from SlideGlasses.
	 * If SlideGlass has multiple rois, mask will be integrated SlideGlass by SlideGlass.
	 * 
	 * @param prap
	 * @param processSeries
	 * @param maskValue
	 * @return
	 */
	public static ImagePlus run(Praparat prap, boolean processSeries, Byte maskValue) {
		if(processSeries) {
			ImageStack stack = new ImageStack(); 
			ConcurrentHashMap<Integer, SlideGlass> slides = prap.getAllSlides();
			for(Integer pos : slides.keySet()) {
				SlideGlass sg = slides.get(pos);
				ImagePlus imp = run(sg, maskValue);
				stack.addSlice(imp.getProcessor());
			}
			return new ImagePlus("mask", stack);
		}else {
			SlideGlass sg = prap.getCurrentSlide();
			return run(sg, maskValue);
		}
	}
	
	/**
	 * Use new ByteProcessor(w,h) instead.
	 * @param w
	 * @param h
	 * @return
	 */
	@Deprecated
	static byte[] blankArray(int w, int h) {
		byte[] b = new byte[w*h];
		for(int i=0; i< w*h;i++) {
			b[i] = (byte)0;
		}
		return b;
	}

	/**
	 * Create mask from roi.
	 * @param roi
	 * @param slide
	 * @param maskValue
	 * @return
	 */
	private static ByteProcessor getMask(RoiObj roi, SlideGlass slide, Byte maskValue/*1 to 255, default 255*/) {
		byte mValue = (byte) 255;
		if(maskValue != null) {
			if((maskValue < 1) || (maskValue > 255)) {
				throw new IllegalArgumentException("Mask value must be in range (1 <= v <= 255)");
			}else {
				mValue = maskValue;
			}
		}
		ImagePlus orgImp = slide.getOriginalImage();
		ImageProcessor dup_ip = orgImp.getProcessor().duplicate();//create copy
		ij.gui.Roi ijRoi = new RoiConverter().convert2Roi(roi);
		dup_ip.setRoi(ijRoi);//update mask using roi (without point roi)
		dup_ip = dup_ip.getMask();//ByteProcessor. in mask 255, outside of mask is 0.
		int h =dup_ip.getHeight();
		int w =dup_ip.getWidth();
		byte[] pixels = (byte[])dup_ip.getPixels(); 
		for(int j=0;j<h;j++) {
			for(int i=0;i<w;i++) {
				byte v = pixels[w*j+i];
				v = (byte)(v/(byte)255);//to be one
				v = (byte)(v * mValue);//change to maskValue
				pixels[w*j+i] = v;//update
			}
		}
		dup_ip.setPixels(pixels);
		return (ByteProcessor)dup_ip;
	}
}
