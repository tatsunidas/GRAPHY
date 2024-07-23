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
import java.io.FileNotFoundException;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.UnsupportedFormatException;
import org.jcodec.common.SeekableDemuxerTrack;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;

import com.vis.core.log.Log;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * Supported format;AVI (no-compress), MP4-H264, MP4-AVC, ISO BMF, MOV(MPEG-4 multimedia container file format developed by Apple)
 *  
 *  When avi file is inputed, it will be processed by IJ.
 *  
 * @author tatsunidas
 *
 */
class VideoReaderJCodec implements VideoReader{
	
	ImagePlus stack = null;
	File video;
	String fileName = null;
	final String MIMETYPE;
	
	public VideoReaderJCodec(String MIMETYPE, File video) {
		this.MIMETYPE = MIMETYPE;
		this.video = video;
		this.fileName = video.getName();
	}

	public ImagePlus read() {
		FileChannelWrapper in = null;
		try {
			in = NIOUtils.readableChannel(video);
			FrameGrab grab = FrameGrab.createFrameGrab(in);
			stack = convert2ImagePlus(grab);
		} catch (FileNotFoundException e) {
			System.err.println("File not found: ");
			stack = null;
		} catch (IOException e) {
			System.err.println("IOException: ");
			stack = null;
		} catch (UnsupportedFormatException e) {
			Log.logger.warning("This video file is not readable.");
			stack = null;
		} catch (JCodecException e) {
			Log.logger.warning(e.getMessage());
			stack = null;
		} finally {
			// Ensure the file channel is closed
			NIOUtils.closeQuietly(in);
		}
		return stack;
	}
	
	private ImagePlus convert2ImagePlus(FrameGrab grab) {
		if(grab == null) {
			return null;
		}
		ImageStack stack = new ImageStack();
		Picture pic = null;
		SeekableDemuxerTrack track = grab.getVideoTrack();
		double total_time = track.getMeta().getTotalDuration();
		int numOfFrames = track.getMeta().getTotalFrames();
		double flops = total_time/numOfFrames; 
		try {
			while(null != (pic = grab.getNativeFrame())) {
				BufferedImage bi = AWTUtil.toBufferedImage(pic);
				int type = bi.getType();
				ImageProcessor ip;
				if(type == BufferedImage.TYPE_BYTE_GRAY || type == BufferedImage.TYPE_BYTE_BINARY) {
					ip = new ByteProcessor(bi);
				}else if(type == BufferedImage.TYPE_USHORT_GRAY) {
					ip = new ShortProcessor(bi);
				}else {
					ip = new ColorProcessor(bi);
				}
				stack.addSlice(ip);
			}
		} catch (IOException e) {
			Log.logger.warning("Can not read video file : "+e.getMessage());
			return null;
		}
		ImagePlus imp = new ImagePlus(fileName, stack);
		imp.getCalibration().fps = flops;
		return imp;
	}
	
	public ImagePlus getImagePlus() {
		return stack;
	}
	
	public int getNumOfFrames() {
		if(stack == null) return -1;
		return stack.getNSlices();
	}
	
	public double getFrameRate() {
		if(stack == null) return -1;
		return stack.getCalibration().fps;
	}
	
	@Override
	public String mimeType() {
		return MIMETYPE;
	}
}
