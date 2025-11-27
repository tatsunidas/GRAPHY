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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;

import javax.swing.JPanel;

import com.vis.dicom.Tag;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * A single frame image screen
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class ImageSpecimenGlass extends JPanel{
	
	/**
	 * src img
	 */
	private final DicomImage dcmImg;
	
	/**
	 * The orgImg is calibrated by SlideGlass.initImageInfo().
	 * See also, GDicomTools.calibrate() function.
	 */
	private final ImagePlus orgImg;//without calibration
	ImagePlus displayImg;
	
	private final String sopUID;
	private final SlideGlass sg;
	
	/**
	 * diaplay image origin in original space (off-screen coordinates).
	 */
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
		resetImageOrigin();
	}
	
	/**
	 * @return imageplus , copy of orgImg. 
	 */
	ImagePlus createInitialDisplayImage() {
		/*
		 * getOriginalImage().duplicate();//DO NOT USE, calibration was removed.
		 */
		ImagePlus dup = getOriginalImage().createImagePlus();
		ImageProcessor ip = getOriginalImage().getProcessor().duplicate();
		ip.setInterpolationMethod(sg.INTERPOLATION_METHOD);
		if(sg.isRGB && ip instanceof ColorProcessor) {
			ip.snapshot();//keep original pixels
		}
		dup.setProcessor(ip);
		dup.setTitle("replica");
		/*
		 * to fill black background after rotation.
		 * https://forum.image.sc/t/set-background-color-for-rotation-of-a-16-bit-image-shortprocessor-miss-bgcolor-attribute/20585/10
		 */
		if(dup.getBitDepth() == 8) {
			dup.getProcessor().setBackgroundValue(0);
		}else if(dup.getBitDepth() == 16) {
			dup.getProcessor().setBackgroundValue(32768);
		}else if(dup.getBitDepth() == 32) {
			dup.getProcessor().setBackgroundValue(0.5/*TODO is it correct ?*/);
		}else {//color RGB
			dup.getProcessor().setBackgroundValue(0);
			dup.getProcessor().setBackgroundColor(Color.BLACK);
		}
		return dup;
	}
	
	ImagePlus applyCurrentState(ImagePlus dup) {
		if (sg.isFlipped()) {
			dup.getProcessor().flipHorizontal();
		}
		if (sg.isZoomed()) {
			double mag = sg.getMagnification();
			dup = sg.imgProcess.zoom(dup, mag);
		}
		if (sg.isRotated()) {
			sg.imgProcess.rotate(dup, sg.getRotateAngle());
			updateOriginWithCurrentCondition();
		}
		dup.setLut(sg.currentLUT);
		dup.updateAndDraw();
		if (sg.isInverted()) {
			sg.imgProcess.invert(dup);
		}
		
		if(sg.flipVerticalFlag) {
			sg.imgProcess.flipHF(dup);
		}
		
		if(sg.flipHorizontalFlag) {
			sg.imgProcess.flipLR(dup);
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
	
	
	/**
	 * calc origin xy on image specimen ( which has same size of view panel).
	 * Ignored pannning.
	 * 
	 * @param newImgW
	 * @param newImgH
	 * @param compWidth
	 * @param compHeight
	 * @return
	 */
	Point calcDefaultImageOrigin(int newImgW, int newImgH) {
		Insets insets = sg.getInsets();//the border's insets
       int x = (getWidth() - insets.left - insets.right - newImgW) / 2 + insets.left;
       int y = (getHeight() - insets.top - insets.bottom - newImgH) / 2 + insets.top;
		return new Point(x, y);
	}
	
	/*
	 * fit size to slide
	 * image drawable area will be small by BORDER size.
	 * Maintain aspect ratio, i.e., scale XY will be same.
	 */
	Dimension calcImageSize2FitComponent() {
		/*
		 * The size of the border is calculated using Insets.
		 */
		if(sg == null) {
			return null;
		}
		Insets insets = sg.getInsets();//the border's insets
		int drawableWidth = getWidth() - insets.left - insets.right;
		int drawableHeight = getHeight() - insets.top - insets.bottom;
		
		int bound_width = drawableWidth;
		int bound_height = drawableHeight;
		
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
			new_height = bound_height;
			new_width = (new_height * original_width) / original_height;
		}
		return new Dimension(new_width, new_height);
	}
	
	public DicomImage getDicomImage() {
		return dcmImg;
	}
	
	public ImagePlus getOriginalImage() {
		return this.orgImg;
	}
	
	public ImagePlus getDisplayImage() {
		return this.displayImg;
	}
	
	public void setDisplayImage(ImagePlus disp) {
		this.displayImg = disp;
	}
	
	int getDisplayOriginX() {
		return originX;
	}
	
	int getDisplayOriginY() {
		return originY;
	}
	
	public String sopInstanceUID() {
		return sopUID;
	}
	
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
	
	public void resetImageOrigin() {
		Dimension dispDim = calcImageSize2FitComponent();
		if(dispDim != null) {
			Point init_coord = calcDefaultImageOrigin(dispDim.width, dispDim.height);
			originX = init_coord.x;
			originY = init_coord.y;
		}
	}
	
	public void updateOriginWithCurrentCondition() {
		if(sg == null) {
			return;
		}
		if(displayImg == null) {
			return;
		}
		Point newOrigin = sg.slideglassCoordinateFromOffScreen(0/*offscreenX*/, 0/*offscreenY*/);
		updateOrigin(newOrigin.x, newOrigin.y);
	}
	
	/**
	 * update origin with display coordinates system.
	 */
	public void updateOrigin(int originX, int originY) {
		this.originX = originX;
		this.originY = originY;
	}
	
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;
		
		Dimension dispDim = calcImageSize2FitComponent();
		ImagePlus dup = createInitialDisplayImage();
		dup = dup.resize(dispDim.width, dispDim.height, "none" /*here, keep NONE ! See, applyCurrentState()*/);
		this.displayImg = applyCurrentState(dup);
		
		Point init_coord = calcDefaultImageOrigin(dispDim.width, dispDim.height);
		if(!sg.panningFlag) {
			originX = init_coord.x;
			originY = init_coord.y;
		}
		
		if (transparent) {
			g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			g2d.drawImage(this.displayImg.getImage(), originX, originY, this);
		} else {
			g2d.drawImage(this.displayImg.getImage(), originX, originY, this);
		}
		g2d.dispose();
	}
}
