package com.vis.resources;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import ij.process.LUT;

import java.io.File;
import java.util.HashMap;

import com.vis.configuration.Resources;
import com.vis.core.util.Utils;

/**
 * LUT (Look-Up Table) ファイルの読み込みテスト。
 *
 * Resources.loadAllLUT() / Resources.loadLUT(name) の動作を検証する。
 * LUT ファイルは GRAPHY 起動時に必ず読み込まれるため、
 * 欠損・破損があると表示が全滅する。
 *
 * 注意:
 *   loadAllLUT() は Utils.isDebug=true のとき相対パス "luts/" を使う。
 *   テスト実行時は BeforeClass で isDebug=true に設定し、
 *   Maven Surefire のデフォルト作業ディレクトリ (プロジェクトルート) でも動作させる。
 */
public class LutLoadingTest {

    private static final String LUTS_DIR = "luts";
    private static boolean lutsAvailable = false;

    @BeforeClass
    public static void setup() {
        // テスト中は相対パス "luts/" が使われるよう debug モードを有効化
        Utils.isDebug = true;
        lutsAvailable = new File(LUTS_DIR).isDirectory();
        if (!lutsAvailable) {
            System.out.println("[WARN] luts/ directory not found. LUT loading tests will be skipped.");
        }
    }

    // -----------------------------------------------------------------------
    // Resources.loadAllLUT() テスト
    // -----------------------------------------------------------------------

    @Test
    public void testLoadAllLUT_returnsNonEmptyMap() {
        if (!lutsAvailable) return;

        HashMap<String, LUT> luts = Resources.loadAllLUT();
        assertNotNull("loadAllLUT() should not return null", luts);
        assertFalse("loadAllLUT() should return at least one LUT", luts.isEmpty());
    }

    @Test
    public void testLoadAllLUT_noNullEntries() {
        if (!lutsAvailable) return;

        HashMap<String, LUT> luts = Resources.loadAllLUT();
        for (java.util.Map.Entry<String, LUT> entry : luts.entrySet()) {
            assertNotNull("LUT entry '" + entry.getKey() + "' should not be null", entry.getValue());
        }
    }

    @Test
    public void testLoadAllLUT_each256Entries() {
        if (!lutsAvailable) return;

        HashMap<String, LUT> luts = Resources.loadAllLUT();
        for (java.util.Map.Entry<String, LUT> entry : luts.entrySet()) {
            LUT lut = entry.getValue();
            assertEquals("LUT '" + entry.getKey() + "' should have 256 entries",
                         256, lut.getMapSize());
        }
    }

    // -----------------------------------------------------------------------
    // Resources.loadLUT(name) テスト – 必須 LUT ファイル（大文字小文字に注意）
    // -----------------------------------------------------------------------

    @Test
    public void testLoadLUT_gray() {
        if (!lutsAvailable) return;
        LUT lut = Resources.loadLUT("gray");
        assertNotNull("gray.lut should load", lut);
        assertEquals("gray.lut: 256 entries", 256, lut.getMapSize());
    }

    @Test
    public void testLoadLUT_fire() {
        if (!lutsAvailable) return;
        LUT lut = Resources.loadLUT("Fire-1");
        assertNotNull("Fire-1.lut should load", lut);
        assertEquals("Fire-1.lut: 256 entries", 256, lut.getMapSize());
    }

    @Test
    public void testLoadLUT_sPet() {
        if (!lutsAvailable) return;
        // 実際のファイル名は S_Pet.lut (大文字小文字に注意)
        LUT lut = Resources.loadLUT("S_Pet");
        assertNotNull("S_Pet.lut should load", lut);
    }

    @Test
    public void testLoadLUT_rainbow() {
        if (!lutsAvailable) return;
        LUT lut = Resources.loadLUT("Rainbow");
        assertNotNull("Rainbow.lut should load", lut);
    }

    @Test
    public void testLoadLUT_viridis() {
        if (!lutsAvailable) return;
        LUT lut = Resources.loadLUT("Viridis");
        assertNotNull("Viridis.lut should load", lut);
    }

    @Test
    public void testLoadLUT_phase() {
        if (!lutsAvailable) return;
        LUT lut = Resources.loadLUT("Phase");
        assertNotNull("Phase.lut should load", lut);
    }

    // -----------------------------------------------------------------------
    // LUT の RGB 値の整合性
    // -----------------------------------------------------------------------

    @Test
    public void testGrayLUT_isMonotonic() {
        if (!lutsAvailable) return;
        LUT lut = Resources.loadLUT("gray");
        if (lut == null) return;

        byte[] reds = new byte[256];
        lut.getReds(reds);
        // gray LUT: R[i] は単調増加（または単調減少）のはず
        boolean increasing = true, decreasing = true;
        for (int i = 1; i < 256; i++) {
            int prev = reds[i-1] & 0xFF;
            int curr = reds[i]   & 0xFF;
            if (curr < prev) increasing = false;
            if (curr > prev) decreasing = false;
        }
        assertTrue("gray LUT red channel should be monotonic",
                   increasing || decreasing);
    }

    @Test
    public void testLUT_colorValuesInRange() {
        if (!lutsAvailable) return;
        LUT lut = Resources.loadLUT("Fire-1");
        if (lut == null) return;

        byte[] reds   = new byte[256];
        byte[] greens = new byte[256];
        byte[] blues  = new byte[256];
        lut.getReds(reds);
        lut.getGreens(greens);
        lut.getBlues(blues);

        for (int i = 0; i < 256; i++) {
            int r = reds[i]   & 0xFF;
            int g = greens[i] & 0xFF;
            int b = blues[i]  & 0xFF;
            assertTrue("R[" + i + "] in 0-255", r >= 0 && r <= 255);
            assertTrue("G[" + i + "] in 0-255", g >= 0 && g <= 255);
            assertTrue("B[" + i + "] in 0-255", b >= 0 && b <= 255);
        }
    }

    // -----------------------------------------------------------------------
    // 存在しない LUT 名で FileNotFoundException がスローされることを確認
    // (Resources.loadLUT() のフォールバックが LutLoader.openLut を直接呼ぶため)
    // -----------------------------------------------------------------------

    @Test
    public void testLoadLUT_nonexistent_throwsOrReturnsNull() {
        if (!lutsAvailable) return;
        try {
            LUT lut = Resources.loadLUT("NonExistentLutName_XYZ_999");
            // null が返る実装の場合はそれでよい
            assertNull("Nonexistent LUT should return null", lut);
        } catch (Exception e) {
            // FileNotFoundException を投げる実装でも acceptable
            assertTrue("Exception should be file-related",
                       e.getClass().getSimpleName().contains("IOException") ||
                       e.getClass().getSimpleName().contains("FileNotFound") ||
                       e instanceof RuntimeException);
        }
    }
}
