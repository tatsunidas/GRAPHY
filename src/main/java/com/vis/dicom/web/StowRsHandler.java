package com.vis.dicom.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.vis.core.log.Log;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;

/**
 * STOW-RS (store). POST {ctx}/studies (and {ctx}/studies/{studyUID}, target study not enforced).
 * Parses a multipart/related request body, and for each DICOM part calls
 * DatabaseHandler.writeDatasetInfo(...) directly — the same final persistence
 * method DIMSE C-STORE's CStoreSCPImpl.writeGraphyDB(...) calls — instead of
 * looping a part back through a DIMSE association (DimseUtilities.store()'s
 * approach), so there is exactly one DB/file write path, reachable even if
 * the DIMSE listener happens to be stopped.
 *
 * @author tatsunidas
 */
public class StowRsHandler implements HttpHandler {

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		File tempDir = null;
		try {
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				throw new DicomWebException(405, "STOW-RS only supports POST.");
			}
			String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
			String boundary = MultipartRelatedReader.extractBoundary(contentType);
			if (boundary == null) {
				throw new DicomWebException(400, "Missing multipart/related boundary in Content-Type.");
			}
			DatabaseHandler db = DatabaseHandler.getInstance();
			if (db == null) {
				throw new DicomWebException(503, "Database is not available.");
			}

			tempDir = com.vis.core.util.Utils.createNewDirInTemp();
			MultipartRelatedReader reader = new MultipartRelatedReader(exchange.getRequestBody(), boundary);

			List<String[]> succeeded = new ArrayList<>(); // {sopClassUID, sopInstanceUID}
			List<String> failed = new ArrayList<>();
			int partIndex = 0;
			while (reader.nextPart()) {
				partIndex++;
				byte[] partBody = reader.currentPartBody();
				if (partBody == null || partBody.length == 0) {
					continue;
				}
				storeOnePart(db, tempDir, partIndex, partBody, succeeded, failed);
			}

			String json = DicomJsonWriter.stowResponse(succeeded, failed);
			byte[] body = json.getBytes(StandardCharsets.UTF_8);
			int status = succeeded.isEmpty() ? 400 : 200;
			exchange.getResponseHeaders().set("Content-Type", "application/dicom+json");
			exchange.sendResponseHeaders(status, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		} catch (DicomWebException e) {
			respondError(exchange, e.getStatusCode(), e.getMessage());
		} catch (Exception e) {
			Log.logger.severe("StowRsHandler: unexpected error.\n" + e);
			respondError(exchange, 500, "Internal error.");
		} finally {
			if (tempDir != null) {
				try {
					FileUtils.deleteDirectory(tempDir);
				} catch (IOException e) {
					Log.logger.warning("StowRsHandler: failed to delete temp dir " + tempDir.getAbsolutePath());
				}
			}
		}
	}

	private void storeOnePart(DatabaseHandler db, File tempDir, int partIndex, byte[] partBody,
			List<String[]> succeeded, List<String> failed) {
		File received = new File(tempDir, "part-" + partIndex + ".dcm");
		try (OutputStream out = new FileOutputStream(received)) {
			out.write(partBody);
		} catch (IOException e) {
			Log.logger.warning("StowRsHandler: failed to buffer part " + partIndex + " to disk.\n" + e);
			return;
		}

		DicomObject dataset;
		try {
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
			reader.read(received.getAbsolutePath(), false);
			dataset = reader.getHeader();
		} catch (Exception e) {
			Log.logger.warning("StowRsHandler: part " + partIndex + " is not a parseable DICOM file.\n" + e);
			return;
		}
		if (dataset == null) {
			return;
		}

		String patientID = dataset.getString(0x00100020); // PatientID
		String studyUID = dataset.getString(0x0020000D); // StudyInstanceUID
		String seriesUID = dataset.getString(0x0020000E); // SeriesInstanceUID
		String sopUID = dataset.getString(0x00080018); // SOPInstanceUID
		String sopClassUID = dataset.getString(0x00080016); // SOPClassUID
		if (patientID == null || studyUID == null || seriesUID == null || sopUID == null) {
			Log.logger.warning("StowRsHandler: part " + partIndex + " is missing required UIDs, rejecting.");
			if (sopUID != null) {
				failed.add(sopUID);
			}
			return;
		}

		try {
			File destDir = new File(db.getLocalDBLocation(),
					"STOW" + File.separator + patientID + File.separator + studyUID + File.separator + seriesUID);
			if (!destDir.exists()) {
				destDir.mkdirs();
			}
			File dest = new File(destDir, sopUID + ".dcm");
			Files.move(received.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			boolean ok = db.writeDatasetInfo(dataset, dest.getAbsolutePath());
			if (ok) {
				succeeded.add(new String[] { sopClassUID, sopUID });
			} else {
				// dedup or insert failure: don't leave an orphaned duplicate file on disk
				dest.delete();
				failed.add(sopUID);
			}
		} catch (Exception e) {
			Log.logger.severe("StowRsHandler: failed to persist part " + partIndex + " (SOPInstanceUID=" + sopUID
					+ ").\n" + e);
			failed.add(sopUID);
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
