package com.vis.core.view.D3.roi;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.vis.configuration.RoiDBKey;
import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.log.Log;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

/**
 * Thin per-Praparat helper that treats the {@link FreeFormRoi3D} entries of a
 * Praparat's 3D ROI list as editable segmentation objects.
 *
 * <p>A segmentation object is just a {@code FreeFormRoi3D} (bit-packed binary
 * mask stack) carrying segment attributes (number, color, name). One object maps
 * to one channel and, on export, to one DICOM SEG segment.
 *
 * @author tatsunidas
 */
public final class SegmentationManager {

	// Distinct default colors assigned to new segments (cycled by segment number).
	private static final Color[] PALETTE = {
			new Color(0xE6194B), new Color(0x3CB44B), new Color(0xFFE119), new Color(0x4363D8),
			new Color(0xF58231), new Color(0x911EB4), new Color(0x46F0F0), new Color(0xF032E6),
			new Color(0xBCF60C), new Color(0xFABEBE), new Color(0x008080), new Color(0xE6BEFF) };

	private SegmentationManager() {
	}

	/** All segmentation objects (FreeFormRoi3D) currently held by the Praparat. */
	public static List<FreeFormRoi3D> getSegmentations(Praparat pp) {
		List<FreeFormRoi3D> result = new ArrayList<>();
		if (pp == null) {
			return result;
		}
		List<RoiObj> list = pp.getRoi3DList();
		if (list == null) {
			return result;
		}
		for (RoiObj r : list) {
			if (r instanceof FreeFormRoi3D) {
				result.add((FreeFormRoi3D) r);
			}
		}
		return result;
	}

	/** Next free 1-based segment number for the Praparat. */
	public static int nextSegmentNumber(Praparat pp) {
		int max = 0;
		for (FreeFormRoi3D seg : getSegmentations(pp)) {
			int n = seg.getSegmentNumber();
			if (n > max) {
				max = n;
			}
		}
		return max + 1;
	}

	/** Default display color for a 1-based segment number. */
	public static Color defaultColor(int segmentNumber) {
		int idx = (segmentNumber - 1) % PALETTE.length;
		if (idx < 0) {
			idx = 0;
		}
		return PALETTE[idx];
	}

	/**
	 * Creates an empty segmentation object whose volume geometry is taken from the
	 * reference series (origin/orientation/spacing/dimensions), assigns it a segment
	 * number, color and name, registers it on the Praparat and returns it.
	 *
	 * <p>The geometry setup mirrors {@code SlideGlass.executeWand3D} so masks painted
	 * into it align with the reference images in physical (IPP) space.
	 *
	 * @return the created object, or null if the series has no usable geometry
	 */
	public static FreeFormRoi3D createSegmentation(Praparat pp, String name) {
		if (pp == null || pp.getAllSlides() == null || pp.getAllSlides().isEmpty()) {
			return null;
		}

		SlideGlass curSg = pp.getCurrentSlide();
		if (curSg == null) {
			curSg = pp.getFirstNoEmptySlide();
		}
		if (curSg == null) {
			return null;
		}

		int[] zct = pp.getZCTArray(curSg);
		int currentC = (zct != null && zct[1] >= 0) ? zct[1] : 0;
		int currentT = (zct != null && zct[2] >= 0) ? zct[2] : 0;

		// Volume dimensions: XY from the image, Z from the highest Z index present.
		int dimX = curSg.getOriginalImageSize().width;
		int dimY = curSg.getOriginalImageSize().height;
		int maxZ = 0;
		for (Integer key : pp.getAllSlides().keySet()) {
			int[] a = pp.calcZCTArrayFromIndex(key);
			if (a[0] > maxZ) {
				maxZ = a[0];
			}
		}
		int dimZ = maxZ + 1;

		// Geometry reference: the Z=0 slice of the current channel/time.
		int idx0 = pp.calcZctIndex(new int[] { 0, currentC, currentT });
		SlideGlass sg0 = pp.getSlideGlassAt(idx0);
		if (sg0 == null) {
			sg0 = curSg;
		}
		DicomObject h0 = sg0.getHeader();
		int frameIdx0 = pp.isMultiFrame() ? h0.getInt(Tag.InstanceNumber, 1) - 1 : 0;
		double[] originIpp = pp.getSafeIPP(h0, frameIdx0);
		double[] iop = pp.getSafeIOP(h0, frameIdx0);
		double spX = sg0.getPixelSpacingX() <= 0 ? 1.0 : sg0.getPixelSpacingX();
		double spY = sg0.getPixelSpacingY() <= 0 ? 1.0 : sg0.getPixelSpacingY();
		double spZ = h0.getDouble(Tag.SpacingBetweenSlices, h0.getDouble(Tag.SliceThickness, 1.0));
		if (spZ <= 0) {
			spZ = 1.0;
		}

		FreeFormRoi3D seg = new FreeFormRoi3D(0, 0, dimX, dimY, sg0);
		seg.setProperty(RoiDBKey.RoiID.name(), RoiObj.createRoiIndex());
		seg.setProperty(RoiDBKey.RoiGroup.name(), String.valueOf((int) (System.currentTimeMillis() % 1000000000L)));
		// Dim_C/Dim_T fix which reference channel/time this segment overlays (usually channel 0).
		seg.setProperty(RoiMetaContextKey.Dim_C.name(), String.valueOf(currentC));
		seg.setProperty(RoiMetaContextKey.Dim_T.name(), String.valueOf(currentT));

		if (originIpp != null && iop != null) {
			seg.initVolume(originIpp, iop, new double[] { spX, spY, spZ }, new int[] { dimX, dimY, dimZ });
		} else {
			Log.logger.warning("SegmentationManager: reference series has no IPP/IOP; segment geometry is incomplete.");
		}

		int number = nextSegmentNumber(pp);
		seg.setSegmentation(true);
		seg.setSegmentNumber(number);
		seg.setSegmentColor(defaultColor(number));
		seg.setName((name != null && !name.trim().isEmpty()) ? name.trim() : ("Segment " + number));

		pp.addRoi3D(seg);
		return seg;
	}
}
