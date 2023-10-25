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
package com.vis.imageio;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.plaf.LayerUI;

public class TestLayerUI {

	public static void main(String[] args) {
		
		JPanel parent = new JPanel();
		parent.setLayout(new GridLayout(1, 2));
		JPanel p1 = new JPanel();
		p1.setBackground(Color.yellow);
		JPanel p2 = new JPanel();
		p2.setBackground(Color.blue);
		
		parent.add(p1);
		parent.add(p2);
		
		JLayer<JPanel> pl = new JLayer<JPanel>(parent, parentUI());
		JLayer<JPanel> l1 = new JLayer<JPanel>(p1, childUI());
		JLayer<JPanel> l2 = new JLayer<JPanel>(p2, childUI());
		
//		parent.add(l1);
//		parent.add(l2);
		
		JFrame f = new JFrame();
		f.add(pl);
		f.setSize(512,512);
		f.setVisible(true);
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	
	static LayerUI<JPanel> parentUI(){
		@SuppressWarnings("serial")
		LayerUI<JPanel> parentUI = new LayerUI<JPanel>() {
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
			
			@Override
			protected void processMouseEvent(MouseEvent e, @SuppressWarnings("rawtypes")JLayer l) {
				if (e.getID() == MouseEvent.MOUSE_ENTERED) {
					System.out.println("PARENT MOUSE ENTERED");
				}
			}
			
			@Override
			protected void processMouseMotionEvent(MouseEvent e, @SuppressWarnings("rawtypes") JLayer l) {
				int x = e.getX();
				int y = e.getY();
				System.out.println("PARENT: MouseMove "+"x:"+x+" y:"+y);
			}
			
		};
		return parentUI;
	}
	
	static LayerUI<JPanel> childUI(){
		@SuppressWarnings("serial")
		LayerUI<JPanel> chiUI = new LayerUI<JPanel>() {
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
			
			@Override
			protected void processMouseEvent(MouseEvent e, @SuppressWarnings("rawtypes")JLayer l) {
				if (e.getID() == MouseEvent.MOUSE_ENTERED) {
					System.out.println("CHILD MOUSE ENTERED");
				}
			}
			
			@Override
			protected void processMouseMotionEvent(MouseEvent e, @SuppressWarnings("rawtypes") JLayer l) {
				int x = e.getX();
				int y = e.getY();
				System.out.println("CHILD: MouseMove "+"x:"+x+" y:"+y);
			}
		};
		return chiUI;
	}
	

}
