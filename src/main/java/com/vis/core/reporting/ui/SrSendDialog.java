package com.vis.core.reporting.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.reporting.ReportDocument;
import com.vis.core.reporting.ReportService;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;

/**
 * Modal dialog for sending a finalized SR to one or more remote PACS nodes.
 * Shows the list of known DICOM servers; the user ticks one or more and clicks
 * "Send". Transmission runs on a background thread.
 *
 * @author tatsunidas
 */
public class SrSendDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final ReportDocument doc;
    private final ReportService service;
    private final JList<DicomCommunicationNode> serverList;
    private final DefaultListModel<DicomCommunicationNode> listModel = new DefaultListModel<>();
    private final JProgressBar progress = new JProgressBar();
    private final JButton sendBtn;
    private final JButton closeBtn;

    /**
     * Show a modal C-STORE send dialog. Returns after the dialog is closed.
     *
     * @param owner owner window
     * @param doc   the finalized report whose SR file to send
     */
    public static void show(Window owner, ReportDocument doc) {
        SrSendDialog dlg = new SrSendDialog(owner, doc);
        dlg.setVisible(true);
    }

    private SrSendDialog(Window owner, ReportDocument doc) {
        super(owner, Resources.i18n("Reporting.send.title"), ModalityType.APPLICATION_MODAL);
        this.doc = doc;
        this.service = new ReportService();

        serverList = new JList<>(listModel);
        serverList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        loadServers();

        progress.setStringPainted(true);
        progress.setString("");

        sendBtn  = new JButton(Resources.i18n("Reporting.send.send"));
        closeBtn = new JButton(Resources.i18n("Reporting.action.close"));
        sendBtn.addActionListener(e -> send());
        closeBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(sendBtn);
        buttons.add(closeBtn);

        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(new JLabel(Resources.i18n("Reporting.send.selectServer")), BorderLayout.NORTH);
        content.add(new JScrollPane(serverList), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(progress, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        setPreferredSize(new Dimension(460, 320));
        pack();
        setLocationRelativeTo(owner);
    }

    private void loadServers() {
        DatabaseHandler db = DatabaseHandler.getInstance();
        if (db == null) return;
        List<DicomCommunicationNode> nodes = db.loadServerList();
        if (nodes != null) {
            for (DicomCommunicationNode n : nodes) {
                listModel.addElement(n);
            }
        }
        if (listModel.isEmpty()) {
            listModel.addElement(null);
            serverList.setEnabled(false);
        }
    }

    private void send() {
        List<DicomCommunicationNode> selected = serverList.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    Resources.i18n("Reporting.send.noServer"),
                    Resources.i18n("dialog.title.information"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        sendBtn.setEnabled(false);
        closeBtn.setEnabled(false);
        progress.setIndeterminate(true);
        progress.setString(Resources.i18n("Reporting.send.sending"));

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                List<String> failed = new ArrayList<>();
                for (DicomCommunicationNode dest : selected) {
                    boolean ok = service.sendSrToRemote(
                            dest,
                            doc.getStudyUID(),
                            doc.getSeriesUID(),
                            doc.getSrSopInstanceUID());
                    if (!ok) {
                        failed.add(dest.getNickname() != null ? dest.getNickname() : dest.getAETitle());
                        Log.logger.warning("SrSendDialog - send to " + dest.getAETitle() + " failed");
                    }
                }
                return failed;
            }

            @Override
            protected void done() {
                progress.setIndeterminate(false);
                sendBtn.setEnabled(true);
                closeBtn.setEnabled(true);
                try {
                    List<String> failed = get();
                    if (failed.isEmpty()) {
                        progress.setString(Resources.i18n("Reporting.send.success"));
                    } else {
                        progress.setString(Resources.i18n("Reporting.send.partialFailure")
                                + ": " + String.join(", ", failed));
                    }
                } catch (Exception ex) {
                    progress.setString(Resources.i18n("Reporting.send.failed"));
                }
            }
        }.execute();
    }
}
