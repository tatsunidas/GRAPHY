/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.slicer;

import org.joml.Vector3d;

import com.vis.core.slicer.Centerline3D.CurveFrame;
import com.vis.core.slicer.Centerline3D.FrameMode;
import com.vis.core.view.D3.ui.VolumeData;

/**
 * The 3D sibling of {@link CurvedReformatter}: instead of a single flat 2D
 * reformat, samples a dense grid (arc length x a square cross-sectional
 * disk) along a {@link Centerline3D} to build a brand-new {@link VolumeData}
 * in which the curve appears perfectly straight - "Straighten" for vascular
 * CPR. Because the result is a real VolumeData, it can be handed straight to
 * the existing VolumeRenderer/GLCanvas for 3D display, or fed back through
 * {@link VolumeSampler}/{@link CurvedReformatter} for further analysis.
 *
 * <b>Important geometric caveat:</b> the output volume's coordinate system is
 * synthetic and private to itself. Each individual cross-sectional slice has
 * correct physical (mm) in-plane spacing, and consecutive slices are
 * correctly spaced along the centerline's arc length - but because the curve
 * twists/rotates along its length (rotation-minimizing frame), there is no
 * single rigid transform that maps this volume's voxels back to the source
 * volume's true patient (LPS) coordinates. Its startIpp/iop/stepZ are left
 * as an identity placeholder and must not be used to compute absolute
 * patient-space positions.
 */
public class StraightenedVolumeBuilder {

	public static final class Params {
		/** Spacing between output slices, i.e. along the curve (mm). */
		public double arcStepMm = 1.0;
		/** Spacing between output pixels within a cross-sectional slice (mm). */
		public double radialStepMm = 1.0;
		/** Half-width of the (square) cross-sectional grid around the centerline (mm). */
		public double radiusMm = 20.0;
		public FrameMode frameMode = FrameMode.ROTATION_MINIMIZING;
		public double outOfBoundsValue = 0.0;
	}

	private StraightenedVolumeBuilder() {
	}

	public static VolumeData build(Centerline3D curve, VolumeSampler sampler, VolumeData sourceVolume, Params params) {
		if (curve == null || curve.size() < 2) {
			throw new IllegalArgumentException("curve must have at least 2 control points");
		}

		double length = curve.getTotalLength();
		int depth = Math.max(1, (int) Math.round(length / params.arcStepMm) + 1);
		int gridRadius = Math.max(1, (int) Math.round(params.radiusMm / params.radialStepMm));
		int size = gridRadius * 2 + 1;

		float[] data = new float[size * size * depth];
		for (int z = 0; z < depth; z++) {
			double s = Math.min(z * params.arcStepMm, length);
			CurveFrame frame = curve.frameAt(s, params.frameMode);
			int sliceBase = z * size * size;
			for (int j = 0; j < size; j++) {
				double v = (j - gridRadius) * params.radialStepMm;
				Vector3d rowOffset = new Vector3d(frame.position).add(new Vector3d(frame.normal).mul(v));
				int rowBase = sliceBase + j * size;
				for (int i = 0; i < size; i++) {
					double u = (i - gridRadius) * params.radialStepMm;
					Vector3d p = new Vector3d(rowOffset).add(new Vector3d(frame.binormal).mul(u));
					data[rowBase + i] = (float) sampler.sampleTrilinear(p, params.outOfBoundsValue);
				}
			}
		}

		VolumeData vol = new VolumeData(size, size, depth, data);
		vol.pixelSpacingX = params.radialStepMm;
		vol.pixelSpacingY = params.radialStepMm;
		vol.sliceThickness = params.arcStepMm;
		vol.calibration = sourceVolume != null ? sourceVolume.calibration : null;

		// Synthetic/private coordinate system - see class javadoc. Identity placeholder only.
		vol.startIpp = new double[] { 0, 0, 0 };
		vol.iop = new double[] { 1, 0, 0, 0, 1, 0 };
		vol.stepZ = new double[] { 0, 0, params.arcStepMm };

		return vol;
	}
}
