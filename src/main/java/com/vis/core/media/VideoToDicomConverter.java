/**
 * copyright Visionary Imaging Sevices, Inc.
 * @author tatsunidas
 */
package com.vis.core.media;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

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
import com.vis.imageio.MpegConverter;
import com.vis.imageio.VideoReader;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.info.VideoInfo;

/**
 * Read non-dicom video, and convert it to dicom with streaming, auto-splitting, and MPEG wrapping.
 */
public class VideoToDicomConverter {

	private static final DICOMBackend backend = DICOMBackend.getCurrent();

	public VideoToDicomConverter() {
	}

	// =========================================================================================
	// 1. MPEGラッパー方式 (JAVE2によるコーデック判定・Remux/Encode・丸ごとカプセル化)
	// =========================================================================================

	/**
	 * コーデックを判定し、MPEGならコピー、非圧縮ならH.264へ変換してDICOMに丸ごとラップするメソッド。
	 */
	public static void convertMpegVideo(File videoFile, File tempDir, int seriesNumber, int instanceNumber, Modality m,
			String patName, String patID, String sex, java.util.Date dob, String studyUID, String studyID,
			String studyDesc, java.util.Date studyDate, java.util.Date studyTime, java.util.Date contentDate,
			java.util.Date contentTime, String seriesDesc) throws Exception {

		// 変換作業用の中間MP4ファイル
		File tempMp4 = new File(tempDir, videoFile.getName() + "_" + System.currentTimeMillis() + "_temp.mp4");

		try {
			// ★ 1. MpegConverter に動画の解析と MP4 化（Remux or Encode）を委譲する
			MultimediaInfo mp4Info = MpegConverter.convertToH264Mp4WithCheck(videoFile, tempMp4);
			VideoInfo mp4VideoInfo = mp4Info.getVideo();
			
			// MPEGカプセル化における「4GBの壁」安全装置
			long mp4FileSize = tempMp4.length();
			long DICOM_SAFE_MAX_SIZE = 4000000000L; // 約3.7GB

			if (mp4FileSize > DICOM_SAFE_MAX_SIZE) {
			    tempMp4.delete();
			    throw new IllegalArgumentException("The compressed MPEG file size (\" + (mp4FileSize / 1024 / 1024) + \" MB) exceeds the 4GB DICOM Item size limit. Please split the video.");
			}

			// 2. 取得した情報からDICOMヘッダに必要な値を計算
			int w = mp4VideoInfo.getSize().getWidth();
			int h = mp4VideoInfo.getSize().getHeight();
			double fps = mp4VideoInfo.getFrameRate();
			long durationMs = mp4Info.getDuration();
			double durationSec = durationMs / 1000.0;
			int frames = (int) Math.rint(durationSec * fps);

			// 3. MPEG専用のDICOMヘッダを作成
			DicomObject dcmHeader = createMpegDicomHeader(w, h, frames, fps, durationSec, seriesNumber, instanceNumber,
					m, patName, patID, sex, dob, studyUID, studyID, studyDesc, studyDate, studyTime, contentDate, contentTime, seriesDesc);

			String sopInstanceUID = dcmHeader.getString(Tag.SOP​Instance​UID);
			File outputFile = new File(tempDir, sopInstanceUID + ".dcm");

			// 4. Transfer Syntax を H.264 (MPEG-4 AVC High Profile / Level 4.1) に指定して書き込み
			String transferSyntax = "1.2.840.10008.1.2.4.102"; 
			DicomWriter writer = DicomWriter.newDicomWriter(backend);
			writer.write(dcmHeader, transferSyntax, outputFile.getAbsolutePath());

			// 5. MP4ファイルをDICOMへ丸ごとストリームカプセル化
			appendMpegDataStream(outputFile, tempMp4);

		} finally {
			// ★ 処理が終了（または失敗）したら中間MP4ファイルは必ず削除
			if (tempMp4.exists()) {
				tempMp4.delete();
			}
		}
	}

	/**
	 * MPEG (H.264) カプセル化専用のDICOMヘッダ構築メソッド
	 */
	private static DicomObject createMpegDicomHeader(int w, int h, int frames, double fps,
			double duration, int seriesNumber, int instanceNumber, Modality m, String patName, String patID, String sex,
			java.util.Date dob, String studyUID, String studyID, String studyDesc, java.util.Date studyDate,
			java.util.Date studyTime, java.util.Date contentDate, java.util.Date contentTime, String seriesDesc) {

		DicomObject core = DicomObject.newDicomObject();

		// Video Photographic Image Storage
		core.setString(Tag.SOP​Class​UID, VR.UI, "1.2.840.10008.5.1.4.1.1.77.1.4.1");

		core.setString(Tag.Patient​Name, TagDict.vrType(Tag.Patient​Name)[0], patName);
		core.setString(Tag.Patient​ID, TagDict.vrType(Tag.Patient​ID)[0], patID);
		core.setString(Tag.Patient​Sex, TagDict.vrType(Tag.Patient​Sex)[0], sex);
		if (dob != null) core.setDate(Tag.Patient​Birth​Date, TagDict.vrType(Tag.Patient​Birth​Date)[0], dob);

		core.setString(Tag.Study​ID, TagDict.vrType(Tag.Study​ID)[0], studyID);
		core.setString(Tag.Study​Description, TagDict.vrType(Tag.Study​Description)[0], studyDesc);
		core.setString(Tag.Study​Instance​UID, VR.UI, studyUID);
		core.setString(Tag.Series​Description, TagDict.vrType(Tag.Series​Description)[0], seriesDesc);
		core.setInt(Tag.Series​Number, TagDict.vrType(Tag.Series​Number)[0], seriesNumber);
		core.setString(Tag.Series​Instance​UID, VR.UI, UIDUtils.createUID());
		core.setString(Tag.SOP​Instance​UID, VR.UI, UIDUtils.createUID());
		core.setInt(Tag.Instance​Number, TagDict.vrType(Tag.Instance​Number)[0], instanceNumber);
		core.setString(Tag.Modality, TagDict.vrType(Tag.Modality)[0], m == Modality.UNKNOWN ? "OT" : m.toString());

		if (studyDate != null) core.setDate(Tag.Study​Date, VR.DA, studyDate);
		if (studyTime != null) core.setDate(Tag.Study​Time, VR.TM, studyTime);
		if (contentDate != null) core.setDate(Tag.Content​Date, VR.DA, contentDate);
		if (contentTime != null) core.setDate(Tag.Content​Time, VR.TM, contentTime);

		core.setInt(Tag.Number​Of​Frames, TagDict.vrType(Tag.Number​Of​Frames)[0], frames);
		
		// ★ MPEGのDICOM仕様に準拠する色空間とビット深度設定
//		core.setString(Tag.Photometric​Interpretation, VR.CS, "YBR_PARTIAL_420");
		core.setString(Tag.Photometric​Interpretation, VR.CS, "RGB");
		core.setInt(Tag.Pixel​Representation, VR.US, 0);
		core.setInt(Tag.Samples​Per​Pixel, TagDict.vrType(Tag.Samples​Per​Pixel)[0], 3);
		core.setInt(Tag.Planar​Configuration, VR.US, 0);
		core.setInt(Tag.Rows, TagDict.vrType(Tag.Rows)[0], h);
		core.setInt(Tag.Columns, TagDict.vrType(Tag.Columns)[0], w);
		core.setInt(Tag.Bits​Allocated, TagDict.vrType(Tag.Bits​Allocated)[0], 8);
		core.setInt(Tag.Bits​Stored, TagDict.vrType(Tag.Bits​Stored)[0], 8);
		core.setInt(Tag.High​Bit, TagDict.vrType(Tag.High​Bit)[0], 7);

		core.setInt(Tag.Cine​Rate, VR.IS, (int) fps);
		core.setDouble(Tag.Effective​Duration, VR.DS, duration);
		core.setDouble(Tag.Frame​Time, VR.DS, 1000.0 / fps);
		
		return core;
	}

	/**
	 * 動画ファイル(MP4)をそのままDICOMのPixelDataとしてカプセル化ストリーム追記するメソッド
	 */
	private static void appendMpegDataStream(File outputFile, File mp4File) throws Exception {
		long videoLength = mp4File.length();
		boolean needsPadding = (videoLength % 2 != 0); // DICOMは偶数バイト必須
		long itemLength = needsPadding ? videoLength + 1 : videoLength;

		try (FileOutputStream fos = new FileOutputStream(outputFile, true);
			 BufferedOutputStream bos = new BufferedOutputStream(fos);
			 FileInputStream fis = new FileInputStream(mp4File)) {

			// 1. PixelData (7FE0, 0010) (OB, Undefined Length)
			bos.write(0xE0); bos.write(0x7F); bos.write(0x10); bos.write(0x00);
			bos.write('O');  bos.write('B');  bos.write(0x00); bos.write(0x00);
			bos.write(0xFF); bos.write(0xFF); bos.write(0xFF); bos.write(0xFF);

			// 2. Basic Offset Table (空)
			bos.write(0xFE); bos.write(0xFF); bos.write(0x00); bos.write(0xE0); 
			bos.write(0x00); bos.write(0x00); bos.write(0x00); bos.write(0x00); 

			// 3. 動画データを入れる Item Tag (FFFE, E000) と、その長さ
			bos.write(0xFE); bos.write(0xFF); bos.write(0x00); bos.write(0xE0);
			bos.write((int) (itemLength & 0xFF));
			bos.write((int) ((itemLength >> 8) & 0xFF));
			bos.write((int) ((itemLength >> 16) & 0xFF));
			bos.write((int) ((itemLength >> 24) & 0xFF));

			// 4. 動画ファイル(MP4)をそのままストリーム転送（バッファを使って爆速コピー）
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1) {
				bos.write(buffer, 0, bytesRead);
			}

			// 奇数長の場合は 0x00 でパディング
			if (needsPadding) {
				bos.write(0x00);
			}

			// 5. Sequence Delimiter (FFFE, E0DD) でカプセル化終了
			bos.write(0xFE); bos.write(0xFF); bos.write(0xDD); bos.write(0xE0);
			bos.write(0x00); bos.write(0x00); bos.write(0x00); bos.write(0x00);

			bos.flush();
		}
	}


	// =========================================================================================
	// 2. 非圧縮分割ストリーム方式 (ImageJを用いた堅牢なピクセル展開と分割処理)
	// =========================================================================================

	/**
	 * 1つの動画ファイルを変換する内部メソッド。4GB超過時は自動的にファイル（インスタンス）を分割する。
	 */
	public static void convertRawVideo(File videoFile, File tempDir, int seriesNumber, int instanceNumber, Modality m,
			String patName, String patID, String sex, java.util.Date dob, String studyUID, String studyID,
			String studyDesc, java.util.Date studyDate, java.util.Date studyTime, java.util.Date contentDate,
			java.util.Date contentTime, String seriesDesc) throws Exception {

		VideoReader reader = VideoReader.load(videoFile);
		if (reader == null) {
			throw new IllegalArgumentException("Unsupported video format: " + videoFile.getName());
		}

		ImagePlus imp = reader.read(); // Virtual Stackとして開かれる
		if (imp == null) {
			throw new IOException("Failed to read video data from: " + videoFile.getName());
		}

		int w = imp.getWidth();
		int h = imp.getHeight();
		int c = (imp.getProcessor() instanceof ColorProcessor) ? 3 : 1;
		int bits = c == 3 ? 8 : imp.getBitDepth();
		int totalFrames = imp.getNSlices();
		boolean isColor = imp.isRGB();
		double fps = imp.getCalibration().fps;

		// 分割されたファイル間で「同じシリーズ」として扱うための共通UID
		String seriesInstanceUID = UIDUtils.createUID();

		// ★ ファイル分割の計算 (安全マージンを取って 4,000,000,000 bytes ≒ 約3.7GB を上限とする)
		long DICOM_SAFE_MAX_SIZE = 4000000000L; 
		int bytesPerPixel = isColor ? 3 : 1;
		long bytesPerFrame = (long) w * h * bytesPerPixel;
		
		int maxFramesPerFile = (int) (DICOM_SAFE_MAX_SIZE / bytesPerFrame);
		if (maxFramesPerFile < 1) {
			imp.close();
			throw new IllegalArgumentException("1フレームのデータサイズが大きすぎます（DICOM仕様上限超過）。");
		}

		int currentFrameOffset = 0;
		int fileIndex = 0; // ファイルが分割されるたびにインクリメント（Instance Number用）

		try {
			while (currentFrameOffset < totalFrames) {
				// このチャンク（ファイル）に書き込むフレーム数を決定
				int chunkFrames = Math.min(maxFramesPerFile, totalFrames - currentFrameOffset);
				long chunkPixelBytes = bytesPerFrame * chunkFrames;
				double chunkDuration = Math.rint(chunkFrames * fps);

				// 1. ヘッダの作成 (チャンクのフレーム数で作成。インスタンス番号を連番にする)
				DicomObject dcmHeader = createDicomHeader(w, h, chunkFrames, isColor, bits, fps, chunkDuration, seriesNumber,
						instanceNumber + fileIndex, m, patName, patID, sex, dob, studyUID, studyID, studyDesc, studyDate, studyTime,
						contentDate, contentTime, seriesDesc, seriesInstanceUID);

				// 2. ヘッダ情報だけを先にファイルへ書き出す (Implicit VR)
				String sopInstanceUID = dcmHeader.getString(Tag.SOP​Instance​UID);
				File outputFile = new File(tempDir, sopInstanceUID + ".dcm");

				DicomWriter writer = DicomWriter.newDicomWriter(backend);
				writer.write(dcmHeader, UID.ImplicitVRLittleEndian.uid(), outputFile.getAbsolutePath());

				// 3. ストリーム処理で指定範囲のフレームだけを追記していく
				try {
					appendPixelDataStreamChunk(outputFile, imp, w, h, chunkFrames, isColor, chunkPixelBytes, currentFrameOffset);
				} catch (Exception e) {
					outputFile.delete(); // 失敗した場合は壊れたファイルを削除
					throw e;
				}

				// 次のチャンク（ファイル）へ進む
				currentFrameOffset += chunkFrames;
				fileIndex++;
			}
		} finally {
			imp.close(); // 確実にリソースを開放
		}
	}

	/**
	 * 非圧縮動画用のDICOMヘッダ情報のみを構築するメソッド。
	 */
	private static DicomObject createDicomHeader(int w, int h, int frames, boolean isColor, int bits, double fps,
			double duration, int seriesNumber, int instanceNumber, Modality m, String patName, String patID, String sex,
			java.util.Date dob, String studyUID, String studyID, String studyDesc, java.util.Date studyDate,
			java.util.Date studyTime, java.util.Date contentDate, java.util.Date contentTime, String seriesDesc, String seriesInstanceUID) {

		DicomObject core = DicomObject.newDicomObject();

		if (isColor) {
			core.setString(Tag.SOP​Class​UID, VR.UI, UID.MultiFrameTrueColorSecondaryCaptureImageStorage.uid());
		} else {
			core.setString(Tag.SOP​Class​UID, VR.UI, UID.MultiFrameGrayscaleByteSecondaryCaptureImageStorage.uid());
		}

		core.setString(Tag.Patient​Name, TagDict.vrType(Tag.Patient​Name)[0], patName);
		core.setString(Tag.Patient​ID, TagDict.vrType(Tag.Patient​ID)[0], patID);
		core.setString(Tag.Patient​Sex, TagDict.vrType(Tag.Patient​Sex)[0], sex);
		if (dob != null) core.setDate(Tag.Patient​Birth​Date, TagDict.vrType(Tag.Patient​Birth​Date)[0], dob);

		core.setString(Tag.Study​ID, TagDict.vrType(Tag.Study​ID)[0], studyID);
		core.setString(Tag.Study​Description, TagDict.vrType(Tag.Study​Description)[0], studyDesc);
		core.setString(Tag.Study​Instance​UID, VR.UI, studyUID);
		core.setString(Tag.Series​Description, TagDict.vrType(Tag.Series​Description)[0], seriesDesc);
		core.setInt(Tag.Series​Number, TagDict.vrType(Tag.Series​Number)[0], seriesNumber);
		
		core.setString(Tag.Series​Instance​UID, VR.UI, seriesInstanceUID);
		core.setString(Tag.SOP​Instance​UID, VR.UI, UIDUtils.createUID()); 
		
		core.setInt(Tag.Instance​Number, TagDict.vrType(Tag.Instance​Number)[0], instanceNumber);
		core.setString(Tag.Modality, TagDict.vrType(Tag.Modality)[0], m == Modality.UNKNOWN ? "OT" : m.toString());

		if (studyDate != null) core.setDate(Tag.Study​Date, VR.DA, studyDate);
		if (studyTime != null) core.setDate(Tag.Study​Time, VR.TM, studyTime);
		if (contentDate != null) core.setDate(Tag.Content​Date, VR.DA, contentDate);
		if (contentTime != null) core.setDate(Tag.Content​Time, VR.TM, contentTime);

		core.setInt(Tag.Number​Of​Frames, TagDict.vrType(Tag.Number​Of​Frames)[0], frames);
		core.setString(Tag.Photometric​Interpretation, VR.CS, isColor ? PhotometricInterpretation.RGB.name() : PhotometricInterpretation.MONOCHROME2.name());
		core.setInt(Tag.Pixel​Representation, VR.US, 0);
		core.setInt(Tag.Samples​Per​Pixel, TagDict.vrType(Tag.Samples​Per​Pixel)[0], isColor ? 3 : 1);
		if (isColor) core.setInt(Tag.Planar​Configuration, VR.US, 0);
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

		return core;
	}

	/**
	 * 指定されたチャンクのフレーム範囲だけを読み込み、DICOMにストリーム追記するメソッド。
	 */
	private static void appendPixelDataStreamChunk(File outputFile, ImagePlus imp, int w, int h, int chunkFrames, boolean isColor, long chunkPixelBytes, int startFrameOffset) throws Exception {
		boolean needsPadding = chunkPixelBytes % 2 != 0;
		long dicomLength = needsPadding ? chunkPixelBytes + 1 : chunkPixelBytes;

		int frameSize = w * h * (isColor ? 3 : 1);
		byte[] frameBuf = isColor ? new byte[frameSize] : null;

		try (FileOutputStream fos = new FileOutputStream(outputFile, true);
				BufferedOutputStream bos = new BufferedOutputStream(fos)) {

			// 1. PixelData (7FE0,0010) タグの書き込み
			bos.write(0xE0);
			bos.write(0x7F);
			bos.write(0x10);
			bos.write(0x00);
			
			bos.write((int) (dicomLength & 0xFF));
			bos.write((int) ((dicomLength >> 8) & 0xFF));
			bos.write((int) ((dicomLength >> 16) & 0xFF));
			bos.write((int) ((dicomLength >> 24) & 0xFF));

			// 2. チャンクのフレーム数分だけループ
			for (int k = 0; k < chunkFrames; k++) {
				imp.setSlice(startFrameOffset + k + 1);

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

			// 3. 奇数長パディング処理
			if (needsPadding) {
				bos.write(0x00);
			}
			bos.flush();
		}
	}
}