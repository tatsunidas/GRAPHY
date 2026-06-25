/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.view.D2.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.PraparatSyncGroup;
import com.vis.db.DatabaseHandler;

/**
 * Comparison board: one study per column (X axis = study / chronological), its
 * series stacked as rows (Y axis). <b>Each study column is a real, shown
 * {@link Eyepiece}</b> ("1 study = 1 Eyepiece"), so the Eyepiece's built-in
 * drag-and-drop reorder is reused to pair adjacent series, and Praparats render
 * exactly as in the regular 2D viewer.
 * <p>
 * Two comparison-specific behaviours are layered on:
 * <ul>
 * <li><b>Row alignment</b>: every column's Eyepiece is forced to {@code
 * maxRows x 1} so columns with fewer series leave trailing blanks and equivalent
 * rows line up horizontally as pairs.</li>
 * <li><b>Cross-study scroll sync</b>: the board is a {@link PraparatSyncGroup}
 * spanning every column, so selected series in different studies scroll together
 * by IPP.</li>
 * </ul>
 *
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class ComparisonBoard extends JPanel implements PraparatSyncGroup {

	private static final Color BOARD_BG = new Color(24, 24, 24);
	private static final Color HEADER_BG = new Color(45, 52, 64);
	private static final int GAP = 3;
	private static final int CELL_W = 320;
	private static final int HEADER_H = 78;

	private final DatabaseHandler db = DatabaseHandler.getInstance();
	private final List<StudyColumn> columns = new ArrayList<>();

	/** 0 = show every column; otherwise the number of study columns visible at once. */
	private int visibleColumnCount = 0;
	/** Left edge of the visible column window (used only when limited). */
	private int windowStart = 0;
	/** Row indices (paired order) hidden across all columns. */
	private final Set<Integer> hiddenRows = new HashSet<>();

	public ComparisonBoard() {
		setBackground(BOARD_BG);
		setLayout(new GridLayout(1, 1, GAP, GAP));
		rebuildLayout();
	}

	/* ------------------------------------------------------------------ */
	/* queries                                                            */
	/* ------------------------------------------------------------------ */

	public boolean isEmpty() {
		return columns.isEmpty();
	}

	public boolean containsStudy(String patID, String studyUID) {
		for (StudyColumn c : columns) {
			if (c.ref.patID.equals(patID) && c.ref.studyUID.equals(studyUID)) {
				return true;
			}
		}
		return false;
	}

	public Set<String> getPatientIDs() {
		Set<String> ids = new LinkedHashSet<>();
		for (StudyColumn c : columns) {
			ids.add(c.ref.patID);
		}
		return ids;
	}

	public int getColumnTotal() {
		return columns.size();
	}

	/**
	 * Immediate-action target: every selected series expanded to its pair (row),
	 * deduplicated. So selecting one cell targets its whole pair across studies.
	 */
	public ArrayList<Praparat> getActionTargetPraparats() {
		ArrayList<Praparat> result = new ArrayList<>();
		for (StudyColumn c : columns) {
			for (Praparat sel : c.eye.getSelectingPraparats()) {
				for (Praparat mate : getSyncTargets(sel)) {
					if (!result.contains(mate)) {
						result.add(mate);
					}
				}
			}
		}
		return result;
	}

	/* ------------------------------------------------------------------ */
	/* cross-study slice synchronization (PraparatSyncGroup)              */
	/* ------------------------------------------------------------------ */

	/**
	 * The series PAIR of {@code source}: the series at the same paired-row index in
	 * every study column (operations in the Comparison View are per-pair).
	 */
	@Override
	public ArrayList<Praparat> getSyncTargets(Praparat source) {
		ArrayList<Praparat> pair = new ArrayList<>();
		int row = -1;
		for (StudyColumn c : columns) {
			for (int i = 0; i < c.series.size(); i++) {
				if (c.series.get(i).prap == source) {
					row = i;
					break;
				}
			}
			if (row >= 0) {
				break;
			}
		}
		if (row < 0) {
			pair.add(source);
			return pair;
		}
		for (StudyColumn c : columns) {
			if (row < c.series.size()) {
				Praparat p = c.series.get(row).prap;
				if (p != null) {
					pair.add(p);
				}
			}
		}
		return pair;
	}

	/* ------------------------------------------------------------------ */
	/* mutation                                                           */
	/* ------------------------------------------------------------------ */

	/**
	 * Loads every series of the given study into a new Eyepiece column. Columns are
	 * kept sorted by StudyDate (ascending). Duplicate studies are ignored.
	 *
	 * @return true if a column was added.
	 */
	public boolean addStudy(StudyRef ref) {
		if (ref == null || ref.patID == null || ref.studyUID == null) {
			return false;
		}
		if (containsStudy(ref.patID, ref.studyUID)) {
			Log.logger.fine("ComparisonBoard: study already present, skip. " + ref.studyUID);
			return false;
		}
		// 1. gather this study's series (natural SeriesNumber order) with DB metadata.
		List<SeriesEntry> entries = new ArrayList<>();
		ArrayList<String> seriesUIDs = db.getSeriesUidList(ref.patID, ref.studyUID);
		if (seriesUIDs != null) {
			for (String seriesUID : seriesUIDs) {
				ArrayList<String> sops = db.getInstanceUidList(ref.patID, ref.studyUID, seriesUID);
				if (sops == null || sops.isEmpty()) {
					continue;
				}
				String[] sopArr = sops.toArray(new String[sops.size()]);
				String refUID = db.getValueFromImage("FrameOfReferenceUID", ref.patID, ref.studyUID, seriesUID, sopArr[0]);
				if (refUID == null) {
					refUID = "";
				}
				entries.add(new SeriesEntry(seriesUID, sopArr, refUID,
						db.getValueFromSeries("Modality", ref.patID, ref.studyUID, seriesUID),
						db.getValueFromSeries("SeriesDescription", ref.patID, ref.studyUID, seriesUID),
						db.getValueFromSeries("BodyPartExamined", ref.patID, ref.studyUID, seriesUID)));
			}
		}

		// 2. default-pair: reorder this column's series to align with the richest
		// existing column (most series). The first study keeps its natural order.
		List<SeriesEntry> anchor = anchorSeries();
		List<SeriesEntry> ordered = SeriesPairing.order(anchor, entries);

		// 3. build the column Eyepiece and add Praparats in the paired order.
		Eyepiece eye = new Eyepiece(ref.patID);
		eye.setSyncGroup(this);
		eye.setOpaque(false);
		for (SeriesEntry e : ordered) {
			eye.addPraparat(ref.patID, ref.studyUID, e.seriesUID, e.sopUIDs, e.refUID);
			e.prap = eye.getPraparatAt(ref.patID, ref.studyUID, e.seriesUID, e.sopUIDs);
		}

		columns.add(new StudyColumn(ref, eye, ordered));
		columns.sort(Comparator.comparing(c -> c.ref.studyDate == null ? "" : c.ref.studyDate));
		rebuildLayout();
		return true;
	}

	/** The existing column with the most series, used as the pairing template. */
	private List<SeriesEntry> anchorSeries() {
		List<SeriesEntry> anchor = null;
		for (StudyColumn c : columns) {
			if (anchor == null || c.series.size() > anchor.size()) {
				anchor = c.series;
			}
		}
		return anchor;
	}

	/** Removes every column. */
	public void clear() {
		columns.clear();
		rebuildLayout();
	}

	/* ------------------------------------------------------------------ */
	/* layout                                                             */
	/* ------------------------------------------------------------------ */

	private void rebuildLayout() {
		removeAll();

		if (columns.isEmpty()) {
			setLayout(new BorderLayout());
			JLabel hint = new JLabel("比較するスタディがありません", SwingConstants.CENTER);
			hint.setForeground(Color.LIGHT_GRAY);
			add(hint, BorderLayout.CENTER);
			revalidate();
			repaint();
			return;
		}

		List<StudyColumn> visible = visibleColumns();

		// Per visible column, the Praparats for rows that are not hidden (compacted).
		List<List<Praparat>> shown = new ArrayList<>();
		int visibleMaxRows = 1;
		for (StudyColumn c : visible) {
			List<Praparat> praps = new ArrayList<>();
			for (int r = 0; r < c.series.size(); r++) {
				if (hiddenRows.contains(r)) {
					continue;
				}
				Praparat p = c.series.get(r).prap;
				if (p != null) {
					praps.add(p);
				}
			}
			shown.add(praps);
			visibleMaxRows = Math.max(visibleMaxRows, praps.size());
		}

		setLayout(new GridLayout(1, visible.size(), GAP, GAP));
		for (int k = 0; k < visible.size(); k++) {
			StudyColumn c = visible.get(k);
			syncEyeShelf(c.eye, shown.get(k));
			add(buildColumnPanel(c));
		}
		// updateLayout(rows, 1) reserves `rows` slots in a single column even when a
		// study has fewer series, leaving trailing blanks -> rows align across columns.
		for (StudyColumn c : visible) {
			c.eye.updateLayout(visibleMaxRows, 1);
		}
		revalidate();
		repaint();
	}

	/**
	 * Makes {@code eye}'s shelf contain exactly {@code show} (existing Praparat
	 * instances, in this order), preserving their window/zoom state. Clears then
	 * re-adds so the shelf order drives row placement deterministically.
	 */
	private void syncEyeShelf(Eyepiece eye, List<Praparat> show) {
		List<Praparat> current = eye.getAllPraparat();
		if (current != null) {
			for (Praparat p : new ArrayList<>(current)) {
				eye.removePraparat(p);
			}
		}
		for (Praparat p : show) {
			eye.addPraparat(p);
		}
	}

	/* ------------------------------------------------------------------ */
	/* display controls (column window + row visibility)                 */
	/* ------------------------------------------------------------------ */

	/** @param n number of study columns to show at once; 0 (or >= total) = all. */
	public void setVisibleColumnCount(int n) {
		visibleColumnCount = Math.max(0, n);
		// default the window to the most recent columns (rightmost, newest dates).
		windowStart = (visibleColumnCount == 0) ? 0 : Math.max(0, columns.size() - visibleColumnCount);
		rebuildLayout();
	}

	public boolean canShiftColumns(int delta) {
		if (visibleColumnCount == 0 || columns.size() <= visibleColumnCount) {
			return false;
		}
		int ns = windowStart + delta;
		return ns >= 0 && ns + visibleColumnCount <= columns.size();
	}

	public void shiftColumnWindow(int delta) {
		if (!canShiftColumns(delta)) {
			return;
		}
		windowStart += delta;
		rebuildLayout();
	}

	private List<StudyColumn> visibleColumns() {
		if (visibleColumnCount == 0 || columns.size() <= visibleColumnCount) {
			return columns;
		}
		windowStart = Math.min(Math.max(0, windowStart), columns.size() - visibleColumnCount);
		return columns.subList(windowStart, windowStart + visibleColumnCount);
	}

	/** Row labels (paired order) for the show/hide series picker. */
	public List<String> getRowDescriptors() {
		int maxRows = 0;
		for (StudyColumn c : columns) {
			maxRows = Math.max(maxRows, c.series.size());
		}
		List<String> labels = new ArrayList<>();
		for (int r = 0; r < maxRows; r++) {
			SeriesEntry rep = null;
			for (StudyColumn c : columns) {
				if (r < c.series.size()) {
					rep = c.series.get(r);
					break;
				}
			}
			String label = (rep == null) ? ""
					: ((rep.modality == null ? "" : rep.modality + " ") + (rep.description == null ? "" : rep.description)).trim();
			if (label.isEmpty()) {
				label = "Row " + (r + 1);
			}
			labels.add(label);
		}
		return labels;
	}

	public boolean isRowHidden(int rowIndex) {
		return hiddenRows.contains(rowIndex);
	}

	public void setRowHidden(int rowIndex, boolean hidden) {
		if (hidden) {
			hiddenRows.add(rowIndex);
		} else {
			hiddenRows.remove(rowIndex);
		}
		rebuildLayout();
	}

	private JPanel buildColumnPanel(StudyColumn col) {
		JPanel panel = new JPanel(new BorderLayout(0, GAP));
		panel.setOpaque(false);
		panel.add(buildHeader(col.ref), BorderLayout.NORTH);
		panel.add(col.eye, BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildHeader(StudyRef ref) {
		JPanel header = new JPanel();
		header.setBackground(HEADER_BG);
		header.setBorder(BorderFactory.createLineBorder(BOARD_BG, 2));
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

		JLabel idLabel = new JLabel(safe(ref.patID));
		idLabel.setForeground(Color.WHITE);
		idLabel.setFont(idLabel.getFont().deriveFont(Font.BOLD, 20f));
		idLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel dateLabel = new JLabel(formatDate(ref.studyDate));
		dateLabel.setForeground(new Color(0xA8, 0xD8, 0xFF));
		dateLabel.setFont(dateLabel.getFont().deriveFont(Font.BOLD, 18f));
		dateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		StringBuilder sub = new StringBuilder();
		if (ref.modality != null && !ref.modality.isEmpty()) {
			sub.append(ref.modality);
		}
		if (ref.patientName != null && !ref.patientName.isEmpty()) {
			if (sub.length() > 0) {
				sub.append("  /  ");
			}
			sub.append(ref.patientName);
		}
		JLabel subLabel = new JLabel(sub.toString());
		subLabel.setForeground(Color.LIGHT_GRAY);
		subLabel.setFont(subLabel.getFont().deriveFont(Font.PLAIN, 13f));
		subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		header.add(Box.createVerticalGlue());
		header.add(idLabel);
		header.add(dateLabel);
		header.add(subLabel);
		header.add(Box.createVerticalGlue());
		header.setPreferredSize(new Dimension(CELL_W, HEADER_H));
		return header;
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	/** "20240115" -> "2024-01-15"; leaves anything unexpected untouched. */
	private static String formatDate(String yyyymmdd) {
		if (yyyymmdd == null) {
			return "";
		}
		String d = yyyymmdd.trim();
		if (d.length() == 8 && d.chars().allMatch(Character::isDigit)) {
			return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8);
		}
		return d;
	}

	/* ------------------------------------------------------------------ */
	/* nested types                                                       */
	/* ------------------------------------------------------------------ */

	/** Lightweight, DB-free descriptor of a study to be compared. */
	public static class StudyRef {
		public final String patID;
		public final String studyUID;
		public final String studyDate;
		public final String modality;
		public final String patientName;

		public StudyRef(String patID, String studyUID, String studyDate, String modality, String patientName) {
			this.patID = patID;
			this.studyUID = studyUID;
			this.studyDate = studyDate;
			this.modality = modality;
			this.patientName = patientName;
		}
	}

	/** A single series with the DB metadata used for default pairing. */
	public static class SeriesEntry {
		public final String seriesUID;
		public final String[] sopUIDs;
		public final String refUID;
		public final String modality;
		public final String description;
		public final String bodyPart;
		/** Built once and reused so row show/hide preserves window/zoom state. */
		Praparat prap;

		public SeriesEntry(String seriesUID, String[] sopUIDs, String refUID,
				String modality, String description, String bodyPart) {
			this.seriesUID = seriesUID;
			this.sopUIDs = sopUIDs;
			this.refUID = refUID;
			this.modality = modality;
			this.description = description;
			this.bodyPart = bodyPart;
		}
	}

	/** One study column: its descriptor, the Eyepiece, and its series (display order). */
	private static class StudyColumn {
		final StudyRef ref;
		final Eyepiece eye;
		final List<SeriesEntry> series;

		StudyColumn(StudyRef ref, Eyepiece eye, List<SeriesEntry> series) {
			this.ref = ref;
			this.eye = eye;
			this.series = series;
		}
	}
}
