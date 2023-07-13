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

import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.ColorModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;

import com.vis.dicom.DicomObject;
import com.vis.dicom.UID;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author tatsunidas
 *
 */
public enum PhotometricInterpretation {
    MONOCHROME1(true, true, false, false) {
        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            return ColorModelFactory.createMonochromeColorModel(bits, dataType);
        }
    },
    MONOCHROME2(true, false, false, false) {
        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            return ColorModelFactory.createMonochromeColorModel(bits, dataType);
        }
    },
    PALETTE_COLOR(false, false, false, false) {
        @Override
        public String toString() {
            return "PALETTE COLOR";
        }

        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            return ColorModelFactory.createPaletteColorModel(bits, dataType, cspace, ds);
        }
    },
    RGB(false, false, false, false) {
        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            return ColorModelFactory.createRGBColorModel(bits, dataType, cspace);
        }

		@Override
		public PhotometricInterpretation compress(UID tsuid) {
			switch (tsuid) {
			case JPEGBaseline8Bit:
			case JPEGExtended12Bit:
				return YBR_FULL_422;
			case JPEGSpectralSelectionNonHierarchical68:
			case JPEGFullProgressionNonHierarchical1012:
				return YBR_FULL;
			case JPEG2000Lossless:
			case JPEG2000MCLossless:
				return YBR_RCT;
			case JPEG2000:
			case JPEG2000MC:
				return YBR_ICT;
			default:
				break;
			}
			return this;
		}
    },
    YBR_FULL(false, false, true, false) {
        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            return ColorModelFactory.createYBRFullColorModel(bits, dataType, new YBRColorSpace(cspace,  YBR.FULL));
        }
    },
    YBR_FULL_422(false, false, true, true) {
        @Override
        public int frameLength(int w, int h, int samples, int bitsAllocated) {
            return ColorSubsampling.YBR_XXX_422.frameLength(w, h);
        }

        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            return ColorModelFactory.createYBRColorModel(bits, dataType, new YBRColorSpace(cspace, YBR.PARTIAL),
                    ColorSubsampling.YBR_XXX_422);
        }

        @Override
        public SampleModel createSampleModel(int dataType, int w, int h,
                                             int samples, boolean banded) {
            return new SampledComponentSampleModel(w, h, ColorSubsampling.YBR_XXX_422);
        }
    },
    YBR_ICT(false, false, true, false) {
        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            throw new UnsupportedOperationException();
        }

    },
    YBR_PARTIAL_420(false, false, true, true) {
        @Override
        public int frameLength(int w, int h, int samples, int bitsAllocated) {
            return ColorSubsampling.YBR_XXX_420.frameLength(w, h);
        }

        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            return ColorModelFactory.createYBRColorModel(bits, dataType, new YBRColorSpace(cspace, YBR.PARTIAL),
                    ColorSubsampling.YBR_XXX_420);
        }

        @Override
        public SampleModel createSampleModel(int dataType, int w, int h,
                                             int samples, boolean banded) {
            return new SampledComponentSampleModel(w, h, ColorSubsampling.YBR_XXX_420);
        }
    },
    YBR_PARTIAL_422(false, false, true, true) {
        @Override
        public int frameLength(int w, int h, int samples, int bitsAllocated) {
            return ColorSubsampling.YBR_XXX_422.frameLength(w, h);
        }

        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            return ColorModelFactory.createYBRColorModel(bits, dataType, new YBRColorSpace(cspace, YBR.PARTIAL),
                    ColorSubsampling.YBR_XXX_422);
        }

        @Override
        public SampleModel createSampleModel(int dataType, int w, int h,
                                             int samples, boolean banded) {
            return new SampledComponentSampleModel(w, h, ColorSubsampling.YBR_XXX_422);
        }
    },
    YBR_RCT(false, false, true, false) {
        @Override
        public ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds) {
            throw new UnsupportedOperationException();
        }
    };

    private final boolean monochrome;
    private final boolean inverse;
    private final boolean ybr;
    private final boolean subSampled;

    PhotometricInterpretation(boolean monochrome, boolean inverse, boolean ybr, boolean subSampled) {
        this.monochrome = monochrome;
        this.inverse = inverse;
        this.ybr = ybr;
        this.subSampled = subSampled;
    }

    public static PhotometricInterpretation fromString(String s) {
        return s.equals("PALETTE COLOR") ? PALETTE_COLOR : valueOf(s);
    }

    public int frameLength(int w, int h, int samples, int bitsAllocated) {
        return w * h * samples * bitsAllocated / 8;
    }

    public boolean isMonochrome() {
        return monochrome;
    }

    public boolean isYBR() {
        return ybr;
    }

    public PhotometricInterpretation compress(UID tsuid) {
        return this;
    }

    public boolean isInverse() {
        return inverse;
    }

    public boolean isSubSampled() {
        return subSampled;
    }

    public abstract ColorModel createColorModel(int bits, int dataType, ColorSpace cspace, DicomObject ds);

    public SampleModel createSampleModel(int dataType, int w, int h,
                                         int samples, boolean banded) {
        int[] indicies = new int[samples];
        for (int i = 1; i < samples; i++)
            indicies[i] = i;
        return banded && samples > 1
                ? new BandedSampleModel(dataType, w, h,  w, indicies, new int[samples])
                : new PixelInterleavedSampleModel(dataType, w, h, samples, w * samples, indicies);
    }
}
