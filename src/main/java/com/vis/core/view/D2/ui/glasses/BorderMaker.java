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

import java.awt.Color;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;

import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;

/**
 * Praparat and SlideGlass border
 * @author tatsunidas
 *
 */
public class BorderMaker {
	
	//slideglass border color
	static Color focusColor = Color.WHITE;
	static Color selectionColor = Color.MAGENTA;
	
	//praparat border color
	static Color prapColor = new Color(0, 50, 240, 100);
	
	static final Color clearColor = new Color(0,0,0,255);
	static final int BORDER_SIZE = 4;
	
	static Border focusBorder = BorderFactory.createLineBorder(focusColor, BORDER_SIZE);
	static Border selectionBorder = BorderFactory.createLineBorder(selectionColor, BORDER_SIZE);
	static Border clearBorder = BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
	
	static Border make(Component con, boolean mouseEntered) {
		
		if(con == null) {
			return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
		}
		
		if(con instanceof SlideGlass) {
			SlideGlass sg = (SlideGlass)con;
			Praparat pp = sg.getPraparat();
			if( pp.isShowGridViewOn() || pp.getViewMode()==ViewMode.FilmGrid )  {
				if(!sg.isSelected() && mouseEntered) {
					return BorderFactory.createLineBorder(focusColor, BORDER_SIZE);
				}else if(sg.isSelected() && !mouseEntered) {
					return BorderFactory.createLineBorder(selectionColor, BORDER_SIZE);
				}else if(sg.isSelected() && mouseEntered) {
					Border focus = BorderFactory.createLineBorder(focusColor, BORDER_SIZE/2);
					Border select = BorderFactory.createLineBorder(selectionColor, BORDER_SIZE/2);
					return new CompoundBorder(focus, select);
				}else {
					return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
				}
			}
			if((pp.getViewMode()==ViewMode.SingleGrid || pp.getViewMode()==ViewMode.Normal || pp.getViewMode()==ViewMode.MPR) && !pp.isShowGridViewOn()) {
				if(sg.isSelected()) {
					return BorderFactory.createLineBorder(selectionColor, BORDER_SIZE);
				}else{
					return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
				}
			}
			//explicit code
			if(pp.getViewMode()==ViewMode.Thumbnail) {
				return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
			}
			return BorderFactory.createLineBorder(clearColor, BORDER_SIZE);
		}else if(con instanceof Praparat) {
			Praparat pp = (Praparat)con;
			Color bgColor = pp.getBackground();
			if(pp.mode==ViewMode.FilmGrid || pp.mode == ViewMode.SingleGrid) {
				return BorderFactory.createLineBorder(bgColor, BORDER_SIZE);
			}
			
			if (pp.mode == ViewMode.Thumbnail) {
				if (pp.isSelected()) {/* DO NOT USE forcusGained here. */
					return BorderFactory.createLineBorder(prapColor, BORDER_SIZE);
				} else {
					return BorderFactory.createLineBorder(bgColor, BORDER_SIZE);
				}
			}

			if(!pp.isShowing2DViewerOn()) {
				if (pp.isSelected()) {
					return BorderFactory.createLineBorder( prapColor, BORDER_SIZE);
				} else if (!pp.isSelected() && mouseEntered) {
					return BorderFactory.createLineBorder(bgColor, BORDER_SIZE);
				} else {
					return BorderFactory.createLineBorder(bgColor, BORDER_SIZE);
				}
			}else {
				if (pp.isSelected() && !mouseEntered) {
					return BorderFactory.createLineBorder( prapColor, BORDER_SIZE);
				} else if (!pp.isSelected() && mouseEntered) {
					return BorderFactory.createLineBorder(pp.getStudyColor(), BORDER_SIZE);
				} else if (pp.isSelected() && mouseEntered) {
					Border focusBorder = BorderFactory.createLineBorder(pp.getStudyColor(), BORDER_SIZE/2);
					Border selectionBorder = BorderFactory.createLineBorder(prapColor, BORDER_SIZE/2);
					return new CompoundBorder(selectionBorder, focusBorder);
				} else {
					return BorderFactory.createLineBorder(bgColor, BORDER_SIZE);
				}
			}
		}
		return null;
	}
}
