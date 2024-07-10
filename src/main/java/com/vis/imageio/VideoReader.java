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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;

import javax.media.ControllerEvent;
import javax.media.ControllerListener;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NoProcessorException;
import javax.media.Player;
import javax.media.PrefetchCompleteEvent;
import javax.media.RealizeCompleteEvent;
import javax.media.control.FrameGrabbingControl;
import javax.media.control.FramePositioningControl;
import javax.media.util.BufferToImage;

import com.vis.core.util.ImageUtils;
import com.vis.core.util.Platform;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * Audio data was ignored when a video has no supported audio format data.
 * @author tatsunidas
 *
 */
public class VideoReader implements ControllerListener {
	
	Player reader;
	FramePositioningControl framePositioningControl; 
	FrameGrabbingControl frameGrabbingControl;
	Integer numOfFrames;
	Integer flops;
	String fileName;
	
	public VideoReader() {}
	
	public void read(File video) throws Exception {
		if(!ImageUtils.isVideoFile(video.getAbsolutePath())) {
			throw new Exception("No video file inputed !");
		}
		if(!readableFormat(video.getAbsolutePath())) {
			System.out.println("This video file is not readable.");
			return;
		}
		constractPlayer(video);
		if(reader == null) {
			System.out.println("Sorry, cannot load this video file...Please check video file.");
		}
		numOfFrames = getFrameCount();
		flops = (int) Math.rint(numOfFrames/reader.getDuration().getSeconds());
		fileName = video.getName();
	}
	
	private void constractPlayer(File videoFile) {
		try {
			MediaLocator locator = new MediaLocator(videoFile.toURI().toURL());
			javax.media.protocol.DataSource source = Manager.createDataSource(locator);
			reader = Manager.createRealizedPlayer(source);
			// realize call will launch a chain of events,
			reader.addControllerListener(this);
			reader.prefetch();
			updateControls();
		} catch (Exception e) {
			System.out.println("Could not create VideoSource!");
			e.printStackTrace();
			if(reader !=null) {
				reader.close();
			}
		}
	}
	
	private Integer getFrameCount() {
		if (reader == null) {
			return null;
		}
		return framePositioningControl.mapTimeToFrame(reader.getDuration()); 
	}
	
	/**
	 * Video : avi, mpg, mov
	 * @param path
	 * @return
	 */
	public static boolean readableFormat(String path) {
		
		try {
			Manager.createProcessor(new MediaLocator(new File(path).toURI().toURL()));
		} catch (NoProcessorException | IOException e) {
			/*
			 * if NoProcessException occurred, can not read that file.
			 */
			return false;
		}

		String mimeType = URLConnection.guessContentTypeFromName(path);
		if (mimeType.endsWith("mpeg")) {
			if (Platform.getCurrentPlatform() == Platform.MAC) {
				System.out.println("Sorry, you have to use win/linux OS to decode MPEG...");
				return false;
			}
		}
		if (path.endsWith(".avi") || path.endsWith(".mpg") || path.endsWith(".mov")) {
			return true;
		}
		return false;
	}
	
	public BufferedImage getFrame(int index) {
		if (reader == null || index < 0 || index > numOfFrames) {
			return null;
		}
		framePositioningControl.seek(index);//set position
		javax.media.Buffer buffer = frameGrabbingControl.grabFrame();
		Image img = new BufferToImage((javax.media.format.VideoFormat) buffer.getFormat()).createImage(buffer);
		if (img != null) {
			BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = bi.createGraphics();
			g.drawImage(img, 0, 0, null);
			return bi;
		}
		return null;
	}
	
	/**
	 * flops : fps
	 * @return
	 */
	public Integer getFrameRate() {
		if(reader == null) {
			return null;
		}
		return flops;
	}
	
	public Double getDurationSeconds() {
		if(reader == null) {
			return null;
		}
		return reader.getDuration().getSeconds();
	}
	
	public Integer getNumberOfFrames() {
		return numOfFrames;
	}
	
	public Integer getWidth() {
		if(reader == null) {
			return null;
		}
		return getFrame(0).getWidth();
	}
	
	public Integer getHeight() {
		if(reader == null) {
			return null;
		}
		return getFrame(0).getHeight();
	}
	
	public ImagePlus convert2ImagePlus() {
		if(reader == null) {
			return null;
		}
		ImageStack stack = new ImageStack();
		for(int i=0;i<numOfFrames;i++) {
			BufferedImage bi = getFrame(i);
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
		ImagePlus imp = new ImagePlus(fileName, stack);
		if(getFrameRate() != null) {
			imp.getCalibration().fps = (double)getFrameRate();
		}
		return imp;
	}
	
	private void updateControls() {
		framePositioningControl = (FramePositioningControl) reader
				.getControl("javax.media.control.FramePositioningControl");
		if (framePositioningControl == null) {
			System.out.println("Error: FramePositioningControl!");
			return;
		}
		frameGrabbingControl = (FrameGrabbingControl) reader.getControl("javax.media.control.FrameGrabbingControl");
		if (frameGrabbingControl == null) {
			System.out.println("Error: FrameGrabbingControl!");
			return;
		}
	}
	
	public void close() {
		if(reader != null) {
			reader.close();
		}
	}

	@Override
	public void controllerUpdate(ControllerEvent e) {
		if (e instanceof RealizeCompleteEvent) {
			reader.prefetch();
		} else if (e instanceof PrefetchCompleteEvent) {
			updateControls();
		}
	}
}
