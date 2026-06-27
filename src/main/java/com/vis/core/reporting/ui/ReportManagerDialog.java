package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import com.vis.configuration.Resources;

/**
 * Modeless dialog for patient-scoped report management.
 * <p>
 * The user enters a patient ID in the search bar and clicks Search (or presses
 * Enter) to load all GRAPHY-authored reports for that patient. If a patient ID
 * is supplied at construction time the list is populated immediately.
 * </p>
 *
 * @author tatsunidas
 */
public class ReportManagerDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextField patIdField;
    private final ReportListPanel listPanel;

    /**
     * @param owner        parent window (may be {@code null})
     * @param initialPatID patient ID to pre-fill and search; {@code null} leaves
     *                     the field empty
     */
    public ReportManagerDialog(Window owner, String initialPatID) {
        super(owner, Resources.i18n("Reporting.window.manage.title"),
                ModalityType.MODELESS);

        patIdField = new JTextField(20);
        listPanel = new ReportListPanel();

        setContentPane(buildContent());
        setSize(860, 520);
        setLocationRelativeTo(owner);

        if (initialPatID != null && !initialPatID.isEmpty()) {
            patIdField.setText(initialPatID);
            listPanel.setPatientContext(initialPatID);
        }
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 4));

        // -- search bar --
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        searchBar.add(new JLabel(Resources.i18n("Reporting.manager.patientId")));
        searchBar.add(patIdField);
        JButton searchBtn = new JButton(Resources.i18n("Reporting.manager.search"));
        searchBtn.addActionListener(e -> search());
        searchBar.add(searchBtn);

        // Enter key in the text field triggers search
        patIdField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "search");
        patIdField.getActionMap().put("search", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                search();
            }
        });

        root.add(searchBar, BorderLayout.NORTH);
        root.add(listPanel, BorderLayout.CENTER);
        return root;
    }

    private void search() {
        String pid = patIdField.getText().trim();
        if (pid.isEmpty()) {
            return;
        }
        listPanel.setPatientContext(pid);
    }
}
