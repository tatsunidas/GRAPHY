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
import javax.swing.tree.DefaultTreeModel;

import com.vis.core.log.Log;
import com.vis.core.util.Utils;

public class TreeTableNodeSorter extends TableRowSorter<TableModel> implements RowSorterListener{
	
	DICOMTreeTable treeTable;
	DICOMTreeTableModel treeTableModel;//for tree
	TreeTableModelAdapter adapter;//for table
	
	// reject if colName is included in rejectlist
	// SHOULD keep same value to TreeTable Column Names, check also DICOMNode keys(but not same as this.).
	String[] rejectListStrings = new String[] { "Datasets", "Archived", "SeriesDate", "AquisitionTime",
			"SeriesDesc", "SeriesNo", "AcquisitionNo", "InstanceNo", "SeriesInstanceUID" };
	/**/
//	columnNames
//	{ "Dataset", "Archived", "PatientName", "PatientID", "StudyDate",
//			"SeriesDate", "StudyTime", "AquisitionTime", "StudyDesc", "SeriesDesc", "Modality", "Sex", "BirthDate",
//			"Age", "Institution", "ModelName", "SeriesNo", "AcquisitionNo", "InstanceNo", "NumOfSeries",
//			"NumOfInstances" };
	/**/
	ArrayList<String> rejectFilter = new ArrayList<>(Arrays.asList(rejectListStrings));
	
	public TreeTableNodeSorter() {}//for table sort
	
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
	
	/* for treetable sort */
	void sort(	DICOMTreeTable treeTable, String columnName) {
		/*
		 * 実際使うときには、たくさんのソート項目は不要。
		 * Studyレベルの項目をソート可能にする
		 * 
		 * //Keys
		 * Dataset：return(Tree selves)
		 * "Retrieved":return
		 * "PatientName"(root>patient pattern || root>study pattern)
		 * "PatientID"(root>patient pattern || root>study pattern)
		 * "StudyDate"(root>study pattern)
		 * "SeriesDate":return
		 * "StudyTime":return（スタディDATEと間違えそうなので）
		 * "AquisitionTime":return
		 * "StudyDescription":(root>study pattern)
		 * "SeriesDescription":return
		 * "Modality"(root>study pattern)
		 * "Sex"(root>patient pattern || root>study pattern)
		 * "BirthDate"(root>patient pattern || root>study pattern)
		 * "Age"(root>patient pattern || root>study pattern)
		 * "Institution"(root>study pattern)
		 * "ModelName"(root>study pattern)
		 * "SeriesNumber":return
		 * "AcquisitionNumber":return
		 * "InstanceNumber":return
		 * "AccessionNumber":(root>study pattern)
		 * "NumOfSeries":(root>study pattern)
		 * "NumOfInstances":(root>study pattern)
		 * "StudyInstanceUID"(root>study pattern)
		 * "SeriesInstanceUID":return
		 * "SOPInstanceUID":(root>study pattern)
		 */
		
		if (rejectFilter.contains(columnName)) {
			System.out.println("Canceled TreeTableSorting. selecteded col was found in reject list");
			return;
		} else {
			System.out.println("TreeTable sorting, " + "using " + columnName + " key.");
		}
		
		DICOMNode root = (DICOMNode) treeTable.getTree().getModel().getRoot();//keep this code for sort.
		DICOMNode sortedroot = (DICOMNode)root.clone();
		//get level
		DICOMNode chi = (DICOMNode) root.getChild(0);//getFirstChild(); DO NOT USE
		String nodeLevel = chi.getLevelString();
		
		//DICOMTreeTableはPATIENTレベルのツリー表示には対応しない。STUDYレベルのみ対応する
		if(nodeLevel.equals("PATIENT")) {
			System.out.println("node level is "+nodeLevel);
			System.out.println("this treetable has patient level tree. cannot sort.return");
			return;
		}
		
		/*
		 * 昇順降順の調査をしてから、
		 * その反対にソートする
		 * すべて文字列として扱う。
		 */
		sortedroot = getSortedNodesByString(sortedroot, columnName);
		DefaultTreeModel model = (DefaultTreeModel) treeTable.getTree().getModel();
		model.setRoot(sortedroot);
		model.reload(sortedroot);
		model.nodeChanged(sortedroot);// or should use .reload(root)
		treeTable.getTree().revalidate();
		treeTable.repaint();// needed
		root = null;
	}
	
	/* deprecated */
	void sort(	String columnName) {
		/*
		 * 実際使うときには、たくさんのソート項目は不要。
		 * Studyレベルの項目をソート可能にする
		 * 
		 * //Keys
		 * Dataset：return(Tree selves)
		 * "Retrieved":return
		 * "PatientName"(root>patient pattern || root>study pattern)
		 * "PatientID"(root>patient pattern || root>study pattern)
		 * "StudyDate"(root>study pattern)
		 * "SeriesDate":return
		 * "StudyTime":return（スタディDATEと間違えそうなので）
		 * "AquisitionTime":return
		 * "StudyDescription":(root>study pattern)
		 * "SeriesDescription":return
		 * "Modality"(root>study pattern)
		 * "Sex"(root>patient pattern || root>study pattern)
		 * "BirthDate"(root>patient pattern || root>study pattern)
		 * "Age"(root>patient pattern || root>study pattern)
		 * "Institution"(root>study pattern)
		 * "ModelName"(root>study pattern)
		 * "SeriesNumber":return
		 * "AcquisitionNumber":return
		 * "InstanceNumber":return
		 * "AccessionNumber":(root>study pattern)
		 * "NumOfSeries":(root>study pattern)
		 * "NumOfInstances":(root>study pattern)
		 * "StudyInstanceUID"(root>study pattern)
		 * "SeriesInstanceUID":return
		 * "SOPInstanceUID":(root>study pattern)
		 */
		
		if (rejectFilter.contains(columnName)) {
			System.out.println("Canceled TreeTableSorting. selecteded col was found in reject list");
			return;
		} else {
			System.out.println("TreeTable sorting, " + "using " + columnName + " key.");
		}
		
		DICOMNode root = (DICOMNode) treeTableModel.getRoot();
		DICOMNode sortedroot = (DICOMNode)root.clone();
		//get level
		DICOMNode chi = (DICOMNode) root.getChild(0);//getFirstChild(); DO NOT USE
		String nodeLevel = chi.getLevelString();
		
		//DICOMTreeTableはPATIENTレベルのツリー表示には対応しない。STUDYレベルのみ対応する
		if(nodeLevel.equals("PATIENT")) {
			System.out.println("node level is "+nodeLevel);
			System.out.println("this treetable has patient level tree. cannot sort.return");
			return;
		}
		
		/*
		 * 昇順降順の調査をしてから、
		 * その反対にソートする
		 * すべて文字列として扱う。
		 */
		sortedroot = getSortedNodesByString(sortedroot, columnName);
		DefaultTreeModel model = (DefaultTreeModel) treeTable.getTree().getModel();
		model.setRoot(sortedroot);
		model.reload(sortedroot);
		model.nodeChanged(sortedroot);// or should use .reload(root)
		treeTable.getTree().revalidate();
		treeTable.repaint();// needed
		root = null;
	}
	
	DICOMNode getSortedNodesByString(DICOMNode root, String columnName){
		//指定された列の値リストを取得
		ArrayList<String> list = new ArrayList<String>();
		for(DICOMNode study:root.getChildren()) {
			list.add(study.getData(columnName));
		}
		//remove duplicated value keeping order.
		list = new ArrayList<String>(new LinkedHashSet<>(list));		
		//check current sort order
		if (list == null || list.size() <= 1) {
			return root;
		}
		if(isSortedAlphabetically(list)) {
			Collections.sort(list, Collections.reverseOrder(String.CASE_INSENSITIVE_ORDER));
		}else {
			Collections.sort(list);
		}
		//sort and re-construct nodes array
		ArrayList<DICOMNode> sortedNodes = new ArrayList<DICOMNode>();
		for(int i=0;i<list.size();i++) {
			for(DICOMNode study:root.getChildren()) {
				if(list.get(i).equals(study.getData(columnName))){
					System.out.println(list.get(i));
					sortedNodes.add(study);
				}
			}
		}
		//validate
		if(sortedNodes.size() != root.getChildCount()) {
			System.out.println("Invalid list size, return:DICOMTreeTableNodeSorter");
			System.out.println("tree has childrens :"+root.getChildCount());
			System.out.println(sortedNodes.size());
			//throw something error or exceptions ??
			return root;//as-is
		}
//		root.removeAllChildren();//java.lang.ArrayIndexOutOfBoundsException: node has no children
//		int numOfChi = root.getChildCount();//get first.to avoid changes num of child.
//		for(int i=0;i<numOfChi;i++) {
//			root.remove(0);
//		}
//		for(int i=0;i<sortedNodes.size();i++) {
//			root.addChild(sortedNodes.get(i));//DO NOT USE; add or insert
//		}
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
		// TODO Auto-generated method stub
		if(Utils.isDebug) {
			Log.logger.info("SortType: " + rse.getType());
		}
		if (rse.getType() == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
			java.util.List<? extends RowSorter.SortKey> keys = rse.getSource().getSortKeys();
			/* 数回押すと、過去の分まで実行される */
//			for (SortKey key : keys) {
//				int colpos = key.getColumn();
//				String colName = adapter.getColumnName(colpos);//columnの入れ替わりにも対応済み
//				System.out.println("Column - " + colName + " is sorted");
//				sort(colName);
//			}
			SortKey key = keys.get(0);
			int colpos = key.getColumn();
			String colName = adapter.getColumnName(colpos);// columnの入れ替わりにも対応済み
			System.out.println("Column - " + colName + " is selected to table sort");
			if(rejectFilter.contains(colName)) {
				return;
			}
			sort(colName);
		}
	}
}
