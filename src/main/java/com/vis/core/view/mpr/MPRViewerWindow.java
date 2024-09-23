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

package com.vis.core.view.mpr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;

import org.joml.Vector3d;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.CanvasGlass;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.IntersectVolume;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.FolderOpener;
import ij.process.ImageProcessor;

/**
 * MPR view ref:
 * https://imagej.nih.gov/ij/developer/source/ij/plugin/Orthogonal_Views.java.html
 * 
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class MPRViewerWindow extends JFrame {

	public static final int XY = 0;
	public static final int XZ = 1;
	public static final int YZ = 2;
	public static final int RECON = 3;

	public static final int CROSS_MODE = 0;
	public static final int SLICE_MODE = 1;

	final MPRViewerWindow own;

	/*
	 * https://forum.image.sc/t/rotate-line-roi-via-rotating-point-coordinates/8323
	 */
	private MPRControlPanel contP;
	private int currentViewType = 0;
	private int previousViewType = 0;

	private ImagePlus imp;// src imp, to backup.

	private ImagePlus xy_image, xz_image, yz_image, recon_image, init_image;

	Eyepiece eye = null;
	private Praparat xy_prap = null;
	private Praparat xz_prap = null;
	private Praparat yz_prap = null;
	private Praparat recon_prap = null;
	private Praparat currentPrap = null;// for mouse action

	private ReferenceLineMPR refLines;
	private CutSurface srcCutSurface;

	private final String patID;
	private final String studyUID;
	private final String refUID;
	private final String sopClassUID;
	Color studyColor = Color.DARK_GRAY;

	boolean starting = true;
	boolean standalone = false;
	public boolean crossViewMode = true;
	public boolean showCrossLine = true;

	int reconResolution = 5; // keep odd value.
	private final String reconNotReady = "NOT-READY";

	private Logger logger = Log.logger;

	public static void main(String[] args) {
		ImagePlus imp = FolderOpener.open(
				"/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151");
		new MPRViewerWindow(imp, null);
	}

	public MPRViewerWindow(Praparat prap) {
		this(prap.getImagePlus(), prap.getStudyColor());
	}

	public MPRViewerWindow(ImagePlus imp, Color studyColor) {
		own = this;
		if (imp == null || imp.getStackSize() < 1) {
			throw new IllegalArgumentException("Number of images not enough.");
		}
		this.imp = imp;
		if (studyColor != null) {
			this.studyColor = studyColor;
		}
		patID = GDicomTools.getTag(imp, "0010,0020");
		studyUID = GDicomTools.getTag(imp, "0020,000D");
		sopClassUID = GDicomTools.getTag(imp, "0008,0016");
		refUID = GDicomTools.getTag(imp, "0020,0052");
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

	public Double getSliceThickness() {
		return contP.getSliceThickness();
	}

	public Double getSliceGap() {
		return contP.getSliceGap();
	}

	public Integer getNumberOfSlices() {
		return contP.getNumberOfSlices();
	}

	public void crossViewModeOn(boolean on) {
		crossViewMode = on;
		eye.crossViewMode = on;
		if (!crossViewMode) {
			// disable crosslines
			xy_prap.clearCrossLines();
			xz_prap.clearCrossLines();
			yz_prap.clearCrossLines();
			xy_prap.setShowCrossLineMode(false);
			xz_prap.setShowCrossLineMode(false);
			yz_prap.setShowCrossLineMode(false);
			xy_prap.repaint();
			xz_prap.repaint();
			yz_prap.repaint();
		} else {
			initCrosses(showCrossLine);
		}
	}

	public void showCrossLine(boolean show) {
		showCrossLine = show;
		initCrosses(show);
	}

	private void init() {
		PlanarSupport psup = new PlanarSupport();
		srcCutSurface = psup.isPlanarOf(imp);
		// set initial loc in offscreen coords.
		initImages();
		initOrthogonals();// create ortho images
		buildGUI();
		initCrosses(showCrossLine);
		revalidate();
		setVisible(true);
	}

	private void buildGUI() {
		if (xz_image == null || yz_image == null) {
			logger.log(Level.WARNING, "Does not ready to start MPR window.");
			return;
		}
		// init view
		setTitle(ConfigInfo.MPRWindow.toString());
		setName(ConfigInfo.MPRWindow.toString());
		setSize(new Dimension(1000, 800));

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
		MPRMenuBar bar = new MPRMenuBar(this);
		setJMenuBar(bar);
		// control panel
		contP = new MPRControlPanel(this, imp.getCalibration().pixelDepth);
		add(contP, BorderLayout.NORTH);
		contP.setPreferredSize(new Dimension(getWidth(), 40));

		xy_prap = new Praparat(xy_image, studyColor, ViewMode.MPR);
		xz_prap = new Praparat(xz_image, studyColor, ViewMode.MPR);
		yz_prap = new Praparat(yz_image, studyColor, ViewMode.MPR);
		recon_prap = new Praparat(recon_image, studyColor, ViewMode.Normal);

		xy_prap.setName("XY");
		xz_prap.setName("XZ");
		yz_prap.setName("YZ");

		eye.addPraparat(xy_prap);
		eye.addPraparat(xz_prap);
		eye.addPraparat(yz_prap);
		eye.addPraparat(recon_prap);

		eye.crossViewMode = this.crossViewMode;

		new Thread(new Runnable() {
			@Override
			public void run() {
				xy_prap.setImagePositionUsingSlider(xy_image.getNSlices() / 2 - 1);
				xz_prap.setImagePositionUsingSlider(xz_image.getNSlices() / 2 - 1);
				yz_prap.setImagePositionUsingSlider(yz_image.getNSlices() / 2 - 1);
			}
		}).start();
		eye.autoLayout();// 2 * 2 grid layout
		setLocationRelativeTo(null);
	}

	/*
	 * 0:ortho 1:reslice
	 */
	public void setCurrentViewType(int viewType) {
		this.previousViewType = this.currentViewType;
		this.currentViewType = viewType;
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
		GDicomTools.setTag(imp, 1, "0008,0016", sopClassUID);
		GDicomTools.setTag(imp, 1, "0010,0020", patID);
		GDicomTools.setTag(imp, 1, "0020,000D", studyUID);
		GDicomTools.setTag(imp, 1, "0020,000E", seriesUID);
		GDicomTools.setTag(imp, 1, "0020,0052", refUID);
		// SOPInstUID
		GDicomTools.setTag(imp, 1, "0008,0018", UIDUtils.createUID());
	}

	/**
	 * Calculate reconstruct stack size from src.
	 * 
	 * @param src
	 * @param cutSurface
	 * @return
	 */
	private int[] calculateOrthogonalImageSize(ImagePlus src, CutSurface srcCutSurface, CutSurface targetCutSurface) {

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
		if (srcCutSurface == CutSurface.AXIAL) {
			xy_image = new Duplicator().run(imp);
			xz_image = new ImagePlus();
			yz_image = new ImagePlus();
		} else if (srcCutSurface == CutSurface.CORONAL) {
			xz_image = new Duplicator().run(imp);
			xy_image = new ImagePlus();
			yz_image = new ImagePlus();
		} else {
			yz_image = new Duplicator().run(imp);
			xy_image = new ImagePlus();
			xz_image = new ImagePlus();
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

	void initOrthogonals() {
		if (srcCutSurface == CutSurface.AXIAL) {
			xz_image = constructXZ(xy_image);
			yz_image = constructYZ(xy_image);
		} else if (srcCutSurface == CutSurface.CORONAL) {
			xy_image = constructXY(xz_image);
			yz_image = constructYZ(xz_image);
		} else {
			xy_image = constructXY(yz_image);
			xz_image = constructXZ(yz_image);
		}
	}

	ImagePlus constructXY(ImagePlus src) {
		if (srcCutSurface == CutSurface.AXIAL) {
			xy_image = src;
			return xy_image;
		}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		ImageStack stack = new ImageStack();
		Calibration cal = null;
		if (srcCutSurface == CutSurface.CORONAL) {
			int size = src.getHeight();
			boolean flipXZ = false;
			boolean rotateXZ = false;
			String seriesUID = UIDUtils.createUID();
			for (int z = 0; z < size; z++) {
				ImagePlus xy_ = orthTool.cutXZ(src, z, 1, flipXZ, rotateXZ);
				if (cal == null) {
					cal = xy_.getCalibration();
				}
				addUIDs(xy_, 1, seriesUID);
				stack.addSlice(xy_.getProcessor());
				stack.setSliceLabel(xy_.getInfoProperty(), z + 1);
			}
		} else {// SAG
			int size = src.getWidth();
			boolean flipXZ = false;
			boolean rotateXZ = true;
			String seriesUID = UIDUtils.createUID();
			for (int z = 0; z < size; z++) {
				ImagePlus xy_ = orthTool.cutXZ(src, z, 1, flipXZ, rotateXZ);
				if (cal == null) {
					cal = xy_.getCalibration();
				}
				addUIDs(xy_, 1, seriesUID);
				stack.addSlice(xy_.getProcessor());
				stack.setSliceLabel(xy_.getInfoProperty(), z + 1);
			}
		}
		orthTool = null;
		Calibration calHolder = this.imp.getCalibration().copy();// with density calibration
		calHolder.pixelWidth = cal.pixelWidth;
		calHolder.pixelHeight = cal.pixelHeight;
		calHolder.pixelDepth = cal.pixelDepth;
		calHolder.setXUnit(cal.getXUnit());
		calHolder.setYUnit(cal.getYUnit());
		calHolder.setZUnit(cal.getZUnit());
		ImagePlus xy_imp = new ImagePlus("XY", stack);
		xy_imp.setCalibration(calHolder);
		return xy_imp;
	}

	ImagePlus constructXZ(ImagePlus src) {
		if (srcCutSurface == CutSurface.CORONAL) {
			xz_image = src;
			return xz_image;
		}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		ImageStack stack = new ImageStack();
		Calibration cal = null;
		if (srcCutSurface == CutSurface.AXIAL) {
			boolean flipXZ = true;
			boolean rotateXZ = false;
			String seriesUID = UIDUtils.createUID();
			int size = src.getHeight();
			for (int y = 0; y < size; y++) {
				ImagePlus xz_ = orthTool.cutXZ(src, y, 1, flipXZ, rotateXZ);
				if (cal == null) {
					cal = xz_.getCalibration();
				}
				addUIDs(xz_, 1, seriesUID);
				stack.addSlice(xz_.getProcessor());
				stack.setSliceLabel(xz_.getInfoProperty(), y + 1);
			}
		} else {
			boolean flipYZ = false;
			boolean rotateYZ = true;
			String seriesUID = UIDUtils.createUID();
			int size = src.getWidth();
			for (int w = 0; w < size; w++) {
				ImagePlus xz_ = orthTool.cutYZ(src, w, 1, flipYZ, rotateYZ);
				if (cal == null) {
					cal = xz_.getCalibration();
				}
				addUIDs(xz_, 1, seriesUID);
				stack.addSlice(xz_.getProcessor());
				stack.setSliceLabel(xz_.getInfoProperty(), w + 1);
			}
		}

		orthTool = null;
		Calibration calHolder = this.imp.getCalibration().copy();// with density calibration
//		calHolder.setCTable(calHolder.getCTable(), null);
		calHolder.pixelWidth = cal.pixelWidth;
		calHolder.pixelHeight = cal.pixelHeight;
		calHolder.pixelDepth = cal.pixelDepth;
		calHolder.setXUnit(cal.getXUnit());
		calHolder.setYUnit(cal.getYUnit());
		calHolder.setZUnit(cal.getZUnit());

		ImagePlus xz_imp = new ImagePlus("XZ", stack);
		xz_imp.setCalibration(calHolder);
		if (src.getProcessor().isSigned16Bit()) {
			xz_imp.getCalibration().setSigned16BitCalibration();
		}
		return xz_imp;
	}

	ImagePlus constructYZ(ImagePlus src) {
		if (srcCutSurface == CutSurface.SAGITTAL) {
			yz_image = src;
			return yz_image;
		}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		ImageStack stack = new ImageStack();
		Calibration cal = null;
		if (srcCutSurface == CutSurface.AXIAL) {
			boolean flipYZ = false;
			boolean rotateYZ = false;
			String seriesUID = UIDUtils.createUID();
			int size = src.getHeight();
			for (int w = 0; w < size; w++) {
				ImagePlus yz_ = orthTool.cutYZ(src, w, 1, flipYZ, rotateYZ);
				if (cal == null) {
					cal = yz_.getCalibration();
				}
				addUIDs(yz_, 1, seriesUID);
				stack.addSlice(yz_.getProcessor());
				stack.setSliceLabel(yz_.getInfoProperty(), w + 1);
			}
		} else {// XZ
			boolean flipYZ = true;
			boolean rotateYZ = true;
			String seriesUID = UIDUtils.createUID();
			int size = src.getWidth();
			for (int w = 0; w < size; w++) {
				ImagePlus yz_ = orthTool.cutYZ(src, w, 1, flipYZ, rotateYZ);
				if (cal == null) {
					cal = yz_.getCalibration();
				}
				addUIDs(yz_, 1, seriesUID);
				stack.addSlice(yz_.getProcessor());
				stack.setSliceLabel(yz_.getInfoProperty(), w + 1);
			}
		}
		orthTool = null;
		Calibration calHolder = this.imp.getCalibration().copy();// with density calibration
		calHolder.pixelWidth = cal.pixelWidth;
		calHolder.pixelHeight = cal.pixelHeight;
		calHolder.pixelDepth = cal.pixelDepth;
		calHolder.setXUnit(cal.getXUnit());
		calHolder.setYUnit(cal.getYUnit());
		calHolder.setZUnit(cal.getZUnit());
		ImagePlus yz_imp = new ImagePlus("YZ", stack);
		yz_imp.setCalibration(calHolder);
		return yz_imp;
	}

	/**
	 * 
	 * @param p : offscreen x and y.
	 */
	@SuppressWarnings("unused")
	private void updateCrossSectionUsingXY(Point xyP) {
		if (xy_prap == null) {
			return;
		}
		int xyX = xyP.x;
		int xyY = xyP.y;
		int xyZ = xy_prap.getCurrentSlidePos();
		/*
		 * Since it is not an ISO voxel, the position of the cross-section is
		 * recalculated from each voxel size.
		 */
		// update xz
		Calibration cal_xy = xy_image.getCalibration();
		double xy_px = cal_xy.pixelWidth;
		double xy_py = cal_xy.pixelHeight;
		double xy_pz = cal_xy.pixelDepth;
		int xzX = xyX;
		int xzY = (int) (xyZ * (xy_pz / xy_px));// xz_size - xzZ;
		int xzZ = (int) (xyY * (xy_py / xy_px));
		if (xzZ < 0) {
			xzZ = 0;
		} else if (xzZ > xz_image.getNSlices() - 1) {
			xyZ = xz_image.getNSlices() - 1;
		}
		xz_prap.setImagePositionUsingSlider(xzZ);

		// update yz
		int yzX = (int) (xyY * (xy_py / xy_px));
		int yzY = (int) (xyZ * (xy_pz / xy_px));
		int yzZ = xyX;
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
		int xz_size = xz_image.getHeight();// xz_prap.getNumberOfImages();

		// update xy
		Calibration cal_xy = xy_image.getCalibration();
		double xy_px = cal_xy.pixelWidth;
		double xy_py = cal_xy.pixelHeight;
		double xy_pz = cal_xy.pixelDepth;
		int xyX = xzX;
		int xyY = (int) (xzZ / (xy_py / xy_px));// xz_size - xzZ;
		int xyZ = (int) ((xz_size - xzY) / (xy_pz / xy_px));
		if (xyZ < 0) {
			xyZ = 0;
		} else if (xyZ > xy_image.getNSlices() - 1) {
			xyZ = xy_image.getNSlices() - 1;
		}
		xy_prap.setImagePositionUsingSlider(xyZ);

		// update yz
		int yzX = (int) (xyY * (xy_py / xy_px));
		int yzY = (int) (xyZ * (xy_pz / xy_px));
		int yzZ = xyX;
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
	@SuppressWarnings("unused")
	private void updateCrossSectionUsingYZ(Point yzP) {
		int yzX = yzP.x;
		int yzY = yzP.y;
		int yzZ = yz_prap.getCurrentSlidePos();
		int yz_size = yz_image.getHeight();

		// update xy
		Calibration cal_xy = xy_image.getCalibration();
		double xy_px = cal_xy.pixelWidth;
		double xy_py = cal_xy.pixelHeight;
		double xy_pz = cal_xy.pixelDepth;
		int xyX = (int) (yzZ / (xy_py / xy_px));
		int xyY = yzX;
		int xyZ = (int) ((yz_size - yzY) / (xy_pz / xy_px));
		if (xyZ < 0) {
			xyZ = 0;
		} else if (xyZ > xy_image.getNSlices() - 1) {
			xyZ = xy_image.getNSlices() - 1;
		}
		xy_prap.setImagePositionUsingSlider(xyZ);

		// update xz
		int xzX = xyX;
		int xzY = (int) (xyZ * (xy_pz / xy_px));
		int xzZ = (int) (xyY * (xy_py / xy_px));
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

	void initCrosses(boolean showCrossLine) {
		refLines = null;
		// set cross line mode for praps
		xy_prap.setShowCrossLineMode(showCrossLine);
		xz_prap.setShowCrossLineMode(showCrossLine);
		yz_prap.setShowCrossLineMode(showCrossLine);
		xy_prap.clearCrossLines();
		xz_prap.clearCrossLines();
		yz_prap.clearCrossLines();
		xy_prap.setReferenceLine(null);
		xz_prap.setReferenceLine(null);
		yz_prap.setReferenceLine(null);
	}

	void initReslice() {
		xy_prap.setShowCrossLineMode(false);
		xz_prap.setShowCrossLineMode(false);
		yz_prap.setShowCrossLineMode(false);
		xy_prap.clearCrossLines();
		xz_prap.clearCrossLines();
		yz_prap.clearCrossLines();
		updateReferenceLineMPR();
		repaint();
	}

	/*
	 * TODO, byte, float, RGB
	 */
	protected void resliceAndShow() {
		/*
		 * get sorted coordinates in general path get num of slice iterate dynamic
		 * reslice show imp // debug
		 */
		if (refLines == null) {
			return;
		}
		boolean excessAngle = false;
		try {
			if (srcCutSurface == CutSurface.AXIAL) {
				excessAngle = refLines.xYLine().isHorizontal() ? false : true;
			} else if (srcCutSurface == CutSurface.CORONAL) {
				excessAngle = refLines.xZLine().isHorizontal();
			} else {
				excessAngle = refLines.yZLine().isHorizontal() ? false : true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		ArrayList<float[]> sortedPointPairList = null;
		if (srcCutSurface == CutSurface.AXIAL) {
			GeneralPath sliceLinePaths = refLines.xYLine().createSliceLinesWithOffScreenCoordinates();
			sortedPointPairList = refLines.xYLine().getPoints(sliceLinePaths);
		} else if (srcCutSurface == CutSurface.CORONAL) {
			GeneralPath sliceLinePaths = refLines.xZLine().createSliceLinesWithOffScreenCoordinates();
			sortedPointPairList = refLines.xZLine().getPoints(sliceLinePaths);
		} else {
			GeneralPath sliceLinePaths = refLines.yZLine().createSliceLinesWithOffScreenCoordinates();
			sortedPointPairList = refLines.yZLine().getPoints(sliceLinePaths);
		}

		ImageStack stack = null;
		ImagePlus mainPlane = this.imp;
		int count = 1;
		String reconType = contP.getReconType();
		PlanarSupport psup = new PlanarSupport();
		if (reconType.equals("SLICECUT")) {
			for (int i = 0; i < sortedPointPairList.size(); i += 2) {
				double cx1 = sortedPointPairList.get(i)[0];
				double cy1 = sortedPointPairList.get(i)[1];
				double cx2 = sortedPointPairList.get(i + 1)[0];
				double cy2 = sortedPointPairList.get(i + 1)[1];
				ImageProcessor resliceIp = Slicer.slice(mainPlane, excessAngle, cx1, cy1, cx2, cy2);
				if (stack == null) {
					stack = new ImageStack(resliceIp.getWidth(), resliceIp.getHeight(), sortedPointPairList.size() / 2);
				}
				resliceIp.resetMinAndMax();

				if (srcCutSurface == CutSurface.AXIAL) {// creating CORONAL or sagittal
					Vector3d ipp_v = psup.getNewImagePositionPatient2D(mainPlane, cx1, cy1, mainPlane.getNSlices());
					double[] iop = psup.rotateOrthogonallyImageOrientationPatient(mainPlane, CutSurface.AXIAL);
					int angle = (int) refLines.getAngleXY();
					Vector3d row = new Vector3d(iop[0], iop[1], iop[2]);// direction cosine
					Vector3d col = new Vector3d(iop[3], iop[4], iop[5]);// direction cosine
					/*
					 * The axis of rotation does not move, but rotates the other axis. e.g., rotateZ
					 * rotate only x and y.
					 */
					/*
					 * row direction cosine : ---> vector col direction cosine : | | | \/ vector
					 */
					if (!excessAngle) {// COR
						row = row.rotateZ(Math.toRadians(-angle));// angle near to horizontal (near 0 or +-180)
						col = col.rotateX(Math.toRadians(-90));
					} else {// SAG
						row = row.rotateZ(Math.toRadians(-angle));// angle near vertical (near +-90).
						col = col.rotateX(Math.toRadians(-90));
					}

					// System.out.println(row.distanceSquared(cx2, mainPlaneType, count));
					iop = new double[] { row.x, row.y, row.z, col.x, col.y, col.z };

					if (Math.abs(row.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Row not a unit vector");
					}
					if (Math.abs(col.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Column not a unit vector");
					}
					if (row.dot(col) > 0.005) { // dot product should be cos(90)=0 if orthogonal
						throw new IllegalArgumentException("Row and column vectors are not orthogonal");
					}

					ImagePlus temp = new ImagePlus();
					GDicomTools.setImagePositionPatient(temp, 1, ipp_v);
					GDicomTools.setImageOrientationPatient(temp, 1, iop);
					stack.setProcessor(resliceIp, count);
					stack.setSliceLabel(temp.getInfoProperty(), count);// stack.addSlice(temp.getInfoProperty(),
																		// resliceIp, count++);//do not use
					count++;
				} else if (srcCutSurface == CutSurface.CORONAL) {// creating sagittal or axial
					Vector3d ipp_v = psup.getNewImagePositionPatient2D(mainPlane, cx1, cy1, 1);
					double[] iop_sag = psup.rotateOrthogonallyImageOrientationPatient(mainPlane, CutSurface.CORONAL);
					int angle = (int) refLines.getAngleXZ();
					Vector3d row = new Vector3d(iop_sag[0], iop_sag[1], iop_sag[2]);
					Vector3d col = new Vector3d(iop_sag[3], iop_sag[4], iop_sag[5]);

					if (excessAngle) {// AXI
						row = row.rotateY(Math.toRadians(-angle));
						col = col.rotateX(Math.toRadians(90));
					} else {// SAG
						row = row.rotateZ(Math.toRadians(90));
						col = col.rotateY(Math.toRadians(-angle - 90));
					}

					// System.out.println(row.distanceSquared(cx2, mainPlaneType, count));
					double[] iop = new double[] { row.x, row.y, row.z, col.x, col.y, col.z };

					if (Math.abs(row.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Row not a unit vector");
					}
					if (Math.abs(col.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Column not a unit vector");
					}
					if (row.dot(col) > 0.005) { // dot product should be cos(90)=0 if orthogonal between row direction
												// cosine and col direction cosine.
						throw new IllegalArgumentException("Row and column vectors are not orthogonal:" + row.dot(col));
					}

					ImagePlus temp = new ImagePlus();
					GDicomTools.setImagePositionPatient(temp, 1, ipp_v);
					GDicomTools.setImageOrientationPatient(temp, 1, iop);
					stack.setProcessor(resliceIp, count);
					stack.setSliceLabel(temp.getInfoProperty(), count);// stack.addSlice(temp.getInfoProperty(),
																		// resliceIp, count++);//do not use
					count++;
				} else {// creating coronal or axial
					Vector3d ipp_v = psup.getNewImagePositionPatient2D(mainPlane, cx1, cy1, 1);
					double[] iop_sag = psup.rotateOrthogonallyImageOrientationPatient(mainPlane, CutSurface.SAGITTAL);
					int angle = (int) refLines.getAngleYZ();
					Vector3d row = new Vector3d(iop_sag[0], iop_sag[1], iop_sag[2]);
					Vector3d col = new Vector3d(iop_sag[3], iop_sag[4], iop_sag[5]);

					if (!excessAngle) {// AXI
						row = row.rotateZ(Math.toRadians(-90));
						col = col.rotateX(Math.toRadians(angle + 90));
					} else {// COR
						row = row.rotateZ(Math.toRadians(-90));
						col = col.rotateX(Math.toRadians(angle + 90));
					}

					// System.out.println(row.distanceSquared(cx2, mainPlaneType, count));
					double[] iop = new double[] { row.x, row.y, row.z, col.x, col.y, col.z };

					if (Math.abs(row.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Row not a unit vector");
					}
					if (Math.abs(col.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Column not a unit vector");
					}
					if (row.dot(col) > 0.005) { // dot product should be cos(90)=0 if orthogonal
						double res = row.dot(col);
						throw new IllegalArgumentException("Row and column vectors are not orthogonal:" + res);
					}

					ImagePlus temp = new ImagePlus();
					GDicomTools.setImagePositionPatient(temp, 1, ipp_v);
					GDicomTools.setImageOrientationPatient(temp, 1, iop);
					stack.setProcessor(resliceIp, count);
					stack.setSliceLabel(temp.getInfoProperty(), count);// stack.addSlice(temp.getInfoProperty(),
																		// resliceIp, count++);//do not use
					count++;
				}
			}
		} else if (reconType.equals("MEAN")) {
//			for(int i=0;i<sortedPointPairList.size();i+=2) {
//				double cx1 = sortedPointPairList.get(i)[0];
//				double cy1 = sortedPointPairList.get(i)[1];
//				double cx2 = sortedPointPairList.get(i+1)[0];
//				double cy2 = sortedPointPairList.get(i+1)[1];
//				double thicknessInPixel = refLines.xYLine().getDepthSpacingInPixel();
//				//center slicecut
//				ImageProcessor resliceIp = slicer.getSlice(mainPlane, new ij.gui.Line(cx1,cy1,cx2,cy2));
//				ImagePlus resliceImpFloat = new ImagePlus("",resliceIp.convertToFloat());
//				
//				/*
//				 * e.g, 5 reconResolution(resolution is must be odd number).
//				 * shift amount = thickness / (reconResolution-1)
//				 * num of bar equals reconResolution, spaces is shift.
//				 * |<-|<-|->|->|
//				 */
//				double subPixelShift = thicknessInPixel/(reconResolution-1.0d);
//				//sub slices
//				if(isXZ) {//cor, shift on y
//					int iterEnd = (reconResolution-1)/2;
//					for(int m=1;m<=iterEnd;m++) {
//						ImageProcessor subIpU = slicer.getSlice(mainPlane, new ij.gui.Line(cx1,cy1+subPixelShift*m,cx2,cy2+subPixelShift*m));
//						ImageProcessor subIpL = slicer.getSlice(mainPlane, new ij.gui.Line(cx1,cy1-subPixelShift*m,cx2,cy2-subPixelShift*m));
//						//add these slices
//						ImagePlus impU = new ImagePlus("", subIpU.convertToFloat());
//						ImagePlus impL = new ImagePlus("", subIpL.convertToFloat());
//						resliceImpFloat = new ImageCalculator().run("add create float", resliceImpFloat, impU);
//						resliceImpFloat = new ImageCalculator().run("add create float", resliceImpFloat, impL);
//					}
//				}else {//sag, shift on x
//					int iterEnd = (reconResolution-1)/2;
//					for(int m=1;m<=iterEnd;m++) {
//						ImageProcessor subIpL = slicer.getSlice(mainPlane, new ij.gui.Line(cx1+subPixelShift*m,cy1,cx2+subPixelShift*m,cy2));
//						ImageProcessor subIpR = slicer.getSlice(mainPlane, new ij.gui.Line(cx1-subPixelShift*m,cy1,cx2-subPixelShift*m,cy2));
//						//add these slices
//						ImagePlus impL = new ImagePlus("", subIpL.convertToFloat());
//						ImagePlus impR = new ImagePlus("", subIpR.convertToFloat());
//						resliceImpFloat = new ImageCalculator().run("add create float", resliceImpFloat, impL);
//						resliceImpFloat = new ImageCalculator().run("add create float", resliceImpFloat, impR);
//					}
//				}
//				resliceImpFloat.getProcessor().multiply(1.0/reconResolution);
//				if(mainPlane.getBitDepth() == 8) {
//					resliceImpFloat.setDisplayRange(0, 255);
//					resliceImpFloat = new ImagePlus("", resliceImpFloat.getProcessor().convertToByteProcessor());
//				}else if(mainPlane.getBitDepth() == 16) {
//					resliceImpFloat.setDisplayRange(0, 65536);
//					resliceImpFloat = new ImagePlus("", resliceImpFloat.getProcessor().convertToShortProcessor());
//				}
//				resliceImpFloat.resetDisplayRange();
//				resliceIp = resliceImpFloat.getProcessor();
//				if(stack == null) {
//					stack = new ImageStack(resliceIp.getWidth(),resliceIp.getHeight(), sortedPointPairList.size()/2);
//				}
//				resliceIp.resetMinAndMax();
//				stack.setProcessor(resliceIp, count++);
//			}
		} else {
			// do other algorithms
		}
		// construct imageplus
		recon_image = new ImagePlus("reslice", stack);
		double px;
		double py;
		double pz;
		// set calibration
		if (srcCutSurface == CutSurface.AXIAL) {
			// SAG or COR
			Calibration calHolder = xy_image.getCalibration().copy();
			px = refLines.xYLine().getLength() / recon_image.getWidth();
			/*
			 * sag/cor images height set to [num slice * (pz/px)] therefore, pz is to be px.
			 */
			py = calHolder.pixelWidth;
			pz = contP.getSliceThickness() + contP.getSliceGap();
			calHolder.pixelWidth = px;
			calHolder.pixelHeight = py;
			calHolder.pixelDepth = pz;
			recon_image.setCalibration(calHolder);
		} else if (srcCutSurface == CutSurface.CORONAL) {
			Calibration calHolder = xz_image.getCalibration().copy();
			if (excessAngle) {// AXI
				px = refLines.xZLine().getLength() / recon_image.getWidth();
				py = calHolder.pixelHeight;
			} else {// SAG
				px = calHolder.pixelHeight;
				py = refLines.xZLine().getLength() / recon_image.getHeight();
			}
			pz = contP.getSliceThickness() + contP.getSliceGap();
			calHolder.pixelWidth = px;
			calHolder.pixelHeight = py;
			calHolder.pixelDepth = pz;
			recon_image.setCalibration(calHolder);
		} else {// YZ
			Calibration calHolder = yz_image.getCalibration().copy();
			if (!excessAngle) {// AXI
				px = calHolder.pixelHeight;
				py = refLines.yZLine().getLength() / recon_image.getHeight();
			} else {// COR
				px = calHolder.pixelDepth;
				py = refLines.yZLine().getLength() / recon_image.getHeight();
			}
			pz = contP.getSliceThickness() + contP.getSliceGap();
			calHolder.pixelWidth = px;
			calHolder.pixelHeight = py;
			calHolder.pixelDepth = pz;
			recon_image.setCalibration(calHolder);
		}
		recon_prap.prepareSlideGlassesUsingImagePlus(recon_image);
		recon_prap.resetView();
	}

	double[] calcImagePositionPatient(double row, double col, int slicePos) {
		PlanarSupport psup = new PlanarSupport();
		if (slicePos < 1) {
			logger.info("SlicePos must be equal or than 1.");
			return null;
		}
		org.joml.Vector3d ipp = psup.getNewImagePositionPatient2D(this.xy_image, col, row, slicePos);
		return new double[] { ipp.x, ipp.y, ipp.z };
	}

	/**
	 * row and col direction, it is easy to get confused. When images are axial
	 * plane, row direction cosine means X direction in RCS. col direction cosine
	 * means Y direction in RCS.
	 * 
	 * @param row_rotateX in degree
	 * @param row_rotateY in degree
	 * @param row_rotateZ in degree
	 * @param col_rotateX in degree
	 * @param col_rotateY in degree
	 * @param col_rotateZ in degree
	 * @return
	 */
	double[] calcImageOrientationPatient(int row_rotateX, int row_rotateY, int row_rotateZ, int col_rotateX,
			int col_rotateY, int col_rotateZ) {
		PlanarSupport psup = new PlanarSupport();
		double[] iop = psup.getNewImageOrientationPatient(this.xy_image, row_rotateX, row_rotateY, row_rotateZ,
				col_rotateX, col_rotateY, col_rotateZ);
		return iop;// nullable
	}

//	void updateResliceLineState() {
//		if(sliceLine == null) {
//			//keep AXIAL, Any surface of this.imp shall be treated as an XY surface.
//			sliceLine = new ReferenceLine(CutSurface.AXIAL, 0, imp.getHeight()/2, imp.getWidth()-1, imp.getHeight()/2, xy_prap.getCurrentSlide().getView());
//			xy_prap.setReferenceLine(sliceLine);
//			xy_prap.getCurrentSlide().getView().setViewer2DToolType(Viewer2DToolBar.LineRoi);
//		}
//	    sliceLine.setThickness(contP.getSliceThickness());
//	    sliceLine.setGap(contP.getSliceGap());
//	    sliceLine.setNumOfSlice(contP.getNumberOfSlices());
//	    sliceLine.createSliceLinesWithOffScreenCoordinates();
//		xy_prap.repaint();
//	}

	void updateReferenceLineMPR() {
		if (refLines == null) {
			refLines = new ReferenceLineMPR(this);
		}
		refLines.updateResliceLineState();
	}

	/**
	 * @return xy, xz, yz, recon array.
	 */
//	public ImagePlus[] getMPRImages() {
//		ImagePlus xy = null;
//		ImagePlus xz = null;
//		ImagePlus yz = null;
//		ImagePlus recon = null;
//		if(xy_prap != null) {
//			xy = xy_prap.getStackSeries();
//		}
//		if(xz_prap != null) {
//			xz = xz_prap.getStackSeries();
//		}
//		if(yz_prap != null) {
//			yz = yz_prap.getStackSeries();
//		}
//		if(recon_image != null && !recon_image.getTitle().equals("NOT-READY")) {
//			recon = recon_image;
//		}
//		ImagePlus[] mprs = new ImagePlus[] {xy, xz, yz, recon};
//		return mprs;
//	}

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

	public CutSurface getSrcSurface() {
		return srcCutSurface;
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

		}
		getContentPane().repaint();
	}

	public void updateState(int newViewType) {
		setCurrentViewType(newViewType);
		if (newViewType == previousViewType) {
			return;
		}
		if (currentViewType == MPRViewerWindow.CROSS_MODE) {
			initCrosses(showCrossLine);
		} else if (currentViewType == MPRViewerWindow.SLICE_MODE) {
			initReslice();
		} else if (currentViewType == 2) {

		}
		revalidate();
		repaint();
	}

}
