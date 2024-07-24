package com.vis.dicom.dimse;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;

/**
 * @author tatsunidas
 */

public class DimseUtilities {
	
	public static boolean echo(DicomCommunicationNode dest) {
		boolean res = new EchoImpl(null).echo(ConfigInfo.AppName.toString(), "localhost", null,
				dest.getAETitle(), dest.getHostName(), String.valueOf(dest.getPort()));// connection established
		return res;
	}
	
	/**
	 * Send dcm to graphy, then refresh mainscreen's treeTable.
	 * 
	 * @param dcms : dicom files
	 */
	public static synchronized void sendMe(File[] dcms) {
		if(dcms == null) {
			return;
		}
		for (File dcm : dcms) {
			sendFile(dcm);
		}
		Window win = WindowManager.getMainScreen();
		if(win !=null) {
			MainScreen main = (MainScreen) win;
			main.loadLocalStudiesBySearchKey();
		}
	}
	
	public static synchronized void editBeforeSend(File dcm, HashMap<Integer, Object> info) {
		Path tempDir = null;
		Path tempFile = null;
		try {
			// Edit
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
			reader.read(dcm.getAbsolutePath());
			DicomObject dobj = reader.getCore();
			DicomObject fmi = reader.getFileMetaInfomation();
			for (int tag : info.keySet()) {
				dobj.setValue(tag, TagDict.vrType(tag)[0], info.get(tag));
			}
			// create temp dir and save
			tempDir = Files.createTempDirectory(ConfigInfo.AppName.toString(), new FileAttribute<?>[0]);
			DicomWriter writer = DicomWriter.newDicomWriter();
			writer.write(dobj, fmi.getString(Tag.Transfer​Syntax​UID), tempDir.toFile().getAbsolutePath()+File.separator+dcm.getName());
			// set temp file path
			String fname = dcm.getName();
			if(!fname.endsWith(".dcm")) {
				fname += ".dcm";
			}
			tempFile = new File(tempDir.toFile().getAbsolutePath()+File.separator+fname).toPath();
			Log.logger.fine("File copied to temporary file");
			// send from temp file
			sendMe(new File[] {tempFile.toFile()});
			// delete file
			Files.delete(tempFile);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (tempDir != null && Files.exists(tempDir)) {
					Files.delete(tempDir);
					Log.logger.fine("Temporary directory deleted");
				}
			} catch (IOException e) {
				Log.logger.severe(e.getMessage());
			}
		}
	}
	
	private static synchronized void sendFile(File dcm) {
		List<String> cmd = new ArrayList<String>();
		String listenerInfo[] = DatabaseHandler.getInstance().getListenerDetails();
		cmd.add("-c");
		cmd.add(listenerInfo[0] + "@" + listenerInfo[1] + ":" + listenerInfo[2]);
		cmd.add(dcm.getAbsolutePath());
		String args[] = new String[cmd.size()];
		args = cmd.toArray(args);
		com.vis.dicom.dimse.StoreSCU.main(args);// after that, writeDB by DcmQRSCP
	}
	
	/**
	 * store dicom file to graphy.
	 * also you can specify whether delete after store src file if you want.
	 * @param read_file
	 * @param delteSrcAfterStored
	 */
	public static synchronized void store(String read_file, boolean deleteSrcAfterStored) {
		String listenerDetail[] = DatabaseHandler.getInstance().getListenerDetails();
		String aet = listenerDetail[0];
		String host = listenerDetail[1];
		int port = Integer.valueOf(listenerDetail[2]);
		String args[] = { "-c", aet + "@" + host + ":" + port, read_file };
		com.vis.dicom.dimse.StoreSCU.storeInstance2Graphy(args, deleteSrcAfterStored);
	}
}
