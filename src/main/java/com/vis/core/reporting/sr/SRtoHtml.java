package com.vis.core.reporting.sr;

import java.util.HashMap;
import java.util.Map;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;

import com.vis.core.reporting.KeyImageRef;

/**
 * Renders any SR-family dataset (free-text SR, RDSR, KO, ...) to HTML by walking
 * the {@link ContentItem} tree. Modality-agnostic — RDSR needs no special casing.
 * <p>
 * IMAGE content items are emitted as {@code graphy://image/...} anchors so the
 * viewer can perform object retrieval; the study/series for each referenced SOP
 * is resolved from the SR's Evidence Sequences.
 *
 * @author tatsunidas
 */
public final class SRtoHtml {

	private SRtoHtml() {
	}

	/** @return the document title (root concept meaning), or a sensible default. */
	public static String documentTitle(Attributes sr) {
		Attributes cn = sr.getNestedDataset(Tag.ConceptNameCodeSequence);
		if (cn != null) {
			String m = cn.getString(Tag.CodeMeaning);
			if (m != null && !m.isEmpty()) {
				return m;
			}
		}
		return "Structured Report";
	}

	public static String toHtml(Attributes sr) {
		Map<String, String[]> evidence = buildEvidenceMap(sr);
		ContentItem root = SRReader.read(sr);

		StringBuilder sb = new StringBuilder();
		sb.append("<html><head><meta charset=\"UTF-8\"></head>");
		sb.append("<body style=\"font-family:sans-serif;font-size:12px;color:#202020;\">");

		// header
		sb.append("<h2 style=\"margin-bottom:2px;\">").append(HtmlText.escape(documentTitle(sr))).append("</h2>");
		sb.append("<table style=\"font-size:11px;color:#505050;\">");
		row(sb, "Patient", sr.getString(Tag.PatientName));
		row(sb, "Patient ID", sr.getString(Tag.PatientID));
		row(sb, "Study Date", com.vis.core.util.DateUtils.toDisplayDate(sr.getString(Tag.StudyDate)));
		row(sb, "Modality", sr.getString(Tag.Modality));
		row(sb, "Completion", sr.getString(Tag.CompletionFlag));
		row(sb, "Verification", sr.getString(Tag.VerificationFlag));
		sb.append("</table>");

		appendParticipants(sb, sr);
		sb.append("<hr>");

		// content tree (skip the root container's own concept; render its children)
		for (ContentItem child : root.getChildren()) {
			renderItem(sb, child, evidence, 0);
		}

		sb.append("</body></html>");
		return sb.toString();
	}

	private static void renderItem(StringBuilder sb, ContentItem ci, Map<String, String[]> evidence, int depth) {
		String vt = ci.getValueType() == null ? "" : ci.getValueType();
		String label = ci.conceptMeaning();

		switch (vt) {
		case "CONTAINER":
			if (!label.isEmpty()) {
				sb.append("<h3 style=\"margin:8px 0 2px 0;\">").append(HtmlText.escape(label)).append("</h3>");
			}
			break;
		case "TEXT":
			sb.append("<p style=\"margin:2px 0;\">");
			if (!label.isEmpty()) {
				sb.append("<b>").append(HtmlText.escape(label)).append(": </b>");
			}
			sb.append(HtmlText.escapeMultiline(ci.getTextValue()));
			sb.append("</p>");
			break;
		case "NUM":
			sb.append("<p style=\"margin:2px 0;\"><b>").append(HtmlText.escape(label)).append(": </b>")
					.append(HtmlText.escape(ci.getNumericValue()));
			if (ci.getUnit() != null && ci.getUnit().getCodeMeaning() != null) {
				sb.append(' ').append(HtmlText.escape(ci.getUnit().getCodeMeaning()));
			}
			sb.append("</p>");
			break;
		case "CODE":
			sb.append("<p style=\"margin:2px 0;\"><b>").append(HtmlText.escape(label)).append(": </b>")
					.append(HtmlText.escape(ci.getCode() == null ? "" : ci.getCode().getCodeMeaning())).append("</p>");
			break;
		case "DATETIME":
		case "DATE":
		case "TIME":
			sb.append("<p style=\"margin:2px 0;\"><b>").append(HtmlText.escape(label)).append(": </b>")
					.append(HtmlText.escape(ci.getDateTime())).append("</p>");
			break;
		case "PNAME":
			sb.append("<p style=\"margin:2px 0;\"><b>").append(HtmlText.escape(label)).append(": </b>")
					.append(HtmlText.escape(ci.getPersonName())).append("</p>");
			break;
		case "UIDREF":
			sb.append("<p style=\"margin:2px 0;\"><b>").append(HtmlText.escape(label)).append(": </b>")
					.append(HtmlText.escape(ci.getUidRef())).append("</p>");
			break;
		case "IMAGE":
		case "COMPOSITE":
			renderImage(sb, ci, evidence, label);
			break;
		case "SCOORD":
		case "SCOORD3D":
			renderScoord(sb, ci);
			break;
		default:
			if (!label.isEmpty()) {
				sb.append("<p style=\"margin:2px 0;\"><b>").append(HtmlText.escape(label)).append("</b></p>");
			}
			break;
		}

		// children, slightly indented
		if (!ci.getChildren().isEmpty()) {
			sb.append("<div style=\"margin-left:14px;\">");
			for (ContentItem child : ci.getChildren()) {
				renderItem(sb, child, evidence, depth + 1);
			}
			sb.append("</div>");
		}
	}

	private static void renderImage(StringBuilder sb, ContentItem ci, Map<String, String[]> evidence, String label) {
		String sop = ci.getRefSopInstanceUID();
		String study = "";
		String series = "";
		if (sop != null && evidence.containsKey(sop)) {
			String[] ss = evidence.get(sop);
			study = ss[0] == null ? "" : ss[0];
			series = ss[1] == null ? "" : ss[1];
		}
		KeyImageRef ref = new KeyImageRef(study, series, sop, ci.getRefSopClassUID(),
				label.isEmpty() ? "Key image" : label);
		String text = label.isEmpty() ? "Key image" : label;
		sb.append("<p style=\"margin:2px 0;\"><a href=\"").append(ref.toHref()).append("\">")
				.append(HtmlText.escape(text)).append("</a></p>");
	}

	/** Render a spatial coordinate (SCOORD / SCOORD3D) as a compact descriptive line. */
	private static void renderScoord(StringBuilder sb, ContentItem ci) {
		boolean is3d = "SCOORD3D".equals(ci.getValueType());
		float[] data = ci.getGraphicData();
		int per = is3d ? 3 : 2;
		int points = data == null ? 0 : data.length / per;
		String gt = ci.getGraphicType() == null ? "" : ci.getGraphicType();
		sb.append("<p style=\"margin:2px 0;color:#707070;font-size:11px;\">")
				.append(is3d ? "◳ 3D region " : "▭ region ")
				.append(HtmlText.escape(gt)).append(" (").append(points).append(points == 1 ? " point" : " points")
				.append(")");
		if (is3d && ci.getReferencedFrameOfReferenceUID() != null) {
			sb.append(" · FoR ").append(HtmlText.escape(ci.getReferencedFrameOfReferenceUID()));
		}
		sb.append("</p>");
	}

	// --- observers / participants attribution -----------------------------------
	private static final int TAG_VERIFYING_OBSERVER_SEQ = 0x0040A073;
	private static final int TAG_VERIFICATION_DATETIME = 0x0040A030;
	private static final int TAG_VERIFYING_OBSERVER_NAME = 0x0040A075;
	private static final int TAG_AUTHOR_OBSERVER_SEQ = 0x0040A078;
	private static final int TAG_PARTICIPANT_SEQ = 0x0040A07A;
	private static final int TAG_PARTICIPATION_TYPE = 0x0040A080;
	private static final int TAG_PERSON_NAME = 0x0040A123;
	private static final int TAG_ORGANIZATIONAL_ROLE_CODE_SEQ = 0x0044010A;

	/**
	 * Render the report's people — who authored / verified / entered / reviewed it,
	 * and each one's job role — from the SR header observer/participant sequences.
	 */
	private static void appendParticipants(StringBuilder sb, Attributes sr) {
		StringBuilder rows = new StringBuilder();
		// Author Observer Sequence
		Sequence authors = sr.getSequence(TAG_AUTHOR_OBSERVER_SEQ);
		if (authors != null) {
			for (Attributes a : authors) {
				participantRow(rows, "Author", a.getString(TAG_PERSON_NAME), roleMeaning(a), null);
			}
		}
		// Verifying Observer Sequence
		Sequence verifiers = sr.getSequence(TAG_VERIFYING_OBSERVER_SEQ);
		if (verifiers != null) {
			for (Attributes v : verifiers) {
				participantRow(rows, "Verifier", v.getString(TAG_VERIFYING_OBSERVER_NAME),
						roleMeaning(v), v.getString(TAG_VERIFICATION_DATETIME));
			}
		}
		// Participant Sequence (enterers / reviewers)
		Sequence participants = sr.getSequence(TAG_PARTICIPANT_SEQ);
		if (participants != null) {
			for (Attributes p : participants) {
				participantRow(rows, participationLabel(p.getString(TAG_PARTICIPATION_TYPE)),
						p.getString(TAG_PERSON_NAME), roleMeaning(p), null);
			}
		}
		if (rows.length() == 0) {
			return;
		}
		sb.append("<table style=\"font-size:11px;color:#404040;border-collapse:collapse;margin-top:4px;\">");
		sb.append("<tr style=\"color:#808080;\"><td><b>&nbsp;</b></td><td><b>Name</b></td>")
				.append("<td><b>&nbsp;Role&nbsp;</b></td><td><b>&nbsp;Date</b></td></tr>");
		sb.append(rows);
		sb.append("</table>");
	}

	private static void participantRow(StringBuilder sb, String kind, String name, String role, String dateTime) {
		if (name == null || name.isEmpty()) {
			return;
		}
		sb.append("<tr><td><b>").append(HtmlText.escape(kind)).append("</b></td>")
				.append("<td>&nbsp;").append(HtmlText.escape(name.replace('^', ' '))).append("</td>")
				.append("<td>&nbsp;").append(HtmlText.escape(role == null ? "" : role)).append("&nbsp;</td>")
				.append("<td>&nbsp;").append(HtmlText.escape(dateTime == null ? "" : dateTime)).append("</td></tr>");
	}

	private static String roleMeaning(Attributes item) {
		Attributes roleItem = item.getNestedDataset(TAG_ORGANIZATIONAL_ROLE_CODE_SEQ);
		return roleItem == null ? null : roleItem.getString(Tag.CodeMeaning);
	}

	private static String participationLabel(String term) {
		if ("ENT".equals(term)) {
			return "Enterer";
		}
		if ("ATTEST".equals(term)) {
			return "Reviewer";
		}
		return term == null ? "Participant" : term;
	}

	private static void row(StringBuilder sb, String key, String value) {
		if (value == null || value.isEmpty()) {
			return;
		}
		sb.append("<tr><td><b>").append(HtmlText.escape(key)).append("</b></td><td>&nbsp;")
				.append(HtmlText.escape(value)).append("</td></tr>");
	}

	/** sopInstanceUID -&gt; [studyUID, seriesUID] from the SR Evidence Sequences. */
	private static Map<String, String[]> buildEvidenceMap(Attributes sr) {
		Map<String, String[]> map = new HashMap<>();
		collectEvidence(sr.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence), map);
		collectEvidence(sr.getSequence(Tag.PertinentOtherEvidenceSequence), map);
		return map;
	}

	private static void collectEvidence(Sequence studies, Map<String, String[]> map) {
		if (studies == null) {
			return;
		}
		for (Attributes studyItem : studies) {
			String studyUID = studyItem.getString(Tag.StudyInstanceUID);
			Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
			if (seriesSeq == null) {
				continue;
			}
			for (Attributes seriesItem : seriesSeq) {
				String seriesUID = seriesItem.getString(Tag.SeriesInstanceUID);
				Sequence sopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
				if (sopSeq == null) {
					continue;
				}
				for (Attributes sopItem : sopSeq) {
					String sop = sopItem.getString(Tag.ReferencedSOPInstanceUID);
					if (sop != null) {
						map.put(sop, new String[] { studyUID, seriesUID });
					}
				}
			}
		}
	}
}
