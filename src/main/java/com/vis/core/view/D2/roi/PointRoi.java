package com.vis.core.view.D2.roi;

import ij.*;
import ij.process.*;
import ij.plugin.Colors;
import ij.plugin.PointToolOptions;
import ij.plugin.filter.Analyzer;
import ij.util.Java2;
import java.awt.*;
import java.util.*;

import com.vis.core.view.D2.ui.glasses.*;

import java.awt.geom.*;

/** This class represents a collection of points that can be associated with counters.
 * @see <a href="http://wsr.imagej.net/macros/js/PointProperties.js">PointProperties.js</a>
*/
@SuppressWarnings("serial")
public class PointRoi extends PolygonRoi {
    public static final String[] sizes = {"Tiny", "Small", "Medium", "Large", "Extra Large", "XXL", "XXXL"};
    public static final String[] types = {"Hybrid", "Cross", "Dot", "Circle"};
    public static final int HYBRID=0, CROSS=1, CROSSHAIR=1, DOT=2, CIRCLE=3;
    private static final String TYPE_KEY = "point.type";
    private static final String SIZE_KEY = "point.size";
//    private static final String CROSS_COLOR_KEY = "point.cross.color";
    private static final int TINY=1, SMALL=3, MEDIUM=5, LARGE=7, EXTRA_LARGE=11, XXL=17, XXXL=25;
    private static final BasicStroke twoPixelsWide = new BasicStroke(2);
    private static final BasicStroke threePixelsWide = new BasicStroke(3);
    private static final BasicStroke fivePixelsWide = new BasicStroke(5);
    private static int defaultType = HYBRID;
    private static int defaultSize = SMALL;
    private static Font font;
//    private static Color defaultCrossColor = Color.white;
    private int fontSize = 9;
    public static final int MAX_COUNTERS = 100;
    private static String[] counterChoices;
    private static Color[] colors;
    private boolean showLabels;
    private int type = HYBRID;
    private int size = SMALL;
    static int defaultCounter;
    private int counter;
    private int nCounters = 1;
    private short[] counters;   //for each point, 0-100 for counter (=category that can be defined by the user)
    private int[] positions;    //for each point, the stack slice, or 0 for 'show on all'
    private int[] counts = new int[MAX_COUNTERS];
    private long lastPointTime;
    private int[] counterInfo;
    private boolean promptBeforeDeleting;
    private boolean promptBeforeDeletingCalled;
    private int nMarkers;
    private boolean addToOverlay;
    public static PointRoi savedPoints;
     
    static {
        setDefaultType((int)Prefs.get(TYPE_KEY, HYBRID),null);
        setDefaultSize((int)Prefs.get(SIZE_KEY, 1),null);
    }
    
    public PointRoi(SlideGlass slide) {
        this(0.0, 0.0, slide);
        deletePoint(0);
    }
    
    /** Creates a new PointRoi using the specified int arrays of offscreen coordinates. */
    public PointRoi(int[] ox, int[] oy, int points,SlideGlass slide) {
        super(itof(ox), itof(oy), points, RoiType.POINT.id(), slide);
        width+=1; height+=1;
        updateCounts();
    }

    /** Creates a new PointRoi using the specified float arrays of offscreen coordinates. */
    public PointRoi(float[] ox, float[] oy, int points,SlideGlass slide) {
        super(ox, oy, points, RoiType.POINT.id(), slide);
        width+=1; height+=1;
        updateCounts();
    }

    /** Creates a new PointRoi using the specified float arrays of offscreen coordinates. */
    public PointRoi(float[] ox, float[] oy, SlideGlass slide) {
        this(ox, oy, ox.length, slide);
    }

    /** Creates a new PointRoi using the specified coordinate arrays and options. */
    public PointRoi(float[] ox, float[] oy, String options, SlideGlass slide) {
        this(ox, oy, ox.length, slide);
        setOptions(options);
    }

    /** Creates a new PointRoi from a FloatPolygon. */
    public PointRoi(FloatPolygon poly, SlideGlass slide) {
        this(poly.xpoints, poly.ypoints, poly.npoints, slide);
    }

    /** Creates a new PointRoi from a Polygon. */
    public PointRoi(Polygon poly, SlideGlass slide) {
        this(itof(poly.xpoints), itof(poly.ypoints), poly.npoints,slide);
    }

    /** Creates a new PointRoi using the specified coordinates and options. */
    public PointRoi(double ox, double oy, String options, SlideGlass slide) {
        super(makeXorYArray(ox, null, false), makeXorYArray(oy, null, true), 1, RoiType.POINT.id(),slide);
        width=1; height=1;
        incrementCounter();
        setOptions(options);
    }

    /** Creates a new PointRoi using the specified offscreen double coordinates. */
    public PointRoi(double ox, double oy, SlideGlass slide) {
        super(makeXorYArray(ox, null, false), makeXorYArray(oy, null, true), 1, RoiType.POINT.id(),slide);
        width=1; height=1;
        incrementCounter();
    }
    
    public PointRoi(int onImageX, int onImageY, int type, SlideGlass sg) {
    	super(onImageX, onImageY, type, sg);
    	if(type == RoiType.POINT.id()) {
    		finishPolygon();
    	}
    }
    
    public void setOptions(String options) {
        if (options==null)
            return;
        if (options.contains("tiny")) size=TINY;
        else if (options.contains("medium")) size=MEDIUM;
        else if (options.contains("extra")) size=EXTRA_LARGE;
        else if (options.contains("large")) size=LARGE;
        else if (options.contains("xxxl")) size=XXXL;
        else if (options.contains("xxl")) size=XXL;
        if (options.contains("cross")) type=CROSS;
        else if (options.contains("dot")) type=DOT;
        else if (options.contains("circle")) type=CIRCLE;
        if (options.contains("nolabel")) setShowLabels(false);
        else if (options.contains("label")) setShowLabels(true);
        setStrokeColor(Colors.getColor(options,RoiObj.getColor()));
        addToOverlay =  options.contains("add");
    }
    
    static float[] itof(int[] arr) {
        if (arr==null)
            return null;
        int n = arr.length;
        float[] temp = new float[n];
        for (int i=0; i<n; i++)
            temp[i] = arr[i];
        return temp;
    }

    /** Creates a one-element array with a coordinate; if 'imp' is non-null
     *  converts from a screen coordinate to an image (offscreen) coordinate.
     *  The array can be used for adding to an existing point selection */
	static float[] makeXorYArray(double value, SlideGlass sg, boolean isY) {
		if (sg != null) {// offset 0.5 converts from area to pixel center coordinates
			value = isY ? (int) value : (int) (value - 0.5);
		}
		return new float[] { (float) value };
	}
    
    void handleMouseMove(int ox, int oy) {
    }
    
    public void handleMouseUp(int sx, int sy) {
        super.handleMouseUp(sx, sy);
        modifyRoi(); //adds this point to previous points if shift key down
    }
    
    /** Draws the points on the image. */
	public void draw(Graphics g) {
		updatePolygon();
		if (showLabels && nPoints > 1) {
			fontSize = 8;
			double scale = size >= XXL ? 2 : 1.5;
			fontSize += scale * convertSizeToIndex(size);
			fontSize = (int) Math.round(fontSize);
			// IJ.log("fontSize: "+fontSize+" "+scale);
			font = new Font("SansSerif", Font.PLAIN, fontSize);
			g.setFont(font);
			if (fontSize > 9)
				Java2.setAntialiasedText(g, true);
		}
		for (int i = 0; i < nPoints; i++) {
			drawPoint(g, xp2[i], yp2[i], i + 1);
		}
		PointToolOptions.update();
		flattenScale = 1.0;
	}

	void drawPoint(Graphics g, int x, int y, int n) {
		int size2 = size / 2;
		boolean colorSet = false;

		AffineTransform aTx = (((Graphics2D) g).getDeviceConfiguration()).getDefaultTransform();
		Graphics2D g2d = (Graphics2D) g;
		double mag = getMagnification();
		double scaleXY[] = getComponentScaleFactor();
		if (slide != null) {
			Point offset = slide.getDisplayImageOriginXY();
			// First, translate image origin without mag and component scale.
			aTx.translate(offset.x, offset.y);
			// Second, scale Roi graphics
			aTx.scale(mag * scaleXY[0], mag * scaleXY[1]);
			g2d.setTransform(aTx);
		}

//		AffineTransform saveXform = null;
//		if (flattenScale > 1.0) {
//			saveXform = g2d.getTransform();
//			g2d.translate(x, y);
//			g2d.scale(flattenScale, flattenScale);
//			x = y = 0;
//		}
		Color color = strokeColor != null ? strokeColor : ROIColor;
		if (!overlay && isActiveOverlayRoi()) {
			color = Color.cyan;
		}
		if (nCounters > 1 && counters != null && n <= counters.length)
			color = getColor(counters[n - 1]);
		if (type == HYBRID || type == CROSS) {
			if (type == HYBRID)
				g.setColor(Color.white);
			else {
				g.setColor(color);
				colorSet = true;
			}
			if (size > XXL)
				g2d.setStroke(fivePixelsWide);
			else if (size > LARGE)
				g2d.setStroke(threePixelsWide);
			g.drawLine(x - (size + 2), y, x + size + 2, y);
			g.drawLine(x, y - (size + 2), x, y + size + 2);
		}
		if (type != CROSS && size > SMALL)
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (type == HYBRID || type == DOT) {
			if (!colorSet) {
				g.setColor(color);
				colorSet = true;
			}
			if (size > LARGE)
				g2d.setStroke(onePixelWide);
			if (size > LARGE && type == DOT)
				g.fillOval(x - size2, y - size2, size, size);
			else if (size > LARGE && type == HYBRID)
				g.fillRect(x - (size2 - 2), y - (size2 - 2), size - 4, size - 4);
			else if (size > SMALL && type == HYBRID)
				g.fillRect(x - (size2 - 1), y - (size2 - 1), size - 2, size - 2);
			else
				g.fillRect(x - size2, y - size2, size, size);
		}
		if (showLabels && nPoints > 1) {
			int xoffset = 2;
			if (size == LARGE)
				xoffset = 3;
			if (size == EXTRA_LARGE)
				xoffset = 4;
			if (size == XXL)
				xoffset = 5;
			if (size == XXXL)
				xoffset = 7;
			int yoffset = xoffset;
			if (size >= LARGE)
				yoffset = yoffset - 1;
			if (nCounters == 1) {
				if (!colorSet)
					g.setColor(color);
				g.drawString("" + n, x + xoffset, y + yoffset + fontSize);
			} else if (counters != null) {
				g.setColor(getColor(counters[n - 1]));
				g.drawString("" + counters[n - 1], x + xoffset, y + yoffset + fontSize);
			}
		}
		if ((size > TINY || type == DOT) && (type == HYBRID || type == DOT)) {
			g.setColor(Color.black);
			if (size > LARGE && type == HYBRID)
				g.drawOval(x - (size2 - 1), y - (size2 - 1), size - 3, size - 3);
			else if (size > SMALL && type == HYBRID)
				g.drawOval(x - size2, y - size2, size - 1, size - 1);
			else
				g.drawOval(x - (size2 + 1), y - (size2 + 1), size + 1, size + 1);
		}
		if (type == CIRCLE) {
			int scaledSize = (int) Math.round(size + 1);
			g.setColor(color);
			if (size > LARGE)
				g2d.setStroke(twoPixelsWide);
			g.drawOval(x - scaledSize / 2, y - scaledSize / 2, scaledSize, scaledSize);
		}
//		if (saveXform != null)
//			g2d.setTransform(saveXform);
	}
    
    public void drawPixels(ImageProcessor ip) {
        ip.setLineWidth(Analyzer.markWidth);
        double x0 = bounds == null ? x : bounds.x;
        double y0 = bounds == null ? y : bounds.y;
        for (int i=0; i<nPoints; i++) {
            ip.moveTo((int)Math.round(x0+xpf[i]), (int)Math.round(y0+ypf[i]));
            ip.lineTo((int)Math.round(x0+xpf[i]), (int)Math.round(y0+ypf[i]));
        }
    }
    
    /** Adds a point to this PointRoi. */
    public void addPoint(double ox, double oy) {
        if (nPoints==xpf.length)
            enlargeArrays();
        addPoint2(ox, oy);
        resetBoundingRect();
        width+=1; height+=1;
    }
    

    public void addUserPoint(double ox, double oy) {
        addPoint(ox, oy);
        nMarkers++;
    }

    private void addPoint2( double ox, double oy) {
        double xbase = getXBase();
        double ybase = getYBase();
        xpf[nPoints] = (float)(ox-xbase);
        ypf[nPoints] = (float)(oy-ybase);
        xp2[nPoints] = (int)ox;
        yp2[nPoints] = (int)oy;
        nPoints++;
        incrementCounter();
        lastPointTime = System.currentTimeMillis();
    }

    protected void deletePoint(int index) {
        super.deletePoint(index);
        if (index>=0 && index<=nPoints && counters!=null) {
            counts[counters[index]]--;
            for (int i=index; i<nPoints; i++) {
                counters[i] = counters[i+1];
                positions[i] = positions[i+1];
            }
        }
    }

    private synchronized void incrementCounter() {
        counts[counter]++;
        if (counter!=0 || counters!=null) {
            if (counters==null) {
                counters = new short[nPoints*2];
                positions = new int[nPoints*2];
            }
            counters[nPoints-1] = (short)counter;
            if (nPoints+1==counters.length) {
                short[] temp = new short[counters.length*2];
                System.arraycopy(counters, 0, temp, 0, counters.length);
                counters = temp;
            }
        }
    }
    
    /** Returns the index of the current counter. */
    public int getCounter() {
        return counter;
    }

    /** Returns the count associated with the specified counter index.
     * @see #getLastCounter
     * @see <a href="http://wsr.imagej.net/macros/js/PointProperties.js">PointProperties.js</a>
     */
    public int getCount(int counter) {
        if (counter==0 && counters==null)
            return nPoints;
        else
            return counts[counter];
    }
    
    /** Returns the index of the last counter. */
    public int getLastCounter() {
        return nCounters - 1;
    }

    /** Returns the number of counters. */
    public int getNCounters() {
        int n = 0;
        for (int counter=0; counter<nCounters; counter++) {
            if (getCount(counter)>0) n++;
        }
        return n;
    }
    
    /** Returns the counter assocated with the specified point. */
    public int getCounter(int index) {
        if (counters==null || index>=counters.length)
            return 0;
        else
            return counters[index];
    }

    public void resetCounters() {
        for (int i=0; i<counts.length; i++)
            counts[i] = 0;
        counters = null;
        positions = null;
        PointToolOptions.update();
    }
    
    /** Returns the points of this Roi that are not contained in the specified area ROI.
     *  Returns null if there are no resulting points or Roi is not an area roi. */
    public PointRoi subtractPoints(RoiObj roi) {
        return checkContained(roi, false);
    }

    /** Returns the points of this Roi that are contained in the specified area ROI.
     *  Returns null if there are no resulting points or Roi is not an area roi. */
    public PointRoi containedPoints(RoiObj roi) {
        return checkContained(roi, true);
    }

    /** Returns the points contained (not contained) in <code>roi</code> if <code>keepContained</code> is true (false). */
    PointRoi checkContained(RoiObj roi, boolean keepContained) {
        if (!roi.isArea()) return null;
        FloatPolygon points = getFloatPolygon();
        FloatPolygon points2 = new FloatPolygon();
        for (int i=0; i<points.npoints; i++) {
            if (keepContained == roi.containsPoint(points.xpoints[i], points.ypoints[i]))
                points2.addPoint(points.xpoints[i], points.ypoints[i]);
        }
        if (points2.npoints==0)
            return null;
        else {
            PointRoi roi2 = new PointRoi(points2.xpoints, points2.ypoints, points2.npoints, getSlideGlass());
            roi2.copyAttributes(this);
            return roi2;
        }
    }

    public ImageProcessor getMask() {
        ImageProcessor mask = cachedMask;
        if (mask!=null && mask.getPixels()!=null)
            return mask;
        mask = new ByteProcessor(width, height);
        double deltaX = getXBase() - x;
        double deltaY = getYBase() - y;
        for (int i=0; i<nPoints; i++)
            mask.putPixel((int)(xpf[i] + deltaX), (int)(ypf[i] + deltaY), 255);
        cachedMask = mask;
        return mask;
    }

    /** Returns true if (x,y) is one of the points in this collection. */
    public boolean contains(int x, int y) {
        for (int i=0; i<nPoints; i++) {
            if (x==this.x+(int)xpf[i] && y==this.y+(int)ypf[i]) return true;
        }
        return false;
    }
    
    public void setShowLabels(boolean showLabels) {
        this.showLabels = showLabels;
    }

    public boolean getShowLabels() {
        return showLabels;
    }

    public static void setDefaultType(int type,SlideGlass sg) {
        if (type>=0 && type<types.length) {
            defaultType = type;
            PointRoi instance = getPointRoiInstance(sg);
            if (instance!=null)
                instance.setPointType(defaultType);
            Prefs.set(TYPE_KEY, type);
        }
    }
    
    public static int getDefaultType() {
        return defaultType;
    }
    
    /** Sets the point type (0=hybrid, 1=cross, 2=dot, 3=circle). */
    public void setPointType(int type) {
        if (type>=0 && type<types.length)
            this.type = type;
    }

    /** Returns the point type (0=hybrid, 1=cross, 2=dot, 3=circle). */
    public int getPointType() {
        return type;
    }


    /** Sets the default point size, where 'size' is 0-6 (Tiny-XXXL). */
    public static void setDefaultSize(int size, SlideGlass sg) {
        int index = size;
        if (index>=0 && index<sizes.length) {
            defaultSize = convertIndexToSize(index);
            PointRoi instance = getPointRoiInstance(sg);
            if (instance!=null)
                instance.setSize(index);
            Prefs.set(SIZE_KEY, index);
        }
    }
    
    /** Returns the default point size 0-6 (Tiny-XXXL). */
    public static int getDefaultSize() {
        return convertSizeToIndex(defaultSize);
    }

    /** Sets the point size, where 'size' is 0-6 (Tiny-XXXL). */
    public void setSize(int size) {
        if (size>=0 && size<sizes.length)
            this.size = convertIndexToSize(size);
    }

    /** Returns the current point size 0-6 (Tiny-XXXL). */
    public int getSize() {
        return convertSizeToIndex(size);
    }
    
    private static int convertSizeToIndex(int size) {
        switch (size) {
            case TINY: return 0;
            case SMALL: return 1;
            case MEDIUM: return 2;
            case LARGE: return 3;
            case EXTRA_LARGE: return 4;
            case XXL: return 5;
            case XXXL: return 6;
        }
        return 1;
    }

    private static int convertIndexToSize(int index) {
        switch (index) {
            case 0: return TINY;
            case 1: return SMALL;
            case 2: return MEDIUM;
            case 3: return LARGE;
            case 4: return EXTRA_LARGE;
            case 5: return XXL;
            case 6: return XXXL;
        }
        return SMALL;
    }

    /** Always returns true. */
    public boolean subPixelResolution() {
        return true;
    }
    
	private static PointRoi getPointRoiInstance(SlideGlass sg) {
		if (sg != null) {
			RoiObj roi = sg.getActiveRoi();
			if (roi != null) {
				if (roi instanceof PointRoi)
					return (PointRoi) roi;
			}
		}
		return null;
	}

    public void setCounter(int counter) {
        this.counter = counter;
        if (counter>nCounters-1 && nCounters<MAX_COUNTERS)
            nCounters = counter + 1;
    }

    public boolean promptBeforeDeleting() {
        if (promptBeforeDeletingCalled && getNCounters()==1)
            return promptBeforeDeleting;
        else
            return (nMarkers>8||getNCounters()>1) && imp!=null && imp.getWindow()!=null;
    } 

    public void promptBeforeDeleting(Boolean prompt) {
        promptBeforeDeleting = prompt;
        promptBeforeDeletingCalled = true;
    }

    public static void setDefaultCounter(int counter) {
        defaultCounter = counter;
    }

    /** Returns an array containing for each point:
     *  The counter number (0-100) in the lower 8 bits, and the slice number
     *  (or 0, if the point appears on all slices) in the higher 24 bits.
     *  Used when writing a Roi to file (RoiEncoder) */
    public int[] getCounters() {
        if (nPoints>65535)
            return null;
        int[] temp = new int[nPoints];
        if (counters!=null) {
            for (int i=0; i<nPoints; i++)
                temp[i] = (counters[i]&0xff) + (positions[i]<<8);
        }
        return temp;
    }

    /** Sets the counter number and slice number for each point from an
     *  array, where the lower 8 bits are the counter number and the
     *  higher 24 bits contain the slice position of each point.
     *  Used when reading a roi fromfile (RoiDecoder).  */
    public void setCounters(int[] counters) {
        if (counters!=null) {
            int n = counters.length;
            this.counters = new short[n*2];
            this.positions = new int[n*2];
            for (int i=0; i<n; i++) {
                int counter = counters[i]&0xff;
                int position = counters[i]>>8;
                //IJ.log(i+" cnt="+counter+" slice="+position);
                this.counters[i] = (short)counter;
                this.positions[i] = position;
                if (counter<counts.length && counter>nCounters-1)
                    nCounters = counter + 1;
            }
            updateCounts();
        }
    }

    /** Updates the counts for each category in 'counters' */
    public void updateCounts() {
        Arrays.fill(counts, 0);
        for (int i=0; i<nPoints; i++)
            counts[(counters==null || counters[i]>=counts.length) ? 0 : counters[i]] ++;
    }

    /** Returns the stack slice of the point with the given index, or 0 if no slice defined for this point */
    public int getPointPosition(int index) {
        if (positions!=null && index<nPoints)
            return positions[index];
        else
            return 0;
    }
    
    public synchronized static String[] getCounterChoices() {
        if (counterChoices==null) {
            counterChoices = new String[MAX_COUNTERS];
            for (int i=0; i<MAX_COUNTERS; i++)
                counterChoices[i] = ""+i;
        }
        return counterChoices;
    }
    
    private static Color getColor(int index) {
        if (colors==null) {
            colors = new Color[MAX_COUNTERS];
            colors[0]=Color.yellow; colors[1]=Color.magenta; colors[2]=Color.cyan;
            colors[3]=Color.orange; colors[4]=Color.green; colors[5]=Color.blue;
            colors[6]=Color.white; colors[7]=Color.darkGray; colors[8]=Color.pink;
            colors[9]=Color.lightGray;
        }
        if (colors[index]!=null)
            return colors[index];
        else {
            Random ran = new Random();
            float r = (float)ran.nextDouble();
            float g = (float)ran.nextDouble();
            float b = (float)ran.nextDouble();
            Color c = new Color(r, g, b);
            colors[index] = c;
            return c;
        }
    }

    /** Returns a point index if it has been at least one second since
        the last point was added and the specified screen coordinates are    
        inside or near a point, otherwise returns -1. */
    public int isHandle(int sx, int sy) {
        if ((System.currentTimeMillis()-lastPointTime)<1000L)
            return -1;
        int ox = offScreenX(sx);
        int oy = offScreenY(sy);
        int size = HANDLE_SIZE+this.size;
        int halfSize = size/2;
        int handle = -1;
        int x2, y2;
        for (int i=0; i<nPoints; i++) {
            x2 = xp2[i]-halfSize; y2=yp2[i]-halfSize;
            if (ox>=x2 && ox<=x2+size && oy>=y2 && oy<=y2+size) {
                handle = i;
                break;
            }
        }
        return handle;
    }
    
    /** Returns the points as an array of Points. 
     * Wilhelm Burger: modified to use FloatPolygon for correct point positions.
    */
    public Point[] getContainedPoints() {
        FloatPolygon p = getFloatPolygon();
        Point[] points = new Point[p.npoints];
        for (int i=0; i<p.npoints; i++)
            points[i] = new Point((int) Math.round(p.xpoints[i] - 0.5f), (int) Math.round(p.ypoints[i] - 0.5f));
        return points;
    }

    /** Returns the points as a FloatPolygon. */
    public FloatPolygon getContainedFloatPoints() {
        return getFloatPolygon();
    }

    /**
     * Custom iterator for points contained in a {@link PointRoi}.
     * Author: W. Burger
    */
    public Iterator<Point> iterator() { 
        return new Iterator<Point>() {
            final Point[] pnts = getContainedPoints();
            final int n = pnts.length;
            int next = (n == 0) ? 1 : 0;
            @Override
            public boolean hasNext() {
                return next < n;
            }
            @Override
            public Point next() {
                if (next >= n) {
                    throw new NoSuchElementException();
                }
                Point pnt = pnts[next];
                next = next + 1;
                return pnt;
            }
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
    
    @Override
    protected int getClosestPoint(double x, double y, FloatPolygon points) {
        int index = -1;
        double distance = Double.MAX_VALUE;
        int slice = imp!=null&&positions!=null&&imp.getStackSize()>1?imp.getCurrentSlice():0;
        if (Prefs.showAllPoints)
            slice = 0;
        for (int i=0; i<points.npoints; i++) {
            double dx = points.xpoints[i] - x;
            double dy = points.ypoints[i] - y;
            double distance2 = dx*dx+dy*dy;
            if (distance2<distance && (slice==0||slice==positions[i])) {
                distance = distance2;
                index = i;
            }
        }
        return index;
    }

    /** Returns a copy of this PointRoi. */
    public synchronized Object clone() {
        PointRoi r = (PointRoi)super.clone();
        if (counters!=null) {
            r.counters = new short[counters.length];
            for (int i=0; i<counters.length; i++)
                r.counters[i] = counters[i];
        }
        if (positions!=null) {
            r.positions = new int[positions.length];
            for (int i=0; i<positions.length; i++)
                r.positions[i] = positions[i];
        }
        if (counts!=null) {
            r.counts = new int[counts.length];
            for (int i=0; i<counts.length; i++)
                r.counts[i] = counts[i];
        }
        return r;
    }
    
    @Override
    public void copyAttributes(RoiObj roi2) {
        super.copyAttributes(roi2);
        if (roi2 instanceof PointRoi) {
            PointRoi p2 = (PointRoi)roi2;
            this.type = p2.type;
            this.size = p2.size;
            this.showLabels = p2.showLabels;
            this.fontSize = p2.fontSize;
        }
    }
    
    public void setCounterInfo(int[] info) {
        counterInfo = info;
    }

    public int[] getCounterInfo() {
        return counterInfo;
    }
    
    public boolean addToOverlay() {
        return addToOverlay;
    }

    public String toString() {
        if (nPoints>1)
            return ("Roi[Points, count="+nPoints+"]");
        else
            return ("Roi[Point, x="+x+", y="+y+"]");
    }
    
    /** @deprecated */
    public void setHideLabels(boolean hideLabels) {
        this.showLabels = !hideLabels;
    }
    
    /** @deprecated */
    public static void setDefaultMarkerSize(String size) {
    }
    
    /** @deprecated */
    public static String getDefaultMarkerSize() {
        return sizes[defaultSize];
    }
    
    /** Deprecated */
    public static void setDefaultCrossColor(Color color) {
    }
    
    /** Deprecated */
    public static Color getDefaultCrossColor() {
        return null;
    }
}