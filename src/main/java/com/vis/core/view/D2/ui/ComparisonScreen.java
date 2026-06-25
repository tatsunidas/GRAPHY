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
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.view.D2.ui.ComparisonBoard.StudyRef;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext;
import com.vis.db.DatabaseHandler;

/**
 * Stand-alone prior-image comparison window. Studies are laid out as columns
 * (chronological) and series as rows, by reusing the existing Praparat/SlideGlass
 * components inside a {@link ComparisonBoard}.
 * <p>
 * Skeleton scope: window + columnar layout + launch/add flows + different-patient
 * warning. Mouse-driven tools (WW/WL, pan, zoom, scroll) follow the 2D viewer's
 * current tool type and work without a dedicated toolbar; immediate-action tools
 * and a dedicated toolbar/menu are deferred (Phase 2).
 *
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class ComparisonScreen extends JFrame implements ImageViewerContext {

	private static final ComparisonScreen instance = new ComparisonScreen();

	private final ComparisonBoard board;

	private JComboBox<String> colCountCombo;
	private JButton prevColBtn;
	private JButton nextColBtn;

	private ComparisonScreen() {
		setTitle(Resources.i18n("ComparisonScreen.title"));
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		board = new ComparisonBoard();
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(buildControlBar(), BorderLayout.NORTH);
		getContentPane().add(board, BorderLayout.CENTER);
		setSize(new Dimension(1400, 900));
		setLocationRelativeTo(null);
		// keep the singleton clean: forget studies when the window is closed.
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				board.clear();
			}
		});
	}

	/** Top bar: visible-study-column count (with ◀▶ paging) and a series show/hide picker. */
	private JToolBar buildControlBar() {
		JToolBar bar = new JToolBar();
		bar.setFloatable(false);

		bar.add(new JLabel(Resources.i18n("ComparisonScreen.toolbar.columns") + " "));
		String all = Resources.i18n("ComparisonScreen.toolbar.allColumns");
		colCountCombo = new JComboBox<>(new String[] { all, "2", "3", "4", "5", "6" });
		colCountCombo.setMaximumSize(colCountCombo.getPreferredSize());
		colCountCombo.addActionListener(e -> {
			String sel = (String) colCountCombo.getSelectedItem();
			int n;
			try {
				n = Integer.parseInt(sel);
			} catch (NumberFormatException ignore) {
				n = 0; // "all"
			}
			board.setVisibleColumnCount(n);
			refreshPaging();
		});
		bar.add(colCountCombo);

		prevColBtn = new JButton("◀");
		prevColBtn.setToolTipText(Resources.i18n("ComparisonScreen.toolbar.prev"));
		prevColBtn.addActionListener(e -> {
			board.shiftColumnWindow(-1);
			refreshPaging();
		});
		bar.add(prevColBtn);

		nextColBtn = new JButton("▶");
		nextColBtn.setToolTipText(Resources.i18n("ComparisonScreen.toolbar.next"));
		nextColBtn.addActionListener(e -> {
			board.shiftColumnWindow(1);
			refreshPaging();
		});
		bar.add(nextColBtn);

		bar.addSeparator();

		JButton seriesBtn = new JButton(Resources.i18n("ComparisonScreen.toolbar.series") + " ▾");
		seriesBtn.addActionListener(e -> showSeriesPicker(seriesBtn));
		bar.add(seriesBtn);

		bar.addSeparator();

		JButton wlBtn = new JButton(Resources.i18n("ComparisonScreen.toolbar.windowLevel"));
		wlBtn.setToolTipText(Resources.i18n("ComparisonScreen.toolbar.windowLevel.tip"));
		wlBtn.addActionListener(e -> openWindowLevel());
		bar.add(wlBtn);

		JButton presetBtn = new JButton(Resources.i18n("ViewerMenu.menu.presets") + " ▾");
		presetBtn.addActionListener(e -> showPresetPicker(presetBtn));
		bar.add(presetBtn);

		JButton invertBtn = new JButton(Resources.i18n("ComparisonScreen.toolbar.invert"));
		invertBtn.addActionListener(e -> applyToTargetPairs(Praparat::processInvertImages));
		bar.add(invertBtn);

		JButton flipLRBtn = new JButton(Resources.i18n("ComparisonScreen.toolbar.flipLR"));
		flipLRBtn.addActionListener(e -> applyToTargetPairs(Praparat::processFlipLR));
		bar.add(flipLRBtn);

		JButton flipHFBtn = new JButton(Resources.i18n("ComparisonScreen.toolbar.flipHF"));
		flipHFBtn.addActionListener(e -> applyToTargetPairs(Praparat::processFlipHF));
		bar.add(flipHFBtn);

		refreshPaging();
		return bar;
	}

	/**
	 * Applies an immediate per-Praparat action to every selected series pair (row),
	 * resolved through {@link ImageViewerContext}. Shows a hint when nothing is selected.
	 */
	private void applyToTargetPairs(java.util.function.Consumer<Praparat> action) {
		ArrayList<Praparat> targets = getActionTargetPraparats();
		if (targets == null || targets.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					Resources.i18n("ComparisonScreen.info.selectSeries"),
					Resources.i18n("dialog.title.information"),
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		for (Praparat pp : targets) {
			action.accept(pp);
		}
	}

	/**
	 * Opens the WW/WL adjuster for the selected series pair (representative cell).
	 * Drag-windowing already locks the whole pair; this is the toolbar entry that
	 * routes through {@link ImageViewerContext}.
	 */
	private void openWindowLevel() {
		ArrayList<Praparat> targets = getActionTargetPraparats();
		if (targets == null || targets.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					Resources.i18n("ComparisonScreen.info.selectSeries"),
					Resources.i18n("dialog.title.information"),
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		WwWlAdjusterDialog.showDialog(targets.get(0), getOwnerWindow());
	}

	/** WW/WL preset dropdown: the chosen preset is applied to every selected series pair. */
	private void showPresetPicker(JButton anchor) {
		List<WwWlPresets.WwWlPreset> presets = WwWlPresets.loadPresets();
		JPopupMenu menu = new JPopupMenu();
		if (presets == null || presets.isEmpty()) {
			JMenuItem none = new JMenuItem(Resources.i18n("ComparisonScreen.info.noSeries"));
			none.setEnabled(false);
			menu.add(none);
		} else {
			for (final WwWlPresets.WwWlPreset preset : presets) {
				JMenuItem item = new JMenuItem(preset.toString());
				item.addActionListener(e -> applyToTargetPairs(pp -> WwWlPresets.applyPreset(pp, preset)));
				menu.add(item);
			}
		}
		menu.show(anchor, 0, anchor.getHeight());
	}

	/* ------------------------------------------------------------------ */
	/* ImageViewerContext                                                 */
	/* ------------------------------------------------------------------ */

	@Override
	public int getCurrentToolType() {
		// Tool type follows the global 2D toolbar until the comparison window has its own.
		return Viewer2DScreen.getInstance().getCurrentToolType();
	}

	@Override
	public ArrayList<Praparat> getActionTargetPraparats() {
		return board.getActionTargetPraparats();
	}

	@Override
	public Window getOwnerWindow() {
		return this;
	}

	private void showSeriesPicker(JButton anchor) {
		JPopupMenu menu = new JPopupMenu();
		List<String> rows = board.getRowDescriptors();
		if (rows.isEmpty()) {
			JCheckBoxMenuItem empty = new JCheckBoxMenuItem(Resources.i18n("ComparisonScreen.info.noSeries"));
			empty.setEnabled(false);
			menu.add(empty);
		}
		for (int i = 0; i < rows.size(); i++) {
			final int idx = i;
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(rows.get(i), !board.isRowHidden(i));
			item.addActionListener(e -> board.setRowHidden(idx, !item.isSelected()));
			menu.add(item);
		}
		menu.show(anchor, 0, anchor.getHeight());
	}

	private void refreshPaging() {
		boolean limited = board.getColumnTotal() > 0;
		prevColBtn.setEnabled(limited && board.canShiftColumns(-1));
		nextColBtn.setEnabled(limited && board.canShiftColumns(1));
	}

	public static ComparisonScreen getInstance() {
		return instance;
	}

	/**
	 * Opens the comparison window (or brings it to front) and adds the given
	 * studies. If the window already shows studies, this behaves additively, which
	 * is exactly the "add to comparison view" flow. Adding a study whose patient
	 * differs from those already on the board prompts a confirmation.
	 */
	public void launch(List<StudyRef> studies) {
		SwingUtilities.invokeLater(() -> {
			setVisible(true);
			setExtendedState(getExtendedState() & ~JFrame.ICONIFIED);
			toFront();
			requestFocus();
			if (studies == null || studies.isEmpty()) {
				if (board.isEmpty()) {
					JOptionPane.showMessageDialog(this,
							Resources.i18n("ComparisonScreen.info.noSelection"),
							Resources.i18n("dialog.title.information"),
							JOptionPane.INFORMATION_MESSAGE);
				}
				return;
			}
			for (StudyRef ref : studies) {
				if (ref == null) {
					continue;
				}
				if (board.containsStudy(ref.patID, ref.studyUID)) {
					continue;
				}
				if (!board.isEmpty() && !board.getPatientIDs().contains(ref.patID)) {
					int res = JOptionPane.showConfirmDialog(this,
							Resources.i18n("ComparisonScreen.warn.differentPatient"),
							Resources.i18n("dialog.title.warning"),
							JOptionPane.OK_CANCEL_OPTION,
							JOptionPane.WARNING_MESSAGE);
					if (res != JOptionPane.OK_OPTION) {
						continue;
					}
				}
				board.addStudy(ref);
			}
			refreshPaging();
		});
	}

	/* ------------------------------------------------------------------ */
	/* StudyRef builders                                                  */
	/* ------------------------------------------------------------------ */

	/**
	 * Collects the distinct studies referenced by a tree selection (any level:
	 * patient / study / series / image). Multi-selection is not required; a single
	 * selected node is enough.
	 */
	public static List<StudyRef> studiesFromNodes(List<DICOMNode> nodes) {
		Map<String, StudyRef> map = new LinkedHashMap<>();
		if (nodes != null) {
			for (DICOMNode node : nodes) {
				collectStudy(node, map);
			}
		}
		return new ArrayList<>(map.values());
	}

	private static void collectStudy(DICOMNode node, Map<String, StudyRef> map) {
		if (node == null) {
			return;
		}
		int level = node.getLevel();
		if (level == DICOMNode.PATIENT) {
			List<DICOMNode> children = node.getChildren();
			if (children != null) {
				for (DICOMNode child : children) {
					collectStudy(child, map);
				}
			}
			return;
		}
		if (level >= DICOMNode.STUDY) {
			String patID = node.getData(DICOMNode.PatientID);
			String studyUID = node.getData(DICOMNode.StudyInstanceUID);
			if (patID == null || studyUID == null) {
				return;
			}
			String key = patID + " " + studyUID;
			if (!map.containsKey(key)) {
				map.put(key, new StudyRef(patID, studyUID,
						node.getData(DICOMNode.StudyDate),
						node.getData(DICOMNode.Modality),
						node.getData(DICOMNode.PatientName)));
			}
		}
	}

	/**
	 * Collects the distinct studies currently displayed on a 2D-viewer stage, so
	 * the user can jump from the 2D viewer into the comparison view.
	 */
	public static List<StudyRef> studiesFromPraparats(List<PraparatContext> contexts) {
		Map<String, StudyRef> map = new LinkedHashMap<>();
		if (contexts == null) {
			return new ArrayList<>();
		}
		DatabaseHandler db = DatabaseHandler.getInstance();
		for (PraparatContext ctx : contexts) {
			Object[] uids = ctx.getContextUIDs();
			if (uids == null || uids.length < 4) {
				continue;
			}
			String patID = (String) uids[0];
			String studyUID = (String) uids[1];
			String seriesUID = (String) uids[2];
			String[] sopUIDs = (String[]) uids[3];
			if (patID == null || studyUID == null) {
				continue;
			}
			String key = patID + " " + studyUID;
			if (map.containsKey(key)) {
				continue;
			}
			String studyDate = null;
			String modality = null;
			if (db != null && seriesUID != null && sopUIDs != null && sopUIDs.length > 0) {
				studyDate = db.getValueFromImage("StudyDate", patID, studyUID, seriesUID, sopUIDs[0]);
				modality = db.getValueFromImage("Modality", patID, studyUID, seriesUID, sopUIDs[0]);
			}
			String patientName = null;
			if (db != null) {
				try {
					patientName = db.getPatientInfo(patID).get("PatientName");
				} catch (Exception ex) {
					Log.logger.fine("ComparisonScreen: cannot resolve patient name for " + patID);
				}
			}
			map.put(key, new StudyRef(patID, studyUID, studyDate, modality, patientName));
		}
		return new ArrayList<>(map.values());
	}
}
