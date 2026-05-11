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

package com.vis.core.slicer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.joml.Vector3d;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.ImageOrientation;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.IntersectVolume;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.core.view.D2.ui.orientation.SlicePlane;
import com.vis.core.view.D3.ui.GantryTiltCorrector;
import com.vis.dicom.Modality;
import com.vis.dicom.Tag;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
import ij.process.ImageProcessor;

/**
 * MPR view ref:
 * https://imagej.nih.gov/ij/developer/source/ij/plugin/Orthogonal_Views.java.html
 * 
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class SlicerWindow extends JFrame {

	/*
	 * debug
	 */
	public static void main(String[] args) {
		// axi src
//		ImagePlus ax = FolderOpener.open(
//				"/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151");

		ImagePlus ax = FolderOpener.open("C:\\Users\\t_kob\\Desktop\\signed");

//		ImagePlus ct = new ImagePlus("/home/tatsunidas/graphy_sample_images/dicom_samples/JIRA_DICOM/CT_LEE_IR87a.dcm");

//		Praparat xy_prap = new Praparat(ax, null, ViewMode.MPR);
//		SeriesWindow se = new SeriesWindow(xy_prap);

//		ImagePlus ax = FolderOpener.open("/home/tatsunidas/デスクトップ/LUNG1-246");
		new SlicerWindow(ax, null);

		// cor src
//		String corDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/T1COR";
//		ImagePlus xz = FolderOpener.open(corDir);
//		new MPRViewerWindow(xz, null);

		// sag src
//		String sagDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";
//		ImagePlus yz = FolderOpener.open(sagDir);
//		new MPRViewerWindow(yz, null);

		// other
//		String otherDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/HASSAKU_3DT1 GEIR/";
//		ImagePlus o = FolderOpener.open(otherDir);
//		new MPRViewerWindow(o, null);
	}

	// praparat position
	public static final int XY = 0;
	public static final int XZ = 1;
	public static final int YZ = 2;
	public static final int RECON = 3;

	final SlicerWindow own;

	/*
	 * https://forum.image.sc/t/rotate-line-roi-via-rotating-point-coordinates/8323
	 */
	private SlicerControlPanel contP;

	private ImagePlus imp;// src imp, to backup.

	private ImagePlus xy_image, xz_image, yz_image, recon_image, init_image;

	Eyepiece eye = null;
	private Praparat xy_prap = null;
	private Praparat xz_prap = null;
	private Praparat yz_prap = null;
	private Praparat recon_prap = null;
	private Praparat currentPrap = null;// for mouse action

	private ReferenceLineMPR refLines;
	private CutSurface srcCutSurface;/* original image plane */

	private final String patID;
	private final String studyUID;
	private final String refUID;
	private final String sopClassUID;
	Color studyColor = Color.DARK_GRAY;

	boolean starting = true;
	boolean standalone = false;

	int reconResolution = 5; // keep odd value.
	private final String reconNotReady = "NOT-READY";

	private Logger logger = Log.logger;

	private boolean isSigned = false;

	/**
	 * See also, D3.ui.GatryTiltCorrector
	 * 
	 * @param prap
	 */
	public SlicerWindow(Praparat prap) {
		this(prap.getImagePlus(), prap.getStudyColor());
	}

	public SlicerWindow(ImagePlus imp, Color studyColor) {
		if (imp == null || imp.getStackSize() < 1) {
			throw new IllegalArgumentException("Number of images not enough.");
		}
		patID = GDicomTools.getTag(imp, "0010,0020");
		studyUID = GDicomTools.getTag(imp, "0020,000D");
		sopClassUID = GDicomTools.getTag(imp, "0008,0016");
		refUID = GDicomTools.getTag(imp, "0020,0052");
		/*
		 * Tilt check
		 */
		Modality m = Modality.is(GDicomTools.getTag(imp, Tag.Modality));
		if (m == Modality.CT) {
			GantryTiltCorrector gtc = new GantryTiltCorrector();
			double tiltAngle = GDicomTools.getDouble(imp, 1, "0018,1120"/* Gantry/Detector Tilt */);
			double pixelSpacingY = imp.getCalibration().pixelHeight;
			double sliceSpacing = GDicomTools.getVoxelDepth(imp);
			double reconSliceSpacing = sliceSpacing < 1d ? sliceSpacing : 1d;
			imp = gtc.correctVolume3D(imp, tiltAngle, pixelSpacingY, sliceSpacing, reconSliceSpacing);
		}
		own = this;
		this.imp = imp;
		if (studyColor != null) {
			this.studyColor = studyColor;
		}

		isSigned = imp.getProcessor().isSigned16Bit();// DICOMにInterceptがある場合、falseのままになる
		String pi = GDicomTools.getTag(imp, Tag.PixelRepresentation);
		try {
			int v = Integer.parseInt(pi);
			isSigned = v == 1;
		} catch (NumberFormatException | NullPointerException e) {
		}

		init();
	}

	public String getPatientID() {
		return this.patID;
	}

	public String getStudyUID() {
		return this.studyUID;
	}

	public ArrayList<Praparat> getSelectedPraps() {
		return eye.getSelectingPraparats();
	}

	public Double getFOV() {
		return contP.getFOV();
	}

	public Double getFOV_W() {
		return contP.getFOV_W();
	}

	public Double getFOV_H() {
		return contP.getFOV_H();
	}

	public Double getSliceThickness() {
		return contP.getSliceThickness();
	}

	public Double getSliceGap() {
		return contP.getSliceGap();
	}

	public Integer getNumberOfSlices() {
		return contP.getNumberOfSlices();
	}

	public ImagePlus getSrcImage() {
		return imp;
	}

	private void init() {
		srcCutSurface = ImageOrientation.getCutSurface(imp);
		initImages();
		buildGUI();
		initReferenceLine();
		revalidate();
		setVisible(true);

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// ★ デバッグログ: 自動調整の「前」の状態を確認
				logDisplayStatus("Before AutoWindow XY (Axial)", xy_image);
				logDisplayStatus("Before AutoWindow XZ (Coronal)", xz_image);
				logDisplayStatus("Before AutoWindow YZ (Sagittal)", yz_image);

				// 各断面の表示を自動調整 (※ここが原因の可能性大)
				xy_prap.applyGlobalAutoWindow();
				yz_prap.applyGlobalAutoWindow();
				xz_prap.applyGlobalAutoWindow();

				// ★ デバッグログ: 自動調整の「後」の状態を確認
				logDisplayStatus("After AutoWindow XY (Axial)", xy_image);
				logDisplayStatus("After AutoWindow XZ (Coronal)", xz_image);
				logDisplayStatus("After AutoWindow YZ (Sagittal)", yz_image);

				// スライダーを中央に移動
				if (xy_image != null)
					xy_prap.setImagePositionUsingSlider(xy_image.getNSlices() / 2);
				if (xz_image != null)
					xz_prap.setImagePositionUsingSlider(xz_image.getNSlices() / 2);
				if (yz_image != null)
					yz_prap.setImagePositionUsingSlider(yz_image.getNSlices() / 2);

				recon_prap.setImagePositionUsingSlider(0);
			}
		});
	}

	/**
	 * コントラスト設定（DisplayRange）とキャリブレーションをログ出力する補助メソッド
	 */
	private void logDisplayStatus(String label, ImagePlus img) {
		if (img == null) {
			Log.logger.warning("[CONTRAST_DEBUG] " + label + " is NULL");
			return;
		}
		Calibration cal = img.getCalibration();
		double[] coef = (cal != null) ? cal.getCoefficients() : null;
		String coefStr = (coef != null && coef.length >= 2)
				? String.format("Slope=%.4f, Intercept=%.4f", coef[1], coef[0])
				: "None";

		Log.logger.info(String.format("[CONTRAST_DEBUG] %s: DispRange=[%.2f to %.2f], Coeffs: %s", label,
				img.getDisplayRangeMin(), img.getDisplayRangeMax(), coefStr));
	}

	private void buildGUI() {
		if (xz_image == null || yz_image == null) {
			logger.log(Level.WARNING, "Does not ready to start MPR window.");
			return;
		}
		// init view
		setTitle(ConfigInfo.SlicerWindow.toString());
		setName(ConfigInfo.SlicerWindow.toString());
		setSize(new Dimension(1200, 800));

		WindowAdapter ada = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				WindowManager.removeWindow(own);
			}
		};
		addWindowListener(ada);

		eye = new Eyepiece(patID);
		add(eye, BorderLayout.CENTER);

		// menubar
		SlicerMenuBar bar = new SlicerMenuBar(this);
		setJMenuBar(bar);
		// control panel
		contP = new SlicerControlPanel(this);
		add(contP, BorderLayout.NORTH);
		contP.setPreferredSize(new Dimension(getWidth(), 40));

		Log.logger.log(Level.FINE, "IOP axial:" + GDicomTools.getTag(xy_image, Tag.ImageOrientationPatient));
		System.out.println("IOP axial:" + GDicomTools.getTag(xy_image, Tag.ImageOrientationPatient));

		xy_prap = new Praparat(xy_image, studyColor, ViewMode.MPR, true);
		xz_prap = new Praparat(xz_image, studyColor, ViewMode.MPR, true);
		yz_prap = new Praparat(yz_image, studyColor, ViewMode.MPR, true);
		recon_prap = new Praparat(recon_image, studyColor, ViewMode.Normal, false);

		xy_prap.setName("XY");// IMPORTANT
		xz_prap.setName("XZ");
		yz_prap.setName("YZ");

		eye.addPraparat(xy_prap);
		eye.addPraparat(xz_prap);
		eye.addPraparat(yz_prap);
		eye.addPraparat(recon_prap);

		eye.autoLayout();// 2 * 2 grid layout
		setLocationRelativeTo(null);
	}

	ImagePlus getSliceTargetImage(CutSurface axis) {
		if (axis == CutSurface.AXIAL) {
			return xyImage();
		} else if (axis == CutSurface.CORONAL) {
			return xzImage();
		} else if (axis == CutSurface.SAGITTAL) {
			return yzImage();
		} else {
			return xyImage();
		}
	}

	public ImagePlus xyImage() {
		return xy_image;
	}

	public ImagePlus xzImage() {
		return xz_image;
	}

	public ImagePlus yzImage() {
		return yz_image;
	}

	public ImagePlus reconImage() {
		if (recon_image == init_image) {
			return null;
		}
		return recon_image;
	}

	private void addUIDs(ImagePlus imp, int pos /* 1 to N */, String seriesUID) {
		imp.setPosition(pos);
		// SOPClassUID
		GDicomTools.setTag(imp, pos, "0008,0016", sopClassUID);
		GDicomTools.setTag(imp, pos, "0010,0020", patID);
		GDicomTools.setTag(imp, pos, "0020,000D", studyUID);
		GDicomTools.setTag(imp, pos, "0020,000E", seriesUID);
		GDicomTools.setTag(imp, pos, "0020,0052", refUID);
		// SOPInstUID
		GDicomTools.setTag(imp, pos, "0008,0018", UIDUtils.createUID());
	}

	/**
	 * Calculate reconstruct stack size from src.
	 * 
	 * @param src
	 * @param cutSurface
	 * @return
	 */
	int[] calculateOrthogonalImageSize(ImagePlus src, CutSurface srcCutSurface, CutSurface targetCutSurface) {

		if (srcCutSurface == targetCutSurface) {
			return new int[] { src.getWidth(), src.getHeight() };
		}

		int size = src.getNSlices();// num of slice
		Calibration cal = src.getCalibration().copy();
		double calx = cal.pixelWidth;
		double caly = cal.pixelHeight;
		double calz = cal.pixelDepth;

		double ax = 1.0;// calx/calx
		double ay = caly / calx;
		double az = calz / calx;
		double arat = az / ax;
		double brat = az / ay;
		int za = (int) Math.ceil(size * arat);
		int zb = (int) Math.ceil(size * brat);

		switch (srcCutSurface) {
		case AXIAL:
			if (targetCutSurface == CutSurface.CORONAL) {
				return new int[] { src.getWidth(), za };
			} else if (targetCutSurface == CutSurface.SAGITTAL) {
				return new int[] { src.getHeight(), zb };
			}
		case SAGITTAL:
			if (targetCutSurface == CutSurface.AXIAL) {
				return new int[] { za, src.getWidth() };
			} else if (targetCutSurface == CutSurface.CORONAL) {
				return new int[] { zb, src.getHeight() };
			}
		case CORONAL:
			if (targetCutSurface == CutSurface.AXIAL) {
				return new int[] { src.getWidth(), za };
			} else if (targetCutSurface == CutSurface.SAGITTAL) {
				return new int[] { zb, src.getHeight() };
			}
		default:// unknown
			return null;
		}
	}

	/**
	 * @param is - used to get the dimensions of the new ImageProcessors
	 * @return
	 */
	private void initImages() {
		if (imp == null) {
			return;
		}
		PlanarSupport.standardizeStackOrientation(imp);
		Calibration cal = imp.getCalibration();
		cal.pixelDepth = GDicomTools.getVoxelDepth(imp);
		if (srcCutSurface == CutSurface.AXIAL) {
			xy_image = imp;// new Duplicator().run(imp);
			xy_image.setCalibration(cal);
			xz_image = constructXZ(xy_image, isSigned);
			yz_image = constructYZ(xy_image, isSigned);
		} else if (srcCutSurface == CutSurface.CORONAL) {
			xz_image = imp;
			xz_image.setCalibration(cal);
			xy_image = constructXY(xz_image, isSigned);
			yz_image = constructYZ(xy_image, isSigned);
		} else if (srcCutSurface == CutSurface.SAGITTAL) {
			yz_image = imp;
			yz_image.setCalibration(cal);
			xy_image = constructXY(yz_image, isSigned);
			xz_image = constructXZ(xy_image, isSigned);
		}
		initRecon();
	}

	private void initRecon() {
		/*
		 * see also MPRMenuBar:Save as... if named "NOT-READY", return null by
		 * getMPRImages()
		 */
		if (srcCutSurface == CutSurface.AXIAL) {
			init_image = NewImage.createByteImage(reconNotReady, xy_image.getWidth(), xy_image.getHeight(), 1,
					NewImage.FILL_BLACK);
			recon_image = init_image;
		} else if (srcCutSurface == CutSurface.CORONAL) {
			init_image = NewImage.createByteImage(reconNotReady, xz_image.getWidth(), xz_image.getHeight(), 1,
					NewImage.FILL_BLACK);
			recon_image = init_image;
		} else {
			init_image = NewImage.createByteImage(reconNotReady, yz_image.getWidth(), yz_image.getHeight(), 1,
					NewImage.FILL_BLACK);
			recon_image = init_image;
		}
		addUIDs(recon_image, 1, UIDUtils.createUID());
	}

	synchronized void update() {
		notify();
	}

	ImagePlus constructXY(ImagePlus src, boolean isSigned) {
		ImagePlus xy = null;
		Calibration cal = null;
		if (srcCutSurface == CutSurface.CORONAL) {
			xy = new OrthogonalSlice().coronalToAxial(src, isSigned);
		} else if (srcCutSurface == CutSurface.SAGITTAL) {
			xy = new OrthogonalSlice().sagittalToAxial(src, isSigned);
		} else {
			throw new IllegalArgumentException("Cannnot create Axial images.");
		}
		cal = xy.getCalibration();
		String seriesUID = UIDUtils.createUID();
		int size = xy.getNSlices();
		for (int z = 1; z <= size; z++) {
			addUIDs(xy, z, seriesUID);
		}
		Calibration calHolder = this.imp.getCalibration().copy();// with density calibration
		calHolder.pixelWidth = cal.pixelWidth;
		calHolder.pixelHeight = cal.pixelHeight;
		calHolder.pixelDepth = cal.pixelDepth;
		calHolder.setXUnit(cal.getXUnit());
		calHolder.setYUnit(cal.getYUnit());
		calHolder.setZUnit(cal.getZUnit());
		xy.setCalibration(calHolder);
		xy.setDisplayRange(src.getDisplayRangeMin(), src.getDisplayRangeMax());
		return xy;
	}

	ImagePlus constructXZ(ImagePlus src, boolean isSigned) {
		if (ImageOrientation.getCutSurface(src) != CutSurface.AXIAL) {
			throw new IllegalArgumentException("Cannot create XZ...");
		}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		ImageStack stack = new ImageStack();
		String seriesUID = UIDUtils.createUID();
		int size = src.getHeight();
		Calibration cal = null;
		for (int y = 0; y < size; y++) {
			ImagePlus xz_ = orthTool.cutHorizontally(src, y);
			if (cal == null) {
				cal = xz_.getCalibration();
			}

			//set pixel representation
			GDicomTools.setTag(xz_, 1, "0028,0103", isSigned ? "1" : "0");

			addUIDs(xz_, 1, seriesUID);
			stack.addSlice(xz_.getProcessor());
			/*
			 * xz_ is non-stack image plus. Tags set to properties.
			 */
			stack.setSliceLabel(xz_.getInfoProperty(), y + 1);
		}
		ImagePlus xz_imp = new ImagePlus("XZ", stack);
		xz_imp.setCalibration(cal);
		xz_imp.setDisplayRange(src.getDisplayRangeMin(), src.getDisplayRangeMax());
		return xz_imp;
	}

	ImagePlus constructYZ(ImagePlus src, boolean isSigned) {
		if (ImageOrientation.getCutSurface(src) != CutSurface.AXIAL) {
			throw new IllegalArgumentException("Cannot create YZ...");
		}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		ImageStack stack = new ImageStack();
		Calibration cal = null;
		String seriesUID = UIDUtils.createUID();
		int width = src.getWidth();
		for (int w = 0; w < width; w++) {
			ImagePlus yz_ = orthTool.cutVertically(src, w);
			if (cal == null) {
				cal = yz_.getCalibration();
			}
			
			//set pixel representation
			GDicomTools.setTag(yz_, 1, "0028,0103", isSigned ? "1" : "0");
			
			addUIDs(yz_, 1, seriesUID);
			stack.addSlice(yz_.getProcessor());
			stack.setSliceLabel(yz_.getInfoProperty(), w + 1);
		}
		ImagePlus yz_imp = new ImagePlus("YZ", stack);
		yz_imp.setCalibration(cal);
		yz_imp.setDisplayRange(src.getDisplayRangeMin(), src.getDisplayRangeMax());
		return yz_imp;
	}

	/**
	 * 
	 * @param p : offscreen x and y.
	 */
	private void updateCrossSectionUsingXY(Point xyP) {
		if (xy_prap == null) {
			return;
		}
		int xyX = xyP.x;
		int xyY = xyP.y;
//		int xyZ = xy_prap.getCurrentSlidePos();
		/*
		 * Since it is not an ISO voxel, the position of the cross-section is
		 * recalculated from each voxel size.
		 */
		// update xz
		double az = xz_image.getNSlices() / (double) xy_image.getHeight();
		int xzZ = (int) (xyY * az);
		if (xzZ < 0) {
			xzZ = 0;
		} else if (xzZ > xz_image.getNSlices() - 1) {
			xzZ = xz_image.getNSlices() - 1;
		}
		xz_prap.setImagePositionUsingSlider(xzZ);

		// update yz
		az = yz_image.getNSlices() / (double) xy_image.getWidth();
		int yzZ = (int) (xyX * az);
		if (yzZ < 0) {
			yzZ = 0;
		} else if (yzZ > yz_image.getNSlices() - 1) {
			yzZ = yz_image.getNSlices() - 1;
		}
		yz_prap.setImagePositionUsingSlider(yzZ);
	}

	/**
	 * 
	 * @param p : point on XZ slideglass
	 */
	@SuppressWarnings("unused")
	private void updateCrossSectionUsingXZ(Point xzP) {
		int xzX = xzP.x;
		int xzY = xzP.y;
		int xzZ = xz_prap.getCurrentSlidePos();
		boolean slicingToUpper = false;

		int prev_pos = xy_image.getCurrentSlice();
		double[] ipp1 = GDicomTools.getImagePositionPatient(xy_image, 1);
		double[] ipp2 = GDicomTools.getImagePositionPatient(xy_image, 2);
		if (ipp1 != null && ipp2 != null) {
			// back to prev pos
			xy_image.setSlice(prev_pos);
			slicingToUpper = ipp1[2] < ipp2[2];
		}

		// update xy
		double az = xy_image.getNSlices() / (double) xz_image.getHeight();
		int xyZ = 0;
		if (slicingToUpper) {
			xyZ = (int) ((xz_image.getHeight() - xzY) * az);
		} else {
			xyZ = (int) (xzY * az);
		}
		if (xyZ < 0) {
			xyZ = 0;
		} else if (xyZ > xy_image.getNSlices() - 1) {
			xyZ = xy_image.getNSlices() - 1;
		}
		xy_prap.setImagePositionUsingSlider(xyZ);

		// update yz
		az = yz_image.getNSlices() / (double) xz_image.getWidth();
		int yzZ = (int) (xzX * az);
		if (yzZ < 0) {
			yzZ = 0;
		} else if (yzZ > yz_image.getNSlices() - 1) {
			yzZ = yz_image.getNSlices() - 1;
		}
		yz_prap.setImagePositionUsingSlider(yzZ);
		repaint();
	}

	/**
	 * 
	 * @param p : point on YZ slideglass
	 */
	private void updateCrossSectionUsingYZ(Point yzP) {
		int yzX = yzP.x;
		int yzY = yzP.y;
		int yz_size = yz_image.getHeight();

		boolean slicingToUpper = false;

		int prev_pos = xy_image.getCurrentSlice();
		double[] ipp1 = GDicomTools.getImagePositionPatient(xy_image, 1);
		double[] ipp2 = GDicomTools.getImagePositionPatient(xy_image, 2);
		if (ipp1 != null && ipp2 != null) {
			// back to prev pos
			xy_image.setSlice(prev_pos);
			slicingToUpper = ipp1[2] < ipp2[2];
		}

		double az = xy_image.getNSlices() / (double) yz_image.getHeight();
		int xyZ = 0;
		if (slicingToUpper) {
			xyZ = (int) ((yz_size - yzY) * az);
		} else {
			xyZ = (int) (yzY * az);
		}
		if (xyZ < 0) {
			xyZ = 0;
		} else if (xyZ > xy_image.getNSlices() - 1) {
			xyZ = xy_image.getNSlices() - 1;
		}
		xy_prap.setImagePositionUsingSlider(xyZ);

		// update xz
		az = xz_image.getNSlices() / (double) yz_image.getWidth();
		int xzZ = (int) (yzX * az);
		if (xzZ < 0) {
			xzZ = 0;
		} else if (xzZ > xz_image.getNSlices() - 1) {
			xzZ = xz_image.getNSlices() - 1;
		}
		xz_prap.setImagePositionUsingSlider(xzZ);
		repaint();
	}

	public void updateCrossSectionViews(Praparat activePP, int offscreenX, int offscreenY) {
		if (activePP.getName().equals("XY")) {
			updateCrossSectionUsingXY(new Point(offscreenX, offscreenY));
		} else if (activePP.getName().equals("XZ")) {
			updateCrossSectionUsingXZ(new Point(offscreenX, offscreenY));
		} else if (activePP.getName().equals("YZ")) {
			updateCrossSectionUsingYZ(new Point(offscreenX, offscreenY));
		} else {
			// do nothing
		}
		repaint();
	}

	void initReferenceLine() {
		xy_prap.setShowCrossLineMode(false);
		xz_prap.setShowCrossLineMode(false);
		yz_prap.setShowCrossLineMode(false);
		xy_prap.clearCrossLines();
		xz_prap.clearCrossLines();
		yz_prap.clearCrossLines();
		updateReferenceLineMPR();
	}

	/*
	 * TODO, byte, float, RGB
	 */
//	protected void resliceAndShow() {
//		
//		if (refLines == null) {
//			return;
//		}
//		
//		Slab slab = refLines.getSlab();
//		
//		if(slab == null || slab.size() < 1) {
//			return; // or set blank image ?
//		}
//		
//		//TODO 20241126
////		boolean excessAngle = false;
////		try {
//			//do something ??
////		}
//
//		ImageStack stack = new ImageStack();
//		/*
//		 *IMPORTANT, Keep using axial for reference volume. 
//		 */
//		ImagePlus mainPlane = xyImage();
//		double min = mainPlane.getDisplayRangeMin();
//		double max = mainPlane.getDisplayRangeMax();
//		int count = 1;
//		int reconMode = reconMode();
//		List<SlicePlane> planes = slab.getSlicePlanes();
//		Slicer slicer = new Slicer(mainPlane);
//		
//		for (int i=0; i<planes.size();i++) {
//			SlicePlane plane = planes.get(i);
//			Vector3d ipp = plane.getGeometryOfSlice().getTLHC();
//			Vector3d row_v = plane.getGeometryOfSlice().getRow();
//			Vector3d col_v = plane.getGeometryOfSlice().getColumn();
//			ImageProcessor resliceIp = slicer.slice(plane, reconMode);
//			resliceIp.setMinAndMax(min, max);
//			ImagePlus temp = new ImagePlus("", resliceIp);
//			GDicomTools.setImagePositionPatient(temp, 1, ipp);
//			GDicomTools.setImageOrientationPatient(temp, 1, row_v, col_v);
//			stack.addSlice(temp.getProcessor());
//			stack.setSliceLabel(temp.getInfoProperty(), count++);
//		}
//		
//		// construct imageplus
//		recon_image = new ImagePlus("reslice", stack);
//		Calibration calHolder = mainPlane.getCalibration();
//		SlicePlane plane = planes.get(0);
//		Vector3d voxelSize = plane.getGeometryOfSlice().getVoxelSpacing();
//		double px = voxelSize.x;//voxelSize.y ? to DICOM ?
//		double py = voxelSize.y;
//		double pz = voxelSize.z;
//		calHolder.pixelWidth = px;
//		calHolder.pixelHeight = py;
//		calHolder.pixelDepth = pz;
//		recon_image.setCalibration(calHolder);
//		String seUID = UIDUtils.createUID();
//		for(int i=1; i<= recon_image.getNSlices(); i++) {
//			addUIDs(recon_image, i, seUID);
//		}
//		SwingUtilities.invokeLater(() -> {
//			recon_prap.reloadSlideGlasses(recon_image);
//		});
//	}

	protected void resliceAndShow() {
		Log.logger.info("[RESLICE_DEBUG] --- resliceAndShow Start ---");

		if (refLines == null) {
			Log.logger.warning("[RESLICE_DEBUG] refLines is null. Aborting.");
			return;
		}

		Slab slab = refLines.getSlab();

		if (slab == null || slab.size() < 1) {
			Log.logger.warning("[RESLICE_DEBUG] slab is null or empty. Aborting.");
			return; // or set blank image ?
		}

		Log.logger.info("[RESLICE_DEBUG] Slab size (number of slices): " + slab.size());

		ImageStack stack = new ImageStack();
		/*
		 * IMPORTANT, Keep using axial for reference volume.
		 */
		ImagePlus mainPlane = xyImage();
		if (mainPlane == null) {
			Log.logger.warning("[RESLICE_DEBUG] mainPlane (xyImage) is null. Aborting.");
			return;
		}

		double min = mainPlane.getDisplayRangeMin();
		double max = mainPlane.getDisplayRangeMax();
		Log.logger
				.info(String.format("[RESLICE_DEBUG] mainPlane Info: W=%d, H=%d, Slices=%d, DispMin=%.2f, DispMax=%.2f",
						mainPlane.getWidth(), mainPlane.getHeight(), mainPlane.getNSlices(), min, max));

		double[] mainIpp = GDicomTools.getImagePositionPatient(mainPlane, 1);
		if (mainIpp != null) {
			Log.logger.info(String.format("[RESLICE_DEBUG] mainPlane IPP (Slice 1): [%.2f, %.2f, %.2f]", mainIpp[0],
					mainIpp[1], mainIpp[2]));
		}

		int count = 1;
		int reconMode = reconMode();
		List<SlicePlane> planes = slab.getSlicePlanes();
		Slicer slicer = new Slicer(mainPlane);

		for (int i = 0; i < planes.size(); i++) {
			SlicePlane plane = planes.get(i);
			Vector3d ipp = plane.getGeometryOfSlice().getTLHC();
			Vector3d row_v = plane.getGeometryOfSlice().getRow();
			Vector3d col_v = plane.getGeometryOfSlice().getColumn();
			Vector3d voxelSize = plane.getGeometryOfSlice().getVoxelSpacing();
			Vector3d dim = plane.getGeometryOfSlice().getDimensions();

			Log.logger.info(String.format("[RESLICE_DEBUG] Slice %d - Geometry:", i));
			Log.logger.info(String.format("  TLHC (IPP): [%.3f, %.3f, %.3f]", ipp.x, ipp.y, ipp.z));
			Log.logger.info(String.format("  Row: [%.3f, %.3f, %.3f]", row_v.x, row_v.y, row_v.z));
			Log.logger.info(String.format("  Col: [%.3f, %.3f, %.3f]", col_v.x, col_v.y, col_v.z));
			Log.logger.info(String.format("  VoxelSpacing: [%.3f, %.3f, %.3f]", voxelSize.x, voxelSize.y, voxelSize.z));
			Log.logger.info(String.format("  Dimensions: W=%.0f, H=%.0f", dim.x, dim.y));

			ImageProcessor resliceIp = slicer.slice(plane, reconMode);

			if (resliceIp != null) {
				// ★抽出された画像の中身(ピクセル値の統計)を確認します。真っ黒なら全て0付近のはずです。
				ij.process.ImageStatistics stats = resliceIp.getStatistics();
				Log.logger
						.info(String.format("[RESLICE_DEBUG] Slice %d - Processor Stats: Min=%.2f, Max=%.2f, Mean=%.2f",
								i, stats.min, stats.max, stats.mean));

				resliceIp.setMinAndMax(min, max);
				ImagePlus temp = new ImagePlus("", resliceIp);
				GDicomTools.setImagePositionPatient(temp, 1, ipp);
				GDicomTools.setImageOrientationPatient(temp, 1, row_v, col_v);
				stack.addSlice(temp.getProcessor());
				stack.setSliceLabel(temp.getInfoProperty(), count++);
			} else {
				Log.logger.warning(String.format("[RESLICE_DEBUG] Slice %d - Slicer returned null ImageProcessor!", i));
			}
		}

		if (stack.getSize() == 0) {
			Log.logger.warning("[RESLICE_DEBUG] Resulting ImageStack is empty. Aborting reconstruction.");
			return;
		}

		// construct imageplus
		recon_image = new ImagePlus("reslice", stack);
		Calibration calHolder = mainPlane.getCalibration().copy();
		SlicePlane plane = planes.get(0);
		Vector3d voxelSize = plane.getGeometryOfSlice().getVoxelSpacing();
		double px = voxelSize.x;
		double py = voxelSize.y;
		double pz = voxelSize.z;
		calHolder.pixelWidth = px;
		calHolder.pixelHeight = py;
		calHolder.pixelDepth = pz;
		recon_image.setCalibration(calHolder);

		Log.logger.info(String.format("[RESLICE_DEBUG] recon_image created: W=%d, H=%d, Slices=%d",
				recon_image.getWidth(), recon_image.getHeight(), recon_image.getNSlices()));

		String seUID = UIDUtils.createUID();
		for (int i = 1; i <= recon_image.getNSlices(); i++) {
			addUIDs(recon_image, i, seUID);
		}
		SwingUtilities.invokeLater(() -> {
			Log.logger.info("[RESLICE_DEBUG] Reloading recon slide glasses on EDT...");
			recon_prap.reloadSlideGlasses(recon_image);
		});
	}

	double[] calcImagePositionPatient(double row, double col, int slicePos) {
		if (slicePos < 1 || slicePos > xy_image.getNSlices()) {
			logger.info("SlicePos out of range.");
			return null;
		}
		org.joml.Vector3d ipp = PlanarSupport.getNewImagePositionPatient2D(this.xy_image, col, row, slicePos);
		return new double[] { ipp.x, ipp.y, ipp.z };
	}

	void updateReferenceLineMPR() {
		if (refLines == null) {
			refLines = new ReferenceLineMPR(this);
		}
		setSliceTargetPlane(getSliceTargetPlane());
		refLines.updateResliceLineState();
	}

	public Praparat getPraparatAt(CutSurface cs) {
		if (cs == CutSurface.AXIAL) {
			return xy_prap;
		} else if (cs == CutSurface.CORONAL) {
			return xz_prap;
		} else if (cs == CutSurface.SAGITTAL) {
			return yz_prap;
		} else if (cs == CutSurface.OBLIQUE) {
			return recon_prap;
		}
		return null;
	}

	public CutSurface getSliceTargetPlane() {
		return contP.getTargetSlicePlane();
	}

	public void setSliceTargetPlane(CutSurface currentSliceTarget) {
		if (refLines != null) {
			refLines.setSliceTarget(currentSliceTarget);
		}
	}

	public CutSurface getSrcSurface() {
		return srcCutSurface;
	}

	private int reconMode() {
		String reconType = contP.getReconType();
		if (reconType.equals(SlicerControlPanel.reconType[0])) {
			return Slicer.SLICECUT;
		} else if (reconType.equals(SlicerControlPanel.reconType[1])) {
			return Slicer.MEAN;
		} else if (reconType.equals(SlicerControlPanel.reconType[2])) {
			return Slicer.MAX;
		} else if (reconType.equals(SlicerControlPanel.reconType[3])) {
			return Slicer.MIN;
		} else if (reconType.equals(SlicerControlPanel.reconType[4])) {
			return Slicer.MEDIAN;
		} else if (reconType.equals(SlicerControlPanel.reconType[5])) {
			return Slicer.MODE;
		} else {
			return Slicer.SLICECUT;
		}
	}

	void drawReferenceLines() {

		if (xz_image == null || yz_image == null) {
			return;
		}

		if (currentPrap != recon_prap) {
			if (recon_image == init_image) {
				return;
			}
			int pos = recon_prap.getCurrentSlidePos() + 1;
			recon_image.setPosition(pos);

			int posXY = xy_prap.getCurrentSlidePos() + 1;
			int posXZ = xz_prap.getCurrentSlidePos() + 1;
			int posYZ = yz_prap.getCurrentSlidePos() + 1;

			xy_image.setPosition(posXY);
			xz_image.setPosition(posXZ);
			yz_image.setPosition(posYZ);

			GeometryOfSlice srcGeometry = new GeometryOfSlice(recon_image);// want to show lines in other praps
			GeometryOfSlice targetGeometry_xy = new GeometryOfSlice(xy_image);
			GeometryOfSlice targetGeometry_xz = new GeometryOfSlice(xz_image);
			GeometryOfSlice targetGeometry_yz = new GeometryOfSlice(yz_image);

			LocalizerPoster localizerPoster_xy = new IntersectVolume(targetGeometry_xy);
			LocalizerPoster localizerPoster_xz = new IntersectVolume(targetGeometry_xz);
			LocalizerPoster localizerPoster_yz = new IntersectVolume(targetGeometry_yz);

			List<Point2D> xy_shapes = localizerPoster_xy.getOutlineOnLocalizerForThisGeometry(srcGeometry);
			List<Point2D> xz_shapes = localizerPoster_xz.getOutlineOnLocalizerForThisGeometry(srcGeometry);
			List<Point2D> yz_shapes = localizerPoster_yz.getOutlineOnLocalizerForThisGeometry(srcGeometry);

			xy_prap.getCurrentSlide().drawLocalizer(xy_shapes);
			xz_prap.getCurrentSlide().drawLocalizer(xz_shapes);
			yz_prap.getCurrentSlide().drawLocalizer(yz_shapes);
		} else {// recon prap
			// do nothing
		}
		getContentPane().repaint();
	}
}
