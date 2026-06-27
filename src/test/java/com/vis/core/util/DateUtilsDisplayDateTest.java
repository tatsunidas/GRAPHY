package com.vis.core.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Locks the canonical StudyDate display normalization: every representation used
 * across GRAPHY (DICOM {@code yyyyMMdd}, SQL/ISO {@code yyyy-MM-dd}, display
 * {@code yyyy/MM/dd}, leading date-time, java.sql.Date) collapses to the single
 * canonical form {@code yyyy/MM/dd}; invalid / empty / null become {@code ""}.
 */
public class DateUtilsDisplayDateTest {

	@Test
	public void normalizesEveryStringRepresentationToCanonical() {
		assertEquals("2026/01/02", DateUtils.toDisplayDate("20260102")); // DICOM yyyyMMdd
		assertEquals("2026/01/02", DateUtils.toDisplayDate("2026-01-02")); // SQL/ISO
		assertEquals("2026/01/02", DateUtils.toDisplayDate("2026/01/02")); // already canonical
		assertEquals("2026/01/02", DateUtils.toDisplayDate("  20260102 ")); // trims
		assertEquals("2026/01/02", DateUtils.toDisplayDate("20260102123045")); // leading date-time
	}

	@Test
	public void invalidAndEmptyBecomeEmptyString() {
		assertEquals("", DateUtils.toDisplayDate((String) null));
		assertEquals("", DateUtils.toDisplayDate(""));
		assertEquals("", DateUtils.toDisplayDate("   "));
		assertEquals("", DateUtils.toDisplayDate("0000/00/00")); // old bogus fallback -> empty
		assertEquals("", DateUtils.toDisplayDate("2026")); // too short
		assertEquals("", DateUtils.toDisplayDate("notadate"));
	}

	@Test
	public void formatsSqlDate() {
		java.sql.Date sd = java.sql.Date.valueOf("2026-01-02");
		assertEquals("2026/01/02", DateUtils.toDisplayDate(sd));
		assertEquals("", DateUtils.toDisplayDate((java.util.Date) null));
	}
}
