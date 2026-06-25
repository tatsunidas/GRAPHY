package com.vis.dicom.web;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.vis.core.log.Log;
import com.vis.db.DatabaseHandler;

/**
 * WADO-RS (retrieve). Maps:
 * GET {ctx}/studies/{studyUID}
 * GET {ctx}/studies/{studyUID}/series/{seriesUID}
 * GET {ctx}/studies/{studyUID}/series/{seriesUID}/instances/{sopUID}
 * onto DatabaseHandler.getImageInstanceInfo(...) (the same FileStoreUrl lookup C-GET/RetrieveTaskImpl use),
 * returning a multipart/related response with the raw DICOM Part10 file(s).
 *
 * @author tatsunidas
 */
public class WadoRsHandler implements HttpHandler {

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		try {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				throw new DicomWebException(405, "WADO-RS only supports GET.");
			}
			String contextPrefix = exchange.getHttpContext().getPath();
			String path = exchange.getRequestURI().getPath();
			String suffix = path.length() > contextPrefix.length() ? path.substring(contextPrefix.length()) : "";
			while (suffix.startsWith("/")) {
				suffix = suffix.substring(1);
			}
			String[] segments = suffix.isEmpty() ? new String[0] : suffix.split("/");

			String studyUID;
			String seriesUID = null;
			String sopUID = null;
			if (segments.length == 2 && "studies".equals(segments[0])) {
				studyUID = segments[1];
			} else if (segments.length == 4 && "studies".equals(segments[0]) && "series".equals(segments[2])) {
				studyUID = segments[1];
				seriesUID = segments[3];
			} else if (segments.length == 6 && "studies".equals(segments[0]) && "series".equals(segments[2])
					&& "instances".equals(segments[4])) {
				studyUID = segments[1];
				seriesUID = segments[3];
				sopUID = segments[5];
			} else {
				throw new DicomWebException(404, "Unknown WADO-RS resource: " + path);
			}

			DatabaseHandler db = DatabaseHandler.getInstance();
			if (db == null) {
				throw new DicomWebException(503, "Database is not available.");
			}
			ArrayList<HashMap<String, String>> rows = db.getImageInstanceInfo(null, studyUID, seriesUID, sopUID);
			if (rows == null || rows.isEmpty()) {
				throw new DicomWebException(404, "No matching instance(s) found.");
			}

			String boundary = MultipartRelatedWriter.newBoundary();
			exchange.getResponseHeaders().set("Content-Type",
					MultipartRelatedWriter.contentTypeHeaderFor(boundary, "application/dicom"));
			exchange.sendResponseHeaders(200, 0); // 0 => chunked, avoids precomputing total length
			try (OutputStream os = exchange.getResponseBody()) {
				MultipartRelatedWriter writer = new MultipartRelatedWriter(os, boundary, "application/dicom");
				for (HashMap<String, String> row : rows) {
					File f = resolveFile(row.get("URI"));
					if (f == null || !f.isFile()) {
						Log.logger.warning("WadoRsHandler: stored file missing on disk, skipping: " + row.get("URI"));
						continue;
					}
					writer.writePart(f);
				}
				writer.writeClosingDelimiter();
			}
		} catch (DicomWebException e) {
			respondError(exchange, e.getStatusCode(), e.getMessage());
		} catch (Exception e) {
			Log.logger.severe("WadoRsHandler: unexpected error.\n" + e);
			respondError(exchange, 500, "Internal error.");
		}
	}

	private File resolveFile(String uriString) {
		if (uriString == null) {
			return null;
		}
		try {
			return new File(new URI(uriString));
		} catch (Exception e) {
			return null;
		}
	}

	private void respondError(HttpExchange exchange, int status, String message) throws IOException {
		byte[] body = ("{\"error\":\"" + message.replace("\"", "'") + "\"}").getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(body);
		}
	}
}
