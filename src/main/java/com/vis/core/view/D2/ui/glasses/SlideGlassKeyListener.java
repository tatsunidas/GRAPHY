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

import com.vis.core.util.Utils;

public class SlideGlassKeyListener implements KeyListener{
	
	final SlideGlass sg;
	final Praparat pp;
	final Eyepiece prapManager;
	
	private Set<Integer> pressedKeys = new HashSet<Integer>();
	
	boolean left = false;
	boolean right = false;
	boolean up = false;
	boolean down = false;
	boolean shift = false;
	boolean ctrl = false;
	@SuppressWarnings("unused")
	boolean alt = false;
	
	public SlideGlassKeyListener(SlideGlass sg) {
		this.sg = sg;
		this.pp = sg.getPraparat();
		this.prapManager = pp.getEyepieceAsPraparatManager();
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getID() == KeyEvent.KEY_PRESSED) {
			if (Utils.isDebug) {
				System.out.println("PraparatUI::KEY PRESSED !:" + e.getKeyCode());
			}
			
			//add first.
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

			// reset slide
			if (shift && ctrl) {
				if (pressedKeys.contains(KeyEvent.VK_R)) {
					if(Utils.isDebug) System.out.println("reset pressed.");
					sg.setCursor(new Cursor(Cursor.WAIT_CURSOR));
					pp.resetView();
					sg.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
					return;
				}
			}

			// paging
			if (left || up && !right && !down && !shift && !ctrl) {
				if (!pp.isShowGridViewOn()) {
					if (prapManager != null) {/*Sync series*/
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
	}

}
