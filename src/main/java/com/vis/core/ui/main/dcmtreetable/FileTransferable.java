package com.vis.core.ui.main.dcmtreetable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class FileTransferable implements Transferable {

	protected ArrayList<File> files;

	public FileTransferable(ArrayList<File> files) {
		this.files = files;
	}

	public DataFlavor[] getTransferDataFlavors() {
		return TreeTableDnDDataFlavors.supportedFlavors;
	}

	public boolean isDataFlavorSupported(DataFlavor flavor) {
		for(DataFlavor flv:TreeTableDnDDataFlavors.supportedFlavors) {
			if(flv.equals(flavor)) {
				return true;
			}
		}
		return false;
	}

	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
		if (!isDataFlavorSupported(flavor)) {
			throw new UnsupportedFlavorException(flavor);
		}
		return files;
	}
}
