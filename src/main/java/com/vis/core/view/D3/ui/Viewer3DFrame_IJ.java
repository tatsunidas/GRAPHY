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

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

import org.scijava.java3d.Canvas3D;
import org.scijava.java3d.GraphicsConfigTemplate3D;
import org.scijava.java3d.utils.universe.SimpleUniverse;

import com.vis.core.util.Platform;

import ij.IJ;
import ij.ImagePlus;
import ij3d.Image3DUniverse;

public class Viewer3DFrame_IJ{
	
	public static final int ORTHO = ij3d.Content.ORTHO;
	public static final int SURFACE = ij3d.Content.SURFACE;
	public static final int VOLUME = ij3d.Content.VOLUME;
	
	ImagePlus org;
	int displayMode = ij3d.Content.ORTHO;
	
	Image3DUniverse univ;
	
	public static void main(String args[]) {
		String url = "https://imagej.net/ij/images/flybrain.zip";
		ImagePlus image = IJ.openImage(url);
		new Viewer3DFrame_IJ(image, VOLUME);
	}
	
	public Viewer3DFrame_IJ(ImagePlus imp, Integer dispMode) {
		workaroundIntelGraphicsBug();
		run(imp, dispMode);
	}
	
	public void test() {
		System.out.println("run test");
		// this makes an error java.lang.IllegalArgumentException: Canvas3D:
		// GraphicsConfiguration is not compatible with Canvas3D, but it works!?
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		GraphicsConfiguration gc = gd.getDefaultConfiguration();

		try {
			gc = SimpleUniverse.getPreferredConfiguration(); // this works
			gc.getDevice();
			// Canvas3D default graphics configuration
			GraphicsConfigTemplate3D template = new GraphicsConfigTemplate3D();
			GraphicsConfiguration defaultGcfg = GraphicsEnvironment.getLocalGraphicsEnvironment()
					.getDefaultScreenDevice().getBestConfiguration(template);
			gc = defaultGcfg; // this works as well
			
			/*
			 * see, run configration
			 * -Dj3d.allowNullGraphicsConfig
			 * 
			 * java.lang.NullPointerException: Cannot invoke "com.jogamp.nativewindow.awt.AWTGr
			 * aphicsConfiguration.getAWTGraphicsConfiguration()" because "awtConfig" is null
			 * 
			 * Must use OracleJDK8 or AdoptOpenJDK11
			 * 
			 * If you use old graphics, may catch following error.
			 * 
			 * org.scijava.java3d.IllegalRenderingStateException: Java 3D ERROR : OpenGL 1.2 or better is required (GL_VERSION=1.1)
			 * Intel：https://www.intel.co.jp/content/www/jp/ja/support/detect.html
			 * 
			 */
			@SuppressWarnings("unused")
			Canvas3D c3d = new Canvas3D(gc);
//			System.out.println(c3d.queryProperties().get("native.version"));//glVersion but failed
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("finish test");
	}

	/*
	 * https://imagej.net/plugins/3d-viewer/display-a-stack
	 */
	private void run(ImagePlus imp, Integer dispMode) {
        // Create a universe and show it
		univ = new Image3DUniverse();
		univ.show();
		/*
		 * after show(); do setLocationRelativeTo.
		 */
		univ.getWindow().setLocationRelativeTo(null);
		
		@SuppressWarnings("unused")
		ij3d.Content content = null;
		
		switch (dispMode) {
		case ORTHO:
			content = univ.addOrthoslice(imp);
			break;
		case VOLUME:
			content = univ.addVoltex(imp);
			break;
		case SURFACE:
			content = univ.addSurfacePlot(imp);
			break;
		default:
			content = univ.addVoltex(imp);
			break;
		}
		
		// Add the image as a volume rendering
//		ij3d.Content c = addVoltex(dup);

		// Display mode change
//		c.displayAs(this.displayMode);

		// Remove the content
//		removeContent(c.getName());

		// Add an isosurface
//		c = addMesh(imp);

		// display slice 10 as a surface plot
//		removeContent(c.getName());
//		imp.setSlice(10);
//		c = addSurfacePlot(imp);

		// remove all contents
//		removeAllContents();
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
		if(Platform.getCurrentPlatform()==Platform.LINUX) {
			System.setProperty("jogl.disable.openglcore", System.getProperty("jogl.disable.openglcore", "false"));
		}
	}
}