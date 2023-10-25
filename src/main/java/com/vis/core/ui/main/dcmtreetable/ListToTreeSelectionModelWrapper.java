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

import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;

/**
 * ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel to listen
 * for changes in the ListSelectionModel it maintains. Once a change in the
 * ListSelectionModel happens, the paths are updated in the
 * DefaultTreeSelectionModel.
 */
class ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4654236451436603510L;
	
	/** Set to true when we are updating the ListSelectionModel. */
	protected boolean updatingListSelectionModel;
	TreeTableCellRenderer tree;

	public ListToTreeSelectionModelWrapper(TreeTableCellRenderer tree) {
		super();
		this.tree = tree;
		getListSelectionModel().addListSelectionListener(createListSelectionListener());
	}

	/**
	 * Returns the list selection model. ListToTreeSelectionModelWrapper listens for
	 * changes to this model and updates the selected paths accordingly.
	 */
	ListSelectionModel getListSelectionModel() {
		return listSelectionModel;
	}

	/**
	 * This is overridden to set <code>updatingListSelectionModel</code> and message
	 * super. This is the only place DefaultTreeSelectionModel alters the
	 * ListSelectionModel.
	 */
	public void resetRowSelection() {
		if (!updatingListSelectionModel) {
			updatingListSelectionModel = true;
			try {
				super.resetRowSelection();
			} finally {
				updatingListSelectionModel = false;
			}
		}
		// Notice how we don't message super if
		// updatingListSelectionModel is true. If
		// updatingListSelectionModel is true, it implies the
		// ListSelectionModel has already been updated and the
		// paths are the only thing that needs to be updated.
	}

	/**
	 * Creates and returns an instance of ListSelectionHandler.
	 */
	protected ListSelectionListener createListSelectionListener() {
		return new ListSelectionHandler();
	}

	/**
	 * If <code>updatingListSelectionModel</code> is false, this will reset the
	 * selected paths from the selected rows in the list selection model.
	 */
	protected void updateSelectedPathsFromSelectedRows() {
		if (!updatingListSelectionModel) {
			updatingListSelectionModel = true;
			try {
				// This is way expensive, ListSelectionModel needs an
				// enumerator for iterating.
				int min = listSelectionModel.getMinSelectionIndex();
				int max = listSelectionModel.getMaxSelectionIndex();

				clearSelection();
				if (min != -1 && max != -1) {
					for (int counter = min; counter <= max; counter++) {
						if (listSelectionModel.isSelectedIndex(counter)) {
							TreePath selPath = tree.getPathForRow(counter);

							if (selPath != null) {
								addSelectionPath(selPath);
							}
						}
					}
				}
			} finally {
				updatingListSelectionModel = false;
			}
		}
	}

	/**
	 * Class responsible for calling updateSelectedPathsFromSelectedRows when the
	 * selection of the list changse.
	 */
	class ListSelectionHandler implements ListSelectionListener {
		public void valueChanged(ListSelectionEvent e) {
			updateSelectedPathsFromSelectedRows();
		}
	}
}