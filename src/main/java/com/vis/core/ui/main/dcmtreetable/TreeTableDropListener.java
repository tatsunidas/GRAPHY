/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
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
