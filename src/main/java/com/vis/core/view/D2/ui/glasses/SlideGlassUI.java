package com.vis.core.view.D2.ui.glasses;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JLayeredPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.LayerUI;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.TextRoi;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;

@SuppressWarnings("serial")
public class SlideGlassUI extends LayerUI<JLayeredPane> implements LayerUISupport{
	
	private SlideGlass slide;
	private Praparat pp;
	private Eyepiece prapManager;
	private int viewerToolType = Viewer2DToolBar.Windowing;
	private Logger logger = Log.logger;
	
	/*
	 * currently pressed keys
	 * pressed keys are catch-up when key pressed, then clear element key by key when key released.
	 */
	private Set<Integer> pressedKeys = new HashSet<Integer>();
		
	public SlideGlassUI(SlideGlass slide) {
		this.slide = slide;
		this.pp = slide.getPraparat();
		//to sync series
		prapManager = pp.getEyepieceAsPraparatManager();
	}
	
	@Override
	public void installUI(JComponent c) {
		super.installUI(c);
		JLayer<?> jlayer = (JLayer<?>) c;
		// enable mouse motion events for the layer's subcomponents
		jlayer.setLayerEventMask(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
				| AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
	}

	@Override
	public void uninstallUI(JComponent c) {
		super.uninstallUI(c);
		// reset the layer event mask
		JLayer<?> jlayer = (JLayer<?>) c;
		jlayer.setLayerEventMask(0);
	}
	
	//************************************************************************************************
	/*
	 * KEY EVENT
	 */
	//************************************************************************************************
	
	@Override
	protected void processKeyEvent(KeyEvent e, @SuppressWarnings("rawtypes") JLayer l) {
		boolean left = false;
		boolean right = false;
		boolean up = false;
		boolean down = false;
		boolean shift = false;
		boolean ctrl = false;
		@SuppressWarnings("unused")
		boolean alt = false;
		@SuppressWarnings("unused")
		boolean enter = false;
		@SuppressWarnings("unused")
		boolean delete = false;
		@SuppressWarnings("unused")
		boolean backspace = false;
		
		if(e.getID() == KeyEvent.KEY_PRESSED) {
			if(Utils.isDebug) {
				System.out.println("KEY PRESSED !:"+e.getKeyCode());
			}
			pressedKeys.add(e.getKeyCode());
			if (pressedKeys.contains(KeyEvent.VK_LEFT))
				left = true;
			if (pressedKeys.contains(KeyEvent.VK_RIGHT))
				right = true;
			if (pressedKeys.contains(KeyEvent.VK_UP))
				up = true;
			if (pressedKeys.contains(KeyEvent.VK_DOWN))
				down = true;
			if (pressedKeys.contains(KeyEvent.VK_SHIFT))
				shift = true;
			if (pressedKeys.contains(KeyEvent.VK_CONTROL))
				ctrl = true;
			if (pressedKeys.contains(KeyEvent.VK_ALT))
				alt = true;
			if (pressedKeys.contains(KeyEvent.VK_ENTER))
				enter = true;
			if (pressedKeys.contains(KeyEvent.VK_DELETE))
				delete = true;
			if (pressedKeys.contains(KeyEvent.VK_BACK_SPACE))
				backspace = true;
		}
		
		/*
		 * KEY TYPED (Activated by both PRESSED and RELEASED)
		 */
		if(e.getID() == KeyEvent.KEY_TYPED) {
	        System.out.println("key typed");
	        /*
	         * typeの場合、e.getKeyCode()は使えない
	         * (int)e.getKeyChar()を使う
	         * https://stackoverflow.com/questions/15693904/java-keylistener-keytyped-backspace-esc-as-input
	         */
			//delete roi
	        if((int)e.getKeyChar()==KeyEvent.VK_DELETE) {
	        	System.out.println("delete roi");
	        	slide.deleteRoi((slide.mouseX), (slide.mouseY));
	        	return;
	        }
	        
	        //edit text roi
	        RoiObj currentRoi = slide.findCurrentRoi();
	        if(currentRoi != null && currentRoi.getType()==RoiObj.TEXT) {
	        	System.out.println("---edit text roi---");
	        	TextRoi tr = (TextRoi)currentRoi;
	        	/*
	        	 * back spaceもそのまま入力
	        	 */
	        	tr.addChar(e.getKeyChar());
	        }
		}
				
		/*
		 * KEY RELEASED
		 */
		if (e.getID() == KeyEvent.KEY_RELEASED) {
			int numOfKeys = pressedKeys.size();
			if(numOfKeys != 0) {
				int releasedKey = e.getKeyCode();
				Integer[] keys = pressedKeys.toArray(new Integer[numOfKeys]);
				pressedKeys.clear();
				for (Integer k : keys) {
					if (k != releasedKey) {
						pressedKeys.add(k);
					}
				}
			}
		}
	}
	
	// ************************************************************************************************
	/*
	 * COMPONENT FOCUS EVENT
	 */
	// ************************************************************************************************

	@Override
	protected void processFocusEvent(FocusEvent e, @SuppressWarnings("rawtypes") JLayer l) {
		if(e.getID() == FocusEvent.FOCUS_GAINED) {
			slide.setFocusGained(true);
		}else if(e.getID() == FocusEvent.FOCUS_LOST) {
			slide.setFocusGained(false);
		}
	}

	// ************************************************************************************************
	/*
	 * MOUSE EVENT
	 */
	// ************************************************************************************************
	
	@SuppressWarnings("rawtypes")
	@Override
	protected void processMouseEvent(MouseEvent e,  JLayer l) {
		/*
		 * MOUSE_PRESSED
		 */
		if(e.getID() == MouseEvent.MOUSE_PRESSED) {
			if(slide == null) {
				return;
			}
			// set start point for ww/wl, panning, roi
			if (SwingUtilities.isLeftMouseButton(e)  && !e.isShiftDown()) {
				logger.info("mouse pressed (x,y):"+e.getX()+" "+e.getY());
				
				viewerToolType = Viewer2DScreen.getInstance().getCurrentToolType();
				if(pp.getViewMode() == ViewMode.Thumbnail) {
					viewerToolType = Viewer2DToolBar.Windowing;
				}
				if(viewerToolType==Viewer2DToolBar.Brush) {
					slide.handleRoiMousePressed(e);
					return;
				}
				
				if(Viewer2DToolBar.isRoiTool(viewerToolType)) {
					slide.handleRoiMousePressed(e);
					return;
				}
				
				if(viewerToolType==Viewer2DToolBar.Windowing) {
					//WW/WL
					if(!pp.isProcessSeries()) {
						slide.lastX = e.getX();
						slide.lastY = e.getY();
						slide.lastOriginX = slide.imageSpecimen.originX;
						slide.lastOriginY = slide.imageSpecimen.originY;
						slide.lastMin = slide.getCurrentDisplayImagePlus().getDisplayRangeMin();
						slide.lastMax = slide.getCurrentDisplayImagePlus().getDisplayRangeMax();
//						logger.info("origin when mouse was pressed (x,y):"+slide.originX+" "+slide.originY);
					}else {
						synchronized (this) {
							HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
							for (Integer key : slides.keySet()) {
								SlideGlass sg = slides.get(key);
								sg.lastX = e.getX();
								sg.lastY = e.getY();
								sg.lastOriginX = sg.imageSpecimen.originX;
								sg.lastOriginY = sg.imageSpecimen.originY;
								sg.lastMin = sg.getCurrentDisplayImagePlus().getDisplayRangeMin();
								sg.lastMax = sg.getCurrentDisplayImagePlus().getDisplayRangeMax();
							}
						}
					}
				}//ww/wl end
//				return;//DO NOT return to execute following
			}//left btn down end
			
			// select current slideglass
			if (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown()) {
				slide.setSelectionState();
			}
			
			// zoom
			if (SwingUtilities.isMiddleMouseButton(e)) {
				/*
				 * USB Mouse issue. Issue that is fired pressed action continuity. To avoid this
				 * issue, DO NOT USE these USB Mouses
				 */
				if(pp.getViewMode() == ViewMode.Thumbnail) {
					return;
				}
				logger.info("zoom : middle mouse btn pressed!!");
				if(!pp.isProcessSeries()) {
					slide.lastX = e.getX();//for move position
					slide.lastY = e.getY();//for move position
					slide.lastDraggedX = e.getX();//for cappulate mag
					slide.lastDraggedY = e.getY();//for cappulate mag
					slide.lastOriginX = slide.imageSpecimen.originX;
					slide.lastOriginY = slide.imageSpecimen.originY;
				}else {
					HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for(Integer key:slides.keySet()) {
						SlideGlass sg = slides.get(key);
						sg.lastX = e.getX();
						sg.lastY = e.getY();
						sg.lastOriginX = sg.imageSpecimen.originX;
						sg.lastOriginY = sg.imageSpecimen.originY;
						sg.lastDraggedX = e.getX();
						sg.lastDraggedY = e.getY();
					}
				}
			}
		}
		
		if (e.getID() == MouseEvent.MOUSE_RELEASED) {
			//roi
			slide.handleRoiMouseUp(e);
			//release panning
			if(!pp.isProcessSeries()) {
				if(slide.panningInAction) {
					slide.releasePanning();
				}
			} else {
				// process series
				System.out.println("panning series released !! mouse released.");
				if(slide.panningInAction) {
					slide.releasePanning();
				}
				synchronized (this) {
					HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					for (Integer key : slides.keySet()) {
						SlideGlass sg = slides.get(key);
						if(sg.panningInAction) {
							sg.releasePanning();
						}
					}
				}
			}
		}
		
		if (e.getID() == MouseEvent.MOUSE_ENTERED) {
			slide.requestFocus();//IMPORTANT : key event listen
			slide.setFocusGained(true);
		}
		
		if (e.getID() == MouseEvent.MOUSE_EXITED) {
			//focus lost
			slide.setFocusGained(false);
		}
	}

	// ************************************************************************************************
	/*
	 * MOUSE MOTION EVENT
	 */
	// ************************************************************************************************
	@Override
	protected void processMouseMotionEvent(MouseEvent e, @SuppressWarnings("rawtypes") JLayer l) {
		if(slide == null) {
			return;
		}
		/*
		 * x and y location on slideglass.
		 * mouse motion XY origin is slideglass origin (no display image origin)
		 */
		int x = e.getX();
		int y = e.getY();
		slide.mouseX = x;
		slide.mouseY = y;
		
		/*
		 * MOVE EVENT
		 */
		if (e.getID() == MouseEvent.MOUSE_MOVED) {
			//show pixel info
			slide.updatePrapInfoLabel(x, y);
			//roi
			slide.handleRoiMouseMoved(e);
		}
		
		/*
		 * DRAG EVENT
		 */
		if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
			
			if(pp.isShowCrossLineMode()) {
				slide.drawCross(e);
				return;//attention
			}
			
			viewerToolType = LayerUISupport.getViewer2DToolType();
			if(pp.getViewMode() == ViewMode.Thumbnail) {
				viewerToolType = Viewer2DToolBar.Windowing;
			}
			
			// roi brush
			if(viewerToolType==Viewer2DToolBar.Brush) {
				if(slide.handleRoiMouseDragged(e)) {
					return;
				}
			}
			// roi
			if (Viewer2DToolBar.isRoiTool(viewerToolType)) {
				if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
					if (slide.handleRoiMouseDragged(e)) {
						return;
					}
				}
			}
			
			//reference line
			if(pp.getReferenceLine() != null) {
				if (slide.handleRoiMouseDragged(e)) {
					return;
				}
				return;//attention
			}
			
			/*
			 * WW/WL
			 */
			if(viewerToolType==Viewer2DToolBar.Windowing) {
				if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown()) {
					// WW/WL left button
					if (!pp.isProcessSeries()) {
						slide.adjustWindowFromMouseAction(x, y);
					} else {
						HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
						for (Integer key : slides.keySet()) {
							SlideGlass sg = slides.get(key);
							sg.adjustWindowFromMouseAction(x, y);
						}
					}
				}
			}

			// zoom
			if (SwingUtilities.isMiddleMouseButton(e)) {
				if(pp.getViewMode() == ViewMode.Thumbnail) {
					return;
				}
				/*
				 * only calcurate mag
				 */
				int currentDragY = e.getY();
				if(!pp.isProcessSeries()) {
					//lastDraggedYはEnter時に更新されている
					double diffY = slide.lastDraggedY - currentDragY;
					double change = 0.005 * diffY;//緩やかに拡大させるために小さく
					double currentMag = slide.getMagnification();
					double newMag = currentMag + change;
					logger.info("dragging to zoom : lastY " + slide.lastY + " NowDragging:" + currentDragY + " mag:"
							+ newMag + " diffY:" + diffY);
					slide.zoom(newMag);
					slide.lastDraggedX = e.getX();
					slide.lastDraggedY = currentDragY;
				}else {
					HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
					double newMag = -1;
					for(Integer key:slides.keySet()) {
						SlideGlass sg = slides.get(key);
						double diffY = sg.lastDraggedY - currentDragY;
						// get current mag
						double mag = sg.getMagnification();
						double magFactor = 0.005 * diffY;//緩やかに拡大させるために小さくする
						newMag = mag + magFactor;
						sg.zoom(newMag);
						sg.lastDraggedX = e.getX();
						sg.lastDraggedY = currentDragY;
					}
					System.out.println("zooming : mag:" + newMag);
				}
			}

			// panning
			/*
			 * panning中は、実寸サイズと表示サイズの比を考慮したscaled originで考える。
			 * pannnig後は、バックスケールする
			 */
			if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown()) {
				if(pp.getViewMode() == ViewMode.Thumbnail) {
					return;
				}
				slide.setCursor(new Cursor(Cursor.MOVE_CURSOR));
				double moveX = slide.lastX - e.getX();
				double moveY = slide.lastY - e.getY();
				if(!pp.isProcessSeries()) {
					slide.panning(moveX, moveY);
				} else {
					// process series
					synchronized (this) {
						HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
						for (Integer key : slides.keySet()) {
							SlideGlass sg = slides.get(key);
							sg.panning(moveX, moveY);
						}
					}
				}
			}
		}
	}

	
	// ************************************************************************************************
	/*
	 * MOUSE WHEEL EVENT
	 */
	// ************************************************************************************************

	@Override
	protected void processMouseWheelEvent(MouseWheelEvent e, @SuppressWarnings("rawtypes") JLayer l) {
		int rotation = e.getWheelRotation();
		int mod = e.getModifiersEx();
		//paging
		/*
		 * see, PraparatUI.
		 * paging task is needed work with series.
		 * For example, if slideglasses have LayerUI one by one, it's events were separated it-selves.
		 * As a result, pressedKeys or something event state were separated slide by slide.
		 * PraparatUI is able to handle series paging.
		 */

		//rorate
		if ((mod & InputEvent.CTRL_DOWN_MASK) != 0 && (mod & InputEvent.SHIFT_DOWN_MASK) == 0) {
			if(pp.getViewMode() == ViewMode.Thumbnail) {
				return;
			}
			if(Utils.isDebug) System.out.println("rotate! "+rotation);
			this.slide.setCursor(new Cursor(Cursor.WAIT_CURSOR));
			if(!pp.isProcessSeries()) {
				this.slide.rotate(rotation);
			}else {
				HashMap<Integer, SlideGlass> slides = pp.getAllSlides();
				for(Integer key:slides.keySet()) {
					SlideGlass sg = slides.get(key);
					sg.rotate(rotation);
				}
			}
		}
	}
}
