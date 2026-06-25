package com.vis.viewer2d;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;

import com.vis.configuration.Resources;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.processing.ImageProcessing;

import ij.ImagePlus;
import ij.process.LUT;
import ij.process.ShortProcessor;

/**
 * Layer 3: LUT application rendering tests.
 *
 * Verifies that ImageProcessing.applyLUT() produces visually distinct output
 * for different LUTs. Uses ImageJ's getBufferedImage() to read the rendered
 * color at a known pixel, all without a display.
 */
public class LutRenderingTest {

    private static ImageProcessing imgProc;
    private static boolean lutsAvailable;

    @BeforeClass
    public static void setup() {
        imgProc = new ImageProcessing();
        Utils.isDebug = true; // use relative "luts/" path
        lutsAvailable = new File("luts").isDirectory();
        if (!lutsAvailable) System.out.println("[WARN] luts/ not found, LUT tests will be skipped.");
    }

    /** Helper: 1x1 image at a given raw value (full display range). */
    private static ImagePlus pixel(int value, int rangeMax) {
        ShortProcessor sp = new ShortProcessor(rangeMax + 1, 1);
        for (int i = 0; i <= rangeMax; i++) sp.set(i, 0, i); // gradient row
        ImagePlus imp = new ImagePlus("lut-test", sp);
        imp.setDisplayRange(0, rangeMax);
        return imp;
    }

    private static int[] rgb(BufferedImage bi, int x) {
        int argb = bi.getRGB(x, 0);
        return new int[]{ (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF };
    }

    // -----------------------------------------------------------------------
    // Gray LUT: output must be achromatic (R == G == B)
    // -----------------------------------------------------------------------

    @Test
    public void grayLut_isAchromatic() {
        Assume.assumeTrue("luts/ required", lutsAvailable);
        LUT gray = Resources.loadLUT("gray");
        assertNotNull(gray);

        ImagePlus imp = pixel(500, 1000);
        imgProc.applyLUT(imp, gray);

        BufferedImage bi = imp.getBufferedImage();
        assertNotNull(bi);

        // Check every 50th pixel: R, G, B must be equal (achromatic)
        for (int x = 0; x <= 1000; x += 50) {
            int[] c = rgb(bi, x);
            assertEquals("gray LUT R==G at x=" + x, c[0], c[1], 3);
            assertEquals("gray LUT G==B at x=" + x, c[1], c[2], 3);
        }
    }

    @Test
    public void grayLut_darkPixelIsNearBlack() {
        Assume.assumeTrue("luts/ required", lutsAvailable);
        LUT gray = Resources.loadLUT("gray");
        ImagePlus imp = pixel(500, 1000);
        imgProc.applyLUT(imp, gray);
        int[] c = rgb(imp.getBufferedImage(), 0); // pixel value 0 = min
        assertTrue("dark end should be near black: R=" + c[0], c[0] <= 10);
    }

    @Test
    public void grayLut_brightPixelIsNearWhite() {
        Assume.assumeTrue("luts/ required", lutsAvailable);
        LUT gray = Resources.loadLUT("gray");
        ImagePlus imp = pixel(500, 1000);
        imgProc.applyLUT(imp, gray);
        int[] c = rgb(imp.getBufferedImage(), 1000); // pixel value 1000 = max
        assertTrue("bright end should be near white: R=" + c[0], c[0] >= 245);
    }

    // -----------------------------------------------------------------------
    // Fire LUT: output must be colorful (not achromatic)
    // -----------------------------------------------------------------------

    @Test
    public void fireLut_midPixelIsColored() {
        Assume.assumeTrue("luts/ required", lutsAvailable);
        LUT fire = Resources.loadLUT("Fire-1");
        assertNotNull(fire);

        ImagePlus imp = pixel(500, 1000);
        imgProc.applyLUT(imp, fire);
        int[] c = rgb(imp.getBufferedImage(), 500); // mid-value pixel

        // Fire LUT mid-value is typically red/yellow — at least one channel differs
        boolean isColored = Math.abs(c[0] - c[1]) > 20 || Math.abs(c[1] - c[2]) > 20;
        assertTrue("Fire LUT mid-value should be colored, not gray: R=" + c[0] + " G=" + c[1] + " B=" + c[2],
                   isColored);
    }

    @Test
    public void fireLut_differentFromGrayLut() {
        Assume.assumeTrue("luts/ required", lutsAvailable);
        LUT gray = Resources.loadLUT("gray");
        LUT fire = Resources.loadLUT("Fire-1");

        // Mid-value pixel rendered with each LUT
        ImagePlus grayImp = pixel(500, 1000);
        ImagePlus fireImp = pixel(500, 1000);
        imgProc.applyLUT(grayImp, gray);
        imgProc.applyLUT(fireImp, fire);

        int[] grayC = rgb(grayImp.getBufferedImage(), 500);
        int[] fireC = rgb(fireImp.getBufferedImage(), 500);

        // At least one channel must differ significantly
        boolean differ = Math.abs(grayC[0] - fireC[0]) > 20
                      || Math.abs(grayC[1] - fireC[1]) > 20
                      || Math.abs(grayC[2] - fireC[2]) > 20;
        assertTrue("Fire LUT must produce different colors than gray LUT at mid-value", differ);
    }

    // -----------------------------------------------------------------------
    // S_Pet LUT: should be colorful (PET scan display)
    // -----------------------------------------------------------------------

    @Test
    public void sPetLut_midPixelIsColored() {
        Assume.assumeTrue("luts/ required", lutsAvailable);
        LUT sPet = Resources.loadLUT("S_Pet");
        assertNotNull(sPet);

        ImagePlus imp = pixel(500, 1000);
        imgProc.applyLUT(imp, sPet);
        int[] c = rgb(imp.getBufferedImage(), 500);

        boolean isColored = Math.abs(c[0] - c[1]) > 20 || Math.abs(c[1] - c[2]) > 20;
        assertTrue("S_Pet LUT mid-value should be colored: R=" + c[0] + " G=" + c[1] + " B=" + c[2],
                   isColored);
    }

    // -----------------------------------------------------------------------
    // LUT replacement: switching LUT changes the output
    // -----------------------------------------------------------------------

    @Test
    public void switchingLutChangesOutput() {
        Assume.assumeTrue("luts/ required", lutsAvailable);
        LUT gray  = Resources.loadLUT("gray");
        LUT fire  = Resources.loadLUT("Fire-1");

        ShortProcessor sp = new ShortProcessor(1, 1);
        sp.set(0, 0, 500);
        ImagePlus imp = new ImagePlus("switch", sp);
        imp.setDisplayRange(0, 1000);

        // Apply gray first
        imgProc.applyLUT(imp, gray);
        int[] afterGray = rgb(imp.getBufferedImage(), 0);

        // Then switch to fire
        imgProc.applyLUT(imp, fire);
        int[] afterFire = rgb(imp.getBufferedImage(), 0);

        boolean changed = afterGray[0] != afterFire[0]
                       || afterGray[1] != afterFire[1]
                       || afterGray[2] != afterFire[2];
        assertTrue("Switching from gray to fire LUT must change the output color", changed);
    }
}
