package com.vis.core.ui.dialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.db.DatabaseHandler;

/**
 * 
 * @author tatsunidas t_kobayashi@vis-ionary.com
 *
 */
@SuppressWarnings("serial")
public class DicomExporter extends JFrame implements Runnable {

    //debug
//	public static void main(String[] args) {
//		// TODO Auto-generated method stub
//		JFrame f = new JFrame();
//		f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//		JFileChooser jfc = new JFileChooser();
//		jfc.setDialogTitle("Export Option Dialog");
//		jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
//		jfc.setCurrentDirectory(new File(System.getProperty("user.home")));
//		ExportOptionPanel eop = new ExportOptionPanel();
//		jfc.setAccessory(eop);
//		jfc.setApproveButtonText("OK");
//		jfc.setApproveButtonToolTipText("");
//		new DicomExporter().doAction(jfc.showOpenDialog(f));
//	}

	boolean burnCD = false;
	ArrayList<DICOMNode> targetNodes;
	JFileChooser jfc;
	ExportOptionPanel eop;
	String approveButtonText = "Export";
	boolean flatOutput = false;// default if hierarchical
	boolean decompress = false;// default

	Thread th;

	public DicomExporter() {
		th = new Thread(this);
	}

	public DicomExporter(ArrayList<DICOMNode> targetNodes) {
		if (targetNodes == null || targetNodes.size() < 1) {
			Log.logger.info("Please select node from TreeTable.");
			return;
		}
		th = new Thread(this);
		this.targetNodes = targetNodes;
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		jfc = new JFileChooser();
		jfc.setDialogTitle("Export Option Dialog");
		jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		jfc.setCurrentDirectory(new File(System.getProperty("user.home")));
		eop = new ExportOptionPanel();
		jfc.setAccessory(eop);
		jfc.setApproveButtonText(approveButtonText);
//		jfc.setApproveButtonToolTipText(approveToolTip);
		int res = jfc.showOpenDialog(this);
		if (res == JFileChooser.APPROVE_OPTION) {
			th.start();
		} else if (res == JFileChooser.CANCEL_OPTION) {
			doClose();
		} else {
			doClose();
		}
	}

	public DicomExporter(ArrayList<DICOMNode> targetNodes, boolean burnCD) {
		if (targetNodes == null || targetNodes.size() < 1) {
			Log.logger.info("Please select node from TreeTable.");
			return;
		}
		this.burnCD = burnCD;
		th = new Thread(this);
		this.targetNodes = targetNodes;
		if (!burnCD) {
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			jfc = new JFileChooser();
			jfc.setDialogTitle("Export Option Dialog");
			jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			jfc.setCurrentDirectory(new File(System.getProperty("user.home")));
			eop = new ExportOptionPanel();
			jfc.setAccessory(eop);
			jfc.setApproveButtonText(approveButtonText);
		}
		th.start();
	}

	ArrayList<DICOMNode> getTargetNodes() {
		return this.targetNodes;
	}

	private void exportDICOM() {
		//TODO ? if QR treetable ?
		ArrayList<String[]> exportSet = WindowManager.getMainScreen().getLocalTreeTable()
				.createNoDuplicateImageList(getTargetNodes());
		String[] selected = eop.getSelectedButtonsName();
		System.out.println("Settings:" + selected[0] + " " + selected[1]);
		// folder structure
		if (!burnCD) {
			switch (selected[0]) {
			case "hierarchical":
				flatOutput = false;
				break;
			case "flat":
				flatOutput = true;
				break;
			default:
			}
			// decompress
			switch (selected[1]) {
			case "asis":
				decompress = false;
				break;
			case "decompress":
				decompress = true;
				break;
			default:
			}
			exportDICOM(flatOutput, decompress, jfc.getSelectedFile(), exportSet);
		}else {
			if(!new File("tmp").exists()) {
				try {
					new File("tmp").createNewFile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			exportDICOM(false, false, new File("tmp/"+"BURN2CD"), exportSet);
		}
		
	}

	private void exportDICOM(boolean flatOutput, boolean decompress, File selectedDir, ArrayList<String[]> exportSet) {

		System.out.println(exportSet.size());

		DatabaseHandler db = DatabaseHandler.getInstance();
		ArrayList<String> patIDs = new ArrayList<String>();
		ArrayList<String> studyIUIDs = new ArrayList<String>();
		ArrayList<String> seriesIUIDs = new ArrayList<String>();
		ArrayList<String> sopIUIDs = new ArrayList<String>();
		for (String[] info : exportSet) {
			patIDs.add(info[0]);
			studyIUIDs.add(info[1]);
			seriesIUIDs.add(info[2]);
			sopIUIDs.add(info[3]);
		}
		patIDs = new ArrayList<>(new HashSet<>(patIDs));
		studyIUIDs = new ArrayList<>(new HashSet<>(studyIUIDs));
		seriesIUIDs = new ArrayList<>(new HashSet<>(seriesIUIDs));
		sopIUIDs = new ArrayList<>(new HashSet<>(sopIUIDs));

		boolean missingFilesFound = false;

		// patient name, if null or "" -> noname
		// study date, if null or "" -> studydate-null
		// series desc, if null or "" -> seriesdesc-null
		// image -> instanceNo. if null -> AS-IS
		for (String patID : patIDs) {
			for (String studyIUID : studyIUIDs) {
				int instanceCount = 0;// for flat saving
				for (String seriesIUID : seriesIUIDs) {
					for (String sopIUID : sopIUIDs) {
						if (!db.checkImageRecordExists(patID, studyIUID, seriesIUID, sopIUID)) {
							continue;
						}
						if (patID == null || patID.equals("") || patID.contentEquals(" ")) {
							patID = "NULL-PatientID";
							System.out.println("patID is null");
						}
						// destはdescription使う
						String studyDesc = db.getParticularInfoFromStudy("StudyDescription", patID, studyIUID);
						if (studyDesc == null || studyDesc.equals("") || studyDesc.equals(" ")) {
//							studyDesc = "no-studydesc";//重複の可能性あり
							studyDesc = studyIUID;
							System.out.println("studyDesc is null, uid used instead.");
						}
						String seriesDesc = db.getParticularInfoFromSeries("SeriesDescription", patID, studyIUID,
								seriesIUID);
						if (seriesDesc == null || seriesDesc.equals("") || seriesDesc.equals(" ")) {
//							seriesDesc = "no-seriesDesc";
							seriesDesc = seriesIUID;
							System.out.println("seriesDesc is null, uid used instead.");
						}
						int instNo = db.getInstanceNo(patID, studyIUID, seriesIUID, sopIUID);

						if (!flatOutput) {// hierarchical
							String destParent = selectedDir.getAbsolutePath() + File.separator + patID + File.separator
									+ studyDesc + File.separator + seriesDesc;
							File destDirs = new File(destParent);
							if (!destDirs.exists()) {
								destDirs.mkdirs();
							}
							String dest = destParent + File.separator + instNo + ".dcm";
							// copy to temp
							String dcmPath = db.getParticularInfoFromImage("FileStoreUrl", patID, studyIUID, seriesIUID,
									sopIUID);
							File from = new File(dcmPath);
							File to = new File(dest);
							if (!from.exists()) {
								missingFilesFound = true;
								continue;
							}
							synchronized (to) {
								if (decompress) {
									/**
									 * TODO 20230831
									 */
									// do it to saved dcm
//									new Decompressor.newInstance(dcmObj, tsuid).decompress();
								} else {
									try {
										Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
									} catch (IOException e) {
										e.printStackTrace();
										return;
									}
								}
							}
							// flat
						} else {
							String destParent = selectedDir.getAbsolutePath() + File.separator + patID + File.separator
									+ studyDesc;
							File destDirs = new File(destParent);
							if (!destDirs.exists()) {
								destDirs.mkdirs();
							}
							String dest = destParent + File.separator + instanceCount + ".dcm";
							// copy to temp
							String dcmPath = db.getParticularInfoFromImage("FileStoreUrl", patID, studyIUID, seriesIUID,
									sopIUID);
							File from = new File(dcmPath);
							File to = new File(dest);
							if (!from.exists()) {
								missingFilesFound = true;
								continue;
							}
							synchronized (to) {
								if (decompress) {
									// do it to saved dcm
									// TODO
//									new Decompressor.newInstance(dcmObj, tsuid).decompress();
								} else {
									try {
										Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
										return;
									}
								}
								instanceCount++;
							}
						}
					}
				}
			} // study loop
		} // patient loop

		if (missingFilesFound) {
			JOptionPane.showConfirmDialog(null, "Missing files found, cannot completed exporting files.");
			return;
		}

		// finally, create DICOMDIR
		/*
		 * CDなどのメディア作成ではないので、不要。
		 */
//		for(String path2PatDir:exportRootParentPathSet) {
//			attachDICOMDIRFor(path2PatDir);
//		}
		// end
		exportDone();
	}

	@Deprecated //see, DicomUtil
	void attachDICOMDIRFor(String path2PatDir) {
//		/*
//		 * path:"DICOM" folder root:"Pname" folder
//		 */
//		String[] cmd = { "-c", path2PatDir + File.separator + "DICOMDIR", path2PatDir };
//		try {
//			new DcmDir().main(cmd);
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

//	private String changePatientName(String name) {
////		String name = tmpAttr.getString(Tag.PatientName);
//		if (name.contains("=")) {
//			name = name.substring(name.lastIndexOf("=") + 1);
//		}
//		// replace space to under score.
//		if (name.contains(" ") || name.contains("　")) {
//			name = name.replaceAll(" ", "_");
//			name = name.replaceAll("　", "_");
//		}
//		return name;
//	}

	private void exportDone() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// show result
				JOptionPane.showOptionDialog(WindowManager.getMainScreen(), "Export done.",
						"Complete -Export images-", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
						new String[] { "OK" }, "default");
			}
		});
	}

	private void doClose() {
		if (jfc != null) {
			jfc.setVisible(false);
		}
		dispose();
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		exportDICOM();
	}
}
