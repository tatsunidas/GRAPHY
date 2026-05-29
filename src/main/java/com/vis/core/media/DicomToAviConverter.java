/**
 * Copyright visionary imaging services, inc.
 */
package com.vis.core.media;

import com.vis.dicom.DICOMBackend;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.plugin.filter.AVI_Writer;

import ij.io.SaveDialog;

import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

/**
 * @author tatsunidas
 */
public class DicomToAviConverter {

    /**
     * Constructor that opens a SaveDialog and starts the conversion process.
     * * @param dicomFilePath Input DICOM file path
     * @param fallbackFps   Default FPS value to use if DICOM tags are missing
     */
    public DicomToAviConverter(String dicomFilePath, double fallbackFps) {
        
        // 1. Check if input file path is valid before showing dialog
        if (dicomFilePath == null || dicomFilePath.isEmpty()) {
            showErrorDialog("Invalid input DICOM path.");
            return;
        }

        // 2. Show ImageJ's SaveDialog to choose output path
        SaveDialog sd = new SaveDialog("Save AVI File", "converted_video", ".avi");
        String directory = sd.getDirectory();
        String fileName = sd.getFileName();

        // 3. Check if user cancelled the dialog
        if (directory == null || fileName == null) {
            System.out.println("Conversion cancelled by the user (No save path selected).");
            return;
        }

        String outputAviPath = directory + fileName;

        // 4. Run the heavy conversion process in a background thread
        //    This prevents the Swing UI (ProgressMonitor) from freezing.
        new Thread(() -> {
            convert(dicomFilePath, outputAviPath, fallbackFps);
        }).start();
    }

    /**
     * Converts a DICOM image to AVI format.
     * * @param dicomFilePath Input DICOM file path
     * @param outputAviPath Output AVI file path
     * @param fallbackFps   Default FPS value to use if DICOM tags are missing
     */
    private void convert(String dicomFilePath, String outputAviPath, double fallbackFps) {
        
        DicomImage dicom = null;
        try {
            dicom = DicomImage.newDicomImage(dicomFilePath, DICOMBackend.getCurrent());
        } catch (Exception e) {
            showErrorDialog("Failed to load DICOM file:\n" + e.getMessage());
            return;
        }

        if (dicom == null || dicom.getHeader() == null) {
            showErrorDialog("No valid DICOM data found.");
            return;
        }

        int numFrames = dicom.getNumOfFrames();
        if (numFrames <= 0) {
            showErrorDialog("No extractable frames found.");
            return;
        }

        int width = dicom.getWidth();
        int height = dicom.getHeight();
        if (width <= 0 || height <= 0) {
            showErrorDialog("Invalid image resolution (Width: " + width + ", Height: " + height + ").");
            return;
        }

        // Accurate FPS calculation (Vendor-specific support)
        double fps = calculateAccurateFps(dicom.getHeader(), fallbackFps);
        convertToAviViaImageJ(dicom, outputAviPath, fps);
    }
    
    /**
     * 従来のImageJ(ImageStack)を利用したAVI変換処理。
     * VirtualStackを用いて、全フレームをメモリに保持せずストリームで逐次書き込みます。
     */
    private void convertToAviViaImageJ(DicomImage dicom, String outputAviPath, double fallbackFps) {
        int numFrames = dicom.getNumOfFrames();
        int width = dicom.getWidth();
        int height = dicom.getHeight();

        double fps = calculateAccurateFps(dicom.getHeader(), fallbackFps);
        ProgressMonitor pm = new ProgressMonitor(null, "AVI Conversion", "Initializing...", 0, numFrames);
        pm.setMillisToDecideToPopup(0);
        pm.setMillisToPopup(0);

        try {
            // ★ 巨大な ImageStack ではなく VirtualStack を使用する
            ij.VirtualStack vStack = new ij.VirtualStack(width, height, numFrames) {
                @Override
                public ImageProcessor getProcessor(int n) {
                    // n は 1 から numFrames まで順番に呼ばれます
                    
                    if (pm.isCanceled()) {
                        // キャンセル時は安全に終わらせるために黒い画像を返す
                        return new ij.process.ColorProcessor(width, height);
                    }

                    SwingUtilities.invokeLater(() -> {
                        pm.setNote("Encoding frame: " + n + " / " + numFrames);
                        pm.setProgress(n);
                    });

                    // その瞬間に必要なフレームだけを抽出 (0始まりなので n-1)
                    ImageProcessor ip = dicom.getImageProcessor(n - 1);
                    
                    if (ip == null) {
                        System.err.println("Warning: Failed to extract frame " + n + ", using blank frame.");
                        // デコード失敗でクラッシュさせないためのフォールバック
                        return new ij.process.ColorProcessor(width, height);
                    }
                    return ip;
                }
            };

            pm.setNote("Exporting to AVI file (Stream mode)...");
            
            // VirtualStack を ImagePlus にセット
            ImagePlus imp = new ImagePlus("DICOM_Video", vStack);
            imp.getCalibration().fps = fps;

            // AVI_Writer は、内部で vStack.getProcessor(n) を1フレームずつ呼んで逐次ファイルに書き込みます
            AVI_Writer writer = new AVI_Writer();
            writer.writeImage(imp, outputAviPath, AVI_Writer.JPEG_COMPRESSION, 85);

            pm.close();
            if (pm.isCanceled()) {
                System.out.println("Conversion was cancelled.");
                // 必要であれば、作りかけの出力ファイルを削除する処理をここに追加
            } else {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "AVI conversion completed.\nOutput: " + outputAviPath, "Complete", JOptionPane.INFORMATION_MESSAGE);
                });
            }

        } catch (Exception e) {
            pm.close();
            showErrorDialog("An unexpected error occurred during conversion:\n" + e.getMessage());
            e.printStackTrace();
        } finally {
            if (pm != null) pm.close();
        }
    }

    /**
     * Calculates accurate FPS from the DICOM header based on priority.
     */
    private double calculateAccurateFps(DicomObject header, double fallbackFps) {
        double calculatedFps = fallbackFps;
        
        try {
            // Priority 1: Recommended Display Frame Rate (0008,2144)
            String recFpsStr = header.getString(Tag.RecommendedDisplayFrameRate);
            if (recFpsStr != null && !recFpsStr.trim().isEmpty()) {
                return Double.parseDouble(recFpsStr.trim());
            }

            // Priority 2: Cine Rate (0018,0040)
            String cineRateStr = header.getString(Tag.CineRate);
            if (cineRateStr != null && !cineRateStr.trim().isEmpty()) {
                return Double.parseDouble(cineRateStr.trim());
            }

            // Priority 3: Frame Time (0018,1063) - Requires conversion as unit is milliseconds
            String frameTimeStr = header.getString(Tag.FrameTime);
            if (frameTimeStr != null && !frameTimeStr.trim().isEmpty()) {
                double frameTimeMs = Double.parseDouble(frameTimeStr.trim());
                if (frameTimeMs > 0) {
                    return 1000.0 / frameTimeMs;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse FPS tags. Using default value (" + fallbackFps + ").");
        }

        return calculatedFps;
    }

    /**
     * Safely displays an error dialog.
     */
    private void showErrorDialog(String message) {
        System.err.println(message);
        // Safely show dialog on the UI thread
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }
}
