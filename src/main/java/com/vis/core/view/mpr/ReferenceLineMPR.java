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
import java.util.List;

import org.joml.Vector3d;
import org.scijava.vecmath.Point2d;

import com.vis.core.view.D2.roi.ReferenceLine;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;

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
	
	/*
	 * Line3D in Axial space
	 */
	int x1, y1, z1, x2, y2, z2;//Line3D
	
	/**
	 * Use ReferenceLine instead of Line to distinguish Rois on CanvasGlass.
	 */
	public ReferenceLine xYLine;//horizontal to x, vertical to y, on XY.
	public ReferenceLine xZLine;//horizontal to z, vertical to x, on XZ.
	public ReferenceLine yZLine;//horizontal to y, vertical to z, on YZ.
	
	Color xyColor = Color.RED;//X coordinates color
	Color xzColor = Color.BLUE;//Z coordinates color
	Color yzColor = Color.GREEN;//Y coordinates color
	
	boolean antiAlias = true;
	int sliceLineStrokeWidth = 1;
	
	CutSurface sliceTarget = CutSurface.AXIAL;
	
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
		
//		CutSurface srcSurf = mprWin.getSrcSurface();
//		ImagePlus src = mprWin.getSrcImage();
//		int[] xy_mat = mprWin.calculateOrthogonalImageSize(src, srcSurf, CutSurface.AXIAL);
//		int[] xz_mat = mprWin.calculateOrthogonalImageSize(src, srcSurf, CutSurface.CORONAL);
//		int[] yz_mat = mprWin.calculateOrthogonalImageSize(src, srcSurf, CutSurface.SAGITTAL);
		
		x1 = 0;
		y1 = yz.getWidth()/2-1;
		z1 = xz.getHeight()/2-1;
		x2 = xy.getWidth()-1;
		y2 = yz.getWidth()/2-1;
		z2 = xz.getHeight()/2-1;
		
		xYLine = new ReferenceLine(CutSurface.AXIAL, x1, y1, x2, y2, xy_prap.getCurrentSlide());
		xZLine = new ReferenceLine(CutSurface.CORONAL, x2/2, 0, x2/2, z2, xy_prap.getCurrentSlide());
		yZLine = new ReferenceLine(CutSurface.SAGITTAL, y1, z1, y2, z2, xy_prap.getCurrentSlide());
		xy_prap.setReferenceLineMPR(this);
		xz_prap.setReferenceLineMPR(this);
		yz_prap.setReferenceLineMPR(this);
	}
	
	public void draw(Graphics g/*Graphics from CanvasGlass*/) {
		xYLine.draw(g);
		xZLine.draw(g);
		yZLine.draw(g);
		//show slice line localizer
		if(sliceTarget == CutSurface.AXIAL) {
			showSliceLinesAsLocalizer(CutSurface.AXIAL, CutSurface.CORONAL);
			showSliceLinesAsLocalizer(CutSurface.AXIAL, CutSurface.SAGITTAL);
		}else if(sliceTarget == CutSurface.CORONAL) {
			showSliceLinesAsLocalizer(CutSurface.CORONAL, CutSurface.AXIAL);
			showSliceLinesAsLocalizer(CutSurface.CORONAL, CutSurface.SAGITTAL);
		}else if(sliceTarget == CutSurface.SAGITTAL) {
			showSliceLinesAsLocalizer(CutSurface.SAGITTAL, CutSurface.AXIAL);
			showSliceLinesAsLocalizer(CutSurface.SAGITTAL, CutSurface.CORONAL);
		}
	}
	
	public void setSliceTarget(CutSurface sliceTarget) {
		xYLine.setSliceTarget(false);
		xZLine.setSliceTarget(false);
		yZLine.setSliceTarget(false);
		this.sliceTarget = sliceTarget;
		if(this.sliceTarget == CutSurface.AXIAL) {
			xYLine.setSliceTarget(true);
		}else if(this.sliceTarget == CutSurface.CORONAL) {
			xZLine.setSliceTarget(true);
		}else if(this.sliceTarget == CutSurface.SAGITTAL) {
			yZLine.setSliceTarget(true);
		}
	}
	
	void showSliceLinesAsLocalizer(CutSurface sliceTarget, CutSurface sub) {
		if(sliceTarget == CutSurface.AXIAL) {
			if(sub == CutSurface.CORONAL) {
				// for loop
				// get slice plane
				// 
				List<Point2D> loca_geo = xy_prap.calcLocalizer(sliceLineStrokeWidth, x2, null, null, null, x1, sliceLineStrokeWidth, null, null, null);
				xz_prap.getCurrentSlide().drawLocalizer(loca_geo);
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
	
	public ReferenceLine referenceLineFrom(Praparat pp) {
		Praparat xy = mprWin.getPraparatAt(CutSurface.AXIAL);
		Praparat xz = mprWin.getPraparatAt(CutSurface.CORONAL);
		Praparat yz = mprWin.getPraparatAt(CutSurface.SAGITTAL);
		if(pp == xy) {
			return xYLine;
		}else if(pp == xz) {
			return xZLine;
		}else if(pp == yz){
			return yZLine;
		}else {
			return null;
		}
	}
	
	public ReferenceLine referenceLineFrom(CutSurface surface) {
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
	
	public ReferenceLine xYLine() {
		return xYLine;
	}
	
	public ReferenceLine xZLine() {
		return xZLine;
	}
	
	public ReferenceLine yZLine() {
		return yZLine;
	}
	
	void updateResliceLineState() {
		if(xYLine == null || xZLine == null || yZLine == null) {
			initLines();
		}
		updateXYLine();
		//TODO 20240924
//		updateXZLine();
//		updateYZLine();
	}
	
	public void updateXYLine() {
		xYLine.setThickness(mprWin.getSliceThickness());
		xYLine.setGap(mprWin.getSliceGap());
		xYLine.setNumOfSlice(mprWin.getNumberOfSlices());
		xYLine.createSliceLinesWithOffScreenCoordinates();
		if(xYLine.getSlideGlass() != null) {
			xYLine.getSlideGlass().repaintCanvasGlass();
		}
	}
	
	public void updateXZLine() {
		xZLine.setThickness(mprWin.getSliceThickness());
		xZLine.setGap(mprWin.getSliceGap());
		xZLine.setNumOfSlice(mprWin.getNumberOfSlices());
		xZLine.createSliceLinesWithOffScreenCoordinates();
	}
	
	public void updateYZLine() {
		yZLine.setThickness(mprWin.getSliceThickness());
		yZLine.setGap(mprWin.getSliceGap());
		yZLine.setNumOfSlice(mprWin.getNumberOfSlices());
		yZLine.createSliceLinesWithOffScreenCoordinates();
	}
	
	
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
		int start_xyY = find_xyY_Position(xy, yz, yZLine.x1d, yZLine.y1d);
		int end_xyY = find_xyY_Position(xy, yz, yZLine.x2d, yZLine.y2d);
		Point2d lu = new Point2d(xYLine.x1d, start_xyY);
		Point2d rl = new Point2d(xYLine.x2d, end_xyY);
		return new Point2d[] { lu, rl };
	}
	
	private Point2d[] getXZLeftUpperAndRightLowerOnXZ_Prap() {
		Praparat xy_prap = mprWin.getPraparatAt(CutSurface.AXIAL);
		Praparat xz_prap = mprWin.getPraparatAt(CutSurface.CORONAL);
		ImagePlus xy = xy_prap.getCurrentSlide().getOriginalImage();
		ImagePlus xz = xz_prap.getCurrentSlide().getOriginalImage();
		
		int start_xzX = find_xzX_Position(xz, xy, xYLine.x1d, xYLine.y1d);
		int end_xzX = find_xzX_Position(xz, xy, xYLine.x2d, xYLine.y2d);
		
		Point2d lu = new Point2d(start_xzX, xZLine.y1d);
		Point2d rl = new Point2d(end_xzX, xZLine.y2d);
		return new Point2d[] {lu,rl};
	}
	
	private Point2d[] getYZLeftUpperAndRightLowerOnYZ_Prap() {
		Praparat xz_prap = mprWin.getPraparatAt(CutSurface.CORONAL);
		Praparat yz_prap = mprWin.getPraparatAt(CutSurface.SAGITTAL);
		ImagePlus xz = xz_prap.getCurrentSlide().getOriginalImage();
		ImagePlus yz = yz_prap.getCurrentSlide().getOriginalImage();
		
		int start_yzY = find_yzY_Position(yz, xz, xZLine.x1d, xZLine.y1d);
		int end_yzY = find_yzY_Position(yz, xz, xZLine.x2d, xZLine.y2d);
		
		Point2d lu = new Point2d(yZLine.x1d, start_yzY);
		Point2d rl = new Point2d(yZLine.x2d, end_yzY);
		return new Point2d[] {lu,rl};
	}
	
	public double getAngleXY() {
		return xYLine.getAngle();
	}
	
	public double getAngleXZ() {
		// default vertical line angle is -90.
		return xZLine.getAngle();
	}
	
	public double getAngleYZ() {
		return yZLine.getAngle();
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
	
	
	private GeneralPath rectPath(ReferenceLine refLine, Point2d leftUpper, Point2d rightLower) {
		GeneralPath rect = new GeneralPath();
		/*
		 * keep moveTo + lineTo pair. because, see,  refLine.toScreenCoordinates()
		 */
		refLine.addSliceLine(rect, (float)leftUpper.x,(float)leftUpper.y, (float)rightLower.x,(float)leftUpper.y);
		refLine.addSliceLine(rect, (float)rightLower.x,(float)leftUpper.y, (float)rightLower.x,(float)rightLower.y);
		refLine.addSliceLine(rect, (float)rightLower.x,(float)rightLower.y, (float)leftUpper.x,(float)rightLower.y);
		refLine.addSliceLine(rect, (float)leftUpper.x,(float)rightLower.y, (float)leftUpper.x,(float)leftUpper.y);
		return rect;
	}
	
	public void drawRect(ReferenceLine refLine, Graphics g) {
		Graphics2D g2d = (Graphics2D) g;
		BasicStroke bs2 = new BasicStroke(sliceLineStrokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1.0f, new float[] {6}, 0);
		g2d.setStroke(bs2);
		if(refLine.getPlane() == CutSurface.AXIAL) {
			Point2d[] xyPs = getXYLeftUpperAndRightLowerOnXY_Prap();
			if(xyPs == null) {
				return;
			}
			GeneralPath xyRect = rectPath(refLine, xyPs[0],xyPs[1]);
			xyRect = xYLine.toScreenCoordinates(xyRect);
			g2d.setColor(xyColor);
			g2d.draw(xyRect);
		}else if(refLine.getPlane() == CutSurface.CORONAL) {
			Point2d[] xzPs = getXZLeftUpperAndRightLowerOnXZ_Prap();
			GeneralPath xzRect = rectPath(refLine, xzPs[0],xzPs[1]);
			xzRect = xZLine.toScreenCoordinates(xzRect);
			g2d.setColor(xzColor);
			g2d.draw(xzRect);
		}else if(refLine.getPlane() == CutSurface.SAGITTAL) {
			Point2d[] yzPs = getYZLeftUpperAndRightLowerOnYZ_Prap();
			GeneralPath yzRect = rectPath(refLine, yzPs[0],yzPs[1]);
			yzRect = yZLine.toScreenCoordinates(yzRect);
			g2d.setColor(yzColor);
			g2d.draw(yzRect);
		}
	}
	
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
