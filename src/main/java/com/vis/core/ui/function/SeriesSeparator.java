package com.vis.core.ui.function;

import java.util.ArrayList;

import javax.swing.JOptionPane;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;

public class SeriesSeparator {
	
	private ArrayList<DICOMNode> selected;//same pt, study, and series level only.
	
	public SeriesSeparator() {}
	
	public void separateSeries() {
		ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
		separateSeries(selected);
	}
	
	public void separateSeries(ArrayList<DICOMNode> selectedNodes) {
		/*
		 * is images level ?
		 * is in same pts ?
		 * is in same study ?
		 */
		this.selected = selectedNodes;
		if(!isSeparateReady()) {
			JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "Select images not separate ready. Please select images from same series.");
			return;
		}
		//show custom popup
		int res = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Do you want to separate series current selected images ?");
		if(res == JOptionPane.OK_OPTION) {
			separate();
			int res2 = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Do you want to delete selected images from current series ?");
			if(res2 == JOptionPane.OK_OPTION) {
				delete();
			}
		}
	}
	
	private boolean isSeparateReady() {
		if(selected == null || selected.size() < 1) {
			return false;
		}
		//ref uids
		String pid = null;
		String studyUID = null;
		String seriesUID = null;
		for(DICOMNode node : selected) {
			if(node.getLevel() != DICOMNode.IMAGE) {
				return false;
			}
			if(pid == null && studyUID == null && seriesUID == null) {
				pid = node.getData(DICOMNode.PatientID);
				studyUID = node.getData(DICOMNode.StudyInstanceUID);
				seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
				continue;//at first time, continue...
			}
			//avoid something strange.
			if(pid == null || studyUID == null || seriesUID == null) {
				return false;
			}
			String pidChi = node.getData(DICOMNode.PatientID);
			String studyUIDChi = node.getData(DICOMNode.StudyInstanceUID);
			String seriesUIDChi = node.getData(DICOMNode.SeriesInstanceUID);
			if(!pid.equals(pidChi) || !studyUID.equals(studyUIDChi) || !pid.equals(seriesUIDChi)) {
				return false;
			}
		}
		return true;
	}
	
	private void separate() {
		//TODO 20230829
//		DicomDuplicator.duplicateImageAndStore2DB(selected, true, true);
	}

	private void delete() {
		DeleteImage.deleteImages(selected);
	}
}
