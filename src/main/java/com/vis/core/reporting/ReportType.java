package com.vis.core.reporting;

import com.vis.dicom.UID;

/**
 * Kind of report and the DICOM SR SOP Class it is exported as.
 * <p>
 * Phase 1 supports only {@link #GENERAL} (free-text). Structured measurement
 * reports (TID 1500) are a future phase.
 *
 * @author tatsunidas
 */
public enum ReportType {

	/**
	 * General free-text report. Exported as Comprehensive SR so that key images
	 * ({@code IMAGE} content items) and (future) spatial coordinates can be embedded.
	 */
	GENERAL(UID.ComprehensiveSRStorage),

	/**
	 * TID 1500 structured measurement report. Exported as Comprehensive 3D SR so it
	 * can carry both 2D ({@code SCOORD}) and 3D ({@code SCOORD3D}) spatial
	 * coordinates; the writer ({@code Tid1500Writer}) downgrades to plain
	 * Comprehensive SR when no 3D geometry is present.
	 */
	MEASUREMENT(UID.Comprehensive3DSRStorage);

	private final UID srSopClass;

	ReportType(UID srSopClass) {
		this.srSopClass = srSopClass;
	}

	/** @return the target SR SOP Class UID enum used on finalize-as-SR. */
	public UID getSrSopClass() {
		return srSopClass;
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
