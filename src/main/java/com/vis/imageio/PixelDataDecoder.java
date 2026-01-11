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
		isMultiFrame = dcm.getNumOfFrames() > 0;
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

	private ImageProcessor decodeGrayscale(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
		switch (bitsAllocated) {
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

	// --- 各型専用の高速プロセッサ ---
	private ImageProcessor processByte(ByteBuffer buffer) {
		byte[] pixels = new byte[w * h];
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
		buffer.asFloatBuffer().get(pixels);
		// Floatは通常Signedなのでそのまま扱う
		return new FloatProcessor(w, h, pixels);
	}

	private ImageProcessor processDouble(ByteBuffer buffer) {
		// ImageJは64bitを直接扱えないため、32bit Floatにダウンキャスト
		float[] pixels = new float[w * h];
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
	 * old code
	 */
//	@Deprecated
//	private ImagePlus read() {
//		ImageStack is = new ImageStack(w, h);
//		for (int i = 0; i < frames; i++) {
//			byte[] pixels = (byte[]) dcm.getPixelData(i);
//			ImageProcessor ip = null;
//			if (samplesPerPixel == 1 && bitsAllocated == 8) {
//				if (pixels.length == w * h * 2) {
//					// decompressed pixel
//					short[] spix = new short[pixels.length / 2];
//					com.vis.core.util.ByteUtils.bytesToShorts(pixels, spix, 0, spix.length,
//							dcm.getHeader().bigEndian());
//					ShortProcessor sp = new ShortProcessor(w, h);
//					sp.setPixels(spix);
//					ip = new ByteProcessor(w, h);
//					ip.setPixels(sp.convertToByteProcessor());
//					continue;
//				}
//				ByteBuffer buffer = null;
//				if (bigEndian) {
//					buffer = ByteBuffer.wrap(pixels).order(ByteOrder.BIG_ENDIAN);
//				} else {
//					buffer = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
//				}
//				ip = new ByteProcessor(w, h);
//				ip.setPixels(buffer.array());
//			} else if (samplesPerPixel == 1 && bitsAllocated == 16) {
//				short[] pixelsShort = toShortArray((byte[]) pixels);
//				ip = new ShortProcessor(w, h);
//				ip.setPixels(pixelsShort);
//			} else if (samplesPerPixel == 1 && bitsAllocated == 32) {
//				float[] pixelsFloat = toFloatArray((byte[]) pixels);
//				ip = new FloatProcessor(w, h);
//				ip.setPixels(pixelsFloat);
//			} else if (samplesPerPixel == 1 && bitsAllocated == 64) {
//				/*
//				 * return 32 bit float.
//				 */
//				double[] pixelsDouble = toDoubleArray((byte[]) pixels);
//				float[] pixelsFloat = doubleArray2floatArray(pixelsDouble);
//				ip = new FloatProcessor(w, h);
//				ip.setPixels(pixelsFloat);
//			}
//
//			if (samplesPerPixel == 3) {
//				ip = transformRGB2Processor(w, h, pixels);
//			}
//			is.addSlice(String.valueOf((i + 1)), ip, i);
//		}
//		ImagePlus imp = new ImagePlus("", is);
//		return imp;
//	}
//
//	private short[] toShortArray(byte[] pixels) {
//		
//		short[] shortArray = new short[pixels.length / 2];
//		ByteBuffer buffer = null;
//		if (bigEndian) {
//			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.BIG_ENDIAN);
//		} else {
//			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
//		}
//		buffer.asShortBuffer().get(shortArray);
//		if (!signed) {// unsigned
//			/*
//			 * If unsigned, just auto-boxing as short. (short)org_pix.
//			 */
//			return shortArray;
//		} else {// signed
//			/*
//			 * see also slideglass::initImageInfo() that adjust image pixel density.
//			 */
//			convertToUnsigned(shortArray);
//			return shortArray;
//		}
//	}
//
//	private float[] toFloatArray(byte[] pixels) {
//		ByteBuffer buffer = ByteBuffer.wrap(pixels);
//		if (bigEndian) {
//			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.BIG_ENDIAN);
//		} else {
//			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
//		}
//		FloatBuffer fb = buffer.asFloatBuffer();// .get(floatArray);
//		float[] floatArray = new float[fb.remaining()];
//		fb.get(floatArray);
//		return floatArray;
//	}
//
//	private double[] toDoubleArray(byte[] pixels) {
//		ByteBuffer buffer = ByteBuffer.wrap(pixels);
//		if (bigEndian) {
//			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.BIG_ENDIAN);
//		} else {
//			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
//		}
//		DoubleBuffer db = buffer.asDoubleBuffer();
//		double[] doubleArray = new double[db.remaining()];
//		db.get(doubleArray);
//		return doubleArray;
//	}

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
