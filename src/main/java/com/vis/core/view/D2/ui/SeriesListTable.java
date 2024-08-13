package com.vis.core.view.D2.ui;

import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
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
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;

public class SeriesListTable extends JTable {
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7377281377248644462L;
	
	Logger log = Log.logger;
	private String[] header = new String[] { "SeriesInstanceUID", "Presence","Modalities", "SeriesDate",
			"SeriesDescription", "NumOfInstances", "Se_No" };
	private DefaultTableModel model;
	private DatabaseHandler db = com.vis.db.DatabaseHandler.getInstance();
	private JScrollPane pane = null;
	private ArrayList<String> onStageSeriesList = new ArrayList<String>();
	private String patID;
	private String studyUID;
	private String currentSeriesUID;
	private ImageListTable imageListTbl;

	public SeriesListTable(String patID, String studyUID, String selectedSeriesUID) {
		this.patID = patID;
		this.studyUID = studyUID;
		addOnStageSeriesUID(selectedSeriesUID);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		model = (DefaultTableModel) getModel();
		setModel(patID, studyUID);
		setDefaultEditor(Object.class, null);
		getSelectionModel().addListSelectionListener(new ListTableSelectionListener());
		addMouseListener(new ListTableMouseListener());
		addMouseMotionListener(new ListTableMouseListener());
		// set drag listener
//		setTransferHandler(new DataListTransferHandler(this));
//		setDragEnabled(true);//important //java.awt.dnd.InvalidDnDOperationException : Drag and drop in progress
		DragSource source = DragSource.getDefaultDragSource();
		source.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, new DnDGesture4ListTable(this));
		pane = new JScrollPane(this);
	}
	
	public String getRelatedPatID() {
		return this.patID;
	}
	
	public String getRelatedStudyUID() {
		return this.studyUID;
	}
	
	public String getCurrentSeriesUID() {
		if(getSelectedRow() != -1) {
			return getSeriesInstanceUIDAtSelectedRow(getSelectedRow());
		}
		return null;
	}
	
	public String[] getCurrentSeriesSopUIDs() {
		if(getSelectedRow() != -1) {
			String patID = getRelatedPatID();
			String studyUID = getRelatedStudyUID();
			String seUID = getCurrentSeriesUID();
			ArrayList<String> sops = db.getInstanceUidList(patID,studyUID, seUID);
			return sops.toArray(new String[sops.size()]);
		}
		return null;
	}
	
	public void setImageListTable(ImageListTable imageTbl) {
		this.imageListTbl = imageTbl;
	}

	public JScrollPane getAsScrollPane() {
		return pane;
	}

	private void setModel(String patID, String studyUID) {
		List<HashMap<String, String>> series = db.getSeriesInfoByUIDs(patID, studyUID);
		Object[][] seriesInfoSets = new Object[series.size()][];
		for (int i = 0; i < series.size(); i++) {
			HashMap<String, String> seriesInfo = series.get(i);
			Object[] row = new Object[header.length];
			row[0] = seriesInfo.get("SeriesInstanceUID");
			if(onStageSeriesList.contains(seriesInfo.get("SeriesInstanceUID"))) {
				row[1] = true;
			}else {
				row[1] = false;
			}
			row[2] = seriesInfo.get("Modalities");// "Modalities"
			row[3] = seriesInfo.get("SeriesDate");// "StudyDate"
			row[4] = seriesInfo.get("SeriesDescription");// "StudyDescription"
			row[5] = seriesInfo.get("NumOfInstanceInSeries");// "NumOfSeriesInStudy"
			row[6] = seriesInfo.get("SeriesNumber");
			seriesInfoSets[i] = row;
		}
		constructTableView(seriesInfoSets);
	}
	
	public void updateTable() {
		setModel(patID, studyUID);
	}
	
	public void updateTableWith(String studyUID) {
		this.studyUID = studyUID;
		setModel(patID, this.studyUID);
	}

	public void constructTableView(Object[][] list) {
		model.setDataVector(list, header);
//		setModel(model);
		model.fireTableDataChanged();
		TableColumnModel tcm = getColumnModel();
		TableColumn tc = tcm.getColumn(model.findColumn("Presence"));
		tc.setCellRenderer(new PresenceCellRenderer(PresenceCellRenderer.DEFAULT));
		removeColumn(getColumnModel().getColumn(0));// remove UID column, but remain in model.
		revalidate();
		repaint();
	}

	public String getSeriesInstanceUIDAtSelectedRow(int row) {
		if(model.getRowCount() < 1) {
			return null;
		}
		String seriesUID = null;
		int ind = convertRowIndexToModel(row);
		seriesUID = (String) model.getValueAt(ind, 0);// col 0 = UID
//		seriesUID = (String)getValueAt(ind, 0);//DO NOT USE
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
	
	public void addOnStageSeriesUID(String seriesUID) {
		if(seriesUID == null) {
			return;
		}
		if (!onStageSeriesList.contains(seriesUID)) {
			onStageSeriesList.add(seriesUID);
		}
	}

	public void removeOnStageSeriesUID(String seriesUID) {
		if (onStageSeriesList.contains(seriesUID)) {
			onStageSeriesList.remove(seriesUID);
//			onStageStudyList.trimToSize();
		}
	}
	
	public void cleanUpdatePresenceOnStageSeries(ArrayList<String> newSeriesUIDs) {
		onStageSeriesList = new ArrayList<String>();
		onStageSeriesList.addAll(newSeriesUIDs);
		updateTable();
	}
	
	public void requestOpenImage(int selectedRow) {
		String seriesUID = getSeriesInstanceUIDAtSelectedRow(selectedRow);
		Eyepiece eye = Viewer2DScreen.getInstance().getEyepieceOnStageWhere(patID);
		//show all series images
		// TODO, why refUID is null ?
		eye.addPraparat(patID, studyUID, seriesUID, null, null, eye.allocateStudyColor());
		eye.autoLayout();
		Viewer2DScreen.getInstance().getStageViewAt(patID).updateInfoCake();
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
				if(Utils.isDebug) log.info(output.toString());
				imageListTbl.updateTableWith(getRelatedStudyUID(), currentSeriesUID);
				//focus to prap on eye.
				if(onStageSeriesList.contains(currentSeriesUID)) {
					Eyepiece eye = Viewer2DScreen.getInstance().getEyepieceOnStageWhere(patID);
					eye.lostAllPraparatFocusGained();
					ArrayList<Praparat> praps = eye.getPraparatAmbiguously(patID, studyUID, currentSeriesUID);
					for(Praparat pp:praps) {
//						pp.setSelectionState(true);
						if(!pp.isFocusGained()) {
							pp.setFocusGained(true);
						}
					}
				}
			}
		}
	}
}
