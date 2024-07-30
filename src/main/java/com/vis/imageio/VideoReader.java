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
import java.net.URLConnection;

import com.vis.core.log.Log;
import com.vis.core.util.ImageUtils;

import ij.ImagePlus;

public interface VideoReader {
	
	// lib IDs
	final int IJ = 0;//ImageJ
	final int JMF = 1;
	final int JC = 2;//JCodec
	
	public static VideoReader load(File video){
		try {
			if (ImageUtils.isVideoFile(video.getAbsolutePath())) {
				String MIMETYPE = URLConnection.guessContentTypeFromName(video.getAbsolutePath());
				int lib_id = selectLib(MIMETYPE);
				if (lib_id == IJ) {
					return (VideoReader) new VideoReaderIJ(MIMETYPE, video);
				}else if (lib_id == JMF) {
					return (VideoReader) new VideoReaderJMF(MIMETYPE, video);
				}else if (lib_id == JC) {
					return (VideoReader) new VideoReaderJCodec(MIMETYPE, video);
				}else {
					return null;
				}
			}
		} catch (Exception e) {
			Log.logger.warning("This file is not video format.:\n" + e.getMessage());
		}
		return null;
	}
	
	public ImagePlus read();
	public ImagePlus getImagePlus();
	public int getNumOfFrames();
	public double getFrameRate();
	public String mimeType();

	/**
	 * ImageJ: avi application/x-troff-msvideo
	 * 
	 * JMF: Supported format: https://www.oracle.com/java/technologies/javase/jmf-211-formats.html
	 * 
	 * [Video]
	 * AVI(.avi) without compress
	 * MPEG-1 Video(.mpg)
	 * QuickTime (.mov)
	 * [Audio]
	 * AIFF(.aiff)
	 * GSM(.gsm)
	 * HotMedia(.mvr)
	 * MIDI(.mid)
	 * MPEG Layer II Audio (.mp2)
	 * Sun Audio (.au)
	 * Wave (.wav)
	 * 
	 * JCodec
	 * [Video]
	 * MP4-AVC, MP4-H.264, ISO BMF, Quicktime container
	 * 
	 * @param path
	 * @return
	 */
	static int selectLib(String MIMETYPE) {
		if(MIMETYPE.endsWith("msvideo") || MIMETYPE.endsWith("avi")) {
			return IJ;
		}
		if(MIMETYPE.endsWith("mpeg") || MIMETYPE.endsWith("quicktime")) {
			return JMF;
		}
		if(MIMETYPE.endsWith("mp4")) {
			return JC;
		}
		return -1;
	}
}
