package com.vis.imageio;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.measure.Calibration;
import ij.process.ImageProcessor;

public class ImageRoiFusionTest {

    public static void main(String[] args) {
        // ※お手元の適当なDICOMやTIF画像のパスに書き換えてください
        String bgPath = "/home/tatsunidas/graphy_sample_images/HCC_001/C-A-P/49.dcm";
        String fgPath = "/home/tatsunidas/graphy_sample_images/HCC_001/C-A-P/76.dcm";

        // 1. 画像の読み込み
        ImagePlus bgImp = IJ.openImage(bgPath);
        ImagePlus fgImp = IJ.openImage(fgPath);

        if (bgImp == null || fgImp == null) {
            System.out.println("画像の読み込みに失敗しました。パスを確認してください。");
            return;
        }

        // 2. 前景プロセッサの抽出とコントラスト（Window/Level）の設定
        ImageProcessor fgIp = fgImp.getProcessor().duplicate();
        Calibration cal = fgImp.getCalibration();
        
        // ★検証ポイント1: ここで任意のRAW値の範囲を設定します
        // （CTの場合、骨だけを残すようなMin/Max値を設定してみてください）
     // ★検証ポイント1: UIで設定するような「HU値」を定義（例：骨を強調する 500〜1500 HU）
        double targetHuMin = -175.0;
        double targetHuMax = 225.0;

        double rawMin = targetHuMin;
        double rawMax = targetHuMax;

        // ★検証ポイント2: キャリブレーションが存在する場合、HU値をRAW値に翻訳（逆変換）する
        if (cal != null && cal.calibrated()) {
            rawMin = cal.getRawValue(targetHuMin);
            rawMax = cal.getRawValue(targetHuMax);
            System.out.println("キャリブレーション変換: HU[" + targetHuMin + " 〜 " + targetHuMax + "] -> RAW[" + rawMin + " 〜 " + rawMax + "]");
        }

        // ★検証ポイント3: プロセッサには翻訳済みの「RAW値」をセットする
        fgIp.setMinAndMax(rawMin, rawMax);

        // 3. 8-bitに変換してコントラストを確定させる
        fgIp = fgIp.convertToByte(true);

        // ★検証ポイント2: カラーマップ（LUT）の適用
        // ImagePlusを経由してImageJ標準の "Fire" や "Spectrum" などを適用します
        ImagePlus tempImp = new ImagePlus("temp", fgIp);
        IJ.run(tempImp, "Fire", ""); 
        fgIp = tempImp.getProcessor();

        // 4. 透過処理の準備（RGB変換と黒塗り）
        ij.process.ImageProcessor rgbIp = fgIp.convertToRGB();
        byte[] pixels8 = (byte[]) fgIp.getPixels();
        int[] pixelsRGB = (int[]) rgbIp.getPixels();

        // ウインドウ下限（myMin）以下になって 0 になったピクセルを、透過用の完全な黒(0)にする
        for (int i = 0; i < pixels8.length; i++) {
            if (pixels8[i] == 0) {
                pixelsRGB[i] = 0; 
            }
        }

        // 5. ImageRoiの作成とOverlayへの追加
        ImageRoi roi = new ImageRoi(0, 0, rgbIp);
        roi.setZeroTransparent(true); // 完全な黒を透明にする
        roi.setOpacity(0.7); // 60%の不透明度でブレンド

        Overlay overlay = new Overlay();
        overlay.add(roi);
        bgImp.setOverlay(overlay);

        // 6. 結果の表示
        bgImp.show();
        
        System.out.println("テスト完了: 画像が表示されました。");
    }
}
