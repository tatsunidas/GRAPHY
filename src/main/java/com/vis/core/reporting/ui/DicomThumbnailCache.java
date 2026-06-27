package com.vis.core.reporting.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;

import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReader;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReaderSpi;

import com.vis.core.log.Log;
import com.vis.db.DatabaseHandler;

/**
 * Thread-safe LRU cache that converts a stored DICOM instance to a PNG
 * thumbnail file and returns its {@code file:} URI so that JEditorPane's
 * HTMLEditorKit can render it inline.
 * <p>
 * Using {@code file:} URIs avoids the well-known limitation that
 * {@code javax.swing.text.html.HTMLEditorKit} cannot render {@code data:}
 * base-64 images — Java's {@code java.net.URL} does not support the
 * {@code data:} scheme.
 * </p>
 * Thumbnails are written once to a per-JVM-session temp directory and reused
 * from the cache on subsequent calls. The temp directory is deleted on JVM
 * shutdown.
 *
 * @author tatsunidas
 */
public final class DicomThumbnailCache {

    /** Maximum pixel dimension (longest edge) for the scaled thumbnail. */
    public static final int THUMB_PX = 150;

    /** Maximum number of cached entries kept in memory. */
    private static final int MAX_ENTRIES = 64;

    /** Empty sentinel — stored when a lookup fails to prevent retries. */
    private static final String MISS = "";

    // --- Per-session temp directory -------------------------------------------

    private static final Path TEMP_DIR;
    static {
        Path td = null;
        try {
            td = Files.createTempDirectory("graphy-thumbs-");
            final Path finalTd = td;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.walk(finalTd)
                         .sorted(Comparator.reverseOrder())
                         .forEach(p -> { try { Files.delete(p); } catch (Exception ignore) {} });
                } catch (Exception ignore) {}
            }));
        } catch (IOException e) {
            Log.logger.warning("DicomThumbnailCache - could not create temp dir: " + e.getMessage());
        }
        TEMP_DIR = td;
    }

    // --- LRU cache (sopUID → file: URI string or MISS sentinel) ---------------

    @SuppressWarnings("serial")
    private static final Map<String, String> CACHE =
            java.util.Collections.synchronizedMap(
                    new LinkedHashMap<String, String>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, String> e) {
                            return size() > MAX_ENTRIES;
                        }
                    });

    private DicomThumbnailCache() {}

    /**
     * Returns a {@code file:} URI pointing to a PNG thumbnail for the given
     * DICOM SOP instance, or {@code null} if the instance is unavailable, has
     * no pixel data, or failed to decode.
     * <p>
     * Performs file I/O — must NOT be called on the EDT.
     * </p>
     *
     * @param studyUID  study UID (used for DB file-path lookup)
     * @param seriesUID series UID
     * @param sopUID    SOP Instance UID (cache key)
     */
    public static String getImageSrc(String studyUID, String seriesUID, String sopUID) {
        return getImageSrc(studyUID, seriesUID, sopUID, 0f, 0f);
    }

    /**
     * Variant of {@link #getImageSrc(String, String, String)} that applies the
     * given window center / width when the DICOM file must be re-read (i.e. on
     * cache miss after a restart).  Pass {@code ww <= 0} to use the default
     * DICOM-embedded window.
     */
    public static String getImageSrc(String studyUID, String seriesUID, String sopUID,
                                      float wc, float ww) {
        if (sopUID == null || TEMP_DIR == null) return null;

        String cached = CACHE.get(sopUID);
        if (cached != null) {
            return MISS.equals(cached) ? null : cached;
        }

        DatabaseHandler db = DatabaseHandler.getInstance();
        if (db == null) {
            return null;
        }
        String filePath = db.getFileLocation(studyUID, seriesUID, sopUID);
        if (filePath == null) {
            CACHE.put(sopUID, MISS);
            return null;
        }

        try {
            BufferedImage thumb = readThumbnail(filePath, wc, ww);
            if (thumb == null) {
                CACHE.put(sopUID, MISS);
                return null;
            }
            String uri = writeTempPng(thumb, sopUID);
            CACHE.put(sopUID, uri);
            return uri;
        } catch (Exception ex) {
            Log.logger.warning("DicomThumbnailCache - " + sopUID + ": " + ex.getMessage());
            CACHE.put(sopUID, MISS);
            return null;
        }
    }

    /**
     * Pre-caches a thumbnail from an already-rendered image (e.g. captured from
     * the 2D viewer at the moment the user registers a key image).
     * <p>
     * This avoids re-reading the DICOM file and re-applying VOI LUT, ensuring the
     * thumbnail reflects the exact W/L the user sees on screen.
     * </p>
     * The file write is done on a background thread so this method returns
     * immediately and is safe to call on the EDT.
     *
     * @param sopUID   SOP Instance UID used as the cache key
     * @param rendered already-windowed {@code BufferedImage} (from
     *                 {@code SlideGlass.getRenderedImage()})
     */
    /**
     * @return the scaled thumbnail (immediately usable as {@code ImageIcon}) or
     *         {@code null} when the temp directory is unavailable
     */
    public static java.awt.image.BufferedImage preCacheRendered(
            String sopUID, java.awt.image.BufferedImage rendered) {
        if (sopUID == null || rendered == null || TEMP_DIR == null) return null;
        final java.awt.image.BufferedImage thumb = scale(toIntRgb(rendered));
        new Thread(() -> {
            try {
                String uri = writeTempPng(thumb, sopUID);
                CACHE.put(sopUID, uri);
            } catch (Exception e) {
                Log.logger.warning("DicomThumbnailCache.preCacheRendered - " + sopUID + ": " + e.getMessage());
            }
        }, "graphy-thumb-prerender").start();
        return thumb;
    }

    /** Remove a cached entry (e.g. after the instance is replaced). */
    public static void invalidate(String sopUID) {
        if (sopUID != null) CACHE.remove(sopUID);
    }

    // ---- Private helpers -------------------------------------------------------

    private static BufferedImage readThumbnail(String filePath, float wc, float ww) throws Exception {
        File f = new File(filePath);
        if (!f.exists()) return null;

        DicomImageReader reader = new DicomImageReader(new DicomImageReaderSpi());
        try (FileImageInputStream iis = new FileImageInputStream(f)) {
            reader.setInput(iis);
            DicomImageReadParam param = (DicomImageReadParam) reader.getDefaultReadParam();
            if (ww > 0f) {
                param.setWindowCenter(wc);
                param.setWindowWidth(ww);
                param.setAutoWindowing(false);
            }
            BufferedImage full = reader.read(0, param);
            return full == null ? null : scale(full);
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage scale(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) return src;
        if (w <= THUMB_PX && h <= THUMB_PX) return toIntRgb(src);

        double ratio = (double) THUMB_PX / Math.max(w, h);
        int tw = Math.max(1, (int) Math.round(w * ratio));
        int th = Math.max(1, (int) Math.round(h * ratio));

        BufferedImage dst = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, tw, th, null);
        g.dispose();
        return dst;
    }

    private static BufferedImage toIntRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    /**
     * Write {@code img} as PNG to the session temp directory and return the
     * resulting {@code file:} URI string suitable for use in {@code <img src="...">}.
     */
    private static String writeTempPng(BufferedImage img, String sopUID) throws IOException {
        // Sanitise sopUID so it is safe as a file name (UIDs are digits + dots, but be defensive)
        String safeName = sopUID.replaceAll("[^A-Za-z0-9._-]", "_") + ".png";
        Path out = TEMP_DIR.resolve(safeName);
        ImageIO.write(img, "png", out.toFile());
        // toUri() on Windows produces file:///C:/... — correct for HTMLEditorKit
        return out.toUri().toString();
    }
}
