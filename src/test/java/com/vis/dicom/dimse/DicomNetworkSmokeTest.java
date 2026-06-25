package com.vis.dicom.dimse;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import com.vis.db.DatabaseHandler;
import com.vis.db.DatabaseHandler.DatabaseHandlerBuilder;
import com.vis.dicom.DicomCommunicationNode;

/**
 * Layer 4: DICOM network smoke tests.
 *
 * Verifies that:
 *   1. DcmQRSCP can be constructed once the DB is initialized (proves DB → SCP wiring).
 *   2. StoreSCU.echo() fails gracefully when no server is listening (no uncaught exception,
 *      returns false).
 *   3. DicomCommunicationNode round-trips through the database SERVERS table correctly.
 *
 * DcmQRSCP.start() is intentionally NOT called here because its catch blocks call
 * System.exit(2), which would kill the test JVM. Construction alone is sufficient to
 * verify the DB ↔ SCP dependency wiring.
 */
public class DicomNetworkSmokeTest {

    private static DatabaseHandler db;
    private static Path tmpDir;

    @BeforeClass
    public static void setupDB() throws Exception {
        tmpDir = Files.createTempDirectory("graphy_nettest_");
        db = new DatabaseHandlerBuilder().build();
        boolean ok = db.startupForTest(tmpDir.toString());
        assertTrue("DatabaseHandler.startupForTest() must succeed for network tests", ok);
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
    // DcmQRSCP construction
    // -----------------------------------------------------------------------

    @Test
    public void dcmQRSCP_canBeConstructed_withInitializedDB() throws Exception {
        // If DB is properly set up, DcmQRSCP constructor should succeed (it reads
        // getListenerDetails() to configure the ApplicationEntity AET).
        DcmQRSCP scp = new DcmQRSCP();
        assertNotNull("DcmQRSCP must be constructible when DB is initialized", scp);
    }

    @Test
    public void dcmQRSCP_listenerAET_matchesDBDefault() throws Exception {
        // Confirm that the AET read from DB matches what DcmQRSCP is configured with.
        // We verify at the DB layer — the constructor reads this same value.
        String[] details = db.getListenerDetails();
        assertNotNull("Listener details must be present", details);
        assertEquals("AET used by DcmQRSCP comes from DB default", "GRAPHY", details[0]);
    }

    // -----------------------------------------------------------------------
    // StoreSCU echo — failure handling
    // -----------------------------------------------------------------------

    @Test
    public void storescuEcho_closedPort_returnsFalse() {
        // StoreSCU.echo() must return false (not throw, not call System.exit)
        // when the remote host is not listening on the given port.
        boolean result = StoreSCU.echo(new String[]{ "-c", "GRAPHY@localhost:19999" });
        assertFalse("echo() to a closed port must return false", result);
    }

    @Test
    public void storescuEcho_invalidHost_returnsFalse() {
        // Unreachable hostname must also be handled gracefully.
        boolean result = StoreSCU.echo(new String[]{ "-c", "GRAPHY@255.255.255.255:4891" });
        assertFalse("echo() to an unreachable host must return false", result);
    }

    // -----------------------------------------------------------------------
    // DicomCommunicationNode: SERVERS table round-trip
    // -----------------------------------------------------------------------

    @Test
    public void communicationNode_insertAndRetrieve_viaDatabase() {
        // Insert a server entry and verify it survives a DB round-trip.
        db.insertServer("TEST-NODE", "REMOTESCU", "10.0.0.1", 11112,
            "", "C-MOVE", "/wado", 8080, "http", "1.2.840.10008.1.2.1");

        boolean found = false;
        for (java.util.HashMap<String, Object> s : db.getCommunicationServerList()) {
            if ("TEST-NODE".equals(s.get("logicalname"))
                    && "REMOTESCU".equals(s.get("aetitle"))
                    && Integer.valueOf(11112).equals(s.get("port"))) {
                found = true;
                break;
            }
        }
        assertTrue("Inserted server node must be retrievable via getCommunicationServerList", found);
    }

    @Test
    public void communicationNode_deleteServer_isRemovedFromList() {
        // Insert, verify present, delete, verify absent.
        db.insertServer("TEMP-NODE", "TEMP-AET", "127.0.0.1", 14000,
            "", "C-GET", "/wado", 9000, "http", "1.2.840.10008.1.2.1");

        boolean beforeDelete = db.getCommunicationServerList().stream()
            .anyMatch(s -> "TEMP-NODE".equals(s.get("logicalname")));
        assertTrue("Node must be present before delete", beforeDelete);

        db.deleteServer("TEMP-NODE");

        boolean afterDelete = db.getCommunicationServerList().stream()
            .anyMatch(s -> "TEMP-NODE".equals(s.get("logicalname")));
        assertFalse("Node must be absent after deleteServer()", afterDelete);
    }

    @Test
    public void communicationNode_dicomNodeObject_parsesAET() {
        // DicomCommunicationNode is a value object — verify it stores and retrieves AET.
        DicomCommunicationNode node = new DicomCommunicationNode(
            "MY-NODE", "ECHOSCU", "localhost", 11112, "");
        assertEquals("AET must match constructor arg", "ECHOSCU", node.getAETitle());
        assertEquals("host must match constructor arg", "localhost", node.getHostName());
        assertEquals("port must match constructor arg", 11112, node.getPort());
    }
}
