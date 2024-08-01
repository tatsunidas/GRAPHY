package com.vis.core.view.D2.roi;

import java.awt.*;
import java.awt.event.MouseEvent;
//import java.awt.geom.Rectangle2D;
//import java.awt.image.*;

import com.vis.core.view.D2.ui.glasses.*;

import ij.*;
import ij.gui.Line;
import ij.gui.Wand;
import ij.process.*;
import ij.measure.Calibration;

/** Oval region of interest */
@SuppressWarnings("serial")
public class OvalRoi extends RoiObj {

	/*
	 * https://github.com/imagej/imagej1/blob/master/ij/gui/OvalRoi.java
	 */
	/** Creates an OvalRoi.*/
	public OvalRoi(int x, int y, int width, int height, SlideGlass slide) {
		super(x, y, width, height, 0, slide);
		type = OVAL;
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
				System.out.println("GROW OVAL");
				grow(sx, sy);
				break;
			case MOVING:
				System.out.println("MOVING OVAL");
				move(sx, sy);
				break;
			case MOVING_HANDLE:
				System.out.println("MOVING_HANDLE OVAL");
				moveHandle(sx, sy);
				break;
			default:
				break;
		}
	}

	protected void moveHandle(int sx, int sy) {
		System.out.println("move handle oval");
		double asp;
//		if (clipboard!=null) return;
		int ox = slide.onImageX(sx);
		int oy = slide.onImageY(sy);
		//IJ.log("moveHandle: "+activeHandle+" "+ox+" "+oy);
		int x1=x, y1=y, x2=x+width, y2=y+height, xc=x+width/2, yc=y+height/2;
		int w2 = (int)(0.14645*width);
		int h2 = (int)(0.14645*height);
		if (width > 7 && height > 7) {
			asp = (double)width/(double)height;
			asp_bk = asp;
		} else {
			asp = asp_bk;
		}
		switch (activeHandle) {
			case 0: x=ox-w2; y=oy-h2; break;
			case 1: y=oy; break;
			case 2: x2=ox+w2; y=oy-h2; break;
			case 3: x2=ox; break;			
			case 4: x2=ox+w2; y2=oy+h2; break;
			case 5: y2=oy; break;
			case 6: x=ox-w2; y2=oy+h2; break;
			case 7: x=ox; break;
		}
		//if (x<0) x=0; if (y<0) y=0;
		if (x<x2)
		   width=x2-x;
		else
		  {width=1; x=x2;}
		if (y<y2)
		   height = y2-y;
		else
		   {height=1; y=y2;}
		if(center) {
			switch(activeHandle){
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
			if(x>=x2) {
				width=1;
				x=x2=xc;
			}
			if(y>=y2) {
				height=1;
				y=y2=yc;
			}

		}

		if (constrain) {
			if(activeHandle==1 || activeHandle==5) width=height;
			else height=width;
			
			if(x>=x2) {
				width=1;
				x=x2=xc;
			}
			if (y>=y2) {
				height=1;
				y=y2=yc;
			}
			switch(activeHandle){
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
			if (center){
				x=xc-width/2;
				y=yc-height/2;
			}
		}

		if (aspect && !constrain) {
			if(activeHandle==1 || activeHandle==5) width=(int)Math.rint((double)height*asp);
			else height=(int)Math.rint((double)width/asp);

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
			if (center){
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
		}
//		bounds = new Rectangle2D.Double(x, y, width, height);
		updateClipRect(slide);
		oldX=x; oldY=y;
		oldWidth=width; oldHeight=height;
		cachedMask = null;
		bounds = null;
		slide.getObservables().repaint();
	}

	public void draw(Graphics g, SlideGlass sg) {
		Color color =  strokeColor!=null? strokeColor:ROIColor;
		if (fillColor!=null) color = fillColor;
		if (isActiveOverlayRoi()) {
			if (color == Color.cyan)
				color = ROIColor;
			else
				color = Color.cyan;
		}
		g.setColor(color);
		
		double mag = sg.getMagnification();
		double compScale = sg.getScaleFactor();
		
		int sw = (int)(width*mag*compScale);
		int sh = (int)(height*mag*compScale);
		int sx1 = sg.screenX((int)getXBase());
		int sy1 = sg.screenY((int)getYBase());
				
		if (subPixelResolution() && bounds!=null) {
			sw = (int)(bounds.width*mag*compScale);
			sh = (int)(bounds.height*mag*compScale);
		}
		int sw2 = (int)(0.14645*width*mag*compScale);
		int sh2 = (int)(0.14645*height*mag*compScale);
		int sx2 = sx1+sw/2;
		int sy2 = sy1+sh/2;
		int sx3 = sx1+sw;
		int sy3 = sy1+sh;
		Graphics2D g2d = (Graphics2D)g;
		if (stroke!=null) 
			g2d.setStroke(getScaledStroke(sg));
//		if (fillColor!=null) {
//			if (!overlay && isActiveOverlayRoi()) {
//				g.setColor(Color.cyan);
//				g.drawOval(sx1, sy1, sw, sh);
//			} else
//				g.fillOval(sx1, sy1, sw, sh);
//		} else
//			g.drawOval(sx1, sy1, sw, sh);
		// now, always show roi
		g.drawOval(sx1, sy1, sw, sh);
		if (state!=CONSTRUCTING && clipboard==null && !overlay) {
			drawHandle(g, sx1+sw2, sy1+sh2, sg);
			drawHandle(g, sx3-sw2, sy1+sh2, sg);
			drawHandle(g, sx3-sw2, sy3-sh2, sg);
			drawHandle(g, sx1+sw2, sy3-sh2, sg);
			drawHandle(g, sx2, sy1, sg);
			drawHandle(g, sx3, sy2, sg);
			drawHandle(g, sx2, sy3, sg);
			drawHandle(g, sx1, sy2, sg);
		}
//		drawPreviousRoi(g);
	}

	/** Draws an outline of this OvalRoi on the image. */
	public void drawPixels(ImageProcessor ip) {
		Polygon p = getPolygon();
		if (p.npoints>0) {
			int saveWidth = ip.getLineWidth();
			if (getStrokeWidth()>1f)
				ip.setLineWidth((int)Math.round(getStrokeWidth()));
			ip.drawPolygon(p);
			ip.setLineWidth(saveWidth);
		}
		if (Line.getWidth()>1 || getStrokeWidth()>1)
			updateFullWindow = true;
	}		

	/** Returns this OvalRoi as a Polygon. */
	public Polygon getPolygon() {
		ImageProcessor mask = getMask();
		Wand wand = new Wand(mask);
		wand.autoOutline(width/2,height/2, 255, 255);
        for (int i=0; i<wand.npoints; i++) {
            wand.xpoints[i] += x;
            wand.ypoints[i] += y;
        }
		return new Polygon(wand.xpoints, wand.ypoints, wand.npoints);
	}		

	/** Returns this OvalRoi as a FloatPolygon. */
	public FloatPolygon getFloatPolygon() {
		Polygon p = getPolygon();
		return new FloatPolygon(toFloat(p.xpoints), toFloat(p.ypoints), p.npoints);
	}
	
	/** Returns the number of points in this selection; equivalent to getPolygon().npoints. */
	public int size() {
		return getPolygon().npoints;
	}


	/** Tests if the specified point is inside the boundary of this OvalRoi.
	* Authors: Barry DeZonia and Michael Schmid
	*/
	public boolean contains(int ox, int oy) {
		double a = width*0.5;
		double b = height*0.5;
		double cx = x + a - 0.5;
		double cy = y + b - 0.5;
		double dx = ox - cx;
		double dy = oy - cy;
		return ((dx*dx)/(a*a) + (dy*dy)/(b*b)) <= 1.0;
	}
		
	/** Returns a handle number if the specified screen coordinates are  
		inside or near a handle, otherwise returns -1. */
	public int isHandle(int sx, int sy,SlideGlass sg) {
//		if (clipboard!=null || ic==null) return -1;
		double mag = sg.getMagnification();
		
		int px = sg.onImageX(sx);
		int py = sg.onImageY(sy);
		
		int size = HANDLE_SIZE+3;
		int halfSize = size/2;
		int x1 = x - halfSize;
		int y1 = y - halfSize;
		int x3 = x+width - halfSize;
		int y3 = y+height - halfSize;
		int x2 = x1 + (x3 - x1)/2;
		int y2 = y1 + (y3 - y1)/2;
		
		int w2 = (int)(0.14645*(x3-x1));
		int h2 = (int)(0.14645*(y3-y1));
		
		if (px>=x1+w2&&px<=x1+w2+size&&py>=y1+h2&&py<=y1+h2+size) return 0;
		if (px>=x2&&px<=x2+size&&py>=y1&&py<=y1+size) return 1;		
		if (px>=x3-w2&&px<=x3-w2+size&&py>=y1+h2&&py<=y1+h2+size) return 2;		
		if (px>=x3&&px<=x3+size&&py>=y2&&py<=y2+size) return 3;		
		if (px>=x3-w2&&px<=x3-w2+size&&py>=y3-h2&&py<=y3-h2+size) return 4;		
		if (px>=x2&&px<=x2+size&&py>=y3&&py<=y3+size) return 5;		
		if (px>=x1+w2&&px<=x1+w2+size&&py>=y3-h2&&py<=y3-h2+size) return 6;
		if (px>=x1&&px<=x1+size&&py>=y2&&py<=y2+size) return 7;
		return -1;
	}

	public ImageProcessor getMask() {
		if (cachedMask!=null && cachedMask.getPixels()!=null)
			return cachedMask;
		ImageProcessor mask = new ByteProcessor(width, height);
		double a=width/2.0, b=height/2.0;
		double a2=a*a, b2=b*b;
        a -= 0.5; b -= 0.5;
		double xx, yy;
        int offset;
        byte[] pixels = (byte[])mask.getPixels();
		for (int y=0; y<height; y++) {
            offset = y*width;
			for (int x=0; x<width; x++) {
				xx = x - a;
				yy = y - b;   
				if ((xx*xx/a2+yy*yy/b2)<=1.0)
					pixels[offset+x] = -1;
			}
		}
		cachedMask = mask;
		return mask;
	}

	/** Returns the perimeter length. */
	public double getLength() {
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		return Math.PI*(width*pw+height*ph)/2.0;
	}
		
}
