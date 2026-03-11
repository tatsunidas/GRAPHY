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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;

import com.vis.core.util.ByteUtils;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.UID;
import com.vis.dicom.VR;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.PhotometricInterpretation;
import com.vis.imageio.Codec;
import com.vis.imageio.Decompressor;
import com.vis.imageio.PDFReader;
import com.vis.imageio.PixelDataDecoder;

import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * 
 * @author tatsunidas
 *
 */
public class DicomImageChe extends DicomObjectChe implements DicomImage{
	
	private static final long serialVersionUID = 1L;
	/**
	 * header, basically without pixels.
	 * After calling ensurePixelLoaded(),
	 * header may have pixels bulk.
	 */
	DicomObject header = null;
	DicomObject fmi = null;
	UID tsuid;
	boolean decompressed = false;
	final String filePath;
	
	public DicomImageChe(String path, boolean withPixel) {
		DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
		reader.read(path, withPixel);
		this.filePath = path;//null-able
		this.header = reader.getHeader();
		this.fmi = reader.getFileMetaInfomation();
		this.tsuid = reader.checkTSUID();
		reader = null;
	}
	
	public DicomImageChe(String path, DicomObject header, DicomObject fmi, UID tsUID) {
		this.filePath = path;//null-able
		if(header == null ) {
			return;
		}
		if(fmi == null) {
			fmi = (DicomObject)header.createFileMetaInformation(tsUID.uid());
		}
		this.header = header;
		this.fmi = fmi;
		this.tsuid = tsUID;
	}

	@Override
	public void decompressed(boolean decompressed) {
		this.decompressed = decompressed;
	}

	@Override
	public int getBitsAllocated() {
		return header.getInt(Tag.BitsAllocated, -1);
	}

	@Override
	public int getBitsStored() {
		return header.getInt(Tag.BitsStored, getBitsAllocated());
	}

	@Override
	public DicomObject getHeader() {
		return header;
	}

	@Override
	public DicomObject getFileMetaInfo() {
		return fmi;
	}

	@Override
	public int getHeight() {
		return header.getInt(Tag.Rows, 0);
	}

	/**
	 * return imageprocessor (without calibration)
	 * if you want calibrated imageplus, see also ImagePlusDicomTagTools.dcmImgToImagePlus. 
	 * 0 to N-1
	 */
	@Override
	public ImageProcessor getImageProcessor(int frame) {
		
		if (isPDF()) {
			PDFReader pdfReader = new PDFReader(new File(filePath));
			BufferedImage rgb = pdfReader.renderPDFPage(frame);
			if (rgb != null) {
				// BufferedImageをImageJのColorProcessorに変換
				return new ColorProcessor(rgb);
			}
			return null;
		}
		
		if(Codec.isCompressed(getTSUID())) {
			Decompressor d = Decompressor.newInstance(this);
			byte[] decompressed_raw = d.decompress(frame);
			PixelDataDecoder pdec = new PixelDataDecoder(this);
			return pdec.decodeDecompressedByte(decompressed_raw);
		}else {
			byte[] raw = getPixelData(frame);
			if(raw == null) {
				return null;
			}
			PixelDataDecoder pdec = new PixelDataDecoder(this);
			return pdec.decode(raw);
		}
	}
	
	@Override
	public int getNumOfFrames() {
		return header.getInt(Tag.NumberOfFrames, 1);
	}
	
	@Override
	public PhotometricInterpretation getPhotometricInterpletation() {
		return PhotometricInterpretation
				.fromString(header.getString(Tag.PhotometricInterpretation, "MONOCHROME2"));
	}

	/**
	 * non-compressed bulk
	 */
	public byte[] getNativePixelData(int frame) {
		
		Attributes attrs = (Attributes) this.header;
		int tag = Tag.PixelData;
		
		// 32/64bit float データのタグ切り替え
		if (attrs.contains(Tag.FloatPixelData)) {
			tag = Tag.FloatPixelData;
		}else if (attrs.contains(Tag.DoubleFloatPixelData)) {
			tag = Tag.DoubleFloatPixelData;
		}
		
		Object value = attrs.getValue(tag);
		if (value == null) {
			return null;
		}
		
		int w = getWidth();
		int h = getHeight();
		int samples = getSamples();
		int bits = getBitsAllocated();
		int frameLength = w * h * samples * (bits / 8);
		
		if(frame < 0 || frame > getNumOfFrames()) {
			return null;
		}
		
		// --- ケース1: Fragments (圧縮データ: JPEG/J2K等) ---
//	    if (value instanceof Fragments) {
//	        // 本来はCodec/Decompressorで解凍後に抜き出すべき。
//	        // 未解凍のまま抜き出す場合は、オフセットテーブルを考慮した結合が必要。
//	        // ここでは簡易的に1フラグメント1フレームと仮定する従来の挙動を安全にする例：
//	        Fragments frags = (Fragments) value;
//	        // 0番目は通常Basic Offset Tableなので、frame+1は概ね正しいが、
//	        // 1フレームが複数フラグメントに跨る場合はこのロジックは破綻します。
//	        Object frag = (frame + 1 < frags.size()) ? frags.get(frame + 1) : null;
//	        if (frag instanceof byte[]) return (byte[]) frag;
//	        if (frag instanceof BulkData) {
//	            try { return ((BulkData) frag).toBytes(org.dcm4che3.data.VR.OB, false); }
//	            catch (IOException e) { return null; }
//	        }
//	    }
		
		// --- BulkData (非圧縮: ファイルから必要な部分だけ読む) ---
		if (value instanceof BulkData) {
			BulkData bd = (BulkData) value;
			try {
				byte[] full = bd.toBytes(((Attributes)this.header).getVR(tag), bigEndian());
				// 必要なフレームのオフセットから読み込む
				int offset = (int) frame * frameLength;
				byte[] dest = new byte[frameLength];
				System.arraycopy(full, offset, dest, 0, frameLength);
				return dest;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
		
		// --- すでにメモリ上にある場合 (byte[] / Value) ---
		// attrs.getSafeBytes() は内部でマルチフレームの切り出しは行わないため自前で実施
		byte[] allPixels = attrs.getSafeBytes(tag);
		if (allPixels == null)
			return null;

		if (allPixels.length > frameLength) {
			byte[] dest = new byte[frameLength];
			System.arraycopy(allPixels, frame * frameLength, dest, 0, frameLength);
			return dest;
		}
		return allPixels;
	}
	
	@Override
	public byte[] getPixelData(int frame) {
		Attributes attrs = (Attributes) this.header;
		Object bulk = attrs.getValue(Tag.PixelData);
		if (!(bulk instanceof Fragments)) {
			// 非圧縮データの場合は前回のBulkData用ロジックへ
			return getNativePixelData(frame);
		}

		/*
		 * 20260113 以降のコードは未使用だが残す。 当初、圧縮ピクセルのbyte[]を取り出してから、 byte[]を解凍しようとしていたが、
		 * DecompressCheの実装に合わせて、 byte[]を取り出さずに、直接フレームインデックス指定で取り出すようにした。
		 */
		Fragments frags = (Fragments) bulk;

		// --- Basic Offset Table (BOT) の解析 ---
		Object botObj = frags.get(0);
		byte[] bot = null;
		if (botObj instanceof byte[]) {
			bot = (byte[]) botObj;
		} else if (botObj instanceof BulkData) {
			try {
				bot = ((BulkData) botObj).toBytes(org.dcm4che3.data.VR.OB, false);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// --- フラグメント範囲の特定 ---
		int numFrames = getNumOfFrames();
		int startFrag = findStartFragment(frags, bot, frame);
		int endFrag = findEndFragment(frags, bot, frame, numFrames);

		// --- 修正ポイント 2: フラグメントの結合 (combineFragments) ---
		return combineFragments(frags, startFrag, endFrag);
	}
	
	/**
	 * Fragmentsから安全にbyte[]を取り出すヘルパーメソッド
	 */
	private byte[] getFragmentBytes(Object frag) {
	    if (frag == null) return null;
	    if (frag instanceof byte[]) {
	        return (byte[]) frag;
	    } else if (frag instanceof BulkData) {
	        try {
	            // BulkDataを実際のバイト配列として読み込む
	            return ((BulkData) frag).toBytes(org.dcm4che3.data.VR.OB, false);
	        } catch (IOException e) {
	            e.printStackTrace();
	            return null;
	        }
	    }
	    return null;
	}

	/**
	 * 結合処理も getFragmentBytes を使うように修正
	 */
	private byte[] combineFragments(Fragments frags, int start, int end) {
	    if (start == end) {
	        return getFragmentBytes(frags.get(start));
	    }

	    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
	        for (int i = start; i <= end; i++) {
	            byte[] b = getFragmentBytes(frags.get(i));
	            if (b != null) {
	                baos.write(b);
	            }
	        }
	        return baos.toByteArray();
	    } catch (IOException e) {
	        return null;
	    }
	}
	
//	private byte[] getFragmentBytes(Object frag) {
//	    if (frag instanceof byte[]) return (byte[]) frag;
//	    if (frag instanceof BulkData) {
//	        try { return ((BulkData) frag).toBytes(org.dcm4che3.data.VR.OB, false); }
//	        catch (IOException e) { return null; }
//	    }
//	    return null;
//	}
	
	/**
	 * 指定されたフレームの開始点となるフラグメント・インデックスを取得します。
	 */
	private int findStartFragment(Fragments frags, byte[] bot, int frame) {
	    if (bot == null || bot.length < (frame + 1) * 4) {
	        return frame + 1; // BOTがない場合は 1フレーム=1フラグメント と仮定
	    }

	    // BOTから目的のフレームのオフセット（バイト数）を取得
	    long targetOffset = getOffsetFromBOT(bot, frame);
	    
	    // フラグメントを走査してオフセットが一致する場所を探す
	    long currentStreamPos = 0;
	    for (int i = 1; i < frags.size(); i++) {
	        if (currentStreamPos == targetOffset) {
	            return i;
	        }
	        currentStreamPos += getFragmentSize(frags.get(i));
	    }
	    return frame + 1; // 見つからない場合のフォールバック
	}

	/**
	 * 指定されたフレームの終了点となるフラグメント・インデックスを取得します。
	 */
	private int findEndFragment(Fragments frags, byte[] bot, int frame, int numFrames) {
	    // 最終フレームの場合は、最後のフラグメントまで全て
	    if (frame == numFrames - 1) {
	        return frags.size() - 1;
	    }

	    if (bot == null || bot.length < (frame + 2) * 4) {
	        return frame + 1; // BOTがない場合は 1フレーム=1フラグメント と仮定
	    }

	    // 次のフレームの開始オフセットを取得
	    long nextFrameOffset = getOffsetFromBOT(bot, frame + 1);
	    
	    // フラグメントを走査して、次のフレームの直前までを特定する
	    long currentStreamPos = 0;
	    for (int i = 1; i < frags.size(); i++) {
	        currentStreamPos += getFragmentSize(frags.get(i));
	        // 次のフレームの開始位置に到達した、あるいは超えた場合、その一つ前が終端
	        if (currentStreamPos >= nextFrameOffset) {
	            return i;
	        }
	    }
	    return frags.size() - 1;
	}

	/**
	 * BOT（4バイトの配列の塊）から特定のフレームのオフセットを little-endian で取得します。
	 */
	private long getOffsetFromBOT(byte[] bot, int frame) {
	    int start = frame * 4;
	    // 無符号32bit整数としてパースするために & 0xFFL を使用
	    return ((bot[start] & 0xFFL)) |
	           ((bot[start + 1] & 0xFFL) << 8) |
	           ((bot[start + 2] & 0xFFL) << 16) |
	           ((bot[start + 3] & 0xFFL) << 24);
	}

	/**
	 * フラグメント（byte[]またはBulkData）のサイズを取得します。
	 */
	private long getFragmentSize(Object frag) {
	    if (frag instanceof byte[]) {
	        return ((byte[]) frag).length;
	    } else if (frag instanceof BulkData) {
	        return ((BulkData) frag).length();
	    }
	    return 0;
	}

	@Override
	public int getPixel​Representation() {
		return header.getInt(Tag.PixelRepresentation, -1);
	}

	@Override
	public int getSamples() {
		return header.getInt(Tag.SamplesPerPixel, 1);
	}

	@Override
	public UID getSopClassUID() {
		String sopClassUID = header.getString(Tag.SOPClassUID);
		if(sopClassUID == null) {
			return null;
		}
		return UID.uidOf(sopClassUID);
	}

	@Override
	public UID getTSUID() {
		return tsuid;
	}

	@Override
	public int getWidth() {
		return header.getInt(Tag.Columns, 0);
	}
	
	@Override
	public boolean isBanded() {
		return header.getInt(Tag.PlanarConfiguration, 0) != 0;
	}

	@Override
	public boolean isColor() {
		return getSamples() == 3;
	}

	@Override
	public boolean isDecompressed() {
		return decompressed;
	}

	/**
	 * frameが１枚しかないマルチフレームもあるが
	 */
	@Override
	public boolean isMultiFrame() {
		// 1. 枚数が2枚以上なら文句なしにマルチフレーム
	    if (header.getInt(Tag.NumberOfFrames, 1) > 1) {
	        return true;
	    }

	    // 2. 1枚でも Enhanced SOP Class ならマルチフレームとして扱う
	    // (将来的にフレームが追加される可能性や、座標取得ロジックが異なるため)
	    if (isEnhancedMultiframe(header)) {
	        return true;
	    }

	    // 3. Functional Groups を持っているか（Enhanced型特有のデータ構造）
	    if (hasMultiframeStructure(header)) {
	        return true;
	    }

	    return false;
	}
	
	public boolean isEnhancedMultiframe(DicomObject header) {
	    String sopClass = header.getString(Tag.SOPClassUID);
	    // UIDUtilsなどで Enhanced 系のUIDに含まれるかチェック
	    return sopClass != null && (
	        sopClass.equals(UID.EnhancedCTImageStorage.uid()) ||
	        sopClass.equals(UID.EnhancedMRImageStorage.uid()) ||
	        sopClass.equals(UID.EnhancedPETImageStorage.uid()) ||
	        sopClass.contains(".1.1.2.1") || // 慣例的なEnhanced CTのサフィックス
	        sopClass.contains(".1.1.4.1")    // 慣例的なEnhanced MRのサフィックス
	    );
	}
	
	public boolean hasMultiframeStructure(DicomObject header) {
	    // どちらかのシーケンスが存在すれば、それはマルチフレーム構造のファイル
	    return header.contains(Tag.SharedFunctionalGroupsSequence) || 
	           header.contains(Tag.PerFrameFunctionalGroupsSequence);
	}

	@Override
	public boolean isPDF() {
		return getSopClassUID() == UID.EncapsulatedPDFStorage;
	}

	@Override
	public boolean isSigned() {
		return header.getInt(Tag.PixelRepresentation, 0) != 0;
	}
	
	@Override
	public boolean ensurePixelDataLoaded() {
		if(this.filePath != null) {
			// read from file dicom image
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
			reader.read(filePath, true/*load pixel bulk*/);
			DicomObject full = reader.getHeader();
			if(full != null) {
				setHeader(full);
				return true;
			}
		}else {
			// building-up dicom image
			int samples = getSamples();
			int bitsAllocated = getBitsAllocated();
			// load from original bitsAllocated
			if(bitsAllocated == 32 && samples == 1) {
				return header.getValue(Tag.FloatPixelData) != null;
			}else if(bitsAllocated == 64 && samples == 1) {
				return  header.getValue(Tag.DoubleFloatPixelData) != null;
			}else {
				return header.getValue(Tag.PixelData) != null;
			}
		}
		return false;
	}
	
	@Override
	public void releasePixelBulkFromHeader() {
		if (this.filePath != null) {
			// read from file dicom image
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
			reader.read(filePath, false/* load pixel bulk */);
			DicomObject header = reader.getHeader();
			if (header != null) {
				this.header = null;
				setHeader(header);
			}
		}
	}

	@Override
	public void setHeader(DicomObject attr) {
		this.header = attr;
		updateFileMetaInfo(tsuid);
	}
	
	@Override
	public void setFileMetaInfo(DicomObject fmi) {
		this.fmi = fmi;
	}
	
	@Override
	public void setPixelData(int frame/*0 base*/, int w, int h, int samples, int bitsPerPixelSample, Object pixels) {
		
		byte[] pixelsByte = null;
		if(pixels instanceof byte[]) {
			pixelsByte = (byte[])pixels;
		}else if(pixels instanceof short[]) {
			short[] pixels_ = (short[])pixels;
			pixelsByte = ByteUtils.shortToBytes(pixels_, bigEndian());
		}else if(pixels instanceof float[]) {
			float[] pixels_ = (float[])pixels;
			pixelsByte = ByteUtils.floatToBytes(pixels_, bigEndian());
		}else if(pixels instanceof double[]) {
			double[] pixels_ = (double[])pixels;
			pixelsByte = ByteUtils.doubleToBytes(pixels_, bigEndian());
		}else if(pixels instanceof int[] && samples == 3) {//RGB
			int[] pixels_ = (int[])pixels;
			pixelsByte = ByteUtils.intToBytes(pixels_, true/*ignore alpha*/);
		}
		
		if(frame < 0 || frame > getNumOfFrames()) {
			throw new IllegalArgumentException("num of frames is invalid...");
		}
		if(w != getWidth() || h != getHeight() || samples != getSamples()) {
			throw new IllegalArgumentException("num of pixels does not match...");
		}
		
		int bitsAllocated = getBitsAllocated();
		
		Object bulk = null;
		// load from original bitsAllocated
		if(bitsAllocated == 32 && samples == 1) {
			bulk = header.getValue(Tag.FloatPixelData);
		}else if(bitsAllocated == 64 && samples == 1) {
			bulk = header.getValue(Tag.DoubleFloatPixelData);
		}else {
			bulk = header.getValue(Tag.PixelData);
		}

		if (bulk instanceof Fragments) {
			Fragments frags = (Fragments) bulk;
			Object frag = frags.get(frame + 1);// frame number count from 1
			if (frag instanceof byte[]) {
				frags.set(frame + 1, pixels);
			}
		} else if (bulk instanceof byte[] || bulk == null/* from scratch */) {
			if (bitsAllocated == 32 && samples == 1) {
				header.setBytes(Tag.FloatPixelData, VR.OF, pixelsByte);
			} else if (bitsAllocated == 64 && samples == 1) {
				header.setBytes(Tag.DoubleFloatPixelData, VR.OD, pixelsByte);
			} else if (bitsAllocated > 8 && bitsAllocated <= 16) {
				header.setBytes(Tag.PixelData, VR.OW, pixelsByte);
			} else {
				header.setBytes(Tag.PixelData, VR.OB, pixelsByte);
			}
		}
	}
	
	@Override
	public void updateFileMetaInfo(com.vis.dicom.UID tsuid) {
		this.fmi = (DicomObject) header.createFileMetaInformation(tsuid.uid());
		this.tsuid = tsuid;
	}
}