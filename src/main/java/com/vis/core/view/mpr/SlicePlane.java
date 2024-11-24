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

import org.joml.Vector3d;

import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;

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

	public Vector3d[] getCubeVerticesInOffScreenPixelUnit() {
		Vector3d[] vers = new Vector3d[8];
		Vector3d voxelSize = geo.getVoxelSpacing();// row↓, col→ and spacingZ↗
		for (int i = 0; i < 8; i++) {
			Vector3d v = cubeVertices[i];
			vers[i] = new Vector3d(v.x / voxelSize.x, v.y / voxelSize.y, v.z / voxelSize.z);
		}
		return vers;
	}

	double[] getCubeCenter() {
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

		if (angle < 0.001) {
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
		// shuft ipp and update vertices.
		double sx = shiftX * geo.getVoxelSpacing().x;
		double sy = shiftY * geo.getVoxelSpacing().y;
		double sz = shiftZ * geo.getVoxelSpacing().z;
//		for(int i =0; i< 8; i++) {
//			Vector3d v = cubeVertices[i];
//			cubeVertices[i] = new Vector3d(v.x + sx, v.y + sy, v.z + sz);
//		}
		Vector3d ipp = geo.getTLHC();
		ipp.x = ipp.x + sx;
		ipp.y = ipp.y + sy;
		ipp.z = ipp.z + sz;
		geo.setImagePositionPatient(ipp);
		initVertices();
	}
}
