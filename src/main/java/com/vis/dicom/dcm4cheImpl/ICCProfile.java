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
package com.vis.dicom.dcm4cheImpl;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;

import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.image.BufferedImageUtils;
import com.vis.dicom.image.ColorModelFactory;
import com.vis.dicom.image.PaletteColorModel;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @author tatsunidas
 * @since Jun 2023
 */
public final class ICCProfile {

    private ICCProfile() {}

    @FunctionalInterface
    public interface ColorSpaceFactory {
        Optional<ColorSpace> getColorSpace(int frameIndex);
    }

    public static boolean isPresentIn(DicomObject attrs) {
        return attrs.containsValue(Tag.ICC​Profile) || attrs.containsValue(Tag.Optical​Path​Sequence);
    }

    public static ColorSpaceFactory colorSpaceFactoryOf(DicomObject attrs) {
        byte[] b = attrs.getSafeBytes(Tag.ICC​Profile);
        if (b == null) {
            Sequence opticalPathSequence = (Sequence) attrs.getSequence(Tag.Optical​Path​Sequence);
            if (opticalPathSequence != null && !opticalPathSequence.isEmpty()) {
                if (opticalPathSequence.size() > 1) {
                    return frameIndex -> getColorSpace(attrs, opticalPathSequence, frameIndex);
                }
                b = opticalPathSequence.get(0).getSafeBytes(Tag.ICC​Profile);
            }
        }
        if (b == null) {
            return frameIndex -> Optional.empty();
        }
        Optional<ColorSpace> cs = Optional.of(new ICC_ColorSpace(ICC_Profile.getInstance(b)));
        return frameIndex -> cs;
    }

    private static Optional<ColorSpace> getColorSpace(DicomObject attrs, Sequence opticalPathSequence, int frameIndex) {
        DicomObject functionGroup = (DicomObject) attrs.getFunctionGroup(Tag.Optical​Path​Identification​Sequence, frameIndex);
        if (functionGroup != null) {
            String opticalPathID = functionGroup.getString(Tag.Optical​Path​Identifier);
            if (opticalPathID != null) {
                Optional<Attributes> match = opticalPathSequence.stream()
                        .filter(item -> opticalPathID.equals(item.getString(Tag.Optical​Path​Identifier)))
                        .findFirst();
                if (match.isPresent()) {
                    byte[] b = match.get().getSafeBytes(Tag.ICC​Profile);
                    if (b != null)
                        return Optional.of(new ICC_ColorSpace(ICC_Profile.getInstance(b)));
                }
            }
        }
        return Optional.empty();
    }

    public enum Option {
        none {
            @Override
            protected BufferedImage convertColor(BufferedImage bi) {
                return isCS_sRGB(bi) ? bi : BufferedImageUtils.convertColor(bi, CM_sRGB);
            }
        },
        no {
            @Override
            protected BufferedImage convertColor(BufferedImage bi) {
                return isCS_sRGB(bi) ? bi : BufferedImageUtils.replaceColorModel(bi, CM_sRGB);
            }
        },
        yes {
            @Override
            protected BufferedImage convertColor(BufferedImage bi) {
                return isCS_sRGB(bi) ? BufferedImageUtils.replaceColorModel(bi, srgb.colorModel) : bi;
            }
        },
        srgb("sRGB.icc") {
            @Override
            protected BufferedImage convertColor(BufferedImage bi) {
                return isCS_sRGB(bi)
                        ? BufferedImageUtils.replaceColorModel(bi, srgb.colorModel)
                        : BufferedImageUtils.convertColor(bi, srgb.colorModel);
            }
        },
        adobergb("adobeRGB.icc"),
        rommrgb("rommRGB.icc");

        private static final ColorModel CM_sRGB = ColorModelFactory.createRGBColorModel(
                8, DataBuffer.TYPE_BYTE, ColorSpace.getInstance(ColorSpace.CS_sRGB));
        private final ColorModel colorModel;

        Option() {
            colorModel = null;
        }

        Option(String fileName) {
            try (InputStream is = ICCProfile.class.getResourceAsStream(fileName)){
                colorModel = ColorModelFactory.createRGBColorModel(8, DataBuffer.TYPE_BYTE,
                        new ICC_ColorSpace(ICC_Profile.getInstance(is)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public BufferedImage adjust(BufferedImage bi) {
            ColorModel cm = bi.getColorModel();
            return cm.getNumColorComponents() == 3
                    ? convertColor(toRGB(bi, cm))
                    : bi;
        }

        protected BufferedImage convertColor(BufferedImage bi) {
            return BufferedImageUtils.convertColor(bi, colorModel);
        }

        private static boolean isCS_sRGB(BufferedImage bi) {
            return bi.getColorModel().getColorSpace().isCS_sRGB();
        }

        private static BufferedImage toRGB(BufferedImage bi, ColorModel cm) {
            return cm instanceof PaletteColorModel
                    ? BufferedImageUtils.convertPalettetoRGB(bi, null)
                    : cm.getColorSpace().getType() == ColorSpace.TYPE_YCbCr
                    ? BufferedImageUtils.convertYBRtoRGB(bi, null)
                    : bi;
        }
    }
}
