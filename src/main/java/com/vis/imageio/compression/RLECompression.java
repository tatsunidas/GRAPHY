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
package com.vis.imageio.compression;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;

import com.vis.dicom.DicomObject;

import javax.imageio.plugins.bmp.BMPImageWriteParam;

@Deprecated
public class RLECompression {

	private BMPImageWriteParam prepareParam(ImageWriter writer) {
		BMPImageWriteParam param = (BMPImageWriteParam) writer.getDefaultWriteParam();
		param.setCompressionMode(BMPImageWriteParam.MODE_EXPLICIT);
		param.setCompressionType("BI_RLE8");// BMPConstants.BI_RLE8
		return param;
	}
	
	public void compress(DicomObject dcm) {
//		if(dcm.getInt(Tag.Number​Of​Frames, -1) == -1) {
//			byte[] compressed = compress(BufferedImageUtils.bulkToImage(dcm)[0]);
//			int bitsCompressed = dcm.getInt(Tag.Bits​Allocated, 8);
//			dcm.setPixelData(0, dcm.getInt(Tag.Columns, 0), dcm.getInt(Tag.Rows, 0), dcm.getInt(Tag.Samples​Per​Pixel, 0), bitsCompressed, compressed);
//		}else {
//			//check MultiframeExtractor
//			int num = dcm.getInt(Tag.Number​Of​Frames, 0);
//			BufferedImage[] comps = BufferedImageUtils.bulkToImage(dcm);
//			int bitsCompressed = getCompressedBitsPerSample(toCompressTS, dcm.getCore().getInt(Tag.Bits​Allocated, 8));
//			for(int i=0;i<num; i++) {
//				byte[] compressed = compress(comps[i], toCompressTS, ratio, lossless);
//				dupImg.setPixelData(i, dcm.getCore().getInt(Tag.Columns, 0), dcm.getCore().getInt(Tag.Rows, 0), dcm.getCore().getInt(Tag.Samples​Per​Pixel, 0), bitsCompressed, compressed);
//			}
//		}
	}

	public byte[] compress(BufferedImage rawImage) {

		byte[] compressed = null;
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageOutputStream ios = ImageIO.createImageOutputStream(baos);) {

			Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("BMP");
			if (writers != null && writers.hasNext()) {
				ImageWriter writer = (ImageWriter) writers.next();
				BMPImageWriteParam param = prepareParam(writer);
				writer.setOutput(ios);
				IIOMetadata metadata = writer.getDefaultImageMetadata(
						new ImageTypeSpecifier(rawImage.getColorModel(), rawImage.getSampleModel()), param);
				writer.write(metadata, new IIOImage(rawImage, null/* no thumbnails */, metadata), param);
				compressed = baos.toByteArray();
				writer.dispose();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return compressed;
	}

}
