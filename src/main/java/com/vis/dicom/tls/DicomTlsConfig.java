/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.dicom.tls;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;

/**
 * GRAPHY自局のDICOM DIMSE TLS設定(相互TLS用の鍵・証明書)を保持し、dcm4cheの
 * {@link Device} / {@link Connection} へ適用する共通ヘルパー。
 *
 * 鍵・証明書はアプリ全体で単一のJKS keystore(自局の鍵+証明書)＋単一truststore
 * (信頼する相手のCA/証明書)で管理する。SCP(listener)とSCU(送信)の両方からこのクラスを
 * 再利用する。
 *
 * 設定の永続化は LISTENER テーブル(自局設定)。{@code DatabaseHandler.getDimseTlsConfig()}
 * がこのクラスのインスタンスを組み立てて返す。
 */
public class DicomTlsConfig {

	/** TLSハンドシェイクで使うデフォルトプロトコル。 */
	public static final String[] DEFAULT_PROTOCOLS = { "TLSv1.2", "TLSv1.3" };

	/**
	 * デフォルトの暗号スイート(カンマ/コロン区切り文字列)。
	 * - TLS_AES_128_GCM_SHA256                : TLS1.3。モダンJDKで有効。
	 * - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 : TLS1.2のモダンcipher。JDK11/17/21いずれでも有効で、
	 *                                           TLS1.2専用の相手にも対応できる。
	 * - TLS_RSA_WITH_AES_128_CBC_SHA          : 互換性のためのレガシーcipher(JDK17+ではデフォルト無効)。
	 *                                           古いPACS相手の保険として末尾に残す。
	 */
	public static final String DEFAULT_CIPHERS_STRING =
			"TLS_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_128_CBC_SHA";

	/** DICOMのTLS慣習ポート(平文の104/11112とは別)。 */
	public static final int DEFAULT_TLS_PORT = 2762;

	private boolean enabled;
	private int tlsPort = DEFAULT_TLS_PORT;
	private String keystorePath;
	private String keystorePassword;
	private String truststorePath;
	private String truststorePassword;
	private String[] protocols = DEFAULT_PROTOCOLS;
	private String[] ciphers = splitList(DEFAULT_CIPHERS_STRING);

	public DicomTlsConfig() {
	}

	// ---------------------------------------------------------------- accessor

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getTlsPort() {
		return tlsPort;
	}

	public void setTlsPort(int tlsPort) {
		this.tlsPort = tlsPort;
	}

	public String getKeystorePath() {
		return keystorePath;
	}

	public void setKeystorePath(String keystorePath) {
		this.keystorePath = keystorePath;
	}

	public String getKeystorePassword() {
		return keystorePassword;
	}

	public void setKeystorePassword(String keystorePassword) {
		this.keystorePassword = keystorePassword;
	}

	public String getTruststorePath() {
		return truststorePath;
	}

	public void setTruststorePath(String truststorePath) {
		this.truststorePath = truststorePath;
	}

	public String getTruststorePassword() {
		return truststorePassword;
	}

	public void setTruststorePassword(String truststorePassword) {
		this.truststorePassword = truststorePassword;
	}

	public String[] getProtocols() {
		return protocols;
	}

	public void setProtocols(String[] protocols) {
		this.protocols = (protocols != null && protocols.length > 0) ? protocols : DEFAULT_PROTOCOLS;
	}

	public String[] getCiphers() {
		return ciphers;
	}

	public void setCiphers(String[] ciphers) {
		this.ciphers = (ciphers != null && ciphers.length > 0) ? ciphers : splitList(DEFAULT_CIPHERS_STRING);
	}

	/**
	 * TLSを実際に張れる設定が揃っているか。enabledかつkeystore/truststoreの両方が指定され、
	 * それぞれのファイルが存在することを要求する(相互TLSのため両方必須)。
	 */
	public boolean isUsable() {
		if (!enabled) {
			return false;
		}
		if (isBlank(keystorePath) || isBlank(truststorePath)) {
			return false;
		}
		return new File(keystorePath).isFile() && new File(truststorePath).isFile();
	}

	// ------------------------------------------------------------------ apply

	/**
	 * 自局のkeystore(鍵+証明書)とtruststore(信頼する相手)を{@link Device}へ設定する。
	 * これにより{@code device.sslContext()/keyManagers()/trustManagers()}が機能し、
	 * SCP/SCU双方のTLSハンドシェイクで自局証明書の提示と相手証明書の検証ができる。
	 */
	public void applyKeyMaterialToDevice(Device device) {
		device.setKeyStoreURL(toUrl(keystorePath));
		device.setKeyStoreType("JKS");
		device.setKeyStorePin(keystorePassword);
		device.setKeyStoreKeyPin(keystorePassword);
		device.setTrustStoreURL(toUrl(truststorePath));
		device.setTrustStoreType("JKS");
		device.setTrustStorePin(truststorePassword);
	}

	/**
	 * {@link Connection}にTLSを適用する。cipher suitesを設定すると
	 * {@code Connection.isTls()}がtrueになり、その接続はTLS化される。
	 *
	 * @param c              対象のConnection(ローカル/リモート/listenerいずれも)
	 * @param cipherSuites   使用する暗号スイート(空ならこの設定のデフォルトcipher)
	 * @param needClientAuth listener側はtrue(相互TLS=クライアント証明書を要求)、SCU側はfalse
	 */
	public void applyTlsToConnection(Connection c, String[] cipherSuites, boolean needClientAuth) {
		String[] cs = (cipherSuites != null && cipherSuites.length > 0) ? cipherSuites : this.ciphers;
		c.setTlsCipherSuites(cs);
		if (this.protocols != null && this.protocols.length > 0) {
			c.setTlsProtocols(this.protocols);
		}
		c.setTlsNeedClientAuth(needClientAuth);
	}

	// -------------------------------------------------- SCU side (ThreadLocal)

	/**
	 * SCU(送信)側で「次に張る接続をTLSにするか/どのcipherで」を、呼び出し元スレッドから
	 * 下層の静的SCUメソッド({@code StoreSCU.main}等)へ運ぶためのスレッドローカル。
	 * 値が非nullなら「この接続はTLSを使う」を意味する(空配列でもデフォルトcipherでTLS)。
	 */
	private static final ThreadLocal<String[]> SCU_TLS_CIPHERS = new ThreadLocal<>();

	/**
	 * 接続先ノードのTLS要求を現在スレッドに設定する。SCU呼び出しの直前に呼び、
	 * 必ずfinallyで{@link #clearScuTls()}すること。
	 *
	 * @param tlsEnabled ノードの「Use TLS」フラグ
	 * @param ciphersCsv ノードの暗号スイート(カンマ/コロン区切り。空ならグローバルのデフォルト)
	 */
	public static void requestScuTls(boolean tlsEnabled, String ciphersCsv) {
		if (tlsEnabled) {
			SCU_TLS_CIPHERS.set(splitList(ciphersCsv)); // 空配列でも「TLSを使う」意思表示
		} else {
			SCU_TLS_CIPHERS.remove();
		}
	}

	public static void clearScuTls() {
		SCU_TLS_CIPHERS.remove();
	}

	/**
	 * SCU接続(Device/ローカルConnection/リモートConnection)の構築直後に呼ぶ。現在スレッドに
	 * TLS要求があれば、自局のkeystore/truststoreをDeviceへ、cipher/protocolを両Connectionへ
	 * 適用する。要求が無ければ何もしない(平文)。
	 *
	 * @return TLSを適用したらtrue
	 */
	public static boolean applyScuTlsIfRequested(Device device, Connection localConn, Connection remote) {
		String[] requested = SCU_TLS_CIPHERS.get();
		if (requested == null) {
			return false; // TLS要求なし=平文
		}
		DicomTlsConfig cfg = com.vis.db.DatabaseHandler.getInstance().getDimseTlsConfig();
		if (cfg == null) {
			return false;
		}
		String[] ciphers = (requested.length > 0) ? requested : cfg.getCiphers();
		cfg.applyKeyMaterialToDevice(device);
		cfg.applyTlsToConnection(localConn, ciphers, false);
		cfg.applyTlsToConnection(remote, ciphers, false);
		return true;
	}

	// ----------------------------------------------------------------- helper

	/** カンマまたはコロン区切りの暗号スイート文字列を配列に変換する。 */
	public static String[] splitList(String seq) {
		if (seq == null || seq.trim().isEmpty()) {
			return new String[0];
		}
		List<String> list = new ArrayList<>();
		for (String token : seq.split("[,:]")) {
			String t = token.trim();
			if (!t.isEmpty()) {
				list.add(t);
			}
		}
		return list.toArray(new String[0]);
	}

	/** ローカルパスをdcm4cheが受け付けるURL(file:...)に変換する。 */
	private static String toUrl(String path) {
		if (path == null) {
			return null;
		}
		if (path.startsWith("file:") || path.contains("://")) {
			return path;
		}
		return new File(path).toURI().toString();
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
