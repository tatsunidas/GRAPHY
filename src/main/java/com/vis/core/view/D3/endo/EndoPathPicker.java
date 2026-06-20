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
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 2Dスクリーン座標とローカルキューブ座標（{@link EndoPath3D}と同じ規約）を相互変換するための
 * 純粋な計算ユーティリティ。GLには依存しない（{@code VolumeEditor.calculateCut()}が
 * 「純粋計算」と「GLCanvasでの呼び出し」を分離している既存パターンと同じ考え方）。
 *
 * ボリュームデータへの本格的なレイキャスティングやGPUデプスバッファの読み出しは行わず、
 * 「カメラ前方向に直交する平面とレイの交差」という単純な幾何計算のみで2Dクリックを3D点に変換する。
 *
 * @author tatsunidas
 */
public final class EndoPathPicker {

	private static final float PARALLEL_EPSILON = 1e-6f;
	private static final float DEGENERATE_W_EPSILON = 1e-8f;

	private EndoPathPicker() {
	}

	/** NDCの1点(ndcZ=-1:near, +1:far)を、mvpの逆行列でローカル空間に逆投影する */
	public static Vector3f unprojectNdc(Matrix4f invMvp, float ndcX, float ndcY, float ndcZ) {
		Vector4f clip = new Vector4f(ndcX, ndcY, ndcZ, 1f);
		invMvp.transform(clip);
		if (Math.abs(clip.w) < DEGENERATE_W_EPSILON) {
			return new Vector3f(clip.x, clip.y, clip.z);
		}
		float invW = 1f / clip.w;
		return new Vector3f(clip.x * invW, clip.y * invW, clip.z * invW);
	}

	/** 画面中心のnear点（カメラの目の位置に相当） */
	public static Vector3f computeEye(Matrix4f invMvp) {
		return unprojectNdc(invMvp, 0f, 0f, -1f);
	}

	/** 画面中心のnear/far点の差から求めたカメラの前方向（単位ベクトル） */
	public static Vector3f computeForward(Matrix4f invMvp) {
		Vector3f eye = computeEye(invMvp);
		Vector3f far = unprojectNdc(invMvp, 0f, 0f, 1f);
		Vector3f dir = far.sub(eye);
		if (dir.lengthSquared() < DEGENERATE_W_EPSILON) {
			return new Vector3f(0f, 0f, 1f);
		}
		return dir.normalize();
	}

	/** レイと平面の交差点。レイが平面とほぼ平行ならnull */
	public static Vector3f intersectRayPlane(Vector3f rayOrigin, Vector3f rayDir, Vector3f planeNormal,
			Vector3f planePoint) {
		float denom = planeNormal.dot(rayDir);
		if (Math.abs(denom) < PARALLEL_EPSILON) {
			return null;
		}
		float t = new Vector3f(planePoint).sub(rayOrigin).dot(planeNormal) / denom;
		return new Vector3f(rayOrigin).add(new Vector3f(rayDir).mul(t));
	}

	/**
	 * 新規点の追加位置を求める。平面はローカル原点(0,0,0)を通り、法線はカメラ前方向。
	 * 失敗（縮退・非有限値）の場合はnull。
	 */
	public static Vector3f computeAddPointPosition(Matrix4f mvp, int mouseX, int mouseY, int canvasWidth,
			int canvasHeight) {
		return computeHitOnForwardPlane(mvp, mouseX, mouseY, canvasWidth, canvasHeight, new Vector3f(0f, 0f, 0f));
	}

	/**
	 * 既存点をドラッグ中の新しい位置を求める。平面はeye+forward*depthを通り、法線はカメラ前方向。
	 * 失敗（縮退・非有限値）の場合はnull。
	 */
	public static Vector3f computeDragPosition(Matrix4f mvp, int mouseX, int mouseY, int canvasWidth,
			int canvasHeight, float depth) {
		Matrix4f invMvp = new Matrix4f(mvp).invert();
		Vector3f eye = computeEye(invMvp);
		Vector3f forward = computeForward(invMvp);
		Vector3f planePoint = new Vector3f(eye).add(new Vector3f(forward).mul(depth));
		return computeHitOnPlane(invMvp, mouseX, mouseY, canvasWidth, canvasHeight, forward, planePoint);
	}

	/** ローカル座標の点の、カメラ前方向に沿った深度(eyeからの距離)を求める */
	public static float computeForwardDepth(Matrix4f mvp, Vector3f pointLocal) {
		Matrix4f invMvp = new Matrix4f(mvp).invert();
		Vector3f eye = computeEye(invMvp);
		Vector3f forward = computeForward(invMvp);
		return new Vector3f(pointLocal).sub(eye).dot(forward);
	}

	/**
	 * 画面上で最もクリック位置に近い制御点のindexを返す。pixelRadius以内に点が無ければ-1。
	 * {@code VolumeEditor.calculateCut()}と同じ投影式（ローカル→クリップ→NDC→スクリーン座標）を使う。
	 */
	public static int hitTestNearestPoint(EndoPath3D path, Matrix4f mvp, int mouseX, int mouseY, int canvasWidth,
			int canvasHeight, float pixelRadius) {
		if (path.isEmpty() || canvasWidth <= 0 || canvasHeight <= 0) {
			return -1;
		}

		int bestIndex = -1;
		float bestDistSq = pixelRadius * pixelRadius;
		Vector4f clip = new Vector4f();

		for (int i = 0; i < path.size(); i++) {
			Vector3f p = path.getPoint(i).getPosition();
			clip.set(p.x, p.y, p.z, 1f);
			mvp.transform(clip);
			if (clip.w <= 0f) {
				continue;
			}
			float ndcX = clip.x / clip.w;
			float ndcY = clip.y / clip.w;
			float screenX = (ndcX + 1f) * 0.5f * canvasWidth;
			float screenY = (1f - ndcY) * 0.5f * canvasHeight;

			float dx = screenX - mouseX;
			float dy = screenY - mouseY;
			float distSq = dx * dx + dy * dy;
			if (distSq <= bestDistSq) {
				bestDistSq = distSq;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	// --- 内部ヘルパー ---

	private static Vector3f computeHitOnForwardPlane(Matrix4f mvp, int mouseX, int mouseY, int canvasWidth,
			int canvasHeight, Vector3f planePoint) {
		Matrix4f invMvp = new Matrix4f(mvp).invert();
		Vector3f forward = computeForward(invMvp);
		return computeHitOnPlane(invMvp, mouseX, mouseY, canvasWidth, canvasHeight, forward, planePoint);
	}

	private static Vector3f computeHitOnPlane(Matrix4f invMvp, int mouseX, int mouseY, int canvasWidth,
			int canvasHeight, Vector3f planeNormal, Vector3f planePoint) {
		if (canvasWidth <= 0 || canvasHeight <= 0) {
			return null;
		}
		float ndcX = (mouseX / (float) canvasWidth) * 2f - 1f;
		float ndcY = 1f - (mouseY / (float) canvasHeight) * 2f;

		Vector3f rayOrigin = unprojectNdc(invMvp, ndcX, ndcY, -1f);
		Vector3f rayFar = unprojectNdc(invMvp, ndcX, ndcY, 1f);
		Vector3f rayDir = rayFar.sub(rayOrigin);
		if (rayDir.lengthSquared() < DEGENERATE_W_EPSILON) {
			return null;
		}
		rayDir.normalize();

		Vector3f hit = intersectRayPlane(rayOrigin, rayDir, planeNormal, planePoint);
		if (hit == null || !isFinite(hit)) {
			return null;
		}
		return hit;
	}

	private static boolean isFinite(Vector3f v) {
		return Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
	}
}
