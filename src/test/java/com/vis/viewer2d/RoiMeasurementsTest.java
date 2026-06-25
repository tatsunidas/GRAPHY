package com.vis.viewer2d;

import org.junit.Test;
import static org.junit.Assert.*;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ShortProcessor;

import java.util.HashMap;
import java.util.List;

import com.vis.core.view.D2.roi.Measurements;
import com.vis.core.view.D2.roi.RoiAnalyzer;
import com.vis.core.view.D2.roi.RoiObj;

/**
 * RoiAnalyzer のユニットテスト。
 *
 * ROI の面積・平均値・最小・最大が正しく計算されるかを検証する。
 * SlideGlass 不要な RoiAnalyzer(RoiObj, ImagePlus) コンストラクタを使用。
 * RoiObj は null SlideGlass で生成（コンストラクタが null-safe）。
 *
 * 検証項目:
 *  - 均一矩形 ROI の面積 = width * height
 *  - 均一矩形 ROI の平均値 = 画素値
 *  - 均一矩形 ROI の min = max = 画素値
 *  - 非均一矩形 ROI の min/max/mean が正確か
 *  - Calibration 適用後の値（HU単位）
 */
public class RoiMeasurementsTest {

    // -----------------------------------------------------------------------
    // ユーティリティ
    // -----------------------------------------------------------------------

    /** 均一値の ShortProcessor を作成 */
    static ImagePlus makeUniform(int w, int h, int value) {
        ShortProcessor sp = new ShortProcessor(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                sp.set(x, y, value);
        return new ImagePlus("test", sp);
    }

    /** SlideGlass なしの矩形 RoiObj を作成 */
    static RoiObj makeRect(int x, int y, int w, int h) {
        return new RoiObj(x, y, w, h, (com.vis.core.view.D2.ui.glasses.SlideGlass) null);
    }

    // -----------------------------------------------------------------------
    // テスト: 均一画像の矩形 ROI
    // -----------------------------------------------------------------------

    @Test
    public void testUniformRect_area() {
        ImagePlus imp = makeUniform(64, 64, 1000);
        RoiObj roi = makeRect(10, 10, 20, 15); // 幅20, 高さ15

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, imp);
        List<HashMap<Measurements, Double>> results = analyzer.measure();

        assertFalse("Results should not be empty", results.isEmpty());
        double area = results.get(0).get(Measurements.AREA);
        assertEquals("Rectangle area = w * h", 20.0 * 15.0, area, 1.0);
    }

    @Test
    public void testUniformRect_mean() {
        int VALUE = 2048;
        ImagePlus imp = makeUniform(64, 64, VALUE);
        RoiObj roi = makeRect(5, 5, 30, 30);

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, imp);
        List<HashMap<Measurements, Double>> results = analyzer.measure();

        double mean = results.get(0).get(Measurements.MEAN);
        assertEquals("Uniform region mean = pixel value", VALUE, mean, 1.0);
    }

    @Test
    public void testUniformRect_minEqualsMax() {
        int VALUE = 3000;
        ImagePlus imp = makeUniform(64, 64, VALUE);
        RoiObj roi = makeRect(0, 0, 64, 64);

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, imp);
        List<HashMap<Measurements, Double>> results = analyzer.measure();

        double min = results.get(0).get(Measurements.MIN);
        double max = results.get(0).get(Measurements.MAX);
        assertEquals("Uniform region: min = value", VALUE, min, 1.0);
        assertEquals("Uniform region: max = value", VALUE, max, 1.0);
        assertEquals("Uniform region: min = max",   min, max, 0.0);
    }

    @Test
    public void testUniformRect_stdDevIsZero() {
        ImagePlus imp = makeUniform(32, 32, 500);
        RoiObj roi = makeRect(5, 5, 20, 20);

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, imp);
        List<HashMap<Measurements, Double>> results = analyzer.measure();

        double std = results.get(0).get(Measurements.STD_DEV);
        assertEquals("Uniform region: stdDev = 0", 0.0, std, 0.5);
    }

    // -----------------------------------------------------------------------
    // テスト: 非均一画像の ROI
    // -----------------------------------------------------------------------

    @Test
    public void testNonUniform_minAndMax() {
        ShortProcessor sp = new ShortProcessor(32, 32);
        // ROI 範囲 (5,5)-(14,14) 内に既知の min/max を配置
        for (int y = 5; y < 15; y++)
            for (int x = 5; x < 15; x++)
                sp.set(x, y, 1000);
        sp.set(7, 7, 500);  // min
        sp.set(9, 9, 2000); // max
        ImagePlus imp = new ImagePlus("nonuniform", sp);
        RoiObj roi = makeRect(5, 5, 10, 10);

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, imp);
        List<HashMap<Measurements, Double>> results = analyzer.measure();

        double min = results.get(0).get(Measurements.MIN);
        double max = results.get(0).get(Measurements.MAX);
        assertEquals("Known min in ROI", 500.0,  min, 1.0);
        assertEquals("Known max in ROI", 2000.0, max, 1.0);
    }

    @Test
    public void testNonUniform_mean_twoValues() {
        ShortProcessor sp = new ShortProcessor(10, 4);
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 10; x++)
                sp.set(x, y, 1000);   // 全体 1000
        sp.set(0, 0, 2000);           // 1ピクセルだけ 2000
        // ROI (0,0)-(9,3) は 40 ピクセル: 39×1000 + 1×2000 = 41000 → mean = 1025
        ImagePlus imp = new ImagePlus("twoval", sp);
        RoiObj roi = makeRect(0, 0, 10, 4);

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, imp);
        List<HashMap<Measurements, Double>> results = analyzer.measure();

        double mean = results.get(0).get(Measurements.MEAN);
        assertEquals("Mean with one outlier", 1025.0, mean, 2.0);
    }

    // -----------------------------------------------------------------------
    // テスト: Calibration（Rescale）適用後の計測値
    // -----------------------------------------------------------------------

    @Test
    public void testCalibrated_meanInHU() {
        // CT: 生値 2048 → HU = 2048*1.0 - 1024 = 1024
        ShortProcessor sp = new ShortProcessor(20, 20);
        for (int y = 0; y < 20; y++)
            for (int x = 0; x < 20; x++)
                sp.set(x, y, 2048);
        ImagePlus imp = new ImagePlus("ct", sp);
        Calibration cal = imp.getCalibration();
        cal.setFunction(Calibration.STRAIGHT_LINE, new double[]{-1024.0, 1.0}, "HU");
        imp.setCalibration(cal);

        RoiObj roi = makeRect(0, 0, 20, 20);
        RoiAnalyzer analyzer = new RoiAnalyzer(roi, imp);
        List<HashMap<Measurements, Double>> results = analyzer.measure();

        double mean = results.get(0).get(Measurements.MEAN);
        assertEquals("CT calibrated mean = 1024 HU", 1024.0, mean, 2.0);
    }

    // -----------------------------------------------------------------------
    // テスト: ROI が画像全体を覆う場合
    // -----------------------------------------------------------------------

    @Test
    public void testFullImageRoi_area() {
        int W = 128, H = 64;
        ImagePlus imp = makeUniform(W, H, 100);
        RoiObj roi = makeRect(0, 0, W, H);

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, imp);
        List<HashMap<Measurements, Double>> results = analyzer.measure();

        double area = results.get(0).get(Measurements.AREA);
        assertEquals("Full image ROI area", W * H, area, 1.0);
    }

    // -----------------------------------------------------------------------
    // テスト: 1×1 ピクセル ROI
    // -----------------------------------------------------------------------

    @Test
    public void testSinglePixelRoi_area() {
        ImagePlus imp = makeUniform(32, 32, 999);
        imp.getProcessor().set(10, 10, 999);
        RoiObj roi = makeRect(10, 10, 1, 1);

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, imp);
        List<HashMap<Measurements, Double>> results = analyzer.measure();

        double area = results.get(0).get(Measurements.AREA);
        assertEquals("1x1 pixel ROI area = 1", 1.0, area, 0.5);

        double mean = results.get(0).get(Measurements.MEAN);
        assertEquals("1x1 pixel ROI mean = pixel value", 999.0, mean, 1.0);
    }
}
