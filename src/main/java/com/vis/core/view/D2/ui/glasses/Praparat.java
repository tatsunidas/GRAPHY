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
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
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

import com.vis.core.log.Log;
import com.vis.core.slicer.ReferenceLineMPR;
import com.vis.core.ui.main.BirdsEyeView;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.processing.ImageProcessing;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.GhostGlassPane;
import com.vis.core.view.D2.ui.SeriesWindow;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.IntersectVolume;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.Modality;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.GDicomTools;
import com.vis.imageio.PDFReader;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

/**
 * SeriesViewer
 * 
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class Praparat extends JPanel {

	final int minimumGridCol = 5;
	private Logger logger = Log.logger;

	public enum ViewMode {
		Normal, /* Switch-able mode, both single view and film grid view */
		Thumbnail, /* Thumbnail mode ( has limit functions) */
		SingleGrid, /* Single grid view, film grid view no acceptable.(for bird's eye) */
		FilmGrid, /* Film grid view, single grid view no acceptable. (for bird's eye) */
		MPR,/* Allow showing crosslines. Its features are same as Normal mode. */
	}

	public static final int ThumbnailSize = BirdsEyeView.thumbnailSize;

	// patient info set keys
	public final String KEY_PadID = "Patient​ID";
	public final String KEY_StudyUID = "StudyInstanceUID";
	public final String KEY_SeriesUID = "SeriesInstanceUID";
	public final String KEY_SopUIDs = "SOPInstanceUIDs";

	// component
	private PraparatViewControlPanel pvcp;
	private JPanel viewPanel;
	private SlideGlassGrid gridScrollPane;

	// resize timer
	javax.swing.Timer resizeTimer;

	int prevViewPanelW = 0;
	int prevViewPanelH = 0;

	private CineSlider slider;// slice position
	private CineSlider channelSlider; // C (Channel) slider
	private CineSlider frameSlider; // T (Time/Frame) slider
	private JPanel sliderPanel; // スライダーを縦に並べるためのコンテナ

	private Color studyColor = Color.CYAN;
	private LUT lut;// null-able

	private int currentSlice = -1;
	private javax.swing.Timer scrollDebounceTimer;
	
	/*
	 * CZT index to handle multi-channel
	 */
	private int pendingTargetIndex = -1;
	
	private java.util.concurrent.atomic.AtomicInteger latestCacheRequest = new java.util.concurrent.atomic.AtomicInteger(0);

	private int filmGridColumns = 5;
	private boolean isMultiFrame = false;/* to set video option */
	private boolean isPDF = false;
	private boolean selected = false;
	private boolean focusGained = false;
	private boolean showGridViewOn = false;// filemGridView

	private boolean crossLineCursorMode = false;// mpr

	private ReferenceLineMPR refLineMPR;

	private List<String> pathToImages = null;
	Eyepiece prapManager;
	String patID;
	String studyUID;
	String seriesUID;
	String[] sopUIDs;
	String frameOfReferenceUID;
	Modality modality = null;

	int prevW;
	int prevH;

	// 多次元管理のための基本プロパティ
	private int nChannels = 1; // C: チャンネル数
	private int nSlices = 1; // P: 空間のスライス数
	private int nFrames = 1; // T: 時間（フレーム）数

	// 現在表示している位置（状態）
	private int currentC = 0;//channel, 0-based
	private int currentZ = 0;//slice position, 0-based
	private int currentT = 0;//time frame, 0-based

	/*
	 * 順序は保証しない
	 */
	private ConcurrentHashMap<Integer/* 0 to N-1 */, SlideGlass> slides;

	private final int PREFETCH_RANGE = 3;
	private ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor();

	final ViewMode mode;
	
	/*
	 * 2D viewerツールタイプ指定
	 * -1 は「未設定（2D Viewerのグローバル状態に従う）」を意味します
	 */
    private int localToolType = -1;

	/**
	 * Load normal praparat
	 * 
	 * @param stack
	 * @param studyColor
	 */
	public Praparat(ImagePlus stack, Color studyColor, ViewMode mode, boolean sortCZT) {
		this.mode = mode;
		if (studyColor != null) {
			this.studyColor = studyColor;
		}
		initComponent();
		String modality_str = GDicomTools.getTag(stack, Tag.Modality);
		this.modality = Modality.is(modality_str);
		loadSeries(stack, sortCZT);
		if (mode != ViewMode.FilmGrid) {
			doSingleGridLayout();
		} else {
			doFilmGridLayout(null);
		}
	}

	public Praparat(String patID, String studyUID, String seriesUID, String[] sopUIDs,
			List<String> pathToSortedinstNoImages, Color studyColor, ViewMode mode) {
		this(patID, studyUID, seriesUID, sopUIDs, pathToSortedinstNoImages, null, null, studyColor, mode);
	}

	public Praparat(String patID, String studyUID, String seriesUID, String[] sopUIDs,
			List<String> pathToSortedinstNoImages, String refUID, Eyepiece manager, Color studyColor, ViewMode mode) {
		if (mode == null) {
			this.mode = ViewMode.Normal;
		} else {
			this.mode = mode;
		}
		if (studyColor != null) {
			this.studyColor = studyColor;
		}
		this.prapManager = manager;
		initComponent();
		loadSeries(patID, studyUID, seriesUID, sopUIDs, pathToSortedinstNoImages);
		DicomObject dcm = slides.get(0).getHeader();
		this.modality = Modality.is(dcm);
		if (mode != ViewMode.FilmGrid) {
			doSingleGridLayout();
		} else {
			doFilmGridLayout(null);
		}
	}

	/**
	 * e.g., Praparat pp = new Praparat(ViewMode.Normal);
	 * pp.prepareSlideGlassesUsingImagePlus(imp); pp.doSingleGridLayout();
	 * 
	 * @param mode
	 */
	public Praparat(ViewMode mode) {
		if (mode == null) {
			this.mode = ViewMode.Normal;
		} else {
			this.mode = mode;
		}
		initComponent();
	}

	public void adjustContrastFromMouseAction(int dragX, int dragY) {
		SlideGlass slide = getCurrentSlide();
		slide.adjustContrastFromMouseAction(dragX, dragY);
		if (isProcessSeries()) {
			ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
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
	 * 
	 * @param min
	 * @param max
	 * @param fromMouseAction
	 */
	public void adjustContrastByMinMax(double min, double max) {
		if (isProcessSeries()) {
			ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.changeWindowingByMinMax(min, max);
			}
		} else {
			SlideGlass slide = getCurrentSlide();
			slide.changeWindowingByMinMax(min, max);
		}
	}

	/**
	 * スタック全体の最適な表示レンジ（Min/Max）からWindow CenterとWindow Widthを算出し表示します。
	 */
	public void applyGlobalAutoWindow() {
		if (slides == null || slides.isEmpty())
			return;

		// 1. 真ん中のスライスを代表として選ぶ
		int midIndex = slides.size() / 2;
		SlideGlass midSlide = slides.get(midIndex);
		if (midSlide == null)
			return;

		DicomImage dcm = midSlide.getDicomImage();
		if (dcm == null)
			return;

		// 2. ピクセルデータを「確実に」ロードする
		int file_pos = isMultiFrame() ? 0 : midIndex;
		if (hasFileSource(file_pos)) {
			dcm.ensurePixelDataLoaded();
		}

		// 3. ImageProcessorを取得
		int frame_pos = isMultiFrame() ? midIndex : 0;
		ImageProcessor ip = dcm.getImageProcessor(frame_pos);

		if (ip != null) {
			// ★ 究極の修正：ImageJのヒストグラム解析を使用する！
			// 上下0.5%の外れ値（ノイズや異常に明るいピクセル）を除外して、
			// 医療画像として最も自然なコントラスト幅を自動計算させます。
			ij.plugin.ContrastEnhancer ce = new ij.plugin.ContrastEnhancer();
			ce.stretchHistogram(ip, 0.5);

			// 解析済みの最適なMin/Maxを取得
			double globalMin = ip.getMin();
			double globalMax = ip.getMax();

			// 真っ暗な画像だった場合のゼロ除算・白飛び防止ガード
			if (globalMin == globalMax) {
				globalMax = globalMin + 1.0;
			}

			// 4. 全スライドに一括適用
			adjustContrastByMinMax(globalMin, globalMax);
		}
	}

	/**
	 * for prepareSlideGlassesUsingImagePlus
	 * 
	 * スタック全体の最適な表示レンジ（Min/Max）からWindow CenterとWindow Widthを算出し、
	 * すべてのスライスのDICOMタグに統一して書き込みます。
	 * スライス移動（スクロール）時のコントラストの飛躍（チカチカ）を防止するグローバルAutoWindow調整機能です。 * @param imp
	 * コントラストを統一したいImagePlus
	 */
	public void applyGlobalAutoWindow(ImagePlus imp) {
		if (imp == null)
			return;

		// 1. ImagePlusに設定されている全体の表示範囲(Min/Max)を取得
		double displayMin = imp.getDisplayRangeMin();
		double displayMax = imp.getDisplayRangeMax();

		// 万が一Min/Maxが同一（真っ黒な画像など）の場合はゼロ除算等を防ぐために補正
		if (displayMin == displayMax) {
			displayMax = displayMin + 1.0;
		}

		// 2. Window Center と Window Width を計算
		long windowCenter = Math.round(displayMin + (displayMax - displayMin) / 2.0);
		long windowWidth = Math.round(displayMax - displayMin);

		String wcStr = String.valueOf(windowCenter);
		String wwStr = String.valueOf(windowWidth);

		// 3. 全スライスのDICOMメタデータに固定値を書き込む
		int depth = imp.getNSlices();
		for (int i = 1; i <= depth; i++) {
			GDicomTools.setTag(imp, i, "0028,1050", wcStr); // Window Center
			GDicomTools.setTag(imp, i, "0028,1051", wwStr); // Window Width
		}
	}

	@Override
	public void addMouseListener(MouseListener l) {
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		for (Integer key : slides.keySet()) {
			SlideGlass sg = slides.get(key);
			EventGlass coverGlass = (EventGlass) sg.getGlassAt(SlideGlass.EVENT_LAYER);
			coverGlass.addMouseListener(l);
		}
	}

	@Override
	public void addMouseMotionListener(MouseMotionListener l) {
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		for (Integer key : slides.keySet()) {
			SlideGlass sg = slides.get(key);
			EventGlass coverGlass = (EventGlass) sg.getGlassAt(SlideGlass.EVENT_LAYER);
			coverGlass.addMouseMotionListener(l);
		}
	}

	@Override
	public void addMouseWheelListener(MouseWheelListener l) {
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		for (Integer key : slides.keySet()) {
			SlideGlass sg = slides.get(key);
			EventGlass coverGlass = (EventGlass) sg.getGlassAt(SlideGlass.EVENT_LAYER);
			coverGlass.addMouseWheelListener(l);
		}
	}

	public List<Point2D> calcLocalizer(GeometryOfSlice bePostedCurrentSlide) {
		SlideGlass sg = getCurrentSlide();
		GeometryOfSlice localizerGeometry = new GeometryOfSlice(sg.getHeader());
		GeometryOfSlice postImageGeometry = bePostedCurrentSlide;
		LocalizerPoster localizerPoster = new IntersectVolume(localizerGeometry);
		List<Point2D> shape = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
		return shape;
	}

	private List<Point2D> calcLocalizer(SlideGlass src/* will draw */, SlideGlass target/* be posted */) {
		GeometryOfSlice localizerGeometry = new GeometryOfSlice(src.getHeader());
		GeometryOfSlice postImageGeometry = new GeometryOfSlice(target.getHeader());
		LocalizerPoster localizerPoster = new IntersectVolume(localizerGeometry);
		List<Point2D> shape = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
		return shape;
	}

	/**
	 * 
	 * @param row        from src
	 * @param col        from src
	 * @param iop        from src
	 * @param ipp        from src
	 * @param voxelSize  from src
	 * @param row_       from target
	 * @param col_       from target
	 * @param iop_       from target
	 * @param ipp_       from target
	 * @param voxelSize_ from target
	 * @return
	 */
	public List<Point2D> calcLocalizer(int row, int col, double[] iop, double[] ipp, double[] voxelSize/* x,y,z */,
			double slicethickness, int row_, int col_, double[] iop_, double[] ipp_, double[] voxelSize_,
			double slicethickness_) {
		GeometryOfSlice localizerGeometry = new GeometryOfSlice();// src
		localizerGeometry.setUp(row, col, iop, ipp, voxelSize, slicethickness);
		GeometryOfSlice postImageGeometry = new GeometryOfSlice();// target
		postImageGeometry.setUp(row_, col_, iop_, ipp_, voxelSize_, slicethickness_);
		LocalizerPoster localizerPoster = new IntersectVolume(localizerGeometry);
		List<Point2D> shape = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
		return shape;
	}

	public void callBackLocalizer() {
		// ref-study-uid
		Eyepiece eye = getEyepiece();
		if (eye == null)
			return;
		PraparatContext con = eye.getPraparatContextOf(patID, studyUID, seriesUID, sopUIDs);
		if (con == null) {
			return;
		}
		String refUid = (String) con.getContextUIDs()[4];
		// get praps which have same ref-uid
		List<Praparat> praps = eye.getAllPraparatByFrameOfReferenceUID(patID, studyUID, refUid);
		// remove previous localizers
		for (Praparat p : praps) {
			ConcurrentHashMap<Integer, SlideGlass> slides = p.slides;
			for (Integer k : slides.keySet()) {
				SlideGlass s = slides.get(k);
				s.drawLocalizer(null);
				s.repaintCanvasGlass();
			}
		}
		// show localizer on slideglass
		SlideGlass from = getCurrentSlide();
		for (Praparat p : praps) {
			// if self, skip
			if (p == this) {
				continue;
			}
			SlideGlass to = p.getCurrentSlide();
			List<Point2D> loca_geo = null;
			try {
				loca_geo = calcLocalizer(to, from);
			} catch (Exception e) {
				loca_geo = null;
			}
			to.drawLocalizer(loca_geo);
		}
	}

	public void clearCrossLines() {
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		for (Integer sgKey : slides.keySet()) {
			SlideGlass sg = slides.get(sgKey);
			CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			cg.setCrossLine(null);
			cg.repaint();
		}
	}

	private void constructSlideGlassesFromDicom(List<String> imgFiles) {
		// including only one series.
		if (imgFiles == null) {
			imgFiles = getImageFileLocations();
		}
		if (imgFiles == null || imgFiles.size() < 1) {
			logger.info("Please set file locations, Praparat::constructSeriesGlassesAsLayer");
			return;
		}
		// init
		slides = new ConcurrentHashMap<Integer, SlideGlass>();

		DICOMBackend backend = DICOMBackend.getCurrent();
		int numOfFiles = imgFiles.size();
		for (int i = 0; i < numOfFiles; i++) {
			DicomReader reader = DicomReader.newDicomReader(backend);
			reader.read(imgFiles.get(i), false);/* read only head */
			DicomObject header = reader.getHeader();
			DicomObject fmi = reader.getFileMetaInfomation();
			String sopClassUID = header.getString(Tag.SOP​Class​UID, "");
			UID tsUID = reader.checkTSUID();
			// PDF
			if (sopClassUID.equals(UID.EncapsulatedPDFStorage.uid())) {
				this.isPDF = true;
				/*
				 * PDF to one series.
				 */
				loadSlideGlassFromPDF(imgFiles.get(i), header, fmi, backend);
				// PDF is one series, break here.
				break;
			}
			/*
			 * isMultiFrame 1.General image types do not have NumberOfFrames tag.(means -1).
			 * 2.3d sequence MRI, number of frame is 1 (of each image).
			 */
			DicomImage dcm = DicomImage.newDicomImage(imgFiles.get(i), header, fmi, tsUID, backend);
			this.isMultiFrame = dcm.isMultiFrame();
			this.isMultiFrame = this.isMultiFrame && dcm.getNumOfFrames() > 1;

			// single frame
			if (!isMultiFrame) {
				loadSlideGlassFromSimpleDicom(imgFiles.get(i), backend, tsUID);
			} else {
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

		if (slides != null && slides.size() > 1) {
			List<Integer> keys = new ArrayList<>(slides.keySet());
		    java.util.Collections.sort(keys);
		    List<SlideGlass> slideList = new ArrayList<>();
		    for(Integer k : keys) {
		        slideList.add(slides.get(k));
		    }
			/*
			 * sort images via IOP and IPP.
			 */
			organizeMultiDimensionalSlides(slideList);
		}
	}

	/**
	 * Attention: Will use large physical memory. 
	 * This method is only used for
	 * single pop-up view or test purpose. Dicom attributes keeps minimally.
	 */
	private void constructSlideGlassesFromImagePlus(ImagePlus images, boolean sortCZT) {
		if (images == null || images.getStackSize() < 1) {
			throw new IllegalArgumentException(
					"Images is null or empty, Praparat::constructSeriesGlassesAsLayerUsingImagePlus");
		}
		// init
		viewPanel.removeAll();
		slides = new ConcurrentHashMap<Integer, SlideGlass>();
		boolean secondaryUse = false;
		String sopClassUID = GDicomTools.getTag(images, "0008,0016");
		String instUID = GDicomTools.getTag(images, "0008,0018");
		if (sopClassUID == null || instUID == null) {
			secondaryUse = true;
		}
		
		HashMap<Integer, DicomImage> ds = ImageUtils.imagePlusToDcm(images, secondaryUse);
		for (int i = 0; i < ds.size(); i++) {
			SlideGlass sg = new SlideGlass(this, ds.get(i));
			slides.put(i, sg);
		}

		if (slides != null && slides.size() > 1) {
			// ConcurrentHashMapの値をそのままリスト化せず、キー順に並び替える
		    List<Integer> keys = new ArrayList<>(slides.keySet());
		    java.util.Collections.sort(keys);
		    List<SlideGlass> slideList = new ArrayList<>();
		    for(Integer k : keys) {
		        slideList.add(slides.get(k));
		    }
		    /*
			 * sort images via IOP and IPP.
			 */
		    if (sortCZT) {
				organizeMultiDimensionalSlides(slideList);
		    } else {
				this.nChannels = images.getNChannels();
				this.nSlices = images.getNSlices();
				this.nFrames = images.getNFrames();
		    }
		}else {
			this.nChannels = images.getNChannels();
			this.nSlices = images.getNSlices();
			this.nFrames = images.getNFrames();
		}

		loadRoisFromDB();

		if (lut != null) {
			for (Integer pos : slides.keySet()) {
				slides.get(pos).setLUT(lut);
			}
		}
	}

	private void constructSlideGlassesFromPraparat(Praparat p) {
		if (p == null) {
			return;
		}
		ConcurrentHashMap<Integer, SlideGlass> srcSlides = p.getAllSlides();
		if (srcSlides == null || srcSlides.size() < 1) {
			System.out.println("Slides have no images...");
			return;
		}
		// init
		removeSlide(currentSlice);
		this.slides = new ConcurrentHashMap<Integer, SlideGlass>();

		isMultiFrame = p.isMultiFrame();

		Set<Integer> keys = srcSlides.keySet();
		for (Integer k : keys) {
			// init slide from another slides to set this praparat.
			SlideGlass sg = srcSlides.get(k);
			SlideGlass newsg = new SlideGlass(this, sg.getDicomImage());
			this.slides.put(k, newsg);
		}
		if (Utils.isDebug) {
			Log.logger.fine(this.slides.size() + " images loaded.");
		}

		// File location
		setImageFileLocations(p.getImageFileLocations());

		loadRoisFromDB();

		if (lut != null) {
			for (Integer pos : this.slides.keySet()) {
				this.slides.get(pos).setLUT(lut);
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
//		int samples = header.getInt(Tag.SamplesPerPixel, 1);
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

		PDFReader pdfReader = new PDFReader(new File(path2dcm)/* read dicom */);
		ExecutorService executor = Executors.newFixedThreadPool(Utils.availableProcessors());
		List<Future<SlideGlass>> futures = new ArrayList<>();

		isMultiFrame = true;// always treats multi
		isPDF = true;// fail safe
		boolean isThumbnail = getViewMode() == ViewMode.Thumbnail;
		int size = isThumbnail ? 1 : pdfReader.getPDFPageCount();
		// if thumbnail, load only one frame
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
				instHeader.setInt(Tag.Bits​Stored, VR.US, 8);
				instHeader.setInt(Tag.High​Bit, VR.US, 7);
				instHeader.setString(Tag.Photometric​Interpretation, VR.CS, "RGB");
				instHeader.setInt(Tag.Planar​Configuration, VR.US, 0);
				DicomImage frame = DicomImage.newDicomImage(path2dcm, instHeader, fmi, UID.ExplicitVRLittleEndian,
						backend);
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
	 * NIfTIやマルチエコーMRIなどの多次元スライスを解析し、次元(C,Z,T)の自動算出と1D配列への再配置を行う
	 */
	private void organizeMultiDimensionalSlides(List<SlideGlass> slideList) {
		if (slideList == null || slideList.isEmpty())
			return;
		
		// ★ 追加: すべてのスライスでIOPが一致するかチェック（回転MIP対応）
		double[] iop = slideList.get(0).getHeader().getDoubles(Tag.ImageOrientationPatient);
		boolean isIopConsistent = true;
		if (iop != null && iop.length == 6) {
			for (int i = 1; i < slideList.size(); i++) {
				double[] currentIop = slideList.get(i).getHeader().getDoubles(Tag.ImageOrientationPatient);
				if (currentIop == null || currentIop.length != 6) {
					isIopConsistent = false;
					break;
				}
				// 誤差(1e-4)を許容してIOPの変化を検知
				for (int j = 0; j < 6; j++) {
					if (Math.abs(iop[j] - currentIop[j]) > 1e-4) {
						isIopConsistent = false;
						break;
					}
				}
				if (!isIopConsistent)
					break;
			}
		} else {
			isIopConsistent = false;
		}

		// ★ 追加: IOPが異なる（回転している）場合は、Z座標でのグループ化を諦め、連番で並べる
		if (!isIopConsistent) {
			Log.logger.info("Inconsistent IOP detected. Treating as standard sequential slices (e.g., Rotating MIP).");
			slideList.sort((sg1, sg2) -> {
				int inst1 = sg1.getHeader().getInt(Tag.InstanceNumber, 0);
				int inst2 = sg2.getHeader().getInt(Tag.InstanceNumber, 0);
				return Integer.compare(inst1, inst2);
			});
			slides.clear();
			for (int i = 0; i < slideList.size(); i++) {
				slides.put(i, slideList.get(i));
			}
			this.nSlices = slideList.size();
			this.nChannels = 1;
			this.nFrames = 1;
			SwingUtilities.invokeLater(() -> updateSlidersVisibility());
			return;
		}

		// 1. 最初のスライスのIOPを用いて、外積から法線ベクトル（スタック進行方向）を算出
//		System.out.println(Arrays.toString(iop));
		final double nx, ny, nz;
		if (iop != null && iop.length == 6) {
			nx = iop[1] * iop[5] - iop[2] * iop[4];
			ny = iop[2] * iop[3] - iop[0] * iop[5];
			nz = iop[0] * iop[4] - iop[1] * iop[3];
		} else {
			nx = 0;
			ny = 0;
			nz = 1; // IOPが無い場合のフォールバック
		}

		// 2. 各スライスの「法線ベクトル上の絶対位置（pos）」と「インスタンス番号」を計算して一時保持
		class SlidePos {
			SlideGlass sg;
			double pos;
			@SuppressWarnings("unused")
			int instNo;
			SlidePos(SlideGlass sg, double pos, int instNo) {
				this.sg = sg;
				this.pos = pos;
				this.instNo = instNo;
			}
		}

		List<SlidePos> spList = new ArrayList<>();
		for (int i = 0; i < slideList.size(); i++) {
			SlideGlass sg = slideList.get(i);
			double[] ipp = sg.getHeader().getDoubles(Tag.ImagePositionPatient);
			double pos = 0;
			if (ipp != null && ipp.length >= 3) {
				pos = (ipp[0] * nx) + (ipp[1] * ny) + (ipp[2] * nz);
//				System.out.println("IOP dot : "+pos);
			} else {
				// 動画マルチフレーム用
				// IPPが存在しないデータの場合、全てが pos=0 となり「チャンネル」と誤認されるのを防ぐため、
				// 元の並び順（インデックス i ）を仮想のZ座標として割り当てる
		        // 意図的に設定されているInstanceNumberを仮想Z座標(pos)として利用する
		        pos = sg.getHeader().getInt(Tag.InstanceNumber, i);
			}
			int instNo = sg.getHeader().getInt(Tag.InstanceNumber, 0);
			spList.add(new SlidePos(sg, pos, instNo));
		}

		// 3. 空間位置（法線ベクトル方向の距離）で昇順ソートする（スライダを進めると法線ベクトルの方向へ進む）
		spList.sort((o1, o2) -> Double.compare(o1.pos, o2.pos));

		// 4. 位置の重複を許容範囲（epsilon）でグループ化し、スライス数とチャンネル数の次元を自動算出する
		List<List<SlideGlass>> sliceGroups = new ArrayList<>();
		List<SlideGlass> currentGroup = new ArrayList<>();

		double EPSILON = 1e-3; // 0.001mm以下の誤差は「全く同じ位置にあるスライス」とみなす
		double lastPos = spList.isEmpty() ? 0 : spList.get(0).pos;

		for (SlidePos sp : spList) {
			if (currentGroup.isEmpty() || Math.abs(sp.pos - lastPos) < EPSILON) {
				currentGroup.add(sp.sg);
			} else {
				sliceGroups.add(currentGroup);
				currentGroup = new ArrayList<>();
				currentGroup.add(sp.sg);
				lastPos = sp.pos;
			}
		}
		if (!currentGroup.isEmpty()) {
			sliceGroups.add(currentGroup);
		}

		// ★ 次元の確定
		this.nSlices = sliceGroups.size(); // ユニークな空間位置の数がそのままスライス数になる
		this.nChannels = 1;
		this.nFrames = 1; // 現状は時間をチャンネルとして吸収させる

		for (List<SlideGlass> group : sliceGroups) {
			if (group.size() > this.nChannels) {
				this.nChannels = group.size(); // 同じ位置に重なっている最大枚数をチャンネル数とする
			}
		}

		Log.logger.info("Auto-detected Dimensions -> Slices: " + nSlices + ", Channels: " + nChannels);

		// 5. 1Dマップ（slides）への再マッピング
		slides.clear();
		for (int sliceIdx = 0; sliceIdx < sliceGroups.size(); sliceIdx++) {
			List<SlideGlass> group = sliceGroups.get(sliceIdx);

			// ★ 安全装置を組み込んだ堅牢な多段ソート
			group.sort((sg1, sg2) -> {
				// 第1キー: InstanceNumber
				int inst1 = sg1.getHeader().getInt(Tag.InstanceNumber, 0);
				int inst2 = sg2.getHeader().getInt(Tag.InstanceNumber, 0);
				if (inst1 != inst2) {
					return Integer.compare(inst1, inst2);
				}

				// 第2キー: AcquisitionTime（撮像時間）
				String time1 = sg1.getHeader().getString(Tag.AcquisitionTime, "");
				String time2 = sg2.getHeader().getString(Tag.AcquisitionTime, "");
				if (!time1.isEmpty() && !time2.isEmpty() && !time1.equals(time2)) {
					return time1.compareTo(time2);
				}

				// 最終手段（第3キー）: SOPInstanceUID（文字列としてのソート）
				// UIDは連番で生成されることが多いため、最低限「全スライスで一貫した並び」を保証できる
				String uid1 = sg1.getSOPInstanceUID();
				String uid2 = sg2.getSOPInstanceUID();
				if (uid1 != null && uid2 != null) {
					return uid1.compareTo(uid2);
				}

				return 0; // 完全に同一とみなす
			});

			for (int c = 0; c < group.size(); c++) {
				// Index = t * (nChannels * nSlices) + sliceIdx * nChannels + c
				int index = (0 * (this.nChannels * this.nSlices)) + (sliceIdx * this.nChannels) + c;
				slides.put(index, group.get(c));
			}
		}
		// 最後に次元情報をUIに反映させる
		SwingUtilities.invokeLater(() -> updateSlidersVisibility());
	}
	
	/**
     * 指定した次元(Slice, Channel, Time)のスライダーを一歩進める/戻す
     * @param dimName 次元名 ("Slice", "Channel", "Time")
     * @param step 増減量 (1, -1など)
     */
    public void stepDimension(String dimName, int step) {
        int val = 0;
        int max = 0;
        CineSlider targetSlider = null;

        // 現在の値と最大枚数、対象のスライダーを特定
        if ("Slice".equals(dimName)) {
            val = currentZ; max = nSlices; targetSlider = slider;
        } else if ("Channel".equals(dimName)) {
            val = currentC; max = nChannels; targetSlider = channelSlider;
        } else if ("Time".equals(dimName)) {
            val = currentT; max = nFrames; targetSlider = frameSlider;
        }

        // スライダーが存在しない、または1枚しかない場合は何もしない
        if (targetSlider == null || max <= 1) return;

        // 次の値を計算（ループ処理）
        int nextVal = val + step;
        if (nextVal < 0) nextVal = max - 1;
        if (nextVal >= max) nextVal = 0;

        // ★ 重要：スライダーの値を更新。これにより CineSlider 内のイベント経由で画像が更新される
        targetSlider.setPosition(nextVal);
    }

	public void doFilmGridLayout(Integer col) {
		if (col == null) {
			col = minimumGridCol;// 5
		}
		if (this.mode != ViewMode.FilmGrid && this.mode != ViewMode.Normal) {
			logger.warning("You are not able to show gridView in this mode::" + this.mode);
			gridViewOn(false);
			return;
		}
		// ★ 追加：多次元データの場合は、フリーズ防止のためFilmGrid表示を許可しない
		if (isMultiDimensional()) {
			Log.logger.warning("FilmGrid view is disabled for multi-dimensional data to prevent freezing.");
			gridViewOn(false);
			return;
		}
		gridViewOn(true);
		setCursor(new Cursor(Cursor.WAIT_CURSOR));
		setFilmGridColumns(col);
		viewPanel.removeAll();
		gridScrollPane = new SlideGlassGrid(this, col, true/* GridLayer */);
		viewPanel.add(gridScrollPane, 0);// setParent
		gridScrollPane.populateView();// set images
		gridScrollPane.update();// update layout and show current images
		setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
	}

	public void doSingleGridLayout() {
		gridViewOn(false);
		updateViewPanel();
		showFirstImage();
	}

	public ConcurrentHashMap<Integer, SlideGlass> getAllSlides() {
		if (slides != null && slides.size() < 1) {
			return null;
		}
		return slides;
	}

	public PraparatViewControlPanel getController() {
		return pvcp;
	}

	public SlideGlass getCurrentSlide() {
		if (slides == null) {
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
	
	public int getCurrentSlideCZTIndex() {
		return currentT * (nChannels * nSlices) + currentZ * nChannels + currentC;
	}

	/**
	 * check Viewer2D selecting tool.
	 * 
	 * @return
	 */
	public int getCurrentViewerToolType() {
		if(Viewer2DScreen.getInstance() != null) {
			return Viewer2DScreen.getInstance().getCurrentToolType();
		}
		return Viewer2DToolBar.NONE;
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
	 * ImagePlus (全体のコンテナ) ├── Calibration (全体共通の単位・スケール) ├── Properties
	 * (全体のメタデータ：DICOMヘッダーなど) └── ImageStack (スライスの集合体) ├── Slice 1 │ ├──
	 * ImageProcessor (ピクセルデータ + 表示Min/Max) │ └── Slice Label (このスライス固有のメタデータ文字列)
	 * ├── Slice 2 │ ├── ImageProcessor ... │ └── Slice Label ... └── Slice N ...
	 * 
	 * 
	 * @return imageplus
	 */
//	public ImagePlus getImagePlus() {
//
//		if (slides == null || slides.size() < 1) {
//			return null;
//		}
//
//		if (isMultiFrame()) {
//			logger.info("This series multi frame. Will you continue convert to imageplus ? It will take a long time...");
//			int res = JOptionPane.showConfirmDialog(this,
//					"This series is multi-frame. Take long time converting to imageplus. Still will you continue ?");
//			if (res != JOptionPane.OK_OPTION) {
//				return null;
//			}
//		}
//
//		ImagePlus replica = null;
//		ImageStack stack = new ImageStack();
//		String info = "";
//
//		// 1. スライドをインデックス順にソートして処理する
//		List<Integer> sortedIndices = new ArrayList<>(slides.keySet());
//		Collections.sort(sortedIndices);
//
//		if (!isMultiFrame()) {
//			Calibration orgCal = null;
//			for (int index : sortedIndices) {
//				SlideGlass sg = slides.get(index);
//				DicomImage frame = sg.getDicomImage();
//				if (orgCal == null) {
//					orgCal = sg.getOriginalCalibration();
//				}
//				if (hasFileSource(index)) {
//					if (!frame.ensurePixelDataLoaded()) {
//						continue;
//					}
//				}
//
//				ImagePlus imp = GDicomTools.dcmImgToImagePlus(frame, orgCal);
//				ImageProcessor ip = imp.getProcessor();
//				if (ip instanceof ColorProcessor) {
//					ip.snapshot();// keep original pixels
//				}
//				/*
//				 * In this case, always only one slice. It case use getInfoProperty().
//				 */
//				String headerInfo = imp.getInfoProperty();
//				String sliceLabel = imp.getStack().getSliceLabel(1);
//
//				// 1スライスしかない場合に備えて、最初の1枚目のヘッダー情報を全体の代表プロパティ用に保持しておく
//				if (info.isEmpty() && headerInfo != null) {
//					info = headerInfo;
//				}
//
//				/*
//				 * if header has "\n" in head (at index 0), DicomTools.getTag() return null.
//				 */
//				// header = imp.getStack().getSliceLabel(1);
//				stack.addSlice(sliceLabel, imp.getProcessor());
//			}
//			replica = new ImagePlus("stack", stack);
//			/*
//			 * if ImagePlus has only one slice, header is updated by setProp("Info", hdr).
//			 */
////			if (replica.getNSlices() == 1) {
////				replica.setProp("Info", info);
////			}
//			if (!info.isEmpty()) {
//				replica.setProperty("Info", info);
//			}
//			/*
//			 * TODO Now, fail safe ? But this is not suitable for EnhancedMultiFrame DICOM.
//			 */
//			replica.setCalibration(orgCal);
//			return replica;
//		} else {
//			if (hasFileSource(0)) {
//				Calibration orgCal = getCurrentSlide().getOriginalCalibration();
//				String path = getImageFileLocations().get(0);
//				DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
//				reader.read(path, false);
//				DicomObject header = reader.getHeader();
//				DicomImage dcm = DicomImage.newDicomImage(path, header, reader.getFileMetaInfomation(),
//						reader.checkTSUID(), DICOMBackend.getCurrent());
//				ImagePlus imp = GDicomTools.dcmImgToImagePlus(dcm, orgCal);
//				return imp;
//			}
//		}
//		return null;
//	}
	
	/**
	 * return slides as imageplus.
	 * 
	 * ImagePlus (全体のコンテナ) ├── Calibration (全体共通の単位・スケール) ├── Properties
	 * (全体のメタデータ：DICOMヘッダーなど) └── ImageStack (スライスの集合体) ├── Slice 1 │ ├──
	 * ImageProcessor (ピクセルデータ + 表示Min/Max) │ └── Slice Label (このスライス固有のメタデータ文字列)
	 * ├── Slice 2 │ ├── ImageProcessor ... │ └── Slice Label ... └── Slice N ...
	 * 
	 * 
	 * @return imageplus
	 */
	public ImagePlus getImagePlus() {
		if (slides == null || slides.isEmpty()) return null;
		
		if(!isMultiFrame()) {
			ImageStack stack = new ImageStack();
			Calibration orgCal = slides.get(0).getOriginalCalibration();
			String globalInfo = "";

			// ImageJ dimension order: C -> Z -> T
			for (int t = 0; t < nFrames; t++) {
				for (int z = 0; z < nSlices; z++) {
					for (int c = 0; c < nChannels; c++) {
						int index = t * (nChannels * nSlices) + z * nChannels + c;
						SlideGlass sg = slides.get(index);
						if (sg == null) continue;
						DicomImage dcmImg = sg.getDicomImage();
						if (hasFileSource(index)) {
							if (!dcmImg.ensurePixelDataLoaded()) {
								continue;
							}
						}

						// 元のコードと同様に ImagePlus を経由して、ピクセルとメタデータを一括取得
						// これにより、個別スライスの全DICOMタグが SliceLabel に入る
						ImagePlus sliceImp = GDicomTools.dcmImgToImagePlus(dcmImg, orgCal);
						String sliceLabel = sliceImp.getStack().getSliceLabel(1/*always*/);
						ImageProcessor ip = sliceImp.getProcessor();
						if (ip instanceof ColorProcessor) {
							ip.snapshot();// keep original pixels
						}

						// 全体プロパティ（Info）用に最初の1枚だけヘッダーを保持
						if (globalInfo.isEmpty()) {
							globalInfo = sliceImp.getInfoProperty();
						}

						stack.addSlice(sliceLabel, ip);
					}
				}
			}

			ImagePlus replica = new ImagePlus("Praparat_HyperStack", stack);
			if (!globalInfo.isEmpty()) replica.setProperty("Info", globalInfo);
			replica.setCalibration(orgCal);

			if (nChannels > 1 || nSlices > 1 || nFrames > 1) {
				replica.setDimensions(nChannels, nSlices, nFrames);
				replica.setOpenAsHyperStack(true);
			}
			return replica;
		}else {
			if (hasFileSource(0)) {
				Calibration orgCal = getCurrentSlide().getOriginalCalibration();
				String path = getImageFileLocations().get(0);
				DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
				reader.read(path, false);
				DicomObject header = reader.getHeader();
				DicomImage dcm = DicomImage.newDicomImage(path, header, reader.getFileMetaInfomation(),
						reader.checkTSUID(), DICOMBackend.getCurrent());
				ImagePlus imp = GDicomTools.dcmImgToImagePlus(dcm, orgCal);
				return imp;
			}
		}
		return null;
	}

	/**
	 * 指定した C, T のシリーズ（Zスタック）を、個別メタデータを保持したまま抽出します。
	 */
	public ImagePlus getImagePlus(int targetC/*0-based*/, int targetT/*0-based*/) {
		if (slides == null || slides.isEmpty()) return null;

		if (targetC < 0 || targetC >= nChannels) targetC = currentC;
		if (targetT < 0 || targetT >= nFrames) targetT = currentT;

		ImageStack stack = new ImageStack();
		Calibration orgCal = slides.get(0).getOriginalCalibration();
		String globalInfo = "";

		for (int s = 0; s < nSlices; s++) {
			/*0-based*/
			int index = targetT * (nChannels * nSlices) + s * nChannels + targetC;
			SlideGlass sg = slides.get(index);
			if (sg == null) continue;
			DicomImage dcmImg = sg.getDicomImage();
			if (hasFileSource(index)) {
				if (!dcmImg.ensurePixelDataLoaded()) {
					continue;
				}
			}

			ImagePlus sliceImp = GDicomTools.dcmImgToImagePlus(dcmImg, orgCal);
			
//			System.out.println("SLICE IPP:"+GDicomTools.getTag(sliceImp, Tag.ImagePositionPatient));
			
			String sliceLabel = sliceImp.getStack().getSliceLabel(1/*always*/);
			
//			System.out.println(sliceLabel);//OK
			
			ImageProcessor ip = sliceImp.getProcessor();

			if (globalInfo.isEmpty()) {
				globalInfo = sliceImp.getInfoProperty();
			}

			stack.addSlice(sliceLabel, ip);
		}

		ImagePlus replica = new ImagePlus("Series_C" + targetC + "_T" + targetT, stack);
		if (!globalInfo.isEmpty() && stack.getSize()==1) {
			replica.setProperty("Info", globalInfo);
		}
		replica.setCalibration(orgCal);
		replica.setDimensions(1, nSlices, 1);
		
		for(int i=0; i< replica.getNSlices(); i++) {
			replica.setPosition(GDicomTools.getRealIndex(replica, i+1));
//			System.out.println("replica IPP:"+GDicomTools.getTag(replica, Tag.ImagePositionPatient));
		}

		return replica;
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
			imp.setPosition(c, z, t);
			return stack2;
		} else {
			return imp.getStack();
		}
	}

	/**
	 * return : ViewPanel Width (same as SlideGlass size border insets included)
	 */
	public int getImageScreenSizeX() {
		if (!showGridViewOn) {
			return getViewPanelWidth();
		} else {
			return gridScrollPane.calcCellSize(getViewPanelWidth());
		}
	}

	/**
	 * return : ViewPanel Height (same as SlideGlass size border insets included)
	 */
	public int getImageScreenSizeY() {
		if (!showGridViewOn) {
			return getViewPanelHeight();
		} else {
			return gridScrollPane.calcCellSize(getViewPanelWidth()/* keep use width */);
		}
	}

	public HashMap<String, Object> getInfoSet() {
		HashMap<String, Object> infoset = new HashMap<>();
		infoset.put(KEY_PadID, patID);
		infoset.put(KEY_StudyUID, studyUID);
		infoset.put(KEY_SeriesUID, seriesUID);
		infoset.put(KEY_SopUIDs, sopUIDs);// string[]
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
	 * 
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
		int[] czt = getSlidePositionCZTArray(slide);
		return czt[1];
	}
	
	public int getSlidePositionOnCZTIndex(SlideGlass slide) {
		int[] czt = getSlidePositionCZTArray(slide);
		return calcCztIndex(czt);
	}
	
	private int calcCztIndex(int[] czt) {
		int c = czt[0];
		int z = czt[1];
		int t = czt[2];
		return t * (nChannels * nSlices) + z * nChannels + c;
	}
	
	public int[] getSlidePositionCZTArray(SlideGlass slide) {
	    ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
	    if (slides == null) return new int[]{-1, -1, -1};

	    for (Entry<Integer, SlideGlass> entry : slides.entrySet()) {
	        if (entry.getValue() == slide) {
	            int index = entry.getKey();
	            return getSlidePositionCZTArray(index);
	        }
	    }
	    return new int[]{-1, -1, -1};
	}
	
	public int[] getSlidePositionCZTArray(int index) {
	    ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		// 線形インデックス czt_index を各次元に分解
		// 公式: czt_index = t * (nChannels * nSlices) + z * nChannels + c
	    if (slides == null) return new int[]{-1, -1, -1};
        int c = index % nChannels;
        int z = (index / nChannels) % nSlices;
        int t = index / (nChannels * nSlices);
        
        return new int[]{c, z, t};
	}

	/**
	 * 
	 * @param sopUID
	 * @return slide pos : 0 to n-1.
	 */
	public int getSlidePosition(String sopUID) {
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		if (slides == null) {
			return -1;
		}
		for (int p : slides.keySet()) {
			SlideGlass sg = slides.get(p);
			String sopUID_ = sg.getSOPInstanceUID();
			if (sopUID.equals(sopUID_)) {
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
	 * 
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
		 * in equals(), the order may change because of sorting. Here, we pass a copy
		 * and make no changes to the original.
		 */
		uids[3] = sopUIDs != null ? sopUIDs.clone() : null;// String[],
		uids[4] = frameOfReferenceUID;
		return uids;
	}

	public ArrayList<RoiObj> getRoiAt(String sopUID) {
		for (int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			if (sg.getSOPInstanceUID().equals(sopUID)) {
				CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
				return cg.getRoiSet();
			}
		}
		return null;
	}

	public ArrayList<RoiObj> getRois() {
		ArrayList<RoiObj> rois = new ArrayList<>();
		for (int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			ArrayList<RoiObj> roisOnSlide = cg.getRoiSet();
			if (roisOnSlide != null && roisOnSlide.size() > 0) {
				rois.addAll(roisOnSlide);
			}
		}
		return rois;
	}

	public ArrayList<RoiObj> getSelectedRois() {
		ArrayList<RoiObj> rois = new ArrayList<>();
		for (int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			ArrayList<RoiObj> roisOnSlide = cg.getRoiSet();
			if (roisOnSlide != null && roisOnSlide.size() > 0) {
				for (RoiObj r : roisOnSlide) {
					if (r.isSelected()) {
						rois.add(r);
					}
				}
			}
		}
		return rois;
	}

	public Modality getModality() {
		return this.modality;
	}

	private String concatenationOfUIDStrings() {
		Object[] uids = getUIDs();
		String str = "";
		for (Object u : uids) {
			if (u == null) {
				continue;
			}
			if (u instanceof String) {
				str += u;
			} else if (u instanceof String[]) {
				List<String> sopUIDs = Arrays.asList((String[]) u);
				if (sopUIDs == null || sopUIDs.isEmpty()) {
					continue;
				}
				if (sopUIDs.contains(null)) {
					continue;
				}
				Collections.sort(sopUIDs);
				for (String s : sopUIDs) {
					str += s;
				}
			}
		}
		return str.length() == 0 ? null : str;
	}

	public ViewMode getViewMode() {
		return this.mode;
	}

	/**
     * 現在選択されているツールタイプを取得します。
     * （SlideGlassMouseListener から呼ばれるメソッドです）
     */
	public int getViewer2DToolType() {
		// 1. ローカルツールが設定されていれば、それを最優先する（単独モード）
		if (this.localToolType != -1) {
			return this.localToolType;
		}
		// 2. 設定されていなければ、従来通りメイン画面のツール状態を取りに行く（通常モード）
		int type = getCurrentViewerToolType();
		// 3. どちらも存在しない場合の安全なデフォルト値
		return type == Viewer2DToolBar.NONE ? Viewer2DToolBar.Windowing : type;
	}

	public void gridViewOn(boolean showFilmGrid) {
		if (this.mode == ViewMode.FilmGrid) {
			this.showGridViewOn = true;
			return;
		}
		if (this.mode == ViewMode.Normal) {
			if (showFilmGrid == false) {
				if (gridScrollPane != null) {
					Component[] cons = viewPanel.getComponents();
					for (Component c : cons) {
						if (c instanceof SlideGlassGrid) {
							viewPanel.remove(c);
							viewPanel.revalidate();
							break;
						}
					}
				}
				gridScrollPane = null;
			}
			this.showGridViewOn = showFilmGrid;
		} else {// single grid and thumbnail
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
		if (this.mode == null) {
			throw new NullPointerException();
		}
		setOpaque(true);// visible
		prevW = getWidth();
		prevH = getHeight();
		// init slides
		slides = new ConcurrentHashMap<Integer, SlideGlass>();
		setLayout(new BorderLayout());
		setBorder(BorderMaker.make(this, false));
		pvcp = new PraparatViewControlPanel(this);// pixelInfoLabel

		sliderPanel = new JPanel();
		sliderPanel.setLayout(new GridLayout(0, 1)); // 行数は動的、1列
		slider = new CineSlider(this, "Slice"); // ★ 引数にラベルを追加できるよう後でCineSliderも改修します
		channelSlider = new CineSlider(this, "Channel");
		frameSlider = new CineSlider(this, "Time");

		// 初期状態ではメインのスライダーのみ（互換性維持のため）
		sliderPanel.add(slider);

		/*
		 * SlideGlass parent component
		 */
		viewPanel = new JPanel();
		viewPanel.setName("ViewPanel");
		viewPanel.setBackground(Color.BLACK);// debug purpose
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

		if (mode == ViewMode.Normal) {
			add(pvcp, BorderLayout.NORTH);
			add(sliderPanel, BorderLayout.SOUTH);
			setFocusable(true);
			setRequestFocusEnabled(true);
		}

		if (mode == ViewMode.SingleGrid) {
			add(pvcp, BorderLayout.NORTH);
			add(sliderPanel, BorderLayout.SOUTH);
			setFocusable(true);
			setRequestFocusEnabled(true);
			pvcp.getFilmGridBtn().setEnabled(false);
		}

		if (mode == ViewMode.FilmGrid) {
			add(pvcp, BorderLayout.NORTH);
			/* No slider */
			setFocusable(true);
			setRequestFocusEnabled(true);// fail safe?
			/* see also setTextVisible(). This is called when after preparedImages */
			pvcp.enableShowInfo(false);
			pvcp.enableProcessSeries(false);
		}

		if (mode == ViewMode.Thumbnail) {
			/* No controller and slider */
			Dimension size = new Dimension(ThumbnailSize, ThumbnailSize);
			setViewPanelSize(ThumbnailSize, ThumbnailSize);
			this.setPreferredSize(size); // FlowLayout用
			this.setMinimumSize(size);
			setFocusable(true);
			setRequestFocusEnabled(true);
			pvcp.enableShowInfo(false);
			pvcp.enableShowROI(false);
		}

		if (mode == ViewMode.MPR) {
			add(pvcp, BorderLayout.NORTH);
			add(sliderPanel, BorderLayout.SOUTH);
			setFocusable(true);
			setRequestFocusEnabled(true);// fail safe?
			/* filmGrid is denied */
			pvcp.getFilmGridBtn().setEnabled(false);
		}
	}

	public void initSlideGlassSize(int w, int h) {
		if (slides == null || slides.size() == 0) {
			return;
		}
		SlideGlass target = slides.get(currentSlice);
		target.setSize(w, h);
		target.repaint();
		// set origin all slides
		for (Integer key : slides.keySet()) {
			SlideGlass sl = slides.get(key);
			if (sl == target)
				continue;
			sl.setSize(w, h);
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
	 * boolean isMultiFrame = dcmimg.isMultiFrame(); isMultiFrame = isMultiFrame &&
	 * dcmimg.getNumOfFrames() > 1;
	 * 
	 * @return
	 */
	public boolean isMultiFrame() {
		return this.isMultiFrame;
	}
	
	/**
	 * 現在のデータが多次元（マルチチャンネルまたはマルチタイムフレーム）かどうかを判定する
	 */
	public boolean isMultiDimensional() {
		return nChannels > 1 || nFrames > 1;
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
	
	// Praparat.java 内に追加
	private void setWaitCursor(boolean isWait) {
	    if (isWait) {
	        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	    } else {
	        // ユーザーの要望通りクロスヘアに戻す
	        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	    }
	}

	public void loadRoiToCurrentSlideGlass() {
		SlideGlass sg = getCurrentSlide();
		sg.loadRoiFromDB();
	}

	public void loadRoisFromDB() {
		if (slides == null || slides.size() == 0) {
			return;
		}
		if (getViewMode() != ViewMode.Thumbnail) {
			for (Integer pos : this.slides.keySet()) {
				this.slides.get(pos).loadRoiFromDB();
			}
		}
	}

	public void addRoi(int slidePos, RoiObj r) {
		if (slidePos < 0 || slidePos >= slides.size()) {
			System.out.println("Praparat: this slide position invalid...cannot addRoi().");
			return;
		}
		SlideGlass sg = slides.get(slidePos);
		sg.addRoi(r);
	}
	
	public void loadSeries(DICOMNode seriesNode) {
		if(seriesNode == null || seriesNode.getLevel()!=DICOMNode.SERIES) {
			Log.logger.log(Level.WARNING,"Cannot prepare slideglasses, no series node is inputed.");
			return;
		}
		String pid = seriesNode.getData(DICOMNode.PatientID);
		String studyUid = seriesNode.getData(DICOMNode.StudyInstanceUID);
		String seriesUid = seriesNode.getData(DICOMNode.SeriesInstanceUID);
		loadSeries(pid, studyUid, seriesUid, null);
	}
	
	public void loadSeries(Praparat p) {
		if (p == null) {
			logger.log(Level.SEVERE, "Can not load images from this Praparat.");
			return;
		}
		// Copy LUT from source Praparat if we don't have one yet
		LUT srcLut = p.getLUT();
		if (this.lut == null && srcLut != null) {
			this.lut = srcLut;
		}
		HashMap<String, Object> info = p.getInfoSet();
		String patID = (String) info.get("PatientID");
		String studyUID = (String) info.get("StudyInstanceUID");
		String seriesUID = (String) info.get("SeriesInstanceUID");
		String[] sopUIDs = (String[]) info.get("SOPInstanceUIDs");
		List<String> pathToImages = p.getImageFileLocations();

		if (pathToImages == null || pathToImages.size() == 0) {
			logger.warning("prap needs path to images..., return.");
			return;
		}
		viewPanel.removeAll();
		setInfo(patID, studyUID, seriesUID, sopUIDs, pathToImages);
		
		// ★ 複製元の多次元プロパティを引き継ぐ
		this.nSlices = p.nSlices;
		this.nChannels = p.nChannels;
		this.nFrames = p.nFrames;

		constructSlideGlassesFromPraparat(p);
		applyGlobalAutoWindow();// before slider.initContext
		currentSlice = -1;
		
		constructSlideGlassesFromPraparat(p);
		applyGlobalAutoWindow();// before slider.initContext
		currentSlice = -1;
		updateSlidersVisibility();
		if (Utils.isDebug) {
			System.out.println(slides.size() + " images loaded.");
		}
	}

	public void loadSeries(String patID, String studyUID, String seriesUID, ArrayList<String> sopUIDs,
			ArrayList<String> pathToImages) {
		String[] sopUids = sopUIDs.toArray(new String[sopUIDs.size()]);
		loadSeries(patID, studyUID, seriesUID, sopUids, pathToImages);
	}

	public void loadSeries(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
		ArrayList<String> pathToImages = null;
		DatabaseHandler db = DatabaseHandler.getInstance();// .getDatabase();
		if (sopUIDs == null || sopUIDs.length < 1) {
			List<String> sopUIDsList = db.getInstanceUidList(patID, studyUID, seriesUID);
			sopUIDs = sopUIDsList.toArray(new String[sopUIDsList.size()]);
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
		if (pathToImages == null || pathToImages.size() < 1) {
			logger.warning("Cannot find images for loading...");
			return;
		}
		loadSeries(patID, studyUID, seriesUID, sopUIDs, pathToImages);
	}

	public void loadSeries(String patID, String studyUID, String seriesUID, String[] sopUIDs,
			List<String> pathToImages) {
		if (pathToImages == null || pathToImages.size() == 0) {
			System.out.println("prap needs path to images..., return.");
			return;
		}
		viewPanel.removeAll();
		/*
		 * update information of series images.
		 */
		setInfo(patID, studyUID, seriesUID, sopUIDs, pathToImages);
		constructSlideGlassesFromDicom(pathToImages);
		applyGlobalAutoWindow();// before slider.initContext
		currentSlice = -1;
		updateSlidersVisibility();
		if (Utils.isDebug) {
			System.out.println(slides.size() + " images loaded.");
		}
	}

	/**
	 * SlideGlasses that created by this method, Dicom attributes keeps minimally.
	 * 
	 * @param images
	 */
	public void loadSeries(ImagePlus images, boolean sortCZT) {
		if (images == null || images.getStackSize() == 0) {
			if (Utils.isDebug)
				System.out.println("praparat needs images..., return.");
			return;
		}
		currentSlice = -1;
		updateInfoLabel(-1, -1, "-1", new double[] { -1, -1 }, -1, -1);

		String patID = GDicomTools.getTag(images, Tag.PatientID);
		String studyUID = GDicomTools.getTag(images, Tag.StudyInstanceUID);
		String seriesUID = GDicomTools.getTag(images, Tag.SeriesInstanceUID);
		String[] sopUIDs = new String[images.getNSlices()];
//		List<String> paths = new ArrayList<>();
		for (int i = 1; i <= images.getNSlices(); i++) {
			images.setSlice(GDicomTools.getRealIndex(images, i));
			String sopInstUid = GDicomTools.getTag(images, Tag.SOPInstanceUID);
			if (sopInstUid == null || sopInstUid.trim().length() == 0) {
				sopInstUid = UIDUtils.createUID();
			} else {
				sopUIDs[i - 1] = sopInstUid.trim();// fail safe
			}
			// if imageplus has series data, does not have all instance file paths.
//			String path = images.getFileInfo().getFilePath();
//			if(path != null && path.length() > 0 && new File(path).exists()) {
//				if(!paths.contains(path)) {
//					paths.add(path);
//				}
//			}
		}
		images.setSlice(1);// back to first.
		String refUID = GDicomTools.getTag(images, "0020,0052");
		setInfo(patID, studyUID, seriesUID, sopUIDs, refUID, null/* keep null file paths */);

		constructSlideGlassesFromImagePlus(images, sortCZT);
		applyGlobalAutoWindow(images);// before slider.initContext
		updateSlidersVisibility();

		if (Utils.isDebug) {
			Log.logger.fine(slides.size() + " images loaded.");
		}
	}

	public void loadSeries(List<String> dcm_paths) {
		if (dcm_paths == null || dcm_paths.size() == 0) {
			if (Utils.isDebug)
				System.out.println("praparat needs images..., return.");
			return;
		}

		// check multi frame

		currentSlice = -1;
		updateInfoLabel(-1, -1, "-1", new double[] { -1, -1 }, -1, -1);

		String pid = null;
		String studyUID = null;
		String seriesUID = null;
		String[] sopUIDs = new String[dcm_paths.size()];
		DICOMBackend be = DICOMBackend.getCurrent();
		int j = 0;
		for (String path : dcm_paths) {
			DicomReader reader = DicomReader.newDicomReader(be);
			reader.read(path, false);
			DicomObject dcmObj = reader.getHeader();
			if (pid == null) {
				pid = dcmObj.getString(Tag.Patient​ID);
				studyUID = dcmObj.getString(Tag.Study​Instance​UID);
				seriesUID = dcmObj.getString(Tag.Series​Instance​UID);
			}
			sopUIDs[j++] = dcmObj.getString(Tag.SOP​Instance​UID);
		}
		setInfo(pid, studyUID, seriesUID, sopUIDs, dcm_paths);
		constructSlideGlassesFromDicom(dcm_paths);

		applyGlobalAutoWindow();// before slider.initContext
		updateSlidersVisibility();

		if (Utils.isDebug) {
			System.out.println(slides.size() + " images loaded.");
		}
	}

	public ImagePlus processCropRectangle(boolean show) {
		SlideGlass sg = getCurrentSlide();
		CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
		RoiObj roi = cg.getSelectedRoi();
		if (roi == null) {
			JOptionPane.showMessageDialog(this, "Please select/create roi first. Can not cropping.", "Crop Tool",
					JOptionPane.INFORMATION_MESSAGE);
			return null;
		}

		RoiObj rect = new RoiObj(roi.getXBase(), roi.getYBase(), roi.getBounds().width, roi.getBounds().height, null);

		int res = JOptionPane.showConfirmDialog(this, "Process all slides in this series ?", "Crop series ?",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		final ImagePlus crop;
		if (res != JOptionPane.YES_OPTION) {
			ImagePlus imp = getImagePlus();
			imp.setSlice(getCurrentSlidePos() + 1);
			crop = new ImageProcessing().cropRect(imp, rect, false);
		} else {
			ImagePlus imp = getImagePlus();
			crop = new ImageProcessing().cropRect(imp, rect, true);
		}
		if (crop == null || crop.getNSlices() < 1) {
			Log.logger.severe("Cropping was failed...");
			return null;
		}

		if (show) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					Praparat prap = new Praparat(crop, getStudyColor(), mode, false);
					new SeriesWindow(prap);
				}
			});
		}
		return crop;
	}

	public ImagePlus processCut(boolean show) {
		SlideGlass sg = getCurrentSlide();
		CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
		RoiObj currentRoi = cg.getSelectedRoi();
		if (currentRoi == null) {
			Log.logger.info("Current ROI is null...");
			return null;
		}

		// RoiObj rect = new RoiObj(currentRoi.getXBase(), currentRoi.getYBase(),
		// currentRoi.getBounds().width, currentRoi.getBounds().height, null);

		final ImagePlus cut;
		int res = JOptionPane.showConfirmDialog(this, "Process all slide in this series ?", "Cut...",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (res != JOptionPane.YES_OPTION) {
			ImagePlus imp2 = getImagePlus();
			imp2.setSlice(getCurrentSlidePos() + 1);
			cut = new ImageProcessing().cut(imp2, currentRoi, false);
		} else {
			ImagePlus imp2 = getImagePlus();
			cut = new ImageProcessing().cut(imp2, currentRoi, true);
		}

		if (cut == null) {
			Log.logger.info("Cut failed... Please re-try...");
			return null;
		}

		new ImageProcessing().windowing(cut, sg.currentMin, sg.currentMax);

		if (show) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					Praparat prap = new Praparat(cut, getStudyColor(), mode, false);
					new SeriesWindow(prap);
				}
			});
		}

		return cut;
	}

	public void processFlipHF() {
		if (!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			sg.flipHF();
		} else {
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.flipHF();
			}
		}
	}

	public void processFlipLR() {
		if (!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			sg.flipLR();
		} else {
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.flipLR();
			}
		}
	}

	public void processInvertImages() {
		if (!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			sg.invert();
		} else {
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.invert();
			}
		}
	}
	
    // UIから、あるいはユーザー操作でROIが削除された時の処理
    public void removeRoi(RoiObj roiToRemove) {
    	boolean deleted = false;
    	for(SlideGlass sg : slides.values()) {
    		deleted = sg.deleteRoi(roiToRemove);
    	}
        if (deleted) {
            repaint();
        }
    }

	/**
	 * 指定したインデックスの画像をメモリ上に実体化（解凍含む）させる
	 */
	private void realizeImage(int index /* 0 to N-1 */, boolean processSeries, Double syncMag, Double syncRot,
			Double syncMin, Double syncMax, Point syncOrigin) {
		if (index < 0 || index >= slides.size()) {
			return;
		}
		SlideGlass sg = slides.get(index);
		DicomImage dcmimg = sg.getDicomImage();
		isMultiFrame = dcmimg.isMultiFrame();
		isMultiFrame = isPDF == true ? true : isMultiFrame;
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
			} else {
				// ★ ImagePlusから生成され、パスはないがメモリ上に画像データがある場合の処理
				if (dcmimg != null) {
					ImageProcessor ip = dcmimg.getImageProcessor(frame_pos);
					if (ip != null) {
						ImagePlus im = new ImagePlus("" + index, ip);
						sg.imageSpecimen.setOriginalImage(im);
					}
				}
			}

			double backupMin = sg.currentMin;
			double backupMax = sg.currentMax;

			sg.initCalibrationAndLUT();

			// ★ バックアップのリストア: 初期値（0と255）から変更されていた場合は、
			// グローバルコントラスト等で意味のある値が設定されていた証拠なので、強制的に復元する。
			if (!(backupMin == 0.0 && backupMax == 255.0)) {
				sg.currentMin = backupMin;
				sg.currentMax = backupMax;
			}

			if (sg.currentMin != sg.currentMax) {
				sg.changeWindowingByMinMax(sg.currentMin, sg.currentMax);
			}

			if (processSeries) {
				// move, zoom, rotate, windowing
				if (syncMag != null && Double.isFinite(syncMag))
					sg.zoom(syncMag, false/* dummy */);
				if (syncRot != null && Double.isFinite(syncRot))
					sg.rotate(syncRot);
				if ((syncMin != null && Double.isFinite(syncMin)) && (syncMax != null && Double.isFinite(syncMax))) {
					sg.changeWindowingByMinMax(syncMin, syncMax);
				}
				// finally set origin
				if (syncOrigin != null)
					sg.setDisplayOrigin(syncOrigin);
			}
		}
	}

	/**
	 * SingleGrid描画用 キャッシュ管理：円環（リングバッファ）状に前後数枚をロードする
	 */
	public void manageCache(int currentIndex) {
		if (slides == null || slides.isEmpty()) {
			return;
		}

		SlideGlass current = getCurrentSlide();
		final double syncMag = current.getMagnification();
		final double syncRot = current.getRotateAngle();
		final double syncMin = current.currentMin;
		final double syncMax = current.currentMax;
		final Point syncOrigin = current.getDisplayImageOriginXY();
		final boolean processSeries = isProcessSeries();

		// ★ 最新のリクエストIDを発行
		final int requestId = latestCacheRequest.incrementAndGet();

		prefetchExecutor.submit(() -> {
			// ★ キューから取り出された時点で、より新しいリクエストが来ていたらキャンセル
			if (requestId != latestCacheRequest.get()) {
				return;
			}

			int totalSize = slides.size();

			// 1. 周回を考慮した範囲の画像をロード
			for (int i = -PREFETCH_RANGE; i <= PREFETCH_RANGE; i++) {
				// ★ 処理中にも新しいリクエスト（次のスクロール）が来たら即座に中断
				if (requestId != latestCacheRequest.get()) return;

				int targetIndex = (currentIndex + i + totalSize) % totalSize;
				realizeImage(targetIndex, processSeries, syncMag, syncRot, syncMin, syncMax, syncOrigin);
			}

			// ロード完了後、SlideGlassに再描画を促す（最新リクエストの時のみ）
			if (requestId == latestCacheRequest.get()) {
				SlideGlass sg = slides.get(currentIndex);
				if (sg != null) {
					synchronized (sg) {
						sg.updateDisplayImage();
						SwingUtilities.invokeLater(sg::repaint);
					}
				}
			}

			// 2. メモリ解放（範囲外の画像をアンロード）
			if (totalSize > (PREFETCH_RANGE * 2 + 1) && requestId == latestCacheRequest.get()) {
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
	 * FilmGrid用のキャッシュ管理。 表示範囲 [first, last] の画像を実体化し、それ以外を解放する。
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
					synchronized (sg) {
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
			if (hasFileSource(index) || (isMultiFrame() && hasFileSource(0))) {
				// original image to null
				sg.imageSpecimen.setOriginalImage(null);
				// bulk file release
				if (!isMultiFrame()) {
					sg.getDicomImage().releasePixelBulkFromHeader();
				}
			}
		}
	}

	/**
	 * irreversible operation.
	 * 
	 * @param imp
	 */
	public void reloadSlideGlasses(ImagePlus imp) {
		if (imp == null) {
			return;
		}
		loadSeries(imp, false/*AS-IS order*/);
		if (!isShowGridViewOn()) {
			doSingleGridLayout();
		} else {
			doFilmGridLayout(filmGridColumns);
		}
		adjustContrastByMinMax(imp.getDisplayRangeMin(), imp.getDisplayRangeMax());
	}

	public void reloadSlideGlasses(Praparat pp) {
		if (pp == null) {
			return;
		}
		loadSeries(pp);
		if (!isShowGridViewOn()) {
			doSingleGridLayout();
		} else {
			doFilmGridLayout(filmGridColumns);
		}
	}

	/**
	 * refresh and load new slideglasses from DB.
	 */
	public void reloadSlideGlasses(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
		// do not allow sopUIDs NULL.
		if (sopUIDs == null) {
			return;
		}
		// remove current series image and get new series info
		loadSeries(patID, studyUID, seriesUID, sopUIDs);
		if (!isShowGridViewOn()) {
			doSingleGridLayout();
		} else {
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
			// do nothing
		} else {
			if (slide != null) {
				viewPanel.removeAll();
			}
		}
		updateInfoLabel(-1, -1, "-1", new double[] { -1, -1 }, -1, -1);
	}

	/**
	 * TODO
	 * 
	 * ATTENTION reloadSlideGlasses(imp); is irreversible operation.
	 */
	@Deprecated
	public void reload() {

		if (getImageFileLocations() == null || getImageFileLocations().size() == 0) {
			ImagePlus imp = getImagePlus();
			pvcp.setProcessSeries(true);// to show all images after reset
			updateInfoLabel(-1, -1, "-1", new double[] { -1, -1 }, -1, -1);
			reloadSlideGlasses(imp);// ATTENTION, irreversible operation. may remove few attributes.
			return;
		}

		if (mode == ViewMode.Normal || mode == ViewMode.SingleGrid) {
			pvcp.setProcessSeries(true);// to show all images after reset
			updateInfoLabel(-1, -1, "-1", new double[] { -1, -1 }, -1, -1);
			if (isShowGridViewOn()) {
				showGridViewOn = false;
				doFilmGridLayout(filmGridColumns);
			} else {
				// reload slides
				// prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
				// setTextVisible(pvcp.isShowInfo());
				// setAnnotationVisible(pvcp.isShowRoi());
				doSingleGridLayout();
			}
			for (int i : slides.keySet()) {
				slides.get(i).reset();
			}
			return;
		}

		/*
		 * BirdsEye FilmGrid
		 */
		if (mode == ViewMode.FilmGrid) {
			pvcp.setProcessSeries(true);// to show all images after reset
			updateInfoLabel(-1, -1, "-1", new double[] { -1, -1 }, -1, -1);
//			setTextVisible(false);
//			setAnnotationVisible(false);
			doFilmGridLayout(filmGridColumns);
			for (int i : slides.keySet()) {
				slides.get(i).reset();
			}
			return;
		}

		if (mode == ViewMode.Thumbnail) {
			// do nothing
			return;
		}
	}

	public void resetView() {
		// 1. コントロールパネルとラベルの初期化
		pvcp.setProcessSeries(true);
		updateInfoLabel(-1, -1, "-1", new double[] { -1, -1 }, -1, -1);

		// 2. グリッド表示の解除
		if (isShowGridViewOn()) {
			showGridViewOn = false;
		}

		// 3. ★重要：全スライドの「表示状態のみ」をリセットする
		// データを再生成しないため、DICOM属性情報は100%保持されます。
		if (slides != null) {
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.reset(); // ズーム、パン、回転のリセット
				sg.resetContrast(); // ウィンドウレベル（DICOM値）のリセット
			}
		}

		// 4. モードに応じたレイアウトの再適用
		if (mode == ViewMode.Normal || mode == ViewMode.SingleGrid || mode == ViewMode.MPR) {
			doSingleGridLayout();
			showFirstImage();
		} else if (mode == ViewMode.FilmGrid) {
			doFilmGridLayout(filmGridColumns);
		}

		// サムネイルモードは何もしない
	}

	public void resetWindow() {
		if (pvcp.processSeries()) {
			for (int i : slides.keySet()) {
				SlideGlass sg = slides.get(i);
				sg.autoWindowing();
			}
		} else {
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
		if (val == null) {
			updateInfoLabel(pointOnOrg, null + "(" + null + ")", scaleXY, mag, rotate);
			return;
		}
		if (!currentSlide.isRGB()) {
			Double[] pixelRawAndCalibrated = (Double[]) val;
			double raw_v = pixelRawAndCalibrated[0];
			double calibrated_v = pixelRawAndCalibrated[1];
			updateInfoLabel(pointOnOrg, calibrated_v + "(" + raw_v + ")", scaleXY, mag, rotate);
		} else {
			String[] rgbAndColor = (String[]) val;
			String r = rgbAndColor[0];
			String g = rgbAndColor[1];
			String b = rgbAndColor[2];
//			String color = rgbAndColor[3];//java.awt.Color[r,g,b]
//			updateInfoLabel(X, Y, r+","+g+","+b+" "+"("+color+")", scale, mag, rotate);
			updateInfoLabel(pointOnOrg, "(r,g,b)" + r + "," + g + "," + b, scaleXY, mag, rotate);
		}
	}

	/**
	 * 
	 * do after load slides
	 * 
	 * @param v
	 */
	public void setAnnotationVisible(boolean v) {
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		if (slides == null) {
			return;
		}
		if (isShowGridViewOn()) {
			if (isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					s.setAnnotationVisible(v);
				}
			} else {
				/*
				 * change state of only selected.
				 */
				List<SlideGlass> selected = getSelectedGlasses();
				for (SlideGlass s : selected) {
					s.setAnnotationVisible(v);
				}
			}
		} else {
			if (isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					s.setAnnotationVisible(v);
				}
			} else {
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
		for (int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			if (sg.isSelected()) {
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
	 * 
	 * @param focusGained
	 */
	public void setFocusGained(boolean focusGained) {
		this.focusGained = focusGained;
		if (getViewMode() != ViewMode.SingleGrid && getViewMode() != ViewMode.FilmGrid) {
			showBorder(focusGained);
		}
	}

	private void setImageFileLocations(List<String> pathToImages) {
		this.pathToImages = pathToImages;
	}

	/**
	 * If you want to change slide position, use setImagePositionUsingSlider
	 * instead.
	 * 
	 * @param sliceIndex:number of slice index, 0 to n-1
	 */
	void setImagePosition(int sliceIndex) {
		if (slides == null) { // do not include pathToImages
			return;
		}
		if (isShowGridViewOn()) {
			// to get current slice
			currentSlice = sliceIndex;
			return;
		}
		
		// ★ 1. 処理開始前にWAITにする
	    setWaitCursor(true);
	    
	    try {
	    	// set image first time
			if (currentSlice == -1) {
				currentSlice = sliceIndex;
				// 1. 現在表示する画像は「最優先」でロード（メインスレッド）
				Double syncMag = 1.0;
				Double syncRot = 0.0;
				Double syncMin = null;
				Double syncMax = null;
				Point syncOrigin = null;
				realizeImage(currentSlice, isProcessSeries(), syncMag, syncRot, syncMin, syncMax, syncOrigin);

				SlideGlass currentGlass = this.slides.get(currentSlice);
				if (currentGlass == null)
					return;

				// init all slide//already loaded (ImagePlus pattern)
				// move, zoom, rotate, windowing
				for (int i = 0; i < slides.size(); i++) {
					SlideGlass sg = slides.get(i);
					if (syncMag != null && Double.isFinite(syncMag))
						sg.zoom(syncMag, false/* dummy */);
					if (syncRot != null && Double.isFinite(syncRot))
						sg.rotate(syncRot);
					if ((syncMin != null && Double.isFinite(syncMin)) && (syncMax != null && Double.isFinite(syncMax))) {
						sg.changeWindowingByMinMax(syncMin, syncMax);
					} else if (sg.currentMin != sg.currentMax) {
						// initGlobalContrast() で設定済みの値をそのまま適用
						sg.changeWindowingByMinMax(sg.currentMin, sg.currentMax);
					}
					// finally set origin
					if (syncOrigin != null)
						sg.setDisplayOrigin(syncOrigin);
				}

				boolean sizeChanged = (currentGlass.getWidth() != viewPanel.getWidth()
						|| currentGlass.getHeight() != viewPanel.getHeight());

				viewPanel.removeAll();
				viewPanel.add(currentGlass, 0);

				if (sizeChanged) {
					currentGlass.setSize(viewPanel.getWidth(), viewPanel.getHeight());
					viewPanel.revalidate(); // サイズが変わった時だけ重い処理をする
				}
				viewPanel.repaint(); // 画面の更新だけならrepaintで十分

				// 2. 前後の先読みを開始（バックグラウンドスレッド）
				manageCache(currentSlice);

				currentGlass.updateDisplayImage();
				currentGlass.repaint();
				currentGlass.requestFocus();
				currentGlass.setFocusGained(true);// for key listener
				return;
			}

			if (currentSlice == sliceIndex) {
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

			currentGlass.setSize(viewPanel.getWidth(), viewPanel.getHeight());

			// 親パネルにレイアウトの再計算と再描画を強制する
			viewPanel.revalidate();
			viewPanel.repaint();

			// 2. 前後の先読みを開始（バックグラウンドスレッド）
			manageCache(currentSlice);

			currentGlass.updateDisplayImage();
			currentGlass.repaint();
			currentGlass.requestFocus();
			currentGlass.setFocusGained(true);// for key listener
	    }finally {
	    	setWaitCursor(false);
	    }
	}

	public void setImagePositionTo(SlideGlass sg) {
		Set<Integer> keys = slides.keySet();
		for (Integer key : keys) {
			SlideGlass slide = slides.get(key);
			if (sg == slide) {
				setImagePosition(key);
				break;
			}
		}
	}

	/**
	 * 指定された1Dインデックス(pos)に基づいて、各次元(C, T, Z)のスライダー位置を同期し、画像を表示する
	 * @param pos 0 to n-1
	 */
	public void setImagePositionUsingSlider(int pos) {
		SwingUtilities.invokeLater(() -> {
			if (nChannels < 1 || nSlices < 1) return; // fail safe

			// 1Dのインデックス(pos)から各次元の現在地を逆算
			int t = pos / (nChannels * nSlices);
			int rem = pos % (nChannels * nSlices);
			int z = rem / nChannels;
			int c = rem % nChannels;

			// update indices
			currentT = t;
			currentZ = z;
			currentC = c;

			// 各スライダーのUI表示を同期（値が変わればCineSliderがイベントを発火し、setImagePositionが呼ばれる）
			boolean sliderUpdated = false;
			
			if (nFrames > 1 && frameSlider.getValue() != (t + 1)) {
				frameSlider.setPosition(t);
				sliderUpdated = true;
			}
			if (nSlices > 1 && slider.getValue() != (z + 1)) {
				slider.setPosition(z);
				sliderUpdated = true;
			}
			if (nChannels > 1 && channelSlider.getValue() != (c + 1)) {
				channelSlider.setPosition(c);
				sliderUpdated = true;
			}

			// どのスライダーも動かなかった場合（すでにUIが目的の値を指していた場合）、直接画像更新を呼び出す
			if (!sliderUpdated) {
				setImagePosition(pos);
				callBackLocalizer();
			}
		});
	}

	private void setInfo(String patID, String studyUID, String seriesUID, String[] sopUIDs, List<String> pathToImages) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		String frameOfRefUID = null;
		if (db != null) {
			frameOfRefUID = db.getValueFromImage("FrameOfReferenceUID", patID, studyUID, seriesUID, sopUIDs[0]);
		} else {
			frameOfRefUID = DicomUtilities.getFrameOfReferenceUID(pathToImages.get(0));
		}
		setInfo(patID, studyUID, seriesUID, sopUIDs, frameOfRefUID, pathToImages);
	}

	private void setInfo(String patID, String studyUID, String seriesUID, String[] sopUIDs, String refUID,
			List<String> pathToImages) {
		this.patID = patID;
		this.studyUID = studyUID;
		this.seriesUID = seriesUID;
		this.sopUIDs = sopUIDs;
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null && (refUID == null || refUID.length() == 0)) {
			this.frameOfReferenceUID = db.getValueFromImage("FrameOfReferenceUID", patID, studyUID, seriesUID,
					sopUIDs[0]);
		} else {
			this.frameOfReferenceUID = refUID;
		}
		setImageFileLocations(pathToImages);
	}
	
	/**
     * Praparat単体で2DViwerツール群を強制設定する場合に使用します（Anonymizer等用）
     * -1でリセット。
     */
    public void setLocalToolType(int toolType) {
        this.localToolType = toolType;
    }

	public void setLUT(LUT lut) {
		this.lut = lut;
		if (!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			sg.setLUT(this.lut);
		} else {
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				sg.setLUT(this.lut);
			}
		}
		if (getViewPanel() != null) {
			getViewPanel().repaint();
		}
	}

	public void setNextSlice() {
		setImagePositionUsingSlider(currentSlice + 1);
	}

	public void setPreviousSlice() {
		setImagePositionUsingSlider(currentSlice - 1);
	}

	public void setReferenceLineMPR(ReferenceLineMPR refLine) {
		this.refLineMPR = refLine;
	}

	/**
	 * Praparat selection state See, SlideGlassMouseListener True if any one of the
	 * SlideGlasses is in the selected state.
	 */
	public void setSelectionState(boolean select) {
		if (mode == ViewMode.Thumbnail) {
			this.selected = select;
			showBorder(false/* focusGained */);
			return;
		}
		this.selected = false;
		for (int i : slides.keySet()) {
			SlideGlass sg = slides.get(i);
			if (sg.isSelected()) {
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
	 * 
	 * @param w
	 * @param h
	 */
	private void setViewPanelSize(int w, int h) {
		viewPanel.setPreferredSize(new Dimension(w, h));
		viewPanel.setBounds(0, 0, w, h);
	}

	public void setStudyColor(Color color) {
		if (color != null) {
			this.studyColor = color;
		}
	}

	/**
	 * do after load slides
	 * 
	 * @param v
	 */
	public void setTextVisible(boolean v) {
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		if (slides == null) {
			return;
		}
		if (isShowGridViewOn()) {
			if (isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					s.setTextVisible(v);
				}
			} else {
				List<SlideGlass> selected = getSelectedGlasses();
				for (SlideGlass s : selected) {
					s.setTextVisible(v);
				}
			}
		} else {
			if (isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					s.setTextVisible(v);
				}
			} else {
				getCurrentSlide().setTextVisible(v);
			}
		}
	}

	/*
	 * GhostGlassPane is the filter on top of the 2DViewerFrame.
	 * JToolBarは、ユーザーがドラッグしてウィンドウから切り離す（フローティングさせる）ことができる。
	 * ツールバーが切り離された場合、そのツールバーは「JFrameの子」ではなくなり、 「独立した別のウィンドウ」 になる。
	 * 
	 * Floatingした場合は、JDialogへGhostGlassPaneを自動追加。 StageView:ancestor
	 * 
	 * 切り離されていない時: JFrame の GlassPane でOK。 切り離された時: フローティングウィンドウ自身の GlassPane
	 * を使う必要がある。
	 */
	public GhostGlassPane getGhostGlassPane() {
		Window currentWindow = SwingUtilities.getWindowAncestor(this);
		if (currentWindow instanceof JFrame) {
			JFrame f = (JFrame) currentWindow;
			Component gp = f.getGlassPane();
			if (gp != null && gp instanceof GhostGlassPane) {
				return (GhostGlassPane) gp;
			}
		}
		return null;
	}

	/**
	 * Prapがメインフレームにあるか、切り離されているかを判定
	 * 
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
		if (isAttachedToMainFrame()) {
			return false;
		}
		if (isAttachedToViewr2D()) {
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

	public boolean isSigned() {
		SlideGlass sg = getCurrentSlide();
		if (sg != null) {
			DicomImage dcm = sg.getDicomImage();
			return dcm.isSigned();
		} else {
			return false;
		}
	}
	
	public boolean isLoaded(String pid, String studyUid, String seriesUid) {
		Object uids[] = getUIDs();
		if(uids == null) {
			return false;
		}
		try {
			if (uids[0].equals(pid) && uids[1].equals(studyUid) && uids[2].equals(seriesUid)) {
				return true;
			}
		}catch(Exception e) {
			return false;
		}
		return false;
	}

	public void invert() {
		if (slides == null || slides.size() == 0) {
			return;
		}
		if (isProcessSeries()) {
			for (SlideGlass sg : slides.values()) {
				sg.invert();
			}
		} else {
			SlideGlass sg = getCurrentSlide();
			sg.invert();
		}
	}

	public void showBorder(boolean show) {
		Border b = BorderMaker.make(this, isFocusGained());
		setBorder(b);
	}

	public void showFirstImage() {
		// position range is 0 to n-1
		currentSlice = -1;
		if(slider != null) {
			setImagePositionUsingSlider(0);
		}else {
			setImagePosition(0);
		}
	}

	private void updateInfoLabel(Point p, String value, double[] scaleXY, double mag, double rotate) {
		updateInfoLabel(p.x, p.y, value, scaleXY, mag, rotate);
	}

	private void updateInfoLabel(int x, int y, String value, double[] scaleXY, double mag, double rotate) {
		if (getViewMode() != ViewMode.Thumbnail) {
			this.pvcp.setText2InfoLabel(x, y, value, scaleXY, mag, rotate);
		}
	}

	/**
	 * See, viewPanel componentListener.
	 */
	public void updateViewPanel() {
		int currentW = getViewPanelWidth();
		int currentH = getViewPanelHeight();

		if (mode == ViewMode.Thumbnail) {
			for (Integer k : slides.keySet()) {
				SlideGlass sg = slides.get(k);
				// 実際の可視領域のサイズにピッタリ合わせる
				sg.setSize(currentW, currentH);
			}
			prevViewPanelW = currentW;
			prevViewPanelH = currentH;
			return;
		}

		if (currentW == prevViewPanelW && currentH == prevViewPanelH) {
			return;
		}

		if (mode == ViewMode.FilmGrid || showGridViewOn/* ViewMode.Normal */) {
			if (viewPanel.getComponentCount() >= 1) {
				Component con = viewPanel.getComponent(0);
				if (con instanceof SlideGlassGrid) {
					SlideGlassGrid sgg = (SlideGlassGrid) con;
					sgg.updateLayout();
				}
			}
		} else {
//			for(Integer k : slides.keySet()) {
//				SlideGlass sg = slides.get(k);
//				sg.setSize(viewPanel.getWidth(), viewPanel.getHeight());
//			}
			SlideGlass currentSg = getCurrentSlide();
			if (currentSg != null) {
				currentSg.setSize(viewPanel.getWidth(), viewPanel.getHeight());
			}
		}
		prevViewPanelW = currentW;
		prevViewPanelH = currentH;
	}

	/**
	 * 解析された次元数(nChannels, nFrames, nSlices)に基づき、スライダーの表示を更新する
	 */
	public void updateSlidersVisibility() {
		if (sliderPanel == null) return;

		sliderPanel.removeAll();

		// Z(Slice)スライダー
		if (nSlices > 1) {
			sliderPanel.add(slider);
			slider.initContext(nSlices);
			slider.setSliderVisible(true);
			slider.setCineButtonVisible(true); // スライス再生有効
		} else {
			// 枚数が1枚なら追加しない
			slider.initContext(1);
			slider.setSliderVisible(false);
			slider.setCineButtonVisible(false);
		}
		slider.setColorBarVisible(true);

		// C(Channel)スライダー
		if (nChannels > 1) {
			sliderPanel.add(channelSlider);
			channelSlider.initContext(nChannels);
			channelSlider.setSliderVisible(true);
			channelSlider.setColorBarVisible(false);
			// ★ 変更：再生ボタンを表示する
			channelSlider.setCineButtonVisible(true); 
		}

		// T(Time)スライダー
		if (nFrames > 1) {
			sliderPanel.add(frameSlider);
			frameSlider.initContext(nFrames);
			frameSlider.setSliderVisible(true);
			frameSlider.setColorBarVisible(false);
			// ★ 確認：再生ボタンを表示する
			frameSlider.setCineButtonVisible(true); 
		}

		sliderPanel.revalidate();
		sliderPanel.repaint();
	}

	/**
	 * CineSliderからの変更通知を受け取り、表示画像を切り替える
	 */
	public void notifyDimensionChanged(String dimName, int value) {
		if ("Slice".equals(dimName)) {
			this.currentZ = value;
		} else if ("Channel".equals(dimName)) {
			this.currentC = value;
		} else if ("Time".equals(dimName)) {
			this.currentT = value;
		}

		// 1次元インデックスの再計算
		pendingTargetIndex = getCurrentSlideCZTIndex();
		
		// ★ 高速スクロール時のフリーズを防ぐための遅延実行（Debounce）
		if (scrollDebounceTimer == null) {
			scrollDebounceTimer = new javax.swing.Timer(40, e -> {
				setImagePosition(pendingTargetIndex);
				callBackLocalizer();
				// fail safe : 画像のセットが終わったらクロスヘアに戻す
	            setWaitCursor(false);
			});
			scrollDebounceTimer.setRepeats(false); // 1回だけ実行
		}
		
		// タイマーをリスタート（ホイールが連続して呼ばれている間は、実際の画像更新が延期される）
		scrollDebounceTimer.restart();
	}

	@Override
	public boolean equals(Object pp) {
		if (pp == null) {
			return false;
		}
		if (!(pp instanceof Praparat)) {
			return false;
		}
		String srcConcatedUIDs = concatenationOfUIDStrings();
		String tarConcatedUIDs = ((Praparat) pp).concatenationOfUIDStrings();
		if (srcConcatedUIDs == null && tarConcatedUIDs == null) {
			return this == pp;
		} else if (srcConcatedUIDs == null && tarConcatedUIDs != null) {
			return this == pp;
		} else if (srcConcatedUIDs != null && tarConcatedUIDs == null) {
			return this == pp;
		}
		return srcConcatedUIDs.equals(tarConcatedUIDs);
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
//		if(slides != null) {
//			if(getViewMode() == ViewMode.FilmGrid) {
//				for (int instNo : slides.keySet()) {
//					SlideGlass sg = slides.get(instNo);
//					sg.repaint();
//				}
//			}else {
//				SlideGlass current = getCurrentSlide();
//				if(current != null) {
//					current.repaint();
//				}
//			}
//		}
		pvcp.repaint();
	}
}
