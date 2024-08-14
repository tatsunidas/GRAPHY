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

import java.io.File;
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
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;

/**
 * 
 * @author tatsunidas
 *
 */
public class ImageListTable extends JTable{
	
	private static final long serialVersionUID = 1L;

	Logger log = Log.logger;
	private final String[] header = new String[] {"SOPInstanceUID","Presence","FileName","Inst_No"};
	private DefaultTableModel model;
	private DatabaseHandler db = DatabaseHandler.getInstance();
	private JScrollPane pane = null;
//	private ArrayList<String> onStageImageList = new ArrayList<String>();
	List<String> onStageImageList = Collections.synchronizedList(new ArrayList<>());
	private String studyUID;
	private String seriesUID;
	private ArrayList<String> currentImageSopUID = new ArrayList<String>();
	final private PatientInfoCake cake;
	private SeriesListTable seriesListTbl;
	
	public ImageListTable(PatientInfoCake cake, SeriesListTable selt) {
		this.cake = cake;
		seriesListTbl = selt;
		seriesListTbl.setRelatedImageListTable(this);
		model = new DefaultTableModel() {
            private static final long serialVersionUID = 1L;
			@Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
       setModel(model);
		setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		getSelectionModel().addListSelectionListener(new TableListSelectionListener());
		initTable(null, null);
		pane = new JScrollPane(this);
	}

	public JScrollPane getAsScrollPane() {
		return pane;
	}
	
	void initTable(String studyUID,String seriesUID) {
		this.studyUID = studyUID;
		this.seriesUID = seriesUID;
		if(this.studyUID == null) {
			this.studyUID = db.getStudyUidList(cake.getPatientInfo("PatientID")).get(0);
		}
		if(this.seriesUID == null) {
			this.seriesUID = db.getSeriesUidList(cake.getPatientInfo("PatientID"), this.studyUID).get(0);
		}
		List<HashMap<String,String>> images = db.getImagesInfoByUIDs(cake.getPatientInfo("PatientID"),this.studyUID,this.seriesUID);
		Object[][] imageInfoSets = new Object[images.size()][];
		for(int i=0;i<images.size();i++) {
			HashMap<String,String> imageInfo = images.get(i);
			String name = new File(db.getFileLocation(this.studyUID, this.seriesUID, imageInfo.get("SOPInstanceUID"))).getName();
			Object[] row = new Object[header.length];
			row[0] = imageInfo.get("SOPInstanceUID");
			if(onStageImageList.contains(imageInfo.get("SOPInstanceUID"))) {
				row[1] = true;
			}else {
				row[1] = false;
			}
			row[2] = name;
			row[3] = imageInfo.get("InstanceNumber");
			imageInfoSets[i] = row;
		}
		constructTableView(imageInfoSets);
	}

	private void constructTableView(Object[][] list) {
		model.setDataVector(list, header);
		model.fireTableDataChanged();
		TableColumnModel tcm = getColumnModel();
		TableColumn tc = tcm.getColumn(model.findColumn(header[1]));
		tc.setCellRenderer(new PresenceCellRenderer(PresenceCellRenderer.DEFAULT));
		removeColumn(getColumnModel().getColumn(0));//remove UID column, but remain in model.
		revalidate();
		repaint();
	}
	
	public String getSOPInstanceUIDAtSelectedRow(int row) {
		String sopUID = null;
		int ind = convertRowIndexToModel(row);
		sopUID = (String)model.getValueAt(ind, 0);//col 0 = UID
//		sopUID = (String)getValueAt(ind, 0);//DO NOT USE
		return sopUID;
	}
	
	public String[] getSelectedSopUIDs() {
		int[] rows = getSelectedRows();
		if(rows.length == 0) {
			return null;
		}
		String[] selectedSopUIDs = new String[rows.length];
		for(int i=0; i<rows.length; i++) {
			String sopUID = getSOPInstanceUIDAtSelectedRow(rows[i]);
			selectedSopUIDs[i] = sopUID;
		}
		return selectedSopUIDs;
	}
	
	public int getSlicePositionBySopUID(String sopUID) {
		int pos = -1;
		for(int i=0;i<model.getRowCount();i++) {
			if(getSOPInstanceUIDAtSelectedRow(i).equals(sopUID)) {
				pos = i;
				break;
			}
		}
		return pos;
	}

	public void setSelectedRow(String sopUID) {
		if (model == null || model.getRowCount() < 1) {
			return;
		}
		for (int row = 0; row < model.getRowCount(); row++) {
			if (sopUID.equals(getSOPInstanceUIDAtSelectedRow(row))) {
				setRowSelectionInterval(row, row);// first,last
			}
		}
	}
	
	public void enterTheStageSopUID(String[] sopUIDs, boolean initTable) {
		if(sopUIDs == null) {
			return;
		}
		for (String uid : sopUIDs) {
			if (!onStageImageList.contains(uid)) {
				onStageImageList.add(uid);
			}
		}
		if(initTable) {
			initTable(studyUID, seriesUID);
		}
	}

	public void leaveTheStageSopUID(String[] sopUIDs) {
		for (String uid : sopUIDs) {
			onStageImageList.remove(uid);
		}
		initTable(studyUID, seriesUID);
	}
	
	public void cleanUpdatePresenceOnStageImages(ArrayList<String[]> newSopUIDs) {
		onStageImageList.clear();
		for(String[] uids:newSopUIDs) {
			enterTheStageSopUID(uids, false);
		}
		initTable(studyUID, seriesUID);
	}
	
	class TableListSelectionListener implements ListSelectionListener {
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
				output.append("ImageListTableEvent for indexes " + firstIndex + " - " + lastIndex + "; isAdjusting is " + isAdjusting
					+ "; selected indexes:");
				// Find out which indexes are selected.
				int minIndex = lsm.getMinSelectionIndex();
				int maxIndex = lsm.getMaxSelectionIndex();
				//init image list
				currentImageSopUID = new ArrayList<String>();
				for (int i = minIndex; i <= maxIndex; i++) {
					if (lsm.isSelectedIndex(i)) {
						if(!currentImageSopUID.contains(getSOPInstanceUIDAtSelectedRow(i))) {
							currentImageSopUID.add(getSOPInstanceUIDAtSelectedRow(i));
						}
						output.append(" " + i);
						output.append(" SopUID:"+currentImageSopUID);
					}
				}
				log.fine(output.toString());
				//focusGained
				String patID = cake.getPatientInfo("PatientID");
				Eyepiece eye = Viewer2DScreen.getInstance().getEyepieceOnStageWhere(patID);
				eye.lostAllPraparatFocusGained();
				ArrayList<Praparat> praps = eye.getPraparatAmbiguously(patID, studyUID, seriesUID);
				for(Praparat pp:praps) {
					Object[] uids = pp.getUIDs();
					String[] sopUIDinPrap = (String[])uids[3];
					for(String sop:sopUIDinPrap) {
						if(currentImageSopUID.contains(sop)) {
							if (!pp.isFocusGained()) {
								pp.setFocusGained(true);
								int pos = getSlicePositionBySopUID(sop);
								if(pos != -1) {
									pp.setImagePositionUsingSlider(pos);
									break;
								}
							}
						}
					}
				}
			}
		}
	}
}
