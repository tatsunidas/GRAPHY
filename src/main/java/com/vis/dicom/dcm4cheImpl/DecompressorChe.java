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
package com.vis.dicom.dcm4cheImpl;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.Value;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.imageio.codec.jpeg.PatchJPEGLS;
import org.dcm4che3.imageio.codec.jpeg.PatchJPEGLSImageInputStream;
import org.dcm4che3.imageio.stream.SegmentedInputImageStream;
import org.dcm4che3.io.DicomEncodingOptions;
import org.dcm4che3.io.DicomOutputStream;

import com.vis.core.log.Log;
import com.vis.core.util.Utils;
import com.vis.dicom.DicomObject;
import com.vis.dicom.image.DicomImage;
import com.vis.imageio.VideoReader;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * 
 * @author tatsunidas
 *
 */
public class DecompressorChe implements com.vis.imageio.Decompressor {

	public DicomImage dcmImg;
	public final Attributes dataset;
	protected final String tsuid;
	protected final TransferSyntaxType tstype;
	protected Fragments pixeldataFragments;
	protected File file;
	protected int rows;
	protected int cols;
	protected int samples;
	protected PhotometricInterpretation pmi;
	protected PhotometricInterpretation pmiAfterDecompression;
	protected int bitsAllocated;
	protected int bitsStored;
	protected boolean banded;
	protected boolean signed;
	protected int frames;
	protected int frameLength;
	protected int length;
	protected BufferedImage bi;
	protected ImageReader decompressor;
	protected ImageReadParam readParam;
	protected PatchJPEGLS patchJpegLS;
	protected ImageDescriptor imageDescriptor;

	protected File tempMpegFile;
	protected ImagePlus mpegVirtualStack;
	protected boolean isMpeg;
	
	private static java.util.concurrent.ConcurrentHashMap<String, ImagePlus> globalMpegStackCache = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * to use method decompress(File from, File to)
	 */
	public DecompressorChe() {
		this.dataset = null;
		this.tsuid = null;
		this.tstype = null;

	};

	/**
	 * Default(Generally used)
	 * 
	 * @param dcmImg
	 */
	public DecompressorChe(DicomImage dcmImg) {
		if (dcmImg == null)
			throw new NullPointerException("DicomImage is Null...");
		this.dcmImg = dcmImg;
		this.dataset = (Attributes) dcmImg.getHeader();
		this.tsuid = dcmImg.getTSUID().uid();
		this.tstype = TransferSyntaxType.forUID(tsuid);
		Log.logger.fine("DEBUG: Attempting to decompress TSUID: " + this.tsuid);
		init(this.dataset, this.tsuid);
	}

	public DecompressorChe(Attributes dataset, String tsuid) {
		if (tsuid == null) {
			throw new NullPointerException("tsuid");
		}
		if (dataset == null) {
			throw new NullPointerException("DicomObject is Null...");
		}
		this.dataset = dataset;
		this.tsuid = tsuid;
		this.tstype = TransferSyntaxType.forUID(tsuid);
		// System.out.println("DEBUG: Attempting to decompress TSUID: " + this.tsuid);
		init(this.dataset, this.tsuid);
	}

	private void init(Attributes dataset, String tsuid) {
		Object pixeldata = dataset.getValue(Tag.PixelData);
		if (pixeldata == null)
			return;

		if (tstype == null)
			throw new IllegalArgumentException("Unknown Transfer Syntax: " + tsuid);
		this.rows = dataset.getInt(Tag.Rows, 0);
		this.cols = dataset.getInt(Tag.Columns, 0);
		this.samples = dataset.getInt(Tag.SamplesPerPixel, 0);
		this.pmi = PhotometricInterpretation
				.fromString(dataset.getString(Tag.PhotometricInterpretation, "MONOCHROME2"));
		this.pmiAfterDecompression = pmi;
		this.bitsAllocated = dataset.getInt(Tag.BitsAllocated, 8);
		this.bitsStored = dataset.getInt(Tag.BitsStored, bitsAllocated);
		this.banded = dataset.getInt(Tag.PlanarConfiguration, 0) != 0;
		this.signed = dataset.getInt(Tag.PixelRepresentation, 0) != 0;
		this.frames = dataset.getInt(Tag.NumberOfFrames, 1);
		this.frameLength = rows * cols * samples * bitsAllocated / 8;
		this.length = frameLength * frames;
		this.imageDescriptor = new ImageDescriptor(dataset);

		// MPEG形式かどうかの判定 (H.264系など)
		this.isMpeg = tsuid.startsWith("1.2.840.10008.1.2.4.10");

		if (pixeldata instanceof Fragments) {
			if (!tstype.isPixeldataEncapsulated())
				throw new IllegalArgumentException("Encapusulated Pixel Data" + "with Transfer Syntax: " + tsuid);
			this.pixeldataFragments = (Fragments) pixeldata;

			int numFragments = pixeldataFragments.size();
			
			// ★ 修正：MPEGでもJPEGでも、抽出を行う前に大元のDICOMファイルの参照をセットしておく！
			if (numFragments > 1) {
				this.file = ((BulkData) pixeldataFragments.get(1)).getFile();
			}
			
			// ★ 分岐処理：MPEGの場合とそれ以外（JPEG等）で分ける
			if (isMpeg) {
				// ビューワ(Praparat)の誤認を防ぐため、メモリ上のタグを強制的に「RGB」に書き換える！
				dataset.setString(Tag.PhotometricInterpretation, VR.CS, "RGB");
				dataset.setInt(Tag.PlanarConfiguration, VR.US, 0);
				Log.logger.fine("MPEG DICOMを検出しました。一時ファイルへの抽出とVirtual Stackの構築を開始します。");
				this.pmiAfterDecompression = PhotometricInterpretation.RGB;
				try {
					this.tempMpegFile = extractMpegToTempFile();
					if (this.tempMpegFile != null) {
						String cacheKey = this.tempMpegFile.getAbsolutePath();

						// ★ キャッシュの確認（2回目以降は必ずここを通るため、処理時間ゼロになります）
						if (globalMpegStackCache.containsKey(cacheKey)) {
							this.mpegVirtualStack = globalMpegStackCache.get(cacheKey);
						} else {
							// 初回のみ VideoReader で重い初期化処理を行い、キャッシュに保存する
							VideoReader vReader = VideoReader.load(this.tempMpegFile);
							if (vReader != null) {
								this.mpegVirtualStack = vReader.read();
								globalMpegStackCache.put(cacheKey, this.mpegVirtualStack);
								Log.logger.info("MPEG Virtual Stack を新規構築・キャッシュしました: " + this.mpegVirtualStack.getNSlices() + " frames");
							}
						}
					}
				} catch (IOException e) {
					Log.logger.severe("MPEGの抽出またはロードに失敗しました: " + e.getMessage());
				}
			} else {
				// 従来のJPEG等の場合の処理
				if (frames == 1 ? (numFragments < 2) : (numFragments != frames + 1))
					throw new IllegalArgumentException(
							"Number of Pixel Data Fragments: " + numFragments + " does not match " + frames);

				this.file = ((BulkData) pixeldataFragments.get(1)).getFile();
				ImageReaderFactory.ImageReaderParam param = ImageReaderFactory.getImageReaderParam(tsuid);
				if (param == null) {
					Log.logger.severe("ImageReaderParam が取得できません。対応するデコーダがありません: " + tsuid);
				} else {
					this.decompressor = ImageReaderFactory.getImageReader(param);
					Log.logger.fine("使用するデコーダ: " + decompressor.getClass().getName());
					this.readParam = decompressor.getDefaultReadParam();
					this.patchJpegLS = param.patchJPEGLS;
					this.pmiAfterDecompression = pmi.isYBR() && TransferSyntaxType.isYBRCompression(tsuid)
							? PhotometricInterpretation.RGB
							: pmi;
				}
			}
		} else {
			this.file = ((BulkData) pixeldata).getFile();
		}
		if (!isMpeg) {
			ImageReaderFactory.ImageReaderParam param = ImageReaderFactory.getImageReaderParam(tsuid);
			if (param == null) {
				Log.logger.severe("ImageReaderParam が取得できません。対応するデコーダがありません: " + tsuid);
			} else {
				this.decompressor = ImageReaderFactory.getImageReader(param);
				Log.logger.fine("使用するデコーダ: " + decompressor.getClass().getName());
			}
		}
	}
	
	/**
	 * MPEG DICOMのフラグメントから、動画データを一時MP4ファイルとして抽出します。
	 * ★ スマートキャッシュ機構：SOPInstanceUID + ピクセルデータサイズのハイブリッドキーで
	 * 高速なシークと、変更検知（マスキング後の確実な上書き）を両立します。
	 */
	public File extractMpegToTempFile() throws IOException {
		if (pixeldataFragments == null || pixeldataFragments.size() < 2) {
			return null;
		}

		// 1. キャッシュキーの生成: SOPInstanceUID ＋ ピクセルデータの総バイト数
		String sop = dataset.getString(Tag.SOPInstanceUID, "unknown_sop");
		long pixelSize = 0;
		for (Object frag : pixeldataFragments) {
		    if (frag instanceof byte[]) {
		        pixelSize += ((byte[]) frag).length;
		    } else if (frag instanceof org.dcm4che3.data.BulkData) {
		        // ★ 追加: BulkData（ファイル参照）の場合も長さを取得する
		        pixelSize += ((org.dcm4che3.data.BulkData) frag).length();
		    }
		}
		
		// ファイル名例: graphy_mpeg_1.2.3.4_10485760.mp4 (SOP + バイト数)
		String cacheFileName = "graphy_mpeg_" + sop + "_" + pixelSize + ".mp4";
		File tempMp4 = new File(System.getProperty("java.io.tmpdir"), cacheFileName);

		// 2. 爆速キャッシュリターン：ファイルが存在し、サイズ(内容)が変わっていなければ即座に返す
		if (tempMp4.exists() && tempMp4.length() > 0) {
			// スライダー操作時などはここを通過するため、I/O負荷ゼロで高速に動きます
			// Log.logger.fine("キャッシュを再利用します: " + tempMp4.getName()); // ログがうるさければ消してください
			return tempMp4;
		}

		// 3. 古いキャッシュのお掃除（同じSOPだけどバイト数が違う＝マスキング前の古いファイル）
		File tmpDir = new File(System.getProperty("java.io.tmpdir"));
		File[] oldCaches = tmpDir.listFiles((dir, name) -> 
				name.startsWith("graphy_mpeg_" + sop + "_") && !name.equals(cacheFileName));
		if (oldCaches != null) {
			for (File old : oldCaches) {
				old.delete(); // 古い動画キャッシュを安全に削除
			}
		}

		Log.logger.info("新しいMPEGストリームを抽出しています... (変更検知: " + tempMp4.getName() + ")");
		
		// 4. 初回、またはデータ変更時のみ抽出処理を行う
		try (FileOutputStream fos = new FileOutputStream(tempMp4);
			 ImageInputStream iis = createImageInputStream();
			 SegmentedInputImageStream siis = new SegmentedInputImageStream(iis, pixeldataFragments, 0)) {
			
			byte[] buffer = new byte[1048576]; 
			int bytesRead;
			while ((bytesRead = siis.read(buffer)) != -1) {
				fos.write(buffer, 0, bytesRead);
			}
			fos.flush();
		}
		
		// アプリケーション終了時にOSに自動削除を任せる
		tempMp4.deleteOnExit();
		return tempMp4;
	}

	public void dispose() {
		if (decompressor != null) {
			decompressor.dispose();
		}
		decompressor = null;
		
		// ★ MPEG用リソースの解放 (ファイルは削除しない！次回シーク時のために残す)
		if (mpegVirtualStack != null) {
			mpegVirtualStack.close();
			mpegVirtualStack = null;
		}
		// ※ tempMpegFile.delete() は絶対に呼ばないように削除してください！
	}

	public boolean decompress() {
		if (decompressor == null)
			return false;

		if (tstype == TransferSyntaxType.RLE)
			bi = createBufferedImage(bitsStored, true, signed);

		/**
		 * VR is always OW.(even if set OB as VR, return 16 bit volume byte array.)
		 */
		toDecompressable();
		Value bulk = (Value) dataset.getValue(Tag.PixelData);
		try {
			byte[] bulkBytes = bulk.toBytes(VR.OW, dataset.bigEndian());
			dataset.setValue(Tag.PixelData, VR.OW, bulkBytes);
			if (dcmImg != null) {
				dcmImg.decompressed(true);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		if (samples > 1) {
			dataset.setString(Tag.PhotometricInterpretation, VR.CS, pmiAfterDecompression.toString());
			dataset.setInt(Tag.PlanarConfiguration, VR.US, tstype.getPlanarConfiguration());
		}
		return true;
	}

	/**
	 * 指定したフレームを解凍し、その生ピクセルデータを byte[] として返します。
	 */
	public byte[] decompress(int frameIndex) {
		// MPEGではない従来画像の場合のみ、デコーダの存在チェックを行う
		if (!isMpeg && (decompressor == null || file == null)) {
			Log.logger.severe("Decompressor または File が null です。");
			return null;
		}

		try {
			BufferedImage decompressedBi;
			
			if (isMpeg) {
				// ★ MPEGの場合は ImageInputStream を開く必要がないので null を渡す
				decompressedBi = decompressFrame(null, frameIndex);
			} else {
				// ★ 従来(JPEG等)はストリームを開いて読み込む
				try (ImageInputStream iis = createImageInputStream()) {
					decompressedBi = decompressFrame(iis, frameIndex);
				}
			}

			if (decompressedBi == null) {
				Log.logger.severe("decompressFrame が null を返しました。");
				return null;
			}

			// Raster から生ピクセルデータを抽出して byte[] 化
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				writeTo(decompressedBi.getRaster(), baos);
				return baos.toByteArray();
			}

		} catch (IOException e) {
			Log.logger.severe("decompressFrameToBytes で例外が発生しました: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * MPEG動画から直接ImageProcessor(ColorProcessor)を抽出して返します。
	 * byte[]へのシリアライズとPixelDataDecoderでの再構築をスキップする高速ルートです。
	 */
//	public ImageProcessor getImageProcessorFromMpeg(int frameIndex/*0 to - N-1*/) {
//		if (isMpeg && mpegVirtualStack != null) {
//			mpegVirtualStack.setSlice(frameIndex + 1);
//			ImageProcessor ip = mpegVirtualStack.getProcessor();
//			
//			// 万が一白黒判定されても、強制的にColorProcessorの器に包み直す
//			if (!(ip instanceof ColorProcessor)) {
//				return new ColorProcessor(ip.getBufferedImage());
//			}
//			return ip;
//		}
//		return null;
//	}
	public ImageProcessor getImageProcessorFromMpeg(int frameIndex/* zct 0 to - N-1 */) {
		if (isMpeg && mpegVirtualStack != null) {
			// 複数スレッド(先読みと描画)からの同時アクセスによる競合を防ぐ
			synchronized (mpegVirtualStack) {
				// ★修正2: setSlice() はUIイベントを誘発して重いため、Stackから直接取得する
				ImageProcessor ip = mpegVirtualStack.getStack().getProcessor(frameIndex + 1);
				// ★修正3(最重要): VirtualStackは同じインスタンスを使い回すため、必ず複製(duplicate)する。
				// これをしないと全フレームが最後に読み込んだ画像に上書きされてしまいます。
				ImageProcessor copyIp = ip.duplicate();
				// 万が一白黒判定されても、強制的にColorProcessorの器に包み直す
				if (!(copyIp instanceof ColorProcessor)) {
					return new ColorProcessor(copyIp.getBufferedImage());
				}
				return copyIp;
			}
		}
		return null;
	}

	public boolean toDecompressable() {
		if (decompressor == null)
			return false;

		if (tstype == TransferSyntaxType.RLE)
			bi = createBufferedImage(bitsStored, true, signed);

		/**
		 * VR is always OW.(even if set OB as VR, return 16 bit volume byte array.)
		 */
		// VR vr = dataset.getVR(Tag.PixelData);
		dataset.setValue(Tag.PixelData, VR.OW, new Value() {

			@Override
			public boolean isEmpty() {
				return false;
			}

			@Override
			public byte[] toBytes(VR vr, boolean bigEndian) throws IOException {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				DecompressorChe.this.writeTo(out);
				return out.toByteArray();
			}

			@Override
			public void writeTo(DicomOutputStream out, VR vr) throws IOException {
				DecompressorChe.this.writeTo(out);
			}

			@Override
			public int calcLength(DicomEncodingOptions encOpts, boolean explicitVR, VR vr) {
				return getEncodedLength(encOpts, explicitVR, vr);
			}

			@Override
			public int getEncodedLength(DicomEncodingOptions encOpts, boolean explicitVR, VR vr) {
				return (length + 1) & ~1;
			}
		});
		if (samples > 1) {
			dataset.setString(Tag.PhotometricInterpretation, VR.CS, pmiAfterDecompression.toString());
			dataset.setInt(Tag.PlanarConfiguration, VR.US, tstype.getPlanarConfiguration());
		}
		return true;
	}

	public void decompress(File src, File target) {
		DicomReaderChe reader = new DicomReaderChe(src.getAbsolutePath(), true);
		DicomObjectChe obj = (DicomObjectChe) reader.getHeader();
		new DecompressorChe(obj, reader.checkTSUID().uid()).toDecompressable();
		DicomWriterChe writer = new DicomWriterChe();
		writer.writeDicomImage((DicomObject) obj,
				(DicomObject) obj.createFileMetaInformation(UID.ImplicitVRLittleEndian), target.getAbsolutePath(),
				true);
	}

	protected BufferedImage createBufferedImage(int bitsStored, boolean banded, boolean signed) {
		int dataType = bitsAllocated > 8 ? (signed ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT)
				: DataBuffer.TYPE_BYTE;
		ComponentColorModel cm = samples == 1
				? new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { bitsStored }, false, // hasAlpha
						false, // isAlphaPremultiplied,
						Transparency.OPAQUE, dataType)
				: new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
						new int[] { bitsStored, bitsStored, bitsStored }, false, // hasAlpha
						false, // isAlphaPremultiplied,
						Transparency.OPAQUE, dataType);

		SampleModel sm = banded ? new BandedSampleModel(dataType, cols, rows, samples)
				: new PixelInterleavedSampleModel(dataType, cols, rows, samples, cols * samples, bandOffsets());
		WritableRaster raster = Raster.createWritableRaster(sm, null);
		return new BufferedImage(cm, raster, false, null);
	}

	private int[] bandOffsets() {
		int[] offsets = new int[samples];
		for (int i = 0; i < samples; i++)
			offsets[i] = i;
		return offsets;
	}

	public void writeTo(OutputStream out) throws IOException {
		ImageInputStream iis = createImageInputStream();
		try {
			for (int i = 0; i < frames; ++i)
				writeFrameTo(iis, i, out);
			if ((length & 1) != 0)
				out.write(0);
		} finally {
			try {
				iis.close();
			} catch (IOException ignore) {
			}
			decompressor.dispose();
		}
	}

	public FileImageInputStream createImageInputStream() throws IOException {
		return new FileImageInputStream(file);
	}

	public void writeFrameTo(ImageInputStream iis, int frameIndex, OutputStream out) throws IOException {
		writeTo(decompressFrame(iis, frameIndex).getRaster(), out);
	}

	protected BufferedImage decompressFrame(ImageInputStream iis, int index) throws IOException {
		// ★ MPEGの場合は、構築済みのVirtual Stackから指定フレームを取り出して返す
		if (isMpeg && mpegVirtualStack != null) {
			mpegVirtualStack.setSlice(index + 1); // ImageJのSliceは1始まり
			ImageProcessor ip = mpegVirtualStack.getProcessor();
			
			// AutoWindow等の処理を邪魔しないよう、標準的なBufferedImageに変換して返す
			return ip.getBufferedImage(); 
		}

		// --- 従来（JPEG等）の解凍処理 ---
		SegmentedInputImageStream siis = new SegmentedInputImageStream(iis, pixeldataFragments, index);
		siis.setImageDescriptor(imageDescriptor);
		decompressor.setInput(patchJpegLS != null ? new PatchJPEGLSImageInputStream(siis, patchJpegLS) : siis);
		readParam.setDestination(bi);
		long start = System.currentTimeMillis();
		bi = decompressor.read(0, readParam);
		long end = System.currentTimeMillis();
		if (Utils.isDebug) {
			String msg = "Decompressed frame " + (index + 1) + " in " + (end - start) + " ms";
			Log.logger.info(msg);
		}

		return bi;
	}

	static int sizeOf(BufferedImage bi) {
		DataBuffer db = bi.getData().getDataBuffer();
		return db.getSize() * db.getNumBanks() * (DataBuffer.getDataTypeSize(db.getDataType()) >>> 3);
	}

	private void writeTo(Raster raster, OutputStream out) throws IOException {
		SampleModel sm = raster.getSampleModel();
		DataBuffer db = raster.getDataBuffer();
		switch (db.getDataType()) {
		case DataBuffer.TYPE_BYTE:
			writeTo(sm, ((DataBufferByte) db).getBankData(), out);
			break;
		case DataBuffer.TYPE_USHORT:
			writeTo(sm, ((DataBufferUShort) db).getData(), out);
			break;
		case DataBuffer.TYPE_SHORT:
			writeTo(sm, ((DataBufferShort) db).getData(), out);
			break;
		case DataBuffer.TYPE_INT:
			writeTo(sm, ((DataBufferInt) db).getData(), out);
			break;
		default:
			throw new UnsupportedOperationException("Unsupported Datatype: " + db.getDataType());
		}
	}

	private void writeTo(SampleModel sm, byte[][] bankData, OutputStream out) throws IOException {
		int h = sm.getHeight();
		int w = sm.getWidth();
		ComponentSampleModel csm = (ComponentSampleModel) sm;
		int len = w * csm.getPixelStride();
		int stride = csm.getScanlineStride();
		if (csm.getBandOffsets()[0] != 0)
			bgr2rgb(bankData[0]);
		if (imageDescriptor.getBitsAllocated() == 16) {
			byte[] buf = new byte[len << 1];
			int j0 = 0;
			if (out instanceof DicomOutputStream) {
				j0 = ((DicomOutputStream) out).isBigEndian() ? 1 : 0;
			}
			for (byte[] b : bankData)
				for (int y = 0, off = 0; y < h; ++y, off += stride) {
					out.write(to16BitsAllocated(b, off, len, buf, j0));
				}
		} else {
			for (byte[] b : bankData)
				for (int y = 0, off = 0; y < h; ++y, off += stride)
					out.write(b, off, len);
		}
	}

	private byte[] to16BitsAllocated(byte[] b, int off, int len, byte[] buf, int j0) {
		for (int i = 0, j = j0; i < len; i++, j++, j++) {
			buf[j] = b[off + i];
		}
		return buf;
	}

	private static void bgr2rgb(byte[] bs) {
		for (int i = 0, j = 2; j < bs.length; i += 3, j += 3) {
			byte b = bs[i];
			bs[i] = bs[j];
			bs[j] = b;
		}
	}

	private static void writeTo(SampleModel sm, short[] data, OutputStream out) throws IOException {
		int h = sm.getHeight();
		int w = sm.getWidth();
		int stride = ((ComponentSampleModel) sm).getScanlineStride();
		byte[] b = new byte[w * 2];
		for (int y = 0; y < h; ++y) {
			for (int i = 0, j = y * stride; i < b.length;) {
				short s = data[j++];
				b[i++] = (byte) s;
				b[i++] = (byte) (s >> 8);
			}
			out.write(b);
		}
	}

	private static void writeTo(SampleModel sm, int[] data, OutputStream out) throws IOException {
		int h = sm.getHeight();
		int w = sm.getWidth();
		int stride = ((SinglePixelPackedSampleModel) sm).getScanlineStride();
		byte[] b = new byte[w * 3];
		for (int y = 0; y < h; ++y) {
			for (int i = 0, j = y * stride; i < b.length;) {
				int s = data[j++];
				b[i++] = (byte) (s >> 16);
				b[i++] = (byte) (s >> 8);
				b[i++] = (byte) s;
			}
			out.write(b);
		}
	}
}
