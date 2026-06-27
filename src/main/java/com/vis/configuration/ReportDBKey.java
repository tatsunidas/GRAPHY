package com.vis.configuration;

/**
 * Context (column) keys for the REPORT table.
 * <p>
 * Mirrors the {@link RoiDBKey} pattern: a report is persisted as a
 * {@code HashMap<String,Object>} keyed by these names. The keys match the
 * column names in {@code sql/REPORT.sql}.
 * <p>
 * The {@code DatabaseHandler} stays decoupled from the {@code com.vis.core.reporting}
 * classes: rich text and key-image references are passed as plain {@code String}
 * (HTML / JSON) through this map.
 *
 * @author tatsunidas
 */
public enum ReportDBKey {
	ReportID,
	Title,
	Status, // DRAFT, FINAL, ADDENDUM
	ReportType, // see com.vis.core.reporting.ReportType
	Author,
	ReferringPhysician,
	ClinicalHistory,
	BodyHtml, // report body (Markdown or HTML); see BodyFormat
	BodyFormat, // "md" (default) or "html" for legacy records
	KeyImageRefs, // JSON (Gson) of List<KeyImageRef>
	SrSopInstanceUID, // filled on finalize-as-SR (nullable)
	StudyDate,
	CreatedDateTime, // epoch millis (Long)
	ModifiedDateTime, // epoch millis (Long)
	PredecessorReportId, // nullable; set on addendum
	PredecessorSrSopUID, // nullable; SOP UID of predecessor SR for addendum
	PredecessorSeriesUID, // nullable; series UID of predecessor SR for addendum
	LockedBy, // nullable; user who has the report open for editing
	LockedAt, // nullable; when the lock was acquired (epoch millis)
	PatientID,
	StudyInstanceUID,
	SeriesInstanceUID,
	KoSopInstanceUID,      // SOP UID of the KO object linked to this report (set on finalize)
	KoSeriesInstanceUID    // series UID of the linked KO object
	;

	public static boolean checkPropertyKey(String key) {
		for (ReportDBKey k : ReportDBKey.values()) {
			if (k.name().equals(key)) {
				return true;
			}
		}
		return false;
	}
}
