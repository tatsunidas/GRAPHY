package com.vis.core.ui.main;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/*
 * for patient id query to DB.
 */
public class TextSearchListener implements DocumentListener {

	JTextField patID;
	SearchKeyPanel ts;

	public TextSearchListener(JTextField keyText, SearchKeyPanel ts) {
		this.patID = keyText;
		this.ts = ts;
	}

	@Override
	public void changedUpdate(DocumentEvent arg0) {
		return;
//		throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
//																		// Tools | Templates
	}

	@Override
	public void insertUpdate(DocumentEvent arg0) {
		queryAndUpdateTreeTable();
	}

	@Override
	public void removeUpdate(DocumentEvent arg0) {
		queryAndUpdateTreeTable();
	}

	/*
	 * study level search
	 */
	void queryAndUpdateTreeTable() {
		String pid = patID.getText().trim();
		if (pid != null) {
			ts.searchDBUsingThisConditions();
		}
	}
}
