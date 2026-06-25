package com.vis.dicom.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.vis.core.log.Log;
import com.vis.db.DatabaseHandler;

/**
 * QIDO-RS (Query based on ID for DICOM Objects). Maps:
 * GET {ctx}/studies
 * GET {ctx}/studies/{studyUID}/series
 * GET {ctx}/studies/{studyUID}/series/{seriesUID}/instances
 * onto the same DatabaseHandler query methods the DIMSE C-FIND *QueryTaskUsingDB classes already use.
 *
 * @author tatsunidas
 */
public class QidoRsHandler implements HttpHandler {

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		try {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				throw new DicomWebException(405, "QIDO-RS only supports GET.");
			}
			String contextPrefix = exchange.getHttpContext().getPath();
			String path = exchange.getRequestURI().getPath();
			String suffix = path.length() > contextPrefix.length() ? path.substring(contextPrefix.length()) : "";
			while (suffix.startsWith("/")) {
				suffix = suffix.substring(1);
			}
			String[] segments = suffix.isEmpty() ? new String[0] : suffix.split("/");
			Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

			String json;
			if (segments.length == 1 && "studies".equals(segments[0])) {
				json = handleStudies(params);
			} else if (segments.length == 3 && "studies".equals(segments[0]) && "series".equals(segments[2])) {
				json = handleSeries(segments[1]);
			} else if (segments.length == 5 && "studies".equals(segments[0]) && "series".equals(segments[2])
					&& "instances".equals(segments[4])) {
				json = handleInstances(segments[1], segments[3]);
			} else {
				throw new DicomWebException(404, "Unknown QIDO-RS resource: " + path);
			}

			if (json == null) {
				exchange.sendResponseHeaders(204, -1);
				exchange.close();
				return;
			}
			byte[] body = json.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/dicom+json");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		} catch (DicomWebException e) {
			respondError(exchange, e.getStatusCode(), e.getMessage());
		} catch (Exception e) {
			Log.logger.severe("QidoRsHandler: unexpected error.\n" + e);
			respondError(exchange, 500, "Internal error.");
		}
	}

	private String handleStudies(Map<String, String> params) throws DicomWebException {
		DatabaseHandler db = requireDb();
		String patientName = likeParam(params.get("PatientName"));
		String patientID = likeParam(params.get("PatientID"));
		String dob = likeParam(params.get("PatientBirthDate"));
		String accNo = likeParam(params.get("AccessionNumber"));
		String studyDate = likeParam(params.get("StudyDate"));
		String studyDesc = likeParam(params.get("StudyDescription"));
		String modality = likeParam(params.get("ModalitiesInStudy"));

		List<Map<String, String>> rows = new ArrayList<>(
				db.listStudies(patientName, patientID, dob, accNo, studyDate, studyDesc, modality));
		if (rows.isEmpty()) {
			return null;
		}
		return DicomJsonWriter.studiesToJsonArray(rows);
	}

	private String handleSeries(String studyUID) throws DicomWebException {
		DatabaseHandler db = requireDb();
		String patID = db.getPatientIDByStudyUID(studyUID);
		if (patID == null) {
			throw new DicomWebException(404, "Study not found: " + studyUID);
		}
		List<HashMap<String, String>> rows = db.getSeriesInfoByUIDs(patID, studyUID);
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		return DicomJsonWriter.seriesToJsonArray(new ArrayList<>(rows));
	}

	private String handleInstances(String studyUID, String seriesUID) throws DicomWebException {
		DatabaseHandler db = requireDb();
		String patID = db.getPatientIDByStudyUID(studyUID);
		if (patID == null) {
			throw new DicomWebException(404, "Study not found: " + studyUID);
		}
		List<HashMap<String, String>> rows = db.getImagesInfoByUIDs(patID, studyUID, seriesUID);
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		return DicomJsonWriter.instancesToJsonArray(new ArrayList<>(rows));
	}

	private DatabaseHandler requireDb() throws DicomWebException {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			throw new DicomWebException(503, "Database is not available.");
		}
		return db;
	}

	/**
	 * listStudies() builds SQL by string concatenation (pre-existing, out of
	 * scope to refactor here), so every value coming from an HTTP query
	 * parameter is escaped before being handed to it, as defense-in-depth for
	 * this new internet-facing entry point. Missing params become "%" (match-all).
	 */
	private String likeParam(String value) {
		if (value == null || value.isEmpty()) {
			return "%";
		}
		return value.toUpperCase().replace("'", "''");
	}

	private Map<String, String> parseQuery(String query) {
		Map<String, String> params = new HashMap<>();
		if (query == null || query.isEmpty()) {
			return params;
		}
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String key = urlDecode(pair.substring(0, eq));
			String value = urlDecode(pair.substring(eq + 1));
			params.put(key, value);
		}
		return params;
	}

	private String urlDecode(String s) {
		try {
			return URLDecoder.decode(s, StandardCharsets.UTF_8);
		} catch (Exception e) {
			return s;
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
