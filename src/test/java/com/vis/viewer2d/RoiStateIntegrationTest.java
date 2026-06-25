package com.vis.viewer2d;

import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.Color;
import java.util.ArrayList;

import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

import ij.ImagePlus;
import ij.process.ShortProcessor;

/**
 * Layer 2: ROI state integration tests.
 *
 * Verifies that ROIs can be added to a SlideGlass and are retrieved correctly.
 * Uses a synthetic ImagePlus so no external files are required.
 */
public class RoiStateIntegrationTest {

    private static Praparat makePraparat(int w, int h) {
        ShortProcessor sp = new ShortProcessor(w, h);
        ImagePlus imp = new ImagePlus("roi-test", sp);
        return new Praparat(imp, Color.GREEN, ViewMode.Normal, false);
    }

    // -----------------------------------------------------------------------
    // ROI add / retrieve
    // -----------------------------------------------------------------------

    @Test
    public void addRoiFromDB_appearsInGetRois() {
        Praparat pp = makePraparat(128, 128);
        SlideGlass sg = pp.getCurrentSlide();
        assertNotNull(sg);

        RoiObj roi = new RoiObj(10, 20, 30, 40, sg);
        sg.addRoiFromDB(roi);

        ArrayList<RoiObj> rois = sg.getRois();
        assertNotNull("getRois() must not be null after addRoiFromDB", rois);
        assertEquals("exactly one ROI should be present", 1, rois.size());
    }

    @Test
    public void multipleRoisPreserveOrder() {
        Praparat pp = makePraparat(128, 128);
        SlideGlass sg = pp.getCurrentSlide();

        RoiObj r1 = new RoiObj(0,  0, 10, 10, sg);
        RoiObj r2 = new RoiObj(20, 20, 10, 10, sg);
        RoiObj r3 = new RoiObj(50, 50, 10, 10, sg);

        sg.addRoiFromDB(r1);
        sg.addRoiFromDB(r2);
        sg.addRoiFromDB(r3);

        ArrayList<RoiObj> rois = sg.getRois();
        assertEquals("three ROIs must be present", 3, rois.size());
        // order of insertion must be preserved
        assertSame("first ROI", r1, rois.get(0));
        assertSame("second ROI", r2, rois.get(1));
        assertSame("third ROI", r3, rois.get(2));
    }

    @Test
    public void emptySlideGlasHasNoRois() {
        Praparat pp = makePraparat(64, 64);
        SlideGlass sg = pp.getCurrentSlide();

        ArrayList<RoiObj> rois = sg.getRois();
        assertNotNull("getRois() must not return null on fresh SlideGlass", rois);
        assertTrue("fresh SlideGlass must have no ROIs", rois.isEmpty());
    }

    @Test
    public void roiRetainsCoordinates() {
        Praparat pp = makePraparat(128, 128);
        SlideGlass sg = pp.getCurrentSlide();

        double x = 15, y = 25, w = 40, h = 50;
        RoiObj roi = new RoiObj(x, y, w, h, sg);
        sg.addRoiFromDB(roi);

        RoiObj stored = sg.getRois().get(0);
        assertEquals("ROI x", x, stored.getXBase(), 0.001);
        assertEquals("ROI y", y, stored.getYBase(), 0.001);
        assertEquals("ROI width",  w, stored.getFloatWidth(),  0.001);
        assertEquals("ROI height", h, stored.getFloatHeight(), 0.001);
    }

    @Test
    public void addRoiFromDB_onOneSlide_doesNotLeakToAnother() {
        // Each single-frame Praparat has its own isolated slide
        Praparat pp1 = makePraparat(64, 64);
        Praparat pp2 = makePraparat(64, 64);

        SlideGlass sg1 = pp1.getCurrentSlide();
        SlideGlass sg2 = pp2.getCurrentSlide();

        sg1.addRoiFromDB(new RoiObj(5, 5, 10, 10, sg1));

        assertTrue("pp2 slide should have no ROIs", sg2.getRois().isEmpty());
        assertEquals("pp1 slide should have 1 ROI", 1, sg1.getRois().size());
    }
}
