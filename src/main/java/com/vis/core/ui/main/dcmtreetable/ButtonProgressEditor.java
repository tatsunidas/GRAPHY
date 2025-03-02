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
package com.vis.core.ui.main.dcmtreetable;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.TableCellEditor;

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
        button.setText("Start");
        ((CardLayout) panel.getLayout()).show(panel, "Button");
    }

    @Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
		if (value == null) {
			return panel;
		}
		this.row = row;
		String v = (String) value;
		try {
			int number = Integer.parseInt(v);
			progressBar.setValue((Integer) number);
			((CardLayout) panel.getLayout()).show(panel, "Progress");
		} catch (NumberFormatException e) {
			button.setText(value.toString());
			((CardLayout) panel.getLayout()).show(panel, "Button");
		}
//		if (value instanceof Integer) {
//			progressBar.setValue((Integer) value);
//			((CardLayout) panel.getLayout()).show(panel, "Progress");
//		} else {
//			button.setText(value.toString());
//			((CardLayout) panel.getLayout()).show(panel, "Button");
//		}
		return panel;
	}

    @Override
    public Object getCellEditorValue() {
        return (worker != null && !worker.isDone()) ? progressBar.getValue() : "Start";
    }
}
