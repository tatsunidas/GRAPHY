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
package com.vis.core.view.D2.ui.glasses;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;

import javax.swing.JPanel;

import com.vis.core.log.Log;
import com.vis.dicom.Tag;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class ImageSpecimenGlass extends JPanel{
	
	/**
	 * single frame image screen
	 */
	private final DicomImage dcmImg;//src
	private final ImagePlus orgImg;
	private final Calibration orgCal;
	private final String sopUID;
	private final SlideGlass sg;
	public ImagePlus displayImg;
	// display image origin
	public int originX;
	public int originY;
	
	final int orgCols;
	final int orgRows;
	
	private boolean transparent = true;
	private float alpha = 1.0f;
	
	public ImageSpecimenGlass(SlideGlass sg /*single frame*/) {
		this.sg = sg;
		this.dcmImg = sg.getDicomImage();
		this.sopUID = dcmImg.getCore().getString(Tag.SOP​Instance​UID);
		/* No calibrated imageplus */
		this.orgImg = new ImagePlus(sopUID, dcmImg.getImageProcessor(0/*always 0*/));
		orgCols = orgImg.getWidth();
		orgRows = orgImg.getHeight();
		setOpaque(false);
		orgCal = orgImg.getCalibration();
		displayImg = createInitialDisplayImage();//init display image
	}
	
	/**
	 * create image to display, it was fitted prap size without zoom/pan/rotation/windowing.
	 * @return
	 */
	private ImagePlus createInitialDisplayImage() {
//		ImagePlus imp = getOriginalImage().duplicate();//DO NOT USE, calibration was removed.
		ImagePlus imp = getOriginalImage().createImagePlus();
		ImageProcessor ip = getOriginalImage().getProcessor().duplicate();
		ip.setInterpolationMethod(sg.INTERPOLATION_METHOD);
		if(sg.isRGB && ip instanceof ColorProcessor) {
			ip.snapshot();//keep original pixels
		}
		imp.setProcessor(ip);
		imp.setTitle("replica");
		// resize to comp size
		Dimension d = calcImageSize2FitComponent();
		if (d != null) {
			imp = sg.imgProcess.zoom(imp, sg.getScaleFactor());
		}
		return imp;
	}
	
	private ImagePlus getCurrentStateImageFreshCopy() {
		ImagePlus dup = createInitialDisplayImage();
		if (sg.isFlipped()) {
			dup.getProcessor().flipHorizontal();
		}
		if (sg.isZoomed()) {
			double mag = sg.getMagnification();
			dup = sg.imgProcess.zoom(dup, mag);
		}
		if (sg.isRotated()) {
			sg.imgProcess.rotate(dup, sg.getRotateAngle());
		}
		dup.setLut(sg.currentLUT);
		if (sg.isInverted()) {
			sg.imgProcess.invert(dup);
		}
		/*
		 * skip panning, to delegate slideglass::updateImage
		 */
		// window
		if (sg.windowing) {
			sg.imgProcess.windowing(dup, sg.currentMin, sg.currentMax);
		}
		return dup;
	}
	
	void resetDisplayImage() {
		this.displayImg = createInitialDisplayImage();
		calcDefaultImageOriginAndReset(displayImg.getWidth(), displayImg.getHeight(), getWidth(), getHeight());
		repaint();
	}
	
	void updateDisplayImageWithCurrentCondition() {
		this.displayImg = getCurrentStateImageFreshCopy();
		if (!sg.panningFlag) {
			calcDefaultImageOriginAndReset(displayImg.getWidth(),displayImg.getHeight(), getWidth(), getHeight());
		}
	}
	
	private void calcDefaultImageOriginAndReset(int newImgW, int newImgH, int prapViewWidth, int prapViewHeight) {
		Point defaultOrigin = calcDefaultImageOrigin(newImgW, newImgH, prapViewWidth, prapViewHeight);
		this.originX = defaultOrigin.x;
		this.originY = defaultOrigin.y;
		// set false force.
		if (sg.panningFlag) {
			sg.panningFlag = false;
		}
		Log.logger.fine("calcDefaultImageOriginAndReset: Reset CurrentOriginXY: " + originX + " " + originY
				+ " ,compXY: " + prapViewWidth + ", " + prapViewHeight);
	}
	
	Point calcDefaultImageOrigin(int newImgW, int newImgH, int compWidth, int compHeight) {
		/*
		 * width basis
		 */
		if (compWidth <= newImgW && compHeight <= newImgH) {
			return new Point(0, 0);
		}
		int x = 0;
		int y = 0;
		int diffX = compWidth - newImgW;
		int diffY = compHeight - newImgH;
		if (!(diffX < 0)) {
			x = (int) ((double) diffX / 2d);
		}
		if (!(diffY < 0)) {
			y = (int) ((double) diffY / 2d);
		}
		return new Point(x, y);
	}
	
	/*
	 * fit size to slide
	 */
	Dimension calcImageSize2FitComponent() {
		int bound_width = getWidth()-(sg.BORDER_SIZE*2);
		int bound_height = getHeight()-(sg.BORDER_SIZE*2);
		if (bound_width < 1 || bound_height < 1) {
			return null;
		}
		int original_width = orgCols;
		int original_height = orgRows;
		// first, adjust new component size
		int new_width = bound_width;
		// scale height to maintain aspect ratio
		int new_height = (new_width * original_height) / original_width;
		// then check if we need to scale width
		if (original_width > bound_width) {
			// scale width to fit
			new_width = bound_width;
			// scale height to maintain aspect ratio
			new_height = (new_width * original_height) / original_width;
		}
		// then check if we need to scale even with the new height
		if (new_height > bound_height) {
			// scale height to fit instead
			new_height = bound_height;
			// scale width to maintain aspect ratio
			new_width = (new_height * original_width) / original_height;
		}
//		System.out.println("Fit slide to prap : new dim = "+new_width+" "+new_height);
		return new Dimension(new_width, new_height);
	}
	
	public DicomImage getDicomImage() {
		return dcmImg;
	}
	
	public ImagePlus getOriginalImage() {
		return this.orgImg;
	}
	
	int getDisplayOriginX() {
		return originX;
	}
	
	int getDisplayOriginY() {
		return originY;
	}
	
	public Calibration getOriCalibration() {
		return orgCal;
	}
	
	public ImagePlus getDisplayImage() {
		return this.displayImg;
	}
	
	public void setDisplayImage(ImagePlus dispImp) {
		this.displayImg = dispImp;
	}
	
	public String sopInstanceUID() {
		return sopUID;
	}
	
//	/**
//	 * Calibrate original image
//	 * 
//	 * @param dataset
//	 */
//	private void initImageInfo(DicomObject dataset) {
//		Calibration originalCal = orgImg.getCalibration();
//		/*
//		 * TODO load lut from dicom tag ?
//		 */
//		sg.setLUT(orgImg.getProcessor().getLut());
//		sg.isRGB = orgImg.getType() == ImagePlus.COLOR_RGB;// choice suitable one.
//		if (sg.isRGB()) {
//			orgImg.getProcessor().snapshot();
//		}
//
//		/*
//		 * Spatial calibrations
//		 */
//		// x-y-z
//		double pixelSpacingX = 1.0;
//		double pixelSpacingY = 1.0;
//		double pixelSpacingZ = 1.0;
//		// Pixel Spacing = Row Spacing [PY] \ Column Spacing [PX] = 0.30\0.25.
//		double[] pixelSpacing = dataset.getDoubles(com.vis.dicom.Tag.Pixel​Spacing);
//		double spacingBetweenSlices = dataset.getDouble(Tag.Spacing​Between​Slices, -1);
//		if (pixelSpacing != null && pixelSpacing != ByteUtils.EMPTY_DOUBLES) {
//			pixelSpacingX = pixelSpacing[1];// column
//			pixelSpacingY = pixelSpacing[0];// row
//			if (spacingBetweenSlices != -1) {
//				pixelSpacingZ = spacingBetweenSlices;
//			} else {
//				double sliceThickness = dataset.getDouble(Tag.Slice​Thickness, -1);
//				if (sliceThickness != -1) {
//					pixelSpacingZ = sliceThickness;
//				}
//			}
//			/*
//			 * Units is mm, that is dicom default. see, Pixel Spacing Attribute (0028,0030)
//			 * definition.
//			 */
//			originalCal.setUnit("mm");//
//		}
//		// then, set to cal
//		originalCal.pixelWidth = pixelSpacingX;
//		originalCal.pixelHeight = pixelSpacingY;
//		originalCal.pixelDepth = pixelSpacingZ;
//
//		/*
//		 * density calibration
//		 */
//		Double slope = dataset.getDouble(Tag.Rescale​Slope, Double.NaN);
//		Double intercept = dataset.getDouble(Tag.Rescale​Intercept, Double.NaN);
//		Boolean signed = (dataset.getInt(Tag.Pixel​Representation, -1) == 1);
//		String modality = sg.getModality();
//		if (dataset.getInt(Tag.Bits​Allocated, -1) == 16 && signed) {
//			if (!intercept.isNaN() && !slope.isNaN()) {
//				// y = a + bx
//				double[] coeff = new double[2];// [a,b]
//				coeff[0] = intercept - 32768;
//				coeff[1] = slope;
//				originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
//				// add another modalities unit...
//			} else {
//				originalCal.setSigned16BitCalibration();
//			}
//			originalCal.getCTable();// to make cTable.
//			if (modality != null && modality.equals("CT")) {
//				originalCal.setValueUnit("HU");
//			}
//		} else if (intercept != 0.0 && slope == 1.0) {
//			double[] coeff = new double[2];
//			coeff[0] = intercept;
//			coeff[1] = slope;
//			originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
//			originalCal.getCTable();// to make cTable.
//		}
//		// adjust WW/WL
//		int WL = dataset.getInt(Tag.Window​Center, Integer.MIN_VALUE);
//		int WW = dataset.getInt(Tag.Window​Width, Integer.MIN_VALUE);
//		if (WL == Integer.MIN_VALUE || WW == Integer.MIN_VALUE) {
//			sg.autoWindow();
//		} else {
//			sg.changeWindow(WL, WW);
//		}
//		sg.setOriginalCalibration(originalCal.copy());
//	}
	
	public void transparent(boolean on) {
		this.transparent = on;
	}
	
	public boolean isTransparent() {
		return transparent;
	}
	
	public void setAlphaForTransparent(Float alpha) {
		if(alpha == null) {
			this.alpha = 1.0f;
			return;
		}
		if(alpha < 0) {
			this.alpha = 0;
			return;
		}
		if(alpha>1) {
			this.alpha = 1;
			return;
		}
		this.alpha = alpha;
	}
	
	/*
	 * without component(prapview) scale
	 */
	public void updateImage(int originX, int originY) {
		updateImage(originX, originY, null);
	}
	
	/*
	 * with component(prapview) scale
	 */
	public void updateImage(int originX, int originY, Double scale) {
		if(scale == null) {
			this.originX = originX;
			this.originY = originY;
		}else {
			this.originX = (int)((double)originX * scale);
			this.originY = (int)((double)originY * scale);
		}
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		if (sg.panningFlag) {
			/*
			 * pannされている場合は、pann済みのオリジンにスケールをかけて表示位置を補正する
			 */
			if (!sg.panningInAction) {
				updateImage(originX, originY);
			} else {
				updateImage(originX, originY, sg.getScaleFactor());
			}
		} else if (!sg.panningFlag && !sg.panningInAction) {
			/*
			 * pannされていない場合は、コンポーネントサイズにリサイズされた画像を表示する
			 */
			updateImage(originX, originY);
		}else {
			updateImage(originX, originY);
		}
	    Graphics2D g2d = (Graphics2D) g.create();
	    if(transparent) {
	    	g2d.setComposite(AlphaComposite.getInstance(
		            AlphaComposite.SRC_OVER, alpha));
		    g2d.drawImage(displayImg.getImage(), originX, originY, this);
	    }else {
	    	g2d.drawImage(displayImg.getImage(), originX, originY, this);
	    }
	    g2d.dispose();
	}
}
