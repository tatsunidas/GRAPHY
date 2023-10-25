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

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JLayeredPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.LayerUI;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;

public class PraparatUI extends LayerUI<JLayeredPane> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1702232026867569134L;
	private Praparat pp;
	private Eyepiece prapManager;
	private int viewerToolType = Viewer2DToolBar.Windowing;

//	private final static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
//	private final static Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
//	private final static Cursor moveCursor = new Cursor(Cursor.MOVE_CURSOR);
//	private final static Cursor crosshairCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);

	private Set<Integer> pressedKeys = new HashSet<Integer>();

	public PraparatUI(Praparat pp) {
		this.pp = pp;
		// to sync series
		prapManager = pp.getEyepieceAsPraparatManager();
	}

	public void setViewer2DToolType(int toolType) {
		this.viewerToolType = toolType;
	}

	protected int getViewer2DToolType() {
		Viewer2DScreen viewer2d = Viewer2DScreen.getInstance();
		if (viewer2d != null) {
			setViewer2DToolType(viewer2d.getCurrentToolType());
		}
		return viewerToolType;
	}

	@Override
	public void paint(Graphics g, JComponent c) {
		// paint slide glass as-is
		super.paint(g, c);
	}

	@Override
	public void installUI(JComponent c) {
		super.installUI(c);
		JLayer<?> jlayer = (JLayer<?>) c;
		// enable mouse motion events for the layer's subcomponents
		jlayer.setLayerEventMask(
				AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK
						| AWTEvent.KEY_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
	}

	@Override
	public void uninstallUI(JComponent c) {
		super.uninstallUI(c);
		// reset the layer event mask
		JLayer<?> jlayer = (JLayer<?>) c;
		jlayer.setLayerEventMask(0);
	}

	// ************************************************************************************************
	/*
	 * KEY EVENT
	 */
	// ************************************************************************************************

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

		if (e.getID() == KeyEvent.KEY_PRESSED) {
			if (Utils.isDebug) {
				System.out.println("PraparatUI::KEY PRESSED !:" + e.getKeyCode());
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

			// reset slide
			if (shift && ctrl) {
				if (pressedKeys.contains(KeyEvent.VK_R)) {
					if(Utils.isDebug) System.out.println("reset pressed.");
					pp.resetView();
				}
			}

			// paging
			if (left || up && !right && !down && !shift && !ctrl) {
				if (!pp.isShowGridViewOn()) {
					if (prapManager != null) {
						ArrayList<Praparat> syncingPraps = prapManager.getSelectingPraparats();
						if (syncingPraps != null && syncingPraps.size() > 1) {
							for (Praparat prap : syncingPraps) {
								int pos = prap.getCurrentSlidePos();
								pos = pos - 1;
								if (pos < 0) {
									pos = prap.getNumberOfImages() - 1;
								}
								prap.setImagePositionUsingSlider(pos);// work with slider
							}
						} else {
							int pos = pp.getCurrentSlidePos();
							pos = pos - 1;
							if (pos < 0) {
								pos = pp.getNumberOfImages() - 1;
							}
							pp.setImagePositionUsingSlider(pos);// work with slider
						}
					} else {
						int pos = pp.getCurrentSlidePos();
						pos = pos - 1;
						if (pos < 0) {
							pos = pp.getNumberOfImages() - 1;
						}
						pp.setImagePositionUsingSlider(pos);// work with slider
					}
				}
			} else if (right || down && !left && !up && !shift && !ctrl) {
				if (!pp.isShowGridViewOn()) {
					if (prapManager != null) {
						ArrayList<Praparat> syncingPraps = prapManager.getSelectingPraparats();
						if (syncingPraps.size() > 1) {
							for (Praparat prap : syncingPraps) {
								int pos = prap.getCurrentSlidePos();
								pos = pos + 1;
								if (pos >= prap.getNumberOfImages()) {
									pos = 0;
								}
								prap.setImagePositionUsingSlider(pos);// work with slider
							}
						} else {
							int pos = pp.getCurrentSlidePos();
							pos = pos + 1;
							if (pos >= pp.getNumberOfImages()) {
								pos = 0;
							}
							pp.setImagePositionUsingSlider(pos);// work with slider
						}
					} else {
						int pos = pp.getCurrentSlidePos();
						pos = pos + 1;
						if (pos >= pp.getNumberOfImages()) {
							pos = 0;
						}
						pp.setImagePositionUsingSlider(pos);// work with slider
					}
				}
			}
		}

		/*
		 * KEY RELEASED
		 */
		if (e.getID() == KeyEvent.KEY_RELEASED) {
			int numOfKeys = pressedKeys.size();
			if (numOfKeys != 0) {
				int releasedKey = e.getKeyCode();
				Integer[] keys = pressedKeys.toArray(new Integer[numOfKeys]);
				pressedKeys.clear();
				for (Integer k : keys) {
					if (k != releasedKey) {
						pressedKeys.add(k);
					}
				}
			}
			if (Utils.isDebug) {
				System.out.println("PraparatUI::KEY RELEASED !:" + e.getKeyCode());
				System.out.println("Num Of Keys (before released): " + numOfKeys);
				System.out.println("Num Of Keys (after released): " + pressedKeys.size());
			}
		}
	}
	
	// ************************************************************************************************
	/*
	 * MOUSE EVENT
	 */
	// ************************************************************************************************

	/*
	 * Notice
	 * You should set detailed if-statement by SwingUtilities.
	 * For example, When mouse middle button wheeled, jvm catches it as double clicked,
	 * if you forgot SwingUtilities.isLeftMouseButton(e) in statement. 
	 */
	@Override
	protected void processMouseEvent(MouseEvent e, @SuppressWarnings("rawtypes")JLayer l) {

		//handle select event
		if (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown()) {
			pp.setSelectionState();
		}
		
		//handle double click event.
		if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && !e.isConsumed()) {
		     e.consume();
		     if(pp.getViewMode()==ViewMode.Thumbnail) {
		    	 MainScreen ms = WindowManager.getMainScreen();
			     if(ms != null && ms.isVisible()) {
			    	 /*
			    	  * show series on birds eye, and update prap's border.
			    	  */
			    	 ms.showImagesOnBirdsEye(pp);
			     }
		     }
		}
		
		if (e.getID() == MouseEvent.MOUSE_ENTERED) {
			pp.setFocusGained(true);
		}
		
		if (e.getID() == MouseEvent.MOUSE_EXITED) {
			//focus lost
			pp.setFocusGained(false);
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
		// paging
		if ((mod & InputEvent.CTRL_DOWN_MASK) == 0 && (mod & InputEvent.SHIFT_DOWN_MASK) == 0) {// NEED
			if (!pp.isShowGridViewOn()) {
				e.consume();
				ArrayList<Praparat> syncingPraps = null;
				if (prapManager != null) {
					syncingPraps = prapManager.getSelectingPraparats();
				}
				if (syncingPraps != null) {
					if (syncingPraps.size() > 1) {
						for (Praparat prap : syncingPraps) {
							int pos = 0;
							if (rotation < 0) {
								pos = prap.getCurrentSlidePos();
								pos = pos - 1;
								if (pos < 0) {
									pos = prap.getNumberOfImages() - 1;
								}
							} else {
								pos = prap.getCurrentSlidePos();
								pos = pos + 1;
								if (pos >= prap.getNumberOfImages()) {
									pos = 0;
								}
							}
							prap.setImagePositionUsingSlider(pos);// work with slider
						}
					} else {
						int pos = 0;
						if (rotation < 0) {
							pos = pp.getCurrentSlidePos();
							pos = pos - 1;
							if (pos < 0) {
								pos = pp.getNumberOfImages() - 1;
							}
						} else {
							pos = pp.getCurrentSlidePos();
							pos = pos + 1;
							if (pos >= pp.getNumberOfImages()) {
								pos = 0;
							}
						}
						pp.setImagePositionUsingSlider(pos);// work with slider
					}
				} else {
					int pos = 0;
					if (rotation < 0) {
						pos = pp.getCurrentSlidePos();
						pos = pos - 1;
						if (pos < 0) {
							pos = pp.getNumberOfImages() - 1;
						}
					} else {
						pos = pp.getCurrentSlidePos();
						pos = pos + 1;
						if (pos >= pp.getNumberOfImages()) {
							pos = 0;
						}
					}
					if (pp.getViewMode() != ViewMode.FilmGrid) {
						pp.setImagePositionUsingSlider(pos);// work with slider
					}
				}
			} else {// showGridViewOn
				try {
					Component t = e.getComponent();
					Component c = pp.getPraparatViewPane().getComponent(0);
					if (c instanceof JScrollPane && !c.equals(t)) {
						JScrollPane gridPane = (JScrollPane) c;
						MouseEvent me = SwingUtilities.convertMouseEvent(t, e, gridPane);
						gridPane.dispatchEvent(me);
						e.consume();/*consume after dispatch*/
					}
				} catch (ArrayIndexOutOfBoundsException aioobe) {
					// do nothing
				}
			}
		}
	}

}
