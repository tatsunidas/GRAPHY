package com.vis.core.reporting.sr;

/**
 * Minimal HTML &lt;-&gt; plain-text helpers. SR TEXT items hold plain text, so the
 * editor's HTML body is flattened on finalize; conversely plain SR text is escaped
 * when rendered into the HTML viewer.
 *
 * @author tatsunidas
 */
public final class HtmlText {

	private HtmlText() {
	}

	/** Flatten HTML to plain text: block tags become newlines, other tags are dropped, entities decoded. */
	public static String toPlainText(String html) {
		if (html == null || html.isEmpty()) {
			return "";
		}
		String s = html;
		// normalise line breaks for common block-level tags
		s = s.replaceAll("(?i)<br\\s*/?>", "\n");
		s = s.replaceAll("(?i)</(p|div|li|tr|h[1-6])>", "\n");
		s = s.replaceAll("(?i)<li[^>]*>", "• ");
		// drop the html/head/style/script blocks entirely
		s = s.replaceAll("(?is)<head.*?</head>", "");
		s = s.replaceAll("(?is)<style.*?</style>", "");
		s = s.replaceAll("(?is)<script.*?</script>", "");
		// strip any remaining tags
		s = s.replaceAll("<[^>]+>", "");
		// decode the handful of entities JEditorPane emits
		s = s.replace("&nbsp;", " ")
				.replace("&amp;", "&")
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&quot;", "\"")
				.replace("&#39;", "'");
		// collapse excessive blank lines
		s = s.replaceAll("[ \\t]+\n", "\n").replaceAll("\n{3,}", "\n\n");
		return s.trim();
	}

	/** Escape a plain-text string for safe inclusion in HTML. */
	public static String escape(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}

	/** Escape plain text and convert newlines to &lt;br&gt; for HTML display. */
	public static String escapeMultiline(String text) {
		return escape(text).replace("\n", "<br>\n");
	}
}
