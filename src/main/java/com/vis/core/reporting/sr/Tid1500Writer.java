package com.vis.core.reporting.sr;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.measurement.Measurement;
import com.vis.core.reporting.measurement.MeasurementGroup;
import com.vis.core.reporting.measurement.MeasurementReport;
import com.vis.core.reporting.measurement.SpatialCoordinate;
import com.vis.dicom.UID;

/**
 * Builds a DICOM TID 1500 <i>Imaging Measurement Report</i> ({@code Attributes}
 * tree) from a {@link MeasurementReport}. Companion to the free-text
 * {@link SRWriter}; dcm4che provides no high-level SR builder so the content tree
 * is constructed by hand.
 * <p>
 * SOP class is chosen by content: {@link UID#Comprehensive3DSRStorage} when any
 * group carries a {@code SCOORD3D} region (the only SR class permitting 3D spatial
 * coordinates), otherwise {@link UID#ComprehensiveSRStorage}.
 * <p>
 * Structure produced (simplified TID 1500 / 1410):
 * <pre>
 * CONTAINER  "Imaging Measurement Report" (DCM 126000)
 *   CONTAINS CONTAINER  "Imaging Measurements" (DCM 126010)
 *     CONTAINS CONTAINER  "Measurement Group" (DCM 125007)        [one per group]
 *       HAS OBS CONTEXT  TEXT    "Tracking Identifier"
 *       HAS OBS CONTEXT  UIDREF  "Tracking Unique Identifier"
 *       CONTAINS         CODE    "Finding"                        [optional]
 *       CONTAINS         SCOORD / SCOORD3D  region                [optional]
 *                          (SCOORD) SELECTED FROM IMAGE
 *       CONTAINS         NUM     measurement                      [one per value]
 * </pre>
 * Per-measurement by-reference {@code INFERRED FROM} linking to the region is
 * deferred; the region is emitted once at group level (sufficient for display and
 * round-trip).
 *
 * @author tatsunidas
 */
public class Tid1500Writer {

	/**
	 * @param ref    a reference instance's dataset from the same study (patient/study identity).
	 * @param report the measurements to serialize.
	 * @return a complete SR dataset ready to be written and stored.
	 */
	public Attributes build(Attributes ref, MeasurementReport report) {
		Attributes sr = new Attributes();
		SrCommon.inheritIdentity(sr, ref);

		String sopClassUID = (report.hasSpatial3D() ? UID.Comprehensive3DSRStorage : UID.ComprehensiveSRStorage).uid();
		SrCommon.fillSrHeader(sr, sopClassUID, new Date(), "902");

		// Root container.
		sr.setString(Tag.ValueType, VR.CS, "CONTAINER");
		SrCommon.setConceptName(sr, SRCodes.IMAGING_MEASUREMENT_REPORT);
		sr.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");

		Sequence content = sr.newSequence(Tag.ContentSequence, 2);

		if (report.getTitle() != null && !report.getTitle().trim().isEmpty()) {
			content.add(textItem(new Code("121060", "DCM", null, "History"), report.getTitle(), "HAS CONCEPT MOD"));
		}

		// Imaging Measurements container holding every group.
		Attributes measurements = container(SRCodes.IMAGING_MEASUREMENTS, "CONTAINS");
		Sequence mseq = measurements.newSequence(Tag.ContentSequence, Math.max(1, report.getGroups().size()));
		for (MeasurementGroup g : report.getGroups()) {
			mseq.add(buildGroup(g));
		}
		content.add(measurements);

		addEvidence(sr, report);
		return sr;
	}

	private Attributes buildGroup(MeasurementGroup g) {
		Attributes group = container(SRCodes.MEASUREMENT_GROUP, "CONTAINS");
		List<Attributes> children = new ArrayList<>();

		if (g.getTrackingIdentifier() != null && !g.getTrackingIdentifier().isEmpty()) {
			children.add(textItem(SRCodes.TRACKING_IDENTIFIER, g.getTrackingIdentifier(), "HAS OBS CONTEXT"));
		}
		if (g.getTrackingUID() != null && !g.getTrackingUID().isEmpty()) {
			children.add(uidItem(SRCodes.TRACKING_UID, g.getTrackingUID(), "HAS OBS CONTEXT"));
		}
		if (g.getFinding() != null) {
			children.add(codeItem(SRCodes.FINDING, g.getFinding(), "CONTAINS"));
		}
		if (g.getRegion() != null && g.getRegion().getData().length > 0) {
			children.add(scoordItem(g.getRegion()));
		}
		for (Measurement m : g.getMeasurements()) {
			if (m.getName() != null) {
				children.add(numItem(m));
			}
		}

		Sequence cs = group.newSequence(Tag.ContentSequence, Math.max(1, children.size()));
		for (Attributes c : children) {
			cs.add(c);
		}
		return group;
	}

	// --- content item builders ---------------------------------------------------

	private Attributes container(Code concept, String relationship) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, relationship);
		ci.setString(Tag.ValueType, VR.CS, "CONTAINER");
		SrCommon.setConceptName(ci, concept);
		ci.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
		return ci;
	}

	private Attributes textItem(Code concept, String text, String relationship) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, relationship);
		ci.setString(Tag.ValueType, VR.CS, "TEXT");
		SrCommon.setConceptName(ci, concept);
		ci.setString(Tag.TextValue, VR.UT, text == null ? "" : text);
		return ci;
	}

	private Attributes uidItem(Code concept, String uid, String relationship) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, relationship);
		ci.setString(Tag.ValueType, VR.CS, "UIDREF");
		SrCommon.setConceptName(ci, concept);
		ci.setString(Tag.UID, VR.UI, uid);
		return ci;
	}

	private Attributes codeItem(Code concept, Code value, String relationship) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, relationship);
		ci.setString(Tag.ValueType, VR.CS, "CODE");
		SrCommon.setConceptName(ci, concept);
		Sequence seq = ci.newSequence(Tag.ConceptCodeSequence, 1);
		seq.add(value.toItem());
		return ci;
	}

	private Attributes numItem(Measurement m) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
		ci.setString(Tag.ValueType, VR.CS, "NUM");
		SrCommon.setConceptName(ci, m.getName());
		Sequence mvs = ci.newSequence(Tag.MeasuredValueSequence, 1);
		Attributes mv = new Attributes();
		mv.setString(Tag.NumericValue, VR.DS, formatNum(m.getValue()));
		if (m.getUnit() != null) {
			Sequence us = mv.newSequence(Tag.MeasurementUnitsCodeSequence, 1);
			us.add(m.getUnit().toItem());
		}
		mvs.add(mv);
		return ci;
	}

	private Attributes scoordItem(SpatialCoordinate region) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
		ci.setString(Tag.ValueType, VR.CS, region.isThreeD() ? "SCOORD3D" : "SCOORD");
		ci.setString(Tag.GraphicType, VR.CS, region.getGraphicType());
		ci.setFloat(Tag.GraphicData, VR.FL, region.getData());
		if (region.isThreeD()) {
			if (region.getFrameOfReferenceUID() != null) {
				ci.setString(Tag.ReferencedFrameOfReferenceUID, VR.UI, region.getFrameOfReferenceUID());
			}
		} else if (region.getImage() != null && region.getImage().getSopUID() != null) {
			Sequence cs = ci.newSequence(Tag.ContentSequence, 1);
			cs.add(imageItem(region.getImage(), "SELECTED FROM"));
		}
		return ci;
	}

	private Attributes imageItem(KeyImageRef ref, String relationship) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, relationship);
		ci.setString(Tag.ValueType, VR.CS, "IMAGE");
		Sequence rs = ci.newSequence(Tag.ReferencedSOPSequence, 1);
		Attributes refSop = new Attributes();
		refSop.setString(Tag.ReferencedSOPClassUID, VR.UI, ref.getSopClassUID());
		refSop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ref.getSopUID());
		if (ref.getFrame() > 0) {
			refSop.setInt(Tag.ReferencedFrameNumber, VR.IS, ref.getFrame());
		}
		rs.add(refSop);
		return ci;
	}

	/** Build Current Requested Procedure Evidence from every group's anchored image. */
	private void addEvidence(Attributes sr, MeasurementReport report) {
		LinkedHashMap<String, List<KeyImageRef>> bySeries = new LinkedHashMap<>();
		for (MeasurementGroup g : report.getGroups()) {
			collectImage(bySeries, g.getImage());
			if (g.getRegion() != null && !g.getRegion().isThreeD()) {
				collectImage(bySeries, g.getRegion().getImage());
			}
		}
		if (bySeries.isEmpty()) {
			return;
		}
		Sequence evidence = sr.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
		Attributes studyItem = new Attributes();
		studyItem.setString(Tag.StudyInstanceUID, VR.UI, report.getStudyUID());
		Sequence seriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, bySeries.size());
		for (Map.Entry<String, List<KeyImageRef>> e : bySeries.entrySet()) {
			Attributes seriesItem = new Attributes();
			seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, e.getKey());
			Sequence sopSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, e.getValue().size());
			for (KeyImageRef ref : e.getValue()) {
				Attributes refSop = new Attributes();
				refSop.setString(Tag.ReferencedSOPClassUID, VR.UI, ref.getSopClassUID());
				refSop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ref.getSopUID());
				sopSeq.add(refSop);
			}
			seriesSeq.add(seriesItem);
		}
		evidence.add(studyItem);
	}

	private static void collectImage(Map<String, List<KeyImageRef>> bySeries, KeyImageRef ref) {
		if (ref == null || ref.getSeriesUID() == null || ref.getSopUID() == null || ref.getSopClassUID() == null) {
			return;
		}
		List<KeyImageRef> list = bySeries.computeIfAbsent(ref.getSeriesUID(), k -> new ArrayList<>());
		for (KeyImageRef existing : list) {
			if (ref.getSopUID().equals(existing.getSopUID())) {
				return; // dedupe by SOP instance
			}
		}
		list.add(ref);
	}

	/** Format a double as a DICOM DS value (<=16 chars), trimming trailing zeros. */
	public static String formatNum(double v) {
		if (v == Math.rint(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
			return Long.toString((long) v);
		}
		String s = String.format(java.util.Locale.US, "%.4f", v);
		// trim trailing zeros but keep at least one decimal digit
		if (s.contains(".")) {
			s = s.replaceAll("0+$", "");
			if (s.endsWith(".")) {
				s = s.substring(0, s.length() - 1);
			}
		}
		return s.length() > 16 ? s.substring(0, 16) : s;
	}
}
