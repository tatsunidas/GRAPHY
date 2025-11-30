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

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.border.Border;

import com.vis.configuration.ContextKey;
import com.vis.core.log.Log;
import com.vis.core.util.ByteUtils;
import com.vis.core.util.MathUtils;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.processing.ImageProcessing;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiPopUpDialog;
import com.vis.core.view.D2.roi.RoiType;
import com.vis.core.view.D2.roi.TextRoi;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.mpr.ReferenceLineMPR;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.GDicomTools;

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

	// glasses
	public ImageSpecimenGlass imageSpecimen;
	private TextOverlayGlass textOverlay;
	private CanvasGlass roiOverlay;
	private EventGlass coverGlass;/* KeyListener */

	// flags
	// private boolean focusFlag = false;
	private boolean selectedFlag = false;
	public boolean panningFlag = false;
	public boolean rotatedFlag = false, flipHorizontalFlag = false, flipVerticalFlag = false, zoomFlag = false;
	private boolean invertFlag = false;
	boolean windowing = false;// WW/WL changed
	private boolean showAnnotation = true;
	private boolean showText = true;
	boolean isPDF = false;
	boolean isGrayscale = false;
	boolean isRGB = false;
	//private int mouseActionFlag = -1; // MouseAction ModifierEx.

	// ww/wl settings
//	private Calibration originalCal = null;
	protected double currentMin = 0;// current window contrast min
	protected double currentMax = 255;// current window contrast max
	protected double lastMin = -1;// see,ImageLayerUI::processMouseEvent
	protected double lastMax = -1;// see,ImageLayerUI::processMouseEvent
	// rotate
	public int currentRotateAngle = 0;
	public int lastRotateAngle = 0;
	
	/**
	 * Size of fit to component is 1.0(100%).
	 * No original size.
	 */
	private double magnification = 1.0d;// zoom ratio 1 to N

	// mouse settings
	/*
	 * Coordinates on SlideGlass(same as viewPanel coordinates). These are not the
	 * coordinate system of the original image, but the coordinates on the
	 * SlideGlass fitted to the current ViewPanel.
	 */
	public int lastDraggedX = 0;//on slide
	public int lastDraggedY = 0;//on slide
	
	public int mouseX = 0;// current mouse loc on slideglass
	public int mouseY = 0;// current mouse loc on slideglass
	
	public int lastPressedX = 0;
	public int lastPressedY = 0;
	
	LUT currentLUT;// null-able, if null set grayscale

	/**
	 * Ratio of display dimension (display zone on ViewPanel without zoom&pann) to
	 * original image size. 
	 * 
	 * The imageSpecimen.calcImageSize2FitComponent() method makes scaleX and scaleY have the same value.
	 * 
	 * scaleX = display dimension width (no zoom & pann) /original image width.
	 * scaleY = display dimension height (no zoom & pann) /original image height.
	 */
	private double scaleX = 1.0d; // (fit to comp size)/(original)
	private double scaleY = 1.0d; // (fit to comp size)/(original)

	public int INTERPOLATION_METHOD = ImageProcessor.NEAREST_NEIGHBOR;
	ImageProcessing imgProcess = new ImageProcessing();
	Logger logger = Log.logger;

	public SlideGlass(Praparat pp, DicomImage dcmImg/* single frame */) {
		if (pp == null || dcmImg == null) {
			throw new NullPointerException();
		}
		initComponents(pp, dcmImg);
	}

	public void addRoi(RoiObj roi) {
		roiOverlay.addRoi(roi);
	}

	void adjustContrastFromMouseAction(int dragX, int dragY) {
		int xDiff = dragX - lastDraggedX;
		int yDiff = dragY - lastDraggedY;
//		double minMaxDifference = getCurrentDisplayImagePlus().getDisplayRangeMax()
//				- getCurrentDisplayImagePlus().getDisplayRangeMin();
//		int totalWidth = pp.getImageScreenSizeX();
//		int totalHeight = pp.getImageScreenSizeY();
//		double xRatio = ((double) xDiff) / ((double) totalWidth);
//		double yRatio = ((double) yDiff) / ((double) totalHeight);
//		// scale to our image range
//		double xScaledValue = minMaxDifference * xRatio;
//		double yScaledValue = minMaxDifference * yRatio;
//		// to avoid rangeMin > rangeMax
//		if (Math.abs(xScaledValue) > minMaxDifference) {
//			if (xScaledValue < 0) {
//				xScaledValue = -1 * minMaxDifference;
//			} else {
//				xScaledValue = minMaxDifference;
//			}
//		}
//		if (Math.abs(yScaledValue) > minMaxDifference) {
//			if (yScaledValue < 0) {
//				yScaledValue = -1 * minMaxDifference;
//			} else {
//				yScaledValue = minMaxDifference;
//			}
//		}
		// invert x
//				 xScaledValue = xScaledValue * -1;
//		adjustWindowLevel(xScaledValue, yScaledValue);
		adjustWindowLevel(xDiff, yDiff);
		lastDraggedX = dragX;
		lastDraggedY = dragY;
	}

	void adjustWindow2Current() {
		if (currentMax == -1 || currentMin == -1) {
			return;
		}
		setWindowingState(true);
		changeWindowingByMinMax(this.currentMin, this.currentMax);
	}

	void adjustWindowLevel(double xDifference, double yDifference) {
		setWindowingState(true);
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
		if (getOriginalImage() == null) {
			return;
		}
		if (isRGB()) {
			getOriginalImage().getProcessor().reset();
		}
		lastMin = currentMin; 
		lastMax = currentMax;
		new ContrastEnhancer().stretchHistogram(getOriginalImage().getProcessor(), 0.5);
		this.currentMin = getOriginalImage().getProcessor().getMin();// DO NOT USE getMinThreshold()
		this.currentMax = getOriginalImage().getProcessor().getMax();// DO NOT USE getMaxThreshold()
		changeWindowingByMinMax(this.currentMin, this.currentMax);
	}

	public void changeWindowing(int WL, int WW) {
		double newMin = WL - (.5 * WW);
		double newMax = WL + (.5 * WW);
		if (newMin >= newMax) {
			logger.log(Level.WARNING, "SlideGlass::changeWindow() problem occured: min value larger than or equals max; min " + newMin + " max " + newMax);
			return;
		}
		lastMin = currentMin; 
		lastMax = currentMax;
		currentMin = newMin;
		currentMax = newMax;
		if (Utils.isDebug)
			logger.info("change ww/wl : newMin " + newMin + " newMax " + newMax);
		changeWindowingByMinMax(this.currentMin, this.currentMax);
	}
	
	public void changeWindowingByMinMax(double newMin, double newMax) {
		if (newMin > newMax) {
			logger.log(Level.WARNING, "SlideGlass::changeWindow() problem occured: min value larger than max; min " + newMin + " max " + newMax);
			return;
		}
		lastMin = currentMin;
		lastMax = currentMax; 
		currentMin = newMin;
		currentMax = newMax;
		if (Utils.isDebug)
			logger.info("change ww/wl : newMin " + newMin + " newMax " + newMax);
		imageSpecimen.updateDisplayImage();
	}

	/**
	 * see, ImageSpecimen
	 * 
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
		return GDicomTools.dcmImgToImagePlus(getDicomImage());
	}

	public ImagePlus cropRect() {
		RoiObj roi = roiOverlay.findCurrentRoi();
		RoiType type = roi.getRoiType();
		if (type != RoiType.RECTANGLE && type != RoiType.OVAL && type != RoiType.POLYGON) {
			return null;
		}
		Rectangle2D rect = roi.getBounds();
//		Roi r = new RoiConverter().convert2Roi(roi);
		Roi r = new Roi(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
		ImagePlus orgImp = getOriginalImage();
		ImageProcessor ip = orgImp.getProcessor().duplicate();
		ip.setRoi(r);
		ip = ip.crop();
		ImagePlus cropImp = getOriginalImage().createImagePlus();
		cropImp.setProcessor(ip);
		return cropImp;
	}

	public void deleteRoi(RoiObj roi) {
		roiOverlay.deleteRoi(roi);
	}

	/**
	 * null-able. if set to null, stop displaying localizer.
	 * 
	 * @param localizer
	 */
	public void drawLocalizer(java.util.List<java.awt.geom.Point2D> localizerGeo) {
		roiOverlay.setLocalizerGeometry(localizerGeo);
		repaintCanvasGlass();
	}

	/**
	 * Flip by X axis
	 */
	public void flipHF() {
		if (flipVerticalFlag) {
			setVerticalFlipState(false);
		} else {
			setVerticalFlipState(true);
		}
		repaint();//update image specimen
	}

	/**
	 * Flip by Y axis
	 */
	public void flipLR() {
		if (flipHorizontalFlag) {
			setHorizontalFlipState(false);
		} else {
			setHorizontalFlipState(true);
		}
		repaint();//update image specimen
	}

	public RoiObj getActiveRoi() {
		return roiOverlay.getActiveRoi();
	}
	
	/*
	 * mouse position on slide glass XY location
	 */
	public Point getCursorLoc() {
		Point pointOnViewPanel = new Point(mouseX, mouseY);
		return pointOnViewPanel;
	}
	
	public double[] getCurrentWindowMinMax() {
		return new double[] {currentMin, currentMax};
	}

	public DicomImage getDicomImage() {
		return this.dcmImg;
	}

	public Dimension getDisplayImageDimension() {
		double scaleComp = getScaleFactor()[0];
		double zoomFactor = getMagnification();
		Dimension defaultDim = this.imageSpecimen.calcImageSize2FitComponent();
		return new Dimension((int)(defaultDim.width*scaleComp*zoomFactor), (int)(defaultDim.height*scaleComp*zoomFactor));
	}

	/*
	 * current display image origin
	 */
	public Point getDisplayImageOriginXY() {
		Point origin = new Point(imageSpecimen.getDisplayOriginX(), imageSpecimen.getDisplayOriginY());
		return origin;
	}

	public double getPixelSpacingX() {
		return getOriginalCalibration().pixelWidth;
	}

	public double getPixelSpacingY() {
		return getOriginalCalibration().pixelHeight;
	}

	public double getPixelSpacingZ() {
		return getOriginalCalibration().pixelDepth;
	}

	public Object getGlassAt(int layer_type) {
		if (layer_type == IMAGE_LAYER) {
			return imageSpecimen;
		} else if (layer_type == ROI_CANVAS_LAYER) {
			return roiOverlay;
		} else if (layer_type == TEXT_LAYER) {
			return textOverlay;
		} else if (layer_type == EVENT_LAYER) {
			return coverGlass;
		} else {
			return null;
		}
	}

	public DicomObject getHeader() {
		return header;
	}

	public Integer getInstanceNo() {
		return header != null ? header.getInt(Tag.Instance​Number, -1) : null;
	}

	public double getMagnification() {
		return this.magnification;
	}

	public String getModality() {
		return header != null ? header.getString(Tag.Modality, "UNKNOWN") : null;
	}

	public Calibration getOriginalCalibration() {
		return getOriginalImage().getCalibration();
	}

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
	
	public Object[] getPixelValueFromOriginal(int orgImageX, int orgImageY) {
		if(orgImageX < 0 || orgImageX > imageSpecimen.orgCols-1) {
			return null;
		}
		if(orgImageY < 0 || orgImageY > imageSpecimen.orgRows-1) {
			return null;
		}
		if (!isRGB()) {
			double pix_raw = getOriginalImage().getProcessor().get(orgImageX, orgImageY);
			double pix_cal = getOriginalImage().getProcessor().getPixelValue(orgImageX, orgImageY);
			if (dcmImg.getBitsAllocated() == 32) {
				pix_raw = Float.intBitsToFloat((int)pix_raw);
			}
			return new Double[] { pix_raw, pix_cal };
		} else {
			ColorProcessor cp = (ColorProcessor) getOriginalImage().getProcessor();
			int[] rgb = cp.getPixel(orgImageX, orgImageY, null);
			return new String[] { String.valueOf(rgb[0]), String.valueOf(rgb[1]), String.valueOf(rgb[2])};
		}
	}

	public Praparat getPraparat() {
		return this.pp;
	}

	/**
	 * If MPR viewtype with RESLICE mode, return ReferenceLine.
	 * @return
	 */
	public ReferenceLineMPR getReferenceLineMPR() {
		if (pp != null) {
			return pp.getReferenceLineMPR();
		} else {
			return null;
		}
	}

	public int getRotateAngle() {
		return currentRotateAngle;
	}

	public ArrayList<RoiObj> getRois() {
		return roiOverlay.getRoiSet();
	}

	/**
	 * 
	 * @param x slideX
	 * @param y slideY
	 * @return
	 */
	public RoiPopUpDialog getRoiPopUpAt(int x, int y) {
		return roiOverlay.getRoiPopupAt(x, y);
	}

	/**
	 * 
	 * @param x slideX
	 * @param y slideY
	 * @return
	 */
	public RoiObj getRoiLocationAt(int x, int y) {
		return roiOverlay.getRoiLoacationAt(x, y);
	}

	/**
	 * Ratio of display dimension (display zone on ViewPanel without zoom pann) to
	 * original image size. scale = display dimension width (no zoom & pann) /
	 * original image width.
	 */
	public double[] getScaleFactor() {
		updateScale();
		return new double[] { scaleX, scaleY };
	}

	public String getSeriesInstanceUID() {
		return header != null ? header.getString(Tag.Series​Instance​UID, "NO_SeriesInstanceUID") : null;
	}

	public String getSOPInstanceUID() {
		return header != null ? header.getString(Tag.SOP​Instance​UID, "NO_SOPInstanceUID") : null;
	}

	public String getStudyInstanceUID() {
		return header != null ? header.getString(Tag.Study​Instance​UID, "NO_StudyInstanceUID") : null;
	}

	public String getUID(int tag) {
		return header != null ? header.getString(tag) : null;
	}
	
	public String[] getUIDs() {
		String[] uids = new String[4];
		uids[0] = header != null ? header.getString(Tag.Patient​ID, "NO_PatientID") : null;
		uids[1] = header != null ? header.getString(Tag.Study​Instance​UID, "NO_StudyInstanceUID") : null;
		uids[2] = header != null ? header.getString(Tag.Series​Instance​UID, "NO_SeriesInstanceUID") : null;
		uids[3] = header != null ? header.getString(Tag.SOP​Instance​UID, "NO_SOPInstanceUID") : null;
		return uids;
	}
	
	public void initComponents(Praparat pp, DicomImage dcmImg/* single frame */) {
		this.pp = pp;
//		this.roiset = new ArrayList<RoiObj>();
		this.dcmImg = dcmImg;
		this.header = dcmImg.getCore();
		setBorder(BorderMaker.make(this, false));
		setOpaque(false);
		setUpGlassLayer(header);
		initImageInfo(header);
		setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		SlideGlassMouseListener sgml = new SlideGlassMouseListener(this);
		coverGlass.addMouseListener(sgml);
		coverGlass.addMouseMotionListener(sgml);
		coverGlass.addMouseWheelListener(sgml);
		coverGlass.addKeyListener(new SlideGlassKeyListener(this));
		if(pp.getViewMode() != ViewMode.Thumbnail) {
			loadRoiFromDB();
		}
		imageSpecimen.updateDisplayImage();
	}

	/**
	 * Calibrate original image
	 * 
	 * @param dataset
	 */
	private void initImageInfo(DicomObject dataset) {
		if (this.header == null) {
			this.header = dataset;
		}
		/* No calibrated imageplus */
		ImagePlus org = getOriginalImage();
		Calibration originalCal = org.getCalibration();
		/*
		 * TODO load lut from dicom tag ? todo, see dicomwriter as reference.
		 */
		setLUT(org.getProcessor().getLut());
		isRGB = org.getType() == ImagePlus.COLOR_RGB;// choice suitable one.
		if (isRGB()) {
			org.getProcessor().snapshot();
		}

		/*
		 * Spatial calibrations
		 */
		// x-y-z
		double pixelSpacingX = 1.0;
		double pixelSpacingY = 1.0;
		double pixelSpacingZ = 1.0;
		boolean pixelSpacingFound = false;
		// Pixel Spacing = Row Spacing [PY] \ Column Spacing [PX] = 0.30\0.25.
		double[] pixelSpacing = dataset.getDoubles(Tag.Pixel​Spacing);
		if (pixelSpacing != null && pixelSpacing != ByteUtils.EMPTY_DOUBLES) {
			pixelSpacingX = pixelSpacing[1];// column
			pixelSpacingY = pixelSpacing[0];// row
			pixelSpacingFound = true;
		}
		pixelSpacingZ = GDicomTools.getVoxelDepth(dataset);
		if(pixelSpacingFound) {
			/*
			 * Units is mm, that is dicom default. see, Pixel Spacing Attribute (0028,0030)
			 * definition.
			 */
			originalCal.setUnit("mm");
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
				//need more test...
				// y = a + bx
				double[] coeff = new double[2];// [a,b]
				coeff[0] = intercept - 32768*slope;
				coeff[1] = slope;
				originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
				// add another modalities unit...
			} else {
				originalCal.setSigned16BitCalibration();
			}
			if (modality != null && modality.equals("CT")) {
				originalCal.setValueUnit("HU");
			}
		} else if (!intercept.isNaN() && !slope.isNaN()) {
			double[] coeff = new double[2];
			coeff[0] = intercept;
			coeff[1] = slope;
			originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
			//originalCal.getCTable();// to make cTable.
		}
		// adjust WW/WL
		resetWindowing();
		setOriginalCalibration(originalCal);
	}

	private void initPrapInfoLabel() {
		if (pp == null) {
			return;
		}
		if(imageSpecimen == null) {
			return;
		}
		
		pp.setAndShowPixelValue(0, 0);
	}

	public void invert() {
		if (isInverted()) {
			setInvertState(false);
		} else {
			setInvertState(true);
		}
		TextOverlayGlass tg = (TextOverlayGlass) getGlassAt(TEXT_LAYER);
		tg.setInvertState(this.invertFlag);
		repaint();
	}

	public boolean isFlipped() {
		return flipHorizontalFlag || flipVerticalFlag;
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
	 * SwingUtilities.invokeLater(new Runnable(){ public void run() { // First -
	 * Always returns false if(frame.getFocusOwner() instanceof JButton) { JButton
	 * focusedButton = (JButton) frame.getFocusOwner(); focusedButton.doClick();
	 * System.out.println("In focus?"); } else { System.out.println("Apparently
	 * not"); }
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
		return roiOverlay.isHereRoiPopup(e);
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

	public boolean isTextOvelayVisible() {
		return showText;
	}

	public boolean isWLWWChanged() {
		return windowing;
	}

	public boolean isZoomed() {
		return zoomFlag;
	}

	public void loadRoiFromDB() {
		roiOverlay.loadRoiFromDB();
	}
	
	/**
	 * 
	 * @param glassX SlideGlassX (screenX)
	 * @param glassY SlideGlassY (screenY)
	 * @return
	 * @throws NoninvertibleTransformException
	 */
	public Point offScreenCoordinate(double glassX, double glassY) throws NoninvertibleTransformException {

		/* 1. 順変換に必要なパラメータの取得 */
		// スケールとズームの合成倍率
		double scaleToFit = getScaleFactor()[0];
		double zoomFactor = getMagnification();
		double s = scaleToFit * zoomFactor;
		double sx = flipVerticalFlag ? -s : s;//Head-Foot X axis flip
       double sy = flipHorizontalFlag ? -s : s;//LR Y axis flip
		// 回転角度（度数法をラジアンに変換）
		double rotateAngleInDegrees = getRotateAngle();
		double thetaInRadians = Math.toRadians(rotateAngleInDegrees);

		Dimension offScreen = getOriginalImageSize(); // OffScreen image size
		double offCenterX = offScreen.width / 2.0;
		double offCenterY = offScreen.height / 2.0;

		// パンニングオフセット（パネル座標）
		Point dispOrigin = getDisplayImageOriginXY();

		/* 順変換行列 (OffScreen -> Panel) */
		java.awt.geom.AffineTransform forwardAt = new AffineTransform();
		forwardAt.translate(dispOrigin.x, dispOrigin.y);
		forwardAt.scale(sx, sy);
		forwardAt.translate(offCenterX, offCenterY);
		forwardAt.rotate(thetaInRadians);
		// 回転の中心をもとに戻す(パネル座標系)
		forwardAt.translate(-offCenterX, -offCenterY);
		
		/* 3. 逆変換の実行 (Panel -> OffScreen) */
		try {
			// 逆行列を取得
			AffineTransform inverseAt = forwardAt.createInverse();
			// JPanel上の座標 (入力値)
			Point2D.Double panelPoint = new Point2D.Double(glassX, glassY);
			// OffScreen座標 (出力先)
			Point2D.Double offScreenPoint = new Point2D.Double();
			// 逆行列を適用して座標を変換
			inverseAt.transform(panelPoint, offScreenPoint);
			// 結果をPoint型で返す
			return new Point((int) Math.round(offScreenPoint.getX()), (int) Math.round(offScreenPoint.getY()));
		} catch (java.awt.geom.NoninvertibleTransformException e) {
			// 変換行列が非可逆の場合の処理（通常は発生しないはず）
			e.printStackTrace();
			throw e;
		}
	}
	
	/**
	 * Calculate display image origin in current condition.
	 * @return
	 */
	public Point slideglassCoordinateFromOffScreen(double offScreenX, double offScreenY) {
		
		double scaleToFit = getScaleFactor()[0];
		double zoomFactor = getMagnification();
		double s = scaleToFit * zoomFactor;
		double sx = flipVerticalFlag ? -s : s;// Head-Foot X axis flip
		double sy = flipHorizontalFlag ? -s : s;// LR Y axis flip
		
		// 回転角度（度数法をラジアンに変換）
		double rotateAngleInDegrees = getRotateAngle();
		double thetaInRadians = Math.toRadians(rotateAngleInDegrees);

		Dimension offScreen = getOriginalImageSize(); // OffScreen image size
		double offCenterX = offScreen.width / 2.0;
		double offCenterY = offScreen.height / 2.0;

		// パンニングオフセット（パネル座標）
		Point dispOrigin = getDisplayImageOriginXY();

		/* 順変換行列 (OffScreen -> Panel) */
		java.awt.geom.AffineTransform forwardAt = new AffineTransform();
		forwardAt.translate(dispOrigin.x, dispOrigin.y);
		forwardAt.scale(sx, sy);
		forwardAt.translate(offCenterX, offCenterY);
		forwardAt.rotate(thetaInRadians);
		forwardAt.translate(-offCenterX, -offCenterY);
		
		// OffScreen origin
		Point2D.Double offOrigin = new Point2D.Double(offScreenX, offScreenY);
		// Display Image Coordinates
		Point2D.Double newOrigin = new Point2D.Double();
		forwardAt.transform(offOrigin, newOrigin);
		
		imageSpecimen.originX = (int) Math.round(newOrigin.getX());
		imageSpecimen.originY = (int) Math.round(newOrigin.getY());
		
		Log.logger.fine("New display image origin after ROTATE:"+imageSpecimen.originX+" and "+imageSpecimen.originY);
		
		return new Point((int) Math.round(newOrigin.getX()), (int) Math.round(newOrigin.getY()));
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		/*
		 * SlideGlass.repaint() update all layered pane components. However, the
		 * following repainting is required in order for the ROI to be displayed
		 * smoothly. As a point to note, the rendering of these excessive graphics can
		 * cause Canvas3D in the 3D Viewer to fail to start.
		 */
		if (!getIgnoreRepaint()) {
//			imageSpecimen.repaint();
//			textOverlay.repaint();
			roiOverlay.repaint();
		}
	}

	/**
	 * Panning is simply a movement of the display image origin position. 
	 * 
	 * @param moveX: screen coordinate
	 * @param moveY: screen coordinate
	 */
	void panning(double moveX, double moveY) {
		panningFlag = true;
		imageSpecimen.updateOrigin(imageSpecimen.originX - (int) moveX, imageSpecimen.originY - (int) moveY);
		logger.fine("Panning : originX " + imageSpecimen.originX + " ," + " originY " + imageSpecimen.originY);
		updatePanningState();
	}
	
	public void repaintCanvasGlass() {
		roiOverlay.repaint();
	}
	
	public void repaintImageGlass() {
		imageSpecimen.repaint();
	}
	
	public void replaceRoi(HashMap<ContextKey, String> uids, RoiObj roiToReplace) {
		String patID = uids.get(ContextKey.PatientID);
		String studyUID = uids.get(ContextKey.StudyInstanceUID);
		String seriesUID = uids.get(ContextKey.SeriesInstanceUID);
		String sopUID = uids.get(ContextKey.SOPInstanceUID);
		String roiID = uids.get(ContextKey.RoiID);
		roiOverlay.replaceRoi(patID, studyUID, seriesUID, sopUID, roiID, roiToReplace);
	}

	public void replaceRoi(String patID, String beReplacedStudyUID, String beReplacedSeriesUID, String beReplacedSopUID,
			String beReplacedRoiId, RoiObj roiToReplace) {
		roiOverlay.replaceRoi(patID, beReplacedStudyUID, beReplacedSeriesUID, beReplacedSopUID, beReplacedRoiId, roiToReplace);
	}

	public void reset() {
		setMagnification(1.0d);
		setRotateAngle(0);
		setHorizontalFlipState(false);
		setVerticalFlipState(false);
		invertFlag = false;
		zoomFlag = false;
		windowing = false;
		rotatedFlag = false;
		panningFlag = false;
		roiOverlay.reset();
		imageSpecimen.resetImageOrigin();
		imageSpecimen.updateDisplayImage();
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

	void rotate(int changeAngle) {
		double willRotateAngle = getRotateAngle() + changeAngle;
		setRotateAngle((int) willRotateAngle);
		imageSpecimen.updateDisplayImage();
		updatePrapInfoLabel(mouseX, mouseY);
	}

	public void saveCurrentRoiSate() {
		roiOverlay.saveCurrentRoiSate();
	}
	
	public void saveRoi(RoiObj roi) {
		roiOverlay.insertOrUpdateRoi4DB(roi);
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
	
	public void setHorizontalFlipState(boolean flip) {
		flipHorizontalFlag = flip;
	}
	
	public void setVerticalFlipState(boolean flip) {
		flipVerticalFlag = flip;
	}

	public void setFocusGained(boolean mouseEntered) {
		if (mouseEntered) {
			coverGlass.requestFocusInWindow();// enable key event
		} else {
			coverGlass.requestFocus(false);
		}
		if (pp.isShowGridViewOn()) {
			pp.setImagePositionTo(this);
		}
		showBorder(mouseEntered);
	}

	/**
	 * Adjust to ViewPanel full size.
	 * 
	 * DO NOT USE SwingUtilities.invokeLater.
	 * 
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
	
	/**
	 * Set/Replace to new ImagePlus.
	 * Header is remaining.
	 */
	public void setImage(ImagePlus imp) {
		imageSpecimen.setImage(imp);
	}

	public void setLUT(LUT lut) {
		if (invertFlag) {
			// do nothing
		} else {
			// do nothing
		}
		this.currentLUT = lut;
		repaint();
	}

	private void setMagnification(double mag) {
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
		if (angle % 360 == 0) {
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
		showBorder(true /* mouseEntered */);
		repaint();
	}

	/**
	 * set slideglass size and update image specimen
	 */
	@Override
	public void setSize(int compW, int compH) {
		/*
		 * keep default bounds of SlideGlass-self.
		 */
		super.setSize(compW, compH);// for updateScale()
		super.setPreferredSize(new Dimension(compW, compH));
		setGlassSize(imageSpecimen, compW, compH);
		setGlassSize(textOverlay, compW, compH);
		setGlassSize(roiOverlay, compW, compH);
		setGlassSize(coverGlass, compW, compH);
		updateScale();
		initPrapInfoLabel();
		//finally
		imageSpecimen.updateDisplayImage();
		repaint();//repaint all glasses.
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
		coverGlass = new EventGlass(this);
		final int top_in_its_layers = 0;
		add(imageSpecimen, IMAGE_LAYER, top_in_its_layers);
		add(roiOverlay, ROI_CANVAS_LAYER, top_in_its_layers);
		add(textOverlay, TEXT_LAYER, top_in_its_layers);
		add(coverGlass, EVENT_LAYER, top_in_its_layers);
	}
	
	/*
	 * TODO
	 */
	public void setVisibleRoiPopupAt(boolean show, int slideX, int slideY) {
//		RoiPopUpDialog rpd = roiOverlay.getRoiPopupFromRoiAt(slideX, slideY);
//		if (rpd == null) {
//			return;
//		} else {
//			rpd.setVisible(show);
//		}
	}

	public void setWindowingState(boolean windowing) {
		this.windowing = windowing;
	}

	public void showBorder(boolean mouseEntered) {
		Border b = BorderMaker.make(this, mouseEntered);
		setBorder(b);
	}

	public void showRoiPopupOf(RoiObj roi) {
		if (roi == null) {
			return;
		}
		if (roi instanceof TextRoi) {
			return;
		}
//		roi.showRoiPopupOnCanvas();//TODO
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
		imageSpecimen.updateDisplayImage();
	}

	/*
	 * slideX: x on slideglass slideY: y on slideglass
	 */
	public void updatePrapInfoLabel(int slideX, int slideY) {
		if (pp == null || pp.getViewMode() == ViewMode.Thumbnail) {
			return;
		}
		pp.setAndShowPixelValue(slideX, slideY);
	}

	public void updateRoi(RoiObj roi) {
		roiOverlay.updateRoi(roi);// show roi
	}

	/**
	 * repaint CanvasOverlay
	 */
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
		 * if component not visible, this will return 0. To avoid this situation, should
		 * do setSize(w,h) before do this.
		 */
		if (getWidth() < 1 || getHeight() < 1) {
			return;
		}
		Dimension d = imageSpecimen.calcImageSize2FitComponent();
		if (d != null) {
			if (header != null) {
				this.scaleX = (double) d.width / (double) header.getInt(Tag.Columns, getOriginalImage().getWidth());
				this.scaleY = (double) d.height / (double) header.getInt(Tag.Rows, getOriginalImage().getHeight());
			} else {
				this.scaleX = (double) d.width / (double) getOriginalImage().getWidth();
				this.scaleY = (double) d.height / (double) getOriginalImage().getHeight();
			}
		}
	}

	/**
	 * 
	 * @param mag
	 * @param zoomUp
	 */
	void zoom(double mag, boolean zoomUp) {
		
		double currentMag = MathUtils.truncateToDecimalPlace(getMagnification(), 3);
		mag = MathUtils.truncateToDecimalPlace(mag, 3);
		
		if(currentMag == mag) {
			return;
		}
		
		// set magnification min max
		if (mag < 0.1) {
			mag = 0.1;
			logger.info("Zoom: magnification is too small, keep 0.1.");
		} else if (mag > 30) {
			mag = 30.0;
			logger.info("Zoom: magnification is too large, not up to 30.");
		}
		
		//update magnification
		setMagnification(mag);
		
		Dimension dispImageSize = getDisplayImageDimension();
		
		int w = dispImageSize.width;
		int h = dispImageSize.height;
		
		/*
		 * w and h are current "display" image size which already scaled by the mag factor.
		 * Here, correct size to previous size with previous mag factor, then, re-zoom current mag, and subtract prev - current.
		 */
		int shiftX = (int)((w/currentMag*mag - w)/2);
		int shiftY = (int)((h/currentMag*mag - h)/2);
		
		if (mag != 1.0) {
			panningFlag = true;// because, image origin shifted by zoom.
		}
		// update origin
		imageSpecimen.updateOrigin(imageSpecimen.originX-shiftX, imageSpecimen.originY-shiftY);
		
		Log.logger.fine("Origin changed by ZOOM: (x) "+imageSpecimen.originX +", (y) "+imageSpecimen.originY);
		
		imageSpecimen.updateDisplayImage();
		updatePrapInfoLabel(mouseX, mouseY);
	}
}
