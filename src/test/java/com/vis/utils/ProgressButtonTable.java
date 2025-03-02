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
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.utils;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.ExecutionException;

public class ProgressButtonTable {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("JTable with Progress Button");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            String[] columnNames = {"Task", "Progress"};
            Object[][] data = {
                    {"Task 1", "Start"},
                    {"Task 2", "Start"},
                    {"Task 3", "Start"}
            };

            DefaultTableModel model = new DefaultTableModel(data, columnNames);
            JTable table = new JTable(model);

            // カラムのレンダラーとエディターを設定
            table.getColumnModel().getColumn(1).setCellRenderer(new ButtonProgressRenderer());
            table.getColumnModel().getColumn(1).setCellEditor(new ButtonProgressEditor(table));

            frame.add(new JScrollPane(table));
            frame.setSize(400, 200);
            frame.setVisible(true);
        });
    }
}

// カラムのボタン＆プログレスバーのレンダラー
class ButtonProgressRenderer extends JPanel implements TableCellRenderer {
    private final JButton button = new JButton("Start");
    private final JProgressBar progressBar = new JProgressBar(0, 100);

    public ButtonProgressRenderer() {
        setLayout(new CardLayout());
        add(button, "Button");
        add(progressBar, "Progress");
        progressBar.setStringPainted(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof Integer) {
            progressBar.setValue((Integer) value);
            ((CardLayout) getLayout()).show(this, "Progress");
        } else {
            button.setText(value.toString());
            ((CardLayout) getLayout()).show(this, "Button");
        }
        return this;
    }
}

// カラムのエディター（ボタン & プログレスバーの切り替えと処理制御）
class ButtonProgressEditor extends AbstractCellEditor implements TableCellEditor {
    private final JPanel panel = new JPanel(new CardLayout());
    private final JButton button = new JButton("Start");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private SwingWorker<Void, Integer> worker;
    private boolean isSuspended = false;
    private int progressValue = 0;

    private JTable table;
    private int row;

    public ButtonProgressEditor(JTable table) {
        this.table = table;

        panel.add(button, "Button");
        panel.add(progressBar, "Progress");
        progressBar.setStringPainted(true);

        button.addActionListener(e -> startProcess());
        progressBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleSuspendResume();
            }
        });
    }

    private void startProcess() {
        button.setText("Running...");
        ((CardLayout) panel.getLayout()).show(panel, "Progress");

        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws InterruptedException {
                for (progressValue = 0; progressValue <= 100; progressValue++) {
                    if (isCancelled()) break;
                    while (isSuspended) {
                        Thread.sleep(100);
                    }
                    publish(progressValue);
                    Thread.sleep(50);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int latestProgress = chunks.get(chunks.size() - 1);
                progressBar.setValue(latestProgress);
                table.setValueAt(latestProgress, row, 1);
            }

            @Override
            protected void done() {
                try {
                    get(); // 例外がある場合にキャッチ
                    reset();
                } catch (ExecutionException | InterruptedException ignored) {}
            }
        };

        worker.execute();
    }

    private void toggleSuspendResume() {
        if (worker == null) return;

        isSuspended = !isSuspended;
        if (isSuspended) {
            progressBar.setString("Suspended");
        } else {
            progressBar.setString(null);
        }
    }

    private void reset() {
        table.setValueAt("Start", row, 1);
        ((CardLayout) panel.getLayout()).show(panel, "Button");
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        this.row = row;
        if (value instanceof Integer) {
            progressBar.setValue((Integer) value);
            ((CardLayout) panel.getLayout()).show(panel, "Progress");
        } else {
            button.setText(value.toString());
            ((CardLayout) panel.getLayout()).show(panel, "Button");
        }
        return panel;
    }

    @Override
    public Object getCellEditorValue() {
        return (worker != null && !worker.isDone()) ? progressBar.getValue() : "Start";
    }
}
