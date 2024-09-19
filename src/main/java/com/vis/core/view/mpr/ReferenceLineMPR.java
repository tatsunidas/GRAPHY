package com.vis.core.view.mpr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;

import org.joml.Vector3d;
import org.scijava.vecmath.Point2d;

import com.vis.core.view.D2.roi.ReferenceLine;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;

import ij.ImagePlus;

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
	
	public static void main(String[] args) {
//		String dir = "D:\\Dropbox\\Graphy-WorkSpace2\\graphy-parent\\graphy-resource\\src\\test\\resources\\dicom_samples\\LGG-104\\06-26-2000-MRI Hd wow-05523\\4-Gad Ax T2 Straight-38151";
//		ImagePlus xy = FolderOpener.open(dir);
//		OrthogonalSlice orthoTool = new OrthogonalSlice();
//		ImagePlus xz = orthoTool.cutXZ(xy, 0, 1, true, false);
//		ImagePlus yz = orthoTool.cutYZ(xy, 0, 1, false, false);
//		
//		PlanarSupport psup = new PlanarSupport();
//		Vector3d ippAtPoint0_ax = psup.getNewImagePositionPatient2D(xy, 10, 0, xy.getNSlices());
//		Vector3d ippAtPoint0_cor = psup.getNewImagePositionPatient2D(xz, 10, 0, 1);
//		
//		System.out.println(ippAtPoint0_ax.x()+" "+ippAtPoint0_ax.y()+" "+ippAtPoint0_ax.z);
//		System.out.println(ippAtPoint0_cor.x()+" "+ippAtPoint0_cor.y()+" "+ippAtPoint0_cor.z);
//		
//		ippAtPoint0_ax = psup.getNewImagePositionPatient2D(xy, 0, 10, xy.getNSlices());//change y
//		Vector3d ippAtPoint0_sag = psup.getNewImagePositionPatient2D(yz, 10, 0, 1);//change y
//		
//		System.out.println(ippAtPoint0_ax.x()+" "+ippAtPoint0_ax.y()+" "+ippAtPoint0_ax.z);
//		System.out.println(ippAtPoint0_sag.x()+" "+ippAtPoint0_sag.y()+" "+ippAtPoint0_sag.z);
		
		ij.gui.Line h = new ij.gui.Line(0, 255, 512, 255);//0
		System.out.println(h.getAngle());
		ij.gui.Line v = new ij.gui.Line(255, 0, 255, 512);//-90
		System.out.println(v.getAngle());
		
	}
	
	MPRViewerWindow mprWin;
	public ReferenceLine xYLine;//horizontal to x, vertical to y, on XY.
	public ReferenceLine xZLine;//horizontal to z, vertical to x, on XZ.
	public ReferenceLine yZLine;//horizontal to y, vertical to z, on YZ.
	
	Color xyColor = Color.RED;
	Color xzColor = Color.BLUE;
	Color yzColor = Color.GREEN;
	Color center_support_line_color = Color.WHITE;
	
	boolean antiAlias = true;
	int sliceLineStrokeWidth = 1;
	
	public ReferenceLineMPR(MPRViewerWindow mprWin) {
		this.mprWin = mprWin;
		initLines();
	}
	
	@SuppressWarnings("serial")
	private void initLines() {
		Praparat xy_prap = mprWin.getPraparatAt(CutSurface.AXIAL);
		ImagePlus xy = mprWin.xyImage();
		xYLine = new ReferenceLine(CutSurface.AXIAL, 0, xy.getHeight()/2-1, xy.getWidth()-1, xy.getHeight()/2-1, xy_prap.getCurrentSlide()) {
			@Override
			public void draw(Graphics g) {
//				drawXY(this, g);//TODO 
			}
			
			/**
			 * This specification does not allow the XYZ cube to be rotated and sliced, so the line should not be rotated unless it is the main plane.
			 */
			@Override
			public void mouseDrag(int sx, int sy, int flags) {
				if(mprWin.getSrcSurface() == CutSurface.AXIAL) {
					super.mouseDrag(sx, sy, flags);
				}
			}
		};
		Praparat xz_prap = mprWin.getPraparatAt(CutSurface.CORONAL);
		ImagePlus xz = mprWin.xzImage();;//xz_prap.getCurrentSlide().getOriginalImage();
		xZLine = new ReferenceLine(CutSurface.CORONAL, xz.getWidth()/2-1, 0, xz.getWidth()/2-1, xz.getHeight()-1, xz_prap.getCurrentSlide()) {
			@Override
			public void draw(Graphics g) {
//				drawXZ(this, g);
			}
			@Override
			public void mouseDrag(int sx, int sy, int flags) {
				if(mprWin.getSrcSurface() == CutSurface.CORONAL) {
					super.mouseDrag(sx, sy, flags);
				}
			}
		};
		Praparat yz_prap = mprWin.getPraparatAt(CutSurface.SAGITTAL);
		ImagePlus yz = mprWin.yzImage();
		yZLine = new ReferenceLine(CutSurface.SAGITTAL, 0, yz.getHeight()/2-1, yz.getWidth()-1, yz.getHeight()/2-1, yz_prap.getCurrentSlide()) {
			@Override
			public void draw(Graphics g) {
				drawYZ(this, g);
			}
			@Override
			public void mouseDrag(int sx, int sy, int flags) {
				if(mprWin.getSrcSurface() == CutSurface.SAGITTAL) {
					super.mouseDrag(sx, sy, flags);
				}
			}
		};
		xy_prap.setReferenceLine(xYLine);
		xz_prap.setReferenceLine(xZLine);
		yz_prap.setReferenceLine(yZLine);
		
//		xy_prap.getCurrentSlide().setViewer2DToolType(Viewer2DToolBar.LineRoi);
//		xz_prap.getCurrentSlide().setViewer2DToolType(Viewer2DToolBar.LineRoi);
//		yz_prap.getCurrentSlide().setViewer2DToolType(Viewer2DToolBar.LineRoi);
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
		updateXZLine();
		updateYZLine();
		mprWin.repaint();
	}
	
	public void updateXYLine() {
		SlideGlass sg = mprWin.getPraparatAt(CutSurface.AXIAL).getCurrentSlide();
		xYLine.setSlideGlass(sg);
		if(mprWin.getSrcSurface() == CutSurface.AXIAL) {
			xYLine.setThickness(mprWin.getSliceThickness());
			xYLine.setGap(mprWin.getSliceGap());
			xYLine.setNumOfSlice(mprWin.getNumberOfSlices());
			xYLine.createSliceLinesWithOffScreenCoordinates();
		}
	}
	
	public void updateXZLine() {
		SlideGlass sg = mprWin.getPraparatAt(CutSurface.CORONAL).getCurrentSlide();
		xZLine.setSlideGlass(sg);
		if(mprWin.getSrcSurface() == CutSurface.CORONAL) {
			xZLine.setThickness(mprWin.getSliceThickness());
			xZLine.setGap(mprWin.getSliceGap());
			xZLine.setNumOfSlice(mprWin.getNumberOfSlices());
			xZLine.createSliceLinesWithOffScreenCoordinates();
		}
	}
	
	public void updateYZLine() {
		SlideGlass sg = mprWin.getPraparatAt(CutSurface.SAGITTAL).getCurrentSlide();
		yZLine.setSlideGlass(sg);
		if(mprWin.getSrcSurface() == CutSurface.SAGITTAL) {
			yZLine.setThickness(mprWin.getSliceThickness());
			yZLine.setGap(mprWin.getSliceGap());
			yZLine.setNumOfSlice(mprWin.getNumberOfSlices());
			yZLine.createSliceLinesWithOffScreenCoordinates();
		}
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
	
	private void drawXY(ReferenceLine refLine, Graphics g, SlideGlass sg){
		drawHandles(refLine, g);
		drawRect(refLine, g);
		Graphics2D g2d = (Graphics2D) g;
		if(refLine.getSliceLines() != null && mprWin.getSrcSurface()==CutSurface.AXIAL) {
			GeneralPath sliceLines_ = refLine.toScreenCoordinates(refLine.getSliceLines());
			if (sliceLines_ != null) {
				g2d.setColor(xyColor);
				BasicStroke bs3 = new BasicStroke(sliceLineStrokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
						1.0f, new float[] { 6 }, 0);
				g2d.setStroke(bs3);
				g2d.draw(sliceLines_);
			}
		}
	}
	
	private void drawXZ(ReferenceLine refLine, Graphics g, SlideGlass sg){
		drawHandles(refLine, g);
		drawRect(refLine, g);
		Graphics2D g2d = (Graphics2D) g;
		if(refLine.getSliceLines() != null && mprWin.getSrcSurface()==CutSurface.CORONAL) {
			GeneralPath sliceLines_ = refLine.toScreenCoordinates(refLine.getSliceLines());
			if (sliceLines_ != null) {
				g2d.setColor(xzColor);
				BasicStroke bs3 = new BasicStroke(sliceLineStrokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
						1.0f, new float[] { 6 }, 0);
				g2d.setStroke(bs3);
				g2d.draw(sliceLines_);
			}
		}
	}
	
	private void drawYZ(ReferenceLine refLine, Graphics g){
		drawHandles(refLine, g);
		drawRect(refLine, g);
		Graphics2D g2d = (Graphics2D) g;
		if(refLine.getSliceLines() != null && mprWin.getSrcSurface()==CutSurface.SAGITTAL) {
			GeneralPath sliceLines_ = refLine.toScreenCoordinates(refLine.getSliceLines());
			if (sliceLines_ != null) {
				g2d.setColor(yzColor);
				BasicStroke bs3 = new BasicStroke(sliceLineStrokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
						1.0f, new float[] { 6 }, 0);
				g2d.setStroke(bs3);
				g2d.draw(sliceLines_);
			}
		}
	}
	
	private void drawHandles(ReferenceLine refLine, Graphics g) {
		Color color = null;
		if(refLine.getPlane() == CutSurface.AXIAL) {
			color = xyColor;
		}else if(refLine.getPlane() == CutSurface.CORONAL) {
			color = xzColor;
		}else if(refLine.getPlane() == CutSurface.SAGITTAL) {
			color = yzColor;
		}
		boolean isActiveOverlayRoi = refLine.isActiveOverlayRoi();
		if (isActiveOverlayRoi) {
			if (color == Color.cyan) {
				//do nothing, go with plane color.
			}else {
				color = Color.cyan;
			}
		}
		
		//TODO 20240820
//		int sx1 = (int)(slide.screenXD(refLine.x1d));
//		int sy1 = (int)(slide.screenYD(refLine.y1d));
//		int sx2 = (int)(slide.screenXD(refLine.x2d));
//		int sy2 = (int)(slide.screenYD(refLine.y2d));
//		int sx3 = sx1 + (int)((sx2 - sx1) / 2.);
//		int sy3 = sy1 + (int)((sy2 - sy1) / 2.);
//		//rotation handle
//		int sx4 = sx1 + (int)((sx2 - sx1) / 5.);// 1/5 location
//		int sy4 = sy1 + (int)((sy2 - sy1) / 5.);
//		int sx5 = sx1 + (int)((sx2 - sx1) - (sx2 - sx1) / 5.);
//		int sy5 = sy1 + (int)((sy2 - sy1) - (sy2 - sy1) / 5.);
//		
//		Graphics2D g2d = (Graphics2D) g;
//		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//	            antiAlias?RenderingHints.VALUE_ANTIALIAS_ON:RenderingHints.VALUE_ANTIALIAS_OFF);
//
//		//draw main reference line
//		g.setColor(color);
//		g.drawLine(sx1, sy1, sx2, sy2);//reflect active overlay color
//		//draw virtical line
//		GeneralPath verLine_ = refLine.toScreenCoordinates(refLine.getCrossVerticalLine(refLine.x1d, refLine.y1d, refLine.x2d, refLine.y2d));
//		g2d.setColor(center_support_line_color);
//		BasicStroke bs2 = new BasicStroke(sliceLineStrokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1.0f, new float[] {6}, 0);
//		g2d.setStroke(bs2);
//		g2d.draw(verLine_);
//		
//		
//		//set handles
//		refLine.drawHandle(g, sx1, sy1);
//		refLine.drawHandle(g, sx2, sy2);
//		//middle handle
//		refLine.drawHandle(g, sx3, sy3);
//		//rotate handle
//		refLine.drawHandle(g, sx4, sy4);
//		refLine.drawHandle(g, sx5, sy5);
		
	}
}
