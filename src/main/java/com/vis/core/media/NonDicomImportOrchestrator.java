/**
 * Copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.media;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.vis.core.util.ImageUtils;
import com.vis.dicom.Modality; // ← VideoConverterなどで使っているModalityに合わせてimport

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
    public void executeImport(List<File> files, NonDicomMediaContext context, File tempDir, int initialSeriesNumber) throws Exception {
        
        // 1. ファイルの種類ごとに仕分ける
        ArrayList<File> images = new ArrayList<>();
        ArrayList<File> videos = new ArrayList<>();
        ArrayList<File> pdfs = new ArrayList<>();
        
        for (File f : files) {
            // (注) DicomUtilities.isDicomFile の判定は UI側(Importer)で弾いている想定ですが、念のため入れてもOK
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

        // 2. 静止画の処理（★ ルール1: まとめて1つのシリーズにする）
        if (!images.isEmpty()) {
            // contextは1つしかないので、その中のseriesUIDをそのまま使う
            ImageToDicomConverter.convertImages(images, tempDir, context, currentSeriesNo);
            currentSeriesNo++;
        }

        // 3. 動画の処理（★ ルール2: 1ファイルごとに別のシリーズにする）
        for (File video : videos) {
            // Video用のUIDを新しく発行する（contextの中身を上書き、あるいは直接渡す）
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
                    context.seriesDesc);
            
            currentSeriesNo++;
        }

        // 4. PDFの処理（★ ルール3: 1ファイルごとに別のシリーズにする）
        for (File pdf : pdfs) {
            // PDF用のUIDを新しく発行する
            context.seriesUID = com.vis.dicom.UIDUtils.createUID();
            
            PdfToDicomConverter.convertPDF(pdf, tempDir, context, currentSeriesNo);
            
            currentSeriesNo++;
        }
    }
}
