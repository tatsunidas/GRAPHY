package com.vis.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.Test;

import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.ReportDocument;
import com.vis.core.reporting.sr.ContentItem;
import com.vis.core.reporting.sr.HtmlText;
import com.vis.core.reporting.sr.SopClassUtil;
import com.vis.core.reporting.sr.SRReader;
import com.vis.core.reporting.sr.SRWriter;
import com.vis.core.reporting.sr.SRtoHtml;
import com.vis.dicom.UID;

/**
 * Layer-1 pure-logic tests for the reporting SR pipeline: no DB, no Swing, no
 * display. Verifies SR build -&gt; read -&gt; HTML round-trip, key-image href
 * parsing, SR-family detection and HTML/plain-text helpers.
 */
public class ReportingSrRoundTripTest {

	private Attributes referenceInstance() {
		Attributes ref = new Attributes();
		ref.setSpecificCharacterSet("ISO_IR 192");
		ref.setString(Tag.PatientName, VR.PN, "Yamada^Taro");
		ref.setString(Tag.PatientID, VR.LO, "PID-001");
		ref.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.study");
		ref.setString(Tag.StudyDate, VR.DA, "20260101");
		ref.setString(Tag.AccessionNumber, VR.SH, "ACC-9");
		return ref;
	}

	@Test
	public void srBuildInheritsPatientAndStudyAndGeneratesUids() {
		ReportDocument doc = ReportDocument.newDraft("PID-001", "1.2.3.study", "2026/01/01", "drR");
		doc.setTitle("Chest CT");
		doc.setBodyHtml("<html><body><p>No <b>acute</b> findings.</p></body></html>");

		Attributes sr = new SRWriter().build(referenceInstance(), doc);

		// patient/study identity inherited
		assertEquals("PID-001", sr.getString(Tag.PatientID));
		assertEquals("1.2.3.study", sr.getString(Tag.StudyInstanceUID));
		// SR identity generated and correct SOP class
		assertEquals("SR", sr.getString(Tag.Modality));
		assertEquals(UID.ComprehensiveSRStorage.uid(), sr.getString(Tag.SOPClassUID));
		assertNotNull(sr.getString(Tag.SOPInstanceUID));
		assertNotNull(sr.getString(Tag.SeriesInstanceUID));
		assertEquals("CONTAINER", sr.getString(Tag.ValueType));
		assertEquals("COMPLETE", sr.getString(Tag.CompletionFlag));
	}

	@Test
	public void srWithKeyImageRoundTripsThroughReaderAndHtml() {
		ReportDocument doc = ReportDocument.newDraft("PID-001", "1.2.3.study", "2026/01/01", "drR");
		doc.setTitle("Report");
		doc.setBodyHtml("<html><body><p>Lesion noted.</p></body></html>");
		doc.addKeyImage(new KeyImageRef("1.2.3.study", "1.2.3.series", "1.2.3.sop",
				UID.SecondaryCaptureImageStorage.uid(), "Key image"));

		Attributes sr = new SRWriter().build(referenceInstance(), doc);

		// read back the content tree
		ContentItem root = SRReader.read(sr);
		assertEquals("CONTAINER", root.getValueType());
		boolean foundText = false;
		boolean foundImage = false;
		for (ContentItem child : root.getChildren()) {
			if ("TEXT".equals(child.getValueType()) && child.getTextValue() != null
					&& child.getTextValue().contains("Lesion noted")) {
				foundText = true;
			}
			if ("IMAGE".equals(child.getValueType())) {
				foundImage = true;
				assertEquals("1.2.3.sop", child.getRefSopInstanceUID());
			}
		}
		assertTrue("findings text present", foundText);
		assertTrue("key image content item present", foundImage);

		// HTML render resolves the key image to a graphy:// retrieval anchor via evidence
		String html = SRtoHtml.toHtml(sr);
		assertTrue(html.contains("graphy://image/1.2.3.study/1.2.3.series/1.2.3.sop"));
		assertTrue(html.contains("Yamada")); // patient header rendered
	}

	@Test
	public void keyImageHrefParsesBack() {
		KeyImageRef ref = new KeyImageRef("S", "SE", "SOP", "cls", "label");
		KeyImageRef parsed = KeyImageRef.fromHref(ref.toHref());
		assertNotNull(parsed);
		assertEquals("S", parsed.getStudyUID());
		assertEquals("SE", parsed.getSeriesUID());
		assertEquals("SOP", parsed.getSopUID());
		assertNull(KeyImageRef.fromHref("https://example.com"));
	}

	@Test
	public void srFamilyDetection() {
		assertTrue(SopClassUtil.isSrFamily(UID.ComprehensiveSRStorage.uid()));
		assertTrue(SopClassUtil.isSrFamily(UID.XRayRadiationDoseSRStorage.uid())); // RDSR
		assertTrue(SopClassUtil.isSrFamily(UID.KeyObjectSelectionDocumentStorage.uid()));
		assertFalse(SopClassUtil.isSrFamily(UID.SecondaryCaptureImageStorage.uid()));
		assertFalse(SopClassUtil.isSrFamily(null));
		assertTrue(SopClassUtil.isSrModality("SR"));
	}

	@Test
	public void htmlToPlainTextFlattensMarkup() {
		String plain = HtmlText.toPlainText("<html><body><p>Line1</p><ul><li>a</li><li>b</li></ul></body></html>");
		assertTrue(plain.contains("Line1"));
		assertTrue(plain.contains("a"));
		assertFalse(plain.contains("<p>"));
		assertFalse(plain.contains("<li>"));
	}
}
