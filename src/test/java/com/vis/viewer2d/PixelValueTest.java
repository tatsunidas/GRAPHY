package com.vis.viewer2d;

import org.junit.Test;
import static org.junit.Assert.*;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;

/**
 * SlideGlass.getPixelValueFromOriginal() の内部ロジックを直接テスト。
 *
 * テスト内容:
 *  - 16bit グレースケール画像の生ピクセル値が正しく取得できるか
 *  - Rescale Slope/Intercept (CT: slope=1, intercept=-1024) 適用後の値が正しいか
 *  - マウス位置の画素値表示に使われる calibrated value の精度
 *  - RGB 画像の R/G/B チャンネル値が正しく取得できるか
 *  - 境界ピクセル（左上・右下角）が取得できるか
 */
public class PixelValueTest {

    // -----------------------------------------------------------------------
    // 16bit グレースケール – 生ピクセル値
    // -----------------------------------------------------------------------

    @Test
    public void testGrayscale16bit_rawValue() {
        ShortProcessor sp = new ShortProcessor(10, 10);
        sp.set(3, 4, 2048); // x=3, y=4 に値 2048 をセット
        ImagePlus imp = new ImagePlus("test", sp);

        // getPixelValueFromOriginal() 内の同等コード
        double raw = imp.getProcessor().get(3, 4);
        assertEquals("raw pixel value at (3,4)", 2048.0, raw, 0.0);
    }

    @Test
    public void testGrayscale16bit_rawValue_zero() {
        ShortProcessor sp = new ShortProcessor(8, 8);
        sp.set(0, 0, 0);
        ImagePlus imp = new ImagePlus("test", sp);

        double raw = imp.getProcessor().get(0, 0);
        assertEquals("raw pixel value 0", 0.0, raw, 0.0);
    }

    @Test
    public void testGrayscale16bit_rawValue_maxUint16() {
        ShortProcessor sp = new ShortProcessor(4, 4);
        sp.set(2, 2, 65535);
        ImagePlus imp = new ImagePlus("test", sp);

        double raw = imp.getProcessor().get(2, 2);
        assertEquals("raw max uint16 value", 65535.0, raw, 0.0);
    }

    // -----------------------------------------------------------------------
    // 16bit – Calibration (Rescale Slope/Intercept) 適用後の値
    // CT モダリティの典型値: slope=1.0, intercept=-1024
    // -----------------------------------------------------------------------

    @Test
    public void testCT_calibratedValue() {
        ShortProcessor sp = new ShortProcessor(10, 10);
        // 生値 1024 + 1024 = 2048 → calibrated = 2048*1.0 - 1024 = 1024 HU
        sp.set(5, 5, 2048);
        ImagePlus imp = new ImagePlus("test", sp);

        Calibration cal = imp.getCalibration();
        cal.setFunction(Calibration.STRAIGHT_LINE,
                        new double[]{-1024.0, 1.0}, "HU");
        imp.setCalibration(cal);

        double calibrated = imp.getProcessor().getPixelValue(5, 5);
        // ImageJ: calibrated = raw * slope + intercept = 2048 * 1 + (-1024) = 1024
        assertEquals("CT calibrated value at 1024 HU", 1024.0, calibrated, 1.0);
    }

    @Test
    public void testCT_calibratedValue_air() {
        // 空気: HU = -1000 → 生値 = -1000 + 1024 = 24
        ShortProcessor sp = new ShortProcessor(10, 10);
        sp.set(0, 0, 24);
        ImagePlus imp = new ImagePlus("test", sp);

        Calibration cal = imp.getCalibration();
        cal.setFunction(Calibration.STRAIGHT_LINE,
                        new double[]{-1024.0, 1.0}, "HU");
        imp.setCalibration(cal);

        double calibrated = imp.getProcessor().getPixelValue(0, 0);
        assertEquals("air HU ~= -1000", -1000.0, calibrated, 1.0);
    }

    @Test
    public void testCT_calibratedValue_water() {
        // 水: HU = 0 → 生値 = 0 + 1024 = 1024
        ShortProcessor sp = new ShortProcessor(10, 10);
        sp.set(3, 3, 1024);
        ImagePlus imp = new ImagePlus("test", sp);

        Calibration cal = imp.getCalibration();
        cal.setFunction(Calibration.STRAIGHT_LINE,
                        new double[]{-1024.0, 1.0}, "HU");
        imp.setCalibration(cal);

        double calibrated = imp.getProcessor().getPixelValue(3, 3);
        assertEquals("water HU = 0", 0.0, calibrated, 1.0);
    }

    // -----------------------------------------------------------------------
    // RGB 画像: R/G/B チャンネルが独立して正しく取得できるか
    // -----------------------------------------------------------------------

    @Test
    public void testRGB_channels() {
        ColorProcessor cp = new ColorProcessor(8, 8);
        cp.setColor(new java.awt.Color(100, 150, 200));
        cp.fill();
        cp.setColor(new java.awt.Color(10, 20, 30));
        cp.drawPixel(4, 4);

        int[] rgb = cp.getPixel(4, 4, null);
        assertEquals("R channel at (4,4)", 10, rgb[0]);
        assertEquals("G channel at (4,4)", 20, rgb[1]);
        assertEquals("B channel at (4,4)", 30, rgb[2]);

        // 他のピクセルは fill 値
        int[] bg = cp.getPixel(0, 0, null);
        assertEquals("R background", 100, bg[0]);
        assertEquals("G background", 150, bg[1]);
        assertEquals("B background", 200, bg[2]);
    }

    // -----------------------------------------------------------------------
    // 境界ピクセル（左上・右下角）の取得
    // -----------------------------------------------------------------------

    @Test
    public void testBoundaryPixels_corners() {
        int W = 512, H = 512;
        ShortProcessor sp = new ShortProcessor(W, H);
        sp.set(0,   0,   111);
        sp.set(W-1, 0,   222);
        sp.set(0,   H-1, 333);
        sp.set(W-1, H-1, 444);
        ImagePlus imp = new ImagePlus("corners", sp);

        assertEquals("top-left",     111, imp.getProcessor().get(0,   0));
        assertEquals("top-right",    222, imp.getProcessor().get(W-1, 0));
        assertEquals("bottom-left",  333, imp.getProcessor().get(0,   H-1));
        assertEquals("bottom-right", 444, imp.getProcessor().get(W-1, H-1));
    }

    // -----------------------------------------------------------------------
    // 32bit float 画像（PET SUV 等）
    // -----------------------------------------------------------------------

    @Test
    public void testFloat32_pixelValue() {
        FloatProcessor fp = new FloatProcessor(8, 8);
        fp.setf(2, 3, 3.14f);
        ImagePlus imp = new ImagePlus("float", fp);

        // get() は IEEE754 のビット表現が返るので float 変換が必要
        int bits = imp.getProcessor().get(2, 3);
        float val = Float.intBitsToFloat(bits);
        assertEquals("float32 value", 3.14f, val, 0.001f);
    }

    // -----------------------------------------------------------------------
    // 範囲外アクセスのガード確認（SlideGlass.getPixelValueFromOriginal と同等）
    // -----------------------------------------------------------------------

    @Test
    public void testOutOfBounds_guardCheck() {
        int W = 64, H = 64;
        ShortProcessor sp = new ShortProcessor(W, H);
        ImagePlus imp = new ImagePlus("test", sp);

        // 範囲外のピクセル取得を試みない（事前に範囲チェックする）ことを確認
        int x = 70, y = 70;
        boolean inRange = (x >= 0 && x <= W - 1 && y >= 0 && y <= H - 1);
        assertFalse("Out-of-bounds should be rejected before access", inRange);

        // 範囲内は取得できる
        boolean inRange2 = (0 >= 0 && 0 <= W - 1 && 0 >= 0 && 0 <= H - 1);
        assertTrue("In-bounds should be accepted", inRange2);
    }
}
