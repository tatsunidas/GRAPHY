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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

import javax.swing.JPanel;

import com.vis.core.log.Log;
import com.vis.dicom.Tag;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * A single frame image screen
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class ImageSpecimenGlass extends JPanel{
	
	/**
	 * src img
	 */
	private final DicomImage dcmImg;
	
	/**
	 * The orgImg is calibrated by SlideGlass.initImageInfo().
	 * See also, GDicomTools.calibrate() function.
	 */
	private ImagePlus orgImg;//without calibration
	BufferedImage display;
	
	Calibration orgCal;
	
	private final String sopUID;
	private final SlideGlass sg;
	
	/**
	 * diaplay image origin in original space (off-screen coordinates).
	 */
	public int originX;
	public int originY;
	
	final int orgCols;
	final int orgRows;
	
	private final Object drawLock = new Object();
	
	private boolean transparent = true;
	private float alpha = 1.0f;
	
	public ImageSpecimenGlass(SlideGlass sg /*single frame*/) {
		this.sg = sg;
		this.dcmImg = sg.getDicomImage();
		this.sopUID = dcmImg.getHeader().getString(Tag.SOP​Instance​UID);
		/* No calibrated imageplus */
		//this.orgImg = new ImagePlus(sopUID, dcmImg.getImageProcessor(0/*always 0*/));
		orgCols = dcmImg.getHeader().getInt(Tag.Columns, 0);
		orgRows = dcmImg.getHeader().getInt(Tag.Rows, 0);
		setOpaque(true);//最下層のため、true/false どちらでも良い
		setBackground(Color.BLACK);
		resetImageOrigin();
	}
	
	/**
	 * @return imageplus , copy of orgImg. 
	 */
	ImagePlus createInitialDisplayImage() {
		/*
		 * getOriginalImage().duplicate();//DO NOT USE, calibration was removed.
		 */
		ImagePlus org = getOriginalImage();
		if(org == null) {
			return null;
		}
		
		synchronized(org) {
			// 内部で再度 getOriginalImage() を呼ばず、ローカル変数 org を一貫して使う
			ImagePlus dup = org.createImagePlus();
			dup.setTitle("replica");
			
			ImageProcessor ip = org.getProcessor();
			if (ip == null) {
	            return null;
	        }
			ImageProcessor ip2 = ip.duplicate();
			ip2.setInterpolationMethod(sg.INTERPOLATION_METHOD);
			if(sg.isRGB && ip2 instanceof ColorProcessor) {
				ip2.snapshot();//keep original pixels
			}
			
			/*
			 * to fill black background after rotation.
			 * https://forum.image.sc/t/set-background-color-for-rotation-of-a-16-bit-image-shortprocessor-miss-bgcolor-attribute/20585/10
			 */
			if(dup.getBitDepth() == 8) {
				if(dcmImg.isSigned()) {
					ip2.setBackgroundValue(128);
				}else {
					ip2.setBackgroundValue(0);
				}
			}else if(dup.getBitDepth() == 16) {
				if(dcmImg.isSigned()) {
					ip2.setBackgroundValue(32768);
				}else {
					ip2.setBackgroundValue(0);
				}
			}else if(dup.getBitDepth() == 32) {
				ip2.setBackgroundValue(0.);
			}else {//color RGB
				ip2.setBackgroundValue(0);
				ip2.setBackgroundColor(Color.BLACK);
			}
			dup.setProcessor(ip2);
			dup.setCalibration(org.getCalibration());
			return dup;
		}
	}
	
	/**
	 * This method support the origin which is inside border area on slideglass.
	 * * @param newImgW
	 * @param newImgH
	 * @return
	 */
	Point calcDefaultImageOrigin(int newImgW, int newImgH) {
		Insets insets = sg.getInsets(); // the border's insets from slideglass
		int marginX = (getWidth() - insets.left - insets.right - newImgW) / 2;
		int marginY = (getHeight() - insets.top - insets.bottom - newImgH) / 2;
		// 原点の決定： 左(上)ボーダーの幅 + 余白
		int x = insets.left + marginX;
		int y = insets.top + marginY;
		return new Point(x, y);
	}
	
	/*
	 * fit size to slide
	 * image drawable area will be small by BORDER size.
	 * Maintain aspect ratio, i.e., scale XY will be same.
	 */
	Dimension calcImageSize2FitComponent() {
		/*
		 * The size of the border is calculated using Insets.
		 */
		if(sg == null) {
			return null;
		}
		
		Insets insets = sg.getInsets();//the border's insets
		int drawableWidth = getWidth() - insets.left - insets.right;
		int drawableHeight = getHeight() - insets.top - insets.bottom;
		
		int bound_width = drawableWidth;
		int bound_height = drawableHeight;
		
		if (bound_width < 1 || bound_height < 1) {
			return null;
		}
		int original_width = orgCols;
		int original_height = orgRows;
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
			new_height = bound_height;
			new_width = (new_height * original_width) / original_height;
		}
		return new Dimension(new_width, new_height);
	}
	
	public DicomImage getDicomImage() {
		return dcmImg;
	}
	
	public ImagePlus getOriginalImage() {
		return this.orgImg;
	}
	
	public Calibration getOriginalCalibration() {
		return this.orgCal;
	}
	
	int getDisplayOriginX() {
		return originX;
	}
	
	int getDisplayOriginY() {
		return originY;
	}
	
	public String sopInstanceUID() {
		return sopUID;
	}
	
	public void transparent(boolean on) {
		this.transparent = on;
	}
	
	public boolean isTransparent() {
		return transparent;
	}
	
	/**
	 * Update image to process.
	 * @param img
	 */
	void replaceImage(ImagePlus img) {
		if(dcmImg == null) {
			throw new IllegalArgumentException("ImageSpecimen DicomImage can not ready. Cannot replace image.");
		}
		if(img != null) {
			if(img.getType() == orgImg.getType() && img.getWidth() == orgCols && img.getHeight() == orgRows) {
				int samples = img.getProcessor() instanceof ColorProcessor ? 3 : 1;
				this.dcmImg.setPixelData(0/*0 base*/, orgCols, orgRows, samples, img.getBitDepth(), img.getProcessor().getPixels());
				this.orgImg = new ImagePlus(sopUID, dcmImg.getImageProcessor(0/*always 0*/));
				updateDisplayImage();
			}else {
				Log.logger.warning("Image type is not same. ImageSpecimen cannot replace image.");
			}
		}
	}
	
	/**
	 * 
	 * @param img: keep null-able
	 */
	void setOriginalImage(ImagePlus img) {
		if(dcmImg == null) {
			throw new IllegalArgumentException("ImageSpecimen DicomImage can not ready. Cannot set image to show.");
		}
		if(img != null) {
			// do not use getNChannels(), this will return 1 even if it is RGB.
			int imgSamples = img.getProcessor() instanceof ColorProcessor ? 3 : 1;
			
			int samples = sg.isRGB ? 3:1;
			
			if(sg.isPDF) {
				samples = 3;
			}
			
			int w=  img.getWidth();
			int h=  img.getHeight();
			
			if(imgSamples == samples && img.getWidth() == orgCols && img.getHeight() == orgRows) {
				this.orgImg = img;
				updateDisplayImage();
			}else {
				String txt = "Image type is not same. ImageSpecimen cannot replace image.\n";
				txt += "samples(new, current)="+imgSamples+","+samples+"\n";
				txt += "width and height(new, current)="+w+","+orgCols+":"+h+","+orgRows;
				Log.logger.warning(txt);
			}
		}else {
			this.orgImg = null;
		}
	}
	
	public void setOriginalCalibration(Calibration cal) {
		ImagePlus org = getOriginalImage();
		if(org != null) {
			org.setCalibration(cal);
		}
		this.orgCal = cal;
	}
	
	public void setAlphaForTransparent(Float alpha) {
		if(alpha == null) {
			this.alpha = 1.0f;
			return;
		}
		if(alpha < 0) {
			this.alpha = 0;
			return;
		}
		if(alpha>1) {
			this.alpha = 1;
			return;
		}
		this.alpha = alpha;
	}
	
	public void resetImageOrigin() {
		Dimension dispDim = calcImageSize2FitComponent();
		if(dispDim != null) {
			Point init_coord = calcDefaultImageOrigin(dispDim.width, dispDim.height);
			originX = init_coord.x;
			originY = init_coord.y;
		}
	}
	
	public void updateOriginWithCurrentCondition() {
		if(sg == null) {
			return;
		}
		Point newOrigin = sg.slideglassCoordinateFromOffScreen(0/*offscreenX*/, 0/*offscreenY*/);
		updateOrigin(newOrigin.x, newOrigin.y);
	}
	
	/**
	 * update origin with display coordinates system.
	 */
	public void updateOrigin(int originX, int originY) {
		this.originX = originX;
		this.originY = originY;
	}
	
	/**
	 * Use this.
	 * 1: do processing something 
	 * 2: then, imageSpecimen.updateDisplayImage();
	 * to update display image which applied current conditions.
	 */
	public void updateDisplayImage() {

		if (!this.isDisplayable() || !this.isVisible()) {
			return;
		}

		if (getWidth() <= 0 || getHeight() <= 0) {
			return;
		}
		
		if(sg == null) {
			return;
		}
		
		if(orgImg == null) {
			return;
		}
		
		if (!sg.panningFlag) {
			//to display open-up
			resetImageOrigin();
		}
		
		ImagePlus dup = createInitialDisplayImage();
		
		if(dup == null) {
			return;
		}
		
		synchronized (dup) { // ロックを開始
			//update transform
			sg.calculateCurrentAffineTransform();
			
			AffineTransform finalTransform = new AffineTransform(sg.getCurrentTransform());
			
			sg.imgProcess.applyLUT(dup, sg.currentLUT);
			
			//adjust contrast to current
			sg.imgProcess.windowing(dup, sg.currentMin, sg.currentMax);
			//invert if it set
			if(sg.isInverted()) {
				sg.imgProcess.invert(dup);
			}
			//create image to display
			BufferedImage srcImg = dup.getBufferedImage();

			int w = Math.max(1, getWidth()/*表示するコンポーネントサイズにする*/);
			int h = Math.max(1, getHeight()/*表示するコンポーネントサイズにする*/);

			int type = srcImg.getType();

			// 注意: TYPE_CUSTOM (0) の場合は扱いづらいため、ARGBにフォールバックします
			if (type == BufferedImage.TYPE_CUSTOM) {
				type = BufferedImage.TYPE_INT_ARGB;
			}
			
			boolean sizeChanged = false;

			// ImagePlusのタイプに合わせたBufferedImageを作成
			if (this.display == null || this.display.getWidth() != w || this.display.getHeight() != h || this.display.getType() != type) {
				sizeChanged = true;
				//for LUT, index color model
				if (type == BufferedImage.TYPE_BYTE_INDEXED) {
					this.display = new BufferedImage(w, h, type, (IndexColorModel) srcImg.getColorModel());
				}else {// normal
					this.display = new BufferedImage(w, h, type);
				}
			}

			try {
	            // AffineTransformOpの作成
				AffineTransformOp op = null;
				if(sg.INTERPOLATION_METHOD == ImageProcessor.BILINEAR) {
					op = new AffineTransformOp(finalTransform, AffineTransformOp.TYPE_BILINEAR);
				}else if(sg.INTERPOLATION_METHOD == ImageProcessor.BICUBIC) {
					op = new AffineTransformOp(finalTransform, AffineTransformOp.TYPE_BICUBIC);
				}else {
					op = new AffineTransformOp(finalTransform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
				}
				op.filter(srcImg, this.display);

	        } catch (Exception e) {
	            Log.logger.warning("Transform failed: " + e.getMessage());
	            return;
	        }
			if (sizeChanged) {
				revalidate();
			}
		}
		repaint();
	}
	
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		// テキストの滑らかさだけは維持
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		/*
		 * waiting state
		 */
		if (getOriginalImage() == null && dcmImg != null) {
			// 1. 背景を少し暗い色で塗りつぶす（視認性向上のため）
			g2d.setColor(Color.DARK_GRAY);
			g2d.fillRect(0, 0, getWidth(), getHeight());
			// 2. 描画する文字列と、フォントの設定
			String text = "Loading...";
			// 例: 少し大きめの太字フォントを設定する（見栄えを良くするため）
			Font font = g2d.getFont().deriveFont(Font.BOLD, 16f);
			g2d.setFont(font);
			g2d.setColor(Color.WHITE);

			// 3. 文字列の寸法を計算するためのFontMetricsを取得
			FontMetrics fm = g2d.getFontMetrics();
			int textWidth = fm.stringWidth(text);
			int textHeight = fm.getHeight();

			// 4. 中心座標を計算する
			// 横方向の中心 X座標: (パネル幅 - テキスト幅) / 2
			int x = (getWidth() - textWidth) / 2;

			// 縦方向の中心 Y座標: (パネル高さ - テキスト高さ) / 2 + アセント(ベースライン位置調整)
			// ※ g.drawStringのY座標は、文字の上端ではなく「ベースライン（基準線）」を指定するため、
			// 単純に高さの半分を足すのではなく、Ascent（ベースラインから文字上端までの高さ）を考慮します。
			int y = (getHeight() - textHeight) / 2 + fm.getAscent();

			// 5. 計算した座標に文字列を描画
			g2d.drawString(text, x, y);
			return;
		}
		
		/*
		 * Do not insert image transformation code here.
		 * Use updateDisplayImage().
		 */
		if(this.display != null) {
			synchronized (drawLock) { // ロックを開始
				/*
				 * DO NOT g2d.setTransform(at) in paintComponent.
				 * This cause display time lag.
				 */
				if (transparent) {
					g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
					g2d.drawImage(this.display, 0, 0, this);
				} else {
					g2d.drawImage(this.display, 0, 0, this);
				}
			}
		}
	}
}
