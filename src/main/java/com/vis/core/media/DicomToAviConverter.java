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

        // Prepare progress dialog
        ProgressMonitor pm = new ProgressMonitor(null, "AVI Conversion", "Initializing...", 0, numFrames);
        pm.setMillisToDecideToPopup(0);
        pm.setMillisToPopup(0);

        try {
            ImageStack stack = new ImageStack(width, height);

            for (int i = 0; i < numFrames; i++) {
                // Check for cancellation
                if (pm.isCanceled()) {
                    System.out.println("Conversion cancelled by the user.");
                    return;
                }

                pm.setNote("Extracting frame: " + (i + 1) + " / " + numFrames);
                pm.setProgress(i);

                // NPE prevention: Skip processing if frame extraction fails
                ImageProcessor ip = dicom.getImageProcessor(i);
                if (ip != null) {
                    stack.addSlice("Frame " + i, ip);
                } else {
                    System.err.println("Warning: Failed to extract frame " + i + ", skipping.");
                }
            }

            if (stack.getSize() == 0) {
                showErrorDialog("Could not extract any valid frames.");
                return;
            }

            pm.setNote("Exporting to AVI file...");
            
            // Create ImagePlus and set FPS
            ImagePlus imp = new ImagePlus("DICOM_Video", stack);
            imp.getCalibration().fps = fps;

            // Export to AVI
            AVI_Writer writer = new AVI_Writer();
            writer.writeImage(imp, outputAviPath, AVI_Writer.JPEG_COMPRESSION, 100);

            // Completion notification
            pm.close();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "AVI conversion completed.\nOutput: " + outputAviPath, "Complete", JOptionPane.INFORMATION_MESSAGE);
            });

        } catch (Exception e) {
            pm.close();
            showErrorDialog("An unexpected error occurred during conversion:\n" + e.getMessage());
            e.printStackTrace();
        } finally {
            if (pm != null) {
                pm.close();
            }
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
