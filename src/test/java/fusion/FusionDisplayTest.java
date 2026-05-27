package fusion;

import ij.gui.ImageRoi;
import ij.process.FloatProcessor;
import ij.process.ByteProcessor;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;

import org.junit.BeforeClass;

public class FusionDisplayTest {
	
    @BeforeClass
    public static void setUpHeadless() {
        // ImageJやSwingがHeadless環境でエラーを出さないためのプロパティ設定
        System.setProperty("java.awt.headless", "false");
        
        // 【修正1】dcm4cheのOpenCVネイティブライブラリ読み込みエラーを回避するため、
        // テスト環境のImageIOレジストリから該当プラグインを動的に除外する
        IIORegistry registry = IIORegistry.getDefaultInstance();
        Iterator<ImageReaderSpi> providers = registry.getServiceProviders(ImageReaderSpi.class, false);
        List<ImageReaderSpi> toRemove = new ArrayList<>();
        while (providers.hasNext()) {
            ImageReaderSpi spi = providers.next();
            if (spi.getClass().getName().contains("dcm4che3.opencv")) {
                toRemove.add(spi);
            }
        }
        for (ImageReaderSpi spi : toRemove) {
            registry.deregisterServiceProvider(spi);
        }
    }

    @Test
    public void testFusionPixelScalingAndTransparency() {
        int width = 3;
        int height = 1;
        // 3つのテストピクセル: [下限以下(空気), 中間(軟部), 上限以上(骨)]
        float[] rawPixels = {-1000f, 40f, 1500f};
        FloatProcessor fgProcessor = new FloatProcessor(width, height, rawPixels);

        // UI設定値（WL:40, WW:400 -> Min:-160, Max:240）
        double fgMin = -160.0;
        double fgMax = 240.0;

        // 1. スケーリング
        fgProcessor.setMinAndMax(fgMin, fgMax);
        ByteProcessor byteProcessor = (ByteProcessor) fgProcessor.convertToByte(true);
        byte[] pixels8 = (byte[]) byteProcessor.getPixels();

        // [確認] -1000は0に、40は中間値(約127)に、1500は255になっているか
        assertEquals(0, pixels8[0] & 0xff);
        assertTrue("中間値が0より大きく255未満であること", (pixels8[1] & 0xff) > 0 && (pixels8[1] & 0xff) < 255);
        assertEquals(255, pixels8[2] & 0xff);

        // 2. 手動RGBマッピングと黒塗り（ゼロ透過用）
        int[] pixelsRGB = new int[width * height];
        int transparentCount = 0;
        
        for (int i = 0; i < pixels8.length; i++) {
            int v = pixels8[i] & 0xff;
            if (v == 0) {
                pixelsRGB[i] = 0; // 完全な黒
                transparentCount++;
            } else {
                pixelsRGB[i] = (0xff << 24) | (v << 16) | (v << 8) | v; // グレースケールRGB
            }
        }

        // [確認] 下限以下のピクセルだけがカウントされ、RGBが0になっているか
        assertEquals("透過されるべきピクセル数が一致しません", 1, transparentCount);
        assertEquals("下限以下のピクセルが黒(0)になっていません", 0, pixelsRGB[0]);
        assertNotEquals("中間値のピクセルが黒になっています", 0, pixelsRGB[1]);

        // 3. ImageRoiのOpacityテスト
        ij.process.ColorProcessor rgbProcessor = new ij.process.ColorProcessor(width, height, pixelsRGB);
        double targetOpacity = 0.6;
        ImageRoi roi = new ImageRoi(0, 0, rgbProcessor);
        roi.setZeroTransparent(true);
        roi.setOpacity(targetOpacity);

        assertTrue("ゼロ透過フラグがセットされていません", roi.getZeroTransparent());
        assertEquals("Opacityが正しくセットされていません", targetOpacity, roi.getOpacity(), 0.01);
    }
}