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
package com.vis.core.view.mpr;

import java.awt.image.ColorModel;

import org.joml.Vector3d;

import com.vis.core.view.D2.ui.orientation.ImageOrientation;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.dicom.image.GDicomTools;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * The horizontal and vertical planes are cut from the input axial volume. The origin
 * of the upper left corner of the plane to be cut is reversed depending on the
 * patient's position (head first, feet first). In the case of head first, the
 * origin is the upper left corner of the slice with the largest Image Position
 * Patient Z value. In the case of feet first, the origin is the upper left
 * corner of the slice with the smallest Image Position Patient Z value.
 * 
 * TODO
 * - COR head first + prone + P->A slicing to Axial
 * - COR head first + prone + A->P slicing to Axial -> Clear
 * - COR feet first + prone + P->A slicing to Axial
 * - COR feet first + prone + A->P slicing to Axial
 * - SAG head first + L->R slicing to Axial -> Clear
 * - SAG head first + R->L slicing to Axial
 * - SAG feet first + L->R slicing to Axial
 * - SAG feet first + R->L slicing to Axial
 *  
 * @author tatsunidas
 *
 */
@SuppressWarnings("unused")
public class OrthogonalSlice {
	
	//debug
	@SuppressWarnings("unused")
	public static void main(String[] args) {
		
		OrthogonalSlice slicer = new OrthogonalSlice();
		
//		String dir = "/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151";
//		ImagePlus xy = FolderOpener.open(dir);
		
//		xy.setPosition(xy.getNSlices()/2);
//		ImagePlus xz = slicer.cutHorizontally(xy, xy.getHeight()/2-1, 1);
//		xz.setTitle("COR");
//		ImagePlus yz = slicer.cutVirtically(xy, xy.getWidth()/2-1, 1);
//		yz.setTitle("SAG");
//		xz.show();
//		yz.show();
		
//		String corDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/T1COR";
//		ImagePlus xz = FolderOpener.open(corDir);
//		ImagePlus xy = slicer.coronalToAxial(xz);
//		xy.show();
		
		String sagDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";
		ImagePlus yz = FolderOpener.open(sagDir);
		ImagePlus xy = slicer.sagittalToAxial(yz);
		xy.show();
	}
	
	/**
	 * @param src: axial
	 * @param y_cutPoint
	 * @return
	 */
	public ImagePlus cutHorizontally(ImagePlus src, int y_cutPoint) {
		if (PlanarSupport.planarOf(src) != CutSurface.AXIAL) {
			throw new IllegalArgumentException("Need axial src volume");
		}
		ImageStack is = getStack(src);
		int width = is.getWidth();
		int size = is.getSize();
		ImageProcessor ip = is.getProcessor(1);
		boolean rgb = (ip instanceof ColorProcessor);
		ColorModel cm = rgb ? null : src.getProcessor().getColorModel();
		double min = src.getDisplayRangeMin();
		double max = src.getDisplayRangeMax();
		Calibration cal = src.getCalibration().copy();
		String xunit = cal.getXUnit();
		String yunit = cal.getYUnit();
		String zunit = cal.getZUnit();
		double src_pz = GDicomTools.getVoxelDepth(src);
		double src_py = cal.pixelHeight;
		double src_px = cal.pixelWidth;
		double az = src_pz / src_px;
		/*
		 * x and y are zero-based.
		 */
		int y = y_cutPoint;
		if (y_cutPoint < 0) {
			y = 0;
		} else if (y_cutPoint > src.getHeight()) {
			y = src.getHeight() - 1;
		}
		Object newpix = null;
		ImageProcessor xz_ip = null;
		ImagePlus xz_image = new ImagePlus();
		boolean isHeadFirst = PlanarSupport.isHeadFirst(src);
		boolean isSlicingToUpperZ = isSlicingToUpperZSide(src);
		if (ip instanceof ShortProcessor) {
			newpix = new short[width * size];
		} else if (ip instanceof ByteProcessor) {
			newpix = new byte[width * size];
		} else if (ip instanceof FloatProcessor) {
			newpix = new float[width * size];
		} else if (ip instanceof ColorProcessor) {
			newpix = new int[width * size];
		}
		// copy lines to newPixel
		for (int i = 0; i < size; i++) {
			Object pixels = is.getPixels(i + 1);
			/* scroll to upper and head */
			if (isHeadFirst && isSlicingToUpperZ) {
				/* from last to start */
				System.arraycopy(pixels, width * y, newpix, width * (size - i - 1), width);
			} else if (isHeadFirst && !isSlicingToUpperZ) {
				/* from start to last */
				System.arraycopy(pixels, width * y, newpix, width * i, width);
			} else if (!isHeadFirst && isSlicingToUpperZ) {
				/* from start to last */
				System.arraycopy(pixels, width * y, newpix, width * i, width);
			} else if (!isHeadFirst && !isSlicingToUpperZ) {
				/* from last to start */
				System.arraycopy(pixels, width * y, newpix, width * (size - i - 1), width);
			}
		}
		if (ip instanceof ShortProcessor) {
			xz_ip = new ShortProcessor(width, size, (short[]) newpix, ip.getCurrentColorModel());
		} else if (ip instanceof ByteProcessor) {
			xz_ip = new ByteProcessor(width, size, (byte[]) newpix, ip.getCurrentColorModel());
		} else if (ip instanceof FloatProcessor) {
			xz_ip = new FloatProcessor(width, size, (float[]) newpix, ip.getCurrentColorModel());
		} else if (ip instanceof ColorProcessor) {
			xz_ip = new ColorProcessor(width, size, (int[]) newpix);
		}
		
		if (cm != null && xz_ip != null && (ip instanceof ColorProcessor)) {
			xz_ip.setColorModel(cm);
		}

		int width2 = xz_ip.getWidth();
		int height2 = (int) Math.ceil(xz_ip.getHeight() * az);
		if (height2 < 1) {
			throw new IllegalArgumentException("Can not create XZ plane...");
		}
		if (width2 != xz_ip.getWidth() || height2 != xz_ip.getHeight()) {
			xz_ip.setInterpolationMethod(ImageProcessor.NONE);
			xz_ip = xz_ip.resize(width2, height2);
		}
		if (!rgb) {
			xz_ip.setMinAndMax(min, max);
		}
		xz_image.setProcessor(""+y, xz_ip);
		
		PlanarSupport psup = new PlanarSupport();

		double col = 0;// x direction
		double row = y;// y direction
		int pos_z = getOriginSlicePosition(size,isSlicingToUpperZ,isHeadFirst);
		Vector3d ipp_vec = psup.getNewImagePositionPatient2D(src, col, row, pos_z);
		double[] iop = null;
		double[] axi_iop = GDicomTools.getImageOrientationPatient(src, 1);
		double[] rowX = new double[]{axi_iop[0],axi_iop[1],axi_iop[2]};
		double[] colY = new double[]{axi_iop[3],axi_iop[4],axi_iop[5]};
		double[] zVec = PlanarSupport.calculateNormal(rowX, colY);
		if(isHeadFirst) {
			if(zVec[2] > 0.0) {
				zVec[0] *= -1;
				zVec[1] *= -1;
				zVec[2] *= -1;
			}
			iop = new double[] {rowX[0],rowX[1],rowX[2],zVec[0], zVec[1],zVec[2]};
		}else {
			if(zVec[2] < 0.0) {
				zVec[0] *= -1;
				zVec[1] *= -1;
				zVec[2] *= -1;
			}
			iop = new double[] {rowX[0],rowX[1],rowX[2],zVec[0], zVec[1],zVec[2]};
		}
		if (ipp_vec != null && iop !=null) {
			double[] ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
//			System.out.println("CORONAL ImagePosition: " + Arrays.toString(ipp));
			GDicomTools.setImagePositionPatient(xz_image, 1, ipp);
			GDicomTools.setImageOrientationPatient(xz_image, 1, iop);
		}
		Calibration cal_xz = new Calibration();
		cal_xz.setXUnit(xunit);
		cal_xz.setYUnit(zunit);
		cal_xz.setZUnit(yunit);
		cal_xz.pixelWidth = src_px;
		cal_xz.pixelHeight = src_pz / az;
		cal_xz.pixelDepth = src_py;
		xz_image.setCalibration(cal_xz);
		return xz_image;
	}
	
	/**
	 * 
	 * Create a sagittal section for the input stack.
	 * 
	 * @param src : axial
	 * @param x_cutPoint: 0 to w-1
	 * @return
	 */
	public ImagePlus cutVirtically(ImagePlus src, int x_cutPoint) {
		
		if(PlanarSupport.planarOf(src) != CutSurface.AXIAL) {
        	throw new IllegalArgumentException("Need axial src volume");
        }
		ImageStack is = getStack(src);
		ImageProcessor ip = is.getProcessor(1);
		int width = is.getWidth();
		int height = is.getHeight();
		int size = is.getSize();
		boolean rgb = ip instanceof ColorProcessor;
		ColorModel cm = rgb ? null : src.getProcessor().getColorModel();
		double min = src.getDisplayRangeMin();
		double max = src.getDisplayRangeMax();
		Calibration cal = src.getCalibration().copy();
		String xunit = cal.getXUnit();
		String yunit = cal.getYUnit();
		String zunit = cal.getZUnit();
		double src_pz = GDicomTools.getVoxelDepth(src);
		double src_py = cal.pixelHeight;
		double src_px = cal.pixelWidth;
		double az = src_pz / src_px;// keep z/x relationship

		ImageProcessor yz_ip = null;
		ImagePlus yz_image = new ImagePlus();
		/*
		 * x and y is zero-based.
		 */
		int x = x_cutPoint;
		if (x < 0) {
			x = 0;
		} else if (x >= width) {
			x = width - 1;
		}
		Object newpix = null;
		
		boolean isHeadFirst = PlanarSupport.isHeadFirst(src);
		boolean isSlicingToUpperZ = isSlicingToUpperZSide(src);
		if (ip instanceof ShortProcessor) {
			newpix = new short[height * size];
		} else if (ip instanceof ByteProcessor) {
			newpix = new byte[height * size];
		} else if (ip instanceof FloatProcessor) {
			newpix = new float[height * size];
		} else if (ip instanceof ColorProcessor) {
			newpix = new int[height * size];
		}
		// copy lines to newPixel
		for (int i = 0; i < size; i++) {
			Object pixels = is.getPixels(i + 1);
			/* scroll to upper and head */
			if (isHeadFirst && isSlicingToUpperZ) {
				/* from last to start */
				for (int j = 0; j < height; j++) {
					System.arraycopy(pixels, x + j * width, newpix, (size - i - 1) * height + j, 1);
				}
			} else if (isHeadFirst && !isSlicingToUpperZ) {
				/* from start to last */
				for (int j = 0; j < height; j++) {
					System.arraycopy(pixels, x + j * width, newpix, i * height + j, 1);
				}
			} else if (!isHeadFirst && isSlicingToUpperZ) {
				/* from start to last */
				for (int j = 0; j < height; j++) {
					System.arraycopy(pixels, x + j * width, newpix, i * height + j, 1);
				}
			} else if (!isHeadFirst && !isSlicingToUpperZ) {
				/* from last to start */
				for (int j = 0; j < height; j++) {
					System.arraycopy(pixels, x + j * width, newpix, (size - i - 1) * height + j, 1);
				}
			}
		}
		if (ip instanceof ShortProcessor) {
			yz_ip = new ShortProcessor(height, size, (short[]) newpix, ip.getCurrentColorModel());
		} else if (ip instanceof ByteProcessor) {
			yz_ip = new ByteProcessor(height, size,(byte[]) newpix, ip.getCurrentColorModel());
		} else if (ip instanceof FloatProcessor) {
			yz_ip = new FloatProcessor(height, size,(float[]) newpix, ip.getCurrentColorModel());
		} else if (ip instanceof ColorProcessor) {
			yz_ip = new ColorProcessor(height, size,(int[]) newpix);
		}

		if (cm != null && yz_ip != null && yz_ip.getBitDepth() != 24) {
			yz_ip.setColorModel(cm);
		}

		int width2 = yz_ip.getWidth();
		int height2 = (int) Math.ceil(yz_ip.getHeight() * az);
		if (width2 != yz_ip.getWidth() || height2 != yz_ip.getHeight()) {
			yz_ip.setInterpolationMethod(ImageProcessor.NONE);
			yz_ip = yz_ip.resize(width2, height2);
		} 
		if (!rgb)
			yz_ip.setMinAndMax(min, max);
		yz_image.setProcessor("", yz_ip);
		// to YZ
		PlanarSupport psup = new PlanarSupport();
		double col = x;// x direction
		double row = 0;// y direction
		int pos_z = getOriginSlicePosition(size,isSlicingToUpperZ,isHeadFirst);
		Vector3d ipp_vec = psup.getNewImagePositionPatient2D(src, col, row, pos_z);
		double[] iop = null;
		double[] axi_iop = GDicomTools.getImageOrientationPatient(src, 1);
		double[] rowX = new double[]{axi_iop[0],axi_iop[1],axi_iop[2]};
		double[] colY = new double[]{axi_iop[3],axi_iop[4],axi_iop[5]};
		double[] zVec = PlanarSupport.calculateNormal(rowX, colY);
		if(isHeadFirst) {
			if(zVec[2] > 0.0) {
				zVec[0] *= -1;
				zVec[1] *= -1;
				zVec[2] *= -1;
			}
			iop = new double[] {colY[0],colY[1],colY[2],zVec[0], zVec[1],zVec[2]};
		}else {
			if(zVec[2] < 0.0) {
				zVec[0] *= -1;
				zVec[1] *= -1;
				zVec[2] *= -1;
			}
			iop = new double[] {colY[0],colY[1],colY[2],zVec[0], zVec[1],zVec[2]};
		}
//		if(isHeadFirst) {
//			double[] cor_iop = PlanarSupport.rotateImageOrientationPatient(axi_iop, -90, 0, 0);
//			iop = new double[] {row_y_vec[0],row_y_vec[1],row_y_vec[2],cor_iop[3],cor_iop[4],cor_iop[5]};
//			//System.out.println(java.util.Arrays.toString(iop));
//		}else {
//			double[] cor_iop = PlanarSupport.rotateImageOrientationPatient(axi_iop, 90, 0, 0);
//			iop = new double[] {row_y_vec[0],row_y_vec[1],row_y_vec[2],cor_iop[3],cor_iop[4],cor_iop[5]};
//		}
		if (ipp_vec != null && iop !=null) {
			double[] ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
			GDicomTools.setImagePositionPatient(yz_image, 1, ipp);
			GDicomTools.setImageOrientationPatient(yz_image, 1, iop);
		}
		Calibration cal_yz = new Calibration();
		cal_yz.setXUnit(yunit);
		cal_yz.setYUnit(zunit);
		cal_yz.setZUnit(xunit);
		cal_yz.pixelWidth = src_py;
		cal_yz.pixelHeight = src_pz / az;
		cal_yz.pixelDepth = src_px;
		yz_image.setCalibration(cal_yz);
		return yz_image;
	}
	
	public ImagePlus coronalToAxial(ImagePlus srcStack) {
		if(ImageOrientation.getCutSurface(srcStack) != CutSurface.CORONAL) {
        	throw new IllegalArgumentException("Need COR stack volume");
        }
		ImageStack is = getStack(srcStack);
		ImageProcessor ip = is.getProcessor(1);
		int width = is.getWidth();
		int height = is.getHeight();
		int size = is.getSize();
		boolean rgb = ip instanceof ColorProcessor;
		ColorModel cm = rgb ? null : srcStack.getProcessor().getColorModel();
		double min = srcStack.getDisplayRangeMin();
		double max = srcStack.getDisplayRangeMax();
		Calibration cal = srcStack.getCalibration().copy();
		String xunit = cal.getXUnit();
		String yunit = cal.getYUnit();
		String zunit = cal.getZUnit();
		double src_pz = GDicomTools.getVoxelDepth(srcStack);
		double src_py = cal.pixelHeight;
		double src_px = cal.pixelWidth;
		double az = src_pz / src_px;// keep z/x relationship
		int newH = (int) Math.ceil(size * az);
		if (newH < 1) {
			throw new IllegalArgumentException("Can not create XY plane...");
		}
		ImageStack xy_stack = new ImageStack(width, newH);
		boolean isHeadFirst = PlanarSupport.isHeadFirst(srcStack);
		for(int y =0; y<height; y++) {
			Object newpix = null;
			ImageProcessor xy_ip = null;
			if (ip instanceof ShortProcessor) {
				newpix = new short[width * size];
			} else if (ip instanceof ByteProcessor) {
				newpix = new byte[width * size];
			} else if (ip instanceof FloatProcessor) {
				newpix = new float[width * size];
			} else if (ip instanceof ColorProcessor) {
				newpix = new int[width * size];
			}
			// copy lines to newPixel
			for (int i = 0; i < size; i++) {
				Object pixels = is.getPixels(i + 1);
				System.arraycopy(pixels, width * y, newpix, width * i, width);
			}
			if (ip instanceof ShortProcessor) {
				xy_ip = new ShortProcessor(width, size, (short[]) newpix, ip.getCurrentColorModel());
			} else if (ip instanceof ByteProcessor) {
				xy_ip = new ByteProcessor(width, size, (byte[]) newpix, ip.getCurrentColorModel());
			} else if (ip instanceof FloatProcessor) {
				xy_ip = new FloatProcessor(width, size, (float[]) newpix, ip.getCurrentColorModel());
			} else if (ip instanceof ColorProcessor) {
				xy_ip = new ColorProcessor(width, size, (int[]) newpix);
			}
			
			if (cm != null && xy_ip != null && (ip instanceof ColorProcessor)) {
				xy_ip.setColorModel(cm);
			}

			int width2 = xy_ip.getWidth();
			int height2 = newH;
			if (width2 != xy_ip.getWidth() || height2 != xy_ip.getHeight()) {
				xy_ip.setInterpolationMethod(ImageProcessor.NONE);
				xy_ip = xy_ip.resize(width2, height2);
			}
			if (!rgb) {
				xy_ip.setMinAndMax(min, max);
			}
			/*
			 * To adding iop and ipp.
			 */
			ImagePlus xy_slice = new ImagePlus(""+y, xy_ip);
			
			PlanarSupport psup = new PlanarSupport();

			double col = 0;// x direction
			double row = y;// y direction
			int pos_z = 1;//1 to N
			Vector3d ipp_vec = psup.getNewImagePositionPatient2D(srcStack, col, row, pos_z);
			double[] cor_iop = GDicomTools.getImageOrientationPatient(srcStack, pos_z);
			double[] rowVec = new double[] {cor_iop[0],cor_iop[1],cor_iop[2]};//X
			double[] colVec = new double[] {cor_iop[3],cor_iop[4],cor_iop[5]};//Z
			double[] yVec = PlanarSupport.calculateNormal(rowVec, colVec);//Y
			yVec = PlanarSupport.truncate(yVec, 6);
			//if y vector in yVec is minus, reverse yVec.
			if(yVec[1] < 0.0) {
				yVec[0] *= -1;
				yVec[1] *= -1;
				yVec[2] *= -1;
			}
			double[] iop = null;
			if(isHeadFirst) {
				iop = new double[] {rowVec[0], rowVec[1], rowVec[2], yVec[0], yVec[1], yVec[2]};
//				System.out.println(java.util.Arrays.toString(iop));
//				LocalizerPoster.validateDirectionCosines(iop);
			}else {
				iop = new double[] {rowVec[0], rowVec[1], rowVec[2], yVec[0], yVec[1], yVec[2]};
			}
			
//			if(isHeadFirst) {
//				iop = PlanarSupport.rotateImageOrientationPatient(srcStack, 90, 0, 0);
//			}else {
//				iop = PlanarSupport.rotateImageOrientationPatient(srcStack, -90, 0, 0);
//			}
			if (ipp_vec != null && iop !=null) {
				double[] ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
//				System.out.println("CORONAL ImagePosition: " + Arrays.toString(ipp));
				GDicomTools.setImagePositionPatient(xy_slice, 1, ipp);
				GDicomTools.setImageOrientationPatient(xy_slice, 1, iop);
			}
			xy_stack.addSlice(xy_slice.getStack().getSliceLabel(1),xy_slice.getProcessor());
		}
		ImagePlus xy = new ImagePlus("XY", xy_stack);
		Calibration cal_xy = new Calibration();
		cal_xy.setXUnit(xunit);
		cal_xy.setYUnit(zunit);
		cal_xy.setZUnit(yunit);
		cal_xy.pixelWidth = src_px;
		cal_xy.pixelHeight = src_pz / az;
		cal_xy.pixelDepth = src_py;
		xy.setCalibration(cal_xy);
		return xy;
	}
	
	public ImagePlus sagittalToAxial(ImagePlus srcStack) {
		if(ImageOrientation.getCutSurface(srcStack) != CutSurface.SAGITTAL) {
        	throw new IllegalArgumentException("Need SAG stack volume");
        }
		ImageStack is = getStack(srcStack);
		ImageProcessor ip = is.getProcessor(1);
		int width = is.getWidth();//newH
		int height = is.getHeight();//newDepth
		int size = is.getSize();
		boolean rgb = ip instanceof ColorProcessor;
		ColorModel cm = rgb ? null : srcStack.getProcessor().getColorModel();
		double min = srcStack.getDisplayRangeMin();
		double max = srcStack.getDisplayRangeMax();
		Calibration cal = srcStack.getCalibration().copy();
		String xunit = cal.getXUnit();
		String yunit = cal.getYUnit();
		String zunit = cal.getZUnit();
		double src_pz = GDicomTools.getVoxelDepth(srcStack);
		double src_py = cal.pixelHeight;
		double src_px = cal.pixelWidth;
		double az = src_pz / src_px;// keep z/x relationship
		int newW = (int) Math.ceil(size * az);
		if (newW < 1) {
			throw new IllegalArgumentException("Can not create XY plane...");
		}
		ImageStack xy_stack = new ImageStack(newW, width);
		boolean isHeadFirst = PlanarSupport.isHeadFirst(srcStack);
		for(int y =0; y<height; y++) {//num of slices in axial
			Object newpix = null;
			ImageProcessor xy_ip = null;
			if (ip instanceof ShortProcessor) {
				newpix = new short[width * size];
			} else if (ip instanceof ByteProcessor) {
				newpix = new byte[width  * size];
			} else if (ip instanceof FloatProcessor) {
				newpix = new float[width  * size];
			} else if (ip instanceof ColorProcessor) {
				newpix = new int[width  * size];
			}
			// copy lines to newPixel
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < size; z++) {
					Object pixels = is.getPixels(z + 1);
					System.arraycopy(pixels, y * width + x, newpix, x * size + z, 1);
				}
			}
			if (ip instanceof ShortProcessor) {
				xy_ip = new ShortProcessor(size, width, (short[]) newpix, ip.getCurrentColorModel());
			} else if (ip instanceof ByteProcessor) {
				xy_ip = new ByteProcessor(size, width, (byte[]) newpix, ip.getCurrentColorModel());
			} else if (ip instanceof FloatProcessor) {
				xy_ip = new FloatProcessor(size, width, (float[]) newpix, ip.getCurrentColorModel());
			} else if (ip instanceof ColorProcessor) {
				xy_ip = new ColorProcessor(size, width, (int[]) newpix);
			}
			
			if (cm != null && xy_ip != null && (ip instanceof ColorProcessor)) {
				xy_ip.setColorModel(cm);
			}

			int width2 = newW;
			int height2 = width;
			if (width2 != xy_ip.getWidth() || height2 != xy_ip.getHeight()) {
				xy_ip.setInterpolationMethod(ImageProcessor.NONE);
				xy_ip = xy_ip.resize(width2, height2);
			}
			if (!rgb) {
				xy_ip.setMinAndMax(min, max);
			}
			/*
			 * To adding iop and ipp.
			 */
			ImagePlus xy_slice = new ImagePlus(""+y, xy_ip);
			
			PlanarSupport psup = new PlanarSupport();

			double col = 0;// x direction
			double row = y;// y direction
			int pos_z = 1;//1 to N
			Vector3d ipp_vec = psup.getNewImagePositionPatient2D(srcStack, col, row, pos_z);
			double[] sag_iop = GDicomTools.getImageOrientationPatient(srcStack, pos_z);
			double[] rowVec = new double[] {sag_iop[0],sag_iop[1],sag_iop[2]};//Y
			double[] colVec = new double[] {sag_iop[3],sag_iop[4],sag_iop[5]};//Z
			double[] xVec = PlanarSupport.calculateNormal(rowVec, colVec);//X
			xVec = PlanarSupport.truncate(xVec, 6);
			double[] iop = null;
			if(isHeadFirst) {
				if(xVec[0] < 0.0) {
					xVec[0] *= -1;
					xVec[1] *= -1;
					xVec[2] *= -1;
//					rowVec[0] *= -1;
				}
				iop = new double[] {xVec[0], xVec[1], xVec[2], rowVec[0], rowVec[1], rowVec[2]};
//				System.out.println(java.util.Arrays.toString(iop));
//				LocalizerPoster.validateDirectionCosines(iop);
			}else {
				if(xVec[0] > 0.0) {
					xVec[0] *= -1;
					xVec[1] *= -1;
					xVec[2] *= -1;
				}
				iop = new double[] {xVec[0], xVec[1], xVec[2], rowVec[0], rowVec[1], rowVec[2]};
			}
			if (ipp_vec != null && iop !=null) {
				double[] ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
				GDicomTools.setImagePositionPatient(xy_slice, 1, ipp);
				GDicomTools.setImageOrientationPatient(xy_slice, 1, iop);
			}
			xy_stack.addSlice(xy_slice.getStack().getSliceLabel(1),xy_slice.getProcessor());
		}
		ImagePlus xy = new ImagePlus("XY", xy_stack);
		Calibration cal_xy = new Calibration();
		cal_xy.setXUnit(zunit);
		cal_xy.setYUnit(xunit);
		cal_xy.setZUnit(yunit);
		cal_xy.pixelWidth = src_pz / az;
		cal_xy.pixelHeight = src_px;
		cal_xy.pixelDepth = src_py;
		xy.setCalibration(cal_xy);
		return xy;
	}
	
	public ImageStack getStack(ImagePlus imp) {
		if (imp.isHyperStack()) {
			int slices = imp.getNSlices();
			int c = imp.getChannel();
			int z = imp.getSlice();
			int t = imp.getFrame();
			int mode = imp.getCompositeMode();
			boolean rgb = mode == IJ.COMPOSITE;
			ImageStack stack = imp.getStack();
			ImageStack stack2 = new ImageStack(imp.getWidth(), imp.getHeight());
			if (slices == 1) {
				String hdr = imp.getInfoProperty();
				if (rgb) {
					imp.setPositionWithoutUpdate(c, 1, t);
					stack2.addSlice(hdr, new ColorProcessor(imp.getImage()));
				} else {
					int index = imp.getStackIndex(c, 1, t);
					stack2.addSlice(hdr, stack.getProcessor(index));
				}
			} else {
				for (int i = 1; i <= slices; i++) {
					if (rgb) {
						imp.setPositionWithoutUpdate(c, i, t);
						stack2.addSlice(stack.getSliceLabel(i), new ColorProcessor(imp.getImage()));
					} else {
						int index = imp.getStackIndex(c, i, t);
						stack2.addSlice(stack.getSliceLabel(i), stack.getProcessor(index));
					}
				}
			}
			// reset position
			if (rgb) {
				imp.setPosition(c, z, t);
			} else {
				imp.setPosition(1);
			}
			return stack2;
		} else {
			return imp.getStack();
		}
	}
	
	/**
	 * If true with HFS/HFP position, it slicing Foot->Head direction in RCS.
	 * @param imp
	 * @return
	 */
	public boolean isSlicingToUpperZSide(ImagePlus imp) {
		if(ImageOrientation.getCutSurface(imp) != CutSurface.AXIAL) {
			throw new IllegalArgumentException("This images are not AXIAL...");
		}
		int size = imp.getNSlices();
		int prev_pos = imp.getCurrentSlice();
		if(size > 1) {
			double[] ipp1 = GDicomTools.getImagePositionPatient(imp, 1);
			double[] ipp2 = GDicomTools.getImagePositionPatient(imp, 2);
			if(ipp1 != null && ipp2 != null) {
				//back to prev pos
				imp.setSlice(prev_pos);
				return ipp1[2] < ipp2[2];
			}
		}
		return false;
	}
	
	/**
	 * If true with HFS position, it slicing A->P direction in RCS.
	 * @param imp
	 * @return
	 */
	public boolean isSlicingToUpperYSide(ImagePlus imp) {
		if(ImageOrientation.getCutSurface(imp) != CutSurface.CORONAL) {
			throw new IllegalArgumentException("This images are not CORONAL...");
		}
		int size = imp.getNSlices();
		int prev_pos = imp.getCurrentSlice();
		if(size > 1) {
			double[] ipp1 = GDicomTools.getImagePositionPatient(imp, 1/*slice pos*/);
			double[] ipp2 = GDicomTools.getImagePositionPatient(imp, 2/*slice pos*/);
			if(ipp1 != null && ipp2 != null) {
				//back to prev pos
				imp.setSlice(prev_pos);
				return ipp1[1] < ipp2[1];
			}
		}
		return false;
	}
	
	/**
	 * If true with HFS position, it slicing R->L direction in RCS.
	 * @param imp
	 * @return
	 */
	public boolean isSlicingToUpperXSide(ImagePlus imp) {
		if(ImageOrientation.getCutSurface(imp) != CutSurface.SAGITTAL) {
			throw new IllegalArgumentException("This images are not SAGITTAL...");
		}
		int size = imp.getNSlices();
		int prev_pos = imp.getCurrentSlice();
		if(size > 1) {
			double[] ipp1 = GDicomTools.getImagePositionPatient(imp, 1/*slice pos*/);
			double[] ipp2 = GDicomTools.getImagePositionPatient(imp, 2/*slice pos*/);
			if(ipp1 != null && ipp2 != null) {
				//back to prev pos
				imp.setSlice(prev_pos);
				return ipp1[0] < ipp2[0];
			}
		}
		return false;
	}
	
	private int getOriginSlicePosition(int totalSliceSize, boolean isSlicingToUpperZ, boolean isHeadFirst) {
		if (isHeadFirst && isSlicingToUpperZ) return totalSliceSize;
		if (!isHeadFirst && isSlicingToUpperZ) return 1;
		if (isHeadFirst && !isSlicingToUpperZ) return 1;
		if (!isHeadFirst && !isSlicingToUpperZ) return totalSliceSize;
		return 1;
	}

}
