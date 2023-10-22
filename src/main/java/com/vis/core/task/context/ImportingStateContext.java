package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;
import java.awt.LayoutManager;

import javax.swing.JButton;
import javax.swing.JProgressBar;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;

import com.vis.core.ui.dialog.DicomImporterDialog;
import com.vis.core.ui.function.DicomImporter;
import com.vis.core.ui.main.QueryRetrieve;

//import com.vis.dimse.delegate.QueryRetrieve;
//import com.vis.ui.form.dialog.DicomImporter;

/**
 * Show progressbar on treetable by study level.
 * @author tatsunidas
 *
 */
public class ImportingStateContext{
	
	JButton suspendBtn = new JButton();
	JProgressBar importingBar = new JProgressBar();
	int importingRow=0;
	int importingCol=0;
	int total;
	String suid;
	String[] infoset;//4 uids
	DicomImporter importer;//for import
	QueryRetrieve qrTask;
	
	public ImportingStateContext(String studyInstanceUID, DicomImporter importer) {
		this.suid = studyInstanceUID;
		this.importer = importer;
		/*
		 * this cancel button rendered as cancel button by CellEditor.
		 */
		suspendBtn.setIcon(null);
		suspendBtn.setText("importing");
		suspendBtn.setOpaque(false);
		suspendBtn.setContentAreaFilled(false);
		suspendBtn.setBorderPainted(false);
		suspendBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		importingBar = new JProgressBar();
		LayoutManager overlay = new OverlayLayout(importingBar);
		importingBar.setLayout(overlay);
//		importingBar.setMaximum(total);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				importingBar.setValue(0);
				importingBar.setIndeterminate(true);
			}
		});
		importingBar.add(suspendBtn);
	}
	
	/*
	 * infoset, 大は小をかねるで、
	 * スタディレベルでしか描画しないので、
	 * SOPIUIDは不要だけれど、何かに使うかもしれないので。
	 */
	public ImportingStateContext(String[] infoset, QueryRetrieve qrTask) {
		this.infoset = infoset;
		this.qrTask = qrTask;
		/*
		 * this cancel button rendered as cancel button by CellEditor.
		 */
		suspendBtn.setIcon(null);
		suspendBtn.setText("importing");
		suspendBtn.setOpaque(false);
		suspendBtn.setContentAreaFilled(false);
		suspendBtn.setBorderPainted(false);
		suspendBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		importingBar = new JProgressBar();
		LayoutManager overlay = new OverlayLayout(importingBar);
		importingBar.setLayout(overlay);
//		importingBar.setMaximum(total);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				importingBar.setValue(0);
				importingBar.setIndeterminate(true);
			}
		});
		importingBar.add(suspendBtn);
	}
	
	JButton getSuspendButton() {
		return suspendBtn;
	}
	JProgressBar getProgressBar() {
		return importingBar;
	}

	int getImportingRow() {
		return importingRow;
	}

	void setImportingRow(int importingRow) {
		this.importingRow = importingRow;
	}

	int getImportingCol() {
		return importingCol;
	}

	void setImportingCol(int importingCol) {
		this.importingCol = importingCol;
	}

	int getTotal() {
		return total;
	}

	void setTotal(int total) {
		this.total = total;
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				importingBar.setMaximum(total);
			}
		});
	}

	String getSuid() {
		return suid;
	}

	void setSuid(String suid) {
		this.suid = suid;
	}
	
	public String[] getInfoSet() {
		return infoset;
	}
	
	DicomImporter getDicomImporter() {
		return importer;
	}
	
	QueryRetrieve getQueryRetrieve() {
		return qrTask;
	}
}
