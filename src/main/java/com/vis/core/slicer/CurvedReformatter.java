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

import com.vis.core.slicer.Centerline3D.CurveFrame;
import com.vis.core.slicer.Centerline3D.FrameMode;
import com.vis.core.view.D3.ui.VolumeData;

import ij.measure.Calibration;

/**
 * Core Curved MPR (curved planar reformation) algorithm: flattens a volume
 * along a {@link Centerline3D} into a 2D raster.
 *
 * Output axes:
 * <ul>
 * <li>X (columns) = arc length along the curve.</li>
 * <li>Y (rows) = offset along the curve's local "normal" axis - either the
 * world-Z projection ({@link FrameMode#FIXED_Z}, e.g. dental panoramic) or a
 * rotation-minimizing cross-sectional axis ({@link FrameMode#ROTATION_MINIMIZING},
 * e.g. vascular CPR). Row 0 is the maximum offset (kept analogous to DICOM
 * axial stacks, which are standardized Z-descending, i.e. "superior" first).</li>
 * </ul>
 * Optionally projects (average/MIP/MinIP) across a band along the curve's
 * binormal axis at each sample, for slab-thickness style reformations.
 *
 * @author tatsunidas
 */
public class CurvedReformatter {

	public enum ProjectionMode {
		/** Sample only the centerline itself (band width ignored). */
		CENTERLINE_ONLY,
		AVERAGE,
		MIP,
		MINIP
	}

	public static final class Params {
		/** Spacing between output columns, i.e. along the curve (mm). */
		public double arcStepMm = 1.0;
		/** Spacing between output rows, i.e. along the second axis (mm). */
		public double secondAxisStepMm = 1.0;
		/** Second-axis offset range relative to each curve point (mm); min < max. */
		public double secondAxisMinMm = -100.0;
		public double secondAxisMaxMm = 100.0;
		public FrameMode frameMode = FrameMode.FIXED_Z;
		/** Half-width of the projection band along the binormal axis (mm); 0 = centerline only. */
		public double bandHalfWidthMm = 0.0;
		/** Number of samples across the band (including both edges); ignored if bandHalfWidthMm <= 0. */
		public int bandSampleCount = 5;
		public ProjectionMode projectionMode = ProjectionMode.CENTERLINE_ONLY;
		/** Pixel value used where a sample falls outside the volume. */
		public double outOfBoundsValue = 0.0;
	}

	public static final class Result {
		public final float[] pixels; // row-major, length = width*height
		public final int width; // along curve
		public final int height; // along second axis
		public final double pixelSpacingX; // mm per column = arcStepMm
		public final double pixelSpacingY; // mm per row = secondAxisStepMm

		Result(float[] pixels, int width, int height, double pixelSpacingX, double pixelSpacingY) {
			this.pixels = pixels;
			this.width = width;
			this.height = height;
			this.pixelSpacingX = pixelSpacingX;
			this.pixelSpacingY = pixelSpacingY;
		}
	}

	private CurvedReformatter() {
	}

	public static Result reformat(Centerline3D curve, VolumeSampler sampler, Params params) {
		if (curve == null || curve.size() < 2) {
			throw new IllegalArgumentException("curve must have at least 2 control points");
		}
		if (params.secondAxisMaxMm <= params.secondAxisMinMm) {
			throw new IllegalArgumentException("secondAxisMaxMm must be > secondAxisMinMm");
		}

		double length = curve.getTotalLength();
		int width = Math.max(1, (int) Math.round(length / params.arcStepMm) + 1);
		int height = Math.max(1,
				(int) Math.round((params.secondAxisMaxMm - params.secondAxisMinMm) / params.secondAxisStepMm) + 1);

		float[] pixels = new float[width * height];

		boolean useBand = params.projectionMode != ProjectionMode.CENTERLINE_ONLY && params.bandHalfWidthMm > 0
				&& params.bandSampleCount > 1;

		for (int col = 0; col < width; col++) {
			double s = Math.min(col * params.arcStepMm, length);
			CurveFrame frame = curve.frameAt(s, params.frameMode);

			for (int row = 0; row < height; row++) {
				// row 0 = max offset (kept consistent with Z-descending axial convention)
				double h = params.secondAxisMaxMm - row * params.secondAxisStepMm;
				Vector3d basePos = new Vector3d(frame.position).add(new Vector3d(frame.normal).mul(h));

				float value;
				if (!useBand) {
					value = (float) sampler.sampleTrilinear(basePos, params.outOfBoundsValue);
				} else {
					value = projectBand(sampler, basePos, frame.binormal, params);
				}
				pixels[row * width + col] = value;
			}
		}

		return new Result(pixels, width, height, params.arcStepMm, params.secondAxisStepMm);
	}

	private static float projectBand(VolumeSampler sampler, Vector3d basePos, Vector3d binormal, Params params) {
		int n = params.bandSampleCount;
		double half = params.bandHalfWidthMm;
		double sum = 0;
		float max = -Float.MAX_VALUE;
		float min = Float.MAX_VALUE;
		for (int k = 0; k < n; k++) {
			double b = -half + (2 * half) * k / (n - 1);
			Vector3d p = new Vector3d(basePos).add(new Vector3d(binormal).mul(b));
			float v = (float) sampler.sampleTrilinear(p, params.outOfBoundsValue);
			sum += v;
			if (v > max) max = v;
			if (v < min) min = v;
		}
		switch (params.projectionMode) {
		case MIP:
			return max;
		case MINIP:
			return min;
		case AVERAGE:
		default:
			return (float) (sum / n);
		}
	}

	/**
	 * Builds an output Calibration: anisotropic spatial spacing (arc-length
	 * vs second-axis), value/HU calibration copied from the source volume
	 * when present - same policy as {@code VolumeData.calibration}.
	 */
	public static Calibration buildCalibration(VolumeData sourceVolume, Result result) {
		Calibration cal = (sourceVolume != null && sourceVolume.calibration != null)
				? sourceVolume.calibration.copy()
				: new Calibration();
		cal.pixelWidth = result.pixelSpacingX;
		cal.pixelHeight = result.pixelSpacingY;
		cal.pixelDepth = 1.0;
		return cal;
	}
}
