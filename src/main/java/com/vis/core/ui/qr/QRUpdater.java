/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.ui.main;

import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;

import com.vis.core.facade.WindowManager;
import com.vis.core.util.Utils;

/**
 * update current anchor treetable.
 * @author tatsunidas
 */
public class QRUpdater extends Timer {
	
	public static long delay = 3000l;//3 seconds
	public static long period = 20000l;//20 seconds

	public QRUpdater() {
		super();
	}
	
	public void start() {
		super.scheduleAtFixedRate(new TreeTableUpdateTask(), delay, period);
	}

	public void start(long delay, long period) {
		super.scheduleAtFixedRate(new TreeTableUpdateTask(), delay, period);
	}

	class TreeTableUpdateTask extends TimerTask {

		public TreeTableUpdateTask() {
			super();
		}

		private void updateTreeTable() {
			if (Utils.isDebug) {
				System.out.println(" TreeTableUpdater is Running ");
			}
			new Thread(() -> {
				MainScreen mainSc = WindowManager.getMainScreen();
				if(mainSc != null && mainSc.isVisible()) {
					mainSc.searchCurrentConditions();
				}
			}).start();
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
