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
package com.vis.dicom.image;

import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class PixelAspectRatio {

    public static float forImage(DicomObject attrs) {
        return forImage(attrs, Tag.Pixel​Aspect​Ratio,
            Tag.Pixel​Spacing,
            Tag.Imager​Pixel​Spacing,
            Tag.Nominal​Scanned​Pixel​Spacing);
    }

    public static float forPresentationState(DicomObject attrs) {
        return forImage(attrs, Tag.Presentation​Pixel​Aspect​Ratio,
                Tag.Presentation​Pixel​Spacing);
    }

    private static float forImage(DicomObject attrs, int aspectRatioTag,
            int... pixelSpacingTags) {
        int[] ratio = attrs.getInts(aspectRatioTag);
        if (ratio != null && ratio.length == 2
                && ratio[0] > 0 && ratio[1] > 0)
            return (float) ratio[0] / ratio[1];

        for (int pixelSpacingTag : pixelSpacingTags) {
            float[] spaces = attrs.getFloats(pixelSpacingTag);
            if (spaces != null && spaces.length == 2
                    && spaces[0] > 0 && spaces[1] > 0)
                return spaces[0] / spaces[1];
        }
        return 1f;
    }

}
