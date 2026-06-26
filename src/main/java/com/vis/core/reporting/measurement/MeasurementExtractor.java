package com.vis.core.reporting.measurement;

import java.util.HashMap;
import java.util.List;

import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.sr.SRCodes;
import com.vis.core.view.D2.roi.Line;
import com.vis.core.view.D2.roi.Measurements;
import com.vis.core.view.D2.roi.RoiAnalyzer;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D3.roi.RoiObj3D;
import com.vis.core.view.D3.roi.SphereRoi3D;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

import org.dcm4che3.data.Code;

/**
 * Converts the live ROIs / annotations on a {@link Praparat} into a
 * {@link MeasurementReport} suitable for export as a DICOM TID 1500 measurement SR
 * (see {@code Tid1500Writer} / {@code ReportService.finalizeMeasurementsAsSR}).
 * <p>
 * Coverage:
 * <ul>
 *   <li><b>2D line</b> ({@link Line}) → Length (mm) + Angle (deg), region
 *       {@code SCOORD} POLYLINE anchored to the image.</li>
 *   <li><b>2D area</b> (rectangle / oval / polygon / freehand) → Area (mm²) plus
 *       intensity statistics (Mean / Min / Max / SD) via {@link RoiAnalyzer},
 *       region {@code SCOORD} POLYLINE of the ROI outline.</li>
 *   <li><b>3D sphere</b> ({@link SphereRoi3D}) → Diameter (mm) + Volume (mm³),
 *       region {@code SCOORD3D} POINT at the centre.</li>
 *   <li><b>3D free-form</b> ({@link RoiObj3D}) → Volume (mm³); voxel-mask geometry
 *       is not emitted as a spatial coordinate.</li>
 * </ul>
 * Point / text / arrow annotations (no measurable quantity) are skipped. Must run
 * on the EDT (reads live Swing-attached ROI/image state).
 *
 * @author tatsunidas
 */
public final class MeasurementExtractor {

	/** Cap on SCOORD outline vertices so a dense polygon doesn't bloat the SR. */
	private static final int MAX_OUTLINE_POINTS = 200;

	private MeasurementExtractor() {
	}

	/**
	 * @param prap  the praparat whose ROIs to extract (2D ROIs + 3D ROI list).
	 * @param title report title (e.g. study description); may be {@code null}.
	 * @return a populated report, or an empty report (no groups) if nothing measurable.
	 */
	public static MeasurementReport fromPraparat(Praparat prap, String title) {
		Object[] uids = prap.getUIDs();
		String patID = str(uids[0]);
		String studyUID = str(uids[1]);
		String frameOfReferenceUID = str(uids[4]);

		MeasurementReport report = new MeasurementReport(patID, studyUID,
				title == null || title.trim().isEmpty() ? "Measurements" : title);

		int idx = 0;
		for (RoiObj roi : prap.getRois()) {
			MeasurementGroup g = from2dRoi(roi, ++idx);
			if (g != null) {
				report.add(g);
			}
		}

		List<RoiObj> roi3d = prap.getRoi3DList();
		if (roi3d != null) {
			for (RoiObj roi : roi3d) {
				MeasurementGroup g = from3dRoi(roi, frameOfReferenceUID, ++idx);
				if (g != null) {
					report.add(g);
				}
			}
		}
		return report;
	}

	// --- 2D --------------------------------------------------------------------

	private static MeasurementGroup from2dRoi(RoiObj roi, int idx) {
		if (roi == null) {
			return null;
		}
		SlideGlass sg = roi.getSlideGlass();
		KeyImageRef image = imageRefOf(sg);

		if (roi.isLine() && roi instanceof Line) {
			Line ln = (Line) roi;
			MeasurementGroup g = newGroup(roi, "Line", idx);
			g.setImage(image);
			g.add(new Measurement(SRCodes.LENGTH, ln.getLength(), SRCodes.U_MM));
			g.add(new Measurement(SRCodes.ANGLE, ln.getAngle(), SRCodes.U_DEGREE));
			g.setRegion(SpatialCoordinate.scoord(SpatialCoordinate.POLYLINE, pointsOf(ln.getFloatPoints()), image));
			return g;
		}

		if (roi.isArea()) {
			HashMap<Measurements, Double> stats = areaStats(roi, sg);
			if (stats == null) {
				return null;
			}
			MeasurementGroup g = newGroup(roi, "ROI", idx);
			g.setImage(image);
			Code intensityUnit = intensityUnit(sg);
			addIfPresent(g, SRCodes.AREA, stats.get(Measurements.AREA), SRCodes.U_MM2);
			addIfPresent(g, SRCodes.MEAN, stats.get(Measurements.MEAN), intensityUnit);
			addIfPresent(g, SRCodes.MINIMUM, stats.get(Measurements.MIN), intensityUnit);
			addIfPresent(g, SRCodes.MAXIMUM, stats.get(Measurements.MAX), intensityUnit);
			addIfPresent(g, SRCodes.STANDARD_DEVIATION, stats.get(Measurements.STD_DEV), intensityUnit);
			if (g.getMeasurements().isEmpty()) {
				return null;
			}
			float[] outline = outlineOf(roi);
			if (outline.length >= 4) {
				g.setRegion(SpatialCoordinate.scoord(SpatialCoordinate.POLYLINE, outline, image));
			}
			return g;
		}

		// point / text / arrow: nothing measurable
		return null;
	}

	private static HashMap<Measurements, Double> areaStats(RoiObj roi, SlideGlass sg) {
		try {
			RoiAnalyzer analyzer = sg != null ? new RoiAnalyzer(roi, sg) : new RoiAnalyzer(roi);
			List<HashMap<Measurements, Double>> res = analyzer.measure();
			return res == null || res.isEmpty() ? null : res.get(0);
		} catch (Exception e) {
			return null;
		}
	}

	/** Hounsfield units for CT, otherwise dimensionless — intensity unit for stats. */
	static Code intensityUnit(SlideGlass sg) {
		String modality = sg == null ? null : sg.getModality();
		return "CT".equalsIgnoreCase(modality) ? SRCodes.U_HU : SRCodes.U_NONE;
	}

	// --- 3D --------------------------------------------------------------------

	private static MeasurementGroup from3dRoi(RoiObj roi, String frameOfReferenceUID, int idx) {
		if (roi instanceof SphereRoi3D) {
			SphereRoi3D s = (SphereRoi3D) roi;
			MeasurementGroup g = newGroup(roi, "Sphere", idx);
			g.add(new Measurement(SRCodes.DIAMETER, 2.0 * s.getRadiusMm(), SRCodes.U_MM));
			g.add(new Measurement(SRCodes.VOLUME, s.getCalculatedVolumeMm3(), SRCodes.U_MM3));
			g.setRegion(SpatialCoordinate.scoord3d(SpatialCoordinate.POINT,
					new float[] { (float) s.getCenterX(), (float) s.getCenterY(), (float) s.getCenterZ() },
					frameOfReferenceUID));
			return g;
		}
		if (roi instanceof RoiObj3D) {
			RoiObj3D r3 = (RoiObj3D) roi;
			MeasurementGroup g = newGroup(roi, "Volume", idx);
			g.add(new Measurement(SRCodes.VOLUME, r3.getCalculatedVolumeMm3(), SRCodes.U_MM3));
			return g;
		}
		return null;
	}

	// --- helpers ---------------------------------------------------------------

	private static MeasurementGroup newGroup(RoiObj roi, String kind, int idx) {
		String name = null;
		try {
			name = roi.getName();
		} catch (Exception ignore) {
			// getName reads a property map; ignore if unavailable
		}
		if (name == null || name.trim().isEmpty()) {
			name = kind + " " + idx;
		}
		return new MeasurementGroup(name);
	}

	private static KeyImageRef imageRefOf(SlideGlass sg) {
		if (sg == null) {
			return null;
		}
		String[] u = sg.getUIDs(); // [patID, studyUID, seriesUID, sopUID]
		String sopClassUID = null;
		DicomObject header = sg.getHeader();
		if (header != null) {
			sopClassUID = header.getString(Tag.SOPClassUID);
		}
		return new KeyImageRef(u[1], u[2], u[3], sopClassUID, null);
	}

	/** Interleave an ij {@code FloatPolygon}'s vertices into {@code [x0,y0,x1,y1,...]}. */
	private static float[] pointsOf(ij.process.FloatPolygon p) {
		if (p == null || p.npoints == 0) {
			return new float[0];
		}
		float[] out = new float[p.npoints * 2];
		for (int i = 0; i < p.npoints; i++) {
			out[i * 2] = p.xpoints[i];
			out[i * 2 + 1] = p.ypoints[i];
		}
		return out;
	}

	/** ROI outline as interleaved pixel coordinates, sub-sampled to {@link #MAX_OUTLINE_POINTS}. */
	private static float[] outlineOf(RoiObj roi) {
		ij.process.FloatPolygon p;
		try {
			p = roi.getFloatPolygon();
		} catch (Exception e) {
			return new float[0];
		}
		if (p == null || p.npoints == 0) {
			return new float[0];
		}
		int n = p.npoints;
		int step = n > MAX_OUTLINE_POINTS ? (int) Math.ceil(n / (double) MAX_OUTLINE_POINTS) : 1;
		java.util.ArrayList<Float> buf = new java.util.ArrayList<>();
		for (int i = 0; i < n; i += step) {
			buf.add(p.xpoints[i]);
			buf.add(p.ypoints[i]);
		}
		float[] out = new float[buf.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = buf.get(i);
		}
		return out;
	}

	private static void addIfPresent(MeasurementGroup g, Code concept, Double value, Code unit) {
		if (value != null && !value.isNaN() && !value.isInfinite()) {
			g.add(new Measurement(concept, value, unit));
		}
	}

	private static String str(Object o) {
		return o == null ? null : o.toString();
	}
}
