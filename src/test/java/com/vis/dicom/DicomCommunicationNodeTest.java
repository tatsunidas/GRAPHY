package com.vis.dicom;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * DicomCommunicationNode のユニットテスト。
 *
 * DICOM 通信ノード（PACSサーバー設定）のコンストラクタと各パラメータの
 * 取得・バリデーションを検証する。ネットワーク接続は不要。
 *
 * 検証項目:
 *  - AE Title / ホスト名 / ポート番号が正しく設定されるか
 *  - ホスト名が null の場合 "localhost" にフォールバックするか
 *  - WADO ポート未設定時は 8080 にフォールバックするか
 *  - HashMap コンストラクタでの設定値取得
 *  - TLS フラグの初期値
 */
public class DicomCommunicationNodeTest {

    // -----------------------------------------------------------------------
    // 基本コンストラクタ (nickname, aet, host, port, ciphers)
    // -----------------------------------------------------------------------

    @Test
    public void testConstructor_aetTitle() {
        DicomCommunicationNode node = new DicomCommunicationNode(
            "TestServer", "TEST_AET", "192.168.1.10", 11112, new ArrayList<>());
        assertEquals("AE Title", "TEST_AET", node.getAETitle());
    }

    @Test
    public void testConstructor_hostname() {
        DicomCommunicationNode node = new DicomCommunicationNode(
            "TestServer", "AET", "10.0.0.1", 104, new ArrayList<>());
        assertEquals("Hostname", "10.0.0.1", node.getHostName());
    }

    @Test
    public void testConstructor_port() {
        DicomCommunicationNode node = new DicomCommunicationNode(
            "TestServer", "AET", "localhost", 4891, new ArrayList<>());
        assertEquals("Port", 4891, node.getPort());
    }

    @Test
    public void testConstructor_nullHost_defaultsToLocalhost() {
        DicomCommunicationNode node = new DicomCommunicationNode(
            "TestServer", "AET", null, 104, new ArrayList<>());
        assertEquals("Null host should default to localhost", "localhost", node.getHostName());
    }

    @Test
    public void testConstructor_emptyString_aetTitle() {
        DicomCommunicationNode node = new DicomCommunicationNode(
            "TestServer", "", "host", 104, new ArrayList<>());
        assertEquals("Empty AE Title should be preserved", "", node.getAETitle());
    }

    // -----------------------------------------------------------------------
    // String ciphers コンストラクタ
    // -----------------------------------------------------------------------

    @Test
    public void testStringCipherConstructor_basicFields() {
        DicomCommunicationNode node = new DicomCommunicationNode(
            "PacsServer", "PACS_AET", "pacs.hospital.jp", 11112, "");
        assertEquals("AET via cipher-string ctor", "PACS_AET", node.getAETitle());
        assertEquals("Host via cipher-string ctor", "pacs.hospital.jp", node.getHostName());
        assertEquals("Port via cipher-string ctor", 11112, node.getPort());
    }

    @Test
    public void testStringCipherConstructor_nullHost_defaultsToLocalhost() {
        DicomCommunicationNode node = new DicomCommunicationNode(
            "S", "AET", null, 104, (String) null);
        assertEquals("localhost fallback", "localhost", node.getHostName());
    }

    // -----------------------------------------------------------------------
    // HashMap コンストラクタ
    // -----------------------------------------------------------------------

    @Test
    public void testHashMapConstructor_basicFields() {
        HashMap<String, Object> map = buildNodeMap(
            "MyPacs", "MY_AET", "10.10.0.2", 104);
        DicomCommunicationNode node = new DicomCommunicationNode(map);

        assertEquals("nickname",  "MyPacs",    node.getNickname());
        assertEquals("aetitle",   "MY_AET",    node.getAETitle());
        assertEquals("host",      "10.10.0.2", node.getHostName());
        assertEquals("port",      104,          node.getPort());
    }

    @Test
    public void testHashMapConstructor_emptyHost_defaultsToLocalhost() {
        HashMap<String, Object> map = buildNodeMap("S", "AET", "", 104);
        DicomCommunicationNode node = new DicomCommunicationNode(map);
        assertEquals("Empty host should default to localhost", "localhost", node.getHostName());
    }

    @Test
    public void testHashMapConstructor_wadoPort_zero_defaultsTo8080() {
        HashMap<String, Object> map = buildNodeMap("S", "AET", "host", 104);
        map.put("wadoport", 0); // 未設定 (0) → 8080 へフォールバック
        DicomCommunicationNode node = new DicomCommunicationNode(map);
        assertEquals("WADO port 0 should default to 8080", 8080, node.getWadoPort());
    }

    @Test
    public void testHashMapConstructor_wadoPort_nonZero_preserved() {
        HashMap<String, Object> map = buildNodeMap("S", "AET", "host", 104);
        map.put("wadoport", 8181);
        DicomCommunicationNode node = new DicomCommunicationNode(map);
        assertEquals("WADO port should be preserved if nonzero", 8181, node.getWadoPort());
    }

    @Test
    public void testHashMapConstructor_tls_defaultFalse() {
        HashMap<String, Object> map = buildNodeMap("S", "AET", "host", 104);
        map.remove("tls_enabled"); // キーなし → false
        DicomCommunicationNode node = new DicomCommunicationNode(map);
        assertFalse("TLS should default to false when not set", node.isTlsEnabled());
    }

    @Test
    public void testHashMapConstructor_tls_trueWhenSet() {
        HashMap<String, Object> map = buildNodeMap("S", "AET", "host", 104);
        map.put("tls_enabled", Boolean.TRUE);
        DicomCommunicationNode node = new DicomCommunicationNode(map);
        assertTrue("TLS should be true when explicitly set", node.isTlsEnabled());
    }

    // -----------------------------------------------------------------------
    // 標準 DICOM ポート番号のバリデーション（仕様チェック）
    // -----------------------------------------------------------------------

    @Test
    public void testStandardDicomPort_wellKnown() {
        // DICOM Well-Known port: 104, 11112
        DicomCommunicationNode node104   = new DicomCommunicationNode("A", "AET", "h", 104,   "");
        DicomCommunicationNode node11112 = new DicomCommunicationNode("A", "AET", "h", 11112, "");
        assertEquals(104,   node104.getPort());
        assertEquals(11112, node11112.getPort());
    }

    // -----------------------------------------------------------------------
    // ヘルパー
    // -----------------------------------------------------------------------

    private HashMap<String, Object> buildNodeMap(String nickname, String aet,
                                                  String host, int port) {
        HashMap<String, Object> m = new HashMap<>();
        m.put("logicalname",   nickname);
        m.put("aetitle",       aet);
        m.put("hostname",      host);
        m.put("port",          port);
        m.put("ciphers",       "");
        m.put("tls_enabled",   Boolean.FALSE);
        m.put("retrievetype",  "C-MOVE");
        m.put("wadocontext",   "/wado");
        m.put("wadoport",      8080);
        m.put("wadoprotocol",  "http");
        m.put("retrievets",    "");
        return m;
    }
}
