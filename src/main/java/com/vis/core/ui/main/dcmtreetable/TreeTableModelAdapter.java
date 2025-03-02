 /*
  *  Version: MPL 1.1/GPL 2.0/LGPL 2.1
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

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

/**
 * This is a wrapper class takes a TreeTableModel and implements the table model
 * interface. The implementation is trivial, with all of the event dispatching
 * support provided by the superclass: the AbstractTableModel.
 *
 *
 * @author Philip Milne
 * @author Scott Violet
 */
@SuppressWarnings("serial")
public class TreeTableModelAdapter extends AbstractTableModel {
	
	JTree tree;//TreeTableCellRenderer
	TreeTableModel treeTableModel;

	public TreeTableModelAdapter(TreeTableModel treeTableModel, JTree tree/*TreeTableCellRenderer*/) {
		this.tree = tree;
		this.treeTableModel = treeTableModel;

		tree.addTreeExpansionListener(new TreeExpansionListener() {
			// Don't use fireTableRowsInserted() here; the selection model
			// would get updated twice.
			public void treeExpanded(TreeExpansionEvent event) {
				fireTableDataChanged();
			}

			public void treeCollapsed(TreeExpansionEvent event) {
				fireTableDataChanged();
			}
		});

		// Install a TreeModelListener that can update the table when
		// tree changes. We use delayedFireTableDataChanged as we can
		// not be guaranteed the tree will have finished processing
		// the event before us.
		treeTableModel.addTreeModelListener(new TreeModelListener() {
			public void treeNodesChanged(TreeModelEvent e) {
				delayedFireTableDataChanged();
			}

			public void treeNodesInserted(TreeModelEvent e) {
				delayedFireTableDataChanged();
			}

			public void treeNodesRemoved(TreeModelEvent e) {
				delayedFireTableDataChanged();
			}

			public void treeStructureChanged(TreeModelEvent e) {
				delayedFireTableDataChanged();
			}
		});
	}

	// Wrappers, implementing TableModel interface.
	public int getColumnCount() {
		return treeTableModel.getColumnCount();
	}

	public String getColumnName(int column) {
		return treeTableModel.getColumnName(column);
	}

	public Class<?> getColumnClass(int column) {
		return treeTableModel.getColumnClass(column);
	}

	public int getRowCount() {
		return tree.getRowCount();
	}
	
	public void reload(Object root/*MUST be Root*/) {
		DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
		treeModel.setRoot((DefaultMutableTreeNode)root);
		((AbstractTreeTableModel)treeTableModel).fireTreeStructureChanged(this, new Object[] {root}, null, null);
		delayedFireTableDataChanged();
	}

	public Object nodeForRow(int row) {
		TreePath treePath = tree.getPathForRow(row);
		if(treePath != null) {
			return treePath.getLastPathComponent();
		}
		return null;
	}
	
	/**
	 * 
	 * @param node
	 * @return row index. if node not visible(close in tree), return -1.
	 */
	public int rowForNode(Object node) {
		DefaultMutableTreeNode n = (DefaultMutableTreeNode)node;
		TreePath pathToNode3 = new TreePath(n.getPath());
       return tree.getRowForPath(pathToNode3);
	}
	
	/**
	 * Once STUDY/SERIES level nodes are specified, any child nodes they have are also recursively deleted.
	 * @param row
	 */
	public void removeRow(int row) {
		TreePath path = tree.getPathForRow(row);
		if (path != null) {
			DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
			if (selectedNode != null) {
				((DefaultTreeModel) tree.getModel()).removeNodeFromParent(selectedNode);
			}
		}
		fireTableDataChanged();
	}

	public Object getValueAt(int row, int column) {
		return treeTableModel.getValueAt(nodeForRow(row), column);
	}

	public boolean isCellEditable(int row, int column) {
		return treeTableModel.isCellEditable(nodeForRow(row), column);
	}

	public void setValueAt(Object value, int row, int column) {
		//see, DICOMTreeModel setValueAt.
		treeTableModel.setValueAt(value, nodeForRow(row), column);
	}

	/**
	 * Invokes fireTableDataChanged after all the pending events have been
	 * processed. SwingUtilities.invokeLater is used to handle this.
	 */
	protected void delayedFireTableDataChanged() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				fireTableDataChanged();
			}
		});
	}
}
