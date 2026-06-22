/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.centerline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Vector3d;

/**
 * A node of a {@link CenterlineGraph}: a skeleton endpoint (degree 1) or
 * bifurcation/branch point (degree &gt;= 3). Physical (LPS, mm) coordinates,
 * matching {@link com.vis.core.slicer.Centerline3D}.
 */
public class CenterlineNode {

	private final int id;
	private final Vector3d position;
	private double radiusMm = Double.NaN; // optional, e.g. from a distance-transform estimate
	private final List<Integer> branchIds = new ArrayList<>();

	CenterlineNode(int id, Vector3d position) {
		this.id = id;
		this.position = new Vector3d(position);
	}

	public int getId() {
		return id;
	}

	public Vector3d getPosition() {
		return new Vector3d(position);
	}

	public double getRadiusMm() {
		return radiusMm;
	}

	public void setRadiusMm(double radiusMm) {
		this.radiusMm = radiusMm;
	}

	/** Number of branches meeting at this node: 1 = endpoint, 2 = mid-branch (rare after extraction), >=3 = bifurcation. */
	public int getDegree() {
		return branchIds.size();
	}

	public List<Integer> getBranchIds() {
		return Collections.unmodifiableList(branchIds);
	}

	void addBranchId(int branchId) {
		branchIds.add(branchId);
	}
}
