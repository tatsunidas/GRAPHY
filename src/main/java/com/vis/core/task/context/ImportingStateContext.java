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
package com.vis.core.task.context;

import java.awt.Color;
import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.OverlayLayout;

import com.vis.core.facade.WindowManager;
import com.vis.core.task.TaskContext;
import com.vis.core.ui.function.DicomImporter;
import com.vis.core.ui.main.QueryRetrieve;

/**
 * TaskContext, such as progress state, component, and so on.
 * to show progressbar on treetable in study level.
 * @author tatsunidas
 *
 */
public class ImportingStateContext implements TaskContext{
	
	JProgressBar importingBar = new JProgressBar();
	long threadId;
	int currentIndex=0;//progress
	int total;
	/*
	 * studyUid is used for unique key.
	 * if perform multiple importer tasks at same time,
	 * and these have same studyuid,
	 * randomly show progressbar in contexts.
	 */
	String suid;//
	DicomImporter importer;//Task
	QueryRetrieve qrTask;//Task TODO
	
	public ImportingStateContext(String studyInstanceUID, DicomImporter importer) {
		this.suid = studyInstanceUID;
		this.importer = importer;
		/**
		 * Suspend btn behaves as textLabel and transparent btn.
		 */
		JButton suspendBtn = new JButton();
		suspendBtn.setIcon(null);
		suspendBtn.setOpaque(false);
		suspendBtn.setContentAreaFilled(false);
		suspendBtn.setBorderPainted(false);
		suspendBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		importingBar = new JProgressBar();
		LayoutManager overlay = new OverlayLayout(importingBar);
		importingBar.setLayout(overlay);
		total = importer.totalSize();
		importingBar.setMaximum(total);
		importingBar.setValue(0);
		/*
		 * if Bar.setStringPainted(true),
		 * can not update treetable cell at the end of task.
		 */
		importingBar.setStringPainted(false);//do not show percentage
		importingBar.setForeground(Color.CYAN);
		importingBar.add(suspendBtn);
		suspendBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// suspend current import thread
				suspend();
				int res = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "Would you cancel import ?", "Cancel Importing",
						JOptionPane.YES_NO_OPTION);
				if (res == JOptionPane.YES_OPTION) {
					// stop
					stop();
				} else {
					// resume
					resume();
				}
			}
		});
	}
	
	public ImportingStateContext(String studyUID, int total, QueryRetrieve qrTask) {
		this.qrTask = qrTask;
		/*
		 * this cancel button rendered as cancel button by CellEditor.
		 */
		JButton suspendBtn = new JButton();
		suspendBtn.setIcon(null);
		suspendBtn.setText("importing");
		suspendBtn.setOpaque(false);
		suspendBtn.setContentAreaFilled(false);
		suspendBtn.setBorderPainted(false);
		suspendBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		importingBar = new JProgressBar();
		LayoutManager overlay = new OverlayLayout(importingBar);
		importingBar.setLayout(overlay);
		this.total = total;
		importingBar.setMaximum(total);
		importingBar.setValue(0);
		importingBar.setIndeterminate(true);
		importingBar.add(suspendBtn);
	}
	
	private JProgressBar getProgressBar() {
		return importingBar;
	}

	public String getStudyUID() {
		return suid;
	}

	@Override
	public Component getCellRenderableComponent() {
		return getProgressBar();
	}
	
	@Override
	public void updateState(HashMap<String, Object> obj) {
		Object currentInd = obj.get("CurrentIndex");
		if(currentInd != null) {
			currentIndex = (Integer)currentInd;
		}
		JProgressBar progress = getProgressBar();
		progress.setValue(currentIndex);
		int progressCount = currentIndex()+1;//progressbar is start from 1.
		JButton susbtn = (JButton) getProgressBar().getComponent(0);
		susbtn.setText(progressCount + " / " + maxSize());
		susbtn.repaint();
		getProgressBar().repaint();
	}

	@Override
	public int currentIndex() {
		return currentIndex;
	}

	@Override
	public int maxSize() {
		return total;
	}

	@Override
	public long getThreadId() {
		return threadId;
	}

	@Override
	public void setThreadId(final long tid) {
		this.threadId = tid;
	}

	@Override
	public void suspend() {
		importer.setSuspended(true);
	}

	@Override
	public void resume() {
		importer.resume();
	}

	@Override
	public void stop() {
		importer.setStopped(true);
	}
}
