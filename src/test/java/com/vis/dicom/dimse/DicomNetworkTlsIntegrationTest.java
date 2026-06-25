package com.vis.dicom.dimse;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import com.vis.db.DatabaseHandler;
import com.vis.db.DatabaseHandler.DatabaseHandlerBuilder;
import com.vis.dicom.tls.DicomTlsConfig;

/**
 * Layer 7: DICOM network integration against a REAL dcm4che {@code dcmqrscp} peer,
 * exercising GRAPHY's own in-process SCU code ({@link StoreSCU#echo} / {@link
 * StoreSCU#storeInstance2Graphy}) in BOTH plaintext and mutual-TLS modes.
 *
 * <p>Unlike Layer 4 ({@code DicomNetworkSmokeTest}, which only checks graceful failure
 * against a closed port), this test stands up an actual Storage SCP and proves a full
 * C-ECHO + C-STORE round-trip succeeds, including a DIMSE-over-TLS handshake using
 * GRAPHY's keystore/truststore read from the {@code LISTENER} DB row.</p>
 *
 * <h3>What it asserts</h3>
 * <ul>
 *   <li>plaintext C-ECHO to dcmqrscp returns {@code true};</li>
 *   <li>plaintext C-STORE lands a *.dcm file in the SCP's storage dir;</li>
 *   <li>mutual-TLS C-ECHO (wrapped in {@link DicomTlsConfig#requestScuTls}) returns
 *       {@code true};</li>
 *   <li>mutual-TLS C-STORE lands a *.dcm file in the TLS SCP's storage dir;</li>
 *   <li>a plaintext SCU against the TLS-only port fails (proves the port truly requires
 *       TLS, i.e. the TLS pass above wasn't a false positive).</li>
 * </ul>
 *
 * <h3>Environment</h3>
 * The test is self-contained: it generates a throw-away self-signed JKS keystore +
 * truststore with the JDK's own {@code keytool}, and launches {@code dcmqrscp} via
 * {@link ProcessBuilder}. It SKIPS (JUnit {@code Assume}) when the dcm4che CLI tools or
 * the sample DICOM file are not present, so CI without dcm4che stays green.
 *
 * <h3>TLS cipher note (why the handshake used to fail)</h3>
 * dcm4che's {@code --tls-aes}/{@code --tls-3des} defaults select
 * {@code TLS_RSA_WITH_AES_128_CBC_SHA} / 3DES, which JDK&nbsp;17+ disables by default
 * ({@code jdk.tls.disabledAlgorithms}); the SCP then has an empty active cipher set and
 * rejects every handshake with "No appropriate protocol". The fix is to use a
 * JDK-21-enabled suite — {@code TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256} over TLSv1.2 —
 * on BOTH ends. That is the cipher GRAPHY requests below and the one dcmqrscp is told to
 * offer.
 *
 * <h3>Equivalent manual reproduction (CLI)</h3>
 * <pre>{@code
 * # ---- Plaintext SCP ----
 * dcmqrscp -b DCMQRSCP@localhost:11112 \
 *     --dicomdir /tmp/qr_plain/DICOMDIR --filepath '{00080018}.dcm'
 * # then, from GRAPHY (or the CLI storescu):
 * storescu -c DCMQRSCP@localhost:11112 CT_LEE_IR87a.dcm
 *
 * # ---- Mutual-TLS SCP (port 2762 is the conventional DICOM-TLS port) ----
 * # 1) make a self-signed JKS keystore + matching truststore (single shared cert):
 * keytool -genkeypair -alias graphy -keyalg RSA -keysize 2048 -validity 3650 \
 *     -dname 'CN=graphy-test,O=GRAPHY,C=JP' \
 *     -keystore keystore.jks -storetype JKS -storepass changeit -keypass changeit
 * keytool -exportcert -alias graphy -keystore keystore.jks -storepass changeit \
 *     -file cert.cer
 * keytool -importcert -noprompt -alias graphy -file cert.cer \
 *     -keystore truststore.jks -storetype JKS -storepass changeit
 * # 2) launch the TLS SCP (JDK-21-enabled GCM cipher + --tls12 are essential):
 * dcmqrscp -b DCMQRSCP@localhost:2762 \
 *     --dicomdir /tmp/qr_tls/DICOMDIR --filepath '{00080018}.dcm' \
 *     --tls12 --tls-cipher TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 \
 *     --key-store keystore.jks --key-store-type JKS --key-store-pass changeit --key-pass changeit \
 *     --trust-store truststore.jks --trust-store-type JKS --trust-store-pass changeit
 * # 3) C-STORE over TLS from the CLI (GRAPHY does this in-process via requestScuTls):
 * storescu -c DCMQRSCP@localhost:2762 \
 *     --tls12 --tls-cipher TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 \
 *     --key-store keystore.jks --key-store-type JKS --key-store-pass changeit --key-pass changeit \
 *     --trust-store truststore.jks --trust-store-type JKS --trust-store-pass changeit \
 *     CT_LEE_IR87a.dcm
 * }</pre>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DicomNetworkTlsIntegrationTest {

    /** JDK-21-enabled suite that both ends must agree on (see class javadoc). */
    private static final String CIPHER = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
    private static final String STORE_PASS = "changeit";
    private static final String ALIAS = "graphy";
    private static final String SCP_AET = "DCMQRSCP";

    /** Candidate dcm4che install roots; first one containing bin/dcmqrscp wins. */
    private static final String[] DCM4CHE_CANDIDATES = {
        System.getenv("DCM4CHE_HOME"),
        "/home/tatsunidas/dcm4che-5.34.2",
        "/opt/dcm4che",
    };
    private static final String[] SAMPLE_CANDIDATES = {
        "/home/tatsunidas/graphy_sample_images/dicom_samples/JIRA_DICOM/CT_LEE_IR87a.dcm",
    };

    private static DatabaseHandler db;
    private static Path tmpRoot;
    private static File dcmqrscpBin;
    private static File sampleDicom;
    private static File keystore;
    private static File truststore;

    private static Path plainStoreDir;
    private static Path tlsStoreDir;
    private static int plainPort;
    private static int tlsPort;
    private static Process plainScp;
    private static Process tlsScp;

    // ------------------------------------------------------------------ setup

    @BeforeClass
    public static void setup() throws Exception {
        dcmqrscpBin = findFirstExecutable(DCM4CHE_CANDIDATES, "bin/dcmqrscp");
        Assume.assumeTrue("dcm4che dcmqrscp not found — skipping network integration test",
                dcmqrscpBin != null);
        sampleDicom = findFirstFile(SAMPLE_CANDIDATES);
        Assume.assumeTrue("Sample DICOM file not found — skipping network integration test",
                sampleDicom != null);

        tmpRoot = Files.createTempDirectory("graphy_tlsnet_");

        // 1) Generate a throw-away self-signed keystore + matching truststore.
        keystore = tmpRoot.resolve("keystore.jks").toFile();
        truststore = tmpRoot.resolve("truststore.jks").toFile();
        generateKeyMaterial();

        // 2) DB up, with the GRAPHY-side DIMSE TLS config pointing at our key material.
        //    getDimseTlsConfig() (read by StoreSCU's applyScuTlsIfRequested) reads exactly
        //    these columns from the single LISTENER row created by startupForTest().
        db = new DatabaseHandlerBuilder().build();
        assertTrue("startupForTest must succeed", db.startupForTest(tmpRoot.resolve("db").toString()));
        db.updateDimseTlsConfig(true, 2762, keystore.getAbsolutePath(), STORE_PASS,
                truststore.getAbsolutePath(), STORE_PASS, "TLSv1.2,TLSv1.3", CIPHER);

        // 3) Two dcmqrscp peers: one plaintext, one mutual-TLS, on free ephemeral ports.
        plainPort = freePort();
        tlsPort = freePort();
        plainStoreDir = Files.createDirectories(tmpRoot.resolve("qr_plain"));
        tlsStoreDir = Files.createDirectories(tmpRoot.resolve("qr_tls"));
        plainScp = launchScp(plainPort, plainStoreDir, false);
        tlsScp = launchScp(tlsPort, tlsStoreDir, true);
    }

    @AfterClass
    public static void teardown() {
        if (plainScp != null) plainScp.destroyForcibly();
        if (tlsScp != null) tlsScp.destroyForcibly();
        DicomTlsConfig.clearScuTls();
        if (db != null) db.shutdownDB();
        if (tmpRoot != null) deleteDir(tmpRoot.toFile());
    }

    // ------------------------------------------------------------------ tests

    @Test
    public void a_plaintext_echo_succeeds() {
        boolean ok = StoreSCU.echo(new String[]{ "-c", SCP_AET + "@localhost:" + plainPort });
        assertTrue("plaintext C-ECHO to live dcmqrscp must succeed", ok);
    }

    @Test
    public void b_plaintext_store_landsFile() throws Exception {
        int before = countDcm(plainStoreDir);
        StoreSCU.storeInstance2Graphy(
                new String[]{ "-c", SCP_AET + "@localhost:" + plainPort, sampleDicom.getAbsolutePath() },
                false /* keep source */);
        int after = countDcm(plainStoreDir);
        assertEquals("plaintext C-STORE must store exactly one *.dcm in the SCP dir",
                before + 1, after);
    }

    @Test
    public void c_tls_echo_succeeds() {
        DicomTlsConfig.requestScuTls(true, CIPHER);
        try {
            boolean ok = StoreSCU.echo(new String[]{ "-c", SCP_AET + "@localhost:" + tlsPort });
            assertTrue("mutual-TLS C-ECHO to live dcmqrscp must succeed", ok);
        } finally {
            DicomTlsConfig.clearScuTls();
        }
    }

    @Test
    public void d_tls_store_landsFile() throws Exception {
        int before = countDcm(tlsStoreDir);
        DicomTlsConfig.requestScuTls(true, CIPHER);
        try {
            StoreSCU.storeInstance2Graphy(
                    new String[]{ "-c", SCP_AET + "@localhost:" + tlsPort, sampleDicom.getAbsolutePath() },
                    false);
        } finally {
            DicomTlsConfig.clearScuTls();
        }
        int after = countDcm(tlsStoreDir);
        assertEquals("mutual-TLS C-STORE must store exactly one *.dcm in the TLS SCP dir",
                before + 1, after);
    }

    @Test
    public void e_plaintext_echo_againstTlsPort_fails() {
        // No requestScuTls() => SCU connects in plaintext. The TLS-only listener must
        // reject it, proving the TLS success above is real (not a port mix-up / no-TLS fallback).
        boolean ok = StoreSCU.echo(new String[]{ "-c", SCP_AET + "@localhost:" + tlsPort });
        assertFalse("plaintext C-ECHO to a TLS-only port must fail", ok);
    }

    // ----------------------------------------------------------------- helpers

    /** Launch dcmqrscp; block until it logs that it is listening (or time out). */
    private static Process launchScp(int port, Path storeDir, boolean tls) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(dcmqrscpBin.getAbsolutePath());
        cmd.add("-b"); cmd.add(SCP_AET + "@localhost:" + port);
        cmd.add("--dicomdir"); cmd.add(storeDir.resolve("DICOMDIR").toString());
        cmd.add("--filepath"); cmd.add("{00080018}.dcm");
        if (tls) {
            cmd.add("--tls12");
            cmd.add("--tls-cipher"); cmd.add(CIPHER);
            cmd.add("--key-store"); cmd.add(keystore.getAbsolutePath());
            cmd.add("--key-store-type"); cmd.add("JKS");
            cmd.add("--key-store-pass"); cmd.add(STORE_PASS);
            cmd.add("--key-pass"); cmd.add(STORE_PASS);
            cmd.add("--trust-store"); cmd.add(truststore.getAbsolutePath());
            cmd.add("--trust-store-type"); cmd.add("JKS");
            cmd.add("--trust-store-pass"); cmd.add(STORE_PASS);
        }
        File log = storeDir.resolve("scp.log").toFile();
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(log))
                .start();
        // dcmqrscp prints "Start TCP Listener on ..." once bound.
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive() && p.exitValue() != 0) {
                fail("dcmqrscp (tls=" + tls + ") exited early:\n" + readFile(log));
            }
            if (logContains(log, "Start TCP Listener")) {
                return p;
            }
            Thread.sleep(150);
        }
        p.destroyForcibly();
        fail("dcmqrscp (tls=" + tls + ") did not start listening in time:\n" + readFile(log));
        return null; // unreachable
    }

    private static void generateKeyMaterial() throws Exception {
        String keytool = System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool";
        File cert = tmpRoot.resolve("cert.cer").toFile();
        run(keytool, "-genkeypair", "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3650", "-dname", "CN=graphy-test,O=GRAPHY,C=JP",
                "-keystore", keystore.getAbsolutePath(), "-storetype", "JKS",
                "-storepass", STORE_PASS, "-keypass", STORE_PASS);
        run(keytool, "-exportcert", "-alias", ALIAS, "-keystore", keystore.getAbsolutePath(),
                "-storepass", STORE_PASS, "-file", cert.getAbsolutePath());
        run(keytool, "-importcert", "-noprompt", "-alias", ALIAS, "-file", cert.getAbsolutePath(),
                "-keystore", truststore.getAbsolutePath(), "-storetype", "JKS",
                "-storepass", STORE_PASS);
        assertTrue("keystore must be generated", keystore.isFile());
        assertTrue("truststore must be generated", truststore.isFile());
    }

    private static void run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            for (String line; (line = r.readLine()) != null; ) out.append(line).append('\n');
        }
        int code = p.waitFor();
        if (code != 0) fail("command failed (" + code + "): " + String.join(" ", cmd) + "\n" + out);
    }

    private static int countDcm(Path dir) throws Exception {
        File[] f = dir.toFile().listFiles((d, n) -> n.toLowerCase().endsWith(".dcm"));
        return f == null ? 0 : f.length;
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }

    private static File findFirstExecutable(String[] roots, String rel) {
        for (String root : roots) {
            if (root == null) continue;
            File f = new File(root, rel);
            if (f.isFile() && f.canExecute()) return f;
        }
        return null;
    }

    private static File findFirstFile(String[] paths) {
        for (String p : paths) {
            if (p == null) continue;
            File f = new File(p);
            if (f.isFile()) return f;
        }
        return null;
    }

    private static boolean logContains(File log, String needle) {
        if (!log.isFile()) return false;
        return readFile(log).contains(needle);
    }

    private static String readFile(File f) {
        if (f == null || !f.isFile()) return "";
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            for (String line; (line = r.readLine()) != null; ) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] kids = dir.listFiles();
        if (kids != null) for (File k : kids) deleteDir(k);
        dir.delete();
    }
}
