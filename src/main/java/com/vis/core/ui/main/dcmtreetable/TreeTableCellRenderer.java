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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 * renderer for tree in treetable.
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class TreeTableCellRenderer extends JTree implements TableCellRenderer,TreeSelectionListener {

	protected int visibleRow;
	private DICOMTreeTable treeTable;

	public TreeTableCellRenderer(DICOMTreeTable treeTable, TreeTableModel model) {
		super(model);
		this.treeTable = treeTable;
		addTreeSelectionListener(this);
		setCellRenderer(new TreeIconCellRenderer());
	}
	
	@Override
	public void updateUI() {
		super.updateUI();
		TreeCellRenderer tcr = getCellRenderer();
		if (tcr instanceof DefaultTreeCellRenderer) {
			DefaultTreeCellRenderer dtcr = ((DefaultTreeCellRenderer) tcr);
			dtcr.setTextSelectionColor(UIManager.getColor("Table.selectionForeground"));
			dtcr.setBackgroundSelectionColor(UIManager.getColor("Table.selectionBackground"));
		}
	}

	/**
	 * SHOULD/MUST keep same height both tree and table
	 */
	@Override
	public void setRowHeight(int rowHeight) {
		if (rowHeight > 0) {
			super.setRowHeight(rowHeight);
			if (treeTable != null && treeTable.getRowHeight() != rowHeight) {
				treeTable.setRowHeight(getRowHeight());
			}
		}
	}

	/**
	 * SHOULD/MUST keep same height both tree and table
	 */
	@Override
	public void setBounds(int x, int y, int w, int h) {
		super.setBounds(x, 0, w, treeTable.getHeight());
	}

	/**
	 * To keep include folder/file
	 */
	@Override
	public void paint(Graphics g) {
		g.translate(0, -visibleRow * getRowHeight());
		super.paint(g);
	}

	/**
	 * set suitable background color (tree background color set to same as table)
	 */
	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		if (isSelected) {
			setBackground(table.getSelectionBackground());
		} else {
			setBackground(table.getBackground());
		}
		visibleRow = row;		
		return this;
	}

	@Override
	public void valueChanged(TreeSelectionEvent tse) {
		
		//Example, if tree obj needed
		/*************************************/
//		JTree treeSource = (JTree) tse.getSource();
		//if all selected nodes needed,
//		TreePath[] path = getSelectionPaths();
//		if (path != null) {
//			for(TreePath p:path) {
//				DICOMNode node = (DICOMNode) p.getLastPathComponent();
//				if (node == null) {
//					return;
//				}
//			}
//		}
		/*************************************/
		
		//TODO 20230829
		/* importing state render */
//		if(ApplicationContext.importing) {
//			ArrayList<Integer> expanded = treeTable.getExpandedRowsPos();
//			/* set position to ImportingStateCellEditor */
//			for (int i = 0; i < expanded.size(); i++) {
//				DICOMNode node = treeTable.nodeForRow(i);
//				String suid = node.getData(DICOMNode.StudyInstanceUID);
//				if(node.getLevel() == DICOMNode.STUDY) {
//					int importingRow = i;
//					int archivedColPos = treeTable.getArchivedColumnPosition();
//					if(!treeTable.isQR) {
//						LocalDBStateCellRendererableEditor importingStateCell = treeTable
//								.getStateCellEditorAtArchiveColumn(archivedColPos);
//						SwingUtilities.invokeLater(()->{
//							importingStateCell.setCellStateLocationInCurrentTableView(suid, importingRow, archivedColPos);
//							/*
//							 * 以下、動作の反応が遅いのでむしろマイナスかも。使わないほうが良いかも。
//							 */
////							ApplicationContext.getInstance().getMainScreen().loadLocalStudies();//shoud use it, but not test yet.
////							treeTable.revalidate();
//						});
//					}else {
//						QRStateCellEditor importingStateCell = treeTable
//								.getQRStateCellEditorAtArchiveColumn(archivedColPos);
//						SwingUtilities.invokeLater(()->{
//							importingStateCell.setCellStateLocationInCurrentTableView(suid, importingRow, archivedColPos);
//							/*
//							 * 以下、動作の反応が遅いのでむしろマイナスかも。使わないほうが良いかも。
//							 */
////							ApplicationContext.getInstance().getMainScreen().loadLocalStudies();//shoud use it, but not test yet.
////							treeTable.revalidate();
//						});
//					}
//				}
//			}
//		}
	}
}
