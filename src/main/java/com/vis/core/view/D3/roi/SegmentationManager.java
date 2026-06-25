package com.vis.core.view.D3.roi;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.vis.configuration.RoiDBKey;
import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.log.Log;
import com.vis.core.view.D2.roi.OvalRoi;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiType;
import com.vis.core.view.D2.roi.ShapeRoi;
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

	// ========== Reference alignment / overlap ==========

	/** Reference series slice IPPs (channel 0, time 0), one per spatial Z slice. */
	public static List<double[]> referenceSliceIpps(Praparat pp) {
		List<double[]> ipps = new ArrayList<>();
		if (pp == null || pp.getAllSlides() == null || pp.getAllSlides().isEmpty()) {
			return ipps;
		}
		SlideGlass ref = pp.getFirstNoEmptySlide();
		if (ref == null) {
			return ipps;
		}
		int[] zct = pp.getZCTArray(ref);
		int c0 = (zct != null && zct[1] >= 0) ? zct[1] : 0;
		int t0 = (zct != null && zct[2] >= 0) ? zct[2] : 0;
		int maxZ = 0;
		for (Integer key : pp.getAllSlides().keySet()) {
			int z = pp.calcZCTArrayFromIndex(key)[0];
			if (z > maxZ) {
				maxZ = z;
			}
		}
		for (int z = 0; z <= maxZ; z++) {
			SlideGlass sgz = pp.getSlideGlassAt(pp.calcZctIndex(new int[] { z, c0, t0 }));
			if (sgz == null) {
				continue;
			}
			int fIdx = pp.isMultiFrame() ? sgz.getHeader().getInt(Tag.InstanceNumber, 1) - 1 : 0;
			double[] ippz = pp.getSafeIPP(sgz.getHeader(), fIdx);
			if (ippz != null && ippz.length >= 3) {
				ipps.add(ippz);
			}
		}
		return ipps;
	}

	/**
	 * True if the segmentation has any mask content that lands on the given reference
	 * slice positions (physical IPP overlap). Tolerates slice thickness/gap/FOV
	 * differences because mapping is by nearest physical slice.
	 */
	public static boolean overlapsReference(FreeFormRoi3D seg, List<double[]> refIpps) {
		if (seg == null || refIpps == null) {
			return false;
		}
		for (double[] ipp : refIpps) {
			int vk = seg.getZIndexForSlice(ipp);
			if (vk >= 0 && seg.getMaskAsBytes(vk) != null) {
				return true;
			}
		}
		return false;
	}

	// ========== Roi2Mask conversion ==========

	/**
	 * Converts a drawn 2D ROI to a filled ShapeRoi in image-pixel coordinates.
	 * Area ROIs are used directly, line ROIs are thickened to an area, and point
	 * ROIs become a small disc per point. Returns null if not convertible.
	 */
	public static ShapeRoi toShapeRoi(RoiObj roi) {
		if (roi == null) {
			return null;
		}
		if (roi instanceof ShapeRoi) {
			return (ShapeRoi) roi;
		}
		RoiType t = roi.getRoiType();
		if (t == RoiType.POINT || t == RoiType.MULTIPOINT) {
			// Paint a small disc at each point so points contribute area to the mask.
			ij.process.FloatPolygon fp = roi.getFloatPolygon();
			if (fp == null || fp.npoints == 0) {
				return null;
			}
			int r = 3;
			ShapeRoi acc = null;
			for (int i = 0; i < fp.npoints; i++) {
				int cx = Math.round(fp.xpoints[i]);
				int cy = Math.round(fp.ypoints[i]);
				OvalRoi oval = new OvalRoi(cx - r, cy - r, 2 * r, 2 * r, roi.getSlideGlass());
				ShapeRoi disc = new ShapeRoi(oval);
				acc = (acc == null) ? disc : acc.or(disc);
			}
			return acc;
		}
		RoiObj area = roi;
		if (roi.isLine()) {
			RoiObj converted = RoiObj.convertLineToArea(roi);
			if (converted != null) {
				area = converted;
			}
		}
		try {
			return new ShapeRoi(area);
		} catch (Exception e) {
			Log.logger.warning("toShapeRoi failed: " + e);
			return null;
		}
	}

	/**
	 * Roi2Mask for a single source ROI: rasterizes/voxelizes it (add) into the
	 * target mask. Handles 2D ROIs (baked at their own slice), spheres and other
	 * FreeFormRoi3D (voxel OR).
	 */
	public static void rasterizeInto(FreeFormRoi3D target, RoiObj roi) {
		if (target == null || roi == null || roi == target) {
			return;
		}
		if (roi instanceof FreeFormRoi3D) {
			target.or((FreeFormRoi3D) roi);
			return;
		}
		if (roi instanceof SphereRoi3D) {
			SlideGlass sg = roi.getSlideGlass();
			Praparat pp = (sg != null) ? sg.getPraparat() : null;
			FreeFormRoi3D tmp = FreeFormRoi3D.createFromSphere(pp, (SphereRoi3D) roi, "tmp");
			if (tmp != null) {
				target.or(tmp);
			}
			return;
		}
		// 2D ROI: bake at its own slice using that slice as the edit context.
		SlideGlass sg = roi.getSlideGlass();
		if (sg == null) {
			return;
		}
		ShapeRoi shape = toShapeRoi(roi);
		if (shape == null) {
			return;
		}
		target.setSlideGlass(sg, false);
		target.editWithBrush(shape, true);
	}

	/**
	 * Imports the given 2D/3D ROIs into a segmentation (Roi2Mask). When {@code target}
	 * is null a new segmentation named {@code newName} is created. The source ROIs are
	 * left intact (non-destructive). Returns the target, or null on failure.
	 */
	public static FreeFormRoi3D importRoisIntoSegmentation(Praparat pp, List<RoiObj> rois, FreeFormRoi3D target,
			String newName) {
		if (pp == null || rois == null || rois.isEmpty()) {
			return null;
		}
		if (target == null) {
			target = createSegmentation(pp, newName);
			if (target == null) {
				return null;
			}
		}
		for (RoiObj r : rois) {
			try {
				rasterizeInto(target, r);
			} catch (Exception e) {
				Log.logger.warning("importRoisIntoSegmentation: failed on a ROI: " + e);
			}
		}
		return target;
	}
}
