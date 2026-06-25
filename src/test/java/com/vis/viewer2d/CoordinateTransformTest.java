package com.vis.viewer2d;

import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;

/**
 * SlideGlass の座標変換ロジック（calculateCurrentAffineTransform / offScreenCoordinate）を
 * Swing なしで検証するテスト。
 *
 * 対象バグ: Borderの影響でスクリーン⇔画像座標が1ピクセルずれる問題。
 *
 * SlideGlass の実装をそのままミラーした buildTransform() を使って
 * 同じ AffineTransform を構築し、逆変換の往復精度を確かめる。
 */
public class CoordinateTransformTest {

    // -----------------------------------------------------------------------
    // Helper: SlideGlass.calculateCurrentAffineTransform() と同等の変換を構築
    // -----------------------------------------------------------------------

    /**
     * @param imgW      元画像の幅 (orgCols)
     * @param imgH      元画像の高さ (orgRows)
     * @param compW     SlideGlass コンポーネントの幅
     * @param compH     SlideGlass コンポーネントの高さ
     * @param insets    SlideGlass の Border Insets
     * @param zoom      拡大率 (magnification) – 1.0 が等倍
     * @param rotateDeg 回転角度 (degree)
     * @param flipH     水平反転フラグ
     * @param flipV     垂直反転フラグ
     */
    static AffineTransform buildTransform(int imgW, int imgH,
                                          int compW, int compH,
                                          Insets insets,
                                          double zoom, double rotateDeg,
                                          boolean flipH, boolean flipV) {

        // calcImageSize2FitComponent() と同等の計算
        int drawableW = compW - insets.left - insets.right;
        int drawableH = compH - insets.top  - insets.bottom;

        // アスペクト比維持でフィット
        int angle = (int) rotateDeg;
        int srcW = (angle % 180 == 0) ? imgW : imgH;
        int srcH = (angle % 180 == 0) ? imgH : imgW;
        int fitW = drawableW;
        int fitH = (fitW * srcH) / srcW;
        if (fitH > drawableH) {
            fitH = drawableH;
            fitW = (fitH * srcW) / srcH;
        }

        // calcImageOriginPoint() と同等
        int marginX = (drawableW - fitW) / 2;
        int marginY = (drawableH - fitH) / 2;
        int originX = insets.left + marginX;
        int originY = insets.top  + marginY;

        double scaleToFit = (double) fitW / srcW;
        double s  = scaleToFit * zoom;
        double sx = flipH ? -s : s;
        double sy = flipV ? -s : s;
        double visualCenterX = originX + fitW / 2.0;
        double visualCenterY = originY + fitH / 2.0;

        AffineTransform at = new AffineTransform();
        at.translate(visualCenterX, visualCenterY);
        at.rotate(Math.toRadians(rotateDeg));
        at.scale(zoom, zoom);
        at.scale(sx / s, sy / s);
        at.scale(scaleToFit, scaleToFit);
        at.translate(-imgW / 2.0, -imgH / 2.0);
        return at;
    }

    /**
     * AffineTransform の逆変換で Screen → Image 座標を得る。
     * SlideGlass.offScreenCoordinate() と同等。
     */
    static Point offScreen(AffineTransform at, double screenX, double screenY)
            throws NoninvertibleTransformException {
        AffineTransform inv = at.createInverse();
        Point2D.Double src = new Point2D.Double(screenX, screenY);
        Point2D.Double dst = new Point2D.Double();
        inv.transform(src, dst);
        return new Point((int) Math.round(dst.getX()), (int) Math.round(dst.getY()));
    }

    // -----------------------------------------------------------------------
    // テスト: ボーダーなし・等倍・回転なし
    // -----------------------------------------------------------------------

    @Test
    public void testImageCorner_noBorder_noZoom() throws Exception {
        // Image 256x256, component 512x512, no border, scale=2.0 (exact integer)
        Insets none = new Insets(0, 0, 0, 0);
        AffineTransform at = buildTransform(256, 256, 512, 512, none, 1.0, 0, false, false);

        // 画像(0,0) → スクリーン(0,0) の期待
        Point2D.Double out = new Point2D.Double();
        at.transform(new Point2D.Double(0, 0), out);
        assertEquals("left-top X should be 0 with no border", 0.0, out.getX(), 0.5);
        assertEquals("left-top Y should be 0 with no border", 0.0, out.getY(), 0.5);

        // 往復精度: スクリーン(0,0) → 画像(0,0)
        Point img = offScreen(at, 0, 0);
        assertEquals(0, img.x);
        assertEquals(0, img.y);
    }

    // -----------------------------------------------------------------------
    // テスト: ボーダーあり → 画像原点がボーダー幅だけオフセットされる
    // -----------------------------------------------------------------------

    @Test
    public void testImageCorner_withBorder_offsetByInset() throws Exception {
        int BORDER = 5;
        Insets insets = new Insets(BORDER, BORDER, BORDER, BORDER);
        // 512+10=522 のコンポーネント、ドロアブル 512x512 → scale=2.0
        AffineTransform at = buildTransform(256, 256, 522, 522, insets, 1.0, 0, false, false);

        // 画像(0,0) はスクリーン上でボーダー幅(5,5) の位置になるはず
        Point2D.Double out = new Point2D.Double();
        at.transform(new Point2D.Double(0, 0), out);
        assertEquals("Border left offset", BORDER, (int) Math.round(out.getX()));
        assertEquals("Border top offset",  BORDER, (int) Math.round(out.getY()));
    }

    // -----------------------------------------------------------------------
    // テスト: ボーダーありの逆変換精度（1ピクセルもずれない）
    // -----------------------------------------------------------------------

    @Test
    public void testRoundTrip_withBorder() throws Exception {
        Insets insets = new Insets(5, 5, 5, 5);
        AffineTransform at = buildTransform(256, 256, 522, 522, insets, 1.0, 0, false, false);

        // 複数の画像ピクセルで往復テスト
        int[][] testPixels = { {0,0}, {10,20}, {100,150}, {255,255}, {128,128} };
        for (int[] px : testPixels) {
            Point2D.Double screen = new Point2D.Double();
            at.transform(new Point2D.Double(px[0], px[1]), screen);
            Point back = offScreen(at, screen.getX(), screen.getY());
            assertEquals("round-trip X for pixel (" + px[0] + "," + px[1] + ")", px[0], back.x);
            assertEquals("round-trip Y for pixel (" + px[0] + "," + px[1] + ")", px[1], back.y);
        }
    }

    // -----------------------------------------------------------------------
    // テスト: 画像中央がディスプレイ中央にマッピングされる
    // -----------------------------------------------------------------------

    @Test
    public void testImageCenter_mapsToVisualCenter() throws Exception {
        Insets insets = new Insets(5, 5, 5, 5);
        int compW = 522, compH = 522;
        AffineTransform at = buildTransform(256, 256, compW, compH, insets, 1.0, 0, false, false);

        // 画像中心 (128,128) → スクリーン中心 = inset + drawable/2
        int drawable = compW - 10;
        int expectedCenterX = 5 + drawable / 2;  // = 261
        int expectedCenterY = 5 + drawable / 2;

        Point2D.Double out = new Point2D.Double();
        at.transform(new Point2D.Double(128, 128), out);
        assertEquals("center X", expectedCenterX, (int) Math.round(out.getX()));
        assertEquals("center Y", expectedCenterY, (int) Math.round(out.getY()));
    }

    // -----------------------------------------------------------------------
    // テスト: ズーム2倍の往復精度
    // -----------------------------------------------------------------------

    @Test
    public void testRoundTrip_zoom2x() throws Exception {
        Insets insets = new Insets(0, 0, 0, 0);
        AffineTransform at = buildTransform(256, 256, 512, 512, insets, 2.0, 0, false, false);

        int[][] pixels = { {0,0}, {64,64}, {128,128} };
        for (int[] px : pixels) {
            Point2D.Double screen = new Point2D.Double();
            at.transform(new Point2D.Double(px[0], px[1]), screen);
            Point back = offScreen(at, screen.getX(), screen.getY());
            assertEquals("zoom2x round-trip X", px[0], back.x);
            assertEquals("zoom2x round-trip Y", px[1], back.y);
        }
    }

    // -----------------------------------------------------------------------
    // テスト: 水平反転の往復精度
    // -----------------------------------------------------------------------

    @Test
    public void testRoundTrip_flipHorizontal() throws Exception {
        Insets insets = new Insets(0, 0, 0, 0);
        AffineTransform at = buildTransform(256, 256, 512, 512, insets, 1.0, 0, true, false);

        // 反転後は X が鏡像: 画像(0,y) → スクリーン右端あたり
        Point2D.Double out0 = new Point2D.Double();
        Point2D.Double out255 = new Point2D.Double();
        at.transform(new Point2D.Double(0, 0), out0);
        at.transform(new Point2D.Double(255, 0), out255);
        // 反転なので X が逆
        assertTrue("flipH: pixel x=0 should be right of x=255", out0.getX() > out255.getX());

        // 往復は正確
        for (int[] px : new int[][]{{0,0},{50,100},{200,200}}) {
            Point2D.Double sc = new Point2D.Double();
            at.transform(new Point2D.Double(px[0], px[1]), sc);
            Point back = offScreen(at, sc.getX(), sc.getY());
            assertEquals("flipH round-trip X", px[0], back.x);
            assertEquals("flipH round-trip Y", px[1], back.y);
        }
    }

    // -----------------------------------------------------------------------
    // テスト: 異なるボーダー幅でもボーダー差分が正確に反映される
    // -----------------------------------------------------------------------

    @Test
    public void testBorderDiffIsExact() throws Exception {
        int[] borders = { 0, 2, 5, 10, 20 };
        for (int b : borders) {
            Insets ins = new Insets(b, b, b, b);
            int comp = 256 + b * 2; // drawable = 256×256, scale=1.0
            AffineTransform at = buildTransform(256, 256, comp, comp, ins, 1.0, 0, false, false);

            Point2D.Double out = new Point2D.Double();
            at.transform(new Point2D.Double(0, 0), out);
            assertEquals("border=" + b + " left-top X", b, (int) Math.round(out.getX()));
            assertEquals("border=" + b + " left-top Y", b, (int) Math.round(out.getY()));
        }
    }
}
