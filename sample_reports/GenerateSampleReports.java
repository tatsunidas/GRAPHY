import java.io.File;
import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;

import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.ReportDocument;
import com.vis.core.reporting.sr.SRWriter;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;

/**
 * Standalone generator for sample DICOM SR files used to exercise the GRAPHY
 * reporting viewer / routing. NOT part of the Maven build (lives outside
 * src/main). Produces:
 * <ul>
 *   <li>{@code sample_text_sr.dcm} — a free-text Comprehensive SR (.88.33) built
 *       with the production {@link SRWriter}, incl. a key-image reference.</li>
 *   <li>{@code sample_rdsr.dcm} — an X-Ray Radiation Dose SR / RDSR (.88.67) built
 *       by hand (CT dose: CTDIvol, DLP).</li>
 * </ul>
 *
 * Compile &amp; run (from the project root) with target/classes + the m2 classpath:
 * <pre>
 *   CP="target/classes:$(cat /tmp/graphy_cp.txt)"
 *   javac -cp "$CP" -d /tmp/genout sample_reports/GenerateSampleReports.java
 *   java -cp "/tmp/genout:$CP" GenerateSampleReports sample_reports
 * </pre>
 */
public class GenerateSampleReports {

	public static void main(String[] args) throws Exception {
		String outDir = args.length > 0 ? args[0] : "sample_reports";
		new File(outDir).mkdirs();

		writeTextSr(new File(outDir, "sample_text_sr.dcm"));
		writeRdsr(new File(outDir, "sample_rdsr.dcm"));
		System.out.println("Done. Wrote sample SR files to " + new File(outDir).getAbsolutePath());
	}

	// ---- free-text Comprehensive SR via the production SRWriter -----------------

	private static void writeTextSr(File out) throws Exception {
		Attributes ref = new Attributes();
		ref.setSpecificCharacterSet("ISO_IR 192");
		ref.setString(Tag.PatientName, VR.PN, "GRAPHY^SampleSR");
		ref.setString(Tag.PatientID, VR.LO, "SR-SAMPLE-001");
		ref.setString(Tag.PatientBirthDate, VR.DA, "19700101");
		ref.setString(Tag.PatientSex, VR.CS, "O");
		String studyUID = UIDUtils.createUID();
		ref.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
		ref.setString(Tag.StudyDate, VR.DA, "20260625");
		ref.setString(Tag.AccessionNumber, VR.SH, "ACC-SR-1");
		ref.setString(Tag.StudyID, VR.SH, "1");

		ReportDocument doc = ReportDocument.newDraft("SR-SAMPLE-001", studyUID, "2026/06/25", "Dr. GRAPHY");
		doc.setTitle("胸部CT 読影レポート（サンプル）");
		doc.setBodyHtml("<html><body>"
				+ "<p>両肺野に明らかな結節影・浸潤影を認めません。縦隔・肺門リンパ節腫大なし。胸水貯留なし。</p>"
				+ "<p><b>診断:</b> 異常所見なし。</p>"
				+ "</body></html>");
		// a key image reference (IMAGE content item + evidence). UIDs are synthetic.
		doc.addKeyImage(new KeyImageRef(studyUID, UIDUtils.createUID(), UIDUtils.createUID(),
				UID.SecondaryCaptureImageStorage.uid(), "キー画像"));

		Attributes sr = new SRWriter().build(ref, doc);
		write(sr, out);
		System.out.println("  wrote " + out.getName() + " (Comprehensive SR .88.33)");
	}

	// ---- X-Ray Radiation Dose SR (RDSR) built by hand ---------------------------

	private static void writeRdsr(File out) throws Exception {
		Date now = new Date();
		Attributes sr = new Attributes();
		sr.setSpecificCharacterSet("ISO_IR 192");
		// patient / study
		sr.setString(Tag.PatientName, VR.PN, "GRAPHY^SampleRDSR");
		sr.setString(Tag.PatientID, VR.LO, "RDSR-SAMPLE-001");
		sr.setString(Tag.PatientBirthDate, VR.DA, "19650401");
		sr.setString(Tag.PatientSex, VR.CS, "O");
		sr.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
		sr.setDate(Tag.StudyDate, VR.DA, now);
		sr.setString(Tag.AccessionNumber, VR.SH, "ACC-RDSR-1");
		sr.setString(Tag.StudyID, VR.SH, "1");
		// SR series / SOP
		sr.setString(Tag.Modality, VR.CS, "SR");
		sr.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
		sr.setString(Tag.SeriesNumber, VR.IS, "999");
		sr.setDate(Tag.SeriesDate, VR.DA, now);
		sr.setDate(Tag.SeriesTime, VR.TM, now);
		sr.setString(Tag.Manufacturer, VR.LO, "GRAPHY");
		sr.setString(Tag.SOPClassUID, VR.UI, UID.XRayRadiationDoseSRStorage.uid()); // .88.67
		sr.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
		sr.setString(Tag.InstanceNumber, VR.IS, "1");
		sr.setDate(Tag.ContentDate, VR.DA, now);
		sr.setDate(Tag.ContentTime, VR.TM, now);
		sr.setString(Tag.CompletionFlag, VR.CS, "COMPLETE");
		sr.setString(Tag.VerificationFlag, VR.CS, "UNVERIFIED");

		// root container: "X-Ray Radiation Dose Report"
		sr.setString(Tag.ValueType, VR.CS, "CONTAINER");
		setConcept(sr, new Code("113701", "DCM", null, "X-Ray Radiation Dose Report"));
		sr.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");

		Sequence content = sr.newSequence(Tag.ContentSequence, 3);

		// Procedure reported = CT
		content.add(codeItem(new Code("121058", "DCM", null, "Procedure reported"),
				new Code("P5-08000", "SRT", null, "Computed Tomography X-Ray")));

		// CT Accumulated Dose Data container
		Attributes acc = container(new Code("113811", "DCM", null, "CT Accumulated Dose Data"));
		Sequence accSeq = acc.newSequence(Tag.ContentSequence, 2);
		accSeq.add(numItem(new Code("113812", "DCM", null, "Total Number of Irradiation Events"), "2",
				new Code("{events}", "UCUM", null, "events")));
		accSeq.add(numItem(new Code("113813", "DCM", null, "CT Dose Length Product Total"), "350.0",
				new Code("mGy.cm", "UCUM", null, "mGy.cm")));
		content.add(acc);

		// CT Acquisition (one irradiation event)
		Attributes evt = container(new Code("113819", "DCM", null, "CT Acquisition"));
		Sequence evtSeq = evt.newSequence(Tag.ContentSequence, 2);
		evtSeq.add(numItem(new Code("113830", "DCM", null, "Mean CTDIvol"), "12.5",
				new Code("mGy", "UCUM", null, "mGy")));
		evtSeq.add(numItem(new Code("113838", "DCM", null, "DLP"), "350.0",
				new Code("mGy.cm", "UCUM", null, "mGy.cm")));
		content.add(evt);

		write(sr, out);
		System.out.println("  wrote " + out.getName() + " (X-Ray Radiation Dose SR / RDSR .88.67)");
	}

	// ---- helpers ----------------------------------------------------------------

	private static Attributes container(Code concept) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
		ci.setString(Tag.ValueType, VR.CS, "CONTAINER");
		setConcept(ci, concept);
		ci.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
		return ci;
	}

	private static Attributes codeItem(Code concept, Code value) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
		ci.setString(Tag.ValueType, VR.CS, "CODE");
		setConcept(ci, concept);
		ci.newSequence(Tag.ConceptCodeSequence, 1).add(value.toItem());
		return ci;
	}

	private static Attributes numItem(Code concept, String numericValue, Code unit) {
		Attributes ci = new Attributes();
		ci.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
		ci.setString(Tag.ValueType, VR.CS, "NUM");
		setConcept(ci, concept);
		Attributes mv = new Attributes();
		mv.setString(Tag.NumericValue, VR.DS, numericValue);
		mv.newSequence(Tag.MeasurementUnitsCodeSequence, 1).add(unit.toItem());
		ci.newSequence(Tag.MeasuredValueSequence, 1).add(mv);
		return ci;
	}

	private static void setConcept(Attributes item, Code code) {
		item.newSequence(Tag.ConceptNameCodeSequence, 1).add(code.toItem());
	}

	private static void write(Attributes ds, File out) throws Exception {
		Attributes fmi = ds.createFileMetaInformation(UID.ExplicitVRLittleEndian.uid());
		try (DicomOutputStream dos = new DicomOutputStream(out)) {
			dos.writeDataset(fmi, ds);
		}
	}
}
