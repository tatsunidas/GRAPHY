///* ***** BEGIN LICENSE BLOCK *****
// * Version: MPL 1.1/GPL 2.0/LGPL 2.1
// *
// * The contents of this file are subject to the Mozilla Public License Version
// * 1.1 (the "License"); you may not use this file except in compliance with
// * the License. You may obtain a copy of the License at
// * http://www.mozilla.org/MPL/
// *
// * Software distributed under the License is distributed on an "AS IS" basis,
// * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
// * for the specific language governing rights and limitations under the
// * License.
// *
// * The Original Code is part of graphy, hosted at https://github.com/graphy.
// *
// * The Initial Developer of the Original Code is
// * Visionary Imaging Services, Inc.
// * Portions created by the Initial Developer are Copyright (C) 2015
// * the Initial Developer. All Rights Reserved.
// *
// * Contributor(s):
// * See @authors listed below
// *
// * Alternatively, the contents of this file may be used under the terms of
// * either the GNU General Public License Version 2 or later (the "GPL"), or
// * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
// * in which case the provisions of the GPL or the LGPL are applicable instead
// * of those above. If you wish to allow use of your version of this file only
// * under the terms of either the GPL or the LGPL, and not to allow others to
// * use your version of this file under the terms of the MPL, indicate your
// * decision by deleting the provisions above and replace them with the notice
// * and other provisions required by the GPL or the LGPL. If you do not delete
// * the provisions above, a recipient may use your version of this file under
// * the terms of any one of the MPL, the GPL or the LGPL.
// *
// * ***** END LICENSE BLOCK *****
// */
//package com.vis.utils;
//
//import java.awt.BorderLayout;
//import java.awt.GraphicsConfiguration;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//
//import javax.swing.JButton;
//import javax.swing.JFrame;
//import javax.swing.SwingUtilities;
//
//import org.jogamp.java3d.BoundingSphere;
//import org.jogamp.java3d.BranchGroup;
//import org.jogamp.java3d.Canvas3D;
//import org.jogamp.java3d.DirectionalLight;
//import org.jogamp.java3d.utils.geometry.ColorCube;
//import org.jogamp.java3d.utils.universe.SimpleUniverse;
//import org.jogamp.vecmath.Color3f;
//import org.jogamp.vecmath.Vector3f;
//
//import com.vis.core.view.D2.ui.glasses.Praparat;
//
//import ij.IJ;
//import ij.ImagePlus;
//
//public class Canvas3DProblem {
//
//	JFrame f;
//	
//	public static void main(String[] args) {
//		new Canvas3DProblem();
//	}
//	
//	public Canvas3DProblem() {
//		
//		String url = "https://imagej.net/ij/images/flybrain.zip";
//		ImagePlus image = IJ.openImage(url);
//		
//		f = new JFrame();
//		f.setTitle("Simple Java 3D Example");
//		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		/*
//		 * More heavy task needed for reproduction that canvas3d will not work.
//		 */
////		JPanel pp = new JPanel() {
////			protected void paintComponent(Graphics g) {
////				super.paintComponent(g);
////				// 描画処理
////				for (int i = 0; i < 10000; i++) {
////					g.drawString("This is a JPanel", i, i);
////				}
////			}
////		};
////		f.setSize(500,500);
//		
//		Praparat pp = new Praparat(Praparat.ViewMode.SingleGrid);
//		pp.prepareSlideGlassesUsingImagePlus(image);
//		f.add(pp);
//		f.pack();
//		
//		JButton btn = new JButton("Set Canvas");
//		btn.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				SwingUtilities.invokeLater(() -> {
//	            	showCanvas();
//	            	f.getContentPane().revalidate();
//	            	f.getContentPane().repaint();
//	            	f.repaint();
//	            });
//			}
//		});
//		
//		f.add(btn, BorderLayout.SOUTH);
//		f.setVisible(true);
//	}
//	
//	void showCanvas() {
//		System.setProperty("jogl.disable.openglcore", System.getProperty("jogl.disable.openglcore", "false"));
//		
//		JFrame f = new JFrame();
//		f.setSize(800, 600);
//		
//		// Canvas3Dの設定
//		GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();
//		Canvas3D canvas3D = new Canvas3D(config);
//
//		f.getContentPane().add(canvas3D, BorderLayout.CENTER);
//
//		// SimpleUniverseの設定
//		SimpleUniverse universe = new SimpleUniverse(canvas3D);
//
//		// 視点をバックする
//		universe.getViewingPlatform().setNominalViewingTransform();
//
//		// シーンを構築
//		// BranchGroup: シーンのルート
//		BranchGroup root = new BranchGroup();
//
//		// 立方体（ColorCube）をシーンに追加
//		ColorCube cube = new ColorCube(0.4); // サイズ0.4の立方体
//		root.addChild(cube);
//
//		// 光源の設定
//		Color3f lightColor = new Color3f(1.0f, 1.0f, 1.0f); // 白色光
//		BoundingSphere bounds = new BoundingSphere(); // 光源の範囲
//		Vector3f lightDir = new Vector3f(4.0f, -7.0f, -12.0f); // 光の方向
//		DirectionalLight light = new DirectionalLight(lightColor, lightDir);
//		light.setInfluencingBounds(bounds);
//		root.addChild(light);
//
//		// シーンをユニバースに追加
//		universe.addBranchGraph(root);
//		canvas3D.getView().repaint();
//		
//		f.setVisible(true);
//	}
//
//}
