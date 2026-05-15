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
package com.vis.core.slicer;

import java.util.List;

import org.joml.Vector3d;

import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.core.view.D2.ui.orientation.SlicePlane;

import ij.ImagePlus;

/**
 * Group of SlicePlanes
 * @author tatsunidas
 *
 */
public class Slab {
	
	double rotateX = 0; // rotation in YZ space
	double rotateY = 0; // rotation in XZ space
	double rotateZ = 0; // rotation in XY space
	
	SlicePlane boundingBox; // cover of all slices.
	private List<SlicePlane> reslicePlanes;
	private double gap;
	
	public Slab(List<SlicePlane> reslicePlanes, double gap) {
		setSlicePlanes(reslicePlanes);
		this.gap = gap;
		initBoundingBox(reslicePlanes);
	}
	
	public void setSlicePlanes(List<SlicePlane> reslicePlanes) {
		this.reslicePlanes = reslicePlanes;
	}
	
	public int size() {
		if (this.reslicePlanes == null) {
			return 0;
		}
		return this.reslicePlanes.size();
	}
	
	public List<SlicePlane> getSlicePlanes() {
		return this.reslicePlanes;
	}
	
	public void initBoundingBox(List<SlicePlane> reslicePlanes) {
		GeometryOfSlice geo = reslicePlanes.get(0).getGeometryOfSlice();
		Vector3d voxelSize = geo.getVoxelSpacing();
		double thickness = geo.getSliceThickness();
		int numOfSlices = reslicePlanes.size();
		
		double slabDepth = Double.isNaN(thickness) 
				? (voxelSize.z * numOfSlices) + (gap * (numOfSlices - 1))
				: (thickness * numOfSlices) + (gap * (numOfSlices - 1));
		
		Vector3d tlhc;
		if (numOfSlices % 2 == 0) { // even
			Vector3d planeTLHC1 = reslicePlanes.get(numOfSlices / 2 - 1).getGeometryOfSlice().getTLHC();
			Vector3d planeTLHC2 = reslicePlanes.get(numOfSlices / 2).getGeometryOfSlice().getTLHC();
			// ★ JOMLを利用して中点座標を美しく計算
			tlhc = new Vector3d(planeTLHC1).add(planeTLHC2).mul(0.5); 
		} else {
			int centerPos = numOfSlices / 2; // 0 base
			tlhc = reslicePlanes.get(centerPos).getGeometryOfSlice().getTLHC();
		}
		
		boundingBox = new SlicePlane(
				(int) geo.getDimensions().x, 
				(int) geo.getDimensions().y, 
				geo.getImageOrientationPatient(),
				PlanarSupport.v2d(tlhc),
				new double[] {voxelSize.x, voxelSize.y, slabDepth},
				slabDepth);
	}
	
	public void rotateSlab(double rx, double ry, double rz) {
		double[] center = boundingBox.getCubeCenter();
		boundingBox.rotateCube(center, rx, true, false, false);
		boundingBox.rotateCube(center, ry, false, true, false);
		boundingBox.rotateCube(center, rz, false, false, true);
		
		for (SlicePlane sp : reslicePlanes) {
			sp.rotateCube(center, rx, true, false, false);
			sp.rotateCube(center, ry, false, true, false);
			sp.rotateCube(center, rz, false, false, true);
		}
		// (int) キャストを削除し、double のまま加算する
		rotateX += rx;
		rotateY += ry;
		rotateZ += rz;
	}
		
	public boolean isBoundingBox(Praparat pp, Vector3d ippOnMouse) {
		return checkAABB(ippOnMouse, false);
	}
	
	public boolean isPointInShrinkedBox(Praparat pp, Vector3d ippOnMouse) {
		return checkAABB(ippOnMouse, true);
	}
	
	/**
	 * ★ AABB判定の共通ヘルパーメソッド
	 * isBoundingBox と isPointInShrinkedBox の重複処理を統合し、無駄なcenter計算を削除。
	 */
	private boolean checkAABB(Vector3d ippOnMouse, boolean checkShrinked) {
		Vector3d[] cubeVertices = boundingBox.cubeVertices;
		
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

		for (Vector3d vertex : cubeVertices) {
			Vector3d aabbV = PlanarSupport.rotateVector(new Vector3d(vertex), -rotateX, -rotateY, -rotateZ);
			minX = Math.min(minX, aabbV.x);
			minY = Math.min(minY, aabbV.y);
			minZ = Math.min(minZ, aabbV.z);
			maxX = Math.max(maxX, aabbV.x);
			maxY = Math.max(maxY, aabbV.y);
			maxZ = Math.max(maxZ, aabbV.z);
		}
		
		Vector3d aabb_P = PlanarSupport.rotateVector(new Vector3d(ippOnMouse), -rotateX, -rotateY, -rotateZ);
		
		boolean isInside = aabb_P.x >= minX && aabb_P.x <= maxX &&
				           aabb_P.y >= minY && aabb_P.y <= maxY &&
				           aabb_P.z >= minZ && aabb_P.z <= maxZ;
		
		if (!checkShrinked) {
			return isInside;
		}
		
		double[] sizeInRCS = boundingBox.sizeInRCS();
		double shrinkFactor = 1.0 / 3.0;
		double halfWidth = sizeInRCS[1] * shrinkFactor / 2.0;
		double halfHeight = sizeInRCS[0] * shrinkFactor / 2.0;
		double halfDepth = sizeInRCS[2] * shrinkFactor / 2.0;
		
		boolean isOutsideShrinked = 
				(aabb_P.x <= minX + halfWidth) || (aabb_P.x >= maxX - halfWidth) ||
				(aabb_P.y <= minY + halfHeight) || (aabb_P.y >= maxY - halfHeight) ||
				(aabb_P.z <= minZ + halfDepth) || (aabb_P.z >= maxZ - halfDepth);
		
       return isInside && isOutsideShrinked;
	}
	
	public Vector3d getRotations() {
		return new Vector3d(rotateX, rotateY, rotateZ);
	}
	
	/**
	 * ★ getCenterOfVolumeInPixelCoords2 を正規名に昇格し、古いDeprecatedロジックを削除。
	 * SlicePlane で最適化された toPixelCoordinates を活用。
	 */
	public double[] getCenterOfVolumeInPixelCoords(ImagePlus referenceVolume) {
		Vector3d[] rcsCoords = boundingBox.cubeVertices;
		Vector3d[] pixelCoords = new Vector3d[8];
		
		for (int i = 0; i < 8; i++) {
			pixelCoords[i] = boundingBox.toPixelCoordinates(rcsCoords[i], referenceVolume);
		}
		
		double[] center = { 0, 0, 0 };
		int validCount = 0;
		for (Vector3d vertex : pixelCoords) {
			if (vertex == null) continue;
			center[0] += vertex.x;
			center[1] += vertex.y;
			center[2] += vertex.z;
			validCount++;
		}
		
		if (validCount > 0) {
			center[0] /= validCount;
			center[1] /= validCount;
			center[2] /= validCount;
		}
		return center;
	}
	
	public void moveSlab(int x, int y, int z) {
		boundingBox.move(x, y, z);
		for (SlicePlane sp : reslicePlanes) {
			sp.move(x, y, z);
		}
	}
}
