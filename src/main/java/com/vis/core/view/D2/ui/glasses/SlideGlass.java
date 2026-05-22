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
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.border.Border;

import org.joml.Vector3d;

import com.vis.configuration.ContextKey;
import com.vis.core.log.Log;
import com.vis.core.slicer.ReferenceLineMPR;
import com.vis.core.util.MathUtils;
import com.vis.core.view.D2.processing.ImageProcessing;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiPopUpDialog;
import com.vis.core.view.D2.roi.RoiType;
import com.vis.core.view.D2.roi.TextRoi;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.orientation.ImageOrientation;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.core.view.D2.ui.orientation.SubjectOrientation;
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
	
	private static final String UNIT_MM = "mm";
	private static final String UNIT_HU = "HU";
	private static final String UNIT_GRAY = "Gray Value";

	private Praparat pp;// series viewer
	private DicomObject header;//from dicom image
	private DicomImage dcmImg;

	// glasses
	public ImageSpecimenGlass imageSpecimen;
	private TextOverlayGlass textOverlay;
	private CanvasGlass roiOverlay;
	private EventGlass coverGlass;/* KeyListener */
	
	//transform
	AffineTransform currentTransform;

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
	
	final boolean isBiped;
	final double[] display_iop = new double[6];

	// ww/wl settings
	protected double currentMin = 0;// current window contrast min
	protected double currentMax = 255;// current window contrast max
	protected double lastMin = -1;
	protected double lastMax = -1;
	double startChangeContrastWW = -1;//mousePressed
	double startChangeContrastWL = -1;//mousePressed
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
	
	public int lastPressedX = 0;//SlideGlass coordinate
	public int lastPressedY = 0;//SlideGlass coordinate
	
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
	
	// ★ 変更：RoiObjのまま保存せず、DB保存用の「HashMap（値の集合）」に変換してスタックに積む！
	private java.util.Deque<java.util.List<java.util.HashMap<String, Object>>> undoStack = new java.util.ArrayDeque<>();
	private java.util.Deque<java.util.List<java.util.HashMap<String, Object>>> redoStack = new java.util.ArrayDeque<>();
	private static final int MAX_UNDO_LIMIT = 20; // upper size of snapshots
	private boolean isRestoring = false;
	
	//fusion ghost start timer
	private int ghostProgressAngle = 0; // 0 〜 360
    private java.awt.Point ghostProgressLocation = null;

	public SlideGlass(Praparat pp, DicomImage dcmImg/* single frame */) {
		if (pp == null || dcmImg == null) {
			throw new NullPointerException();
		}
		isRGB = dcmImg.isColor();
		isBiped = SubjectOrientation.isBiped(dcmImg.getHeader());
		initComponents(pp, dcmImg);
	}

	public void addRoi(RoiObj roi) {
//		roi.setSlideGlass(this);//DO NOT set here. Should set before this.
		roiOverlay.addRoi(roi);
	}
	
	void adjustContrastFromMouseAction(int dragX, int dragY) {
		// 1. 開始位置からの「総移動距離」を計算
		int xDiff = dragX - lastPressedX;
		int yDiff = dragY - lastPressedY;

		// 2. 画面サイズに対する移動割合
		int totalWidth = getWidth();
		int totalHeight = getHeight();
		
		// ゼロ除算対策
		if (totalWidth == 0 || totalHeight == 0) return;

		// 感度調整。もし動きが遅いと感じたら 2.0 などに上げてください
		double sensitivity = 1.0; 
		
		// ドラッグ中に変動する値ではなく、クリック時の固定されたWindow幅を基準にする
		double dynamicRange = startChangeContrastWW;
		
		// 安全対策：もし何らかの理由で初期Window幅が狭すぎる（または0以下）場合は、
		// マウスが動かなくなるのを防ぐために最低限の倍率を保証する
		if (dynamicRange < 1.0) {
			dynamicRange = 256.0; 
		}

		// 3. 移動量に応じた変化量を計算
		double windowChange = (xDiff / (double) totalWidth) * dynamicRange * sensitivity;
		double levelChange = -1 * (yDiff / (double) totalHeight) * dynamicRange * sensitivity;

		// 4. 新しい値を計算 (開始時の値 + 変化量)
		double newWindow = startChangeContrastWW + windowChange;
		double newLevel = startChangeContrastWL + levelChange;

		// Window幅が1未満や負にならないようにガード
		if (newWindow < 1.0) {
			newWindow = 1.0;
		}
		
		// 5. 適用
		changeWindowingByWWWL(newLevel, newWindow);
	}

	/**
	 * see also ImageUtils.autoContrast()
	 */
	public void autoWindowing() {
		ImagePlus org = getOriginalImage();
		if (org == null) {
			return;
		}
		synchronized(org) {
			ImageProcessor ip = org.getProcessor();
			if(ip == null) {
				return;
			}
			if (isRGB()) {
				ip.reset();
			}
			lastMin = currentMin; 
			lastMax = currentMax;
			new ContrastEnhancer().stretchHistogram(ip, 0.5);
			this.currentMin = ip.getMin();// DO NOT USE getMinThreshold()
			this.currentMax = ip.getMax();// DO NOT USE getMaxThreshold()
			changeWindowingByMinMax(this.currentMin, this.currentMax);
		}
	}

	/**
	 * 
	 * @param WL : calibrated real value
	 * @param WW : calibrated real value range
	 */
	public void changeWindowingByWWWL(double WL, double WW) {
		double newMin = WL - (.5 * WW);
		double newMax = WL + (.5 * WW);
		if (newMin >= newMax) {
			logger.log(Level.WARNING, "SlideGlass::changeWindow() problem occured: min value larger than or equals max; min " + newMin + " max " + newMax);
			return;
		}
		changeWindowingByMinMax(newMin, newMax);
	}
	
	void changeWindowingByMinMax(double newMin, double newMax) {
		if (newMin > newMax) {
			logger.log(Level.WARNING, "SlideGlass::changeWindow() problem occured: min value larger than max; min " + newMin + " max " + newMax);
			return;
		}
		lastMin = currentMin;
		lastMax = currentMax; 
		currentMin = newMin;
		currentMax = newMax;
		//logger.fine("change ww/wl : newMin " + newMin + " newMax " + newMax);
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
		return GDicomTools.dcmImgToImagePlus(getDicomImage(), getOriginalCalibration());
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

	public boolean deleteRoi(RoiObj roi) {
		return roiOverlay.deleteRoi(roi);
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
		imageSpecimen.updateDisplayImage();
		updateOrientation();
		repaint();
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
		imageSpecimen.updateDisplayImage();
		updateOrientation();
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
		double zoomFactor = getMagnification();
		Dimension defaultDim = this.imageSpecimen.calcImageSize2FitComponent();
		return new Dimension((int)(defaultDim.width*zoomFactor), (int)(defaultDim.height*zoomFactor));
	}

	/*
	 * current display image origin (offscreen coordinate)
	 */
	public Point getDisplayImageOriginXY() {
		Point origin = new Point(imageSpecimen.getDisplayOriginX(), imageSpecimen.getDisplayOriginY());
		return origin;
	}

	public double getPixelSpacingX() {
		if(getOriginalCalibration() == null) {
			return 1.;
		}
		return getOriginalCalibration().pixelWidth;
	}

	public double getPixelSpacingY() {
		if(getOriginalCalibration() == null) {
			return 1.;
		}
		return getOriginalCalibration().pixelHeight;
	}

	public double getPixelSpacingZ() {
		if(getOriginalCalibration() == null) {
			return 1.;
		}
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
		if(getOriginalImage() !=null) {
			return getOriginalImage().getCalibration();
		}else {
			return imageSpecimen.getOriginalCalibration();
		}
	}

	public ImagePlus getOriginalImage() {
		return this.imageSpecimen.getOriginalImage();
	}
	
	/**
	 * 
	 * @return 8 bit or rgb buffered image.
	 */
	public BufferedImage getBufferedImage() {
		return this.imageSpecimen.getOriginalImage().getBufferedImage();
	}

	public Dimension getOriginalImageSize() {
		int w = header.getInt(Tag.Columns, 0);
		int h = header.getInt(Tag.Rows, 0);
		return new Dimension(w,h);
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
		if(getOriginalCalibration() == null) {
			return UNIT_GRAY;
		}
		return getOriginalCalibration().getUnit();
	}
	
	public Object[] getPixelValueFromOriginal(int orgImageX, int orgImageY) {
		if(orgImageX < 0 || orgImageX > imageSpecimen.orgCols-1) {
			return null;
		}
		if(orgImageY < 0 || orgImageY > imageSpecimen.orgRows-1) {
			return null;
		}
		
		ImagePlus org = getOriginalImage();
		
		if(org == null) {
			return null;
		}
		
		synchronized(org) {
			if (!isRGB()) {
//				System.out.println(Arrays.toString(org.getCalibration().getCoefficients()));
				double pix_raw = org.getProcessor().get(orgImageX, orgImageY);
				double pix_cal = org.getProcessor().getPixelValue(orgImageX, orgImageY);
				if (dcmImg.getBitsAllocated() == 32) {
					pix_raw = Float.intBitsToFloat((int)pix_raw);
				}
				return new Double[] { pix_raw, pix_cal };
			} else {
				ColorProcessor cp = (ColorProcessor) org.getProcessor();
				int[] rgb = cp.getPixel(orgImageX, orgImageY, null);
				return new String[] { String.valueOf(rgb[0]), String.valueOf(rgb[1]), String.valueOf(rgb[2])};
			}
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
	public RoiObj getRoiLocationAt(int sx, int sy) {
		return roiOverlay.getRoiLoacationAt(sx, sy);
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
	
	public String getStudyDate() {
	    String rawDate = (header != null) ? header.getString(Tag.Study​Date) : null;
	    // 値が取得できない、またはDICOM標準の8桁に満たない場合のガード
	    if (rawDate == null || rawDate.length() < 8) {
	        return "0000/00/00"; // または "NO_DATE" など
	    }
	    try {
	        // DICOM形式 (yyyyMMdd) を LocalDate にパース
	        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
	        LocalDate date = LocalDate.parse(rawDate.substring(0, 8), inputFormatter);

	        // yyyy/MM/dd 形式に変換
	        return date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
	    } catch (Exception e) {
	        // パースエラー（不正な日付文字列など）の場合
	        return "0000/00/00";
	    }
	}
	
	public void initComponents(Praparat pp, DicomImage dcmImg/* single frame */) {
		this.pp = pp;
//		this.roiset = new ArrayList<RoiObj>();
		this.dcmImg = dcmImg;
		this.header = dcmImg.getHeader();
		setBorder(BorderMaker.make(this, false));
		setOpaque(false);
		setUpGlassLayer(header);
		initCalibrationAndLUT(header);
		setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		SlideGlassMouseListener sgml = new SlideGlassMouseListener(this);
		coverGlass.addMouseListener(sgml);
		coverGlass.addMouseMotionListener(sgml);
		coverGlass.addMouseWheelListener(sgml);
		coverGlass.addKeyListener(new SlideGlassKeyListener(this));
		imageSpecimen.updateDisplayImage();
	}
	
	public void initCalibrationAndLUT() {
		initCalibrationAndLUT(header);
	}

	/**
	 * Calibrate original image
	 * 
	 * @param header
	 */
	private void initCalibrationAndLUT(DicomObject header) {
		
		Calibration originalCal = new Calibration();
		
		if(currentLUT == null && !isRGB){
			setLUT(extractDisplayLUT(header));
		}		
		
		setupSpatialCalibration(originalCal, header);
		if(!isRGB) {
			setupDensityCalibration(originalCal, header);
		}
		
		setOriginalCalibration(originalCal);
		// adjust WW/WL
		if (this.currentMin != 0 && this.currentMax != 255 && !isRGB) {
			changeWindowingByMinMax(currentMin, currentMax);
		}else {
			//here, do nothing. delegate global auto contrast.
//			autoWindowing();
		}
	}
	
	private LUT extractDisplayLUT(DicomObject header) {
		// Red, Green, Blue の Descriptor を取得 [エントリー数, 最初のエントリー値, ビット数]
		int[] rDesc = header.getInts(Tag.Red​Palette​Color​Lookup​Table​Descriptor);
		int[] gDesc = header.getInts(Tag.Green​Palette​Color​Lookup​Table​Descriptor);
		int[] bDesc = header.getInts(Tag.Blue​Palette​Color​Lookup​Table​Descriptor);

		if (rDesc == null || gDesc == null || bDesc == null)
			return null;

		// LUT Data を取得
		try {
			byte[] rData = header.getBytes(Tag.Red​Palette​Color​Lookup​Table​Data);
			byte[] gData = header.getBytes(Tag.Green​Palette​Color​Lookup​Table​Data);
			byte[] bData = header.getBytes(Tag.Blue​Palette​Color​Lookup​Table​Data);

			if (rData == null || gData == null || bData == null)
				return null;

			// ImageJの標準的な256色LUTにマッピングする場合（表示用）
			// DICOMのLUTは通常 16-bit (65536エントリー) のことが多いですが、
			// 多くの表示系では 8-bit (256段階) にリサンプルして使用します。
			byte[] r = new byte[256];
			byte[] g = new byte[256];
			byte[] b = new byte[256];

			// DICOM LUTから256段階をサンプリング
			// (DICOMデータが16bit値で格納されている場合は上位バイトを取得)
			int step = Math.max(1, (rData.length / 2) / 256);
			for (int i = 0; i < 256; i++) {
				int idx = i * step * 2; // byte[] なので 2倍
				if (idx < rData.length) {
					// DICOMのLUTデータはLittle Endianの16bitであることが多いため上位バイトを使用
					r[i] = rData[idx + 1];
					g[i] = gData[idx + 1];
					b[i] = bData[idx + 1];
				}
			}

			return new LUT(r, g, b);

		} catch (IOException ioe) {
			System.out.println(ioe);
			logger.log(Level.WARNING, "LUT loading failed...");
			return null;
		}
	}
	
	/**
	 * ピクセル間隔（Pixel Spacing / Voxel Depth）を設定します。
	 */
	private void setupSpatialCalibration(Calibration cal, DicomObject header) {
	    double[] spacing = header.getDoubles(Tag.Pixel​Spacing);
	    if (spacing != null && spacing.length >= 2) {
	        // DICOM: [0]=Row Spacing(Y), [1]=Column Spacing(X)
	        cal.pixelWidth = spacing[1];
	        cal.pixelHeight = spacing[0];
	        cal.setUnit(UNIT_MM);
	    } else {
	        cal.pixelWidth = 1.0;
	        cal.pixelHeight = 1.0;
	    }
	    cal.pixelDepth = GDicomTools.getVoxelDepth(header);
	}
	
	
	/**
	 * Rescale Slope/Intercept
	 * ImageJのShortProcessorは符号なし(0-65535)としてデータを扱う.
	 */
	private void setupDensityCalibration(Calibration cal, DicomObject header) {

		double slope = header.getDouble(Tag.Rescale​Slope, 1.0);
		double intercept = header.getDouble(Tag.Rescale​Intercept, 0.0);

		boolean isSigned = dcmImg.isSigned();
		int bitsAllocated = header.getInt(Tag.Bits​Allocated, 8);
		String modality = getModality();

		if (isSigned) {
			if (bitsAllocated == 8) {
				if (!Double.isNaN(slope) && !Double.isNaN(intercept)) {
					// ImageJ内部で 0~255 となっている値を -128~127 にマッピング
					double[] coeff = { intercept - (128.0 * slope), slope };
					cal.setFunction(Calibration.STRAIGHT_LINE, coeff, UNIT_GRAY);
				} else {
					double[] coeff = new double[2];
					coeff[0] = -128.0;
					coeff[1] = 1.0;
					cal.setFunction(Calibration.STRAIGHT_LINE, coeff, "Gray Value");
				}
			} else if (bitsAllocated == 16) {
				if (!Double.isNaN(slope) && !Double.isNaN(intercept)) {
					// ImageJ内部で 0~65535 となっている値を -32768~32767 にマッピングしつつ Slope/Intercept を適用
					// y=slope⋅(x−32768)+intercept
					// これを一次関数 y=a+bx の形に整理すると：
					// 係数 a (Intercept部): intercept−(32768⋅slope)
					// 係数 b (Slope部): slope
					// y = slope * (x - 32768) + intercept
					// y = (intercept - 32768 * slope) + (slope * x)
					double[] coeff = { intercept - (32768.0 * slope), slope };
					cal.setFunction(Calibration.STRAIGHT_LINE, coeff, UNIT_GRAY);
				} else {
					//intercept=-32768
					//slope=1
					cal.setSigned16BitCalibration();
				}
			}
		} // その他のデータの Rescale Slope/Intercept 適用
		else if (!Double.isNaN(slope) && !Double.isNaN(intercept)) {
			double[] coeff = { intercept, slope };
			cal.setFunction(Calibration.STRAIGHT_LINE, coeff, UNIT_GRAY);
		}

		// モダリティがCTの場合は単位をHU（ハンスフィールド・ユニット）に設定
		if ("CT".equals(modality)) {
			cal.setValueUnit(UNIT_HU);
		}
	}

	private void initPrapInfoLabel() {
		if (pp == null || pp.getViewMode() == ViewMode.Thumbnail) {
			return;
		}
		if(imageSpecimen == null) {
			return;
		}
		pp.setAndShowPixelValue(this, 0, 0);
	}

	public void invert() {
		if (isInverted()) {
			setInvertState(false);
		} else {
			setInvertState(true);
		}
		TextOverlayGlass tg = (TextOverlayGlass) getGlassAt(TEXT_LAYER);
		tg.setInvertState(this.invertFlag);
		imageSpecimen.updateDisplayImage();
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
	
	AffineTransform calculateCurrentAffineTransform() {
		double scaleToFit = getScaleFactor()[0]; // 画面に合わせる初期縮小率
		double zoomFactor = getMagnification();  // ユーザーのズーム倍率 (1.0 = 100%)
		
		// 総合倍率
		double s = scaleToFit * zoomFactor;
		double sx = flipHorizontalFlag ? -s : s;
		double sy = flipVerticalFlag ? -s : s;

		double thetaInRadians = Math.toRadians(getRotateAngle());
		Dimension offScreen = getOriginalImageSize(); // 元画像サイズ

		// 1. 等倍表示（zoom=1.0）の時、画面上に見えているべき画像の「見かけのサイズ」
		Dimension defaultDim = imageSpecimen.calcImageSize2FitComponent();
		double fitW = (defaultDim != null) ? defaultDim.width : offScreen.width * scaleToFit;
		double fitH = (defaultDim != null) ? defaultDim.height : offScreen.height * scaleToFit;

		// 2. ズームや回転の「中心軸」を、画面上の絶対座標として計算
		// 基本配置位置（originX/Y）に、フィットサイズの中央分を足す
		double visualCenterX = imageSpecimen.originX + (fitW / 2.0);
		double visualCenterY = imageSpecimen.originY + (fitH / 2.0);

		currentTransform = new AffineTransform();

		// 【重要：行列の組み立て順序（逆順に適用されます）】
		
		// Step 4: 画面上の回転・拡大中心軸（visualCenter）へ持っていく
		currentTransform.translate(visualCenterX, visualCenterY);

		// Step 3: その中心軸を基準に、回転とズーム（拡大縮小）を適用
		currentTransform.rotate(thetaInRadians);
		currentTransform.scale(zoomFactor, zoomFactor); // ユーザーのズーム倍率を掛ける

		// Step 2: 初期フィット倍率（scaleToFit）と反転を適用
		currentTransform.scale(sx / s, sy / s); // 純粋なscaleToFitと反転成分のみを抽出
		currentTransform.scale(scaleToFit, scaleToFit);

		// Step 1: 元画像の中心を原点 (0,0) に合わせる
		currentTransform.translate(-offScreen.width / 2.0, -offScreen.height / 2.0);

		return currentTransform;
	}
	
	/**
	 * Calculate current transform and update it.
	 * @return
	 */
	public AffineTransform getCurrentTransform() {
		if(currentTransform == null) {
			return calculateCurrentAffineTransform();
		}
		return currentTransform;
	}
	
	/**
	 * 
	 * @param glassX SlideGlassX (screenX)
	 * @param glassY SlideGlassY (screenY)
	 * @return
	 * @throws NoninvertibleTransformException
	 */
	public Point offScreenCoordinate(double glassX, double glassY) throws NoninvertibleTransformException {

		AffineTransform at = getCurrentTransform();
		/* 逆変換の実行 (Panel -> OffScreen) */
		try {
			// 逆行列を取得
			AffineTransform inverseAt = at.createInverse();
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
	 * Calculate slide glass coordinate in current condition.
	 * @return
	 */
	public Point slideglassCoordinateFromOffScreen(double offScreenX, double offScreenY) {
		
		AffineTransform at = getCurrentTransform();
		
		// OffScreen origin
		Point2D.Double offOrigin = new Point2D.Double(offScreenX, offScreenY);
		// Display Image Coordinates
		Point2D.Double newOrigin = new Point2D.Double();
		at.transform(offOrigin, newOrigin);
		
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
		TextOverlayGlass tg = (TextOverlayGlass) getGlassAt(TEXT_LAYER);
		tg.setInvertState(this.invertFlag);
		repaint();//show rois
	}

//	public void resetContrast() {
//		// adjust WW/WL
//		Double wl = header.getDouble(Tag.Window​Center, Double.NaN);
//		Double ww = header.getDouble(Tag.Window​Width, Double.NaN);
//		if (!Double.isFinite(wl) || !Double.isFinite(ww)) {
//			autoWindowing();
//			return;
//		}
//		/*
//		 * * 2. オフセットの計算 ImageJのShortProcessorはUnsigned 16-bit(0-65535)として扱うため、
//		 * Signedデータを読み込む際に足し合わせたオフセットを引いて、 WL/WWの基準をImageJのピクセル値に合わせる必要があります。
//		 */
//		double offset = 0;
//		boolean isSigned = dcmImg.isSigned();
//		int bitsAllocated = dcmImg.getBitsAllocated();
//
//		if (isSigned) {
//			if (bitsAllocated == 16) {
//				// Signed 16-bitの場合、通常中心を32768シフトさせている
//				offset = 32768.0;
//			} else if (bitsAllocated == 8) {
//				// Signed 8-bitの場合（稀）
//				offset = 128.0;
//			}
//		}
//
//		/*
//		 * 3. Rescale Slope/Intercept の考慮 DICOMのWL/WWは「Rescale適用後の値（HUなど）」で定義されています。
//		 * ImageProcessor.setMinAndMax() は「生のピクセル値」に対して行う必要があるため、
//		 * 逆計算をして生のピクセル値ベースのMin/Maxを求めます。
//		 */
//		double slope = header.getDouble(Tag.Rescale​Slope, 1.0);
//		double intercept = header.getDouble(Tag.Rescale​Intercept, 0.0);
//
//		// 表示範囲の最小・最大を計算 (物理単位)
//		double minPhys = wl - (ww / 2.0);
//		double maxPhys = wl + (ww / 2.0);
//
//		// 生のピクセル値 (ImageJの内部値) に逆変換
//		// 物理値 = (raw - offset) * slope + intercept
//		// => raw = ((物理値 - intercept) / slope) + offset
//		double rawMin = ((minPhys - intercept) / slope) + offset;
//		double rawMax = ((maxPhys - intercept) / slope) + offset;
//		changeWindowingByMinMax(rawMin, rawMax);
//	}
	
	/**
	 * 12 bit対応バージョン 
	 */
	public void resetContrast() {
		// 1. Window Center / Width の取得と有限性チェック
		Double wl = header.getDouble(Tag.Window​Center, Double.NaN);
		Double ww = header.getDouble(Tag.Window​Width, Double.NaN);
		if (!Double.isFinite(wl) || !Double.isFinite(ww)) {
			autoWindowing();
			return;
		}

		// 不正データ対策：Window Width が 1 未満の場合は強制的に 1 にする（DICOM規格の防衛）
		if (ww < 1.0) {
			ww = 1.0;
		}

		/*
		 * 2. 動的なオフセットの計算
		 * BitsAllocated ではなく、実際にデータが格納されている BitsStored を基準にシフト量を計算します。
		 * (ImageJの標準DICOMプラグインの符号なし化ロジックと完全に同期させます)
		 */
		double offset = 0;
		boolean isSigned = dcmImg.isSigned();
		int bitsAllocated = dcmImg.getBitsAllocated();
		// BitsStored（格納ビット数：12や16など）を取得。無ければAllocatedで代用
		int bitsStored = header.getInt(Tag.Bits​Stored, bitsAllocated);

		if (isSigned) {
			// 例: 16bit Allocated であっても、12bit Stored なら (1 << 11) = 2048.0 になる
			offset = (double) (1 << (bitsStored - 1));
		}

		/*
		 * 3. Rescale Slope/Intercept の考慮
		 * DICOMのWL/WW（物理空間）から、ImageJ内部の生のピクセル値へと逆算します。
		 */
		double slope = header.getDouble(Tag.Rescale​Slope, 1.0);
		double intercept = header.getDouble(Tag.Rescale​Intercept, 0.0);

		// 表示範囲の最小・最大を計算 (物理単位)
		double minPhys = wl - (ww / 2.0);
		double maxPhys = wl + (ww / 2.0);

		// 生のピクセル値 (ImageJの内部値) に逆変換
		double rawMin = ((minPhys - intercept) / slope) + offset;
		double rawMax = ((maxPhys - intercept) / slope) + offset;

		// ★★★ 修正ポイント: ビット深度に応じた値の範囲内への安全クランピング ★★★
		double maxPossibleValue = (double) ((1 << bitsAllocated) - 1); // 16bitなら65535.0、8bitなら255.0
		
		rawMin = Math.max(0.0, Math.min(maxPossibleValue, rawMin));
		rawMax = Math.max(0.0, Math.min(maxPossibleValue, rawMax));

		// 万が一Slopeが負の画像などでMin/Maxが逆転した場合の最終保険
		if (rawMin > rawMax) {
			double tmp = rawMin;
			rawMin = rawMax;
			rawMax = tmp;
		}

		changeWindowingByMinMax(rawMin, rawMax);
	}

	void rotate(double changeAngle) {
		if(changeAngle == 0) {
			return;
		}
		double willRotateAngle = getRotateAngle() + changeAngle;
		setRotateAngle((int) willRotateAngle);
		imageSpecimen.updateDisplayImage();
		updatePrapInfoLabel(mouseX, mouseY);
		updateOrientation();
	}
	
	void updateOrientation() {

		double rotateAngleInDegrees = getRotateAngle();
		double thetaInRadians = Math.toRadians(rotateAngleInDegrees);

		Vector3d baseRow = ImageOrientation.getRowDirection(dcmImg.getHeader());
		Vector3d baseCol = ImageOrientation.getColumnDirection(dcmImg.getHeader());
		
		//DX video
		if(baseRow == null || baseCol == null) {
			return;
		}
		
		if (baseRow.length() < 1e-6 || baseCol.length() < 1e-6) {
			Log.logger.log(Level.WARNING, "ImagePositionPatient is NULL, cannot calculate Orientations.");
			return;
		}
		
		double flipX = flipHorizontalFlag ? -1.0 : 1.0;
       double flipY = flipVerticalFlag ? -1.0 : 1.0;
       
       Vector3d workingRow = new Vector3d(baseRow).mul(flipX);
       Vector3d workingCol = new Vector3d(baseCol).mul(flipY);

		// 2. 回転計算 (線形結合)
		double cos = Math.cos(thetaInRadians);
		double sin = Math.sin(thetaInRadians);
		
		// NewRow = workingRow * cos - workingCol * sin
		Vector3d newRow = new Vector3d(workingRow).mul(cos)
                .sub(new Vector3d(workingCol).mul(sin));

		// NewCol = workingRow * sin + workingCol * cos
		Vector3d newCol = new Vector3d(workingRow).mul(sin)
		                .add(new Vector3d(workingCol).mul(cos));

		// 3. 直交性と長さの正規化を保証する
		PlanarSupport.normalizeAndOrthogonalize(newRow, newCol);

		// 4. 結果をdisplay_iopに格納
		display_iop[0] = newRow.x;
		display_iop[1] = newRow.y;
		display_iop[2] = newRow.z;
		display_iop[3] = newCol.x;
		display_iop[4] = newCol.y;
		display_iop[5] = newCol.z;

		textOverlay.updateDisplayDirection(display_iop, isBiped);

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
		imageSpecimen.replaceImage(imp);
	}

	public void setLUT(LUT lut) {
		this.currentLUT = lut;
		if(imageSpecimen != null) {
			imageSpecimen.updateDisplayImage();
		}
	}
	
	public void setDisplayOrigin(Point p) {
		if(imageSpecimen == null) {
			return;
		}
		
		// ======= ログ追加 =======
//	    System.out.println("[DEBUG-ZOOM] setDisplayOrigin called. Input P: " + p + 
//	                       " | Current Size: " + getWidth() + "x" + getHeight() + 
//	                       " | Current panningFlag: " + this.panningFlag);
	    // ======================
		
		// ★ 修正1: コンポーネントがまだ画面に配置されておらずサイズが確定していない（先読み状態など）場合は、
		// 異常な座標計算や panningFlag の誤汚染を防ぐため、単純に座標をセットするだけで処理を抜ける
		if (getWidth() <= 0 || getHeight() <= 0) {
			if (p != null) {
				imageSpecimen.updateOrigin(p.x, p.y);
			}
			return;
		}

		// ★ 修正2: p == null のときは、画面上のサイズ（getWidth）ではなく、画像の見かけのサイズ（defaultDim）を渡す
		if (p == null) {
			Dimension defaultDim = imageSpecimen.calcImageSize2FitComponent();
			if (defaultDim != null) {
				p = imageSpecimen.calcDefaultImageOrigin(defaultDim.width, defaultDim.height);
			} else {
				p = new Point(0, 0);
			}
		}
		
		imageSpecimen.updateOrigin(p.x, p.y);
		
		// 1. 現在の倍率を取得
		double mag = getMagnification();
		
		// 2. 倍率1.0（等倍）における本来のデフォルト中央位置を計算
		Dimension defaultDim = imageSpecimen.calcImageSize2FitComponent();
		boolean isDefaultPosition = false;
		
		if (defaultDim != null) {
			Point defaultOrigin = imageSpecimen.calcDefaultImageOrigin(defaultDim.width, defaultDim.height);
			// 渡された位置がデフォルト位置と完全に一致するか判定
			if (p.x == defaultOrigin.x && p.y == defaultOrigin.y) {
				isDefaultPosition = true;
			}
		}
		
		// 3. Zoomが1.0、かつ位置もデフォルト中央ならパン状態を解除
		if (mag == 1.0 && isDefaultPosition) {
			this.panningFlag = false;
		} else {
			this.panningFlag = true;
		}
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
		imageSpecimen.setOriginalCalibration(cal);
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
	
	public void setAbsoluteRotate(double absoluteAngle) {
		setRotateAngle((int) Math.round(absoluteAngle));
		imageSpecimen.updateDisplayImage();
		updatePrapInfoLabel(mouseX, mouseY);
		updateOrientation();
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
		// 幅や高さが0の場合は計算をスキップ（初期化時のバグ防止）
		if (compW <= 0 || compH <= 0) {
			super.setSize(compW, compH);
			return;
		}

		super.setSize(compW, compH);
		super.setPreferredSize(new Dimension(compW, compH));
		setGlassSize(imageSpecimen, compW, compH);
		setGlassSize(textOverlay, compW, compH);
		setGlassSize(roiOverlay, compW, compH);
		setGlassSize(coverGlass, compW, compH);
		
		// 1. スケール（初期フィット縮小率）を更新
		updateScale();
		
		if(!panningFlag) {
			Dimension defaultDim = imageSpecimen.calcImageSize2FitComponent();
			if (defaultDim != null && defaultDim.width > 0 && defaultDim.height > 0) {
				// フィット表示（100%）における、正確な中央マージン（左上座標）を計算
				int defX = (compW - defaultDim.width) / 2;
				int defY = (compH - defaultDim.height) / 2;
				imageSpecimen.updateOrigin(defX, defY);
			}
		}else {
			Dimension defaultDim = imageSpecimen.calcImageSize2FitComponent();
			// フィット表示（100%）における、正確な中央マージン（左上座標）を計算
			int defX = (compW - defaultDim.width) / 2;
			int defY = (compH - defaultDim.height) / 2;
			int sx = imageSpecimen.getDisplayOriginX();
			int sy = imageSpecimen.getDisplayOriginY();
			// 誤差レベルで中央に戻っていた場合はパンフラグを安全に落とす
			if(defX == sx && defY == sy && Math.abs(getMagnification() - 1.0) < 1e-3) {
			    panningFlag = false;
			}
		}
		
		// ======= ログ追加 =======
//	    System.out.println("[DEBUG-ZOOM] setSize finished. Size: " + compW + "x" + compH + 
//	                       " | panningFlag: " + panningFlag + 
//	                       " | Final Origin: " + imageSpecimen.getDisplayOriginX() + "," + imageSpecimen.getDisplayOriginY());
	    // ======================
		
		initPrapInfoLabel();
		
		// 3. 描画更新
		imageSpecimen.updateDisplayImage();
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
	
	/**
     * リスナーからアニメーションの進捗と座標を受け取り、再描画を要求します。
     */
    public void setGhostProgress(int angle, java.awt.Point location) {
        this.ghostProgressAngle = angle;
        this.ghostProgressLocation = location;
        repaint(); // 値が更新されたら再描画
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
		pp.setAndShowPixelValue(this, slideX, slideY);
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
		if (getWidth() < 1 || getHeight() < 1) {
			return;
		}
		Dimension d = imageSpecimen.calcImageSize2FitComponent();
		if (d != null) {
			if (header != null) {
				// ★★★ 90度、270度回転時は、分母となるオリジナルサイズの縦横も入れ替える ★★★
				int angle = getRotateAngle();
				int srcW = (angle % 180 == 0) ? header.getInt(Tag.Columns, 0) : header.getInt(Tag.Rows, 0);
				int srcH = (angle % 180 == 0) ? header.getInt(Tag.Rows, 0) : header.getInt(Tag.Columns, 0);

				this.scaleX = (double) d.width / (double) srcW;
				this.scaleY = (double) d.height / (double) srcH;
			} else {
				int angle = getRotateAngle();
				int imgW = (angle % 180 == 0) ? getOriginalImage().getWidth() : getOriginalImage().getHeight();
				int imgH = (angle % 180 == 0) ? getOriginalImage().getHeight() : getOriginalImage().getWidth();
				this.scaleX = (double) d.width / (double) imgW;
				this.scaleY = (double) d.height / (double) imgH;
			}
		}
	}
	
	public void updateDisplayImage() {
		imageSpecimen.updateDisplayImage();
	}

	void zoom(double mag, boolean zoomUp) {
		double currentMag = MathUtils.truncateToDecimalPlace(getMagnification(), 3);
		mag = MathUtils.truncateToDecimalPlace(mag, 3);
		if (currentMag == mag) return;

		// 倍率の安全ガード
		if (mag < 0.1) mag = 0.1;
		else if (mag > 30.0) mag = 30.0;

		Dimension defaultDim = this.imageSpecimen.calcImageSize2FitComponent();
		if (defaultDim != null) {
			// フィット時のサイズを基準（100%）とする
			double baseW = defaultDim.width;
			double baseH = defaultDim.height;

			// ズームの中心（マウス位置、未設定ならコンポーネント中央）
			double centerX = (mouseX > 0) ? mouseX : getWidth() / 2.0;
			double centerY = (mouseY > 0) ? mouseY : getHeight() / 2.0;

			// 現在の画像の見かけの左上座標
			double currentVisualX = imageSpecimen.originX - (baseW * (currentMag - 1.0) / 2.0);
			double currentVisualY = imageSpecimen.originY - (baseH * (currentMag - 1.0) / 2.0);

			// ズーム中心点への「画像内での相対比率」を固定する
			double relX = (centerX - currentVisualX) / (baseW * currentMag);
			double relY = (centerY - currentVisualY) / (baseH * currentMag);

			// 新しい倍率における、中心点を維持するための新しい等倍原点（originX/Y）の逆算
			double newVisualW = baseW * mag;
			double newVisualH = baseH * mag;
			
			double newVisualX = centerX - (relX * newVisualW);
			double newVisualY = centerY - (relY * newVisualH);

			// originX, originY の定義（zoom=1.0の時の位置）に復元マッピング
			int newOriginX = (int) Math.round(newVisualX + (baseW * (mag - 1.0) / 2.0));
			int newOriginY = (int) Math.round(newVisualY + (baseH * (mag - 1.0) / 2.0));

			panningFlag = (mag != 1.0);
			imageSpecimen.updateOrigin(newOriginX, newOriginY);
		}

		setMagnification(mag);
		imageSpecimen.updateDisplayImage();
		updatePrapInfoLabel(mouseX, mouseY);
	}

	/**
	 * ★ 変更が起きる「直前」にこのメソッドを呼んで、現在の状態を保存します。
	 */
	public void saveUndoState() {
		Log.logger.fine("--- saveUndoState called ---");
		
		if (isRestoring) {
			Log.logger.fine("--- saveUndoState called, is restoring is true, return ---");
			return; 
		}
		
		java.util.List<java.util.HashMap<String, Object>> currentState = createSnapshot();
		
		Log.logger.fine("Current ROIs count to save: " + currentState.size());

		if (!undoStack.isEmpty() && isSameState(undoStack.peek(), currentState)) {
			Log.logger.fine("State is identical to the top of undoStack. Skipping save.");
			return;
		}

		undoStack.push(currentState);
		if (undoStack.size() > MAX_UNDO_LIMIT) {
			undoStack.removeLast();
		}
		redoStack.clear();
		Log.logger.fine("Saved to undoStack. undoStack size: " + undoStack.size() + ", redoStack size: " + redoStack.size());
	}
	
	private java.util.List<java.util.HashMap<String, Object>> createSnapshot() {
		java.util.List<java.util.HashMap<String, Object>> snapshot = new java.util.ArrayList<>();
		java.util.List<RoiObj> currentRois = getRois();
		if (currentRois != null) {
			for (RoiObj roi : new java.util.ArrayList<>(currentRois)) {
				java.util.HashMap<String, Object> ctx = roi.readContext();
				snapshot.add(ctx);
				Log.logger.fine("  -> Snapshot added ROI: " + ctx.get(com.vis.configuration.ContextKey.RoiID.name()) + " (Type: " + ctx.get(com.vis.configuration.ContextKey.RoiType.name()) + ")");
			}
		}
		return snapshot;
	}

	private boolean isSameState(java.util.List<java.util.HashMap<String, Object>> state1, java.util.List<java.util.HashMap<String, Object>> state2) {
		if (state1.size() != state2.size()) return false;
		for (int i = 0; i < state1.size(); i++) {
			java.util.HashMap<String, Object> r1 = state1.get(i);
			java.util.HashMap<String, Object> r2 = state2.get(i);
			
			// 座標やサイズに変化がないか簡易チェック
			if (!String.valueOf(r1.get(com.vis.core.view.D2.roi.RoiGeometry.OriginX.name())).equals(String.valueOf(r2.get(com.vis.core.view.D2.roi.RoiGeometry.OriginX.name()))) ||
			    !String.valueOf(r1.get(com.vis.core.view.D2.roi.RoiGeometry.OriginY.name())).equals(String.valueOf(r2.get(com.vis.core.view.D2.roi.RoiGeometry.OriginY.name()))) ||
			    !String.valueOf(r1.get(com.vis.core.view.D2.roi.RoiGeometry.Width.name())).equals(String.valueOf(r2.get(com.vis.core.view.D2.roi.RoiGeometry.Width.name()))) ||
			    !String.valueOf(r1.get(com.vis.core.view.D2.roi.RoiGeometry.Height.name())).equals(String.valueOf(r2.get(com.vis.core.view.D2.roi.RoiGeometry.Height.name())))) {
				return false;
			}
		}
		return true;
	}

	public void undo() {
		Log.logger.fine("--- undo called ---");
		// 過去の履歴がないなら何もしない
		if (undoStack.isEmpty()) {
			Log.logger.fine("undoStack is empty.");
			return;
		}

		// 1. 今見えている画面の状態を Redo スタックに退避する
		java.util.List<java.util.HashMap<String, Object>> currentlyVisibleState = createSnapshot();
		redoStack.push(currentlyVisibleState);

		// 2. Undo スタックから一番上の過去を取り出す
		java.util.List<java.util.HashMap<String, Object>> stateToRestore = undoStack.pop();
		
		// ★ 究極のガード：もし取り出した過去が「今の画面と全く同じ」なら、それは「無駄に保存された履歴」なので、
		// もう一回 pop してさらに過去に遡る！
		while (!undoStack.isEmpty() && isSameState(stateToRestore, currentlyVisibleState)) {
			Log.logger.fine("Popped state is identical to current. Popping again to find real history.");
			stateToRestore = undoStack.pop();
		}

		// 3. 過去を復元する
		Log.logger.fine("Restoring past state with " + stateToRestore.size() + " ROIs.");
		restoreState(stateToRestore);
	}

	public void redo() {
		Log.logger.fine("--- redo called ---");
		if (redoStack.isEmpty()) return;

		// 1. 今見えている画面の状態を Undo スタックに退避する
		java.util.List<java.util.HashMap<String, Object>> currentlyVisibleState = createSnapshot();
		undoStack.push(currentlyVisibleState);

		// 2. Redo スタックから未来を取り出す
		java.util.List<java.util.HashMap<String, Object>> stateToRestore = redoStack.pop();
		
		// 3. 未来を復元する
		restoreState(stateToRestore);
	}

	/**
	 * ★ DBとの整合性を保ちながら過去の状態を復元する心臓部
	 */	
	private void restoreState(java.util.List<java.util.HashMap<String, Object>> pastState) {

		isRestoring = true;
		Log.logger.fine("restoreState executed, it is restoring...");

		try {

			java.util.List<RoiObj> currentRois = getRois();
			Log.logger.fine("Clearing current ROIs on slide...");
			if (currentRois != null) {
				for (RoiObj roi : new java.util.ArrayList<>(currentRois)) {
					roiOverlay.deleteRoi(roi);
				}
				currentRois.clear();
			}

			com.vis.core.view.D2.roi.RoiConverter converter = new com.vis.core.view.D2.roi.RoiConverter();
			int restoredCount = 0;
			for (java.util.HashMap<String, Object> pastRoiCtx : pastState) {
				Log.logger.fine(
						"Attempting to build RoiObj from Context. ID: " + pastRoiCtx.get(ContextKey.RoiID.name()));
				RoiObj revivedRoi = converter.buildRoiObj(pastRoiCtx);
				if (revivedRoi != null) {
					revivedRoi.setSlideGlass(this, false);
					this.addRoi(revivedRoi);
					restoredCount++;
					Log.logger.fine("Successfully restored ROI ID: " + revivedRoi.getProperty(ContextKey.RoiID.name()));
				} else {
					Log.logger.severe(
							"CRITICAL: Failed to build RoiObj! converter.buildRoiObj returned null. Context keys: "
									+ pastRoiCtx.keySet());
				}
			}

			Log.logger.fine("Restore complete. Successfully restored " + restoredCount + " ROIs.");
			repaint();

			repaint();
			com.vis.core.view.D2.roi.RoiObjManager.getInstance().updateState();

		} finally {
			// ★ ガード解除：処理が終わったら（エラーが起きても）必ずフラグを下ろす
			isRestoring = false;
			Log.logger.fine("restoreState finished.");
		}
	}
	
	@Override
    public void paint(java.awt.Graphics g) {
        super.paint(g); // 元の画像やOverlayの描画を先に済ませる

        // アニメーションが有効な場合のみ、最前面に円を描画する
        if (ghostProgressAngle > 0 && ghostProgressLocation != null) {
            java.awt.Graphics2D g2d = (java.awt.Graphics2D) g.create();
            // アンチエイリアスを有効にして円を滑らかにする
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            int radius = 25; // 円の半径
            int x = ghostProgressLocation.x - radius;
            int y = ghostProgressLocation.y - radius;

            // 背景の薄いガイド円を描画（任意）
            g2d.setColor(new java.awt.Color(255, 255, 255, 100)); // 半透明の白
            g2d.setStroke(new java.awt.BasicStroke(4.0f));
            g2d.drawOval(x, y, radius * 2, radius * 2);

            // 進捗を示す円弧を描画
            g2d.setColor(new java.awt.Color(0, 153, 255, 220)); // 鮮やかなブルー
            // drawArc(x, y, w, h, 開始角度, 描画角度)
            // 90度が時計の12時方向、マイナスの値を指定すると時計回りに描画されます
            g2d.drawArc(x, y, radius * 2, radius * 2, 90, -ghostProgressAngle);

            g2d.dispose();
        }
    }
}
