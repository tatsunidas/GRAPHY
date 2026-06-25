package com.vis.dicom.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.vis.core.log.Log;

/**
 * Lifecycle for GRAPHY's DICOMweb (QIDO-RS/WADO-RS/STOW-RS) server, built on
 * the JDK's own {@code com.sun.net.httpserver.HttpServer} (no new runtime
 * dependency). Sibling to {@code com.vis.dicom.dimse.DcmQRSCP} for the
 * DIMSE server, but does not implement that interface (DcmQRSCP's
 * {@code DicomServer} contract is shaped around dcm4che's CLI-style args,
 * which don't apply to an HTTP server).
 *
 * All three services are mounted under one context ("{contextPath}/studies")
 * because the JDK HttpServer allows only one handler per exact context path;
 * a single dispatcher routes by HTTP method and path-segment shape:
 * POST /studies -> STOW-RS; GET with an odd segment count (.../studies,
 * .../series, .../instances as the last segment) -> QIDO-RS (list); GET with
 * an even segment count (last segment is a UID) -> WADO-RS (retrieve).
 *
 * @author tatsunidas
 */
public class DicomWebServer {

	private final int port;
	private final String contextPath;
	private HttpServer httpServer;
	private ExecutorService executor;

	public DicomWebServer(int port, String contextPath) {
		this.port = port;
		this.contextPath = (contextPath == null || contextPath.isEmpty()) ? "/dicomweb" : contextPath;
	}

	public void start() throws IOException {
		if (httpServer != null) {
			return;
		}
		httpServer = HttpServer.create(new InetSocketAddress(port), 0);
		executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()),
				namedThreadFactory());
		httpServer.setExecutor(executor);

		QidoRsHandler qido = new QidoRsHandler();
		WadoRsHandler wado = new WadoRsHandler();
		StowRsHandler stow = new StowRsHandler();
		// ★ コンテキストはcontextPath自体に登録する("studies"はそこに含めない)。
		// QidoRsHandler/WadoRsHandlerは「studiesがsegments[0]である」前提でパースしているため、
		// ここで"studies"まで消費してしまうと整合しなくなる。
		httpServer.createContext(contextPath, exchange -> dispatch(exchange, contextPath, qido, wado, stow));

		httpServer.start();
		Log.logger.info("DICOMweb server started on port " + port + ", context " + contextPath);
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

	private void dispatch(HttpExchange exchange, String registeredContextPath, HttpHandler qido, HttpHandler wado,
			HttpHandler stow) throws IOException {
		String method = exchange.getRequestMethod();
		if ("POST".equalsIgnoreCase(method)) {
			stow.handle(exchange);
			return;
		}
		String path = exchange.getRequestURI().getPath();
		String suffix = path.length() > registeredContextPath.length() ? path.substring(registeredContextPath.length())
				: "";
		while (suffix.startsWith("/")) {
			suffix = suffix.substring(1);
		}
		// 末尾が"studies"/"series"/"instances"(一覧)なら奇数セグメント、UIDで終わる(取得)なら偶数セグメント
		int segmentCount = suffix.isEmpty() ? 0 : suffix.split("/").length;
		if (segmentCount % 2 == 1) {
			qido.handle(exchange); // .../studies, .../series, .../instances (list)
		} else {
			wado.handle(exchange); // .../{uid} (retrieve)
		}
	}

	private java.util.concurrent.ThreadFactory namedThreadFactory() {
		AtomicInteger counter = new AtomicInteger(0);
		return r -> new Thread(r, "dicomweb-http-" + counter.incrementAndGet());
	}
}
