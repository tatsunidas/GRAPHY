package com.vis.core.ui.main.dcmtreetable;

import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.main.MainScreen;

/*
 * update current anchor treetable,
 */
public class QRTreeTableUpdater extends Timer{
	
	public QRTreeTableUpdater() {
		super();
		start(3000,20000);
	}
	
	public void start(long delay, long period) {
		super.scheduleAtFixedRate(new QRTreeTableUpdateTask(), delay, period);
	}
	
	public class QRTreeTableUpdateTask extends TimerTask{
		
		public QRTreeTableUpdateTask() {
			super();
		}
		
		public void updateQRTreeTable(){
			System.out.println(" QRUpdater is Running ");
			MainScreen mainSc = WindowManager.getMainScreen();
			if(mainSc.getCurrentTreeTableManager() == null) {
				return;
			}
			String anchorTreeTableTitle = mainSc.getCurrentTreeTableManager().getCurrentAnchorTitle();
			/*update only QR treetable*/
			if(!anchorTreeTableTitle.equals("HOME")) {
				mainSc.getMainSearchToolBar().searchDBOnCurrentConditions();
			}
		}
		
		@Override
		public void run() {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					updateQRTreeTable();
				}
			});
		}
	}
}
