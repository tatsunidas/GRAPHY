import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import io.github.tatsunidas.radiomics.features.Shape2DFeatureType;
import io.github.tatsunidas.radiomics.features.Shape2DFeatures;

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

public class Shape2DPixelSurface {

	public static void main(String[] args) {
		ImageProcessor ip = new ByteProcessor(100,100);
		ImagePlus imp = new ImagePlus("", ip);
		ImagePlus mask = new ImagePlus("", ip.duplicate());
		ij.gui.Roi roi = new Roi(10,10, 20, 10);
		mask.getProcessor().setColor(255);
		mask.getProcessor().fill(roi);
		
		imp.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		
		Shape2DFeatures s2d = new Shape2DFeatures(imp, mask, 1, 255);
//		System.out.println(s2d.calculate(Shape2DFeatureType.PixelSurface.id()));
		
		int cnt = 0;
		for(int i=0; i<imp.getWidth() ;i++) {
			for(int j=0; j<imp.getHeight() ;j++) {
				if(roi.contains(i, j)) {
					cnt++;
				}
			}
		}
		Calibration cal = imp.getCalibration();
		System.out.println(cnt * cal.pixelHeight * cal.pixelWidth);
		
	}

}
