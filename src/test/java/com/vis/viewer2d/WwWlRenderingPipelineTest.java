package com.vis.viewer2d;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.image.BufferedImage;

import com.vis.core.view.D2.processing.ImageProcessing;

import ij.ImagePlus;
import ij.process.ShortProcessor;

/**
 * Layer 3: WW/WL rendering pipeline tests.
 *
 * Verifies that ImageProcessing.windowing() correctly maps 16-bit pixel values
 * to 8-bit display values via ImageJ's display range mechanism.
 *
 * Pipeline under test:
 *   ShortProcessor → setDisplayRange(min, max) → getBufferedImage() → RGB pixel
 *
 * All tests are headless-safe (no Swing component, no display needed).
 */
public class WwWlRenderingPipelineTest {

    private static ImageProcessing imgProc;

    @BeforeClass
    public static void setup() {
        imgProc = new ImageProcessing();
    }

    /** Helper: single-pixel image with one known value, no calibration. */
    private static ImagePlus singlePixel(int rawValue) {
        ShortProcessor sp = new ShortProcessor(1, 1);
        sp.set(0, 0, rawValue);
        return new ImagePlus("px", sp);
    }

    /** Helper: extract gray value (R channel) from 1x1 rendered image. */
    private static int renderGray(ImagePlus imp) {
        BufferedImage bi = imp.getBufferedImage();
        assertNotNull("getBufferedImage() must not return null", bi);
        int rgb = bi.getRGB(0, 0);
        return (rgb >> 16) & 0xFF; // red channel (= green = blue for gray)
    }

    // -----------------------------------------------------------------------
    // Display range clamping
    // -----------------------------------------------------------------------

    @Test
    public void pixelAtMinMapsToBlack() {
        ImagePlus imp = singlePixel(0);
        imgProc.windowing(imp, 0, 1000);
        assertEquals("pixel=min must render black", 0, renderGray(imp));
    }

    @Test
    public void pixelAtMaxMapsToWhite() {
        ImagePlus imp = singlePixel(1000);
        imgProc.windowing(imp, 0, 1000);
        int gray = renderGray(imp);
        assertTrue("pixel=max must render white (>=250): " + gray, gray >= 250);
    }

    @Test
    public void pixelAtMidpointMapsToMidGray() {
        // value = 500, range [0, 1000].
        // ImageJ ShortProcessor applies gamma ≈ 0.45:  (500/1000)^0.45 * 255 ≈ 188.
        // The exact value depends on the LUT/gamma; test that it's in the upper-mid range.
        ImagePlus imp = singlePixel(500);
        imgProc.windowing(imp, 0, 1000);
        int gray = renderGray(imp);
        assertTrue("midpoint pixel should give upper-mid gray (128–240): " + gray,
                   gray >= 128 && gray <= 240);
    }

    @Test
    public void pixelBelowMinClampsToBlack() {
        // value = -100 as unsigned short = 65436 → below min=0 if stored as 0
        // Use value 0, min=100: output should be clamped to 0
        ImagePlus imp = singlePixel(0);
        imgProc.windowing(imp, 100, 1000);
        int gray = renderGray(imp);
        assertEquals("pixel below display min must render black", 0, gray);
    }

    @Test
    public void pixelAboveMaxClampsToWhite() {
        ImagePlus imp = singlePixel(2000);
        imgProc.windowing(imp, 0, 1000);
        int gray = renderGray(imp);
        assertTrue("pixel above display max must render white (>=250): " + gray, gray >= 250);
    }

    // -----------------------------------------------------------------------
    // Window width / window center semantics
    // -----------------------------------------------------------------------

    @Test
    public void narrowWindowIncreasesContrast() {
        // Same pixel value, narrower window → higher output gray
        int pixVal = 300;
        ImagePlus wideImp   = singlePixel(pixVal);
        ImagePlus narrowImp = singlePixel(pixVal);

        imgProc.windowing(wideImp,   0, 1000); // 300/1000 → 30% → ~77
        imgProc.windowing(narrowImp, 0, 400);  // 300/400 → 75% → ~191

        int grayWide   = renderGray(wideImp);
        int grayNarrow = renderGray(narrowImp);

        assertTrue("narrow window must give higher gray than wide window: "
                   + grayNarrow + " vs " + grayWide,
                   grayNarrow > grayWide);
    }

    @Test
    public void wideWindowDecreasesContrast() {
        // Pixel at 1000, compared between range [0,2000] and [0,1000]
        ImagePlus wideImp   = singlePixel(1000);
        ImagePlus narrowImp = singlePixel(1000);

        imgProc.windowing(wideImp,   0, 2000); // 1000/2000 = 50% → ~128
        imgProc.windowing(narrowImp, 0, 1000); // 1000/1000 = 100% → ~255

        int grayWide   = renderGray(wideImp);
        int grayNarrow = renderGray(narrowImp);

        assertTrue("wide window must give lower gray: " + grayWide + " < " + grayNarrow,
                   grayWide < grayNarrow);
    }

    @Test
    public void windowingIsMonotone() {
        // For increasing pixel values, gray output must be non-decreasing
        int[] values = { 0, 100, 200, 300, 400, 500 };
        int[] grays  = new int[values.length];

        for (int i = 0; i < values.length; i++) {
            ImagePlus imp = singlePixel(values[i]);
            imgProc.windowing(imp, 0, 500);
            grays[i] = renderGray(imp);
        }

        for (int i = 1; i < grays.length; i++) {
            assertTrue("gray[" + i + "]=" + grays[i] + " must be >= gray[" + (i-1) + "]=" + grays[i-1],
                       grays[i] >= grays[i-1]);
        }
    }

    // -----------------------------------------------------------------------
    // Pixel-level accuracy: gamma-corrected mapping
    // ImageJ ShortProcessor applies gamma ≈ 0.45 for display conversion.
    // Formula: output ≈ (value / max)^0.45 * 255
    // -----------------------------------------------------------------------

    @Test
    public void quarterPointMapsToGammaCorrectedGray() {
        // value=250, range [0,1000]: (0.25)^0.45 * 255 ≈ 137; allow ±20
        ImagePlus imp = singlePixel(250);
        imgProc.windowing(imp, 0, 1000);
        int gray = renderGray(imp);
        assertTrue("quarter value should give gamma-corrected gray (~137, range 110-160): " + gray,
                   gray >= 110 && gray <= 160);
    }

    @Test
    public void threeQuarterPointMapsToGammaCorrectedGray() {
        // value=750, range [0,1000]: (0.75)^0.45 * 255 ≈ 225; allow ±20
        ImagePlus imp = singlePixel(750);
        imgProc.windowing(imp, 0, 1000);
        int gray = renderGray(imp);
        assertTrue("three-quarter value should give gamma-corrected gray (~225, range 205-245): " + gray,
                   gray >= 205 && gray <= 245);
    }
}
