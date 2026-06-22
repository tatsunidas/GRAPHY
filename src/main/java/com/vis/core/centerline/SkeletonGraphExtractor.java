/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.centerline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3d;

import com.vis.core.slicer.VolumeSampler;

/**
 * Turns a 1-voxel-wide skeleton mask (see {@link Skeletonizer3D}) into a
 * {@link CenterlineGraph}: classifies skeleton voxels by how many other
 * skeleton voxels touch them (26-connectivity) - 1 neighbor = endpoint,
 * >=3 = bifurcation - then traces the chain of voxels between every pair
 * of such node voxels into one {@link CenterlineBranch}, simplifying each
 * raw voxel chain (Douglas-Peucker) before converting voxel indices to
 * physical (mm) coordinates via {@link VolumeSampler#toPhysical}.
 */
public class SkeletonGraphExtractor {

	private SkeletonGraphExtractor() {
	}

	public static CenterlineGraph extract(byte[] skeletonMask, int w, int h, int d, VolumeSampler sampler,
			double simplifyEpsilonMm) {
		boolean[] vol = new boolean[skeletonMask.length];
		for (int i = 0; i < skeletonMask.length; i++) {
			vol[i] = skeletonMask[i] != 0;
		}

		CenterlineGraph graph = new CenterlineGraph();
		Map<Integer, Integer> voxelIndexToNodeId = new HashMap<>();
		Set<Long> directEdgesSeen = new HashSet<>();
		boolean[] consumed = new boolean[vol.length]; // interior (non-node) voxels already assigned to a branch

		// 1. Register every endpoint/bifurcation voxel as a graph node up front.
		for (int z = 0; z < d; z++) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int idx = index(x, y, z, w, h);
					if (!vol[idx]) continue;
					int degree = countNeighbors(vol, w, h, d, x, y, z);
					if (degree == 1 || degree >= 3) {
						Vector3d physical = sampler.toPhysical(x, y, z);
						CenterlineNode node = graph.addNode(physical);
						voxelIndexToNodeId.put(idx, node.getId());
					}
					// degree == 0 (isolated speck) is treated as noise and skipped;
					// degree == 2 voxels are plain mid-branch points, not nodes.
				}
			}
		}

		// 2. From every node voxel, trace each outgoing direction to the next node voxel.
		for (Map.Entry<Integer, Integer> entry : voxelIndexToNodeId.entrySet()) {
			int startIdx = entry.getKey();
			int startNodeId = entry.getValue();
			int[] startXyz = toXyz(startIdx, w, h);

			for (int[] n : neighbors26(vol, w, h, d, startXyz[0], startXyz[1], startXyz[2])) {
				int neighborIdx = index(n[0], n[1], n[2], w, h);

				if (voxelIndexToNodeId.containsKey(neighborIdx)) {
					// Direct node-to-node adjacency: a 2-point branch with no interior voxels.
					int endNodeId = voxelIndexToNodeId.get(neighborIdx);
					long edgeKey = canonicalEdgeKey(startIdx, neighborIdx);
					if (directEdgesSeen.add(edgeKey)) {
						List<Vector3d> pts = new ArrayList<>();
						pts.add(sampler.toPhysical(startXyz[0], startXyz[1], startXyz[2]));
						pts.add(sampler.toPhysical(n[0], n[1], n[2]));
						graph.addBranch(startNodeId, endNodeId, pts);
					}
					continue;
				}

				if (consumed[neighborIdx]) {
					continue; // this direction was already traced from the other end
				}

				List<int[]> rawPathVoxels = new ArrayList<>();
				rawPathVoxels.add(startXyz);
				rawPathVoxels.add(n);
				consumed[neighborIdx] = true;

				int[] previous = startXyz;
				int[] current = n;
				int endNodeId = -1;
				while (true) {
					int curIdx = index(current[0], current[1], current[2], w, h);
					if (voxelIndexToNodeId.containsKey(curIdx)) {
						endNodeId = voxelIndexToNodeId.get(curIdx);
						break;
					}
					int[] next = nextStep(vol, w, h, d, current, previous);
					if (next == null) {
						// Dead end without reaching a registered node (shouldn't normally happen
						// since degree<=1 voxels are always registered as nodes) - stop tracing.
						break;
					}
					rawPathVoxels.add(next);
					int nextIdx = index(next[0], next[1], next[2], w, h);
					if (!voxelIndexToNodeId.containsKey(nextIdx)) {
						consumed[nextIdx] = true;
					}
					previous = current;
					current = next;
				}

				if (endNodeId >= 0) {
					List<Vector3d> physicalPts = new ArrayList<>(rawPathVoxels.size());
					for (int[] v : rawPathVoxels) {
						physicalPts.add(sampler.toPhysical(v[0], v[1], v[2]));
					}
					List<Vector3d> simplified = douglasPeucker(physicalPts, simplifyEpsilonMm);
					graph.addBranch(startNodeId, endNodeId, simplified);
				}
			}
		}

		return graph;
	}

	// --- voxel-space helpers ------------------------------------------------

	private static int index(int x, int y, int z, int w, int h) {
		return z * w * h + y * w + x;
	}

	private static int[] toXyz(int idx, int w, int h) {
		int z = idx / (w * h);
		int rem = idx % (w * h);
		int y = rem / w;
		int x = rem % w;
		return new int[] { x, y, z };
	}

	private static boolean inBounds(int x, int y, int z, int w, int h, int d) {
		return x >= 0 && x < w && y >= 0 && y < h && z >= 0 && z < d;
	}

	private static int countNeighbors(boolean[] vol, int w, int h, int d, int x, int y, int z) {
		int count = 0;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					int nx = x + dx, ny = y + dy, nz = z + dz;
					if (inBounds(nx, ny, nz, w, h, d) && vol[index(nx, ny, nz, w, h)]) count++;
				}
			}
		}
		return count;
	}

	private static List<int[]> neighbors26(boolean[] vol, int w, int h, int d, int x, int y, int z) {
		List<int[]> out = new ArrayList<>();
		for (int dz = -1; dz <= 1; dz++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					int nx = x + dx, ny = y + dy, nz = z + dz;
					if (inBounds(nx, ny, nz, w, h, d) && vol[index(nx, ny, nz, w, h)]) {
						out.add(new int[] { nx, ny, nz });
					}
				}
			}
		}
		return out;
	}

	/** The one skeleton neighbor of `current` that is not `previous` (current must have degree 2). */
	private static int[] nextStep(boolean[] vol, int w, int h, int d, int[] current, int[] previous) {
		for (int[] n : neighbors26(vol, w, h, d, current[0], current[1], current[2])) {
			if (n[0] != previous[0] || n[1] != previous[1] || n[2] != previous[2]) {
				return n;
			}
		}
		return null;
	}

	private static long canonicalEdgeKey(int idxA, int idxB) {
		long lo = Math.min(idxA, idxB);
		long hi = Math.max(idxA, idxB);
		return (lo << 32) | hi;
	}

	// --- Douglas-Peucker simplification ------------------------------------

	private static List<Vector3d> douglasPeucker(List<Vector3d> points, double epsilonMm) {
		int n = points.size();
		if (n < 3 || epsilonMm <= 0) {
			return new ArrayList<>(points);
		}
		boolean[] keep = new boolean[n];
		keep[0] = true;
		keep[n - 1] = true;
		simplifySegment(points, 0, n - 1, epsilonMm, keep);
		List<Vector3d> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (keep[i]) out.add(points.get(i));
		}
		return out;
	}

	private static void simplifySegment(List<Vector3d> pts, int start, int end, double epsilonMm, boolean[] keep) {
		if (end <= start + 1) return;
		Vector3d a = pts.get(start), b = pts.get(end);
		double maxDist = -1;
		int maxIdx = -1;
		for (int i = start + 1; i < end; i++) {
			double dist = perpendicularDistance(pts.get(i), a, b);
			if (dist > maxDist) {
				maxDist = dist;
				maxIdx = i;
			}
		}
		if (maxDist > epsilonMm) {
			keep[maxIdx] = true;
			simplifySegment(pts, start, maxIdx, epsilonMm, keep);
			simplifySegment(pts, maxIdx, end, epsilonMm, keep);
		}
	}

	private static double perpendicularDistance(Vector3d p, Vector3d a, Vector3d b) {
		Vector3d ab = new Vector3d(b).sub(a);
		double abLenSq = ab.lengthSquared();
		if (abLenSq < 1e-12) {
			return p.distance(a);
		}
		Vector3d ap = new Vector3d(p).sub(a);
		double t = ap.dot(ab) / abLenSq;
		Vector3d proj = new Vector3d(a).add(ab.mul(t));
		return p.distance(proj);
	}
}
