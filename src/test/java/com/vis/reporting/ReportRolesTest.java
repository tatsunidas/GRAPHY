package com.vis.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.Test;

import com.vis.core.reporting.ParticipationType;
import com.vis.core.reporting.ReportDocument;
import com.vis.core.reporting.ReportParticipant;
import com.vis.core.reporting.ReportService;
import com.vis.core.reporting.ReportType;
import com.vis.core.reporting.StaffRole;
import com.vis.core.reporting.sr.SRWriter;
import com.vis.core.reporting.sr.SRtoHtml;

/**
 * Layer-1 pure-logic tests for the reporting roles upgrade: participant →
 * observer/participant sequence encoding (with job roles), the per-type
 * verification policy gate, and participant persistence round-trip. No DB / Swing.
 */
public class ReportRolesTest {

	private static final int TAG_VERIFYING_OBSERVER_SEQ = 0x0040A073;
	private static final int TAG_VERIFYING_OBSERVER_NAME = 0x0040A075;
	private static final int TAG_AUTHOR_OBSERVER_SEQ = 0x0040A078;
	private static final int TAG_PARTICIPANT_SEQ = 0x0040A07A;
	private static final int TAG_PARTICIPATION_TYPE = 0x0040A080;
	private static final int TAG_PERSON_NAME = 0x0040A123;
	private static final int TAG_ORGANIZATIONAL_ROLE_CODE_SEQ = 0x0044010A;

	private Attributes referenceInstance() {
		Attributes ref = new Attributes();
		ref.setSpecificCharacterSet("ISO_IR 192");
		ref.setString(Tag.PatientName, VR.PN, "Yamada^Taro");
		ref.setString(Tag.PatientID, VR.LO, "PID-001");
		ref.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.study");
		ref.setString(Tag.StudyDate, VR.DA, "20260101");
		return ref;
	}

	private ReportDocument reportWithParticipants() {
		ReportDocument doc = ReportDocument.newDraft("PID-001", "1.2.3.study", "2026/01/01", null);
		doc.setType(ReportType.IMAGING_DIAGNOSTIC);
		doc.setTitle("Chest CT");
		doc.setBodyHtml("Findings.");
		doc.addParticipant(new ReportParticipant("Author^Doc", StaffRole.PHYSICIAN, ParticipationType.AUTHOR));
		doc.addParticipant(new ReportParticipant("Verify^Doc", StaffRole.PHYSICIAN, ParticipationType.VERIFIER));
		doc.addParticipant(new ReportParticipant("Enter^Clerk", StaffRole.CLERICAL_WORKER, ParticipationType.ENTERER));
		doc.addParticipant(new ReportParticipant("Review^Tech", StaffRole.RADIOLOGIC_TECHNOLOGIST, ParticipationType.REVIEWER));
		return doc;
	}

	@Test
	public void participantsEncodeIntoObserverAndParticipantSequences() {
		Attributes sr = new SRWriter().build(referenceInstance(), reportWithParticipants());

		// Author Observer Sequence with physician role code (CID 7452)
		Sequence authors = sr.getSequence(TAG_AUTHOR_OBSERVER_SEQ);
		assertNotNull("author observer sequence", authors);
		assertEquals(1, authors.size());
		Attributes author = authors.get(0);
		assertEquals("Author^Doc", author.getString(TAG_PERSON_NAME));
		Attributes role = author.getNestedDataset(TAG_ORGANIZATIONAL_ROLE_CODE_SEQ);
		assertNotNull("organizational role code seq", role);
		assertEquals("309343006", role.getString(Tag.CodeValue));
		assertEquals("SCT", role.getString(Tag.CodingSchemeDesignator));

		// Verifying Observer Sequence + VERIFIED flag
		Sequence verifiers = sr.getSequence(TAG_VERIFYING_OBSERVER_SEQ);
		assertNotNull("verifying observer sequence", verifiers);
		assertEquals("Verify^Doc", verifiers.get(0).getString(TAG_VERIFYING_OBSERVER_NAME));
		assertEquals("VERIFIED", sr.getString(Tag.VerificationFlag));

		// Participant Sequence carries enterer (ENT) and reviewer (ATTEST)
		Sequence participants = sr.getSequence(TAG_PARTICIPANT_SEQ);
		assertNotNull("participant sequence", participants);
		assertEquals(2, participants.size());
		boolean ent = false, attest = false;
		for (Attributes p : participants) {
			String term = p.getString(TAG_PARTICIPATION_TYPE);
			if ("ENT".equals(term)) ent = true;
			if ("ATTEST".equals(term)) attest = true;
		}
		assertTrue("enterer ENT present", ent);
		assertTrue("reviewer ATTEST present", attest);
	}

	@Test
	public void participantsRenderInHtml() {
		Attributes sr = new SRWriter().build(referenceInstance(), reportWithParticipants());
		String html = SRtoHtml.toHtml(sr);
		assertTrue(html.contains("Author Doc"));      // name with '^' replaced by space
		assertTrue(html.contains("Physician"));        // role meaning rendered
		assertTrue(html.contains("Reviewer"));         // ATTEST mapped to Reviewer label
	}

	@Test
	public void verificationPolicyByType() {
		// Imaging diagnostic: only physician may verify
		assertTrue(ReportType.IMAGING_DIAGNOSTIC.canVerify(StaffRole.PHYSICIAN));
		assertTrue(!ReportType.IMAGING_DIAGNOSTIC.canVerify(StaffRole.RADIOLOGIC_TECHNOLOGIST));
		// Technologist: technologist (or physician) may verify
		assertTrue(ReportType.TECHNOLOGIST.canVerify(StaffRole.RADIOLOGIC_TECHNOLOGIST));
		assertTrue(ReportType.TECHNOLOGIST.canVerify(StaffRole.PHYSICIAN));
		assertTrue(!ReportType.TECHNOLOGIST.canVerify(StaffRole.CLERICAL_WORKER));
		// Measurement / general: no restriction
		assertTrue(ReportType.MEASUREMENT.canVerify(null));
		assertTrue(ReportType.GENERAL.canVerify(StaffRole.CLERICAL_WORKER));
	}

	@Test
	public void checkVerifiableGate() {
		ReportService svc = new ReportService((com.vis.db.DatabaseHandler) null);

		// Imaging diagnostic with no verifier → needs verifier
		ReportDocument d1 = ReportDocument.newDraft("P", "S", "2026/01/01", null);
		d1.setType(ReportType.IMAGING_DIAGNOSTIC);
		assertEquals("Reporting.verify.needVerifier", svc.checkVerifiable(d1));

		// ... with a technologist verifier → role not allowed
		d1.addParticipant(new ReportParticipant("T^Tech", StaffRole.RADIOLOGIC_TECHNOLOGIST, ParticipationType.VERIFIER));
		assertEquals("Reporting.verify.roleNotAllowed", svc.checkVerifiable(d1));

		// ... with a physician verifier → allowed
		ReportDocument d2 = ReportDocument.newDraft("P", "S", "2026/01/01", null);
		d2.setType(ReportType.IMAGING_DIAGNOSTIC);
		d2.addParticipant(new ReportParticipant("D^Doc", StaffRole.PHYSICIAN, ParticipationType.VERIFIER));
		assertNull(svc.checkVerifiable(d2));

		// Technologist report signed off by a technologist → allowed
		ReportDocument d3 = ReportDocument.newDraft("P", "S", "2026/01/01", null);
		d3.setType(ReportType.TECHNOLOGIST);
		d3.addParticipant(new ReportParticipant("T^Tech", StaffRole.RADIOLOGIC_TECHNOLOGIST, ParticipationType.VERIFIER));
		assertNull(svc.checkVerifiable(d3));

		// Measurement report: no gate
		ReportDocument d4 = ReportDocument.newDraft("P", "S", "2026/01/01", null);
		d4.setType(ReportType.MEASUREMENT);
		assertNull(svc.checkVerifiable(d4));
	}

	@Test
	public void participantsPersistRoundTrip() {
		ReportDocument doc = reportWithParticipants();
		HashMap<String, Object> ctx = doc.readContext();
		ReportDocument back = ReportDocument.fromContext(ctx);

		assertEquals(4, back.getParticipants().size());
		ReportParticipant verifier = back.getVerifier();
		assertNotNull(verifier);
		assertEquals("Verify^Doc", verifier.getName());
		assertEquals(StaffRole.PHYSICIAN, verifier.getRole());
		assertEquals(ParticipationType.VERIFIER, verifier.getParticipation());
		// legacy author column synced from the AUTHOR participant
		assertEquals("Author^Doc", back.getAuthor());
	}

	@Test
	public void legacyAuthorSynthesizedAsAuthorObserver() {
		// A pre-upgrade report: only the legacy author field, no participants
		ReportDocument doc = ReportDocument.newDraft("PID-001", "1.2.3.study", "2026/01/01", "Legacy^Author");
		Attributes sr = new SRWriter().build(referenceInstance(), doc);
		Sequence authors = sr.getSequence(TAG_AUTHOR_OBSERVER_SEQ);
		assertNotNull(authors);
		assertEquals("Legacy^Author", authors.get(0).getString(TAG_PERSON_NAME));
	}
}
