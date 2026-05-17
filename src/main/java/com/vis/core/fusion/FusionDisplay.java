package com.vis.core.fusion;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageRoi;
import ij.process.ImageProcessor;
import ij.process.LUT;
import com.vis.core.log.Log;

public class FusionDisplay {

    /**
     * 前景画像（マップやマスク）と背景画像（CT/MRI等）を指定した透過度でフュージョンします。
     * 両者のスライス枚数とサイズが一致していることが前提です。
     * * @param foreground 前景画像 (Radiomics Map or Aligned Mask)
     * @param background 背景画像 (Original Image)
     * @param opacity    透過度 (0.0: 透明 〜 1.0: 不透明)
     * @param fgLUT      前景に適用するカラーマップ (LUT)、不要な場合は null
     * @return RGBカラーのフュージョン済み ImagePlus
     */
    public static ImagePlus createFusionImage(ImagePlus foreground, ImagePlus background, double opacity, LUT fgLUT) {
        if (foreground == null || background == null) return null;

        if (foreground.getNSlices() != background.getNSlices() || 
            foreground.getWidth() != background.getWidth() || 
            foreground.getHeight() != background.getHeight()) {
            Log.logger.warning("Fusion Error: 画像のサイズまたはスライス数が一致しません。事前に ImagePairingEngine でアライメントしてください。");
            return null;
        }

        int slices = foreground.getNSlices();
        ImageStack stack = new ImageStack(background.getWidth(), background.getHeight());

        for (int i = 1; i <= slices; i++) {
            ImageProcessor fgIp = foreground.getStack().getProcessor(i).duplicate();
            if (fgLUT != null) {
                fgIp.setLut(fgLUT);
            }

            // ImageRoiを利用して透過度を設定
            ImageRoi overlayRoi = new ImageRoi(0, 0, fgIp);
            overlayRoi.setOpacity(opacity);

            background.setSlice(i); // 背景の対象スライスに移動
            ImagePlus flattenBase = new ImagePlus(i + "", background.getProcessor().duplicate());
            flattenBase.setRoi(overlayRoi);
            
            // "flatten" (焼き付け) してRGB画像化
            flattenBase = flattenBase.flatten();
            
            stack.addSlice(flattenBase.getProcessor());
            background.deleteRoi();
        }

        return new ImagePlus("Fusion_Image", stack);
    }
}