package com.vis.core.view.D3.endo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.Test;

public class EndoPathPickerTest {

	private static final float EPS = 1e-3f;

	private static void assertVectorEquals(Vector3f expected, Vector3f actual, float eps) {
		assertEquals(expected.x, actual.x, eps);
		assertEquals(expected.y, actual.y, eps);
		assertEquals(expected.z, actual.z, eps);
	}

	@Test
	public void testIntersectRayPlane_hits() {
		Vector3f rayOrigin = new Vector3f(0f, 0f, 0f);
		Vector3f rayDir = new Vector3f(0f, 0f, 1f);
		Vector3f planeNormal = new Vector3f(0f, 0f, 1f);
		Vector3f planePoint = new Vector3f(0f, 0f, 5f);

		Vector3f hit = EndoPathPicker.intersectRayPlane(rayOrigin, rayDir, planeNormal, planePoint);
		assertNotNull(hit);
		assertVectorEquals(new Vector3f(0f, 0f, 5f), hit, EPS);
	}

	@Test
	public void testIntersectRayPlane_parallelReturnsNull() {
		Vector3f rayOrigin = new Vector3f(0f, 0f, 0f);
		Vector3f rayDir = new Vector3f(1f, 0f, 0f); // 平面に平行（法線と直交）
		Vector3f planeNormal = new Vector3f(0f, 0f, 1f);
		Vector3f planePoint = new Vector3f(0f, 0f, 5f);

		assertNull(EndoPathPicker.intersectRayPlane(rayOrigin, rayDir, planeNormal, planePoint));
	}

	@Test
	public void testComputeAddPointPosition_identityMvp_screenCenterHitsOrigin() {
		Matrix4f identity = new Matrix4f().identity();
		int w = 100, h = 100;

		Vector3f hit = EndoPathPicker.computeAddPointPosition(identity, w / 2, h / 2, w, h);
		assertNotNull(hit);
		assertVectorEquals(new Vector3f(0f, 0f, 0f), hit, EPS);
	}

	@Test
	public void testComputeAddPointPosition_identityMvp_offCenterClick() {
		Matrix4f identity = new Matrix4f().identity();
		int w = 100, h = 100;
		// ndcX = (75/100)*2-1 = 0.5, ndcY = 1-(50/100)*2 = 0
		Vector3f hit = EndoPathPicker.computeAddPointPosition(identity, 75, 50, w, h);
		assertNotNull(hit);
		assertVectorEquals(new Vector3f(0.5f, 0f, 0f), hit, EPS);
	}

	@Test
	public void testComputeForwardDepth_identityMvp() {
		Matrix4f identity = new Matrix4f().identity();
		float depth = EndoPathPicker.computeForwardDepth(identity, new Vector3f(0f, 0f, 3f));
		assertEquals(4f, depth, EPS);
	}

	@Test
	public void testComputeDragPosition_identityMvp_usesGivenDepth() {
		Matrix4f identity = new Matrix4f().identity();
		int w = 100, h = 100;

		Vector3f hit = EndoPathPicker.computeDragPosition(identity, w / 2, h / 2, w, h, 4f);
		assertNotNull(hit);
		// eye=(0,0,-1), forward=(0,0,1), depth=4 -> planePoint=(0,0,3); 中心レイは(0,0,-1)->(0,0,1)方向なので(0,0,3)で交差
		assertVectorEquals(new Vector3f(0f, 0f, 3f), hit, EPS);
	}

	@Test
	public void testHitTestNearestPoint_identityMvp_findsPointAtScreenCenter() {
		Matrix4f identity = new Matrix4f().identity();
		int w = 100, h = 100;

		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f)); // 中心に投影される

		int idx = EndoPathPicker.hitTestNearestPoint(path, identity, w / 2, h / 2, w, h, 10f);
		assertEquals(0, idx);
	}

	@Test
	public void testHitTestNearestPoint_missReturnsMinusOne() {
		Matrix4f identity = new Matrix4f().identity();
		int w = 100, h = 100;

		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f)); // 中心に投影される

		int idx = EndoPathPicker.hitTestNearestPoint(path, identity, 0, 0, w, h, 10f); // 角をクリック
		assertEquals(-1, idx);
	}

	@Test
	public void testHitTestNearestPoint_emptyPathReturnsMinusOne() {
		Matrix4f identity = new Matrix4f().identity();
		EndoPath3D path = new EndoPath3D();

		int idx = EndoPathPicker.hitTestNearestPoint(path, identity, 50, 50, 100, 100, 10f);
		assertEquals(-1, idx);
	}
}
