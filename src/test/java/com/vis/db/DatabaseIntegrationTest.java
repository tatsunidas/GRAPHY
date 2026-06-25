package com.vis.db;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import com.vis.configuration.RoiDBKey;
import com.vis.core.view.D2.roi.RoiGeometry;
import com.vis.db.DatabaseHandler.DatabaseHandlerBuilder;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.VR;

/**
 * Layer 4: Apache Derby embedded database integration tests.
 *
 * Uses DatabaseHandler.startupForTest(dir) to initialize an isolated temp
 * database without touching ~/.GRAPHY or starting the DICOM server.
 *
 * Tests run in name-ascending order so state-independent checks come before
 * state-modifying ones.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DatabaseIntegrationTest {

    private static DatabaseHandler db;
    private static Path tmpDir;

    // Fixed UIDs used across tests — these match the inserted image hierarchy.
    static final String PID       = "TEST-PID-001";
    static final String STUDY_UID  = "1.2.840.99999.1";
    static final String SERIES_UID = "1.2.840.99999.2";
    static final String SOP_UID    = "1.2.840.99999.3";

    @BeforeClass
    public static void setupDB() throws Exception {
        tmpDir = Files.createTempDirectory("graphy_dbtest_");
        db = new DatabaseHandlerBuilder().build();
        boolean ok = db.startupForTest(tmpDir.toString());
        assertTrue("DatabaseHandler.startupForTest() must succeed", ok);

        // Insert the minimal Patient/Study/Series/Image hierarchy required by the
        // ROI table's foreign key constraints.
        DicomObject ds = buildMinimalDicomObject(PID, STUDY_UID, SERIES_UID, SOP_UID);
        String fakePath = tmpDir.resolve("test.dcm").toString();
        boolean written = db.writeDatasetInfo(ds, fakePath);
        assertTrue("Minimal DICOM hierarchy must be writable", written);
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
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) deleteDir(child);
        }
        dir.delete();
    }

    // -----------------------------------------------------------------------
    // a_ prefix: run before any state-modifying tests (name-ascending order)
    // -----------------------------------------------------------------------

    @Test
    public void a_checkDBExists_afterStartup_returnsTrue() {
        assertTrue("checkDBExists() must return true after startupForTest", db.checkDBExists());
    }

    @Test
    public void a_getListenerDetails_hasDefaultAET() {
        String[] details = db.getListenerDetails();
        assertNotNull("getListenerDetails() must not return null", details);
        assertEquals("listener array length must be 4", 4, details.length);
        assertEquals("default AET must be GRAPHY", "GRAPHY", details[0]);
    }

    @Test
    public void a_getListenerDetails_hasDefaultPort() {
        String[] details = db.getListenerDetails();
        assertNotNull(details);
        assertEquals("default DICOM port must be 4891", "4891", details[2]);
    }

    @Test
    public void a_communicationServers_initiallyEmpty() {
        // Verified before communicationServer_insertAndRetrieve runs (name-ascending order).
        ArrayList<HashMap<String, Object>> servers = db.getCommunicationServerList();
        assertNotNull("getCommunicationServerList() must not return null", servers);
        assertTrue("SERVERS table must be empty on a fresh database", servers.isEmpty());
    }

    @Test
    public void a_imageRecord_exists_afterHierarchySetup() {
        // The @BeforeClass wrote a Patient/Study/Series/Image record; verify it's there.
        assertTrue("Image record inserted in @BeforeClass must be found",
            db.checkImageRecordExists(STUDY_UID, SERIES_UID, SOP_UID));
    }

    @Test
    public void a_imageRecord_notExistsForRandomUIDs() {
        assertFalse("Random UIDs must not appear in IMAGE table",
            db.checkImageRecordExists("9.9.9.9.1", "9.9.9.9.2", "9.9.9.9.3"));
    }

    @Test
    public void a_roi_initiallyEmpty() {
        ArrayList<HashMap<String, Object>> rois =
            db.loadRoiContextFromInstance(PID, STUDY_UID, SERIES_UID, SOP_UID);
        assertNotNull("loadRoiContextFromInstance must never return null", rois);
        assertTrue("No ROIs expected before any insert", rois.isEmpty());
    }

    // -----------------------------------------------------------------------
    // ROI insert + load round-trips
    // -----------------------------------------------------------------------

    @Test
    public void roi_insertAndLoadByInstance_roundTrip() {
        String roiId = UUID.randomUUID().toString();
        db.insertRoi(buildRoiMap(roiId, "rect-roi", "AXI",
            10, 20, 50, 30,
            new double[]{10.0, 60.0, 60.0, 10.0},
            new double[]{20.0, 20.0, 50.0, 50.0}));

        ArrayList<HashMap<String, Object>> loaded =
            db.loadRoiContextFromInstance(PID, STUDY_UID, SERIES_UID, SOP_UID);
        assertTrue("At least one ROI must be present after insert", loaded.size() >= 1);

        HashMap<String, Object> found = null;
        for (HashMap<String, Object> r : loaded) {
            if (roiId.equals(r.get("RoiID"))) { found = r; break; }
        }
        assertNotNull("Inserted ROI must appear in loadRoiContextFromInstance", found);
        assertEquals("Name round-trips", "rect-roi", found.get("Name"));
        assertEquals("OriginX round-trips", 10, found.get("OriginX"));
        assertEquals("OriginY round-trips", 20, found.get("OriginY"));
        assertEquals("Width round-trips",   50, found.get("Width"));
        assertEquals("Height round-trips",  30, found.get("Height"));
    }

    @Test
    public void roi_loadById_returnsMatchingRoi() {
        String roiId = UUID.randomUUID().toString();
        db.insertRoi(buildRoiMap(roiId, "by-id-test", "SAG",
            5, 15, 40, 20,
            new double[]{5.0, 45.0}, new double[]{15.0, 35.0}));

        HashMap<String, Object> found = db.loadRoiContext(roiId, PID, STUDY_UID, SERIES_UID, SOP_UID);
        assertNotNull("loadRoiContext by ID must find the inserted ROI", found);
        assertEquals("RoiID matches", roiId, found.get("RoiID"));
        assertEquals("Name matches", "by-id-test", found.get("Name"));
        assertEquals("CrossSection matches", "SAG", found.get(RoiDBKey.CrossSection.name()));
    }

    @Test
    public void roi_sameInstance_twoRois_bothLoaded() {
        String roiId1 = UUID.randomUUID().toString();
        String roiId2 = UUID.randomUUID().toString();
        db.insertRoi(buildRoiMap(roiId1, "roi-one", "AXI", 0, 0, 10, 10, null, null));
        db.insertRoi(buildRoiMap(roiId2, "roi-two", "AXI", 5, 5, 15, 15, null, null));

        ArrayList<HashMap<String, Object>> loaded =
            db.loadRoiContextFromInstance(PID, STUDY_UID, SERIES_UID, SOP_UID);
        boolean hasOne = false, hasTwo = false;
        for (HashMap<String, Object> r : loaded) {
            if (roiId1.equals(r.get("RoiID"))) hasOne = true;
            if (roiId2.equals(r.get("RoiID"))) hasTwo = true;
        }
        assertTrue("roi-one must appear in instance load", hasOne);
        assertTrue("roi-two must appear in instance load", hasTwo);
    }

    // -----------------------------------------------------------------------
    // Communication servers
    // -----------------------------------------------------------------------

    @Test
    public void communicationServer_insertAndRetrieve() {
        db.insertServer("ORTHANC-TEST", "ORTHANC", "192.168.0.100", 4242,
            "", "C-MOVE", "/wado", 8042, "http", "1.2.840.10008.1.2.1");

        boolean found = false;
        for (HashMap<String, Object> s : db.getCommunicationServerList()) {
            if ("ORTHANC-TEST".equals(s.get("logicalname"))) { found = true; break; }
        }
        assertTrue("Inserted server must appear in getCommunicationServerList", found);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a minimal DicomObject with the UIDs and PatientID needed to satisfy
     * the foreign key chain Patient → Study → Series → Image used by the ROI table.
     */
    static DicomObject buildMinimalDicomObject(
            String patientId, String studyUid, String seriesUid, String sopUid) {
        DicomObject ds = DicomObject.newDicomObject();
        ds.setString(Tag.Patient​ID,           VR.LO, patientId);
        ds.setString(Tag.Patient​Name,          VR.PN, "Test^Patient");
        ds.setString(Tag.Study​Instance​UID,     VR.UI, studyUid);
        ds.setString(Tag.Series​Instance​UID,    VR.UI, seriesUid);
        ds.setString(Tag.SOP​Instance​UID,       VR.UI, sopUid);
        ds.setString(Tag.SOP​Class​UID,          VR.UI, "1.2.840.10008.5.1.4.1.1.2"); // CT
        ds.setString(Tag.Modality,              VR.CS, "CT");
        ds.setString(Tag.Instance​Number,        VR.IS, "1");
        return ds;
    }

    static HashMap<String, Object> buildRoiMap(
            String roiId, String name, String crossSection,
            int x, int y, int w, int h,
            double[] pointX, double[] pointY) {
        HashMap<String, Object> map = new HashMap<>();
        map.put(RoiDBKey.RoiID.name(), roiId);
        map.put(RoiDBKey.Name.name(), name);
        map.put(RoiDBKey.RoiType.name(), "0");      // String — parsed with Integer.parseInt
        map.put(RoiGeometry.OriginX.name(), x);
        map.put(RoiGeometry.OriginY.name(), y);
        map.put(RoiGeometry.Width.name(), w);
        map.put(RoiGeometry.Height.name(), h);
        map.put(RoiGeometry.PointX.name(), pointX);
        map.put(RoiGeometry.PointY.name(), pointY);
        map.put(RoiGeometry.Shape.name(), null);
        map.put(RoiDBKey.InstanceNo.name(), "1");   // String — parsed with Integer.parseInt
        map.put(RoiDBKey.RoiGroup.name(), "0");     // String — parsed with Integer.parseInt
        map.put(RoiDBKey.RoiLabel.name(), "");
        map.put(RoiDBKey.ObjectType.name(), null);
        map.put(RoiDBKey.Organ.name(), null);
        map.put(RoiDBKey.Description.name(), null);
        map.put(RoiDBKey.StudyDate.name(), null);
        map.put(RoiDBKey.CrossSection.name(), crossSection);
        map.put(RoiDBKey.PatientID.name(), PID);
        map.put(RoiDBKey.StudyInstanceUID.name(), STUDY_UID);
        map.put(RoiDBKey.SeriesInstanceUID.name(), SERIES_UID);
        map.put(RoiDBKey.SOPInstanceUID.name(), SOP_UID);
        return map;
    }
}
