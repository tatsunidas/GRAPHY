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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import com.vis.core.log.Log;
import com.vis.db.DatabaseHandler;

/**
 * Show all studies in patient.
 * @author tatsunidas
 *
 */
public class StudyListTable extends JTable {

	/**
	 * UID-studydate-studydesc-modalities-numOfSeries
	 */
	private static final long serialVersionUID = 1L;
	Logger log = Log.logger;
	private final String[] header = new String[] { "StudyInstanceUID", "Presence", "ModalitiesInStudy", "StudyDate",
			"StudyDescription", "NumOfSeries" };
	private DefaultTableModel model;
	private DatabaseHandler db = DatabaseHandler.getInstance();
	private JScrollPane pane = null;
	/*
	 * Thread safe
	 */
	List<String> onStageStudyList = Collections.synchronizedList(new ArrayList<>());
	private String currentStudyUID; //selected row on table
	private SeriesListTable seriesListTbl;
	private final PatientInfoCake cake;

	public StudyListTable(PatientInfoCake cake) {
		this.cake = cake;
		model = new DefaultTableModel(header, 0) {
			private static final long serialVersionUID = 1L;
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
       setModel(model);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				ListSelectionModel lsm = (ListSelectionModel) e.getSource();
				if (lsm.isSelectionEmpty()) {
					return;
				}
				StringBuilder output = new StringBuilder();
				int firstIndex = e.getFirstIndex();
				int lastIndex = e.getLastIndex();
				boolean isAdjusting = e.getValueIsAdjusting();
				if(!isAdjusting) {
					output.append("StudyListTableEvent for indexes " + firstIndex + " - " + lastIndex + "; isAdjusting is " + isAdjusting
						+ "; selected indexes:");
					// Find out which indexes are selected.
					int minIndex = lsm.getMinSelectionIndex();
					int maxIndex = lsm.getMaxSelectionIndex();
					for (int i = minIndex; i <= maxIndex; i++) {
						if (lsm.isSelectedIndex(i)) {
							currentStudyUID = getStudyInstanceUIDAtSelectedRow(i);
							output.append(" " + i);
							output.append(" studyUID:"+currentStudyUID);
						}
					}
					log.fine(output.toString());
					if(seriesListTbl != null) {
						seriesListTbl.initTable(currentStudyUID);
					}
				}
			}
		});
		initTable();
		pane = new JScrollPane(this);
	}

	public void setRelatedSeriesListTable(SeriesListTable tbl) {
		seriesListTbl = tbl;
	}
	
	public JScrollPane getAsScrollPane() {
		return pane;
	}

	private void initTable() {
		String patID = cake.getPatientInfo("PatientID");
		ArrayList<String> studies = db.getStudyUidList(patID);
		Object[][] studyInfoSets = new Object[studies.size()][];
		for (int i = 0; i < studies.size(); i++) {
			String studyUID = studies.get(i);
			HashMap<String, String> studyInfo = db.getStudyInfo(patID, studyUID);
			List<String> modalities = db.getModalitiesInStudyRealatedAllSeries(patID, studyUID);
			String m = "";
			for (String mo : modalities) {
				if (mo.equals("")) {
					continue;
				}
				m += mo + ",";
			}
			m = m.substring(0, m.length()-1);//delete last comma
			Object[] row = new Object[header.length];
			row[0] = studyUID;
			if (onStageStudyList.contains(studies.get(i))) {
				row[1] = true;
			} else {
				row[1] = false;
			}
			row[2] = m;// "Modalities"
			row[3] = studyInfo.get("StudyDate");// "StudyDate"
			row[4] = studyInfo.get("StudyDescription");// "StudyDescription"
			row[5] = studyInfo.get("NumOfSeriesInStudy");// "NumOfSeriesInStudy"
			studyInfoSets[i] = row;
		}
		constructTableView(studyInfoSets);
	}

	/**
	 * The model must have a UID.
	 * Delete columns from the table after reflecting them in the table.
	 * 
	 * table.removeColumn(table.getColumnModel().getColumn(0));
	 * 
	 * When accessing the UID later, access it through the model. 
	 * Note that it cannot be obtained from a table.
	 * 
	 * table.getModel().getValueAt(table.getSelectedRow(),0);
	 * 
	 * UID-studydate-studydesc-modalities-numOfSeries
	 */
	private void constructTableView(Object[][] data) {
		model.setDataVector(data, header);
		model.fireTableDataChanged();
		TableColumnModel tcm = getColumnModel();
		TableColumn presenceCol = tcm.getColumn(model.findColumn(header[1]));
		presenceCol.setCellRenderer(new PresenceCellRenderer(PresenceCellRenderer.DEFAULT));
		removeColumn(getColumnModel().getColumn(model.findColumn(header[0])));// remove UID column, but remain data in model.
	}

	private String getStudyInstanceUIDAtSelectedRow(int row) {
		String studyUID = null;
		int ind = convertRowIndexToModel(row);
		studyUID = (String) model.getValueAt(ind, 0);// col 0 = UID
//		studyUID = (String)getValueAt(ind, 0);//DO NOT USE
		return studyUID;
	}
	
	String getSlectedStudyUID() {
		if(getSelectedRow() != -1) {
			return getStudyInstanceUIDAtSelectedRow(getSelectedRow());
		}else {
			return getStudyInstanceUIDAtSelectedRow(0);
		}
	}

	/**
	 * Set showing study
	 * @param studyUID
	 */
	public void enterTheStageStudyUID(String studyUID, boolean initTable) {
		if (studyUID == null) {
			return;
		}
		if (!onStageStudyList.contains(studyUID)) {
			onStageStudyList.add(studyUID);
		}
		if(initTable) {
			initTable();
		}
	}

	public void leaveTheStageStudyUID(String studyUID) {
		onStageStudyList.remove(studyUID);
		initTable();
	}

	public void cleanUpdatePresenceOnStageStudy(List<String> newStudyUIDs/*showing StudyUID*/) {
		onStageStudyList.clear();
		for(String uid : newStudyUIDs) {
			enterTheStageStudyUID(uid, false);
		}
		initTable();
	}
}
