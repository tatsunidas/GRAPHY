package com.vis.core.view.D2.ui;

import java.awt.Cursor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTable;

public class DnDGesture4ListTable implements DragGestureListener, DragSourceListener{
	
    private JTable tbl;
    private List<Object> uids = null;

	public DnDGesture4ListTable(JTable tbl) {
		this.tbl = tbl;
	}

	@Override
	public void dragGestureRecognized(DragGestureEvent event) {
		Object dragObj = event.getComponent();
		System.out.println("dragGestureRecognized: " + dragObj.getClass().getName());
		// Create our transferable wrapper
		uids = new ArrayList<Object>();
		if (this.tbl instanceof SeriesListTable) {
			SeriesListTable stbl = (SeriesListTable)this.tbl;
			uids.add(stbl.getRelatedPatID());
			uids.add(stbl.getRelatedStudyUID());
			uids.add(stbl.getSelectedSeriesUID());
			uids.add(stbl.getCurrentSeriesSopUIDs());//sopUIDs array
		} else if (this.tbl instanceof ImageListTable) {
			// future work
		}
		Transferable transferable = new UIDTransferable(uids);
		// Start the "drag" process...
		Cursor cursor = getCursorFromAction(event.getDragAction(), DnDConstants.ACTION_NONE);// Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
		DragSource ds = event.getDragSource();
		ds.startDrag(event, cursor, transferable, this);
	}
	
	public Cursor getCursorFromAction(int userAction,int dropAction) {
		boolean accepted = true;//(userAction == dropAction);
		Cursor cursor = null;
		switch (userAction) {
		case DnDConstants.ACTION_MOVE:
			cursor = (accepted ? DragSource.DefaultMoveDrop:DragSource.DefaultMoveNoDrop);
			break;
		case DnDConstants.ACTION_COPY:
			cursor = (accepted ? DragSource.DefaultCopyDrop:DragSource.DefaultCopyNoDrop);
			break;
		case DnDConstants.ACTION_LINK:
			cursor = (accepted ? DragSource.DefaultLinkDrop:DragSource.DefaultLinkNoDrop);
			break;
		default:
			break;
		}
		return cursor;
	}

	@Override
	public void dragDropEnd(DragSourceDropEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dragEnter(DragSourceDragEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dragExit(DragSourceEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dragOver(DragSourceDragEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dropActionChanged(DragSourceDragEvent arg0) {
		// TODO Auto-generated method stub
		
	}
}
