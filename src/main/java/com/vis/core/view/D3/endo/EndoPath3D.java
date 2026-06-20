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

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 仮想内視鏡カメラが通る3Dパス。制御点の順序付きリストを保持し、Centripetal Catmull-Rom補間で
 * 滑らかな位置・接線を返す。さらに等速再生用に弧長テーブルを保持し、距離(または[0,1]の正規化距離)
 * を指定した問い合わせにも対応する。
 *
 * 座標系は {@link EndoPathPoint3D} と同じレンダラーのローカルキューブ座標系。
 *
 * v1ではループ（閉じたパス）は扱わない。
 *
 * @author tatsunidas
 */
public class EndoPath3D {

	private static final float CATMULL_ROM_ALPHA = 0.5f; // centripetal
	private static final float MIN_CHORD = 1e-6f; // 制御点が一致している場合のゼロ除算対策
	private static final int SAMPLES_PER_SEGMENT = 20; // 弧長テーブルのサンプリング密度

	private final List<EndoPathPoint3D> points = new ArrayList<>();

	private boolean dirty = true;
	private float[] sampleArcLength;
	private Vector3f[] samplePosition;
	private Vector3f[] sampleTangent;
	private float totalLength = 0f;

	// ===================== CRUD =====================

	public int size() {
		return points.size();
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	public EndoPathPoint3D getPoint(int index) {
		return points.get(index).copy();
	}

	/** 全制御点のコピーからなる読み取り専用リストを返す（内部リストの参照は漏らさない） */
	public List<EndoPathPoint3D> getPointsSnapshot() {
		List<EndoPathPoint3D> copyList = new ArrayList<>(points.size());
		for (EndoPathPoint3D p : points) {
			copyList.add(p.copy());
		}
		return Collections.unmodifiableList(copyList);
	}

	/** 末尾に制御点を追加し、新しいインデックスを返す */
	public int addPoint(Vector3f position) {
		points.add(new EndoPathPoint3D(position));
		markDirty();
		return points.size() - 1;
	}

	public void insertPoint(int index, Vector3f position) {
		points.add(index, new EndoPathPoint3D(position));
		markDirty();
	}

	public void setPointPosition(int index, Vector3f position) {
		points.get(index).setPosition(position);
		markDirty();
	}

	public void removePoint(int index) {
		points.remove(index);
		markDirty();
	}

	public void clear() {
		points.clear();
		markDirty();
	}

	// ===================== スプライン評価 =====================

	/**
	 * パス上の位置を返す。tはセグメント番号空間（0=先頭点、segmentCount=末尾点）。範囲外はクランプする。
	 */
	public Vector3f evaluatePosition(float t) {
		if (points.isEmpty()) {
			throw new IllegalStateException("EndoPath3D has no points");
		}
		if (points.size() == 1) {
			return points.get(0).getPosition();
		}

		int segmentCount = points.size() - 1;
		float ct = clamp(t, 0f, (float) segmentCount);
		int segIndex = (int) Math.floor(ct);
		if (segIndex >= segmentCount) {
			segIndex = segmentCount - 1;
		}
		float s = ct - segIndex;

		if (points.size() == 2) {
			Vector3f p0 = points.get(0).getPosition();
			Vector3f p1 = points.get(1).getPosition();
			return lerp(p0, p1, s);
		}

		Vector3f p0 = getControlPoint(segIndex - 1);
		Vector3f p1 = getControlPoint(segIndex);
		Vector3f p2 = getControlPoint(segIndex + 1);
		Vector3f p3 = getControlPoint(segIndex + 2);
		return catmullRom(p0, p1, p2, p3, s);
	}

	/** パス上の単位接線ベクトルを、position関数の中心差分で数値的に求める */
	public Vector3f evaluateTangent(float t) {
		if (points.isEmpty()) {
			throw new IllegalStateException("EndoPath3D has no points");
		}
		if (points.size() == 1) {
			return new Vector3f(0, 0, 1);
		}

		float h = 1e-3f;
		Vector3f after = evaluatePosition(t + h);
		Vector3f before = evaluatePosition(t - h);
		Vector3f diff = after.sub(before);
		if (diff.lengthSquared() < 1e-12f) {
			return new Vector3f(0, 0, 1);
		}
		return diff.normalize();
	}

	// index < 0 または index >= size のとき、外挿によるファントムポイントを返す
	private Vector3f getControlPoint(int index) {
		int n = points.size();
		if (index < 0) {
			Vector3f p0 = points.get(0).getPosition();
			Vector3f p1 = points.get(1).getPosition();
			return p0.mul(2f).sub(p1); // 2*p0 - p1
		}
		if (index >= n) {
			Vector3f pLast = points.get(n - 1).getPosition();
			Vector3f pPrev = points.get(n - 2).getPosition();
			return pLast.mul(2f).sub(pPrev); // 2*pLast - pPrev
		}
		return points.get(index).getPosition();
	}

	// Centripetal Catmull-Rom (Barry-Goldman型ブレンド)。P1-P2間をs∈[0,1]で評価する。
	private static Vector3f catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float s) {
		float d0 = Math.max(p1.distance(p0), MIN_CHORD);
		float d1 = Math.max(p2.distance(p1), MIN_CHORD);
		float d2 = Math.max(p3.distance(p2), MIN_CHORD);

		float t0 = 0f;
		float t1 = t0 + (float) Math.pow(d0, CATMULL_ROM_ALPHA);
		float t2 = t1 + (float) Math.pow(d1, CATMULL_ROM_ALPHA);
		float t3 = t2 + (float) Math.pow(d2, CATMULL_ROM_ALPHA);

		float t = t1 + s * (t2 - t1);

		Vector3f a1 = lerp(p0, p1, (t - t0) / (t1 - t0));
		Vector3f a2 = lerp(p1, p2, (t - t1) / (t2 - t1));
		Vector3f a3 = lerp(p2, p3, (t - t2) / (t3 - t2));

		Vector3f b1 = lerp(a1, a2, (t - t0) / (t2 - t0));
		Vector3f b2 = lerp(a2, a3, (t - t1) / (t3 - t1));

		return lerp(b1, b2, (t - t1) / (t2 - t1));
	}

	// a, b を変更せず、新しいVector3fでa->bのfrac地点を返す
	private static Vector3f lerp(Vector3f a, Vector3f b, float frac) {
		return new Vector3f(a).lerp(b, frac);
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	// ===================== 等速再生 (弧長ベース) =====================

	public float getTotalLength() {
		ensureFresh();
		return totalLength;
	}

	/** 制御点indexの位置に対応する正規化距離u([0,1])を返す。点が0〜1個の場合は0を返す */
	public float getNormalizedDistanceAtPoint(int index) {
		if (points.size() < 2) {
			return 0f;
		}
		ensureFresh();
		if (totalLength <= 0f) {
			return 0f;
		}
		int sampleIndex = index * SAMPLES_PER_SEGMENT;
		return sampleArcLength[sampleIndex] / totalLength;
	}

	/** currentUより後ろにある直近の制御点indexを返す。無ければ末尾のindex */
	public int findNextPointIndex(float currentU) {
		if (points.isEmpty()) {
			throw new IllegalStateException("EndoPath3D has no points");
		}
		for (int i = 0; i < points.size(); i++) {
			if (getNormalizedDistanceAtPoint(i) > currentU + 1e-4f) {
				return i;
			}
		}
		return points.size() - 1;
	}

	/** currentUより手前にある直近の制御点indexを返す。無ければ先頭のindex */
	public int findPreviousPointIndex(float currentU) {
		if (points.isEmpty()) {
			throw new IllegalStateException("EndoPath3D has no points");
		}
		for (int i = points.size() - 1; i >= 0; i--) {
			if (getNormalizedDistanceAtPoint(i) < currentU - 1e-4f) {
				return i;
			}
		}
		return 0;
	}

	public Vector3f getPositionAtNormalizedDistance(float u) {
		return sampleAtNormalizedDistance(u).position;
	}

	public Vector3f getTangentAtNormalizedDistance(float u) {
		return sampleAtNormalizedDistance(u).tangent;
	}

	/** uは[0,1]の正規化距離。二分探索1回でposition/tangentの両方を返す */
	public PathSample sampleAtNormalizedDistance(float u) {
		if (points.isEmpty()) {
			throw new IllegalStateException("EndoPath3D has no points");
		}
		ensureFresh();
		if (points.size() == 1) {
			return new PathSample(points.get(0).getPosition(), new Vector3f(0, 0, 1));
		}
		float clampedU = clamp(u, 0f, 1f);
		return sampleAtArcLength(clampedU * totalLength);
	}

	private PathSample sampleAtArcLength(float distance) {
		int n = sampleArcLength.length;
		if (n == 1) {
			return new PathSample(new Vector3f(samplePosition[0]), new Vector3f(sampleTangent[0]));
		}

		float d = clamp(distance, 0f, totalLength);
		int lo = 0;
		int hi = n - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) / 2;
			if (sampleArcLength[mid] <= d) {
				lo = mid;
			} else {
				hi = mid;
			}
		}

		float segLen = sampleArcLength[hi] - sampleArcLength[lo];
		float f = segLen > 1e-9f ? (d - sampleArcLength[lo]) / segLen : 0f;

		Vector3f pos = lerp(samplePosition[lo], samplePosition[hi], f);
		Vector3f tan = lerp(sampleTangent[lo], sampleTangent[hi], f);
		if (tan.lengthSquared() > 1e-12f) {
			tan.normalize();
		} else {
			tan.set(0, 0, 1);
		}
		return new PathSample(pos, tan);
	}

	private void markDirty() {
		dirty = true;
	}

	private void ensureFresh() {
		if (dirty) {
			rebuildArcLengthTable();
			dirty = false;
		}
	}

	private void rebuildArcLengthTable() {
		int n = points.size();
		if (n < 2) {
			sampleArcLength = new float[] { 0f };
			samplePosition = new Vector3f[] { n == 1 ? points.get(0).getPosition() : new Vector3f() };
			sampleTangent = new Vector3f[] { new Vector3f(0, 0, 1) };
			totalLength = 0f;
			return;
		}

		int segmentCount = n - 1;
		int sampleCount = segmentCount * SAMPLES_PER_SEGMENT + 1;
		sampleArcLength = new float[sampleCount];
		samplePosition = new Vector3f[sampleCount];
		sampleTangent = new Vector3f[sampleCount];

		int idx = 0;
		for (int seg = 0; seg < segmentCount; seg++) {
			for (int k = 0; k < SAMPLES_PER_SEGMENT; k++) {
				float t = seg + (float) k / SAMPLES_PER_SEGMENT;
				samplePosition[idx] = evaluatePosition(t);
				idx++;
			}
		}
		samplePosition[idx] = evaluatePosition((float) segmentCount); // 末尾点
		idx++;
		// idx == sampleCount のはず

		sampleArcLength[0] = 0f;
		for (int i = 1; i < sampleCount; i++) {
			float d = samplePosition[i].distance(samplePosition[i - 1]);
			sampleArcLength[i] = sampleArcLength[i - 1] + d;
		}
		totalLength = sampleArcLength[sampleCount - 1];

		for (int i = 0; i < sampleCount; i++) {
			Vector3f prev = samplePosition[Math.max(i - 1, 0)];
			Vector3f next = samplePosition[Math.min(i + 1, sampleCount - 1)];
			Vector3f diff = new Vector3f(next).sub(prev);
			if (diff.lengthSquared() < 1e-12f) {
				sampleTangent[i] = new Vector3f(0, 0, 1);
			} else {
				sampleTangent[i] = diff.normalize();
			}
		}
	}

	public static final class PathSample {
		public final Vector3f position;
		public final Vector3f tangent;

		public PathSample(Vector3f position, Vector3f tangent) {
			this.position = position;
			this.tangent = tangent;
		}
	}
}
