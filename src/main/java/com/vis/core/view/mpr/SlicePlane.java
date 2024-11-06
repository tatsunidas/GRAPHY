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

import com.vis.core.view.D2.ui.orientation.LocalizerPoster;

/**
 * 
 * TODO Use with GeometryOfSlice class.
 * 
 * Line to Plane
 * @author tatsunidas
 *
 */
public class SlicePlane {
	int rows;
	int cols;
	double[] iop;
	double[] ipp;
	Vector3d xVector;//row direction cosines
	Vector3d yVector;//col direction cosines
	Vector3d voxelSize;//x,y,z of voxel
	Vector3d dimension;// [rows, cols, frames], i.e, [rows, cols, 1]
	double sliceThickness;
	Vector3d[] cubeVertices;
	
	public SlicePlane(
			int rows,/*rows in slice*/ 
			int cols,/*cols in slice*/ 
			double[] iop, 
			double[] ipp, double[] voxelSize, double sliceThickness){
		this.rows = rows;
		this.cols = cols;
		this.iop = iop;
		this.ipp = ipp;
		this.sliceThickness = sliceThickness;
		this.dimension = new Vector3d(rows, cols, 1);
		initVertices(voxelSize);
	}
	
	private void initVertices(double[] voxelSize) {
		this.voxelSize = new Vector3d(voxelSize);
		this.xVector = new Vector3d(iop[0], iop[1], iop[2]);
		this.yVector = new Vector3d(iop[3], iop[4], iop[5]);
		/*
		 * You need test. is these vertices corrects ?
		 */
		cubeVertices = LocalizerPoster.getCornersOfSourceCubeInSourceSpace(xVector, yVector, new Vector3d(ipp), this.voxelSize, sliceThickness, dimension);
	}
	
	public Vector3d[] getCubeVerticesInOffScreenWorldCoordinate(){
		return cubeVertices;
	}
	
	public Vector3d[] getCubeVerticesInOffScreenPixelUnit(){
		Vector3d[] vers = new Vector3d[8];
		for(int i =0; i< 8; i++) {
			Vector3d v = cubeVertices[i];
			vers[i] = new Vector3d(v.x/voxelSize.x, v.y/voxelSize.y, v.z/sliceThickness);
		}
		return vers;
	}
	
	private double[] getCubeCenter() {
        double[] center = {0, 0, 0};
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
	
	/**
	 * Rotate from center of gravity.
	 * 
	 * @param rotateX
	 * @param rotateY
	 * @param rotateZ
	 */
	public void rotateCube(double angle, boolean rotateX, boolean rotateY, boolean rotateZ) {
		double[] center = getCubeCenter();
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
		//updateIOP and ipp
		if(rotateX) {
			updateIOPAfterRotateFromCenter(angle, 0, 0);
			updateIPPAfterRotateFromCenter(center, angle, 0, 0);
		}
		if(rotateY) {
			updateIOPAfterRotateFromCenter(0, angle, 0);
			updateIPPAfterRotateFromCenter(center, 0, angle, 0);
		}
		if(rotateZ) {
			updateIOPAfterRotateFromCenter(0, 0, angle);
			updateIPPAfterRotateFromCenter(center, 0, 0, angle);
		}
	}
	
	public void updateIOPAfterRotateFromCenter(double rotateX, double rotateY, double rotateZ) {
		this.iop = PlanarSupport.rotateImageOrientationPatient(iop, rotateX, rotateY, rotateZ);
	}

	public void updateIPPAfterRotateFromCenter(double[] center, double rotateX, double rotateY, double rotateZ) {
		// ラジアンに変換
	    double radX = Math.toRadians(rotateX);
	    double radY = Math.toRadians(rotateY);
	    double radZ = Math.toRadians(rotateZ);

	    // 元の位置を中心からの相対位置に変換
	    double[] relativePosition = new double[3];
	    relativePosition[0] = ipp[0] - center[0];
	    relativePosition[1] = ipp[1] - center[1];
	    relativePosition[2] = ipp[2] - center[2];

	    // 回転行列の計算
	    double[][] Rx = {
	        {1, 0, 0},
	        {0, Math.cos(radX), -Math.sin(radX)},
	        {0, Math.sin(radX), Math.cos(radX)}
	    };

	    double[][] Ry = {
	        {Math.cos(radY), 0, Math.sin(radY)},
	        {0, 1, 0},
	        {-Math.sin(radY), 0, Math.cos(radY)}
	    };

	    double[][] Rz = {
	        {Math.cos(radZ), -Math.sin(radZ), 0},
	        {Math.sin(radZ), Math.cos(radZ), 0},
	        {0, 0, 1}
	    };

	    // 回転の適用
	    double[] rotatedPosition = new double[3];
	    
	    // P' = Rz * (Ry * (Rx * P_relative))
	    // まずRxを適用
	    double[] tempPosition = new double[3];
	    tempPosition[0] = Rx[0][0] * relativePosition[0] + Rx[0][1] * relativePosition[1] + Rx[0][2] * relativePosition[2];
	    tempPosition[1] = Rx[1][0] * relativePosition[0] + Rx[1][1] * relativePosition[1] + Rx[1][2] * relativePosition[2];
	    tempPosition[2] = Rx[2][0] * relativePosition[0] + Rx[2][1] * relativePosition[1] + Rx[2][2] * relativePosition[2];
	    
	    // 次にRyを適用
	    rotatedPosition[0] = Ry[0][0] * tempPosition[0] + Ry[0][1] * tempPosition[1] + Ry[0][2] * tempPosition[2];
	    rotatedPosition[1] = Ry[1][0] * tempPosition[0] + Ry[1][1] * tempPosition[1] + Ry[1][2] * tempPosition[2];
	    rotatedPosition[2] = Ry[2][0] * tempPosition[0] + Ry[2][1] * tempPosition[1] + Ry[2][2] * tempPosition[2];

	    // 最後にRzを適用
	    double[] finalPosition = new double[3];
	    finalPosition[0] = Rz[0][0] * rotatedPosition[0] + Rz[0][1] * rotatedPosition[1] + Rz[0][2] * rotatedPosition[2];
	    finalPosition[1] = Rz[1][0] * rotatedPosition[0] + Rz[1][1] * rotatedPosition[1] + Rz[1][2] * rotatedPosition[2];
	    finalPosition[2] = Rz[2][0] * rotatedPosition[0] + Rz[2][1] * rotatedPosition[1] + Rz[2][2] * rotatedPosition[2];

	    // 新しい位置を画像の中心に戻す
	    ipp[0] = finalPosition[0] + center[0];
	    ipp[1] = finalPosition[1] + center[1];
	    ipp[2] = finalPosition[2] + center[2];
	}
}
