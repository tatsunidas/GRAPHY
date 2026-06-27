package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.ReportDocument;
import com.vis.core.reporting.ReportService;
import com.vis.core.reporting.template.ReportTemplate;
import com.vis.core.reporting.template.ReportTemplateStore;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.db.DatabaseHandler;

/**
 * Markdown report editor dialog (Qiita-style split pane: left = Markdown source,
 * right = live HTML preview).
 * <p>
 * Replaces the old {@code HTMLEditorKit}-based editor. The body is now stored as
 * CommonMark Markdown (BodyFormat = "md"). Legacy HTML reports (BodyFormat = "html")
 * are loaded read-only and remain editable as raw HTML since CommonMark passes raw
 * HTML blocks through unchanged.
 * </p>
 * <h3>Features</h3>
 * <ul>
 *   <li>P1-1: Patient/study info header auto-populated from DICOM DB</li>
 *   <li>P1-2: New reports pre-filled with structured sections</li>
 *   <li>P1-3: Unsaved-change guard on window close</li>
 *   <li>P2-5: Undo/Redo via {@link MarkdownEditorPanel} (Ctrl+Z / Ctrl+Y)</li>
 *   <li>P2-6: Referring physician and clinical history fields</li>
 * </ul>
 *
 * @author tatsunidas
 */
public class ReportEditorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final ReportService service = new ReportService();
    private final ReportTemplateStore templateStore = new ReportTemplateStore();

    private final ReportDocument doc;
    private final JTextField titleField        = new JTextField(28);
    private final JTextField authorField       = new JTextField(14);
    private final JTextField referringField    = new JTextField(20);
    private final JTextField clinicalHistField = new JTextField(30);
    private final JLabel statusLabel           = new JLabel();
    private final MarkdownEditorPanel mdEditor = new MarkdownEditorPanel();
    private final KeyImageGridPanel keyImageGrid = new KeyImageGridPanel();
    private boolean lockedByMe = false;
    private Runnable onSaved;

    // ---- Factory methods -----------------------------------------------------

    /** Open editor for a fresh draft anchored to the given patient/study. */
    public static ReportEditorDialog showNew(Window owner,
            String patID, String studyUID, String studyDate, String author) {
        ReportDocument d = ReportDocument.newDraft(patID, studyUID, studyDate, author);
        d.setBodyFormat("md");
        ReportEditorDialog dlg = new ReportEditorDialog(owner, d);
        dlg.mdEditor.setText(buildInitialBody());
        dlg.mdEditor.markClean();
        dlg.setVisible(true);
        return dlg;
    }

    /** Open editor for an existing (draft or final) report. */
    public static ReportEditorDialog showExisting(Window owner, ReportDocument doc) {
        ReportEditorDialog dlg = new ReportEditorDialog(owner, doc);
        dlg.setVisible(true);
        return dlg;
    }

    /** Open an addendum editor for a new addendum derived from the given FINAL report. */
    public static ReportEditorDialog showAddendum(Window owner, ReportDocument predecessor, String author) {
        ReportService svc = new ReportService();
        ReportDocument addendum = svc.createAddendum(predecessor.getReportId(), author);
        if (addendum == null) {
            return null;
        }
        ReportEditorDialog dlg = new ReportEditorDialog(owner, addendum);
        dlg.mdEditor.setText(buildInitialBody());
        dlg.mdEditor.markClean();
        dlg.setVisible(true);
        return dlg;
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    // ---- Constructor ---------------------------------------------------------

    private ReportEditorDialog(Window owner, ReportDocument doc) {
        super(owner, Resources.i18n("Reporting.editor.title"), ModalityType.MODELESS);
        this.doc = doc;
        setSize(new Dimension(1000, 780));
        setLocationRelativeTo(owner);
        buildUI();
        loadFromDoc();
        setupWindowClosing();
        tryAcquireLock();
    }

    private void tryAcquireLock() {
        String author = doc.getAuthor();
        if (author == null || author.isEmpty()) return;
        String holder = service.getLockHolder(doc.getReportId());
        if (holder != null && !holder.equals(author)) {
            // Someone else has it open — open read-only
            setTitle(getTitle() + " [" + Resources.i18n("Reporting.editor.readOnly") + " - " + holder + "]");
            mdEditor.setEnabled(false);
            titleField.setEditable(false);
            authorField.setEditable(false);
            referringField.setEditable(false);
            clinicalHistField.setEditable(false);
            JOptionPane.showMessageDialog(this,
                    Resources.i18n("Reporting.editor.lockedBy") + " " + holder,
                    Resources.i18n("dialog.title.information"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        lockedByMe = service.tryLock(doc.getReportId(), author);
    }

    // ---- UI construction -----------------------------------------------------

    private void buildUI() {
        // Wire up key image grid
        keyImageGrid.setAddAction(this::insertKeyImage);
        keyImageGrid.setChangeNotifier(mdEditor::markDirty);
        mdEditor.setExtraHtmlSupplier(keyImageGrid::generateHtml);
        mdEditor.setKeyImageSupplier(keyImageGrid::getKeyImageRefs);
        keyImageGrid.setNavigateAction(this::navigateToKeyImage);

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        content.add(buildPatientInfoPanel(), BorderLayout.NORTH);

        // Editor area: meta panel + markdown editor
        JPanel editorPanel = new JPanel(new BorderLayout(0, 4));
        editorPanel.add(buildMetaPanel(), BorderLayout.NORTH);
        editorPanel.add(mdEditor,         BorderLayout.CENTER);

        // Vertical split: markdown editor (top) + key image grid (bottom)
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.VERTICAL_SPLIT, editorPanel, keyImageGrid);
        split.setResizeWeight(0.65);
        split.setDividerSize(5);

        JPanel body = new JPanel(new BorderLayout(0, 4));
        body.add(split,            BorderLayout.CENTER);
        body.add(buildButtonBar(), BorderLayout.SOUTH);
        content.add(body, BorderLayout.CENTER);

        setContentPane(content);
    }

    private JPanel buildPatientInfoPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        p.setBorder(BorderFactory.createTitledBorder(
                Resources.i18n("Reporting.editor.patientInfo")));

        HashMap<String, String> studyInfo  = null;
        HashMap<String, String> patInfo    = null;
        DatabaseHandler db = DatabaseHandler.getInstance();
        if (db != null && doc.getPatientID() != null && doc.getStudyUID() != null) {
            studyInfo = db.getStudyInfo(doc.getPatientID(), doc.getStudyUID());
            patInfo   = db.getPatientInfo(doc.getPatientID());
        }

        String patName   = get(patInfo,   "PatientName",        "");
        String patID     = nz(doc.getPatientID());
        String studyDate = nz(doc.getStudyDate());
        String modality  = get(studyInfo, "ModalitiesInStudy",  "");
        String accession = get(studyInfo, "AccessionNumber",     "");
        String studyDesc = get(studyInfo, "StudyDescription",    "");

        p.add(infoLabel(Resources.i18n("Reporting.editor.patientName") + " " + patName));
        p.add(infoLabel("|  " + Resources.i18n("Reporting.editor.patientId") + " " + patID));
        p.add(infoLabel("|  " + Resources.i18n("Reporting.editor.studyDate") + " " + studyDate));
        if (!modality.isEmpty())  p.add(infoLabel("|  " + modality));
        if (!accession.isEmpty()) p.add(infoLabel("|  Acc:" + accession));
        if (!studyDesc.isEmpty()) p.add(infoLabel("|  " + studyDesc));
        return p;
    }

    private JPanel buildMetaPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        GridBagConstraints lc = new GridBagConstraints();
        lc.insets = new Insets(2, 4, 2, 2);
        lc.anchor = GridBagConstraints.WEST;
        GridBagConstraints fc = new GridBagConstraints();
        fc.insets = new Insets(2, 2, 2, 10);
        fc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Title | Author | Status
        lc.gridx = 0; lc.gridy = 0;
        p.add(new JLabel(Resources.i18n("Reporting.editor.field.title")), lc);
        fc.gridx = 1; fc.gridy = 0; fc.weightx = 0.4;
        p.add(titleField, fc);

        lc.gridx = 2;
        p.add(new JLabel(Resources.i18n("Reporting.editor.field.author")), lc);
        fc.gridx = 3; fc.weightx = 0.2;
        p.add(authorField, fc);

        fc.gridx = 4; fc.weightx = 0;
        p.add(statusLabel, fc);

        // Row 1: Referring | ClinicalHistory | Template selector
        lc.gridx = 0; lc.gridy = 1;
        p.add(new JLabel(Resources.i18n("Reporting.editor.field.referring")), lc);
        fc.gridx = 1; fc.gridy = 1; fc.weightx = 0.3;
        p.add(referringField, fc);

        lc.gridx = 2;
        p.add(new JLabel(Resources.i18n("Reporting.editor.field.clinicalHistory")), lc);
        fc.gridx = 3; fc.weightx = 0.5;
        p.add(clinicalHistField, fc);

        // Template chooser at the right of row 1
        fc.gridx = 4; fc.weightx = 0;
        JPanel tmplPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        tmplPanel.add(new JLabel(Resources.i18n("Reporting.editor.template") + " "));
        JComboBox<ReportTemplate> templates = new JComboBox<>();
        templates.addItem(null);
        for (ReportTemplate t : templateStore.getTemplates()) {
            templates.addItem(t);
        }
        templates.setPreferredSize(new Dimension(160, 24));
        templates.addActionListener(e -> {
            ReportTemplate t = (ReportTemplate) templates.getSelectedItem();
            if (t != null && t.getBody() != null) {
                mdEditor.insertTemplate(t.getBody());
                templates.setSelectedItem(null);
            }
        });
        tmplPanel.add(templates);

        // Manage templates button
        JButton manageTmpl = new JButton(Resources.i18n("Reporting.template.manage"));
        manageTmpl.addActionListener(e -> {
            ReportTemplateManagerDialog.show(this, templateStore);
            // Reload combo after edits
            templates.removeAllItems();
            templates.addItem(null);
            templateStore.invalidate();
            for (ReportTemplate t : templateStore.getTemplates()) {
                templates.addItem(t);
            }
        });
        tmplPanel.add(manageTmpl);
        p.add(tmplPanel, fc);

        return p;
    }

    private JPanel buildButtonBar() {
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton addendum = new JButton(Resources.i18n("Reporting.action.addendum"));
        addendum.addActionListener(e -> createAddendum());
        addendum.setVisible(doc.getStatus() == ReportDocument.Status.FINAL);

        JButton send = new JButton(Resources.i18n("Reporting.action.sendToPacs"));
        send.addActionListener(e -> sendToPacs());
        send.setVisible(doc.getStatus() == ReportDocument.Status.FINAL
                && doc.getSrSopInstanceUID() != null);

        JButton save = new JButton(Resources.i18n("Reporting.action.saveDraft"));
        save.addActionListener(e -> saveDraft());
        JButton finalize = new JButton(Resources.i18n("Reporting.action.finalizeSR"));
        finalize.addActionListener(e -> finalizeAsSR(send));
        JButton close = new JButton(Resources.i18n("Reporting.action.close"));
        close.addActionListener(e -> maybeClose());

        south.add(addendum);
        south.add(send);
        south.add(save);
        south.add(finalize);
        south.add(close);
        return south;
    }

    // ---- P1-3: unsaved-change guard ------------------------------------------

    private void setupWindowClosing() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                maybeClose();
            }
            @Override
            public void windowClosed(WindowEvent e) {
                if (lockedByMe) {
                    service.unlock(doc.getReportId(), doc.getAuthor());
                    lockedByMe = false;
                }
            }
        });
    }

    private void maybeClose() {
        if (mdEditor.isDirty() || hasMetaChanges()) {
            int ans = JOptionPane.showConfirmDialog(this,
                    Resources.i18n("Reporting.editor.unsavedChanges"),
                    Resources.i18n("dialog.title.information"),
                    JOptionPane.YES_NO_CANCEL_OPTION);
            if (ans == JOptionPane.YES_OPTION) {
                saveDraft();
                dispose();
            } else if (ans == JOptionPane.NO_OPTION) {
                dispose();
            }
            // CANCEL → stay open
        } else {
            dispose();
        }
    }

    // ---- Load / Apply --------------------------------------------------------

    private void loadFromDoc() {
        titleField.setText(nz(doc.getTitle()));
        authorField.setText(nz(doc.getAuthor()));
        referringField.setText(nz(doc.getReferringPhysician()));
        clinicalHistField.setText(nz(doc.getClinicalHistory()));
        mdEditor.setText(nz(doc.getBodyHtml()));
        mdEditor.markClean();
        keyImageGrid.loadRows(doc.getKeyImages());
        refreshStatus();
    }

    private void applyToDoc() {
        doc.setTitle(titleField.getText().trim());
        doc.setAuthor(authorField.getText().trim());
        doc.setReferringPhysician(referringField.getText().trim());
        doc.setClinicalHistory(clinicalHistField.getText().trim());
        doc.setBodyHtml(mdEditor.getText());
        doc.setBodyFormat("md");
        doc.setKeyImages(keyImageGrid.getKeyImageRefs());
    }

    private boolean hasMetaChanges() {
        return !titleField.getText().trim().equals(nz(doc.getTitle()))
            || !authorField.getText().trim().equals(nz(doc.getAuthor()))
            || !referringField.getText().trim().equals(nz(doc.getReferringPhysician()))
            || !clinicalHistField.getText().trim().equals(nz(doc.getClinicalHistory()));
    }

    // ---- Actions -------------------------------------------------------------

    private void saveDraft() {
        applyToDoc();
        service.saveDraft(doc);
        mdEditor.markClean();
        refreshStatus();
        if (onSaved != null) onSaved.run();
        info(Resources.i18n("Reporting.editor.saved"));
    }

    private void finalizeAsSR(JButton sendBtn) {
        normalizeInlineKeyImages();
        applyToDoc();
        service.saveDraft(doc);
        final ReportEditorDialog self = this;
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return service.finalizeAsSR(doc);
            }
            @Override
            protected void done() {
                String sop = null;
                try { sop = get(); } catch (Exception ignore) {}
                mdEditor.markClean();
                refreshStatus();
                if (onSaved != null) onSaved.run();
                if (sop != null) {
                    // Reveal "Send to PACS" button now that an SR file exists
                    if (sendBtn != null) sendBtn.setVisible(true);
                    info(Resources.i18n("Reporting.editor.finalized"));
                } else {
                    JOptionPane.showMessageDialog(self,
                            Resources.i18n("Reporting.editor.finalizeFailed"),
                            Resources.i18n("dialog.title.information"),
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    /** Open the C-STORE send dialog for the finalized SR (P1-5). */
    private void sendToPacs() {
        if (doc.getSrSopInstanceUID() == null || doc.getSeriesUID() == null) {
            info(Resources.i18n("Reporting.editor.notYetFinalized"));
            return;
        }
        SrSendDialog.show(this, doc);
    }

    /** Create an addendum draft from this finalized report (P1-7). */
    private void createAddendum() {
        if (doc.getStatus() != ReportDocument.Status.FINAL) {
            info(Resources.i18n("Reporting.editor.mustBeFinal"));
            return;
        }
        ReportEditorDialog addendumDlg = showAddendum(getOwner(), doc, doc.getAuthor());
        if (addendumDlg != null && onSaved != null) {
            addendumDlg.setOnSaved(onSaved);
        }
    }

    /**
     * Navigate the 2D viewer to the series and slice referenced by {@code ref}.
     * Called when the user clicks a thumbnail in the key-image grid.
     */
    private void navigateToKeyImage(com.vis.core.reporting.KeyImageRef ref) {
        Viewer2DScreen viewer = Viewer2DScreen.getInstance();
        if (viewer == null) return;
        String pid = doc.getPatientID();
        String refUID = DatabaseHandler.getInstance().getValueFromImage(
                "FrameOfReferenceUID", pid, ref.getStudyUID(), ref.getSeriesUID(), ref.getSopUID());
        if (refUID == null) refUID = "";
        viewer.loadSeriesAndNavigate(pid, ref.getStudyUID(), ref.getSeriesUID(), ref.getSopUID(), refUID);
    }

    /**
     * Scan the Markdown body for inline {@code [label](graphy://image/...)} links.
     * Any found are automatically removed from the body text and added to the
     * key-image grid (if not already present there).  This guarantees that all key
     * images end up in the dedicated section at the end of the report rather than
     * scattered through the body.
     * <p>
     * Called automatically at the start of {@link #finalizeAsSR(JButton)} so that
     * older drafts (created before the grid UI existed) are normalised on first
     * finalize without manual intervention.
     * </p>
     */
    private static final Pattern INLINE_KEY_IMAGE_MD =
            Pattern.compile("\\[([^\\]]*)\\]\\((graphy://image/[^)]+)\\)[ \\t]*");

    private void normalizeInlineKeyImages() {
        String md = mdEditor.getText();
        if (md == null || !md.contains("graphy://image/")) return;

        Matcher m = INLINE_KEY_IMAGE_MD.matcher(md);
        StringBuffer sb = new StringBuffer();
        boolean moved = false;

        // Snapshot current grid refs to avoid adding duplicates
        List<KeyImageRef> existing = keyImageGrid.getKeyImageRefs();

        while (m.find()) {
            String label = m.group(1);
            String href  = m.group(2);
            KeyImageRef ref = KeyImageRef.fromHref(href);
            if (ref == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            // Populate sopClassUID from DB (needed for SR IMAGE content item)
            if (ref.getSopClassUID() == null && ref.getSopUID() != null) {
                String pid = doc.getPatientID();
                String sClass = DatabaseHandler.getInstance().getValueFromImage(
                        "SOPClassUID", pid, ref.getStudyUID(), ref.getSeriesUID(), ref.getSopUID());
                if (sClass != null) ref.setSopClassUID(sClass);
            }
            ref.setLabel(label);
            // Add to grid only if not already present (match by sopUID)
            boolean dup = existing.stream()
                    .anyMatch(r -> ref.getSopUID() != null && ref.getSopUID().equals(r.getSopUID()));
            if (!dup) {
                keyImageGrid.addRow(ref, null);
                existing = keyImageGrid.getKeyImageRefs(); // refresh after add
            }
            m.appendReplacement(sb, ""); // remove from body
            moved = true;
        }
        m.appendTail(sb);

        if (moved) {
            // Collapse runs of blank lines left after link removal
            String cleaned = sb.toString().replaceAll("(\r?\n){3,}", "\n\n").stripLeading();
            mdEditor.setText(cleaned);
            Log.logger.info("ReportEditorDialog - moved " + "inline key image(s) to grid on finalize");
        }
    }

    /** Insert key-image(s) from all currently selected viewer series.
     *  Each selected Praparat contributes its current slide.
     *  Already-registered SOP Instance UIDs are silently skipped. */
    private void insertKeyImage() {
        Viewer2DScreen viewer = Viewer2DScreen.getInstance();
        if (viewer == null) {
            info(Resources.i18n("Reporting.editor.noViewer"));
            return;
        }
        ArrayList<Praparat> sel = viewer.getSelectedPraps();
        if (sel == null || sel.isEmpty()) {
            info(Resources.i18n("Reporting.editor.noSelection"));
            return;
        }

        // Collect already-registered SOP UIDs for duplicate check
        java.util.Set<String> registered = new java.util.HashSet<>();
        for (KeyImageRef r : keyImageGrid.getKeyImageRefs()) {
            if (r.getSopUID() != null) registered.add(r.getSopUID());
        }

        String label = Resources.i18n("Reporting.editor.keyImageLabel");
        int added = 0;
        for (Praparat pp : sel) {
            Object[] uids = pp.getUIDs();
            String patID     = (String) uids[0];
            String studyUID  = (String) uids[1];
            String seriesUID = (String) uids[2];
            String[] sopUIDs = (String[]) uids[3];

            SlideGlass slide = pp.getCurrentSlide();
            String sop = null;
            if (slide != null && slide.getSOPInstanceUID() != null) {
                sop = slide.getSOPInstanceUID();
            } else if (sopUIDs != null && sopUIDs.length > 0) {
                sop = sopUIDs[0];
            }
            if (sop == null) continue;
            if (registered.contains(sop)) continue; // already in grid — skip

            String sopClass = DatabaseHandler.getInstance()
                    .getValueFromImage("SOPClassUID", patID, studyUID, seriesUID, sop);
            java.awt.image.BufferedImage rendered = slide != null ? slide.createCaptureImage() : null;
            java.awt.image.BufferedImage thumb =
                    rendered != null ? DicomThumbnailCache.preCacheRendered(sop, rendered) : null;
            KeyImageRef ref = new KeyImageRef(studyUID, seriesUID, sop, sopClass, label);
            if (slide != null) {
                double[] minMax = slide.getCurrentWindowMinMax();
                float wc = (float) ((minMax[0] + minMax[1]) / 2.0);
                float ww = (float) (minMax[1] - minMax[0]);
                if (ww > 0f) {
                    ref.setWindowCenter(wc);
                    ref.setWindowWidth(ww);
                }
            }
            keyImageGrid.addRow(ref, thumb);
            registered.add(sop);
            added++;
        }

        if (added == 0 && !sel.isEmpty()) {
            info(Resources.i18n("Reporting.editor.keyImageAlreadyRegistered"));
        }
    }

    // ---- Helpers -------------------------------------------------------------

    private void refreshStatus() {
        statusLabel.setText("   [" + doc.getStatus() + "]");
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg,
                Resources.i18n("dialog.title.information"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static JLabel infoLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(lbl.getFont().getSize() - 1f));
        return lbl;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String get(HashMap<String, String> map, String key, String def) {
        if (map == null) return def;
        String v = map.get(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    /**
     * Default Markdown body for new reports (P1-2: structured sections).
     * Localised via i18n so section headings adapt to the user's locale.
     */
    private static String buildInitialBody() {
        return "## " + Resources.i18n("Reporting.section.clinicalHistory") + "\n\n"
             + "## " + Resources.i18n("Reporting.section.technique")       + "\n\n"
             + "## " + Resources.i18n("Reporting.section.findings")        + "\n\n"
             + "## " + Resources.i18n("Reporting.section.impression")      + "\n\n";
    }
}
