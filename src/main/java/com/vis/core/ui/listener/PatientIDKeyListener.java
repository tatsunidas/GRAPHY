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
package com.vis.core.ui.listener;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashMap;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JTextField;

import com.vis.db.DatabaseHandler;

public class PatientIDKeyListener implements KeyListener {

	private JTextField pidField;
	private JTextField pnameField;
	private JTextField dobField;
	private ButtonGroup btnGroupSex;

	public PatientIDKeyListener(JTextField pidField, JTextField pnameField, JTextField dobField,
			ButtonGroup btnGroupSex) {
		this.pidField = pidField;
		this.pnameField = pnameField;
		this.dobField = dobField;
		this.btnGroupSex = btnGroupSex;
	}

	HashMap<String, String> searchInDB(String pid) {
		return DatabaseHandler.getInstance().getPatientInfoByPatID(pid);
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if (pidField != null) {
			String pid = pidField.getText();
			if (pid != null) {
				HashMap<String, String> info = searchInDB(pid);
				if (info != null) {
					pnameField.setText(info.get("PatientName"));
					dobField.setText(info.get("PatientBirthDate"));
					String sex = info.get("PatientSex");
					if (sex != null) {
						while (btnGroupSex.getElements().asIterator().hasNext()) {
							AbstractButton currentButton = btnGroupSex.getElements().asIterator().next();
							if (sex.equals("M")) {
								if (currentButton.getActionCommand().equals("Male")) {
									btnGroupSex.setSelected(currentButton.getModel(), true);
									return;
								}
							} else if (sex.equals("F")) {
								if (currentButton.getActionCommand().equals("Female")) {
									btnGroupSex.setSelected(currentButton.getModel(), true);
									return;
								}
							} else {
								if (currentButton.getActionCommand().equals("Other")) {
									btnGroupSex.setSelected(currentButton.getModel(), true);
									return;
								}
							}
						}
					}
				}
			}
		}
	}
}
