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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.Border;

import com.vis.core.log.Log;
import com.vis.core.util.ByteUtils;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;

import ij.ImagePlus;
import ij.plugin.FolderOpener;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class TestPraparat {

	public static void main(String[] args) {
		
		ConsoleHandler consoleHandler = new ConsoleHandler();
        if (Utils.isDebug) {
            consoleHandler.setLevel(Level.FINE);
            Log.logger.setLevel(Level.FINE);
        } else {
            consoleHandler.setLevel(Level.INFO);
            Log.logger.setLevel(Level.INFO);
        }
       Log.logger.addHandler(consoleHandler);
		show();
//		showGrid();
//		showThumbnail();
//		borderTest();
	}
	
	static javax.swing.JFrame loadFrame(Praparat pp) {
		javax.swing.JFrame frame = new javax.swing.JFrame("PraparatTest");
		frame.addComponentListener(new ComponentAdapter() {
			@Override
            public void componentResized(ComponentEvent e) {
				if(frame.isVisible()){
					pp.repaint();
				}
            }
        });
		frame.add(pp);
		return frame;
	}
	
	static void unsined() {
		short ss = (short)-100;
		short us = (short)40000;
		System.out.println("to unsigned:"+((ss+(short)32765) & 0xffff));
		System.out.println("to unsigned:"+(us & 0xffff));
	}
	
	static void show() {
		ImagePlus imp = FolderOpener.open("/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151");
//		ImagePlus imp = new ImagePlus("/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/dicom_samples/JIRA_DICOM/MR_LEE_IR87a.dcm");
		Praparat pp = new Praparat(ViewMode.Normal);
		pp.prepareSlideGlassesUsingImagePlus(imp);
		pp.doSingleGridLayout();
		javax.swing.JFrame f = loadFrame(pp);
		f.setSize(300,300);
		f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		f.setVisible(true);
	}
	
	static void showGrid() {
		ImagePlus imp = FolderOpener.open("/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151");
//		ImagePlus imp = new ImagePlus("/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/dicom_samples/JIRA_DICOM/MR_LEE_IR87a.dcm");
		Praparat pp = new Praparat(ViewMode.FilmGrid);
		pp.prepareSlideGlassesUsingImagePlus(imp);
		pp.doFilmGridLayout(5);
		javax.swing.JFrame f = loadFrame(pp);
		f.setSize(300,300);
		f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		f.setVisible(true);
	}
	
	static void showThumbnail() {
		ImagePlus imp = FolderOpener.open("/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151");
		Praparat pp = new Praparat(ViewMode.Thumbnail);
		pp.prepareSlideGlassesUsingImagePlus(imp);
		pp.setTextVisible(false);
		pp.setAnnotationVisible(false);
		pp.doSingleGridLayout();
		
		javax.swing.JFrame f = new javax.swing.JFrame("PraparatTest");
		f.setSize(300,300);
		f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);		
		f.setVisible(true);
		
		JPanel base = new JPanel(null);
		f.add(base, BorderLayout.CENTER);
		base.add(pp);
	}
	
	static void showUsingPixelDecoder() {

		ArrayList<String> paths = new ArrayList<String>();
		
		//suidobashi test
//		paths.add("/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/dicom_samples/JIRA_DICOM/MR_LEE_IR87a.dcm");//Signed
//		paths.add("/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/dicom_samples/JIRA_DICOM/CT_LEE_IR87a.dcm");//Signed
		paths.add("/home/tatsunidas/graphy_test_images/dicom_samples/JIRA_DICOM/MG_CC_L_LEE_IR87a.dcm");//Unsigned
		
//		File[] lists = new File("/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151").listFiles();
//		for(int i=0; i<lists.length; i++) {
//			paths.add(lists[i].getAbsolutePath());
//		}
		
		Praparat pp = new Praparat(ViewMode.Normal);
		pp.prepareSlideGlassesFromDcmObj(paths);
		pp.doSingleGridLayout();
		pp.showFirstImage();
		javax.swing.JFrame f = loadFrame(pp);
		f.setSize(300,300);
		f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		f.setVisible(true);
	}
	
	/*
	 * ShortProceccer is designed for unsigned 16 bit images as default.
	 * If you handle images that have signed pixels, 
	 * first, pix += 32768,
	 * then, pix & 0xffff to convert unsigned.
	 */
	static void singnedImageTest() {
		// array conversion test
//		ImagePlus imp = FolderOpener.open("/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151");
		ImagePlus imp = new ImagePlus("/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/dicom_samples/JIRA_DICOM/MR_LEE_IR87a.dcm");
		short[] spix = (short[])((ShortProcessor)(imp.getProcessor())).getPixels();
		System.out.println("org isSigned16Bit: "+imp.getProcessor().isSigned16Bit());//true
		/*
		 * back to imp
		 */
		ImageProcessor ip = new ShortProcessor(imp.getWidth(), imp.getHeight(), spix, null);
		new ImagePlus("",ip).show();
		System.out.println("replica isSigned16Bit "+ip.isSigned16Bit());//false
		
		ij.plugin.DICOM d;//reference
		
		short[] shortArray = new short[spix.length];
		//short to byte
		byte[] bpix = ByteUtils.shortToBytes(spix);
		//back to short
		ByteUtils.bytesToShorts(bpix, shortArray, 0, spix.length, false);
		
		for(int i = 512*128; i<512*128+10; i++) {
			System.out.println("org "+spix[i]);
			System.out.println("inv "+shortArray[i]);
		}
		
		ImageProcessor ip2 = new ShortProcessor(imp.getWidth(), imp.getHeight(), shortArray, null);
		new ImagePlus("",ip2).show();
		System.out.println("replica2 isSigned16Bit "+ip2.isSigned16Bit());//false
	}
	
	static void borderTest() {
		JFrame frame = new JFrame("Border Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 200);
        
        JPanel panel = new JPanel();
        panel.setBackground(Color.CYAN);
        
        Border border = BorderFactory.createLineBorder(Color.BLACK, 10); // 幅10ピクセルの黒いボーダー
        panel.setBorder(border);
        
        frame.add(panel);
        frame.setVisible(true);
        
     // インセットの確認
        System.out.println("Frame insets: " + frame.getInsets());
        System.out.println("Panel size: " + panel.getSize());
	}

}
