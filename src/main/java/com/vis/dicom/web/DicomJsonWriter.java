package com.vis.dicom.web;

import java.util.List;
import java.util.Map;

/**
 * Minimal, hand-written DICOM JSON Model (PS3.18 Annex F) serializer for
 * QIDO-RS/STOW-RS responses. Deliberately not a general-purpose
 * {@code org.dcm4che3.data.Attributes} serializer (that would need the
 * dcm4che-json module, which pulls in a new jakarta.json dependency): the
 * tag set returned by GRAPHY's existing search queries is small and fixed,
 * so this only knows how to render exactly those tags, straight from the
 * {@code HashMap<String,String>} rows the DB layer already returns.
 *
 * Format per tag: {"GGGGEEEE":{"vr":"XX","Value":[...]}}; the "Value" key is
 * omitted entirely when there's no value, per PS3.18 Annex F.2.5.
 *
 * @author tatsunidas
 */
public class DicomJsonWriter {

	private DicomJsonWriter() {
	}

	public static String studiesToJsonArray(List<Map<String, String>> studyRows) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < studyRows.size(); i++) {
			if (i > 0) {
				sb.append(",");
			}
			appendStudy(sb, studyRows.get(i));
		}
		return sb.append("]").toString();
	}

	public static String seriesToJsonArray(List<Map<String, String>> seriesRows) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < seriesRows.size(); i++) {
			if (i > 0) {
				sb.append(",");
			}
			appendSeries(sb, seriesRows.get(i));
		}
		return sb.append("]").toString();
	}

	public static String instancesToJsonArray(List<Map<String, String>> instanceRows) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < instanceRows.size(); i++) {
			if (i > 0) {
				sb.append(",");
			}
			appendInstance(sb, instanceRows.get(i));
		}
		return sb.append("]").toString();
	}

	/** STOW-RS response body: one dataset with ReferencedSOPSequence/FailedSOPSequence. */
	public static String stowResponse(List<String[]> succeeded /* {sopClassUID, sopInstanceUID} */,
			List<String> failedSopInstanceUIDs) {
		StringBuilder sb = new StringBuilder("{");
		boolean any = false;
		if (!succeeded.isEmpty()) {
			sb.append("\"00081199\":{\"vr\":\"SQ\",\"Value\":["); // ReferencedSOPSequence
			for (int i = 0; i < succeeded.size(); i++) {
				if (i > 0) {
					sb.append(",");
				}
				String[] s = succeeded.get(i);
				sb.append("{");
				appendStringAttr(sb, "00081150", "UI", s[0]); // ReferencedSOPClassUID
				sb.append(",");
				appendStringAttr(sb, "00081155", "UI", s[1]); // ReferencedSOPInstanceUID
				sb.append("}");
			}
			sb.append("]}");
			any = true;
		}
		if (!failedSopInstanceUIDs.isEmpty()) {
			if (any) {
				sb.append(",");
			}
			sb.append("\"00081198\":{\"vr\":\"SQ\",\"Value\":["); // FailedSOPSequence
			for (int i = 0; i < failedSopInstanceUIDs.size(); i++) {
				if (i > 0) {
					sb.append(",");
				}
				sb.append("{");
				appendStringAttr(sb, "00081155", "UI", failedSopInstanceUIDs.get(i)); // ReferencedSOPInstanceUID
				sb.append("}");
			}
			sb.append("]}");
		}
		sb.append("}");
		return sb.toString();
	}

	private static void appendStudy(StringBuilder sb, Map<String, String> row) {
		sb.append("{");
		appendStringAttr(sb, "0020000D", "UI", row.get("StudyInstanceUID")); // StudyInstanceUID
		sb.append(",");
		appendStringAttr(sb, "00100020", "LO", row.get("PatientID")); // PatientID
		sb.append(",");
		appendPnAttr(sb, "00100010", row.get("PatientName")); // PatientName
		sb.append(",");
		appendStringAttr(sb, "00100030", "DA", row.get("PatientBirthDate")); // PatientBirthDate
		sb.append(",");
		appendStringAttr(sb, "00080020", "DA", row.get("StudyDate")); // StudyDate
		sb.append(",");
		appendStringAttr(sb, "00080030", "TM", row.get("StudyTime")); // StudyTime
		sb.append(",");
		appendStringAttr(sb, "00080050", "SH", row.get("AccessionNumber")); // AccessionNumber
		sb.append(",");
		appendStringAttr(sb, "00081030", "LO", row.get("StudyDescription")); // StudyDescription
		sb.append(",");
		appendStringAttr(sb, "00080061", "CS", row.get("ModalitiesInStudy")); // ModalitiesInStudy
		sb.append(",");
		appendNumericAttr(sb, "00201206", "IS", row.get("NoOfSeries")); // NumberOfStudyRelatedSeries
		sb.append(",");
		appendNumericAttr(sb, "00201208", "IS", row.get("NoOfInstances")); // NumberOfStudyRelatedInstances
		sb.append("}");
	}

	private static void appendSeries(StringBuilder sb, Map<String, String> row) {
		sb.append("{");
		appendStringAttr(sb, "0020000D", "UI", row.get("StudyInstanceUID")); // StudyInstanceUID
		sb.append(",");
		appendStringAttr(sb, "0020000E", "UI", row.get("SeriesInstanceUID")); // SeriesInstanceUID
		sb.append(",");
		appendStringAttr(sb, "00080060", "CS", row.get("Modality")); // Modality
		sb.append(",");
		appendNumericAttr(sb, "00200011", "IS", row.get("SeriesNumber")); // SeriesNumber
		sb.append(",");
		appendStringAttr(sb, "0008103E", "LO", row.get("SeriesDescription")); // SeriesDescription
		sb.append(",");
		appendNumericAttr(sb, "00201209", "IS", row.get("NumOfInstanceInSeries")); // NumberOfSeriesRelatedInstances
		sb.append("}");
	}

	private static void appendInstance(StringBuilder sb, Map<String, String> row) {
		sb.append("{");
		appendStringAttr(sb, "0020000D", "UI", row.get("StudyInstanceUID")); // StudyInstanceUID
		sb.append(",");
		appendStringAttr(sb, "0020000E", "UI", row.get("SeriesInstanceUID")); // SeriesInstanceUID
		sb.append(",");
		appendStringAttr(sb, "00080018", "UI", row.get("SOPInstanceUID")); // SOPInstanceUID
		sb.append(",");
		appendStringAttr(sb, "00080016", "UI", row.get("SOPClassUID")); // SOPClassUID (absent for some sources, ok if null)
		sb.append(",");
		appendNumericAttr(sb, "00200013", "IS", row.get("InstanceNumber")); // InstanceNumber
		sb.append("}");
	}

	private static void appendStringAttr(StringBuilder sb, String tag, String vr, String value) {
		sb.append("\"").append(tag).append("\":{\"vr\":\"").append(vr).append("\"");
		if (value != null && !value.isEmpty()) {
			sb.append(",\"Value\":[\"").append(escape(value)).append("\"]");
		}
		sb.append("}");
	}

	private static void appendPnAttr(StringBuilder sb, String tag, String value) {
		sb.append("\"").append(tag).append("\":{\"vr\":\"PN\"");
		if (value != null && !value.isEmpty()) {
			sb.append(",\"Value\":[{\"Alphabetic\":\"").append(escape(value)).append("\"}]");
		}
		sb.append("}");
	}

	private static void appendNumericAttr(StringBuilder sb, String tag, String vr, String value) {
		sb.append("\"").append(tag).append("\":{\"vr\":\"").append(vr).append("\"");
		if (value != null && !value.isEmpty()) {
			try {
				long n = Long.parseLong(value.trim());
				sb.append(",\"Value\":[").append(n).append("]");
			} catch (NumberFormatException e) {
				// not actually numeric (unexpected for IS, but don't fail the whole response over it)
				sb.append(",\"Value\":[\"").append(escape(value)).append("\"]");
			}
		}
		sb.append("}");
	}

	private static String escape(String s) {
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"':
					out.append("\\\"");
					break;
				case '\\':
					out.append("\\\\");
					break;
				case '\n':
					out.append("\\n");
					break;
				case '\r':
					out.append("\\r");
					break;
				case '\t':
					out.append("\\t");
					break;
				default:
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
			}
		}
		return out.toString();
	}
}
