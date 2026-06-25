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
package com.vis.core.view.D3.ui;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.vis.core.view.D3.endo.EndoPathPicker;

/**
 * 3D裁断バウンディングボックスに対するマウス操作（面の選択＝ヒットテスト／面のドラッグ＝リサイズ／
 * 全体の平行移動）を計算する純粋ユーティリティ。GLには依存せず、ローカル単位立方体空間
 * （ボリュームと同じ -0.5〜0.5、{@link EndoPathPicker} と同一規約）で完結する。
 *
 * 面IDの規約は {@link ClipBoxRenderer} と一致させている: 0:X- 1:X+ 2:Y- 3:Y+ 4:Z- 5:Z+。
 *
 * @author tatsunidas
 */
public final class ClipBoxInteractor {

	/** これ以上は薄くできない最小の厚み（ローカル単位）。面同士がすり抜けるのを防ぐ。 */
	public static final float MIN_THICKNESS = 0.02f;

	private static final float CUBE_MIN = -0.5f;
	private static final float CUBE_MAX = 0.5f;
	private static final float IN_RANGE_TOL = 1e-3f;
	private static final float PARALLEL_EPSILON = 1e-6f;

	private ClipBoxInteractor() {
	}

	private static float comp(Vector3f v, int axis) {
		return axis == 0 ? v.x : (axis == 1 ? v.y : v.z);
	}

	/** スクリーン座標からローカル空間のレイ(origin/dir)を構築する。dirは単位ベクトル。失敗時 null。 */
	private static Vector3f[] buildRay(Matrix4f mvp, int mx, int my, int w, int h) {
		if (w <= 0 || h <= 0)
			return null;
		Matrix4f invMvp = new Matrix4f(mvp).invert();
		float ndcX = (mx / (float) w) * 2f - 1f;
		float ndcY = 1f - (my / (float) h) * 2f;
		Vector3f origin = EndoPathPicker.unprojectNdc(invMvp, ndcX, ndcY, -1f);
		Vector3f far = EndoPathPicker.unprojectNdc(invMvp, ndcX, ndcY, 1f);
		Vector3f dir = new Vector3f(far).sub(origin);
		if (dir.lengthSquared() < 1e-12f)
			return null;
		dir.normalize();
		return new Vector3f[] { origin, dir };
	}

	/**
	 * マウス位置でヒットしている面のIDを返す。どの面にも当たらなければ -1。
	 * 6面の平面と交差判定し、交点が面の矩形内に収まり、かつカメラに最も近い(t最小)面を選ぶ。
	 */
	public static int pickFace(Matrix4f mvp, int mx, int my, int w, int h, Vector3f min, Vector3f max) {
		Vector3f[] ray = buildRay(mvp, mx, my, w, h);
		if (ray == null)
			return -1;
		Vector3f o = ray[0];
		Vector3f d = ray[1];

		int bestFace = -1;
		float bestT = Float.MAX_VALUE;

		for (int faceId = 0; faceId < 6; faceId++) {
			int axis = faceId / 2;
			boolean isMax = (faceId % 2) == 1;
			float planeCoord = isMax ? comp(max, axis) : comp(min, axis);

			float dAxis = comp(d, axis);
			if (Math.abs(dAxis) < PARALLEL_EPSILON)
				continue;
			float t = (planeCoord - comp(o, axis)) / dAxis;
			if (t <= 0f)
				continue;

			Vector3f hit = new Vector3f(o).add(new Vector3f(d).mul(t));

			// 面に張る他2軸が範囲内かを確認
			boolean inside = true;
			for (int other = 0; other < 3; other++) {
				if (other == axis)
					continue;
				float val = comp(hit, other);
				if (val < comp(min, other) - IN_RANGE_TOL || val > comp(max, other) + IN_RANGE_TOL) {
					inside = false;
					break;
				}
			}
			if (inside && t < bestT) {
				bestT = t;
				bestFace = faceId;
			}
		}
		return bestFace;
	}

	/**
	 * 面ドラッグ中の、その面の軸方向の新しい座標値を返す（クランプ済み）。計算不能なら {@link Float#NaN}。
	 * ピッキングレイと「ボックス中心を通る面の軸方向の直線」との最近接点の軸成分を採用する。
	 * 立方体境界 [-0.5,0.5] と対面との最小厚みでクランプする。
	 */
	public static float dragFaceCoord(Matrix4f mvp, int mx, int my, int w, int h, int faceId,
			Vector3f min, Vector3f max) {
		Vector3f[] ray = buildRay(mvp, mx, my, w, h);
		if (ray == null)
			return Float.NaN;
		Vector3f o = ray[0];
		Vector3f d2 = ray[1]; // ray dir (unit)

		int axis = faceId / 2;
		boolean isMax = (faceId % 2) == 1;

		// 軸の直線: A = boxCenter, 方向 d1 = 単位軸ベクトル
		Vector3f center = new Vector3f(min).add(max).mul(0.5f);
		Vector3f d1 = new Vector3f(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);

		// 2直線の最近接点（軸直線側パラメータ s）
		Vector3f w0 = new Vector3f(o).sub(center);
		float b = d2.dot(d1);
		float denom = 1f - b * b; // a=c=1（両方単位ベクトル）
		if (Math.abs(denom) < PARALLEL_EPSILON)
			return Float.NaN; // レイが軸とほぼ平行
		float dd = d2.dot(w0);
		float e = d1.dot(w0);
		float s = (e - b * dd) / denom; // A + s*d1 が最近接点（d1は単位軸なので軸座標は center + s）

		float newCoord = comp(center, axis) + s;

		// クランプ
		newCoord = Math.max(CUBE_MIN, Math.min(CUBE_MAX, newCoord));
		if (isMax) {
			newCoord = Math.max(newCoord, comp(min, axis) + MIN_THICKNESS);
		} else {
			newCoord = Math.min(newCoord, comp(max, axis) - MIN_THICKNESS);
		}
		return newCoord;
	}

	/**
	 * 全体平行移動用に、ボックス中心を通りカメラ前方向を法線とする平面とレイの交点を返す。
	 * 失敗時 null。呼び出し側がドラッグ開始時とのこの交点の差分を移動量として使う。
	 */
	public static Vector3f computeTranslatePlaneHit(Matrix4f mvp, int mx, int my, int w, int h, Vector3f boxCenter) {
		Vector3f[] ray = buildRay(mvp, mx, my, w, h);
		if (ray == null)
			return null;
		Matrix4f invMvp = new Matrix4f(mvp).invert();
		Vector3f forward = EndoPathPicker.computeForward(invMvp);
		Vector3f hit = EndoPathPicker.intersectRayPlane(ray[0], ray[1], forward, boxCenter);
		if (hit == null || !Float.isFinite(hit.x) || !Float.isFinite(hit.y) || !Float.isFinite(hit.z))
			return null;
		return hit;
	}
}
