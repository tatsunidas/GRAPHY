package com.vis.core.view.D2.ui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.SwingUtilities;

public class ListTableMouseListener implements MouseListener, MouseMotionListener{

	@Override
	public void mouseDragged(MouseEvent arg0) {
		//future work
	}

	@Override
	public void mouseMoved(MouseEvent arg0) {}

	@Override
	public void mouseClicked(MouseEvent e) {
		if(e.getClickCount() >= 2 && SwingUtilities.isLeftMouseButton(e)) {
			System.out.println("clicked twice...Open new images");
			Object target = e.getSource();
			if(target instanceof SeriesListTable) {
				SeriesListTable tbl = (SeriesListTable)target;
				tbl.requestOpenImage(tbl.getSelectedRow());
			}else if(target instanceof ImageListTable) {
				
			}
		}
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
//		System.out.println("Entered");
	}

	@Override
	public void mouseExited(MouseEvent arg0) {}

	@Override
	public void mousePressed(MouseEvent arg0) {}

	@Override
	public void mouseReleased(MouseEvent arg0) {}

}
