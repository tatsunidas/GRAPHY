package com.vis.core.reporting;

/**
 * Job/occupation of a person involved in a report ("職種"), modelled on the DICOM
 * "Organizational Role" concept (CID 7452). Each role carries the DICOM coded
 * triple it is exported as inside an {@code Organizational Role Code Sequence}
 * (0044,010A) item; the two roles that exist in CID 7452 use the standard SNOMED
 * codes, the three that do not are encoded with the private scheme
 * {@code 99GRAPHY} (a conformant, site-specific coding scheme designator) and can
 * be swapped for verified standard codes by editing this single enum.
 * <p>
 * Plain {@code String} code parts are stored here so {@code com.vis.core.reporting}
 * stays decoupled from dcm4che; the {@code sr} package turns them into a
 * {@code org.dcm4che3.data.Code}.
 *
 * @author tatsunidas
 */
public enum StaffRole {

	/** 読影医 — CID 7452 Physician. */
	PHYSICIAN("309343006", "SCT", "Physician", "Reporting.role.physician"),

	/** 放射線技師 — CID 7452 Radiologic Technologist. */
	RADIOLOGIC_TECHNOLOGIST("159016003", "SCT", "Radiologic Technologist", "Reporting.role.technologist"),

	/** 医療助手 — not in CID 7452; private scheme. */
	MEDICAL_ASSISTANT("MEDASSIST", "99GRAPHY", "Medical Assistant", "Reporting.role.assistant"),

	/** 事務職員 — not in CID 7452; private scheme. */
	CLERICAL_WORKER("CLERK", "99GRAPHY", "Clerical Worker", "Reporting.role.clerk"),

	/** 研究者 — not in CID 7452; private scheme. */
	SCIENTIST("SCIENTIST", "99GRAPHY", "Scientist", "Reporting.role.scientist");

	private final String codeValue;
	private final String codeScheme;
	private final String codeMeaning;
	private final String i18nKey;

	StaffRole(String codeValue, String codeScheme, String codeMeaning, String i18nKey) {
		this.codeValue = codeValue;
		this.codeScheme = codeScheme;
		this.codeMeaning = codeMeaning;
		this.i18nKey = i18nKey;
	}

	public String codeValue() {
		return codeValue;
	}

	public String codeScheme() {
		return codeScheme;
	}

	public String codeMeaning() {
		return codeMeaning;
	}

	/** i18n key for the human-facing (Japanese) label. */
	public String i18nKey() {
		return i18nKey;
	}

	/** Resolve by enum name; returns {@code null} for null/unknown (role is optional). */
	public static StaffRole fromName(String name) {
		if (name != null) {
			for (StaffRole r : values()) {
				if (r.name().equals(name)) {
					return r;
				}
			}
		}
		return null;
	}
}
