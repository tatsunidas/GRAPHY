package com.vis.core.ui.settings;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;

@SuppressWarnings("serial")
public class PreferencesWin extends JFrame implements WindowListener{
		
	private static JTabbedPane tabPane;
	private JScrollPane scrPane;
	ImageIcon settingsIcon;
	private static JFrame prefWin;

	public PreferencesWin() {
		
		prefWin = new JFrame("Preferences");
		setContents();
		
//		URI settingsIconURI = null;
//		try {
//			settingsIconURI = getClass().getResource(settingsIconPath).toURI();
//		} catch (URISyntaxException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		settingsIcon = Toolkit.getDefaultToolkit().createImage(new File(settingsIconPath).getAbsolutePath());
		settingsIcon = Resources.MenuBarSettingsIcon.loadIconFromResource();

		prefWin.setIconImage(settingsIcon.getImage());
		
		prefWin.setMaximumSize(new Dimension(150, 100));
		/* show window */
		prefWin.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		prefWin.setLocationRelativeTo(WindowManager.getMainScreen());
		prefWin.pack();
		prefWin.setVisible(true);
	}

	private void setContents() {
		// TODO Auto-generated method stub
		tabPane = new JTabbedPane();
		/* construct panels */
		/* General */
		GeneralPrefsBase general = new GeneralPrefsBase();
		tabPane.add(general);
		tabPane.setIconAt(0, Resources.PrefsIcon.loadIconFromResource());
		/* PACS Nodes */
		PACSConnectionPrefs pacsPref = new PACSConnectionPrefs();
		tabPane.add(pacsPref);
		tabPane.setIconAt(1, Resources.PrefsPACSIcon.loadIconFromResource());

		/* Roi Prefs */
		RoiPrefs roiPref = new RoiPrefs();
		tabPane.add(roiPref);
		tabPane.setIconAt(2, Resources.PrefsROIIcon.loadIconFromResource());
		scrPane = new JScrollPane(tabPane);
		prefWin.getContentPane().add(scrPane);
		
	}
	
	public static void refreshOwnLookAndFeels() {
		if(prefWin != null) {
			SwingUtilities.updateComponentTreeUI(prefWin);
		}
	}

	@Override
	public void windowActivated(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}
}
