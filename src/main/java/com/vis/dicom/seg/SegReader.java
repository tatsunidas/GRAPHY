package com.vis.dicom.seg;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vis.configuration.RoiDBKey;
import com.vis.core.log.Log;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D3.roi.FreeFormRoi3D;
import com.vis.dicom.DicomObject;

/**
 * Decodes a BINARY DICOM Segmentation (SEG) instance into editable
 * {@link FreeFormRoi3D} objects (one per segment), reconstructing each volume's
 * geometry from the SEG's functional groups so the masks align with the
 * reference series in physical (IPP) space (Mask2Roi).
 *
 * @author tatsunidas
 */
public final class SegReader {

	// Tags (integer hex literals, matching SegWriter).
	private static final int SliceThickness = 0x00180050;
	private static final int SpacingBetweenSlices = 0x00180088;
	private static final int ImagePositionPatient = 0x00200032;
	private static final int ImageOrientationPatient = 0x00200037;
	private static final int NumberOfFrames = 0x00280008;
	private static final int Rows = 0x00280010;
	private static final int Columns = 0x00280011;
	private static final int PixelSpacing = 0x00280030;
	private static final int SegmentationType = 0x00620001;
	private static final int SegmentSequence = 0x00620002;
	private static final int SegmentNumber = 0x00620004;
	private static final int SegmentLabel = 0x00620005;
	private static final int RecommendedDisplayCIELabValue = 0x0062000D;
	private static final int SegmentIdentificationSequence = 0x0062000A;
	private static final int ReferencedSegmentNumber = 0x0062000B;
	private static final int SharedFunctionalGroupsSequence = 0x52009229;
	private static final int PerFrameFunctionalGroupsSequence = 0x52009230;
	private static final int PlaneOrientationSequence = 0x00209116;
	private static final int PixelMeasuresSequence = 0x00289110;
	private static final int PlanePositionSequence = 0x00209113;
	private static final int PixelMeasuresSliceThickness = SliceThickness;
	private static final int PixelData = 0x7FE00010;

	private SegReader() {
	}

	/** Decodes the SEG into per-segment editable masks. Empty list on failure / unsupported. */
	public static List<FreeFormRoi3D> read(DicomObject seg) {
		if (seg == null) {
			return Collections.emptyList();
		}
		try {
			String segType = seg.getString(SegmentationType);
			if (segType != null && !segType.trim().equalsIgnoreCase("BINARY")) {
				Log.logger.warning("SegReader: only BINARY SEG is supported, got: " + segType);
				return Collections.emptyList();
			}
			int rows = seg.getInt(Rows, 0);
			int cols = seg.getInt(Columns, 0);
			int numFrames = seg.getInt(NumberOfFrames, 0);
			if (rows <= 0 || cols <= 0 || numFrames <= 0) {
				return Collections.emptyList();
			}

			// ---- Shared geometry (orientation + pixel measures) ----
			double[] iop = null;
			double sx = 1, sy = 1, sz = 1;
			DicomObject shared = seg.getNestedDataset(SharedFunctionalGroupsSequence, 0);
			if (shared != null) {
				DicomObject po = shared.getNestedDataset(PlaneOrientationSequence, 0);
				if (po != null) {
					iop = po.getDoubles(ImageOrientationPatient);
				}
				DicomObject pm = shared.getNestedDataset(PixelMeasuresSequence, 0);
				if (pm != null) {
					double[] ps = pm.getDoubles(PixelSpacing); // [rowSpacing(Y), colSpacing(X)]
					if (ps != null && ps.length == 2) {
						sy = ps[0];
						sx = ps[1];
					}
					sz = pm.getDouble(SpacingBetweenSlices, pm.getDouble(PixelMeasuresSliceThickness, 1.0));
				}
			}
			if (iop == null) {
				iop = seg.getDoubles(ImageOrientationPatient);
			}
			if (iop == null || iop.length != 6) {
				iop = new double[] { 1, 0, 0, 0, 1, 0 };
			}
			if (sz <= 0) {
				sz = seg.getDouble(SpacingBetweenSlices, seg.getDouble(SliceThickness, 1.0));
			}

			double nx = iop[1] * iop[5] - iop[2] * iop[4];
			double ny = iop[2] * iop[3] - iop[0] * iop[5];
			double nz = iop[0] * iop[4] - iop[1] * iop[3];
			double nlen = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (nlen > 1e-12) {
				nx /= nlen;
				ny /= nlen;
				nz /= nlen;
			}

			// ---- Segment metadata ----
			Map<Integer, String> labels = new HashMap<>();
			Map<Integer, Color> colors = new HashMap<>();
			int segCount = seqSize(seg, SegmentSequence);
			for (int i = 0; i < segCount; i++) {
				DicomObject item = seg.getNestedDataset(SegmentSequence, i);
				if (item == null) {
					continue;
				}
				int num = item.getInt(SegmentNumber, -1);
				if (num < 0) {
					continue;
				}
				labels.put(num, item.getString(SegmentLabel, "Segment " + num));
				int[] lab = item.getInts(RecommendedDisplayCIELabValue);
				colors.put(num, (lab != null && lab.length == 3) ? cieLabToRgb(lab) : null);
			}

			// ---- Per-frame: segment + position ----
			int pfCount = seqSize(seg, PerFrameFunctionalGroupsSequence);
			if (pfCount == 0) {
				return Collections.emptyList();
			}
			int n = Math.min(numFrames, pfCount);
			int[] frameSeg = new int[n];
			double[] framePos = new double[n];
			double[][] frameIpp = new double[n][];
			double minPos = Double.POSITIVE_INFINITY;
			double maxPos = Double.NEGATIVE_INFINITY;
			for (int f = 0; f < n; f++) {
				DicomObject fr = seg.getNestedDataset(PerFrameFunctionalGroupsSequence, f);
				if (fr == null) {
					frameSeg[f] = 1;
					frameIpp[f] = new double[] { 0, 0, f * sz };
					framePos[f] = f * sz;
					if (framePos[f] < minPos) {
						minPos = framePos[f];
					}
					if (framePos[f] > maxPos) {
						maxPos = framePos[f];
					}
					continue;
				}
				int segNum = 1;
				DicomObject sid = fr.getNestedDataset(SegmentIdentificationSequence, 0);
				if (sid != null) {
					segNum = sid.getInt(ReferencedSegmentNumber, 1);
				}
				double[] ipp = null;
				DicomObject pp = fr.getNestedDataset(PlanePositionSequence, 0);
				if (pp != null) {
					ipp = pp.getDoubles(ImagePositionPatient);
				}
				if (ipp == null || ipp.length < 3) {
					ipp = new double[] { 0, 0, f * sz };
				}
				double pos = ipp[0] * nx + ipp[1] * ny + ipp[2] * nz;
				frameSeg[f] = segNum;
				frameIpp[f] = ipp;
				framePos[f] = pos;
				if (pos < minPos) {
					minPos = pos;
				}
				if (pos > maxPos) {
					maxPos = pos;
				}
			}

			// Shared volume origin = IPP of the lowest slice; dimZ spans all frames.
			double[] origin = null;
			for (int f = 0; f < n; f++) {
				if (framePos[f] == minPos) {
					origin = frameIpp[f];
					break;
				}
			}
			if (origin == null) {
				origin = new double[] { 0, 0, 0 };
			}
			int dimZ = (sz > 0) ? (int) Math.round((maxPos - minPos) / sz) + 1 : 1;
			if (dimZ < 1) {
				dimZ = 1;
			}

			byte[] pix = seg.getBytes(PixelData);
			int frameSize = rows * cols;

			// ---- Build one FreeFormRoi3D per segment ----
			Map<Integer, FreeFormRoi3D> byNum = new LinkedHashMap<>();
			long groupBase = System.currentTimeMillis() % 1000000000L;
			for (int f = 0; f < n; f++) {
				int segNum = frameSeg[f];
				FreeFormRoi3D ff = byNum.get(segNum);
				if (ff == null) {
					ff = new FreeFormRoi3D(0, 0, cols, rows, null);
					ff.initVolume(origin, iop, new double[] { sx, sy, sz }, new int[] { cols, rows, dimZ });
					ff.setSegmentation(true);
					ff.setSegmentNumber(segNum);
					ff.setName(labels.getOrDefault(segNum, "Segment " + segNum));
					Color c = colors.get(segNum);
					if (c != null) {
						ff.setSegmentColor(c);
					}
					ff.setProperty(RoiDBKey.RoiID.name(), RoiObj.createRoiIndex());
					ff.setProperty(RoiDBKey.RoiGroup.name(), String.valueOf(groupBase + segNum));
					byNum.put(segNum, ff);
				}
				int k = (sz > 0) ? (int) Math.round((framePos[f] - minPos) / sz) : 0;
				ff.setSliceMaskFromBytes(k, decodeFrame(pix, f, frameSize));
			}
			return new ArrayList<>(byNum.values());
		} catch (Exception e) {
			Log.logger.warning("SegReader: failed to read SEG: " + e);
			return Collections.emptyList();
		}
	}

	/** Number of items in a sequence (getSequence may return a raw dcm4che Sequence or a List). */
	private static int seqSize(DicomObject d, int tag) {
		Object o = d.getSequence(tag);
		if (o instanceof org.dcm4che3.data.Sequence) {
			return ((org.dcm4che3.data.Sequence) o).size();
		}
		if (o instanceof java.util.List) {
			return ((java.util.List<?>) o).size();
		}
		return 0;
	}

	/** Unpacks one frame's plane from the continuous LSB-first 1-bit pixel stream. */
	private static byte[] decodeFrame(byte[] pix, int frame, int frameSize) {
		byte[] plane = new byte[frameSize];
		if (pix == null) {
			return plane;
		}
		long base = (long) frame * frameSize;
		for (int idx = 0; idx < frameSize; idx++) {
			long p = base + idx;
			int bytePos = (int) (p >> 3);
			int bit = (int) (p & 7);
			if (bytePos < pix.length && ((pix[bytePos] >> bit) & 1) != 0) {
				plane[idx] = (byte) 255;
			}
		}
		return plane;
	}

	// DICOM RecommendedDisplayCIELabValue (16-bit) -> sRGB.
	private static Color cieLabToRgb(int[] lab) {
		double L = lab[0] * 100.0 / 65535.0;
		double a = lab[1] * 255.0 / 65535.0 - 128.0;
		double b = lab[2] * 255.0 / 65535.0 - 128.0;
		double fy = (L + 16.0) / 116.0;
		double fx = fy + a / 500.0;
		double fz = fy - b / 200.0;
		double X = labInv(fx) * 0.95047;
		double Y = labInv(fy) * 1.0;
		double Z = labInv(fz) * 1.08883;
		double r = X * 3.2406 - Y * 1.5372 - Z * 0.4986;
		double g = -X * 0.9689 + Y * 1.8758 + Z * 0.0415;
		double bb = X * 0.0557 - Y * 0.2040 + Z * 1.0570;
		return new Color(to8(linToSrgb(r)), to8(linToSrgb(g)), to8(linToSrgb(bb)));
	}

	private static double labInv(double t) {
		double t3 = t * t * t;
		return (t3 > 0.008856) ? t3 : (t - 16.0 / 116.0) / 7.787;
	}

	private static double linToSrgb(double v) {
		v = Math.max(0, Math.min(1, v));
		return (v <= 0.0031308) ? v * 12.92 : 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
	}

	private static int to8(double v) {
		return Math.max(0, Math.min(255, (int) Math.round(v * 255.0)));
	}
}
