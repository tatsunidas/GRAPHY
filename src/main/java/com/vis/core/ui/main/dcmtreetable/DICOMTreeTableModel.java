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

/**
 * 
 * @author tatsunidas
 *
 */
public class DICOMTreeTableModel extends AbstractTreeTableModel {

	private static final long serialVersionUID = 1L;

	/*
	 * see, DICOMNode class
	 * 
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
	//include "Archived" for "Retrieve" btn action
	static protected String[] columnNames = { "Datasets", "Archived", "PatientName", "PatientID", "StudyDate",
			"SeriesDate", "StudyTime", "AquisitionTime", "StudyDesc", "SeriesDesc", "Modality", "Sex", "BirthDate",
			"Age", "Institution", "ModelName", "SeriesNo", "AcquisitionNo", "InstanceNo", "NumOfSeries",
			"NumOfInstances" };
	
	/* columnTypeでテーブル列に入るオブジェクトを管理する。一列目はTreeTableModel */
	static protected Class<?>[] columnTypes = { TreeTableModel.class, String.class, String.class, String.class,
			String.class, String.class, String.class, String.class, String.class, String.class, String.class,
			String.class, String.class, String.class, String.class, String.class, String.class, String.class,
			String.class, String.class, String.class };

	public boolean isQR = false;

	public DICOMTreeTableModel(Object root) {
		super(root);
	}
	
	@Override
	public Object getChild(Object parent, int index) {
        return ((DICOMNode) parent).getChildren().get(index);
    }
	
	@Override
	public int getChildCount(Object parent) {
        return ((DICOMNode) parent).getChildren().size();
    }
	
	@Override
	public int getColumnCount() {
		return columnNames.length;
    }
 
 
	@Override
	public String getColumnName(int column) {
		/*
		 * Future work-> localize...
		 */
//		switch (column) {
//		case 2:
//			return ApplicationContext.currentBundle.getString("MainScreen.patientIdColumn.text");
//		case 3:
//			return ApplicationContext.currentBundle.getString("MainScreen.patientNameColumn.text");
//		case 4:
//			return ApplicationContext.currentBundle.getString("MainScreen.dobColumn.text");
//		case 5:
//			return ApplicationContext.currentBundle.getString("MainScreen.accessionNoColumn.text");
//		case 6:
//			return ApplicationContext.currentBundle.getString("MainScreen.studyDateColumn.text");
//		case 7:
//			return ApplicationContext.currentBundle.getString("MainScreen.studyDescColumn.text");
//		case 8:
//			return ApplicationContext.currentBundle.getString("MainScreen.modalityColumn.text");
//		case 9:
//			return ApplicationContext.currentBundle.getString("MainScreen.imagesColumn.text");
//		}
		return columnNames[column];
	}
    
    @Override
	public Class<?> getColumnClass(int column) {
    	return columnTypes[column];
    }
    
    @Override
	public boolean isCellEditable(Object node, int column) {
//		if (node instanceof StudyNode) {
//			switch (((StudyNode) node).isRoot()) {
//			case 1: // Important to activate tree expand listener
//				if (column == 0) {
//					return true;
//				}
//				break;
//			case 0:
//				if (column != 0 && column != -1 && column != 9) {
//					return true;
//				}
//				break;
//			}
//		}
//		return false;
		return true; // Important to activate TreeExpandListener
	}
    
	@Override
	public Object getValueAt(Object node, int column) {
		if (node == null) {
			return node;
		}
		switch (column) {
		case 0:
			return ((DICOMNode) node).getLevelString();//level string
		case 1:// retrieved//Archived
			return null;//((DICOMNode) node).getData(DICOMNode.Archived);
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
	
	/* TODO DB updation  */
	public void setValueAt(String colName, Object aValue, Object node) {
		
		switch (colName) {
		case DICOMNode.PatientName:
			((DICOMNode) node).setData(DICOMNode.PatientID, (String)aValue);
			break;
		case DICOMNode.PatientID:
			((DICOMNode) node).setData(DICOMNode.PatientName, (String)aValue);
			break;
//		case 4:
//			((DICOMNode) node).setDob(String.valueOf(aValue));
//			break;
//		case 5:
//			((DICOMNode) node).setAccessionNo(String.valueOf(aValue));
//			break;
//		case 6:
//			((DICOMNode) node).setStudyDate(String.valueOf(aValue));
//			break;
//		case 7:
//			((DICOMNode) node).setStudyDescription(String.valueOf(aValue));
//			break;
//		case 8:
//			((DICOMNode) node).setModalitiesInStudy(String.valueOf(aValue));
//			break;
//		case 9:
//			((DICOMNode) node).setStudyReleatedInstances(String.valueOf(aValue));
//			break;
		}
		//create dataset copy ??
		//TODO UPDATE DB ??
	}
}
