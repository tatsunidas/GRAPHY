package com.vis.core.view.D2.ui.glasses;

import java.awt.Color;
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

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;

import com.vis.core.log.Log;
import com.vis.core.util.ByteUtils;
import com.vis.core.util.Utils;
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
import ij.plugin.Duplicator;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.util.DicomTools;

/**
 * image screen with overlays
 * 
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class SlideGlass extends JLayeredPane {

	// main layer component
	private JLayer<SlideGlass> slide;// main component
	private Praparat pp;// series viewer
	private DicomObject header;
	private DicomImage dcmImg;
//	protected Color studyColor = Color.CYAN;
	// glasses
	private ImageSpecimenGlass imageSpecimen;
	private TextOverlayGlass textOverlay;
	private CanvasGlass roiOverlay;
	private SlideGlassUI layerUI;
	// glass layer type
	public final static int IMAGE_LAYER = JLayeredPane.DEFAULT_LAYER;
	public final static int ROI_CANVAS_LAYER = JLayeredPane.PALETTE_LAYER;
	public final static int TEXT_LAYER = JLayeredPane.MODAL_LAYER;
	//Border
	Color focusColor = Color.WHITE;
	Color selectionColor = Color.MAGENTA;
	Color clearColor = new Color(0,0,0,255);
	final int BORDER_SIZE = 4;
	Border focusBorder = BorderFactory.createLineBorder(focusColor, BORDER_SIZE);
	Border selectionBorder = BorderFactory.createLineBorder(selectionColor, BORDER_SIZE);
	// image // TODO change to ImageProcessor.
	private ImagePlus original = null;
	private ImagePlus displayImp = null;
	// flags
	private boolean focusFlag = false;
	private boolean selectedFlag = false;
	public boolean panningFlag = false;
	public boolean panningInAction = false; //
	public boolean rotatedFlag = false, flipHorizontalFlag = false, flipVerticalFlag = false, zoomFlag = false;
	private boolean invertFlag = false;
	private boolean flipFlag = false;
	private boolean windowing = false;// WW/WL changed
	private boolean showAnnotation = true;
	private boolean showText = true;
	boolean isPDF = false;
	boolean isGrayscale = false;//LUTがGrayかどうか。チャンネル数に関係なく。
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
	private double magnification = 1.0d;// 表示しているslideglassコンポーネントサイズに対応した画像サイズに対する倍率
	private double lastMagnification = 1.0d;
	// mouse settings
	public int lastX = -1;// last clicked mouse position X on slideglass
	public int lastY = -1;// last clicked mouse position Y on slideglass
	public int lastDraggedX = 0;
	public int lastDraggedY = 0;
	public int mouseX = 0;// current mouse loc on slideglass
	public int mouseY = 0;// current mouse loc on slideglass
	// displayed image origin
	public int originX = 0;
	public int originY = 0;
	public int lastOriginX = 0;// for pann&zoom
	public int lastOriginY = 0;// for pann&zoom
	private LUT currentLUT;//null-able, if null set grayscale

	// for pixel scale (praparatview vs original)
	private double scale = 1.0d; // (fit to comp size)/(original)

	/* will need test related ColorModels... */
	// ColorModel variables
//    private ColorModelParam cmParam = null;
//    private static final ColorModelFactory cmFactory = new ColorModelFactory();//tatsu
////    private ColorModel cm = null;
//    private PaletteColorModel cm = null;//tatsu

	// TextOverlay
//    private TextOverlayParam textOverlayParam;
//    private float floatAspectRatio;
	// Scout Param
//    ScoutLineInfoModel currentScoutDetails;
//    private String orientationLabel = "";
//    private boolean isLocalizer = false, isEncapsulatedDocument = false;
//    private static boolean displayScout = false;
//    private int scoutLine1X1, scoutLine1Y1, scoutLine1X2, scoutLine1Y2, scoutLine2X1, scoutLine2Y1, scoutLine2X2, scoutLine2Y2;
//    private int boundaryLine1X1, boundaryLine1Y1, boundaryLine1X2, boundaryLine1Y2, boundaryLine2X1, boundaryLine2Y1, boundaryLine2X2, boundaryLine2Y2;
//    double slope1, slope2;
//    private int thumbWidth = 512, thumbHeight = 512, maxHeight = 512, maxWidth = 512;
//    private double thumbRatio, currentScaleFactor = 1;
//    private int axis1LeftX, axis1LeftY, axis1RightX, axis1RightY, axis2LeftX, axis2LeftY, axis2RightX, axis2RightY, axisLeftX, axisLeftY, axisRightX, axisRightY;
//    public static boolean synchornizeTiles = false;
//    private PDFFile curFile = null;
//    private PDDocument curFile = null;
//    private int curpage = -1;
//    SeriesAnnotations currentSeriesAnnotation = null;
//    public Buffer buffer = null;//future work??
//    boolean isNormal = false;
//    public boolean setHints = true;
//    int multiframePtr = 0;
//    PDPage pg = null;

	public int INTERPOLATION_METHOD = ImageProcessor.NEAREST_NEIGHBOR;
	private ImageProcessing imgProcess = new ImageProcessing();
	private ArrayList<RoiObj> roiset;
	Logger logger = Log.logger;

	public SlideGlass(Praparat pp, DicomImage dcmImg) {
		initComponents(pp, dcmImg);
	}
	
	public void initComponents(Praparat pp, DicomImage dcmImg) {
		this.pp = pp;
		this.roiset = new ArrayList<RoiObj>();
		this.dcmImg = dcmImg;
		this.header = dcmImg.getCore();
		setBorder(BorderFactory.createLineBorder(clearColor, BORDER_SIZE));
		setOpaque(false);
		setFocusable(true);// for keylistener
		setRequestFocusEnabled(true);
		setOriginalImage(new ImagePlus("", dcmImg.getImageProcessor(0)));
		initImageInfo(header);// execute before setUpGlasses
		setUpGlassLayer(header);
		loadRoiFromDB();
		setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
	}

	public Praparat getPraparat() {
		return this.pp;
	}
	
	public DicomImage getDicomImage() {
		return this.dcmImg;
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

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public int onImageX(int glassX) {
		return onOriginalImageX(glassX);
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public int onImageY(int glassY) {
		return onOriginalImageY(glassY);
	}

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public double onImageXD(int glassX) {
		return onOriginalImageXD(glassX);
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public double onImageYD(int glassY) {
		return onOriginalImageYD(glassY);
	}

	/** Converts an screen x-coordinate to a original image x-coordinate. */
	public int onOriginalImageX(int glassX) {
		if (!panningFlag) {
			double backScale = (glassX - originX) / getMagnification() / getScaleFactor();
			return (int) backScale;
		} else {
			/*
			 * 見かけ上の原点に合うように原点位置に対してスケールは乗ずる
			 */
			double backScale = (glassX - (originX * getScaleFactor())) / getMagnification() / getScaleFactor();
			return (int) backScale;
		}
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public int onOriginalImageY(int glassY) {
		if (!panningFlag) {
			double backScale = (glassY - originY) / getMagnification() / getScaleFactor();
			return (int) backScale;
		} else {
			double backScale = (glassY - (originY * getScaleFactor())) / getMagnification() / getScaleFactor();
			return (int) backScale;
		}
	}

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public double onOriginalImageXD(int glassX) {
		if (!panningFlag) {
			double backScale = (glassX - originX) / getMagnification() / getScaleFactor();
			return backScale;
		} else {
			double backScale = (glassX - (originX * getScaleFactor())) / getMagnification() / getScaleFactor();
			return backScale;
		}
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public double onOriginalImageYD(int glassY) {
		if (!panningFlag) {
			double backScale = (glassY - originY) / getMagnification() / getScaleFactor();
			return backScale;
		} else {
			double backScale = (glassY - (originY * getScaleFactor())) / getMagnification() / getScaleFactor();
			return backScale;
		}
	}

	/** Converts an offscreen x-coordinate to a screen x-coordinate. */
	public int onDisplayImageX(int glassX) {
		if (!panningFlag) {
			return (int) ((glassX - originX) * getMagnification());
		} else {
			return (int) ((glassX - (originX * getScaleFactor())) * getMagnification());
		}
	}

	/** Converts an offscreen y-coordinate to a screen y-coordinate. */
	public int onDisplayImageY(int glassY) {
		if (!panningFlag) {
			return (int) ((glassY - originY) * getMagnification());
		} else {
			return (int) ((glassY - (originY * getScaleFactor())) * getMagnification());
		}
	}

	/**
	 * Converts a floating-point offscreen x-coordinate to a screen x-coordinate.
	 */
	public double onDisplayImageXD(double glassX) {
		if (!panningFlag) {
			return ((glassX - originX) * getMagnification());
		} else {
			return ((glassX - (originX * getScaleFactor())) * getMagnification());
		}
	}

	/**
	 * Converts a floating-point offscreen x-coordinate to a screen x-coordinate.
	 */
	public double onDisplayImageYD(double glassY) {
		if (!panningFlag) {
			return ((glassY - originY) * getMagnification());
		} else {
			return ((glassY - (originY * getScaleFactor())) * getMagnification());
		}
	}

	/*
	 * convert originale coordinate to glass coordinate.
	 */
	public int screenX(int imageX) {
		return orgX2ScreenX(imageX);
	}

	public int screenY(int imageY) {
		return orgY2ScreenY(imageY);
	}

	public double screenXD(double imageX) {
		return orgX2ScreenXD(imageX);
	}

	public double screenYD(double imageY) {
		return orgY2ScreenYD(imageY);
	}

	private int orgX2ScreenX(int orgImageX) {
		if (!panningFlag) {
			return (int) ((orgImageX * getMagnification() * getScaleFactor()) + originX);
		} else {
			return (int) ((orgImageX * getMagnification() * getScaleFactor()) + (originX * getScaleFactor()));
		}
	}

	private int orgY2ScreenY(int orgImageY) {
		if (!panningFlag) {
			return (int) ((orgImageY * getMagnification() * getScaleFactor()) + originY);
		} else {
			return (int) ((orgImageY * getMagnification() * getScaleFactor()) + (originY * getScaleFactor()));
		}
	}

	private double orgX2ScreenXD(double orgImageX) {
		if (!panningFlag) {
			return ((orgImageX * getMagnification() * getScaleFactor()) + originX);
		} else {
			return ((orgImageX * getMagnification() * getScaleFactor()) + (originX * getScaleFactor()));
		}
	}

	public double orgY2ScreenYD(double orgImageY) {
		if (!panningFlag) {
			return ((orgImageY * getMagnification() * getScaleFactor()) + originY);
		} else {
			return ((orgImageY * getMagnification() * getScaleFactor()) + (originY * getScaleFactor()));
		}
	}

	public String getPatientID() {
		return header != null ? header.getString(Tag.Patient​ID, "NO_PID") : null;// safe ?
	}

	public String getStudyInstanceUID() {
		return header != null ? header.getString(Tag.Study​Instance​UID, "NO_StudyInstanceUID"):null;
	}

	public String getSeriesInstanceUID() {
		return header != null ? header.getString(Tag.Series​Instance​UID, "NO_SeriesInstanceUID"):null;
	}

	public String getSOPInstanceUID() {
		return header != null ? header.getString(Tag.SOP​Instance​UID, "NO_SOPInstanceUID"):null;
	}

	public Integer getInstanceNo() {
		return header != null ? header.getInt(Tag.Instance​Number, 0):null;
	}

	public String getUID(int tag) {
		return header != null ? header.getString(tag):null;
	}

//	private void setModality(String modality) {
//		this.modality = modality;
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
	
	public DicomObject getHeader() {
		return header;
	}

	public JLayer<SlideGlass> getSlideGlassAsLayer() {
		return slide;
	}

	public void setOriginalImage(ImagePlus imp) {
		this.original = imp;
		imp.deleteRoi();
		initDisplayImage();// IMPORTANT; update calibration
	}

	public ImagePlus getOriginalImage() {
		return this.original;
	}
	
	private void setOriginalCalibration(Calibration cal) {
		getOriginalImage().setCalibration(cal);
	}
	
	public Calibration getOriginalCalibration() {
		return getOriginalImage().getCalibration();
	}

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
		showBorder();
		repaint();
	}

	public boolean isSelected() {
		return selectedFlag;
	}

	public boolean isFocusGained() {
		return focusFlag;
	}

	public void setFocusGained(boolean focusGained) {
		this.focusFlag = focusGained;
		if(pp.getViewMode()==ViewMode.Thumbnail) {
			this.focusFlag = false;
			showBorder();
			return;
		}
		if(pp.isShowGridViewOn()) {
			pp.setImagePositionTo(this);
		}
		showBorder();
	}
	
	private Border constructBorder() {
		if(pp.getViewMode()==ViewMode.SingleGrid) {
			if(!isSelected() && isFocusGained()) {
				return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
			}else if(isSelected() && !isFocusGained()) {
				return selectionBorder;
			}else if(isSelected() && isFocusGained()) {
				return selectionBorder;
			}else {
				return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
			}
		}
		if(pp.getViewMode()==ViewMode.Normal && !pp.isShowGridViewOn())  {
			if(!isSelected() && isFocusGained()) {
				return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
			}else if(isSelected() && !isFocusGained()) {
				return selectionBorder;
			}else if(isSelected() && isFocusGained()) {
				return selectionBorder;
			}else {
				return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
			}
		}
		if(!isSelected() && isFocusGained()) {
			return focusBorder;
		}else if(isSelected() && !isFocusGained()) {
			return selectionBorder;
		}else if(isSelected() && isFocusGained()) {
			Border focus = BorderFactory.createLineBorder(focusColor, BORDER_SIZE/2);
			Border select = BorderFactory.createLineBorder(selectionColor, BORDER_SIZE/2);
			return new CompoundBorder(focus, select);
		}else {
			return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
		}
	}

	public void showBorder() {
		Border b = constructBorder();
		setBorder(b);
		repaint();
	}
	
//	public void showBorder(boolean show) {
//		if(show) {
//			showBorder();
//		}else {
//			Border b = BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
//			setBorder(b);
//		}
//	}
	
	public void setViewer2DToolType(int toolType) {
		layerUI.setViewer2DToolType(toolType);
	}
	
	public int getViewer2DToolTypeInSlideGlassUI() {
		return layerUI.getViewer2DToolType();
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
	
	/**
	 * create simple image to display, it was fitted prap size without zoom/pan/rotation/windowing.
	 * @return
	 */
	private ImagePlus createInitialDisplayImage() {
//		ImagePlus imp = getOriginalImage().duplicate();//DO NOT USE, does not attach calibration.	
//		ImageProcessor ip = getOriginalImage().getProcessor().duplicate();//DO NOT USE, does not attach calibration.	
//		ImagePlus dup = new Duplicator().run(getOriginalImage());//OK
		ImagePlus imp = getOriginalImage().createImagePlus();
		ImageProcessor ip = getOriginalImage().getProcessor().duplicate();
		ip.setInterpolationMethod(INTERPOLATION_METHOD);
		if(isRGB && ip instanceof ColorProcessor) {
			ip.snapshot();//keep original pixels
		}
		imp.setProcessor(ip);
		imp.setTitle("replica");
		// resize to comp size
		Dimension d = calcImageSize2FitComponent();
		if (d != null) {
			imp = imgProcess.zoom(imp, getScaleFactor());
		}
		return imp;
	}
	
	private void loadDisplayImage() {
		this.displayImp = createInitialDisplayImage();
	}

	/*
	 * init reference size (praparatview size) image
	 */
	private void initDisplayImage() {
		if(getOriginalImage() == null) {
			return;
		}
		loadDisplayImage();
		// windowing
		if (imageSpecimen != null) {
//			autoWindow();
			adjustWindow2Current();
		}
	}

	@SuppressWarnings("unused")
	private ImagePlus getCurrentStateImageFreshCopy(boolean flip, boolean invert, boolean zoom, boolean rotate,
			boolean window) {
		ImagePlus dup = createInitialDisplayImage();
		if (flip) {
			dup.getProcessor().flipHorizontal();
		}
		if (zoom) {
			double mag = getMagnification();
			dup = imgProcess.zoom(dup, mag);
		}
		if (rotate) {
			imgProcess.rotate(dup, getRotateAngle());
		}
		dup.setLut(currentLUT);
		if (invert) {
			imgProcess.invert(dup);
		}
		/*
		 * skip panning, delegate slideglass::updateImage
		 */
		// window
		if (window) {
			imgProcess.windowing(dup, currentMin, currentMax);
		}
		return dup;
	}

	private ImagePlus getCurrentStateImageFreshCopy() {
		ImagePlus dup = createInitialDisplayImage();
		if (isFlipped()) {
			dup.getProcessor().flipHorizontal();
		}
		if (isZoomed()) {
			double mag = getMagnification();
			dup = imgProcess.zoom(dup, mag);
		}
		if (isRotated()) {
			imgProcess.rotate(dup, getRotateAngle());
		}
		dup.setLut(currentLUT);
		if (isInverted()) {
			imgProcess.invert(dup);
		}
		/*
		 * skip panning, to delegate slideglass::updateImage
		 */
		// window
		if (windowing) {
			imgProcess.windowing(dup, currentMin, currentMax);
		}
		return dup;
	}

	private void setUpGlassLayer(DicomObject header) {
		imageSpecimen = new ImageSpecimenGlass();
		roiOverlay = new CanvasGlass(this);
		textOverlay = new TextOverlayGlass(header);
		int top_in_its_layers = 0;
		add(imageSpecimen, IMAGE_LAYER, top_in_its_layers);
		add(roiOverlay, ROI_CANVAS_LAYER, top_in_its_layers);
		add(textOverlay, TEXT_LAYER, top_in_its_layers);
		// finally, add LayerUI for Actions
		layerUI = new SlideGlassUI(this);
		slide = new JLayer<SlideGlass>(this, layerUI);
		slide.setOpaque(true);// IMPORTANT
		slide.setBackground(Color.BLACK);
	}

	private void initImageInfo(DicomObject dataset) {
		if(dataset == null) {
			initImageInfoUsingImagePlus();
			return;
		}
		ImagePlus org = getOriginalImage();
		Calibration originalCal = org.getCalibration().copy();
		/*
		 * load lut from dicom tag ? todo, see dicomwriter.
		 */
		setLUT(org.getProcessor().getLut());
		this.header = dataset;
		isRGB = org.getType() == ImagePlus.COLOR_RGB;//choice suitable one.
		if(isRGB()) {
			org.getProcessor().snapshot();
		}
		String inverted = dataset.getString(Tag.Photometric​Interpretation, null);
		if ("MONOCHROME1".equals(inverted) || "MONOCHROME2".equals(inverted)) {
			/*
			 * Spatial calibrations
			 */
			try {
				// x-y-z
				double pixelSpacingX = 1.0;
				double pixelSpacingY = 1.0;
				double pixelSpacingZ = 1.0;
				double[] pixelSpacing = dataset.getDoubles(Tag.Pixel​Spacing);
				double spacingBetweenSlices = dataset.getDouble(Tag.Spacing​Between​Slices, -1);
				if (pixelSpacing != null && pixelSpacing != ByteUtils.EMPTY_DOUBLES) {
					pixelSpacingX = pixelSpacing[0];
					pixelSpacingY = pixelSpacing[1];
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
				} else {
					// init
					pixelSpacingX = 1.0;
					pixelSpacingY = 1.0;
					pixelSpacingZ = 1.0;
					if (spacingBetweenSlices != -1) {
						pixelSpacingZ = spacingBetweenSlices;
					} else {
						double sliceThickness = dataset.getDouble(Tag.Slice​Thickness, -1);
						if (sliceThickness != -1) {
							pixelSpacingZ = sliceThickness;
						}
					}
					originalCal.setUnit("pixel");
				}
				// then, set to cal
				originalCal.pixelWidth = pixelSpacingX;
				originalCal.pixelHeight = pixelSpacingY;
				originalCal.pixelDepth = pixelSpacingZ;
			} catch (NullPointerException e) { // ignore
				logger.log(Level.SEVERE,"SlideGlass - Unable to get Pixel spacing", e.getMessage());
			}
			/*
			 * set density calibration
			 */
//			if(!originalCal.scaled()) {//DO NOT USE
			/*
			 * see, ij.measure.Calibration.setImage()
			 */
			if (getModality().equals("CT") && dataset.getInt(Tag.Bits​Stored, -1) == 16) {
				double slope = dataset.getDouble(Tag.Rescale​Slope, -1);
				double intercept = dataset.getDouble(Tag.Rescale​Intercept, -1);
				// 0 = unsigned, 1 = signed
				int pixelRep = dataset.getInt(Tag.Pixel​Representation,-1);
				if (intercept == 0 && pixelRep == 1) {
//					double[] coeff = new double[2];
//					coeff[0] = -32768.0;
//					coeff[1] = slope;// 1.0
					String pixelValUnit = "HU";
//					originalCal.setFunction(Calibration.STRAIGHT_LINE, coeff, pixelValUnit);
					originalCal.setSigned16BitCalibration();
					originalCal.setValueUnit(pixelValUnit);
				} else {
					if (slope != -1 && intercept != -1) {
						originalCal.setFunction(Calibration.STRAIGHT_LINE, new double[] { intercept, slope }, "HU");
					}
				}
			}else if(dataset.getInt(Tag.Pixel​Representation, -1)==1 && dataset.getInt(Tag.Bits​Stored, -1) == 16) {
				originalCal.setSigned16BitCalibration();
			}
//			}
			
			// adjust WW/WL
			int WL = dataset.getInt(Tag.Window​Center, -1);
			int WW = dataset.getInt(Tag.Window​Width, -1);
			
			if (WL == -1 || WW == -1) {
				autoWindow();
			} else {
				changeWindow(WL, WW);
			}
		}
		setOriginalCalibration(originalCal);
	}
	
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

	public void setAnnotationVisible(boolean v) {
		if (this.showAnnotation == v) {
			return;
		}
		this.showAnnotation = v;
		if (this.showAnnotation) {
			// show annotation
			add(roiOverlay, 0,  JLayeredPane.PALETTE_LAYER);
		} else {
			// do not show annotation
			remove(roiOverlay);
		}
		repaint();
	}

	public void setTextVisible(boolean v) {
		if (this.showText == v) {
			return;
		}
		this.showText = v;
		if (this.showText) {
			// show annotation
			add(textOverlay, 0, JLayeredPane.MODAL_LAYER);
		} else {
			// do not show annotation
			remove(textOverlay);
		}
		repaint();
	}

	/*
	 * current display image origin
	 */
	public Point getDisplayImageLocationXY() {
		Point origin = new Point(this.originX, this.originY);
		return origin;
	}

	/*
	 * mouse position on slide glass XY location
	 */
	public Point getCursorLoc() {
		Point pointOnScreen = new Point(this.mouseX, this.mouseY);
		return pointOnScreen;
	}

	public Dimension getOriginalImageSize() {
		int[] dims = getOriginalImage().getDimensions();
		return new Dimension(dims[0], dims[1]);
	}

	/*
	 * for grid view
	 */
	public void fitImg2Comp(int compW, int compH) {
		if (compW == 0 || compH == 0) {
			return;
		}
		adjustGlassesSize(compW, compH);
		updateScale();
		displayCurrentStateImage();
		initPrapInfoLabel();
	}

	/*
	 * for single view resize img modify img origin
	 */
	public void fit2Praparat() {
		if (getWidth() < 10 || getHeight() < 10) {
			return;
		}
		adjustGlassesSize(getWidth(), getHeight());
		updateScale();
		displayCurrentStateImage();
		initPrapInfoLabel();
	}

	private void adjustGlassesSize(int compW, int compH) {
		setGlassSize(this, compW, compH);
		setGlassSize(slide, compW, compH);
		setGlassSize(imageSpecimen, compW, compH);
		setGlassSize(textOverlay, compW, compH);
		setGlassSize(roiOverlay, compW, compH);
	}

	private void setGlassSize(JComponent comp, int compW, int compH) {
		comp.setSize(new Dimension(compW, compH));
		comp.setPreferredSize(new Dimension(compW, compH));
		/*************************************************************************************/
		comp.setBounds(0, 0, compW, compH);// MUST, set pane size and position.this is not image position
		/*************************************************************************************/
	}

	/*
	 * fit original size to praparat view comp size
	 */
	private Dimension calcImageSize2FitComponent() {
		int bound_width = getWidth()-(BORDER_SIZE*2);
		int bound_height = getHeight()-(BORDER_SIZE*2);
		if (bound_width < 1 || bound_height < 1) {
			return null;
		}
		Dimension orgImgSize = getOriginalImageSize();
		int original_width = orgImgSize.width;
		int original_height = orgImgSize.height;
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

	private void calcDefaultImageOriginAndReset(int newImgW, int newImgH, int prapViewWidth, int prapViewHeight) {
		Point defaultOrigin = calcDefaultImageOrigin(newImgW, newImgH, prapViewWidth, prapViewHeight);
		this.originX = defaultOrigin.x;
		this.originY = defaultOrigin.y;
		// set false force.
		if (panningFlag) {
			panningFlag = false;
		}
//		System.out.println("Reset Origin, CurrentOrigin (X,Y) : "+originX +" "+ originY);
		logger.info("calcDefaultImageOriginAndReset: Reset CurrentOriginXY: " + originX + " " + originY + " ,compXY: "
				+ prapViewWidth + ", " + prapViewHeight);
	}

	private Point calcDefaultImageOrigin(int newImgW, int newImgH, int compWidth, int compHeight) {
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

	public Dimension getDisplayImageDimension() {
		int[] dim = this.displayImp.getDimensions();
		return new Dimension(dim[0], dim[1]);
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

	/**
	 * This routine used to retrieve the instance related information
	 */
//	private void retrieveInstanceInformation(Attributes dataset) {
//		String inverted = dataset.getString(Tag.PhotometricInterpretation, null);
//		if ("MONOCHROME1".equals(inverted) || "MONOCHROME2".equals(inverted)) {
////            cmParam = cmFactory.makeParam(dataset);//tatsu
//
//			int size = 1 << dataset.getInt(Tag.BitsStored, 8);
//			int signed = dataset.getInt(Tag.PixelRepresentation, 0);
//			int min = dataset.getInt(Tag.SmallestImagePixelValue, signed == 0 ? 0 : -(size >> 1));
//			int max = dataset.getInt(Tag.LargestImagePixelValue, signed == 0 ? size - 1 : (size >> 1) - 1);
////			int cMin = (int) cmParam.toMeasureValue(min);
////			int cMax = (int) cmParam.toMeasureValue(max - 1);
////			int wMax = cMax - cMin;
////			int w = wMax;
//			try {
//				pixelSpacingY = Double.parseDouble(dataset.getString(Tag.PixelSpacing, 0));
//				pixelSpacingX = Double.parseDouble(dataset.getString(Tag.PixelSpacing, 1));
//			} catch (NullPointerException e) { // ignore
//				Viewer2DScreen.logger().error("Image Panel - Unable to get Pixel spacing", e.getMessage());
//			}
//			//set initial pixel scale, see annotaion ovly
//			scale = (double)img.getWidth() / (double)dataset.getInt(Tag.Rows,img.getWidth());
//			
//			//following, future work...
////			int nWindow = cmParam.getNumberOfWindows();
////			if (nWindow > 0) {
////				WC = windowLevel = (int) cmParam.getWindowCenter(0);
////				WW = windowWidth = (int) cmParam.getWindowWidth(0);
////			} else {
////				WW = windowWidth = (int) Math.pow(2, dataset.getInt(Tag.BitsStored, 8));
////				WC = windowLevel = (int) w / 2;
////			}
//		}
////        windowChanged(windowLevel, windowWidth);
//	}

	// To render the overlay data in image
	private BufferedImage combineImages(BufferedImage currentbufferedimage, BufferedImage overlayImg) {
		Graphics2D g2d = currentbufferedimage.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g2d.drawImage(overlayImg, 0, 0, null);
		g2d.dispose();
		return currentbufferedimage;
	}

//	private void setIsNormal() {
//        if (!multiframe && !isEncapsulatedDocument && !isPDF) {
//            isNormal = true;
//        }
//    }
//	
//    private void openPDFByteBuffer(ByteBuffer buf, String path, String name) {
//        PDDocument newfile = null;
//        try {
//            newfile = PDDocument.load(buf.array());//tatsu, need test!
//        } catch (IOException ioe) {
//            return;
//        }
//        curFile = newfile;
//        forceGotoPage(0);
//    }
//    
//    public void forceGotoPage(int pagenum) {
//        Image loadedImage = null;
//        ImageIcon imageIcon = null;
//        if (pagenum <= 0) {
//            pagenum = 0;
//        } else if (pagenum >= curFile.getNumberOfPages()) {
//            pagenum = curFile.getNumberOfPages() - 1;
//        }
//        PDFRenderer pdfRenderer = new PDFRenderer(curFile);
//        totalInstance = curFile.getNumberOfPages();
//        curpage = pagenum;
//        pg = curFile.getPage(pagenum + 1);
//        Rectangle rect = new Rectangle(0, 0,
//                (int) pg.getBBox().getWidth(),
//                (int) pg.getBBox().getHeight());
//
//        //generate the image
////        Image current = pg.getImage(
////                rect.width, rect.height, //width & height
////                rect, // clip rect
////                null, // null for the ImageObserver
////                true, // fill background with white
////                true // block until drawing is done
////        );
//        
//      //generate the image
//        BufferedImage current = null;
//		try {
//			current = pdfRenderer.renderImage(pagenum);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//        
//        imageIcon = new ImageIcon();
//        imageIcon.setImage(current);
//        loadedImage = imageIcon.getImage();
//        currentbufferedimage = new BufferedImage(loadedImage.getWidth(null), loadedImage.getHeight(null), BufferedImage.TYPE_INT_RGB);
//        Graphics2D g2 = currentbufferedimage.createGraphics();
//        g2.drawImage(loadedImage, 0, 0, null);
//        image = null;
//    }
//
//    public ArrayList createPDFArray() {
//        ImageIcon imageIcon = null;
//        ArrayList<BufferedImage> temp = new ArrayList<BufferedImage>();
//        PDFRenderer pdfRenderer = new PDFRenderer(curFile);
//        for (int pagenum = 0; pagenum < curFile.getNumberOfPages(); pagenum++) {
//            PDPage pdfPage = curFile.getPage(pagenum + 1);
//            Rectangle rect = new Rectangle(0, 0,
//                    (int) pdfPage.getBBox().getWidth(),
//                    (int) pdfPage.getBBox().getHeight());
//
//            //generate the image
////            Image current = pdfPage.getImage(
////                    rect.width, rect.height, //width & height
////                    rect, // clip rect
////                    null, // null for the ImageObserver
////                    true, // fill background with white
////                    true // block until drawing is done
////            );
//            
//          //generate the image
//            BufferedImage current = null;
//    		try {
//    			current = pdfRenderer.renderImage(pagenum);
//    		} catch (IOException e) {
//    			// TODO Auto-generated catch block
//    			e.printStackTrace();
//    		}
//            
//            imageIcon = new ImageIcon();
//            imageIcon.setImage(current);
//            Image tempImage = imageIcon.getImage();
//            BufferedImage tempBufferedImage = new BufferedImage(tempImage.getWidth(null), tempImage.getHeight(null), BufferedImage.TYPE_INT_RGB);
//            Graphics2D g2 = tempBufferedImage.createGraphics();
//            g2.drawImage(tempImage, 0, 0, null);
//            temp.add(tempBufferedImage);
//        }
//        return temp;
//    }

//    private void findOrientation() {
//        String imageOrientationArray[];
//        if (!currentScoutDetails.getImageOrientation().equalsIgnoreCase("null")) {
//            imageOrientationArray = currentScoutDetails.getImageOrientation().split("\\\\");
//            float _imgRowCosx = Float.parseFloat(imageOrientationArray[0]);
//            float _imgRowCosy = Float.parseFloat(imageOrientationArray[1]);
//            float _imgRowCosz = Float.parseFloat(imageOrientationArray[2]);
//            float _imgColCosx = Float.parseFloat(imageOrientationArray[3]);
//            float _imgColCosy = Float.parseFloat(imageOrientationArray[4]);
//            float _imgColCosz = Float.parseFloat(imageOrientationArray[5]);
//            orientationLabel = getOrientationLabelFromImageOrientation(_imgRowCosx, _imgRowCosy, _imgRowCosz, _imgColCosx, _imgColCosy, _imgColCosz);
//            if (orientationLabel.equalsIgnoreCase("CORONAL") || orientationLabel.equalsIgnoreCase("SAGITTAL")) {
//                isLocalizer = true;
//            }
//        }
//    }
//    
//    public String getOrientationLabelFromImageOrientation(double rowX, double rowY, double rowZ, double colX, double colY, double colZ) {
//        String label = null;
//        String ColumnRight = ImageOrientation.getOrientation(rowX, rowY, rowZ);
//        String rowDown = ImageOrientation.getOrientation(colX, colY, colZ);
//        String axis1 = ColumnRight.substring(0, 1);
//        String axis2 = rowDown.substring(0, 1);
//        /*
//         * please check strictly
//         */
////        if ((axis1 != null) && (axis2 != null)) {
////            if ((((axis1.equals("right"))) || (axis1.equals("left"))) && (((axis2.equals("anterior").substring(0, 1))) || (axis2.equals("posterior").substring(0, 1)))))) {
////                label = "AXIAL";
////            } else if ((((axis2.equals("right"))) || (axis2.equals("left")))) && (((axis1.equals("anterior").substring(0, 1))) || (axis1.equals("posterior").substring(0, 1)))))) {
////                label = "AXIAL";
////            } else if ((((axis1.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.right"))) || (axis1.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.left"))))) && (((axis2.equals("head").substring(0, 1))) || (axis2.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.foot").substring(0, 1)))))) {
////                label = "CORONAL";
////            } else if ((((axis2.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.right"))) || (axis2.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.left"))))) && (((axis1.equals("head").substring(0, 1))) || (axis1.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.foot").substring(0, 1)))))) {
////                label = "CORONAL";
////            } else if ((((axis1.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.anterior").substring(0, 1))) || (axis1.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.posterior").substring(0, 1))))) && (((axis2.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.head").substring(0, 1))) || (axis2.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.foot").substring(0, 1)))))) {
////                label = "SAGITTAL";
////            } else if ((((axis2.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.anterior").substring(0, 1))) || (axis2.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.posterior").substring(0, 1))))) && (((axis1.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.head").substring(0, 1))) || (axis1.equals(ApplicationContext.currentBundle.getString("ImageView.imageOrientation.foot").substring(0, 1)))))) {
////                label = "SAGITTAL";
////            }
////        } else {
////            label = "OBLIQUE";
////        }
//        return label;
//    }

//	/**
//	 * IMPORTTANT
//	 * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
//	 */
//	@Override
//	protected void paintComponent(Graphics g) {
////		super.paintComponent(g);//Needed?
//		Graphics2D g2d = (Graphics2D) g;
//		g2d.drawImage(this.img, originX, originY, null);
//	}

	public double getOriginalPixelSpacingX() {
		return getOriginalCalibration().pixelWidth;
	}

	public double getOriginalPixelSpacingY() {
		return getOriginalCalibration().pixelHeight;
	}

	public double getOriginalPixelSpacingZ() {
		return getOriginalCalibration().pixelDepth;
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

	public String getPixelSpacingUnit() {
		return getOriginalCalibration().getUnit();
	}

	public double getScaleFactor() {
		updateScale();
		return this.scale;
	}

	/*
	 * call when component resized.
	 */
	public void updateScale() {
		// DO NOT USE displayImp directly.
		if (pp == null) {
			return;
		}
		if (getWidth() < 1 || getHeight() < 1) {
			return;
		}
		Dimension d = calcImageSize2FitComponent();
		if (d != null) {
			if(header != null) {
				this.scale = (double) d.width / (double) header.getInt(Tag.Columns, getOriginalImage().getWidth());
			}else {
				this.scale = (double) d.width / (double)getOriginalImage().getWidth();
			}
		}
	}

	private void initPrapInfoLabel() {
		if (pp == null) {
			return;
		}
		pp.updateInfoLabel(-1, -1, null, this.scale, getMagnification(), getRotateAngle());
	}

	/*
	 * slideX: x on slideglass slideY: y on slideglass
	 */
	protected void updatePrapInfoLabel(int slideX, int slideY) {
		if (pp == null || pp.getViewMode()==ViewMode.Thumbnail) {
			return;
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
					pp.setAndShowPixelValue(imageX, imageY);
					logger.info("scaledOriginXY:" + scaledOriginX + " " + scaledOriginY);
					logger.info("slideXY:" + slideX + " " + slideY + " ,imageXY:" + imageX + " " + imageY);
				}
			}
		} else {
			/*
			 * pannされていない場合は、PrapView中心に、コンポーネントサイズにリサイズされた画像を表示する
			 */
			if (currentOrigin.x <= slideX && slideX < (currentOrigin.x + currentDimension.width)) {
				if (currentOrigin.y <= slideY && slideY < (currentOrigin.y + currentDimension.height)) {
					int dispImageX = slideX - currentOrigin.x;
					int dispImageY = slideY - currentOrigin.y;
					pp.setAndShowPixelValue(dispImageX, dispImageY);
					logger.info("originXY:" + currentOrigin.x + " " + currentOrigin.y);
					logger.info("slideXY:" + slideX + " " + slideY + " ,imageXY:" + dispImageX + " " + dispImageY);
				}
			}
		}
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

	/*
	 * return no calibrate val and calibrated val at displayImageX and displayImageY
	 * are coordinate on the display image.
	 * displayImageX and displayImageY are not slideX/Y.
	 * slideXY has praparat origin.
	 * displayImage have origin(0,0), but it was magnified and scaled.
	 */
	public Object[] getPixelValueFromDisplay(int displayImageX, int displayImageY) {
		if(!isRGB()) {
			double pix_raw = getCurrentDisplayImagePlus().getProcessor().get(displayImageX, displayImageY);
			double pix_cal = getCurrentDisplayImagePlus().getProcessor().getPixelValue(displayImageX, displayImageY);
			return new Double[] { pix_raw, pix_cal };
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

	public void setMagnification(double mag) {
		this.lastMagnification = getMagnification();
		this.magnification = mag;
		if (mag == 0.0d) {
			zoomFlag = false;
		} else {
			zoomFlag = true;
		}
	}

	public double getMagnification() {
		return this.magnification;
	}

	public boolean isZoomed() {
		return zoomFlag;
	}

	public void setInvertState(boolean invert) {
		invertFlag = invert;
	}

	public boolean isInverted() {
		return invertFlag;
	}
	
	public boolean isRGB() {
		return isRGB;
	}

	public void setFlipState(boolean flip) {
		flipFlag = flip;
	}

	public boolean isFlipped() {
		return flipFlag;
	}

	public void setWindowChanged(boolean changed) {
		windowing = changed;
	}

	public boolean isChangedWLWW() {
		return windowing;
	}

	public void autoWindow() {
		if(getOriginalImage() == null || getCurrentDisplayImagePlus() == null) {
			return;
		}
		if(isRGB()) {
			getOriginalImage().getProcessor().reset();
		}
		new ContrastEnhancer().stretchHistogram(getOriginalImage().getProcessor(), 0.5);
		this.currentMin = getOriginalImage().getProcessor().getMin();// DO NOT USE getMinThreshold()
		this.currentMax = getOriginalImage().getProcessor().getMax();// DO NOT USE getMaxThreshold()
		imgProcess.windowing(displayImp, this.currentMin, this.currentMax);
		repaint();
	}

	public void changeWindow(int WL, int WW) {
		double newMin = WL - (.5 * WW);
		double newMax = WL + (.5 * WW);
		if (newMin > newMax) {
			logger.log(Level.SEVERE,"SlideGlass::changeWindow() problem occured :" + newMin + " " + newMax);
		}
		
//		lastMin = currentMin;//DO NOT SET HERE, see mouse enter 
//		lastMax = currentMax;//DO NOT SET HERE, see mouse enter 
		currentMin = newMin;
		currentMax = newMax;
		logger.info("change ww/wl : newMin "+newMin+" newMax "+newMax);
		imgProcess.windowing(displayImp, newMin, newMax);
//		displayImp.setDisplayRange(newMin, newMax);
//		if(!isRGB()) {
//			displayImp.updateImage();// IMPORTANT
//		}
		repaint();
	}

	void adjustWindow2Current() {
		if (currentMax == -1 || currentMin == -1) {
			return;
		}
		setWindowChanged(true);
		// https://imagej.nih.gov/ij/plugins/window-level-tool/Window_Level_Tool.java
		// current settings
		double currentWindow = currentMax - currentMin;
		double currentLevel = currentMin + (.5 * currentWindow);
		changeWindow((int) currentLevel, (int) currentWindow);
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
		changeWindow((int) newLevel, (int) newWindow);
	}

	public void adjustWindowFromMouseAction(int locX, int locY) {
		double minMaxDifference = getCurrentDisplayImagePlus().getDisplayRangeMax()
				- getCurrentDisplayImagePlus().getDisplayRangeMin();
		int xDiff = locX - this.lastX;
		int yDiff = locY - this.lastY;
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

	/**
	 * 
	 * @param magnification : zoom scale(0.2<, <7.0)
	 */
	void zoom(double mag) {
		// set magnification min max
		if (mag < 0.2) {
			mag = 0.2;
		} else if (mag > 7.0) {
			mag = 7.0;
		}
		// save last mag
		setMagnification(mag);
//    	System.out.println("Prev mag:"+lastMagnification+" "+"New mag:"+ mag);
		displayCurrentStateImage();

		// TODO
//    	if(mag != 1.0 && !panningFlag) {
//    		panningFlag = true;//because, image origin shifted by focuse zoom.
//    	}

		// adjust image origin, keep last pressed point(on screen) to center
//    	スクリーン中心
//    	int screenCX = (getWidth() / 2)-1;
//    	int screenCY = (getHeight() /2)-1;
////		スクリーン中心までの距離
//		int diffCX = lastX - screenCX;
//		int diffCY = lastY - screenCY;
//		System.out.println("ScreenCenter distance:" + diffCX + " " + diffCY);
//    	double moveX = 0;
//		double moveY = 0;
//    	if(prevImg.getWidth() >= zoomed.getWidth()) {
//			moveX = (prevImg.getWidth() - zoomed.getWidth())/2;
//			moveY = (prevImg.getHeight() - zoomed.getHeight())/2;
//    	}else {
//    		moveX = (-1 * (prevImg.getWidth() - zoomed.getWidth())/2);
//			moveY = (-1 * (prevImg.getHeight() - zoomed.getHeight())/2);
//    	}
//    	System.out.println("Move mount X:"+moveX+" Y:"+moveY);
//    	moveX = moveX + diffCX*mag + lastOriginX;
//    	moveY = moveY + diffCY*mag + lastOriginY;
//    	System.out.println("Move mount X:"+moveX+" Y:"+moveY);
//		panning((double)moveX,(double)moveY);
	}

//    private void zoom() {
//    	//create reference image (component size).
//    	ImagePlus rep = getReplicaFreshCopy(isFlipped(),false, rotatedFlag, windowing);//zoom,rotate,window
//    	int refW = rep.getWidth();
//    	int refH = rep.getHeight();
//    	//zoom
//    	double mag = getMagnification();
//    	ImageProcessor zoomed = rep.getProcessor().resize((int)(refW*mag), (int)(refH*mag));
//    	displayImp = new ImagePlus("", zoomed);//necessary, reset image
//    	displayImp.setCalibration(originalCal);
////    	replica.updateImage();
////    	updateGlass();
//    	repaint();
//    }

	/*
	 * パンニングは単純に表示画像原点位置の移動。 Zoomやスケールは無視。
	 */
	void panning(double moveX, double moveY) {
		if (panningFlag && !panningInAction) {
			// pann状態の場合、先に原点をスケールさせる
			this.originX = (int) (lastOriginX * getScaleFactor()) - (int) moveX;
			this.originY = (int) (lastOriginY * getScaleFactor()) - (int) moveY;
			lastOriginX = originX;
			lastOriginY = originY;
		} else {
			this.originX = this.lastOriginX - (int) moveX;
			this.originY = this.lastOriginY - (int) moveY;
		}
		if(Utils.isDebug) logger.info("Panning : originX " + originX + " ," + " originY " + originY);
		panningFlag = true;// fail safe
		panningInAction = true;// 敢えてここでハンドリングする
//		System.out.println("panning in action "+panningInAction);
		updatePanningState();
		repaint();
	}

	private void updatePanningState() {
		Dimension d = calcImageSize2FitComponent();
		if (d == null) {
			return;
		}
		Point defOrigin = calcDefaultImageOrigin(d.width, d.height, getWidth(), getHeight());
		if ((this.originX == defOrigin.x) && (this.originY == defOrigin.y)) {
			panningFlag = false;
		} else {
			panningFlag = true;
		}
	}

	protected void releasePanning() {
		/*
		 * pann中はscalingOriginが基本。 pann中はスケールさせずに表示するが、あくまでもスケールしている見せかけの状態。
		 * pann操作後、ずれを修正しておく。
		 */
//		System.out.println("Released Panning pre:"+originX+"  "+originY);
		if (panningInAction) {
			// https://stackoverflow.com/questions/2654839/rounding-a-double-to-turn-it-into-an-int-java
			double reverseToNoScaleOriginX = originX / getScaleFactor();
			double reverseToNoScaleOriginY = originY / getScaleFactor();
			if (reverseToNoScaleOriginX >= 0) {
				originX = (int) (reverseToNoScaleOriginX + 0.5);
			} else {
				originX = (int) (reverseToNoScaleOriginX - 0.5);
			}
			if (reverseToNoScaleOriginY >= 0) {
				originY = (int) (reverseToNoScaleOriginY + 0.5);
			} else {
				originY = (int) (reverseToNoScaleOriginY - 0.5);
			}
			// update lastOrigin
			lastOriginX = originX;
			lastOriginY = originY;
		}
		this.panningInAction = false;
		System.out.println("panning released, in action ? " + panningInAction);
	}

	void rotate(int changeAngle) {

		double willRotateAngle = getRotateAngle() + changeAngle;
		if (willRotateAngle >= 360) {
			willRotateAngle = willRotateAngle - 360;
		} else if (willRotateAngle <= -360) {
			willRotateAngle = willRotateAngle + 360;
		}
		setRotateAngle((int) willRotateAngle);
		displayCurrentStateImage();
		setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
	}

	private void displayCurrentStateImage() {
		this.displayImp = getCurrentStateImageFreshCopy();
		if (!panningFlag) {
			calcDefaultImageOriginAndReset(getCurrentDisplayImagePlus().getWidth(),
					getCurrentDisplayImagePlus().getHeight(), getWidth(), getHeight());
		}
		setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		repaint();
	}

	public int getRotateAngle() {
		return currentRotateAngle;
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

	private boolean isRotated() {
		return rotatedFlag;
	}

	public ImagePlus getCurrentDisplayImagePlus() {
		return this.displayImp;
	}

	public void invert() {
		if (isInverted()) {
			setInvertState(false);
		} else {
			setInvertState(true);
		}
		imgProcess.invert(displayImp);
//		displayImp.getProcessor().invert();
//		displayImp.updateImage();
		TextOverlayGlass tg = (TextOverlayGlass)getGlassAt(TEXT_LAYER);
		tg.setInvertState(this.invertFlag);
		repaint();
	}

	public void flipLR() {
		if (isFlipped()) {
			setFlipState(false);
		} else {
			setFlipState(true);
		}
		displayImp.getProcessor().flipHorizontal();
		displayImp.updateImage();
		repaint();
	}

	public void flipHF() {
		rotate(180);// to avoid flipFlag mismatch, run first.
		flipLR();
		displayImp.updateImage();
		repaint();
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
	
	public ImagePlus cropRect(RoiObj rectRoi) {
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
	 * 
	 * @param roi
	 */
	public void cut(RoiObj roi) {
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
		imp.getProcessor().set(0);
		setOriginalImage(imp);
		repaint();
	}

	public void setLUT(LUT lut) {
		if (invertFlag) {
			// do nothing
		} else {
			// do nothing
		}
		this.currentLUT = lut;
		displayImp.setLut(currentLUT);
		displayImp.updateImage();
		repaint();
	}

	public void reset() {
		initDisplayImage();
		// reset window range
//		this.currentMin = this.replica.getDisplayRangeMin();
//		this.currentMax = this.replica.getDisplayRangeMax();
		// reset manification
		setMagnification(1.0d);
		// reset rotate angle
		setRotateAngle(0);
		// set image position for origin and resize
//		Dimension d = calcImageSize4FitComponent(original.getWidth(), original.getHeight());//initReplica
//		replica.getProcessor().resize(d.width, d.height);//initReplica
		setFlipState(false);
		zoomFlag = false;
		windowing = false;
		rotatedFlag = false;
		panningFlag = false;
		panningInAction = false;
		originX = 0;
		originY = 0;
		calcDefaultImageOriginAndReset(displayImp.getWidth(), displayImp.getHeight(), getWidth(), getHeight());
		initRoiSet();
		repaint();
	}

//	public ArrayList<RoiObj> getRoiSet() {
//		return this.roiset;
//	}
	
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
	
	private void deleteRoiFromDB(RoiObj roi) {
		String patID = roi.getPatientID();
		String studyUid = roi.getStudyUID();
		String seriesUid = roi.getSeriesUID();
		String sopUid = roi.getSopUID();
		String roiId = roi.getPropertyAt(RoiObj.RoiContextKeySet.RoiID.name());
		Viewer2DScreen.getInstance().getDatabase().deleteRoi(patID, studyUid, seriesUid,sopUid,roiId);
		if(Viewer2DScreen.getRoiObjManager() != null) {
			RoiObjManager rom = Viewer2DScreen.getRoiObjManager();
			rom.updateRoiObjList(getPatientID());
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

	private void initRoiSet() {
		roiset = null;
		roiset = new ArrayList<RoiObj>();
	}

	public ArrayList<RoiObj> getRois() {
		return this.roiset;
//    	return pp.getRoiAt(header.getString(Tag.SOPInstanceUID));
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
	
	public void saveCurrentRoiSate() {
		RoiObj roi = findCurrentRoi();
		insertOrUpdateRoi4DB(roi);
	}
	
	public void insertOrUpdateRoi4DB(RoiObj roi) {
		if(roi == null) {
			return;
		}
		//save as new or update
		if(Viewer2DScreen.getInstance().getDatabase() != null) {
			Viewer2DScreen.getInstance().getDatabase().insertRoi(roi.readContext());
		}
	}
	
	public void loadRoiFromDB() {
//		Viewer2DScreen viewer = Viewer2DScreen.getInstance();
//		if (viewer == null){
//			return;//maybe, never be null, but fail safe. for mpr view
//		}
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
	
	public void setRoiBrush(RoiObj brush) {
		roiOverlay.setBrush(brush);
	}
	
	public RoiPopupDialog getRoiPopupFromRoiAt(int slideX, int slideY) {
		RoiObj roi = roiOverlay.activateAndGetRoiAt(slideX, slideY);
		if (roi == null) {
			return null;
		} else {
			return roi.getRoiPopupDialog();
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

	public void setVisibleRoiPopupAt(boolean show, int slideX, int slideY) {
		RoiPopupDialog rpd = getRoiPopupFromRoiAt(slideX, slideY);
		if (rpd == null) {
			return;
		} else {
			rpd.setVisible(show);
		}
	}
	
	public boolean isHereRoiPopup(MouseEvent e) {
		Object obj = e.getSource();
		if(obj instanceof RoiPopupDialog) {
			return true;
		}else {
			return false;
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

	public void showRoiPopupOf(RoiObj roi) {
		if (roi == null) {
			return;
		}
		if (roi instanceof TextRoi) {
			return;
		}
		roi.showRoiPopupOnCanvas();
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

	public void removeRoiPopupDialogOnCanvas(RoiPopupDialog rpd) {
		if (rpd != null && roiOverlay != null) {
			roiOverlay.remove(rpd);
			roiOverlay.revalidate();// no need, but maybe fail safe
			roiOverlay.repaint();
			repaint();
		}
	}
		
	public ReferenceLine getReferenceLine() {
		if(pp != null) {
			return pp.getReferenceLine();
		}else {
			return null;
		}
	}
	
	/**
	 * null-able.
	 * if set to null, stop displaying localizer.
	 * @param localizer
	 */
	public void drawLocalizer(java.util.List<java.awt.geom.Point2D> localizerGeo) {
		roiOverlay.setLocalizerGeometry(localizerGeo);
	}
	
	public void drawCross(MouseEvent e) {
		Point currentScreenPos = e.getPoint();
		GeneralPath path = new GeneralPath();
    	int sx = currentScreenPos.x;
    	int sy = currentScreenPos.y;
        path.moveTo(0f, sy);
        path.lineTo(slide.getWidth(), sy);
        path.moveTo(sx, 0f);
        path.lineTo(sx, slide.getHeight());
        roiOverlay.setCrossLine(path);
        //do not return
        repaint();
    }
	
	public void drawCross(Point onOrgImageCoordinatePoint) {
    	GeneralPath path = new GeneralPath();
    	int sx = screenX(onOrgImageCoordinatePoint.x);
    	int sy = screenY(onOrgImageCoordinatePoint.y);
        path.moveTo(0f, sy);
        path.lineTo(slide.getWidth(), sy);
        path.moveTo(sx, 0f);
        path.lineTo(sx, slide.getHeight());
        roiOverlay.setCrossLine(path);
        //do not return
        repaint();
    }

	// see, TestRoi2.java
	public int getCurrentModifiersEx() {
		return mouseActionFlag;
	}

	public void handleRoiMousePressed(MouseEvent me) {
		mouseActionFlag = me.getModifiersEx();// future work change to getButton()? related getModifiersEx...but buggy??
//		roiOverlay.handleRoiMouseDown(me);
		roiOverlay.mousePressed(me);
	}

	public void handleRoiMouseMoved(MouseEvent me) {
		mouseActionFlag = me.getModifiersEx();
		roiOverlay.mouseMoved(me);
	}

	public boolean handleRoiMouseDragged(MouseEvent me) {
		if(pp.isShowCrossLineMode()) {
			drawCross(me);
		}
		mouseActionFlag = me.getModifiersEx();
		return roiOverlay.handleRoiMouseDragged(me, this);
	}

	public void handleRoiMouseUp(MouseEvent me) {
		mouseActionFlag = me.getModifiersEx();
		roiOverlay.mouseReleased(me);// (me, x, y);
	}

	public void updateRoiCanvas() {
		roiOverlay.repaint();// show roi
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (panningFlag) {
			/*
			 * pannされている場合は、pann済みのオリジンにスケールをかけて表示位置を補正する
			 */
			if (panningInAction) {
				imageSpecimen.updateImage(originX, originY, getCurrentDisplayImagePlus());
			} else {
				imageSpecimen.updateImage(originX, originY, getScaleFactor(), getCurrentDisplayImagePlus());
			}
		} else if (!panningFlag && !panningInAction) {
			/*
			 * pannされていない場合は、PrapView中心に、コンポーネントサイズにリサイズされた画像を表示する
			 */
			imageSpecimen.updateImage(originX, originY, getCurrentDisplayImagePlus());
		}
		textOverlay.repaint();
		updateRoiCanvas();// show roi
	}
}
