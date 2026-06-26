package com.vis.core.view.D3.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.vis.core.view.D3.ui.MeshData;
import com.vis.core.view.D3.ui.MeshMeasureResult;

/**
 * MeshAnalyzer のユニットテスト。
 *
 * 検証項目:
 *  - 立方体・直方体の表面積が解析的な値と一致する
 *  - 立方体・直方体の体積が解析的な値と一致する
 *  - PCA 主軸径が長径 ≥ 中径 ≥ 短径の順に並ぶ
 *  - 直方体の各主軸径が辺長と一致する
 *  - null・空メッシュで例外が出ず、ゼロが返る
 */
public class MeshAnalyzerTest {

    // -----------------------------------------------------------------------
    // ヘルパー: 閉じた直方体メッシュを生成する（12 三角形, 8 頂点）
    // 頂点は (0,0,0)-(ax,ay,az) の範囲。法線は計算不要（ゼロ配列）。
    // -----------------------------------------------------------------------
    static MeshData makeCuboid(float ax, float ay, float az) {
        float[] v = {
            0,   0,  0,   ax,  0,  0,   ax, ay,  0,   0, ay,  0,
            0,   0, az,   ax,  0, az,   ax, ay, az,   0, ay, az
        };
        float[] n = new float[v.length];
        int[] idx = {
            // Bottom (z=0, normal -Z)
            0, 2, 1,   0, 3, 2,
            // Top    (z=az, normal +Z)
            4, 5, 6,   4, 6, 7,
            // Front  (y=0, normal -Y)
            0, 1, 5,   0, 5, 4,
            // Back   (y=ay, normal +Y)
            3, 6, 2,   3, 7, 6,
            // Left   (x=0, normal -X)
            0, 4, 7,   0, 7, 3,
            // Right  (x=ax, normal +X)
            1, 2, 6,   1, 6, 5
        };
        return new MeshData(v, n, idx);
    }

    // -----------------------------------------------------------------------
    // 表面積: 立方体 10×10×10 = 600 mm²
    // -----------------------------------------------------------------------
    @Test
    public void testCube10_surfaceArea() {
        MeshData mesh = makeCuboid(10f, 10f, 10f);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertEquals("Cube 10 surface area", 600.0, r.surfaceAreaMm2, 0.01);
    }

    // -----------------------------------------------------------------------
    // 体積: 立方体 10×10×10 = 1000 mm³
    // -----------------------------------------------------------------------
    @Test
    public void testCube10_volume() {
        MeshData mesh = makeCuboid(10f, 10f, 10f);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertEquals("Cube 10 volume", 1000.0, r.volumeMm3, 0.5);
    }

    // -----------------------------------------------------------------------
    // PCA 主軸径: 立方体は等方的なので全径が等しい
    // -----------------------------------------------------------------------
    @Test
    public void testCube10_diameters_allEqual() {
        MeshData mesh = makeCuboid(10f, 10f, 10f);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertEquals("Cube 10 long diameter",  10.0, r.longDiameterMm,  0.5);
        assertEquals("Cube 10 mid diameter",   10.0, r.midDiameterMm,   0.5);
        assertEquals("Cube 10 short diameter", 10.0, r.shortDiameterMm, 0.5);
    }

    // -----------------------------------------------------------------------
    // 表面積: 直方体 20×10×5 = 2*(200+100+50) = 700 mm²
    // -----------------------------------------------------------------------
    @Test
    public void testCuboid_surfaceArea() {
        MeshData mesh = makeCuboid(20f, 10f, 5f);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertEquals("Cuboid 20x10x5 surface area", 700.0, r.surfaceAreaMm2, 0.01);
    }

    // -----------------------------------------------------------------------
    // 体積: 直方体 20×10×5 = 1000 mm³
    // -----------------------------------------------------------------------
    @Test
    public void testCuboid_volume() {
        MeshData mesh = makeCuboid(20f, 10f, 5f);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertEquals("Cuboid 20x10x5 volume", 1000.0, r.volumeMm3, 0.5);
    }

    // -----------------------------------------------------------------------
    // PCA 主軸径の順序: long ≥ mid ≥ short
    // -----------------------------------------------------------------------
    @Test
    public void testCuboid_diameters_descendingOrder() {
        MeshData mesh = makeCuboid(20f, 10f, 5f);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertTrue("long >= mid",   r.longDiameterMm  >= r.midDiameterMm  - 0.01);
        assertTrue("mid >= short",  r.midDiameterMm   >= r.shortDiameterMm - 0.01);
        assertTrue("long > short",  r.longDiameterMm  >  r.shortDiameterMm);
    }

    // -----------------------------------------------------------------------
    // PCA 主軸径の値: 直方体は主軸が座標軸に一致するため辺長と等しい
    // -----------------------------------------------------------------------
    @Test
    public void testCuboid_diameters_matchEdgeLengths() {
        MeshData mesh = makeCuboid(20f, 10f, 5f);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertEquals("Cuboid long diameter  = 20", 20.0, r.longDiameterMm,  0.5);
        assertEquals("Cuboid mid diameter   = 10", 10.0, r.midDiameterMm,   0.5);
        assertEquals("Cuboid short diameter =  5",  5.0, r.shortDiameterMm, 0.5);
    }

    // -----------------------------------------------------------------------
    // 非対称直方体のスケール比が保持されるか（倍スケール検証）
    // -----------------------------------------------------------------------
    @Test
    public void testScaleInvariant_doubledSize() {
        MeshData mesh1 = makeCuboid(10f, 6f, 4f);
        MeshData mesh2 = makeCuboid(20f, 12f, 8f);
        MeshMeasureResult r1 = MeshAnalyzer.analyze(mesh1);
        MeshMeasureResult r2 = MeshAnalyzer.analyze(mesh2);

        // 辺が2倍 → 面積は4倍, 体積は8倍, 径は2倍
        assertEquals("Doubled size: surface area x4",
                r1.surfaceAreaMm2 * 4.0, r2.surfaceAreaMm2, 0.1);
        assertEquals("Doubled size: volume x8",
                r1.volumeMm3 * 8.0, r2.volumeMm3, 0.5);
        assertEquals("Doubled size: long diameter x2",
                r1.longDiameterMm * 2.0, r2.longDiameterMm, 0.5);
    }

    // -----------------------------------------------------------------------
    // null メッシュ: 例外なくゼロ結果を返す
    // -----------------------------------------------------------------------
    @Test
    public void testNullMesh_returnsZeroResult() {
        MeshMeasureResult r = MeshAnalyzer.analyze(null);
        assertNotNull(r);
        assertEquals("null mesh surface area", 0.0, r.surfaceAreaMm2, 0.0);
        assertEquals("null mesh volume",       0.0, r.volumeMm3,      0.0);
        assertEquals("null mesh long diam",    0.0, r.longDiameterMm, 0.0);
    }

    // -----------------------------------------------------------------------
    // 空インデックスのメッシュ: 例外なくゼロ結果を返す
    // -----------------------------------------------------------------------
    @Test
    public void testEmptyIndices_returnsZeroResult() {
        MeshData mesh = new MeshData(new float[]{0f, 0f, 0f}, new float[3], new int[0]);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertNotNull(r);
        assertEquals("empty indices surface area", 0.0, r.surfaceAreaMm2, 0.0);
        assertEquals("empty indices volume",       0.0, r.volumeMm3,      0.0);
    }

    // -----------------------------------------------------------------------
    // 1頂点メッシュ: 面積・体積ゼロ, 径ゼロ
    // -----------------------------------------------------------------------
    @Test
    public void testSingleVertex_returnsZeroResult() {
        MeshData mesh = new MeshData(new float[]{5f, 5f, 5f}, new float[3], new int[0]);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertEquals("single vertex long diameter", 0.0, r.longDiameterMm, 0.0);
    }

    // -----------------------------------------------------------------------
    // 正確な単面積: 1辺 1mm の正方形 (2三角形) の面積 = 1 mm²
    // -----------------------------------------------------------------------
    @Test
    public void testUnitSquare_surfaceArea() {
        // Z=0 平面の正方形: (0,0)-(1,1) を 2 三角形で表現
        float[] v = {0f, 0f, 0f,  1f, 0f, 0f,  1f, 1f, 0f,  0f, 1f, 0f};
        float[] n = new float[12];
        int[] idx = {0, 1, 2,   0, 2, 3};
        MeshData mesh = new MeshData(v, n, idx);
        MeshMeasureResult r = MeshAnalyzer.analyze(mesh);
        assertEquals("Unit square surface area = 1", 1.0, r.surfaceAreaMm2, 1e-9);
    }
}
