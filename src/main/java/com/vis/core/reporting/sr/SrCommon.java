package com.vis.core.reporting.sr;

import java.util.Date;
import java.util.List;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import com.vis.core.reporting.ParticipationType;
import com.vis.core.reporting.ReportParticipant;
import com.vis.core.reporting.StaffRole;
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

	// --- DICOM tags for observer / participant attribution -----------------------
	private static final int TAG_VERIFYING_OBSERVER_SEQ = 0x0040A073;
	private static final int TAG_VERIFYING_ORGANIZATION = 0x0040A027;
	private static final int TAG_VERIFICATION_DATETIME = 0x0040A030;
	private static final int TAG_VERIFYING_OBSERVER_NAME = 0x0040A075;
	private static final int TAG_AUTHOR_OBSERVER_SEQ = 0x0040A078;
	private static final int TAG_PARTICIPANT_SEQ = 0x0040A07A;
	private static final int TAG_PARTICIPATION_TYPE = 0x0040A080;
	private static final int TAG_PARTICIPATION_DATETIME = 0x0040A082;
	private static final int TAG_OBSERVER_TYPE = 0x0040A084;
	private static final int TAG_PERSON_NAME = 0x0040A123;
	private static final int TAG_ORGANIZATIONAL_ROLE_CODE_SEQ = 0x0044010A;

	/**
	 * Write the participants of a report into the SR header sequences, capturing
	 * <em>both</em> how each person is involved (participation type) and their job
	 * (organizational role, CID 7452):
	 * <ul>
	 *   <li>AUTHOR  → Author Observer Sequence (0040,A078)</li>
	 *   <li>VERIFIER → Verifying Observer Sequence (0040,A073); also sets
	 *       {@code VerificationFlag=VERIFIED}</li>
	 *   <li>ENTERER → Participant Sequence (0040,A07A) with Participation Type {@code ENT}</li>
	 *   <li>REVIEWER → Participant Sequence (0040,A07A) with Participation Type {@code ATTEST}</li>
	 * </ul>
	 * Each author/participant item carries Observer Type {@code PSN}, Person Name,
	 * Institution Name and an Organizational Role Code Sequence (0044,010A) for the
	 * job role.
	 */
	static void addObservers(Attributes sr, List<ReportParticipant> participants, Date now) {
		if (participants == null || participants.isEmpty()) {
			return;
		}
		Sequence authorSeq = null;
		Sequence verifySeq = null;
		Sequence partSeq = null;
		boolean verified = false;

		for (ReportParticipant p : participants) {
			if (p == null || !p.hasName()) {
				continue;
			}
			ParticipationType type = p.getParticipation();
			if (type == ParticipationType.AUTHOR) {
				if (authorSeq == null) {
					authorSeq = sr.newSequence(TAG_AUTHOR_OBSERVER_SEQ, 1);
				}
				authorSeq.add(personObserverItem(p));
			} else if (type == ParticipationType.VERIFIER) {
				if (verifySeq == null) {
					verifySeq = sr.newSequence(TAG_VERIFYING_OBSERVER_SEQ, 1);
				}
				verifySeq.add(verifyingObserverItem(p, now));
				verified = true;
			} else { // ENTERER / REVIEWER → Participant Sequence
				if (partSeq == null) {
					partSeq = sr.newSequence(TAG_PARTICIPANT_SEQ, 1);
				}
				partSeq.add(participantItem(p, now));
			}
		}
		if (verified) {
			sr.setString(Tag.VerificationFlag, VR.CS, "VERIFIED");
		}
	}

	/** Author Observer Sequence item: Observer Type / Person Name / Institution / Role. */
	private static Attributes personObserverItem(ReportParticipant p) {
		Attributes item = new Attributes();
		item.setString(TAG_OBSERVER_TYPE, VR.CS, "PSN");
		item.setString(TAG_PERSON_NAME, VR.PN, p.getName());
		item.setString(Tag.InstitutionName, VR.LO, institutionOf(p));
		setOrganizationalRole(item, p.getRole());
		return item;
	}

	/** Verifying Observer Sequence item: name / datetime / organization. */
	private static Attributes verifyingObserverItem(ReportParticipant p, Date now) {
		Attributes item = new Attributes();
		item.setString(TAG_VERIFYING_OBSERVER_NAME, VR.PN, p.getName());
		item.setDate(TAG_VERIFICATION_DATETIME, VR.DT,
				p.getDateTimeMillis() > 0 ? new Date(p.getDateTimeMillis()) : now);
		item.setString(TAG_VERIFYING_ORGANIZATION, VR.LO, institutionOf(p));
		// The verifier's job role is also recorded for traceability, even though the
		// Verifying Observer Sequence item has no standard role-code attribute.
		setOrganizationalRole(item, p.getRole());
		return item;
	}

	/** Participant Sequence item (ENTERER/REVIEWER): participation type + person + role. */
	private static Attributes participantItem(ReportParticipant p, Date now) {
		Attributes item = new Attributes();
		String term = p.getParticipation() == null ? null : p.getParticipation().participantTerm();
		item.setString(TAG_PARTICIPATION_TYPE, VR.CS, term == null ? "ENT" : term);
		item.setDate(TAG_PARTICIPATION_DATETIME, VR.DT,
				p.getDateTimeMillis() > 0 ? new Date(p.getDateTimeMillis()) : now);
		item.setString(TAG_OBSERVER_TYPE, VR.CS, "PSN");
		item.setString(TAG_PERSON_NAME, VR.PN, p.getName());
		item.setString(Tag.InstitutionName, VR.LO, institutionOf(p));
		setOrganizationalRole(item, p.getRole());
		return item;
	}

	/** Organizational Role Code Sequence (0044,010A) carrying the job role (CID 7452). */
	private static void setOrganizationalRole(Attributes item, StaffRole role) {
		if (role == null) {
			return;
		}
		Sequence seq = item.newSequence(TAG_ORGANIZATIONAL_ROLE_CODE_SEQ, 1);
		seq.add(new Code(role.codeValue(), role.codeScheme(), null, role.codeMeaning()).toItem());
	}

	private static String institutionOf(ReportParticipant p) {
		return (p.getOrganization() != null && !p.getOrganization().trim().isEmpty())
				? p.getOrganization() : "GRAPHY";
	}

	/** Set an item's Concept Name Code Sequence (0040,A043). */
	static void setConceptName(Attributes item, Code code) {
		Sequence seq = item.newSequence(Tag.ConceptNameCodeSequence, 1);
		seq.add(code.toItem());
	}
}
