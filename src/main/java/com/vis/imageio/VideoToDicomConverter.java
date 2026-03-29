package com.vis.imageio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.vis.core.log.Log;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Modality;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;
import com.vis.dicom.image.PhotometricInterpretation;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * Read non-dicom video, and convert it to dicom with streaming.
 */
public class VideoToDicomConverter {

	private final DICOMBackend backend;

	public VideoToDicomConverter() {
		this.backend = DICOMBackend.getCurrent();
	}

	/**
	 * 複数の動画ファイルを一括でDICOMに変換するメインの公開メソッド。
	 */
	public void convertVideos(ArrayList<File> videos, File tempDir, Modality m, String patName, String patID,
			String sex, java.util.Date dob, String studyUID, String studyID, String studyDesc, java.util.Date studyDate,
			java.util.Date studyTime, java.util.Date contentDate, java.util.Date contentTime, String seriesDesc,
			int initialSeriesNumber) {
		for (int i = 0; i < videos.size(); i++) {
			File videoFile = videos.get(i);
			int currentSeriesNumber = initialSeriesNumber + i;
			int instanceNumber = i + 1;
			try {
				convertSingleVideo(videoFile, tempDir, currentSeriesNumber, instanceNumber, m, patName, patID, sex, dob,
						studyUID, studyID, studyDesc, studyDate, studyTime, contentDate, contentTime, seriesDesc);
				Log.logger.info("Successfully converted: " + videoFile.getName());
			} catch (Exception e) {
				// 1ファイル失敗しても、次のファイルの処理は継続する
				Log.logger.warning("Failed to convert video " + videoFile.getName() + ": " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	/**
	 * 1つの動画ファイルを変換する内部メソッド。
	 */
	private void convertSingleVideo(File videoFile, File tempDir, int seriesNumber, int instanceNumber, Modality m,
			String patName, String patID, String sex, java.util.Date dob, String studyUID, String studyID,
			String studyDesc, java.util.Date studyDate, java.util.Date studyTime, java.util.Date contentDate,
			java.util.Date contentTime, String seriesDesc) throws Exception {

		VideoReader reader = VideoReader.load(videoFile);
		if (reader == null) {
			throw new IllegalArgumentException("Unsupported video format: " + videoFile.getName());
		}

		ImagePlus imp = reader.read(); // Virtual Stackとして開かれる想定
		if (imp == null) {
			throw new IOException("Failed to read video data from: " + videoFile.getName());
		}

		int w = imp.getWidth();
		int h = imp.getHeight();
		int c = imp.getNChannels();
		int bits = imp.getBitDepth();
		int frames = imp.getNSlices();
		boolean isColor = (c > 1 || bits >= 24);
		double fps = imp.getCalibration().fps;
		double duration = Math.rint(frames * fps);

		// 1. PixelDataを含まないヘッダのみのDicomObjectを作成
		DicomObject dcmHeader = createDicomHeader(w, h, frames, isColor, bits, fps, duration, seriesNumber,
				instanceNumber, m, patName, patID, sex, dob, studyUID, studyID, studyDesc, studyDate, studyTime,
				contentDate, contentTime, seriesDesc);

		String sopInstanceUID = dcmHeader.getString(Tag.SOP​Instance​UID);
		File outputFile = new File(tempDir, sopInstanceUID);

		// 2. まずヘッダだけをファイルに書き出す
		DicomWriter writer = DicomWriter.newDicomWriter(backend);
		writer.write(dcmHeader, UID.ImplicitVRLittleEndian.uid(), outputFile.getAbsolutePath());

		// 3. PixelDataをストリームで追記する（失敗時は作成途中のファイルを削除する）
		try {
			appendPixelDataStream(outputFile, imp, w, h, frames, isColor);
		} catch (Exception e) {
			cleanupFailedFile(outputFile);
			throw new IOException("Failed during streaming pixel data for " + videoFile.getName(), e);
		} finally {
			// Virtual Stackを開放し、ファイルロックを解除する
			imp.close();
		}
	}

	/**
	 * PixelDataを含まないDICOMヘッダ情報のみを構築するメソッド。
	 */
	private DicomObject createDicomHeader(int w, int h, int frames, boolean isColor, int bits, double fps,
			double duration, int seriesNumber, int instanceNumber, Modality m, String patName, String patID, String sex,
			java.util.Date dob, String studyUID, String studyID, String studyDesc, java.util.Date studyDate,
			java.util.Date studyTime, java.util.Date contentDate, java.util.Date contentTime, String seriesDesc) {

		DicomObject core = DicomObject.newDicomObject();

		// --- 基本タグ・患者情報 ---
		if (isColor) {
			core.setString(Tag.SOP​Class​UID, VR.UI, UID.MultiFrameTrueColorSecondaryCaptureImageStorage.uid());
		} else {
			// 8-bits only
			core.setString(Tag.SOP​Class​UID, VR.UI, UID.MultiFrameGrayscaleByteSecondaryCaptureImageStorage.uid());
		}

		core.setString(Tag.Patient​Name, TagDict.vrType(Tag.Patient​Name)[0], patName);
		core.setString(Tag.Patient​ID, TagDict.vrType(Tag.Patient​ID)[0], patID);
		core.setString(Tag.Patient​Sex, TagDict.vrType(Tag.Patient​Sex)[0], sex);
		if (dob != null)
			core.setDate(Tag.Patient​Birth​Date, TagDict.vrType(Tag.Patient​Birth​Date)[0], dob);

		// --- 検査(Study)・シリーズ(Series)情報 ---
		core.setString(Tag.Study​ID, TagDict.vrType(Tag.Study​ID)[0], studyID);
		core.setString(Tag.Study​Description, TagDict.vrType(Tag.Study​Description)[0], studyDesc);
		core.setString(Tag.Study​Instance​UID, VR.UI, studyUID);
		core.setString(Tag.Series​Description, TagDict.vrType(Tag.Series​Description)[0], seriesDesc);
		core.setInt(Tag.Series​Number, TagDict.vrType(Tag.Series​Number)[0], seriesNumber);
		core.setString(Tag.Series​Instance​UID, VR.UI, UIDUtils.createUID());
		core.setString(Tag.SOP​Instance​UID, VR.UI, UIDUtils.createUID());
		core.setInt(Tag.Instance​Number, TagDict.vrType(Tag.Instance​Number)[0], instanceNumber);
		core.setString(Tag.Modality, TagDict.vrType(Tag.Modality)[0], m == Modality.UNKNOWN ? "OT" : m.toString());

		if (studyDate != null)
			core.setDate(Tag.Study​Date, VR.DA, studyDate);
		if (studyTime != null)
			core.setDate(Tag.Study​Time, VR.TM, studyTime);
		if (contentDate != null)
			core.setDate(Tag.Content​Date, VR.DA, contentDate);
		if (contentTime != null)
			core.setDate(Tag.Content​Time, VR.TM, contentTime);

		// --- 画像(Pixel)・シネ(Cine)モジュール ---
		core.setInt(Tag.Number​Of​Frames, TagDict.vrType(Tag.Number​Of​Frames)[0], frames);
		core.setString(Tag.Photometric​Interpretation, VR.CS,
				isColor ? PhotometricInterpretation.RGB.name() : PhotometricInterpretation.MONOCHROME2.name());
		core.setInt(Tag.Pixel​Representation, VR.US, 0);
		core.setInt(Tag.Samples​Per​Pixel, TagDict.vrType(Tag.Samples​Per​Pixel)[0], isColor ? 3 : 1);
		if (isColor) {
			/*
			 * 0 (Color-by-pixel): ピクセルごとに色情報が並ぶ形式。つまり RGB RGB RGB 1 (Color-by-plane):
			 * 色（プレーン）ごとにすべてのピクセル情報が並ぶ形式. RRR... GGG... BBB...
			 */
			core.setInt(Tag.Planar​Configuration, VR.US, 0);
		}
		core.setInt(Tag.Rows, TagDict.vrType(Tag.Rows)[0], h);
		core.setInt(Tag.Columns, TagDict.vrType(Tag.Columns)[0], w);

		int bitsAllocated = isColor ? 8 : bits;
		core.setInt(Tag.Bits​Allocated, TagDict.vrType(Tag.Bits​Allocated)[0], bitsAllocated);
		core.setInt(Tag.Bits​Stored, TagDict.vrType(Tag.Bits​Stored)[0], bitsAllocated);
		core.setInt(Tag.High​Bit, TagDict.vrType(Tag.High​Bit)[0], bitsAllocated - 1);

		core.setInt(Tag.Cine​Rate, VR.IS, (int) fps);
		core.setDouble(Tag.Effective​Duration, VR.DS, duration);
		core.setDouble(Tag.Frame​Time, VR.DS, 1000.0 / fps);

		core.setString(Tag.Conversion​Type, VR.CS, "WSD");

		// ※ ストリームで追記するため、ここでは Tag.PixelData をセットしない
		return core;
	}

	/**
	 * DICOMファイルにPixelData(7FE0,0010)と動画フレームのバイト列を追記するメソッド。
	 */
	private void appendPixelDataStream(File outputFile, ImagePlus imp, int w, int h, int frames, boolean isColor)
			throws Exception {
		int bytesPerPixel = isColor ? 3 : 1;
		int frameSize = w * h * bytesPerPixel;

		long totalPixelBytes = (long) frameSize * frames;
		boolean needsPadding = totalPixelBytes % 2 != 0;
		long dicomLength = needsPadding ? totalPixelBytes + 1 : totalPixelBytes;

		byte[] frameBuf = isColor ? new byte[frameSize] : null;

		try (FileOutputStream fos = new FileOutputStream(outputFile, true);
				BufferedOutputStream bos = new BufferedOutputStream(fos)) {

			// 1. PixelData (7FE0,0010) タグ (Implicit VR Little Endian)
			bos.write(0xE0);
			bos.write(0x7F);
			bos.write(0x10);
			bos.write(0x00);

			// 2. 値の長さ (32-bit Little Endian)
			bos.write((int) (dicomLength & 0xFF));
			bos.write((int) ((dicomLength >> 8) & 0xFF));
			bos.write((int) ((dicomLength >> 16) & 0xFF));
			bos.write((int) ((dicomLength >> 24) & 0xFF));

			// 3. 各フレームのピクセルデータを Virtual Stack から取得して書き込み
			for (int k = 0; k < frames; k++) {
				imp.setSlice(k + 1); // ここでディスクから1フレーム読み込まれる

				if (!isColor) {
					ImageProcessor ip = imp.getProcessor().convertToByte(true);
					byte[] b = (byte[]) ip.getPixels();
					bos.write(b, 0, frameSize);
				} else {
					ColorProcessor cp = (ColorProcessor) imp.getProcessor();
					byte[] r = new byte[w * h];
					byte[] g = new byte[w * h];
					byte[] b = new byte[w * h];
					cp.getRGB(r, g, b);

					int pIndex = 0;
					for (int p = 0; p < w * h; p++) {
						frameBuf[pIndex++] = r[p];
						frameBuf[pIndex++] = g[p];
						frameBuf[pIndex++] = b[p];
					}
					bos.write(frameBuf, 0, frameSize);
				}
			}

			// 4. 奇数長パディング処理
			if (needsPadding) {
				bos.write(0x00);
			}
			bos.flush();
		}
	}

	/**
	 * 変換途中でエラーが発生した場合に、不完全なDICOMファイルを削除する。
	 */
	private void cleanupFailedFile(File file) {
		if (file != null && file.exists()) {
			boolean deleted = file.delete();
			if (!deleted) {
				Log.logger.warning("Failed to delete incomplete DICOM file: " + file.getAbsolutePath());
			} else {
				Log.logger.info("Deleted incomplete DICOM file due to error: " + file.getAbsolutePath());
			}
		}
	}
}