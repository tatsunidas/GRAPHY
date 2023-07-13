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

import com.vis.dicom.UID;

public enum Codec {
	JPEGBase(UID.JPEGBaseline8Bit),
	JPEGExtended12Bit(UID.JPEGExtended12Bit),
	JPEG2000(UID.JPEG2000), 
	JPEG2000LOSSLESS(UID.JPEG2000Lossless),
	JPEGLOSSLESS(UID.JPEGLossless), 
	JPEG_LS(UID.JPEGLSLossless),
	RLE(UID.RLELossless);

	com.vis.dicom.UID uid;

	Codec(com.vis.dicom.UID uid) {
		this.uid = uid;
	}

	public static boolean availableCodec(com.vis.dicom.UID uid) {
		for (Codec c : Codec.values()) {
			if (c.uid == uid) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean isCompressed(UID tsuid) {
		if(tsuid == UID.ImplicitVRLittleEndian) {
			return false;
		}else if(tsuid == UID.ExplicitVRLittleEndian) {
			return false;
		}else if(tsuid == UID.DeflatedExplicitVRLittleEndian) {//retired
			return false;
		}else if(tsuid == UID.ExplicitVRBigEndian) {//retired
			return false;
		}else {
			return true;
		}
	}
}
