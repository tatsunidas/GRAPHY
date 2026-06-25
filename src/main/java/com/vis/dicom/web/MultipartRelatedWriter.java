package com.vis.dicom.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Writes an HTTP "multipart/related" body (RFC 2387), one part per DICOM
 * instance, for WADO-RS responses. The single place that knows the exact
 * wire format, so WadoRsHandler doesn't hand-roll boundary framing itself.
 *
 * @author tatsunidas
 */
public class MultipartRelatedWriter {

	private final OutputStream out;
	private final String boundary;
	private final String partContentType;

	public MultipartRelatedWriter(OutputStream out, String boundary, String partContentType) {
		this.out = out;
		this.boundary = boundary;
		this.partContentType = partContentType;
	}

	public static String newBoundary() {
		return "graphy-" + UUID.randomUUID();
	}

	public static String contentTypeHeaderFor(String boundary, String partContentType) {
		return "multipart/related; type=\"" + partContentType + "\"; boundary=\"" + boundary + "\"";
	}

	public void writePart(File file) throws IOException {
		try (InputStream in = new FileInputStream(file)) {
			writePart(in);
		}
	}

	public void writePart(InputStream body) throws IOException {
		writeAscii("--" + boundary + "\r\n");
		writeAscii("Content-Type: " + partContentType + "\r\n");
		writeAscii("\r\n");
		byte[] buf = new byte[8192];
		int len;
		while ((len = body.read(buf)) != -1) {
			out.write(buf, 0, len);
		}
		writeAscii("\r\n");
	}

	public void writeClosingDelimiter() throws IOException {
		writeAscii("--" + boundary + "--\r\n");
	}

	private void writeAscii(String s) throws IOException {
		out.write(s.getBytes(StandardCharsets.US_ASCII));
	}
}
