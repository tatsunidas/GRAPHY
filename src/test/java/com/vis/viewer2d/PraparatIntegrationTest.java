package com.vis.viewer2d;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.Color;
import java.io.File;

import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

import ij.ImagePlus;
import ij.process.ShortProcessor;

/**
 * Layer 2: Praparat (2D viewer) integration tests in headless mode.
 *
 * Uses new ImagePlus(path) to load DICOM files via the dcm4che ImageIO SPI
 * (synchronous), so tests don't race against a SwingWorker.
 *
 * Multi-slice synthetic stacks are not testable because constructing them
 * requires DICOM metadata (ImageOrientationPatient) for the sort pass.
 * Those cases are covered by real DICOM files instead.
 */
public class PraparatIntegrationTest {

    private static final String CT_FILE =
        "/home/tatsunidas/graphy_sample_images/dicom_samples/JIRA_DICOM/CT_LEE_IR87a.dcm";

    private static boolean ctAvailable;

    @BeforeClass
    public static void checkFiles() {
        ctAvailable = new File(CT_FILE).isFile();
        if (!ctAvailable) System.out.println("[WARN] CT file not found, CT tests will be skipped.");
    }

    // -----------------------------------------------------------------------
    // Synthetic ImagePlus tests (single-frame, no DICOM metadata required)
    // -----------------------------------------------------------------------

    @Test
    public void synthetic_praparatCreatesWithoutException() {
        ShortProcessor sp = new ShortProcessor(64, 64);
        ImagePlus imp = new ImagePlus("test", sp);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        assertNotNull("Praparat must not be null", pp);
    }

    @Test
    public void synthetic_getAllSlidesIsNotEmpty() {
        ShortProcessor sp = new ShortProcessor(64, 64);
        ImagePlus imp = new ImagePlus("test", sp);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        assertFalse("getAllSlides() must not be empty", pp.getAllSlides().isEmpty());
    }

    @Test
    public void synthetic_getCurrentSlideIsNotNull() {
        ShortProcessor sp = new ShortProcessor(64, 64);
        ImagePlus imp = new ImagePlus("test", sp);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        SlideGlass sg = pp.getCurrentSlide();
        assertNotNull("getCurrentSlide() must return a SlideGlass", sg);
    }

    // -----------------------------------------------------------------------
    // Real DICOM CT file tests (single-frame, loaded synchronously via ImageIO)
    // -----------------------------------------------------------------------

    @Test
    public void ct_loadViaImageIOWorks() {
        Assume.assumeTrue("CT file required", ctAvailable);
        ImagePlus imp = new ImagePlus(CT_FILE);
        assertNotNull("ImagePlus from CT file must not be null", imp);
        assertTrue("CT must have at least 1 slice", imp.getNSlices() >= 1);
        assertTrue("CT width > 0", imp.getWidth() > 0);
        assertTrue("CT height > 0", imp.getHeight() > 0);
    }

    @Test
    public void ct_praparatCreatesFromImagePlus() {
        Assume.assumeTrue("CT file required", ctAvailable);
        ImagePlus imp = new ImagePlus(CT_FILE);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        assertNotNull("Praparat must not be null", pp);
        assertNotNull("getAllSlides() must not be null", pp.getAllSlides());
        assertFalse("getAllSlides() must not be empty", pp.getAllSlides().isEmpty());
    }

    @Test
    public void ct_getCurrentSlideIsNotNull() {
        Assume.assumeTrue("CT file required", ctAvailable);
        ImagePlus imp = new ImagePlus(CT_FILE);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        assertNotNull("getCurrentSlide() must return a SlideGlass", pp.getCurrentSlide());
    }

    @Test
    public void ct_pixelValueAtCenterIsNonNull() {
        Assume.assumeTrue("CT file required", ctAvailable);
        ImagePlus imp = new ImagePlus(CT_FILE);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        // Populate pixel data: images are lazily realized when "shown"
        pp.realizeImage(0);
        SlideGlass sg = pp.getCurrentSlide();
        assertNotNull("SlideGlass must exist", sg);

        int cx = imp.getWidth() / 2;
        int cy = imp.getHeight() / 2;
        Object[] vals = sg.getPixelValueFromOriginal(cx, cy);
        assertNotNull("Center pixel value must not be null", vals);
        assertEquals("Two values returned: raw and calibrated", 2, vals.length);
    }

    @Test
    public void ct_pixelValueIsFinite() {
        Assume.assumeTrue("CT file required", ctAvailable);
        ImagePlus imp = new ImagePlus(CT_FILE);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        pp.realizeImage(0);
        SlideGlass sg = pp.getCurrentSlide();

        int w = imp.getWidth();
        int h = imp.getHeight();
        for (int x : new int[]{ w/4, w/2, 3*w/4 }) {
            for (int y : new int[]{ h/4, h/2, 3*h/4 }) {
                Object[] vals = sg.getPixelValueFromOriginal(x, y);
                assertNotNull("pixel at (" + x + "," + y + ") must not be null", vals);
                double raw = (Double) vals[0];
                double cal = (Double) vals[1];
                assertTrue("raw at (" + x + "," + y + ") must be finite", Double.isFinite(raw));
                assertTrue("cal at (" + x + "," + y + ") must be finite", Double.isFinite(cal));
            }
        }
    }

    @Test
    public void ct_calibratedHuInRange() {
        Assume.assumeTrue("CT file required", ctAvailable);
        ImagePlus imp = new ImagePlus(CT_FILE);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        pp.realizeImage(0);
        SlideGlass sg = pp.getCurrentSlide();

        int cx = imp.getWidth() / 2;
        int cy = imp.getHeight() / 2;
        Object[] vals = sg.getPixelValueFromOriginal(cx, cy);
        assertNotNull("Center pixel value must not be null after realizeImage", vals);
        double calibrated = (Double) vals[1];
        // CT HU range: air≈-1000, soft tissue≈0–80, bone≈+400 to +1000
        assertTrue("CT calibrated value should be in reasonable HU range: " + calibrated,
                   calibrated >= -1200 && calibrated <= 4000);
    }

    @Test
    public void ct_outOfBoundsPixelReturnsNull() {
        Assume.assumeTrue("CT file required", ctAvailable);
        ImagePlus imp = new ImagePlus(CT_FILE);
        Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
        pp.realizeImage(0);
        SlideGlass sg = pp.getCurrentSlide();

        Object[] vals = sg.getPixelValueFromOriginal(-1, -1);
        assertNull("Out-of-bounds pixel must return null", vals);

        vals = sg.getPixelValueFromOriginal(imp.getWidth() + 10, imp.getHeight() + 10);
        assertNull("Far out-of-bounds pixel must return null", vals);
    }
}
