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
package com.vis.core.view.D2.ui.glasses;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.BirdsEyeView;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.processing.ImageProcessing;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.SeriesWindow;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.IntersectVolume;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.core.view.mpr.ReferenceLineMPR;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.GDicomTools;
import com.vis.imageio.Codec;
import com.vis.imageio.Decompressor;
import com.vis.imageio.PDFReader;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

/**
 * SeriesViewer
 */
@SuppressWarnings("serial")
public class Praparat extends JPanel {
	
	final int minimumGridCol = 5;
	private Logger logger = Log.logger;
	
	public enum ViewMode{
		Normal,/*Switch-able mode, both single view and film grid view*/
		Thumbnail,/*Thumbnail mode ( has limit some features)*/
		SingleGrid,/*Single grid view, film grid view no acceptable.(for bird's eye)*/
		FilmGrid,/*Film grid view, single grid view no acceptable. (for bird's eye)*/
		MPR,/*Allow showing crosslines. Its features are same as Normal mode.*/
	}
	//patient info set keys
	public final String KEY_PadID = "Patient​ID";
	public final String KEY_StudyUID = "StudyInstanceUID";
	public final String KEY_SeriesUID = "SeriesInstanceUID";
	public final String KEY_SopUIDs = "SOPInstanceUIDs";
		
	// component
	private PraparatViewControlPanel pvcp;
	private JPanel viewPanel;
	private SlideGlassGrid gridScrollPane;
	
	int prevViewPanelW = 0;
	int prevViewPanelH = 0;
	
	private CineSlider slider;
	private Color studyColor = Color.CYAN;
	
	public static final int ThumbnailSize = BirdsEyeView.thumbnailSize;
	private int currentSlice = 0;
	private int prevSlice = -1;

	private int filmGridColumns = 5;
	private boolean isMultiframe = false;/*to set video option*/
	private boolean isPDF = false;
	private boolean selected = false;
	private boolean focusGained = false;
	private boolean showGridViewOn = false;//filemGridView
		
	private boolean crossLineCursorMode = false;//mpr

	private ReferenceLineMPR refLineMPR;

	private ArrayList<String> pathToImages = null;
	Eyepiece prapManager;
	String patID;
	String studyUID;
	String seriesUID;
	String[] sopUIDs;
	String frameOfReferenceUID;
	String modality = null;
	
	int prevW;
	int prevH;
	
	private HashMap<Integer, SlideGlass> slides;
	
	final ViewMode mode;
	
	/**
	 * Load normal praparat
	 * @param stack
	 * @param studyColor
	 */
	public Praparat(ImagePlus stack, Color studyColor, ViewMode mode) {
		this.mode = mode;
		if(studyColor != null) {
			this.studyColor = studyColor;
		}
		String patID = GDicomTools.getTag(stack, "0010,0020");
		if(patID != null) patID = patID.trim();
		String studyUID = GDicomTools.getTag(stack, "0020,000D");
		if(studyUID != null) studyUID = studyUID.trim();
		String seriesUID = GDicomTools.getTag(stack, "0020,000E");
		if(seriesUID != null) seriesUID = seriesUID.trim();
		String[] sopUIDs = new String[stack.getNSlices()];
		for(int i = 1; i <= stack.getNSlices(); i++) {
			stack.setPosition(i);
			String sopInstUid = GDicomTools.getTag(stack, "0008,0018");
			if(sopInstUid==null || sopInstUid.trim().length()==0) {
				sopInstUid = UIDUtils.createUID();
			}else {
				sopUIDs[i-1] = sopInstUid.trim();//need trim.
			}
		}
		String refUID = GDicomTools.getTag(stack, "0020,0052");
		setInfo(patID, studyUID, seriesUID, sopUIDs, refUID, null);
		init();
		prepareSlideGlassesUsingImagePlus(stack);
	}
	
	public Praparat(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToSortedinstNoImages, Color studyColor, ViewMode mode) {
		this(patID, studyUID, seriesUID, sopUIDs, pathToSortedinstNoImages, null, null, studyColor, mode);
	}
	
	public Praparat(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToSortedinstNoImages, String refUID, Eyepiece manager,
			Color studyColor, ViewMode mode) {
		if(mode == null) {
			this.mode = ViewMode.Normal;
		}else {
			this.mode = mode;
		}
		if(studyColor != null) {
			this.studyColor = studyColor;
		}
		this.prapManager = manager;
		setInfo(patID, studyUID, seriesUID, sopUIDs, refUID, pathToSortedinstNoImages);
		init();
		prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs, pathToSortedinstNoImages);
		if(mode != ViewMode.FilmGrid) {
			doSingleGridLayout();
		}else {
			doFilmGridLayout(null);
		}
	}

	/**
	 * Only used for Birds Eye View.
	 * 
	 * e.g.,
	 * Praparat pp = new Praparat(ViewMode.Normal);
	 * pp.prepareSlideGlassesUsingImagePlus(imp);
	 * pp.doSingleGridLayout();
	 * 
	 * @param mode
	 */
	public Praparat(ViewMode mode) {
		if(mode == null) {
			this.mode = ViewMode.Normal;
		}else {
			this.mode = mode;
		}
		init();
	}
	
	public void adjustGridViewSize() {
		synchronized (this) {
			if(gridScrollPane == null || !showGridViewOn) {
				return;
			}
			Object comp = gridScrollPane.getViewport().getView();
			if(!(comp instanceof JPanel)) {
				return;
			}
			/*
			 * update slide glass holder size. 
			 */
			int screenSizeW = getViewPanelWidth();
			int screenSizeH = getViewPanelHeight();
			
			if(screenSizeW <= 0 && screenSizeH <= 0) {
				return;
			}
			
			JPanel gridView = (JPanel) comp;
//			GridLayout gl = (GridLayout) gridView.getLayout();
			gridScrollPane.setPreferredSize(new Dimension(screenSizeW, screenSizeH));
			gridScrollPane.setBounds(0, 0, screenSizeW, screenSizeH);
			
			int margin = 4;/*to avoid horizontal scroll bar shown*/
			int scrollBarWidth = gridScrollPane.getVerticalScrollBar().getWidth()+margin;
			if(scrollBarWidth <= margin) {
				scrollBarWidth = 15/*default bar width*/+margin;
			}
//			gridView.setSize(screenSizeW-scrollBarWidth, screenSizeH);
			//DO NOT set setPreferedSize to avoid collapsing squared GridLayout
			gridView.setBounds(0, 0, screenSizeW-scrollBarWidth, screenSizeH);
			int cols = getFilmGridColumns();//gl.getColumns();
			int gridX = gridView.getWidth() / cols;
			int gridY = gridX;//grids are showed square
			int s = getNumberOfImages();
			for (int i = 0; i < s; i++) {
				SlideGlass slide = slides.get(i);
				slide.setSize(gridX, gridY);
			}
		}
	}
	
	public void adjustSlideGlassSize() {
		adjustSlideGlassSize(getViewPanelWidth(), getViewPanelHeight());
	}
	
	/**
	 * For single grid view.
	 * Run after componentResized()
	 */
	public void adjustSlideGlassSize(int w , int h) {
		if(slides == null || slides.size() == 0) {
			return;
		}
		if(!pvcp.processSeries()) {
			SlideGlass target = slides.get(currentSlice);
			target.setSize(w, h);
		}else {
			SlideGlass target = slides.get(currentSlice);
			target.setSize(w,h);
			//set origin all slides
			for (Integer key : slides.keySet()) {
				SlideGlass sl = slides.get(key);
				if(sl == target) continue;
				sl.setSize(w,h);
				if (target != null && !target.panningFlag && !sl.panningFlag) {
					sl.imageSpecimen.originX = target.imageSpecimen.originX;
					sl.imageSpecimen.originY = target.imageSpecimen.originY;
					sl.repaint();
				}
			}
		}
	}
	
	public List<Point2D> calcLocalizer(GeometryOfSlice bePostedCurrentSlide){
		SlideGlass sg = getCurrentSlide();
		GeometryOfSlice localizerGeometry = new GeometryOfSlice(sg.getHeader());
		GeometryOfSlice postImageGeometry = bePostedCurrentSlide;
		LocalizerPoster localizerPoster = new IntersectVolume(localizerGeometry);
		List<Point2D> shape = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
		return shape;
	}
	
	private List<Point2D> calcLocalizer(SlideGlass src/*will draw*/, SlideGlass target/*be posted*/) {
		GeometryOfSlice localizerGeometry = new GeometryOfSlice(src.getHeader());
		GeometryOfSlice postImageGeometry = new GeometryOfSlice(target.getHeader());
		LocalizerPoster localizerPoster = new IntersectVolume(localizerGeometry);
		List<Point2D> shape = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
		return shape;
	}
	
	/**
	 * 
	 * @param row from src
	 * @param col from src
	 * @param iop from src
	 * @param ipp from src
	 * @param voxelSize from src
	 * @param row_ from target
	 * @param col_ from target
	 * @param iop_ from target
	 * @param ipp_ from target
	 * @param voxelSize_ from target
	 * @return
	 */
	public List<Point2D> calcLocalizer(int row, int col, double[] iop, double[] ipp, double[] voxelSize/*x,y,z*/,
			double slicethickness, int row_, int col_, double[] iop_, double[] ipp_, double[] voxelSize_,double slicethickness_){
		GeometryOfSlice localizerGeometry = new GeometryOfSlice();//src
		localizerGeometry.setUp(row, col, iop, ipp, voxelSize, slicethickness);
		GeometryOfSlice postImageGeometry = new GeometryOfSlice();//target
		postImageGeometry.setUp(row_, col_, iop_, ipp_, voxelSize_, slicethickness_);
		LocalizerPoster localizerPoster = new IntersectVolume(localizerGeometry);
		List<Point2D> shape = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
		return shape;
	}
	
	public void callBackLocalizer() {
		// ref-study-uid
		Eyepiece eye = getEyepiece();
		if(eye == null) return;
		PraparatContext con = eye.getPraparatContextOf(patID, studyUID, seriesUID, sopUIDs);
		if(con == null) {
			return;
		}
		String refUid = (String) con.getContextUIDs()[4];
		// get praps which have same ref-uid
		List<Praparat> praps = eye.getAllPraparatByFrameOfReferenceUID(patID, studyUID, refUid);
		//remove previous localizers
		for(Praparat p:praps) {
			HashMap<Integer, SlideGlass> slides = p.slides;
			for(Integer k:slides.keySet()) {
				SlideGlass s = slides.get(k);
				s.drawLocalizer(null);
				s.repaintCanvasGlass();
			}
		}
		// show localizer on slideglass
		SlideGlass from = getCurrentSlide(); 
		for(Praparat p:praps) {
			//if self, skip
			if(p == this) {
				continue;
			}
			SlideGlass to = p.getCurrentSlide();
			List<Point2D> loca_geo = null;
			try {
				loca_geo = calcLocalizer(to, from);
			}catch(Exception e) {
				loca_geo = null;
			}
			to.drawLocalizer(loca_geo);
		}
	}
	
	public void clearCrossLines() {
		HashMap<Integer,SlideGlass> slides = getAllSlides();
		for(Integer sgKey : slides.keySet()) {
			SlideGlass sg = slides.get(sgKey);
			CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			cg.setCrossLine(null);
			cg.repaint();
		}
	}
	
//	private void constructSlideGlassesFromDicom(ArrayList<String> imgFiles) {
//		//including only one series.
//		if(imgFiles == null) {
//			imgFiles = getImageFileLocations();
//		}
//		if (imgFiles == null || imgFiles.size() < 1) {
//			logger.info("Please set file locations, Praparat::constructSeriesGlassesAsLayer");
//			return;
//		}
//		// init
//		slides = new HashMap<Integer, SlideGlass>();
//		
//		/*
//		 * as a premise, image files were sorted by inst No before loading.
//		 */
//		DICOMBackend backend = DICOMBackend.getCurrent();
//		for (int i = 0; i < imgFiles.size(); i++) {
//			DicomReader reader = DicomReader.newDicomReader(backend);
//			reader.read(imgFiles.get(i), false);/*read only head*/
//			DicomObject header = reader.getCore();
//			String sopClassUID = header.getString(Tag.SOP​Class​UID, "");
//			UID tsUID = reader.checkTSUID();
//			//PDF
//			if(sopClassUID.equals(UID.EncapsulatedPDFStorage.uid())) {
//				//read as series level
//				PDFReader pdfReader = new PDFReader(new File(imgFiles.get(i))/*read dicom*/);
//				ImagePlus pdfStack = pdfReader.pdf2ImageStack();
//				isMultiframe = true;//always treats multi
//				isPDF = true;
//				//if thumbnail, load only one frame
//				if(getViewMode() == ViewMode.Thumbnail) {
//					ImageProcessor instIp = pdfStack.getStack().getProcessor(1);
//					DicomObject instHeader = DicomObject.newDicomObject(header, backend);
//					instHeader.setInt(Tag.Columns, VR.US, instIp.getWidth());
//					instHeader.setInt(Tag.Rows, VR.US, instIp.getHeight());
//					instHeader.setInt(Tag.Samples​Per​Pixel, VR.US, 3);
//					instHeader.setInt(Tag.Bits​Allocated, VR.US, 8);
//					instHeader.setInt(Tag.Instance​Number, VR.IS, (1));
//					DicomImage img = DicomImage.newDicomImage(instHeader, UID.ImplicitVRLittleEndian, backend);
//					img.setPixelData(0, pdfStack.getWidth(), pdfStack.getHeight(), 3, 8, instIp.getPixels());
//					SlideGlass sg = new SlideGlass(this, img);
//					slides.put(0, sg);
//				}else {
//					for (int j = 0; j < pdfStack.getNSlices(); j++) {
//						ImageProcessor instIp = pdfStack.getStack().getProcessor(j + 1);
//						DicomObject instHeader = DicomObject.newDicomObject(header, backend);
//						instHeader.setInt(Tag.Columns, VR.US, instIp.getWidth());
//						instHeader.setInt(Tag.Rows, VR.US, instIp.getHeight());
//						instHeader.setInt(Tag.Samples​Per​Pixel, VR.US, 3);
//						instHeader.setInt(Tag.Bits​Allocated, VR.US, 8);
//						instHeader.setInt(Tag.Instance​Number, VR.IS, (j + 1));
//						DicomImage img = DicomImage.newDicomImage(instHeader, UID.ImplicitVRLittleEndian, backend);
//						img.setPixelData(j, pdfStack.getWidth(), pdfStack.getHeight(), 3, 8, instIp.getPixels());
//						SlideGlass sg = new SlideGlass(this, img);
//						slides.put(j, sg);
//					}
//				}
//				pdfReader.close();
//				break;//if multiframe, load only first file.
//			}
//			// images
//			int size = header.getInt(Tag.Number​Of​Frames, -1);
//			/*
//			 * isMultiFrame
//			 * 1.General image types do not have NumberOfFrames tag.(means -1).
//			 * 2.When image acquiring in 3d sequence on MRI, number of frame is 1 (of each image).
//			 */
//			isMultiframe = size > 1;
//			//single frame
//			if(!isMultiframe) {
//				DicomImage dcmimg = DicomImage.newDicomImage(imgFiles.get(i), backend);
//				if(Codec.isCompressed(tsUID.uid())) {
//					Decompressor.newInstance(dcmimg).decompress();
//				}
//				SlideGlass sg = new SlideGlass(this, dcmimg);
//				slides.put(i, sg);
//			}else {
//				/*
//				 * multiframe to one series.
//				 */
//				DicomReader video_reader_ = DicomReader.newDicomReader(backend);
//				video_reader_.read(imgFiles.get(i), true/*with bulk*/);
//				DicomImage videoDcm = DicomImage.newDicomImage(video_reader_.getCore(), video_reader_.checkTSUID());
//				int w = header.getInt(Tag.Columns, 0);
//				int h = header.getInt(Tag.Rows, 0);
//				int c = header.getInt(Tag.Samples​Per​Pixel, 1);
//				int bits = header.getInt(Tag.Bits​Allocated, 8);
//				/*
//				 * to address jpeg multiframe
//				 */
//				if(Codec.isCompressed(reader.checkTSUID())) {
//					Decompressor.newInstance(videoDcm).decompress();
//				}
//				for(int j=0;j<size;j++) {
//					DicomObject instHeader = DicomObject.newDicomObject(header, backend);
//					instHeader.setInt(Tag.Instance​Number, VR.IS, (j+1));
//					DicomImage frame = DicomImage.newDicomImage(instHeader, UID.ImplicitVRLittleEndian, backend);
//					frame.setPixelData(j, w, h, c, bits, videoDcm.getImageProcessor(j).getPixels());
//					SlideGlass sg = new SlideGlass(this, frame);
//					slides.put(j, sg);
//				}
//				video_reader_ = null;
//				/*
//				 * if multiframe, load as only one file.
//				 */
//				break;
//			}
//			reader = null;
//		}
//	}
	
	private void constructSlideGlassesFromDicom(ArrayList<String> imgFiles) {
		//including only one series.
		if(imgFiles == null) {
			imgFiles = getImageFileLocations();
		}
		if (imgFiles == null || imgFiles.size() < 1) {
			logger.info("Please set file locations, Praparat::constructSeriesGlassesAsLayer");
			return;
		}
		// init
		slides = new HashMap<Integer, SlideGlass>();
		
		/*
		 * as a premise, image files were sorted by inst No before loading.
		 */
		DICOMBackend backend = DICOMBackend.getCurrent();
		for (int i = 0; i < imgFiles.size(); i++) {
			DicomReader reader = DicomReader.newDicomReader(backend);
			reader.read(imgFiles.get(i), false);/*read only head*/
			DicomObject header = reader.getCore();
			String sopClassUID = header.getString(Tag.SOP​Class​UID, "");
			UID tsUID = reader.checkTSUID();
			//PDF
			if(sopClassUID.equals(UID.EncapsulatedPDFStorage.uid())) {
				//read as series level
				isMultiframe = true;//always treats multi
				isPDF = true;
				//if thumbnail, load only one frame
				if(getViewMode() == ViewMode.Thumbnail) {
					PDFReader pdfReader = new PDFReader(new File(imgFiles.get(i))/*read dicom*/);
					ImagePlus pdfStack = pdfReader.pdf2ImageStack();
					loadSlideGlassFromPDF(pdfStack, 0, header, backend);
					pdfReader.close();
				}else {
					loadSlideGlassFromPDF(imgFiles.get(i), header, backend);
				}
				//PDF is one series, break here.
				break;
			}
			// images
			int size = header.getInt(Tag.Number​Of​Frames, -1);
			/*
			 * isMultiFrame
			 * 1.General image types do not have NumberOfFrames tag.(means -1).
			 * 2.When image acquiring in 3d sequence on MRI, number of frame is 1 (of each image).
			 */
			isMultiframe = size > 1;
			//single frame
			if(!isMultiframe) {
				loadSlideGlassFromSimpleDicom(imgFiles.get(i), backend, tsUID);
			}else {
				/*
				 * multiframe to one series.
				 */
				loadSlideGlassFromMultiFrame(imgFiles.get(i), header, backend);
				/*
				 * if multiframe, load as only one file.
				 */
				break;
			}
			reader = null;
		}
	}
	
	private void loadSlideGlassFromSimpleDicom(String path2dcm, DICOMBackend backend, UID tsUID) {
		ExecutorService executor = Executors.newFixedThreadPool(Utils.availableProcessors());
		List<Future<SlideGlass>> futures = new ArrayList<>();
	    Callable<SlideGlass> task = () -> {
	    	 DicomImage dcmimg = DicomImage.newDicomImage(path2dcm, backend);
	 		if(Codec.isCompressed(tsUID.uid())) {
	 			Decompressor.newInstance(dcmimg).decompress();
	 		}
	 		return new SlideGlass(this, dcmimg);
	    };
	    futures.add(executor.submit(task));
	    for (Future<SlideGlass> future : futures) {
            try {
				slides.put(slides.size(), future.get());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
        }
		executor.shutdown();
		try {
			executor.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private void loadSlideGlassFromMultiFrame(String path2dcm, DicomObject header, DICOMBackend backend) {

		ExecutorService executor = Executors.newFixedThreadPool(Utils.availableProcessors());
		List<Future<SlideGlass>> futures = new ArrayList<>();

		DicomReader video_reader_ = DicomReader.newDicomReader(backend);
		video_reader_.read(path2dcm, true/* with bulk */);
		DicomImage videoDcm = DicomImage.newDicomImage(video_reader_.getCore(), video_reader_.checkTSUID());
		int w = header.getInt(Tag.Columns, 0);
		int h = header.getInt(Tag.Rows, 0);
		int c = header.getInt(Tag.Samples​Per​Pixel, 1);
		int bits = header.getInt(Tag.Bits​Allocated, 8);
		int size = header.getInt(Tag.Number​Of​Frames, -1);
		/*
		 * to address jpeg multiframe
		 */
		if (Codec.isCompressed(video_reader_.checkTSUID())) {
			Decompressor.newInstance(videoDcm).decompress();
		}
		for (int j = 0; j < size; j++) {
			final int k = j;
			Callable<SlideGlass> task = () -> {
				DicomObject instHeader = DicomObject.newDicomObject(header, backend);
				instHeader.setInt(Tag.Instance​Number, VR.IS, (k + 1));
				DicomImage frame = DicomImage.newDicomImage(instHeader, UID.ImplicitVRLittleEndian, backend);
				frame.setPixelData(k, w, h, c, bits, videoDcm.getImageProcessor(k).getPixels());
				return new SlideGlass(this, frame);
			};
			futures.add(executor.submit(task));
		}
		AtomicInteger counter = new AtomicInteger(0);
		for (Future<SlideGlass> future : futures) {
			try {
				slides.put(counter.addAndGet(1), future.get());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		executor.shutdown();
		try {
			executor.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		video_reader_ = null;
	}
	
	private void loadSlideGlassFromPDF(String path2dcm, DicomObject header, DICOMBackend backend) {
		PDFReader pdfReader = new PDFReader(new File(path2dcm)/*read dicom*/);
		ImagePlus pdfStack = pdfReader.pdf2ImageStack();
		isMultiframe = true;//always treats multi
		isPDF = true;
		
		ExecutorService executor = Executors.newFixedThreadPool(Utils.availableProcessors());
       List<Future<SlideGlass>> futures = new ArrayList<>();
		
		//if thumbnail, load only one frame
		if(getViewMode() == ViewMode.Thumbnail) {
			ImageProcessor instIp = pdfStack.getStack().getProcessor(1);
			DicomObject instHeader = DicomObject.newDicomObject(header, backend);
			instHeader.setInt(Tag.Columns, VR.US, instIp.getWidth());
			instHeader.setInt(Tag.Rows, VR.US, instIp.getHeight());
			instHeader.setInt(Tag.Samples​Per​Pixel, VR.US, 3);
			instHeader.setInt(Tag.Bits​Allocated, VR.US, 8);
			instHeader.setInt(Tag.Instance​Number, VR.IS, (1));
			DicomImage img = DicomImage.newDicomImage(instHeader, UID.ImplicitVRLittleEndian, backend);
			img.setPixelData(0, pdfStack.getWidth(), pdfStack.getHeight(), 3, 8, instIp.getPixels());
			SlideGlass sg = new SlideGlass(this, img);
			slides.put(0, sg);
		}else {
			for (int j = 0; j < pdfStack.getNSlices(); j++) {
				final int k = j;
				Callable<SlideGlass> task = () -> {
					return loadSlideGlassFromPDF(pdfStack, k, header, backend);
				};
				futures.add(executor.submit(task));
			}
			AtomicInteger counter = new AtomicInteger(0);
			for (Future<SlideGlass> future : futures) {
	            try {
					slides.put(counter.addAndGet(1), future.get());
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
	        }
			executor.shutdown();
			try {
				executor.awaitTermination(1, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		pdfReader.close();
	}
	
	private SlideGlass loadSlideGlassFromPDF(ImagePlus pdf, int pos/*0 to n-1*/, DicomObject header, DICOMBackend backend) {
		ImageProcessor instIp = pdf.getStack().getProcessor(pos + 1);
		DicomObject instHeader = DicomObject.newDicomObject(header, backend);
		instHeader.setInt(Tag.Columns, VR.US, instIp.getWidth());
		instHeader.setInt(Tag.Rows, VR.US, instIp.getHeight());
		instHeader.setInt(Tag.Samples​Per​Pixel, VR.US, 3);
		instHeader.setInt(Tag.Bits​Allocated, VR.US, 8);
		instHeader.setInt(Tag.Instance​Number, VR.IS, (pos + 1));
		DicomImage img = DicomImage.newDicomImage(instHeader, UID.ImplicitVRLittleEndian, backend);
		img.setPixelData(pos, pdf.getWidth(), pdf.getHeight(), 3, 8, instIp.getPixels());
		return new SlideGlass(this, img);
	}
	

	/**
	 * This method is used to a dependence single series view or MPR.
	 */
	private void constructSlideGlassesFromImagePlus(ImagePlus images) {
		if (images == null || images.getStackSize() < 1) {
			throw new IllegalArgumentException("Images is null or empty, Praparat::constructSeriesGlassesAsLayerUsingImagePlus");
		}
		// init
		viewPanel.removeAll();
		slides = new HashMap<Integer, SlideGlass>();
		boolean secondaryUse = false;
		String sopClassUID = GDicomTools.getTag(images, "0008,0016");
		String instUID = GDicomTools.getTag(images, "0008,0018");
		if(sopClassUID == null || instUID == null) {
			secondaryUse = true;
		}
		HashMap<Integer, DicomImage> ds = ImageUtils.imagePlusToDcm(images, secondaryUse/*treat as secondary*/);
		for (int i = 0; i < ds.size(); i++) {
			SlideGlass sg = new SlideGlass(this, ds.get(i));
			slides.put(i, sg);
		}
	}

	private void constructSlideGlassesFromPraparat(Praparat p) {
		if(p == null) {
			return;
		}
		HashMap<Integer, SlideGlass> slides = p.getAllSlides();
		if (slides == null || slides.size() < 1) {
			System.out.println("Slides have no images...");
			return;
		}
		// init
		removeSlide(currentSlice);
		this.slides = new HashMap<Integer, SlideGlass>();
		
		Set<Integer> keys = slides.keySet();
		for(Integer k : keys) {
			//init slide from another slides to set this praparat.
			SlideGlass sg = slides.get(k);
			SlideGlass newsg = new SlideGlass(this, sg.getDicomImage());
			this.slides.put(k, newsg);
		}
		if(Utils.isDebug) {
			Log.logger.fine(slides.size()+" images loaded.");
		}
	}
	
	public ImagePlus cropRectangle(boolean show) {
		SlideGlass sg = getCurrentSlide();
		CanvasGlass cg = (CanvasGlass)sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
		RoiObj roi = cg.findCurrentRoi();
		if(roi == null) {
			return null;
		}
		int res = JOptionPane.showConfirmDialog(this, "Process all slides in this series ?", "Crop series ?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		final ImagePlus crop;
		if(res != JOptionPane.YES_OPTION) {
			ImagePlus imp = getImagePlus();
			imp.setSlice(getCurrentSlidePos()+1);
			crop = new ImageProcessing().cropRect(imp, roi, false);
		}else {
			ImagePlus imp = getImagePlus();
			crop = new ImageProcessing().cropRect(imp, roi, true);
		}
		if(crop == null || crop.getNSlices() < 1) {
			return null;
		}
		if(show) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					Praparat prap = new Praparat(crop, getStudyColor(), mode);
					new SeriesWindow(prap);
				}
			});
		}
		return crop;
	}

	public void doFilmGridLayout(Integer col) {
		if(col == null) {
			col = minimumGridCol;//5
		}
		if(this.mode != ViewMode.FilmGrid && this.mode != ViewMode.Normal) {
			logger.warning("You are not able to show gridView in this mode::"+this.mode);
			gridViewOn(false);
			return;
		}
		gridViewOn(true);
		setCursor(new Cursor(Cursor.WAIT_CURSOR));
		setFilmGridColumns(col);
		viewPanel.removeAll();
		gridScrollPane = new SlideGlassGrid(this, col, false/*GridLayer*/);
		viewPanel.add(gridScrollPane,0);
		setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
	}

	public void doSingleGridLayout() {
		gridViewOn(false);
		adjustSlideGlassSize(getViewPanelWidth(), getViewPanelHeight());
		setImagePositionUsingSlider(currentSlice);
	}
	
	public HashMap<Integer, SlideGlass> getAllSlides() {
		if (currentSlice == -1) {
			return null;
		}
		if(slides != null && slides.size() < 1) {
			return null;
		}
		return slides;
	}

	public PraparatViewControlPanel getController() {
		return pvcp;
	}
	
	public SlideGlass getCurrentSlide() {
		if (currentSlice == -1) {
			return null;
		}
		return slides.get(currentSlice);
	}
	
	public int getCurrentSlidePos() {
		return currentSlice;
	}

	/**
	 * check Viewer2D selecting tool.
	 * @return
	 */
	public int getCurrentViewerToolType() {
		return Viewer2DScreen.getInstance().getCurrentToolType();
	}
	
	public Eyepiece getEyepiece() {
		return prapManager;
	}

	public ArrayList<String> getImageFileLocations() {
		return this.pathToImages;
	}

	/**
	 * return slides as imageplus.
	 * @return imageplus
	 */
	public ImagePlus getImagePlus() {
		if (slides == null || slides.size() < 1) {
			return null;
		}
		ImagePlus org = getCurrentSlide().getOriginalImage();
		Calibration cal = org.getCalibration();
		ImageStack stack = new ImageStack();
		String info = "";
		int iter = 0;
		for (int arrayOrder : slides.keySet()) {
			SlideGlass sg = slides.get(arrayOrder);
			/**
			 * 20250610
			 * should do test
			 */
//			ImagePlus imp = sg.convertToImagePlus();//memory leak
			ImagePlus imp = sg.getOriginalImage();
			/*
			 * calibration is gone here, so finally add it.
			 */
			ImageProcessor ip = imp.getProcessor();
			if (ip.getNChannels() == 3 && ip instanceof ColorProcessor) {
				ip.snapshot();// keep original pixels
			}
			/*
			 * In this case, allways return only one slice.
			 * It case use getInfoProperty().
			 */
			String header = imp.getInfoProperty();
			/*
			 * if header has "\n" in head (at index 0), 
			 * DicomTools.getTag() return null.
			 */
//			String header = imp.getStack().getSliceLabel(1);//why ? automatically added "\n" in head.
			if(iter == 0){
				info = header;
				iter++;
			}
			stack.addSlice(header, ip);
		}
		ImagePlus stacked = new ImagePlus("stack-series", stack);
		/*
		 * if ImagePlus has only one slice, header is updated by setProp("Info", hdr).
		 */
		if(stacked.getNSlices() == 1) {
			stacked.setProp("Info", info);
		}
		stacked.setCalibration(cal);
		return stacked;
	}
	
	public int getImageScreenSizeX() {
		return getViewPanelWidth();
	}
	
	public int getImageScreenSizeY() {
		return getViewPanelHeight();
	}
	
	public HashMap<String,Object> getInfoSet() {
		HashMap<String,Object> infoset = new HashMap<>();
		infoset.put(KEY_PadID, patID);
		infoset.put(KEY_StudyUID, studyUID);
		infoset.put(KEY_SeriesUID, seriesUID);
		infoset.put(KEY_SopUIDs, sopUIDs);//string[]
		return infoset;
	}

	public int getNumberOfImages() {
		if (isMultiframe) {
			return slides.size();
		} else {
			return slides.size();
		}
	}
	
	public ReferenceLineMPR getReferenceLineMPR() {
		return this.refLineMPR;
	}
	
	/**
	 * PraparatViewPane(JLayeredPane) Height
	 * @return
	 */
	int getViewPanelHeight() {
		return viewPanel.getHeight();
	}

	int getViewPanelWidth() {
		return viewPanel.getWidth();
	}
	
	JPanel getViewPanel() {
		return viewPanel;
	}

	public int getSlidePosition(SlideGlass slide) {
		HashMap<Integer, SlideGlass> slides = getAllSlides();
		if(slides == null) {
			return -1;
		}
		for(int p : slides.keySet()) {
			SlideGlass sg = slides.get(p);
			if(slide == sg) {
				return p;
			}
		}
		return -1;
	}
	
//	void saveSeriesSettings() {
//		winMin = imagePane.currentMin;
//		winMax = imagePane.currentMax;
//		//add ...
//	}

	public int getSlidePosition(String sopUID) {
		HashMap<Integer, SlideGlass> slides = getAllSlides();
		if(slides == null) {
			return -1;
		}
		for(int p : slides.keySet()) {
			SlideGlass sg = slides.get(p);
			String sopUID_ = sg.getSOPInstanceUID();
			if(sopUID.equals(sopUID_)) {
				return p;
			}
		}
		return -1;
	}

	public Color getStudyColor() {
		return this.studyColor;
	}

	/**
	 * without frameOfReferenceUID
	 * @return
	 */
	public Object[] getUIDs() {
		final Object[] uids = new Object[5];
		uids[0] = patID;
		uids[1] = studyUID;
		uids[2] = seriesUID;
		/*
		 * Basically, the order of the sopUID array is not guaranteed. However, we do
		 * not want to change the order unnecessarily if at all possible. For example,
		 * in equals(), the order may change because of sorting. Here, we pass a copy and
		 * make no changes to the original.
		 */
		uids[3] = sopUIDs != null ? sopUIDs.clone():null;// String[],
		uids[4] = frameOfReferenceUID;
		return uids;
	}
	
	public ArrayList<RoiObj> getRoiAt(String sopUID){
		for(int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			if(sg.getSOPInstanceUID().equals(sopUID)) {
				CanvasGlass cg = (CanvasGlass)sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
				return cg.getRoiSet();
			}
		}
		return null;
	}
	
	public ArrayList<RoiObj> getRois(){
		ArrayList<RoiObj> rois = new ArrayList<>();
		for(int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			CanvasGlass cg = (CanvasGlass)sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			ArrayList<RoiObj> roisOnSlide = cg.getRoiSet();
			if(roisOnSlide != null && roisOnSlide.size() > 0) {
				rois.addAll(roisOnSlide);
			}
		}
		return rois;
	}
	
	public ArrayList<RoiObj> getSelectedRois(){
		ArrayList<RoiObj> rois = new ArrayList<>();
		for(int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			CanvasGlass cg = (CanvasGlass)sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			ArrayList<RoiObj> roisOnSlide = cg.getRoiSet();
			if(roisOnSlide != null && roisOnSlide.size() > 0) {
				for(RoiObj r : roisOnSlide) {
					if(r.isSelected()) {
						rois.add(r);
					}
				}
			}
		}
		return rois;
	}
	
	private String concatenationOfUIDStrings() {
		Object[] uids = getUIDs();
		String str = "";
		for(Object u : uids) {
			if(u == null) {
				continue;
			}
			if(u instanceof String) {
				str += u;
			}else if(u instanceof String[]) {
				List<String> sopUIDs = Arrays.asList((String[])u);
				if(sopUIDs == null || sopUIDs.isEmpty()) {
					continue;
				}
				if(sopUIDs.contains(null)) {
					continue;
				}
				Collections.sort(sopUIDs);
				for (String s : sopUIDs) {
					str += s;
				}
			}
		}
		return str.length()==0 ? null : str;
	}
	
	public ViewMode getViewMode() {
		return this.mode;
	}
	
	public int getViewer2DToolType() {
		Window win = WindowManager.getWindow(ConfigInfo.D2ViewerWindow.toString());
		if(win != null) {
			Viewer2DScreen viewer2d = (Viewer2DScreen)win;
			return viewer2d.getCurrentToolType();
		}
		return -1;//means "None"
	}
	
	public void gridViewOn(boolean showFilmGrid) {
		if(this.mode == ViewMode.FilmGrid) {
			this.showGridViewOn = true;
			return;
		}
		if(this.mode == ViewMode.Normal) {
			if(showFilmGrid == false) {
				if(gridScrollPane != null) {
					Component[] cons = viewPanel.getComponents();
					for(Component c : cons) {
						if(c instanceof SlideGlassGrid) {
							viewPanel.remove(c);
							viewPanel.revalidate();
							break;
						}
					}
				}
				gridScrollPane = null;
			}
			this.showGridViewOn = showFilmGrid;
		}else {//single grid and thumbnail
			this.showGridViewOn = false;
			gridScrollPane = null;
		}
	}
	
	private void init() {
		if(this.mode == null) {
			throw new NullPointerException();
		}
		setOpaque(true);//visible
		prevW = getWidth();
		prevH = getHeight();
		//init slides
		slides = new HashMap<Integer, SlideGlass>();
		setLayout(new BorderLayout());
		setBorder(BorderMaker.make(this, false));
		pvcp = new PraparatViewControlPanel(this);// pixelInfoLabel
		slider = new CineSlider(this);
		viewPanel = new JPanel();
		viewPanel.setName("ViewPanel");
		viewPanel.setBackground(Color.BLACK);//debug purpose
		viewPanel.setLayout(new GridLayout(1, 1));
		viewPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				updateViewPanel();
			}
		});
		add(viewPanel, BorderLayout.CENTER);
		
		if(mode == ViewMode.Normal) {
			add(pvcp, BorderLayout.NORTH);
			add(slider, BorderLayout.SOUTH);
			setFocusable(true);
			setRequestFocusEnabled(true);
		}
		
		if(mode == ViewMode.SingleGrid) {
			add(pvcp, BorderLayout.NORTH);
			add(slider, BorderLayout.SOUTH);
			setFocusable(true);
			setRequestFocusEnabled(true);
			pvcp.getFilmGridBtn().setEnabled(false);
		}
		
		if(mode == ViewMode.FilmGrid){
			add(pvcp, BorderLayout.NORTH);
			/*No slider*/
			setFocusable(true);
			setRequestFocusEnabled(true);// fail safe?
			/*see also setTextVisible(). This is called when after preparedImages*/
			pvcp.enableShowInfo(false);
			pvcp.enableProcessSeries(false);
		}
		
		if(mode == ViewMode.Thumbnail) {
			/*No controller and slider*/
			/*
			 * Do NOT USE setPreferedSize.
			 */
			setViewPanelSize(ThumbnailSize, ThumbnailSize);
			setFocusable(true);
			setRequestFocusEnabled(true);
		}
		
		if(mode == ViewMode.MPR){
			add(pvcp, BorderLayout.NORTH);
			add(slider, BorderLayout.SOUTH);
			setFocusable(true);
			setRequestFocusEnabled(true);// fail safe?
			/*filmGrid is denied*/
			pvcp.getFilmGridBtn().setEnabled(false);
		}
	}
	
	public void initSlideGlassSize(int w , int h) {
		if(slides == null || slides.size() == 0) {
			return;
		}
		SlideGlass target = slides.get(currentSlice);
		target.setSize(w,h);
		target.repaint();
		//set origin all slides
		for (Integer key : slides.keySet()) {
			SlideGlass sl = slides.get(key);
			if(sl == target) continue;
			sl.setSize(w,h);
			if (target != null && !target.panningFlag && !sl.panningFlag) {
				sl.imageSpecimen.originX = target.imageSpecimen.originX;
				sl.imageSpecimen.originY = target.imageSpecimen.originY;
				sl.repaint();
			}
		}
	}

	public boolean isFocusGained() {
		return focusGained;
	}
	
	public boolean isMultiFrame() {
		return this.isMultiframe;
	}
	
	public boolean isPDF() {
		return this.isPDF;
	}

	/**
	 * Must call from pvcp, to avoid series and film grid state conflicts. 
	 */
	public boolean isProcessSeries() {
		return pvcp.processSeries();
	}
	
	public boolean isSelected() {
		return selected;
	}
	
	public boolean isShowCrossLineMode() {
		return this.crossLineCursorMode;
	}

	public boolean isShowGridViewOn() {
		return this.showGridViewOn;
	}
	
	public boolean isShowing2DViewerOn() {
		return prapManager != null;
	}

	public void loadRoiToCurrentSlideGlass() {
		SlideGlass sg = getCurrentSlide();
		sg.loadRoiFromDB();
	}
	
	public void addRoi(int slidePos, RoiObj r) {
		if(slidePos < 0 || slidePos >= slides.size()) {
			System.out.println("Praparat: this slide position invalid...cannot addRoi().");
			return;
		}
		SlideGlass sg = slides.get(slidePos);
		sg.addRoi(r);
	}

	public void prepareSlideGlasses(Praparat p) {
		if(p == null) {
			logger.log(Level.SEVERE, "Can not load images from this Praparat.");
			return;
		}
		HashMap<String, Object> info = p.getInfoSet();
		String patID = (String)info.get("PatientID");
		String studyUID = (String)info.get("StudyInstanceUID");
		String seriesUID = (String)info.get("SeriesInstanceUID");
		String[] sopUIDs = (String[])info.get("SOPInstanceUIDs");
		ArrayList<String> pathToImages = p.getImageFileLocations();
		
		if(pathToImages == null || pathToImages.size()==0) {
			logger.warning("prap needs path to images..., return.");
			return;
		}
		viewPanel.removeAll();
		setInfo(patID, studyUID, seriesUID, sopUIDs, pathToImages);
		constructSlideGlassesFromPraparat(p);
		prevSlice = -1;// IMPORTANT
		currentSlice = 0;
		if(slider != null) {
			slider.initContext();
		}
		if(Utils.isDebug) {
			System.out.println(slides.size()+" images loaded.");
		}
	}

	public void prepareSlideGlasses(String patID, String studyUID, String seriesUID, ArrayList<String> sopUIDs, ArrayList<String> pathToImages) {
		String[] sopUids = sopUIDs.toArray(new String[sopUIDs.size()]);
		prepareSlideGlasses(patID, studyUID, seriesUID, sopUids, pathToImages);
	}
	
	public void prepareSlideGlasses(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
		ArrayList<String> pathToImages = null;
		DatabaseHandler db = DatabaseHandler.getInstance();//.getDatabase();
		if (sopUIDs == null || sopUIDs.length < 1) {
			// load all instances in series
			pathToImages = db.getFileLocationsSeriesLevel(studyUID, seriesUID);
		} else {
			// load particular instances
			pathToImages = new ArrayList<String>();
			for (String sopUID : sopUIDs) {
				String p2img = db.getFileLocation(studyUID, seriesUID, sopUID);
				pathToImages.add(p2img);
			}
		}
		if (pathToImages == null || pathToImages.size()<1) {
			logger.warning("Cannot find images for loading...");
			return;
		}
		prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs, pathToImages);
	}
	
	public void prepareSlideGlasses(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToImages) {
		if(pathToImages == null || pathToImages.size()==0) {
			System.out.println("prap needs path to images..., return.");
			return;
		}
		viewPanel.removeAll();
		
		setInfo(patID, studyUID, seriesUID, sopUIDs, pathToImages);
		constructSlideGlassesFromDicom(pathToImages);
		prevSlice = -1;// IMPORTANT
		currentSlice = 0;
		if(slider != null) {
			slider.initContext();
		}
		if(Utils.isDebug) {
			System.out.println(slides.size()+" images loaded.");
		}
	}
		
	public void prepareSlideGlassesUsingImagePlus(ImagePlus images) {
		if(images == null || images.getStackSize()==0) {
			if(Utils.isDebug) System.out.println("praparat needs images..., return.");
			return;
		}
		prevSlice = -1;
		currentSlice = 0;
		updateInfoLabel(-1,-1,"-1",new double[] {-1,-1},-1,-1);
		constructSlideGlassesFromImagePlus(images);
		if(slider != null) {
			slider.initContext();
		}
		if(Utils.isDebug) {
			Log.logger.fine(slides.size()+" images loaded.");
		}
	}
	
	public void prepareSlideGlassesFromDcmObj(ArrayList<String> paths) {
		if(paths == null || paths.size()==0) {
			if(Utils.isDebug) System.out.println("praparat needs images..., return.");
			return;
		}
		prevSlice = -1;
		currentSlice = 0;
		updateInfoLabel(-1,-1,"-1",new double[] {-1,-1},-1,-1);
		constructSlideGlassesFromDicom(paths);
		if(slider != null) {
			slider.initContext();
		}
		if(Utils.isDebug) {
			System.out.println(slides.size()+" images loaded.");
		}
	}
	
	public ImagePlus processCut(boolean show) {
		SlideGlass sg = getCurrentSlide();
		CanvasGlass cg = (CanvasGlass)sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
		RoiObj currentRoi = cg.findCurrentRoi();
		if(currentRoi == null ) {
			Log.logger.info("Current ROI is null...");
			return null;
		}
		final ImagePlus cut;
		int res = JOptionPane.showConfirmDialog(this, "Process all slide in this series ?", "Cut...", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if(res != JOptionPane.YES_OPTION) {
			ImagePlus imp2 = getImagePlus();
			imp2.setSlice(getCurrentSlidePos()+1);
			cut = new ImageProcessing().cut(imp2, currentRoi, false);
		}else {
			ImagePlus imp2 = getImagePlus();
			cut = new ImageProcessing().cut(imp2, currentRoi, true);
		}
		if(show) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					Praparat prap = new Praparat(cut, getStudyColor(), mode);
					new SeriesWindow(prap);
				}
			});
		}
		return cut;
	}

	public void processFlipHF() {
		if(!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			sg.flipHF();
		}else {
			for(Integer key:slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.flipHF();
			}
		}
	}

	public void processFlipLR() {
		if(!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			sg.flipLR();
		}else {
			for(Integer key:slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.flipLR();
			}
		}
	}

	public void processInvertImages() {
		if(!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			sg.invert();
		}else {
			for(Integer key:slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.invert();
			}
		}
	}

	public void reloadSlideGlasses(ImagePlus imp) {
		if (imp == null) { return; }
		prepareSlideGlassesUsingImagePlus(imp);
		if(!isShowGridViewOn()) {
			doSingleGridLayout();
		}else {
			doFilmGridLayout(filmGridColumns);
		}
	}
	
	/**
	 * refresh and load new slideglasses from DB.
	 */
	public void reloadSlideGlasses(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
		// do not allow sopUIDs NULL.
		if (sopUIDs == null) { return; }
		// remove current series image and get new series info
		prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
		if(!isShowGridViewOn()) {
			doSingleGridLayout();
		}else {
			doFilmGridLayout(filmGridColumns);
		}
	}

	public void removeSlide(int target) {
		SlideGlass s = this.slides.get(target);
		if (s != null) {
			removeSlide(s);
		}
	}

	private void removeSlide(SlideGlass slide) {
		if (isShowGridViewOn()) {
			//do nothing
		} else {
			if (slide != null) {
				viewPanel.removeAll();
			}
		}
		updateInfoLabel(-1,-1,"-1", new double[] {-1,-1},-1,-1);
	}

	public void resetView() {
		
		if (getImageFileLocations() == null || getImageFileLocations().size()==0) {
			ImagePlus imp = getImagePlus();
			pvcp.setProcessSeries(true);//to show all images after reset
			updateInfoLabel(-1,-1,"-1",new double[] {-1,-1},-1,-1);
			reloadSlideGlasses(imp);
			return;
		}
		
		if(mode == ViewMode.Normal || mode == ViewMode.SingleGrid) {
			if (isShowGridViewOn()) {
				showGridViewOn = false;
			}
			pvcp.setProcessSeries(true);//to show all images after reset
			updateInfoLabel(-1,-1,"-1",new double[] {-1,-1},-1,-1);
			// reload slides
			//prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
			//setTextVisible(pvcp.isShowInfo());
			//setAnnotationVisible(pvcp.isShowRoi());
			//doSingleGridLayout();
			for(int i : slides.keySet()) {
				slides.get(i).reset();
			}
			return;
		}
		
		if(mode == ViewMode.FilmGrid) {
			pvcp.setProcessSeries(true);//to show all images after reset
			updateInfoLabel(-1,-1,"-1",new double[] {-1,-1},-1,-1);
			// reload slides
//			prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
//			setTextVisible(false);
//			setAnnotationVisible(false);
//			doFilmGridLayout(filmGridColumns);
			for(int i : slides.keySet()) {
				slides.get(i).reset();
			}
			return;
		}
		
		if(mode == ViewMode.Thumbnail) {
			//do nothing
			return;
		}
		
	}
	
	public void resetWindow() {
		if(pvcp.processSeries()) {
			for(int i : slides.keySet()) {
				SlideGlass sg = slides.get(i);
				sg.autoWindowing();
			}
		}else {
			SlideGlass sg = getCurrentSlide();
			sg.autoWindowing();
		}
	}

	public void setAndShowPixelValue(int imageX, int imageY) {
		SlideGlass currentSlide = getCurrentSlide();
		double[] scaleXY = currentSlide.getScaleFactor();
		double mag = currentSlide.getMagnification();
		double rotate = currentSlide.getRotateAngle();
		Object[] val = getCurrentSlide().getPixelValueFromOriginal(imageX, imageY);
		if(val == null) {
			updateInfoLabel(imageX, imageY, null+"("+null+")",scaleXY, mag,rotate);
			return;
		}
		if(!currentSlide.isRGB()) {
			Double[] pixelRawAndCalibrated = (Double[])getCurrentSlide().getPixelValueFromOriginal(imageX, imageY);
			double raw_v = pixelRawAndCalibrated[0];
			double calibrated_v = pixelRawAndCalibrated[1];
			updateInfoLabel(imageX, imageY, calibrated_v+"("+raw_v+")",scaleXY, mag,rotate);
		}else {
			String[] rgbAndColor = (String[])getCurrentSlide().getPixelValueFromOriginal(imageX, imageY);
			String r = rgbAndColor[0];
			String g = rgbAndColor[1];
			String b = rgbAndColor[2];
//			String color = rgbAndColor[3];//java.awt.Color[r,g,b]
//			updateInfoLabel(X, Y, r+","+g+","+b+" "+"("+color+")", scale, mag, rotate);
			updateInfoLabel(imageX, imageY, "(r,g,b)"+r+","+g+","+b, scaleXY, mag, rotate);
		}
	}

	/**
	 * 
	 * do after load slides
	 * @param v
	 */
	public void setAnnotationVisible(boolean v) {
		HashMap<Integer, SlideGlass> slides = getAllSlides();
		if(slides == null) {
			return;
		}
		if (isShowGridViewOn()) {
			if(isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					s.setAnnotationVisible(v);
				}
			}else {
				/*
				 * change state of only selected.
				 */
				List<SlideGlass> selected = getSelectedGlasses();
				for (SlideGlass s : selected) {
					s.setAnnotationVisible(v);
				}
			}
		} else {
			if(isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					s.setAnnotationVisible(v);
				}
			}else {
				/*
				 * change state of current showing glass.
				 */
				getCurrentSlide().setAnnotationVisible(v);
			}
		}
	}
	
	public void setEyepiece(Eyepiece eye) {
		this.prapManager = eye;
	}
	
	public List<SlideGlass> getSelectedGlasses() {
		List<SlideGlass> selected = Collections.synchronizedList(new ArrayList<>());
		for(int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			if(sg.isSelected()) {
				selected.add(sg);
			}
		}
		return selected;
	}
	
	public int getFilmGridColumns() {
		return this.filmGridColumns;
	}
	
	public void setFilmGridColumns(int num) {
		this.filmGridColumns = num;
	}
	
	/**
	 * show border to prap
	 * @param focusGained
	 */
	public void setFocusGained(boolean focusGained) {
		this.focusGained = focusGained;
		if(getViewMode()!=ViewMode.SingleGrid && getViewMode()!=ViewMode.FilmGrid) {
			showBorder(focusGained);
		}
	}
	
	private void setImageFileLocations(ArrayList<String> pathToImages) {
		this.pathToImages = pathToImages;
	}
	
	/**
	 * If you want to change slide position, use setImagePositionUsingSlider instead.
	 * @param sliceIndex:number of slice index, 0 to n-1
	 */
	void setImagePosition(int sliceIndex) {
		if (slides == null) { //do not include pathToImages
			return;
		}
		if(isShowGridViewOn()) {
			if(prevSlice == sliceIndex) {
				return;
			}
			prevSlice = currentSlice;
			currentSlice = sliceIndex;
			return;
		}
		if(currentSlice != sliceIndex) {
			prevSlice = currentSlice;
			currentSlice = sliceIndex;
		}
		SlideGlass currentGlass = this.slides.get(currentSlice);
		final int top = 0;
		viewPanel.removeAll();
		viewPanel.add(currentGlass, top);
		currentGlass.requestFocus();
		currentGlass.setFocusGained(true);//for key listener
		currentGlass.revalidate();//check layout
		currentGlass.repaint();//repaint all layered glasses(image/roi/text).
	}
	
	public void setImagePositionTo(SlideGlass sg) {
		Set<Integer> keys = slides.keySet();
		for(Integer key : keys) {
			SlideGlass slide = slides.get(key);
			if(sg == slide) {
				setImagePosition(key);
				break;
			}
		}
	}
	
	/**
	 * Specify slideglass position on prap.
	 * pos: 0 to n-1
	 */
	public void setImagePositionUsingSlider(int pos) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				/*
				 * Slider does not fire state change when the same index as the current index is
				 * entered.
				 */
				if(slider.getCurrentSliceIndex()/*1 to N*/ == (pos+1) /*0 to N-1*/) {
					setImagePosition(pos);
					callBackLocalizer();
				}else {
					slider.setSlice(pos);
				}
			}
		});
	}
	
	private void setInfo(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToImages) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		String frameOfRefUID = null;
		if(db != null) {
			frameOfRefUID = db.getParticularInfoFromImage("FrameOfReferenceUID", patID, studyUID, seriesUID, sopUIDs[0]);
		}else {
			frameOfRefUID = DicomUtilities.getFrameOfReferenceUID(pathToImages.get(0));
		}
		setInfo(patID, studyUID, seriesUID, sopUIDs, frameOfRefUID, pathToImages);
	}
	
	private void setInfo(String patID, String studyUID, String seriesUID, String[] sopUIDs, String refUID, ArrayList<String> pathToImages) {
		this.patID = patID;
		this.studyUID = studyUID;
		this.seriesUID = seriesUID;
		this.sopUIDs = sopUIDs;
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db != null && (refUID == null || refUID.length()==0)) {
			this.frameOfReferenceUID = db.getParticularInfoFromImage("FrameOfReferenceUID", patID, studyUID, seriesUID, sopUIDs[0]);
		}else {
			this.frameOfReferenceUID = refUID;
		}
		setImageFileLocations(pathToImages);
	}
	
	public void setLUT(LUT lut) {
		if(!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			sg.setLUT(lut);
		}else {
			for(Integer key:slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.setLUT(lut);
			}
		}
	}
		
	public void setNextSlice() {
		setImagePositionUsingSlider(currentSlice+1);
	}
	
	public void setPreviousSlice() {
		setImagePositionUsingSlider(currentSlice-1);
	}
		
	public void setReferenceLineMPR(ReferenceLineMPR refLine) {
		this.refLineMPR = refLine;
	}
	
	/**
	 * Praparat selection state
	 * True if any one of the SlideGlasses is in the selected state.
	 */
	public void setSelectionState(boolean select) {
		if(mode == ViewMode.Thumbnail) {
			this.selected = select;
			showBorder(false/*focusGained*/);
			return;
		}
		this.selected = false;
		for(int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			if(sg.isSelected()) {
				this.selected = true;
				break;
			}
		}
		showBorder(isFocusGained());
	}
	
	public void setShowCrossLineMode(boolean crossMode) {
		this.crossLineCursorMode = crossMode;
	}

	/**
	 * for thumbnail
	 * @param w
	 * @param h
	 */
	private void setViewPanelSize(int w, int h) {
		viewPanel.setSize(w, h);
		viewPanel.setPreferredSize(new Dimension(w, h));
		viewPanel.setBounds(0, 0, w, h);
	}

	public void setStudyColor(Color color) {
		if(color != null) {
			this.studyColor = color;
		}
	}

	/**
	 * do after load slides
	 * @param v
	 */
	public void setTextVisible(boolean v) {
		HashMap<Integer, SlideGlass> slides = getAllSlides();
		if(slides == null) {
			return;
		}
		if (isShowGridViewOn()) {
			if(isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					s.setTextVisible(v);
				}
			}else {
				List<SlideGlass> selected = getSelectedGlasses();
				for (SlideGlass s : selected) {
					s.setTextVisible(v);
				}
			}
		} else {
			if(isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					s.setTextVisible(v);
				}
			}else {
				getCurrentSlide().setTextVisible(v);
			}
		}
	}

	public void showBorder(boolean show) {
		Border b = BorderMaker.make(this, isFocusGained());
		setBorder(b);
	}
	
	public void showFirstImage() {
		slider.initContext();//slider setValue -1.
		// position range is 0 to n-1
		prevSlice = -1;
		currentSlice = 0;
		setImagePosition(currentSlice);
	}
	
	private void updateInfoLabel(int x, int y, String value, double[] scaleXY, double mag, double rotate) {
		if(getViewMode() != ViewMode.Thumbnail) {
			this.pvcp.setText2InfoLabel(x, y, value, scaleXY, mag, rotate);
		}
	}
	
	/**
	 * See, viewPanel componentListener.
	 */
	public void updateViewPanel() {
		int currentW = getViewPanelWidth();
		int currentH = getViewPanelHeight();
		if(currentW == prevViewPanelW && currentH == prevViewPanelH) {
			return;
		}
		if(mode == ViewMode.Thumbnail) {
			//do noting
		}else if(mode == ViewMode.FilmGrid) {
			Component con = viewPanel.getComponent(0);
			if(con instanceof SlideGlassGrid) {
				SlideGlassGrid sgg = (SlideGlassGrid)con;
				sgg.update(viewPanel.getWidth());
			}
		}else {
			for(Integer k : slides.keySet()) {
				SlideGlass sg = slides.get(k);
				sg.setSize(viewPanel.getWidth(), viewPanel.getHeight());
			}
		}
		prevViewPanelW = currentW;
		prevViewPanelH = currentH;
	}
	
	@Override
	public boolean equals(Object pp) {
		if(pp == null) {
			return false;
		}
		if(!(pp instanceof Praparat)) {
			return false;
		}
		String srcConcatedUIDs = concatenationOfUIDStrings();
		String tarConcatedUIDs = ((Praparat)pp).concatenationOfUIDStrings();
		if(srcConcatedUIDs==null && tarConcatedUIDs==null) {
			return this == pp;
		}else if(srcConcatedUIDs==null && tarConcatedUIDs != null) {
			return this == pp;
		}else if(srcConcatedUIDs!=null && tarConcatedUIDs == null){
			return this == pp;
		}
		return srcConcatedUIDs.equals(tarConcatedUIDs);
	}
	
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		if(slides != null) {
			for(int instNo : slides.keySet()) {
				SlideGlass sg = slides.get(instNo);
				sg.repaint();
			}
		}
		pvcp.repaint();
	}
}
