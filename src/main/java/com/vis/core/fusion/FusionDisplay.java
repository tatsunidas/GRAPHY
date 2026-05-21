package com.vis.core.fusion;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.process.ImageProcessor;
import ij.process.LUT;
import com.vis.core.log.Log;

public class FusionDisplay {
	
	public enum FusionMode {
        FLATTEN_RGB,
        INTERACTIVE_OVERLAY
    }

    /**
     * @param foreground 前景画像 (マスク/マップ)
     * @param background 背景画像 (CT/MRI)
     * @param opacity 透過度
     * @param fgLUT 前景用LUT
     * @param mode フュージョンモード
     * @return 処理結果。INTERACTIVEの場合は背景画像にOverlayがセットされて返る。
     */
    public static ImagePlus executeFusion(ImagePlus foreground, ImagePlus background, 
                                          double opacity, LUT fgLUT, FusionMode mode) {
        if (foreground == null || background == null) return null;

        if (mode == FusionMode.FLATTEN_RGB) {
            // ★ 既存の createFusionImage のロジックをここに移植
            return createFlattenedFusion(foreground, background, opacity, fgLUT);
        } else {
            // ★ 新規: インタラクティブモード (Overlayの適用)
            return createInteractiveFusion(foreground, background, opacity, fgLUT);
        }
    }

    private static ImagePlus createInteractiveFusion(ImagePlus foreground, ImagePlus background, 
                                                     double opacity, LUT fgLUT) {
        // 既存のOverlayがあれば取得、なければ新規作成
        Overlay overlay = background.getOverlay();
        if (overlay == null) overlay = new Overlay();

        int slices = foreground.getNSlices();
        for (int i = 1; i <= slices; i++) {
            ij.process.ImageProcessor fgIp = foreground.getStack().getProcessor(i).duplicate();
            if (fgLUT != null) fgIp.setLut(fgLUT);

            ImageRoi imageRoi = new ImageRoi(0, 0, fgIp);
            imageRoi.setOpacity(opacity);
            
            // ImageRoiがどのスライスに表示されるべきかを指定（1-based index）
            imageRoi.setPosition(i); 
            overlay.add(imageRoi);
        }

        background.setOverlay(overlay);
        return background;
    }

    /**
     * 前景画像（マップやマスク）と背景画像（CT/MRI等）を指定した透過度でフュージョンします。
     * 両者のスライス枚数とサイズが一致していることが前提です。
     * * @param foreground 前景画像 (Radiomics Map or Aligned Mask)
     * @param background 背景画像 (Original Image)
     * @param opacity    透過度 (0.0: 透明 〜 1.0: 不透明)
     * @param fgLUT      前景に適用するカラーマップ (LUT)、不要な場合は null
     * @return RGBカラーのフュージョン済み ImagePlus
     */
    public static ImagePlus createFlattenedFusion(ImagePlus foreground, ImagePlus background, double opacity, LUT fgLUT) {
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