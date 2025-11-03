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

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import io.github.tatsunidas.radiomics.main.Utils;

public class TestResample3D {

	public static void main(String[] args) {
		
		String imagePath = "/home/tatsunidas/デスクトップ/batch_test_radj/T1_LEFT_PLAQUE/IMAGES/case_003";
		String maskPath = "/home/tatsunidas/デスクトップ/batch_test_radj/T1_LEFT_PLAQUE/MASKS/case_003";
		
		ImagePlus image = FolderOpener.open(imagePath);
		ImagePlus mask = FolderOpener.open(maskPath);
		
		mask.setCalibration(image.getCalibration());
		ImagePlus pad_mask = Utils.padMaskStack(mask, image, new int[] {7,8});
		
		ImagePlus pad_mask2 = io.github.tatsunidas.radiomics.main.Utils.initMaskAsFloatAndConvertLabelOne(pad_mask, 255/*original mask label*/);
		
		ImagePlus reImage = Utils.resample3D(image, false, 1, 1, 1);
		ImagePlus reMask = Utils.resample3D(pad_mask2, true, 1, 1, 1);
		
		reImage.show();
		reMask.show();
		
		IJ.saveAsTiff(reMask, "/home/tatsunidas/デスクトップ/reMask.tif");
		IJ.saveAsTiff(pad_mask2, "/home/tatsunidas/デスクトップ/reMask_padMask.tif");
	}

}
