package com.vis.core.ui.dialog;

import javax.swing.JFrame;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JTextField;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.settings.*;
import com.vis.core.util.DBUtils;
import com.vis.core.util.StringUtils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.dimse.FindSCU;

import javax.swing.JButton;
import java.awt.Component;
import javax.swing.Box;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.awt.event.ActionEvent;

public class AddDicomCommunicationNodeWin extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JTextField aeText;
	private JTextField hostText;
	private JTextField portText;
	private JTextField cipherText;
	private PACSConnectionPrefs pacsPrefPanel;
	private JTextField nicknameField;

	public AddDicomCommunicationNodeWin(PACSConnectionPrefs pacsPref) {
		setTitle("Create New Connection");
		pacsPrefPanel = pacsPref;
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		getContentPane().setLayout(gridBagLayout);
		
		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 1;
		gbc_verticalStrut.gridy = 0;
		getContentPane().add(verticalStrut, gbc_verticalStrut);
		
		JLabel lblNickname = new JLabel("Nickname");
		GridBagConstraints gbc_lblNickname = new GridBagConstraints();
		gbc_lblNickname.anchor = GridBagConstraints.WEST;
		gbc_lblNickname.insets = new Insets(0, 0, 5, 5);
		gbc_lblNickname.gridx = 1;
		gbc_lblNickname.gridy = 1;
		getContentPane().add(lblNickname, gbc_lblNickname);
		
		nicknameField = new JTextField();
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.insets = new Insets(0, 0, 5, 0);
		gbc_textField.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField.gridx = 3;
		gbc_textField.gridy = 1;
		getContentPane().add(nicknameField, gbc_textField);
		nicknameField.setColumns(10);
		
		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut.gridx = 0;
		gbc_horizontalStrut.gridy = 2;
		getContentPane().add(horizontalStrut, gbc_horizontalStrut);
		
		JLabel lblAeTitle = new JLabel("AE Title");
		lblAeTitle.setToolTipText("Set identical name for Dicom nodes.");
		GridBagConstraints gbc_lblAeTitle = new GridBagConstraints();
		gbc_lblAeTitle.anchor = GridBagConstraints.WEST;
		gbc_lblAeTitle.insets = new Insets(0, 0, 5, 5);
		gbc_lblAeTitle.gridx = 1;
		gbc_lblAeTitle.gridy = 2;
		getContentPane().add(lblAeTitle, gbc_lblAeTitle);
		
		aeText = new JTextField();
		aeText.setToolTipText("e.g, MyDICOMServer");
		GridBagConstraints gbc_txtEgMydicomserver = new GridBagConstraints();
		gbc_txtEgMydicomserver.insets = new Insets(0, 0, 5, 0);
		gbc_txtEgMydicomserver.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtEgMydicomserver.gridx = 3;
		gbc_txtEgMydicomserver.gridy = 2;
		getContentPane().add(aeText, gbc_txtEgMydicomserver);
		aeText.setColumns(10);
		
		JLabel lblHostipAddress = new JLabel("Host(IP address)");
		lblHostipAddress.setToolTipText("Input address as xxx.xxx.xxx.xxx format. e.g, 192.168.1.1");
		GridBagConstraints gbc_lblHostipAddress = new GridBagConstraints();
		gbc_lblHostipAddress.insets = new Insets(0, 0, 5, 5);
		gbc_lblHostipAddress.anchor = GridBagConstraints.WEST;
		gbc_lblHostipAddress.gridx = 1;
		gbc_lblHostipAddress.gridy = 3;
		getContentPane().add(lblHostipAddress, gbc_lblHostipAddress);
		
		hostText = new JTextField();
		hostText.setToolTipText("Input address as xxx.xxx.xxx.xxx format. e.g, 192.168.1.1");
		GridBagConstraints gbc_txtEg_1 = new GridBagConstraints();
		gbc_txtEg_1.insets = new Insets(0, 0, 5, 0);
		gbc_txtEg_1.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtEg_1.gridx = 3;
		gbc_txtEg_1.gridy = 3;
		getContentPane().add(hostText, gbc_txtEg_1);
		hostText.setColumns(10);
		
		JLabel lblPort = new JLabel("Port");
		lblPort.setToolTipText("Set TCP/IP Port number, like 11112.");
		GridBagConstraints gbc_lblPort = new GridBagConstraints();
		gbc_lblPort.insets = new Insets(0, 0, 5, 5);
		gbc_lblPort.anchor = GridBagConstraints.WEST;
		gbc_lblPort.gridx = 1;
		gbc_lblPort.gridy = 4;
		getContentPane().add(lblPort, gbc_lblPort);
		
		portText = new JTextField();
		portText.setToolTipText("Set TCP/IP Port number, like 11112.");
		GridBagConstraints gbc_txtEg = new GridBagConstraints();
		gbc_txtEg.insets = new Insets(0, 0, 5, 0);
		gbc_txtEg.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtEg.gridx = 3;
		gbc_txtEg.gridy = 4;
		getContentPane().add(portText, gbc_txtEg);
		portText.setColumns(10);
		
		JButton btnNewButton = new JButton("Validate and Apply");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				String identical = nicknameField.getText().trim();
				addQRTreeTable(identical);
				dispose();
			}
		});
		
		JLabel lblCiphers = new JLabel("Ciphers");
		lblCiphers.setToolTipText("Set following value splitted by  \":\"  or  \",\"\ne.g, single pattern->SSL_RSA_WITH_NULL_SHA, multi pattern -> SSL_RSA_WITH_NULL_SHA:TLS_RSA_WITH_AES_128_CBC_SHA:TLS_RSA_WITH_3DES_EDE_CBC_SHA");
		GridBagConstraints gbc_lblCiphers = new GridBagConstraints();
		gbc_lblCiphers.anchor = GridBagConstraints.WEST;
		gbc_lblCiphers.insets = new Insets(0, 0, 5, 5);
		gbc_lblCiphers.gridx = 1;
		gbc_lblCiphers.gridy = 5;
		getContentPane().add(lblCiphers, gbc_lblCiphers);
		
		cipherText = new JTextField();
		GridBagConstraints gbc_textField1 = new GridBagConstraints();
		gbc_textField1.insets = new Insets(0, 0, 5, 0);
		gbc_textField1.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField1.gridx = 3;
		gbc_textField1.gridy = 5;
		getContentPane().add(cipherText, gbc_textField1);
		cipherText.setColumns(10);
		GridBagConstraints gbc_btnNewButton = new GridBagConstraints();
		gbc_btnNewButton.anchor = GridBagConstraints.EAST;
		gbc_btnNewButton.gridx = 3;
		gbc_btnNewButton.gridy = 6;
		getContentPane().add(btnNewButton, gbc_btnNewButton);
		
		pack();
		setVisible(true);
		setLocationRelativeTo(pacsPref);
	}
	
	public void addQRTreeTable(String nickname){
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(nickname == null || nickname.equals("") || db.isAlreadyRegisteredServer(nickname)) {
			JOptionPane.showMessageDialog(null,"Already registered , please set identical nickname.");
			return;
		}
		String aet = aeText.getText().trim();
		String Host = hostText.getText().trim();
		String Port = portText.getText().trim();
		
		if(StringUtils.isInvalidAET(aet)) {
			System.out.println("AET is invalid.:"+aet);
			return;
		}
		if(StringUtils.isInvalidHostIP(Host)) {
			System.out.println("HOST IP ADDRESS is invalid.:"+Host);
			return;
		}
		if(StringUtils.isInvalidPort(Port)) {
			System.out.println("Port is invalid.:"+Port);
			return;
		}
		
		if(checkCFINDCapability(aet, Host, Port)==false) {
			String msg = "GRAPHY cannnot add this remote node.\n";
			msg += "reason why ? maybe...\n";
			msg += "- remote node is offline or sleeping or stopped ?\n";
			msg += "- remote node does not support C-Find ?\n";
			msg += "- remote node does not recognized graphy ae title ?";
			Log.logger.info(msg);
			return;
		}
		
		String CipherSeq = cipherText.getText().trim();
//		DicomCommunicationNode node = new DicomCommunicationNode(nickname,aet, Host, portNum, CipherSeq);
		db.insertServer(nickname,aet, Host, Integer.valueOf(Port), CipherSeq, null,null,-1,null,null);
		
		DBUtils.updateAEProperties(aet, Host, Port, CipherSeq);
		
		//restart DicomServer
		try {
			db.initDicomServer();
		} catch (IOException | SQLException e) {
			Log.logger.severe("Can not start DcmQRSCP...");
			JOptionPane.showMessageDialog(null, "Something happen when adding DICOM node, GRAPHY-DB can not restart correctly, please restart GRAPHY...");
		}
		
		pacsPrefPanel.constructTableModel(pacsPrefPanel.getTable());
		new Thread(() -> {
		    WindowManager.getMainScreen().updateQRTreeTables();
		}).start();
	}
	
	boolean checkCFINDCapability(String aet, String host, String port) {
		try {
			FindSCU findSCU = new FindSCU();
			return findSCU.isRemoteCFINDCapable(aet, host, port);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
}
