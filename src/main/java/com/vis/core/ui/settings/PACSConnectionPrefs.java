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

package com.vis.core.ui.settings;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.SocketException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.logging.Level;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import java.awt.GridBagLayout;
import java.awt.Image;

import javax.swing.JLabel;
import javax.swing.JOptionPane;

import java.awt.GridBagConstraints;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;

import java.awt.Insets;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.NumberFormatter;
import javax.swing.JScrollPane;

import com.vis.configuration.Resources;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.AddDicomCommunicationNodeWin;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.core.util.DBUtils;
import com.vis.core.util.Platform;
import com.vis.core.util.StringUtils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.dimse.EchoImpl;

public class PACSConnectionPrefs extends JPanel {

	private static final long serialVersionUID = -6369768191399187866L;
	private JTable table;

	private DefaultTableModel model;
	public String[] dicomNetworkNodeTableHeader = new String[] { "Nickname", "AE title", "Host(IP address)",
			"Port(TCP/IP)", "Ciphers", "Ready" };
	private PACSConnectionPrefs thisPanel = this;

	public PACSConnectionPrefs() {
		
		setLayout(new BorderLayout());
		
		JPanel nodes = new JPanel();
		
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0, 0, 0, 0, 0, 0, 0, 0 };
		gridBagLayout.rowHeights = new int[] { 0, 0, 0, 306, 0 };
		gridBagLayout.columnWeights = new double[] { 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
		nodes.setLayout(gridBagLayout);

		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.gridwidth = 5;
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 1;
		gbc_verticalStrut.gridy = 0;
		nodes.add(verticalStrut, gbc_verticalStrut);

		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut.gridx = 0;
		gbc_horizontalStrut.gridy = 1;
		nodes.add(horizontalStrut, gbc_horizontalStrut);

		JLabel lblNewLabel = new JLabel("DICOM Communication Nodes");
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.anchor = GridBagConstraints.WEST;
		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.gridx = 1;
		gbc_lblNewLabel.gridy = 1;
		nodes.add(lblNewLabel, gbc_lblNewLabel);

		JButton addButton = new JButton("Add");
		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				/* add new nodes */
				new AddDicomCommunicationNodeWin(thisPanel);
			}
		});
		GridBagConstraints gbc_btnNewButton = new GridBagConstraints();
		gbc_btnNewButton.insets = new Insets(0, 0, 5, 5);
		gbc_btnNewButton.gridx = 2;
		gbc_btnNewButton.gridy = 1;
		nodes.add(addButton, gbc_btnNewButton);

		JButton deleteButton = new JButton("Delete");
		deleteButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int row = table.getSelectedRow();
				if (row == -1) {
					return;
				}
				String identical = (String) model.getValueAt(row, model.findColumn("Nickname"));
				String aet = (String) model.getValueAt(row, model.findColumn("AE title"));
				DatabaseHandler.getInstance().deleteServer(identical);
				DBUtils.deleteAEProperties(aet);
				model.removeRow(row);
				constructTableModel(getTable());
				/* 表示していたドックが削除された場合、タブから削除 */
				TreeTableDockManager dttm = WindowManager.getMainScreen().getTreeTableDockManager();
				for (String nickname : dttm.getAllNicknamesFromDocks()) {
					if (nickname.equals(identical)) {
						dttm.removeDockAt(nickname);
						break;
					}
				}
				//restart DicomServer
				try {
					DatabaseHandler db = DatabaseHandler.getInstance();
					db.initDicomServer();
					Log.logger.info("PACSConnectionPrefs: DICOM server restarted successfully after node deletion.");
				} catch (IOException | SQLException e) {
					Log.logger.severe("Can not start DcmQRSCP...");
					JOptionPane.showMessageDialog(null, Resources.i18n("AddDicomCommunicationNodeWin.error.restartRequired"), Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
				}
				WindowManager.getMainScreen().updateQRTreeTables();
			}
		});
		GridBagConstraints gbc_btnNewButton_1 = new GridBagConstraints();
		gbc_btnNewButton_1.insets = new Insets(0, 0, 5, 5);
		gbc_btnNewButton_1.gridx = 3;
		gbc_btnNewButton_1.gridy = 1;
		nodes.add(deleteButton, gbc_btnNewButton_1);

		JScrollPane scrollPane = new JScrollPane();
		GridBagConstraints gbc_scrollPane = new GridBagConstraints();
		gbc_scrollPane.gridheight = 2;
		gbc_scrollPane.gridwidth = 5;
		gbc_scrollPane.insets = new Insets(0, 0, 5, 5);
		gbc_scrollPane.fill = GridBagConstraints.BOTH;
		gbc_scrollPane.gridx = 1;
		gbc_scrollPane.gridy = 2;
		nodes.add(scrollPane, gbc_scrollPane);

		Component horizontalStrut_1 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_1 = new GridBagConstraints();
		gbc_horizontalStrut_1.insets = new Insets(0, 0, 0, 5);
		gbc_horizontalStrut_1.gridx = 0;
		gbc_horizontalStrut_1.gridy = 3;
		nodes.add(horizontalStrut_1, gbc_horizontalStrut_1);

		table = new JTable();
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		constructTableModel(table);
		scrollPane.setViewportView(table);
		table.setRowHeight(24);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		Component horizontalStrut_2 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_2 = new GridBagConstraints();
		gbc_horizontalStrut_2.gridx = 6;
		gbc_horizontalStrut_2.gridy = 3;
		nodes.add(horizontalStrut_2, gbc_horizontalStrut_2);

		JPanel northPanel = new JPanel(new java.awt.GridLayout(2, 1));
		northPanel.add(buildDCMQRSCPSettingPanel());
		northPanel.add(buildDicomWebSettingPanel());
		add(northPanel, BorderLayout.NORTH);
		add(nodes, BorderLayout.CENTER);
	}

	public void constructTableModel(JTable table) {
		// get server lists
		DatabaseHandler db = DatabaseHandler.getInstance();
		ArrayList<DicomCommunicationNode> tableData = db.loadServerList();
		Object[][] tblObj = new Object[tableData.size()][];
		
		String[] listenerInfo = db.getListenerDetails();//GRAPHY listener
		
		for (int i = 0; i < tableData.size(); i++) {
			DicomCommunicationNode nodeInfo = tableData.get(i);
			Object[] row = new Object[] { 
					nodeInfo.getNickname(),
					nodeInfo.getAETitle(), // aet
					nodeInfo.getHostName(), // host
					String.valueOf(nodeInfo.getPort()), // port
					nodeInfo.cipherListToString(), // ciphers
					new EchoImpl(null).echo(
							listenerInfo[0],
							listenerInfo[1],
							listenerInfo[2], 
							nodeInfo.getAETitle(), 
							nodeInfo.getHostName(), 
							String.valueOf(nodeInfo.getPort())
							)// connection established
			};
			tblObj[i] = row;
		}

		model = new DefaultTableModel(tblObj, dicomNetworkNodeTableHeader);
		/* ChangeListener */
		model.addTableModelListener(new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent tme) {
				int row = tme.getFirstRow();// current edited row
				int col = tme.getColumn();
				// "AE title","Host(IP address)","Port(TCP/IP)","Ciphers","Ready"
				String colType = model.getColumnName(col);
				if (colType.equals("Ready")) {
					return;
				}
				int name_col = model.findColumn("Nickname");
				if (colType.equals("Nickname")) {
					/* 編集されたserverName */
					String newNickname = (String) model.getValueAt(row, col);
					/* 既存のリストの中に無いかどうか */
					boolean inlist = db.isAlreadyRegisteredServer(newNickname);
					if (!inlist) {
						/* 変更されたサーバーを特定して、アップデートする */
						/* テーブルのニックネームと、DBのニックネームの差分を取り、DBのニックの内、残ったものが変更されたサーバー */
						String prevName = "";
						ArrayList<String> nameInDB = new ArrayList<String>();
						ArrayList<HashMap<String,Object>>  serverMaterials= db.getCommunicationServerList();
						for(HashMap<String,Object> nodeMaterials:serverMaterials) {
							String name = (String)nodeMaterials.get("logicalname");
							nameInDB.add(name);
						}
						/* テーブル上のニックネームをDB内のニックネームと差分 */
						for (int r = 0; r < model.getRowCount(); r++) {
							String name = (String) model.getValueAt(r, col);
							if (nameInDB.contains(name)) {
								nameInDB.remove(name);
							}
						}
						/* 残ったものが変更されたサーバー */
						prevName = nameInDB.get(0);
						/* update */
						if (!prevName.isEmpty()) {
							HashMap<String, Object> prevNodeMaterials = db.getServerInfo(prevName);
							DicomCommunicationNode prevToUpdate = new DicomCommunicationNode(prevNodeMaterials);
							prevToUpdate.setNickname(newNickname);
							db.updateServer(prevToUpdate.getNodeMaterials(), prevName);
						}
					}else {
						return;
					}
				} else if (colType.equals("AE title")) {
					String serverName = (String) model.getValueAt(row, name_col);
					String aet = (String) model.getValueAt(row, col);
					if (aet == null ) {
						aet = "";
					}
					HashMap<String, Object> nodeMaterials = db.getServerInfo(serverName);
					DicomCommunicationNode node = new DicomCommunicationNode(nodeMaterials);
					node.setAETitle(aet);
					db.updateServer(node.getNodeMaterials(), serverName);
				} else if (colType.equals("Host(IP address)")) {
					String serverName = (String) model.getValueAt(row, name_col);
					String host_new = (String) model.getValueAt(row, col);
					if (host_new.equals("") || host_new == null) {
						host_new = "localhost";// default value
					}
					HashMap<String, Object> nodeMaterials = db.getServerInfo(serverName);
					DicomCommunicationNode node = new DicomCommunicationNode(nodeMaterials);
					node.setHostName(host_new);
					db.updateServer(node.getNodeMaterials(), serverName);
				} else if (colType.equals("Port(TCP/IP)")) {
					String serverName = (String) model.getValueAt(row, name_col);
					Integer port_new = Integer.valueOf((String) model.getValueAt(row, col));
					if (port_new == null) {
						port_new = 11112;// default value
					}
					HashMap<String, Object> nodeMaterials = db.getServerInfo(serverName);
					DicomCommunicationNode node = new DicomCommunicationNode(nodeMaterials);
					node.setPort(port_new);
					db.updateServer(node.getNodeMaterials(), serverName);
				} else if (colType.equals("Ciphers")) {
					String serverName = (String) model.getValueAt(row, name_col);
					String cip_new = String.valueOf((String) model.getValueAt(row, col));
					if (cip_new.equals("") || cip_new == null) {
						cip_new = "";// default value
					}
					HashMap<String, Object> nodeMaterials = db.getServerInfo(serverName);
					DicomCommunicationNode node = new DicomCommunicationNode(nodeMaterials);
					node.setCipherFromStringSequence(cip_new);
					db.updateServer(node.getNodeMaterials(), serverName);
				}
				constructTableModel(table);
			}
		});
		/* continue constructions */
		table.setModel(model);
		TableColumnModel tcm = table.getColumnModel();
		TableColumn tc = tcm.getColumn(model.findColumn("Ready"));
		tc.setCellRenderer(new EchoButtonRenderer());
		tc.setCellEditor(new EchoButtonEditor());
		revalidate();
		repaint();
	}

	public JTable getTable() {
		return table;
	}
	
	private JPanel buildDCMQRSCPSettingPanel() {
		JPanel base = new JPanel();
		base.setLayout(new BorderLayout());
		base.setBorder(BorderFactory.createEtchedBorder());
		
		JPanel currentInfo = new JPanel(new FlowLayout(FlowLayout.LEFT));
		String aet = "AET:";
		String host = "HOST:";
		String port = "PORT:";
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db != null) {
			String[] listenerInfo = db.getListenerDetails();
			aet += listenerInfo[0];//.getString("aetitle");
			host += listenerInfo[1];//.getString("host");
			port += listenerInfo[2];//.getString("port");
//			detail[3] = listenerInfo.getString("storagelocation");
		}
		currentInfo.add(new JLabel("Current Listener:"+aet+","+host+","+port, JLabel.LEFT));
		base.add(currentInfo, BorderLayout.NORTH);
		
		// input area
		JLabel l1 = new JLabel("AE Title");
		JLabel l2 = new JLabel("Host(IP address)");
		JLabel l3 = new JLabel("Port");
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
		//AE
		p.add(l1);
		JFormattedTextField t1 = new JFormattedTextField();
		t1.setColumns(10);
		p.add(t1);
		
		//Host
		p.add(l2);
		// 作成したIpAddressFormatterをJFormattedTextFieldに設定
//       JFormattedTextField ipAddressField = new JFormattedTextField(new IpAddressFormatter());
       JFormattedTextField ipAddressField = new JFormattedTextField();
       ipAddressField.setColumns(15); // フィールドの幅を適切に設定
       //ipAddressField.setValue("192.168.1.1"); // 初期値を設定
		p.add(ipAddressField);
		
		//Port
		p.add(l3);
		// 1. 整数用のNumberFormatインスタンスを取得
       NumberFormat format = NumberFormat.getIntegerInstance();
        // 2. 桁区切りカンマ(,)を無効にする
       format.setGroupingUsed(false);
       // 3. NumberFormatを元にNumberFormatterを作成
		NumberFormatter formatter = new NumberFormatter(format);
		formatter.setValueClass(Integer.class); // 値のクラスをIntegerに設定
		formatter.setMinimum(0); // 最小値を1に設定 (これにより正の整数のみとなる)
		formatter.setMaximum(65535); // 最大値を設定.maximum TCP/IP port number.
		formatter.setAllowsInvalid(false); // 無効な値の入力を一時的にでも許可しない
		formatter.setCommitsOnValidEdit(true); // 有効な編集が行われるたびに値をコミットする
		JFormattedTextField t3 = new JFormattedTextField(formatter);
		t3.setColumns(5);
		p.add(t3);
		
		JButton update = new JButton("Update Listener");
		String sampleIp = null;
		try {
			sampleIp = Platform.getLocalIpAddresses().get(0);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String msg = "AET: Application Entity Name, such as GRAPHY\n";
		msg += "Host: IP address at this machine, such as "+ sampleIp +"\n";
		msg += "Port: 5 digit number in range 0~65535. 1024~49151 is recommended.";
		update.setToolTipText(msg);
		update.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				
				MainScreen ms = WindowManager.getMainScreen();
				int res = JOptionPane.showConfirmDialog(ms, Resources.i18n("PACSConnectionPrefs.confirm.shutdown"), Resources.i18n("dialog.title.confirm"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
				if(res != JOptionPane.OK_OPTION) {
					return;
				}
				//validate input
				String aet = t1.getText();
				String host = ipAddressField.getText();
				String port = t3.getText();

				if(StringUtils.isInvalidAET(aet)) {
					Log.logger.warning("PACSConnectionPrefs: invalid AET value: " + aet);
					JOptionPane.showMessageDialog(null, Resources.i18n("PACSConnectionPrefs.error.invalidAET"), Resources.i18n("dialog.title.inputWarning"), JOptionPane.WARNING_MESSAGE);
					return;
				}

				if(StringUtils.isInvalidHostIP(host)) {
					Log.logger.warning("PACSConnectionPrefs: invalid IP address value: " + host);
					JOptionPane.showMessageDialog(null, Resources.i18n("PACSConnectionPrefs.error.invalidIP"), Resources.i18n("dialog.title.inputWarning"), JOptionPane.WARNING_MESSAGE);
					return;
				}

				if(StringUtils.isInvalidPort(port)) {
					Log.logger.warning("PACSConnectionPrefs: invalid port number value: " + port);
					JOptionPane.showMessageDialog(null, Resources.i18n("PACSConnectionPrefs.error.invalidPort"), Resources.i18n("dialog.title.inputWarning"), JOptionPane.WARNING_MESSAGE);
					return;
				}
				
				DatabaseHandler db = DatabaseHandler.getInstance();
				try {
					db.updateListener(aet, host, port, db.getLocalDBLocation());
				} catch (Exception e1) {
					Log.logger.log(Level.SEVERE, e1.getMessage());
				}
				// same process with main window closing.
				if(ms != null) {
					try {
						ApplicationFacade.readyToClose(Level.SEVERE/*Force closing*/, "Database Lister details was updated. GRAPHY need to restart.");
					} catch (Throwable e1) {
						e1.printStackTrace();
					}
				}
			}
		});
		p.add(update);
		
		base.add(p, BorderLayout.CENTER);

		return base;
	}

	/**
	 * DICOMweb(QIDO-RS/WADO-RS/STOW-RS)サーバーの有効化/ポート設定。
	 * 既存の「Update Listener」と同じパターン(検証→DB更新→アプリ再起動を促す)に合わせる。
	 */
	private JPanel buildDicomWebSettingPanel() {
		JPanel base = new JPanel();
		base.setLayout(new BorderLayout());
		base.setBorder(BorderFactory.createEtchedBorder());

		DatabaseHandler db = DatabaseHandler.getInstance();
		boolean currentEnabled = false;
		String currentPort = "";
		String currentContextPath = "/dicomweb";
		if (db != null) {
			String[] webDetails = db.getDicomWebListenerDetails();
			if (webDetails != null) {
				currentEnabled = Boolean.parseBoolean(webDetails[0]);
				currentPort = webDetails[1];
				currentContextPath = webDetails[2];
			}
		}

		JPanel currentInfo = new JPanel(new FlowLayout(FlowLayout.LEFT));
		currentInfo.add(new JLabel("Current DICOMweb: ENABLED=" + currentEnabled + ", PORT=" + currentPort
				+ ", PATH=" + currentContextPath, JLabel.LEFT));
		base.add(currentInfo, BorderLayout.NORTH);

		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JCheckBox enabledCheck = new JCheckBox("Enable DICOMweb server (QIDO-RS/WADO-RS/STOW-RS)", currentEnabled);
		p.add(enabledCheck);

		p.add(new JLabel("Port"));
		NumberFormat webPortFormat = NumberFormat.getIntegerInstance();
		webPortFormat.setGroupingUsed(false);
		NumberFormatter webPortFormatter = new NumberFormatter(webPortFormat);
		webPortFormatter.setValueClass(Integer.class);
		webPortFormatter.setMinimum(0);
		webPortFormatter.setMaximum(65535);
		webPortFormatter.setAllowsInvalid(false);
		webPortFormatter.setCommitsOnValidEdit(true);
		JFormattedTextField webPortField = new JFormattedTextField(webPortFormatter);
		webPortField.setColumns(5);
		if (currentPort != null && !currentPort.isEmpty() && !"0".equals(currentPort)) {
			webPortField.setValue(Integer.valueOf(currentPort));
		}
		p.add(webPortField);

		JButton updateWeb = new JButton("Update DICOMweb Settings");
		updateWeb.setToolTipText(
				"DIMSEのPortとは別の番号にしてください。HTTP(暗号化なし)で公開されるため、信頼できるネットワークでのみ有効化してください。");
		updateWeb.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				MainScreen ms = WindowManager.getMainScreen();
				int res = JOptionPane.showConfirmDialog(ms, Resources.i18n("PACSConnectionPrefs.confirm.shutdown"),
						Resources.i18n("dialog.title.confirm"), JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.QUESTION_MESSAGE);
				if (res != JOptionPane.OK_OPTION) {
					return;
				}
				boolean enabled = enabledCheck.isSelected();
				Object portValue = webPortField.getValue();
				int port = (portValue instanceof Number) ? ((Number) portValue).intValue() : 0;
				if (enabled && port <= 0) {
					JOptionPane.showMessageDialog(null, "Please set a port number when enabling DICOMweb.",
							Resources.i18n("dialog.title.inputWarning"), JOptionPane.WARNING_MESSAGE);
					return;
				}
				DatabaseHandler db2 = DatabaseHandler.getInstance();
				try {
					db2.updateDicomWebListener(enabled, port, "/dicomweb");
				} catch (Exception e1) {
					Log.logger.log(Level.SEVERE, e1.getMessage());
				}
				if (ms != null) {
					try {
						ApplicationFacade.readyToClose(Level.SEVERE/* Force closing */,
								"DICOMweb settings was updated. GRAPHY need to restart.");
					} catch (Throwable e1) {
						e1.printStackTrace();
					}
				}
			}
		});
		p.add(updateWeb);

		base.add(p, BorderLayout.CENTER);
		return base;
	}

	class EchoButtonRenderer extends JButton implements TableCellRenderer {

		/**
		 * 
		 */
		private static final long serialVersionUID = 8906997032244269301L;
		String successIconPath = "/icon/ic_location_on_black_24dp.png";
		String failedIconPath = "/icon/ic_location_off_black_24dp.png";

		public EchoButtonRenderer() {
			super();
		}

		public void setIcon(boolean eho_success) {

			if (eho_success) {
				Image img = Toolkit.getDefaultToolkit().createImage(getClass().getResource(successIconPath));
				ImageIcon icon = new ImageIcon(img);
//				JLabel iconLabel = new JLabel();
//				iconLabel.setOpaque(false);
//				iconLabel.setIcon(icon);
				setIcon(icon);
//				setForeground(new Color(152, 251, 152));
				setBackground(new Color(152, 251, 152));
			} else {
				Image img = Toolkit.getDefaultToolkit().createImage(getClass().getResource(failedIconPath));
				ImageIcon icon = new ImageIcon(img);
//				JLabel iconLabel = new JLabel();
//				iconLabel.setOpaque(false);
//				iconLabel.setIcon(icon);
				setIcon(icon);
			}
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int col) {
			if (isSelected) {
				setForeground(table.getSelectionForeground());
				super.setBackground(table.getSelectionBackground());
			} else {
				setForeground(table.getForeground());
				setBackground(table.getBackground());
			}
			if(value != null) {
				boolean echo_success = ((Boolean) value).booleanValue();
				this.setIcon(echo_success);
			}
			return this;
		}
	}

	class EchoButtonEditor extends JButton implements TableCellEditor {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		protected EventListenerList listenerList = new EventListenerList();
		protected ChangeEvent changeEvent = new ChangeEvent(this);

		String successIconPath = "/icon/ic_location_on_black_24dp.png";
		String failedIconPath = "/icon/ic_location_off_black_24dp.png";

		boolean state;

		public EchoButtonEditor() {
			super();
			addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					constructTableModel(table);
					WindowManager.getMainScreen().updateQRTreeTables();
				}
			});
		}

		public void setIcon(boolean eho_success) {
			if (eho_success) {
				Image img = Toolkit.getDefaultToolkit().createImage(getClass().getResource(successIconPath));
				ImageIcon icon = new ImageIcon(img);
				setIcon(icon);
				setBackground(new Color(152, 251, 152));
			} else {
				Image img = Toolkit.getDefaultToolkit().createImage(getClass().getResource(failedIconPath));
				ImageIcon icon = new ImageIcon(img);
				setIcon(icon);
			}
		}

		@Override
		public void addCellEditorListener(CellEditorListener listener) {
			listenerList.add(CellEditorListener.class, listener);
		}

		@Override
		public void cancelCellEditing() {
			fireEditingCanceled();
		}

		protected void fireEditingStopped() {
			CellEditorListener listener;
			Object[] listeners = listenerList.getListenerList();
			for (int i = 0; i < listeners.length; i++) {
				if (listeners[i] == CellEditorListener.class) {
					listener = (CellEditorListener) listeners[i + 1];
					listener.editingStopped(changeEvent);
				}
			}
		}

		private void fireEditingCanceled() {
			CellEditorListener listener;
			Object[] listeners = listenerList.getListenerList();
			for (int i = 0; i < listeners.length; i++) {
				if (listeners[i] == CellEditorListener.class) {
					listener = (CellEditorListener) listeners[i + 1];
					listener.editingCanceled(changeEvent);
				}
			}
		}

		@Override
		public Object getCellEditorValue() {
			return state;
		}

		@Override
		public boolean isCellEditable(EventObject arg0) {
			return true;// keep always true for can push button
		}

		@Override
		public void removeCellEditorListener(CellEditorListener listener) {
			listenerList.remove(CellEditorListener.class, listener);
		}

		@Override
		public boolean shouldSelectCell(EventObject arg0) {
			return true;
		}

		@Override
		public boolean stopCellEditing() {
			fireEditingStopped();
			return true;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int col) {
			state = ((Boolean) value).booleanValue();
			setIcon(state);
			return this;
		}
	}

}
