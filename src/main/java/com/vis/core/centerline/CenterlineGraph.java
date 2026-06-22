/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.centerline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.joml.Vector3d;

import com.vis.core.slicer.Centerline3D;

/**
 * The full branching centerline topology extracted from a skeleton (or
 * built manually): every {@link CenterlineNode} and {@link CenterlineBranch}
 * is always kept. "Pruning" to a region of interest is non-destructive:
 * {@link #extractBranch(int)} and {@link #extractPath(int, int)} just hand
 * back a freshly built {@link Centerline3D} for the requested subset,
 * without modifying or discarding anything from the graph itself, so a
 * different branch/path can always be selected afterwards.
 */
public class CenterlineGraph {

	private final Map<Integer, CenterlineNode> nodes = new LinkedHashMap<>();
	private final Map<Integer, CenterlineBranch> branches = new LinkedHashMap<>();
	private int nextNodeId = 0;
	private int nextBranchId = 0;

	public CenterlineNode addNode(Vector3d position) {
		int id = nextNodeId++;
		CenterlineNode node = new CenterlineNode(id, position);
		nodes.put(id, node);
		return node;
	}

	public CenterlineBranch addBranch(int startNodeId, int endNodeId, List<Vector3d> controlPoints) {
		if (!nodes.containsKey(startNodeId) || !nodes.containsKey(endNodeId)) {
			throw new IllegalArgumentException("Unknown node id");
		}
		int id = nextBranchId++;
		CenterlineBranch branch = new CenterlineBranch(id, startNodeId, endNodeId, controlPoints);
		branches.put(id, branch);
		nodes.get(startNodeId).addBranchId(id);
		nodes.get(endNodeId).addBranchId(id);
		return branch;
	}

	public Collection<CenterlineNode> getNodes() {
		return Collections.unmodifiableCollection(nodes.values());
	}

	public Collection<CenterlineBranch> getBranches() {
		return Collections.unmodifiableCollection(branches.values());
	}

	public CenterlineNode getNode(int id) {
		return nodes.get(id);
	}

	public CenterlineBranch getBranch(int id) {
		return branches.get(id);
	}

	public List<CenterlineNode> getLeafNodes() {
		List<CenterlineNode> out = new ArrayList<>();
		for (CenterlineNode n : nodes.values()) {
			if (n.getDegree() == 1) out.add(n);
		}
		return out;
	}

	public List<CenterlineNode> getBranchPointNodes() {
		List<CenterlineNode> out = new ArrayList<>();
		for (CenterlineNode n : nodes.values()) {
			if (n.getDegree() >= 3) out.add(n);
		}
		return out;
	}

	/** Non-destructive: a fresh Centerline3D for just this one branch. */
	public Centerline3D extractBranch(int branchId) {
		CenterlineBranch b = branches.get(branchId);
		if (b == null) {
			throw new IllegalArgumentException("Unknown branch id: " + branchId);
		}
		Centerline3D curve = new Centerline3D();
		for (Vector3d p : b.getControlPoints()) {
			curve.addControlPoint(p);
		}
		return curve;
	}

	/**
	 * Non-destructive: the shortest (by total arc length) chain of branches
	 * connecting two nodes, concatenated into a single Centerline3D - this
	 * is the entry point for vascular CPR/straightening across a
	 * bifurcation (e.g. "from this point in the aorta to this point in the
	 * renal artery").
	 */
	public Centerline3D extractPath(int nodeIdA, int nodeIdB) {
		List<CenterlineBranch> pathBranches = shortestPathBranches(nodeIdA, nodeIdB);
		Centerline3D curve = new Centerline3D();
		int currentNode = nodeIdA;
		for (CenterlineBranch b : pathBranches) {
			boolean forward = b.getStartNodeId() == currentNode;
			List<Vector3d> ordered = forward ? b.getControlPoints() : reversed(b.getControlPoints());
			// Skip the first point of every branch after the first: it's the same
			// junction node the previous branch already ended on.
			int startIdx = curve.isEmpty() ? 0 : 1;
			for (int i = startIdx; i < ordered.size(); i++) {
				curve.addControlPoint(ordered.get(i));
			}
			currentNode = forward ? b.getEndNodeId() : b.getStartNodeId();
		}
		return curve;
	}

	private static List<Vector3d> reversed(List<Vector3d> points) {
		List<Vector3d> out = new ArrayList<>(points);
		Collections.reverse(out);
		return out;
	}

	/**
	 * Builds a new, simplified graph with short leaf spurs removed - the
	 * usual skeleton-pruning step, since thinning a discretized (voxel)
	 * mask routinely produces tiny spurious branches off an otherwise clean
	 * vessel/structure. Iterative: removing one spur can expose another
	 * (e.g. a node that had three branches drops to a leaf once its
	 * shortest neighbor is pruned), so this repeats until nothing shorter
	 * than {@code minLengthMm} remains. Any node left with exactly two
	 * branches afterwards (a former bifurcation that lost one arm) is
	 * spliced away, joining its two remaining branches into one continuous
	 * branch, so the result doesn't accumulate pass-through nodes.
	 *
	 * This graph itself is never modified - the result is a brand new
	 * {@link CenterlineGraph}, so callers can keep the original around to
	 * re-prune with a different threshold or fall back to the unpruned
	 * topology.
	 */
	public CenterlineGraph pruneShortLeafBranches(double minLengthMm) {
		Map<Integer, Segment> live = new LinkedHashMap<>();
		Map<Integer, Set<Integer>> touching = new HashMap<>(); // nodeId -> live segment ids touching it
		for (Integer nodeId : nodes.keySet()) {
			touching.put(nodeId, new HashSet<>());
		}
		int syntheticId = 0;
		for (CenterlineBranch b : branches.values()) {
			live.put(syntheticId, new Segment(b.getStartNodeId(), b.getEndNodeId(), b.getControlPoints()));
			touching.get(b.getStartNodeId()).add(syntheticId);
			touching.get(b.getEndNodeId()).add(syntheticId);
			syntheticId++;
		}

		boolean changed = true;
		while (changed && live.size() > 1) {
			changed = false;
			for (Integer segId : new ArrayList<>(live.keySet())) {
				Segment seg = live.get(segId);
				int degStart = touching.get(seg.startNodeId).size();
				int degEnd = touching.get(seg.endNodeId).size();
				boolean isLeafSegment = (degStart == 1 || degEnd == 1);
				if (isLeafSegment && seg.lengthMm() < minLengthMm) {
					live.remove(segId);
					touching.get(seg.startNodeId).remove(segId);
					touching.get(seg.endNodeId).remove(segId);
					changed = true;
				}
			}
		}

		// Splice away any node that now has exactly two branches, merging them into one.
		boolean spliced = true;
		while (spliced) {
			spliced = false;
			for (Integer nodeId : touching.keySet()) {
				Set<Integer> segIds = touching.get(nodeId);
				if (segIds.size() != 2) continue;
				Iterator<Integer> it = segIds.iterator();
				int segId1 = it.next();
				int segId2 = it.next();
				Segment s1 = live.get(segId1);
				Segment s2 = live.get(segId2);
				int far1 = (s1.startNodeId == nodeId) ? s1.endNodeId : s1.startNodeId;
				int far2 = (s2.startNodeId == nodeId) ? s2.endNodeId : s2.startNodeId;
				// far1/far2 land back on nodeId itself when s1/s2 is a self-loop (both its
				// endpoints are nodeId) - splicing would then try to re-touch a node we are
				// about to remove. Skip in that case too, not just the far1==far2 case.
				if (far1 == far2 || far1 == nodeId || far2 == nodeId) continue;

				List<Vector3d> pts1 = (s1.endNodeId == nodeId) ? s1.points : reversed(s1.points); // far1 -> nodeId
				List<Vector3d> pts2 = (s2.startNodeId == nodeId) ? s2.points : reversed(s2.points); // nodeId -> far2
				List<Vector3d> mergedPts = new ArrayList<>(pts1);
				mergedPts.addAll(pts2.subList(1, pts2.size()));

				live.remove(segId1);
				live.remove(segId2);
				touching.get(far1).remove(segId1);
				touching.get(far2).remove(segId2);
				touching.remove(nodeId);

				int newSegId = syntheticId++;
				live.put(newSegId, new Segment(far1, far2, mergedPts));
				touching.get(far1).add(newSegId);
				touching.get(far2).add(newSegId);
				spliced = true;
				break; // touching/live changed - rescan from the top
			}
		}

		CenterlineGraph pruned = new CenterlineGraph();
		Map<Integer, Integer> oldToNewNodeId = new HashMap<>();
		for (Map.Entry<Integer, Set<Integer>> entry : touching.entrySet()) {
			if (entry.getValue().isEmpty()) continue; // fully pruned away
			CenterlineNode oldNode = nodes.get(entry.getKey());
			CenterlineNode newNode = pruned.addNode(oldNode.getPosition());
			oldToNewNodeId.put(entry.getKey(), newNode.getId());
		}
		for (Segment seg : live.values()) {
			Integer newStart = oldToNewNodeId.get(seg.startNodeId);
			Integer newEnd = oldToNewNodeId.get(seg.endNodeId);
			if (newStart != null && newEnd != null) {
				pruned.addBranch(newStart, newEnd, seg.points);
			}
		}
		return pruned;
	}

	/** Local working representation used only inside pruneShortLeafBranches - not tied to a CenterlineBranch's fixed id/graph membership. */
	private static final class Segment {
		final int startNodeId;
		final int endNodeId;
		final List<Vector3d> points;

		Segment(int startNodeId, int endNodeId, List<Vector3d> points) {
			this.startNodeId = startNodeId;
			this.endNodeId = endNodeId;
			this.points = points;
		}

		double lengthMm() {
			double total = 0;
			for (int i = 1; i < points.size(); i++) {
				total += points.get(i).distance(points.get(i - 1));
			}
			return total;
		}
	}

	private List<CenterlineBranch> shortestPathBranches(int nodeIdA, int nodeIdB) {
		if (!nodes.containsKey(nodeIdA) || !nodes.containsKey(nodeIdB)) {
			throw new IllegalArgumentException("Unknown node id");
		}
		Map<Integer, Double> dist = new HashMap<>();
		Map<Integer, Integer> viaBranch = new HashMap<>();
		Set<Integer> visited = new HashSet<>();
		PriorityQueue<NodeDist> queue = new PriorityQueue<>();

		dist.put(nodeIdA, 0d);
		queue.add(new NodeDist(nodeIdA, 0d));

		while (!queue.isEmpty()) {
			NodeDist current = queue.poll();
			if (!visited.add(current.nodeId)) continue;
			if (current.nodeId == nodeIdB) break;

			CenterlineNode node = nodes.get(current.nodeId);
			for (int branchId : node.getBranchIds()) {
				CenterlineBranch b = branches.get(branchId);
				int neighbor = (b.getStartNodeId() == current.nodeId) ? b.getEndNodeId() : b.getStartNodeId();
				if (visited.contains(neighbor)) continue;
				double candidate = current.dist + b.getLengthMm();
				if (candidate < dist.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
					dist.put(neighbor, candidate);
					viaBranch.put(neighbor, branchId);
					queue.add(new NodeDist(neighbor, candidate));
				}
			}
		}

		if (!viaBranch.containsKey(nodeIdB) && nodeIdA != nodeIdB) {
			throw new IllegalStateException("No path between node " + nodeIdA + " and node " + nodeIdB);
		}

		List<CenterlineBranch> path = new ArrayList<>();
		int walk = nodeIdB;
		while (walk != nodeIdA) {
			int branchId = viaBranch.get(walk);
			CenterlineBranch b = branches.get(branchId);
			path.add(b);
			walk = (b.getStartNodeId() == walk) ? b.getEndNodeId() : b.getStartNodeId();
		}
		Collections.reverse(path);
		return path;
	}

	private static final class NodeDist implements Comparable<NodeDist> {
		final int nodeId;
		final double dist;

		NodeDist(int nodeId, double dist) {
			this.nodeId = nodeId;
			this.dist = dist;
		}

		@Override
		public int compareTo(NodeDist o) {
			return Double.compare(dist, o.dist);
		}
	}
}
