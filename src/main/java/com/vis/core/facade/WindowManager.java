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
package com.vis.core.facade;

import java.awt.Font;
import java.awt.Window;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.swing.JFrame;

import com.vis.configuration.ConfigInfo;
import com.vis.core.ui.FontSettings;
import com.vis.core.ui.LookAndFeels;
import com.vis.core.ui.main.MainScreen;

public class WindowManager {

	private static HashMap<String, Window> wins = new HashMap<>();
	
	public static void addWindow(java.awt.Window frame) {
		if(wins.isEmpty()) {
			wins.put(frame.getName(), frame);
		}else {
			if(!wins.containsValue(frame)) {
				wins.put(frame.getName(),frame);
			}
		}
	}
	
	public static boolean removeWindow(java.awt.Window frame) {
		if(wins.isEmpty()) {
			return true;
		}else {
			if(wins.containsValue(frame)) {
				return wins.remove(frame.getName(), frame);
			}
		}
		return false;
	}
	
	public static Window getWindow(String name) {
		Window w = wins.get(name);
		if(w != null && !w.isDisplayable()/*already disposed.*/) {
			removeWindow(w);
			w = null;
		}
		return w;
	}
	
	public static Window getCurrentWindow() {
		if(wins.isEmpty()) {
			return null;
		}
		for(Entry<String, Window> l : wins.entrySet()) {
			if(l.getValue().isFocused()) {
				return l.getValue();
			}
		}
		return null;
	}
	
	public static MainScreen getMainScreen() {
		Window win = getWindow(ConfigInfo.MainScreen.toString());
		if(win != null) {
			return (MainScreen)win;
		}
		return null;
	}
	
	public static void toFront(String name) {
		if (name == null)	return;
		Window window = wins.get(name);
		toFront(window);
	}
	
	public static void toFront(Window window) {
		if (window == null)
			return;
		if (window instanceof JFrame && ((JFrame) window).getState() == JFrame.ICONIFIED) {
			((JFrame) window).setState(JFrame.NORMAL);
		}
		window.toFront();
	}
	
	public static void updateFont(Font fon) {
		for(String k : wins.keySet()) {
			Window w = wins.get(k);
			FontSettings.changeFont(w, fon);
		}
		FontSettings.saveFont(fon);
	}
	
	public static void updateLookAndFeels(LookAndFeels lafObj) {
		for(String k : wins.keySet()) {
			Window w = wins.get(k);
			lafObj.updateLookAndFeels(w);
		}
	}
	
}
