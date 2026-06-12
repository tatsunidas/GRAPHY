package com.vis.core.view.D3.ui;

import java.awt.Color;

/**
 * 3Dメッシュ（ポリゴン）データを保持するコンテナクラス
 */
public class MeshData {
    public float[] vertices; // 頂点座標 (x, y, zの連続配列)
    public float[] normals;  // 法線ベクトル (nx, ny, nz)
    public int[] indices;    // 描画インデックス (三角形の頂点順序)

    public Color color = new Color(200, 200, 200, 255); // デフォルトの色
    public boolean visible = true;

    public MeshData(float[] vertices, float[] normals, int[] indices) {
        this.vertices = vertices;
        this.normals = normals;
        this.indices = indices;
    }
}