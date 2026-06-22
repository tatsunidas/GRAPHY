/**
 * Copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.media;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import com.vis.core.util.ImageUtils;
import com.vis.dicom.Modality;

public class NonDicomImportOrchestrator {

    /**
     * 選択された複数の非DICOMファイルをルールに従って分類し、
     * それぞれ適切なConverterを呼び出してDICOM化を実行します。
     * * ルール:
     * 1. 静止画(Image)は、複数あっても「1つのシリーズ」にまとめて変換する。
     * 2. 動画(Video)は、「1ファイルにつき1つのシリーズ」として変換する。
     * 3. PDFも、「1ファイルにつき1つのシリーズ」として変換する。
     * @param files インポート対象のファイル群
     * @param context ユーザーが入力した患者情報等のコンテキスト
     * @param tempDir 変換後のDICOMファイルを保存する一時ディレクトリ
     * @param initialSeriesNumber 既存Studyに追加する場合はその続きの番号、新規の場合は1
     */
    public void executeConvert(List<File> files, NonDicomMediaContext context, File tempDir, int initialSeriesNumber) throws Exception {
    	
        // 1. ファイルの種類ごとに仕分ける
        ArrayList<File> images = new ArrayList<>();
        ArrayList<File> videos = new ArrayList<>();
        ArrayList<File> pdfs = new ArrayList<>();
        for (File f : files) {
            String path = f.getAbsolutePath();
            if (ImageUtils.isImageFile(path)) {
                images.add(f);
            } else if (ImageUtils.isVideoFile(path)) {
                videos.add(f);
            } else if (ImageUtils.isPDF(path)) {
                pdfs.add(f);
            }
        }

        int currentSeriesNo = initialSeriesNumber;

        // ★ 簡易プログレスモニターの作成 (0〜100%)
        ProgressMonitor pm = new ProgressMonitor(null, "Importing as DICOM", "Ready to start...", 0, 100);
        pm.setMillisToDecideToPopup(0);
        pm.setMillisToPopup(0);

        try {
            // 2. 静止画の処理（★ ルール1: まとめて1つのシリーズにする）
            if (!images.isEmpty()) {
                if (pm.isCanceled()) return; // キャンセルチェック
                
                SwingUtilities.invokeLater(() -> {
                    pm.setProgress(0);
                    pm.setNote("Converting images (" + images.size() + " images)...");
                });

                // contextは1つしかないので、その中のseriesUIDをそのまま使う
                ImageToDicomConverter.convertImages(images, tempDir, context, currentSeriesNo);
                currentSeriesNo++;
            }

            // 3. 動画の処理（★ ルール2: 1ファイルごとに別のシリーズにする）
            for (int i = 0; i < videos.size(); i++) {
                if (pm.isCanceled()) return; // キャンセルチェック

                File video = videos.get(i);
                int videoIndex = i + 1;
                int totalVideos = videos.size();
                
                // Video用のUIDを新しく発行する
                context.seriesUID = com.vis.dicom.UIDUtils.createUID(); 
                
                VideoToDicomConverter.convertMpegVideo(
                        video, tempDir, currentSeriesNo, 1, Modality.OT, // OT = Other
                        context.pname, context.pid, context.sex, 
                        com.vis.core.util.DateUtils.toDateObj(context.dob, "/"), 
                        context.studyUID, null, context.studyDesc, 
                        com.vis.core.util.DateUtils.toDateObj(context.studyDate, "/"), 
                        com.vis.core.util.DateUtils.toTimeObj(context.studyTime, "/"), 
                        com.vis.core.util.DateUtils.toDateObj(context.contentDate, "/"), 
                        com.vis.core.util.DateUtils.toTimeObj(context.contentTime, "/"), 
                        context.seriesDesc,
                        // ★ プログレスリスナーの実装
                        (percent, message) -> {
                            // UIの更新は必ずSwingのイベントスレッドで行う
                            SwingUtilities.invokeLater(() -> {
                                pm.setProgress(percent);
                                pm.setNote("Video " + videoIndex + "/" + totalVideos + " : " + message);
                            });
                        }
                );
                            
                currentSeriesNo++;
            }

            // 4. PDFの処理（★ ルール3: 1ファイルごとに別のシリーズにする）
            for (int i = 0; i < pdfs.size(); i++) {
                if (pm.isCanceled()) return; // キャンセルチェック

                File pdf = pdfs.get(i);
                int pdfIndex = i + 1;
                int totalPdfs = pdfs.size();

                SwingUtilities.invokeLater(() -> {
                    pm.setProgress(50); // PDFは細かい進捗が取れないと仮定し、便宜上50%にしておく
                    pm.setNote("Converting PDF " + pdfIndex + "/" + totalPdfs + " : " + pdf.getName());
                });

                // PDF用のUIDを新しく発行する
                context.seriesUID = com.vis.dicom.UIDUtils.createUID();
                PdfToDicomConverter.convertPDF(pdf, tempDir, context, currentSeriesNo);
                
                currentSeriesNo++;
            }
            
            // 完了時の通知（必要に応じて）
            SwingUtilities.invokeLater(() -> pm.setProgress(100));

        } finally {
            // エラー時も含め、確実にダイアログを閉じる。
            // setProgress/setNoteと同様、ProgressMonitorの操作は必ずEDTで行う必要がある。
            // ここだけバックグラウンドスレッドから直接close()していたため、EDT側で進行中の
            // ダイアログ生成処理と競合してNullPointerException (ProgressMonitor.close()内)
            // になることがあった。
            if (pm != null) {
                SwingUtilities.invokeLater(pm::close);
            }
        }
    }
}
