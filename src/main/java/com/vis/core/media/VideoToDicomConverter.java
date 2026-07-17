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
import com.vis.imageio.VideoReader;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import ws.schild.jave.progress.EncoderProgressListener;

/**
 * Read non-dicom video, and convert it to dicom with streaming, auto-splitting, and MPEG wrapping.
 */
public class VideoToDicomConverter {

	private static final DICOMBackend backend = DICOMBackend.getCurrent();
	
	public interface ProgressListener {
		/**
		 * @param percent 進行度 (0〜100)
		 * @param message 現在の処理状態を示すメッセージ
		 */
		void onProgress(int percent, String message);
	}

	public VideoToDicomConverter() {
	}
	
	/**
	 * 動画を確実にH.264(MP4)へ変換してDICOMに丸ごとラップするメソッド。
	 * MJPEGなど非対応コーデックが来てもJAVE2で自動的にトランスコードします。
	 */
	public static void convertMpegVideo(File videoFile, File tempDir, int seriesNumber, int instanceNumber, Modality m,
			String patName, String patID, String sex, java.util.Date dob, String studyUID, String studyID,
			String studyDesc, java.util.Date studyDate, java.util.Date studyTime, java.util.Date contentDate,
			java.util.Date contentTime, String seriesDesc, ProgressListener listener) throws Exception {

		File tempMp4 = new File(tempDir, videoFile.getName() + "_" + System.currentTimeMillis() + "_temp.mp4");

		try {
			if (listener != null) {
				listener.onProgress(0, "Starting video analysis and H.264 (MP4) conversion...");
			}

			// ★ 修正点: 厳しいMpegConverterのチェックを使わず、JAVE2で直接H.264(MP4)へ強制トランスコードする
			ws.schild.jave.MultimediaObject multimediaObject = new ws.schild.jave.MultimediaObject(videoFile);

			ws.schild.jave.encode.VideoAttributes videoAttrs = new ws.schild.jave.encode.VideoAttributes();
			videoAttrs.setCodec("libx264"); // DICOM仕様に適合する標準的なH.264コーデックを指定

			ws.schild.jave.encode.EncodingAttributes encodingAttrs = new ws.schild.jave.encode.EncodingAttributes();
			encodingAttrs.setOutputFormat("mp4");
			encodingAttrs.setVideoAttributes(videoAttrs);

			// ★ 短いキーフレーム(GOP)間隔を強制する：
			// JCodecでの再生時、ランダムアクセス(離れたフレームへのジャンプ)は直近のキーフレームから
			// 対象フレームまで逐次デコードし直す必要があるため、キーフレームが動画先頭の1個しかないと
			// 終端付近へのジャンプが全フレーム再デコードに近いコストになり非常に重くなる(2D Viewerで確認)。
			// -g でGOPサイズの上限を指定し、最大でもGOP_SIZE_FRAMES枚先読みすれば
			// 任意のフレームに到達できるようにする(ジャンプのコストをO(全長)からO(GOPサイズ)に抑える)。
			// 既にDICOM化済みの動画には遡って効果はなく、今後の取り込みにのみ適用される。
			final int GOP_SIZE_FRAMES = 30;
			// ★ Bフレームを無効化する：
			// libx264はデフォルトでBフレーム(bframes=3)を使うため、MP4内のサンプル物理順(decode順)と
			// 実際の表示順(PTS順)が異なる。表示側(VideoReaderJCodec)はJCodecのFrameGrab.getNativeFrame()を
			// 単純に連番で呼び出しておりdecode順のまま返す(PTS順への並べ替えを行わない)ため、
			// Bフレームが存在すると中間フレームの表示順序が崩れる不具合が発生していた。
			// -bf 0 でBフレームを禁止すればdecode順=表示順になり、確実にこの不具合を回避できる。
			// (トレードオフ: 圧縮効率がわずかに下がりファイルサイズが増える。既存の変換済みDICOMには
			// 遡って効果はなく、今後の取り込みにのみ適用される)
			java.util.List<ws.schild.jave.encode.EncodingArgument> currOptions = java.util.Arrays.asList(
					new ws.schild.jave.encode.ValueArgument(ws.schild.jave.encode.ArgType.OUTFILE, "-g",
							ea -> java.util.Optional.of(String.valueOf(GOP_SIZE_FRAMES))),
					new ws.schild.jave.encode.ValueArgument(ws.schild.jave.encode.ArgType.OUTFILE, "-bf",
							ea -> java.util.Optional.of("0")));

			ws.schild.jave.Encoder encoder = new ws.schild.jave.Encoder();
			encoder.encode(java.util.Collections.singletonList(multimediaObject), tempMp4, encodingAttrs, new EncoderProgressListener() {
				@Override
				public void sourceInfo(ws.schild.jave.info.MultimediaInfo info) {}

				@Override
				public void progress(int permil) {
					if (listener != null) {
						// 0〜30%をH.264トランスコードの進捗として割り当てる
						int percent = (int) ((permil / 1000.0) * 30.0);
						listener.onProgress(percent, "Transcoding to H.264 format: " + (permil / 10) + "%");
					}
				}

				@Override
				public void message(String message) {}
			}, currOptions);

			// 変換後のMP4ファイルからメタ情報を取得
			ws.schild.jave.MultimediaObject mp4Object = new ws.schild.jave.MultimediaObject(tempMp4);
			ws.schild.jave.info.MultimediaInfo mp4Info = mp4Object.getInfo();
			ws.schild.jave.info.VideoInfo mp4VideoInfo = mp4Info.getVideo();
			
			if (listener != null) {
				listener.onProgress(30, "MP4 conversion completed. Preparing for DICOM encapsulation...");
			}

			long mp4FileSize = tempMp4.length();
			long DICOM_SAFE_MAX_SIZE = 4000000000L; // 約3.7GB

			if (mp4FileSize > DICOM_SAFE_MAX_SIZE) {
			    throw new IllegalArgumentException("The compressed file size (" + (mp4FileSize / 1024 / 1024) + " MB) exceeds the DICOM limit (4GB). Please split the video.");
			}

			int w = mp4VideoInfo.getSize().getWidth();
			int h = mp4VideoInfo.getSize().getHeight();
			double fps = mp4VideoInfo.getFrameRate();
			long durationMs = mp4Info.getDuration();
			double durationSec = durationMs / 1000.0;
			int frames = (int) Math.rint(durationSec * fps);

			DicomObject dcmHeader = createMpegDicomHeader(w, h, frames, fps, durationSec, seriesNumber, instanceNumber,
					m, patName, patID, sex, dob, studyUID, studyID, studyDesc, studyDate, studyTime, contentDate, contentTime, seriesDesc);

			String sopInstanceUID = dcmHeader.getString(Tag.SOP​Instance​UID);
			File outputFile = new File(tempDir, sopInstanceUID + ".dcm");

			// DICOM規格の MPEG-4 AVC/H.264 Transfer Syntax
			String transferSyntax = "1.2.840.10008.1.2.4.102"; 
			DicomWriter writer = DicomWriter.newDicomWriter(backend);
			writer.write(dcmHeader, transferSyntax, outputFile.getAbsolutePath());

			// ★ ストリームカプセル化処理（ここで30% -> 100%まで進捗を刻みます）
			appendMpegDataStream(outputFile, tempMp4, listener);

			if (listener != null) {
				listener.onProgress(100, "DICOM conversion of MPEG video completed.");
			}

		} finally {
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
	private static void appendMpegDataStream(File outputFile, File mp4File, ProgressListener listener) throws Exception {
		long videoLength = mp4File.length();
		boolean needsPadding = (videoLength % 2 != 0); 
		long itemLength = needsPadding ? videoLength + 1 : videoLength;

		try (FileOutputStream fos = new FileOutputStream(outputFile, true);
			 BufferedOutputStream bos = new BufferedOutputStream(fos);
			 FileInputStream fis = new FileInputStream(mp4File)) {

			bos.write(0xE0); bos.write(0x7F); bos.write(0x10); bos.write(0x00);
			bos.write('O');  bos.write('B');  bos.write(0x00); bos.write(0x00);
			bos.write(0xFF); bos.write(0xFF); bos.write(0xFF); bos.write(0xFF);
			bos.write(0xFE); bos.write(0xFF); bos.write(0x00); bos.write(0xE0); 
			bos.write(0x00); bos.write(0x00); bos.write(0x00); bos.write(0x00); 
			bos.write(0xFE); bos.write(0xFF); bos.write(0x00); bos.write(0xE0);
			bos.write((int) (itemLength & 0xFF));
			bos.write((int) ((itemLength >> 8) & 0xFF));
			bos.write((int) ((itemLength >> 16) & 0xFF));
			bos.write((int) ((itemLength >> 24) & 0xFF));

			byte[] buffer = new byte[8192];
			int bytesRead;
			long copiedBytes = 0;
			
			// ★ ストリーム書き込みの進捗を報告
			while ((bytesRead = fis.read(buffer)) != -1) {
				bos.write(buffer, 0, bytesRead);
				copiedBytes += bytesRead;
				
				if (listener != null) {
					// 30%からスタートし、残り70%分を割り当てる計算
					int percent = 30 + (int) ((copiedBytes * 70.0) / videoLength);
					listener.onProgress(percent, "DICOMへ動画データをカプセル化中...");
				}
			}

			//奇数長の場合
			if (needsPadding) {
				bos.write(0x00);
			}

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
			java.util.Date contentTime, String seriesDesc, ProgressListener listener) throws Exception {

		if (listener != null) listener.onProgress(0, "動画ファイルの読み込みと解析を行っています...");

		VideoReader reader = VideoReader.load(videoFile);
		if (reader == null) {
			throw new IllegalArgumentException("Unsupported video format: " + videoFile.getName());
		}

		ImagePlus imp = reader.read(); 
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

		String seriesInstanceUID = UIDUtils.createUID();
		long DICOM_SAFE_MAX_SIZE = 4000000000L; 
		int bytesPerPixel = isColor ? 3 : 1;
		long bytesPerFrame = (long) w * h * bytesPerPixel;
		
		int maxFramesPerFile = (int) (DICOM_SAFE_MAX_SIZE / bytesPerFrame);
		if (maxFramesPerFile < 1) {
			imp.close();
			throw new IllegalArgumentException("1フレームのデータサイズが大きすぎます（DICOM仕様上限超過）。");
		}

		int currentFrameOffset = 0;
		int fileIndex = 0; 

		try {
			while (currentFrameOffset < totalFrames) {
				int chunkFrames = Math.min(maxFramesPerFile, totalFrames - currentFrameOffset);
				long chunkPixelBytes = bytesPerFrame * chunkFrames;
				double chunkDuration = Math.rint(chunkFrames * fps);

				DicomObject dcmHeader = createDicomHeader(w, h, chunkFrames, isColor, bits, fps, chunkDuration, seriesNumber,
						instanceNumber + fileIndex, m, patName, patID, sex, dob, studyUID, studyID, studyDesc, studyDate, studyTime,
						contentDate, contentTime, seriesDesc, seriesInstanceUID);

				String sopInstanceUID = dcmHeader.getString(Tag.SOP​Instance​UID);
				File outputFile = new File(tempDir, sopInstanceUID + ".dcm");

				DicomWriter writer = DicomWriter.newDicomWriter(backend);
				writer.write(dcmHeader, UID.ImplicitVRLittleEndian.uid(), outputFile.getAbsolutePath());

				try {
					// ★ 進捗計算用に totalFrames と currentFrameOffset を渡す
					appendPixelDataStreamChunk(outputFile, imp, w, h, chunkFrames, isColor, chunkPixelBytes, currentFrameOffset, totalFrames, listener);
				} catch (Exception e) {
					outputFile.delete(); 
					throw e;
				}

				currentFrameOffset += chunkFrames;
				fileIndex++;
			}
			
			if (listener != null) {
				listener.onProgress(100, "非圧縮動画のDICOM変換が完了しました。");
			}
			
		} finally {
			imp.close(); 
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
	private static void appendPixelDataStreamChunk(File outputFile, ImagePlus imp, int w, int h, int chunkFrames, boolean isColor, long chunkPixelBytes, int startFrameOffset, int totalFrames, ProgressListener listener) throws Exception {
		boolean needsPadding = chunkPixelBytes % 2 != 0;
		long dicomLength = needsPadding ? chunkPixelBytes + 1 : chunkPixelBytes;

		int frameSize = w * h * (isColor ? 3 : 1);
		byte[] frameBuf = isColor ? new byte[frameSize] : null;

		try (FileOutputStream fos = new FileOutputStream(outputFile, true);
				BufferedOutputStream bos = new BufferedOutputStream(fos)) {

			bos.write(0xE0);
			bos.write(0x7F);
			bos.write(0x10);
			bos.write(0x00);
			
			bos.write((int) (dicomLength & 0xFF));
			bos.write((int) ((dicomLength >> 8) & 0xFF));
			bos.write((int) ((dicomLength >> 16) & 0xFF));
			bos.write((int) ((dicomLength >> 24) & 0xFF));

			for (int k = 0; k < chunkFrames; k++) {
				
				// ★ 全体に対するフレームの進捗を計算して通知
				if (listener != null) {
					int currentGlobalFrame = startFrameOffset + k + 1;
					int percent = (int) ((currentGlobalFrame * 100.0) / totalFrames);
					listener.onProgress(percent, "フレーム抽出・書き込み中 (" + currentGlobalFrame + "/" + totalFrames + ")");
				}

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

			if (needsPadding) {
				bos.write(0x00);
			}
			bos.flush();
		}
	}
}