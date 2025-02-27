package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.DefaultCellEditor;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.task.TaskContext;
import com.vis.core.task.TaskManager;
import com.vis.core.task.context.ImportingStateContext;
import com.vis.core.util.ImageUtils;
import com.vis.db.DatabaseHandler;

import ij.ImagePlus;

public class ArchiveCellRendererableEditor extends DefaultCellEditor implements TableCellRenderer, TableCellEditor{

	private static final long serialVersionUID = -4324960997246613616L;
	
	private JLabel localLabel = new JLabel("",SwingConstants.CENTER);
	private JLabel linkLabel = new JLabel("",SwingConstants.CENTER);
	private JLabel bothLabel = new JLabel("",SwingConstants.CENTER);
	DICOMTreeTable homeTable;
	
	public ArchiveCellRendererableEditor(JTextField holder) {
		super(new JTextField());
		localLabel.setIcon(Resources.ArchivedIcon.loadIconFromResource());
		localLabel.setEnabled(false);// NEED to avoid make editable cell
		linkLabel.setIcon(Resources.LinkIcon.loadIconFromResource());
		linkLabel.setEnabled(false);
		//merge icon
		ImagePlus localImg = new ImagePlus("", Resources.ArchivedIcon.loadIconFromResource().getImage());
		ImagePlus linkImg = new ImagePlus("", Resources.LinkIcon.loadIconFromResource().getImage());
		BufferedImage merge = ImageUtils.merge(localImg, linkImg);
		bothLabel.setIcon(new ImageIcon(merge));
		bothLabel.setEnabled(false);
	}
	
	@Override
	public Component getTableCellRendererComponent(JTable table, Object obj, boolean selected, boolean focused, int row,
			int col) {
		if(this.homeTable == null) {
			this.homeTable = WindowManager.getMainScreen().getLocalTreeTable();
		}
		// get studyuid at this row
		DICOMNode node = homeTable.nodeForRow(row);
		if (node == null) {
			return (Component) obj;
		}
		// only show label
		if (node.getLevel() == DICOMNode.IMAGE) {
			if (DatabaseHandler.getInstance().isInstanceSavedAsLink(node.getData(DICOMNode.PatientID),
					node.getData(DICOMNode.StudyInstanceUID), node.getData(DICOMNode.SeriesInstanceUID),
					node.getData(DICOMNode.SOPInstanceUID))) {
				return linkLabel;
			}
			return localLabel;
		}
		// only show label
		if (node.getLevel() == DICOMNode.SERIES) {
			String pid = node.getData(DICOMNode.PatientID);
			String studyUid = node.getData(DICOMNode.StudyInstanceUID);
			String seriesUid = node.getData(DICOMNode.SeriesInstanceUID);
			ArrayList<String> insts = DatabaseHandler.getInstance().getInstanceUidList(pid, studyUid, seriesUid);
			boolean foundSaveAsLink = false;
			boolean foundSaveAsLocal = false;
			for (String ins : insts) {
				boolean isLink = DatabaseHandler.getInstance().isInstanceSavedAsLink(pid, studyUid, seriesUid, ins);
				if (isLink) {
					foundSaveAsLink = true;
				} else {
					foundSaveAsLocal = true;
				}
			}
			if (foundSaveAsLocal && !foundSaveAsLink) {
				return localLabel;
			}
			if (!foundSaveAsLocal && foundSaveAsLink) {
				return linkLabel;
			}
			if (foundSaveAsLocal && foundSaveAsLink) {
				return bothLabel;
			}
		}
		// show progress and label
		if (node.getLevel() == DICOMNode.STUDY) {
			String pid = node.getData(DICOMNode.PatientID);
			String studyUid = node.getData(DICOMNode.StudyInstanceUID);

			TaskContext con = getContextByCellLocationAt(row);
			if (con != null) {
				return con.getCellRenderableComponent();
			}

			ArrayList<String> series = DatabaseHandler.getInstance().getSeriesUidList(pid, studyUid);
			boolean foundSaveAsLink = false;
			boolean foundSaveAsLocal = false;
			for (String se : series) {
				ArrayList<String> insts = DatabaseHandler.getInstance().getInstanceUidList(pid, studyUid, se);
				for (String ins : insts) {
					boolean isLink = DatabaseHandler.getInstance().isInstanceSavedAsLink(pid, studyUid, se, ins);
					if (isLink) {
						foundSaveAsLink = true;
					} else {
						foundSaveAsLocal = true;
					}
				}
			}
			if (foundSaveAsLocal && !foundSaveAsLink) {
				return localLabel;
			}
			if (!foundSaveAsLocal && foundSaveAsLink) {
				return linkLabel;
			}
			if (foundSaveAsLocal && foundSaveAsLink) {
				return bothLabel;
			}
		}
		return (Component) obj;
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object obj, boolean selected, int row, int col) {
		if(this.homeTable == null) {
			this.homeTable = WindowManager.getMainScreen().getLocalTreeTable();
		}
		//get studyuid at this row
		DICOMNode node = homeTable.nodeForRow(row);
		if(node == null) {
			return (Component)obj;
		}
		//only show label
		if(node.getLevel() == DICOMNode.IMAGE) {
			if (DatabaseHandler.getInstance().isInstanceSavedAsLink(node.getData(DICOMNode.PatientID),
					node.getData(DICOMNode.StudyInstanceUID), node.getData(DICOMNode.SeriesInstanceUID),
					node.getData(DICOMNode.SOPInstanceUID))) {
				return linkLabel;
			}
			return localLabel;
		}
		//only show label
		if(node.getLevel() == DICOMNode.SERIES) {
			String pid = node.getData(DICOMNode.PatientID);
			String studyUid = node.getData(DICOMNode.StudyInstanceUID);
			String seriesUid = node.getData(DICOMNode.SeriesInstanceUID);
			ArrayList<String> insts = DatabaseHandler.getInstance().getInstanceUidList(pid, studyUid, seriesUid);
			boolean foundSaveAsLink = false;
			boolean foundSaveAsLocal = false;
			for(String ins : insts) {
				boolean isLink = DatabaseHandler.getInstance().isInstanceSavedAsLink(pid, studyUid, seriesUid,ins);
				if(isLink) {
					foundSaveAsLink = true;
				}else {
					foundSaveAsLocal = true;
				}
			}
			if(foundSaveAsLocal && !foundSaveAsLink) {
				return localLabel;
			}
			if(!foundSaveAsLocal && foundSaveAsLink) {
				return linkLabel;
			}
			if(foundSaveAsLocal && foundSaveAsLink) {
				return bothLabel;
			}
		}
		//show progress and label
		if(node.getLevel() == DICOMNode.STUDY) {
			String pid = node.getData(DICOMNode.PatientID);
			String studyUid = node.getData(DICOMNode.StudyInstanceUID);
			
			TaskContext con = getContextByCellLocationAt(row);
			if(con != null) {
				return con.getCellRenderableComponent();
			}
			
			ArrayList<String> series = DatabaseHandler.getInstance().getSeriesUidList(pid, studyUid);
			boolean foundSaveAsLink = false;
			boolean foundSaveAsLocal = false;
			for(String se : series) {
				ArrayList<String> insts = DatabaseHandler.getInstance().getInstanceUidList(pid, studyUid, se);
				for (String ins : insts) {
					boolean isLink = DatabaseHandler.getInstance().isInstanceSavedAsLink(pid, studyUid, se, ins);
					if (isLink) {
						foundSaveAsLink = true;
					} else {
						foundSaveAsLocal = true;
					}
				}
			}
			if(foundSaveAsLocal && !foundSaveAsLink) {
				return localLabel;
			}
			if(!foundSaveAsLocal && foundSaveAsLink) {
				return linkLabel;
			}
			if(foundSaveAsLocal && foundSaveAsLink) {
				return bothLabel;
			}
		}
		return (Component)obj;
	}

	
	/**
	 * ArchiveCellRenderableEditor was set to DICOMTreeTable Archive Column.
	 * @param row
	 * @return
	 */
	private ImportingStateContext getContextByCellLocationAt(int row ) {
		DICOMNode node = homeTable.nodeForRow(row);
		if(node.getLevel()==DICOMNode.STUDY) {
			TaskManager tm = TaskManager.getInstance();
			HashMap<Long, TaskContext> tasks = tm.getAllTask();
			for (long tid : tasks.keySet()) {
				TaskContext con = tasks.get(tid);
				if (con instanceof ImportingStateContext) {
					ImportingStateContext isc = (ImportingStateContext) con;
					Thread t = tm.getThreadInCurrentThreads(tid);
					if(t != null && t.isAlive() && isc.getStudyUID().equals(node.getData(DICOMNode.StudyInstanceUID))) {
						return isc;
					}
				}
			}
		}
		return null;
	}
}
