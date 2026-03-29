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

import java.io.File;

import ij.ImagePlus;
import ij.plugin.AVI_Reader;

class VideoReaderIJ implements VideoReader{

	ImagePlus stack = null;
	File video;
	String fileName;
	final String MIMETYPE;
	
	public VideoReaderIJ(String MIMETYPE, File video) {
		this.MIMETYPE = MIMETYPE;
		this.video = video;
		this.fileName = video.getName();
	}
	
	@Override
	public ImagePlus read() {
		stack = AVI_Reader.open(video.getAbsolutePath(), true /*virtual*/);
		return stack;
	}

	@Override
	public ImagePlus getImagePlus() {
		return stack;
	}

	@Override
	public int getNumOfFrames() {
		if(stack != null) {
			return stack.getNSlices();
		}
		return -1;
	}

	@Override
	public double getFrameRate() {
		if(stack != null) {
			return stack.getCalibration().fps;
		}
		return -1;
	}

	@Override
	public String mimeType() {
		return MIMETYPE;
	}

}
