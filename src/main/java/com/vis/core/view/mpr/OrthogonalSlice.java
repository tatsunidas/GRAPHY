package com.vis.core.view.mpr;

import java.awt.image.ColorModel;

import org.joml.Vector3d;

import com.vis.core.view.D2.ui.orientation.ImageOrientation;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
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

public class OrthogonalSlice {
	
	/**
	 * 
	 * @param src
	 * @param y_cutPoint : offScreen coordinate Y
	 * @param flipXZ : usualy, true
	 * @return
	 */
	
	//debug
	public static void main(String[] args) {
		String dir = "D:\\Dropbox\\Graphy-WorkSpace2\\graphy-parent\\graphy-resource\\src\\test\\resources\\dicom_samples\\LGG-104\\06-26-2000-MRI Hd wow-05523\\4-Gad Ax T2 Straight-38151";
		ImagePlus xy = FolderOpener.open(dir);
		OrthogonalSlice slicer = new OrthogonalSlice();
		xy.setPosition(xy.getNSlices()/2);
		ImagePlus xz_no_flip = slicer.cutXZ(xy, xy.getHeight()/2-1, 1, false, false);
		ImagePlus xz_flip = slicer.cutXZ(xy, xy.getHeight()/2-1, 1, true,false);
		xz_no_flip.show();
		xz_flip.show();
	}
	
	/**
	 * Create a coronal section for the input stack.
	 * 
	 * @param src
	 * @param y_cutPoint
	 * @param slicePos
	 * @param flipVertical
	 * @param rotate90
	 * @return
	 */
	public ImagePlus cutXZ(ImagePlus src, int y_cutPoint, int slicePos, boolean flipVertical, boolean rotate90) {
        
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
		double src_pz = cal.pixelDepth;
		double src_py = cal.pixelHeight;
		double src_px = cal.pixelWidth;
		double az = src_pz / src_px;
		/*
		 * x and y is zero-based.
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
		// XZ
		if (ip instanceof ShortProcessor) {
			newpix = new short[width * size];
			//copy lines to newPixel
			for (int i = 0; i < size; i++) {
				Object pixels = is.getPixels(i + 1);
				if (flipVertical) {
					System.arraycopy(pixels, width * y, newpix, width * (size - i - 1), width);
				} else {
					System.arraycopy(pixels, width * y, newpix, width * i, width);
				}
			}
			xz_ip = new ShortProcessor(width, size, (short[]) newpix, ip.getCurrentColorModel());
		} else if (ip instanceof ByteProcessor) {
			newpix = new byte[width * size];
			for (int i = 0; i < size; i++) {
				Object pixels = is.getPixels(i + 1);
				if (flipVertical) {
					System.arraycopy(pixels, width * y, newpix, width * (size - i - 1), width);
				} else {
					System.arraycopy(pixels, width * y, newpix, width * i, width);
				}
			}
			xz_ip = new ByteProcessor(width, size, (byte[]) newpix, ip.getCurrentColorModel());
		} else if (ip instanceof FloatProcessor) {
			newpix = new float[width * size];
			for (int i = 0; i < size; i++) {
				Object pixels = is.getPixels(i + 1);
				if (flipVertical) {
					System.arraycopy(pixels, width * y, newpix, width * (size - i - 1), width);
				} else {
					System.arraycopy(pixels, width * y, newpix, width * i, width);
				}
			}
			xz_ip = new FloatProcessor(width, size, (float[]) newpix, ip.getCurrentColorModel());
		} else if (ip instanceof ColorProcessor) {
			newpix = new int[width * size];
			for (int i = 0; i < size; i++) {
				Object pixels = is.getPixels(i + 1);
				if (flipVertical) {
					System.arraycopy(pixels, width * y, newpix, width * (size - i - 1), width);
				} else {
					System.arraycopy(pixels, width * y, newpix, width * i, width);
				}
			}
			xz_ip = new ColorProcessor(width, size, (int[]) newpix);
		}
        
		if (rotate90)
			xz_ip = xz_ip.rotateRight();

		if (cm != null && xz_ip != null && xz_ip.getBitDepth() != 24) {
			xz_ip.setColorModel(cm);
		}

		int width2 = xz_ip.getWidth();
		int height2 = (int) Math.ceil(xz_ip.getHeight() * az);
		if (height2 < 1) {
			height2 = 1;
		}
		if (width2 != xz_ip.getWidth() || height2 != xz_ip.getHeight()) {
			xz_ip.setInterpolate(false);//Nearest
			ImageProcessor sxz_ip = xz_ip.resize(width2, height2);
			if (!rgb) {
				sxz_ip.setMinAndMax(min, max);
			}
			xz_image.setProcessor("", sxz_ip);
		} else {
			if (!rgb) {
				xz_ip.setMinAndMax(min, max);
			}
			xz_image.setProcessor("", xz_ip);
		}
		
		PlanarSupport psup = new PlanarSupport();
		CutSurface planar = ImageOrientation.getCutSurface(src);

		double col = 0;// x direction
		double row = y;
		Vector3d ipp_vec = null;
		double[] iop = null;
		src.setPosition(slicePos);
		switch (planar) {
		case SAGITTAL:
			// YZ to YX in RCS
			col = 0;// x direction
			row = y;
			src.setPosition(slicePos);
			ipp_vec = psup.getNewImagePositionPatient2D(src, col, row, 1);
			if (ipp_vec != null) {
				double[] ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
				GDicomTools.setImagePositionPatient(xz_image, 1, ipp);
				src.setPosition(slicePos);
				// SAG Image Orientation (Patient): 0\1\0\0\0\-1, in Degrees : 90\0\90\90\90\180
				iop = psup.getNewImageOrientationPatient(src, 0, 0, 0, -90, 0, -90);
				GDicomTools.setImageOrientationPatient(xz_image, 1, iop);
			}
			break;
		case CORONAL:
			// XZ to XY
			col = 0;// x direction
			row = y;
			src.setPosition(slicePos);
			ipp_vec = psup.getNewImagePositionPatient2D(src, col, row, 1);
			if (ipp_vec != null) {
				double[] ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
				GDicomTools.setImagePositionPatient(xz_image, 1, ipp);
				src.setPosition(slicePos);
				// COR Image Orientation (Patient): 1\0\0\0\0\-1 ( in degrees 0\90\90\90\90\180)
				iop = psup.getNewImageOrientationPatient(src, 0, 0, 0, 0, -90, -90);
				GDicomTools.setImageOrientationPatient(xz_image, 1, iop);
			}
			break;
		case AXIAL:
		case OBLIQUE:// here, treat as axial
		case UNKNOWN:
		default:
			// XY to XZ
			src.setPosition(slicePos);
			ipp_vec = psup.getNewImagePositionPatient2D(src, col, row, size);
			if (ipp_vec != null) {
				double[] ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
				GDicomTools.setImagePositionPatient(xz_image, 1, ipp);
				// 1\0\0\0\1\0, in degrees 0\90\90\90\0\90
				src.setPosition(slicePos);
				iop = psup.getNewImageOrientationPatient(src, 0, 0, 0, 0, 90, 90);
				GDicomTools.setImageOrientationPatient(xz_image, 1, iop);
			}
			break;
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
	 * @param src
	 * @param x_cutPoint: 0 to w-1
	 * @param slicePos: 1 to n
	 * @param flipVertical: flip before rotate90
	 * @param rotate90 : clock-wise 90 degree
	 * @return
	 */
	public ImagePlus cutYZ(ImagePlus src, int x_cutPoint, int slicePos, boolean flipVertical, boolean rotate90) {
		
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
		double src_pz = cal.pixelDepth;
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

		if (ip instanceof FloatProcessor) {
			newpix = new float[size * height];
			for (int i = 0; i < size; i++) {
				float[] pixels = (float[]) is.getPixels(i + 1);// toFloatPixels(pixels);
				for (int j = 0; j < height; j++) {
					((float[]) newpix)[(size - i - 1) * height + j] = pixels[x + j * width];
				}
			}
			yz_ip = new FloatProcessor(height, size, (float[]) newpix, ip.getColorModel());
		} else if (ip instanceof ByteProcessor) {
			newpix = new byte[size * height];
			for (int i = 0; i < size; i++) {
				byte[] pixels = (byte[]) is.getPixels(i + 1);// toFloatPixels(pixels);
				for (int j = 0; j < height; j++) {
					((byte[]) newpix)[(size - i - 1) * height + j] = pixels[x + j * width];
				}
			}
			yz_ip = new ByteProcessor(height, size, (byte[]) newpix, ip.getColorModel());
		} else if (ip instanceof ShortProcessor) {
			newpix = new short[size * height];
			for (int i = 0; i < size; i++) {
				short[] pixels = (short[]) is.getPixels(i + 1);// toFloatPixels(pixels);
				for (int j = 0; j < height; j++) {
					((short[]) newpix)[(size - i - 1) * height + j] = pixels[x + j * width];
				}
			}
			yz_ip = new ShortProcessor(height, size, (short[]) newpix, ip.getColorModel());
		} else if (ip instanceof ColorProcessor) {
			newpix = new int[size * height];
			for (int i = 0; i < size; i++) {
				int[] pixels = (int[]) is.getPixels(i + 1);// toFloatPixels(pixels);
				for (int j = 0; j < height; j++) {
					((int[]) newpix)[(size - i - 1) * height + j] = pixels[x + j * width];
				}
			}
			yz_ip = new ColorProcessor(height, size, (int[]) newpix);
		}

		if (cm != null && yz_ip != null && yz_ip.getBitDepth() != 24) {
			yz_ip.setColorModel(cm);
		}

		int width2 = yz_ip.getWidth();
		int height2 = (int) Math.ceil(yz_ip.getHeight() * az);
		if (width2 != yz_ip.getWidth() || height2 != yz_ip.getHeight()) {
			yz_ip.setInterpolate(true);
			ImageProcessor syz_ip = yz_ip.resize(width2, height2);
			if (flipVertical)
				syz_ip.flipVertical();
			if (rotate90)
				syz_ip = syz_ip.rotateRight();
			if (!rgb)
				syz_ip.setMinAndMax(min, max);
			yz_image.setProcessor("", syz_ip);
		} else {
			if (flipVertical)
				yz_ip.flipVertical();
			if (rotate90)
				yz_ip = yz_ip.rotateRight();
			if (!rgb)
				yz_ip.setMinAndMax(min, max);
			yz_image.setProcessor("", yz_ip);
		}

		PlanarSupport psup = new PlanarSupport();
		CutSurface planar = ImageOrientation.getCutSurface(src);
		// allways ortho
		Vector3d ipp_vec = null;
		double[] ipp = null;
		double[] iop = null;
		double col = x;// x direction on prap
		double row = 0;
		switch (planar) {
		case SAGITTAL:
			// YZ to XZ
			src.setPosition(slicePos);
			ipp_vec = psup.getNewImagePositionPatient2D(src, col, row, 1);
			if (ipp_vec != null) {
				ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
				GDicomTools.setImagePositionPatient(yz_image, 1, ipp);
				src.setPosition(slicePos);
				// SAG Image Orientation (Patient): 0\1\0\0\0\-1, inDegrees : 90\0\90\90\90\180
				iop = psup.getNewImageOrientationPatient(src, -90, 90, 0, 0, 0, 0);// 1\0\0\0\0\-1
				GDicomTools.setImageOrientationPatient(yz_image, 1, iop);
			}
			break;
		case CORONAL:
			// XZ to YZ
			src.setPosition(slicePos);
			ipp_vec = psup.getNewImagePositionPatient2D(src, col, row, 1);
			if (ipp_vec != null) {
				ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
				GDicomTools.setImagePositionPatient(yz_image, 1, ipp);
				src.setPosition(slicePos);
				// COR Image Orientation (Patient): 1\0\0\0\0\-1, inDegrees : 0\90\90\90\90\180
				iop = psup.getNewImageOrientationPatient(src, 90, -90, 0, 0, 0, 0);// 0\1\0\0\0\-1
				GDicomTools.setImageOrientationPatient(yz_image, 1, iop);
			}
			break;
		case AXIAL:
		case OBLIQUE:// here, treat as axial
		case UNKNOWN:
		default:
			// to YZ
			src.setPosition(slicePos);
			ipp_vec = psup.getNewImagePositionPatient2D(src, col, row, size);
			if (ipp_vec != null) {
				ipp = new double[] { ipp_vec.x(), ipp_vec.y(), ipp_vec.z() };
				GDicomTools.setImagePositionPatient(yz_image, 1, ipp);
				src.setPosition(slicePos);
				// AXI, 1\0\0\0\1\0, in degrees 0\90\90\90\0\90
				iop = psup.getNewImageOrientationPatient(src, 90, -90, 0, 0, 90, 90);// 0\1\0\0\0\-1
				GDicomTools.setImageOrientationPatient(yz_image, 1, iop);
			}
			break;
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

}
