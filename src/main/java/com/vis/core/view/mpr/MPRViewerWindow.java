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
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.joml.Vector3d;

import com.vis.core.view.D2.processing.ImagePlusDicomTagTools;
import com.vis.core.view.D2.ui.glasses.CanvasGlass;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.IntersectVolume;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.dicom.DicomObject;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.process.ImageProcessor;


/**
 * MPR view
 * ref: https://imagej.nih.gov/ij/developer/source/ij/plugin/Orthogonal_Views.java.html
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
    private int currentMainPlaneType = XY;
    
	
	/*
	 * https://forum.image.sc/t/rotate-line-roi-via-rotating-point-coordinates/8323
	 */
	//ui base
	private JPanel basePanel;
	private MPRControlPanel contP;
	private int currentViewType = 0;
	private int previousViewType = 0;
	
    private ImagePlus imp;//src imp, to backup.
    private DicomObject refDcm;//attribute holder to copy.

    private ImagePlus xy_image, xz_image, yz_image, recon_image, init_image;
    
    private Praparat xy_prap = null;
    private Praparat xz_prap = null;
    private Praparat yz_prap = null;
    private Praparat recon_prap = null;
    private Praparat currentPrap = null;//for mouse action
    
    private ReferenceLineMPR refLines;
    private String srcCutSurface;
    
    private String patID;
    private String studyUID;
    private String seriesUID;
    Color studyColor = Color.DARK_GRAY;
    
    boolean starting = true;
    boolean standalone= false;
    public boolean showCrossLines = true;
    
    int reconResolution = 5; // keep odd side value.
    private final String reconNotReady = "NOT-READY";
    
    private Logger logger = Logger.getLogger(MPRViewerWindow.class.getName());
    
    /**
     * debug purpose
     */
    public MPRViewerWindow() {}
    
    public MPRViewerWindow(Praparat prap) {
    	if(prap == null) {
    		throw new IllegalArgumentException("Praparat is null...");
    	}
    	loadImagePlus(prap);
		if(imp == null || imp.getStackSize() < 1) {
			logger.info("Number of images not enough.");
			throw new IllegalArgumentException("Number of images not enough.");
		}
		
		DicomObject refDcm = prap.getCurrentSlide().getHeader();
		this.refDcm = DicomObject.newDicomObject(refDcm, null);//duplicate
		
		Color sc = prap.getStudyColor();
		if(sc != null) {
			this.studyColor = sc;
		}
		Object[] idset = prap.getUIDs();
		if(idset != null) {
			this.patID = idset[0] != null ? (String)idset[0]:null;
			this.studyUID = idset[1] != null ? (String)idset[1]:null;
			this.seriesUID = idset[2] != null ? (String)idset[2]:null;
//			this.sopUIDs = idset[3] != null ? (String[])idset[3]:null;
		}
		init();
	}
    
	public MPRViewerWindow(ImagePlus imp, Color studyColor) {
		if(imp == null || imp.getStackSize() < 3) {
			return;
		}
		this.imp = imp;//xy_image
		if(studyColor != null) {
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
	
	public String getSeriesUID() {
		return this.seriesUID;
	}
	
	public DicomObject getSampleReferenceDcm() {
		return this.refDcm;
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
	
	public int getCurrentMainPlaneType() {
		return currentMainPlaneType;
	}
	
	public ImagePlus getCurrentMainPlaneImage() {
		int type = getCurrentMainPlaneType();
		if(type == XY) {
			return xyImage();
		}else if(type == XZ) {
			return xzImage();
		}else {
			return yzImage();
		}
	}
	
	public void showCrossLines(boolean show) {
		if(showCrossLines && !show) {
			//disable crosslines
			xy_prap.clearCrossLines();
			xz_prap.clearCrossLines();
			yz_prap.clearCrossLines();
			xy_prap.setShowCrossLineMode(false);
	      	xz_prap.setShowCrossLineMode(false);
	      	yz_prap.setShowCrossLineMode(false);
		}else if(!showCrossLines && show) {
			//enable crosslines
			initCrosses();
		}
		showCrossLines = show;
	}
	
	public void setCurrentMainPlaneType(String surface) {
		if(surface.equals(CutSurface.CORONAL.name())) {
			setCurrentMainPlaneType(XZ);
		}else if(surface.equals(CutSurface.SAGITTAL.name())) {
			setCurrentMainPlaneType(YZ);
		}else {
			setCurrentMainPlaneType(XY);
		}
	}
	
	public void setCurrentMainPlaneType(int surfaceType) {
		currentMainPlaneType = surfaceType;
	}
	
	/**
	 * load imagplus and dicom attributes
	 * @param pathToImages
	 */
	private void loadImagePlus(Praparat seriesStack) {
		this.imp = seriesStack.getImagePlus();
	}
	
	private void init() {
		PlanarSupport psup = new PlanarSupport();
		srcCutSurface = psup.isPlanarOf(imp);
		setCurrentMainPlaneType(srcCutSurface);
		// set initial loc in offscreen coords.
		initImages();
		initOrthogonals();// create ortho images
		buildGUI();
		initCrosses();
		setVisible(true);
	}
	
	private synchronized void buildGUI() {
		if(xz_image == null || yz_image == null) {
			logger.log(Level.WARNING, "Does not ready to start MPR window.");
			return;
		}
        //init view
		setTitle("Graphy MPR Viewer");	
		setSize(new Dimension(1000, 600));
		basePanel = new JPanel();
		basePanel.setLayout(new GridLayout(2, 2));
		add(basePanel, BorderLayout.CENTER);
      	setLocationRelativeTo(null);
      	//menubar
      	MPRMenuBar bar = new MPRMenuBar(this);
      	setJMenuBar(bar);
      	//control panel
      	contP = new MPRControlPanel(this, imp.getCalibration().pixelDepth);
      	add(contP, BorderLayout.NORTH);
      	contP.setPreferredSize(new Dimension(getWidth(), 40));
      	
      	xy_prap = new Praparat(xy_image, studyColor, ViewMode.MPR);
      	xz_prap = new Praparat(xz_image, studyColor, ViewMode.MPR);
      	yz_prap = new Praparat(yz_image, studyColor, ViewMode.MPR);
      	recon_prap = new Praparat(recon_image,studyColor, ViewMode.Normal);
        
        basePanel.add(xy_prap);
      	basePanel.add(xz_prap);
      	basePanel.add(yz_prap);
      	basePanel.add(recon_prap);
      	
    	xy_prap.setImagePositionUsingSlider(xy_image.getNSlices()/2-1);
   		xz_prap.setImagePositionUsingSlider(xz_image.getNSlices()/2-1);
   		yz_prap.setImagePositionUsingSlider(yz_image.getNSlices()/2-1);
	}
	
	/*
	 * 0:ortho
	 * 1:reslice
	 */
	public void setCurrentViewType(int viewType) {
		this.previousViewType = this.currentViewType;
		this.currentViewType = viewType;
	}
	
	/**
	 *  TODO
	 *  adjust ipp, iop for each direction.
	 *  adjust flip each direction.
	 * @param type
	 */
	
	public void changeMainPlane(int type) {
		if(getCurrentMainPlaneType() != type) {
			setCurrentMainPlaneType(type);
			refLines = null;
			initReslice();
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
		if(recon_image == init_image) {
			return null;
		}
		return recon_image;
	}
	
	/**
	 * Calculate stack size Z.
	 * @param xy
	 * @param cutSurfaceName
	 * @return
	 */
	private int corrected_Z_Size(ImagePlus xy, String cutSurfaceName) {
        int size = xy.getNSlices();//num of slice
        Calibration cal = xy.getCalibration().copy();
        double calx = cal.pixelWidth;
        double caly = cal.pixelHeight;
        double calz = cal.pixelDepth;
        double ax = 1.0;
        double ay = caly/calx;
        double az = calz/calx;
        double arat = az/ax;
        double brat = az/ay;
        int za = (int)(size*arat);
        int zb = (int)(size*brat);
		switch (cutSurfaceName) {
		case "CORONAL":
			return za;
		case "SAGITTAL":
			return zb;
		default://axi, oblique, unknown
			return size;
		}
	}
	
	
	/**
     * @param is - used to get the dimensions of the new ImageProcessors
     * @return
     */
    private void initImages() {
    	if(getCurrentMainPlaneType()==XY) {
			xy_image = new Duplicator().run(imp);
			xz_image = new ImagePlus();
			yz_image = new ImagePlus();
        }else if((getCurrentMainPlaneType()==XZ)) {
        	xz_image = new Duplicator().run(imp);
			xy_image = new ImagePlus();
			yz_image = new ImagePlus();
        }else {
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
		if (getCurrentMainPlaneType() == XY) {
			init_image = NewImage.createByteImage(reconNotReady, xy_image.getWidth(), xy_image.getHeight(), 1,
					NewImage.FILL_BLACK);
			recon_image = init_image;
		} else if (getCurrentMainPlaneType() == XZ) {
			init_image = NewImage.createByteImage(reconNotReady, xz_image.getWidth(), xz_image.getHeight(), 1,
					NewImage.FILL_BLACK);
			recon_image = init_image;
		} else {
			init_image = NewImage.createByteImage(reconNotReady, yz_image.getWidth(), yz_image.getHeight(), 1,
					NewImage.FILL_BLACK);
			recon_image = init_image;
		}
	}
	
	synchronized void update() {
        notify();
    }
	
	void initOrthogonals() {
		int type = getCurrentMainPlaneType();
		if(type == XY) {
			xz_image = constructAllXZ(xy_image);
			yz_image = constructAllYZ(xy_image);
		}else if(type == XZ){
			xz_image = constructAllXZ(xz_image);
			yz_image = constructAllYZ(xz_image);
		}else {
			xz_image = constructAllXZ(yz_image);
			yz_image = constructAllYZ(yz_image);
		}
    }
		
	/**
	 * Create orthogonal planes
	 * @param p : offScreen coordinates on SlideGlass.
	 */
//	void createOrthogonals_(Point p) {
//		int type = getCurrentMainPlaneType();
//		if(type == XY) {
//			constructXZ(p, xy_image);
//			constructYZ(p, xy_image);
//		}else if(type == XZ){
//			constructXZ(p, xz_image);
//			constructYZ(p, xz_image);
//		}else {
//			constructXZ(p, yz_image);
//			constructYZ(p, yz_image);
//		}
//    }
//	
//	
//	void constructXZ(Point p, ImagePlus src) {
//		OrthogonalSlice orthTool = new OrthogonalSlice();
//		if(p == null) {
//			p = new Point(0, src.getHeight()/2-1);
//		}
//		int slicePos = 1;
//		int type = getCurrentMainPlaneType();
//		if(type == XY) {
//			if (xy_prap != null) {
//				slicePos = xy_prap.getCurrentSlidePos() + 1;
//			}
//		}else if(type == XZ){
//			if (xz_prap != null) {
//				slicePos = xz_prap.getCurrentSlidePos() + 1;
//			}
//		}else {
//			if (yz_prap != null) {
//				slicePos = yz_prap.getCurrentSlidePos() + 1;
//			}
//		}
//		boolean flipXZ = true;
//		boolean rotateXZ = false;
//		this.xz_image = orthTool.cutXZ(src, p.y, slicePos, flipXZ, rotateXZ);
//		Calibration calHolder = this.imp.getCalibration().copy();//with density calibration
//		Calibration cal = xz_image.getCalibration();
//		calHolder.pixelWidth = cal.pixelWidth;
//		calHolder.pixelHeight = cal.pixelHeight;
//		calHolder.pixelDepth = cal.pixelDepth;
//		calHolder.setXUnit(cal.getXUnit());
//		calHolder.setYUnit(cal.getYUnit());
//		calHolder.setZUnit(cal.getZUnit());
//		xz_image.setCalibration(calHolder);
//		orthTool = null;
//    }
//    
//	
//    void constructYZ(Point p, ImagePlus src) {
//    	OrthogonalSlice orthTool = new OrthogonalSlice();
//		if(p == null) {
//			p = new Point(src.getWidth()/2-1, 0);
//		}
//		int slicePos = 1;
//		int type = getCurrentMainPlaneType();
//		if(type == XY) {
//			if (xy_prap != null) {
//				slicePos = xy_prap.getCurrentSlidePos() + 1;
//			}
//		}else if(type == XZ){
//			if (xz_prap != null) {
//				slicePos = xz_prap.getCurrentSlidePos() + 1;
//			}
//		}else {
//			if (yz_prap != null) {
//				slicePos = yz_prap.getCurrentSlidePos() + 1;
//			}
//		}
//		this.yz_image = orthTool.cutYZ(src, p.x, slicePos, flipYZ, rotateYZ);
//		Calibration calHolder = this.imp.getCalibration().copy();//with density calibration
//		Calibration cal = yz_image.getCalibration();
//		calHolder.pixelWidth = cal.pixelWidth;
//		calHolder.pixelHeight = cal.pixelHeight;
//		calHolder.pixelDepth = cal.pixelDepth;
//		calHolder.setXUnit(cal.getXUnit());
//		calHolder.setYUnit(cal.getYUnit());
//		calHolder.setZUnit(cal.getZUnit());
//		yz_image.setCalibration(calHolder);
//		orthTool = null;
//    }
    
    ImagePlus constructAllXY(ImagePlus src) {
    	int mainPlane = getCurrentMainPlaneType();
    	if(mainPlane == XY) {
    		return xy_image;
    	}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		int size = 0;
		ImageStack stack = new ImageStack();
		Calibration cal = null;
		if(mainPlane == XZ) {
			size = corrected_Z_Size(src, CutSurface.CORONAL.name());
			boolean flipXZ = false;
			boolean rotateXZ = false;
			for(int z=0;z<size;z++) {
				ImagePlus xy_ = orthTool.cutXZ(src, z, 1, flipXZ, rotateXZ);
				if(cal == null) {
					cal = xy_.getCalibration();
				}
				stack.addSlice(xy_.getProcessor());
				stack.setSliceLabel(xy_.getInfoProperty(), z+1);
			}
		}else {
			size = corrected_Z_Size(src, CutSurface.SAGITTAL.name());
			boolean flipXZ = false;
			boolean rotateXZ = true;
			for(int z=0;z<size;z++) {
				ImagePlus xy_ = orthTool.cutXZ(src, z, 1, flipXZ, rotateXZ);
				if(cal == null) {
					cal = xy_.getCalibration();
				}
				stack.addSlice(xy_.getProcessor());
				stack.setSliceLabel(xy_.getInfoProperty(), z+1);
			}
		}
		orthTool = null;
		Calibration calHolder = this.imp.getCalibration().copy();//with density calibration
		calHolder.pixelWidth = cal.pixelWidth;
		calHolder.pixelHeight = cal.pixelHeight;
		calHolder.pixelDepth = cal.pixelDepth;
		calHolder.setXUnit(cal.getXUnit());
		calHolder.setYUnit(cal.getYUnit());
		calHolder.setZUnit(cal.getZUnit());
		ImagePlus xz_imp = new ImagePlus("XZ", stack);
		xz_imp.setCalibration(calHolder);
		return xz_imp;
    }
    
    ImagePlus constructAllXZ(ImagePlus src) {
    	int mainPlane = getCurrentMainPlaneType();
    	if(mainPlane == XZ) {
    		return xz_image;
    	}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		int size = 0;
		ImageStack stack = new ImageStack();
		Calibration cal = null;
		if(mainPlane == XY) {
			size = src.getHeight();
			boolean flipXZ = true;
			boolean rotateXZ = false;
			for(int y=0;y<size;y++) {
				ImagePlus xz_ = orthTool.cutXZ(src, y, 1, flipXZ, rotateXZ);
				if(cal == null) {
					cal = xz_.getCalibration();
				}
				stack.addSlice(xz_.getProcessor());
				stack.setSliceLabel(xz_.getInfoProperty(), y+1);
			}
		}else {
			size = src.getWidth();
			boolean flipYZ = false;
			boolean rotateYZ = true;
			for(int w=0;w<size;w++) {
				ImagePlus xz_ = orthTool.cutYZ(src, w, 1, flipYZ, rotateYZ);
				if(cal == null) {
					cal = xz_.getCalibration();
				}
				stack.addSlice(xz_.getProcessor());
				stack.setSliceLabel(xz_.getInfoProperty(), w+1);
			}
		}
    	
		orthTool = null;
		Calibration calHolder = this.imp.getCalibration().copy();//with density calibration
		calHolder.pixelWidth = cal.pixelWidth;
		calHolder.pixelHeight = cal.pixelHeight;
		calHolder.pixelDepth = cal.pixelDepth;
		calHolder.setXUnit(cal.getXUnit());
		calHolder.setYUnit(cal.getYUnit());
		calHolder.setZUnit(cal.getZUnit());
		ImagePlus xz_imp = new ImagePlus("XZ", stack);
		xz_imp.setCalibration(calHolder);
		return xz_imp;
    }
    
	
    ImagePlus constructAllYZ(ImagePlus src) {
    	int mainPlane = getCurrentMainPlaneType();
    	if(mainPlane == YZ) {
    		return yz_image;
    	}
		OrthogonalSlice orthTool = new OrthogonalSlice();
		int size = src.getWidth();
		ImageStack stack = new ImageStack();
		Calibration cal = null;
		if(mainPlane == XY) {
			boolean flipYZ = false;
			boolean rotateYZ = false;
			for(int w=0;w<size;w++) {
				ImagePlus yz_ = orthTool.cutYZ(src, w, 1, flipYZ, rotateYZ);
				if(cal == null) {
					cal = yz_.getCalibration();
				}
				stack.addSlice(yz_.getProcessor());
				stack.setSliceLabel(yz_.getInfoProperty(), w+1);
			}
		}else {//XZ
			boolean flipYZ = true;
			boolean rotateYZ = true;
			for(int w=0;w<size;w++) {
				ImagePlus yz_ = orthTool.cutYZ(src, w, 1, flipYZ, rotateYZ);
				if(cal == null) {
					cal = yz_.getCalibration();
				}
				stack.addSlice(yz_.getProcessor());
				stack.setSliceLabel(yz_.getInfoProperty(), w+1);
			}
		}
		orthTool = null;
		Calibration calHolder = this.imp.getCalibration().copy();//with density calibration
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
     * @param p : point on XY slideglass
     */
    private void updateViewsUsingXY(Point xyP) {
    	if(xy_prap == null) {
    		return;
    	}
    	int xyX = xyP.x;
    	int xyY = xyP.y;
    	int xyZ = xy_prap.getCurrentSlidePos();
    	
    	// update xz
    	Calibration cal_xy = xy_image.getCalibration();
		double xy_px = cal_xy.pixelWidth;
		double xy_py = cal_xy.pixelHeight;
		double xy_pz = cal_xy.pixelDepth;
    	int xzX = xyX;
    	int xzY = (int)(xyZ*(xy_pz/xy_px));//xz_size - xzZ;
    	int xzZ = (int)(xyY*(xy_py/xy_px));
    	if(xzZ < 0) {
    		xzZ = 0;
    	}else if(xzZ > xz_image.getNSlices()-1) {
    		xyZ = xz_image.getNSlices()-1;
    	}
    	xz_prap.setImagePositionUsingSlider(xzZ);
    	
    	// update yz
    	int yzX = (int)(xyY*(xy_py/xy_px));
    	int yzY = (int)(xyZ*(xy_pz/xy_px));
    	int yzZ = xyX;
    	if(yzZ < 0) {
    		yzZ = 0;
    	}else if(yzZ > yz_image.getNSlices()-1) {
    		yzZ = yz_image.getNSlices()-1;
    	}
    	yz_prap.setImagePositionUsingSlider(yzZ);
    	repaint();
    }
    
    /**
     * 
     * @param p : point on XZ slideglass
     */
    private void updateViewsUsingXZ(Point xzP) {
    	int xzX = xzP.x;
    	int xzY = xzP.y;
    	int xzZ = xz_prap.getCurrentSlidePos();
    	int xz_size = xz_image.getHeight();//xz_prap.getNumberOfImages();
		
		// update xy
    	Calibration cal_xy = xy_image.getCalibration();
		double xy_px = cal_xy.pixelWidth;
		double xy_py = cal_xy.pixelHeight;
		double xy_pz = cal_xy.pixelDepth;
    	int xyX = xzX;
    	int xyY = (int)(xzZ/(xy_py/xy_px));//xz_size - xzZ;
    	int xyZ = (int)((xz_size - xzY)/(xy_pz/xy_px));
    	if(xyZ < 0) {
    		xyZ = 0;
    	}else if(xyZ > xy_image.getNSlices()-1) {
    		xyZ = xy_image.getNSlices()-1;
    	}
    	xy_prap.setImagePositionUsingSlider(xyZ);
    	
    	// update yz
    	int yzX = (int)(xyY*(xy_py/xy_px));
    	int yzY = (int)(xyZ*(xy_pz/xy_px));
    	int yzZ = xyX;
    	if(yzZ < 0) {
    		yzZ = 0;
    	}else if(yzZ > yz_image.getNSlices()-1) {
    		yzZ = yz_image.getNSlices()-1;
    	}
    	yz_prap.setImagePositionUsingSlider(yzZ);
    	repaint();
    }
    
    /**
     * 
     * @param p : point on YZ slideglass
     */
    private void updateViewsUsingYZ(Point yzP) {
    	int yzX = yzP.x;
    	int yzY = yzP.y;
    	int yzZ = yz_prap.getCurrentSlidePos();
    	int yz_size = yz_image.getHeight();
		
		// update xy
    	Calibration cal_xy = xy_image.getCalibration();
		double xy_px = cal_xy.pixelWidth;
		double xy_py = cal_xy.pixelHeight;
		double xy_pz = cal_xy.pixelDepth;
    	int xyX = (int)(yzZ/(xy_py/xy_px));
    	int xyY = yzX;
    	int xyZ = (int)((yz_size-yzY)/(xy_pz/xy_px));
    	if(xyZ < 0) {
    		xyZ = 0;
    	}else if(xyZ > xy_image.getNSlices()-1) {
    		xyZ = xy_image.getNSlices()-1;
    	}
    	xy_prap.setImagePositionUsingSlider(xyZ);
    	
    	// update xz
    	int xzX = xyX;
    	int xzY = (int)(xyZ*(xy_pz/xy_px));
    	int xzZ = (int)(xyY*(xy_py/xy_px));
    	if(xzZ < 0) {
    		xzZ = 0;
    	}else if(xzZ > xz_image.getNSlices()-1) {
    		xzZ = xz_image.getNSlices()-1;
    	}
    	xz_prap.setImagePositionUsingSlider(xzZ);
    	repaint();
    }
        
//    private void updateMagnification(int x, int y) {
//        double magnification= xy_prap.getCurrentSlide().getView().getMagnification();
//        int z = imp.getSlice()-1;
//        ImageWindow xz_win = xz_image.getWindow();
//        if (xz_win==null) return;
//        ImageCanvas xz_ic = xz_win.getCanvas();
//        double xz_mag = xz_ic.getMagnification();
//        double arat = az/ax;
//        int zcoord=(int)(arat*z);
//        if (flipXZ) zcoord=(int)(arat*(imp.getNSlices()-z));
//        while (xz_mag<magnification) {
//            xz_ic.zoomIn(xz_ic.screenX(x), xz_ic.screenY(zcoord));
//            xz_mag = xz_ic.getMagnification();
//        }
//        while (xz_mag>magnification) {
//            xz_ic.zoomOut(xz_ic.screenX(x), xz_ic.screenY(zcoord));
//            xz_mag = xz_ic.getMagnification();
//        }
//        ImageWindow yz_win = yz_image.getWindow();
//        if (yz_win==null) return;
//        ImageCanvas yz_ic = yz_win.getCanvas();
//        double yz_mag = yz_ic.getMagnification();
//        zcoord = (int)(arat*z);
//        while (yz_mag<magnification) {
//            yz_ic.zoomIn(yz_ic.screenX(zcoord), yz_ic.screenY(y));
//            yz_mag = yz_ic.getMagnification();
//        }
//        while (yz_mag>magnification) {
//            yz_ic.zoomOut(yz_ic.screenX(zcoord), yz_ic.screenY(y));
//            yz_mag = yz_ic.getMagnification();
//        }
//    }
    
	private void updateCrossXY(int xyX, int xyY) {
		xy_prap.clearCrossLines();
        Point p = new Point(xyX, xyY);
		drawCross(xy_prap, p);
	}

	private void updateCrossXZ(int xzX, int xzY) {
		xz_prap.clearCrossLines();
        Point p = new Point(xzX, xzY);
		drawCross(xz_prap, p);
	}

	private void updateCrossYZ(int yzX, int yzY) {
		yz_prap.clearCrossLines();
        Point p = new Point(yzX, yzY);
		drawCross(yz_prap, p);
	}
    
    /**
     * 
     * @param x: org image's off screen coordinate X (by xy_sg.onImageX())
     * @param y: org image's off screen coordinate Y (by xy_sg.onImageY())
     */
    private void updateCrossByXY(int xyX, int xyY) {
    	xy_prap.clearCrossLines();
        Point p = new Point(xyX, xyY);
		drawCross(xy_prap, p);
		
		Calibration xy_cal = xy_image.getCalibration();
		
		int xyZ = xy_prap.getCurrentSlidePos();
		
		//change xz
		int xzX = xyX;
		int xzY = (int)(xyZ * (xy_cal.pixelDepth/xy_cal.pixelWidth));
		updateCrossXZ(xzX, xzY);
		//change yz
		int yzX = (int)(xyY * (xy_cal.pixelHeight/xy_cal.pixelWidth));
		int yzY = (int)(xyZ * (xy_cal.pixelDepth/xy_cal.pixelWidth));
		updateCrossYZ(yzX, yzY);
    }
    
    private void updateCrossByXZ(int xzX, int xzY) {
    	xz_prap.clearCrossLines();
        Point p = new Point(xzX, xzY);
        int xzZ = xz_prap.getCurrentSlidePos();
		drawCross(xz_prap, p);
		
		Calibration xy_cal = xy_image.getCalibration();
		
		//change xy
		int xyX = xzX;
		int xyY = (int)(xzZ / (xy_cal.pixelHeight/xy_cal.pixelWidth));
		updateCrossXY(xyX, xyY);
		//change yz
		int yzX = (int)(xyY * (xy_cal.pixelHeight/xy_cal.pixelWidth));
		int yzY = xzY;
		updateCrossYZ(yzX, yzY);
    }
    
    private void updateCrossByYZ(int yzX, int yzY) {
    	yz_prap.clearCrossLines();
        Point p = new Point(yzX, yzY);
        int yzZ = yz_prap.getCurrentSlidePos();
		drawCross(yz_prap, p);
		
		Calibration xy_cal = xy_image.getCalibration();
		
		//change xy
		int xyX = (int)(yzZ / (xy_cal.pixelHeight/xy_cal.pixelWidth));
		int xyY = yzX;
		updateCrossXY(xyX, xyY);
		//change xz
		int xzX = xyX;
		int xzY = yzY;
		updateCrossXZ(xzX, xzY);
    }
    	
    /**
     * 
     * @param x: org image's off screen coordinate X
     * @param y: org image's off screen coordinate Y
     */
//	private synchronized void updateCrossesByXY(int xyX, int xyY) {
//		double arat=az/ax;
//        double brat=az/ay;
//        updateCrossXY(xyX, xyY);
//		//XZ
//        int z = xy_prap.getAllSlides().size();//imp.getNSlices();
//        int zlice = xy_prap.getCurrentSlidePos();//imp.getSlice()-1;
//        int zcoord = (int)Math.round(arat*zlice);
//        if (flipXZ) {
//        	zcoord = (int)Math.round(arat*(z-zlice));
//        }
//        updateCrossXZ(xyX, zcoord);
//        //YZ
//        if (!rotateYZ) {
//            if (flipXZ)
//                zcoord=(int)Math.round(brat*(z-zlice));
//            else
//                zcoord=(int)Math.round(brat*(zlice));           
//            updateCrossYZ(xyY, zcoord);
//        } else {
//            zcoord = (int)Math.round(arat*zlice);
//            updateCrossYZ(zcoord, xyY);
//        }
//    }
	
	synchronized void initCrosses() {
		/*
    	 * reset sliceLine
    	 */
    	refLines = null;
    	//set cross line mode for praps
		xy_prap.setShowCrossLineMode(true);
      	xz_prap.setShowCrossLineMode(true);
      	yz_prap.setShowCrossLineMode(true);
    	
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
		 * get sorted coordinates in general path
		 * get num of slice 
		 * iterate dynamic reslice
		 * show imp // debug
		 */
		if(refLines == null) {
			return;
		}
		int mainPlaneType = getCurrentMainPlaneType();
		boolean excessAngle = false;
		try {
			if(mainPlaneType == XY) {
				excessAngle = refLines.xYLine().isHorizontal() ? false:true;
			}else if(mainPlaneType == XZ) {
				excessAngle = refLines.xZLine().isHorizontal();
			}else {
				excessAngle = refLines.yZLine().isHorizontal() ? false:true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		ArrayList<float[]> sortedPointPairList = null;
		if(mainPlaneType == XY) {
			GeneralPath sliceLinePaths = refLines.xYLine().createSliceLinesWithOffScreenCoordinates();
			sortedPointPairList = refLines.xYLine().getPoints(sliceLinePaths);
		}else if(mainPlaneType == XZ) {
			GeneralPath sliceLinePaths = refLines.xZLine().createSliceLinesWithOffScreenCoordinates();
			sortedPointPairList = refLines.xZLine().getPoints(sliceLinePaths);
		}else {
			GeneralPath sliceLinePaths = refLines.yZLine().createSliceLinesWithOffScreenCoordinates();
			sortedPointPairList = refLines.yZLine().getPoints(sliceLinePaths);
		}
		
		ImageStack stack = null;
		ImagePlus mainPlane = getCurrentMainPlaneImage();
		int count = 1;
		String reconType = contP.getReconType();
		PlanarSupport psup = new PlanarSupport();
		ImagePlusDicomTagTools tools = new ImagePlusDicomTagTools();
		if(reconType.equals("SLICECUT")) {
			for(int i=0;i<sortedPointPairList.size();i+=2) {
				double  cx1 = sortedPointPairList.get(i)[0];
				double  cy1 = sortedPointPairList.get(i)[1];
				double  cx2 = sortedPointPairList.get(i+1)[0];
				double  cy2 = sortedPointPairList.get(i+1)[1];
				ImageProcessor resliceIp = Slicer.slice(mainPlane, excessAngle, cx1, cy1,cx2,cy2);
				if(stack == null) {
					stack = new ImageStack(resliceIp.getWidth(),resliceIp.getHeight(), sortedPointPairList.size()/2);
				}
				resliceIp.resetMinAndMax();
				
				if(mainPlaneType == XY) {//creating CORONAL or sagittal
					Vector3d ipp_v = psup.getNewImagePositionPatient2D(mainPlane, cx1, cy1, mainPlane.getNSlices());
					double[] iop = psup.rotateOrthogonallyImageOrientationPatient(mainPlane, CutSurface.AXIAL);
					int angle = (int) refLines.getAngleXY();
					Vector3d row = new Vector3d(iop[0], iop[1], iop[2]);//direction cosine
					Vector3d col = new Vector3d(iop[3], iop[4], iop[5]);//direction cosine
					/*
					 * The axis of rotation does not move, but rotates the other axis.
					 * e.g., rotateZ rotate only x and y.
					 */
					/*
					 * row direction cosine : --->	vector
					 * col direction cosine :	|
					 * 							|
					 * 							|
					 * 						   \/ 	vector
					 */
					if(!excessAngle) {//COR
						row = row.rotateZ(Math.toRadians(-angle));//angle near to horizontal (near 0 or +-180)
						col = col.rotateX(Math.toRadians(-90));
					}else {//SAG
						row = row.rotateZ(Math.toRadians(-angle));//angle near vertical (near +-90).
						col = col.rotateX(Math.toRadians(-90));
					}
					
					//System.out.println(row.distanceSquared(cx2, mainPlaneType, count));
					iop = new double[] {row.x, row.y, row.z, col.x, col.y, col.z};
					
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
					tools.setImagePositionPatient(temp, 1, ipp_v);
					tools.setImageOrientationPatient(temp, 1,iop);
					stack.setProcessor(resliceIp, count);
					stack.setSliceLabel(temp.getInfoProperty(), count);//stack.addSlice(temp.getInfoProperty(), resliceIp, count++);//do not use
					count++;
				}else if(mainPlaneType == XZ) {//creating sagittal or axial
					Vector3d ipp_v = psup.getNewImagePositionPatient2D(mainPlane, cx1, cy1, 1);
					double[] iop_sag = psup.rotateOrthogonallyImageOrientationPatient(mainPlane, CutSurface.CORONAL);
					int angle = (int) refLines.getAngleXZ();
					Vector3d row = new Vector3d(iop_sag[0], iop_sag[1], iop_sag[2]);
					Vector3d col = new Vector3d(iop_sag[3], iop_sag[4], iop_sag[5]);
					
					if(excessAngle) {//AXI
						row = row.rotateY(Math.toRadians(-angle));
						col = col.rotateX(Math.toRadians(90));
					} else {//SAG
						row = row.rotateZ(Math.toRadians(90));
						col = col.rotateY(Math.toRadians(-angle-90));
					}
					
					//System.out.println(row.distanceSquared(cx2, mainPlaneType, count));
					double[] iop = new double[] {row.x, row.y, row.z, col.x, col.y, col.z};
					
					if (Math.abs(row.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Row not a unit vector");
					}
					if (Math.abs(col.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Column not a unit vector");
					}
					if (row.dot(col) > 0.005) { // dot product should be cos(90)=0 if orthogonal between row direction cosine and col direction cosine.
						throw new IllegalArgumentException("Row and column vectors are not orthogonal:"+row.dot(col));
					}
					
					ImagePlus temp = new ImagePlus();
					tools.setImagePositionPatient(temp, 1,ipp_v);
					tools.setImageOrientationPatient(temp,1, iop);
					stack.setProcessor(resliceIp, count);
					stack.setSliceLabel(temp.getInfoProperty(), count);//stack.addSlice(temp.getInfoProperty(), resliceIp, count++);//do not use
					count++;
				}else {//creating coronal or axial
					Vector3d ipp_v = psup.getNewImagePositionPatient2D(mainPlane, cx1, cy1, 1);
					double[] iop_sag = psup.rotateOrthogonallyImageOrientationPatient(mainPlane, CutSurface.SAGITTAL);
					int angle = (int) refLines.getAngleYZ();
					Vector3d row = new Vector3d(iop_sag[0], iop_sag[1], iop_sag[2]);
					Vector3d col = new Vector3d(iop_sag[3], iop_sag[4], iop_sag[5]);
					
					if(!excessAngle) {//AXI
						row = row.rotateZ(Math.toRadians(-90));
						col = col.rotateX(Math.toRadians(angle+90));
					} else {//COR
						row = row.rotateZ(Math.toRadians(-90));
						col = col.rotateX(Math.toRadians(angle+90));
					}
					
					//System.out.println(row.distanceSquared(cx2, mainPlaneType, count));
					double[] iop = new double[] {row.x, row.y, row.z, col.x, col.y, col.z};
					
					if (Math.abs(row.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Row not a unit vector");
					}
					if (Math.abs(col.lengthSquared() - 1) > 0.001) {
						throw new IllegalArgumentException("Column not a unit vector");
					}
					if (row.dot(col) > 0.005) { // dot product should be cos(90)=0 if orthogonal
						double res = row.dot(col);
						throw new IllegalArgumentException("Row and column vectors are not orthogonal:"+res);
					}
					
					ImagePlus temp = new ImagePlus();
					tools.setImagePositionPatient(temp, 1,ipp_v);
					tools.setImageOrientationPatient(temp,1, iop);
					stack.setProcessor(resliceIp, count);
					stack.setSliceLabel(temp.getInfoProperty(), count);//stack.addSlice(temp.getInfoProperty(), resliceIp, count++);//do not use
					count++;
				}
			}
		}else if(reconType.equals("MEAN")) {
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
		}else {
			//do other algorithms
		}
		//construct imageplus
		recon_image = new ImagePlus("reslice",stack);
		double px;
		double py;
		double pz;
		//set calibration
		if(mainPlaneType == XY) {
			//SAG or COR
			Calibration calHolder = xy_image.getCalibration().copy();
			px = refLines.xYLine().getLength() / recon_image.getWidth();
			/*
			 * sag/cor images height set to [num slice * (pz/px)]
			 * therefore, pz is to be px.
			 */
			py = calHolder.pixelWidth;
			pz = contP.getSliceThickness() + contP.getSliceGap();
			calHolder.pixelWidth = px;
			calHolder.pixelHeight = py;
			calHolder.pixelDepth = pz;
			recon_image.setCalibration(calHolder);
		}else if(mainPlaneType == XZ) {
			Calibration calHolder = xz_image.getCalibration().copy();
			if(excessAngle) {//AXI
				px = refLines.xZLine().getLength() / recon_image.getWidth();
				py = calHolder.pixelHeight;
			}else {//SAG
				px = calHolder.pixelHeight;
				py = refLines.xZLine().getLength() / recon_image.getHeight();
			}
			pz = contP.getSliceThickness() + contP.getSliceGap();
			calHolder.pixelWidth = px;
			calHolder.pixelHeight = py;
			calHolder.pixelDepth = pz;
			recon_image.setCalibration(calHolder);
		}else {//YZ
			Calibration calHolder = yz_image.getCalibration().copy();
			if(!excessAngle) {//AXI
				px = calHolder.pixelHeight;
				py = refLines.yZLine().getLength() / recon_image.getHeight();
			}else {//COR
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
    	recon_prap.repaint();
	}
	
	double[] calcImagePositionPatient(double row, double col, int slicePos) {
		PlanarSupport psup = new PlanarSupport();
		if(slicePos < 1) {
			logger.info("SlicePos must be equal or than 1.");
			return null;
		}
		org.joml.Vector3d ipp = psup.getNewImagePositionPatient2D(this.xy_image,  col, row, slicePos);
		return new double[] {ipp.x, ipp.y, ipp.z};
	}
	
	/**
	 * row and col direction, it is easy to get confused.
	 * When images are axial plane,
	 * row direction cosine means X direction in RCS.
	 * col direction cosine means Y direction in RCS.
	 * @param row_rotateX in degree
	 * @param row_rotateY in degree
	 * @param row_rotateZ in degree
	 * @param col_rotateX in degree
	 * @param col_rotateY in degree
	 * @param col_rotateZ in degree
	 * @return
	 */
	double[] calcImageOrientationPatient(int row_rotateX, int row_rotateY, int row_rotateZ, int col_rotateX, int col_rotateY, int col_rotateZ) {
		PlanarSupport psup = new PlanarSupport();
		double[] iop = psup.getNewImageOrientationPatient(this.xy_image, row_rotateX, row_rotateY, row_rotateZ, col_rotateX, col_rotateY, col_rotateZ);
		return iop;//nullable
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
		if(refLines == null) {
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
	
	public Praparat getPraparatAt(int index) {
		if(index == MPRViewerWindow.XY) {
			return xy_prap;
		}else if(index == MPRViewerWindow.XZ) {
			return xz_prap;
		}else if(index == MPRViewerWindow.YZ) {
			return yz_prap;
		}else if(index == MPRViewerWindow.RECON) {
			return recon_prap;
		}
		return null;
	}
	
	
	void drawCross(Praparat pp, Point offScreenP) {
		if(showCrossLines) {
			CanvasGlass cg = (CanvasGlass)pp.getCurrentSlide().getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			cg.drawCross(offScreenP);
		}
	}
	
	void drawReferenceLines() {
		
		if(xz_image == null || yz_image == null) {
			return;
		}
		
		if(currentPrap != recon_prap) {
			if(recon_image == init_image) {
				return;
			}
			int pos = recon_prap.getCurrentSlidePos()+1;
			recon_image.setPosition(pos);
			
			int posXY = xy_prap.getCurrentSlidePos()+1;
			int posXZ = xz_prap.getCurrentSlidePos()+1;
			int posYZ = yz_prap.getCurrentSlidePos()+1;
			
			xy_image.setPosition(posXY);
			xz_image.setPosition(posXZ);
			yz_image.setPosition(posYZ);
			
			GeometryOfSlice srcGeometry = new GeometryOfSlice(recon_image);//want to show lines in other praps
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
		}else {//recon prap
			
		}
		getContentPane().repaint();
	}
	
	public void updateState(int newViewType) {
		setCurrentViewType(newViewType);
		if(newViewType == previousViewType) {
			return;
		}
		if(currentViewType == MPRViewerWindow.CROSS_MODE) {
			initCrosses();
		}else if(currentViewType == MPRViewerWindow.SLICE_MODE) {
			initReslice();
		}else if(currentViewType == 2){
			
		}
	}
	
}
