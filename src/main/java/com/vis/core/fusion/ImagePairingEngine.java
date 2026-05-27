/**
 * Copyright: Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.fusion;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.joml.Vector3d;

import com.vis.core.log.Log;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;

/**
 * This class is responsible for pairing foreground images for fusion.
 * For DICOM-SEG, it prioritizes pairing based on attribute information.
 * For standard image stacks, it reconstructs the signal at each voxel coordinate of the background image using IOP and IPP.
 * If pairing via attributes or reconstruction using real-space coordinates fails, it returns the foreground image as-is.
 * 
 * @author tatsunidas
 */
public class ImagePairingEngine {

    private static class AlignResult {
        ImagePlus image;
        int validCount;
        int totalCount;
        
        AlignResult(ImagePlus image, int validCount, int totalCount) {
            this.image = image;
            this.validCount = validCount;
            this.totalCount = totalCount;
        }
        
        boolean isBlankRateOver(double threshold) {
            if (totalCount == 0) return true;
            double blankRate = 1.0 - ((double) validCount / totalCount);
            return blankRate >= threshold;
        }
    }

    public static ImagePlus alignVolumeStatic(ImagePlus fgImp, ImagePlus bgImp) {
        if (fgImp == null || bgImp == null) return null;
        Log.logger.info("[Engine] Advanced Alignment started...");

        String fgModality = extractTagFromLabel(fgImp.getStack().getSliceLabel(1), "0008,0060");
        double[] bgIop = GDicomTools.getImageOrientationPatient(bgImp, 1);
        double[] fgIop = GDicomTools.getImageOrientationPatient(fgImp, 1);
        double[] fgIpp = GDicomTools.getImagePositionPatient(fgImp, 1);

        AlignResult result = null;

        if (fgModality == null || bgIop == null || fgIop == null || fgIpp == null) {
            Log.logger.warning("[Engine] Missing spatial metadata or Modality. Fallback to Simple Overlay.");
            result = buildStackBySimpleOverlay(fgImp, bgImp);
        } else {
            double nx = bgIop[1] * bgIop[5] - bgIop[2] * bgIop[4];
            double ny = bgIop[2] * bgIop[3] - bgIop[0] * bgIop[5];
            double nz = bgIop[0] * bgIop[4] - bgIop[1] * bgIop[3];

            boolean isSparseData = "SEG".equals(fgModality);
            
            Log.logger.fine("Foreground modality is : "+fgModality);

            Log.logger.info("[Engine] Phase 1: Try IPP Matching (Z-Axis)...");
            result = buildStackByIppMatching(fgImp, bgImp, nx, ny, nz, 1.0);

            if (result.isBlankRateOver(0.90)) {
                Log.logger.warning("[Engine] IPP Matching failed (>=90% blank). Fallbacking...");

                if (isSparseData) {
                    Log.logger.info("[Engine] Phase 2 (SEG): Try SOP Matching...");
                    result = buildStackBySopMatching(fgImp, bgImp);
                    
                    if (result.isBlankRateOver(0.90)) {
                        Log.logger.warning("[Engine] SOP Matching failed. Fallback to Simple Overlay...");
                        result = buildStackBySimpleOverlay(fgImp, bgImp);
                    }
                } else {
                    Log.logger.info("[Engine] Phase 2 (Dense): 3D Nearest Neighbor Resampling...");
                    result = reconstructBy3DResampling(fgImp, bgImp);
                    
                    if (result.isBlankRateOver(0.90)) {
                        Log.logger.warning("[Engine] 3D Resampling failed (Still >=90% blank).");
                        if (askUserForSimpleOverlay()) {
                            result = buildStackBySimpleOverlay(fgImp, bgImp);
                        } else {
                            return null;
                        }
                    }
                }
            }
        }

        ImagePlus finalImp = result.image;
        if (finalImp != null) {
            if (fgImp.getCalibration() != null) finalImp.setCalibration(fgImp.getCalibration().copy());
            finalImp.setProperty("Info", fgImp.getInfoProperty());
            if (fgImp.getLuts() != null && fgImp.getLuts().length > 0) {
                finalImp.setLut(fgImp.getLuts()[0]);
            }
            // 標準の表示レンジ引き継ぎ
            finalImp.setDisplayRange(fgImp.getDisplayRangeMin(), fgImp.getDisplayRangeMax());
        }
        
        Log.logger.info("[Engine] Alignment completed successfully.");
        return finalImp;
    }

    private static AlignResult buildStackByIppMatching(ImagePlus fgImp, ImagePlus bgImp, double nx, double ny, double nz, double threshold) {
        int bgSlices = bgImp.getStackSize();
        int fgSlices = fgImp.getStackSize();
        int fgW = fgImp.getWidth();
        int fgH = fgImp.getHeight();
        ImageStack matchedStack = new ImageStack(fgW, fgH);
        int validCount = 0;

        for (int z = 1; z <= bgSlices; z++) {
            ij.process.ImageProcessor matchedIp = null;
            String updatedFgLabel = bgImp.getStack().getSliceLabel(z);

            double[] bgIpp = GDicomTools.getImagePositionPatient(bgImp, z);
            if (bgIpp != null) {
                double bgZPos = bgIpp[0]*nx + bgIpp[1]*ny + bgIpp[2]*nz;
                int fgZIndex = findIndexByIPP(fgImp, bgZPos, nx, ny, nz, threshold);

                if (fgZIndex >= 1 && fgZIndex <= fgSlices) {
                    matchedIp = fgImp.getStack().getProcessor(fgZIndex).duplicate();
                    updatedFgLabel = fgImp.getStack().getSliceLabel(fgZIndex);
                    validCount++;
                }
            }

            if (matchedIp == null) {
                matchedIp = createBlankProcessor(fgImp.getBitDepth(), fgW, fgH);
            }
            matchedStack.addSlice(updatedFgLabel, matchedIp);
        }
        return new AlignResult(new ImagePlus("Aligned_IPP", matchedStack), validCount, bgSlices);
    }

    private static AlignResult buildStackBySopMatching(ImagePlus fgImp, ImagePlus bgImp) {
        int bgSlices = bgImp.getStackSize();
        int fgSlices = fgImp.getStackSize();
        int fgW = fgImp.getWidth();
        int fgH = fgImp.getHeight();
        ImageStack matchedStack = new ImageStack(fgW, fgH);
        int validCount = 0;

        for (int z = 1; z <= bgSlices; z++) {
            ij.process.ImageProcessor matchedIp = null;
            String updatedFgLabel = bgImp.getStack().getSliceLabel(z);

            String bgSopUid = extractTagFromLabel(bgImp.getStack().getSliceLabel(z), "0008,0018");
            int fgZIndex = findIndexBySOP(fgImp, bgSopUid);

            if (fgZIndex >= 1 && fgZIndex <= fgSlices) {
                matchedIp = fgImp.getStack().getProcessor(fgZIndex).duplicate();
                updatedFgLabel = fgImp.getStack().getSliceLabel(fgZIndex);
                validCount++;
            }

            if (matchedIp == null) {
                matchedIp = createBlankProcessor(fgImp.getBitDepth(), fgW, fgH);
            }
            matchedStack.addSlice(updatedFgLabel, matchedIp);
        }
        return new AlignResult(new ImagePlus("Aligned_SOP", matchedStack), validCount, bgSlices);
    }

    /**
     * 【極めて重要】実空間からの逆算による 3D Voxel Nearest Neighbor リサンプリング
     */
    private static AlignResult reconstructBy3DResampling(ImagePlus fgImp, ImagePlus bgImp) {
        int bgW = bgImp.getWidth();
        int bgH = bgImp.getHeight();
        int bgSlices = bgImp.getStackSize();
        
        int fgW = fgImp.getWidth();
        int fgH = fgImp.getHeight();
        int fgSlices = fgImp.getStackSize();
        
        ImageStack matchedStack = new ImageStack(bgW, bgH); 
        
        // 前景(Reference)の空間メタデータ
        double[] fgIop = GDicomTools.getImageOrientationPatient(fgImp, 1);
        double[] fgIpp1 = GDicomTools.getImagePositionPatient(fgImp, 1);
        if (fgIop == null || fgIpp1 == null) return new AlignResult(null, 0, bgSlices);
        
        double fgPx = 1.0, fgPy = 1.0, fgPz = 1.0;
        if (fgImp.getCalibration() != null) {
            fgPx = fgImp.getCalibration().pixelWidth;
            fgPy = fgImp.getCalibration().pixelHeight;
        }
        
        Vector3d fRr = new Vector3d(fgIop[0], fgIop[1], fgIop[2]);
        Vector3d fRc = new Vector3d(fgIop[3], fgIop[4], fgIop[5]);
        Vector3d fRs = new Vector3d();
        
        // ★修正: ImageJの不確かなpixelDepthに頼らず、IPPから「本物のZ軸間隔」を計算する
        if (fgSlices > 1) {
            double[] fgIppN = GDicomTools.getImagePositionPatient(fgImp, fgSlices);
            if (fgIppN != null) {
                Vector3d start = new Vector3d(fgIpp1[0], fgIpp1[1], fgIpp1[2]);
                Vector3d end = new Vector3d(fgIppN[0], fgIppN[1], fgIppN[2]);
                fRs = new Vector3d(end).sub(start).normalize();
                fgPz = end.distance(start) / (fgSlices - 1); // 本物のZ間隔
            } else {
                fRr.cross(fRc, fRs).normalize();
            }
        } else {
            fRr.cross(fRc, fRs).normalize();
        }
        if (fgPz <= 0) fgPz = 1.0;
        
        Vector3d fIpp = new Vector3d(fgIpp1[0], fgIpp1[1], fgIpp1[2]);
        
        // 背景(Target)の空間メタデータ
        double[] bgIop = GDicomTools.getImageOrientationPatient(bgImp, 1);
        double[] bgIpp1 = GDicomTools.getImagePositionPatient(bgImp, 1);
        if (bgIop == null || bgIpp1 == null) return new AlignResult(null, 0, bgSlices);
        Vector3d bRr = new Vector3d(bgIop[0], bgIop[1], bgIop[2]);
        Vector3d bRc = new Vector3d(bgIop[3], bgIop[4], bgIop[5]);
        
        double bgPx = 1.0, bgPy = 1.0;
        if (bgImp.getCalibration() != null) {
            bgPx = bgImp.getCalibration().pixelWidth;
            bgPy = bgImp.getCalibration().pixelHeight;
        }

        // ==========================================
        // ★検証ログ: 計算のコアとなるベクトルの出力
        // ==========================================
        Log.logger.info("=== 3D Resampling Debug Info ===");
        Log.logger.info(String.format("FG - Size:%dx%dx%d, Spacing(x,y,z):%.3f, %.3f, %.3f", fgW, fgH, fgSlices, fgPx, fgPy, fgPz));
        Log.logger.info(String.format("FG - IPP1:%.2f, %.2f, %.2f", fIpp.x, fIpp.y, fIpp.z));
        Log.logger.info(String.format("FG - Row(%.2f,%.2f,%.2f) Col(%.2f,%.2f,%.2f) Norm(%.2f,%.2f,%.2f)", fRr.x,fRr.y,fRr.z, fRc.x,fRc.y,fRc.z, fRs.x,fRs.y,fRs.z));
        
        Log.logger.info(String.format("BG - Size:%dx%dx%d, Spacing(x,y):%.3f, %.3f", bgW, bgH, bgSlices, bgPx, bgPy));
        Log.logger.info(String.format("BG - IPP1:%.2f, %.2f, %.2f", bgIpp1[0], bgIpp1[1], bgIpp1[2]));
        Log.logger.info(String.format("BG - Row(%.2f,%.2f,%.2f) Col(%.2f,%.2f,%.2f)", bRr.x,bRr.y,bRr.z, bRc.x,bRc.y,bRc.z));
        
        int bitDepth = fgImp.getBitDepth();
        int validCount = 0;
        
        for (int z = 1; z <= bgSlices; z++) {
            double[] bgIppArr = GDicomTools.getImagePositionPatient(bgImp, z);
            ij.process.ImageProcessor ip = createBlankProcessor(bitDepth, bgW, bgH);
            boolean hasValidPixel = false;
            
            if (bgIppArr != null) {
                Vector3d bIpp = new Vector3d(bgIppArr[0], bgIppArr[1], bgIppArr[2]);
                
                // ★検証ログ: 中心スライスの中心ピクセルのマッピングテスト
                if (z == bgSlices / 2) {
                    double cx = bIpp.x + bRc.x * (bgH/2.0) * bgPy + bRr.x * (bgW/2.0) * bgPx;
                    double cy = bIpp.y + bRc.y * (bgH/2.0) * bgPy + bRr.y * (bgW/2.0) * bgPx;
                    double cz = bIpp.z + bRc.z * (bgH/2.0) * bgPy + bRr.z * (bgW/2.0) * bgPx;
                    
                    double dx = cx - fIpp.x; double dy = cy - fIpp.y; double dz = cz - fIpp.z;
                    double u = (dx * fRr.x + dy * fRr.y + dz * fRr.z) / fgPx;
                    double v = (dx * fRc.x + dy * fRc.y + dz * fRc.z) / fgPy;
                    double w = (dx * fRs.x + dy * fRs.y + dz * fRs.z) / fgPz;
                    
                    Log.logger.info(String.format("TEST PROJECTION (Center) -> BG(z=%d) Absolute:(%.1f, %.1f, %.1f) mapped to FG Pixel:(%.1f, %.1f, %.1f)", z, cx, cy, cz, u, v, w));
                }
                
                for (int y = 0; y < bgH; y++) {
                    double offY = y * bgPy;
                    double baseYY = bIpp.y + bRc.y * offY;
                    double baseYX = bIpp.x + bRc.x * offY;
                    double baseYZ = bIpp.z + bRc.z * offY;
                    
                    for (int x = 0; x < bgW; x++) {
                        double offX = x * bgPx;
                        double px = baseYX + bRr.x * offX;
                        double py = baseYY + bRr.y * offX;
                        double pz = baseYZ + bRr.z * offX;
                        
                        double dx = px - fIpp.x;
                        double dy = py - fIpp.y;
                        double dz = pz - fIpp.z;
                        
                        double u = (dx * fRr.x + dy * fRr.y + dz * fRr.z) / fgPx;
                        double v = (dx * fRc.x + dy * fRc.y + dz * fRc.z) / fgPy;
                        double w = (dx * fRs.x + dy * fRs.y + dz * fRs.z) / fgPz;
                        
                        int fu = (int) Math.round(u);
                        int fv = (int) Math.round(v);
                        int fw = (int) Math.round(w) + 1; 
                        
                        if (fu >= 0 && fu < fgW && fv >= 0 && fv < fgH && fw >= 1 && fw <= fgSlices) {
                            if (bitDepth == 8 || bitDepth == 24) {
                                int val = fgImp.getStack().getProcessor(fw).get(fu, fv);
                                ip.set(x, y, val);
                                if (val != 0) hasValidPixel = true;
                            } else if (bitDepth == 16) {
                                // 16bitの場合は生のint値を維持してコピーする
                                int val = fgImp.getStack().getProcessor(fw).get(fu, fv);
                                ip.set(x, y, val);
                                if (val != 0) hasValidPixel = true;
                            } else {
                                float val = fgImp.getStack().getProcessor(fw).getf(fu, fv);
                                ip.setf(x, y, val);
                                if (val != 0.0f) hasValidPixel = true;
                            }
                        }
                    }
                }
            }
            
            if (hasValidPixel) validCount++;
            matchedStack.addSlice(bgImp.getStack().getSliceLabel(z), ip);
        }
        
        Log.logger.info("=== 3D Resampling Finished. Valid Slices: " + validCount + "/" + bgSlices + " ===");
        ImagePlus res = new ImagePlus("Aligned_3DResampled", matchedStack);
        return new AlignResult(res, validCount, bgSlices);
    }

    private static AlignResult buildStackBySimpleOverlay(ImagePlus fgImp, ImagePlus bgImp) {
        int bgSlices = bgImp.getStackSize();
        int fgSlices = fgImp.getStackSize();
        int fgW = fgImp.getWidth();
        int fgH = fgImp.getHeight();
        ImageStack matchedStack = new ImageStack(fgW, fgH);

        for (int z = 1; z <= bgSlices; z++) {
            ij.process.ImageProcessor matchedIp;
            String label;
            
            if (z <= fgSlices) {
                matchedIp = fgImp.getStack().getProcessor(z).duplicate();
                label = fgImp.getStack().getSliceLabel(z);
            } else {
                matchedIp = createBlankProcessor(fgImp.getBitDepth(), fgW, fgH);
                label = bgImp.getStack().getSliceLabel(z);
            }
            matchedStack.addSlice(label, matchedIp);
        }
        ImagePlus res = new ImagePlus("Aligned_Simple", matchedStack);
        return new AlignResult(res, Math.min(bgSlices, fgSlices), bgSlices);
    }

    // -------------------------------------------------------------
    // ヘルパーメソッド群
    // -------------------------------------------------------------

    private static int findIndexBySOP(ImagePlus fgImp, String targetSopUid) {
        if (targetSopUid == null) return -1;
        int fgSlices = fgImp.getStackSize();
        for (int i = 1; i <= fgSlices; i++) {
            String label = fgImp.getStack().getSliceLabel(i);
            String refSopUid = extractTagFromLabel(label, "0008,1155"); 
            if (targetSopUid.equals(refSopUid)) return i;
        }
        return -1;
    }

    private static int findIndexByIPP(ImagePlus fgImp, double targetZPos, double nx, double ny, double nz, double threshold) {
        int bestZ = -1;
        double minDistance = Double.MAX_VALUE;
        int fgSlices = fgImp.getStackSize();
        
        for (int i = 1; i <= fgSlices; i++) {
            double[] fgIpp = GDicomTools.getImagePositionPatient(fgImp, i);
            if (fgIpp != null) {
                double fgZPos = fgIpp[0]*nx + fgIpp[1]*ny + fgIpp[2]*nz;
                double distance = Math.abs(fgZPos - targetZPos);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestZ = i;
                }
            }
        }
        return (minDistance <= threshold) ? bestZ : -1;
    }

    private static boolean askUserForSimpleOverlay() {
        boolean[] result = new boolean[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                int ans = JOptionPane.showConfirmDialog(
                    null, 
                    "The spatial coordinates (Bounding Box) do not overlap at all.\nDo you want to force fusion using Simple Overlay (Index-to-Index)?", 
                    "Fusion Warning", 
                    JOptionPane.YES_NO_OPTION, 
                    JOptionPane.WARNING_MESSAGE
                );
                result[0] = (ans == JOptionPane.YES_OPTION);
            });
        } catch (Exception e) {
            result[0] = false;
        }
        return result[0];
    }

    private static ij.process.ImageProcessor createBlankProcessor(int bitDepth, int w, int h) {
        if (bitDepth == 24) return new ij.process.ColorProcessor(w, h);
        if (bitDepth == 16) return new ij.process.ShortProcessor(w, h);
        if (bitDepth == 32) return new ij.process.FloatProcessor(w, h);
        return new ij.process.ByteProcessor(w, h);
    }

    private static String extractTagFromLabel(String label, String tag) {
        if (label == null || tag == null) return null;
        String[] lines = label.split("\n");
        for (String line : lines) {
            if (line.contains(tag)) {
                String[] parts = line.split(":", 2);
                if (parts.length > 1) return parts[1].trim();
            }
        }
        return null;
    }
}


//package com.vis.core.fusion;
//
//import java.util.concurrent.ConcurrentHashMap;
//
//import com.vis.core.log.Log;
//import com.vis.core.view.D2.ui.glasses.Praparat;
//import com.vis.core.view.D2.ui.glasses.SlideGlass;
//import com.vis.dicom.DicomObject;
//import com.vis.dicom.Tag;
//import com.vis.dicom.image.GDicomTools;
//
//import ij.ImagePlus;
//import ij.ImageStack;
//import ij.gui.ImageRoi;
//import ij.gui.Overlay;
//import ij.process.ByteProcessor;
//import ij.process.ImageProcessor;
//import ij.process.LUT;
//
///**
// * @author tatsunidas
// */
//public class ImagePairingEngine {
//	
//	/**
//     * 【デッドロック回避・独立エンジン版（SEGパディング対応）】
//     * 抽出済みのシングルスタック ImagePlus を受け取り、IPP/IOP に基づいて空間アライメントを行います。
//     * マスクが存在しないスライスには空きマス（パディング）を挿入します。
//     */
//    public static ImagePlus alignVolumeStatic(ImagePlus fgImp, ImagePlus bgImp) {
//        if (fgImp == null || bgImp == null) return null;
//
//        com.vis.core.log.Log.logger.info("[Engine] Alignment started...");
//
//        int bgSlices = bgImp.getStackSize(); 
//        ImageStack matchedStack = new ImageStack(fgImp.getWidth(), fgImp.getHeight());
//        
//        // 1. 背景の法線ベクトル（Z軸の進行方向）を最初のスライスから計算する
//        double[] baseIop = GDicomTools.getImageOrientationPatient(bgImp, 1);
//        double nx = 0, ny = 0, nz = 1; // フォールバック
//        if (baseIop != null && baseIop.length == 6) {
//            nx = baseIop[1] * baseIop[5] - baseIop[2] * baseIop[4];
//            ny = baseIop[2] * baseIop[3] - baseIop[0] * baseIop[5];
//            nz = baseIop[0] * baseIop[4] - baseIop[1] * baseIop[3];
//        }
//
//        for (int z = 1; z <= bgSlices; z++) {
//            double[] bgIpp = GDicomTools.getImagePositionPatient(bgImp, z);
//            
//            // 背景スライスの空間座標（法線ベクトル上の絶対位置）を正確に計算
//            double bgZPos = (bgIpp != null) ? (bgIpp[0] * nx + bgIpp[1] * ny + bgIpp[2] * nz) : z;
//
//            // 前景から合致するスライスを探す（法線ベクトルを渡す）
//            int bestMatchedFgIndex = findClosestSliceIndex(bgZPos, nx, ny, nz, fgImp);
//            
//            ij.process.ImageProcessor matchedIp;
//            String updatedFgLabel;
//            
//            if (bestMatchedFgIndex > 0) {
//                // 合致するマスクスライスがある場合は複製
//                matchedIp = fgImp.getStack().getProcessor(bestMatchedFgIndex).duplicate();
//                updatedFgLabel = fgImp.getStack().getSliceLabel(bestMatchedFgIndex);
//            } else {
//                // ★★★ 修正: 合致するマスクがない（空きマス）場合は、透明な黒プロセッサを生成してパディング ★★★
//                int bitDepth = fgImp.getBitDepth();
//                if (bitDepth == 24) {
//                    matchedIp = new ij.process.ColorProcessor(fgImp.getWidth(), fgImp.getHeight());
//                } else if (bitDepth == 16) {
//                    matchedIp = new ij.process.ShortProcessor(fgImp.getWidth(), fgImp.getHeight());
//                } else if (bitDepth == 32) {
//                    matchedIp = new ij.process.FloatProcessor(fgImp.getWidth(), fgImp.getHeight());
//                } else {
//                    matchedIp = new ij.process.ByteProcessor(fgImp.getWidth(), fgImp.getHeight());
//                }
//                updatedFgLabel = bgImp.getStack().getSliceLabel(z); // 背景のラベルを仮置き
//            }
//
//            matchedStack.addSlice(updatedFgLabel, matchedIp);
//        }
//
//        ImagePlus alignedImp = new ImagePlus("Static_Aligned_Foreground", matchedStack);
//        if (fgImp.getCalibration() != null) alignedImp.setCalibration(fgImp.getCalibration().copy());
//        alignedImp.setProperty("Info", fgImp.getInfoProperty());
//        
//        if (fgImp.getLuts() != null && fgImp.getLuts().length > 0) {
//            alignedImp.setLut(fgImp.getLuts()[0]);
//        }
//        
//        com.vis.core.log.Log.logger.info("[Engine] Alignment completed.");
//        return alignedImp;
//    }
//
//    /**
//     * 背景のZPosに対し、前景内で最も近いZ座標を持つスライスのインデックス(1-based)を返します。
//     * 閾値（1.0mm）を超える場合は、該当なし（-1）を返します。
//     */
//    private static int findClosestSliceIndex(double bgZPos, double nx, double ny, double nz, ImagePlus fgImp) {
//        int bestIndex = -1;
//        double minDistance = Double.MAX_VALUE;
//        double EPSILON = 1.0; // 許容するスライス間隔の誤差（1.0mm以内）
//        
//        int fgSlices = fgImp.getStackSize();
//        
//        for (int i = 1; i <= fgSlices; i++) {
//            double[] fgIpp = GDicomTools.getImagePositionPatient(fgImp, i);
//            if (fgIpp != null && fgIpp.length == 3) {
//                // 前景スライスの法線ベクトル上の絶対位置
//                double fgZPos = (fgIpp[0] * nx + fgIpp[1] * ny + fgIpp[2] * nz);
//                double distance = Math.abs(bgZPos - fgZPos); 
//                
//                if (distance < minDistance) {
//                    minDistance = distance;
//                    bestIndex = i; 
//                }
//            }
//        }
//        
//        // ★★★ 修正: 距離が閾値（EPSILON）以内ならそのインデックスを返し、遠すぎれば -1 を返す ★★★
//        if (minDistance <= EPSILON) {
//            return bestIndex;
//        }
//        
//        return -1; // 該当スライスなし（パディング対象）
//    }
//    
////	/**
////     * 【デッドロック回避・独立エンジン版】
////     * 抽出済みのシングルスタック ImagePlus を受け取り、IPP/IOP に基づいて空間アライメントを行います。
////     */
////    public static ImagePlus alignVolumeStatic(ImagePlus fgImp, ImagePlus bgImp) {
////        if (fgImp == null || bgImp == null) return null;
////
////        com.vis.core.log.Log.logger.info("[Engine] Alignment started...");
////
////        int bgSlices = bgImp.getStackSize(); 
////        ImageStack matchedStack = new ImageStack(fgImp.getWidth(), fgImp.getHeight());
////
////        for (int z = 1; z <= bgSlices; z++) {
////            double[] bgIpp = GDicomTools.getImagePositionPatient(bgImp, z);
////            double[] bgIop = GDicomTools.getImageOrientationPatient(bgImp, z);
////            
////            // ★引数から C, T の絞り込みを削除（すでに fgImp はシングルスタックのため）
////            int bestMatchedFgIndex = findClosestSliceIndex(bgIpp, fgImp);
////            
////			ij.process.ImageProcessor matchedIp = fgImp.getStack().getProcessor(bestMatchedFgIndex).duplicate();
////			GDicomTools.setDoubles(fgImp, bestMatchedFgIndex, Tag.ImageOrientationPatient, bgIop);
////			GDicomTools.setDoubles(fgImp, bestMatchedFgIndex, Tag.ImagePositionPatient, bgIpp);
////			String updatedFgLabel = fgImp.getStack().getSliceLabel(bestMatchedFgIndex);
////			matchedStack.addSlice(updatedFgLabel, matchedIp);
////		}
////
////        ImagePlus alignedImp = new ImagePlus("Static_Aligned_Foreground", matchedStack);
////        if (fgImp.getCalibration() != null) alignedImp.setCalibration(fgImp.getCalibration().copy());
////        alignedImp.setProperty("Info", fgImp.getInfoProperty());
////        
////        com.vis.core.log.Log.logger.info("[Engine] Alignment completed.");
////        return alignedImp;
////    }
//
//
//    /**
//     * マスク（SEG等）をオリジナル画像の空間座標（Z軸・スライス枚数）に完全に一致するように再構成します。
//     * マスクが存在しないスライスには空のByteProcessorが挿入されます。
//     *
//     * @param originalPrap オリジナル画像のPraparat
//     * @param maskPrap     マスク画像のPraparat (SEG)
//     * @param targetC      マスクの対象チャンネル (部位ごとに抽出する場合)
//     * @param targetT      マスクの対象タイムフレーム
//     * @return オリジナル画像とスライス数が完全に一致したSingle Stack マスクのImagePlus
//     */
//	public static ImagePlus alignMaskToOriginalSpace(Praparat originalPrap, int orgC, int orgT, Praparat maskPrap,
//			int targetC, int targetT) {
//		if (originalPrap == null || maskPrap == null)
//			return null;
//
//		int orgSlices = originalPrap.getImagePlus().getNSlices();
//		int width = originalPrap.getImageWidth();
//		int height = originalPrap.getImageHeight();
//
//		ImageStack alignedMaskStack = new ImageStack(width, height);
//		ConcurrentHashMap<Integer, SlideGlass> orgSlides = originalPrap.getAllSlides();
//		ConcurrentHashMap<Integer, SlideGlass> maskSlides = maskPrap.getAllSlides();
//		
//		String maskPatID = "";
//		String maskStudyUID = "";
//		String maskSeriesUID = "";
//
//		for (SlideGlass sg : maskSlides.values()) {
//			if (sg != null && sg.getHeader() != null) {
//				maskPatID = sg.getHeader().getString(Tag.PatientID, "");
//				maskStudyUID = sg.getHeader().getString(Tag.StudyInstanceUID, "");
//				maskSeriesUID = sg.getHeader().getString(Tag.SeriesInstanceUID, "");
//				break;
//			}
//		}
//
//		// 法線ベクトル（Z軸方向）を計算するためのIOP取得
//		// ★ 修正: ループで確実にヘッダーを持つスライドを探す（空きマス対応）
//		SlideGlass orgFirstSg = null;
//		for (SlideGlass sg : orgSlides.values()) {
//			if (sg != null && sg.getHeader() != null) {
//				orgFirstSg = sg;
//				break;
//			}
//		}
//		if (orgFirstSg == null) {
//			Log.logger.warning("All original slides are blank (no metadata). Cannot align mask.");
//			return null;
//		}
//		
//		int firstOrgFrameIdx = orgFirstSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
//		double[] iop = getSafeIOP(orgFirstSg.getHeader(), firstOrgFrameIdx);
//
//		double nx = 0, ny = 0, nz = 1; // Fallback
//		if (iop != null && iop.length == 6) {
//			nx = iop[1] * iop[5] - iop[2] * iop[4];
//			ny = iop[2] * iop[3] - iop[0] * iop[5];
//			nz = iop[0] * iop[4] - iop[1] * iop[3];
//		}
//
//		// オリジナル画像の全スライス(Z)を基準にループ
//		for (int z = 0; z < orgSlices; z++) {
//			int orgZCTIndex = originalPrap.calcZctIndex(new int[] { z, orgC, orgT });
//			SlideGlass orgSg = originalPrap.getSlideGlassAt(orgZCTIndex);
//
//			ImageProcessor matchedProcessor = null;
//			double[] orgIpp = null;
//			int orgInstNo = z + 1; // フォールバック用のインスタンス番号
//			String orgSopUid = null;
//
//			// ★ 修正: orgSg が null、またはメタデータを持たない空きマスの場合は例外を投げず安全にスキップ（パディング）する
//			if (orgSg != null && orgSg.getHeader() != null) {
//				orgSopUid = orgSg.getSOPInstanceUID();
//				int orgFrameIdx = orgSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
//				orgInstNo = orgSg.getHeader().getInt(Tag.InstanceNumber, z + 1);
//				
//				orgIpp = getSafeIPP(orgSg.getHeader(), orgFrameIdx);
//				double orgZPos = (orgIpp != null) ? (orgIpp[0] * nx + orgIpp[1] * ny + orgIpp[2] * nz) : z;
//
//				// 【ハイブリッド方式】マスク側から該当するスライスを探索
//				SlideGlass matchedMaskSg = findMatchingMaskSlide(maskSlides, maskPrap, orgSopUid, orgZPos, targetC,
//						targetT, nx, ny, nz);
//
//				if (matchedMaskSg != null && matchedMaskSg.getDicomImage().ensurePixelDataLoaded()) {
//					int maskFrameIdx = matchedMaskSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
//					matchedProcessor = matchedMaskSg.getDicomImage().getImageProcessor(maskFrameIdx);
//				}
//			} else {
//				// ★ ここが一番の修正ポイント！
//				// orgSg が存在しない（Empty）ということは、このZ位置のこのチャンネルには画像がないということ。
//				// つまり、合わせるべきマスクも「空」にするのが正しい挙動。
//				Log.logger.fine("Original slide at Z=" + z + " is empty. Skipping mask alignment for this slice.");
//			}
//
//			// マッチするマスクが無い、またはオリジナルが空の場合は、空のプロセッサを生成してパディング（穴埋め）
//			if (matchedProcessor == null) {
//				matchedProcessor = new ByteProcessor(width, height); // 全て0（黒）の空きマス
//			}
//			
//			StringBuilder sb = new StringBuilder();
//			sb.append("\n");//DICOMToolsの仕様に合わせる
//			
//			// 1. マスク画像の共通メタデータ (PatientID, Study, Series)
//			sb.append("0010,0020: ").append(maskPatID).append("\n");
//			sb.append("0020,000D: ").append(maskStudyUID).append("\n");
//			sb.append("0020,000E: ").append(maskSeriesUID).append("\n");
//			
//			// 2. 新規生成するUID
//			String newSopUid = com.vis.dicom.UIDUtils.createUID();
//			sb.append("0008,0018: ").append(newSopUid).append("\n");
//			
//			// 3. 【最重要】オリジナル画像の番号と空間座標に「完全に同期」させる
//			sb.append("0020,0013: ").append(orgInstNo).append("\n");
//			
//			if (orgIpp != null) {
//				sb.append("0020,0032: ")
//				  .append(orgIpp[0]).append("\\")
//				  .append(orgIpp[1]).append("\\")
//				  .append(orgIpp[2]).append("\n");
//			}
//			if (iop != null && iop.length == 6) {
//				sb.append("0020,0037: ")
//				  .append(iop[0]).append("\\").append(iop[1]).append("\\").append(iop[2]).append("\\")
//				  .append(iop[3]).append("\\").append(iop[4]).append("\\").append(iop[5]).append("\n");
//			}
//			String sliceLabel = sb.toString();
//			alignedMaskStack.addSlice(sliceLabel, matchedProcessor);
//		}
//		
//		ImagePlus alignedMaskImp = new ImagePlus("Aligned_Mask", alignedMaskStack);
//		
//		if (orgSlices == 1) {
//			String firstSliceMeta = alignedMaskStack.getSliceLabel(1);
//			if (firstSliceMeta != null) {
//				alignedMaskImp.setProperty("Info", firstSliceMeta);
//			}
//		}
//		
//		alignedMaskImp.setDimensions(1, alignedMaskStack.getSize(), 1);
//		alignedMaskImp.setOpenAsHyperStack(false);
//		alignedMaskImp.copyScale(originalPrap.getImagePlus(1,1)); // 安全のためチャンネル1・フレーム1からスケールを取る
//		return alignedMaskImp;
//	}
//	
//	/**
//     * SOPInstanceUIDをベースに背景(CT/MRI)と前景(Mask/SEG)を紐付け、
//     * 非破壊でインタラクティブに表示できるOverlayを生成します。
//     *
//     * @param bgPrap  背景画像のPraparat (CT/MRI)
//     * @param bgC     背景の対象チャンネル
//     * @param bgT     背景の対象タイムフレーム
//     * @param fgPrap  前景画像のPraparat (SEG/Map)
//     * @param fgC     前景の対象チャンネル
//     * @param fgT     前景の対象タイムフレーム
//     * @param opacity 初期透過度 (0.0〜1.0)
//     * @param fgLUT   前景に適用するカラーマップ (不要な場合はnull)
//     * @return 背景の各スライスに一対一で対応した ImageRoi を含む Overlay
//     */
//	public static Overlay createFusionOverlay(Praparat bgPrap, int bgC, int bgT, Praparat fgPrap, int fgC, int fgT,
//			double opacity, LUT fgLUT) {
//		if (bgPrap == null || fgPrap == null)
//			return null;
//
//		Overlay overlay = new Overlay();
//		int bgSlices = bgPrap.getImagePlus().getNSlices();
//
//		ConcurrentHashMap<Integer, SlideGlass> bgSlides = bgPrap.getAllSlides();
//		ConcurrentHashMap<Integer, SlideGlass> fgSlides = fgPrap.getAllSlides();
//
//		// 空間座標計算用の法線ベクトルを取得 (既存ロジックを流用)
//		SlideGlass bgFirstSg = null;
//		for (SlideGlass sg : bgSlides.values()) {
//			if (sg != null && sg.getHeader() != null) {
//				bgFirstSg = sg;
//				break;
//			}
//		}
//		if (bgFirstSg == null) {
//			Log.logger.warning("All background slides are blank. Cannot create overlay.");
//			return null;
//		}
//
//		int firstBgFrameIdx = bgFirstSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
//		double[] iop = getSafeIOP(bgFirstSg.getHeader(), firstBgFrameIdx);
//		double nx = 0, ny = 0, nz = 1;
//		if (iop != null && iop.length == 6) {
//			nx = iop[1] * iop[5] - iop[2] * iop[4];
//			ny = iop[2] * iop[3] - iop[0] * iop[5];
//			nz = iop[0] * iop[4] - iop[1] * iop[3];
//		}
//
//		// 背景画像の全スライス(Z)をループして、対応する前景を探す
//		for (int z = 0; z < bgSlices; z++) {
//			int bgZCTIndex = bgPrap.calcZctIndex(new int[] { z, bgC, bgT });
//			SlideGlass bgSg = bgPrap.getSlideGlassAt(bgZCTIndex);
//
//			if (bgSg != null && bgSg.getHeader() != null) {
//				String bgSopUid = bgSg.getSOPInstanceUID();
//				int bgFrameIdx = bgSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
//
//				double[] bgIpp = getSafeIPP(bgSg.getHeader(), bgFrameIdx);
//				double bgZPos = (bgIpp != null) ? (bgIpp[0] * nx + bgIpp[1] * ny + bgIpp[2] * nz) : z;
//
//				// 既存のハイブリッド探索メソッドを使ってマッチする前景SlideGlassを探す
//				SlideGlass matchedFgSg = findMatchingMaskSlide(fgSlides, fgPrap, bgSopUid, bgZPos, fgC, fgT, nx, ny,
//						nz);
//
//				if (matchedFgSg != null && matchedFgSg.getDicomImage().ensurePixelDataLoaded()) {
//					int fgFrameIdx = matchedFgSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
//					ImageProcessor fgProcessor = matchedFgSg.getDicomImage().getImageProcessor(fgFrameIdx).duplicate();
//
//					// カラーマップの適用
//					if (fgLUT != null) {
//						fgProcessor.setLut(fgLUT);
//					}
//
//					// ImageRoiの生成とプロパティ設定
//					ImageRoi imageRoi = new ImageRoi(0, 0, fgProcessor);
//					imageRoi.setOpacity(opacity);
//
//					// ★重要: ImageJのスライス番号は1-based index
//					imageRoi.setPosition(z + 1);
//
//					// 後からUIで操作できるように、SOPInstanceUIDなどを名前に持たせておくと便利です
//					imageRoi.setName("FusionROI_" + bgSopUid);
//
//					overlay.add(imageRoi);
//				}
//			}
//		}
//
//		return overlay;
//	}
//	
//	/**
//     * 背景のIPPに対し、前景ImagePlus内で最も近いZ座標を持つスライスのインデックス(1-based)を返します。
//     */
////    private static int findClosestSliceIndex(double[] bgIpp, ImagePlus fgImp) {
////        int bestIndex = 1;
////        double minDistance = Double.MAX_VALUE;
////        
////        int fgSlices = fgImp.getStackSize();
////        
////        for (int i = 1; i <= fgSlices; i++) {
////            double[] fgIpp = GDicomTools.getImagePositionPatient(fgImp, i);
////            
////            if (bgIpp != null && fgIpp != null && bgIpp.length == 3 && fgIpp.length == 3) {
////                // Z座標(インデックス2)の差分で最短距離を判定
////                double distance = Math.abs(bgIpp[2] - fgIpp[2]); 
////                if (distance < minDistance) {
////                    minDistance = distance;
////                    bestIndex = i; 
////                }
////            }
////        }
////        return bestIndex;
////    }
//
//    private static SlideGlass findMatchingMaskSlide(ConcurrentHashMap<Integer, SlideGlass> maskSlides, Praparat maskPrap, 
//                                                    String targetSopUid, double targetZPos, 
//                                                    int targetC, int targetT, double nx, double ny, double nz) {
//        double EPSILON = 1e-3; 
//        
//        for (Integer idx : maskSlides.keySet()) {
//            int[] zct = maskPrap.calcZCTArrayFromIndex(idx);
//            if (zct[1] != targetC || zct[2] != targetT) continue;
//
//            SlideGlass maskSg = maskSlides.get(idx);
//            if (maskSg == null) continue;
//
//            int maskFrameIdx = maskSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
//
//            String refSopUid = getReferencedSopInstanceUid(maskSg.getHeader(), maskFrameIdx);
//            if (targetSopUid != null && targetSopUid.equals(refSopUid)) {
//                return maskSg; 
//            }
//
//            double[] maskIpp = getSafeIPP(maskSg.getHeader(), maskFrameIdx);
//            if (maskIpp != null) {
//                double maskZPos = (maskIpp[0]*nx + maskIpp[1]*ny + maskIpp[2]*nz);
//                if (Math.abs(maskZPos - targetZPos) < EPSILON) {
//                    return maskSg; 
//                }
//            }
//        }
//        return null; 
//    }
//
//    private static String getReferencedSopInstanceUid(DicomObject header, int frameIndex) {
//        DicomObject perFrameSeq = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frameIndex);
//        if (perFrameSeq != null) {
//            DicomObject derivationSeq = perFrameSeq.getNestedDataset(Tag.DerivationImageSequence, 0);
//            if (derivationSeq != null) {
//                DicomObject sourceImageSeq = derivationSeq.getNestedDataset(Tag.SourceImageSequence, 0);
//                if (sourceImageSeq != null) {
//                    String uid = sourceImageSeq.getString(Tag.ReferencedSOPInstanceUID);
//                    if (uid != null && !uid.isEmpty()) {
//                        return uid;
//                    }
//                }
//            }
//        }
//        return null;
//    }
//
//    private static double[] getSafeIPP(DicomObject header, int frameIndex) {
//        double[] ipp = header.getDoubles(Tag.ImagePositionPatient);
//        if (ipp != null && ipp.length == 3) return ipp;
//
//        DicomObject perFrameSeq = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frameIndex);
//        if (perFrameSeq != null) {
//            DicomObject planePosSeq = perFrameSeq.getNestedDataset(Tag.PlanePositionSequence, 0);
//            if (planePosSeq != null) {
//                return planePosSeq.getDoubles(Tag.ImagePositionPatient);
//            }
//        }
//        return null;
//    }
//
//    private static double[] getSafeIOP(DicomObject header, int frameIndex) {
//        double[] iop = header.getDoubles(Tag.ImageOrientationPatient);
//        if (iop != null && iop.length == 6) return iop;
//
//        DicomObject sharedSeq = header.getNestedDataset(Tag.SharedFunctionalGroupsSequence, 0);
//        if (sharedSeq != null) {
//            DicomObject planeOriSeq = sharedSeq.getNestedDataset(Tag.PlaneOrientationSequence, 0);
//            if (planeOriSeq != null) {
//                iop = planeOriSeq.getDoubles(Tag.ImageOrientationPatient);
//                if (iop != null && iop.length == 6) return iop;
//            }
//        }
//
//        DicomObject perFrameSeq = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frameIndex);
//        if (perFrameSeq != null) {
//            DicomObject planeOriSeq = perFrameSeq.getNestedDataset(Tag.PlaneOrientationSequence, 0);
//            if (planeOriSeq != null) {
//                return planeOriSeq.getDoubles(Tag.ImageOrientationPatient);
//            }
//        }
//        return null;
//    }
//}