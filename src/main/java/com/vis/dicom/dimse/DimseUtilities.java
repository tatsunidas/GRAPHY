package com.vis.dicom.dimse;

import java.awt.Window;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.MainScreen;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;

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
		Window win = WindowManager.getWindow(ConfigInfo.MainScreen.toString());
		if(win !=null) {
			MainScreen main = (MainScreen) win;
			main.loadLocalStudiesBySearchKey();
		}
	}
	
	public static synchronized void sendFile(File dcm) {
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
	public static synchronized void store(String read_file, boolean delteSrcAfterStored) {
		String listenerDetail[] = DatabaseHandler.getInstance().getListenerDetails();
		String aet = listenerDetail[0];
		String host = listenerDetail[1];
		int port = Integer.valueOf(listenerDetail[2]);
		String args[] = { "-c", aet + "@" + host + ":" + port, read_file };
		com.vis.dicom.dimse.StoreSCU.storeInstance2Graphy(args, delteSrcAfterStored);
	}
}
