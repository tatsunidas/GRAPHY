package com.vis.viewer2d;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import com.vis.core.view.D2.roi.Measurements;
import com.vis.core.view.D2.roi.OvalRoi;
import com.vis.core.view.D2.roi.RoiAnalyzer;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiType;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

import ij.ImagePlus;
import ij.process.ShortProcessor;

/**
 * Layer 6: ROI drawing accuracy (screen-drag → image-space geometry + pixels).
 *
 * When a user draws a rectangle/oval, the real pipeline is:
 *   1. CanvasGlass.createNewRoi(sx1,sy1,type) — converts the press point to image
 *      coords via {@code sg.offScreenCoordinate} and constructs a CONSTRUCTING ROI.
 *   2. RoiObj.mouseDrag(sx2,sy2,flags) → grow(sx2,sy2) — converts the drag point to
 *      image coords and resizes the ROI relative to the start point.
 *   3. RoiObj.handleMouseUp(...) — finalizes to NORMAL.
 *
 * Layer 1 ({@code RoiMeasurementsTest}) and Layer 2 ({@code RoiStateIntegrationTest})
 * build ROIs already expressed in image coordinates, so they never exercise the
 * screen→image conversion that drawing performs. This test does: it drives the real
 * createNewRoi + mouseDrag path and asserts (a) the resulting ROI bounding box lands
 * on the correct image pixels, and (b) RoiAnalyzer over the drawn ROI measures the
 * pixels actually enclosed. This is the ROI analog of Layer 5's hover accuracy.
 */
public class RoiDrawingAccuracyTest {

    private static final String CT_FILE =
        "/home/tatsunidas/graphy_sample_images/dicom_samples/JIRA_DICOM/CT_LEE_IR87a.dcm";

    private static boolean ctAvailable;

    @BeforeClass
    public static void checkFiles() {
        ctAvailable = new File(CT_FILE).isFile();
        if (!ctAvailable) System.out.println("[WARN] CT file not found, CT drawing tests will be skipped.");
    }

    /** Single-slide Praparat over a uniform image, sized + realized for drawing. */
    private static SlideGlass makeUniformSlide(int imgW, int imgH, int fill, int compW, int compH) {
        ShortProcessor sp = new ShortProcessor(imgW, imgH);
        for (int y = 0; y < imgH; y++)
            for (int x = 0; x < imgW; x++)
                sp.set(x, y, fill);
        return realizeSized(sp, compW, compH);
    }

    private static SlideGlass realizeSized(ShortProcessor sp, int compW, int compH) {
        ImagePlus imp = new ImagePlus("roi-draw", sp);
        Praparat pp = new Praparat(imp, Color.DARK_GRAY, ViewMode.Normal, false);
        SlideGlass sg = pp.getCurrentSlide();
        sg.setSize(compW, compH);
        if (sg.imageSpecimen != null) sg.imageSpecimen.setSize(compW, compH);
        pp.realizeImage(0); // realize before ROI creation: grow() clamps to image bounds
        return sg;
    }

    /**
     * Draw an ROI exactly as the UI does. This mirrors {@code CanvasGlass.createNewRoi}'s
     * geometric construction (press point → image coords via offScreenCoordinate, then a
     * 1x1 ROI in CONSTRUCTING state — see CanvasGlass.java lines 348/352) followed by the
     * real {@code mouseDrag→grow} and {@code handleMouseUp} path. We construct directly
     * rather than calling createNewRoi() to avoid its DB-persistence side effect
     * (insertOrUpdateRoi4DB pops a headless JOptionPane without a started database);
     * persistence is covered separately by Layer 4. The screen→image conversion under
     * test (offScreenCoordinate + grow) is identical either way.
     */
    private static RoiObj drawRoi(SlideGlass sg, int type, int sx1, int sy1, int sx2, int sy2)
            throws Exception {
        Point p = sg.offScreenCoordinate(sx1, sy1); // press: screen → image (as createNewRoi does)
        RoiObj roi;
        if (type == RoiType.OVAL.id()) {
            roi = new OvalRoi((double) p.x, (double) p.y, 1, 1, sg);
        } else {
            roi = new RoiObj(p.x, p.y, 1, 1, 0, sg);
        }
        roi.setState(RoiObj.CONSTRUCTING);
        roi.mouseDrag(sx2, sy2, 0); // drag: real grow() screen → image
        roi.handleMouseUp(sx2, sy2); // release: finalize to NORMAL
        return roi;
    }

    /** Image-space bounding box of the two screen points after inverse transform. */
    private static Rectangle expectedImageBounds(SlideGlass sg, int sx1, int sy1, int sx2, int sy2)
            throws Exception {
        Point a = sg.offScreenCoordinate(sx1, sy1);
        Point b = sg.offScreenCoordinate(sx2, sy2);
        int x = Math.min(a.x, b.x), y = Math.min(a.y, b.y);
        int w = Math.abs(b.x - a.x), h = Math.abs(b.y - a.y);
        return new Rectangle(x, y, w, h);
    }

    private static void assertBoundsClose(String msg, Rectangle exp, Rectangle act, int tol) {
        assertEquals(msg + " x", exp.x, act.x, tol);
        assertEquals(msg + " y", exp.y, act.y, tol);
        assertEquals(msg + " w", exp.width, act.width, tol);
        assertEquals(msg + " h", exp.height, act.height, tol);
    }

    // -----------------------------------------------------------------------
    // Rectangle: drawn screen rectangle maps to the correct image rectangle.
    // -----------------------------------------------------------------------

    @Test
    public void rectangle_screenDrag_mapsToCorrectImageBounds() throws Exception {
        // 200x200 image shown in a 400x400 panel (≈2x zoom).
        SlideGlass sg = makeUniformSlide(200, 200, 1000, 400, 400);
        int sx1 = 100, sy1 = 120, sx2 = 300, sy2 = 260;

        Rectangle exp = expectedImageBounds(sg, sx1, sy1, sx2, sy2);
        RoiObj roi = drawRoi(sg, RoiType.RECTANGLE.id(), sx1, sy1, sx2, sy2);

        assertBoundsClose("drawn rectangle image bounds", exp, roi.getBounds(), 1);
        assertTrue("drawn rectangle must have positive area",
                   roi.getBounds().width > 0 && roi.getBounds().height > 0);
    }

    @Test
    public void rectangle_reversedDrag_normalizesBounds() throws Exception {
        // Dragging bottom-right → top-left must still yield a normalized (positive) box.
        SlideGlass sg = makeUniformSlide(200, 200, 1000, 400, 400);
        int sx1 = 300, sy1 = 260, sx2 = 100, sy2 = 120;

        Rectangle exp = expectedImageBounds(sg, sx1, sy1, sx2, sy2);
        RoiObj roi = drawRoi(sg, RoiType.RECTANGLE.id(), sx1, sy1, sx2, sy2);

        assertBoundsClose("reversed-drag rectangle bounds", exp, roi.getBounds(), 1);
    }

    // -----------------------------------------------------------------------
    // Oval: bounding box of the drawn oval matches the dragged screen rectangle.
    // -----------------------------------------------------------------------

    @Test
    public void oval_screenDrag_boundingBoxMatches() throws Exception {
        SlideGlass sg = makeUniformSlide(200, 200, 1000, 400, 400);
        int sx1 = 120, sy1 = 140, sx2 = 280, sy2 = 300;

        Rectangle exp = expectedImageBounds(sg, sx1, sy1, sx2, sy2);
        RoiObj roi = drawRoi(sg, RoiType.OVAL.id(), sx1, sy1, sx2, sy2);

        assertBoundsClose("drawn oval bounding box", exp, roi.getBounds(), 1);
    }

    // -----------------------------------------------------------------------
    // Enclosed pixels: RoiAnalyzer over the drawn ROI measures the right region.
    // -----------------------------------------------------------------------

    @Test
    public void rectangle_drawnRoi_measuresEnclosedPixels() throws Exception {
        final int FILL = 1500;
        SlideGlass sg = makeUniformSlide(200, 200, FILL, 400, 400);
        int sx1 = 100, sy1 = 100, sx2 = 300, sy2 = 300;

        Rectangle exp = expectedImageBounds(sg, sx1, sy1, sx2, sy2);
        RoiObj roi = drawRoi(sg, RoiType.RECTANGLE.id(), sx1, sy1, sx2, sy2);

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, sg.getOriginalImage());
        List<HashMap<Measurements, Double>> res = analyzer.measure();
        assertFalse("measurement results must not be empty", res.isEmpty());

        double area = res.get(0).get(Measurements.AREA);
        double mean = res.get(0).get(Measurements.MEAN);
        assertEquals("drawn-ROI area = expected w*h (image px)",
                     (double) exp.width * exp.height, area, Math.max(exp.width, exp.height) + 2.0);
        assertEquals("uniform field: drawn-ROI mean = fill value", (double) FILL, mean, 1.0);
    }

    @Test
    public void rectangle_drawnRoi_enclosesKnownHotPixel() throws Exception {
        // Uniform field with one bright pixel; a rect drawn around it must capture it as MAX.
        final int BG = 800, HOT = 4000;
        ShortProcessor sp = new ShortProcessor(200, 200);
        for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) sp.set(x, y, BG);
        int hotIx = 75, hotIy = 60;
        sp.set(hotIx, hotIy, HOT);
        SlideGlass sg = realizeSized(sp, 400, 400); // ≈2x zoom

        // Screen rectangle whose inverse-mapped bounds enclose (hotIx,hotIy).
        Point tl = sg.slideglassCoordinateFromOffScreen(hotIx - 20, hotIy - 20);
        Point br = sg.slideglassCoordinateFromOffScreen(hotIx + 20, hotIy + 20);
        RoiObj roi = drawRoi(sg, RoiType.RECTANGLE.id(), tl.x, tl.y, br.x, br.y);

        assertTrue("drawn ROI must contain the hot image pixel",
                   roi.getBounds().contains(hotIx, hotIy));

        RoiAnalyzer analyzer = new RoiAnalyzer(roi, sg.getOriginalImage());
        double max = analyzer.measure().get(0).get(Measurements.MAX);
        assertEquals("drawn ROI must capture the hot pixel as MAX", (double) HOT, max, 1.0);
    }

    // -----------------------------------------------------------------------
    // Zoom + Pan: the screen→image conversion runs through SlideGlass's AffineTransform,
    // which folds in both the user zoom magnification and the pan (display origin). The
    // tests above only exercised a single ≈2x zoom with no pan, so a regression in how
    // pan or a different zoom level is incorporated would slip past them. These drive the
    // SAME transform inputs the live UI sets (magnification + display origin) and then
    // refresh the cached transform exactly as ImageSpecimenGlass.updateDisplayImage() does
    // (its line 452 calls sg.calculateCurrentAffineTransform()). NOTE: in headless tests
    // updateDisplayImage() early-returns (component not displayable), so we must force the
    // recalculation ourselves — otherwise offScreenCoordinate would use a stale transform.
    // -----------------------------------------------------------------------

    /**
     * Apply a user zoom magnification and a pan offset to the slide, then refresh the
     * cached transform. Mirrors the real pan/zoom flow: {@code zoom()} ultimately sets
     * {@code magnification}; {@code panning()} shifts {@code imageSpecimen} display origin.
     */
    private static void setZoomPan(SlideGlass sg, double mag, int panDx, int panDy) throws Exception {
        Method setMag = SlideGlass.class.getDeclaredMethod("setMagnification", double.class);
        setMag.setAccessible(true);
        setMag.invoke(sg, mag);

        Point o = sg.getDisplayImageOriginXY();              // pan = move display origin
        sg.imageSpecimen.updateOrigin(o.x + panDx, o.y + panDy);

        Method calc = SlideGlass.class.getDeclaredMethod("calculateCurrentAffineTransform");
        calc.setAccessible(true);
        calc.invoke(sg);                                     // == updateDisplayImage() line 452
    }

    private static SlideGlass makeHotPixelSlide(int imgW, int imgH, int bg,
            int hotIx, int hotIy, int hot, int compW, int compH) {
        ShortProcessor sp = new ShortProcessor(imgW, imgH);
        for (int y = 0; y < imgH; y++)
            for (int x = 0; x < imgW; x++)
                sp.set(x, y, bg);
        sp.set(hotIx, hotIy, hot);
        return realizeSized(sp, compW, compH);
    }

    @Test
    public void pan_shiftsScreenPositionOfFixedImagePixel() throws Exception {
        // Sanity: a pan must actually move where an image pixel lands on screen — otherwise
        // the zoom+pan ROI test below could "pass" simply because pan was silently ignored.
        SlideGlass sg = makeUniformSlide(200, 200, 1000, 400, 400);
        setZoomPan(sg, 1.0, 0, 0);
        Point before = sg.slideglassCoordinateFromOffScreen(100, 100);
        setZoomPan(sg, 1.0, 50, 30);
        Point after = sg.slideglassCoordinateFromOffScreen(100, 100);
        assertEquals("pan must shift screen X of a fixed image pixel by panDx", 50, after.x - before.x, 1);
        assertEquals("pan must shift screen Y of a fixed image pixel by panDy", 30, after.y - before.y, 1);
    }

    @Test
    public void rectangle_underZoomAndPan_enclosesKnownHotPixel() throws Exception {
        // Forward → draw → inverse round-trip at several zoom levels and pan offsets.
        // A hot pixel sits at a KNOWN image coordinate; we forward-map (via
        // slideglassCoordinateFromOffScreen) image points around it to screen, draw the
        // screen rectangle, and require the resulting image-space ROI to (a) match the
        // independent inverse-transform bbox, (b) enclose the hot pixel, and (c) report it
        // as MAX. This is true ground truth: forward and inverse must agree under zoom+pan.
        final int BG = 800, HOT = 4000, hotIx = 75, hotIy = 60, M = 18;
        double[] zooms = { 0.5, 1.0, 2.0, 4.0 };
        int[][] pans = { {0, 0}, {40, 25}, {-33, 18} };

        for (double mag : zooms) {
            for (int[] pan : pans) {
                SlideGlass sg = makeHotPixelSlide(200, 200, BG, hotIx, hotIy, HOT, 400, 400);
                setZoomPan(sg, mag, pan[0], pan[1]);
                String tag = "mag=" + mag + " pan=(" + pan[0] + "," + pan[1] + ")";

                Point tl = sg.slideglassCoordinateFromOffScreen(hotIx - M, hotIy - M);
                Point br = sg.slideglassCoordinateFromOffScreen(hotIx + M, hotIy + M);

                Rectangle exp = expectedImageBounds(sg, tl.x, tl.y, br.x, br.y);
                RoiObj roi = drawRoi(sg, RoiType.RECTANGLE.id(), tl.x, tl.y, br.x, br.y);

                assertBoundsClose(tag + " drawn bounds vs inverse-transform", exp, roi.getBounds(), 1);
                assertTrue(tag + ": drawn ROI must enclose the hot image pixel",
                           roi.getBounds().contains(hotIx, hotIy));

                double max = new RoiAnalyzer(roi, sg.getOriginalImage())
                        .measure().get(0).get(Measurements.MAX);
                assertEquals(tag + ": drawn ROI must capture the hot pixel as MAX",
                             (double) HOT, max, 1.0);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Real CT: a screen-drawn ROI measures the same as the equivalent image-space ROI.
    // -----------------------------------------------------------------------

    @Test
    public void rectangle_ct_drawnRoiMatchesDirectImageRoi() throws Exception {
        Assume.assumeTrue("CT file required", ctAvailable);
        ImagePlus imp = new ImagePlus(CT_FILE);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        SlideGlass sg = pp.getCurrentSlide();
        int compW = imp.getWidth() * 2, compH = imp.getHeight() * 2;
        sg.setSize(compW, compH);
        if (sg.imageSpecimen != null) sg.imageSpecimen.setSize(compW, compH);
        pp.realizeImage(0);

        int sx1 = compW / 4, sy1 = compH / 4, sx2 = compW / 4 + 120, sy2 = compH / 4 + 100;
        Rectangle exp = expectedImageBounds(sg, sx1, sy1, sx2, sy2);

        // Drawn via the screen path.
        RoiObj drawn = drawRoi(sg, RoiType.RECTANGLE.id(), sx1, sy1, sx2, sy2);
        assertBoundsClose("CT drawn rectangle image bounds", exp, drawn.getBounds(), 1);

        double drawnMean = new RoiAnalyzer(drawn, sg.getOriginalImage())
                .measure().get(0).get(Measurements.MEAN);

        // Equivalent rectangle constructed directly in image coordinates.
        RoiObj direct = new RoiObj(exp.x, exp.y, exp.width, exp.height, sg);
        double directMean = new RoiAnalyzer(direct, sg.getOriginalImage())
                .measure().get(0).get(Measurements.MEAN);

        assertEquals("screen-drawn ROI mean must match direct image-space ROI mean",
                     directMean, drawnMean, 0.5);
        assertTrue("CT mean HU should be in a plausible range: " + drawnMean,
                   drawnMean >= -1200 && drawnMean <= 4000);
    }
}
