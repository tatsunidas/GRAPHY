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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.vis.configuration.RoiDBKey;
import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.log.Log;
import com.vis.core.slicer.ReferenceLineMPR;
import com.vis.core.ui.main.BirdsEyeView;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.processing.ImageProcessing;
import com.vis.core.view.D2.roi.RoiConverter;
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
import ij.gui.Overlay;
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
	ColorBar colorBar;
	private String currentLutName = "Grayscale";
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

	private int currentSliceZCT = -1;
	private javax.swing.Timer scrollDebounceTimer;

	// Fusion
	private boolean isFusionMode = false;
	// 空間座標（IPP）が背景と同期された、メタデータ保持済みの純粋な前景画像スタック
	private ImagePlus foregroundOverlay;
	private double currentFusionOpacity = 0.5;
	private int fusionOffsetX = 0;
	private int fusionOffsetY = 0;

	/*
	 * SUV calibration factor
	 */
	private double suvFactor = 0.0;

	/*
	 * ZCT index to handle multi-channel
	 */
	private int pendingTargetIndex = -1;

	private java.util.concurrent.atomic.AtomicInteger latestCacheRequest = new java.util.concurrent.atomic.AtomicInteger(
			0);

	private int filmGridColumns = 5;
	private boolean isMultiFrame = false;/* to set video option */
	private boolean isPDF = false;
	private boolean selected = false;
	private boolean focusGained = false;
	private boolean showGridViewOn = false;// filemGridView

	private boolean crossLineCursorMode = false;// mpr
	
	// 3D ROI管理リスト
	private List<RoiObj> roi3DList = new java.util.concurrent.CopyOnWriteArrayList<>();

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
	private int currentC = 0;// channel, 0-based
	private int currentZ = 0;// slice position, 0-based
	private int currentT = 0;// time frame, 0-based

	/*
	 * 順序は保証しない
	 */
	private ConcurrentHashMap<Integer/* ZCT */, SlideGlass> slides;

	private final int PREFETCH_RANGE = 3;
	private ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor();

	private final java.util.concurrent.ConcurrentHashMap<Integer, WwWlState> wwWlStorage = new java.util.concurrent.ConcurrentHashMap<>();

	final ViewMode mode;

	/*
	 * 2D viewerツールタイプ指定 -1 は「未設定（2D Viewerのグローバル状態に従う）」を意味します
	 */
	private int localToolType = -1;

	private JPanel emptyGlassPanel;

	/**
	 * Load normal praparat
	 * 
	 * @param stack
	 * @param studyColor
	 */
	public Praparat(ImagePlus stack, Color studyColor, ViewMode mode, boolean sortZCT) {
		this.mode = mode;
		if (studyColor != null) {
			this.studyColor = studyColor;
		}
		initComponent();
		String modality_str = GDicomTools.getTag(stack, Tag.Modality);
		this.modality = Modality.is(modality_str);
		loadSeries(stack, sortZCT);
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
		if (pathToSortedinstNoImages != null && !pathToSortedinstNoImages.isEmpty()) {
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
			reader.read(pathToSortedinstNoImages.get(0), false);
			this.modality = Modality.is(reader.getHeader());
		}
		loadSeries(patID, studyUID, seriesUID, sopUIDs, pathToSortedinstNoImages);
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
		if (slide == null)
			return;

		slide.adjustContrastFromMouseAction(dragX, dragY);
		double newMin = slide.currentMin;
		double newMax = slide.currentMax;

		// 自身のストレージを更新
		getWwWlState(getCurrentSlidePos()).setValues(-1, newMin, newMax);

		if (isProcessSeries()) {
			ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				if (slide == sg) {
					continue;
				}
				// 他のスライドのストレージも更新して適用
				getWwWlState(key).setValues(-1, newMin, newMax);
				sg.changeWindowingByMinMax(newMin, newMax);
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
				if (sg != null) {
					// 記憶領域へ保存（-1 は All Channels）
					getWwWlState(key).setValues(-1, min, max);
					sg.changeWindowingByMinMax(min, max);
				}
			}
		} else {
			int zct = getCurrentSlidePos();
			SlideGlass slide = slides.get(zct);
			if (slide != null) {
				// 単一スライスの場合
				getWwWlState(zct).setValues(-1, min, max);
				slide.changeWindowingByMinMax(min, max);
			}
		}
	}

	/**
	 * スタック全体の最適な表示レンジ（Min/Max）からWindow CenterとWindow Widthを算出し表示します。
	 */
	public void applyGlobalAutoWindow() {
		if (slides == null || slides.isEmpty())
			return;

		// 1. 真ん中のスライスを代表として選ぶ
		// ★ 修正: ZCT対応。実際に存在するキーをソートし、その中央値を取得することで空きマスを確実に回避
		List<Integer> keys = new ArrayList<>(slides.keySet());
		java.util.Collections.sort(keys);
		int midKey = keys.get(keys.size() / 2);

		SlideGlass midSlide = slides.get(midKey);
		if (midSlide == null)
			return;

		DicomImage dcm = midSlide.getDicomImage();
		if (dcm == null)
			return;

		// 2. ピクセルデータを「確実に」ロードする
		// ★ 修正: hasFileSource には取得したZCTインデックス(midKey)を渡す
		if (hasFileSource(midKey)) {
			dcm.ensurePixelDataLoaded();
		}

		// 3. ImageProcessorを取得
		// ★ 修正: realizeImage と同様に、マルチフレームの場合はInstanceNumberからフレーム位置を逆算
		int frame_pos = isMultiFrame() ? (midSlide.getHeader().getInt(Tag.InstanceNumber, 1) - 1) : 0;
		ImageProcessor ip = dcm.getImageProcessor(frame_pos);

		if (ip != null) {
			// 上下0.5%の外れ値を除外して、医療画像として最も自然なコントラスト幅を自動計算
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

	/**
	 * 対象スライスのWW/WL状態を取得、存在しなければ初期化して返す
	 */
	public WwWlState getWwWlState(int zctIndex) {
		return wwWlStorage.computeIfAbsent(zctIndex, key -> {
			SlideGlass sg = slides.get(key);
			if (sg != null) {
				// SlideGlassの初期状態からWwWlStateを生成
				double[] minMax = sg.getCurrentWindowMinMax();
				return new WwWlState(minMax[0], minMax[1]);
			}
			return new WwWlState(0.0, 255.0);
		});
	}

	/**
	 * 外部（UIダイアログなど）からWW/WLが変更されたときに呼び出されるメソッド
	 */
	public void updateSliderContrast(int zctIndex, int colorChannel, double min, double max) {
		// 1. ストレージ（記憶領域）に保存（これでアンロードされても消えない）
		WwWlState state = getWwWlState(zctIndex);
		state.setValues(colorChannel, min, max);

		// 2. 現在メモリにある実際のSlideGlassにリアルタイム反映
		if (isProcessSeries()) {
			// シリーズ全体同期がONの場合、全スライスのストレージを更新して反映
			for (Integer key : slides.keySet()) {
				getWwWlState(key).setValues(colorChannel, min, max);
				SlideGlass sg = slides.get(key);
				if (sg != null && sg.getOriginalImage() != null) {
					applyStateToSlideGlass(sg, colorChannel, min, max);
				}
			}
		} else {
			// 単一スライスのみ反映
			SlideGlass sg = slides.get(zctIndex);
			if (sg != null) {
				applyStateToSlideGlass(sg, colorChannel, min, max);
			}
		}
	}

	/**
	 * 実際のSlideGlass（ImageJのImageProcessor等）に値を適用するヘルパー
	 */
	private void applyStateToSlideGlass(SlideGlass sg, int colorChannel, double min, double max) {
		if (sg.isRGB()) {
			// ★改善点: カラー画像の場合、実際のウインドウイング適用は描画パイプライン（updateDisplayImage）内で
			// WwWlState を参照して行うため、ここではグローバル管理値の同期と、再描画のトリガーのみを行います。
			if (colorChannel == -1) {
				sg.currentMin = min;
				sg.currentMax = max;
			}

			// 画面描画の更新をリアルタイムにトリガー
			sg.imageSpecimen.updateDisplayImage();
		} else {
			// モノクロ画像（通常のWW/WL）
			sg.changeWindowingByMinMax(min, max);
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

	public void addRoi(int slidePos, RoiObj r) {
		// ★ 修正：slides.size() ではなく、全体容量(capacity)か、Mapにキーが存在するかで判定
		int capacity = nChannels * nSlices * nFrames;
		if (slidePos < 0 || slidePos >= capacity) {
			Log.logger.warning("Praparat: this slide position invalid...cannot addRoi().");
			return;
		}
		SlideGlass sg = slides.get(slidePos);
		if (sg != null) {
			sg.addRoi(r);
		}
	}
	
	public List<RoiObj> getRoi3DList() {
		return roi3DList;
	}
	
	public void addRoi3D(RoiObj roi3D) {
		if (roi3D != null && !roi3DList.contains(roi3D)) {
			roi3DList.add(roi3D);
		}
	}
	
	public void removeRoi3D(RoiObj roi3D) {
		if (roi3D != null) {
			roi3DList.remove(roi3D);
		}
	}

	public List<Point2D> calcLocalizer(GeometryOfSlice bePostedCurrentSlide) {
		SlideGlass sg = getCurrentSlide();
		if (sg == null || bePostedCurrentSlide == null) {
			return null;
		}
		GeometryOfSlice localizerGeometry = new GeometryOfSlice(sg.getHeader());
		GeometryOfSlice postImageGeometry = bePostedCurrentSlide;
		LocalizerPoster localizerPoster = new IntersectVolume(localizerGeometry);
		List<Point2D> shape = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
		return shape;
	}

	private List<Point2D> calcLocalizer(SlideGlass src/* will draw */, SlideGlass target/* be posted */) {
		if (src == null || target == null) {
			return null;
		}
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
		// 1. remove previous localizers
		for (Praparat p : praps) {
			ConcurrentHashMap<Integer, SlideGlass> targetSlides = p.getAllSlides();
			if (targetSlides != null) {
				// null guard
				for (SlideGlass s : targetSlides.values()) {
					if (s != null) {
						s.drawLocalizer(null);
						s.repaintCanvasGlass();
					}
				}
			}
		}
		// 2. show localizer on slideglass
		SlideGlass from = getCurrentSlide();
		// If slideglass is null (NO IMAGE), process end.
		if (from == null) {
			return;
		}
		for (Praparat p : praps) {
			// if self, skip
			if (p == this) {
				continue;
			}
			SlideGlass to = p.getCurrentSlide();
			// If slideglass is null (NO IMAGE), process skip.
			if (to == null) {
				continue;
			}
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

	/**
	 * Attention: Will use large physical memory. This method is only used for
	 * single pop-up view or test purpose. Dicom attributes keeps minimally.
	 */
	private void constructSlideGlassesFromImagePlus(ImagePlus images, boolean sortZCT) {
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
			/*
			 * 一旦、slidesは連番で埋めておき、 後の、organizeMultiDimensionalSlidesでZCTにマップし直す
			 */
			slides.put(i, sg);
		}

		if (slides != null && slides.size() > 1) {
			// ConcurrentHashMapの値をそのままリスト化せず、キー順に並び替える
			List<Integer> keys = new ArrayList<>(slides.keySet());
			java.util.Collections.sort(keys);
			List<SlideGlass> slideList = new ArrayList<>();
			for (Integer k : keys) {
				slideList.add(slides.get(k));
			}
			/*
			 * sort images via IOP and IPP.
			 */
			if (sortZCT) {
				organizeMultiDimensionalSlides(slideList);
			} else {
				this.nChannels = images.getNChannels();
				this.nSlices = images.getNSlices();
				this.nFrames = images.getNFrames();
			}
		} else {
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
		removeSlide(currentSliceZCT);
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

	/**
	 * NIfTIやマルチエコーMRIなどの多次元スライスを解析し、次元(C,Z,T)の自動算出と1D配列への再配置を行う
	 */
	private void organizeMultiDimensionalSlides(List<SlideGlass> slideList) {
		if (slideList == null || slideList.isEmpty())
			return;

		int firstFrameIdx = slideList.get(0).getHeader().getInt(Tag.InstanceNumber, 1) - 1;
		double[] iop = getSafeIOP(slideList.get(0).getHeader(), firstFrameIdx);
		boolean isIopConsistent = true;
		if (iop != null && iop.length == 6) {
			for (int i = 1; i < slideList.size(); i++) {
				int currentFrameIdx = slideList.get(i).getHeader().getInt(Tag.InstanceNumber, i + 1) - 1;
				double[] currentIop = getSafeIOP(slideList.get(i).getHeader(), currentFrameIdx);
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

			int frameIdx = sg.getHeader().getInt(Tag.InstanceNumber, i + 1) - 1;
			double[] ipp = getSafeIPP(sg.getHeader(), frameIdx);

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

		// デバッグ用ログ出力
//		Log.logger.fine("--- Slide Position Debug Log ---");
//		for (SlidePos sp : spList) {
//			int fIdx = sp.sg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
//			double[] debugIpp = getSafeIPP(sp.sg.getHeader(), fIdx);
//			int segNum = getSegmentNumber(sp.sg.getHeader(), fIdx);
//			Log.logger.fine(String.format("InstNo: %3d | Seg: %2d | Pos: %8.4f | IPP: %s", sp.instNo, segNum, sp.pos,
//					java.util.Arrays.toString(debugIpp)));
//		}
//		Log.logger.fine("--------------------------------");

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
		this.nSlices = sliceGroups.size();
		this.nFrames = 1;

		boolean isSegmentation = false;
		int maxSegNum = -1;
		int maxTempPos = 1; // 最大のTimeFrameインデックスを探す

		for (SlidePos sp : spList) {
			int frameIdx = sp.sg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
			// SEGのチェック
			int segNum = getSegmentNumber(sp.sg.getHeader(), frameIdx);
			if (segNum > 0) {
				isSegmentation = true;
				if (segNum > maxSegNum) {
					maxSegNum = segNum;
				}
			}
			// ★ TimeFrame（T）のチェック
			int tIdx = sp.sg.getHeader().getInt(Tag.TemporalPositionIndex, 1);
			if (tIdx > maxTempPos) {
				maxTempPos = tIdx;
			}
		}

		this.nFrames = maxTempPos; // ★ 取得したTimeの最大値をT次元にセット

		if (isSegmentation && maxSegNum > 0) {
			this.nChannels = maxSegNum;
		} else {
			// 既存の「重なり枚数」ロジックから、Timeフレーム分を割って純粋なチャンネル数を出す
			this.nChannels = 1;
			for (List<SlideGlass> group : sliceGroups) {
				int overlappingImages = group.size();
				int channels = overlappingImages / this.nFrames; // Tで割る
				if (channels > this.nChannels) {
					this.nChannels = channels;
				}
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

			for (int i = 0; i < group.size(); i++) {
				SlideGlass sg = group.get(i);

				int tIdx = sg.getHeader().getInt(Tag.TemporalPositionIndex, 1) - 1; // 0ベースに変換
				if (tIdx < 0)
					tIdx = 0; // フォールバック

				int c = i; // デフォルトはリストの順番
				if (isSegmentation) {
					int frameIdx = sg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;
					int segNum = getSegmentNumber(sg.getHeader(), frameIdx);
					if (segNum > 0) {
						c = segNum - 1; // 1ベースのSegmentNumberを0ベースのチャンネルIndexに変換
					}
				} else if (this.nFrames > 1) {
					// ★ 汎用5D対応: Timeが存在する場合、残りの重なり順(i)をチャンネルとして分配
					c = i % this.nChannels;
				}

//				int index = (tIdx * (this.nChannels * this.nSlices)) + (sliceIdx * this.nChannels) + c;
				int index = calcZctIndex(new int[] { sliceIdx, c, tIdx });
				slides.put(index, sg);
			}
		}
		// 最後に次元情報をUIに反映させる
		SwingUtilities.invokeLater(() -> updateSlidersVisibility());
	}

	/**
	 * 指定した次元(Slice, Channel, Time)のスライダーを一歩進める/戻す
	 * 
	 * @param dimName 次元名 ("Slice", "Channel", "Time")
	 * @param step    増減量 (1, -1など)
	 */
	public void stepDimension(String dimName, int step) {
		int val = 0;
		int max = 0;
		CineSlider targetSlider = null;

		// 現在の値と最大枚数、対象のスライダーを特定
		if ("Slice".equals(dimName)) {
			val = currentZ;
			max = nSlices;
			targetSlider = slider;
		} else if ("Channel".equals(dimName)) {
			val = currentC;
			max = nChannels;
			targetSlider = channelSlider;
		} else if ("Time".equals(dimName)) {
			val = currentT;
			max = nFrames;
			targetSlider = frameSlider;
		}

		// スライダーが存在しない、または1枚しかない場合は何もしない
		if (targetSlider == null || max <= 1)
			return;

		// 次の値を計算（ループ処理）
		int nextVal = val + step;
		if (nextVal < 0)
			nextVal = max - 1;
		if (nextVal >= max)
			nextVal = 0;

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

		if (isMultiDimensional() || isMultiFrame() || isPDF()) {
			Log.logger.warning("FilmGrid view is disabled for MultiFrame/PDF/Multi-dimensional data.");
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

	public SlideGlass getFirstNoEmptySlide() {
		if (slides != null && slides.size() < 1) {
			return null;
		}
		for (int z = 0; z < nSlices; z++) {
			int zct = calcZctIndex(new int[] { z, 0, 0 });
			SlideGlass sg = getSlideGlassAt(zct);
			if (sg != null)
				return sg;
		}
		return null;
	}

	public PraparatViewControlPanel getController() {
		return pvcp;
	}

	/**
	 * If it is multichannel/timeframe mode, praparat may have few blank
	 * slideglasses. If slideglass is blank{NO IMAGE}, return null.
	 * 
	 * @return
	 */
	public SlideGlass getCurrentSlide() {
		if (slides == null || slides.isEmpty())
			return null;
		if (currentSliceZCT == -1) {
			for (int z = 0; z < nSlices; z++) {
				int zct = calcZctIndex(new int[] { z, 0, 0 });
				SlideGlass sg = getSlideGlassAt(zct);
				if (sg != null)
					return sg;
			}
		}
		/*
		 * Explicit code.
		 */
		SlideGlass sg = slides.get(currentSliceZCT);
		if (sg != null) {
			return sg;
		} else {
			return null;
		}
	}

	public int getCurrentSlidePos() {
		return currentSliceZCT;
	}

	public int getCurrentSlideZCTIndex() {
		return currentT * (nChannels * nSlices) + currentZ * nChannels + currentC;
	}

	/**
	 * check Viewer2D selecting tool.
	 * 
	 * @return
	 */
	public int getCurrentViewerToolType() {
		if (Viewer2DScreen.getInstance() != null) {
			return Viewer2DScreen.getInstance().getCurrentToolType();
		}
		return Viewer2DToolBar.NONE;
	}

	public Eyepiece getEyepiece() {
		return prapManager;
	}

	/**
	 * NO IMAGE background panel
	 */
	private JPanel getEmptyGlassPanel() {
		if (emptyGlassPanel == null) {
			emptyGlassPanel = new JPanel(new BorderLayout()) {
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					// 背景を黒に
					g.setColor(Color.BLACK);
					g.fillRect(0, 0, getWidth(), getHeight());
					// 中央にNO IMAGEのテキストを描画
					g.setColor(Color.DARK_GRAY);
					String msg = "NO IMAGE";
					int stringWidth = g.getFontMetrics().stringWidth(msg);
					g.drawString(msg, (getWidth() - stringWidth) / 2, getHeight() / 2);
				}
			};
			emptyGlassPanel.setBackground(Color.BLACK);

			// 空きマスでもマウスホイールでのスライス送りを可能にする
			emptyGlassPanel.addMouseWheelListener(e -> {
				if (e.getWheelRotation() > 0) {
					stepDimension("Slice", 1); // 下スクロールで次へ
				} else {
					stepDimension("Slice", -1); // 上スクロールで前へ
				}
			});

			// Shift ＋ 左クリックでPraparatを選択状態にする
			// ==========================================================
			emptyGlassPanel.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e) {
					if (e.isShiftDown() && SwingUtilities.isLeftMouseButton(e)) {
						// Emptyパネルは単体のSlideGlassが存在しないため、強制的にPraparat全体を切り替える
						setSelectionState(!isSelected());
					}
				}
			});
		}
		return emptyGlassPanel;
	}

	public List<String> getImageFileLocations() {
		return this.pathToImages;
	}

	public HashMap<Integer, DicomImage> getDicomImages() {
		if (slides == null || slides.isEmpty()) {
			return null;
		}
		HashMap<Integer, DicomImage> ds = new HashMap<Integer, DicomImage>();
		for (int zct : slides.keySet()) {
			SlideGlass sg = slides.get(zct);
			if (sg != null) {
				ds.put(zct, sg.getDicomImage());
			}
		}
		return ds;
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
	public ImagePlus getImagePlus() {
		if (slides == null || slides.isEmpty())
			return null;

		if (!isMultiFrame()) {
			ImageStack stack = new ImageStack();
			// 確実に存在するSlideGlassから、共通の縦横サイズを安全に取得する
			SlideGlass representative = null;
			for (SlideGlass sg : slides.values()) {
				if (sg != null && sg.getHeader() != null) {
					representative = sg;
					break;
				}
			}
			if (representative == null)
				return null; // ガード

			int w = representative.getOriginalImageSize().width;
			int h = representative.getOriginalImageSize().height;
			/*
			 * SUV較正済み
			 */
			Calibration orgCal = representative.getOriginalCalibration();
			String globalInfo = "";

			// =====================================================================
			// ★1. [シリーズ共通属性ドナーの事前探索] チャンネル×タイムフレームごとにスキャン
			// =====================================================================
			String[][] baseLabels = new String[nFrames][nChannels];
			boolean[][] isColorFlags = new boolean[nFrames][nChannels];
			int[][] bitDepths = new int[nFrames][nChannels];

			for (int t = 0; t < nFrames; t++) {
				for (int c = 0; c < nChannels; c++) {
					baseLabels[t][c] = "Empty";
					isColorFlags[t][c] = false;
					bitDepths[t][c] = 8;
					for (int z = 0; z < nSlices; z++) {
						int idx = t * (nChannels * nSlices) + z * nChannels + c;
						SlideGlass sg = slides.get(idx);
						if (sg != null && sg.getDicomImage() != null && hasFileSource(idx)) {
							if (sg.getDicomImage().ensurePixelDataLoaded()) {
								ImagePlus donorImp = GDicomTools.dcmImgToImagePlus(sg.getDicomImage(), orgCal);
								if (donorImp != null && donorImp.getStackSize() > 0) {
									baseLabels[t][c] = donorImp.getStack().getSliceLabel(1);
									isColorFlags[t][c] = sg.isRGB(); // ★カラー画像属性の取得
									bitDepths[t][c] = sg.getDicomImage().getBitsAllocated();
									break;
								}
							}
						}
					}
				}
			}

			// =====================================================================
			// ★2. [空間位置ドナーの事前探索] Z座標ごとにスキャン
			// =====================================================================
			String[] zSpaceLabels = new String[nSlices];
			for (int z = 0; z < nSlices; z++) {
				zSpaceLabels[z] = "Empty";
				for (int t = 0; t < nFrames; t++) {
					for (int c = 0; c < nChannels; c++) {
						int idx = t * (nChannels * nSlices) + z * nChannels + c;
						SlideGlass sg = slides.get(idx);
						if (sg != null && sg.getDicomImage() != null && hasFileSource(idx)) {
							if (sg.getDicomImage().ensurePixelDataLoaded()) {
								ImagePlus donorImp = GDicomTools.dcmImgToImagePlus(sg.getDicomImage(), orgCal);
								if (donorImp != null && donorImp.getStackSize() > 0) {
									zSpaceLabels[z] = donorImp.getStack().getSliceLabel(1);
									break;
								}
							}
						}
					}
					if (!zSpaceLabels[z].equals("Empty"))
						break;
				}
			}

			// ImageJ dimension order: C -> Z -> T
			for (int t = 0; t < nFrames; t++) {
				for (int z = 0; z < nSlices; z++) {
					for (int c = 0; c < nChannels; c++) {
						int index = t * (nChannels * nSlices) + z * nChannels + c;
						SlideGlass sg = slides.get(index);

						// ★ 修正: 空きマス（null）パディングの完全最適化
						if (sg == null || !hasFileSource(index) || !sg.getDicomImage().ensurePixelDataLoaded()) {
							boolean isColor = isColorFlags[t][c];
							int bits = bitDepths[t][c];

							// 本来のシリーズ属性と、同Zの空間座標をテキストレベルで安全にマージ
							String mergedLabel = mergeDicomMetaLabels(baseLabels[t][c], zSpaceLabels[z]);

							ImageProcessor dummyIp;
							if (isColor) {
								// ★ カラー画像対応：ColorProcessorを生成
								dummyIp = new ij.process.ColorProcessor(w, h);
							} else {
								// モノクロ画像対応：ビット深度を一致させる
								switch (bits) {
								case 16:
									dummyIp = new ij.process.ShortProcessor(w, h);
									break;
								case 32:
									dummyIp = new ij.process.FloatProcessor(w, h);
									break;
								case 8:
								case 1:
								default:
									dummyIp = new ij.process.ByteProcessor(w, h);
									break;
								}
							}
							stack.addSlice(mergedLabel, dummyIp);
							continue;
						}

						DicomImage dcmImg = sg.getDicomImage();
						if (hasFileSource(index)) {
							if (!dcmImg.ensurePixelDataLoaded()) {
								continue;
							}
						}

						// 元のコードと同様に ImagePlus を経由して、ピクセルとメタデータを一括取得
						// これにより、個別スライスの全DICOMタグが SliceLabel に入る
						ImagePlus sliceImp = GDicomTools.dcmImgToImagePlus(dcmImg, orgCal);
						String sliceLabel = sliceImp.getStack().getSliceLabel(1/* always */);
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
			if (!globalInfo.isEmpty())
				replica.setProperty("Info", globalInfo);
			replica.setCalibration(orgCal);

			if (nChannels > 1 || nSlices > 1 || nFrames > 1) {
				replica.setDimensions(nChannels, nSlices, nFrames);
				replica.setOpenAsHyperStack(true);
			}

			if (this.lut != null) {
				replica.setLut(this.lut);
			}
			return replica;
		} else {
			if (hasFileSource(0)) {
				Calibration orgCal = getCurrentSlide().getOriginalCalibration();
				String path = getImageFileLocations().get(0);
				DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
				reader.read(path, false);
				DicomObject header = reader.getHeader();
				DicomImage dcm = DicomImage.newDicomImage(path, header, reader.getFileMetaInfomation(),
						reader.checkTSUID(), DICOMBackend.getCurrent());
				ImagePlus imp = GDicomTools.dcmImgToImagePlus(dcm, orgCal);
				if (this.lut != null) {
					imp.setLut(this.lut);
				}
				return imp;
			}
		}
		return null;
	}

	/**
	 * 指定した C, T のシリーズ（Zスタック）を、個別メタデータを保持したまま抽出します。
	 */
	public ImagePlus getImagePlus(int targetC/* 0-based */, int targetT/* 0-based */) {
		com.vis.core.log.Log.logger.info("[getImagePlus] Started for C=" + targetC + ", T=" + targetT);
		if (slides == null || slides.isEmpty())
			return null;

		if (targetC < 0 || targetC >= nChannels)
			targetC = currentC;
		if (targetT < 0 || targetT >= nFrames)
			targetT = currentT;

		SlideGlass representative = null;
		for (SlideGlass sg : slides.values()) {
			if (sg != null && sg.getHeader() != null) {
				representative = sg;
				break;
			}
		}
		if (representative == null)
			return null;

		int w = representative.getOriginalImageSize().width;
		int h = representative.getOriginalImageSize().height;

		ij.measure.Calibration orgCal = representative.getOriginalCalibration();
		ij.ImageStack stack = new ij.ImageStack(w, h); // 幅と高さを指定
		String globalInfo = "";

		boolean isColor = false;
		int bitDepth = 8;
		double displayMin = Double.NaN;
		double displayMax = Double.NaN;

		com.vis.core.log.Log.logger.info("[getImagePlus] Phase 1: Scanning image info...");
		for (int z = 0; z < nSlices; z++) {
			int idx = targetT * (nChannels * nSlices) + z * nChannels + targetC;
			SlideGlass sg = slides.get(idx);
			// sg が null でないことを確認してから getDicomImage() を呼ぶ
			if (sg != null && sg.getDicomImage() != null && hasFileSource(idx)) {
				com.vis.dicom.image.DicomImage dcmImg = sg.getDicomImage();
				if (dcmImg.ensurePixelDataLoaded()) {
					isColor = sg.isRGB();
					bitDepth = dcmImg.getBitsAllocated();
					if (Double.isNaN(displayMin)) {
						displayMin = sg.currentMin;
						displayMax = sg.currentMax;
					}
					break;
				}
			}
		}

		com.vis.core.log.Log.logger.info("[getImagePlus] Phase 2: Scanning labels...");
		String[] sliceLabels = new String[nSlices];
		for (int z = 0; z < nSlices; z++) {
			sliceLabels[z] = "Empty";
			int idx = targetT * (nChannels * nSlices) + z * nChannels + targetC;
			SlideGlass sg = slides.get(idx);
			if (sg != null && sg.getDicomImage() != null && hasFileSource(idx)) {
				int frameIdx = sg.getDicomImage().isMultiFrame() ? (sg.getHeader().getInt(Tag.InstanceNumber, 1) - 1)
						: 0;
				double[] ipp = getSafeIPP(sg.getHeader(), frameIdx);
				double[] iop = getSafeIOP(sg.getHeader(), frameIdx);
				sg.getHeader().setDouble(Tag.ImagePositionPatient, VR.DS, ipp);
				sg.getHeader().setDouble(Tag.ImageOrientationPatient, VR.DS, iop);
				sliceLabels[z] = com.vis.dicom.image.GDicomTools.getHeaderAsString(sg.getHeader(), new StringBuilder(),
						0);
			}
		}

		com.vis.core.log.Log.logger
				.info("[getImagePlus] Phase 3: Building ImageStack (Total Slices: " + nSlices + ")...");
		for (int s = 0; s < nSlices; s++) {
			if (s % 20 == 0) {
				com.vis.core.log.Log.logger.info("[getImagePlus] Processing slice " + s + " / " + nSlices);
			}
			int index = targetT * (nChannels * nSlices) + s * nChannels + targetC;
			SlideGlass sg = slides.get(index);

			// Padding empty image with slice label.
			if (sg == null || !hasFileSource(index) || !sg.getDicomImage().ensurePixelDataLoaded()) {
				ij.process.ImageProcessor dummyIp;
				if (isColor)
					dummyIp = new ij.process.ColorProcessor(w, h);
				else {
					switch (bitDepth) {
					case 16:
						dummyIp = new ij.process.ShortProcessor(w, h);
						break;
					case 32:
						dummyIp = new ij.process.FloatProcessor(w, h);
						break;
					default:
						dummyIp = new ij.process.ByteProcessor(w, h);
						break;
					}
				}
				stack.addSlice(sliceLabels[s], dummyIp);
				continue;
			}

			// set original image
			if (sg.getDicomImage().ensurePixelDataLoaded()) {
				ij.process.ImageProcessor ip = null;
				ImagePlus orgImp = sg.getOriginalImage();

				if (orgImp != null) {
					ip = orgImp.getProcessor().duplicate(); // 必ず複製して安全に積む
				} else {
					com.vis.dicom.image.DicomImage dcmImg = sg.getDicomImage();
					int frameIdx = dcmImg.isMultiFrame()
							? (sg.getHeader().getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1)
							: 0;
					ip = dcmImg.getImageProcessor(frameIdx).duplicate();
				}

				if (globalInfo.isEmpty())
					globalInfo = sliceLabels[0]; // zero index

				stack.addSlice(sliceLabels[s], ip);
			}
		}

		com.vis.core.log.Log.logger.info("[getImagePlus] Phase 4: Finalizing ImagePlus...");
		ImagePlus replica = new ImagePlus("Series_C" + targetC + "_T" + targetT, stack);
		if (!globalInfo.isEmpty() && stack.getSize() == 1) {
			replica.setProperty("Info", globalInfo);
		}
		replica.setCalibration(orgCal);
		replica.setDimensions(1, nSlices, 1);

		if (!Double.isNaN(displayMin) && !Double.isNaN(displayMax) && displayMin != displayMax) {
			replica.setDisplayRange(displayMin, displayMax);
		}

		if (this.lut != null) {
			replica.setLut(this.lut);
		}

		com.vis.core.log.Log.logger.info("[getImagePlus] Completed successfully!");
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
			imp.setPositionWithoutUpdate(c, z, t);
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
		for (SlideGlass sg : slides.values()) {
			if (sg != null)
				return sg.getOriginalImageSize().width;
		}
		return 0;
	}

	public int getImageHeight() {
		for (SlideGlass sg : slides.values()) {
			if (sg != null)
				return sg.getOriginalImageSize().height;
		}
		return 0;
	}

	public LUT getLUT() {
		return this.lut;
	}

	public String getLUTName() {
		return currentLutName;
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

	public SlideGlass getSlideGlassAt(int zctInd) {
		if (this.slides == null || this.slides.isEmpty()) {
			return null;
		}
		int capacity = nChannels * nSlices * nFrames;
		if (zctInd < 0 || zctInd >= capacity) {
			return null;
		}
		// null-able
		SlideGlass sg = this.slides.get(zctInd);
		return sg;
	}

	public int getSlidePosition(SlideGlass slide) {
		int[] zct = getZCTArray(slide);
		return zct[0];
	}

	public int getZCTIndex(SlideGlass slide) {
		int[] zct = getZCTArray(slide);
		return calcZctIndex(zct);
	}

	/**
	 * 0 based. (but, in ImageJ it is 1-based, be careful.)
	 * 
	 * @param zct
	 * @return
	 */
	public int calcZctIndex(int[] zct) {
		int z = zct[0];
		int c = zct[1];
		int t = zct[2];
		return t * (nChannels * nSlices) + z * nChannels + c;
	}

	public int[] getZCTArray(SlideGlass slide) {
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		if (slides == null)
			return new int[] { -1, -1, -1 };

		for (Entry<Integer, SlideGlass> entry : slides.entrySet()) {
			if (entry.getValue() == slide) {
				int index = entry.getKey();
				return calcZCTArrayFromIndex(index);
			}
		}
		return new int[] { -1, -1, -1 };
	}

	public int[] calcZCTArrayFromIndex(int index/* zct */) {
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		// 線形インデックス zct_index を各次元に分解
		// 公式: zct_index = t * (nChannels * nSlices) + z * nChannels + c
		if (slides == null)
			return new int[] { -1, -1, -1 };
		int c = index % nChannels;
		int z = (index / nChannels) % nSlices;
		int t = index / (nChannels * nSlices);

		return new int[] { z, c, t };
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
//
//	/**
//	 * マスター3D球ROIの情報を元に、交差する前後のスライスへ自動的にスレイブROIを展開します。
//	 */
//	public void generate3DSphereSlaves(com.vis.core.view.D2.roi.RoiObj masterRoi) {
//		if (masterRoi == null)
//			return;
//
//		String radiusStr = masterRoi.getProperty(RoiMetaContextKey.Sphere_Radius_mm.name());
//		String centerIppStr = masterRoi.getProperty(RoiMetaContextKey.Sphere_Center_IPP.name());
//		String groupId = masterRoi.getProperty(com.vis.configuration.RoiDBKey.RoiGroup.name());
//		
//		if (radiusStr == null || centerIppStr == null || groupId == null) {
//			com.vis.core.log.Log.logger.warning("Missing 3D Sphere parameters.");
//			return;
//		}
//
//		try {
//			double R = Double.parseDouble(radiusStr); // 球の半径(mm)
//			String[] ippParts = centerIppStr.split(",");
//			double cx = Double.parseDouble(ippParts[0].trim());
//			double cy = Double.parseDouble(ippParts[1].trim());
//			double cz = Double.parseDouble(ippParts[2].trim());
//
//			String cStr = masterRoi.getProperty(RoiMetaContextKey.Dim_C.name());
//			String tStr = masterRoi.getProperty(RoiMetaContextKey.Dim_T.name());
//			int targetC = (cStr != null && !cStr.isEmpty()) ? Integer.parseInt(cStr) : -1;
//			int targetT = (tStr != null && !tStr.isEmpty()) ? Integer.parseInt(tStr) : -1;
//			
//			com.vis.core.log.Log.logger.info(String.format(
//					"[DEBUG-7] generate3DSphereSlaves starts. R=%.2f, CX=%.2f, CY=%.2f, CZ=%.2f", 
//					R, cx, cy, cz
//				));
//				int slaveCount = 0;
//			
//			for (java.util.Map.Entry<Integer, SlideGlass> entry : slides.entrySet()) {
//				SlideGlass sg = entry.getValue();
//
//				// マスター自身が乗っているスライスはスキップ
//				if (sg == masterRoi.getSlideGlass())
//					continue;
//
//				int[] zct = getZCTArray(sg);
//				// チャンネルとフレームがマスターと異なる場合はスキップ（別の時空間）
//				if (targetC != -1 && targetC != zct[1])
//					continue;
//				if (targetT != -1 && targetT != zct[2])
//					continue;
//
//				com.vis.dicom.DicomObject header = sg.getHeader();
//				int frameIdx = isMultiFrame() ? header.getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1 : 0;
//
//				double[] sliceIpp = getSafeIPP(header, frameIdx);
//				double[] sliceIop = getSafeIOP(header, frameIdx);
//
//				if (sliceIpp == null || sliceIop == null)
//					continue;
//
//				// 1. スライスの法線ベクトル (Row x Col)
//				double nx = sliceIop[1] * sliceIop[5] - sliceIop[2] * sliceIop[4];
//				double ny = sliceIop[2] * sliceIop[3] - sliceIop[0] * sliceIop[5];
//				double nz = sliceIop[0] * sliceIop[4] - sliceIop[1] * sliceIop[3];
//
//				// 2. スライスの原点(IPP)から球の中心へ向かうベクトル
//				double vx = cx - sliceIpp[0];
//				double vy = cy - sliceIpp[1];
//				double vz = cz - sliceIpp[2];
//
//				// 3. 球の中心からスライス平面までの直交距離 d
//				double d = Math.abs(vx * nx + vy * ny + vz * nz);
//
//				// 4. 交差判定 (距離が半径未満なら交差)
//				if (d < R) {
//					// 断面の円の半径 r
//					double r_mm = Math.sqrt(R * R - d * d);
//
//					// 5. 球の中心座標をスライスの2Dピクセル座標(x, y)に投影
//					double projX_mm = vx * sliceIop[0] + vy * sliceIop[1] + vz * sliceIop[2];
//					double projY_mm = vx * sliceIop[3] + vy * sliceIop[4] + vz * sliceIop[5];
//
//					double pxSpacingX = sg.getPixelSpacingX();
//					double pxSpacingY = sg.getPixelSpacingY();
//					if (pxSpacingX <= 0)
//						pxSpacingX = 1.0;
//					if (pxSpacingY <= 0)
//						pxSpacingY = 1.0;
//
//					double pixelX = projX_mm / pxSpacingX;
//					double pixelY = projY_mm / pxSpacingY;
//
//					double radiusPxX = r_mm / pxSpacingX;
//					double radiusPxY = r_mm / pxSpacingY;
//
//					// スレイブROIの生成
//					int startX = (int)(pixelX - radiusPxX);
//					int startY = (int)(pixelY - radiusPxY);
//					int width = (int)(radiusPxX * 2.0);
//					int height = (int)(radiusPxY * 2.0);
//
//					com.vis.core.view.D2.roi.OvalRoi slaveRoi = new com.vis.core.view.D2.roi.OvalRoi(startX, startY,
//							width, height, sg);
//					slaveRoi.setState(com.vis.core.view.D2.roi.RoiObj.NORMAL);
//
//					// プロパティの継承
//					slaveRoi.setProperty(com.vis.configuration.RoiDBKey.RoiGroup.name(), groupId); // 同じグループID
//					slaveRoi.setProperty(RoiMetaContextKey.Shape_3D_Type.name(), "SPHERE"); 
//					slaveRoi.setProperty(RoiMetaContextKey.Sphere_Radius_mm.name(), String.valueOf(R));
//					slaveRoi.setProperty(RoiMetaContextKey.Dim_C.name(), String.valueOf(zct[1]));
//					slaveRoi.setProperty(RoiMetaContextKey.Dim_Z.name(), String.valueOf(zct[0]));
//					slaveRoi.setProperty(RoiMetaContextKey.Dim_T.name(), String.valueOf(zct[2]));
//					slaveRoi.setProperty(RoiMetaContextKey.Is3D_Slave.name(), "true");
//
//					// キャンバスへ追加（これにより自動的にDBに保存される）
//					CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
//					if (cg != null) {
//						cg.addRoi(slaveRoi);
//						// 裏側のスライドは自動で再描画されない場合があるため再描画を促す
//						sg.repaintCanvasGlass();
//						slaveCount++;
//					}
//				}
//			}
//			
//			// --- 検証ログ 3-2 ---
//			com.vis.core.log.Log.logger
//					.info("[DEBUG-8] generate3DSphereSlaves finished. Created slaves: " + slaveCount);
//			// ----------------
//
//			// Managerが開いていればリストを更新
//			com.vis.core.view.D2.roi.RoiObjManager rom = com.vis.core.view.D2.roi.RoiObjManager.getInstance();
//			if (rom != null && rom.isVisible()) {
//				rom.updateState();
//			}
//
//		} catch (Exception e) {
//			com.vis.core.log.Log.logger.log(java.util.logging.Level.SEVERE, "Failed to generate 3D Sphere Slaves", e);
//		}
//	}
//
//	// Praparat.java
//	public void deleteSphereGroup(String groupId) {
//		if (groupId == null)
//			return;
//		for (SlideGlass sg : slides.values()) {
//			if (sg == null)
//				continue;
//			com.vis.core.view.D2.ui.glasses.CanvasGlass cg = (com.vis.core.view.D2.ui.glasses.CanvasGlass) sg
//					.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
//			if (cg != null) {
//				java.util.ArrayList<com.vis.core.view.D2.roi.RoiObj> roiset = cg.getRoiSet();
//				if (roiset != null) {
//					java.util.Iterator<com.vis.core.view.D2.roi.RoiObj> it = roiset.iterator();
//					boolean removed = false;
//					while (it.hasNext()) {
//						com.vis.core.view.D2.roi.RoiObj r = it.next();
//						String gId = r.getProperty(com.vis.configuration.RoiDBKey.RoiGroup.name());
//						if (groupId.equals(gId)) {
//							it.remove();
//							// ==========================================================
//							// ★修正: getUIDs() を使って安全に削除キーを取得する
//							// ==========================================================
//							java.util.HashMap<com.vis.configuration.RoiDBKey, String> uids = r.getUIDs();
//							com.vis.db.DatabaseHandler.getInstance().deleteRoi(
//									uids.get(com.vis.configuration.RoiDBKey.PatientID),
//									uids.get(com.vis.configuration.RoiDBKey.StudyInstanceUID),
//									uids.get(com.vis.configuration.RoiDBKey.SeriesInstanceUID),
//									uids.get(com.vis.configuration.RoiDBKey.SOPInstanceUID),
//									uids.get(com.vis.configuration.RoiDBKey.RoiID));
//							removed = true;
//						}
//					}
//					if (removed)
//						cg.repaint();
//				}
//			}
//		}
//	}
//
//	/**
//	 * 移動・サイズ変更されたROIを新たなマスターとして、3D Sphere全体を再計算・再配置します
//	 */
//	public void updateSphere3D(com.vis.core.view.D2.roi.RoiObj modifiedRoi) {
//		String groupId = modifiedRoi.getProperty(com.vis.configuration.RoiDBKey.RoiGroup.name());
//		
//		// --- 検証ログ 1 ---
//		com.vis.core.log.Log.logger.info("[DEBUG-5] updateSphere3D called. GroupID = " + groupId);
//		// ----------------
//		
//		if (groupId == null) {
//			com.vis.core.log.Log.logger.warning("[DEBUG-5 ERROR] GroupID is NULL! Aborting update.");
//			return;
//		}
//
//		// 1. 自分以外のグループ内ROI（古いスレイブ等）を全スライドから一括削除
//		for (SlideGlass sg : slides.values()) {
//			if (sg == null)
//				continue;
//			com.vis.core.view.D2.ui.glasses.CanvasGlass cg = (com.vis.core.view.D2.ui.glasses.CanvasGlass) sg
//					.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
//			if (cg != null) {
//				java.util.ArrayList<com.vis.core.view.D2.roi.RoiObj> roiset = cg.getRoiSet();
//				if (roiset != null) {
//					java.util.Iterator<com.vis.core.view.D2.roi.RoiObj> it = roiset.iterator();
//					while (it.hasNext()) {
//						com.vis.core.view.D2.roi.RoiObj r = it.next();
//						if (r != modifiedRoi
//								&& groupId.equals(r.getProperty(com.vis.configuration.RoiDBKey.RoiGroup.name()))) {
//							it.remove();
//							java.util.HashMap<com.vis.configuration.RoiDBKey, String> uids = r.getUIDs();
//							com.vis.db.DatabaseHandler.getInstance().deleteRoi(
//								uids.get(com.vis.configuration.RoiDBKey.PatientID),
//								uids.get(com.vis.configuration.RoiDBKey.StudyInstanceUID),
//								uids.get(com.vis.configuration.RoiDBKey.SeriesInstanceUID),
//								uids.get(com.vis.configuration.RoiDBKey.SOPInstanceUID),
//								uids.get(com.vis.configuration.RoiDBKey.RoiID)
//							);
//						}
//					}
//					cg.repaint();
//				}
//			}
//		}
//
//		// 2. この modifiedRoi を「真のマスター」として3D空間座標(IPP)を再計算
//		SlideGlass sg = modifiedRoi.getSlideGlass();
//		double pixelSpacingX = sg.getPixelSpacingX();
//		double pixelSpacingY = sg.getPixelSpacingY();
//		if (pixelSpacingX <= 0)
//			pixelSpacingX = 1.0;
//		if (pixelSpacingY <= 0)
//			pixelSpacingY = 1.0;
//
//		// 現在の円の中心ピクセル座標
//		double imageX = modifiedRoi.getXBase() + modifiedRoi.getBounds().width / 2.0;
//		double imageY = modifiedRoi.getYBase() + modifiedRoi.getBounds().height / 2.0;
//
//		com.vis.dicom.DicomObject header = sg.getHeader();
//		int frameIdx = isMultiFrame() ? header.getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1 : 0;
//		double[] currentIpp = getSafeIPP(header, frameIdx);
//		double[] iop = getSafeIOP(header, frameIdx);
//
//		if (currentIpp != null && currentIpp.length == 3 && iop != null && iop.length == 6) {
//			double physX = currentIpp[0] + iop[0] * imageX * pixelSpacingX + iop[3] * imageY * pixelSpacingY;
//			double physY = currentIpp[1] + iop[1] * imageX * pixelSpacingX + iop[4] * imageY * pixelSpacingY;
//			double physZ = currentIpp[2] + iop[2] * imageX * pixelSpacingX + iop[5] * imageY * pixelSpacingY;
//			modifiedRoi.setProperty(RoiMetaContextKey.Sphere_Center_IPP.name(), physX + "," + physY + "," + physZ);
//		}
//
//		modifiedRoi.setProperty(RoiMetaContextKey.Is3D_Master.name(), "true");
//		modifiedRoi.setProperty(RoiMetaContextKey.Is3D_Slave.name(), null); // スレイブからマスターへ昇格
//
//		// DBの自身を上書き更新
//		/*
//		 * この処理はCanvasGlassのZCTにメタデータを上書きしてしまうため、直接DBに保存してアップデートする
//		 */
////		com.vis.core.view.D2.ui.glasses.CanvasGlass cg = (com.vis.core.view.D2.ui.glasses.CanvasGlass) sg
////				.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
////		cg.insertOrUpdateRoi4DB(modifiedRoi);
//		
//		com.vis.core.log.Log.logger.info(String.format("[DEBUG-6] Saving new Master to DB. New IPP: %s, Radius: %s",
//				modifiedRoi.getProperty(RoiMetaContextKey.Sphere_Center_IPP.name()),
//				modifiedRoi.getProperty(RoiMetaContextKey.Sphere_Radius_mm.name())));
//		
//		com.vis.db.DatabaseHandler db = com.vis.db.DatabaseHandler.getInstance();
//		if (db != null) {
//		    db.insertRoi(modifiedRoi.readContext());
//		}
//
//		// 3. 再展開
//		generate3DSphereSlaves(modifiedRoi);
//	}

	public Modality getModality() {
		return this.modality;
	}

	public double[] getSafeIOP(DicomObject header, int frameIndex) {
		// 1. まずルート階層をチェック
		double[] iop = header.getDoubles(Tag.ImageOrientationPatient);
		if (iop != null && iop.length == 6)
			return iop;

		// 2. SharedFunctionalGroupsSequence をチェック (Enhanced DICOM共通)
		DicomObject sharedSeq = header.getNestedDataset(Tag.SharedFunctionalGroupsSequence, 0);
		if (sharedSeq != null) {
			DicomObject planeOriSeq = sharedSeq.getNestedDataset(Tag.PlaneOrientationSequence, 0);
			if (planeOriSeq != null) {
				iop = planeOriSeq.getDoubles(Tag.ImageOrientationPatient);
				if (iop != null && iop.length == 6)
					return iop;
			}
		}

		// 3. PerFrameFunctionalGroupsSequence をチェック
		DicomObject perFrameSeq = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frameIndex);
		if (perFrameSeq != null) {
			DicomObject planeOriSeq = perFrameSeq.getNestedDataset(Tag.PlaneOrientationSequence, 0);
			if (planeOriSeq != null) {
				return planeOriSeq.getDoubles(Tag.ImageOrientationPatient);
			}
		}
		return null;
	}

	public double[] getSafeIPP(DicomObject header, int frameIndex) {
		// 1. ルート階層をチェック
		double[] ipp = header.getDoubles(Tag.ImagePositionPatient);
		if (ipp != null && ipp.length == 3)
			return ipp;

		// 2. PerFrameFunctionalGroupsSequence をチェック (SEG等のフレーム固有位置)
		DicomObject perFrameSeq = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frameIndex);
		if (perFrameSeq != null) {
			DicomObject planePosSeq = perFrameSeq.getNestedDataset(Tag.PlanePositionSequence, 0);
			if (planePosSeq != null) {
				return planePosSeq.getDoubles(Tag.ImagePositionPatient);
			}
		}
		return null;
	}

	private int getSegmentNumber(DicomObject header, int frameIndex) {
		// 0x52009230: PerFrameFunctionalGroupsSequence
		DicomObject perFrameSeq = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frameIndex);
		if (perFrameSeq != null) {
			// 0x0062000A: SegmentIdentificationSequence
			DicomObject segIdentSeq = perFrameSeq.getNestedDataset(Tag.SegmentIdentificationSequence, 0);
			if (segIdentSeq != null) {
				// 0x0062000B: ReferencedSegmentNumber
				return segIdentSeq.getInt(Tag.ReferencedSegmentNumber, -1);
			}
		}
		return -1;
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
	 * 現在選択されているツールタイプを取得します。 （SlideGlassMouseListener から呼ばれるメソッドです）
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

	// 修正前は index を使って pathToImages.get(index) をしていましたが、これをやめます
	/**
	 * 
	 * @param zct index
	 * @return
	 */
	public boolean hasFileSource(int index /* zct index */) {
		List<String> paths = getImageFileLocations();
		// パスリストが null または空でなければ、このシリーズはファイルベースであると判定
		if (paths == null || paths.isEmpty()) {
			return false;
		}

		// index（ZCT）に対応するスライドが存在するかどうかだけチェックする
		SlideGlass sg = slides.get(index);
		if (sg == null) {
			return false; // 空きマス（パディング領域）なら false
		}

		return true;
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
		showBorder(false);//
		pvcp = new PraparatViewControlPanel(this);// pixelInfoLabel

		JPanel southComponentPanel = new JPanel(new BorderLayout());

		colorBar = new ColorBar(this, 10, 10);
//		JPanel lutPanel = new JPanel(new GridLayout(0, 1));
//		lutPanel.add(lutManager);
		southComponentPanel.add(colorBar, BorderLayout.NORTH);

		sliderPanel = new JPanel(new GridLayout(0, 1));
		slider = new CineSlider(this, "Slice"); // ★ 引数にラベルを追加できるよう後でCineSliderも改修します
		channelSlider = new CineSlider(this, "Channel");
		frameSlider = new CineSlider(this, "Time");
		sliderPanel.add(slider);
		southComponentPanel.add(sliderPanel, BorderLayout.SOUTH);

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
			add(southComponentPanel, BorderLayout.SOUTH);
			setFocusable(true);
			setRequestFocusEnabled(true);
		}

		if (mode == ViewMode.SingleGrid) {
			add(pvcp, BorderLayout.NORTH);
			add(southComponentPanel, BorderLayout.SOUTH);
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
			add(southComponentPanel, BorderLayout.SOUTH);
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
		SlideGlass target = slides.get(currentSliceZCT);
		if (target != null) {
			target.setSize(w, h);
			target.repaint();
		}
		// set origin all slides
		for (Integer key : slides.keySet()) {
			SlideGlass sl = slides.get(key);
			if (sl == null || sl == target)
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

	/**
	 * for fMRI/DTI
	 * 
	 * @param sg
	 * @return
	 */
	private boolean isMosaic(SlideGlass sg) {
		if (sg == null || sg.getHeader() == null)
			return false;
		String[] imageTypes = sg.getHeader().getStrings(Tag.ImageType);
		if (imageTypes != null) {
			for (String type : imageTypes) {
				if (type != null && type.trim().equalsIgnoreCase("MOSAIC"))
					return true;
			}
		} else {
			String it = sg.getHeader().getString(Tag.ImageType, "");
			return it != null && it.contains("MOSAIC");
		}
		return false;
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

	/**
	 * SlideGlassの空間（IPP/FoR）と、ROIが持つ空間情報が一致するかを判定します。
	 */
	private boolean isSpatialMatch(SlideGlass sg, String roiIppStr, String roiForUid, String originSop) {
		DicomObject header = sg.getHeader();
		int frameIdx = isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;

		// SlideGlass側のIPPを取得
		double[] currentIpp = getSafeIPP(header, frameIdx);

		// 1. IPPが存在しない（完全な2D画像）場合のフォールバック判定
		if (currentIpp == null || roiIppStr == null) {
			// 空間の概念がないため、厳密なSOPの一致のみを許可する（C=ALLなどの共有を遮断）
			String currentSop = sg.getSOPInstanceUID();
			return currentSop != null && currentSop.equals(originSop);
		}

		// 2. FrameOfReferenceの判定 (厳密に空間を区別する場合)
		String currentForUid = header.getString(Tag.FrameOfReferenceUID);
		if (currentForUid == null || currentForUid.trim().isEmpty()) {
			currentForUid = header.getString(Tag.SeriesInstanceUID); // FoR欠損時はSeriesUIDを代替に
		}
		if (roiForUid != null && !roiForUid.equals(currentForUid)) {
			return false; // 空間の基準（座標系）が違う
		}

		// 3. IPP（3D空間座標）の距離計算によるZ軸の一致判定
		try {
			String[] parts = roiIppStr.split(",");
			if (parts.length == 3) {
				double rx = Double.parseDouble(parts[0].trim());
				double ry = Double.parseDouble(parts[1].trim());
				double rz = Double.parseDouble(parts[2].trim());

				double dx = currentIpp[0] - rx;
				double dy = currentIpp[1] - ry;
				double dz = currentIpp[2] - rz;
				double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

				// ユークリッド距離が 1e-3 (0.001mm) 以下なら同一スライス（Z）とみなす
				return distance <= 1e-3;
			}
		} catch (NumberFormatException e) {
			Log.logger.warning("ROI IPP Parsing Error: " + roiIppStr);
		}

		return false;
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
		if (sg != null) {
			sg.loadRoiFromDB();
		}
	}

	public void loadRoisFromDB() {
		if (slides == null || slides.size() == 0 || getViewMode() == ViewMode.Thumbnail) {
			return;
		}

		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null)
			return;

		// 1. シリーズ内の全ROIを一括取得
		ArrayList<HashMap<String, Object>> seriesRois = db.loadRoiContextFromSeries(patID, studyUID, seriesUID);
		if (seriesRois == null || seriesRois.isEmpty())
			return;
		
		Log.logger.fine("[DEBUG-LOAD] DBから読み込んだROI総数: " + (seriesRois != null ? seriesRois.size() : 0));

		// 2. ディスパッチ処理: 取得した各ROIを評価
		for (HashMap<String, Object> roiCtx : seriesRois) {

			// メタプロパティから多次元空間情報を抽出
			@SuppressWarnings("unchecked")
			Map<String, String> metaProps = (Map<String, String>) roiCtx.get(RoiDBKey.RoiMetaProperties.name());
			if (metaProps == null)
				metaProps = new HashMap<>();

			// 前回の議論で設計した情報を取得（保存時にこれらが付与されている前提）
			String roiIppStr = metaProps.get("ReferenceImagePositionPatient");
			String roiForUid = metaProps.get("FrameOfReferenceUID");

			String dimCStr = metaProps.get("Dim_C");
			String dimTStr = metaProps.get("Dim_T");

			// NULLの場合は "NULL" という特別な状態として保持しておく
			int targetC = (dimCStr != null && !dimCStr.trim().isEmpty()) ? Integer.parseInt(dimCStr) : -99;
			int targetT = (dimTStr != null && !dimTStr.trim().isEmpty()) ? Integer.parseInt(dimTStr) : -99;
			String originSop = (String) roiCtx.get("SOPInstanceUID");

			for (Map.Entry<Integer, SlideGlass> entry : slides.entrySet()) {
				int zctIndex = entry.getKey();
				SlideGlass sg = entry.getValue();
				if (sg == null)
					continue;

				int[] currentZCT = calcZCTArrayFromIndex(zctIndex);
				int currentC = currentZCT[1];
				int currentT = currentZCT[2];

				// ========================================================
				// ★判定A: 次元のマッチング（NULLの場合はALLにしない）
				// ========================================================
				// ターゲットが -99 (NULL) の場合は、その後の空間判定で Origin SOP との完全一致を要求することで単一スライスに縛る
				if (targetC != -1 && targetC != -99 && targetC != currentC)
					continue;
				if (targetT != -1 && targetT != -99 && targetT != currentT)
					continue;

				// ========================================================
				// ★判定B: 空間（Z座標 / IPP）とSOPのマッチング
				// ========================================================
				boolean spatialMatch = isSpatialMatch(sg, roiIppStr, roiForUid, originSop);

				// DimがNULL(-99)のレガシーROI/新規ROIの場合は、空間が一致してもSOPが違えば弾く（単一スライス表示）
				if (targetC == -99 || targetT == -99) {
					String currentSop = sg.getSOPInstanceUID();
					if (currentSop == null || !currentSop.equals(originSop)) {
						continue; // 単一スライス用なので、別のSOPには分配しない
					}
				} else if (!spatialMatch) {
					continue; // 空間が不一致なら弾く
				}

				// マッチング成功！
				RoiObj revivedRoi = new RoiConverter().buildRoiObj(roiCtx);
				
//				Log.logger.info("[DEBUG-LOAD] 復元されたクラス型: " + (revivedRoi != null ? revivedRoi.getClass().getSimpleName() : "null"));
				
				if (revivedRoi != null) {
					revivedRoi.setSlideGlass(sg, false);
					if (metaProps.containsKey(RoiMetaContextKey.Shape_3D_Type.name())) {
						revivedRoi.setProperty(RoiMetaContextKey.Shape_3D_Type.name(),
								metaProps.get(RoiMetaContextKey.Shape_3D_Type.name()));
					}
					if (metaProps.containsKey(RoiMetaContextKey.Sphere_Radius_mm.name())) {
						revivedRoi.setProperty(RoiMetaContextKey.Sphere_Radius_mm.name(),
								metaProps.get(RoiMetaContextKey.Sphere_Radius_mm.name()));
					}
					if (metaProps.containsKey(RoiMetaContextKey.Sphere_Center_IPP.name())) {
						revivedRoi.setProperty(RoiMetaContextKey.Sphere_Center_IPP.name(),
								metaProps.get(RoiMetaContextKey.Sphere_Center_IPP.name()));
					}

					// 2Dの各次元情報も確実に同期
					if (dimCStr != null)
						revivedRoi.setProperty(RoiMetaContextKey.Dim_C.name(), dimCStr);
					String dimZStr = metaProps.get("Dim_Z");
					if (dimZStr != null)
						revivedRoi.setProperty(RoiMetaContextKey.Dim_Z.name(), dimZStr);
					if (dimTStr != null)
						revivedRoi.setProperty(RoiMetaContextKey.Dim_T.name(), dimTStr);

					// SphereRoi3D / FreeFormRoi3D は per-slide roiset でなく Praparat の 3D リストで管理する
					boolean is3DManaged = (revivedRoi instanceof com.vis.core.view.D3.roi.SphereRoi3D)
							|| (revivedRoi instanceof com.vis.core.view.D3.roi.FreeFormRoi3D);
					
//					Log.logger.info("[DEBUG-LOAD] is3DManaged 判定: " + is3DManaged);
					
					if (is3DManaged) {
						// meta props 設定後に 3D フィールドを再初期化
						if (revivedRoi instanceof com.vis.core.view.D3.roi.SphereRoi3D) {
							((com.vis.core.view.D3.roi.SphereRoi3D) revivedRoi).initFromProperties();
						} else {
							((com.vis.core.view.D3.roi.FreeFormRoi3D) revivedRoi).initFromProperties();
						}
						// ROI ID で重複チェック (複数スライスがマッチしても1回だけ追加)
						String roiId3D = revivedRoi.getProperty(com.vis.configuration.RoiDBKey.RoiID.name());
						boolean alreadyLoaded = roi3DList.stream().anyMatch(r ->
								roiId3D != null && roiId3D.equals(
										r.getProperty(com.vis.configuration.RoiDBKey.RoiID.name())));
						if (!alreadyLoaded) {
							addRoi3D(revivedRoi);
//							Log.logger.info("[DEBUG-LOAD] Praparatの3Dリストに追加しました。現在の3Dリストサイズ: " + roi3DList.size());
						}
						break; // このROIの処理完了、他のスライドは不要
					}

					sg.addRoiFromDB(revivedRoi); // 通常2D ROI はキャンバスへ追加
				}
			}
		}
		repaint();
		// RoiObjManagerが開いている場合は、リストを一括更新
		if (com.vis.core.facade.WindowManager.getWindow(com.vis.configuration.ConfigInfo.RoiManager) != null) {
			com.vis.core.view.D2.roi.RoiObjManager.getInstance().updateState();
		}
	}

	/**
	 * 特定のRoiIDを持つROIを全スライドから一度削除し、 DBの最新状態に基づいて正しい次元・空間へ再分配（再描画）します。
	 */
	public void redispatchRoi(String targetRoiId) {
		if (slides == null || slides.isEmpty() || targetRoiId == null)
			return;

		// 1. 全スライドのキャンバスから、該当のROIを完全に除去する
		for (SlideGlass sg : slides.values()) {
			if (sg != null) {
				com.vis.core.view.D2.ui.glasses.CanvasGlass cg = (com.vis.core.view.D2.ui.glasses.CanvasGlass) sg
						.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
				if (cg != null) {
					java.util.ArrayList<com.vis.core.view.D2.roi.RoiObj> roiset = cg.getRoiSet();
					if (roiset != null) {
						java.util.Iterator<com.vis.core.view.D2.roi.RoiObj> it = roiset.iterator();
						boolean removed = false;
						while (it.hasNext()) {
							com.vis.core.view.D2.roi.RoiObj r = it.next();
							if (targetRoiId.equals(r.getProperty(com.vis.configuration.RoiDBKey.RoiID.name()))) {
								it.remove();
								// CanvasGlass がこのROIをアクティブとして保持している場合は解放
								if (cg.getCurrentRoi() == r) {
									cg.setCurrentRoi2NULL();
								}
								removed = true;
							}
						}
						// 削除が行われたスライドはキャンバスを再描画して古い線を消す
						if (removed) {
							cg.repaint();
						}
					}
				}
			}
		}

		// 2. DBから対象のROIコンテキストを再ロード
		com.vis.db.DatabaseHandler db = com.vis.db.DatabaseHandler.getInstance();
		if (db == null)
			return;

		// シリーズ内の全ROIを取得し、対象のRoiIDだけをフィルタリング
		java.util.ArrayList<java.util.HashMap<String, Object>> seriesRois = db.loadRoiContextFromSeries(patID, studyUID,
				seriesUID);
		if (seriesRois == null || seriesRois.isEmpty())
			return;

		java.util.HashMap<String, Object> targetRoiCtx = null;
		for (java.util.HashMap<String, Object> ctx : seriesRois) {
			if (targetRoiId.equals(ctx.get("RoiID"))) {
				targetRoiCtx = ctx;
				break;
			}
		}

		if (targetRoiCtx == null)
			return; // DBに存在しない場合は終了

		// 3. 取得した最新のプロパティを用いて再分配（ディスパッチ）
		@SuppressWarnings("unchecked")
		java.util.Map<String, String> metaProps = (java.util.Map<String, String>) targetRoiCtx
				.get(com.vis.configuration.RoiDBKey.RoiMetaProperties.name());
		if (metaProps == null)
			metaProps = new java.util.HashMap<>();

		String roiIppStr = metaProps.get("ReferenceImagePositionPatient");
		String roiForUid = metaProps.get("FrameOfReferenceUID");

		String dimCStr = metaProps.get("Dim_C");
		String dimTStr = metaProps.get("Dim_T");
		int targetC = (dimCStr != null && !dimCStr.trim().isEmpty()) ? Integer.parseInt(dimCStr) : -99;
		int targetT = (dimTStr != null && !dimTStr.trim().isEmpty()) ? Integer.parseInt(dimTStr) : -99;
		String originSop = (String) targetRoiCtx.get("SOPInstanceUID");

		com.vis.core.log.Log.logger.info(String.format(
				"[DEBUG-4: DISPATCH] Loaded from DB: Target C=%d, T=%d | OriginSOP=%s", targetC, targetT, originSop));

		for (java.util.Map.Entry<Integer, SlideGlass> entry : slides.entrySet()) {
			int zctIndex = entry.getKey();
			SlideGlass sg = entry.getValue();
			if (sg == null)
				continue;

			int[] currentZCT = calcZCTArrayFromIndex(zctIndex);
			int currentC = currentZCT[1];
			int currentT = currentZCT[2];

			// 判定A: 次元
			if (targetC != -1 && targetC != -99 && targetC != currentC)
				continue;
			if (targetT != -1 && targetT != -99 && targetT != currentT)
				continue;

			// 判定B: 空間
			boolean spatialMatch = isSpatialMatch(sg, roiIppStr, roiForUid, originSop);

			com.vis.core.log.Log.logger
					.fine(String.format("[DEBUG-4: MATCHING] Slide(Z=%d, C=%d, T=%d) | SpatialMatch=%b", currentZCT[0],
							currentC, currentT, spatialMatch));

			if (targetC == -99 || targetT == -99) {
				String currentSop = sg.getSOPInstanceUID();
				if (currentSop == null || !currentSop.equals(originSop))
					continue;
			} else if (!spatialMatch) {
				continue;
			}

			// マッチング成功 -> SlideGlassに追加して再描画
			com.vis.core.view.D2.roi.RoiObj revivedRoi = new com.vis.core.view.D2.roi.RoiConverter()
					.buildRoiObj(targetRoiCtx);
			if (revivedRoi != null) {

				if (dimCStr != null)
					revivedRoi.setProperty("Dim_C", dimCStr);
				String dimZStr = metaProps.get("Dim_Z"); // Dim_Zも取得しておく
				if (dimZStr != null)
					revivedRoi.setProperty("Dim_Z", dimZStr);
				if (dimTStr != null)
					revivedRoi.setProperty("Dim_T", dimTStr);

				revivedRoi.setSlideGlass(sg, false);
				sg.addRoiFromDB(revivedRoi);
				sg.repaintCanvasGlass(); // 新しい表示先に再描画をかける
			}
		}
	}

	public void loadSeries(DICOMNode seriesNode) {
		if (seriesNode == null || seriesNode.getLevel() != DICOMNode.SERIES) {
			Log.logger.log(Level.WARNING, "Cannot prepare slideglasses, no series node is inputed.");
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

		// サムネイルモードではファイルが1つしか読まれておらずMosaicも未展開のため、ファイルからフルロードし直す
		if (p.getViewMode() == ViewMode.Thumbnail && this.mode != ViewMode.Thumbnail) {
			loadSeries(patID, studyUID, seriesUID, sopUIDs, pathToImages);
		} else {
			// ★ 複製元の多次元プロパティを引き継ぐ（同モード間などの通常のコピー）
			this.nSlices = p.nSlices;
			this.nChannels = p.nChannels;
			this.nFrames = p.nFrames;
			constructSlideGlassesFromPraparat(p);
		}

		applyGlobalAutoWindow();// before slider.initContext
		currentSliceZCT = -1;

		updateSlidersVisibility();
		if (Utils.isDebug) {
			Log.logger.fine(slides.size() + " images loaded.");
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
		if (pathToImages == null || pathToImages.isEmpty()) {
			System.out.println("prap needs path to images..., return.");
			return;
		}
		viewPanel.removeAll();
		setInfo(patID, studyUID, seriesUID, sopUIDs, pathToImages);

		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		// ★非同期フローの要: SwingWorkerの導入
		javax.swing.SwingWorker<java.util.List<DicomImage>, Void> worker = new javax.swing.SwingWorker<java.util.List<DicomImage>, Void>() {

			@Override
			protected java.util.List<DicomImage> doInBackground() throws Exception {
				/*
				 * SimpleDicom, MultiFrame, PDF, MosaicDicom
				 */
				return buildDicomImagesInBackground(pathToImages);
			}

			@Override
			protected void done() {
				try {
					// バックグラウンド処理の結果（全画像データ）を取得
					java.util.List<DicomImage> dcmImages = get();

					slides = new java.util.concurrent.ConcurrentHashMap<>();
					java.util.List<SlideGlass> slideList = new java.util.ArrayList<>();

					// 2. EDT（UIスレッド）上で安全にSlideGlassを一括生成
					for (int i = 0; i < dcmImages.size(); i++) {
						SlideGlass sg = new SlideGlass(Praparat.this, dcmImages.get(i));
						slides.put(i, sg);
						slideList.add(sg);
					}

					// 3. ★全スライドが揃った状態で計算を実行（ここでスライダーの最大値や適正コントラストが確定する）
					organizeMultiDimensionalSlides(slideList);
					applyGlobalAutoWindow();

					// 4. UI状態の更新
					currentSliceZCT = -1;
					updateSlidersVisibility(); // ここでスライダーが正しく表示される

					// ==========================================================
					// 非同期ロードが完全に完了し、SlideGlassが生成された直後に
					// サムネイルモードであればテキストとアノテーションを確実に非表示にする
					// ==========================================================
					if (mode == ViewMode.Thumbnail) {
						setTextVisible(false);
						setAnnotationVisible(false);
					}

					loadRoisFromDB();

					// ==========================================================
					// ★【追加コード】
					// 非同期ロードが完了する前に、すでに呼び出し元（BirdsEyeView）から
					// 選択命令（selected=true）を受け取っていた場合、
					// 新しく出揃ったすべてのSlideGlassへその選択状態を確実に強制同期させます。
					// ==========================================================
					if (isSelected()) {
						setSelectionState(true);
					}

					// 5. 初回描画のトリガー（ここで初めて画像が描画される）
					if (!isShowGridViewOn()) {
						doSingleGridLayout();
					} else {
						doFilmGridLayout(filmGridColumns);
					}

				} catch (Exception e) {
					com.vis.core.log.Log.logger.log(java.util.logging.Level.SEVERE, "Failed to load series", e);
				} finally {
					SwingUtilities.invokeLater(() -> {
						setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
						MainScreen ms = MainScreen.getInstance();
						if (ms != null) {
							ms.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.DEFAULT_CURSOR));
							BirdsEyeView bev = ms.getBirdsEyeView();
							bev.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.DEFAULT_CURSOR));
							bev.getThumbnailListView()
									.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.DEFAULT_CURSOR));
						}
					});
				}
			}
		};
		// 非同期処理を開始
		worker.execute();
	}

	/**
	 * SlideGlasses that created by this method, Dicom attributes keeps minimally.
	 * 
	 * @param images
	 */
	public void loadSeries(ImagePlus images, boolean sortZCT) {
		if (images == null || images.getStackSize() == 0) {
			if (Utils.isDebug)
				System.out.println("praparat needs images..., return.");
			return;
		}
		currentSliceZCT = -1;
		updateInfoLabel(-1, -1, "-1", new double[] { -1, -1 }, -1, -1);

		String patID = GDicomTools.getTag(images, Tag.PatientID);
		String studyUID = GDicomTools.getTag(images, Tag.StudyInstanceUID);
		String seriesUID = GDicomTools.getTag(images, Tag.SeriesInstanceUID);
		String[] sopUIDs = new String[images.getNSlices()];
		// 全次元のサイズを取得
		int cTotal = images.getNChannels();
		int zTotal = images.getNSlices();
		int tTotal = images.getNFrames();
		int n = 0;
		for (int t = 1; t <= tTotal; t++) {
			for (int z = 1; z <= zTotal; z++) {
				for (int c = 1; c <= cTotal; c++) {
					images.setPositionWithoutUpdate(c, z, t);
					String sopInstUid = GDicomTools.getTag(images, z, c, t, Tag.SOPInstanceUID);
					if (sopInstUid == null || sopInstUid.trim().length() == 0) {
						sopInstUid = UIDUtils.createUID();
					} else {
						sopUIDs[n++] = sopInstUid.trim();// fail safe
					}
				}
			}
		}

		images.setSlice(1);// back to first.
		String refUID = GDicomTools.getTag(images, "0020,0052");
		setInfo(patID, studyUID, seriesUID, sopUIDs, refUID, null/* keep null file paths */);

		constructSlideGlassesFromImagePlus(images, sortZCT);
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

		currentSliceZCT = -1;
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
		loadSeries(pid, studyUID, seriesUID, sopUIDs, dcm_paths);

		applyGlobalAutoWindow();// before slider.initContext
		updateSlidersVisibility();

		if (Utils.isDebug) {
			System.out.println(slides.size() + " images loaded.");
		}
	}

	// 旧 loadSlideGlassFromMultiFrame を改修
	private java.util.List<DicomImage> loadDicomImagesFromMultiFrame(String path2dcm, DICOMBackend backend) {
		java.util.List<DicomImage> extractedFrames = new java.util.ArrayList<>();
		DicomReader video_reader_ = DicomReader.newDicomReader(backend);
		video_reader_.read(path2dcm, false);
		final UID u = video_reader_.checkTSUID();
		final DicomObject fmi = video_reader_.getFileMetaInfomation();
		final DicomObject header = video_reader_.getHeader();
		int size = header.getInt(Tag.NumberOfFrames, -1);
		for (int j = 0; j < size; j++) {
			DicomObject instHeader = DicomObject.newDicomObject(header, backend);
			instHeader.setInt(Tag.InstanceNumber, VR.IS, (j + 1));
			DicomImage frame = DicomImage.newDicomImage(path2dcm, instHeader, fmi, u, backend);
			extractedFrames.add(frame);
		}
		return extractedFrames;
	}

	private java.util.List<DicomImage> loadDicomImagesFromPDF(String path2dcm, DicomObject header, DicomObject fmi,
			DICOMBackend backend) {
		java.util.List<DicomImage> extractedFrames = new java.util.ArrayList<>();
		PDFReader pdfReader = new PDFReader(new File(path2dcm)/* read dicom */);
		isMultiFrame = true;// always treats multi
		isPDF = true;// fail safe
		boolean isThumbnail = getViewMode() == ViewMode.Thumbnail;
		int size = isThumbnail ? 1 : pdfReader.getPDFPageCount();
		// if thumbnail, load only one frame
		if (isThumbnail) {
			Log.logger.fine("Praparat view mode is Thumbnail, PDF will be loaded only first page.");
		}
		for (int j = 0; j < size; j++) {
			BufferedImage page = pdfReader.renderPDFPage(j);
			DicomObject instHeader = DicomObject.newDicomObject(header, backend);
			instHeader.setInt(Tag.Instance​Number, VR.IS, (j + 1));
			instHeader.setInt(Tag.Columns, VR.US, page.getWidth());
			instHeader.setInt(Tag.Rows, VR.US, page.getHeight());
			instHeader.setInt(Tag.Samples​Per​Pixel, VR.US, 3);
			instHeader.setInt(Tag.Bits​Allocated, VR.US, 8);
			instHeader.setInt(Tag.Bits​Stored, VR.US, 8);
			instHeader.setInt(Tag.High​Bit, VR.US, 7);
			instHeader.setString(Tag.Photometric​Interpretation, VR.CS, "RGB");
			instHeader.setInt(Tag.Planar​Configuration, VR.US, 0);
			DicomImage frame = DicomImage.newDicomImage(path2dcm, instHeader, fmi, UID.ExplicitVRLittleEndian, backend);
			extractedFrames.add(frame);
		}
		return extractedFrames;
	}

	private java.util.List<DicomImage> loadDicomImagesFromMosaic(String path2dcm, DICOMBackend backend) {
		java.util.List<DicomImage> extractedFrames = new java.util.ArrayList<>();
		DicomReader reader = DicomReader.newDicomReader(backend);
		reader.read(path2dcm, true); // ピクセルデータも読み込む
		DicomObject header = reader.getHeader();
		DicomObject fmi = reader.getFileMetaInfomation();
		UID tsUID = reader.checkTSUID();

		// 0x0019,100a (Siemens Private): NumberOfImagesInMosaic
		int numOfImages = header.getInt(0x0019100a, -1);
		if (numOfImages <= 0) {
			Log.logger.warning("Mosaic format detected but NumberOfImagesInMosaic is invalid.");
			return null;
		}

		int mosaicCols = header.getInt(Tag.Columns, 0);
		int mosaicRows = header.getInt(Tag.Rows, 0);
		int gridSize = (int) Math.ceil(Math.sqrt(numOfImages));
		int sliceW = mosaicCols / gridSize;
		int sliceH = mosaicRows / gridSize;

		DicomImage mosaicDcm = DicomImage.newDicomImage(path2dcm, header, fmi, tsUID, backend);
		mosaicDcm.ensurePixelDataLoaded();
		ImageProcessor mosaicIp = mosaicDcm.getImageProcessor(0);

		// ★ 空間座標再計算のための準備
		double[] ipp = header.getDoubles(Tag.ImagePositionPatient);
		double[] iop = header.getDoubles(Tag.ImageOrientationPatient);
		double spacing = header.getDouble(Tag.SpacingBetweenSlices, header.getDouble(Tag.SliceThickness, 1.0));

		final double nx, ny, nz;
		if (iop != null && iop.length == 6) {
			double tempNx = iop[1] * iop[5] - iop[2] * iop[4];
			double tempNy = iop[2] * iop[3] - iop[0] * iop[5];
			double tempNz = iop[0] * iop[4] - iop[1] * iop[3];
			double len = Math.sqrt(tempNx * tempNx + tempNy * tempNy + tempNz * tempNz);
			// 一度だけ代入する
			nx = tempNx / len;
			ny = tempNy / len;
			nz = tempNz / len;
		} else {
			// デフォルト値もここで一度だけ代入する
			nx = 0;
			ny = 0;
			nz = 1;
		}

		final int baseInstNo = header.getInt(Tag.InstanceNumber, 1);

		for (int index = 0; index < numOfImages; index++) {
			int colIndex = index % gridSize;
			int rowIndex = index / gridSize;
			int x = colIndex * sliceW;
			int y = rowIndex * sliceH;

			// 1. スライス画像のクロップ
			ImageProcessor sliceIp = mosaicIp.duplicate();
			sliceIp.setRoi(x, y, sliceW, sliceH);
			sliceIp = sliceIp.crop();

			// 2. ヘッダーの複製と書き換え (Rows, Columnsを上書き)
			DicomObject sliceHeader = DicomObject.newDicomObject(header, backend);
			sliceHeader.setInt(Tag.Columns, VR.US, sliceW);
			sliceHeader.setInt(Tag.Rows, VR.US, sliceH);

			// 3. Z座標(IPP)の再計算と適用
			if (ipp != null && ipp.length == 3) {
				double newX = ipp[0] + nx * spacing * index;
				double newY = ipp[1] + ny * spacing * index;
				double newZ = ipp[2] + nz * spacing * index;
				// ※DICOMBackendの実装に合わせて適宜調整してください
				sliceHeader.setDouble(Tag.ImagePositionPatient, VR.DS, new double[] { newX, newY, newZ });
			}

			// 時系列・空間を区別するための擬似インスタンス番号
			sliceHeader.setInt(Tag.InstanceNumber, VR.IS, (baseInstNo - 1) * numOfImages + index + 1);

			// 4. DicomImageの再構築とメモリ上のピクセルデータの流し込み
			DicomImage sliceDcm = DicomImage.newDicomImage(path2dcm, sliceHeader, fmi, tsUID, backend);
			int samples = sliceIp instanceof ColorProcessor ? 3 : 1;
			sliceDcm.setPixelData(0, sliceW, sliceH, samples, mosaicDcm.getBitsAllocated(), sliceIp.getPixels());

			extractedFrames.add(sliceDcm);
		}
		return extractedFrames;
	}

	private java.util.List<DicomImage> buildDicomImagesInBackground(java.util.List<String> imgFiles) {
		java.util.List<DicomImage> resultImages = new java.util.ArrayList<>();
		DICOMBackend backend = DICOMBackend.getCurrent();

		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
				.newFixedThreadPool(com.vis.core.util.Utils.availableProcessors());
		java.util.List<java.util.concurrent.Future<java.util.List<DicomImage>>> futures = new java.util.ArrayList<>();

		// ★追加: スレッドセーフなフラグ収集用の変数
		java.util.concurrent.atomic.AtomicBoolean detectMultiFrame = new java.util.concurrent.atomic.AtomicBoolean(
				false);
		java.util.concurrent.atomic.AtomicBoolean detectPDF = new java.util.concurrent.atomic.AtomicBoolean(false);

		for (String path : imgFiles) {
			futures.add(executor.submit(() -> {
				java.util.List<DicomImage> localList = new java.util.ArrayList<>();
				DicomReader reader = DicomReader.newDicomReader(backend);
				reader.read(path, false);
				DicomObject header = reader.getHeader();
				DicomObject fmi = reader.getFileMetaInfomation();
				String sopClassUID = header.getString(Tag.SOPClassUID, "");
				UID tsUID = reader.checkTSUID();

				// モザイク判定ロジック
				boolean isMosaic = false;
				String[] imageTypes = header.getStrings(Tag.ImageType);
				if (imageTypes != null) {
					for (String type : imageTypes) {
						if (type != null && type.trim().equalsIgnoreCase("MOSAIC")) {
							isMosaic = true;
							break;
						}
					}
				} else {
					String it = header.getString(Tag.ImageType, "");
					if (it != null && it.contains("MOSAIC"))
						isMosaic = true;
				}

				if (sopClassUID.equals(UID.EncapsulatedPDFStorage.uid())) {
					// ★修正: スレッドセーフにフラグを立てる
					detectPDF.set(true);
					localList.addAll(loadDicomImagesFromPDF(path, header, fmi, backend));
					return localList;
				}

				DicomImage dcm = DicomImage.newDicomImage(path, header, fmi, tsUID, backend);
				boolean localIsMultiFrame = dcm.isMultiFrame() && dcm.getNumOfFrames() > 1;

				// ★修正: ローカルでMultiFrameを検知したらフラグを立てる
				if (localIsMultiFrame) {
					detectMultiFrame.set(true);
				}

				if (isMosaic && this.mode != ViewMode.Thumbnail) {
					localList.addAll(loadDicomImagesFromMosaic(path, backend));
				} else if (!localIsMultiFrame) {
					localList.add(DicomImage.newDicomImage(path, false, backend));
				} else {
					localList.addAll(loadDicomImagesFromMultiFrame(path, backend));
				}
				return localList;
			}));
		}

		// 全てのスレッドの読み込み完了を待機し、結果を結合する
		for (java.util.concurrent.Future<java.util.List<DicomImage>> future : futures) {
			try {
				resultImages.addAll(future.get());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		executor.shutdown();

		// ★追加: 全スレッドが安全に完了した後で、Praparatのインスタンス変数に反映する
		if (detectMultiFrame.get())
			this.isMultiFrame = true;
		if (detectPDF.get())
			this.isPDF = true;

		return resultImages;
	}

	public ImagePlus processCropRectangle(boolean show) {
		SlideGlass sg = getCurrentSlide();
		if (sg == null)
			return null; // 空きマスの場合は処理しない
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
		if (sg == null)
			return null; // 空きマスの場合は処理しない
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
			if (sg != null)
				sg.flipHF();
		} else {
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				if (sg == null) {
					continue; // 空きマスの場合は処理しない
				}
				sg.flipHF();
			}
		}
	}

	public void processFlipLR() {
		if (!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			if (sg != null)
				sg.flipLR();
		} else {
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				if (sg == null)
					continue; // 空きマスの場合は処理しない
				sg.flipLR();
			}
		}
	}

	public void processInvertImages() {
		if (!isProcessSeries()) {
			SlideGlass sg = getCurrentSlide();
			if (sg == null)
				return; // 空きマスの場合は処理しない
			sg.invert();
		} else {
			for (Integer key : slides.keySet()) {
				SlideGlass sg = slides.get(key);
				if (sg == null)
					continue; // 空きマスの場合は処理しない
				sg.invert();
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

		// ★ 修正：現在表示しているスライスがまだドラッグ移動（パン）されていない初期表示状態なら、
		// 先読みする前後のスライスには絶対座標を渡さず null を渡して、それぞれのスライスに綺麗に中央配置させる
		Point tempOrigin = current.getDisplayImageOriginXY();
		if (!current.panningFlag || (tempOrigin.x == 0 && tempOrigin.y == 0)) {
			tempOrigin = null;
		}

		final Point syncOrigin = tempOrigin;
		final boolean processSeries = isProcessSeries();

		// ★ 最新のリクエストIDを発行
		final int requestId = latestCacheRequest.incrementAndGet();

		prefetchExecutor.submit(() -> {
			// ★ キューから取り出された時点で、より新しいリクエストが来ていたらキャンセル
			if (requestId != latestCacheRequest.get()) {
				return;
			}

			// ★ 修正: totalSizeではなく全体容量(capacity)を使ってリングバッファを回す
			int capacity = nChannels * nSlices * nFrames;

			// 1. 周回を考慮した範囲の画像をロード
			for (int i = -PREFETCH_RANGE; i <= PREFETCH_RANGE; i++) {
				if (requestId != latestCacheRequest.get())
					return;
				int targetIndex = (currentIndex + i + capacity) % capacity;
				// ★修正: 現在表示中の画像(currentIndex)に対する非同期のUI上書きを防ぎ、カクつきをなくす
				boolean shouldSync = processSeries && (targetIndex != currentIndex);
				
				realizeImage(targetIndex, shouldSync, syncMag, syncRot, syncMin, syncMax, syncOrigin);
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
			if (capacity > (PREFETCH_RANGE * 2 + 1) && requestId == latestCacheRequest.get()) {
				// ★ 修正: keysetで回すことで、存在する要素だけを安全に解放
				for (Integer j : slides.keySet()) {
					if (!isWithinCircularRange(j, currentIndex, capacity, PREFETCH_RANGE)) {
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
			int buffer = 15;
			// int capacity = nChannels * nSlices * nFrames; // ★ 修正
			for (Integer j : slides.keySet()) { // ★ 修正：keySetで回す
				if (j < (firstIdx - buffer) || j > (lastIdx + buffer)) {
					unloadImage(j);
				}
			}
		});
	}

	/**
	 * 本来のシリーズのメタデータ(baseLabel)をベースにしつつ、 空間位置情報(spaceLabelから抽出したIPP, IOP,
	 * SliceLocation等)のみを正確に上書き融合したSliceLabelを生成します。
	 */
	private String mergeDicomMetaLabels(String baseLabel, String spaceLabel) {
		if (baseLabel == null || "Empty".equals(baseLabel))
			return spaceLabel;
		if (spaceLabel == null || "Empty".equals(spaceLabel))
			return baseLabel;

		// 空間ドナーから位置情報タグの行を抽出
		String[] spaceLines = spaceLabel.split("\n");
		String ippLine = null;
		String iopLine = null;
		String locLine = null;

		for (String line : spaceLines) {
			if (line.contains("0020,0032"))
				ippLine = line; // ImagePositionPatient
			if (line.contains("0020,0037"))
				iopLine = line; // ImageOrientationPatient
			if (line.contains("0020,0041"))
				locLine = line; // SliceLocation
		}

		// ベース（シリーズ共通メタデータ）の空間タグ行のみを差し替える
		String[] baseLines = baseLabel.split("\n");
		StringBuilder sb = new StringBuilder();
		for (String line : baseLines) {
			if (ippLine != null && line.contains("0020,0032")) {
				sb.append(ippLine).append("\n");
			} else if (iopLine != null && line.contains("0020,0037")) {
				sb.append(iopLine).append("\n");
			} else if (locLine != null && line.contains("0020,0041")) {
				sb.append(locLine).append("\n");
			} else {
				sb.append(line).append("\n");
			}
		}
		return sb.toString();
	}

	/**
	 * 
	 * @param index : zct
	 */
	private void unloadImage(int index) {
		SlideGlass sg = slides.get(index);
		if (sg != null && sg.getDicomImage() != null) {
			boolean isFileBacked = (getImageFileLocations() != null && !getImageFileLocations().isEmpty());
			boolean isUnpackedMosaic = isMosaic(sg) && this.mode != ViewMode.Thumbnail;
			if (isFileBacked && !isUnpackedMosaic) {
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
	 * 指定したインデックスの画像をメモリ上に実体化（解凍含む）させる
	 */
	private void realizeImage(int index /* zct */, boolean processSeries, Double syncMag, Double syncRot,
			Double syncMin, Double syncMax, Point syncOrigin) {

		int capacity = nChannels * nSlices * nFrames;
		if (index < 0 || index >= capacity) {
			return;
		}
		SlideGlass sg = slides.get(index);
		// ★ 該当座標にSEGマスクが存在しない（スパースデータの空きマス）場合はスキップ
		if (sg == null) {
			return;
		}
		DicomImage dcmimg = sg.getDicomImage();
		isMultiFrame = dcmimg.isMultiFrame();
		isMultiFrame = isPDF == true ? true : isMultiFrame;
		if (sg.getOriginalImage() == null) {
			// Praparat全体がファイルソースを持っているかで判定
			boolean isFileBacked = (getImageFileLocations() != null && !getImageFileLocations().isEmpty());
			int frame_pos = isMultiFrame ? (sg.getHeader().getInt(Tag.InstanceNumber, 1) - 1) : 0;
			boolean isUnpackedMosaic = isMosaic(sg) && this.mode != ViewMode.Thumbnail;

			// モザイク画像(fMRI)の場合はファイルからの再ロードを行わず、メモリのデータを直接使う
			if (isFileBacked && !isUnpackedMosaic) {
				// 1. ファイルからピクセルを読み込む
				if (dcmimg.ensurePixelDataLoaded()) {
					ImagePlus im = new ImagePlus("" + index, dcmimg.getImageProcessor(frame_pos));
					sg.imageSpecimen.setOriginalImage(im);
				}
			} else {
				// ★ ImagePlusから生成され、パスはないがメモリ上に画像データがある場合、およびMosaicの処理
				if (dcmimg != null) {
					// ※ すでにメモリ上にあるピクセルデータを取得して表示に使う
					ImageProcessor ip = dcmimg.getImageProcessor(frame_pos);
					if (ip != null) {
						ImagePlus im = new ImagePlus("" + index, ip);
						sg.imageSpecimen.setOriginalImage(im);
					}
				}
			}

			sg.initCalibrationAndLUT();

			String modalityStr = sg.getHeader().getString(Tag.Modality, "");
			double realMaxPixelVal = 0.0;
			if (sg.getOriginalImage() != null && sg.getOriginalImage().getProcessor() != null) {
				// DO NOT USE processor.getMax()
				realMaxPixelVal = sg.getOriginalImage().getProcessor().getStatistics().max;
			}

			// ピクセル最大値が1.0、またはSEG指定の場合をマスク画像と判定
			// See, PixelDataDecoder
			boolean isMask = "SEG".equals(modalityStr) || dcmimg.getBitsAllocated() == 1 || realMaxPixelVal == 1.0;

			if (isMask) {
				sg.currentMin = 0.0;
				// 実際のピクセル最大値(1.0)に合わせて白レベルを設定
				sg.currentMax = (realMaxPixelVal > 0.0) ? realMaxPixelVal : 1.0;

				// 同期バグ（誤上書き）の防止
				syncMin = null;
				syncMax = null;
			} else {

				WwWlState savedState = getWwWlState(index);

				if (sg.isRGB()) {
					// RGBの場合は各チャンネルの設定を復元
					applyStateToSlideGlass(sg, -1, savedState.getMin(-1), savedState.getMax(-1));
					applyStateToSlideGlass(sg, 0, savedState.getMin(0), savedState.getMax(0));
					applyStateToSlideGlass(sg, 1, savedState.getMin(1), savedState.getMax(1));
					applyStateToSlideGlass(sg, 2, savedState.getMin(2), savedState.getMax(2));
				} else {
					// モノクロ画像
					double savedMin = savedState.getMin(-1);
					double savedMax = savedState.getMax(-1);
					sg.changeWindowingByMinMax(savedMin, savedMax);
				}

			}

			// 画像が実体化されたタイミングでフュージョンを動的適用
			if (isFusionMode) {
				applyFusionOverlayToSlide(index, sg);
			}
			
			final Double syncMin_ = syncMin;
			final Double syncMax_ = syncMax;

			if (processSeries) {
				// ★ 修正: スレッド競合を防ぐため、UI状態の変更は必ずEDT(メインスレッド)に委譲する
				SwingUtilities.invokeLater(() -> {
					// move, zoom, rotate, windowing
					if (syncMag != null && Double.isFinite(syncMag))
						sg.zoom(syncMag, false/* dummy */);
					if (syncRot != null && Double.isFinite(syncRot))
						sg.setAbsoluteRotate(syncRot);
					if ((syncMin_ != null && Double.isFinite(syncMin_)) && (syncMax_ != null && Double.isFinite(syncMax_))) {
						sg.changeWindowingByMinMax(syncMin_, syncMax_);
					}
					// finally set origin
					if (syncOrigin == null || !sg.panningFlag) {
						/*
						 * update origin if not panning.
						 */
						sg.setSize(getViewPanelWidth(), getViewPanelHeight());
					} else {
						sg.setDisplayOrigin(syncOrigin);
					}
				});
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
		loadSeries(imp, false/* AS-IS order */);
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

	// UIから、あるいはユーザー操作でROIが削除された時の処理
	public void removeRoi(RoiObj roiToRemove) {
		boolean deleted = false;
		for (SlideGlass sg : slides.values()) {
			deleted = sg.deleteRoi(roiToRemove);
		}
		if (deleted) {
			repaint();
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

		if (isFusionMode) {
			disableFusionMode();
		}

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

	/**
	 * DICOMタグに定義されている Window Center / Width に戻すリセット処理
	 */
	public void resetWindow() {
		if (isProcessSeries()) {
			java.util.concurrent.ConcurrentHashMap<Integer, SlideGlass> allSlides = getAllSlides();
			if (allSlides != null) {
				for (Integer key : allSlides.keySet()) {
					SlideGlass sg = allSlides.get(key);
					if (sg != null) {
						sg.resetContrast(); // DICOMタグ(Window Center/Width)から初期コントラストを復元

						// ★修正: RGB個別の状態も確実に初期状態へリセットする
						WwWlState state = getWwWlState(key);
						state.resetToDefault();
						state.setValues(-1, sg.currentMin, sg.currentMax); // Allのみ復元値で上書き

						// リセットされたストレージでLUTを再合成して描画更新
						if (sg.isRGB())
							sg.imageSpecimen.updateDisplayImage();
					}
				}
			}
		} else {
			int zct = getCurrentSlidePos();
			SlideGlass sg = slides.get(zct);
			if (sg != null) {
				sg.resetContrast();

				WwWlState state = getWwWlState(zct);
				state.resetToDefault();
				state.setValues(-1, sg.currentMin, sg.currentMax);

				if (sg.isRGB())
					sg.imageSpecimen.updateDisplayImage();
			}
		}
		repaint();
	}

	/**
	 * SUV校正係数を設定し、配下のすべてのSlideGlassに伝搬して画面を更新します。
	 * 
	 * @param factor 算出したSUV Factor
	 */
	public void setSUVFactor(double factor, String unit) {
		this.suvFactor = factor;
		Log.logger.info("Praparat: SUV Factor set to " + factor + ". Propagating to all SlideGlasses...");

		// 1. 配下のすべてのSlideGlassに係数を伝搬
		if (this.slides != null) { // ※ 実際のコレクション名（getAllSlides()等）に合わせて調整してください
			for (SlideGlass sg : this.slides.values()) {
				if (sg != null) {
					sg.setSUVFactor(factor, unit);
				}
			}
		}
		repaint();
	}

	/**
	 * 現在保持しているSUV校正係数を取得します。
	 */
	public double getSUVFactor() {
		return this.suvFactor;
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
			String formattedCal = formatPixelValue(calibrated_v);
			String formattedRaw = formatPixelValue(raw_v);
			updateInfoLabel(pointOnOrg, formattedCal + " (" + formattedRaw + ")", scaleXY, mag, rotate);
		} else {
			String[] rgbAndColor = (String[]) val;
			String r = rgbAndColor[0];
			String g = rgbAndColor[1];
			String b = rgbAndColor[2];
			updateInfoLabel(pointOnOrg, "(R,G,B) " + r + ", " + g + ", " + b, scaleXY, mag, rotate);
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
					if (s != null) {
						s.setAnnotationVisible(v);
					}
				}
			} else {
				/*
				 * change state of only selected.
				 */
				List<SlideGlass> selected = getSelectedGlasses();
				for (SlideGlass s : selected) {
					if (s != null) {
						s.setAnnotationVisible(v);
					}
				}
			}
		} else {
			if (isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					if (s != null) {
						s.setAnnotationVisible(v);
					}
				}
			} else {
				/*
				 * change state of current showing glass.
				 */
				SlideGlass sg = getCurrentSlide();
				if (sg != null) {
					sg.setAnnotationVisible(v);
				}
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

	@Override
	public void setCursor(java.awt.Cursor cursor) {
		super.setCursor(cursor);
		ConcurrentHashMap<Integer, SlideGlass> slides = getAllSlides();
		if (slides != null) {
			for (SlideGlass sg : slides.values()) {
				if (sg != null) {
					sg.setCursor(cursor);
				} else {
					for (Component c : viewPanel.getComponents()) {
						c.setCursor(cursor);
					}
				}
			}
		}
		repaint();
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
	void setImagePosition(int sliceZctIndex) {
		if (slides == null) {
			return;
		}
		if (isShowGridViewOn()) {
			currentSliceZCT = sliceZctIndex;
			return;
		}

		setWaitCursor(true);

		try {
			if (currentSliceZCT == -1) {
				currentSliceZCT = sliceZctIndex;
				Double syncMag = 1.0;
				Double syncRot = 0.0;
				Double syncMin = null;
				Double syncMax = null;
				Point syncOrigin = null;
				// ★修正：自身に対する遅延UI更新を防ぐため、第2引数を false にする
				realizeImage(currentSliceZCT, false, syncMag, syncRot, syncMin, syncMax, syncOrigin);
				
				SlideGlass currentGlass = this.slides.get(currentSliceZCT);
				if (currentGlass == null) {
					viewPanel.removeAll();
					viewPanel.add(getEmptyGlassPanel(), BorderLayout.CENTER);
					viewPanel.revalidate();
					viewPanel.repaint();
					updateInfoLabel(-1, -1, "No Data", new double[] { -1, -1 }, -1, -1);
					return;
				}

				for (SlideGlass sg : slides.values()) {
					if (sg == null)
						continue;

					if (syncMag != null && Double.isFinite(syncMag))
						sg.zoom(syncMag, false);
					if (syncRot != null && Double.isFinite(syncRot))
						sg.setAbsoluteRotate(syncRot);
					if ((syncMin != null && Double.isFinite(syncMin))
							&& (syncMax != null && Double.isFinite(syncMax))) {
						sg.changeWindowingByMinMax(syncMin, syncMax);
					} else if (sg.currentMin != sg.currentMax) {
						sg.changeWindowingByMinMax(sg.currentMin, sg.currentMax);
					}

					if (syncOrigin == null) {
						sg.setSize(getViewPanelWidth(), getViewPanelHeight());
					} else {
						sg.setDisplayOrigin(syncOrigin);
					}
				}

				boolean sizeChanged = (currentGlass.getWidth() != viewPanel.getWidth()
						|| currentGlass.getHeight() != viewPanel.getHeight());

				viewPanel.removeAll();
				viewPanel.add(currentGlass, 0);

				java.awt.Component cover1 = (java.awt.Component) currentGlass.getGlassAt(SlideGlass.EVENT_LAYER);
				if (cover1 != null)
					cover1.requestFocusInWindow();

				// ★追加: 初回表示時もホバー状態を安全にリセットし、ステートリークを防ぐ
				currentGlass.setFocusGained(false);

				if (sizeChanged) {
					currentGlass.setSize(viewPanel.getWidth(), viewPanel.getHeight());
					viewPanel.revalidate();
				}
				viewPanel.repaint();

				manageCache(currentSliceZCT);

				currentGlass.updateDisplayImage();
				currentGlass.repaint();
				currentGlass.requestFocus();
				return;
			}

			if (currentSliceZCT == sliceZctIndex) {
				return;
			}

			SlideGlass oldGlass = this.slides.get(currentSliceZCT);

			double syncMag = 1.0;
			double syncRot = 0.0;
			Double syncMin = null;
			Double syncMax = null;
			Point syncOrigin = null;

			boolean wasHovered = false; // ★ 追加

			if (oldGlass != null) {
				syncMag = oldGlass.getMagnification();
				syncRot = oldGlass.getRotateAngle();
				syncMin = oldGlass.currentMin;
				syncMax = oldGlass.currentMax;

				if (!oldGlass.panningFlag) {
					syncOrigin = null;
				} else {
					Point p = oldGlass.getDisplayImageOriginXY();
					syncOrigin = (p.x == 0 && p.y == 0) ? null : p;
				}

				// ★追加: ステートリーク対策。コンポーネントが剥がされる前にホバー状態を記憶し、強制解除する
				wasHovered = oldGlass.isHovered();
				oldGlass.setFocusGained(false);
			}

			currentSliceZCT = sliceZctIndex;
			SlideGlass nextGlass = this.slides.get(currentSliceZCT);
			if (nextGlass == null) {
				viewPanel.removeAll();
				viewPanel.add(getEmptyGlassPanel(), BorderLayout.CENTER);
				viewPanel.revalidate();
				viewPanel.repaint();
				updateInfoLabel(-1, -1, "No Data", new double[] { -1, -1 }, -1, -1);
				return;
			}

			// Series同期OFFの場合は、抽出したパラメータを自身のものに書き換える
			if (!isProcessSeries()) {
				syncMag = nextGlass.getMagnification();
				syncRot = nextGlass.getRotateAngle();
				syncMin = nextGlass.currentMin;
				syncMax = nextGlass.currentMax;
				if (nextGlass.panningFlag) {
					Point currentOrigin = nextGlass.getDisplayImageOriginXY();
					syncOrigin = (currentOrigin.x == 0 && currentOrigin.y == 0) ? null : currentOrigin;
				} else {
					syncOrigin = null;
				}
			}
			
			// 【検証ログ】実体化前の状態
			com.vis.core.log.Log.logger.info(String.format("[Paging Debug] Before realize: ZCT=%d, Mag=%.2f, Origin=%s", currentSliceZCT, syncMag, syncOrigin));

			// 1. 画像の実体化（未ロード時のみ発動する）
			// ★修正：自身に対する遅延UI更新(invokeLater)を防ぎ、二重適用のカクつきを完全になくす
			realizeImage(currentSliceZCT, false, syncMag, syncRot, syncMin, syncMax, syncOrigin);
			
			// ==========================================================
			// ★ 修正: 正しい処理順序に変更
			// ==========================================================

			// 2. ズーム計算の基準を正常にするため、"先"にサイズを確定させる
			nextGlass.setSize(viewPanel.getWidth(), viewPanel.getHeight());

			// 3. パラメータの適用 (サイズが確定しているので正確に計算される)
			if (Double.isFinite(syncMag))
				nextGlass.zoom(syncMag, false);
			if (Double.isFinite(syncRot))
				nextGlass.setAbsoluteRotate(syncRot);
			if (syncMin != null && syncMax != null && Double.isFinite(syncMin) && Double.isFinite(syncMax)) {
				nextGlass.changeWindowingByMinMax(syncMin, syncMax);
			}

			// パラメータ適用後に原点座標をセット
			nextGlass.setDisplayOrigin(syncOrigin);

			// 【検証ログ】パラメータ適用後の状態
			com.vis.core.log.Log.logger
					.info(String.format("[Paging Debug] After params: Size=%dx%d, Mag=%.2f, Origin=(%d,%d)",
							nextGlass.getWidth(), nextGlass.getHeight(), nextGlass.getMagnification(),
							nextGlass.getDisplayImageOriginXY().x, nextGlass.getDisplayImageOriginXY().y));

			// 4. 画面に追加する "前" に表示用バッファを更新し、未完成の画像がチラつくのを防ぐ
			nextGlass.updateDisplayImage();
						
			viewPanel.removeAll();
			viewPanel.add(nextGlass, 0);
			nextGlass.setSize(viewPanel.getWidth(), viewPanel.getHeight());

			java.awt.Component cover2 = (java.awt.Component) nextGlass.getGlassAt(SlideGlass.EVENT_LAYER);
			if (cover2 != null)
				cover2.requestFocusInWindow();

			// ★追加: ホイール操作時のチラつき防止 兼 過去のステートリークの確実なリセット
			nextGlass.setFocusGained(wasHovered);

			manageCache(currentSliceZCT);
			nextGlass.updateDisplayImage();
			nextGlass.requestFocus();

			viewPanel.revalidate();
			viewPanel.repaint();
		} finally {
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
	 * 
	 * @param pos 0 to n-1, zct.
	 */
	public void setImagePositionUsingSlider(int pos) {
		SwingUtilities.invokeLater(() -> {
			if (nChannels < 1 || nSlices < 1)
				return; // fail safe

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
	 * Praparat単体で2DViwerツール群を強制設定する場合に使用します（Anonymizer等用） -1でリセット。
	 */
	public void setLocalToolType(int toolType) {
		this.localToolType = toolType;
	}

	public void setLUT(LUT lut, String LutName) {
		colorBar.setLUT(lut);
		this.lut = lut;
		this.currentLutName = LutName;
		if (isProcessSeries()) {
			synchronized (slides) {
				for (Integer key : slides.keySet()) {
					SlideGlass sg = slides.get(key);
					if (sg != null) {
						sg.setLUT(this.lut);
					}
				}
			}
		}
		SlideGlass current = getCurrentSlide();
		if (current != null) {
			current.setLUT(this.lut);
			current.updateDisplayImage();
		}
		if (getViewPanel() != null) {
			getViewPanel().repaint();
		}
	}

	public void setNextSlice() {
		stepDimension("Slice", 1);
	}

	public void setPreviousSlice() {
		stepDimension("Slice", -1);
	}

	public void setReferenceLineMPR(ReferenceLineMPR refLine) {
		this.refLineMPR = refLine;
	}

	/**
	 * Praparat selection state See, SlideGlassMouseListener True if any one of the
	 * SlideGlasses is in the selected state.
	 */
	// 1. 外部からPraparatを強制的に選択/解除する場合（全SlideGlassもそれに従う）
	public void setSelectionState(boolean select) {
		this.selected = select;

		// 配下の全SlideGlassの状態も一斉に同期させる
		// サムネイルでも同様。
		if (slides != null) {
			for (SlideGlass sg : slides.values()) {
				if (sg != null) {
					// SlideGlassのメソッドを呼んで状態とボーダーを更新
					sg.setSelectionState(select);
				}
			}
		}
		showBorder(isFocusGained());
	}

	// SlideGlassの選択状態から、Praparatの選択状態を自動再計算するメソッド
	public void updateSelectionStateFromSlides() {
		if (mode == ViewMode.Thumbnail)
			return;

		// 1. シリーズ内に1つでも選択されたスライドがあるかをスキャン
		this.selected = false;
		if (slides != null) {
			for (SlideGlass sg : slides.values()) {
				if (sg != null && sg.isSelected()) {
					this.selected = true;
					break;
				}
			}
		}

		// 2. ★仕様の要
		// 自身の他スライドの選択状態によって「紫の単線ボーダー」を出す・出さないが
		// 動的に変わるため、選択変更があった際は配下の全SlideGlassにボーダーの描き直しを通知する
		if (slides != null) {
			for (SlideGlass sg : slides.values()) {
				if (sg != null) {
					sg.showBorder();
				}
			}
		}

		showBorder(isFocusGained());
	}

	// 3. 【新規】マウスでShift+クリックされたときのスマートなトグル処理
	public void toggleSelection(SlideGlass clickedSlide) {
		if (clickedSlide == null)
			return;

		// 次になりたい状態（現在の逆）
		boolean nextState = !clickedSlide.isSelected();

		if (isProcessSeries()) {
			// 【Series=true】Praparatと配下の全SlideGlassを一斉に切り替える
			setSelectionState(nextState);
		} else {
			// 【Series=false】クリックされたSlideGlassのみ切り替える
			clickedSlide.setSelectionState(nextState);
			// 全体（Praparat）の選択状態を再評価して、必要ならOffにする
			updateSelectionStateFromSlides();
		}
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
					if (s != null) {
						s.setTextVisible(v);
					}
				}
			} else {
				List<SlideGlass> selected = getSelectedGlasses();
				for (SlideGlass s : selected) {
					if (s != null) {
						s.setTextVisible(v);
					}
				}
			}
		} else {
			if (isProcessSeries()) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					if (s != null) {
						s.setTextVisible(v);
					}
				}
			} else {
				SlideGlass sg = getCurrentSlide();
				if (sg != null) {
					sg.setTextVisible(v);
				}
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
		if (uids == null) {
			return false;
		}
		try {
			if (uids[0].equals(pid) && uids[1].equals(studyUid) && uids[2].equals(seriesUid)) {
				return true;
			}
		} catch (Exception e) {
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
		// SlideGlass側で2層のステータスボーダーを描画するため、
		// Praparat側の外枠は二重線にならないよう、透明なパディング（EmptyBorder）にします。
		setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
	}

	public void showFirstImage() {
		// position range is 0 to n-1
		currentSliceZCT = -1;
		if (slider != null) {
			setImagePositionUsingSlider(0);
		} else {
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
	 * 値の大きさに応じて小数点以下の表示桁数をスマートに切り替えるヘルパーメソッド
	 */
	private String formatPixelValue(double value) {
		if (Double.isNaN(value))
			return "NaN";
		if (value == 0.0)
			return "0.00";

		double abs = Math.abs(value);
		if (abs >= 0.01) {
			// 0.01以上（通常のSUVや、大きなRAW値）は小数点以下2桁固定
			return String.format("%.2f", value);
		} else if (abs >= 0.0001) {
			// 0.01未満で0に近い値の場合は、有効数字を確保するため小数点以下4桁まで表示
			return String.format("%.4f", value);
		} else {
			// さらに極端に小さい値は指数表記 (例: 3.50e-5)
			return String.format("%.2e", value);
		}
	}

	/**
	 * UIのスライダー等から呼ばれるメソッド（Praparat内、またはコントローラーに実装）
	 * 
	 * @param newOpacity 0.0〜1.0
	 * @param newLUT     新しいカラーマップ（コントラスト変更用）
	 */
	public void updateFusionAppearance(double newOpacity, LUT newLUT) {
		if (slides == null || slides.size() == 0) {
			return;
		}
		for (Integer zct : slides.keySet()) {
			SlideGlass sg = slides.get(zct);
			if (sg == null) {
				continue;
			}
			ImagePlus org = sg.getOriginalImage();
			if (org == null || org.getOverlay() == null) {
				continue;
			}
			Overlay overlay = org.getOverlay();
			boolean updated = false;

			// Overlay内の全ROIを走査し、ImageRoiのみを更新する
			for (int i = 0; i < overlay.size(); i++) {
				ij.gui.Roi roi = overlay.get(i);
				if (roi instanceof ij.gui.ImageRoi) {
					ij.gui.ImageRoi imgRoi = (ij.gui.ImageRoi) roi;
					imgRoi.setOpacity(newOpacity);
					if (newLUT != null) {
						imgRoi.getProcessor().setLut(newLUT);
					}
					updated = true;
				}
			}
			if (updated) {
				// 画像を再描画して変更を即座に反映
				org.updateAndDraw();
			}
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
				if (sg != null) {
					sg.setSize(currentW, currentH);
				}
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
		if (sliderPanel == null)
			return;

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

		// C(Channel)スライダー
		if (nChannels > 1) {
			sliderPanel.add(channelSlider);
			channelSlider.initContext(nChannels);
			channelSlider.setSliderVisible(true);
			// ★ 変更：再生ボタンを表示する
			channelSlider.setCineButtonVisible(true);
		}

		// T(Time)スライダー
		if (nFrames > 1) {
			sliderPanel.add(frameSlider);
			frameSlider.initContext(nFrames);
			frameSlider.setSliderVisible(true);
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
		pendingTargetIndex = getCurrentSlideZCTIndex();

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

	public void enableFusionMode(ImagePlus fgImages) {
		if (fgImages == null || fgImages.getStackSize() == 0)
			return;

		com.vis.core.log.Log.logger.info("[Fusion Render] enableFusionMode called. Overlay: "
				+ (foregroundOverlay != null ? foregroundOverlay.getTitle() : "null"));

		this.isFusionMode = true;
		this.foregroundOverlay = fgImages;

		// すでにメモリ上に実体化されているスライド（現在表示中＋キャッシュ）に即座に適用
		for (Integer zct : this.slides.keySet()) {
			SlideGlass bgSg = this.slides.get(zct);
			if (bgSg != null && bgSg.getOriginalImage() != null) {
				applyFusionOverlayToSlide(zct, bgSg);
				bgSg.updateDisplayImage();
			}
		}
		repaint();
		com.vis.core.log.Log.logger.info("[Fusion Render] Fusion mode is ON and repaint() requested.");
	}

	/**
	 * 特定のSlideGlassに対して、動的にフュージョンオーバーレイを適用します。
	 */
	private void applyFusionOverlayToSlide(int bgZctIndex, SlideGlass bgSg) {
		if (!isFusionMode || foregroundOverlay == null)
			return;
		if (bgSg == null || bgSg.getOriginalImage() == null)
			return;

		int[] bgZctArray = this.calcZCTArrayFromIndex(bgZctIndex);
		int z = bgZctArray[0];
		int ijSlice = z + 1; // ImageJは1-based
		if (ijSlice < 1 || ijSlice > foregroundOverlay.getStackSize()) {
			if (bgSg.getOriginalImage() != null)
				bgSg.getOriginalImage().setOverlay(null);
			return;
		}

		// UIで選択された安全なLUTを強制使用
		ij.process.LUT fgLUT = this.lut;
		String bgSopUid = bgSg.getSOPInstanceUID();

		ij.process.ImageProcessor rawProcessor = foregroundOverlay.getStack().getProcessor(ijSlice);
		if (rawProcessor == null) {
			if (bgSg.getOriginalImage() != null)
				bgSg.getOriginalImage().setOverlay(null);
			return;
		}

		ij.process.ImageProcessor fgProcessor = rawProcessor.duplicate();
		int bgFrameIdx = bgSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1;

		double[] bgIop = getSafeIOP(bgSg.getHeader(), bgFrameIdx);
		double[] fgIop = com.vis.dicom.image.GDicomTools.getImageOrientationPatient(foregroundOverlay, ijSlice);

		if (bgIop != null && fgIop != null && bgIop.length == 6 && fgIop.length == 6) {
			double rowDot = bgIop[0] * fgIop[0] + bgIop[1] * fgIop[1] + bgIop[2] * fgIop[2];
			if (rowDot < -0.5)
				fgProcessor.flipHorizontal();
			double colDot = bgIop[3] * fgIop[3] + bgIop[4] * fgIop[4] + bgIop[5] * fgIop[5];
			if (colDot < -0.5)
				fgProcessor.flipVertical();
		}

		// Raw values
		double fgMin = foregroundOverlay.getDisplayRangeMin();
		double fgMax = foregroundOverlay.getDisplayRangeMax();

		if (Double.isNaN(fgMin) || Double.isNaN(fgMax)) {
			fgMin = fgProcessor.getMin();
			fgMax = fgProcessor.getMax();
		}
		if (fgMin >= fgMax) {
			fgMax = fgMin + 1.0;
		}

		// 3. Set RAW value
		fgProcessor.setMinAndMax(fgMin, fgMax);

		// 4. 8-bitへ変換（指定レンジ外が 0 と 255 になる）
		ij.process.ImageProcessor byteProcessor = fgProcessor.convertToByte(true);
		byte[] pixels8 = (byte[]) byteProcessor.getPixels();

		// 5. RGB変換とカラーマップ適用
		int w = fgProcessor.getWidth();
		int h = fgProcessor.getHeight();
		int[] pixelsRGB = new int[w * h];

		/*
		 * The LUT (colormap) is applied directly at the very last moment—right after
		 * scaling perfectly to 8-bit—using the intact original LUT (this.lut) held by
		 * the UI (Praparat).
		 */
		java.awt.image.IndexColorModel cm = fgLUT;
		if (cm == null) {
			cm = (java.awt.image.IndexColorModel) byteProcessor.getColorModel(); // LUTがない場合は白黒
		}

		int transparentCount = 0;
		for (int i = 0; i < pixels8.length; i++) {
			int v = pixels8[i] & 0xff; // 0〜255のunsigned値に変換
			if (v == 0) {
				pixelsRGB[i] = 0; // 完全な黒・完全透明 (0x00000000)
				transparentCount++;
			} else {
				int r = cm.getRed(v);
				int g = cm.getGreen(v);
				int b = cm.getBlue(v);
				// 不透明度(Alpha=FF)を付与した完全なRGB値
				pixelsRGB[i] = (0xff << 24) | (r << 16) | (g << 8) | b;
			}
		}

		ij.process.ColorProcessor rgbProcessor = new ij.process.ColorProcessor(w, h, pixelsRGB);

		// 6. ImageRoiの作成とOverlayへの追加
		ij.gui.ImageRoi imageRoi = new ij.gui.ImageRoi(fusionOffsetX, fusionOffsetY, rgbProcessor);
		imageRoi.setZeroTransparent(true);
		imageRoi.setOpacity(currentFusionOpacity);
		imageRoi.setName("FusionROI_" + bgSopUid);

		ij.gui.Overlay overlay = new ij.gui.Overlay();
		overlay.add(imageRoi);

		if (bgSg.getOriginalImage() != null) {
			bgSg.getOriginalImage().setOverlay(overlay);
		}

		com.vis.core.log.Log.logger.info(String.format(
				"[Fusion Render] Slice %d | RAW Range: %.1f - %.1f | Transparent Pixels (Below Min): %d / %d", ijSlice,
				fgMin, fgMax, transparentCount, pixels8.length));
	}

	/**
	 * フュージョンモードを解除し、オーバーレイを破棄します。
	 */
	public void disableFusionMode() {
		this.isFusionMode = false;
		this.foregroundOverlay = null;

		for (SlideGlass bgSg : this.slides.values()) {
			if (bgSg != null && bgSg.getOriginalImage() != null) {
				bgSg.getOriginalImage().setOverlay(null);
				bgSg.updateDisplayImage();
			}
		}
		repaint();
	}

	/**
	 * コントロールダイアログ等から透過度と位置シフトの指示を受け取り、動的にフュージョンをアップデートします。
	 */
	public void updateFusionParameters(double opacity, int xShift, int yShift) {
		this.currentFusionOpacity = opacity;
		this.fusionOffsetX = xShift;
		this.fusionOffsetY = yShift;

		if (isFusionMode) {
			// 現在メモリ上にある（表示中＋キャッシュ先読み済みの）スライドに即座に新パラメータを再適用
			for (Integer zct : this.slides.keySet()) {
				SlideGlass bgSg = this.slides.get(zct);
				if (bgSg != null && bgSg.getOriginalImage() != null) {
					applyFusionOverlayToSlide(zct, bgSg);
					bgSg.updateDisplayImage();
				}
			}
			repaint();
		}
	}

	// 4. ダイアログ側で現在の値を初期値として読み込めるよう、Getterを追加します
	public boolean isFusionMode() {
		return this.isFusionMode;
	}

	public double getCurrentFusionOpacity() {
		return this.currentFusionOpacity;
	}

	public int getFusionOffsetX() {
		return this.fusionOffsetX;
	}

	public int getFusionOffsetY() {
		return this.fusionOffsetY;
	}

	/**
	 * フュージョン用の前景オーバーレイ画像（ImagePlus）をセットします。
	 */
	public void setForegroundOverlay(ImagePlus overlayImp) {
		this.foregroundOverlay = overlayImp;
		if (overlayImp != null) {
			int c = overlayImp.getNChannels();
			int t = overlayImp.getNFrames();
			if (c > 1 || t > 1) {
				JOptionPane.showMessageDialog(this,
						"Overlay should be a single stack.\nThis overlay seems multi channel/frame stackm it cannot set to overlay.");
				this.foregroundOverlay = null;
			}
		}
		// セットされたら再描画をトリガーする
		repaint();
	}

	/**
	 * 現在セットされている前景オーバーレイ画像を取得します。
	 */
	public ImagePlus getForegroundOverlay() {
		return this.foregroundOverlay;
	}

	/**
	 * コントロールダイアログ等からLUT（カラーマップ）の変更指示を受け取り、 前景画像に適用してフュージョン表示を更新します。
	 */
	public void updateFusionLUT(ij.process.LUT newLut, String lutName) {
		if (foregroundOverlay != null) {
			// 前景のPraparat自体のLUTを書き換える
			foregroundOverlay.setLut(newLut);
			// 新しいLUTで再計算してリフレッシュ
			// (Step 1で追加した updateFusionParameters と同様の一括更新を行う)
			updateFusionParameters(this.currentFusionOpacity, this.fusionOffsetX, this.fusionOffsetY);
		}
	}

	/**
	 * 現在のPraparatの断面の向き（AXIAL, SAGITTAL, CORONAL等）を取得します。
	 */
	public com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface getCutSurface() {
		SlideGlass sg = getCurrentSlide();
		if (sg == null)
			return com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface.UNKNOWN;
		return com.vis.core.view.D2.ui.orientation.ImageOrientation.getCutSurface(sg.getHeader());
	}

	/**
	 * 指定されたIPP(物理座標)に最も近いスライスを探し、5mm以内であれば同期移動します。 マルチチャンネル・マルチフレーム環境下でも現在のC,
	 * Tを維持してZ方向のみを検索します。
	 */
	public void syncSliceToIPP(org.joml.Vector3d sourceIPP,
			com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface sourceSurface, double toleranceMm) {
		if (nSlices <= 1 || sourceIPP == null || sourceSurface == null)
			return;
		if (sourceSurface == com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface.UNKNOWN)
			return;

		// 1. 断面（CutSurface）が異なる場合は同期しない
		if (getCutSurface() != sourceSurface)
			return;

		// 2. 自身のスライス平面の法線ベクトル(Normal)を取得
		SlideGlass currentSg = getCurrentSlide();
		if (currentSg == null)
			return;
		int frameIdx = isMultiFrame() ? (currentSg.getHeader().getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1) : 0;
		double[] iop = getSafeIOP(currentSg.getHeader(), frameIdx);
		if (iop == null || iop.length != 6)
			return;

		org.joml.Vector3d row = new org.joml.Vector3d(iop[0], iop[1], iop[2]);
		org.joml.Vector3d col = new org.joml.Vector3d(iop[3], iop[4], iop[5]);
		org.joml.Vector3d normal = new org.joml.Vector3d();
		row.cross(col, normal).normalize(); // 法線ベクトルを計算

		// 3. 自身の全スライス(Z)の中から、最も物理距離が近いものを探す
		int bestZ = -1;
		double minDistance = Double.MAX_VALUE;

		for (int z = 0; z < nSlices; z++) {
			// ★マルチチャンネル対応: ターゲット側の現在のChannel(C)とTime(T)を固定してZだけを走査する
			int index = calcZctIndex(new int[] { z, currentC, currentT });
			SlideGlass sg = slides.get(index);
			if (sg == null)
				continue;

			int fIdx = isMultiFrame() ? (sg.getHeader().getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1) : 0;
			double[] ipp = getSafeIPP(sg.getHeader(), fIdx);
			if (ipp == null || ipp.length != 3)
				continue;

			org.joml.Vector3d myIPP = new org.joml.Vector3d(ipp[0], ipp[1], ipp[2]);

			// ★重要: 単純な3D直線距離ではなく、法線ベクトルへの投影距離(内積)を求める
			// これにより、FOVのXYズレを無視して「スライス平面同士の垂直距離」だけを正確に測れます
			org.joml.Vector3d diff = new org.joml.Vector3d(sourceIPP).sub(myIPP);
			double dist = Math.abs(diff.dot(normal));

			if (dist < minDistance) {
				minDistance = dist;
				bestZ = z;
			}
		}

		// 4. 最も近いスライスが許容誤差（toleranceMm = 5.0mmなど）以内なら移動する
		if (bestZ != -1 && minDistance <= toleranceMm) {
			// スライダーを通じて移動を指示（これで画像ロード処理等も発火します）
			if (slider != null && slider.getValue() != (bestZ + 1)) {
				slider.setPosition(bestZ);
			}
		}
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
