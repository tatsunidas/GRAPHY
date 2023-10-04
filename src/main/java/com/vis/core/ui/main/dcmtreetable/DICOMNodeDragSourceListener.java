package com.vis.core.ui.main.dcmtreetable;

import java.awt.Cursor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;

import com.vis.core.log.Log;
import com.vis.core.util.Utils;

/*
 * For drag export.
 * Used with DragGestureListener
 */
public class DICOMNodeDragSourceListener implements DragSourceListener{
	
	protected Cursor getCursorFromAction(int userAction, int dropAction) {
		boolean accepted = (userAction == dropAction);
		Cursor cursor = null;
		switch (userAction) {
		case DnDConstants.ACTION_MOVE:
			cursor = (accepted ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop);
			break;
		case DnDConstants.ACTION_COPY:
			cursor = (accepted ? DragSource.DefaultCopyDrop : DragSource.DefaultCopyNoDrop);
			break;
		case DnDConstants.ACTION_LINK:
			cursor = (accepted ? DragSource.DefaultLinkDrop : DragSource.DefaultLinkNoDrop);
			break;
		}
		return cursor;
	}

	protected void displayDragOverEffects(DragSourceDragEvent evt) {
//		Cursor cursor = getCursorFromAction(evt.getUserAction(), evt.getDropAction());
//		evt.getDragSourceContext().setCursor(cursor);
	}

	@Override
	public void dragEnter(DragSourceDragEvent dsde) {
		displayDragOverEffects(dsde);
	}

	@Override
	public void dragOver(DragSourceDragEvent dsde) {
		displayDragOverEffects(dsde);
	}

	@Override
	public void dropActionChanged(DragSourceDragEvent dsde) {
		displayDragOverEffects(dsde);
	}
	

	@Override
	public void dragDropEnd(DragSourceDropEvent arg0) {
		if(Utils.isDebug) {
			Log.logger.info("DICOMNodeDragSourceListener::dragDropEnd:Drop performed!!");
		}
		
		/**
		 * TODO 20230825
		 */
		//ApplicationContext.treeNodeDragging4Export = false;
		
		/*
		 * tmp fileを空にする？
		 * 
		 * いや、ドロップの処理が重かったら、
		 * 終了しないうちに削除操作を実行することになるかもしれない。
		 * ここでは何もせず、GRAPHY終了時に空にしよう。
		 * 
		 */
	}

	@Override
	public void dragExit(DragSourceEvent arg0) {}
}
