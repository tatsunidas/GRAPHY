/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.centerline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Vector3d;

import com.vis.core.slicer.Centerline3D;

/**
 * One edge of a {@link CenterlineGraph}: the geometry between two
 * {@link CenterlineNode}s. Wraps a {@link Centerline3D} for all the curve
 * math (spline, arc-length, frames) - this class only adds graph topology
 * (which nodes it connects) on top of that.
 */
public class CenterlineBranch {

	private final int id;
	private final int startNodeId;
	private final int endNodeId;
	private final List<Vector3d> controlPoints; // ordered start -> end, physical mm
	private final Centerline3D curve;

	/** Optional, parallel to controlPoints - e.g. a distance-transform radius estimate per point. */
	private double[] radiusMmPerControlPoint;

	CenterlineBranch(int id, int startNodeId, int endNodeId, List<Vector3d> controlPoints) {
		if (controlPoints == null || controlPoints.size() < 2) {
			throw new IllegalArgumentException("A branch needs at least 2 control points");
		}
		this.id = id;
		this.startNodeId = startNodeId;
		this.endNodeId = endNodeId;
		this.controlPoints = new ArrayList<>(controlPoints);
		this.curve = new Centerline3D();
		for (Vector3d p : controlPoints) {
			curve.addControlPoint(p);
		}
	}

	public int getId() {
		return id;
	}

	public int getStartNodeId() {
		return startNodeId;
	}

	public int getEndNodeId() {
		return endNodeId;
	}

	public List<Vector3d> getControlPoints() {
		return Collections.unmodifiableList(controlPoints);
	}

	public Centerline3D getCurve() {
		return curve;
	}

	public double getLengthMm() {
		return curve.getTotalLength();
	}

	public double[] getRadiusMmPerControlPoint() {
		return radiusMmPerControlPoint;
	}

	public void setRadiusMmPerControlPoint(double[] radiusMmPerControlPoint) {
		if (radiusMmPerControlPoint != null && radiusMmPerControlPoint.length != controlPoints.size()) {
			throw new IllegalArgumentException("radius array must be parallel to the control points");
		}
		this.radiusMmPerControlPoint = radiusMmPerControlPoint;
	}
}
