package com.vis.core.reporting;

/**
 * A reference to a single DICOM image instance ("key image") embedded inside a
 * report. Two roles:
 * <ul>
 *   <li>In the editor / HTML it is rendered as a clickable anchor whose href uses
 *       the custom scheme {@code graphy://image/{study}/{series}/{sop}} so that the
 *       {@code SRHtmlViewerWindow} can navigate (object retrieval) to the instance.</li>
 *   <li>On finalize it is emitted as an SR {@code IMAGE} content item
 *       (Referenced SOP Sequence) so the reference survives the round-trip.</li>
 * </ul>
 *
 * @author tatsunidas
 */
public class KeyImageRef {

	public static final String SCHEME = "graphy";
	public static final String HOST = "image";

	private String studyUID;
	private String seriesUID;
	private String sopUID;
	private String sopClassUID; // optional, for SR Referenced SOP Sequence
	private int frame = -1; // optional 1-based frame number, -1 = none
	private String label; // human readable text shown in the report

	public KeyImageRef() {
	}

	public KeyImageRef(String studyUID, String seriesUID, String sopUID, String sopClassUID, String label) {
		this.studyUID = studyUID;
		this.seriesUID = seriesUID;
		this.sopUID = sopUID;
		this.sopClassUID = sopClassUID;
		this.label = label;
	}

	/**
	 * @return the href string used in the editor / HTML, e.g.
	 *         {@code graphy://image/<study>/<series>/<sop>} (optionally
	 *         {@code ?frame=N}).
	 */
	public String toHref() {
		StringBuilder sb = new StringBuilder();
		sb.append(SCHEME).append("://").append(HOST).append('/')
				.append(nz(studyUID)).append('/').append(nz(seriesUID)).append('/').append(nz(sopUID));
		if (frame > 0) {
			sb.append("?frame=").append(frame);
		}
		return sb.toString();
	}

	/**
	 * Parse an href produced by {@link #toHref()} back into a {@link KeyImageRef}.
	 *
	 * @param href the raw href (use {@code HyperlinkEvent.getDescription()}; the
	 *             {@code getURL()} accessor returns null for non-standard schemes).
	 * @return the parsed reference, or {@code null} if the href is not a graphy image link.
	 */
	public static KeyImageRef fromHref(String href) {
		if (href == null) {
			return null;
		}
		String prefix = SCHEME + "://" + HOST + "/";
		if (!href.startsWith(prefix)) {
			return null;
		}
		String rest = href.substring(prefix.length());
		int frame = -1;
		int q = rest.indexOf('?');
		if (q >= 0) {
			String query = rest.substring(q + 1);
			rest = rest.substring(0, q);
			if (query.startsWith("frame=")) {
				try {
					frame = Integer.parseInt(query.substring("frame=".length()));
				} catch (NumberFormatException ignore) {
				}
			}
		}
		String[] parts = rest.split("/");
		if (parts.length < 3) {
			return null;
		}
		KeyImageRef ref = new KeyImageRef();
		ref.studyUID = parts[0];
		ref.seriesUID = parts[1];
		ref.sopUID = parts[2];
		ref.frame = frame;
		return ref;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
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

	public String getSopUID() {
		return sopUID;
	}

	public void setSopUID(String sopUID) {
		this.sopUID = sopUID;
	}

	public String getSopClassUID() {
		return sopClassUID;
	}

	public void setSopClassUID(String sopClassUID) {
		this.sopClassUID = sopClassUID;
	}

	public int getFrame() {
		return frame;
	}

	public void setFrame(int frame) {
		this.frame = frame;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
}
