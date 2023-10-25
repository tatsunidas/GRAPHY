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
package com.vis.core.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.vis.core.log.Log;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageStatistics;

public class ImageUtils {
	
	final static int AUTO_THRESHOLD = 5000;
	
	//check file is general format image
		public static boolean isImageFile(String path) {
		    String mimeType = URLConnection.guessContentTypeFromName(path);
		    return mimeType != null && mimeType.startsWith("image");
		}
		
		public static boolean isVideoFile(String path) {
		    String mimeType = URLConnection.guessContentTypeFromName(path);
		    return mimeType != null && mimeType.startsWith("video");
		}
		
		public static boolean isDicom(File f) {
			return DicomUtilities.isDicomFile(f);
		}
		
		public static boolean isPDF(String path) {
			if(path == null || path.equals("")) {
				return false;
			}
			PDDocument doc = null;
			try {
				doc = PDDocument.load(new File(path));
			} catch (IOException e) {
				return false;
			}
			if(doc == null) {
				return false;
			}
			return true;
		}
		
		
		public static ImagePlus readFile(File f) {
			if(isDicom(f)){
				DicomImage dcmObj = DicomImage.newDicomImage(f.getAbsolutePath(), DICOMBackend.getCurrent());
				String tsUID = dcmObj.getTSUID().uid();
				return readDicomObject(dcmObj, tsUID);
			}else {
				//general image format or avi, pdf ??
				try {
					return new ImagePlus(f.getAbsolutePath());
				}catch(Exception err) {
					System.out.println(err);
					Log.logger.info(err.getMessage());
					return null;
				}
			}
		}
		
		/*
		 * TODO 20230906
		 */
		//single frame dicom
		public static ImagePlus readDicomObject(DicomImage dcm, String tsUID) {
			if(dcm == null || tsUID == null) {
				Log.logger.info("Please input non-null values...");
				return null;
			}
//			DicomPixelDataDecoder decoder = new DicomPixelDataDecoder(dcm, tsUID);
//			return decoder.decode();
			
			return null;
		}
		
//		public ArrayList<Attributes> readMultiFrameAsAttributes(Attributes ds, String tsUID) {
//			//return arraylist
//			ArrayList<Attributes> frames = new ArrayList<Attributes>();
//			int w = ds.getInt(Tag.Columns, -1);
//			int h= ds.getInt(Tag.Rows, -1);
//			int numOfFrames = ds.getInt(Tag.NumberOfFrames, -1);
//			if(w == -1 || h == -1 || numOfFrames == -1) {
//				return null;
//			}
//			
//			GMultiframeExtractor ext = new GMultiframeExtractor();
//			for(int i=0;i<numOfFrames;i++) {
//				Attributes f = ext.extract(ds, i);
//				Decompression de = new Decompression();
//				Attributes decompressed = de.decompress(tsUID, f);
//				frames.add(decompressed);
//			}
//			return frames;
//		}
		
		/**
		 * 
		 * TODO 202308906
		 * 
		 * get headers(without pixels) to show multi frames.
		 * @param multiFrameDcm
		 * @param tsUID
		 * @return
		 */
		public static ArrayList<DicomObject> readMultiFrameDicomHeaders(DicomObject multiFrameDcm) {
			// return arraylist
//			ArrayList<DicomObject> frames = new ArrayList<DicomObject>();
//			int w = multiFrameDcm.getInt(Tag.Columns, -1);
//			int h = multiFrameDcm.getInt(Tag.Rows, -1);
//			int numOfFrames = multiFrameDcm.getInt(Tag.NumberOfFrames, -1);
//			if (w == -1 || h == -1 || numOfFrames == -1) {
//				return null;
//			}
//			if (backend.equals("dcm4che")) {
//				GMultiframeExtractor ext = new GMultiframeExtractor();
//				DicomObjectDcm4che multi_dcm = (DicomObjectDcm4che) multiFrameDcm.getCore();
//				for (int i = 0; i < numOfFrames; i++) {
//					Attributes inst = ext.extract(multi_dcm.getAttributes(), i);
//					inst.remove(TagDict.At("PixelData"));
//					inst.remove(TagDict.At("FloatPixelData"));
//					inst.remove(TagDict.At("DoubleFloatPixelData"));
//					DicomObject frame = new DicomObject();
//					frame.setCore(new DicomObjectDcm4che(inst, multi_dcm.getFileMetaInformation()));
//					frames.add(frame);
//				}
//				return frames;
//			} else {
//				return null;
//			}
			return null;
		}
		
		public static Image resize(Image srcImg, int w, int h) {
			BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = resizedImg.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(srcImg, 0, 0, w, h, null);
			g2.dispose();
			return resizedImg;
		}
		
		public static BufferedImage merge(ImagePlus imp1, ImagePlus imp2) {
			int w1 = imp1.getWidth();
			int w2 = imp2.getWidth();
			int h1 = imp1.getHeight();
			int h2 = imp2.getHeight();
			if(w1 != w2 || h1 != h2) {
				imp2 = imp2.resize(w1, h1, "none");
			}
			ColorProcessor cp = new ColorProcessor(w1+w2, h1);
			for(int j=0; j<h1;j++) {
				for(int i=0; i<w1+w2;i++) {
					if(i < w1) {
						cp.set(i, j, imp1.getProcessor().get(i, j));
					}else {
						cp.set(i, j, imp2.getProcessor().get(i-w1, j));
					}
				}
			}
			ImagePlus merge = new ImagePlus("merge",cp);
			// autoContrast(merge);
			return merge.getBufferedImage();
		}
		
		public static void autoContrast(ImagePlus imp) {
			if(imp == null) {
				return;
			}
			if (imp.getType() == ImagePlus.COLOR_RGB)
				imp.getProcessor().reset();
			ImageStatistics stats = imp.getRawStatistics();
			int limit = stats.pixelCount/10;
			int[] histogram = stats.histogram;
			int autoThreshold = imp.getProcessor().getAutoThreshold();
			if (autoThreshold<10)
				autoThreshold = AUTO_THRESHOLD;
			else
				autoThreshold /= 2;
			int threshold = stats.pixelCount/autoThreshold;
			int i = -1;
			boolean found = false;
			int count;
			do {
				i++;
				count = histogram[i];
				if (count>limit) count = 0;
				found = count> threshold;
			} while (!found && i<255);
			int hmin = i;
			i = 256;
			do {
				i--;
				count = histogram[i];
				if (count>limit) count = 0;
				found = count > threshold;
			} while (!found && i>0);
			int hmax = i;
			ij.gui.Roi roi = imp.getRoi();
			if (hmax>=hmin) {
				if (imp.getType() == ImagePlus.COLOR_RGB) imp.deleteRoi();
				double min = stats.histMin+hmin*stats.binSize;
				double max = stats.histMin+hmax*stats.binSize;
				if (min==max)	{min=stats.min; max=stats.max;}
				imp.getProcessor().setMinAndMax(min, max);
				if ((imp.getType() == ImagePlus.COLOR_RGB) && roi!=null) imp.setRoi(roi);
			} else {
				imp.getProcessor().resetMinAndMax();
				return;
			}
		}
		
		public static BufferedImage toBufferedImage(Image img) {
			if (img instanceof BufferedImage) {
				return (BufferedImage) img;
			}

			// Create a buffered image with transparency
			BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null),
					BufferedImage.TYPE_INT_ARGB);

			// Draw the image on to the buffered image
			Graphics2D bGr = bimage.createGraphics();
			bGr.drawImage(img, 0, 0, null);
			bGr.dispose();

			// Return the buffered image
			return bimage;
		}
}
