package com.vis.core.ui.main.dcmtreetable;

import java.awt.Cursor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

import javax.swing.JOptionPane;


import com.vis.configuration.ConfigInfo;
import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.util.Utils;

/**
 * ノードをドラッグしてからの処理は、このクラスと、ドラッグソースリスナで記述する。
 * ジャヴァアプリケーションからネイティブへのデータコピーなどは、
 * 相手（OS）側からのレスポンスを取るのが難しい。
 * ドロップアクションを取得してから、細かな処理を実行するほうが良いだろう。
 * 
 * @author tatsunidas
 *
 */
public class DICOMNodeDragGestureListener implements DragGestureListener{
	
	
	public DICOMNodeDragGestureListener() {
	}

	/*
	 * ドラッグされたTreeNodeのドロップ制御
	 * (non-Javadoc)
	 * @see java.awt.dnd.DragGestureListener#dragGestureRecognized(java.awt.dnd.DragGestureEvent)
	 */
	@Override
	public void dragGestureRecognized(DragGestureEvent dge) {
		if(Utils.isDebug) {
			Log.logger.info("Drag recognizes by DICOMNodeDragGestureListener");
		}
		Cursor cursor = getCursorFromAction(dge.getDragAction(),DnDConstants.ACTION_COPY);
		DICOMTreeTable table = (DICOMTreeTable)(dge.getComponent());
		ArrayList<DICOMNode> nodes = table.getSelectedNodes();
		if(nodes == null || nodes.size()<1) {
			return;
		}
		/*
		 * Defer heavy file preparation (DB queries, file copy) to when the OS
		 * actually requests the transfer data. This makes the drag initiation
		 * feel responsive instead of blocking on I/O.
		 */
		final ArrayList<DICOMNode> capturedNodes = new ArrayList<>(nodes);
		try {
			dge.startDrag(cursor, new FileTransferable(() -> prepareExportFiles(capturedNodes)), new DICOMNodeDragSourceListener());
		} catch (java.awt.dnd.InvalidDnDOperationException e) {
			/*
			 * The native DnD machinery still considers the previous drag "in
			 * progress" (it won't finish until that drag's getTransferData()
			 * call - i.e. prepareExportFiles() - returns). Starting a new drag
			 * gesture before that resolves throws here; without this catch the
			 * exception was silently swallowed by AWT and the new drag simply
			 * never started. Log it instead of crashing the gesture recognizer.
			 */
			Log.logger.warning("DICOMNodeDragGestureListener: drag already in progress, ignoring new gesture. " + e.getMessage());
		}

		/**
		 * TODO 20230825
		 */
//		ApplicationContext.treeNodeDragging4Export = true;
		
		/*
		 * Finally, move to trash files in tmpdir.
		 * see, DICOMNodeDragSourceListener
		 */
	}
	
	/**
	 * Prepare export files by copying DICOM data to a temporary directory.
	 * This is called lazily when the drop target requests the transfer data.
	 */
	private ArrayList<File> prepareExportFiles(ArrayList<DICOMNode> nodes) {
		com.vis.db.DatabaseHandler db = com.vis.db.DatabaseHandler.getInstance();
		ArrayList<String[]> instlist = WindowManager.getMainScreen().getLocalTreeTable().createNoDuplicateImageList(nodes);
		
		if(instlist == null || instlist.isEmpty()) {
			return new ArrayList<>();
		}
		
		ArrayList<File> exportFiles = new ArrayList<File>();
		boolean fileNotFoundInDB = false;

		/*
		 * instlist already holds one correctly correlated
		 * {patID, studyIUID, seriesIUID, sopIUID} tuple per instance
		 * (see DICOMTreeTable.createNoDuplicateImageList). Iterate it directly
		 * instead of rebuilding the cartesian product of each column's distinct
		 * values - that previous approach multiplied the number of
		 * checkImageRecordExists() DB round-trips far beyond the actual instance
		 * count (e.g. 1 study x 3 series x 300 images => 900 lookups instead of
		 * 300), which is what made the drop feel stuck before the copy started.
		 */
		for (String[] info : instlist) {
			String patID = info[0];
			String studyIUID = info[1];
			String seriesIUID = info[2];
			String sopIUID = info[3];

			String baseDest = ConfigInfo.getPath(ConfigInfo.TemporalDirName);
			if(patID == null || patID.equals("") || patID.contentEquals(" ") || patID.equals("null")) {
				patID = "NULL-PatientID";
				Log.logger.warning("DICOMNodeDragGestureListener: patID is null, using fallback.");
			}
			String studyDesc = db.getValueFromStudy("StudyDescription", patID, studyIUID);
			if(studyDesc == null || studyDesc.equals("") || studyDesc.equals(" ")) {
				studyDesc = "no-studydesc";
				Log.logger.fine("DICOMNodeDragGestureListener: studyDesc is null, using fallback.");
			}
			String seriesDesc = db.getValueFromSeries("SeriesDescription", patID, studyIUID, seriesIUID);
			if(seriesDesc == null || seriesDesc.equals("") || seriesDesc.equals(" ")) {
				seriesDesc = "no-seriesDesc";
				Log.logger.fine("DICOMNodeDragGestureListener: seriesDesc is null, using fallback.");
			}
			int instNo = db.getInstanceNo(studyIUID, seriesIUID, sopIUID);
			String destParent = baseDest+File.separator+patID+File.separator+studyDesc+File.separator+seriesDesc;
			String dest = destParent+File.separator+instNo+".dcm";
			File destDir = new File(destParent);
			if(!destDir.exists()) {
				destDir.mkdirs();
			}
			String destRoot = baseDest+File.separator+patID;
			if(!exportFiles.contains(new File(destRoot))) {
				exportFiles.add(new File(baseDest+File.separator+patID));
			}
			String dcmPath = db.getValueFromImage("FileStoreUrl", patID,studyIUID, seriesIUID, sopIUID);
			File from = new File(dcmPath);
			File to = new File(dest);
			if(!from.exists()) {
				fileNotFoundInDB = true;
				continue;
			}
			try {
				Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				e.printStackTrace();
				return new ArrayList<>();
			}
		}
		if(fileNotFoundInDB) {
			Log.logger.warning("DICOMNodeDragGestureListener: missing files detected in DB, export aborted.");
			javax.swing.SwingUtilities.invokeLater(() -> {
				JOptionPane.showMessageDialog(null, Resources.i18n("DICOMNodeDragGestureListener.error.missingFiles"), Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
			});
			return new ArrayList<>();
		}
		return exportFiles;
	}
	
	protected Cursor getCursorFromAction(int userAction, int dropAction) {
		boolean accepted = (userAction == dropAction);
		Cursor cursor = null;
		switch (userAction) {
		case DnDConstants.ACTION_MOVE:
			cursor = (accepted ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop);
			break;
		case DnDConstants.ACTION_COPY:
			cursor = (accepted ? DragSource.DefaultCopyDrop : DragSource.DefaultCopyNoDrop);
			break;
		case DnDConstants.ACTION_LINK:
			cursor = (accepted ? DragSource.DefaultLinkDrop : DragSource.DefaultLinkNoDrop);
			break;
		}
		return cursor;
	}
	
}
