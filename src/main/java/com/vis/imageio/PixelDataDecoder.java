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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.*;
import java.util.stream.IntStream;

import com.vis.core.log.Log;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import ij.process.FloatProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * Extract PixelData from DicomObject
 * 
 * @author tatsunidas
 *
 */
public class PixelDataDecoder {

	static Logger logger = Log.logger;

	DicomImage dcm;
	ImagePlus imp;

	int w = -1;
	int h = -1;
	int bitsAllocated;
	int bitsStored;
	int samplesPerPixel;
	int pixelRep;
	/*
	 * banded [true] means rrr...ggg...bbb.. banded [false] mean rgbrgb...
	 */
	boolean banded;// i.e, Tag.PlanarConfiguration, 0) != 0;
	
	/**
	 * 1. DICOMが「Unsigned（符号なし）」の場合
	 * 
	 * ピクセル値: 0 〜 65535 メモリへの格納: そのままキャストしてJavaの short 配列として扱う。32768
	 * 以上の値はJavaではマイナス（例: 65535 は -1）に見えるが、ビットの並び自体は全く変わっていないので問題ない。 値の読み出し: ImageJが
	 * & 0xffff でビットマスクし、マイナスの値を元の 0 〜 65535 に変換する。
	 * 
	 * Calibration（輝度校正）: 値は元のままシフトしていないため、DICOMタグの Rescale Intercept と Rescale
	 * Slope をそのまま適用する。何も設定がなければ、slope=1, intercept=0
	 * 
	 * 2. DICOMが「Signed（符号付き）」の場合
	 * 
	 * ピクセル値: -32768 〜 32767
	 * 
	 * メモリへの格納: ImageJの ShortProcessor
	 * は「Unsigned（0〜65535）」のみを期待している。Signedをいれるとオーバーフローしてしまう。そこで、ImageJは読み込む際、全ピクセルに
	 * +32768 を足して、0 〜 65535 の範囲にスライド（シフト）させる。（-32768 は 0 になり、0 は 32768 になる）
	 * 
	 * Calibration（輝度校正）: すべて 32768 だけズレて明るくなってしまうため、Calibrationの Intercept に -32768
	 * を設定する。
	 * 
	 * 値の読み出し: cal.getCValue(getf(x,y))あるいは、ip.getPixelValue(x,y) で、「生の値（+32768された値）
	 * - 32768 = 元の値という計算が行われ、元の値が復元される。
	 * 
	 * @param min
	 * @param max
	 * @param fromMouseAction
	 */
	boolean signed;// = dataset.getInt(Tag.PixelRepresentation, 0) != 0;
	
	int frames;// = dataset.getInt(Tag.NumberOfFrames, 1);
	int frameLength;// = rows * cols * samples * bitsAllocated / 8;
	int length;// frameLength * frames;
	String colorType;
	String tsUID;
	com.vis.dicom.VR pixel_vr;
	boolean bigEndian = false;
	boolean isMultiFrame = false;
	boolean compressed = false;
	boolean float_pixel_data = false;
	boolean double_float_pixel_data = false;

	public PixelDataDecoder() {}

	/**
	 * See also initImageInfo in SlideGlass/ImageSpecimen to adjust signed contrast.
	 * @param dcm
	 */
	public PixelDataDecoder(DicomImage dcm) {
		this.dcm = dcm;
		this.tsUID = dcm.getTSUID().uid();
		w = dcm.getWidth();
		h = dcm.getHeight();
		bitsAllocated = dcm.getBitsAllocated();
		bitsStored = dcm.getBitsStored();
		samplesPerPixel = dcm.getSamples();
		colorType = dcm.getPhotometricInterpletation().name();
		bigEndian = tsUID.equals(UID.ExplicitVRBigEndian.uid());
		banded = dcm.isBanded();
		signed = dcm.isSigned();
		this.frames = Math.max(1, dcm.getNumOfFrames());
		isMultiFrame = dcm.isMultiFrame();
		frameLength = w * h * samplesPerPixel * bitsAllocated / 8;
		length = frameLength * frames;

		if (w == -1 || h == -1 || bitsAllocated == -1 || samplesPerPixel == -1) {
			return;
		}

		pixel_vr = null;
		if (dcm.getHeader().getValue(Tag.Float​​Pixel​​Data) != null) {
			float_pixel_data = true;
			pixel_vr = TagDict.vrType(Tag.Float​​Pixel​​Data)[0];
		} else if (dcm.getHeader().getValue(Tag.Double​Float​Pixel​​Data) != null) {
			double_float_pixel_data = true;
			pixel_vr = TagDict.vrType(Tag.Double​Float​Pixel​​Data)[0];
		} else {
			pixel_vr = TagDict.vrType(Tag.Pixel​Data)[0];
		}
		this.compressed = Codec.isCompressed(tsUID);
		/*
		 * here, do not perform decompress. decompressing should be performed before
		 * pixel decoding.
		 */
//		if(compressed) {
//			Decompressor.newInstance(dcm.getCore(), tsUID).decompress();
//			dcm.getFileMetaInfo().setString(Tag.Transfer​Syntax​UID, VR.UI, UID.ImplicitVRLittleEndian);
//		}
	}

	public ImagePlus decode() {
		if (dcm == null || w <= 0 || h <= 0) {
			logger.log(Level.INFO, "PixelDataDecoder not ready to decode dicom.");
			return null;
		}

		ImageStack stack = new ImageStack(w, h);
		for (int i = 0; i < frames; i++) {
			byte[] bytes = dcm.getPixelData(i);
			if (bytes == null) {
				continue;
			}
			ImageProcessor ip = (samplesPerPixel == 1) ? decodeGrayscale(bytes) : decodeColor(bytes);
			if (ip != null) {
				stack.addSlice(String.valueOf(i + 1), ip);
			}
		}
		String sopInstUID = dcm.getHeader().getString(Tag.SOP​Instance​UID);
		return new ImagePlus(sopInstUID, stack);
	}
	
	public ImagePlus decodeParallel() {
		if (dcm == null || w <= 0 || h <= 0)
			return null;

		// フレームごとの ImageProcessor を格納する配列を用意（順番を維持するため）
		ImageProcessor[] processors = new ImageProcessor[frames];

		// Java Parallel Stream を使用して並列処理
		IntStream.range(0, frames).parallel().forEach(i -> {
			try {
				// 各スレッドで個別にピクセルデータを取得してデコード
				Object rawPixels = dcm.getPixelData(i);
				if (rawPixels instanceof byte[]) {
					byte[] bytes = (byte[]) rawPixels;

					// 既存のデコードロジックを呼び出す
					ImageProcessor ip = (samplesPerPixel == 1) ? decodeGrayscale(bytes) : decodeColor(bytes);

					// 結果を配列の特定位置に格納（スレッドセーフ）
					processors[i] = ip;
				}
			} catch (Exception e) {
				logger.severe("Error decoding frame " + i + ": " + e.getMessage());
			}
		});

		// デコード完了後、メインスレッドで順番に ImageStack に追加
		ImageStack stack = new ImageStack(w, h);
		for (int i = 0; i < processors.length; i++) {
			if (processors[i] != null) {
				stack.addSlice(String.valueOf(i + 1), processors[i]);
			}
		}
		String sopInstUID = dcm.getHeader().getString(Tag.SOP​Instance​UID);
		return new ImagePlus(sopInstUID, stack);
	}

	/**
	 * dcm4che decoder will return OW byte[].
	 * @param bytes
	 * @return
	 */
	public ImageProcessor decodeDecompressedByte(byte[] bytes) {
		if (bytes == null) {
	        Log.logger.severe("解凍されたバイト配列が null です。解凍に失敗した可能性があります。");
	        return null; // または適切なエラー処理
	    }
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
		return processShort(buffer);
	}
	
	private ImageProcessor decodeGrayscale(byte[] bytes) {
	    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
	    switch (bitsAllocated) {
	        case 1:
	            return processBit(bytes); // for SEG
	        case 8:
	            return processByte(buffer);
	        case 16:
	            return processShort(buffer);
	        case 32:
	            return processFloat(buffer);
	        case 64:
	            return processDouble(buffer);
	        default:
	            logger.warning("Unsupported BitsAllocated: " + bitsAllocated);
	            return null;
	    }
	}
	
	// --- 1-bit 専用の高速プロセッサ:DICOM SEG ---
	private ImageProcessor processBit(byte[] bytes) {
		byte[] pixels = new byte[w * h];
		int pixelIndex = 0;

		for (int i = 0; i < bytes.length && pixelIndex < w * h; i++) {
			byte b = bytes[i];
			// DICOMの仕様: 1バイト内のピクセル順序は LSB (Bit 0) から MSB (Bit 7) の順
			for (int bit = 0; bit < 8 && pixelIndex < w * h; bit++) {
				// 対象ビットが立っているか（1か）判定
				boolean isSet = (b & (1 << bit)) != 0;
				// マスク画像を視覚化するため、1なら白(255)、0なら黒(0)にマッピングする
//				pixels[pixelIndex++] = (byte) (isSet ? 255 : 0);
				/*
				 * keep label to be 1
				 */
				pixels[pixelIndex++] = (byte) (isSet ? 1 : 0);
			}
		}

		// ImageJは1bit画像を直接持てないため、8bitのByteProcessorとして返す
		return new ByteProcessor(w, h, pixels);
	}

	private ImageProcessor processByte(ByteBuffer buffer) {
		byte[] pixels = new byte[w * h];
		/*
		 * Endian ByteOrder does not effect to "Byte", but remaining code to explicitly.
		 */
		if(bigEndian) {
			buffer.order(ByteOrder.BIG_ENDIAN);
		}else {
			buffer.order(ByteOrder.LITTLE_ENDIAN);
		}
		buffer.get(pixels);
		// Signed Byteの場合: -128~127 を 0~255 にマッピング (XOR 0x80)
		if (signed) {
			for (int i = 0; i < pixels.length; i++) {
				pixels[i] = (byte) (pixels[i] ^ 0x80);//same as +128
			}
		}
		return new ByteProcessor(w, h, pixels);
	}

	/**
	 * If signed: 
	 * Input to image processor
	 *  shiftSignedToUnsignedValue = signedValue ^ 0x8000
	 * If you did getPixels() from shortprocessor, do: int unsignedValue = pixels[j] & 0xffff
	 * If you shortProcessor.get(x,y), this is return unsigned value.
	 * @param buffer
	 * @return
	 */
	private ImageProcessor processShort(ByteBuffer buffer) {
		short[] pixels = new short[w * h];
		if(bigEndian) {
			buffer.order(ByteOrder.BIG_ENDIAN);
		}else {
			buffer.order(ByteOrder.LITTLE_ENDIAN);
		}
		buffer.asShortBuffer().get(pixels);
		// Signed Shortの場合: -32768~32767 を 0~65535 にマッピング (XOR 0x8000)
		if (signed) {
			for (int i = 0; i < pixels.length; i++) {
				pixels[i] = (short) (pixels[i] ^ 0x8000);//same as +32768
			}
		}
		return new ShortProcessor(w, h, pixels, null/* make default color model */);
	}

	private ImageProcessor processFloat(ByteBuffer buffer) {
		float[] pixels = new float[w * h];
		if(bigEndian) {
			buffer.order(ByteOrder.BIG_ENDIAN);
		}else {
			buffer.order(ByteOrder.LITTLE_ENDIAN);
		}
		buffer.asFloatBuffer().get(pixels);
		// Floatは通常Signedなのでそのまま扱う
		return new FloatProcessor(w, h, pixels);
	}

	private ImageProcessor processDouble(ByteBuffer buffer) {
		// ImageJは64bitを直接扱えないため、32bit Floatにダウンキャスト
		float[] pixels = new float[w * h];
		if(bigEndian) {
			buffer.order(ByteOrder.BIG_ENDIAN);
		}else {
			buffer.order(ByteOrder.LITTLE_ENDIAN);
		}
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = (float) buffer.getDouble();
		}
		return new FloatProcessor(w, h, pixels);
	}

	private ImageProcessor decodeColor(byte[] bytes) {
		int size = w * h;
		int[] rgbPixels = new int[size];

		if (!banded) { // Interleaved: RGBRGB...
			for (int i = 0; i < size; i++) {
				int r = bytes[i * 3] & 0xFF;
				int g = bytes[i * 3 + 1] & 0xFF;
				int b = bytes[i * 3 + 2] & 0xFF;
				rgbPixels[i] = (r << 16) | (g << 8) | b;
			}
		} else { // Banded: RRR...GGG...BBB...
			for (int i = 0; i < size; i++) {
				int r = bytes[i] & 0xFF;
				int g = bytes[i + size] & 0xFF;
				int b = bytes[i + 2 * size] & 0xFF;
				rgbPixels[i] = (r << 16) | (g << 8) | b;
			}
		}
		return new ColorProcessor(w, h, rgbPixels);
	}

	public ImageProcessor decode(byte[] decompressed) {
		if (samplesPerPixel == 1) {
			return decodeGrayscale(decompressed);
		} else {
			return decodeColor(decompressed);
		}
	}


	/*
	 * imp's imageprocessor is always unsigned.
	 */
	public byte[] pixel2Byte(ImagePlus imp) {
		int bits = imp.getBitDepth();
		int sample = imp.getChannel();
		int length = bits / 8 * sample * imp.getWidth() * imp.getHeight() * imp.getNSlices();
		byte[] blob = new byte[length];
		int loc = 0;
		for (int i = 0; i < imp.getNSlices(); i++) {
			imp.setPosition(i + 1);// one based
			ImageProcessor ip = imp.getProcessor();
			Object pixels = ip.getPixels();
			if (bits == 8) {
				byte[] pix = (byte[]) pixels;
				for (byte p : pix) {
					blob[loc++] = p;
				}
			} else if (bits == 16) {
				short[] pix = (short[]) pixels;
				for (short p : pix) {
					ByteBuffer buffer = ByteBuffer.allocate(2);
					buffer.putShort(p);
					byte[] bytes = buffer.array();
					for (byte b : bytes) {
						blob[loc++] = b;
					}
				}
			} else if (bits == 32 && sample == 1) {
				float[] pix = (float[]) pixels;
				for (float p : pix) {
					ByteBuffer buffer = ByteBuffer.allocate(4);
					buffer.putFloat(p);
					byte[] bytes = buffer.array();
					for (byte b : bytes) {
						blob[loc++] = b;
					}
				}
			} else if (bits == 24 && sample == 3) {
				// ColorProceccer always rgbrgbrgb...
				// BufferedImage.TYPE_INT_RGB
				int[] pix = (int[]) pixels;
				for (int rgb : pix) {
					byte r = (byte) ((rgb >> 16) & 0xFF);
					byte g = (byte) ((rgb >> 8) & 0xFF);
					byte b = (byte) (rgb & 0xFF);
					pix[loc++] = r;
					pix[loc++] = g;
					pix[loc++] = b;
				}
			} else {
				return null;
			}
		}
		return blob;
	}
	
	/**
     * ImageProcessorのピクセルを、DICOMの形式（エンディアン、符号、RGB順など）に合わせてByte配列にエンコードする
     */
    public byte[] encodeImageProcessorToBytes(ij.process.ImageProcessor ip, com.vis.dicom.image.DicomImage dcm) {
        int bits = dcm.getBitsAllocated();
        int samples = dcm.getSamples();
        Object pixels = ip.getPixels();
        int w = ip.getWidth();
        int h = ip.getHeight();
        int length = w * h * samples * (bits / 8);
        byte[] blob = new byte[length];
        
        boolean isBigEndian = dcm.getHeader().bigEndian();
        
        if (bits == 8 && samples == 1) {
            byte[] pix = (byte[]) pixels;
            if (dcm.isSigned()) {
                for(int i=0; i<pix.length; i++) {
                	int unsignedVal = pix[i] & 0xFF;
                	byte signedVal = (byte)(unsignedVal - 128);
                	blob[i] = signedVal;
                }
            } else {
                System.arraycopy(pix, 0, blob, 0, pix.length);
            }
        } else if (bits == 16 && samples == 1) {
            short[] pix = (short[]) pixels;
            boolean signed = dcm.isSigned();
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(length);
            if (isBigEndian) buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
            else buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int i=0;i<pix.length; i++) {
                if (signed) {
                	int unsignedVal = pix[i] & 0xFFFF;
                	short signedVal = (short)(unsignedVal - 32768);
                	buffer.putShort(signedVal);
                }else {
                	buffer.putShort(pix[i]);
                }
            }
            blob = buffer.array();
        } else if (bits == 32 && samples == 1) {
            float[] pix = (float[]) pixels;
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(length);
            if (isBigEndian) buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
            else buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (float p : pix) buffer.putFloat(p);
            blob = buffer.array();
        } else if (bits == 8 && samples == 3) {
            int[] pix = (int[]) pixels;
            int loc = 0;
            if (dcm.isBanded()) { // RRR...GGG...BBB
                int size = w * h;
                for (int i=0; i<size; i++) blob[i] = (byte)((pix[i] >> 16) & 0xFF);
                for (int i=0; i<size; i++) blob[size + i] = (byte)((pix[i] >> 8) & 0xFF);
                for (int i=0; i<size; i++) blob[size*2 + i] = (byte)(pix[i] & 0xFF);
            } else {              // RGBRGBRGB
                for (int rgb : pix) {
                    blob[loc++] = (byte) ((rgb >> 16) & 0xFF);
                    blob[loc++] = (byte) ((rgb >> 8) & 0xFF);
                    blob[loc++] = (byte) (rgb & 0xFF);
                }
            }
        }
        return blob;
    }

	// int[] to RGB imageplus
	/**
	 * 
	 * @param w
	 * @param h
	 * @param planarConfiguration:0, rgbrgb. 1, rrrgggbbb
	 * @param pixels
	 * @return
	 */
	public ImagePlus transformRGB2ImagePlusFromInt(int w, int h, int[] pixels) {
		if (pixels instanceof int[]) {
			// can only decode rgb
			if (!banded) {
				int[] rgb = (int[]) pixels;
				ColorProcessor cp = new ColorProcessor(w, h, rgb);
				return new ImagePlus("", cp);
			} else {
				/*
				 * Assuming that they are stored in RGBRGBRGB order, put them back in order.
				 */
				int[] rgb = (int[]) pixels;
				int size = rgb.length;
				ColorProcessor dummy = new ColorProcessor(w, h, pixels);
				byte[] r = new byte[size];
				byte[] g = new byte[size];
				byte[] b = new byte[size];
				dummy.getRGB(r, g, b);
				int itr = 0;
				int stop = 0;
				for (int i = 0; i < size; i++) {
					r[itr++] = (byte) ((rgb[i] & 0xff) << 16);
					if (itr == size) {
						stop = i;
						break;
					}
					r[itr++] = (byte) ((rgb[i] & 0xff) << 8);
					if (itr == size) {
						stop = i;
						break;
					}
					r[itr++] = (byte) (rgb[i] & 0xff);
					if (itr == size) {
						stop = i;
						break;
					}
				}
				int g_start = stop;
				itr = 0;
				for (int i = g_start; i < size; i++) {
					g[itr++] = (byte) ((rgb[i] & 0xff) << 16);
					if (itr == size) {
						stop = i;
						break;
					}
					g[itr++] = (byte) ((rgb[i] & 0xff) << 8);
					if (itr == size) {
						stop = i;
						break;
					}
					g[itr++] = (byte) (rgb[i] & 0xff);
					if (itr == size) {
						stop = i;
						break;
					}
				}
				int b_start = stop;
				itr = 0;
				for (int i = b_start; i < size; i++) {
					b[itr++] = (byte) ((rgb[i] & 0xff) << 16);
					if (itr == size) {
						stop = i;
						break;
					}
					b[itr++] = (byte) ((rgb[i] & 0xff) << 8);
					if (itr == size) {
						stop = i;
						break;
					}
					b[itr++] = (byte) (rgb[i] & 0xff);
					if (itr == size) {
						stop = i;
						break;
					}
				}
				ColorProcessor cp = new ColorProcessor(w, h);
				cp.setRGB(r, g, b);
				return new ImagePlus("", cp);
			}
		} else {
			return null;
		}
	}
	
	public void bgr2rgb(byte[] bs) {
		for (int i = 0, j = 2; j < bs.length; i += 3, j += 3) {
			byte b = bs[i];
			bs[i] = bs[j];
			bs[j] = b;
		}
	}

}
