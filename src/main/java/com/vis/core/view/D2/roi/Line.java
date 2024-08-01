package com.vis.core.view.D2.roi;

import ij.*;
import ij.gui.ProfilePlot;
import ij.process.*;
import ij.measure.*;
import ij.plugin.RoiRotator;
import ij.plugin.Straightener;
import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.vis.core.view.D2.ui.glasses.*;

import java.awt.event.*;
import java.awt.geom.Rectangle2D;

/** This class represents a straight line selection. 
 * https://github.com/imagej/imagej1/blob/ac470b687acd12ed7bdf5979677940c67a672b47/ij/gui/Line.java
 */
@SuppressWarnings("serial")
public class Line extends RoiObj {

	public int x1, y1, x2, y2; // the line
	public double x1d, y1d, x2d, y2d; // the line using sub-pixel coordinates
	protected double x1R, y1R, x2R, y2R; // the line, relative to base of bounding rect
	static boolean widthChanged;
	private boolean dragged;
	private int mouseUpCount;
	
	/** Used for angle searches in line ROI creation */
	private static final double[] PI_SEARCH = { Math.tan(Math.PI / 8), Math.tan((3 * Math.PI) / 8) };
	private static final double[] PI_MULT = { 0, Math.tan((2 * Math.PI) / 8) };

	/*
	 * ox : x on original image coordinate
	 * oy : y on original image coordinate
	 */
	public Line(int ox1, int oy1, int ox2, int oy2, SlideGlass slide) {
		this((double) ox1, (double) oy1, (double) ox2, (double) oy2, slide);
	}

	public Line(double ox1, double oy1, double ox2, double oy2, SlideGlass slide) {
		super((int)(ox1+0.5), (int)(oy1+0.5), 0, 0, 0, slide);
//		super((int)(ox1), (int)(oy1), 0, 0, 0, slide);
		type = LINE;
		updateCoordinates(ox1, oy1, ox2, oy2);
		updateWideLine(lineWidth);
		if (!(this instanceof Arrow) && lineWidth>1) {
			updateWideLine(lineWidth);
		}
		/*
		 * TODO?
		 */
        updateClipRect(slide);
//		oldX = x;
//		oldY = y;
//		oldWidth = width;
//		oldHeight = height;
		state = NORMAL;
	}
	
	
	@Override
	public HashMap<String, Object> readContext(){
    	HashMap<String,Object> con = new HashMap<>();
    	//to string
    	for(RoiContextKeySet k:RoiContextKeySet.values()) {
    		con.put(k.name(), getPropertyAt(k.name()));
    	}
    	
    	con.put("OriginX", (int)getXBase());
    	con.put("OriginY", (int)getYBase());
    	con.put("Width", getBounds().width);
    	con.put("Height", getBounds().height);
    	
    	con.put("PointX", fArray2dArray(getFloatPoints().xpoints));
		con.put("PointY", fArray2dArray(getFloatPoints().ypoints));
		
    	return con;
    }

	protected void grow(int sx, int sy) { // mouseDragged
		drawLine(sx, sy);
		dragged = true;
	}

	public void mouseMoved(MouseEvent e) {
		drawLine(e.getX(), e.getY());
	}
	
	public void handleMouseDrag(int sx, int sy, int flags) {
		System.out.println("dragging line roi");
		switch (state) {
		case CONSTRUCTING:
			System.out.println("LINE ROI GROW");
			grow(sx, sy);
			break;
		case MOVING:
			System.out.println("LINE ROI MOVING");
			move(sx, sy);
			break;
		case MOVING_HANDLE:
			System.out.println("LINE ROI MOVING_HANDLE");
			moveHandle(sx, sy);
			break;
		default:
			break;
		}
	}

	public void handleMouseUp(int screenX, int screenY, SlideGlass sg) {
		mouseUpCount++;
		if (Prefs.enhancedLineTool && mouseUpCount == 1 && !dragged)
			return;
		state = NORMAL;
		//reset cursor
		if(dragged) {
			sg.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		}
		dragged = false;
		if (getLength() == 0.0) {
			sg.deleteRoi(this);
		}
	}

	protected void drawLine(int sx, int sy) {
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
		double xend = slide.onImageXD(sx);
		double yend = slide.onImageYD(sy);
		if (xend < 0.0)
			xend = 0.0;
		if (yend < 0.0)
			yend = 0.0;
		if (xend > xMax)
			xend = xMax;
		if (yend > yMax)
			yend = yMax;
		double xstart = getXBase() + x1R;
		double ystart = getYBase() + y1R;
		if (constrain) {
			int i = 0;
			double dy = Math.abs(yend - ystart);
			double dx = Math.abs(xend - xstart);
			double comp = dy / dx;

			for (; i < PI_SEARCH.length; i++) {
				if (comp < PI_SEARCH[i]) {
					break;
				}
			}

			if (i < PI_SEARCH.length) {
				if (yend > ystart) {
					yend = ystart + dx * PI_MULT[i];
				} else {
					yend = ystart - dx * PI_MULT[i];
				}
			} else {
				xend = xstart;
			}
		}
		if (!magnificationForSubPixel(slide)) {
			xend = Math.round(xend);
			yend = Math.round(yend);
		}
		updateCoordinates(xstart, ystart, xend, yend);
		updateClipRect(slide);
		slide.getObservables().repaint();
	}

	void move(int sx, int sy, SlideGlass sg) {
		oldX = x;
		oldY = y;
		/*
		 * mouse moved 
		 */
		int refX = sg.onImageX(sg.lastX);
		int refY = sg.onImageX(sg.lastY);
		int xNew = sg.onImageX(sx);
		int yNew = sg.onImageY(sy);
		/*
		 * moved distance subtract from roi origin
		 */
		x = startX - (xNew-refX);
		y = startY - (yNew-refY);
		bounds = new Rectangle2D.Double(x, y, width, height);
		clipboard = null;
		updateClipRect(sg);
		slide.getObservables().repaint();
	}

	protected void moveHandle(int sx, int sy) {
		
		System.out.println("move roi handle, active handle is "+activeHandle);
		
//		if (constrain && activeHandle == 2) { // constrain translation in 90deg steps
//			int dx = beforeDraggingOriginX - slide.onImageX(sx);
//			int dy = beforeDraggingOriginY - slide.onImageX(sy);
//			if (Math.abs(dx) > Math.abs(dy))
//				dy = 0;
//			else
//				dx = 0;
//			sx = sg.lastDraggedX + dx;//??
//			sy = sg.lastDraggedY + dy;
//		}
		
		//original image scale(=offScreen) variables
		double x1d=getXBase()+x1R, y1d=getYBase()+y1R;
		double x2d=getXBase()+x2R, y2d=getYBase()+y2R;
		double length = Math.sqrt(sqr(x2d-x1d) + sqr(y2d-y1d));
		
		double dragIX = slide.onImageXD(sx);
		double dragIY = slide.onImageYD(sy);
		
		/*
		 * activeHandle: 2 is middle handle
		 */
		switch (activeHandle) {
		case 0://initial point
			//diffs used this in switch-case 0-2 statement
			double dx = dragIX - x1d;
			double dy = dragIY - y1d;
			x1d = dragIX;
			y1d = dragIY;
			if (center) {
				x2d -= dx;
				y2d -= dy;
			}
			if (aspect) {
				double ratio = length / (Math.sqrt(sqr(x2d - x1d) + sqr(y2d - y1d)));
				double xcd = x1d + (x2d - x1d) / 2;
				double ycd = y1d + (y2d - y1d) / 2;

				if (center) {
					x1d = xcd - ratio * (xcd - x1d);
					x2d = xcd + ratio * (x2d - xcd);
					y1d = ycd - ratio * (ycd - y1d);
					y2d = ycd + ratio * (y2d - ycd);
				} else {
					x1d = x2d - ratio * (x2d - x1d);
					y1d = y2d - ratio * (y2d - y1d);
				}

			}
			break;
		case 1://end point
			dx = dragIX - x2d;
			dy = dragIY - y2d;
			x2d = dragIX;
			y2d = dragIY;
			if (center) {
				x1d -= dx;
				y1d -= dy;
			}
			if (aspect) {
				double ratio = length / (Math.sqrt((x2d - x1d) * (x2d - x1d) + (y2d - y1d) * (y2d - y1d)));
				double xcd = x1d + (x2d - x1d) / 2;
				double ycd = y1d + (y2d - y1d) / 2;

				if (center) {
					x1d = xcd - ratio * (xcd - x1d);
					x2d = xcd + ratio * (x2d - xcd);
					y1d = ycd - ratio * (ycd - y1d);
					y2d = ycd + ratio * (y2d - ycd);
				} else {
					x2d = x1d + ratio * (x2d - x1d);
					y2d = y1d + ratio * (y2d - y1d);
				}

			}
			break;
		case 2://middle point
			dx = dragIX - (x1d + (x2d - x1d) / 2);
			dy = dragIY - (y1d + (y2d - y1d) / 2);
			x1d += dx;
			y1d += dy;
			x2d += dx;
			y2d += dy;
			break;
		case 3://right rotate point
			if(slide.lastDraggedY < sy) {
				rotateLine(1);
			}else if(slide.lastDraggedY >= sy){
				rotateLine(-1);
			}
			x1d = this.x1d;
			y1d = this.y1d;
			x2d = this.x2d;
			y2d = this.y2d;
			//DO NOT DO THIS, p1,p2 were confused...
//			Point[] p1p2 = rotatePoints(this, 1);
//			x1d = p1p2[0].x;
//			y1d = p1p2[0].y;
//			x2d = p1p2[1].x;
//			y2d = p1p2[1].y;
			break;
		case 4://left rotate point
			if(slide.lastDraggedY < sy) {
				rotateLine(-1);
			}else if(slide.lastDraggedY >= sy){
				rotateLine(1);
			}
			x1d = this.x1d;
			y1d = this.y1d;
			x2d = this.x2d;
			y2d = this.y2d;
			break;
		}
		if (constrain) {
			double dx = Math.abs(x1d - x2d);
			double dy = Math.abs(y1d - y2d);
			double xcd = Math.min(x1d, x2d) + dx / 2;
			double ycd = Math.min(y1d, y2d) + dy / 2;

			// double ratio = length/(Math.sqrt((x2d-x1d)*(x2d-x1d) + (y2d-y1d)*(y2d-y1d)));
			if (activeHandle == 0) {
				if (dx >= dy) {
					if (aspect) {
						if (x2d > x1d)
							x1d = x2d - length;
						else
							x1d = x2d + length;
					}
					y1d = y2d;
					if (center) {
						y1d = y2d = ycd;
						if (aspect) {
							if (xcd > x1d) {
								x1d = xcd - length / 2;
								x2d = xcd + length / 2;
							} else {
								x1d = xcd + length / 2;
								x2d = xcd - length / 2;
							}
						}
					}
				} else {
					if (aspect) {
						if (y2d > y1d)
							y1d = y2d - length;
						else
							y1d = y2d + length;
					}
					x1d = x2d;
					if (center) {
						x1d = x2d = xcd;
						if (aspect) {
							if (ycd > y1d) {
								y1d = ycd - length / 2;
								y2d = ycd + length / 2;
							} else {
								y1d = ycd + length / 2;
								y2d = ycd - length / 2;
							}
						}
					}
				}
			} else if (activeHandle == 1) {
				if (dx >= dy) {
					if (aspect) {
						if (x1d > x2d)
							x2d = x1d - length;
						else
							x2d = x1d + length;
					}
					y2d = y1d;
					if (center) {
						y1d = y2d = ycd;
						if (aspect) {
							if (xcd > x1d) {
								x1d = xcd - length / 2;
								x2d = xcd + length / 2;
							} else {
								x1d = xcd + length / 2;
								x2d = xcd - length / 2;
							}
						}
					}
				} else {
					if (aspect) {
						if (y1d > y2d)
							y2d = y1d - length;
						else
							y2d = y1d + length;
					}
					x2d = x1d;
					if (center) {
						x1d = x2d = xcd;
						if (aspect) {
							if (ycd > y1d) {
								y1d = ycd - length / 2;
								y2d = ycd + length / 2;
							} else {
								y1d = ycd + length / 2;
								y2d = ycd - length / 2;
							}
						}
					}
				}
			}
		}
		updateCoordinates(x1d, y1d, x2d, y2d);
		updateClipRect(slide);
		//see, ij Line.java
		oldX = x;
		oldY = y;
		oldWidth = width;
		oldHeight = height;
		slide.getObservables().repaint();
	}

	public void mouseDownInHandle(int handle, int sx, int sy, SlideGlass sg) {
		super.mouseDownInHandle(handle, sx, sy, sg); // sets state, activeHandle, previousSX&Y
	}
	
	/** Sets the x1d, y1d, x2d, y2d line end points,
	 *  the (legacy) integer coordinates of the end points x1, y1, x2, y2
	 *  the 'bounds' subpixel rectangle of the Roi superclass (spanned by the end points),
	 *  the int x, y, width, height integer bounds of the superclass (these enclose
	 *  the 'draw' area for 1 pixel width), and
	 *  the coordinates x1R, y1R, x2R, y2R relative to the base x, y of the 'bounds' */
	protected void updateCoordinates(double x1d, double y1d, double x2d, double y2d) {
		this.x1d = x1d; this.y1d = y1d;
		this.x2d = x2d; this.y2d = y2d;
		java.awt.geom.Rectangle2D.Double bounds = this.bounds;  //local variable (this.bounds may become null asynchronously upon nudge)
		if (bounds == null) {
			bounds = new Rectangle2D.Double();
		}
		bounds.x = Math.min(x1d, x2d);
		bounds.y = Math.min(y1d, y2d);
		bounds.width  = Math.abs(x2d - x1d);
		bounds.height = Math.abs(y2d - y1d);
		setBounds(bounds); //sets x, y, width, height
		x1R = x1d - bounds.x; y1R = y1d - bounds.y;
		x2R = x2d - bounds.x; y2R = y2d - bounds.y;
		x1=(int)x1d; y1=(int)y1d; x2=(int)x2d; y2=(int)y2d;
		this.bounds = bounds;
	}

	/** Draws this line on the image. */
	public void draw(Graphics g, SlideGlass sg) {
		Color color = strokeColor != null ? strokeColor : ROIColor;
		boolean isActiveOverlayRoi = isActiveOverlayRoi();
		if (isActiveOverlayRoi) {
			if (color == Color.cyan)
				color = ROIColor;
			else
				color = Color.cyan;
		}
		g.setColor(color);
		int sx1 = (int)(slide.screenXD(x1));
		int sy1 = (int)(slide.screenYD(y1));
		int sx2 = (int)(slide.screenXD(x2d));
		int sy2 = (int)(slide.screenYD(y2d));
		int sx3 = sx1 + (int)((sx2 - sx1) / 2.);
		int sy3 = sy1 + (int)((sy2 - sy1) / 2.);
		
		//rotation handle
		int sx4 = sx1 + (int)((sx2 - sx1) / 5.);// 1/5 location
		int sy4 = sy1 + (int)((sy2 - sy1) / 5.);
		int sx5 = sx1 + (int)((sx2 - sx1) - (sx2 - sx1) / 5.);
		int sy5 = sy1 + (int)((sy2 - sy1) - (sy2 - sy1) / 5.);
		
		Graphics2D g2d = (Graphics2D) g;
		setRenderingHint(g2d);
		if (stroke != null) {
			g2d.setStroke(getScaledStroke(sg));
		}
//		if (wideLine && !isActiveOverlayRoi()) {
//			double dx = x2 - x1;
//			double dy = y2 - y1;
//			double len = length(dx, dy);
//			dx *= 0.5 * sg.getMagnification() / len; // half-pixel extension, corresponding to getFloatPolygon or
//														// convertLineToArea
//			dy *= 0.5 * sg.getMagnification() / len;
//			//convert screenXY
//			int sx1 = (int) (x1 - dx + sg.originX);
//			int sy1 = (int) (y1 - dy + sg.originY);
//			int sx2 = (int) (x2 + dx + sg.originX);
//			int sy2 = (int) (y2 + dy + sg.originY);
//			g2d.draw(new Line2D.Double(sx1, sy1, sx2, sy2));
//		} else {
//			g.drawLine(sg.screenX(x1), sg.screenY(y1), sg.screenX(x2), sg.screenY(y2));
//		}
		//default run this
//		if (wideLine && !overlay) {
//			g2d.setStroke(onePixelWide);
//			g.setColor(getColor());
//			g.drawLine(sg.screenX(x1), sg.screenY(y1), sg.screenX(x2), sg.screenY(y2));
//		}
//        if (!overlay) {
//            mag = getMagnification();
//            handleColor = strokeColor!=null?strokeColor:ROIColor;
//            drawHandle(g, sx1, sy1);
//            handleColor=Color.white;
//            drawHandle(g, sx2, sy2);
//            drawHandle(g, sx3, sy3);
//        }
		
//		g2d.setStroke(onePixelWide);
//		System.out.println("draw line at:x1 "+sg.screenX(x1)+" y1 "+sg.screenY(y1)+" x2 "+sg.screenX(x2)+" y2 "+sg.screenY(y2));
		g.drawLine(sx1, sy1, sx2, sy2);
		
		//set handles
		handleColor = Color.ORANGE;
		drawHandle(g, sx1, sy1, sg);
		handleColor = Color.ORANGE;
		drawHandle(g, sx2, sy2, sg);
		//middle handle
		handleColor = Color.WHITE;
		drawHandle(g, sx3, sy3, sg);
		//rotate handle
		handleColor = new Color(50,42,127,127);
		drawHandle(g, sx4, sy4, sg);
		drawHandle(g, sx5, sy5, sg);
	}

	public void showStatus() {
		IJ.showStatus(imp.getLocationAsString((int) Math.round(x2d), (int) Math.round(y2d)) + ", angle="
				+ IJ.d2s(getAngle()) + ", length=" + IJ.d2s(getLength()));
	}

	public double getAngle() {
		return getFloatAngle(x1d, y1d, x2d, y2d);
	}

	/** Returns the length of this line. */
	public double getLength() {
		if (imp == null || IJ.altKeyDown())
			return getRawLength();
		else {
			Calibration cal = imp.getCalibration();
			return Math.sqrt(sqr((x2d - x1d) * cal.pixelWidth) + sqr((y2d - y1d) * cal.pixelHeight));
		}
	}

	/** Returns the length of this line in pixels. */
	public double getRawLength() {
		return Math.sqrt(sqr(x2d - x1d) + sqr(y2d - y1d));
	}

	/**
	 * Returns the pixel values along this line. The line roi must have an
	 * associated ImagePlus
	 */
	public double[] getPixels() {
		double[] profile;
		if (getStrokeWidth() <= 1) {
			ImageProcessor ip = imp.getProcessor();
			profile = ip.getLine(x1d, y1d, x2d, y2d);
		} else {
			ImageProcessor ip2 = (new Straightener()).rotateLine(imp, (int) getStrokeWidth());
			if (ip2 == null)
				return new double[0];
			int width = ip2.getWidth();
			int height = ip2.getHeight();
			if (ip2 instanceof FloatProcessor)
				return ProfilePlot.getColumnAverageProfile(new Rectangle(0, 0, width, height), ip2);
			profile = new double[width];
			double[] aLine;
			ip2.setInterpolate(false);
			for (int y = 0; y < height; y++) {
				aLine = ip2.getLine(0, y, width - 1, y);
				for (int i = 0; i < width; i++)
					profile[i] += aLine[i];
			}
			for (int i = 0; i < width; i++)
				profile[i] /= height;
		}
		return profile;
	}

	/** Returns, as a Polygon, the two points that define this line. */
	public Polygon getPoints() {
		Polygon p = new Polygon();
		p.addPoint((int) Math.round(x1d), (int) Math.round(y1d));
		p.addPoint((int) Math.round(x2d), (int) Math.round(y2d));
		return p;
	}

	/** Returns, as a FloatPolygon, the two points that define this line. */
	public FloatPolygon getFloatPoints() {
		FloatPolygon p = new FloatPolygon();
		p.addPoint((float) x1d, (float) y1d);
		p.addPoint((float) x2d, (float) y2d);
		return p;
	}

	/**
	 * If the width of this line is less than or equal to one, returns the starting
	 * and ending coordinates as a 2-point Polygon, or, if the width is greater than
	 * one, returns an outline of the line as a 4-point Polygon.
	 * 
	 * @see #getFloatPolygon
	 * @see #getPoints
	 */
	public Polygon getPolygon() {
		FloatPolygon p = getFloatPolygon();
		return new Polygon(toIntR(p.xpoints), toIntR(p.ypoints), p.npoints);
	}

	/**
	 * If the width of this line is less than or equal to one, returns the starting
	 * and ending coordinates as a 2-point FloatPolygon, or, if the width is greater
	 * than one, returns an outline of the line as a 4-point FloatPolygon.
	 * 
	 * @see #getFloatPoints
	 */
	public FloatPolygon getFloatPolygon() {
		return getFloatPolygon(getStrokeWidth());
	}

	public FloatPolygon getFloatPolygon(double strokeWidth) {
		FloatPolygon p = new FloatPolygon();
		if (strokeWidth <= 1) {
			p.addPoint((float) x1d, (float) y1d);
			p.addPoint((float) x2d, (float) y2d);
		} else {
			double dx = x2d - x1d;
			double dy = y2d - y1d;
			double len = length(dx, dy);
			dx *= 0.5 / len; // half unit vector in the direction of the line
			dy *= 0.5 / len; // when rotated 90 deg cw, this yields the vector (-dx, dy)
			double p1x = x1d + 0.5 - dx + dy * strokeWidth; // 0.5 pxl shift: area vs. line coordinate convention
			double p1y = y1d + 0.5 - dy - dx * strokeWidth;
			double p2x = x1d + 0.5 - dx - dy * strokeWidth;
			double p2y = y1d + 0.5 - dy + dx * strokeWidth;
			double p3x = x2d + 0.5 + dx - dy * strokeWidth;
			double p3y = y2d + 0.5 + dy + dx * strokeWidth;
			double p4x = x2d + 0.5 + dx + dy * strokeWidth;
			double p4y = y2d + 0.5 + dy - dx * strokeWidth;
			p.addPoint((float) p1x, (float) p1y);
			p.addPoint((float) p2x, (float) p2y);
			p.addPoint((float) p3x, (float) p3y);
			p.addPoint((float) p4x, (float) p4y);
		}
		return p;
	}

	/**
	 * Returns the number of points in this selection; equivalent to
	 * getPolygon().npoints.
	 */
	public int size() {
		return getStrokeWidth() <= 1 ? 2 : 4;
	}

	/**
	 * If the width of this line is less than or equal to one, draws the line.
	 * Otherwise draws the outline of the area of this line
	 */
	public void drawPixels(ImageProcessor ip) {
		ip.setLineWidth(1);
		double x = getXBase();
		double y = getYBase();
		x1d = x + x1R;
		y1d = y + y1R;
		x2d = x + x2R;
		y2d = y + y2R;
		if (getStrokeWidth() <= 1) {
			ip.moveTo((int) Math.round(x1d), (int) Math.round(y1d));
			ip.lineTo((int) Math.round(x2d), (int) Math.round(y2d));
		} else {
			Polygon p = getPolygon();
			ip.drawPolygon(p);
			updateFullWindow = true;
		}
	}

	public boolean contains(int x, int y) {
		if (getStrokeWidth() > 1) {
			if ((x == x1 && y == y1) || (x == x2 && y == y2))
				return true;
			else
				return getPolygon().contains(x, y);
		} else
			return false;
	}
	
	public void handleMouseDown(MouseEvent e, SlideGlass sg) {
        super.handleMouseDown(e, sg);//set start mouse position
    }

	/**
	 * Returns a handle number if the specified screen coordinates are inside or
	 * near a handle, otherwise returns -1.
	 */
	@Override
	public int isHandle(int sx, int sy, SlideGlass sg) {
		int size = HANDLE_SIZE + 5;
		if (getStrokeWidth() > 1) {
			size += (int) Math.log(getStrokeWidth());
		}
		int halfSize = size / 2;
		int sx1 = (int) (sg.screenXD(getXBase()+x1R) - halfSize);
		int sy1 = (int) (sg.screenYD(getYBase()+y1R) - halfSize);
		int sx2 = (int) (sg.screenXD(getXBase()+x2R) - halfSize);
		int sy2 = (int) (sg.screenYD(getYBase()+y2R) - halfSize);
		int sx3 = (int) (sx1 + (sx2-sx1)/2.);
		int sy3 = (int) (sy1 + (sy2-sy1)/2.);
		//rotation handle
		int sx4 = sx1 + (int)((sx2 - sx1) / 5.);// 1/5 location
		int sy4 = sy1 + (int)((sy2 - sy1) / 5.);
		int sx5 = sx1 + (int)((sx2 - sx1) - (sx2 - sx1) / 5.);
		int sy5 = sy1 + (int)((sy2 - sy1) - (sy2 - sy1) / 5.);
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy1&&sy<=sy1+size) return 0;
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy2&&sy<=sy2+size) return 1;
		if (sx>=sx3&&sx<=sx3+size+2&&sy>=sy3&&sy<=sy3+size+2) return 2;
		if (sx>=sx4&&sx<=sx4+size+2&&sy>=sy4&&sy<=sy4+size+2) return 3;
		if (sx>=sx5&&sx<=sx5+size+2&&sy>=sy5&&sy<=sy5+size+2) return 4;
		return -1;
	}

	public static int getWidth() {
		return lineWidth;
	}

	public void setWidth(int w) {
		if (w < 1)
			w = 1;
		int max = 500;
		if (w > max) {
			ImagePlus imp2 = WindowManager.getCurrentImage();
			if (imp2 != null) {
				max = Math.max(max, imp2.getWidth());
				max = Math.max(max, imp2.getHeight());
			}
			if (w > max)
				w = max;
		}
		lineWidth = w;
		widthChanged = true;
	}

	public void setStrokeWidth(float width) {
		super.setStrokeWidth(width);
		if (getStrokeColor() == RoiObj.getColor())
			wideLine = true;
	}

	/** Return the bounding rectangle of this line. */
	public Rectangle getBounds() {
		int xmin = (int) Math.round(Math.min(x1d, x2d));
		int ymin = (int) Math.round(Math.min(y1d, y2d));
		int w = (int) Math.round(Math.abs(x2d - x1d));
		int h = (int) Math.round(Math.abs(y2d - y1d));
		System.out.println("LINE getBounds():"+xmin+" "+ymin+" "+w+" "+h);
		return new Rectangle(xmin, ymin, w, h);
	}

	protected int clipRectMargin() {
		return 4;
	}
	
	/*
	 * center location 
	 * see, setRotationCenter in RoiObj.
	 */
	public void rotateLine(int angle) {
//		Point p1 = new Point(line.x1, line.y1);
//		Point p2 = new Point(line.x2, line.y2);
		//DO NOT DO THIS(this method calc before do it)
//		FloatPolygon fp = getRotationCenter();
//		double xcenter = fp.xpoints[0];
//		double ycenter = fp.ypoints[0];
//		Point center = new Point((int)xcenter, (int)ycenter);
//		Point rp1 = rotatePoint(center, p1, angle);
//		Point rp2 = rotatePoint(center, p2, angle);
		ij.gui.Roi rotatedLine = RoiRotator.rotate(new RoiConverter().convert2Roi(this), angle);
		ij.gui.Line l2 = (ij.gui.Line)rotatedLine;
		updateCoordinates(l2.x1d, l2.y1d, l2.x2d, l2.y2d);
		rotatedLine = null;
		l2 = null;
	}
	
	public Point[] rotatePoints(com.vis.core.view.D2.roi.Line line, int angle) {
//		Point p1 = new Point(line.x1, line.y1);
//		Point p2 = new Point(line.x2, line.y2);
//		FloatPolygon fp = getRotationCenter();
//		double xcenter = fp.xpoints[0];
//		double ycenter = fp.ypoints[0];
//		Point center = new Point((int)xcenter, (int)ycenter);
//		Point rp1 = rotatePoint(center, p1, angle);
//		Point rp2 = rotatePoint(center, p2, angle);
		ij.gui.Roi rotatedLine = RoiRotator.rotate(new RoiConverter().convert2Roi(this), angle);
		ij.gui.Line l2 = (ij.gui.Line)rotatedLine;
		Point rp1 = new Point(l2.x1, l2.y1);
		Point rp2 = new Point(l2.x2, l2.y2);
		rotatedLine = null;
		l2 = null;
		return new Point[] {rp1,rp2};
	}
	
	private Point getRotateEdgePoint(Point center, Point edge, int angle) {
	    double xRot = center.x + Math.cos(Math.toRadians(angle)) * (edge.x - center.x) - Math.sin(Math.toRadians(angle)) * (edge.y - center.y);
	    double yRot = center.y + Math.sin(Math.toRadians(angle)) * (edge.x - center.x) + Math.cos(Math.toRadians(angle)) * (edge.y - center.y);
	    return new Point((int) xRot, (int) yRot);
	}

	/** Nudge end point of line by one pixel. */
//	public void nudgeCorner(int key) {
//		double inc = 1.0 / slide.getMagnification();
//		switch (key) {
//		case KeyEvent.VK_UP:
//			y2R -= inc;
//			break;
//		case KeyEvent.VK_DOWN:
//			y2R += inc;
//			break;
//		case KeyEvent.VK_LEFT:
//			x2R -= inc;
//			break;
//		case KeyEvent.VK_RIGHT:
//			x2R += inc;
//			break;
//		}
//		grow((int) (x + x2R + slide.originX), (int) (y + y2R + slide.originY));//TODO change to screenXY
//		notifyListeners(RoiListener.MOVED);
//		showStatus();
//	}

	/** Always returns true. */
	public boolean subPixelResolution() {
		return true;
	}

	public void setLocation(int x, int y) {
		setLocation((double)x, (double)y);
	}

	/** Sets the x coordinate of the leftmost and y coordinate of the topmost end point */
	public void setLocation(double x, double y) {
		updateCoordinates(x+x1R, y+y1R, x+x2R, y+y2R);
	}

	@Override
	public FloatPolygon getRotationCenter() {
		double xcenter = x1d + (x2d - x1d) / 2.0;
		double ycenter = y1d + (y2d - y1d) / 2.0;
		FloatPolygon p = new FloatPolygon();
		p.addPoint(xcenter, ycenter);
		return p;
	}

	/**
	 * Dedicated point iterator for thin lines. The iterator is based on (an
	 * improved version of) the algorithm used by the original method
	 * {@code ImageProcessor.getLine(double, double, double, double)}. Improvements
	 * are (a) that the endpoint is drawn too and (b) every line point is visited
	 * only once, duplicates are skipped.
	 * 
	 * Author: Wilhelm Burger (04/2017)
	 */
	public static class PointIterator implements Iterator<Point> {
		private double x1, y1;
		private final int n;
		private final double xinc, yinc;
		private double x, y;
		private int u, v;
		private int u_prev, v_prev;
		private int i;

		public PointIterator(Line line) {
			this(line.x1d, line.y1d, line.x2d, line.y2d);
		}

		public PointIterator(double x1, double y1, double x2, double y2) {
			this.x1 = x1;
			this.y1 = y1;
			double dx = x2 - x1;
			double dy = y2 - y1;
			this.n = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy));
			this.xinc = dx / n;
			this.yinc = dy / n;
			x = x1;
			y = y1;
			u = (int) Math.round(x - 0.5);
			v = (int) Math.round(y - 0.5);
			u_prev = Integer.MIN_VALUE;
			v_prev = Integer.MIN_VALUE;
			i = 0;
		}

		@Override
		public boolean hasNext() {
			return i <= n; // needs to be '<=' to include last segment (point)!
		}

		@Override
		public Point next() {
			if (i > n)
				throw new NoSuchElementException();
			Point p = new Point(u, v); // the current (next) point
			moveToNext();
			return p;
		}

		// move to next point by skipping duplicate points
		private void moveToNext() {
			do {
				i = i + 1;
				x = x1 + i * xinc;
				y = y1 + i * yinc;
				u_prev = u;
				v_prev = v;
				u = (int) Math.round(x - 0.5);
				v = (int) Math.round(y - 0.5);
			} while (i <= n && u == u_prev && v == v_prev);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public Iterator<Point> iterator() {
		if (getStrokeWidth() <= 1.0)
			return new PointIterator(this); // use the specific thin-line iterator
		else
			return super.iterator(); // fall back on Roi's iterator
	}

}