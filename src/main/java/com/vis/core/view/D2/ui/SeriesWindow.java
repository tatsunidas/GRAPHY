package com.vis.core.view.D2.ui;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import com.vis.core.ui.dialog.SaveImage;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;

import ij.ImagePlus;
import ij.plugin.FolderOpener;

@SuppressWarnings("serial")
public class SeriesWindow extends javax.swing.JFrame implements java.awt.event.WindowListener{
	
	//debug
	public static void main(String[] args) {
		String dir = "C:\\Users\\ユーザー\\Desktop\\LGG-104\\06-26-2000-MRI Hd wow-05523\\4-Gad Ax T2 Straight-38151";
		Praparat prap = new Praparat(FolderOpener.open(dir), java.awt.Color.CYAN, ViewMode.Normal);
		new SeriesWindow(prap);
	}
	
	Praparat prap;
	
	public SeriesWindow(Praparat prap) {
		super();
		addWindowListener(this);
		this.prap = prap;
		setTitle("Series Window");
		setMenu();
		add(prap, java.awt.BorderLayout.CENTER);
//		setMinimumSize(new Dimension(30,30));
		setSize(512,512);
		setPreferredSize(new Dimension(512, 512));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}

	private void setMenu() {
		JMenuBar menu = new JMenuBar();
		JMenu mnFile = new JMenu("File");
		JMenuItem mntmSaveNew = new JMenuItem("Save as ...");
		mntmSaveNew.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(prap == null) {
					return;
				}
				ImagePlus imp = prap.getImagePlus();
				if(imp != null && imp.getNSlices() > 0) {
					String title = "Images save to..."; 
					String defaultDir = System.getProperty("user.home");
					String defaultName = "SAVE_IMAGES";
					String extensionWithDot = ".tif";
					SaveImage.save(imp, title, defaultDir, defaultName, extensionWithDot);
				}
			}
		});
		mnFile.add(mntmSaveNew);
		menu.add(mnFile);
		setJMenuBar(menu);
	}

	@Override
	public void windowOpened(WindowEvent e) {}

	@Override
	public void windowClosing(WindowEvent e) {
		dispose();
	}

	@Override
	public void windowClosed(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowActivated(WindowEvent e) {}

	@Override
	public void windowDeactivated(WindowEvent e) {}

}
