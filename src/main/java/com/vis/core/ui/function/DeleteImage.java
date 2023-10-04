package com.vis.core.ui.function;

import java.sql.SQLException;
import java.util.ArrayList;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomReader;
import com.vis.dicom.Tag;

/**
 * @author tatsunidas
 */

public class DeleteImage {

	public static void deleteImages(ArrayList<DICOMNode> nodeList){
		if(nodeList == null || nodeList.size() <1) {
			Log.logger.info("DeleteImage :: please select row from DICOMTreeTable, return nothing to do.");
			return;
		}
		ArrayList<String[]> deleteList = WindowManager.getMainScreen().getLocalTreeTable().createNoDuplicateImageList(nodeList);
		deleteImages(deleteList, true);
	}
	
	public static void deleteImages(ArrayList<String[]> deleteInstList, boolean dummy){
		if(deleteInstList == null || deleteInstList.size() <1) {
			return;
		}
		for(String[] infoSet : deleteInstList) {
			//0:pid,1:studyuid,2:seriesuid,3:sopuid,4:path2img
			String patID = infoSet[0];
			String studyUID = infoSet[1];
			String seriesUID = infoSet[2];
			String sopUID = infoSet[3];
			try {
				DatabaseHandler.getInstance().deleteInstance(patID, studyUID, seriesUID, sopUID);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
	}
	
	public static void deleteImagesByFilePath(ArrayList<String> deleteInstFileLocs){
		if( deleteInstFileLocs == null || deleteInstFileLocs.size() <1) {
			return;
		}
		for(String loc: deleteInstFileLocs) {
			//0:pid,1:studyuid,2:seriesuid,3:sopuid,4:path2img
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
			reader.read(loc);
			String patID = reader.getCore().getString(Tag.Patient​ID);
			String studyUID = reader.getCore().getString(Tag.Study​Instance​UID);
			String seriesUID = reader.getCore().getString(Tag.Series​Instance​UID);
			String sopUID = reader.getCore().getString(Tag.SOP​Instance​UID);
			reader = null;
			try {
				DatabaseHandler.getInstance().deleteInstance(patID, studyUID, seriesUID, sopUID);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		WindowManager.getMainScreen().loadLocalStudiesBySearchKey();
	}
}
