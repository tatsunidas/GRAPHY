package com.vis.core.ui.main.dcmtreetable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Supplier;

public class FileTransferable implements Transferable {

	protected ArrayList<File> files;
	private Supplier<ArrayList<File>> fileSupplier;
	private boolean prepared = false;

	public FileTransferable(ArrayList<File> files) {
		this.files = files;
		this.prepared = true;
	}

	/**
	 * Lazy constructor: file preparation is deferred until getTransferData() is called.
	 * This improves drag initiation responsiveness by avoiding heavy I/O during dragGestureRecognized().
	 */
	public FileTransferable(Supplier<ArrayList<File>> fileSupplier) {
		this.fileSupplier = fileSupplier;
		this.prepared = false;
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
		if (!prepared && fileSupplier != null) {
			files = fileSupplier.get();
			prepared = true;
		}
		return files;
	}
}
