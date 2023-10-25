package com.vis.core.ui.settings;

import javax.swing.JPanel;

import java.awt.BorderLayout;

public class GeneralPrefsBase extends JPanel{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 5903200709323280116L;
	
	public GeneralPrefsBase() {
		setLayout(new BorderLayout(0, 0));
		
		JPanel centerPanel = new JPanel();
		add(centerPanel);
		centerPanel.setLayout(new BorderLayout(0, 0));
		
		GeneralPrefs general = new GeneralPrefs();
		centerPanel.add(general);
		
		JPanel futurePanel = new JPanel();
		add(futurePanel, BorderLayout.SOUTH);
		futurePanel.setLayout(new BorderLayout(0, 0));
		
		JPanel southPanel = new JPanel();
		add(southPanel, BorderLayout.NORTH);
		southPanel.setLayout(new BorderLayout(0, 0));
		LocalDBPrefs localdb = new LocalDBPrefs();
		southPanel.add(localdb);
		
	}
}
