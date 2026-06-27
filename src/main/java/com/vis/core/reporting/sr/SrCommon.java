package com.vis.core.reporting.sr;

import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import com.vis.dicom.UIDUtils;

/**
 * Shared building blocks for constructing SR-family datasets by hand (dcm4che has
 * no high-level SR builder). Used by both the free-text {@link SRWriter} and the
 * TID 1500 {@link Tid1500Writer}.
 *
 * @author tatsunidas
 */
final class SrCommon {

	private SrCommon() {
	}

	/**
	 * Tags copied from the reference instance to keep patient/study identity.
	 * MUST be in ascending tag order — {@code Attributes.addSelected(other, int...)}
	 * walks the selection assuming it is sorted.
	 */
	static final int[] INHERIT_TAGS = {
			Tag.SpecificCharacterSet, // 0008,0005
			Tag.StudyDate, // 0008,0020
			Tag.StudyTime, // 0008,0030
			Tag.AccessionNumber, // 0008,0050
			Tag.ReferringPhysicianName, // 0008,0090
			Tag.PatientName, // 0010,0010
			Tag.PatientID, // 0010,0020
			Tag.IssuerOfPatientID, // 0010,0021
			Tag.PatientBirthDate, // 0010,0030
			Tag.PatientSex, // 0010,0040
			Tag.StudyInstanceUID, // 0020,000D
			Tag.StudyID // 0020,0010
	};

	/** Copy patient/study identity from a reference instance in the same study. */
	static void inheritIdentity(Attributes sr, Attributes ref) {
		if (ref != null) {
			sr.addSelected(ref, INHERIT_TAGS);
		}
	}

	/**
	 * Fill the SR Document Series + SOP Common + SR Document General modules with
	 * freshly generated UIDs and the given SOP class.
	 *
	 * @param seriesNumber series number string (e.g. "901").
	 */
	static void fillSrHeader(Attributes sr, String sopClassUID, Date now, String seriesNumber) {
		// Ensure Japanese and other non-ASCII text is written as UTF-8.
		sr.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192");

		// SR Document Series module
		sr.setString(Tag.Modality, VR.CS, "SR");
		sr.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
		sr.setString(Tag.SeriesNumber, VR.IS, seriesNumber);
		sr.setDate(Tag.SeriesDate, VR.DA, now);
		sr.setDate(Tag.SeriesTime, VR.TM, now);
		sr.setString(Tag.Manufacturer, VR.LO, "GRAPHY");

		// SOP Common / General module
		sr.setString(Tag.SOPClassUID, VR.UI, sopClassUID);
		sr.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
		sr.setString(Tag.InstanceNumber, VR.IS, "1");

		// SR Document General module
		sr.setDate(Tag.ContentDate, VR.DA, now);
		sr.setDate(Tag.ContentTime, VR.TM, now);
		sr.setString(Tag.CompletionFlag, VR.CS, "COMPLETE");
		sr.setString(Tag.VerificationFlag, VR.CS, "UNVERIFIED");
	}

	/**
	 * Add Verification Observer Sequence (0040,A073) to an SR dataset. Records who
	 * produced the report so the DICOM header carries an author attribution even
	 * before the report is formally verified by a radiologist.
	 */
	static void setVerificationObserver(Attributes sr, String observerName) {
		if (observerName == null || observerName.isEmpty()) {
			return;
		}
		// (0040,A073) Verification Observer Sequence
		Sequence seq = sr.newSequence(0x0040A073, 1);
		Attributes obs = new Attributes();
		obs.setString(0x0040A075, VR.PN, observerName); // Verification Observer Name
		obs.setString(0x0040A084, VR.CS, "PSN");         // Observer Type = Person
		obs.setString(Tag.InstitutionName, VR.LO, "GRAPHY");
		seq.add(obs);
	}

	/** Set an item's Concept Name Code Sequence (0040,A043). */
	static void setConceptName(Attributes item, Code code) {
		Sequence seq = item.newSequence(Tag.ConceptNameCodeSequence, 1);
		seq.add(code.toItem());
	}
}
