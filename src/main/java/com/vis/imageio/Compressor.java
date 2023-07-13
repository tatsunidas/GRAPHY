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

import java.util.logging.Level;

import javax.imageio.ImageIO;

import com.vis.core.log.Log;
import com.vis.dicom.UID;

public class Compressor {
	
	/**
	 * https://github.com/blezek/nifi-dicom/blob/
	 * 1a669c5cc23f0ad52c3f51ac43c5d1fe9ffc9c2d/src/main/java/com/pixelmed/dicom/
	 * CompressedFrameEncoder.java#L56
	 */
	
	static {
		ImageIO.scanForPlugins();
	}
	
	public static byte[] compress(byte[] pixels, UID fromTS, UID toCompressTS) {
		
		if(pixels == null || fromTS == null || toCompressTS == null) {
			return null;
		}
		
		if(!isCompatibleTransferSyntax(fromTS, toCompressTS)) {
			return null;
		}
		return compress(pixels, toCompressTS);
	}
	
	private static byte[] compress(byte[] pixels, UID toCompressTS) {
		
		if(toCompressTS == UID.JPEGBaseline8Bit) {
			
		}else if(toCompressTS == UID.JPEG2000) {
			
		}else if(toCompressTS == UID.JPEG2000Lossless) {
			
		}else if(toCompressTS == UID.JPEGLSLossless) {
			
		}else if(toCompressTS == UID.RLELossless) {
			
		}
		return null;
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
			Log.logger.log(Level.WARNING, "This UID " + to + "does not accepted by graphy-image-io.");
			return false;
		}
		
		return true;
	}
	
	

}
