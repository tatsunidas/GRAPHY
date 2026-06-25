package com.vis.viewer2d;

import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.AffineTransform;

import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

import ij.ImagePlus;
import ij.process.ShortProcessor;

/**
 * Layer 2: SlideGlass coordinate mapping integration tests.
 *
 * Verifies that the real SlideGlass AffineTransform-based coordinate mapping
 * (offScreenCoordinate ↔ slideglassCoordinateFromOffScreen) round-trips
 * accurately when the component is given an explicit size in headless mode.
 *
 * This complements Layer 1 (CoordinateTransformTest) which tests the math
 * in isolation; here we exercise the actual SlideGlass code paths.
 */
public class SlideGlassCoordinateIntegrationTest {

    /** Create a single-slide Praparat and size it manually for headless use. */
    private static SlideGlass makeSlide(int imgW, int imgH, int compW, int compH) {
        ShortProcessor sp = new ShortProcessor(imgW, imgH);
        ImagePlus imp = new ImagePlus("coord-test", sp);
        Praparat pp = new Praparat(imp, Color.DARK_GRAY, ViewMode.Normal, false);
        SlideGlass sg = pp.getCurrentSlide();
        // In headless mode the component has no natural size; set it explicitly.
        sg.setSize(compW, compH);
        if (sg.imageSpecimen != null) sg.imageSpecimen.setSize(compW, compH);
        return sg;
    }

    // -----------------------------------------------------------------------
    // Round-trip: screen → image → screen
    // -----------------------------------------------------------------------

    @Test
    public void offScreen_toScreen_roundTrip_center() throws Exception {
        // 128×128 image, 256×256 display panel (zoom=1, no rotation/flip)
        SlideGlass sg = makeSlide(128, 128, 256, 256);

        // Image center → screen; then screen → image: should recover original
        Point screen = sg.slideglassCoordinateFromOffScreen(64, 64);
        Point back   = sg.offScreenCoordinate(screen.x, screen.y);

        assertEquals("round-trip X", 64, back.x, 1.0);
        assertEquals("round-trip Y", 64, back.y, 1.0);
    }

    @Test
    public void offScreen_toScreen_roundTrip_corner() throws Exception {
        SlideGlass sg = makeSlide(128, 128, 256, 256);

        Point screen = sg.slideglassCoordinateFromOffScreen(0, 0);
        Point back   = sg.offScreenCoordinate(screen.x, screen.y);

        assertEquals("top-left X", 0, back.x, 1.0);
        assertEquals("top-left Y", 0, back.y, 1.0);
    }

    @Test
    public void offScreen_toScreen_roundTrip_arbitraryPoint() throws Exception {
        SlideGlass sg = makeSlide(256, 256, 512, 512);

        int imgX = 80, imgY = 120;
        Point screen = sg.slideglassCoordinateFromOffScreen(imgX, imgY);
        Point back   = sg.offScreenCoordinate(screen.x, screen.y);

        assertEquals("arbitrary X round-trip", imgX, back.x, 1.0);
        assertEquals("arbitrary Y round-trip", imgY, back.y, 1.0);
    }

    // -----------------------------------------------------------------------
    // getCurrentTransform() sanity checks
    // -----------------------------------------------------------------------

    @Test
    public void currentTransform_isNotNull() {
        ShortProcessor sp = new ShortProcessor(64, 64);
        ImagePlus imp = new ImagePlus("t", sp);
        Praparat pp = new Praparat(imp, Color.RED, ViewMode.Normal, false);
        SlideGlass sg = pp.getCurrentSlide();
        sg.setSize(128, 128);
        if (sg.imageSpecimen != null) sg.imageSpecimen.setSize(128, 128);

        AffineTransform at = sg.getCurrentTransform();
        assertNotNull("getCurrentTransform() must not return null", at);
    }

    @Test
    public void currentTransform_isInvertible() {
        ShortProcessor sp = new ShortProcessor(64, 64);
        ImagePlus imp = new ImagePlus("t", sp);
        Praparat pp = new Praparat(imp, Color.RED, ViewMode.Normal, false);
        SlideGlass sg = pp.getCurrentSlide();
        sg.setSize(128, 128);
        if (sg.imageSpecimen != null) sg.imageSpecimen.setSize(128, 128);

        AffineTransform at = sg.getCurrentTransform();
        double det = at.getDeterminant();
        assertNotEquals("transform determinant must not be zero (invertible)", 0.0, det, 1e-12);
    }

    @Test
    public void magnification_default_isOne() {
        ShortProcessor sp = new ShortProcessor(64, 64);
        ImagePlus imp = new ImagePlus("t", sp);
        Praparat pp = new Praparat(imp, Color.RED, ViewMode.Normal, false);
        SlideGlass sg = pp.getCurrentSlide();

        assertEquals("default magnification must be 1.0", 1.0, sg.getMagnification(), 1e-9);
    }
}
