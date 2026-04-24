/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.imageio;

import java.io.File;

import org.jcodec.api.FrameGrab;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;

import com.vis.core.log.Log;

import ij.ImagePlus;
import ij.VirtualStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * Supported format;AVI (no-compress), MP4-H264, MP4-AVC, ISO BMF, MOV(MPEG-4 multimedia container file format developed by Apple)
 *  
 *  When avi file is inputed, it will be processed by IJ.
 *  
 * @author tatsunidas
 *
 */

/*
 * JAVE には統一できない。
 * JAVEはコマンドラインツールで、読み書き時の詳細な処理には対応しない。
 * そのかわり、単純なファイルへの読み書きはJCodecよりも数段速い。
 * 
 */

class VideoReaderJCodec implements VideoReader{
	
	private ImagePlus imp = null;
	private File video;
	private String fileName;
	private final String MIMETYPE;
	
	private int width = 0;
	private int height = 0;
	private int totalFrames = 0;
	private double fps = 0;

	public VideoReaderJCodec(String MIMETYPE, File video) {
		this.MIMETYPE = MIMETYPE;
		this.video = video;
		this.fileName = video.getName();
	}

	@Override
	public ImagePlus read() {
		/*
		 * 無害なエラーだが、非表示にする。
		 * [ERROR]	. (:0):	Broken atom of size 0
		 */
		org.jcodec.common.logging.Logger.setLevel(org.jcodec.common.logging.LogLevel.ERROR);
		FileChannelWrapper in = null;
		try {
			in = NIOUtils.readableChannel(video);
			FrameGrab grab = FrameGrab.createFrameGrab(in);
			
			// 1. 動画のメタデータ（フレーム数、FPS）を取得
			DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();
			totalFrames = meta.getTotalFrames();
			double totalDuration = meta.getTotalDuration();
			if (totalFrames > 0 && totalDuration > 0) {
				fps = totalFrames / totalDuration;
			}
			
			// 2. 最初のフレームを読み込んで縦横の解像度を取得
			Picture firstFrame = grab.getNativeFrame();
			if (firstFrame != null) {
				width = firstFrame.getWidth();
				height = firstFrame.getHeight();
			} else {
				Log.logger.severe("動画の解像度が取得できませんでした。");
				return null;
			}

			// 3. Virtual Stackの構築
			MpegVirtualStack vStack = new MpegVirtualStack(width, height, totalFrames, video);
			imp = new ImagePlus(fileName, vStack);
			imp.getCalibration().fps = fps;
			
			Log.logger.info("Virtual Stackとして動画をロードしました: " + totalFrames + " frames.");
			
		} catch (Exception e) {
			Log.logger.severe("MP4のVirtualStack構築に失敗しました: " + e.getMessage());
			imp = null;
		} finally {
			NIOUtils.closeQuietly(in);
		}
		return imp;
	}

	@Override
	public ImagePlus getImagePlus() {
		return imp;
	}

	@Override
	public int getNumOfFrames() {
		return totalFrames;
	}

	@Override
	public double getFrameRate() {
		return fps;
	}

	@Override
	public String mimeType() {
		return MIMETYPE;
	}

	// =========================================================================
	// VirtualStackの内部クラス（表示時にオンデマンドでフレームを抽出する）
	// =========================================================================
	class MpegVirtualStack extends VirtualStack {
		private File videoFile;

		public MpegVirtualStack(int width, int height, int size, File videoFile) {
			super(width, height, size);
			this.videoFile = videoFile;
		}

		/**
		 * ImageJから「n番目(1始まり)の画像」が要求された時に呼ばれる
		 */
		@Override
		public ImageProcessor getProcessor(int n) {
			FileChannelWrapper in = null;
			try {
				in = NIOUtils.readableChannel(videoFile);
				FrameGrab grab = FrameGrab.createFrameGrab(in);
				
				// VirtualStackは1始まりだが、JCodecのシークは0始まり
				grab.seekToFramePrecise(n - 1);
				Picture pic = grab.getNativeFrame();
				
				if (pic != null) {
					BufferedImage bi = AWTUtil.toBufferedImage(pic);
					
					// ★ 【デバッグ機能】最初のフレーム(1枚目)が要求された時、PNGとして保存する
//					if (n == 1) {
//						try {
//							// ユーザーのホームディレクトリ(Windowsなら C:\Users\ユーザー名) に保存
//							File debugFile = new File(System.getProperty("user.home"), "graphy_debug_frame1.png");
//							javax.imageio.ImageIO.write(bi, "png", debugFile);
//							Log.logger.info("【デバッグ】フレーム1のピクセル抽出に成功しました。画像を保存しました: " + debugFile.getAbsolutePath());
//						} catch (Exception ex) {
//							Log.logger.warning("デバッグ画像の保存に失敗: " + ex.getMessage());
//						}
//					}
					
					return new ColorProcessor(bi);
//					int type = bi.getType();
//					if (type == BufferedImage.TYPE_BYTE_GRAY || type == BufferedImage.TYPE_BYTE_BINARY) {
//						return new ByteProcessor(bi);
//					} else if (type == BufferedImage.TYPE_USHORT_GRAY) {
//						return new ShortProcessor(bi);
//					} else {
//						return new ColorProcessor(bi);
//					}
				}
			} catch (Exception e) {
				Log.logger.warning("フレーム抽出に失敗しました (Frame " + n + "): " + e.getMessage());
			} finally {
				// リソースリークを防ぐため確実に閉じる
				NIOUtils.closeQuietly(in);
			}
			
			// 抽出失敗時は黒い画像を返してクラッシュを防ぐ
			return new ColorProcessor(getWidth(), getHeight());
		}
	}
}
