package com.vis.core.view.mpr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3d;
import org.scijava.vecmath.Point2d;

import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.measure.Calibration;

/**
 * Main lines for each plane;
 * X axis line (horizontal to x on XY) is main line in AXIAL plane.
 * Y axis line (horizontal to y on YZ) is main line on SAGITAL plane.
 * Z axis line (horizontal to z on XZ) is main line on CORONAL plane.
 * 
 * @author tatsunidas
 *
 */
public class ReferenceLineMPR {
		
	MPRViewerWindow mprWin;
	
	final Praparat xy_prap;
	final ImagePlus xy;
	final Praparat xz_prap;
	final ImagePlus xz;
	final Praparat yz_prap;
	final ImagePlus yz;
	
	final Calibration calXY;
	final Calibration calXZ;
	final Calibration calYZ;
	
	/**
	 * Use ReferenceLine instead of Line to distinguish Rois on CanvasGlass.
	 * 
	 * TODO
	 * ReferenceLine does not need rotation and change length...
	 * 
	 */
	public CenterPositionLine xYCenterLine;//horizontal to x, vertical to y, on XY.
	public CenterPositionLine xZCenterLine;//horizontal to z, vertical to x, on XZ.
	public CenterPositionLine yZCenterLine;//horizontal to y, vertical to z, on YZ.
	
	Color xyColor = Color.RED;//X coordinates color
	Color xzColor = Color.BLUE;//Z coordinates color
	Color yzColor = Color.GREEN;//Y coordinates color
	
	boolean antiAlias = true;
	int sliceLineStrokeWidth = 1;
	
	CutSurface sliceTarget = CutSurface.AXIAL;
	
	Slab slab;//reference slices
	
	public ReferenceLineMPR(MPRViewerWindow mprWin) {
		this.mprWin = mprWin;
		xy_prap = mprWin.getPraparatAt(CutSurface.AXIAL);
		xy = mprWin.xyImage();
		xz_prap = mprWin.getPraparatAt(CutSurface.CORONAL);
		xz = mprWin.xzImage();
		yz_prap = mprWin.getPraparatAt(CutSurface.SAGITTAL);
		yz = mprWin.yzImage();
		calXY = xy.getCalibration();
		calXZ = xz.getCalibration();
		calYZ = yz.getCalibration();
		initLines();
	}
	
	private void initLines() {
		xYCenterLine = new CenterPositionLine(CutSurface.AXIAL, 0, xy.getHeight()/2-1, xy.getWidth()-1, xy.getHeight()/2-1, xy_prap.getCurrentSlide());
		xZCenterLine = new CenterPositionLine(CutSurface.CORONAL, xz.getWidth()/2-1, 0, xz.getWidth()/2-1, xz.getHeight()-1, xz_prap.getCurrentSlide());
		yZCenterLine = new CenterPositionLine(CutSurface.SAGITTAL, 0, yz.getHeight()/2-1, yz.getWidth()-1, yz.getHeight()/2-1, yz_prap.getCurrentSlide());
		xy_prap.setReferenceLineMPR(this);
		xz_prap.setReferenceLineMPR(this);
		yz_prap.setReferenceLineMPR(this);
	}
	
	public void draw(Graphics g/*Graphics from CanvasGlass*/, String cutSurfaceName) {
		if(cutSurfaceName.equals("XY")) {
			xYCenterLine.draw(g);
		}else if(cutSurfaceName.equals("XZ")) {
			xZCenterLine.draw(g);
		}else if(cutSurfaceName.equals("YZ")) {
			yZCenterLine.draw(g);
		}
		
		//show slice line localizer
//		if(sliceTarget == CutSurface.AXIAL) {
//			showSliceLinesAsLocalizer(CutSurface.AXIAL, CutSurface.CORONAL);
//			showSliceLinesAsLocalizer(CutSurface.AXIAL, CutSurface.SAGITTAL);
//		}else if(sliceTarget == CutSurface.CORONAL) {
//			showSliceLinesAsLocalizer(CutSurface.CORONAL, CutSurface.AXIAL);
//			showSliceLinesAsLocalizer(CutSurface.CORONAL, CutSurface.SAGITTAL);
//		}else if(sliceTarget == CutSurface.SAGITTAL) {
//			showSliceLinesAsLocalizer(CutSurface.SAGITTAL, CutSurface.AXIAL);
//			showSliceLinesAsLocalizer(CutSurface.SAGITTAL, CutSurface.CORONAL);
//		}
	}
	
	public void setSliceTarget(CutSurface sliceTarget) {
		this.sliceTarget = sliceTarget;
	}
	
	void showSliceLinesAsLocalizer(CutSurface sliceTarget, CutSurface sub) {
		if(sliceTarget == CutSurface.AXIAL) {
			if(sub == CutSurface.CORONAL) {
				// for loop
				// get slice plane
				// 
//				List<Point2D> loca_geo = xy_prap.calcLocalizer(sliceLineStrokeWidth, x2, null, null, null, x1, sliceLineStrokeWidth, null, null, null);
//				xz_prap.getCurrentSlide().drawLocalizer(loca_geo);
			}else if(sub == CutSurface.SAGITTAL) {
				
			}
			return;
		}
		if(sliceTarget == CutSurface.CORONAL) {
			if(sub == CutSurface.AXIAL) {
				
			}else if(sub == CutSurface.SAGITTAL) {
				
			}
			return;
		}
		if(sliceTarget == CutSurface.SAGITTAL) {
			if(sub == CutSurface.AXIAL) {
				
			}else if(sub == CutSurface.CORONAL) {
				
			}
			return;
		}
	}
	
	//TODO
	/**
	 * Update reference slice lines on XY plane.
	 * @param from
	 * @param refLine
	 */
//	private void lineX(CutSurface from, ReferenceLine refLine) {
//		if(from == CutSurface.AXIAL) {
//			//do nothing
//		}else if(from == CutSurface.CORONAL) {
//			
//		}else if(from == CutSurface.SAGITTAL) {
//			
//		}
//		if (xy_prap == null) {
//			return;
//		}
//		int xyX = xyP.x;
//		int xyY = xyP.y;
//		int xyZ = xy_prap.getCurrentSlidePos();
//		/*
//		 * Since it is not an ISO voxel, the position of the cross-section is
//		 * recalculated from each voxel size.
//		 */
//		// update xz
//		Calibration cal_xy = xy_image.getCalibration();
//		double xy_px = cal_xy.pixelWidth;
//		double xy_py = cal_xy.pixelHeight;
//		double xy_pz = cal_xy.pixelDepth;
//		int xzX = xyX;
//		int xzY = (int) (xyZ * (xy_pz / xy_px));// xz_size - xzZ;
//		int xzZ = (int) (xyY * (xy_py / xy_px));
//		if (xzZ < 0) {
//			xzZ = 0;
//		} else if (xzZ > xz_image.getNSlices() - 1) {
//			xyZ = xz_image.getNSlices() - 1;
//		}
//		xz_prap.setImagePositionUsingSlider(xzZ);
//
//		// update yz
//		int yzX = (int) (xyY * (xy_py / xy_px));
//		int yzY = (int) (xyZ * (xy_pz / xy_px));
//		int yzZ = xyX;
//		if (yzZ < 0) {
//			yzZ = 0;
//		} else if (yzZ > yz_image.getNSlices() - 1) {
//			yzZ = yz_image.getNSlices() - 1;
//		}
//		yz_prap.setImagePositionUsingSlider(yzZ);
//	}
	
	public CenterPositionLine centerPositionLineFrom(Praparat pp) {
		Praparat xy = mprWin.getPraparatAt(CutSurface.AXIAL);
		Praparat xz = mprWin.getPraparatAt(CutSurface.CORONAL);
		Praparat yz = mprWin.getPraparatAt(CutSurface.SAGITTAL);
		if(pp == xy) {
			return xYCenterLine;
		}else if(pp == xz) {
			return xZCenterLine;
		}else if(pp == yz){
			return yZCenterLine;
		}else {
			return null;
		}
	}
	
	public CenterPositionLine centerPositionLineFrom(CutSurface surface) {
		if(surface == CutSurface.AXIAL) {
			return xYLine();
		}else if (surface == CutSurface.CORONAL){
			return xZLine();
		}else if(surface == CutSurface.SAGITTAL) {
			return yZLine();
		}else {
			return null;
		}
	}
	
	/**
	 * 
	 * @return center position of initial slice planar.
	 */
	public Vector3d getCenterPosition() {
		//todo
		return null;
	}
	
	public CenterPositionLine xYLine() {
		return xYCenterLine;
	}
	
	public CenterPositionLine xZLine() {
		return xZCenterLine;
	}
	
	public CenterPositionLine yZLine() {
		return yZCenterLine;
	}
	
	void updateResliceLineState() {
		if(xYCenterLine == null || xZCenterLine == null || yZCenterLine == null) {
			initLines();
		}
		updateReslicePlanes();
		//TODO 20240924
//		updateXZLine();
//		updateYZLine();
	}
	
	public void updateReslicePlanes() {
		Double fovW = mprWin.getFOV_W();//mm
		Double fovH = mprWin.getFOV_H();//mm
		Double thickness = mprWin.getSliceThickness();
		Double gap = mprWin.getSliceGap();
		Integer numOfSlice = mprWin.getNumberOfSlices();
		
		if(Double.isNaN(fovW) || fovW <= 0.) {
			return;
		}
		if(Double.isNaN(fovH) || fovH <= 0.) {
			return;
		}
		if(Double.isNaN(thickness) || thickness <= 0.) {
			return;
		}
		if(Double.isNaN(numOfSlice) || numOfSlice < 1) {
			return;
		}
		if(Double.isNaN(gap) || gap < 0d) {
			gap = 0d;
		}
		
		int rotateX = (int)PlanarSupport.truncate(yZCenterLine.getAngle(),0);
		int rotateY = (int)PlanarSupport.truncate(xZCenterLine.getAngle()-90/*correct vertically*/,0);
		int rotateZ = (int)PlanarSupport.truncate(xYCenterLine.getAngle(),0);
		
		Vector3d refIOP = null;
		CenterPositionLine refCenterLine = null;
		ImagePlus refVolume = null;
		int currentPos = -1;
		if(sliceTarget == CutSurface.AXIAL) {
			double[] iop = GDicomTools.getImageOrientationPatient(xz, 1);
			refIOP = new Vector3d(iop);
			refCenterLine = xYCenterLine;
			refVolume = xy;
			currentPos = xy_prap.getCurrentSlidePos()+1;//to 1 base
		}else if(sliceTarget == CutSurface.CORONAL) {
			double[] iop = GDicomTools.getImageOrientationPatient(yz, 1);
			refIOP = new Vector3d(iop);
			refCenterLine = xZCenterLine;
			refVolume = xz;
			currentPos = xz_prap.getCurrentSlidePos()+1;//to 1 base
		}else if(sliceTarget == CutSurface.SAGITTAL) {
			double[] iop = GDicomTools.getImageOrientationPatient(xy, 1);
			refIOP = new Vector3d(iop);
			refCenterLine = yZCenterLine;
			refVolume = yz;
			currentPos = yz_prap.getCurrentSlidePos()+1;//to 1 base
		}
		
		Vector3d iop = PlanarSupport.rotateImageOrientationPatient(refIOP, rotateX, rotateY, rotateZ);

		Point2d currentLineStart = null;
		Point2d currentLineEnd = null;
		
		List<SlicePlane> slices = new ArrayList<>();
		
		for(int i=0; i<numOfSlice;i++) {
//			if(i == 0) {
//				//First, create center line SlicePlanar
//				int half_w = (int) ((fovW/2)/refVolume.getCalibration().pixelWidth);
//				int half_h = (int) ((fovH/2)/refVolume.getCalibration().pixelDepth);
//				// Cubeの頂点の配列
//		        Vector3d[] vertices = new Vector3d[8];
//				// 8つの頂点を計算
//		        vertices[0] = new Vector3d(center.x - halfSide, center.y - halfSide, center.z - halfSide);
//		        vertices[1] = new Vector3d(center.x + halfSide, center.y - halfSide, center.z - halfSide);
//		        vertices[2] = new Vector3d(center.x - halfSide, center.y + halfSide, center.z - halfSide);
//		        vertices[3] = new Vector3d(center.x + halfSide, center.y + halfSide, center.z - halfSide);
//		        vertices[4] = new Vector3d(center.x - halfSide, center.y - halfSide, center.z + halfSide);
//		        vertices[5] = new Vector3d(center.x + halfSide, center.y - halfSide, center.z + halfSide);
//		        vertices[6] = new Vector3d(center.x - halfSide, center.y + halfSide, center.z + halfSide);
//		        vertices[7] = new Vector3d(center.x + halfSide, center.y + halfSide, center.z + halfSide);
//		        
//				
//				Vector3d newIPP = new PlanarSupport().getNewImagePositionPatient2D(refVolume, (int)currentLineStart.x, (int)currentLineStart.y, currentPos);
//				SlicePlane sp = new 
//				addSliceLine(sliceLines, (float)currentLineStart.x, (float)currentLineStart.y, (float)currentLineEnd.x, (float)currentLineEnd.y);
//				continue;
//			}
//			if(i <= numOfSlice/2) {// create upper side slice
//				Point2d[] nextPoints = nextParallelLinePoints(currentLineStart, currentLineEnd, true);
//				addSliceLine(sliceLines, (float)nextPoints[0].x, (float)nextPoints[0].y, (float)nextPoints[1].x, (float)nextPoints[1].y);
//				currentLineStart = nextPoints[0];
//				currentLineEnd = nextPoints[1];
//				if(i == numOfSlice/2) {//reset to center
//					currentLineStart = new Point2d(centerLineX1, centerLineY1);
//					currentLineEnd = new Point2d(centerLineX2, centerLineY2);
//				}
//			}else {// create bottom side slice
//				Point2d[] nextPoints = nextParallelLinePoints(currentLineStart, currentLineEnd, false);
//				addSliceLine(sliceLines, (float)nextPoints[0].x, (float)nextPoints[0].y, (float)nextPoints[1].x, (float)nextPoints[1].y);
//				currentLineStart = nextPoints[0];
//				currentLineEnd = nextPoints[1];
//			}
		}
		
		
//		Slab slab = new Slab();
	}
	
//	public void updateXYLine() {
//		xYCenterLine.setThickness(mprWin.getSliceThickness());
//		xYCenterLine.setGap(mprWin.getSliceGap());
//		xYCenterLine.setNumOfSlice(mprWin.getNumberOfSlices());
//		xYCenterLine.createSliceLinesWithOffScreenCoordinates();
//		if(xYCenterLine.getSlideGlass() != null) {
//			xYCenterLine.getSlideGlass().repaintCanvasGlass();
//		}
//	}
//	
//	public void updateXZLine() {
//		xZCenterLine.setThickness(mprWin.getSliceThickness());
//		xZCenterLine.setGap(mprWin.getSliceGap());
//		xZCenterLine.setNumOfSlice(mprWin.getNumberOfSlices());
//		xZCenterLine.createSliceLinesWithOffScreenCoordinates();
//	}
//	
//	public void updateYZLine() {
//		yZCenterLine.setThickness(mprWin.getSliceThickness());
//		yZCenterLine.setGap(mprWin.getSliceGap());
//		yZCenterLine.setNumOfSlice(mprWin.getNumberOfSlices());
//		yZCenterLine.createSliceLinesWithOffScreenCoordinates();
//	}
//	
	
	private Integer find_yzY_Position(ImagePlus yz, ImagePlus xz, double xzX, double xzY) {
		PlanarSupport psup = new PlanarSupport();
		Vector3d ippAtPointInRCS = psup.getNewImagePositionPatient2D(xz, xzX, xzY, 1);
		if(ippAtPointInRCS == null) {
			return null;
		}
		double target_Z_InRCS = ippAtPointInRCS.z();
		int h = yz.getHeight();
		double dif = Double.MAX_VALUE;
		int coorY = -1;//Y axis in YZ is means Z in RCS.
		for(int i=0;i<h;i++) {
			Vector3d ipp = psup.getNewImagePositionPatient2D(yz, 0, i, 1);
			if(ipp == null) {
				continue;
			}
			if(Math.abs(target_Z_InRCS - ipp.z()) < 0.0001) {
				return i;
			}
			if(Math.abs(target_Z_InRCS - ipp.z()) < dif) {
				dif = Math.abs(target_Z_InRCS - ipp.z());
				coorY = i;
			}
		}
		return coorY;
	}
	
	private Integer find_xzX_Position(ImagePlus xz, ImagePlus xy, double xyX, double xyY) {
		PlanarSupport psup = new PlanarSupport();
		Vector3d ippAtPointInRCS = psup.getNewImagePositionPatient2D(xy, xyX, xyY, 1);
		if(ippAtPointInRCS == null) {
			return null;
		}
		double target_X_InRCS = ippAtPointInRCS.x();
		int w = xz.getWidth();
		double dif = Double.MAX_VALUE;
		int coorX = -1;
		for(int i=0;i<w;i++) {
			Vector3d ipp = psup.getNewImagePositionPatient2D(xz, i, 0, 1);
			if(ipp == null) {
				continue;
			}
			if(Math.abs(target_X_InRCS - ipp.x()) < 0.0001) {
				return i;
			}
			if(Math.abs(target_X_InRCS - ipp.x()) < dif) {
				dif = Math.abs(target_X_InRCS - ipp.x());
				coorX = i;
			}
		}
		return coorX;
	}
	
	private Integer find_xyY_Position(ImagePlus xy, ImagePlus yz, double yzX, double yzY) {
		PlanarSupport psup = new PlanarSupport();
		Vector3d ippAtPointInRCS = psup.getNewImagePositionPatient2D(yz, yzX, yzY, 1);
		if(ippAtPointInRCS == null) {
			return null;
		}
		double target_Y_InRCS = ippAtPointInRCS.y();
		int h = xy.getHeight();
		double dif = Double.MAX_VALUE;
		int coorY = -1;
		for(int i=0;i<h;i++) {
			Vector3d ipp = psup.getNewImagePositionPatient2D(xy, 0, i, 1);
			if(ipp == null) {
				continue;
			}
			if(Math.abs(target_Y_InRCS - ipp.y()) < 0.0001) {
				return i;
			}
			if(Math.abs(target_Y_InRCS - ipp.y()) < dif) {
				dif = Math.abs(target_Y_InRCS - ipp.y());
				coorY = i;
			}
		}
		return coorY;
	}
	
	private Point2d[] getXYLeftUpperAndRightLowerOnXY_Prap() {
		ImagePlus yz = mprWin.yzImage();
		Praparat xy_prap = mprWin.getPraparatAt(CutSurface.AXIAL);
		ImagePlus xy = xy_prap.getCurrentSlide().getOriginalImage();
		int start_xyY = find_xyY_Position(xy, yz, yZCenterLine.x1d, yZCenterLine.y1d);
		int end_xyY = find_xyY_Position(xy, yz, yZCenterLine.x2d, yZCenterLine.y2d);
		Point2d lu = new Point2d(xYCenterLine.x1d, start_xyY);
		Point2d rl = new Point2d(xYCenterLine.x2d, end_xyY);
		return new Point2d[] { lu, rl };
	}
	
	private Point2d[] getXZLeftUpperAndRightLowerOnXZ_Prap() {
		Praparat xy_prap = mprWin.getPraparatAt(CutSurface.AXIAL);
		Praparat xz_prap = mprWin.getPraparatAt(CutSurface.CORONAL);
		ImagePlus xy = xy_prap.getCurrentSlide().getOriginalImage();
		ImagePlus xz = xz_prap.getCurrentSlide().getOriginalImage();
		
		int start_xzX = find_xzX_Position(xz, xy, xYCenterLine.x1d, xYCenterLine.y1d);
		int end_xzX = find_xzX_Position(xz, xy, xYCenterLine.x2d, xYCenterLine.y2d);
		
		Point2d lu = new Point2d(start_xzX, xZCenterLine.y1d);
		Point2d rl = new Point2d(end_xzX, xZCenterLine.y2d);
		return new Point2d[] {lu,rl};
	}
	
	private Point2d[] getYZLeftUpperAndRightLowerOnYZ_Prap() {
		Praparat xz_prap = mprWin.getPraparatAt(CutSurface.CORONAL);
		Praparat yz_prap = mprWin.getPraparatAt(CutSurface.SAGITTAL);
		ImagePlus xz = xz_prap.getCurrentSlide().getOriginalImage();
		ImagePlus yz = yz_prap.getCurrentSlide().getOriginalImage();
		
		int start_yzY = find_yzY_Position(yz, xz, xZCenterLine.x1d, xZCenterLine.y1d);
		int end_yzY = find_yzY_Position(yz, xz, xZCenterLine.x2d, xZCenterLine.y2d);
		
		Point2d lu = new Point2d(yZCenterLine.x1d, start_yzY);
		Point2d rl = new Point2d(yZCenterLine.x2d, end_yzY);
		return new Point2d[] {lu,rl};
	}
	
	public double getAngleXY() {
		return xYCenterLine.getAngle();
	}
	
	public double getAngleXZ() {
		// default vertical line angle is -90.
		return xZCenterLine.getAngle();
	}
	
	public double getAngleYZ() {
		return yZCenterLine.getAngle();
	}
	
//	private GeometryOfSlice slicelineToGeometry(ImagePlus mainPlane, double sx,double sy,double ex, double ey) {
//		int currentPos = mainPlane.getCurrentSlice();
//		int mainPlaneType = mprWin.getCurrentMainPlaneType();
//		PlanarSupport psup = new PlanarSupport();
//		if(mainPlaneType == MPRViewerWindow.XY) {
//			Calibration cal = mainPlane.getCalibration();
//			//stack left upper
//			int x = (int)sx;
//			int y = (int)sy; //(yZLine.x1 /(cal.pixelHeight/cal.pixelWidth));
//			int startSlicePos = (int) ((xZLine.y1 /(cal.pixelDepth/cal.pixelWidth))+1);
//			int endSlicePos = (int) ((xZLine.y2 /(cal.pixelDepth/cal.pixelWidth))+1);
//			Vector3d ipp = psup.getNewImagePositionPatient2D(mainPlane, x, y, startSlicePos);
//			
//			//row direction (i.e. x direction)
//			int row_rotate_x = (int)getAngleXY();
//			int row_rotate_y = (int)getAngleYZ();
//			int row_rotate_z = (int)getAngleXZ();
//			int col_rotate_x = (int)getAngleXY();
//			int col_rotate_y = (int)getAngleYZ();
//			int col_rotate_z = (int)getAngleXZ();
//			double[] iop_cor = psup.rotateOrthogonallyImageOrientationPatient(mainPlane, CutSurface.CORONAL);
//			double[] iop = psup.rotateImageOrientationPatient(iop_cor, row_rotate_x, row_rotate_y, row_rotate_z, col_rotate_x, col_rotate_y, col_rotate_z);
//			
//			Vector3d iop_row = new Vector3d(iop[0], iop[1], iop[2]);
//			Vector3d iop_col = new Vector3d(iop[3], iop[4], iop[5]);
//			Vector3d tlhc = ipp;
//			int numCol = (int) (new Vector2d(sx,sy).distance(ex, ey));
//			int numRow = (int) (new Vector3d(sx,sy,startSlicePos).distance(ex, ey, endSlicePos));
//			double[] pixelSpacing = new double[] {};
//		}
//		
////		
//////		double spacingBetweenSlices = TagDict.getReadableValue(dcm, TagDict.At("SpacingBetweenSlices"), double[].class)[0];
////		Double spacingBetweenSlices = Double.parseDouble(dcm.getString(TagDict.At("SpacingBetweenSlices"), "-1"));
//////		this.sliceThickness = TagDict.getReadableValue(dcm, TagDict.At("SliceThickness"), double[].class)[0];
////		this.sliceThickness = Double.parseDouble(dcm.getString(TagDict.At("SliceThickness"), "1"));//keep 1. fail safe
////		this.voxelSpacing = new Vector3d(new double[] {pixelSpacing[0],pixelSpacing[1],spacingBetweenSlices == null ? this.sliceThickness:spacingBetweenSlices});
//////		int numRow = TagDict.getReadableValue(dcm, TagDict.At("Rows"), int[].class)[0];
//////		int numColumn = TagDict.getReadableValue(dcm, TagDict.At("Columns"), int[].class)[0];
////		Integer numRow = Integer.parseInt(dcm.getString(TagDict.At("Rows"), "-1"));
////		Integer numColumn = Integer.parseInt(dcm.getString(TagDict.At("Columns"), "-1"));
////		this.dimensions = new Vector3d(new double[] {numRow,numColumn,1});
////		return new GeometryOfSlice();
//		
//		mainPlane.setPosition(currentPos);
//		return null;
//	}
	
	
//	private GeneralPath rectPath(ReferenceLine refLine, Point2d leftUpper, Point2d rightLower) {
//		GeneralPath rect = new GeneralPath();
//		/*
//		 * keep moveTo + lineTo pair. because, see,  refLine.toScreenCoordinates()
//		 */
//		refLine.addSliceLine(rect, (float)leftUpper.x,(float)leftUpper.y, (float)rightLower.x,(float)leftUpper.y);
//		refLine.addSliceLine(rect, (float)rightLower.x,(float)leftUpper.y, (float)rightLower.x,(float)rightLower.y);
//		refLine.addSliceLine(rect, (float)rightLower.x,(float)rightLower.y, (float)leftUpper.x,(float)rightLower.y);
//		refLine.addSliceLine(rect, (float)leftUpper.x,(float)rightLower.y, (float)leftUpper.x,(float)leftUpper.y);
//		return rect;
//	}
	
//	public void drawRect(ReferenceLine refLine, Graphics g) {
//		Graphics2D g2d = (Graphics2D) g;
//		BasicStroke bs2 = new BasicStroke(sliceLineStrokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1.0f, new float[] {6}, 0);
//		g2d.setStroke(bs2);
//		if(refLine.getPlane() == CutSurface.AXIAL) {
//			Point2d[] xyPs = getXYLeftUpperAndRightLowerOnXY_Prap();
//			if(xyPs == null) {
//				return;
//			}
//			GeneralPath xyRect = rectPath(refLine, xyPs[0],xyPs[1]);
//			xyRect = xYCenterLine.toScreenCoordinates(xyRect);
//			g2d.setColor(xyColor);
//			g2d.draw(xyRect);
//		}else if(refLine.getPlane() == CutSurface.CORONAL) {
//			Point2d[] xzPs = getXZLeftUpperAndRightLowerOnXZ_Prap();
//			GeneralPath xzRect = rectPath(refLine, xzPs[0],xzPs[1]);
//			xzRect = xZCenterLine.toScreenCoordinates(xzRect);
//			g2d.setColor(xzColor);
//			g2d.draw(xzRect);
//		}else if(refLine.getPlane() == CutSurface.SAGITTAL) {
//			Point2d[] yzPs = getYZLeftUpperAndRightLowerOnYZ_Prap();
//			GeneralPath yzRect = rectPath(refLine, yzPs[0],yzPs[1]);
//			yzRect = yZCenterLine.toScreenCoordinates(yzRect);
//			g2d.setColor(yzColor);
//			g2d.draw(yzRect);
//		}
//	}
	
//	private void drawXY(Graphics g){
//		drawHandles(refLine, g);
//		drawRect(refLine, g);
//		Graphics2D g2d = (Graphics2D) g;
//		if(refLine.getSliceLines() != null) {
//			GeneralPath sliceLines_ = refLine.toScreenCoordinates(refLine.getSliceLines());
//			if (sliceLines_ != null) {
//				g2d.setColor(xyColor);
//				BasicStroke bs3 = new BasicStroke(sliceLineStrokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
//						1.0f, new float[] { 6 }, 0);
//				g2d.setStroke(bs3);
//				g2d.draw(sliceLines_);
//			}
//		}
//	}
//	
//	private void drawXZ(ReferenceLine refLine, Graphics g, SlideGlass sg){
//		drawHandles(refLine, g);
//		drawRect(refLine, g);
//		Graphics2D g2d = (Graphics2D) g;
//		if(refLine.getSliceLines() != null && mprWin.getSrcSurface()==CutSurface.CORONAL) {
//			GeneralPath sliceLines_ = refLine.toScreenCoordinates(refLine.getSliceLines());
//			if (sliceLines_ != null) {
//				g2d.setColor(xzColor);
//				BasicStroke bs3 = new BasicStroke(sliceLineStrokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
//						1.0f, new float[] { 6 }, 0);
//				g2d.setStroke(bs3);
//				g2d.draw(sliceLines_);
//			}
//		}
//	}
//	
//	private void drawYZ(ReferenceLine refLine, Graphics g){
//		drawHandles(refLine, g);
//		drawRect(refLine, g);
//		Graphics2D g2d = (Graphics2D) g;
//		if(refLine.getSliceLines() != null && mprWin.getSrcSurface()==CutSurface.SAGITTAL) {
//			GeneralPath sliceLines_ = refLine.toScreenCoordinates(refLine.getSliceLines());
//			if (sliceLines_ != null) {
//				g2d.setColor(yzColor);
//				BasicStroke bs3 = new BasicStroke(sliceLineStrokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
//						1.0f, new float[] { 6 }, 0);
//				g2d.setStroke(bs3);
//				g2d.draw(sliceLines_);
//			}
//		}
//	}
}
