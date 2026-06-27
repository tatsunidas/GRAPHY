package com.vis.core.reporting;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import org.dcm4che3.data.Attributes;

import com.vis.core.log.Log;
import com.vis.core.reporting.sr.SRtoHtml;
import com.vis.core.reporting.sr.SRWriter;
import com.vis.core.reporting.ui.SRHtmlViewerWindow;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.UID;
import com.vis.dicom.dcm4cheImpl.DicomObjectChe;
import com.vis.dicom.dcm4cheImpl.DicomReaderChe;
import com.vis.dicom.dimse.DimseUtilities;
import com.vis.dicom.dimse.StoreSCU;

/**
 * Single orchestration seam for the reporting feature. UI and menus call only
 * this class.
 * <ul>
 *   <li>{@link #saveDraft(ReportDocument)} / {@link #listReports} / {@link #loadReport}
 *       / {@link #deleteReport} — REPORT table CRUD (HTML is the source of truth).</li>
 *   <li>{@link #finalizeAsSR(ReportDocument)} — build a DICOM SR, write it, register it
 *       into the store/DB, and stamp the SR SOP Instance UID back onto the report.</li>
 *   <li>{@link #openSr} — render an SR (or RDSR) instance to HTML and show it.</li>
 * </ul>
 *
 * @author tatsunidas
 */
public class ReportService {

	private static final Logger logger = Log.logger;

	private final DatabaseHandler db;

	public ReportService() {
		this.db = DatabaseHandler.getInstance();
	}

	public ReportService(DatabaseHandler db) {
		this.db = db;
	}

	// ---- CRUD ----------------------------------------------------------------

	public void saveDraft(ReportDocument doc) {
		if (doc == null) {
			return;
		}
		doc.touchModified();
		db.insertReport(doc.readContext());
	}

	public List<ReportDocument> listReports(String patientID, String studyUID) {
		List<ReportDocument> out = new ArrayList<>();
		ArrayList<HashMap<String, Object>> rows = db.loadReportContextFromStudy(patientID, studyUID);
		if (rows != null) {
			for (HashMap<String, Object> row : rows) {
				out.add(ReportDocument.fromContext(row));
			}
		}
		return out;
	}

	public List<ReportDocument> listReportsForPatient(String patientID) {
		List<ReportDocument> out = new ArrayList<>();
		ArrayList<HashMap<String, Object>> rows = db.loadReportContextFromPatient(patientID);
		if (rows != null) {
			for (HashMap<String, Object> row : rows) {
				out.add(ReportDocument.fromContext(row));
			}
		}
		return out;
	}

	public ReportDocument loadReport(String reportId) {
		HashMap<String, Object> ctx = db.loadReportContext(reportId);
		return ctx == null ? null : ReportDocument.fromContext(ctx);
	}

	/**
	 * Imported SR-family instances in a study that are NOT GRAPHY-authored reports (i.e. have
	 * no REPORT row). These are view-only.
	 *
	 * @return list of {@code [seriesUID, sopUID, typeLabel]} rows.
	 */
	/**
	 * Returns all study UIDs (and their dates) for the given patient, as
	 * {@code String[]{studyUID, studyDate}}. Used by PATIENT mode to enumerate
	 * imported SRs across all studies.
	 */
	public List<String[]> listStudiesForPatient(String patID) {
		return db.getStudyUIDsForPatient(patID);
	}

	public List<String[]> listImportedSrInStudy(String patID, String studyUID) {
		List<String[]> out = new ArrayList<>();
		java.util.Set<String> exclude = new java.util.HashSet<>();
		for (ReportDocument d : listReports(patID, studyUID)) {
			if (d.getSrSopInstanceUID() != null) {
				exclude.add(d.getSrSopInstanceUID());
			}
		}
		for (HashMap<String, String> inst : db.getReportInstancesInStudy(patID, studyUID)) {
			String sop = inst.get("SOPInstanceUID");
			if (sop == null || exclude.contains(sop)) {
				continue;
			}
			out.add(new String[] { inst.get("SeriesInstanceUID"), sop,
					com.vis.core.reporting.sr.SopClassUtil.reportTypeLabel(inst.get("SOPClassUID")) });
		}
		return out;
	}

	public void deleteReport(String reportId) {
		ReportDocument doc = loadReport(reportId);
		db.deleteReport(reportId);
		if (doc == null) return;
		// Cascade-delete the finalized SR DICOM object (otherwise it re-surfaces
		// as an "imported" SR after the REPORT row is gone).
		if (doc.getSrSopInstanceUID() != null) {
			deleteImportedSr(doc.getPatientID(), doc.getStudyUID(),
					doc.getSeriesUID(), doc.getSrSopInstanceUID());
		}
		// Cascade-delete the associated KO.
		if (doc.getKoSopInstanceUID() != null) {
			deleteImportedSr(doc.getPatientID(), doc.getStudyUID(),
					doc.getKoSeriesInstanceUID(), doc.getKoSopInstanceUID());
		}
	}

	public ReportDocument findReportByKoSopUID(String koSopUID) {
		HashMap<String, Object> ctx = db.findReportContextByKoSopUID(koSopUID);
		return ctx == null ? null : ReportDocument.fromContext(ctx);
	}

	/**
	 * Create an addendum draft from a finalized report. The returned document is
	 * pre-filled with the predecessor's metadata and a reference to its SR SOP UID.
	 * Call {@link #saveDraft(ReportDocument)} then open in the editor.
	 */
	public ReportDocument createAddendum(String predecessorReportId, String author) {
		ReportDocument original = loadReport(predecessorReportId);
		if (original == null) {
			return null;
		}
		ReportDocument addendum = ReportDocument.newAddendum(original, author);
		saveDraft(addendum);
		return addendum;
	}

	// ---- report locking (P2-2) -----------------------------------------------

	/**
	 * Attempt to acquire an exclusive editor lock for {@code user}. Returns true if
	 * the lock was granted, false if held by someone else.
	 */
	public boolean tryLock(String reportId, String user) {
		return db.tryLockReport(reportId, user);
	}

	/** Release the lock held by {@code user}. No-op if already unlocked. */
	public void unlock(String reportId, String user) {
		db.unlockReport(reportId, user);
	}

	/** Returns the current lock holder, or null if unlocked. */
	public String getLockHolder(String reportId) {
		return db.getReportLockHolder(reportId);
	}

	/**
	 * Delete an imported / derived SR instance (e.g. a measurement SR, RDSR, KO) from
	 * the local store. Removes the IMAGE row, its file, and any now-empty
	 * series/study directory+record (see {@link DatabaseHandler#deleteInstance}).
	 * Unlike {@link #deleteReport(String)} this acts on a stored DICOM object, not a
	 * REPORT row. Runs synchronous I/O — call off the EDT.
	 *
	 * @return {@code true} if the instance is no longer present after the call.
	 */
	public boolean deleteImportedSr(String patID, String studyUID, String seriesUID, String sopUID) {
		if (patID == null || studyUID == null || seriesUID == null || sopUID == null) {
			return false;
		}
		try {
			db.deleteInstance(patID, studyUID, seriesUID, sopUID);
			return db.getFileLocation(studyUID, seriesUID, sopUID) == null;
		} catch (Exception e) {
			logger.severe("ReportService - deleteImportedSr failed: " + e.getMessage());
			return false;
		}
	}

	// ---- finalize as DICOM SR ------------------------------------------------

	/**
	 * Build a DICOM SR from the report, write it to a temp file, register it into
	 * the GRAPHY store/DB (like any received instance), and update the report row
	 * (status FINAL, SR SOP/Series UID). Runs synchronous I/O — call off the EDT.
	 *
	 * @return the new SR SOP Instance UID, or {@code null} on failure.
	 */
	public String finalizeAsSR(ReportDocument doc) {
		if (doc == null) {
			return null;
		}
		Attributes ref = loadReferenceInstance(doc.getStudyUID());
		if (ref == null) {
			logger.severe("ReportService - finalizeAsSR: no reference instance found in study " + doc.getStudyUID());
			return null;
		}
		try {
			Attributes sr = new SRWriter().build(ref, doc);
			String sopUID = sr.getString(org.dcm4che3.data.Tag.SOPInstanceUID);
			String seriesUID = sr.getString(org.dcm4che3.data.Tag.SeriesInstanceUID);

			storeSr(sr, sopUID);

			// P2-8: generate a KO object when key images are present
			if (doc.getKeyImages() != null && !doc.getKeyImages().isEmpty()) {
				try {
					Attributes ko = new com.vis.core.reporting.sr.KeyObjectWriter().build(ref, doc);
					if (ko != null) {
						String koSopUID = ko.getString(org.dcm4che3.data.Tag.SOPInstanceUID);
						String koSeriesUID = ko.getString(org.dcm4che3.data.Tag.SeriesInstanceUID);
						storeSr(ko, koSopUID);
						doc.setKoSopInstanceUID(koSopUID);
						doc.setKoSeriesInstanceUID(koSeriesUID);
						logger.info("ReportService - KO generated: " + koSopUID);
					}
				} catch (Exception koEx) {
					logger.warning("ReportService - KO generation failed (SR still saved): " + koEx.getMessage());
				}
			}

			doc.setSrSopInstanceUID(sopUID);
			doc.setSeriesUID(seriesUID);
			doc.setStatus(ReportDocument.Status.FINAL);
			saveDraft(doc);
			logger.info("ReportService - report finalized as SR: " + sopUID);
			return sopUID;
		} catch (Exception e) {
			logger.severe("ReportService - finalizeAsSR failed: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Build a DICOM TID 1500 measurement SR from live measurements and register it
	 * into the GRAPHY store/DB. Unlike {@link #finalizeAsSR(ReportDocument)} there is
	 * no editable HTML draft — a measurement SR is a derived structured artifact, so
	 * no REPORT row is created; it surfaces as a view-only SR in the study (see
	 * {@link #listImportedSrInStudy}). Runs synchronous I/O — call off the EDT.
	 *
	 * @return the new SR SOP Instance UID, or {@code null} on failure.
	 */
	public String finalizeMeasurementsAsSR(
			com.vis.core.reporting.measurement.MeasurementReport report) {
		if (report == null || report.getGroups().isEmpty()) {
			logger.warning("ReportService - finalizeMeasurementsAsSR: empty report");
			return null;
		}
		Attributes ref = loadReferenceInstance(report.getStudyUID());
		if (ref == null) {
			logger.severe("ReportService - finalizeMeasurementsAsSR: no reference instance in study "
					+ report.getStudyUID());
			return null;
		}
		try {
			Attributes sr = new com.vis.core.reporting.sr.Tid1500Writer().build(ref, report);
			String sopUID = sr.getString(org.dcm4che3.data.Tag.SOPInstanceUID);
			storeSr(sr, sopUID);
			logger.info("ReportService - measurements finalized as SR: " + sopUID);
			return sopUID;
		} catch (Exception e) {
			logger.severe("ReportService - finalizeMeasurementsAsSR failed: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Write an SR dataset to a temp file and store it into the GRAPHY managed store
	 * (registering it in the DB like any received instance), deleting the temp source.
	 */
	private void storeSr(Attributes sr, String sopUID) throws Exception {
		File tmpDir = Files.createTempDirectory("graphy-sr").toFile();
		String dest = new File(tmpDir, sopUID).getAbsolutePath();
		DicomWriter writer = DicomWriter.newDicomWriter(DICOMBackend.getCurrent());
		writer.write(new DicomObjectChe(sr), UID.ExplicitVRLittleEndian.uid(), dest);
		String written = dest.endsWith(".dcm") ? dest : dest + ".dcm";
		DimseUtilities.store(written, true);
	}

	private Attributes loadReferenceInstance(String studyUID) {
		ArrayList<String> files = db.getFileLocationsStudyLevel(studyUID);
		if (files == null || files.isEmpty()) {
			return null;
		}
		for (String path : files) {
			if (path == null) {
				continue;
			}
			DicomReaderChe reader = new DicomReaderChe(path, false);
			if (reader.getHeader() != null) {
				return (Attributes) reader.getHeader();
			}
		}
		return null;
	}

	// ---- C-STORE to remote PACS (P1-5) ----------------------------------------

	/**
	 * Send a finalized SR to a remote PACS node via C-STORE. The SR file is looked
	 * up from the local store using the studyUID / seriesUID / sopUID triple.
	 * Runs synchronous network I/O — call off the EDT.
	 *
	 * @param dest the remote PACS node to send to.
	 * @param studyUID study UID of the SR.
	 * @param seriesUID series UID of the SR.
	 * @param sopUID SOP Instance UID of the SR.
	 * @return true on success, false on failure.
	 */
	public boolean sendSrToRemote(DicomCommunicationNode dest,
			String studyUID, String seriesUID, String sopUID) {
		if (dest == null || studyUID == null || seriesUID == null || sopUID == null) {
			return false;
		}
		String path = db.getFileLocation(studyUID, seriesUID, sopUID);
		if (path == null) {
			logger.warning("ReportService - sendSrToRemote: file not found for sop " + sopUID);
			return false;
		}
		try {
			String cParam = dest.getAETitle() + "@" + dest.getHostName() + ":" + dest.getPort();
			StoreSCU.main(new String[] { "-c", cParam, path });
			logger.info("ReportService - sendSrToRemote: sent " + sopUID + " to " + cParam);
			return true;
		} catch (Exception ex) {
			logger.severe("ReportService - sendSrToRemote failed: " + ex.getMessage());
			return false;
		}
	}

	// ---- SR / RDSR viewing ---------------------------------------------------

	/**
	 * Render an SR-family instance (SR, RDSR, KO, ...) to HTML and show it in the
	 * dedicated viewer window. Used by the routing guard so SR objects never open
	 * in the image viewer.
	 */
	public void openSr(String patID, String studyUID, String seriesUID, String sopUID) {
		String path = db.getFileLocation(studyUID, seriesUID, sopUID);
		if (path == null) {
			logger.warning("ReportService - openSr: file not found for sop " + sopUID);
			return;
		}
		try {
			DicomReaderChe reader = new DicomReaderChe(path, false);
			Attributes attr = (Attributes) reader.getHeader();
			if (attr == null) {
				logger.warning("ReportService - openSr: could not read SR " + path);
				return;
			}
			String html = SRtoHtml.toHtml(attr);
			String title = SRtoHtml.documentTitle(attr);
			SRHtmlViewerWindow.showSr(title, html, patID);
		} catch (Exception e) {
			logger.severe("ReportService - openSr failed: " + e.getMessage());
		}
	}
}
