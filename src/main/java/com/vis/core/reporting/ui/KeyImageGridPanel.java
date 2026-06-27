package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import com.vis.configuration.Resources;
import com.vis.core.reporting.KeyImageRef;

/**
 * N×2 key-image grid panel displayed at the bottom of the report editor.
 * <p>
 * Left column: DICOM thumbnail (captured from viewer at insert time).<br>
 * Right column: auto-numbered citation label + editable annotation text area.
 * </p>
 * Key images are placed here rather than inline in the Markdown body.
 * {@link #generateHtml()} produces an HTML table (with {@code graphy://} link
 * in the image cell) that is appended to the Markdown preview by
 * {@link MarkdownEditorPanel}. {@link KeyImageHtmlInjector} then replaces the
 * link with the thumbnail image.
 *
 * @author tatsunidas
 */
public class KeyImageGridPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int THUMB = DicomThumbnailCache.THUMB_PX;
    private static final int ROW_H = THUMB + 20;

    private final List<RowEntry> rows = new ArrayList<>();
    private final JPanel rowsPanel;
    private Runnable addAction;
    private Runnable changeNotifier;
    /** Called when the user clicks a thumbnail; argument is the clicked {@link KeyImageRef}. */
    private Consumer<KeyImageRef> navigateAction;

    // ---- Inner data class -------------------------------------------------------

    private static final class RowEntry {
        final KeyImageRef ref;
        final JLabel thumbLabel;
        final JLabel citeLabel;
        final JTextArea annotArea;
        final JPanel rowPanel;

        RowEntry(KeyImageRef ref, JLabel thumbLabel, JLabel citeLabel,
                JTextArea annotArea, JPanel rowPanel) {
            this.ref = ref;
            this.thumbLabel = thumbLabel;
            this.citeLabel = citeLabel;
            this.annotArea = annotArea;
            this.rowPanel = rowPanel;
        }
    }

    // ---- Constructor ------------------------------------------------------------

    public KeyImageGridPanel() {
        super(new BorderLayout(0, 0));
        setBorder(BorderFactory.createTitledBorder(
                Resources.i18n("Reporting.keyimage.section")));

        // Header with "＋ Add" button
        JPanel header = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        JButton addBtn = new JButton(Resources.i18n("Reporting.keyimage.addButton"));
        addBtn.addActionListener(e -> { if (addAction != null) addAction.run(); });
        header.add(addBtn);
        add(header, BorderLayout.NORTH);

        // Rows container (vertical list)
        rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(rowsPanel);
        scroll.setPreferredSize(new Dimension(0, ROW_H + 60));
        scroll.setMinimumSize(new Dimension(0, ROW_H + 20));
        add(scroll, BorderLayout.CENTER);
    }

    // ---- Public API -------------------------------------------------------------

    public void setAddAction(Runnable action) {
        this.addAction = action;
    }

    public void setChangeNotifier(Runnable notifier) {
        this.changeNotifier = notifier;
    }

    /**
     * Set a callback invoked when the user clicks a thumbnail image.
     * Use this to navigate the 2D viewer to the corresponding slice.
     */
    public void setNavigateAction(Consumer<KeyImageRef> action) {
        this.navigateAction = action;
    }

    /**
     * Add a new key-image row. {@code thumb} may be {@code null} — the thumbnail
     * will then be loaded asynchronously from {@link DicomThumbnailCache}.
     */
    public void addRow(KeyImageRef ref, BufferedImage thumb) {
        int n = rows.size() + 1;
        JPanel row = buildRowPanel(ref, n, thumb);
        rows.add((RowEntry) row.getClientProperty("entry"));
        rowsPanel.add(row, rowsPanel.getComponentCount() - 1); // keep glue at end
        rowsPanel.revalidate();
        rowsPanel.repaint();
        notifyChange();
    }

    /** Load rows from a saved document (thumbnails loaded asynchronously). */
    public void loadRows(List<KeyImageRef> refs) {
        clear();
        if (refs == null) return;
        for (KeyImageRef ref : refs) {
            addRow(ref, null);
        }
    }

    /** Remove all rows. */
    public void clear() {
        rows.clear();
        rowsPanel.removeAll();
        rowsPanel.add(Box.createVerticalGlue());
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * Return current key-image refs with annotation text read from the text areas.
     */
    public List<KeyImageRef> getKeyImageRefs() {
        List<KeyImageRef> result = new ArrayList<>();
        for (RowEntry e : rows) {
            e.ref.setAnnotation(e.annotArea.getText().trim());
            result.add(e.ref);
        }
        return result;
    }

    /**
     * Generate an HTML table section for the key-image list.
     * {@code graphy://} anchor links in the image column are replaced by
     * thumbnails when rendered through {@link KeyImageHtmlInjector}.
     */
    public String generateHtml() {
        if (rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<hr>");
        sb.append("<h3>").append(esc(Resources.i18n("Reporting.keyimage.section"))).append("</h3>");
        sb.append("<table border=\"1\" cellpadding=\"8\" cellspacing=\"0\"")
          .append(" style=\"border-collapse:collapse;width:100%\">");
        sb.append("<thead><tr>")
          .append("<th width=\"").append(THUMB + 20).append("\" align=\"center\">")
          .append(esc(Resources.i18n("Reporting.keyimage.colImage")))
          .append("</th><th>")
          .append(esc(Resources.i18n("Reporting.keyimage.colAnnotation")))
          .append("</th></tr></thead><tbody>");
        for (int i = 0; i < rows.size(); i++) {
            RowEntry e = rows.get(i);
            String ann = e.annotArea.getText().trim();
            sb.append("<tr>");
            // Image cell: graphy:// link → replaced by KeyImageHtmlInjector with <img>
            // Label is a bare number (not "[N]") so the citation-link skip in KeyImageHtmlInjector
            // does not suppress thumbnail injection here.
            sb.append("<td align=\"center\"><a href=\"")
              .append(e.ref.toHref()).append("\">").append(i + 1).append("</a></td>");
            // Annotation cell
            sb.append("<td>[").append(i + 1).append("] ").append(esc(ann)).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    /**
     * Same as {@link #generateHtml()} but uses a static list of refs (for
     * generating HTML outside the editor, e.g. in {@code ReportListPanel}).
     */
    public static String generateHtmlFromRefs(List<KeyImageRef> refs) {
        if (refs == null || refs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<hr>");
        sb.append("<h3>").append(esc(Resources.i18n("Reporting.keyimage.section"))).append("</h3>");
        sb.append("<table border=\"1\" cellpadding=\"8\" cellspacing=\"0\"")
          .append(" style=\"border-collapse:collapse;width:100%\">");
        sb.append("<thead><tr>")
          .append("<th width=\"").append(THUMB + 20).append("\" align=\"center\">")
          .append(esc(Resources.i18n("Reporting.keyimage.colImage")))
          .append("</th><th>")
          .append(esc(Resources.i18n("Reporting.keyimage.colAnnotation")))
          .append("</th></tr></thead><tbody>");
        for (int i = 0; i < refs.size(); i++) {
            KeyImageRef ref = refs.get(i);
            String ann = ref.getAnnotation();
            sb.append("<tr>");
            sb.append("<td align=\"center\"><a href=\"")
              .append(ref.toHref()).append("\">").append(i + 1).append("</a></td>");
            sb.append("<td>[").append(i + 1).append("] ").append(esc(ann)).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    // ---- Private helpers --------------------------------------------------------

    private JPanel buildRowPanel(KeyImageRef ref, int n, BufferedImage thumb) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_H));
        row.setMinimumSize(new Dimension(0, ROW_H));
        row.setPreferredSize(new Dimension(row.getPreferredSize().width, ROW_H));

        // Left: thumbnail label (clickable — navigates viewer to the referenced slice)
        JLabel thumbLabel = new JLabel();
        thumbLabel.setPreferredSize(new Dimension(THUMB + 8, THUMB + 8));
        thumbLabel.setMinimumSize(new Dimension(THUMB + 8, THUMB + 8));
        thumbLabel.setHorizontalAlignment(JLabel.CENTER);
        thumbLabel.setVerticalAlignment(JLabel.CENTER);
        thumbLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        thumbLabel.setBackground(Color.DARK_GRAY);
        thumbLabel.setOpaque(true);
        thumbLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        thumbLabel.setToolTipText(Resources.i18n("Reporting.keyimage.navigateTip"));
        thumbLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (navigateAction != null) navigateAction.accept(ref);
            }
        });
        if (thumb != null) {
            thumbLabel.setIcon(new ImageIcon(thumb));
        } else {
            thumbLabel.setForeground(Color.LIGHT_GRAY);
            thumbLabel.setText("...");
            loadThumbAsync(thumbLabel, ref);
        }
        row.add(thumbLabel, BorderLayout.WEST);

        // Right: citation label + annotation textarea + remove button
        JPanel right = new JPanel(new BorderLayout(4, 2));
        right.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

        JLabel citeLabel = new JLabel("[" + n + "]");
        citeLabel.setFont(citeLabel.getFont().deriveFont(Font.BOLD));
        right.add(citeLabel, BorderLayout.NORTH);

        JTextArea annotArea = new JTextArea(3, 0);
        annotArea.setLineWrap(true);
        annotArea.setWrapStyleWord(true);
        annotArea.setBorder(BorderFactory.createLoweredBevelBorder());
        String savedAnnotation = ref.getAnnotation();
        if (savedAnnotation != null && !savedAnnotation.isEmpty()) {
            annotArea.setText(savedAnnotation);
        }
        annotArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { notifyChange(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { notifyChange(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        right.add(new JScrollPane(annotArea), BorderLayout.CENTER);

        JButton removeBtn = new JButton("×");
        removeBtn.setMargin(new Insets(1, 5, 1, 5));
        removeBtn.setFont(removeBtn.getFont().deriveFont(Font.BOLD));
        right.add(removeBtn, BorderLayout.EAST);
        row.add(right, BorderLayout.CENTER);

        RowEntry entry = new RowEntry(ref, thumbLabel, citeLabel, annotArea, row);
        row.putClientProperty("entry", entry);
        removeBtn.addActionListener(e -> removeRow(entry));
        return row;
    }

    private void removeRow(RowEntry entry) {
        rows.remove(entry);
        rowsPanel.remove(entry.rowPanel);
        renumber();
        rowsPanel.revalidate();
        rowsPanel.repaint();
        notifyChange();
    }

    private void renumber() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).citeLabel.setText("[" + (i + 1) + "]");
        }
    }

    private void notifyChange() {
        if (changeNotifier != null) changeNotifier.run();
    }

    private void loadThumbAsync(JLabel label, KeyImageRef ref) {
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() {
                String src = DicomThumbnailCache.getImageSrc(
                        ref.getStudyUID(), ref.getSeriesUID(), ref.getSopUID(),
                        ref.getWindowCenter(), ref.getWindowWidth());
                if (src == null) return null;
                try {
                    BufferedImage img = ImageIO.read(new URL(src));
                    return img == null ? null : new ImageIcon(img);
                } catch (Exception e) {
                    return null;
                }
            }
            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        label.setIcon(icon);
                        label.setText("");
                        label.setForeground(null);
                    }
                } catch (Exception ignore) {}
            }
        }.execute();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
    }
}
