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
package com.vis.core.view.D2.ui.glasses;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

import javax.swing.JTable;

import com.vis.core.log.Log;

/**
 * 
 * @author tatsunidas
 *
 */
public class ImageDropTargetListener implements DropTargetListener{

	DataFlavor flavor = new DataFlavor(JTable.class, "DataList");
	
	@Override
	public void dragEnter(DropTargetDragEvent e) {
		if(e.isDataFlavorSupported(flavor)) {
			//do something...
			return;
		}
		e.rejectDrag();
	}

	@Override
	public void dragExit(DropTargetEvent e) {}

	@Override
	public void dragOver(DropTargetDragEvent e) {}

	@Override
	public void drop(DropTargetDropEvent e) {
		if(e.isDataFlavorSupported(flavor)) {
			//do something...
			try {
				e.acceptDrop(DnDConstants.ACTION_COPY);
				Transferable t =e.getTransferable();
				JTable tbl = (JTable) t.getTransferData(flavor);
				Log.logger.fine("Now number of selected row is... "+tbl.getSelectedRowCount());
			}catch (Exception ex) {
				e.dropComplete(false);
			}
			return;
		}
	}

	@Override
	public void dropActionChanged(DropTargetDragEvent e) {}

}
