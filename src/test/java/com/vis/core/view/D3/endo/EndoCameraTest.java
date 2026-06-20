package com.vis.core.view.D3.endo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.Test;

public class EndoCameraTest {

	private static final float EPS = 1e-3f;

	private static void assertVectorEquals(Vector3f expected, Vector3f actual, float eps) {
		assertEquals(expected.x, actual.x, eps);
		assertEquals(expected.y, actual.y, eps);
		assertEquals(expected.z, actual.z, eps);
	}

	private static void assertNoNaNOrInf(Matrix4f m) {
		float[] a = new float[16];
		m.get(a);
		for (float v : a) {
			assertFalse("matrix contains NaN/Inf: " + java.util.Arrays.toString(a),
					Float.isNaN(v) || Float.isInfinite(v));
		}
	}

	// lookAtは-Zをforwardに合わせるため、positiveZ()はforwardの逆向きになる（符号規約の確認）
	@Test
	public void testStraightPathIdentityModel_forwardMatchesTangentAntiParallel() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(1f, 0f, 0f));

		EndoCamera cam = new EndoCamera(path);
		cam.setU(0.5f);

		Matrix4f view = cam.getViewMatrix(new Matrix4f().identity());

		Vector3f expectedForward = new Vector3f(1f, 0f, 0f);
		Vector3f extractedPosZ = new Vector3f();
		view.positiveZ(extractedPosZ);

		assertVectorEquals(new Vector3f(expectedForward).negate(), extractedPosZ, EPS);
	}

	@Test
	public void testUpVector_unitLengthAndOrthogonalToForward() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(0.3f, 0.1f, 0.2f));
		path.addPoint(new Vector3f(0.6f, -0.05f, 0.5f));

		EndoCamera cam = new EndoCamera(path);
		cam.setU(0.5f);

		Matrix4f view = cam.getViewMatrix(new Matrix4f().identity());

		Vector3f up = new Vector3f();
		view.positiveY(up);
		Vector3f forward = new Vector3f();
		view.positiveZ(forward);

		assertEquals(1f, up.length(), EPS);
		assertEquals(0f, up.dot(forward), EPS);
	}

	// 既定up軸(0,1,0)にほぼ平行な接線 -> フォールバックが作動してもNaNが出ないこと
	@Test
	public void testTangentNearlyParallelToPrimaryUp_noNaN_fallbackEngaged() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(0.0001f, 1f, 0.0001f));

		EndoCamera cam = new EndoCamera(path);
		cam.setU(0.5f);

		Matrix4f view = cam.getViewMatrix(new Matrix4f().identity());
		assertNoNaNOrInf(view);

		Vector3f up = new Vector3f();
		view.positiveY(up);
		Vector3f forward = new Vector3f();
		view.positiveZ(forward);
		assertEquals(1f, up.length(), EPS);
		assertEquals(0f, up.dot(forward), EPS);
	}

	// 接線が(0,1,0)と完全一致する真の特異点でもNaNが出ないこと
	@Test
	public void testTangentExactlyParallelToPrimaryUp_noNaN() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(0f, 1f, 0f));

		EndoCamera cam = new EndoCamera(path);
		cam.setU(0.5f);

		Matrix4f view = cam.getViewMatrix(new Matrix4f().identity());
		assertNoNaNOrInf(view);
	}

	@Test
	public void testChangingU_eyePositionMatchesPathPosition() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(2f, 0f, 0f));

		EndoCamera cam = new EndoCamera(path);
		Matrix4f model = new Matrix4f().identity();

		for (float u = 0f; u <= 1.0001f; u += 0.25f) {
			float clamped = Math.min(u, 1f);
			cam.setU(clamped);
			Matrix4f view = cam.getViewMatrix(model);

			Vector3f expectedEyeWorld = model
					.transformPosition(new Vector3f(path.getPositionAtNormalizedDistance(clamped)));

			Vector3f recoveredEye = new Vector3f();
			new Matrix4f(view).invert().transformPosition(recoveredEye);

			assertVectorEquals(expectedEyeWorld, recoveredEye, EPS);
		}
	}

	@Test
	public void testNonUniformModelScale_eyePositionReflectsScale() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(1f, 1f, 0f));

		EndoCamera cam = new EndoCamera(path);
		cam.setU(1f); // ローカル(1,1,0)

		Matrix4f model = new Matrix4f().identity().scale(1f, 2f, 1f);

		Matrix4f view = cam.getViewMatrix(model);
		Vector3f recoveredEye = new Vector3f();
		new Matrix4f(view).invert().transformPosition(recoveredEye);

		assertVectorEquals(new Vector3f(1f, 2f, 0f), recoveredEye, EPS); // local(1,1,0) -> world(1,2,0)
	}

	@Test
	public void testSinglePointPath_validNonNaNMatrix() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0.2f, 0.3f, 0.4f));

		EndoCamera cam = new EndoCamera(path);
		cam.setU(0.5f);

		Matrix4f view = cam.getViewMatrix(new Matrix4f().identity());
		assertNoNaNOrInf(view);

		Vector3f recoveredEye = new Vector3f();
		new Matrix4f(view).invert().transformPosition(recoveredEye);
		assertVectorEquals(new Vector3f(0.2f, 0.3f, 0.4f), recoveredEye, EPS);
	}

	@Test
	public void testEmptyPath_throwsIllegalStateException() {
		EndoPath3D path = new EndoPath3D();
		EndoCamera cam = new EndoCamera(path);
		try {
			cam.getViewMatrix(new Matrix4f().identity());
			fail("Expected IllegalStateException for empty path");
		} catch (IllegalStateException expected) {
			// OK
		}
	}

	@Test
	public void testNullPath_throwsIllegalStateException() {
		EndoCamera cam = new EndoCamera();
		try {
			cam.getViewMatrix(new Matrix4f().identity());
			fail("Expected IllegalStateException for null path");
		} catch (IllegalStateException expected) {
			// OK
		}
	}

	@Test
	public void testSetU_clampsToZeroOne() {
		EndoCamera cam = new EndoCamera();
		cam.setU(-1f);
		assertEquals(0f, cam.getU(), EPS);
		cam.setU(2f);
		assertEquals(1f, cam.getU(), EPS);
		cam.setU(0.5f);
		assertEquals(0.5f, cam.getU(), EPS);
	}

	@Test
	public void testSampleLocal_returnsLocalSpaceUnaffectedByModel() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(1f, 0f, 0f));

		EndoCamera cam = new EndoCamera(path);
		cam.setU(0.5f);

		EndoPath3D.PathSample sample = cam.sampleLocal();
		assertVectorEquals(new Vector3f(0.5f, 0f, 0f), sample.position, EPS);
		assertVectorEquals(new Vector3f(1f, 0f, 0f), sample.tangent, EPS);
	}

	@Test
	public void testAddLookDelta_defaultIsZero() {
		EndoCamera cam = new EndoCamera();
		assertEquals(0f, cam.getLookYaw(), EPS);
		assertEquals(0f, cam.getLookPitch(), EPS);
	}

	@Test
	public void testAddLookDelta_accumulates() {
		EndoCamera cam = new EndoCamera();
		cam.addLookDelta(0.1f, 0.05f);
		cam.addLookDelta(0.2f, 0.05f);
		assertEquals(0.3f, cam.getLookYaw(), EPS);
		assertEquals(0.1f, cam.getLookPitch(), EPS);
	}

	@Test
	public void testAddLookDelta_pitchClamped() {
		EndoCamera cam = new EndoCamera();
		float maxPitch = (float) Math.toRadians(85.0);

		cam.addLookDelta(0f, 1000f);
		assertEquals(maxPitch, cam.getLookPitch(), EPS);

		cam.addLookDelta(0f, -2000f);
		assertEquals(-maxPitch, cam.getLookPitch(), EPS);
	}

	// yaw=pitch=0の場合に既存の挙動と完全に一致することは、他の全テスト（addLookDeltaを使わない）が
	// 無改造のまま通ることで確認される。以下はyaw/pitchが実際に向きを変えることの確認。

	@Test
	public void testYaw90Degrees_forwardBecomesPerpendicularToOriginal() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(0f, 0f, 1f));

		EndoCamera cam = new EndoCamera(path);
		cam.setU(0.5f);
		cam.addLookDelta((float) (Math.PI / 2.0), 0f);

		Matrix4f view = cam.getViewMatrix(new Matrix4f().identity());
		assertNoNaNOrInf(view);

		Vector3f extractedForward = new Vector3f();
		view.positiveZ(extractedForward); // 実際の前方向の逆向き（既存テストと同じ抽出方法）

		Vector3f baseForward = new Vector3f(0f, 0f, 1f);
		Vector3f baseUp = new Vector3f(0f, 1f, 0f);

		// 90度yawした後は、元の接線方向・元のup方向の両方とほぼ直交するはず（符号規約に依存しない確認）
		assertEquals(0f, extractedForward.dot(baseForward), EPS);
		assertEquals(0f, extractedForward.dot(baseUp), EPS);
	}

	@Test
	public void testMaxPitch_forwardTiltsStronglyTowardUp() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(0f, 0f, 1f));

		EndoCamera cam = new EndoCamera(path);
		cam.setU(0.5f);
		cam.addLookDelta(0f, 10f); // クランプ上限(85度)まで効く大きな値

		Matrix4f view = cam.getViewMatrix(new Matrix4f().identity());
		assertNoNaNOrInf(view);

		Vector3f extractedForward = new Vector3f();
		view.positiveZ(extractedForward);

		Vector3f baseUp = new Vector3f(0f, 1f, 0f);
		// 最大近くまでpitchすると、前方向はup方向と強く相関するはず（符号には依存しない確認）
		assertTrue(Math.abs(extractedForward.dot(baseUp)) > 0.9f);
	}
}
