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

import ij.ImagePlus;
import ij.process.*;
import ij.io.FileSaver;
import java.awt.*;
import java.awt.image.*;

import com.vis.core.view.D2.ui.glasses.SlideGlass;

/** An ImageRoi is an Roi that overlays an image. 
* @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
*/
public class ImageRoi extends RoiObj {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2701567868628690833L;
	private Image img;
	private Composite composite;
	private double opacity = 1.0;
	private double angle = 0.0;
	private boolean zeroTransparent;
	private ImageProcessor ip;

	/** Creates a new ImageRoi from a BufferedImage.*/
	public ImageRoi(int x, int y, BufferedImage bi, SlideGlass sg) {
		super(x, y, bi.getWidth(), bi.getHeight(),sg);
		img = bi;
		setStrokeColor(Color.black);
	}

	/** Creates a new ImageRoi from a ImageProcessor.*/
	public ImageRoi(int x, int y, ImageProcessor ip, SlideGlass sg) {
		super(x, y, ip.getWidth(), ip.getHeight(), sg);
		img = ip.createImage();
		this.ip = ip;
		setStrokeColor(Color.black);
	}
		
	public void draw(Graphics g) {
		Graphics2D g2d = (Graphics2D)g;	
		//TODO 20240817
		double mag = getMagnification();
		double compScale[] = getComponentScaleFactor();
		int sx2 = screenX(x+width);
		int sy2 = screenY(y+height);
		Composite saveComposite = null;
		if (composite!=null) {
			saveComposite = g2d.getComposite();
			g2d.setComposite(composite);
		}
		Image img2 = img;
		if (angle!=0.0) {
			ImageProcessor ip = new ColorProcessor(img);
			ip.setInterpolate(true);
			ip.setBackgroundValue(0.0);
			ip.rotate(angle);
			if (zeroTransparent)
				ip = makeZeroTransparent(ip, true);
			img2 = ip.createImage();
		}
		
		g.drawImage(img2, screenX(x), screenY(y), sx2, sy2, 0, 0, img.getWidth(null), img.getHeight(null), null);
		if (composite!=null) g2d.setComposite(saveComposite);
		if (isActiveOverlayRoi() && !overlay)
			super.draw(g);
 	}
 	 	
	/** Sets the composite mode. */
	public void setComposite(Composite composite) {
		this.composite = composite;
	}
	
	/** Sets the composite mode using the specified opacity (alpha), in the 
	     range 0.0-1.0, where 0.0 is fully transparent and 1.0 is fully opaque. */
	public void setOpacity(double opacity) {
		if (opacity<0.0) opacity = 0.0;
		if (opacity>1.0) opacity = 1.0;
		this.opacity = opacity;
		if (opacity!=1.0)
			composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)opacity);
		else
			composite = null;
	}
	
	/** Returns a serialized version of the image. */
	public byte[] getSerializedImage() {
		ImagePlus imp = new ImagePlus("",img);
		return new FileSaver(imp).serialize();
	}

	/** Returns the current opacity. */
	public double getOpacity() {
		return opacity;
	}

	public void rotate(double angle) {
		this.angle += angle;
	}

	public void setAngle(double angle) {
		this.angle = angle;
	}

	public void setZeroTransparent(boolean zeroTransparent) {
		if (this.zeroTransparent!=zeroTransparent) {
			ip = makeZeroTransparent(new ColorProcessor(img), zeroTransparent);
			img = ip.createImage();
		}
		this.zeroTransparent = zeroTransparent;
	}
	
	public boolean getZeroTransparent() {
		return zeroTransparent;
	}

	private ImageProcessor makeZeroTransparent(ImageProcessor ip, boolean transparent) {
		if (transparent) {
			ip.setColorModel(new DirectColorModel(32,0x00ff0000,0x0000ff00,0x000000ff,0xff000000));
			for (int x=0; x<ip.getWidth(); x++) {
				for (int y=0; y<ip.getHeight(); y++) {
					double v = ip.getPixelValue(x, y);
					if (v>1)
						ip.set(x, y, ip.get(x,y)|0xff000000); // set alpha bits
					else
						ip.set(x, y, ip.get(x,y)&0xffffff); // clear alpha bits
				}
			}
		}
		return ip;
	}

	public synchronized Object clone() {
		ImageRoi roi2 = (ImageRoi)super.clone();
		ImagePlus imp = new ImagePlus("", img);
		roi2.setProcessor(imp.getProcessor());
		roi2.setOpacity(getOpacity());
		return roi2;
	}
	
	public ImageProcessor getProcessor() {
		if (ip!=null)
			return ip;
		else {
			ip = new ColorProcessor(img);
			return ip;
		}
	}

	public void setProcessor(ImageProcessor ip) {
		img = ip.createImage();
		this.ip = ip;
		if (zeroTransparent) {
			setZeroTransparent(false);
			setZeroTransparent(true);
		}
		width = ip.getWidth();
		height = ip.getHeight();
	}

}