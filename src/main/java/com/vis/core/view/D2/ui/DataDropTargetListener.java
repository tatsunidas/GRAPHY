package com.vis.core.view.D2.ui;

import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.util.List;

import com.vis.core.view.D2.ui.glasses.Eyepiece;

public class DataDropTargetListener implements DropTargetListener{
	
	Eyepiece eye;
	
	public DataDropTargetListener(Eyepiece eye) {
		this.eye = eye;
		// set drop target
		new DropTarget(eye, DnDConstants.ACTION_COPY, this, true, null);
	}
	
	@Override
	public void dragEnter(DropTargetDragEvent e) {
		if(e.isDataFlavorSupported(UIDTransferable.uidflavor)) {
			System.out.println("Drag entered");
			//do something...
			return;
		}
		e.rejectDrag();
	}

	@Override
	public void dragExit(DropTargetEvent e) {}

	@Override
	public void dragOver(DropTargetDragEvent e) {}

	@Override
	public void drop(DropTargetDropEvent e) {
		System.out.println("dropped at : x:"+e.getLocation().x+" y:"+e.getLocation().y);//eyepiece-origin base.
		e.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
		try {
//			TblTransferable t = (TblTransferable)e.getTransferable();//java.lang.ClassCastException: or Serialize exception...
			Transferable t = e.getTransferable();
			Object df = t.getTransferData(UIDTransferable.uidflavor);
			//uids from dragged on serieslist
			@SuppressWarnings("unchecked")
			List<Object> uids = (List<Object>)df;
			String patID = (String)uids.get(0);
			String studyUID = (String)uids.get(1);
			String seriesUID = (String)uids.get(2);
			String[] sopUIDs = (String[])uids.get(3);
			String refUID = (String)uids.get(4);
			//seriesUID null, return
			if(seriesUID == null) {
				System.out.println("Dropped object does not have seriesUID.");
				return;
			}
			eye = Viewer2DScreen.getInstance().getEyepieceOnStageWhere((String)uids.get(0));
			if(eye != null) {
				com.vis.core.view.D2.ui.glasses.Praparat prap = eye.getPraparatOnEyeAt(e.getLocation());
				Viewer2DScreen.getInstance().getStageViewAt(patID).updatePraparatOnEye(prap, patID, studyUID, seriesUID, sopUIDs, refUID);
			}
		} catch (Exception ex) {
			System.out.println("drop failed, something wrong...");
			System.out.println(ex);
		}
	}

	@Override
	public void dropActionChanged(DropTargetDragEvent e) {}

}