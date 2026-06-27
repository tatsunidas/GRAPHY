package com.vis.core.reporting;

/**
 * How a person is involved in a report, modelled on the DICOM SR participation
 * mechanisms. Each value maps to a specific DICOM construct:
 * <ul>
 *   <li>{@link #AUTHOR} → Author Observer Sequence (0040,A078)</li>
 *   <li>{@link #VERIFIER} → Verifying Observer Sequence (0040,A073) — sets
 *       {@code VerificationFlag=VERIFIED} on finalize</li>
 *   <li>{@link #ENTERER} → Participant Sequence (0040,A07A), Participation Type
 *       (0040,A080) = {@code ENT} (data enterer / transcriptionist)</li>
 *   <li>{@link #REVIEWER} → Participant Sequence (0040,A07A), Participation Type
 *       (0040,A080) = {@code ATTEST} (attestor); DICOM has no "REVIEWER" defined
 *       term, ATTEST is its closest standard role</li>
 * </ul>
 *
 * @author tatsunidas
 */
public enum ParticipationType {

	AUTHOR(null, "Reporting.participation.author"),
	VERIFIER(null, "Reporting.participation.verifier"),
	ENTERER("ENT", "Reporting.participation.enterer"),
	REVIEWER("ATTEST", "Reporting.participation.reviewer");

	/** Participation Type (0040,A080) defined term when this maps to Participant Sequence, else null. */
	private final String participantTerm;
	private final String i18nKey;

	ParticipationType(String participantTerm, String i18nKey) {
		this.participantTerm = participantTerm;
		this.i18nKey = i18nKey;
	}

	/**
	 * @return the (0040,A080) Participation Type defined term ("ENT"/"ATTEST") for
	 *         types that go into the Participant Sequence, or {@code null} for
	 *         AUTHOR/VERIFIER which use their own dedicated sequences.
	 */
	public String participantTerm() {
		return participantTerm;
	}

	public String i18nKey() {
		return i18nKey;
	}

	/** Resolve by enum name; defaults to {@link #AUTHOR} for null/unknown. */
	public static ParticipationType fromName(String name) {
		if (name != null) {
			for (ParticipationType p : values()) {
				if (p.name().equals(name)) {
					return p;
				}
			}
		}
		return AUTHOR;
	}
}
