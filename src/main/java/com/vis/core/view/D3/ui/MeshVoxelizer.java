/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rasterizes a triangle mesh ({@link MeshData}) back into a binary
 * {@link VolumeData} mask - the inverse of {@link MarchingCubes}. Vertex
 * coordinates are assumed to already be in the same "local volume mm
 * space" {@code MarchingCubes.generateMesh()} produces (vertex = voxel
 * index * spacing, no IPP/IOP offset or rotation applied), so a mesh that
 * came from this app's own marching-cubes pipeline voxelizes back onto a
 * grid with the same spacing without any extra transform.
 *
 * Uses the standard per-Z-slice scanline approach: for each output slice,
 * intersect every triangle with that Z plane to get 2D line segments, then
 * fill each row with the odd-even rule using those segments' crossings.
 */
public class MeshVoxelizer {

	private MeshVoxelizer() {
	}

	public static VolumeData voxelize(MeshData mesh, double pixelSpacingX, double pixelSpacingY,
			double sliceThickness, int outWidth, int outHeight, int outDepth) {
		byte[] mask = new byte[outWidth * outHeight * outDepth];
		int triCount = mesh.indices.length / 3;

		for (int z = 0; z < outDepth; z++) {
			double zMm = z * sliceThickness;
			List<double[]> segments = new ArrayList<>();
			for (int t = 0; t < triCount; t++) {
				int i0 = mesh.indices[t * 3];
				int i1 = mesh.indices[t * 3 + 1];
				int i2 = mesh.indices[t * 3 + 2];
				double[] seg = triangleZIntersection(vertexAt(mesh, i0), vertexAt(mesh, i1), vertexAt(mesh, i2), zMm);
				if (seg != null) segments.add(seg);
			}
			if (segments.isEmpty()) continue;

			int sliceBase = z * outWidth * outHeight;
			for (int y = 0; y < outHeight; y++) {
				double yMm = y * pixelSpacingY;
				List<Double> xs = new ArrayList<>();
				for (double[] seg : segments) {
					Double x = segmentYIntersectionX(seg, yMm);
					if (x != null) xs.add(x);
				}
				if (xs.size() < 2) continue;
				Collections.sort(xs);

				int rowBase = sliceBase + y * outWidth;
				for (int k = 0; k + 1 < xs.size(); k += 2) {
					int xStart = (int) Math.ceil(xs.get(k) / pixelSpacingX);
					int xEnd = (int) Math.floor(xs.get(k + 1) / pixelSpacingX);
					int from = Math.max(0, xStart);
					int to = Math.min(outWidth - 1, xEnd);
					for (int x = from; x <= to; x++) {
						mask[rowBase + x] = 1;
					}
				}
			}
		}

		VolumeData vol = new VolumeData(outWidth, outHeight, outDepth, mask);
		vol.pixelSpacingX = pixelSpacingX;
		vol.pixelSpacingY = pixelSpacingY;
		vol.sliceThickness = sliceThickness;
		return vol;
	}

	private static double[] vertexAt(MeshData mesh, int i) {
		return new double[] { mesh.vertices[i * 3], mesh.vertices[i * 3 + 1], mesh.vertices[i * 3 + 2] };
	}

	/** Intersection of a triangle's edges with the Z=z plane, as a 2D (x,y) line segment, or null if there isn't one. */
	private static double[] triangleZIntersection(double[] p0, double[] p1, double[] p2, double z) {
		double[][] pts = { p0, p1, p2 };
		List<double[]> crossings = new ArrayList<>();
		for (int e = 0; e < 3; e++) {
			double[] a = pts[e];
			double[] b = pts[(e + 1) % 3];
			double za = a[2] - z, zb = b[2] - z;
			boolean crosses = (za >= 0 && zb < 0) || (za < 0 && zb >= 0);
			if (crosses) {
				double t = (z - a[2]) / (b[2] - a[2]);
				double x = a[0] + t * (b[0] - a[0]);
				double y = a[1] + t * (b[1] - a[1]);
				crossings.add(new double[] { x, y });
			}
		}
		if (crossings.size() == 2) {
			double[] c0 = crossings.get(0), c1 = crossings.get(1);
			return new double[] { c0[0], c0[1], c1[0], c1[1] };
		}
		return null;
	}

	/** X where a 2D segment crosses horizontal line y, using a half-open interval test (avoids double-counting shared vertices). */
	private static Double segmentYIntersectionX(double[] seg, double y) {
		double y1 = seg[1], y2 = seg[3];
		boolean crosses = (y1 <= y && y2 > y) || (y2 <= y && y1 > y);
		if (!crosses) return null;
		double t = (y - y1) / (y2 - y1);
		return seg[0] + t * (seg[2] - seg[0]);
	}
}
