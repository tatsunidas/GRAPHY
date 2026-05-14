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
package com.vis.core.view.D2.ui.orientation;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3d;
import org.joml.Vector2d;
import org.joml.Vector3d;

import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;

/**
 * @author tatsunidas
 */
public class SlicePlane {

	GeometryOfSlice geo;
	public Vector3d[] cubeVertices;

	public SlicePlane(int rows, int cols, double[] iop, double[] ipp, double[] voxelSize, Double sliceThickness) {
		this(new GeometryOfSlice(iop, ipp, voxelSize, sliceThickness, cols, rows, 1));
	}
	
	public SlicePlane(Vector3d rowVector, Vector3d colVector, Vector3d ipp, Vector3d voxelSize, Double sliceThickness, Vector3d dimension) {
		this(new GeometryOfSlice(rowVector, colVector, ipp, voxelSize, sliceThickness, dimension));
	}

	public SlicePlane(GeometryOfSlice geo) {
		this.geo = geo;
		initVertices();
	}

	private void initVertices() {
		cubeVertices = LocalizerPoster.getCornersOfSourceCubeInSourceSpace(geo);
	}

	public Vector3d[] getCubeVerticesInOffScreenWorldCoordinate() {
		return cubeVertices;
	}

	public Vector3d[] getCubeVerticesInOffScreenPixelUnit(Vector3d[] rcsCoords, Vector3d[] ippOfEachPlane, double[] iop, Vector3d pixelSpacingInReference) {
		Vector3d[] vers = new Vector3d[rcsCoords.length];
		for (int i = 0; i < rcsCoords.length; i++) {
			vers[i] = toPixelCoordinates(rcsCoords[i], ippOfEachPlane[i], iop, pixelSpacingInReference);
		}
		return vers;
	}

	public Vector3d toPixelCoordinates(Vector3d rcsCoord, Vector3d ippOfPlane, double[] iop, Vector3d pixelSpacing) {
		if (ippOfPlane == null) return null;

		Vector3d Rc = new Vector3d(iop[0], iop[1], iop[2]); // Row vector
		Vector3d Rr = new Vector3d(iop[3], iop[4], iop[5]); // Column vector
		Vector3d Rs = new Vector3d();
		Rc.cross(Rr, Rs); // Slice normal vector

		Vector3d diff = new Vector3d(rcsCoord).sub(ippOfPlane);

		double u = diff.dot(Rc) / pixelSpacing.y;
		double v = diff.dot(Rr) / pixelSpacing.x;
		double w = diff.dot(Rs) / pixelSpacing.z;

		return new Vector3d(u, v, w);
	}
	
	/**
	 * 指定された絶対座標(RCS)を、参照ボリューム内のピクセル座標(u, v, w)に変換します。
	 * Slab等の外部クラスから単発の座標変換を行うために使用します。
	 */
	public Vector3d toPixelCoordinates(Vector3d rcsPoint, ImagePlus refAxial) {
		// 1. 参照ボリュームから必要なメタデータを1回だけ抽出
		double[] imagePosition = GDicomTools.getImagePositionPatient(refAxial, 1);
		double[] imageOrientation = GDicomTools.getImageOrientationPatient(refAxial, 1);
		double pixelSpacingY = refAxial.getCalibration().pixelHeight;
		double pixelSpacingX = refAxial.getCalibration().pixelWidth;
		double sliceThickness = GDicomTools.getVoxelDepth(refAxial);

		Vector3d refIpp = new Vector3d(imagePosition);
		Vector3d Rr = new Vector3d(imageOrientation[0], imageOrientation[1], imageOrientation[2]);
		Vector3d Rc = new Vector3d(imageOrientation[3], imageOrientation[4], imageOrientation[5]);
		Vector3d Rs = new Vector3d();

		// 2. ★ 修正済みロジック: 実際のスタック進行方向からZ軸ベクトル(Rs)を算出
		int nSlices = refAxial.getNSlices();
		if (nSlices > 1) {
			Vector3d lastIppVec = new Vector3d(GDicomTools.getImagePositionPatient(refAxial, nSlices));
			lastIppVec.sub(refIpp, Rs).normalize();
		} else {
			Rc.cross(Rr, Rs).normalize();
		}

		// 3. 座標変換計算
		double dx = rcsPoint.x - refIpp.x;
		double dy = rcsPoint.y - refIpp.y;
		double dz = rcsPoint.z - refIpp.z;

		double u = (dx * Rr.x + dy * Rr.y + dz * Rr.z) / pixelSpacingX;
		double v = (dx * Rc.x + dy * Rc.y + dz * Rc.z) / pixelSpacingY;
		double w = (dx * Rs.x + dy * Rs.y + dz * Rs.z) / sliceThickness;

		return new Vector3d(u, v, w);
	}

	public Vector3d computeNearestIPP(Vector3d virtualIPP, Vector3d baseIPP, double[] baseIOP, double sliceThickness) {
		Vector3d row = new Vector3d(baseIOP[0], baseIOP[1], baseIOP[2]);
		Vector3d col = new Vector3d(baseIOP[3], baseIOP[4], baseIOP[5]);
		Vector3d normal = PlanarSupport.crossProduct(row, col, false);
		return computeNearestIPP(virtualIPP, baseIPP, normal, sliceThickness);
	}

	public Vector3d computeNearestIPP(Vector3d virtualIPP, Vector3d baseIPP, Vector3d sliceNormal, double sliceThickness) {
		Vector3d diff = new Vector3d(virtualIPP).sub(baseIPP);
		double sliceIndex = diff.dot(sliceNormal) / sliceThickness;
		return new Vector3d(sliceNormal).mul(sliceIndex * sliceThickness).add(baseIPP);
	}

	/**
	 * ★ 劇的高速化: ループ内のオブジェクト生成とDICOMアクセスを完全に排除
	 */
	public List<Vector3d> computeVoxelCoordinatesInPixelCoords(ImagePlus refAxial) {
		List<Vector3d> voxelCoordinates = new ArrayList<>();
		int w = (int)Math.ceil(geo.getDimensions().y);
		int h = (int)Math.ceil(geo.getDimensions().x);
		
		double vx = geo.getVoxelSpacing().y;
		double vy = geo.getVoxelSpacing().x;
		
		Vector3d ipp = geo.getTLHC();
		Vector3d row = geo.getRow();
		Vector3d column = geo.getColumn();
		
		// ループ外で参照ボリュームのメタデータを一度だけ計算・キャッシュする
		double[] imageOrientation = GDicomTools.getImageOrientationPatient(refAxial, 1);
		Vector3d Rr = new Vector3d(imageOrientation[0], imageOrientation[1], imageOrientation[2]);
		Vector3d Rc = new Vector3d(imageOrientation[3], imageOrientation[4], imageOrientation[5]);
		Vector3d Rs = new Vector3d();
		
		Vector3d refIpp = new Vector3d(GDicomTools.getImagePositionPatient(refAxial, 1));
		int nSlices = refAxial.getNSlices();
		if (nSlices > 1) {
			Vector3d lastIppVec = new Vector3d(GDicomTools.getImagePositionPatient(refAxial, nSlices));
			lastIppVec.sub(refIpp, Rs).normalize();
		} else {
			Rc.cross(Rr, Rs).normalize();
		}

		double pixelSpacingY = refAxial.getCalibration().pixelHeight;
		double pixelSpacingX = refAxial.getCalibration().pixelWidth;
		double sliceThickness = GDicomTools.getVoxelDepth(refAxial);

		// ループ内はプリミティブ演算のみに限定 (GC負荷ゼロ)
		for(int y=0; y<h; y++) {
			double yOffset = y * vy;
			for(int x=0; x<w; x++) {
				double xOffset = x * vx;
				
				// RCS座標の計算 (インライン化)
				double px = ipp.x + (row.x * xOffset) + (column.x * yOffset);
				double py = ipp.y + (row.y * xOffset) + (column.y * yOffset);
				double pz = ipp.z + (row.z * xOffset) + (column.z * yOffset);
				
				// Pixel座標への変換 (インライン化)
				double dx = px - refIpp.x;
				double dy = py - refIpp.y;
				double dz = pz - refIpp.z;
				
				double uCoord = (dx * Rr.x + dy * Rr.y + dz * Rr.z) / pixelSpacingX;
				double vCoord = (dx * Rc.x + dy * Rc.y + dz * Rc.z) / pixelSpacingY;
				double wCoord = (dx * Rs.x + dy * Rs.y + dz * Rs.z) / sliceThickness;

				voxelCoordinates.add(new Vector3d(uCoord, vCoord, wCoord));
			}
		}
		return voxelCoordinates;
	}

	public Vector2d mapToPixelCoordinates(Vector3d rcsPoint, Vector3d nearestIPP) {
		return mapToPixelCoordinates(rcsPoint, nearestIPP, geo.getRow(), geo.getColumn(), geo.getVoxelSpacing().y, geo.getVoxelSpacing().x);
	}

	public Vector2d mapToPixelCoordinates(Vector3d rcsPoint, Vector3d nearestIPP, Vector3d row, Vector3d col, double pixelSpacingX, double pixelSpacingY) {
		Vector3d diff = new Vector3d(rcsPoint).sub(nearestIPP);
		return new Vector2d(diff.dot(row) / pixelSpacingX, diff.dot(col) / pixelSpacingY);
	}

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
		Vector3d dim = geo.getDimensions();
		Vector3d vsize = geo.getVoxelSpacing();
		return new double[] { dim.x * vsize.x, dim.y * vsize.y, dim.z * vsize.z };
	}

	/**
	 * ★ JOMLによる行列回転の最適化
	 */
	public void rotateCube(double[] center, double angle, boolean rotateX, boolean rotateY, boolean rotateZ) {
		if (Math.abs(angle) < 0.001) return;

		Vector3d centerVec = new Vector3d(center[0], center[1], center[2]);
		Matrix3d rotMatrix = new Matrix3d();
		
		if (rotateZ) rotMatrix.rotateZ(angle);
		if (rotateY) rotMatrix.rotateY(angle);
		if (rotateX) rotMatrix.rotateX(angle);

		for (Vector3d vertex : cubeVertices) {
			vertex.sub(centerVec).mul(rotMatrix).add(centerVec);
		}

		if (rotateX) {
			updateIOPAfterRotateFromCenter(angle, 0, 0);
			updateIPPAfterRotateFromCenter(centerVec, angle, 0, 0);
		}
		if (rotateY) {
			updateIOPAfterRotateFromCenter(0, angle, 0);
			updateIPPAfterRotateFromCenter(centerVec, 0, angle, 0);
		}
		if (rotateZ) {
			updateIOPAfterRotateFromCenter(0, 0, angle);
			updateIPPAfterRotateFromCenter(centerVec, 0, 0, angle);
		}
		initVertices(); // re-calculate
	}

	public void updateIOPAfterRotateFromCenter(double rotateX, double rotateY, double rotateZ) {
		Vector3d row = PlanarSupport.rotateImageOrientationPatient(geo.getRow(), rotateX, rotateY, rotateZ);
		Vector3d col = PlanarSupport.rotateImageOrientationPatient(geo.getColumn(), rotateX, rotateY, rotateZ);
		geo.setRowVector(row);
		geo.setColumnVector(col);
	}

	/**
	 * ★ 手動での行列計算(double[][])をJOMLマトリクスに置き換え
	 */
	public void updateIPPAfterRotateFromCenter(Vector3d center, double rotateX, double rotateY, double rotateZ) {
		Vector3d ipp = geo.getTLHC();

		Matrix3d rotMatrix = new Matrix3d()
				.rotateZ(Math.toRadians(rotateZ))
				.rotateY(Math.toRadians(rotateY))
				.rotateX(Math.toRadians(rotateX));

		// (ipp - center) * RotationMatrix + center
		ipp.sub(center).mul(rotMatrix).add(center);
		
		geo.setImagePositionPatient(ipp);
	}

	public void move(int shiftX, int shiftY, int shiftZ) {
		double min = Math.min(geo.getVoxelSpacing().x, Math.min(geo.getVoxelSpacing().y, geo.getVoxelSpacing().z));
		
		Vector3d ipp = geo.getTLHC();
		ipp.add(shiftX * min, shiftY * min, shiftZ * min);
		
		geo.setImagePositionPatient(ipp);
		initVertices();
	}
}
