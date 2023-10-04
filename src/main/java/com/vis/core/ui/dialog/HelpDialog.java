package com.vis.core.ui.dialog;

import javax.swing.JDialog;
import java.awt.BorderLayout;
import java.net.URISyntaxException;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

import com.vis.configuration.Resources;

import javax.swing.JButton;

public class HelpDialog extends JDialog{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2165830563860479431L;

	public HelpDialog() throws URISyntaxException {
		setTitle("Always with your ambition.");
		getContentPane().setLayout(new BorderLayout(0, 0));
		
		JPanel panel = new JPanel();
		getContentPane().add(panel, BorderLayout.CENTER);
		panel.setLayout(new BorderLayout(0, 0));
		
		JPanel panel_1 = new JPanel();
		panel.add(panel_1, BorderLayout.SOUTH);
		
		JLabel lblCompany = new JLabel("Visionary Imaging Services, Inc.");
		panel_1.add(lblCompany);
		lblCompany.setIcon(Resources.GraphyIcon.loadIconFromResource());
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		panel.add(tabbedPane, BorderLayout.NORTH);
		
		JPanel panel_2 = new JPanel();
		tabbedPane.addTab("Requests", null, panel_2, null);
		panel_2.setLayout(new BorderLayout(0, 0));
		
		JTextArea textArea = new JTextArea();
		panel_2.add(textArea);
		String requestsText = "We are developing the GRAPHY to help improve/enhance QOL.\n";
		requestsText = requestsText + "If you interested in this activities, contact us with any suggestions or improvements.\n";
		requestsText = requestsText + "We also accept your requests for collaboration such as personal study and research, grant research, corporate product development, sales promotion, education.";
		textArea.setText(requestsText);
		
		JButton btnGoToWeb = new JButton("Go to web form");
		panel_2.add(btnGoToWeb, BorderLayout.NORTH);
		
		JPanel panel_3 = new JPanel();
		tabbedPane.addTab("HowTos", null, panel_3, null);
		panel_3.setLayout(new BorderLayout(0, 0));
		
		/*
		 * TODO
		 * https://docs.oracle.com/javase/tutorial/uiswing/components/editorpane.html
		 */
		JTextArea textArea_1 = new JTextArea();
		panel_3.add(textArea_1, BorderLayout.NORTH);
		String line1 = "You can find some resources on the web,\n";
		String line2 = "Some examples are following,\n";
		String line3 = "GRAPHY web page: https://www.vis-ionary.com/graphy-how-to,\n";
		String line4 = "StackOverflow related graphy questions: ,\n";
		String line5 = "YouTube VIS Channels: ,\n";
		textArea_1.setText(line1+line2+line3+line4+line5);
		
		pack();
		setVisible(true);
	}
}
