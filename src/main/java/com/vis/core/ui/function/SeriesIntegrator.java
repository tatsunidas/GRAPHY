package com.vis.core.ui.function;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.pushingpixels.substance.internal.utils.RolloverMenuItemListener;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;

/**
 * procedure:
 * 1.select series node as integrate destination.
 * 2.select series nodes or image nodes to integrate above.
 * @author tatsunidas
 */
public class SeriesIntegrator{
	
	private ArrayList<DICOMNode> selected;//same pt, study, and series level only.
	private JComboBox<String> destSeriesList;
	private String destSeriesKey;
	private HashMap<String, DICOMNode> seriesMap;
	private ArrayList<DICOMNode> willIntegrate;
//	private boolean deleteAfterSeparation = false;
	
	public SeriesIntegrator() {}
	
	public void integrateSeries() {
		ArrayList<DICOMNode> selected = WindowManager.getMainScreen().getSelectedNode();
		integrateSeries(selected);
	}
	
	public void integrateSeries(ArrayList<DICOMNode> selectedNodes) {
		
		this.selected = selectedNodes;
		if(!isReady()) {
			JOptionPane.showMessageDialog(WindowManager.getMainScreen(), "Selected images not integrate ready. Please select series/images.");
			return;
		}
		buildDestSeriesCombo();
		//show custom popup
		if(showCustomPopup() == JOptionPane.OK_OPTION) {
			destSeriesKey = (String) destSeriesList.getSelectedItem();
			//do integrate
			integrate();
			int res2 = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Do you want to delete integrated images from current series ?");
			if(res2 == JOptionPane.OK_OPTION) {
				delete(willIntegrate);
			}
		}
	}
	
	private boolean isReady() {
		if(selected == null || selected.size() < 1) {
			return false;
		}
		//ref uids
//		String pid = null;
//		String studyUID = null;
//		String seriesUID = null;
		seriesMap = new HashMap<String, DICOMNode>();
		for(DICOMNode node : selected) {
			if(node.getLevel() != DICOMNode.SERIES && node.getLevel() != DICOMNode.IMAGE) {
				return false;
			}
			if(node.getLevel() == DICOMNode.SERIES) {
				String pid = node.getData(DICOMNode.PatientID);
				String date = node.getData(DICOMNode.StudyDate);
				String desc = node.getData(DICOMNode.SeriesDescription);
				seriesMap.put(pid+"_"+date+"_"+desc, node);
			}
//			if(pid == null && studyUID == null && seriesUID == null) {
//				pid = node.getData(DICOMNode.PatientID);
//				studyUID = node.getData(DICOMNode.StudyInstanceUID);
//				seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
//				continue;//at first time, continue...
//			}
//			//avoid something strange.
//			if(pid == null || studyUID == null || seriesUID == null) {
//				return false;
//			}
//			String pidChi = node.getData(DICOMNode.PatientID);
//			String studyUIDChi = node.getData(DICOMNode.StudyInstanceUID);
//			String seriesUIDChi = node.getData(DICOMNode.SeriesInstanceUID);
//			if(!pid.equals(pidChi) || !studyUID.equals(studyUIDChi) || !pid.equals(seriesUIDChi)) {
//				return false;
//			}
		}
		return true;
	}
	
	private void buildDestSeriesCombo() {
		/*
		 * key : pid + study date + series desc
		 * value : node
		 */
		Set<String> keys = seriesMap.keySet();
		destSeriesList = new JComboBox<>(keys.toArray(new String[keys.size()]));
		destSeriesList.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				destSeriesKey = (String) destSeriesList.getSelectedItem();
			}
		});
	}
		
	private JPanel constructConfirmPanel() {
		JPanel p = new JPanel();
		p.setLayout(new java.awt.BorderLayout());
		JLabel l1 = new JLabel(" will integrate to...");
		p.add(l1, java.awt.BorderLayout.NORTH);
		p.add(destSeriesList, java.awt.BorderLayout.SOUTH);
		return p;
	}

	private int showCustomPopup() {
		 return JOptionPane.showConfirmDialog(null,constructConfirmPanel(),"Integration detail...", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
	}
	
	private void integrate() {
		willIntegrate = new ArrayList<>();
		DICOMNode destNode = seriesMap.get(destSeriesKey);
		
		String refPid = destNode.getData(DICOMNode.PatientID);
		String refStudyUID = destNode.getData(DICOMNode.StudyInstanceUID);
		String refSeriesUID = destNode.getData(DICOMNode.SeriesInstanceUID);
		
		for(DICOMNode node : selected) {
			/*
			 * skip if same series to destNode
			 * set destNode's SeriesUID
			 */
			String pid = node.getData(DICOMNode.PatientID);
			String studyUID = node.getData(DICOMNode.StudyInstanceUID);
			String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
			if(refPid.equals(pid) && refStudyUID.equals(studyUID) && refSeriesUID.equals(seriesUID)) {
				continue;
			}else {
				willIntegrate.add(node);
			}
		}
		//TODO 20230829
//		DicomDuplicator.duplicateImageAndStore2DB(willIntegrate, refPid, refStudyUID, refSeriesUID, true);
	}
	
	private void delete(ArrayList<DICOMNode> deleteNodes) {
		DeleteImage.deleteImages(deleteNodes);
	}
}
