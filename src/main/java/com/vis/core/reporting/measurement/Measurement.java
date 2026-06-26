package com.vis.core.reporting.measurement;

import org.dcm4che3.data.Code;

/**
 * A single named numeric measurement inside a {@link MeasurementGroup} — the unit
 * of a DICOM TID 1500 {@code NUM} content item (e.g. <i>Length = 42.3 mm</i>,
 * <i>Area = 153.0 mm2</i>, <i>Mean attenuation = 48.0 HU</i>).
 * <p>
 * {@link #name} is the measured concept (TID 300 row name) and {@link #unit} is a
 * UCUM unit code; both are plain {@link Code} triples, mirroring the rest of the
 * SR pipeline. Common concepts/units live in {@link com.vis.core.reporting.sr.SRCodes}.
 *
 * @author tatsunidas
 */
public class Measurement {

	/** Concept name of the measured quantity (ConceptNameCodeSequence of the NUM item). */
	private Code name;
	/** Numeric value. */
	private double value;
	/** Measurement unit (MeasurementUnitsCodeSequence), typically UCUM. */
	private Code unit;

	public Measurement() {
	}

	public Measurement(Code name, double value, Code unit) {
		this.name = name;
		this.value = value;
		this.unit = unit;
	}

	public Code getName() {
		return name;
	}

	public void setName(Code name) {
		this.name = name;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	public Code getUnit() {
		return unit;
	}

	public void setUnit(Code unit) {
		this.unit = unit;
	}
}
