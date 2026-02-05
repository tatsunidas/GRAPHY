package com.vis.core.view.D2.roi;

import java.awt.Point;
import java.awt.geom.NoninvertibleTransformException;
import java.util.logging.Level;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.*;

import ij.*;

/** Freehand region of interest or freehand line of interest*/
@SuppressWarnings("serial")
public class FreehandRoi extends PolygonRoi {

	public FreehandRoi(int sx, int sy, int type, SlideGlass sg) {
		super(sx, sy, type, sg);
		if (nPoints == 2) {
			nPoints--;
		}
	}

	public void grow(int sx, int sy) {
		if (subPixelResolution() && xpf != null) {
			growFloat(sx, sy, slide);
			return;
		}

		Point p = null;
		try {
			p = slide.offScreenCoordinate(sx, sy);
		} catch (NoninvertibleTransformException nte) {
			nte.printStackTrace();
			Log.logger.log(Level.SEVERE, "CanvasGlass::activateRoiAt : Can not translate offscreen coordinates...");
		}

		int ox = p.x;
		int oy = p.y;

		if (ox < 0)
			ox = 0;
		if (oy < 0)
			oy = 0;
		if (ox > xMax)
			ox = xMax;
		if (oy > yMax)
			oy = yMax;
		if (ox != xp[nPoints - 1] + x || oy != yp[nPoints - 1] + y) {
			xp[nPoints] = ox - x;
			yp[nPoints] = oy - y;
			nPoints++;
			if (IJ.altKeyDown())
				wipeBack();
			if (nPoints == xp.length)
				enlargeArrays();
			drawLine();
		}
	}
              
	private void growFloat(int sx, int sy, SlideGlass sg) {
		Point p = null;
		try {
			p = slide.offScreenCoordinate(sx, sy);
		} catch (NoninvertibleTransformException nte) {
			nte.printStackTrace();
			Log.logger.log(Level.SEVERE, "CanvasGlass::activateRoiAt : Can not translate offscreen coordinates...");
		}

		double ox = p.x;
		double oy = p.y;
		if (ox < 0.0)
			ox = 0.0;
		if (oy < 0.0)
			oy = 0.0;
		if (ox > xMax)
			ox = xMax;
		if (oy > yMax)
			oy = yMax;
		double xbase = getXBase();
		double ybase = getYBase();
		if (ox != xpf[nPoints - 1] + xbase || oy != ypf[nPoints - 1] + ybase) {
			xpf[nPoints] = (float) (ox - xbase);
			ypf[nPoints] = (float) (oy - ybase);
			nPoints++;
			if (nPoints == xpf.length)
				enlargeArrays();
			drawLine();
		}
	}
    
    void drawLine() {
        int margin = 4;
        if (lineWidth>margin && isLine())
            margin = lineWidth;
        if (slide!=null) {
            double mag = slide.getMagnification();
            if (mag<1.0) margin = (int)(margin/mag);
        }
        if (IJ.altKeyDown())
            margin += 20; // for wipeBack
        
        if(slide !=null) {
        	CanvasGlass p = (CanvasGlass)slide.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
        	p.repaint();
        }
    }

    public void handleMouseUp(int screenX, int screenY) {
        if (state==CONSTRUCTING) {
            addOffset();
            finishPolygon();
        }
        state = NORMAL;
    }

}