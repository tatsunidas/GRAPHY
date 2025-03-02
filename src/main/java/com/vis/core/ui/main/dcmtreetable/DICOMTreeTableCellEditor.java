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

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

import com.vis.core.log.Log;

/**
 * DICOMTreeTable default cell editor
 * @author tatsunidas
 *
 */
public class DICOMTreeTableCellEditor extends AbstractCellEditor implements TableCellEditor {
	
	final DICOMTreeTable treeTable;
	
	public DICOMTreeTableCellEditor(DICOMTreeTable treeTable) {
		this.treeTable = treeTable;
	}
	
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int r, int c) {
		return treeTable.getTree();
	}

	/**
	 * Overridden to return false, and if the event is a mouse event it is forwarded
	 * to the tree.
	 * <p>
	 * The behavior for this is debatable, and should really be offered as a
	 * property. By returning false, all keyboard actions are implemented in terms
	 * of the table. By returning true, the tree would get a chance to do something
	 * with the keyboard events. For the most part this is ok. But for certain keys,
	 * such as left/right, the tree will expand/collapse where as the table focus
	 * should really move to a different column. Page up/down should also be
	 * implemented in terms of the table. By returning false this also has the added
	 * benefit that clicking outside of the bounds of the tree node, but still in
	 * the tree column will select the row, whereas if this returned true that
	 * wouldn't be the case.
	 * <p>
	 * By returning false we are also enforcing the policy that the tree will never
	 * be editable (at least by a key sequence).
	 */
	public boolean isCellEditable(EventObject e) {
		/*
		 * if double clicked, mouseEvent was detected.
		 */
		if (e instanceof MouseEvent) {
			MouseEvent me = (MouseEvent) e;
			//add DICOMTreeTable specifications
			int colPos = treeTable.columnAtPoint(me.getPoint());
			int currentDSPos = treeTable.getColumnPosition(DICOMTreeTableModel.DatasetsCol);
			if (colPos == currentDSPos) {
				Log.logger.fine("Datasets column pressed");
				@SuppressWarnings("deprecation")
				MouseEvent newME = new MouseEvent(treeTable.getTree(), me.getID(), me.getWhen(),
						me.getModifiers()/* When using getModifiersEx() directly, do not action properly. */,
						me.getX() - treeTable.getCellRect(0, currentDSPos, true).x, me.getY(), me.getClickCount(),
						me.isPopupTrigger());
				treeTable.getTree().dispatchEvent(newME);
				//DO NOT RETURN BOOL
			}
			/*
			 * Archived column
			 */
			int currentArcPos = treeTable.getColumnPosition(DICOMTreeTableModel.ArchivedCol);
			if(colPos == currentArcPos) {
				//only allow single clicked
				if(me.getClickCount() == 1) {
					Log.logger.fine("Archive column pressed");
					return true;
				}
			}
		}
		return false;
	}

}
