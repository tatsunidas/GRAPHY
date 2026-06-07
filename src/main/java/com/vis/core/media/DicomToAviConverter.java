/**
 * Copyright visionary imaging services, inc.
 */
package com.vis.core.media;

import com.vis.core.log.Log;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.progress.EncoderProgressListener;
import ij.plugin.filter.AVI_Writer;

import ij.io.SaveDialog;

import java.io.File;

import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.configuration.Resources;
import com.vis.dicom.dcm4cheImpl.DecompressorChe;

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
            Log.logger.info("DicomToAviConverter: Conversion cancelled by the user (no save path selected).");
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
            dicom = DicomImage.newDicomImage(dicomFilePath, true, DICOMBackend.getCurrent());
        } catch (Exception e) {
            showErrorDialog("Failed to load DICOM file:\n" + e.getMessage());
            return;
        }

        if (dicom == null || dicom.getHeader() == null) {
            showErrorDialog("No valid DICOM data found.");
            return;
        }
        
        if(dicom.getHeader().getValue(Tag.PixelData) == null) {
        	Log.logger.severe("What's ??");
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
        
        String tsUid = dicom.getTSUID().uid();
        boolean isMpeg = tsUid.startsWith("1.2.840.10008.1.2.4.10");
        if(isMpeg) {
        	transcodeMpegToAviViaJave(dicom, outputAviPath);
        }else {
        	convertToAviViaImageJ(dicom, outputAviPath, fps);
        }
    }
    
    /**
     * 抽出済みの動画ファイルを、JAVE2を用いてAVIへ一括トランスコードします。
     * 毎フレームのデコードが発生しないため、長編動画でも数秒〜数十秒で完了します。
     */
    private void transcodeMpegToAviViaJave(DicomImage dicom, String outputAviPath) {
        ProgressMonitor pm = new ProgressMonitor(null, "AVI Conversion", "Transcoding video stream...", 0, 100);
        pm.setMillisToDecideToPopup(0);
        pm.setMillisToPopup(0);

        try {
        	
        	DecompressorChe d = new DecompressorChe(dicom);
        	File sourceMp4 = d.extractMpegToTempFile();
        	
            VideoAttributes video = new VideoAttributes();
            video.setCodec("mjpeg");
//            video.setCodec("mpeg4"); // AVIと最も互換性の高い標準的な動画コーデック
            
			// ★ なるべく圧縮しない（最高画質）ための設定
			// MJPEGの品質を1（最高画質・最低圧縮）に指定します（範囲: 1〜31）
			video.setQuality(1);

			// ★ ビットレートの制限による劣化を防ぐため、非常に高い値（例: 100Mbps）を許容します
			video.setBitRate(100000000);

            EncodingAttributes encodingAttrs = new EncodingAttributes();
            encodingAttrs.setOutputFormat("avi");
            encodingAttrs.setVideoAttributes(video);

            Encoder encoder = new Encoder();
            EncoderProgressListener javeListener = new EncoderProgressListener() {
                @Override
                public void sourceInfo(MultimediaInfo info) {}

                @Override
                public void progress(int permil) {
                    if (pm.isCanceled()) {
                        // 必要に応じて処理の中断ロジックを実装可能
                        SwingUtilities.invokeLater(() -> pm.setNote("Cancelling..."));
                    } else {
                        int percent = permil / 10;
                        SwingUtilities.invokeLater(() -> {
                            pm.setProgress(percent);
                            pm.setNote("Transcoding to AVI: " + percent + "%");
                        });
                    }
                }

                @Override
                public void message(String message) {}
            };

            // JAVE2による一括変換の実行
            encoder.encode(new MultimediaObject(sourceMp4), new File(outputAviPath), encodingAttrs, javeListener);

            pm.close();
            if (!pm.isCanceled()) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, Resources.i18n("DicomToAviConverter.done") + " " + outputAviPath,
                            Resources.i18n("dialog.title.complete"), JOptionPane.INFORMATION_MESSAGE);
                });
            }

        } catch (Exception e) {
            pm.close();
            showErrorDialog("Error during JAVE2 transcoding:\n" + e.getMessage());
            e.printStackTrace();
        } finally {
            if (pm != null) pm.close();
        }
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
            writer.writeImage(imp, outputAviPath, AVI_Writer.JPEG_COMPRESSION, 100);

            pm.close();
            if (pm.isCanceled()) {
                Log.logger.info("DicomToAviConverter: Conversion was cancelled.");
                // 必要であれば、作りかけの出力ファイルを削除する処理をここに追加
            } else {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, Resources.i18n("DicomToAviConverter.done") + " " + outputAviPath,
                            Resources.i18n("dialog.title.complete"), JOptionPane.INFORMATION_MESSAGE);
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
        Log.logger.severe("DicomToAviConverter error: " + message);
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, message, Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
        });
    }
}
