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

		/*
		 * ★ 連番アクセス高速化用のデコーダ状態キャッシュ。
		 * JCodecのFrameGrabは「直近のキーフレーム（GOP構造によっては実質フレーム0しかない）から
		 * 対象フレームまで毎回デコードし直す」ため、毎呼び出しごとに新規FrameGrabを作って
		 * seekToFramePrecise()するだけでは、後方のフレームほど線形にコストが増大する
		 * (=2D Viewer起動直後のプリフェッチやAutoWindowでフリーズする原因)。
		 * 直前にデコードしたフレーム番号とFrameGrabを保持し、前方への連番/近傍アクセスを
		 * 「続きから少し進めるだけ」にすることで、その場合のコストをO(1)〜O(差分)に抑える。
		 * 後方アクセスや初回は従来通りseekToFramePreciseで再構築する（挙動は変えない）。
		 */
		// ★ 前方ジャンプをシークせず逐次デコードで続ける距離の上限。
		// VideoToDicomConverterでのエンコード時GOPサイズ(30)の目安に対して余裕を持たせた値。
		// これを超える距離の前方ジャンプは、逐次デコードよりシークの方が確実に速い。
		private static final int MAX_FORWARD_CONTINUE_FRAMES = 60;

		private FileChannelWrapper persistentChannel;
		private FrameGrab persistentGrab;
		private int lastDecodedFrame = -1; // 0-based. -1 = 未デコード
		// ★ 直前に正常デコードできた画像。範囲外アクセスやデコード失敗時のフォールバックに使う
		// (真っ黒な画像を返すより、直前のフレームを表示し続ける方がユーザーへの混乱が少ない)
		private BufferedImage lastGoodImage;

		public MpegVirtualStack(int width, int height, int size, File videoFile) {
			super(width, height, size);
			this.videoFile = videoFile;
		}

		/**
		 * ImageJから「n番目(1始まり)の画像」が要求された時に呼ばれる
		 */
		@Override
		public synchronized ImageProcessor getProcessor(int n) {
			// VirtualStackは1始まりだが、JCodecのシークは0始まり
			int target = n - 1;

			// ★ DICOMヘッダのNumberOfFramesは動画から推定した値(duration*fps)であり、
			// 実際にJCodecが復号できるフレーム数(=getSize())と食い違うことがある。
			// 食い違った状態で範囲外のフレームをseek/decodeしようとすると、EOFに達して
			// 何も得られず「真っ黒」になる（スライダーで大きいインデックスへ移動した時の症状）。
			// ここで実際に存在する範囲にクランプし、黒画面化を防ぐ。
			int maxFrame = getSize() - 1;
			if (target > maxFrame) {
				target = maxFrame;
			}
			if (target < 0) {
				target = 0;
			}

			try {
				Picture pic;
				if (persistentGrab != null && target == lastDecodedFrame + 1) {
					// ★ 直前のフレームの続き：シーク不要、1枚進めるだけ（高速パス）
					pic = persistentGrab.getNativeFrame();
				} else if (persistentGrab != null && target > lastDecodedFrame
						&& target - lastDecodedFrame <= MAX_FORWARD_CONTINUE_FRAMES) {
					// ★ 前方への近傍ジャンプ：既存のデコーダ位置から差分だけ読み進める
					// (フレーム0からの再デコードを避けられる)
					for (int f = lastDecodedFrame + 1; f < target; f++) {
						persistentGrab.getNativeFrame();
					}
					pic = persistentGrab.getNativeFrame();
				} else {
					// ★ 後方アクセス、初回アクセス、または遠方への前方ジャンプ(スライダーでの大きな
					// ジャンプ等)：再オープンしてシークする。
					// 前方の遠距離ジャンプを↑の分岐に含めてしまうと、間の全フレームを律儀に
					// 逐次デコードすることになり、GOP構造を無視したO(距離)のコストになってしまう
					// (スライダー操作が極端に重くなる原因だった)。距離がGOPサイズの目安を超えたら
					// 後方ジャンプと同じくシークに切り替え、コストをO(GOPサイズ)に抑える。
					closePersistent();
					persistentChannel = NIOUtils.readableChannel(videoFile);
					persistentGrab = FrameGrab.createFrameGrab(persistentChannel);
					persistentGrab.seekToFramePrecise(target);
					pic = persistentGrab.getNativeFrame();
				}

				if (pic != null) {
					lastDecodedFrame = target;
					BufferedImage bi = AWTUtil.toBufferedImage(pic);
					lastGoodImage = bi;
					return new ColorProcessor(bi);
				}
				// ★ デコーダがEOF等でnullを返した場合は状態をリセットし、次回は再構築させる
				closePersistent();
				lastDecodedFrame = -1;
			} catch (Exception e) {
				Log.logger.warning("フレーム抽出に失敗しました (Frame " + n + "): " + e.getMessage());
				// ★ デコーダの状態が壊れている可能性があるため、次回アクセス時に再構築させる
				closePersistent();
				lastDecodedFrame = -1;
			}

			// ★ 抽出失敗時は、可能なら直前に成功した画像を返す（真っ黒よりはるかに分かりやすい）。
			// 一度も成功していない場合のみ、クラッシュ防止のため黒画像を返す。
			if (lastGoodImage != null) {
				return new ColorProcessor(lastGoodImage);
			}
			return new ColorProcessor(getWidth(), getHeight());
		}

		private void closePersistent() {
			NIOUtils.closeQuietly(persistentChannel);
			persistentChannel = null;
			persistentGrab = null;
		}
	}
}
