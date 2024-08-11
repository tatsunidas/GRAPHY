package com.vis.core.view.D2.ui.glasses;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JComponent;


import com.vis.core.view.D2.roi.*;
//import com.vis.viewer2d.roi.Line;
//import com.vis.viewer2d.roi.OvalRoi;
//import com.vis.viewer2d.roi.PointRoi;
//import com.vis.viewer2d.roi.PolygonRoi;
//import com.vis.viewer2d.roi.ReferenceLine;
//import com.vis.viewer2d.roi.RoiBrush;
//import com.vis.viewer2d.roi.RoiObj;
//import com.vis.viewer2d.roi.RoiPopupDialog;
//import com.vis.viewer2d.roi.ShapeRoi;
//import com.vis.viewer2d.roi.TextRoi;
import com.vis.core.view.D2.ui.Viewer2DToolBar;

import ij.IJ;
import ij.Prefs;
import ij.gui.Overlay;
import ij.gui.RoiListener;
import ij.measure.Measurements;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.plugin.tool.PlugInTool;
import ij.process.FloatPolygon;

/**
 *
 * Overlay glass for slideglass. As works for canvas of RoiObj.
 * 
 * @author Tatsunidas
 *
 */
public class CanvasGlass extends javax.swing.JPanel {

	private static final long serialVersionUID = 775809436040950583L;
	private Praparat pp;
	private SlideGlass sg;
	private final String sopUID;
	public boolean paintSizeCaliper = true;
	private RoiObj currentRoi = null;
	private RoiObj previousRoi = null;
	private RoiBrush brushTool = null;
	private RoiObj brush = null;//roi brush, see also draw()
	
	private GeneralPath crossLine = null;
	private Color crossLineColor = new Color(31, 255, 0, 127);
	private int crossLineStrokeSize = 3;
	
	private java.util.List<java.awt.geom.Point2D> localizerGeo = null;
	private Color localizerColor = new Color(255, 0, 0, 127);
	private int localizerStrokeSize = 3;

	public CanvasGlass(SlideGlass sg) {
		setOpaque(false);
		setLayout(null);
		this.sopUID = sg.getSOPInstanceUID();
		this.pp = sg.getPraparat();
		this.sg = sg;
	}
	
	/**
	 * keep null-able.
	 */
	public void setCrossLine(GeneralPath cross) {
		//keep null-able
		this.crossLine = cross;
	}
	
	public void setCrossLineColor(Color crossColor) {
		if(crossColor != null) {
			this.crossLineColor = crossColor;
		}
	}
	
	public void setCrossLineStrokeSize(int strokeSize) {
		if(strokeSize > 30) {
			strokeSize = 30;
		}else if(strokeSize < 1) {
			strokeSize = 1;
		}
		crossLineStrokeSize = strokeSize;
	}
	
	public synchronized void setLocalizerGeometry(java.util.List<java.awt.geom.Point2D> localizerGeo) {
		//keep null-able
		this.localizerGeo = localizerGeo;
		revalidate();
	}
	
	public void setLocalizerColor(Color color) {
		if(color != null) {
			this.localizerColor = color;
		}
	}
	
	public void setLocalizerStrokeSize(int strokeSize) {
		if(strokeSize > 30) {
			strokeSize = 30;
		}else if(strokeSize < 1) {
			strokeSize = 1;
		}
		localizerStrokeSize = strokeSize;
	}

	public void setPaintCaliper(boolean show) {
		this.paintSizeCaliper = show;
	}
	
	public String sopInstanceUID() {
		return sopUID;
	}

//	protected boolean waitForImage(Image image) {
//		MediaTracker tracker = new MediaTracker(this);
//		tracker.addImage(image, 0);
//		try {
//			tracker.waitForAll();
//		} catch (InterruptedException e) {
//			/* ignore */ }
//		return (!tracker.isErrorAny());
//	}

	private void showCaliper(Graphics gs) {
		setSize(sg.getWidth(), sg.getHeight());
		// 100 mm scale bar
		gs.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		if (sg != null) {
			if (paintSizeCaliper) {
				if (sg.getDisplayPixelSpacingY() > 0.) {
					// pixels in 100 mm
					/*
					 * calc FOV
					 */
					double fovY = sg.getOriginalPixelSpacingY() * sg.getOriginalImageSize().height;
					double imgY = sg.getCurrentDisplayImagePlus().getHeight();
					double currentViewingPixelSizeY = (double) fovY / imgY;
					double fovX = (int) (sg.getOriginalPixelSpacingX() * sg.getOriginalImageSize().width);
					double imgX = sg.getCurrentDisplayImagePlus().getWidth();
					double currentViewingPixelSizeX = (double) fovX / imgX;
					/*
					 * 表示ピクセルサイズがいくつあれば100mmになるか(num of pixels)
					 */
					int viewScaleHeight = (int) (100 / currentViewingPixelSizeY);
					//show location
					//upper
					int y1 = (getHeight() - viewScaleHeight) / 2;
					//lower
					int y2 = y1 + viewScaleHeight;
					//location x for height
					int hx = 20;
					if(sg.getPixelSpacingUnit().equals("mm")) {
						gs.setColor(Color.YELLOW);
					}else {
						//pixel unit
						gs.setColor(Color.LIGHT_GRAY);
					}
					gs.drawLine(hx, y1, hx, y2);
					gs.drawLine(hx, y1, hx + 12, y1);
					gs.drawLine(hx, y2, hx + 12, y2);
					double viewScaleHeightMinorTick = (double)(viewScaleHeight / 10d);
					/*
					 * TODO draw unit [mm] string on top.
					 */
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 1)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 1)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 2)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 2)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 3)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 3)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 4)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 4)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 5)), hx + 12,
							(int) (y1 + (viewScaleHeightMinorTick * 5)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 6)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 6)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 7)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 7)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 8)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 8)));
					gs.drawLine(hx, (int) (y1 + (viewScaleHeightMinorTick * 9)), hx + 6,
							(int) (y1 + (viewScaleHeightMinorTick * 9)));
					/*
					 * scale width (show under of slide)
					 */
					int viewScaleWidth = (int) (100 / currentViewingPixelSizeX);
					int wx1 = (getWidth() - viewScaleWidth) / 2;
					int wy = getHeight() - 20;
					int wx2 = wx1 + viewScaleWidth;
					gs.drawLine(wx1, wy, wx2, wy);
					gs.drawLine(wx1, wy, wx1, wy - 12);
					gs.drawLine(wx2, wy, wx2, wy - 12);
					double viewScaleWidthMinorTick = (double)(viewScaleWidth / 10d);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 1)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 1)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 2)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 2)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 3)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 3)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 4)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 4)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 5)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 5)),
							wy - 12);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 6)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 6)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 7)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 7)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 8)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 8)),
							wy - 6);
					gs.drawLine((int) (wx1 + (viewScaleWidthMinorTick * 9)), wy, (int) (wx1 + (viewScaleWidthMinorTick * 9)),
							wy - 6);
				}
			}
		}
	}
	
	/*
	 * slide XY, prap basis.
	 */
	protected RoiObj activateAndGetRoiAt(int screenX, int screenY) {

		if (currentRoi != null) {
			previousRoi = currentRoi;
			// polygonroi
			int type = currentRoi.getType();
			if ((type == RoiObj.POLYGON || type == RoiObj.POLYLINE || type == RoiObj.ANGLE || type == RoiObj.LINE)
					&& currentRoi.getState() == RoiObj.CONSTRUCTING) {
				return currentRoi;
			}
		}

		int ix = sg.onImageX(screenX);
		int iy = sg.onImageY(screenY);
		ArrayList<RoiObj> rois = sg.getRois();
		int handle = -1;
		boolean found = false;
		if (rois != null && rois.size() > 0) {
			// reset activate
			for (RoiObj roi : rois) {
				roi.setActiveOverlayRoi(false);
			}
			for (RoiObj roi : rois) {
				handle = roi.isHandle(screenX, screenY, sg);
				if (handle >= 0) {
					roi.setActiveOverlayRoi(true);
					roi.showRoiPopupOnCanvas();
					currentRoi = roi;
					found = true;
					break;
				} else if (roi.contains(ix, iy)) {
					if (roi instanceof ShapeRoi) {
						System.out.println("this is shape roi !!");

					}
					roi.setActiveOverlayRoi(true);
					roi.showRoiPopupOnCanvas();
					currentRoi = roi;
					found = true;
					break;
				}
			}
			if (handle >= 0) {
				sg.setCursor(new Cursor(Cursor.HAND_CURSOR));
			} else if (found && currentRoi.contains(ix, iy)) {
				sg.setCursor(new Cursor(Cursor.MOVE_CURSOR));
			} else {
				sg.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
				currentRoi = null;
			}
			/*
			 * after search current roi
			 */
			if (found) {
				if (currentRoi != previousRoi && previousRoi != null) {
					previousRoi.setVisibleRoiPopup(false);
				}
			} else {
//					if(previousRoi != null) {
//						previousRoi.setVisibleRoiPopup(false);
//					}
			}
		}
		return currentRoi;
	}
	
	/**
	 * set reference line activate color and change cursor
	 * @param screenX
	 * @param screenY
	 * @return
	 */
	protected ReferenceLine referenceLineHereAt(int screenX, int screenY) {

		int ix = sg.onImageX(screenX);
		int iy = sg.onImageY(screenY);
		int handle = -1;
		boolean found = false;
		if (pp.getReferenceLine() != null) {
			ReferenceLine refLine = pp.getReferenceLine();
			refLine.setActiveOverlayRoi(false);// reset activate
			handle = refLine.isHandle(screenX, screenY, sg);
			if (handle >= 0) {
				refLine.setActiveOverlayRoi(true);
				found = true;
			} else if (refLine.contains(ix, iy)) {
				refLine.setActiveOverlayRoi(true);
				found = true;
			}
			if (handle >= 0) {
				sg.setCursor(new Cursor(Cursor.HAND_CURSOR));
			} else if (found && refLine.contains(ix, iy)) {
				sg.setCursor(new Cursor(Cursor.MOVE_CURSOR));
			} else {
				sg.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
			}
			return refLine;
		}
		return null;
	}
	
	public void mousePressed(MouseEvent e) {
		int toolID = pp.getViewer2DToolType();
		int sx = e.getX();
		int sy = e.getY();
		sg.lastX = sx; 
		sg.lastY = sy;
//		int ox = sg.onImageX(sx);
//		int oy = sg.onImageY(sy);
		long mousePressedTime = System.currentTimeMillis();
		switch (toolID) {
//		case Viewer2DToolBar.MAGNIFIER:
//			if (IJ.shiftKeyDown())
//				zoomToSelection(ox, oy);
//			else if ((flags & (Event.ALT_MASK|Event.META_MASK|Event.CTRL_MASK))!=0) {
//				zoomOut(x, y);
//				if (getMagnification()<1.0)
//					imp.repaintWindow();
//			} else {
// 				zoomIn(x, y);
//				if (getMagnification()<=1.0)
//					imp.repaintWindow();
//			}
//			break;
//		case Toolbar.HAND:
//			setupScroll(ox, oy);
//			break;
//		case Toolbar.DROPPER:
//			setDrawingColor(ox, oy, IJ.altKeyDown());
//			break;
//		case Toolbar.WAND:
//			double tolerance = WandToolOptions.getTolerance();
//			Roi roi = imp.getRoi();
//			if (roi!=null && (tolerance==0.0||imp.isThreshold()) && roi.contains(ox, oy)) {
//				Rectangle r = roi.getBounds();
//				if (r.width==imageWidth && r.height==imageHeight)
//					imp.deleteRoi();
//				else if (!e.isAltDown()) {
//					handleRoiMouseDown(e);
//					return;
//				}
//			}
//			if (roi!=null) {
//				int handle = roi.isHandle(x, y);
//				if (handle>=0) {
//					roi.mouseDownInHandle(handle, x, y);
//					return;
//				}
//			}
//			setRoiModState(e, roi, -1);
//			String mode = WandToolOptions.getMode();
//			if (Prefs.smoothWand)
//				mode = mode + " smooth";
//			int npoints = IJ.doWand(ox, oy, tolerance, mode);
//			if (Recorder.record && npoints>0) {
//				if (Recorder.scriptMode())
//					Recorder.recordCall("IJ.doWand(imp, "+ox+", "+oy+", "+tolerance+", \""+mode+"\");");
//				else {
//					if (tolerance==0.0 && mode.equals("Legacy"))
//						Recorder.record("doWand", ox, oy);
//					else
//						Recorder.recordString("doWand("+ox+", "+oy+", "+tolerance+", \""+mode+"\");\n");
//				}
//			}
//			break;
		case Viewer2DToolBar.Brush:
			handleRoiBrushMouseDown(e);
			break;
		default:  //rois
			handleRoiMouseDown(e);
		}
	}

	public void handleRoiMouseDown(MouseEvent e) {

		int sx = e.getX();//slide screen x (praparat view coordinates)
		int sy = e.getY();//slide screen y (praparat view coordinates)
		int roiType = pp.getCurrentViewerToolType();
		if(referenceLineHereAt(sx,sy)!=null) {
			ReferenceLine refLine = referenceLineHereAt(sx,sy);
			int handle = refLine.isHandle(sx, sy, sg);
			refLine.setRoiModState(e, handle);
			refLine.handleMouseDown(e, sg);
			return;
		}
		if(currentRoi != null && (currentRoi instanceof PolygonRoi) && roiType==RoiObj.POLYGON && (currentRoi.getState() == RoiObj.CONSTRUCTING)) {
			return;
		}
		//get currentRoi
		activateAndGetRoiAt(sx, sy);
		
		if (currentRoi != null){
			if(sg.isHereRoiPopup(e)) {
				//NORTICE; if mouse on RoiPopup, slideXY is change to RoiPopUp origin...
				RoiPopupDialog dialog = sg.getRoiPopupAt(e);
				dialog.handleMousePressed(e);
				return;
			}
			int handle = currentRoi.isHandle(sx, sy, sg);
			currentRoi.setRoiModState(e, handle);
			currentRoi.handleMouseDown(e, sg);
		}else {
			
			if(sg.isHereRoiPopup(e)) {
				//NORTICE; if mouse on RoiPopup, slideXY is change to RoiPopUp origin...
				RoiPopupDialog dialog = sg.getRoiPopupAt(e);
				dialog.handleMousePressed(e);
				return;
			}
			currentRoi = createNewRoi(sx, sy,roiType);
		}
	}
	
	public void handleRoiBrushMouseDown(MouseEvent e) {
		brushTool = new RoiBrush(sg,e);
//		brushTool.createBrush(e);
	}
	
	public boolean handleRoiMouseDragged(MouseEvent e, SlideGlass sg) {
		int dragSX = e.getX();//x on slideglass
		int dragSY = e.getY();
		int flags = e.getModifiers();//TODO, needed.
		/*
		 * drag roi or popup
		 */
		int roiType = pp.getCurrentViewerToolType();//from viewer2d
		if(roiType == Viewer2DToolBar.Brush) {
			if(brushTool != null) {
				brushTool.createBrush(e);
			}
			sg.lastDraggedX = dragSX;
			sg.lastDraggedY = dragSY;
			return true;
		}
		boolean dragging = false;
		if (flags==0 && IJ.isMacOSX()) {
			// workaround for Mac OS 9 bug
			flags = InputEvent.BUTTON1_MASK;
		}
		//is reference line?
		if(referenceLineHereAt(dragSX, dragSY) != null) {
			pp.getReferenceLine().handleMouseDrag(dragSX, dragSY, flags);
			sg.lastDraggedX = dragSX;
			sg.lastDraggedY = dragSY;
			return true;
		}
		//is dialog ?
		if(sg.isHereRoiPopup(e)) {
//			System.out.println("RoiDialog DRAGGING!!!");
			RoiPopupDialog dialog = sg.getRoiPopupAt(e);
			if(dialog != null) {
				dialog.handleMouseDragged(e);
				dragging = true;
			}
		//is roi ?
		}else {
			if (currentRoi != null) {
				currentRoi.handleMouseDrag(dragSX, dragSY, flags);
//			if(currentRoi instanceof OvalRoi) {
//				OvalRoi oval = (OvalRoi)currentRoi;
//				oval.handleMouseDrag(dragSX, dragSY, flags,sg);
//			}else {
//				
//			}
				dragging = true;
			}
		}
		
		sg.lastDraggedX = dragSX;
		sg.lastDraggedY = dragSY;
		return dragging;
	}
	
	public void mouseMoved(MouseEvent e) {
		if(pp.getReferenceLine() != null) {
			ReferenceLine refLine = referenceLineHereAt(e.getX(), e.getY());
			if(refLine != null) {
				return;
			}
		}
		//update currentRoi
		activateAndGetRoiAt(e.getX(), e.getY());
		int type = currentRoi != null ? currentRoi.getType() : -1;
		if (type>0 && (type==RoiObj.POLYGON||type==RoiObj.POLYLINE||type==RoiObj.ANGLE||type==RoiObj.LINE) 
		&& currentRoi.getState()==RoiObj.CONSTRUCTING) {
			currentRoi.mouseMoved(e);
		}
	}
	
	public void mouseReleased(MouseEvent emr) {
		if(currentRoi != null) {
			currentRoi.handleMouseUp(emr.getX(), emr.getY());
			previousRoi = currentRoi;//clone()?
		}
		//brush
		if(pp.getCurrentViewerToolType()==Viewer2DToolBar.Brush) {
			if(brushTool != null) {
				brushTool.brushingEnd();
			}
		}
			
//		int ox = offScreenX(e.getX());
//		int oy = offScreenY(e.getY());
//		if ((overlay!=null||showAllOverlay!=null) && ox==mousePressedX && oy==mousePressedY) {
//			boolean cmdDown = IJ.isMacOSX() && e.isMetaDown();
//			Roi roi = imp.getRoi();
//			if (roi!=null && roi.getBounds().width==0)
//				roi=null;
//			if ((e.isAltDown()||e.isControlDown()||cmdDown) && roi==null) {
//				if (activateOverlayRoi(ox, oy))
//					return;
//			} else if ((System.currentTimeMillis()-mousePressedTime)>250L && !drawingTool()) {
//				if (activateOverlayRoi(ox,oy))
//					return;
//			}
//		}
//
//		PlugInTool tool = Toolbar.getPlugInTool();
//		if (tool!=null) {
//			tool.mouseReleased(imp, e);
//			if (e.isConsumed()) return;
//		}
//		flags = e.getModifiers();
//		flags &= ~InputEvent.BUTTON1_MASK; // make sure button 1 bit is not set
//		flags &= ~InputEvent.BUTTON2_MASK; // make sure button 2 bit is not set
//		flags &= ~InputEvent.BUTTON3_MASK; // make sure button 3 bit is not set
//		Roi roi = imp.getRoi();
//		if (roi != null) {
//			Rectangle r = roi.getBounds();
//			int type = roi.getType();
//			if ((r.width==0 || r.height==0)
//			&& !(type==Roi.POLYGON||type==Roi.POLYLINE||type==Roi.ANGLE||type==Roi.LINE)
//			&& !(roi instanceof TextRoi)
//			&& roi.getState()==roi.CONSTRUCTING
//			&& type!=roi.POINT)
//				imp.deleteRoi();
//			else
//				roi.handleMouseUp(e.getX(), e.getY());
		
	}

	/**
	 * Starts the process of creating a new selection, where sx and sy are the
	 * starting screen coordinates. The selection type is determined by which tool
	 * in the tool bar is active. The user interactively sets the selection size and
	 * shape.
	 */
	public RoiObj createNewRoi(int screenX, int screenY, int roiType) {
		
		int imageX = sg.onImageX(screenX);//org img X
		int imageY = sg.onImageY(screenY);//org img Y
		RoiObj roi = null;
		roiType = pp.getCurrentViewerToolType();
		switch (roiType) {
		case RoiObj.RECTANGLE://0
//			if (Toolbar.getRectToolType() == Toolbar.ROTATED_RECT_ROI)
//				roi = new RotatedRectRoi(sx, sy, this);
//			else
//				roi = new Roi(sx, sy, this, Toolbar.getRoundRectArcSize());
			roi = new RoiObj(imageX, imageY, 1, 1, 0, sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case RoiObj.OVAL://1
//			if (Toolbar.getOvalToolType() == Toolbar.ELLIPSE_ROI)
//				roi = new EllipseRoi(sx, sy, this);
//			else
			roi = new OvalRoi(imageX, imageY, 1,1,sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case RoiObj.POLYGON:
			roi = new PolygonRoi(imageX, imageY,roiType,sg);
			roi.setState(RoiObj.CONSTRUCTING);//fail safe
			break;
//		case Toolbar.POLYLINE:
		case RoiObj.ANGLE:
			roi = new PolygonRoi(imageX, imageY,roiType,sg);
			roi.setState(RoiObj.CONSTRUCTING);//fail safe
			break;
//		case Toolbar.FREEROI:
//		case Toolbar.FREELINE:
//			roi = new FreehandRoi(sx, sy, this);
//			break;
		case RoiObj.LINE://5
//			if ("arrow".equals(Toolbar.getToolName()))
//				roi = new Arrow(sx, sy, this);
//			else
//				roi = new Line(sx, sy, this);
			System.out.println("create new roi:line, s:"+screenX+" "+screenY+" i:"+imageX+" "+imageY);
			roi = new Line(imageX, imageY, imageX+1, imageY+1, sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case RoiObj.ARROW:
//			if ("arrow".equals(Toolbar.getToolName()))
//				roi = new Arrow(sx, sy, this);
//			else
//				roi = new Line(sx, sy, this);
//			System.out.println("create new roi:Arrow, s:"+screenX+" "+screenY+" i:"+imageX+" "+imageY);
			roi = new Arrow(imageX, imageY, imageX+1, imageY+1, sg);
			roi.setState(RoiObj.CONSTRUCTING);
			break;
		case RoiObj.TEXT:
			roi = new TextRoi(imageX, imageY, null, sg);
			roi.setState(RoiObj.CONSTRUCTING);
//			((TextRoi) roi).setPreviousRoi(previousRoi);
			break;
		case RoiObj.POINT:
			roi = new PointRoi(imageX, imageY, sg);
//			if (Prefs.pointAddToOverlay) {
//				int measurements = Analyzer.getMeasurements();
//				if (!(Prefs.pointAutoMeasure && (measurements & Measurements.ADD_TO_OVERLAY) != 0))
//					IJ.run(this, "Add Selection...", "");
//				Overlay overlay2 = getOverlay();
//				if (overlay2 != null)
//					overlay2.drawLabels(!Prefs.noPointLabels);
//				Prefs.pointAddToManager = false;
//			}
//			if (Prefs.pointAutoMeasure || (Prefs.pointAutoNextSlice && !Prefs.pointAddToManager))
//				IJ.run(this, "Measure", "");
//			if (Prefs.pointAddToManager) {
//				IJ.run(this, "Add to Manager ", "");
//				ImageCanvas ic = getCanvas();
//				if (ic != null) {
//					RoiManager rm = RoiManager.getInstance();
//					if (rm != null) {
//						if (Prefs.noPointLabels)
//							rm.runCommand("show all without labels");
//						else
//							rm.runCommand("show all with labels");
//					}
//				}
//			}
//			if (Prefs.pointAutoNextSlice && getStackSize() > 1) {
//				IJ.run(this, "Next Slice [>]", "");
//				deleteRoi();
//			}
//			break;
//		default:
//			if(type == ) {
//				
//			}
		}
		if (roi != null) {
			sg.addRoi(roi);
			repaint();
		}
		return roi;
	}
	
	public RoiObj getCurrentRoi() {
		return currentRoi;
	}
	
	public RoiObj getPreviousRoi() {
		return previousRoi;
	}
	
	protected void setCurrentRoi2NULL() {
		currentRoi = null;
	}
	
	protected RoiObj getBrush() {
		return brush;
	}

	protected void setBrush(RoiObj brush) {
		this.brush = brush;
	}
	
	boolean rect = false;
	void createRect() {
		rect = true;
	}

	// http://alga.no.coocan.jp/paint.html
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		drawCanvas(g);
	}
	
	private void drawCanvas(Graphics g) {
		if (paintSizeCaliper) {
			showCaliper(g);
		}
		drawRoi(g);
		drawReferenceLine(g);
		drawLocalizerLine(g);
		//show cross line
		if(crossLine != null) {
			Graphics2D g2 = (Graphics2D)g;
			g2.setColor(crossLineColor);
			g2.setStroke(new BasicStroke(crossLineStrokeSize));
			g2.draw(crossLine);
		}
	}

	private void drawRoi(Graphics g) {

		ArrayList<RoiObj> rois = sg.getRois();
		for (int i = 0; i < rois.size(); i++) {
			RoiObj roiObj = rois.get(i);
			roiObj.draw(g, sg);
		}
		
		if(brush != null) {
			brush.draw(g, sg);
		}
//		if (pp.getReferenceLine() != null) {
//			ReferenceLine refLine = pp.getReferenceLine();
//			refLine.draw(g, sg);
//		}
	}
	
	private void drawReferenceLine(Graphics g) {
		if (sg == null || sg.getRois() == null || sg.getRois().size() < 1) {
			if (pp.getReferenceLine() != null) {
				ReferenceLine refLine = pp.getReferenceLine();
				refLine.draw(g, sg);
			}
		}
	}
	
	private void drawLocalizerLine(Graphics g) {
		if(localizerGeo != null) {
//			System.out.println(shapes.size());
			Point2D p0_leftlower = localizerGeo.get(0);
			Point2D p1_rightlower = localizerGeo.get(1);
			Point2D p2_rightupper = localizerGeo.get(2);
			Point2D p3_leftupper = localizerGeo.get(3);
//			System.out.println(p0_leftlower.getX()+" "+p0_leftlower.getY());
//			System.out.println(p1_rightlower.getX()+" "+p1_rightlower.getY());
//			System.out.println(p2_rightupper.getX()+" "+p2_rightupper.getY());
//			System.out.println(p3_leftupper.getX()+" "+p3_leftupper.getY());
			GeneralPath loca = new GeneralPath();
	        loca.moveTo(sg.screenXD(p3_leftupper.getX()), sg.screenYD(p3_leftupper.getY()));
	        loca.lineTo(sg.screenXD(p2_rightupper.getX()), sg.screenYD(p2_rightupper.getY()));
	        loca.lineTo(sg.screenXD(p1_rightlower.getX()), sg.screenYD(p1_rightlower.getY()));
	        loca.lineTo(sg.screenXD(p0_leftlower.getX()), sg.screenYD(p0_leftlower.getY()));
	        loca.lineTo(sg.screenXD(p3_leftupper.getX()), sg.screenYD(p3_leftupper.getY()));
			Graphics2D g2 = (Graphics2D)g;
			g2.setColor(localizerColor);
			g2.setStroke(new BasicStroke(localizerStrokeSize));
			g2.draw(loca);
		}
	}
}
