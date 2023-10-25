package com.vis.core.view.D2.ui;

import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;


public class FloatingDialogWindowListener extends ComponentAdapter {
	public void componentResized(ComponentEvent e) {
		int w = e.getComponent().getWidth();
		int h = e.getComponent().getHeight();
		JDialog floatingDialog = (JDialog) e.getSource();
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				floatingDialog.setPreferredSize(new Dimension(w, h));
				floatingDialog.repaint();
			}
		});

	}
}
