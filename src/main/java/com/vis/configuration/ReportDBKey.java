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
	Status, // DRAFT, FINAL
	ReportType, // see com.vis.core.reporting.ReportType
	Author,
	BodyHtml, // editable rich text (HTML), source of truth
	KeyImageRefs, // JSON (Gson) of List<KeyImageRef>
	SrSopInstanceUID, // filled on finalize-as-SR (nullable)
	StudyDate,
	CreatedDateTime, // epoch millis (Long)
	ModifiedDateTime, // epoch millis (Long)
	PatientID,
	StudyInstanceUID,
	SeriesInstanceUID
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
