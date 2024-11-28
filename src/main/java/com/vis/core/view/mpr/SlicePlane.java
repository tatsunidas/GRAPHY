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

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector2d;
import org.joml.Vector3d;

import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;

/**
 * 
 * @author tatsunidas
 *
 */
public class SlicePlane {

	GeometryOfSlice geo;
	Vector3d[] cubeVertices;

	public SlicePlane(int rows, /* rows in slice */
			int cols, /* cols in slice */
			double[] iop, double[] ipp, double[] voxelSize, Double sliceThickness) {
		// double[] iop, double[] ipp, double[] voxelXYZ, double sliceThickness, int w,
		// int h, int s)
		this(new GeometryOfSlice(iop, ipp, voxelSize, sliceThickness, cols, rows, 1));
	}

	public SlicePlane(GeometryOfSlice geo) {
		this.geo = geo;
		initVertices();
	}

	private void initVertices() {
		/*
		 * You need test. is these vertices corrects ?
		 */
		cubeVertices = LocalizerPoster.getCornersOfSourceCubeInSourceSpace(geo);
	}

	public Vector3d[] getCubeVerticesInOffScreenWorldCoordinate() {
		return cubeVertices;
	}

	public Vector3d[] getCubeVerticesInOffScreenPixelUnit(Vector3d[] rcsCoords, Vector3d[] ippOfEachPlane, /*
																											 * 各座標が属する面のIPP
																											 */
			double[] iop, Vector3d pixelSpacingInReference/* ippの取得に使用したボリュームのvoxelSize */) {

		Vector3d[] vers = new Vector3d[rcsCoords.length];
		for (int i = 0; i < rcsCoords.length; i++) {
			Vector3d v = rcsCoords[i];
			vers[i] = toPixelCoordinates(v, ippOfEachPlane[i], iop, pixelSpacingInReference);
		}
		return vers;
	}

	public Vector3d toPixelCoordinates(Vector3d rcsCoord, Vector3d ippOfPlane, /* 各座標が属する面のIPP */
			double[] iop, Vector3d pixelSpacing/* ippの取得に使用したボリュームのvoxelSize */) {

		if (ippOfPlane == null) {
			return null;
		}

		Vector3d P0 = ippOfPlane;
		Vector3d Rc = new Vector3d(iop[0], iop[1], iop[2]); // Row vector
		Vector3d Rr = new Vector3d(iop[3], iop[4], iop[5]); // Column vector

		// Compute slice normal vector
		Vector3d Rs = new Vector3d();
		Rc.cross(Rr, Rs); // Rs = Rc × Rr

		// Compute pixel coordinates
		Vector3d diff = new Vector3d(rcsCoord);
		diff.sub(P0); // diff = rcsPoint - P0

		double u = diff.dot(Rc) / pixelSpacing.y;
		double v = diff.dot(Rr) / pixelSpacing.x;
		double w = diff.dot(Rs) / pixelSpacing.z;

		return new Vector3d(u, v, w);
	}

	public Vector3d computeNearestIPP(Vector3d baseIPP, double[] baseIOP, double sliceThickness) {
		Vector3d row = new Vector3d(baseIOP[0], baseIOP[1], baseIOP[2]);
		Vector3d col = new Vector3d(baseIOP[3], baseIOP[4], baseIOP[5]);
		Vector3d normal = PlanarSupport.calculateNormal(row, col);
		return computeNearestIPP(geo.getTLHC(), baseIPP, normal, sliceThickness);
	}

	/**
	 * 
	 * @param virtualIPP
	 * @param baseIPP
	 * @param baseIOP
	 * @param sliceThickness
	 * @return
	 */
	public Vector3d computeNearestIPP(Vector3d virtualIPP, Vector3d baseIPP, double[] baseIOP, double sliceThickness) {
		Vector3d row = new Vector3d(baseIOP[0], baseIOP[1], baseIOP[2]);
		Vector3d col = new Vector3d(baseIOP[3], baseIOP[4], baseIOP[5]);
		Vector3d normal = PlanarSupport.calculateNormal(row, col);
		return computeNearestIPP(virtualIPP, baseIPP, normal, sliceThickness);
	}

	/**
	 * 
	 * @param virtualIPP     : SlicePlane IPP
	 * @param baseIPP        : Reference Volume IPP at 0 slice.
	 * @param sliceNormal    : Reference volume IOP cross product norm.
	 * @param sliceThickness : Reference volume slice thickness.
	 * @return
	 */
	public Vector3d computeNearestIPP(Vector3d virtualIPP, Vector3d baseIPP, Vector3d sliceNormal,
			double sliceThickness) {
		Vector3d diff = new Vector3d(virtualIPP).sub(baseIPP);
		double sliceIndex = diff.dot(sliceNormal) / sliceThickness;
		Vector3d nearestIPP = new Vector3d(sliceNormal).mul(sliceIndex * sliceThickness).add(baseIPP);
		return nearestIPP;
	}
	
	/**
	 * In RSC coords.
	 * @return
	 */
	public List<Vector3d> computeVoxelCoordinates() {
		Vector3d ipp = geo.getTLHC();
		Vector3d rowVector = geo.getRow();
		Vector3d colVector = geo.getColumn();
		double pixelSpacingX = geo.getVoxelSpacing().y;
		double pixelSpacingY = geo.getVoxelSpacing().x;
		double width = geo.getDimensions().y;
		double height = geo.getDimensions().x;
		return computeVoxelCoordinates(ipp, rowVector, colVector, pixelSpacingX, pixelSpacingY, width, height);
	}
	
	/**
	 * In RSC coords.
	 * @return
	 */
	public List<Vector3d> computeVoxelCoordinates(Vector3d ipp, Vector3d rowVector, Vector3d colVector,
			double pixelSpacingX, double pixelSpacingY, double width, double height) {
		List<Vector3d> voxelCoordinates = new ArrayList<>();
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				// calculate with RCS coordinates
				Vector3d voxelCoord = new Vector3d(ipp).add(new Vector3d(rowVector).mul(j * pixelSpacingX))
						.add(new Vector3d(colVector).mul(i * pixelSpacingY));
				voxelCoordinates.add(voxelCoord);
			}
		}
		return voxelCoordinates;
	}
	
	public List<Vector3d> computeVoxelCoordinatesInPixelCoords(ImagePlus ref){
		Vector3d baseIPP = new Vector3d(GDicomTools.getImagePositionPatient(ref, 1));
		double[] baseIOP = GDicomTools.getImageOrientationPatient(ref, 1);
		Vector3d row = new Vector3d(baseIOP[0], baseIOP[1], baseIOP[2]);
		Vector3d col = new Vector3d(baseIOP[3], baseIOP[4], baseIOP[5]);
		Vector3d normal = PlanarSupport.calculateNormal(row, col);
		double thickness = GDicomTools.getVoxelDepth(ref);
		List<Vector3d> voxelCoordinates = computeVoxelCoordinates();
		for(Vector3d p : voxelCoordinates) {
			Vector3d diff = new Vector3d(p).sub(baseIPP);
			double sliceIndex = diff.dot(normal) / thickness;
			Vector3d nearestIPP = new Vector3d(normal).mul(sliceIndex * thickness).add(baseIPP);
			Vector2d xy = mapToPixelCoordinates(p, nearestIPP);
			p.x = xy.x;
			p.y = xy.y;
			p.z = sliceIndex;
		}
		return voxelCoordinates;
	}

	public Vector2d mapToPixelCoordinates(Vector3d rcsPoint, Vector3d nearestIPP) {
		return mapToPixelCoordinates(rcsPoint, nearestIPP, geo.getRow(), geo.getColumn(), geo.getVoxelSpacing().y,
				geo.getVoxelSpacing().x);
	}

	/**
	 * 
	 * @param rcs           : Point(with RCS Unit) on SlicePlane
	 * @param nearestIPP    : IPP of the slice to which the specified RCS
	 *                      coordinates belong in the reference volume space (the
	 *                      slice position is not limited to the actual number of
	 *                      slices). Calculated by computeNearestIPP.
	 * @param row           : SlicePlane Row vector
	 * @param col           : SlicePlane Col vector
	 * @param pixelSpacingX : SlicePlane PX
	 * @param pixelSpacingY : SlicePlane PY
	 * @return
	 */
	public Vector2d mapToPixelCoordinates(Vector3d rcsPoint, Vector3d nearestIPP, Vector3d row, Vector3d col,
			double pixelSpacingX, double pixelSpacingY) {
		Vector3d diff = new Vector3d(rcsPoint).sub(nearestIPP);
		double pixelX = diff.dot(row) / pixelSpacingX;
		double pixelY = diff.dot(col) / pixelSpacingY;
		return new Vector2d(pixelX, pixelY);
	}

	/**
	 * Center position with RCS coords
	 * 
	 * @return
	 */
	public double[] getCubeCenter() {
		double[] center = { 0, 0, 0 };
		for (Vector3d vertex : cubeVertices) {
			center[0] += vertex.x;
			center[1] += vertex.y;
			center[2] += vertex.z;
		}
		center[0] /= cubeVertices.length;
		center[1] /= cubeVertices.length;
		center[2] /= cubeVertices.length;
		return center;
	}

	public GeometryOfSlice getGeometryOfSlice() {
		return geo;
	}

	public double[] sizeInRCS() {
		Vector3d dim = geo.getDimensions();// row-col-depth
		Vector3d vsize = geo.getVoxelSpacing();// row-col-depth
		return new double[] { dim.x * vsize.x, dim.y * vsize.y, dim.z * vsize.z };
	}

	/**
	 * Rotate from center of gravity.
	 * 
	 * If the center is set to the center of each slice plane, the slices will
	 * rotate around the center of each slice.
	 * 
	 * If the center is set to the center of the slab, each slice will rotate as the
	 * slab rotates.
	 * 
	 * @param rotateX
	 * @param rotateY
	 * @param rotateZ
	 */
	public void rotateCube(double[] center, double angle, boolean rotateX, boolean rotateY, boolean rotateZ) {

		if (Math.abs(angle) < 0.001) {
			return;
		}

		for (Vector3d vertex : cubeVertices) {
			// move shift to center
			vertex.x -= center[0];
			vertex.y -= center[1];
			vertex.z -= center[2];

			// rotate X in YZ plane
			if (rotateX) {
				double tempY = vertex.y;
				double tempZ = vertex.z;
				vertex.y = tempY * Math.cos(angle) - tempZ * Math.sin(angle);
				vertex.z = tempY * Math.sin(angle) + tempZ * Math.cos(angle);
			}

			// rotate Y in XZ plane
			if (rotateY) {
				double tempX = vertex.x;
				double tempZ = vertex.z;
				vertex.x = tempX * Math.cos(angle) + tempZ * Math.sin(angle);
				vertex.z = -tempX * Math.sin(angle) + tempZ * Math.cos(angle);
			}

			// rotate Z in XY plane
			if (rotateZ) {
				double tempX = vertex.x;
				double tempY = vertex.y;
				vertex.x = tempX * Math.cos(angle) - tempY * Math.sin(angle);
				vertex.y = tempX * Math.sin(angle) + tempY * Math.cos(angle);
			}

			// back to center
			vertex.x += center[0];
			vertex.y += center[1];
			vertex.z += center[2];
		}
		// updateIOP and ipp
		if (rotateX) {
			updateIOPAfterRotateFromCenter(angle, 0, 0);
			updateIPPAfterRotateFromCenter(center, angle, 0, 0);
		}
		if (rotateY) {
			updateIOPAfterRotateFromCenter(0, angle, 0);
			updateIPPAfterRotateFromCenter(center, 0, angle, 0);
		}
		if (rotateZ) {
			updateIOPAfterRotateFromCenter(0, 0, angle);
			updateIPPAfterRotateFromCenter(center, 0, 0, angle);
		}
		initVertices();// re-calculate.
	}

	public void updateIOPAfterRotateFromCenter(double rotateX, double rotateY, double rotateZ) {
		Vector3d row = geo.getRow();
		Vector3d col = geo.getColumn();
		row = PlanarSupport.rotateImageOrientationPatient(row, rotateX, rotateY, rotateZ);
		col = PlanarSupport.rotateImageOrientationPatient(col, rotateX, rotateY, rotateZ);
		geo.setRowVector(row);
		geo.setColumnVector(col);
	}

	public void updateIPPAfterRotateFromCenter(double[] center, double rotateX, double rotateY, double rotateZ) {

		Vector3d ipp = geo.getTLHC();

		double radX = Math.toRadians(rotateX);
		double radY = Math.toRadians(rotateY);
		double radZ = Math.toRadians(rotateZ);

		// shift to center
		double[] relativePosition = new double[3];
		relativePosition[0] = ipp.x - center[0];
		relativePosition[1] = ipp.y - center[1];
		relativePosition[2] = ipp.z - center[2];

		double[][] Rx = { { 1, 0, 0 }, { 0, Math.cos(radX), -Math.sin(radX) }, { 0, Math.sin(radX), Math.cos(radX) } };

		double[][] Ry = { { Math.cos(radY), 0, Math.sin(radY) }, { 0, 1, 0 }, { -Math.sin(radY), 0, Math.cos(radY) } };

		double[][] Rz = { { Math.cos(radZ), -Math.sin(radZ), 0 }, { Math.sin(radZ), Math.cos(radZ), 0 }, { 0, 0, 1 } };

		double[] rotatedPosition = new double[3];

		// P' = Rz * (Ry * (Rx * P_relative))
		// Rx
		double[] tempPosition = new double[3];
		tempPosition[0] = Rx[0][0] * relativePosition[0] + Rx[0][1] * relativePosition[1]
				+ Rx[0][2] * relativePosition[2];
		tempPosition[1] = Rx[1][0] * relativePosition[0] + Rx[1][1] * relativePosition[1]
				+ Rx[1][2] * relativePosition[2];
		tempPosition[2] = Rx[2][0] * relativePosition[0] + Rx[2][1] * relativePosition[1]
				+ Rx[2][2] * relativePosition[2];

		// Ry
		rotatedPosition[0] = Ry[0][0] * tempPosition[0] + Ry[0][1] * tempPosition[1] + Ry[0][2] * tempPosition[2];
		rotatedPosition[1] = Ry[1][0] * tempPosition[0] + Ry[1][1] * tempPosition[1] + Ry[1][2] * tempPosition[2];
		rotatedPosition[2] = Ry[2][0] * tempPosition[0] + Ry[2][1] * tempPosition[1] + Ry[2][2] * tempPosition[2];

		// Rz
		double[] finalPosition = new double[3];
		finalPosition[0] = Rz[0][0] * rotatedPosition[0] + Rz[0][1] * rotatedPosition[1]
				+ Rz[0][2] * rotatedPosition[2];
		finalPosition[1] = Rz[1][0] * rotatedPosition[0] + Rz[1][1] * rotatedPosition[1]
				+ Rz[1][2] * rotatedPosition[2];
		finalPosition[2] = Rz[2][0] * rotatedPosition[0] + Rz[2][1] * rotatedPosition[1]
				+ Rz[2][2] * rotatedPosition[2];

		// Move back to center.
		ipp.x = finalPosition[0] + center[0];
		ipp.y = finalPosition[1] + center[1];
		ipp.z = finalPosition[2] + center[2];

		geo.setImagePositionPatient(ipp);
	}

	/**
	 * offscreen pixel unit.
	 * 
	 * @param shiftX
	 * @param shiftY
	 * @param shiftZ
	 */
	public void move(int shiftX, int shiftY, int shiftZ) {
		/*
		 * 
		 */
		double min = Double.MAX_VALUE;
		for (double v : PlanarSupport.v2d(geo.getVoxelSpacing())) {
			if (min > v) {
				min = v;
			}
		}
		// shift ipp and update vertices.
//		double sx = shiftX * geo.getVoxelSpacing().x;
//		double sy = shiftY * geo.getVoxelSpacing().y;
//		double sz = shiftZ * geo.getVoxelSpacing().z;
		double sx = shiftX * min;
		double sy = shiftY * min;
		double sz = shiftZ * min;
		Vector3d ipp = geo.getTLHC();
		ipp.x = ipp.x + sx;
		ipp.y = ipp.y + sy;
		ipp.z = ipp.z + sz;
		geo.setImagePositionPatient(ipp);
		initVertices();
	}
}
