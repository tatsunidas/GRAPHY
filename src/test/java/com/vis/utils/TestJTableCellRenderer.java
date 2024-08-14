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

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import com.vis.core.view.D2.ui.PresenceCellRenderer;

public class TestJTableCellRenderer {

	public static void main(String[] args) {

		// サンプルデータとカラム名
		Object[][] data = { { "1", "John", "500", true }, { "2", "Jane", "750", false },
				{ "3", "Emily", "600", true } };
		String[] columnNames = { "ID", "Name", "Score", "Presence" };

		// JTableとモデルの作成
		DefaultTableModel model = new DefaultTableModel(data, columnNames);
		JTable table = new JTable(model);

		TableColumnModel tcm = table.getColumnModel();
		TableColumn presenceCol = tcm.getColumn(model.findColumn("Presence"));
		presenceCol.setCellRenderer(new PresenceCellRenderer(PresenceCellRenderer.DEFAULT));

		table.removeColumn(table.getColumnModel().getColumn(model.findColumn("ID")));
		
		// フレームの設定
		JFrame frame = new JFrame("JTable getSelectedRows Example");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(new JScrollPane(table));
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

	}

}
