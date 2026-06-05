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
import org.joml.Vector4f;

import java.awt.Polygon;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ※ VolumeData のパッケージが異なる場合は、適切に import してください
// import com.vis.d3viewer.VolumeData; 

public class VolumeEditor {

	// カット操作を表すコマンドクラス
	public static class CutCommand implements UndoManager.Command {
		private final VolumeData volumeData;
		private final Map<Integer, Number> backupMap; // 変更前の値 (インデックス -> Numberで汎用化)
		private final Map<Integer, Number> newMap; // 変更後の値
		private final Runnable onUpdate; // テクスチャ更新用コールバック

		public CutCommand(VolumeData vol, Map<Integer, Number> changes, Runnable onUpdate) {
			this.volumeData = vol;
			this.backupMap = new HashMap<>();
			this.newMap = changes;
			this.onUpdate = onUpdate;

			// 変更される場所の「元の値」をデータ型に合わせてバックアップ
			for (Integer index : changes.keySet()) {
				backupMap.put(index, getVoxelValueAsNumber(volumeData, index));
			}
		}

		@Override
		public void execute() {
			applyChanges(newMap);
			onUpdate.run(); // GPUへ転送
		}

		@Override
		public void undo() {
			applyChanges(backupMap);
			onUpdate.run(); // GPUへ転送
		}

		// 型に応じて配列に値を書き込む
		private void applyChanges(Map<Integer, Number> changes) {
			switch (volumeData.dataType) {
			case BYTE:
				byte[] bData = (byte[]) volumeData.data;
				for (Map.Entry<Integer, Number> entry : changes.entrySet())
					bData[entry.getKey()] = entry.getValue().byteValue();
				break;
			case SHORT:
				short[] sData = (short[]) volumeData.data;
				for (Map.Entry<Integer, Number> entry : changes.entrySet())
					sData[entry.getKey()] = entry.getValue().shortValue();
				break;
			case FLOAT:
				float[] fData = (float[]) volumeData.data;
				for (Map.Entry<Integer, Number> entry : changes.entrySet())
					fData[entry.getKey()] = entry.getValue().floatValue();
				break;
			case RGB:
				int[] iData = (int[]) volumeData.data;
				for (Map.Entry<Integer, Number> entry : changes.entrySet())
					iData[entry.getKey()] = entry.getValue().intValue();
				break;
			}
		}

		// 型に応じて配列から値を読み出す
		private Number getVoxelValueAsNumber(VolumeData vol, int index) {
			switch (vol.dataType) {
			case BYTE:
				return ((byte[]) vol.data)[index];
			case SHORT:
				return ((short[]) vol.data)[index];
			case FLOAT:
				return ((float[]) vol.data)[index];
			case RGB:
				return ((int[]) vol.data)[index];
			default:
				return 0;
			}
		}
	}

	/**
	 * 画面上のポリゴン内部にあるボクセルを特定し、削除リストを作成する
	 */
	public static Map<Integer, Number> calculateCut(VolumeData volumeData, List<java.awt.Point> screenPolygon,
			Matrix4f mvpMatrix, int screenWidth, int screenHeight) {

		Map<Integer, Number> changes = new HashMap<>();

		// AWTのPolygonに変換（包含判定用）
		Polygon poly = new Polygon();
		for (java.awt.Point p : screenPolygon) {
			poly.addPoint(p.x, p.y);
		}

		Vector4f pos = new Vector4f();
		int index = 0;

		int width = volumeData.width;
		int height = volumeData.height;
		int depth = volumeData.depth;
		float minVal = volumeData.minVal;
		VolumeData.DataType type = volumeData.dataType;

		// 型による配列のキャスト（ループ外で1回だけ行う）
		byte[] bData = type == VolumeData.DataType.BYTE ? (byte[]) volumeData.data : null;
		short[] sData = type == VolumeData.DataType.SHORT ? (short[]) volumeData.data : null;
		float[] fData = type == VolumeData.DataType.FLOAT ? (float[]) volumeData.data : null;
		int[] iData = type == VolumeData.DataType.RGB ? (int[]) volumeData.data : null;

		// 空気判定の閾値（floatの場合は10では大きすぎるので適宜調整）
		float threshold = (type == VolumeData.DataType.FLOAT) ? minVal + (volumeData.maxVal - minVal) * 0.01f
				: minVal + 10;

		// 全ボクセルを走査
		for (int z = 0; z < depth; z++) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {

					// 1. 現在の値を取得し、すでに「空気」ならスキップ (高速化)
					boolean isAir = false;
					switch (type) {
					case BYTE:
						isAir = (bData[index] & 0xFF) <= threshold;
						break;
					case SHORT:
						isAir = (sData[index] & 0xFFFF) <= threshold;
						break;
					case FLOAT:
						isAir = fData[index] <= threshold;
						break;
					case RGB:
						isAir = iData[index] == 0; // RGBは黒(0)ならスキップ
						break;
					}

					if (isAir) {
						index++;
						continue;
					}

					// 2. ボクセルの3D座標を正規化座標 (-0.5 ~ 0.5) に変換
					float lx = (float) x / width - 0.5f;
					float ly = 0.5f - (float) y / height;
					float lz = 0.5f - (float) z / depth;

					// 3. MVP行列でスクリーン座標へ投影
					pos.set(lx, ly, lz, 1.0f);
					pos.mul(mvpMatrix); // クリップ座標系へ

					if (pos.w > 0) {
						float ndcX = pos.x / pos.w;
						float ndcY = pos.y / pos.w;

						int screenX = (int) ((ndcX + 1.0f) * 0.5f * screenWidth);
						int screenY = (int) ((1.0f - ndcY) * 0.5f * screenHeight);

						// 4. ポリゴンの中に入っているか？
						if (poly.contains(screenX, screenY)) {
							// 型に合わせて「消去用の値（空気）」を作成
							Number cutVal = 0;
							switch (type) {
							case BYTE:
								cutVal = (byte) minVal;
								break;
							case SHORT:
								cutVal = (short) minVal;
								break;
							case FLOAT:
								cutVal = minVal;
								break;
							case RGB:
								cutVal = 0;
								break;
							}
							changes.put(index, cutVal);
						}
					}
					index++;
				}
			}
		}
		return changes;
	}
}