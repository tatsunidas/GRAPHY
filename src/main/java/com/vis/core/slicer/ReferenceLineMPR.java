package com.vis.core.slicer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.joml.Vector3d;

import com.vis.core.log.Log;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.core.view.D2.ui.orientation.SlicePlane;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.measure.Calibration;

/**
 * Main lines for each plane; X axis line (horizontal to x on XY) is main line
 * in AXIAL plane. Y axis line (horizontal to y on YZ) is main line on SAGITAL
 * plane. Z axis line (horizontal to z on XZ) is main line on CORONAL plane.
 * 
 * @author tatsunidas
 *
 */
public class ReferenceLineMPR {

	SlicerWindow parent;

	final Praparat xy_prap;
	final ImagePlus xy;
	final Praparat xz_prap;
	final ImagePlus xz;
	final Praparat yz_prap;
	final ImagePlus yz;

	final Calibration calXY;
	final Calibration calXZ;
	final Calibration calYZ;

	public CenterPositionLine xYCenterLine;// horizontal to x, vertical to y, on XY.
	public CenterPositionLine xZCenterLine;// horizontal to z, vertical to x, on XZ.
	public CenterPositionLine yZCenterLine;// horizontal to y, vertical to z, on YZ.
	public CenterPositionLine currentCenterLine = null;

	Color xyColor = Color.RED;// X coordinates color
	Color xzColor = Color.BLUE;// Z coordinates color
	Color yzColor = Color.GREEN;// Y coordinates color

	boolean antiAlias = true;
	int sliceLineStrokeWidth = 1;

	CutSurface currentTarget = CutSurface.AXIAL;

	Slab slab;// reference slices

	Double currentFovW = -1.;// mm
	Double currentFovH = -1.;// mm
	Double currentThickness = -1.;// mm
	Double currentGap = -1.;// mm
	Integer currentNumOfSlice = -1;

	public int state = 3;

	final int NORMAL = RoiObj.NORMAL;// 3
	final int MOVING = RoiObj.MOVING;// 1
	final int MOVING_LINE = RoiObj.MOVING_HANDLE;// 4, for line move.
	final int ROTATE = 5;

	public ReferenceLineMPR(SlicerWindow mprWin) {
		this.parent = mprWin;
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
		// set to center of volumes
		int axX1 = (xy.getWidth() / 2) - 10 - 1;
		int axX2 = (xy.getWidth() / 2) + 10 - 1;
		int axY = (xy.getHeight() / 2) - 1;
		xYCenterLine = new CenterPositionLine(CutSurface.AXIAL, axX1, axY, axX2, axY, xy_prap.getCurrentSlide());
		int corX = (xz.getWidth() / 2) - 1;
		int corY1 = (xz.getHeight() / 2) - 10 - 1;
		int corY2 = (xz.getHeight() / 2) + 10 - 1;
		xZCenterLine = new CenterPositionLine(CutSurface.CORONAL, corX, corY1, corX, corY2, xz_prap.getCurrentSlide());
		int sagX1 = (yz.getWidth() / 2) - 10 - 1;
		int sagX2 = (yz.getWidth() / 2) + 10 - 1;
		int sagY = (yz.getHeight() / 2) - 1;
		yZCenterLine = new CenterPositionLine(CutSurface.SAGITTAL, sagX1, sagY, sagX2, sagY, yz_prap.getCurrentSlide());
		xy_prap.setReferenceLineMPR(this);
		xz_prap.setReferenceLineMPR(this);
		yz_prap.setReferenceLineMPR(this);
	}

	public void draw(Graphics g/* Graphics from CanvasGlass */, String cutSurfaceName) {
		if (cutSurfaceName.equals("XY")) {
			xYCenterLine.draw(g);
		} else if (cutSurfaceName.equals("XZ")) {
			xZCenterLine.draw(g);
		} else if (cutSurfaceName.equals("YZ")) {
			yZCenterLine.draw(g);
		}
		showSliceLinesAsLocalizer(g, cutSurfaceName);
	}

	public void setSliceTarget(CutSurface sliceTarget) {
		this.currentTarget = sliceTarget;
	}

	public int getState() {
		return state;
	}

//	Graphics2D screenCoordinate(Graphics g, SlideGlass sg) {
//	    // 描画設定が他に波及しないようにGraphicsオブジェクトを複製する
//	    Graphics2D g2d = (Graphics2D)g.create();
//	    
//	    // 真っさらなものではなく、現在の変換行列（HiDPI情報込み）を取得する
//	    AffineTransform aTx = g2d.getTransform();
//	    
//	    double mag = sg.getMagnification();
//	    double scaleXY[] = sg.getScaleFactor();
//	    Point offset = sg.getDisplayImageOriginXY();
//	    
//	    // First, translate image origin without mag and component scale.
//	    aTx.translate(offset.x, offset.y);
//	    // Second, scale Roi graphics
//	    aTx.scale(mag*scaleXY[0], mag*scaleXY[1]);
//	    
//	    // 既存の行列に上乗せしたものをセット
//	    g2d.setTransform(aTx);
//	    return g2d;
//	}

	Graphics2D screenCoordinate(Graphics g, SlideGlass sg) {
		// ★ 修正: CanvasGlass側で既にAffineTransform(ズーム・パン等)が
		// 適用された Graphics が渡されてくるため、ここで再度スケールやオフセットを
		// 加算すると「二重変換」になって位置がズレます。
		// そのため、そのままの Graphics2D をコピーして返すだけにします。
		Graphics2D g2d = (Graphics2D) g.create();
		return g2d;
	}

	void showSliceLinesAsLocalizer(Graphics g, String cutSurfaceName) {
		if (slab == null || slab.getSlicePlanes().size() < 1) {
			Log.logger.fine("SlicePlanes are null");
			return;
		}
		CutSurface surface = null;
		if (cutSurfaceName.equals("XY")) {
			surface = CutSurface.AXIAL;
		} else if (cutSurfaceName.equals("XZ")) {
			surface = CutSurface.CORONAL;
		} else if (cutSurfaceName.equals("YZ")) {
			surface = CutSurface.SAGITTAL;
		} else {
			// do nothing
			return;
		}

		// debug
//		if(surface != CutSurface.AXIAL) {
//			return;
//		}

		Praparat pp = parent.getPraparatAt(surface);
		List<SlicePlane> slices = slab.getSlicePlanes();
		SlideGlass sg = pp.getCurrentSlide();
		
		Graphics2D g2 = screenCoordinate(g, sg);
		GeneralPath path = new GeneralPath();
		for (SlicePlane sp : slices) {
			GeometryOfSlice geo = sp.getGeometryOfSlice();
			List<Point2D> loca_geo = pp.calcLocalizer(geo);
			if (loca_geo != null) {
				addSliceLine(path, loca_geo);
			}
		}
		g2.setColor(axisColor());
		g2.setStroke(new BasicStroke(1));
		g2.draw(path);
		g2.dispose();
	}

	private void addSliceLine(GeneralPath path, List<Point2D> localizerGeo) {
		Point2D p0_leftUpper = localizerGeo.get(0);
		Point2D p1_rightUpper = localizerGeo.get(1);
		Point2D p2_rightLower = localizerGeo.get(2);
		Point2D p3_leftLower = localizerGeo.get(3);
		path.moveTo(p0_leftUpper.getX(), p0_leftUpper.getY());
		path.lineTo(p1_rightUpper.getX(), p1_rightUpper.getY());
		path.lineTo(p2_rightLower.getX(), p2_rightLower.getY());
		path.lineTo(p3_leftLower.getX(), p3_leftLower.getY());
		path.lineTo(p0_leftUpper.getX(), p0_leftUpper.getY());
	}

	public void mouseDragged(Praparat pp, int dragSX, int dragSY, int flags) {
		if (slab == null || slab.size() < 1) {
			return;
		}
		SlideGlass sg = pp.getCurrentSlide();

		Point p = null;
		Point pl = null;
		try {
			p = sg.offScreenCoordinate(dragSX, dragSY);
			pl = sg.offScreenCoordinate(sg.lastDraggedX, sg.lastDraggedY);
		} catch (NoninvertibleTransformException nte) {
			nte.printStackTrace();
			Log.logger.log(Level.SEVERE, "Can not translate offscreen coordinates...");
		}

		int offX = p.x;
		int offY = p.y;
		int lastOffX = pl.x;
		int lastOffY = pl.y;
		int shiftX = offX - lastOffX, shiftY = offY - lastOffY;
		if (state == MOVING) {
			if (pp.getName().equals("XY")) {
				CenterPositionLine cpl = centerPositionLineFrom(CutSurface.AXIAL);
				cpl.mouseDrag(offX, offY, flags); // dragSX, dragSY から offX, offY に変更
				slab.moveSlab(shiftX, shiftY, 0);
			} else if (pp.getName().equals("XZ")) {
				CenterPositionLine cpl = centerPositionLineFrom(CutSurface.CORONAL);
				cpl.mouseDrag(offX, offY, flags); // 同上
				slab.moveSlab(shiftX, 0, -1 * shiftY);
			} else if (pp.getName().equals("YZ")) {
				CenterPositionLine cpl = centerPositionLineFrom(CutSurface.SAGITTAL);
				cpl.mouseDrag(offX, offY, flags); // 同上
				slab.moveSlab(0, shiftX, -1 * shiftY);
			} else {
				return;
			}
		} else if (state == ROTATE) {
			if (pp.getName().equals("XY")) {
				slab.rotateSlab(0, 0, shiftX * 0.5);
			} else if (pp.getName().equals("XZ")) {
				slab.rotateSlab(0, shiftX * 0.5, 0);
			} else if (pp.getName().equals("YZ")) {
				slab.rotateSlab(shiftX * 0.5, 0, 0);
			} else {
				return;
			}
		}
	}

	public void mousePressed(Praparat pp, int dragSX, int dragSY) {
		currentCenterLine = centerPositionLineHereAt(pp, dragSX, dragSY);
		if (currentCenterLine != null) {
			state = MOVING;// slab state
			/*
			 * Line objects can be moved in MOVE_HANDLE mode.
			 */
			// ▼ 追加：オフスクリーン座標に変換して渡す
			SlideGlass sg = pp.getCurrentSlide();
			Point p = null;
			try {
				p = sg.offScreenCoordinate(dragSX, dragSY);
			} catch (Exception e) {
			}
			currentCenterLine.mouseDownInHandle(2/* center */, p.x, p.y);
//			currentCenterLine.mouseDownInHandle(2/*center*/, dragSX, dragSY);
		} else if (isPeripheralArea(pp, dragSX, dragSY)) {
			state = ROTATE;
		} else {
			state = NORMAL;
		}
	}

	public void mouseReleased() {
		state = NORMAL;
		if (currentCenterLine != null) {
			currentCenterLine.setState(NORMAL);
		}
		// re-calculate center line positions
		calculateCenterPositionsAndMove();
	}

	private Color axisColor() {
		if (currentTarget == CutSurface.AXIAL) {
			return xzColor;
		} else if (currentTarget == CutSurface.CORONAL) {
			return yzColor;
		} else if (currentTarget == CutSurface.SAGITTAL) {
			return xyColor;
		}
		return Color.WHITE;
	}

//	public void calculateCenterPositionsAndMove() {
////		// Slabの有無に関わらず、Axial画像(xy)のサイズを基準に中心を割り出す
////		if (xy == null) return;
////		
////		int cx = xy.getWidth() / 2;
////		int cy = xy.getHeight() / 2;
////		
////		// xYCenterLine (AXIAL上のライン: 水平) 幅20の線
////		if (xYCenterLine != null) xYCenterLine.setLocation(cx - 10, cy);
////		
////		// xZCenterLine (CORONAL上のライン: 垂直) 高さ20の線
////		if (xZCenterLine != null) xZCenterLine.setLocation(xz.getWidth() / 2, (xz.getHeight() / 2) - 10);
////		
////		// yZCenterLine (SAGITTAL上のライン: 水平) 幅20の線
////		if (yZCenterLine != null) yZCenterLine.setLocation((yz.getWidth() / 2) - 10, yz.getHeight() / 2);
//	}
	
//	public void calculateCenterPositionsAndMove() {
//		if(slab == null || slab.size()<1) {
//			return;
//		}
//		
//		// 1. Axial上の中心線 (水平線：幅20, 高さ0) -> 左上の座標は (X-10, Y)
//		double[] coordsXY = slab.getCenterOfVolumeInPixelCoords2(xy);
//		CenterPositionLine cXY = centerPositionLineFrom(CutSurface.AXIAL);
//		if (cXY != null) {
//			cXY.setLocation(coordsXY[0] - 10, coordsXY[1]);
//		}
//		
//		// 2. Coronal上の中心線 (垂直線：幅0, 高さ20) -> 左上の座標は (X, Y-10)
//		double[] coordsXZ = slab.getCenterOfVolumeInPixelCoords2(xz);
//		CenterPositionLine cXZ = centerPositionLineFrom(CutSurface.CORONAL);
//		if (cXZ != null) {
//			cXZ.setLocation(coordsXZ[0], coordsXZ[1] - 10);
//		}
//		
//		// 3. Sagittal上の中心線 (水平線：幅20, 高さ0) -> 左上の座標は (X-10, Y)
//		double[] coordsYZ = slab.getCenterOfVolumeInPixelCoords2(yz);
//		CenterPositionLine cYZ = centerPositionLineFrom(CutSurface.SAGITTAL);
//		if (cYZ != null) {
//			cYZ.setLocation(coordsYZ[0] - 10, coordsYZ[1]);
//		}
//	}
	
	public void calculateCenterPositionsAndMove() {
		if(slab == null || slab.size()<1) {
			return;
		}
		
		// Axial上の中心線 (水平線：幅20) -> 左上の座標は (X-10, Y)
		double[] coordsXY = slab.getCenterOfVolumeInPixelCoords2(xy);
		CenterPositionLine cXY = centerPositionLineFrom(CutSurface.AXIAL);
		if (cXY != null) {
			cXY.setLocation(coordsXY[0] - 10, coordsXY[1]);
		}
		
		// Coronal上の中心線 (垂直線：高さ20) -> 左上の座標は (X, Y-10)
		double[] coordsXZ = slab.getCenterOfVolumeInPixelCoords2(xz);
		CenterPositionLine cXZ = centerPositionLineFrom(CutSurface.CORONAL);
		if (cXZ != null) {
			cXZ.setLocation(coordsXZ[0], coordsXZ[1] - 10);
		}
		
		// Sagittal上の中心線 (水平線：幅20) -> 左上の座標は (X-10, Y)
		double[] coordsYZ = slab.getCenterOfVolumeInPixelCoords2(yz);
		CenterPositionLine cYZ = centerPositionLineFrom(CutSurface.SAGITTAL);
		if (cYZ != null) {
			cYZ.setLocation(coordsYZ[0] - 10, coordsYZ[1]);
		}
	}

	public CenterPositionLine centerPositionLineFrom(Praparat pp) {
		Praparat xy = parent.getPraparatAt(CutSurface.AXIAL);
		Praparat xz = parent.getPraparatAt(CutSurface.CORONAL);
		Praparat yz = parent.getPraparatAt(CutSurface.SAGITTAL);
		if (pp == xy) {
			return xYCenterLine;
		} else if (pp == xz) {
			return xZCenterLine;
		} else if (pp == yz) {
			return yZCenterLine;
		} else {
			return null;
		}
	}

	public CenterPositionLine centerPositionLineFrom(CutSurface surface) {
		if (surface == CutSurface.AXIAL) {
			return xYLine();
		} else if (surface == CutSurface.CORONAL) {
			return xZLine();
		} else if (surface == CutSurface.SAGITTAL) {
			return yZLine();
		} else {
			return null;
		}
	}

	public CenterPositionLine centerPositionLineHereAt(Praparat pp, int screenX, int screenY) {
		SlideGlass sg = pp.getCurrentSlide();
		Point p = null;
		try {
			p = sg.offScreenCoordinate(screenX, screenY);
		} catch (NoninvertibleTransformException nte) {
			nte.printStackTrace();
			Log.logger.log(Level.SEVERE, "Can not translate offscreen coordinates...");
		}
		int handle = -1;
		boolean found = false;
		CenterPositionLine cenLine = centerPositionLineFrom(pp);
		cenLine.setActiveOverlayRoi(false);// reset activate
		handle = cenLine.isHandle(p.x, p.y);

		if (handle == 2) {
			cenLine.setActiveOverlayRoi(true);
			found = true;
		} else if (cenLine.contains(p.x, p.y)) {
			cenLine.setActiveOverlayRoi(true);
			found = true;
		}
		if (found) {
			Log.logger.fine("Find center !!");
			return cenLine;
		}
		return null;
	}

	/**
	 * Slabが生成・移動された際に、十字のハンドル(CenterPositionLine)をSlabの中心に追従させます。
	 */
	public void syncHandlesToSlabCenter() {
		if (slab == null || xy == null || xz == null || yz == null) return;
		
		double[] centerArr = slab.getCenterOfVolumeInPixelCoords2(xy);
		double cx = centerArr[0];
		double cy = centerArr[1];
		double cz = centerArr[2]; // Zはスライス番号
		
		// 1. Axial (XY) ハンドルの更新
		if (xYCenterLine != null) {
			double width = xYCenterLine.x2d - xYCenterLine.x1d;
			// ★専用メソッドで座標を更新する
			xYCenterLine.updateCoordinates(cx - width / 2.0, cy, cx + width / 2.0, cy);
		}
		
		// 2. CORONAL (XZ) ハンドルの更新
		if (xZCenterLine != null) {
			int maxZ = xy.getNSlices();
			double corY = xz.getHeight() - 1 - (cz * xz.getHeight() / maxZ);
			double height = xZCenterLine.y2d - xZCenterLine.y1d;
			// ★専用メソッドで座標を更新する
			xZCenterLine.updateCoordinates(cx, corY - height / 2.0, cx, corY + height / 2.0);
		}
		
		// 3. SAGITTAL (YZ) ハンドルの更新
		if (yZCenterLine != null) {
			int maxZ = xy.getNSlices();
			double sagY = yz.getHeight() - 1 - (cz * yz.getHeight() / maxZ);
			double width = yZCenterLine.x2d - yZCenterLine.x1d;
			// ★専用メソッドで座標を更新する
			yZCenterLine.updateCoordinates(cy - width / 2.0, sagY, cy + width / 2.0, sagY);
		}
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
		if (xYCenterLine == null || xZCenterLine == null || yZCenterLine == null) {
			initLines();
		}
		initReslicePlanes();
	}

//	public void initReslicePlanes() {
//		Double fovW = parent.getFOV_W();//mm
//		Double fovH = parent.getFOV_H();//mm
//		Double thickness = parent.getSliceThickness();//mm
//		Double gap = parent.getSliceGap();//mm
//		Integer numOfSlice = parent.getNumberOfSlices();
//		
//		if(Double.isNaN(fovW) || fovW <= 0.) {
//			return;
//		}
//		if(Double.isNaN(fovH) || fovH <= 0.) {
//			return;
//		}
//		if(Double.isNaN(thickness) || thickness <= 0.) {
//			return;
//		}
//		if(Double.isNaN(numOfSlice) || numOfSlice < 1) {
//			return;
//		}
//		if(Double.isNaN(gap) || gap < 0d) {
//			gap = 0d;
//		}
//		
//		//debug
////		if(currentFovW.equals(fovW) && currentFovH.equals(fovH)&& 
////				currentThickness.equals(thickness) && currentGap.equals(gap) && currentNumOfSlice.equals(numOfSlice)) {
////			return;
////		};
//		
//		currentFovW = fovW;
//		currentFovH = fovH;
//		currentThickness = thickness;
//		currentGap = gap;
//		currentNumOfSlice = numOfSlice;
//		
//		ImagePlus refVolume = null;
//		ImagePlus targetVolume = null;
//		
//		//RCS base, with reference volume coordinates(pixel unit).
//		double half_x = 0;
//		double half_y = 0;
//		
//		double[] voxelSize = null;
//		double[] iop = null;
//		
//		if(currentTarget == CutSurface.AXIAL) {
//			//iop from cornal
//			iop = GDicomTools.getImageOrientationPatient(xz, 1);
//			refVolume = xz;
//			targetVolume = xy;
//		}else if(currentTarget == CutSurface.CORONAL) {
//			//iop from sagittal
//			iop = GDicomTools.getImageOrientationPatient(yz, 1);
//			refVolume = yz;
//			targetVolume = xz;
//		}else if(currentTarget == CutSurface.SAGITTAL) {
//			//iop from axial
//			iop = GDicomTools.getImageOrientationPatient(xy, 1);
//			refVolume = xy;
//			targetVolume = yz;
//		}
//		double px = refVolume.getCalibration().pixelWidth;
//		double py = refVolume.getCalibration().pixelHeight;
//		//recon image half dimension in pixel coords
//		half_x = (fovW/2.)/px;
//		half_y = (fovH/2.)/py;
//		voxelSize = new double[] {px,py,thickness};
//		
//		Vector3d[] centers = calcSliceCentersBeforeRotation();
//		if(centers == null) {
//			slab = null;
//			return;
//		}
//		List<SlicePlane> slices = new ArrayList<>();
//		for(int i=0; i<numOfSlice;i++) {
//			addSlicePlane(centers[i], targetVolume, iop, slices, half_x, half_y, voxelSize, thickness);
//		}
//		
//		double rotateX = 0;
//		double rotateY = 0;
//		double rotateZ = 0;
//		if(slab != null) {
//			Vector3d angles = slab.getRotations();
//			rotateX = angles.x;
//			rotateY = angles.y;
//			rotateZ = angles.z;
//		}
//		slab = null;
//		slab = new Slab(slices, gap);
//		slab.rotateSlab(rotateX, rotateY, rotateZ);
//	}

	public void initReslicePlanes() {
		Double fovW = parent.getFOV_W();
		Double fovH = parent.getFOV_H();
		Double thickness = parent.getSliceThickness();
		Double gap = parent.getSliceGap();
		Integer numOfSlice = parent.getNumberOfSlices();

		if (Double.isNaN(fovW) || fovW <= 0.)
			return;
		if (Double.isNaN(fovH) || fovH <= 0.)
			return;
		if (Double.isNaN(thickness) || thickness <= 0.)
			return;
		if (Double.isNaN(numOfSlice) || numOfSlice < 1)
			return;
		if (Double.isNaN(gap) || gap < 0d)
			gap = 0d;

		currentFovW = fovW;
		currentFovH = fovH;
		currentThickness = thickness;
		currentGap = gap;
		currentNumOfSlice = numOfSlice;

		// 1. ターゲットとする断面の正しい ImagePlus と IOP を取得
		ImagePlus targetVolume = null;
		if (currentTarget == CutSurface.AXIAL) {
			targetVolume = xy;
		} else if (currentTarget == CutSurface.CORONAL) {
			targetVolume = xz;
		} else if (currentTarget == CutSurface.SAGITTAL) {
			targetVolume = yz;
		}

		double[] iop = GDicomTools.getImageOrientationPatient(targetVolume, 1);

		// 2. ターゲットのボクセルサイズ（FOVのピクセル換算用）
		double px = targetVolume.getCalibration().pixelWidth;
		double py = targetVolume.getCalibration().pixelHeight;
		double half_x = (fovW / 2.) / px;
		double half_y = (fovH / 2.) / py;
		double[] voxelSize = new double[] { px, py, thickness };

		// 3. 現在のセンターライン（UI）から、3D空間上の中心座標（IPP）を取得
		Vector3d centerIpp = getCenterIppFromLines();

		// 4. 法線ベクトル（スライスの進行方向）を計算
		Vector3d row = new Vector3d(iop[0], iop[1], iop[2]);
		Vector3d col = new Vector3d(iop[3], iop[4], iop[5]);
		Vector3d normal = PlanarSupport.crossProduct(row, col, true);

		// 5. 各スライスの作成
		List<SlicePlane> slices = new ArrayList<>();
		double step = thickness + gap;
		int centerPos = numOfSlice / 2;
		if (numOfSlice % 2 == 0)
			centerPos = (numOfSlice / 2) - 1;

		// 最初のスライスのIPP（法線の逆方向にずらす）
		Vector3d startIpp = new Vector3d(normal).mul(-step * centerPos).add(centerIpp);

		int h = (int) Math.round(half_y * 2);
		int w = (int) Math.round(half_x * 2);

		for (int i = 0; i < numOfSlice; i++) {
			Vector3d currentIpp = new Vector3d(normal).mul(step * i).add(startIpp);

			// 中心座標から、SlicePlaneに必要な原点（左上: TLHC）を逆算
			Vector3d tlhc = PlanarSupport.getNewImagePositionPatient(w, h, voxelSize, iop, currentIpp);

			SlicePlane slice = new SlicePlane(h, w, iop, PlanarSupport.v2d(tlhc), new double[] { px, py, thickness },
					thickness);
			slices.add(slice);
		}

		// 6. 回転の復元と Slab の生成
		double rotateX = 0, rotateY = 0, rotateZ = 0;
		if (slab != null) {
			Vector3d angles = slab.getRotations();
			rotateX = angles.x;
			rotateY = angles.y;
			rotateZ = angles.z;
		}
		slab = new Slab(slices, gap);
		slab.rotateSlab(rotateX, rotateY, rotateZ);
		
		calculateCenterPositionsAndMove();
	}

	/**
	 * 現在のセンターラインのUI座標から、3D空間上の中心座標(IPP)を算出します。
	 */
	private Vector3d getCenterIppFromLines() {
		// 十字線の交点 (x, y) を取得
		double cx = (xYCenterLine.x1d + xYCenterLine.x2d) / 2.0;
		double cy = (xYCenterLine.y1d + xYCenterLine.y2d) / 2.0;

		// 現在の Axial スライス位置を取得
		int sliceZ = xy_prap.getCurrentSlidePos();

		// ★ 修正: 起動時（slabがまだ生成されていない場合）は、
		// UIの状態に関わらずボリュームのちょうど真ん中のスライスをターゲットにする
		if (slab == null) {
			sliceZ = xy.getNSlices() / 2;
		}

		// 指定したピクセル座標 (cx, cy, sliceZ) に対応する 3D空間座標 (IPP) を取得
		Vector3d ipp = PlanarSupport.getNewImagePositionPatient2D(xy, cx, cy, sliceZ + 1);

		if (ipp == null) {
			double[] ippArr = GDicomTools.getImagePositionPatient(xy, 1);
			return new Vector3d(ippArr[0], ippArr[1], ippArr[2]);
		}
		return ipp;
	}

	@SuppressWarnings("unused")
	private java.awt.geom.Point2D[] nextParallelLinePoints(Point2D lineStart, Point2D lineEnd, boolean topSide) {
		Point2D startPoint = lineStart;
		Point2D endPoint = lineEnd;
		double lineAngle = Math.atan2(endPoint.getY() - startPoint.getY(), endPoint.getX() - startPoint.getX());
		double angle;
		double gapInPixel = getGapInPixelUnit();
		double thicknessInPixel = getThicknessInPixelUnit();
		double radians = 180 / Math.PI;
		if (topSide) {
			angle = 90 / radians + lineAngle;
			double topOffsetX = Math.cos(angle) * (gapInPixel + thicknessInPixel);
			double topOffsetY = Math.sin(angle) * (gapInPixel + thicknessInPixel);
			Point2D topStart = new Point2D.Double(startPoint.getX() + topOffsetX, startPoint.getY() + topOffsetY);
			Point2D topEnd = new Point2D.Double(endPoint.getX() + topOffsetX, endPoint.getY() + topOffsetY);
			return new Point2D[] { topStart, topEnd };
		} else {
			angle = -90 / radians + lineAngle;
			double bottomOffsetX = Math.cos(angle) * (gapInPixel + thicknessInPixel);
			double bottomOffsetY = Math.sin(angle) * (gapInPixel + thicknessInPixel);
			Point2D bottomStart = new Point2D.Double(startPoint.getX() + bottomOffsetX,
					startPoint.getY() + bottomOffsetY);
			Point2D bottomEnd = new Point2D.Double(endPoint.getX() + bottomOffsetX, endPoint.getY() + bottomOffsetY);
			return new Point2D[] { bottomStart, bottomEnd };
		}
	}

	private void addSlicePlane(Vector3d center, ImagePlus refVolume, double[] refIop, List<SlicePlane> slices,
			double half_x, double half_y, // in pixel unit
			double[] voxelSize, Double thickness) {
		// pre rotation
		double[] iop = refIop;

		int slicePos = (int) (center.z) + 1;
		if (slicePos < 1)
			slicePos = 1;
		if (slicePos > refVolume.getNSlices())
			slicePos = refVolume.getNSlices();

		Vector3d centerIPP = PlanarSupport.getNewImagePositionPatient2D(refVolume, (int) (center.x), (int) (center.y),
				slicePos);

		int h = (int) Math.round(half_y * 2);
		int w = (int) Math.round(half_x * 2);

		// check out of range center in reference volume...//TODO

		/*
		 * SlicePlane ipp is shifted by half z, because centerline to be located at
		 * slice center position.
		 */
		voxelSize[2] = voxelSize[2] / 2.;
		Vector3d newIPP = PlanarSupport.getNewImagePositionPatient(w, h, voxelSize, iop, centerIPP);

		// back scale
		voxelSize[2] *= 2.;
		SlicePlane slice = new SlicePlane(h, // rows
				w, // cols
				iop, PlanarSupport.v2d(newIPP), voxelSize, thickness);

		slices.add(slice);
	}

	private Double getGapInPixelUnit() {
		Double gap = parent.getSliceGap();
		if (currentTarget == CutSurface.AXIAL) {
			double py = xy.getCalibration().pixelHeight;
			return gap / py;
		} else if (currentTarget == CutSurface.CORONAL) {
			double px = xz.getCalibration().pixelWidth;
			return gap / px;
		} else if (currentTarget == CutSurface.SAGITTAL) {
			double py = yz.getCalibration().pixelHeight;
			return gap / py;
		}
		return Double.NaN;
	}

	private Double getThicknessInPixelUnit() {
		Double t = parent.getSliceThickness();
		if (currentTarget == CutSurface.AXIAL) {
			double py = xy.getCalibration().pixelHeight;
			return t / py;
		} else if (currentTarget == CutSurface.CORONAL) {
			double px = xz.getCalibration().pixelWidth;
			return t / px;
		} else if (currentTarget == CutSurface.SAGITTAL) {
			double py = yz.getCalibration().pixelHeight;
			return t / py;
		}
		return Double.NaN;
	}

	public Slab getSlab() {
		return slab;
	}

	public boolean isPeripheralArea(Praparat pp/* on mouse */, double screenX, double screenY) {
		if (slab == null || slab.getSlicePlanes() == null || slab.getSlicePlanes().size() == 0) {
			return false;
		}
		SlideGlass sg = pp.getCurrentSlide();
		Point p = null;
		try {
			p = sg.offScreenCoordinate(screenX, screenY);
		} catch (NoninvertibleTransformException nte) {
			nte.printStackTrace();
			Log.logger.log(Level.SEVERE, "Can not translate offscreen coordinates...");
		}
		double ox = p.x;
		double oy = p.y;
		int oz = pp.getCurrentSlidePos();// 0 base
		ImagePlus ref = imagePlus(pp.getName());
		Vector3d ippOnMouse = PlanarSupport.getNewImagePositionPatient2D(ref, ox, oy, oz + 1);
		if (ippOnMouse == null) {
			return false;
		}
		return slab.isPointInShrinkedBox(pp, ippOnMouse);
	}

	public boolean isBoundingBox(Praparat pp/* on mouse */, double screenX, double screenY) {
		if (slab == null || slab.getSlicePlanes() == null || slab.getSlicePlanes().size() == 0) {
			return false;
		}
		SlideGlass sg = pp.getCurrentSlide();
		Point p = null;
		try {
			p = sg.offScreenCoordinate(screenX, screenY);
		} catch (NoninvertibleTransformException nte) {
			nte.printStackTrace();
			Log.logger.log(Level.SEVERE, "Can not translate offscreen coordinates...");
		}
		double ox = p.x;
		double oy = p.y;
		int oz = pp.getCurrentSlidePos();// 0 base
		ImagePlus ref = imagePlus(pp.getName());
		Vector3d ippOnMouse = PlanarSupport.getNewImagePositionPatient2D(ref, ox, oy, oz + 1);
		if (ippOnMouse == null) {
			return false;
		}
		return slab.isBoundingBox(pp, ippOnMouse);
	}

	private ImagePlus imagePlus(String name) {
		ImagePlus ref = null;
		if (name.equals("XY")) {
			ref = xy;
		} else if (name.equals("XZ")) {
			ref = xz;
		} else if (name.equals("YZ")) {
			ref = yz;
		}
		return ref;
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

}
