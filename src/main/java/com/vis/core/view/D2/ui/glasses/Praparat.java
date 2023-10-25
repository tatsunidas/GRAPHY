package com.vis.core.view.D2.ui.glasses;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
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
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.VR;
//import com.vis.mediareader.DICOMImage;
//import com.vis.mediareader.GImageReader;
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

@SuppressWarnings("serial")
public class Praparat extends JPanel implements ComponentListener {

	/**
	 * SeriesViewer
	 */
	/*
	 * Keep in my mind:
	 * Praparat should be stay simple series viewer. 
	 * Praparat has layers namely SlideGlass that is a holder of a single image.
	 */
	
	public enum ViewMode{
		Normal,
		Thumbnail,
		SingleGrid,//for bird's eye
		FilmGrid,//for bird's eye
		MPR,
	}
	
	// component
	private PraparatViewControlPanel pvcp;
	private JLayer<JLayeredPane> praparatView;
	private JLayeredPane viewPane; // slide glass screen.
	private JScrollPane gridPane;
	private CineSlider slider;
	
	Color studyColor = Color.CYAN;
	
	private int currentSlice = 0;
	private int prevSlice = -1;
	private int previousPraparatX = -1;// component sizeX
	private int previousPraparatY = -1;// component sizeY
	private int filmGridColumns = 5;

	private boolean isMultiframe = false;
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
	private HashMap<Integer, JLayer<SlideGlass>> slides;
	
	final ViewMode mode;
	
	private Logger logger = Log.logger;
	
	public Praparat(ViewMode mode) {
		if(mode == null) {
			this.mode = ViewMode.Normal;
		}else {
			this.mode = mode;
		}
		init();
	}
	
	public Praparat(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToSortedinstNoImages, Color studyColor, ViewMode mode) {
		this(patID, studyUID, seriesUID, sopUIDs, pathToSortedinstNoImages, null, studyColor, mode);
	}
	
	//for simple series view
	public Praparat(ImagePlus stack, Color studyColor) {
		this.mode = ViewMode.Normal;
		if(studyColor != null) {
			this.studyColor = studyColor;
		}
		init();
		prepareSlideGlassesUsingImagePlus(stack);
//		initImageSizeAndShowFirstImage();
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

	private void init() {
		if(this.mode == null) {
			return;
		}
		slides = new HashMap<Integer, JLayer<SlideGlass>>();
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createLineBorder(getBackground()/*DO NOT USE clearColor*/, 6));// necessary
		pvcp = new PraparatViewControlPanel(this);// pixelInfoLabel
		slider = new CineSlider(this);
		slider.initContext();
		
		if(mode == ViewMode.Normal) {
			add(pvcp, BorderLayout.NORTH);
			add(slider, BorderLayout.SOUTH);
			// finally, set praparat to center using LayerUI
			viewPane = new JLayeredPane();
			praparatView = new JLayer<JLayeredPane>(viewPane, new PraparatUI(this));
			add(praparatView, BorderLayout.CENTER);
			setFocusable(true);
			setRequestFocusEnabled(true);
		}
		
		if(mode == ViewMode.SingleGrid) {
			add(pvcp, BorderLayout.NORTH);
			add(slider, BorderLayout.SOUTH);
			// finally, set praparat to center using LayerUI
			viewPane = new JLayeredPane();
			praparatView = new JLayer<JLayeredPane>(viewPane, new PraparatUI(this));
			add(praparatView, BorderLayout.CENTER);
			setFocusable(true);
			setRequestFocusEnabled(true);
			pvcp.getFilmGridBtn().setEnabled(false);
			pvcp.enableProcessSeries(false);
		}
		
		if(mode == ViewMode.FilmGrid){
			add(pvcp, BorderLayout.NORTH);
			// finally, set praparat to center using LayerUI
			viewPane = new JLayeredPane();
			praparatView = new JLayer<JLayeredPane>(viewPane, new PraparatUI(this));
			add(praparatView, BorderLayout.CENTER);
			setFocusable(true);
			setRequestFocusEnabled(true);// fail safe?
			/*see also setTextVisible(). This is called when after preparedImages*/
			pvcp.enableShowInfo(false);
			pvcp.enableProcessSeries(false);
		}
		
		if(mode == ViewMode.Thumbnail) {
			viewPane = new JLayeredPane();
			praparatView = new JLayer<JLayeredPane>(viewPane, new PraparatUI(this));
			setPraparatViewSize(64, 64);
			add(praparatView, BorderLayout.CENTER);
			setFocusable(true);
			setRequestFocusEnabled(true);
		}
		
		if(mode == ViewMode.MPR){
			add(pvcp, BorderLayout.NORTH);
			add(slider, BorderLayout.SOUTH);
			viewPane = new JLayeredPane();
			praparatView = new JLayer<JLayeredPane>(viewPane, new PraparatUI(this));
			add(praparatView, BorderLayout.CENTER);
			setFocusable(true);
			setRequestFocusEnabled(true);// fail safe?
			pvcp.getFilmGridBtn().setEnabled(false);
		}
		addComponentListener(this);
	}

	/*
	 * prepareSlideGlasses
	 * set specimen
	 */
	public void prepareSlideGlasses(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
		ArrayList<String> pathToImages = null;
		DatabaseHandler db = DatabaseHandler.getInstance();//.getDatabase();
		if (sopUIDs == null || sopUIDs.length < 1) {
			// load all instances in series
			pathToImages = db.getFileLocations(patID, studyUID, seriesUID);
		} else {
			// load particular instances
			pathToImages = new ArrayList<String>();
			for (String sopUID : sopUIDs) {
				String p2img = db.getFileLocation(patID, studyUID, seriesUID, sopUID);
				pathToImages.add(p2img);
			}
		}
		if (pathToImages == null || pathToImages.size()<1) {
			return;
		}
		prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs, pathToImages);
	}
	
	public void prepareSlideGlasses(String patID, String studyUID, String seriesUID, ArrayList<String> sopUIDs, ArrayList<String> pathToImages) {
		String[] sopUids = sopUIDs.toArray(new String[sopUIDs.size()]);
		prepareSlideGlasses(patID, studyUID, seriesUID, sopUids, pathToImages);
	}
	
	public void prepareSlideGlasses(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToImages) {
		if(pathToImages == null || pathToImages.size()==0) {
			System.out.println("prap needs path to images..., return.");
			return;
		}
		setInfo(patID, studyUID, seriesUID, sopUIDs, pathToImages);
		constructSeriesGlassesAsLayer();
		if(slider != null) {
			slider.initContext();
		}
		prevSlice = -1;// IMPORTANT
		currentSlice = 0;
		if(Utils.isDebug) {
			System.out.println(slides.size()+" images loaded.");
		}
	}
	
	public void prepareSlideGlassesUsingImagePlus(ImagePlus images) {
		if(images == null || images.getStackSize()==0) {
			System.out.println("prap needs images..., return.");
			return;
		}

		if(viewPane != null) {
			viewPane.removeAll();
		}
		if(prevSlice != -1) {
			//do something ??
		}
		constructSeriesGlassesAsLayerUsingImagePlus(images);
		System.out.println(slides.size()+" images loaded.");
		if(slider != null) {
			slider.initContext();
		}
	}
	
	
	public void setInfo(String patID, String studyUID, String seriesUID, String[] sopUIDs, ArrayList<String> pathToImages) {
		this.patID = patID;
		this.studyUID = studyUID;
		this.seriesUID = seriesUID;
		this.sopUIDs = sopUIDs;
		setImageFileLocations(pathToImages);
	}
	
	public ViewMode getViewMode() {
		return mode;
	}
	
	public HashMap<String,Object> getInfoSet() {
		HashMap<String,Object> infoset = new HashMap<>();
		infoset.put("Patient​ID", patID);
		infoset.put("StudyInstanceUID", studyUID);
		infoset.put("SeriesInstanceUID", seriesUID);
		infoset.put("SOPInstanceUIDs", sopUIDs);//arraylist
		return infoset;
	}
	
	public PraparatViewControlPanel getController() {
		return pvcp;
	}
	
	public void setFilmGridColumns(int num) {
		this.filmGridColumns = num;
	}

	public int getSlideHolderWidth() {
		if(viewPane == null) {
			return -1;
		}
		return viewPane.getWidth();
	}

	public int getSlideHolderHeight() {
		if(viewPane == null) {
			return -1;
		}
		return viewPane.getHeight();
	}
	
	/**
	 * series view JLayeredPane
	 * @return
	 */
	public JLayeredPane getPraparatViewPane() {
		return viewPane;
	}
	
	public JLayer<JLayeredPane> getPraparatView() {
		return praparatView;
	}

	/*
	 * refleshAndLoadNewSlideGlasses
	 */
	public void wink(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
		// do not allow sopUIDs NULL.
		if (sopUIDs == null) {
			return;
		}
		// at first, remove current series image.
		removeSlide(getCurrentSlide());
		// get new series info
		prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
		slider.initContext();
		// show new series
//		showFirstImage();
		initImageSizeAndShowFirstImage();
	}

	public void constructSeriesGlassesAsLayer() {
		//including only one series.
		ArrayList<String> imgFiles = getImageFileLocations();
		if (imgFiles == null || imgFiles.size() < 1) {
			logger.info("Please set file locations, Praparat::constructSeriesGlassesAsLayer");
			return;
		}
		// init
		slides = new HashMap<Integer, JLayer<SlideGlass>>();
		/*
		 * image files already sorted by inst No.
		 */
		DICOMBackend be = DICOMBackend.getCurrent();
		for (int i = 0; i < imgFiles.size(); i++) {
			DicomImage header = DicomImage.newDicomImage(imgFiles.get(i), be);
			// TODO
			/*
			 * pdfToDicomObject()
			 */
			if(header.isPDF()) {
				//read as series level
				PDFReader pdfReader = new PDFReader(new File(imgFiles.get(i)));
				ImagePlus pdfStack = pdfReader.pdf2ImageStack();
				isMultiframe = pdfStack.getNSlices() > 1;
				if(!isMultiframe) {
					ImageProcessor instIp = pdfStack.getProcessor();
					header.setPixelData(0, pdfStack.getWidth(), pdfStack.getHeight(), 3, 8, instIp.getPixels());
					DicomObject header_core = ((DicomObject)header.getCore());
					header_core.setString(Tag.Instance​Number, VR.IS, String.valueOf((1)));
					SlideGlass sg = new SlideGlass(this, header);
					JLayer<SlideGlass> ly = sg.getSlideGlassAsLayer();
					slides.put(0, ly);
				}else {
					for(int j=0;j<pdfStack.getStackSize();j++) {
//						ImageProcessor instIp = pdfStack.getStack().getProcessor(j+1);
//						DicomObject header_core = ((DicomObject)header.getCore().duplicate());
//						header.setPixelData(j, pdfStack.getWidth(), pdfStack.getHeight(), 3, 8, instIp.getPixels());
//						header_core.setString(Tag.Instance​Number, VR.IS, String.valueOf((j+1)));
//						DicomImage dcm = DicomImage.newDicomImage(header_core, UID.EncapsulatedPDFStorage);
//						
//						SlideGlass sg = new SlideGlass(this, iamge, studyColor);
//						// change to jlayer
//						JLayer<SlideGlass> ly = sg.getSlideGlassAsLayer();
//						slides.put(j, ly);
					}
				}
				pdfReader.close();
				continue;
			}
			/*
			 * multiframe to one series.
			 */
			isMultiframe = header.isMultiFrame();
			//single frame
			if(!isMultiframe) {
				DicomImage dcmimg = DicomImage.newDicomImage(imgFiles.get(i), DICOMBackend.getCurrent());
				/*
				 * TODO
				 * if decompressed, update DicomImage and Attributes TSUID.
				 * but, original TSUID remains in it ?,... 
				 */
				if(Codec.isCompressed(dcmimg.getCore().getString(Tag.Transfer​Syntax​UID))) {
					Decompressor decom = Decompressor.newInstance(dcmimg.getCore(), dcmimg.getTSUID().uid());
					if(decom.decompress()) {
//						dcmimg.decompressed(true);
					}
				}
				SlideGlass sg = new SlideGlass(this, dcmimg);
				// change to jlayer
				JLayer<SlideGlass> l = sg.getSlideGlassAsLayer();
				slides.put(i, l);
			//multiframe
			}else {
				/*
				 * TODO 20231008
				 */
				//read and decompressed
//				DICOMImage frames = new DICOMImage();//multi
//				frames.load(imgFiles.get(i));
//				DicomObject multi_dcm = new DicomObject(imgFiles.get(i), true);
//				GImageReader gir = new GImageReader();
//				ArrayList<DicomObject> headers = gir.readMultiFrameDicomHeaders(multi_dcm);
//				for(int j=0;j<frames.getNSlices();j++) {
//					ImagePlus inst = new ImagePlus("", frames.getImageStack().getProcessor(j+1));
//					inst.setCalibration(frames.getCalibration().copy());
//					DicomObject header_in_frames = headers.get(j);
//					SlideGlass sg = new SlideGlass(this, inst, header_in_frames, studyColor);
//					// change to jlayer
//					JLayer<SlideGlass> l = sg.getSlideGlassAsLayer();
//					slides.put(j, l);
//				}
				/*
				 * if multiframe, load only one file.
				 */
				return;
			}
//			gir = null;
		}
	}
	
	/*
	 * TODO 20231008
	 */
	public void constructSeriesGlassesAsLayerUsingImagePlus(ImagePlus images) {
		if (images == null || images.getStackSize() < 1) {
			System.out.println("Please set images, Praparat::constructSeriesGlassesAsLayerUsingImagePlus");
			return;
		}
		// init
		removeShowingSlide();
		slides = new HashMap<Integer, JLayer<SlideGlass>>();// need
		if(images.getNSlices() == 1) {
			/*
			 * TODO 20231008
			 */
			DicomObject dcmObj = DicomObject.newDicomObject();
			
			//add attributes from imageplus's fileinfo
			//...TODO...
			
			DicomImage dcm = DicomImage.newDicomImage(dcmObj, UID.ImplicitVRLittleEndian);
			SlideGlass sg = new SlideGlass(this, dcm);
			// change to jlayer
			JLayer<SlideGlass> l = sg.getSlideGlassAsLayer();
			slides.put(0, l);
		}else {
			for (int i = 0; i < images.getStackSize(); i++) {
				images.setSlice(i + 1);
				ImagePlus slice = new ImagePlus("" + (i + 1), images.getProcessor().duplicate());
				slice.setCalibration(images.getCalibration().copy());
				slice.setProperty("Info", images.getStack().getSliceLabel(i+1));
				/*
				 * TODO 20231008
				 */
//				SlideGlass sg = new SlideGlass(this, slice,null, studyColor);
//				// change to jlayer
//				JLayer<SlideGlass> l = sg.getSlideGlassAsLayer();
//				slides.put(i, l);
			}
		}
	}
	
	public void loadSlideGlasses(HashMap<Integer, JLayer<SlideGlass>> slides) {
		if (slides == null || slides.size() < 1) {
			System.out.println("Slides have no images...");
			return;
		}
		// init
		if(isVisible()) {
			removeShowingSlide();
		}
		this.slides = new HashMap<Integer, JLayer<SlideGlass>>();
		
		Set<Integer> keys = slides.keySet();
		for(Integer k : keys) {
			//init slide from another slides to set this praparat.
			SlideGlass sg = slides.get(k).getView();
			SlideGlass newsg = new SlideGlass(this, sg.getDicomImage());
			JLayer<SlideGlass> ly = newsg.getSlideGlassAsLayer();
			this.slides.put(k, ly);
		}
		
		slider.initContext();
		
		if(Utils.isDebug) {
			System.out.println(slides.size()+" images loaded.");
		}
	}


	/**
	 * without frameOfReferenceUID
	 * @return
	 */
	public Object[] getUIDs() {
		Object[] uids = new Object[5];
		uids[0] = patID;
		uids[1] = studyUID;
		uids[2] = seriesUID;
		uids[3] = sopUIDs;// String[]
		return uids;
	}
	
	public void setStudyColor(Color color) {
		if(color != null) {
			this.studyColor = color;
		}
	}
	
	public Color getStudyColor() {
		return this.studyColor;
	}

	public Eyepiece getEyepieceAsPraparatManager() {
		return prapManager;
	}
	
	public void setImagePositionTo(SlideGlass sg) {
		Set<Integer> keys = slides.keySet();
		for(Integer key : keys) {
			SlideGlass slide = slides.get(key).getView();
			if(sg == slide) {
				setImagePosition(key);
				break;
			}
		}
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
		if(viewPane == null) {
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
		prevSlice = currentSlice;
		removeShowingSlide();
		currentSlice = sliceIndex;
		JLayer<SlideGlass> currentGlass = this.slides.get(currentSlice);
		// avoid starting up Null excep.
		if (currentGlass == null) {
			return;
		}
		fitImageSize2PraparatView(sliceIndex);
//		System.out.println("Current Image Position " + (currentSlice + 1) + "/" + slides.size());
		viewPane.add(currentGlass);
		viewPane.validate();
		viewPane.repaint();
		currentGlass.requestFocus();//IMPORTANT for key listener
	}

	/**
	 * Specify slideglass position on prap.
	 * pos: 0 to n-1
	 */
	public void setImagePositionUsingSlider(int pos) {
		slider.setSlice(pos);
	}
	
	public void setNextSlice() {
		setImagePositionUsingSlider(currentSlice+1);
	}
	
	public void setPreviousSlice() {
		setImagePositionUsingSlider(currentSlice-1);
	}

	/*
	 * do not use, see SlideGlass::calcImageSize2FitComponent() or fit2praparat()
	 */
//	public int[] calcSlideSize(int compWidth, int compHeight) {
//		JLayer<SlideGlass> currentGlass = this.slides.get(currentSlice);
//		Dimension orgImgSize = currentGlass.getView().getOriginalImageSize();
//		int original_width = orgImgSize.width;
//		int original_height = orgImgSize.height;
//		int bound_width = compWidth;
//		int bound_height = compHeight;
//		// first, adjust new component size
//		int new_width = bound_width;
//		// scale height to maintain aspect ratio
//		int new_height = (new_width * original_height) / original_width;
//		// then check if we need to scale width
//		if (original_width > bound_width) {
//			new_width = bound_width;
//			new_height = (new_width * original_height) / original_width;
//		}
//		// then check if we need to scale even with the new height
//		if (new_height > bound_height) {
//			new_height = bound_height;
//			new_width = (new_height * original_width) / original_height;
//		}
//		return new int[] { new_width, new_height };
//	}
	
	/*
	 * 表示中のGridViewのサイズを調整する
	 */
	public void adjustGridViewSize() {
		if(gridPane == null || !showGridViewOn) {
			return;
		}
		if(viewPane.getWidth()<1 && viewPane.getHeight()<1) {
			return;
		}
		// calc grid size
		JPanel gridView = (JPanel) gridPane.getViewport().getView();
		GridLayout gl = (GridLayout) gridView.getLayout();
		gridPane.setBounds(0, 0, viewPane.getWidth(), viewPane.getHeight());
		//show always square
		int scrollBarWidth = 15;//default size //gridPane.getVerticalScrollBar().getWidth();return zero 
		int gridX = (viewPane.getWidth()-scrollBarWidth) / gl.getColumns();
		int gridY = gridX;// viewPane.getHeight()/row;
		int s = getNumberOfImages();
		for (int i = 0; i < s; i++) {
			JLayer<SlideGlass> slide = slides.get(i);
			slide.getView().fitImg2Comp(gridX, gridY);
//			slide.getView().showBorder(false);
		}
		gridView.repaint();
		gridPane.repaint();
		viewPane.repaint();
	}

	public int getCurrentSlidePos() {
		return currentSlice;
	}
	
	public int getSlidePosition(String sopUID) {
		HashMap<Integer, JLayer<SlideGlass>> slides = getAllSlides();
		if(slides == null) {
			return -1;
		}
		for(int p : slides.keySet()) {
			JLayer<SlideGlass> g = slides.get(p);
			SlideGlass sg = g.getView();
			String sopUID_ = sg.getSOPInstanceUID();
			if(sopUID.equals(sopUID_)) {
				return p;
			}
		}
		return -1;
	}
	
//	private void fitAllImagesSize2PraparatView() {
//		if(slides == null || slides.size() == 0) {
//			return;
//		}
//		if(getSlideHolderWidth() == 0 || getSlideHolderHeight() == 0) {
//			return;
//		}
//		for(int i=0;i<slides.size();i++) {
//			SlideGlass sg = slides.get(i).getView();
////			sg.fitImg2Comp(new_size[0], new_size[1], getSlideHolderWidth(), getSlideHolderHeight());
//			sg.fit2Praparat();
//		}
//		viewPane.validate();
//		viewPane.repaint();
//	}
	
	private void fitImageSize2PraparatView(int slice) {
		if(slides == null || slides.size() == 0 || slides.get(slice).getView() == null) {
			return;
		}
		if(viewPane == null) {
			return;
		}
		if(getSlideHolderWidth() == 0 || getSlideHolderHeight() == 0) {
			return;
		}
		SlideGlass current = slides.get(slice).getView();
		//set sg size to same size of prap.
		current.setSize(new Dimension(viewPane.getWidth(), viewPane.getHeight()));//IMPORTANT
		current.fit2Praparat();
		//set origin all slides
		if(processSeries) {
			for (Integer key : slides.keySet()) {
				JLayer<SlideGlass> sl = slides.get(key);
				SlideGlass sg_ = sl.getView();
				if(sg_ == current) {
					continue;
				}
				sg_.setSize(new Dimension(viewPane.getWidth(), viewPane.getHeight()));//IMPORTANT
				sg_.updateScale();
				if (!current.panningFlag && !sg_.panningFlag) {
					sg_.lastOriginX = current.originX;
					sg_.lastOriginY = current.originY;
					sg_.originX = current.originX;
					sg_.originY = current.originY;
//					System.out.println("default origin adjusted !!! :"+sg_.originX+" "+sg_.originY);
				}
				/*
				 * too slow
				 */
//				sg_.fit2Praparat();
			}
		}
	}
	
	public void initImageSizeAndShowFirstImage() {
//		initImageSize to component
//		fitAllImagesSize2PraparatView();
		
		previousPraparatX = getWidth(); 
		previousPraparatY = getHeight(); 
		showFirstImage();
		fitImageSize2PraparatView(currentSlice);
	}

	public void showFirstImage() {
		// position range is 0 to n
		prevSlice = -1;// IMPORTANT
		currentSlice = 0;
//		System.out.println("showFirstImage:: "+viewPane.getWidth()+" "+viewPane.getHeight());
		setImagePosition(currentSlice);
	}

	public void resetView() {
		if (getCurrentSlidePos() == -1) {
			return;
		}
		//for mpr view
		if (Viewer2DScreen.getInstance() == null) {
			return;
		}
		if(mode == ViewMode.Normal) {
			if (isShowGridViewOn()) {
				viewPane.remove(gridPane);
				showGridViewOn = false;
			} else {
				// remove view before init slideglasses.
				viewPane.remove(getCurrentSlide());
			}
			prevSlice = -1;
			currentSlice = 0;
			updateInfoLabel(-1,-1,"-1",-1,-1,-1);
			// reload slides
			prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
			slider.initContext();
			initImageSizeAndShowFirstImage();
			setTextVisible(pvcp.isShowInfo());
			setAnnotationVisible(pvcp.isShowRoi());
			return;
		}
		
		if(mode == ViewMode.SingleGrid) {
			viewPane.remove(getCurrentSlide());
			prevSlice = -1;
			currentSlice = 0;
			updateInfoLabel(-1,-1,"-1",-1,-1,-1);
			// reload slides
			prepareSlideGlasses(patID, studyUID, seriesUID, sopUIDs);
			setTextVisible(pvcp.isShowInfo());
			setAnnotationVisible(pvcp.isShowRoi());
			slider.initContext();
			initImageSizeAndShowFirstImage();
			return;
		}
		
		if(mode == ViewMode.FilmGrid) {
			viewPane.remove(gridPane);
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

	public void removeSlide(JLayer<SlideGlass> slide) {
		if (isShowGridViewOn()) {
			if (gridPane != null) {
				viewPane.remove(gridPane);
			}
		} else {
			if (slide != null) {
				viewPane.remove(slide);
				viewPane.removeAll();
			}
		}
		updateInfoLabel(-1,-1,"-1",-1,-1,-1);
	}
	
	@SuppressWarnings("unchecked")
	public void removeShowingSlide() {
		if(prevSlice == -1) {
			if(viewPane == null) {
				return;
			}
			Component con = viewPane.getComponentAt(getSlideHolderWidth()/2,getSlideHolderHeight()/2);
			if(con instanceof JLayer<?>) {
				removeSlide((JLayer<SlideGlass>)con);
			}
			return;
		}
		JLayer<SlideGlass> prevGlass = this.slides.get(prevSlice);
		if (prevGlass != null) {
			removeSlide(prevGlass);
		}
	}

//	void saveSeriesSettings() {
//		winMin = imagePane.currentMin;
//		winMax = imagePane.currentMax;
//		//add ...
//	}

	public int[] getSlideHolderLocation() {
		return new int[] { viewPane.getX(), viewPane.getY() };
	}

	public Point getSlideHolderLocationOnScreen() {
		return viewPane.getLocationOnScreen();
	}

	public void setAndShowPixelValue(int X, int Y) {
		SlideGlass currentSlide = getCurrentSlide().getView();
		double scale = currentSlide.getScaleFactor();
		double mag = currentSlide.getMagnification();
		double rotate = currentSlide.getRotateAngle();
		if(!currentSlide.isRGB()) {
			Double[] pixelRawAndCalibrated = (Double[])getCurrentSlide().getView().getPixelValueFromDisplay(X, Y);
			double raw_v = pixelRawAndCalibrated[0];
			double calibrated_v = pixelRawAndCalibrated[1];
			updateInfoLabel(X, Y, raw_v+"("+calibrated_v+")",scale,mag,rotate);
		}else {
			String[] rgbAndColor = (String[])getCurrentSlide().getView().getPixelValueFromDisplay(X, Y);
			String r = rgbAndColor[0];
			String g = rgbAndColor[1];
			String b = rgbAndColor[2];
//			String color = rgbAndColor[3];//java.awt.Color[r,g,b]
//			updateInfoLabel(X, Y, r+","+g+","+b+" "+"("+color+")", scale, mag, rotate);
			updateInfoLabel(X, Y, r+","+g+","+b, scale, mag, rotate);
		}
	}
	
	protected void updateInfoLabel(int x, int y, String value, double scale, double mag, double rotate) {
		if(getViewMode() != ViewMode.Thumbnail) {
			this.pvcp.setText2InfoLabel(x, y, value, scale, mag, rotate);
		}
	}

	public void doFilmGridLayout(int col) {
		if(!isShowGridViewOn()) {
			System.out.println("You must set gridViewOn before do this.");
			return;
		}
		setFilmGridColumns(col);
		try {
			Component c = viewPane.getComponent(0);
			if (c instanceof JLayer<?>) {
				viewPane.remove(getCurrentSlide());
			} else if (gridPane != null && c == gridPane) {
				viewPane.remove(gridPane);
			}
		}catch(java.lang.ArrayIndexOutOfBoundsException e) {
			//do nothing
		}
		
		// calc num of row
		int row = 1;
		int numOfImage = slides.size();
		if (numOfImage % col > 0) {
			row = (int) (numOfImage / col) + 1;
		} else {
			row = (int) (numOfImage / col);
		}
		JPanel gridView = new JPanel();
		gridView.setLayout(new GridLayout(row, col));
		gridPane = new JScrollPane(gridView);
		viewPane.add(gridPane);
		setCursor(new Cursor(Cursor.WAIT_CURSOR));
		for (int i = 0; i < numOfImage; i++) {
			JLayer<SlideGlass> slide = slides.get(i);
			gridView.add(slide);
		}
		gridView.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
		/*
		 * if already showing parapat on anything component, prap is directly adjusted by adjustGridViewSize().
		 */
		adjustGridViewSize();
	}
	
	public void setAnnotationVisible(boolean v) {
		HashMap<Integer, JLayer<SlideGlass>> slides = getAllSlides();
		if(slides == null) {
			return;
		}
		if (isShowGridViewOn()) {
			for (Integer k : slides.keySet()) {
				JLayer<SlideGlass> s = slides.get(k);
				s.getView().setAnnotationVisible(v);
			}
		} else {
			if(processSeries) {
				for (Integer k : slides.keySet()) {
					JLayer<SlideGlass> s = slides.get(k);
					s.getView().setAnnotationVisible(v);
				}
			}else {
				getCurrentSlide().getView().setAnnotationVisible(v);
			}
		}
	}

	public void setTextVisible(boolean v) {
		HashMap<Integer, JLayer<SlideGlass>> slides = getAllSlides();
		if(slides == null) {
			return;
		}
		if (showGridViewOn) {
			for (Integer k : slides.keySet()) {
				JLayer<SlideGlass> s = slides.get(k);
				s.getView().setTextVisible(v);
			}
		} else {
			if(this.processSeries) {
				for (Integer k : slides.keySet()) {
					JLayer<SlideGlass> s = slides.get(k);
					s.getView().setTextVisible(v);
				}
			}else {
				getCurrentSlide().getView().setTextVisible(v);
			}
		}
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
	
	public boolean getProcessSeries() {
		return pvcp.processSeries();
	}

	public int getNumberOfImages() {
		if (isMultiframe) {
			return slides.size();
		} else {
			return slides.size();
		}
	}

	private void setImageFileLocations(ArrayList<String> pathToImages) {
		this.pathToImages = pathToImages;
	}

	public ArrayList<String> getImageFileLocations() {
		return this.pathToImages;
	}

	public JLayer<SlideGlass> getCurrentSlide() {
		if (currentSlice == -1) {
			return null;
		}
		return slides.get(currentSlice);
	}
	
	public HashMap<Integer,JLayer<SlideGlass>> getAllSlides() {
		if (currentSlice == -1) {
			return null;
		}
		return slides;
	}
	
	/**
	 * return stacked series slides as imageplus.
	 * @return imageplus
	 */
	public ImagePlus getStackSeries() {
		JLayer<SlideGlass> currentSlide = getCurrentSlide();
		if(currentSlide == null) {
			return null;
		}
		ImagePlus ref_imp = currentSlide.getView().getOriginalImage();
		ref_imp.deleteRoi();
		ref_imp.resetDisplayRange();
		Calibration cal = ref_imp.getCalibration().copy();
		int size = getNumberOfImages();
		ImageStack stack = new ImageStack(ref_imp.getWidth(), ref_imp.getHeight(), size);
		HashMap<Integer,JLayer<SlideGlass>> all_slides = getAllSlides();
		for(int i=0; i<size; i++) {
			ImagePlus imp = all_slides.get(i).getView().getOriginalImage();
			imp.deleteRoi();
			ImagePlus imp2 = new Duplicator().run(imp);
//			imp2.setSlice(1);//1 slideglass have 1 imageplus
			ImageProcessor ip = imp2.getProcessor();//.duplicate();
			if(ip.getNChannels() == 3 && ip instanceof ColorProcessor) {
				ip.snapshot();//keep original pixels
			}
			ip.resetMinAndMax();
			stack.setProcessor(ip, i+1);//1<=N<=slices
		}
		ImagePlus stacked = new ImagePlus("stack-series", stack);
		stacked.setCalibration(cal);
		return stacked;
	}
		
	public void clearCrossLines() {
		HashMap<Integer,JLayer<SlideGlass>> slides = getAllSlides();
		for(Integer sglKey : slides.keySet()) {
			JLayer<SlideGlass> sgl = slides.get(sglKey);
			CanvasGlass cg = (CanvasGlass) sgl.getView().getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
			cg.setCrossLine(null);
		}
	}
	
	public void setPraparatViewSize(int w, int h) {
		if(!isShowGridViewOn()) {
			if(viewPane != null) {
				viewPane.setSize(w, h);
				viewPane.setBounds(0, 0, w, h);
				praparatView.setSize(w, h);
				praparatView.setBounds(0, 0, w, h);
				if(mode == ViewMode.Thumbnail) {
					viewPane.setPreferredSize(new Dimension(w, h));
					praparatView.setPreferredSize(new Dimension(w, h));
				}
				viewPane.repaint();
			}
		}else {
			if(gridPane != null) {
				int scrollWidth = gridPane.getHorizontalScrollBar().getWidth(); 
				gridPane.setBounds(0, 0, w, h);
				if(viewPane != null) {
					viewPane.setSize(w-scrollWidth, h);
					viewPane.setBounds(0, 0, w-scrollWidth, h);
					praparatView.setSize(w, h);
					praparatView.setBounds(0, 0, w, h);
					viewPane.repaint();
				}
				gridPane.repaint();
			}
		}
	}

	public int getImageScreenSizeX() {
		return viewPane.getWidth();
	}

	public int getImageScreenSizeY() {
		return viewPane.getHeight();
	}

	public void gridViewOn(boolean show) {
		this.showGridViewOn = show;
	}

	public boolean isShowGridViewOn() {
		return this.showGridViewOn;
	}

	public boolean isFocusGained() {
		return focusGained;
	}

	public void setFocusGained(boolean focusGained) {
		this.focusGained = focusGained;
		if(getViewMode()!=ViewMode.SingleGrid && getViewMode()!=ViewMode.FilmGrid) {
			showBorder();
		}
		repaint();
	}

	// list selection action
	public void setSelectionState(boolean select) {
		this.selected = select;
		showBorder();
		repaint();
	}

	// mouse action
	public void setSelectionState() {
		if (isSelected()) {
			setSelectionState(false);
		} else {
			setSelectionState(true);
		}
	}

	public boolean isSelected() {
		return selected;
	}
	
	public boolean isMultiFrame() {
		return this.isMultiframe;
	}

	public void showBorder() {
		if(this.mode==ViewMode.FilmGrid || this.mode == ViewMode.SingleGrid) {
			setBorder(BorderFactory.createLineBorder(getBackground(), 4));
			return;
		}
		if(this.mode == ViewMode.Thumbnail) {
			if (isSelected()) {/*DO NOT USE forcusGained here.*/
				Border selectionBorder = BorderFactory.createLineBorder(new Color(0, 50, 240, 100), 4);
				setBorder(selectionBorder);
			}else {
				setBorder(BorderFactory.createLineBorder(getBackground(), 4));
			}
			return;
		}
		
		if (isSelected() && !isFocusGained()) {// show border force
			Border selectionBorder = BorderFactory.createLineBorder(new Color(0, 50, 240, 100), 4);
			setBorder(selectionBorder);
			repaint();
		} else if (!isSelected() && isFocusGained()) {
			Border focusBorder = BorderFactory.createLineBorder(studyColor, 4);
			setBorder(focusBorder);
			repaint();
		} else if (isSelected() && isFocusGained()) {
			//if you want 3 color border
//			Border studyBorder = BorderFactory.createLineBorder(studyColor, 2);
//			Border selectionBorder = BorderFactory.createLineBorder(new Color(0, 50, 240, 100), 1);
//			Border focusBorder = BorderFactory.createLineBorder(Color.CYAN, 1);
//			Border compBorder1 = new CompoundBorder(focusBorder, selectionBorder);
//			Border compBorder2 = new CompoundBorder(compBorder1, studyBorder);
//			setBorder(compBorder2);
			Border focusBorder = BorderFactory.createLineBorder(studyColor, 2);
			Border selectionBorder = BorderFactory.createLineBorder(new Color(0, 50, 240, 100), 2);
			Border compBorder = new CompoundBorder(selectionBorder, focusBorder);
			setBorder(compBorder);
			repaint();
		} else {
			setBorder(BorderFactory.createLineBorder(getBackground(), 4));
			repaint();
		}
	}
	
	public void setShowCrossLineMode(boolean crossMode) {
		this.crossLineCursorMode = crossMode;
	}
	
	public boolean isShowCrossLineMode() {
		return this.crossLineCursorMode;
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
			HashMap<Integer, JLayer<SlideGlass>> slides = p.slides;
			for(Integer k:slides.keySet()) {
				JLayer<SlideGlass> s = slides.get(k);
				s.getView().drawLocalizer(null);
			}
		}
		// show localizer on slideglass
		SlideGlass target = getCurrentSlide().getView(); 
		for(Praparat p:praps) {
			//if self, skip
			if(p == this) {
				continue;
			}
			SlideGlass src = p.getCurrentSlide().getView();
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
	
	public void invertImages() {
		if(!getProcessSeries()) {
			SlideGlass sg = getCurrentSlide().getView();
			sg.invert();
		}else {
			for(Integer key:slides.keySet()) {
				SlideGlass sg = slides.get(key).getView();
				sg.invert();
			}
		}
	}
	
	public void flipLR() {
		if(!getProcessSeries()) {
			SlideGlass sg = getCurrentSlide().getView();
			sg.flipLR();
		}else {
			for(Integer key:slides.keySet()) {
				SlideGlass sg = slides.get(key).getView();
				sg.flipLR();
			}
		}
	}
	
	public void flipHF() {
		if(!getProcessSeries()) {
			SlideGlass sg = getCurrentSlide().getView();
			sg.flipHF();
		}else {
			for(Integer key:slides.keySet()) {
				SlideGlass sg = slides.get(key).getView();
				sg.flipHF();
			}
		}
	}
		
	public void loadRoiFromDB() {
		SlideGlass sg = getCurrentSlide().getView();
		sg.loadRoiFromDB();
	}
	
	public void setReferenceLine(ReferenceLine refLine) {
		this.refLine = refLine;
	}
	
	public ReferenceLine getReferenceLine() {
		return this.refLine;
	}
	
	public void setLUT(LUT lut) {
		if(!getProcessSeries()) {
			SlideGlass sg = getCurrentSlide().getView();
			sg.setLUT(lut);
		}else {
			for(Integer key:slides.keySet()) {
				SlideGlass sg = slides.get(key).getView();
				sg.setLUT(lut);
			}
		}
	}
	
	/**
	 * check Viewer2D selecting tool.
	 * @return
	 */
	public int getCurrentViewerToolType() {
		return Viewer2DScreen.getInstance().getCurrentToolType();
	}
	
	public ImagePlus cropRectangle(boolean show) {
		SlideGlass sg = getCurrentSlide().getView();
		RoiObj roi = sg.findCurrentRoi();
		if(roi == null) {
			return null;
		}
		int res = JOptionPane.showConfirmDialog(viewPane, "process all slide in this series ?", "Crop series ?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		final ImagePlus crop;
		if(res != JOptionPane.YES_OPTION) {
			crop = sg.cropRect(roi);
		}else {
			HashMap<Integer,JLayer<SlideGlass>> slides = getAllSlides();
			Set<Integer> keys = slides.keySet();
			int size = keys.size();
			int pos = 1;
			ImageStack cropStack = null;
			Calibration cal = null;
			for(Integer k : keys) {
				JLayer<SlideGlass> l = slides.get(k);
				SlideGlass slide = l.getView();
				ImagePlus c = slide.cropRect(roi);
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
	
	public void cut() {
		SlideGlass sg = getCurrentSlide().getView();
		RoiObj currentRoi = sg.findCurrentRoi();
		if(currentRoi == null ) {
			return;
		}
		int roiType = currentRoi.getType();
		if(roiType == RoiObj.ANGLE || roiType == RoiObj.ARROW || roiType == RoiObj.FREELINE || roiType == RoiObj.POINT || roiType==RoiObj.LINE) {
			JOptionPane.showMessageDialog(Viewer2DScreen.getInstance(), "Cut process needed closed type roi.");
			return;
		}
		int res = JOptionPane.showConfirmDialog(viewPane, "process all slide in this series ?", "Cut...", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if(res == JOptionPane.YES_OPTION) {
			HashMap<Integer,JLayer<SlideGlass>> slides = getAllSlides();
			Set<Integer> keys = slides.keySet();
			for(Integer k : keys) {
				JLayer<SlideGlass> l = slides.get(k);
				SlideGlass s = l.getView();
				s.cut(currentRoi);
			}
		}else {
			JLayer<SlideGlass> l = getCurrentSlide();
			SlideGlass s = l.getView();
			s.cut(currentRoi);
		}
	}

	private void findMultiframeStatus() {
//        textOverlay.multiframeStatusDisplay(imgpanel.isMultiFrame());//tatsu
	}

	private void setTextOverlayParam() {
//        textOverlay.setTextOverlayParam(imgpanel.getTextOverlayParam());//tatsu
	}

	@Override
	public void componentHidden(ComponentEvent arg0) {}

	@Override
	public void componentMoved(ComponentEvent arg0) {}

	@Override
	public void componentResized(ComponentEvent e) {
		if(Utils.isDebug) System.out.println("Praparat:ComponentResized !!:"+this.mode);

		/*
		 * Can not get win size before 2DViewerFrame.setVisible(true)
		 * You should get prap (or other component) size after show Viewer2D
		 */
//		Viewer2DScreen viewerWin = Viewer2DScreen.getInstance();
//		if(viewerWin == null && !viewerWin.isVisible()) {
//			return;
//		}
		int compW = getSlideHolderWidth();
		int compH = getSlideHolderHeight();
		if (compW < 1 && compH < 1) {
			return;
		}
		//setPreferredSize::can not get getWidth/getHeight.
		viewPane.setSize(new Dimension(compW, compH));
		viewPane.setPreferredSize(new Dimension(compW, compH));
		
		//resize slider
		if(slider != null) {
			slider.setPreferredSize(new Dimension(slider.getWidth(), slider.getHeight()));
			slider.revalidate();
			slider.repaint();
		}
		//resize images
		// if resizing size is small, ignore.
		int subX = Math.abs(previousPraparatX - compW);
		int subY = Math.abs(previousPraparatY - compH);
		if (subX > 3 || subY > 3) {
			if (!isShowGridViewOn()) {
				fitImageSize2PraparatView(currentSlice);
			} else {
				adjustGridViewSize();
			}
		}
	}
	
	@Override
	public void componentShown(ComponentEvent arg0) {
		/*
		 * dummy, it is never call. MUST use JFrame.
		 */
	}
}
