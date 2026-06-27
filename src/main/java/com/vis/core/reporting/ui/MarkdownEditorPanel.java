package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.undo.UndoManager;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import com.vis.configuration.Resources;
import com.vis.core.reporting.KeyImageRef;

/**
 * Split-pane Markdown editor: left = JTextArea (plain Markdown input, monospace),
 * right = JEditorPane (live CommonMark + GFM HTML preview).
 * <p>
 * A mini toolbar above the split pane provides syntax shortcuts:
 * H1/H2/H3, bold, italic, strikethrough, unordered/ordered list, blockquote,
 * code block, horizontal rule, text color (color-picker), font size, and font
 * family. Undo/Redo are wired to Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z.
 * </p>
 *
 * @author tatsunidas
 */
public class MarkdownEditorPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // ---- CommonMark parser/renderer (thread-safe statics) --------------------

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        List<Extension> exts = Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create());
        PARSER   = Parser.builder().extensions(exts).build();
        RENDERER = HtmlRenderer.builder().extensions(exts).build();
    }

    private static final String PREVIEW_CSS =
            "body{font-family:sans-serif;font-size:14px;padding:8px 14px;margin:0;line-height:1.6}" +
            "h1{font-size:22px;border-bottom:2px solid #ccc;padding-bottom:4px;margin-top:12px}" +
            "h2{font-size:18px;border-bottom:1px solid #ddd;padding-bottom:3px;margin-top:10px}" +
            "h3{font-size:15px;margin-top:8px}" +
            "code{font-family:monospace;background:#f0f0f0;padding:1px 5px;border-radius:3px}" +
            "pre{background:#f6f8fa;padding:10px 14px;border-radius:4px;overflow-x:auto}" +
            "pre code{padding:0;background:none}" +
            "blockquote{border-left:4px solid #ccc;margin:4px 0;padding:2px 14px;color:#555}" +
            "table{border-collapse:collapse;margin:6px 0}" +
            "th,td{border:1px solid #ccc;padding:5px 10px}" +
            "th{background:#f0f0f0;font-weight:bold}" +
            "a{color:#0366d6}" +
            "del{text-decoration:line-through;color:#999}" +
            "hr{border:none;border-top:1px solid #ddd;margin:10px 0}" +
            "ul,ol{padding-left:24px}" +
            "p{margin:4px 0}" +
            "img{max-width:100%;height:auto}";

    /**
     * Render a report body to a standalone HTML page using the shared CommonMark
     * parser and the live-preview CSS.  Safe to call from any thread.
     *
     * @param body       raw Markdown source (or legacy HTML body fragment)
     * @param isMarkdown {@code true} → run through CommonMark; {@code false} → treat
     *                   as pre-rendered HTML body
     * @param extraHtml  additional HTML appended before {@code </body>} (e.g. key-image
     *                   table from {@code KeyImageGridPanel.generateHtmlFromRefs()}),
     *                   or {@code null} / empty to omit
     */
    public static String buildHtml(String body, boolean isMarkdown, String extraHtml) {
        String safe = body == null ? "" : body;
        String rendered;
        if (isMarkdown) {
            String mathProcessed = MathRenderer.processLatex(safe);
            rendered = RENDERER.render(PARSER.parse(mathProcessed));
        } else {
            rendered = safe;
        }
        String extra = extraHtml == null ? "" : extraHtml;
        return "<html><head><meta charset=\"UTF-8\"><style>" + PREVIEW_CSS
                + "</style></head><body>" + rendered + extra + "</body></html>";
    }

    // ---- Components ----------------------------------------------------------

    private final JTextArea mdArea;
    private final javax.swing.JEditorPane preview;
    private final UndoManager undoManager = new UndoManager();
    private final javax.swing.Timer previewTimer;
    private boolean dirty = false;

    /** Optional extra HTML appended after the Markdown body in the preview (e.g. key-image grid). */
    private Supplier<String> extraHtmlSupplier = null;

    /** Supplies the current key-image list for the "Insert image reference" context menu. */
    private Supplier<List<KeyImageRef>> keyImageSupplier = null;

    public MarkdownEditorPanel() {
        super(new BorderLayout());

        // Left: Markdown text editor
        mdArea = new JTextArea();
        mdArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        mdArea.setTabSize(4);
        mdArea.setLineWrap(true);
        mdArea.setWrapStyleWord(true);
        mdArea.getDocument().addUndoableEditListener(undoManager);
        mdArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onEdit(); }
            @Override public void removeUpdate(DocumentEvent e)  { onEdit(); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });
        setupUndoKeys();
        setupContextMenu();

        // Right: HTML preview
        preview = new javax.swing.JEditorPane();
        preview.setEditable(false);
        preview.setEditorKit(new HTMLEditorKit());
        preview.setContentType("text/html");

        // Split pane
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(mdArea),
                new JScrollPane(preview));
        split.setResizeWeight(0.5);

        add(buildToolbar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        // Debounced preview refresh (350 ms after last keystroke)
        previewTimer = new javax.swing.Timer(350, e -> refreshPreview());
        previewTimer.setRepeats(false);
    }

    // ---- Public API ----------------------------------------------------------

    public String getText() {
        return mdArea.getText();
    }

    public void setText(String text) {
        mdArea.setText(text == null ? "" : text);
        mdArea.setCaretPosition(0);
        undoManager.discardAllEdits();
        dirty = false;
        refreshPreview();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    /**
     * Sets a supplier that provides extra HTML appended after the Markdown body in
     * the live preview. Used by {@link ReportEditorDialog} to inject the key-image
     * grid table without embedding it in the Markdown text.
     */
    public void setExtraHtmlSupplier(Supplier<String> supplier) {
        this.extraHtmlSupplier = supplier;
    }

    /**
     * Sets a supplier that returns the currently registered key images.
     * Used by the right-click "Insert image reference" context menu.
     */
    public void setKeyImageSupplier(Supplier<List<KeyImageRef>> supplier) {
        this.keyImageSupplier = supplier;
    }

    /** Insert text at the current caret position. */
    public void insertAtCaret(String text) {
        int pos = mdArea.getCaretPosition();
        mdArea.insert(text, pos);
        mdArea.setCaretPosition(pos + text.length());
        mdArea.requestFocus();
    }

    /** Insert a key-image as a Markdown link. */
    public void insertKeyImage(KeyImageRef ref, String label) {
        String safeLabel = (label == null || label.isEmpty()) ? "Key Image" : label;
        insertAtCaret("[" + safeLabel + "](" + ref.toHref() + ")  \n");
    }

    /** Insert a template body (HTML or Markdown fragment) at the caret. */
    public void insertTemplate(String body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        insertAtCaret("\n" + body + "\n");
    }

    public JTextArea getTextArea() {
        return mdArea;
    }

    // ---- Toolbar -------------------------------------------------------------

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2));

        // Headings
        addBtn(bar, "H1", Resources.i18n("Reporting.editor.h1"), () -> insertAtLineStart("# "));
        addBtn(bar, "H2", Resources.i18n("Reporting.editor.h2"), () -> insertAtLineStart("## "));
        addBtn(bar, "H3", Resources.i18n("Reporting.editor.h3"), () -> insertAtLineStart("### "));
        bar.addSeparator();

        // Inline formatting
        addBtn(bar, "B",   Resources.i18n("Reporting.editor.bold"),
                () -> wrapSelection("**", "**", Resources.i18n("Reporting.editor.boldPlaceholder")));
        addBtn(bar, "I",   Resources.i18n("Reporting.editor.italic"),
                () -> wrapSelection("*", "*",   Resources.i18n("Reporting.editor.italicPlaceholder")));
        addBtn(bar, "~~",  Resources.i18n("Reporting.editor.strike"),
                () -> wrapSelection("~~", "~~", Resources.i18n("Reporting.editor.strikePlaceholder")));
        bar.addSeparator();

        // Block elements
        addBtn(bar, "•",   Resources.i18n("Reporting.editor.bulletList"),  () -> insertAtLineStart("- "));
        addBtn(bar, "1.",  Resources.i18n("Reporting.editor.numberList"),  () -> insertAtLineStart("1. "));
        addBtn(bar, ">",   Resources.i18n("Reporting.editor.blockquote"),  () -> insertAtLineStart("> "));
        addBtn(bar, "```", Resources.i18n("Reporting.editor.code"),
                () -> wrapSelection("```\n", "\n```", "code"));
        addBtn(bar, "—",   Resources.i18n("Reporting.editor.hr"),          () -> insertAtCaret("\n---\n"));
        bar.addSeparator();

        // Color
        JButton colorBtn = new JButton(Resources.i18n("Reporting.editor.color"));
        colorBtn.setToolTipText(Resources.i18n("Reporting.editor.color"));
        colorBtn.setMargin(new Insets(2, 5, 2, 5));
        colorBtn.addActionListener(e -> pickColor());
        bar.add(colorBtn);

        // Font size
        bar.add(new JLabel(" " + Resources.i18n("Reporting.editor.fontSize") + " "));
        String[] sizes = {"10px", "12px", "14px", "16px", "18px", "20px", "24px", "28px", "32px"};
        JComboBox<String> sizeBox = new JComboBox<>(sizes);
        sizeBox.setSelectedItem("14px");
        sizeBox.setPreferredSize(new Dimension(72, 26));
        sizeBox.setMaximumSize(new Dimension(72, 26));
        sizeBox.addActionListener(e -> {
            String sz = (String) sizeBox.getSelectedItem();
            if (sz != null) {
                wrapSelection("<span style=\"font-size:" + sz + "\">", "</span>", "text");
                sizeBox.setSelectedItem("14px");
            }
        });
        bar.add(sizeBox);

        // Font family
        bar.add(new JLabel(" " + Resources.i18n("Reporting.editor.fontFamily") + " "));
        String[] fonts = {"sans-serif", "serif", "monospace",
                          "Arial", "Times New Roman", "Courier New", "Noto Sans JP"};
        JComboBox<String> fontBox = new JComboBox<>(fonts);
        fontBox.setPreferredSize(new Dimension(140, 26));
        fontBox.setMaximumSize(new Dimension(140, 26));
        fontBox.addActionListener(e -> {
            String f = (String) fontBox.getSelectedItem();
            if (f != null) {
                wrapSelection("<span style=\"font-family:'" + f + "'\">", "</span>", "text");
                fontBox.setSelectedIndex(0);
            }
        });
        bar.add(fontBox);

        bar.addSeparator();

        // Undo / Redo
        addBtn(bar, Resources.i18n("Reporting.editor.undo"), Resources.i18n("Reporting.editor.undo"),
                () -> { if (undoManager.canUndo()) undoManager.undo(); });
        addBtn(bar, Resources.i18n("Reporting.editor.redo"), Resources.i18n("Reporting.editor.redo"),
                () -> { if (undoManager.canRedo()) undoManager.redo(); });

        return bar;
    }

    private void addBtn(JToolBar bar, String label, String tooltip, Runnable action) {
        JButton btn = new JButton(label);
        btn.setToolTipText(tooltip);
        btn.setMargin(new Insets(2, 5, 2, 5));
        btn.addActionListener(e -> action.run());
        bar.add(btn);
    }

    private void pickColor() {
        Color chosen = JColorChooser.showDialog(this,
                Resources.i18n("Reporting.editor.color"), Color.BLACK);
        if (chosen == null) {
            return;
        }
        String hex = String.format("#%02x%02x%02x",
                chosen.getRed(), chosen.getGreen(), chosen.getBlue());
        wrapSelection("<span style=\"color:" + hex + "\">", "</span>", "text");
    }

    // ---- Text-manipulation helpers -------------------------------------------

    private void wrapSelection(String prefix, String suffix, String placeholder) {
        String selected = mdArea.getSelectedText();
        if (selected == null || selected.isEmpty()) {
            selected = placeholder;
        }
        int start = mdArea.getSelectionStart();
        mdArea.replaceSelection(prefix + selected + suffix);
        // Re-select inner content so the user can immediately overtype
        mdArea.select(start + prefix.length(), start + prefix.length() + selected.length());
        mdArea.requestFocus();
    }

    private void insertAtLineStart(String prefix) {
        try {
            int selStart = mdArea.getSelectionStart();
            int selEnd   = mdArea.getSelectionEnd();
            int firstLine = mdArea.getLineOfOffset(selStart);
            int lastLine  = mdArea.getLineOfOffset(selEnd > selStart ? selEnd - 1 : selEnd);
            int blockStart = mdArea.getLineStartOffset(firstLine);
            int blockEnd   = mdArea.getLineEndOffset(lastLine);

            String block = mdArea.getText().substring(blockStart, blockEnd);
            String[] lines = block.split("\n", -1);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                out.append(prefix).append(lines[i]);
                if (i < lines.length - 1) out.append('\n');
            }
            mdArea.replaceRange(out.toString(), blockStart, blockEnd);
            mdArea.setCaretPosition(blockStart + out.length());
        } catch (Exception ex) {
            insertAtCaret(prefix);
        }
        mdArea.requestFocus();
    }

    // ---- Context menu (right-click) -----------------------------------------

    private void setupContextMenu() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem insertRefItem = new JMenuItem(Resources.i18n("Reporting.editor.insertImageRef"));
        insertRefItem.addActionListener(e -> insertImageRefAtCaret());
        popup.add(insertRefItem);

        mdArea.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) show(e);
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) show(e);
            }
            private void show(java.awt.event.MouseEvent e) {
                List<KeyImageRef> refs = keyImageSupplier != null ? keyImageSupplier.get() : null;
                insertRefItem.setEnabled(refs != null && !refs.isEmpty());
                popup.show(mdArea, e.getX(), e.getY());
            }
        });
    }

    private void insertImageRefAtCaret() {
        if (keyImageSupplier == null) return;
        List<KeyImageRef> refs = keyImageSupplier.get();
        if (refs == null || refs.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    Resources.i18n("Reporting.editor.noKeyImages"),
                    Resources.i18n("Reporting.editor.insertImageRef"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Build display list: "[1] annotation" or "[1] label"
        String[] items = new String[refs.size()];
        for (int i = 0; i < refs.size(); i++) {
            KeyImageRef ref = refs.get(i);
            String ann = ref.getAnnotation();
            String desc = (ann != null && !ann.isEmpty()) ? ann
                        : (ref.getLabel() != null ? ref.getLabel() : "");
            items[i] = "[" + (i + 1) + "]" + (desc.isEmpty() ? "" : "  " + desc);
        }

        Object chosen = JOptionPane.showInputDialog(
                this,
                Resources.i18n("Reporting.editor.insertImageRefPrompt"),
                Resources.i18n("Reporting.editor.insertImageRef"),
                JOptionPane.PLAIN_MESSAGE,
                null, items, items[0]);
        if (chosen == null) return;

        int idx = java.util.Arrays.asList(items).indexOf(chosen);
        if (idx < 0) return;

        KeyImageRef ref = refs.get(idx);
        // Insert citation link: [[N]](graphy://image/...) — renders as [N] hyperlink
        insertAtCaret("[[" + (idx + 1) + "]](" + ref.toHref() + ")");
    }

    // ---- Undo/Redo key bindings ----------------------------------------------

    private void setupUndoKeys() {
        javax.swing.InputMap im = mdArea.getInputMap(JComponent.WHEN_FOCUSED);
        javax.swing.ActionMap am = mdArea.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "redo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");

        am.put("undo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo()) undoManager.undo();
            }
        });
        am.put("redo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (undoManager.canRedo()) undoManager.redo();
            }
        });
    }

    // ---- Live preview --------------------------------------------------------

    private void onEdit() {
        dirty = true;
        previewTimer.restart();
    }

    private void refreshPreview() {
        final String md    = mdArea.getText() == null ? "" : mdArea.getText();
        final String extra = extraHtmlSupplier != null ? extraHtmlSupplier.get() : "";

        // Phase 1: CommonMark only (no math, no key images) — immediate display
        String body = RENDERER.render(PARSER.parse(md));
        String html = "<html><head><style>" + PREVIEW_CSS
                + "</style></head><body>" + body + extra + "</body></html>";
        preview.setText(html);
        preview.setCaretPosition(0);

        // Phase 2: math rendering (JLaTeXMath) + key image injection (file I/O) — background
        final boolean needsMath      = md.contains("$");
        final boolean needsKeyImages = KeyImageHtmlInjector.hasKeyImages(html);
        if (needsMath || needsKeyImages) {
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    String processed = needsMath ? MathRenderer.processLatex(md) : md;
                    String body2 = RENDERER.render(PARSER.parse(processed));
                    String html2 = "<html><head><style>" + PREVIEW_CSS
                            + "</style></head><body>" + body2 + extra + "</body></html>";
                    return needsKeyImages ? KeyImageHtmlInjector.inject(html2) : html2;
                }
                @Override
                protected void done() {
                    try {
                        String enriched = get();
                        if (enriched != null) {
                            preview.setText(enriched);
                            preview.setCaretPosition(0);
                        }
                    } catch (InterruptedException | ExecutionException ignore) {}
                }
            }.execute();
        }
    }
}
