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
package com.vis.core.view.D3.ui;

import java.awt.BorderLayout;
import java.awt.GraphicsConfiguration;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.jogamp.java3d.BoundingSphere;
import org.jogamp.java3d.BranchGroup;
import org.jogamp.java3d.Canvas3D;
import org.jogamp.java3d.DirectionalLight;
import org.jogamp.java3d.utils.geometry.ColorCube;
import org.jogamp.java3d.utils.universe.SimpleUniverse;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Vector3f;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.Platform;
import com.vis.core.view.D2.ui.Viewer2DScreen;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij3d.Image3DUniverse;
import ij3d.ImageCanvas3D;
import ij3d.ImageWindow3D;

/**
 * Must use OracleJDK8 or AdoptOpenJDK11.
 * 
 * Assume that run configration: "-Dj3d.allowNullGraphicsConfig" to avoid
 * following error. 
 * 
 * java.lang.NullPointerException: Cannot invoke
 * "com.jogamp.nativewindow.awt.AWTGr
 * aphicsConfiguration.getAWTGraphicsConfiguration()" because "awtConfig" is
 * null
 * 
 * Warning! If other Swing components are performing special drawing processing,
 * the Canvas3D graphics will not be able to start up. Please be careful when
 * trying to start up the 3D Viewer if you are running any components that
 * override paintComponent.
 * 
 * @author tatsunidas
 *
 */
public class Viewer3DFrame_IJ {

	public static final int ORTHO = ij3d.Content.ORTHO;
	public static final int SURFACE = ij3d.Content.SURFACE;
	public static final int VOLUME = ij3d.Content.VOLUME;

	ImagePlus org;
	int displayMode = ij3d.Content.ORTHO;

	ImageWindow3D win3d;
	Image3DUniverse univ;

	public static void main(String args[]) {
		//test case 1
		/*
		 * たくさん画像を開いているときに起動しようとするとスクリーンが描画されないことがある。
		 * 一旦、他のウィンドウを最小化すると表示される。
		 */
		String url = "https://imagej.net/ij/images/flybrain.zip";
		ImagePlus image = ij.IJ.openImage(url);
		Viewer3DFrame_IJ d3 = new Viewer3DFrame_IJ(image, VOLUME);
		d3.run();
		
		//test case 2
//		Viewer3DFrame_IJ d3 = new Viewer3DFrame_IJ(null, VOLUME);
//		d3.test3D();
	}

	public Viewer3DFrame_IJ(ImagePlus imp, Integer dispMode) {
		workaroundIntelGraphicsBug();
		org = imp;
		displayMode = dispMode;
	}

	/*
	 * https://imagej.net/plugins/3d-viewer/display-a-stack
	 */
	public void run() {
		final ImagePlus imp2;
		if (org.getType() != ImagePlus.GRAY8 && org.getType() != ImagePlus.COLOR_256
				&& org.getType() != ImagePlus.COLOR_RGB) {
			int slices = org.getNSlices();
			ImageStack stack = new ImageStack(org.getWidth(), org.getHeight(), slices);
			for (int i = 0; i < slices; i++) {
				org.setPosition(i + 1);
				ImageProcessor bp = org.getProcessor().convertToByte(true);
				stack.setProcessor(bp, (i + 1));
			}
			
			imp2 = new ImagePlus("dup", stack);
			imp2.setCalibration(org.getCalibration().copy());
		} else {
			imp2 = org;
		}
		
		/*
		 * 3D Viewer does not read same name imp.
		 */
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddSSS");
		Date now = new Date();
		String formattedDate = sdf.format(now);
		imp2.setTitle(imp2.getTitle()+"_"+formattedDate);
		
		Window win = WindowManager.getWindow(ConfigInfo.D3ViewerWindow.toString());
//		new Thread(new Runnable() {
//			@Override
//			public void run() {
				if (win == null) {
					MainScreen ms = WindowManager.getMainScreen();
					if (ms != null) {
						ms.ignoreRepaintBirdsEye(true);
					}
					Window viewer2DScreen = WindowManager.getWindow(ConfigInfo.D2ViewerWindow.toString());
					if (viewer2DScreen != null) {
						((Viewer2DScreen) viewer2DScreen).ignoreRepaintAllSlides(true);
					}
					univ = new Image3DUniverse();
					univ.show();
					win3d = (ImageWindow3D) univ.getWindow();
					win3d.setName(ConfigInfo.D3ViewerWindow.toString());
					WindowManager.addWindow(win3d);
					win3d.setTitle("ImageJ 3D Viewer GRAPHY built-in");
					// after show(); do setLocationRelativeTo.
					win3d.setLocationRelativeTo(null);
					ImageCanvas3D canvas3D = (ImageCanvas3D) univ.getCanvas();
					canvas3D.getView().setMinimumFrameCycleTime(20);

					WindowAdapter ada = new WindowAdapter() {
						@Override
						public void windowClosing(WindowEvent e) {
							WindowManager.removeWindow(win3d);
						}
					};
					win3d.addWindowListener(ada);
					if (ms != null) {
						ms.ignoreRepaintBirdsEye(false);
					}
					if (viewer2DScreen != null) {
						((Viewer2DScreen) viewer2DScreen).ignoreRepaintAllSlides(false);
					}
				} else {
					win3d = (ImageWindow3D) win;
					if (!win3d.isVisible()) {
						win3d.setVisible(true);
					}
					win3d.toFront();
					univ = (Image3DUniverse) win3d.getUniverse();
				}

				switch (displayMode) {
				case ORTHO:
					new Thread(new Runnable() {
						@Override
						public void run() {
							univ.addOrthoslice(imp2);
						}
					}).start();
					break;
				case VOLUME:
					new Thread(new Runnable() {
						@Override
						public void run() {
							univ.addVoltex(imp2);
						}
					}).start();
					break;
				case SURFACE:
					new Thread(new Runnable() {
						@Override
						public void run() {
							univ.addSurfacePlot(imp2);
						}
					}).start();
					break;
				default:
					new Thread(new Runnable() {
						@Override
						public void run() {
							univ.addVoltex(imp2);
						}
					}).start();
					break;
				}

				win3d.getContentPane().revalidate();
				win3d.getContentPane().repaint();
//			}
//		}).start();
	}
	
	public boolean isVisible() {
		if(win3d == null) {
			return false;
		}
		return win3d.isVisible() && univ.getContents().size() > 0;
	}

	public void test3D() {
		SwingUtilities.invokeLater(() -> {
			JFrame f = new JFrame();
			f.setTitle("Simple Java 3D Example");
			f.setSize(800, 600);
//			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

			// Canvas3D
			GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();
			Canvas3D canvas3D = new Canvas3D(config);

			f.getContentPane().add(canvas3D, BorderLayout.CENTER);

			// SimpleUniverse
			SimpleUniverse universe = new SimpleUniverse(canvas3D);
			universe.getViewer().getView().setMinimumFrameCycleTime(5);
			
			System.out.println("GraphicsConfiguration: " + canvas3D.getGraphicsConfiguration());
			System.out.println("SimpleUniverse state: " + universe.toString());
			
			universe.getViewingPlatform().setNominalViewingTransform();

			// Scene
			// BranchGroup: Root of scene
			BranchGroup root = new BranchGroup();

			// ColorCube
			ColorCube cube = new ColorCube(0.4);
			root.addChild(cube);

			// Lighting
			Color3f lightColor = new Color3f(1.0f, 1.0f, 1.0f); // white
			BoundingSphere bounds = new BoundingSphere(); // range

			Vector3f lightDir = new Vector3f(4.0f, -7.0f, -12.0f); // light direction
			DirectionalLight light = new DirectionalLight(lightColor, lightDir);
			light.setInfluencingBounds(bounds);
			root.addChild(light);

			root.compile();
			universe.addBranchGraph(root);

			f.setVisible(true);
			f.requestFocus();
			f.getContentPane().revalidate();
			f.getContentPane().repaint();
		});
	}

	/**
	 * https://github.com/morphonets/SNT/blob/ea139559ee8356d306eea83e38ab1a50bd32e3d2/src/main/java/sc/fiji/snt/viewer/Viewer3D.java#L430
	 */
	public static void workaroundIntelGraphicsBug() { // This should go away with jogl 2.40?
		/*
		 * In a fresh install of ubuntu 20.04 displaying a 3DViewer triggers a
		 * ```com.jogamp.opengl.GLException: Profile GL4bc is not available on
		 * X11GraphicsDevice (...)``` The workaround discussed here works:
		 * https://github.com/processing/processing/issues/5476. Since it has no
		 * (apparent) side effects, we'll use it here for all platforms
		 */
		if (Platform.getOS() == Platform.LINUX) {
			System.setProperty("jogl.disable.openglcore", System.getProperty("jogl.disable.openglcore", "false"));
		}
		// in JRE 7 from causing redraw problems (i.e. the Java 3D canvas is sometimes
		// drawn as a blank gray rectangle).
		/*
		 * Conversely, if it is drawn in black, it is not possible to tell whether
		 * Canvas3D is running correctly, so it is commented out.
		 */
//        System.setProperty("sun.awt.noerasebackground", "true"); 
	}
}