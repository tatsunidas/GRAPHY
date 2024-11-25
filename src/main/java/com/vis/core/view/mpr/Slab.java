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

import java.util.List;

import org.joml.Vector3d;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;

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
	
	public List<SlicePlane> getSlicePlanes() {
		return this.reslicePlanes;
	}
	
	public void initBoundingBox(List<SlicePlane> reslicePlanes) {
		//get first slice
		GeometryOfSlice geo = reslicePlanes.get(0).getGeometryOfSlice();
		Vector3d voxelSize = geo.getVoxelSpacing();
		double thickness = geo.getSliceThickness();
		double slabDepth = 0;
		if(Double.isNaN(thickness)) {
			slabDepth =  (voxelSize.z+gap)*(reslicePlanes.size()-1);
		}else {
			slabDepth =  (thickness+gap)*(reslicePlanes.size()-1);
		}
		
		int numOfSlices = reslicePlanes.size();
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
	 * Rotate slices from slab center.
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
			if(rx > 0.001) {
				sp.rotateCube(center, rx, true, false, false);
				rotateX = (int)rx;
			}
			if(ry > 0.001) {
				sp.rotateCube(center, ry, false, true, false);
				rotateY = (int)ry;
			}
			if(rz > 0.001) {
				sp.rotateCube(center, rz, false, false, true);
				rotateZ = (int)rz;
			}
		}
	}
	
	public boolean isNearCorner(Praparat pp/*on mouse*/, Vector3d ippOnMouse) {
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
		
		
		// Draw XY projection of the cube
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		
		for (Vector3d vertex :aabbVertices) {
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
												
//		Log.logger.fine("is inside ;" + isInside);
		
		if(!isInside) {
			return false;
		}else {
			System.out.println("ok");
		}
		
		double threshold = 30.0; // 10ピクセル以内なら四隅とみなす

		// 四隅とマウス位置の距離を計算
		for (Vector3d corner : cubeVertices) {
			double dx = corner.x - ippOnMouse.x;
			double dy = corner.y - ippOnMouse.y;
			double dz = corner.z - ippOnMouse.z;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			// もし距離が閾値以内なら、四隅に近いと判断
			if (distance < threshold) {
				return true;
			}
		}
		return false; // 四隅に近くない
	}
	
	public Vector3d getRotations() {
		return new Vector3d(rotateX, rotateY, rotateZ);
	}
	
	/**
	 * OffScreen pixel unit
	 * @param x
	 * @param y
	 * @param z
	 */
	public void moveSlab(int x, int y, int z) {
		for(SlicePlane sp : reslicePlanes) {
			sp.move(x, y, z);
		}
	}
}
