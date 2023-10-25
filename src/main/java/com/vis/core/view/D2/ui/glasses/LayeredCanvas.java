package com.vis.core.view.D2.ui.glasses;



import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

/**
 *
 * @author BabuHussain
 * @author tatsunidas
 * @version 0.1
 *
 */
public class LayeredCanvas extends JLayeredPane {

	int ImageHeight = 512;
	int ImageWidth = 512;
	/*
	 * Layers
	 */
	public SlideGlass imagePane;
	public CanvasGlass annotationPanel;
	public TextOverlayGlass textOverlay;

//    public ij.gui.ImageCanvas canvas;//com.visionary.graphy.form.Canvas
	public boolean focusGained = false;
	public boolean fileIsNull = false;
	private String studyUID = "";

	String imgPath = null;
	BufferedImage img = null;
	ImagePlus imp = null;

	ArrayList<String> pathToImages = null;
//	private DragPane dragPane;

	public LayeredCanvas() {}

	public LayeredCanvas(ArrayList<String> pathToImages) {
		this.pathToImages = pathToImages;
		setBorder(new LineBorder(Color.DARK_GRAY));
		fileIsNull = true;
	}

	public LayeredCanvas(ImagePlus imp) {
		this.imp = imp;
		setBorder(new LineBorder(Color.DARK_GRAY));
		fileIsNull = true;
	}

	public void setImageLocations(ArrayList<String> pathToImages) {
		this.pathToImages = pathToImages;
	}

	private void createLayers(BufferedImage img, int parentWidth, int parentHeight, Attributes header) {
		setSize(parentWidth, parentHeight);
		createImageLayer(img, parentWidth, parentHeight, header);// level4(deepest)
		createTextOverlay(parentWidth, parentHeight, header);// level3
		createAnnotationOverlay(parentWidth, parentHeight);// level2
//		createDragPane(imagePane, parentWidth, parentHeight);//level1(surface)
		
	}

	private void createImageLayer(BufferedImage img, int parentWidth, int parentHeight, Attributes header) {
		if (img == null) {
			System.out.println("LayeredCanvas2::createImageLayer() img is null");
			return;
		}
		ImageWidth = img.getWidth();
		ImageHeight = img.getHeight();
		if (imagePane == null) {// Init imagePane
//			imagePane = new SlideGlass(img, parentWidth, parentHeight, header);
		} else {// set new image
//			imagePane.setNewImage(img, parentWidth, parentHeight, header);
		}
//		imagePane.setBounds(setPointX, setPointY, ImageWidth, ImageHeight);
		this.add(imagePane, 0);
		this.setLayer(imagePane, 0);
	}

	private void createImageLayerForGrid(BufferedImage img, int parentWidth, int parentHeight, Attributes header) {
		if (img == null) {
			System.out.println("LayeredCanvas2::createImageLayer() img is null");
			return;
		}
		ImageWidth = img.getWidth();
		ImageHeight = img.getHeight();
		int setPointX = 0;
		int setPointY = 0;
		if (parentWidth > ImageWidth) {
			setPointX = (parentWidth - ImageWidth) / 2;
		}
		if (parentHeight > ImageHeight) {
			setPointY = (parentHeight - ImageHeight) / 2;
		}
//		imagePane = new SlideGlass(img, header);
//		this.add(imagePane, 0);
//		this.setLayer(imagePane, 0);
	}

	private CanvasGlass createAnnotationOverlay(int parentWidth, int parentHeight) {
//		annotationPanel = new AnnotationGalss(this);
//		annotationPanel.setForeground(Color.white);
//		annotationPanel.setSize(new Dimension(parentWidth, parentHeight));
//		add(annotationPanel, 1);
//		setLayer(annotationPanel, 1);
		return annotationPanel;
	}

	private TextOverlayGlass createTextOverlay(int parentWidth, int parentHeight, Attributes header) {
//		textOverlay = new TextOverlayGlass(parentWidth, parentHeight, header);
		textOverlay.setForeground(Color.white);
		add(textOverlay, 2);
		setLayer(textOverlay, 2);
		return textOverlay;
	}

//    private void createDragPane(ImagePane iPane, int parentWidth, int parentHeight) {
//        dragPane = new DragPane(iPane, parentWidth, parentHeight);
//        add(dragPane,2);
//        setLayer(dragPane, 3);
//    }

	/**
	 * for image preview panel
	 * 
	 * @param                sliceIndex:1 or above
	 * @param MaxResizing512
	 */
//    public void setNewImageLayer(int sliceIndex, boolean MaxResizing512) {
//    	File input = new File(pathToImages.get(sliceIndex-1));
//    	BufferedImage img = null;
//		try {
//			img = ImageIO.read(input);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		if (MaxResizing512) {
//			int w = img.getWidth();
//			int h = img.getHeight();
//			if (w > h) {
//				h = (int) ((double) h * (double) (512 / w));
//				w = 512;
//			} else if (h > w) {
//				w = (int) ((double) w * (double) (512 / h));
//				h = 512;
//			}
//			ImagePlus imp = new ImagePlus("", img);
//			ImageProcessor ip = imp.getProcessor();
//			ip.setInterpolate(true);
//			ip.setInterpolationMethod(ImageProcessor.BICUBIC);
//			ip = ip.resize(w, 512);
//			img = ip.getBufferedImage();
//		}
//		//check dimension
//		removeAll();
//		createLayers(img);
//		repaint();
//    }

	/**
	 * TODO multiframe
	 * 
	 * for main screen grid view
	 * 
	 * @param sliceIndex:number of slice index 1 to end
	 * @param adjustSize:resize it, keep aspect.
	 */
	public void setNewImage(int sliceIndex, int adjustSize, int parentWidth, int parentHeight) {
		if (pathToImages == null) {
			return;
		}
		File input = new File(pathToImages.get(sliceIndex - 1));
		BufferedImage img = null;
		Attributes header = new Attributes();
		DicomInputStream in = null;
		try {
			img = ImageIO.read(input);
//			System.out.println(pathToImages.get(sliceIndex-1));
			in = new DicomInputStream(input);
			header = in.readDataset(-1, Tag.PixelData);// exclude pixeldata
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}catch (java.lang.NullPointerException e) {
				// TODO Auto-generated catch block
				System.out.println("Images are null.");
				return;
			}
		}
		int w = img.getWidth();
		int h = img.getHeight();
//		System.out.println(w+" "+h+" "+adjustSize);
		if (w > h) {
			h = (int) ((double) h * ((double)adjustSize / (double)w));
			w = adjustSize;
		} else if (h > w) {
			w = (int) ((double) w * ((double)adjustSize / (double)h));
			h = adjustSize;
		} else {
			w = adjustSize;
			h = w;
		}
//		System.out.println(w+" "+h);
		ImagePlus imp = new ImagePlus("", img);
		ImageProcessor ip = imp.getProcessor();
		ip.setInterpolationMethod(ImageProcessor.BICUBIC);// must to following resize
		ip = ip.resize(w, h);
//		System.out.println(ip.isGrayscale());
		img = ip.getBufferedImage();
		// check dimension
		removeAll();
		createLayers(img, parentWidth, parentHeight, header);
		repaint();
	}

	public SlideGlass getImagePane() {
		return imagePane;
	}

	public void setImagePane(SlideGlass imagePane) {
		this.imagePane = imagePane;
	}

	public boolean isFocusGained() {
		return focusGained;
	}

	public void setFocusGained(boolean focusGained) {
		this.focusGained = focusGained;
		repaint();
	}

	private void findMultiframeStatus() {
//        textOverlay.multiframeStatusDisplay(imgpanel.isMultiFrame());//tatsu
	}

	private void setTextOverlayParam() {
//        textOverlay.setTextOverlayParam(imgpanel.getTextOverlayParam());//tatsu
	}

	/**
	 * This routine used to set the selection coloring.
	 */
	public void setSelectionColoring() {
		this.setBorder(new LineBorder(new Color(255, 138, 0)));
//        ApplicationContext.imgView.imageToolbar.disableAllTools();        
	}

	public void setColoring() {
		this.setBorder(new LineBorder(new Color(255, 138, 0)));
	}

	/**
	 * This routine used to remove the selection coloring.
	 */
	public void setNoSelectionColoring() {
		this.setBorder(new LineBorder(Color.DARK_GRAY));
	}

//    public Canvas getCanvas() {
//        return canvas;
//    }

	public String getStudyUID() {
		return studyUID;
	}

	public void setStudyUID(String studyUID) {
		this.studyUID = studyUID;
	}
}
