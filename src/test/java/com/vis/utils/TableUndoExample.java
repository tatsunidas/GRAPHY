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
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.CellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.text.Document;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoManager;

public class TableUndoExample {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		// テーブルモデルの作成
		// UndoManagerの作成
        UndoManager undoManager = new UndoManager();
        DefaultTableModel model = new DefaultTableModel() {
            @Override
            public void setValueAt(Object aValue, int row, int column) {
            	 Object oldValue = getValueAt(row, column);
                 if (!oldValue.equals(aValue)) {
                     super.setValueAt(aValue, row, column);
                     undoManager.addEdit(new CellEdit(oldValue, aValue, row, column, this));
                 }
            }
        };
        model.addColumn("Name");
        model.addColumn("Age");
        model.addRow(new Object[]{"Alice", 25});
        model.addRow(new Object[]{"Bob", 30});
        model.addRow(new Object[]{"Carol", 22});

        // JTableの作成
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);

        

        // JTableに追加されるセルエディタにUndoableEditListenerを追加
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            JTextField textField = new JTextField();
            DefaultCellEditor cellEditor = new DefaultCellEditor(textField);
            column.setCellEditor(cellEditor);

            Document doc = textField.getDocument();
            doc.addUndoableEditListener(new UndoableEditListener() {
                public void undoableEditHappened(UndoableEditEvent e) {
                    undoManager.addEdit(e.getEdit());
                }
            });
        }

        // Ctrl+ZでUndo, Ctrl+YでRedoを登録
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "Undo");
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "Redo");

        table.getActionMap().put("Undo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            }
        });

        table.getActionMap().put("Redo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                }
            }
        });

        // JFrameの作成と設定
        JFrame frame = new JFrame("JTable Undo Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(scrollPane);
        frame.setSize(300, 200);
        frame.setVisible(true);
	}

	
	// セルの編集操作を表すクラス
    static class CellEdit extends AbstractUndoableEdit {
        private final Object oldValue;
        private final Object newValue;
        private final int row;
        private final int column;
        private final DefaultTableModel model;

        public CellEdit(Object oldValue, Object newValue, int row, int column, DefaultTableModel model) {
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.row = row;
            this.column = column;
            this.model = model;
        }

        @Override
        public void undo() {
            super.undo();
            model.setValueAt(oldValue, row, column);
        }

        @Override
        public void redo() {
            super.redo();
            model.setValueAt(newValue, row, column);
        }
    }
}
