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
package com.vis.core.view.D2.ui;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import com.vis.core.ui.dialog.SaveImage;
import com.vis.core.view.D2.ui.glasses.Praparat;

import ij.ImagePlus;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class SeriesWindow extends javax.swing.JFrame implements java.awt.event.WindowListener{
	
	//debug
	@SuppressWarnings("unused")
	public static void main(String[] args) {
		String dir = "/home/tatsunidas/graphy_sample_images/HASSAKU_DCM/T1 AX short";
		String dir2 = "/home/tatsunidas/graphy_sample_images/NIfTI-DICOM";
//		String img = "/home/tatsunidas/crop_test.tif";
//		ImagePlus test = new ImagePlus(img);
		
//		ij.gui.Roi roi = new ij.gui.Roi(50,50,70,60);
////		test.setRoi(roi);
//		test.getProcessor().setColor(java.awt.Color.WHITE);
////		test.getProcessor().setBackgroundColor(Color.WHITE);
//		test.getProcessor().fill(roi);
//		Object r = test.getRoi();
//		System.out.println(r);
//		test.show();
		Praparat prap = new Praparat(ij.plugin.FolderOpener.open(dir2), java.awt.Color.CYAN, Praparat.ViewMode.Normal);
//		Praparat prap = new Praparat(test, java.awt.Color.CYAN, Praparat.ViewMode.Normal);
		new SeriesWindow(prap);
		
	}
	
	Praparat prap;
	boolean save_closing = false;
	
	public SeriesWindow(Praparat prap) {
		super();
		addWindowListener(this);
		this.prap = prap;
		setTitle("Series Window");
		setMenu();
		add(prap, java.awt.BorderLayout.CENTER);
//		setMinimumSize(new Dimension(30,30));
		setSize(512,512);
		setPreferredSize(new Dimension(512, 512));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		/*
		 * here, must be run to show single image.
		 */
		prap.doSingleGridLayout();
	}

	private void setMenu() {
		JMenuBar menu = new JMenuBar();
		JMenu mnFile = new JMenu("File");
		JMenuItem mntmSaveNew = new JMenuItem("Save as ...");
		mntmSaveNew.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(prap == null) {
					return;
				}
				ImagePlus imp = prap.getImagePlus();
				if(imp != null && imp.getNSlices() > 0) {
					String title = "Images save to..."; 
					String defaultDir = System.getProperty("user.home");
					String defaultName = "SAVE_IMAGES";
					String extensionWithDot = ".tif";
					SaveImage.save(imp, title, defaultDir, defaultName, extensionWithDot);
				}
			}
		});
		mnFile.add(mntmSaveNew);
		menu.add(mnFile);
		setJMenuBar(menu);
	}

	@Override
	public void windowOpened(WindowEvent e) {}

	@Override
	public void windowClosing(WindowEvent e) {
		dispose();
	}

	@Override
	public void windowClosed(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowActivated(WindowEvent e) {}

	@Override
	public void windowDeactivated(WindowEvent e) {}

}
