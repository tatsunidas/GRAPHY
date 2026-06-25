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
		DRAFT, FINAL
	}

	private String reportId;
	private String patientID;
	private String studyUID;
	private String seriesUID; // SR series UID (set on finalize); nullable for drafts
	private String studyDate; // yyyy/MM/dd
	private String title;
	private String author;
	private Status status = Status.DRAFT;
	private ReportType type = ReportType.GENERAL;
	private String bodyHtml = "";
	private List<KeyImageRef> keyImages = new ArrayList<>();
	private String srSopInstanceUID; // set on finalize
	private long createdMillis;
	private long modifiedMillis;

	private static final Gson GSON = new Gson();
	private static final Type KEYIMG_LIST_TYPE = new TypeToken<ArrayList<KeyImageRef>>() {
	}.getType();

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
		ctx.put(ReportDBKey.BodyHtml.name(), bodyHtml);
		ctx.put(ReportDBKey.KeyImageRefs.name(), keyImages == null ? null : GSON.toJson(keyImages, KEYIMG_LIST_TYPE));
		ctx.put(ReportDBKey.SrSopInstanceUID.name(), srSopInstanceUID);
		ctx.put(ReportDBKey.StudyDate.name(), studyDate);
		ctx.put(ReportDBKey.CreatedDateTime.name(), createdMillis);
		ctx.put(ReportDBKey.ModifiedDateTime.name(), modifiedMillis);
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
		d.status = "FINAL".equals(st) ? Status.FINAL : Status.DRAFT;
		d.type = ReportType.fromName((String) ctx.get(ReportDBKey.ReportType.name()));
		d.author = (String) ctx.get(ReportDBKey.Author.name());
		d.bodyHtml = (String) ctx.getOrDefault(ReportDBKey.BodyHtml.name(), "");
		String json = (String) ctx.get(ReportDBKey.KeyImageRefs.name());
		if (json != null && !json.isEmpty()) {
			List<KeyImageRef> refs = GSON.fromJson(json, KEYIMG_LIST_TYPE);
			d.keyImages = refs == null ? new ArrayList<>() : refs;
		}
		d.srSopInstanceUID = (String) ctx.get(ReportDBKey.SrSopInstanceUID.name());
		d.studyDate = (String) ctx.get(ReportDBKey.StudyDate.name());
		d.createdMillis = asLong(ctx.get(ReportDBKey.CreatedDateTime.name()));
		d.modifiedMillis = asLong(ctx.get(ReportDBKey.ModifiedDateTime.name()));
		d.patientID = (String) ctx.get(ReportDBKey.PatientID.name());
		d.studyUID = (String) ctx.get(ReportDBKey.StudyInstanceUID.name());
		d.seriesUID = (String) ctx.get(ReportDBKey.SeriesInstanceUID.name());
		return d;
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

	public long getCreatedMillis() {
		return createdMillis;
	}

	public long getModifiedMillis() {
		return modifiedMillis;
	}
}
