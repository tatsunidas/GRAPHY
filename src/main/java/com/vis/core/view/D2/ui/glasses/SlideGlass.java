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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.border.Border;

import com.vis.core.log.Log;
import com.vis.core.util.ByteUtils;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.processing.ImagePlusDicomTagTools;
import com.vis.core.view.D2.processing.ImageProcessing;
import com.vis.core.view.D2.roi.ReferenceLine;
import com.vis.core.view.D2.roi.RoiConverter;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiObjManager;
import com.vis.core.view.D2.roi.RoiPopupDialog;
import com.vis.core.view.D2.roi.TextRoi;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.ContrastEnhancer;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

/**
 * image and overlays
 * 
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class SlideGlass extends JLayeredPane {

	// glass layer type
	public final static int IMAGE_LAYER = JLayeredPane.DEFAULT_LAYER;
	public final static int ROI_CANVAS_LAYER = JLayeredPane.PALETTE_LAYER;
	public final static int TEXT_LAYER = JLayeredPane.MODAL_LAYER;
	public final static int EVENT_LAYER = JLayeredPane.DRAG_LAYER;
	
	private Praparat pp;// series viewer
	private DicomObject header;
	private DicomImage dcmImg;
	//	protected Color studyColor = Color.CYAN;
	// glasses
	public ImageSpecimenGlass imageSpecimen;
	private TextOverlayGlass textOverlay;
	private CanvasGlass roiOverlay;
	private EventGlass coverGlass;/*KeyListener*/
	
	// flags
	//private boolean focusFlag = false;
	private boolean selectedFlag = false;
	public boolean panningFlag = false;
	public boolean rotatedFlag = false, flipHorizontalFlag = false, flipVerticalFlag = false, zoomFlag = false;
	private boolean invertFlag = false;
	private boolean flipFlag = false;
	boolean windowing = false;// WW/WL changed
	private boolean showAnnotation = true;
	private boolean showText = true;
	boolean isPDF = false;
	boolean isGrayscale = false;
	boolean isRGB = false;
	private int mouseActionFlag = -1; // MouseAction ModifierEx.

	// ww/wl settings
//	private Calibration originalCal = null;
	protected double currentMin = 0;// current window contrast min
	protected double currentMax = 255;// current window contrast max
	protected double lastMin = -1;// see,ImageLayerUI::processMouseEvent
	protected double lastMax = -1;// see,ImageLayerUI::processMouseEvent
	// rotate
	public int currentRotateAngle = 0;
	public int lastRotateAngle = 0;
	// zoom
	private double magnification = 1.0d;// zoom ratio 1 to N
	
	// mouse settings
	/*
	 * Coordinates on SlideGlass(same as viewPanel coordinates). These are not the coordinate system of the
	 * original image, but the coordinates on the SlideGlass fitted to the current
	 * ViewPanel.
	 */
	public int lastDraggedX = 0;
	public int lastDraggedY = 0;
	public int mouseX = 0;// current mouse loc on slideglass
	public int mouseY = 0;// current mouse loc on slideglass
	LUT currentLUT;//null-able, if null set grayscale

	// for pixel scale (praparatview vs original)
	private double scale = 1.0d; // (fit to comp size)/(original)

	public int INTERPOLATION_METHOD = ImageProcessor.NEAREST_NEIGHBOR;
	ImageProcessing imgProcess = new ImageProcessing();
	private ArrayList<RoiObj> roiset;
	Logger logger = Log.logger;

	public SlideGlass(Praparat pp, DicomImage dcmImg/*single frame*/) {
		if(pp == null || dcmImg == null) {
			throw new NullPointerException();
		}
		initComponents(pp, dcmImg);
	}
	
	public void addRoi(RoiObj newRoi) {
		if(newRoi instanceof ReferenceLine) {
			return;
		}
		if (!this.roiset.contains(newRoi)) {
			if(isExistsInRoiSet(newRoi)) {
				updateRoi(newRoi.getStudyUID(), newRoi.getSeriesUID(), newRoi.getSopUID(), newRoi.getProperty(RoiObj.RoiContextKeySet.RoiID.name()), newRoi);
			}else {
				roiset.add(newRoi);
				insertOrUpdateRoi4DB(newRoi);
			}
		}
	}

	public void addRoi(RoiObj newRoi, boolean updateDB) {
		if(newRoi instanceof ReferenceLine) {
			return;
		}
		if(updateDB) {
			addRoi(newRoi);
		}else {
			if (!this.roiset.contains(newRoi)) {
				roiset.add(newRoi);
			}
		}
	}
	
	/**
	 * set slideglass size and update image specimen
	 */
	@Override
	public void setSize(int compW, int compH) {
		/*
		 * keep default bounds of SlideGlass-self.
		 */
//		setGlassSize(this, compW, compH);//to avoid setBounds(0,0, w, h,).
		super.setSize(compW, compH);//for updateScale()
		super.setPreferredSize(new Dimension(compW, compH));
		setGlassSize(imageSpecimen, compW, compH);
		setGlassSize(textOverlay, compW, compH);
		setGlassSize(roiOverlay, compW, compH);
		setGlassSize(coverGlass, compW, compH);
		updateScale();
		initPrapInfoLabel();
		imageSpecimen.updateDisplayImageWithCurrentCondition();
		repaint();
	}
	
	void adjustWindow2Current() {
		if (currentMax == -1 || currentMin == -1) {
			return;
		}
		setWindowingState(true);
		// https://imagej.nih.gov/ij/plugins/window-level-tool/Window_Level_Tool.java
		// current settings
		double currentWindow = currentMax - currentMin;
		double currentLevel = currentMin + (.5 * currentWindow);
		changeWindowing((int) currentLevel, (int) currentWindow);
	}

	void adjustContrastFromMouseAction(int locX, int locY) {
		double minMaxDifference = getCurrentDisplayImagePlus().getDisplayRangeMax()
				- getCurrentDisplayImagePlus().getDisplayRangeMin();
		int xDiff = locX - mouseX;
		int yDiff = locY - mouseY;
		int totalWidth = pp.getImageScreenSizeX();
		int totalHeight = pp.getImageScreenSizeY();
		double xRatio = ((double) xDiff) / ((double) totalWidth);
		double yRatio = ((double) yDiff) / ((double) totalHeight);
		// scale to our image range
		double xScaledValue = minMaxDifference * xRatio;
		double yScaledValue = minMaxDifference * yRatio;
		// to avoid rangeMin > rangeMax
		if (Math.abs(xScaledValue) > minMaxDifference) {
			if (xScaledValue < 0) {
				xScaledValue = -1 * minMaxDifference;
			} else {
				xScaledValue = minMaxDifference;
			}
		}
		if (Math.abs(yScaledValue) > minMaxDifference) {
			if (yScaledValue < 0) {
				yScaledValue = -1 * minMaxDifference;
			} else {
				yScaledValue = minMaxDifference;
			}
		}
		// invert x
//				 xScaledValue = xScaledValue * -1;
		adjustWindowLevel(xScaledValue, yScaledValue);
	}

	void adjustWindowLevel(double xDifference, double yDifference) {
		this.windowing = true;
		// https://imagej.nih.gov/ij/plugins/window-level-tool/Window_Level_Tool.java
		// current settings
		double currentWindow = lastMax - lastMin;
		double currentLevel = lastMin + (.5 * currentWindow);
		// change
		double newWindow = currentWindow + xDifference;
		double newLevel = currentLevel + yDifference;
		changeWindowing((int) newLevel, (int) newWindow);
	}

	/**
	 * see also ImageUtils.autoContrast()
	 */
	public void autoWindowing() {
		if(getOriginalImage() == null || getCurrentDisplayImagePlus() == null) {
			return;
		}
		if(isRGB()) {
			getOriginalImage().getProcessor().reset();
		}
		new ContrastEnhancer().stretchHistogram(getOriginalImage().getProcessor(), 0.5);
		this.currentMin = getOriginalImage().getProcessor().getMin();// DO NOT USE getMinThreshold()
		this.currentMax = getOriginalImage().getProcessor().getMax();// DO NOT USE getMaxThreshold()
		imgProcess.windowing(imageSpecimen.getDisplayImage(), this.currentMin, this.currentMax);
	}

	public void changeWindowing(int WL, int WW) {
		double newMin = WL - (.5 * WW);
		double newMax = WL + (.5 * WW);
		if (newMin > newMax) {
			logger.log(Level.SEVERE,"SlideGlass::changeWindow() problem occured: min " + newMin + " max " + newMax);
		}
//		lastMin = currentMin;//DO NOT SET HERE, see mouse enter 
//		lastMax = currentMax;//DO NOT SET HERE, see mouse enter 
		currentMin = newMin;
		currentMax = newMax;
		if(Utils.isDebug)logger.info("change ww/wl : newMin "+newMin+" newMax "+newMax);
		imgProcess.windowing(imageSpecimen.getDisplayImage(), newMin, newMax);
		repaint();
	}
	
	public void resetWindowing() {
		// adjust WW/WL
		int WL = header.getInt(Tag.Window​Center, Integer.MIN_VALUE);
		int WW = header.getInt(Tag.Window​Width, Integer.MIN_VALUE);
		if (WL == Integer.MIN_VALUE || WW == Integer.MIN_VALUE) {
			autoWindowing();
		} else {
			changeWindowing(WL, WW);
		}
	}

	/**
	 * see, ImageSpecimen
	 * @param currentbufferedimage
	 * @param overlayImg
	 * @return
	 */
	@SuppressWarnings("unused")
	private BufferedImage combineImages(BufferedImage currentbufferedimage, BufferedImage overlayImg) {
		Graphics2D g2d = currentbufferedimage.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g2d.drawImage(overlayImg, 0, 0, null);
		g2d.dispose();
		return currentbufferedimage;
	}

	public ImagePlus convertToImagePlus() {
		return ImagePlusDicomTagTools.dcmImgToImagePlus(getDicomImage());
	}

	public ImagePlus cropRect() {
		if(roiset.size() < 1) {
			return null;
		}
		RoiObj roi = findCurrentRoi();
		int type = roi.getType();
		System.out.println(type);
		if(type != RoiObj.RECTANGLE && type != RoiObj.OVAL && type != RoiObj.POLYGON) {
			return null;
		}
		Rectangle2D rect = roi.getBounds();
//		Roi r = new RoiConverter().convert2Roi(roi);
		Roi r = new Roi(rect.getX(),rect.getY(),rect.getWidth(), rect.getHeight());
		ImagePlus orgImp = getOriginalImage();
		ImageProcessor ip = orgImp.getProcessor().duplicate();
		ip.setRoi(r);
		ip = ip.crop();
		ImagePlus cropImp = getOriginalImage().createImagePlus();
		cropImp.setProcessor(ip);
		return cropImp;
	}

	public ImagePlus processCropRect(RoiObj rectRoi) {
		if(rectRoi == null) {
			return null;
		}
		RoiObj roi = rectRoi;
		int type = roi.getType();
		System.out.println(type);
		if(type != RoiObj.RECTANGLE && type != RoiObj.OVAL && type != RoiObj.POLYGON) {
			return null;
		}
		Rectangle2D rect = roi.getBounds();
//		Roi r = new RoiConverter().convert2Roi(roi);
		Roi r = new Roi(rect.getX(),rect.getY(),rect.getWidth(), rect.getHeight());
		ImagePlus orgImp = getOriginalImage();
		ImageProcessor ip = orgImp.getProcessor().duplicate();
		ip.setRoi(r);
		ip = ip.crop();
		ImagePlus cropImp = getOriginalImage().createImagePlus();
		cropImp.setProcessor(ip);
		return cropImp;
	}

	/**
	 * TODO 20240803
	 * @param roi
	 */
	public void processCut(RoiObj roi) {
		if(roi == null) {
			return;
		}
		int roiType = roi.getType();
		if(roiType == RoiObj.ANGLE || roiType == RoiObj.ARROW || roiType == RoiObj.FREELINE || roiType == RoiObj.POINT || roiType==RoiObj.LINE) {
			JOptionPane.showMessageDialog(Viewer2DScreen.getInstance(), "You need set closed type roi.");
			return;
		}
		ImagePlus imp = getOriginalImage();
		Roi ijRoi = new RoiConverter().convert2Roi(roi);
		imp.setRoi(ijRoi);
//		imp.getProcessor().fill();
//		imp.getProcessor().set(0);
//		setOriginalImage(imp);
		repaint();
	}

	public void deleteRoi(int sx, int sy) {
		if (roiset == null || roiset.size() < 1) {
			return;
		}
		RoiObj roi2remove = roiOverlay.activateAndGetRoiAt(sx, sy);
		if (roi2remove != null) {
			String studyUID = roi2remove.getStudyUID();
			String seriesUID = roi2remove.getSeriesUID();
			String sopUID = roi2remove.getSopUID();
			String roiInd = roi2remove.getProperty(RoiObj.RoiContextKeySet.RoiID.name());
			deleteRoi(studyUID, seriesUID, sopUID, roiInd);
		} else {
			/*
			 * TODO delete activeRoi ?
			 */
		}
		repaint();
	}

	public void deleteRoi(RoiObj roi2remove) {
		if (roiset == null || roiset.size() < 1) {
			return;
		}
		String studyUID = roi2remove.getStudyUID();
		String seriesUID = roi2remove.getSeriesUID();
		String sopUID = roi2remove.getSopUID();
		String roiInd = roi2remove.getProperty(RoiObj.RoiContextKeySet.RoiID.name());
		deleteRoi(studyUID, seriesUID, sopUID, roiInd);
	}

	public void deleteRoi(String studyUID, String seriesUID, String sopUID, String roiInd) {
		if (roiset == null || roiset.size() < 1) {
			return;
		}
		/*
		 * pay attention remove item from list
		 * see, https://stackoverflow.com/questions/8104692/how-to-avoid-java-util-concurrentmodificationexception-when-iterating-through-an
		 */
		Iterator<RoiObj> itr = roiset.iterator();
		ArrayList<RoiObj> roi2Remove = new ArrayList<>();
		while(itr.hasNext()){
		    RoiObj roi = itr.next();
			if (roi.isThisRoi(studyUID, seriesUID, sopUID, roiInd)) {
				removeRoiPopupDialogOnCanvas(roi.getRoiPopupDialog());
				deleteRoiFromDB(roi);
//				roiset.remove(roi);//DO NOT DO THIS !
				roi2Remove.add(roi);
//					roi = null;//safe ??
				break;
			} else if (studyUID == null && seriesUID == null && sopUID == null) {
				// SliceLine or temporal roi
				// skip delete from db
				removeRoiPopupDialogOnCanvas(roi.getRoiPopupDialog());
				roi2Remove.add(roi);
			}
		}
		if(roiset.size() > 0) {
			roiset.removeAll(roi2Remove);
			roiset.trimToSize();
		}
		roiOverlay.setCurrentRoi2NULL();
	}

	private void deleteRoiFromDB(RoiObj roi) {
		String patID = roi.getPatientID();
		String studyUid = roi.getStudyUID();
		String seriesUid = roi.getSeriesUID();
		String sopUid = roi.getSopUID();
		String roiId = roi.getPropertyAt(RoiObj.RoiContextKeySet.RoiID.name());
		DatabaseHandler.getInstance().deleteRoi(patID, studyUid, seriesUid,sopUid,roiId);
		if(Viewer2DScreen.getRoiObjManager() != null) {
			RoiObjManager rom = Viewer2DScreen.getRoiObjManager();
			rom.updateRoiObjList(getPatientID());
		}
	}

	public void drawCross(MouseEvent e) {
		Point currentScreenPos = e.getPoint();
		GeneralPath path = new GeneralPath();
		int sx = currentScreenPos.x;
		int sy = currentScreenPos.y;
		path.moveTo(0f, sy);
		path.lineTo(getWidth(), sy);
		path.moveTo(sx, 0f);
		path.lineTo(sx, getHeight());
		roiOverlay.setCrossLine(path);
		// do not return
		repaint();
	}

	public void drawCross(Point onOrgImageCoordinatePoint) {
    	GeneralPath path = new GeneralPath();
    	int sx = screenX(onOrgImageCoordinatePoint.x);
    	int sy = screenY(onOrgImageCoordinatePoint.y);
        path.moveTo(0f, sy);
        path.lineTo(getWidth(), sy);
        path.moveTo(sx, 0f);
        path.lineTo(sx, getHeight());
        roiOverlay.setCrossLine(path);
        //do not return
        repaint();
    }

	/**
	 * null-able.
	 * if set to null, stop displaying localizer.
	 * @param localizer
	 */
	public void drawLocalizer(java.util.List<java.awt.geom.Point2D> localizerGeo) {
		roiOverlay.setLocalizerGeometry(localizerGeo);
	}

	public RoiObj findCurrentRoi() {
		RoiObj currentRoi = getActiveRoi();
		if (currentRoi != null) {
			return currentRoi;
		} else {
			currentRoi = roiOverlay.getCurrentRoi();
			if(currentRoi == null) {
				return roiOverlay.getPreviousRoi();
			}else {
				return currentRoi;
			}
		}
	}

	public void flipHF() {
		rotate(180);// to avoid flipFlag mismatch, run first.
		flipLR();
		repaint();
	}

	public void flipLR() {
		if (isFlipped()) {
			setFlipState(false);
		} else {
			setFlipState(true);
		}
		imageSpecimen.getDisplayImage().getProcessor().flipHorizontal();
		repaint();
	}

	public RoiObj getActiveRoi() {
		ArrayList<RoiObj> rois = getRois();
		for (RoiObj roi : rois) {
			if (roi.isActiveOverlayRoi()) {
				return roi;
			}
		}
		return null;
	}

	public ImagePlus getCurrentDisplayImagePlus() {
		return this.imageSpecimen.getDisplayImage();
	}

	// see, TestRoi2.java
	public int getCurrentModifiersEx() {
		return mouseActionFlag;
	}

	/*
	 * mouse position on slide glass XY location
	 */
	public Point getCursorLoc() {
		Point pointOnViewPanel = new Point(mouseX, mouseY);
		return pointOnViewPanel;
	}

	public DicomImage getDicomImage() {
		return this.dcmImg;
	}
	
	public Dimension getDisplayImageDimension() {
		int[] dim = this.imageSpecimen.getDisplayImage().getDimensions();
		return new Dimension(dim[0], dim[1]);
	}
	
	/*
	 * current display image origin
	 */
	public Point getDisplayImageLocationXY() {
		Point origin = new Point(imageSpecimen.getDisplayOriginX(),imageSpecimen.getDisplayOriginY());
		return origin;
	}

	public double getDisplayPixelSpacingX() {
		return getOriginalCalibration().pixelWidth;
	}
	
	public double getDisplayPixelSpacingY() {
		return getOriginalCalibration().pixelHeight;
	}

	public double getDisplayPixelSpacingZ() {
		return getOriginalCalibration().pixelDepth;
	}

	public Object getGlassAt(int layer_type) {
		if(layer_type == IMAGE_LAYER) {
			return imageSpecimen;
		}else if(layer_type == ROI_CANVAS_LAYER) {
			return roiOverlay;
		}else if(layer_type == TEXT_LAYER) {
			return textOverlay;
		}else {
			return null;
		}
	}

	public DicomObject getHeader() {
		return header;
	}
	
	public Integer getInstanceNo() {
		return header != null ? header.getInt(Tag.Instance​Number, 0):null;
	}

	public double getMagnification() {
		return this.magnification;
	}
	
//	public void showBorder(boolean show) {
//		if(show) {
//			showBorder();
//		}else {
//			Border b = BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
//			setBorder(b);
//		}
//	}
	
	public String getModality() {
		return header != null ? header.getString(Tag.Modality, "UNKNOWN"):null;
		/*
		 * IJ return null...
		 */
//		if(header != null) {
//			return header.getString(Tag.Modality, "UNKNOWN");
//		}else {
//			return DicomTools.getTag(getOriginalImage(), "0008,0060");
//		}
	}
	
	public Calibration getOriginalCalibration() {
		return getOriginalImage().getCalibration();
	}

	/*
	 * load reference size (praparatview size) image
	 */
//	private ImagePlus loadImageThatFittedPrap() {
//		ImageProcessor ip = getOriginalImage().getProcessor().duplicate();
//		ip.setInterpolationMethod(INTERPOLATION_METHOD);
//		ImagePlus dup = new ImagePlus("", ip);
//		dup.setCalibration(cal);
//		dup.updateImage();
//		if(isRGB && ip instanceof ColorProcessor) {
//			ip.snapshot();//keep original pixels
//		}
//		Dimension d = calcImageSize2FitComponent();
//		if (d != null) {
//			dup = imgProcess.zoom(dup, getScaleFactor());// imp.resize(d.width, d.height, "bicubic");
//		}
//		return dup;
//	}
	
	public ImagePlus getOriginalImage() {
		return this.imageSpecimen.getOriginalImage();
	}
	
	public Dimension getOriginalImageSize() {
		int[] dims = getOriginalImage().getDimensions();
		return new Dimension(dims[0], dims[1]);
	}

	public double getOriginalPixelSpacingX() {
		return getOriginalCalibration().pixelWidth;
	}

	public double getOriginalPixelSpacingY() {
		return getOriginalCalibration().pixelHeight;
	}

	public double getOriginalPixelSpacingZ() {
		return getOriginalCalibration().pixelDepth;
	}

	public String getPatientID() {
		return header != null ? header.getString(Tag.Patient​ID, "NO_PID") : null;// safe ?
	}

	public String getPixelSpacingUnit() {
		return getOriginalCalibration().getUnit();
	}
	
	/*
	 * return no calibrate val and calibrated val at displayImageX and displayImageY
	 * are coordinate on the display image.
	 * displayImageX and displayImageY are not slideX/Y.
	 * slideXY has praparat origin.
	 * displayImage have origin(0,0), but it was magnified and scaled.
	 */
	public Object[] getPixelValueFromDisplay(int displayImageX, int displayImageY) {
		if(!isRGB()) {
			int pix_raw = getCurrentDisplayImagePlus().getProcessor().get(displayImageX, displayImageY);
			double pix_cal = getCurrentDisplayImagePlus().getProcessor().getPixelValue(displayImageX, displayImageY);
			double v_raw = 0;
			double v_cal = 0;
			if(dcmImg.getBitsAllocated() == 32) {
				v_raw = Float.intBitsToFloat(pix_raw);
			}else if (dcmImg.isSigned()){
				v_raw = pix_cal;//-32768 by calibration function
				v_cal = pix_raw;//original scale
			}else {
				v_raw = pix_raw;
				v_cal = pix_cal;
			}
			return new Double[] { v_raw, v_cal };
		}else {
			ColorProcessor cp = (ColorProcessor)getCurrentDisplayImagePlus().getProcessor();
			int[] rgb = cp.getPixel(displayImageX, displayImageY, null);
			String color = cp.getColor(displayImageX, displayImageY).toString();
			return new String[] { String.valueOf(rgb[0]), String.valueOf(rgb[1]), String.valueOf(rgb[2]), color };
		}
	}

	public double[] getPixelValueFromOriginal(int orgImageX, int orgImageY) {
		double pix_raw = getOriginalImage().getProcessor().get(orgImageX, orgImageY);
		double pix_cal = getOriginalImage().getProcessor().getPixelValue(orgImageX, orgImageY);
		return new double[] { pix_raw, pix_cal };
	}

	protected Object[] getPixelValueOnSlide(int slideX, int slideY) {
		if (pp == null) {
			return null;
		}
		// 画像の原点座標を取得する
		Point currentOrigin = getDisplayImageLocationXY();
		// 画像のディメンションを取得する
		Dimension currentDimension = getDisplayImageDimension();
		// ディメンション内のとき、ピクセル値を出力する
		if (panningFlag) {
			/*
			 * pannされている場合は、pann済みのオリジンにスケールをかけて表示位置を補正する see,ImageSpecimen.paintComponent()
			 */
			int scaledOriginX = (int) (currentOrigin.x * getScaleFactor());
			int scaledOriginY = (int) (currentOrigin.y * getScaleFactor());
			if (scaledOriginX <= slideX && slideX < (scaledOriginX + currentDimension.width)) {
				if (scaledOriginY <= slideY && slideY < (scaledOriginY + currentDimension.height)) {
					int imageX = slideX - scaledOriginX;
					int imageY = slideY - scaledOriginY;
					return getPixelValueFromDisplay((imageX), (imageY));
				}
			}
		} else {
			/*
			 * pannされていない場合は、PrapView中心に、コンポーネントサイズにリサイズされた画像を表示する
			 */
			if (currentOrigin.x <= slideX && slideX < (currentOrigin.x + currentDimension.width)) {
				if (currentOrigin.y <= slideY && slideY < (currentOrigin.y + currentDimension.height)) {
					int imageX = slideX - currentOrigin.x;
					int imageY = slideY - currentOrigin.y;
					return getPixelValueFromDisplay((imageX), (imageY));
				}
			}
		}
		return null;
	}

	public Praparat getPraparat() {
		return this.pp;
	}

	public ReferenceLine getReferenceLine() {
		if(pp != null) {
			return pp.getReferenceLine();
		}else {
			return null;
		}
	}

	public ArrayList<RoiObj> getRoiAt(String sopUID) {
		ArrayList<RoiObj> roisOnSlice = new ArrayList<RoiObj>();
		for (RoiObj roi : getRois()) {
			if (roi.getProperty("SOPInstanceUID") != null && roi.getProperty("SOPInstanceUID").equals(sopUID)) {
				roisOnSlice.add(roi);
			}
		}
		if (roisOnSlice.size() < 1) {
			return null;
		} else {
			return roisOnSlice;
		}
	}

	/**
	 * 
	 * @param screenX:slideX
	 * @param screenY:slideY
	 * @return
	 */
	public RoiObj getRoiLoacationAt(int screenX, int screenY) {

		int ix = onImageX(screenX);
		int iy = onImageY(screenY);
		ArrayList<RoiObj> rois = getRois();
		/*
		 * if rois are overlapping, return roi that find first.
		 */
		if (rois != null && rois.size() > 0) {
			for (RoiObj roi : rois) {
				if (roi.contains(ix, iy)) {
					return roi;
				}
			}
		}
		return null;
	}

	public RoiPopupDialog getRoiPopupAt(int slideX, int slideY) {
		/*
		 * MouseEventのgetXYでは、
		 * RoiPopupDialogがJPanelのサブクラスならslideXYのままでいいのだけど TextAreaにすると座標がリセットされる
		 */
		Component com = roiOverlay.getComponentAt(slideX, slideY);
		if (com != null && com != roiOverlay && com instanceof RoiPopupDialog) {
			return (RoiPopupDialog) com;
		} else {
			return null;
		}
	}

	public RoiPopupDialog getRoiPopupAt(MouseEvent e) {
		Object obj = e.getSource();
		if(obj != null && obj instanceof RoiPopupDialog) {
			return (RoiPopupDialog)obj;
		}else {
			return null;
		}
	}

	public RoiPopupDialog getRoiPopupFromRoiAt(int slideX, int slideY) {
		RoiObj roi = roiOverlay.activateAndGetRoiAt(slideX, slideY);
		if (roi == null) {
			return null;
		} else {
			return roi.getRoiPopupDialog();
		}
	}

	public ArrayList<RoiObj> getRois() {
		return this.roiset;
//    	return pp.getRoiAt(header.getString(Tag.SOPInstanceUID));
	}

	public int getRotateAngle() {
		return currentRotateAngle;
	}

	public double getScaleFactor() {
		updateScale();
		return this.scale;
	}

	public String getSeriesInstanceUID() {
		return header != null ? header.getString(Tag.Series​Instance​UID, "NO_SeriesInstanceUID"):null;
	}

	public String getSOPInstanceUID() {
		return header != null ? header.getString(Tag.SOP​Instance​UID, "NO_SOPInstanceUID"):null;
	}

	public String getStudyInstanceUID() {
		return header != null ? header.getString(Tag.Study​Instance​UID, "NO_StudyInstanceUID"):null;
	}

	public String getUID(int tag) {
		return header != null ? header.getString(tag):null;
	}
	
	public boolean handleRoiMouseDragged(MouseEvent me) {
		if(pp.isShowCrossLineMode()) {
			drawCross(me);
		}
		mouseActionFlag = me.getModifiersEx();
		return roiOverlay.handleRoiMouseDragged(me, this);
	}

	//todo
	public void handleRoiMouseMoved(MouseEvent me) {
		mouseActionFlag = me.getModifiersEx();
		roiOverlay.mouseMoved(me);
	}

	public void handleRoiMousePressed(MouseEvent me) {
		mouseActionFlag = me.getModifiersEx();// future work change to getButton()? related getModifiersEx...but buggy??
//		roiOverlay.handleRoiMouseDown(me);
		roiOverlay.mousePressed(me);
	}

	public void handleRoiMouseUp(MouseEvent me) {
		mouseActionFlag = me.getModifiersEx();
		roiOverlay.mouseReleased(me);// (me, x, y);
	}

	public void hideRoiDialogAt(int sx, int sy) {
		Component com = roiOverlay.getComponentAt(sx, sy);
		if (com != null && com != roiOverlay && com instanceof RoiPopupDialog) {
			RoiPopupDialog rpd = (RoiPopupDialog) com;
			rpd.setVisible(false);
			roiOverlay.revalidate();// no need, but maybe fail safe
			roiOverlay.repaint();
			repaint();
		}
	}

	public void hideRoiDialogOf(RoiObj roi) {
		if (roi == null) {
			return;
		}
		roi.setVisibleRoiPopup(false);
		roiOverlay.revalidate();// no need, but maybe fail safe
		roiOverlay.repaint();
		repaint();
	}

	public void initComponents(Praparat pp, DicomImage dcmImg/*single frame*/) {
		this.pp = pp;
		this.roiset = new ArrayList<RoiObj>();
		this.dcmImg = dcmImg;
		this.header = dcmImg.getCore();
		setBorder(BorderMaker.make(this, false));
		setOpaque(false);
		setUpGlassLayer(header);
		initImageInfo(header);// execute before Glasses
		loadRoiFromDB();
		setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		SlideGlassMouseListener sgml = new SlideGlassMouseListener(this);
		coverGlass.addMouseListener(sgml);
		coverGlass.addMouseMotionListener(sgml);
		coverGlass.addMouseWheelListener(sgml);
		coverGlass.addKeyListener(new SlideGlassKeyListener(this));
	}

	/**
	 * Calibrate original image
	 * @param dataset
	 */
	private void initImageInfo(DicomObject dataset) {
		if(dataset == null) {
			//for MPR ?
			initImageInfoUsingImagePlus();
			return;
		}
		if(this.header == null) {
			this.header = dataset;
		}
		/*No calibrated imageplus*/
		ImagePlus org = getOriginalImage();
		Calibration originalCal = org.getCalibration();
		/*
		 * TODO
		 * load lut from dicom tag ? todo, see dicomwriter as reference.
		 */
		setLUT(org.getProcessor().getLut());
		isRGB = org.getType() == ImagePlus.COLOR_RGB;//choice suitable one.
		if(isRGB()) {
			org.getProcessor().snapshot();
		}
		
		/*
		 * Spatial calibrations
		 */
		// x-y-z
		double pixelSpacingX = 1.0;
		double pixelSpacingY = 1.0;
		double pixelSpacingZ = 1.0;
		// Pixel Spacing = Row Spacing [PY] \ Column Spacing [PX] = 0.30\0.25.
		double[] pixelSpacing = dataset.getDoubles(Tag.Pixel​Spacing);
		double spacingBetweenSlices = dataset.getDouble(Tag.Spacing​Between​Slices, -1);
		if (pixelSpacing != null && pixelSpacing != ByteUtils.EMPTY_DOUBLES) {
			pixelSpacingX = pixelSpacing[1];// column
			pixelSpacingY = pixelSpacing[0];// row
			if (spacingBetweenSlices != -1) {
				pixelSpacingZ = spacingBetweenSlices;
			} else {
				double sliceThickness = dataset.getDouble(Tag.Slice​Thickness, -1);
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
		Double slope = dataset.getDouble(Tag.Rescale​Slope, Double.NaN);
		Double intercept = dataset.getDouble(Tag.Rescale​Intercept, Double.NaN);
		Boolean signed = (dataset.getInt(Tag.Pixel​Representation, -1) == 1);
		String modality = getModality();
		if (dataset.getInt(Tag.Bits​Allocated, -1) == 16 && signed) {
			if (!intercept.isNaN() && !slope.isNaN()) {
				//y = a + bx
				double[] coeff = new double[2];//[a,b]
				coeff[0] = intercept-32768;
				coeff[1] = slope;
				originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
				//add another modalities unit...
			}else {
				originalCal.setSigned16BitCalibration();
			}
			originalCal.getCTable();//to make cTable.
			if(modality != null && modality.equals("CT")) {
				originalCal.setValueUnit("HU");
			}
		}else if (intercept!=0.0 && slope==1.0) {
			double[] coeff = new double[2];
			coeff[0] = intercept;
			coeff[1] = slope;
			originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
			originalCal.getCTable();//to make cTable.
		}
		// adjust WW/WL
		resetWindowing();
		setOriginalCalibration(originalCal.copy());
	}

	@Deprecated
	private void initImageInfoUsingImagePlus() {
		ImagePlus org = getOriginalImage();
		Calibration originalCal = org.getCalibration().copy();
		isRGB = org.getType() == ImagePlus.COLOR_RGB;//choice suitable one.
		if(isRGB()) {
			org.getProcessor().snapshot();
		}
//		if (org.getNChannels() == 1) {
//			/*
//			 * set density calibration
//			 */
////			if(!originalCal.scaled()) {//DO NOT USE
//			/*
//			 * see, ij.measure.Calibration.setImage()
//			 */
//			// 0 = unsigned, 1 = signed , Tag.PixelRepresentation
//			if (getModality().equals("CT") && org.getType() == ImagePlus.GRAY16) {
//				double slope = Double.parseDouble(DicomTools.getTag(org, "0028,1053").trim());
//				double intercept = Double.parseDouble(DicomTools.getTag(org, "0028,1052").trim());
//				if (intercept == 0 && originalCal.isSigned16Bit()) {
////					double[] coeff = new double[2];
////					coeff[0] = -32768.0;
////					coeff[1] = slope;// 1.0
//					String pixelValUnit = "HU";
////					originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, pixelValUnit);
//					originalCal.setSigned16BitCalibration();
//					originalCal.setValueUnit(pixelValUnit);
//				} else {
//					if (slope != -1 && intercept != -1) {
//						originalCal.setFunction(Calibration.STRAIGHT_LINE, new double[] { intercept, slope }, "HU");
//					}
//				}
//			}else if(originalCal.isSigned16Bit() && org.getType() == ImagePlus.GRAY16) {
//				originalCal.setSigned16BitCalibration();
//			}
////			}
//			// adjust WW/WL
//			/*
//			 * 0x00281050;
//			 * 0028,1051
//			 */
//			int WL = Integer.parseInt(DicomTools.getTag(org, "0028,1050").trim());
//			int WW = Integer.parseInt(DicomTools.getTag(org, "0028,1051").trim());
//			if (WL == -1 || WW == -1) {
//				autoWindow();
//			} else {
//				changeWindow(WL, WW);
//			}
//		}
		setOriginalCalibration(originalCal);
	}

	private void initPrapInfoLabel() {
		if (pp == null) {
			return;
		}
		pp.updateInfoLabel(-1, -1, null, this.scale, getMagnification(), getRotateAngle());
	}

	private void initRoiSet() {
		roiset = null;
		roiset = new ArrayList<RoiObj>();
	}

	public void insertOrUpdateRoi4DB(RoiObj roi) {
		if(roi == null) {
			return;
		}
		//save as new or update
		if(DatabaseHandler.getInstance() != null) {
			DatabaseHandler.getInstance().insertRoi(roi.readContext());
		}
	}

	public void invert() {
		if (isInverted()) {
			setInvertState(false);
		} else {
			setInvertState(true);
		}
		imgProcess.invert(imageSpecimen.getDisplayImage());
		TextOverlayGlass tg = (TextOverlayGlass)getGlassAt(TEXT_LAYER);
		tg.setInvertState(this.invertFlag);
		repaint();
	}
	
	public boolean isWLWWChanged() {
		return windowing;
	}

	private boolean isExistsInRoiSet(RoiObj newRoi) {
		String studyUid = newRoi.getStudyUID();
		String seriesUid = newRoi.getSeriesUID();
		String sopUid = newRoi.getSopUID();
		String roiId = newRoi.getPropertyAt(RoiObj.RoiContextKeySet.RoiID.name());
		ArrayList<RoiObj> currentRoiSet = getRois();
		int size = currentRoiSet.size();
		for(int i =0;i<size;i++) {
			RoiObj r = currentRoiSet.get(i);
			if(r.isThisRoi(studyUid, seriesUid, sopUid, roiId)) {
				return true;
			}
		}
		return false;
	}

	public boolean isFlipped() {
		return flipFlag;
	}

	/**
	 * Whether have keybord event focus.
	 * 
	 * Not all code executes synchronously. Some code get added to the end of the
	 * Event Dispatch Thread (EDT). It appears that this is the case for focus
	 * requests. So when the if statements are executed, focus has not yet been
	 * placed on the component.
	 * 
	 * The solution is to wrap your code with a SwingUtilties.invokeLater() so the
	 * code gets added to the end of the EDT, so it can execute after the component
	 * has received focus:
	 * 
	 * coverGlass.requestFocusInWindow();
	 * 
	 * SwingUtilities.invokeLater(new Runnable(){ 
	 * 		public void run() {
	 * 		// First - Always returns false if(frame.getFocusOwner() instanceof JButton)
	 * { JButton focusedButton = (JButton) frame.getFocusOwner();
	 * focusedButton.doClick(); System.out.println("In focus?"); } else {
	 * System.out.println("Apparently not"); }
	 * 
	 * // Second - Also always returns false if(b2.isFocusOwner()) {
	 * System.out.println("In focus..."); } else { System.out.println("Not in
	 * focus"); } } });
	 * 
	 * Use state of mouseEntered instead.
	 * 
	 * @deprecated
	 * @return
	 */
	public boolean isFocusGained() {
		return coverGlass.isFocusOwner();
	}

	public boolean isHereRoiPopup(MouseEvent e) {
		Object obj = e.getSource();
		if(obj instanceof RoiPopupDialog) {
			return true;
		}else {
			return false;
		}
	}

	public boolean isInverted() {
		return invertFlag;
	}

	public boolean isRGB() {
		return isRGB;
	}

	boolean isRotated() {
		return rotatedFlag;
	}

	public boolean isSelected() {
		return selectedFlag;
	}

	public boolean isZoomed() {
		return zoomFlag;
	}

	public void loadRoiFromDB() {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {//if not starting from GRAPHY Launcher, return false.
			return;//for mpr view
		}
		String pid = getPatientID();
		String studyUid = getStudyInstanceUID();
		String seriesUid = getSeriesInstanceUID();
		String sopUid = getSOPInstanceUID();
		ArrayList<HashMap<String,Object>> cons = db.loadRoiContextFromInstance(pid, studyUid, seriesUid, sopUid);
		if(cons != null && cons.size() > 0) {
			for(int i=0; i<cons.size(); i++) {
				RoiObj roi = new RoiConverter().buildRoiObj(cons.get(i));
				if(roi == null) {
					continue;
				}
				roi.setSlideGlass(this);
				addRoi(roi);
//				if(isVisible()) {
//					repaint();
//				}
			}
		}
	}

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public int onDisplayImageX(int glassX) {
		if (!panningFlag) {
			return (int) ((glassX - imageSpecimen.getDisplayOriginX()) * getMagnification());
		} else {
			return (int) ((glassX - (imageSpecimen.getDisplayOriginX() * getScaleFactor())) * getMagnification());
		}
	}

	/**
	 * Converts a floating-point offscreen x-coordinate to a screen x-coordinate.
	 */
	public double onDisplayImageXD(double glassX) {
		if (!panningFlag) {
			return ((glassX - imageSpecimen.getDisplayOriginX()) * getMagnification());
		} else {
			return ((glassX - (imageSpecimen.getDisplayOriginX()* getScaleFactor())) * getMagnification());
		}
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public int onDisplayImageY(int glassY) {
		if (!panningFlag) {
			return (int) ((glassY - imageSpecimen.getDisplayOriginY()) * getMagnification());
		} else {
			return (int) ((glassY - (imageSpecimen.getDisplayOriginY() * getScaleFactor())) * getMagnification());
		}
	}

	/**
	 * Converts a floating-point offscreen x-coordinate to a screen x-coordinate.
	 */
	public double onDisplayImageYD(double glassY) {
		if (!panningFlag) {
			return ((glassY - imageSpecimen.getDisplayOriginY()) * getMagnification());
		} else {
			return ((glassY - (imageSpecimen.getDisplayOriginY() * getScaleFactor())) * getMagnification());
		}
	}

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public int onImageX(int glassX) {
		return onOriginalImageX(glassX);
	}

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public double onImageXD(int glassX) {
		return onOriginalImageXD(glassX);
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public int onImageY(int glassY) {
		return onOriginalImageY(glassY);
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public double onImageYD(int glassY) {
		return onOriginalImageYD(glassY);
	}

	/** Converts an screen x-coordinate to a original image x-coordinate. */
	public int onOriginalImageX(int glassX) {
		if (!panningFlag) {
			double backScale = (glassX - imageSpecimen.getDisplayOriginX()) / getMagnification() / getScaleFactor();
			return (int) backScale;
		} else {
			/*
			 * 見かけ上の原点に合うように原点位置に対してスケールは乗ずる
			 */
			double backScale = (glassX - (imageSpecimen.getDisplayOriginX() * getScaleFactor())) / getMagnification() / getScaleFactor();
			return (int) backScale;
		}
	}

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public double onOriginalImageXD(int glassX) {
		if (!panningFlag) {
			double backScale = (glassX - imageSpecimen.getDisplayOriginX()) / getMagnification() / getScaleFactor();
			return backScale;
		} else {
			double backScale = (glassX - (imageSpecimen.getDisplayOriginX() * getScaleFactor())) / getMagnification() / getScaleFactor();
			return backScale;
		}
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public int onOriginalImageY(int glassY) {
		if (!panningFlag) {
			double backScale = (glassY - imageSpecimen.getDisplayOriginY()) / getMagnification() / getScaleFactor();
			return (int) backScale;
		} else {
			double backScale = (glassY - (imageSpecimen.getDisplayOriginY() * getScaleFactor())) / getMagnification() / getScaleFactor();
			return (int) backScale;
		}
	}
	
	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public double onOriginalImageYD(int glassY) {
		if (!panningFlag) {
			double backScale = (glassY - imageSpecimen.getDisplayOriginY()) / getMagnification() / getScaleFactor();
			return backScale;
		} else {
			double backScale = (glassY - (imageSpecimen.getDisplayOriginY() * getScaleFactor())) / getMagnification() / getScaleFactor();
			return backScale;
		}
	}
	
	private int orgX2ScreenX(int orgImageX) {
		if (!panningFlag) {
			return (int) ((orgImageX * getMagnification() * getScaleFactor()) + imageSpecimen.getDisplayOriginX());
		} else {
			return (int) ((orgImageX * getMagnification() * getScaleFactor()) + (imageSpecimen.getDisplayOriginX()* getScaleFactor()));
		}
	}
	
	private double orgX2ScreenXD(double orgImageX) {
		if (!panningFlag) {
			return ((orgImageX * getMagnification() * getScaleFactor()) + imageSpecimen.getDisplayOriginX());
		} else {
			return ((orgImageX * getMagnification() * getScaleFactor()) + (imageSpecimen.getDisplayOriginX() * getScaleFactor()));
		}
	}

	private int orgY2ScreenY(int orgImageY) {
		if (!panningFlag) {
			return (int) ((orgImageY * getMagnification() * getScaleFactor()) + imageSpecimen.getDisplayOriginY());
		} else {
			return (int) ((orgImageY * getMagnification() * getScaleFactor()) + (imageSpecimen.getDisplayOriginY() * getScaleFactor()));
		}
	}

	public double orgY2ScreenYD(double orgImageY) {
		if (!panningFlag) {
			return ((orgImageY * getMagnification() * getScaleFactor()) + imageSpecimen.getDisplayOriginY());
		} else {
			return ((orgImageY * getMagnification() * getScaleFactor()) + (imageSpecimen.getDisplayOriginY() * getScaleFactor()));
		}
	}

	public ArrayList<RoiObj> getRoiSet() {
		return this.roiset;
	}
	
	/**
	 * SlideGlass reflect self.
	 */
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		imageSpecimen.repaint();
		textOverlay.repaint();
		roiOverlay.repaint();
		//coverGlass.repaint();
	}

	/**
	 * Panning is simply a movement of the display image origin position. Zoom is ignored.
	 * 
	 * moveX : Amount to be moved on X axis
	 * moveY : Amount to be moved on Y axis
	 */
	void panning(double moveX, double moveY) {
		panningFlag = true;
		imageSpecimen.originX = imageSpecimen.originX - (int) moveX;
		imageSpecimen.originY = imageSpecimen.originY - (int) moveY;
		logger.fine("Panning : originX " + imageSpecimen.originX + " ," + " originY " + imageSpecimen.originY);
		updatePanningState();
	}
	
//	void releasePanning() {
//		if (panningInAction) {
//			// https://stackoverflow.com/questions/2654839/rounding-a-double-to-turn-it-into-an-int-java
//			double reverseToNoScaleOriginX = imageSpecimen.originX / getScaleFactor();
//			double reverseToNoScaleOriginY = imageSpecimen.originY / getScaleFactor();
//			if (reverseToNoScaleOriginX >= 0) {
//				imageSpecimen.originX = (int) (reverseToNoScaleOriginX + 0.5);
//			} else {
//				imageSpecimen.originX = (int) (reverseToNoScaleOriginX - 0.5);
//			}
//			if (reverseToNoScaleOriginY >= 0) {
//				imageSpecimen.originY = (int) (reverseToNoScaleOriginY + 0.5);
//			} else {
//				imageSpecimen.originY = (int) (reverseToNoScaleOriginY - 0.5);
//			}
//			// update lastOrigin
//			lastOriginX = imageSpecimen.originX;
//			lastOriginY = imageSpecimen.originY;
//		}
//		this.panningInAction = false;
//		System.out.println("panning released, in action ? " + panningInAction);
//	}

	public void removeRoiPopupDialogOnCanvas(RoiPopupDialog rpd) {
		if (rpd != null && roiOverlay != null) {
			roiOverlay.remove(rpd);
			roiOverlay.revalidate();// no need, but maybe fail safe
			roiOverlay.repaint();
			repaint();
		}
	}
	
	public void replaceRoi(String beReplacedStudyUID, String beReplacedSeriesUID, String beReplacedSopUID, String beReplacedRoiId, RoiObj roiToReplace) {
		if(roiToReplace == null) {
			return;
		}
		String candidateRoiID = roiToReplace.getProperty(RoiObj.RoiContextKeySet.RoiID.name());
		if (roiset != null && roiset.size() > 0) {
			for (RoiObj roi : roiset) {
				if (roi.isThisRoi(beReplacedStudyUID, beReplacedSeriesUID, beReplacedSopUID, beReplacedRoiId)) {
					if(roiToReplace.getProperty(RoiObj.RoiContextKeySet.RoiID.name()).equals(candidateRoiID)) {
						updateRoi(beReplacedStudyUID, beReplacedSeriesUID, beReplacedSopUID, candidateRoiID, roiToReplace);
					}else {
						addRoi(roiToReplace);// then add !
						deleteRoi(roi);// delete first!
					}
					break;
				}
			}
		}
	}

	public void reset() {
		imageSpecimen.resetDisplayImage();
		setMagnification(1.0d);
		setRotateAngle(0);
		setFlipState(false);
		invertFlag = false;
		zoomFlag = false;
		windowing = false;
		rotatedFlag = false;
		panningFlag = false;
		initRoiSet();
		repaint();
	}

	/**
	 * This routine used to retrieve some other tag information from the dataset
	 */
//    private void retrieveTagInfo(Attributes dataset) {
//        try {
//            instanceUidList = Viewer2DScreen.getInstance().getDatabase().getInstanceUidList(dataset.getString(Tag.StudyInstanceUID), dataset.getString(Tag.SeriesInstanceUID));
//            totalInstance = !isMultiFrame() ? instanceUidList.size() : totalInstance;
//        } catch (NullPointerException e) {
//            Viewer2DScreen.logger().error("Image Panel", e);
//        }
//    }

//	public void showBorder(boolean show) {
//		if(show) {
//			Border border = BorderFactory.createLineBorder(Color.CYAN, 5);
//			textOverlay.setBorder(border);
//			textOverlay.repaint();
//		}else {
//			if(textOverlay != null) { //&& textOverlay.getBorder() != null) {
//				textOverlay.setBorder(null);
//				textOverlay.repaint();
//			}
//		}
//	}

	// future work
	private void retrieveScoutParam() {
//        currentScoutDetails = Viewer2DScreen.getInstance().getDatabase().getScoutLineDetails(dataset.getString(Tag.StudyInstanceUID), dataset.getString(Tag.SeriesInstanceUID), dataset.getString(Tag.SOPInstanceUID));
//        isLocalizer = (currentScoutDetails.getImageType().equalsIgnoreCase("LOCALIZER")) ? true : false;
//        findOrientation();
	}

	void rotate(int changeAngle) {
		double willRotateAngle = getRotateAngle() + changeAngle;
		if (willRotateAngle >= 360) {
			willRotateAngle = willRotateAngle - 360;
		} else if (willRotateAngle <= -360) {
			willRotateAngle = willRotateAngle + 360;
		}
		setRotateAngle((int) willRotateAngle);
		imageSpecimen.updateDisplayImageWithCurrentCondition();
		repaint();
		updatePrapInfoLabel(mouseX, mouseY);
	}
	
	public void saveCurrentRoiSate() {
		RoiObj roi = findCurrentRoi();
		insertOrUpdateRoi4DB(roi);
	}

	/*
	 * convert originale coordinate to glass coordinate.
	 */
	public int screenX(int imageX) {
		return orgX2ScreenX(imageX);
	}

	public double screenXD(double imageX) {
		return orgX2ScreenXD(imageX);
	}

	public int screenY(int imageY) {
		return orgY2ScreenY(imageY);
	}

	public double screenYD(double imageY) {
		return orgY2ScreenYD(imageY);
	}
	
	public void setAnnotationVisible(boolean v) {
		this.showAnnotation = v;
		if (this.showAnnotation) {
			// show annotation
			add(roiOverlay, ROI_CANVAS_LAYER, 0);
		} else {
			// do not show annotation
			remove(roiOverlay);
		}
		repaint();
	}

	public void setFlipState(boolean flip) {
		flipFlag = flip;
	}
	
	public void setDisplayImage(ImagePlus dispImg) {
		imageSpecimen.setDisplayImage(dispImg);
	}
	
	public void setFocusGained(boolean mouseEntered) {
		if(mouseEntered) {
			coverGlass.requestFocusInWindow();//enable key event
		}else {
			coverGlass.requestFocus(false);
		}
		if(pp.isShowGridViewOn()) {
			pp.setImagePositionTo(this);
		}
		showBorder(mouseEntered);
	}
	
	/**
	 * Adjust to ViewPanel full size.
	 * @param comp: ImageSpecimen, RoiOverlay, TextOverlay, MouseOverlay
	 * @param compW
	 * @param compH
	 */
	private void setGlassSize(JComponent comp, int compW, int compH) {
		comp.setSize(compW, compH);
		comp.setPreferredSize(new Dimension(compW, compH));
		comp.setBounds(0, 0, compW, compH);// MUST, set pane size and position.this is not image position
	}
	
	public void setInvertState(boolean invert) {
		invertFlag = invert;
	}
	
	public void setLUT(LUT lut) {
		if (invertFlag) {
			// do nothing
		} else {
			// do nothing
		}
		this.currentLUT = lut;
		imageSpecimen.getDisplayImage().setLut(currentLUT);
		imageSpecimen.getDisplayImage().updateImage();
		repaint();
	}
	
	public void setMagnification(double mag) {
		this.magnification = mag;
		if (mag == 1.0d) {
			zoomFlag = false;
		} else {
			zoomFlag = true;
		}
	}

	private void setOriginalCalibration(Calibration cal) {
		getOriginalImage().setCalibration(cal);
	}
	
	public void setRoiBrush(RoiObj brush) {
		roiOverlay.setBrush(brush);
	}
	
	public void setRotateAngle(int angle) {
		this.currentRotateAngle = angle;
		System.out.println("setRotateAngle:: " + angle);
		if (angle == 0) {
			rotatedFlag = false;
		} else {
			rotatedFlag = true;
		}
	}
	
//	public RoiPopupDialog isHereRoiPopup(int slideX, int slideY) {
//		Component[] comps = roiOverlay.getComponents();
//		for (Component com : comps) {
//			if (com != null && com != roiOverlay && com instanceof RoiPopupDialog) {
//				RoiPopupDialog candi_rpd = (RoiPopupDialog) com;
//				if (candi_rpd == null || !candi_rpd.roiAlive()) {
//					continue;
//				}
//				/*
//				 * SlideUIで発生したMouseEventでも、 RoiPopup上にあるとRoiPopup座標になる
//				 */
//				System.out.println(slideX + " " + slideY + " " + candi_rpd.getX() + " " + candi_rpd.getY());
//				int checkX = slideX + candi_rpd.getX();
//				int checkY = slideY + candi_rpd.getY();
//				// if com is JPanel subclass
////				int checkX = slideX - rpd.getX();
////				int checkY = slideY - rpd.getY();
////				boolean found = candi_rpd.contains(new Point(checkX,checkY));
//				// avoid conflict
//				/*
//				 * rpdのマウスイベントで位置を取得するとrpd座標になるため、 SlideGlass左上の位置を誤って検出してしまう。 これを避ける
//				 * RoiPopupDialog内では、sxsyは原点座標に戻る
//				 */
//				Component pretended_rpd = roiOverlay.getComponentAt(slideX, slideY);
//				Component target_rpd = roiOverlay.getComponentAt(checkX, checkY);
//				if (target_rpd == null) {
//					continue;
//				}
//				// in dialog or left upper corner
//				//true left upper dialog
//				if (candi_rpd.getX() == 0 && candi_rpd.getY() == 0) {
//					if ((pretended_rpd instanceof RoiPopupDialog) && target_rpd instanceof RoiPopupDialog) {
//						if (pretended_rpd == target_rpd) {
//							return (RoiPopupDialog) target_rpd;
//						}
//					}
//				}
//				if (!(pretended_rpd instanceof RoiPopupDialog) && target_rpd instanceof RoiPopupDialog) {
//					if(candi_rpd.getWidth() > candi_rpd.getX() && candi_rpd.getHeight() > candi_rpd.getY()) {
//						return null;
//					}else {
//						if (target_rpd != null && target_rpd != roiOverlay && target_rpd instanceof RoiPopupDialog) {
//							System.out.println("ROI POPUP FOUND !!!!");
//							return (RoiPopupDialog) target_rpd;
//						}
//					}
//					
//				}
//			}
//		}
//		return null;
//	}

	// mouse action
	public void setSelectionState() {
		if (isSelected()) {
			setSelectionState(false);
		} else {
			setSelectionState(true);
		}
	}

	// list selection action
	public void setSelectionState(boolean select) {
		this.selectedFlag = select;
		showBorder(true /*mouseEntered*/);
		repaint();
	}

	public void setTextVisible(boolean v) {
		this.showText = v;
		if (this.showText) {
			// show annotation
			add(textOverlay, TEXT_LAYER, 0);
		} else {
			// do not show annotation
			remove(textOverlay);
		}
		repaint();
	}

	private void setUpGlassLayer(DicomObject header) {
		imageSpecimen = new ImageSpecimenGlass(this);
		roiOverlay = new CanvasGlass(this);
		textOverlay = new TextOverlayGlass(this);
		coverGlass = new EventGlass();
		final int top_in_its_layers = 0;
		add(imageSpecimen, IMAGE_LAYER, top_in_its_layers);
		add(roiOverlay, ROI_CANVAS_LAYER, top_in_its_layers);
		add(textOverlay, TEXT_LAYER, top_in_its_layers);
		add(coverGlass, EVENT_LAYER, top_in_its_layers);
	}
	
	public void setVisibleRoiPopupAt(boolean show, int slideX, int slideY) {
		RoiPopupDialog rpd = getRoiPopupFromRoiAt(slideX, slideY);
		if (rpd == null) {
			return;
		} else {
			rpd.setVisible(show);
		}
	}
	
	public void setWindowingState(boolean windowing) {
		this.windowing = windowing;
	}
	
	public void showBorder(boolean mouseEntered) {
		Border b = BorderMaker.make(this, mouseEntered);
		setBorder(b);
	}
	
	public boolean isTextOvelayVisible() {
		return showText;
	}

	public void showRoiPopupOf(RoiObj roi) {
		if (roi == null) {
			return;
		}
		if (roi instanceof TextRoi) {
			return;
		}
		roi.showRoiPopupOnCanvas();
	}

	private void updatePanningState() {
		Dimension d = imageSpecimen.calcImageSize2FitComponent();
		if (d == null) {
			return;
		}
		Point defOrigin = imageSpecimen.calcDefaultImageOrigin(d.width, d.height);
		if ((imageSpecimen.originX == defOrigin.x) && (imageSpecimen.originY == defOrigin.y)) {
			panningFlag = false;
		} else {
			panningFlag = true;
		}
		repaint();
	}

	/*
	 * slideX: x on slideglass slideY: y on slideglass
	 */
	protected void updatePrapInfoLabel(int slideX, int slideY) {
		if (pp == null || pp.getViewMode()==ViewMode.Thumbnail) {
			return;
		}
		// get image origin
		Point currentOrigin = getDisplayImageLocationXY();
		// get image dimension
		Dimension currentDimension = getDisplayImageDimension();
		// Output pixel values when in dimension
		if (panningFlag) {
			int scaledOriginX = (int) (currentOrigin.x * getScaleFactor());
			int scaledOriginY = (int) (currentOrigin.y * getScaleFactor());
			if (scaledOriginX <= slideX && slideX < (scaledOriginX + currentDimension.width)) {
				if (scaledOriginY <= slideY && slideY < (scaledOriginY + currentDimension.height)) {
					int imageX = slideX - scaledOriginX;
					int imageY = slideY - scaledOriginY;
					pp.setAndShowPixelValue(imageX, imageY);
					logger.fine("scaledOriginXY:" + scaledOriginX + " " + scaledOriginY);
					logger.fine("slideXY:" + slideX + " " + slideY + " ,imageXY:" + imageX + " " + imageY);
				}
			}
		} else {
			if (currentOrigin.x <= slideX && slideX < (currentOrigin.x + currentDimension.width)) {
				if (currentOrigin.y <= slideY && slideY < (currentOrigin.y + currentDimension.height)) {
					int dispImageX = slideX - currentOrigin.x;
					int dispImageY = slideY - currentOrigin.y;
					pp.setAndShowPixelValue(dispImageX, dispImageY);
					logger.fine("originXY:" + currentOrigin.x + " " + currentOrigin.y);
					logger.fine("slideXY:" + slideX + " " + slideY + " ,imageXY:" + dispImageX + " " + dispImageY);
				}
			}
		}
		pp.repaint();
	}

	/**
	 * 
	 * @param StudyUID
	 * @param seriesUID
	 * @param sopUID
	 * @param roiInd
	 * @param updatedRoi : attached attributes should be same to original roi.
	 */
	public void updateRoi(String studyUID, String seriesUID, String sopUID, String roiInd, RoiObj updatedRoi) {
		if(updatedRoi == null) {
			return;
		}
		int ind = -1;
		if (roiset != null && roiset.size() > 0) {
			for(int i=0;i<roiset.size();i++) {
				if (roiset.get(i).isThisRoi(studyUID, seriesUID, sopUID, roiInd)) {
					ind = i;
					break;
				}
			}
			if(ind != -1) {
				roiset.set(ind, updatedRoi);
				insertOrUpdateRoi4DB(updatedRoi);// saveRoi to db
			}
		}
	}

	public void updateRoiCanvas() {
		roiOverlay.repaint();// show roi
	}

	/*
	 * called when component resized.
	 */
	public void updateScale() {
		if (pp == null) {
			return;
		}
		/*
		 * if component not visible, this will return 0.
		 * To avoid this situation, should do setSize(w,h) before do it.
		 */
		if (getWidth() < 1 || getHeight() < 1) {
			return;
		}
		Dimension d = imageSpecimen.calcImageSize2FitComponent();
		if (d != null) {
			if(header != null) {
				this.scale = (double) d.width / (double) header.getInt(Tag.Columns, getOriginalImage().getWidth());
			}else {
				this.scale = (double) d.width / (double)getOriginalImage().getWidth();
			}
		}
	}

	/**
	 * 
	 * @param magnification : zoom scale(0.2<, <7.0)
	 */
	void zoom(double mag, boolean zoomUp) {
		// set magnification min max
		if (mag < 0.1) {
			mag = 0.1;
			logger.info("Zoom: magnification is too small, keep 0.1.");
		} else if (mag > 30) {
			mag = 30.0;
			logger.info("Zoom: magnification is too large, not up to 30.");
		}
		setMagnification(mag);
		if(mag != 1.0 && !panningFlag) {
    		panningFlag = true;//because, image origin shifted by focuse zoom.
    	}
		//update origin
		if (zoomUp) {
			imageSpecimen.originX = (int) ((imageSpecimen.originX - mouseX) * (mag / (mag - .1)) + mouseX);
			imageSpecimen.originY = (int) ((imageSpecimen.originY - mouseY) * (mag / (mag - .1)) + mouseY);
		} else {
			imageSpecimen.originX = (int) ((imageSpecimen.originX - mouseX) * (mag / (mag + .1)) + mouseX);
			imageSpecimen.originY = (int) ((imageSpecimen.originY - mouseY) * (mag / (mag + .1)) + mouseY);
		}
		imageSpecimen.updateDisplayImageWithCurrentCondition();
		repaint();
    	updatePrapInfoLabel(mouseX, mouseY);
	}
}
