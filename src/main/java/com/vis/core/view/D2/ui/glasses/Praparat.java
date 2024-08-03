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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.*;

import javax.swing.BorderFactory;
import javax.swing.JLayer;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;

import com.vis.core.log.Log;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.roi.ReferenceLine;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.SeriesWindow;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext;
import com.vis.core.view.D2.ui.orientation.GeometryOfSlice;
import com.vis.core.view.D2.ui.orientation.IntersectVolume;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.VR;
//import com.vis.mediareader.PDFReader;
import com.vis.dicom.image.DicomImage;
import com.vis.imageio.Codec;
import com.vis.imageio.Decompressor;
import com.vis.imageio.PDFReader;

//import com.vis.viewer2d.roi.ReferenceLine;
//import com.vis.viewer2d.roi.RoiObj;
//import com.vis.viewer2d.ui.eyepiece.PraparatShelf.PraparatContext;
//import com.vis.viewer2d.ui.frame.SeriesWindow;
//import com.vis.viewer2d.ui.frame.Viewer2DScreen;
//import com.vis.viewer2d.ui.orientation.GeometryOfSlice;
//import com.vis.viewer2d.ui.orientation.IntersectVolume;
//import com.vis.viewer2d.ui.orientation.LocalizerPoster;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
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
		Normal,
		Thumbnail,
		SingleGrid,//for bird's eye
		FilmGrid,//for bird's eye
		MPR,
	}
	//patient info set keys
	public final String KEY_PadID = "Patient​ID";
	public final String KEY_StudyUID = "StudyInstanceUID";
	public final String KEY_SeriesUID = "SeriesInstanceUID";
	public final String KEY_SopUIDs = "SOPInstanceUIDs";
		
	// component
	private PraparatViewControlPanel pvcp;
	private JScrollPane gridScrollPane;//gridViewScroll
	private JPanel viewPanel;
	
	int prevViewPanelW = 0;
	int prevViewPanelH = 0;
	
	private CineSlider slider;
	private Color studyColor = Color.CYAN;
	
	private final int BORDER_SIZE = 6;
	private final int ThumbnailSize = 64;
	private int currentSlice = 0;
	private int prevSlice = -1;

	private int filmGridColumns = 5;
	private boolean isMultiframe = false;/*to set video option*/
	private boolean isPDF = false;
	private boolean selected = false;
	private boolean focusGained = false;
	private boolean showGridViewOn = false;
	
	private boolean processSeries = true;
	//mpr
	private boolean crossLineCursorMode = false;

	private ReferenceLine refLine;

	private ArrayList<String> pathToImages = null;
	Eyepiece prapManager;
	String patID;
	String studyUID;
	String seriesUID;
	String[] sopUIDs;
	String modality = null;
	
	int prevW;
	int prevH;
	
	private HashMap<Integer, SlideGlass> slides;
	
	final ViewMode mode;
	
	public Praparat(ImagePlus stack, Color studyColor) {
		this.mode = ViewMode.Normal;
		if(studyColor != null) {
			this.studyColor = studyColor;
		}
		init();
		prepareSlideGlassesUsingImagePlus(stack);
	}
	
	public Praparat(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToSortedinstNoImages, Color studyColor, ViewMode mode) {
		this(patID, studyUID, seriesUID, sopUIDs, pathToSortedinstNoImages, null, studyColor, mode);
	}
	
	public Praparat(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToSortedinstNoImages, Eyepiece manager,
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
		setInfo(patID, studyUID, seriesUID, sopUIDs, pathToSortedinstNoImages);
		init();
	}

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
		if(mode == ViewMode.FilmGrid) {
			return;
		}
		//setViewPanelSize(getViewPanelWidth(), getViewPanelHeight());
		if(slides == null || slides.size() == 0) {
			return;
		}
		if(!processSeries) {
			SlideGlass target = slides.get(currentSlice);
			target.setSize(w, h);
			target.repaint();
		}else {
			SlideGlass target = slides.get(currentSlice);
			target.setSize(w,h);
			target.repaint();
			//set origin all slides
			for (Integer key : slides.keySet()) {
				SlideGlass sl = slides.get(key);
				if(sl == target) continue;
				sl.setSize(w,h);
				if (target != null && !target.panningFlag && !sl.panningFlag) {
					sl.lastOriginX = target.imageSpecimen.getDisplayOriginX();
					sl.lastOriginY = target.imageSpecimen.getDisplayOriginY();
					sl.imageSpecimen.originX = target.imageSpecimen.originX;
					sl.imageSpecimen.originY = target.imageSpecimen.originY;
//					logger.fine("default origin adjusted !!! :"+sg_.originX+" "+sg_.originY);
					sl.repaint();
				}
			}
		}
	}
	
	private List<Point2D> calcLocalizer(SlideGlass src, SlideGlass target) {
		GeometryOfSlice localizerGeometry = new GeometryOfSlice(src.getHeader());
		GeometryOfSlice postImageGeometry = new GeometryOfSlice(target.getHeader());
		LocalizerPoster localizerPoster = new IntersectVolume(localizerGeometry);
		List<Point2D> shapes = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
		if(Utils.isDebug){
			System.out.println(src.getHeader().getString(TagDict.forName("InstanceNumber")));
			System.out.println(target.getHeader().getString(TagDict.forName("InstanceNumber")));
			Point2D p0_leftlower = shapes.get(0);
			Point2D p1_rightlower = shapes.get(1);
			Point2D p2_rightupper = shapes.get(2);
			Point2D p3_leftupper = shapes.get(3);
			System.out.println(p0_leftlower.getX()+" "+p0_leftlower.getY());
			System.out.println(p1_rightlower.getX()+" "+p1_rightlower.getY());
			System.out.println(p2_rightupper.getX()+" "+p2_rightupper.getY());
			System.out.println(p3_leftupper.getX()+" "+p3_leftupper.getY());
		}
		return shapes;
	}
	
	public void callBackLocalizer() {
		// ref-study-uid
		Eyepiece prapmng = getEyepieceAsPraparatManager();
		if(prapmng == null) return;
		PraparatContext con = prapmng.getPraparatContextOf(patID, studyUID, seriesUID, sopUIDs);
		if(con == null) {
			return;
		}
		String refUid = (String) con.getContextUIDs()[4];
		// get praps which have same refuid
		ArrayList<Praparat> praps = prapmng.getAllPraparatByFrameOfReferenceUID(patID, studyUID, refUid);
		//remove previous localizers
		for(Praparat p:praps) {
			HashMap<Integer, SlideGlass> slides = p.slides;
			for(Integer k:slides.keySet()) {
				SlideGlass s = slides.get(k);
				s.drawLocalizer(null);
			}
		}
		// show localizer on slideglass
		SlideGlass target = getCurrentSlide(); 
		for(Praparat p:praps) {
			//if self, skip
			if(p == this) {
				continue;
			}
			SlideGlass src = p.getCurrentSlide();
			List<Point2D> loca_geo = null;
			try {
				loca_geo = calcLocalizer(src, target);
			}catch(Exception e) {
				//do somethoing
				loca_geo = null;
			}
			src.drawLocalizer(loca_geo);
		}
	}
	
	public void clearCrossLines() {
		HashMap<Integer,SlideGlass> slides = getAllSlides();
		for(Integer sglKey : slides.keySet()) {
			SlideGlass sgl = slides.get(sglKey);
			CanvasGlass cg = (CanvasGlass) sgl.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			cg.setCrossLine(null);
		}
	}
	
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
			reader.read(imgFiles.get(i), false);
			DicomObject header = reader.getCore();
			String sopClassUID = header.getString(Tag.SOP​Class​UID, "");
			UID tsUID = reader.checkTSUID();
			//PDF
			if(sopClassUID.equals(UID.EncapsulatedPDFStorage.uid())) {
				//read as series level
				PDFReader pdfReader = new PDFReader(new File(imgFiles.get(i))/*read dicom*/);
				ImagePlus pdfStack = pdfReader.pdf2ImageStack();
				isMultiframe = true;//always treats multi
				isPDF = true;
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
						ImageProcessor instIp = pdfStack.getStack().getProcessor(j + 1);
						DicomObject instHeader = DicomObject.newDicomObject(header, backend);
						instHeader.setInt(Tag.Columns, VR.US, instIp.getWidth());
						instHeader.setInt(Tag.Rows, VR.US, instIp.getHeight());
						instHeader.setInt(Tag.Samples​Per​Pixel, VR.US, 3);
						instHeader.setInt(Tag.Bits​Allocated, VR.US, 8);
						instHeader.setInt(Tag.Instance​Number, VR.IS, (j + 1));
						DicomImage img = DicomImage.newDicomImage(instHeader, UID.ImplicitVRLittleEndian, backend);
						img.setPixelData(j, pdfStack.getWidth(), pdfStack.getHeight(), 3, 8, instIp.getPixels());
						SlideGlass sg = new SlideGlass(this, img);
						slides.put(j, sg);
					}
				}
				pdfReader.close();
				break;//if multiframe, load only first file.
			}
			// images
			int size = header.getInt(Tag.Number​Of​Frames, -1);
			isMultiframe = size > 0;//general image type does not have NumberOfFrames tag.
			//single frame
			if(!isMultiframe) {
				DicomImage dcmimg = DicomImage.newDicomImage(imgFiles.get(i), backend);
				if(Codec.isCompressed(tsUID.uid())) {
					Decompressor.newInstance(dcmimg).decompress();
				}
				SlideGlass sg = new SlideGlass(this, dcmimg);
				slides.put(i, sg);
			}else {
				/*
				 * multiframe to one series.
				 */
				DicomReader video_reader_ = DicomReader.newDicomReader(backend);
				video_reader_.read(imgFiles.get(i), true/*with bulk*/);
				DicomImage videoDcm = DicomImage.newDicomImage(video_reader_.getCore(), video_reader_.checkTSUID());
				int w = header.getInt(Tag.Columns, 0);
				int h = header.getInt(Tag.Rows, 0);
				int c = header.getInt(Tag.Samples​Per​Pixel, 1);
				int bits = header.getInt(Tag.Bits​Allocated, 8);
				/*
				 * to address jpeg multiframe
				 */
				if(Codec.isCompressed(reader.checkTSUID())) {
					Decompressor.newInstance(videoDcm).decompress();
				}
				for(int j=0;j<size;j++) {
					DicomObject instHeader = DicomObject.newDicomObject(header, backend);
					instHeader.setInt(Tag.Instance​Number, VR.IS, (j+1));
					DicomImage frame = DicomImage.newDicomImage(instHeader, UID.ImplicitVRLittleEndian, backend);
					frame.setPixelData(j, w, h, c, bits, videoDcm.getImageProcessor(j).getPixels());
					SlideGlass sg = new SlideGlass(this, frame);
					slides.put(j, sg);
				}
				video_reader_ = null;
				/*
				 * if multiframe, load as only one file.
				 */
				break;
			}
			reader = null;
		}
	}

	/**
	 * This method is used to a dependence single series view or MPR.
	 */
	private void constructSlideGlassesFromImagePlus(ImagePlus images) {
		if (images == null || images.getStackSize() < 1) {
			logger.warning("Please set not null images, Praparat::constructSeriesGlassesAsLayerUsingImagePlus");
			return;
		}
		// init
		viewPanel.removeAll();
		slides = new HashMap<Integer, SlideGlass>();
		HashMap<Integer, DicomImage> ds = ImageUtils.imagePlusToDcm(images, false/*treat as secondary*/);
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
			System.out.println(slides.size()+" images loaded.");
		}
	}
	
	public ImagePlus cropRectangle(boolean show) {
		SlideGlass sg = getCurrentSlide();
		RoiObj roi = sg.findCurrentRoi();
		if(roi == null) {
			return null;
		}
		int res = JOptionPane.showConfirmDialog(this, "process all slide in this series ?", "Crop series ?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		final ImagePlus crop;
		if(res != JOptionPane.YES_OPTION) {
			crop = sg.processCropRect(roi);
		}else {
			HashMap<Integer,SlideGlass> slides = getAllSlides();
			Set<Integer> keys = slides.keySet();
			int size = keys.size();
			int pos = 1;
			ImageStack cropStack = null;
			Calibration cal = null;
			for(Integer k : keys) {
				SlideGlass slide = slides.get(k);
				ImagePlus c = slide.processCropRect(roi);
				if(c == null) {
					System.err.println("something happened...crop failed.");
					return null;
				}
				/*
				 * init at first crop iteration
				 */
				if(cropStack == null) {
					cropStack = new ImageStack(c.getWidth(), c.getHeight(), size);
					cal = sg.getOriginalCalibration().copy();
				}
				cropStack.setProcessor(c.getProcessor().duplicate(), pos++);
				c =  null;//init
			}
			crop = new ImagePlus("crop", cropStack);
			crop.setCalibration(cal);
		}
		if(crop == null || crop.getNSlices() < 1) {
			return null;
		}
		if(show) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					Praparat prap = new Praparat(crop, getStudyColor());
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
		SlideGlassGrid sgg = new SlideGlassGrid(this, false/*GridLayer*/);
		viewPanel.add(sgg,0);
		setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
	}

	public void doSingleGridLayout() {
		if(this.mode == ViewMode.FilmGrid) {
			logger.warning("You are not able to show single grid view on this mode::"+this.mode);
			return;
		}
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
	
	public Eyepiece getEyepieceAsPraparatManager() {
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
		if(slides == null || slides.size() < 1) {
			return null;
		}
		ImagePlus ref_imp = slides.get(0).convertToImagePlus();
		Calibration cal = ref_imp.getCalibration().copy();
		int size = getNumberOfImages();
		ImageStack stack = new ImageStack(ref_imp.getWidth(), ref_imp.getHeight(), size);
		HashMap<Integer,SlideGlass> all_slides = getAllSlides();
		for(int i=0; i<size; i++) {
			ImagePlus imp = all_slides.get(i).convertToImagePlus();
			ImagePlus imp2 = new Duplicator().run(imp);
			String header = imp.getInfoProperty();//imp.getStack().getSliceLabel(1);
//			imp2.setProperty("Info", header);
			ImageProcessor ip = imp2.getProcessor();
			if(ip.getNChannels() == 3 && ip instanceof ColorProcessor) {
				ip.snapshot();//keep original pixels
			}
			stack.setProcessor(ip, i+1);//1<=N<=slices
			stack.setSliceLabel(header, i+1);
		}
		ImagePlus stacked = new ImagePlus("stack-series", stack);
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
	
	public ReferenceLine getReferenceLine() {
		return this.refLine;
	}
	
//	private void fitAllImagesSize2PraparatView() {
//		if(slides == null || slides.size() == 0) {
//			return;
//		}
//		if(getViewPanelWidth() == 0 || getViewPanelHeight() == 0) {
//			return;
//		}
//		for(int i=0;i<slides.size();i++) {
//			SlideGlass sg = slides.get(i).getView();
////			sg.fitImg2Comp(new_size[0], new_size[1], getViewPanelWidth(), getViewPanelHeight());
//			sg.fit2Praparat();
//		}
//		viewPane.validate();
//		viewPane.repaint();
//	}
	
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
		uids[3] = sopUIDs;// String[]
		return uids;
	}
	
	public ViewMode getViewMode() {
		return this.mode;
	}
	
	public void gridViewOn(boolean show) {
		if(this.mode == ViewMode.FilmGrid) {
			this.showGridViewOn = true;
			return;
		}
		if(this.mode == ViewMode.Normal) {
			this.showGridViewOn = show;
			if(show == false) {
				gridScrollPane = null;
			}
		}else {
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
		//common
		slides = new HashMap<Integer, SlideGlass>();
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createLineBorder(getBackground()/*DO NOT USE clearColor*/, BORDER_SIZE));
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
			pvcp.enableProcessSeries(false);
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

	public boolean isFocusGained() {
		return focusGained;
	}
	
	public boolean isMultiFrame() {
		return this.isMultiframe;
	}
	
	public boolean isPDF() {
		return this.isPDF;
	}

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

	public void loadRoiFromDB() {
		SlideGlass sg = getCurrentSlide();
		sg.loadRoiFromDB();
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
		if(slider != null) {
			slider.initContext();
		}
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
		updateInfoLabel(-1,-1,"-1",-1,-1,-1);
		constructSlideGlassesFromImagePlus(images);
		slider.initContext();
		if(Utils.isDebug) {
			System.out.println(slides.size()+" images loaded.");
		}
	}
	
	public void prepareSlideGlassesFromDcmObj(ArrayList<String> paths) {
		if(paths == null || paths.size()==0) {
			if(Utils.isDebug) System.out.println("praparat needs images..., return.");
			return;
		}
		prevSlice = -1;
		currentSlice = 0;
		updateInfoLabel(-1,-1,"-1",-1,-1,-1);
		constructSlideGlassesFromDicom(paths);
		if(slider != null) {
			slider.initContext();
		}
		prevSlice = -1;// IMPORTANT
		currentSlice = 0;
		if(slider != null) {
			slider.initContext();
		}
		if(Utils.isDebug) {
			System.out.println(slides.size()+" images loaded.");
		}
	}
	
	public void processCut() {
		SlideGlass sg = getCurrentSlide();
		RoiObj currentRoi = sg.findCurrentRoi();
		if(currentRoi == null ) {
			return;
		}
		int roiType = currentRoi.getType();
		if(roiType == RoiObj.ANGLE || roiType == RoiObj.ARROW || roiType == RoiObj.FREELINE || roiType == RoiObj.POINT || roiType==RoiObj.LINE) {
			JOptionPane.showMessageDialog(Viewer2DScreen.getInstance(), "Cut process needed closed type roi.");
			return;
		}
		int res = JOptionPane.showConfirmDialog(this, "process all slide in this series ?", "Cut...", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if(res == JOptionPane.YES_OPTION) {
			HashMap<Integer,SlideGlass> slides = getAllSlides();
			Set<Integer> keys = slides.keySet();
			for(Integer k : keys) {
				SlideGlass s = slides.get(k);
				s.processCut(currentRoi);
			}
		}else {
			SlideGlass s = getCurrentSlide();
			s.processCut(currentRoi);
		}
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
			adjustSlideGlassSize(getViewPanelWidth(), getViewPanelHeight());
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
//				getSlideGlassHolder().remove(slide);
				viewPanel.removeAll();
			}
		}
		updateInfoLabel(-1,-1,"-1",-1,-1,-1);
	}

	public void resetView() {
		if (getImageFileLocations() == null || getImageFileLocations().size()==0) {
			ImagePlus imp = getImagePlus();
			reloadSlideGlasses(imp);
			return;
		}
		
		if(mode == ViewMode.Normal) {
			if (isShowGridViewOn()) {
				showGridViewOn = false;
			}
			prevSlice = -1;
			currentSlice = 0;
			updateInfoLabel(-1,-1,"-1",-1,-1,-1);
			// reload slides
			prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
			slider.initContext();
			doSingleGridLayout();
			setTextVisible(pvcp.isShowInfo());
			setAnnotationVisible(pvcp.isShowRoi());
			return;
		}
		
		if(mode == ViewMode.SingleGrid) {
			viewPanel.removeAll();
			prevSlice = -1;
			currentSlice = 0;
			updateInfoLabel(-1,-1,"-1",-1,-1,-1);
			// reload slides
			prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
			setTextVisible(pvcp.isShowInfo());
			setAnnotationVisible(pvcp.isShowRoi());
			slider.initContext();
			showFirstImage();
			return;
		}
		
		if(mode == ViewMode.FilmGrid) {
			viewPanel.removeAll();
			prevSlice = -1;
			currentSlice = 0;
			updateInfoLabel(-1,-1,"-1",-1,-1,-1);
			// reload slides
			prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
			setTextVisible(false);
			setAnnotationVisible(false);
			doFilmGridLayout(filmGridColumns);
			return;
		}
		
		if(mode == ViewMode.Thumbnail) {
			//do nothing
			return;
		}
		
	}

	public void setAndShowPixelValue(int X, int Y) {
		SlideGlass currentSlide = getCurrentSlide();
		double scale = currentSlide.getScaleFactor();
		double mag = currentSlide.getMagnification();
		double rotate = currentSlide.getRotateAngle();
		if(!currentSlide.isRGB()) {
			Double[] pixelRawAndCalibrated = (Double[])getCurrentSlide().getPixelValueFromDisplay(X, Y);
			double raw_v = pixelRawAndCalibrated[0];
			double calibrated_v = pixelRawAndCalibrated[1];
			updateInfoLabel(X, Y, raw_v+"("+calibrated_v+")",scale,mag,rotate);
		}else {
			String[] rgbAndColor = (String[])getCurrentSlide().getPixelValueFromDisplay(X, Y);
			String r = rgbAndColor[0];
			String g = rgbAndColor[1];
			String b = rgbAndColor[2];
//			String color = rgbAndColor[3];//java.awt.Color[r,g,b]
//			updateInfoLabel(X, Y, r+","+g+","+b+" "+"("+color+")", scale, mag, rotate);
			updateInfoLabel(X, Y, r+","+g+","+b, scale, mag, rotate);
		}
	}

	public void setAnnotationVisible(boolean v) {
		HashMap<Integer, SlideGlass> slides = getAllSlides();
		if(slides == null) {
			return;
		}
		if (isShowGridViewOn()) {
			for (Integer k : slides.keySet()) {
				SlideGlass s = slides.get(k);
				s.setAnnotationVisible(v);
			}
		} else {
			if(processSeries) {
				for (Integer k : slides.keySet()) {
					SlideGlass s = slides.get(k);
					s.setAnnotationVisible(v);
				}
			}else {
				getCurrentSlide().setAnnotationVisible(v);
			}
		}
	}
	
	public int getFilmGridColumns() {
		return this.filmGridColumns;
	}
	
	public void setFilmGridColumns(int num) {
		this.filmGridColumns = num;
	}
	
	public void setFocusGained(boolean focusGained) {
		this.focusGained = focusGained;
		if(getViewMode()!=ViewMode.SingleGrid && getViewMode()!=ViewMode.FilmGrid) {
			showBorder();
		}
	}
	
	private void setImageFileLocations(ArrayList<String> pathToImages) {
		this.pathToImages = pathToImages;
	}
	
	/**
	 * For CineSlider.
	 * If you want to change slide position, use setImagePositionUsingSlider instead.
	 * @param sliceIndex:number of slice index, 0 to n-1
	 */
	protected void setImagePosition(int sliceIndex) {
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
		currentGlass.requestFocus();//IMPORTANT for key listener
		viewPanel.repaint();
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
		slider.setSlice(pos);
	}
	
	private void setInfo(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToImages) {
		this.patID = patID;
		this.studyUID = studyUID;
		this.seriesUID = seriesUID;
		this.sopUIDs = sopUIDs;
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
	
	/*
	 * ??
	 */
	public void setProcessSeries(boolean v) {
		if (showGridViewOn) {
			//same-as
			this.processSeries = v;
		} else {
			this.processSeries = v;
		}
	}
	
	public void setReferenceLine(ReferenceLine refLine) {
		this.refLine = refLine;
	}
	
	// mouse action
	public void setSelectionState() {
		if (isSelected()) {
			setSelectionState(false);
		} else {
			setSelectionState(true);
		}
	}
	
	// list selection action
	public void setSelectionState(boolean select) {
		this.selected = select;
		showBorder();
	}
	
	public void setShowCrossLineMode(boolean crossMode) {
		this.crossLineCursorMode = crossMode;
	}

	/**
	 * for thumbnail
	 * @param w
	 * @param h
	 */
	public void setViewPanelSize(int w, int h) {
		viewPanel.setPreferredSize(new Dimension(w, h));
		viewPanel.setBounds(0, 0, w, h);
	}

	public void setStudyColor(Color color) {
		if(color != null) {
			this.studyColor = color;
		}
	}

	public void setTextVisible(boolean v) {
		HashMap<Integer, SlideGlass> slides = getAllSlides();
		if(slides == null) {
			return;
		}
		if (isShowGridViewOn()) {
			for (Integer k : slides.keySet()) {
				SlideGlass s = slides.get(k);
				s.setTextVisible(v);
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

	public void showBorder() {
		if(this.mode==ViewMode.FilmGrid || this.mode == ViewMode.SingleGrid) {
			setBorder(BorderFactory.createLineBorder(getBackground(), BORDER_SIZE));
			return;
		}
		if(this.mode == ViewMode.Thumbnail) {
			if (isSelected()) {/*DO NOT USE forcusGained here.*/
				Border selectionBorder = BorderFactory.createLineBorder(new Color(0, 50, 240, 100), BORDER_SIZE);
				setBorder(selectionBorder);
			}else {
				setBorder(BorderFactory.createLineBorder(getBackground(), 4));
			}
			return;
		}
		
		if (isSelected() && !isFocusGained()) {// show border force
			Border selectionBorder = BorderFactory.createLineBorder(new Color(0, 50, 240, 100), BORDER_SIZE);
			setBorder(selectionBorder);
		} else if (!isSelected() && isFocusGained()) {
			Border focusBorder = BorderFactory.createLineBorder(studyColor, BORDER_SIZE);
			setBorder(focusBorder);
		} else if (isSelected() && isFocusGained()) {
			//if you want 3 color border
//			Border studyBorder = BorderFactory.createLineBorder(studyColor, 2);
//			Border selectionBorder = BorderFactory.createLineBorder(new Color(0, 50, 240, 100), 1);
//			Border focusBorder = BorderFactory.createLineBorder(Color.CYAN, 1);
//			Border compBorder1 = new CompoundBorder(focusBorder, selectionBorder);
//			Border compBorder2 = new CompoundBorder(compBorder1, studyBorder);
//			setBorder(compBorder2);
			Border focusBorder = BorderFactory.createLineBorder(studyColor, BORDER_SIZE/2);
			Border selectionBorder = BorderFactory.createLineBorder(new Color(0, 50, 240, 100), BORDER_SIZE/2);
			Border compBorder = new CompoundBorder(selectionBorder, focusBorder);
			setBorder(compBorder);
		} else {
			setBorder(BorderFactory.createLineBorder(getBackground(), BORDER_SIZE));
		}
	}
	
	public void showFirstImage() {
		adjustSlideGlassSize(getViewPanelWidth(), getViewPanelHeight());
		slider.initContext();//slider setValue -1.
		// position range is 0 to n-1
		prevSlice = -1;
		currentSlice = 0;
		setImagePosition(currentSlice);
	}
	
	protected void updateInfoLabel(int x, int y, String value, double scale, double mag, double rotate) {
		if(getViewMode() != ViewMode.Thumbnail) {
			this.pvcp.setText2InfoLabel(x, y, value, scale, mag, rotate);
		}
	}
	
	/**
	 * See, viewPanel componentListener.
	 */
	public void updateViewPanel() {
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
				sg.adjustToParentComponent(viewPanel);
			}
		}
	}
}
