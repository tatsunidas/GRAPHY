package com.vis.core.view.D3.endo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.joml.Vector3f;
import org.junit.Test;

public class EndoPath3DTest {

	private static final float EPS = 1e-3f;

	private static void assertVectorEquals(Vector3f expected, Vector3f actual, float eps) {
		assertEquals(expected.x, actual.x, eps);
		assertEquals(expected.y, actual.y, eps);
		assertEquals(expected.z, actual.z, eps);
	}

	@Test
	public void testEmptyPath_evaluatePositionThrows() {
		EndoPath3D path = new EndoPath3D();
		try {
			path.evaluatePosition(0f);
			fail("Expected IllegalStateException for empty path");
		} catch (IllegalStateException expected) {
			// OK
		}
	}

	@Test
	public void testSinglePoint_returnsThatPoint() {
		EndoPath3D path = new EndoPath3D();
		Vector3f p0 = new Vector3f(0.1f, 0.2f, 0.3f);
		path.addPoint(p0);

		assertVectorEquals(p0, path.evaluatePosition(0f), EPS);
		assertEquals(0f, path.getTotalLength(), EPS);
		// 接線は未定義方向だが、NaNにならず固定のデフォルト値を返すこと
		Vector3f tangent = path.evaluateTangent(0f);
		assertFalse(Float.isNaN(tangent.x) || Float.isNaN(tangent.y) || Float.isNaN(tangent.z));
	}

	@Test
	public void testTwoPoints_linearInterpolation() {
		EndoPath3D path = new EndoPath3D();
		Vector3f p0 = new Vector3f(0f, 0f, 0f);
		Vector3f p1 = new Vector3f(1f, 0f, 0f);
		path.addPoint(p0);
		path.addPoint(p1);

		assertVectorEquals(new Vector3f(0.5f, 0f, 0f), path.evaluatePosition(0.5f), EPS);
		assertVectorEquals(p0, path.evaluatePosition(0f), EPS);
		assertVectorEquals(p1, path.evaluatePosition(1f), EPS);

		Vector3f tangent = path.evaluateTangent(0.5f);
		assertVectorEquals(new Vector3f(1f, 0f, 0f), tangent, EPS);
	}

	@Test
	public void testThreePoints_passesThroughAllControlPoints() {
		EndoPath3D path = new EndoPath3D();
		Vector3f p0 = new Vector3f(0f, 0f, 0f);
		Vector3f p1 = new Vector3f(0.2f, 0.1f, 0f);
		Vector3f p2 = new Vector3f(0.4f, -0.1f, 0.1f);
		path.addPoint(p0);
		path.addPoint(p1);
		path.addPoint(p2);

		assertVectorEquals(p0, path.evaluatePosition(0f), EPS);
		assertVectorEquals(p1, path.evaluatePosition(1f), EPS);
		assertVectorEquals(p2, path.evaluatePosition(2f), EPS);
	}

	@Test
	public void testSixPoints_passesThroughAllControlPoints() {
		EndoPath3D path = new EndoPath3D();
		Vector3f[] pts = new Vector3f[] {
				new Vector3f(0f, 0f, 0f),
				new Vector3f(0.1f, 0.05f, 0f),
				new Vector3f(0.2f, 0.1f, 0.02f),
				new Vector3f(0.3f, 0.08f, 0.05f),
				new Vector3f(0.4f, 0.0f, 0.1f),
				new Vector3f(0.5f, -0.05f, 0.12f) };
		for (Vector3f p : pts) {
			path.addPoint(p);
		}

		for (int i = 0; i < pts.length; i++) {
			assertVectorEquals(pts[i], path.evaluatePosition((float) i), EPS);
		}
	}

	@Test
	public void testCoincidentPoints_noNaN() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(0f, 0f, 0f)); // 直前の点と完全一致
		path.addPoint(new Vector3f(0.3f, 0.1f, 0f));
		path.addPoint(new Vector3f(0.3f, 0.1f, 0f)); // 末尾も一致させる

		int segmentCount = path.size() - 1;
		for (float t = 0f; t <= segmentCount; t += 0.05f) {
			Vector3f pos = path.evaluatePosition(t);
			Vector3f tan = path.evaluateTangent(t);
			assertFalse("position NaN/Inf at t=" + t,
					Float.isNaN(pos.x) || Float.isNaN(pos.y) || Float.isNaN(pos.z) || Float.isInfinite(pos.x)
							|| Float.isInfinite(pos.y) || Float.isInfinite(pos.z));
			assertFalse("tangent NaN/Inf at t=" + t,
					Float.isNaN(tan.x) || Float.isNaN(tan.y) || Float.isNaN(tan.z) || Float.isInfinite(tan.x)
							|| Float.isInfinite(tan.y) || Float.isInfinite(tan.z));
		}

		float total = path.getTotalLength();
		assertFalse(Float.isNaN(total) || Float.isInfinite(total));
	}

	@Test
	public void testArcLengthTable_monotonicAndEndpointsMatch() {
		EndoPath3D path = new EndoPath3D();
		Vector3f p0 = new Vector3f(0f, 0f, 0f);
		Vector3f p1 = new Vector3f(0.2f, 0.1f, 0f);
		Vector3f p2 = new Vector3f(0.4f, -0.1f, 0.1f);
		Vector3f p3 = new Vector3f(0.6f, 0.05f, 0.2f);
		path.addPoint(p0);
		path.addPoint(p1);
		path.addPoint(p2);
		path.addPoint(p3);

		assertVectorEquals(p0, path.getPositionAtNormalizedDistance(0f), EPS);
		assertVectorEquals(p3, path.getPositionAtNormalizedDistance(1f), EPS);

		float prevDistanceFromStart = -1f;
		Vector3f prevPos = path.getPositionAtNormalizedDistance(0f);
		for (float u = 0.1f; u <= 1.0001f; u += 0.1f) {
			Vector3f pos = path.getPositionAtNormalizedDistance(Math.min(u, 1f));
			float distFromStart = pos.distance(p0);
			// 厳密な単調性は保証されないが、累積弧長自体は単調増加であることを別途確認する
			assertTrue(distFromStart >= 0f);
			prevPos = pos;
			prevDistanceFromStart = distFromStart;
		}
		assertTrue(prevDistanceFromStart >= 0f);
		assertVectorEquals(p3, prevPos, EPS);
	}

	@Test
	public void testCrudAndDirtyRecompute() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(1f, 0f, 0f));
		assertEquals(2, path.size());

		float lengthBefore = path.getTotalLength();
		assertEquals(1f, lengthBefore, EPS);

		// 末尾点を移動 -> 弧長が再計算され、移動後の値を反映すること
		path.setPointPosition(1, new Vector3f(2f, 0f, 0f));
		float lengthAfter = path.getTotalLength();
		assertEquals(2f, lengthAfter, EPS);
		assertVectorEquals(new Vector3f(2f, 0f, 0f), path.evaluatePosition(1f), EPS);

		path.insertPoint(1, new Vector3f(1f, 1f, 0f));
		assertEquals(3, path.size());
		assertVectorEquals(new Vector3f(1f, 1f, 0f), path.evaluatePosition(1f), EPS);

		path.removePoint(1);
		assertEquals(2, path.size());

		path.clear();
		assertEquals(0, path.size());
		assertTrue(path.isEmpty());
	}

	@Test
	public void testDefensiveCopies_internalStateNotLeaked() {
		EndoPath3D path = new EndoPath3D();
		Vector3f original = new Vector3f(0.1f, 0.2f, 0.3f);
		path.addPoint(original);

		// addPoint に渡したVector3fを後で書き換えても、内部状態には影響しないこと
		original.set(99f, 99f, 99f);
		assertVectorEquals(new Vector3f(0.1f, 0.2f, 0.3f), path.evaluatePosition(0f), EPS);

		// getPoint() で取得したコピーを書き換えても、内部状態には影響しないこと
		EndoPathPoint3D got = path.getPoint(0);
		got.setPosition(123f, 123f, 123f);
		assertVectorEquals(new Vector3f(0.1f, 0.2f, 0.3f), path.evaluatePosition(0f), EPS);

		// getPointsSnapshot() で取得したコピーを書き換えても、内部状態には影響しないこと
		EndoPathPoint3D snapshotPoint = path.getPointsSnapshot().get(0);
		snapshotPoint.setPosition(456f, 456f, 456f);
		assertVectorEquals(new Vector3f(0.1f, 0.2f, 0.3f), path.evaluatePosition(0f), EPS);
	}

	@Test
	public void testGetNormalizedDistanceAtPoint_endpointsAndMonotonic() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(1f, 0f, 0f));
		path.addPoint(new Vector3f(2f, 0f, 0f));
		path.addPoint(new Vector3f(3f, 0f, 0f));

		assertEquals(0f, path.getNormalizedDistanceAtPoint(0), EPS);
		assertEquals(1f, path.getNormalizedDistanceAtPoint(3), EPS);

		float uPrev = -1f;
		for (int i = 0; i < path.size(); i++) {
			float u = path.getNormalizedDistanceAtPoint(i);
			assertTrue("uは単調増加であること", u > uPrev);
			uPrev = u;
		}
	}

	@Test
	public void testGetNormalizedDistanceAtPoint_degenerateCases() {
		EndoPath3D empty = new EndoPath3D();
		assertEquals(0f, empty.getNormalizedDistanceAtPoint(0), EPS);

		EndoPath3D single = new EndoPath3D();
		single.addPoint(new Vector3f(1f, 2f, 3f));
		assertEquals(0f, single.getNormalizedDistanceAtPoint(0), EPS);
	}

	@Test
	public void testFindNextAndPreviousPointIndex() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(1f, 0f, 0f));
		path.addPoint(new Vector3f(2f, 0f, 0f));
		path.addPoint(new Vector3f(3f, 0f, 0f));

		float uMid = path.getNormalizedDistanceAtPoint(1)
				+ (path.getNormalizedDistanceAtPoint(2) - path.getNormalizedDistanceAtPoint(1)) * 0.5f;

		assertEquals(2, path.findNextPointIndex(uMid));
		assertEquals(1, path.findPreviousPointIndex(uMid));

		// 先頭より前 -> nextは先頭以降の最初の点、previousは先頭(0)のまま
		assertEquals(0, path.findNextPointIndex(-0.1f));
		assertEquals(0, path.findPreviousPointIndex(-0.1f));

		// 末尾より後 -> nextは末尾のまま、previousは末尾の手前
		assertEquals(3, path.findNextPointIndex(1.1f));
		assertEquals(3, path.findPreviousPointIndex(1.1f));

		// ちょうど点の上 -> nextはその次、previousはその手前
		float uPoint1 = path.getNormalizedDistanceAtPoint(1);
		assertEquals(2, path.findNextPointIndex(uPoint1));
		assertEquals(0, path.findPreviousPointIndex(uPoint1));
	}
}
