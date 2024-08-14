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

import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
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
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;

/**
 * 
 * @author tatsunidas
 *
 */
public class SeriesListTable extends JTable {
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7377281377248644462L;
	
	Logger log = Log.logger;
	private final String[] header = new String[] { "SeriesInstanceUID", "Presence","Modalities", "SeriesDate",
			"SeriesDescription", "NumOfInstances", "SeriesNo" };
	private DefaultTableModel model;
	private DatabaseHandler db = com.vis.db.DatabaseHandler.getInstance();
	private JScrollPane pane = null;
//	private ArrayList<String> onStageSeriesList = new ArrayList<String>();
	List<String> onStageSeriesList = Collections.synchronizedList(new ArrayList<>());
	//private String patID;
	private String studyUID;
	private String currentSeriesUID;
	private StudyListTable studyListTbl;
	private ImageListTable imageListTbl;
	final private PatientInfoCake cake;

	public SeriesListTable(PatientInfoCake cake, StudyListTable slt) {
		this.cake = cake;
		this.studyListTbl = slt;
		this.studyListTbl.setRelatedSeriesListTable(this);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		model = new DefaultTableModel(header, 0) {
            private static final long serialVersionUID = 1L;
			@Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
       setModel(model);
		//setDefaultEditor(Object.class, null);
		getSelectionModel().addListSelectionListener(new ListTableSelectionListener());
		addMouseListener(new ListTableMouseListener());
		addMouseMotionListener(new ListTableMouseListener());
		// set drag listener
//		setTransferHandler(new DataListTransferHandler(this));
//		setDragEnabled(true);//important //java.awt.dnd.InvalidDnDOperationException : Drag and drop in progress
		DragSource source = DragSource.getDefaultDragSource();
		source.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, new DnDGesture4ListTable(this));
		initTable(studyListTbl.getSlectedStudyUID());
		pane = new JScrollPane(this);
	}
	
	public String getRelatedPatID() {
		return cake.getPatientInfo("PatientID");
	}
	
	public String getRelatedStudyUID() {
		return this.studyUID;
	}
	
	public String getSelectedSeriesUID() {
		if(getSelectedRow() != -1) {
			return getSeriesInstanceUIDAtSelectedRow(getSelectedRow());
		}else {
			return getSeriesInstanceUIDAtSelectedRow(0);
		}
	}
	
	public String[] getCurrentSeriesSopUIDs() {
		if(getSelectedRow() != -1) {
			String patID = getRelatedPatID();
			String studyUID = getRelatedStudyUID();
			String seUID = getSelectedSeriesUID();
			ArrayList<String> sops = db.getInstanceUidList(patID,studyUID, seUID);
			return sops.toArray(new String[sops.size()]);
		}
		return null;
	}
	
	public void setRelatedImageListTable(ImageListTable imageTbl) {
		this.imageListTbl = imageTbl;
	}

	public JScrollPane getAsScrollPane() {
		return pane;
	}

	void initTable(String studyUID) {
		String patID = cake.getPatientInfo("PatientID");
		this.studyUID = studyUID;
		List<HashMap<String, String>> series = db.getSeriesInfoByUIDs(patID, studyUID);
		Object[][] seriesInfoSets = new Object[series.size()][];
		for (int i = 0; i < series.size(); i++) {
			HashMap<String, String> seriesInfo = series.get(i);
			Object[] row = new Object[header.length];
			String seUID = seriesInfo.get("SeriesInstanceUID");
			row[0] = seUID;
			if(onStageSeriesList.contains(seUID)) {
				row[1] = true;
			}else {
				row[1] = false;
			}
			row[2] = seriesInfo.get("Modality");
			row[3] = seriesInfo.get("SeriesDate");
			row[4] = seriesInfo.get("SeriesDescription");
			row[5] = seriesInfo.get("NumOfInstanceInSeries");
			row[6] = seriesInfo.get("SeriesNumber");
			seriesInfoSets[i] = row;
		}
		constructTableView(seriesInfoSets);
	}
	
	private void constructTableView(Object[][] data) {
		model.setDataVector(data, header);
		model.fireTableDataChanged();
		TableColumnModel tcm = getColumnModel();
		TableColumn presenceCol = tcm.getColumn(model.findColumn(header[1]));
		presenceCol.setCellRenderer(new PresenceCellRenderer(PresenceCellRenderer.DEFAULT));
		removeColumn(getColumnModel().getColumn(model.findColumn(header[0])));// remove UID column, but remain in model.
	}

	private String getSeriesInstanceUIDAtSelectedRow(int row) {
		if(model.getRowCount() < 1) {
			return null;
		}
		String seriesUID = null;
		int ind = convertRowIndexToModel(row);
		seriesUID = (String) model.getValueAt(ind, 0);// col 0 = UID
		return seriesUID;
	}

	public void setSelectedRow(String seriesUID) {
		if (model == null || model.getRowCount() < 1) {
			return;
		}
		for (int row = 0; row < model.getRowCount(); row++) {
			if (seriesUID.equals(getSeriesInstanceUIDAtSelectedRow(row))) {
				setRowSelectionInterval(row, row);// first,last
			}
		}
	}
	
	public void enterTheStageSeriesUID(String seriesUID, boolean initTable) {
		if(seriesUID == null) {
			return;
		}
		if (!onStageSeriesList.contains(seriesUID)) {
			onStageSeriesList.add(seriesUID);
		}
		if(initTable) {
			initTable(studyUID);
		}
	}

	public void leaveTheStageSeriesUID(String seriesUID) {
		onStageSeriesList.remove(seriesUID);
		initTable(studyUID);
	}
	
	public void cleanUpdatePresenceOnStageSeries(ArrayList<String> newSeriesUIDs) {
		onStageSeriesList.clear();
		onStageSeriesList.addAll(newSeriesUIDs);
		initTable(studyUID);
	}
	
	public void requestOpenImage(int selectedRow) {
		String seriesUID = getSeriesInstanceUIDAtSelectedRow(selectedRow);
		Eyepiece eye = Viewer2DScreen.getInstance().getEyepieceOnStageWhere(cake.getPatientInfo("PatientID"));
		//show all series images
		eye.addPraparat(cake.getPatientInfo("PatientID"), studyUID, seriesUID, null, null/*refUID will load in prap*/);
		eye.autoLayout();
		Viewer2DScreen.getInstance().getStageViewAt(cake.getPatientInfo("PatientID")).updateInfoCake();
	}
	
	class ListTableSelectionListener implements ListSelectionListener{

		@Override
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
				output.append("SeriesListTableEvent for indexes " + firstIndex + " - " + lastIndex + "; isAdjusting is " + isAdjusting
					+ "; selected indexes:");
				output.append(" studyUID:"+getRelatedStudyUID());
				// Find out which indexes are selected.
				int minIndex = lsm.getMinSelectionIndex();
				int maxIndex = lsm.getMaxSelectionIndex();
				for (int i = minIndex; i <= maxIndex; i++) {
					if (lsm.isSelectedIndex(i)) {
						currentSeriesUID = getSeriesInstanceUIDAtSelectedRow(i);
						output.append(" row:" + i);
						output.append(" seriesUID:"+currentSeriesUID);
					}
				}
				log.fine(output.toString());
				imageListTbl.initTable(getRelatedStudyUID(), currentSeriesUID);
				//focus to prap on eye.
				if(onStageSeriesList.contains(currentSeriesUID)) {
					Eyepiece eye = Viewer2DScreen.getInstance().getEyepieceOnStageWhere(cake.getPatientInfo("PatientID"));
					eye.lostAllPraparatFocusGained();
					ArrayList<Praparat> praps = eye.getPraparatAmbiguously(cake.getPatientInfo("PatientID"), studyUID, currentSeriesUID);
					for(Praparat pp:praps) {
						if(!pp.isFocusGained()) {
							pp.setFocusGained(true);
						}
					}
				}
			}
		}
	}
}
