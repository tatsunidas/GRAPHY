package com.vis.core.view.D2.processing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import javax.swing.JLayer;
import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;

import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomWriter;
import com.vis.core.view.D2.roi.*;
import com.vis.core.view.D2.ui.glasses.*;
import com.vis.core.view.D2.ui.Viewer2DScreen;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;

/**
 * masking using roiObj
 * @author tatsu
 *
 */
public class Masking {
	
	/*
	 * 
	 */
	
//	public Masking(RoiObj roi) {
//		if(roi == null) {
//			return;
//		}
//		if(roi.getSlideGlass() == null) {
//			System.out.println("This roi does not have SlideGlass. return Masking...");
//			return;
//		}
//		RoiObjManager rm = Viewer2DFrame.getRoiObjManager();
//		boolean processSeries = false;
//		boolean createNewSeries = false;
//		int res = JOptionPane.showConfirmDialog(rm, "Mask all images in this series ? (YES:mask all, NO:mask current image only)");
//		if(res == JOptionPane.OK_OPTION) {
//			processSeries = true;
//		}else if(res == JOptionPane.NO_OPTION) {
//			processSeries = false;
//		}else {
//			return;//Canceling
//		}
//		res = JOptionPane.showConfirmDialog(rm, "Create as new series ?(YES:create new series in db, NO:show image only)");
//		if(res == JOptionPane.OK_OPTION) {
//			createNewSeries = true;
//		}else if(res == JOptionPane.NO_OPTION) {
//			createNewSeries = false;
//		}else {
//			return;//Canceling
//		}
//		doMask(roi, roi.getSlideGlass(), processSeries, createNewSeries);
//	}
//	
//	/**
//	 * draw pixel and create new series.
//	 * @param seriesProcess
//	 */
//	public static void doMask(RoiObj roi, SlideGlass slide, boolean processSeries, boolean createNewSeries) {
//		if(roi == null || slide == null) {
//			System.out.println("Masking::Please set roiObj and slideglass. return.");
//			return;
//		}
//		//show only
//		if(!processSeries && !createNewSeries) {
//			masking(roi,slide);
//		}else if(!processSeries && createNewSeries) {
//			masking(roi,slide);
//			createNewSeriesAndSaveToDB(slide.getPraparat());
//		}else if(processSeries && !createNewSeries) {
//			Praparat prap = slide.getPraparat();
//			HashMap<Integer, JLayer<SlideGlass>> slides = prap.getAllSlides();
//			Set<Integer> keys = slides.keySet();
//			for(int readNo : keys) {
//				masking(roi, slides.get(readNo).getView());
//			}
//		}else if(processSeries && createNewSeries) {
//			Praparat prap = slide.getPraparat();
//			HashMap<Integer, JLayer<SlideGlass>> slides = prap.getAllSlides();
//			Set<Integer> keys = slides.keySet();
//			for(int readNo : keys) {
//				masking(roi, slides.get(readNo).getView());
//			}
//			createNewSeriesAndSaveToDB(prap);
//		}
//	}
//	
//	private static void masking(RoiObj roi, SlideGlass slide) {
//		ImagePlus orgImp = slide.getOriginalImage();
//		Calibration cal = orgImp.getCalibration().copy();
//		ij.gui.Roi ijRoi = new RoiConverter().convert2Roi(roi);
//		orgImp.setRoi(ijRoi);
//		orgImp.setProcessor(orgImp.getProcessor().crop());
//		orgImp.setCalibration(cal);
//		orgImp.updateImage();
//		slide.setOriginalImage(orgImp);
//		slide.repaint();
//	}
}
