package com.vis.core.view.D2.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

import javax.swing.JTable;

class UIDTransferable implements Transferable{
	
	public static final DataFlavor uidflavor = new DataFlavor(List.class, "uid");
	DataFlavor[] flavors = {uidflavor};
	List<Object> uids;
	
	public UIDTransferable(List<Object> uids) {
		this.uids = uids;
	}

	@Override
	public Object getTransferData(DataFlavor fla) throws UnsupportedFlavorException, IOException {
		if(fla.equals(uidflavor)) {
			return uids;
		}
		return null;
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return flavors;
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor fla) {
		for(DataFlavor df:flavors) {
			if(df.equals(fla)) {
				return true;
			}
		}
		return false;
	}
	
}
