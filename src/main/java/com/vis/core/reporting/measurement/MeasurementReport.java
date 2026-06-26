package com.vis.core.reporting.measurement;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory model of a DICOM TID 1500 <i>Imaging Measurement Report</i>: a study
 * title plus the list of {@link MeasurementGroup}s to serialize. Provider-agnostic
 * — viewer code populates it from live 2D ROIs / 3D measurements and hands it to
 * {@link com.vis.core.reporting.sr.Tid1500Writer} to produce the SR dataset.
 *
 * @author tatsunidas
 */
public class MeasurementReport {

	private String title;
	private String patientID;
	private String studyUID;

	private final List<MeasurementGroup> groups = new ArrayList<>();

	public MeasurementReport() {
	}

	public MeasurementReport(String patientID, String studyUID, String title) {
		this.patientID = patientID;
		this.studyUID = studyUID;
		this.title = title;
	}

	public MeasurementReport add(MeasurementGroup g) {
		if (g != null) {
			groups.add(g);
		}
		return this;
	}

	/** @return true if any group carries a 3D ({@code SCOORD3D}) region. */
	public boolean hasSpatial3D() {
		for (MeasurementGroup g : groups) {
			if (g.getRegion() != null && g.getRegion().isThreeD()) {
				return true;
			}
		}
		return false;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
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

	public List<MeasurementGroup> getGroups() {
		return groups;
	}
}
