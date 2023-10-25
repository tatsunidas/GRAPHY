package com.vis.core.view.D2.ui;

import java.util.ArrayList;
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

public class StudyListTable extends JTable {

	/**
	 * UID-studydate-studydesc-modalities-numOfSeries
	 */
	private static final long serialVersionUID = 1L;
	Logger log = Log.logger;
	private static String[] header = new String[] { "StudyInstanceUID", "Presence", "ModalitiesInStudy", "StudyDate",
			"StudyDescription", "NumOfSeries" };
	private DefaultTableModel model;
	private DatabaseHandler db = DatabaseHandler.getInstance();
	private JScrollPane pane = null;
	private ArrayList<String> onStageStudyList = new ArrayList<String>();
	private String patID;
	private String currentStudyUID; //selected in table
	private SeriesListTable seriesListTbl;

	public StudyListTable(String patID, String selectedStudyUID) {
		this.patID = patID;
		addOnStageStudyUID(selectedStudyUID);
		model = (DefaultTableModel) getModel();
		setModel(patID);
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
					log.info(output.toString());
					seriesListTbl.updateTableWith(currentStudyUID);
				}
			}
		});
		pane = new JScrollPane(this);
		repaint();
	}

	public void setRelatedSeriesListTable(SeriesListTable tbl) {
		seriesListTbl = tbl;
	}
	
	public JScrollPane getAsScrollPane() {
		return pane;
	}

	private void setModel(String patID) {
		ArrayList<String> studies = db.getStudyUidList(patID);
		Object[][] studyInfoSets = new Object[studies.size()][];
		for (int i = 0; i < studies.size(); i++) {
			HashMap<String, String> studyInfo = db.getStudyInfoByUIDs(patID, studies.get(i));
			List<String> modalities = db.getModalitiesInStudyRealatedAllSeries(patID, studies.get(i));
			String m = "";
			for (String mo : modalities) {
				if (m.equals("")) {
					m = m + mo;
					continue;
				}
				m = m + "," + mo;
			}
			Object[] row = new Object[header.length];
			row[0] = studies.get(i);
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

	/*
	 * modelにはUIDを含めてすべて情報を持たせる テーブルに反映後に、テーブルから列を削除する
	 * table.removeColumn(table.getColumnModel().getColumn(4));
	 * あとからUIDにアクセするときは、modelを通してアクセスする。テーブルからは取得できないので注意。
	 * table.getModel().getValueAt(table.getSelectedRow(),4);
	 * 
	 * UID-studydate-studydesc-modalities-numOfSeries
	 */
	public void constructTableView(Object[][] list) {
		model.setDataVector(list, header);
		setModel(model);
		model.fireTableDataChanged();
		TableColumnModel tcm = getColumnModel();
		TableColumn tc = tcm.getColumn(model.findColumn("Presence"));
		tc.setCellRenderer(new PresenceCellRenderer(PresenceCellRenderer.DEFAULT));
		removeColumn(getColumnModel().getColumn(0));// remove UID column, but remain in model.
		revalidate();
		repaint();
	}

	public void updateTable() {
		setModel(patID);
	}

	public String getStudyInstanceUIDAtSelectedRow(int row) {
		String studyUID = null;
		int ind = convertRowIndexToModel(row);
		studyUID = (String) model.getValueAt(ind, 0);// col 0 = UID
//		studyUID = (String)getValueAt(ind, 0);//DO NOT USE
		return studyUID;
	}

	public void addOnStageStudyUID(String studyUID) {
		if (studyUID == null) {
			return;
		}
		if (!onStageStudyList.contains(studyUID)) {
			onStageStudyList.add(studyUID);
		}
	}

	public void removeOnStageStudyUID(String studyUID) {
		if (onStageStudyList.contains(studyUID)) {
			onStageStudyList.remove(studyUID);
//			onStageStudyList.trimToSize();
		}
	}

	public void cleanUpdatePresenceOnStageStudy(ArrayList<String> newStudyUIDs) {
		onStageStudyList = new ArrayList<String>();
		onStageStudyList.addAll(newStudyUIDs);
		updateTable();
	}
}
