package com.vis.core.reporting.sr;

import org.dcm4che3.data.Code;

/**
 * Coded concepts used when building a free-text SR.
 * <p>
 * dcm4che has no high-level SR builder; concepts are plain
 * {@link org.dcm4che3.data.Code} triples (value, scheme, meaning). Where the
 * {@code org.dcm4che3.dcmr.*} context groups provide a concept it should be
 * reused; the few below are the minimum needed for a general report.
 *
 * @author tatsunidas
 */
public final class SRCodes {

	private SRCodes() {
	}

	/** Document title for the root container: LOINC 18748-4 "Diagnostic Imaging Report". */
	public static final Code DOC_TITLE_IMAGING_REPORT = new Code("18748-4", "LN", null, "Diagnostic Imaging Report");

	/** Section concept for the free-text body: DCM 121070 "Findings". */
	public static final Code FINDINGS = new Code("121070", "DCM", null, "Findings");

	/** Concept naming an embedded key image: DCM 121079 is not it; use 111036 "Mammography Breast Material"? no.
	 *  Use the generic purpose "Best illustration of finding" (DCM 111036) is CAD-specific; instead use
	 *  TID-1500-friendly DCM 121071 "Finding"? That is for measurement context. For a general report key image
	 *  the closest standard concept is DCM 113000 "Of Interest". */
	public static final Code KEY_IMAGE = new Code("113000", "DCM", null, "Of Interest");

	/** Optional report title text concept: DCM 121049 "Language of Content Item and Descendants" is unrelated.
	 *  Use DCM 121144 "Document Title Modifier"? For a simple title TEXT item we use DCM 121060 "History"? no.
	 *  We expose the report's own title via the root container concept meaning instead, so no extra code needed. */

	// --- TID 1500 Imaging Measurement Report structure ---------------------------

	/** Root container concept: DCM 126000 "Imaging Measurement Report" (TID 1500). */
	public static final Code IMAGING_MEASUREMENT_REPORT = new Code("126000", "DCM", null, "Imaging Measurement Report");
	/** Imaging Measurements container: DCM 126010 (TID 1500 row "Imaging Measurements"). */
	public static final Code IMAGING_MEASUREMENTS = new Code("126010", "DCM", null, "Imaging Measurements");
	/** Measurement Group container: DCM 125007 (TID 1410/1411). */
	public static final Code MEASUREMENT_GROUP = new Code("125007", "DCM", null, "Measurement Group");
	/** Tracking Identifier (TEXT): DCM 112039. */
	public static final Code TRACKING_IDENTIFIER = new Code("112039", "DCM", null, "Tracking Identifier");
	/** Tracking Unique Identifier (UIDREF): DCM 112040. */
	public static final Code TRACKING_UID = new Code("112040", "DCM", null, "Tracking Unique Identifier");
	/** Finding (CODE): DCM 121071. */
	public static final Code FINDING = new Code("121071", "DCM", null, "Finding");

	// --- common measured concepts (SNOMED CT) ------------------------------------

	public static final Code LENGTH = new Code("410668003", "SCT", null, "Length");
	public static final Code DIAMETER = new Code("81827009", "SCT", null, "Diameter");
	public static final Code AREA = new Code("42798000", "SCT", null, "Area");
	public static final Code VOLUME = new Code("118565006", "SCT", null, "Volume");
	public static final Code ANGLE = new Code("1483009", "SCT", null, "Angle");
	public static final Code MEAN = new Code("373098007", "SCT", null, "Mean");
	public static final Code MINIMUM = new Code("255605001", "SCT", null, "Minimum");
	public static final Code MAXIMUM = new Code("56851009", "SCT", null, "Maximum");
	public static final Code STANDARD_DEVIATION = new Code("386136009", "SCT", null, "Standard Deviation");

	// --- common units (UCUM) -----------------------------------------------------

	public static final Code U_MM = new Code("mm", "UCUM", null, "mm");
	public static final Code U_MM2 = new Code("mm2", "UCUM", null, "mm2");
	public static final Code U_MM3 = new Code("mm3", "UCUM", null, "mm3");
	public static final Code U_DEGREE = new Code("deg", "UCUM", null, "degree");
	public static final Code U_HU = new Code("[hnsf'U]", "UCUM", null, "Hounsfield unit");
	public static final Code U_NONE = new Code("1", "UCUM", null, "no units");
}
