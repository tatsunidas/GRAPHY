package com.vis.core.view.D3.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.vis.core.log.Log;

public class MeshRepairer {

    /**
     * 不正な頂点や面を除去し、安全なMeshDataを再構築します。
     */
    public static MeshData repair(MeshData mesh) {
        Log.logger.info("=== Starting Mesh Auto-Repair ===");

        int originalVertexCount = mesh.vertices.length / 3;
        int originalTriangleCount = mesh.indices.length / 3;

        // 1. 各頂点が正常な数値かチェックする
        boolean[] validVertices = new boolean[originalVertexCount];
        for (int i = 0; i < originalVertexCount; i++) {
            float vx = mesh.vertices[i * 3];
            float vy = mesh.vertices[i * 3 + 1];
            float vz = mesh.vertices[i * 3 + 2];

            if (Float.isNaN(vx) || Float.isInfinite(vx) ||
                Float.isNaN(vy) || Float.isInfinite(vy) ||
                Float.isNaN(vz) || Float.isInfinite(vz)) {
                validVertices[i] = false;
            } else {
                validVertices[i] = true;
            }
        }

        // 2. 正常な三角形（面）だけを抽出する
        List<Integer> validTriangles = new ArrayList<>();
        boolean[] vertexUsed = new boolean[originalVertexCount]; // 実際に使われているか追跡

        for (int i = 0; i < originalTriangleCount; i++) {
            int v0 = mesh.indices[i * 3];
            int v1 = mesh.indices[i * 3 + 1];
            int v2 = mesh.indices[i * 3 + 2];

            // 頂点が範囲外、または不正な数値(NaN等)を含んでいる場合は面ごと破棄
            if (v0 < 0 || v0 >= originalVertexCount || !validVertices[v0] ||
                v1 < 0 || v1 >= originalVertexCount || !validVertices[v1] ||
                v2 < 0 || v2 >= originalVertexCount || !validVertices[v2]) {
                continue;
            }

            // 縮退面（2つ以上の頂点が同じID＝線や点になってしまっている面）は破棄
            if (v0 == v1 || v1 == v2 || v2 == v0) {
                continue;
            }

            validTriangles.add(v0);
            validTriangles.add(v1);
            validTriangles.add(v2);

            vertexUsed[v0] = true;
            vertexUsed[v1] = true;
            vertexUsed[v2] = true;
        }

        // 3. 使われている頂点だけを集めて再マッピング（配列の圧縮）
        List<Float> newVertices = new ArrayList<>();
        List<Float> newNormals = new ArrayList<>();
        Map<Integer, Integer> oldToNewIndex = new HashMap<>();
        
        int newIndexCounter = 0;
        for (int i = 0; i < originalVertexCount; i++) {
            if (vertexUsed[i]) {
                oldToNewIndex.put(i, newIndexCounter++);
                
                // 座標をコピー
                newVertices.add(mesh.vertices[i * 3]);
                newVertices.add(mesh.vertices[i * 3 + 1]);
                newVertices.add(mesh.vertices[i * 3 + 2]);
                
                // 法線をコピー
                newNormals.add(mesh.normals[i * 3]);
                newNormals.add(mesh.normals[i * 3 + 1]);
                newNormals.add(mesh.normals[i * 3 + 2]);
            }
        }

        // 4. 新しいインデックス配列の作成
        int[] finalIndices = new int[validTriangles.size()];
        for (int i = 0; i < validTriangles.size(); i++) {
            finalIndices[i] = oldToNewIndex.get(validTriangles.get(i));
        }

        // 配列化
        float[] finalVertices = new float[newVertices.size()];
        for (int i = 0; i < newVertices.size(); i++) finalVertices[i] = newVertices.get(i);
        
        float[] finalNormals = new float[newNormals.size()];
        for (int i = 0; i < newNormals.size(); i++) finalNormals[i] = newNormals.get(i);

        int removedVertices = originalVertexCount - (finalVertices.length / 3);
        int removedTriangles = originalTriangleCount - (finalIndices.length / 3);

        Log.logger.info(String.format("Auto-Repair Result: Removed %d unused/invalid vertices and %d invalid triangles.", removedVertices, removedTriangles));
        
        MeshData repairedMesh = new MeshData(finalVertices, finalNormals, finalIndices);
        repairedMesh.color = mesh.color;
        repairedMesh.visible = mesh.visible;

        return repairedMesh;
    }
}