package com.vis.core.view.D2.ui;

import java.io.File;
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

public class ImageListTable extends JTable{
	
	private static final long serialVersionUID = 1L;

	Logger log = Log.logger;
	private static String[] header = new String[] {"SOPInstanceUID","Presence","filename","Inst_No"};
	private DefaultTableModel model;
	private DatabaseHandler db = DatabaseHandler.getInstance();
	private JScrollPane pane = null;
	private ArrayList<String> onStageImageList = new ArrayList<String>();
	private String patID;
	private String studyUID;
	private String seriesUID;
	private ArrayList<String> currentImageSopUID = new ArrayList<String>();
	
	public ImageListTable(String patID, String studyUID, String seriesUID, String[] selectedSopUIDs) {
		this.patID = patID;
		this.studyUID = studyUID;
		this.seriesUID = seriesUID;
		addOnStageSopUID(selectedSopUIDs);
		model = (DefaultTableModel) getModel();
		setModel(patID,studyUID,seriesUID);
		getSelectionModel().addListSelectionListener(new TableListSelectionListener());
		pane = new JScrollPane(this);
	}

	public JScrollPane getAsScrollPane() {
		return pane;
	}
	
	private void setModel(String patID,String studyUID,String seriesUID) {
		List<HashMap<String,String>> images = db.getImagesInfoByUIDs(patID,studyUID,seriesUID);
		Object[][] imageInfoSets = new Object[images.size()][];
		for(int i=0;i<images.size();i++) {
			HashMap<String,String> imageInfo = images.get(i);
			String name = new File(db.getFileLocation(patID, studyUID, seriesUID, imageInfo.get("SOPInstanceUID"))).getName();
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

	public void constructTableView(Object[][] list) {
		model.setDataVector(list, header);
		setModel(model);
		model.fireTableDataChanged();
		TableColumnModel tcm = getColumnModel();
		TableColumn tc = tcm.getColumn(model.findColumn("Presence"));
		tc.setCellRenderer(new PresenceCellRenderer(PresenceCellRenderer.DEFAULT));
		removeColumn(getColumnModel().getColumn(0));//remove UID column, but remain in model.
		revalidate();
		repaint();
	}
	
	public void updateTable() {
		setModel(patID, studyUID, seriesUID);
	}
	
	public void updateTableWith(String studyUID, String seriesUID) {
		this.studyUID = studyUID;
		this.seriesUID = seriesUID;
		updateTable();
	}
	
	public String getSOPInstanceUIDAtSelectedRow(int row) {
		String sopUID = null;
		int ind = convertRowIndexToModel(row);
		sopUID = (String)model.getValueAt(ind, 0);//col 0 = UID
//		sopUID = (String)getValueAt(ind, 0);//DO NOT USE
		return sopUID;
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
	
	public void addOnStageSopUID(String[] sopUIDs) {
		if(sopUIDs == null) {
			return;
		}
		for (String uid : sopUIDs) {
			if (!onStageImageList.contains(uid)) {
				onStageImageList.add(uid);
			}
		}
	}

	public void removeOnStageSopUID(String[] sopUIDs) {
		for (String uid : sopUIDs) {
			if (onStageImageList.contains(uid)) {
				onStageImageList.remove(uid);
//				if(onStageImageList.size() == 0) {
//					onStageImageList = new ArrayList<String>();//reset
//				}
			}
		}
	}
	
	public void cleanUpdatePresenceOnStageImages(ArrayList<String[]> newSopUIDs) {
		onStageImageList = new ArrayList<String>();
		for(String[] uids:newSopUIDs) {
			addOnStageSopUID(uids);
		}
		updateTable();
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
				if(Utils.isDebug) {
					log.info(output.toString());
				}
				//focusGained
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
