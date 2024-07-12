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

package com.vis.core.ui.dialog;

import javax.swing.JDialog;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ImageIcon;
import javax.swing.JButton;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.util.ImageUtils;

/**
 * 
 * @author tatsunidas
 *
 */

public class HelpDialog extends JDialog {

	private static final long serialVersionUID = 2165830563860479431L;

	public HelpDialog() {
		setTitle("Always with your ambition.");
		getContentPane().setLayout(new BorderLayout(0, 0));

		JPanel panel = new JPanel();
		getContentPane().add(panel, BorderLayout.CENTER);
		panel.setLayout(new BorderLayout(0, 0));

		JPanel panel_1 = new JPanel();
		panel.add(panel_1, BorderLayout.SOUTH);

		JLabel lblCompany = new JLabel("Visionary Imaging Services, Inc.");
		panel_1.add(lblCompany);
		ImageIcon ii = Resources.GraphyIcon.loadIconFromResource();
		Image im = ImageUtils.resize(ii.getImage(), 64, 64);
		ii.setImage(im);
		lblCompany.setIcon(ii);

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		panel.add(tabbedPane, BorderLayout.NORTH);

		JPanel panel_2 = new JPanel();
		tabbedPane.addTab("Requests", null, panel_2, null);
		panel_2.setLayout(new BorderLayout(0, 0));

		JTextArea textArea = new JTextArea();
		panel_2.add(textArea);
		String requestsText = "VIS,inc. is developing GRAPHY to help improve/enhance QOL.\n";
		requestsText += "If you are interesting in this activities, contact us with any suggestions or improvements.\n";
		textArea.setText(requestsText);

		JButton btnGoToWeb = new JButton("Go to web user's forum");
		panel_2.add(btnGoToWeb, BorderLayout.NORTH);
		btnGoToWeb.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					Desktop.getDesktop().browse(new URI("https://groups.google.com/g/graphy-users"));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});

		JPanel panel_3 = new JPanel();
		tabbedPane.addTab("HowTos", null, panel_3, null);
		panel_3.setLayout(new BorderLayout(0, 0));

		JTextArea textArea_1 = new JTextArea();
		panel_3.add(textArea_1, BorderLayout.NORTH);
		String line = "You can find some resources on the web,\n";
		line += "GRAPHY User's Group: https://groups.google.com/g/graphy-users\n";
		line += "GRAPHY web page: https://www.vis-ionary.com/graphy-how-to\n";
		line = "YouTube VIS Channels: https://www.youtube.com/@vis-official8769/videos\n";
		textArea_1.setText(line);

		pack();
		setVisible(true);
		
		setLocationRelativeTo(WindowManager.getMainScreen());
	}
}
