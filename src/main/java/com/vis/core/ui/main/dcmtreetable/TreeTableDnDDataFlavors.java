package com.vis.core.ui.main.dcmtreetable;

import java.awt.datatransfer.DataFlavor;

public class TreeTableDnDDataFlavors {
	
	public static DataFlavor javaFile = DataFlavor.javaFileListFlavor;//for import
	static DataFlavor localObjectFlavor;
	static {
		try {
			localObjectFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType);
		} catch (ClassNotFoundException e) {

		}
	}
	static DataFlavor[] supportedFlavors = { localObjectFlavor ,javaFile};
}
