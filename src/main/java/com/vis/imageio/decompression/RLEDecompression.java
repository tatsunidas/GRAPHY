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
package com.vis.imageio.decompression;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageReaderFactory.ImageReaderParam;
import org.dcm4che3.imageio.plugins.rle.RLEImageReader;

import com.vis.core.util.StringUtils;

@Deprecated
public class RLEDecompression {
	
	/**
	 * Gray or RGB.
	 * @param w
	 * @param h
	 * @param bitsAllocated
	 * @param bitsStored
	 * @param samplesPerPixel
	 * @param pixelRep
	 * @param colorType
	 * @param bigEndian
	 * @param pixelData
	 * @return byte[] or short[]
	 */
	public Object inflateRLE(int w, int h, int bitsAllocated, int bitsStored, int samplesPerPixel, int pixelRep, String colorType, boolean bigEndian, byte[] pixelData){
		//1.2.840.10008.1.2.5:rle:
		ByteBuffer byteBuf = null;
		if(bigEndian) {
			byteBuf = ByteBuffer.wrap(pixelData).order(ByteOrder.LITTLE_ENDIAN);
		}
		byte[] pixels = byteBuf.array();
		/*Get image input stream*/
		InputStream is = new ByteArrayInputStream(pixels);
		ImageInputStream iis = null;
		try {
			iis = ImageIO.createImageInputStream(is);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		//org.dcm4che3.imageio.plugins.rle.RLEImageReader
		ImageReaderFactory.ImageReaderParam readerParam = new ImageReaderParam("rle", "org.dcm4che3.imageio.plugins.rle.RLEImageReader", null, StringUtils.EMPTY_STRING);
		RLEImageReader reader = (RLEImageReader) ImageReaderFactory.getImageReader(readerParam);
		ImageReadParam param = reader.getDefaultReadParam();
		
		
		/* set read param and set input pixels to reader */
		boolean  seekForwardOnly = false; 
		boolean ignoreMetadata = false;
		reader.setInput(iis, seekForwardOnly, ignoreMetadata);
		
		// this approach is also ok.
//		int samples = samplesPerPixel;
//		boolean signed = false;
//		if(bitsAllocated > 8 && pixelRep == 1) {
//			signed = true;
//		}
//		boolean banded = (samples == 3);
//		BufferedImage destImage = createDestBufferedImage(bitsAllocated, samplesPerPixel, w, h, bitsStored, banded, signed);
//		param.setDestination(destImage);
//		try {
//			reader.read(0, param);
//		} catch (IOException e) {
//			e.printStackTrace();
//			return null;
//		}
//		Raster ras = destImage.getData();//also can WritableRaster casting.
//		DataBuffer bf = ras.getDataBuffer();
		
		try {
			RenderedImage rimg = reader.readAsRenderedImage(0, param);
			Raster ras = rimg.getData();//also can WritableRaster casting.
			DataBuffer bf = ras.getDataBuffer();
			if (bf instanceof DataBufferByte) {
				bf = (DataBufferByte) bf;
				return ((DataBufferByte) bf).getData();//byte[] gray or color
			} else if (bf instanceof DataBufferShort) {
				bf = (DataBufferShort) bf;
				return ((DataBufferShort) bf).getData();//short[]
			} else if (bf instanceof DataBufferUShort) {
				bf = (DataBufferUShort) bf;
				return ((DataBufferUShort) bf).getData();//short[]
			} else {
				return null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	protected BufferedImage createDestBufferedImage(int bitsAllocated, int samples, int w, int h, int bitsStored, boolean banded, boolean signed) {
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

		SampleModel sm = banded ? new BandedSampleModel(dataType, w, h, samples)
				: new PixelInterleavedSampleModel(dataType, w, h, samples, w * samples, bandOffsets(samples));
		WritableRaster raster = Raster.createWritableRaster(sm, null);
		return new BufferedImage(cm, raster, false, null);
	}
	
	private int[] bandOffsets(int samples) {
		int[] offsets = new int[samples];
		for (int i = 0; i < samples; i++)
			offsets[i] = i;
		return offsets;
	}

}
