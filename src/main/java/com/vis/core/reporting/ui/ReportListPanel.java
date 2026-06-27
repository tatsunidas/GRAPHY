package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import com.vis.configuration.Resources;
import com.vis.core.reporting.ReportDocument;
import com.vis.core.reporting.ReportService;

/**
 * Reports list, scoped to one of three contexts:
 * <ul>
 *   <li><b>single study</b> ({@link #setContext}) — a tree-popup for one study;</li>
 *   <li><b>multiple studies</b> ({@link #setStudies}) — the studies currently open
 *       in the 2D viewer;</li>
 *   <li><b>patient</b> ({@link #setPatientContext}) — every GRAPHY report authored
 *       for a patient (the main-screen report management view).</li>
 * </ul>
 * Unifies GRAPHY-authored reports (REPORT table — open / edit / delete) and, in the
 * study-scoped modes, imported / derived SR instances (measurement SR, RDSR, KO —
 * view / delete-from-store). Each row carries its own patient/study so actions work
 * across a mixed, multi-study list.
 *
 * @author tatsunidas
 */
public class ReportListPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private enum Mode {
		SINGLE, MULTI, PATIENT
	}

	private final ReportService service = new ReportService();
	private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd HH:mm");

	private final DefaultTableModel model;
	private final JTable table;
	private List<Entry> entries;
	private JComboBox<String> statusFilter;

	private Mode mode = Mode.SINGLE;
	private String patID; // SINGLE / PATIENT
	private String studyUID; // SINGLE
	private String studyDate; // SINGLE
	private List<String[]> studies; // MULTI: each = {patID, studyUID, studyDate}

	/** A list row: either a GRAPHY-authored report or an imported (view-only) SR instance. */
	private static class Entry {
		final ReportDocument doc; // non-null => GRAPHY-authored (editable)
		final String impSeriesUID; // imported SR
		final String impSopUID;
		final String impType; // "Report" / "RDSR" / "KO"
		// owning patient/study of THIS row (list may span studies)
		final String patID;
		final String studyUID;
		final String studyDate;

		Entry(ReportDocument doc, String patID, String studyUID, String studyDate) {
			this.doc = doc;
			this.impSeriesUID = null;
			this.impSopUID = null;
			this.impType = null;
			this.patID = patID;
			this.studyUID = studyUID;
			this.studyDate = studyDate;
		}

		Entry(String seriesUID, String sopUID, String type, String patID, String studyUID, String studyDate) {
			this.doc = null;
			this.impSeriesUID = seriesUID;
			this.impSopUID = sopUID;
			this.impType = type;
			this.patID = patID;
			this.studyUID = studyUID;
			this.studyDate = studyDate;
		}

		boolean isGraphy() {
			return doc != null;
		}
	}

	public ReportListPanel() {
		super(new BorderLayout());

		model = new DefaultTableModel(new Object[] {
				Resources.i18n("Reporting.list.col.study"),
				Resources.i18n("Reporting.list.col.modified"),
				Resources.i18n("Reporting.list.col.title"),
				Resources.i18n("Reporting.list.col.status"),
				Resources.i18n("Reporting.list.col.author") }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		table = new JTable(model);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JPanel top = new JPanel(new BorderLayout());
		top.add(buildToolbar(),  BorderLayout.NORTH);
		top.add(buildFilter(),   BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);
		add(new JScrollPane(table), BorderLayout.CENTER);
	}

	private JPanel buildFilter() {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		p.add(new JLabel(Resources.i18n("Reporting.list.filter.status")));
		statusFilter = new JComboBox<>(new String[]{
			Resources.i18n("Reporting.list.filter.all"),
			Resources.i18n("Reporting.list.filter.draft"),
			Resources.i18n("Reporting.list.filter.final")
		});
		statusFilter.addActionListener(e -> applyFilter());
		p.add(statusFilter);
		return p;
	}

	private void applyFilter() {
		if (entries == null) return;
		String sel = (String) statusFilter.getSelectedItem();
		String all   = Resources.i18n("Reporting.list.filter.all");
		String draft = Resources.i18n("Reporting.list.filter.draft");
		model.setRowCount(0);
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			if (!all.equals(sel)) {
				if (e.isGraphy()) {
					boolean isDraft = e.doc.getStatus() == ReportDocument.Status.DRAFT;
					if (draft.equals(sel) && !isDraft) continue;
					if (!draft.equals(sel) &&  isDraft) continue;
				} else {
					// imported SRs count as FINAL
					if (draft.equals(sel)) continue;
				}
			}
			// Re-add the row
			if (e.isGraphy()) {
				String when = e.doc.getModifiedMillis() > 0
						? fmt.format(new Date(e.doc.getModifiedMillis())) : "";
				model.addRow(new Object[]{ nz(e.doc.getStudyDate()), when,
					e.doc.getTitle() == null ? "" : e.doc.getTitle(),
					e.doc.getStatus(), e.doc.getAuthor() == null ? "" : e.doc.getAuthor() });
			} else {
				String title = "[" + (e.impType == null ? "SR" : e.impType) + "]";
				model.addRow(new Object[]{ nz(e.studyDate), "", title,
					Resources.i18n("Reporting.list.imported"), "" });
			}
		}
	}

	private JToolBar buildToolbar() {
		JToolBar bar = new JToolBar();
		bar.setFloatable(false);
		JButton create = new JButton(Resources.i18n("Reporting.list.new"));
		create.addActionListener(e -> createReport());
		JButton open = new JButton(Resources.i18n("Reporting.list.open"));
		open.addActionListener(e -> openSelected());
		JButton view = new JButton(Resources.i18n("Reporting.list.view"));
		view.addActionListener(e -> viewSelected());
		JButton delete = new JButton(Resources.i18n("Reporting.list.delete"));
		delete.addActionListener(e -> deleteSelected());
		JButton refresh = new JButton(Resources.i18n("Reporting.list.refresh"));
		refresh.addActionListener(e -> reload());
		bar.add(create);
		bar.add(open);
		bar.add(view);
		bar.add(delete);
		bar.add(refresh);
		return bar;
	}

	/** Single-study context (tree popup). */
	public void setContext(String patID, String studyUID, String studyDate) {
		this.mode = Mode.SINGLE;
		this.patID = patID;
		this.studyUID = studyUID;
		this.studyDate = studyDate;
		reload();
	}

	/** Multi-study context: each entry = {patID, studyUID, studyDate}. Used for the studies open in the viewer. */
	public void setStudies(List<String[]> studies) {
		this.mode = Mode.MULTI;
		this.studies = studies;
		reload();
	}

	/** Patient context: every GRAPHY-authored report for the patient. */
	public void setPatientContext(String patID) {
		this.mode = Mode.PATIENT;
		this.patID = patID;
		reload();
	}

	public void reload() {
		// Rebuild entries list (full, unfiltered)
		entries = new ArrayList<>();
		switch (mode) {
		case PATIENT:
			if (patID != null) {
				// GRAPHY-authored reports (all studies)
				for (ReportDocument d : service.listReportsForPatient(patID)) {
					entries.add(new Entry(d, d.getPatientID(), d.getStudyUID(), d.getStudyDate()));
				}
				// Imported / derived SRs from every study of this patient (TD-4)
				for (String[] study : service.listStudiesForPatient(patID)) {
					String suid  = study[0];
					String sdate = study.length > 1 ? study[1] : null;
					for (String[] sr : service.listImportedSrInStudy(patID, suid)) {
						entries.add(new Entry(sr[0], sr[1], sr[2], patID, suid, sdate));
					}
				}
			}
			break;
		case MULTI:
			if (studies != null) {
				for (String[] s : studies) {
					collectStudy(s[0], s[1], s.length > 2 ? s[2] : null);
				}
			}
			break;
		case SINGLE:
		default:
			collectStudy(patID, studyUID, studyDate);
			break;
		}
		// Apply filter to refresh table rows
		model.setRowCount(0);
		applyFilter();
	}

	private void collectStudy(String pid, String suid, String sdate) {
		if (pid == null || suid == null) return;
		for (ReportDocument d : service.listReports(pid, suid)) {
			entries.add(new Entry(d, d.getPatientID(), d.getStudyUID(), d.getStudyDate()));
		}
		for (String[] sr : service.listImportedSrInStudy(pid, suid)) {
			entries.add(new Entry(sr[0], sr[1], sr[2], pid, suid, sdate));
		}
	}


	/** Visible entries after filter (parallel to table rows). */
	private List<Entry> visibleEntries() {
		if (entries == null) return new ArrayList<>();
		String sel  = statusFilter != null ? (String) statusFilter.getSelectedItem() : null;
		String all   = Resources.i18n("Reporting.list.filter.all");
		String draft = Resources.i18n("Reporting.list.filter.draft");
		if (sel == null || all.equals(sel)) return new ArrayList<>(entries);
		List<Entry> out = new ArrayList<>();
		for (Entry e : entries) {
			if (e.isGraphy()) {
				boolean isDraft = e.doc.getStatus() == ReportDocument.Status.DRAFT;
				if (draft.equals(sel) && !isDraft) continue;
				if (!draft.equals(sel) &&  isDraft) continue;
			} else {
				if (draft.equals(sel)) continue;
			}
			out.add(e);
		}
		return out;
	}

	private Entry selected() {
		int row = table.getSelectedRow();
		List<Entry> vis = visibleEntries();
		if (row < 0 || row >= vis.size()) return null;
		return vis.get(row);
	}

	private Window owner() {
		return SwingUtilities.getWindowAncestor(this);
	}

	/**
	 * @return the single study a new report can target ({patID, studyUID, studyDate}),
	 *         or {@code null} when the context is ambiguous (multiple or no studies).
	 */
	private String[] createTarget() {
		if (mode == Mode.SINGLE && patID != null && studyUID != null) {
			return new String[] { patID, studyUID, studyDate };
		}
		if (mode == Mode.MULTI && studies != null) {
			Map<String, String[]> distinct = new LinkedHashMap<>();
			for (String[] s : studies) {
				if (s[1] != null) {
					distinct.put(s[0] + "|" + s[1], s);
				}
			}
			if (distinct.size() == 1) {
				return distinct.values().iterator().next();
			}
		}
		return null;
	}

	private void createReport() {
		String[] t = createTarget();
		if (t == null) {
			JOptionPane.showMessageDialog(this, Resources.i18n("Reporting.list.createNeedsStudy"));
			return;
		}
		ReportEditorDialog dlg = ReportEditorDialog.showNew(owner(), t[0], t[1], t.length > 2 ? t[2] : null, null);
		dlg.setOnSaved(this::reload);
	}

	private void openSelected() {
		Entry e = selected();
		if (e == null) {
			return;
		}
		if (!e.isGraphy()) {
			JOptionPane.showMessageDialog(this, Resources.i18n("Reporting.list.importedNotEditable"));
			return;
		}
		ReportEditorDialog dlg = ReportEditorDialog.showExisting(owner(), e.doc);
		dlg.setOnSaved(this::reload);
	}

	private void viewSelected() {
		Entry e = selected();
		if (e == null) {
			return;
		}
		if (!e.isGraphy()) {
			service.openSr(e.patID, e.studyUID, e.impSeriesUID, e.impSopUID);
			return;
		}
		ReportDocument d = e.doc;
		// Always render from ReportDocument to preserve Markdown layout and encoding.
		// (The DICOM SR path uses TextContentRenderer which strips Markdown to plain text.)
		String keyImageHtml = KeyImageGridPanel.generateHtmlFromRefs(d.getKeyImages());
		String html = MarkdownEditorPanel.buildHtml(d.getBodyHtml(), d.isMarkdown(), keyImageHtml);
		SRHtmlViewerWindow.showSr(d.getTitle(), html, e.patID);
	}

	private void deleteSelected() {
		Entry e = selected();
		if (e == null) {
			return;
		}
		if (e.isGraphy()) {
			int ans = JOptionPane.showConfirmDialog(this, Resources.i18n("Reporting.list.confirmDelete"),
					Resources.i18n("dialog.title.information"), JOptionPane.YES_NO_OPTION);
			if (ans == JOptionPane.YES_OPTION) {
				service.deleteReport(e.doc.getReportId());
				afterDelete();
			}
			return;
		}
		// KO linked to a GRAPHY report must not be deleted independently
		if ("KO".equals(e.impType)) {
			com.vis.core.reporting.ReportDocument linked = service.findReportByKoSopUID(e.impSopUID);
			if (linked != null) {
				JOptionPane.showMessageDialog(this, Resources.i18n("Reporting.list.koLinkedToReport"),
						Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
				return;
			}
		}
		// imported / derived SR -> delete the DICOM object from the local store
		int ans = JOptionPane.showConfirmDialog(this, Resources.i18n("Reporting.list.confirmDeleteSr"),
				Resources.i18n("dialog.title.warning"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (ans != JOptionPane.YES_OPTION) {
			return;
		}
		final String pid = e.patID, suid = e.studyUID, seUID = e.impSeriesUID, sopUID = e.impSopUID;
		new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() {
				return service.deleteImportedSr(pid, suid, seUID, sopUID);
			}

			@Override
			protected void done() {
				boolean ok = false;
				try {
					ok = Boolean.TRUE.equals(get());
				} catch (Exception ignore) {
				}
				if (!ok) {
					JOptionPane.showMessageDialog(ReportListPanel.this,
							Resources.i18n("Reporting.list.deleteSrFailed"));
				}
				afterDelete();
			}
		}.execute();
	}

	/** Reload this panel and best-effort refresh the tree Report column. */
	private void afterDelete() {
		reload();
		com.vis.core.ui.main.MainScreen ms = com.vis.core.ui.main.MainScreen.getInstance();
		if (ms != null) {
			try {
				ms.loadLocalStudiesBySearchKey();
			} catch (Exception ignore) {
			}
		}
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
