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
package com.vis.core.view.D2.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.DefaultTableModel;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEdit;

public class UndoableTableModel extends DefaultTableModel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private List<UndoableEdit> editHistory = new ArrayList<>();
	final public static int HISTORY_SIZE = 3;
	int undoCount;
	boolean updateCount = true;
	
	public UndoableTableModel(Object[] columnNames, int rowCount) {
		super(columnNames, rowCount);
//		this.undoManager = undoManager;
	}

	@Override
	public void setValueAt(Object aValue, int row, int column) {
		// Create an UndoableEdit for the change
		@SuppressWarnings("serial")
		UndoableEdit edit = new AbstractUndoableEdit() {

			private final Object oldValue = new String(getValueAt(row, column)!=null?(String)getValueAt(row, column):"");
			private final Object newValue = new String(aValue!=null?(String)aValue:"");

			@Override
			public void undo() {
				super.undo();
				updateCount = false;
				setValueAt(oldValue, row, column);
				updateCount = true;
			}

			@Override
			public void redo() {
				super.redo();
				updateCount = false;
				setValueAt(newValue, row, column);
				updateCount = true;
			}
		};
		editHistory.add(edit);
		if (editHistory.size() > HISTORY_SIZE) {
			editHistory.remove(0);
		}
		super.setValueAt(aValue, row, column);
		updateUndoCount(updateCount);
	}

	public List<UndoableEdit> getEditHistory() {
		return editHistory;
	}
	
	public UndoableEdit getCurrentIndexEdit(boolean undoAction) {
		if(undoCount < 0) {
			undoCount = 0;
		}else if(undoCount >= getEditHistory().size()){
			undoCount = getEditHistory().size()-1;
		}
		UndoableEdit e = getEditHistory().get(undoCount);
		if(undoAction) {
			undoCount--;
		}else {
			undoCount++;
		}
		return e;
	}
	
	void updateUndoCount(boolean update) {
		if(update) {
			undoCount = editHistory.size();
		}
	}
}
