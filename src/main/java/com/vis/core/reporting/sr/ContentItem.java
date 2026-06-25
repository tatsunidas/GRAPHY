package com.vis.core.reporting.sr;

import java.util.ArrayList;
import java.util.List;

import org.dcm4che3.data.Code;

/**
 * Generic node of a parsed SR content tree. Modality-agnostic: the same model
 * represents a free-text report, an RDSR, or any SR-family document. Produced by
 * {@link SRReader} and consumed by {@link SRtoHtml}.
 *
 * @author tatsunidas
 */
public class ContentItem {

	/** DICOM ValueType (0040,A040): CONTAINER, TEXT, NUM, CODE, IMAGE, DATETIME, ... */
	private String valueType;
	/** Concept Name Code Sequence (0040,A043) — what this item is. */
	private Code conceptName;
	/** Relationship Type (0040,A010) relative to the parent. */
	private String relationshipType;

	// value carriers (only the one matching valueType is populated)
	private String textValue; // TEXT
	private Code code; // CODE -> Concept Code Sequence
	private String numericValue; // NUM
	private Code unit; // NUM unit
	private String dateTime; // DATE / TIME / DATETIME (display string)
	private String uidRef; // UIDREF
	private String personName; // PNAME
	private String refSopClassUID; // IMAGE / COMPOSITE
	private String refSopInstanceUID; // IMAGE / COMPOSITE

	private final List<ContentItem> children = new ArrayList<>();

	public String getValueType() {
		return valueType;
	}

	public void setValueType(String valueType) {
		this.valueType = valueType;
	}

	public Code getConceptName() {
		return conceptName;
	}

	public void setConceptName(Code conceptName) {
		this.conceptName = conceptName;
	}

	public String getRelationshipType() {
		return relationshipType;
	}

	public void setRelationshipType(String relationshipType) {
		this.relationshipType = relationshipType;
	}

	public String getTextValue() {
		return textValue;
	}

	public void setTextValue(String textValue) {
		this.textValue = textValue;
	}

	public Code getCode() {
		return code;
	}

	public void setCode(Code code) {
		this.code = code;
	}

	public String getNumericValue() {
		return numericValue;
	}

	public void setNumericValue(String numericValue) {
		this.numericValue = numericValue;
	}

	public Code getUnit() {
		return unit;
	}

	public void setUnit(Code unit) {
		this.unit = unit;
	}

	public String getDateTime() {
		return dateTime;
	}

	public void setDateTime(String dateTime) {
		this.dateTime = dateTime;
	}

	public String getUidRef() {
		return uidRef;
	}

	public void setUidRef(String uidRef) {
		this.uidRef = uidRef;
	}

	public String getPersonName() {
		return personName;
	}

	public void setPersonName(String personName) {
		this.personName = personName;
	}

	public String getRefSopClassUID() {
		return refSopClassUID;
	}

	public void setRefSopClassUID(String refSopClassUID) {
		this.refSopClassUID = refSopClassUID;
	}

	public String getRefSopInstanceUID() {
		return refSopInstanceUID;
	}

	public void setRefSopInstanceUID(String refSopInstanceUID) {
		this.refSopInstanceUID = refSopInstanceUID;
	}

	public List<ContentItem> getChildren() {
		return children;
	}

	/** @return concept name's meaning for display, or empty string. */
	public String conceptMeaning() {
		return conceptName == null || conceptName.getCodeMeaning() == null ? "" : conceptName.getCodeMeaning();
	}
}
