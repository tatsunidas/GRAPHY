package com.vis.dicom.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Parses an HTTP "multipart/related" request body (RFC 2387) for STOW-RS.
 * Mirror-image of {@link MultipartRelatedWriter}: the single place that
 * knows the wire format on the receiving side.
 *
 * v1 reads the whole request body into memory before splitting it (desktop
 * app, not expecting huge concurrent uploads); a true incremental parser
 * could be added later if large multi-instance uploads become an issue.
 *
 * @author tatsunidas
 */
public class MultipartRelatedReader {

	private final byte[] body;
	private final byte[] delimiter; // "--" + boundary, ASCII
	private int pos = 0;
	private byte[] currentPartBody;

	public MultipartRelatedReader(InputStream in, String boundary) throws IOException {
		this.body = readAll(in);
		this.delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
	}

	/**
	 * Extracts the boundary token from a "Content-Type: multipart/related;
	 * ...; boundary=..." header value. Returns null if no boundary= is present.
	 */
	public static String extractBoundary(String contentTypeHeader) {
		if (contentTypeHeader == null) {
			return null;
		}
		String marker = "boundary=";
		int idx = contentTypeHeader.toLowerCase().indexOf(marker);
		if (idx < 0) {
			return null;
		}
		String rest = contentTypeHeader.substring(idx + marker.length()).trim();
		// boundary value may be quoted and/or followed by other parameters
		int semicolon = rest.indexOf(';');
		if (semicolon >= 0) {
			rest = rest.substring(0, semicolon).trim();
		}
		if (rest.startsWith("\"") && rest.endsWith("\"") && rest.length() >= 2) {
			rest = rest.substring(1, rest.length() - 1);
		}
		return rest.isEmpty() ? null : rest;
	}

	/**
	 * Advances to the next part. Returns false once the closing delimiter
	 * ("--boundary--") is reached or no more parts are found.
	 */
	public boolean nextPart() throws IOException {
		currentPartBody = null;
		int delimPos = indexOf(body, delimiter, pos);
		if (delimPos < 0) {
			return false;
		}
		int afterDelim = delimPos + delimiter.length;
		// closing delimiter check: "--boundary--"
		if (afterDelim + 1 < body.length && body[afterDelim] == '-' && body[afterDelim + 1] == '-') {
			return false;
		}
		// skip CRLF after the delimiter line
		int headerStart = skipCrlf(afterDelim);
		// headers end at the first blank line (\r\n\r\n)
		int blankLine = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), headerStart);
		if (blankLine < 0) {
			return false;
		}
		int bodyStart = blankLine + 4;
		// this part's body runs up to (but not including) the next delimiter line
		int nextDelimPos = indexOf(body, delimiter, bodyStart);
		if (nextDelimPos < 0) {
			return false;
		}
		// trim the trailing CRLF that precedes the next delimiter
		int bodyEnd = nextDelimPos;
		if (bodyEnd >= 2 && body[bodyEnd - 1] == '\n' && body[bodyEnd - 2] == '\r') {
			bodyEnd -= 2;
		}
		currentPartBody = new byte[bodyEnd - bodyStart];
		System.arraycopy(body, bodyStart, currentPartBody, 0, currentPartBody.length);
		pos = nextDelimPos;
		return true;
	}

	public byte[] currentPartBody() {
		return currentPartBody;
	}

	private int skipCrlf(int from) {
		int i = from;
		if (i + 1 < body.length && body[i] == '\r' && body[i + 1] == '\n') {
			i += 2;
		}
		return i;
	}

	private static int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
		if (needle.length == 0 || fromIndex < 0) {
			return -1;
		}
		int max = haystack.length - needle.length;
		outer: for (int i = fromIndex; i <= max; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int len;
		while ((len = in.read(buf)) != -1) {
			baos.write(buf, 0, len);
		}
		return baos.toByteArray();
	}
}
