package com.vis.core.view.D3.util;

import com.vis.core.log.Log;
import com.vis.core.view.D3.ui.MeshData;

public class MeshAnalyzer {

    /**
	 * メッシュの体積を計算して返します。 頂点の座標系が mm であれば、戻り値は mm^3 になります。
	 * 
	 * // 1. 実寸サイズ (mm空間) でメッシュを生成 MeshData generatedMesh =
	 * convertRoiGroupToMesh(targetRois);
	 * 
	 * // 2. alignMeshToVolume()で正規化される「前」に実寸の体積 (mm^3) を計算する！ double volumeMm3 =
	 * MeshAnalyzer.calculateVolume(generatedMesh); double volumeCc = volumeMm3 /
	 * 1000.0; // cc (mL) に変換 Log.logger.info("Volume: " + volumeCc + " cc");
	 * 
	 * // 3. 描画用に -0.5〜0.5 空間に座標を縮小・マッピング alignMeshToVolume(generatedMesh,
	 * canvas.getVolumeData());
	 * 
	 */
	public static double calculateVolume(MeshData mesh) {
        if (mesh == null || mesh.vertices == null || mesh.indices == null) {
            return 0.0;
        }

        float[] v = mesh.vertices;
        int[] idx = mesh.indices;
        
        validateMeshScale(v);
        
        // 浮動小数点誤差を防ぐため、合計値の計算には必ず double を使用します
        double totalVolume = 0.0;

        for (int i = 0; i < idx.length; i += 3) {
            // 3つの頂点の配列インデックス（1つの頂点はx,y,zの3要素を持つため3倍する）
            int i1 = idx[i] * 3;
            int i2 = idx[i + 1] * 3;
            int i3 = idx[i + 2] * 3;

            // 頂点1 (v1)
            double v1x = v[i1], v1y = v[i1 + 1], v1z = v[i1 + 2];
            // 頂点2 (v2)
            double v2x = v[i2], v2y = v[i2 + 1], v2z = v[i2 + 2];
            // 頂点3 (v3)
            double v3x = v[i3], v3y = v[i3 + 1], v3z = v[i3 + 2];

            // v2 と v3 の外積 (Cross Product: v2 x v3)
            double cx = (v2y * v3z) - (v2z * v3y);
            double cy = (v2z * v3x) - (v2x * v3z);
            double cz = (v2x * v3y) - (v2y * v3x);

            // v1 と外積結果の内積 (Dot Product: v1 ・ (v2 x v3))
            double dotProduct = (v1x * cx) + (v1y * cy) + (v1z * cz);

            totalVolume += dotProduct;
        }

        // 最後に6で割り、絶対値をとる
        return Math.abs(totalVolume) / 6.0;
    }

	/**
     * メッシュの表面積を計算します。
     * 各三角形の面積（2つの辺の外積の長さの半分）を合計します。
     * 座標系が mm の場合、戻り値は mm^2 になります。
     *
     * @param mesh 計算対象のMeshData
     * @return 表面積
     */
    public static double calculateSurfaceArea(MeshData mesh) {
        if (mesh == null || mesh.vertices == null || mesh.indices == null) {
            return 0.0;
        }

        float[] v = mesh.vertices;
        int[] idx = mesh.indices;
        double totalArea = 0.0;
        
        validateMeshScale(v);

        for (int i = 0; i < idx.length; i += 3) {
            int i1 = idx[i] * 3;
            int i2 = idx[i + 1] * 3;
            int i3 = idx[i + 2] * 3;

            // 2つの辺のベクトルを作成 (v2 - v1) と (v3 - v1)
            double edge1x = v[i2] - v[i1];
            double edge1y = v[i2 + 1] - v[i1 + 1];
            double edge1z = v[i2 + 2] - v[i1 + 2];

            double edge2x = v[i3] - v[i1];
            double edge2y = v[i3 + 1] - v[i1 + 1];
            double edge2z = v[i3 + 2] - v[i1 + 2];

            // 外積 (Cross Product)
            double cx = (edge1y * edge2z) - (edge1z * edge2y);
            double cy = (edge1z * edge2x) - (edge1x * edge2z);
            double cz = (edge1x * edge2y) - (edge1y * edge2x);

            // 外積の長さ（ベクトルのノルム）の半分が三角形の面積
            double area = Math.sqrt((cx * cx) + (cy * cy) + (cz * cz)) / 2.0;
            totalArea += area;
        }

        return totalArea;
    }
    
	// ==========================================================
	// ★ 追加：フェイルセーフのためのスケール検証メソッド
	// ==========================================================
	private static void validateMeshScale(float[] vertices) {
		if (vertices == null || vertices.length == 0)
			return;

		float maxAbs = 0.0f;
		// 頂点座標の最大絶対値をチェック
		for (float v : vertices) {
			float abs = Math.abs(v);
			if (abs > maxAbs) {
				maxAbs = abs;
			}
		}

		// alignMeshToVolume を通過したメッシュは、必ず -1.0 〜 1.0 の空間に収まります。
		// 一方、実寸(mm)の医療画像メッシュであれば、数mm〜数百mmの座標を持つはずです。
		if (maxAbs <= 1.5f) {
			Log.logger.severe("【フェイルセーフ発動】メッシュ座標の最大値が極端に小さいです (Max: " + maxAbs + ")");
			Log.logger.severe("原因: alignMeshToVolume 適用後のメッシュが計算に渡されています。");

			// サイレントバグを防ぐため、明確な例外を投げて処理を停止させる（Fail Fast）
			throw new IllegalStateException("体積・面積計算は、alignMeshToVolume で縮小される【前】のメッシュで行ってください。");
		}
	}
}