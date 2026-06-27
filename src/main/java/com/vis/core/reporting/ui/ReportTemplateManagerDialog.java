package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.reporting.template.ReportTemplate;
import com.vis.core.reporting.template.ReportTemplateStore;

/**
 * CRUD dialog for managing user-defined report templates (P1-6).
 * Bundled (read-only) templates are listed but cannot be edited or deleted.
 * User-created templates are fully manageable and persisted to
 * {@code ~/.graphy/report-templates.json}.
 *
 * @author tatsunidas
 */
public class ReportTemplateManagerDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final ReportTemplateStore store;

    private final DefaultListModel<ReportTemplate> listModel = new DefaultListModel<>();
    private final JList<ReportTemplate> templateList;
    private final JTextField nameField     = new JTextField(24);
    private final JTextField categoryField = new JTextField(16);
    private final JTextArea  bodyArea      = new JTextArea(12, 40);

    private final JButton newBtn    = new JButton(Resources.i18n("Reporting.template.new"));
    private final JButton saveBtn   = new JButton(Resources.i18n("Reporting.template.save"));
    private final JButton deleteBtn = new JButton(Resources.i18n("Reporting.template.delete"));
    private final JButton closeBtn  = new JButton(Resources.i18n("Reporting.action.close"));

    private ReportTemplate editing = null;

    public static void show(Window owner, ReportTemplateStore store) {
        ReportTemplateManagerDialog dlg = new ReportTemplateManagerDialog(owner, store);
        dlg.setVisible(true);
    }

    private ReportTemplateManagerDialog(Window owner, ReportTemplateStore store) {
        super(owner, Resources.i18n("Reporting.template.manager.title"), ModalityType.APPLICATION_MODAL);
        this.store = store;

        templateList = new JList<>(listModel);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onSelect();
        });

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));

        newBtn.addActionListener(e -> startNew());
        saveBtn.addActionListener(e -> save());
        deleteBtn.addActionListener(e -> delete());
        closeBtn.addActionListener(e -> dispose());

        // Left panel: list + list buttons
        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        listButtons.add(newBtn);
        listButtons.add(deleteBtn);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        leftPanel.add(new JScrollPane(templateList), BorderLayout.CENTER);
        leftPanel.add(listButtons, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(200, 0));

        // Right panel: edit form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(Resources.i18n("Reporting.template.detail")));
        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST; lc.insets = new Insets(3, 4, 3, 4);
        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL; fc.weightx = 1; fc.insets = new Insets(3, 2, 3, 4);

        lc.gridx = 0; lc.gridy = 0; form.add(new JLabel(Resources.i18n("Reporting.template.name")), lc);
        fc.gridx = 1; fc.gridy = 0; form.add(nameField, fc);
        lc.gridx = 0; lc.gridy = 1; form.add(new JLabel(Resources.i18n("Reporting.template.category")), lc);
        fc.gridx = 1; fc.gridy = 1; form.add(categoryField, fc);
        lc.gridx = 0; lc.gridy = 2; lc.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel(Resources.i18n("Reporting.template.body")), lc);
        fc.gridx = 1; fc.gridy = 2; fc.weighty = 1; fc.fill = GridBagConstraints.BOTH;
        form.add(new JScrollPane(bodyArea), fc);

        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        saveRow.add(saveBtn);
        fc.gridx = 1; fc.gridy = 3; fc.weighty = 0; fc.fill = GridBagConstraints.HORIZONTAL;
        form.add(saveRow, fc);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));
        rightPanel.add(form, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(200);
        split.setResizeWeight(0);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(closeBtn);

        JPanel content = new JPanel(new BorderLayout());
        content.add(split, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);

        setSize(new Dimension(680, 480));
        setLocationRelativeTo(owner);

        reload();
    }

    private void reload() {
        listModel.clear();
        for (ReportTemplate t : store.getTemplates()) {
            listModel.addElement(t);
        }
    }

    private void onSelect() {
        ReportTemplate t = templateList.getSelectedValue();
        if (t == null) return;
        editing = t;
        nameField.setText(nz(t.getName()));
        categoryField.setText(nz(t.getCategory()));
        bodyArea.setText(nz(t.getBody()));
        bodyArea.setCaretPosition(0);
        boolean editable = store.isUserTemplate(t);
        nameField.setEditable(editable);
        categoryField.setEditable(editable);
        bodyArea.setEditable(editable);
        saveBtn.setEnabled(editable);
        deleteBtn.setEnabled(editable);
    }

    private void startNew() {
        templateList.clearSelection();
        editing = null;
        nameField.setText("");
        categoryField.setText("");
        bodyArea.setText("");
        nameField.setEditable(true);
        categoryField.setEditable(true);
        bodyArea.setEditable(true);
        saveBtn.setEnabled(true);
        deleteBtn.setEnabled(false);
        SwingUtilities.invokeLater(nameField::requestFocusInWindow);
    }

    private void save() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    Resources.i18n("Reporting.template.nameRequired"),
                    Resources.i18n("dialog.title.information"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String body = bodyArea.getText();
        String category = categoryField.getText().trim();
        if (editing == null || !store.isUserTemplate(editing)) {
            // new user template
            ReportTemplate t = new ReportTemplate(null, name, category, body);
            store.addUserTemplate(t);
        } else {
            editing.setName(name);
            editing.setCategory(category);
            editing.setBody(body);
            store.updateUserTemplate(editing);
        }
        store.invalidate();
        reload();
    }

    private void delete() {
        ReportTemplate t = templateList.getSelectedValue();
        if (t == null || !store.isUserTemplate(t)) return;
        int ans = JOptionPane.showConfirmDialog(this,
                Resources.i18n("Reporting.template.confirmDelete") + " \"" + t.getName() + "\"?",
                Resources.i18n("dialog.title.information"),
                JOptionPane.YES_NO_OPTION);
        if (ans == JOptionPane.YES_OPTION) {
            store.removeUserTemplate(t.getId());
            store.invalidate();
            startNew();
            reload();
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
