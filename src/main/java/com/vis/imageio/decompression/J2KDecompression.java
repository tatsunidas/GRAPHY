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

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReaderSpi;

public class J2KDecompression {
	
	public static Object inflate(boolean bigEndian, byte[] pixelData) {
		try {
			ImageReader reader = new J2KImageReaderSpi().createReaderInstance();
			
//			Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("jpeg2000");
//			ImageReader reader = (ImageReader) iter.next();
			
			ByteBuffer byteBuf = null;
			if(bigEndian) {
				byteBuf = ByteBuffer.wrap(pixelData).order(ByteOrder.LITTLE_ENDIAN);
			}
			
			byte[] pixels = byteBuf.array();
			
			/*Get image input stream*/
			InputStream is = new ByteArrayInputStream(pixels);
			ImageInputStream iis = ImageIO.createImageInputStream(is);
			
			/* set read param and set input pixels to reader */
			boolean  seekForwardOnly = false; 
			boolean ignoreMetadata = false;
			reader.setInput(iis, seekForwardOnly, ignoreMetadata);
			
			ImageReadParam param = reader.getDefaultReadParam();
			
			/* decompress pixels */
			RenderedImage rimg = reader.readAsRenderedImage(0, param);//important
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
}
