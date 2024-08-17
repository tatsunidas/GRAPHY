package com.vis.core.view.D2.roi;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.swing.JTextArea;

import com.vis.core.view.D2.ui.glasses.*;

import ij.IJ;
import ij.process.ImageStatistics;

/**
 * TODO
 * 
 * RoiPopUpDialogは、CanvasGlassのpaintComponent内で表示・非表示を操作できるように改良したい。
RoiMouseMoved、ActivateROI、ShowDialog。
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class RoiPopUpDialog extends JTextArea{

	private RoiObj roi;
	private SlideGlass owner; //SlideGlass.getObserbables
	// on slideglass x,y
	private int lastPressX = 0;
	private int lastPressY = 0;
	private int lastDragX = 0;
	private int lastDragY = 0;
	
	private Color backColor = new Color(0, 100, 255, 76);//rgba

	//setLocation
	//setBounds
	//setPreferredSize
	
	public RoiPopUpDialog(SlideGlass owner, RoiObj roi) {
		super();
		this.roi = roi;
		this.owner = owner;
		setUp();
	}

	private void setUp() {
//		setOpaque(false);//DO NOT USE
		setLayout(null);
		setBackground(backColor);
		setPreferredSize(new Dimension(100, 70));
		setSize(new Dimension(100, 70));
//		setBounds(0, 0, 70, 50);
		setText("no info");
		setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
		setLineWrap(true);
		setAutoscrolls(true);
		setEditable(false);
		setFocusable(false);
		setRequestFocusEnabled(false);
	}
	
	public void setSlideGlass(SlideGlass slide) {
		this.owner = slide;
	}
	
	public void setRoi(RoiObj roi) {
		this.roi = roi;
	}
	
	public RoiObj getRoi() {
		return roi;
	}
	
	public void updateText(String stats) {
		//init text
    	super.selectAll();
    	super.replaceSelection("");
    	//show text
		super.setText(stats);
		//update size
		int numOfRow = stats.split("\n").length;
		int stringWidth = (getFontMetrics(getFont()).stringWidth(stats)/numOfRow)+10;
		int stringHeight = getFontMetrics(getFont()).getHeight()*numOfRow;
		super.setSize(stringWidth, stringHeight);
		super.repaint();
	}
	
	public void handleMousePressed(MouseEvent e) {
		if(!isVisible()) {
			return;
		}
		if (e.getButton() == MouseEvent.BUTTON1) {//SwingUtilities.isLeftMouseButton(e)) {
			lastPressX = e.getX();//roiDialog origin
			lastPressY = e.getY();
			//init
			lastDragX = e.getXOnScreen();//e.getX();//roiDialog origin
			lastDragY = e.getYOnScreen();//e.getY();
			System.out.println("ROI POPUP DIALOG PRESSD XY:"+lastPressX+" "+lastPressY);
		}
	}
	
	public void handleMouseDragged(MouseEvent e) {
		if(!isVisible()) {
			return;
		}
		/*
		 * usb mouse problem ??
		 * do not use e.getXY
		 */
		System.out.println("rpdDragX:"+e.getX()+" "+e.getXOnScreen());
		System.out.println("rpdDragY:"+e.getY()+" "+e.getYOnScreen());
		Point currentP = getLocation();
		int defX = e.getXOnScreen() - lastDragX;
		int defY = e.getYOnScreen() - lastDragY;
		int newX = currentP.x + defX;
		int newY = currentP.y + defY;
		setLocation(newX, newY);
		lastDragX = e.getXOnScreen();//roiDialog origin
		lastDragY = e.getYOnScreen();
	}

	public boolean roiAlive() {
		if (roi == null) {
			return false;
		} else {
			return true;
		}
	}
	
	//isVisible, isDisplayable and isShowing
	public void setVisible(boolean show) {
		super.setVisible(show);
		setBasicStats();
	}
	
	public void setBasicStats() {
		ImageStatistics stats = roi.getStatistics();
		if(stats == null) {
			return;
		}
    	String txt = null;
    	RoiType t = RoiType.find(roi.getType());
		switch (t) {
			case LINE:
				txt = IJ.d2s(((Line) roi).getLength(), 1);
				updateText("length:"+txt);
				break;
			case ANGLE:
				txt = IJ.d2s(((PolygonRoi) roi).getAngle(), 1);
				updateText("angle:"+txt);
				break;
			case POINT:
				//on pixel unit XY.
				txt = "x:"+IJ.d2s(((PointRoi) roi).getXBase(), 1);
				txt = txt + " ";
				txt = txt + "y:"+IJ.d2s(((PointRoi) roi).getYBase(), 1);
				Object pixelRawAndCalibrated[] = owner.getPixelValueFromOriginal((int)((PointRoi) roi).getXBase(), (int)((PointRoi) roi).getYBase());
				if(pixelRawAndCalibrated != null) {
					if(pixelRawAndCalibrated instanceof Double[]) {
						Double[] values = (Double[])pixelRawAndCalibrated;
						txt = txt + " :"+IJ.d2s(values[0],0)+"("+IJ.d2s(values[1],0)+")";
					}else {
						String[] rgbAndColor = (String[])pixelRawAndCalibrated;
						String r = rgbAndColor[0];
						String g = rgbAndColor[1];
						String b = rgbAndColor[2];
						String color = rgbAndColor[3];//java.awt.Color[r,g,b]
						txt = txt + " :"+r+","+g+","+b+"("+color+")"+")";
					}
				}
				updateText("locOnOrgAndValue:"+txt);
				break;
			case RECTANGLE:
			case POLYGON:
			case OVAL:
			case COMPOSITE:
				String area = IJ.d2s(stats.area, 1);
				String mean = IJ.d2s(stats.mean, 1);
				txt = "area:" + area + "\n" + "mean:" + mean;
				updateText(txt);
				break;
			default:
				// do nothing
				updateText("no info");
		}
	}
	
	/*
	 * String angle = IJ.d2s(stats.angle, 2);
    	String area = IJ.d2s(stats.area, 2);
    	String mean = IJ.d2s(stats.mean, 2);
    	String min = IJ.d2s(stats.min);
    	String max = IJ.d2s(stats.max);
    	String median = IJ.d2s(stats.median);
    	String std = IJ.d2s(stats.stdDev);
	 */
	/*
	 * 必要なもののみを表示する最大3つ
	 */
//	public void setBasicStats(double length, double angle, double area, double mean, double min, double max, double median, double stdDev) {
//		String angleString = IJ.d2s(angle, 2);
//    	String areaString = IJ.d2s(area, 2);
//    	String meanString = IJ.d2s(mean, 2);
//    	String minString = IJ.d2s(min);
//    	String maxString = IJ.d2s(max);
//    	String medianString = IJ.d2s(median);
//    	String stdString = IJ.d2s(stdDev);
//    	String basicStatString = 
//    			"mean:"+meanString+", "+
//    			"max:"+maxString+", "+
//    			"min:"+minString+", "+
//    			"median:"+medianString+", "+
//    			"stdDev:"+stdString+", "+
//    			"area:"+areaString+", "+
//    			"angle:"+angleString;
//    	//init text
//    	super.selectAll();
//    	super.replaceSelection("");
//    	//show text
//		super.setText(basicStatString);
//		repaint();
//	}

}
