package com.vis.core.ui.main.dcmtreetable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Logger;

import com.vis.core.log.Log;

/**
 * to adapt drop image files to DICOMTreeTable for import.
 * 
 */
public class DICOMTreeTableDropListener implements DropTargetListener{
	
	Logger logger = Log.logger;
	
	@Override
	public void dragEnter(DropTargetDragEvent enter) {
		// TODO Auto-generated method stub
		if(enter.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			return;
		}
		enter.rejectDrag();
	}

	@Override
	public void dragExit(DropTargetEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dragOver(DropTargetDragEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * あくまでもTreeTableに対するDrop操作に対応する。
	 * ExportはDICOMNodeDragGestureListener側で操作している。
	 */
	@Override
	public void drop(DropTargetDropEvent dtde) {
		
		/*
		 * TODO 20230829
		 */
		
//		if(ApplicationContext.treeNodeDragging4Export) {
//			System.out.println("this is itself, reject");
//			dtde.rejectDrop();
//			dtde.dropComplete(true);
//			return;
//		}
//		if(dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
//			try {
//				dtde.acceptDrop(DnDConstants.ACTION_COPY);
//				Transferable t = dtde.getTransferable();
//				@SuppressWarnings("unchecked")
//				java.util.List<File> list = (java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
//				java.util.Iterator<File> iter = list.iterator();
//				while(iter.hasNext()) {
//					File importCandidate = (java.io.File)iter.next();
//					logger.debug("Dropped to imoport " +importCandidate.getName());
//					DicomFileCollection collec = new DicomFileCollection();//init first
//					if (collec.setImportCandidate(importCandidate)) {
//						//boolean saveAsLink = chooser.getIsLink();//TODO
//						//boolean ignorePrivate = chooser.ignorePrivate();//TODO
//						//import of each study.
//						for (String willImportStudyUID : collec.getNoSubstituteStudyUIDList()) {
//							ArrayList<String> candidateList = collec.selectCandidateUsingSUID(willImportStudyUID);
//							DicomImporter importer = new DicomImporter(candidateList,false,false);
//							int res = importer.isLink();
//							if(res == 1) {
//								importer.setSaveAsLink(true);
//							}else if(res < 0) {
//								return;//canceled
//							}
//							int total = candidateList.size();
//							//As Thread Manager of importer.
//							DICOMTreeTable mainTreeTable = ApplicationContext.getInstance().getMainScreen().getTreeTable();
//							int currentArchiveCol = mainTreeTable.getArchivedColumnPosition();
//							LocalDBStateCellRendererableEditor stateCell = mainTreeTable.getStateCellEditorAtArchiveColumn(currentArchiveCol);
//							// add new ImportingState.
//							stateCell.addImportingState(willImportStudyUID, total, importer);
//							ApplicationContext.importing = true;
//							ApplicationContext.importerExecSvc.submit(importer);
////							importer.startImport();//use executor.(but this is also can use.)
//						}
//					}
//				}
//			}catch(Exception e) {
//				dtde.dropComplete(false);
//				return;
//			}
//		}else if(dtde.getTransferable() instanceof File) {
//			//not need??
//		}
	}

	@Override
	public void dropActionChanged(DropTargetDragEvent arg0) {
		// TODO Auto-generated method stub
		
	}

}
