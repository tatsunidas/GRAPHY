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

import org.joml.Vector3d;

import com.vis.core.view.D3.ui.VolumeData;

/**
 * Samples raw voxel values out of a {@link VolumeData} at arbitrary physical
 * (LPS, mm) coordinates, using the same IPP/IOP/PixelSpacing/stepZ fields
 * that {@code com.vis.core.view.D3.ui.VolumeLoader} already populates (and
 * whose Z-axis orientation bug was fixed earlier) - so Curved MPR shares one
 * source of truth for voxel-index <-> physical-coordinate mapping instead of
 * re-deriving it.
 *
 * Assumes the volume grid is rectilinear (row/col/slice-normal mutually
 * orthogonal), which already holds for any VolumeData produced by
 * VolumeLoader (gantry tilt and oblique stacks are resampled onto an
 * orthogonal grid before reaching VolumeData).
 *
 * @author tatsunidas
 */
public class VolumeSampler {

	private final VolumeData volume;
	private final Vector3d startIpp;
	private final Vector3d rowDir; // unit vector, +X voxel direction
	private final Vector3d colDir; // unit vector, +Y voxel direction
	private final double pixelSpacingX;
	private final double pixelSpacingY;
	private final Vector3d stepZ; // full per-slice-index step vector (already includes spacing)
	private final double stepZLenSq;

	public VolumeSampler(VolumeData volume) {
		if (volume == null) {
			throw new IllegalArgumentException("volume must not be null");
		}
		if (volume.dataType == VolumeData.DataType.RGB) {
			throw new UnsupportedOperationException("VolumeSampler only supports scalar intensity volumes (BYTE/SHORT/FLOAT), not RGB");
		}
		if (volume.startIpp == null || volume.iop == null || volume.iop.length < 6 || volume.stepZ == null) {
			throw new IllegalArgumentException("volume is missing spatial calibration (startIpp/iop/stepZ)");
		}
		this.volume = volume;
		this.startIpp = new Vector3d(volume.startIpp[0], volume.startIpp[1], volume.startIpp[2]);
		this.rowDir = new Vector3d(volume.iop[0], volume.iop[1], volume.iop[2]);
		this.colDir = new Vector3d(volume.iop[3], volume.iop[4], volume.iop[5]);
		this.pixelSpacingX = volume.pixelSpacingX;
		this.pixelSpacingY = volume.pixelSpacingY;
		this.stepZ = new Vector3d(volume.stepZ[0], volume.stepZ[1], volume.stepZ[2]);
		this.stepZLenSq = stepZ.lengthSquared();
	}

	/** Converts a physical (mm) point to fractional voxel-index coordinates (may be out of [0,dim) range). */
	public double[] toVoxelIndex(Vector3d physicalPointMm) {
		Vector3d v = new Vector3d(physicalPointMm).sub(startIpp);
		double i = v.dot(rowDir) / pixelSpacingX;
		double j = v.dot(colDir) / pixelSpacingY;
		double k = stepZLenSq > 1e-12 ? v.dot(stepZ) / stepZLenSq : 0d;
		return new double[] { i, j, k };
	}

	/** Inverse of {@link #toVoxelIndex}: fractional voxel-index coordinates back to a physical (mm) point. */
	public Vector3d toPhysical(double i, double j, double k) {
		Vector3d p = new Vector3d(startIpp);
		p.add(new Vector3d(rowDir).mul(i * pixelSpacingX));
		p.add(new Vector3d(colDir).mul(j * pixelSpacingY));
		p.add(new Vector3d(stepZ).mul(k));
		return p;
	}

	/**
	 * Physical (mm) point converted to the object-space coordinates
	 * GLCanvas's volume ray-marching shader assumes: a fixed unit cube
	 * centered on the origin, [-0.5, 0.5] along every axis, regardless of
	 * the volume's physical (possibly anisotropic) extent - per-axis
	 * physical scaling is applied afterwards by GLCanvas's model matrix,
	 * not before. Points converted this way land in the same space as the
	 * volume cube currently being rendered.
	 */
	public Vector3d toLocalRenderSpace(Vector3d physicalPointMm) {
		double[] idx = toVoxelIndex(physicalPointMm);
		return new Vector3d(idx[0] / volume.width - 0.5, idx[1] / volume.height - 0.5, idx[2] / volume.depth - 0.5);
	}

	/**
	 * Trilinearly interpolated raw voxel value at the given physical point.
	 * Returns {@code outOfBoundsValue} when the point falls (even partially,
	 * for the surrounding 8 voxels) outside the volume.
	 */
	public double sampleTrilinear(Vector3d physicalPointMm, double outOfBoundsValue) {
		double[] idx = toVoxelIndex(physicalPointMm);
		double fi = idx[0], fj = idx[1], fk = idx[2];

		int x0 = (int) Math.floor(fi);
		int y0 = (int) Math.floor(fj);
		int z0 = (int) Math.floor(fk);
		int x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;

		if (x0 < 0 || y0 < 0 || z0 < 0 || x1 >= volume.width || y1 >= volume.height || z1 >= volume.depth) {
			return outOfBoundsValue;
		}

		double tx = fi - x0;
		double ty = fj - y0;
		double tz = fk - z0;

		double c000 = rawValueAt(x0, y0, z0);
		double c100 = rawValueAt(x1, y0, z0);
		double c010 = rawValueAt(x0, y1, z0);
		double c110 = rawValueAt(x1, y1, z0);
		double c001 = rawValueAt(x0, y0, z1);
		double c101 = rawValueAt(x1, y0, z1);
		double c011 = rawValueAt(x0, y1, z1);
		double c111 = rawValueAt(x1, y1, z1);

		double c00 = lerp(c000, c100, tx);
		double c10 = lerp(c010, c110, tx);
		double c01 = lerp(c001, c101, tx);
		double c11 = lerp(c011, c111, tx);

		double c0 = lerp(c00, c10, ty);
		double c1 = lerp(c01, c11, ty);

		return lerp(c0, c1, tz);
	}

	/** Same as {@link #sampleTrilinear} but converted through the volume's value calibration (e.g. HU) when available. */
	public double sampleCalibrated(Vector3d physicalPointMm, double outOfBoundsValue) {
		double raw = sampleTrilinear(physicalPointMm, Double.NaN);
		if (Double.isNaN(raw)) {
			return outOfBoundsValue;
		}
		return volume.toCalibrated(raw);
	}

	private double rawValueAt(int x, int y, int z) {
		int sliceSize = volume.width * volume.height;
		int index = z * sliceSize + y * volume.width + x;
		switch (volume.dataType) {
		case BYTE:
			return ((byte[]) volume.data)[index] & 0xFF;
		case SHORT:
			return ((short[]) volume.data)[index] & 0xFFFF;
		case FLOAT:
			return ((float[]) volume.data)[index];
		default:
			throw new UnsupportedOperationException("Unsupported data type: " + volume.dataType);
		}
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
