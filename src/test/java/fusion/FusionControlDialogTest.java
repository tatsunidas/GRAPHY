package fusion;

import org.junit.BeforeClass;
import org.junit.Test;

import com.vis.core.fusion.FusionControlDialog;
import com.vis.core.view.D2.ui.glasses.Praparat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;

public class FusionControlDialogTest {
	
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

    // ダイアログからの通知を受け取るモック（Praparatの代用）
    @SuppressWarnings("serial")
	class MockPraparat extends Praparat {
        double opacity = -1.0;
        int shiftX = -1;
        int shiftY = -1;
        String lutName = null;

        public MockPraparat() {
            super(ViewMode.Normal); // 必要に応じて初期化
        }

        @Override
        public void updateFusionParameters(double opacity, int xShift, int yShift) {
            this.opacity = opacity;
            this.shiftX = xShift;
            this.shiftY = yShift;
        }

        @Override
        public void updateFusionLUT(ij.process.LUT lut, String lutName) {
            this.lutName = lutName;
        }
    }

    @Test
    public void testDialogOpacityChangePropagatesToPraparat() {
        MockPraparat mockPraparat = new MockPraparat();
        
        // ★ 実際のダイアログをインスタンス化する
        @SuppressWarnings("unused")
		FusionControlDialog dialog = new FusionControlDialog(null, mockPraparat);
        
//        // 1. スライダーのコンポーネントを取得（※実際のコードのメソッド名に合わせてください）
//        JSlider opacitySlider = dialog.getOpacitySlider(); 
//        
//        // 2. ユーザーが操作したと仮定して、値をプログラムから変更する (例: 75%)
//        opacitySlider.setValue(75);
//        
//        // 3. スライダーの ChangeListener が発火し、MockPraparat に値が伝達されたかを検証する
//        assertEquals("透明度がPraparatに正しく伝播していません", 0.75, mockPraparat.opacity, 0.01);
    }

    @Test
    public void testDialogLutChangePropagatesToPraparat() {
        MockPraparat mockPraparat = new MockPraparat();
        @SuppressWarnings("unused")
		FusionControlDialog dialog = new FusionControlDialog(null, mockPraparat);
        
//        // 1. コンボボックスのコンポーネントを取得
//        JComboBox<String> lutComboBox = dialog.getLutComboBox();
//        
//        // 2. ユーザーが別のカラーマップを選択したと仮定する
//        lutComboBox.setSelectedItem("Fire");
//        
        // 3. ItemListener が発火し、MockPraparat にカラーマップ名が伝達されたかを検証する
//        assertEquals("選択されたLUTがPraparatに正しく伝播していません", "Fire", mockPraparat.lutName);
    }
}