package com.vis.core.ui.main.dcmtreetable;

import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.Utils;

/*
 * update current anchor treetable,
 */
public class QRUpdater extends Timer{
	
	public QRUpdater() {
		super();
		start(3000,20000);
	}
	
	public void start(long delay, long period) {
		super.scheduleAtFixedRate(new TreeTableUpdateTask(), delay, period);
	}
	
	class TreeTableUpdateTask extends TimerTask{
		
		public TreeTableUpdateTask() {
			super();
		}
		
		public void updateTreeTable(){
			if(Utils.isDebug) System.out.println(" TreeTableUpdater is Running ");
			MainScreen mainSc = WindowManager.getMainScreen();
			if(mainSc.getCurrentTreeTableManager() == null) {
				return;
			}
			mainSc.getMainSearchToolBar().searchDBOnCurrentConditions();
		}
		
		@Override
		public void run() {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					updateTreeTable();
				}
			});
		}
	}
}
