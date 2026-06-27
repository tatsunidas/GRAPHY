package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.reporting.StaffRole;
import com.vis.core.reporting.staff.StaffMember;
import com.vis.core.reporting.staff.StaffStore;

/**
 * CRUD dialog for the lightweight staff directory (STAFF table). Staff entries
 * (name + job role) populate the participant pickers in {@link ReportEditorDialog}
 * so role assignment stays consistent. GRAPHY has no login — this is a convenience
 * directory, not an authentication store.
 *
 * @author tatsunidas
 */
public class StaffManagerDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final StaffStore store;

	private final DefaultListModel<StaffMember> listModel = new DefaultListModel<>();
	private final JList<StaffMember> staffList;
	private final JTextField nameField = new JTextField(20);
	private final JComboBox<StaffRole> roleCombo = new JComboBox<>(StaffRole.values());
	private final JTextField orgField = new JTextField(20);
	private final JTextField deptField = new JTextField(20);

	private final JButton newBtn = new JButton(Resources.i18n("Reporting.staff.new"));
	private final JButton saveBtn = new JButton(Resources.i18n("Reporting.staff.save"));
	private final JButton deleteBtn = new JButton(Resources.i18n("Reporting.staff.delete"));
	private final JButton closeBtn = new JButton(Resources.i18n("Reporting.action.close"));

	private StaffMember editing = null;

	public static void show(Window owner) {
		StaffManagerDialog dlg = new StaffManagerDialog(owner, new StaffStore());
		dlg.setVisible(true);
	}

	public static void show(Window owner, StaffStore store) {
		StaffManagerDialog dlg = new StaffManagerDialog(owner, store);
		dlg.setVisible(true);
	}

	private StaffManagerDialog(Window owner, StaffStore store) {
		super(owner, Resources.i18n("Reporting.staff.manager.title"), ModalityType.APPLICATION_MODAL);
		this.store = store;

		staffList = new JList<>(listModel);
		staffList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		staffList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) onSelect();
		});

		roleCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				String text = value instanceof StaffRole ? Resources.i18n(((StaffRole) value).i18nKey()) : "";
				return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
			}
		});

		newBtn.addActionListener(e -> startNew());
		saveBtn.addActionListener(e -> save());
		deleteBtn.addActionListener(e -> delete());
		closeBtn.addActionListener(e -> dispose());

		JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		listButtons.add(newBtn);
		listButtons.add(deleteBtn);

		JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
		leftPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		leftPanel.add(new JScrollPane(staffList), BorderLayout.CENTER);
		leftPanel.add(listButtons, BorderLayout.SOUTH);
		leftPanel.setPreferredSize(new Dimension(220, 0));

		JPanel form = new JPanel(new GridBagLayout());
		form.setBorder(BorderFactory.createTitledBorder(Resources.i18n("Reporting.staff.detail")));
		GridBagConstraints lc = new GridBagConstraints();
		lc.anchor = GridBagConstraints.WEST; lc.insets = new Insets(3, 4, 3, 4);
		GridBagConstraints fc = new GridBagConstraints();
		fc.fill = GridBagConstraints.HORIZONTAL; fc.weightx = 1; fc.insets = new Insets(3, 2, 3, 4);

		lc.gridx = 0; lc.gridy = 0; form.add(new JLabel(Resources.i18n("Reporting.staff.name")), lc);
		fc.gridx = 1; fc.gridy = 0; form.add(nameField, fc);
		lc.gridx = 0; lc.gridy = 1; form.add(new JLabel(Resources.i18n("Reporting.staff.role")), lc);
		fc.gridx = 1; fc.gridy = 1; form.add(roleCombo, fc);
		lc.gridx = 0; lc.gridy = 2; form.add(new JLabel(Resources.i18n("Reporting.staff.organization")), lc);
		fc.gridx = 1; fc.gridy = 2; form.add(orgField, fc);
		lc.gridx = 0; lc.gridy = 3; form.add(new JLabel(Resources.i18n("Reporting.staff.department")), lc);
		fc.gridx = 1; fc.gridy = 3; form.add(deptField, fc);

		JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		saveRow.add(saveBtn);
		fc.gridx = 1; fc.gridy = 4; form.add(saveRow, fc);

		JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
		rightPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));
		rightPanel.add(form, BorderLayout.NORTH);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
		split.setDividerLocation(220);
		split.setResizeWeight(0);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bottom.add(closeBtn);

		JPanel content = new JPanel(new BorderLayout());
		content.add(split, BorderLayout.CENTER);
		content.add(bottom, BorderLayout.SOUTH);
		setContentPane(content);

		setSize(new Dimension(620, 420));
		setLocationRelativeTo(owner);

		reload();
	}

	private void reload() {
		listModel.clear();
		for (StaffMember s : store.getStaff()) {
			listModel.addElement(s);
		}
	}

	private void onSelect() {
		StaffMember s = staffList.getSelectedValue();
		if (s == null) return;
		editing = s;
		nameField.setText(nz(s.getName()));
		roleCombo.setSelectedItem(s.getRole() == null ? StaffRole.PHYSICIAN : s.getRole());
		orgField.setText(nz(s.getOrganization()));
		deptField.setText(nz(s.getDepartment()));
		deleteBtn.setEnabled(true);
	}

	private void startNew() {
		staffList.clearSelection();
		editing = null;
		nameField.setText("");
		roleCombo.setSelectedItem(StaffRole.PHYSICIAN);
		orgField.setText("");
		deptField.setText("");
		deleteBtn.setEnabled(false);
		SwingUtilities.invokeLater(nameField::requestFocusInWindow);
	}

	private void save() {
		String name = nameField.getText().trim();
		if (name.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					Resources.i18n("Reporting.staff.nameRequired"),
					Resources.i18n("dialog.title.information"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		StaffRole role = (StaffRole) roleCombo.getSelectedItem();
		String org = orgField.getText().trim();
		String dept = deptField.getText().trim();
		if (editing == null) {
			store.addStaff(new StaffMember(null, name, role, org, dept));
		} else {
			editing.setName(name);
			editing.setRole(role);
			editing.setOrganization(org);
			editing.setDepartment(dept);
			store.updateStaff(editing);
		}
		store.invalidate();
		reload();
	}

	private void delete() {
		StaffMember s = staffList.getSelectedValue();
		if (s == null) return;
		int ans = JOptionPane.showConfirmDialog(this,
				Resources.i18n("Reporting.staff.confirmDelete") + " \"" + s.getName() + "\"?",
				Resources.i18n("dialog.title.information"), JOptionPane.YES_NO_OPTION);
		if (ans == JOptionPane.YES_OPTION) {
			store.removeStaff(s.getId());
			store.invalidate();
			startNew();
			reload();
		}
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
