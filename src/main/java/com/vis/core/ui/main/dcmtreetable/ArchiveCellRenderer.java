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
package com.vis.core.ui.main.dcmtreetable;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import com.vis.configuration.Resources;
import com.vis.core.task.Task;
import com.vis.core.task.TaskContext;
import com.vis.core.task.TaskManager;
import com.vis.core.task.TaskType;
import com.vis.core.task.context.ImportingStateContext;
import com.vis.core.ui.MissingIcon;
import com.vis.core.ui.main.QRUtil;

/**
 * 
 * @author tatsunidas
 *
 */
public class ArchiveCellRenderer extends JPanel implements TableCellRenderer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 287081234892957526L;
	
	final boolean isRemote;

	private final JButton button;
	private final JProgressBar progressBar;
	private final JPanel empty = new JPanel();
	
	final ImageIcon qrReadyIcon;
	final ImageIcon localIcon;
	
	DICOMTreeTable treeTable;
	DICOMNode node;
	
	//now not using...
//	ImageIcon linkIcon= Resources.LinkIcon.loadIconFromResource();
//	ImageIcon mergeIcon;
	/*
	 * //merge icon
	 * ImagePlus localImg = new ImagePlus("", Resources.ArchivedIcon.loadIconFromResource().getImage());
	 * ImagePlus linkImg = new ImagePlus("", Resources.LinkIcon.loadIconFromResource().getImage());
	 * BufferedImage merge = ImageUtils.merge(localImg, linkImg);
	 * ImageIcon mergeIcon = new ImageIcon(merge);
	 */
	
	public ArchiveCellRenderer(boolean isRemote) {
		this.isRemote = isRemote;
		//this required treetable.setRowHeight(--).
		setLayout(new CardLayout());
		button = new JButton();
		progressBar = new JProgressBar(Integer.MIN_VALUE, Integer.MAX_VALUE);
		progressBar.setStringPainted(true);
		add(button, "Button");
       add(progressBar, "Progress");
       add(empty, "Empty");
		//load icons
		localIcon = Resources.ArchivedIcon.loadIconFromResource();
		qrReadyIcon = Resources.QR_Ready_Icon.loadIconFromResource();
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		DICOMTreeTable treeTable = (DICOMTreeTable)table;
		node = treeTable.nodeForRow(row);
		if(node == null) {
			((CardLayout) getLayout()).show(this, "Empty");
			return this;
		}
		
		Task t = getTaskTypeImportByCellLocationAt(node);
		ImportingStateContext isc = null;
		if(t != null) {
			isc = (ImportingStateContext)t.getContext();
		}
		
		if(isc != null) {
			progressBar.setMinimum(0);
			progressBar.setMaximum(isc.totalSize());
			progressBar.setValue((Integer) isc.currentIndex()+1);//1 base for progress bar
			((CardLayout) getLayout()).show(this, "Progress");
		}else {
			reset(isRemote, null);
		}
		return this;
	}
	
	private Task getTaskTypeImportByCellLocationAt(DICOMNode node ) {
		if(node.getLevel()==DICOMNode.STUDY) {
			TaskManager tm = TaskManager.getInstance();
			HashMap<Long, Task> tasks = tm.getAllTask();
			for (long tid : tasks.keySet()) {
				Task t = tasks.get(tid);
				TaskContext con = t.getContext();
				if (con instanceof ImportingStateContext && con.getType()==TaskType.TypeImport) {
					ImportingStateContext isc = (ImportingStateContext) con;
					if(isc.getThreadId() == tid) {
						Thread thr = tm.getThread(tid);
						if(t != null && thr != null && thr.isAlive() && isc.getStudyUID().equals(node.getData(DICOMNode.StudyInstanceUID))) {
							return t;
						}
					}
				}
			}
		}
		return null;
	}
	
	private void reset(boolean isRemote, Boolean dummy) {
		if(!isRemote) {//HOME
			if (node.getLevel() == DICOMNode.STUDY || node.getLevel() == DICOMNode.SERIES) {
				reset(false);
			} else if (node.getLevel() == DICOMNode.IMAGE){
				if (QRUtil.inLocalInstance(node)) {
					reset(false);
				} else {//missing
					button.setIcon(new MissingIcon(Color.red, treeTable.getRowHeight(), treeTable.getRowHeight()));
					button.setEnabled(false);
					((CardLayout) getLayout()).show(this, "Button");
				}
			}
		}else {//REMOTE
			if (node.getLevel() == DICOMNode.STUDY) {
				if (QRUtil.archivedInLocalAllInstance(node)) {
					reset(false);
				} else {
					reset(true);
				}
			} else if (node.getLevel() == DICOMNode.SERIES) {
				if (QRUtil.archivedInAllInstancesRelatedSeries(node)) {
					reset(false);
				} else {
					reset(true);
				}
			} else if (node.getLevel() == DICOMNode.IMAGE){
				if (QRUtil.inLocalInstance(node)) {
					reset(false);
				} else {//retrievable
					reset(true);
				}
			}
		}
	}
	
	private void reset(boolean retrievable) {
		if(!retrievable) {
			button.setEnabled(false);
			button.setIcon(localIcon);
		}else {
			button.setEnabled(true);
			button.setIcon(qrReadyIcon);
		}
		((CardLayout) getLayout()).show(this, "Button");
	}
}
