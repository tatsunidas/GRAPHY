package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Window;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
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

		add(buildToolbar(), BorderLayout.NORTH);
		add(new JScrollPane(table), BorderLayout.CENTER);
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
		model.setRowCount(0);
		entries = new ArrayList<>();
		switch (mode) {
		case PATIENT:
			if (patID != null) {
				for (ReportDocument d : service.listReportsForPatient(patID)) {
					addReportRow(d);
				}
			}
			break;
		case MULTI:
			if (studies != null) {
				for (String[] s : studies) {
					loadStudy(s[0], s[1], s.length > 2 ? s[2] : null);
				}
			}
			break;
		case SINGLE:
		default:
			loadStudy(patID, studyUID, studyDate);
			break;
		}
	}

	/** Append a study's GRAPHY reports + imported SR instances. */
	private void loadStudy(String pid, String suid, String sdate) {
		if (pid == null || suid == null) {
			return;
		}
		for (ReportDocument d : service.listReports(pid, suid)) {
			addReportRow(d);
		}
		String imported = Resources.i18n("Reporting.list.imported");
		for (String[] sr : service.listImportedSrInStudy(pid, suid)) {
			entries.add(new Entry(sr[0], sr[1], sr[2], pid, suid, sdate));
			String title = "[" + (sr[2] == null ? "SR" : sr[2]) + "]";
			model.addRow(new Object[] { nz(sdate), "", title, imported, "" });
		}
	}

	private void addReportRow(ReportDocument d) {
		entries.add(new Entry(d, d.getPatientID(), d.getStudyUID(), d.getStudyDate()));
		String when = d.getModifiedMillis() > 0 ? fmt.format(new Date(d.getModifiedMillis())) : "";
		model.addRow(new Object[] { nz(d.getStudyDate()), when, d.getTitle() == null ? "" : d.getTitle(),
				d.getStatus(), d.getAuthor() == null ? "" : d.getAuthor() });
	}

	private Entry selected() {
		int row = table.getSelectedRow();
		if (row < 0 || entries == null || row >= entries.size()) {
			return null;
		}
		return entries.get(row);
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
		if (d.getStatus() == ReportDocument.Status.FINAL && d.getSrSopInstanceUID() != null) {
			service.openSr(e.patID, e.studyUID, d.getSeriesUID(), d.getSrSopInstanceUID());
		} else {
			SRHtmlViewerWindow.showSr(d.getTitle(), d.getBodyHtml(), e.patID);
		}
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
