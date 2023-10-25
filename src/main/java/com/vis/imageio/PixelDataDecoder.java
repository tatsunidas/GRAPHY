package com.vis.imageio;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.logging.*;

import org.dcm4che3.data.UID;

import com.vis.core.log.Log;
import com.vis.core.view.D2.processing.ImageProcessing;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.VR;
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
	boolean banded;// = dataset.getInt(Tag.PlanarConfiguration, 0) != 0;
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
	
	public PixelDataDecoder(DicomImage dcm) {
		this.dcm = dcm;
		this.tsUID = dcm.getTSUID().uid();
		w = dcm.getWidth();
		h = dcm.getHeight();
		bitsAllocated = dcm.getBitsAllocated();
		bitsStored = dcm.getBitsStored();
		samplesPerPixel = dcm.getSamples();
		colorType = dcm.getPhotometricInterpletation().name();
		bigEndian = tsUID.indexOf("1.2.840.10008.1.2.2")>=0 ? true:false;
		isMultiFrame = dcm.getNumOfFrames() > 1;
		banded = dcm.isBanded();
       signed = dcm.isSigned();
       frames = dcm.getNumOfFrames();
       frameLength = w * h * samplesPerPixel * bitsAllocated / 8;
       length = frameLength * frames;
		
		if(w == -1 || h == -1 || bitsAllocated == -1 || samplesPerPixel == -1) {
			return;
		}
		
		pixel_vr = null;
		if(dcm.getCore().getValue(Tag.Float​​Pixel​​Data) != null) {
			float_pixel_data = true;
			pixel_vr = TagDict.vrType(Tag.Float​​Pixel​​Data)[0];
		}else if(dcm.getCore().getValue(Tag.Double​Float​Pixel​​Data) != null) {
			double_float_pixel_data = true;
			pixel_vr = TagDict.vrType(Tag.Double​Float​Pixel​​Data)[0];
		}else {
			pixel_vr = TagDict.vrType(Tag.Pixel​Data)[0];
		}
		this.compressed = Codec.isCompressed(tsUID);
		/*
		 * here, do not perform decompress.
		 * do decompress first, before pixel decoding.
		 */
//		if(compressed) {
//			Decompressor.newInstance(dcm.getCore(), tsUID).decompress();
//			dcm.getFileMetaInfo().setString(Tag.Transfer​Syntax​UID, VR.UI, UID.ImplicitVRLittleEndian);
//		}
	}
	
	public ImagePlus decode(byte[] decompressed) {
		if(samplesPerPixel == 1) {
			return transformMONO2ImagePlus(w, h, decompressed);
		}else {
			return new ImagePlus("", transformRGB2Processor(w, h, decompressed));
		}
	}
	
	public ImagePlus decode() {
		if(dcm == null) {
			return null;
		}
		return read();
	}
	
	private ImagePlus read() {
		ImageStack is = new ImageStack(w, h);
		
		for (int i = 0; i < frames; i++) {
			byte[] pixels = (byte[]) dcm.getPixelData(i);
			ImageProcessor ip = null;
			if(samplesPerPixel == 1 && bitsAllocated == 8) {
				if(pixels.length == w*h*2) {
					//decompressed pixel
					short[] spix = new short[pixels.length/2];
					com.vis.core.util.ByteUtils.bytesToShorts(pixels, spix, 0, spix.length, dcm.getCore().bigEndian());
					ShortProcessor sp = new ShortProcessor(w, h, spix, null);
					ip = new ByteProcessor(w, h);
					ip.setPixels(sp.convertToByteProcessor());
					continue;
				}
				ByteBuffer buffer = null;
				if(bigEndian) {
					buffer = ByteBuffer.wrap(pixels).order(ByteOrder.BIG_ENDIAN);
				}else {
					buffer = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
				}
				ip = new ByteProcessor(w, h);
				ip.setPixels(buffer.array());
			}else if(samplesPerPixel == 1 && bitsAllocated == 16){
				short[] pixelsShort = toShortArray((byte[])pixels);
				ip = new ShortProcessor(w, h, signed);
				ip.setPixels(pixelsShort);
			}else if(samplesPerPixel == 1 && bitsAllocated == 32){
				float[] pixelsFloat = toFloatArray((byte[])pixels);
				ip = new FloatProcessor(w, h);
				ip.setPixels(pixelsFloat);
			}else if(samplesPerPixel == 1 && bitsAllocated == 64){
				/*
				 * return 32 bit float.
				 */
				double[] pixelsDouble = toDoubleArray((byte[])pixels);
				float[] pixelsFloat = doubleArray2floatArray(pixelsDouble);
				ip = new FloatProcessor(w, h);
				ip.setPixels(pixelsFloat);
			}
			
			if(samplesPerPixel == 3) {
				ip = transformRGB2Processor(w, h, pixels);
			}
			
			is.addSlice(String.valueOf((i+1)), ip, i);
		}
		ImagePlus imp = new ImagePlus("", is);
		return imp;
	}
	
	
	private short[] toShortArray(byte[] pixels) {
		short[] shortArray = new short[pixels.length / 2];
		ByteBuffer buffer = null;
		if(bigEndian) {
			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.BIG_ENDIAN);
		}else {
			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
		}
		buffer.asShortBuffer().get(shortArray);
		buffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(shortArray);
//		ShortBuffer sb = buffer.asShortBuffer();//DO NOT USE java.lang.UnsupportedOperationException
//		short[] unsigned = sb.array();//DO NOT USE java.lang.UnsupportedOperationException
		if(!signed) {//unsigned
			return shortArray;
		}else {//signed
			short[] shortArraySigned = new short[shortArray.length];
			for(int i=0;i<shortArray.length;i++) {
				shortArraySigned[i] = (short) (shortArray[i] - (short)32768);
			}
			return shortArraySigned;
		}
	}
	
	private float[] toFloatArray(byte[] pixels) {
		ByteBuffer buffer = null;
		if(bigEndian) {
			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.BIG_ENDIAN);
		}else {
			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
		}
		FloatBuffer fb = buffer.asFloatBuffer();//.get(floatArray);
		return fb.array();
	}
	
	private double[] toDoubleArray(byte[] pixels) {
		ByteBuffer buffer = null;
		if(bigEndian) {
			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.BIG_ENDIAN);
		}else {
			buffer = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
		}
		DoubleBuffer db = buffer.asDoubleBuffer();
		return db.array();
	}
	
	
	/*
	 * TODO PALETTE COLOR...?
	 */
	public ImagePlus transformMONO2ImagePlus(int w, int h, Object decompressed){
		if(samplesPerPixel != 1) {
			return null;
		}
		if(decompressed instanceof byte[]) {
			byte[] pixelArray = (byte[])decompressed;
			if(pixelArray.length == w*h) {
				ByteProcessor bp = new ByteProcessor(w, h, pixelArray);//, cm);
				return new ImagePlus("",bp);
			}else if(pixelArray.length == w*h*2) {
				short[] pixelsShort = toShortArray((byte[])pixelArray);
				ShortProcessor sp = new ShortProcessor(w, h, pixelsShort, null);
				return new ImagePlus("",sp);
			}else if(pixelArray.length == w*h*3) {
				float[] pixelsFloat = toFloatArray((byte[])pixelArray);
				FloatProcessor fp = new FloatProcessor(w, h, pixelsFloat, null);
				return new ImagePlus("",fp);
			}else if(pixelArray.length == w*h*4) {
				double[] pixelsDouble = toDoubleArray((byte[])pixelArray);
				float[] pixelsFloat = doubleArray2floatArray(pixelsDouble);
				FloatProcessor fp = new FloatProcessor(w, h, pixelsFloat, null);
				return new ImagePlus("",fp);
			}else {
				return null;
			}
		}else if(decompressed instanceof short[]) {
			short[] pixelArray = (short[])decompressed;
			ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
			ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE,
					DataBuffer.TYPE_BYTE);// allways byte for colormodel!
			ShortProcessor sp = new ShortProcessor(w, h, pixelArray, cm);
			return new ImagePlus("",sp);
		}else if(decompressed instanceof float[]) {
			float[] floatArray = (float[])decompressed;
			ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
			ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE,
					DataBuffer.TYPE_BYTE);// allways byte for colormodel!
			FloatProcessor fp = new FloatProcessor(w, h, floatArray, cm);
			return new ImagePlus("", fp);
		}else if(decompressed instanceof double[]) {
			//imageplus does not support 64 bit.
			double[] doubleArray = (double[])decompressed;
			float[] floatArray = doubleArray2floatArray(doubleArray);
			ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
			ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE,
					DataBuffer.TYPE_BYTE);// allways byte for colormodel!
			FloatProcessor fp = new FloatProcessor(w, h, floatArray, cm);
			return new ImagePlus("", fp);
		}else {
			return null;
		}
	}
	
	//http://dicomiseasy.blogspot.com/2012/08/chapter-12-pixel-data.html
	public ImageProcessor transformRGB2Processor(int w, int h, byte[] pixelsRaw) {
		if (pixelsRaw instanceof byte[]) {
			int size = w*h;
			byte red[] = new byte[size];
			byte green[] = new byte[size];
			byte blue[] = new byte[size];
			if (!banded) {//rgbrgb...
				for (int i = 0; i < size; i++) {
					red[i] = pixelsRaw[i*3];
					green[i] = pixelsRaw[i*3+1];
					blue[i] = pixelsRaw[i*3+2];
				}
				ColorProcessor cp = new ColorProcessor(w, h);
				cp.setRGB(red, green, blue);
				return cp;
			}else{//rrrgggbbb...
				for (int i = 0; i < size; i++) {
					red[i] = pixelsRaw[i];
					green[i] = pixelsRaw[i+size];
					blue[i] = pixelsRaw[i+(2*size)];
				}
				ColorProcessor cp = new ColorProcessor(w, h);
				cp.setRGB(red, green, blue);
				return cp;
			}
		} else {
			return null;
		}
	}
	
	//int[] to RGB imageplus
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
			if(!banded) {
				int[] rgb = (int[]) pixels;
				ColorProcessor cp = new ColorProcessor(w, h, rgb);
				return new ImagePlus("", cp);
			}else{
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
					if(itr == size) { stop=i; break;}
					r[itr++] = (byte) ((rgb[i] & 0xff) << 8);
					if(itr == size) { stop=i; break;}
					r[itr++] = (byte) (rgb[i] & 0xff);
					if(itr == size) { stop=i; break;}
				}
				int g_start = stop;
				itr = 0;
				for (int i = g_start; i < size; i++) {
					g[itr++] = (byte) ((rgb[i] & 0xff) << 16);
					if(itr == size) { stop=i; break;}
					g[itr++] = (byte) ((rgb[i] & 0xff) << 8);
					if(itr == size) { stop=i; break;}
					g[itr++] = (byte) (rgb[i] & 0xff);
					if(itr == size) { stop=i; break;}
				}
				int b_start = stop;
				itr = 0;
				for (int i = b_start; i < size; i++) {
					b[itr++] = (byte) ((rgb[i] & 0xff) << 16);
					if(itr == size) { stop=i; break;}
					b[itr++] = (byte) ((rgb[i] & 0xff) << 8);
					if(itr == size) { stop=i; break;}
					b[itr++] = (byte) (rgb[i] & 0xff);
					if(itr == size) { stop=i; break;}
				}
				ColorProcessor cp = new ColorProcessor(w, h);
				cp.setRGB(r, g, b);
				return new ImagePlus("", cp);
			}
		} else {
			return null;
		}
	}
	
	private float[] doubleArray2floatArray(double[] pixels) {
		if(pixels == null) {
			return null;
		}
		float[] p = new float[pixels.length];
		int i = 0;
		for(double a:pixels) {
			p[i++] = (float)a;
		}
		return p;
	}
	
	public void bgr2rgb(byte[] bs) {
		for (int i = 0, j = 2; j < bs.length; i += 3, j += 3) {
			byte b = bs[i];
			bs[i] = bs[j];
			bs[j] = b;
		}
	}
	
}
