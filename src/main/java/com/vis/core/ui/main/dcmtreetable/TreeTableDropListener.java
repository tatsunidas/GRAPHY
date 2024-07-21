package com.vis.core.ui.main.dcmtreetable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.ui.function.DicomImporter;
import com.vis.core.util.Utils;
import com.vis.dicom.DicomFileCollection;

/**
 * to adapt drop image files to DICOMTreeTable to import.
 * 
 */
public class TreeTableDropListener implements DropTargetListener{
	
	Logger logger = Log.logger;
	
	@Override
	public void dragEnter(DropTargetDragEvent enter) {
		if(enter.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			if(Utils.isDebug) System.out.println("JavaFilesFlavor Dragging In...");
			return;
		}
	}

	@Override
	public void dragExit(DropTargetEvent arg0) {}

	@Override
	public void dragOver(DropTargetDragEvent arg0) {}

	/**
	 * For dropping on DICOMTreeTable.
	 * Export is also implemeted in DICOMNodeDragGestureListener.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void drop(DropTargetDropEvent dtde) {
		
		if(Utils.isDebug) System.out.println("dropped");
		
		DataFlavor[] fs = dtde.getCurrentDataFlavors();
		for(DataFlavor f: fs) {
			// skip swing component
			if(f.isMimeTypeEqual(DataFlavor.javaJVMLocalObjectMimeType)) {
				dtde.rejectDrop();
				dtde.dropComplete(true);
				return;
			}
		}
		ArrayList<File> candidates = new ArrayList<>();
		for(DataFlavor f: fs) {
			if(f.isMimeTypeEqual(DataFlavor.javaFileListFlavor)) {
				dtde.acceptDrop(DnDConstants.ACTION_COPY);
				Transferable t = dtde.getTransferable();
				java.util.List<Object> list = null;
				try {
					list = (java.util.List<Object>)t.getTransferData(DataFlavor.javaFileListFlavor);
				} catch (UnsupportedFlavorException | IOException e) {
					e.printStackTrace();
					dtde.rejectDrop();
					dtde.dropComplete(true);
					return;
				}
				java.util.Iterator<Object> iter = list.iterator();
				while(iter.hasNext()) {
					Object obj = iter.next();
					if(!(obj instanceof File)) {
						continue;
					}else {
						candidates.add((java.io.File)obj);
					}
				}
			}
		}
		if(!candidates.isEmpty()) {
			File[] files = candidates.toArray(new File[candidates.size()]);
			DicomFileCollection collec = new DicomFileCollection(files);
			collec.collectCandidates();
			boolean saveAsLink = false;
			if(collec.getNumOfTotalDcmFiles()>0) {
				for (String willImportStudyUID : collec.getNoSubstituteStudyUIDList()) {
					ArrayList<String> candidateList = collec.selectCandidateUsingStudyUID(willImportStudyUID);
					DicomImporter importer = new DicomImporter(candidateList,willImportStudyUID);
//					int total = candidateList.size();
					//As Thread Manager of importer.
//					DICOMTreeTable mainTreeTable = ApplicationContext.getInstance().getMainScreen().getTreeTable();
//					int currentArchiveCol = mainTreeTable.getArchivedColumnPosition();
//					LocalDBStateCellRendererableEditor stateCell = mainTreeTable.getStateCellEditorAtArchiveColumn(currentArchiveCol);
//					stateCell.addImportingState(willImportStudyUID, total, importer);
//					ApplicationContext.importing = true;
//					ApplicationContext.importerExecSvc.submit(importer);
					importer.start();//use executor.(but this is also can use.)
				}
			}
		}
		dtde.dropComplete(true);
	}

	@Override
	public void dropActionChanged(DropTargetDragEvent arg0) {}

}
