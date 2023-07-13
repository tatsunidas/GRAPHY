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

import com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageWriter;
import com.sun.media.imageio.plugins.jpeg2000.J2KImageWriteParam;

public class J2KCompression {
	
	private boolean lossless = false;
	
	//lossy : set to 0.25-1.0
	//lossless : must set Double.MAX_VALUE
	private double encodingRate = 0.55;
	
	/**
	 * In contrast to JPEG, JPEG2000 ImageWriter and Param class are public.
	 * Use these classes directly.
	 * @param writer
	 * @return
	 */
	private J2KImageWriteParam prepareParam(J2KImageWriter writer) {
		J2KImageWriteParam param = (J2KImageWriteParam) writer.getDefaultWriteParam();
		if(lossless) {
			param.setEncodingRate(Double.MAX_VALUE);//also set setLossless(true).
		}else {
			param.setEncodingRate(encodingRate);
		}
		// Assume RGB and assume always want to transform; other than YBR_RCT is illegal in DICOM anyway; JJ2000 will fail if set to false anyway (000981)
		param.setComponentTransformation(true);
		return param;
	}
	
	public byte[] transcode(BufferedImage rawImage) {
		
		byte[] compressed = null;
		try (
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
				)
		{
			
			Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG2000");
			if (writers != null && writers.hasNext()) {
				J2KImageWriter writer = (J2KImageWriter) writers.next();
				J2KImageWriteParam param = prepareParam(writer);
				writer.setOutput(ios);
				IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(rawImage.getColorModel(), rawImage.getSampleModel()), param);
				writer.write(metadata, new IIOImage(rawImage, null/* no thumbnails */, metadata), param);
				compressed = baos.toByteArray();
				writer.dispose();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return compressed;
	}
	
	public void setLossless(boolean lossless) {
		this.lossless = lossless;
	}
	
	/**
	 * min value is 0.25
	 * max value is 1.0
	 * @param rate
	 */
	public void setEncodingRate(double rate) {
		if(rate > 1.0) {
			rate = 1.0;
		}else if(rate < 0.25) {
			rate = 0.25;
		}
		this.encodingRate = rate;
	}
	
}
