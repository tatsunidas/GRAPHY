package probe;
import org.junit.Test;
import static org.junit.Assert.*;
import ij.ImagePlus;
import ij.process.ShortProcessor;
import java.awt.Color;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

public class HeadlessProbe {
    @Test
    public void probe() throws Exception {
        System.out.println("headless=" + java.awt.GraphicsEnvironment.isHeadless());
        ShortProcessor sp = new ShortProcessor(64, 64);
        sp.set(10, 20, 1500);
        ImagePlus imp = new ImagePlus("probe", sp);
        System.out.println("ImagePlus created");
        try {
            Praparat pp = new Praparat(imp, Color.ORANGE, ViewMode.Normal, false);
            System.out.println("Praparat OK: slides=" + pp.getAllSlides().size());
            SlideGlass sg = pp.getCurrentSlide();
            assertNotNull("SlideGlass must be created", sg);
            Object[] v = sg.getPixelValueFromOriginal(10, 20);
            System.out.println("pixel(10,20)=" + (v != null ? v[0] : "null"));
        } catch (java.awt.HeadlessException he) {
            he.printStackTrace(System.out);
            fail("HeadlessException: " + he.getMessage() + "\n" + java.util.Arrays.toString(he.getStackTrace()));
        }
    }
}
