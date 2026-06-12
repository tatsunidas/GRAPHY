package com.vis.core.view.D3.ui;

import java.util.HashMap;
import java.util.Map;
import com.vis.core.log.Log;

/**
 * 3Dメッシュデータの健全性を検証するバリデーションクラス
 */
public class MeshValidator {

    public static boolean validate(MeshData mesh) {
        Log.logger.info("=== Starting Mesh Validation ===");

        if (mesh == null) {
            Log.logger.severe("Validation Failed: MeshData is null.");
            return false;
        }

        boolean isValid = true;

        // 1. 配列サイズの基本的な整合性チェック
        if (mesh.vertices == null || mesh.vertices.length == 0) {
            Log.logger.severe("Validation Failed: Vertices array is empty or null.");
            return false;
        }
        if (mesh.indices == null || mesh.indices.length == 0) {
            Log.logger.severe("Validation Failed: Indices array is empty or null.");
            return false;
        }
        if (mesh.vertices.length % 3 != 0) {
            Log.logger.severe("Validation Error: Vertices length is not a multiple of 3.");
            isValid = false;
        }
        if (mesh.indices.length % 3 != 0) {
            Log.logger.severe("Validation Error: Indices length is not a multiple of 3 (Not perfectly triangulated).");
            isValid = false;
        }

        // 2. 不正な数値（NaNやInfinity）のチェック
        int invalidVertexCount = 0;
        for (int i = 0; i < mesh.vertices.length; i++) {
            float v = mesh.vertices[i];
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                invalidVertexCount++;
            }
        }
        if (invalidVertexCount > 0) {
            Log.logger.warning("Validation Warning: Found " + (invalidVertexCount / 3) + " vertices with NaN or Infinity values.");
            isValid = false;
        }

        // 3. トポロジー（穴や不正な接続）のチェック
        // エッジの出現回数をカウントして判定します
        Map<Long, Integer> edgeCounts = new HashMap<>();
        int triangleCount = mesh.indices.length / 3;

        for (int i = 0; i < triangleCount; i++) {
            int v0 = mesh.indices[i * 3];
            int v1 = mesh.indices[i * 3 + 1];
            int v2 = mesh.indices[i * 3 + 2];

            // 3つのエッジを登録
            addEdge(edgeCounts, v0, v1);
            addEdge(edgeCounts, v1, v2);
            addEdge(edgeCounts, v2, v0);
        }

        int boundaryEdges = 0;     // 穴の縁（1回しか使われないエッジ）
        int nonManifoldEdges = 0;  // 不正な接続（3回以上使われるエッジ）

        for (int count : edgeCounts.values()) {
            if (count == 1) {
                boundaryEdges++;
            } else if (count > 2) {
                nonManifoldEdges++;
            }
        }

        // 結果のレポーティング
        if (boundaryEdges > 0) {
            Log.logger.warning("Topology Warning: Mesh has holes. Found " + boundaryEdges + " boundary edges.");
            // 穴が開いているからといって描画できないわけではないので、isValid は false にしないケースが多いですが、
            // 厳密なソリッドモデルを求める場合はここで false にします。
        } else {
            Log.logger.fine("Topology Info: Mesh is watertight (no holes).");
        }

        if (nonManifoldEdges > 0) {
            Log.logger.warning("Topology Warning: Found " + nonManifoldEdges + " non-manifold edges (faces intersecting incorrectly).");
            isValid = false;
        }

        if (isValid) {
            Log.logger.info("=== Mesh Validation Passed ===");
        } else {
            Log.logger.warning("=== Mesh Validation Completed with Errors/Warnings ===");
        }

        return isValid;
    }

    /**
     * 2つの頂点インデックスから一意なエッジキーを作成し、カウントを増やす
     * (順序に依存しないように、小さいインデックスを上位ビットにする)
     */
    private static void addEdge(Map<Long, Integer> edgeCounts, int v1, int v2) {
        long min = Math.min(v1, v2);
        long max = Math.max(v1, v2);
        
        // 2つの32bit intを結合して1つの64bit longキーを作成 (高速化のため)
        long edgeKey = (min << 32) | (max & 0xFFFFFFFFL);
        
        edgeCounts.put(edgeKey, edgeCounts.getOrDefault(edgeKey, 0) + 1);
    }
}