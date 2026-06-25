package com.vis.core.ui.main.dcmtreetable;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import com.vis.configuration.Resources;

/**
 * Renders the study-level "Report" column: a colored marker when a study has
 * reports (blue) or only unfinalized drafts (orange), with a count, or blank when
 * there are none / for non-study rows. The state is precomputed and stored on the
 * STUDY {@link DICOMNode} ({@link DICOMNode#ReportState} / {@link DICOMNode#ReportCount}).
 *
 * @author tatsunidas
 */
public class ReportCellRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	private static final Color REPORT_COLOR = new Color(30, 110, 200);
	private static final Color DRAFT_COLOR = new Color(210, 140, 0);

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
		setHorizontalAlignment(SwingConstants.CENTER);
		setText("");
		setToolTipText(null);

		DICOMNode node = null;
		if (table instanceof DICOMTreeTable) {
			node = ((DICOMTreeTable) table).nodeForRow(row);
		}
		decorate(this, node);
		return this;
	}

	/** Configure the marker for a node; returns true if the study has any reports. */
	static boolean decorate(DefaultTableCellRenderer label, DICOMNode node) {
		if (node == null || node.getLevel() != DICOMNode.STUDY) {
			label.setText("");
			return false;
		}
		String state = node.getData(DICOMNode.ReportState);
		String countStr = node.getData(DICOMNode.ReportCount);
		String count = (countStr == null || countStr.equals("0") || countStr.isEmpty()) ? "" : countStr;
		if ("report".equals(state)) {
			label.setForeground(REPORT_COLOR);
			label.setText("● " + count);
			label.setToolTipText(tip("Reporting.tree.reportTooltip", count));
			return true;
		}
		if ("draft".equals(state)) {
			label.setForeground(DRAFT_COLOR);
			label.setText("○ " + count);
			label.setToolTipText(tip("Reporting.tree.draftTooltip", count));
			return true;
		}
		label.setText("");
		return false;
	}

	private static String tip(String key, String count) {
		try {
			return java.text.MessageFormat.format(Resources.i18n(key), count);
		} catch (Exception e) {
			return key;
		}
	}
}
