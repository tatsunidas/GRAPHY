package com.vis.core.reporting.sr;

import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.ReportDocument;

/**
 * Builds a DICOM Comprehensive SR ({@code Attributes} tree) from a
 * {@link ReportDocument}. dcm4che provides no high-level SR builder, so the
 * content tree (ValueType / ConceptNameCodeSequence / RelationshipType /
 * ContentSequence) is constructed by hand.
 * <p>
 * Patient and Study identity are inherited from a reference instance in the same
 * study so the resulting SR registers under the correct patient/study. Series and
 * SOP Instance UIDs are freshly generated.
 *
 * @author tatsunidas
 */
public class SRWriter {

	/**
	 * @param ref a reference instance's dataset from the same study (for patient/study identity).
	 * @param doc the report to serialize.
	 * @return a complete SR dataset ready to be written and stored.
	 */
	public Attributes build(Attributes ref, ReportDocument doc) {
		Attributes sr = new Attributes();
		SrCommon.inheritIdentity(sr, ref);

		String sopClassUID = doc.getType().getSrSopClass().uid();
		SrCommon.fillSrHeader(sr, sopClassUID, new Date(), "901");

		// SR Document Content module (root container)
		sr.setString(Tag.ValueType, VR.CS, "CONTAINER");
		setConceptName(sr, SRCodes.DOC_TITLE_IMAGING_REPORT);
		sr.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");

		Sequence content = sr.newSequence(Tag.ContentSequence, 4);

		// Title as the first text item (so it survives round-trip and is visible in HTML).
		if (doc.getTitle() != null && !doc.getTitle().trim().isEmpty()) {
			content.add(textItem(new Code("121060", "DCM", null, "History"), doc.getTitle()));
		}

		// Findings: the plain-text rendering of the report body.
		String body = HtmlText.toPlainText(doc.getBodyHtml());
		content.add(textItem(SRCodes.FINDINGS, body));

		// Key images as IMAGE content items.
		if (doc.getKeyImages() != null) {
			for (KeyImageRef ref0 : doc.getKeyImages()) {
				if (ref0.getSopUID() != null && ref0.getSopClassUID() != null) {
					content.add(imageItem(ref0));
				}
			}
		}

		// Evidence: list referenced composite SOP instances so IMAGE references resolve.
		addEvidence(sr, doc);

		return sr;
	}

	private Attributes textItem(Code concept, String text) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
		ci.setString(Tag.ValueType, VR.CS, "TEXT");
		setConceptName(ci, concept);
		ci.setString(Tag.TextValue, VR.UT, text == null ? "" : text);
		return ci;
	}

	private Attributes imageItem(KeyImageRef ref) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
		ci.setString(Tag.ValueType, VR.CS, "IMAGE");
		setConceptName(ci, SRCodes.KEY_IMAGE);
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

	/**
	 * Build Current Requested Procedure Evidence Sequence grouping the referenced
	 * key images by study and series.
	 */
	private void addEvidence(Attributes sr, ReportDocument doc) {
		if (doc.getKeyImages() == null || doc.getKeyImages().isEmpty()) {
			return;
		}
		Sequence evidence = sr.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
		// Single study assumption for Phase 1: group by series.
		Attributes studyItem = new Attributes();
		studyItem.setString(Tag.StudyInstanceUID, VR.UI, doc.getStudyUID());
		Sequence seriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, 1);

		java.util.LinkedHashMap<String, java.util.List<KeyImageRef>> bySeries = new java.util.LinkedHashMap<>();
		for (KeyImageRef ref : doc.getKeyImages()) {
			if (ref.getSeriesUID() == null || ref.getSopUID() == null || ref.getSopClassUID() == null) {
				continue;
			}
			bySeries.computeIfAbsent(ref.getSeriesUID(), k -> new java.util.ArrayList<>()).add(ref);
		}
		for (java.util.Map.Entry<String, java.util.List<KeyImageRef>> e : bySeries.entrySet()) {
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

	private static void setConceptName(Attributes item, Code code) {
		SrCommon.setConceptName(item, code);
	}
}
