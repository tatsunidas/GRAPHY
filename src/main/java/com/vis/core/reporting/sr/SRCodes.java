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
}
