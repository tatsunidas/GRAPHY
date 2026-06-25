package com.vis.dicom.web;

/**
 * Carries an intended HTTP status code out of a DICOMweb handler, so each
 * handler can have a single top-level catch that turns it into a response.
 *
 * @author tatsunidas
 */
public class DicomWebException extends Exception {

	private static final long serialVersionUID = 1L;

	private final int statusCode;

	public DicomWebException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return statusCode;
	}
}
