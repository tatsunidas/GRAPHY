package com.vis.core.reporting.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vis.core.reporting.KeyImageRef;

/**
 * Post-processes an HTML string that contains
 * {@code <a href="graphy://image/{study}/{series}/{sop}...">label</a>} links and
 * replaces them with thumbnail-embedded anchors.
 * <p>
 * Runs entirely on the calling thread — must be called off the EDT (e.g. inside
 * a SwingWorker) because thumbnail lookup involves file I/O.
 * </p>
 * Used by both:
 * <ul>
 *   <li>{@link MarkdownEditorPanel} — live Markdown preview</li>
 *   <li>{@link SRHtmlViewerWindow} — finalized SR viewer</li>
 * </ul>
 *
 * @author tatsunidas
 */
public final class KeyImageHtmlInjector {

    // Matches <a href="graphy://image/..."> links produced by CommonMark / SRtoHtml.
    // Capture groups: 1=full-href, 2=studyUID, 3=seriesUID, 4=sopUID, 5=optional-query, 6=link-text
    private static final Pattern KEY_LINK = Pattern.compile(
            "<a\\s+href=\"(graphy://image/([^/?\"]+)/([^/?\"]+)/([^/?\"]+)([^\"]*))\">([^<]*)</a>",
            Pattern.CASE_INSENSITIVE);

    private KeyImageHtmlInjector() {}

    /**
     * Returns true when {@code html} contains at least one key-image link.
     * Cheap check to skip unnecessary processing.
     */
    public static boolean hasKeyImages(String html) {
        return html != null && html.contains("graphy://image/");
    }

    /**
     * Replace all key-image anchor tags in {@code html} with thumbnail-embedded
     * versions. Links whose thumbnail cannot be loaded are left unchanged.
     *
     * @param html source HTML (from CommonMark renderer or SRtoHtml)
     * @return enriched HTML (same object when nothing changed)
     */
    public static String inject(String html) {
        if (!hasKeyImages(html)) return html;

        Matcher m = KEY_LINK.matcher(html);
        StringBuffer sb = new StringBuffer(html.length() + 4096);
        boolean changed = false;

        while (m.find()) {
            String fullHref  = m.group(1);
            String studyUID  = m.group(2);
            String seriesUID = m.group(3);
            String sopUID    = m.group(4);
            String label     = m.group(6).trim();

            // Citation links (text = "[1]", "[2]", …) stay as text hyperlinks.
            if (label.matches("\\[\\d+\\]")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            String imgSrc = DicomThumbnailCache.getImageSrc(studyUID, seriesUID, sopUID);
            if (imgSrc == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            String replacement = buildThumbnailAnchor(fullHref, imgSrc, label);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            changed = true;
        }
        m.appendTail(sb);
        return changed ? sb.toString() : html;
    }

    // ---- Private helpers -------------------------------------------------------

    private static String buildThumbnailAnchor(String href, String imgSrc, String label) {
        String safeLabel = label.isEmpty() ? "Key Image" : label;
        int px = DicomThumbnailCache.THUMB_PX;
        // Set only width; omit height so the browser scales height proportionally.
        // Setting both to THUMB_PX would force a square and distort non-square images.
        return "<a href=\"" + href + "\">"
                + "<img src=\"" + imgSrc + "\""
                + " width=\"" + px + "\""
                + " border=\"0\" alt=\"" + safeLabel + "\">"
                + "</a> ";
    }
}
