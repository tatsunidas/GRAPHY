package com.vis.core.view.D2.roi;

import ij.*;
import ij.gui.RoiListener;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.ThresholdToSelection;
import java.awt.*;
import java.util.*;

import javax.swing.JComponent;
import java.text.SimpleDateFormat;
import java.awt.image.*;
import java.lang.reflect.Field;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.*;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.view.D2.ui.glasses.*;
/** 
 * A rectangular region of interest and superclass for the other ROI classes. 
 * 
 * This class implements {@code Iterable<Point>} and can thus be
 * used to iterate over the contained coordinates. Usage example: 
 * <pre>
 * Roi roi = ...;
 * for (Point p : roi) {
 *   // process p
 * }
 * </pre>
 */
@SuppressWarnings("serial")
public class RoiObj extends Object implements Cloneable, java.io.Serializable, Iterable<Point> {

	// States
	public static final int CONSTRUCTING=0;
	public static final int MOVING=1;
	public static final int RESIZING=2;
	public static final int NORMAL=3;
	public static final int MOVING_HANDLE=4; 
	// Types
	public static final int RECTANGLE=0;
	public static final int OVAL=1;
	public static final int POLYGON=2;
	public static final int FREEROI=3;
	public static final int TRACED_ROI=4;
	public static final int LINE=5;//see, isLine()
	public static final int POLYLINE=6;//see, isLine()
	public static final int FREELINE=7;//see, isLine()
	public static final int ANGLE=8;
	public static final int COMPOSITE=9;
	public static final int POINT=10; 
	public static final int ARROW=11;
	public static final int TEXT=12;
	public static final int NOTYPE = -99;
	
	/*
	 * TODO, replace it
	 */
	public enum RoiType{
		RECTANGLE("",0),
		OVAL("",1),
		POLYGON("",2);
		//add more
		
		private String roi_type;
		private int roi_id;
		
		private RoiType(String name, int id) {
			roi_type = name;
			roi_id = id;
		}
		
		public String type() {
			return roi_type;
		}
		
		public int id() {
			return roi_id;
		}
		
		public String findType(int id) {
			for(RoiType rt : RoiType.values()) {
				if(rt.id() == id) {
					return rt.type();
				}
			}
			return null;
		}
	}
	
	//other
	public static final int HANDLE_SIZE = 5; 
	public static final int NOT_PASTING = -1;
	// modification states
	public static final int NO_MODS=0;
	public static final int ADD_TO_ROI=1;
	public static final int SUBTRACT_FROM_ROI=2; 
	
	/*
	 * 座標系やピクセルサイズはoriginal imageに準ずる
	 */
	public int startX, startY;//original image based origin at mouse pressed (no lastXY mouse)
	public double startXD, startYD;//original image based origin at mouse pressed  (no lastXY mouse)
	public int x, y;//current original image based origin
	protected int width, height;
	int previousX;//on imageX
	int previousY;//on imageY
	int previousSX;//on slideX
	int previousSY;//on slideY
	java.awt.geom.Rectangle2D.Double bounds;
	int activeHandle;
	int state = NORMAL;
	int modState = NO_MODS;
	int cornerDiameter;
	
	/** Get using getPreviousRoi() and set using setPreviousRoi() */
	public static RoiObj previousRoi;
	
    private static LUT glasbeyLut;
    private static int defaultGroup; // zero is no specific group
    private static Color groupColor;
    private static Double defaultStrokeWidth = 1.5;
    private static String groupNamesString = null;
    private static String[] groupNames;
    private static boolean groupNamesChanged;
	
	public static final BasicStroke onePixelWide = new BasicStroke(1);
	
	/*
	 * lineWidth is used to for Line, FreehandRoi, PolygonRoi.
	 * but, getStrokeWidth was used as usual.
	 */
	protected static int lineWidth = 1;//keep static
	protected static Color ROIColor = Color.orange; //default Roi stroke color
	protected static Color defaultFillColor = Color.white;
	protected static Color defaultHandleColor = Color.yellow;
	
	protected static int pasteMode = Blitter.COPY;
	private static Vector<RoiListener> listeners = new Vector<RoiListener>();
	
	protected SlideGlass slide;
	
	protected int type;
	protected int xMax, yMax; //original img WH
	protected ImagePlus imp;//original imp (no display imp)
	private int imageID;
	protected int oldX, oldY, oldWidth, oldHeight;
	protected int clipX, clipY, clipWidth, clipHeight;
	protected ImagePlus clipboard;
	protected boolean constrain; // to be square
	protected boolean center;
	protected boolean aspect;
	protected boolean updateFullWindow;
	protected double mag = 1.0;
	protected double asp_bk; //saves aspect ratio if resizing takes roi very small
	protected ImageProcessor cachedMask;
	protected Color handleColor = Color.white;
	protected Color strokeColor;
	protected Color instanceColor; //obsolete; replaced by	strokeColor
	protected Color fillColor;
	protected BasicStroke stroke;
	protected boolean nonScalable;
	protected boolean overlay;
	protected boolean wideLine;
	protected boolean ignoreClipRect;
	protected double flattenScale = 1.0;

//	private String name;
    private int position;
    private int channel, slice, frame;
    private boolean hyperstackPosition;
//    private Overlay prototypeOverlay;
    private boolean subPixel;
    private boolean activeOverlayRoi;
    private Properties props;
    private boolean isCursor;
    protected double xcenter = Double.NaN;
    protected double ycenter;
    private boolean listenersNotified;
    private boolean antiAlias = true;
    private int group = -1;
    private boolean usingDefaultStroke;
    private static int defaultHandleSize;
    private int handleSize = -1;    
//    private boolean scaleStrokeWidth; // Scale stroke width when zooming images
	
//	ij.gui.Roi roi;//for processing
//	private boolean global = true;//series roi//一旦、なしに。
	/*
	 * multiframe roi is seems same as global roi,
	 * but, that is only situation that no more hierarchy of multiframes(dimensions).
	 * So, I remain multiframe variables.
	 */
	private boolean multiframe = false;//for multiframe
	private boolean isVisible = false;
	
	private RoiPopupDialog rpd;
	private boolean showRoiPopup = true;
	
	// property keys
	// if you want type, -> int type = roi.getType();
	
	/*
	 * how to 
	 * String :: KeySet.PatientID.name()
	 * 
	 * see also ROI.sql
	 */
	public enum RoiContextKeySet {
		Name,
		PatientID, //string
		StudyInstanceUID, //string
		SeriesInstanceUID, //string
		SOPInstanceUID, //string
		InstanceNo,//int //this is NOT slice position!! //int
		RoiType, //int
		RoiID, //RoiID string
		RoiGroup, //int
		RoiLabel, //string
//		Multiframe,
		ObjectType,//string target object type, e.g., target lesions.
		Organ,//string
		Description;//for textroi and any context.string

		@Override
		public String toString() {
			return this.name();
		}
	}

	/** Creates a new rounded rectangular ROI. */
	/*
	 * new RoiObj(x,y,w,h,0);
	 * w and h are can be 0.
	 * keep original image based.
	 */
	public RoiObj(int x, int y, int width, int height, int cornerDiameter, SlideGlass slide) {
		if(slide != null) {
			setSlideGlass(slide);//and set props
		}
		rpd = new RoiPopupDialog(slide, this);//remain ?
		if (width<1) width = 1;
		if (height<1) height = 1;
		if(slide != null) {
			if (width>xMax) width = xMax;
			if (height>yMax) height = yMax;
		}
		this.cornerDiameter = cornerDiameter;//for rounded rectangle
		this.x = x;
		this.y = y;
		startX = x; startY = y;
		oldX = x; oldY = y;
		this.width = width;
		this.height = height;
		oldWidth=width;
		oldHeight=height;
		clipX = x;
		clipY = y;
		clipWidth = width;
		clipHeight = height;
		state = NORMAL;
		type = RECTANGLE;
		loadSettings();
		//set double handling
		bounds = new java.awt.geom.Rectangle2D.Double(x, y, width, height);
		this.subPixel = true;
	}

	/** Creates a new rectangular Roi. */
	public RoiObj(Rectangle r,SlideGlass slide) {
		this(r.x, r.y, r.width, r.height,0,slide);
	}
	
	private void loadSettings() {
		/*
		 * "RoiStrokeColor"
		 * "RoiStrokeWidth"
		 * "RoiFillColor"
		 * "RoiHandleColor"
		 * "RoiBrushSize"
		 * "RoiBrushType"
		 */
		//strokeColor;
		String strokeColorString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiStrokeColor);
		this.strokeColor = colorFromString(strokeColorString, ROIColor);
		//fillColor;
		String fillColorString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiFillColor);
		this.fillColor = colorFromString(fillColorString, defaultFillColor);
		//handleColor
		String handleColorString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiHandleColor);
		this.handleColor = colorFromString(handleColorString, defaultHandleColor);
		//
		String lineWidthString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiStrokeWidth);
		if(lineWidthString != null) {
			setStrokeWidth(Double.parseDouble(lineWidthString.trim()));
		}
	}
	
	public Color colorFromString(String colorName, Color backup) {
		if(colorName == null) {
			return ROIColor;
		}
		Color color = null;;
		try {
			java.lang.reflect.Field field = Class.forName("java.awt.Color").getField(colorName.trim());
		    color = (Color)field.get(null);
		} catch (Exception e) {
		    color = null; // Not defined
		}
		if(color == null) {
			System.out.println("Can not load roi color, use default...");
			return backup;
		}else {
			return color;
		}
	}
	
	public String findColorNameByColor(Color c) {
		String candidateColorName = null;
		int rgbDistance = -1;
		for(Field f : Color.class.getFields()) {
			Color sys_c = null;
			try {
				sys_c = (Color) f.get(null);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
			if(sys_c == null) {
				continue;
			}
			int dif_r = Math.abs(c.getRed() - sys_c.getRed());
			int dif_g = Math.abs(c.getGreen() - sys_c.getGreen());
			int dif_b = Math.abs(c.getBlue() - sys_c.getBlue());
			int sum = dif_r+dif_g+dif_b;
			if(sum == 0) {
				return f.getName().trim().toLowerCase();
			}else {
				if (rgbDistance == -1) {
					rgbDistance = sum;
					candidateColorName = f.getName().trim().toLowerCase();
				}
				if(rgbDistance > sum) {
					rgbDistance = sum;
					candidateColorName = f.getName().trim().toLowerCase();
				}
			}
		}
		return candidateColorName;
	}
		
	public String getPatientID() {
		return getProperty(RoiContextKeySet.PatientID.name());
	}
	
	public String getStudyUID() {
		return getProperty(RoiContextKeySet.StudyInstanceUID.name());
	}
	
	public String getSeriesUID() {
		return getProperty(RoiContextKeySet.SeriesInstanceUID.name());
	}
	
	public String getSopUID() {
		return getProperty(RoiContextKeySet.SOPInstanceUID.name());
	}
	
	/*
	 * same as frame no.
	 */
	public int getInstanceNo() {
		String num = getProperty(RoiContextKeySet.InstanceNo.name());
		return Integer.parseInt(num);
	}
	
	public String getPropertyAt(String roiContextKey) {
		for(RoiContextKeySet k : RoiContextKeySet.values()) {
			if(k.name().equals(roiContextKey)) {
				return getProperty(roiContextKey);
			}
		}
		return null;
	}
	
//	public void setGlobal(boolean global){
//    	this.global = global;
//    }
//    
//    public boolean isGlobal() {
//    	return this.global;
//    }
    
    public void setMultiframe(boolean multiframe){
    	this.multiframe = multiframe;
    }
    
    public boolean isMultiframe() {
    	return this.multiframe;
    }
    
    public void addProperty(String key, String value) {
		if (checkPropertyKey(key)) {
			setProperty(key, value);
		} else {
			try {
				throw new Exception();
			} catch (Exception e) {
				System.err.println("RoiObj2:addProperty()::Catch invalid key type. return.");
				e.printStackTrace();
				return;
			}
		}
	}
    
    private boolean checkPropertyKey(String key) {
    	for(RoiContextKeySet k : RoiContextKeySet.values()) {
    		String stringKey = k.name();
    		if(stringKey.equals(key)) {
    			return true;
    		}
    	}
    	return false;
    }
    
    public void setRoiLabel(String name) {
    	if(name == null) {
    		return;
    	}
    	setProperty(RoiContextKeySet.RoiLabel.name(), name);
    }
    
    private void setBasicProps(SlideGlass slide) {
    	String pid = slide.getPatientID();
    	String studyUID = slide.getStudyInstanceUID();
    	String seriesUID = slide.getSeriesInstanceUID();
    	String sopUID = slide.getSOPInstanceUID();
    	String instNo = String.valueOf(slide.getInstanceNo());
    	setBasicProps(pid, studyUID, seriesUID, sopUID, instNo);
    }
    
    private void setBasicProps(String pid, String studyUID,String seriesUID,String sopUID, String instNo) {
    	setProperty(RoiContextKeySet.PatientID.name(), pid);
    	setProperty(RoiContextKeySet.StudyInstanceUID.name(), studyUID);
		setProperty(RoiContextKeySet.SeriesInstanceUID.name(), seriesUID);
		setProperty(RoiContextKeySet.SOPInstanceUID.name(), sopUID);
		setProperty(RoiContextKeySet.InstanceNo.name(), instNo);
//		setProperty(RoiContextKeySet.RoiID.name(), createRoiIndex());//always created new roiId //DO NOT DO THIS
		/*
		 * roi id created only initialization.
		 */
		if(getPropertyAt(RoiContextKeySet.RoiID.name()) == null || getPropertyAt(RoiContextKeySet.RoiID.name()).trim().equals("")) {
			setProperty(RoiContextKeySet.RoiID.name(), createRoiIndex());
		}
    }
    
    /*
     * see also Overrided another Roi.
     */
    public HashMap<String, Object> readContext(){
    	HashMap<String,Object> con = new HashMap<>();
    	//basicなものはいったんStringに
    	for(RoiContextKeySet k:RoiContextKeySet.values()) {
    		con.put(k.name(), getPropertyAt(k.name()));
    	}
    	if(getType()==POLYGON || getType()==ANGLE) {
			con.put("OriginX", x);
			con.put("OriginY", y);
    	}else {
			con.put("OriginX", (int) getXBase());// for line roi.
			con.put("OriginY", (int) getYBase());
    	}
    	con.put("Width", width);
    	con.put("Height", height);
    	/*
    	 * LINE, ARROW are overrided.
    	 */
    	if(getType()==POLYGON || getType()==ANGLE) {
			con.put("PointX", fArray2dArray(getFloatPolygon().xpoints));
			con.put("PointY", fArray2dArray(getFloatPolygon().ypoints));
    	}else {//Rect, Oval, Point
			con.put("PointX", null);
			con.put("PointY", null);
    	}
    	//shape,see also ShapeRoi::roiToShape(RoiObj roi)
    	con.put("Shape", null);
    	
    	return con;
    }
    
    protected double[] fArray2dArray(float[] floatp) {
    	if (floatp == null) {
    		return null;
    	}
    	double[] da = new double[floatp.length];
    	for(int i=0;i<floatp.length; i++) {
    		da[i] = (double)floatp[i];
    	}
    	System.out.println(da.length);
    	return da;
    }
    
    private String createRoiIndex() {
    	SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
    	Date now = new Date();
    	int hash = new Random().hashCode();
    	return f.format(now)+"_"+hash;
    }
    
    public boolean isThisRoi(String studyUID,String seriesUID,String sopUID,String roiInd) {
    	String uid1 = getStudyUID();
    	String uid2 = getSeriesUID();
    	String uid3 = getSopUID();
    	String index = getProperty(RoiContextKeySet.RoiID.name());
    	if(uid1 == null || uid2 == null || uid3 == null) {
    		return false;
    	}
    	if(uid1.equals(studyUID) && uid2.equals(seriesUID) && uid3.equals(sopUID) && index.equals(roiInd)) {
    		return true;
    	}else {
    		return false;
    	}
    }
    
    public RoiPopupDialog getRoiPopupDialog() {
    	return rpd;
    }
    
    public void setRoiPopupDialog(RoiPopupDialog rpd) {
    	rpd.setSlideGlass(slide);
    	rpd.setRoi(this);
    	this.rpd = rpd;
    }
    
    public void setRoiPopupVisible(boolean show) {
    	this.showRoiPopup = show;
    }
    
    public void showRoiPopupOnCanvas() {
    	if(slide == null) {
    		return;
    	}
    	if(!showRoiPopup) {
    		return;
    	}
    	CanvasGlass roiCanvas = (CanvasGlass) slide.getGlassAt(slide.ROI_CANVAS_LAYER);
    	/*
    	 * set default location
    	 */
    	double screenScale = slide.getMagnification() * slide.getScaleFactor();
    	int sx = 0;
    	int sy = 0;
    	if(!(this instanceof ShapeRoi)) {
    		sx = (int)(slide.screenXD(getBounds().x));// + getBounds().width*screenScale);
        	sy = (int)(slide.screenYD(getBounds().y) + getBounds().height*screenScale);
    	}else {
    		sx = (int)(slide.screenXD(getBounds().x));// + getBounds().width*screenScale);
        	sy = (int)(slide.screenYD(getBounds().y) + height*screenScale);
    	}
    	putRoiPopup(roiCanvas, sx, sy);
    	setVisibleRoiPopup(true);
    	slide.repaint();
    }
    
    public void showRoiPopupOnCanvasAt(int sx, int sy) {
    	CanvasGlass roiCanvas = (CanvasGlass) slide.getGlassAt(slide.ROI_CANVAS_LAYER);
    	putRoiPopup(roiCanvas, sx, sy);
    	setVisibleRoiPopup(true);
    	slide.repaint();
    }
    
    public void putRoiPopup(JComponent panel, int sx, int sy) {
    	if(rpd == null) {
			return;
		}
    	Component[] roiPopups = panel.getComponents();
    	//check already in roiOverlay
    	boolean exist = false;
    	for(Component com:roiPopups) {
    		if(com != null && com == rpd) {
    			exist = true;
    			break;
    		}
    	}
    	if(!exist) {
    		rpd.setLocation(sx, sy);
			panel.add(rpd);
			panel.revalidate();
			panel.repaint();
    	}else {
    		//if exists, update location
    		double screenScale = slide.getMagnification() * slide.getScaleFactor();
    		int scaleX = (int)(slide.screenXD(getBounds().x));// + getBounds().width*screenScale);
        	int scaleY = (int)(slide.screenYD(getBounds().y) + getBounds().height*screenScale);
    		rpd.setLocation(scaleX, scaleY);
    		panel.revalidate();
			panel.repaint();
    	}
    }
    
    public void setBasicStatistics2Popup() {
    	if(getState() == CONSTRUCTING) {
    		return;
    	}
    	if(getType() == ARROW || getType() == TEXT || getType()==NOTYPE) {
    		return;
    	}
    	if(rpd == null) {
    		return;
    	}
    	rpd.setBasicStats();
    }
    
    public void setVisibleRoiPopup(boolean show) {
		if(rpd == null || !rpd.roiAlive()) {
			return;
		}
		if(getType() != TEXT && getType() != ARROW) {
			rpd.setVisible(show);
		}else {
			rpd.setVisible(false);
		}
	}

//	/** Set the location of the ROI in image coordinates. */
//	public void setLocation(int x, int y) {
//		this.x = x;
//		this.y = y;
//		startX = x; startY = y;
//		oldX = x; oldY = y; oldWidth=0; oldHeight=0;
//		if (bounds!=null) {
//			bounds.x = x;
//			bounds.y = y;
//		}
//	}
//	
//	/** Set the location of the ROI in image coordinates. */
//	public void setLocation(double x, double y) {
//		setLocation((int)x, (int)y);
//		//whether a number is an integer
//		if ((int)x==x && (int)y==y)
//			return;
//		if (bounds!=null) {
//			bounds.x = x;
//			bounds.y = y;
//		} else {
//			bounds = new Rectangle2D.Double(x, y, width, height);
//		}
//	}
	
	/** Set the location of the ROI in image coordinates. */
	public void setLocation(int x, int y) {
		this.x = x;
		this.y = y;
		startX = x; startY = y;
		oldX = x; oldY = y; oldWidth=0; oldHeight=0;
		if (bounds!=null) {
			if (!isInteger(bounds.x) || !isInteger(bounds.y)) {
				cachedMask = null;
				width  = (int)Math.ceil(bounds.width);
				height = (int)Math.ceil(bounds.height);
			}
			bounds.x = x;
			bounds.y = y;
			if (this instanceof PolygonRoi) setIntBounds(bounds);
		}
	}

	/** Set the location of the ROI in image coordinates. */
	public void setLocation(double x, double y) {
		setLocation((int)x, (int)y);
		if (isInteger(x) && isInteger(y))
			return;
		if (bounds!=null) {
			if (!isInteger(x-bounds.x) || !isInteger(y-bounds.y)) {
				cachedMask = null;
				width  = (int)Math.ceil(bounds.x + bounds.width) - this.x;	//ensure that all pixels are inside
				height = (int)Math.ceil(bounds.y + bounds.height) - this.y;
			}
			bounds.x = x;
			bounds.y = y;
		} else {
			cachedMask = null;
			bounds = new Rectangle2D.Double(x, y, width, height);
		}
		if (this instanceof PolygonRoi) setIntBounds(bounds);
		subPixel = true;
	}
	
	/** Returns whether a number is an integer */
	public static boolean isInteger(double x) {
		return x == (int)x;
	}
	
	public void setSlideGlass(SlideGlass slide) {
		this.slide = slide;
		setImage(slide.getOriginalImage());
		setBasicProps(getSlideGlass());
		rpd = new RoiPopupDialog(slide, this);
	}
	
	public SlideGlass getSlideGlass() {
		return this.slide;
	}
	
	public void setImage(ImagePlus imp) {
		this.imp = imp;
		cachedMask = null;
		if (imp==null) {
			clipboard = null;
			xMax = yMax = Integer.MAX_VALUE;
		} else {
			xMax = imp.getWidth();
			yMax = imp.getHeight();
		}
	}
	
	/** Returns the ImagePlus associated with this ROI, or null. */
	public ImagePlus getImage() {
		return imp;
	}
	
	/** Returns the ID of the image associated with this ROI. */
	public int getImageID() {
		return imp!=null?imp.getID():imageID;
	}

	public int getType() {
		return type;
	}
	
	public int getState() {
		return state;
	}
	
	public void setType( int type) {
		this.type = type;
	}
	
	public void setState(int state) {
		this.state = state;
	}
	
	public void setModificationState(int modState) {
		this.modState = modState;
	}
	
	public int getModificationState() {
		return this.modState;
	}
	
	/**
	 * modification states
	 * NO_MODS=0
	 * ADD_TO_ROI=1
	 * SUBTRACT_FROM_ROI=2;
	 * 
	 * @param e
	 * @param handleId
	 */
	public void setRoiModState(MouseEvent e, int handleId) {
		if ((handleId>=0 && getModificationState()==NO_MODS)) {
			return;
		}
			
		if (getState()==RoiObj.CONSTRUCTING) {
			return;
		}
		//TODO
//		int tool = Toolbar.getToolId();
//		if (tool>Toolbar.FREEROI && tool!=Toolbar.WAND && tool!=Toolbar.POINT)
//			{roi.modState = Roi.NO_MODS; return;}
//		if (e.isShiftDown())
//			roi.modState = Roi.ADD_TO_ROI;
//		else if (e.isAltDown())
//			roi.modState = Roi.SUBTRACT_FROM_ROI;
		else {
			setModificationState(NO_MODS);
		}
	}
	
    /** Returns 'true' if this ROI uses for drawing the convention for
     *  line and point ROIs, where the coordinates are with respect
     *  to the pixel center.
     *  Returns false for area rois, which have coordinates with respect to
     *  the upper left corners of the pixels */
    protected boolean useLineSubpixelConvention() {
        return isLineOrPoint();
    }

    /** Returns whether a roi created interactively should have subpixel resolution,
     *  (if the roi type supports it), i.e., whether the magnification is high enough */
    protected boolean magnificationForSubPixel(SlideGlass sg) {
        return magnificationForSubPixel(sg.getMagnification());
    }

    protected static boolean magnificationForSubPixel(double magnification) {
        return magnification > 1.5;
    }
	
	/** Returns the perimeter length. */
	public double getLength() {
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		return 2.0*width*pw+2.0*height*ph;
	}
	
	/**
	 * Returns Feret's diameter, the greatest distance between any two points along
	 * the ROI boundary.
	 */
	public double getFeretsDiameter() {
		double[] a = getFeretValues();
		return a != null ? a[0] : 0.0;
	}

	/**
	 * Caculates "Feret" (maximum caliper width), "FeretAngle" and "MinFeret"
	 * (minimum caliper width), "FeretX" and "FeretY".
	 */
	public double[] getFeretValues() {
		double min = Double.MAX_VALUE, diameter = 0.0, angle = 0.0, feretX = 0.0, feretY = 0.0;
		int p1 = 0, p2 = 0;
		double pw = 1.0, ph = 1.0;
		if (imp != null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		Polygon poly = getConvexHull();
		if (poly == null) {
			poly = getPolygon();
			if (poly == null)
				return null;
		}
		double w2 = pw * pw, h2 = ph * ph;
		double dx, dy, d;
		for (int i = 0; i < poly.npoints; i++) {
			for (int j = i; j < poly.npoints; j++) {
				dx = poly.xpoints[i] - poly.xpoints[j];
				dy = poly.ypoints[i] - poly.ypoints[j];
				d = Math.sqrt(dx * dx * w2 + dy * dy * h2);
				if (d > diameter) {
					diameter = d;
					p1 = i;
					p2 = j;
				}
			}
		}
		Rectangle r = getBounds();
		double cx = r.x + r.width / 2.0;
		double cy = r.y + r.height / 2.0;
		int n = poly.npoints;
		double[] x = new double[n];
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = (poly.xpoints[i] - cx) * pw;
			y[i] = (poly.ypoints[i] - cy) * ph;
		}
		double xr, yr;
		for (double a = 0; a <= 90; a += 0.5) { // rotate calipers in 0.5 degree increments
			double cos = Math.cos(a * Math.PI / 180.0);
			double sin = Math.sin(a * Math.PI / 180.0);
			double xmin = Double.MAX_VALUE, ymin = Double.MAX_VALUE;
			double xmax = -Double.MAX_VALUE, ymax = -Double.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				xr = cos * x[i] - sin * y[i];
				yr = sin * x[i] + cos * y[i];
				if (xr < xmin)
					xmin = xr;
				if (xr > xmax)
					xmax = xr;
				if (yr < ymin)
					ymin = yr;
				if (yr > ymax)
					ymax = yr;
			}
			double width = xmax - xmin;
			double height = ymax - ymin;
			double min2 = Math.min(width, height);
			min = Math.min(min, min2);
		}
		double x1 = poly.xpoints[p1], y1 = poly.ypoints[p1];
		double x2 = poly.xpoints[p2], y2 = poly.ypoints[p2];
		if (x1 > x2) {
			double tx1 = x1, ty1 = y1;
			x1 = x2;
			y1 = y2;
			x2 = tx1;
			y2 = ty1;
		}
		feretX = x1 * pw;
		feretY = y1 * ph;
		dx = x2 - x1;
		dy = y1 - y2;
		angle = (180.0 / Math.PI) * Math.atan2(dy * ph, dx * pw);
		if (angle < 0.0)
			angle += 180.0;
		// breadth = getFeretBreadth(poly, angle, x1, y1, x2, y2);
		double[] a = new double[5];
		a[0] = diameter;
		a[1] = angle;
		a[2] = min;
		a[3] = feretX;
		a[4] = feretY;
		return a;
	}

	public Polygon getConvexHull() {
		return getPolygon();
	}

	protected double getFeretBreadth(Shape shape, double angle, double x1, double y1, double x2, double y2) {
		double cx = x1 + (x2 - x1) / 2;
		double cy = y1 + (y2 - y1) / 2;
		AffineTransform at = new AffineTransform();
		at.rotate(angle * Math.PI / 180.0, cx, cy);
		Shape s = at.createTransformedShape(shape);
		Rectangle2D r = s.getBounds2D();
		return Math.min(r.getWidth(), r.getHeight());
	}

	/** Return this selection's bounding rectangle. */
	public Rectangle getBounds() {
		return new Rectangle(x, y, width, height);
	}
	    
    /** Return this selection's bounding rectangle. */
    public java.awt.geom.Rectangle2D.Double getFloatBounds() {
        if (bounds!=null)
            return new java.awt.geom.Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
        else
            return new java.awt.geom.Rectangle2D.Double(x, y, width, height);
    }

    /** Sets the bounds of rectangular, oval or text selections. */
    public void setBounds(java.awt.geom.Rectangle2D.Double b) {
        if (!(type==RECTANGLE||type==OVAL||(this instanceof TextRoi)))
            return;
        this.x = (int)b.x;
        this.y = (int)b.y;
        this.width = (int)Math.ceil(b.width);
        this.height = (int)Math.ceil(b.height);
        bounds = new java.awt.geom.Rectangle2D.Double(b.x, b.y, b.width, b.height);
        cachedMask = null;
    }
    
    /** Sets the integer boundaries x, y, width, height from given sub-pixel
	 *  boundaries, such that all points are within the integer bounding rectangle.
	 *  For open line selections and (multi)Point Rois, note that integer Roi
	 *  coordinates correspond to the center of the 1x1 rectangle enclosing a pixel.
	 *  Points at the boundary of such a rectangle are counted for the higher x or y
	 *  value, in agreement to how (poly-)line or PointRois are displayed at the
	 *  screen at high zoom levels. (For lines and points, it should include all
	 *  pixels affected by 'draw' */
	void setIntBounds(Rectangle2D.Double bounds) {
		if (useLineSubpixelConvention()) { //for PointRois & open lines, ensure the 'draw' area is enclosed
			x = (int)Math.floor(bounds.x + 0.5);
			y = (int)Math.floor(bounds.y + 0.5);
			width  = (int)Math.floor(bounds.x + bounds.width + 1.5)  - x;
			height = (int)Math.floor(bounds.y + bounds.height + 1.5) - y;
		} else {                           //for area Rois, the subpixel bounds must be enclosed in the int bounds
			x = (int)Math.floor(bounds.x);
			y = (int)Math.floor(bounds.y);
			width  = (int)Math.ceil(bounds.x + bounds.width)  - x;
			height = (int)Math.ceil(bounds.y + bounds.height) - y;
		}
	}

	/** Returns the outline of this selection as a Polygon, or 
		null if this is a straight line selection. 
		@see ij.process.ImageProcessor#setRoi
		@see ij.process.ImageProcessor#drawPolygon
		@see ij.process.ImageProcessor#fillPolygon
	*/
	public Polygon getPolygon() {
		int[] xpoints = new int[4];
		int[] ypoints = new int[4];
		xpoints[0] = x;
		ypoints[0] = y;
		xpoints[1] = x+width;
		ypoints[1] = y;
		xpoints[2] = x+width;
		ypoints[2] = y+height;
		xpoints[3] = x;
		ypoints[3] = y+height;
		return new Polygon(xpoints, ypoints, 4);
	}

	public FloatPolygon getFloatPolygon() {
	if (cornerDiameter>0) {
		ImageProcessor ip = getMask();
		ij.gui.Roi roi2 = (new ThresholdToSelection()).convert(ip);
		if (roi2!=null) {
			roi2.setLocation(x, y);
			return roi2.getFloatPolygon();
		}
	}
	if (subPixelResolution() && bounds!=null) {
		float[] xpoints = new float[4];
		float[] ypoints = new float[4];
		xpoints[0] = (float)bounds.x;
		ypoints[0] = (float)bounds.y;
		xpoints[1] = (float)(bounds.x+bounds.width);
		ypoints[1] = (float)bounds.y;
		xpoints[2] = (float)(bounds.x+bounds.width);
		ypoints[2] = (float)(bounds.y+bounds.height);
		xpoints[3] = (float)bounds.x;
		ypoints[3] = (float)(bounds.y+bounds.height);
		return new FloatPolygon(xpoints, ypoints);
	} else {
		Polygon p = getPolygon();
		return new FloatPolygon(toFloat(p.xpoints), toFloat(p.ypoints), p.npoints);
	}
}

	/**
	 * Returns, as a FloatPolygon, an interpolated version of this selection that
	 * has points spaced 1.0 pixel apart.
	 */
	public FloatPolygon getInterpolatedPolygon() {
		return getInterpolatedPolygon(1.0, false);
	}

	/**
	 * Returns, as a FloatPolygon, an interpolated version of this selection with
	 * points spaced 'interval' pixels apart. If 'smooth' is true, traced and
	 * freehand selections are first smoothed using a 3 point running average.
	 */
	public FloatPolygon getInterpolatedPolygon(double interval, boolean smooth) {
		FloatPolygon p = getFloatPolygon();
		if (getType() == LINE) {
			int lastInd = p.npoints - 1;
			Line line = new Line(p.xpoints[0], p.ypoints[0], p.xpoints[lastInd], p.ypoints[lastInd],getSlideGlass());
			p = line.getFloatPoints();
		}
		return getInterpolatedPolygon(p, interval, smooth);
	}

	/**
	 * Returns, as a FloatPolygon, an interpolated version of this selection with
	 * points spaced abs('interval') pixels apart. If 'smooth' is true, traced and
	 * freehand selections are first smoothed using a 3 point running average. If
	 * 'interval' is negative, the program is allowed to decrease abs('interval') so
	 * that the last segment will hit the end point
	 */
	protected FloatPolygon getInterpolatedPolygon(FloatPolygon p, double interval, boolean smooth) {
		boolean allowToAdjust = interval < 0;
		interval = Math.abs(interval);
		boolean isLine = this.isLine();
		double length = p.getLength(isLine);

		int npoints = p.npoints;
		if (!isLine) {// **append (and later remove) closing point to end of array
			npoints++;
			p.xpoints = java.util.Arrays.copyOf(p.xpoints, npoints);
			p.xpoints[npoints - 1] = p.xpoints[0];
			p.ypoints = java.util.Arrays.copyOf(p.ypoints, npoints);
			p.ypoints[npoints - 1] = p.ypoints[0];
		}
		int npoints2 = (int) (10 + (length * 1.5) / interval);// allow some headroom

		double tryInterval = interval;
		double minDiff = 1e9;
		double bestInterval = 0;
		int srcPtr = 0;// index of source polygon
		int destPtr = 0;// index of destination polygon
		double[] destXArr = new double[npoints2];
		double[] destYArr = new double[npoints2];
		int nTrials = 50;
		int trial = 0;
		while (trial <= nTrials) {
			destXArr[0] = p.xpoints[0];
			destYArr[0] = p.ypoints[0];
			srcPtr = 0;
			destPtr = 0;
			double xA = p.xpoints[0];// start of current segment
			double yA = p.ypoints[0];

			while (srcPtr < npoints - 1) {// collect vertices
				double xC = destXArr[destPtr];// center circle
				double yC = destYArr[destPtr];
				double xB = p.xpoints[srcPtr + 1];// end of current segment
				double yB = p.ypoints[srcPtr + 1];
				double[] intersections = lineCircleIntersection(xA, yA, xB, yB, xC, yC, tryInterval, true);
				if (intersections.length >= 2) {
					xA = intersections[0];// only use first of two intersections
					yA = intersections[1];
					destPtr++;
					destXArr[destPtr] = xA;
					destYArr[destPtr] = yA;
				} else {
					srcPtr++;// no intersection found, pick next segment
					xA = p.xpoints[srcPtr];
					yA = p.ypoints[srcPtr];
				}
			}
			destPtr++;
			destXArr[destPtr] = p.xpoints[npoints - 1];
			destYArr[destPtr] = p.ypoints[npoints - 1];
			destPtr++;
			if (!allowToAdjust) {
				if (isLine)
					destPtr--;
				break;
			}

			int nSegments = destPtr - 1;
			double dx = destXArr[destPtr - 2] - destXArr[destPtr - 1];
			double dy = destYArr[destPtr - 2] - destYArr[destPtr - 1];
			double lastSeg = Math.sqrt(dx * dx + dy * dy);

			double diff = lastSeg - tryInterval;// always <= 0
			if (Math.abs(diff) < minDiff) {
				minDiff = Math.abs(diff);
				bestInterval = tryInterval;
			}
			double feedBackFactor = 0.66;// factor <1: applying soft successive approximation
			tryInterval = tryInterval + feedBackFactor * diff / nSegments;
			// stop if tryInterval < 80% of interval, OR if last segment differs < 0.05
			// pixels
			if ((tryInterval < 0.8 * interval || Math.abs(diff) < 0.05 || trial == nTrials - 1) && trial < nTrials) {
				trial = nTrials;// run one more loop with bestInterval to get best polygon
				tryInterval = bestInterval;
			} else
				trial++;
		}
		if (!isLine) // **remove closing point from end of array
			destPtr--;
		float[] xPoints = new float[destPtr];
		float[] yPoints = new float[destPtr];
		for (int jj = 0; jj < destPtr; jj++) {
			xPoints[jj] = (float) destXArr[jj];
			yPoints[jj] = (float) destYArr[jj];
		}
		FloatPolygon fPoly = new FloatPolygon(xPoints, yPoints);
		return fPoly;
	}

	/**
	 * Returns the coordinates of the pixels inside this ROI as an array of Points.
	 * 
	 * @see #getContainedFloatPoints()
	 * @see #iterator()
	 */
	public Point[] getContainedPoints() {
		if (isLine()) {
			FloatPolygon p = getInterpolatedPolygon();
			Point[] points = new Point[p.npoints];
			for (int i = 0; i < p.npoints; i++)
				points[i] = new Point((int) Math.round(p.xpoints[i]), (int) Math.round(p.ypoints[i]));
			return points;
		}
		ImageProcessor mask = getMask();
		Rectangle bounds = getBounds();
		ArrayList<Point> points = new ArrayList<Point>();
		for (int y = 0; y < bounds.height; y++) {
			for (int x = 0; x < bounds.width; x++) {
				if (mask == null || mask.getPixel(x, y) != 0)
					points.add(new Point(this.x + x, this.y + y));
			}
		}
		return (Point[]) points.toArray(new Point[points.size()]);
	}

	/**
	 * Returns the coordinates of the pixels inside this ROI as a FloatPolygon.
	 * 
	 * @see #getContainedPoints()
	 * @see #iterator()
	 */
	public FloatPolygon getContainedFloatPoints() {
		RoiObj roi2 = this;
//		ij.gui.Roi ij_roi = new RoiConverter().convert2Roi(this);
		if (isLine()) {
			if (getStrokeWidth() <= 1)
				return getInterpolatedPolygon();
			else
				roi2 = convertLineToArea(this);
		}
		ImageProcessor mask = roi2.getMask();
		Rectangle bounds = roi2.getBounds();
		FloatPolygon points = new FloatPolygon();
		for (int y = 0; y < bounds.height; y++) {
			for (int x = 0; x < bounds.width; x++) {
				if (mask == null || mask.getPixel(x, y) != 0)
					points.addPoint((float) (bounds.x + x), (float) (bounds.y + y));
			}
		}
		return points;
	}

	/**
	 * <pre>
	 * Calculates intersections of a line segment with a circle
	 * Author N.Vischer
	 * ax, ay, bx, by: points A and B of line segment
	 * cx, cy, rad: Circle center and radius.
	 * ignoreOutside: if true, ignores intersections outside the line segment A-B
	 * Returns an array of 0, 2 or 4 coordinates (for 0, 1, or 2 intersection
	 * points). If two intersection points are returned, they are listed in travel
	 * direction A->B
	 * </pre>
	 */
	public static double[] lineCircleIntersection(double ax, double ay, double bx, double by, double cx, double cy,
			double rad, boolean ignoreOutside) {
		// rotates & translates points A, B and C, creating new points A2, B2 and C2.
		// A2 is then on origin, and B2 is on positive x-axis

		double dxAC = cx - ax;
		double dyAC = cy - ay;
		double lenAC = Math.sqrt(dxAC * dxAC + dyAC * dyAC);

		double dxAB = bx - ax;
		double dyAB = by - ay;

		// calculate B2 and C2:
		double xB2 = Math.sqrt(dxAB * dxAB + dyAB * dyAB);

		double phi1 = Math.atan2(dyAB, dxAB);// amount of rotation
		double phi2 = Math.atan2(dyAC, dxAC);
		double phi3 = phi1 - phi2;
		double xC2 = lenAC * Math.cos(phi3);
		double yC2 = lenAC * Math.sin(phi3);// rotation & translation is done
		if (Math.abs(yC2) > rad)
			return new double[0];// no intersection found
		double halfChord = Math.sqrt(rad * rad - yC2 * yC2);
		double sectOne = xC2 - halfChord;// first intersection point, still on x axis
		double sectTwo = xC2 + halfChord;// second intersection point, still on x axis
		double[] xyCoords = new double[4];
		int ptr = 0;
		if ((sectOne >= 0 && sectOne <= xB2) || !ignoreOutside) {
			double sectOneX = Math.cos(phi1) * sectOne + ax;// undo rotation and translation
			double sectOneY = Math.sin(phi1) * sectOne + ay;
			xyCoords[ptr++] = sectOneX;
			xyCoords[ptr++] = sectOneY;
		}
		if ((sectTwo >= 0 && sectTwo <= xB2) || !ignoreOutside) {
			double sectTwoX = Math.cos(phi1) * sectTwo + ax;// undo rotation and translation
			double sectTwoY = Math.sin(phi1) * sectTwo + ay;
			xyCoords[ptr++] = sectTwoX;
			xyCoords[ptr++] = sectTwoY;
		}
		if (halfChord == 0 && ptr > 2) // tangent line returns only one intersection
			ptr = 2;
		xyCoords = java.util.Arrays.copyOf(xyCoords, ptr);
		return xyCoords;
	}

	/** Returns a copy of this roi. See Thinking is Java by Bruce Eckel
		(www.eckelobjects.com) for a good description of object cloning. */	
	public synchronized Object clone() {
		try {
			RoiObj r = (RoiObj)super.clone();
//			r.setImage(null);
			if (!usingDefaultStroke) {
				r.setStroke(getStroke());
			}
			r.setFillColor(getFillColor());
			r.imageID = getImageID();
			r.listenersNotified = false;
			if (bounds!=null)
				r.bounds = (Rectangle2D.Double)bounds.clone();
			return r;
		}
		catch (CloneNotSupportedException e) {return null;}
	}
	
	protected void grow(int sx, int sy) {
		//tatsu
//		if (clipboard!=null) {
//			return;
//		}
//		if (constrain) {
//			// constrain selection to be square
//			if (!center) {
//				growConstrained(sx, sy);
//				return;
//			}
//			int dx, dy, d;
//			dx = ix - x;
//			dy = iy - y;
//			if (dx<dy)
//				d = dx;
//			else
//				d = dy;
//			xNew = x + d;
//			yNew = y + d;
//		}
//		if (center) {
//			width = Math.abs(xNew - startX)*2;
//			height = Math.abs(yNew - startY)*2;
//			x = startX - width/2;
//			y = startY - height/2;
//		} else {
//			width = Math.abs(xNew - startX);
//			height = Math.abs(yNew - startY);
//			x = (xNew>=startX)?startX:startX - width;
//			y = (yNew>=startY)?startY:startY - height;
//			if (type==RECTANGLE) {
//				if ((x+width) > xMax) width = xMax-x;
//				if ((y+height) > yMax) height = yMax-y;
//			}
//		}
		//元画像に基づくROI原点位置をバックアップ
		oldX = x;
		oldY = y;
		//元画像に基づくROIサイズをバックアップ
		oldWidth = width;
		oldHeight = height;
		
		double mag = slide.getMagnification();
		double scale = slide.getScaleFactor();
		double widthOnOrg = Math.abs(sx - slide.mouseX)/mag/scale;
		double heightOnOrg = Math.abs(sy - slide.mouseY)/mag/scale;
		
		width = (int)widthOnOrg;
		height = (int)heightOnOrg;
		
		System.out.println("slide last clicked:"+slide.mouseX+" "+slide.mouseY);
		System.out.println("rectangle size on original (w,h):"+width+" "+height);
		
		/*
		 * update roi location on org image
		 */
		x = (sx>=slide.mouseX) ? x : slide.onImageX(slide.mouseX) - width;
		y = (sy>=slide.mouseY) ? y : slide.onImageY(slide.mouseY) - height;
//		System.out.println("Growing result info: "+x+" "+y+" "+width+" "+height);
		
		if (type==RECTANGLE) {
			if ((x+width) > xMax) {
				width = xMax-x;
			}
			if ((y+height) > yMax) {
				height = yMax-y;
			}
		}
		updateClipRect(slide);
		bounds = new java.awt.geom.Rectangle2D.Double(x, y, width, height);
		slide.repaint();
	}

	private void growConstrained(int xNew, int yNew,SlideGlass sg) {
		int dx = xNew - startX;
		int dy = yNew - startY;
		width = height = (int)Math.round(Math.sqrt(dx*dx + dy*dy));
		if (type==RECTANGLE) {
			x = (xNew>=startX)?startX:startX - width;
			y = (yNew>=startY)?startY:startY - height;
			if (x<0) x = 0;
			if (y<0) y = 0;
			if ((x+width) > xMax) width = xMax-x;
			if ((y+height) > yMax) height = yMax-y;
		} else {
			x = startX + dx/2 - width/2;
			y = startY + dy/2 - height/2;
		}
		updateClipRect(sg);
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
//		bounds = new Rectangle2D.Double(x, y, width, height);
	}

	protected void moveHandle(int sx, int sy) {
		double asp;
//		if (clipboard!=null) return;
		int ox = slide.onImageX(sx);
		int oy = slide.onImageY(sy);
		if (ox<0) ox=0; if (oy<0) oy=0;
		if (ox>xMax) ox=xMax; if (oy>yMax) oy=yMax;
		int x1=x, y1=y, x2=x1+width, y2=y+height, xc=x+(width/2), yc=y+(height/2);
		if (width > 7 && height > 7) {
			asp = (double)width/(double)height;
			asp_bk = asp;
		} else
			asp = asp_bk;
		
		switch (activeHandle) {
			case 0:
				x=ox; y=oy;
				break;
			case 1:
				y=oy;
				break;
			case 2:
				x2=ox; y=oy;
				break;
			case 3:
				x2=ox;
				break;
			case 4:
				x2=ox; y2=oy;
				break;
			case 5:
				y2=oy;
				break;
			case 6:
				x=ox; y2=oy;
				break;
			case 7:
				x=ox;
				break;
		}
		if (x<x2)
		   width=x2-x;
		else
		  {width=1; x=x2;}
		if (y<y2)
		   height = y2-y;
		else
		   {height=1; y=y2;}
		
		if (center) {
			switch (activeHandle){
				case 0:
					width=(xc-x)*2;
					height=(yc-y)*2;
					break;
				case 1:
					height=(yc-y)*2;
					break;
				case 2:
					width=(x2-xc)*2;
					x=x2-width;
					height=(yc-y)*2;
					break;
				case 3:
					width=(x2-xc)*2;
					x=x2-width;
					break;
				case 4:
					width=(x2-xc)*2;
					x=x2-width;
					height=(y2-yc)*2;
					y=y2-height;
					break;
				case 5:
					height=(y2-yc)*2;
					y=y2-height;
					break;
				case 6:
					width=(xc-x)*2;
					height=(y2-yc)*2;
					y=y2-height;
					break;
				case 7:
					width=(xc-x)*2;
					break;
			}
			if (x>=x2) {
				width=1;
				x=x2=xc;
			}
			if (y>=y2) {
				height=1;
				y=y2=yc;
			}
			bounds = new Rectangle2D.Double(x, y, width, height);
		}
		
		if (constrain) {
			if (activeHandle==1 || activeHandle==5)
				width=height;
			else
				height=width;

			if(x>=x2) {
				width=1;
				x=x2=xc;
			}
			if (y>=y2) {
				height=1;
				y=y2=yc;
			}
			switch (activeHandle) {
				case 0:
					x=x2-width;
					y=y2-height;
					break;
				case 1:
					x=xc-width/2;
					y=y2-height;
					break;
				case 2:
					y=y2-height;
					break;
				case 3:
					y=yc-height/2;
					break;
				case 5:
					x=xc-width/2;
					break;
				case 6:
					x=x2-width;
					break;
				case 7:
					y=yc-height/2;
					x=x2-width;
					break;
			}
			if (center) {
				x=xc-width/2;
				y=yc-height/2;
			}
			bounds = new Rectangle2D.Double(x, y, width, height);
		}

		if (aspect && !constrain) {
			if (activeHandle==1 || activeHandle==5) {
				width=(int)Math.rint((double)height*asp);
			}else {
				height=(int)Math.rint((double)width/asp);
			}
			
			switch (activeHandle){
				case 0:
					x=x2-width;
					y=y2-height;
					break;
				case 1:
					x=xc-width/2;
					y=y2-height;
					break;
				case 2:
					y=y2-height;
					break;
				case 3:
					y=yc-height/2;
					break;
				case 5:
					x=xc-width/2;
					break;
				case 6:
					x=x2-width;
					break;
				case 7:
					y=yc-height/2;
					x=x2-width;
					break;
			}
			if (center) {
				x=xc-width/2;
				y=yc-height/2;
			}
			
			// Attempt to preserve aspect ratio when roi very small:
			if (width<8) {
				if(width<1) width = 1;
				height=(int)Math.rint((double)width/asp_bk);
			}
			if (height<8) {
				if(height<1) height =1;
				width=(int)Math.rint((double)height*asp_bk);
			}
			bounds = new Rectangle2D.Double(x, y, width, height);
		}
		
		System.out.println("MOVING_HANDLE INFO:"+"originX "+x+" originY "+y+" w "+width+" h "+height);
		bounds = new Rectangle2D.Double(x, y, width, height);
		
		updateClipRect(slide);
		slide.repaint();//re-show roi
		oldX=x; 
		oldY=y;
		oldWidth=width; 
		oldHeight=height;
	}

	void move(int sx, int sy) {
		
		oldX = x;
		oldY = y;
		
		int lastSX = slide.mouseX;//image origin at left btn press
		int lastSY = slide.mouseY;
		int dx = sx - lastSX;
		int dy = sy - lastSY;
		if (dx==0 && dy==0) {
			return;
		}
//		System.out.println(dx+" "+dy);
		/*
		 * scale to orginal
		 */
		double mag = slide.getMagnification();
		double scale = slide.getScaleFactor();
		double org_scaled_dx = dx / mag / scale;
		double org_scaled_dy = dy / mag / scale;
		//diffs
//		x = beforeDraggingOriginX + (int)org_scaled_dx;
//		y = beforeDraggingOriginY + (int)org_scaled_dy;
		x = startX + (int)org_scaled_dx;
		y = startY + (int)org_scaled_dy;
		//IMPORTANT, see getXBase(), getYBase()
		if (bounds!=null) {
			bounds.x = x;
			bounds.y = y;
		}
		
		//tatsu
		if(this instanceof ShapeRoi) {
			Shape s = ((ShapeRoi)this).getShape();
//			s.getBounds().x = x;//代入できない
//			s.getBounds().y = y;
			ShapeRoi moveSr = new ShapeRoi(x, y, s, this.getSlideGlass());
			((ShapeRoi)this).setShape(moveSr.getShape());
			System.out.println("change shape origin to "+moveSr.getBounds().x+" "+moveSr.getBounds().y);
		}
		
		//tatsu
//		boolean isImageRoi = this instanceof ImageRoi;
		boolean isImageRoi = false;
		if (clipboard==null && type==RECTANGLE && !isImageRoi) {
			if (x<0) x=0; if (y<0) y=0;
			if ((x+width)>xMax) x = xMax-width;
			if ((y+height)>yMax) y = yMax-height;
			//adjusted
			if (bounds!=null) {
				bounds.x = x;
				bounds.y = y;
			}
		}
		
		//tatsu todo
//		if (type==POINT || ((this instanceof TextRoi) && ((TextRoi)this).getAngle()!=0.0))
//			ignoreClipRect = true;
		
		updateClipRect(slide);
		
		//tatsu todo
//		if ((lineWidth>1 && isLine()) || ignoreClipRect || ((this instanceof PolygonRoi)&&((PolygonRoi)this).isSplineFit()))
//			imp.draw();
//		else
//			imp.draw(clipX, clipY, clipWidth, clipHeight);
		slide.repaint();
	}

	/** Nudge ROI one pixel on arrow key press. */
	public void nudge(int key, SlideGlass sg) {
		if (WindowManager.getActiveWindow() instanceof ij.plugin.frame.RoiManager)
			return;
		switch(key) {
			case KeyEvent.VK_UP:
				y--;
				if (y<0 && (type!=RECTANGLE||clipboard==null))
					y = 0;
				break;
			case KeyEvent.VK_DOWN:
				y++;
				if ((y+height)>=yMax && (type!=RECTANGLE||clipboard==null))
					y = yMax-height;
				break;
			case KeyEvent.VK_LEFT:
				x--;
				if (x<0 && (type!=RECTANGLE||clipboard==null))
					x = 0;
				break;
			case KeyEvent.VK_RIGHT:
				x++;
				if ((x+width)>=xMax && (type!=RECTANGLE||clipboard==null))
					x = xMax-width;
				break;
		}
		updateClipRect(sg);
		if (type==POINT)
			imp.draw();
		else
			imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x; oldY = y;
		bounds = null;
	}
	
	/** Nudge lower right corner of rectangular and oval ROIs by
		one pixel based on arrow key press. */
	public void nudgeCorner(int key,SlideGlass sg) {
		if (type>OVAL || clipboard!=null)
			return;
		switch(key) {
			case KeyEvent.VK_UP:
				height--;
				if (height<1) height = 1;
//				notifyListeners(RoiListener.MODIFIED);//tatsu
				break;
			case KeyEvent.VK_DOWN:
				height++;
				if ((y+height) > yMax) height = yMax-y;
//				notifyListeners(RoiListener.MODIFIED);
				break;
			case KeyEvent.VK_LEFT:
				width--;
				if (width<1) width = 1;
//				notifyListeners(RoiListener.MODIFIED);
				break;
			case KeyEvent.VK_RIGHT:
				width++;
				if ((x+width) > xMax) width = xMax-x;
//				notifyListeners(RoiListener.MODIFIED);
				break;
		}
		updateClipRect(sg);
//		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x; oldY = y;
		cachedMask = null;
	}
	
	// Finds the union of current and previous roi
	protected void updateClipRect(SlideGlass sg) {
		clipX = (x<=oldX)?x:oldX;
		clipY = (y<=oldY)?y:oldY;
		clipWidth = ((x+width>=oldX+oldWidth)?x+width:oldX+oldWidth) - clipX + 1;
		clipHeight = ((y+height>=oldY+oldHeight)?y+height:oldY+oldHeight) - clipY + 1;
		int m = 5;
		
		//tatsu
//		if (sg!=null) {
//			double mag = sg.getMagnification();
//			if (mag<1.0)
//				m = (int)(4.0/mag);
//		}
		
		m += clipRectMargin();
		m = (int)(m+getStrokeWidth()*2);
		clipX-=m; clipY-=m;
		clipWidth+=m*2; clipHeight+=m*2;
	 }
	
	/** Always returns null for rectangular Roi's */
	public ImageProcessor getMask() {
//		if (cornerDiameter>0)
//			return (new ShapeRoi(new RoundRectangle2D.Float(x, y, width, height, cornerDiameter, cornerDiameter))).getMask();
//		else
//			return null;
		return null;//tatsu
	}
	 
	protected int clipRectMargin() {
		return 0;
	}
		
	public void handleMouseDrag(int sx, int sy, int flags) {
		if(slide == null) {
			return;
		}
		constrain = (flags&MouseEvent.SHIFT_MASK)!=0;
		center = (flags&Event.CTRL_MASK)!=0 || (IJ.isMacintosh()&&(flags&Event.META_MASK)!=0);
		aspect = (flags&Event.ALT_MASK)!=0;
		switch(state) {
			case CONSTRUCTING:
				System.out.println("GROW");
				grow(sx, sy);
				break;
			case MOVING:
				System.out.println("MOVING");
				move(sx, sy);
				break;
			case MOVING_HANDLE:
				System.out.println("MOVING_HANDLE");
				moveHandle(sx, sy);
				break;
			default:
				break;
		}
	}

	int getHandleSize(SlideGlass sg) {
		double mag = sg.getMagnification();
//		double size = HANDLE_SIZE/mag;
		return (int)(HANDLE_SIZE*mag);
	}
	
	public void draw(Graphics g, SlideGlass sg) {
		Graphics2D g2 = (Graphics2D)g;
		Color color =  strokeColor!=null? strokeColor:ROIColor;
		if (isActiveOverlayRoi()) {
			color = Color.cyan;
		}
//		if (Interpreter.isBatchMode() && imp!=null && imp.getOverlay()!=null && strokeColor==null && fillColor==null)
//			return;
		g.setColor(color);
		/*
		 * basicaly, size and scale is based on original image.
		 * mag*scale
		 */
		double mag = sg.getMagnification();
		double compScale = sg.getScaleFactor();
		int sw = (int)(width*mag*compScale);
		int sh = (int)(height*mag*compScale);
		int sx1 = (int)(sg.screenX((int)getXBase()));
		int sy1 = (int)(sg.screenY((int)getYBase()));
		g2.setStroke(new BasicStroke(getStrokeWidth()));
		g.drawRect(sx1, sy1, sw, sh);
		
		/*
		 * draw handle as rect roi.
		 */
		drawHandle(g, sx1, sy1, sg);
		drawHandle(g, sx1+(sw/2), sy1, sg);
		drawHandle(g, sx1+sw, sy1, sg);
		drawHandle(g, sx1, sy1+(sh/2), sg);
		drawHandle(g, sx1+sw, sy1+(sh/2), sg);
		drawHandle(g, sx1, sy1+sh, sg);
		drawHandle(g, sx1+(sw/2), sy1+sh, sg);
		drawHandle(g, sx1+sw, sy1+sh, sg);
	}
	
	void drawPreviousRoi(Graphics g) {
		if (previousRoi!=null && previousRoi!=this && previousRoi.modState!=NO_MODS) {
			if (type!=POINT && previousRoi.getType()==POINT && previousRoi.modState!=SUBTRACT_FROM_ROI)
				return;
			previousRoi.setImage(imp);
			previousRoi.draw(g, slide);
		}
	}
	
	   /** Returns the current handle size. */
    public int getHandleSize() {
        if (handleSize>=0)
            return handleSize;
        else
            return getDefaultHandleSize();
    }

    /** Sets the current handle size. */
    public void setHandleSize(int size) {
        if (size>=0 && ((size&1)==0))
            size++; // add 1 if odd
        handleSize = size;
    }
    
    private double defaultStrokeWidth() {
        double defaultWidth = defaultStrokeWidth;
        SlideGlass slide = getSlideGlass();
        if(slide == null) {
			double guiScale = Prefs.getGuiScale();//always 1.0
			if (guiScale > 1.0) {
				defaultWidth = guiScale;
				if (defaultWidth < 1.5)
					defaultWidth = 1.5;
			}
        }else {
        	double zoom = slide.getMagnification();
        	if(zoom > 1.0) {
        		defaultWidth = zoom;
        	}else {
        		defaultWidth = defaultStrokeWidth;
        	}
        }
        return defaultWidth;
    }

    /** Returns the default handle size. */
    public int getDefaultHandleSize() {
        if (defaultHandleSize>0)
            return defaultHandleSize;
        double defaultWidth = defaultStrokeWidth();
        int size = 7;
        if (defaultWidth>1.5) size=9;
        if (defaultWidth>=3) size=11;
        if (defaultWidth>=4) size=13;
        if (defaultWidth>=5) size=15;
        if (defaultWidth>=11) size=(int)defaultWidth;
        defaultHandleSize = size;
        return defaultHandleSize;
    }
    
    public static void resetDefaultHandleSize() {
        defaultHandleSize = 0;
    }
	    
	public void drawHandle(Graphics g, int x, int y, SlideGlass sg) {
		double mag = sg.getMagnification();
		int threshold1 = 7500;
		int threshold2 = 1500;
//		double size = (this.width*this.height)*this.mag*this.mag;
		double size = (this.width*this.height)*mag*mag;
		if (this instanceof Line) {
			size = ((Line)this).getLength()*this.mag;
			threshold1 = 150;
			threshold2 = 50;
		} else {
			if (state==CONSTRUCTING && !(type==RECTANGLE||type==OVAL))
				size = threshold1 + 1;
		}
		int width = 7;
		int x0= slide.screenX(x), y0=slide.screenY(y);
		if (size>threshold1) {
			x -= 3;
			y -= 3;
		} else if (size>threshold2) {
			x -= 2;
			y -= 2;
			width = 5;
		} else {
			x--; y--;
			width = 3;
		}
		int inc = getHandleSize() - 7;
		width += inc;
		x -= inc/2;
		y -= inc/2;
		g.setColor(handleColor);
		if (width<3) {
			g.fillOval(x0,y0,1,1);
			return;
		}
		g.fillOval(x++,y++,width,width);
		g.setColor(handleColor);
		width -= 2;
		g.fillOval(x,y,width,width);
		sg.repaint();
	}
	
	public void drawHandleRect(Graphics g, int x, int y, SlideGlass sg) {
		double mag = sg.getMagnification();
		int threshold1 = 7500;
		int threshold2 = 1500;
		double size = (this.width*this.height)*mag*mag;
		if (this instanceof Line) {
			size = ((Line)this).getLength()*this.mag;
			threshold1 = 150;
			threshold2 = 50;
		} else {
			if (state==CONSTRUCTING && !(type==RECTANGLE||type==OVAL))
				size = threshold1 + 1;
		}
		int width = 7;
		int x0= slide.screenX(x), y0=slide.screenY(y);
		if (size>threshold1) {
			x -= 3;
			y -= 3;
		} else if (size>threshold2) {
			x -= 2;
			y -= 2;
			width = 5;
		} else {
			x--; y--;
			width = 3;
		}
		int inc = getHandleSize() - 7;
		width += inc;
		x -= inc/2;
		y -= inc/2;
		g.setColor(handleColor);
		if (width<3) {
			g.fillRect(x0,y0,1,1);
			return;
		}
		g.fillRect(x++,y++,width,width);
		g.setColor(handleColor);
		width -= 2;
		g.fillRect(x,y,width,width);
		sg.repaint();
	}
	
	/**
	 * Draws the selection outline on the specified ImageProcessor.
	 * 
	 * @see ij.process.ImageProcessor#setColor
	 * @see ij.process.ImageProcessor#setLineWidth
	 */
	public void drawPixels(ImageProcessor ip) {
//	endPaste();//tatsu
		int saveWidth = ip.getLineWidth();
		if (getStrokeWidth() > 1f)
			ip.setLineWidth((int) Math.round(getStrokeWidth()));
		if (cornerDiameter > 0)
			drawRoundedRect(ip);
		else {
			if (ip.getLineWidth() == 1)
				ip.drawRect(x, y, width + 1, height + 1);
			else
				ip.drawRect(x, y, width, height);
		}
		ip.setLineWidth(saveWidth);
		if (Line.getWidth() > 1 || getStrokeWidth() > 1)
			updateFullWindow = true;
	}

	private void drawRoundedRect(ImageProcessor ip) {
		int margin = (int) getStrokeWidth() / 2;
		BufferedImage bi = new BufferedImage(width + margin * 2 + 1, height + margin * 2 + 1,
				BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = bi.createGraphics();
		if (stroke != null)
			g.setStroke(stroke);
		g.drawRoundRect(margin, margin, width, height, cornerDiameter, cornerDiameter);
		ByteProcessor mask = new ByteProcessor(bi);
		ip.setRoi(x - margin, y - margin, width + margin * 2 + 1, height + margin * 2 + 1);
		ip.fill(mask);
	}
	
	public boolean contains(int x, int y) {
		Rectangle r = new Rectangle(this.x, this.y, width, height);
		boolean contains = r.contains(x, y);
		if (cornerDiameter==0 || contains==false)
			return contains;
		RoundRectangle2D rr = new RoundRectangle2D.Float(this.x, this.y, width, height, cornerDiameter, cornerDiameter);
		return rr.contains(x, y);
	}
	
	/** Returns whether coordinate (x,y) is contained in the Roi.
     *  Note that the coordinate (0,0) is the top-left corner of pixel (0,0).
     *  Use contains(int, int) to determine whether a given pixel is contained in the Roi. */
    public boolean containsPoint(double x, double y) {
        boolean contains = false;
        if (bounds == null)
            contains = x>=this.x && y>=this.y && x<this.x+width && y<this.y+height;
        if (cornerDiameter==0 || contains==false)
            return contains;
        RoundRectangle2D rr = new RoundRectangle2D.Double(this.x, this.y, width, height, cornerDiameter, cornerDiameter);
        return rr.contains(x, y);
    }
		
	/** Returns a handle number if the specified screen coordinates are	 
		inside or near a handle, otherwise returns -1. */
	public int isHandle(int sx, int sy, SlideGlass sg) {
//		if (clipboard!=null) {
//			return -1;
//		}
		int margin = sg.getWidth()>1280?7:5;
		int size = HANDLE_SIZE+margin;
		int halfSize = size/2;
		double width = bounds.width;
		double height = bounds.height;
		int px = sg.onImageX(sx);
		int py = sg.onImageY(sy);
		int x1 = getBounds().x - halfSize;
		int y1 = getBounds().y - halfSize;
		int x3 = (getBounds().x+ (int)width) - halfSize;
		int y3 = (getBounds().y+ (int)height) - halfSize;
		int x2 = x1 + (x3 - x1)/2;
		int y2 = y1 + (y3 - y1)/2;
		if (px>=x1&&px<=x1+size&&py>=y1&&py<=y1+size) return 0;//upper left
		if (px>=x2&&px<=x2+size&&py>=y1&&py<=y1+size) return 1;//upper middle
		if (px>=x3&&px<=x3+size&&py>=y1&&py<=y1+size) return 2;//upper right
		if (px>=x3&&px<=x3+size&&py>=y2&&py<=y2+size) return 3;//middle right
		if (px>=x3&&px<=x3+size&&py>=y3&&py<=y3+size) return 4;//lower right
		if (px>=x2&&px<=x2+size&&py>=y3&&py<=y3+size) return 5;//lower middle
		if (px>=x1&&px<=x1+size&&py>=y3&&py<=y3+size) return 6;//lower left
		if (px>=x1&&px<=x1+size&&py>=y2&&py<=y2+size) return 7;//middle left
		return -1;
	}
	
	public void mouseDownInHandle(int handle, int sx, int sy,SlideGlass sg) {
		setState(MOVING_HANDLE);
		previousSX = sx;
		previousSY = sy;
		activeHandle = handle;
	}

	public void mouseDownWithoutHandle(int sx, int sy, SlideGlass sg) {
		if (state==NORMAL && sg!=null) {
			state = MOVING;
		}
	}

    public void handleMouseDown(MouseEvent e, SlideGlass sg) {
		int sx = e.getX();
		int sy = e.getY();
		int ix = sg.onImageX(sx);
		int iy = sg.onImageY(sy);
		//update beforeDraggingOriginX,Y with image origin
		startX = x;
        startY = y;
        startXD = (double)x;
        startYD = (double)y;
		
		int handleId = isHandle(sx, sy, sg);
		
		if (handleId >= 0) {
			setRoiModState(e, handleId);//TODO ?
			mouseDownInHandle(handleId, sx, sy, sg);
		} else if (contains(ix, iy)) {
			mouseDownWithoutHandle(sx, sy, sg);
		}
    }
	
	public void handleMouseUp(int screenX, int screenY) {
		setState(NORMAL);
		setModificationState(NO_MODS);
		System.out.println("Roi RELEASED, state to be normal");
//		if (imp==null) return;
//		imp.draw(clipX-5, clipY-5, clipWidth+10, clipHeight+10);
		//tatsu
//		if (Recorder.record) {
//			String method;
//			if (type==OVAL)
//				Recorder.record("makeOval", x, y, width, height);
//			else if (!(this instanceof TextRoi)) {
//				if (cornerDiameter==0)
//					Recorder.record("makeRectangle", x, y, width, height);
//				else {
//					if (Recorder.scriptMode())
//						Recorder.recordCall("imp.setRoi(new Roi("+x+","+y+","+width+","+height+","+cornerDiameter+"));");
//					else
//						Recorder.record("makeRectangle", x, y, width, height, cornerDiameter);
//				}
//			}
//		}
		//TODO tatsu
//		if (Toolbar.getToolId()==Toolbar.OVAL&&Toolbar.getBrushSize()>0)  {
//			int flags = ic!=null?ic.getModifiers():16;
//			if ((flags&16)==0) // erase ROI Brush
//				{imp.draw(); return;}
//		}
		modifyRoi();
	}
	
	void modifyRoi() {
		if (previousRoi==null || previousRoi.modState==NO_MODS) {
			setBasicStatistics2Popup();
			//avoid nullpointer when loading roi from db, which has no slideglass yet.
			if(getSlideGlass() != null) {
				getSlideGlass().insertOrUpdateRoi4DB(this);//need test
			}
			return;
		}
		if (type==POINT || previousRoi.getType()==POINT) {
			if (type==POINT && previousRoi.getType()==POINT) {
				addPoint();
			} else if (isArea() && previousRoi.getType()==POINT && previousRoi.modState==SUBTRACT_FROM_ROI)
				subtractPoints();
			return;
		}
		RoiObj previous = (RoiObj)previousRoi.clone();
		previous.modState = NO_MODS;
		//TODO
//		ShapeRoi s1 = null;
//		ShapeRoi s2 = null;
//		if (previousRoi instanceof ShapeRoi)
//			s1 = (ShapeRoi)previousRoi;
//		else
//			s1 = new ShapeRoi(previousRoi);
//		if (this instanceof ShapeRoi)
//			s2 = (ShapeRoi)this;
//		else
//			s2 = new ShapeRoi(this);
//		if (previousRoi.modState==ADD_TO_ROI)
//			s1.or(s2);
//		else
//			s1.not(s2);
		previousRoi.modState = NO_MODS;
		//TODO
//		RoiObj[] rois = s1.getRois();
//		if (rois.length==0) return;
//		int type2 = rois[0].getType();
//		RoiObj roi2 = null;
//		if (rois.length==1 && (type2==POLYGON||type2==FREEROI))
//			roi2 = rois[0];
//		else
//			roi2 = s1;
//		if (roi2!=null)
//			roi2.copyAttributes(previousRoi);
		previousRoi = previous;
		getSlideGlass().insertOrUpdateRoi4DB(this);//need test
		setBasicStatistics2Popup();
	}
	
	void addPoint() {
		//if no point roi, return
		if (!(type==POINT && previousRoi.getType()==POINT)) {
			modState = NO_MODS;
			return;
		}
		previousRoi.modState = NO_MODS;
		PointRoi p1 = (PointRoi)previousRoi;
		FloatPolygon poly = getFloatPolygon();
		p1.addPoint(poly.xpoints[0], poly.ypoints[0]);
	}
	
	void subtractPoints() {
		previousRoi.modState = NO_MODS;
		PointRoi p1 = (PointRoi)previousRoi;
		PointRoi p2 = p1.subtractPoints(this);
		if (p2!=null) {
			p1 = (PointRoi)p2.clone();
			p2 = null;
		}
	}
	
	/** If 'add' is true, adds this selection to the previous one. If 'subtract' is true, subtracts 
	it from the previous selection. Called by the IJ.doWand() method, and the makeRectangle(), 
	makeOval(), makePolygon() and makeSelection() macro functions. */
	public void update(boolean add, boolean subtract) {
		if (previousRoi == null)
			return;
		if (add) {
			previousRoi.modState = ADD_TO_ROI;
			modifyRoi();
		} else if (subtract) {
			previousRoi.modState = SUBTRACT_FROM_ROI;
			modifyRoi();
		} else
			previousRoi.modState = NO_MODS;
	}
	
	public void abortPaste() {
//		clipboard = null;
//		imp.getProcessor().reset();
//		imp.updateAndDraw();
	}

	/** Returns the angle in degrees between the specified line and a horizontal line. */
	public double getAngle(int x1, int y1, int x2, int y2) {
		return getFloatAngle(x1, y1, x2, y2);
	}
	
	/** Returns the angle in degrees between the specified line and a horizontal line. */
	public double getFloatAngle(double x1, double y1, double x2, double y2) {
		double dx = x2-x1;
		double dy = y1-y2;
		if (imp!=null && !IJ.altKeyDown()) {
			Calibration cal = imp.getCalibration();
			dx *= cal.pixelWidth;
			dy *= cal.pixelHeight;
		}
		return (180.0/Math.PI)*Math.atan2(dy, dx);
	}

	/** Sets the default (global) color used for ROI outlines.
	 * @see #getColor()
	 * @see #setStrokeColor(Color)
	 */
	public static void setColor(Color c) {
		ROIColor = c;
	}
	
	/** Returns the default (global) color used for drawing ROI outlines.
	 * @see #setColor(Color)
	 * @see #getStrokeColor()
	 */
	public static Color getColor() {
		return ROIColor;
	}

	/** Sets the color used by this ROI to draw its outline. This color, if not null, 
	 * overrides the global color set by the static setColor() method.
	 * @see #getStrokeColor
	 * @see #setStrokeWidth
	 * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
	 */
	public void setStrokeColor(Color c) {
		 strokeColor = c;
	}

	/** Returns the the color used to draw the ROI outline or null if the default color is being used.
	 * @see #setStrokeColor(Color)
	 */
	public Color getStrokeColor() {
		return	strokeColor;
	}

	/** Sets the fill color used to display this ROI, or set to null to display it transparently.
	 * @see #getFillColor
	 * @see #setStrokeColor
	 */
	public void setFillColor(Color color) {
		fillColor = color;
	}

	/** Returns the fill color used to display this ROI, or null if it is displayed transparently.
	 * @see #setFillColor
	 * @see #getStrokeColor
	 */
	public Color getFillColor() {
		return fillColor;
	}
	
	public static void setDefaultFillColor(Color color) {
		defaultFillColor = color;
	}
	
	public static Color getDefaultFillColor() {
		return defaultFillColor;
	}
	
	public void setAntiAlias(boolean antiAlias) {
        this.antiAlias = antiAlias;
    }
    
    public boolean getAntiAlias() {
        return antiAlias;
    }
    
    protected void setRenderingHint(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            antiAlias?RenderingHints.VALUE_ANTIALIAS_ON:RenderingHints.VALUE_ANTIALIAS_OFF);
    }
        
    /** Copy the attributes (outline color, fill color, outline width) 
        of  'roi2' to the this selection. */
    public void copyAttributes(RoiObj roi2) {
        this. strokeColor = roi2. strokeColor;
        this.fillColor = roi2.fillColor;
        this.setStrokeWidth(roi2.getStrokeWidth());
        this.setName(roi2.getName());
        this.group = roi2.group;
        if(roi2.getSlideGlass() != null) {
        	setBasicProps(roi2.getSlideGlass());//and create new roiId
        }
    }

	public void updateWideLine(float width) {
		if (isLine()) {
			wideLine = true;
			setStrokeWidth(width);
			if (getStrokeColor()==null) {
				Color c = getColor();
				setStrokeColor(new Color(c.getRed(),c.getGreen(),c.getBlue(), 77));
			}
		}
	}

	/** Set 'nonScalable' true to have TextRois in a display 
		list drawn at a fixed location and size. */
	public void setNonScalable(boolean nonScalable) {
		this.nonScalable = nonScalable;
	}
	
	/** Sets the width of the line used to draw this ROI. Set
	 * the width to 0.0 and the ROI will be drawn using a
	 * a 1 pixel stroke width regardless of the magnification.
	 * @see #setStrokeColor(Color)
	 * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
	 */
	public void setStrokeWidth(float width) {
		if (width<0f)
			width = 0f;
//		boolean notify = listeners.size()>0 && isLine() && getStrokeWidth()!=width;
		if (width==0)
			stroke = null;
		else if (wideLine)
			this.stroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
		else
			this.stroke = new BasicStroke(width);
		if (width>1f)
			fillColor = null;
		if(width >= 1.0) {
			//update for LINE, Polygon,Freehand
			this.lineWidth = (int)width;
		}
	}

	/** This is a version of setStrokeWidth() that accepts a double argument. */
	public void setStrokeWidth(double width) {
		setStrokeWidth((float)width);
	}

	/** Returns the lineWidth. */
	public float getStrokeWidth() {
		return stroke!=null?stroke.getLineWidth():0f;
	}

	/** Sets the Stroke used to draw this ROI. */
	public void setStroke(BasicStroke stroke) {
		this.stroke = stroke;
	}
	
	/** Returns the Stroke used to draw this ROI, or null if no Stroke is used. */
	public BasicStroke getStroke() {
		return stroke;
	}
	
	public BasicStroke getScaledStroke(SlideGlass sg) {
		if (sg==null)
			return stroke;
		double mag = sg.getMagnification();
		if (mag!=1.0) {
			float width = (float)(stroke.getLineWidth()*mag);
			//return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
			return new BasicStroke(width, stroke.getEndCap(), stroke.getLineJoin(), stroke.getMiterLimit(), stroke.getDashArray(), stroke.getDashPhase());
		} else
			return stroke;
	}

	/** Returns the name of this ROI, or null. */
	public String getName() {
		return getPropertyAt(RoiContextKeySet.Name.name());
	}

	/** Sets the name of this ROI. */
	public void setName(String name) {
		setProperty(RoiContextKeySet.Name.name(), name);
	}
	
	/**
	 * Sets the Paste transfer mode.
	 * 
	 * @see ij.process.Blitter
	 */
	public static void setPasteMode(int transferMode) {
		if (transferMode == pasteMode)
			return;
		pasteMode = transferMode;
		//tatsu
//		ImagePlus imp = WindowManager.getCurrentImage();
//		if (imp != null)
//			imp.updateAndDraw();
	}

	/** Sets the rounded rectangle corner diameter (pixels). */
	public void setCornerDiameter(int cornerDiameter) {
		if (cornerDiameter<0) cornerDiameter = 0;
		this.cornerDiameter = cornerDiameter;
	}

	/** Returns the rounded rectangle corner diameter (pixels). */
	public int getCornerDiameter() {
		return cornerDiameter;
	}
	
	/** Obsolete; replaced by setCornerDiameter(). */
	public void setRoundRectArcSize(int cornerDiameter) {
		setCornerDiameter(cornerDiameter);
	}

	/** Obsolete; replaced by getCornerDiameter(). */
	public int getRoundRectArcSize() {
		return cornerDiameter;
	}

	/** Sets the stack position (image number) of this ROI. In an overlay, this
	* ROI is only displayed when the stack is at the specified position.
	* Set to zero to have the ROI displayed on all images in the stack.
	* @see ij.gui.Overlay
	*/
	public void setPosition(int n) {
		if (n<0) n=0;
		position = n;
		channel = slice = frame = 0;
		hyperstackPosition = false;
	} 

	/** Returns the stack position (image number) of this ROI, or
	*  zero if the ROI is not associated with a particular stack image.
	* @see ij.gui.Overlay
	*/
	public int getPosition() {
		return position;
	}
	
	/** Sets the hyperstack position of this ROI. In an overlay, this
	* ROI is only displayed when the hyperstack is at the specified position.
	* @see ij.gui.Overlay
	*/
	public void setPosition(int channel, int slice, int frame) {
		if (channel<0) channel=0;
		this.channel = channel;
		if (slice<0) slice=0;
		this.slice = slice;
		if (frame<0) frame=0;
		this.frame = frame;
		position = 0;
		hyperstackPosition = true;
	}
	
	/** Returns 'true' if setPosition(C,Z,T) has been called. */
	public boolean hasHyperStackPosition() {
		return hyperstackPosition;
	}
	
	/** Sets the position of this ROI based on the stack position of the specified image.  */
	public void setPosition(ImagePlus imp ) {
		if (imp==null)
			return;
		if (imp.isHyperStack()) {
			int channel = imp.getDisplayMode()==IJ.COMPOSITE?0:imp.getChannel();
			setPosition(channel, imp.getSlice(), imp.getFrame());
		} else if (imp.getStackSize()>1)
			setPosition(imp.getCurrentSlice());
		else
			setPosition(0);
	}
	
	/** Returns the channel position of this ROI, or zero
	*  if this ROI is not associated with a particular channel.
	*/
	public final int getCPosition() {
		return channel;
	}

	/** Returns the slice position of this ROI, or zero
	*  if this ROI is not associated with a particular slice.
	*/
	public final int getZPosition() {
		return slice==0&&!hyperstackPosition?position:slice;
	}
	
	/** Returns the frame position of this ROI, or zero
	*  if this ROI is not associated with a particular frame.
	*/
	public final int getTPosition() {
		return frame;
	}
	
	/**
	 * Returns the current paste transfer mode, or NOT_PASTING (-1) if no paste
	 * operation is in progress.
	 * 
	 * @see ij.process.Blitter
	 */
	public int getPasteMode() {
		if (clipboard == null)
			return NOT_PASTING;
		else
			return pasteMode;
	}

	/** Returns the current paste transfer mode. */
	public static int getCurrentPasteMode() {
		return pasteMode;
	}
	
	/** Returns 'true' if this is an area selection. */
	public boolean isArea() {
		return (type>=RECTANGLE && type<=TRACED_ROI) || type==COMPOSITE;
	}

	/** Returns 'true' if this is a line selection. */
	public boolean isLine() {
		return type>=LINE && type<=FREELINE;
	}
	
	/** Return 'true' if this is a line or point selection. */
    protected boolean isLineOrPoint() {
        return isLine() || type==POINT;
    }
	
	/** Returns 'true' if this is an ROI primarily used from drawing
		(e.g., TextRoi or Arrow). */
	public boolean isDrawingTool() {
		//return cornerDiameter>0;
		return false;
	}
		
	/** Convenience method that converts Roi type to a human-readable form. */
	public String getTypeAsString() {
		String s="";
		switch(type) {
			case POLYGON: s="Polygon"; break;
			case FREEROI: s="Freehand"; break;
			case TRACED_ROI: s="Traced"; break;
			case POLYLINE: s="Polyline"; break;
			case FREELINE: s="Freeline"; break;
			case ANGLE: s="Angle"; break;
			case LINE: s="Straight Line"; break;
			case OVAL: s="Oval"; break;
			case COMPOSITE: s = "Composite"; break;
			case POINT: s="Point"; break;
			default:
				//tatsu
//				if (this instanceof TextRoi)
//					s = "Text";
//				else if (this instanceof ImageRoi)
//					s = "Image";
//				else
//					s = "Rectangle";
				s = "Rectangle";
				break;
		}
		return s;
	}
	
	/** Returns true if this ROI is currently displayed on an image. */
	public boolean isVisible() {
		return isVisible;
	}

	/** Returns true if this is a slection that supports sub-pixel resolution. */
	public boolean subPixelResolution() {
		return subPixel;
	}
	
	/** Returns true if this is a PolygonRoi that supports sub-pixel 
	resolution and polygons are drawn on zoomed images offset
	down and to the right by 0.5 pixels.. */
	public boolean getDrawOffset() {
		return false;
	}

	public void setDrawOffset(boolean drawOffset) {
	}

	public void setIgnoreClipRect(boolean ignoreClipRect) {
		this.ignoreClipRect = ignoreClipRect;
	}
	
	public void setActiveOverlayRoi(boolean active) {
		activeOverlayRoi = active;
	}
	
	/** Returns 'true' if this ROI is displayed and is also in an overlay. */
	public final boolean isActiveOverlayRoi() {
		return activeOverlayRoi;
	}

	/** Checks whether two rectangles are equal. */
	public boolean equals(Object obj) {
		if (obj instanceof RoiObj) {
			RoiObj roi2 = (RoiObj)obj;
			if (type!=roi2.getType()) return false;
			if (!getBounds().equals(roi2.getBounds())) return false;
			if (getLength()!=roi2.getLength()) return false;
			return true;
		} else
			return false;
	}
	
	/** Converts a float array to an int array using truncation. */
	public static int[] toInt(float[] arr) {
		return toInt(arr, null, arr.length);
	}

	public static int[] toInt(float[] arr, int[] arr2, int size) {
		int n = arr.length;
		if (size>n) size=n;
		int[] temp = arr2;
		if (temp==null || temp.length<n)
			temp = new int[n];
		for (int i=0; i<size; i++)
			temp[i] = (int)arr[i];
		return temp;
	}

	/** Converts a float array to an int array using rounding. */
	public static int[] toIntR(float[] arr) {
		int n = arr.length;
		int[] temp = new int[n];
		for (int i=0; i<n; i++)
			temp[i] = (int)Math.floor(arr[i]+0.5);
		return temp;
	}

	/** Converts an int array to a float array. */
	public static float[] toFloat(int[] arr) {
		int n = arr.length;
		float[] temp = new float[n];
		for (int i=0; i<n; i++)
			temp[i] = arr[i];
		return temp;
	}
	
	public void setProperty(String key, String value) {
		if (key==null) {
			return;
		}
		if (props==null) {
			props = new Properties();
		}
		if (value==null || value.length()==0) {
			props.remove(key);
		}else {
			props.setProperty(key, value);
		}
	}
	
	public String getProperty(String property) {
		if (props==null) {
			return null;
		}else {
			//if integer
			if(property.equals(RoiContextKeySet.RoiType.name())) {
				return String.valueOf(getType());
			}else if(property.equals(RoiContextKeySet.RoiGroup.name())) {
				//basicaly, roiGroup was saved as string.
				//here, return sa-is
			}else if(property.equals(RoiContextKeySet.InstanceNo.name())) {
				//here, return sa-is
			}
			//else
			return props.getProperty(property);
		}
	}
	
	public void setProperties(HashMap<String,Object> roiCon) {
		if(roiCon == null || roiCon.size() < 1) {
			return;
		}
		if (props==null) {
			props = new Properties();
		}else {
			props.clear();
		}
		//read
		Integer roiType = (Integer) roiCon.get(RoiObj.RoiContextKeySet.RoiType.name());
		String rid = (String)roiCon.get(RoiObj.RoiContextKeySet.RoiID.name());
		String name = (String)roiCon.get(RoiObj.RoiContextKeySet.Name.name());
		Integer instNo = (Integer)roiCon.get(RoiObj.RoiContextKeySet.InstanceNo.name());
		Integer rg = roiCon.get(RoiObj.RoiContextKeySet.RoiGroup.name()) == null ? null:(int)roiCon.get(RoiObj.RoiContextKeySet.RoiGroup.name());
		String rlbl = (String)roiCon.get(RoiObj.RoiContextKeySet.RoiLabel.name());
		String ot = (String)roiCon.get(RoiObj.RoiContextKeySet.ObjectType.name());
		String organ = (String)roiCon.get(RoiObj.RoiContextKeySet.Organ.name());
		String desc = (String)roiCon.get(RoiObj.RoiContextKeySet.Description.name());
		String pid = (String)roiCon.get(RoiObj.RoiContextKeySet.PatientID.name());
		String studyUid = (String)roiCon.get(RoiObj.RoiContextKeySet.StudyInstanceUID.name());
		String seriesUid = (String)roiCon.get(RoiObj.RoiContextKeySet.SeriesInstanceUID.name());
		String sopUid = (String)roiCon.get(RoiObj.RoiContextKeySet.SOPInstanceUID.name());
		//set properties
		setProperty(RoiObj.RoiContextKeySet.RoiType.name(), String.valueOf(roiType));
		setProperty(RoiObj.RoiContextKeySet.RoiID.name(), rid);
		setName(name);
		setProperty(RoiObj.RoiContextKeySet.InstanceNo.name(), instNo==null ? null:String.valueOf(instNo));
		setProperty(RoiObj.RoiContextKeySet.RoiGroup.name(), rg==null ? null : String.valueOf(rg));
		setProperty(RoiObj.RoiContextKeySet.RoiLabel.name(), rlbl);
		setProperty(RoiObj.RoiContextKeySet.ObjectType.name(), ot);
		setProperty(RoiObj.RoiContextKeySet.Organ.name(), organ);
		setProperty(RoiObj.RoiContextKeySet.Description.name(), desc);
		setProperty(RoiObj.RoiContextKeySet.PatientID.name(), pid);
		setProperty(RoiObj.RoiContextKeySet.StudyInstanceUID.name(), studyUid);
		setProperty(RoiObj.RoiContextKeySet.SeriesInstanceUID.name(), seriesUid);
		setProperty(RoiObj.RoiContextKeySet.SOPInstanceUID.name(), sopUid);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public String getProperties() {
		if (props==null)
			return null;
		Vector v = new Vector();
		for (Enumeration en=props.keys(); en.hasMoreElements();)
			v.addElement(en.nextElement());
		String[] keys = new String[v.size()];
		for (int i=0; i<keys.length; i++)
			keys[i] = (String)v.elementAt(i);
		Arrays.sort(keys);
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<keys.length; i++) {
			sb.append(keys[i]);
			sb.append(": ");
			sb.append(props.get(keys[i]));
			sb.append("\n");
		}
		return sb.toString();
	}
	
	public int getPropertyCount() {
		if (props==null)
			return 0;
		else
			return props.size();
	}
		
	public String toString() {
		return ("Roi["+getTypeAsString()+", x="+x+", y="+y+", width="+width+", height="+height+"]");
	}
	
	public void mouseDragged(MouseEvent e) {
		handleMouseDrag(e.getX(), e.getY(), e.getModifiers());
	}
	
	//need for polygons override
	public void mouseMoved(MouseEvent e) {
		/*
		 * SEE each mouseMoved in Roi Type Override !!!!
		 */
	}

	public void mouseReleased(MouseEvent e) {
		handleMouseUp(e.getX(), e.getY());
	}

	public double getXBase() {
		if (bounds!=null)
			return bounds.x;
		else
			return x;
	}

	public double getYBase() {
		if (bounds!=null)
			return bounds.y;
		else
			return y;
	}
	
	public double getFloatWidth() {
		if (bounds!=null)
			return bounds.width;
		else
			return width;
	}

	public double getFloatHeight() {
		if (bounds!=null)
			return bounds.height;
		else
			return height;
	}
	
	/** Overridden by PolygonRoi (angle between first two points), TextRoi (text angle) and Line (line angle). */
	public double getAngle() {
		return 0.0;
	}
	
	public void enableSubPixelResolution() {
		bounds = new java.awt.geom.Rectangle2D.Double(getXBase(), getYBase(), getFloatWidth(), getFloatHeight());
		subPixel = true;
	}
	
	public void setIsCursor(boolean isCursor) {
		this.isCursor = isCursor;
	}

	public boolean isCursor() {
		return isCursor;
	}

	public String getDebugInfo() {
		return "";
	}
	
	public ImageStatistics getRawStatistics(ImagePlus imp) {
		if(imp == null) {
			return null;
		}
		RoiObj roi = this;
		ImageProcessor ip = imp.getProcessor();
		boolean noImage = (ip == null);
		Rectangle bounds = null;
		if (noImage) {
			roi = (RoiObj)this.clone();
			bounds = roi.getBounds();
			ip = new ByteProcessor(bounds.width, bounds.height);
			roi.setLocation(0, 0);
		}
		if (roi.isLine())
			roi = null;
		ij.gui.Roi ijRoi = new RoiConverter().convert2Roi(roi);
		ip.setRoi(ijRoi);
		ImageStatistics stats = ip.getStatistics();
		if (noImage) {
			stats.mean = stats.min = stats.max = Double.NaN;
			stats.xCentroid+=bounds.x; stats.yCentroid+=bounds.y; 
		}
		ip.resetRoi();
		return stats;
	}
	
	public ImageStatistics getStatistics() {
		return getStatistics(this.imp);
	}
	
	/*
	 * public static ImageStatistics getStatistics​(ImageProcessor ip, int mOptions,Calibration cal)
	 * Calculates and returns statistics for the specified image using the specified measurent options and calibration. Use ImageProcessor.setRoi(x,y,width,height) to limit statistics to a rectangular area and ImageProcessor.setRoi(Roi) to limit to a non-rectangular area.
	 */
	public ImageStatistics getStatistics(ImagePlus imp) {
		if(imp == null) {
			return null;
		}
		RoiObj roi = this;
		ImageProcessor ip = imp.getProcessor();
		boolean noImage = (ip == null);
		Rectangle bounds = null;
		if (noImage) {
			roi = (RoiObj)this.clone();
			bounds = roi.getBounds();
			ip = new ByteProcessor(bounds.width, bounds.height);
			roi.setLocation(0, 0);
		}
//		if (roi.isLine()) {
//			roi = null;
//		}
		/*
		 * pay attention
		 */
		ij.gui.Roi ijRoi = new RoiConverter().convert2Roi(roi);
		ip.setRoi(ijRoi);//see, above description
		ImageStatistics stats = ImageStatistics.getStatistics(ip, -1, imp.getCalibration());
		if (noImage) {
			stats.mean = stats.min = stats.max = Double.NaN;
			stats.xCentroid+=bounds.x; stats.yCentroid+=bounds.y; 
		}
		ip.resetRoi();
		return stats;
	}

	public FloatPolygon getRotationCenter() {
		FloatPolygon p = new FloatPolygon();
		Rectangle2D r = getFloatBounds();
		if (Double.isNaN(xcenter)) {
			xcenter = r.getX()+r.getWidth()/2.0;
			ycenter = r.getY()+r.getHeight()/2.0;
		}
		p.addPoint(xcenter,ycenter);
		return p;
	}

	public void setRotationCenter(double x, double y) {
		xcenter = x;
		ycenter = y;
	}
	
	/** Returns the number of points in this selection; equivalent to getPolygon().npoints. */
	public int size() {
		return 4;
	}
	
	/** Saves 'roi' so it can be restored later using Edit/Selection/Restore Selection. */
    public static void setPreviousRoi(RoiObj roi) {
        if (roi!=null) {
            previousRoi = (RoiObj)roi.clone();
            previousRoi.setImage(null);
        } else
            previousRoi = null;
    }

    /** Returns the Roi saved by setPreviousRoi(). */
    public static RoiObj getPreviousRoi() {
        return previousRoi;
    }

	/* 
	 * Returns the center of the of this selection's countour, or the
	 * center of the bounding box of composite selections.<br> 
	 * Author: Peter Haub (phaub at dipsystems.de)
	 */
	public double[] getContourCentroid() {
		double xC=0, yC=0, lSum=0, x, y, dx, dy, l;
		FloatPolygon poly = getFloatPolygon();
		int nPoints = poly.npoints;
		int n2 = nPoints-1;
		for (int n1=0; n1<nPoints; n1++){
			dx = poly.xpoints[n1] - poly.xpoints[n2];
			dy = poly.ypoints[n1] - poly.ypoints[n2];
			x = poly.xpoints[n2] + dx/2.0;
			y = poly.ypoints[n2] + dy/2.0;
			l = Math.sqrt(dx*dx + dy*dy);
			xC += x*l;
			yC += y*l;
			lSum += l;
			n2 = n1;
		}
		xC /= lSum;
		yC /= lSum;
		return new double[]{xC, yC};
	}
	
	/** Converts a line selection into an area (polygon or composite) selection.<br>
	 * Author: Michael Schmid
	*/
	public static RoiObj convertLineToArea(RoiObj line) {
		if (line==null || !line.isLine())
			throw new IllegalArgumentException("Line selection required"); 
		double lineWidth = line.getStrokeWidth();
		ij.gui.Roi roi2 = null;//tatsu , keep Roi.
		RoiObj roiObj2 = null;
		
		if (line.getType()==RoiObj.LINE) {
			if (lineWidth<=1.0)
				lineWidth = 1.0000001;
			FloatPolygon p = ((Line)line).getFloatPolygon(lineWidth);
			line.setStrokeWidth(lineWidth);
		} else {
			FloatPolygon p = line.getFloatPolygon();
			if (lineWidth<1)
				lineWidth = 1;
			Rectangle bounds = line.getBounds();
			double width = bounds.x+bounds.width + lineWidth;
			double height = bounds.y+bounds.height + lineWidth;
			ByteProcessor ip = new ByteProcessor((int)Math.round(width), (int)Math.round(height));
			PolygonFiller polygonFiller = new PolygonFiller();
			//ip.setColor(255);
			double radius = lineWidth/2.0;
			int n = p.npoints;
			float[] xv = new float[4]; //vertex points of rectangle will be filled for each line segment
			float[] yv = new float[4];
			float[] xt = new float[3]; //vertex points of triangle will be filled between line segments
			float[] yt = new float[3];
			double dx1 = p.xpoints[1]-p.xpoints[0];
			double dy1 = p.ypoints[1]-p.ypoints[0];
			double l = length(dx1, dy1);
			dx1 = dx1/l;			   //unit vector along current line segment
			dy1 = dy1/l;
			double dx0 = dx1;
			double dy0 = dy1;
			double xfrom = p.xpoints[0] - 0.5*dx1;
			double yfrom = p.ypoints[0] - 0.5*dy1;
			//Overlay ovly = new Overlay();
			for (int i=1; i<n; i++) { //line segment from point i-1 ("from") to point i ("to")
				double xto = p.xpoints[i];
				double yto = p.ypoints[i];
				if (i==n-1) {
					xto += 0.5*dx1;
					yto += 0.5*dy1;
				}
				xv[0] = (float)(xfrom + radius*dy1);
				yv[0] = (float)(yfrom - radius*dx1);
				xv[1] = (float)(xfrom - radius*dy1);
				yv[1] = (float)(yfrom + radius*dx1);
				xv[2] = (float)(xto - radius*dy1);
				yv[2] = (float)(yto + radius*dx1);
				xv[3] = (float)(xto + radius*dy1);
				yv[3] = (float)(yto - radius*dx1);
				polygonFiller.setPolygon(xv, yv, 4, 0.5f, 0.5f); //offset 0.5 pxl: line vs area coordinate convention
				polygonFiller.fillByteProcessorMask(ip);
				//ovly.add(new PolygonRoi(xv,yv,Roi.POLYGON));
				if (i>1) {  //fill triangle to previous line segment
					boolean rightTurn=(dx1*dy0>dx0*dy1);
					xt[0] = (float)xfrom;
					yt[0] = (float)yfrom;
					if (rightTurn) {
						xt[1] = (float)(xfrom-radius*dy0);
						yt[1] = (float)(yfrom+radius*dx0);
						xt[2] = (float)(xfrom-radius*dy1);
						yt[2] = (float)(yfrom+radius*dx1);
						xt[0] += (float)(0.5*(radius*dy0+radius*dy1));  //extend triangle to avoid missing pixels (due to rounding errors)
						yt[0] -= (float)(0.5*(radius*dx0+radius*dx1));  //where it touches a rectangle
					} else {
						xt[1] = (float)(xfrom+radius*dy0);
						yt[1] = (float)(yfrom-radius*dx0);
						xt[2] = (float)(xfrom+radius*dy1);
						yt[2] = (float)(yfrom-radius*dx1);
						xt[0] -= (float)(0.5*(radius*dy0+radius*dy1));
						yt[0] += (float)(0.5*(radius*dx0+radius*dx1));
					}
					polygonFiller.setPolygon(xt, yt, 3, 0.5f, 0.5f);
					polygonFiller.fillByteProcessorMask(ip);
					//ovly.add(new PolygonRoi(xt,yt,Roi.POLYGON));
				}
				dx0 = dx1;
				dy0 = dy1;
				xfrom = xto;
				yfrom = yto;
				if (i<n-1) {
					dx1 = p.xpoints[i+1]-p.xpoints[i];
					dy1 = p.ypoints[i+1]-p.ypoints[i];
					l = length(dx1, dy1);
					dx1 = dx1/l;	   //unit vector along next line segment
					dy1 = dy1/l;
				}
			}
			//IJ.getImage().setOverlay(ovly);
			ip.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
			ThresholdToSelection tts = new ThresholdToSelection();
			roi2 = tts.convert(ip);
			roiObj2 = new RoiConverter().convert2RoiObj(roi2);
			roiObj2.setSlideGlass(line.getSlideGlass());
		}
		if (roi2==null)
			return null;
		transferProperties(line, roiObj2);
		roi2.setStrokeWidth(0);
		Color c = roi2.getStrokeColor();
		if (c!=null)  // remove any transparency
			roi2.setStrokeColor(new Color(c.getRed(),c.getGreen(),c.getBlue()));
		return roiObj2;
	}
	
	 /** Returns the length of a vector with components dx, dy */
    static double length(double dx, double dy) {
        return Math.sqrt(dx*dx+dy*dy);
    }

    /** Used by PolygonRoi, Line, ShapeRoi etc. */
    static double sqr(double x) {return x*x; }

    private static void transferProperties(RoiObj roi1, RoiObj roi2) {
        if (roi1==null || roi2==null)
            return;
        roi2.setStrokeColor(roi1.getStrokeColor());
        if (roi1.getStroke()!=null)
            roi2.setStroke(roi1.getStroke());
        roi2.setDrawOffset(roi1.getDrawOffset());
    }

	/** Returns a hashcode for this Roi that typically changes 
		if it is moved, even though it is still the same object. */
	public int getHashCode() {
		return hashCode() ^ (Double.valueOf(getXBase()).hashCode()) ^
			Integer.rotateRight(Double.valueOf(getYBase()).hashCode(),16);
	}
	
	public void setFlattenScale(double scale) {
		flattenScale = scale;
	}
	
	public static RoiObj xor(RoiObj[] rois) {
		ShapeRoi s1=null, s2=null;
		for (int i=0; i<rois.length; i++) {
			RoiObj roi = rois[i];
			if (roi==null)
				continue;
			if (s1==null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi)roi.clone();
				else
					s1 = new ShapeRoi(roi);
				if (s1==null) return null;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi)roi.clone();
				else
					s2 = new ShapeRoi(roi);
				if (s2==null) continue;
				s1.xor(s2);
			}
		}
		return s1!=null?s1.trySimplify():null;
	}
	
	public void notifyListeners(int id) {
        if (id==RoiListener.CREATED) {
            if (listenersNotified)
                return;
            listenersNotified = true;   
        }
        synchronized (listeners) {
            for (int i=0; i<listeners.size(); i++) {
                RoiListener listener = (RoiListener)listeners.elementAt(i);
                listener.roiModified(imp, id);
            }
        }
    }

	public static void addRoiListener(RoiListener listener) {
		listeners.addElement(listener);
	}
	
	public static void removeRoiListener(RoiListener listener) {
		listeners.removeElement(listener);
	}
	
	/**
	 * Required by the {@link Iterable} interface.
	 * Use to iterate over the contained coordinates. Usage example: 
	 * <pre>
	 * for (Point p : roi) {
	 *   // process p
	 * }
	 * </pre>
	 * Author: Wilhelm Burger
	 * @see #getContainedPoints()
	 * @see #getContainedFloatPoints()
	*/
	@Override
	public Iterator<Point> iterator() {
		// Returns the default (mask-based) point iterator. Note that 'Line' overrides the 
		// iterator() method and returns a specific point iterator.
		return new RoiPointsIteratorMask();
	}
	
	
	/**
	 * Default iterator over points contained in a mask-backed {@link Roi}.
	 * Author: W. Burger
	*/
	private class RoiPointsIteratorMask implements Iterator<Point> {
		private final ImageProcessor mask;
		private final Rectangle bounds;
		private final int xbase, ybase;
		private final int n;
		private int next;
		
		RoiPointsIteratorMask() {
//			if (isLine()) {
//				Roi roi2 = Selection.lineToArea(this);
//				mask = roi2.getMask();
//				bounds = roi2.getBounds();
//				xbase = bounds.x;
//				ybase = bounds.y;
//			} else {
//				mask = getMask();
//				bounds = getBounds();
//				xbase = bounds.x;
//				ybase = bounds.y;
//			}
			
			//tatsu
			mask = getMask();
			bounds = getBounds();
			xbase = bounds.x;
			ybase = bounds.y;
			
			n = bounds.width * bounds.height;
			findNext(0);	// sets next
		}

		@Override
		public boolean hasNext() {
			return next < n;
		}

		@Override
		public Point next() {
			if (next >= n)
				throw new NoSuchElementException();
			int x = next % bounds.width;
			int y = next / bounds.width;
			findNext(next+1);
			return new Point(xbase+x, ybase+y);
		}
		
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
		
		// finds the next element (from start), sets next
		private void findNext(int start) {
			if (mask == null)
				next = start;
			else {
				next = n;
				for (int i=start; i<n; i++) {
					if (mask.get(i)!=0) {
						next = i;
						break;
					}
				}
			}
		}
	}
}

