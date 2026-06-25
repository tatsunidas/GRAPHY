package com.vis.core.reporting.sr;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.vis.dicom.UID;

/**
 * Helpers to recognise the DICOM SR Document family (SR, RDSR, KO, CAD SR, ...).
 * Used by the viewer routing guard (to keep SR objects out of the image viewer)
 * and by the SR viewer.
 *
 * @author tatsunidas
 */
public final class SopClassUtil {

	private static final Set<String> SR_FAMILY;

	static {
		Set<String> s = new HashSet<>();
		add(s, UID.ComprehensiveSRStorageTrial);
		add(s, UID.BasicTextSRStorage);
		add(s, UID.EnhancedSRStorage);
		add(s, UID.ComprehensiveSRStorage);
		add(s, UID.Comprehensive3DSRStorage);
		add(s, UID.ExtensibleSRStorage);
		add(s, UID.ProcedureLogStorage);
		add(s, UID.MammographyCADSRStorage);
		add(s, UID.KeyObjectSelectionDocumentStorage);
		add(s, UID.ChestCADSRStorage);
		add(s, UID.XRayRadiationDoseSRStorage);
		add(s, UID.RadiopharmaceuticalRadiationDoseSRStorage);
		add(s, UID.ColonCADSRStorage);
		SR_FAMILY = Collections.unmodifiableSet(s);
	}

	private SopClassUtil() {
	}

	private static void add(Set<String> s, UID uid) {
		if (uid != null) {
			s.add(uid.uid());
		}
	}

	/**
	 * @return true if the given SOP Class UID belongs to the SR Document family
	 *         (so it must be routed to the SR HTML viewer, not the image viewer).
	 */
	public static boolean isSrFamily(String sopClassUID) {
		return sopClassUID != null && SR_FAMILY.contains(sopClassUID.trim());
	}

	/** @return the immutable set of SR-family SOP Class UIDs (e.g. for a SQL IN clause). */
	public static Set<String> srFamilyUids() {
		return SR_FAMILY;
	}

	/**
	 * @return a short display label for an SR-family SOP Class UID ("RDSR", "KO", or
	 *         "Report"), or {@code null} if it is not an SR-family class.
	 */
	public static String reportTypeLabel(String sopClassUID) {
		if (UID.XRayRadiationDoseSRStorage.uid().equals(sopClassUID)
				|| UID.RadiopharmaceuticalRadiationDoseSRStorage.uid().equals(sopClassUID)) {
			return "RDSR";
		}
		if (UID.KeyObjectSelectionDocumentStorage.uid().equals(sopClassUID)) {
			return "KO";
		}
		if (isSrFamily(sopClassUID)) {
			return "Report";
		}
		return null;
	}

	/** Fallback detection by DICOM Modality (0008,0060) value, e.g. "SR" or "KO". */
	public static boolean isSrModality(String modality) {
		if (modality == null) {
			return false;
		}
		String m = modality.trim().toUpperCase();
		return m.equals("SR") || m.equals("KO") || m.equals("DOC");
	}
}
