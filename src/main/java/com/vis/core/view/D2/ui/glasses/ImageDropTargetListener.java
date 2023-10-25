package com.vis.core.view.D2.ui.glasses;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

import javax.swing.JTable;

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
				System.out.println("Now number of selected row is... "+tbl.getSelectedRowCount());
				System.out.println();
			}catch (Exception ex) {
				// TODO: handle exception
				e.dropComplete(false);
			}
			return;
		}
	}

	@Override
	public void dropActionChanged(DropTargetDragEvent e) {}

}
