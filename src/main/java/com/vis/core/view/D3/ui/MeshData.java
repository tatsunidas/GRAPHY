package com.vis.core.view.D3.ui;

/**
 * 3Dメッシュ（ポリゴン）データを保持するコンテナクラス
 */
public class MeshData {
    public float[] vertices; // 頂点座標 (x, y, zの連続配列)
    public float[] normals;  // 法線ベクトル (nx, ny, nz)
    public int[] indices;    // 描画インデックス (三角形の頂点順序)
    
    public float[] colors;
    public boolean visible = true;

    public MeshData(float[] vertices, float[] normals, int[] indices) {
        this.vertices = vertices;
        this.normals = normals;
        this.indices = indices;
    }
}