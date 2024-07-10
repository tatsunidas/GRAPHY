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
package com.vis.dicom.image;

import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOInvalidTreeException;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import com.vis.core.log.Log;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.VR;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author tatsunidas
 */
public class BufferedImageUtils {
	
	private BufferedImageUtils() {}

    private static int[] bandOffsets(int samples) {
		int[] offsets = new int[samples];
		for (int i = 0; i < samples; i++)
			offsets[i] = i;
		return offsets;
	}

    public static BufferedImage[] bulkToImage(DicomImage noCompress) {
    	
    	int numOfFrame = noCompress.getCore().getInt(Tag.Number​Of​Frames, 1);
    	BufferedImage[] images = new BufferedImage[numOfFrame];
    	int w = noCompress.getCore().getInt(Tag.Columns, 0);
    	int h = noCompress.getCore().getInt(Tag.Rows, 0);
    	PhotometricInterpretation pmi = PhotometricInterpretation
				.fromString(noCompress.getCore().getString(Tag.Photometric​Interpretation, "MONOCHROME2"));
    	boolean signed = noCompress.getCore().getInt(Tag.Pixel​Representation, -1) == 0;
    	boolean banded = noCompress.getCore().getInt(Tag.Planar​Configuration, 0) != 0;
    	int samples = noCompress.getCore().getInt(Tag.Samples​Per​Pixel, 1);
		int bitsAllocated = noCompress.getCore().getInt(Tag.Bits​Allocated, 8);
		ColorSpace cs = samples >= 3 ? ColorSpace.getInstance(ColorSpace.CS_sRGB):ColorSpace.getInstance(ColorSpace.CS_GRAY);
		int dataType = dataType(bitsAllocated, signed, samples);
		//int imageType = samples >= 3 ? BufferedImage.TYPE_3BYTE_BGR:BufferedImage.TYPE_BYTE_GRAY;
    	for(int i=0; i<numOfFrame; i++) {
    		byte[] pixels = (byte[]) noCompress.getPixelData(i);
    		DataBuffer buffer = new DataBufferByte(pixels, pixels.length);
    		if(banded) {
    			/*
        		 * カラーの場合、DataBufferに入れるbyte[]を、bandedな2d-arrayに変換しないといけないかも。
        		 * その場合は。インデックスを調整して。
        		 * bank index : new int[]{0,1,2}, bandOffset : new int[]{0, 0, 0}
        		 * [
        		 *  [rrr]
        		 *   [ggg]
        		 *   [bbb]
        		 * ]
        		 */
    			ColorModel cmodel = pmi.createColorModel(bitsAllocated, dataType, cs,noCompress.getCore());
    			//SampleModel sampleModel = cmodel.createCompatibleSampleModel(w, h);
				WritableRaster raster = WritableRaster.createBandedRaster(buffer, w, h, w, new int[]{0,0,0}, new int[]{0, w*h, w*h*2}, null);
				BufferedImage dst = new BufferedImage(cmodel, raster, false /*preMultipliedAlpha*/, null/*properties*/);
				images[i] = dst;
    		}else {
    			ColorModel cmodel = pmi.createColorModel(bitsAllocated, dataType, cs,noCompress.getCore());
    			SampleModel sampleModel = cmodel.createCompatibleSampleModel(w, h);
    			WritableRaster raster = Raster.createWritableRaster(sampleModel, buffer, null);
    			BufferedImage dst = new BufferedImage(cmodel, raster, false /*preMultipliedAlpha*/, null/*properties*/);
				images[i] = dst;
    		}
    	}
    	return images;
    }
    
    public static BufferedImage convertColor(BufferedImage bi, ColorModel colorModel) {
        BufferedImage dest = new BufferedImage(colorModel,
                Raster.createWritableRaster(
                        colorModel.createCompatibleSampleModel(
                                bi.getWidth(),
                                bi.getHeight()),
                        null),
                false,
                null);
        new ColorConvertOp(null).filter(bi, dest);
        return dest;
    }

    public static BufferedImage convertPalettetoRGB(BufferedImage src, BufferedImage dst) {
        ColorModel pcm = src.getColorModel();
        if (!(pcm instanceof PaletteColorModel || pcm instanceof IndexColorModel)) {
            throw new UnsupportedOperationException(
                "Cannot convert " + pcm.getClass().getName() + " to RGB");
        }

        int width = src.getWidth();
        int height = src.getHeight();
        if (dst == null) {
            ColorModel cmodel = new ComponentColorModel(pcm.getColorSpace(), new int[]{8, 8, 8},
                    false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
            SampleModel sampleModel = cmodel.createCompatibleSampleModel(width, height);
            DataBuffer dataBuffer = sampleModel.createDataBuffer();
            WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null);
            dst = new BufferedImage(cmodel, rasterDst, false, null);
        }
        WritableRaster rasterDst = dst.getRaster();
        WritableRaster raster = src.getRaster();
        byte[] b = new byte[3];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int rgb = pcm.getRGB(raster.getSample(j, i, 0));
                b[0] = (byte) ((rgb >> 16) & 0xff);
                b[1] = (byte) ((rgb >> 8) & 0xff);
                b[2] = (byte) (rgb & 0xff);
                rasterDst.setDataElements(j, i, b);
            }
        }
        return dst;
    }

    /**
     * ???
     * Use ShortProcessor instead.
     * tatsuaki
     * @param src
     * @param dst
     * @return
     */
    public static BufferedImage convertShortsToBytes(BufferedImage src, BufferedImage dst) {
        if (dst == null) {
            dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        }
        DataBuffer srcBuffer = src.getRaster().getDataBuffer();
        short[] srcData = srcBuffer instanceof DataBufferUShort
                ? ((DataBufferUShort) srcBuffer).getData()
                : ((DataBufferShort) srcBuffer).getData();
        byte[] dstData = ((DataBufferByte) dst.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < srcData.length; i++) {
            dstData[i] = (byte) srcData[i];
        }
        return dst;
    }
    
    public static BufferedImage convertToIntRGB(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        if (cm instanceof DirectColorModel)
            return bi;

        if (cm.getNumComponents() != 3)
            throw new IllegalArgumentException("ColorModel: " + cm);

        WritableRaster raster = bi.getRaster();
        if (cm instanceof PaletteColorModel)
            return ((PaletteColorModel) cm).convertToIntDiscrete(raster);

        BufferedImage intRGB = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics graphics = intRGB.getGraphics();
        try {
            graphics.drawImage(bi, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return intRGB;
    }
    
	public static BufferedImage convertYBRtoRGB(BufferedImage src, BufferedImage dst) {
        if (src.getColorModel().getTransferType() != DataBuffer.TYPE_BYTE) {
            throw new UnsupportedOperationException(
                "Cannot convert color model to RGB: unsupported transferType" + src.getColorModel().getTransferType());
        }
        if (src.getColorModel().getNumComponents() != 3) {
            throw new IllegalArgumentException("Unsupported colorModel: " + src.getColorModel());
        }

        int width = src.getWidth();
        int height = src.getHeight();
        if (dst == null) {
            ColorModel cmodel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), new int[]{8, 8, 8},
                    false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
            SampleModel sampleModel = cmodel.createCompatibleSampleModel(width, height);
            DataBuffer dataBuffer = sampleModel.createDataBuffer();
            WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null);
            dst = new BufferedImage(cmodel, rasterDst, false, null);
        }
        WritableRaster rasterDst = dst.getRaster();
        WritableRaster raster = src.getRaster();
        ColorSpace cs = src.getColorModel().getColorSpace();
        ColorSpace dstcs = dst.getColorModel().getColorSpace();
        byte[] ba = new byte[3];
        float[] fba = new float[3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.getDataElements(x, y, ba);
                for (int i = 0; i < 3; i++) {
                    fba[i] = (ba[i] & 0xff) / 255f;
                }
                float[] rgb = cs.toRGB(fba);
                float[] color = dstcs.fromRGB(rgb);
                for (int i = 0; i < 3; i++) {
                    ba[i] = (byte) (color[i] * 255 + 0.5f);
                }
                rasterDst.setDataElements(x, y, ba);
            }
        }
        return dst;
    }

	private static void copyRGBPixelDataTo(Raster raster, byte[] dest, int offset) {
        ComponentSampleModel csb = getComponentSampleModel(raster);
        int pixelStride = csb.getPixelStride();
        int scanlineStride = csb.getScanlineStride();
        int h = csb.getHeight();
        int w = csb.getWidth();
        int r = csb.getOffset(0, 0, 0);
        int g = csb.getOffset(0, 0, 1);
        int b = csb.getOffset(0, 0, 2);
        byte[] src = getData(raster);
        for (int y = 0, j = offset; y < h; y++) {
            for (int x = 0, i = y * scanlineStride; x < w; x++, i += pixelStride) {
                dest[j++] = src[i + r];
                dest[j++] = src[i + g];
                dest[j++] = src[i + b];
            }
        }
    }
	
	/**
	 * 
	 * @param imp
	 * @param isMultiFrame : imp.getStackSize() > 1 ? true:false;
	 * @return
	 */
	public static BufferedImage[] createBufferedImage(ImagePlus imp, boolean isMultiFrame) {
		if(!isMultiFrame) {
			BufferedImage bi = createBufferedImage(imp);
			return new BufferedImage[] {bi};
		}
		int size = imp.getNFrames();
		BufferedImage[] images = new BufferedImage[size];
		int type = imp.getType();
		for(int i=1;i<=size;i++) {
			images[i-1] = createBufferedImage(imp.getStack().getProcessor(i), type);
		}
		return images;
	}
	
	public static BufferedImage createBufferedImage(ImagePlus singleFrame) {
		boolean isMultiFrame = singleFrame.getStackSize() > 1 ? true:false;
		if(isMultiFrame) {
			Log.logger.info("This images are multiframes. return null.");
			return null;
		}
		return createBufferedImage(singleFrame.getProcessor(), singleFrame.getType());
	}
	
	public static BufferedImage createBufferedImage(ImageProcessor singleFrame, int imageType) {

		boolean signed = singleFrame.isSigned16Bit();
		int type = imageType;
		int samples = singleFrame.getNChannels();//channels
		
    	int w = singleFrame.getWidth();
    	int h = singleFrame.getHeight();
    	
//    	PhotometricInterpretation pmi = null;
//    	if(type == ImagePlus.GRAY8 || type == ImagePlus.GRAY16 || type == ImagePlus.GRAY32) {
//    		pmi = PhotometricInterpretation.fromString("MONOCHROME2");
//    	}else {
//    		pmi = PhotometricInterpretation.fromString("RGB");
//    	}
    	
//    	boolean banded = true; // imageplus always banded when rgb ??
    	
		int bitsAllocated = singleFrame.getBitDepth();
		ColorSpace cs = samples >= 3 ? ColorSpace.getInstance(ColorSpace.CS_sRGB):ColorSpace.getInstance(ColorSpace.CS_GRAY);
		int dataType = dataType(bitsAllocated, signed, samples);
		
		if(type == ImagePlus.GRAY8 && samples == 1) {
			DataBufferByte dbb = (DataBufferByte)singleFrame.getBufferedImage().getData().getDataBuffer();
			ColorModel cmodel = ColorModelFactory.createRGBColorModel(bitsAllocated, dataType, cs);
			WritableRaster raster = Raster.createWritableRaster(cmodel.createCompatibleSampleModel(w, h), dbb, null);
			return new BufferedImage(cmodel, raster, false /*preMultipliedAlpha*/, null/*properties*/);
		}else if(type == ImagePlus.GRAY16 && samples == 1) {
			ShortProcessor img_16 = (ShortProcessor) singleFrame;
			if(img_16.isSigned16Bit()) {
				// get pixels array reference
				final short[] pix = ((short[]) img_16.getPixels()).clone();//ushort in imp
				// adjust raw pixel values
				for (int i=0; i<pix.length; i++) {
					pix[i] += 32768;
				}
				DataBufferShort dbs = new DataBufferShort(pix, pix.length);
				ColorModel cmodel = ColorModelFactory.createRGBColorModel(bitsAllocated, dataType, cs);
				WritableRaster raster = Raster.createWritableRaster(cmodel.createCompatibleSampleModel(w, h), dbs, null);
				return new BufferedImage(cmodel, raster, false /*preMultipliedAlpha*/, null/*properties*/);
			}else {
				DataBufferUShort dbs = (DataBufferUShort)img_16.get16BitBufferedImage().getData().getDataBuffer();
				ColorModel cmodel = ColorModelFactory.createRGBColorModel(bitsAllocated, dataType, cs);
				WritableRaster raster = Raster.createWritableRaster(cmodel.createCompatibleSampleModel(w, h), dbs, null);
				return new BufferedImage(cmodel, raster, false /*preMultipliedAlpha*/, null/*properties*/);
			}
		}else if(type == ImagePlus.GRAY32 && samples == 1) {//gray 32
			FloatProcessor img_32 = (FloatProcessor) singleFrame;
			final float[] pix = (float[]) img_32.getPixels();
			DataBufferFloat db = new DataBufferFloat(pix, pix.length);
			ColorModel cmodel = ColorModelFactory.createRGBColorModel(bitsAllocated, dataType, cs);
			WritableRaster raster = Raster.createWritableRaster(cmodel.createCompatibleSampleModel(w, h), db, null);
			return new BufferedImage(cmodel, raster, false /*preMultipliedAlpha*/, null/*properties*/);
		}else if(type == ImagePlus.COLOR_RGB || type == ImagePlus.COLOR_256) {//RGB
			return singleFrame.getBufferedImage();
		}else {//type 0 unknown
			Log.logger.warning("Unknow image type, return null...");
			return null;
		}
	}

    public static BufferedImage createNullBufferedImage(int bitsAllocated, int bitsStored, int samples, int cols, int rows,
			boolean banded, boolean signed) {
		int dataType = DataBuffer.TYPE_BYTE;
		if (bitsAllocated > 8 && bitsAllocated <= 16) {
			dataType = signed ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
		} else if (bitsAllocated == 32) {
			dataType = DataBuffer.TYPE_FLOAT;
		} else if (bitsAllocated == 64) {
			dataType = DataBuffer.TYPE_DOUBLE;
		}
		ComponentColorModel cm = samples == 1
				? new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { bitsStored }, false, // hasAlpha
						false, // isAlphaPremultiplied,
						Transparency.OPAQUE, dataType)
				: new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
						new int[] { bitsStored, bitsStored, bitsStored }, false, // hasAlpha
						false, // isAlphaPremultiplied,
						Transparency.OPAQUE, dataType);

		SampleModel sm = banded ? new BandedSampleModel(dataType, cols, rows, samples)
				: new PixelInterleavedSampleModel(dataType, cols, rows, samples, cols * samples, bandOffsets(samples));
		WritableRaster raster = Raster.createWritableRaster(sm, null);
		return new BufferedImage(cm, raster, false, null);
	}

    private static int dataType(int bit, boolean signed, int sample) {
    	if(bit == 8 && sample == 1) {
    		return DataBuffer.TYPE_BYTE;
    	}else if(bit == 8 && sample >= 3) {
    		return DataBuffer.TYPE_BYTE;//INT ? 
    	}else if((bit > 8 && bit <= 16) && signed && sample == 1) {
    		return DataBuffer.TYPE_SHORT;
    	}else if((bit > 8 && bit <= 16) && !signed && sample == 1) {
    		return DataBuffer.TYPE_USHORT;
    	}else if(bit == 32 && sample == 1) {
    		return DataBuffer.TYPE_FLOAT;
    	}else if(bit == 64 && sample == 1) {
    		return DataBuffer.TYPE_DOUBLE;
    	}
    	return DataBuffer.TYPE_UNDEFINED;
    }

    private static Node getChildNode(Node root, String name) throws IIOInvalidTreeException {
        Node child = root.getFirstChild();
        while (child != null) {
            if (name.equals(child.getLocalName())) {
                return child;
            }
            child = child.getNextSibling();
        }
        throw new IIOInvalidTreeException("Required child " + name + " not present!", root);
    }

    private static ComponentSampleModel getComponentSampleModel(Raster raster) {
        SampleModel sb = raster.getSampleModel();
        if (!(sb instanceof ComponentSampleModel)) {
            throw new UnsupportedOperationException(sb.toString());
        }
        return (ComponentSampleModel) sb;
    }

    private static byte[] getData(Raster raster) {
        DataBuffer dataBuffer = raster.getDataBuffer();
        if (!(dataBuffer instanceof DataBufferByte) || dataBuffer.getNumBanks() > 1) {
            throw new UnsupportedOperationException(raster.toString());
        }
        return ((DataBufferByte) dataBuffer).getData();
    }

    private static String getDelayTime(Node node) throws IIOInvalidTreeException {
        return getStringAttribute(getChildNode(node, "GraphicControlExtension"), "delayTime");
    }

    private static int getIntAttribute(Node node, String name) throws IIOInvalidTreeException {
        try {
            return Integer.parseInt(getStringAttribute(node, name));
        } catch (NumberFormatException e) {
            throw new IIOInvalidTreeException("Bad value for " + node.getNodeName() + " attribute " + name + "!", node);
        }
    }


    private static Node getMetadata(IIOImage iioImage) {
        return iioImage.getMetadata().getAsTree("javax_imageio_gif_image_1.0");
    }

    private static String getStringAttribute(Node node, String name) throws IIOInvalidTreeException {
        Node attr = node.getAttributes().getNamedItem(name);
        if (attr == null) {
            throw new IIOInvalidTreeException("Required attribute " + name + " not present!", node);
        }
        return attr.getNodeValue();
    }

    private static void mergeFrame(Graphics graphics, BufferedImage src, Node metadata)
            throws IIOInvalidTreeException {
        Node imageDescriptor = getChildNode(metadata, "ImageDescriptor");
        graphics.drawImage(src,
                getIntAttribute(imageDescriptor, "imageLeftPosition"),
                getIntAttribute(imageDescriptor, "imageTopPosition"),
                null);
    }

    public static BufferedImage replaceColorModel(BufferedImage bi, ColorModel colorModel) {
        return new BufferedImage(colorModel, bi.getRaster(), false, null);
    }

    private static void setFrameTimeVector(DicomObject attrs, List<String> delayTimes) {
        String delayTime0 = delayTimes.get(0);
        for (int i = 1; i < delayTimes.size(); i++) {
            if (!delayTime0.equals(delayTimes.get(i))) {
                attrs.setString(Tag.Frame​Time​Vector, VR.DS, toFrameTimes(delayTimes));
                attrs.setInt(Tag.Frame​Increment​Pointer, VR.AT, Tag.Frame​Time​Vector);
            }
        }
        attrs.setString(Tag.Frame​Time, VR.DS, toFrameTime(delayTime0));
        attrs.setInt(Tag.Frame​Increment​Pointer, VR.AT, Tag.Frame​Time​Vector);
    }

    private static String toFrameTime(String delayTime) {
        return "0".equals(delayTime) ? "0" : (delayTime + "0");
    }

    private static String[] toFrameTimes(List<String> delayTimes) {
        String[] frameTimes = new String[delayTimes.size()];
        for (int i = 0; i < frameTimes.length; i++) {
            frameTimes[i] = toFrameTime(delayTimes.get(i));
        }
        return frameTimes;
    }

    /**
     * Set Image Pixel Module Attributes from Buffered Image. Supports Buffered Images with ColorSpace GRAY or RGB
     * and with a DataBuffer containing one bank of unsigned byte data.
     *
     * @param bi Buffered Image
     * @param attrs Data Set to supplement with Image Pixel Module Attributes or {@code null}
     * @return Data Set with included Image Pixel Module Attributes
     * @throws UnsupportedOperationException if the ColorSpace or DataBuffer of the Buffered Image is not supported
     */
    public static DicomObject toImagePixelModule(BufferedImage bi, DicomObject attrs) {
        ColorModel cm = bi.getColorModel();
        if (cm instanceof IndexColorModel || cm instanceof PaletteColorModel) {
            bi = convertPalettetoRGB(bi, null);
            cm = bi.getColorModel();
        }
        ColorSpace cs = cm.getColorSpace();
        Raster raster = bi.getRaster();
        int rows = raster.getHeight();
        int columns = raster.getWidth();
        switch (cs.getType()) {
            case ColorSpace.TYPE_GRAY:
                return toImagePixelModule(1, "MONOCHROME2", rows, columns,
                        toMonochrome2PixelData(raster), attrs);
            case ColorSpace.TYPE_RGB:
                return toImagePixelModule(3, "RGB", rows, columns,
                        toRGBPixelData(raster), attrs);
            default:
                throw new UnsupportedOperationException(toString(cs));
        }
    }

    /**
     * Set Image Pixel Module Attributes from read image. Supports reading animated GIFs and single frame images
     * with ColorSpace GRAY or RGB and with a DataBuffer containing one bank of unsigned byte data.
     *
     * @param reader image reader
     * @param attrs Data Set to supplement with Image Pixel Module Attributes or {@code null}.
     * @return Data Set with included Image Pixel Module Attributes.
     * @throws IOException if an error occurs during reading.
     * @throws UnsupportedOperationException if the read image is not supported.
     */
    public static DicomObject toImagePixelModule(ImageReader reader, DicomObject attrs) throws IOException {
        IIOImage firstFrame = reader.readAll(0, null);
        BufferedImage bi = (BufferedImage) firstFrame.getRenderedImage();
        IIOImage nextFrame;
        try {
            nextFrame = reader.readAll(1, null);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IOException("JDK-7132728: stringtable overflow in GIFImageReader", e);
        } catch (IndexOutOfBoundsException e) {
            return toImagePixelModule(bi, attrs);
        }
        List<byte[]> frames = new ArrayList<>();
        List<String> delayTimes = new ArrayList<>();
        BufferedImage rgb = convertPalettetoRGB(bi, null);
        frames.add(toRGBPixelData(rgb.getRaster()));
        delayTimes.add(getDelayTime(getMetadata(firstFrame)));
        Graphics graphics = bi.getGraphics();
        try {
            while (true) {
                Node metadata = getMetadata(nextFrame);
                mergeFrame(graphics, (BufferedImage) nextFrame.getRenderedImage(), metadata);
                frames.add(toRGBPixelData(convertPalettetoRGB(bi, rgb).getRaster()));
                delayTimes.add(getDelayTime(metadata));
                nextFrame = reader.readAll(frames.size(), null);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IOException("JDK-7132728: stringtable overflow in GIFImageReader", e);
        } catch (IndexOutOfBoundsException ignore) {
        }
        graphics.dispose();
        attrs = toImagePixelModule(3, "RGB", bi.getHeight(), bi.getWidth(), toPixeldata(frames), attrs);
        attrs.setInt(Tag.Number​Of​Frames, VR.IS, frames.size());
        setFrameTimeVector(attrs, delayTimes);
        return attrs;
    }

    public static DicomObject toImagePixelModule(int samples, String pmi, int rows,  int columns,
            byte[] pixelData, DicomObject attrs) {
        if (attrs == null) {
            attrs = DicomObject.newDicomObject();
        }
        attrs.setInt(Tag.Samples​Per​Pixel, VR.US, samples);
        attrs.setString(Tag.Photometric​Interpretation, VR.CS, pmi);
        if (samples > 1) {
            attrs.setInt(Tag.Planar​Configuration, VR.US, 0);
        }
        attrs.setInt(Tag.Rows, VR.US, rows);
        attrs.setInt(Tag.Columns, VR.US, columns);
        attrs.setInt(Tag.Bits​Allocated, VR.US, 8);
        attrs.setInt(Tag.Bits​Stored, VR.US, 8);
        attrs.setInt(Tag.High​Bit, VR.US, 7);
        attrs.setInt(Tag.Pixel​Representation, VR.US, 0);
        attrs.setBytes(Tag.Pixel​Data, VR.OB, pixelData);
        return attrs;
    }

    private static byte[] toMonochrome2PixelData(Raster raster) {
        ComponentSampleModel csb = getComponentSampleModel(raster);
        int pixelStride = csb.getPixelStride();
        int scanlineStride = csb.getScanlineStride();
        int h = csb.getHeight();
        int w = csb.getWidth();
        int offset = csb.getOffset(0, 0);
        byte[] src = getData(raster);
        byte[] dest = new byte[h * w];
        for (int y = 0, j = 0; y < h; y++) {
            for (int x = 0, i = y * scanlineStride; x < w; x++, i += pixelStride) {
                dest[j++] = src[i + offset];
            }
        }
        return dest;
    }

    private static byte[] toPixeldata(List<byte[]> frames) {
        byte[] pixeldata = new byte[frames.get(0).length * frames.size()];
        int pos = 0;
        for (byte[] frame : frames) {
            System.arraycopy(frame, 0, pixeldata, pos, frame.length);
            pos += frame.length;
        }
        return pixeldata;
    }

    private static byte[] toRGBPixelData(Raster raster) {
        byte[] dest = new byte[raster.getHeight() * raster.getWidth() * 3];
        copyRGBPixelDataTo(raster, dest, 0);
        return dest;
    }

    private static String toString(ColorSpace cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = cs.getNumComponents(); i < n; i++) {
            sb.append(i > 0 ? ", 0" : "ColorSpace[").append(cs.getName(i));
        }
        return sb.append(']').toString();
    }

}
