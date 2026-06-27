package com.vis.core.reporting;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vis.configuration.ReportDBKey;
import com.vis.dicom.UIDUtils;

/**
 * In-memory model of a radiology report. The editable rich text ({@link #bodyHtml})
 * is the source of truth and is persisted in the REPORT table. A DICOM SR is a
 * derived artifact produced on finalize (see {@code ReportService}).
 * <p>
 * {@link #readContext()} / {@link #fromContext(HashMap)} convert to/from the
 * {@code HashMap<String,Object>} context map used by {@code DatabaseHandler}
 * (keyed by {@link ReportDBKey} names), mirroring the ROI persistence pattern.
 *
 * @author tatsunidas
 */
public class ReportDocument {

	public enum Status {
		DRAFT, FINAL, ADDENDUM
	}

	private String reportId;
	private String patientID;
	private String studyUID;
	private String seriesUID; // SR series UID (set on finalize); nullable for drafts
	private String studyDate; // yyyy/MM/dd
	private String title;
	private String author;
	private String referringPhysician;
	private String clinicalHistory;
	private Status status = Status.DRAFT;
	private ReportType type = ReportType.GENERAL;
	private String bodyHtml = ""; // report body; format determined by bodyFormat
	private String bodyFormat = "md"; // "md" (Markdown, default) or "html" (legacy)
	private List<KeyImageRef> keyImages = new ArrayList<>();
	private String srSopInstanceUID; // set on finalize
	private String koSopInstanceUID; // set on finalize when key images exist
	private String koSeriesInstanceUID;
	private String predecessorReportId; // nullable; set on addendum
	private String predecessorSrSopUID; // nullable; SOP UID of predecessor SR
	private String predecessorSeriesUID; // nullable; series UID of predecessor SR
	private long createdMillis;
	private long modifiedMillis;

	private static final Gson GSON = new Gson();
	private static final Type KEYIMG_LIST_TYPE = new TypeToken<ArrayList<KeyImageRef>>() {
	}.getType();

	/**
	 * Create a fresh addendum that references an existing finalized report.
	 * The addendum is a new draft that, when finalized, includes a
	 * PredecessorDocumentsSequence pointing to the original SR.
	 */
	public static ReportDocument newAddendum(ReportDocument predecessor, String author) {
		ReportDocument d = new ReportDocument();
		d.reportId = UIDUtils.createUID();
		d.patientID = predecessor.patientID;
		d.studyUID = predecessor.studyUID;
		d.studyDate = predecessor.studyDate;
		d.author = (author != null && !author.isEmpty()) ? author : predecessor.author;
		d.referringPhysician = predecessor.referringPhysician;
		d.clinicalHistory = predecessor.clinicalHistory;
		d.title = predecessor.title;
		d.status = Status.ADDENDUM;
		d.type = predecessor.type;
		d.predecessorReportId = predecessor.reportId;
		d.predecessorSrSopUID = predecessor.srSopInstanceUID;
		d.predecessorSeriesUID = predecessor.seriesUID;
		d.bodyFormat = "md";
		long now = System.currentTimeMillis();
		d.createdMillis = now;
		d.modifiedMillis = now;
		return d;
	}

	/**
	 * Create a fresh draft anchored to the given patient/study.
	 */
	public static ReportDocument newDraft(String patientID, String studyUID, String studyDate, String author) {
		ReportDocument d = new ReportDocument();
		d.reportId = UIDUtils.createUID();
		d.patientID = patientID;
		d.studyUID = studyUID;
		d.studyDate = studyDate;
		d.author = author;
		d.status = Status.DRAFT;
		d.type = ReportType.GENERAL;
		long now = System.currentTimeMillis();
		d.createdMillis = now;
		d.modifiedMillis = now;
		return d;
	}

	/** Serialize to the DatabaseHandler context map. */
	public HashMap<String, Object> readContext() {
		HashMap<String, Object> ctx = new HashMap<>();
		ctx.put(ReportDBKey.ReportID.name(), reportId);
		ctx.put(ReportDBKey.Title.name(), title);
		ctx.put(ReportDBKey.Status.name(), status == null ? Status.DRAFT.name() : status.name());
		ctx.put(ReportDBKey.ReportType.name(), type == null ? ReportType.GENERAL.name() : type.name());
		ctx.put(ReportDBKey.Author.name(), author);
		ctx.put(ReportDBKey.ReferringPhysician.name(), referringPhysician);
		ctx.put(ReportDBKey.ClinicalHistory.name(), clinicalHistory);
		ctx.put(ReportDBKey.BodyHtml.name(), bodyHtml);
		ctx.put(ReportDBKey.BodyFormat.name(), bodyFormat == null ? "md" : bodyFormat);
		ctx.put(ReportDBKey.KeyImageRefs.name(), keyImages == null ? null : GSON.toJson(keyImages, KEYIMG_LIST_TYPE));
		ctx.put(ReportDBKey.SrSopInstanceUID.name(), srSopInstanceUID);
		ctx.put(ReportDBKey.KoSopInstanceUID.name(), koSopInstanceUID);
		ctx.put(ReportDBKey.KoSeriesInstanceUID.name(), koSeriesInstanceUID);
		ctx.put(ReportDBKey.StudyDate.name(), studyDate);
		ctx.put(ReportDBKey.CreatedDateTime.name(), createdMillis);
		ctx.put(ReportDBKey.ModifiedDateTime.name(), modifiedMillis);
		ctx.put(ReportDBKey.PredecessorReportId.name(), predecessorReportId);
		ctx.put(ReportDBKey.PredecessorSrSopUID.name(), predecessorSrSopUID);
		ctx.put(ReportDBKey.PredecessorSeriesUID.name(), predecessorSeriesUID);
		ctx.put(ReportDBKey.PatientID.name(), patientID);
		ctx.put(ReportDBKey.StudyInstanceUID.name(), studyUID);
		ctx.put(ReportDBKey.SeriesInstanceUID.name(), seriesUID);
		return ctx;
	}

	/** Rebuild from a DatabaseHandler context map. */
	public static ReportDocument fromContext(HashMap<String, Object> ctx) {
		ReportDocument d = new ReportDocument();
		d.reportId = (String) ctx.get(ReportDBKey.ReportID.name());
		d.title = (String) ctx.get(ReportDBKey.Title.name());
		String st = (String) ctx.get(ReportDBKey.Status.name());
		if ("FINAL".equals(st)) {
			d.status = Status.FINAL;
		} else if ("ADDENDUM".equals(st)) {
			d.status = Status.ADDENDUM;
		} else {
			d.status = Status.DRAFT;
		}
		d.type = ReportType.fromName((String) ctx.get(ReportDBKey.ReportType.name()));
		d.author = (String) ctx.get(ReportDBKey.Author.name());
		d.referringPhysician = (String) ctx.get(ReportDBKey.ReferringPhysician.name());
		d.clinicalHistory = (String) ctx.get(ReportDBKey.ClinicalHistory.name());
		d.bodyHtml = (String) ctx.getOrDefault(ReportDBKey.BodyHtml.name(), "");
		d.bodyFormat = (String) ctx.getOrDefault(ReportDBKey.BodyFormat.name(), "html");
		// Auto-detect legacy HTML records that predate BodyFormat column
		if (d.bodyFormat == null || d.bodyFormat.isEmpty()) {
			d.bodyFormat = (d.bodyHtml != null && d.bodyHtml.startsWith("<")) ? "html" : "md";
		}
		String json = (String) ctx.get(ReportDBKey.KeyImageRefs.name());
		if (json != null && !json.isEmpty()) {
			List<KeyImageRef> refs = GSON.fromJson(json, KEYIMG_LIST_TYPE);
			d.keyImages = refs == null ? new ArrayList<>() : refs;
		}
		d.srSopInstanceUID = (String) ctx.get(ReportDBKey.SrSopInstanceUID.name());
		d.koSopInstanceUID = (String) ctx.get(ReportDBKey.KoSopInstanceUID.name());
		d.koSeriesInstanceUID = (String) ctx.get(ReportDBKey.KoSeriesInstanceUID.name());
		d.studyDate = (String) ctx.get(ReportDBKey.StudyDate.name());
		d.createdMillis = asLong(ctx.get(ReportDBKey.CreatedDateTime.name()));
		d.modifiedMillis = asLong(ctx.get(ReportDBKey.ModifiedDateTime.name()));
		d.predecessorReportId = (String) ctx.get(ReportDBKey.PredecessorReportId.name());
		d.predecessorSrSopUID = (String) ctx.get(ReportDBKey.PredecessorSrSopUID.name());
		d.predecessorSeriesUID = (String) ctx.get(ReportDBKey.PredecessorSeriesUID.name());
		d.patientID = (String) ctx.get(ReportDBKey.PatientID.name());
		d.studyUID = (String) ctx.get(ReportDBKey.StudyInstanceUID.name());
		d.seriesUID = (String) ctx.get(ReportDBKey.SeriesInstanceUID.name());
		return d;
	}

	/** True if the body is stored as Markdown (default for new reports). */
	public boolean isMarkdown() {
		return !"html".equalsIgnoreCase(bodyFormat);
	}

	private static long asLong(Object o) {
		if (o instanceof Number) {
			return ((Number) o).longValue();
		}
		return 0L;
	}

	public void touchModified() {
		this.modifiedMillis = System.currentTimeMillis();
	}

	// getters / setters --------------------------------------------------------

	public String getReportId() {
		return reportId;
	}

	public String getPatientID() {
		return patientID;
	}

	public void setPatientID(String patientID) {
		this.patientID = patientID;
	}

	public String getStudyUID() {
		return studyUID;
	}

	public void setStudyUID(String studyUID) {
		this.studyUID = studyUID;
	}

	public String getSeriesUID() {
		return seriesUID;
	}

	public void setSeriesUID(String seriesUID) {
		this.seriesUID = seriesUID;
	}

	public String getStudyDate() {
		return studyDate;
	}

	public void setStudyDate(String studyDate) {
		this.studyDate = studyDate;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getReferringPhysician() {
		return referringPhysician;
	}

	public void setReferringPhysician(String referringPhysician) {
		this.referringPhysician = referringPhysician;
	}

	public String getClinicalHistory() {
		return clinicalHistory;
	}

	public void setClinicalHistory(String clinicalHistory) {
		this.clinicalHistory = clinicalHistory;
	}

	public String getBodyFormat() {
		return bodyFormat == null ? "md" : bodyFormat;
	}

	public void setBodyFormat(String bodyFormat) {
		this.bodyFormat = bodyFormat;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public ReportType getType() {
		return type;
	}

	public void setType(ReportType type) {
		this.type = type;
	}

	public String getBodyHtml() {
		return bodyHtml;
	}

	public void setBodyHtml(String bodyHtml) {
		this.bodyHtml = bodyHtml;
	}

	public List<KeyImageRef> getKeyImages() {
		return keyImages;
	}

	public void setKeyImages(List<KeyImageRef> keyImages) {
		this.keyImages = keyImages == null ? new ArrayList<>() : keyImages;
	}

	public void addKeyImage(KeyImageRef ref) {
		if (ref != null) {
			this.keyImages.add(ref);
		}
	}

	public String getSrSopInstanceUID() {
		return srSopInstanceUID;
	}

	public void setSrSopInstanceUID(String srSopInstanceUID) {
		this.srSopInstanceUID = srSopInstanceUID;
	}

	public String getKoSopInstanceUID() {
		return koSopInstanceUID;
	}

	public void setKoSopInstanceUID(String koSopInstanceUID) {
		this.koSopInstanceUID = koSopInstanceUID;
	}

	public String getKoSeriesInstanceUID() {
		return koSeriesInstanceUID;
	}

	public void setKoSeriesInstanceUID(String koSeriesInstanceUID) {
		this.koSeriesInstanceUID = koSeriesInstanceUID;
	}

	public String getPredecessorReportId() {
		return predecessorReportId;
	}

	public void setPredecessorReportId(String predecessorReportId) {
		this.predecessorReportId = predecessorReportId;
	}

	public String getPredecessorSrSopUID() {
		return predecessorSrSopUID;
	}

	public void setPredecessorSrSopUID(String predecessorSrSopUID) {
		this.predecessorSrSopUID = predecessorSrSopUID;
	}

	public String getPredecessorSeriesUID() {
		return predecessorSeriesUID;
	}

	public void setPredecessorSeriesUID(String predecessorSeriesUID) {
		this.predecessorSeriesUID = predecessorSeriesUID;
	}

	public long getCreatedMillis() {
		return createdMillis;
	}

	public long getModifiedMillis() {
		return modifiedMillis;
	}
}
