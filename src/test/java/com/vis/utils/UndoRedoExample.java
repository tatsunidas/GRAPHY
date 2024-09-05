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

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import javax.swing.table.*;

public class UndoRedoExample {
    private static final int HISTORY_SIZE = 3;

    private static class MyTableModel extends DefaultTableModel {
        private UndoManager undoManager = new UndoManager();
        private List<UndoableEdit> editHistory = new ArrayList<>();

        @Override
        public void setValueAt(Object aValue, int row, int column) {
            // Create an UndoableEdit for the change
            UndoableEdit edit = new AbstractUndoableEdit() {
                private final Object oldValue = getValueAt(row, column);
                private final Object newValue = aValue;

                @Override
                public void undo() {
                    super.undo();
                    setValueAt(oldValue, row, column);
                }

                @Override
                public void redo() {
                    super.redo();
                    setValueAt(newValue, row, column);
                }
            };
            undoManager.addEdit(edit);
            editHistory.add(edit);
            if (editHistory.size() > HISTORY_SIZE) {
                editHistory.remove(0);
            }
            super.setValueAt(aValue, row, column);
        }

        public UndoManager getUndoManager() {
            return undoManager;
        }

        public List<UndoableEdit> getEditHistory() {
            return editHistory;
        }
    }

    private static class UndoAction extends AbstractAction {
        private final UndoManager undoManager;

        public UndoAction(UndoManager undoManager) {
            super("Undo");
            this.undoManager = undoManager;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (undoManager.canUndo()) {
                undoManager.undo();
            }
        }
    }

    private static class RedoAction extends AbstractAction {
        private final UndoManager undoManager;

        public RedoAction(UndoManager undoManager) {
            super("Redo");
            this.undoManager = undoManager;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (undoManager.canRedo()) {
                undoManager.redo();
            }
        }
    }

    private static class RestoreAction extends AbstractAction {
        private final MyTableModel tableModel;
        private final int index;

        public RestoreAction(MyTableModel tableModel, int index) {
            super("Restore " + (index + 1));
            this.tableModel = tableModel;
            this.index = index;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<UndoableEdit> history = tableModel.getEditHistory();
            if (index >= 0 && index < history.size()) {
                UndoableEdit edit = history.get(index);
                try {
                    edit.undo();
                } catch (CannotUndoException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Undo/Redo Example");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            MyTableModel tableModel = new MyTableModel();
            tableModel.setColumnIdentifiers(new String[] {"Column 1", "Column 2"});
            JTable table = new JTable(tableModel);
            tableModel.addRow(new Object[] {"Cell 1", "Cell 2"});

            JPanel panel = new JPanel();
            panel.add(new JButton(new UndoAction(tableModel.getUndoManager())));
            panel.add(new JButton(new RedoAction(tableModel.getUndoManager())));

            // Add Restore Actions
            for (int i = 0; i < HISTORY_SIZE; i++) {
                panel.add(new JButton(new RestoreAction(tableModel, i)));
            }

            frame.add(new JScrollPane(table), "Center");
            frame.add(panel, "South");

            frame.pack();
            frame.setVisible(true);
        });
    }
}