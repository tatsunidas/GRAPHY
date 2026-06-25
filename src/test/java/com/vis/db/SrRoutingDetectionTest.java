package com.vis.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vis.core.reporting.sr.SopClassUtil;
import com.vis.db.DatabaseHandler.DatabaseHandlerBuilder;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.dcm4cheImpl.DicomReaderChe;

/**
 * Reproduces the import + routing-detection used by BirdsEyeView and the tree
 * double-click guard: an imported SR/RDSR must be stored with its SR-family SOP
 * Class UID and be recognised by {@link SopClassUtil}, so it is never built as an
 * image thumbnail / Praparat (the cause of the ArithmeticException: / by zero).
 */
public class SrRoutingDetectionTest {

	private static DatabaseHandler db;
	private static Path tmpDir;

	@BeforeClass
	public static void setup() throws Exception {
		tmpDir = Files.createTempDirectory("graphy_srroute_");
		db = new DatabaseHandlerBuilder().build();
		assertTrue(db.startupForTest(tmpDir.toString()));
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

	private void importAndAssertSr(String relPath) {
		File f = new File(relPath);
		Assume.assumeTrue("sample file present: " + relPath, f.exists());

		DicomObject ds = new DicomReaderChe(f.getAbsolutePath(), false).getHeader();
		assertNotNull("sample SR must be readable", ds);
		String patID = ds.getString(Tag.Patient​ID);
		String studyUID = ds.getString(Tag.Study​Instance​UID);
		String seriesUID = ds.getString(Tag.Series​Instance​UID);
		String sopUID = ds.getString(Tag.SOP​Instance​UID);

		assertTrue("import must register the SR instance",
				db.writeDatasetInfo(ds, f.getAbsolutePath()));

		// what BirdsEyeView.isSrFamilySeries / TreeTableMouseListener.routeSrNode read:
		String sopClass = db.getValueFromImage("SOPClassUID", patID, studyUID, seriesUID, sopUID);
		assertNotNull("SOPClassUID must be persisted for the SR", sopClass);
		assertTrue("SR/RDSR must be detected as SR-family -> never rendered as a thumbnail",
				SopClassUtil.isSrFamily(sopClass));

		// series-level detection path (first instance of the series)
		ArrayList<String> sops = db.getInstanceUidList(patID, studyUID, seriesUID);
		assertNotNull(sops);
		assertFalse(sops.isEmpty());
		String cls0 = db.getValueFromImage("SOPClassUID", patID, studyUID, seriesUID, sops.get(0));
		assertTrue(SopClassUtil.isSrFamily(cls0));
	}

	@Test
	public void freeTextSrIsDetected() {
		importAndAssertSr("sample_reports/sample_text_sr.dcm");
	}

	@Test
	public void rdsrIsDetected() {
		importAndAssertSr("sample_reports/sample_rdsr.dcm");
	}
}
