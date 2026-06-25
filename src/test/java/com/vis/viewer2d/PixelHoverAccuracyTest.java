package com.vis.viewer2d;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.Color;
import java.awt.Point;
import java.io.File;

import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

import ij.ImagePlus;
import ij.process.ShortProcessor;

/**
 * Layer 5: Mouse-hover pixel value accuracy (screen → image → value end-to-end).
 *
 * The hover handler {@code Praparat.setAndShowPixelValue(slide, slideX, slideY)}
 * does exactly two things to turn a mouse position into a displayed value:
 *
 *   pointOnOrg = currentSlide.offScreenCoordinate(slideX, slideY);   // screen → image
 *   val        = currentSlide.getPixelValueFromOriginal(pointOnOrg);  // image → raw/cal
 *
 * Layer 1 ({@code PixelValueTest}) checks the pixel math in isolation and Layer 2
 * ({@code SlideGlassCoordinateIntegrationTest}) checks the coordinate round-trip,
 * but neither verifies the *combined* path: that hovering screen pixel (sx,sy)
 * reports the value of the correct underlying image pixel. That combined path is
 * what the user actually sees, and an off-by-one in the inverse transform would
 * slip past both lower layers. This test closes that gap.
 */
public class PixelHoverAccuracyTest {

    private static final String CT_FILE =
        "/home/tatsunidas/graphy_sample_images/dicom_samples/JIRA_DICOM/CT_LEE_IR87a.dcm";

    private static boolean ctAvailable;

    @BeforeClass
    public static void checkFiles() {
        ctAvailable = new File(CT_FILE).isFile();
        if (!ctAvailable) System.out.println("[WARN] CT file not found, CT hover tests will be skipped.");
    }

    /**
     * Build a single-slide Praparat whose pixels carry a known, spatially-varying
     * pattern (value = x*100 + y) so an off-by-one mapping yields a wrong value.
     * Sizes the component + imageSpecimen and realizes the image for pixel access.
     */
    private static SlideGlass makeGradientSlide(int imgW, int imgH, int compW, int compH) {
        ShortProcessor sp = new ShortProcessor(imgW, imgH);
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                sp.set(x, y, x * 100 + y);
            }
        }
        ImagePlus imp = new ImagePlus("hover-gradient", sp);
        Praparat pp = new Praparat(imp, Color.DARK_GRAY, ViewMode.Normal, false);
        SlideGlass sg = pp.getCurrentSlide();
        sg.setSize(compW, compH);
        if (sg.imageSpecimen != null) sg.imageSpecimen.setSize(compW, compH);
        pp.realizeImage(0); // lazily realizes orgImg; required for pixel access
        return sg;
    }

    /** Raw value reported when "hovering" the screen position of image pixel (ix,iy). */
    private static Double hoverRawAtImagePixel(SlideGlass sg, int ix, int iy) throws Exception {
        Point screen = sg.slideglassCoordinateFromOffScreen(ix, iy);
        Point back   = sg.offScreenCoordinate(screen.x, screen.y); // the real hover step
        Object[] val = sg.getPixelValueFromOriginal(back.x, back.y);
        if (val == null) return null;
        return (Double) ((Double[]) val)[0];
    }

    // -----------------------------------------------------------------------
    // Sanity: the synthetic gradient really is spatially varying once realized.
    // If this fails, the off-by-one tests below would be meaningless.
    // -----------------------------------------------------------------------

    @Test
    public void synthetic_gradientHasSpatialVariation() {
        SlideGlass sg = makeGradientSlide(64, 64, 128, 128);
        Object[] a = sg.getPixelValueFromOriginal(10, 20);
        Object[] b = sg.getPixelValueFromOriginal(40, 5);
        assertNotNull("(10,20) must be readable", a);
        assertNotNull("(40,5) must be readable", b);
        double va = (Double) ((Double[]) a)[0];
        double vb = (Double) ((Double[]) b)[0];
        assertNotEquals("realized image must retain distinct pixel values", va, vb, 0.0);
    }

    // -----------------------------------------------------------------------
    // Hover at the screen position of a known image pixel reports that pixel's value.
    // -----------------------------------------------------------------------

    @Test
    public void hover_centerPixel_reportsCorrectRawValue() throws Exception {
        SlideGlass sg = makeGradientSlide(64, 64, 128, 128);
        int ix = 32, iy = 32;
        double truth = (Double) ((Double[]) sg.getPixelValueFromOriginal(ix, iy))[0];
        Double hover = hoverRawAtImagePixel(sg, ix, iy);
        assertNotNull("hover over center must yield a value", hover);
        assertEquals("hover value must equal underlying pixel", truth, hover, 0.0);
        assertEquals("expected gradient value x*100+y", (double)(ix * 100 + iy), hover, 0.0);
    }

    @Test
    public void hover_manyPixels_noOffByOne() throws Exception {
        SlideGlass sg = makeGradientSlide(64, 64, 128, 128);
        int[][] probes = {
            {1, 1}, {5, 17}, {17, 5}, {30, 40}, {40, 30}, {62, 62}, {0, 63}, {63, 0}
        };
        for (int[] p : probes) {
            int ix = p[0], iy = p[1];
            Point screen = sg.slideglassCoordinateFromOffScreen(ix, iy);
            Point back   = sg.offScreenCoordinate(screen.x, screen.y);
            assertEquals("inverse transform X for (" + ix + "," + iy + ")", ix, back.x, 1.0);
            assertEquals("inverse transform Y for (" + ix + "," + iy + ")", iy, back.y, 1.0);

            Double hover = hoverRawAtImagePixel(sg, ix, iy);
            assertNotNull("hover at (" + ix + "," + iy + ") must yield a value", hover);
            assertEquals("hover raw at (" + ix + "," + iy + ")",
                         (double)(ix * 100 + iy), hover, 0.0);
        }
    }

    @Test
    public void hover_corners_reportCornerValues() throws Exception {
        int W = 64, H = 64;
        SlideGlass sg = makeGradientSlide(W, H, 256, 256); // 4x zoom
        int[][] corners = { {0,0}, {W-1,0}, {0,H-1}, {W-1,H-1} };
        for (int[] c : corners) {
            Double hover = hoverRawAtImagePixel(sg, c[0], c[1]);
            assertNotNull("corner (" + c[0] + "," + c[1] + ") must be readable", hover);
            assertEquals("corner raw value", (double)(c[0]*100 + c[1]), hover, 0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Out-of-bounds: hovering outside the image returns null (handler shows "null").
    // -----------------------------------------------------------------------

    @Test
    public void hover_belowZeroScreen_returnsNull() throws Exception {
        SlideGlass sg = makeGradientSlide(64, 64, 128, 128);
        // A screen point well above/left of the image maps to a negative image coord.
        Point back = sg.offScreenCoordinate(-100, -100);
        Object[] val = sg.getPixelValueFromOriginal(back.x, back.y);
        assertNull("hovering outside the image (top-left) must return null", val);
    }

    @Test
    public void hover_beyondExtentScreen_returnsNull() throws Exception {
        SlideGlass sg = makeGradientSlide(64, 64, 128, 128);
        // Far beyond the bottom-right of the displayed image.
        Point back = sg.offScreenCoordinate(100000, 100000);
        Object[] val = sg.getPixelValueFromOriginal(back.x, back.y);
        assertNull("hovering past the image extent must return null", val);
    }

    // -----------------------------------------------------------------------
    // Real CT: hovering reports a calibrated HU value alongside the raw value,
    // verifying the calibrated branch through the full screen→image→value path.
    // -----------------------------------------------------------------------

    @Test
    public void hover_ct_reportsCalibratedHuThroughScreenPath() throws Exception {
        Assume.assumeTrue("CT file required", ctAvailable);
        ImagePlus imp = new ImagePlus(CT_FILE);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        SlideGlass sg = pp.getCurrentSlide();
        int compW = imp.getWidth() * 2, compH = imp.getHeight() * 2;
        sg.setSize(compW, compH);
        if (sg.imageSpecimen != null) sg.imageSpecimen.setSize(compW, compH);
        pp.realizeImage(0);

        int ix = imp.getWidth() / 2, iy = imp.getHeight() / 2;

        // Ground truth read directly at the image pixel.
        Object[] direct = sg.getPixelValueFromOriginal(ix, iy);
        assertNotNull("direct center read must be non-null", direct);
        double truthRaw = (Double) ((Double[]) direct)[0];
        double truthCal = (Double) ((Double[]) direct)[1];

        // Same pixel reached via the hover path (screen → image).
        Point screen = sg.slideglassCoordinateFromOffScreen(ix, iy);
        Point back   = sg.offScreenCoordinate(screen.x, screen.y);
        assertEquals("CT inverse transform X", ix, back.x, 1.0);
        assertEquals("CT inverse transform Y", iy, back.y, 1.0);

        Object[] hover = sg.getPixelValueFromOriginal(back.x, back.y);
        assertNotNull("CT hover value must be non-null", hover);
        double hoverRaw = (Double) ((Double[]) hover)[0];
        double hoverCal = (Double) ((Double[]) hover)[1];

        assertEquals("CT hover raw must match direct read", truthRaw, hoverRaw, 0.0);
        assertEquals("CT hover calibrated must match direct read", truthCal, hoverCal, 0.0);
        assertTrue("CT calibrated HU should be in a plausible range: " + hoverCal,
                   hoverCal >= -1200 && hoverCal <= 4000);
    }
}
