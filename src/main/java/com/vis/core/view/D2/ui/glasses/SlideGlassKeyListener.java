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

import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.vis.core.log.Log;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.TextRoi;

public class SlideGlassKeyListener implements KeyListener{
	
	final SlideGlass sg;
	final Praparat pp;
	final Eyepiece prapManager;
	int viewerToolType;
	
	private Set<Integer> pressedKeys = new HashSet<Integer>();
		
	public SlideGlassKeyListener(SlideGlass sg) {
		this.sg = sg;
		this.pp = sg.getPraparat();
		this.prapManager = pp.getEyepiece();
	}

	@Override
	public void keyTyped(KeyEvent e) {
		RoiObj roi = sg.getActiveRoi();
		//fail safe
		if(roi instanceof TextRoi) {
			//DO NOTHING, to avoid TextArea input conflict
		}else {
			//do something.
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		Log.logger.fine("SlideGlassKey::KEY PRESSED !:" + e.getKeyCode());
		viewerToolType = pp.getViewer2DToolType();
		CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);

		int k = e.getKeyCode();
		System.out.println(k);

		// add first.
		pressedKeys.add(k);

		// reset slide
		if (e.isControlDown() && e.isShiftDown()) {
			if (pressedKeys.contains(KeyEvent.VK_R)) {
				if (Utils.isDebug)
					System.out.println("reset pressed.");
				sg.setCursor(new Cursor(Cursor.WAIT_CURSOR));
				pp.resetView();
				sg.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
				return;
			}
		}

		/*
		 * On some keyboards, Fn+BackSpace will result in Delete; be aware of the
		 * limitations of the Fn key.
		 */
		if (pressedKeys.contains(KeyEvent.VK_DELETE) || pressedKeys.contains(KeyEvent.VK_BACK_SPACE)) {
			Log.logger.fine("Will delet Roi, " + "Delete pressed");
			cg.deleteRoi(sg.mouseX, sg.mouseY);
			return;
		}
		
		//roi move
		if (pressedKeys.contains(KeyEvent.VK_UP) || pressedKeys.contains(KeyEvent.VK_DOWN) || 
				pressedKeys.contains(KeyEvent.VK_LEFT) || pressedKeys.contains(KeyEvent.VK_RIGHT)) {
			boolean roiKeyEventDone = cg.keyPressed(e.getKeyCode(), e.getModifiersEx());
			if(roiKeyEventDone) {
				e.consume();
			}
		}

		
		if(e.isConsumed()) return;
		
		// paging
		if (pressedKeys.contains(KeyEvent.VK_LEFT) || pressedKeys.contains(KeyEvent.VK_UP)) {
			if (cg.activateRoiAt(sg.mouseX, sg.mouseY) == null) {
				if (!pp.isShowGridViewOn()) {
					if (prapManager != null) {/* Sync series */
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
			}
		} else if (pressedKeys.contains(KeyEvent.VK_RIGHT) || pressedKeys.contains(KeyEvent.VK_DOWN)) {
			if (cg.activateRoiAt(sg.mouseX, sg.mouseY) == null) {
				if (!pp.isShowGridViewOn()) {
					if (prapManager != null) {
						ArrayList<Praparat> syncingPraps = prapManager.getSelectingPraparats();
						if (syncingPraps.size() > 1) {
							for (Praparat prap : syncingPraps) {
								int pos = prap.getCurrentSlidePos();
								pos += 1;
								prap.setImagePositionUsingSlider(pos);// work with slider
							}
						} else {
							int pos = pp.getCurrentSlidePos();
							pos += 1;
							pp.setImagePositionUsingSlider(pos);// work with slider
						}
					} else {
						int pos = pp.getCurrentSlidePos();
						pos += 1;
						pp.setImagePositionUsingSlider(pos);// work with slider
					}
				}
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		/*
		 * why ? when input right arrow key(39), then press left key(37),
		 * multi key code still remain.
		 * Here, remove all pressedkeys.
		 */
//		int numOfKeys = pressedKeys.size();
//		System.out.println("pressed ! "+e.getKeyCode());
//		System.out.println(Arrays.toString(pressedKeys.toArray()));
//		System.out.println("Num of key: "+numOfKeys);
//		pressedKeys.remove(e.getKeyCode());
//		System.out.println("num of key after removed "+pressedKeys.size());
		//force remove
		pressedKeys.clear();
	}
}
