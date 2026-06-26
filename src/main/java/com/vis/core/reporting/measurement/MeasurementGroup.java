package com.vis.core.reporting.measurement;

import java.util.ArrayList;
import java.util.List;

import org.dcm4che3.data.Code;

import com.vis.core.reporting.KeyImageRef;
import com.vis.dicom.UIDUtils;

/**
 * One DICOM TID 1500 <i>Measurement Group</i> (DCM 125007): the measurements
 * extracted from a single ROI / annotation, plus the geometry and image it came
 * from.
 * <p>
 * Maps to a TID 1410 (Planar ROI) / TID 1411 (Volumetric ROI) measurement group:
 * a tracking identifier + tracking UID (so a group can be correlated across
 * studies), an optional finding code (what was measured — e.g. a lesion), a
 * {@link SpatialCoordinate} region (SCOORD / SCOORD3D), and the list of numeric
 * {@link Measurement}s.
 *
 * @author tatsunidas
 */
public class MeasurementGroup {

	/** Human-readable tracking identifier (TEXT, DCM 112039). */
	private String trackingIdentifier;
	/** Tracking Unique Identifier (UIDREF, DCM 112040) — generated if absent. */
	private String trackingUID;
	/** Optional finding (CODE, DCM 121071) — what the group measures. */
	private Code finding;
	/** The region the measurements were taken from (SCOORD / SCOORD3D). Nullable. */
	private SpatialCoordinate region;
	/** Image this group is anchored to (group-level IMAGE reference). Nullable. */
	private KeyImageRef image;

	private final List<Measurement> measurements = new ArrayList<>();

	public MeasurementGroup() {
		this.trackingUID = UIDUtils.createUID();
	}

	public MeasurementGroup(String trackingIdentifier) {
		this();
		this.trackingIdentifier = trackingIdentifier;
	}

	public MeasurementGroup add(Measurement m) {
		if (m != null) {
			measurements.add(m);
		}
		return this;
	}

	public String getTrackingIdentifier() {
		return trackingIdentifier;
	}

	public void setTrackingIdentifier(String trackingIdentifier) {
		this.trackingIdentifier = trackingIdentifier;
	}

	public String getTrackingUID() {
		return trackingUID;
	}

	public void setTrackingUID(String trackingUID) {
		this.trackingUID = trackingUID;
	}

	public Code getFinding() {
		return finding;
	}

	public void setFinding(Code finding) {
		this.finding = finding;
	}

	public SpatialCoordinate getRegion() {
		return region;
	}

	public void setRegion(SpatialCoordinate region) {
		this.region = region;
	}

	public KeyImageRef getImage() {
		return image;
	}

	public void setImage(KeyImageRef image) {
		this.image = image;
	}

	public List<Measurement> getMeasurements() {
		return measurements;
	}
}
