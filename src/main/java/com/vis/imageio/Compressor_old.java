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

import java.awt.image.BufferedImage;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import com.vis.core.log.Log;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.image.BufferedImageUtils;
import com.vis.dicom.image.DicomImage;
import com.vis.imageio.compression.J2KCompression;
import com.vis.imageio.compression.JPEGCompression;
import com.vis.imageio.compression.RLECompression;

@SuppressWarnings("deprecation")
public class Compressor_old {
	
	/**
	 * Junk code... delete in future.
	 */
	
	/**
	 * https://github.com/blezek/nifi-dicom/blob/
	 * 1a669c5cc23f0ad52c3f51ac43c5d1fe9ffc9c2d/src/main/java/com/pixelmed/dicom/
	 * CompressedFrameEncoder.java#L56
	 * 
	 * https://github.com/dcm4che/dcm4che/blob/master/dcm4che-imageio/src/main/java/org/dcm4che3/imageio/codec/Compressor.java
	 * 
	 */
	
	static {
		ImageIO.scanForPlugins();
	}
	
	public final static DicomImage compress(DicomImage dcm, UID toCompressTS, Float ratio, Boolean lossless) {
		
		if (dcm == null || toCompressTS == null) {
			return null;
		}

		if (!isCompatibleTransferSyntax(dcm.getTSUID(), toCompressTS)) {
			return null;
		}
		
		if(dcm.getCore().getInt(Tag.Bits​Allocated, 8) > 16) {
			throw new UnsupportedOperationException(
                    "Unsupported BitsAllocated: " + dcm.getCore().getInt(Tag.Bits​Allocated, 8));
		}
		
		if(lossless) {
			ratio = 1.0f;
		}
		
		if(ratio < 0.25f) {
			ratio = 0.25f;
		}
		
		DicomObject dup = DicomObject.newDicomObject(dcm.getCore(), null);
		DicomImage dupImg = DicomImage.newDicomImage(dup, dcm.getTSUID());
		if(!dcm.isMultiFrame()) {
			byte[] compressed = compress(BufferedImageUtils.bulkToImage(dcm)[0], toCompressTS, ratio, lossless);
			int bitsCompressed = getCompressedBitsPerSample(toCompressTS, dcm.getCore().getInt(Tag.Bits​Allocated, 8));
			dupImg.setPixelData(0, dcm.getCore().getInt(Tag.Columns, 0), dcm.getCore().getInt(Tag.Rows, 0), dcm.getCore().getInt(Tag.Samples​Per​Pixel, 0), bitsCompressed, compressed);
		}else {
			//check MultiframeExtractor
			int num = dcm.getCore().getInt(Tag.Number​Of​Frames, 0);
			BufferedImage[] comps = BufferedImageUtils.bulkToImage(dcm);
			int bitsCompressed = getCompressedBitsPerSample(toCompressTS, dcm.getCore().getInt(Tag.Bits​Allocated, 8));
			for(int i=0;i<num; i++) {
				byte[] compressed = compress(comps[i], toCompressTS, ratio, lossless);
				dupImg.setPixelData(i, dcm.getCore().getInt(Tag.Columns, 0), dcm.getCore().getInt(Tag.Rows, 0), dcm.getCore().getInt(Tag.Samples​Per​Pixel, 0), bitsCompressed, compressed);
			}
		}
		return dupImg;
	}
	
	
	@SuppressWarnings("deprecation")
	private final static byte[] compress(BufferedImage bi, UID toCompressTS, Float ratio, Boolean lossless) {
		
		if(toCompressTS == Codec.JPEGBase.uid()) {
			JPEGCompression compr = new JPEGCompression(toCompressTS.uid());
			compr.setQuality(ratio);
			return compr.compress(bi);
		}else if(toCompressTS == Codec.JPEGExtended12Bit.uid()) {
			JPEGCompression compr = new JPEGCompression(toCompressTS.uid());
//			compr.setLossless(false);
//			compr.setEncodingRate(ratio);
			return compr.compress(bi);
		}else if(toCompressTS== UID.JPEG2000) {
			J2KCompression compr = new J2KCompression();
			compr.setLossless(false);
			compr.setEncodingRate(ratio);
			return compr.compress(bi);
		}else if(toCompressTS == UID.JPEG2000Lossless) {
			J2KCompression compr = new J2KCompression();
			compr.setLossless(true);
			return compr.compress(bi);
		}else if(toCompressTS == UID.JPEGLossless) {
			JPEGCompression compr = new JPEGCompression(toCompressTS.uid());
			compr.setQuality(1.0f);
			return compr.compress(bi);
		}else if(toCompressTS == UID.JPEGLSLossless || toCompressTS == UID.JPEGLSNearLossless) {
			JPEGCompression compr = new JPEGCompression(toCompressTS.uid());
			if(lossless) {
				compr.setQuality(1.0f);
			}else {
				compr.setQuality(ratio);
			}
			return compr.compress(bi);
		}else if(toCompressTS == UID.RLELossless) {
			RLECompression compr = new RLECompression();
			return compr.compress(bi);
		}else {
			 throw new UnsupportedOperationException("This uid can not compress ( no compatible uid)");
		}
	}
	
	static boolean isCompatibleTransferSyntax(UID from, UID to) {
		
		if(Codec.isCompressed(from)) {
			/*
			 * Should avoid compress by compress
			 */
			Log.logger.log(Level.SEVERE, "This DicomImage already compressed. return null.");
			return false; 
		}
		
		if(!Codec.availableCodec(to)) {
			Log.logger.log(Level.WARNING, "This UID " + to + "does not accepted by graphy-image-io compression.");
			return false;
		}
		
		return true;
	}
	
	private static int getCompressedBitsPerSample(UID tsUID, int currentBitsAllocated) {
		if(tsUID == UID.JPEGBaseline8Bit) {
			return 8;
		}else if(tsUID == UID.JPEGExtended12Bit) {
			return 12;
		}else{
			return currentBitsAllocated;
		}
	}

}
