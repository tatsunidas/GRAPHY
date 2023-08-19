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

import ij.*;
import ij.process.*;
import java.awt.image.*;
import java.awt.*;
import java.io.*;
import java.util.Iterator;
import java.util.logging.Level;

import javax.imageio.*;
import javax.imageio.stream.*;

//import com.sun.media.imageioimpl.plugins.jpeg.*;

import com.vis.core.log.Log;
import com.vis.imageio.Codec;

//import org.dcm4che3.imageio.codec.ImageWriterFactory.ImageWriterParam;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.imageio.metadata.IIOMetadata;

/**
 * 
 * This class is junk codes for JPEG Compression how to.
 * 
 * This differs from the core JPEG ImageWriteParam in that:
 *
 * <ul>
 * <li>compression types are: "JPEG" (standard), "JPEG-LOSSLESS"
 * (lossless JPEG from 10918-1/ITU-T81), "JPEG-LS" (ISO 14495-1 lossless).</li>
 * <li>compression modes are: MODE_DEFAULT and MODE_EXPLICIT and the
 * other modes (MODE_DISABLED and MODE_COPY_FROM_METADATA) cause
 * an UnsupportedOperationException.</li>
 * <li>isCompressionLossless() will return true if type is NOT "JPEG".</li>
 * </ul>
 * 
 * Native codec libs in JAI-ImageIO only compatible with 32-bit JDK/JRE.
 * With this reason, JPEG-LOSSLESS, JPEG-LS, JPEG Extended 12-bit are handled by opencv.
 * 
 * @author tatsunidas
 *
 */

@Deprecated
public class JPEGCompression {
	
//	public static final String LOSSY_COMPRESSION_TYPE = "JPEG";
	
	// these compression types are not compatible on 64 bit JDK/JRE
//	public static final String LOSSLESS_COMPRESSION_TYPE = "JPEG-LOSSLESS";
//	public static final String LS_COMPRESSION_TYPE = "JPEG-LS";
	
	// Jpeg Metadata Format Name
	private static final String FORMAT_NAME = "javax_imageio_jpeg_image_1.0";
	
	private boolean disableChromaSubsampling;
	private boolean chromaSubsamplingSet;
	private float QUALITY = 0.75f;
	
	private final String destTSUID;
	
	public JPEGCompression(final String destTSUID) {
		this.destTSUID = destTSUID;
	}
	
	@SuppressWarnings("unused")
	private ImageWriter loadWriter() {
		if (destTSUID.equals(Codec.JPEGBase.uidString())) {
			Iterator<ImageWriter> w = ImageIO.getImageWritersByFormatName("jpeg");
			return w.hasNext() ? w.next():null;			
		} else if (destTSUID.equals(Codec.JPEGExtended12Bit.uidString())) {
			org.dcm4che3.imageio.codec.ImageWriterFactory.ImageWriterParam param = org.dcm4che3.imageio.codec.ImageWriterFactory.getImageWriterParam(destTSUID);
			ImageWriter w = org.dcm4che3.imageio.codec.ImageWriterFactory.getImageWriterFromServiceLoader(param);
			return w;
		} else if (destTSUID.equals(Codec.JPEG2000.uidString()) || destTSUID.equals(Codec.JPEG2000LOSSLESS.uidString())) {
			Iterator<ImageWriter> w = ImageIO.getImageWritersByFormatName("jpeg2000");
			return w.hasNext() ? w.next():null;
		} else if (destTSUID.equals(Codec.JPEGLOSSLESS.uidString())) {
			org.dcm4che3.imageio.codec.ImageWriterFactory.ImageWriterParam param = org.dcm4che3.imageio.codec.ImageWriterFactory.getImageWriterParam(destTSUID);
			ImageWriter w = org.dcm4che3.imageio.codec.ImageWriterFactory.getImageWriterFromServiceLoader(param);
			return w;
		} else if (destTSUID.equals(Codec.JPEG_LS.uidString())) {
			org.dcm4che3.imageio.codec.ImageWriterFactory.ImageWriterParam param = org.dcm4che3.imageio.codec.ImageWriterFactory.getImageWriterParam(destTSUID);
			ImageWriter w = org.dcm4che3.imageio.codec.ImageWriterFactory.getImageWriterFromServiceLoader(param);
			return w;
		} else if (destTSUID.equals(Codec.JPEG_NearLS.uidString())) {
			org.dcm4che3.imageio.codec.ImageWriterFactory.ImageWriterParam param = org.dcm4che3.imageio.codec.ImageWriterFactory.getImageWriterParam(destTSUID);
			ImageWriter w = org.dcm4che3.imageio.codec.ImageWriterFactory.getImageWriterFromServiceLoader(param);
			return w;
		}
		return null;
	}
	
	/**
	 * JPEGImageWriteParam is inner-class of ClibJPEGImageWriter.
	 * Here, use it as ImageWriterParam through interfaces. 
	 * this ImageWriteParam is means CLibJPEGImageWriteParam.
	 */
	private ImageWriteParam prepareParam(ImageWriter writer) {
		
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		/*
		 * Example, 
		 * you can set compressionRatio 
		 */
		
		if(destTSUID.equals(Codec.JPEGBase.uidString())) {
			param.setCompressionQuality(getQuality());
		}else if(destTSUID.equals(Codec.JPEGExtended12Bit.uidString())) {
			
		}else if(destTSUID.equals(Codec.JPEG2000.uidString())) {
			
		}else if(destTSUID.equals(Codec.JPEG2000LOSSLESS.uidString())) {
			
		}else if(destTSUID.equals(Codec.JPEGLOSSLESS.uidString())) {
			
		}else if(destTSUID.equals(Codec.JPEG_LS.uidString())) {
			
		}else if(destTSUID.equals(Codec.JPEG_NearLS.uidString())) {
			
		}else if(destTSUID.equals(Codec.RLE.uidString())) {
			
		}else {
			return null;
		}
		
//		
//		
//		if(COMPRESSION_TYPE.equals(LOSSY_COMPRESSION_TYPE)) {
//			param.setCompressionType(LOSSY_COMPRESSION_TYPE);
//		}else if(COMPRESSION_TYPE.equals(LS_COMPRESSION_TYPE)) {
//			param.setCompressionType(LS_COMPRESSION_TYPE);
//		}else {
//			param.setCompressionType(LOSSLESS_COMPRESSION_TYPE);
//		}
//		
		if(getQuality() >= 1.0f) {
			param.setSourceSubsampling(1, 1, 0, 0);
		}
		return param;
	}

	public byte[] compress(BufferedImage bi) {
		byte[] compressed = null;
		
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageOutputStream ios = ImageIO.createImageOutputStream(baos);) {
			
			ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
			ImageWriteParam param = prepareParam(writer);
		
			writer.setOutput(ios);
			
			IIOMetadata metadata = null;
			IIOImage iioImage = null;
			
			boolean disableSubsampling = getQuality() >= 0.9;
			if (chromaSubsamplingSet) {
				disableSubsampling = disableChromaSubsampling;
			}
			if (!disableSubsampling) {
				// Use chroma subsampling YUV420
				iioImage = new IIOImage(bi, null, null);
			}else {
				// Disable JPEG chroma subsampling
				// http://svn.apache.org/repos/asf/shindig/trunk/java/gadgets/src/main/java/org/apache/shindig/gadgets/rewrite/image/BaseOptimizer.java
				// http://svn.apache.org/repos/asf/shindig/trunk/java/gadgets/src/main/java/org/apache/shindig/gadgets/rewrite/image/JpegImageUtils.java
				// Peter Haub, Okt. 2019
				metadata = writer.getDefaultImageMetadata(
						new ImageTypeSpecifier(bi.getColorModel(), bi.getSampleModel()), param);
				Node rootNode = metadata != null ? metadata.getAsTree(FORMAT_NAME) : null;
				boolean metadataUpdated = false;
				// The top level root node has two children, out of which the second one will
				// contain all the information related to image markers.
				if (rootNode != null && rootNode.getLastChild() != null) {
					Node markerNode = rootNode.getLastChild();
					NodeList markers = markerNode.getChildNodes();
					// Search for 'SOF' marker where subsampling information is stored.
					for (int i = 0; i < markers.getLength(); i++) {
						Node node = markers.item(i);
						// 'SOF' marker can have
						// 1 child node if the color representation is greyscale,
						// 3 child nodes if the color representation is YCbCr, and
						// 4 child nodes if the color representation is YCMK.
						// This subsampling applies only to YCbCr.
						if (node.getNodeName().equalsIgnoreCase("sof") && node.hasChildNodes()
								&& node.getChildNodes().getLength() == 3) {
							// In 'SOF' marker, first child corresponds to the luminance channel, and
							// setting
							// the HsamplingFactor and VsamplingFactor to 1, will imply 4:4:4 chroma
							// subsampling.
							NamedNodeMap attrMap = node.getFirstChild().getAttributes();
							// SamplingModes: UNKNOWN(-2), DEFAULT(-1), YUV444(17), YUV422(33), YUV420(34),
							// YUV411(65)
							int samplingmode = 17; // YUV444
							attrMap.getNamedItem("HsamplingFactor").setNodeValue((samplingmode & 0xf) + "");
							attrMap.getNamedItem("VsamplingFactor").setNodeValue(((samplingmode >> 4) & 0xf) + "");
							metadataUpdated = true;
							break;
						}
					}
				}
				// Read the updated metadata from the metadata node tree.
				if (metadataUpdated) {
					metadata.setFromTree(FORMAT_NAME, rootNode);
				}
				iioImage = new IIOImage(bi, null/* no thumbnails */, metadata);
			} // end of code adaption (Disable JPEG chroma subsampling)
			writer.write(metadata, iioImage, param);
			compressed = baos.toByteArray();
			writer.dispose();
		} catch (IOException e) {
			Log.logger.log(Level.SEVERE, e.getMessage());
		}
		return compressed;
	}

	/**
	 * transcode with 0.75 compression rate
	 * 
	 * @param rawImage
	 * @return
	 */
	public byte[] compressDefault(BufferedImage rawImage) {
		
		byte[] imageInByte = null;
		try (
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				)
		{
			ImageIO.write(rawImage, "jpg", baos);
			imageInByte = baos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return imageInByte;
	}

	String saveAsJpeg(ImagePlus imp, String path, int quality) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		int biType = BufferedImage.TYPE_INT_RGB;
		boolean overlay = imp.getOverlay() != null && !imp.getHideOverlay();
		ImageProcessor ip = imp.getProcessor();
		if (ip.isDefaultLut() && !imp.isComposite() && !overlay && ip.getMinThreshold() == ImageProcessor.NO_THRESHOLD)
			biType = BufferedImage.TYPE_BYTE_GRAY;
		BufferedImage bi = new BufferedImage(width, height, biType);
		String error = null;
		try {
			Graphics g = bi.createGraphics();
			Image img = imp.getImage();
			if (overlay && (imp.getOverlay() != null))
				img = imp.flatten().getImage();
			g.drawImage(img, 0, 0, null);
			g.dispose();
			Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpeg");
			ImageWriter writer = (ImageWriter) iter.next();
			File f = new File(path);
			String originalPath = null;
			boolean replacing = f.exists();
			if (replacing) {
				originalPath = path;
				path += ".temp";
				f = new File(path);
			}
			ImageOutputStream ios = ImageIO.createImageOutputStream(f);
			writer.setOutput(ios);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(quality / 100f);
			if (quality == 100)
				param.setSourceSubsampling(1, 1, 0, 0);
			IIOImage iioImage = null;
			boolean disableSubsampling = quality >= 90;
			if (chromaSubsamplingSet)
				disableSubsampling = disableChromaSubsampling;
			if (!disableSubsampling) // Use chroma subsampling YUV420
				iioImage = new IIOImage(bi, null, null);
			else {
				// Disable JPEG chroma subsampling
				// http://svn.apache.org/repos/asf/shindig/trunk/java/gadgets/src/main/java/org/apache/shindig/gadgets/rewrite/image/BaseOptimizer.java
				// http://svn.apache.org/repos/asf/shindig/trunk/java/gadgets/src/main/java/org/apache/shindig/gadgets/rewrite/image/JpegImageUtils.java
				// Peter Haub, Okt. 2019
				IIOMetadata metadata = writer.getDefaultImageMetadata(
						new ImageTypeSpecifier(bi.getColorModel(), bi.getSampleModel()), param);
				Node rootNode = metadata != null ? metadata.getAsTree(FORMAT_NAME) : null;
				boolean metadataUpdated = false;
				// The top level root node has two children, out of which the second one will
				// contain all the information related to image markers.
				if (rootNode != null && rootNode.getLastChild() != null) {
					Node markerNode = rootNode.getLastChild();
					NodeList markers = markerNode.getChildNodes();
					// Search for 'SOF' marker where subsampling information is stored.
					for (int i = 0; i < markers.getLength(); i++) {
						Node node = markers.item(i);
						// 'SOF' marker can have
						// 1 child node if the color representation is greyscale,
						// 3 child nodes if the color representation is YCbCr, and
						// 4 child nodes if the color representation is YCMK.
						// This subsampling applies only to YCbCr.
						if (node.getNodeName().equalsIgnoreCase("sof") && node.hasChildNodes()
								&& node.getChildNodes().getLength() == 3) {
							// In 'SOF' marker, first child corresponds to the luminance channel, and
							// setting
							// the HsamplingFactor and VsamplingFactor to 1, will imply 4:4:4 chroma
							// subsampling.
							NamedNodeMap attrMap = node.getFirstChild().getAttributes();
							// SamplingModes: UNKNOWN(-2), DEFAULT(-1), YUV444(17), YUV422(33), YUV420(34),
							// YUV411(65)
							int samplingmode = 17; // YUV444
							attrMap.getNamedItem("HsamplingFactor").setNodeValue((samplingmode & 0xf) + "");
							attrMap.getNamedItem("VsamplingFactor").setNodeValue(((samplingmode >> 4) & 0xf) + "");
							metadataUpdated = true;
							break;
						}
					}
				}
				// Read the updated metadata from the metadata node tree.
				if (metadataUpdated)
					metadata.setFromTree(FORMAT_NAME, rootNode);
				iioImage = new IIOImage(bi, null, metadata);
			} // end of code adaption (Disable JPEG chroma subsampling)
			writer.write(null, iioImage, param);
			ios.close();
			writer.dispose();
			if (replacing) {
				File f2 = new File(originalPath);
				boolean ok = f2.delete();
				if (ok)
					f.renameTo(f2);
			}
		} catch (Exception e) {
			error = "" + e;
			if (error.contains("Output has not been set!"))
				error = "Incorrect file path: \"" + path + "\"";
			Log.logger.log(Level.SEVERE, "JPEG Writer", error);
		}
		return error;
	}

	/**
	 * 
	 * @param jpegQuality 0.05 ~ 1.0
	 */
	public void setQuality(float jpegQuality) {
		if(jpegQuality < 0.05f){
			jpegQuality = 0.05f;
		}
		this.QUALITY = jpegQuality;
	}

	public float getQuality() {
		return QUALITY;
	}

	/**
	 * Enhance quality of JPEGs by disabing chroma subsampling. By default, enhanced
	 * quality is automatically used when the Quality setting is 90 or greater.
	 */
	public void enhanceQuality(boolean enhance) {
		disableChromaSubsampling = enhance;
		chromaSubsamplingSet = true;
	}

	public void disableChromaSubsampling(boolean disable) {
		enhanceQuality(disable);
	}
}
