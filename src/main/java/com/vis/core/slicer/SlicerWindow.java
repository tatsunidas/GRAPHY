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

	//praparat position
	public static final int XY = 0;
	public static final int XZ = 1;
	public static final int YZ = 2;
	public static final int RECON = 3;

	public static final int CROSS_MODE = 0;//ORTHO
	public static final int SLICE_MODE = 1;//RESLICE

	final MPRViewerWindow own;

	/*
	 * https://forum.image.sc/t/rotate-line-roi-via-rotating-point-coordinates/8323
	 */
	private MPRControlPanel contP;
	private int currentViewType = CROSS_MODE;

	private ImagePlus imp;// src imp, to backup.

	private ImagePlus xy_image, xz_image, yz_image, recon_image, init_image;

	Eyepiece eye = null;
	private Praparat xy_prap = null;
	private Praparat xz_prap = null;
	private Praparat yz_prap = null;
	private Praparat recon_prap = null;
	private Praparat currentPrap = null;// for mouse action

	private ReferenceLineMPR refLines;
	private CutSurface srcCutSurface;/*original image plane*/

	private final String patID;
	private final String studyUID;
	private final String refUID;
	private final String sopClassUID;
	Color studyColor = Color.DARK_GRAY;

	boolean starting = true;
	boolean standalone = false;
	public boolean crossViewMode = true;
	public boolean showCrossLine = false;

	int reconResolution = 5; // keep odd value.
	private final String reconNotReady = "NOT-READY";

	private Logger logger = Log.logger;

	/*
	 * debug
	 */
	public static void main(String[] args) {
		//axi src
		ImagePlus ax = FolderOpener.open(
				"/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151");
		
//		ImagePlus ax = FolderOpener.open("/home/tatsunidas/デスクトップ/LUNG1-246");
		new MPRViewerWindow(ax, null);
		
		//cor src
//		String corDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/T1COR";
//		ImagePlus xz = FolderOpener.open(corDir);
//		new MPRViewerWindow(xz, null);
		
		//sag src
//		String sagDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";
//		ImagePlus yz = FolderOpener.open(sagDir);
//		new MPRViewerWindow(yz, null);
		
		//other
//		String otherDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/HASSAKU_3DT1 GEIR/";
//		ImagePlus o = FolderOpener.open(otherDir);
//		new MPRViewerWindow(o, null);
	}

	/**
	 * See also, D3.ui.GatryTiltCorrector
	 * @param prap
	 */
	public MPRViewerWindow(Praparat prap) {
		this(prap.getImagePlus(), prap.getStudyColor());
	}

	public MPRViewerWindow(ImagePlus imp, Color studyColor) {
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
		if(m == Modality.CT) {
			GantryTiltCorrector gtc = new GantryTiltCorrector();
			double tiltAngle = GDicomTools.getDouble(imp, 1, "0018,1120"/*Gantry/Detector Tilt*/);
			double pixelSpacingY = imp.getCalibration().pixelHeight;
			double sliceSpacing = GDicomTools.getVoxelDepth(imp);
			double reconSliceSpacing = sliceSpacing < 1d ? sliceSpacing:1d;
			imp = gtc.correctVolume3D(imp, tiltAngle, pixelSpacingY, sliceSpacing, reconSliceSpacing);
		}
		own = this;
		this.imp = imp;
		if (studyColor != null) {
			this.studyColor = studyColor;
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
	
	public int getCurrentViewType() {
		return currentViewType;
	}
	
	public ImagePlus getSrcImage() {
		return imp;
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
		srcCutSurface = ImageOrientation.getCutSurface(imp);
		initImages();// create orthogonal images
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
		MPRMenuBar bar = new MPRMenuBar(this);
		setJMenuBar(bar);
		// control panel
		contP = new MPRControlPanel(this);
		add(contP, BorderLayout.NORTH);
		contP.setPreferredSize(new Dimension(getWidth(), 40));

		xy_prap = new Praparat(xy_image, studyColor, ViewMode.MPR);
		xz_prap = new Praparat(xz_image, studyColor, ViewMode.MPR);
		yz_prap = new Praparat(yz_image, studyColor, ViewMode.MPR);
		recon_prap = new Praparat(recon_image, studyColor, ViewMode.Normal);

		xy_prap.setName("XY");//IMPORTANT
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
				recon_prap.setImagePositionUsingSlider(0);
			}
		}).start();
		eye.autoLayout();// 2 * 2 grid layout
		setLocationRelativeTo(null);
	}

	/*
	 * 0:ortho 1:reslice
	 */
	public void setCurrentViewType(int viewType) {
		this.currentViewType = viewType;
	}
	
	ImagePlus getSliceTargetImage(CutSurface axis) {
		if(axis == CutSurface.AXIAL) {
			return xyImage();
		}else if(axis == CutSurface.CORONAL) {
			return xzImage();
		}else if(axis == CutSurface.SAGITTAL) {
			return yzImage();
		}else {
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
		if (srcCutSurface == CutSurface.AXIAL) {
			xy_image = new Duplicator().run(imp);
			//see also, com.vis.core.util.ImageUtils.sort(xy_image, true/*reverse order*/, ImageUtils.SORT_BY_Z);
			xy_image = PlanarSupport.loadAndSortDicomImages(xy_image);
			Calibration cal = imp.getCalibration();
			cal.pixelDepth = GDicomTools.getVoxelDepth(imp);
			xy_image.setCalibration(cal);
			xz_image = constructXZ(xy_image);
			yz_image = constructYZ(xy_image);
		} else if (srcCutSurface == CutSurface.CORONAL) {
			xz_image = new Duplicator().run(imp);
			Calibration cal = imp.getCalibration();
			cal.pixelDepth = GDicomTools.getVoxelDepth(imp);
			xz_image.setCalibration(cal);
			xy_image = constructXY(xz_image);
			yz_image = constructYZ(xy_image);
		} else if (srcCutSurface == CutSurface.SAGITTAL){
			yz_image = new Duplicator().run(imp);
			Calibration cal = imp.getCalibration();
			cal.pixelDepth = GDicomTools.getVoxelDepth(imp);
			yz_image.setCalibration(cal);
			xy_image = constructXY(yz_image);
			xz_image = constructXZ(xy_image);
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
	
	ImagePlus constructXY(ImagePlus src) {
		ImagePlus xy = null;
		Calibration cal = null;
		if (srcCutSurface == CutSurface.CORONAL) {
			xy =new OrthogonalSlice().coronalToAxial(src);
		} else if (srcCutSurface == CutSurface.SAGITTAL){
			xy =new OrthogonalSlice().sagittalToAxial(src);
		}else {
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
		if (src.getProcessor().isSigned16Bit()) {
			xy.getCalibration().setSigned16BitCalibration();
		}
		return xy;
	}

	ImagePlus constructXZ(ImagePlus src) {
		if (ImageOrientation.getCutSurface(src) != CutSurface.AXIAL) {
			throw new IllegalArgumentException("Cannot create XZ...");
		}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		ImageStack stack = new ImageStack();
		Calibration cal = null;
		String seriesUID = UIDUtils.createUID();
		int size = src.getHeight();
		for (int y = 0; y < size; y++) {
			ImagePlus xz_ = orthTool.cutHorizontally(src, y);
			if(cal == null) {
				cal = xz_.getCalibration();
			}
			addUIDs(xz_, 1, seriesUID);
			stack.addSlice(xz_.getProcessor());
			stack.setSliceLabel(xz_.getInfoProperty(), y + 1);
		}
		orthTool = null;
		Calibration calHolder = this.imp.getCalibration().copy();// with density calibration
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
		if (ImageOrientation.getCutSurface(src) != CutSurface.AXIAL) {
			throw new IllegalArgumentException("Cannot create YZ...");
		}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		ImageStack stack = new ImageStack();
		Calibration cal = null;
		String seriesUID = UIDUtils.createUID();
		int width = src.getWidth();
		for (int w = 0; w < width; w++) {
			ImagePlus yz_ = orthTool.cutVirtically(src, w);
			if (cal == null) {
				cal = yz_.getCalibration();
			}
			addUIDs(yz_, 1, seriesUID);
			stack.addSlice(yz_.getProcessor());
			stack.setSliceLabel(yz_.getInfoProperty(), w + 1);
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
		if (src.getProcessor().isSigned16Bit()) {
			yz_imp.getCalibration().setSigned16BitCalibration();
		}
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
		double az = xz_image.getNSlices()/(double)xy_image.getHeight();
		int xzZ = (int) (xyY * az);
		if (xzZ < 0) {
			xzZ = 0;
		} else if (xzZ > xz_image.getNSlices() - 1) {
			xzZ = xz_image.getNSlices() - 1;
		}
		xz_prap.setImagePositionUsingSlider(xzZ);

		// update yz
		az = yz_image.getNSlices()/(double)xy_image.getWidth();
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
		if(ipp1 != null && ipp2 != null) {
			//back to prev pos
			xy_image.setSlice(prev_pos);
			slicingToUpper = ipp1[2] < ipp2[2];
		}

		// update xy
		double az = xy_image.getNSlices()/(double)xz_image.getHeight();
		int xyZ = 0;
		if(slicingToUpper) {
			xyZ = (int) ((xz_image.getHeight() - xzY) *az);
		}else {
			xyZ = (int) (xzY *az);
		}
		if (xyZ < 0) {
			xyZ = 0;
		} else if (xyZ > xy_image.getNSlices() - 1) {
			xyZ = xy_image.getNSlices() - 1;
		}
		xy_prap.setImagePositionUsingSlider(xyZ);

		// update yz
		az = yz_image.getNSlices()/(double)xz_image.getWidth();
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
		if(ipp1 != null && ipp2 != null) {
			//back to prev pos
			xy_image.setSlice(prev_pos);
			slicingToUpper = ipp1[2] < ipp2[2];
		}
		
		double az = xy_image.getNSlices()/(double)yz_image.getHeight();
		int xyZ = 0;
		if(slicingToUpper) {
			xyZ = (int) ((yz_size - yzY) * az);
		}else {
			xyZ = (int) (yzY * az);
		}
		if (xyZ < 0) {
			xyZ = 0;
		} else if (xyZ > xy_image.getNSlices() - 1) {
			xyZ = xy_image.getNSlices() - 1;
		}
		xy_prap.setImagePositionUsingSlider(xyZ);

		// update xz
		az = xz_image.getNSlices()/(double)yz_image.getWidth();
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

	void initCrosses(boolean showCrossLine) {
		refLines = null;
		// set cross line mode for praps
		xy_prap.setShowCrossLineMode(showCrossLine);
		xz_prap.setShowCrossLineMode(showCrossLine);
		yz_prap.setShowCrossLineMode(showCrossLine);
		xy_prap.clearCrossLines();
		xz_prap.clearCrossLines();
		yz_prap.clearCrossLines();
		xy_prap.setReferenceLineMPR(null);
		xz_prap.setReferenceLineMPR(null);
		yz_prap.setReferenceLineMPR(null);
	}

	void initReslice() {
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
	protected void resliceAndShow() {
		
		if (refLines == null) {
			return;
		}
		
		Slab slab = refLines.getSlab();
		
		if(slab == null || slab.size() < 1) {
			return; // or set blank image ?
		}
		
		//TODO 20241126
//		boolean excessAngle = false;
//		try {
			//do something ??
//		}

		ImageStack stack = new ImageStack();
		/*
		 *IMPORTANT, Keep using axial for reference volume. 
		 */
//		ImagePlus mainPlane = getSliceTargetImage(getSliceTargetPlane());
		ImagePlus mainPlane = xyImage();
		double min = mainPlane.getDisplayRangeMin();
		double max = mainPlane.getDisplayRangeMax();
		int count = 1;
		int reconMode = reconMode();
		List<SlicePlane> planes = slab.getSlicePlanes();
		Slicer slicer = new Slicer(mainPlane);
		
		for (int i=0; i<planes.size();i++) {
			SlicePlane plane = planes.get(i);
			Vector3d ipp = plane.getGeometryOfSlice().getTLHC();
			Vector3d row_v = plane.getGeometryOfSlice().getRow();
			Vector3d col_v = plane.getGeometryOfSlice().getColumn();
			ImageProcessor resliceIp = slicer.slice(plane, reconMode);
			resliceIp.setMinAndMax(min, max);
			ImagePlus temp = new ImagePlus("", resliceIp);
			GDicomTools.setImagePositionPatient(temp, 1, ipp);
			GDicomTools.setImageOrientationPatient(temp, 1, row_v, col_v);
			stack.addSlice(temp.getProcessor());
			stack.setSliceLabel(temp.getInfoProperty(), count++);
		}
		
		// construct imageplus
		recon_image = new ImagePlus("reslice", stack);
		Calibration calHolder = mainPlane.getCalibration();
		SlicePlane plane = planes.get(0);
		double px = plane.getGeometryOfSlice().getVoxelSpacing().y;
		double py = plane.getGeometryOfSlice().getVoxelSpacing().x;
		double pz = plane.getGeometryOfSlice().getVoxelSpacing().z;
		calHolder.pixelWidth = px;
		calHolder.pixelHeight = py;
		calHolder.pixelDepth = pz;
		recon_image.setCalibration(calHolder);
		String seUID = UIDUtils.createUID();
		for(int i=1; i<= recon_image.getNSlices(); i++) {
			addUIDs(recon_image, i, seUID);
		}
		SwingUtilities.invokeLater(() -> {
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
		if(refLines != null) {
			refLines.setSliceTarget(currentSliceTarget);
		}
	}
	
	public CutSurface getSrcSurface() {
		return srcCutSurface;
	}
	
	private int reconMode() {
		String reconType = contP.getReconType();
		if(reconType.equals(MPRControlPanel.reconType[0])) {
			return Slicer.SLICECUT;
		}else if(reconType.equals(MPRControlPanel.reconType[1])) {
			return Slicer.MEAN;
		}else if(reconType.equals(MPRControlPanel.reconType[2])) {
			return Slicer.MAX;
		}else if(reconType.equals(MPRControlPanel.reconType[3])) {
			return Slicer.MIN;
		}else if(reconType.equals(MPRControlPanel.reconType[4])) {
			return Slicer.MEDIAN;
		}else if(reconType.equals(MPRControlPanel.reconType[5])) {
			return Slicer.MODE;
		}else {
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
			//do nothing
		}
		getContentPane().repaint();
	}

	public void initState(int newViewType) {
		if (newViewType == currentViewType) {
			Log.logger.fine("Same viewType, return");
			return;
		}
		setCurrentViewType(newViewType);
		if (currentViewType == MPRViewerWindow.CROSS_MODE) {
			initCrosses(showCrossLine);
			Log.logger.fine("init Cross mode");
		} else if (currentViewType == MPRViewerWindow.SLICE_MODE) {
			initReslice();
			Log.logger.fine("init reslice mode");
		} else if (currentViewType == 2) {
			//add more
		}
		xy_prap.repaint();
		xz_prap.repaint();
		yz_prap.repaint();
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				revalidate();
				repaint();
			}
		});
	}
}
