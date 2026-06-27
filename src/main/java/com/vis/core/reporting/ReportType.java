package com.vis.core.reporting;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.vis.dicom.UID;

/**
 * Kind of report: its DICOM SR SOP Class and its verification policy (which
 * {@link StaffRole} may sign it off / VERIFY).
 * <p>
 * Verification gate (GRAPHY has no login, so the gate is evaluated against the
 * role of the person set as the report's VERIFIER):
 * <ul>
 *   <li>{@link #IMAGING_DIAGNOSTIC} (画像診断) — a <b>physician</b> must verify.</li>
 *   <li>{@link #TECHNOLOGIST} (検査技師) — a <b>radiologic technologist</b> may verify.</li>
 *   <li>{@link #MEASUREMENT} / {@link #GENERAL} — no restriction.</li>
 * </ul>
 * A physician is always allowed to verify any type (treated as the senior role).
 *
 * @author tatsunidas
 */
public enum ReportType {

	/**
	 * Legacy free-text report (pre-upgrade rows). Kept for backward compatibility;
	 * exported as Comprehensive SR, no verification restriction.
	 */
	GENERAL(UID.ComprehensiveSRStorage, "Reporting.type.general", EnumSet.noneOf(StaffRole.class)),

	/**
	 * Imaging diagnostic report (画像診断レポート). Exported as Comprehensive SR.
	 * Requires a physician verifier to finalize.
	 */
	IMAGING_DIAGNOSTIC(UID.ComprehensiveSRStorage, "Reporting.type.imaging",
			EnumSet.of(StaffRole.PHYSICIAN)),

	/**
	 * Technologist report (検査技師レポート). Exported as Comprehensive SR. May be
	 * verified by a radiologic technologist (or a physician).
	 */
	TECHNOLOGIST(UID.ComprehensiveSRStorage, "Reporting.type.technologist",
			EnumSet.of(StaffRole.RADIOLOGIC_TECHNOLOGIST)),

	/**
	 * TID 1500 structured measurement report. Exported as Comprehensive 3D SR
	 * (downgraded to Comprehensive SR when no 3D geometry is present). No
	 * verification restriction.
	 */
	MEASUREMENT(UID.Comprehensive3DSRStorage, "Reporting.type.measurement",
			EnumSet.noneOf(StaffRole.class));

	private final UID srSopClass;
	private final String i18nKey;
	private final Set<StaffRole> allowedVerifierRoles;

	ReportType(UID srSopClass, String i18nKey, Set<StaffRole> allowedVerifierRoles) {
		this.srSopClass = srSopClass;
		this.i18nKey = i18nKey;
		this.allowedVerifierRoles = Collections.unmodifiableSet(allowedVerifierRoles);
	}

	/** @return the target SR SOP Class UID enum used on finalize-as-SR. */
	public UID getSrSopClass() {
		return srSopClass;
	}

	/** i18n key for the human-facing (Japanese) label. */
	public String i18nKey() {
		return i18nKey;
	}

	/** Roles explicitly allowed to verify this type (empty = no restriction). */
	public Set<StaffRole> allowedVerifierRoles() {
		return allowedVerifierRoles;
	}

	/** True when this type mandates a qualified verifier before it can be finalized. */
	public boolean requiresQualifiedVerifier() {
		return !allowedVerifierRoles.isEmpty();
	}

	/**
	 * Whether a person with the given role may verify (sign off) a report of this
	 * type. A physician may verify anything; otherwise the role must be in the
	 * allowed set (or the type has no restriction).
	 */
	public boolean canVerify(StaffRole role) {
		if (role == StaffRole.PHYSICIAN) {
			return true;
		}
		if (allowedVerifierRoles.isEmpty()) {
			return true;
		}
		return role != null && allowedVerifierRoles.contains(role);
	}

	/** Types offered to the user when creating a new report (excludes legacy GENERAL). */
	public static ReportType[] selectableTypes() {
		return new ReportType[] { IMAGING_DIAGNOSTIC, TECHNOLOGIST, MEASUREMENT };
	}

	public static ReportType fromName(String name) {
		if (name != null) {
			for (ReportType t : values()) {
				if (t.name().equals(name)) {
					return t;
				}
			}
		}
		return GENERAL;
	}
}
