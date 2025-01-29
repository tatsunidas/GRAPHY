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
	/**
	 * This orgImg is calibrated by SlideGlass.initImageInfo().
	 * see also, GDicomTools.calibrate() function.
	 */
	private final ImagePlus orgImg;//without calibration
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
		displayImg = createInitialDisplayImage();//init display image
	}
	
	/**
	 * create image to display, it was fitted prap size without zoom/pan/rotation/windowing.
	 * @return
	 */
	ImagePlus createInitialDisplayImage() {
		/*
		 * getOriginalImage().duplicate();//DO NOT USE, calibration was removed.
		 */
		ImagePlus imp = getOriginalImage().createImagePlus();
		ImageProcessor ip = getOriginalImage().getProcessor().duplicate();
		ip.setInterpolationMethod(sg.INTERPOLATION_METHOD);
		if(sg.isRGB && ip instanceof ColorProcessor) {
			ip.snapshot();//keep original pixels
		}
		imp.setProcessor(ip);
		imp.setTitle("replica");
		// resize to comp size
		/*
		 * The calcImageSize2FitComponent method makes scaleX and scaleY have the same value.
		 */
		imp = sg.imgProcess.zoom(imp, sg.getScaleFactor()[0]/*here, scale by x*/);
		/*
		 * to fill black background after rotation.
		 * https://forum.image.sc/t/set-background-color-for-rotation-of-a-16-bit-image-shortprocessor-miss-bgcolor-attribute/20585/10
		 */
		if(imp.getBitDepth() == 8) {
			imp.getProcessor().setBackgroundValue(0);
		}else if(imp.getBitDepth() == 16) {
			imp.getProcessor().setBackgroundValue(32768);
		}else if(imp.getBitDepth() == 32) {
			imp.getProcessor().setBackgroundValue(0.5);
		}else {
			imp.getProcessor().setBackgroundValue(0);
			imp.getProcessor().setBackgroundColor(Color.BLACK);
		}
		return imp;
	}
	
	ImagePlus getCurrentStateImageFreshCopy() {
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
		resetImageOrigin(displayImg.getWidth(), displayImg.getHeight());
		repaint();
	}
	
	void updateDisplayImageWithCurrentCondition() {
		this.displayImg = getCurrentStateImageFreshCopy();
		if (!sg.panningFlag) {
			resetImageOrigin(displayImg.getWidth(), displayImg.getHeight());
		}
	}
	
	/**
	 * Set no pannning origin.
	 * 
	 * @param newImgW
	 * @param newImgH
	 * @param prapViewWidth
	 * @param prapViewHeight
	 */
	private void resetImageOrigin(int newImgW, int newImgH) {
		Point defaultOrigin = calcDefaultImageOrigin(newImgW, newImgH);
		this.originX = defaultOrigin.x;
		this.originY = defaultOrigin.y;
		// set false force.
		if (sg.panningFlag) {
			sg.panningFlag = false;
		}
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
		Insets insets = sg.getInsets();
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
		Insets insets = sg.getInsets();
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
	
	int getDisplayOriginX() {
		return originX;
	}
	
	int getDisplayOriginY() {
		return originY;
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
	
	/**
	 * update origin with display coordinates system.
	 */
	public void updateOrigin(int originX, int originY) {
		this.originX = originX;
		this.originY = originY;
	}
	
	/**
	 * For handle ROI.
	 * If origin is an original coordinate system basis,
	 * this method will convert it to display coordinates.
	 */
	public void updateOrigin(int originalScaleOriginX, int originalScaleOriginY, double scale) {
		this.originX = (int)((double)originalScaleOriginX * scale);
		this.originY = (int)((double)originalScaleOriginY * scale);
	}
	
	@Override
	protected void paintComponent(Graphics g) {
	    Graphics2D g2d = (Graphics2D) g;
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
