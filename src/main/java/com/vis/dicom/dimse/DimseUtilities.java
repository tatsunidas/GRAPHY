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

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;

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
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			return false;
		}
		String[] listenerInfo = db.getListenerDetails();
		boolean res = new EchoImpl(null).echo(listenerInfo[0], listenerInfo[1], listenerInfo[2],
				dest.getAETitle(), dest.getHostName(), String.valueOf(dest.getPort()));// connection established
		return res;
	}
	
	/**
     * 接続先PACS/Modalityが、Study Rootにおける C-GET および C-MOVE をサポートしているか検証します。
     * @param dest 接続先ノード情報 (IP, Port, AET)
     * @param localAET 自アプリケーションのAET (例: "GRAPHY")
     * @return boolean配列 [0]: C-GETのサポート有無, [1]: C-MOVEのサポート有無
     */
	public static boolean[] checkRetrieveSupport(DicomCommunicationNode dest, String localAET) {
		boolean[] supportStatus = new boolean[] { false, false };

		Device device = new Device("capability-checker");
		Connection conn = new Connection();
		ApplicationEntity ae = new ApplicationEntity(localAET);
		device.addConnection(conn);
		device.addApplicationEntity(ae);
		ae.addConnection(conn);

		Connection remoteConn = new Connection();
		remoteConn.setHostname(dest.getHostName());
		remoteConn.setPort(dest.getPort());

		AAssociateRQ rq = new AAssociateRQ();
		rq.setCalledAET(dest.getAETitle());
		rq.setCallingAET(localAET);

		// 【ここがポイント】テストしたい機能を「提案（Presentation Context）」として追加する
		// IDは奇数である必要があります (1, 3, 5...)

		// 1. C-GET (Study Root) を提案
		rq.addPresentationContext(
				new PresentationContext(1, org.dcm4che3.data.UID.StudyRootQueryRetrieveInformationModelGet,
						org.dcm4che3.data.UID.ImplicitVRLittleEndian));

		// 2. C-MOVE (Study Root) を提案
		rq.addPresentationContext(
				new PresentationContext(3, org.dcm4che3.data.UID.StudyRootQueryRetrieveInformationModelMove,
						org.dcm4che3.data.UID.ImplicitVRLittleEndian));

		Association as = null;
		try {
			// アソシエーション確立（ここで相手からの Accept または Reject が返ってくる）
			as = ae.connect(conn, remoteConn, rq);

			// 結果の判定：pcFor(...) が null でなければ、相手が「承諾」したことを意味する
			supportStatus[0] = as.pcFor(org.dcm4che3.data.UID.StudyRootQueryRetrieveInformationModelGet,
					org.dcm4che3.data.UID.ImplicitVRLittleEndian) != null;
			supportStatus[1] = as.pcFor(org.dcm4che3.data.UID.StudyRootQueryRetrieveInformationModelMove,
					org.dcm4che3.data.UID.ImplicitVRLittleEndian) != null;

			Log.logger.info(String.format("Capability Check for %s - C-GET: %b, C-MOVE: %b", dest.getAETitle(),
					supportStatus[0], supportStatus[1]));

		} catch (Exception e) {
			Log.logger.warning("Failed to negotiate with " + dest.getAETitle() + ": " + e.getMessage());
			// 接続自体に失敗した場合は両方 false のまま返す
		} finally {
			// 用が済んだらすぐに通信を切断する
			if (as != null && as.isReadyForDataTransfer()) {
				try {
					as.release();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return supportStatus;
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
			reader.read(dcm.getAbsolutePath(), true/*with pixel*/);
			DicomObject dobj = reader.getHeader();
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
	public static synchronized void store(String read_file, boolean deleteSrcAfterStored) {
		String listenerDetail[] = DatabaseHandler.getInstance().getListenerDetails();
		String aet = listenerDetail[0];
		String host = listenerDetail[1];
		int port = Integer.valueOf(listenerDetail[2]);
		String args[] = { "-c", aet + "@" + host + ":" + port, read_file };
		com.vis.dicom.dimse.StoreSCU.storeInstance2Graphy(args, deleteSrcAfterStored);
	}
}
