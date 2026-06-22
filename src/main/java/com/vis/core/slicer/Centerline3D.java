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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Vector3d;

/**
 * A 3D space curve defined by an ordered list of control points in patient
 * (LPS, mm) coordinates - the same coordinate system as DICOM
 * ImagePositionPatient/ImageOrientationPatient. Used as the centerline input
 * for Curved MPR (curved planar reformation).
 *
 * Control points may come from a single reference slice (all sharing the
 * same Z, e.g. a dental arch traced on one axial slice) or from several
 * different views/orientations (e.g. a vessel traced across axial + coronal
 * references) - the model itself has no notion of "which slice a point came
 * from", only physical position, so both use cases are the same data
 * structure.
 *
 * Interpolation uses Centripetal Catmull-Rom, mirroring
 * {@code com.vis.core.view.D3.endo.EndoPath3D}, but evaluated in real mm
 * coordinates instead of renderer-local cube coordinates, and extended with
 * a per-arclength orthonormal frame (tangent/normal/binormal) needed to
 * sample a flat band around the curve rather than just a camera direction.
 *
 * @author tatsunidas
 */
public class Centerline3D {

	private static final double CATMULL_ROM_ALPHA = 0.5; // centripetal
	private static final double MIN_CHORD = 1e-6;
	private static final int SAMPLES_PER_SEGMENT = 20; // arc-length table density

	/** Which convention to use for the curve's local "up"/second axis. */
	public enum FrameMode {
		/**
		 * Second axis is always the projection of the world Z (superior)
		 * axis onto the plane perpendicular to the tangent. Matches the
		 * classic dental panoramic reformation, where the curve stays
		 * within one axial slice and the output's vertical axis must stay
		 * literally vertical (head-to-foot) regardless of curve direction.
		 */
		FIXED_Z,
		/**
		 * Second axis is propagated along the curve with the double
		 * reflection method (Wang et al. 2008) to minimize twist. Needed
		 * when the curve itself travels out of a single plane (e.g.
		 * vascular CPR), where a fixed world axis can become parallel to
		 * the tangent and degenerate.
		 */
		ROTATION_MINIMIZING
	}

	/** Position + orthonormal frame at a given arc-length position. */
	public static final class CurveFrame {
		public final Vector3d position;
		public final Vector3d tangent; // unit
		public final Vector3d normal; // unit, perpendicular to tangent
		public final Vector3d binormal; // unit, tangent x normal

		CurveFrame(Vector3d position, Vector3d tangent, Vector3d normal, Vector3d binormal) {
			this.position = position;
			this.tangent = tangent;
			this.normal = normal;
			this.binormal = binormal;
		}
	}

	private final List<Vector3d> points = new ArrayList<>();

	private boolean dirty = true;
	private double[] sampleArcLength;
	private Vector3d[] samplePosition;
	private Vector3d[] sampleTangent;
	private Vector3d[] sampleNormalFixedZ;
	private Vector3d[] sampleNormalRmf;
	private double totalLength = 0d;

	// ===================== CRUD =====================

	public int size() {
		return points.size();
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	public Vector3d getControlPoint(int index) {
		return new Vector3d(points.get(index));
	}

	public List<Vector3d> getControlPointsSnapshot() {
		List<Vector3d> copy = new ArrayList<>(points.size());
		for (Vector3d p : points) {
			copy.add(new Vector3d(p));
		}
		return Collections.unmodifiableList(copy);
	}

	public int addControlPoint(Vector3d position) {
		points.add(new Vector3d(position));
		markDirty();
		return points.size() - 1;
	}

	public void insertControlPoint(int index, Vector3d position) {
		points.add(index, new Vector3d(position));
		markDirty();
	}

	public void setControlPoint(int index, Vector3d position) {
		points.get(index).set(position);
		markDirty();
	}

	public void removeControlPoint(int index) {
		points.remove(index);
		markDirty();
	}

	public void clear() {
		points.clear();
		markDirty();
	}

	// ===================== spline evaluation (segment space) =====================

	/** Position on the curve; t is segment space (0 = first point, segmentCount = last point), clamped. */
	public Vector3d evaluatePosition(double t) {
		if (points.isEmpty()) {
			throw new IllegalStateException("Centerline3D has no points");
		}
		if (points.size() == 1) {
			return new Vector3d(points.get(0));
		}

		int segmentCount = points.size() - 1;
		double ct = clamp(t, 0d, segmentCount);
		int segIndex = (int) Math.floor(ct);
		if (segIndex >= segmentCount) {
			segIndex = segmentCount - 1;
		}
		double s = ct - segIndex;

		if (points.size() == 2) {
			return lerp(points.get(0), points.get(1), s);
		}

		Vector3d p0 = getPhantomControlPoint(segIndex - 1);
		Vector3d p1 = getPhantomControlPoint(segIndex);
		Vector3d p2 = getPhantomControlPoint(segIndex + 1);
		Vector3d p3 = getPhantomControlPoint(segIndex + 2);
		return catmullRom(p0, p1, p2, p3, s);
	}

	// index < 0 or index >= size returns an extrapolated phantom point
	private Vector3d getPhantomControlPoint(int index) {
		int n = points.size();
		if (index < 0) {
			Vector3d p0 = points.get(0);
			Vector3d p1 = points.get(1);
			return new Vector3d(p0).mul(2d).sub(p1);
		}
		if (index >= n) {
			Vector3d pLast = points.get(n - 1);
			Vector3d pPrev = points.get(n - 2);
			return new Vector3d(pLast).mul(2d).sub(pPrev);
		}
		return points.get(index);
	}

	// Centripetal Catmull-Rom (Barry-Goldman blend). Evaluates P1-P2 at s in [0,1].
	private static Vector3d catmullRom(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double s) {
		double d0 = Math.max(p1.distance(p0), MIN_CHORD);
		double d1 = Math.max(p2.distance(p1), MIN_CHORD);
		double d2 = Math.max(p3.distance(p2), MIN_CHORD);

		double t0 = 0d;
		double t1 = t0 + Math.pow(d0, CATMULL_ROM_ALPHA);
		double t2 = t1 + Math.pow(d1, CATMULL_ROM_ALPHA);
		double t3 = t2 + Math.pow(d2, CATMULL_ROM_ALPHA);

		double t = t1 + s * (t2 - t1);

		Vector3d a1 = lerp(p0, p1, (t - t0) / (t1 - t0));
		Vector3d a2 = lerp(p1, p2, (t - t1) / (t2 - t1));
		Vector3d a3 = lerp(p2, p3, (t - t2) / (t3 - t2));

		Vector3d b1 = lerp(a1, a2, (t - t0) / (t2 - t0));
		Vector3d b2 = lerp(a2, a3, (t - t1) / (t3 - t1));

		return lerp(b1, b2, (t - t1) / (t2 - t1));
	}

	private static Vector3d lerp(Vector3d a, Vector3d b, double frac) {
		return new Vector3d(a).lerp(b, frac);
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	// ===================== arc-length parameterization =====================

	public double getTotalLength() {
		ensureFresh();
		return totalLength;
	}

	/** Position at the given arc-length distance (mm) from the curve start, clamped to [0, length]. */
	public Vector3d positionAt(double arcLengthMm) {
		return sampleAt(arcLengthMm).position;
	}

	public Vector3d tangentAt(double arcLengthMm) {
		return sampleAt(arcLengthMm).tangent;
	}

	public CurveFrame frameAt(double arcLengthMm, FrameMode mode) {
		ensureFresh();
		if (points.size() == 1) {
			Vector3d pos = new Vector3d(points.get(0));
			Vector3d tangent = new Vector3d(0, 0, 1);
			Vector3d normal = perpendicularOf(tangent);
			Vector3d binormal = new Vector3d();
			tangent.cross(normal, binormal);
			return new CurveFrame(pos, tangent, normal, binormal);
		}

		int n = sampleArcLength.length;
		double d = clamp(arcLengthMm, 0d, totalLength);
		int lo = 0;
		int hi = n - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) / 2;
			if (sampleArcLength[mid] <= d) {
				lo = mid;
			} else {
				hi = mid;
			}
		}

		double segLen = sampleArcLength[hi] - sampleArcLength[lo];
		double f = segLen > 1e-9 ? (d - sampleArcLength[lo]) / segLen : 0d;

		Vector3d pos = lerp(samplePosition[lo], samplePosition[hi], f);
		Vector3d tangent = lerp(sampleTangent[lo], sampleTangent[hi], f);
		if (tangent.lengthSquared() > 1e-12) {
			tangent.normalize();
		} else {
			tangent.set(0, 0, 1);
		}

		Vector3d[] normalTable = (mode == FrameMode.FIXED_Z) ? sampleNormalFixedZ : sampleNormalRmf;
		Vector3d normal = lerp(normalTable[lo], normalTable[hi], f);
		// re-orthogonalize against the interpolated tangent (independent lerps drift slightly)
		normal.sub(new Vector3d(tangent).mul(normal.dot(tangent)));
		if (normal.lengthSquared() > 1e-12) {
			normal.normalize();
		} else {
			normal = perpendicularOf(tangent);
		}

		Vector3d binormal = new Vector3d();
		tangent.cross(normal, binormal);
		return new CurveFrame(pos, tangent, normal, binormal);
	}

	/** Evenly spaced frames covering [0, length] with the given spacing (mm); always includes both ends. */
	public List<CurveFrame> resample(double spacingMm, FrameMode mode) {
		ensureFresh();
		List<CurveFrame> out = new ArrayList<>();
		if (spacingMm <= 0) {
			throw new IllegalArgumentException("spacingMm must be > 0");
		}
		int steps = (int) Math.ceil(totalLength / spacingMm);
		for (int i = 0; i <= steps; i++) {
			double s = Math.min(i * spacingMm, totalLength);
			out.add(frameAt(s, mode));
		}
		return out;
	}

	private PathSample sampleAt(double arcLengthMm) {
		ensureFresh();
		if (points.size() == 1) {
			return new PathSample(new Vector3d(points.get(0)), new Vector3d(0, 0, 1));
		}
		int n = sampleArcLength.length;
		double d = clamp(arcLengthMm, 0d, totalLength);
		int lo = 0;
		int hi = n - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) / 2;
			if (sampleArcLength[mid] <= d) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		double segLen = sampleArcLength[hi] - sampleArcLength[lo];
		double f = segLen > 1e-9 ? (d - sampleArcLength[lo]) / segLen : 0d;
		Vector3d pos = lerp(samplePosition[lo], samplePosition[hi], f);
		Vector3d tangent = lerp(sampleTangent[lo], sampleTangent[hi], f);
		if (tangent.lengthSquared() > 1e-12) {
			tangent.normalize();
		} else {
			tangent.set(0, 0, 1);
		}
		return new PathSample(pos, tangent);
	}

	private static final class PathSample {
		final Vector3d position;
		final Vector3d tangent;

		PathSample(Vector3d position, Vector3d tangent) {
			this.position = position;
			this.tangent = tangent;
		}
	}

	private void markDirty() {
		dirty = true;
	}

	private void ensureFresh() {
		if (dirty) {
			rebuildArcLengthTable();
			dirty = false;
		}
	}

	private void rebuildArcLengthTable() {
		int n = points.size();
		if (n < 2) {
			Vector3d pos = n == 1 ? new Vector3d(points.get(0)) : new Vector3d();
			sampleArcLength = new double[] { 0d };
			samplePosition = new Vector3d[] { pos };
			sampleTangent = new Vector3d[] { new Vector3d(0, 0, 1) };
			sampleNormalFixedZ = new Vector3d[] { perpendicularOf(sampleTangent[0]) };
			sampleNormalRmf = new Vector3d[] { new Vector3d(sampleNormalFixedZ[0]) };
			totalLength = 0d;
			return;
		}

		int segmentCount = n - 1;
		int sampleCount = segmentCount * SAMPLES_PER_SEGMENT + 1;
		sampleArcLength = new double[sampleCount];
		samplePosition = new Vector3d[sampleCount];
		sampleTangent = new Vector3d[sampleCount];

		int idx = 0;
		for (int seg = 0; seg < segmentCount; seg++) {
			for (int k = 0; k < SAMPLES_PER_SEGMENT; k++) {
				double t = seg + (double) k / SAMPLES_PER_SEGMENT;
				samplePosition[idx] = evaluatePosition(t);
				idx++;
			}
		}
		samplePosition[idx] = evaluatePosition(segmentCount); // last point
		idx++;

		sampleArcLength[0] = 0d;
		for (int i = 1; i < sampleCount; i++) {
			double d = samplePosition[i].distance(samplePosition[i - 1]);
			sampleArcLength[i] = sampleArcLength[i - 1] + d;
		}
		totalLength = sampleArcLength[sampleCount - 1];

		for (int i = 0; i < sampleCount; i++) {
			Vector3d prev = samplePosition[Math.max(i - 1, 0)];
			Vector3d next = samplePosition[Math.min(i + 1, sampleCount - 1)];
			Vector3d diff = new Vector3d(next).sub(prev);
			if (diff.lengthSquared() < 1e-12) {
				sampleTangent[i] = new Vector3d(0, 0, 1);
			} else {
				sampleTangent[i] = diff.normalize();
			}
		}

		buildFixedZNormals(sampleCount);
		buildRmfNormals(sampleCount);
	}

	// Second axis = projection of world Z onto the plane perpendicular to the tangent.
	// Falls back to a different axis where the tangent is (nearly) parallel to Z.
	private void buildFixedZNormals(int sampleCount) {
		sampleNormalFixedZ = new Vector3d[sampleCount];
		for (int i = 0; i < sampleCount; i++) {
			sampleNormalFixedZ[i] = perpendicularOf(sampleTangent[i]);
		}
	}

	private static Vector3d perpendicularOf(Vector3d tangent) {
		Vector3d worldUp = new Vector3d(0, 0, 1);
		double alignment = Math.abs(tangent.dot(worldUp));
		Vector3d reference = (alignment > 0.95) ? new Vector3d(1, 0, 0) : worldUp;
		Vector3d normal = new Vector3d(reference).sub(new Vector3d(tangent).mul(tangent.dot(reference)));
		if (normal.lengthSquared() < 1e-12) {
			// extremely unlucky cancellation; try the other fallback axis
			reference = (reference.x != 0) ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
			normal = new Vector3d(reference).sub(new Vector3d(tangent).mul(tangent.dot(reference)));
		}
		return normal.normalize();
	}

	// Rotation-minimizing frame via the double reflection method (Wang et al. 2008),
	// propagated along the already-built arc-length sample table.
	private void buildRmfNormals(int sampleCount) {
		sampleNormalRmf = new Vector3d[sampleCount];
		sampleNormalRmf[0] = perpendicularOf(sampleTangent[0]);

		for (int i = 0; i + 1 < sampleCount; i++) {
			Vector3d p0 = samplePosition[i];
			Vector3d p1 = samplePosition[i + 1];
			Vector3d t0 = sampleTangent[i];
			Vector3d t1 = sampleTangent[i + 1];
			Vector3d r0 = sampleNormalRmf[i];

			Vector3d v1 = new Vector3d(p1).sub(p0);
			double c1 = v1.dot(v1);
			Vector3d rL;
			Vector3d tL;
			if (c1 < 1e-12) {
				rL = new Vector3d(r0);
				tL = new Vector3d(t0);
			} else {
				rL = new Vector3d(r0).sub(new Vector3d(v1).mul(2d * v1.dot(r0) / c1));
				tL = new Vector3d(t0).sub(new Vector3d(v1).mul(2d * v1.dot(t0) / c1));
			}

			Vector3d v2 = new Vector3d(t1).sub(tL);
			double c2 = v2.dot(v2);
			Vector3d r1;
			if (c2 < 1e-12) {
				r1 = rL;
			} else {
				r1 = new Vector3d(rL).sub(new Vector3d(v2).mul(2d * v2.dot(rL) / c2));
			}
			// re-orthogonalize against t1 and normalize to keep numerical drift in check
			r1.sub(new Vector3d(t1).mul(r1.dot(t1)));
			if (r1.lengthSquared() < 1e-12) {
				r1 = perpendicularOf(t1);
			} else {
				r1.normalize();
			}
			sampleNormalRmf[i + 1] = r1;
		}
	}
}
