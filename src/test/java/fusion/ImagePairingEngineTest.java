package fusion;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import org.junit.Test;

import com.vis.core.fusion.ImagePairingEngine;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;

import org.junit.BeforeClass;

public class ImagePairingEngineTest {
	
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

    // モック画像の生成ヘルパー
    private ImagePlus createMockVolume(String title, int slices, double zSpacing, String modality) {
        ImageStack stack = new ImageStack(10, 10);
        for (int i = 1; i <= slices; i++) {
            ByteProcessor ip = new ByteProcessor(10, 10);
            ip.setValue(100);
            ip.fill();
            
            // 擬似的なDICOMメタデータをラベルに仕込む
            double zPos = i * zSpacing;
            String label = "0008,0060: " + modality + "\n" +
                           "0020,0032: 0.0\\0.0\\" + zPos + "\n" +
                           "0020,0037: 1.0\\0.0\\0.0\\0.0\\1.0\\0.0\n" +
                           "0008,0018: SOP_UID_" + i + "\n";
            stack.addSlice(label, ip);
        }
        return new ImagePlus(title, stack);
    }

    @Test
    public void testAlignVolumeStatic() {
        // 背景: 10スライス, Z間隔 2.0
        ImagePlus bgImp = createMockVolume("BG_CT", 10, 2.0, "CT");
        
        // 前景(CT): 5スライス, Z間隔 4.0 (範囲はBGと重なる)
        ImagePlus fgImp = createMockVolume("FG_PET", 5, 4.0, "PT");

        // 実際の Engine の実行（※環境によってはGDicomToolsのモック化が必要です）
         ImagePlus alignedImp = ImagePairingEngine.alignVolumeStatic(fgImp, bgImp);
        
        assertNotNull("再構成された画像がnullです", alignedImp);
        assertEquals("スライス数が背景と一致しません", bgImp.getStackSize(), alignedImp.getStackSize());
        assertEquals("画像幅が背景と一致しません", bgImp.getWidth(), alignedImp.getWidth());
        
        // Min/Max プロパティのバケツリレーが維持されているか
        fgImp.setProperty("VisRawMin", 100.0);
        ImagePairingEngine.alignVolumeStatic(fgImp, bgImp);
    }
}
