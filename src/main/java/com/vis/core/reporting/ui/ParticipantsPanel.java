package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import com.vis.configuration.Resources;
import com.vis.core.reporting.ParticipationType;
import com.vis.core.reporting.ReportParticipant;
import com.vis.core.reporting.StaffRole;
import com.vis.core.reporting.staff.StaffMember;
import com.vis.core.reporting.staff.StaffStore;

/**
 * Editable table of report participants, pairing a {@link ParticipationType}
 * (how they are involved) with a {@link StaffRole} (their job). Names can be typed
 * or picked from the {@link StaffStore} directory. Used by {@link ReportEditorDialog}.
 *
 * @author tatsunidas
 */
public class ParticipantsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final StaffStore staffStore = new StaffStore();
	private final DefaultTableModel model;
	private final JTable table;
	private Runnable changeNotifier;

	public ParticipantsPanel() {
		super(new BorderLayout(0, 2));
		setBorder(BorderFactory.createTitledBorder(Resources.i18n("Reporting.participants.title")));

		model = new DefaultTableModel(new Object[] {
				Resources.i18n("Reporting.participants.col.participation"),
				Resources.i18n("Reporting.participants.col.name"),
				Resources.i18n("Reporting.participants.col.role") }, 0) {
			private static final long serialVersionUID = 1L;
			@Override
			public Class<?> getColumnClass(int c) {
				if (c == 0) return ParticipationType.class;
				if (c == 2) return StaffRole.class;
				return String.class;
			}
		};
		model.addTableModelListener(e -> {
			if (changeNotifier != null) changeNotifier.run();
		});

		table = new JTable(model);
		table.setRowHeight(22);
		table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

		// Participation column editor + renderer
		TableColumn partCol = table.getColumnModel().getColumn(0);
		JComboBox<ParticipationType> partCombo = new JComboBox<>(ParticipationType.values());
		partCombo.setRenderer(participationRenderer());
		partCol.setCellEditor(new DefaultCellEditor(partCombo));
		partCol.setCellRenderer(new EnumCellRenderer(true));
		partCol.setPreferredWidth(110);

		// Role column editor + renderer (null = unspecified)
		TableColumn roleCol = table.getColumnModel().getColumn(2);
		JComboBox<StaffRole> roleCombo = new JComboBox<>();
		roleCombo.addItem(null);
		for (StaffRole r : StaffRole.values()) roleCombo.addItem(r);
		roleCombo.setRenderer(roleRenderer());
		roleCol.setCellEditor(new DefaultCellEditor(roleCombo));
		roleCol.setCellRenderer(new EnumCellRenderer(false));
		roleCol.setPreferredWidth(130);

		JScrollPane sp = new JScrollPane(table);
		sp.setPreferredSize(new Dimension(560, 96));
		add(sp, BorderLayout.CENTER);
		add(buildButtons(), BorderLayout.SOUTH);
	}

	public void setChangeNotifier(Runnable r) {
		this.changeNotifier = r;
	}

	private JPanel buildButtons() {
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
		JButton add = new JButton(Resources.i18n("Reporting.participants.add"));
		add.addActionListener(e -> model.addRow(new Object[] { ParticipationType.AUTHOR, "", null }));
		JButton addStaff = new JButton(Resources.i18n("Reporting.participants.addFromStaff"));
		addStaff.addActionListener(e -> addFromStaff());
		JButton remove = new JButton(Resources.i18n("Reporting.participants.remove"));
		remove.addActionListener(e -> {
			int r = table.getSelectedRow();
			if (table.isEditing()) table.getCellEditor().stopCellEditing();
			if (r >= 0) model.removeRow(r);
		});
		bar.add(add);
		bar.add(addStaff);
		bar.add(remove);
		return bar;
	}

	private void addFromStaff() {
		List<StaffMember> staff = staffStore.getStaff();
		if (staff.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					Resources.i18n("Reporting.participants.noStaff"),
					Resources.i18n("dialog.title.information"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		StaffMember[] arr = staff.toArray(new StaffMember[0]);
		JComboBox<StaffMember> picker = new JComboBox<>(arr);
		JComboBox<ParticipationType> partPicker = new JComboBox<>(ParticipationType.values());
		partPicker.setRenderer(participationRenderer());
		JPanel form = new JPanel(new java.awt.GridLayout(2, 2, 4, 4));
		form.add(new JLabel(Resources.i18n("Reporting.participants.col.name")));
		form.add(picker);
		form.add(new JLabel(Resources.i18n("Reporting.participants.col.participation")));
		form.add(partPicker);
		int ok = JOptionPane.showConfirmDialog(this, form,
				Resources.i18n("Reporting.participants.addFromStaff"), JOptionPane.OK_CANCEL_OPTION);
		if (ok != JOptionPane.OK_OPTION) {
			return;
		}
		StaffMember m = (StaffMember) picker.getSelectedItem();
		ParticipationType pt = (ParticipationType) partPicker.getSelectedItem();
		if (m != null) {
			model.addRow(new Object[] { pt == null ? ParticipationType.AUTHOR : pt, m.getName(), m.getRole() });
		}
	}

	/** Replace table contents from a participant list. */
	public void setParticipants(List<ReportParticipant> participants) {
		model.setRowCount(0);
		if (participants != null) {
			for (ReportParticipant p : participants) {
				model.addRow(new Object[] { p.getParticipation(), p.getName() == null ? "" : p.getName(), p.getRole() });
			}
		}
	}

	/** Read the table back into a participant list (rows with a blank name are dropped). */
	public List<ReportParticipant> getParticipants() {
		if (table.isEditing()) {
			table.getCellEditor().stopCellEditing();
		}
		List<ReportParticipant> out = new ArrayList<>();
		for (int i = 0; i < model.getRowCount(); i++) {
			Object name = model.getValueAt(i, 1);
			String nm = name == null ? "" : name.toString().trim();
			if (nm.isEmpty()) {
				continue;
			}
			ParticipationType pt = (ParticipationType) model.getValueAt(i, 0);
			StaffRole role = (StaffRole) model.getValueAt(i, 2);
			out.add(new ReportParticipant(nm, role, pt == null ? ParticipationType.AUTHOR : pt));
		}
		return out;
	}

	// ---- renderers ---------------------------------------------------------

	private static ListCellRenderer<Object> participationRenderer() {
		return (list, value, index, isSelected, cellHasFocus) -> {
			JLabel l = new JLabel(value instanceof ParticipationType
					? Resources.i18n(((ParticipationType) value).i18nKey()) : "");
			if (isSelected) {
				l.setOpaque(true);
				l.setBackground(list.getSelectionBackground());
				l.setForeground(list.getSelectionForeground());
			}
			return l;
		};
	}

	private static ListCellRenderer<Object> roleRenderer() {
		return (list, value, index, isSelected, cellHasFocus) -> {
			JLabel l = new JLabel(value instanceof StaffRole
					? Resources.i18n(((StaffRole) value).i18nKey()) : "");
			if (isSelected) {
				l.setOpaque(true);
				l.setBackground(list.getSelectionBackground());
				l.setForeground(list.getSelectionForeground());
			}
			return l;
		};
	}

	/** Renders enum cells (participation / role) using their localized labels. */
	private static class EnumCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		private final boolean participation;
		EnumCellRenderer(boolean participation) {
			this.participation = participation;
		}
		@Override
		public Component getTableCellRendererComponent(JTable t, Object value, boolean sel,
				boolean focus, int row, int col) {
			String text = "";
			if (participation && value instanceof ParticipationType) {
				text = Resources.i18n(((ParticipationType) value).i18nKey());
			} else if (!participation && value instanceof StaffRole) {
				text = Resources.i18n(((StaffRole) value).i18nKey());
			}
			return super.getTableCellRendererComponent(t, text, sel, focus, row, col);
		}
	}
}
