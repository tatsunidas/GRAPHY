package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Window;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.vis.configuration.Resources;
import com.vis.core.reporting.ReportDocument;
import com.vis.core.reporting.ReportService;

/**
 * Reports list for a patient/study. Unifies GRAPHY-authored reports (REPORT table,
 * with open/edit/delete) and imported SR/RDSR/KO instances (view-only). Embeddable
 * in the main screen / a popup.
 *
 * @author tatsunidas
 */
public class ReportListPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final ReportService service = new ReportService();
	private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd HH:mm");

	private final DefaultTableModel model;
	private final JTable table;
	private List<Entry> entries;

	private String patID;
	private String studyUID;
	private String studyDate;

	/** A list row: either a GRAPHY-authored report or an imported (view-only) SR instance. */
	private static class Entry {
		final ReportDocument doc; // non-null => GRAPHY-authored (editable)
		final String impSeriesUID; // imported SR
		final String impSopUID;
		final String impType; // "Report" / "RDSR" / "KO"

		Entry(ReportDocument doc) {
			this.doc = doc;
			this.impSeriesUID = null;
			this.impSopUID = null;
			this.impType = null;
		}

		Entry(String seriesUID, String sopUID, String type) {
			this.doc = null;
			this.impSeriesUID = seriesUID;
			this.impSopUID = sopUID;
			this.impType = type;
		}

		boolean isGraphy() {
			return doc != null;
		}
	}

	public ReportListPanel() {
		super(new BorderLayout());

		model = new DefaultTableModel(new Object[] {
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

	/** Set the patient/study context and reload the list. */
	public void setContext(String patID, String studyUID, String studyDate) {
		this.patID = patID;
		this.studyUID = studyUID;
		this.studyDate = studyDate;
		reload();
	}

	public void reload() {
		model.setRowCount(0);
		entries = new ArrayList<>();
		if (patID == null || studyUID == null) {
			return;
		}
		// GRAPHY-authored reports (drafts + finalized)
		for (ReportDocument d : service.listReports(patID, studyUID)) {
			entries.add(new Entry(d));
			String when = d.getModifiedMillis() > 0 ? fmt.format(new Date(d.getModifiedMillis())) : "";
			model.addRow(new Object[] { when, d.getTitle() == null ? "" : d.getTitle(), d.getStatus(),
					d.getAuthor() == null ? "" : d.getAuthor() });
		}
		// Imported SR/RDSR/KO (view-only)
		String imported = Resources.i18n("Reporting.list.imported");
		for (String[] sr : service.listImportedSrInStudy(patID, studyUID)) {
			entries.add(new Entry(sr[0], sr[1], sr[2]));
			String title = "[" + (sr[2] == null ? "SR" : sr[2]) + "]";
			model.addRow(new Object[] { "", title, imported, "" });
		}
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

	private void createReport() {
		if (patID == null || studyUID == null) {
			JOptionPane.showMessageDialog(this, Resources.i18n("Reporting.list.noStudy"));
			return;
		}
		ReportEditorDialog dlg = ReportEditorDialog.showNew(owner(), patID, studyUID, studyDate, null);
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
			// imported SR/RDSR/KO -> render the DICOM SR
			service.openSr(patID, studyUID, e.impSeriesUID, e.impSopUID);
			return;
		}
		ReportDocument d = e.doc;
		if (d.getStatus() == ReportDocument.Status.FINAL && d.getSrSopInstanceUID() != null) {
			service.openSr(patID, studyUID, d.getSeriesUID(), d.getSrSopInstanceUID());
		} else {
			SRHtmlViewerWindow.showSr(d.getTitle(), d.getBodyHtml(), patID);
		}
	}

	private void deleteSelected() {
		Entry e = selected();
		if (e == null) {
			return;
		}
		if (!e.isGraphy()) {
			JOptionPane.showMessageDialog(this, Resources.i18n("Reporting.list.importedNotDeletable"));
			return;
		}
		int ans = JOptionPane.showConfirmDialog(this, Resources.i18n("Reporting.list.confirmDelete"),
				Resources.i18n("dialog.title.information"), JOptionPane.YES_NO_OPTION);
		if (ans == JOptionPane.YES_OPTION) {
			service.deleteReport(e.doc.getReportId());
			reload();
		}
	}
}
