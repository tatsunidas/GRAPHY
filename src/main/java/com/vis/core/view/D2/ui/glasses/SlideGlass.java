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

	public SlideGlass(Praparat pp, DicomImage dcmImg/* single frame */) {
		if (pp == null || dcmImg == null) {
			throw new NullPointerException();
		}
		initComponents(pp, dcmImg);
		isRGB = dcmImg.isColor();
		isBiped = SubjectOrientation.isBiped(dcmImg.getHeader());
	}

	public void addRoi(RoiObj roi) {
		roiOverlay.addRoi(roi);
	}

	void adjustContrastFromMouseAction(int dragX, int dragY) {
		// 1. 開始位置からの「総移動距離」を計算 (前回との差分ではない)
	    int xDiff = dragX - lastPressedX;
	    int yDiff = dragY - lastPressedY;

	    // 2. 画面サイズに対する移動割合
	    int totalWidth = getWidth();
	    int totalHeight = getHeight();
	    
	    // ゼロ除算対策
	    if (totalWidth == 0 || totalHeight == 0) return;

	    // 感度調整（係数）。1.0だと画面端から端までドラッグして全範囲変化。
	    // 必要に応じて 2.0 などを掛けて感度を上げてください。
	    double sensitivity = 1.0; 
	    
	    // 現在のダイナミックレンジ（全体の最大-最小）
	    double dynamicRange = currentMax - currentMin;

	    // 3. 移動量に応じた変化量を計算
	    // X軸: 右(正)でWWを広げる、左(負)で狭める -> そのまま加算
	    double windowChange = (xDiff / (double) totalWidth) * dynamicRange * sensitivity;
	    
	    // Y軸: 下(正)でWLを下げる、上(負)でWLを上げる -> 符号を反転させる
	    // (スクリーン座標は下がプラス、要望は上がプラスなので -1 を掛ける)
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
		logger.fine("change ww/wl : newMin " + newMin + " newMax " + newMax);
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
		double scaleComp = getScaleFactor()[0];
		double zoomFactor = getMagnification();
		Dimension defaultDim = this.imageSpecimen.calcImageSize2FitComponent();
		return new Dimension((int)(defaultDim.width*scaleComp*zoomFactor), (int)(defaultDim.height*scaleComp*zoomFactor));
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
		if(pp.getViewMode() != ViewMode.Thumbnail) {
			loadRoiFromDB();
		}
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
		setLUT(extractDisplayLUT(header));
		
		setupSpatialCalibration(originalCal, header);
		setupDensityCalibration(originalCal, header);
		// adjust WW/WL
		resetContrast();
		setOriginalCalibration(originalCal);
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
		if (pp == null) {
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
		double scaleToFit = getScaleFactor()[0];
		double zoomFactor = getMagnification();
		double s = scaleToFit * zoomFactor;
		double sx = flipHorizontalFlag ? -s : s;// LR flip
		double sy = flipVerticalFlag ? -s : s;// Head-Foot flip

		// 回転角度（度数法をラジアンに変換）
		double rotateAngleInDegrees = getRotateAngle();
		double thetaInRadians = Math.toRadians(rotateAngleInDegrees);

		Dimension offScreen = getOriginalImageSize(); // OffScreen image size
		double offCenterX = offScreen.width / 2.0;
		double offCenterY = offScreen.height / 2.0;

		// B. 画面上の表示位置（Destination）の中心
		// originX, originY は「画像の左上」を指しているため、サイズ/2 を足して中心を求めます。
		// ※ここでのサイズは、回転前の元画像のサイズにスケールを掛けたものです。
		double currentImgW = offScreen.width * s;
		double currentImgH = offScreen.height * s;

		double destCenterX = imageSpecimen.originX + (currentImgW / 2.0);
		double destCenterY = imageSpecimen.originY + (currentImgH / 2.0);

		// 3. 行列の作成 (順序が重要です)
		currentTransform = new AffineTransform();

		// Step 4: 最後に、求めた画面上の中心位置へ移動させる
		currentTransform.translate(destCenterX, destCenterY);

		// Step 3: 回転させる
		currentTransform.rotate(thetaInRadians);

		// Step 2: スケール（拡大縮小）とフリップ（反転）を適用する
		currentTransform.scale(sx, sy);

		// Step 1: まず、元画像の中心を原点(0,0)に持ってくる
		currentTransform.translate(-offCenterX, -offCenterY);

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

	public void resetContrast() {
		// adjust WW/WL
		Double wl = header.getDouble(Tag.Window​Center, Double.NaN);
		Double ww = header.getDouble(Tag.Window​Width, Double.NaN);
		if (wl.isNaN() || ww.isNaN()) {
			autoWindowing();
			return;
		}
		/*
		 * * 2. オフセットの計算 ImageJのShortProcessorはUnsigned 16-bit(0-65535)として扱うため、
		 * Signedデータを読み込む際に足し合わせたオフセットを引いて、 WL/WWの基準をImageJのピクセル値に合わせる必要があります。
		 */
		double offset = 0;
		boolean isSigned = dcmImg.isSigned();
		int bitsAllocated = dcmImg.getBitsAllocated();

		if (isSigned) {
			if (bitsAllocated == 16) {
				// Signed 16-bitの場合、通常中心を32768シフトさせている
				offset = 32768.0;
			} else if (bitsAllocated == 8) {
				// Signed 8-bitの場合（稀）
				offset = 128.0;
			}
		}

		/*
		 * 3. Rescale Slope/Intercept の考慮 DICOMのWL/WWは「Rescale適用後の値（HUなど）」で定義されています。
		 * ImageProcessor.setMinAndMax() は「生のピクセル値」に対して行う必要があるため、
		 * 逆計算をして生のピクセル値ベースのMin/Maxを求めます。
		 */
		double slope = header.getDouble(Tag.Rescale​Slope, 1.0);
		double intercept = header.getDouble(Tag.Rescale​Intercept, 0.0);

		// 表示範囲の最小・最大を計算 (物理単位)
		double minPhys = wl - (ww / 2.0);
		double maxPhys = wl + (ww / 2.0);

		// 生のピクセル値 (ImageJの内部値) に逆変換
		// 物理値 = (raw - offset) * slope + intercept
		// => raw = ((物理値 - intercept) / slope) + offset
		double rawMin = ((minPhys - intercept) / slope) + offset;
		double rawMax = ((maxPhys - intercept) / slope) + offset;
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
		imageSpecimen.updateDisplayImage();
	}
	
	public void setDisplayOrigin(Point p) {
		imageSpecimen.updateOrigin(p.x, p.y);
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
				this.scaleX = (double) d.width / (double) header.getInt(Tag.Columns, 0);
				this.scaleY = (double) d.height / (double) header.getInt(Tag.Rows, 0);
			} else {
				this.scaleX = (double) d.width / (double) getOriginalImage().getWidth();
				this.scaleY = (double) d.height / (double) getOriginalImage().getHeight();
			}
		}
	}
	
	public void updateDisplayImage() {
		imageSpecimen.updateDisplayImage();
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
