package com.vis.core.ui.dialog;

import java.io.File;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import com.vis.core.view.D2.ui.glasses.*;

import ij.IJ;
import ij.ImagePlus;

/**
 * Save images as general format.
 * see also DicomWriter, DicomDuplicator
 * @author tatsunidas
 *
 */
public class SaveImage {
	
	static JComponent parent;
	
	public SaveImage() {}
	
	public static void save(ImagePlus imp, String title, String defaultDir, String defaultName, String extensionWithDot) {
		jSaveDispatchThread(imp, title, defaultDir, defaultName, extensionWithDot);
	}
	
	public static void save(Praparat prap, String title, String defaultDir, String defaultName, String extensionWithDot) {
		ImagePlus imp = prap.getImagePlus();
		if(imp != null && imp.getNSlices() > 0) {
			jSaveDispatchThread(imp, title, defaultDir, defaultName, extensionWithDot);
		}
	}
	
	public static void jSaveDispatchThread(ImagePlus imp, String title, String defaultDir, String defaultName, String extensionWithDot) {
		JFileChooser fc = new JFileChooser();
		JPanel p = new JPanel();
		p.setLayout(new java.awt.GridLayout(1,1));
		String msg = "\n The format must be \n tiff, tif, jpeg, jpg, gif, zip, raw, avi, bmp, pgm, png ";
		JTextArea a = new JTextArea(msg);
		a.setBackground(null);
		a.setEditable(false);
		p.add(a);
		fc.setAccessory(p);
		fc.setDialogTitle(title);
		if (defaultDir!=null) {
			File f = new File(defaultDir);
			if (f!=null)
				fc.setCurrentDirectory(f);
		}
		if (defaultName!=null)
			fc.setSelectedFile(new File(defaultName));
		int returnVal = fc.showSaveDialog(parent);
		if (returnVal!=JFileChooser.APPROVE_OPTION) return;
		File f = fc.getSelectedFile();
		if(f.exists()) {
			int ret = JOptionPane.showConfirmDialog (fc,
				"The file "+ f.getName() + " already exists. \nWould you like to replace it?",
				"Replace?",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (ret!=JOptionPane.OK_OPTION) f = null;
		}
		if (f==null)
			return;
		else {
			String dir = fc.getCurrentDirectory().getPath()+File.separator;
			String name = fc.getName(f);//inputed file name on file chooser
			name = name.trim();
			if (noExtension(name)) {
				if (".raw".equals(extensionWithDot))
					extensionWithDot = null;
				name = setExtension(name, extensionWithDot);
				String extension = null;
				if(extensionWithDot != null) {
					extension = extensionWithDot.replace(".", "");
				}
				IJ.saveAs(imp, extension, dir+name);
			}else {
				String newExtension = name.substring(name.lastIndexOf(".")+1);
				IJ.saveAs(imp, newExtension, dir+name);
			}
		}
	}
	
	private static boolean noExtension(String name) {
		if (name==null) return false;
		int dotIndex = name.indexOf(".");
		return dotIndex==-1 || (name.length()-dotIndex)>5;
	}
	
	public static String setExtension(String name, String extension) {
		if (name==null || extension==null || extension.length()==0)
			return name;
		int dotIndex = name.lastIndexOf(".");
		if (dotIndex>=0 && (name.length()-dotIndex)<=5) {
			if (dotIndex+1<name.length() && Character.isDigit(name.charAt(dotIndex+1)))
				name += extension;
			else
				name = name.substring(0, dotIndex) + extension;
		} else if (!name.endsWith(extension))
			name += extension;
		return name;
	}

}
