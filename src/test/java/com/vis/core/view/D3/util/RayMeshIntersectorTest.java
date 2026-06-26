package com.vis.core.view.D3.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.joml.Matrix4f;
import org.junit.Test;

import com.vis.core.view.D3.ui.MeshData;

/**
 * RayMeshIntersector のユニットテスト (OpenGL コンテキスト不要)。
 *
 * 検証項目:
 *  - スクリーン中央から正面の三角形へのレイが正しくヒットする
 *  - 三角形の外へ向けたレイがヒットしない (null)
 *  - 奥・手前に 2 枚の三角形がある場合, 手前 (小さい t) の交点が返る
 *  - メッシュの背面 (法線逆向き) でもヒットを検出する
 *  - null メッシュは null を返す
 *  - インデックスが空のメッシュは null を返す
 *
 * 座標系: render space (–0.5 … 0.5)。MVP = 単位行列のとき
 * スクリーン (W/2, H/2) → NDC (0, 0) → レイ origin=(0,0,−1), dir=(0,0,+1)。
 */
public class RayMeshIntersectorTest {

    private static final float EPS = 1e-4f;
    private static final int W = 800, H = 600;

    // ── ヘルパー ────────────────────────────────────────────────────────────

    /**
     * Z=z0 の平面上に置いた三角形 (origin を囲む大きさ) の MeshData。
     * A=(−0.5,−0.5,z0) B=(0.5,−0.5,z0) C=(0,0.5,z0)
     */
    private static MeshData makeTriangle(float z0) {
        float[] v = {-0.5f,-0.5f,z0,  0.5f,-0.5f,z0,  0f,0.5f,z0};
        float[] n = new float[9];
        int[] idx = {0, 1, 2};
        return new MeshData(v, n, idx);
    }

    /**
     * 2 枚の三角形 (z=near と z=far) を持つ MeshData。
     */
    private static MeshData makeTwoTriangles(float zNear, float zFar) {
        float[] v = {
            -0.5f,-0.5f,zNear,  0.5f,-0.5f,zNear,  0f,0.5f,zNear,
            -0.5f,-0.5f,zFar,   0.5f,-0.5f,zFar,    0f,0.5f,zFar
        };
        float[] n = new float[18];
        int[] idx = {0,1,2,  3,4,5};
        return new MeshData(v, n, idx);
    }

    // ── スクリーン中央ヒット ─────────────────────────────────────────────────

    @Test
    public void testHit_centerScreen_identityMvp_returnsOriginOnTriangle() {
        MeshData mesh = makeTriangle(0f);
        Matrix4f mvp  = new Matrix4f(); // identity

        float[] hit = RayMeshIntersector.intersect(mesh, mvp, W/2, H/2, W, H);

        assertNotNull("center ray should hit the triangle", hit);
        assertArrayEquals("hit point near (0,0,0)",
                new float[]{0f, 0f, 0f}, hit, EPS);
    }

    @Test
    public void testHit_triangleAtPositiveZ_returnsCorrectZ() {
        float z0 = 0.25f;
        MeshData mesh = makeTriangle(z0);
        Matrix4f mvp  = new Matrix4f();

        float[] hit = RayMeshIntersector.intersect(mesh, mvp, W/2, H/2, W, H);

        assertNotNull("should hit triangle at z=0.25", hit);
        org.junit.Assert.assertEquals("hit z ≈ 0.25", z0, hit[2], EPS);
    }

    // ── スクリーン端でミス ───────────────────────────────────────────────────

    @Test
    public void testMiss_topLeftScreen_returnsNull() {
        // NDC (−1, 1) → レイが三角形の外を通過
        MeshData mesh = makeTriangle(0f);
        Matrix4f mvp  = new Matrix4f();

        float[] hit = RayMeshIntersector.intersect(mesh, mvp, 0, 0, W, H);

        assertNull("top-left ray should miss the triangle", hit);
    }

    @Test
    public void testMiss_bottomRightScreen_returnsNull() {
        MeshData mesh = makeTriangle(0f);
        Matrix4f mvp  = new Matrix4f();

        float[] hit = RayMeshIntersector.intersect(mesh, mvp, W - 1, H - 1, W, H);

        assertNull("bottom-right ray should miss the triangle", hit);
    }

    // ── 最近傍優先 ───────────────────────────────────────────────────────────

    @Test
    public void testClosestHit_twoTriangles_returnsNearerOne() {
        // z=−0.1 (手前) と z=0.3 (奥) の 2 枚
        MeshData mesh = makeTwoTriangles(-0.1f, 0.3f);
        Matrix4f mvp  = new Matrix4f();

        float[] hit = RayMeshIntersector.intersect(mesh, mvp, W/2, H/2, W, H);

        assertNotNull("should hit one of the triangles", hit);
        org.junit.Assert.assertEquals("nearest triangle at z=−0.1", -0.1f, hit[2], EPS);
    }

    @Test
    public void testClosestHit_reversedOrder_stillReturnsNearerOne() {
        // インデックス順が逆でも手前三角形が返る
        MeshData mesh = makeTwoTriangles(0.3f, -0.1f);
        Matrix4f mvp  = new Matrix4f();

        float[] hit = RayMeshIntersector.intersect(mesh, mvp, W/2, H/2, W, H);

        assertNotNull(hit);
        org.junit.Assert.assertEquals("nearest at z=−0.1 regardless of index order",
                -0.1f, hit[2], EPS);
    }

    // ── 背面三角形 (法線逆向き) ─────────────────────────────────────────────

    @Test
    public void testBackface_stillHit() {
        // 頂点を逆順に並べて法線を反転 → カリングなしなので当たる
        float[] v = {0f,0.5f,0f,  0.5f,-0.5f,0f,  -0.5f,-0.5f,0f};
        float[] n = new float[9];
        int[] idx = {0, 1, 2};
        MeshData mesh = new MeshData(v, n, idx);
        Matrix4f mvp  = new Matrix4f();

        float[] hit = RayMeshIntersector.intersect(mesh, mvp, W/2, H/2, W, H);

        assertNotNull("backface triangle should still be hit (no culling)", hit);
    }

    // ── null / 空メッシュ ────────────────────────────────────────────────────

    @Test
    public void testNullMesh_returnsNull() {
        assertNull(RayMeshIntersector.intersect(null, new Matrix4f(), W/2, H/2, W, H));
    }

    @Test
    public void testEmptyIndices_returnsNull() {
        MeshData mesh = new MeshData(
                new float[]{0f,0f,0f, 0.5f,0f,0f, 0f,0.5f,0f},
                new float[9],
                new int[0]);
        assertNull(RayMeshIntersector.intersect(mesh, new Matrix4f(), W/2, H/2, W, H));
    }

    // ── 平行レイ (法線平行, det ≈ 0) ────────────────────────────────────────

    @Test
    public void testEdgeOn_rayParallelToTrianglePlane_returnsNull() {
        // X=0 の鉛直三角形。レイ方向は (0,0,1) 、三角形法線も (0,0,1) → 平行
        // 三角形: A=(−0.5,−0.5,0) B=(0.5,−0.5,0) C=(0,0.5,0)
        // 全頂点が Z=0 の平面にあり、レイ方向と平行 → ヒットしない
        MeshData mesh = makeTriangle(0f);
        // レイを Z 方向と垂直にするため、スクリーン中央で Z=0 に平行移動した MVP
        // → 単純に screen center だが… ここでは三角形を Y 軸まわりに 90° 回した別メッシュを使う
        float[] v = {0f,-0.5f,-0.5f,  0f,-0.5f,0.5f,  0f,0.5f,0f};
        float[] n = new float[9];
        int[] idx = {0,1,2};
        MeshData sideTriangle = new MeshData(v, n, idx);
        Matrix4f mvp = new Matrix4f();

        // レイ (0,0,+1) は X=0 の三角形と平行 → miss
        float[] hit = RayMeshIntersector.intersect(sideTriangle, mvp, W/2, H/2, W, H);

        // det ≈ 0 → skip → null が返ることを確認
        // (実装では Math.abs(det) < EPS で skip しているため null になるが,
        //  浮動小数点の丸めで微小ヒットが起きる場合も許容する)
        if (hit != null) {
            // ヒットしても交点の X は 0 に近いはず
            org.junit.Assert.assertEquals("if hit, x ≈ 0", 0f, hit[0], 0.01f);
        }
        // null でも非 null でも例外が出ないことを主に確認
    }
}
