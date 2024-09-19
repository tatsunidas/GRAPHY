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
package com.vis.core.view.D2.roi;

import ij.*;
import ij.gui.Roi;
import ij.gui.RoiListener;
import ij.process.*;
import ij.process.PolygonFiller;
import ij.measure.*;
import ij.plugin.LutLoader;
import ij.plugin.filter.ThresholdToSelection;
import java.awt.*;
import java.util.*;

import java.text.SimpleDateFormat;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.geom.*;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.ContextKey;
import com.vis.configuration.GraphyProp;
import com.vis.core.log.Log;
import com.vis.core.util.Platform;
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
 * <b>
 * Convention for subpixel resolution and zooming in:
 * </b><ul>
 * <li> Area ROIs: Integer coordinates refer to the top-left corner of the pixel with these coordinates.
 *      Thus, pixel (0,0) is enclosed by the rectangle spanned between points (0,0) and (1,1),
 *      i.e., a rectangle at (0,0) with width = height = 1 pixel.
 * <li> Line and Point Rois: Integer coordinates refer to the center of a pixel.
 *      Thus, a line from (0,0) to (1,0) has its start and end points in the center of
 *      pixels (0,0) and (1,0), respectively, and drawing the line should affect both
 *      pixels. For images dispplayed at high zoom levels, this means that (open) lines
 *      and single points are displayed 0.5 pixels further to the right and bottom than
 *      the outlines of area ROIs (closed lines) with the same coordinates.
 * </ul>
 * Note that rectangular and (nonrotated) oval ROIs do not support subpixel resolution.
 * Since ImageJ 1.52t, this convention does not depend on the Prefs.subpixelResolution
 * (previously accessible via Edit>Options>Plot) and this flag has no effect any more.
 *
  */

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class RoiObj extends Object implements Cloneable, java.io.Serializable, Iterable<Point> {

	/**
	 * Default iterator over points contained in a mask-backed {@link Roi}. Author:
	 * W. Burger
	 */
	private class RoiPointsIteratorMask implements Iterator<Point> {
		private ImageProcessor mask;
		private final Rectangle bounds;
		private final int xbase, ybase;
		private final int n;
		private int next;

		RoiPointsIteratorMask() {
			if (isLine()) {
				RoiObj roi2 = RoiObj.convertLineToArea(RoiObj.this);
				mask = roi2.getMask();
				xbase = roi2.x;
				ybase = roi2.y;
			} else {
				mask = getMask();
				if (mask==null && type==RoiType.RECTANGLE.id()) {
					mask = new ByteProcessor(width, height);
					mask.invert();
				}
				xbase = RoiObj.this.x;
				ybase = RoiObj.this.y;
			}
			bounds = new Rectangle(mask.getWidth(), mask.getHeight());
			n = bounds.width * bounds.height;
			findNext(0);	// sets next
		}

		// finds the next element (from start), sets next
		private void findNext(int start) {
			if (mask == null)
				next = start;
			else {
				next = n;
				for (int i = start; i < n; i++) {
					if (mask.get(i) != 0) {
						next = i;
						break;
					}
				}
			}
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
			findNext(next + 1);
			return new Point(xbase + x, ybase + y);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	// States
	public static final int CONSTRUCTING = 0;
	public static final int MOVING = 1;
	public static final int RESIZING = 2;
	public static final int NORMAL = 3;

	public static final int MOVING_HANDLE = 4;
	// other
	public static final int HANDLE_SIZE = 5; // replaced by getHandleSize()

	public static final int NOT_PASTING = -1;
	// modification states
	public static final int NO_MODS = 0;
	public static final int ADD_TO_ROI = 1;

	public static final int SUBTRACT_FROM_ROI = 2;
	public static final BasicStroke onePixelWide = new BasicStroke(1);
	/** Get using getPreviousRoi() and set using setPreviousRoi() */
	public static RoiObj previousRoi;
	protected static LUT glasbeyLut;
	private static Double defaultStrokeWidth;

	protected static int lineWidth = 1;// keep static
	public static final int FERET_ARRAYSIZE = 16; // Size of array with Feret values
	public static final int FERET_ARRAY_POINTOFFSET = 8; // Where point coordinates start in Feret array
	
	protected static Color ROIColor = Color.YELLOW;
	protected static Color defaultFillColor = Color.white;
	protected static Color defaultHandleColor = Color.yellow;
	protected static int pasteMode = Blitter.COPY;
	private static Vector<RoiListener> listeners = new Vector<RoiListener>();
	private static int defaultHandleSize;
	
	/*
	 * Coordinate system conforms to the original image coordinate system.
	 */
	public int startX, startY;
	public double startXD, startYD;
	public int x, y;
	protected int width, height;
	//Original image coordinate basis
	int previousX;// on imageX
	int previousY;// on imageY
	// SlideGlass coordinate basis.
	int previousSX;// on slideX
	int previousSY;// on slideY
	java.awt.geom.Rectangle2D.Double bounds;
	int activeHandle;
	int state = NORMAL;

	int modState = NO_MODS;
	int cornerDiameter;
	protected int type;
	protected int xMax, yMax; // original img WH
	protected ImagePlus imp;// original imp (no display imp)

	protected int oldX, oldY, oldWidth, oldHeight;
	protected int clipX, clipY, clipWidth, clipHeight;
	protected ImagePlus clipboard;
	protected boolean constrain; // to be square
	protected boolean center;
	protected boolean aspect;
	protected boolean updateFullWindow;
	protected double mag = 1.0;

	protected double asp_bk; // saves aspect ratio if resizing takes roi very small
	protected ImageProcessor cachedMask;
	
	protected Color handleColor;
	protected Color strokeColor;
	protected Color instanceColor; // obsolete; replaced by strokeColor
	protected Color fillColor;
	protected BasicStroke stroke;
	protected boolean nonScalable;
	protected boolean overlay;
	protected boolean wideLine;
	protected boolean ignoreClipRect;
	protected double flattenScale = 1.0;
	private int position;
	private int channel, slice, frame;
	private boolean hyperstackPosition;
	private boolean subPixel;
	private boolean isCursor;
	protected double xcenter = Double.NaN;
	protected double ycenter;

	private boolean listenersNotified;
	private boolean antiAlias = true;
	private int group = -1; // 0 to 255.
	private boolean usingDefaultStroke;
	private int handleSize = -1;
	private boolean scaleStrokeWidth;

	/*
	 * ROi Properties.
	 */
	public Properties props = new Properties();

	protected boolean activeOverlayRoi = false;//get mouse focus
	protected boolean isSelected = false;
	protected boolean fill = false;
	
	protected SlideGlass slide;
	
	/*
	 * STATIC METHOD
	 */
	
	public static void addRoiListener(RoiListener listener) {
		listeners.addElement(listener);
	}

	/**
	 * Converts a line selection into an area (polygon or composite) selection.<br>
	 * Author: Michael Schmid
	 */
	public static RoiObj convertLineToArea(RoiObj line) {
		
		if (line == null || !line.isLine())
			throw new IllegalArgumentException("Line selection required");
		
		double lineWidth = line.getStrokeWidth();
		if (lineWidth<1.0)
			lineWidth = 1.0;
		
		ij.gui.Roi roi2 = null;// tatsu , keep Roi.
		RoiObj roiObj2 = null;

		if (line.getType() == RoiType.LINE.id()) {
			FloatPolygon p = ((Line)line).getFloatPoints();
			roi2 = new ij.gui.RotatedRectRoi(p.xpoints[0],p.ypoints[0],p.xpoints[1],p.ypoints[1],lineWidth);
			line.setStrokeWidth(lineWidth);
		} else {
			Rectangle bounds = line.getBounds();
			double width = bounds.x+bounds.width + lineWidth;
			double height = bounds.y+bounds.height + lineWidth;
			ByteProcessor ip = new ByteProcessor((int)Math.round(width), (int)Math.round(height));
			PolygonFiller polygonFiller = new PolygonFiller();
			double radius = lineWidth/2.0;
			FloatPolygon p = line.getFloatPolygon();
			int n = p.npoints;
			float[] xv = new float[4]; // vertex points of rectangle will be filled for each line segment
			float[] yv = new float[4];
			float[] xt = new float[3]; // vertex points of triangle will be filled between line segments
			float[] yt = new float[3];
			double dx1 = p.xpoints[1] - p.xpoints[0];
			double dy1 = p.ypoints[1] - p.ypoints[0];
			double l = length(dx1, dy1);
			dx1 = dx1 / l; // unit vector along current line segment
			dy1 = dy1 / l;
			double dx0 = dx1;
			double dy0 = dy1;
			double xfrom = p.xpoints[0] - 0.5 * dx1;
			double yfrom = p.ypoints[0] - 0.5 * dy1;
			// Overlay ovly = new Overlay();
			for (int i = 1; i < n; i++) { // line segment from point i-1 ("from") to point i ("to")
				double xto = p.xpoints[i];
				double yto = p.ypoints[i];
				if (i == n - 1) {
					xto += 0.5 * dx1;
					yto += 0.5 * dy1;
				}
				xv[0] = (float) (xfrom + radius * dy1);
				yv[0] = (float) (yfrom - radius * dx1);
				xv[1] = (float) (xfrom - radius * dy1);
				yv[1] = (float) (yfrom + radius * dx1);
				xv[2] = (float) (xto - radius * dy1);
				yv[2] = (float) (yto + radius * dx1);
				xv[3] = (float) (xto + radius * dy1);
				yv[3] = (float) (yto - radius * dx1);
				polygonFiller.setPolygon(xv, yv, 4, 0.5f, 0.5f); // offset 0.5 pxl: line vs area coordinate convention
				polygonFiller.fillByteProcessorMask(ip);
				// ovly.add(new PolygonRoi(xv,yv,Roi.POLYGON));
				if (i > 1) { // fill triangle to previous line segment
					boolean rightTurn = (dx1 * dy0 > dx0 * dy1);
					xt[0] = (float) xfrom;
					yt[0] = (float) yfrom;
					if (rightTurn) {
						xt[1] = (float) (xfrom - radius * dy0);
						yt[1] = (float) (yfrom + radius * dx0);
						xt[2] = (float) (xfrom - radius * dy1);
						yt[2] = (float) (yfrom + radius * dx1);
						xt[0] += (float) (0.5 * (radius * dy0 + radius * dy1)); // extend triangle to avoid missing
																				// pixels (due to rounding errors)
						yt[0] -= (float) (0.5 * (radius * dx0 + radius * dx1)); // where it touches a rectangle
					} else {
						xt[1] = (float) (xfrom + radius * dy0);
						yt[1] = (float) (yfrom - radius * dx0);
						xt[2] = (float) (xfrom + radius * dy1);
						yt[2] = (float) (yfrom - radius * dx1);
						xt[0] -= (float) (0.5 * (radius * dy0 + radius * dy1));
						yt[0] += (float) (0.5 * (radius * dx0 + radius * dx1));
					}
					polygonFiller.setPolygon(xt, yt, 3, 0.5f, 0.5f);
					polygonFiller.fillByteProcessorMask(ip);
				}
				dx0 = dx1;
				dy0 = dy1;
				xfrom = xto;
				yfrom = yto;
				if (i < n - 1) {
					dx1 = p.xpoints[i + 1] - p.xpoints[i];
					dy1 = p.ypoints[i + 1] - p.ypoints[i];
					l = length(dx1, dy1);
					dx1 = dx1 / l; // unit vector along next line segment
					dy1 = dy1 / l;
				}
			}
			ip.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
			ThresholdToSelection tts = new ThresholdToSelection();
			roi2 = tts.convert(ip);
		}
		if(roi2 == null) {
			return null;
		}
		roiObj2 = new RoiConverter().convert2RoiObj(roi2);
		roiObj2.setSlideGlass(line.getSlideGlass());
		transferProperties(line, roiObj2);
		roiObj2.setStrokeWidth(0);
		Color c = roiObj2.getStrokeColor();
		if (c != null) // remove any transparency
			roiObj2.setStrokeColor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
		return roiObj2;
	}

	private static double defaultStrokeWidth() {
		if(defaultStrokeWidth == null) {
			double defaultWidth = 1.5;
			return defaultWidth;
		}else {
			return defaultStrokeWidth;
		}
	}

	/**
	 * Returns the default (global) color used for drawing ROI outlines.
	 * 
	 * @see #setColor(Color)
	 * @see #getStrokeColor()
	 */
	public static Color getColor() {
		return ROIColor;
	}
	/** Returns the current paste transfer mode. */
	public static int getCurrentPasteMode() {
		return pasteMode;
	}

	public static Color getDefaultFillColor() {
		return defaultFillColor;
	}
	
	/** Retrieves color associated to a given roi group. */
	public static Color getGroupColor(int group) {
		Color color = ROIColor; // default ROI color
		if (group>0) { // read Glasbey Lut
			if (glasbeyLut==null) {
				String path = IJ.getDir("luts")+"Glasbey.lut";
				glasbeyLut = LutLoader.openLut("noerror:"+path);
				if (glasbeyLut==null)
					IJ.log("LUT not found: "+path);
			}
			if (glasbeyLut!=null)
				color = new Color(glasbeyLut.getRGB(group));
		}
		return color;
	}
	
	/** Returns the Roi saved by setPreviousRoi(). */
	public static RoiObj getPreviousRoi() {
		return previousRoi;
	}
	
	/** Returns whether a number is an integer */
	public static boolean isInteger(double x) {
		return x == (int) x;
	}
	
	/** Returns the length of a vector with components dx, dy */
	static double length(double dx, double dy) {
		return Math.sqrt(dx * dx + dy * dy);
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
	
	protected static boolean magnificationForSubPixel(double magnification) {
		return magnification > 1.5;
	}

	public static void removeRoiListener(RoiListener listener) {
		listeners.removeElement(listener);
	}
	public static void resetDefaultHandleSize() {
		defaultHandleSize = 0;
	}
	/**
	 * Sets the default (global) color used for ROI outlines.
	 * 
	 * @see #getColor()
	 * @see #setStrokeColor(Color)
	 */
	public static void setColor(Color c) {
		ROIColor = c;
	}
	public static void setDefaultFillColor(Color color) {
		defaultFillColor = color;
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
		// tatsu
//		ImagePlus imp = WindowManager.getCurrentImage();
//		if (imp != null)
//			imp.updateAndDraw();
	}
	/**
	 * Saves 'roi' so it can be restored later using Edit/Selection/Restore
	 * Selection.
	 */
	public static void setPreviousRoi(RoiObj roi) {
		if (roi != null) {
			previousRoi = (RoiObj) roi.clone();
			previousRoi.setImage(null);
		} else
			previousRoi = null;
	}
	
	/** Used by PolygonRoi, Line, ShapeRoi etc. */
	static double sqr(double x) {
		return x * x;
	}
	
	/** Converts an int array to a float array. */
	public static float[] toFloat(int[] arr) {
		int n = arr.length;
		float[] temp = new float[n];
		for (int i = 0; i < n; i++)
			temp[i] = arr[i];
		return temp;
	}
	
	/** Converts a float array to an int array using truncation. */
	public static int[] toInt(float[] arr) {
		return toInt(arr, null, arr.length);
	}
	
	public static int[] toInt(float[] arr, int[] arr2, int size) {
		int n = arr.length;
		if (size > n)
			size = n;
		int[] temp = arr2;
		if (temp == null || temp.length < n)
			temp = new int[n];
		for (int i = 0; i < size; i++)
			temp[i] = (int) arr[i];
		return temp;
	}
	
	/** Converts a float array to an int array using rounding. */
	public static int[] toIntR(float[] arr) {
		int n = arr.length;
		int[] temp = new int[n];
		for (int i = 0; i < n; i++)
			temp[i] = (int) Math.floor(arr[i] + 0.5);
		return temp;
	}
	
	private static void transferProperties(RoiObj roi1, RoiObj roi2) {
		if (roi1 == null || roi2 == null)
			return;
		roi2.copyAttributes(roi1);
		if (roi1.getStroke() != null)
			roi2.setStroke(roi1.getStroke());
	}
	
	public static RoiObj xor(RoiObj[] rois) {
		ShapeRoi s1 = null, s2 = null;
		for (int i = 0; i < rois.length; i++) {
			RoiObj roi = rois[i];
			if (roi == null)
				continue;
			if (s1 == null) {
				s1 = new ShapeRoi(roi);
			} else {
				s2 = new ShapeRoi(roi);
				s1.xor(s2);
			}
		}
		return s1 != null ? s1.trySimplify() : null;
	}
	

	/** Creates a rounded rectangular ROI using double arguments. */
	public RoiObj(double x, double y, double width, double height, int cornerDiameter, SlideGlass sg) {
		this((int) x, (int) y, (int) Math.ceil(width), (int) Math.ceil(height), cornerDiameter, sg);
		bounds = new Rectangle2D.Double(x, y, width, height);
		subPixel = true;
	}

	/** Creates a rectangular ROI using double arguments. */
	public RoiObj(double x, double y, double width, double height, SlideGlass sg) {
		this(x, y, width, height, 0, sg);
	}

	/*
	 * new RoiObj(x,y,w,h,0); w and h are can be 0. x and y are keep original image
	 * coordinates basis.
	 * Without subPixelResolution
	 */
	public RoiObj(int x, int y, int width, int height, int cornerDiameter, SlideGlass sg) {
		setSlideGlass(sg);
		if (width < 1)
			width = 1;
		if (height < 1)
			height = 1;
		this.cornerDiameter = cornerDiameter;// for rounded rectangle
		this.x = x;
		this.y = y;
		startX = x;
		startY = y;
		oldX = x;
		oldY = y;
		this.width = width;
		this.height = height;
		oldWidth = width;
		oldHeight = height;
		clipX = x;
		clipY = y;
		clipWidth = width;
		clipHeight = height;
		state = NORMAL;
		type = RoiType.RECTANGLE.id();
		loadSettings();
	}

	/** Creates a rectangular ROI. */
	public RoiObj(int x, int y, int width, int height, SlideGlass sg) {
		this(x, y, width, height, 0, sg);
	}

	/** Creates a new rectangular Roi. */
	public RoiObj(Rectangle r, SlideGlass sg) {
		this(r.x, r.y, r.width, r.height, sg);
	}

	public void abortPaste() {
		clipboard = null;
		imp.getProcessor().reset();
		imp.updateAndDraw();
	}

	void addPoint() {
		// if no point roi, return
		if (!(type == RoiType.POINT.id() && previousRoi.getType() == RoiType.POINT.id())) {
			modState = NO_MODS;
			return;
		}
		previousRoi.modState = NO_MODS;
		PointRoi p1 = (PointRoi) previousRoi;
		FloatPolygon poly = getFloatPolygon();
		p1.addPoint(poly.xpoints[0], poly.ypoints[0]);
	}

	public void addProperty(String key, String value) {
		if (ContextKey.checkPropertyKey(key)) {
			setProperty(key, value);
		} else {
			try {
				throw new Exception();
			} catch (Exception e) {
				Log.logger.fine("RoiObj:addProperty()::Catch invalid key type. return.");
				e.printStackTrace();
				return;
			}
		}
	}

	protected int clipRectMargin() {
		return 0;
	}

	/**
	 * Returns a copy of this roi. See Thinking is Java by Bruce Eckel
	 * (www.eckelobjects.com) for a good description of object cloning.
	 */
	public synchronized Object clone() {
		try {
			RoiObj r = (RoiObj) super.clone();
			r.setProperty(ContextKey.RoiID.name(), createRoiIndex());
//			r.setSlideGlass(null);
//			r.setImage(null);
			if (!usingDefaultStroke) {
				r.setStroke(getStroke());
			}
			r.setFillColor(getFillColor());
			r.listenersNotified = false;
			if (bounds != null)
				r.bounds = (Rectangle2D.Double) bounds.clone();
			return r;
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	public Color colorFromString(String colorName, Color defaultColor) {
		return GraphyProp.getColorFromName(colorName, defaultColor);
	}

	public boolean contains(int x, int y) {
		Rectangle r = new Rectangle(this.x, this.y, width, height);
		boolean contains = r.contains(x, y);
		if (cornerDiameter == 0 || contains == false)
			return contains;
		RoundRectangle2D rr = new RoundRectangle2D.Float(this.x, this.y, width, height, cornerDiameter, cornerDiameter);
		return rr.contains(x, y);
	}

	/**
	 * Returns whether coordinate (x,y) is contained in the Roi. Note that the
	 * coordinate (0,0) is the top-left corner of pixel (0,0). Use contains(int,
	 * int) to determine whether a given pixel is contained in the Roi.
	 */
	public boolean containsPoint(double x, double y) {
		boolean contains = false;
		if (bounds == null)
			contains = x >= this.x && y >= this.y && x < this.x + width && y < this.y + height;
		if (cornerDiameter == 0 || contains == false)
			return contains;
		RoundRectangle2D rr = new RoundRectangle2D.Double(this.x, this.y, width, height, cornerDiameter,
				cornerDiameter);
		return rr.contains(x, y);
	}

	/**
	 * Copy the attributes (outline color, fill color, outline width) of 'roi2' to
	 * the this selection.
	 */
	public void copyAttributes(RoiObj roi2) {
		this.strokeColor = roi2.strokeColor;
		this.fillColor = roi2.fillColor;
		this.setStrokeWidth(roi2.getStrokeWidth());
		this.setName(roi2.getName());
		this.group = roi2.group;
		setSlideGlass(roi2.getSlideGlass());// and create new roiId
	}

	private String createRoiIndex() {
		SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
		Date now = new Date();
		int hash = new Random().hashCode();
		return f.format(now) + "_" + hash;
	}

	public void draw(Graphics g) {
		AffineTransform aTx = new AffineTransform();
		Graphics2D g2d = (Graphics2D)g;
		double mag = getMagnification();
		double scaleXY[] = getComponentScaleFactor();
		if (slide != null) {
			Point offset = slide.getDisplayImageOriginXY();
			//First, translate image origin without mag and component scale.
			aTx.translate(offset.x, offset.y);
			//Second, scale Roi graphics
			aTx.scale(mag*scaleXY[0],mag*scaleXY[1]);
			g2d.setTransform(aTx);
		}
		Color color = strokeColor != null ? strokeColor : ROIColor;
		if(fill) {
			color = fillColor;
		}
		if (isActiveOverlayRoi()) {
			color = Color.cyan;
		}
		g2d.setColor(color);
		int w = (int) width;
		int h = (int) height;
		int x1 = (int) getXBase();
		int y1 = (int) getYBase();
		if (subPixelResolution() && bounds!=null) {
			w = (int)(bounds.width);
			h = (int)(bounds.height);
			x1 = (int)(bounds.x);
			y1 = (int)(bounds.y);
		}
		int x2 = x1+w/2;
		int y2 = y1+h/2;
		int x3 = x1+w;
		int y3 = y1+h;
		if (stroke!=null)
			g2d.setStroke(getScaledStroke());
		setRenderingHint(g2d);
		if (cornerDiameter>0) {
			int sArcSize = (int)Math.round(cornerDiameter*mag*scaleXY[0]);
			if (fillColor!=null && fill) {
				g2d.fillRoundRect(x1, y1, w, h, sArcSize, sArcSize);
			}
			g2d.drawRoundRect(x1, y1, w, h, sArcSize, sArcSize);
		} else {
			if (fillColor!=null && fill) {
				if (!overlay && isActiveOverlayRoi()) {
					g2d.setColor(Color.cyan);
					g2d.drawRect(x1, y1, w, h);
				} else {
					if (!(this instanceof TextRoi)) {
						g2d.fillRect(x1, y1, w, h);
						g2d.drawRect(x1, y1, w, h);
					} else {
						g2d.drawRect(x1, y1, w, h);
					}
				}
			} else {
				g2d.drawRect(x1, y1, w, h);
			}
		}
		if (clipboard==null && !overlay) {
			drawHandle(g, x1, y1);
			drawHandle(g, x2, y1);
			drawHandle(g, x3, y1);
			drawHandle(g, x3, y2);
			drawHandle(g, x3, y3);
			drawHandle(g, x2, y3);
			drawHandle(g, x1, y3);
			drawHandle(g, x1, y2);
		}
	}

	/**
	 * 
	 * @param g
	 * @param x
	 * @param y
	 */
	public void drawHandle(Graphics g, int x, int y) {
		
		Graphics2D g2d = (Graphics2D)g;
		setRenderingHint(g2d);
		
		int threshold1 = 7500;
		double size = (this.width * this.height);
		if (this instanceof Line) {
			size = ((Line) this).getLength();
			threshold1 = 150;
		} else {
			if (state == CONSTRUCTING && !(type == RoiType.RECTANGLE.id() || type == RoiType.OVAL.id()))
				size = threshold1 + 1;
		}
		int width = 5;
		int x0 = x, y0 = y;
		if (size > threshold1) {
			x -= 2;
			y -= 2;
		} else {
			x -= 1;
			y -= 1;
			width = 3;
		}
		int inc = getHandleSize() - 7;
		width += inc;
		x -= inc / 2;
		y -= inc / 2;
		g2d.setColor(Color.darkGray);
		if (width < 3) {
			g2d.fillRect(x0, y0, 1, 1);
			return;
		}
		g2d.fillRect(x, y, width, width);
		handleColor = strokeColor != null? strokeColor: ROIColor;
		g2d.setColor(handleColor);
		width -= 2;
		x++; y++;
		g2d.fillRect(x, y, width, width);
	}

	/**
	 * Draws the selection outline on the specified ImageProcessor.
	 * 
	 * @see ij.process.ImageProcessor#setColor
	 * @see ij.process.ImageProcessor#setLineWidth
	 */
	public void drawPixels(ImageProcessor ip) {
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

	void drawPreviousRoi(Graphics g) {
		if (previousRoi != null && previousRoi != this && previousRoi.modState != NO_MODS) {
			if (type != RoiType.POINT.id() && previousRoi.getType() == RoiType.POINT.id()
					&& previousRoi.modState != SUBTRACT_FROM_ROI)
				return;
			previousRoi.setImage(imp);
			previousRoi.draw(g);
		}
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

	public void enableSubPixelResolution() {
		bounds = new java.awt.geom.Rectangle2D.Double(getXBase(), getYBase(), getFloatWidth(), getFloatHeight());
		subPixel = true;
	}

	/** Checks whether two rectangles are equal. */
	public boolean equals(Object obj) {
		if (obj instanceof RoiObj) {
			RoiObj roi2 = (RoiObj) obj;
			if (isThisRoi(roi2)) {
				if (type != roi2.getType())
					return false;
				if (!getBounds().equals(roi2.getBounds()))
					return false;
				if (getLength() != roi2.getLength())
					return false;
				return true;
			}
			return false;
		} else
			return false;
	}

	protected double[] fArray2dArray(float[] floatp) {
		if (floatp == null) {
			return null;
		}
		double[] da = new double[floatp.length];
		for (int i = 0; i < floatp.length; i++) {
			da[i] = (double) floatp[i];
		}
		return da;
	}
	
	/** Overridden by PolygonRoi (angle between first two points), TextRoi (text angle) and Line (line angle). */
	public double getAngle() {
		return 0.0;
	}

	/**
	 * Returns the angle in degrees between the specified line and a horizontal
	 * line.
	 */
	public double getAngle(int x1, int y1, int x2, int y2) {
		return getFloatAngle(x1, y1, x2, y2);
	}

	public boolean getAntiAlias() {
		return antiAlias;
	}

	/** Return this selection's bounding rectangle. */
	public Rectangle getBounds() {
		return new Rectangle(x, y, width, height);
	}

	/**
	 * Returns the coordinates of the pixels inside this ROI as a FloatPolygon.
	 * 
	 * @see #getContainedPoints()
	 * @see #iterator()
	 */
	public FloatPolygon getContainedFloatPoints() {
		RoiObj roi2 = this;
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
	 * Returns the coordinates of the pixels inside this ROI as an array of Points.
	 * 
	 * @see #getContainedFloatPoints()
	 * @see #iterator()
	 */
	public Point[] getContainedPoints() {
		RoiObj roi = this;
		if (isLine())
			roi = convertLineToArea(this);
		ImageProcessor mask = roi.getMask();
		Rectangle bounds = roi.getBounds();
		ArrayList<Point> points = new ArrayList<>();
		for (int y=0; y<bounds.height; y++) {
			for (int x=0; x<bounds.width; x++) {
				if (mask==null || mask.getPixel(x,y)!=0)
					points.add(new Point(roi.x+x,roi.y+y));
			}
		}
		return (Point[])points.toArray(new Point[points.size()]);
	}

	/*
	 * Returns the center of the of this selection's countour, or the center of the
	 * bounding box of composite selections.<br> Author: Peter Haub (phaub at
	 * dipsystems.de)
	 */
	public double[] getContourCentroid() {
		double xC = 0, yC = 0, lSum = 0, x, y, dx, dy, l;
		FloatPolygon poly = getFloatPolygon();
		int nPoints = poly.npoints;
		int n2 = nPoints - 1;
		for (int n1 = 0; n1 < nPoints; n1++) {
			dx = poly.xpoints[n1] - poly.xpoints[n2];
			dy = poly.ypoints[n1] - poly.ypoints[n2];
			x = poly.xpoints[n2] + dx / 2.0;
			y = poly.ypoints[n2] + dy / 2.0;
			l = Math.sqrt(dx * dx + dy * dy);
			xC += x * l;
			yC += y * l;
			lSum += l;
			n2 = n1;
		}
		xC /= lSum;
		yC /= lSum;
		return new double[] { xC, yC };
	}
	
	/** Returns the convex hull of this Roi as a Polygon with integer coordinates
	 *  by rounding the floating-point values.
	 *  Coordinates of the convex hull are image pixel coordinates. */
	public Polygon getConvexHull() {
		FloatPolygon fp = getFloatConvexHull();
		return new Polygon(toIntR(fp.xpoints), toIntR(fp.ypoints), fp.npoints);
	}

	/** Returns the convex hull of this Roi as a FloatPolygon.
	 *  Coordinates of the convex hull are image pixel coordinates. */
	public FloatPolygon getFloatConvexHull() {
		FloatPolygon fp = getFloatPolygon("");   //no duplicate closing points, no path-separating NaNs needed
		return fp == null ? null : fp.getConvexHull();
	}
	
	/** Returns the rounded rectangle corner diameter (pixels). */
	public int getCornerDiameter() {
		return cornerDiameter;
	}

	/**
	 * Returns the channel position of this ROI, or zero if this ROI is not
	 * associated with a particular channel.
	 */
	public final int getCPosition() {
		return channel;
	}

	public String getDebugInfo() {
		return "";
	}

	/** Returns the default handle size. */
	public int getDefaultHandleSize() {
		if (defaultHandleSize > 0)
			return defaultHandleSize;
		double defaultWidth = defaultStrokeWidth();
		int size = 7;
		if (defaultWidth > 1.5)
			size = 9;
		if (defaultWidth >= 3)
			size = 11;
		if (defaultWidth >= 4)
			size = 13;
		if (defaultWidth >= 5)
			size = 15;
		if (defaultWidth >= 11)
			size = (int) defaultWidth;
		defaultHandleSize = size;
		return defaultHandleSize;
	}

	/**
	 * Returns true if this is a PolygonRoi that supports sub-pixel resolution and
	 * polygons are drawn on zoomed images offset down and to the right by 0.5
	 * pixels..
	 */
	public boolean getDrawOffset() {
		return false;
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

	/**
	 * Returns Feret's diameter, the greatest distance between any two points along
	 * the ROI boundary.
	 */
	public double getFeretsDiameter() {
		double[] a = getFeretValues();
		return a != null ? a[0] : 0.0;
	}

	/** Returns an array with the following values:
	 *  <br>[0] "Feret" (maximum caliper width)
	 *  <br>[1] "FeretAngle" (angle of diameter with maximum caliper width, between 0 and 180 deg)
	 *  <br>[2] "MinFeret" (minimum caliper width)
	 *  <br>[3][4] , "FeretX" and "FeretY", the X and Y coordinates of the starting point
	 *  (leftmost point) of the maximum-caliper-width diameter.
	 *  <br>[5-7] reserved
	 *  <br>All these values and point coordinates are in calibrated image coordinates.
	 *  <p>
	 *  The following array elements are end points of the maximum and minimum caliper diameter,
	 *  in unscaled image pixel coordinates:
	 *  <br>[8][9]   "FeretX1", "FeretY1"; unscaled versions of "FeretX" and "FeretY"
	 *  (subclasses may use any end of the diameter, not necessarily the left one)
	 *  <br>[10][11] "FeretX2", "FeretY2", end point of the maxium-caliper-width diameter.
	 *  Both of these points are vertices of the convex hull.
	 *  <br> The final four array elements are the starting and end points of the minimum caliper width,
	 *  <br>[12],[13] "MinFeretX", "MinFeretY", and
	 *  <br>[14],[15] "MinFeretX2", "MinFeretY2". These two pooints are not sorted by x,
	 *  but the first point point (MinFeretX, MinFeretY) is guaranteed to be a vertex of the convex hull,
	 *  while second point (MinFeretX2, MinFeretY2) usually is not a vertex point but at a
	 *  boundary line of the convex hull. */
	public double[] getFeretValues() {
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}

		FloatPolygon poly = getFloatConvexHull();
		if (poly==null || poly.npoints==0) return null;

		double[] a = new double[FERET_ARRAYSIZE];
		// calculate maximum Feret diameter: largest distance between any two points
		int p1=0, p2=0;
		double diameterSqr = 0.0;  //square of maximum Feret diameter
		for (int i=0; i<poly.npoints; i++) {
			for (int j=i+1; j<poly.npoints; j++) {
				double dx = (poly.xpoints[i] - poly.xpoints[j])*pw;
				double dy = (poly.ypoints[i] - poly.ypoints[j])*ph;
				double dsqr = dx*dx + dy*dy;
				if (dsqr>diameterSqr) {diameterSqr=dsqr; p1=i; p2=j;}
			}
		}
        if (poly.xpoints[p1] > poly.xpoints[p2]) {
            int p2swap = p1; p1 = p2; p2 = p2swap;
        }
		double xf1=poly.xpoints[p1], yf1=poly.ypoints[p1];
		double xf2=poly.xpoints[p2], yf2=poly.ypoints[p2];
		double angle = (180.0/Math.PI)*Math.atan2((yf1-yf2)*ph, (xf2-xf1)*pw);
		if (angle < 0.0)
			angle += 180.0;
		a[0] = Math.sqrt(diameterSqr);
		a[1] = angle;
		a[3] = xf1; a[4] = yf1;
		{ int i = FERET_ARRAY_POINTOFFSET;     //array elements 8-11 are start and end points of max Feret diameter
			a[i++] = poly.xpoints[p1]; a[i++] = poly.ypoints[p1];
			a[i++] = poly.xpoints[p2]; a[i++] = poly.ypoints[p2];
		}

		// Calculate minimum Feret diameter:
		// For all pairs of points on the convex hull:
		//   Get the point with the largest distance from the line between these two points
		//   Of all these pairs, take the one where the distance is the lowest
		// The following code requires a counterclockwise convex hull with no duplicate points
		double x0 = poly.xpoints[poly.npoints-1];
		double y0 = poly.ypoints[poly.npoints-1];
		double minFeret = Double.MAX_VALUE;
		double[] xyEnd = new double[4];        //start and end points of the minFeret diameter, uncalibrated
		double[] xyEi  = new double[4];        //intermediate values of xyEnd
		for (int i=0; i<poly.npoints; i++) {   //find caliper width for one side of calipers touching points i-1, i
			double xprev = x0;
			double yprev = y0;
			x0 = poly.xpoints[i];
			y0 = poly.ypoints[i];
			double xnorm = (y0 - yprev) * ph;
			double ynorm = (xprev - x0) * pw;
			double normalizationFactor = 1/Math.sqrt(xnorm*xnorm + ynorm*ynorm);
			xnorm *= normalizationFactor * pw; //normalized vector perpendicular to line between i-1, i; * scale factor for product below
			ynorm *= normalizationFactor * ph;
			double maxDist = 0;
			for (int j=0; j<poly.npoints; j++) {
				double x1 = poly.xpoints[j];
				double y1 = poly.ypoints[j];
				double dx = x1 - x0;
				double dy = y1 - y0;
				double dist = dx*xnorm + dy*ynorm;
				if (dist > maxDist) {
					maxDist = dist;
					xyEi[0] = x1;
					xyEi[1] = y1;
					xyEi[2] = xyEi[0] - (xnorm/pw * dist)/pw;
					xyEi[3] = xyEi[1] - (ynorm/ph * dist)/ph;
				}
			}
			if (maxDist < minFeret) {
				minFeret = maxDist;
				System.arraycopy(xyEi, 0, xyEnd, 0, 4);
			}
		}
		a[2] = minFeret;
		System.arraycopy(xyEnd, 0, a, FERET_ARRAY_POINTOFFSET+4, 4);    //a[12]-a[15] are minFeretX, Y, X2, Y2
		return a;
	}

	/**
	 * Returns the fill color used to display this ROI, or null if it is displayed
	 * transparently.
	 * 
	 * @see #setFillColor
	 * @see #getStrokeColor
	 */
	public Color getFillColor() {
		return fillColor;
	}

	/**
	 * Returns the angle in degrees between the specified line and a horizontal
	 * line.
	 */
	public double getFloatAngle(double x1, double y1, double x2, double y2) {
		double dx = x2 - x1;
		double dy = y1 - y2;
		if (imp != null && !IJ.altKeyDown()) {
			Calibration cal = imp.getCalibration();
			dx *= cal.pixelWidth;
			dy *= cal.pixelHeight;
		}
		return (180.0 / Math.PI) * Math.atan2(dy, dx);
	}

	/** Return this selection's bounding rectangle. */
	public java.awt.geom.Rectangle2D.Double getFloatBounds() {
		if (bounds != null)
			return new java.awt.geom.Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
		else
			return new java.awt.geom.Rectangle2D.Double(x, y, width, height);
	}

	public double getFloatHeight() {
		if (bounds != null)
			return bounds.height;
		else
			return height;
	}

	/** Returns the outline of this selection as a FloatPolygon */
	public FloatPolygon getFloatPolygon() {
		if (cornerDiameter>0) {  // Rounded Rectangle
			com.vis.core.view.D2.roi.ShapeRoi s = new ShapeRoi(this);
			return s.getFloatPolygon();
		} else if (subPixelResolution() && bounds!=null) {
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
	
	/** Returns the outline in image pixel coordinates,
	 *  where options may include "close" to add a point to close the outline
	 *  if this is an area roi and the outline is not closed yet.
	 *  (For ShapeRois, "separate" inserts NaN values between subpaths). */
	public FloatPolygon getFloatPolygon(String options) {
		options = options.toLowerCase();
		boolean addPointForClose = options.indexOf("close") >= 0;
		FloatPolygon fp = getFloatPolygon();
		int n = fp.npoints;
		if (isArea() && n > 1) {
			boolean isClosed = fp.xpoints[0] == fp.xpoints[n-1] && fp.ypoints[0] == fp.ypoints[n-1];
			if (addPointForClose && !isClosed)
				fp.addPoint(fp.xpoints[0], fp.ypoints[0]);
			else if (!addPointForClose && isClosed)
				fp.npoints--;
		}
		return fp;
	}

	public double getFloatWidth() {
		if (bounds != null)
			return bounds.width;
		else
			return width;
	}

	/** Returns the current handle size. */
	public int getHandleSize() {
		if (handleSize >= 0)
			return handleSize;
		else
			return getDefaultHandleSize();
	}
	
	/**
	 * Returns a hashcode for this Roi that typically changes if it is moved, even
	 * though it is still the same object.
	 */
	public int getHashCode() {
		return hashCode() ^ (Double.valueOf(getXBase()).hashCode())
				^ Integer.rotateRight(Double.valueOf(getYBase()).hashCode(), 16);
	}

	/** Returns the ImagePlus associated with this ROI, or null. */
	public ImagePlus getImage() {
		return imp;
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
		FloatPolygon p = (this instanceof Line)?((Line)this).getFloatPoints():getFloatPolygon();
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
		if (npoints<2)
			return p;
		if (Math.abs(interval)<0.01) {
			IJ.error("Interval must be >= 0.01");
			return p;
		}
		
		if (!isLine) {//**append (and later remove) closing point to end of array
			npoints++;
			p.xpoints = java.util.Arrays.copyOf(p.xpoints, npoints);
			p.xpoints[npoints - 1] = p.xpoints[0];
			p.ypoints = java.util.Arrays.copyOf(p.ypoints, npoints);
			p.ypoints[npoints - 1] = p.ypoints[0];
		}
		int npoints2 = (int) (10 + (length * 1.5) / interval);//allow some headroom

		double tryInterval = interval;
		double minDiff = 1e9;
		double bestInterval = 0;
		int srcPtr = 0;//index of source polygon
		int destPtr = 0;//index of destination polygon
		double[] destXArr = new double[npoints2];
		double[] destYArr = new double[npoints2];
		int nTrials = 50;
		int trial = 0;
		while (trial <= nTrials) {
			destXArr[0] = p.xpoints[0];
			destYArr[0] = p.ypoints[0];
			srcPtr = 0;
			destPtr = 0;
			double xA = p.xpoints[0];//start of current segment
			double yA = p.ypoints[0];

			while (srcPtr < npoints - 1) {//collect vertices
				double xC = destXArr[destPtr];//center circle
				double yC = destYArr[destPtr];
				double xB = p.xpoints[srcPtr + 1];//end of current segment
				double yB = p.ypoints[srcPtr + 1];
				double[] intersections = lineCircleIntersection(xA, yA, xB, yB, xC, yC, tryInterval, true);
				if (intersections.length >= 2) {
					xA = intersections[0];//only use first of two intersections
					yA = intersections[1];
					destPtr++;
					destXArr[destPtr] = xA;
					destYArr[destPtr] = yA;
				} else {
					srcPtr++;//no intersection found, pick next segment
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

			double diff = lastSeg - tryInterval;//always <= 0
			if (Math.abs(diff) < minDiff) {
				minDiff = Math.abs(diff);
				bestInterval = tryInterval;
			}
			double feedBackFactor = 0.66;//factor <1: applying soft successive approximation
			tryInterval = tryInterval + feedBackFactor * diff / nSegments;
			//stop if tryInterval < 80% of interval, OR if last segment differs < 0.05 pixels
			if ((tryInterval < 0.8 * interval || Math.abs(diff) < 0.05 || trial == nTrials - 1) && trial < nTrials) {
				trial = nTrials;//run one more loop with bestInterval to get best polygon
				tryInterval = bestInterval;
			} else
				trial++;
		}
		if (!isLine) //**remove closing point from end of array
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

	/** Returns the perimeter length. */
	public double getLength() {
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		double perimeter = 2.0*width*pw + 2.0*height*ph;
		if (cornerDiameter > 0) {      //using Ramanujan's approximation for the circumference of an ellipse
			double a = 0.5*Math.min(cornerDiameter, width)*pw;
			double b = 0.5*Math.min(cornerDiameter, height)*ph;
			 perimeter += Math.PI*(3*(a + b) - Math.sqrt((3*a + b)*(a + 3*b))) -4*(a+b);
		}
		return perimeter;
	}

	/** Always returns null for rectangular Roi's */
	public ImageProcessor getMask() {
		if (cornerDiameter>0)
			return new ShapeRoi(new RoundRectangle2D.Float(x, y, width, height, cornerDiameter, cornerDiameter), getSlideGlass()).getMask();
		else
			return null;
	}
	
	protected double getMagnification() {
		return slide!=null?slide.getMagnification():1.0;
	}

	public int getModificationState() {
		return this.modState;
	}

	/** Returns the name of this ROI, or null. */
	public String getName() {
		return getProperty(ContextKey.Name.name());
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

	/**
	 * Returns the outline of this selection as a Polygon, or null if this is a
	 * straight line selection.
	 * 
	 * @see ij.process.ImageProcessor#setRoi
	 * @see ij.process.ImageProcessor#drawPolygon
	 * @see ij.process.ImageProcessor#fillPolygon
	 */
	public Polygon getPolygon() {
		int[] xpoints = new int[4];
		int[] ypoints = new int[4];
		xpoints[0] = x;
		ypoints[0] = y;
		xpoints[1] = x + width;
		ypoints[1] = y;
		xpoints[2] = x + width;
		ypoints[2] = y + height;
		xpoints[3] = x;
		ypoints[3] = y + height;
		return new Polygon(xpoints, ypoints, 4);
	}

	/**
	 * Returns the stack position (image number) of this ROI, or zero if the ROI is
	 * not associated with a particular stack image.
	 * 
	 * @see ij.gui.Overlay
	 */
	public int getPosition() {
		return position;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public String getProperties() {
		if (props == null)
			return null;
		Vector v = new Vector();
		for (Enumeration en = props.keys(); en.hasMoreElements();)
			v.addElement(en.nextElement());
		String[] keys = new String[v.size()];
		for (int i = 0; i < keys.length; i++)
			keys[i] = (String) v.elementAt(i);
		Arrays.sort(keys);
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < keys.length; i++) {
			sb.append(keys[i]);
			sb.append(": ");
			sb.append(props.get(keys[i]));
			sb.append("\n");
		}
		return sb.toString();
	}

	public String getProperty(ContextKey conkey) {
		return getProperty(conkey.name());
	}
	
	public String getProperty(String property) {
		if (props == null) {
			return null;
		} else {
			// if integer
			if (property.equals(ContextKey.RoiType.name())) {
				return String.valueOf(getType());
			} else if (property.equals(ContextKey.RoiGroup.name())) {
				// basicaly, roiGroup was saved as string.
				// here, return sa-is
			} else if (property.equals(ContextKey.InstanceNo.name())) {
				// here, return sa-is
			}
			// else
			return props.getProperty(property);
		}
	}

	public int getPropertyCount() {
		if (props == null)
			return 0;
		else
			return props.size();
	}

	public ImageStatistics getRawStatistics(ImagePlus imp) {
		if (imp == null) {
			return null;
		}
		RoiObj roi = this;
		ImageProcessor ip = imp.getProcessor();
		boolean noImage = (ip == null);
		Rectangle bounds = null;
		if (noImage) {
			roi = (RoiObj) this.clone();
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
			stats.xCentroid += bounds.x;
			stats.yCentroid += bounds.y;
		}
		ip.resetRoi();
		return stats;
	}

	public RoiType getRoiType() {
		return RoiType.find(type);
	}

	public FloatPolygon getRotationCenter() {
		FloatPolygon p = new FloatPolygon();
		Rectangle2D r = getFloatBounds();
		if (Double.isNaN(xcenter)) {
			xcenter = r.getX() + r.getWidth() / 2.0;
			ycenter = r.getY() + r.getHeight() / 2.0;
		}
		p.addPoint(xcenter, ycenter);
		return p;
	}

	/** Obsolete; replaced by getCornerDiameter(). */
	public int getRoundRectArcSize() {
		return cornerDiameter;
	}
	
	public double[] getComponentScaleFactor() {
		return slide != null ? slide.getScaleFactor():new double[] {1,1}; 
	}

	public BasicStroke getScaledStroke() {
		if (slide==null || usingDefaultStroke || !scaleStrokeWidth)
			return stroke;
		double mag = getMagnification();
		if (mag != 1.0) {
			/*
			 * ignoring component scale factor ? tatsu 
			 */
			float width = (float) (stroke.getLineWidth() * mag);
			// return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
			return new BasicStroke(width, stroke.getEndCap(), stroke.getLineJoin(), stroke.getMiterLimit(),
					stroke.getDashArray(), stroke.getDashPhase());
		} else
			return stroke;
	}

	public SlideGlass getSlideGlass() {
		return slide;
	}

	public int getState() {
		return state;
	}

	public ImageStatistics getStatistics() {
		return getStatistics(this.imp);
	}
	
	public ImageStatistics getStatistics(ImagePlus imp) {
		if (imp == null) {
			return null;
		}
		RoiObj roi = this;
		ImageProcessor ip = imp.getProcessor();
		boolean noImage = (ip == null);
		Rectangle bounds = null;
		if (noImage) {
			roi = (RoiObj) this.clone();
			bounds = roi.getBounds();
			ip = new ByteProcessor(bounds.width, bounds.height);
			roi.setLocation(0, 0);
		}
		ij.gui.Roi ijRoi = new RoiConverter().convert2Roi(roi);
		ip.setRoi(ijRoi);
		/*
		 * public static ImageStatistics getStatistics​(ImageProcessor ip, int
		 * mOptions,Calibration cal) Calculates and returns statistics for the specified
		 * image using the specified measurent options and calibration. Use
		 * ImageProcessor.setRoi(x,y,width,height) to limit statistics to a rectangular
		 * area and ImageProcessor.setRoi(Roi) to limit to a non-rectangular area.
		 */
		ImageStatistics stats = ImageStatistics.getStatistics(ip, -1, imp.getCalibration());
		if (noImage) {
			stats.mean = stats.min = stats.max = Double.NaN;
			stats.xCentroid += bounds.x;
			stats.yCentroid += bounds.y;
		}
		ip.resetRoi();
		return stats;
	}

	/** Returns the Stroke used to draw this ROI, or null if no Stroke is used. */
	public BasicStroke getStroke() {
		return stroke;
	}

	/**
	 * Returns the the color used to draw the ROI outline or null if the default
	 * color is being used.
	 * 
	 * @see #setStrokeColor(Color)
	 */
	public Color getStrokeColor() {
		return strokeColor;
	}

	/** Returns the lineWidth. */
	public float getStrokeWidth() {
		return stroke != null ? stroke.getLineWidth() : 0f;
	}

	/**
	 * Returns the frame position of this ROI, or zero if this ROI is not associated
	 * with a particular frame.
	 */
	public final int getTPosition() {
		return frame;
	}

	public int getType() {
		return type;
	}
	
	/** Convenience method that converts Roi type to a human-readable form. */
	public String getTypeAsString() {
		String s = "";
		RoiType t = RoiType.find(type);
		switch (t) {
		case POLYGON:
			s = "Polygon";
			break;
		case FREEROI:
			s = "Freehand";
			break;
		case TRACED_ROI:
			s = "Traced";
			break;
		case POLYLINE:
			s = "Polyline";
			break;
		case FREELINE:
			s = "Freeline";
			break;
		case ANGLE:
			s = "Angle";
			break;
		case LINE:
			s = "Straight Line";
			break;
		case OVAL:
			s = "Oval";
			break;
		case COMPOSITE:
			s = "Composite";
			break;
		case POINT:
			s = "Point";
			break;
		default:
			// tatsu
				if (this instanceof TextRoi)
					s = "Text";
				else if (this instanceof ImageRoi)
					s = "Image";
				else
					s = "Rectangle";
			break;
		}
		return s;
	}

	public HashMap<ContextKey, String> getUIDs() {
		HashMap<ContextKey, String> info = new HashMap<>();
		info.put(ContextKey.PatientID, getProperty(ContextKey.PatientID.name()));
		info.put(ContextKey.StudyInstanceUID, getProperty(ContextKey.StudyInstanceUID.name()));
		info.put(ContextKey.SeriesInstanceUID, getProperty(ContextKey.SeriesInstanceUID.name()));
		info.put(ContextKey.SOPInstanceUID, getProperty(ContextKey.SOPInstanceUID.name()));
		info.put(ContextKey.RoiID, getProperty(ContextKey.RoiID.name()));
		return info;
	}

	public double getXBase() {
		if (bounds != null)
			return bounds.x;
		else
			return x;
	}

	public double getYBase() {
		if (bounds != null)
			return bounds.y;
		else
			return y;
	}

	/**
	 * Returns the slice position of this ROI, or zero if this ROI is not associated
	 * with a particular slice.
	 */
	public final int getZPosition() {
		return slice == 0 && !hyperstackPosition ? position : slice;
	}

	protected void grow(int sx, int sy) {
		int xNew = offScreenX(sx);
		int yNew = offScreenY(sy);
		if (type==RoiType.RECTANGLE.id()) {
			if (xNew < 0) xNew = 0;
			if (yNew < 0) yNew = 0;
		}
		if (constrain) {
			// constrain selection to be square
			if (!center) {
				growConstrained(xNew, yNew);
				return;
			}
			int dx, dy, d;
			dx = xNew - x;
			dy = yNew - y;
			if (dx<dy)
				d = dx;
			else
				d = dy;
			xNew = x + d;
			yNew = y + d;
		}
		if (center) {
			width = Math.abs(xNew - startX)*2;
			height = Math.abs(yNew - startY)*2;
			x = startX - width/2;
			y = startY - height/2;
		} else {
			width = Math.abs(xNew - startX);
			height = Math.abs(yNew - startY);
			x = (xNew>=startX)?startX:startX - width;
			y = (yNew>=startY)?startY:startY - height;
			if (type==RoiType.RECTANGLE.id()) {
				if ((x+width) > xMax) width = xMax-x;
				if ((y+height) > yMax) height = yMax-y;
			}
		}
		updateClipRect();
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
		bounds = null;
		//bounds = new java.awt.geom.Rectangle2D.Double(x, y, width, height);

		/*
		 * OLD code
		 */
//		double mag = getMagnification();
//		double[] scaleXY = getComponentScaleFactor();
//		double widthOnOrg = Math.abs(sx - sg.mouseX) / mag / scale;
//		double heightOnOrg = Math.abs(sy - sg.mouseY) / mag / scale;
//
//		width = (int) widthOnOrg;
//		height = (int) heightOnOrg;
//
//		System.out.println("slide last clicked:" + sg.mouseX + " " + sg.mouseY);
//		System.out.println("rectangle size on original (w,h):" + width + " " + height);
//
//		/*
//		 * update roi location on org image
//		 */
//		x = (sx >= sg.mouseX) ? x : sg.onImageX(sg.mouseX) - width;
//		y = (sy >= sg.mouseY) ? y : sg.onImageY(sg.mouseY) - height;
////		System.out.println("Growing result info: "+x+" "+y+" "+width+" "+height);
//
//		if (type == RoiType.RECTANGLE.id()) {
//			if ((x + width) > xMax) {
//				width = xMax - x;
//			}
//			if ((y + height) > yMax) {
//				height = yMax - y;
//			}
//		}
		
	}

	private void growConstrained(int xNew, int yNew) {
		int dx = xNew - startX;
		int dy = yNew - startY;
		width = height = (int) Math.round(Math.sqrt(dx * dx + dy * dy));
		if (type == RoiType.RECTANGLE.id()) {
			x = (xNew >= startX) ? startX : startX - width;
			y = (yNew >= startY) ? startY : startY - height;
			if (x < 0)
				x = 0;
			if (y < 0)
				y = 0;
			if ((x + width) > xMax)
				width = xMax - x;
			if ((y + height) > yMax)
				height = yMax - y;
		} else {
			x = startX + dx / 2 - width / 2;
			y = startY + dy / 2 - height / 2;
		}
		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
	}

	public void mouseDown(MouseEvent e) {
		int sx = e.getX();
		int sy = e.getY();
		int handleId = isHandle(sx, sy);
		setRoiModState(e, handleId);
		if(handleId < 0) {
			mouseDownWithoutHandle(sx, sy);
		}else {
			mouseDownInHandle(handleId, sx, sy);
		}
		previousSX = sx;
		previousSY = sy;
		
		if(state != CONSTRUCTING) {
			startX = offScreenX(sx);
			startY = offScreenY(sy);
			startXD = offScreenXD(sx);
			startYD = offScreenYD(sy);
		}
	}

	public void mouseDrag(int sx, int sy, int flags) {
		constrain = (flags & InputEvent.SHIFT_DOWN_MASK) != 0;
		center = (flags & InputEvent.CTRL_DOWN_MASK) != 0 || (Platform.isMac()&& (flags & InputEvent.META_DOWN_MASK) != 0);
		aspect = (flags & InputEvent.ALT_DOWN_MASK) != 0;
		switch (state) {
		case CONSTRUCTING:
			Log.logger.fine("GROW");
			grow(sx, sy);
			break;
		case MOVING:
			Log.logger.fine("MOVING");
			move(sx, sy);
			break;
		case MOVING_HANDLE:
			Log.logger.fine("MOVING_HANDLE");
			moveHandle(sx, sy);
			break;
		default:
			break;
		}
	}

	/**
	 * see also CanvasGlass:mouseReleased
	 * @param screenX
	 * @param screenY
	 */
	public void handleMouseUp(int screenX, int screenY) {
		setState(NORMAL);
		setModificationState(NO_MODS);
		Log.logger.fine("Roi RELEASED, state to be normal");
		modifyRoi();
	}

	/** Returns 'true' if setPosition(C,Z,T) has been called. */
	public boolean hasHyperStackPosition() {
		return hyperstackPosition;
	}

	protected void initUIDs(String[] uids) {
		if (uids == null) {
			setUIDs(null, null, null, null, createRoiIndex());
		} else {
			setUIDs(uids[0], uids[1], uids[2], uids[3], createRoiIndex());
		}
	}

	protected void initUIDsBySlideGlass(SlideGlass sg) {
		if (sg == null) {
			initUIDs(null);
			return;
		}
		String[] uids = sg.getUIDs();
		initUIDs(uids);
	}
	
	protected void updateUIDsBySlideGlass(SlideGlass sg) {
		if (sg == null) {
			initUIDs(null);
			return;
		}
		String[] uids = sg.getUIDs();
		setUIDs(uids[0], uids[1], uids[2], uids[3], getProperty(ContextKey.RoiID.name()));
	}

	/** Returns 'true' if this ROI is displayed and is also in an overlay. */
	public final boolean isActiveOverlayRoi() {
		return activeOverlayRoi;
	}

	/** Returns 'true' if this is an area selection. */
	public boolean isArea() {
		return (type >= RoiType.RECTANGLE.id() && type <= RoiType.TRACED_ROI.id()) || type == RoiType.COMPOSITE.id();
	}

	public boolean isCursor() {
		return isCursor;
	}

	/**
	 * Returns 'true' if this is an ROI primarily used from drawing (e.g., TextRoi
	 * or Arrow).
	 */
	public boolean isDrawingTool() {
		// return cornerDiameter>0;
		return false;
	}

	/**
	 * Returns a handle number if the specified screen coordinates are inside or
	 * near a handle, otherwise returns -1.
	 */
	public int isHandle(int sx, int sy) {
		int margin = IJ.getScreenSize().width > 1280 ? 7 : 5;
		int size = getHandleSize()+margin;
		int halfSize = size/2;
		double x = getXBase();
		double y = getYBase();
		double width = getFloatWidth();
		double height = getFloatHeight();
		int sx1 = screenXD(x) - halfSize;
		int sy1 = screenYD(y) - halfSize;
		int sx3 = screenXD(x+width) - halfSize;
		int sy3 = screenYD(y+height) - halfSize;
		int sx2 = sx1 + (sx3 - sx1)/2;
		int sy2 = sy1 + (sy3 - sy1)/2;
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy1&&sy<=sy1+size) return 0;
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy1&&sy<=sy1+size) return 1;
		if (sx>=sx3&&sx<=sx3+size&&sy>=sy1&&sy<=sy1+size) return 2;
		if (sx>=sx3&&sx<=sx3+size&&sy>=sy2&&sy<=sy2+size) return 3;
		if (sx>=sx3&&sx<=sx3+size&&sy>=sy3&&sy<=sy3+size) return 4;
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy3&&sy<=sy3+size) return 5;
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy3&&sy<=sy3+size) return 6;
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy2&&sy<=sy2+size) return 7;
		return -1;
	}

	/** Returns 'true' if this is a line selection. */
	public boolean isLine() {
		return type >= RoiType.LINE.id() && type <= RoiType.ANGLE.id() || type == RoiType.ARROW.id();
	}

	/** Return 'true' if this is a line or point selection. */
	protected boolean isLineOrPoint() {
		return isLine() || type == RoiType.POINT.id() || type == RoiType.MULTIPOINT.id();
	}

	public boolean isThisRoi(RoiObj roi) {
		HashMap<ContextKey, String> uids = roi.getUIDs();
		String patID = uids.get(ContextKey.PatientID);
		String studyUID = uids.get(ContextKey.StudyInstanceUID);
		String seriesUID = uids.get(ContextKey.SeriesInstanceUID);
		String sopUID = uids.get(ContextKey.SOPInstanceUID);
		String id = uids.get(ContextKey.RoiID);
		return isThisRoi(patID, studyUID, seriesUID, sopUID, id);
	}

	/**
	 * use equals() instead.
	 * 
	 * @param studyUID
	 * @param seriesUID
	 * @param sopUID
	 * @param roiInd
	 * @return
	 */
	public boolean isThisRoi(String patID, String studyUID, String seriesUID, String sopUID, String roiId) {
		HashMap<ContextKey, String> uids = getUIDs();
		String uid1 = uids.get(ContextKey.PatientID);
		String uid2 = uids.get(ContextKey.StudyInstanceUID);
		String uid3 = uids.get(ContextKey.SeriesInstanceUID);
		String uid4 = uids.get(ContextKey.SOPInstanceUID);
		String id = uids.get(ContextKey.RoiID);
		if (uid1 == null || uid2 == null || uid3 == null || id == null) {
			return false;
		}
		if (uid1.equals(patID) && uid2.equals(studyUID) && uid3.equals(seriesUID) && uid4.equals(sopUID)
				&& id.equals(roiId)) {
			return true;
		} else {
			return false;
		}
	}

	/** Returns true if this ROI is currently displayed on an image. */
	public boolean isVisible() {
		return slide != null;
	}
	
	public boolean isSelected() {
		return isSelected;
	}

	/**
	 * Required by the {@link Iterable} interface. Use to iterate over the contained
	 * coordinates. Usage example:
	 * 
	 * <pre>
	 * for (Point p : roi) {
	 * 	// process p
	 * }
	 * </pre>
	 * 
	 * Author: Wilhelm Burger
	 * 
	 * @see #getContainedPoints()
	 * @see #getContainedFloatPoints()
	 */
	@Override
	public Iterator<Point> iterator() {
		// Returns the default (mask-based) point iterator. Note that 'Line' overrides
		// the
		// iterator() method and returns a specific point iterator.
		return new RoiPointsIteratorMask();
	}

	protected void loadSettings() {
		/*
		 * "RoiStrokeColor" "RoiStrokeWidth" "RoiFillColor" "RoiHandleColor"
		 * "RoiBrushSize" "RoiBrushType"
		 */
		// strokeColor;
		String strokeColorString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiStrokeColor);
		this.strokeColor = colorFromString(strokeColorString, ROIColor);
		// fillColor;
		String fillColorString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiFillColor);
		this.fillColor = colorFromString(fillColorString, defaultFillColor);
		// handleColor
		String handleColorString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiHandleColor);
		this.handleColor = colorFromString(handleColorString, defaultHandleColor);
		//
		String lineWidthString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RoiStrokeWidth);
		if (lineWidthString != null) {
			try {
				double v = Double.parseDouble(lineWidthString.trim());
				setStrokeWidth(v);
				if(v != defaultStrokeWidth) {
					usingDefaultStroke = false;
				}else {
					usingDefaultStroke = true;
				}
			}catch(NumberFormatException e) {
				//do nothing
			}
		} else {
			double defaultWidth = defaultStrokeWidth();
			if (defaultWidth>0) {
				stroke = new BasicStroke((float)defaultWidth);
				usingDefaultStroke = true;
			}
		}
	}

	/**
	 * Returns whether a roi created interactively should have subpixel resolution,
	 * (if the roi type supports it), i.e., whether the magnification is high enough
	 */
	protected boolean magnificationForSubPixel() {
		return magnificationForSubPixel(getMagnification());
	}

	/**
	 * TODO
	 * future work, record modify history
	 */
	void modifyRoi() {
		if(getState() == CONSTRUCTING) {
			return;
		}
		//do something...
	}

	protected void mouseDownInHandle(int handle, int sx, int sy) {
		setState(MOVING_HANDLE);
		activeHandle = handle;
	}

	protected void mouseDownWithoutHandle(int sx, int sy) {
		if (state == NORMAL) {
			setState(MOVING);
		}
		activeHandle = -1;
	}

	public void mouseDragged(MouseEvent e) {
		mouseDrag(e.getX(), e.getY(), e.getModifiersEx());
	}
	
	// For each RoiObj child classes.
	public void mouseMoved(MouseEvent e) {}

	public void mouseReleased(MouseEvent e) {
		handleMouseUp(e.getX(), e.getY());
	}

	void move(int sx, int sy) {
		if (constrain) {  // constrain translation in 90deg steps
			int dx = sx - previousSX;
			int dy = sy - previousSY;
			if (Math.abs(dx) > Math.abs(dy))
				dy = 0;
			else
				dx = 0;
			sx = previousSX + dx;
			sy = previousSY + dy;
		}
		int xNew = offScreenX(sx);
		int yNew = offScreenY(sy);
		int dx = xNew - startX;
		int dy = yNew - startY;
		if (dx==0 && dy==0)
			return;
		x += dx;
		y += dy;
		if (bounds!=null && !(this instanceof ShapeRoi)){//tatsu
			setLocation(bounds.x + dx, bounds.y + dy);
		}else {
			setLocation(x, y);
		}
		boolean isImageRoi = this instanceof ImageRoi;
		if (clipboard==null && type==RoiType.RECTANGLE.id() && !isImageRoi) {
			if (x<0) x=0; if (y<0) y=0;
			if ((x+width)>xMax) x = xMax-width;
			if ((y+height)>yMax) y = yMax-height;
		}
		startX = xNew;
		startY = yNew;
		if (type==RoiType.POINT.id() || ((this instanceof TextRoi) && ((TextRoi)this).getAngle()!=0.0))
			ignoreClipRect = true;
		updateClipRect();
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight=height;
	}

	protected void moveHandle(int sx, int sy) {
		double asp;
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		if (ox < 0)
			ox = 0;
		if (oy < 0)
			oy = 0;
		if (ox > xMax)
			ox = xMax;
		if (oy > yMax)
			oy = yMax;
		
		@SuppressWarnings("unused")
		int x1 = x, y1 = y, x2 = x1 + width, y2 = y + height, xc = x + (width / 2), yc = y + (height / 2);
		
		if (width > 7 && height > 7) {
			asp = (double) width / (double) height;
			asp_bk = asp;
		} else
			asp = asp_bk;

		switch (activeHandle) {
		case 0:
			x = ox;
			y = oy;
			break;
		case 1:
			y = oy;
			break;
		case 2:
			x2 = ox;
			y = oy;
			break;
		case 3:
			x2 = ox;
			break;
		case 4:
			x2 = ox;
			y2 = oy;
			break;
		case 5:
			y2 = oy;
			break;
		case 6:
			x = ox;
			y2 = oy;
			break;
		case 7:
			x = ox;
			break;
		}
		if (x < x2)
			width = x2 - x;
		else {
			width = 1;
			x = x2;
		}
		if (y < y2)
			height = y2 - y;
		else {
			height = 1;
			y = y2;
		}

		if (center) {
			switch (activeHandle) {
			case 0:
				width = (xc - x) * 2;
				height = (yc - y) * 2;
				break;
			case 1:
				height = (yc - y) * 2;
				break;
			case 2:
				width = (x2 - xc) * 2;
				x = x2 - width;
				height = (yc - y) * 2;
				break;
			case 3:
				width = (x2 - xc) * 2;
				x = x2 - width;
				break;
			case 4:
				width = (x2 - xc) * 2;
				x = x2 - width;
				height = (y2 - yc) * 2;
				y = y2 - height;
				break;
			case 5:
				height = (y2 - yc) * 2;
				y = y2 - height;
				break;
			case 6:
				width = (xc - x) * 2;
				height = (y2 - yc) * 2;
				y = y2 - height;
				break;
			case 7:
				width = (xc - x) * 2;
				break;
			}
			if (x >= x2) {
				width = 1;
				x = x2 = xc;
			}
			if (y >= y2) {
				height = 1;
				y = y2 = yc;
			}
			bounds = new Rectangle2D.Double(x, y, width, height);
		}

		if (constrain) {
			if (activeHandle == 1 || activeHandle == 5)
				width = height;
			else
				height = width;

			if (x >= x2) {
				width = 1;
				x = x2 = xc;
			}
			if (y >= y2) {
				height = 1;
				y = y2 = yc;
			}
			switch (activeHandle) {
			case 0:
				x = x2 - width;
				y = y2 - height;
				break;
			case 1:
				x = xc - width / 2;
				y = y2 - height;
				break;
			case 2:
				y = y2 - height;
				break;
			case 3:
				y = yc - height / 2;
				break;
			case 5:
				x = xc - width / 2;
				break;
			case 6:
				x = x2 - width;
				break;
			case 7:
				y = yc - height / 2;
				x = x2 - width;
				break;
			}
			if (center) {
				x = xc - width / 2;
				y = yc - height / 2;
			}
			bounds = new Rectangle2D.Double(x, y, width, height);
		}

		if (aspect && !constrain) {
			if (activeHandle == 1 || activeHandle == 5) {
				width = (int) Math.rint((double) height * asp);
			} else {
				height = (int) Math.rint((double) width / asp);
			}

			switch (activeHandle) {
			case 0:
				x = x2 - width;
				y = y2 - height;
				break;
			case 1:
				x = xc - width / 2;
				y = y2 - height;
				break;
			case 2:
				y = y2 - height;
				break;
			case 3:
				y = yc - height / 2;
				break;
			case 5:
				x = xc - width / 2;
				break;
			case 6:
				x = x2 - width;
				break;
			case 7:
				y = yc - height / 2;
				x = x2 - width;
				break;
			}
			if (center) {
				x = xc - width / 2;
				y = yc - height / 2;
			}

			// Attempt to preserve aspect ratio when roi very small:
			if (width < 8) {
				if (width < 1)
					width = 1;
				height = (int) Math.rint((double) width / asp_bk);
			}
			if (height < 8) {
				if (height < 1)
					height = 1;
				width = (int) Math.rint((double) height * asp_bk);
			}
			bounds = new Rectangle2D.Double(x, y, width, height);
		}

		Log.logger.fine("MOVING_HANDLE INFO:" + "originX " + x + " originY " + y + " w " + width + " h " + height);
		bounds = new Rectangle2D.Double(x, y, width, height);

		updateClipRect();

		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
		bounds = null;
		subPixel = false;
	}

	public void notifyListeners(int id) {
		if (id == RoiListener.CREATED) {
			if (listenersNotified)
				return;
			listenersNotified = true;
		}
		synchronized (listeners) {
			for (int i = 0; i < listeners.size(); i++) {
				RoiListener listener = (RoiListener) listeners.elementAt(i);
				listener.roiModified(imp, id);
			}
		}
	}

	/** Nudge ROI one pixel on arrow key press. */
	public void nudge(int key, SlideGlass sg) {
		if (WindowManager.getActiveWindow() instanceof ij.plugin.frame.RoiManager)
			return;
		switch (key) {
		case KeyEvent.VK_UP:
			y--;
			if (y < 0 && (type != RoiType.RECTANGLE.id() || clipboard == null))
				y = 0;
			break;
		case KeyEvent.VK_DOWN:
			y++;
			if ((y + height) >= yMax && (type != RoiType.RECTANGLE.id() || clipboard == null))
				y = yMax - height;
			break;
		case KeyEvent.VK_LEFT:
			x--;
			if (x < 0 && (type != RoiType.RECTANGLE.id() || clipboard == null))
				x = 0;
			break;
		case KeyEvent.VK_RIGHT:
			x++;
			if ((x + width) >= xMax && (type != RoiType.RECTANGLE.id() || clipboard == null))
				x = xMax - width;
			break;
		}
		updateClipRect();
		if (type == RoiType.POINT.id())
			imp.draw();
		else
			imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x;
		oldY = y;
		bounds = null;
	}

	/**
	 * Nudge lower right corner of rectangular and oval ROIs by one pixel based on
	 * arrow key press.
	 */
	public void nudgeCorner(int key, SlideGlass sg) {
		if (type > RoiType.OVAL.id() || clipboard != null)
			return;
		switch (key) {
		case KeyEvent.VK_UP:
			height--;
			if (height < 1)
				height = 1;
//				notifyListeners(RoiListener.MODIFIED);//tatsu
			break;
		case KeyEvent.VK_DOWN:
			height++;
			if ((y + height) > yMax)
				height = yMax - y;
//				notifyListeners(RoiListener.MODIFIED);
			break;
		case KeyEvent.VK_LEFT:
			width--;
			if (width < 1)
				width = 1;
//				notifyListeners(RoiListener.MODIFIED);
			break;
		case KeyEvent.VK_RIGHT:
			width++;
			if ((x + width) > xMax)
				width = xMax - x;
//				notifyListeners(RoiListener.MODIFIED);
			break;
		}
		updateClipRect();
//		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX = x;
		oldY = y;
		cachedMask = null;
	}

	/*
	 * see also override another Roi.
	 */
	public HashMap<String, Object> readContext() {
		HashMap<String, Object> con = new HashMap<>();
		for (ContextKey k : ContextKey.values()) {
			String v = getProperty(k.name());
			if (v != null) {
				con.put(k.name(), v);//keep as String.
			}
		}
		con.put(RoiGeometry.OriginX.name(), (int) getXBase());
		con.put(RoiGeometry.OriginY.name(), (int) getYBase());
		con.put(RoiGeometry.Width.name(), width);
		con.put(RoiGeometry.Height.name(), height);
		con.put(RoiGeometry.PointX.name(), fArray2dArray(getFloatPolygon().xpoints));
		con.put(RoiGeometry.PointY.name(), fArray2dArray(getFloatPolygon().ypoints));
		// See also ShapeRoi::readContext(), roiToShape(RoiObj roi)
		con.put(RoiGeometry.Shape.name(), null);

		return con;
	}
	
	/** Converts slideglass x coordinates to integer offscreen image pixel
	 *  coordinates, depending on whether this roi uses the line or area convention
	 *  for coordinates. */
	public int offScreenX(int sx) {
		if (slide == null) return sx;
		return useLineSubpixelConvention() ? slide.offScreenX(sx) : slide.offScreenX2(sx);
	}
		
	/** Converts slideglass screen y coordinates to integer offscreen image pixel
	 *  coordinates, depending on whether this roi uses the line or area convention
	 *  for coordinates. */
	public int offScreenY(int sy) {
		if (slide == null) return sy;
		return useLineSubpixelConvention() ? slide.offScreenY(sy) : slide.offScreenY2(sy);
	}
	
	/** Converts slideglass screen x coordinates to floating-point offscreen image pixel
	 *  coordinates, depending on whether this roi uses the line or area convention
	 *  for coordinates. */
	protected double offScreenXD(int sx) {
		if (slide == null) return sx;
		double offScreenValue = slide.offScreenXD(sx);
		if (useLineSubpixelConvention())
			offScreenValue -= 0.5;
		return offScreenValue;
	}

	/** Converts slideglass screen y coordinates to floating-point offscreen image pixel
	 *  coordinates, depending on whether this roi uses the line or area convention
	 *  for coordinates. */
	protected double offScreenYD(int sy) {
		if (slide == null) return sy;
		double offScreenValue = slide.offScreenYD(sy);
		if (useLineSubpixelConvention())
			offScreenValue -= 0.5;
		return offScreenValue;
	}
	
	/**Converts an image pixel x (offscreen)coordinate to a screen x coordinate,
	 * taking the the line or area convention for coordinates into account */
	protected int screenXD(double ox) {
		if (slide==null) return (int)ox;
		if (useLineSubpixelConvention()) ox += 0.5;
		return slide!=null?(int)slide.screenXD(ox):(int)ox;
	}

	/**Converts an image pixel y (offscreen)coordinate to a screen y coordinate,
	 * taking the the line or area convention for coordinates into account */
	protected int screenYD(double oy) {
		if (slide==null) return (int)oy;
		if (useLineSubpixelConvention()) oy += 0.5;
		return slide!=null? (int)slide.screenYD(oy):(int)oy;
	}

	protected int screenX(int ox) {return screenXD(ox);}
	protected int screenY(int oy) {return screenYD(oy);}

	public void setActiveOverlayRoi(boolean active) {
		activeOverlayRoi = active;
	}

	public void setAntiAlias(boolean antiAlias) {
		this.antiAlias = antiAlias;
	}

	/** Sets the bounds of rectangular, oval or text selections. */
	public void setBounds(java.awt.geom.Rectangle2D.Double b) {
		if (!(type == RoiType.RECTANGLE.id() || type == RoiType.OVAL.id() || (this instanceof TextRoi)))
			return;
		this.x = (int) b.x;
		this.y = (int) b.y;
		this.width = (int) Math.ceil(b.width);
		this.height = (int) Math.ceil(b.height);
		bounds = new java.awt.geom.Rectangle2D.Double(b.x, b.y, b.width, b.height);
		cachedMask = null;
	}

	/** Sets the rounded rectangle corner diameter (pixels). */
	public void setCornerDiameter(int cornerDiameter) {
		if (cornerDiameter < 0)
			cornerDiameter = 0;
		this.cornerDiameter = cornerDiameter;
		if(slide != null) {
			slide.repaint();
		}
	}
	
	/**
	 * Sets the fill color used to display this ROI, or set to null to display it
	 * transparently.
	 * 
	 * @see #getFillColor
	 * @see #setStrokeColor
	 */
	public void setFillColor(Color color) {
		fillColor = color;
	}

	public void setFlattenScale(double scale) {
		flattenScale = scale;
	}

	/** Sets the current handle size. */
	public void setHandleSize(int size) {
		if (size >= 0 && ((size & 1) == 0))
			size++; // add 1 if odd
		handleSize = size;
	}

	public void setIgnoreClipRect(boolean ignoreClipRect) {
		this.ignoreClipRect = ignoreClipRect;
	}

	public void setImage(ImagePlus imp) {
		this.imp = imp;
		cachedMask = null;
		if (imp == null) {
			clipboard = null;
			xMax = yMax = Integer.MAX_VALUE;
		} else {
			xMax = imp.getWidth();
			yMax = imp.getHeight();
		}
	}

	/**
	 * Sets the integer boundaries x, y, width, height from given sub-pixel
	 * boundaries, such that all points are within the integer bounding rectangle.
	 * For open line selections and (multi)Point Rois, note that integer Roi
	 * coordinates correspond to the center of the 1x1 rectangle enclosing a pixel.
	 * Points at the boundary of such a rectangle are counted for the higher x or y
	 * value, in agreement to how (poly-)line or PointRois are displayed at the
	 * screen at high zoom levels. (For lines and points, it should include all
	 * pixels affected by 'draw'
	 */
	void setIntBounds(Rectangle2D.Double bounds) {
		if (useLineSubpixelConvention()) { // for PointRois & open lines, ensure the 'draw' area is enclosed
			x = (int) Math.floor(bounds.x + 0.5);
			y = (int) Math.floor(bounds.y + 0.5);
			width = (int) Math.floor(bounds.x + bounds.width + 1.5) - x;
			height = (int) Math.floor(bounds.y + bounds.height + 1.5) - y;
		} else { // for area Rois, the subpixel bounds must be enclosed in the int bounds
			x = (int) Math.floor(bounds.x);
			y = (int) Math.floor(bounds.y);
			width = (int) Math.ceil(bounds.x + bounds.width) - x;
			height = (int) Math.ceil(bounds.y + bounds.height) - y;
		}
	}

	public void setIsCursor(boolean isCursor) {
		this.isCursor = isCursor;
	}

	/** Set the location of the ROI in image coordinates. */
	public void setLocation(double x, double y) {
		setLocation((int) x, (int) y);
		if (isInteger(x) && isInteger(y))
			return;
		if (bounds != null) {
			if (!isInteger(x - bounds.x) || !isInteger(y - bounds.y)) {
				cachedMask = null;
				width = (int) Math.ceil(bounds.x + bounds.width) - this.x; // ensure that all pixels are inside
				height = (int) Math.ceil(bounds.y + bounds.height) - this.y;
			}
			bounds.x = x;
			bounds.y = y;
		} else {
			cachedMask = null;
			bounds = new Rectangle2D.Double(x, y, width, height);
		}
		if (this instanceof PolygonRoi)
			setIntBounds(bounds);
		subPixel = true;
	}

	/** Set the location of the ROI in image coordinates. */
	public void setLocation(int x/* imageX */, int y/* imageY */) {
		this.x = x;
		this.y = y;
		startX = x;
		startY = y;
		oldX = x;
		oldY = y;
		oldWidth = 0;
		oldHeight = 0;
		if (bounds != null) {
			if (!isInteger(bounds.x) || !isInteger(bounds.y)) {
				cachedMask = null;
				width = (int) Math.ceil(bounds.width);
				height = (int) Math.ceil(bounds.height);
			}
			bounds.x = x;
			bounds.y = y;
			if (this instanceof PolygonRoi)
				setIntBounds(bounds);
		}
	}

	public void setModificationState(int modState) {
		this.modState = modState;
	}

	/** Sets the name of this ROI. */
	public void setName(String name) {
		setProperty(ContextKey.Name.name(), name);
	}

	/**
	 * Set 'nonScalable' true to have TextRois in a display list drawn at a fixed
	 * location and size.
	 */
	public void setNonScalable(boolean nonScalable) {
		this.nonScalable = nonScalable;
	}

	/**
	 * Sets the position of this ROI based on the stack position of the specified
	 * image.
	 */
	public void setPosition(ImagePlus imp) {
		if (imp == null)
			return;
		if (imp.isHyperStack()) {
			int channel = imp.getDisplayMode() == IJ.COMPOSITE ? 0 : imp.getChannel();
			setPosition(channel, imp.getSlice(), imp.getFrame());
		} else if (imp.getStackSize() > 1)
			setPosition(imp.getCurrentSlice());
		else
			setPosition(0);
	}

	/**
	 * Sets the stack position (image number) of this ROI. In an overlay, this ROI
	 * is only displayed when the stack is at the specified position. Set to zero to
	 * have the ROI displayed on all images in the stack.
	 * 
	 * @see ij.gui.Overlay
	 */
	public void setPosition(int n) {
		if (n < 0)
			n = 0;
		position = n;
		channel = slice = frame = 0;
		hyperstackPosition = false;
	}

	/**
	 * Sets the hyperstack position of this ROI. In an overlay, this ROI is only
	 * displayed when the hyperstack is at the specified position.
	 * 
	 * @see ij.gui.Overlay
	 */
	public void setPosition(int channel, int slice, int frame) {
		if (channel < 0)
			channel = 0;
		this.channel = channel;
		if (slice < 0)
			slice = 0;
		this.slice = slice;
		if (frame < 0)
			frame = 0;
		this.frame = frame;
		position = 0;
		hyperstackPosition = true;
	}

	public void setProperties(HashMap<String, Object> roiCon) {
		if (roiCon == null || roiCon.size() < 1) {
			return;
		}
		if (props == null) {
			props = new Properties();
		} else {
			props.clear();
		}
		// read
		for(ContextKey key : ContextKey.values()) {
			Object v = roiCon.get(key.name());
			if(v != null) {
				if(v instanceof java.sql.Date || v instanceof java.util.Date) {
					SimpleDateFormat form = new SimpleDateFormat("yyyy/MM/dd");
					//sql.Date is a child class of util.Date.
					java.util.Date d = (java.util.Date)v;
					setProperty(key.name(), form.format(d));
					continue;
				}
				if(v instanceof Integer) {
					int num = (Integer)v;
					setProperty(key.name(), String.valueOf(num));
					continue;
				}
				setProperty(key.name(), (String)v);
				if(key == ContextKey.Name) {
					setName((String)v);
				}
			}
		}
	}
	
	public void setProperty(ContextKey key, String value) {
		setProperty(key.name(), value);
	}

	public void setProperty(String key, String value) {
		if (key == null) {
			return;
		}
		if (props == null) {
			props = new Properties();
		}
		if (value == null || value.length() == 0) {
			props.remove(key);
		} else {
			props.setProperty(key, value);
		}
	}

	protected void setRenderingHint(Graphics2D g2d) {
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}

	public void setRoiLabel(String name) {
		setProperty(ContextKey.RoiLabel.name(), name);
	}

	/**
	 * modification states NO_MODS=0 ADD_TO_ROI=1 SUBTRACT_FROM_ROI=2;
	 * 
	 * @param e
	 * @param handleId
	 */
	public void setRoiModState(MouseEvent e, int handleId) {
		if ((handleId >= 0 && getModificationState() == NO_MODS)) {
			return;
		}

		if (getState() == CONSTRUCTING) {
			return;
		}
		// TODO
//		int tool = Toolbar.getToolId();
//		if (tool>Toolbar.FREEROI && tool!=Toolbar.WAND && tool!=Toolbar.POINT)
//			{roi.modState = Roi.NO_MODS; return;}
		if (e.isShiftDown())
			setModificationState(ADD_TO_ROI);
		else if (e.isAltDown())
			setModificationState(SUBTRACT_FROM_ROI);
		else {
			setModificationState(NO_MODS);
		}
	}

	public void setRotationCenter(double x, double y) {
		xcenter = x;
		ycenter = y;
	}

	/** Obsolete; replaced by setCornerDiameter(). */
	public void setRoundRectArcSize(int cornerDiameter) {
		setCornerDiameter(cornerDiameter);
	}

	public void setSlideGlass(SlideGlass sg) {
		this.slide = sg;
		if(sg != null) {
			setImage(sg.getOriginalImage());
		}
		if(getProperty(ContextKey.RoiID.name()) == null) {
			initUIDsBySlideGlass(sg);
		}else {
			updateUIDsBySlideGlass(sg);
		}
	}

	public void setState(int state) {
		this.state = state;
	}
	
	public void setSelectedState(boolean select) {
		this.isSelected = select;
	}
	
	public void setFillState(boolean fill) {
		this.fill = fill;
	}

	/** Sets the Stroke used to draw this ROI. */
	public void setStroke(BasicStroke stroke) {
		this.stroke = stroke;
		if (stroke!=null)
			usingDefaultStroke = false;
	}

	/**
	 * Sets the color used by this ROI to draw its outline. This color, if not null,
	 * overrides the global color set by the static setColor() method.
	 * 
	 * @see #getStrokeColor
	 * @see #setStrokeWidth
	 * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
	 */
	public void setStrokeColor(Color c) {
		strokeColor = c;
	}

	/** This is a version of setStrokeWidth() that accepts a double argument. */
	public void setStrokeWidth(double width) {
		setStrokeWidth((float) width);
	}

	/**
	 * Sets the width of the line used to draw this ROI. Set the width to 0.0 and
	 * the ROI will be drawn using a a 1 pixel stroke width regardless of the
	 * magnification.
	 * 
	 * @see #setStrokeColor(Color)
	 * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
	 */
	public void setStrokeWidth(float strokeWidth) {
		if (strokeWidth<0f)
			strokeWidth = 0f;
		if (strokeWidth==0f && usingDefaultStroke)
			return;
		if (strokeWidth>0f) {
			scaleStrokeWidth = true;
			usingDefaultStroke = false;
		}
		boolean notify = listeners.size()>0 && isLine() && getStrokeWidth()!=strokeWidth;
		if (strokeWidth==0f)
			this.stroke = null;
		else if (wideLine)
			this.stroke = new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
		else
			this.stroke = new BasicStroke(strokeWidth);
		if (notify)
			notifyListeners(RoiListener.MODIFIED);
	}

	public void setType(RoiType t) {
		setType(t.id());
	}
	
	protected void setType(int type) {
		this.type = type;
	}

	protected void setUIDs(String pid, String studyUID, String seriesUID, String sopUID, String roiID) {
		setProperty(ContextKey.PatientID.name(), pid);
		setProperty(ContextKey.StudyInstanceUID.name(), studyUID);
		setProperty(ContextKey.SeriesInstanceUID.name(), seriesUID);
		setProperty(ContextKey.SOPInstanceUID.name(), sopUID);
		setProperty(ContextKey.RoiID.name(), roiID);
	}

	/**
	 * Returns the number of points in this selection; equivalent to
	 * getPolygon().npoints.
	 */
	public int size() {
		return getFloatPolygon().npoints;
	}

	/** Returns true if this is a slection that supports sub-pixel resolution. */
	public boolean subPixelResolution() {
		return subPixel;
	}

	void subtractPoints() {
		previousRoi.modState = NO_MODS;
		PointRoi p1 = (PointRoi) previousRoi;
		PointRoi p2 = p1.subtractPoints(this);
		if (p2 != null) {
			p1 = (PointRoi) p2.clone();
			p2 = null;
		}
	}
	
	public void translate(double dx, double dy) {
		boolean intArgs = (int)dx==dx && (int)dy==dy;
		if (subPixelResolution() || !intArgs) {
			Rectangle2D r = getFloatBounds();
			setLocation(r.getX()+dx, r.getY()+dy);
		} else {
			Rectangle r = getBounds();
			setLocation(r.x+(int)dx, r.y+(int)dy);
		}
	}

	public String toString() {
		return ("Roi[" + getTypeAsString() + ", x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]");
	}

	/**
	 * If 'add' is true, adds this selection to the previous one. If 'subtract' is
	 * true, subtracts it from the previous selection. Called by the IJ.doWand()
	 * method, and the makeRectangle(), makeOval(), makePolygon() and
	 * makeSelection() macro functions.
	 */
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

	// Finds the union of current and previous roi
	protected void updateClipRect() {
		clipX = (x <= oldX) ? x : oldX;
		clipY = (y <= oldY) ? y : oldY;
		clipWidth = ((x + width >= oldX + oldWidth) ? x + width : oldX + oldWidth) - clipX + 1;
		clipHeight = ((y + height >= oldY + oldHeight) ? y + height : oldY + oldHeight) - clipY + 1;
		int handleSize = getHandleSize();
		double mag = getMagnification();
		int m = mag<1.0?(int)(handleSize/mag):handleSize;
		m += clipRectMargin();
		double strokeWidth = getStrokeWidth();
		if (strokeWidth==0.0)
			strokeWidth = defaultStrokeWidth();
		m = (int)(m+strokeWidth*2);
		clipX-=m; clipY-=m;
		clipWidth+=m*2; clipHeight+=m*2;
	}

	public void updateWideLine(float width) {
		if (isLine()) {
			wideLine = true;
			setStrokeWidth(width);
			if (getStrokeColor() == null) {
				Color c = getColor();
				setStrokeColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 77));
			}
		}
	}

	/**
	 * Returns 'true' if this ROI uses for drawing the convention for line and point
	 * ROIs, where the coordinates are with respect to the pixel center. Returns
	 * false for area rois, which have coordinates with respect to the upper left
	 * corners of the pixels
	 */
	protected boolean useLineSubpixelConvention() {
		return isLineOrPoint();
	}
}
