package com.vis.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.ReportDocument;
import com.vis.db.DatabaseHandler.DatabaseHandlerBuilder;
import com.vis.dicom.DicomObject;

/**
 * Layer 4: REPORT table integration against an isolated embedded Derby DB.
 * Verifies the schema creates the REPORT table and that the
 * insert/update/load/delete CRUD round-trips, including the
 * {@link ReportDocument#readContext()} / {@link ReportDocument#fromContext} mapping
 * and Gson-serialized key-image references.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ReportDbIntegrationTest {

	private static DatabaseHandler db;
	private static Path tmpDir;

	private static final String PID = "RPT-PID-1";
	private static final String STUDY_UID = "1.2.840.77777.1";
	private static final String SERIES_UID = "1.2.840.77777.2";
	private static final String SOP_UID = "1.2.840.77777.3";

	@BeforeClass
	public static void setupDB() throws Exception {
		tmpDir = Files.createTempDirectory("graphy_reportdbtest_");
		db = new DatabaseHandlerBuilder().build();
		assertTrue("startupForTest must succeed", db.startupForTest(tmpDir.toString()));
		// minimal hierarchy so REPORT's patient/study FKs are satisfiable
		DicomObject ds = DatabaseIntegrationTest.buildMinimalDicomObject(PID, STUDY_UID, SERIES_UID, SOP_UID);
		assertTrue(db.writeDatasetInfo(ds, tmpDir.resolve("rpt.dcm").toString()));
	}

	@AfterClass
	public static void teardownDB() {
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
		File[] children = dir.listFiles();
		if (children != null) {
			for (File c : children) {
				deleteDir(c);
			}
		}
		dir.delete();
	}

	private ReportDocument newReport() {
		ReportDocument d = ReportDocument.newDraft(PID, STUDY_UID, "2026/06/25", "drK");
		d.setTitle("CT abdomen");
		d.setBodyHtml("<html><body><p>No acute findings.</p></body></html>");
		d.addKeyImage(new KeyImageRef(STUDY_UID, SERIES_UID, SOP_UID, "1.2.840.10008.5.1.4.1.1.2", "Key image"));
		return d;
	}

	@Test
	public void a_reportTableExists() {
		assertTrue("REPORT table must be created by the schema",
				db.checkRecordExists("report", "ReportID", "no-such-id") == false);
		// checkRecordExists returning false (not throwing) proves the table exists & is queryable
	}

	@Test
	public void b_insertAndLoadRoundTrips() {
		ReportDocument d = newReport();
		String id = d.getReportId();
		db.insertReport(d.readContext());

		ArrayList<HashMap<String, Object>> rows = db.loadReportContextFromStudy(PID, STUDY_UID);
		assertNotNull(rows);
		HashMap<String, Object> found = null;
		for (HashMap<String, Object> r : rows) {
			if (id.equals(r.get("ReportID"))) {
				found = r;
			}
		}
		assertNotNull("inserted report must be loadable by study", found);

		ReportDocument back = ReportDocument.fromContext(found);
		assertEquals("CT abdomen", back.getTitle());
		assertEquals(ReportDocument.Status.DRAFT, back.getStatus());
		assertEquals(PID, back.getPatientID());
		assertEquals(STUDY_UID, back.getStudyUID());
		assertTrue(back.getBodyHtml().contains("No acute findings"));
		assertEquals("key image ref survives Gson round-trip", 1, back.getKeyImages().size());
		assertEquals(SOP_UID, back.getKeyImages().get(0).getSopUID());
	}

	@Test
	public void c_updatePersists() {
		ReportDocument d = newReport();
		db.insertReport(d.readContext());

		d.setTitle("CT abdomen (revised)");
		d.setStatus(ReportDocument.Status.FINAL);
		d.setSrSopInstanceUID("1.2.840.77777.99");
		db.insertReport(d.readContext()); // insert-or-update path

		HashMap<String, Object> row = db.loadReportContext(d.getReportId());
		assertNotNull(row);
		ReportDocument back = ReportDocument.fromContext(row);
		assertEquals("CT abdomen (revised)", back.getTitle());
		assertEquals(ReportDocument.Status.FINAL, back.getStatus());
		assertEquals("1.2.840.77777.99", back.getSrSopInstanceUID());
	}

	@Test
	public void d_deleteRemoves() {
		ReportDocument d = newReport();
		db.insertReport(d.readContext());
		assertNotNull(db.loadReportContext(d.getReportId()));

		db.deleteReport(d.getReportId());
		assertNull("deleted report must be gone", db.loadReportContext(d.getReportId()));
	}
}
