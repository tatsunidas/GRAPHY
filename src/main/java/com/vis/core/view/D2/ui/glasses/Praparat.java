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
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.BirdsEyeView;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.processing.ImageProcessing;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.GhostGlassPane;
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
		Thumbnail,/*Thumbnail mode ( has limit functions)*/
		SingleGrid,/*Single grid view, film grid view no acceptable.(for bird's eye)*/
		FilmGrid,/*Film grid view, single grid view no acceptable. (for bird's eye)*/
		MPR,/*Allow showing crosslines. Its features are same as Normal mode.*/
	}
	public static final int ThumbnailSize = BirdsEyeView.thumbnailSize;
	
	//patient info set keys
	public final String KEY_PadID = "Patient​ID";
	public final String KEY_StudyUID = "StudyInstanceUID";
	public final String KEY_SeriesUID = "SeriesInstanceUID";
	public final String KEY_SopUIDs = "SOPInstanceUIDs";
		
	// component
	private PraparatViewControlPanel pvcp;
	private JPanel viewPanel;
	private SlideGlassGrid gridScrollPane;
	
	//resize timer
	javax.swing.Timer resizeTimer;
	
	int prevViewPanelW = 0;
	int prevViewPanelH = 0;
	
	private CineSlider slider;
	private Color studyColor = Color.CYAN;
	private LUT lut;//null-able
	
	private int currentSlice = -1;

	private int filmGridColumns = 5;
	private boolean isMultiFrame = false;/*to set video option*/
	private boolean isPDF = false;
	private boolean selected = false;
	private boolean focusGained = false;
	private boolean showGridViewOn = false;//filemGridView
		
	private boolean crossLineCursorMode = false;//mpr

	private ReferenceLineMPR refLineMPR;

	private List<String> pathToImages = null;
	Eyepiece prapManager;
	String patID;
	String studyUID;
	String seriesUID;
	String[] sopUIDs;
	String frameOfReferenceUID;
	String modality = null;
	
	int prevW;
	int prevH;
	
	private HashMap<Integer/*0 to N-1*/, SlideGlass> slides;
	
	private final int PREFETCH_RANGE = 3;
	private ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor();
	
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
		initComponent();
		prepareSlideGlassesUsingImagePlus(stack);
		if(mode != ViewMode.FilmGrid) {
			doSingleGridLayout();
		}else {
			doFilmGridLayout(null);
		}
	}
	
	public Praparat(String patID, String studyUID, String seriesUID, String[] sopUIDs, List<String> pathToSortedinstNoImages, Color studyColor, ViewMode mode) {
		this(patID, studyUID, seriesUID, sopUIDs, pathToSortedinstNoImages, null, null, studyColor, mode);
	}
	
	public Praparat(String patID, String studyUID, String seriesUID, String[] sopUIDs, List<String> pathToSortedinstNoImages, String refUID, Eyepiece manager,
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
		initComponent();
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
		initComponent();
	}
	
	public void adjustContrastFromMouseAction(int dragX, int dragY) {
		SlideGlass slide = getCurrentSlide();
		slide.adjustContrastFromMouseAction(dragX, dragY);
		if (isProcessSeries()) {
			HashMap<Integer, SlideGlass> slides = getAllSlides();
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				if (slide == sg) {
					continue;
				}
				sg.changeWindowingByMinMax(slide.currentMin, slide.currentMax);
			}
		}
	}
	
	public void adjustContrastAuto() {
		resetWindow();
	}
	
	/**
	 * Signedの場合に注意。See, sg.resetContrast()
	 * 
	 * @param min
	 * @param max
	 * @param fromMouseAction
	 */
	public void adjustContrastByMinMax(double min, double max) {
		SlideGlass slide = getCurrentSlide();
		slide.changeWindowingByMinMax(min, max);
		if (isProcessSeries()) {
			HashMap<Integer, SlideGlass> slides = getAllSlides();
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				if(slide == sg) {
					continue;
				}
				sg.changeWindowingByMinMax(min, max);
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
	
	
	private void constructSlideGlassesFromDicom(List<String> imgFiles) {
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
		 * as a premise, image files were sorted by inst No or z-order before loading.
		 */
		DICOMBackend backend = DICOMBackend.getCurrent();
		int numOfFiles = imgFiles.size();
		for (int i = 0; i < numOfFiles; i++) {
			DicomReader reader = DicomReader.newDicomReader(backend);
			reader.read(imgFiles.get(i), false);/*read only head*/
			DicomObject header = reader.getHeader();
			DicomObject fmi = reader.getFileMetaInfomation();
			String sopClassUID = header.getString(Tag.SOP​Class​UID, "");
			UID tsUID = reader.checkTSUID();
			//PDF
			if(sopClassUID.equals(UID.EncapsulatedPDFStorage.uid())) {
				this.isPDF = true;
				/*
				 * PDF to one series.
				 */
				loadSlideGlassFromPDF(imgFiles.get(i), header, fmi, backend);
				//PDF is one series, break here.
				break;
			}
			/*
			 * isMultiFrame
			 * 1.General image types do not have NumberOfFrames tag.(means -1).
			 * 2.3d sequence MRI, number of frame is 1 (of each image).
			 */
			DicomImage dcm = DicomImage.newDicomImage(imgFiles.get(i), header, fmi, tsUID, backend);
			this.isMultiFrame = dcm.isMultiFrame();
			this.isMultiFrame = this.isMultiFrame && dcm.getNumOfFrames() > 1;
			
			//single frame
			if(!isMultiFrame) {
				loadSlideGlassFromSimpleDicom(imgFiles.get(i), backend, tsUID);
			}else {
				/*
				 * multiframe to one series.
				 */
				loadSlideGlassFromMultiFrame(imgFiles.get(i), backend);
				/*
				 * if multiframe, load as only one file.
				 */
				break;
			}
			reader = null;
		}
		if(lut != null) {
			for(Integer pos : slides.keySet()) {
				slides.get(pos).setLUT(lut);
			}
		}
	}
	
	private void loadSlideGlassFromSimpleDicom(String path2dcm, DICOMBackend backend, UID tsUID) {
		ExecutorService executor = Executors.newFixedThreadPool(Utils.availableProcessors());
		List<Future<SlideGlass>> futures = new ArrayList<>();
		Callable<SlideGlass> task = () -> {
			DicomImage dcmimg = DicomImage.newDicomImage(path2dcm, backend);
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
//		try {
//			executor.awaitTermination(1, TimeUnit.MINUTES);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
	}
	
	private void loadSlideGlassFromMultiFrame(String path2dcm, DICOMBackend backend) {

		ExecutorService executor = Executors.newFixedThreadPool(Utils.availableProcessors());
		List<Future<SlideGlass>> futures = new ArrayList<>();

		DicomReader video_reader_ = DicomReader.newDicomReader(backend);
		video_reader_.read(path2dcm, false/* with bulk */);
		final UID u = video_reader_.checkTSUID();
		final DicomObject fmi = video_reader_.getFileMetaInfomation();
		final DicomObject header = video_reader_.getHeader();
		int size = header.getInt(Tag.Number​Of​Frames, -1);
		// System.out.println(header.getInt(Tag.Instance​Number, -1));
		
		for (int j = 0; j < size; j++) {
			final int k = j;
			Callable<SlideGlass> task = () -> {
				DicomObject instHeader = DicomObject.newDicomObject(header, backend);
				instHeader.setInt(Tag.Instance​Number, VR.IS, (k + 1));
				DicomImage frame = DicomImage.newDicomImage(path2dcm, instHeader, fmi, u, backend);
				return new SlideGlass(this, frame);
			};
			futures.add(executor.submit(task));
		}
		AtomicInteger counter = new AtomicInteger(0);
		for (Future<SlideGlass> future : futures) {
			try {
				slides.put(counter.getAndAdd(1), future.get());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		executor.shutdown();
		video_reader_ = null;
	}
	
	private void loadSlideGlassFromPDF(String path2dcm, DicomObject header, DicomObject fmi, DICOMBackend backend) {
		
		PDFReader pdfReader = new PDFReader(new File(path2dcm)/*read dicom*/);
		ExecutorService executor = Executors.newFixedThreadPool(Utils.availableProcessors());
		List<Future<SlideGlass>> futures = new ArrayList<>();
		
		isMultiFrame = true;//always treats multi
		isPDF = true;//fail safe
		boolean isThumbnail = getViewMode() == ViewMode.Thumbnail;
		int size = isThumbnail ? 1 : pdfReader.getPDFPageCount();
		//if thumbnail, load only one frame
		if (isThumbnail) {
			Log.logger.fine("Praparat view mode is Thumbnail, PDF will be loaded only first page.");
		}
		for (int j = 0; j < size; j++) {
			final int k = j;
			Callable<SlideGlass> task = () -> {
				BufferedImage page = pdfReader.renderPDFPage(k);
				DicomObject instHeader = DicomObject.newDicomObject(header, backend);
				instHeader.setInt(Tag.Instance​Number, VR.IS, (k + 1));
				instHeader.setInt(Tag.Columns, VR.US, page.getWidth());
				instHeader.setInt(Tag.Rows, VR.US, page.getHeight());
				instHeader.setInt(Tag.Samples​Per​Pixel, VR.US, 3);
				instHeader.setInt(Tag.Bits​Allocated, VR.US, 8);
				DicomImage frame = DicomImage.newDicomImage(path2dcm, instHeader, fmi, UID.ExplicitVRLittleEndian, backend);
				return new SlideGlass(this, frame);
			};
			futures.add(executor.submit(task));
		}
		AtomicInteger counter = new AtomicInteger(0);
		for (Future<SlideGlass> future : futures) {
			try {
				slides.put(counter.getAndAdd(1), future.get());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		executor.shutdown();
	}
	
	/**
	 * Attention: Will use large physical memory.
	 * This method is only used for single pop-up view or test purpose.
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
		HashMap<Integer, DicomImage> ds = ImageUtils.imagePlusToDcm(images, secondaryUse/*treat as secondary capture*/);
		for (int i = 0; i < ds.size(); i++) {
			SlideGlass sg = new SlideGlass(this, ds.get(i));
			images.setSlice(i+1);
			sg.imageSpecimen.setOriginalCalibration(images.getCalibration());
			slides.put(i, sg);
		}
		
		//TODO
		//lut = images.getLuts();
		
		if(lut != null) {
			for(Integer pos : slides.keySet()) {
				slides.get(pos).setLUT(lut);
			}
		}
	}

	private void constructSlideGlassesFromPraparat(Praparat p) {
		if(p == null) {
			return;
		}
		HashMap<Integer, SlideGlass> srcSlides = p.getAllSlides();
		if (srcSlides == null || srcSlides.size() < 1) {
			System.out.println("Slides have no images...");
			return;
		}
		// init
		removeSlide(currentSlice);
		this.slides = new HashMap<Integer, SlideGlass>();
		
		isMultiFrame = p.isMultiFrame();
		
		Set<Integer> keys = srcSlides.keySet();
		for(Integer k : keys) {
			//init slide from another slides to set this praparat.
			SlideGlass sg = srcSlides.get(k);
			SlideGlass newsg = new SlideGlass(this, sg.getDicomImage());
			this.slides.put(k, newsg);
		}
		if(Utils.isDebug) {
			Log.logger.fine(this.slides.size()+" images loaded.");
		}
		if(lut != null) {
			for(Integer pos : this.slides.keySet()) {
				this.slides.get(pos).setLUT(lut);
			}
		}
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
		updateViewPanel();
		showFirstImage();
	}
	
	public HashMap<Integer, SlideGlass> getAllSlides() {
		if(slides != null && slides.size() < 1) {
			return null;
		}
		return slides;
	}

	public PraparatViewControlPanel getController() {
		return pvcp;
	}
	
	public SlideGlass getCurrentSlide() {
		if(slides == null) {
			return null;
		}
		if (currentSlice == -1) {
			return slides.get(0);
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

	public List<String> getImageFileLocations() {
		return this.pathToImages;
	}

	/**
	 * return slides as imageplus.
	 * 
	 * TODO: if multi-frame ??
	 * 
	 * @return imageplus
	 */
	public ImagePlus getImagePlus() {
		
		if (slides == null || slides.size() < 1) {
			return null;
		}
		
		if(isMultiFrame()) {
			logger.info("This series multi frame. Do you continue convert imageplus ? It will take a long time...");
			int res = JOptionPane.showConfirmDialog(this, "This series is multi-frame. Take long time converting to imageplus. Still are you continue ?");
			if(res != JOptionPane.OK_OPTION) {
				return null;
			}
		}

		ImagePlus replica = null;
		ImageStack stack = new ImageStack();
		String info = "";

		// 1. スライドをインデックス順にソートして処理する
		List<Integer> sortedIndices = new ArrayList<>(slides.keySet());
		Collections.sort(sortedIndices);

		if(!isMultiFrame()) {
			Calibration orgCal = null;
			for (int index : sortedIndices) {
				SlideGlass sg = slides.get(index);
				DicomImage frame = sg.getDicomImage();
				if(orgCal == null) {
					orgCal = sg.getOriginalCalibration();
				}
				if (hasFileSource(index)) {
					if (!frame.ensurePixelDataLoaded()) {
						continue;
					}
				}
				
				ImagePlus imp = GDicomTools.dcmImgToImagePlus(frame, orgCal);
				ImageProcessor ip = imp.getProcessor();
				if (ip.getNChannels() == 3 || ip instanceof ColorProcessor) {
					ip.snapshot();// keep original pixels
				}
				/*
				 * In this case, always only one slice. It case use getInfoProperty().
				 */
				String headerLabel = imp.getInfoProperty();
				/*
				 * if header has "\n" in head (at index 0), DicomTools.getTag() return null.
				 */
				//header = imp.getStack().getSliceLabel(1);
				stack.addSlice(headerLabel, imp.getProcessor());
			}
			replica = new ImagePlus("stack", stack);
			/*
			 * if ImagePlus has only one slice, header is updated by setProp("Info", hdr).
			 */
			if (replica.getNSlices() == 1) {
				replica.setProp("Info", info);
			}
			/*
			 * Now, fail safe ?
			 * But this is not suitable for EnhancedMultiFrame DICOM.
			 */
//			replica.setCalibration(orgCal);			
			return replica;
		}else {
			if (hasFileSource(0)) {
				Calibration orgCal = getCurrentSlide().getOriginalCalibration();
				String path = getImageFileLocations().get(0);
				DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
				reader.read(path, false);
				DicomObject header  = reader.getHeader();
				DicomImage dcm = DicomImage.newDicomImage(path, header, reader.getFileMetaInfomation(), reader.checkTSUID(), DICOMBackend.getCurrent());
				ImagePlus imp = GDicomTools.dcmImgToImagePlus(dcm, orgCal);
				return imp;
			}
		}
		
		return null;
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
	
	public int getImageWidth() {
		return slides.get(0).getOriginalImage().getWidth();
	}
	
	public int getImageHeight() {
		return slides.get(0).getOriginalImage().getHeight();
	}
	
	public LUT getLUT() {
		return this.lut;
	}

	public int getNumberOfImages() {
		return slides.size();
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

	/**
	 * 
	 * @param sopUID
	 * @return slide pos : 0 to n-1.
	 */
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
	
	public boolean hasFileSource(int index /* 0 base */) {
		List<String> paths = getImageFileLocations();
		if (paths == null) {
			return false;
		}
		if (paths.size() < index) {
			return false;
		}
		String p = paths.get(index);
		return p != null && new File(p).exists();
	}
	
	private void initComponent() {
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
		/*
		 *SlideGlass parent component 
		 */
		viewPanel = new JPanel();
		viewPanel.setName("ViewPanel");
		viewPanel.setBackground(Color.BLACK);//debug purpose
		viewPanel.setLayout(new GridLayout(1, 1));
		
		/*
		 * Component listener
		 */
		// タイマーの初期化（300ミリ秒待機）
		resizeTimer = new javax.swing.Timer(200, e -> {
			logger.fine("Delayed resize execution.");
			updateViewPanel();
		});
		resizeTimer.setRepeats(false); // 1回だけ実行
		viewPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if (resizeTimer.isRunning()) {
	                resizeTimer.restart();
	            } else {
	                resizeTimer.start();
	            }
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
			Dimension size = new Dimension(ThumbnailSize, ThumbnailSize);
			viewPanel.setPreferredSize(size);
			this.setPreferredSize(size); // FlowLayout用
			this.setMinimumSize(size);
			setFocusable(true);
			setRequestFocusEnabled(true);
			pvcp.enableShowInfo(false);
			pvcp.enableShowROI(false);
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
	
	/**
	 * boolean isMultiFrame = dcmimg.isMultiFrame();
	 * isMultiFrame = isMultiFrame && dcmimg.getNumOfFrames() > 1;
	 * @return
	 */
	public boolean isMultiFrame() {
		return this.isMultiFrame;
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
		// Copy LUT from source Praparat if we don't have one yet
		LUT srcLut = p.getLUT();
		if(this.lut == null && srcLut != null) {
			this.lut = srcLut;
		}
		HashMap<String, Object> info = p.getInfoSet();
		String patID = (String)info.get("PatientID");
		String studyUID = (String)info.get("StudyInstanceUID");
		String seriesUID = (String)info.get("SeriesInstanceUID");
		String[] sopUIDs = (String[])info.get("SOPInstanceUIDs");
		List<String> pathToImages = p.getImageFileLocations();
		
		if(pathToImages == null || pathToImages.size()==0) {
			logger.warning("prap needs path to images..., return.");
			return;
		}
		viewPanel.removeAll();
		setInfo(patID, studyUID, seriesUID, sopUIDs, pathToImages);
		constructSlideGlassesFromPraparat(p);
		currentSlice = -1;
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
	
	public void prepareSlideGlasses(String patID, String studyUID, String seriesUID, String[] sopUIDs, List<String> pathToImages) {
		if(pathToImages == null || pathToImages.size()==0) {
			System.out.println("prap needs path to images..., return.");
			return;
		}
		viewPanel.removeAll();
		/*
		 * update information of series images.
		 */
		setInfo(patID, studyUID, seriesUID, sopUIDs, pathToImages);
		constructSlideGlassesFromDicom(pathToImages);
		currentSlice = -1;
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
		currentSlice = -1;
		updateInfoLabel(-1,-1,"-1",new double[] {-1,-1},-1,-1);
		
		String patID = GDicomTools.getTag(images, "0010,0020");
		if(patID != null) patID = patID.trim();
		String studyUID = GDicomTools.getTag(images, "0020,000D");
		if(studyUID != null) studyUID = studyUID.trim();
		String seriesUID = GDicomTools.getTag(images, "0020,000E");
		if(seriesUID != null) seriesUID = seriesUID.trim();
		String[] sopUIDs = new String[images.getNSlices()];
		List<String> paths = new ArrayList<>();
		for(int i = 1; i <= images.getNSlices(); i++) {
			images.setPosition(i);
			String sopInstUid = GDicomTools.getTag(images, "0008,0018");
			if(sopInstUid==null || sopInstUid.trim().length()==0) {
				sopInstUid = UIDUtils.createUID();
			}else {
				sopUIDs[i-1] = sopInstUid.trim();//need trim.
			}
			String path = images.getFileInfo().getFilePath();
			if(path != null && path.length() > 0 && new File(path).exists()) {
				if(!paths.contains(path)) {
					paths.add(path);
				}
			}
		}
		images.setSlice(1);//back to first.
		String refUID = GDicomTools.getTag(images, "0020,0052");
		setInfo(patID, studyUID, seriesUID, sopUIDs, refUID, paths.size()==sopUIDs.length?paths:null);
		
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
		currentSlice = -1;
		updateInfoLabel(-1,-1,"-1",new double[] {-1,-1},-1,-1);
		
		String pid = null;
		String studyUID = null;
		String seriesUID = null;
		String[] sopUIDs = new String[paths.size()];
		DICOMBackend be = DICOMBackend.getCurrent();
		int j =0;
		for(String path : paths) {
			DicomReader reader = DicomReader.newDicomReader(be);
			reader.read(path, false);
			DicomObject dcmObj = reader.getHeader();
			if(pid == null) {
				pid = dcmObj.getString(Tag.Patient​ID);
				studyUID = dcmObj.getString(Tag.Study​Instance​UID);
				seriesUID = dcmObj.getString(Tag.Series​Instance​UID);
			}
			sopUIDs[j++] = dcmObj.getString(Tag.SOP​Instance​UID);
		}
		setInfo(pid, studyUID, seriesUID, sopUIDs, paths);
		constructSlideGlassesFromDicom(paths);
		if(slider != null) {
			slider.initContext();
		}
		if(Utils.isDebug) {
			System.out.println(slides.size()+" images loaded.");
		}
	}
	
	public ImagePlus processCropRectangle(boolean show) {
		SlideGlass sg = getCurrentSlide();
		CanvasGlass cg = (CanvasGlass)sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
		RoiObj roi = cg.getSelectedRoi();
		if(roi == null) {
			JOptionPane.showMessageDialog(this, "Please select/create roi first. Can not cropping.", "Crop Tool", JOptionPane.INFORMATION_MESSAGE);
			return null;
		}
		
		RoiObj rect = new RoiObj(roi.getXBase(), roi.getYBase(), roi.getBounds().width, roi.getBounds().height, null);
		
		int res = JOptionPane.showConfirmDialog(this, "Process all slides in this series ?", "Crop series ?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		final ImagePlus crop;
		if(res != JOptionPane.YES_OPTION) {
			ImagePlus imp = getImagePlus();
			imp.setSlice(getCurrentSlidePos()+1);
			crop = new ImageProcessing().cropRect(imp, rect, false);
		}else {
			ImagePlus imp = getImagePlus();
			crop = new ImageProcessing().cropRect(imp, rect, true);
		}
		if(crop == null || crop.getNSlices() < 1) {
			Log.logger.severe("Cropping was failed...");
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
	
	public ImagePlus processCut(boolean show) {
		SlideGlass sg = getCurrentSlide();
		CanvasGlass cg = (CanvasGlass)sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
		RoiObj currentRoi = cg.getSelectedRoi();
		if(currentRoi == null ) {
			Log.logger.info("Current ROI is null...");
			return null;
		}
		
		//RoiObj rect = new RoiObj(currentRoi.getXBase(), currentRoi.getYBase(), currentRoi.getBounds().width, currentRoi.getBounds().height, null);
		
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
		
		if(cut == null) {
			Log.logger.info("Cut failed... Please re-try...");
			return null;
		}
		
		new ImageProcessing().windowing(cut, sg.currentMin, sg.currentMax);
		
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
	
	/**
	 * 指定したインデックスの画像をメモリ上に実体化（解凍含む）させる
	 */
	private void realizeImage(int index /* 0 to N-1 */, boolean processSeries, Double syncMag, Double syncRot, Double syncMin, Double syncMax, Point syncOrigin) {
		if (index < 0 || index >= slides.size()) {
			return;
		}
		SlideGlass sg = slides.get(index);
		DicomImage dcmimg = sg.getDicomImage();
		isMultiFrame = dcmimg.isMultiFrame();
		isMultiFrame = isMultiFrame && dcmimg.getNumOfFrames() > 1;
		isMultiFrame = isPDF == true ? true:isMultiFrame;
		// まだピクセルデータがロードされていない、または解凍されていない場合
		if (sg.getOriginalImage() == null) {
			/**
			 * multi frame file has only one file path.
			 */
			int file_pos = isMultiFrame ? 0 : index;
			int frame_pos = isMultiFrame ? index : 0;
			if (hasFileSource(file_pos)) {
				// 1. ファイルからピクセルを読み込む
				if (dcmimg.ensurePixelDataLoaded()) {
					// pixel情報がある場合にのみ更新
					ImagePlus im = new ImagePlus("" + index, dcmimg.getImageProcessor(frame_pos));
					sg.imageSpecimen.setOriginalImage(im);
				}
			}
			//common
			sg.initCalibrationAndLUT();
			if(processSeries) {
				//move, zoom, rotate, windowing
				if(syncMag!=null && !syncMag.isNaN()) sg.zoom(syncMag, false/*dummy*/);
				if(syncRot!=null && !syncRot.isNaN()) sg.rotate(syncRot);
				if((syncMin!=null && !syncMin.isNaN()) && (syncMax!=null && !syncMax.isNaN())) sg.changeWindowingByMinMax(syncMin, syncMax);
				//finally set origin
				if(syncOrigin!=null) sg.setDisplayOrigin(syncOrigin);
			}
		}
	}
	
	/**
	 * SingleGrid描画用
	 * キャッシュ管理：円環（リングバッファ）状に前後10枚をロードする
	 */
	public void manageCache(int currentIndex) {
		if (slides == null || slides.isEmpty())
			return;
		
		// --- 同期する状態のスナップショットを取得 ---
	    SlideGlass current = getCurrentSlide();
	    final double syncMag = current.getMagnification();
	    final double syncRot = current.getRotateAngle();
	    final double syncMin = current.currentMin;
	    final double syncMax = current.currentMax;
	    final Point syncOrigin = current.getDisplayImageOriginXY();
	    final boolean processSeries = isProcessSeries();

		prefetchExecutor.submit(() -> {
			int totalSize = slides.size();

			// 1. 周回を考慮した範囲の画像をロード
			// currentIndexを中心に、-10から+10までの相対位置を計算
			for (int i = -PREFETCH_RANGE; i <= PREFETCH_RANGE; i++) {
				// 剰余演算を利用してインデックスを [0 ～ totalSize-1] に収める
				// (currentIndex + i + totalSize) % totalSize により、マイナス値も正しく末尾へ回る
				int targetIndex = (currentIndex + i + totalSize) % totalSize;
				realizeImage(targetIndex, processSeries, syncMag, syncRot, syncMin, syncMax, syncOrigin);
			}
			
			// ロード完了後、SlideGlassに再描画を促す
			SlideGlass sg = slides.get(currentIndex);
			if (sg != null) {
				synchronized(sg) {
					sg.updateDisplayImage();
					SwingUtilities.invokeLater(sg::repaint);
				}
			}

			// 2. メモリ解放（範囲外の画像をアンロード）
			// 枚数が少ない場合は解放不要（例: 全体が21枚以下の場合は全て保持）
			if (totalSize > (PREFETCH_RANGE * 2 + 1)) {
				for (int j = 0; j < totalSize; j++) {
					if (!isWithinCircularRange(j, currentIndex, totalSize, PREFETCH_RANGE)) {
						unloadImage(j);
					}
				}
			}
		});
	}

	/**
	 * 円環状のインデックスにおいて、targetがcenterのrange内にあるか判定するヘルパー
	 */
	private boolean isWithinCircularRange(int target, int center, int totalSize, int range) {
		// 2点間の最短距離（周回考慮）を計算
		int diff = Math.abs(target - center);
		int distance = Math.min(diff, totalSize - diff);
		return distance <= range;
	}
	
	/**
	 * FilmGrid用のキャッシュ管理。
	 * 表示範囲 [first, last] の画像を実体化し、それ以外を解放する。
	 */
	public void manageGridCache(int firstIdx, int lastIdx) {
		if (slides == null || slides.isEmpty())
			return;

		prefetchExecutor.submit(() -> {
			// 1. 指定範囲をロード
			// FilmGridでは通常同期設定（ズーム等）は不要なため、デフォルト値で呼ぶ
			for (int i = firstIdx; i <= lastIdx; i++) {
				realizeImage(i, false, 1.0, 0.0, null, null, null);

				// ロード完了後、SlideGlassに再描画を促す
				SlideGlass sg = slides.get(i);
				if (sg != null) {
					synchronized(sg) {
						sg.updateDisplayImage();
						SwingUtilities.invokeLater(sg::repaint);
					}
				}
			}

			// 2. 範囲外（少し余裕を持たせる）をアンロード
			int buffer = 15; // 画面外前後は保持しておく（頻繁なロード防止）
			int totalSize = slides.size();
			for (int j = 0; j < totalSize; j++) {
				if (j < (firstIdx - buffer) || j > (lastIdx + buffer)) {
					unloadImage(j);
				}
			}
		});
	}

	private void unloadImage(int index) {
		SlideGlass sg = slides.get(index);
		if (sg != null && sg.getDicomImage() != null) {
			// original image to null
			sg.imageSpecimen.setOriginalImage(null);
			// bulk file release
			if(!isMultiFrame()) {
				if (hasFileSource(index)) {
					sg.getDicomImage().releasePixelBulkFromHeader();
				}
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
	
	public void reloadSlideGlasses(Praparat pp) {
		if (pp == null) { return; }
		prepareSlideGlasses(pp);
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
			pvcp.setProcessSeries(true);//to show all images after reset
			updateInfoLabel(-1,-1,"-1",new double[] {-1,-1},-1,-1);
			if (isShowGridViewOn()) {
				showGridViewOn = false;
				doFilmGridLayout(filmGridColumns);
			}else {
				// reload slides
				//prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
				//setTextVisible(pvcp.isShowInfo());
				//setAnnotationVisible(pvcp.isShowRoi());
				doSingleGridLayout();
			}
			for(int i : slides.keySet()) {
				slides.get(i).reset();
			}
			return;
		}
		
		/*
		 * BirdsEye FilmGrid
		 */
		if(mode == ViewMode.FilmGrid) {
			pvcp.setProcessSeries(true);//to show all images after reset
			updateInfoLabel(-1,-1,"-1",new double[] {-1,-1},-1,-1);
//			setTextVisible(false);
//			setAnnotationVisible(false);
			doFilmGridLayout(filmGridColumns);
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

	/**
	 * 
	 * @param slideX SlideGlass mouse X
	 * @param slideY SlideGlass mouse Y
	 */
	public void setAndShowPixelValue(SlideGlass currentSlide, int slideX, int slideY) {
//		SlideGlass currentSlide = getCurrentSlide();
//		if(currentSlide == null) return;
		double[] scaleXY = currentSlide.getScaleFactor();
		double mag = currentSlide.getMagnification();
		double rotate = currentSlide.getRotateAngle();
		Point pointOnOrg;
		try {
			pointOnOrg = currentSlide.offScreenCoordinate(slideX, slideY);
		} catch (NoninvertibleTransformException e) {
			e.printStackTrace();
			return;
		}
		
		Object[] val = currentSlide.getPixelValueFromOriginal(pointOnOrg.x, pointOnOrg.y);
		if(val == null) {
			updateInfoLabel(pointOnOrg, null+"("+null+")",scaleXY, mag,rotate);
			return;
		}
		if(!currentSlide.isRGB()) {
			Double[] pixelRawAndCalibrated = (Double[])currentSlide.getPixelValueFromOriginal(pointOnOrg.x, pointOnOrg.y);
			double raw_v = pixelRawAndCalibrated[0];
			double calibrated_v = pixelRawAndCalibrated[1];
			updateInfoLabel(pointOnOrg, calibrated_v+"("+raw_v+")",scaleXY, mag,rotate);
		}else {
			String[] rgbAndColor = (String[])currentSlide.getPixelValueFromOriginal(pointOnOrg.x, pointOnOrg.y);
			String r = rgbAndColor[0];
			String g = rgbAndColor[1];
			String b = rgbAndColor[2];
//			String color = rgbAndColor[3];//java.awt.Color[r,g,b]
//			updateInfoLabel(X, Y, r+","+g+","+b+" "+"("+color+")", scale, mag, rotate);
			updateInfoLabel(pointOnOrg, "(r,g,b)"+r+","+g+","+b, scaleXY, mag, rotate);
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
	
	private void setImageFileLocations(List<String> pathToImages) {
		this.pathToImages = pathToImages;
	}
	
	/**
	 * If you want to change slide position, use setImagePositionUsingSlider instead.
	 * @param sliceIndex:number of slice index, 0 to n-1
	 */
	void setImagePosition(int sliceIndex) {
		if (slides == null) { // do not include pathToImages
			return;
		}
		if (isShowGridViewOn()) {
			return;
		}
		
		if(currentSlice == -1) {
			currentSlice = sliceIndex;
			// 1. 現在表示する画像は「最優先」でロード（メインスレッド）
			double syncMag = 1.0;
			double syncRot = 0.0;
			Double syncMin = null;
			Double syncMax = null;
			Point syncOrigin = null;
			realizeImage(currentSlice, isProcessSeries(), syncMag, syncRot, syncMin, syncMax, syncOrigin);

			SlideGlass currentGlass = this.slides.get(currentSlice);
			if (currentGlass == null)
				return;

			viewPanel.removeAll();
			viewPanel.add(currentGlass, 0);

			// 2. 前後の先読みを開始（バックグラウンドスレッド）
			manageCache(currentSlice);

			currentGlass.updateDisplayImage();
			currentGlass.repaint();
			currentGlass.requestFocus();
			currentGlass.setFocusGained(true);// for key listener
			return;
		}
		
		if(currentSlice == sliceIndex) {
			return;
		}

		currentSlice = sliceIndex;
		// 1. 現在表示する画像は「最優先」でロード（メインスレッド）
		SlideGlass currentGlass = this.slides.get(currentSlice);
		if (currentGlass == null)
			return;
		
		double syncMag = currentGlass.getMagnification();
		double syncRot = currentGlass.getRotateAngle();
		Double syncMin = currentGlass.currentMin;
		Double syncMax = currentGlass.currentMax;
		Point syncOrigin = currentGlass.getDisplayImageOriginXY();
		realizeImage(currentSlice, isProcessSeries(), syncMag, syncRot, syncMin, syncMax, syncOrigin);

		viewPanel.removeAll();
		viewPanel.add(currentGlass, 0);

		// 2. 前後の先読みを開始（バックグラウンドスレッド）
		manageCache(currentSlice);

		currentGlass.updateDisplayImage();
		currentGlass.repaint();
		currentGlass.requestFocus();
		currentGlass.setFocusGained(true);// for key listener
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
				if(slider.getCurrentSliceIndex()/*1 to N*/ == (pos+1) /*pos is 0 to N-1*/) {
					setImagePosition(pos);
					callBackLocalizer();
				}else {
					slider.setSlice(pos);
				}
			}
		});
	}
	
	private void setInfo(String patID, String studyUID, String seriesUID, String[] sopUIDs, List<String> pathToImages) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		String frameOfRefUID = null;
		if(db != null) {
			frameOfRefUID = db.getValueFromImage("FrameOfReferenceUID", patID, studyUID, seriesUID, sopUIDs[0]);
		}else {
			frameOfRefUID = DicomUtilities.getFrameOfReferenceUID(pathToImages.get(0));
		}
		setInfo(patID, studyUID, seriesUID, sopUIDs, frameOfRefUID, pathToImages);
	}
	
	private void setInfo(String patID, String studyUID, String seriesUID, String[] sopUIDs, String refUID, List<String> pathToImages) {
		this.patID = patID;
		this.studyUID = studyUID;
		this.seriesUID = seriesUID;
		this.sopUIDs = sopUIDs;
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db != null && (refUID == null || refUID.length()==0)) {
			this.frameOfReferenceUID = db.getValueFromImage("FrameOfReferenceUID", patID, studyUID, seriesUID, sopUIDs[0]);
		}else {
			this.frameOfReferenceUID = refUID;
		}
		setImageFileLocations(pathToImages);
	}
	
	public void setLUT(LUT lut) {
		this.lut = lut;
		if(!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			sg.setLUT(this.lut);
		}else {
			for(Integer key:slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.setLUT(this.lut);
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
	 * See, SlideGlassMouseListener
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
	@SuppressWarnings("unused")
	private void setViewPanelSize(int w, int h) {
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
	
	/*
	 * GhostGlassPane is the filter on top of the 2DViewerFrame. 
	 * JToolBarは、ユーザーがドラッグしてウィンドウから切り離す（フローティングさせる）ことができる。 
	 * ツールバーが切り離された場合、そのツールバーは「JFrameの子」ではなくなり、
	 * 「独立した別のウィンドウ」 になる。 
	 * 
	 * Floatingした場合は、JDialogへGhostGlassPaneを自動追加。
	 * StageView:ancestor
	 * 
	 * 切り離されていない時: JFrame の GlassPane でOK。
	 * 切り離された時: フローティングウィンドウ自身の GlassPane を使う必要がある。
	 */
	public GhostGlassPane getGhostGlassPane() {
		Window currentWindow = SwingUtilities.getWindowAncestor(this);
		if(currentWindow instanceof JFrame) {
			JFrame f = (JFrame)currentWindow;
			Component gp = f.getGlassPane();
			if(gp != null && gp instanceof GhostGlassPane) {
				return (GhostGlassPane)gp;
			}
		}
		return null;
	}
	
	/**
	 * Prapがメインフレームにあるか、切り離されているかを判定
	 * @return true: メインフレーム内にある（ドッキング中）, false: 切り離されている
	 */
	public boolean isAttachedToMainFrame() {
	    Window currentWindow = SwingUtilities.getWindowAncestor(this);
	    MainScreen mainScreen = MainScreen.getInstance();
	    return currentWindow == mainScreen;
	}
	
	public boolean isAttachedToViewr2D() {
	    Window currentWindow = SwingUtilities.getWindowAncestor(this);
	    Viewer2DScreen d2Screen = Viewer2DScreen.getInstance();
	    return currentWindow == d2Screen;
	}
	
	public boolean isAttachedToFloatingDialog() {
	    Window currentWindow = SwingUtilities.getWindowAncestor(this);
	    // 冗長だが、可読性のために記載
	    if(isAttachedToMainFrame()) {
	    	return false;
	    }
	    if(isAttachedToViewr2D()) {
	    	return false;
	    }
	    // 親が JFrame なら「くっついている」とみなす
	    // 親が JDialog (フローティング用) なら「切り離されている」とみなす
	    if (currentWindow instanceof JFrame) {
	        return true; // ドッキング中
	    } else {
	        return false; // フローティング中（JDialogなど）
	    }
	}

	public void showBorder(boolean show) {
		Border b = BorderMaker.make(this, isFocusGained());
		setBorder(b);
	}
	
	public void showFirstImage() {
		// position range is 0 to n-1
		currentSlice = -1;
		setImagePosition(0);
	}
	
	private void updateInfoLabel(Point p, String value, double[] scaleXY, double mag, double rotate) {
		updateInfoLabel(p.x, p.y, value, scaleXY, mag, rotate);
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
		
		if(mode == ViewMode.Thumbnail) {
			for(Integer k : slides.keySet()) {
				SlideGlass sg = slides.get(k);
				sg.setSize(ThumbnailSize, ThumbnailSize);
			}
			prevViewPanelW = currentW;
			prevViewPanelH = currentH;
			return;
		}
		
		if(currentW == prevViewPanelW && currentH == prevViewPanelH) {
			return;
		}
		
		if(mode == ViewMode.FilmGrid || showGridViewOn/*ViewMode.Normal*/) {
			if (viewPanel.getComponentCount() >=1) {
				Component con = viewPanel.getComponent(0);
				if(con instanceof SlideGlassGrid) {
					SlideGlassGrid sgg = (SlideGlassGrid)con;
					sgg.update(viewPanel.getWidth());
				}
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
