package com.vis.dicom.dcm4cheImpl;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.PackedColorModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.awt.image.DirectColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferDouble;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.Random;

import com.pixelmed.display.BufferedImageUtilities;

import ij.ImagePlus;

public class Test {

	public static void main(String[] args) {
		
		/*
		 * 8 bit グレースケール
		 * 符号付き16 bit ( 12 bit ) 
		 * グレースケール符号なし16 bit ( 12 bit ) 
		 * グレースケール32 bit グレースケール（単精度浮動小数点
		 * 64 bit グレースケール（倍精度浮動小数点） 
		 * Component RGB（ 8 bit / Sample ）
		 * Packed RGB（ 8 bit / Sample ）
		 * 
		 * 
		 * YBR（ 8 bit / Sample ）
		 * 
		 * 
		 * RGBRGBRRRGGGBBB case
		 */
		
//		bit64Gray();
		
//		BufferedImage r1 = bit8BandedRGB();
		
//		BufferedImage r1 = bit8PackedRGB();
		
		BufferedImage r1 = bit8InterleavedRGB();
		
		new ImagePlus("rgb", r1).show();
		System.out.println(r1.getType());
//		new ImagePlus("rgb", bit8RGB2YBR()).show();
		
	}
	
	static BufferedImage bit8Gray() {
		// 8 bit grayscale
		Random rand = new Random(76);
		int w = 128;
		int h = 128;
		byte[] gray8 = new byte[w * h];
		rand.nextBytes(gray8);
		
		ColorModel cmodel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[]{8},
                false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
		SampleModel sampleModel = cmodel.createCompatibleSampleModel(w, h);
		DataBufferByte dataBuffer = new DataBufferByte(gray8, gray8.length);
		WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null/*left upper location*/);
       BufferedImage dst = new BufferedImage(cmodel, rasterDst, false /*preMultipliedAlpha*/, null/*properties*/);
		return dst;
	}
	
	static BufferedImage bit16ShortGray() {
		// 16 bit grayscale
		Random rand = new Random(76);
		int w = 128;
		int h = 128;
		short[] gray16 = new short[w * h];
		int max = (int)Math.pow(2, 16) - 1;
		for(int i=0; i<w*h; i++) {
			int v = rand.nextInt(max)-32768 ;//-32768 to 32767
			gray16[i] = (short)v;
		}
		
		ColorModel cmodel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[]{16},
                false, false, Transparency.OPAQUE, DataBuffer.TYPE_SHORT);
		SampleModel sampleModel = cmodel.createCompatibleSampleModel(w, h);
		DataBufferShort dataBuffer = new DataBufferShort(gray16, gray16.length);
//		DataBuffer dataBuffer = sampleModel.createDataBuffer();
		WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null/*left upper location*/);
       BufferedImage dst = new BufferedImage(cmodel, rasterDst, false /*preMultipliedAlpha*/, null/*properties*/);
		return dst;
	}
	
	static BufferedImage bit16UShortGray() {
		// 16 bit grayscale
		Random rand = new Random(76);
		int w = 128;
		int h = 128;
		short[] gray16 = new short[w * h];
		int max = (int)Math.pow(2, 16) - 1;
		for(int i=0; i<w*h; i++) {
			int v = rand.nextInt(max);//0 to 65535
			gray16[i] = (short)v;
		}
		
		ColorModel cmodel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[]{16},
                false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT);
		SampleModel sampleModel = cmodel.createCompatibleSampleModel(w, h);
		DataBufferUShort dataBuffer = new DataBufferUShort(gray16, gray16.length);
		WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null/*left upper location*/);
       BufferedImage dst = new BufferedImage(cmodel, rasterDst, false /*preMultipliedAlpha*/, null/*properties*/);
		return dst;
	}
	
	static BufferedImage bit32Gray() {
		// 32 bit grayscale
		Random rand = new Random(76);
		int w = 128;
		int h = 128;
		float[] gray32 = new float[w * h];
		for(int i=0; i<w*h; i++) {
			float v = rand.nextFloat();
			gray32[i] = v;
		}
		
		ColorModel cmodel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[]{32},
                false, false, Transparency.OPAQUE, DataBuffer.TYPE_FLOAT);
		SampleModel sampleModel = cmodel.createCompatibleSampleModel(w, h);
		DataBufferFloat dataBuffer = new DataBufferFloat(gray32, gray32.length);
		WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null/*left upper location*/);
       BufferedImage dst = new BufferedImage(cmodel, rasterDst, false /*preMultipliedAlpha*/, null/*properties*/);
		return dst;
	}
	
	static BufferedImage bit64Gray() {
		// 64 bit grayscale
		Random rand = new Random(76);
		int w = 128;
		int h = 128;
		double[] gray64 = new double[w * h];
		for(int i=0; i<w*h; i++) {
			double v = rand.nextDouble();
			gray64[i] = v;
		}
		
		ColorModel cmodel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[]{64},
                false, false, Transparency.OPAQUE, DataBuffer.TYPE_DOUBLE);
		int band = 1;
		int pixelStride = 1; // num of sample in band
		int scanlineStride = w * band;
		int[] bandOffset = {0};//only single sample in one band.
		SampleModel sampleModel = new ComponentSampleModel(DataBuffer.TYPE_DOUBLE, w, h, pixelStride, scanlineStride, bandOffset);
		DataBufferDouble dataBuffer = new DataBufferDouble(gray64, gray64.length);
		WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null/*left upper location*/);
       BufferedImage dst = new BufferedImage(cmodel, rasterDst, false /*preMultipliedAlpha*/, null/*properties*/);
		return dst;
	}
	
	static BufferedImage bit8InterleavedRGB() {
		// 8 bit/sample RGB
		Random rand = new Random(76);
		int band = 3;
		int w = 128;
		int h = 128;
		byte[] rgb = new byte[w * h * band];
		rand.nextBytes(rgb);
		
		int pixelStride = band; // num of sample in band
		int scanlineStride = w * band;
		int[] bandOffset = {0,1,2};// location index of samples in one band.
		SampleModel sampleModel = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, w, h, pixelStride, scanlineStride, bandOffset);
		DataBufferByte dataBuffer = new DataBufferByte(rgb, rgb.length);
		WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null);
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
		dst.setData(rasterDst);
       return dst;
	}
	
	static BufferedImage bit8BandedRGB() {
		// 8 bit/sample RGB
		int band = 3;
		int w = 128;
		int h = 128;
		byte[] red = new byte[w * h];
		byte[] green = new byte[w * h];
		byte[] blue = new byte[w * h];
		byte[][] rgb = new byte[band][];
		Random rand = new Random(76);
		rand.nextBytes(red);
		rand.nextBytes(green);
		rand.nextBytes(blue);
		
		rgb[0] = blue;
		rgb[1] = green;
		rgb[2] = red;
		
		SampleModel sampleModel = new BandedSampleModel(DataBuffer.TYPE_BYTE, w, h, band);
		DataBufferByte dataBuffer = new DataBufferByte(rgb, rgb.length);
		WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null);
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
       dst.setData(rasterDst);
		return dst;
	}
	
	static BufferedImage bit8PackedRGB() {
		// 8 bit/sample RGB
		Random rand = new Random(76);
		int w = 128;
		int h = 128;
		int[] rgb = new int[w * h];
		for(int i=0; i<w*h; i++) {
			int v = rand.nextInt();
//			int A = (v << 24) & 0xFF000000;
//			int R = (v << 16) & 0x00FF0000;
//			int G = (v << 8) & 0x0000FF00;
//			int B = v & 0x000000FF;
			rgb[i] = v;
		}
		
		int[] masks_RGBa = new int[]{0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000};
		
		ColorModel cmodel = new DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), 32, 0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000, false, DataBuffer.TYPE_INT);
		SampleModel sampleModel = new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, w, h, masks_RGBa);
		DataBufferInt dataBuffer = new DataBufferInt(rgb, rgb.length);
		WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null);
       BufferedImage dst = new BufferedImage(cmodel, rasterDst, false /*preMultipliedAlpha*/, null/*properties*/);
		return dst;
	}
	
	static BufferedImage bit8RGB2YBR() {
		// 8 bit/sample RGB
		Random rand = new Random(76);
		int w = 128;
		int h = 128;
		int[] rgb = new int[w * h];
		for(int i=0; i<w*h; i++) {
			int v = rand.nextInt();
			rgb[i] = v;
		}
		
		int[] masks = new int[]{0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000};
		
		ColorModel cmodel = new DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), 32, 0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000, false, DataBuffer.TYPE_INT);
		int scanlineStride = w;//w * band; because, packed.
		DataBufferInt dataBuffer = new DataBufferInt(rgb, rgb.length);
		WritableRaster rasterDst = Raster.createPackedRaster(dataBuffer, w, h,scanlineStride, masks, null);
       BufferedImage dst = new BufferedImage(cmodel, rasterDst, false /*preMultipliedAlpha*/, null/*properties*/);
       BufferedImage trans = BufferedImageUtilities.convertYBRToRGB(dst);
       
		return trans;
	}
	
}
