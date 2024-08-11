package com.vis.core.view.D2.processing;

import com.vis.core.view.D2.roi.RoiConverter;
import com.vis.core.view.D2.roi.RoiObj;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;

public class ImageProcessing {
	
	public ImageProcessing(){}
	
	public void invert(ImagePlus imp) {
		imp.getProcessor().invert();//invertLUT??
		imp.updateImage();
	}
	
	public void windowing(ImagePlus imp, double currentMin, double currentMax) {
		if(imp.getType() == ImagePlus.COLOR_RGB) {
			//fail safe
			if(imp.getProcessor().getSnapshotPixels() == null) {
				imp.getProcessor().snapshot();
			}
			imp.getProcessor().reset();
		}
		imp.setDisplayRange(currentMin, currentMax);
		imp.updateImage();
	}
	
	public ImagePlus zoom(ImagePlus imp, double mag) {
		Calibration cal = imp.getCalibration().copy();
//		ImagePlus imp2 = imp.resize((int)(imp.getWidth()*mag), (int)(imp.getHeight()*mag), "bicubic");//too slow? 
		ImageProcessor ip = imp.getProcessor().duplicate();
//		ip.setInterpolationMethod(ImageProcessor.BICUBIC);//slow ?
		ip.setInterpolationMethod(ImageProcessor.BILINEAR);
		ip = ip.resize((int)(imp.getWidth()*mag), (int)(imp.getHeight()*mag));
		ImagePlus imp2= new ImagePlus("display", ip);
		if (cal.scaled()) {
			cal.pixelWidth *= 1.0/(imp2.getWidth()/imp.getWidth());
			cal.pixelHeight *= 1.0/(imp2.getHeight()/imp.getHeight());
		}
		imp2.setCalibration(cal);
//		System.out.println("zoom result::"+imp2.getWidth()+"  "+imp2.getHeight());
		imp = null;//.flush();
		return imp2;
	}
	
	public void rotate(ImagePlus imp, double angle) {
		imp.getProcessor().rotate(angle);
		imp.updateImage();
	}
	
	public ImagePlus rotateRight(ImagePlus imp, double currentAngle) {
		Calibration cal = imp.getCalibration().copy();
		ImageProcessor ip = imp.getProcessor().rotateRight();
		imp = new ImagePlus("replica", ip);
		imp.setCalibration(cal);
		//explicit
//		double angle = currentAngle+90;
//		rotate(imp,angle);
		imp.updateImage();
		return imp;
	}
	
	public ImagePlus rotateLeft(ImagePlus imp, double currentAngle) {
		Calibration cal = imp.getCalibration().copy();
		ImageProcessor ip = imp.getProcessor().rotateLeft();
		imp = new ImagePlus("replica", ip);
		imp.setCalibration(cal);
		//explicit
//		double angle = currentAngle+90;
//		rotate(imp,angle);
		imp.updateImage();
		return imp;
	}
	
	public void rotate180(ImagePlus imp, double currentAngle) {
		//explicit
		double angle = currentAngle+180;
		rotate(imp,angle);
	}
	
	public void flipHF(ImagePlus imp,double currentAngle) {
		imp.getProcessor().flipVertical();
		//explicit
//		double angle = currentAngle+180;
//		rotate(imp, angle);
		imp.updateImage();
	}

	public void flipLR(ImagePlus imp) {
		imp.getProcessor().flipHorizontal();
		imp.updateImage();
	}
	
	public ImagePlus crop(ImagePlus imp, RoiObj roi) {
		if(roi != null) {
			ij.gui.Roi ijRoi = new RoiConverter().convert2Roi(roi);
			if(ijRoi != null) {
				imp.deleteRoi();//init
				imp.setRoi(ijRoi);
			}
		}
		Calibration cal = imp.getCalibration().copy();
		/*
		 * DO NOT USE imageprocessor.crop(). will occur white-out.
		 */
		ImagePlus sliceCrop = imp.crop();
		sliceCrop.setCalibration(cal);
		return sliceCrop;
	}
}
