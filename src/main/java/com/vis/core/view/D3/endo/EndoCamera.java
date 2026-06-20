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
package com.vis.core.view.D3.endo;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 仮想内視鏡カメラ。{@link EndoPath3D}上の現在位置(u: 正規化距離[0,1])から、
 * ワールド/レンダー空間のビュー行列を生成する。
 *
 * 可変状態は path と u のみ。フレーム間でキャッシュする向きの状態は持たない。
 * 真のパラレルトランスポート（回転最小化フレーム）は実装していないため、
 * パスの接線方向が基準up軸(既定+Y)に近づくと、upベクトルが不連続に切り替わることがある
 * （既知の制限。将来的にパラレルトランスポートフレームへの置き換えを推奨）。
 *
 * 座標系: {@link EndoPath3D}の position/tangent はレンダラーのローカルキューブ座標系。
 * {@link #getViewMatrix(Matrix4f)} はワールド/レンダー座標系（model適用後）のビュー行列を返す
 * （{@link com.vis.core.view.D3.ui.Camera#getViewMatrix()} と同じ呼び出し規約）。
 *
 * @author tatsunidas
 */
public class EndoCamera {

	// Gram-Schmidt射影後の長さ^2に対するしきい値。
	// refWorld/forwardWorldは単位ベクトルなので、射影後の長さは sin(θ) (θ=両者の角度)。
	// 1e-6 は θ が平行/反平行から約0.057度以内であることに相当する、縮退判定専用の狭いepsilon。
	private static final float UP_DEGENERATE_EPSILON_SQ = 1e-6f;

	private static final Vector3f DEFAULT_UP_REFERENCE = new Vector3f(0f, 1f, 0f);
	private static final Vector3f FALLBACK_UP_REFERENCE = new Vector3f(0f, 0f, 1f);

	// 上下を向きすぎてupが縮退するのを防ぐためのpitchの可動範囲
	private static final float MAX_LOOK_PITCH = (float) Math.toRadians(85.0);

	private EndoPath3D path;
	private float u = 0f;

	// パスの接線・up基準フレームに対する見回しオフセット（マイクラ風マウスルック用）
	private float lookYaw = 0f;
	private float lookPitch = 0f;

	public EndoCamera() {
	}

	public EndoCamera(EndoPath3D path) {
		this.path = path;
	}

	public EndoPath3D getPath() {
		return path;
	}

	public void setPath(EndoPath3D path) {
		this.path = path;
	}

	public float getU() {
		return u;
	}

	public void setU(float u) {
		this.u = clamp01(u);
	}

	public float getLookYaw() {
		return lookYaw;
	}

	public float getLookPitch() {
		return lookPitch;
	}

	/**
	 * パスの接線方向を基準にした見回しオフセットを加算する（マイクラ風マウスルック）。
	 * {@link com.vis.core.view.D3.ui.Camera#rotate(float, float)}と同じ「差分を加算する」方式。
	 * pitchは{@link #MAX_LOOK_PITCH}にクランプされる（upベクトルの縮退を防ぐため）。
	 */
	public void addLookDelta(float deltaYaw, float deltaPitch) {
		this.lookYaw += deltaYaw;
		this.lookPitch = Math.max(-MAX_LOOK_PITCH, Math.min(MAX_LOOK_PITCH, this.lookPitch + deltaPitch));
	}

	/**
	 * 現在のuにおけるワールド/レンダー空間のビュー行列を返す。
	 * パスの接線方向を基準に、{@link #addLookDelta(float, float)}で設定した見回しオフセット(yaw->pitch)を適用する。
	 *
	 * @param model ローカルキューブ座標系からワールド/レンダー座標系への変換
	 *              （呼び出し側が用意する。例: GLCanvasのcalculateModelMatrix()に相当するもの）
	 * @throws IllegalStateException pathが未設定の場合。pathが空の場合はEndoPath3D由来の例外がそのまま伝播する。
	 */
	public Matrix4f getViewMatrix(Matrix4f model) {
		if (path == null) {
			throw new IllegalStateException("EndoCamera has no path set");
		}
		EndoPath3D.PathSample sample = path.sampleAtNormalizedDistance(u);

		Vector3f eyeWorld = model.transformPosition(new Vector3f(sample.position));
		Vector3f baseForward = model.transformDirection(new Vector3f(sample.tangent)).normalize();
		Vector3f baseUp = computeStableUp(model, baseForward);
		Vector3f baseRight = new Vector3f(baseForward).cross(baseUp).normalize();

		// yaw: baseUp軸まわりの首振り
		Quaternionf yawRot = new Quaternionf().rotateAxis(lookYaw, baseUp);
		Vector3f forwardYawed = yawRot.transform(new Vector3f(baseForward));
		Vector3f rightYawed = yawRot.transform(new Vector3f(baseRight));

		// pitch: yaw後のright軸まわりの首振り
		Quaternionf pitchRot = new Quaternionf().rotateAxis(lookPitch, rightYawed);
		Vector3f finalForward = pitchRot.transform(new Vector3f(forwardYawed)).normalize();
		Vector3f finalUp = pitchRot.transform(new Vector3f(baseUp)).normalize();

		Vector3f centerWorld = new Vector3f(eyeWorld).add(finalForward);

		return new Matrix4f().lookAt(eyeWorld, centerWorld, finalUp);
	}

	/**
	 * ローカルキューブ座標系（model変換前）での現在のposition/tangent。
	 * 将来のカメラ向き表示ギズモ等、ローカル座標系で直接描画したい用途のための拡張点。
	 */
	public EndoPath3D.PathSample sampleLocal() {
		if (path == null) {
			throw new IllegalStateException("EndoCamera has no path set");
		}
		return path.sampleAtNormalizedDistance(u);
	}

	// 3段フォールバック: 既定up軸 -> 副軸 -> モデル変換に依存しない最終手段
	private static Vector3f computeStableUp(Matrix4f model, Vector3f forwardWorld) {
		Vector3f up = projectPerpendicular(model, DEFAULT_UP_REFERENCE, forwardWorld);
		if (up != null) {
			return up;
		}
		up = projectPerpendicular(model, FALLBACK_UP_REFERENCE, forwardWorld);
		if (up != null) {
			return up;
		}
		return arbitraryPerpendicular(forwardWorld);
	}

	// referenceLocalをmodelで変換・正規化し、forwardWorldに直交するようGram-Schmidt射影する。縮退していればnull。
	private static Vector3f projectPerpendicular(Matrix4f model, Vector3f referenceLocal, Vector3f forwardWorld) {
		Vector3f refWorld = model.transformDirection(new Vector3f(referenceLocal));
		if (refWorld.lengthSquared() < 1e-12f) {
			return null;
		}
		refWorld.normalize();

		float d = refWorld.dot(forwardWorld);
		Vector3f projected = new Vector3f(refWorld).sub(new Vector3f(forwardWorld).mul(d));
		if (projected.lengthSquared() < UP_DEGENERATE_EPSILON_SQ) {
			return null;
		}
		return projected.normalize();
	}

	// model変換を介さない、常にforwardWorldに直交する単位ベクトルを返す最終フォールバック
	private static Vector3f arbitraryPerpendicular(Vector3f forwardWorld) {
		Vector3f candidate = Math.abs(forwardWorld.x) < 0.9f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 1f, 0f);
		float d = candidate.dot(forwardWorld);
		Vector3f projected = new Vector3f(candidate).sub(new Vector3f(forwardWorld).mul(d));
		return projected.lengthSquared() < 1e-12f ? new Vector3f(0f, 1f, 0f) : projected.normalize();
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}
}
