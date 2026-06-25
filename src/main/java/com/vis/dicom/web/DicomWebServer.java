package com.vis.dicom.web;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.vis.core.log.Log;

/**
 * Lifecycle for GRAPHY's DICOMweb (QIDO-RS/WADO-RS/STOW-RS) server, built on
 * the JDK's own {@code com.sun.net.httpserver.HttpServer} (no new runtime
 * dependency). Sibling to {@code com.vis.dicom.dimse.DcmQRSCP} for the
 * DIMSE server.
 *
 * <h3>HTTP routing</h3>
 * All three services are mounted under one context ({@code contextPath}) and a
 * single dispatcher routes by HTTP method and path-segment shape:
 * POST → STOW-RS; GET with odd segment count → QIDO-RS (list); GET with even
 * segment count → WADO-RS (retrieve).
 *
 * <h3>HTTPS mode</h3>
 * When {@code keystorePath} is non-empty the server uses {@code HttpsServer}
 * with a JKS keystore. Generate a self-signed keystore with:
 * <pre>
 *   keytool -genkeypair -alias graphy -keyalg RSA -keysize 2048
 *           -validity 3650 -keystore graphy-keystore.jks
 *           -storepass &lt;password&gt; -keypass &lt;password&gt;
 * </pre>
 *
 * <h3>Basic認証</h3>
 * {@code authEnabled=true} にすると JDK の {@link BasicAuthenticator} が有効になる。
 * パスワードは SHA-256 ハッシュで保存される({@link #hashPassword(String)})。
 *
 * @author tatsunidas
 */
public class DicomWebServer {

	private final int port;
	private final String contextPath;
	private final boolean httpsEnabled;
	private final String keystorePath;
	private final String keystorePassword;
	private final boolean authEnabled;
	private final String authUsername;
	private final String authPasswordHash;

	private HttpServer httpServer;
	private ExecutorService executor;

	/** HTTP mode (backward-compatible constructor). */
	public DicomWebServer(int port, String contextPath) {
		this(port, contextPath, false, null, null, false, null, null);
	}

	/** Full constructor. */
	public DicomWebServer(int port, String contextPath,
			boolean httpsEnabled, String keystorePath, String keystorePassword,
			boolean authEnabled, String authUsername, String authPasswordHash) {
		this.port = port;
		this.contextPath = (contextPath == null || contextPath.isEmpty()) ? "/dicomweb" : contextPath;
		this.httpsEnabled = httpsEnabled;
		this.keystorePath = keystorePath;
		this.keystorePassword = keystorePassword;
		this.authEnabled = authEnabled;
		this.authUsername = authUsername;
		this.authPasswordHash = authPasswordHash;
	}

	public void start() throws IOException {
		if (httpServer != null) {
			return;
		}

		// --- HTTP or HTTPS server ---
		if (httpsEnabled && keystorePath != null && !keystorePath.isBlank()) {
			try {
				SSLContext sslContext = buildSSLContext(keystorePath, keystorePassword);
				HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(port), 0);
				httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
					@Override
					public void configure(HttpsParameters params) {
						SSLEngine engine = getSSLContext().createSSLEngine();
						params.setNeedClientAuth(false);
						params.setCipherSuites(engine.getEnabledCipherSuites());
						params.setProtocols(engine.getEnabledProtocols());
						SSLParameters sp = getSSLContext().getSupportedSSLParameters();
						params.setSSLParameters(sp);
					}
				});
				httpServer = httpsServer;
			} catch (Exception e) {
				throw new IOException("DICOMweb TLS設定に失敗しました(keystorePath=" + keystorePath + "): " + e.getMessage(), e);
			}
		} else {
			httpServer = HttpServer.create(new InetSocketAddress(port), 0);
		}

		executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()),
				namedThreadFactory());
		httpServer.setExecutor(executor);

		QidoRsHandler qido = new QidoRsHandler();
		WadoRsHandler wado = new WadoRsHandler();
		StowRsHandler stow = new StowRsHandler();

		HttpContext ctx = httpServer.createContext(contextPath,
				exchange -> dispatch(exchange, contextPath, qido, wado, stow));

		// --- Basic認証 ---
		if (authEnabled && authUsername != null && !authUsername.isBlank()
				&& authPasswordHash != null && !authPasswordHash.isBlank()) {
			final String expectedHash = authPasswordHash;
			final String expectedUser = authUsername;
			ctx.setAuthenticator(new BasicAuthenticator("GRAPHY DICOMweb") {
				@Override
				public boolean checkCredentials(String user, String password) {
					if (!expectedUser.equals(user)) {
						return false;
					}
					try {
						return expectedHash.equalsIgnoreCase(hashPassword(password));
					} catch (Exception e) {
						Log.logger.warning("DICOMweb Basic認証: パスワードのハッシュ計算に失敗しました。");
						return false;
					}
				}
			});
			Log.logger.info("DICOMweb Basic認証が有効です (user=" + authUsername + ")。");
		}

		httpServer.start();
		String scheme = (httpServer instanceof HttpsServer) ? "https" : "http";
		Log.logger.info("DICOMweb server started on " + scheme + " port " + port + ", context " + contextPath);
	}

	public void stop() {
		if (httpServer != null) {
			httpServer.stop(0);
			httpServer = null;
			Log.logger.info("DICOMweb server stopped (port " + port + ").");
		}
		if (executor != null) {
			executor.shutdown();
			executor = null;
		}
	}

	public boolean isRunning() {
		return httpServer != null;
	}

	/**
	 * パスワードを SHA-256 でハッシュ化し、小文字 hex 文字列として返す。
	 * DB 保存時および認証チェック時の両方で使用する。
	 */
	public static String hashPassword(String password) throws NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder(64);
		for (byte b : hash) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	private void dispatch(HttpExchange exchange, String registeredContextPath,
			HttpHandler qido, HttpHandler wado, HttpHandler stow) throws IOException {
		String method = exchange.getRequestMethod();
		if ("POST".equalsIgnoreCase(method)) {
			stow.handle(exchange);
			return;
		}
		String path = exchange.getRequestURI().getPath();
		String suffix = path.length() > registeredContextPath.length()
				? path.substring(registeredContextPath.length()) : "";
		while (suffix.startsWith("/")) {
			suffix = suffix.substring(1);
		}
		int segmentCount = suffix.isEmpty() ? 0 : suffix.split("/").length;
		if (segmentCount % 2 == 1) {
			qido.handle(exchange);
		} else {
			wado.handle(exchange);
		}
	}

	private SSLContext buildSSLContext(String ksPath, String ksPassword) throws Exception {
		char[] password = ksPassword != null ? ksPassword.toCharArray() : new char[0];
		KeyStore ks = KeyStore.getInstance("JKS");
		try (FileInputStream fis = new FileInputStream(ksPath)) {
			ks.load(fis, password);
		}
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, password);
		SSLContext ctx = SSLContext.getInstance("TLS");
		ctx.init(kmf.getKeyManagers(), null, null);
		return ctx;
	}

	private java.util.concurrent.ThreadFactory namedThreadFactory() {
		AtomicInteger counter = new AtomicInteger(0);
		return r -> new Thread(r, "dicomweb-http-" + counter.incrementAndGet());
	}
}
