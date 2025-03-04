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

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * TreeModel for DICOMTreeTable.
 * 
 * If you want something to do, you can handle by "TreeTableModelAdapter".
 * 
 * @author tatsunidas
 *
 */
public class DICOMTreeTableModel extends AbstractTreeTableModel/*tree model*/ {
	
	private static final long serialVersionUID = -4830975177975801077L;
	public static final String DatasetsCol = "Datasets"; 
	public static final String ArchivedCol = "Archived"; 

	/*
	 * LEVEL
	 *  pname
	 *  pid
	 *  studyDate
	 *  seriesDate
	 *  studyTime
	 *  acquisitiontime
	 *  studyDesc
	 *  seriesDesc
	 *  modality
	 *  sex
	 *  bod
	 *  age
	 *  institution
	 *  modelname
	 *  seriesNumber
	 *  acquisitionNumber
	 *  instanceNumber
	 *  numOfSeries
	 *  numOfInstances
	 */
	static final public String[] columnNames = { DatasetsCol, ArchivedCol, DICOMNode.PatientName, DICOMNode.PatientID, DICOMNode.StudyDate,
			DICOMNode.SeriesDate, DICOMNode.StudyTime, DICOMNode.AcquisitionTime, "StudyDesc", "SeriesDesc", DICOMNode.Modality, DICOMNode.Sex, DICOMNode.BirthDate,
			DICOMNode.Age, DICOMNode.Institution, DICOMNode.ModelName, "SeriesNo", "AcquisitionNo", "InstanceNo", "NumOfSeries",
			"NumOfInstances" };
	
	/**
	 * if TreeTableModel.class was set, it's column is handle editable(do something) column in TreeTableCellEditor, else ignored.
	 * see, this.isCellEditable()
	 * and also see JTreeTable.TreeTableCellEditor.isCellEditable
	 * 
	 * if this.isCellEditable == false, cannot run cell component on TreeTableCellEditor.
	 */
	static protected Class<?>[] columnTypes = { TreeTableModel.class/*tree icon*/, TreeTableModel.class/*Archived*/, String.class, String.class,
			String.class, String.class, String.class, String.class, String.class, String.class, String.class,
			String.class, String.class, String.class, String.class, String.class, String.class, String.class,
			String.class, String.class, String.class };

	public boolean isQR = false;

	public DICOMTreeTableModel(Object root) {
		/**
		 * set root at super class.
		 */
		super((DefaultMutableTreeNode)root);
	}
	
	@Override
	public Object getChild(Object node, int index) {
        return ((DICOMNode) node).getChildren().get(index);
    }
	
	@Override
	public int getChildCount(Object node) {
        return ((DICOMNode) node).getChildren().size();
    }
	
	@Override
	public int getColumnCount() {
		return columnNames.length;
    }
 
 
	@Override
	public String getColumnName(int column) {
		/*
		 * Future work-> localize...
		 * e.g., ApplicationContext.currentBundle.getString("MainScreen.patientIdColumn.text");
		 */
		return columnNames[column];
	}
	
    @Override
	public Class<?> getColumnClass(int column) {
    	return columnTypes[column];
    }
    
	@Override
	public Object getValueAt(Object node, int column) {
		if (node == null) {
			return null;
		}
		switch (column) {
		case 0:
			return ((DICOMNode) node).getLevel();//level (int)
		case 1://Archived
			return null;
		case 2:// pname
			return ((DICOMNode) node).getData(DICOMNode.PatientName);
		case 3:// pid
			return ((DICOMNode) node).getData(DICOMNode.PatientID);
		case 4:// studyDate
			return ((DICOMNode) node).getData(DICOMNode.StudyDate);
		case 5:// seriesDate
			return ((DICOMNode) node).getData(DICOMNode.SeriesDate);
		case 6:// studyTime
			return ((DICOMNode) node).getData(DICOMNode.StudyTime);
		case 7:// acquisitiontime
			return ((DICOMNode) node).getData(DICOMNode.AcquisitionTime);
		case 8:// studyDesc
			return ((DICOMNode) node).getData(DICOMNode.StudyDescription);
		case 9:// seriesDesc
			return ((DICOMNode) node).getData(DICOMNode.SeriesDescription);
		case 10:// modality
			return ((DICOMNode) node).getData(DICOMNode.Modality);
		case 11:// sex
			return ((DICOMNode) node).getData(DICOMNode.Sex);
		case 12:// birthDate
			return ((DICOMNode) node).getData(DICOMNode.BirthDate);
		case 13:// age
			return ((DICOMNode) node).getData(DICOMNode.Age);
		case 14:// institution
			return ((DICOMNode) node).getData(DICOMNode.Institution);
		case 15:// modelname
			return ((DICOMNode) node).getData(DICOMNode.ModelName);
		case 16:// seriesNumber
			return ((DICOMNode) node).getData(DICOMNode.SeriesNumber);
		case 17:// acquisitionNumber
			return ((DICOMNode) node).getData(DICOMNode.AcquisitionNumber);
		case 18:// instanceNumber
			return ((DICOMNode) node).getData(DICOMNode.InstanceNumber);
		case 19:// numOfSeries
			return ((DICOMNode) node).getData(DICOMNode.NumOfSeries);
		case 20:// numOfInstances
			return ((DICOMNode) node).getData(DICOMNode.NumOfInstances);
		default:
			break;
		}
		return null;
	}
	
	/**
	 * aValue will handle as String.
	 * if Date obj, throw exception. it should be convert to String before setData().
	 */
	@Override
	public void setValueAt(Object aValue, Object node, int column) {
		if (node == null) {
			return;
		}
		if(aValue instanceof java.util.Date || aValue instanceof java.sql.Date) {
			throw new IllegalArgumentException("Date obj should be convert String before setData.");
		}
		//numeric to string
		String v = String.valueOf(aValue);
		switch (column) {
		case 0:
			//do nothing
			break;
		case 1://Archived
			((DICOMNode) node).setData(DICOMNode.Archive, v);
			break;
		case 2:// pname
			((DICOMNode) node).setData(DICOMNode.PatientName, v);
			break;
		case 3:// pid
			((DICOMNode) node).setData(DICOMNode.PatientID, v);
			break;
		case 4:// studyDate
			((DICOMNode) node).setData(DICOMNode.StudyDate, v);
			break;
		case 5:// seriesDate
			((DICOMNode) node).setData(DICOMNode.SeriesDate, v);
			break;
		case 6:// studyTime
			((DICOMNode) node).setData(DICOMNode.StudyTime, v);
			break;
		case 7:// acquisitiontime
			((DICOMNode) node).setData(DICOMNode.AcquisitionTime,v);
			break;
		case 8:// studyDesc
			((DICOMNode) node).setData(DICOMNode.StudyDescription,v);
			break;
		case 9:// seriesDesc
			((DICOMNode) node).setData(DICOMNode.SeriesDescription,v);
			break;
		case 10:// modality
			((DICOMNode) node).setData(DICOMNode.Modality,v);
			break;
		case 11:// sex
			((DICOMNode) node).setData(DICOMNode.Sex,v);
			break;
		case 12:// birthDate
			((DICOMNode) node).setData(DICOMNode.BirthDate,v);
			break;
		case 13:// age
			((DICOMNode) node).setData(DICOMNode.Age,v);
			break;
		case 14:// institution
			((DICOMNode) node).setData(DICOMNode.Institution,v);
			break;
		case 15:// modelname
			((DICOMNode) node).setData(DICOMNode.ModelName,v);
			break;
		case 16:// seriesNumber
			((DICOMNode) node).setData(DICOMNode.SeriesNumber,v);
			break;
		case 17:// acquisitionNumber
			((DICOMNode) node).setData(DICOMNode.AcquisitionNumber,v);
			break;
		case 18:// instanceNumber
			((DICOMNode) node).setData(DICOMNode.InstanceNumber,v);
			break;
		case 19:// numOfSeries
			((DICOMNode) node).setData(DICOMNode.NumOfSeries,v);
			break;
		case 20:// numOfInstances
			((DICOMNode) node).setData(DICOMNode.NumOfInstances,v);
			break;
		default:
			break;
		}
	}
	
	public boolean isCellEditable(Object node, int column) {
		return getColumnClass(column) == TreeTableModel.class;
	}
	
	@Deprecated
	/**
	 * Use TreeTableModelAdapter.reload() instead.
	 */
	public void reload(Object root) {
		setRoot((DefaultMutableTreeNode)root);
		fireTreeStructureChanged(DICOMTreeTableModel.this, new Object[] {root}, null, null);
	}
}
