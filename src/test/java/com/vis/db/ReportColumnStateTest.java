package com.vis.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.vis.core.reporting.ReportDocument;
import com.vis.core.reporting.sr.SopClassUtil;
import com.vis.core.reporting.sr.SRWriter;
import com.vis.db.DatabaseHandler.DatabaseHandlerBuilder;
import com.vis.dicom.DicomObject;
import com.vis.dicom.dcm4cheImpl.DicomObjectChe;

/**
 * Integration test for the tree Report column data: per-study report counts, the
 * SR-instance listing, and SR-family series detection — verified against a real
 * Derby DB holding a study that mixes a normal image series and a report (SR).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ReportColumnStateTest {

	private static DatabaseHandler db;
	private static Path tmpDir;

	private static final String PID = "RC-PID-1";
	private static final String STUDY = "1.2.840.66666.1";
	private static final String IMG_SERIES = "1.2.840.66666.2";
	private static final String IMG_SOP = "1.2.840.66666.3";

	private static String srSeriesUID;
	private static String srSopUID;

	@BeforeClass
	public static void setup() throws Exception {
		tmpDir = Files.createTempDirectory("graphy_reportcol_");
		db = new DatabaseHandlerBuilder().build();
		assertTrue(db.startupForTest(tmpDir.toString()));

		// 1) a normal image series in the study
		DicomObject ct = DatabaseIntegrationTest.buildMinimalDicomObject(PID, STUDY, IMG_SERIES, IMG_SOP);
		assertTrue(db.writeDatasetInfo(ct, tmpDir.resolve("ct.dcm").toString()));

		// 2) a report (Comprehensive SR) in the SAME study, built with the production writer
		Attributes ref = new Attributes();
		ref.setString(Tag.PatientID, VR.LO, PID);
		ref.setString(Tag.PatientName, VR.PN, "Mixed^Study");
		ref.setString(Tag.StudyInstanceUID, VR.UI, STUDY);
		ReportDocument doc = ReportDocument.newDraft(PID, STUDY, null, "dr");
		doc.setTitle("SR in mixed study");
		doc.setBodyHtml("<html><body><p>finding</p></body></html>");
		Attributes sr = new SRWriter().build(ref, doc);
		srSeriesUID = sr.getString(Tag.SeriesInstanceUID);
		srSopUID = sr.getString(Tag.SOPInstanceUID);
		assertTrue(db.writeDatasetInfo(new DicomObjectChe(sr), tmpDir.resolve("sr.dcm").toString()));
	}

	@AfterClass
	public static void teardown() {
		if (db != null) {
			db.shutdownDB();
		}
		if (tmpDir != null) {
			deleteDir(tmpDir.toFile());
		}
	}

	private static void deleteDir(File dir) {
		if (dir == null || !dir.exists()) {
			return;
		}
		File[] ch = dir.listFiles();
		if (ch != null) {
			for (File c : ch) {
				deleteDir(c);
			}
		}
		dir.delete();
	}

	@Test
	public void a_imageSeriesIsNotReport_srSeriesIs() {
		assertTrue("SR series detected via first SOP class",
				SopClassUtil.isSrFamily(db.getFirstSopClassUIDInSeries(PID, STUDY, srSeriesUID)));
		assertTrue("image series is NOT a report",
				!SopClassUtil.isSrFamily(db.getFirstSopClassUIDInSeries(PID, STUDY, IMG_SERIES)));
	}

	@Test
	public void b_studyReportCounts_reportPresentNoDraft() {
		int[] counts = db.getStudyReportCounts(PID, STUDY);
		assertEquals("no GRAPHY draft yet", 0, counts[0]);
		assertEquals("one SR instance", 1, counts[1]);
	}

	@Test
	public void c_reportInstancesListed() {
		ArrayList<HashMap<String, String>> insts = db.getReportInstancesInStudy(PID, STUDY);
		assertEquals(1, insts.size());
		assertEquals(srSopUID, insts.get(0).get("SOPInstanceUID"));
		assertTrue(SopClassUtil.isSrFamily(insts.get(0).get("SOPClassUID")));
	}

	@Test
	public void d_draftRaisesDraftCount() {
		ReportDocument draft = ReportDocument.newDraft(PID, STUDY, null, "dr");
		draft.setTitle("draft");
		db.insertReport(draft.readContext());
		int[] counts = db.getStudyReportCounts(PID, STUDY);
		assertEquals("draft now counted", 1, counts[0]);
		assertEquals("SR instance still counted", 1, counts[1]);
	}
}
