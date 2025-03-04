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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import javax.swing.RowSorter;
import javax.swing.event.RowSorterEvent;
import javax.swing.event.RowSorterListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import com.vis.core.log.Log;
import com.vis.core.util.Utils;

/**
 * 
 * @author tatsunidas
 *
 */
public class TreeTableNodeSorter extends TableRowSorter<TableModel> implements RowSorterListener{
	
	DICOMTreeTable treeTable;
	DICOMTreeTableModel treeTableModel;//for tree
	TreeTableModelAdapter adapter;//for table
	
	// reject if colName is included in rejectlist
	// SHOULD keep same value to DICOMTreeTableModel Column Names.
	String[] rejectListStrings = new String[] { DICOMTreeTableModel.DatasetsCol, DICOMTreeTableModel.ArchivedCol, DICOMNode.SeriesDate, DICOMNode.AcquisitionTime,
			"SeriesDesc", "SeriesNo", "AcquisitionNo", "InstanceNo"};
	
	ArrayList<String> rejectFilter = new ArrayList<>(Arrays.asList(rejectListStrings));
	
	public TreeTableNodeSorter(DICOMTreeTable treeTable) {
		super((TreeTableModelAdapter) treeTable.getModel());
		this.adapter = (TreeTableModelAdapter) treeTable.getModel();
		this.treeTable = treeTable;
		this.treeTableModel = (DICOMTreeTableModel) treeTable.getTree().getModel();
		addRowSorterListener(this);
		
		for(int i=0; i<treeTable.getColumnCount();i++) {
			String colName = treeTableModel.getColumnName(i);
			if(rejectFilter.contains(colName)) {
				setSortable(i, false);
			}
		}
	}
	
	void sort(String columnName) {
		sort(treeTable, columnName);
	}
	
	private void sort(DICOMTreeTable treeTable, String columnName) {
		if (rejectFilter.contains(columnName)) {
			Log.logger.warning(getClass().getName()+" : Canceled TreeTableSorting. Selected col is listed in reject list");
			return;
		} else {
			Log.logger.fine("TreeTable sorting, " + "using " + columnName + " key.");
		}
		
		DICOMNode root = (DICOMNode) treeTable.getTree().getModel().getRoot();//keep this code for sort.
		DICOMNode sortedroot = (DICOMNode)root.clone();
		//get level
		DICOMNode chi = (DICOMNode) root.getChild(0);//getFirstChild(); DO NOT USE
		String nodeLevel = chi.getLevelString();
		
		//DICOMTreeTable does not handle with PATIENT level tree node (less or equals STUDY).
		if(nodeLevel.equals("PATIENT")) {
			Log.logger.fine("This treetable has PATIENT level tree nodes. Cannot sort.");
			return;
		}
		
		sortedroot = getSortedNodesByString(sortedroot, columnName);
		adapter.reload(sortedroot);
		/*
		 * memo
		 */
//		javax.swing.tree.DefaultTreeModel model = (javax.swing.tree.DefaultTreeModel) treeTable.getTree().getModel();
//		model.setRoot(sortedroot);
//		model.reload(sortedroot);
//		model.nodeChanged(sortedroot);// or should use .reload(root)
		treeTable.revalidate();
		treeTable.repaint();// needed
		root = null;
	}
	
	
	DICOMNode getSortedNodesByString(DICOMNode root, String columnName){
		//list specified col cell values 
		ArrayList<String> list = new ArrayList<String>();
		for(DICOMNode study:root.getChildren()) {
			String v = study.getData(columnName);
			if(columnName.equals(DICOMNode.BirthDate) && v == null) {
				v = "1900/01/01";
			}
			list.add(v);
		}
		//remove duplicated value to keep order.
		list = new ArrayList<String>(new LinkedHashSet<>(list));		
		
		if (list == null || list.size() <= 1) {
			return root;
		}
		
		//check current sort order and sort values.
		if(isSortedAlphabetically(list)) {
			Collections.sort(list, Collections.reverseOrder(String.CASE_INSENSITIVE_ORDER));
		}else {
			Collections.sort(list);
		}
		
		//back to null
		if (columnName.equals(DICOMNode.BirthDate)) {
			String[] values = new String[list.size()];
			int count = 0;
			for(String v : list) {
				if(v != null && v.equals("1900/01/01")) {
					v = null;
				}
				values[count++] = v;
			}
			list = new ArrayList<>(Arrays.asList(values));
		}
		
		//sort nodes and re-construct root
		ArrayList<DICOMNode> sortedNodes = new ArrayList<DICOMNode>();
		for(int i=0;i<list.size();i++) {
			for(DICOMNode study:root.getChildren()) {
				Object v1 = list.get(i);
				Object v2 = study.getData(columnName);
				if(v1 == null && v2 == null) {
					sortedNodes.add(study);
					continue;
				}else if(v1 == null && v2 != null) {
					continue;
				}else if(v1 != null && v2 == null) {
					continue;
				}else {
					if(list.get(i).equals(study.getData(columnName))){
						sortedNodes.add(study);
					}
				}
			}
		}
		//validate
		if(sortedNodes.size() != root.getChildCount()) {
			Log.logger.severe("TreeTableSort task was failed. return AS-IS.");
			System.out.println("Invalid list size, return:DICOMTreeTableNodeSorter");
			System.out.println("original tree has childrens :"+root.getChildCount());
			System.out.println("sorted nodes count :"+sortedNodes.size());
			//throw something error or exceptions ??
			return root;//as-is
		}
		return new DICOMNode(true, sortedNodes);
	}
	
	public boolean isSortedAlphabetically(ArrayList<String> list) {
		if (list.size() < 1) {
			return true;
		}
		String previous = ""; // empty string: guaranteed to be less than or equal to any other
		for (String current : list) {
			if (current == null) {
				return false;//no change
			}
			if (current.compareTo(previous) < 0) {
				return false;
			}
			previous = current;
		}
		return true;
	}
	
	public boolean isAcendingOrder(ArrayList<Integer> list) {
		int previous = Integer.MIN_VALUE; 
		for (final Integer current: list) {
		    if (current.compareTo(previous) < 0)
		        return false;
		    previous = current;
		}
		return true;
	}

	@Override
	public void sorterChanged(RowSorterEvent rse) {
		if(Utils.isDebug) {
			Log.logger.info("SortType: " + rse.getType());
		}
		if (rse.getType() == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
			java.util.List<? extends RowSorter.SortKey> keys = rse.getSource().getSortKeys();
			SortKey key = keys.get(0);
			int colpos = key.getColumn();
			String colName = adapter.getColumnName(colpos);// this is ok when changed column order.
			System.out.println("Column - " + colName + " is selected to table sort");
			if(rejectFilter.contains(colName)) {
				return;
			}
			sort(colName);
		}
	}
}
