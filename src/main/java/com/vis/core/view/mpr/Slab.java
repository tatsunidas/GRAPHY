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

import java.awt.geom.Point2D;
import java.util.List;

import org.joml.Vector2d;
import org.joml.Vector3d;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;

/**
 * Group of SlicePlanes
 * @author tatsunidas
 *
 */
public class Slab {
	
	int rotateX =0; // rotation in YZ space
	int rotateY =0; // rotation in XZ space
	int rotateZ =0; // rotation in XY space
	
	SlicePlane boundingBox;//cover of all slices.
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
		if(this.reslicePlanes == null) {
			return 0;
		}
		return this.reslicePlanes.size();
	}
	
	public List<SlicePlane> getSlicePlanes() {
		return this.reslicePlanes;
	}
	
	public void initBoundingBox(List<SlicePlane> reslicePlanes) {
		//get first slice
		GeometryOfSlice geo = reslicePlanes.get(0).getGeometryOfSlice();
		Vector3d voxelSize = geo.getVoxelSpacing();
		double thickness = geo.getSliceThickness();
		double slabDepth = 0;
		int numOfSlices = reslicePlanes.size();
		if(Double.isNaN(thickness)) {
			slabDepth =  (voxelSize.z*numOfSlices)+(gap*(numOfSlices-1));
		}else {
			slabDepth =  (thickness*numOfSlices)+(gap*(numOfSlices-1));
		}
		if(numOfSlices%2 == 0/*even*/) {
			Vector3d planeTLHC1 = reslicePlanes.get(numOfSlices/2-1).getGeometryOfSlice().getTLHC();
			Vector3d planeTLHC2 = reslicePlanes.get(numOfSlices/2).getGeometryOfSlice().getTLHC();
			Vector3d tlhc = new Vector3d(
					new double[] {
							(planeTLHC1.x+planeTLHC2.x)/2,
							(planeTLHC1.y+planeTLHC2.y)/2,
							(planeTLHC1.z+planeTLHC2.z)/2
							}); 
			boundingBox = new SlicePlane(
					(int)geo.getDimensions().x, 
					(int)geo.getDimensions().y, 
					geo.getImageOrientationPatient(),
					PlanarSupport.v2d(tlhc),
					new double[] {voxelSize.x, voxelSize.y, slabDepth},
					slabDepth);
		}else {
			int centerPos = (int)Math.floor(numOfSlices/2);//0 base
			boundingBox = new SlicePlane(
					(int)geo.getDimensions().x, 
					(int)geo.getDimensions().y, 
					geo.getImageOrientationPatient(),
					PlanarSupport.v2d(reslicePlanes.get(centerPos).getGeometryOfSlice().getTLHC()),
					new double[] {voxelSize.x, voxelSize.y, slabDepth},
					slabDepth);
		}
	}
	
	/**
	 * Rotate slices by slab center from current rotation status.
	 * @param rx
	 * @param ry
	 * @param rz
	 */
	public void rotateSlab(double rx, double ry, double rz) {
		double[] center = boundingBox.getCubeCenter();
		boundingBox.rotateCube(center, rx, true, false, false);
		boundingBox.rotateCube(center, ry, false, true, false);
		boundingBox.rotateCube(center, rz, false, false, true);
		for(SlicePlane sp : reslicePlanes) {
			sp.rotateCube(center, rx, true, false, false);
			sp.rotateCube(center, ry, false, true, false);
			sp.rotateCube(center, rz, false, false, true);
		}
		rotateX += (int)rx;
		rotateY += (int)ry;
		rotateZ += (int)rz;
	}
		
	public boolean isBoundingBox(Praparat pp/*on mouse*/, Vector3d ippOnMouse) {
		//OBB : Oriented Bounding Box
		//AABB : Axis Aligned Bounding Box
		//First, convert all points to AABB state.
		Vector3d cubeVertices[] = boundingBox.cubeVertices;
		Vector3d aabbVertices[] = new Vector3d[8];
		int i =0;
		for(Vector3d v: cubeVertices) {
			aabbVertices[i++] = PlanarSupport.rotateVector(v, -1*rotateX, -1*rotateY, -1*rotateZ);
		}
		Vector3d aabb_P = PlanarSupport.rotateVector(ippOnMouse, -1*rotateX, -1*rotateY, -1*rotateZ);
		
		Vector3d center = new Vector3d(0,0,0);
		for(Vector3d v : aabbVertices) {
			center.x += v.x;
			center.y += v.y;
			center.z += v.z;
		}
		center.x /= 8;
		center.y /= 8;
		center.z /= 8;
		
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

		for (Vector3d vertex : aabbVertices) {
			int x = (int) vertex.x;
			int y = (int) vertex.y;
			int z = (int) vertex.z;
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
		}
		
		boolean isInside = aabb_P.x >= minX && aabb_P.x <= maxX &&
				aabb_P.y >= minY && aabb_P.y <= maxY &&
						aabb_P.z >= minZ && aabb_P.z <= maxZ;
		return isInside;
	}
	
	public boolean isPointInShrinkedBox(Praparat pp/*on mouse*/, Vector3d ippOnMouse) {
		Vector3d cubeVertices[] = boundingBox.cubeVertices;
		Vector3d aabbVertices[] = new Vector3d[8];
		int i =0;
		for(Vector3d v: cubeVertices) {
			aabbVertices[i++] = PlanarSupport.rotateVector(v, -1*rotateX, -1*rotateY, -1*rotateZ);
		}
		Vector3d aabb_P = PlanarSupport.rotateVector(ippOnMouse, -1*rotateX, -1*rotateY, -1*rotateZ);
		
		Vector3d center = new Vector3d(0,0,0);
		for(Vector3d v : aabbVertices) {
			center.x += v.x;
			center.y += v.y;
			center.z += v.z;
		}
		center.x /= 8;
		center.y /= 8;
		center.z /= 8;
		
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

		for (Vector3d vertex : aabbVertices) {
			int x = (int) vertex.x;
			int y = (int) vertex.y;
			int z = (int) vertex.z;
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
		}
		
		boolean isInside = aabb_P.x >= minX && aabb_P.x <= maxX &&
				aabb_P.y >= minY && aabb_P.y <= maxY &&
						aabb_P.z >= minZ && aabb_P.z <= maxZ;
		
		double[] sizeInRCS = boundingBox.sizeInRCS();
		
		boolean isOutsideShrinked = false;
		
		// 内部領域の範囲を計算
		double shrinkFactor = 1.0 / 3.0;
		double halfWidth = sizeInRCS[1] * shrinkFactor / 2.0;
		double halfHeight = sizeInRCS[0] * shrinkFactor / 2.0;
		double halfDepth = sizeInRCS[2] * shrinkFactor / 2.0;
		
		// 点が縮小された直方体の外側にあるか判定
//		if(pp.getName().equals("XY")) {
//			
//		}else if(pp.getName().equals("XZ")) {
//			
//		}else if(pp.getName().equals("YZ")) {
//			
//		}else {
//			// this is not MPR pp.
//			return false;
//		}
		
		isOutsideShrinked = 
				(aabb_P.x <= minX+halfWidth) || (aabb_P.x >= maxX-halfWidth) ||
        		(aabb_P.y <= minY+halfHeight) || (aabb_P.y >= maxY-halfHeight) ||
        		(aabb_P.z <= minZ+halfDepth) || (aabb_P.z >= maxZ-halfDepth);
		
       return isInside && isOutsideShrinked;
    }
	
	public Vector3d getRotations() {
		return new Vector3d(rotateX, rotateY, rotateZ);
	}
	
	public double[] getCenterOfVolumeInPixelCoords(ImagePlus referenceVolume) {
		Vector3d[] rcsCoords = boundingBox.cubeVertices;
		Vector3d[] nearestSliceIPPOfEachPoint = new Vector3d[rcsCoords.length];/*各座標が属する面のIPP*/
		double[] baseIPP = GDicomTools.getImagePositionPatient(referenceVolume, 1);
		double[] baseIOP = GDicomTools.getImageOrientationPatient(referenceVolume, 1);
		double thickness = GDicomTools.getVoxelDepth(referenceVolume);
		for(int i=0;i<rcsCoords.length; i++) {
			nearestSliceIPPOfEachPoint[i] = boundingBox.computeNearestIPP(rcsCoords[i], PlanarSupport.d2v(baseIPP), baseIOP, thickness);
		}
		double[] iop = GDicomTools.getImageOrientationPatient(referenceVolume, 1);
		Vector3d pixelSpacingInReference = new Vector3d();
		pixelSpacingInReference.x = referenceVolume.getCalibration().pixelWidth;
		pixelSpacingInReference.y = referenceVolume.getCalibration().pixelHeight;
		pixelSpacingInReference.z = GDicomTools.getVoxelDepth(referenceVolume);
		Vector3d[] pixelCoords = boundingBox.getCubeVerticesInOffScreenPixelUnit(rcsCoords, nearestSliceIPPOfEachPoint, iop, pixelSpacingInReference);
		double[] center = { 0, 0, 0 };
		int c = 0;
		for (Vector3d vertex : pixelCoords) {
			if (vertex==null) continue;
			center[0] += vertex.x;
			center[1] += vertex.y;
			center[2] += vertex.z;
			c++;
		}
		center[0] /= c;
		center[1] /= c;
		center[2] /= c;
		return center;
	}
	
	
	/**
	 * OffScreen pixel unit
	 * @param x
	 * @param y
	 * @param z
	 */
	public void moveSlab(int x, int y, int z) {
		boundingBox.move(x, y, z);
		for(SlicePlane sp : reslicePlanes) {
			sp.move(x, y, z);
		}
	}
}
